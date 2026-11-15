package com.searchlauncher.app.ui.browser

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build

/**
 * Keeps a page that brought the whole app down from doing it again the moment it is reopened.
 *
 * A tab normally comes back exactly as it was, history and all. When the browser engine froze or
 * crashed the app while a page was on screen, that same saved state is the likeliest thing to
 * freeze it again, so that one tab opens its address fresh instead, once. Losing its back history
 * is a much smaller price than a launcher that falls over every time it is reopened.
 */
internal object BrowserCrashGuard {
  private const val PREFS = "browser-crash-guard"
  private const val KEY_ON_SCREEN = "on_screen_tab"
  private const val KEY_SKIP = "skip_restore_tab"

  @Volatile private var onScreen: Long? = null
  private var checkedPreviousExit = false

  /** Called when a tab's page is shown, so a crash while it is up can be traced back to it. */
  fun shown(context: Context, tabId: Long) {
    if (onScreen == tabId) return
    // Read what the previous process left behind before this one overwrites it.
    checkPreviousExit(context)
    onScreen = tabId
    prefs(context).edit().putLong(KEY_ON_SCREEN, tabId).apply()
  }

  /** Called when a tab's page is hidden. Another tab may already have taken the screen. */
  fun hidden(context: Context, tabId: Long) {
    if (onScreen != tabId) return
    onScreen = null
    prefs(context).edit().remove(KEY_ON_SCREEN).apply()
  }

  /**
   * Called from the watchdog just before it ends the frozen process: written synchronously, since
   * nothing queued after this point will run.
   */
  fun appFroze(context: Context) {
    val tab = onScreen ?: return
    prefs(context).edit().putLong(KEY_SKIP, tab).commit()
  }

  /** Whether [tabId] may come back from its saved state, or should load its address fresh. */
  fun mayRestore(context: Context, tabId: Long): Boolean {
    val prefs = prefs(context)
    checkPreviousExit(context)
    if (!prefs.contains(KEY_SKIP) || prefs.getLong(KEY_SKIP, 0) != tabId) return true
    prefs.edit().remove(KEY_SKIP).apply()
    return false
  }

  /**
   * A native crash or an "isn't responding" kill leaves no chance to write anything, so the tab
   * that was on screen when it happened is worked out at the next start instead.
   */
  @Synchronized
  private fun checkPreviousExit(context: Context) {
    if (checkedPreviousExit) return
    checkedPreviousExit = true
    val prefs = prefs(context)
    if (!prefs.contains(KEY_ON_SCREEN)) return
    val tab = prefs.getLong(KEY_ON_SCREEN, 0)
    val edit = prefs.edit().remove(KEY_ON_SCREEN)
    if (lastExitWasCrash(context)) edit.putLong(KEY_SKIP, tab)
    edit.apply()
  }

  private fun lastExitWasCrash(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false
    val manager =
      context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return false
    val process = android.app.Application.getProcessName()
    return runCatching {
        val last =
          manager.getHistoricalProcessExitReasons(context.packageName, 0, 10).firstOrNull {
            it.processName == process
          } ?: return false
        isCrash(last.reason)
      }
      .getOrDefault(false)
  }

  internal fun isCrash(reason: Int): Boolean =
    reason == ApplicationExitInfo.REASON_CRASH_NATIVE ||
      reason == ApplicationExitInfo.REASON_ANR ||
      reason == ApplicationExitInfo.REASON_CRASH

  private fun prefs(context: Context) =
    context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
