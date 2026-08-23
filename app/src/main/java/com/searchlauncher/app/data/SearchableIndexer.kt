package com.searchlauncher.app.data

import android.app.SearchManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/**
 * Discovers activities that accept [Intent.ACTION_SEARCH] and builds index documents for them.
 *
 * Two official surfaces are merged and de-duplicated by component:
 * - [SearchManager.getSearchablesInGlobalSearch] (apps that opted into global search)
 * - [PackageManager.queryIntentActivities] for [Intent.ACTION_SEARCH]
 *
 * These are not in-app content indexes. They are destinations that will open the app's own search
 * UI with [SearchManager.QUERY] filled in when the user types a query after the app name.
 */
class SearchableIndexer(private val context: Context) {

  /**
   * Cheap summary of which searchable activities exist and what they are called. Null when the
   * catalog cannot be read, which the caller should treat as "no answer" rather than "nothing
   * changed".
   */
  fun readFingerprint(): String? {
    return try {
      val discovery = discover(packageNames = null)
      if (!discovery.succeeded) return null
      val seen = discovery.entries.map { "${it.component.flattenToString()}:${it.label}" }.sorted()
      "${seen.size}/${seen.joinToString("|").hashCode()}"
    } catch (e: Exception) {
      android.util.Log.w(TAG, "Failed to read searchable fingerprint", e)
      null
    }
  }

  /**
   * Returns one document per searchable activity. [pauseCheck] is invoked between entries so
   * indexing yields to active searches.
   *
   * When [packageNames] is set, only those packages are queried. An empty filter returns nothing
   * without walking the catalog. Returns null when discovery failed, so the caller can leave the
   * existing index alone instead of wiping it.
   */
  suspend fun buildDocuments(
    pauseCheck: suspend () -> Unit,
    packageNames: Collection<String>? = null,
  ): List<AppSearchDocument>? {
    if (packageNames != null && packageNames.isEmpty()) return emptyList()

    val discovery = discover(packageNames)
    if (!discovery.succeeded) return null

    val docs = ArrayList<AppSearchDocument>(discovery.entries.size)
    for (entry in discovery.entries) {
      pauseCheck()
      docs.add(documentFrom(entry))
    }
    return docs
  }

  internal fun documentFrom(entry: SearchableActivity): AppSearchDocument {
    return AppSearchDocument(
      namespace = NAMESPACE,
      id = entry.component.flattenToString(),
      name = entry.label,
      score = 1,
      intentUri = searchIntent(entry.component, query = "").toUri(Intent.URI_INTENT_SCHEME),
      description = entry.hint?.takeIf { it.isNotBlank() } ?: "Search in ${entry.label}",
    )
  }

  private fun discover(packageNames: Collection<String>?): Discovery {
    val searchManager = context.getSystemService(Context.SEARCH_SERVICE) as? SearchManager
    val pm = context.packageManager
    val seen = LinkedHashMap<String, SearchableActivity>()
    var succeeded = false

    fun add(component: ComponentName?, info: android.app.SearchableInfo?) {
      if (component == null) return
      if (component.packageName == context.packageName) return
      if (packageNames != null && component.packageName !in packageNames) return
      val key = component.flattenToString()
      if (key in seen) return
      val label = labelFor(pm, component) ?: return
      seen[key] = SearchableActivity(component, label, hintFor(pm, info))
    }

    if (searchManager != null) {
      try {
        for (info in searchManager.searchablesInGlobalSearch) {
          add(info.searchActivity, info)
        }
        succeeded = true
      } catch (e: Exception) {
        android.util.Log.w(TAG, "Failed reading global searchables", e)
      }
    }

    try {
      val intent = Intent(Intent.ACTION_SEARCH)
      val flags = PackageManager.MATCH_ALL or PackageManager.GET_META_DATA
      val resolves =
        if (packageNames == null) {
          pm.queryIntentActivities(intent, flags)
        } else {
          packageNames.flatMap { pkg ->
            try {
              pm.queryIntentActivities(Intent(intent).setPackage(pkg), flags)
            } catch (_: Exception) {
              emptyList()
            }
          }
        }
      for (resolve in resolves) {
        val activity = resolve.activityInfo ?: continue
        val component = ComponentName(activity.packageName, activity.name)
        val info =
          try {
            searchManager?.getSearchableInfo(component)
          } catch (_: Exception) {
            null
          }
        add(component, info)
      }
      succeeded = true
    } catch (e: Exception) {
      android.util.Log.w(TAG, "Failed querying ACTION_SEARCH activities", e)
    }

    return Discovery(seen.values.toList(), succeeded)
  }

  private fun labelFor(pm: PackageManager, component: ComponentName): String? {
    return try {
      val info = pm.getApplicationInfo(component.packageName, 0)
      pm.getApplicationLabel(info).toString().trim().takeIf { it.isNotEmpty() }
    } catch (_: Exception) {
      null
    }
  }

  private fun hintFor(pm: PackageManager, info: android.app.SearchableInfo?): String? {
    if (info == null) return null
    val resId = info.settingsDescriptionId
    if (resId == 0) return null
    return try {
      pm
        .getResourcesForApplication(info.searchActivity.packageName)
        .getString(resId)
        .trim()
        .takeIf { it.isNotEmpty() }
    } catch (_: Exception) {
      null
    }
  }

  private data class Discovery(val entries: List<SearchableActivity>, val succeeded: Boolean)

  companion object {
    const val NAMESPACE = "searchables"
    private const val TAG = "SearchableIndexer"

    fun searchIntent(component: ComponentName, query: String): Intent {
      return Intent(Intent.ACTION_SEARCH).apply {
        this.component = component
        putExtra(SearchManager.QUERY, query)
      }
    }

    /**
     * If [query] is [label] or starts with `[label] `, returns the remainder (possibly empty).
     * Otherwise null — the query is not a searchable prefix for this label.
     */
    fun queryAfterLabel(query: String, label: String): String? {
      val q = query.trim()
      val l = label.trim()
      if (l.isEmpty() || q.length < l.length) return null
      if (!q.regionMatches(0, l, 0, l.length, ignoreCase = true)) return null
      if (q.length == l.length) return ""
      if (!q[l.length].isWhitespace()) return null
      return q.substring(l.length).trim()
    }

    fun componentFromId(id: String): ComponentName? = ComponentName.unflattenFromString(id)
  }
}

data class SearchableActivity(val component: ComponentName, val label: String, val hint: String?)
