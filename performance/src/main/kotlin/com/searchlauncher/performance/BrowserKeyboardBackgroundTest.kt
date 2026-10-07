package com.searchlauncher.performance

import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Configurator
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import java.io.File
import java.net.ServerSocket
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Runs against either optimized backend without changing the measured app. */
@RunWith(AndroidJUnit4::class)
class BrowserKeyboardBackgroundTest {
  @Test
  fun darkPageRegainsViewportAfterKeyboardCloses() {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val context = instrumentation.context
    val device = UiDevice.getInstance(instrumentation)
    val config = Configurator.getInstance()
    val oldIdle = config.waitForIdleTimeout
    config.waitForIdleTimeout = 0
    val heights = CopyOnWriteArrayList<Int>()
    val server = ServerSocket(0)
    thread(isDaemon = true) {
      while (!server.isClosed) {
        val socket = runCatching { server.accept() }.getOrNull() ?: break
        thread(isDaemon = true) {
          runCatching {
            socket.use {
              val reader = it.getInputStream().bufferedReader()
              val path = reader.readLine()?.split(" ")?.getOrNull(1).orEmpty()
              while (!reader.readLine().isNullOrEmpty()) {}
              if (path.startsWith("/height/"))
                path.substringAfterLast('/').toIntOrNull()?.let(heights::add)
              val body =
                if (path.startsWith("/height/")) "ok"
                else
                  """
                <!doctype html><meta name="viewport" content="width=device-width,initial-scale=1">
                <meta name="theme-color" content="#aa1239">
                <style>html,body{margin:0;background:#000;color:white;font:20px sans-serif}
                input{margin:24px;font-size:20px}footer{position:fixed;bottom:0;height:30px}</style>
                <input aria-label="Keyboard resize probe" placeholder="Type here">
                <footer>Bottom of viewport</footer>
                <script>
                function report(){fetch('/height/'+Math.round(visualViewport.height))}
                visualViewport.addEventListener('resize',report);addEventListener('load',report);
                </script>
              """
                    .trimIndent()
              val bytes = body.toByteArray()
              it
                .getOutputStream()
                .write(
                  ("HTTP/1.1 200 OK\r\nContent-Type: text/html\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n")
                    .toByteArray() + bytes
                )
            }
          }
        }
      }
    }
    fun waitFor(check: () -> Boolean) {
      val deadline = SystemClock.uptimeMillis() + 10000
      while (!check() && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(25)
      assertTrue(check())
    }
    try {
      device.wakeUp()
      device.executeShellCommand("wm dismiss-keyguard")
      context.startActivity(
        Intent(Intent.ACTION_VIEW, Uri.parse("http://localhost:${server.localPort}/"))
          .setClassName(
            "com.searchlauncher.app.gecko",
            "com.searchlauncher.app.ui.browser.BrowserActivity",
          )
          .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      )
      assertNotNull(device.wait(Until.findObject(By.clazz("android.widget.EditText")), 15000))
      waitFor { heights.isNotEmpty() }
      val fullHeight = heights.last()
      repeat(3) { cycle ->
        device.findObject(By.clazz("android.widget.EditText"))!!.click()
        waitFor { heights.last() < fullHeight * 0.85 }
        device.executeShellCommand("input text hello")
        device.pressBack()
        waitFor { kotlin.math.abs(heights.last() - fullHeight) <= 2 }
        assertNotNull(device.wait(Until.findObject(By.desc("Browser menu")), 5000))
        val chrome = device.findObject(By.desc("Browser menu"))!!.visibleBounds
        waitFor {
          val footer = device.findObject(By.text("Bottom of viewport"))?.visibleBounds
          footer != null && footer.height() > 0 && footer.bottom <= chrome.top
        }
        val shot = File(context.getExternalFilesDir(null), "keyboard-background-$cycle.png")
        assertTrue(device.takeScreenshot(shot))
        val bitmap = BitmapFactory.decodeFile(shot.absolutePath)
        try {
          // Check the area the IME occupied, away from the footer text and browser chrome.
          for (dy in listOf(70, 170, 300)) {
            val pixel = bitmap.getPixel(bitmap.width * 3 / 4, chrome.top - dy)
            assertTrue(
              "White/unpainted strip after keyboard close: $pixel",
              Color.red(pixel) < 15 && Color.green(pixel) < 15 && Color.blue(pixel) < 15,
            )
          }
        } finally {
          bitmap.recycle()
        }
      }
    } finally {
      File(context.getExternalFilesDir(null), "keyboard-viewport-heights.txt")
        .writeText(heights.joinToString("\n"))
      config.waitForIdleTimeout = oldIdle
      server.close()
    }
  }
}
