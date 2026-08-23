package com.searchlauncher.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FavoriteKeysTest {
  @Test
  fun `of joins namespace and id`() {
    assertEquals("apps/com.example", FavoriteKeys.of("apps", "com.example"))
    assertEquals(
      "shortcuts/com.example/shortcut1",
      FavoriteKeys.of("shortcuts", "com.example/shortcut1"),
    )
  }

  @Test
  fun `parse splits on first slash`() {
    assertEquals("apps" to "com.example", FavoriteKeys.parse("apps/com.example"))
    assertEquals("contacts" to "lookup/42", FavoriteKeys.parse("contacts/lookup/42"))
    assertNull(FavoriteKeys.parse("noslash"))
    assertNull(FavoriteKeys.parse("/leading"))
    assertNull(FavoriteKeys.parse("trailing/"))
  }

  @Test
  fun `normalize migrates bare package names to apps`() {
    assertEquals("apps/com.whatsapp", FavoriteKeys.normalize("com.whatsapp"))
    assertEquals("apps/com.whatsapp", FavoriteKeys.normalize("apps/com.whatsapp"))
    assertEquals("snippets/hello", FavoriteKeys.normalize("snippets/hello"))
  }

  @Test
  fun `isFavoritable covers durable result types`() {
    assertTrue(
      SearchResult.App(
          id = "com.example",
          title = "Example",
          subtitle = null,
          icon = null,
          packageName = "com.example",
        )
        .isFavoritable()
    )
    assertTrue(
      SearchResult.Contact(
          id = "lk/1",
          title = "Ada",
          subtitle = null,
          icon = null,
          lookupKey = "lk",
          contactId = 1L,
          photoUri = null,
        )
        .isFavoritable()
    )
    assertTrue(
      SearchResult.Shortcut(
          id = "com.example/s1",
          title = "Shortcut",
          subtitle = null,
          icon = null,
          packageName = "com.example",
          intentUri = "shortcut://com.example/s1",
        )
        .isFavoritable()
    )
    assertTrue(
      SearchResult.Snippet(
          id = "hi",
          title = "Hi",
          subtitle = null,
          icon = null,
          alias = "hi",
          content = "hello",
        )
        .isFavoritable()
    )
    assertTrue(
      SearchResult.SearchIntent(
          id = "search_google",
          namespace = "search_shortcuts",
          title = "Google",
          subtitle = null,
          icon = null,
          trigger = "g",
        )
        .isFavoritable()
    )
    assertTrue(
      SearchResult.Content(
          id = "saved_1",
          namespace = "web_saved",
          title = "Saved",
          subtitle = null,
          icon = null,
          packageName = "",
          deepLink = "https://example.com",
        )
        .isFavoritable()
    )
    assertTrue(
      SearchResult.Content(
          id = "42",
          namespace = "downloads",
          title = "ticket.pdf",
          subtitle = null,
          icon = null,
          packageName = "application/pdf",
          deepLink = "content://media/external/downloads/42",
        )
        .isFavoritable()
    )
    assertTrue(
      SearchResult.Content(
          id = "9/1",
          namespace = "calendar",
          title = "Dentist",
          subtitle = null,
          icon = null,
          packageName = "calendar",
          deepLink = "content://com.android.calendar/events/9",
        )
        .isFavoritable()
    )
    assertFalse(
      SearchResult.Content(
          id = "calc",
          namespace = "calculator",
          title = "1+1",
          subtitle = null,
          icon = null,
          packageName = "",
          deepLink = "calculator://copy?text=2",
        )
        .isFavoritable()
    )
    assertFalse(SearchResult.IndexingIndicator().isFavoritable())
    assertFalse(
      SearchResult.App(
          id = "com.example#private",
          title = "Example",
          subtitle = "Private",
          icon = null,
          packageName = "com.example",
          isPrivate = true,
        )
        .isFavoritable()
    )
    assertFalse(
      SearchResult.PrivateSpace(
          title = "Private Space",
          subtitle = "Locked · tap to unlock",
          icon = null,
          unlocked = false,
        )
        .isFavoritable()
    )
  }
}
