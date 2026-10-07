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
import kotlin.concurrent.thread
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FullscreenBrowserTest {
  @Test
  fun siteAndVideoEnterAndExitFullscreen() {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val context = instrumentation.context
    val device = UiDevice.getInstance(instrumentation)
    val configurator = Configurator.getInstance()
    val previousIdleTimeout = configurator.waitForIdleTimeout
    configurator.waitForIdleTimeout = 0
    val server = ServerSocket(0)
    val video = context.assets.open("fullscreen-test.mp4").use { it.readBytes() }
    thread(isDaemon = true) {
      while (!server.isClosed) {
        val socket = runCatching { server.accept() }.getOrNull() ?: break
        thread(isDaemon = true) {
          runCatching {
            socket.use {
              val reader = it.getInputStream().bufferedReader()
              val path = reader.readLine()?.split(" ")?.getOrNull(1)
              while (!reader.readLine().isNullOrEmpty()) {}
              val body =
                if (path == "/video.mp4") video
                else
                  """
                <!doctype html><meta name="viewport" content="width=device-width,initial-scale=1">
                <style>html,body{margin:0;background:#007744;color:white;font:20px sans-serif}button{font-size:20px;padding:16px}video{width:100%}</style>
                <button onclick="document.documentElement.requestFullscreen().catch(failed)">Site fullscreen</button>
                <button onclick="v.play();v.requestFullscreen().catch(failed)">Video fullscreen</button>
                <button onclick="document.exitFullscreen()">Exit fullscreen</button>
                <p id="state">Fullscreen off</p><video id="v" src="/video.mp4" controls loop playsinline></video>
                <script>
                function failed(e){state.textContent='Fullscreen error: '+e.name+' '+e.message;}
                document.addEventListener('fullscreenchange',()=>{state.textContent=document.fullscreenElement?'Fullscreen on':'Fullscreen off';});
                </script>
              """
                    .trimIndent()
                    .toByteArray()
              val type = if (path == "/video.mp4") "video/mp4" else "text/html"
              it
                .getOutputStream()
                .write(
                  ("HTTP/1.1 200 OK\r\nContent-Type: $type\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n")
                    .toByteArray() + body
                )
            }
          }
        }
      }
    }
    try {
      device.wakeUp()
      device.executeShellCommand("wm dismiss-keyguard")
      context.startActivity(
        Intent(Intent.ACTION_VIEW, Uri.parse("http://localhost:${server.localPort}/"))
          .setClassName(targetBrowserPackage, "com.searchlauncher.app.ui.browser.BrowserActivity")
          .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      )
      for (label in listOf("Site fullscreen", "Video fullscreen")) {
        assertNotNull(device.wait(Until.findObject(By.text(label)), 15000))
        // Accessibility can expose the document before the tab has input focus on a cold start.
        SystemClock.sleep(500)
        device.findObject(By.text(label)).click()
        SystemClock.sleep(1500)
        device.findObject(By.text("Got it"))?.click()
        device.takeScreenshot(File(context.getExternalFilesDir(null), "fullscreen-$label.png"))
        device.dumpWindowHierarchy(File(context.getExternalFilesDir(null), "fullscreen-$label.xml"))
        assertTrue(
          "$label should hide browser controls",
          device.wait(Until.gone(By.desc("Browser menu")), 5000),
        )
        device.pressBack()
        assertNotNull(device.wait(Until.findObject(By.desc("Browser menu")), 5000))
        assertNotNull(device.wait(Until.findObject(By.text("Fullscreen off")), 5000))
      }
    } finally {
      configurator.waitForIdleTimeout = previousIdleTimeout
      server.close()
    }
  }
}
