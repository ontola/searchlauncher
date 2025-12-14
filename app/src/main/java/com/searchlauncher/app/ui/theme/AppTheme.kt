package com.searchlauncher.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.google.android.material.color.utilities.Hct
import com.google.android.material.color.utilities.TonalPalette

@Composable
fun SearchLauncherTheme(
  themeColor: Int,
  darkThemeMode: Int = 0, // 0: System, 1: Light, 2: Dark
  chroma: Float = 50f,
  isOled: Boolean = false,
  content: @Composable () -> Unit,
) {
  val useDarkTheme =
    when (darkThemeMode) {
      1 -> false
      2 -> true
      else -> isSystemInDarkTheme()
    }

  val colorScheme =
    remember(themeColor, useDarkTheme, chroma, isOled) {
      schemeFromUserColor(themeColor, useDarkTheme, chroma, isOled)
    }

  val view = LocalView.current
  if (!view.isInEditMode) {
    SideEffect {
      val window = (view.context as Activity).window
      // Force navigation bar color to be transparent to let edge-to-edge work
      // or set it to match theme if desired. For now, keep transparent for edge-to-edge.
      // But we MUST update the icon colors (appearanceLight...).

      val insetsController = WindowCompat.getInsetsController(window, view)
      insetsController.isAppearanceLightStatusBars = !useDarkTheme
      insetsController.isAppearanceLightNavigationBars = !useDarkTheme
    }
  }

  MaterialTheme(colorScheme = colorScheme, content = content)
}

