package com.searchlauncher.app.data

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.Color
import android.graphics.drawable.Drawable
import androidx.appsearch.app.AppSearchSession
import androidx.appsearch.app.PutDocumentsRequest
import androidx.appsearch.app.SearchSpec
import androidx.appsearch.app.SetSchemaRequest
import androidx.appsearch.localstorage.LocalStorage
import com.searchlauncher.app.SearchLauncherApp
import com.searchlauncher.app.util.FuzzyMatch
import com.searchlauncher.app.util.StaticShortcutScanner
import java.net.HttpURLConnection
import java.net.URL
import java.util.Collections
import java.util.concurrent.Executors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray

class SearchRepository(private val context: Context) {
  private val documentCache = Collections.synchronizedList(mutableListOf<AppSearchDocument>())
  private var appSearchSession: AppSearchSession? = null
  private val executor = Executors.newSingleThreadExecutor()
  private val smartActionManager = SmartActionManager(context)
  private val iconGenerator = SearchIconGenerator(context)

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

  // Tracks whether the search index has been fully initialized.
  // This is used to prevent the UI from querying the index before it's ready,
  // which was causing favorites to not load on fresh installs.
  private val _isInitialized = kotlinx.coroutines.flow.MutableStateFlow(false)
  val isInitialized: kotlinx.coroutines.flow.StateFlow<Boolean> = _isInitialized

  private val _indexUpdated = kotlinx.coroutines.flow.MutableSharedFlow<Unit>(replay = 1)
  val indexUpdated: kotlinx.coroutines.flow.SharedFlow<Unit> = _indexUpdated

  suspend fun initialize() =
    withContext(Dispatchers.IO) {
      try {
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

        indexApps()
        indexCustomShortcuts()
        indexStaticShortcuts()
        indexContacts()
        _isInitialized.value = true
      } catch (e: Throwable) {
        e.printStackTrace()
      }
    }

  private suspend fun indexApps() =
    withContext(Dispatchers.IO) {
      android.util.Log.d("SearchRepository", "Indexing apps...")
      searchCache.clear() // Invalidate cache
      val session = appSearchSession ?: return@withContext
      val packageManager = context.packageManager
      val intent = Intent(Intent.ACTION_MAIN, null).apply { addCategory(Intent.CATEGORY_LAUNCHER) }

      val apps =
        packageManager
          .queryIntentActivities(intent, 0)
          .filter { resolveInfo ->
            val appInfo = resolveInfo.activityInfo.applicationInfo
            (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) == 0 ||
              (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
          }
          .mapNotNull { resolveInfo ->
            try {
              val appName = resolveInfo.loadLabel(packageManager).toString()
              val packageName = resolveInfo.activityInfo.packageName

              val category =
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                  val appInfo = resolveInfo.activityInfo.applicationInfo
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
                } else {
                  "Application"
                }

              AppSearchDocument(
                namespace = "apps",
                id = packageName,
                name = appName,
                score = 2,
                description = category,
              )
            } catch (e: Exception) {
              null
            }
          }

      if (apps.isNotEmpty()) {
        val putRequest = PutDocumentsRequest.Builder().addDocuments(apps).build()
        try {
          session.putAsync(putRequest).get()
        } catch (e: Exception) {
          android.util.Log.e("SearchRepository", "Failed to index apps", e)
        }
      }
      synchronized(documentCache) {
        documentCache.clear()
        documentCache.addAll(apps)
      }

      try {
        indexShortcuts()
      } catch (e: Exception) {
        e.printStackTrace()
      }
      updateAppsCache()
    }

  suspend fun indexShortcuts() =
    withContext(Dispatchers.IO) {
      val session = appSearchSession ?: return@withContext
      val launcherApps =
        context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as android.content.pm.LauncherApps

      val shortcuts = mutableListOf<AppSearchDocument>()

      try {
        // 1. Get all profiles (to support work profiles)
        val profiles = launcherApps.profiles

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

                shortcuts.add(
                  AppSearchDocument(
                    namespace = "shortcuts",
                    id = "${shortcut.`package`}/${shortcut.id}",
                    name = name,
                    score = 1,
                    intentUri = intent,
                    description = "Shortcut",
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
          documentCache.addAll(shortcuts)
        }
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
            description = null,
            intentUri = shortcut.intentUri,
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
        documentCache.addAll(allDocs)
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
            description = s.longLabel ?: "Shortcut",
            score = 1,
            intentUri = s.intent.toUri(0),
            iconResId = s.iconResId.toLong(),
          )
        }

      if (docs.isNotEmpty()) {
        val putRequest = PutDocumentsRequest.Builder().addDocuments(docs).build()
        session.putAsync(putRequest).get()
        documentCache.addAll(docs)
      }
    }

