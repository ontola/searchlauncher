package com.searchlauncher.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.google.android.material.color.utilities.Hct

/** The site supplies a hue, never the brightness of the search UI. */
@android.annotation.SuppressLint("RestrictedApi")
internal fun browserSearchColors(
  site: Color,
  isDark: Boolean,
  chroma: Float,
  isOled: Boolean,
): ColorScheme {
  // Keep neutral websites neutral, and cap vivid branding at a muted accent.
  val accentChroma = minOf(Hct.fromInt(site.toArgb()).chroma.toFloat(), chroma, 24f)
  val scheme = schemeFromUserColor(site.toArgb(), isDark, accentChroma, isOled)
  return scheme.copy(surfaceTint = scheme.primary)
}

internal data class BrowserSurfaceStyle(val dark: Boolean, val chroma: Float, val oled: Boolean)

internal val LocalBrowserSurfaceStyle = staticCompositionLocalOf {
  BrowserSurfaceStyle(dark = false, chroma = 50f, oled = false)
}

/** Reuse the search-results palette with the enclosing app's exact appearance preferences. */
@Composable
internal fun rememberBrowserSurfaceColors(site: Color): ColorScheme {
  val style = LocalBrowserSurfaceStyle.current
  return remember(site, style) { browserSearchColors(site, style.dark, style.chroma, style.oled) }
}
