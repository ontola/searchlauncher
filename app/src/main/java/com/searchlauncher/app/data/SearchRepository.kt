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

        suspend fun searchApps(query: String, limit: Int = -1): List<SearchResult> =
                withContext(Dispatchers.IO) {
                        val session = appSearchSession
                        if (session == null) return@withContext emptyList()

                        val results = mutableListOf<SearchResult>()
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
                                                val icon = getColoredSearchIcon(shortcut.color)

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
                                                                icon = icon,
                                                                packageName = shortcut.packageName
                                                                                ?: "android",
                                                                deepLink = url
                                                        )
                                                )

                                                val suggestionUrl = shortcut.suggestionUrl
                                                if (suggestionUrl != null && searchTerm.isNotEmpty()
                                                ) {
                                                        val suggestions =
                                                                fetchSuggestions(
                                                                        suggestionUrl,
                                                                        searchTerm
                                                                )
                                                        suggestions.forEach { suggestion ->
                                                                val suggestionUrlFormatted =
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
                                                                                icon = icon,
                                                                                packageName =
                                                                                        shortcut.packageName
                                                                                                ?: "android",
                                                                                deepLink =
                                                                                        suggestionUrlFormatted
                                                                        )
                                                                )
                                                        }
                                                }
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
                                        searchSpecBuilder.addFilterNamespaces("apps", "shortcuts")
                                }

                                val searchSpec = searchSpecBuilder.build()
                                val searchResults = session.search(query, searchSpec)
                                var nextPage = searchResults.nextPageAsync.get()
                                val appSearchResults = mutableListOf<SearchResult>()

                                while (nextPage.isNotEmpty()) {
                                        for (result in nextPage) {
                                                val doc =
                                                        result.genericDocument.toDocumentClass(
                                                                AppSearchDocument::class.java
                                                        )
                                                val baseScore = result.rankingSignal.toInt()
                                                val boost = if (doc.namespace == "apps") 5 else 0
                                                appSearchResults.add(
                                                        convertDocumentToResult(
                                                                doc,
                                                                baseScore + boost
                                                        )
                                                )
                                        }
                                        if (limit > 0 && appSearchResults.size >= limit * 2) break
                                        nextPage = searchResults.nextPageAsync.get()
                                }

                                // Fuzzy Search
                                if (query.length >= 2) {
                                        val existingIds = appSearchResults.map { it.id }.toSet()
                                        val fuzzyDocs =
                                                synchronized(documentCache) {
                                                        documentCache.toList()
                                                }

                                        val fuzzyMatches =
                                                fuzzyDocs
                                                        .map { doc ->
                                                                val score =
                                                                        FuzzyMatch.calculateScore(
                                                                                query,
                                                                                doc.name
                                                                        )
                                                                doc to score
                                                        }
                                                        .filter { it.second > 40 }
                                                        .sortedByDescending { it.second }
                                                        .take(10)

                                        for ((doc, score) in fuzzyMatches) {
                                                if (doc.id !in existingIds) {
                                                        val boost =
                                                                if (doc.namespace == "apps") 5
                                                                else 0
                                                        appSearchResults.add(
                                                                convertDocumentToResult(doc, boost)
                                                        ) // Base score 0 for fuzzy
                                                }
                                        }
                                }

                                appSearchResults.sortByDescending { it.rankingScore }
                                if (limit > 0) {
                                        results.addAll(appSearchResults.take(limit))
                                } else {
                                        results.addAll(appSearchResults)
                                }

                                if (query.isNotEmpty()) {
                                        results.add(
                                                SearchResult.Content(
                                                        id = "web_search",
                                                        title = "Search \"$query\" in Browser",
                                                        subtitle = "Web Search",
                                                        icon = null,
                                                        packageName = "android",
                                                        deepLink =
                                                                "https://www.google.com/search?q=$query"
                                                )
                                        )
                                }
                        } catch (e: Exception) {
                                e.printStackTrace()
                        }

                        results
                }

        suspend fun searchContent(query: String): List<SearchResult.Content> =
                withContext(Dispatchers.IO) { emptyList() }

        suspend fun search(query: String): List<SearchResult> =
                withContext(Dispatchers.IO) { searchApps(query) }

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

        private fun getColoredSearchIcon(color: Long?): Drawable? {
                if (color == null) return null

                val background = GradientDrawable()
                background.shape = GradientDrawable.OVAL
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
                                        SearchResult.Content(
                                                id = doc.id,
                                                namespace = "custom_shortcuts",
                                                title = doc.name,
                                                subtitle = "Action",
                                                icon = null,
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
                                        val icon = getColoredSearchIcon(shortcutDef?.color)

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
}
