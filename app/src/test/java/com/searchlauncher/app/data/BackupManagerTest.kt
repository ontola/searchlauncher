package com.searchlauncher.app.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.searchlauncher.app.SearchLauncherApp
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = SearchLauncherApp::class)
class BackupManagerTest {
  private val search = mockk<SearchRepository>()
  private val snippets = mockk<SnippetsRepository>(relaxed = true)
  private val shortcuts = mockk<SearchShortcutRepository>(relaxed = true)
  private val history = mockk<HistoryRepository>(relaxed = true)
  private val wallpapers = mockk<WallpaperRepository>(relaxed = true)
  private val widgets = mockk<WidgetRepository>(relaxed = true)

  private fun manager(): BackupManager {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val favorites = FavoritesRepository(context)
    every { snippets.items } returns MutableStateFlow(emptyList())
    every { shortcuts.items } returns MutableStateFlow(emptyList())
    every { history.historyIds } returns MutableStateFlow(emptyList())
    every { widgets.widgets } returns MutableStateFlow(emptyList())
    return BackupManager(
      context,
      snippets,
      shortcuts,
      favorites,
      history,
      wallpapers,
      widgets,
      search,
    )
  }

  @Test
  fun `bookmarks round trip with exact URLs and titles`() = runBlocking {
    val bookmark =
      SearchRepository.SavedBookmark("https://example.com/?q=a&b=2", "A \"title\" — café")
    coEvery { search.exportBookmarks() } returns listOf(bookmark)
    coEvery { search.saveBookmark(bookmark.url, bookmark.title) } returns true
    val backup = manager()
    val output = ByteArrayOutputStream()
    backup.exportBackup(output, false).getOrThrow()
    val json = JSONObject(output.toString("UTF-8"))
    assertEquals(4, json.getInt("version"))
    assertEquals(bookmark.url, json.getJSONArray("bookmarks").getJSONObject(0).getString("url"))
    val restored = backup.importBackup(output.toByteArray().inputStream()).getOrThrow()
    assertEquals(1, restored.bookmarksCount)
    coVerify(exactly = 1) { search.saveBookmark(bookmark.url, bookmark.title) }
  }

  @Test
  fun `older backups leave bookmarks untouched`() = runBlocking {
    assertEquals(
      0,
      manager().importBackup("""{"version":3}""".byteInputStream()).getOrThrow().bookmarksCount,
    )
    coVerify(exactly = 0) { search.saveBookmark(any(), any()) }
  }

  @Test
  fun `invalid bookmarks are rejected before any bookmark is saved`() = runBlocking {
    val json =
      """{"version":4,"bookmarks":[{"url":"https://example.com","title":"Good"},{"url":"","title":"Bad"}]}"""
    assertTrue(manager().importBackup(json.byteInputStream()).isFailure)
    coVerify(exactly = 0) { search.saveBookmark(any(), any()) }
  }

  @Test
  fun `failed storage is reported as an import failure`() = runBlocking {
    coEvery { search.saveBookmark(any(), any()) } returns false
    val json = """{"version":4,"bookmarks":[{"url":"https://example.com","title":"Example"}]}"""
    assertTrue(manager().importBackup(json.byteInputStream()).isFailure)
  }
}
