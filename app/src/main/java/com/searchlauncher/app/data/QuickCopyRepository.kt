package com.searchlauncher.app.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class QuickCopyRepository(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("quick_copy", Context.MODE_PRIVATE)

    private val _items = MutableStateFlow<List<QuickCopyItem>>(emptyList())
    val items: StateFlow<List<QuickCopyItem>> = _items

    init {
        loadItems()
    }

    private fun loadItems() {
        val json = prefs.getString("items", "[]") ?: "[]"
        val items = try {
            val jsonArray = JSONArray(json)
            List(jsonArray.length()) { i ->
                val obj = jsonArray.getJSONObject(i)
                QuickCopyItem(
                    alias = obj.getString("alias"),
                    content = obj.getString("content")
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
        _items.value = items
    }

    suspend fun addItem(alias: String, content: String) = withContext(Dispatchers.IO) {
        val currentItems = _items.value.toMutableList()
        // Remove existing item with same alias
        currentItems.removeAll { it.alias.equals(alias, ignoreCase = true) }
        currentItems.add(QuickCopyItem(alias, content))
        saveItems(currentItems)
    }

    suspend fun removeItem(alias: String) = withContext(Dispatchers.IO) {
        val currentItems = _items.value.toMutableList()
        currentItems.removeAll { it.alias.equals(alias, ignoreCase = true) }
        saveItems(currentItems)
    }

    suspend fun updateItem(oldAlias: String, newAlias: String, newContent: String) =
        withContext(Dispatchers.IO) {
            val currentItems = _items.value.toMutableList()
            val index = currentItems.indexOfFirst { it.alias.equals(oldAlias, ignoreCase = true) }
            if (index != -1) {
                currentItems[index] = QuickCopyItem(newAlias, newContent)
                saveItems(currentItems)
            }
        }

    private fun saveItems(items: List<QuickCopyItem>) {
        val jsonArray = JSONArray()
        items.forEach { item ->
            val obj = JSONObject()
            obj.put("alias", item.alias)
            obj.put("content", item.content)
            jsonArray.put(obj)
        }
        prefs.edit().putString("items", jsonArray.toString()).apply()
        _items.value = items
    }

    fun searchItems(query: String): List<QuickCopyItem> {
        if (query.isEmpty()) return _items.value
        val lowerQuery = query.lowercase()
        return _items.value.filter {
            it.alias.lowercase().contains(lowerQuery) ||
                    it.content.lowercase().contains(lowerQuery)
        }
    }
}
