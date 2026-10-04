package com.searchlauncher.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance

/** Keep the exact browser surface, with related key shades and contrasting labels. */
internal fun browserKeyboardColors(site: Color, base: ColorScheme): ColorScheme {
  fun foreground(background: Color) =
    if (background.luminance() > 0.179f) Color.Black else Color.White
  val text = foreground(site)
  // Lighten very dark sites; darken midtones/light sites so tonal keys retain readable labels.
  val tint = if (site.luminance() < 0.09f) Color.White else Color.Black
  val key = lerp(site, tint, 0.08f)
  val selected = lerp(site, tint, 0.16f)
  val pressed = lerp(site, tint, 0.24f)
  return base.copy(
    surface = site,
    onSurface = text,
    surfaceTint = tint,
    surfaceVariant = key,
    onSurfaceVariant = foreground(key),
    secondaryContainer = selected,
    onSecondaryContainer = foreground(selected),
    primaryContainer = pressed,
    onPrimaryContainer = foreground(pressed),
    primary = text,
    onPrimary = site,
  )
}
