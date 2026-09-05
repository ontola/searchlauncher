package com.searchlauncher.app.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Repository for user-editable search shortcuts. Users can customize the alias/trigger for each
 * shortcut.
 */
class SearchShortcutRepository(context: Context) {
  private val appContext = context.applicationContext
  private val prefs: SharedPreferences =
    appContext.getSharedPreferences(Prefs.SearchShortcuts.FILE, Context.MODE_PRIVATE)

  private val _items = MutableStateFlow<List<SearchShortcut>>(emptyList())
  /** Every shortcut, including ones with nothing to open. Settings manages this list. */
  val items: StateFlow<List<SearchShortcut>> = _items

  private val _launchable = MutableStateFlow<List<SearchShortcut>>(emptyList())
  /**
   * The shortcuts worth offering — [items] minus those pointing at an app that is not installed.
   * Everywhere a shortcut is presented as something to pick should read this instead of [items].
   */
  val launchable: StateFlow<List<SearchShortcut>> = _launchable

  init {
    loadItems()
  }

  /**
   * Recomputes [launchable]. Called whenever the shortcuts change, and by [SearchRepository] when
   * packages are installed or removed, since that is the other half of the answer.
   */
  fun refreshAvailability() {
    val packageManager = appContext.packageManager
    _launchable.value = _items.value.filter { ShortcutAvailability.isAvailable(packageManager, it) }
  }

  private fun loadItems() {
    val json = prefs.getString(Prefs.SearchShortcuts.SHORTCUTS, null)
    if (json == null) {
      // First run, load defaults
      resetToDefaults()
      return
    }

    val persistedItems =
      try {
        val jsonArray = JSONArray(json)
        List(jsonArray.length()) { i ->
          val obj = jsonArray.getJSONObject(i)
          SearchShortcut(
            id = obj.getString("id"),
            alias = obj.getString("alias"),
            urlTemplate = obj.getString("urlTemplate"),
            description = obj.getString("description"),
            packageName = obj.optString("packageName").takeIf { it.isNotEmpty() },
            suggestionUrl = obj.optString("suggestionUrl").takeIf { it.isNotEmpty() },
            color = if (obj.has("color")) obj.getLong("color") else null,
            shortLabel = obj.optString("shortLabel").takeIf { it.isNotEmpty() },
          )
        }
      } catch (e: Exception) {
        DefaultShortcuts.searchShortcuts // Fallback to defaults
      }

    // Merge in any new default shortcuts that are missing from persisted items
    val defaults = DefaultShortcuts.searchShortcuts
    val missingDefaults = defaults.filter { default -> persistedItems.none { it.id == default.id } }
    val migrated = migratePersistedShortcuts(persistedItems)

    if (missingDefaults.isNotEmpty() || migrated != persistedItems) {
      saveItems(migrated + missingDefaults)
    } else {
      _items.value = persistedItems
      refreshAvailability()
    }
  }

  /**
   * Rewrites stock shortcuts that a previous version persisted, without touching ones the user
   * edited themselves.
   *
   * URL templates are matched against [SUPERSEDED_TEMPLATES] so only a shortcut still carrying the
   * old default is moved forward. Package names are filled in when the default now names an app
   * (YouTube → `com.google.android.youtube`) and this copy still has the stock URL with no package
   * of its own — that is how existing installs pick up "open in the app if installed" without a
   * Reset Defaults.
   *
   * New ids arrive through [missingDefaults] instead; this is for ids that already exist and would
   * otherwise keep the stale data forever.
   */
  private fun migratePersistedShortcuts(items: List<SearchShortcut>): List<SearchShortcut> {
    val defaultsById = DefaultShortcuts.searchShortcuts.associateBy { it.id }
    return items.map { item ->
      var updated = item
      val replacement = SUPERSEDED_TEMPLATES[item.id to item.urlTemplate]
      if (replacement != null) updated = updated.copy(urlTemplate = replacement)
      val default = defaultsById[item.id]
      if (
        updated.packageName == null &&
          default?.packageName != null &&
          updated.urlTemplate == default.urlTemplate
      ) {
        updated = updated.copy(packageName = default.packageName)
      }
      updated
    }
  }

  fun resetToDefaults() {
    val defaults = DefaultShortcuts.searchShortcuts
    saveItems(defaults)
  }

  suspend fun updateShortcut(shortcut: SearchShortcut) =
    withContext(Dispatchers.IO) {
      val currentItems = _items.value.toMutableList()
      val index = currentItems.indexOfFirst { it.id == shortcut.id }
      if (index != -1) {
        currentItems[index] = shortcut
        saveItems(currentItems)
      }
    }

  suspend fun addShortcut(shortcut: SearchShortcut) =
    withContext(Dispatchers.IO) {
      val currentItems = _items.value.toMutableList()
      currentItems.add(shortcut)
      saveItems(currentItems)
    }

  suspend fun removeShortcut(shortcutId: String) =
    withContext(Dispatchers.IO) {
      val currentItems = _items.value.toMutableList()
      currentItems.removeAll { it.id == shortcutId }
      saveItems(currentItems)
    }

  private companion object {
    /**
     * (shortcut id, template it used to ship with) → the template it should use now. Claude moved
     * from the website to its app's deep link, which opens the composer with the query already in
     * it rather than going through the browser and a sign-in.
     */
    val SUPERSEDED_TEMPLATES =
      mapOf(("claude" to "https://claude.ai/new?q=%s") to "claude://claude.ai/new?q=%s")
  }

  private fun saveItems(items: List<SearchShortcut>) {
    val jsonArray = JSONArray()
    items.forEach { item ->
      val obj = JSONObject()
      obj.put("id", item.id)
      obj.put("alias", item.alias)
      obj.put("urlTemplate", item.urlTemplate)
      obj.put("description", item.description)
      item.packageName?.let { obj.put("packageName", it) }
      item.suggestionUrl?.let { obj.put("suggestionUrl", it) }
      item.color?.let { obj.put("color", it) }
      item.shortLabel?.let { obj.put("shortLabel", it) }
      jsonArray.put(obj)
    }
    prefs.edit().putString(Prefs.SearchShortcuts.SHORTCUTS, jsonArray.toString()).apply()
    _items.value = items
    refreshAvailability()
  }

  fun replaceAll(shortcuts: List<SearchShortcut>) {
    saveItems(shortcuts)
  }
}
