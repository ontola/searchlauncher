package com.searchlauncher.app.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject

class SnippetsRepository(context: Context) {
  private val prefs: SharedPreferences =
    context.getSharedPreferences("quick_copy", Context.MODE_PRIVATE)

  private val _items = MutableStateFlow<List<SnippetItem>>(emptyList())
  val items: StateFlow<List<SnippetItem>> = _items

  init {
    loadItems()
  }

  private fun loadItems() {
    val json = prefs.getString("items", "[]") ?: "[]"
    try {
      val jsonArray = JSONArray(json)
      val items = mutableListOf<SnippetItem>()
      for (i in 0 until jsonArray.length()) {
        val obj = jsonArray.getJSONObject(i)
        items.add(SnippetItem(alias = obj.getString("alias"), content = obj.getString("content")))
      }
      _items.value = items.sortedBy { it.alias.lowercase() }
    } catch (e: Exception) {
      _items.value = emptyList()
    }
  }

  suspend fun addItem(alias: String, content: String) {
    _items.update { currentItems ->
      val mutableItems = currentItems.toMutableList()
      // Remove existing item with same alias
      mutableItems.removeAll { it.alias.equals(alias, ignoreCase = true) }
      mutableItems.add(SnippetItem(alias, content))
      saveItems(mutableItems)
      mutableItems.sortedBy { it.alias.lowercase() }
    }
  }

  suspend fun deleteItem(alias: String) {
    _items.update { currentItems ->
      val mutableItems = currentItems.toMutableList()
      mutableItems.removeAll { it.alias.equals(alias, ignoreCase = true) }
      saveItems(mutableItems)
      mutableItems
    }
  }

  suspend fun updateItem(oldAlias: String, newAlias: String, newContent: String) {
    _items.update { currentItems ->
      val mutableItems = currentItems.toMutableList()
      val index = mutableItems.indexOfFirst { it.alias.equals(oldAlias, ignoreCase = true) }
      if (index != -1) {
        mutableItems[index] = SnippetItem(newAlias, newContent)
        saveItems(mutableItems)
      }
      mutableItems.sortedBy { it.alias.lowercase() }
    }
  }

  private fun saveItems(items: List<SnippetItem>) {
    val jsonArray = JSONArray()
    items.forEach { item ->
      val obj = JSONObject()
      obj.put("alias", item.alias)
      obj.put("content", item.content)
      jsonArray.put(obj)
    }
    prefs.edit().putString("items", jsonArray.toString()).apply()
  }

  fun searchItems(query: String): List<SnippetItem> {
    if (query.isEmpty()) return _items.value
    val lowerQuery = query.lowercase()
    return _items.value.filter { item ->
      item.alias.lowercase().contains(lowerQuery) || item.content.lowercase().contains(lowerQuery)
    }
  }

  fun clearAll() {
    _items.value = emptyList()
    prefs.edit().remove("items").apply()
  }
}
