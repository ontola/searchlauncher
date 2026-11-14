package com.searchlauncher.app.ui.browser

import android.content.Context
import io.sentry.Sentry
import io.sentry.SentryEvent
import io.sentry.SentryLevel

/**
 * Restarts the app when Gecko freezes the main thread, rather than leaving the launcher frozen.
 *
 * Gecko's own content processes already crash on their own: a page that dies there shows "Reload to
 * try again". What is not isolated is Gecko's main-process side. Reports from 0.1.6 show the main
 * thread waiting, without end, in `GeckoSession$Compositor.syncResumeResizeCompositor` for the
 * compositor to answer a surface change. Because the launcher lives in the same process, the home
 * screen froze too and pressing Home could not get back to it.
 *
 * Once the main thread has sat inside Gecko for four seconds, this reports the stack, marks the tab
 * that was on screen so it does not come back from the same saved state, and ends the process.
 * Android then starts the launcher afresh on the next Home press.
 */
internal object GeckoHangRecovery {
  private var started = false

  @Synchronized
  fun start(context: Context) {
    if (started) return
    started = true
    MainThreadWatchdog(
        isEngineFrame = ::isGeckoFrame,
        onHang = { stack -> recover(context, stack) },
      )
      .start()
  }

  internal fun isGeckoFrame(frame: StackTraceElement): Boolean =
    frame.className.startsWith("org.mozilla.")

  private fun recover(context: Context, stack: Array<StackTraceElement>) {
    val culprit = stack.firstOrNull(::isGeckoFrame)
    android.util.Log.e("GeckoHangRecovery", "Main thread stuck in Gecko at $culprit, restarting")
    runCatching { BrowserCrashGuard.appFroze(context) }
    runCatching {
      BrowserReloadLog.record(
        context,
        "app restarted: main thread stuck in Gecko",
        "$culprit",
        sync = true,
      )
    }
    runCatching { report(stack) }
    android.os.Process.killProcess(android.os.Process.myPid())
  }

  /**
   * Sent only when crash reporting is on; Sentry is not started otherwise and this does nothing.
   */
  private fun report(stack: Array<StackTraceElement>) {
    if (!Sentry.isEnabled()) return
    val hang =
      GeckoHang("Main thread stuck in Gecko for 4 seconds; app restarted").apply {
        stackTrace = stack
      }
    Sentry.captureEvent(
      SentryEvent(hang).apply {
        level = SentryLevel.FATAL
        setTag("recovery", "gecko_hang_restart")
      }
    )
    Sentry.flush(2_000)
  }

  private class GeckoHang(message: String) : RuntimeException(message)
}