  suspend fun resetIndex() =
    withContext(Dispatchers.IO) {
      val session = appSearchSession ?: return@withContext
      try {
        documentCache.clear()
        val setSchemaRequest = SetSchemaRequest.Builder().setForceOverride(true).build()
        session.setSchemaAsync(setSchemaRequest).get()

        val initSchemaRequest =
          SetSchemaRequest.Builder().addDocumentClasses(AppSearchDocument::class.java).build()
        session.setSchemaAsync(initSchemaRequest).get()

        indexApps()
        indexCustomShortcuts()
        indexStaticShortcuts()
        indexContacts()
      } catch (e: Exception) {
        e.printStackTrace()
      }
    }

  suspend fun getRecentItems(
    limit: Int = 10,
    excludedIds: Set<String> = emptySet(),
  ): List<SearchResult> =
    withContext(Dispatchers.IO) {
      val session = appSearchSession
      if (session == null) return@withContext emptyList()

      try {
        val searchSpec =
          SearchSpec.Builder()
            .setRankingStrategy(SearchSpec.RANKING_STRATEGY_USAGE_LAST_USED_TIMESTAMP)
            .setResultCountPerPage(100) // Get more to filter
            .build()

        val searchResults = session.search("", searchSpec)
        val page = searchResults.nextPageAsync.get()

        // Only return items that have been used (ranking signal > 0)
        return@withContext page
          .filter { result -> result.rankingSignal > 0 }
          .mapNotNull { result ->
            val doc = result.genericDocument.toDocumentClass(AppSearchDocument::class.java)
            convertDocumentToResult(doc, result.rankingSignal.toInt())
          }
          .filter { !excludedIds.contains(it.id) }
          .take(limit)
      } catch (e: Exception) {
        e.printStackTrace()
        return@withContext emptyList()
      }
    }

  suspend fun getSearchShortcuts(limit: Int = 10): List<SearchResult> =
    withContext(Dispatchers.IO) {
      try {
        // Get all search shortcuts from cache
        val searchShortcuts =
          synchronized(documentCache) {
            documentCache.filter { it.namespace == "search_shortcuts" }
          }

        // Try to get usage data from AppSearch to sort them
        val session = appSearchSession
        if (session != null) {
          try {
            val searchSpec =
              SearchSpec.Builder()
                .setRankingStrategy(SearchSpec.RANKING_STRATEGY_USAGE_COUNT)
                .addFilterNamespaces("search_shortcuts")
                .setResultCountPerPage(50)
                .build()

            // Try searching for everything in the namespace to
            // get usage stats for all shortcuts
            val searchResults = session.search("", searchSpec)
            val page = searchResults.nextPageAsync.get()

            // Build a map of document ID to usage count
            val usageMap =
              page.associate { result -> result.genericDocument.id to result.rankingSignal }

            // Sort by usage, with unused items at the end
            return@withContext searchShortcuts
              .sortedByDescending { doc -> usageMap[doc.id] ?: 0.0 }
              .take(limit)
              .map { doc -> convertDocumentToResult(doc, 100) }
              .filterIsInstance<SearchResult.SearchIntent>()
          } catch (e: Exception) {
            android.util.Log.e("SearchRepository", "Error querying usage", e)
          }
        }

        // Fallback: return all shortcuts without usage sorting
        return@withContext searchShortcuts
          .take(limit)
          .map { doc -> convertDocumentToResult(doc, 100) }
          .filterIsInstance<SearchResult.SearchIntent>()
      } catch (e: Exception) {
        e.printStackTrace()
        android.util.Log.e("SearchRepository", "Error getting search shortcuts", e)
        return@withContext emptyList()
      }
    }

