package com.searchlauncher.app.ui.browser

import android.app.DownloadManager
import android.content.Context
import android.os.Environment
import android.widget.Toast
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
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
  geckoDownloadScope.launch {
    var target: File? = null
    var saved = false
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
                val count = input.read(buffer)
                if (count < 0) break
                output.write(buffer, 0, count)
                bytes += count
                if (bytes - reported >= 512 * 1024) {
                  reported = bytes
                  withContext(Dispatchers.Main) {
                    pendingPageDownloads[key] =
                      PageDownloadProgress(
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
        manager.addCompletedDownload(name, "Gecko download", false, type, file.path, bytes, true)
        saved = true
      }
      Toast.makeText(app, "Downloaded $name", Toast.LENGTH_LONG).show()
    } catch (error: Exception) {
      Toast.makeText(app, error.message ?: "Download failed", Toast.LENGTH_LONG).show()
    } finally {
      withContext(Dispatchers.IO) {
        runCatching { response.body?.close() }
        if (!saved) {
          target?.delete()
          target?.parentFile?.delete()
        }
      }
      pendingPageDownloads.remove(key)
    }
  }
}
