package com.searchlauncher.app.ui.browser

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.searchlauncher.app.SearchLauncherApp
import com.searchlauncher.app.data.*
import com.searchlauncher.app.ui.*
import com.searchlauncher.app.ui.components.FAVORITES_MAX_ROWS_AUTO
import com.searchlauncher.app.ui.components.FavoritesRow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Same favorites, recents and pinning rules as the main browser and launcher. */
@Composable
internal fun GeckoFavoritesStrip(onOpenSearch: (String) -> Unit, modifier: Modifier = Modifier) {
  val context = LocalContext.current
  val app = context.applicationContext as SearchLauncherApp
  val privateMode = false
  val sharedSearchRepository = app.searchRepositoryOrNull
  // Null in private mode so browsing never writes index/history/favicons.
  val searchRepository = if (privateMode) null else sharedSearchRepository
  val favoritesRepository = app.favoritesRepositoryOrNull
  val noIds = remember { MutableStateFlow(emptyList<String>()) }
  val noResults = remember { MutableStateFlow(emptyList<SearchResult>()) }
  val favoriteIds by (favoritesRepository?.favoriteIds ?: noIds).collectAsState()
  val favorites by (sharedSearchRepository?.favorites ?: noResults).collectAsState()
  val allRecentItems by (sharedSearchRepository?.recentItems ?: noResults).collectAsState()
  val historyLimit by
    remember { context.dataStore.data.map { it[PreferencesKeys.HISTORY_LIMIT] ?: -1 } }
      .collectAsState(initial = -1)
  val treatFavoritedSitesAsApps by
    remember {
        context.dataStore.data.map {
          it[PreferencesKeys.TREAT_FAVORITED_SITES_AS_APPS] ?: TREAT_FAVORITED_SITES_AS_APPS_DEFAULT
        }
      }
      .collectAsState(initial = TREAT_FAVORITED_SITES_AS_APPS_DEFAULT)
  val minIconSizeSetting by
    remember { MinIconSize.flow(context) }.collectAsState(initial = MinIconSize.cached(context))
  val favoritesMaxRows by
    remember {
        context.dataStore.data.map {
          it[PreferencesKeys.FAVORITES_MAX_ROWS] ?: FAVORITES_MAX_ROWS_AUTO
        }
      }
      .collectAsState(initial = FAVORITES_MAX_ROWS_AUTO)
  val noEntries = remember {
    MutableStateFlow(emptyList<com.searchlauncher.app.data.HistoryEntry>())
  }
  val historyEntries by (app.historyRepositoryOrNull?.historyEntries ?: noEntries).collectAsState()
  val openTabRecents = if (privateMode) emptyList() else openTabsAsRecents(context)
  val displayedFavorites =
    remember(favorites, treatFavoritedSitesAsApps, privateMode) {
      if (privateMode) favorites else collapsePinnedSites(favorites, treatFavoritedSitesAsApps)
    }
  LaunchedEffect(favoriteIds, favorites, treatFavoritedSitesAsApps, privateMode) {
    if (privateMode || !treatFavoritedSitesAsApps) return@LaunchedEffect
    val collapsed = keysAfterCollapsingSites(favoriteIds, favorites)
    if (collapsed != favoriteIds) favoritesRepository?.updateOrder(collapsed)
  }
  val historyItems =
    remember(
      allRecentItems,
      favoriteIds,
      favorites,
      historyLimit,
      privateMode,
      openTabRecents,
      historyEntries,
      treatFavoritedSitesAsApps,
    ) {
      if (privateMode || historyLimit == 0) emptyList()
      else {
        val favoriteKeys = favoriteIds.toSet()
        val filteredApps =
          applySiteAppHistoryFilter(
            allRecentItems.filter { it.favoriteKey !in favoriteKeys },
            favorites,
            treatFavoritedSitesAsApps,
          )
        val merged =
          mergeRecentsByTime(
            filteredApps,
            historyEntries.associate { it.id to it.lastUsedMs },
            applySiteAppTabFilter(openTabRecents, favorites, treatFavoritedSitesAsApps),
          )
        applyHistoryLimit(merged.map { it.result }, historyLimit)
      }
    }
  val coroutineScope = rememberCoroutineScope()
  val resultLauncher =
    remember(context, sharedSearchRepository, coroutineScope) {
      sharedSearchRepository?.let {
        ResultLauncher(
          context = context,
          searchRepository = it,
          scope = coroutineScope,
          treatFavoritedSitesAsApps = { treatFavoritedSitesAsApps },
          favoriteResults = { it.favorites.value },
        )
      }
    }

  Box(modifier) {
    FavoritesRow(
      favorites = displayedFavorites,
      history = historyItems,
      historyLimit = historyLimit,
      minIconSizeSetting = minIconSizeSetting,
      maxRows = favoritesMaxRows,
      onLaunch = { result ->
        if (result is SearchResult.SearchIntent) onOpenSearch(result.trigger + " ")
        else resultLauncher?.launch(result, reportUsage = true)
      },
      onToggleFavorite = { result ->
        coroutineScope.launch {
          searchRepository?.pinOrUnpinFavorite(result, treatFavoritedSitesAsApps)
        }
      },
      isItemFavorite = { result ->
        isDisplayedAsFavorite(result, favorites, favoriteIds, treatFavoritedSitesAsApps)
      },
      onReorder = { favoritesRepository?.updateOrder(it) },
      onCapacityChanged = { searchRepository?.updateObservedHistoryLimit(it) },
    )
  }
}
