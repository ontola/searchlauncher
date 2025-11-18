package com.searchlauncher.app.data

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.appsearch.app.AppSearchSession
import androidx.appsearch.app.PutDocumentsRequest
import androidx.appsearch.app.SearchSpec
import androidx.appsearch.app.SetSchemaRequest
import androidx.appsearch.localstorage.LocalStorage
import java.util.concurrent.Executors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SearchRepository(private val context: Context) {

    private var appSearchSession: AppSearchSession? = null
    private val executor = Executors.newSingleThreadExecutor()

    suspend fun initialize() =
            withContext(Dispatchers.IO) {
                android.util.Log.d("SearchRepository", "initialize: Starting")
                try {
                    val sessionFuture =
                            LocalStorage.createSearchSessionAsync(
                                    LocalStorage.SearchContext.Builder(context, "searchlauncher_db")
                                            .build()
                            )
                    appSearchSession = sessionFuture.get()
                    android.util.Log.d("SearchRepository", "initialize: Session created")

                    // Set schema
                    val setSchemaRequest =
                            SetSchemaRequest.Builder()
                                    .addDocumentClasses(AppSearchDocument::class.java)
                                    .build()
                    appSearchSession?.setSchemaAsync(setSchemaRequest)?.get()
                    android.util.Log.d("SearchRepository", "initialize: Schema set")

                    // Index apps
                    indexApps()
                } catch (e: Exception) {
                    e.printStackTrace()
                    android.util.Log.e("SearchRepository", "initialize: Error", e)
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
                                    val appInfo = resolveInfo.activityInfo.applicationInfo
                                    (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) == 0 ||
                                            (appInfo.flags and
                                                    ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
                                }
                                .mapNotNull { resolveInfo ->
                                    try {
                                        val appName =
                                                resolveInfo.loadLabel(packageManager).toString()
                                        val packageName = resolveInfo.activityInfo.packageName

                                        AppSearchDocument(
                                                namespace = "apps",
                                                id = packageName,
                                                name = appName,
                                                score = 2 // Apps have higher priority than
                                                // shortcuts
                                                )
                                    } catch (e: Exception) {
                                        null
                                    }
                                }

                if (apps.isNotEmpty()) {
                    val putRequest = PutDocumentsRequest.Builder().addDocuments(apps).build()
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
                        context.getSystemService(Context.USER_SERVICE) as android.os.UserManager

                val shortcuts = mutableListOf<AppSearchDocument>()
                val user = android.os.Process.myUserHandle()

                try {
                    val query = android.content.pm.LauncherApps.ShortcutQuery()
                    query.setQueryFlags(
                            android.content.pm.LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                                    android.content.pm.LauncherApps.ShortcutQuery
                                            .FLAG_MATCH_MANIFEST
                    )

                    val shortcutList = launcherApps.getShortcuts(query, user) ?: emptyList()
                    android.util.Log.d(
                            "SearchRepository",
                            "indexShortcuts: Found ${shortcutList.size} shortcuts"
                    )

                    for (shortcut in shortcutList) {
                        try {
                            val intent =
                                    try {
                                        // We can't easily get the exact intent for a shortcut
                                        // without starting it,
                                        // but we can store the ID and package to launch it via
                                        // LauncherApps later.
                                        // However, for AppSearch we need a string.
                                        // Let's store a custom URI scheme:
                                        // shortcut://<package>/<id>
                                        "shortcut://${shortcut.`package`}/${shortcut.id}"
                                    } catch (e: Exception) {
                                        continue
                                    }

                            shortcuts.add(
                                    AppSearchDocument(
                                            namespace = "shortcuts",
                                            id = "${shortcut.`package`}/${shortcut.id}",
                                            name = shortcut.shortLabel?.toString()
                                                            ?: shortcut.longLabel?.toString() ?: "",
                                            score = 1,
                                            intentUri = intent
                                    )
                            )
                        } catch (e: Exception) {
                            // Ignore
                        }
                    }

                    if (shortcuts.isNotEmpty()) {
                        val putRequest =
                                PutDocumentsRequest.Builder().addDocuments(shortcuts).build()
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

    suspend fun searchApps(query: String): List<SearchResult> =
            withContext(Dispatchers.IO) {
                android.util.Log.d("SearchRepository", "searchApps: Query='$query'")
                val session = appSearchSession ?: return@withContext emptyList()
                val packageManager = context.packageManager
                val launcherApps =
                        context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as
                                android.content.pm.LauncherApps

                val searchSpec =
                        SearchSpec.Builder()
                                .setSnippetCount(1)
                                .setRankingStrategy(SearchSpec.RANKING_STRATEGY_DOCUMENT_SCORE)
                                .build()

                val searchResults = session.search(query, searchSpec)
                val results = mutableListOf<SearchResult>()

                var nextPage = searchResults.nextPageAsync.get()
                while (nextPage.isNotEmpty()) {
                    for (result in nextPage) {
                        val doc =
                                result.genericDocument.toDocumentClass(
                                        AppSearchDocument::class.java
                                )
                        android.util.Log.d(
                                "SearchRepository",
                                "searchApps: Found doc ${doc.id} in namespace ${doc.namespace}"
                        )
                        if (doc.namespace == "apps") {
                            try {
                                val icon = packageManager.getApplicationIcon(doc.id)
                                results.add(
                                        SearchResult.App(
                                                id = doc.id,
                                                title = doc.name,
                                                subtitle = doc.id,
                                                icon = icon,
                                                packageName = doc.id
                                        )
                                )
                            } catch (e: PackageManager.NameNotFoundException) {
                                // App might have been uninstalled
                            }
                        } else if (doc.namespace == "shortcuts") {
                            try {
                                // Parse ID: shortcut://<package>/<id>
                                // Actually we stored doc.id as <package>/<id>
                                val parts = doc.id.split("/")
                                if (parts.size == 2) {
                                    val pkg = parts[0]
                                    val id = parts[1]

                                    // To get icon we need to query LauncherApps again or cache it.
                                    // For now, let's try to get it.
                                    // This might be slow in a loop, ideally we cache or load
                                    // lazily.
                                    // For MVP, let's use app icon or generic icon.
                                    val appIcon =
                                            try {
                                                packageManager.getApplicationIcon(pkg)
                                            } catch (e: Exception) {
                                                null
                                            }

                                    results.add(
                                            SearchResult.Shortcut(
                                                    id = doc.id,
                                                    title = doc.name,
                                                    subtitle = "Action", // Could be app name
                                                    icon = appIcon, // Placeholder
                                                    packageName = pkg,
                                                    intentUri = doc.intentUri ?: ""
                                            )
                                    )
                                }
                            } catch (e: Exception) {
                                // Ignore
                            }
                        }
                    }
                    nextPage = searchResults.nextPageAsync.get()
                }

                // Add "Search in Browser" action
                if (query.isNotEmpty()) {
                    // Check for custom shortcuts
                    val parts = query.split(" ", limit = 2)
                    if (parts.size >= 2) {
                        val trigger = parts[0]
                        val searchTerm = parts[1]
                        val shortcut = CustomShortcuts.shortcuts.find { it.trigger == trigger }

                        if (shortcut != null) {
                            val url =
                                    String.format(
                                            shortcut.urlTemplate,
                                            java.net.URLEncoder.encode(searchTerm, "UTF-8")
                                    )
                            results.add(
                                    0, // Add to top
                                    SearchResult.Content(
                                            id = "shortcut_${shortcut.trigger}",
                                            title = "${shortcut.description}: $searchTerm",
                                            subtitle = "Custom Shortcut",
                                            icon = null, // TODO: Add icon
                                            packageName = shortcut.packageName ?: "android",
                                            deepLink = url
                                    )
                            )
                        }
                    }

                    results.add(
                            SearchResult.Content(
                                    id = "web_search",
                                    title = "Search \"$query\" in Browser",
                                    subtitle = "Web Search",
                                    icon = null, // TODO: Add browser icon
                                    packageName = "android",
                                    deepLink = "https://www.google.com/search?q=$query"
                            )
                    )
                }

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
                // val content = searchContent(query) // Commented out for now as it was empty/dummy
                apps
            }

    fun close() {
        appSearchSession?.close()
        executor.shutdown()
    }
}
