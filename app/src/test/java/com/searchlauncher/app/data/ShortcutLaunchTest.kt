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
