package com.searchlauncher.app.ui

import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.ColorDrawable
import android.os.Build
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class ThemedIconsTest {

  @Config(sdk = [33])
  @Test
  fun apply_tintsMonochromeLayerOnThemePlate() {
    val mono = ColorDrawable(0xFFFFFFFF.toInt())
    val adaptive =
      AdaptiveIconDrawable(ColorDrawable(0xFF0000FF.toInt()), ColorDrawable(0xFF00FF00.toInt()))
    // AdaptiveIconDrawable.monochrome is set via the third constructor on API 33+.
    val withMono =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        AdaptiveIconDrawable(
          ColorDrawable(0xFF0000FF.toInt()),
          ColorDrawable(0xFF00FF00.toInt()),
          mono,
        )
      } else {
        adaptive
      }

    val themed =
      ThemedIcons.apply(
        withMono,
        backgroundArgb = 0xFF112233.toInt(),
        foregroundArgb = 0xFFEEEEEE.toInt(),
      )
    assertNotNull(themed)
    assertTrue(themed is AdaptiveIconDrawable)
  }

  @Config(sdk = [33])
  @Test
  fun apply_leavesNonAdaptiveIconsAlone() {
    val original = ColorDrawable(0xFF123456.toInt())
    assertSame(original, ThemedIcons.apply(original, 0xFF000000.toInt(), 0xFFFFFFFF.toInt()))
  }

  @Config(sdk = [30])
  @Test
  fun apply_isNoOpBeforeAndroid13() {
    val original = ColorDrawable(0xFF123456.toInt())
    assertSame(original, ThemedIcons.apply(original, 0xFF000000.toInt(), 0xFFFFFFFF.toInt()))
  }
}
