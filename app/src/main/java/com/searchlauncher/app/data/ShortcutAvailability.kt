package com.searchlauncher.app.data

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri

/**
 * Whether a search shortcut has anything to open.
 *
 * A shortcut pointing at an app — `claude://`, `spotify:`, `market://` — is useless when that app
 * is not installed: launching it throws and the user gets a toast instead of a search. Those are
 * kept out of the places a shortcut is *offered* (the index, the search-options bar, the alias
 * results) so the user is never shown an option that cannot work.
 *
 * Settings deliberately still lists them, because a shortcut you cannot see is one you cannot edit
 * or delete.
 */
object ShortcutAvailability {
  /**
   * The scheme the launcher answers itself. `internal://widget` never reaches an intent at all —
   * [SearchRepository.getShortcutResults] intercepts it by id — so no installed app backs it and
   * resolving it would wrongly come back empty.
   */
  private const val INTERNAL_SCHEME = "internal"

  /** A web template needs no app: the launcher carries its own browser. */
  fun isWebTemplate(urlTemplate: String): Boolean =
    urlTemplate.startsWith("http://", ignoreCase = true) ||
      urlTemplate.startsWith("https://", ignoreCase = true)

  /**
   * Resolved with an empty query, since what decides the answer is the scheme, action and type —
   * not what the user is searching for.
   *
   * [Intent.parseUri] is what the launch path itself uses, so a shortcut is tested exactly as it
   * would be opened. That matters for the `intent:` templates, which name an action rather than a
   * scheme and would resolve to nothing if treated as a plain URI.
   *
   * Anything this cannot judge counts as available, following [SearchRepository.isPackagePresent]:
   * a transient PackageManager failure, or a template too malformed to reason about, must not make
   * a shortcut vanish. A template with no scheme is one of those — [Intent.parseUri] accepts it
   * without complaint and hands back an intent nothing resolves, which would look exactly like a
   * missing app.
   */
  fun isAvailable(packageManager: PackageManager, shortcut: SearchShortcut): Boolean {
    if (isWebTemplate(shortcut.urlTemplate)) return true
    return try {
      val uri = shortcut.urlForQuery("")
      val scheme = Uri.parse(uri).scheme
      if (scheme.isNullOrBlank() || scheme.equals(INTERNAL_SCHEME, ignoreCase = true)) return true
      packageManager.resolveActivity(Intent.parseUri(uri, Intent.URI_INTENT_SCHEME), 0) != null
    } catch (_: Exception) {
      true
    }
  }
}
