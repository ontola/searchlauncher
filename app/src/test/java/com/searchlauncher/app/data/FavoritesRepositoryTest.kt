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

  @Test
  fun `search option favorites default to google youtube spotify`() {
    val repo = FavoritesRepository(context)
    assertEquals(SearchOptions.DEFAULT_FAVORITE_IDS, repo.getSearchOptionIds())
  }

  @Test
  fun `toggleSearchOption persists and reloads`() {
    val repo = FavoritesRepository(context)
    val youtube =
      SearchResult.SearchIntent(
        id = "search_youtube",
        namespace = "search_shortcuts",
        title = "YouTube Search",
        subtitle = null,
        icon = null,
        trigger = "y",
      )

    assertTrue(repo.isSearchOptionFavorite(youtube))
    repo.toggleSearchOption(youtube)
    assertEquals(listOf("google", "spotify"), repo.getSearchOptionIds())
    assertFalse(repo.isSearchOptionFavorite(youtube))

    val reloaded = FavoritesRepository(context)
    assertEquals(listOf("google", "spotify"), reloaded.getSearchOptionIds())
  }

  @Test
  fun `updateSearchOptionOrder normalizes keys`() {
    val repo = FavoritesRepository(context)
    repo.updateSearchOptionOrder(listOf("search_shortcuts/spotify", "search_youtube", "google"))
    assertEquals(listOf("spotify", "youtube", "google"), repo.getSearchOptionIds())
  }

  @Test
  fun `togglePinnedWebFavorite removes every pin on that site`() {
    val repo = FavoritesRepository(context)
    val home =
      SearchResult.Content(
        id = "saved_1",
        namespace = "web_saved",
        title = "GitHub",
        subtitle = "Bookmark",
        icon = null,
        packageName = "",
        deepLink = "https://github.com",
      )
    val pr =
      SearchResult.Content(
        id = "saved_2",
        namespace = "web_saved",
        title = "PR",
        subtitle = "Bookmark",
        icon = null,
        packageName = "",
        deepLink = "https://github.com/org/repo/pull/1",
      )
    repo.toggleFavorite(home)
    repo.toggleFavorite(pr)
    togglePinnedWebFavorite(
      pr,
      favorites = listOf(home, pr),
      repository = repo,
      treatFavoritedSitesAsApps = true,
    )
    assertTrue(repo.getFavoriteIds().isEmpty())
  }

  @Test
  fun `addFavorite is idempotent and removeKeys drops several`() {
    val repo = FavoritesRepository(context)
    repo.addFavorite("web_saved", "saved_1")
    repo.addFavorite("web_saved", "saved_1")
    repo.addFavorite("web_saved", "saved_2")
    repo.addFavorite("apps", "com.example")
    assertEquals(
      listOf("web_saved/saved_1", "web_saved/saved_2", "apps/com.example"),
      repo.getFavoriteIds(),
    )

    repo.removeKeys(listOf("web_saved/saved_1", "web_saved/saved_2"))
    assertEquals(listOf("apps/com.example"), repo.getFavoriteIds())
  }

  @Test
  fun `clear restores search option defaults`() {
    val repo = FavoritesRepository(context)
    repo.replaceSearchOptions(listOf("wikipedia"))
    repo.clear()
    assertEquals(SearchOptions.DEFAULT_FAVORITE_IDS, repo.getSearchOptionIds())
  }
}
