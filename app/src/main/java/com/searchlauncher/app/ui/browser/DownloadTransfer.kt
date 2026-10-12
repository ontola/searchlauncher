package com.searchlauncher.app.ui.browser

import android.os.SystemClock
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class DownloadTransfer(
  val bytes: Long,
  val total: Long?,
  val bytesPerSecond: Double?,
) {
  val remaining: Long?
    get() = total?.takeIf { it >= bytes && it >= 0 }?.minus(bytes)

  val fraction: Float?
    get() = total?.takeIf { it > 0 && it >= bytes }?.let { bytes.toFloat() / it }

  fun description(locale: Locale = Locale.getDefault()): String {
    fun mb(value: Long) = String.format(locale, "%.1f MB", value / 1_000_000.0)
    val speed =
      bytesPerSecond?.let {
        if (it >= 1_000_000) String.format(locale, "%.1f MB/s", it / 1_000_000)
        else String.format(locale, "%.0f KB/s", it / 1_000)
      }
    val size = remaining?.let { "${mb(it)} left" } ?: "${mb(bytes)} downloaded · Size unknown"
    return listOfNotNull(speed, size).joinToString(" · ")
  }
}

/** Recent throughput, including idle intervals; elapsed time must use a monotonic clock. */
internal class DownloadSpeedTracker {
  private val samples = ArrayDeque<Pair<Long, Long>>()

  fun sample(bytes: Long, total: Long?, now: Long): DownloadTransfer {
    val count = bytes.coerceAtLeast(0)
    val last = samples.lastOrNull()
    if (last != null && (count < last.second || now < last.first)) samples.clear()
    samples.addLast(now to count)
    // Keep the sample immediately before the three-second window as its baseline.
    while (samples.size > 2 && samples.elementAt(1).first <= now - 3_000) samples.removeFirst()
    val first = samples.first()
    val elapsed = now - first.first
    val speed = if (elapsed >= 500) (count - first.second) * 1_000.0 / elapsed else null
    return DownloadTransfer(count, total?.takeIf { it >= count && it >= 0 }, speed)
  }
}

/** A timer keeps slow or stalled transfers accurate without posting UI work for every chunk. */
internal suspend fun <T> trackDownloadTransfer(
  key: String,
  name: String,
  total: Long?,
  notification: DownloadNotification,
  copy: suspend (reportBytes: (Long) -> Unit) -> T,
): T = coroutineScope {
  val bytes = AtomicLong()
  val tracker = DownloadSpeedTracker()
  tracker.sample(0, total, SystemClock.elapsedRealtime())
  val ticker =
    launch(Dispatchers.Main) {
      while (true) {
        val transfer = tracker.sample(bytes.get(), total, SystemClock.elapsedRealtime())
        val pending = pendingPageDownloads[key]
        if (pending != null && pending.completed == null && !pending.cancelling) {
          updatePageDownload(key, name, transfer.fraction, transfer)
          notification.progress(name, transfer.fraction, transfer)
        }
        delay(500)
      }
    }
  try {
    copy { bytes.set(it) }
  } finally {
    // Stop before completion/cancellation can publish its final state.
    withContext(NonCancellable) { ticker.cancelAndJoin() }
  }
}
