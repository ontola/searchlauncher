package com.searchlauncher.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SiteAppsTest {
  private fun bookmark(id: String, url: String, title: String = url) =
    SearchResult.Content(
      id = id,
      namespace = "web_saved",
      title = title,
      subtitle = "Bookmark",
      icon = null,
      packageName = "",
      deepLink = url,
    )

  private fun history(id: String, url: String) =
    SearchResult.Content(
      id = id,
      namespace = "web_bookmarks",
      title = url,
      subtitle = "Browser history",
      icon = null,
      packageName = "",
      deepLink = url,
    )

  private fun tab(tabId: Long, url: String) =
    SearchResult.BrowserTab(
      id = "browser_tab_$tabId",
      title = url,
      subtitle = "Open tab",
      icon = null,
      tabId = tabId,
      url = url,
    )

  @Test
  fun `www and case do not split a site`() {
    assertEquals("github.com", siteKey("https://www.GitHub.com/foo"))
    assertEquals("github.com", siteKey("https://github.com/bar"))
    assertEquals(siteKey("https://www.example.com/a"), siteKey("http://example.com/b"))
  }

  @Test
  fun `subdomains and ports stay distinct`() {
    assertEquals("mail.google.com", siteKey("https://mail.google.com/mail"))
    assertEquals("calendar.google.com", siteKey("https://calendar.google.com"))
    assertEquals("example.com:8080", siteKey("http://example.com:8080/"))
    assertTrue(siteKey("http://example.com:8080/") != siteKey("http://example.com/"))
  }

  @Test
  fun `blank and about pages have no site`() {
    assertNull(siteKey("about:blank"))
    assertNull(siteKey("not a url"))
    assertNull(tab(1, "about:blank").siteKey())
  }

  @Test
  fun `collapse keeps the first pin per site`() {
    val githubHome = bookmark("saved_1", "https://github.com", "GitHub")
    val githubPr = bookmark("saved_2", "https://github.com/org/repo/pull/1", "PR")
    val maps = bookmark("saved_3", "https://www.google.com/maps", "Maps")
    val collapsed = collapsePinnedSites(listOf(githubHome, githubPr, maps), enabled = true)

    assertEquals(listOf("saved_1", "saved_3"), collapsed.map { it.id })
    assertEquals(
      listOf(githubHome, githubPr, maps),
      collapsePinnedSites(listOf(githubHome, githubPr, maps), enabled = false),
    )
  }

  @Test
  fun `storage keys drop later pins for the same site`() {
    val githubHome = bookmark("saved_1", "https://github.com")
    val githubPr = bookmark("saved_2", "https://www.github.com/foo")
    val keys =
      keysAfterCollapsingSites(
        favoriteIds = listOf("apps/phone", githubHome.favoriteKey, githubPr.favoriteKey),
        favorites = listOf(githubHome, githubPr),
      )
    assertEquals(listOf("apps/phone", githubHome.favoriteKey), keys)
  }

  @Test
  fun `a pin resumes only when that bookmark is the favorite`() {
    val pin = bookmark("saved_1", "https://github.com")
    val otherPage = history("web_2", "https://github.com/explore")
    val ids = listOf(pin.favoriteKey)

    assertTrue(shouldResumeFavoritedSite(pin, ids, treatFavoritedSitesAsApps = true))
    assertFalse(shouldResumeFavoritedSite(otherPage, ids, treatFavoritedSitesAsApps = true))
    assertFalse(shouldResumeFavoritedSite(pin, ids, treatFavoritedSitesAsApps = false))
  }

  @Test
  fun `star is site-wide when treating pins as apps`() {
    val pin = bookmark("saved_1", "https://github.com")
    val otherPage = history("web_2", "https://github.com/explore")
    val openTab = tab(9, "https://github.com/settings")
    val favorites = listOf(pin)
    val ids = listOf(pin.favoriteKey)

    assertTrue(isDisplayedAsFavorite(otherPage, favorites, ids, treatFavoritedSitesAsApps = true))
    assertTrue(isDisplayedAsFavorite(openTab, favorites, ids, treatFavoritedSitesAsApps = true))
    assertFalse(isDisplayedAsFavorite(otherPage, favorites, ids, treatFavoritedSitesAsApps = false))
  }

  @Test
  fun `recents hide tabs and history on a pinned site`() {
    val pin = bookmark("saved_1", "https://github.com")
    val otherHistory = history("web_2", "https://github.com/explore")
    val maps = history("web_3", "https://maps.example")
    val githubTab = TimedRecent(tab(1, "https://github.com/foo"), atMs = 2000L)
    val mapsTab = TimedRecent(tab(2, "https://maps.example"), atMs = 3000L)

    assertEquals(
      listOf("web_3"),
      applySiteAppHistoryFilter(listOf(otherHistory, maps), listOf(pin), enabled = true).map {
        it.id
      },
    )
    assertEquals(
      listOf("browser_tab_2"),
      applySiteAppTabFilter(listOf(githubTab, mapsTab), listOf(pin), enabled = true).map {
        it.result.id
      },
    )
    assertEquals(
      listOf("browser_tab_1", "browser_tab_2"),
      applySiteAppTabFilter(listOf(githubTab, mapsTab), listOf(pin), enabled = false).map {
        it.result.id
      },
    )
  }

  @Test
  fun `site tab picker prefers the active tab then the newest`() {
    val tabs =
      listOf(
        SiteTab(index = 0, url = "https://github.com/old", openedAtMs = 1000L, active = false),
        SiteTab(index = 1, url = "https://example.com", openedAtMs = 4000L, active = true),
        SiteTab(index = 2, url = "https://www.github.com/new", openedAtMs = 3000L, active = false),
      )
    assertEquals(2, indexOfTabOnSite(tabs, "https://github.com"))
    assertEquals(
      0,
      indexOfTabOnSite(
        listOf(
          SiteTab(index = 0, url = "https://github.com/old", openedAtMs = 1000L, active = true),
          SiteTab(index = 2, url = "https://github.com/new", openedAtMs = 3000L, active = false),
        ),
        "https://github.com/settings",
      ),
    )
    assertEquals(-1, indexOfTabOnSite(tabs, "https://news.example"))
  }
}
