package com.searchlauncher.app.ui

import android.content.Context
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

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

  /**
   * The drawable [apply] should actually be handed, which is not always the one on screen.
   *
   * Icons that have been through the disk cache come back as a flattened PNG, and a bitmap has no
   * monochrome layer left to tint - which silently turned the whole feature into a no-op. So for
   * anything that is not already adaptive, go back to PackageManager for the live icon. Costs an
   * IPC, hence the IO dispatcher and the callers caching the result.
   */
  suspend fun resolveThemeable(
    context: Context,
    drawable: Drawable?,
    packageName: String?,
  ): Drawable? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return drawable
    if (drawable is AdaptiveIconDrawable) return drawable
    if (packageName.isNullOrBlank() || '/' in packageName) return drawable
    return withContext(Dispatchers.IO) {
      runCatching { context.packageManager.getApplicationIcon(packageName) }.getOrNull() ?: drawable
    }
  }
}

/**
 * The icon to draw for [drawable], themed when the setting is on.
 *
 * The untinted bitmap is produced synchronously so the row never renders blank; the themed one
 * replaces it once the live icon has been fetched.
 */
@Composable
fun rememberThemedIconBitmap(drawable: Drawable?, packageName: String? = null): ImageBitmap? {
  val context = LocalContext.current
  val themed by
    remember { context.dataStore.data.map { it[PreferencesKeys.THEMED_ICONS] ?: false } }
      .collectAsState(initial = false)
  val background = MaterialTheme.colorScheme.primary.toArgb()
  val foreground = MaterialTheme.colorScheme.onPrimary.toArgb()

  val plain = remember(drawable) { drawable?.toImageBitmap() }
  val themedBitmap by
    produceState<ImageBitmap?>(null, drawable, packageName, themed, background, foreground) {
      value =
        if (!themed) null
        else {
          val source = ThemedIcons.resolveThemeable(context, drawable, packageName)
          withContext(Dispatchers.IO) {
            ThemedIcons.apply(source, background, foreground)?.toImageBitmap()
          }
        }
    }

  return themedBitmap ?: plain
}
