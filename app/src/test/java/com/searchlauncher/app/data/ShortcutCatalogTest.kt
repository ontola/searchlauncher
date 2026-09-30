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
  fun `the shortcuts everyone starts with have keys of their own`() {
    val aliases = DefaultShortcuts.searchShortcuts.map { it.alias.lowercase() }
    assertEquals(
      aliases.filter { alias -> aliases.count { it == alias } > 1 }.distinct(),
      emptyList<String>(),
    )
  }

  @Test
  fun `an app shortcut always has a key no other shortcut wants`() {
    // Earlier keys may be shared, since the first free one wins, but the last resort must not be,
    // or the shortcut could be left without a key on some phone.
    val all = entries.flatMap { entry -> entry.aliases.map { it.lowercase() } }
    DefaultShortcuts.installableShortcuts.forEach { entry ->
      assertTrue(
        "${entry.shortcut.id} needs a key only it uses",
        entry.aliases.any { alias -> all.count { it == alias.lowercase() } == 1 },
      )
    }
  }

  @Test
  fun `every key is a single word`() {
    entries.forEach { entry ->
      entry.aliases.forEach { alias ->
        assertTrue(
          "'$alias' of ${entry.shortcut.id}",
          alias.isNotBlank() && alias.none { it.isWhitespace() },
        )
      }
    }
  }

  @Test
  fun `no key is a word that starts ordinary queries`() {
    // "a cheap flight" should stay a search, not become one inside Amazon.
    val words =
      setOf("a", "i", "o", "u", "an", "at", "in", "is", "it", "me", "my", "of", "on", "to")
    entries.forEach { entry ->
      entry.aliases.forEach { alias ->
        assertTrue("'$alias' of ${entry.shortcut.id}", alias.lowercase() !in words)
      }
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
