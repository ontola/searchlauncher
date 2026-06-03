package com.searchlauncher.app.data

import android.content.Context
import android.content.pm.LauncherApps
import com.searchlauncher.app.SearchLauncherApp
import com.searchlauncher.app.util.StaticShortcutScanner
import io.sentry.Sentry

/**
 * Builds AppSearch documents for the three flavours of shortcut the launcher indexes:
 * - dynamic/pinned/manifest shortcuts published by other apps via [LauncherApps]
 * - static shortcuts declared in app manifests (scanned by [StaticShortcutScanner])
 * - the launcher's own app actions and user-defined search shortcuts
 *
 * These are pure readers (aside from caching icons to disk as a side effect). Persisting the
 * documents is the caller's responsibility.
 */
class ShortcutIndexer(private val context: Context, private val iconRepository: IconRepository) {

  /**
   * Reads dynamic/manifest/pinned shortcuts for the given [packages] across all profiles and caches
   * their icons to disk.
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
      pauseCheck()
      for (packageName in packages) {
        pauseCheck()
        try {
          val query = LauncherApps.ShortcutQuery()
          query.setQueryFlags(
            LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
              LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
              LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED
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

              try {
                val icon =
                  launcherApps.getShortcutIconDrawable(
                    shortcut,
                    context.resources.displayMetrics.densityDpi,
                  )
                if (icon != null) {
                  iconRepository.saveToDisk("shortcut_$shortcutId", icon, force = true)
                }
              } catch (e: Exception) {
                // Ignore icon loading failures
              }

              val pkg = shortcut.`package`
              if (!iconRepository.hasOnDisk("appicon_$pkg")) {
                try {
                  val appIcon = context.packageManager.getApplicationIcon(pkg)
                  iconRepository.saveToDisk("appicon_$pkg", appIcon, force = true)
                } catch (e: Exception) {
                  // Ignore icon loading failures
                }
              }

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

  /** Scans static (manifest-declared) shortcuts, caching their icons and app icons to disk. */
  suspend fun buildStaticDocuments(pauseCheck: suspend () -> Unit): List<AppSearchDocument> {
    val shortcuts = StaticShortcutScanner.scan(context)
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

      // Pre-load and cache static shortcut icon
      val shortcutId = "${s.packageName}/${s.id}"
      try {
        if (s.iconResId > 0) {
          val res = context.packageManager.getResourcesForApplication(s.packageName)
          val icon = res.getDrawable(s.iconResId.toInt(), null)
          if (icon != null) {
            iconRepository.saveToDisk("static_shortcut_$shortcutId", icon, force = true)
          }
        }
      } catch (e: Exception) {
        // Ignore icon loading failures
        Sentry.captureException(e)
      }

      // Pre-load and cache app icon
      if (!iconRepository.hasOnDisk("appicon_${s.packageName}")) {
        try {
          val appIcon = context.packageManager.getApplicationIcon(s.packageName)
          iconRepository.saveToDisk("appicon_${s.packageName}", appIcon, force = true)
        } catch (e: Exception) {
          // Ignore app icon loading failures
          Sentry.captureException(e)
        }
      }

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
