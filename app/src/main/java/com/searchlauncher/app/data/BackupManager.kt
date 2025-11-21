package com.searchlauncher.app.data

import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.searchlauncher.app.ui.dataStore
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class BackupManager(
        private val context: Context,
        private val quickCopyRepository: QuickCopyRepository,
        private val customShortcutRepository: CustomShortcutRepository,
        private val favoritesRepository: FavoritesRepository
) {
        companion object {
                const val BACKUP_VERSION = 1
                private const val MAX_IMAGE_SIZE = 10 * 1024 * 1024 // 10MB limit
        }

        suspend fun exportBackup(outputStream: OutputStream, backgroundUri: String?): Result<Int> =
                withContext(Dispatchers.IO) {
                        try {
                                val backupData = JSONObject()
                                backupData.put("version", BACKUP_VERSION)

                                // Export QuickCopy items
                                val quickCopyArray = JSONArray()
                                quickCopyRepository.items.value.forEach { item ->
                                        val obj = JSONObject()
                                        obj.put("alias", item.alias)
                                        obj.put("content", item.content)
                                        quickCopyArray.put(obj)
                                }
                                backupData.put("quickCopy", quickCopyArray)

                                // Export Custom Shortcuts (only Search type, not Action type)
                                val shortcutsArray = JSONArray()
                                customShortcutRepository.items.value.filterIsInstance<
                                                CustomShortcut.Search>()
                                        .forEach { shortcut ->
                                                val obj = JSONObject()
                                                obj.put("type", "search")
                                                obj.put("trigger", shortcut.trigger)
                                                obj.put("urlTemplate", shortcut.urlTemplate)
                                                obj.put("description", shortcut.description)
                                                obj.put("color", shortcut.color)
                                                shortcut.suggestionUrl?.let {
                                                        obj.put("suggestionUrl", it)
                                                }
                                                shortcut.packageName?.let {
                                                        obj.put("packageName", it)
                                                }
                                                shortcutsArray.put(obj)
                                        }
                                backupData.put("customShortcuts", shortcutsArray)

                                // Export Favorites
                                val favoritesArray = JSONArray()
                                favoritesRepository.getFavoriteIds().forEach { id ->
                                        favoritesArray.put(id)
                                }
                                backupData.put("favorites", favoritesArray)

                                // Export Background Image
                                if (backgroundUri != null) {
                                        val imageBase64 =
                                                encodeImageToBase64(Uri.parse(backgroundUri))
                                        backupData.put("backgroundImage", imageBase64)
                                } else {
                                        backupData.put("backgroundImage", JSONObject.NULL)
                                }

                                outputStream.write(backupData.toString(2).toByteArray())
                                outputStream.flush()

                                val totalItems =
                                        quickCopyRepository.items.value.size +
                                                customShortcutRepository.items.value
                                                        .filterIsInstance<CustomShortcut.Search>()
                                                        .size +
                                                favoritesRepository.getFavoriteIds().size

                                Result.success(totalItems)
                        } catch (e: Exception) {
                                Result.failure(e)
                        }
                }

        suspend fun importBackup(inputStream: InputStream): Result<ImportStats> =
                withContext(Dispatchers.IO) {
                        try {
                                val jsonString = inputStream.bufferedReader().use { it.readText() }
                                val backupData = JSONObject(jsonString)

                                val version = backupData.getInt("version")
                                if (version > BACKUP_VERSION) {
                                        return@withContext Result.failure(
                                                Exception(
                                                        "Backup file version ($version) is newer than supported version ($BACKUP_VERSION)"
                                                )
                                        )
                                }

                                var quickCopyCount = 0
                                var shortcutsCount = 0
                                var favoritesCount = 0
                                var backgroundRestored = false

                                // Import QuickCopy items
                                if (backupData.has("quickCopy")) {
                                        val quickCopyArray = backupData.getJSONArray("quickCopy")
                                        for (i in 0 until quickCopyArray.length()) {
                                                val obj = quickCopyArray.getJSONObject(i)
                                                val alias = obj.getString("alias")
                                                val content = obj.getString("content")
                                                quickCopyRepository.addItem(alias, content)
                                                quickCopyCount++
                                        }
                                }

                                // Import Custom Shortcuts (only Search type)
                                if (backupData.has("customShortcuts")) {
                                        val shortcutsArray =
                                                backupData.getJSONArray("customShortcuts")
                                        val searchShortcuts = mutableListOf<CustomShortcut.Search>()

                                        for (i in 0 until shortcutsArray.length()) {
                                                val obj = shortcutsArray.getJSONObject(i)
                                                val type = obj.getString("type")

                                                if (type == "search") {
                                                        val shortcut =
                                                                CustomShortcut.Search(
                                                                        trigger =
                                                                                obj.getString(
                                                                                        "trigger"
                                                                                ),
                                                                        urlTemplate =
                                                                                obj.getString(
                                                                                        "urlTemplate"
                                                                                ),
                                                                        description =
                                                                                obj.getString(
                                                                                        "description"
                                                                                ),
                                                                        color =
                                                                                obj.getLong(
                                                                                        "color"
                                                                                ),
                                                                        suggestionUrl =
                                                                                if (obj.has(
                                                                                                "suggestionUrl"
                                                                                        )
                                                                                )
                                                                                        obj.getString(
                                                                                                "suggestionUrl"
                                                                                        )
                                                                                else null,
                                                                        packageName =
                                                                                if (obj.has(
                                                                                                "packageName"
                                                                                        )
                                                                                )
                                                                                        obj.getString(
                                                                                                "packageName"
                                                                                        )
                                                                                else null
                                                                )
                                                        searchShortcuts.add(shortcut)
                                                        shortcutsCount++
                                                }
                                        }

                                        // Merge with existing Action shortcuts (preserve
                                        // programmatically generated ones)
                                        val existingActions =
                                                customShortcutRepository.items.value
                                                        .filterIsInstance<CustomShortcut.Action>()
                                        val allShortcuts = searchShortcuts + existingActions
                                        customShortcutRepository.replaceAll(allShortcuts)
                                }

                                // Import Favorites
                                if (backupData.has("favorites")) {
                                        val favoritesArray = backupData.getJSONArray("favorites")
                                        val favoriteIds = mutableSetOf<String>()
                                        for (i in 0 until favoritesArray.length()) {
                                                favoriteIds.add(favoritesArray.getString(i))
                                                favoritesCount++
                                        }
                                        favoritesRepository.replaceAll(favoriteIds)
                                }

                                // Import Background Image
                                if (backupData.has("backgroundImage") &&
                                                !backupData.isNull("backgroundImage")
                                ) {
                                        val imageBase64 = backupData.getString("backgroundImage")
                                        val uri = decodeBase64ToImage(imageBase64)
                                        if (uri != null) {
                                                saveBackgroundUri(uri.toString())
                                                backgroundRestored = true
                                        }
                                }

                                Result.success(
                                        ImportStats(
                                                quickCopyCount = quickCopyCount,
                                                shortcutsCount = shortcutsCount,
                                                favoritesCount = favoritesCount,
                                                backgroundRestored = backgroundRestored
                                        )
                                )
                        } catch (e: Exception) {
                                Result.failure(e)
                        }
                }

        private fun encodeImageToBase64(uri: Uri): String? {
                return try {
                        context.contentResolver.openInputStream(uri)?.use { inputStream ->
                                val bytes = inputStream.readBytes()
                                if (bytes.size > MAX_IMAGE_SIZE) {
                                        throw Exception("Image too large (max 10MB)")
                                }
                                Base64.encodeToString(bytes, Base64.NO_WRAP)
                        }
                } catch (e: Exception) {
                        null
                }
        }

        private fun decodeBase64ToImage(base64: String): Uri? {
                return try {
                        val bytes = Base64.decode(base64, Base64.NO_WRAP)
                        val fileName = "background_${System.currentTimeMillis()}.jpg"
                        val file = java.io.File(context.filesDir, fileName)
                        file.writeBytes(bytes)
                        Uri.fromFile(file)
                } catch (e: Exception) {
                        null
                }
        }

        private suspend fun saveBackgroundUri(uri: String) {
                context.dataStore.edit { preferences ->
                        preferences[stringPreferencesKey("background_uri")] = uri
                }
        }

        data class ImportStats(
                val quickCopyCount: Int,
                val shortcutsCount: Int,
                val favoritesCount: Int,
                val backgroundRestored: Boolean
        )
}
