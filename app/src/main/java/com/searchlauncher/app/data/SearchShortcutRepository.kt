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
        private val prefs: SharedPreferences =
                context.getSharedPreferences("search_shortcuts", Context.MODE_PRIVATE)

        private val _items = MutableStateFlow<List<SearchShortcut>>(emptyList())
        val items: StateFlow<List<SearchShortcut>> = _items

        init {
                loadItems()
        }

        private fun loadItems() {
                val json = prefs.getString("shortcuts", null)
                if (json == null) {
                        // First run, load defaults
                        resetToDefaults()
                        return
                }

                val items =
                        try {
                                val jsonArray = JSONArray(json)
                                List(jsonArray.length()) { i ->
                                        val obj = jsonArray.getJSONObject(i)
                                        SearchShortcut(
                                                id = obj.getString("id"),
                                                alias = obj.getString("alias"),
                                                urlTemplate = obj.getString("urlTemplate"),
                                                description = obj.getString("description"),
                                                packageName =
                                                        obj.optString("packageName").takeIf {
                                                                it.isNotEmpty()
                                                        },
                                                suggestionUrl =
                                                        obj.optString("suggestionUrl").takeIf {
                                                                it.isNotEmpty()
                                                        },
                                                color =
                                                        if (obj.has("color")) obj.getLong("color")
                                                        else null
                                        )
                                }
                        } catch (e: Exception) {
                                DefaultShortcuts.searchShortcuts // Fallback to defaults
                        }
                _items.value = items
        }

        fun resetToDefaults() {
                val defaults = DefaultShortcuts.searchShortcuts
                saveItems(defaults)
        }

        suspend fun updateAlias(shortcutId: String, newAlias: String) =
                withContext(Dispatchers.IO) {
                        val currentItems = _items.value.toMutableList()
                        val index = currentItems.indexOfFirst { it.id == shortcutId }
                        if (index != -1) {
                                currentItems[index] = currentItems[index].copy(alias = newAlias)
                                saveItems(currentItems)
                        }
                }

        suspend fun addShortcut(shortcut: SearchShortcut) =
                withContext(Dispatchers.IO) {
                        val currentItems = _items.value.toMutableList()
                        currentItems.add(shortcut)
                        saveItems(currentItems)
                }

        suspend fun addShortcutAt(index: Int, shortcut: SearchShortcut) =
                withContext(Dispatchers.IO) {
                        val currentItems = _items.value.toMutableList()
                        if (index in 0..currentItems.size) {
                                currentItems.add(index, shortcut)
                        } else {
                                currentItems.add(shortcut)
                        }
                        saveItems(currentItems)
                }

        suspend fun removeShortcut(shortcutId: String) =
                withContext(Dispatchers.IO) {
                        val currentItems = _items.value.toMutableList()
                        currentItems.removeAll { it.id == shortcutId }
                        saveItems(currentItems)
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
                        jsonArray.put(obj)
                }
                prefs.edit().putString("shortcuts", jsonArray.toString()).apply()
                _items.value = items
        }

        fun replaceAll(shortcuts: List<SearchShortcut>) {
                saveItems(shortcuts)
        }
}
