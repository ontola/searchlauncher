package com.searchlauncher.app.ui.browser

import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class BrowserStoragePanelTest {
  @get:Rule val compose = createComposeRule()
  private val instrumentation = InstrumentationRegistry.getInstrumentation()

  private fun seed(domain: String) {
    val done = CountDownLatch(1)
    lateinit var view: WebView
    instrumentation.runOnMainSync {
      view = WebView(instrumentation.targetContext)
      view.settings.javaScriptEnabled = true
      view.settings.domStorageEnabled = true
      view.webViewClient =
        object : WebViewClient() {
          override fun onPageFinished(view: WebView, url: String) {
            view.evaluateJavascript(
              """
            (() => { const r = indexedDB.open('storage-panel-test', 1);
              r.onupgradeneeded = () => r.result.createObjectStore('data');
              r.onsuccess = () => { const db = r.result;
                const t = db.transaction('data', 'readwrite');
                t.objectStore('data').put('x'.repeat(120000), 'payload');
                t.oncomplete = () => { db.close(); document.title = 'seeded'; };
              };
              localStorage.setItem('probe', 'present');
            })()
          """
                .trimIndent(),
              null,
            )
          }
        }
      view.webChromeClient =
        object : android.webkit.WebChromeClient() {
          override fun onReceivedTitle(view: WebView, title: String) {
            if (title == "seeded") done.countDown()
          }
        }
      CookieManager.getInstance().setCookie("https://$domain", "probe=present; Path=/; Secure")
      view.loadDataWithBaseURL(
        "https://$domain/",
        "<html><head></head><body>Storage fixture</body></html>",
        "text/html",
        "UTF-8",
        null,
      )
    }
    assertTrue("Database write completed", done.await(15, TimeUnit.SECONDS))
    instrumentation.runOnMainSync { view.destroy() }
  }

  @Test
  fun reportsRealStorageAndClearsOnlySelectedSite() {
    val store = BrowserStorage()
    seed("storage-one.test")
    seed("storage-two.test")
    try {
      val before = runBlocking(Dispatchers.Main) { store.load() }
      assertTrue(before.any { it.domain == "storage-one.test" && it.bytes > 0 })
      assertTrue(before.any { it.domain == "storage-two.test" && it.bytes > 0 })
      compose.setContent { MaterialTheme { BrowserStoragePanel {} } }
      compose.waitUntil(15_000) {
        compose
          .onAllNodes(androidx.compose.ui.test.hasText("storage-one.test"))
          .fetchSemanticsNodes()
          .isNotEmpty()
      }
      instrumentation.uiAutomation.takeScreenshot().let { bitmap ->
        java.io
          .File(instrumentation.targetContext.cacheDir, "website-storage.png")
          .outputStream()
          .use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
      }
      compose.onNodeWithContentDescription("Clear data for storage-one.test").performClick()
      compose.onNodeWithText("Cancel").performClick()
      assertTrue(
        runBlocking(Dispatchers.Main) { store.load() }
          .any { it.domain == "storage-one.test" && it.bytes > 0 }
      )
      compose.onNodeWithContentDescription("Clear data for storage-one.test").performClick()
      compose.onNodeWithText("Clear data").performClick()
      compose.waitUntil(15_000) {
        compose
          .onAllNodes(androidx.compose.ui.test.hasText("storage-one.test"))
          .fetchSemanticsNodes()
          .isEmpty()
      }
      val after = runBlocking(Dispatchers.Main) { store.load() }
      assertTrue(after.none { it.domain == "storage-one.test" && it.bytes > 0 })
      assertTrue(after.any { it.domain == "storage-two.test" && it.bytes > 0 })
      instrumentation.runOnMainSync {
        assertNull(CookieManager.getInstance().getCookie("https://storage-one.test"))
        assertTrue(
          CookieManager.getInstance()
            .getCookie("https://storage-two.test")
            .contains("probe=present")
        )
      }
    } finally {
      runBlocking(Dispatchers.Main) {
        store.clear("storage-one.test")
        store.clear("storage-two.test")
      }
    }
  }
}
