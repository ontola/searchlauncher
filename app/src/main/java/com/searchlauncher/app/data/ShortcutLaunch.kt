package com.searchlauncher.app.data

import android.app.SearchManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri

/**
 * Opens a search-shortcut URL in a specific app when that app is installed and can handle it.
 *
 * Web templates ([ShortcutAvailability.isWebTemplate]) always have a browser fallback — the
 * launcher carries its own — so a missing app must not hide the shortcut. Launch is the other half:
 * if the user *does* have the app (YouTube for `youtube.com/results`, …), send the query there
 * instead of loading the site in the in-app browser.
 */
object ShortcutLaunch {
  /**
   * An intent that delivers [url] to [packageName], or null if that app is not a viable target and
   * the caller should fall back to the in-app browser (http/https) or a generic view intent.
   *
   * [query] is the unencoded search text, used only if the app does not claim the URL as an app
   * link and instead exposes [Intent.ACTION_SEARCH].
   */
  fun preferredAppIntent(
    packageManager: PackageManager,
    url: String,
    packageName: String?,
    query: String? = null,
  ): Intent? {
    if (!isPreferredAppPackage(packageName)) return null
    val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return null
    if (uri.scheme != "http" && uri.scheme != "https") return null

    val viewIntent =
      Intent(Intent.ACTION_VIEW, uri)
        .setPackage(packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    if (packageManager.resolveActivity(viewIntent, 0) != null) return viewIntent

    val searchQuery =
      query?.takeIf { it.isNotBlank() }
        ?: uri.getQueryParameter("search_query")
        ?: uri.getQueryParameter("q")
    if (searchQuery.isNullOrBlank()) return null

    val searchIntent =
      Intent(Intent.ACTION_SEARCH)
        .setPackage(packageName)
        .putExtra(SearchManager.QUERY, searchQuery)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return searchIntent.takeIf { packageManager.resolveActivity(it, 0) != null }
  }

  /**
   * Same as [preferredAppIntent], but only for content results that came from a search shortcut.
   * Bookmarks and other web hits use placeholder packages such as Chrome and must stay in the
   * in-app browser.
   */
  fun preferredAppIntentForContent(
    packageManager: PackageManager,
    result: SearchResult.Content,
    query: String = "",
  ): Intent? {
    if (result.namespace != SearchOptions.NAMESPACE) return null
    val deepLink = result.deepLink ?: return null
    return preferredAppIntent(packageManager, deepLink, result.packageName, query)
  }

  /**
   * Real app ids look like `com.google.android.youtube`. Placeholders used elsewhere — `android`,
   * MIME types, empty — must not pin the intent, or the browser fallback never runs.
   */
  fun isPreferredAppPackage(packageName: String?): Boolean {
    if (packageName.isNullOrBlank()) return false
    if (packageName == "android") return false
    if ('/' in packageName) return false
    return '.' in packageName
  }
}
