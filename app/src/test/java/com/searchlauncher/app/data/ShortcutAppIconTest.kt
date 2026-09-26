package com.searchlauncher.app.data

import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import androidx.test.core.app.ApplicationProvider
import com.searchlauncher.app.SearchLauncherApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = SearchLauncherApp::class)
class ShortcutAppIconTest {
  private val context = ApplicationProvider.getApplicationContext<SearchLauncherApp>()

  private fun default(id: String) = DefaultShortcuts.searchShortcuts.first { it.id == id }

  @Test
  fun `known apps name the app they search in`() {
    assertEquals("Search inside Reddit", default("reddit").searchHint)
    assertEquals("Search inside YouTube", default("youtube").searchHint)
  }

  @Test
  fun `shortcuts without an app keep the plain hint`() {
    assertEquals("Type 'cal ' to search", default("calendar").searchHint)
  }

  @Test
  fun `the explicit package comes before the borrowed ones`() {
    val custom = default("reddit").copy(packageName = "com.example.reddit")
    assertEquals(listOf("com.example.reddit", "com.reddit.frontpage"), custom.iconPackages)
  }

  @Test
  fun `an app row finds the shortcut that searches inside it`() {
    val shortcuts = DefaultShortcuts.searchShortcuts
    assertEquals("reddit", shortcuts.forApp("com.reddit.frontpage")?.id)
    assertEquals("youtube", shortcuts.forApp("com.google.android.youtube")?.id)
    assertEquals(null, shortcuts.forApp("com.example.unrelated"))
  }

  @Test
  fun `every default with an app icon has a label to name it by`() {
    DefaultShortcuts.searchShortcuts
      .filter { it.iconPackages.isNotEmpty() }
      .forEach { assertTrue(it.id, it.shortLabel != null) }
  }

  @Test
  fun `falls back to the letter tile when the app is missing and badges it when installed`() {
    val generator = SearchIconGenerator(context)
    val reddit = default("reddit")
    val size = (40 * context.resources.displayMetrics.density).toInt()
    val appIcon = ColorDrawable(Color.RED)

    val letterTile = generator.getShortcutIcon(reddit)
    assertNotNull(letterTile)
    assertNotSame(appIcon, letterTile)

    val packageManager = shadowOf(context.packageManager)
    packageManager.installPackage(
      PackageInfo().apply {
        packageName = "com.reddit.frontpage"
        applicationInfo = ApplicationInfo().apply { packageName = "com.reddit.frontpage" }
      }
    )
    packageManager.setApplicationIcon("com.reddit.frontpage", appIcon)

    assertSame(appIcon, generator.getShortcutIcon(reddit))
    val badged = generator.getShortcutIcon(reddit, badged = true)
    assertTrue(badged is BitmapDrawable)
    assertEquals(size, (badged as BitmapDrawable).bitmap.width)
  }
}
