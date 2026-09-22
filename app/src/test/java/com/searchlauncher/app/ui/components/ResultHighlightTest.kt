package com.searchlauncher.app.ui.components

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ResultHighlightTest {
  @Test
  fun keepsAContainerTheTextCanBeReadOn() {
    val surface = Color(0xFF141210)
    val onSurface = Color(0xFFEDE8EE)
    val container = Color(0xFF5D4032)

    assertTrue(contrastRatio(onSurface, container) >= 4.5f)
    assertEquals(container, resultHighlightColor(surface, onSurface, container))
  }

  @Test
  fun replacesADarkContainerUnderDarkText() {
    // A light page chrome with the dark theme's secondary container still selected. The title is
    // the dark text chosen for the page, which disappears on that brown.
    val surface = Color(0xFFFAFBF6)
    val onSurface = Color(0xFF1C1B1F)
    val container = Color(0xFF5D4032)

    assertTrue(contrastRatio(onSurface, container) < 4.5f)
    val highlight = resultHighlightColor(surface, onSurface, container)

    assertTrue(highlight != container)
    assertTrue(contrastRatio(onSurface, highlight) >= 4.5f)
    assertTrue(highlight.red > 0.7f && highlight.green > 0.7f && highlight.blue > 0.7f)
  }
}
