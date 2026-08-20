package com.searchlauncher.app.ui

import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.flow.map

/**
 * Material You-style themed icons: the app's monochrome layer, tinted, on a theme-color plate.
 *
 * Apps that do not ship a monochrome layer are left alone. Pre-Android 13 has no monochrome API, so
 * theming is a no-op there.
 */
object ThemedIcons {
  fun apply(drawable: Drawable?, backgroundArgb: Int, foregroundArgb: Int): Drawable? {
    if (drawable == null) return null
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return drawable
    val adaptive = drawable as? AdaptiveIconDrawable ?: return drawable
    val mono = adaptive.monochrome ?: return drawable
    val foreground = (mono.constantState?.newDrawable() ?: mono).mutate()
    foreground.setTint(foregroundArgb)
    return AdaptiveIconDrawable(ColorDrawable(backgroundArgb), foreground)
  }
}

@Composable
fun rememberThemedIconBitmap(drawable: Drawable?): ImageBitmap? {
  val context = LocalContext.current
  val themed by
    remember { context.dataStore.data.map { it[PreferencesKeys.THEMED_ICONS] ?: false } }
      .collectAsState(initial = false)
  val background = MaterialTheme.colorScheme.primary.toArgb()
  val foreground = MaterialTheme.colorScheme.onPrimary.toArgb()
  return remember(drawable, themed, background, foreground) {
    val toDraw = if (themed) ThemedIcons.apply(drawable, background, foreground) else drawable
    toDraw?.toImageBitmap()
  }
}
