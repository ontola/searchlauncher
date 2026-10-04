package com.searchlauncher.app.ui.browser

import android.app.DownloadManager
import android.content.Context
import android.os.Environment
import android.widget.Toast
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import org.mozilla.geckoview.WebResponse

private val geckoDownloadScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

/** Consume Gecko's original response: preserve cookies, POST bodies, blobs, and single-use URLs. */
@Suppress("DEPRECATION")
internal fun saveGeckoDownload(context: Context, response: WebResponse) {
  val app = context.applicationContext
  val key = UUID.randomUUID().toString()
  fun header(name: String) =
    response.headers.entries.firstOrNull { it.key.equals(name, true) }?.value
  val type = header("Content-Type")?.substringBefore(';') ?: "application/octet-stream"
  val name = downloadFileName(response.uri, header("Content-Disposition"), type)
  val total = header("Content-Length")?.toLongOrNull()
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
        var reported = 0L
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
                if (bytes - reported >= 512 * 1024) {
                  reported = bytes
                  withContext(Dispatchers.Main) {
                    notification.progress(
                      name,
                      total?.takeIf { it > 0 }?.let { bytes.toFloat() / it },
                    )
                    updatePageDownload(
                      key,
                      name,
                      total?.takeIf { it > 0 }?.let { (bytes.toFloat() / it).coerceIn(0f, 1f) },
                    )
                  }
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
