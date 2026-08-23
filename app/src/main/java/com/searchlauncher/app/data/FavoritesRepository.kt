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

  private val _searchOptionIds = MutableStateFlow(SearchOptions.DEFAULT_FAVORITE_IDS)
  /** Ordered shortcut ids shown in the query-time search-options bar. */
  val searchOptionIds: StateFlow<List<String>> = _searchOptionIds

  init {
    loadFavorites()
    loadSearchOptions()
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
    _searchOptionIds.value = SearchOptions.DEFAULT_FAVORITE_IDS
    prefs
      .edit()
      .remove(Prefs.Favorites.IDS_ORDERED)
      .remove(Prefs.Favorites.IDS)
      .remove(Prefs.Favorites.SEARCH_OPTION_IDS)
      .apply()
  }

  private fun loadSearchOptions() {
    val jsonString = prefs.getString(Prefs.Favorites.SEARCH_OPTION_IDS, null) ?: return
    try {
      val array = JSONArray(jsonString)
      _searchOptionIds.value =
        (0 until array.length()).map { SearchOptions.normalizeId(array.getString(it)) }
    } catch (e: Exception) {
      e.printStackTrace()
    }
  }

  fun toggleSearchOption(result: SearchResult) {
    toggleSearchOption(SearchOptions.normalizeId(result.id))
  }

  fun toggleSearchOption(id: String) {
    val normalized = SearchOptions.normalizeId(id)
    val current = _searchOptionIds.value.toMutableList()
    if (current.contains(normalized)) {
      current.remove(normalized)
    } else {
      current.add(normalized)
    }
    replaceSearchOptions(current)
  }

  fun isSearchOptionFavorite(result: SearchResult): Boolean =
    isSearchOptionFavorite(SearchOptions.normalizeId(result.id))

  fun isSearchOptionFavorite(id: String): Boolean =
    _searchOptionIds.value.contains(SearchOptions.normalizeId(id))

  fun updateSearchOptionOrder(newOrder: List<String>) {
    replaceSearchOptions(newOrder.map(SearchOptions::normalizeId))
  }

  fun getSearchOptionIds(): List<String> = _searchOptionIds.value

  fun replaceSearchOptions(ids: List<String>) {
    val normalized = ids.map(SearchOptions::normalizeId)
    _searchOptionIds.value = normalized
    val array = JSONArray()
    normalized.forEach { array.put(it) }
    prefs.edit().putString(Prefs.Favorites.SEARCH_OPTION_IDS, array.toString()).apply()
  }
}
