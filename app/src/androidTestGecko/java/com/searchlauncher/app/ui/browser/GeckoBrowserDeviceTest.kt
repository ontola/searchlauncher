package com.searchlauncher.app.ui.browser

import android.app.DownloadManager
import android.app.NotificationManager
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.SystemClock
import androidx.datastore.preferences.core.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.searchlauncher.app.SearchLauncherApp
import com.searchlauncher.app.data.SearchResult
import com.searchlauncher.app.ui.MainActivity
import com.searchlauncher.app.ui.PreferencesKeys
import com.searchlauncher.app.ui.dataStore
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread
import kotlinx.coroutines.runBlocking
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
  private val releaseIcon = java.util.concurrent.CountDownLatch(1)

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
    releaseIcon.countDown()
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
    menuItem("Clear this site's data").click()
    device.wait(Until.findObject(By.res("android:id/button1")), 10000)!!.click()
    waitFor("engine")
    tap("Read data")
    waitFor("storage-missing")
  }

  @Test
  fun previewsChromeAndSwipeSurviveRepeatedHandoffs() {
    device.wait(Until.findObject(By.text("Export file")), 15000)
    assertFalse(device.hasObject(By.textContains("Gecko experiment")))
    var tabId = 0L
    instrumentation.runOnMainSync { tabId = BrowserTabStore.tabs!!.active.id }
    repeat(4) {
      val button = device.wait(Until.findObject(By.descContains("open tab")), 10000)!!
      val bounds = button.visibleBounds
      val chrome = android.graphics.BitmapFactory.decodeFile(saveScreenshot("chrome").absolutePath)
      val sample = chrome.getPixel(bounds.left - 15, bounds.centerY())
      assertTrue(
        "Browser toolbar should stay light even in dark launcher theme",
        Color.red(sample) > 220 && Color.green(sample) > 220,
      )
      chrome.recycle()
      button.click()
      device.wait(Until.findObject(By.text("Close all")), 10000)
      var hasColoredPreview = false
      instrumentation.runOnMainSync {
        val bitmap = BrowserTabStore.tab(tabId)?.snapshot
        if (bitmap != null) {
          for (y in 0 until bitmap.height step 8) for (x in 0 until bitmap.width step 8) {
            val pixel = bitmap.getPixel(x, y)
            if (Color.green(pixel) > 90 && Color.red(pixel) < 70 && Color.blue(pixel) > 80)
              hasColoredPreview = true
          }
        }
      }
      assertTrue(
        "Overview must contain the rendered teal fixture, not a blank bitmap",
        hasColoredPreview,
      )
      saveScreenshot("overview-$it")
      device.pressBack()
      val bar = device.wait(Until.findObject(By.desc("Browser menu")), 10000)!!.visibleBounds
      if (it == 0) {
        // A short drag returns to this page; a committed left swipe returns to launcher.
        device.swipe(
          device.displayWidth / 2,
          bar.centerY(),
          device.displayWidth / 2 - 80,
          bar.centerY(),
          100,
        )
        device.waitForIdle()
        assertTrue(device.hasObject(By.desc("Browser menu")))
      }
      device.swipe(
        device.displayWidth * 3 / 4,
        bar.centerY(),
        device.displayWidth / 10,
        bar.centerY(),
        24,
      )
      eventually {
        var home = false
        instrumentation.runOnMainSync {
          home =
            ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).any {
              it is MainActivity
            }
        }
        home
      }
      instrumentation.runOnMainSync { BrowserTabTasks.open(context, tabId) }
      device.wait(Until.findObject(By.desc("Browser menu")), 10000)
    }
    device.findObject(By.desc("Browser menu")).click()
    val back = device.wait(Until.findObject(By.text("Back")), 10000)!!
    saveScreenshot("menu")
    assertTrue(
      "Menu should not cover the whole screen",
      back.visibleBounds.top > device.displayHeight / 4,
    )
    menuItem("About this experiment").click()
    assertNotNull(device.wait(Until.findObject(By.text("SearchLauncher Gecko")), 10000))
    device.pressBack()
  }

  @Test
  fun siteIconsReachExistingFavoritesRecentsAndDiskCache() {
    val app = context.applicationContext as SearchLauncherApp
    eventually(30000) { app.searchRepository.isInitialized.value }
    // Pin before the icon is available: the already visible globe must refresh when it arrives.
    val iconHost = "127.0.${server.localPort / 256}.${server.localPort % 256}"
    val url = "http://$iconHost:${server.localPort}/icons"
    assertNull(
      "Use a fresh host to test globe replacement",
      runBlocking { app.searchRepository.loadFavicon(url) },
    )
    runBlocking {
      app.searchRepository.saveAndFavoriteBookmark(url, "Icon fixture")
      context.dataStore.edit { it[PreferencesKeys.BROWSER_SHOW_FAVORITES] = true }
    }
    try {
      context.startActivity(BrowserActivity.createIntent(context, url))
      waitFor("icon-request", 30000)
      releaseIcon.countDown()
      eventually(15000) {
        var matches = false
        instrumentation.runOnMainSync {
          matches =
            BrowserTabStore.tabs?.items?.firstOrNull { it.url == url }?.favicon?.let(::isMagenta) ==
              true
        }
        matches
      }
      eventually {
        app.searchRepository.favorites.value.any {
          it is SearchResult.Content &&
            it.deepLink == url &&
            (it.icon as? BitmapDrawable)?.bitmap?.let(::isMagenta) == true
        }
      }
      instrumentation.runOnMainSync {
        val tab = BrowserTabStore.tabs!!.items.first { it.url == url }
        assertTrue(isMagenta((tab.toSearchResult(context)!!.icon as BitmapDrawable).bitmap))
      }
      assertTrue(runBlocking { app.searchRepository.loadFavicon(url)?.let(::isMagenta) == true })
      device.waitForIdle()
      SystemClock.sleep(800)
      val screenshot =
        android.graphics.BitmapFactory.decodeFile(saveScreenshot("favicon-strip").absolutePath)
      var visibleIcon = false
      for (y in screenshot.height * 3 / 4 until screenshot.height step 3) {
        for (x in 0 until screenshot.width step 3) {
          val pixel = screenshot.getPixel(x, y)
          if (Color.red(pixel) > 200 && Color.green(pixel) < 70 && Color.blue(pixel) > 180)
            visibleIcon = true
        }
      }
      screenshot.recycle()
      assertTrue("The favorites strip must visibly render the site's icon", visibleIcon)
      // A private host with an icon must never create a public icon-cache entry.
      context.startActivity(
        BrowserActivity.createPrivateIntent(context, "http://127.0.0.2:${server.localPort}/icons")
      )
      waitFor("icon-page-127.0.0.2", 30000)
      SystemClock.sleep(500)
      assertNull(runBlocking { app.searchRepository.loadFavicon("http://127.0.0.2/") })
      device.pressBack()
    } finally {
      runBlocking { context.dataStore.edit { it[PreferencesKeys.BROWSER_SHOW_FAVORITES] = false } }
    }
  }

  private fun isMagenta(bitmap: android.graphics.Bitmap): Boolean {
    val pixel = bitmap.getPixel(bitmap.width / 2, bitmap.height / 2)
    return Color.red(pixel) > 200 && Color.green(pixel) < 70 && Color.blue(pixel) > 180
  }

  private fun saveScreenshot(name: String): java.io.File =
    java.io.File(context.getExternalFilesDir(null), "gecko-$name.png").also {
      device.takeScreenshot(it)
    }

  private fun menuItem(text: String): androidx.test.uiautomator.UiObject2 {
    repeat(8) {
      device.findObject(By.text(text))?.let {
        return it
      }
      val scrollable = device.findObject(By.scrollable(true))
      if (scrollable != null) scrollable.scroll(androidx.test.uiautomator.Direction.DOWN, 0.7f)
      else SystemClock.sleep(200)
    }
    error("Missing menu item: $text")
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
          path == "/assets/custom-icon.svg" -> {
            reports += "icon-request"
            releaseIcon.await(20, java.util.concurrent.TimeUnit.SECONDS)
            mime = "image/svg+xml"
            """<svg xmlns="http://www.w3.org/2000/svg" width="96" height="96"><rect width="96" height="96" rx="16" fill="#ff00ee"/></svg>"""
          }
          path == "/icons" ->
            """
            <!doctype html><meta name="viewport" content="width=device-width"><title>Icon fixture</title>
            <link rel="icon" type="image/svg+xml" sizes="any" href="/assets/custom-icon.svg">
            <h1>Icon fixture</h1><p>This page has a custom SVG favicon.</p>
            <script>fetch("/report?icon-page-"+location.hostname)</script>
          """
              .trimIndent()
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
          <h2 style="background:#009688;color:white;padding:24px">Gecko browser checks</h2><p>Real browser integration tests</p>
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
