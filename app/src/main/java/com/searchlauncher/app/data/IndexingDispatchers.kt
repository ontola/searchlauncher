package com.searchlauncher.app.data

import android.os.Process
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher

/**
 * Indexing is CPU- and Binder-heavy (PackageManager, AppSearch, bitmap encode). Running that work
 * on [kotlinx.coroutines.Dispatchers.IO] at default thread priority lets it compete with the UI
 * thread and with interactive search, which is why a "background" rebuild can still make typing
 * feel sticky.
 *
 * A single background-priority thread keeps rebuilds off the interactive path and naturally
 * serializes durable writes.
 */
object IndexingDispatchers {
  private val threadCount = AtomicInteger()

  val limited: CoroutineDispatcher =
    Executors.newSingleThreadExecutor { runnable ->
        Thread(
            {
              Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
              runnable.run()
            },
            "search-index-${threadCount.incrementAndGet()}",
          )
          .apply { isDaemon = true }
      }
      .asCoroutineDispatcher()
}
