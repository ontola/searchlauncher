package com.searchlauncher.app.data

import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import androidx.test.core.app.ApplicationProvider
import com.searchlauncher.app.SearchLauncherApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
    assertEquals("Type 'r ' to search in Reddit", default("reddit").searchHint)
    assertEquals("Type 'y ' to search in YouTube", default("youtube").searchHint)
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

    assertNotNull(generator.getShortcutIcon(reddit))

    shadowOf(context.packageManager)
      .installPackage(
        PackageInfo().apply {
          packageName = "com.reddit.frontpage"
          applicationInfo = ApplicationInfo().apply { packageName = "com.reddit.frontpage" }
        }
      )
    val badged = generator.getShortcutIcon(reddit)
    assertTrue(badged is android.graphics.drawable.BitmapDrawable)
    assertEquals(size, (badged as android.graphics.drawable.BitmapDrawable).bitmap.width)
  }
}
