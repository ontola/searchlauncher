package com.searchlauncher.app.ui.browser

import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import org.junit.Assert.*
import org.junit.Test

class ResumableDownloadTest {
  @Test
  fun contentRangeParsesAllForms() {
    assertEquals(ContentRange(200, 999, 5000), parseContentRange("bytes 200-999/5000"))
    assertEquals(ContentRange(0, 9, null), parseContentRange("bytes 0-9/*"))
    assertEquals(ContentRange(-1, null, 5000), parseContentRange("bytes */5000"))
    assertNull(parseContentRange(null))
    assertNull(parseContentRange("bytes 9-1/5000"))
    assertNull(parseContentRange("items 0-9/10"))
    assertNull(parseContentRange("bytes */*"))
  }

  @Test
  fun partialResponseAtTheSavedOffsetAppends() {
    assertEquals(
      ResumeDecision.Append(5000),
      decideResume(1000, 5000, 206, "bytes 1000-4999/5000", 4000, null),
    )
    // No size known before: the Content-Range total tells it.
    assertEquals(
      ResumeDecision.Append(5000),
      decideResume(1000, null, 206, "bytes 1000-4999/5000", 4000, "identity"),
    )
  }

  @Test
  fun partialResponseThatDoesNotLineUpStartsOver() {
    assertEquals(
      ResumeDecision.Retry("the server resumed at the wrong place", fromZero = true),
      decideResume(1000, 5000, 206, "bytes 0-4999/5000", 5000, null),
    )
    assertEquals(
      ResumeDecision.Retry("the file changed on the server", fromZero = true),
      decideResume(1000, 5000, 206, "bytes 1000-5999/6000", 5000, null),
    )
    // Compressed bytes do not line up with the decompressed ones already saved.
    assertTrue(
      (decideResume(1000, 5000, 206, "bytes 1000-4999/5000", 4000, "gzip") as ResumeDecision.Retry)
        .fromZero
    )
  }

  @Test
  fun fullResponseRestartsAndOnlyTrustsLengthForIdentity() {
    assertEquals(ResumeDecision.Restart(5000), decideResume(1000, 5000, 200, null, 5000, null))
    assertEquals(ResumeDecision.Restart(null), decideResume(0, null, 200, null, 300, "gzip"))
  }

  @Test
  fun rangeNotSatisfiableMeansCompleteOnlyWhenSizesMatch() {
    assertEquals(ResumeDecision.Complete, decideResume(5000, 5000, 416, "bytes */5000", null, null))
    assertTrue(decideResume(4000, 5000, 416, "bytes */5000", null, null) is ResumeDecision.Retry)
  }

  @Test
  fun serverErrorsRetryButRefusalsStop() {
    for (status in listOf(408, 429, 500, 503)) assertTrue(
      decideResume(0, null, status, null, null, null) is ResumeDecision.Retry
    )
    for (status in listOf(401, 403, 404, 410)) assertEquals(
      ResumeDecision.Fail("the server refused it (HTTP $status)"),
      decideResume(0, null, status, null, null, null),
    )
  }

  @Test
  fun rangeHeadersCarryAStrongValidatorAndAskForIdentity() {
    val record =
      ResumableRecord(
        key = "k",
        url = "https://example.com/big.iso",
        name = "big.iso",
        mimeType = "application/octet-stream",
        path = "/tmp/big.iso",
        validator = resumeValidator("\"abc\"", "Mon, 01 Jan 2024 00:00:00 GMT"),
        referer = "https://example.com/",
        userAgent = "UA",
      )
    assertEquals(
      mapOf(
        "User-Agent" to "UA",
        "Referer" to "https://example.com/",
        "Range" to "bytes=1234-",
        "Accept-Encoding" to "identity",
        "If-Range" to "\"abc\"",
      ),
      resumeHeaders(record, 1234),
    )
    assertFalse("Range" in resumeHeaders(record, 0))
    assertFalse("Range" in resumeHeaders(record.copy(rangeSafe = false), 1234))
    // A weak ETag cannot guard a byte range; the date can.
    assertEquals("Tue", resumeValidator("W/\"abc\"", "Tue")?.take(3))
    assertNull(resumeValidator(null, " "))
  }

  @Test
  fun backoffDoublesUpToFiveMinutes() {
    assertEquals(2_000, retryDelayMillis(1))
    assertEquals(4_000, retryDelayMillis(2))
    assertEquals(16_000, retryDelayMillis(4))
    assertEquals(300_000, retryDelayMillis(MAX_DOWNLOAD_ATTEMPTS + 5))
  }

  @Test
  fun networkErrorsAreTransientButDiskErrorsAreNot() {
    assertTrue(isTransientDownloadError(SocketTimeoutException()))
    assertTrue(isTransientDownloadError(UnknownHostException("example.com")))
    assertTrue(isTransientDownloadError(IOException("unexpected end of stream")))
    assertFalse(isTransientDownloadError(IOException("write failed: ENOSPC (No space left)")))
    assertFalse(isTransientDownloadError(IllegalStateException("bug")))
    assertEquals("not enough storage", downloadErrorReason(IOException("ENOSPC")))
    assertEquals("the server stopped responding", downloadErrorReason(SocketTimeoutException()))
  }

  @Test
  fun recordsSurviveJson() {
    val record =
      ResumableRecord(
        key = "k",
        url = "https://example.com/a.zip",
        name = "a.zip",
        mimeType = "application/zip",
        path = "/x/a.zip",
        bytes = 10,
        total = 100,
        validator = "\"v\"",
        state = ResumableState.RETRYING,
        attempts = 2,
        error = "the connection dropped",
        retryAt = 99,
        rangeSafe = false,
        provisional = true,
        createdAt = 5,
      )
    assertEquals(
      listOf(record),
      ResumableRecord.listFromJson(ResumableRecord.listToJson(listOf(record))),
    )
    assertEquals(
      listOf(record.copy(total = null, validator = null, error = null)),
      ResumableRecord.listFromJson(
        ResumableRecord.listToJson(
          listOf(record.copy(total = null, validator = null, error = null))
        )
      ),
    )
    assertTrue(ResumableRecord.listFromJson("not json").isEmpty())
  }

  @Test
  fun aWebPageInsteadOfTheFileIsNoticed() {
    assertTrue(isPageInsteadOfFile("application/zip", "text/html; charset=utf-8"))
    assertFalse(isPageInsteadOfFile("text/html", "text/html"))
    assertFalse(isPageInsteadOfFile("application/zip", "application/zip"))
    assertFalse(isPageInsteadOfFile("application/zip", null))
  }

  @Test
  fun cardHeadlinesNameEachState() {
    fun status(state: ResumableState, error: String? = null, retryAt: Long = 0) =
      ResumableStatus(state, 380, 1000, error, retryAt)
    assertEquals("Downloading · 38%", resumableStatusText(status(ResumableState.RUNNING), 0))
    assertEquals(
      "Waiting for network · continues by itself",
      resumableStatusText(status(ResumableState.WAITING_FOR_NETWORK), 0),
    )
    assertEquals(
      "The connection dropped · retrying in 12 s",
      resumableStatusText(status(ResumableState.RETRYING, "the connection dropped", 12_000), 400),
    )
    assertEquals("Paused", resumableStatusText(status(ResumableState.PAUSED), 0))
    assertEquals(
      "Download failed: not enough storage",
      resumableStatusText(status(ResumableState.FAILED, "not enough storage"), 0),
    )
  }
}
