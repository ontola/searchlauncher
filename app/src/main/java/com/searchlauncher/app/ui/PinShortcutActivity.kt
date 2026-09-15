package com.searchlauncher.app.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.pm.LauncherApps
import android.os.Bundle
import android.widget.Toast
import com.searchlauncher.app.SearchLauncherApp
import com.searchlauncher.app.data.PrivateSpaceProfiles
import com.searchlauncher.app.data.ProfileItemIds

/**
 * Android delivers an authenticated PinItemRequest, not an arbitrary caller-supplied launch intent.
 */
class PinShortcutActivity : Activity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val launcherApps = getSystemService(LauncherApps::class.java)
    val request =
      try {
        launcherApps.getPinItemRequest(intent)
      } catch (_: Exception) {
        null
      }
    if (
      request == null ||
        !request.isValid ||
        request.requestType != LauncherApps.PinItemRequest.REQUEST_TYPE_SHORTCUT
    ) {
      finish()
      return
    }
    val shortcut =
      request.shortcutInfo
        ?: run {
          finish()
          return
        }
    // Private Space items must never enter the persistent public index or favorites.
    if (PrivateSpaceProfiles.isPrivate(launcherApps, shortcut.userHandle)) {
      finish()
      return
    }
    AlertDialog.Builder(this)
      .setTitle("Add shortcut?")
      .setMessage("Add “${shortcut.shortLabel}” to SearchLauncher favorites?")
      .setNegativeButton("Cancel") { _, _ -> finish() }
      .setOnCancelListener { finish() }
      .setPositiveButton("Add") { _, _ ->
        try {
          val id =
            "${ProfileItemIds.packageKey(this, shortcut.`package`, shortcut.userHandle)}/${shortcut.id}"
          if (!request.isValid || !request.accept()) {
            Toast.makeText(this, "This shortcut request has expired", Toast.LENGTH_SHORT).show()
          } else {
            val app = application as SearchLauncherApp
            if (!app.favoritesRepository.isFavorite("shortcuts", id)) {
              app.favoritesRepository.toggleFavorite("shortcuts", id)
            }
            app.searchRepository.refreshPinnedShortcut(shortcut.`package`)
          }
        } catch (_: Exception) {
          Toast.makeText(this, "Could not add this shortcut", Toast.LENGTH_SHORT).show()
        }
        finish()
      }
      .show()
  }
}
