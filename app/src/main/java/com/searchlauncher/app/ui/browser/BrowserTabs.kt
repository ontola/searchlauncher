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
import kotlin.math.abs

/**
 * [restoredId] rebuilds a tab under an identity it already had. A tab's id is what its window's
 * task is keyed on, so a card that outlives the process — the app switcher keeps showing it — has
 * to come back as the same tab rather than as a new one wearing the old card.
 */
internal class BrowserTab(initialUrl: String, restoredId: Long? = null) {
  val id: Long = restoredId ?: System.nanoTime()
  var url by mutableStateOf(initialUrl)
  var title by mutableStateOf<String?>(null)
  var desktopMode by mutableStateOf(false)
  /** The colour the page itself is painted on, which is what shows through any gap in it. */
  var pageBackgroundArgb by mutableIntStateOf(0xff000000.toInt())
  /**
   * The colour the site asks the browser's own furniture to wear, from `<meta name="theme-color">`,
   * or null when it asks for nothing. Kept apart from [pageBackgroundArgb] because the two are
   * genuinely different answers: a site is perfectly entitled to a dark toolbar over a white page,
   * and painting the page's canvas in the toolbar's colour would show through every load gap.
   */
  var themeColorArgb by mutableStateOf<Int?>(null)

  /**
   * What the browser paints around the page — the bars above and below it, and the fill behind a
   * preview. The site's own theme colour when it names one, which is what Chrome tints its toolbar
   * and status bar with; otherwise the page's background, so a page that says nothing still gets
   * furniture that belongs to it rather than a fixed grey.
   */
  val frameColorArgb: Int
    get() = themeColorArgb ?: pageBackgroundArgb

  var snapshot by mutableStateOf<Bitmap?>(null)
  /** Site icon as the WebView reported it, shown next to the tab's address in the overview. */
  var favicon by mutableStateOf<Bitmap?>(null)
  /**
   * Whether the page currently loaded here has actually been painted. Captures taken before that —
   * mid-load, mid-restore — come back blank, and storing one would throw away a perfectly good
   * preview of the same page.
   */
  var pageDrawn = false
  /**
   * Whether this tab has ever had a window of its own, which is what puts a card for it in the
   * system's app switcher. Set by [BrowserActivity] when it takes the tab up, and read when
   * reconciling the tab list against the cards that are still there — a tab created moments ago is
   * not an abandoned one just because its task has not been built yet.
   */
  var hasOwnTask = false
  var webViewState: Bundle? = null
  /**
   * Requests the ad blocker rejected for the page currently loaded in this tab. Incremented from
   * the WebView's background request thread, so it is deliberately not Compose state.
   */
  val blockedRequestCount = AtomicInteger(0)
}

@Stable
internal class BrowserTabs(initialUrl: String, initialId: Long? = null) {
  val items = mutableStateListOf(BrowserTab(initialUrl, initialId))
  var activeIndex by mutableIntStateOf(0)
    private set

  val active: BrowserTab
    get() = items[activeIndex]

