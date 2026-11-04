package com.searchlauncher.app.ui.browser

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import android.os.Process
import android.os.SystemClock
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Why open tabs had to load their page again, kept on the device and attached to feedback reports.
 *
 * Tabs reload for reasons the browser cannot prevent (Android stopping a page process or the whole
 * app) and for reasons it can, and from the outside they look identical. This records which one it
 * was, with the timing around it, so a report sent right after a reload says what happened. Pages'
 * addresses are never recorded.
 */
internal object BrowserReloadLog {
  private const val PREFS = "browser-reload-log"
  private const val KEY = "events"
  private const val LIMIT = 40

  fun record(context: Context, reason: String, details: String = "") {
    val app = context.applicationContext
    val uptimeS = (SystemClock.elapsedRealtime() - Process.getStartElapsedRealtime()) / 1000
    val line =
      listOf(timestamp(System.currentTimeMillis()), reason, "app up ${uptimeS}s", details)
        .filter { it.isNotEmpty() }
        .joinToString(" | ")
    android.util.Log.i("BrowserReloadLog", line)
    val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    val lines = (prefs.getString(KEY, null)?.lines().orEmpty() + line).takeLast(LIMIT)
    prefs.edit().putString(KEY, lines.joinToString("\n")).apply()
  }

  fun text(context: Context): String =
    context.applicationContext
      .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
      .getString(KEY, null)
      .orEmpty()
      .ifEmpty { "none recorded" }

  /**
   * Android's own account of how this app's processes ended: the app itself and each Gecko page
   * process. This is what tells a low-memory kill apart from a manufacturer's battery policy.
   */
  fun processExits(context: Context): String {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return "needs Android 11"
    val manager =
      context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return "unavailable"
    return runCatching {
        manager.getHistoricalProcessExitReasons(null, 0, 25).joinToString("\n") { exit ->
          listOf(
              timestamp(exit.timestamp),
              exit.processName,
              exitReason(exit.reason),
              "importance ${exit.importance}",
              "pss ${exit.pss / 1024}MB",
              exit.description.orEmpty(),
            )
            .filter { it.isNotEmpty() }
            .joinToString(" | ")
        }
      }
      .getOrElse { "unavailable: ${it.javaClass.simpleName}" }
      .ifEmpty { "none recorded" }
  }

  /**
   * The main thread's stack from the app's most recent "isn't responding" exit, which Android keeps
   * after the dialog is dismissed. That stack is what says what the app was stuck on.
   */
  fun lastAnrMainThread(context: Context): String {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return "needs Android 11"
    val manager =
      context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return "unavailable"
    return runCatching {
        val anr =
          manager.getHistoricalProcessExitReasons(null, 0, 25).firstOrNull {
            it.reason == ApplicationExitInfo.REASON_ANR
          } ?: return "none recorded"
        val trace =
          anr.traceInputStream?.bufferedReader()?.use { it.readText() } ?: return "no trace kept"
        val lines = trace.lines()
        val start = lines.indexOfFirst { it.startsWith("\"main\"") }
        val stack =
          if (start < 0) lines.take(80)
          else lines.drop(start).takeWhile { it.isNotBlank() }.take(80)
        "${timestamp(anr.timestamp)} ${anr.processName} ${anr.description.orEmpty()}\n" +
          stack.joinToString("\n")
      }
      .getOrElse { "unavailable: ${it.javaClass.simpleName}" }
  }

  @androidx.annotation.RequiresApi(Build.VERSION_CODES.R)
  private fun exitReason(reason: Int): String =
    when (reason) {
      ApplicationExitInfo.REASON_LOW_MEMORY -> "low memory"
      ApplicationExitInfo.REASON_SIGNALED -> "signaled"
      ApplicationExitInfo.REASON_CRASH -> "crash"
      ApplicationExitInfo.REASON_CRASH_NATIVE -> "native crash"
      ApplicationExitInfo.REASON_ANR -> "anr"
      ApplicationExitInfo.REASON_EXIT_SELF -> "exit self"
      ApplicationExitInfo.REASON_USER_REQUESTED -> "user requested"
      ApplicationExitInfo.REASON_USER_STOPPED -> "user stopped"
      ApplicationExitInfo.REASON_DEPENDENCY_DIED -> "dependency died"
      ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "excessive resource usage"
      ApplicationExitInfo.REASON_PERMISSION_CHANGE -> "permission change"
      ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "initialization failure"
      ApplicationExitInfo.REASON_OTHER -> "other"
      else -> "reason $reason"
    }

  private fun timestamp(ms: Long): String =
    SimpleDateFormat("MM-dd HH:mm:ss", Locale.ROOT).format(Date(ms))
}
