package com.searchlauncher.app.ui.browser

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import org.junit.Assert.*
import org.junit.Test

class BrowserChromeContrastTest {
  @Test
  fun siteColorsAndEveryTransitionFrameKeepReadableText() {
    val colors = buildList {
      for (r in 0..255 step 17) for (g in 0..255 step 17) for (b in 0..255 step 17) add(
        Color(r, g, b)
      )
      for (step in 0..140) {
        add(lerp(Color.White, Color(0xFF0283EB), step / 140f))
        add(lerp(Color(0xFFAA1239), Color.Black, step / 140f))
      }
    }
    for (background in colors) {
      val content = browserChromeContentColor(background)
      assertTrue("Text on $background", browserChromeContrast(content, background) >= 4.5f)
      val disabled = browserChromeDisabledColor(background, content)
      assertTrue(
        "Disabled action on $background",
        browserChromeContrast(disabled, background) >= 3f,
      )
      assertTrue(disabled.alpha < 1f)
      val icon = if (browserChromeUsesDarkIcons(background)) Color.Black else Color.White
      assertTrue(browserChromeContrast(icon, background) >= 4.5f)
    }
  }

  @Test
  fun vividBlueNeedsBlackRatherThanWhiteOrTheOldDarkGray() {
    val blue = Color(0xFF0283EB)
    assertTrue(browserChromeContrast(Color(0xFF1C1B1F), blue) < 4.5f)
    assertTrue(browserChromeContrast(Color.White, blue) < 4.5f)
    assertEquals(Color.Black, browserChromeContentColor(blue))
  }
}
