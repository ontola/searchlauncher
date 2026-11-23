package com.searchlauncher.app.ui.browser

import android.app.Application
import android.app.DownloadManager
import android.content.Context
import android.os.Environment
import android.widget.Toast
import java.io.File
import java.io.InputStream
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.mozilla.geckoview.GeckoWebExecutor
import org.mozilla.geckoview.WebRequest
import org.mozilla.geckoview.WebResponse

private val geckoDownloadScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

private fun WebResponse.header(name: String) =
  headers.entries.firstOrNull { it.key.equals(name, true) }?.value

/** A Gecko response as a [DownloadResponse]. */
private class GeckoDownloadResponse(private val response: WebResponse) : DownloadResponse {
  override val status: Int
    // Responses for blob: and data: URLs carry no HTTP status.
    get() = if (response.statusCode == 0) 200 else response.statusCode

  override fun header(name: String) = response.header(name)

  override val body: InputStream
    get() = requireNotNull(response.body) { "This response contains no download" }

  override fun abort() {
    // Gecko's read wait swallows interrupts, and close() does not wake it. A short deadline makes
    // the blocked read return before the stream is closed.
    runCatching { response.setReadTimeoutMillis(1) }
    close()
  }

  override fun close() {
    runCatching { response.body?.close() }
  }
}

/** Gecko builds refetch through Gecko itself, so the browser's cookies and TLS settings apply. */
internal object DownloadEngine {
  fun fetcher(context: Context): DownloadFetcher {
    val app = context.applicationContext
    return DownloadFetcher { url, headers ->
      val executor =
        withContext(Dispatchers.Main) { GeckoWebExecutor(GeckoEnvironment.runtime(app)) }
      val request =
        WebRequest.Builder(url)
          .apply {
            for ((name, value) in headers) {
              if (name.equals("Referer", true)) referrer(value) else header(name, value)
            }
          }
          // A cached copy can be a different version than the bytes already saved.
          .cacheMode(WebRequest.CACHE_MODE_NO_STORE)
          .build()
      val response =
        withContext(Dispatchers.Main) {
          suspendCancellableCoroutine<WebResponse> { continuation ->
            executor
              .fetch(request)
              .accept(
                { result ->
                  if (result == null)
                    continuation.resumeWithException(java.io.IOException("No response"))
                  else if (continuation.isActive) continuation.resume(result)
                  else runCatching { result.body?.close() }
                },
                { error ->
                  val failure =
                    if (error is java.io.IOException) error
                    else java.io.IOException(error?.message ?: "the connection dropped", error)
                  if (continuation.isActive) continuation.resumeWithException(failure)
                },
              )
          }
        }
      response.setReadTimeoutMillis(60_000)
      GeckoDownloadResponse(response)
    }
  }
}

private fun isPrivateProcess() = Application.getProcessName().endsWith(":incognito")

/**
 * Hand Gecko's original response to [ResumableDownloads], which reads it first (so cookies, POST
 * bodies, blobs and single-use URLs still work once) and continues it with Range requests through
 * Gecko when the connection drops. Private windows keep the transfer in their own process and write
 * nothing about it to disk.
 */
internal fun saveGeckoDownload(context: Context, response: WebResponse) {
  if (isPrivateProcess()) {
    saveGeckoDownloadInProcess(context, response)
    return
  }
  val type =
    response.header("Content-Type")?.substringBefore(';')?.trim()?.ifBlank { null }
      ?: "application/octet-stream"
  val name = downloadFileName(response.uri, response.header("Content-Disposition"), type)
  val encoding = response.header("Content-Encoding")
  val identity = encoding.isNullOrBlank() || encoding.equals("identity", true)
  val key = ResumableDownloads.newKey()
  val path =
    try {
      ResumableDownloads.partialPath(context, key, name)
    } catch (_: Exception) {
      Toast.makeText(context, "Download failed: no storage available", Toast.LENGTH_LONG).show()
      runCatching { response.body?.close() }
      return
    }
  ResumableDownloads.start(
    context,
    ResumableRecord(
      key = key,
      url = response.uri,
      name = name,
      mimeType = type,
      path = path,
      // The stream can be decompressed by Gecko, so Content-Length is only a size for identity.
      total = response.header("Content-Length")?.toLongOrNull()?.takeIf { identity && it >= 0 },
      validator = resumeValidator(response.header("ETag"), response.header("Last-Modified")),
      rangeSafe = identity,
      refetchable =
        response.uri.startsWith("https://", true) || response.uri.startsWith("http://", true),
    ),
    GeckoDownloadResponse(response),
  )
}

/** The original single-attempt transfer, for private windows. */
@Suppress("DEPRECATION")
private fun saveGeckoDownloadInProcess(context: Context, response: WebResponse) {
  val app = context.applicationContext
  val key = UUID.randomUUID().toString()
  fun header(name: String) =
    response.headers.entries.firstOrNull { it.key.equals(name, true) }?.value
  val type = header("Content-Type")?.substringBefore(';') ?: "application/octet-stream"
  val name = downloadFileName(response.uri, header("Content-Disposition"), type)
  val total =
    header("Content-Length")?.toLongOrNull()?.takeIf {
      header("Content-Encoding").let { encoding ->
        encoding.isNullOrBlank() || encoding.equals("identity", true)
      }
    }
  pendingPageDownloads[key] = PageDownloadProgress(name, null)
  val notification = DownloadNotification(context, key, name)
  geckoDownloadScope.launch {
    var target: File? = null
    var saved = false
    // Gecko's read wait swallows interrupts, and close() does not wake it. Set a short
    // deadline before interrupting so the awakened read exits, then close the network stream.
    val control =
      PageDownloadControl.attach(key, beforeCancel = { response.setReadTimeoutMillis(1) }) {
        response.body?.close()
      }
    try {
      withContext(Dispatchers.IO) {
        check(response.statusCode == 0 || response.statusCode in 200..299) {
          "Download failed (HTTP ${response.statusCode})"
        }
        val directory = File(app.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), key)
        check(directory.mkdirs()) { "Could not create download folder" }
        val file = File(directory, name).also { target = it }
        var bytes = 0L
        trackDownloadTransfer(key, name, total, notification) { reportBytes ->
          requireNotNull(response.body) { "This response contains no download" }
            .use { input ->
              file.outputStream().use { output ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                  currentCoroutineContext().ensureActive()
                  val count = runInterruptible { input.read(buffer) }
                  if (count < 0) break
                  output.write(buffer, 0, count)
                  bytes += count
                  reportBytes(bytes)
                }
              }
            }
        }
        // The stream can be decompressed by Gecko, so Content-Length is only a progress hint.
        val manager = app.getSystemService(DownloadManager::class.java)
        control.complete(
          name,
          bytes,
          register = {
            manager.addCompletedDownload(
              name,
              "Gecko download",
              false,
              type,
              file.path,
              bytes,
              false,
            )
          },
          onRegistered = { saved = true },
        )
        withContext(Dispatchers.Main) { notification.complete(name) }
      }
      Toast.makeText(app, "Downloaded $name", Toast.LENGTH_LONG).show()
    } catch (error: Exception) {
      if (control.cancelled) notification.cancel()
      else if (error is CancellationException) throw error
      else {
        notification.failed()
        Toast.makeText(app, error.message ?: "Download failed", Toast.LENGTH_LONG).show()
      }
    } finally {
      withContext(NonCancellable) {
        withContext(Dispatchers.IO) {
          runCatching { response.body?.close() }
          if (!saved) {
            target?.delete()
            target?.parentFile?.delete()
          }
        }
        finishPageDownload(key)
      }
    }
  }
}
