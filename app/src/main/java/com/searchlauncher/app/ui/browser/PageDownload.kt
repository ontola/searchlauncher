package com.searchlauncher.app.ui.browser

import android.app.DownloadManager
import android.content.Context
import android.os.Environment
import android.util.Base64
import android.webkit.URLUtil
import android.webkit.WebView
import android.widget.Toast
import java.io.File
import java.util.UUID
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.json.JSONTokener

internal fun isPageDownload(url: String): Boolean =
  url.startsWith("blob:", ignoreCase = true) || url.startsWith("data:", ignoreCase = true)

/**
 * The filename a `Content-Disposition` header names, preferring the RFC 5987 `filename*` form.
 * [URLUtil.guessFileName] only understands a lone `attachment; filename="…"`, so a header that also
 * carries `filename*` (or says `inline`) falls through to the URL, whose last segment often has no
 * extension, and the file is saved as `something.bin`.
 */
internal fun contentDispositionFileName(disposition: String?): String? {
  if (disposition.isNullOrBlank()) return null
  val params =
    disposition.split(';').drop(1).mapNotNull { part ->
      val eq = part.indexOf('=')
      if (eq < 0) null
      else part.substring(0, eq).trim().lowercase() to part.substring(eq + 1).trim()
    }
  params
    .firstOrNull { it.first == "filename*" }
    ?.second
    ?.let { value ->
      val quote = value.indexOf('\'')
      val second = if (quote < 0) -1 else value.indexOf('\'', quote + 1)
      if (second < 0) return@let null
      val charset = value.substring(0, quote).ifBlank { "UTF-8" }
      runCatching {
          java.net.URLDecoder.decode(value.substring(second + 1).replace("+", "%2B"), charset)
        }
        .getOrNull()
    }
    ?.takeIf { it.isNotBlank() }
    ?.let {
      return it
    }
  return params
    .firstOrNull { it.first == "filename" }
    ?.second
    ?.removeSurrounding("\"")
    ?.takeIf { it.isNotBlank() }
}

/**
 * The name to save a download under: the server's, else a guess from the URL and type. A generic
 * type is left out of the guess, because [URLUtil.guessFileName] swaps any extension that does not
 * match the type for the type's own: `photo.jpg` sent as `application/octet-stream` became
 * `photo.bin`.
 */
internal fun downloadFileName(url: String, disposition: String?, mimeType: String?): String =
  safePageDownloadName(
    contentDispositionFileName(disposition)
      ?: URLUtil.guessFileName(
        url,
        disposition,
        mimeType?.takeUnless { it.isBlank() || it == "application/octet-stream" },
      )
  )

internal fun safePageDownloadName(name: String): String =
  name
    .substringAfterLast('/')
    .substringAfterLast('\\')
    .replace(Regex("[\\p{Cntrl}]"), "_")
    .take(160)
    .takeUnless { it.isBlank() || it == "." || it == ".." } ?: "download"

internal data class PageDownloadProgress(
  val name: String,
  val fraction: Float?,
  val startedAt: Long = System.currentTimeMillis(),
  val completed: BrowserDownload? = null,
  val seenInHistory: Boolean = false,
)

internal val pendingPageDownloads =
  androidx.compose.runtime.mutableStateMapOf<String, PageDownloadProgress>()

private const val MAX_PAGE_DOWNLOAD_BYTES = 100L * 1024 * 1024
private const val PAGE_DOWNLOAD_CHUNK_BYTES = 48 * 1024

private suspend fun WebView.scriptResult(script: String): String =
  withTimeout(30_000) {
    suspendCancellableCoroutine { continuation ->
      evaluateJavascript(script) { result ->
        if (continuation.isActive) continuation.resume(result ?: "null")
      }
    }
  }

/**
 * Pull bounded chunks from the initiating page; no native interface is exposed to page scripts.
 *
 * Also used for images on http(s), which the page already has and can read back with its own
 * session and referrer. Refetching those through DownloadManager is what failed on sites that only
 * serve a file to the page that shows it. With [quiet], a failure shows nothing so the caller can
 * fall back; the result says whether the file was saved.
 */
