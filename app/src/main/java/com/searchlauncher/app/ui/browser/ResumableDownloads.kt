package com.searchlauncher.app.ui.browser

import android.app.Application
import android.app.DownloadManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.os.Environment
import android.os.SystemClock
import android.util.AtomicFile
import android.widget.Toast
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** How a resumable download appears on its Downloads card. */
internal data class ResumableStatus(
  val state: ResumableState,
  val bytes: Long,
  val total: Long?,
  val error: String? = null,
  val retryAt: Long = 0,
  val onPause: (() -> Unit)? = null,
  val onResume: (() -> Unit)? = null,
)

/**
 * Downloads the app transfers itself, kept going by [DownloadService]: they wait for the network,
 * retry with backoff, continue with HTTP Range requests from the bytes already saved, and pick up
 * again after the app process was killed. All state changes happen on the main thread.
 */
internal object ResumableDownloads {
  private const val STORE = "resumable-downloads.json"
  private const val PROGRESS_INTERVAL_MS = 500L
  private const val PERSIST_INTERVAL_MS = 3_000L

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

  @OptIn(ExperimentalCoroutinesApi::class)
  private val storeWriter = Dispatchers.IO.limitedParallelism(1)
  private val records = LinkedHashMap<String, ResumableRecord>()
  private val jobs = mutableMapOf<String, Job>()
  private val responses = mutableMapOf<String, DownloadResponse>()
  private val initialResponses = mutableMapOf<String, DownloadResponse>()
  private val notifications = mutableMapOf<String, DownloadNotification>()
  private var app: Application? = null

  private val _working = MutableStateFlow<List<String>>(emptyList())
  /** Keys of downloads the service must keep the process alive for, oldest first. */
  val working: StateFlow<List<String>> = _working.asStateFlow()

  private val online = MutableStateFlow(true)
  private val networkReturned = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

  private fun isMainProcess(context: Context) = Application.getProcessName() == context.packageName

  /** Load persisted downloads and show them on the Downloads screen. Safe to call repeatedly. */
  fun load(context: Context) {
    if (app != null || !isMainProcess(context)) return
    val application = context.applicationContext as Application
    app = application
    watchNetwork(application)
    val stored =
      runCatching { String(storeFile(application).readFully()) }
        .getOrNull()
        ?.let { ResumableRecord.listFromJson(it) } ?: emptyList()
    for (record in stored) {
      if (record.key in records) continue
      // The file is the source of truth for progress: the last persisted count can lag behind.
      val saved = File(record.path).length()
      records[record.key] = record.copy(bytes = saved)
      publish(records.getValue(record.key))
    }
    syncWorking()
  }

  /**
   * Restart downloads the process was working on when it died. Call from a visible Activity:
   * Android only lets a foreground app start the download service.
   */
  fun resumeInterrupted(context: Context) {
    load(context)
    val application = app ?: return
    val interrupted = records.values.filter { it.state.working && it.key !in jobs }
    if (interrupted.isEmpty()) return
    interrupted.forEach { launch(application, it.key) }
    DownloadService.ensureRunning(application)
  }

  /**
   * Start a new download. [initial] is a response already in hand (Gecko's original response), used
   * for the first attempt so single-use links and POST results still work once.
   */
  fun start(context: Context, record: ResumableRecord, initial: DownloadResponse? = null) {
    load(context)
    val application = app ?: return
    records[record.key] = record.copy(state = ResumableState.QUEUED)
    initial?.let { initialResponses[record.key] = it }
    persist()
    publish(records.getValue(record.key))
    launch(application, record.key)
    DownloadService.ensureRunning(application)
  }

  fun pause(key: String) {
    val record = records[key] ?: return
    if (!record.state.working) return
    update(key) { it.copy(state = ResumableState.PAUSED, error = null, retryAt = 0) }
    stop(key)
    notificationOf(key)?.status(record.name, "Paused", records[key]?.fraction, ongoing = false)
  }

