package com.searchlauncher.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import com.searchlauncher.app.ui.dataStore
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONObject

class BackupManager(
  private val context: Context,
  private val snippetsRepository: SnippetsRepository,
  private val searchShortcutRepository: SearchShortcutRepository,
  private val favoritesRepository: FavoritesRepository,
  private val historyRepository: HistoryRepository,
  private val wallpaperRepository: WallpaperRepository,
  private val widgetRepository: WidgetRepository,
) {
  companion object {
    const val BACKUP_VERSION = 2 // Bumped version for new format
    private const val MAX_IMAGE_SIZE = 10 * 1024 * 1024 // 10MB limit
  }

  suspend fun exportBackup(outputStream: OutputStream, includeWallpapers: Boolean): Result<Int> =
    withContext(Dispatchers.IO) {
      try {
        android.util.Log.d("BackupManager", "Starting export...")
        // Using BufferedWriter and JsonWriter for memory efficiency
        android.util.JsonWriter(outputStream.writer().buffered()).use { writer ->
          writer.setIndent("  ")
          writer.beginObject()

          writer.name("version").value(BACKUP_VERSION)

          // 1. Export Snippets
          android.util.Log.d("BackupManager", "Exporting Snippets...")
          writer.name("snippets")
          writer.beginArray()
          var snippetsCount = 0
          snippetsRepository.items.value.forEach { item ->
            writer.beginObject()
            writer.name("alias").value(item.alias)
            writer.name("content").value(item.content)
            writer.endObject()
            snippetsCount++
          }
          writer.endArray()

          // 2. Export Search Shortcuts
          android.util.Log.d("BackupManager", "Exporting Shortcuts...")
          writer.name("searchShortcuts")
          writer.beginArray()
          var shortcutsCount = 0
          searchShortcutRepository.items.value.forEach { shortcut ->
            writer.beginObject()
            writer.name("id").value(shortcut.id)
            writer.name("alias").value(shortcut.alias)
            writer.name("urlTemplate").value(shortcut.urlTemplate)
            writer.name("description").value(shortcut.description)
            shortcut.color?.let { writer.name("color").value(it) }
            shortcut.suggestionUrl?.let { writer.name("suggestionUrl").value(it) }
            shortcut.packageName?.let { writer.name("packageName").value(it) }
            writer.endObject()
            shortcutsCount++
          }
          writer.endArray()

          // 3. Export Favorites
          android.util.Log.d("BackupManager", "Exporting Favorites...")
          writer.name("favorites")
          writer.beginArray()
          var favoritesCount = 0
          favoritesRepository.getFavoriteIds().forEach { id ->
            writer.value(id)
            favoritesCount++
          }
          writer.endArray()

          // 4. Export History
          android.util.Log.d("BackupManager", "Exporting History...")
          writer.name("history")
          writer.beginArray()
          var historyCount = 0
          historyRepository.historyIds.value.forEach { id ->
            writer.value(id)
            historyCount++
          }
          writer.endArray()

          // 5. Export Wallpapers (Base64)
          var wallpapersCount = 0
          if (includeWallpapers) {
            android.util.Log.d("BackupManager", "Exporting Wallpapers...")
            writer.name("wallpapers")
            writer.beginArray()
            wallpaperRepository.wallpapers.value.forEach { uri ->
              try {
                val file = File(uri.path ?: return@forEach)
                if (file.exists()) {
                  val bytes = file.readBytes()
                  if (bytes.size <= MAX_IMAGE_SIZE) {
                    val base64 =
                      android.util.Base64.encodeToString(bytes, android.util.Base64.DEFAULT)
                    writer.beginObject()
                    writer.name("filename").value(file.name)
                    writer.name("data").value(base64)
                    writer.endObject()
                    wallpapersCount++
                  }
                }
              } catch (e: Exception) {
                e.printStackTrace()
              }
            }
            writer.endArray()
          } else {
            android.util.Log.d("BackupManager", "Skipping Wallpapers (user selection)...")
          }

          // 6. Export Widgets
          android.util.Log.d("BackupManager", "Exporting Widgets...")
          writer.name("widgets")
          writer.beginArray()
          var widgetsCount = 0
          // Fetch widgets before writing
          val widgetsList = widgetRepository.widgets.first()
          widgetsList.forEach { widget ->
            writer.beginObject()
            writer.name("id").value(widget.id)
            widget.height?.let { writer.name("height").value(it) }
            writer.endObject()
            widgetsCount++
          }
          writer.endArray()

          // 7. Export Settings (DataStore)
          android.util.Log.d("BackupManager", "Exporting Settings...")
          writer.name("settings")
          writer.beginObject()
          var settingsCount = 0
          val prefs = context.dataStore.data.first()
          prefs.asMap().forEach { entry ->
            val key = entry.key
            val value = entry.value
            when (value) {
              is String -> {
                writer.name(key.name).value(value)
                settingsCount++
              }
              is Int -> {
                writer.name(key.name).value(value.toLong()) // JsonWriter doesn't have value(Int)
                settingsCount++
              }
              is Long -> {
                writer.name(key.name).value(value)
                settingsCount++
              }
              is Float -> {
                writer.name(key.name).value(value.toDouble())
                settingsCount++
              }
              is Double -> {
                writer.name(key.name).value(value)
                settingsCount++
              }
              is Boolean -> {
                writer.name(key.name).value(value)
                settingsCount++
              }
            }
          }
          writer.endObject()

          writer.endObject() // End root object

          val totalItems =
            snippetsCount +
              shortcutsCount +
              favoritesCount +
              historyCount +
              wallpapersCount +
              widgetsCount +
              settingsCount

          android.util.Log.d("BackupManager", "Export complete. Total items: $totalItems")
          Result.success(totalItems)
        }
      } catch (e: Exception) {
        android.util.Log.e("BackupManager", "Export failed", e)
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

        var snippetsCount = 0
        var shortcutsCount = 0
        var favoritesCount = 0
        var historyCount = 0
        var wallpapersCount = 0
        var widgetsCount = 0
        var settingsCount = 0

        // 1. Import Snippets
        if (backupData.has("snippets")) {
          val snippetsArray = backupData.getJSONArray("snippets")
          snippetsRepository.clearAll()
          for (i in 0 until snippetsArray.length()) {
            val obj = snippetsArray.getJSONObject(i)
            snippetsRepository.addItem(obj.getString("alias"), obj.getString("content"))
            snippetsCount++
          }
        }

        // 2. Import Search Shortcuts
        if (backupData.has("searchShortcuts")) {
          val shortcutsArray = backupData.getJSONArray("searchShortcuts")
          val newShortcuts = mutableListOf<SearchShortcut>()
          for (i in 0 until shortcutsArray.length()) {
            val obj = shortcutsArray.getJSONObject(i)
            newShortcuts.add(
              SearchShortcut(
                id = obj.optString("id", java.util.UUID.randomUUID().toString()),
                alias = obj.getString("alias"),
                urlTemplate = obj.getString("urlTemplate"),
                description = obj.getString("description"),
                color = if (obj.has("color")) obj.getLong("color") else null,
                suggestionUrl = obj.optString("suggestionUrl").takeIf { it.isNotEmpty() },
                packageName = obj.optString("packageName").takeIf { it.isNotEmpty() },
              )
            )
            shortcutsCount++
          }
          searchShortcutRepository.replaceAll(newShortcuts)
        }

        // 3. Import Favorites
        if (backupData.has("favorites")) {
          val favoritesArray = backupData.getJSONArray("favorites")
          val favorites = mutableListOf<String>()
          for (i in 0 until favoritesArray.length()) {
            favorites.add(favoritesArray.getString(i))
            favoritesCount++
          }
          favoritesRepository.replaceAll(favorites)
        }

        // 4. Import History
        if (backupData.has("history")) {
          val historyArray = backupData.getJSONArray("history")
          historyRepository.clearHistory()
          for (i in 0 until historyArray.length()) {
            historyRepository.addHistoryItem(historyArray.getString(i))
            historyCount++
          }
        }

        // 5. Import Wallpapers
        if (backupData.has("wallpapers")) {
          val wallpapersArray = backupData.getJSONArray("wallpapers")
          wallpaperRepository.clearAll()
          val wallpaperDir = File(context.filesDir, "wallpapers").apply { if (!exists()) mkdirs() }
          for (i in 0 until wallpapersArray.length()) {
            try {
              val obj = wallpapersArray.getJSONObject(i)
              val filename = obj.getString("filename")
              val base64 = obj.getString("data")
              val bytes = android.util.Base64.decode(base64, android.util.Base64.DEFAULT)
              File(wallpaperDir, filename).writeBytes(bytes)
              wallpapersCount++
            } catch (e: Exception) {
              e.printStackTrace()
            }
          }
          wallpaperRepository.reload()
        }

        // 6. Import Widgets
        if (backupData.has("widgets")) {
          val widgetsArray = backupData.getJSONArray("widgets")
          widgetRepository.clearAllWidgets()
          for (i in 0 until widgetsArray.length()) {
            val obj = widgetsArray.getJSONObject(i)
            val id = obj.getInt("id")
            widgetRepository.addWidgetId(id)
            if (obj.has("height")) {
              widgetRepository.updateWidgetHeight(id, obj.getInt("height"))
            }
            widgetsCount++
          }
        }

        // 7. Import Settings
        if (backupData.has("settings")) {
          val settingsObj = backupData.getJSONObject("settings")
          context.dataStore.edit { prefs ->
            settingsObj.keys().forEach { keyName ->
              val value = settingsObj.get(keyName)
              when (value) {
                is String ->
                  prefs[androidx.datastore.preferences.core.stringPreferencesKey(keyName)] = value
                is Int ->
                  prefs[androidx.datastore.preferences.core.intPreferencesKey(keyName)] = value
                is Long ->
                  prefs[androidx.datastore.preferences.core.longPreferencesKey(keyName)] = value
                is Double ->
                  prefs[androidx.datastore.preferences.core.floatPreferencesKey(keyName)] =
                    value.toFloat()
                is Boolean ->
                  prefs[androidx.datastore.preferences.core.booleanPreferencesKey(keyName)] = value
              }
              settingsCount++
            }
          }
        }

        Result.success(
          ImportStats(
            snippetsCount = snippetsCount,
            shortcutsCount = shortcutsCount,
            favoritesCount = favoritesCount,
            historyCount = historyCount,
            wallpapersCount = wallpapersCount,
            widgetsCount = widgetsCount,
            settingsCount = settingsCount,
            backgroundRestored = wallpapersCount > 0,
          )
        )
      } catch (e: Exception) {
        Result.failure(e)
      }
    }

  data class ImportStats(
    val snippetsCount: Int,
    val shortcutsCount: Int,
    val favoritesCount: Int,
    val historyCount: Int,
    val wallpapersCount: Int,
    val widgetsCount: Int,
    val settingsCount: Int,
    val backgroundRestored: Boolean,
  )
}