@Suppress("DEPRECATION")
internal suspend fun downloadFromPage(
  view: WebView,
  url: String,
  disposition: String?,
  mimeType: String?,
  quiet: Boolean = false,
): Boolean {
  val context = view.context
  val key = "__searchlauncher_download_" + UUID.randomUUID().toString().replace("-", "")
  val quotedKey = JSONObject.quote(key)
  val quotedUrl = JSONObject.quote(url)
  var file: File? = null
  var registered = false
  val fromPage = isPageDownload(url)
  pendingPageDownloads[key] =
    PageDownloadProgress(if (fromPage) "Preparing export…" else "Preparing download…", null)
  val notification = DownloadNotification(context, key, pendingPageDownloads.getValue(key).name)
  if (!quiet) Toast.makeText(context, "Preparing download…", Toast.LENGTH_SHORT).show()
  try {
    check(fromPage || url.startsWith("https://") || url.startsWith("http://"))
    view.scriptResult(
      """
      (() => {
        const state = window[$quotedKey] = {ready:false};
        fetch($quotedUrl, {cache: 'force-cache'}).then(r => {
          if (!r.ok) throw new Error(r.status);
          return r.blob();
        }).then(blob => {
          state.blob = blob;
          state.size = blob.size;
          state.type = blob.type;
          const link = Array.from(document.querySelectorAll('a[download]'))
            .find(a => a.href === $quotedUrl);
          state.name = link ? link.download : '';
          state.ready = true;
        }).catch(() => { state.error = true; });
      })()
      """
        .trimIndent()
    )
    suspend fun awaitState(): JSONObject =
      withTimeout(30_000) {
        while (true) {
          val raw =
            view.scriptResult(
              "JSON.stringify(window[$quotedKey] && {ready:window[$quotedKey].ready," +
                "error:window[$quotedKey].error,size:window[$quotedKey].size," +
                "type:window[$quotedKey].type,name:window[$quotedKey].name," +
                "chunk:window[$quotedKey].chunk})"
            )
          val decoded = JSONTokener(raw).nextValue()
          check(decoded is String) { "The page changed before the download finished" }
          val state = JSONObject(decoded)
          check(!state.optBoolean("error")) { "The page could not read this export" }
          if (state.optBoolean("ready")) return@withTimeout state
          delay(25)
        }
        @Suppress("UNREACHABLE_CODE") error("Unreachable")
      }
    val metadata = awaitState()
    val size = metadata.getLong("size")
    check(size in 0..MAX_PAGE_DOWNLOAD_BYTES) { "Exports larger than 100 MB are not supported yet" }
    val type =
      mimeType?.takeIf { it.isNotBlank() }
        ?: metadata.optString("type").takeIf { it.isNotBlank() }
        ?: "application/octet-stream"
    val name =
      safePageDownloadName(
        metadata.optString("name").takeIf { it.isNotBlank() }
          ?: downloadFileName(
            if (fromPage) "https://download.invalid/download" else url,
            disposition,
            type,
          )
      )
    // DownloadManager accepts app-owned external paths on scoped-storage Android. Register the
    // completed file so existing history, open, share and delete actions all work for JS exports.
    val target =
      withContext(Dispatchers.IO) {
        val root = requireNotNull(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS))
        val directory = File(root, UUID.randomUUID().toString())
        check(directory.mkdirs())
        File(directory, name)
      }
    file = target
    withContext(Dispatchers.IO) { target.writeBytes(byteArrayOf()) }
    var offset = 0L
    while (offset < size) {
      val end = minOf(offset + PAGE_DOWNLOAD_CHUNK_BYTES, size)
      view.scriptResult(
        """
        (() => {
          const s = window[$quotedKey];
          if (!s) return;
          s.ready = false; s.chunk = null;
          const reader = new FileReader();
          reader.onload = () => { s.chunk = reader.result.split(',')[1]; s.ready = true; };
          reader.onerror = () => { s.error = true; };
          reader.readAsDataURL(s.blob.slice($offset, $end));
        })()
        """
          .trimIndent()
      )
      val chunk = awaitState().getString("chunk")
      check(chunk.length <= PAGE_DOWNLOAD_CHUNK_BYTES * 2)
      val bytes = Base64.decode(chunk, Base64.DEFAULT)
      check(bytes.size.toLong() == end - offset)
      withContext(Dispatchers.IO) { target.appendBytes(bytes) }
      offset = end
      updatePageDownload(key, name, offset.toFloat() / size)
      notification.progress(name, offset.toFloat() / size)
    }
    val id =
      withContext(Dispatchers.IO) {
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        manager.addCompletedDownload(
          name,
          "Exported from a webpage",
          false,
          type,
          target.path,
          size,
          false,
        )
      }
    registered = true
    completePageDownload(key, id, name, size)
    notification.complete(name)
    Toast.makeText(context, "Downloaded $name", Toast.LENGTH_LONG).show()
  } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
    if (!quiet)
      Toast.makeText(
          context,
          "Export timed out. Keep the source tab open and try again.",
          Toast.LENGTH_LONG,
        )
        .show()
  } catch (cancelled: kotlinx.coroutines.CancellationException) {
    throw cancelled
  } catch (error: Exception) {
    if (!quiet)
      Toast.makeText(context, error.message ?: "Could not save this export", Toast.LENGTH_LONG)
        .show()
  } finally {
    withContext(NonCancellable + Dispatchers.Main) {
      if (!registered) notification.failed()
      finishPageDownload(key)
      // No waiting: a destroyed/navigated WebView may no longer invoke evaluation callbacks.
      runCatching { view.evaluateJavascript("delete window[$quotedKey]", null) }
    }
    if (!registered)
      withContext(NonCancellable + Dispatchers.IO) {
        file?.let {
          it.delete()
          it.parentFile?.delete()
        }
      }
  }
  return registered
}
