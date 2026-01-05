package com.searchlauncher.app.data

import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.Log
import android.util.LruCache
import androidx.appsearch.app.AppSearchSession
import androidx.appsearch.app.PutDocumentsRequest
import androidx.appsearch.app.RemoveByDocumentIdRequest
import androidx.appsearch.app.SearchSpec
import androidx.appsearch.app.SetSchemaRequest
import androidx.appsearch.localstorage.LocalStorage
import com.searchlauncher.app.SearchLauncherApp
import com.searchlauncher.app.ui.MainActivity
import com.searchlauncher.app.ui.dataStore
import com.searchlauncher.app.util.FuzzyMatch
import com.searchlauncher.app.util.StaticShortcutScanner
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Collections
import java.util.concurrent.Executors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class SearchRepository(private val context: Context) {
  private val documentCache = Collections.synchronizedList(mutableListOf<AppSearchDocument>())
  private var appSearchSession: AppSearchSession? = null

  private fun replaceCollection(namespace: String, documents: List<AppSearchDocument>) {
    synchronized(documentCache) {
      documentCache.removeAll { it.namespace == namespace }
      documentCache.addAll(documents)
    }
  }

  private val executor = Executors.newSingleThreadExecutor()
  private val smartActionManager = SmartActionManager(context)
  private val iconGenerator = SearchIconGenerator(context)
  private val iconCache = LruCache<String, Drawable>(200)

  // LRU cache for single-letter search queries
  private val searchCache =
    Collections.synchronizedMap(
      object : LinkedHashMap<String, List<SearchResult>>(16, 0.75f, true) {
        override fun removeEldestEntry(
          eldest: MutableMap.MutableEntry<String, List<SearchResult>>?
        ): Boolean {
          return size > CACHE_SIZE
        }
      }
    )
  private val CACHE_SIZE = 50
  private val SINGLE_LETTER_PATTERN = Regex("^[a-zA-Z0-9]$")
  private var lastUsageReportTime = 0L
  private val CACHE_COOLDOWN_MS = 500L // Wait 500ms after usage report before caching again

  private val _isInitialized = kotlinx.coroutines.flow.MutableStateFlow(false)
  val isInitialized: kotlinx.coroutines.flow.StateFlow<Boolean> = _isInitialized

  private val _isIndexing = kotlinx.coroutines.flow.MutableStateFlow(false)
  val isIndexing: kotlinx.coroutines.flow.StateFlow<Boolean> = _isIndexing

  private val _indexUpdated = kotlinx.coroutines.flow.MutableSharedFlow<Unit>(replay = 1)
  val indexUpdated: kotlinx.coroutines.flow.SharedFlow<Unit> = _indexUpdated

  private val _packageUpdated = kotlinx.coroutines.flow.MutableSharedFlow<String?>(replay = 0)
  val packageUpdated: kotlinx.coroutines.flow.SharedFlow<String?> = _packageUpdated

  private val _favorites = kotlinx.coroutines.flow.MutableStateFlow<List<SearchResult>>(emptyList())
  val favorites: kotlinx.coroutines.flow.StateFlow<List<SearchResult>> = _favorites

  private val usageStats = java.util.concurrent.ConcurrentHashMap<String, Int>()

  suspend fun initialize() =
    withContext(Dispatchers.IO) {
      val startTime = System.currentTimeMillis()
      try {
        loadUsageStats()
        _isIndexing.value = true
        // Load cached favorites immediately for instant UI
        _favorites.value = loadFavoritesFromCache()

        val sessionFuture =
          LocalStorage.createSearchSessionAsync(
            LocalStorage.SearchContext.Builder(context, "searchlauncher_db").build()
          )
        appSearchSession = sessionFuture.get()

        try {
          val launcherApps =
            context.getSystemService(Context.LAUNCHER_APPS_SERVICE)
              as android.content.pm.LauncherApps
          launcherApps.registerCallback(
            launcherCallback,
            android.os.Handler(android.os.Looper.getMainLooper()),
          )
        } catch (e: Exception) {
          e.printStackTrace()
        }

        val setSchemaRequest =
          SetSchemaRequest.Builder().addDocumentClasses(AppSearchDocument::class.java).build()
        appSearchSession?.setSchemaAsync(setSchemaRequest)?.get()

        val loadStart = System.currentTimeMillis()
        loadFromIndex()
        android.util.Log.d(
          "SearchRepository",
          "loadFromIndex took ${System.currentTimeMillis() - loadStart}ms",
        )

        // Signal ready after loading cache (very quick)
        _isInitialized.value = true
        _isIndexing.value = false
        android.util.Log.d(
          "SearchRepository",
          "Early Initialization (ready for UI) took ${System.currentTimeMillis() - startTime}ms",
        )

        // Perform full re-indexing in background to refresh any changes after a quiet window
        scope.launch {
          delay(2000) // Give the UI 2 seconds to load icons and settle

          val prefs = context.getSharedPreferences("search_launcher_prefs", Context.MODE_PRIVATE)
          val lastReindex = prefs.getLong("last_reindex_timestamp", 0L)
          val currentTime = System.currentTimeMillis()
          // Re-index only if empty or older than 4 hours
          val isStale = (currentTime - lastReindex) > (12 * 60 * 60 * 1000)
          val hasDocs = synchronized(documentCache) { documentCache.isNotEmpty() }

          if (hasDocs && !isStale) {
            android.util.Log.d(
              "SearchRepository",
              "Index is fresh (last: $lastReindex), skipping re-index",
            )
            return@launch
          }

          // Don't block UI with _isIndexing = true. Let it happen in bg.
          _isIndexing.value = true
          val backgroundStart = System.currentTimeMillis()

          try {
            val appsStart = System.currentTimeMillis()
            indexApps()
            android.util.Log.d(
              "SearchRepository",
              "indexApps took ${System.currentTimeMillis() - appsStart}ms",
            )

            val customStart = System.currentTimeMillis()
            indexCustomShortcuts()
            android.util.Log.d(
              "SearchRepository",
              "indexCustomShortcuts took ${System.currentTimeMillis() - customStart}ms",
            )

            val staticStart = System.currentTimeMillis()
            indexStaticShortcuts()
            android.util.Log.d(
              "SearchRepository",
              "indexStaticShortcuts took ${System.currentTimeMillis() - staticStart}ms",
            )

            val contactsStart = System.currentTimeMillis()
            indexContacts()
            android.util.Log.d(
              "SearchRepository",
              "indexContacts took ${System.currentTimeMillis() - contactsStart}ms",
            )

            val snippetsStart = System.currentTimeMillis()
            indexSnippets()
            android.util.Log.d(
              "SearchRepository",
              "indexSnippets took ${System.currentTimeMillis() - snippetsStart}ms",
            )

            android.util.Log.d(
              "SearchRepository",
              "Background Re-indexing took ${System.currentTimeMillis() - backgroundStart}ms",
            )
            prefs.edit().putLong("last_reindex_timestamp", System.currentTimeMillis()).apply()
          } finally {
            _isIndexing.value = false
          }
        }
      } catch (e: Throwable) {
        e.printStackTrace()
      }
    }

  private suspend fun loadFromIndex() =
    withContext(Dispatchers.IO) {
      val session = appSearchSession ?: return@withContext
      try {
        val searchSpec =
          SearchSpec.Builder()
            .setTermMatch(SearchSpec.TERM_MATCH_PREFIX)
            .setResultCountPerPage(1000)
            .build()

        val searchResults = session.search("", searchSpec)
        val allDocs = mutableListOf<AppSearchDocument>()

        var page = searchResults.nextPageAsync.get()
        while (page.isNotEmpty()) {
          allDocs.addAll(
            page.mapNotNull { it.genericDocument.toDocumentClass(AppSearchDocument::class.java) }
          )
          page = searchResults.nextPageAsync.get()
        }

        synchronized(documentCache) {
          documentCache.clear()
          documentCache.addAll(allDocs)
        }
        updateAppsCache()
        android.util.Log.d("SearchRepository", "Loaded ${allDocs.size} documents from index")
      } catch (e: Exception) {
        android.util.Log.e("SearchRepository", "Failed to load from index", e)
      }
    }

  private suspend fun indexApps() =
    withContext(Dispatchers.IO) {
      android.util.Log.d("SearchRepository", "Indexing apps...")
      searchCache.clear() // Invalidate cache
      val session = appSearchSession ?: return@withContext
      val launcherApps =
        context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as android.content.pm.LauncherApps

      val apps = mutableListOf<AppSearchDocument>()

      // Note: We NO LONGER clear the entire 'apps' namespace here.
      // Clearing the namespace also wipes usage stats (history).
      // Instead, we put new documents (updates existing ones) and later
      // we could implement a cleanup for apps that were uninstalled.

      val profiles = launcherApps.profiles

      for (profile in profiles) {
        try {
          val activityList = launcherApps.getActivityList(null, profile)
          for (info in activityList) {
            try {
              val appName = info.label.toString()
              val packageName = info.componentName.packageName

              val appInfo = info.applicationInfo
              val category =
                when (appInfo.category) {
                  ApplicationInfo.CATEGORY_GAME -> "Game"
                  ApplicationInfo.CATEGORY_AUDIO -> "Audio"
                  ApplicationInfo.CATEGORY_VIDEO -> "Video"
                  ApplicationInfo.CATEGORY_IMAGE -> "Image"
                  ApplicationInfo.CATEGORY_SOCIAL -> "Social"
                  ApplicationInfo.CATEGORY_NEWS -> "News"
                  ApplicationInfo.CATEGORY_MAPS -> "Maps"
                  ApplicationInfo.CATEGORY_PRODUCTIVITY -> "Productivity"
                  else -> "Application"
                }

              apps.add(
                AppSearchDocument(
                  namespace = "apps",
                  id = packageName,
                  name = appName,
                  score = 2,
                  description = category,
                )
              )
            } catch (e: Exception) {
              // Ignore individual app failures
            }
          }
        } catch (e: Exception) {
          android.util.Log.e("SearchRepository", "Error querying apps for profile $profile", e)
        }
      }

      if (apps.isNotEmpty()) {
        val putRequest = PutDocumentsRequest.Builder().addDocuments(apps).build()
        try {
          session.putAsync(putRequest).get()
        } catch (e: Exception) {
          android.util.Log.e("SearchRepository", "Failed to put indexed apps", e)
        }

        // Cleanup: Remove apps from AppSearch that are no longer in our current 'apps' list.
        try {
          val currentPackageNames = apps.map { it.id }.toSet()
          val searchSpec =
            SearchSpec.Builder().addFilterNamespaces("apps").setResultCountPerPage(1000).build()
          val searchResults = session.search("", searchSpec)
          var page = searchResults.nextPageAsync.get()
          val idsToRemove = mutableListOf<String>()
          while (page.isNotEmpty()) {
            page.forEach {
              val id = it.genericDocument.id
              if (!currentPackageNames.contains(id)) {
                idsToRemove.add(id)
              }
            }
            page = searchResults.nextPageAsync.get()
          }
          if (idsToRemove.isNotEmpty()) {
            val removeRequest =
              RemoveByDocumentIdRequest.Builder("apps").addIds(idsToRemove).build()
            session.removeAsync(removeRequest).get()
            android.util.Log.d(
              "SearchRepository",
              "Removed ${idsToRemove.size} zombie apps from index",
            )
          }
        } catch (e: Exception) {
          android.util.Log.e("SearchRepository", "Failed to cleanup zombie apps", e)
        }

        replaceCollection("apps", apps)
      } else {
        android.util.Log.w(
          "SearchRepository",
          "indexApps: No apps found. Skipping update to prevent accidental wipe.",
        )
      }

      try {
        indexShortcuts()
        indexStaticShortcuts() // Static shortcuts also change when apps are removed/added
      } catch (e: Exception) {
        android.util.Log.e("SearchRepository", "Error updating shortcuts after indexApps", e)
      }
      updateAppsCache()
    }

  suspend fun indexShortcuts() =
    withContext(Dispatchers.IO) {
      val session = appSearchSession ?: return@withContext
      val launcherApps =
        context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as android.content.pm.LauncherApps

      val shortcuts = mutableListOf<AppSearchDocument>()

      // Clear old shortcuts entries to prevent zombies
      try {
        val removeSpec = SearchSpec.Builder().addFilterNamespaces("shortcuts").build()
        session.removeAsync("", removeSpec).get()
        android.util.Log.d("SearchRepository", "Cleared 'shortcuts' namespace for re-indexing")
      } catch (e: Exception) {
        android.util.Log.e("SearchRepository", "Failed to clear 'shortcuts' namespace", e)
      }

      try {
        // 1. Get all profiles (to support work profiles)
        val profiles = launcherApps.profiles

        val appNameCache = mutableMapOf<String, String>()

        for (profile in profiles) {
          try {
            // 2. Query for ALL shortcuts in the profile
            val query = android.content.pm.LauncherApps.ShortcutQuery()
            query.setQueryFlags(
              android.content.pm.LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                android.content.pm.LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                android.content.pm.LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED
            )

            val shortcutList = launcherApps.getShortcuts(query, profile) ?: emptyList()

            for (shortcut in shortcutList) {
              try {
                val intent =
                  try {
                    "shortcut://${shortcut.`package`}/${shortcut.id}"
                  } catch (e: Exception) {
                    continue
                  }

                val name = shortcut.shortLabel?.toString() ?: shortcut.longLabel?.toString() ?: ""
                val appName =
                  appNameCache.getOrPut(shortcut.`package`) {
                    try {
                      val appInfo = context.packageManager.getApplicationInfo(shortcut.`package`, 0)
                      context.packageManager.getApplicationLabel(appInfo).toString()
                    } catch (e: Exception) {
                      shortcut.`package`
                    }
                  }

                shortcuts.add(
                  AppSearchDocument(
                    namespace = "shortcuts",
                    id = "${shortcut.`package`}/${shortcut.id}",
                    name = name,
                    score = 1,
                    intentUri = intent,
                    description = "Shortcut - $appName",
                  )
                )
              } catch (e: Exception) {
                // Ignore individual shortcut failures
              }
            }
          } catch (e: Exception) {
            android.util.Log.e(
              "SearchRepository",
              "Error querying shortcuts for profile $profile",
              e,
            )
          }
        }

        if (shortcuts.isNotEmpty()) {
          val putRequest = PutDocumentsRequest.Builder().addDocuments(shortcuts).build()
          session.putAsync(putRequest).get()
        }
        replaceCollection("shortcuts", shortcuts)
      } catch (e: Exception) {
        e.printStackTrace()
      }
      _indexUpdated.emit(Unit)
    }

  suspend fun indexCustomShortcuts() =
    withContext(Dispatchers.IO) {
      searchCache.clear() // Invalidate cache
      val session = appSearchSession ?: return@withContext

      val app = context.applicationContext as? SearchLauncherApp ?: return@withContext

      // Remove old shortcuts first to prevent zombies
      try {
        val removeSpec =
          SearchSpec.Builder().addFilterNamespaces("search_shortcuts", "app_shortcuts").build()
        session.removeAsync("", removeSpec).get()
      } catch (e: Exception) {
        e.printStackTrace()
      }

      // Index app-defined shortcuts (settings, actions)
      val appShortcutDocs: List<AppSearchDocument> =
        DefaultShortcuts.appShortcuts.map { shortcut ->
          AppSearchDocument(
            namespace = "app_shortcuts",
            id = "app_${shortcut.id}",
            name = shortcut.description,
            score = 3,
            description = (shortcut as? AppShortcut.Action)?.aliases,
            intentUri = (shortcut as? AppShortcut.Action)?.intentUri,
            isAction = true,
          )
        }

      // Index user-editable search shortcuts
      val searchShortcuts = app.searchShortcutRepository.items.value
      val searchShortcutDocs: List<AppSearchDocument> =
        searchShortcuts.map { shortcut ->
          AppSearchDocument(
            namespace = "search_shortcuts",
            id = "search_${shortcut.id}",
            name = shortcut.description,
            score = 3,
            description = shortcut.alias,
            intentUri = shortcut.urlTemplate,
            isAction = false,
          )
        }

      val allDocs = appShortcutDocs + searchShortcutDocs
      if (allDocs.isNotEmpty()) {
        val putRequest = PutDocumentsRequest.Builder().addDocuments(allDocs).build()
        session.putAsync(putRequest).get()
        // Special case for custom shortcuts: they have two namespaces
        synchronized(documentCache) {
          documentCache.removeAll {
            it.namespace == "search_shortcuts" || it.namespace == "app_shortcuts"
          }
          documentCache.addAll(allDocs)
        }
      }
    }

  suspend fun indexStaticShortcuts() =
    withContext(Dispatchers.IO) {
      searchCache.clear() // Invalidate cache
      val session = appSearchSession ?: return@withContext
      val shortcuts = StaticShortcutScanner.scan(context)
      val docs =
        shortcuts.map { s ->
          val appName =
            try {
              val appInfo = context.packageManager.getApplicationInfo(s.packageName, 0)
              context.packageManager.getApplicationLabel(appInfo).toString()
            } catch (e: Exception) {
              s.packageName
            }

          AppSearchDocument(
            namespace = "static_shortcuts",
            id = "${s.packageName}/${s.id}",
            name = "$appName: ${s.shortLabel}",
            description = "Shortcut - $appName",
            score = 1,
            intentUri = s.intent.toUri(0),
            iconResId = s.iconResId.toLong(),
          )
        }

      // Clear old entries
      try {
        val removeSpec = SearchSpec.Builder().addFilterNamespaces("static_shortcuts").build()
        session.removeAsync("", removeSpec).get()
      } catch (e: Exception) {
        android.util.Log.e("SearchRepository", "Failed to clear static_shortcuts namespace", e)
      }

      if (docs.isNotEmpty()) {
        val putRequest = PutDocumentsRequest.Builder().addDocuments(docs).build()
        session.putAsync(putRequest).get()
      }
      replaceCollection("static_shortcuts", docs)
    }

  suspend fun resetIndex() =
    withContext(Dispatchers.IO) {
      val session = appSearchSession ?: return@withContext
      try {
        withContext(Dispatchers.Main) { _isIndexing.value = true }

        // Clear manual usage persistence
        resetUsageStats()
        getFavoritesCacheFile().delete()
        _favorites.value = emptyList() // Explicitly clear favorites

        // Clear document cache (except bookmarks if possible, or just reload)
        synchronized(documentCache) { documentCache.removeAll { it.namespace != "web_bookmarks" } }

        // Selective wipe: everything EXCEPT web_bookmarks
        try {
          val removeSpec =
            SearchSpec.Builder()
              .addFilterNamespaces(
                "apps",
                "shortcuts",
                "static_shortcuts",
                "search_shortcuts",
                "app_shortcuts",
                "contacts",
                "snippets",
              )
              .build()
          session.removeAsync("", removeSpec).get()
          android.util.Log.d("SearchRepository", "Selectively cleared index (excluding bookmarks)")
        } catch (e: Exception) {
          android.util.Log.e("SearchRepository", "Failed to selectively clear index", e)
        }

        // Clear timestamp to ensure future "fresh" checks fail until we are done
        val prefs = context.getSharedPreferences("search_launcher_prefs", Context.MODE_PRIVATE)
        prefs.edit().remove("last_reindex_timestamp").apply()

        // Re-index everything (indexApps etc. will re-populate documentCache)
        indexApps()
        indexCustomShortcuts()
        indexStaticShortcuts()
        indexContacts()
        indexSnippets()

        warmupCache()

        prefs.edit().putLong("last_reindex_timestamp", System.currentTimeMillis()).apply()
      } catch (e: Exception) {
        android.util.Log.e("SearchRepository", "Error during resetIndex", e)
      } finally {
        withContext(Dispatchers.Main) { _isIndexing.value = false }
      }
    }

  suspend fun resetAppData() =
    withContext(Dispatchers.IO) {
      val session = appSearchSession ?: return@withContext
      try {
        withContext(Dispatchers.Main) { _isIndexing.value = true }

        // Clear all local state
        resetUsageStats()
        getFavoritesCacheFile().delete()
        _favorites.value = emptyList() // Explicitly clear favorites
        synchronized(documentCache) { documentCache.clear() }

        // Full AppSearch wipe
        val setSchemaRequest = SetSchemaRequest.Builder().setForceOverride(true).build()
        session.setSchemaAsync(setSchemaRequest).get()

        val initSchemaRequest =
          SetSchemaRequest.Builder().addDocumentClasses(AppSearchDocument::class.java).build()
        session.setSchemaAsync(initSchemaRequest).get()

        // Re-index core only
        indexApps()
        indexCustomShortcuts()
        indexStaticShortcuts()

        // Signal update
        _indexUpdated.emit(Unit)

        android.util.Log.d("SearchRepository", "Full app data reset completed")
      } catch (e: Exception) {
        android.util.Log.e("SearchRepository", "Error during resetAppData", e)
      } finally {
        withContext(Dispatchers.Main) { _isIndexing.value = false }
      }
    }

  suspend fun getRecentItems(
    limit: Int = 10,
    excludedIds: Set<String> = emptySet(),
  ): List<SearchResult> =
    withContext(Dispatchers.IO) {
      val historyIds =
        (context.applicationContext as SearchLauncherApp).historyRepository.historyIds.value

      if (historyIds.isEmpty()) return@withContext emptyList()

      // Filter out already favorited apps and take limit
      val filteredIds = historyIds.filter { !excludedIds.contains(it) }.take(limit)
      if (filteredIds.isEmpty()) return@withContext emptyList()

      val matchingDocs =
        synchronized(documentCache) {
          filteredIds.mapNotNull { id ->
            documentCache.find { it.id == id && it.namespace == "apps" }
          }
        }

      val results = matchingDocs.map { doc -> convertDocumentToResult(doc, 100) }
      return@withContext results
    }

  suspend fun getSearchShortcuts(limit: Int = 100): List<SearchResult> =
    withContext(Dispatchers.IO) {
      try {
        // Get all search shortcuts from synchronized documentCache
        val shortcuts =
          synchronized(documentCache) {
            documentCache.filter { it.namespace == "search_shortcuts" }
          }

        // Sort by manual usage persistence (usageStats) which is the source of truth for sorting
        // among shortcuts
        val sortedShortcuts = shortcuts.sortedByDescending { doc -> usageStats[doc.id] ?: 0 }

        return@withContext coroutineScope {
          sortedShortcuts
            .map { doc -> async { convertDocumentToResult(doc, 100) } }
            .awaitAll()
            .filterIsInstance<SearchResult.SearchIntent>()
            .take(limit)
        }
      } catch (e: Exception) {
        android.util.Log.e("SearchRepository", "Error getting search shortcuts", e)
        return@withContext emptyList()
      }
    }

  suspend fun getFavorites(favoriteIds: List<String>): List<SearchResult> =
    withContext(Dispatchers.IO) {
      try {
        val docs =
          synchronized(documentCache) {
            val idMap = documentCache.filter { favoriteIds.contains(it.id) }.associateBy { it.id }
            favoriteIds.mapNotNull { idMap[it] }
          }
        val freshResults = coroutineScope {
          docs.map { doc -> async { convertDocumentToResult(doc, 100) } }.awaitAll()
        }
        // Update flow and cache with fresh data (including potential new icons or labels)
        _favorites.value = freshResults
        saveFavoritesToCache(freshResults)
        freshResults
      } catch (e: Exception) {
        e.printStackTrace()
        emptyList()
      }
    }

  suspend fun reportUsage(
    namespace: String,
    id: String,
    query: String? = null,
    wasFirstResult: Boolean = false,
  ) =
    withContext(Dispatchers.IO) {
      val session = appSearchSession ?: return@withContext
      try {
        android.util.Log.d(
          "SearchRepository",
          "reportUsage: namespace=$namespace, id=$id, query=$query",
        )

        // Only report usage to AppSearch for namespaces that are actually in the index.
        // smart_actions and others are dynamic and will cause reportUsageAsync to fail.
        if (namespace != "smart_actions" && namespace != "snippets") {
          try {
            val request =
              androidx.appsearch.app.ReportUsageRequest.Builder(namespace, id)
                .setUsageTimestampMillis(System.currentTimeMillis())
                .build()
            session.reportUsageAsync(request).get()
          } catch (e: Exception) {
            // Not in index or other issue, ignore
            android.util.Log.w(
              "SearchRepository",
              "AppSearch reportUsage failed for $namespace:$id",
            )
          }
        }

        // Persistent history for apps
        if (namespace == "apps") {
          (context.applicationContext as SearchLauncherApp).historyRepository.addHistoryItem(id)
          _indexUpdated.emit(Unit)
        }

        // Manual usage persistence
        val count = usageStats[id] ?: 0
        usageStats[id] = count + 1
        saveUsageStats()

        // Only invalidate the cache if we picked a result that wasn't first
        // (meaning the order might change next time)
        if (!wasFirstResult && query != null && query.isNotEmpty()) {
          val firstLetter = query.substring(0, 1)
          if (firstLetter.matches(SINGLE_LETTER_PATTERN)) {
            searchCache.remove(firstLetter.lowercase())
          }
        }

        lastUsageReportTime = System.currentTimeMillis()

        // Auto-bookmark clicked URLs - Use repository scope to ensure it survives UI destruction
        if (id.startsWith("smart_action_url_")) {
          val url = id.removePrefix("smart_action_url_").trim()
          scope.launch {
            android.util.Log.d("SearchRepository", "Found smart action URL, indexing: $url")
            indexWebUrl(url)
          }
        }
      } catch (e: Exception) {
        android.util.Log.e("SearchRepository", "Error reporting usage", e)
      }
    }

  suspend fun indexWebUrl(url: String, title: String? = null) =
    withContext(Dispatchers.IO) {
      val session =
        appSearchSession
          ?: run {
            android.util.Log.e("SearchRepository", "indexWebUrl: session is null")
            return@withContext
          }

      val trimmedUrl = url.trim()
      if (trimmedUrl.isEmpty()) return@withContext

      // Use DataStore for preference
      val shouldStore =
        try {
          context.dataStore.data
            .map { it[MainActivity.PreferencesKeys.STORE_WEB_HISTORY] ?: true }
            .first()
        } catch (e: Exception) {
          android.util.Log.e("SearchRepository", "Error reading store_web_history pref", e)
          true // Proceed if DataStore fails
        }

      if (!shouldStore) {
        android.util.Log.d("SearchRepository", "Web history storage is disabled in settings")
        return@withContext
      }

      val displayTitle =
        title ?: trimmedUrl.removePrefix("https://").removePrefix("http://").removeSuffix("/")

      val doc =
        AppSearchDocument(
          namespace = "web_bookmarks",
          id = "web_${trimmedUrl.hashCode()}",
          name = displayTitle,
          score = 1,
          intentUri = if (!trimmedUrl.startsWith("http")) "https://$trimmedUrl" else trimmedUrl,
          description = trimmedUrl,
        )

      try {
        android.util.Log.d(
          "SearchRepository",
          "Actually putting web doc into AppSearch: ${doc.id} ($trimmedUrl)",
        )
        val request = PutDocumentsRequest.Builder().addDocuments(doc).build()
        val result = session.putAsync(request).get()

        if (result.isSuccess) {
          android.util.Log.d("SearchRepository", "Successfully indexed $trimmedUrl")
        } else {
          android.util.Log.e("SearchRepository", "Failed to index $trimmedUrl: ${result.failures}")
        }

        synchronized(documentCache) {
          documentCache.removeAll { it.id == doc.id }
          documentCache.add(doc)
        }
        _indexUpdated.emit(Unit)
      } catch (e: Exception) {
        android.util.Log.e("SearchRepository", "Exception in indexWebUrl for $trimmedUrl", e)
      }
    }

  suspend fun removeFromIndex(namespace: String, id: String) =
    withContext(Dispatchers.IO) {
      val session = appSearchSession ?: return@withContext
      try {
        val request =
          androidx.appsearch.app.RemoveByDocumentIdRequest.Builder(namespace).addIds(id).build()
        session.removeAsync(request).get()
        synchronized(documentCache) {
          documentCache.removeAll { it.namespace == namespace && it.id == id }
        }
        _indexUpdated.emit(Unit)
      } catch (e: Exception) {
        android.util.Log.e("SearchRepository", "Error removing from index", e)
      }
    }

  private val scope = kotlinx.coroutines.CoroutineScope(Dispatchers.IO)

  private val launcherCallback =
    object : android.content.pm.LauncherApps.Callback() {
      override fun onPackageRemoved(packageName: String, user: android.os.UserHandle) {
        android.util.Log.d("SearchRepository", "onPackageRemoved: $packageName")
        scope.launch {
          indexApps()
          _packageUpdated.emit(packageName)
        }
      }

      override fun onPackageAdded(packageName: String, user: android.os.UserHandle) {
        android.util.Log.d("SearchRepository", "onPackageAdded: $packageName")
        scope.launch {
          indexApps()
          _packageUpdated.emit(null) // null means generic update
        }
      }

      override fun onPackageChanged(packageName: String, user: android.os.UserHandle) {
        scope.launch {
          indexApps()
          _packageUpdated.emit(null)
        }
      }

      override fun onPackagesAvailable(
        packageNames: Array<out String>?,
        user: android.os.UserHandle,
        replacing: Boolean,
      ) {
        scope.launch {
          indexApps()
          _packageUpdated.emit(null)
        }
      }

      override fun onPackagesUnavailable(
        packageNames: Array<out String>?,
        user: android.os.UserHandle,
        replacing: Boolean,
      ) {
        scope.launch {
          indexApps()
          _packageUpdated.emit(null)
        }
      }

      override fun onShortcutsChanged(
        packageName: String,
        shortcuts: List<android.content.pm.ShortcutInfo>,
        user: android.os.UserHandle,
      ) {
        scope.launch { indexShortcuts() }
      }
    }

  suspend fun warmupCache() =
    withContext(Dispatchers.IO) {
      if (!_isInitialized.value) return@withContext

      // Pre-warm cache for single letters/digits
      val chars = "abcdefghijklmnopqrstuvwxyz0123456789".toCharArray()
      for (char in chars) {
        if (!isActive) break

        // Skip if already cached
        val key = char.toString()
        if (!searchCache.containsKey(key)) {
          searchApps(key)
          // Small delay to be unobtrusive
          kotlinx.coroutines.delay(50)
        }
      }
    }

  suspend fun indexContacts() =
    withContext(Dispatchers.IO) {
      searchCache.clear() // Invalidate cache
      val session = appSearchSession ?: return@withContext
      val permission = context.checkSelfPermission(android.Manifest.permission.READ_CONTACTS)
      if (permission != android.content.pm.PackageManager.PERMISSION_GRANTED) {
        android.util.Log.w("SearchRepository", "READ_CONTACTS permission denied")
        return@withContext
      }
      android.util.Log.d("SearchRepository", "Indexing contacts...")

      val contacts = mutableListOf<AppSearchDocument>()
      // 1. Fetch search data (Phone numbers and Emails)
      val searchDataMap = mutableMapOf<Long, StringBuilder>()
      val dataCursor =
        context.contentResolver.query(
          android.provider.ContactsContract.Data.CONTENT_URI,
          arrayOf(
            android.provider.ContactsContract.Data.CONTACT_ID,
            android.provider.ContactsContract.Data.MIMETYPE,
            android.provider.ContactsContract.Data.DATA1,
          ),
          "${android.provider.ContactsContract.Data.MIMETYPE} IN (?, ?)",
          arrayOf(
            android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE,
            android.provider.ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE,
          ),
          null,
        )

      dataCursor?.use {
        val contactIdIndex = it.getColumnIndex(android.provider.ContactsContract.Data.CONTACT_ID)
        val mimeTypeIndex = it.getColumnIndex(android.provider.ContactsContract.Data.MIMETYPE)
        val dataIndex = it.getColumnIndex(android.provider.ContactsContract.Data.DATA1)

        while (it.moveToNext()) {
          val contactId = it.getLong(contactIdIndex)
          val mimeType = it.getString(mimeTypeIndex)
          val data = it.getString(dataIndex)

          if (data != null) {
            val sb = searchDataMap.getOrPut(contactId) { StringBuilder() }
            // Use space as separator for better tokenization
            sb.append(" ").append(data)

            // Normalize phone numbers and add variants (e.g. 06... for +31...)
            if (
              mimeType == android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE
            ) {
              com.searchlauncher.app.util.ContactUtils.getIndexableVariants(data).forEach {
                sb.append(" ").append(it)
              }
            }
          }
        }
      }

      // 2. Fetch Contacts and merge
      val cursor =
        context.contentResolver.query(
          android.provider.ContactsContract.Contacts.CONTENT_URI,
          arrayOf(
            android.provider.ContactsContract.Contacts._ID,
            android.provider.ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
            android.provider.ContactsContract.Contacts.LOOKUP_KEY,
            android.provider.ContactsContract.Contacts.PHOTO_THUMBNAIL_URI,
          ),
          null,
          null,
          null,
        )

      cursor?.use {
        val idIndex = it.getColumnIndex(android.provider.ContactsContract.Contacts._ID)
        val nameIndex =
          it.getColumnIndex(android.provider.ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)
        val lookupIndex = it.getColumnIndex(android.provider.ContactsContract.Contacts.LOOKUP_KEY)
        val photoIndex =
          it.getColumnIndex(android.provider.ContactsContract.Contacts.PHOTO_THUMBNAIL_URI)

        while (it.moveToNext()) {
          val id = it.getLong(idIndex)
          val name = it.getString(nameIndex)
          val lookupKey = it.getString(lookupIndex)
          val photoUri = it.getString(photoIndex)

          if (name != null) {
            val extraData = searchDataMap[id]?.toString() ?: ""
            // Store photoUri + delimiter + searchable text
            val description = "${photoUri ?: ""}|$extraData"

            contacts.add(
              AppSearchDocument(
                namespace = "contacts",
                id = "$lookupKey/$id",
                name = name,
                description = description,
                score = 4,
                intentUri = "content://com.android.contacts/contacts/lookup/$lookupKey/$id",
              )
            )
          }
        }
      }

      if (contacts.isNotEmpty()) {
        android.util.Log.d("SearchRepository", "Indexed ${contacts.size} contacts")
        val putRequest = PutDocumentsRequest.Builder().addDocuments(contacts).build()
        session.putAsync(putRequest).get()
        replaceCollection("contacts", contacts)
      } else {
        android.util.Log.d("SearchRepository", "No contacts found to index")
      }
    }

  suspend fun removeBookmark(id: String) =
    withContext(Dispatchers.IO) {
      val session = appSearchSession ?: return@withContext
      try {
        val request =
          androidx.appsearch.app.RemoveByDocumentIdRequest.Builder("web_bookmarks")
            .addIds(id)
            .build()
        val result = session.removeAsync(request).get()
        if (result.isSuccess) {
          android.util.Log.d(
            "SearchRepository",
            "Successfully removed bookmark from AppSearch: $id",
          )
        } else {
          android.util.Log.e(
            "SearchRepository",
            "AppSearch failed to remove bookmark $id: ${result.failures}",
          )
        }

        synchronized(documentCache) {
          val removed = documentCache.removeAll { it.id == id && it.namespace == "web_bookmarks" }
          android.util.Log.d(
            "SearchRepository",
            "Removed $removed items from documentCache for ID: $id",
          )
        }
        _indexUpdated.emit(Unit)
      } catch (e: Exception) {
        android.util.Log.e("SearchRepository", "Failed to remove bookmark: $id", e)
      }
    }

  suspend fun indexSnippets() =
    withContext(Dispatchers.IO) {
      val session = appSearchSession ?: return@withContext
      val app = context.applicationContext as? SearchLauncherApp ?: return@withContext

      try {
        val snippets = app.snippetsRepository.items.value
        val docs =
          snippets.map { snippet ->
            AppSearchDocument(
              namespace = "snippets",
              id = snippet.alias,
              name = snippet.alias,
              description = snippet.content,
              score = 5,
              // Snippets don't really use intentUri for launching/Action,
              // but we might need a dummy one or update schema if required.
              // Assuming optional or strict checking is handled.
              // Logic in SearchResultItem handles it via clipboard.
              intentUri = "snippet://${snippet.alias}",
              isAction = true,
            )
          }

        if (docs.isNotEmpty()) {
          val putRequest = PutDocumentsRequest.Builder().addDocuments(docs).build()
          session.putAsync(putRequest).get()
          replaceCollection("snippets", docs)
          android.util.Log.d("SearchRepository", "Indexed ${docs.size} snippets")
        }
      } catch (e: Exception) {
        e.printStackTrace()
        android.util.Log.e("SearchRepository", "Failed to index snippets", e)
      }
    }

  private val _allApps = kotlinx.coroutines.flow.MutableStateFlow<List<SearchResult>>(emptyList())
  val allApps = _allApps.asStateFlow()

  private suspend fun updateAppsCache() {
    val apps =
      withContext(Dispatchers.IO) {
        coroutineScope {
          val docs =
            synchronized(documentCache) {
              documentCache.filter { it.namespace == "apps" }.sortedBy { it.name.lowercase() }
            }
          docs.map { doc -> async { convertDocumentToResult(doc, 0) } }.awaitAll()
        }
      }
    _allApps.emit(apps)
  }

  suspend fun getAllApps(): List<SearchResult> {
    if (_allApps.value.isEmpty()) {
      updateAppsCache()
    }
    return _allApps.value
  }

  suspend fun searchApps(query: String, limit: Int = -1): List<SearchResult> =
    withContext(Dispatchers.IO) {
      val startTime = System.currentTimeMillis()
      val session = appSearchSession
      if (session == null) return@withContext emptyList()

      // Check cache for single-letter queries
      if (query.matches(SINGLE_LETTER_PATTERN)) {
        val cached = searchCache[query.lowercase()]
        if (cached != null) {
          return@withContext cached
        }
      }

      val results = mutableListOf<SearchResult>()

      // 1. Custom Shortcuts (Triggers)
      val customShortcutResults = findMatchingCustomShortcut(query)
      results.addAll(customShortcutResults)

      // 2. Snippets
      results.addAll(getSnippetResults(query))

      // 3. Smart Actions
      if (query.isNotEmpty()) {
        results.addAll(smartActionManager.checkSmartActions(query))
      }

      // 4. AppSearch Index
      // Identify active shortcut aliases to exclude them from index results (deduplication)
      val excludedAliases =
        customShortcutResults
          .filterIsInstance<SearchResult.Content>()
          .mapNotNull { result ->
            // Extract alias from ID "shortcut_alias" or just use logic if accessible
            // Actually, findMatchingCustomShortcut uses 'shortcut.alias'.
            // We can infer it or simply rely on the fact that if we have a match,
            // we likely want to exclude THAT specific alias from results.
            // But result.id is "shortcut_y", and doc.description is "y".
            result.id.removePrefix("shortcut_")
          }
          .toSet()

      results.addAll(searchAppIndex(query, excludedAliases, limit))

      results.sortByDescending { it.rankingScore }

      // Cache single-letter queries (but not immediately after usage report)
      if (query.matches(SINGLE_LETTER_PATTERN)) {
        val timeSinceLastUsage = System.currentTimeMillis() - lastUsageReportTime
        if (timeSinceLastUsage > CACHE_COOLDOWN_MS) {
          searchCache[query.lowercase()] = results
        }
      }

      val duration = System.currentTimeMillis() - startTime
      android.util.Log.d(
        "SearchRepository",
        "searchApps for '$query' took ${duration}ms, results: ${results.size}",
      )

      results
    }

  private fun findMatchingCustomShortcut(query: String): List<SearchResult> {
    if (query.isEmpty()) return emptyList()

    val parts = query.split(" ", limit = 2)
    val trigger = parts[0]
    val searchTerm = if (parts.size > 1) parts[1] else ""

    val app = context.applicationContext as? SearchLauncherApp ?: return emptyList()
    val shortcuts = app.searchShortcutRepository.items.value
    var shortcut = shortcuts.find { it.alias.equals(trigger, ignoreCase = true) }

    if (shortcut == null) {
      // Fallback to defaults (e.g. for widgets shortcut if not in DB yet)
      shortcut =
        com.searchlauncher.app.data.DefaultShortcuts.searchShortcuts.find {
          it.alias.equals(trigger, ignoreCase = true)
        }
    }

    if (shortcut == null) return emptyList()

    // Ignore reserved triggers that are now handled by smart actions
    if (
      trigger.equals("call", ignoreCase = true) ||
        trigger.equals("sms", ignoreCase = true) ||
        trigger.equals("mailto", ignoreCase = true)
    ) {
      return emptyList()
    }

    // Special handling for Widget Search
    if (shortcut.id == "widget_search") {
      val appWidgetManager =
        context.getSystemService(Context.APPWIDGET_SERVICE) as android.appwidget.AppWidgetManager
      val installedProviders = appWidgetManager.installedProviders

      val matchingWidgets =
        if (searchTerm.isBlank()) {
          installedProviders
        } else {
          installedProviders.filter {
            it.loadLabel(context.packageManager).contains(searchTerm, ignoreCase = true) ||
              it.provider.packageName.contains(searchTerm, ignoreCase = true)
          }
        }

      return matchingWidgets.map { info ->
        val widgetLabel = info.loadLabel(context.packageManager)
        val providerName = info.provider.flattenToString()
        val widgetIcon =
          info.loadIcon(
            context,
            0,
          ) // Load icon (can be heavy on main thread? This function is suspect)
        // ideally we should load icon async or cache it, but let's try direct first since this is
        // background thread (withContext(Dispatchers.IO) calls searchApps)
        // Actually findMatchingCustomShortcut is called from searchApps which is IO. So it is fine.

        SearchResult.Content(
          id = "widget_${providerName}",
          namespace = "widgets",
          title = widgetLabel,
          subtitle = "${info.minWidth}x${info.minHeight} dp",
          icon = widgetIcon,
          packageName = info.provider.packageName,
          deepLink =
            "intent:#Intent;action=com.searchlauncher.action.BIND_WIDGET;S.component=$providerName;end",
          rankingScore = 200,
        )
      }
    }

    val results = mutableListOf<SearchResult>()
    val icon = iconGenerator.getColoredSearchIcon(shortcut.color, shortcut.alias)

    val url = String.format(shortcut.urlTemplate, java.net.URLEncoder.encode(searchTerm, "UTF-8"))

    // Determine UX based on match type
    val isExactMatch = parts.size == 1 // e.g. "y"
    val subtitle =
      if (isExactMatch) {
        "Press Space to search"
      } else if (searchTerm.isBlank()) {
        "Type your query..."
      } else {
        "Search Shortcut"
      }

    val deepLink =
      if (searchTerm.isBlank()) {
        "intent:#Intent;action=com.searchlauncher.action.APPEND_SPACE;end"
      } else {
        url // Normal behavior: open URL
      }

    results.add(
      SearchResult.Content(
        id = "shortcut_${shortcut.alias}",
        namespace = "search_shortcuts",
        title =
          if (searchTerm.isBlank()) shortcut.description
          else "${shortcut.description}: $searchTerm",
        subtitle = subtitle,
        icon = icon,
        packageName = shortcut.packageName ?: "android",
        deepLink = deepLink, // Use custom deepLink
        rankingScore = if (searchTerm.isBlank()) 150 else 1200,
      )
    )

    val suggestionUrl = shortcut.suggestionUrl
    if (suggestionUrl != null && searchTerm.isNotEmpty()) {
      val suggestions = fetchSuggestions(suggestionUrl, searchTerm)
      suggestions.forEach { suggestion ->
        val suggestionUrlFormatted =
          String.format(shortcut.urlTemplate, java.net.URLEncoder.encode(suggestion, "UTF-8"))
        results.add(
          SearchResult.Content(
            id = "suggestion_${shortcut.alias}_$suggestion",
            namespace = "search_shortcuts",
            title = suggestion,
            subtitle = "${shortcut.description} Suggestion",
            icon = icon,
            packageName = shortcut.packageName ?: "android",
            deepLink = suggestionUrlFormatted,
            rankingScore = 200,
          )
        )
      }
    }
    return results
  }

  private fun getSnippetResults(query: String): List<SearchResult> {
    if (query.isEmpty()) return emptyList()
    val app = context.applicationContext as? SearchLauncherApp ?: return emptyList()
    val clipboardIcon = context.getDrawable(android.R.drawable.ic_menu_edit)

    return app.snippetsRepository.searchItems(query).map { item ->
      val aliasLower = item.alias.lowercase()
      val queryLower = query.lowercase()

      val score =
        when {
          aliasLower == queryLower -> 150 // Exact alias match
          aliasLower.startsWith(queryLower) -> 90 // Prefix alias match
          aliasLower.contains(queryLower) -> 50 // Partial alias match
          else -> 40 // Content match
        }

      SearchResult.Snippet(
        id = "snippet_${item.alias}",
        namespace = "snippets",
        title = item.alias,
        subtitle = item.content.take(50) + if (item.content.length > 50) "..." else "",
        icon = clipboardIcon,
        alias = item.alias,
        content = item.content,
        rankingScore = score,
      )
    }
  }

  private suspend fun searchAppIndex(
    query: String,
    excludedAliases: Set<String>,
    limit: Int,
  ): List<SearchResult> {
    val startTime = System.currentTimeMillis()
    val session = appSearchSession ?: return emptyList()
    val candidates = mutableListOf<Pair<AppSearchDocument, Int>>()

    try {
      val searchSpecBuilder =
        SearchSpec.Builder()
          .setRankingStrategy(SearchSpec.RANKING_STRATEGY_DOCUMENT_SCORE)
          .setTermMatch(SearchSpec.TERM_MATCH_PREFIX)

      if (query.length < 3) {
        searchSpecBuilder.addFilterNamespaces(
          "apps",
          "app_shortcuts",
          "search_shortcuts",
          "web_bookmarks",
        )
      }

      val searchSpec = searchSpecBuilder.build()
      val normalizedQuery = com.searchlauncher.app.util.ContactUtils.normalizePhoneNumber(query)
      val finalQuery =
        if (normalizedQuery != null && normalizedQuery != query && normalizedQuery.length >= 3) {
          "$query OR $normalizedQuery"
        } else {
          query
        }

      val searchResults = session.search(finalQuery, searchSpec)
      var nextPage = searchResults.nextPageAsync.get()

      // 1. Collect documents and calculate scores (Lightweight)
      while (nextPage.isNotEmpty()) {
        for (result in nextPage) {
          val doc = result.genericDocument.toDocumentClass(AppSearchDocument::class.java)
          val manualUsage = usageStats[doc.id] ?: 0
          val baseScore = (result.rankingSignal.toInt() + (manualUsage * 10)).coerceAtLeast(0)

          val isSettings =
            doc.id == "com.android.settings" ||
              doc.intentUri?.contains("android.settings.SETTINGS") == true
          val boost =
            when {
              isSettings -> 15
              doc.namespace == "apps" -> 100
              doc.namespace == "app_shortcuts" -> 90
              doc.namespace == "web_bookmarks" -> 80
              else -> 0
            }

          val nameLower = doc.name.lowercase()
          val queryLower = query.lowercase()
          var matchBoost = 0
          if (nameLower == queryLower) matchBoost = 100
          else if (nameLower.startsWith(queryLower)) matchBoost = 50
          else if (nameLower.contains(queryLower)) matchBoost = 20

          candidates.add(doc to (baseScore + boost + matchBoost))
        }
        // If we have 200 candidates, that's more than enough for display
        if (candidates.size >= 200) break
        nextPage = searchResults.nextPageAsync.get()
      }

      // 2. Sort by our custom refined score
      candidates.sortByDescending { it.second }

      // 3. Convert ONLY the top candidates into SearchResults (Expensive icon loading here)
      val appSearchResults = mutableListOf<SearchResult>()
      val conversionLimit = if (limit > 0) limit * 2 else 50

      for ((doc, score) in candidates.take(conversionLimit)) {
        val searchResult = convertDocumentToResult(doc, score, query)

        if (searchResult is SearchResult.SearchIntent && searchResult.trigger in excludedAliases) {
          continue
        }

        if (searchResult is SearchResult.SearchIntent && query.isNotBlank()) {
          appSearchResults.add(searchResult.copy(title = "${searchResult.title}: $query"))
        } else {
          appSearchResults.add(searchResult)
        }

        if (limit > 0 && appSearchResults.size >= limit) break
      }

      android.util.Log.d(
        "SearchRepository",
        "searchAppIndex for '$query' took ${System.currentTimeMillis() - startTime}ms, candidates: ${candidates.size}, results: ${appSearchResults.size}",
      )

      // Fuzzy Search
      if (query.length >= 2) {
        val existingIds = appSearchResults.map { it.id }.toSet()
        val fuzzyMatches = getFuzzyMatches(query)

        val addedIds = mutableSetOf<String>()
        for ((doc, _) in fuzzyMatches) {
          if (doc.id !in existingIds && doc.id !in addedIds) {
            val boost = if (doc.namespace == "apps") 100 else 0
            val result = convertDocumentToResult(doc, boost, query)
            appSearchResults.add(result)
            addedIds.add(doc.id)
          }
        }
      }

      appSearchResults.sortByDescending { it.rankingScore }
      return if (limit > 0) appSearchResults.take(limit) else appSearchResults
    } catch (e: Exception) {
      e.printStackTrace()
      return emptyList()
    }
  }

  private fun getFuzzyMatches(query: String): List<Pair<AppSearchDocument, Int>> {
    val results = mutableListOf<Pair<AppSearchDocument, Int>>()
    synchronized(documentCache) {
      for (doc in documentCache) {
        val score = FuzzyMatch.calculateScore(query, doc.name)
        if (score > 40) {
          results.add(doc to score)
        }
        // Safety break if cache is somehow malformed/infinite
        if (results.size > 500) break
      }
    }
    return results.sortedByDescending { it.second }.take(50)
  }

  suspend fun searchContent(): List<SearchResult.Content> =
    withContext(Dispatchers.IO) { emptyList() }

  private fun fetchSuggestions(urlTemplate: String, query: String): List<String> {
    val suggestions = mutableListOf<String>()
    try {
      val urlString = String.format(urlTemplate, java.net.URLEncoder.encode(query, "UTF-8"))
      val url = URL(urlString)
      val connection = url.openConnection() as HttpURLConnection
      connection.requestMethod = "GET"
      connection.connectTimeout = 1000 // Reduced from 2000
      connection.readTimeout = 1000 // Reduced from 2000

      if (connection.responseCode == HttpURLConnection.HTTP_OK) {
        val response = connection.inputStream.bufferedReader().use { it.readText() }
        val jsonArray = JSONArray(response)
        if (jsonArray.length() > 1) {
          val suggestionsArray = jsonArray.getJSONArray(1)
          for (i in 0 until suggestionsArray.length()) {
            suggestions.add(suggestionsArray.getString(i))
            if (suggestions.size >= 5) break
          }
        }
      }
    } catch (e: Exception) {
      // Log error
    }
    return suggestions
  }

  fun close() {
    appSearchSession?.close()
    executor.shutdown()
  }

  // Delegation method for icon generation
  fun getColoredSearchIcon(color: Long?, text: String? = null): Drawable? {
    return iconGenerator.getColoredSearchIcon(color, text)
  }

  private suspend fun convertDocumentToResult(
    doc: AppSearchDocument,
    rankingScore: Int,
    query: String? = null,
  ): SearchResult {
    return when (doc.namespace) {
      "shortcuts" -> {
        var icon: Drawable? = iconCache.get("shortcut_${doc.id}")
        if (icon == null) {
          try {
            val launcherApps =
              context.getSystemService(Context.LAUNCHER_APPS_SERVICE)
                as android.content.pm.LauncherApps
            val user = android.os.Process.myUserHandle()
            val q = android.content.pm.LauncherApps.ShortcutQuery()
            val packageName = doc.id.split("/").firstOrNull() ?: ""
            val shortcutId = doc.id.substringAfter("/")
            q.setPackage(packageName)
            q.setShortcutIds(listOf(shortcutId))
            q.setQueryFlags(
              android.content.pm.LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                android.content.pm.LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                android.content.pm.LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED
            )
            val shortcuts = launcherApps.getShortcuts(q, user)
            if (shortcuts != null && shortcuts.isNotEmpty()) {
              icon =
                launcherApps.getShortcutIconDrawable(
                  shortcuts[0],
                  context.resources.displayMetrics.densityDpi,
                )
              if (icon != null) {
                iconCache.put("shortcut_${doc.id}", icon)
              }
            }
          } catch (e: Exception) {
            // Ignore
          }
        }

        val pkg = doc.id.split("/").firstOrNull() ?: ""
        val appIcon =
          try {
            context.packageManager.getApplicationIcon(pkg)
          } catch (e: Exception) {
            null
          }

        SearchResult.Shortcut(
          id = doc.id,
          namespace = "shortcuts",
          title = doc.name,
          subtitle = doc.description ?: "Shortcut",
          icon = icon,
          packageName = pkg,
          intentUri = doc.intentUri ?: "",
          appIcon = appIcon,
          rankingScore = rankingScore,
        )
      }
      "app_shortcuts" -> {
        var icon: Drawable? = iconCache.get("app_shortcut_${doc.id}")
        if (icon == null) {
          icon =
            when {
              doc.intentUri?.contains("android.settings") == true -> {
                try {
                  context.packageManager.getApplicationIcon("com.android.settings")
                } catch (e: Exception) {
                  null
                }
              }
              doc.intentUri?.contains("STILL_IMAGE_CAMERA") == true -> {
                context.getDrawable(android.R.drawable.ic_menu_camera)
              }
              doc.intentUri?.contains("VIDEO_CAMERA") == true -> {
                context.getDrawable(android.R.drawable.ic_menu_camera)
              }
              else -> null
            }
          if (icon != null) {
            iconCache.put("app_shortcut_${doc.id}", icon)
          }
        }

        val isLauncherItem = doc.id.contains("launcher_")
        val launcherIcon =
          if (isLauncherItem) {
            try {
              context.packageManager.getApplicationIcon(context.packageName)
            } catch (e: Exception) {
              null
            }
          } else null

        SearchResult.Content(
          id = doc.id,
          namespace = "app_shortcuts",
          title = doc.name,
          subtitle = if (isLauncherItem) "SearchLauncher" else "Action",
          icon = launcherIcon ?: icon,
          packageName = "android",
          deepLink = doc.intentUri,
          rankingScore = rankingScore,
        )
      }
      "search_shortcuts" -> {
        val alias = doc.description ?: ""
        val app = context.applicationContext as? SearchLauncherApp
        val shortcutDef = app?.searchShortcutRepository?.items?.value?.find { it.alias == alias }
        val icon = iconGenerator.getColoredSearchIcon(shortcutDef?.color, shortcutDef?.alias)

        SearchResult.SearchIntent(
          id = doc.id,
          namespace = "search_shortcuts",
          title = doc.name,
          subtitle = "Type '${doc.description} ' to search",
          icon = icon,
          trigger = doc.description ?: "",
          rankingScore = rankingScore,
        )
      }
      "web_bookmarks" -> {
        val browserIcon =
          context.getDrawable(android.R.drawable.ic_menu_compass)
            ?: context.getDrawable(android.R.drawable.ic_menu_search)
        SearchResult.Content(
          id = doc.id,
          namespace = "web_bookmarks",
          title = doc.name,
          subtitle = "Bookmark",
          icon = browserIcon,
          packageName = "com.android.chrome",
          deepLink = doc.intentUri,
          rankingScore = rankingScore,
        )
      }
      "static_shortcuts" -> {
        var icon: Drawable? = iconCache.get("static_shortcut_${doc.id}")
        if (icon == null) {
          try {
            val pkg = doc.id.substringBefore("/")
            if (doc.iconResId > 0) {
              val res = context.packageManager.getResourcesForApplication(pkg)
              icon = res.getDrawable(doc.iconResId.toInt(), null)
              if (icon != null) {
                iconCache.put("static_shortcut_${doc.id}", icon)
              }
            }
          } catch (e: Exception) {}
        }

        val pkg = doc.id.split("/").firstOrNull() ?: ""
        val appIcon =
          try {
            context.packageManager.getApplicationIcon(pkg)
          } catch (e: Exception) {
            null
          }

        SearchResult.Shortcut(
          id = doc.id,
          namespace = "static_shortcuts",
          title = doc.name,
          subtitle = doc.description ?: "Shortcut",
          icon = icon,
          packageName = pkg,
          intentUri = doc.intentUri ?: "",
          appIcon = appIcon,
          rankingScore = rankingScore,
        )
      }
      "contacts" -> {
        val lookupKey = doc.id.substringBefore("/")
        val contactId = doc.id.substringAfter("/").toLongOrNull() ?: 0L
        val rawDescription = doc.description
        // We stored photo URI + | + extra search data in description
        val photoUri = rawDescription?.substringBefore("|")?.takeIf { it.isNotEmpty() }

        var icon: Drawable? = null
        val cached = iconCache.get("contact_${doc.id}")
        if (cached != null) {
          icon = cached
        } else if (photoUri != null) {
          try {
            val uri = android.net.Uri.parse(photoUri)
            val stream = context.contentResolver.openInputStream(uri)
            icon = Drawable.createFromStream(stream, uri.toString())
            stream?.close()
            if (icon != null) {
              iconCache.put("contact_${doc.id}", icon)
            }
          } catch (e: Exception) {
            // Ignore
          }
        }

        if (icon == null) {
          // Default contact icon
          icon = context.getDrawable(android.R.drawable.sym_contact_card)
          icon?.setTint(Color.GRAY)
        }

        SearchResult.Contact(
          id = doc.id,
          namespace = "contacts",
          title = doc.name,
          subtitle =
            if (query != null && !doc.name.contains(query, ignoreCase = true)) {
              val parts = rawDescription?.split("|")
              val extraData = parts?.getOrNull(1) ?: ""
              // Tokens separated by space
              val tokens = extraData.split(" ")
              val match = tokens.find { it.contains(query, ignoreCase = true) }
              match?.trim() ?: "Contact"
            } else {
              "Contact"
            },
          icon = icon,
          rankingScore = rankingScore,
          lookupKey = lookupKey,
          contactId = contactId,
          photoUri = photoUri,
        )
      }
      else -> { // apps
        val packageName = doc.id
        var icon: Drawable? = null
        val cached = iconCache.get("app_$packageName")
        if (cached != null) {
          icon = cached
        } else {
          try {
            icon = context.packageManager.getApplicationIcon(packageName)
            iconCache.put("app_$packageName", icon)
          } catch (e: Exception) {
            // Ignore
          }
        }

        SearchResult.App(
          id = doc.id,
          namespace = "apps",
          title = doc.name,
          subtitle = doc.description ?: doc.id,
          icon = icon,
          packageName = doc.id,
          rankingScore = rankingScore,
        )
      }
    }
  }

  // --- Persistence for Instant Favorites ---

  private fun getUsageStatsFile() = File(context.filesDir, "usage_stats.json")

  private fun saveUsageStats() {
    scope.launch(Dispatchers.IO) {
      try {
        val obj = JSONObject()
        usageStats.forEach { (key, value) -> obj.put(key, value) }
        getUsageStatsFile().writeText(obj.toString())
      } catch (e: Exception) {
        e.printStackTrace()
      }
    }
  }

  private fun resetUsageStats() {
    usageStats.clear()
    getUsageStatsFile().delete()
  }

  private fun loadUsageStats() {
    val file = getUsageStatsFile()
    if (!file.exists()) return
    try {
      val json = JSONObject(file.readText())
      val keys = json.keys()
      while (keys.hasNext()) {
        val key = keys.next()
        usageStats[key] = json.getInt(key)
      }
    } catch (e: Exception) {
      e.printStackTrace()
    }
  }

  private fun getFavoritesCacheFile() = File(context.filesDir, "favorites_metadata.json")

  private fun getIconDir() = File(context.filesDir, "favorite_icons").apply { mkdirs() }

  private fun sanitizeId(id: String) = id.replace("/", "_").replace(":", "_")

  private fun saveIconToDisk(id: String, drawable: Drawable?) {
    if (drawable == null) return
    val file = File(getIconDir(), "${sanitizeId(id)}.png")
    try {
      val bitmap =
        if (drawable is BitmapDrawable) {
          drawable.bitmap
        } else {
          val b =
            Bitmap.createBitmap(
              drawable.intrinsicWidth.coerceAtLeast(1),
              drawable.intrinsicHeight.coerceAtLeast(1),
              Bitmap.Config.ARGB_8888,
            )
          val canvas = Canvas(b)
          drawable.setBounds(0, 0, canvas.width, canvas.height)
          drawable.draw(canvas)
          b
        }
      FileOutputStream(file).use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
    } catch (e: Exception) {
      e.printStackTrace()
    }
  }

  private fun loadIconFromDisk(id: String): Drawable? {
    val file = File(getIconDir(), "${sanitizeId(id)}.png")
    if (!file.exists()) return null
    return try {
      val bitmap = BitmapFactory.decodeFile(file.absolutePath)
      BitmapDrawable(context.resources, bitmap)
    } catch (e: Exception) {
      null
    }
  }

  private fun saveFavoritesToCache(results: List<SearchResult>) {
    android.util.Log.d("SearchRepository", "Saving ${results.size} favorites to cache...")
    scope.launch(Dispatchers.IO) {
      try {
        val array = JSONArray()
        results.forEach { res ->
          val obj = JSONObject()
          obj.put("id", res.id)
          obj.put("namespace", res.namespace)
          obj.put("title", res.title)
          obj.put("subtitle", res.subtitle ?: "")
          obj.put("type", res::class.java.simpleName)

          when (res) {
            is SearchResult.App -> {
              obj.put("packageName", res.packageName)
            }
            is SearchResult.Content -> {
              obj.put("packageName", res.packageName)
              obj.put("deepLink", res.deepLink ?: "")
            }
            is SearchResult.Shortcut -> {
              obj.put("packageName", res.packageName)
              obj.put("intentUri", res.intentUri)
            }
            is SearchResult.SearchIntent -> {
              obj.put("trigger", res.trigger)
            }
            is SearchResult.Contact -> {
              obj.put("lookupKey", res.lookupKey)
              obj.put("contactId", res.contactId)
              obj.put("photoUri", res.photoUri ?: "")
            }
            is SearchResult.Snippet -> {
              obj.put("alias", res.alias)
              obj.put("content", res.content)
            }
          }
          array.put(obj as Any)
          // Also persist the icon if it's not already on disk or if we want to ensure it's fresh
          saveIconToDisk(res.id, res.icon)
        }
        getFavoritesCacheFile().writeText(array.toString())
      } catch (e: Exception) {
        e.printStackTrace()
      }
    }
  }

  private fun loadFavoritesFromCache(): List<SearchResult> {
    val file = getFavoritesCacheFile()
    if (!file.exists()) return emptyList()
    android.util.Log.d("SearchRepository", "Loading favorites from cache...")
    return try {
      val array = JSONArray(file.readText())
      val results = mutableListOf<SearchResult>()
      for (i in 0 until array.length()) {
        val obj = array.getJSONObject(i)
        val id = obj.getString("id")
        val namespace = obj.getString("namespace")
        val title = obj.getString("title")
        val subtitle = obj.optString("subtitle", "").takeIf { it.isNotEmpty() }
        val type = obj.getString("type")
        val icon = loadIconFromDisk(id)

        val res =
          when (type) {
            "App" ->
              SearchResult.App(
                id = id,
                namespace = namespace,
                title = title,
                subtitle = subtitle,
                icon = icon,
                packageName = obj.getString("packageName"),
              )
            "Content" ->
              SearchResult.Content(
                id = id,
                namespace = namespace,
                title = title,
                subtitle = subtitle,
                icon = icon,
                packageName = obj.getString("packageName"),
                deepLink = obj.optString("deepLink", "").takeIf { it.isNotEmpty() },
              )
            "Shortcut" ->
              SearchResult.Shortcut(
                id = id,
                namespace = namespace,
                title = title,
                subtitle = subtitle,
                icon = icon,
                packageName = obj.getString("packageName"),
                intentUri = obj.getString("intentUri"),
              )
            "SearchIntent" ->
              SearchResult.SearchIntent(
                id = id,
                namespace = namespace,
                title = title,
                subtitle = subtitle,
                icon = icon,
                trigger = obj.getString("trigger"),
              )
            "Contact" ->
              SearchResult.Contact(
                id = id,
                namespace = namespace,
                title = title,
                subtitle = subtitle,
                icon = icon,
                lookupKey = obj.getString("lookupKey"),
                contactId = obj.getLong("contactId"),
                photoUri = obj.optString("photoUri", "").takeIf { it.isNotEmpty() },
              )
            "Snippet" ->
              SearchResult.Snippet(
                id = id,
                namespace = namespace,
                title = title,
                subtitle = subtitle,
                icon = icon,
                alias = obj.getString("alias"),
                content = obj.getString("content"),
              )
            else -> null
          }
        if (res != null) results.add(res)
      }
      android.util.Log.d("SearchRepository", "Loaded ${results.size} favorites from cache")
      results
    } catch (e: Exception) {
      e.printStackTrace()
      emptyList()
    }
  }

  // checkSmartActions extracted to SmartActionManager
}
