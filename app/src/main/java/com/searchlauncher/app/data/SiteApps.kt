package com.searchlauncher.app.data

import java.net.URI

/**
 * Treat a pinned bookmark as a site, the way an app favorite is a package.
 *
 * Bookmarks stay exact-URL records for search and titles. Once one is pinned, the favorites bar and
 * its opener use the host (minus a leading `www.`) so extra tabs on that site do not mint extra
 * icons, and tapping the pin resumes the tab that is already there instead of opening the start URL
 * again.
 */
const val TREAT_FAVORITED_SITES_AS_APPS_DEFAULT = true

/** Pages that can be pinned as a site: saved bookmarks and visited history. */
val SearchResult.isWebFavoritePage: Boolean
  get() = namespace == "web_bookmarks" || namespace == "web_saved"

/**
 * Host (+ port when it is not the default) that groups tabs and pins as one site.
 *
 * `www.` is dropped so `www.github.com` and `github.com` are the same app. Subdomains are kept, so
 * `mail.google.com` and `calendar.google.com` stay apart. The port is kept because two servers on
 * one host are two places.
 */
fun siteKey(url: String): String? {
  val uri = runCatching { URI(url.trim()) }.getOrNull() ?: return null
  val host = uri.host?.lowercase()?.removePrefix("www.")?.takeIf { it.isNotEmpty() } ?: return null
  return if (uri.port > 0) "$host:${uri.port}" else host
}

fun SearchResult.pageUrl(): String? =
  when (this) {
    is SearchResult.BrowserTab -> url.takeUnless { it.startsWith("about:") }
    is SearchResult.Content ->
      deepLink?.takeIf { it.startsWith("https://") || it.startsWith("http://") }
    else -> null
  }

fun SearchResult.siteKey(): String? = pageUrl()?.let(::siteKey)

/** True when [result] is the pinned start URL, or (when enabled) any page on that pinned site. */
fun isDisplayedAsFavorite(
  result: SearchResult,
  favorites: List<SearchResult>,
  favoriteIds: Collection<String>,
  treatFavoritedSitesAsApps: Boolean,
): Boolean {
  if (
    treatFavoritedSitesAsApps && (result.isWebFavoritePage || result is SearchResult.BrowserTab)
  ) {
    val site = result.siteKey()
    if (site != null) return favorites.any { it.siteKey() == site }
  }
  return result.favoriteKey in favoriteIds
}

/**
 * Tapping a pin should resume a live tab only when this result *is* that pin. A different history
 * page on the same site still means "open this page".
 */
fun shouldResumeFavoritedSite(
  result: SearchResult,
  favoriteIds: Collection<String>,
  treatFavoritedSitesAsApps: Boolean,
): Boolean =
  treatFavoritedSitesAsApps && result.isWebFavoritePage && result.favoriteKey in favoriteIds

/** One icon per site, first pin wins. Non-web favorites are left as they are. */
fun collapsePinnedSites(favorites: List<SearchResult>, enabled: Boolean): List<SearchResult> {
  if (!enabled) return favorites
  val seen = mutableSetOf<String>()
  return favorites.filter { result ->
    val site = result.siteKey()
    if (site == null) true else seen.add(site)
  }
}

/**
 * Drops later pins for a site already represented earlier in [favoriteIds], so reorder and the
 * stored list agree with [collapsePinnedSites].
 */
fun keysAfterCollapsingSites(
  favoriteIds: List<String>,
  favorites: List<SearchResult>,
): List<String> {
  val byKey = favorites.associateBy { it.favoriteKey }
  val seen = mutableSetOf<String>()
  return favoriteIds.filter { key ->
    val site = byKey[key]?.siteKey()
    if (site == null) true else seen.add(site)
  }
}

fun pinnedFavoritesForSite(
  url: String,
  favorites: List<SearchResult>,
  treatFavoritedSitesAsApps: Boolean,
): List<SearchResult> {
  val site = siteKey(url) ?: return emptyList()
  return if (treatFavoritedSitesAsApps) {
    favorites.filter { it.siteKey() == site }
  } else {
    favorites.filter { it.pageUrl() == url }
  }
}

