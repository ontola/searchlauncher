package com.searchlauncher.app.ui.browser

import android.content.Context
import android.net.Uri
import android.widget.Toast

/**
 * Hosts whose download links only work once. The WebView spends that one use finding out the link
 * is a download, so the usual DownloadManager hand-off asks again and gets a 404 (WeTransfer).
 * Links to these hosts are taken before the WebView requests them and fetched exactly once, here.
 */
private val SINGLE_USE_DOWNLOAD_HOSTS = setOf("download.wetransfer.com")

internal fun isSingleUseDownload(uri: Uri): Boolean =
  uri.scheme == "https" && uri.host?.lowercase() in SINGLE_USE_DOWNLOAD_HOSTS

/**
 * Fetch [url] with the page's session and save it next to page exports, registered with
 * DownloadManager so the Downloads screen can open, share and delete it like any other download.
 * [ResumableDownloads] keeps it going in the background and continues it after a dropped
 * connection.
 */
internal fun startDirectDownload(
  context: Context,
  url: String,
  userAgent: String?,
  referer: String?,
) {
  val key = ResumableDownloads.newKey()
  val placeholder = "Preparing download…"
  val path =
    try {
      ResumableDownloads.partialPath(context, key, "download")
    } catch (_: Exception) {
      Toast.makeText(context, "Download failed: no storage available", Toast.LENGTH_LONG).show()
      return
    }
  Toast.makeText(context.applicationContext, placeholder, Toast.LENGTH_SHORT).show()
  ResumableDownloads.start(
    context,
    ResumableRecord(
      key = key,
      url = url,
      name = placeholder,
      mimeType = "application/octet-stream",
      path = path,
      referer = referer,
      userAgent = userAgent,
      provisional = true,
    ),
  )
}
