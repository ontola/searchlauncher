package com.searchlauncher.app.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray

class FavoritesRepository(context: Context) {
  private val prefs: SharedPreferences =
    context.getSharedPreferences(Prefs.Favorites.FILE, Context.MODE_PRIVATE)

  private val _favoriteIds = MutableStateFlow<List<String>>(emptyList())
  /** Ordered list of namespaced favorite keys (`namespace/id`). */
  val favoriteIds: StateFlow<List<String>> = _favoriteIds

  init {
    loadFavorites()
  }

  private fun loadFavorites() {
    // Try to load the ordered JSON list first
    val jsonString = prefs.getString(Prefs.Favorites.IDS_ORDERED, null)
    if (jsonString != null) {
      try {
        val array = JSONArray(jsonString)
        val list = mutableListOf<String>()
        for (i in 0 until array.length()) {
          list.add(FavoriteKeys.normalize(array.getString(i)))
        }
        _favoriteIds.value = list
        // Persist migration from bare package ids → namespaced keys
        if (list != (0 until array.length()).map { array.getString(it) }) {
          saveFavorites(list)
        }
        return
      } catch (e: Exception) {
        e.printStackTrace()
      }
    }

    // Migration path: load from the old Set if JSON is missing
    val favoritesSet = prefs.getStringSet(Prefs.Favorites.IDS, emptySet()) ?: emptySet()
    val migrated = favoritesSet.map { FavoriteKeys.normalize(it) }
    _favoriteIds.value = migrated
    if (migrated.isNotEmpty()) {
      saveFavorites(migrated)
    }
  }

  fun toggleFavorite(result: SearchResult) {
    toggleFavorite(result.namespace, result.id)
  }

  fun toggleFavorite(namespace: String, id: String) {
    val key = FavoriteKeys.of(namespace, id)
    val currentFavorites = _favoriteIds.value.toMutableList()
    if (currentFavorites.contains(key)) {
      currentFavorites.remove(key)
    } else {
      currentFavorites.add(key)
    }
    _favoriteIds.value = currentFavorites
    saveFavorites(currentFavorites)
  }

  fun isFavorite(result: SearchResult): Boolean = isFavorite(result.namespace, result.id)

  fun isFavorite(namespace: String, id: String): Boolean {
    return _favoriteIds.value.contains(FavoriteKeys.of(namespace, id))
  }

  private fun saveFavorites(favorites: List<String>) {
    val array = JSONArray()
    favorites.forEach { array.put(it) }
    prefs.edit().putString(Prefs.Favorites.IDS_ORDERED, array.toString()).apply()
    // Also update the old Set for backward compatibility or simple lookups
    prefs.edit().putStringSet(Prefs.Favorites.IDS, favorites.toSet()).apply()
  }

  fun updateOrder(newOrder: List<String>) {
    val normalized = newOrder.map { FavoriteKeys.normalize(it) }
    _favoriteIds.value = normalized
    saveFavorites(normalized)
  }

  fun getFavoriteIds(): List<String> {
    return _favoriteIds.value
  }

  fun replaceAll(favorites: List<String>) {
    val normalized = favorites.map { FavoriteKeys.normalize(it) }
    _favoriteIds.value = normalized
    saveFavorites(normalized)
  }

  fun clear() {
    _favoriteIds.value = emptyList()
    prefs.edit().remove(Prefs.Favorites.IDS_ORDERED).remove(Prefs.Favorites.IDS).apply()
  }
}
