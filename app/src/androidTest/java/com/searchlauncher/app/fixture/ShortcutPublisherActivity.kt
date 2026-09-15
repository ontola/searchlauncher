package com.searchlauncher.app.fixture

import android.app.Activity
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.os.Bundle
import android.widget.TextView

/**
 * Manual device fixture: same requestPinShortcut API used by Shelter; no real accounts required.
 */
class ShortcutPublisherActivity : Activity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val manager = getSystemService(ShortcutManager::class.java)
    val label =
      if (intent.getBooleanExtra("opened", false)) "Pinned shortcut opened"
      else "Shortcut publisher"
    setContentView(
      TextView(this).apply {
        text = "$label\nPinning supported: ${manager.isRequestPinShortcutSupported}"
      }
    )
    if (intent.getBooleanExtra("request", false)) {
      manager.requestPinShortcut(
        ShortcutInfo.Builder(this, intent.getStringExtra("shortcut_id") ?: "shelter/probe")
          .setShortLabel("Shelter shortcut probe")
          .setIcon(Icon.createWithResource(this, android.R.drawable.ic_menu_view))
          .setIntent(
            Intent(this, ShortcutPublisherActivity::class.java)
              .setAction(Intent.ACTION_VIEW)
              .putExtra("opened", true)
          )
          .build(),
        null,
      )
    }
  }
}
