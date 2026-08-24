package com.searchlauncher.app.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.searchlauncher.app.SearchLauncherApp
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
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
}
