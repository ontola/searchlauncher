package com.searchlauncher.app.data

import android.graphics.Bitmap
import java.net.URI

/**
 * Favicons are stored per host rather than per page: every page on a site shares one icon, so a
 * history full of pages from a handful of sites costs a handful of files.
 */
internal fun faviconHost(url: String): String? =
  runCatching { URI(url.trim()).host }.getOrNull()?.lowercase()?.takeIf { it.isNotEmpty() }

/** Key for a host's favicon in [IconRepository]'s shared memory and disk caches. */
internal fun faviconCacheKey(host: String): String = "$FAVICON_KEY_PREFIX$host"

/** Distinguishes favicons from app and shortcut icons, which share the same cache directory. */
internal const val FAVICON_KEY_PREFIX = "favicon_"

/** Match the shared disk cache so size/config differences do not cause needless rewrites. */
internal fun normalizedFavicon(icon: Bitmap): Bitmap? {
  if (icon.isRecycled) return null
  return runCatching {
      val scaled = Bitmap.createScaledBitmap(icon, 192, 192, true)
      try {
        // Both browser engines retain ownership of their bitmap; never cache or recycle it.
        scaled.copy(Bitmap.Config.ARGB_8888, false)
      } finally {
        if (scaled !== icon) scaled.recycle()
      }
    }
    .getOrNull()
}
