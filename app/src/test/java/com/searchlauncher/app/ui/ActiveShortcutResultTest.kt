package com.searchlauncher.app.ui

import com.searchlauncher.app.data.SearchResult
import org.junit.Assert.assertEquals
import org.junit.Test

class ActiveShortcutResultTest {
  private val active =
    SearchResult.Content(
      id = "shortcut_y",
      namespace = "search_shortcuts",
      title = "Search in YouTube",
      subtitle = "Type your query...",
      icon = null,
      packageName = "android",
      deepLink = "https://www.youtube.com/results?search_query=",
    )

  @Test
  fun `empty or stale background results cannot remove active action`() {
    assertEquals(listOf(active), prioritizeActiveShortcut(emptyList(), active))
    val other = active.copy(id = "other", title = "Other result")
    val stale = active.copy(title = "YouTube Search")
    assertEquals(listOf(active, other), prioritizeActiveShortcut(listOf(other, stale), active))
    assertEquals(listOf(other), prioritizeActiveShortcut(listOf(other), null))
  }
}
