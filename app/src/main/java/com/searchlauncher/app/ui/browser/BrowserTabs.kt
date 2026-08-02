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
  var tabs: BrowserTabs? = null
    private set

  fun adopt(tabs: BrowserTabs) {
    this.tabs = tabs
  }

  fun lastTab(): BrowserTab? = tabs?.items?.lastOrNull()

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
  // The replaced snapshot is dropped rather than recycled: the same bitmap may still be on screen
  // (the tabs overview, a settling swipe), and drawing a recycled bitmap crashes.
  captureWebViewSnapshot(webView)?.let { snapshot -> tab.snapshot = snapshot }
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

// Full resolution on common 1080p phones; RGB_565 keeps that at ~5 MB per tab. Higher-density
// screens get a mild downscale, which is barely visible at swipe speed.
private const val SNAPSHOT_WIDTH_PX = 1080
