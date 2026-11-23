package com.searchlauncher.app.ui.browser

import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.net.ConnectException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import org.json.JSONArray
import org.json.JSONObject

/**
 * Where a download stands. Every state but [COMPLETE] keeps the partial file, so the transfer can
 * continue from the bytes already saved instead of starting over.
 */
internal enum class ResumableState {
  QUEUED,
  RUNNING,
  WAITING_FOR_NETWORK,
  RETRYING,
  PAUSED,
  FAILED;

  /** States the download service keeps working on without the user doing anything. */
  val working: Boolean
    get() = this == QUEUED || this == RUNNING || this == WAITING_FOR_NETWORK || this == RETRYING
}

/**
 * A download the app transfers itself (Gecko responses and single-use links), persisted so it
 * survives network loss, the browser closing, and the app process being killed.
 */
internal data class ResumableRecord(
  val key: String,
  val url: String,
  val name: String,
  val mimeType: String,
  /** The partial file. Its length is the authoritative count of bytes saved so far. */
  val path: String,
  val bytes: Long = 0,
  val total: Long? = null,
  /** A strong ETag or Last-Modified date, sent as If-Range so a changed file is never spliced. */
  val validator: String? = null,
  val referer: String? = null,
  val userAgent: String? = null,
  val state: ResumableState = ResumableState.QUEUED,
  /** Consecutive failed attempts without new bytes. Progress resets it. */
  val attempts: Int = 0,
  val error: String? = null,
  /** When the next automatic attempt starts (wall clock), while [state] is RETRYING. */
  val retryAt: Long = 0,
  /**
   * Whether the bytes on disk match the server's byte offsets. A response Gecko decompressed
   * (Content-Encoding: gzip) does not, so it can only be restarted from zero.
   */
  val rangeSafe: Boolean = true,
  /** Only http(s) URLs can be requested again; blob:, data: and POST results cannot. */
  val refetchable: Boolean = true,
  /** [name] and [mimeType] are placeholders until the first response names the file. */
  val provisional: Boolean = false,
  val createdAt: Long = System.currentTimeMillis(),
) {
  val fraction: Float?
    get() = total?.takeIf { it > 0 }?.let { (bytes.toDouble() / it).toFloat().coerceIn(0f, 1f) }

  fun toJson(): JSONObject =
    JSONObject()
      .put("key", key)
      .put("url", url)
      .put("name", name)
      .put("mimeType", mimeType)
      .put("path", path)
      .put("bytes", bytes)
      .put("total", total ?: -1)
      .put("validator", validator ?: JSONObject.NULL)
      .put("referer", referer ?: JSONObject.NULL)
      .put("userAgent", userAgent ?: JSONObject.NULL)
      .put("state", state.name)
      .put("attempts", attempts)
      .put("error", error ?: JSONObject.NULL)
      .put("retryAt", retryAt)
      .put("rangeSafe", rangeSafe)
      .put("refetchable", refetchable)
      .put("provisional", provisional)
      .put("createdAt", createdAt)

  companion object {
    fun fromJson(json: JSONObject): ResumableRecord? =
      runCatching {
          fun optString(name: String) =
            if (json.isNull(name)) null else json.optString(name).takeIf { it.isNotEmpty() }
          ResumableRecord(
            key = json.getString("key"),
            url = json.getString("url"),
            name = json.getString("name"),
            mimeType = json.optString("mimeType", "application/octet-stream"),
            path = json.getString("path"),
            bytes = json.optLong("bytes"),
            total = json.optLong("total", -1).takeIf { it >= 0 },
            validator = optString("validator"),
            referer = optString("referer"),
            userAgent = optString("userAgent"),
            state =
              runCatching { ResumableState.valueOf(json.getString("state")) }
                .getOrDefault(ResumableState.PAUSED),
            attempts = json.optInt("attempts"),
            error = optString("error"),
            retryAt = json.optLong("retryAt"),
            rangeSafe = json.optBoolean("rangeSafe", true),
            refetchable = json.optBoolean("refetchable", true),
            provisional = json.optBoolean("provisional", false),
            createdAt = json.optLong("createdAt", System.currentTimeMillis()),
          )
        }
        .getOrNull()

    fun listToJson(records: Collection<ResumableRecord>): String =
      JSONArray().apply { records.forEach { put(it.toJson()) } }.toString()

    fun listFromJson(text: String): List<ResumableRecord> =
      runCatching {
          val array = JSONArray(text)
          (0 until array.length()).mapNotNull { fromJson(array.getJSONObject(it)) }
        }
        .getOrDefault(emptyList())
  }
}

/** One HTTP response for a download, from whichever engine fetched it. */
internal interface DownloadResponse : Closeable {
  val status: Int

  fun header(name: String): String?

  val body: InputStream

  /**
   * Abort a read that is blocked on the network. Must be safe to call from any thread, including
   * while another thread is reading [body].
   */
  fun abort() = close()
}

/** Requests a download's URL again, with the browser session's cookies. */
internal fun interface DownloadFetcher {
  suspend fun fetch(url: String, headers: Map<String, String>): DownloadResponse
}

internal data class ContentRange(val start: Long, val end: Long?, val total: Long?)

