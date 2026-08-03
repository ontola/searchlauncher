package com.searchlauncher.app.ui.browser

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.webkit.WebView
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.util.concurrent.atomic.AtomicInteger

internal class BrowserTab(initialUrl: String) {
  val id: Long = System.nanoTime()
  var url by mutableStateOf(initialUrl)
  var title by mutableStateOf<String?>(null)
  var desktopMode by mutableStateOf(false)
  var pageBackgroundArgb by mutableIntStateOf(0xff000000.toInt())
  var snapshot by mutableStateOf<Bitmap?>(null)
  /** Site icon as the WebView reported it, shown next to the tab's address in the overview. */
  var favicon by mutableStateOf<Bitmap?>(null)
  /**
   * Whether the page currently loaded here has actually been painted. Captures taken before that —
   * mid-load, mid-restore — come back blank, and storing one would throw away a perfectly good
   * preview of the same page.
   */
  var pageDrawn = false
  var webViewState: Bundle? = null
  /**
   * Requests the ad blocker rejected for the page currently loaded in this tab. Incremented from
   * the WebView's background request thread, so it is deliberately not Compose state.
   */
  val blockedRequestCount = AtomicInteger(0)
}

@Stable
internal class BrowserTabs(initialUrl: String) {
  val items = mutableStateListOf(BrowserTab(initialUrl))
  var activeIndex by mutableIntStateOf(0)
    private set

  val active: BrowserTab
    get() = items[activeIndex]

  fun add(url: String): BrowserTab {
    if (items.size >= MAX_TABS) {
      val removableIndex = items.indices.firstOrNull { it != activeIndex } ?: 0
      items.removeAt(removableIndex)
      if (removableIndex < activeIndex) activeIndex--
    }
    val tab = BrowserTab(url)
    items.add(tab)
    activeIndex = items.lastIndex
    return tab
  }

  fun activate(index: Int): BrowserTab? {
    if (index !in items.indices || index == activeIndex) return null
    activeIndex = index
    return active
  }

  /** Used when re-entering the browser from the launcher, which always lands on the newest tab. */
  fun activateLast() {
    activeIndex = items.lastIndex
  }

  fun adjacent(direction: Int): BrowserTab? = items.getOrNull(activeIndex + direction)

  fun closeActive(): BrowserTab? {
    if (items.size == 1) return null
    items.removeAt(activeIndex)
    activeIndex = activeIndex.coerceAtMost(items.lastIndex)
    return active
  }

  /**
   * Removes [index], returning the tab that is active afterwards, or null when [index] was the only
   * remaining tab (the caller decides what "no tabs left" means).
   */
  fun close(index: Int): BrowserTab? {
    if (index !in items.indices || items.size == 1) return null
    items.removeAt(index)
    if (index < activeIndex || activeIndex > items.lastIndex) {
      activeIndex = (activeIndex - 1).coerceIn(0, items.lastIndex)
    }
    return active
  }

  companion object {
    private const val MAX_TABS = 16
  }
}

/**
 * Process-wide home of the (non-private) browser tabs. Tabs used to live in [BrowserActivity]'s
 * composition, which meant they died with the activity — the launcher home screen could not preview
 * the last tab, and returning to the browser lost everything. Private browsing runs in its own
 * process and keeps its tabs activity-local, so it never touches this.
 */
internal object BrowserTabStore {
  // Compose state rather than a plain field: the launcher shows a live count of these in its own
  // search bar, so it has to recompose when the browser — a different activity in this process —
  // opens or closes tabs.
  var tabs: BrowserTabs? by mutableStateOf(null)
    private set

  fun adopt(tabs: BrowserTabs) {
    this.tabs = tabs
  }

  fun lastTab(): BrowserTab? = tabs?.items?.lastOrNull()

  /** Position of the tab with [id], or -1 if it has since been closed. */
  fun indexOfTab(id: Long): Int = tabs?.items?.indexOfFirst { it.id == id } ?: -1

  /**
   * Closes the tab with [id], returning true when it was the last one — the caller is then expected
   * to shut the browser window down too, since an emptied list is not something [BrowserTabs] can
   * represent and a browser showing tabs nothing else agrees exist is worse than no browser.
   */
  fun close(id: Long): Boolean {
    val current = tabs ?: return false
    val index = indexOfTab(id)
    if (index < 0) return false
    if (current.items.size == 1) {
      clear()
      return true
    }
    current.close(index)
    return false
  }

