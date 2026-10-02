package com.searchlauncher.app.ui.browser

import android.app.DownloadManager
import android.app.NotificationManager
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** End-to-end checks through the actual Gecko build; localhost is a secure context for SW APIs. */
@RunWith(AndroidJUnit4::class)
class GeckoBrowserDeviceTest {
  private val instrumentation = InstrumentationRegistry.getInstrumentation()
  private val context = instrumentation.targetContext
  private val device = UiDevice.getInstance(instrumentation)
  private lateinit var server: ServerSocket
  private val reports = CopyOnWriteArrayList<String>()
  private lateinit var origin: String

  @Before
  fun start() {
    server = ServerSocket(0)
    origin = "http://localhost:${server.localPort}"
    thread(isDaemon = true) {
      while (!server.isClosed) {
        val socket = runCatching { server.accept() }.getOrNull() ?: break
        thread(isDaemon = true) { serve(socket) }
      }
    }
    context.startActivity(
      Intent(context, BrowserActivity::class.java)
        .setData(Uri.parse("$origin/"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
    waitFor("engine", 30000)
    assertTrue("Fixture must run in Gecko, not WebView", reports.any { it.contains("Firefox/") })
  }

  @After
  fun stop() {
    server.close()
  }

  @Test
  fun browserSupportsNavigationPopupExportsAndLocalNotifications() {
    assertTrue(reports.any { it.contains("apis:true,true,true") })
    tap("Export file")
    val manager = context.getSystemService(DownloadManager::class.java)
    eventually {
      manager.query(DownloadManager.Query()).use { cursor ->
        var found = false
        while (cursor.moveToNext()) {
          if (
            cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TITLE)) ==
              "gecko-fixture.txt" &&
              cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)) ==
                DownloadManager.STATUS_SUCCESSFUL
          ) {
            val id = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_ID))
            manager.openDownloadedFile(id).use { descriptor ->
              android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).use {
                found =
                  found ||
                    it.readBytes().toString(Charsets.UTF_8) ==
                      "Original Gecko response ${server.localPort}"
              }
            }
          }
        }
        found
      }
    }
    tap("Open popup")
    waitFor("popup-opened")
    tap("Message opener")
    waitFor("popup-message")
    device.pressBack()
    tap("Notify me")
    val allow = device.wait(Until.findObject(By.res("android:id/button1")), 10000)
    assertNotNull("Website notification permission prompt", allow)
    allow!!.click()
    // The runtime notification permission is pregranted by the test command.
    waitFor("notification-granted")
    eventually {
      context.getSystemService(NotificationManager::class.java).activeNotifications.any {
        it.notification.extras.getString("android.title") == "Gecko fixture notification"
      }
    }
    tap("Store data")
    waitFor("storage-written")
    tap("Read data")
    waitFor("storage-present")
  }

  @Test
  fun privateStorageIsIsolatedAndSiteDataCanBeCleared() {
    tap("Store data")
    waitFor("storage-written")
    reports.clear()
    context.startActivity(BrowserActivity.createPrivateIntent(context, "$origin/"))
    waitFor("engine", 30000)
    tap("Read data")
    waitFor("storage-missing")
    tap("Store data")
    waitFor("storage-written")
    device.pressBack()
    reports.clear()
    tap("Read data")
    waitFor("storage-present")
    reports.clear()
    context.startActivity(BrowserActivity.createPrivateIntent(context, "$origin/"))
    waitFor("engine", 30000)
    tap("Read data")
    waitFor("storage-missing")
    device.pressBack()
    reports.clear()
    device.wait(Until.findObject(By.desc("Browser menu")), 10000)!!.click()
    device.wait(Until.findObject(By.text("Clear this site's data")), 10000)!!.click()
    device.wait(Until.findObject(By.res("android:id/button1")), 10000)!!.click()
    waitFor("engine")
    tap("Read data")
    waitFor("storage-missing")
  }

  private fun tap(text: String) {
    val item = device.wait(Until.findObject(By.text(text)), 15000)
    assertNotNull("Missing visible control: $text", item)
    device.waitForIdle()
    // The initial Gecko accessibility tree can precede the first viewport resize.
    SystemClock.sleep(800)
    device.dumpWindowHierarchy(
      java.io.File(context.getExternalFilesDir(null), "gecko-before-tap.xml")
    )
    val ready = device.findObject(By.text(text))!!
    android.util.Log.i("GeckoFixture", "Tap $text at ${ready.visibleBounds}")
    ready.click()
    device.takeScreenshot(java.io.File(context.getExternalFilesDir(null), "gecko-after-tap.png"))
  }

  private fun waitFor(value: String, timeout: Long = 15000) =
    eventually(timeout) { reports.any { it.contains(value) } }

  private fun eventually(timeout: Long = 15000, predicate: () -> Boolean) {
    val deadline = SystemClock.uptimeMillis() + timeout
    while (SystemClock.uptimeMillis() < deadline) {
      if (predicate()) return
      SystemClock.sleep(100)
    }
    fail("Timed out; reports=$reports")
  }

  private fun serve(socket: Socket) {
    socket.use {
      val reader = it.getInputStream().bufferedReader()
      val path = reader.readLine()?.split(' ')?.getOrNull(1) ?: return
      while (!reader.readLine().isNullOrEmpty()) {}
      var mime = "text/html"
      val body =
        when {
          path.startsWith("/report?") -> {
            reports += java.net.URLDecoder.decode(path, "UTF-8")
            "ok"
          }
          path == "/sw.js" -> {
            mime = "application/javascript"
            "self.addEventListener('install',()=>self.skipWaiting()); self.addEventListener('activate',e=>e.waitUntil(clients.claim()));"
          }
          path == "/popup" ->
            """
          <meta name="viewport" content="width=device-width"><title>Gecko popup</title>
          <button style="font-size:24px;padding:20px" onclick="opener.postMessage('hello','*');document.body.append('Sent')">Message opener</button>
          <script>fetch('/report?popup-opened')</script>
        """
              .trimIndent()
          else ->
            """
          <!doctype html><meta name="viewport" content="width=device-width"><title>Gecko browser checks</title>
          <style>body{font:18px sans-serif;background:white;color:#192218;padding:16px}button{display:block;margin:12px 0;padding:12px;font:inherit}</style>
          <h2>Gecko browser checks</h2><p>Real browser integration tests</p>
          <button onclick="report('export-click');const a=document.createElement('a');a.href=window.URL.createObjectURL(new Blob(['Original Gecko response ${server.localPort}'],{type:'text/plain'}));a.download='gecko-fixture.txt';document.body.append(a);a.click()">Export file</button>
          <button onclick="window.open('/popup','_blank')">Open popup</button>
          <button onclick="notify()">Notify me</button>
          <button onclick="localStorage.setItem('fixture','saved');report('storage-written')">Store data</button>
          <button onclick="report(localStorage.getItem('fixture')==='saved'?'storage-present':'storage-missing')">Read data</button>
          <script>
          function report(s){fetch('/report?'+encodeURIComponent(s))}
          report('engine:'+navigator.userAgent);
          report('apis:'+('serviceWorker' in navigator)+','+('Notification' in window)+','+('PublicKeyCredential' in window));
          window.addEventListener('message',e=>{if(e.data==='hello')report('popup-message')});
          navigator.serviceWorker.register('/sw.js');
          async function notify(){
            const p=await Notification.requestPermission();report('notification-'+p);
            if(p==='granted'){const reg=await navigator.serviceWorker.ready;await reg.showNotification('Gecko fixture notification',{body:'Displayed by the Android app',tag:'fixture'})}
          }
          </script>
        """
              .trimIndent()
        }
      val bytes = body.toByteArray()
      it.getOutputStream().apply {
        write(
          "HTTP/1.1 200 OK\r\nContent-Type: $mime; charset=utf-8\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n"
            .toByteArray()
        )
        write(bytes)
        flush()
      }
    }
  }
}
