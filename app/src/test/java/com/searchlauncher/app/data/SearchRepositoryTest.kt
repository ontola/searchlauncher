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
  fun `snippet search matches both alias and content`() = runBlocking {
    val snippet =
      AppSearchDocument(
        namespace = "snippets",
        id = "shipping",
        score = 5,
        name = "shipping",
        description = "Warehouse pickup window closes at 17:30",
        intentUri = "snippet://shipping",
        isAction = true,
      )

    repository.documentSnapshot = listOf(repository.wrap(snippet))

    val aliasResults =
      repository.searchApps("ship", limit = 5, includeSuggestions = false).getOrThrow()
    assertEquals(snippet.id, aliasResults.filterIsInstance<SearchResult.Snippet>().first().id)

    val contentResults =
      repository.searchApps("warehouse", limit = 5, includeSuggestions = false).getOrThrow()
    val contentMatch = contentResults.filterIsInstance<SearchResult.Snippet>().first()
    assertEquals(snippet.id, contentMatch.id)
    assertEquals("Warehouse pickup window closes at 17:30", contentMatch.content)
  }

  @Test
  fun `search shortcuts are hidden when optional web shortcuts are disabled`() = runBlocking {
    val shortcut =
      AppSearchDocument(
        namespace = "search_shortcuts",
        id = "google",
        score = 5,
        name = "Google Search",
        description = "g",
        intentUri = "https://www.google.com/search?q=%s",
        isAction = true,
      )

    repository.documentSnapshot = listOf(repository.wrap(shortcut))

    val enabledResults =
      repository
        .searchApps("google", limit = 5, includeSuggestions = false, includeSearchShortcuts = true)
        .getOrThrow()
    assertTrue(enabledResults.any { it.id == shortcut.id })

    val disabledResults =
      repository
        .searchApps("google", limit = 5, includeSuggestions = false, includeSearchShortcuts = false)
        .getOrThrow()
    assertFalse(disabledResults.any { it.id == shortcut.id })
  }

  @Test
  fun `fallback search shortcuts can return all defaults`() = runBlocking {
    repository.documentSnapshot =
      DefaultShortcuts.searchShortcuts
        .map { shortcut ->
          repository.wrap(
            AppSearchDocument(
              namespace = "search_shortcuts",
              id = "search_${shortcut.id}",
              score = 3,
              name = shortcut.description,
              description = shortcut.alias,
              intentUri = shortcut.urlTemplate,
              isAction = false,
            )
          )
        }
        .sortedBy { it.namespaceInt }

    val results = repository.getSearchShortcuts(limit = 100)

    assertEquals(DefaultShortcuts.searchShortcuts.size, results.size)
  }

  @Test
  fun `fallback search shortcuts prefer google and play store by default`() = runBlocking {
    repository.documentSnapshot =
      DefaultShortcuts.searchShortcuts
        .map { shortcut ->
          repository.wrap(
            AppSearchDocument(
              namespace = "search_shortcuts",
              id = "search_${shortcut.id}",
              score = 3,
              name = shortcut.description,
              description = shortcut.alias,
              intentUri = shortcut.urlTemplate,
              isAction = false,
            )
          )
        }
        .sortedBy { it.namespaceInt }

    val results = repository.getSearchShortcuts(limit = 100)

    assertEquals("search_google", results[0].id)
    assertEquals("search_playstore", results[1].id)
  }

  @Test
  fun `timer smart action outranks learned shortcut matches`() = runBlocking {
    val napShortcut =
      AppSearchDocument(
        namespace = "app_shortcuts",
        id = "nap_15m",
        score = 3,
        name = "nap 15m",
        intentUri = "intent://nap",
        description = "Timer preset",
        isAction = true,
      )

    repository.documentSnapshot = listOf(repository.wrap(napShortcut))

    repeat(5) { repository.reportUsage("app_shortcuts", napShortcut.id, "5m") }

    val results = repository.searchApps("5m", limit = 5, includeSuggestions = false).getOrThrow()

    assertEquals("smart_action_timer_300", results.first().id)
  }

  @Test
  fun `global usage does not outrank a strong word-prefix title match`() = runBlocking {
    val strongMatch =
      AppSearchDocument(
        namespace = "apps",
        id = "com.example.atomiccanvas",
        score = 1,
        name = "Atomic Canvas",
        description = "Creative",
      )
    val weakMatch =
      AppSearchDocument(
        namespace = "apps",
        id = "com.example.cobaltnavigation",
        score = 1,
        name = "Cobalt Navigation",
        description = "Utility",
      )

    repository.documentSnapshot =
      listOf(repository.wrap(weakMatch), repository.wrap(strongMatch)).sortedBy { it.namespaceInt }

    repeat(40) {
      repository.reportUsage(
        weakMatch.namespace,
        weakMatch.id,
        query = null,
        wasFirstResult = false,
      )
    }

    val results = repository.searchApps("Canv", limit = 5, includeSuggestions = false).getOrThrow()

    assertEquals(
      "A strong title match should beat an often-opened but weaker title match",
      strongMatch.id,
      results.first().id,
    )
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
      "A strong 3-character contact prefix should win before query-specific learning",
      contact.id,
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
  fun `latest selected app wins for the same exact query`() = runBlocking {
    val reddit =
      AppSearchDocument(
        namespace = "apps",
        id = "com.reddit.frontpage",
        score = 1,
        name = "Reddit",
        description = "Social",
      )
    val reolink =
      AppSearchDocument(
        namespace = "apps",
        id = "com.mcu.reolink",
        score = 1,
        name = "Reolink",
        description = "Camera",
      )

    repository.documentSnapshot =
      listOf(repository.wrap(reddit), repository.wrap(reolink)).sortedBy { it.namespaceInt }

    repeat(5) {
      repository.reportUsage(reolink.namespace, reolink.id, query = "re", wasFirstResult = false)
    }

    val beforeRedditTap =
      repository.searchApps("re", limit = 5, includeSuggestions = false).getOrThrow()
    assertEquals(reolink.id, beforeRedditTap.first().id)

    repository.reportUsage(reddit.namespace, reddit.id, query = "re", wasFirstResult = false)

    val afterRedditTap =
      repository.searchApps("re", limit = 5, includeSuggestions = false).getOrThrow()
    assertEquals(
      "Selecting Reddit for the exact same query should make Reddit the top learned result",
      reddit.id,
      afterRedditTap.first().id,
    )
  }

  @Test
  fun `three character contact prefix appears above weak app matches`() = runBlocking {
    val contact =
      AppSearchDocument(
        namespace = "contacts",
        id = "alice_lookup/1",
        score = 1,
        name = "Alice Smith",
        description = "|+15550123",
      )
    val weakAppMatches =
      (0 until 20).map { index ->
        AppSearchDocument(
          namespace = "apps",
          id = "com.example.metallic$index",
          score = 1,
          name = "Metallic $index",
          description = "Application",
        )
      }

    repository.documentSnapshot =
      (weakAppMatches + contact).map { repository.wrap(it) }.sortedBy { it.namespaceInt }

    val results = repository.searchApps("ali", limit = 16, includeSuggestions = false).getOrThrow()

    assertTrue(
      "A 3-character contact prefix should not be hidden below weak app contains matches",
      results.any { it.id == contact.id },
    )
    assertTrue(
      "A 3-character contact prefix should rank above weak app contains matches",
      results.indexOfFirst { it.id == contact.id } <
        results.indexOfFirst { it.id == weakAppMatches.first().id },
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
    assertEquals(
      "A strong 3-character contact prefix should win before query-specific learning",
      contact.id,
      beforePol.first().id,
    )

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
      "A selected contact should get a learned boost for a two-character prefix",
      contactScoreAfterPo > contactScoreBeforePo,
    )
    assertEquals(
      "A selected contact should be visible at the top for a learned two-character prefix",
      contact.id,
      afterPo.first().id,
    )

    val afterP = repository.searchApps("p", limit = 5, includeSuggestions = false).getOrThrow()
    assertEquals(
      "A selected contact should be visible at the top for a learned one-character prefix",
      contact.id,
      afterP.first().id,
    )
  }

  @Test
  fun `learned one character contact is not skipped after many app matches`() = runBlocking {
    val contact =
      AppSearchDocument(
        namespace = "contacts",
        id = "anja_lookup/1",
        score = 1,
        name = "Anja Person",
        description = "|+15550123",
      )
    val appMatches =
      (0 until 1100).map { index ->
        AppSearchDocument(
          namespace = "apps",
          id = "com.example.alpha$index",
          score = 1,
          name = "Alpha App $index",
          description = "Application",
        )
      }

    repository.documentSnapshot =
      (appMatches + contact).map { repository.wrap(it) }.sortedBy { it.namespaceInt }

    repository.reportUsage(contact.namespace, contact.id, query = "anja", wasFirstResult = false)

    val results = repository.searchApps("a", limit = 5, includeSuggestions = false).getOrThrow()
    assertEquals(
      "A learned contact should still be considered after a one-character app-heavy candidate list",
      contact.id,
      results.first().id,
    )
  }
}
