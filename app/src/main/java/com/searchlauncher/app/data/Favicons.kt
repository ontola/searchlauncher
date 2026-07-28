package com.searchlauncher.app.data

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

/**
 * How long a stored favicon is trusted before the next visit replaces it. Sites change their icon
 * rarely, so this only needs to be short enough that a change isn't cached forever.
 */
internal const val FAVICON_MAX_AGE_MS = 30L * 24 * 60 * 60 * 1000
