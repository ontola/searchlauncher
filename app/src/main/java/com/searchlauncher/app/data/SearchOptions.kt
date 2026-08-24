package com.searchlauncher.app.data

/**
 * The query-time favorites bar shows search destinations rather than pinned apps.
 *
 * Favorites are shortcut ids (`google`, `youtube`, …). Unknown or removed ids are skipped. Extra
 * shortcuts fill whatever space is left so the row uses the full width.
 */
object SearchOptions {
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
   * Orders the fill slots by how often each option was used, most-used first, so an option migrates
   * toward the divider as the user picks it. The sort is stable, so options nobody has used yet
   * keep their incoming order instead of shuffling on every launch.
   *
   * Pinned favorites are deliberately not sorted this way: their order is the one the user set by
   * dragging, and usage counts must not overrule it.
   */
  fun byUsage(
    shortcuts: List<SearchShortcut>,
    usageCount: (SearchShortcut) -> Int,
  ): List<SearchShortcut> = shortcuts.sortedByDescending(usageCount)

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
