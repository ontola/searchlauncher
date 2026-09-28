package com.searchlauncher.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Checks `search_shortcuts.json`, which people edit by hand, for the mistakes that would only show
 * up on someone's phone.
 */
class ShortcutCatalogTest {
  private val entries = ShortcutCatalog.entries

  @Test
  fun `every id is used once`() {
    val ids = entries.map { it.shortcut.id }
    assertEquals(ids.filter { id -> ids.count { it == id } > 1 }.distinct(), emptyList<String>())
  }

  @Test
  fun `every alias is used once`() {
    val aliases = entries.map { it.shortcut.alias.lowercase() }
    assertEquals(
      aliases.filter { alias -> aliases.count { it == alias } > 1 }.distinct(),
      emptyList<String>(),
    )
  }

  @Test
  fun `every alias is a single word`() {
    entries.forEach { entry ->
      val alias = entry.shortcut.alias
      assertTrue(
        "'$alias' of ${entry.shortcut.id}",
        alias.isNotBlank() && alias.none { it.isWhitespace() },
      )
    }
  }

  @Test
  fun `every template has a place for the query`() {
    entries.forEach { entry -> assertTrue(entry.shortcut.id, "%s" in entry.shortcut.urlTemplate) }
  }

  @Test
  fun `every entry has a name, label and colour`() {
    entries.forEach { entry ->
      assertTrue(entry.shortcut.id, entry.shortcut.description.isNotBlank())
      assertTrue(entry.shortcut.id, !entry.shortcut.shortLabel.isNullOrBlank())
      assertTrue(entry.shortcut.id, entry.shortcut.color != null)
    }
  }

  @Test
  fun `a shortcut that waits for its app names that app`() {
    DefaultShortcuts.installableShortcuts.forEach { entry ->
      assertTrue(entry.shortcut.id, entry.packages.isNotEmpty())
      entry.packages.forEach { packageName ->
        assertTrue(
          "$packageName of ${entry.shortcut.id}",
          ShortcutLaunch.isPreferredAppPackage(packageName),
        )
      }
    }
  }

  @Test
  fun `the shortcuts every install starts with are unchanged`() {
    assertEquals(
      listOf(
        "google",
        "gemini",
        "duckduckgo",
        "bing",
        "calendar",
        "youtube",
        "navigate",
        "maps",
        "reddit",
        "wikipedia",
        "chatgpt_ask",
        "perplexity",
        "claude",
        "playstore",
        "spotify",
        "linkedin",
        "widget_search",
      ),
      DefaultShortcuts.searchShortcuts.map { it.id },
    )
  }

  @Test
  fun `colours are read as opaque ARGB`() {
    assertEquals(0xFF4285F4, ShortcutCatalog.parseColor("#4285F4"))
    assertEquals(0xFF000000, ShortcutCatalog.parseColor("#000000"))
  }

  @Test
  fun `app icons come from the list`() {
    assertEquals(listOf("com.reddit.frontpage"), DefaultShortcuts.iconPackages["reddit"])
    assertEquals(
      listOf("com.amazon.mShop.android.shopping"),
      DefaultShortcuts.iconPackages["amazon"],
    )
  }
}
