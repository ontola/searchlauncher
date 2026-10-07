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
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Drives an optimized APK: manifest discovery, real install UI, scope changes and persistence. */
@RunWith(AndroidJUnit4::class)
class InstalledWebAppTest {
  @Test fun installLaunchNavigateAndNotify() = exerciseApp("standalone")

  @Test fun fullscreenAppUsesImmersiveMode() = exerciseApp("fullscreen")

  private fun exerciseApp(display: String) {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val context = instrumentation.context
    val device = UiDevice.getInstance(instrumentation)
    val config = Configurator.getInstance()
    val oldIdle = config.waitForIdleTimeout
    config.waitForIdleTimeout = 0
    val reports = CopyOnWriteArrayList<String>()
    val server = ServerSocket(0)
    val origin = "http://localhost:${server.localPort}"
    val name = "PWA test ${server.localPort}"
    thread(isDaemon = true) {
      while (!server.isClosed) {
        val socket = runCatching { server.accept() }.getOrNull() ?: break
        thread(isDaemon = true) {
          runCatching {
            socket.use {
              val reader = it.getInputStream().bufferedReader()
              val path = reader.readLine()?.split(" ")?.getOrNull(1).orEmpty()
              while (!reader.readLine().isNullOrEmpty()) {}
              var type = "text/html"
              val body =
                when {
                  path.startsWith("/report/") -> {
                    reports += path
                    "ok"
                  }
                  path == "/app/manifest.json" -> {
                    type = "application/manifest+json"
                    """{"name":"$name","start_url":"/app/start","scope":"/app/","display":"$display"}"""
                  }
                  path == "/app/sw.js" -> {
                    type = "application/javascript"
                    """
                  self.addEventListener('install',e=>self.skipWaiting());
                  self.addEventListener('activate',e=>e.waitUntil(clients.claim()));
                  self.addEventListener('notificationclick',e=>{e.notification.close();e.waitUntil(fetch('/report/clicked'))});
                """
                      .trimIndent()
                  }
                  path == "/outside" ->
                    """<meta name="viewport" content="width=device-width"><h1>Outside app scope</h1>"""
                  else ->
                    """
                <!doctype html><meta name="viewport" content="width=device-width,initial-scale=1">
                <link rel="manifest" href="/app/manifest.json"><title>$name</title>
                <style>body{background:#132b24;color:white;font:22px sans-serif;margin:24px}button,a{display:block;margin:24px 0;font:22px sans-serif;color:inherit}button{background:#234c40;padding:18px}</style>
                <h1>$name</h1><p id="mode"></p><a href="/outside">Leave app scope</a>
                <button onclick="notify()">Notify me</button>
                <script>
                  function report(s){fetch('/report/'+s)}
                  function mode(){let m=matchMedia('(display-mode: $display)').matches?'$display':'browser';document.querySelector('#mode').textContent='Mode '+m;report(m)}
                  mode();matchMedia('(display-mode: $display)').addEventListener('change',mode);
                  navigator.serviceWorker.register('/app/sw.js');
                  async function notify(){let p=await Notification.requestPermission();report(p);if(p==='granted'){let reg=await navigator.serviceWorker.ready;await reg.showNotification('PWA notification',{body:'Installed app notification',tag:'pwa-test'})}}
                </script>
              """
                      .trimIndent()
                }
              val bytes = body.toByteArray()
              it
                .getOutputStream()
                .write(
                  ("HTTP/1.1 200 OK\r\nContent-Type: $type\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n")
                    .toByteArray() + bytes
                )
            }
          }
        }
      }
    }
    fun waitFor(message: String, check: () -> Boolean) {
      val deadline = SystemClock.uptimeMillis() + 15000
      while (!check() && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(50)
      assertTrue(message, check())
    }
    fun tap(text: String) {
      val node = device.wait(Until.findObject(By.text(text)), 10000)
      assertNotNull(text, node)
      node!!.click()
    }
    fun open(url: String) {
      context.startActivity(
        Intent(Intent.ACTION_VIEW, Uri.parse(url))
          .setClassName(targetBrowserPackage, "com.searchlauncher.app.ui.browser.BrowserActivity")
          .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      )
    }
    try {
      device.wakeUp()
      device.executeShellCommand("wm dismiss-keyguard")
      device.executeShellCommand(
        "pm grant $targetBrowserPackage android.permission.POST_NOTIFICATIONS"
      )
      open("$origin/app/start")
      waitFor("Browser document loaded") { reports.any { it.endsWith("/browser") } }
      device.wait(Until.findObject(By.desc("Browser menu")), 10000)!!.click()
      tap("Install app")
      device.wait(Until.findObject(By.res("android:id/button1")), 10000)!!.click()
      tap("Mode $display")
      assertFalse(
        "Standalone app must not show browser controls",
        device.hasObject(By.desc("Browser menu")),
      )
      // Accessibility can expose the DOM before Gecko has composited it. Require real pixels.
      fun painted(): Boolean {
        val image = instrumentation.uiAutomation.takeScreenshot() ?: return false
        return try {
          val color = image.getPixel(image.width / 2, image.height / 2)
          android.graphics.Color.red(color) < 40 && android.graphics.Color.green(color) in 25..65
        } finally {
          image.recycle()
        }
      }
      waitFor("Installed app must be visibly painted") { painted() }
      SystemClock.sleep(250) // Let Android finish hiding bars for fullscreen screenshots.
      assertTrue("Paint remains visible", painted())
      device.takeScreenshot(File(context.getExternalFilesDir(null), "pwa-$display-installed.png"))
      tap("Leave app scope")
      tap("Outside app scope")
      assertNotNull(
        "External scope must reveal browser identity",
        device.wait(Until.findObject(By.desc("Browser menu")), 10000),
      )
      device.pressBack()
      tap("Mode $display")
      assertFalse(device.hasObject(By.desc("Browser menu")))
      tap("Notify me")
      device.wait(Until.findObject(By.res("android:id/button1")), 10000)!!.click()
      waitFor("Notification permission") { reports.any { it.endsWith("/granted") } }
      device.openNotification()
      tap("PWA notification")
      waitFor("Notification click reached service worker") {
        reports.any { it.endsWith("/clicked") }
      }
      tap("Mode $display")
      device.takeScreenshot(
        File(context.getExternalFilesDir(null), "pwa-$display-notification-return.png")
      )
      // The favorite must launch as an app even after the browser process has gone away.
      device.executeShellCommand("am force-stop $targetBrowserPackage")
      context.startActivity(
        Intent()
          .setClassName(targetBrowserPackage, "com.searchlauncher.app.ui.MainActivity")
          .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      )
      device.wait(Until.hasObject(By.desc(name)), 15000)
      device.findObject(By.text("Got it"))?.click()
      val favorite = device.wait(Until.findObject(By.desc(name)), 15000)
      assertNotNull("Installed app favorite", favorite)
      favorite!!.click()
      tap("Mode $display")
      assertFalse(device.hasObject(By.desc("Browser menu")))
      // Reopen the browser document, then its persisted installation. No duplicate install flow.
      device.executeShellCommand("am force-stop $targetBrowserPackage")
      open("$origin/app/start")
      tap("Mode browser")
      device.wait(Until.findObject(By.desc("Browser menu")), 10000)!!.click()
      tap("Open app")
      tap("Mode $display")
      assertFalse(device.hasObject(By.desc("Browser menu")))
    } finally {
      device.dumpWindowHierarchy(File(context.getExternalFilesDir(null), "pwa-$display-final.xml"))
      device.takeScreenshot(File(context.getExternalFilesDir(null), "pwa-$display-final.png"))
      File(context.getExternalFilesDir(null), "pwa-$display-reports.txt")
        .writeText(reports.joinToString("\n"))
      server.close()
      config.waitForIdleTimeout = oldIdle
    }
  }
}
