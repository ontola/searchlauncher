package com.searchlauncher.app.data

import androidx.test.core.app.ApplicationProvider
import com.searchlauncher.app.SearchLauncherApp
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = SearchLauncherApp::class)
class SearchProgressiveResultsTest {
  @Test
  fun `activated youtube with empty term is first in every update`() = runBlocking {
    val app = ApplicationProvider.getApplicationContext<SearchLauncherApp>()
    val repository = SearchRepository(app)
    val updates = repository.searchAppUpdates("y ", includeSuggestions = false).toList()
    assertTrue(updates.isNotEmpty())
    updates.forEach { results ->
      assertEquals("Search in YouTube", results.first().title)
      assertEquals("shortcut_y", results.first().id)
      assertEquals(
        "https://www.youtube.com/results?search_query=",
        (results.first() as SearchResult.Content).deepLink,
      )
    }
  }

  private suspend fun withGatedSuggestions(
    responseStatus: Int = 200,
    block: suspend (SearchRepository, CountDownLatch) -> Unit,
  ) {
    val app = ApplicationProvider.getApplicationContext<SearchLauncherApp>()
    val repository = SearchRepository(app)
    val releaseResponse = CountDownLatch(1)
    ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { server ->
      server.soTimeout = 2000
      val worker =
        thread(isDaemon = true) {
          try {
            server.accept().use { socket ->
              socket.soTimeout = 2000
              val input = socket.getInputStream().bufferedReader()
              while (!input.readLine().isNullOrEmpty()) {}
              if (!releaseResponse.await(2, TimeUnit.SECONDS)) return@thread
              val body = "[\"cats\",[\"cats pictures\"]]"
              socket
                .getOutputStream()
                .write(
                  ("HTTP/1.1 $responseStatus Test\r\nContent-Length: ${body.toByteArray().size}\r\nConnection: close\r\n\r\n$body")
                    .toByteArray()
                )
            }
          } catch (_: java.io.IOException) {
            // Cancellation may close the fixture before the request connects.
          }
        }
      app.searchShortcutRepository.addShortcut(
        SearchShortcut(
          id = "progressive_test",
          alias = "progressive",
          description = "Progressive test",
          urlTemplate = "https://example.com/?q=%s",
          suggestionUrl = "http://localhost:${server.localPort}/?q=%s",
        )
      )
      try {
        block(repository, releaseResponse)
      } finally {
        releaseResponse.countDown()
        server.close()
        worker.join(2500)
        app.searchShortcutRepository.removeShortcut("progressive_test")
      }
    }
  }

  @Test
  fun `local results arrive before server may respond then suggestions are merged`() = runBlocking {
    withGatedSuggestions { repository, release ->
      val updates = mutableListOf<List<SearchResult>>()
      repository.searchAppUpdates("progressive cats").collect { results ->
        updates.add(results)
        if (updates.size == 1) {
          assertTrue(results.any { it.id == "shortcut_progressive" })
          assertFalse(results.any { it.id.startsWith("suggestion_") })
          release.countDown()
        }
      }
      assertEquals(2, updates.size)
      assertTrue(updates.last().any { it.id == "suggestion_progressive_cats pictures" })
      assertTrue(updates.last().containsAll(updates.first()))
      assertEquals(updates.last().sortedByDescending { it.rankingScore }, updates.last())
      assertFalse(updates.first().any { it.id.startsWith("suggestion_") })
    }
  }

  @Test
  fun `failed suggestions keep the already published local results`() = runBlocking {
    withGatedSuggestions(responseStatus = 500) { repository, release ->
      val updates = mutableListOf<List<SearchResult>>()
      repository.searchAppUpdates("progressive cats").collect {
        updates.add(it)
        release.countDown()
      }
      assertEquals(1, updates.size)
      assertTrue(updates.single().any { it.id == "shortcut_progressive" })
    }
  }

  @Test
  fun `cancelled collection cannot deliver a late suggestion update`() = runBlocking {
    withGatedSuggestions { repository, release ->
      val updates = repository.searchAppUpdates("progressive cats").take(1).toList()
      release.countDown()
      assertEquals(1, updates.size)
      assertFalse(updates.single().any { it.id.startsWith("suggestion_") })
    }
  }

  @Test
  fun `disabled suggestions and empty queries emit once`() = runBlocking {
    val app = ApplicationProvider.getApplicationContext<SearchLauncherApp>()
    val repository = SearchRepository(app)
    assertEquals(listOf(emptyList<SearchResult>()), repository.searchAppUpdates("").toList())
    val updates = repository.searchAppUpdates("g cats", includeSuggestions = false).toList()
    assertEquals(1, updates.size)
    assertFalse(updates.single().any { it.id.startsWith("suggestion_") })
  }
}
