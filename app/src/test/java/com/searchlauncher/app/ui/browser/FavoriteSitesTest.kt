package com.searchlauncher.app.ui.browser

import com.searchlauncher.app.data.SearchResult
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class FavoriteSitesTest {
  @After
  fun reset() {
    FavoriteSites.source = { emptyList() }
  }

  private fun pinned(url: String) =
    SearchResult.Content(
      id = url,
      namespace = "web_bookmarks",
      title = url,
      subtitle = null,
      icon = null,
      packageName = "",
      deepLink = url,
    )

  @Test
  fun `a tab anywhere on a pinned site is a favorite`() {
    FavoriteSites.source = { listOf(pinned("https://www.github.com/")) }
    assertTrue(FavoriteSites.covers(BrowserTab("https://github.com/ontola/searchlauncher")))
    assertFalse(FavoriteSites.covers(BrowserTab("https://gitlab.com/")))
    assertFalse(FavoriteSites.covers(BrowserTab("about:blank")))
  }

  @Test
  fun `the tab cap closes ordinary tabs before favorites`() {
    FavoriteSites.source = { listOf(pinned("https://mail.example.com/")) }
    val tabs = BrowserTabs("https://mail.example.com/inbox")
    val evicted = mutableListOf<BrowserTab>()
    repeat(16) { tabs.add("https://news.example.org/$it", onEvict = { evicted += it }) }

    assertEquals(1, evicted.size)
    assertEquals("https://news.example.org/0", evicted.single().url)
    assertEquals("https://mail.example.com/inbox", tabs.items.first().url)
  }
}