  suspend fun getFavorites(favoriteIds: Set<String>): List<SearchResult> =
    withContext(Dispatchers.IO) {
      try {
        synchronized(documentCache) {
          documentCache
            .filter { favoriteIds.contains(it.id) }
            .map { doc -> convertDocumentToResult(doc, 100) }
        }
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
        val request =
          androidx.appsearch.app.ReportUsageRequest.Builder(namespace, id)
            .setUsageTimestampMillis(System.currentTimeMillis())
            .build()
        session.reportUsageAsync(request).get()

        // Only invalidate the cache if we picked a result that wasn't first
        // (meaning the order might change next time)
        if (!wasFirstResult && query != null && query.isNotEmpty()) {
          val firstLetter = query.substring(0, 1)
          if (firstLetter.matches(SINGLE_LETTER_PATTERN)) {
            searchCache.remove(firstLetter.lowercase())
          }
        }

        lastUsageReportTime = System.currentTimeMillis()
      } catch (e: Exception) {
        e.printStackTrace()
      }
    }

  private val scope = kotlinx.coroutines.CoroutineScope(Dispatchers.IO)

  private val launcherCallback =
    object : android.content.pm.LauncherApps.Callback() {
      override fun onPackageRemoved(packageName: String, user: android.os.UserHandle) {
        android.util.Log.d("SearchRepository", "onPackageRemoved: $packageName")
        scope.launch { indexApps() }
      }

      override fun onPackageAdded(packageName: String, user: android.os.UserHandle) {
        android.util.Log.d("SearchRepository", "onPackageAdded: $packageName")
        scope.launch { indexApps() }
      }

      override fun onPackageChanged(packageName: String, user: android.os.UserHandle) {
        scope.launch { indexApps() }
      }

      override fun onPackagesAvailable(
        packageNames: Array<out String>?,
        user: android.os.UserHandle,
        replacing: Boolean,
      ) {
        scope.launch { indexApps() }
      }

