package com.searchlauncher.app.ui.browser

import android.app.Application
import android.os.Looper
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ResumableDownloadsTest {
  private val app: Application = RuntimeEnvironment.getApplication()
  private val content = ByteArray(200_000) { (it % 251).toByte() }
  private val requests = CopyOnWriteArrayList<Map<String, String>>()

  private class FakeResponse(
    override val status: Int,
    private val headers: Map<String, String>,
    override val body: InputStream,
  ) : DownloadResponse {
    override fun header(name: String) =
      headers.entries.firstOrNull { it.key.equals(name, true) }?.value

    override fun close() = body.close()
  }

  /** Serves [bytes], then fails as if the connection dropped. */
  private class DroppingStream(private val bytes: ByteArray, private val dropAfter: Int) :
    InputStream() {
    private var position = 0

    override fun read(): Int = throw UnsupportedOperationException()

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
      if (position >= dropAfter) throw IOException("unexpected end of stream")
      val count = minOf(length, dropAfter - position, bytes.size - position)
      if (count <= 0) return -1
      System.arraycopy(bytes, position, buffer, offset, count)
      position += count
      return count
    }
  }

  @Before
  fun setUp() {
    ResumableDownloads.resetForTest()
    pendingPageDownloads.clear()
  }

  @After
  fun tearDown() {
    ResumableDownloads.fetcherForTest = null
    ResumableDownloads.resetForTest()
    pendingPageDownloads.clear()
  }

  private fun record(key: String) =
    ResumableRecord(
      key = key,
      url = "https://example.com/big.bin",
      name = "big.bin",
      mimeType = "application/octet-stream",
      path = ResumableDownloads.partialPath(app, key, "big.bin"),
      total = content.size.toLong(),
      validator = "\"v1\"",
    )

  /** Run the main looper (and so the download coroutines) until [done] or a timeout. */
  private fun runUntil(timeoutMs: Long = 20_000, done: () -> Boolean) {
    val deadline = System.currentTimeMillis() + timeoutMs
    while (!done()) {
      check(System.currentTimeMillis() < deadline) { "Timed out" }
      shadowOf(Looper.getMainLooper()).idleFor(250, TimeUnit.MILLISECONDS)
      Thread.sleep(5)
    }
  }

  private fun rangeResponse(headers: Map<String, String>): DownloadResponse {
    val start = headers["Range"]?.removePrefix("bytes=")?.removeSuffix("-")?.toInt() ?: 0
    val rest = content.copyOfRange(start, content.size)
    return if (start == 0) FakeResponse(200, mapOf(), ByteArrayInputStream(content))
    else
      FakeResponse(
        206,
        mapOf("Content-Range" to "bytes $start-${content.size - 1}/${content.size}"),
        ByteArrayInputStream(rest),
      )
  }

  @Test
  fun droppedConnectionContinuesWithARangeRequest() {
    ResumableDownloads.fetcherForTest = DownloadFetcher { _, headers ->
      requests += headers
      rangeResponse(headers)
    }
    val initial = FakeResponse(200, mapOf(), DroppingStream(content, 70_000))
    ResumableDownloads.start(app, record("drop"), initial)
    runUntil { pendingPageDownloads["drop"]?.completed != null }

    assertEquals("bytes=70000-", requests.single()["Range"])
    assertEquals("\"v1\"", requests.single()["If-Range"])
    val saved = File(ResumableDownloads.partialPath(app, "drop", "big.bin"))
    assertArrayEquals(content, saved.readBytes())
    assertTrue(ResumableDownloads.working.value.isEmpty())
  }

  @Test
  fun aServerThatIgnoresRangeRestartsFromZero() {
    ResumableDownloads.fetcherForTest = DownloadFetcher { _, headers ->
      requests += headers
      FakeResponse(200, mapOf(), ByteArrayInputStream(content))
    }
    ResumableDownloads.start(
      app,
      record("full"),
      FakeResponse(200, mapOf(), DroppingStream(content, 50_000)),
    )
    runUntil { pendingPageDownloads["full"]?.completed != null }
    assertArrayEquals(
      content,
      File(ResumableDownloads.partialPath(app, "full", "big.bin")).readBytes(),
    )
  }

  @Test
  fun pausedDownloadKeepsItsBytesAndResumesFromThem() {
    val gate = Object()
    var release = false
    ResumableDownloads.fetcherForTest = DownloadFetcher { _, headers ->
      requests += headers
      rangeResponse(headers)
    }
    // An initial stream that stalls after 40 000 bytes until released.
    val stalling =
      object : InputStream() {
        var position = 0

        override fun read(): Int = throw UnsupportedOperationException()

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
          if (position >= 40_000) {
            synchronized(gate) { while (!release) gate.wait(50) }
            throw IOException("closed")
          }
          val count = minOf(length, 40_000 - position)
          System.arraycopy(content, position, buffer, offset, count)
          position += count
          return count
        }

        override fun close() {
          synchronized(gate) {
            release = true
            gate.notifyAll()
          }
        }
      }
    ResumableDownloads.start(app, record("pause"), FakeResponse(200, mapOf(), stalling))
    val partial = File(ResumableDownloads.partialPath(app, "pause", "big.bin"))
    runUntil { partial.length() >= 40_000 }

    pendingPageDownloads.getValue("pause").resumable!!.onPause!!.invoke()
    runUntil { ResumableDownloads.working.value.isEmpty() }
    val paused = pendingPageDownloads.getValue("pause").resumable!!
    assertEquals(ResumableState.PAUSED, paused.state)
    assertNotNull(paused.onResume)
    assertEquals(40_000L, partial.length())

    paused.onResume!!.invoke()
    runUntil { pendingPageDownloads["pause"]?.completed != null }
    assertEquals("bytes=40000-", requests.last()["Range"])
    assertArrayEquals(content, partial.readBytes())
  }

  @Test
  fun aRefusedResumeFailsButKeepsThePartialFileForRetry() {
    ResumableDownloads.fetcherForTest = DownloadFetcher { _, headers ->
      requests += headers
      FakeResponse(404, mapOf(), ByteArrayInputStream(ByteArray(0)))
    }
    ResumableDownloads.start(
      app,
      record("gone"),
      FakeResponse(200, mapOf(), DroppingStream(content, 30_000)),
    )
    runUntil { pendingPageDownloads["gone"]?.resumable?.state == ResumableState.FAILED }
    val failed = pendingPageDownloads.getValue("gone").resumable!!
    assertEquals("the server refused it (HTTP 404)", failed.error)
    assertEquals(30_000L, File(ResumableDownloads.partialPath(app, "gone", "big.bin")).length())

    // Cancel (Delete on the card) removes the partial file and the row.
    pendingPageDownloads.getValue("gone").onCancel!!.invoke()
    runUntil { "gone" !in pendingPageDownloads }
    assertFalse(File(ResumableDownloads.partialPath(app, "gone", "big.bin")).exists())
  }

  @Test
  fun downloadsInterruptedByProcessDeathResumeOnNextStart() {
    ResumableDownloads.fetcherForTest = DownloadFetcher { _, headers ->
      requests += headers
      rangeResponse(headers)
    }
    // Simulate a previous process: a stored RUNNING record with 60 000 bytes on disk.
    val stored = record("restart").copy(state = ResumableState.RUNNING, bytes = 1)
    File(stored.path).apply {
      parentFile!!.mkdirs()
      writeBytes(content.copyOfRange(0, 60_000))
    }
    File(app.noBackupFilesDir, "resumable-downloads.json")
      .writeText(ResumableRecord.listToJson(listOf(stored)))

    ResumableDownloads.load(app)
    assertEquals(60_000L, pendingPageDownloads.getValue("restart").resumable!!.bytes)
    ResumableDownloads.resumeInterrupted(app)
    runUntil { pendingPageDownloads["restart"]?.completed != null }
    assertEquals("bytes=60000-", requests.single()["Range"])
    assertArrayEquals(content, File(stored.path).readBytes())
  }
}