@android.annotation.SuppressLint("RestrictedApi")
fun schemeFromUserColor(
  seed: Int,
  isDark: Boolean,
  chroma: Float,
  isOled: Boolean = false,
): ColorScheme {
  val seedHct = Hct.fromInt(seed)
  val hue = seedHct.hue

  val primaryPalette = TonalPalette.fromHueAndChroma(hue, chroma.toDouble())
  val secondaryPalette = TonalPalette.fromHueAndChroma(hue, chroma.toDouble() / 3.0)
  val tertiaryPalette = TonalPalette.fromHueAndChroma(hue + 60.0, chroma.toDouble() / 2.0)
  val neutralPalette = TonalPalette.fromHueAndChroma(hue, chroma.toDouble() / 8.0)
  val neutralVariantPalette = TonalPalette.fromHueAndChroma(hue, chroma.toDouble() / 4.0)
  val errorPalette = TonalPalette.fromHueAndChroma(25.0, 84.0)

  val primary = if (isDark) Color(primaryPalette.tone(80)) else Color(primaryPalette.tone(40))
  val onPrimary = if (isDark) Color(primaryPalette.tone(20)) else Color(primaryPalette.tone(100))
  val primaryContainer =
    if (isDark) Color(primaryPalette.tone(30)) else Color(primaryPalette.tone(90))
  val onPrimaryContainer =
    if (isDark) Color(primaryPalette.tone(90)) else Color(primaryPalette.tone(10))

  val secondary = if (isDark) Color(secondaryPalette.tone(80)) else Color(secondaryPalette.tone(40))
  val onSecondary =
    if (isDark) Color(secondaryPalette.tone(20)) else Color(secondaryPalette.tone(100))
  val secondaryContainer =
    if (isDark) Color(secondaryPalette.tone(30)) else Color(secondaryPalette.tone(90))
  val onSecondaryContainer =
    if (isDark) Color(secondaryPalette.tone(90)) else Color(secondaryPalette.tone(10))

  val tertiary = if (isDark) Color(tertiaryPalette.tone(80)) else Color(tertiaryPalette.tone(40))
  val onTertiary = if (isDark) Color(tertiaryPalette.tone(20)) else Color(tertiaryPalette.tone(100))
  val tertiaryContainer =
    if (isDark) Color(tertiaryPalette.tone(30)) else Color(tertiaryPalette.tone(90))
  val onTertiaryContainer =
    if (isDark) Color(tertiaryPalette.tone(90)) else Color(tertiaryPalette.tone(10))

  val error = if (isDark) Color(errorPalette.tone(80)) else Color(errorPalette.tone(40))
  val onError = if (isDark) Color(errorPalette.tone(20)) else Color(errorPalette.tone(100))
  val errorContainer = if (isDark) Color(errorPalette.tone(30)) else Color(errorPalette.tone(90))
  val onErrorContainer = if (isDark) Color(errorPalette.tone(90)) else Color(errorPalette.tone(10))

  val background =
    if (isDark && isOled) Color.Black
    else if (isDark) Color(neutralPalette.tone(6)) else Color(neutralPalette.tone(98))
  val onBackground = if (isDark) Color(neutralPalette.tone(90)) else Color(neutralPalette.tone(10))
  val surface =
    if (isDark && isOled) Color.Black
    else if (isDark) Color(neutralPalette.tone(6)) else Color(neutralPalette.tone(98))
  val onSurface = if (isDark) Color(neutralPalette.tone(90)) else Color(neutralPalette.tone(10))
  val surfaceVariant =
    if (isDark) Color(neutralVariantPalette.tone(30)) else Color(neutralVariantPalette.tone(90))
  val onSurfaceVariant =
    if (isDark) Color(neutralVariantPalette.tone(80)) else Color(neutralVariantPalette.tone(30))
  val outline =
    if (isDark) Color(neutralVariantPalette.tone(60)) else Color(neutralVariantPalette.tone(50))
  val outlineVariant =
    if (isDark) Color(neutralVariantPalette.tone(30)) else Color(neutralVariantPalette.tone(80))
  val scrim = Color(neutralPalette.tone(0))
  val inverseSurface =
    if (isDark) Color(neutralPalette.tone(90)) else Color(neutralPalette.tone(20))
  val inverseOnSurface =
    if (isDark) Color(neutralPalette.tone(20)) else Color(neutralPalette.tone(95))
  val inversePrimary =
    if (isDark) Color(primaryPalette.tone(40)) else Color(primaryPalette.tone(80))

  return if (isDark) {
    darkColorScheme(
      primary = primary,
      onPrimary = onPrimary,
      primaryContainer = primaryContainer,
      onPrimaryContainer = onPrimaryContainer,
      secondary = secondary,
      onSecondary = onSecondary,
      secondaryContainer = secondaryContainer,
      onSecondaryContainer = onSecondaryContainer,
      tertiary = tertiary,
      onTertiary = onTertiary,
      tertiaryContainer = tertiaryContainer,
      onTertiaryContainer = onTertiaryContainer,
      error = error,
      onError = onError,
      errorContainer = errorContainer,
      onErrorContainer = onErrorContainer,
      background = background,
      onBackground = onBackground,
      surface = surface,
      onSurface = onSurface,
      surfaceVariant = surfaceVariant,
      onSurfaceVariant = onSurfaceVariant,
      outline = outline,
      outlineVariant = outlineVariant,
      scrim = scrim,
      inverseSurface = inverseSurface,
      inverseOnSurface = inverseOnSurface,
      inversePrimary = inversePrimary,
    )
  } else {
    lightColorScheme(
      primary = primary,
      onPrimary = onPrimary,
      primaryContainer = primaryContainer,
      onPrimaryContainer = onPrimaryContainer,
      secondary = secondary,
      onSecondary = onSecondary,
      secondaryContainer = secondaryContainer,
      onSecondaryContainer = onSecondaryContainer,
      tertiary = tertiary,
      onTertiary = onTertiary,
      tertiaryContainer = tertiaryContainer,
      onTertiaryContainer = onTertiaryContainer,
      error = error,
      onError = onError,
      errorContainer = errorContainer,
      onErrorContainer = onErrorContainer,
      background = background,
      onBackground = onBackground,
      surface = surface,
      onSurface = onSurface,
      surfaceVariant = surfaceVariant,
      onSurfaceVariant = onSurfaceVariant,
      outline = outline,
      outlineVariant = outlineVariant,
      scrim = scrim,
      inverseSurface = inverseSurface,
      inverseOnSurface = inverseOnSurface,
      inversePrimary = inversePrimary,
    )
  }
}
