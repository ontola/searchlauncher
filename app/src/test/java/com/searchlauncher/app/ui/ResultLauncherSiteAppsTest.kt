package com.searchlauncher.app.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.searchlauncher.app.SearchLauncherApp
import com.searchlauncher.app.data.SearchRepository
import com.searchlauncher.app.data.SearchResult
import com.searchlauncher.app.ui.browser.BrowserTabStore
import com.searchlauncher.app.ui.browser.BrowserTabs
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = SearchLauncherApp::class)
class ResultLauncherSiteAppsTest {
  private val context: Context = ApplicationProvider.getApplicationContext()

  @After
  fun tearDown() {
    BrowserTabStore.clear()
  }

  private fun bookmark(url: String) =
    SearchResult.Content(
      id = "saved_${url.hashCode()}",
      namespace = "web_saved",
      title = "GitHub",
      subtitle = "Bookmark",
      icon = null,
      packageName = "",
      deepLink = url,
    )

  private fun history(url: String) =
    SearchResult.Content(
      id = "web_${url.hashCode()}",
      namespace = "web_bookmarks",
      title = url,
      subtitle = "Browser history",
      icon = null,
      packageName = "",
      deepLink = url,
    )

  @Test
  fun `a pinned bookmark resumes the tab already on that site`() {
    val tabs = BrowserTabs("https://github.com/old")
    tabs.add("https://example.com")
    BrowserTabStore.adopt(tabs)
    val pin = bookmark("https://github.com")
    var openedIndex: Int? = null
    var openedUrl: String? = null
    ResultLauncher(
        context = context,
        searchRepository = mockk<SearchRepository>(relaxed = true),
        scope = CoroutineScope(Dispatchers.Unconfined),
        onOpenInBrowser = { openedUrl = it },
        onOpenBrowserTab = { openedIndex = it },
        treatFavoritedSitesAsApps = { true },
        favoriteResults = { listOf(pin) },
      )
      .launch(pin, reportUsage = false)

    assertEquals(0, openedIndex)
    assertNull(openedUrl)
  }

  @Test
  fun `a different page on the pinned site still opens that page`() {
    BrowserTabStore.adopt(BrowserTabs("https://github.com/foo"))
    val pin = bookmark("https://github.com")
    val other = history("https://github.com/explore")
    var openedIndex: Int? = null
    var openedUrl: String? = null
    ResultLauncher(
        context = context,
        searchRepository = mockk<SearchRepository>(relaxed = true),
        scope = CoroutineScope(Dispatchers.Unconfined),
        onOpenInBrowser = { openedUrl = it },
        onOpenBrowserTab = { openedIndex = it },
        treatFavoritedSitesAsApps = { true },
        favoriteResults = { listOf(pin) },
      )
      .launch(other, reportUsage = false)

    assertEquals("https://github.com/explore", openedUrl)
    assertNull(openedIndex)
  }

  @Test
  fun `turning the setting off keeps exact-page matching`() {
    BrowserTabStore.adopt(BrowserTabs("https://github.com/foo"))
    val pin = bookmark("https://github.com")
    var openedIndex: Int? = null
    var openedUrl: String? = null
    ResultLauncher(
        context = context,
        searchRepository = mockk<SearchRepository>(relaxed = true),
        scope = CoroutineScope(Dispatchers.Unconfined),
        onOpenInBrowser = { openedUrl = it },
        onOpenBrowserTab = { openedIndex = it },
        treatFavoritedSitesAsApps = { false },
        favoriteResults = { listOf(pin) },
      )
      .launch(pin, reportUsage = false)

    assertEquals("https://github.com", openedUrl)
    assertNull(openedIndex)
  }
}
