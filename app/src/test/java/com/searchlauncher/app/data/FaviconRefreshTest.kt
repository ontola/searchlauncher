package com.searchlauncher.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import com.searchlauncher.app.SearchLauncherApp
import com.searchlauncher.app.ui.PreferencesKeys
import com.searchlauncher.app.ui.dataStore
import java.io.File
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = SearchLauncherApp::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FaviconRefreshTest {
  private lateinit var context: Context
  private lateinit var repository: SearchRepository
  private val url = "https://favicon-refresh.test/bookmark"
  private val id = "saved_${url.hashCode()}"
  private val key = faviconCacheKey("favicon-refresh.test")
  private val file
    get() = File(context.filesDir, "favorite_icons/$key.png")

  @Before
  fun setUp() = runBlocking {
    context = ApplicationProvider.getApplicationContext()
    repository = SearchRepository(context)
    file.delete()
    context.dataStore.edit { it[PreferencesKeys.STORE_WEB_HISTORY] = false }
    repository.documentSnapshot =
      listOf(
        repository.wrap(
          AppSearchDocument(
            namespace = "web_saved",
            id = id,
            name = "Saved site",
            score = 1,
            intentUri = url,
          )
        )
      )
  }

  private fun icon(color: Int, size: Int = 32) =
    Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }

  @Test
  fun `visit to another path fills missing favorite icon with history disabled`() = runBlocking {
    repository.saveFavicon("https://favicon-refresh.test/another/path", icon(Color.RED))
    assertEquals(Color.RED, repository.loadFavicon(url)!!.getPixel(0, 0))
    val favorite = repository.getResults(listOf(FavoriteKeys.of("web_saved", id))).single()
    assertEquals(Color.RED, (favorite.icon as BitmapDrawable).bitmap.getPixel(0, 0))
    assertEquals(
      Color.RED,
      (IconRepository(context).loadFromDisk(key) as BitmapDrawable).bitmap.getPixel(0, 0),
    )
  }

  @Test
  fun `different pixels replace even a newly cached favicon`() = runBlocking {
    repository.saveFavicon(url, icon(Color.RED))
    repository.saveFavicon(url, icon(Color.BLUE))
    assertEquals(Color.BLUE, repository.loadFavicon(url)!!.getPixel(0, 0))
    assertEquals(
      Color.BLUE,
      (IconRepository(context).loadFromDisk(key) as BitmapDrawable).bitmap.getPixel(0, 0),
    )
  }

  @Test
  fun `same pixels at a different source size avoid disk rewrite and preserve caller bitmap`() =
    runBlocking {
      repository.saveFavicon(url, icon(Color.RED))
      val cached = repository.loadFavicon(url)
      assertTrue(file.setLastModified(1_000_000))
      val source = icon(Color.RED, 64)
      repository.saveFavicon(url, source)
      assertEquals(1_000_000, file.lastModified())
      assertSame(cached, repository.loadFavicon(url))
      assertFalse(source.isRecycled)
    }

  @Test
  fun `corrupt icon file is replaced on next visit`() = runBlocking {
    file.parentFile!!.mkdirs()
    file.writeText("broken image")
    assertNull(IconRepository(context).loadFromDisk(key))
    repository.saveFavicon(url, icon(Color.GREEN))
    assertEquals(
      Color.GREEN,
      (IconRepository(context).loadFromDisk(key) as BitmapDrawable).bitmap.getPixel(0, 0),
    )
  }

  @Test
  fun `cold start favorites and recents use the updated host icon over stale thumbnails`() =
    runBlocking {
      IconRepository(context).saveToDisk(id, BitmapDrawable(context.resources, icon(Color.RED)))
      val metadata =
        JSONArray()
          .put(
            JSONObject()
              .put("id", id)
              .put("namespace", "web_saved")
              .put("title", "Saved site")
              .put("type", "Content")
              .put("deepLink", url)
          )
      File(context.filesDir, "favorites_metadata.json").writeText(metadata.toString())
      File(context.filesDir, "history_cache.json").writeText(metadata.toString())
      repository.saveFavicon(url, icon(Color.BLUE))
      for (favorites in listOf(true, false)) {
        val cachedResult = repository.loadResultsFromCache(favorites).single()
        assertEquals(Color.BLUE, (cachedResult.icon as BitmapDrawable).bitmap.getPixel(0, 0))
      }
    }

  @Test
  fun `unbookmarked hosts still respect history opt out`() = runBlocking {
    repository.saveFavicon("https://unbookmarked.test/", icon(Color.RED))
    assertNull(repository.loadFavicon("https://unbookmarked.test/"))
  }
}