sealed class PinFavoritePlan {
  data class Toggle(val result: SearchResult) : PinFavoritePlan()

  data class Unpin(val keys: List<String>) : PinFavoritePlan()

  data class BookmarkAndPin(val url: String, val title: String?) : PinFavoritePlan()
}

/**
 * What pinning [result] should do. A tab is never stored as itself: if that site is not pinned yet,
 * save the current page as a bookmark and pin that.
 */
fun pinFavoritePlan(
  result: SearchResult,
  favorites: List<SearchResult>,
  treatFavoritedSitesAsApps: Boolean,
): PinFavoritePlan {
  if (result is SearchResult.BrowserTab) {
    val url = result.pageUrl() ?: return PinFavoritePlan.Toggle(result)
    val existing = pinnedFavoritesForSite(url, favorites, treatFavoritedSitesAsApps)
    return if (existing.isNotEmpty()) {
      PinFavoritePlan.Unpin(existing.map { it.favoriteKey })
    } else {
      PinFavoritePlan.BookmarkAndPin(url, result.title)
    }
  }
  val url = result.pageUrl()
  if (treatFavoritedSitesAsApps && result.isWebFavoritePage && url != null) {
    val existing = pinnedFavoritesForSite(url, favorites, treatFavoritedSitesAsApps = true)
    if (existing.isNotEmpty()) return PinFavoritePlan.Unpin(existing.map { it.favoriteKey })
  }
  return PinFavoritePlan.Toggle(result)
}

fun togglePinnedWebFavorite(
  result: SearchResult,
  favorites: List<SearchResult>,
  repository: FavoritesRepository,
  treatFavoritedSitesAsApps: Boolean,
) {
  when (val plan = pinFavoritePlan(result, favorites, treatFavoritedSitesAsApps)) {
    is PinFavoritePlan.Unpin -> repository.removeKeys(plan.keys)
    is PinFavoritePlan.Toggle -> repository.toggleFavorite(plan.result)
    is PinFavoritePlan.BookmarkAndPin -> {
      // Needs AppSearch; callers that can hit a tab use [SearchRepository.pinOrUnpinFavorite].
    }
  }
}

fun applySiteAppHistoryFilter(
  apps: List<SearchResult>,
  favorites: List<SearchResult>,
  enabled: Boolean,
): List<SearchResult> {
  if (!enabled) return apps
  val pinned = pinnedSiteKeys(favorites)
  if (pinned.isEmpty()) return apps
  return apps.filter { result ->
    val site = result.siteKey()
    site == null || site !in pinned
  }
}

fun applySiteAppTabFilter(
  tabs: List<TimedRecent>,
  favorites: List<SearchResult>,
  enabled: Boolean,
): List<TimedRecent> {
  if (!enabled) return tabs
  val pinned = pinnedSiteKeys(favorites)
  if (pinned.isEmpty()) return tabs
  return tabs.filter { recent ->
    val site = recent.result.siteKey()
    site == null || site !in pinned
  }
}

private fun pinnedSiteKeys(favorites: List<SearchResult>): Set<String> =
  favorites.mapNotNull { it.siteKey() }.toSet()

/** Enough of a tab to choose which one a site pin should resume. */
data class SiteTab(val index: Int, val url: String, val openedAtMs: Long, val active: Boolean)

/**
 * The tab already on [url]'s site, or -1. Prefers the tab the user is looking at, then the one
 * opened last — never navigates that tab back to [url].
 */
fun indexOfTabOnSite(tabs: List<SiteTab>, url: String): Int {
  val site = siteKey(url) ?: return -1
  val matches = tabs.filter { siteKey(it.url) == site }
  if (matches.isEmpty()) return -1
  return (matches.firstOrNull { it.active } ?: matches.maxBy { it.openedAtMs }).index
}
