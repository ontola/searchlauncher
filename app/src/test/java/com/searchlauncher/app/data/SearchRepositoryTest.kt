package com.searchlauncher.app.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.searchlauncher.app.SearchLauncherApp
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = SearchLauncherApp::class)
class SearchRepositoryTest {

  private lateinit var context: Context
  private lateinit var repository: SearchRepository
  private lateinit var cacheFile: File

  @Before
  fun setup() {
    context = ApplicationProvider.getApplicationContext()
    repository = SearchRepository(context)
    cacheFile = File(context.cacheDir, "fast_index_cache.json")
    if (cacheFile.exists()) {
      cacheFile.delete()
    }
    File(context.filesDir, "usage_stats.json").delete()
    File(context.filesDir, "query_usage_stats.json").delete()
  }

  @Test
  fun `test fast index cache saves and loads correctly`() = runBlocking {
    // Create dummy AppSearchDocuments
    val doc1 =
      AppSearchDocument(
        namespace = "apps",
        id = "com.test.app1",
        score = 2,
        name = "Test App 1",
        intentUri = "intent://test1",
        description = "Productivity",
        isAction = false,
        iconResId = 0L,
      )

    val doc2 =
      AppSearchDocument(
        namespace = "shortcuts",
        id = "com.test.app2/shortcut1",
        score = 1,
        name = "Test Shortcut",
        intentUri = "shortcut://test2",
        description = "Some Shortcut",
        isAction = true,
        iconResId = 123L,
      )

    // Inject them into the repository's active cache using the internal wrap method
    repository.documentSnapshot =
      listOf(repository.wrap(doc1), repository.wrap(doc2)).sortedBy { it.namespaceInt }

    // Save to fast cache
    repository.saveFastIndexCache()

    // Verify file was created
    assertTrue("Cache file should exist after saving", cacheFile.exists())

    // Clear active cache to simulate a fresh app start
    repository.documentSnapshot = emptyList()
    assertTrue("Cache should be empty before loading", repository.documentSnapshot.isEmpty())

    // Load from fast cache
    val loadResult = repository.loadFastIndexCache()

    // Verify load succeeded and populated documentCache
    assertTrue("loadFastIndexCache should return true", loadResult)

    val loadedDocs = repository.documentSnapshot.toList()
    assertEquals(2, loadedDocs.size)

    // Verify doc1 properties
    val loadedDoc1 = loadedDocs.find { it.doc.id == doc1.id }?.doc
    requireNotNull(loadedDoc1) { "Doc1 was not loaded" }
    assertEquals("apps", loadedDoc1.namespace)
    assertEquals(2, loadedDoc1.score)
    assertEquals("Test App 1", loadedDoc1.name)
    assertEquals("intent://test1", loadedDoc1.intentUri)
    assertEquals("Productivity", loadedDoc1.description)
    assertFalse(loadedDoc1.isAction)
    assertEquals(0L, loadedDoc1.iconResId)

    // Verify doc2 properties
    val loadedDoc2 = loadedDocs.find { it.doc.id == doc2.id }?.doc
    requireNotNull(loadedDoc2) { "Doc2 was not loaded" }
    assertEquals("shortcuts", loadedDoc2.namespace)
    assertEquals(1, loadedDoc2.score)
    assertEquals("Test Shortcut", loadedDoc2.name)
    assertEquals("shortcut://test2", loadedDoc2.intentUri)
    assertEquals("Some Shortcut", loadedDoc2.description)
    assertTrue(loadedDoc2.isAction)
    assertEquals(123L, loadedDoc2.iconResId)
  }

  @Test
  fun `loadFastIndexCache returns false when no cache exists`() = runBlocking {
    // Ensure file is deleted
    if (cacheFile.exists()) cacheFile.delete()

    val loadResult = repository.loadFastIndexCache()
    assertFalse("loadFastIndexCache should return false if file does not exist", loadResult)
    assertTrue("documentSnapshot should be empty", repository.documentSnapshot.isEmpty())
  }

  @Test
  fun `query usage boost moves tapped contact above app for same prefix`() = runBlocking {
    val contact =
      AppSearchDocument(
        namespace = "contacts",
        id = "alice_lookup/1",
        score = 1,
        name = "Alice Smith",
        description = "|+15550123",
      )
    val app =
      AppSearchDocument(
        namespace = "apps",
        id = "com.example.aliexpress",
        score = 1,
        name = "AliExpress",
        description = "Shopping",
      )

    repository.documentSnapshot =
      listOf(repository.wrap(app), repository.wrap(contact)).sortedBy { it.namespaceInt }

    val beforeTap = repository.searchApps("ali", limit = 5, includeSuggestions = false).getOrThrow()
    assertEquals(
      "App should win before the query-specific tap is learned",
      app.id,
      beforeTap.first().id,
    )

    repository.reportUsage(contact.namespace, contact.id, query = "ali", wasFirstResult = false)

    val afterTap = repository.searchApps("ali", limit = 5, includeSuggestions = false).getOrThrow()
    assertEquals(
      "Tapped contact should win for the same query prefix",
      contact.id,
      afterTap.first().id,
    )
  }

  @Test
  fun `query usage boost also applies to shorter prefixes`() = runBlocking {
    val contact =
      AppSearchDocument(
        namespace = "contacts",
        id = "polle_lookup/1",
        score = 1,
        name = "Polle Person",
        description = "|+15550123",
      )
    val app =
      AppSearchDocument(
        namespace = "apps",
        id = "com.example.polaris",
        score = 1,
        name = "Polaris",
        description = "Tools",
      )

    repository.documentSnapshot =
      listOf(repository.wrap(app), repository.wrap(contact)).sortedBy { it.namespaceInt }

    val beforePol = repository.searchApps("pol", limit = 5, includeSuggestions = false).getOrThrow()
    assertEquals("App should win before the longer query is learned", app.id, beforePol.first().id)

    val beforePo = repository.searchApps("po", limit = 5, includeSuggestions = false).getOrThrow()
    val contactScoreBeforePo = beforePo.first { it.id == contact.id }.rankingScore

    repository.reportUsage(contact.namespace, contact.id, query = "polle", wasFirstResult = false)

    val afterPol = repository.searchApps("pol", limit = 5, includeSuggestions = false).getOrThrow()
    assertEquals(
      "Tapped contact should win for a shorter, strong prefix",
      contact.id,
      afterPol.first().id,
    )

    val afterPo = repository.searchApps("po", limit = 5, includeSuggestions = false).getOrThrow()
    val contactScoreAfterPo = afterPo.first { it.id == contact.id }.rankingScore
    assertTrue(
      "Very short prefixes should get a smaller boost, even if they do not become top result",
      contactScoreAfterPo > contactScoreBeforePo,
    )
  }
}
