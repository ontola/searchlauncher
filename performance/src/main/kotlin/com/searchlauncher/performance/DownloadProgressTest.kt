package com.searchlauncher.performance

import android.content.Intent
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
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Exercise the original Gecko response stream, download cards, and real Android notifications. */
@RunWith(AndroidJUnit4::class)
class DownloadProgressTest {
  @Test fun speedAndRemainingSurviveNotificationAndCompletion() = download(knownSize = true)

  @Test fun unknownSizeShowsTransferredBytesAndCanBeCancelled() = download(knownSize = false)

  private fun download(knownSize: Boolean) {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val context = instrumentation.context
    val device = UiDevice.getInstance(instrumentation)
    val config = Configurator.getInstance()
    val oldIdle = config.waitForIdleTimeout
    config.waitForIdleTimeout = 0
    val finish = AtomicBoolean(false)
    val server = ServerSocket(0)
    val name =
      "speed-test-${if (knownSize) "known" else "unknown"}-${SystemClock.uptimeMillis()}.bin"
    thread(isDaemon = true) {
      while (!server.isClosed) {
        val socket = runCatching { server.accept() }.getOrNull() ?: break
        thread(isDaemon = true) {
          runCatching {
            socket.use {
              val reader = it.getInputStream().bufferedReader()
              reader.readLine()
              while (!reader.readLine().isNullOrEmpty()) {}
              val output = it.getOutputStream()
              val length = if (knownSize) "Content-Length: 33554432\r\n" else ""
              output.write(
                ("HTTP/1.1 200 OK\r\nContent-Type: application/octet-stream\r\n" +
                    "Content-Disposition: attachment; filename=\"$name\"\r\n" +
                    length +
                    "Connection: close\r\n\r\n")
                  .toByteArray()
              )
              val chunk = ByteArray(65536) { 42 }
              repeat(512) {
                output.write(chunk)
                output.flush()
                if (!finish.get()) SystemClock.sleep(100)
              }
            }
          }
        }
      }
    }
    try {
      device.executeShellCommand("cmd statusbar collapse")
      device.wakeUp()
      device.executeShellCommand("wm dismiss-keyguard")
      device.executeShellCommand(
        "pm grant $targetBrowserPackage android.permission.POST_NOTIFICATIONS"
      )
      context.startActivity(
        Intent(Intent.ACTION_VIEW, Uri.parse("http://localhost:${server.localPort}/$name"))
          .setClassName(targetBrowserPackage, "com.searchlauncher.app.ui.browser.BrowserActivity")
          .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      )
      // Download-only navigations open the downloads panel automatically.
      assertNotNull(device.wait(Until.findObject(By.text(name)), 20000))
      SystemClock.sleep(500) // Wait for the downloads-panel entrance animation.
      val detail = if (knownSize) "MB left" else "Size unknown"
      assertNotNull(device.wait(Until.findObject(By.textContains(detail)), 10000))
      val speed = device.wait(Until.findObject(By.textContains("B/s")), 5000)
      assertNotNull(speed)
      assertTrue(speed!!.text.contains(detail))
      assertTrue(
        device.takeScreenshot(
          File(context.getExternalFilesDir(null), "download-$knownSize-card.png")
        )
      )
      device.executeShellCommand("cmd statusbar expand-notifications")
      assertNotNull(device.wait(Until.findObject(By.pkg("com.android.systemui").text(name)), 5000))
      assertNotNull(
        device.wait(Until.findObject(By.pkg("com.android.systemui").textContains(detail)), 5000)
      )
      // Expand the individual notification to inspect its full text, beyond the compact header.
      var container = device.findObject(By.pkg("com.android.systemui").text(name))
      while (container != null) {
        val expand = container.findObject(By.res("android", "expand_button"))
        if (expand != null) {
          expand.click()
          SystemClock.sleep(350)
          break
        }
        container = container.parent
      }
      assertTrue(
        device.takeScreenshot(
          File(context.getExternalFilesDir(null), "download-$knownSize-notification.png")
        )
      )
      device.executeShellCommand("cmd statusbar collapse")
      SystemClock.sleep(500) // Do not tap through the closing notification shade.
      assertNotNull(device.wait(Until.findObject(By.text("Downloads")), 5000))
      if (knownSize) {
        finish.set(true)
        assertTrue(device.wait(Until.gone(By.textContains("MB left")), 15000))
        assertNotNull(device.findObject(By.text(name)))
        assertNotNull(device.findObject(By.text("Open file")))
        // A late timer tick must never turn a completed card back into an active one.
        SystemClock.sleep(1200)
        assertNull(device.findObject(By.textContains("MB left")))
        assertNotNull(device.findObject(By.text(name)))
      } else {
        device.findObject(By.text("Cancel"))!!.click()
        assertTrue(device.wait(Until.gone(By.text(name)), 10000))
        SystemClock.sleep(1200)
        assertNull(device.findObject(By.text(name)))
      }
    } finally {
      device.executeShellCommand("cmd statusbar collapse")
      finish.set(true)
      server.close()
      config.waitForIdleTimeout = oldIdle
    }
  }
}
