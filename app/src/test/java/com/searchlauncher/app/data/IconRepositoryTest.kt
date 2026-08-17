package com.searchlauncher.app.data

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import androidx.test.core.app.ApplicationProvider
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class IconRepositoryTest {

  private lateinit var context: Context
  private lateinit var repository: IconRepository

  @Before
  fun setup() {
    context = ApplicationProvider.getApplicationContext()
    repository = IconRepository(context)
    File(context.filesDir, "favorite_icons").deleteRecursively()
  }

  @Test
  fun saveToDisk_writesWhenFileIsMissingEvenIfNotForced() {
    repository.saveToDisk("new_icon", ColorDrawable(Color.RED), force = false)

    val file = iconFile("new_icon")
    assertTrue("Missing icons must still be written on the lazy path", file.exists())
    assertTrue(file.length() > 0)
  }

  @Test
  fun saveToDisk_skipsExistingFileWhenNotForced() {
    repository.saveToDisk("cached_icon", ColorDrawable(Color.RED), force = true)
    val file = iconFile("cached_icon")
    val firstModified = file.lastModified()
    val firstSize = file.length()

    file.setLastModified(firstModified - 5_000)
    val stampedModified = file.lastModified()

    repository.saveToDisk("cached_icon", ColorDrawable(Color.BLUE), force = false)

    assertEquals(
      "Re-encoding an unchanged icon on every index pass is the CPU spike users feel",
      stampedModified,
      file.lastModified(),
    )
    assertEquals(firstSize, file.length())
  }

  @Test
  fun saveToDisk_overwritesWhenForced() {
    repository.saveToDisk("forced_icon", ColorDrawable(Color.RED), force = true)
    val file = iconFile("forced_icon")
    file.setLastModified(file.lastModified() - 5_000)
    val stampedModified = file.lastModified()

    repository.saveToDisk("forced_icon", ColorDrawable(Color.BLUE), force = true)

    assertTrue(
      "force=true must refresh an icon the caller knows changed",
      file.lastModified() > stampedModified,
    )
  }

  @Test
  fun invalidateShortcutIcons_dropsOnlyThatPackage() {
    repository.saveToDisk("shortcut_com.chat.app/conv1", ColorDrawable(Color.RED), force = true)
    repository.saveToDisk(
      "static_shortcut_com.chat.app/settings",
      ColorDrawable(Color.GREEN),
      force = true,
    )
    repository.saveToDisk("shortcut_com.other.app/conv1", ColorDrawable(Color.BLUE), force = true)
    repository.saveToDisk("appicon_com.chat.app", ColorDrawable(Color.YELLOW), force = true)
    repository.putMemory("shortcut_com.chat.app/conv1", ColorDrawable(Color.RED))
    repository.putMemory("shortcut_com.other.app/conv1", ColorDrawable(Color.BLUE))

    repository.invalidateShortcutIcons("com.chat.app")

    assertTrue(!iconFile("shortcut_com.chat.app_conv1").exists())
    assertTrue(!iconFile("static_shortcut_com.chat.app_settings").exists())
    assertTrue(
      "Other packages' shortcut icons must survive",
      iconFile("shortcut_com.other.app_conv1").exists(),
    )
    assertTrue(
      "App icons are refreshed separately via cacheAppIcon(force=true)",
      iconFile("appicon_com.chat.app").exists(),
    )
    assertTrue(repository.getMemory("shortcut_com.chat.app/conv1") == null)
    assertTrue(repository.getMemory("shortcut_com.other.app/conv1") != null)
  }

  private fun iconFile(id: String) = File(context.filesDir, "favorite_icons/${id}.png")
}
