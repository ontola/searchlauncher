package com.searchlauncher.app.ui.theme

import androidx.compose.material3.ColorScheme
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
