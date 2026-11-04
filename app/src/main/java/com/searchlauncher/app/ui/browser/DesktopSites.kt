package com.searchlauncher.app.ui.browser

import android.content.Context
import com.searchlauncher.app.ui.PreferencesKeys
import com.searchlauncher.app.ui.dataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Whether new tabs ask for the desktop version of sites. Each tab can still switch with the menu's
 * "Desktop site" / "Mobile site" item; this only decides where a tab starts.
 *
 * Off by default on every device, tablets included, so nothing changes until someone asks for it.
 */
object DesktopSites {
  const val DEFAULT = false

  /**
   * Last known value, read when a tab is created, which cannot suspend. Kept in sync by
   * [keepInSync]; until the first read, and in processes that do not sync it, it is [DEFAULT].
   */
  @Volatile
  var enabled: Boolean = DEFAULT
    private set

  fun flow(context: Context): Flow<Boolean> =
    context.dataStore.data.map { it[PreferencesKeys.BROWSER_DESKTOP_SITES] ?: DEFAULT }

  fun keepInSync(context: Context) {
    CoroutineScope(Dispatchers.IO).launch {
      flow(context).distinctUntilChanged().collect { enabled = it }
    }
  }
}
