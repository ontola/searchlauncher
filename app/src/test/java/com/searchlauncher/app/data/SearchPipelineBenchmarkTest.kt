package com.searchlauncher.app.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.searchlauncher.app.SearchLauncherApp
import java.net.ServerSocket
import kotlin.concurrent.thread
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = SearchLauncherApp::class)
class SearchPipelineBenchmarkTest {
  @Test
  fun measureLimitedShortcuts() = runBlocking {
    assumeTrue(System.getenv("SEARCHLAUNCHER_BENCHMARK") == "1")
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repository = SearchRepository(context)
    for (count in listOf(20, 200)) {
      repository.documentSnapshot =
        (0 until count).map { i ->
          repository.wrap(
            AppSearchDocument(
              namespace = "search_shortcuts",
              id = "bench_$i",
              score = 3,
              name = "Engine ${i.toString().padStart(3, '0')}",
              description = "bench$i",
              intentUri = "https://example.com/?q=%s",
            )
          )
        }
      repeat(20) { repository.getSearchShortcuts(3) }
      val timings =
        LongArray(40) {
            val start = System.nanoTime()
            val results = repository.getSearchShortcuts(3)
            check(results.size == 3)
            System.nanoTime() - start
          }
          .sorted()
      println(
        "PIPELINE shortcuts=$count limit=3 p50_ms=${timings[19] / 1e6} p95_ms=${timings[37] / 1e6}"
      )
    }
  }

  @Test
  fun measureSlowSuggestions() = runBlocking {
    assumeTrue(System.getenv("SEARCHLAUNCHER_BENCHMARK") == "1")
    val app = ApplicationProvider.getApplicationContext<SearchLauncherApp>()
    val repository = SearchRepository(app)
    ServerSocket(0, 1, java.net.InetAddress.getLoopbackAddress()).use { server ->
      val worker =
        thread(isDaemon = true) {
          server.accept().use { socket ->
            val input = socket.getInputStream().bufferedReader()
            while (!input.readLine().isNullOrEmpty()) {}
            Thread.sleep(300)
            val body = "[\"cats\",[\"cats pictures\"]]"
            val reply =
              "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${body.toByteArray().size}\r\nConnection: close\r\n\r\n$body"
            socket.getOutputStream().write(reply.toByteArray())
          }
        }
      val shortcut =
        SearchShortcut(
          id = "perf_test",
          alias = "perf",
          description = "Performance test",
          urlTemplate = "https://example.com/?q=%s",
          suggestionUrl = "http://localhost:${server.localPort}/?q=%s",
        )
      app.searchShortcutRepository.addShortcut(shortcut)
      try {
        repository.searchApps("perf cats", includeSuggestions = false).getOrThrow()
        val start = System.nanoTime()
        var firstResultsMs = 0.0
        var results = emptyList<SearchResult>()
        repository.searchAppUpdates("perf cats", includeSuggestions = true).collect { update ->
          if (results.isEmpty()) firstResultsMs = (System.nanoTime() - start) / 1e6
          results = update
        }
        val elapsed = (System.nanoTime() - start) / 1e6
        assertTrue(results.any { it.id.startsWith("suggestion_") })
        println(
          "PIPELINE suggestion_delay_ms=300 first_results_ms=$firstResultsMs final_results_ms=$elapsed"
        )
      } finally {
        worker.join(2000)
        app.searchShortcutRepository.removeShortcut(shortcut.id)
      }
    }
  }
}
