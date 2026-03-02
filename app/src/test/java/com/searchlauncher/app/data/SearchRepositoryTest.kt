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
    synchronized(repository.documentCache) {
      repository.documentCache.clear()
      repository.documentCache.add(repository.wrap(doc1))
      repository.documentCache.add(repository.wrap(doc2))
    }

    // Save to fast cache
    repository.saveFastIndexCache()

    // Verify file was created
    assertTrue("Cache file should exist after saving", cacheFile.exists())

    // Clear active cache to simulate a fresh app start
    synchronized(repository.documentCache) { repository.documentCache.clear() }
    assertTrue("Cache should be empty before loading", repository.documentCache.isEmpty())

    // Load from fast cache
    val loadResult = repository.loadFastIndexCache()

    // Verify load succeeded and populated documentCache
    assertTrue("loadFastIndexCache should return true", loadResult)

    val loadedDocs = synchronized(repository.documentCache) { repository.documentCache.toList() }
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
    assertTrue("documentCache should be empty", repository.documentCache.isEmpty())
  }
}
