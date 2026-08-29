package com.searchlauncher.app.ui.browser

import androidx.test.core.app.ApplicationProvider
import com.searchlauncher.app.SearchLauncherApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = SearchLauncherApp::class)
class BrowserTabRecentsTest {
  @Test
  fun `blank tabs are not offered as recents`() {
    val context = ApplicationProvider.getApplicationContext<SearchLauncherApp>()
    assertNull(BrowserTab("about:blank").toSearchResult(context))
  }

  @Test
  fun `an open page becomes a browser tab result with its url`() {
    val context = ApplicationProvider.getApplicationContext<SearchLauncherApp>()
    val tab = BrowserTab("https://example.com/page")
    tab.title = "Example"
    val result = tab.toSearchResult(context)!!

    assertEquals("Example", result.title)
    assertEquals("Open tab", result.subtitle)
    assertEquals(tab.id, result.tabId)
    assertEquals("https://example.com/page", result.url)
  }
}
