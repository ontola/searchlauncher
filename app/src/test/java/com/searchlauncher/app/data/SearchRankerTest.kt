package com.searchlauncher.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchRankerTest {
  private fun doc(namespace: String, namespaceInt: Int, id: String, name: String) =
    name.lowercase().let { lower ->
      val words = lower.split(' ').filter { it.isNotEmpty() }
      SearchableDocument(
        doc = AppSearchDocument(namespace = namespace, id = id, score = 0, name = name),
        nameLower = lower,
        targetWords = words,
        acronym = words.joinToString("") { it.take(1) },
        namespaceInt = namespaceInt,
        normalizedPhone = null,
        charMask = SearchRanker.calculateCharMask(lower),
      )
    }

  private fun rank(
    query: String,
    snapshot: List<SearchableDocument>,
    favoriteKeys: Set<String> = emptySet(),
  ) =
    SearchRanker.rankCandidates(
      query = query,
      snapshot = snapshot,
      includeSearchShortcuts = true,
      usageStats = emptyMap(),
      queryUsageStats = emptyMap(),
      documentByNamespaceAndId = emptyMap(),
      favoriteKeys = favoriteKeys,
    )

  private val favorite = doc("web_saved", 9, "https://atomic.place", "atomic.place")
  private val favoriteKey = FavoriteKeys.of("web_saved", "https://atomic.place")

  /** Lots of browser history and contacts that all contain an "a", ordered ahead of saved sites. */
  private val crowd =
    (0 until 1500).map { doc("web_bookmarks", 3, "h$it", "page about stuff $it") } +
      (0 until 500).map { doc("contacts", 5, "c$it", "anna $it") }

  @Test
  fun `a favorite past a crowd of earlier matches is still scored on one letter`() {
    val snapshot = (crowd + favorite).sortedBy { it.namespaceInt }
    for (query in listOf("a", "at", "ato", "atom")) {
      assertTrue(
        "favorite missing for '$query'",
        rank(query, snapshot, setOf(favoriteKey)).any { it.first === favorite },
      )
    }
  }

  @Test
  fun `a favorite prefix match outranks unpinned apps from the first letter`() {
    val apps =
      listOf(
        doc("apps", 1, "com.amazon", "Amazon"),
        doc("apps", 1, "com.authy", "Authy"),
        doc("apps", 1, "com.atom", "Atomic Mail"),
      )
    val snapshot = (apps + favorite).sortedBy { it.namespaceInt }
    for (query in listOf("a", "ato", "atom")) {
      assertEquals(favorite, rank(query, snapshot, setOf(favoriteKey)).first().first)
    }
  }

  @Test
  fun `a favorite gets no boost for a scattered subsequence match`() {
    val snapshot = listOf(favorite)
    val boosted = rank("apc", snapshot, setOf(favoriteKey)).single().second
    val plain = rank("apc", snapshot).single().second
    assertEquals(plain, boosted)
  }

  @Test
  fun `an app outranks contacts with the same match grade`() {
    val teams = doc("apps", 1, "com.microsoft.teams", "Teams")
    val contacts =
      listOf(
        doc("contacts", 5, "c1", "Team Ontola"),
        doc("contacts", 5, "c2", "Sam Teamleader"),
        doc("contacts", 5, "c3", "Teams Support"),
      )
    val ranked = rank("team", (listOf(teams) + contacts).sortedBy { it.namespaceInt })
    assertEquals(teams, ranked.first().first)
  }

  @Test
  fun `a contact that matches better still outranks an app`() {
    val teams = doc("apps", 1, "com.microsoft.teams", "Teams")
    val contact = doc("contacts", 5, "c1", "Team")
    val ranked = rank("team", listOf(teams, contact))
    assertEquals(contact, ranked.first().first)
  }
}
