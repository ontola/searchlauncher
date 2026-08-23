package com.searchlauncher.app.data

import android.content.Context
import android.content.pm.LauncherApps
import com.searchlauncher.app.SearchLauncherApp
import com.searchlauncher.app.util.StaticShortcutScanner

/**
 * Builds AppSearch documents for the three flavours of shortcut the launcher indexes:
 * - dynamic/pinned/manifest shortcuts published by other apps via [LauncherApps]
 * - static shortcuts declared in app manifests (scanned by [StaticShortcutScanner])
 * - the launcher's own app actions and user-defined search shortcuts
 *
 * These are pure readers. Persisting the documents (and loading icons when a result is shown) is
 * the caller's responsibility.
 */
class ShortcutIndexer(private val context: Context) {

  /**
   * Reads dynamic/manifest/pinned/cached shortcuts for the given [packages] across all profiles.
   *
   * Returns null if the system shortcut service became unavailable mid-scan (the caller should then
   * abandon the index update), otherwise the collected documents.
   */
  suspend fun buildDynamicDocuments(
    packages: List<String>,
    pauseCheck: suspend () -> Unit,
  ): List<AppSearchDocument>? {
    val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps

    val newShortcuts = mutableListOf<AppSearchDocument>()
    val appNameCache = mutableMapOf<String, String>()

    for (profile in launcherApps.profiles) {
      if (PrivateSpaceProfiles.isPrivate(launcherApps, profile)) continue
      pauseCheck()
      for (packageName in packages) {
        pauseCheck()
        try {
          val query = LauncherApps.ShortcutQuery()
          // Chat apps publish most conversations as cached rather than dynamic — WhatsApp keeps
          // only a handful dynamic and caches the rest — so without FLAG_MATCH_CACHED the majority
          // of a user's conversations never reach the index.
          val cached =
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
              LauncherApps.ShortcutQuery.FLAG_MATCH_CACHED
            } else {
              0
            }
          query.setQueryFlags(
            LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
              LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
              LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED or
              cached
          )
          query.setPackage(packageName)

          val shortcutList =
            try {
              launcherApps.getShortcuts(query, profile) ?: emptyList()
            } catch (e: android.os.DeadObjectException) {
              android.util.Log.w(
                "SearchRepository",
                "System unavailable querying shortcuts for $packageName, skipping",
              )
              return null
            } catch (e: Exception) {
              emptyList()
            }

          for (shortcut in shortcutList) {
            pauseCheck()
            try {
              val shortcutId = "${shortcut.`package`}/${shortcut.id}"
              val name = shortcut.shortLabel?.toString() ?: shortcut.longLabel?.toString() ?: ""
              val appName =
                appNameCache.getOrPut(shortcut.`package`) {
                  try {
                    val appInfo = context.packageManager.getApplicationInfo(shortcut.`package`, 0)
                    context.packageManager.getApplicationLabel(appInfo).toString()
                  } catch (e: Exception) {
                    shortcut.`package`
                  }
                }

              // Icons are loaded lazily when a result is shown. Encoding every shortcut PNG here
              // (chat apps publish hundreds of cached conversations) saturates CPU on the same
              // pool search uses, which is what makes a "background" reindex feel sticky.

              newShortcuts.add(
                AppSearchDocument(
                  namespace = "shortcuts",
                  id = shortcutId,
                  name = name,
                  score = 1,
                  intentUri = "shortcut://${shortcut.`package`}/${shortcut.id}",
                  description = "Shortcut - $appName",
                )
              )
            } catch (e: Exception) {
              // Ignore individual shortcut failures
            }
          }
        } catch (e: Exception) {
          android.util.Log.w(
            "SearchRepository",
            "Failed to query shortcuts for package $packageName",
            e,
          )
        }
      }
    }

    return newShortcuts
  }

  /**
   * Scans static (manifest-declared) shortcuts. When [packageNames] is set, only those packages are
   * parsed — a full APK walk on every package-changed event is what made incremental updates as
   * expensive as a full reindex.
   */
  suspend fun buildStaticDocuments(
    pauseCheck: suspend () -> Unit,
    packageNames: Collection<String>? = null,
  ): List<AppSearchDocument> {
    val shortcuts = StaticShortcutScanner.scan(context, packageNames)
    val docs = mutableListOf<AppSearchDocument>()
    for (s in shortcuts) {
      pauseCheck()
      val appName =
        try {
          val appInfo = context.packageManager.getApplicationInfo(s.packageName, 0)
          context.packageManager.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
          s.packageName
        }

      val shortcutId = "${s.packageName}/${s.id}"

      docs.add(
        AppSearchDocument(
          namespace = "static_shortcuts",
          id = shortcutId,
          name = "$appName: ${s.shortLabel}",
          description = "Shortcut - $appName",
          score = 1,
          intentUri = s.intent.toUri(0),
          iconResId = s.iconResId.toLong(),
        )
      )
    }
    return docs
  }

  /**
   * Builds the launcher's own action shortcuts (settings, actions) and the user's editable search
   * shortcuts. Returns an empty list if the app context is unavailable.
   *
   * App-defined shortcuts use the `app_shortcuts` namespace; search shortcuts use
   * `search_shortcuts`.
   */
  fun buildCustomDocuments(): List<AppSearchDocument> {
    val app = context.applicationContext as? SearchLauncherApp ?: return emptyList()

    // Index app-defined shortcuts (settings, actions)
    val appShortcutDocs =
      DefaultShortcuts.appShortcuts.map { shortcut ->
        AppSearchDocument(
          namespace = "app_shortcuts",
          id = "app_${shortcut.id}",
          name = shortcut.description,
          score = 3,
          description = (shortcut as? AppShortcut.Action)?.aliases,
          intentUri = (shortcut as? AppShortcut.Action)?.intentUri,
          isAction = true,
        )
      }

    // Index user-editable search shortcuts
    val searchShortcutDocs =
      app.searchShortcutRepository.items.value.map { shortcut ->
        AppSearchDocument(
          namespace = "search_shortcuts",
          id = "search_${shortcut.id}",
          name = shortcut.description,
          score = 3,
          description = shortcut.alias,
          intentUri = shortcut.urlTemplate,
          isAction = false,
        )
      }

    return appShortcutDocs + searchShortcutDocs
  }
}
