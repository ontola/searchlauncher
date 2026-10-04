package com.searchlauncher.app.ui.browser

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance

/** Contrast against the color actually painted, including a foreground's opacity. */
internal fun browserChromeContrast(foreground: Color, background: Color): Float {
  val a = foreground.compositeOver(background).luminance()
  val b = background.luminance()
  return (maxOf(a, b) + 0.05f) / (minOf(a, b) + 0.05f)
}

/** Prefer our softer text colors, but fall back to black/white to keep small text readable. */
internal fun browserChromeContentColor(background: Color): Color {
  val preferred =
    listOf(Color(0xFF1C1B1F), Color(0xFFEDE8EE)).maxBy { browserChromeContrast(it, background) }
  if (browserChromeContrast(preferred, background) >= 4.5f) return preferred
  return if (browserChromeUsesDarkIcons(background)) Color.Black else Color.White
}

internal fun browserChromeUsesDarkIcons(background: Color): Boolean =
  browserChromeContrast(Color.Black, background) >= browserChromeContrast(Color.White, background)

/** Keep unavailable actions subdued, without letting site colors make them disappear. */
internal fun browserChromeDisabledColor(background: Color, content: Color): Color {
  var low = 0.38f
  var high = 1f
  repeat(12) {
    val alpha = (low + high) / 2f
    if (browserChromeContrast(content.copy(alpha = alpha), background) >= 3f) high = alpha
    else low = alpha
  }
  return content.copy(alpha = high)
}
