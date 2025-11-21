package com.searchlauncher.app.data

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
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
import kotlinx.coroutines.withContext
import org.json.JSONArray

class SearchRepository(private val context: Context) {
        private val documentCache = Collections.synchronizedList(mutableListOf<AppSearchDocument>())
        private var appSearchSession: AppSearchSession? = null
        private val executor = Executors.newSingleThreadExecutor()

        // Tracks whether the search index has been fully initialized.
        // This is used to prevent the UI from querying the index before it's ready,
        // which was causing favorites to not load on fresh installs.
        private val _isInitialized = kotlinx.coroutines.flow.MutableStateFlow(false)
        val isInitialized: kotlinx.coroutines.flow.StateFlow<Boolean> = _isInitialized

        suspend fun initialize() =
                withContext(Dispatchers.IO) {
                        try {
                                val sessionFuture =
                                        LocalStorage.createSearchSessionAsync(
                                                LocalStorage.SearchContext.Builder(
                                                                context,
                                                                "searchlauncher_db"
                                                        )
                                                        .build()
                                        )
                                appSearchSession = sessionFuture.get()

                                val setSchemaRequest =
                                        SetSchemaRequest.Builder()
                                                .addDocumentClasses(AppSearchDocument::class.java)
                                                .build()
                                appSearchSession?.setSchemaAsync(setSchemaRequest)?.get()

                                indexApps()
                                indexCustomShortcuts()
                                indexStaticShortcuts()
                                indexContacts()
                                _isInitialized.value = true
                        } catch (e: Exception) {
                                e.printStackTrace()
                        }
                }

        suspend fun indexCustomShortcuts() =
                withContext(Dispatchers.IO) {
                        val session = appSearchSession ?: return@withContext

                        data class ShortcutInfo(
                                val idSuffix: String,
                                val docDescription: String?,
                                val intentUri: String,
                                val isAction: Boolean
                        )

                        val shortcuts =
                                CustomShortcuts.shortcuts.map { shortcut ->
                                        val info =
                                                when (shortcut) {
                                                        is CustomShortcut.Search -> {
                                                                val trigger = shortcut.trigger
                                                                ShortcutInfo(
                                                                        trigger,
                                                                        trigger,
                                                                        shortcut.urlTemplate,
                                                                        false
                                                                )
                                                        }
                                                        is CustomShortcut.Action -> {
                                                                val slug =
                                                                        shortcut.description
                                                                                .replace(
                                                                                        Regex(
                                                                                                "[^a-zA-Z0-9]"
                                                                                        ),
                                                                                        ""
                                                                                )
                                                                                .lowercase()
                                                                ShortcutInfo(
                                                                        slug,
                                                                        null,
                                                                        shortcut.intentUri,
                                                                        true
                                                                )
                                                        }
                                                }

                                        AppSearchDocument(
                                                namespace = "custom_shortcuts",
                                                id = "custom_${info.idSuffix}",
                                                name = shortcut.description,
                                                score = 3,
                                                description = info.docDescription,
                                                intentUri = info.intentUri,
                                                isAction = info.isAction
                                        )
                                }

                        if (shortcuts.isNotEmpty()) {
                                val putRequest =
                                        PutDocumentsRequest.Builder()
                                                .addDocuments(shortcuts)
                                                .build()
                                session.putAsync(putRequest).get()
                                documentCache.addAll(shortcuts)
                        }
                }

        suspend fun indexStaticShortcuts() =
                withContext(Dispatchers.IO) {
                        val session = appSearchSession ?: return@withContext
                        val shortcuts = StaticShortcutScanner.scan(context)
                        val docs =
                                shortcuts.map { s ->
                                        val appName =
                                                try {
                                                        val appInfo =
                                                                context.packageManager
                                                                        .getApplicationInfo(
                                                                                s.packageName,
                                                                                0
                                                                        )
                                                        context.packageManager
                                                                .getApplicationLabel(appInfo)
                                                                .toString()
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
                                                iconResId = s.iconResId.toLong()
                                        )
                                }

                        if (docs.isNotEmpty()) {
                                val putRequest =
                                        PutDocumentsRequest.Builder().addDocuments(docs).build()
                                session.putAsync(putRequest).get()
                                documentCache.addAll(docs)
                        }
                }

