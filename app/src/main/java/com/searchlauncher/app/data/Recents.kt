package com.searchlauncher.app.data

/**
 * An item in the favorites-bar recents strip, with the time used to order it against the others.
 */
data class TimedRecent(val result: SearchResult, val atMs: Long)

/**
 * Apps and open tabs share one newest-first strip. [appTimes] is keyed by history id, result id, or
 * favorite key — whichever the caller recorded.
 */
fun mergeRecentsByTime(
  apps: List<SearchResult>,
  appTimes: Map<String, Long>,
  tabs: List<TimedRecent>,
): List<TimedRecent> {
  val timedApps =
    apps.map { result ->
      val atMs =
        appTimes[result.id]
          ?: appTimes[result.favoriteKey]
          ?: appTimes[FavoriteKeys.normalize(result.id)]
          ?: 0L
      TimedRecent(result, atMs)
    }
  return (timedApps + tabs).sortedByDescending { it.atMs }
}

fun applyHistoryLimit(items: List<SearchResult>, historyLimit: Int): List<SearchResult> =
  when {
    historyLimit == 0 -> emptyList()
    historyLimit > 0 -> items.take(historyLimit)
    else -> items
  }
