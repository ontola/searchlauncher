package com.searchlauncher.app.ui.browser

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.searchlauncher.app.SearchLauncherApp
import com.searchlauncher.app.data.FavoriteKeys
import com.searchlauncher.app.data.IconRepository
import com.searchlauncher.app.data.faviconCacheKey
import com.searchlauncher.app.ui.PreferencesKeys
import com.searchlauncher.app.ui.dataStore
import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

class BrowserFaviconRefreshTest {
  @Test
  fun pageVisitAndReloadRefreshFavoriteWithoutChangingTheIconUrl() = runBlocking {
    assumeTrue(BrowserEngine.isGecko)
    val app =
      InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        as SearchLauncherApp
    val repository = app.searchRepository
    withTimeout(30_000) { repository.isInitialized.first { it } }
    val host = "127.0.0.2"
    val server = ServerSocket(0, 10, InetAddress.getByName(host))
    val origin = "http://$host:${server.localPort}"
    val bookmark = "$origin/saved"
    val id = "saved_${bookmark.hashCode()}"
    val key = FavoriteKeys.of("web_saved", id)
    val phase = AtomicInteger(0)
    val pageRequests = AtomicInteger(0)
    val iconRequests = AtomicInteger(0)
    val oldHistory = app.dataStore.data.first()[PreferencesKeys.STORE_WEB_HISTORY]
    val colors = listOf(Color.RED, Color.BLUE)
    val icons =
      colors.map { color ->
        val bitmap =
          Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
        ByteArrayOutputStream()
          .also {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            bitmap.recycle()
          }
          .toByteArray()
      }
    val worker =
      thread(isDaemon = true, name = "favicon-fixture") {
        while (!server.isClosed) {
          val socket = runCatching { server.accept() }.getOrNull() ?: break
          socket.use {
            val reader = it.getInputStream().bufferedReader()
            val path = reader.readLine()?.split(' ')?.getOrNull(1) ?: return@use
            while (!reader.readLine().isNullOrEmpty()) {}
            val current = phase.get()
            val (mime, body) =
              when (path) {
                "/icon.png",
                "/favicon.ico" -> {
                  iconRequests.incrementAndGet()
                  "image/png" to icons[current]
                }
                "/phase" -> "text/plain" to current.toString().toByteArray()
                else -> {
                  pageRequests.incrementAndGet()
                  "text/html" to
                    """<!doctype html><html><head>
                <title>Favicon refresh fixture</title><link rel="icon" href="/icon.png">
                </head><body><h1>Favicon refresh fixture</h1>
                <script>setInterval(async () => {
                  if (await (await fetch('/phase', {cache:'no-store'})).text() !== '$current') location.reload();
                }, 250);</script></body></html>"""
                      .toByteArray()
                }
              }
            val out = it.getOutputStream()
            out.write(
              "HTTP/1.1 200 OK\r\nContent-Type: $mime\r\nContent-Length: ${body.size}\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n"
                .toByteArray()
            )
            out.write(body)
            out.flush()
          }
        }
      }
    var scenario: ActivityScenario<BrowserActivity>? = null
    try {
      app.dataStore.edit { it[PreferencesKeys.STORE_WEB_HISTORY] = false }
      assertTrue(repository.saveAndFavoriteBookmark(bookmark, "Favicon refresh fixture"))
      scenario = ActivityScenario.launch(BrowserActivity.createIntent(app, "$origin/page"))
      for ((index, color) in colors.withIndex()) {
        phase.set(index)
        withTimeout(25_000) {
          repository.favorites.first { favorites ->
            (favorites.firstOrNull { it.id == id }?.icon as? BitmapDrawable)
              ?.bitmap
              ?.getPixel(0, 0) == color
          }
        }
        withTimeout(5_000) {
          while (
            (IconRepository(app).loadFromDisk(faviconCacheKey(host)) as? BitmapDrawable)
              ?.bitmap
              ?.getPixel(0, 0) != color
          ) delay(50)
        }
      }
      assertTrue("The same page really reloaded", pageRequests.get() >= 2)
      assertTrue("The same favicon URL was fetched again", iconRequests.get() >= 2)
    } finally {
      scenario?.close()
      server.close()
      worker.join(1000)
      app.favoritesRepository.removeKeys(listOf(key))
      repository.removeBookmark(id, "web_saved")
      app.dataStore.edit {
        if (oldHistory == null) it.remove(PreferencesKeys.STORE_WEB_HISTORY)
        else it[PreferencesKeys.STORE_WEB_HISTORY] = oldHistory
      }
    }
  }
}
