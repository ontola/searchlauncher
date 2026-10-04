package com.searchlauncher.app.ui.theme

import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import com.google.android.material.color.utilities.Hct
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserSearchColorsTest {
  @Test
  fun siteHueNeverOverridesDarkModeOrOledAndKeepsTextReadable() {
    for (site in
      listOf(Color.White, Color.Black, Color.Red, Color.Green, Color.Blue, Color(0xFFAA1239))) {
      for (dark in listOf(false, true)) for (oled in listOf(false, true)) {
        val colors = browserSearchColors(site, dark, 50f, oled)
        if (dark && oled) assertEquals(Color.Black, colors.surface)
        assertTrue(
          if (dark) colors.surface.luminance() < 0.02f else colors.surface.luminance() > 0.9f
        )
        val key = if (dark) colors.surfaceColorAtElevation(3.dp) else colors.surfaceVariant
        if (dark) assertTrue("Dark keys must stay dark", key.luminance() < 0.06f)
        for ((background, text) in
          listOf(
            colors.surface to colors.onSurface,
            key to (if (dark) colors.onSurface else colors.onSurfaceVariant),
            colors.primaryContainer to colors.onPrimaryContainer,
            colors.secondaryContainer to colors.onSecondaryContainer,
            colors.primary to colors.onPrimary,
          )) {
          val a = background.luminance()
          val b = text.luminance()
          assertTrue((maxOf(a, b) + 0.05f) / (minOf(a, b) + 0.05f) >= 4.5f)
        }
        assertTrue("Site accent must be muted", Hct.fromInt(colors.primary.toArgb()).chroma < 27)
      }
    }
  }
}
