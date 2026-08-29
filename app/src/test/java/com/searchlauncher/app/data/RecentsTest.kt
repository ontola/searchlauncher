package com.searchlauncher.app.data

import android.graphics.drawable.ColorDrawable
import org.junit.Assert.assertEquals
import org.junit.Test

class RecentsTest {
  private val icon = ColorDrawable(0)

  private fun app(pkg: String) =
    SearchResult.App(
      id = pkg,
      namespace = "apps",
      title = pkg,
      subtitle = null,
      icon = icon,
      packageName = pkg,
    )

  private fun tab(tabId: Long, url: String) =
    SearchResult.BrowserTab(
      id = "browser_tab_$tabId",
      title = url,
      subtitle = "Open tab",
      icon = icon,
      tabId = tabId,
      url = url,
    )

  @Test
  fun `a newer tab sits ahead of an older app`() {
    val settings = app("com.android.settings")
    val merged =
      mergeRecentsByTime(
        apps = listOf(settings),
        appTimes = mapOf("com.android.settings" to 1000L),
        tabs = listOf(TimedRecent(tab(1, "https://example.com"), atMs = 2000L)),
      )

    assertEquals(listOf("browser_tab_1", "com.android.settings"), merged.map { it.result.id })
  }

  @Test
  fun `a newer app sits ahead of an older tab`() {
    val settings = app("com.android.settings")
    val merged =
      mergeRecentsByTime(
        apps = listOf(settings),
        appTimes = mapOf("com.android.settings" to 3000L),
        tabs = listOf(TimedRecent(tab(1, "https://example.com"), atMs = 2000L)),
      )

    assertEquals(listOf("com.android.settings", "browser_tab_1"), merged.map { it.result.id })
  }

  @Test
  fun `tabs keep open order among themselves`() {
    val older = TimedRecent(tab(1, "https://one.example"), atMs = 1000L)
    val newer = TimedRecent(tab(2, "https://two.example"), atMs = 2000L)
    val merged =
      mergeRecentsByTime(apps = emptyList(), appTimes = emptyMap(), tabs = listOf(older, newer))

    assertEquals(listOf("browser_tab_2", "browser_tab_1"), merged.map { it.result.id })
  }

  @Test
  fun `app times resolve by favorite key`() {
    val settings = app("com.android.settings")
    val merged =
      mergeRecentsByTime(
        apps = listOf(settings),
        appTimes = mapOf("apps/com.android.settings" to 5000L),
        tabs = listOf(TimedRecent(tab(1, "https://example.com"), atMs = 1000L)),
      )

    assertEquals("com.android.settings", merged.first().result.id)
  }
}