  /** A negative [index] means "the newest tab", which is where a launcher swipe always lands. */
  fun activate(index: Int) {
    val current = tabs ?: return
    if (index < 0) current.activateLast() else current.activate(index)
  }

  fun clear() {
    tabs = null
  }

  /**
   * Drops every preview except the visible tab's under memory pressure. Snapshots are only a
   * rendering nicety, and each is a few megabytes; they are dropped rather than recycled because a
   * composition may still be drawing one.
   */
  fun trimSnapshots() {
    val current = tabs ?: return
    current.items.forEachIndexed { index, tab ->
      if (index != current.activeIndex) tab.snapshot = null
    }
  }
}

internal fun saveWebViewIntoTab(webView: WebView, tab: BrowserTab) {
  tab.url = webView.url ?: tab.url
  tab.title = webView.title
  tab.webViewState = Bundle().also(webView::saveState)
  captureTabSnapshot(webView, tab)
}

/**
 * Refreshes [tab]'s preview from [webView] — but only when there is something worth storing.
 *
 * A capture is skipped outright until the page has been painted, since a WebView that is still
 * loading or restoring draws as a blank sheet. Even then the result is checked for being one flat
 * colour, which is what a software canvas returns for hardware-rendered content (video, canvas,
 * WebGL) and for a page whose paint has not landed yet. In both cases whatever preview the tab
 * already has is a better answer than a white rectangle.
 *
 * The replaced bitmap is dropped rather than recycled: the same one may still be on screen in the
 * tabs overview or a settling swipe, and drawing a recycled bitmap crashes.
 */
internal fun captureTabSnapshot(webView: WebView, tab: BrowserTab) {
  if (!tab.pageDrawn || webView.contentHeight <= 0) return
  // With the keyboard up the WebView has been shrunk to sit above the keys, so a capture is short
  // and shows only the top half of the page. Whatever the tab already has is a truer picture of it
  // than that; a tab with nothing yet takes it, since a partial preview still beats a bare icon.
  if (tab.snapshot != null && webView.isKeyboardVisible()) return
  val snapshot = captureWebViewSnapshot(webView) ?: return
  if (tab.snapshot != null && snapshot.isOneFlatColor()) return
  tab.snapshot = snapshot
}

/** Asked of the view itself, so every capture path gets the answer as of the moment it captures. */
private fun WebView.isKeyboardVisible(): Boolean =
  ViewCompat.getRootWindowInsets(this)?.isVisible(WindowInsetsCompat.Type.ime()) == true

/** Samples a coarse grid rather than every pixel; a real page differs somewhere within 8 steps. */
private fun Bitmap.isOneFlatColor(): Boolean {
  val corner = getPixel(0, 0)
  val stepX = (width / 8).coerceAtLeast(1)
  val stepY = (height / 8).coerceAtLeast(1)
  var x = 0
  while (x < width) {
    var y = 0
    while (y < height) {
      if (getPixel(x, y) != corner) return false
      y += stepY
    }
    x += stepX
  }
  return true
}

private fun captureWebViewSnapshot(webView: WebView): Bitmap? {
  if (webView.width <= 0 || webView.height <= 0) return null
  val scale = (SNAPSHOT_WIDTH_PX.toFloat() / webView.width).coerceAtMost(1f)
  val width = (webView.width * scale).toInt().coerceAtLeast(1)
  val height = (webView.height * scale).toInt().coerceAtLeast(1)
  return runCatching {
      Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565).also { bitmap ->
        Canvas(bitmap).apply {
          scale(scale, scale)
          webView.draw(this)
        }
      }
    }
    .getOrNull()
}

// Half resolution on common 1080p phones, which with RGB_565 is roughly 1.3 MB per tab rather than
// 5 MB. Tabs now outlive the browser activity so the whole set is held for as long as the process
// is, and 16 of them at full width was a lot of memory to be sitting on — enough that the system
// would rather kill the process than let it keep previews. Upscaling this much is soft on a
// swipe's incoming page, but only while it is moving; the overview draws its cards well under half
// width anyway.
private const val SNAPSHOT_WIDTH_PX = 540
