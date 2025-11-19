package com.searchlauncher.app.data

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import androidx.appsearch.app.AppSearchSession
import androidx.appsearch.app.PutDocumentsRequest
import androidx.appsearch.app.SearchSpec
import androidx.appsearch.app.SetSchemaRequest
import androidx.appsearch.localstorage.LocalStorage
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray

class SearchRepository(private val context: Context) {
        // ...

        private var appSearchSession: AppSearchSession? = null
        private val executor = Executors.newSingleThreadExecutor()

        suspend fun initialize() =
                withContext(Dispatchers.IO) {
                        android.util.Log.d("SearchRepository", "initialize: Starting")
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
                                android.util.Log.d(
                                        "SearchRepository",
                                        "initialize: Session created"
                                )

                                // Set schema
                                val setSchemaRequest =
                                        SetSchemaRequest.Builder()
                                                .addDocumentClasses(AppSearchDocument::class.java)
                                                .build()
                                appSearchSession?.setSchemaAsync(setSchemaRequest)?.get()
                                android.util.Log.d("SearchRepository", "initialize: Schema set")

                                // Index apps
                                indexApps()
                                indexCustomShortcuts()
                        } catch (e: Exception) {
                                e.printStackTrace()
                                android.util.Log.e("SearchRepository", "initialize: Error", e)
                        }
                }

        suspend fun indexCustomShortcuts() =
                withContext(Dispatchers.IO) {
                        android.util.Log.d("SearchRepository", "indexCustomShortcuts: Starting")
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
                                                                // Search: id based on trigger,
                                                                // description = trigger
                                                                val trigger = shortcut.trigger
                                                                ShortcutInfo(
                                                                        trigger,
                                                                        trigger,
                                                                        shortcut.urlTemplate,
                                                                        false
                                                                )
                                                        }
                                                        is CustomShortcut.Action -> {
                                                                // Action: id based on description
                                                                // slug
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
                                android.util.Log.d(
                                        "SearchRepository",
                                        "indexCustomShortcuts: Indexed ${shortcuts.size} custom shortcuts"
                                )
                        }
                }

        suspend fun resetIndex() =
                withContext(Dispatchers.IO) {
                        val session = appSearchSession ?: return@withContext
                        android.util.Log.d("SearchRepository", "resetIndex: Clearing all documents")
                        try {
                                // Force clear EVERYTHING including schema
                                val setSchemaRequest =
                                        SetSchemaRequest.Builder()
                                                .setForceOverride(true)
                                                .build() // Empty schema clears everything
                                session.setSchemaAsync(setSchemaRequest).get()

                                // Re-initialize schema
                                val initSchemaRequest =
                                        SetSchemaRequest.Builder()
                                                .addDocumentClasses(AppSearchDocument::class.java)
                                                .build()
                                session.setSchemaAsync(initSchemaRequest).get()

                                // Re-index
                                indexApps()
                                indexCustomShortcuts()
                                android.util.Log.d("SearchRepository", "resetIndex: Complete")
                        } catch (e: Exception) {
                                e.printStackTrace()
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

                                                        // Get category
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
                                                                score = 2, // Apps have higher
                                                                // priority than
                                                                // shortcuts
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
                        }

                        indexShortcuts()
                }

        suspend fun indexShortcuts() =
                withContext(Dispatchers.IO) {
                        android.util.Log.d("SearchRepository", "indexShortcuts: Starting")
                        val session = appSearchSession ?: return@withContext
                        val launcherApps =
                                context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as
                                        android.content.pm.LauncherApps
                        val userManager =
                                context.getSystemService(Context.USER_SERVICE) as
                                        android.os.UserManager

                        val shortcuts = mutableListOf<AppSearchDocument>()
                        val user = android.os.Process.myUserHandle()

                        try {
                                val query = android.content.pm.LauncherApps.ShortcutQuery()
                                query.setQueryFlags(
                                        android.content.pm.LauncherApps.ShortcutQuery
                                                .FLAG_MATCH_DYNAMIC or
                                                android.content.pm.LauncherApps.ShortcutQuery
                                                        .FLAG_MATCH_MANIFEST
                                )

                                val shortcutList =
                                        launcherApps.getShortcuts(query, user) ?: emptyList()
                                android.util.Log.d(
                                        "SearchRepository",
                                        "indexShortcuts: Found ${shortcutList.size} shortcuts"
                                )

                                for (shortcut in shortcutList) {
                                        try {
                                                val intent =
                                                        try {
                                                                // We can't easily get the exact
                                                                // intent for a shortcut
                                                                // without starting it,
                                                                // but we can store the ID and
                                                                // package to launch it via
                                                                // LauncherApps later.
                                                                // However, for AppSearch we need a
                                                                // string.
                                                                // Let's store a custom URI scheme:
                                                                // shortcut://<package>/<id>
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

                                if (shortcuts.isNotEmpty()) {
                                        val putRequest =
                                                PutDocumentsRequest.Builder()
                                                        .addDocuments(shortcuts)
                                                        .build()
                                        session.putAsync(putRequest).get()
                                        android.util.Log.d(
                                                "SearchRepository",
                                                "indexShortcuts: Indexed ${shortcuts.size} shortcuts"
                                        )
                                }
                        } catch (e: Exception) {
                                e.printStackTrace()
                                android.util.Log.e("SearchRepository", "indexShortcuts: Error", e)
                        }
                }

        suspend fun searchApps(query: String, limit: Int = -1): List<SearchResult> =
                withContext(Dispatchers.IO) {
                        val startTime = System.currentTimeMillis()
                        val session = appSearchSession
                        if (session == null) {
                                android.util.Log.w("SearchRepository", "Session not initialized")
                                return@withContext emptyList()
                        }

                        val results = mutableListOf<SearchResult>()

                        // Check for custom shortcuts (Direct use: "g test")
                        // If we match a shortcut trigger, we manually add the result and
                        // filter out the generic "custom_shortcuts" from AppSearch to avoid
                        // duplicates.
                        var filterCustomShortcuts = false

                        if (query.isNotEmpty()) {
                                val parts = query.split(" ", limit = 2)
                                if (parts.size >= 2) {
                                        val trigger = parts[0]
                                        val searchTerm = parts[1]
                                        val shortcut =
                                                CustomShortcuts.shortcuts.filterIsInstance<
                                                                CustomShortcut.Search>()
                                                        .find {
                                                                it.trigger.equals(
                                                                        trigger,
                                                                        ignoreCase = true
                                                                )
                                                        }

                                        if (shortcut != null) {
                                                filterCustomShortcuts = true

                                                // Fetch suggestions if available
                                                val suggestionUrl = shortcut.suggestionUrl
                                                if (suggestionUrl != null && searchTerm.isNotEmpty()
                                                ) {
                                                        val suggestions =
                                                                fetchSuggestions(
                                                                        suggestionUrl,
                                                                        searchTerm
                                                                )
                                                        suggestions.forEach { suggestion ->
                                                                val url =
                                                                        String.format(
                                                                                shortcut.urlTemplate,
                                                                                java.net.URLEncoder
                                                                                        .encode(
                                                                                                suggestion,
                                                                                                "UTF-8"
                                                                                        )
                                                                        )
                                                                results.add(
                                                                        SearchResult.Content(
                                                                                id =
                                                                                        "suggestion_${shortcut.trigger}_$suggestion",
                                                                                namespace =
                                                                                        "custom_shortcuts",
                                                                                title = suggestion,
                                                                                subtitle =
                                                                                        "${shortcut.description} Suggestion",
                                                                                icon = null,
                                                                                packageName =
                                                                                        shortcut.packageName
                                                                                                ?: "android",
                                                                                deepLink = url
                                                                        )
                                                                )
                                                        }
                                                }

                                                val url =
                                                        String.format(
                                                                shortcut.urlTemplate,
                                                                java.net.URLEncoder.encode(
                                                                        searchTerm,
                                                                        "UTF-8"
                                                                )
                                                        )
                                                results.add(
                                                        SearchResult.Content(
                                                                id = "shortcut_${shortcut.trigger}",
                                                                namespace = "custom_shortcuts",
                                                                title =
                                                                        "${shortcut.description}: $searchTerm",
                                                                subtitle = "Custom Shortcut",
                                                                icon = null, // TODO: Add
                                                                // icon
                                                                packageName = shortcut.packageName
                                                                                ?: "android",
                                                                deepLink = url
                                                        )
                                                )
                                        }
                                }
                        }

                        try {
                                val searchSpecBuilder =
                                        SearchSpec.Builder()
                                                .setRankingStrategy(
                                                        SearchSpec.RANKING_STRATEGY_USAGE_COUNT
                                                )
                                                .setTermMatch(SearchSpec.TERM_MATCH_PREFIX)

                                if (filterCustomShortcuts) {
                                        // If we handled a custom shortcut manually, exclude them
                                        // from search
                                        // to avoid "discovery" items appearing
                                        searchSpecBuilder.addFilterNamespaces("apps", "shortcuts")
                                }

                                val searchSpec = searchSpecBuilder.build()

                                val searchResults = session.search(query, searchSpec)
                                var nextPage = searchResults.nextPageAsync.get()

                                while (nextPage.isNotEmpty()) {
                                        for (result in nextPage) {
                                                if (limit > 0 && results.size >= limit) break
                                                val doc =
                                                        result.genericDocument.toDocumentClass(
                                                                AppSearchDocument::class.java
                                                        )
                                                val packageName = doc.id

                                                // Check namespace to distinguish between apps and
                                                // shortcuts
                                                if (result.genericDocument.namespace == "shortcuts"
                                                ) {
                                                        results.add(
                                                                SearchResult.Shortcut(
                                                                        id = doc.id,
                                                                        namespace = "shortcuts",
                                                                        title = doc.name,
                                                                        subtitle = doc.description
                                                                                        ?: "Shortcut",
                                                                        icon = null, // Shortcuts
                                                                        // might not
                                                                        // have easily
                                                                        // accessible icons
                                                                        packageName =
                                                                                doc.id
                                                                                        .split("/")
                                                                                        .firstOrNull()
                                                                                        ?: "",
                                                                        intentUri = doc.intentUri
                                                                                        ?: ""
                                                                )
                                                        )
                                                } else if (result.genericDocument.namespace ==
                                                                "custom_shortcuts"
                                                ) {
                                                        if (doc.isAction) {
                                                                // Direct action (e.g. ADB Wireless)
                                                                results.add(
                                                                        SearchResult.Content(
                                                                                id = doc.id,
                                                                                namespace =
                                                                                        "custom_shortcuts",
                                                                                title = doc.name,
                                                                                subtitle = "Action",
                                                                                icon = null,
                                                                                packageName =
                                                                                        "android",
                                                                                deepLink =
                                                                                        doc.intentUri
                                                                        )
                                                                )
                                                        } else {
                                                                // Search template (e.g. YouTube
                                                                // Search)
                                                                results.add(
                                                                        SearchResult.SearchIntent(
                                                                                id = doc.id,
                                                                                namespace =
                                                                                        "custom_shortcuts",
                                                                                title = doc.name,
                                                                                subtitle =
                                                                                        "Type '${doc.description} ' to search",
                                                                                icon = null,
                                                                                trigger =
                                                                                        doc.description
                                                                                                ?: ""
                                                                        )
                                                                )
                                                        }
                                                } else {
                                                        // It's an app
                                                        val icon =
                                                                try {
                                                                        context.packageManager
                                                                                .getApplicationIcon(
                                                                                        packageName
                                                                                )
                                                                } catch (e: Exception) {
                                                                        null
                                                                }

                                                        results.add(
                                                                SearchResult.App(
                                                                        id = doc.id,
                                                                        namespace = "apps",
                                                                        title = doc.name,
                                                                        subtitle = doc.description
                                                                                        ?: doc.id,
                                                                        icon = icon,
                                                                        packageName = doc.id
                                                                )
                                                        )
                                                }
                                        }
                                        if (limit > 0 && results.size >= limit) break
                                        nextPage = searchResults.nextPageAsync.get()
                                }

                                // Web search fallback
                                if (query.isNotEmpty()) {
                                        results.add(
                                                SearchResult.Content(
                                                        id = "web_search",
                                                        title = "Search \"$query\" in Browser",
                                                        subtitle = "Web Search",
                                                        icon = null, // TODO: Add browser icon
                                                        packageName = "android",
                                                        deepLink =
                                                                "https://www.google.com/search?q=$query"
                                                )
                                        )
                                }
                        } catch (e: Exception) {
                                e.printStackTrace()
                        }

                        val duration = System.currentTimeMillis() - startTime
                        android.util.Log.d(
                                "SearchRepository",
                                "searchApps: took $duration ms for query '$query'"
                        )

                        results
                }

        // ... (rest of the class)

        suspend fun searchContent(query: String): List<SearchResult.Content> =
                withContext(Dispatchers.IO) {
                        // ... (keep existing empty implementation or update if needed)
                        emptyList()
                }

        suspend fun search(query: String): List<SearchResult> =
                withContext(Dispatchers.IO) {
                        val apps = searchApps(query)
                        // val content = searchContent(query) // Commented out for now as it was
                        // empty/dummy
                        apps
                }

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
                        connection.connectTimeout = 2000 // 2 seconds timeout
                        connection.readTimeout = 2000

                        if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                                val response =
                                        connection.inputStream.bufferedReader().use {
                                                it.readText()
                                        }
                                // YouTube API returns JSON like: ["query", ["suggestion1",
                                // "suggestion2", ...], ...]
                                val jsonArray = JSONArray(response)
                                if (jsonArray.length() > 1) {
                                        val suggestionsArray = jsonArray.getJSONArray(1)
                                        for (i in 0 until suggestionsArray.length()) {
                                                suggestions.add(suggestionsArray.getString(i))
                                                if (suggestions.size >= 5)
                                                        break // Limit to 5 suggestions
                                        }
                                }
                        }
                } catch (e: Exception) {
                        // Log error but don't crash search
                        android.util.Log.w(
                                "SearchRepository",
                                "Error fetching suggestions: ${e.message}"
                        )
                }
                return suggestions
        }

        fun close() {
                appSearchSession?.close()
                executor.shutdown()
        }
}