/** Parses `bytes 200-1000/67589`, `bytes 200-1000/*` and `bytes */67589`. */
internal fun parseContentRange(header: String?): ContentRange? {
  val value = header?.trim() ?: return null
  if (!value.startsWith("bytes", ignoreCase = true)) return null
  val spec = value.substring(5).trim()
  val slash = spec.indexOf('/')
  if (slash < 0) return null
  val total = spec.substring(slash + 1).trim().let { if (it == "*") null else it.toLongOrNull() }
  val range = spec.substring(0, slash).trim()
  if (range == "*") return ContentRange(-1, null, total ?: return null)
  val dash = range.indexOf('-')
  if (dash < 0) return null
  val start = range.substring(0, dash).trim().toLongOrNull() ?: return null
  val end = range.substring(dash + 1).trim().toLongOrNull() ?: return null
  if (start < 0 || end < start) return null
  return ContentRange(start, end, total)
}

/** What to do with a response to a (possibly ranged) request. */
internal sealed interface ResumeDecision {
  /** Append the body to the [offset] bytes already saved; [total] is the full size if known. */
  data class Append(val total: Long?) : ResumeDecision

  /** The body is the whole file from byte zero: truncate the partial file first. */
  data class Restart(val total: Long?) : ResumeDecision

  /** The saved bytes already are the whole file. */
  data object Complete : ResumeDecision

  /** Retry the request, after a delay, without a Range header if [fromZero]. */
  data class Retry(val reason: String, val fromZero: Boolean = false) : ResumeDecision

  /** Do not retry: the server refused the request for good. */
  data class Fail(val reason: String) : ResumeDecision
}

private fun encodingIsIdentity(encoding: String?) =
  encoding.isNullOrBlank() || encoding.trim().equals("identity", ignoreCase = true)

/**
 * Decide how a response continues a download that already has [offset] bytes saved. [expectedTotal]
 * is the size an earlier response reported, used to notice a file that changed on the server when
 * there is no validator to send as If-Range.
 */
internal fun decideResume(
  offset: Long,
  expectedTotal: Long?,
  status: Int,
  contentRange: String?,
  contentLength: Long?,
  contentEncoding: String?,
): ResumeDecision {
  val identity = encodingIsIdentity(contentEncoding)
  return when {
    status == 206 -> {
      val range = parseContentRange(contentRange)
      when {
        range == null || !identity -> ResumeDecision.Retry("unexpected partial response", true)
        range.start != offset -> ResumeDecision.Retry("the server resumed at the wrong place", true)
        expectedTotal != null && range.total != null && range.total != expectedTotal ->
          ResumeDecision.Retry("the file changed on the server", true)
        else -> ResumeDecision.Append(range.total ?: expectedTotal)
      }
    }
    status == 416 && offset > 0 -> {
      val range = parseContentRange(contentRange)
      if (range?.total != null && range.total == offset) ResumeDecision.Complete
      else ResumeDecision.Retry("the server could not resume", true)
    }
    status in 200..299 -> ResumeDecision.Restart(contentLength?.takeIf { it >= 0 && identity })
    status == 408 || status == 425 || status == 429 || status in 500..599 ->
      ResumeDecision.Retry("the server is busy (HTTP $status)")
    status in 400..499 -> ResumeDecision.Fail("the server refused it (HTTP $status)")
    else -> ResumeDecision.Fail("unexpected server response (HTTP $status)")
  }
}

/** A strong ETag, else Last-Modified: the value If-Range needs to refuse a changed file. */
internal fun resumeValidator(etag: String?, lastModified: String?): String? =
  etag?.trim()?.takeIf { it.isNotEmpty() && !it.startsWith("W/") }
    ?: lastModified?.trim()?.takeIf { it.isNotEmpty() }

/** Headers for the next attempt at [record], starting at [offset] bytes. */
internal fun resumeHeaders(record: ResumableRecord, offset: Long): Map<String, String> = buildMap {
  record.userAgent?.takeIf { it.isNotBlank() }?.let { put("User-Agent", it) }
  record.referer
    ?.takeIf { it.startsWith("https://") || it.startsWith("http://") }
    ?.let { put("Referer", it) }
  if (offset > 0 && record.rangeSafe) {
    put("Range", "bytes=$offset-")
    // Byte offsets only line up with an uncompressed body.
    put("Accept-Encoding", "identity")
    record.validator?.let { put("If-Range", it) }
  }
}

internal const val MAX_DOWNLOAD_ATTEMPTS = 8

/** 2 s, 4 s, 8 s … capped at five minutes. [attempt] counts from 1. */
internal fun retryDelayMillis(attempt: Int): Long =
  (2_000L shl (attempt - 1).coerceIn(0, 20)).coerceAtMost(5 * 60_000L)

/** Network errors that a later attempt can get past; anything else is a bug or a full disk. */
internal fun isTransientDownloadError(error: Throwable): Boolean =
  when (error) {
    is SocketTimeoutException,
    is UnknownHostException,
    is ConnectException,
    is SocketException,
    is SSLException,
    is java.io.EOFException,
    is java.net.ProtocolException -> true
    is IOException ->
      // Disk errors must not loop: ENOSPC and EACCES come through as plain IOException.
      error.message?.let { message ->
        !message.contains("ENOSPC", true) &&
          !message.contains("No space", true) &&
          !message.contains("EACCES", true) &&
          !message.contains("Permission denied", true) &&
          !message.contains("EROFS", true)
      } ?: true
    else -> false
  }

/** The words a download card shows for a non-transient error. */
internal fun downloadErrorReason(error: Throwable): String {
  val message = error.message.orEmpty()
  return when {
    message.contains("ENOSPC", true) || message.contains("No space", true) -> "not enough storage"
    message.contains("EACCES", true) ||
      message.contains("EROFS", true) ||
      message.contains("Permission denied", true) -> "the file could not be written"
    error is UnknownHostException -> "no connection to the server"
    error is SocketTimeoutException -> "the server stopped responding"
    error is IOException -> "the connection dropped"
    else -> message.ifBlank { "unexpected error" }
  }
}
