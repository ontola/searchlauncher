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
      items.removeAt(removableIndex).snapshot?.recycle()
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

  fun adjacent(direction: Int): BrowserTab? = items.getOrNull(activeIndex + direction)

  fun closeActive(): BrowserTab? {
    if (items.size == 1) return null
    val removed = items.removeAt(activeIndex)
    removed.snapshot?.recycle()
    activeIndex = activeIndex.coerceAtMost(items.lastIndex)
    return active
  }

  companion object {
    private const val MAX_TABS = 16
  }
}

internal fun saveWebViewIntoTab(webView: WebView, tab: BrowserTab) {
  tab.url = webView.url ?: tab.url
  tab.title = webView.title
  tab.webViewState = Bundle().also(webView::saveState)
  captureWebViewSnapshot(webView)?.let { snapshot ->
    tab.snapshot?.takeUnless(Bitmap::isRecycled)?.recycle()
    tab.snapshot = snapshot
  }
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
