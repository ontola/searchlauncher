package com.searchlauncher.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchOptionsTest {
  private val shortcuts = DefaultShortcuts.searchShortcuts

  @Test
  fun `defaults are google youtube spotify`() {
    assertEquals(listOf("google", "youtube", "spotify"), SearchOptions.DEFAULT_FAVORITE_IDS)
  }

  @Test
  fun `normalizeId strips namespace and search prefix`() {
    assertEquals("google", SearchOptions.normalizeId("google"))
    assertEquals("google", SearchOptions.normalizeId("search_google"))
    assertEquals("google", SearchOptions.normalizeId("search_shortcuts/google"))
    assertEquals("google", SearchOptions.normalizeId("search_shortcuts/search_google"))
  }

  @Test
  fun `partition puts favorites first and fills with the rest`() {
    val (favorites, extras) = SearchOptions.partition(shortcuts, SearchOptions.DEFAULT_FAVORITE_IDS)

    assertEquals(listOf("google", "youtube", "spotify"), favorites.map { it.id })
    assertTrue(extras.none { it.id in SearchOptions.DEFAULT_FAVORITE_IDS })
    assertEquals(shortcuts.size, favorites.size + extras.size)
    assertTrue(extras.isNotEmpty())
  }

  @Test
  fun `partition skips unknown ids and keeps leftover shortcuts`() {
    val (favorites, extras) =
      SearchOptions.partition(shortcuts, listOf("google", "missing", "youtube"))

    assertEquals(listOf("google", "youtube"), favorites.map { it.id })
    assertTrue(extras.any { it.id == "spotify" })
    assertTrue(extras.none { it.id == "google" || it.id == "youtube" })
  }

  @Test
  fun `partition with empty favorites fills entirely from extras`() {
    val (favorites, extras) = SearchOptions.partition(shortcuts, emptyList())

    assertTrue(favorites.isEmpty())
    assertEquals(shortcuts.map { it.id }, extras.map { it.id })
  }

  @Test
  fun `searchTerm strips an alias prefix`() {
    assertEquals("cats", SearchOptions.searchTerm("y cats", shortcuts))
    assertEquals("cats", SearchOptions.searchTerm("g cats", shortcuts))
    assertEquals("cats", SearchOptions.searchTerm("cats", shortcuts))
  }

  @Test
  fun `searchTerm is empty for a bare alias`() {
    assertEquals("", SearchOptions.searchTerm("y", shortcuts))
    assertEquals("", SearchOptions.searchTerm("y ", shortcuts))
  }

  @Test
  fun `toSearchIntent reuses the result-list identity and letter alias`() {
    val youtube = shortcuts.first { it.id == "youtube" }
    val result = youtube.toSearchIntent()

    assertEquals("youtube", result.id)
    assertEquals("search_shortcuts", result.namespace)
    assertEquals("y", result.trigger)
    assertEquals("YouTube Search", result.title)
    assertEquals(0xFFFF0000L, youtube.color)
  }
}
