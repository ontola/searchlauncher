package com.searchlauncher.app.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.searchlauncher.app.SearchLauncherApp
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
class FavoritesRepositoryTest {
  private lateinit var context: Context

  @Before
  fun setup() {
    context = ApplicationProvider.getApplicationContext()
    context.getSharedPreferences(Prefs.Favorites.FILE, Context.MODE_PRIVATE).edit().clear().commit()
  }

  @Test
  fun `toggleFavorite stores namespaced keys`() {
    val repo = FavoritesRepository(context)
    val contact =
      SearchResult.Contact(
        id = "lk/1",
        title = "Ada",
        subtitle = null,
        icon = null,
        lookupKey = "lk",
        contactId = 1L,
        photoUri = null,
      )

    repo.toggleFavorite(contact)
    assertEquals(listOf("contacts/lk/1"), repo.getFavoriteIds())
    assertTrue(repo.isFavorite(contact))

    repo.toggleFavorite(contact)
    assertTrue(repo.getFavoriteIds().isEmpty())
    assertFalse(repo.isFavorite(contact))
  }

  @Test
  fun `loads and migrates legacy bare package favorites`() {
    val prefs = context.getSharedPreferences(Prefs.Favorites.FILE, Context.MODE_PRIVATE)
    prefs
      .edit()
      .putString(Prefs.Favorites.IDS_ORDERED, """["com.whatsapp","com.android.settings"]""")
      .commit()

    val repo = FavoritesRepository(context)
    assertEquals(listOf("apps/com.whatsapp", "apps/com.android.settings"), repo.getFavoriteIds())

    // Migration is persisted
    val reloaded = FavoritesRepository(context)
    assertEquals(
      listOf("apps/com.whatsapp", "apps/com.android.settings"),
      reloaded.getFavoriteIds(),
    )
  }

  @Test
  fun `replaceAll normalizes mixed legacy and namespaced keys`() {
    val repo = FavoritesRepository(context)
    repo.replaceAll(listOf("com.example", "snippets/hello", "contacts/lk/2"))
    assertEquals(
      listOf("apps/com.example", "snippets/hello", "contacts/lk/2"),
      repo.getFavoriteIds(),
    )
  }
}
