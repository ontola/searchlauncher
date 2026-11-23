package com.searchlauncher.app.ui.browser

import android.content.Context
import android.webkit.CookieManager
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A plain HTTP response as a [DownloadResponse]. */
private class HttpDownloadResponse(private val connection: HttpURLConnection) : DownloadResponse {
  override val status: Int = connection.responseCode

  override fun header(name: String): String? = connection.getHeaderField(name)

  override val body: InputStream by lazy { connection.inputStream }

  override fun close() = connection.disconnect()
}

/** WebView builds refetch with HttpURLConnection and the WebView's cookies. */
internal object DownloadEngine {
  fun fetcher(@Suppress("UNUSED_PARAMETER") context: Context): DownloadFetcher =
    DownloadFetcher { url, headers ->
      withContext(Dispatchers.IO) {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
          connection.instanceFollowRedirects = true
          connection.connectTimeout = 30_000
          connection.readTimeout = 60_000
          // Without this HttpURLConnection asks for gzip and decodes it, so byte offsets would
          // not match what the server counts.
          connection.setRequestProperty("Accept-Encoding", "identity")
          runCatching { CookieManager.getInstance().getCookie(url) }
            .getOrNull()
            ?.let { connection.setRequestProperty("Cookie", it) }
          for ((name, value) in headers) connection.setRequestProperty(name, value)
          HttpDownloadResponse(connection)
        } catch (error: Exception) {
          connection.disconnect()
          throw error
        }
      }
    }
}