  private fun notificationOf(key: String): DownloadNotification? {
    val application = app ?: return null
    val record = records[key] ?: return notifications[key]
    return notifications.getOrPut(key) {
      DownloadNotification(application, key, record.name, foreground = true)
    }
  }

  fun resume(context: Context, key: String) {
    load(context)
    val application = app ?: return
    val record = records[key] ?: return
    if (record.state.working && key in jobs) return
    update(key) { it.copy(state = ResumableState.QUEUED, attempts = 0, error = null) }
    launch(application, key)
    DownloadService.ensureRunning(application)
  }

  fun cancel(key: String) {
    val record = records.remove(key) ?: return
    persist()
    pendingPageDownloads[key]?.let {
      pendingPageDownloads[key] = it.copy(onCancel = null, cancelling = true)
    }
    val job = jobs.remove(key)
    responses.remove(key)?.let { response -> scope.launch(Dispatchers.IO) { response.abort() } }
    initialResponses.remove(key)?.let { response ->
      scope.launch(Dispatchers.IO) { response.close() }
    }
    syncWorking()
    scope.launch {
      job?.cancelAndJoin()
      withContext(Dispatchers.IO) { deletePartial(record) }
      notifications.remove(key)?.cancel()
        ?: app
          ?.getSystemService(android.app.NotificationManager::class.java)
          ?.cancel(DownloadNotification.foregroundNotificationId(key))
      pendingPageDownloads.remove(key)
    }
  }

  /** Android's foreground-service time limit ran out: keep the files, wait for the user. */
  fun pauseAll(reason: String) {
    for (key in records.filterValues { it.state.working }.keys.toList()) {
      update(key) { it.copy(state = ResumableState.PAUSED, error = reason) }
      stop(key)
      records[key]?.let { notificationOf(key)?.status(it.name, "Paused", it.fraction, false) }
    }
  }

  fun notificationFor(key: String) = notifications[key]

  private fun stop(key: String) {
    val job = jobs.remove(key)
    responses.remove(key)?.let { response -> scope.launch(Dispatchers.IO) { response.abort() } }
    // A response that never got read cannot be resumed later anyway: the next attempt refetches.
    initialResponses.remove(key)?.let { response ->
      scope.launch(Dispatchers.IO) { response.close() }
    }
    job?.cancel()
    syncWorking()
  }

  private fun launch(application: Application, key: String) {
    if (jobs[key]?.isActive == true) return
    val notification = notificationOf(key) ?: return
    jobs[key] =
      scope.launch {
        try {
          transfer(application, key, notification)
        } finally {
          if (jobs[key] == currentCoroutineContext()[Job]) jobs.remove(key)
          syncWorking()
        }
      }
    syncWorking()
  }

