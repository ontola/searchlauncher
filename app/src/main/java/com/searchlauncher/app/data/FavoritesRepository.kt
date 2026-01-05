package com.searchlauncher.app.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray

class FavoritesRepository(context: Context) {
  private val prefs: SharedPreferences =
    context.getSharedPreferences("favorites", Context.MODE_PRIVATE)

  private val _favoriteIds = MutableStateFlow<List<String>>(emptyList())
  val favoriteIds: StateFlow<List<String>> = _favoriteIds

  init {
    loadFavorites()
  }

  private fun loadFavorites() {
    // Try to load the ordered JSON list first
    val jsonString = prefs.getString("favorite_ids_ordered", null)
    if (jsonString != null) {
      try {
        val array = JSONArray(jsonString)
        val list = mutableListOf<String>()
        for (i in 0 until array.length()) {
          list.add(array.getString(i))
        }
        _favoriteIds.value = list
        return
      } catch (e: Exception) {
        e.printStackTrace()
      }
    }

    // Migration path: load from the old Set if JSON is missing
    val favoritesSet = prefs.getStringSet("favorite_ids", emptySet()) ?: emptySet()
    _favoriteIds.value = favoritesSet.toList()
  }

  fun toggleFavorite(id: String) {
    val currentFavorites = _favoriteIds.value.toMutableList()
    if (currentFavorites.contains(id)) {
      currentFavorites.remove(id)
    } else {
      currentFavorites.add(id)
    }
    _favoriteIds.value = currentFavorites
    saveFavorites(currentFavorites)
  }

  fun isFavorite(id: String): Boolean {
    return _favoriteIds.value.contains(id)
  }

  private fun saveFavorites(favorites: List<String>) {
    val array = JSONArray()
    favorites.forEach { array.put(it) }
    prefs.edit().putString("favorite_ids_ordered", array.toString()).apply()
    // Also update the old Set for backward compatibility or simple lookups
    prefs.edit().putStringSet("favorite_ids", favorites.toSet()).apply()
  }

  fun updateOrder(newOrder: List<String>) {
    _favoriteIds.value = newOrder
    saveFavorites(newOrder)
  }

  fun getFavoriteIds(): List<String> {
    return _favoriteIds.value
  }

  fun replaceAll(favorites: List<String>) {
    _favoriteIds.value = favorites
    saveFavorites(favorites)
  }

  fun clear() {
    _favoriteIds.value = emptyList()
    prefs.edit().remove("favorite_ids_ordered").remove("favorite_ids").apply()
  }
}
