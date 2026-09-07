package com.searchlauncher.app.ui.browser

import android.content.Context
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.test.core.app.ApplicationProvider
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class LinkPeekTest {
  @Test
  fun `only web links with a host are previewed`() {
    assertTrue(canPeekLink("https://example.com/page"))
    assertTrue(canPeekLink("http://example.com/page"))
    for (url in
      listOf(
        "mailto:test@example.com",
        "tel:1234",
        "javascript:alert(1)",
        "file:///tmp/a",
        "https:",
        "/relative",
        "intent://app",
      )) {
      assertFalse(url, canPeekLink(url))
    }
  }

  private fun request(url: String, mainFrame: Boolean = true) =
    mockk<WebResourceRequest> {
      every { this@mockk.url } returns Uri.parse(url)
      every { isForMainFrame } returns mainFrame
    }

  @Test
  fun `preview inherits per-site settings and private caching policy`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val store = BrowserSiteSettingsStore(context, privateMode = true)
    store.save(
      "https://example.com",
      BrowserSiteSettings(javaScriptEnabled = false, thirdPartyCookiesEnabled = true),
    )
    val view = preview(context, store)
    try {
      assertEquals("https://example.com", shadowOf(view).lastLoadedUrl)
      assertFalse(view.settings.javaScriptEnabled)
      assertEquals(WebSettings.LOAD_NO_CACHE, view.settings.cacheMode)
      assertFalse(view.settings.allowFileAccess)
      assertFalse(view.settings.allowContentAccess)
      assertFalse(view.settings.javaScriptCanOpenWindowsAutomatically)
      assertFalse(
        view.webViewClient.shouldOverrideUrlLoading(view, request("https://other.example/"))
      )
      assertTrue(view.settings.javaScriptEnabled)
    } finally {
      view.destroy()
    }
  }

  @Test
  fun `external navigation stays in preview and reports how to continue`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    var error: String? = null
    val view = preview(context, BrowserSiteSettingsStore(context, true), onError = { error = it })
    try {
      assertTrue(view.webViewClient.shouldOverrideUrlLoading(view, request("intent://app")))
      assertNotNull(error)
      assertEquals("https://example.com", shadowOf(view).lastLoadedUrl)
    } finally {
      view.destroy()
    }
  }

  @Test
  fun `same-page navigation updates the action URL without resetting the title`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    var current = "https://example.com"
    var pageStarts = 0
    val view =
      createLinkPeekWebView(
        context,
        current,
        true,
        BrowserSiteSettingsStore(context, true),
        { false },
        { pageStarts++ },
        {},
        { current = it },
        {},
        {},
      )
    try {
      view.webViewClient.doUpdateVisitedHistory(view, "https://example.com/next", false)
      assertEquals("https://example.com/next", current)
      assertEquals(0, pageStarts)
    } finally {
      view.destroy()
    }
  }

  private fun preview(
    context: Context,
    store: BrowserSiteSettingsStore,
    onError: (String) -> Unit = {},
  ): WebView =
    createLinkPeekWebView(
      context,
      "https://example.com",
      true,
      store,
      { false },
      {},
      {},
      {},
      {},
      onError,
    )
}