  private suspend fun transfer(
    application: Application,
    key: String,
    notification: DownloadNotification,
  ) {
    val fetcher = fetcherForTest ?: DownloadEngine.fetcher(application)
    var initial = initialResponses.remove(key)
    while (true) {
      currentCoroutineContext().ensureActive()
      val before = records[key] ?: return
      if (initial == null && !before.refetchable) {
        fail(key, notification, "the site would have to send it again")
        return
      }
      if (initial == null && !online.value) {
        update(key) { it.copy(state = ResumableState.WAITING_FOR_NETWORK, retryAt = 0) }
        notification.status(before.name, "Waiting for network", before.fraction, ongoing = true)
        online.first { it }
      }
      update(key) { it.copy(state = ResumableState.RUNNING, retryAt = 0) }
      val file = File(before.path)
      val offset =
        withContext(Dispatchers.IO) {
          file.parentFile?.mkdirs()
          if (before.rangeSafe) file.length() else 0L
        }
      var response: DownloadResponse? = null
      try {
        val fromInitial = initial != null
        response = initial ?: fetcher.fetch(before.url, resumeHeaders(before, offset))
        initial = null
        responses[key] = response
        val decision =
          if (fromInitial) ResumeDecision.Restart(before.total)
          else
            decideResume(
              offset = offset,
              expectedTotal = before.total,
              status = response.status,
              contentRange = response.header("Content-Range"),
              contentLength = response.header("Content-Length")?.toLongOrNull(),
              contentEncoding = response.header("Content-Encoding"),
            )
        if (
          !fromInitial &&
            (decision is ResumeDecision.Append || decision is ResumeDecision.Restart) &&
            isPageInsteadOfFile(before.mimeType, response.header("Content-Type"))
        ) {
          // A login page or an error page would be saved as the file.
          fail(key, notification, "the site sent a page instead of the file")
          return
        }
        when (decision) {
          is ResumeDecision.Fail -> {
            fail(key, notification, decision.reason)
            return
          }
          is ResumeDecision.Retry -> {
            if (decision.fromZero) withContext(Dispatchers.IO) { file.delete() }
            throw RetryableDownloadException(decision.reason)
          }
          ResumeDecision.Complete -> Unit
          is ResumeDecision.Append,
          is ResumeDecision.Restart -> {
            val append = decision is ResumeDecision.Append
            val total =
              if (decision is ResumeDecision.Append) decision.total
              else (decision as ResumeDecision.Restart).total
            if (!append && !fromInitial) {
              val validator =
                resumeValidator(response.header("ETag"), response.header("Last-Modified"))
              val identity =
                response.header("Content-Encoding").let {
                  it.isNullOrBlank() || it.equals("identity", true)
                }
              update(key) { it.copy(validator = validator, rangeSafe = identity) }
            }
            if (!append && before.provisional) {
              val type =
                response.header("Content-Type")?.substringBefore(';')?.trim()?.ifBlank { null }
                  ?: "application/octet-stream"
              val name = downloadFileName(before.url, response.header("Content-Disposition"), type)
              update(key) {
                it.copy(
                  name = name,
                  mimeType = type,
                  path = File(file.parentFile, name).path,
                  provisional = false,
                )
              }
            }
            update(key) { it.copy(total = total) }
            val target = File(records.getValue(key).path)
            copy(key, response, target, if (append) offset else 0L, total, notification)
            val saved = withContext(Dispatchers.IO) { target.length() }
            if (total != null && saved < total)
              throw RetryableDownloadException("the connection dropped")
            if (total != null && saved > total) {
              withContext(Dispatchers.IO) { target.delete() }
              throw RetryableDownloadException("the file changed on the server")
            }
          }
        }
        finish(application, key, notification)
        return
      } catch (error: CancellationException) {
        throw error
      } catch (error: Exception) {
        val record = records[key] ?: return
        val saved = withContext(Dispatchers.IO) { File(record.path).length() }
        val transient = error is RetryableDownloadException || isTransientDownloadError(error)
        val reason = (error as? RetryableDownloadException)?.reason ?: downloadErrorReason(error)
        if (!transient) {
          fail(key, notification, reason)
          return
        }
        if (!online.value) {
          // Losing the network is not the download's fault: wait for it without counting.
          update(key) { it.copy(bytes = saved, attempts = 0, error = null) }
          continue
        }
        val attempts = if (saved > offset) 1 else record.attempts + 1
        if (attempts > MAX_DOWNLOAD_ATTEMPTS || !record.refetchable) {
          fail(key, notification, reason)
          return
        }
        val wait = retryDelayMillis(attempts)
        update(key) {
          it.copy(
            state = ResumableState.RETRYING,
            bytes = saved,
            attempts = attempts,
            error = reason,
            retryAt = System.currentTimeMillis() + wait,
          )
        }
        notification.status(
          record.name,
          "Retrying in ${(wait + 999) / 1000} s: $reason",
          records[key]?.fraction,
          ongoing = true,
        )
        // A network that just came back is the best moment to try again.
        withTimeoutOrNull(wait) { networkReturned.first() }
      } finally {
        responses.remove(key)
        response?.let {
          withContext(NonCancellable + Dispatchers.IO) { runCatching { it.close() } }
        }
      }
    }
  }

