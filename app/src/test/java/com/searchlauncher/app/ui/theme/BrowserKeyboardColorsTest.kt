package com.searchlauncher.app.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserKeyboardColorsTest {
  @Test
  fun websitePaletteKeepsSurfaceAndReadableKeysInEveryState() {
    // Include midtone colors around the black/white text crossover, not only white and black.
    for (r in 0..255 step 17) for (g in 0..255 step 17) for (b in 0..255 step 17) {
      val site = Color(r, g, b)
      for (base in listOf(lightColorScheme(), darkColorScheme())) {
        val colors = browserKeyboardColors(site, base)
        assertEquals(site, colors.surface)
        val dark = colors.surface.luminance() < colors.onSurface.luminance()
        val pairs =
          listOf(
            colors.surface to colors.onSurface,
            (if (dark) colors.surfaceColorAtElevation(3.dp) else colors.surfaceVariant) to
              (if (dark) colors.onSurface else colors.onSurfaceVariant),
            (if (dark) colors.surfaceColorAtElevation(6.dp) else colors.secondaryContainer) to
              (if (dark) colors.onSurface else colors.onSecondaryContainer),
            colors.primaryContainer to colors.onPrimaryContainer,
            colors.primary to colors.onPrimary,
          )
        for ((background, text) in pairs) {
          val l1 = background.luminance()
          val l2 = text.luminance()
          val contrast = (maxOf(l1, l2) + 0.05f) / (minOf(l1, l2) + 0.05f)
          assertTrue("Unreadable key palette for $site: $contrast", contrast >= 4.5f)
        }
      }
    }
  }
}
