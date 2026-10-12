package com.searchlauncher.app.ui.browser

import com.searchlauncher.app.data.SearchResult
import com.searchlauncher.app.data.isWebFavoritePage
import com.searchlauncher.app.data.siteKey

/**
 * Whether a tab shows a site the user pinned, or an installed web app. Those are the pages the
 * browser keeps open the longest: the tab cap closes other tabs first, and Gecko keeps their page
 * at high priority for as long as Android has memory for it.
 */
internal object FavoriteSites {
  /** The launcher's favorites, read live so pinning or unpinning takes effect at once. */
  @Volatile var source: () -> List<SearchResult> = { emptyList() }

  fun covers(tab: BrowserTab): Boolean {
    if (tab.installedApp != null) return true
    val site = siteKey(tab.url) ?: return false
    return source().any { it.isWebFavoritePage && it.siteKey() == site }
  }
}
