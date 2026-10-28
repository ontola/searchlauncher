package com.searchlauncher.app.data

import android.app.SearchManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build

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
   *
   * [otherApps] are further apps the shortcut is known to search inside (Maps for
   * `google.com/maps`, …). They get the URL only when they claim it themselves: unlike
   * [packageName], they are never sent a bare [Intent.ACTION_SEARCH], since some of them may answer
   * that with something other than this shortcut's search.
   */
  fun preferredAppIntent(
    packageManager: PackageManager,
    url: String,
    packageName: String?,
    query: String? = null,
    otherApps: List<String> = emptyList(),
  ): Intent? {
    val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return null
    if (uri.scheme != "http" && uri.scheme != "https") return null
    if (!isPreferredAppPackage(packageName)) return otherAppIntent(packageManager, uri, otherApps)

    val viewIntent = viewIntent(uri, packageName)
    if (packageManager.resolveActivity(viewIntent, 0) != null) return viewIntent

    val searchQuery =
      query?.takeIf { it.isNotBlank() }
        ?: uri.getQueryParameter("search_query")
        ?: uri.getQueryParameter("q")
    if (searchQuery.isNullOrBlank()) return otherAppIntent(packageManager, uri, otherApps)

    val searchIntent =
      Intent(Intent.ACTION_SEARCH)
        .setPackage(packageName)
        .putExtra(SearchManager.QUERY, searchQuery)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return searchIntent.takeIf { packageManager.resolveActivity(it, 0) != null }
      ?: otherAppIntent(packageManager, uri, otherApps)
  }

  private fun otherAppIntent(
    packageManager: PackageManager,
    uri: Uri,
    apps: List<String>,
  ): Intent? =
    apps
      .filter(::isPreferredAppPackage)
      .map { viewIntent(uri, it) }
      .firstOrNull { packageManager.resolveActivity(it, 0) != null }

  private fun viewIntent(uri: Uri, packageName: String?): Intent =
    Intent(Intent.ACTION_VIEW, uri).setPackage(packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

  /**
   * The user's default non-browser app for [url], for shortcuts that name no app or whose named
   * apps are not installed (a custom template for a site whose app the user has, …). Null below
   * Android 11, which cannot exclude browsers, so the caller keeps the page in its own browser.
   */
  fun defaultAppIntent(url: String): Intent? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
    val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return null
    if (uri.scheme != "http" && uri.scheme != "https") return null
    return Intent(Intent.ACTION_VIEW, uri)
      .addCategory(Intent.CATEGORY_BROWSABLE)
      .addFlags(
        Intent.FLAG_ACTIVITY_NEW_TASK or
          Intent.FLAG_ACTIVITY_REQUIRE_NON_BROWSER or
          Intent.FLAG_ACTIVITY_REQUIRE_DEFAULT
      )
  }

  /**
   * Same as [preferredAppIntent], but only for content results that came from a search shortcut
   * (`imdb matrix`, a suggestion under it, …). Bookmarks and other web hits use placeholder
   * packages such as Chrome and must stay in the in-app browser.
   *
   * [shortcuts] lets the result find its shortcut again, so the apps it lists get the URL too, and
   * failing those the user's default app for it. Callers must still fall back to the browser if
   * starting the returned intent fails.
   */
  fun preferredAppIntentForContent(
    packageManager: PackageManager,
    result: SearchResult.Content,
    query: String = "",
    shortcuts: List<SearchShortcut> = emptyList(),
  ): Intent? {
    if (result.namespace != SearchOptions.NAMESPACE) return null
    val deepLink = result.deepLink ?: return null
    val shortcut = shortcutForResultId(result.id, shortcuts)
    return preferredAppIntent(
      packageManager,
      deepLink,
      result.packageName,
      query,
      otherApps = shortcut?.iconPackages.orEmpty(),
    ) ?: shortcut?.let { defaultAppIntent(deepLink) }
  }

  /**
   * The shortcut behind a result id built by the search list: `shortcut_<alias>` for the shortcut
   * itself and `suggestion_<alias>_<text>` for a suggestion under it. The longest matching alias
   * wins, since an alias may itself contain an underscore.
   */
  fun shortcutForResultId(id: String, shortcuts: List<SearchShortcut>): SearchShortcut? =
    shortcuts
      .filter { id == "shortcut_${it.alias}" || id.startsWith("suggestion_${it.alias}_") }
      .maxByOrNull { it.alias.length }

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
