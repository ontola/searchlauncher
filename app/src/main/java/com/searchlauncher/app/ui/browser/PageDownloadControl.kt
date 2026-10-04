package com.searchlauncher.app.ui.browser

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val downloadCleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

/** Main-thread cancellation and completion handoff for transfers outside DownloadManager. */
internal class PageDownloadControl
private constructor(
  private val key: String,
  private val job: Job,
  private val closeStream: () -> Unit,
  private val beforeCancel: () -> Unit,
) {
  var cancelled = false
    private set

  private fun cancel() {
    val progress = pendingPageDownloads[key] ?: return
    if (progress.onCancel == null) return // Already cancelling or committing the completed file.
    cancelled = true
    pendingPageDownloads[key] = progress.copy(onCancel = null, cancelling = true)
    runCatching(beforeCancel)
    job.cancel()
    // Closing a blocked stream must not hold up the UI or depend on its cancelled coroutine.
    downloadCleanupScope.launch { runCatching(closeStream) }
  }

  suspend fun complete(name: String, bytes: Long, register: () -> Long, onRegistered: () -> Unit) {
    withContext(Dispatchers.Main.immediate) {
      currentCoroutineContext().ensureActive()
      pendingPageDownloads[key]?.let { pendingPageDownloads[key] = it.copy(onCancel = null) }
      // Once committed, cancellation must not delete a file whose history row already exists.
      withContext(NonCancellable) {
        val id = withContext(Dispatchers.IO) { register() }
        onRegistered()
        completePageDownload(key, id, name, bytes)
      }
    }
  }

  companion object {
    suspend fun attach(
      key: String,
      beforeCancel: () -> Unit = {},
      closeStream: () -> Unit = {},
    ): PageDownloadControl {
      val control =
        PageDownloadControl(key, currentCoroutineContext().job, closeStream, beforeCancel)
      pendingPageDownloads[key]?.let {
        pendingPageDownloads[key] = it.copy(onCancel = control::cancel)
      }
      return control
    }
  }
}