  /**
   * [onEvict] is handed whichever tab had to go to make room, so the caller can take its window
   * down with it — a tab dropped from this list but left with a card in the app switcher is a card
   * that reopens nothing.
   */
  fun add(url: String, onEvict: (BrowserTab) -> Unit = {}, restoredId: Long? = null): BrowserTab {
    if (items.size >= MAX_TABS) {
      val removableIndex = items.indices.firstOrNull { it != activeIndex } ?: 0
      onEvict(items.removeAt(removableIndex))
      if (removableIndex < activeIndex) activeIndex--
    }
    val tab = BrowserTab(url, restoredId)
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

  fun indexOfFirst(id: Long): Int = items.indexOfFirst { it.id == id }

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

  fun tab(id: Long): BrowserTab? = tabs?.items?.firstOrNull { it.id == id }

  /**
   * The tab [id] names, brought back at [url] if it is not here any more — which is what a window
   * restored from a card in the app switcher finds after the process has been killed, and what a
   * window opened for a page from another app finds when it has no id to go on at all.
   *
   * Made active, because the window asking is the one about to show it.
   */
  fun ensureTab(id: Long?, url: String): BrowserTab {
    val existing = id?.let(::tab)
    val current = tabs
    return when {
      existing != null -> existing.also { current?.activate(current.indexOfFirst(it.id)) }
      current == null -> BrowserTabs(url, id).also { adopt(it) }.active
      else -> current.add(url, restoredId = id)
    }
  }

  /**
   * Adds [url] as a tab without disturbing which one is active. [BrowserTabs.add] activates what it
   * adds, which is right inside a window that is about to show it — but a tab is opened here to be
   * handed to a window of its own, and the window that asked keeps showing the page it was on until
   * the new one arrives in front of it.
   */
  fun addBackgroundTab(url: String, onEvict: (BrowserTab) -> Unit = {}): BrowserTab {
    val current = tabs ?: return BrowserTabs(url).also(::adopt).active
    val activeId = current.active.id
    val tab = current.add(url, onEvict)
    current.activate(current.indexOfFirst(activeId))
    return tab
  }

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
   * Shrinks every preview except the visible tab's under memory pressure, giving back ~97% of what
   * they hold while leaving something to draw.
   *
   * These used to be dropped outright, which cost more than memory: a tab with no preview has
   * nothing to cover its WebView while it reloads, so coming back to one meant watching it repaint
   * from blank. A thumbnail is a poor picture of a page but a perfectly good screenful of its
   * colours, which is all the cover has to be for the couple of hundred milliseconds it is up.
   *
   * The old bitmap is dropped rather than recycled because a composition may still be drawing it.
   */
  fun trimSnapshots() {
    val current = tabs ?: return
    current.items.forEachIndexed { index, tab ->
      if (index == current.activeIndex) return@forEachIndexed
      val snapshot = tab.snapshot ?: return@forEachIndexed
      // Already trimmed: re-scaling every time the system asks would grind them to nothing.
      if (snapshot.width <= TRIMMED_SNAPSHOT_WIDTH_PX) return@forEachIndexed
      tab.snapshot = snapshot.scaledToWidth(TRIMMED_SNAPSHOT_WIDTH_PX) ?: snapshot
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
  // Refused outright, even for a tab holding nothing: a near-empty capture is the frame between a
  // page's first paint and its content, and storing one is worse than storing none. It becomes the
  // card in the overview and the cover behind a tab switch, so the user taps a white rectangle and
  // then watches it stay white — the flash the cover exists to prevent, served from cache.
  if (snapshot.isMostlyBlank()) return
  tab.snapshot = snapshot
}

/** Asked of the view itself, so every capture path gets the answer as of the moment it captures. */
private fun WebView.isKeyboardVisible(): Boolean =
  ViewCompat.getRootWindowInsets(this)?.isVisible(WindowInsetsCompat.Type.ime()) == true

/**
 * Whether almost every sampled pixel matches the background, which is what a page that has not
 * finished rendering looks like.
 *
 * Deliberately a proportion rather than "is it all one colour": a half-rendered page is rarely
 * uniform — a Google results page paints its logo and header long before any results — and a single
 * differing pixel used to be enough to pass a blank capture as a real one. Sampled finely enough
 * that a loaded page of text lands well under the threshold, which a coarse grid would not.
 */
private fun Bitmap.isMostlyBlank(): Boolean {
  val stepX = (width / BLANK_SAMPLE_STEPS).coerceAtLeast(1)
  val stepY = (height / BLANK_SAMPLE_STEPS).coerceAtLeast(1)
  val background = getPixel(0, 0)
  var sampled = 0
  var matching = 0
  var x = 0
  while (x < width) {
    var y = 0
    while (y < height) {
      sampled++
      if (getPixel(x, y).matchesWithinTolerance(background)) matching++
      y += stepY
    }
    x += stepX
  }
  return sampled == 0 || matching >= sampled * BLANK_UNIFORM_FRACTION
}

/**
 * Compared with a little slack rather than exactly, so that the faint gradients and off-white
 * panels a blank page is often made of still read as background — and so that RGB_565's coarser
 * colour steps do not turn one flat area into several.
 */
private fun Int.matchesWithinTolerance(other: Int): Boolean =
  abs((this shr 16 and 0xFF) - (other shr 16 and 0xFF)) <= COLOR_TOLERANCE &&
    abs((this shr 8 and 0xFF) - (other shr 8 and 0xFF)) <= COLOR_TOLERANCE &&
    abs((this and 0xFF) - (other and 0xFF)) <= COLOR_TOLERANCE

private const val BLANK_SAMPLE_STEPS = 32
private const val COLOR_TOLERANCE = 12

/**
 * How uniform a capture has to be before it counts as blank.
 *
 * Near 1 because blank means one flat colour, and real pages get closer to that than they look:
 * measured over this grid, a dense page of text scores 0.89, a dark article 0.95, and a heading
 * with a line under it 0.98 — while an unpainted capture is uniform by construction. At the 0.95
 * this used to sit at, the cut ran straight through the middle of that range and threw away
 * captures of perfectly good sparse pages. Since the tab keeps its previous preview when one is
 * refused, the cost was a preview stuck several navigations behind, which is far worse than briefly
 * showing a page that was still filling in — that gets replaced by the next capture, whereas
 * staleness does not clear itself. [BrowserTab.pageDrawn] is the real guard against unpainted
 * captures now that it is set at first paint, leaving this as a backstop for hardware-rendered
 * content drawing flat.
 */
private const val BLANK_UNIFORM_FRACTION = 0.995f

/**
 * The colour [webView] is actually painted on, read back off a real draw of it.
 *
 * Needed because CSS cannot be asked. A page that sets no background of its own is painted on a
 * canvas Chromium picks: white normally, but dark for anything opting into `color-scheme: dark` —
 * and `getComputedStyle` reports `rgba(0, 0, 0, 0)` for both, so the two are indistinguishable from
 * script. Assuming white there washed the bars around a dark page white a moment after it rendered.
 *
 * The answer is the most common colour in the draw rather than a corner pixel, which lands on a
 * header as often as on the page. Scaling down first is what makes that work: it averages text into
 * its background, leaving flat areas as the only exactly-repeated colour. A page with no colour
 * that common — a full-bleed photo, a map — returns null so the caller can keep what it has, which
 * is a better answer than the average of a picture.
 */
internal fun sampleDrawnBackgroundColor(webView: WebView): Int? {
  if (webView.width <= 0 || webView.height <= 0) return null
  val scale = (BACKGROUND_SAMPLE_WIDTH_PX.toFloat() / webView.width).coerceAtMost(1f)
  val width = (webView.width * scale).toInt().coerceAtLeast(1)
  val height = (webView.height * scale).toInt().coerceAtLeast(1)
  // ARGB_8888 rather than the snapshots' RGB_565: this colour goes on to fill the bars beside the
  // page, where 565's coarser steps show up as a visible seam against the page's own background.
  val bitmap =
    runCatching {
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
          Canvas(bitmap).apply {
            scale(scale, scale)
            webView.draw(this)
          }
        }
      }
      .getOrNull() ?: return null

  val counts = HashMap<Int, Int>()
  var dominant = 0
  var dominantCount = 0
  for (y in 0 until height) {
    for (x in 0 until width) {
      val pixel = bitmap.getPixel(x, y) or (0xFF shl 24)
      val count = (counts[pixel] ?: 0) + 1
      counts[pixel] = count
      if (count > dominantCount) {
        dominantCount = count
        dominant = pixel
      }
    }
  }
  bitmap.recycle()
  return dominant.takeIf { dominantCount >= width * height * BACKGROUND_DOMINANT_FRACTION }
}

/** Coarse enough that text dissolves into the page, fine enough to still see a header apart. */
private const val BACKGROUND_SAMPLE_WIDTH_PX = 64

/**
 * How much of the page one colour has to cover to count as its background. Low, because the parts
 * of a page that are *not* background — text, images, cards — routinely add up to most of it.
 */
private const val BACKGROUND_DOMINANT_FRACTION = 0.25f

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

/**
 * What a preview is reduced to under memory pressure: about 30 KB rather than 1.3 MB, which is
 * blurry in the overview but still the right shape and colours behind a reloading page.
 */
private const val TRIMMED_SNAPSHOT_WIDTH_PX = 96

/** Null rather than throwing if the allocation fails — the caller keeps what it already had. */
private fun Bitmap.scaledToWidth(targetWidth: Int): Bitmap? {
  val targetHeight = (height.toFloat() * targetWidth / width).toInt().coerceAtLeast(1)
  return runCatching { Bitmap.createScaledBitmap(this, targetWidth, targetHeight, true) }
    .getOrNull()
}
