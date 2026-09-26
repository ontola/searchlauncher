package com.searchlauncher.app.data

/**
 * The query-time favorites bar shows search destinations rather than pinned apps.
 *
 * Favorites are shortcut ids (`google`, `youtube`, …). Unknown or removed ids are skipped. The bar
 * scrolls sideways through every launchable shortcut — pinned ones first, then the rest by usage —
 * and the last cell opens custom shortcut settings.
 */
object SearchOptions {
  /** Key of the trailing settings cell in [queryBar]. */
  const val QUERY_BAR_SETTINGS = "settings"

  /** One cell in the scrolling query-time bar. */
  sealed interface QueryBarEntry {
    data class Option(val shortcut: SearchShortcut) : QueryBarEntry

    data object Settings : QueryBarEntry
  }

  val DEFAULT_FAVORITE_IDS = listOf("google", "youtube", "spotify")

  /** Namespace shortcut results and their usage counts are recorded under. */
  const val NAMESPACE = "search_shortcuts"

  /**
   * Shortcut id from a stored value or a [SearchResult] key, with or without a `search_` prefix.
   */
  fun normalizeId(raw: String): String {
    val id = FavoriteKeys.parse(raw)?.second ?: raw
    return id.removePrefix("search_")
  }

  /**
   * Id usage stats are stored and read under. Indexed results use `search_google`; the query-time
   * bar uses `google`; a typed alias hit uses `shortcut_g`. All three must count as the same
   * shortcut or the bar never sees the usage the results list just recorded.
   */
  fun canonicalId(raw: String, aliasToId: (String) -> String? = { null }): String {
    val id = normalizeId(raw)
    if (id.startsWith("shortcut_")) {
      val alias = id.removePrefix("shortcut_")
      return aliasToId(alias) ?: id
    }
    return id
  }

  /** Same default tilt [SearchRepository.getSearchShortcuts] gives unused Google / Play Store. */
  fun defaultUsageBoost(id: String): Int =
    when (normalizeId(id)) {
      "google" -> 2
      "playstore" -> 1
      else -> 0
    }

  /** Alternate ids that may already be stored for [raw] from older reportUsage call sites. */
  fun usageIdAliases(raw: String, aliasToId: (String) -> String? = { null }): List<String> {
    val canonical = canonicalId(raw, aliasToId)
    return listOf(canonical, raw, normalizeId(raw), "search_$canonical").distinct()
  }

  /**
   * Splits [shortcuts] into pinned favorites (in [favoriteIds] order) and the remaining options
   * that fill unused slots in the bar.
   */
  fun partition(
    shortcuts: List<SearchShortcut>,
    favoriteIds: List<String>,
  ): Pair<List<SearchShortcut>, List<SearchShortcut>> {
    val byId = shortcuts.associateBy { it.id }
    val favorites = favoriteIds.map(SearchOptions::normalizeId).distinct().mapNotNull(byId::get)
    val favoriteSet = favorites.map { it.id }.toSet()
    val extras = shortcuts.filter { it.id !in favoriteSet }
    return favorites to extras
  }

  /**
   * Orders fill slots the same way results do: most-used first (Google / Play Store slightly ahead
   * when unused), then the built-in catalog. Pinned favorites keep the drag order.
   */
  fun byUsage(
    shortcuts: List<SearchShortcut>,
    usageCount: (SearchShortcut) -> Int,
  ): List<SearchShortcut> {
    val counts = shortcuts.associate { it.id to usageCount(it) }
    return rankByUsage(shortcuts, { it.id }, { it.description }) { counts[it] ?: 0 }
  }

  /**
   * The same order [com.searchlauncher.app.data.SearchRepository.getSearchShortcuts] appends onto
   * the results list: most-used first (with the Google / Play Store tilt), then the built-in
   * catalog order. The query-time favorites bar uses this so a tap that moves a shortcut in results
   * also moves it in the bar.
   */
  fun <T> rankByUsage(
    items: List<T>,
    idOf: (T) -> String,
    titleOf: (T) -> String,
    usageCount: (String) -> Int,
  ): List<T> =
    items.sortedWith(
      compareByDescending<T> { usageCount(idOf(it)) + defaultUsageBoost(idOf(it)) }
        .thenBy { DefaultShortcuts.searchShortcutOrder(idOf(it)) }
        .thenBy { titleOf(it) }
    )

  /**
   * Every cell of the query-time bar: pinned favorites in drag order, then the other launchable
   * shortcuts most-used first, then [QueryBarEntry.Settings]. Every launchable shortcut is
   * included.
   */
  fun queryBar(
    shortcuts: List<SearchShortcut>,
    favoriteIds: List<String>,
    usageCount: (SearchShortcut) -> Int,
  ): List<QueryBarEntry> {
    val (favorites, extras) = partition(shortcuts, favoriteIds)
    val ordered = favorites + byUsage(extras, usageCount)
    return ordered.map { QueryBarEntry.Option(it) } + QueryBarEntry.Settings
  }

  /**
   * The term to send to a search option. An alias prefix (`y cats`) is stripped so tapping Google
   * still searches for `cats`, not `y cats`.
   */
  fun searchTerm(query: String, shortcuts: List<SearchShortcut>): String {
    val trimmed = query.trimStart()
    val match =
      shortcuts.firstOrNull { trimmed.startsWith("${it.alias} ", ignoreCase = true) }
        ?: shortcuts.firstOrNull { trimmed.equals(it.alias, ignoreCase = true) }
    return when {
      match == null -> query
      trimmed.equals(match.alias, ignoreCase = true) -> ""
      else -> trimmed.substring(match.alias.length).trimStart()
    }
  }
}