      override fun onPackagesUnavailable(
        packageNames: Array<out String>?,
        user: android.os.UserHandle,
        replacing: Boolean,
      ) {
        scope.launch { indexApps() }
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
      if (
        context.checkSelfPermission(android.Manifest.permission.READ_CONTACTS) !=
          android.content.pm.PackageManager.PERMISSION_GRANTED
      ) {
        return@withContext
      }

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
            // Use semicolon as separator to distinct formatted values
            sb.append(";").append(data)

            // Normalize phone numbers and add variants (e.g. 06... for +31...)
            if (
              mimeType == android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE
            ) {
              com.searchlauncher.app.util.ContactUtils.getIndexableVariants(data).forEach {
                sb.append(";").append(it)
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
        val putRequest = PutDocumentsRequest.Builder().addDocuments(contacts).build()
        session.putAsync(putRequest).get()
        documentCache.addAll(contacts)
      }
    }

  private val _allApps = kotlinx.coroutines.flow.MutableStateFlow<List<SearchResult>>(emptyList())
  val allApps = _allApps.asStateFlow()

  private suspend fun updateAppsCache() {
    val apps =
      withContext(Dispatchers.IO) {
        synchronized(documentCache) {
          documentCache
            .filter { it.namespace == "apps" }
            .sortedBy { it.name.lowercase() }
            .map { convertDocumentToResult(it, 0) }
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
      val filterCustomShortcuts = customShortcutResults.isNotEmpty()

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
        rankingScore = 150,
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
    val session = appSearchSession ?: return emptyList()
    val appSearchResults = mutableListOf<SearchResult>()

    try {
      val searchSpecBuilder =
        SearchSpec.Builder()
          .setRankingStrategy(SearchSpec.RANKING_STRATEGY_USAGE_COUNT)
          .setTermMatch(SearchSpec.TERM_MATCH_PREFIX)

      // We no longer strictly exclude namespace, because we want fallbacks.
      // We will filter by alias ID later.

      // Optimization: For short queries, limit scope to apps only
      if (query.length < 3) {
        // Include search_shortcuts so we can show them even for short queries if needed,
        // or if we want fallbacks. But standard logic allows search_shortcuts.
        // Just list namespaces explicitly to avoid expensive contacts if short.
        searchSpecBuilder.addFilterNamespaces("apps", "app_shortcuts", "search_shortcuts")
      }

      val searchSpec = searchSpecBuilder.build()
      val searchResults = session.search(query, searchSpec)
      var nextPage = searchResults.nextPageAsync.get()

      while (nextPage.isNotEmpty()) {
        for (result in nextPage) {
          val doc = result.genericDocument.toDocumentClass(AppSearchDocument::class.java)
          val baseScore = result.rankingSignal.toInt()
          val isSettings =
            doc.id == "com.android.settings" ||
              doc.intentUri?.contains("android.settings.SETTINGS") == true
          val boost = if (isSettings) 15 else if (doc.namespace == "apps") 5 else 0

          val name = doc.name
          val queryLower = query.lowercase()
          val nameLower = name.lowercase()

          var matchBoost = 0
          if (nameLower == queryLower) {
            matchBoost = 100 // Exact match
          } else if (nameLower.startsWith(queryLower)) {
            matchBoost = 50 // Prefix match
          } else if (nameLower.contains(queryLower)) {
            matchBoost = 20 // Partial match
          }

          val result = convertDocumentToResult(doc, baseScore + boost + matchBoost, query)

          // Deduplication: If this is a search shortcut and its alias matches an active one, skip
          // it.
          if (result is SearchResult.SearchIntent && result.trigger in excludedAliases) {
            continue
          }

          // Title Formatting: If it's a search shortcut fallback, append the query
          if (result is SearchResult.SearchIntent && query.isNotBlank()) {
            // Create a copy with updated title
            // We need SearchResult structure to support copy, or just cast and copy.
            // SearchIntent is a data class.
            val updatedResult =
              result.copy(title = "${result.title}: $query", subtitle = "Search for '$query'")
            appSearchResults.add(updatedResult)
          } else {
            appSearchResults.add(result)
          }
        }
        if (limit > 0 && appSearchResults.size >= limit * 2) break
        nextPage = searchResults.nextPageAsync.get()
      }

      // Fuzzy Search
      if (query.length >= 2) {
        val existingIds = appSearchResults.map { it.id }.toSet()
        val fuzzyMatches = getFuzzyMatches(query)

        val addedIds = mutableSetOf<String>()
        for ((doc, _) in fuzzyMatches) {
          if (doc.id !in existingIds && doc.id !in addedIds) {
            val boost = if (doc.namespace == "apps") 5 else 0
            appSearchResults.add(convertDocumentToResult(doc, boost, query))
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
    val fuzzyDocs = synchronized(documentCache) { documentCache.toList() }
    return fuzzyDocs
      .map { doc ->
        val score = FuzzyMatch.calculateScore(query, doc.name)
        doc to score
      }
      .filter { it.second > 40 }
      .sortedByDescending { it.second }
      .take(10)
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
      connection.connectTimeout = 2000
      connection.readTimeout = 2000

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

  private fun convertDocumentToResult(
    doc: AppSearchDocument,
    rankingScore: Int,
    query: String? = null,
  ): SearchResult {
    return when (doc.namespace) {
      "shortcuts" -> {
        var icon: Drawable? = null
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
          }
        } catch (e: Exception) {
          // Ignore
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
        val icon =
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

        SearchResult.Content(
          id = doc.id,
          namespace = "app_shortcuts",
          title = doc.name,
          subtitle = "Action",
          icon = icon,
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
      "static_shortcuts" -> {
        var icon: Drawable? = null
        try {
          val pkg = doc.id.substringBefore("/")
          if (doc.iconResId > 0) {
            val res = context.packageManager.getResourcesForApplication(pkg)
            icon = res.getDrawable(doc.iconResId.toInt(), null)
          }
        } catch (e: Exception) {}

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
        if (photoUri != null) {
          try {
            val uri = android.net.Uri.parse(photoUri)
            val stream = context.contentResolver.openInputStream(uri)
            icon = Drawable.createFromStream(stream, uri.toString())
            stream?.close()
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
              // Tokens separated by ;
              val tokens = extraData.split(";")
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
        val icon =
          try {
            context.packageManager.getApplicationIcon(packageName)
          } catch (e: Exception) {
            null
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

  // checkSmartActions extracted to SmartActionManager
}
