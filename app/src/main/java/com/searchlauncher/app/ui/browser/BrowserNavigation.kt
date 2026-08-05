package com.searchlauncher.app.ui.browser

import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sign

/**
 * Whether a released tab swipe should switch tabs, given how far it travelled ([offsetPx]) and how
 * fast it was going when it left ([velocityPxPerSecond], positive rightwards).
 *
 * Distance *or* speed, the way a photo viewer pages: a slow drag has to cover ground, a flick does
 * not. [commitDistanceCapPx] is what stops the distance being a share of the viewport all the way
 * up — on a tablet that share is a reach rather than a swipe.
 *
 * All in pixels, so the caller resolves dp against its own density and this stays testable.
 */
internal fun shouldCommitTabSwipe(
  offsetPx: Float,
  velocityPxPerSecond: Float,
  viewportWidthPx: Int,
  commitFraction: Float,
  commitDistanceCapPx: Float,
  flingVelocityPx: Float,
): Boolean {
  if (offsetPx == 0f) return false
  val distance = min(viewportWidthPx * commitFraction, commitDistanceCapPx)
  if (abs(offsetPx) >= distance) return true
  // Only in the direction the tab actually moved: a flick back towards where it came from is the
  // user putting it down, not asking for the next one.
  return abs(velocityPxPerSecond) >= flingVelocityPx && sign(velocityPxPerSecond) == sign(offsetPx)
}

internal fun browserDestination(input: String): String {
  val trimmed = input.trim()
  if (
    trimmed.startsWith("https://", ignoreCase = true) ||
      trimmed.startsWith("http://", ignoreCase = true)
  ) {
    return trimmed
  }

  if (!trimmed.contains(' ') && (trimmed.contains('.') || trimmed.startsWith("localhost"))) {
    return "https://$trimmed"
  }

  val encoded = URLEncoder.encode(trimmed, StandardCharsets.UTF_8.toString())
  return "https://www.google.com/search?q=$encoded"
}

/**
 * Parameters that identify how you arrived at a page rather than which page it is. Stripped before
 * comparing, because a bookmark saved from one campaign link and the same page reached from another
 * are the same place — and a raw string comparison would say otherwise, which is exactly when a
 * duplicate tab appears.
 */
private val TRACKING_PARAMS =
  setOf(
    "gclid",
    "dclid",
    "gbraid",
    "wbraid",
    "fbclid",
    "msclkid",
    "yclid",
    "igshid",
    "mc_cid",
    "mc_eid",
    "_ga",
  )

private fun String.isTrackingParam(): Boolean {
  val name = substringBefore('=').lowercase()
  return name in TRACKING_PARAMS || name.startsWith("utm_") || name.startsWith("gad_")
}

/**
 * What makes two addresses the same page, for deciding whether one is already open.
 *
 * Deliberately not the URL itself. Scheme is dropped because http and https are the same page
 * reached two ways; the fragment because it is a position within a page rather than another one;
 * `www.`, a trailing slash and query order because none of them change where you end up. The port
 * is kept — two servers on one host are two different places.
 *
 * Domains are *not* collapsed: one site is many pages, and treating them as one would mean tapping
 * a bookmark and landing somewhere you did not ask for.
 */
internal fun browserTabMatchKey(url: String): String {
  val trimmed = url.trim()
  val uri = runCatching { URI(trimmed) }.getOrNull()
  val host = uri?.host?.lowercase()?.removePrefix("www.") ?: return trimmed.lowercase()
  val authority = if (uri.port > 0) "$host:${uri.port}" else host
  val path = (uri.path ?: "").trimEnd('/')
  val query =
    uri.rawQuery
      ?.split('&')
      ?.filter { it.isNotBlank() && !it.isTrackingParam() }
      ?.sorted()
      ?.joinToString("&")
      .orEmpty()
  return if (query.isEmpty()) "$authority$path" else "$authority$path?$query"
}

/**
 * Which of [tabUrls] is already showing [url], or -1. Matched against where each tab is *now*
 * rather than what it was opened with: a tab that has since been navigated elsewhere is not showing
 * that page any more, and asking for it should open it afresh.
 */
internal fun indexOfTabShowing(tabUrls: List<String>, url: String): Int {
  val key = browserTabMatchKey(url)
  return tabUrls.indexOfFirst { browserTabMatchKey(it) == key }
}
