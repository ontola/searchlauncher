package com.searchlauncher.app.ui.browser

import android.os.Debug
import android.os.Handler
import android.os.Looper
import android.os.SystemClock

/**
 * Notices when the main thread has been stuck inside the browser engine for longer than a person
 * would wait, and hands its stack to [onHang].
 *
 * The browser engine shares its process with the launcher, so when it stops answering the home
 * screen stops answering with it, and pressing Home lands in the same frozen process. Android only
 * steps in after its own "isn't responding" timeout, and some manufacturers leave a frozen
 * background app frozen for much longer. [onHang] is expected to end the process: Android then
 * starts the launcher again from scratch, which costs the open page but gives the phone back.
 *
 * Only a stall whose stack is in the engine counts. A slow frame in the launcher's own code is not
 * something restarting the app would fix.
 */
internal class MainThreadWatchdog(
  private val isEngineFrame: (StackTraceElement) -> Boolean,
  private val onHang: (Array<StackTraceElement>) -> Unit,
  private val intervalMs: Long = 500,
  thresholdMs: Long = 4_000,
) {
  private val verdict = HangVerdict(thresholdMs, requiredSamples = 3)
  private val main = Handler(Looper.getMainLooper())
  @Volatile private var pendingSince = 0L
  private val beat = Runnable { pendingSince = 0L }

  fun start() {
    Thread(::watch, "engine-watchdog").apply { isDaemon = true }.start()
  }

  private fun watch() {
    val mainThread = Looper.getMainLooper().thread
    while (true) {
      try {
        Thread.sleep(intervalMs)
      } catch (_: InterruptedException) {
        return
      }
      val now = SystemClock.uptimeMillis()
      val since = pendingSince
      if (since == 0L) {
        verdict.sample(blockedForMs = 0, inEngine = false)
        pendingSince = now
        main.post(beat)
        continue
      }
      // A debugger parks the main thread on breakpoints; that is not a hang.
      if (Debug.isDebuggerConnected()) continue
      val stack = mainThread.stackTrace
      if (verdict.sample(now - since, stack.any(isEngineFrame))) {
        onHang(stack)
        return
      }
    }
  }
}

/**
 * Decides, one stack sample at a time, whether the main thread is stuck in the engine: blocked for
 * at least [thresholdMs], and inside the engine for each of the last [requiredSamples] samples, so
 * that a long stall which merely passes through the engine at the moment it is sampled is not
 * blamed on it.
 */
internal class HangVerdict(private val thresholdMs: Long, private val requiredSamples: Int) {
  private var engineSamples = 0

  fun sample(blockedForMs: Long, inEngine: Boolean): Boolean {
    engineSamples = if (blockedForMs > 0 && inEngine) engineSamples + 1 else 0
    return blockedForMs >= thresholdMs && engineSamples >= requiredSamples
  }
}
