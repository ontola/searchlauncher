package com.searchlauncher.app.data

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import io.sentry.Sentry

/**
 * Builds AppSearch documents for launchable apps across all user profiles.
 *
 * This is a pure reader: it queries [LauncherApps] and returns documents. Persisting them (and
 * cleaning up zombie entries) is the caller's responsibility.
 */
class AppIndexer(private val context: Context) {

  /**
   * Returns one document per launchable activity across all profiles. [pauseCheck] is invoked
   * between profiles and apps so indexing yields to active searches.
   *
   * When [packageNames] is set, only those packages are queried. An empty filter returns nothing
   * without walking the full catalog.
   */
  /**
   * A cheap summary of the launchable catalog as it stands: which packages there are and what they
   * are called. Null when it cannot be read, which the caller should treat as "no answer" rather
   * than as "nothing changed".
   *
   * Names are part of it, not just the package list, because a name is what search matches on and
   * what the user reads - and an app that renames itself on update (a build that adds a "(debug)"
   * suffix, an app rebranding) keeps its package. Without the names such a change is invisible here
   * and the old name stays in the index until the periodic rebuild comes round hours later.
   *
   * Deliberately does not build documents or touch AppSearch: the point is to answer "is a rebuild
   * worth it" for much less than a rebuild costs.
   */
  fun readFingerprint(): String? {
    return try {
      val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
      val seen = sortedSetOf<String>()
      for (profile in launcherApps.profiles) {
        for (info in launcherApps.getActivityList(null, profile)) {
          seen.add("${info.componentName.packageName}:${info.label}")
        }
      }
      // Sorted before hashing so the same catalog in a different order is the same catalog.
      "${seen.size}/${seen.joinToString("|").hashCode()}"
    } catch (e: Exception) {
      android.util.Log.w("AppIndexer", "Failed to read app fingerprint", e)
      null
    }
  }

  suspend fun buildDocuments(
    pauseCheck: suspend () -> Unit,
    packageNames: Collection<String>? = null,
  ): List<AppSearchDocument> {
    if (packageNames != null && packageNames.isEmpty()) return emptyList()

    val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps

    val apps = mutableListOf<AppSearchDocument>()
    // Apps are addressed by package id everywhere (search, favorites, history, the app list), so we
    // keep one document per package. A package can expose several launcher activities (e.g. Tasker)
    // or appear in multiple profiles, which would otherwise yield colliding ids.
    val seenPackages = mutableSetOf<String>()

    for (profile in launcherApps.profiles) {
      pauseCheck()
      try {
        // fetch activities directly per profile. This is more efficient and safer
        // than getInstalledPackages which is prone to DeadObjectException.
        val activityList =
          if (packageNames == null) {
            launcherApps.getActivityList(null, profile)
          } else {
            packageNames.flatMap { pkg ->
              try {
                launcherApps.getActivityList(pkg, profile)
              } catch (_: Exception) {
                emptyList()
              }
            }
          }

        for (info in activityList) {
          pauseCheck()
          try {
            val appName = info.label.toString()
            val packageName = info.componentName.packageName
            if (!seenPackages.add(packageName)) continue

            val appInfo = info.applicationInfo
            val category =
              when (appInfo.category) {
                ApplicationInfo.CATEGORY_GAME -> "Game"
                ApplicationInfo.CATEGORY_AUDIO -> "Audio"
                ApplicationInfo.CATEGORY_VIDEO -> "Video"
                ApplicationInfo.CATEGORY_IMAGE -> "Image"
                ApplicationInfo.CATEGORY_SOCIAL -> "Social"
                ApplicationInfo.CATEGORY_NEWS -> "News"
                ApplicationInfo.CATEGORY_MAPS -> "Maps"
                ApplicationInfo.CATEGORY_PRODUCTIVITY -> "Productivity"
                else -> "Application"
              }

            apps.add(
              AppSearchDocument(
                namespace = "apps",
                id = packageName,
                name = appName,
                score = 2,
                description = category,
              )
            )
          } catch (e: Exception) {
            // Ignore individual app failures
          }
        }
      } catch (e: android.os.DeadObjectException) {
        android.util.Log.w(
          "SearchRepository",
          "System unavailable querying apps for profile $profile, skipping",
        )
      } catch (e: Exception) {
        android.util.Log.e("SearchRepository", "Error querying apps for profile $profile", e)
        Sentry.captureException(e)
      }
    }

    return apps
  }
}
