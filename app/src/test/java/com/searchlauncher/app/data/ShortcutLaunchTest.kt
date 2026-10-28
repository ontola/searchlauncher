package com.searchlauncher.app.data

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.graphics.drawable.ColorDrawable
import androidx.test.core.app.ApplicationProvider
import com.searchlauncher.app.SearchLauncherApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = SearchLauncherApp::class)
class ShortcutLaunchTest {
  private lateinit var context: Context

  private val youtubeUrl = "https://www.youtube.com/results?search_query=cats"
  private val youtubePackage = "com.google.android.youtube"

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
  }

  private fun installViewHandler(url: String, packageName: String) {
    val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)).setPackage(packageName)
    shadowOf(context.packageManager)
      .addResolveInfoForIntent(intent, android.content.pm.ResolveInfo())
  }

  private fun installSearchHandler(packageName: String) {
    val intent = Intent(Intent.ACTION_SEARCH).setPackage(packageName)
    shadowOf(context.packageManager)
      .addResolveInfoForIntent(intent, android.content.pm.ResolveInfo())
  }

  @Test
  fun `youtube default keeps the website template so it stays available without the app`() {
    val youtube = DefaultShortcuts.searchShortcuts.first { it.id == "youtube" }

    assertEquals("https://www.youtube.com/results?search_query=%s", youtube.urlTemplate)
    assertEquals(youtubePackage, youtube.packageName)
    assertTrue(ShortcutAvailability.isWebTemplate(youtube.urlTemplate))
    assertTrue(ShortcutAvailability.isAvailable(context.packageManager, youtube))
  }

  @Test
  fun `no preferred intent when the app is not installed`() {
    assertNull(
      ShortcutLaunch.preferredAppIntent(context.packageManager, youtubeUrl, youtubePackage, "cats")
    )
  }

  @Test
  fun `view intent is pinned to the app when it handles the url`() {
    installViewHandler(youtubeUrl, youtubePackage)

    val intent =
      ShortcutLaunch.preferredAppIntent(context.packageManager, youtubeUrl, youtubePackage, "cats")

    assertNotNull(intent)
    assertEquals(Intent.ACTION_VIEW, intent!!.action)
    assertEquals(youtubePackage, intent.`package`)
    assertEquals(youtubeUrl, intent.data.toString())
  }

  @Test
  fun `search intent is used when the app does not claim the url`() {
    installSearchHandler(youtubePackage)

    val intent =
      ShortcutLaunch.preferredAppIntent(context.packageManager, youtubeUrl, youtubePackage, "cats")

    assertNotNull(intent)
    assertEquals(Intent.ACTION_SEARCH, intent!!.action)
    assertEquals(youtubePackage, intent.`package`)
    assertEquals("cats", intent.getStringExtra(SearchManager.QUERY))
  }

  @Test
  fun `search intent falls back to the url query parameter`() {
    installSearchHandler(youtubePackage)

    val intent =
      ShortcutLaunch.preferredAppIntent(context.packageManager, youtubeUrl, youtubePackage)

    assertEquals("cats", intent!!.getStringExtra(SearchManager.QUERY))
  }

  @Test
  fun `placeholder packages do not pin the intent`() {
    installViewHandler(youtubeUrl, "android")

    assertNull(ShortcutLaunch.preferredAppIntent(context.packageManager, youtubeUrl, "android"))
    assertNull(ShortcutLaunch.preferredAppIntent(context.packageManager, youtubeUrl, null))
    assertNull(
      ShortcutLaunch.preferredAppIntent(context.packageManager, youtubeUrl, "application/pdf")
    )
  }

  @Test
  fun `non web urls are left to the generic launch path`() {
    val spotify = "spotify:search:cats"
    assertNull(
      ShortcutLaunch.preferredAppIntent(
        context.packageManager,
        spotify,
        "com.spotify.music",
        "cats",
      )
    )
  }

  @Test
  fun `maps shortcut opens in the maps app it lists`() {
    val maps = DefaultShortcuts.searchShortcuts.first { it.id == "maps" }
    val url = maps.urlForQuery("coffee")
    val mapsPackage = "com.google.android.apps.maps"
    installViewHandler(url, mapsPackage)

    val intent =
      ShortcutLaunch.preferredAppIntent(
        context.packageManager,
        url,
        maps.packageName,
        "coffee",
        otherApps = maps.iconPackages,
      )

    assertEquals(Intent.ACTION_VIEW, intent!!.action)
    assertEquals(mapsPackage, intent.`package`)
    assertEquals(url, intent.data.toString())
  }

  @Test
  fun `listed apps are not sent a bare search when they do not claim the url`() {
    val other = "com.example.maps"
    installSearchHandler(other)

    assertNull(
      ShortcutLaunch.preferredAppIntent(
        context.packageManager,
        youtubeUrl,
        null,
        "cats",
        otherApps = listOf(other),
      )
    )
  }

  @Test
  fun `listed apps are tried when the named app is missing`() {
    val other = "com.example.tube"
    installViewHandler(youtubeUrl, other)

    val intent =
      ShortcutLaunch.preferredAppIntent(
        context.packageManager,
        youtubeUrl,
        youtubePackage,
        "cats",
        otherApps = listOf(youtubePackage, other),
      )

    assertEquals(other, intent!!.`package`)
  }

  @Test
  fun `default app intent excludes browsers`() {
    val intent = ShortcutLaunch.defaultAppIntent(youtubeUrl)!!

    assertTrue(intent.flags and Intent.FLAG_ACTIVITY_REQUIRE_NON_BROWSER != 0)
    assertTrue(intent.flags and Intent.FLAG_ACTIVITY_REQUIRE_DEFAULT != 0)
    assertNull(ShortcutLaunch.defaultAppIntent("geo:0,0?q=cats"))
  }

  @Test
  fun `content from a search shortcut prefers the app`() {
    installViewHandler(youtubeUrl, youtubePackage)
    val result =
      SearchResult.Content(
        id = "shortcut_y",
        namespace = SearchOptions.NAMESPACE,
        title = "YouTube Search: cats",
        subtitle = "Search Shortcut",
        icon = ColorDrawable(0),
        packageName = youtubePackage,
        deepLink = youtubeUrl,
      )

    val intent = ShortcutLaunch.preferredAppIntentForContent(context.packageManager, result, "cats")

    assertEquals(youtubePackage, intent!!.`package`)
    assertEquals(Intent.ACTION_VIEW, intent.action)
  }

  private fun imdbResult(id: String, url: String) =
    SearchResult.Content(
      id = id,
      namespace = SearchOptions.NAMESPACE,
      title = "IMDb Search: matrix",
      subtitle = "Search Shortcut",
      icon = ColorDrawable(0),
      packageName = "android",
      deepLink = url,
    )

  @Test
  fun `typed shortcut search opens in an app the shortcut lists`() {
    val imdb = ShortcutCatalog.entries.first { it.shortcut.id == "imdb" }.shortcut
    val url = imdb.urlForQuery("matrix")
    val imdbPackage = "com.imdb.mobile"
    installViewHandler(url, imdbPackage)

    val intent =
      ShortcutLaunch.preferredAppIntentForContent(
        context.packageManager,
        imdbResult("shortcut_${imdb.alias}", url),
        "imdb matrix",
        listOf(imdb),
      )

    assertEquals(Intent.ACTION_VIEW, intent!!.action)
    assertEquals(imdbPackage, intent.`package`)
  }

  @Test
  fun `typed shortcut search falls back to the default app for the link`() {
    val imdb = ShortcutCatalog.entries.first { it.shortcut.id == "imdb" }.shortcut
    val url = imdb.urlForQuery("matrix")

    val intent =
      ShortcutLaunch.preferredAppIntentForContent(
        context.packageManager,
        imdbResult("shortcut_${imdb.alias}", url),
        "imdb matrix",
        listOf(imdb),
      )

    assertNull(intent!!.`package`)
    assertTrue(intent.flags and Intent.FLAG_ACTIVITY_REQUIRE_NON_BROWSER != 0)
  }

  @Test
  fun `shortcutForResultId finds the shortcut behind results and suggestions`() {
    val y = SearchShortcut(id = "youtube", alias = "y", urlTemplate = "", description = "")
    val yt = SearchShortcut(id = "yt_music", alias = "y_t", urlTemplate = "", description = "")
    val shortcuts = listOf(y, yt)

    assertEquals(y, ShortcutLaunch.shortcutForResultId("shortcut_y", shortcuts))
    assertEquals(y, ShortcutLaunch.shortcutForResultId("suggestion_y_cats", shortcuts))
    assertEquals(yt, ShortcutLaunch.shortcutForResultId("suggestion_y_t_cats", shortcuts))
    assertNull(ShortcutLaunch.shortcutForResultId("shortcut_x", shortcuts))
  }

  @Test
  fun `bookmarks stay in the browser even if chrome is named`() {
    val chrome = "com.android.chrome"
    val page = "https://www.youtube.com/watch?v=dQw4w9WgXcQ"
    installViewHandler(page, chrome)
    val bookmark =
      SearchResult.Content(
        id = "bookmark",
        namespace = "web_saved",
        title = "A video",
        subtitle = "Bookmark",
        icon = ColorDrawable(0),
        packageName = chrome,
        deepLink = page,
      )

    assertNull(ShortcutLaunch.preferredAppIntentForContent(context.packageManager, bookmark))
  }

  @Test
  fun `isPreferredAppPackage rejects placeholders`() {
    assertTrue(ShortcutLaunch.isPreferredAppPackage(youtubePackage))
    assertFalse(ShortcutLaunch.isPreferredAppPackage("android"))
    assertFalse(ShortcutLaunch.isPreferredAppPackage(null))
    assertFalse(ShortcutLaunch.isPreferredAppPackage(""))
    assertFalse(ShortcutLaunch.isPreferredAppPackage("application/pdf"))
  }
}
