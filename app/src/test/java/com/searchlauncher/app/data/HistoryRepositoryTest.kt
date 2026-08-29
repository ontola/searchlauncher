package com.searchlauncher.app.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.searchlauncher.app.SearchLauncherApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = SearchLauncherApp::class)
class HistoryRepositoryTest {
  private lateinit var context: Context

  @Before
  fun setup() {
    context = ApplicationProvider.getApplicationContext()
    context.getSharedPreferences(Prefs.History.FILE, Context.MODE_PRIVATE).edit().clear().commit()
  }

  @Test
  fun `addHistoryItem moves the id to the front and stamps the time`() {
    val repo = HistoryRepository(context)
    repo.addHistoryItem("a", atMs = 1000L)
    repo.addHistoryItem("b", atMs = 2000L)
    repo.addHistoryItem("a", atMs = 3000L)

    assertEquals(listOf("a", "b"), repo.historyIds.value)
    assertEquals(3000L, repo.timesById()["a"])
    assertEquals(2000L, repo.timesById()["b"])
  }

  @Test
  fun `legacy ids without times keep their order`() {
    context
      .getSharedPreferences(Prefs.History.FILE, Context.MODE_PRIVATE)
      .edit()
      .putString(Prefs.History.IDS, """["one","two","three"]""")
      .commit()

    val repo = HistoryRepository(context)
    assertEquals(listOf("one", "two", "three"), repo.historyIds.value)
    val times = repo.historyEntries.value.map { it.lastUsedMs }
    assertTrue(times[0] > times[1])
    assertTrue(times[1] > times[2])
  }
}
