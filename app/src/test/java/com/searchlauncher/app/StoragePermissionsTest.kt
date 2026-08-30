package com.searchlauncher.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class StoragePermissionsTest {

  @Test
  fun appDoesNotRequestBroadFileOrMediaAccess() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val info =
      context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
    val requested = info.requestedPermissions?.toSet().orEmpty()
    val forbidden =
      setOf(
        Manifest.permission.READ_EXTERNAL_STORAGE,
        Manifest.permission.READ_MEDIA_IMAGES,
        Manifest.permission.READ_MEDIA_VIDEO,
        Manifest.permission.READ_MEDIA_AUDIO,
        Manifest.permission.MANAGE_EXTERNAL_STORAGE,
      )
    val declared = requested.intersect(forbidden)
    assertTrue("Unexpected storage/media permissions: $declared", declared.isEmpty())
  }

  @Test
  fun launcherThemeShowsTheSystemWallpaperThroughTheWindow() {
    val themeFiles =
      listOf("src/main/res/values/themes.xml", "src/main/res/values-night/themes.xml")
        .map(::File)
        .filter { it.exists() }
        .ifEmpty {
          listOf(
              File("app/src/main/res/values/themes.xml"),
              File("app/src/main/res/values-night/themes.xml"),
            )
            .filter { it.exists() }
        }
    assertFalse("theme files missing", themeFiles.isEmpty())
    themeFiles.forEach { file ->
      val text = file.readText()
      assertTrue("${file.path} should show the wallpaper", text.contains("windowShowWallpaper"))
      assertTrue(
        "${file.path} should keep the window transparent",
        text.contains("@android:color/transparent"),
      )
    }
  }
}
