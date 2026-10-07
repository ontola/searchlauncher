package com.searchlauncher.app.data

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import androidx.datastore.preferences.core.edit
import androidx.test.platform.app.InstrumentationRegistry
import com.searchlauncher.app.SearchLauncherApp
import com.searchlauncher.app.ui.PreferencesKeys
import com.searchlauncher.app.ui.dataStore
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** Real AppSearch and icon-file round trip, with no site visit or network dependency. */
class BookmarkFaviconBackupTest {
  @Test
  fun restoredBookmarkAndFavoriteResolveTheBackedUpIconWithHistoryDisabled() = runBlocking {
    val app =
      InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        as SearchLauncherApp
    val search = app.searchRepository
    withTimeout(30_000) { search.isInitialized.first { it } }
    val runId = java.util.UUID.randomUUID().toString()
    val sourceHost = "source-$runId.test"
    val restoredHost = "restored-$runId.test"
    val sourceUrl = "https://$sourceHost/page"
    val restoredUrl = "https://$restoredHost/page"
    val restoredId = "saved_${restoredUrl.hashCode()}"
    val favoriteKey = FavoriteKeys.of("web_saved", restoredId)
    val oldHistory = app.dataStore.data.first()[PreferencesKeys.STORE_WEB_HISTORY]
    val manager =
      BackupManager(
        app,
        app.snippetsRepository,
        app.searchShortcutRepository,
        app.favoritesRepository,
        app.historyRepository,
        app.wallpaperRepository,
        app.widgetRepository,
        search,
      )
    try {
      app.dataStore.edit { it[PreferencesKeys.STORE_WEB_HISTORY] = false }
      assertTrue(search.saveBookmark(sourceUrl, "Favicon backup fixture"))
      val icon =
        Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.MAGENTA) }
      search.saveFavicon(sourceUrl, icon)
      icon.recycle()
      val output = ByteArrayOutputStream()
      manager.exportBackup(output, false).getOrThrow()
      val exported = JSONObject(output.toString("UTF-8"))
      val encoded = exported.getJSONObject("bookmarkFavicons").getString(sourceHost)
      // Import into an uncached host to simulate a new device, without deleting any existing data.
      assertNull(search.loadFavicon(restoredUrl))
      val backup =
        JSONObject()
          .put("version", BackupManager.BACKUP_VERSION)
          .put(
            "bookmarks",
            JSONArray().put(JSONObject().put("url", restoredUrl).put("title", "Restored favicon")),
          )
          .put("bookmarkFavicons", JSONObject().put(restoredHost, encoded))
          .put("favorites", JSONArray(app.favoritesRepository.getFavoriteIds() + favoriteKey))
      assertEquals(
        1,
        manager.importBackup(backup.toString().byteInputStream()).getOrThrow().bookmarksCount,
      )
      assertEquals(Color.MAGENTA, search.loadFavicon(restoredUrl)!!.getPixel(0, 0))
      // A separate repository proves the icon persisted, not just the live memory cache.
      val diskIcon =
        IconRepository(app).loadFromDisk(faviconCacheKey(restoredHost)) as BitmapDrawable
      assertEquals(Color.MAGENTA, diskIcon.bitmap.getPixel(0, 0))
      val favorites =
        withTimeout(15_000) {
          search.favorites.first { results ->
            results.any { it.id == restoredId && it.icon is BitmapDrawable }
          }
        }
      val favoriteIcon = favorites.first { it.id == restoredId }.icon as BitmapDrawable
      assertEquals(Color.MAGENTA, favoriteIcon.bitmap.getPixel(0, 0))
      // Import also leaves the source cache intact.
      assertEquals(Color.MAGENTA, search.loadFavicon(sourceUrl)!!.getPixel(0, 0))
    } finally {
      app.favoritesRepository.removeKeys(listOf(favoriteKey))
      search.removeBookmark("saved_${sourceUrl.hashCode()}", "web_saved")
      search.removeBookmark(restoredId, "web_saved")
      listOf(sourceHost, restoredHost).forEach { host ->
        File(app.filesDir, "favorite_icons/${faviconCacheKey(host)}.png").delete()
      }
      app.dataStore.edit {
        if (oldHistory == null) it.remove(PreferencesKeys.STORE_WEB_HISTORY)
        else it[PreferencesKeys.STORE_WEB_HISTORY] = oldHistory
      }
    }
  }
}
