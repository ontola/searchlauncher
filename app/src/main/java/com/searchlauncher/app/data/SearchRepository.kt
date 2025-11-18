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
                try {
                    val sessionFuture =
                            LocalStorage.createSearchSessionAsync(
                                    LocalStorage.SearchContext.Builder(context, "searchlauncher_db")
                                            .build()
                            )
                    appSearchSession = sessionFuture.get()

                    // Set schema
                    val setSchemaRequest =
                            SetSchemaRequest.Builder()
                                    .addDocumentClasses(AppSearchDocument::class.java)
                                    .build()
                    appSearchSession?.setSchemaAsync(setSchemaRequest)?.get()

                    // Index apps
                    indexApps()
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
                                                id = packageName,
                                                name = appName,
                                                score = 1
                                        )
                                    } catch (e: Exception) {
                                        null
                                    }
                                }

                if (apps.isNotEmpty()) {
                    val putRequest = PutDocumentsRequest.Builder().addDocuments(apps).build()
                    session.putAsync(putRequest).get()
                }
            }

    suspend fun searchApps(query: String): List<SearchResult.App> =
            withContext(Dispatchers.IO) {
                val session = appSearchSession ?: return@withContext emptyList()
                val packageManager = context.packageManager

                if (query.isEmpty()) {
                    // Fallback to PM for empty query or implement "getAll" in AppSearch if needed
                    // For now, let's just return empty or use the old method for "all apps" if that
                    // was the behavior
                    // But the original code filtered by query. If query is empty, it returned
                    // everything matching "" which is everything.
                    // AppSearch with empty query returns everything.
                }

                val searchSpec =
                        SearchSpec.Builder()
                                .setSnippetCount(1)
                                .setRankingStrategy(SearchSpec.RANKING_STRATEGY_DOCUMENT_SCORE)
                                .build()

                val searchResults = session.search(query, searchSpec)
                val results = mutableListOf<SearchResult.App>()

                var nextPage = searchResults.nextPageAsync.get()
                while (nextPage.isNotEmpty()) {
                    for (result in nextPage) {
                        val doc =
                                result.genericDocument.toDocumentClass(
                                        AppSearchDocument::class.java
                                )
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
                    }
                    nextPage = searchResults.nextPageAsync.get()
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
