package com.searchlauncher.app.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class FavoritesRepository(context: Context) {
    private val prefs: SharedPreferences =
            context.getSharedPreferences("favorites", Context.MODE_PRIVATE)

    private val _favoriteIds = MutableStateFlow<Set<String>>(emptySet())
    val favoriteIds: StateFlow<Set<String>> = _favoriteIds

    init {
        loadFavorites()
    }

    private fun loadFavorites() {
        val favorites = prefs.getStringSet("favorite_ids", emptySet()) ?: emptySet()
        _favoriteIds.value = favorites
    }

    fun toggleFavorite(id: String) {
        val currentFavorites = _favoriteIds.value.toMutableSet()
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

    private fun saveFavorites(favorites: Set<String>) {
        prefs.edit().putStringSet("favorite_ids", favorites).apply()
    }

    fun getFavoriteIds(): Set<String> {
        return _favoriteIds.value
    }

    fun replaceAll(favorites: Set<String>) {
        _favoriteIds.value = favorites
        saveFavorites(favorites)
    }
}
