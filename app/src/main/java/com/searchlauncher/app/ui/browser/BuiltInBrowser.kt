package com.searchlauncher.app.ui.browser

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import com.searchlauncher.app.ui.PreferencesKeys
import com.searchlauncher.app.ui.dataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Whether web pages open in the browser built into the launcher or in the user's own browser app.
 * On by default: tabs, swipe-to-tab and open-tab search only exist with the built-in browser.
 */
object BuiltInBrowser {
  const val DEFAULT = true

  /**
   * Last known value, for code that cannot suspend (result launching, tab search). Kept in sync by
   * [keepInSync]; before the first read it is [DEFAULT], which matches the setting's own default.
   */
  @Volatile
  var enabled: Boolean = DEFAULT
    private set

  fun flow(context: Context): Flow<Boolean> =
    context.dataStore.data.map { it[PreferencesKeys.BUILT_IN_BROWSER] ?: DEFAULT }

  fun keepInSync(context: Context) {
    CoroutineScope(Dispatchers.IO).launch {
      flow(context).distinctUntilChanged().collect { enabled = it }
    }
  }

  /**
   * Opens [url] in another app. SearchLauncher may itself be the default browser, so a plain VIEW
   * intent could land straight back in [BrowserActivity]; ours is left out of the choice.
   */
  fun openExternally(context: Context, url: String) {
    val intent =
      Intent(Intent.ACTION_VIEW, Uri.parse(url))
        .addCategory(Intent.CATEGORY_BROWSABLE)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    val pm = context.packageManager
    val others =
      pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY).filter {
        it.activityInfo.packageName != context.packageName
      }
    val preferred = pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo
    val target =
      when {
        others.isEmpty() -> null
        // The user's default browser is another app: let Android hand it over as usual.
        preferred != null && others.any { it.activityInfo.packageName == preferred.packageName } ->
          intent
        others.size == 1 ->
          others[0].activityInfo.let { Intent(intent).setClassName(it.packageName, it.name) }
        else ->
          Intent.createChooser(intent, null)
            .putExtra(
              Intent.EXTRA_EXCLUDE_COMPONENTS,
              arrayOf(ComponentName(context, BrowserActivity::class.java)),
            )
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
    if (target == null) {
      Toast.makeText(context, "No browser app installed", Toast.LENGTH_SHORT).show()
      return
    }
    try {
      context.startActivity(target)
    } catch (_: Exception) {
      Toast.makeText(context, "Cannot open link", Toast.LENGTH_SHORT).show()
    }
  }
}
