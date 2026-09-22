package com.searchlauncher.app.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import kotlin.math.max
import kotlin.math.min

/**
 * Colour of the keyboard selection behind a result row.
 *
 * [container] is the theme's secondary container, which is the right highlight when the row is
 * painted in that same theme. A results panel opened over a web page keeps the page's own chrome
 * colour while the container colour stays the theme's — a dark brown under the dark text chosen for
 * a light page. Text has to stay readable, so a container that fails that test is replaced by a
 * tint of the row's own surface.
 */
internal fun resultHighlightColor(surface: Color, onSurface: Color, container: Color): Color {
  if (contrastRatio(onSurface, container) >= MIN_RESULT_HIGHLIGHT_CONTRAST) return container
  return lerp(surface, onSurface, RESULT_HIGHLIGHT_TINT)
}

/** WCAG AA for normal-size text. A selection the title cannot be read on is not a selection. */
private const val MIN_RESULT_HIGHLIGHT_CONTRAST = 4.5f

/** How far a fallback highlight moves from the row surface toward its text. */
private const val RESULT_HIGHLIGHT_TINT = 0.12f

internal fun contrastRatio(foreground: Color, background: Color): Float {
  val lighter = max(foreground.luminance(), background.luminance())
  val darker = min(foreground.luminance(), background.luminance())
  return (lighter + 0.05f) / (darker + 0.05f)
}
