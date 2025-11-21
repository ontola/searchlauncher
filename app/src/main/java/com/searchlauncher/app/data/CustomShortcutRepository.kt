package com.searchlauncher.app.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class CustomShortcutRepository(context: Context) {
        private val prefs: SharedPreferences =
                context.getSharedPreferences("custom_shortcuts", Context.MODE_PRIVATE)

        private val _items = MutableStateFlow<List<CustomShortcut>>(emptyList())
        val items: StateFlow<List<CustomShortcut>> = _items

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
                                        val type = obj.getString("type")
                                        if (type == "search") {
                                                CustomShortcut.Search(
                                                        trigger = obj.getString("trigger"),
                                                        urlTemplate = obj.getString("urlTemplate"),
                                                        description = obj.getString("description"),
                                                        color = obj.getLong("color"),
                                                        suggestionUrl =
                                                                if (obj.has("suggestionUrl"))
                                                                        obj.getString(
                                                                                "suggestionUrl"
                                                                        )
                                                                else null,
                                                        packageName =
                                                                if (obj.has("packageName"))
                                                                        obj.getString("packageName")
                                                                else null
                                                )
                                        } else {
                                                CustomShortcut.Action(
                                                        intentUri = obj.getString("intentUri"),
                                                        description = obj.getString("description")
                                                )
                                        }
                                }
                        } catch (e: Exception) {
                                CustomShortcuts.shortcuts // Fallback to defaults
                        }
                _items.value = items
        }

        fun resetToDefaults() {
                val defaults = CustomShortcuts.shortcuts
                saveItems(defaults)
        }

        suspend fun addShortcut(shortcut: CustomShortcut) =
                withContext(Dispatchers.IO) {
                        val currentItems = _items.value.toMutableList()
                        currentItems.add(shortcut)
                        saveItems(currentItems)
                }

        suspend fun removeShortcut(shortcut: CustomShortcut) =
                withContext(Dispatchers.IO) {
                        val currentItems = _items.value.toMutableList()
                        currentItems.remove(shortcut)
                        saveItems(currentItems)
                }

        suspend fun updateShortcut(oldShortcut: CustomShortcut, newShortcut: CustomShortcut) =
                withContext(Dispatchers.IO) {
                        val currentItems = _items.value.toMutableList()
                        val index = currentItems.indexOf(oldShortcut)
                        if (index != -1) {
                                currentItems[index] = newShortcut
                                saveItems(currentItems)
                        }
                }

        private fun saveItems(items: List<CustomShortcut>) {
                val jsonArray = JSONArray()
                items.forEach { item ->
                        val obj = JSONObject()
                        when (item) {
                                is CustomShortcut.Search -> {
                                        obj.put("type", "search")
                                        obj.put("trigger", item.trigger)
                                        obj.put("urlTemplate", item.urlTemplate)
                                        obj.put("description", item.description)
                                        obj.put("color", item.color)
                                        item.suggestionUrl?.let { obj.put("suggestionUrl", it) }
                                        item.packageName?.let { obj.put("packageName", it) }
                                }
                                is CustomShortcut.Action -> {
                                        obj.put("type", "action")
                                        obj.put("intentUri", item.intentUri)
                                        obj.put("description", item.description)
                                }
                        }
                        jsonArray.put(obj)
                }
                prefs.edit().putString("shortcuts", jsonArray.toString()).apply()
                _items.value = items
        }

        fun replaceAll(shortcuts: List<CustomShortcut>) {
                saveItems(shortcuts)
        }
}
