package com.searchlauncher.app.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.searchlauncher.app.SearchLauncherApp
import org.json.JSONArray
import org.json.JSONObject
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
class SearchShortcutRepositoryTest {
  private lateinit var context: Context

  private val claudeDeepLink = "claude://claude.ai/new?q=%s"
  private val claudeWebsite = "https://claude.ai/new?q=%s"

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    context
      .getSharedPreferences(Prefs.SearchShortcuts.FILE, Context.MODE_PRIVATE)
      .edit()
      .clear()
      .commit()
  }

  /** Writes [shortcuts] straight to prefs, standing in for what a previous version persisted. */
  private fun persist(vararg shortcuts: SearchShortcut) {
    val array = JSONArray()
    shortcuts.forEach { shortcut ->
      array.put(
        JSONObject().apply {
          put("id", shortcut.id)
          put("alias", shortcut.alias)
          put("urlTemplate", shortcut.urlTemplate)
          put("description", shortcut.description)
        }
      )
    }
    context
      .getSharedPreferences(Prefs.SearchShortcuts.FILE, Context.MODE_PRIVATE)
      .edit()
      .putString(Prefs.SearchShortcuts.SHORTCUTS, array.toString())
      .commit()
  }

  private fun templateFor(id: String, repository: SearchShortcutRepository) =
    repository.items.value.first { it.id == id }.urlTemplate

  @Test
  fun `claude default is the app deep link`() {
    val claude = DefaultShortcuts.searchShortcuts.first { it.id == "claude" }

    assertEquals(claudeDeepLink, claude.urlTemplate)
  }

  @Test
  fun `a persisted claude on the old website template is moved to the deep link`() {
    persist(SearchShortcut("claude", "cl", claudeWebsite, "Ask Claude"))

    assertEquals(claudeDeepLink, templateFor("claude", SearchShortcutRepository(context)))
  }

  @Test
  fun `the migration survives a restart`() {
    persist(SearchShortcut("claude", "cl", claudeWebsite, "Ask Claude"))
    SearchShortcutRepository(context)

    // A second instance reads what the first one wrote, so the rewrite has to have been saved.
    assertEquals(claudeDeepLink, templateFor("claude", SearchShortcutRepository(context)))
  }

  @Test
  fun `a claude the user pointed somewhere else is left alone`() {
    val custom = "https://claude.ai/new?q=%s&model=opus"
    persist(SearchShortcut("claude", "cl", custom, "Ask Claude"))

    assertEquals(custom, templateFor("claude", SearchShortcutRepository(context)))
  }

  @Test
  fun `a user's own alias is kept while the template moves`() {
    persist(SearchShortcut("claude", "ai", claudeWebsite, "Ask Claude"))
    val repository = SearchShortcutRepository(context)

    val claude = repository.items.value.first { it.id == "claude" }
    assertEquals("ai", claude.alias)
    assertEquals(claudeDeepLink, claude.urlTemplate)
  }

  @Test
  fun `missing defaults are still merged in alongside a migration`() {
    persist(SearchShortcut("claude", "cl", claudeWebsite, "Ask Claude"))
    val repository = SearchShortcutRepository(context)

    val ids = repository.items.value.map { it.id }
    assertEquals(DefaultShortcuts.searchShortcuts.map { it.id }.toSet(), ids.toSet())
    assertEquals(ids.size, ids.distinct().size)
  }

  @Test
  fun `a persisted youtube without a package is pointed at the youtube app`() {
    persist(
      SearchShortcut(
        "youtube",
        "y",
        "https://www.youtube.com/results?search_query=%s",
        "YouTube Search",
      )
    )

    val youtube = SearchShortcutRepository(context).items.value.first { it.id == "youtube" }
    assertEquals("com.google.android.youtube", youtube.packageName)
    assertEquals("https://www.youtube.com/results?search_query=%s", youtube.urlTemplate)
  }

  @Test
  fun `a youtube the user pointed at a different url is left without a package`() {
    val custom = "https://www.youtube.com/results?search_query=%s&sp=EgIQAQ%253D%253D"
    persist(SearchShortcut("youtube", "y", custom, "YouTube Search"))

    val youtube = SearchShortcutRepository(context).items.value.first { it.id == "youtube" }
    assertEquals(custom, youtube.urlTemplate)
    assertEquals(null, youtube.packageName)
  }

  @Test
  fun `a user's youtube alias is kept while the package is filled in`() {
    persist(
      SearchShortcut(
        "youtube",
        "yt",
        "https://www.youtube.com/results?search_query=%s",
        "YouTube Search",
      )
    )

    val youtube = SearchShortcutRepository(context).items.value.first { it.id == "youtube" }
    assertEquals("yt", youtube.alias)
    assertEquals("com.google.android.youtube", youtube.packageName)
  }

  @Test
  fun `a fresh list is not a manual order`() {
    assertFalse(SearchShortcutRepository(context).manualOrder.value)
  }

  @Test
  fun `reorder saves the dragged list and survives a restart`() {
    val repository = SearchShortcutRepository(context)
    val reversed = repository.items.value.reversed()

    repository.reorder(reversed)

    assertTrue(repository.manualOrder.value)
    assertEquals(reversed.map { it.id }, repository.items.value.map { it.id })
    val reloaded = SearchShortcutRepository(context)
    assertTrue(reloaded.manualOrder.value)
    assertEquals(reversed.map { it.id }, reloaded.items.value.map { it.id })
  }

  @Test
  fun `reset defaults clears a manual order`() {
    val repository = SearchShortcutRepository(context)
    repository.reorder(repository.items.value.reversed())

    repository.resetToDefaults()

    assertFalse(repository.manualOrder.value)
    assertEquals(
      DefaultShortcuts.searchShortcuts.map { it.id },
      repository.items.value.map { it.id },
    )
  }

  @Test
  fun `editing a shortcut does not drop a manual order`() {
    val repository = SearchShortcutRepository(context)
    val reversed = repository.items.value.reversed()
    repository.reorder(reversed)
    val first = reversed.first()

    kotlinx.coroutines.runBlocking { repository.updateShortcut(first.copy(alias = "zz")) }

    assertTrue(repository.manualOrder.value)
    assertEquals(reversed.map { it.id }, repository.items.value.map { it.id })
    assertEquals("zz", repository.items.value.first().alias)
  }

  private fun installApp(packageName: String) {
    org.robolectric.Shadows.shadowOf(context.packageManager)
      .installPackage(android.content.pm.PackageInfo().apply { this.packageName = packageName })
  }

  private val amazonApp = "com.amazon.mShop.android.shopping"

  @Test
  fun `an app shortcut stays out until its app is installed`() {
    val repository = SearchShortcutRepository(context)
    assertFalse(repository.items.value.any { it.id == "amazon" })

    installApp(amazonApp)
    repository.refreshAvailability()

    val amazon = repository.items.value.last()
    assertEquals("amazon", amazon.id)
    assertTrue(repository.launchable.value.any { it.id == "amazon" })
  }

  @Test
  fun `an app shortcut already installed is there on first start`() {
    installApp(amazonApp)

    assertTrue(SearchShortcutRepository(context).items.value.any { it.id == "amazon" })
  }

  @Test
  fun `a removed app shortcut is not added back`() {
    installApp(amazonApp)
    val repository = SearchShortcutRepository(context)
    kotlinx.coroutines.runBlocking { repository.removeShortcut("amazon") }

    repository.refreshAvailability()
    assertFalse(repository.items.value.any { it.id == "amazon" })
    assertFalse(SearchShortcutRepository(context).items.value.any { it.id == "amazon" })
  }

  @Test
  fun `a removed default stays removed after a restart`() {
    val repository = SearchShortcutRepository(context)
    kotlinx.coroutines.runBlocking { repository.removeShortcut("wikipedia") }

    assertFalse(SearchShortcutRepository(context).items.value.any { it.id == "wikipedia" })
  }

  @Test
  fun `reset defaults brings removed shortcuts back`() {
    installApp(amazonApp)
    val repository = SearchShortcutRepository(context)
    kotlinx.coroutines.runBlocking {
      repository.removeShortcut("wikipedia")
      repository.removeShortcut("amazon")
    }

    repository.resetToDefaults()

    val ids = repository.items.value.map { it.id }
    assertTrue("wikipedia" in ids)
    assertTrue("amazon" in ids)
  }

  @Test
  fun `an app shortcut whose key is taken is left out`() {
    val repository = SearchShortcutRepository(context)
    val google = repository.items.value.first { it.id == "google" }
    kotlinx.coroutines.runBlocking { repository.updateShortcut(google.copy(alias = "am")) }

    installApp(amazonApp)
    repository.refreshAvailability()

    assertFalse(repository.items.value.any { it.id == "amazon" })
  }

  @Test
  fun `an app shortcut is no longer offered once its app is uninstalled`() {
    installApp(amazonApp)
    val repository = SearchShortcutRepository(context)

    org.robolectric.Shadows.shadowOf(context.packageManager).removePackage(amazonApp)
    repository.refreshAvailability()

    assertTrue(repository.items.value.any { it.id == "amazon" })
    assertFalse(repository.launchable.value.any { it.id == "amazon" })
  }
}
