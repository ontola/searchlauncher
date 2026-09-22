package com.searchlauncher.app.data

import androidx.test.core.app.ApplicationProvider
import com.searchlauncher.app.SearchLauncherApp
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = SearchLauncherApp::class)
class WebResultSubtitleTest {
  @Test
  fun bookmarksAndHistoryShowThePageAddress() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<SearchLauncherApp>()
    val factory =
      SearchResultFactory(context, IconRepository(context), SearchIconGenerator(context))

    val saved =
      factory.create(
        searchable("web_saved", "https://atomic.place/docs/", "Atomic Place"),
        rankingScore = 0,
        allowDisk = false,
      )
    val history =
      factory.create(
        searchable("web_bookmarks", "http://atomic.place/a/b", "Atomic Place"),
        rankingScore = 0,
        allowDisk = false,
      )
    val unlabeled =
      factory.create(
        searchable("web_saved", null, "Atomic Place"),
        rankingScore = 0,
        allowDisk = false,
      )

    assertEquals("atomic.place/docs", saved.subtitle)
    assertEquals("atomic.place/a/b", history.subtitle)
    assertEquals("Bookmark", unlabeled.subtitle)
  }

  private fun searchable(namespace: String, url: String?, title: String) =
    SearchableDocument(
      doc =
        AppSearchDocument(
          namespace = namespace,
          id = "1",
          score = 0,
          name = title,
          intentUri = url,
        ),
      nameLower = title.lowercase(),
      targetWords = listOf(title.lowercase()),
      acronym = "ap",
      namespaceInt = 0,
      normalizedPhone = null,
      charMask = 0L,
    )
}
