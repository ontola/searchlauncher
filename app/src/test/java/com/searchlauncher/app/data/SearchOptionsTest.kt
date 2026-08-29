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
  fun `byUsage puts the most used option first`() {
    val counts = mapOf("spotify" to 2, "google" to 9, "youtube" to 5)
    val ordered = SearchOptions.byUsage(shortcuts) { counts[it.id] ?: 0 }

    assertEquals(listOf("google", "youtube", "spotify"), ordered.take(3).map { it.id })
  }

  @Test
  fun `byUsage applies the same unused tilt as results`() {
    val ordered = SearchOptions.byUsage(shortcuts) { 0 }

    assertEquals("google", ordered[0].id)
    assertEquals("playstore", ordered[1].id)
  }

  @Test
  fun `canonicalId collapses indexed and alias result ids`() {
    assertEquals("google", SearchOptions.canonicalId("google"))
    assertEquals("google", SearchOptions.canonicalId("search_google"))
    assertEquals("google", SearchOptions.canonicalId("search_shortcuts/google"))
    assertEquals(
      "google",
      SearchOptions.canonicalId("shortcut_g") { alias -> if (alias == "g") "google" else null },
    )
  }

  @Test
  fun `usage aliases include the indexed search_ id the results list records`() {
    val aliases = SearchOptions.usageIdAliases("google")
    assertTrue("google" in aliases)
    assertTrue("search_google" in aliases)
  }

  @Test
  fun `rankByUsage matches results when usage was stored under search_google`() {
    val extras = SearchOptions.partition(shortcuts, SearchOptions.DEFAULT_FAVORITE_IDS).second
    val stored = mapOf("search_wikipedia" to 8, "search_bing" to 3)
    val ranked =
      SearchOptions.rankByUsage(extras, { it.id }, { it.description }) { id ->
        SearchOptions.usageIdAliases(id).maxOf { stored[it] ?: 0 }
      }

    assertEquals("wikipedia", ranked.first().id)
    assertEquals("bing", ranked[1].id)
  }

  @Test
  fun `byUsage leaves pinned favorites alone`() {
    val (favorites, extras) = SearchOptions.partition(shortcuts, SearchOptions.DEFAULT_FAVORITE_IDS)
    val counts = extras.associate { it.id to 0 } + mapOf(favorites.last().id to 99)
    val ranked = SearchOptions.byUsage(extras) { counts[it.id] ?: 0 }

    // Only the fill slots are reordered; the pinned row keeps the order the user dragged.
    assertEquals(listOf("google", "youtube", "spotify"), favorites.map { it.id })
    assertTrue(ranked.none { it.id in SearchOptions.DEFAULT_FAVORITE_IDS })
  }

  @Test
  fun `namespace matches the one results are indexed under`() {
    assertEquals(SearchOptions.NAMESPACE, shortcuts.first().toSearchIntent().namespace)
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
