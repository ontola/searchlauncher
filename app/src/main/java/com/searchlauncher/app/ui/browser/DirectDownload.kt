package com.searchlauncher.app.ui.browser

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.webkit.CookieManager
import android.widget.Toast
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Hosts whose download links only work once. The WebView spends that one use finding out the link
 * is a download, so the usual DownloadManager hand-off asks again and gets a 404 (WeTransfer).
 * Links to these hosts are taken before the WebView requests them and fetched exactly once, here.
 */
private val SINGLE_USE_DOWNLOAD_HOSTS = setOf("download.wetransfer.com")

internal fun isSingleUseDownload(uri: Uri): Boolean =
  uri.scheme == "https" && uri.host?.lowercase() in SINGLE_USE_DOWNLOAD_HOSTS

/** Outlives the browser screen, so leaving the browser does not abort a large transfer. */
private val directDownloadScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

/**
 * Fetch [url] once with the page's session and save it next to page exports, registered with
 * DownloadManager so the Downloads screen can open, share and delete it like any other download.
 */
@Suppress("DEPRECATION")
internal fun startDirectDownload(
  context: Context,
  url: String,
  userAgent: String?,
  referer: String?,
) {
  val appContext = context.applicationContext
  val key = "__searchlauncher_direct_" + UUID.randomUUID().toString().replace("-", "")
  pendingPageDownloads[key] = PageDownloadProgress("Preparing download…", null)
  Toast.makeText(appContext, "Preparing download…", Toast.LENGTH_SHORT).show()
  directDownloadScope.launch {
    var file: File? = null
    var registered = false
    try {
      val saved =
        withContext(Dispatchers.IO) {
          val connection = URL(url).openConnection() as HttpURLConnection
          try {
            connection.instanceFollowRedirects = true
            connection.connectTimeout = 30_000
            connection.readTimeout = 60_000
            CookieManager.getInstance().getCookie(url)?.let {
              connection.setRequestProperty("Cookie", it)
            }
            if (!userAgent.isNullOrBlank()) connection.setRequestProperty("User-Agent", userAgent)
            if (
              referer != null && (referer.startsWith("https://") || referer.startsWith("http://"))
            )
              connection.setRequestProperty("Referer", referer)
            val code = connection.responseCode
            check(code in 200..299) { "Download failed: the server refused it (HTTP $code)" }
            val type =
              connection.contentType?.substringBefore(';')?.trim()?.takeIf { it.isNotBlank() }
                ?: "application/octet-stream"
            val name =
              downloadFileName(
                connection.url.toString(),
                connection.getHeaderField("Content-Disposition"),
                type,
              )
            val root =
              requireNotNull(appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS))
            val directory = File(root, UUID.randomUUID().toString())
            check(directory.mkdirs())
            val target = File(directory, name)
            file = target
            val total = connection.contentLengthLong
            var written = 0L
            var reported = 0L
            connection.inputStream.use { input ->
              target.outputStream().use { output ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                  val read = input.read(buffer)
                  if (read < 0) break
                  output.write(buffer, 0, read)
                  written += read
                  if (written - reported >= 512 * 1024) {
                    reported = written
                    val fraction = if (total > 0) written.toFloat() / total else null
                    withContext(Dispatchers.Main) { updatePageDownload(key, name, fraction) }
                  }
                }
              }
            }
            check(total < 0 || written == total) { "Download failed: the connection dropped" }
            val manager = appContext.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val id =
              manager.addCompletedDownload(
                name,
                Uri.parse(url).host ?: "Download",
                false,
                type,
                target.path,
                written,
                true,
              )
            Triple(id, name, written)
          } finally {
            connection.disconnect()
          }
        }
      registered = true
      completePageDownload(key, saved.first, saved.second, saved.third)
      Toast.makeText(appContext, "Downloaded ${saved.second}", Toast.LENGTH_LONG).show()
    } catch (error: Exception) {
      Toast.makeText(appContext, error.message ?: "Download failed", Toast.LENGTH_LONG).show()
    } finally {
      finishPageDownload(key)
      if (!registered)
        withContext(Dispatchers.IO) {
          file?.let {
            it.delete()
            it.parentFile?.delete()
          }
        }
    }
  }
}