        suspend fun resetIndex() =
                withContext(Dispatchers.IO) {
                        val session = appSearchSession ?: return@withContext
                        try {
                                documentCache.clear()
                                val setSchemaRequest =
                                        SetSchemaRequest.Builder().setForceOverride(true).build()
                                session.setSchemaAsync(setSchemaRequest).get()

                                val initSchemaRequest =
                                        SetSchemaRequest.Builder()
                                                .addDocumentClasses(AppSearchDocument::class.java)
                                                .build()
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
                excludedIds: Set<String> = emptySet()
        ): List<SearchResult> =
                withContext(Dispatchers.IO) {
                        val session = appSearchSession
                        if (session == null) return@withContext emptyList()

                        try {
                                val searchSpec =
                                        SearchSpec.Builder()
                                                .setRankingStrategy(
                                                        SearchSpec
                                                                .RANKING_STRATEGY_USAGE_LAST_USED_TIMESTAMP
                                                )
                                                .setResultCountPerPage(100) // Get more to filter
                                                .build()

                                val searchResults = session.search("", searchSpec)
                                val page = searchResults.nextPageAsync.get()

                                // Only return items that have been used (ranking signal > 0)
                                return@withContext page
                                        .filter { result -> result.rankingSignal > 0 }
                                        .mapNotNull { result ->
                                                val doc =
                                                        result.genericDocument.toDocumentClass(
                                                                AppSearchDocument::class.java
                                                        )
                                                convertDocumentToResult(
                                                        doc,
                                                        result.rankingSignal.toInt()
                                                )
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
                                // Get all custom search shortcuts from cache
                                val searchShortcuts =
                                        synchronized(documentCache) {
                                                documentCache.filter {
                                                        it.namespace == "custom_shortcuts" &&
                                                                !it.isAction
                                                }
                                        }

                                android.util.Log.d(
                                        "SearchRepository",
                                        "Found ${searchShortcuts.size} search shortcuts in cache"
                                )

                                // Try to get usage data from AppSearch to sort them
                                val session = appSearchSession
                                if (session != null) {
                                        try {
                                                val searchSpec =
                                                        SearchSpec.Builder()
                                                                .setRankingStrategy(
                                                                        SearchSpec
                                                                                .RANKING_STRATEGY_USAGE_COUNT
                                                                )
                                                                .addFilterNamespaces(
                                                                        "custom_shortcuts"
                                                                )
                                                                .setResultCountPerPage(50)
                                                                .build()

                                                // Try searching for everything in the namespace to
                                                // get usage stats for all shortcuts
                                                val searchResults = session.search("", searchSpec)
                                                val page = searchResults.nextPageAsync.get()

                                                // Build a map of document ID to usage count
                                                val usageMap =
                                                        page.associate { result ->
                                                                result.genericDocument.id to
                                                                        result.rankingSignal
                                                        }

                                                android.util.Log.d(
                                                        "SearchRepository",
                                                        "Got usage data for ${usageMap.size} shortcuts"
                                                )

                                                // Sort by usage, with unused items at the end
                                                return@withContext searchShortcuts
                                                        .sortedByDescending { doc ->
                                                                usageMap[doc.id] ?: 0.0
                                                        }
                                                        .take(limit)
                                                        .map { doc ->
                                                                convertDocumentToResult(doc, 100)
                                                        }
                                                        .filterIsInstance<
                                                                SearchResult.SearchIntent>()
                                        } catch (e: Exception) {
                                                android.util.Log.e(
                                                        "SearchRepository",
                                                        "Error querying usage",
                                                        e
                                                )
                                        }
                                }

                                // Fallback: return all shortcuts without usage sorting
                                return@withContext searchShortcuts
                                        .take(limit)
                                        .map { doc -> convertDocumentToResult(doc, 100) }
                                        .filterIsInstance<SearchResult.SearchIntent>()
                        } catch (e: Exception) {
                                e.printStackTrace()
                                android.util.Log.e(
                                        "SearchRepository",
                                        "Error getting search shortcuts",
                                        e
                                )
                                return@withContext emptyList()
                        }
                }

        suspend fun getFavorites(favoriteIds: Set<String>): List<SearchResult> =
                withContext(Dispatchers.IO) {
                        try {
                                synchronized(documentCache) {
                                        documentCache.filter { favoriteIds.contains(it.id) }.map {
                                                doc ->
                                                convertDocumentToResult(doc, 100)
                                        }
                                }
                        } catch (e: Exception) {
                                e.printStackTrace()
                                emptyList()
                        }
                }

        suspend fun reportUsage(namespace: String, id: String) =
                withContext(Dispatchers.IO) {
                        val session = appSearchSession ?: return@withContext
                        try {
                                val request =
                                        androidx.appsearch.app.ReportUsageRequest.Builder(
                                                        namespace,
                                                        id
                                                )
                                                .setUsageTimestampMillis(System.currentTimeMillis())
                                                .build()
                                session.reportUsageAsync(request).get()
                        } catch (e: Exception) {
                                e.printStackTrace()
                        }
                }

        private suspend fun indexApps() =
                withContext(Dispatchers.IO) {
                        val session = appSearchSession ?: return@withContext
                        val packageManager = context.packageManager
                        val intent =
                                Intent(Intent.ACTION_MAIN, null).apply {
                                        addCategory(Intent.CATEGORY_LAUNCHER)
                                }

                        val apps =
                                packageManager
                                        .queryIntentActivities(intent, 0)
                                        .filter { resolveInfo ->
                                                val appInfo =
                                                        resolveInfo.activityInfo.applicationInfo
                                                (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) ==
                                                        0 ||
                                                        (appInfo.flags and
                                                                ApplicationInfo
                                                                        .FLAG_UPDATED_SYSTEM_APP) !=
                                                                0
                                        }
                                        .mapNotNull { resolveInfo ->
                                                try {
                                                        val appName =
                                                                resolveInfo
                                                                        .loadLabel(packageManager)
                                                                        .toString()
                                                        val packageName =
                                                                resolveInfo.activityInfo.packageName

                                                        val category =
                                                                if (android.os.Build.VERSION
                                                                                .SDK_INT >=
                                                                                android.os.Build
                                                                                        .VERSION_CODES
                                                                                        .O
                                                                ) {
                                                                        val appInfo =
                                                                                resolveInfo
                                                                                        .activityInfo
                                                                                        .applicationInfo
                                                                        when (appInfo.category) {
                                                                                ApplicationInfo
                                                                                        .CATEGORY_GAME ->
                                                                                        "Game"
                                                                                ApplicationInfo
                                                                                        .CATEGORY_AUDIO ->
                                                                                        "Audio"
                                                                                ApplicationInfo
                                                                                        .CATEGORY_VIDEO ->
                                                                                        "Video"
                                                                                ApplicationInfo
                                                                                        .CATEGORY_IMAGE ->
                                                                                        "Image"
                                                                                ApplicationInfo
                                                                                        .CATEGORY_SOCIAL ->
                                                                                        "Social"
                                                                                ApplicationInfo
                                                                                        .CATEGORY_NEWS ->
                                                                                        "News"
                                                                                ApplicationInfo
                                                                                        .CATEGORY_MAPS ->
                                                                                        "Maps"
                                                                                ApplicationInfo
                                                                                        .CATEGORY_PRODUCTIVITY ->
                                                                                        "Productivity"
                                                                                else ->
                                                                                        "Application"
                                                                        }
                                                                } else {
                                                                        "Application"
                                                                }

                                                        AppSearchDocument(
                                                                namespace = "apps",
                                                                id = packageName,
                                                                name = appName,
                                                                score = 2,
                                                                description = category
                                                        )
                                                } catch (e: Exception) {
                                                        null
                                                }
                                        }

                        if (apps.isNotEmpty()) {
                                val putRequest =
                                        PutDocumentsRequest.Builder().addDocuments(apps).build()
                                session.putAsync(putRequest).get()
                                documentCache.addAll(apps)
                        }

                        indexShortcuts()
                }

        suspend fun indexShortcuts() =
                withContext(Dispatchers.IO) {
                        val session = appSearchSession ?: return@withContext
                        val launcherApps =
                                context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as
                                        android.content.pm.LauncherApps

                        val shortcuts = mutableListOf<AppSearchDocument>()
                        val user = android.os.Process.myUserHandle()

                        try {
                                val activities = launcherApps.getActivityList(null, user)
                                val packages =
                                        activities.map { it.componentName.packageName }.distinct()

                                for (packageName in packages) {
                                        val query = android.content.pm.LauncherApps.ShortcutQuery()
                                        query.setPackage(packageName)
                                        query.setQueryFlags(
                                                android.content.pm.LauncherApps.ShortcutQuery
                                                        .FLAG_MATCH_DYNAMIC or
                                                        android.content.pm.LauncherApps
                                                                .ShortcutQuery
                                                                .FLAG_MATCH_MANIFEST or
                                                        android.content.pm.LauncherApps
                                                                .ShortcutQuery.FLAG_MATCH_PINNED
                                        )

                                        val shortcutList =
                                                try {
                                                        launcherApps.getShortcuts(query, user)
                                                                ?: emptyList()
                                                } catch (e: Exception) {
                                                        emptyList()
                                                }

                                        for (shortcut in shortcutList) {
                                                try {
                                                        val intent =
                                                                try {
                                                                        "shortcut://${shortcut.`package`}/${shortcut.id}"
                                                                } catch (e: Exception) {
                                                                        continue
                                                                }

                                                        shortcuts.add(
                                                                AppSearchDocument(
                                                                        namespace = "shortcuts",
                                                                        id =
                                                                                "${shortcut.`package`}/${shortcut.id}",
                                                                        name =
                                                                                shortcut.shortLabel
                                                                                        ?.toString()
                                                                                        ?: shortcut.longLabel
                                                                                                ?.toString()
                                                                                                ?: "",
                                                                        score = 1,
                                                                        intentUri = intent,
                                                                        description = "Shortcut"
                                                                )
                                                        )
                                                } catch (e: Exception) {
                                                        // Ignore
                                                }
                                        }
                                }

                                if (shortcuts.isNotEmpty()) {
                                        val putRequest =
                                                PutDocumentsRequest.Builder()
                                                        .addDocuments(shortcuts)
                                                        .build()
                                        session.putAsync(putRequest).get()
                                        documentCache.addAll(shortcuts)
                                }
                        } catch (e: Exception) {
                                e.printStackTrace()
                        }
                }

        suspend fun indexContacts() =
                withContext(Dispatchers.IO) {
                        val session = appSearchSession ?: return@withContext
                        if (context.checkSelfPermission(
                                        android.Manifest.permission.READ_CONTACTS
                                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                        ) {
                                return@withContext
                        }

                        val contacts = mutableListOf<AppSearchDocument>()
                        val cursor =
                                context.contentResolver.query(
                                        android.provider.ContactsContract.Contacts.CONTENT_URI,
                                        arrayOf(
                                                android.provider.ContactsContract.Contacts._ID,
                                                android.provider.ContactsContract.Contacts
                                                        .DISPLAY_NAME_PRIMARY,
                                                android.provider.ContactsContract.Contacts
                                                        .LOOKUP_KEY,
                                                android.provider.ContactsContract.Contacts
                                                        .PHOTO_THUMBNAIL_URI
                                        ),
                                        null,
                                        null,
                                        null
                                )

                        cursor?.use {
                                val idIndex =
                                        it.getColumnIndex(
                                                android.provider.ContactsContract.Contacts._ID
                                        )
                                val nameIndex =
                                        it.getColumnIndex(
                                                android.provider.ContactsContract.Contacts
                                                        .DISPLAY_NAME_PRIMARY
                                        )
                                val lookupIndex =
                                        it.getColumnIndex(
                                                android.provider.ContactsContract.Contacts
                                                        .LOOKUP_KEY
                                        )
                                val photoIndex =
                                        it.getColumnIndex(
                                                android.provider.ContactsContract.Contacts
                                                        .PHOTO_THUMBNAIL_URI
                                        )

                                while (it.moveToNext()) {
                                        val id = it.getLong(idIndex)
                                        val name = it.getString(nameIndex)
                                        val lookupKey = it.getString(lookupIndex)
                                        val photoUri = it.getString(photoIndex)

                                        if (name != null) {
                                                contacts.add(
                                                        AppSearchDocument(
                                                                namespace = "contacts",
                                                                id = "$lookupKey/$id",
                                                                name = name,
                                                                description =
                                                                        photoUri, // Storing photo
                                                                // URI in
                                                                // description for
                                                                // simplicity
                                                                score = 4, // Higher priority than
                                                                // apps
                                                                intentUri =
                                                                        "content://com.android.contacts/contacts/lookup/$lookupKey/$id"
                                                        )
                                                )
                                        }
                                }
                        }

                        if (contacts.isNotEmpty()) {
                                val putRequest =
                                        PutDocumentsRequest.Builder().addDocuments(contacts).build()
                                session.putAsync(putRequest).get()
                                documentCache.addAll(contacts)
                        }
                }

        suspend fun searchApps(query: String, limit: Int = -1): List<SearchResult> =
                withContext(Dispatchers.IO) {
                        val session = appSearchSession
                        if (session == null) return@withContext emptyList()

                        val results = mutableListOf<SearchResult>()

                        // 1. Custom Shortcuts (Triggers)
                        val customShortcutResults = findMatchingCustomShortcut(query)
                        results.addAll(customShortcutResults)
                        val filterCustomShortcuts = customShortcutResults.isNotEmpty()

                        // 2. QuickCopy Items
                        results.addAll(getQuickCopyResults(query))

                        // 3. Smart Actions
                        if (query.isNotEmpty()) {
                                results.addAll(checkSmartActions(query))
                        }

                        // 4. AppSearch Index
                        results.addAll(searchAppIndex(query, filterCustomShortcuts, limit))

                        results
                }

        private fun findMatchingCustomShortcut(query: String): List<SearchResult> {
                if (query.isEmpty()) return emptyList()
                val parts = query.split(" ", limit = 2)
                if (parts.size < 2) return emptyList()

                val trigger = parts[0]
                val searchTerm = parts[1]
                val shortcut =
                        CustomShortcuts.shortcuts.filterIsInstance<CustomShortcut.Search>().find {
                                it.trigger.equals(trigger, ignoreCase = true)
                        }
                                ?: return emptyList()

                val results = mutableListOf<SearchResult>()
                val icon = getColoredSearchIcon(shortcut.color, shortcut.trigger)

                val url =
                        String.format(
                                shortcut.urlTemplate,
                                java.net.URLEncoder.encode(searchTerm, "UTF-8")
                        )
                results.add(
                        SearchResult.Content(
                                id = "shortcut_${shortcut.trigger}",
                                namespace = "custom_shortcuts",
                                title = "${shortcut.description}: $searchTerm",
                                subtitle = "Custom Shortcut",
                                icon = icon,
                                packageName = shortcut.packageName ?: "android",
                                deepLink = url
                        )
                )

                val suggestionUrl = shortcut.suggestionUrl
                if (suggestionUrl != null && searchTerm.isNotEmpty()) {
                        val suggestions = fetchSuggestions(suggestionUrl, searchTerm)
                        suggestions.forEach { suggestion ->
                                val suggestionUrlFormatted =
                                        String.format(
                                                shortcut.urlTemplate,
                                                java.net.URLEncoder.encode(suggestion, "UTF-8")
                                        )
                                results.add(
                                        SearchResult.Content(
                                                id = "suggestion_${shortcut.trigger}_$suggestion",
                                                namespace = "custom_shortcuts",
                                                title = suggestion,
                                                subtitle = "${shortcut.description} Suggestion",
                                                icon = icon,
                                                packageName = shortcut.packageName ?: "android",
                                                deepLink = suggestionUrlFormatted
                                        )
                                )
                        }
                }
                return results
        }

        private fun getQuickCopyResults(query: String): List<SearchResult> {
                if (query.isEmpty()) return emptyList()
                val app = context.applicationContext as? SearchLauncherApp ?: return emptyList()
                val clipboardIcon = context.getDrawable(android.R.drawable.ic_menu_edit)

                return app.quickCopyRepository.searchItems(query).map { item ->
                        SearchResult.QuickCopy(
                                id = "quickcopy_${item.alias}",
                                namespace = "quickcopy",
                                title = item.alias,
                                subtitle =
                                        item.content.take(50) +
                                                if (item.content.length > 50) "..." else "",
                                icon = clipboardIcon,
                                alias = item.alias,
                                content = item.content,
                                rankingScore = 95
                        )
                }
        }

        private suspend fun searchAppIndex(
                query: String,
                filterCustomShortcuts: Boolean,
                limit: Int
        ): List<SearchResult> {
                val session = appSearchSession ?: return emptyList()
                val appSearchResults = mutableListOf<SearchResult>()

                try {
                        val searchSpecBuilder =
                                SearchSpec.Builder()
                                        .setRankingStrategy(SearchSpec.RANKING_STRATEGY_USAGE_COUNT)
                                        .setTermMatch(SearchSpec.TERM_MATCH_PREFIX)

                        if (filterCustomShortcuts) {
                                searchSpecBuilder.addFilterNamespaces("apps", "shortcuts")
                        }

                        // Optimization: For short queries, limit scope to apps only
                        if (query.length < 3) {
                                // Restrict to "apps" and "custom_shortcuts" namespace to avoid
                                // expensive contact
                                // lookups/large result sets, but still show search shortcuts
                                searchSpecBuilder.addFilterNamespaces("apps", "custom_shortcuts")
                        }

                        val searchSpec = searchSpecBuilder.build()
                        val searchResults = session.search(query, searchSpec)
                        var nextPage = searchResults.nextPageAsync.get()

                        while (nextPage.isNotEmpty()) {
                                for (result in nextPage) {
                                        val doc =
                                                result.genericDocument.toDocumentClass(
                                                        AppSearchDocument::class.java
                                                )
                                        val baseScore = result.rankingSignal.toInt()
                                        val isSettings =
                                                doc.id == "com.android.settings" ||
                                                        doc.intentUri?.contains(
                                                                "android.settings.SETTINGS"
                                                        ) == true
                                        val boost =
                                                if (isSettings) 15
                                                else if (doc.namespace == "apps") 5 else 0
                                        appSearchResults.add(
                                                convertDocumentToResult(doc, baseScore + boost)
                                        )
                                }
                                if (limit > 0 && appSearchResults.size >= limit * 2) break
                                nextPage = searchResults.nextPageAsync.get()
                        }

                        // Fuzzy Search
                        if (query.length >= 2) {
                                val existingIds = appSearchResults.map { it.id }.toSet()
                                val fuzzyMatches = getFuzzyMatches(query)

                                for ((doc, _) in fuzzyMatches) {
                                        if (doc.id !in existingIds) {
                                                val boost = if (doc.namespace == "apps") 5 else 0
                                                appSearchResults.add(
                                                        convertDocumentToResult(doc, boost)
                                                )
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
                        val urlString =
                                String.format(
                                        urlTemplate,
                                        java.net.URLEncoder.encode(query, "UTF-8")
                                )
                        val url = URL(urlString)
                        val connection = url.openConnection() as HttpURLConnection
                        connection.requestMethod = "GET"
                        connection.connectTimeout = 2000
                        connection.readTimeout = 2000

                        if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                                val response =
                                        connection.inputStream.bufferedReader().use {
                                                it.readText()
                                        }
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

        fun getColoredSearchIcon(color: Long?, text: String? = null): Drawable? {
                if (color == null) return null

                if (text != null) {
                        val density = context.resources.displayMetrics.density
                        // 40dp to pixels - matching the icon size in the UI
                        val size = (40 * density).toInt()

                        val bitmap =
                                android.graphics.Bitmap.createBitmap(
                                        size,
                                        size,
                                        android.graphics.Bitmap.Config.ARGB_8888
                                )
                        val canvas = android.graphics.Canvas(bitmap)

                        // Draw rounded background
                        val paint =
                                android.graphics.Paint().apply {
                                        this.color = color.toInt()
                                        this.isAntiAlias = true
                                        this.style = android.graphics.Paint.Style.FILL
                                }

                        // Draw rounded rect for background
                        val rect = android.graphics.RectF(0f, 0f, size.toFloat(), size.toFloat())
                        val cornerRadius = 8 * density
                        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint)

                        // Draw text
                        val textPaint =
                                android.graphics.Paint().apply {
                                        this.color = Color.WHITE
                                        // Adjust text size to be roughly 50% of the box height
                                        this.textSize = size * 0.5f
                                        this.isAntiAlias = true
                                        this.textAlign = android.graphics.Paint.Align.CENTER
                                        this.typeface =
                                                android.graphics.Typeface.create(
                                                        android.graphics.Typeface.DEFAULT,
                                                        android.graphics.Typeface.BOLD
                                                )
                                }

                        val displayText = text.uppercase()
                        val maxTextWidth = size * 0.85f
                        var currentTextSize = size * 0.5f
                        textPaint.textSize = currentTextSize

                        // Dynamic text scaling
                        while (textPaint.measureText(displayText) > maxTextWidth &&
                                currentTextSize > 10f) {
                                currentTextSize -= 2f
                                textPaint.textSize = currentTextSize
                        }

                        // Center text both horizontally and vertically
                        val xPos = size / 2f
                        val yPos = (size / 2f) - ((textPaint.descent() + textPaint.ascent()) / 2f)

                        canvas.drawText(displayText, xPos, yPos, textPaint)

                        return android.graphics.drawable.BitmapDrawable(context.resources, bitmap)
                }

                val background = GradientDrawable()
                background.shape = GradientDrawable.RECTANGLE
                // Match the 8.dp corner radius used for Contact icons
                val cornerRadius = 8 * context.resources.displayMetrics.density
                background.cornerRadius = cornerRadius
                background.setColor(color.toInt())

                val icon =
                        context.getDrawable(com.searchlauncher.app.R.drawable.ic_search)?.mutate()
                icon?.setTint(Color.WHITE)

                if (icon == null) return background

                val layers = arrayOf(background, icon)
                val layerDrawable = LayerDrawable(layers)

                val inset = (6 * context.resources.displayMetrics.density).toInt()
                layerDrawable.setLayerInset(1, inset, inset, inset, inset)

                return layerDrawable
        }

        private fun convertDocumentToResult(
                doc: AppSearchDocument,
                rankingScore: Int
        ): SearchResult {
                return when (doc.namespace) {
                        "shortcuts" -> {
                                var icon: Drawable? = null
                                try {
                                        val launcherApps =
                                                context.getSystemService(
                                                        Context.LAUNCHER_APPS_SERVICE
                                                ) as
                                                        android.content.pm.LauncherApps
                                        val user = android.os.Process.myUserHandle()
                                        val q = android.content.pm.LauncherApps.ShortcutQuery()
                                        val packageName = doc.id.split("/").firstOrNull() ?: ""
                                        val shortcutId = doc.id.substringAfter("/")
                                        q.setPackage(packageName)
                                        q.setShortcutIds(listOf(shortcutId))
                                        q.setQueryFlags(
                                                android.content.pm.LauncherApps.ShortcutQuery
                                                        .FLAG_MATCH_DYNAMIC or
                                                        android.content.pm.LauncherApps
                                                                .ShortcutQuery
                                                                .FLAG_MATCH_MANIFEST or
                                                        android.content.pm.LauncherApps
                                                                .ShortcutQuery.FLAG_MATCH_PINNED
                                        )
                                        val shortcuts = launcherApps.getShortcuts(q, user)
                                        if (shortcuts != null && shortcuts.isNotEmpty()) {
                                                icon =
                                                        launcherApps.getShortcutIconDrawable(
                                                                shortcuts[0],
                                                                context.resources
                                                                        .displayMetrics
                                                                        .densityDpi
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
                                        rankingScore = rankingScore
                                )
                        }
                        "custom_shortcuts" -> {
                                if (doc.isAction) {
                                        val icon =
                                                if (doc.intentUri?.contains("android.settings") ==
                                                                true
                                                ) {
                                                        try {
                                                                context.packageManager
                                                                        .getApplicationIcon(
                                                                                "com.android.settings"
                                                                        )
                                                        } catch (e: Exception) {
                                                                null
                                                        }
                                                } else {
                                                        null
                                                }

                                        SearchResult.Content(
                                                id = doc.id,
                                                namespace = "custom_shortcuts",
                                                title = doc.name,
                                                subtitle = "Action",
                                                icon = icon,
                                                packageName = "android",
                                                deepLink = doc.intentUri,
                                                rankingScore = rankingScore
                                        )
                                } else {
                                        val trigger = doc.description ?: ""
                                        val shortcutDef =
                                                CustomShortcuts.shortcuts.filterIsInstance<
                                                                CustomShortcut.Search>()
                                                        .find { it.trigger == trigger }
                                        val icon =
                                                getColoredSearchIcon(
                                                        shortcutDef?.color,
                                                        shortcutDef?.trigger
                                                )

                                        SearchResult.SearchIntent(
                                                id = doc.id,
                                                namespace = "custom_shortcuts",
                                                title = doc.name,
                                                subtitle = "Type '${doc.description} ' to search",
                                                icon = icon,
                                                trigger = doc.description ?: "",
                                                rankingScore = rankingScore
                                        )
                                }
                        }
                        "static_shortcuts" -> {
                                var icon: Drawable? = null
                                try {
                                        val pkg = doc.id.substringBefore("/")
                                        if (doc.iconResId > 0) {
                                                val res =
                                                        context.packageManager
                                                                .getResourcesForApplication(pkg)
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
                                        rankingScore = rankingScore
                                )
                        }
                        "contacts" -> {
                                val lookupKey = doc.id.substringBefore("/")
                                val contactId = doc.id.substringAfter("/").toLongOrNull() ?: 0L
                                val photoUri = doc.description // We stored photo URI in description

                                var icon: Drawable? = null
                                if (photoUri != null) {
                                        try {
                                                val uri = android.net.Uri.parse(photoUri)
                                                val stream =
                                                        context.contentResolver.openInputStream(uri)
                                                icon =
                                                        Drawable.createFromStream(
                                                                stream,
                                                                uri.toString()
                                                        )
                                                stream?.close()
                                        } catch (e: Exception) {
                                                // Ignore
                                        }
                                }

                                if (icon == null) {
                                        // Default contact icon
                                        icon =
                                                context.getDrawable(
                                                        android.R.drawable.sym_contact_card
                                                )
                                        icon?.setTint(Color.GRAY)
                                }

                                SearchResult.Contact(
                                        id = doc.id,
                                        namespace = "contacts",
                                        title = doc.name,
                                        subtitle = "Contact",
                                        icon = icon,
                                        rankingScore = rankingScore,
                                        lookupKey = lookupKey,
                                        contactId = contactId,
                                        photoUri = photoUri
                                )
                        }
                        else -> { // apps
                                val packageName = doc.id
                                val icon =
                                        try {
                                                context.packageManager.getApplicationIcon(
                                                        packageName
                                                )
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
                                        rankingScore = rankingScore
                                )
                        }
                }
        }

        private fun checkSmartActions(query: String): List<SearchResult> {
                val results = mutableListOf<SearchResult>()

                // Phone Number Check
                if (android.util.Patterns.PHONE.matcher(query).matches() && query.length >= 3) {
                        // Call Action
                        val callIcon = context.getDrawable(android.R.drawable.sym_action_call)
                        results.add(
                                SearchResult.Content(
                                        id = "smart_action_call_$query",
                                        namespace = "smart_actions",
                                        title = "Call $query",
                                        subtitle = "Phone",
                                        icon = callIcon,
                                        packageName = "com.android.dialer", // Best effort
                                        deepLink = "tel:$query",
                                        rankingScore = 100 // High priority
                                )
                        )

                        // Text Action
                        val messageIcon = context.getDrawable(android.R.drawable.sym_action_chat)
                        results.add(
                                SearchResult.Content(
                                        id = "smart_action_sms_$query",
                                        namespace = "smart_actions",
                                        title = "Text $query",
                                        subtitle = "SMS",
                                        icon = messageIcon,
                                        packageName = "com.android.mms", // Best effort
                                        deepLink = "sms:$query",
                                        rankingScore = 99 // Slightly lower than call
                                )
                        )
                }

                // Email Check
                if (android.util.Patterns.EMAIL_ADDRESS.matcher(query).matches()) {
                        val emailIcon = context.getDrawable(android.R.drawable.sym_action_email)
                        results.add(
                                SearchResult.Content(
                                        id = "smart_action_email_$query",
                                        namespace = "smart_actions",
                                        title = "Send Email to $query",
                                        subtitle = "Email",
                                        icon = emailIcon,
                                        packageName = "com.android.email", // Best effort
                                        deepLink = "mailto:$query",
                                        rankingScore = 100
                                )
                        )
                }

                return results
        }
}
