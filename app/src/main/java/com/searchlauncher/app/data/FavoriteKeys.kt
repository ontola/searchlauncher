package com.searchlauncher.app.data

/**
 * Favorites are stored as `"namespace/id"` keys so items from different indexes never collide.
 *
 * Legacy favorites were bare app package names; [normalize] rewrites those to `apps/<package>`.
 */
object FavoriteKeys {
  private val KNOWN_NAMESPACES =
    setOf(
      "apps",
      "shortcuts",
      "static_shortcuts",
      "search_shortcuts",
      "app_shortcuts",
      "contacts",
      "snippets",
      "web_bookmarks",
      "web_saved",
    )

  fun of(namespace: String, id: String): String = "$namespace/$id"

  fun of(result: SearchResult): String = of(result.namespace, result.id)

  /** Returns namespace to id, or null if [key] is blank. */
  fun parse(key: String): Pair<String, String>? {
    val slash = key.indexOf('/')
    if (slash <= 0 || slash >= key.length - 1) return null
    return key.substring(0, slash) to key.substring(slash + 1)
  }

  /**
   * Ensures [raw] is a namespaced favorite key. Bare ids (historical app favorites) become
   * `apps/<id>`.
   */
  fun normalize(raw: String): String {
    val slash = raw.indexOf('/')
    if (slash > 0) {
      val namespace = raw.substring(0, slash)
      if (namespace in KNOWN_NAMESPACES && slash < raw.length - 1) {
        return raw
      }
    }
    return of("apps", raw)
  }

  /** Package name when [key] refers to an app favorite; otherwise null. */
  fun appPackageName(key: String): String? {
    val (namespace, id) = parse(normalize(key)) ?: return null
    return id.takeIf { namespace == "apps" }
  }
}

/** Stable key used for favorites storage, list identity, and reorder. */
val SearchResult.favoriteKey: String
  get() = FavoriteKeys.of(this)

/** Whether this result can be pinned to the favorites row. */
fun SearchResult.isFavoritable(): Boolean =
  when (this) {
    is SearchResult.App,
    is SearchResult.Contact,
    is SearchResult.Shortcut,
    is SearchResult.Snippet,
    is SearchResult.SearchIntent -> true
    is SearchResult.Content -> namespace in setOf("app_shortcuts", "web_saved", "web_bookmarks")
    // An open tab is a live view of the browser, not a thing to pin: it disappears when closed,
    // which would leave the favorite pointing at nothing.
    is SearchResult.BrowserTab,
    is SearchResult.IndexingIndicator -> false
  }