  private suspend fun copy(
    key: String,
    response: DownloadResponse,
    file: File,
    offset: Long,
    total: Long?,
    notification: DownloadNotification,
  ) {
    val name = records[key]?.name ?: return
    val bytes = AtomicLong(offset)
    val tracker = DownloadSpeedTracker()
    tracker.sample(offset, total, SystemClock.elapsedRealtime())
    val ticker =
      scope.launch {
        var persisted = SystemClock.elapsedRealtime()
        while (true) {
          delay(PROGRESS_INTERVAL_MS)
          val now = SystemClock.elapsedRealtime()
          val transfer = tracker.sample(bytes.get(), total, now)
          if (records[key]?.state != ResumableState.RUNNING) continue
          records[key] = records.getValue(key).copy(bytes = transfer.bytes)
          publish(records.getValue(key), transfer)
          notification.progress(name, transfer.fraction, transfer, pausable = true)
          if (now - persisted >= PERSIST_INTERVAL_MS) {
            persisted = now
            persist()
          }
        }
      }
    try {
      withContext(Dispatchers.IO) {
        FileOutputStream(file, offset > 0).use { output ->
          if (offset == 0L) output.channel.truncate(0)
          val input = response.body
          val buffer = ByteArray(64 * 1024)
          var written = offset
          while (true) {
            currentCoroutineContext().ensureActive()
            val read = input.read(buffer)
            if (read < 0) break
            output.write(buffer, 0, read)
            written += read
            bytes.set(written)
          }
        }
      }
    } finally {
      withContext(NonCancellable) { ticker.cancelAndJoin() }
    }
  }

  @Suppress("DEPRECATION")
  private suspend fun finish(
    application: Application,
    key: String,
    notification: DownloadNotification,
  ) {
    val record = records[key] ?: return
    val file = File(record.path)
    val size = withContext(Dispatchers.IO) { file.length() }
    // Committed: from here on a cancel must not delete a file whose history row exists.
    pendingPageDownloads[key]?.let { pendingPageDownloads[key] = it.copy(onCancel = null) }
    val id =
      withContext(NonCancellable + Dispatchers.IO) {
        runCatching {
            application
              .getSystemService(DownloadManager::class.java)
              .addCompletedDownload(
                record.name,
                android.net.Uri.parse(record.url).host ?: "Download",
                false,
                record.mimeType,
                file.path,
                size,
                false,
              )
          }
          .getOrNull()
      }
    if (id == null) {
      fail(key, notification, "the file could not be added to Downloads")
      return
    }
    records.remove(key)
    persist()
    syncWorking()
    jobs.remove(key)
    notifications.remove(key)
    completePageDownload(key, id, record.name, size)
    notification.complete(record.name)
    Toast.makeText(application, "Downloaded ${record.name}", Toast.LENGTH_LONG).show()
  }

  private fun fail(key: String, notification: DownloadNotification, reason: String) {
    val record = records[key] ?: return
    update(key) {
      it.copy(
        state = ResumableState.FAILED,
        error = reason,
        retryAt = 0,
        bytes = File(it.path).length(),
      )
    }
    jobs.remove(key)
    syncWorking()
    notification.status(record.name, "Download failed: $reason", null, ongoing = false)
  }

  private fun update(key: String, change: (ResumableRecord) -> ResumableRecord) {
    val record = records[key] ?: return
    val next = change(record)
    records[key] = next
    if (next.state != record.state || next.error != record.error) persist()
    publish(
      next,
      pendingPageDownloads[key]?.transfer?.takeIf { next.state == ResumableState.RUNNING },
    )
    syncWorking()
  }

  private fun publish(record: ResumableRecord, transfer: DownloadTransfer? = null) {
    val key = record.key
    val previous = pendingPageDownloads[key]
    if (previous?.completed != null) return
    val status =
      ResumableStatus(
        state = record.state,
        bytes = record.bytes,
        total = record.total,
        error = record.error,
        retryAt = record.retryAt,
        onPause = if (record.state.working && record.refetchable) ({ pause(key) }) else null,
        onResume =
          if (!record.state.working && record.refetchable) ({ app?.let { resume(it, key) } })
          else null,
      )
    pendingPageDownloads[key] =
      (previous ?: PageDownloadProgress(record.name, record.fraction, startedAt = record.createdAt))
        .copy(
          name = record.name,
          fraction = record.fraction,
          transfer = transfer,
          onCancel = { cancel(key) },
          cancelling = false,
          resumable = status,
        )
  }

  private fun syncWorking() {
    val next = records.values.filter { it.state.working }.map { it.key }
    if (_working.value != next) _working.value = next
  }

  private fun persist() {
    val application = app ?: return
    val snapshot = ResumableRecord.listToJson(records.values.toList())
    scope.launch(storeWriter) {
      val store = storeFile(application)
      val stream = runCatching { store.startWrite() }.getOrNull() ?: return@launch
      try {
        stream.write(snapshot.toByteArray())
        store.finishWrite(stream)
      } catch (_: Exception) {
        store.failWrite(stream)
      }
    }
  }

  private fun storeFile(context: Context) = AtomicFile(File(context.noBackupFilesDir, STORE))

  private fun deletePartial(record: ResumableRecord) {
    val file = File(record.path)
    file.delete()
    file.parentFile?.takeIf { it.list()?.isEmpty() == true }?.delete()
  }

  private fun watchNetwork(application: Application) {
    val connectivity = application.getSystemService(ConnectivityManager::class.java) ?: return
    online.value = connectivity.activeNetwork != null
    runCatching {
      connectivity.registerDefaultNetworkCallback(
        object : ConnectivityManager.NetworkCallback() {
          override fun onAvailable(network: Network) {
            scope.launch {
              online.value = true
              networkReturned.tryEmit(Unit)
            }
          }

          override fun onLost(network: Network) {
            scope.launch {
              online.value = connectivity.activeNetwork != null
              // A read on a vanished network can hang until its timeout; fail it now instead.
              if (!online.value)
                responses.values.toList().forEach {
                  launch(Dispatchers.IO) { runCatching { it.abort() } }
                }
            }
          }
        }
      )
    }
  }

  /** A fresh partial-file location next to page exports, one folder per download. */
  fun partialPath(context: Context, key: String, name: String): String {
    val root =
      requireNotNull(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)) {
        "No storage available"
      }
    return File(File(root, key), name).path
  }

  fun newKey(): String = UUID.randomUUID().toString()

  /** For tests: replaces the engine's fetcher. */
  internal var fetcherForTest: DownloadFetcher? = null

  /** For tests: forget everything held in memory. */
  internal fun resetForTest() {
    jobs.values.forEach { it.cancel() }
    jobs.clear()
    records.clear()
    responses.clear()
    initialResponses.clear()
    notifications.clear()
    app = null
    _working.value = emptyList()
  }
}

internal class RetryableDownloadException(val reason: String) : java.io.IOException(reason)

/**
 * True when a refetch came back as a web page although the download was not one: the site wants a
 * login or a fresh link, and saving that page as the file would be worse than stopping.
 */
internal fun isPageInsteadOfFile(expected: String?, actual: String?): Boolean {
  val type = actual?.substringBefore(';')?.trim()?.lowercase() ?: return false
  val wanted = expected?.substringBefore(';')?.trim()?.lowercase()
  return type == "text/html" && wanted != "text/html"
}
