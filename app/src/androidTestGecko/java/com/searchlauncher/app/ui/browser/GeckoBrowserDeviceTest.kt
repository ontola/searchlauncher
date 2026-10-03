package com.searchlauncher.app.ui.browser

import android.app.DownloadManager
import android.app.NotificationManager
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.SystemClock
import androidx.compose.ui.graphics.asAndroidBitmap
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
import kotlinx.coroutines.flow.first
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
  private val delayNextDocument = java.util.concurrent.atomic.AtomicBoolean(false)
  private val releaseRestore = java.util.concurrent.CountDownLatch(1)
  private val releaseReload = java.util.concurrent.CountDownLatch(1)
  private val releaseDownload = java.util.concurrent.CountDownLatch(1)
  private val downloadBytes = ByteArray(1024 * 1024 + 123) { (it % 251).toByte() }
  private val releaseIcon = java.util.concurrent.CountDownLatch(1)

  @Before
  fun start() {
    if (android.os.Build.VERSION.SDK_INT >= 33) {
      instrumentation.uiAutomation.grantRuntimePermission(
        context.packageName,
        android.Manifest.permission.POST_NOTIFICATIONS,
      )
    }
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
    releaseDownload.countDown()
    releaseReload.countDown()
    releaseRestore.countDown()
    server.close()
  }

  @Test
  fun browserSupportsNavigationPopupExportsAndLocalNotifications() {
    if (android.os.Build.VERSION.SDK_INT >= 33) {
      instrumentation.uiAutomation.grantRuntimePermission(
        context.packageName,
        android.Manifest.permission.POST_NOTIFICATIONS,
      )
    }
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
    assertNotNull(device.wait(Until.findObject(By.text("Downloads")), 10000))
    val expectedSize =
      android.text.format.Formatter.formatShortFileSize(
        context,
        "Original Gecko response ${server.localPort}".toByteArray().size.toLong(),
      )
    assertNotNull(device.wait(Until.findObject(By.textStartsWith("$expectedSize ·")), 10000))
    tap("Done")
    tap("Open popup")
    waitFor("popup-opened")
    tap("Message opener")
    waitFor("popup-message")
    device.pressBack()
    tap("Notify me")
    val allow = device.wait(Until.findObject(By.res("android:id/button1")), 10000)
    assertNotNull("Website notification permission prompt", allow)
    allow!!.click()
    // Android permission is granted above; this click tests the separate website permission.
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
  fun adFilteringBlocksNetworkRequestsAndRespectsBothSettings() {
    val hashes =
      AdBlocker::class.java.getDeclaredField("domainHashes").apply { isAccessible = true }
    val original = hashes.get(AdBlocker)
    val settings = BrowserSiteSettingsStore(context, false)
    val originalSite = settings.load(origin)
    val originalEnabled = runBlocking {
      context.dataStore.data.first()[PreferencesKeys.AD_BLOCK_ENABLED] ?: true
    }
    try {
      hashes.set(AdBlocker, longArrayOf(domainHash("127.0.0.2")))
      runBlocking { context.dataStore.edit { it[PreferencesKeys.AD_BLOCK_ENABLED] = true } }
      settings.save(origin, originalSite.copy(adBlockEnabled = true))
      context.startActivity(BrowserActivity.createIntent(context, "$origin/ad-test"))
      waitFor("ads-ready")
      tap("Fetch ad")
      waitFor("ad-blocked")
      assertFalse(reports.contains("ad-server-hit"))
      reports.clear()
      settings.save(origin, originalSite.copy(adBlockEnabled = false))
      tap("Fetch ad")
      waitFor("ad-allowed")
      assertTrue(reports.contains("ad-server-hit"))
      reports.clear()
      settings.save(origin, originalSite.copy(adBlockEnabled = true))
      runBlocking { context.dataStore.edit { it[PreferencesKeys.AD_BLOCK_ENABLED] = false } }
      tap("Fetch ad")
      waitFor("ad-allowed")
      assertTrue(reports.contains("ad-server-hit"))
      reports.clear()
      runBlocking { context.dataStore.edit { it[PreferencesKeys.AD_BLOCK_ENABLED] = true } }
      tap("Fetch ad")
      waitFor("ad-blocked")
      assertFalse(reports.contains("ad-server-hit"))
      // Visiting a blocked host deliberately must still work.
      context.startActivity(
        BrowserActivity.createIntent(context, "http://127.0.0.2:${server.localPort}/ad-test")
      )
      assertNotNull(device.wait(Until.findObject(By.text("Fetch ad")), 10000))
      instrumentation.runOnMainSync {
        assertEquals(
          "http://127.0.0.2:${server.localPort}/ad-test",
          BrowserTabStore.tabs!!.active.url,
        )
      }
      saveScreenshot("ad-filtering")
    } finally {
      hashes.set(AdBlocker, original)
      settings.save(origin, originalSite)
      runBlocking {
        context.dataStore.edit { it[PreferencesKeys.AD_BLOCK_ENABLED] = originalEnabled }
      }
    }
  }

  @Test
  fun restoringWithoutHistoryAndReloadingDuringStartupRetainsTheRealAddress() {
    var restoredId = 0L
    instrumentation.runOnMainSync {
      val tab = BrowserTabStore.addBackgroundTab("about:blank")
      restoredId = tab.id
      context
        .getSharedPreferences("gecko-tabs", android.content.Context.MODE_PRIVATE)
        .edit()
        .putString("url:${tab.id}", "$origin/restored")
        .remove("state:${tab.id}")
        .commit()
      BrowserTabTasks.open(context, tab.id)
    }
    waitFor("restore-waiting")
    instrumentation.runOnMainSync {
      assertEquals("$origin/restored", BrowserTabStore.tab(restoredId)!!.url)
    }
    device.wait(Until.findObject(By.desc("Browser menu")), 10000)!!.click()
    menuItem("Reload").click()
    assertTrue(device.wait(Until.gone(By.text("Reload")), 10000))
    instrumentation.runOnMainSync {
      assertEquals("$origin/restored", BrowserTabStore.tab(restoredId)!!.url)
    }
    reports.clear()
    releaseRestore.countDown()
    waitFor("engine")
    tap("Next route")
    waitFor("route-changed")
    eventually {
      val saved = context.getSharedPreferences("gecko-tabs", android.content.Context.MODE_PRIVATE)
      val state =
        org.mozilla.geckoview.GeckoSession.SessionState.fromString(
          saved.getString("state:$restoredId", null)
        )
      state != null &&
        runCatching { state[state.currentIndex].uri == "$origin/next" }.getOrDefault(false)
    }
    instrumentation.runOnMainSync {
      assertEquals("$origin/next", BrowserTabStore.tab(restoredId)!!.url)
      ActivityLifecycleMonitorRegistry.getInstance()
        .getActivitiesInStage(Stage.RESUMED)
        .filterIsInstance<BrowserActivity>()
        .single()
        .recreate()
    }
    assertNotNull(device.wait(Until.findObject(By.text("Export file")), 15000))
    device.wait(Until.findObject(By.desc("Browser menu")), 10000)!!.click()
    menuItem("Reload").click()
    assertTrue(device.wait(Until.gone(By.text("Reload")), 10000))
    assertNotNull(device.wait(Until.findObject(By.text("Export file")), 15000))
    instrumentation.runOnMainSync {
      assertEquals("$origin/next", BrowserTabStore.tab(restoredId)!!.url)
    }
    device.pressBack()
    eventually {
      var correct = false
      instrumentation.runOnMainSync {
        correct = BrowserTabStore.tab(restoredId)?.url == "$origin/restored"
      }
      correct
    }
    saveScreenshot("restored-after-back")
  }

  @Test
  fun killedContentProcessRecoversOnRetryAndNewNavigation() {
    var tabId = 0L
    instrumentation.runOnMainSync { tabId = BrowserTabStore.tabs!!.active.id }
    // Keep session history and website storage across a real OS process termination.
    tap("Store data")
    waitFor("storage-written")
    reports.clear()
    killContentProcesses()
    // A killed foreground page should restore itself without requiring a tap.
    waitFor("engine", 30000)
    assertNotNull(device.wait(Until.findObject(By.text("Export file")), 15000))
    tap("Read data")
    waitFor("storage-present")
    instrumentation.runOnMainSync { assertEquals("$origin/", BrowserTabStore.tab(tabId)!!.url) }
    saveScreenshot("recovered-after-kill")

    // A second kill inside the cooldown must stop instead of entering a reload loop.
    killContentProcesses()
    assertNotNull(device.wait(Until.findObject(By.text("Try again")), 15000))
    reports.clear()
    tap("Try again")
    waitFor("engine", 30000)

    killContentProcesses()
    assertNotNull(device.wait(Until.findObject(By.text("Try again")), 15000))
    reports.clear()
    // Exercise the app's same-tab address/navigation path without pressing Retry.
    instrumentation.runOnMainSync {
      context.startActivity(BrowserActivity.createNavigateIntent(context, tabId, "$origin/next"))
    }
    waitFor("engine", 30000)
    assertNotNull(device.wait(Until.findObject(By.text("Export file")), 15000))
    instrumentation.runOnMainSync { assertEquals("$origin/next", BrowserTabStore.tab(tabId)!!.url) }
    saveScreenshot("new-address-after-kill")
  }

  private fun prepareHome() {
    (context.applicationContext as SearchLauncherApp).apply {
      setAskedDefaultLauncher()
      setAskedDefaultBrowser()
      setConsent(false)
    }
    runBlocking {
      context.dataStore.edit {
        it[PreferencesKeys.ONBOARDING_PERMISSIONS_ASKED] = true
        it[PreferencesKeys.BUILT_IN_KEYBOARD] = true
      }
      com.searchlauncher.app.ui.onboarding.OnboardingManager(context).skipAll()
    }
  }

  private fun killContentProcesses() {
    val processes = device.executeShellCommand("ps -A -o PID,NAME")
    val pids =
      processes
        .lineSequence()
        .map { it.trim().split(Regex("\\s+")) }
        .filter { it.size == 2 && it[1].startsWith("${context.packageName}:tab_") }
        .map { it[0].toInt() }
        .toList()
    assertTrue("Gecko content processes must be running", pids.isNotEmpty())
    // This instrumentation scenario runs on a rooted AOSP emulator (adb root).
    pids.forEach { device.executeShellCommand("kill -9 $it") }
  }

  @Test
  fun killedBackgroundPageWaitsUntilReturningToBrowser() {
    prepareHome()
    var tabId = 0L
    instrumentation.runOnMainSync { tabId = BrowserTabStore.tabs!!.active.id }
    tap("Next route")
    waitFor("route-changed")
    instrumentation.runOnMainSync { BrowserTabTasks.openHome(context) }
    assertNotNull(device.wait(Until.findObject(By.desc("Settings")), 10000))
    eventually {
      var stopped = false
      instrumentation.runOnMainSync {
        stopped =
          ActivityLifecycleMonitorRegistry.getInstance()
            .getActivitiesInStage(Stage.STOPPED)
            .filterIsInstance<BrowserActivity>()
            .isNotEmpty()
      }
      stopped
    }
    reports.clear()
    killContentProcesses()
    SystemClock.sleep(2000)
    assertFalse("Do not restart discarded background pages", reports.any { it.contains("engine") })
    instrumentation.runOnMainSync { BrowserTabTasks.open(context, tabId) }
    waitFor("engine", 30000)
    assertNotNull(device.wait(Until.findObject(By.text("Export file")), 15000))
    instrumentation.runOnMainSync { assertEquals("$origin/next", BrowserTabStore.tab(tabId)!!.url) }
    // History survives the discard, not just the current URL.
    device.pressBack()
    eventually {
      var restored = false
      instrumentation.runOnMainSync { restored = BrowserTabStore.tab(tabId)?.url == "$origin/" }
      restored
    }
    saveScreenshot("recovered-background-tab")
  }

  @Test
  fun reloadAndFailureKeepAnOpaqueBrowserAndRetryTheFailedAddress() {
    tap("Read data")
    reports.clear()
    delayNextDocument.set(true)
    device.wait(Until.findObject(By.desc("Browser menu")), 10000)!!.click()
    menuItem("Reload").click()
    waitFor("reload-waiting")
    assertTrue(device.wait(Until.gone(By.text("Reload")), 10000))
    device.waitForIdle()
    val reloadImage =
      android.graphics.BitmapFactory.decodeFile(saveScreenshot("reload-waiting").absolutePath)
    val center = reloadImage.getPixel(reloadImage.width / 2, reloadImage.height * 3 / 4)
    assertTrue(
      "Stalled reload must keep an opaque white page",
      Color.red(center) > 230 && Color.green(center) > 230 && Color.blue(center) > 230,
    )
    reloadImage.recycle()
    releaseReload.countDown()
    waitFor("engine")
    assertNotNull(device.wait(Until.findObject(By.text("Export file")), 10000))

    val failedPort = ServerSocket(0).use { it.localPort }
    context.startActivity(
      BrowserActivity.createIntent(context, "http://localhost:$failedPort/retry")
    )
    assertNotNull(device.wait(Until.findObject(By.text("Could not open this page")), 15000))
    val retry = device.wait(Until.findObject(By.text("Try again")), 10000)!!
    instrumentation.runOnMainSync {
      assertEquals("http://localhost:$failedPort/retry", BrowserTabStore.tabs!!.active.url)
    }
    val failureImage =
      android.graphics.BitmapFactory.decodeFile(saveScreenshot("load-error").absolutePath)
    val textBounds = retry.visibleBounds
    var minLuminance = 255
    var maxLuminance = 0
    for (y in textBounds.top until textBounds.bottom) for (x in
      textBounds.left until textBounds.right) {
      val pixel = failureImage.getPixel(x, y)
      val luminance = (Color.red(pixel) + Color.green(pixel) + Color.blue(pixel)) / 3
      minLuminance = minOf(minLuminance, luminance)
      maxLuminance = maxOf(maxLuminance, luminance)
    }
    assertTrue(
      "Retry text must actually be drawn above the browser surface",
      maxLuminance - minLuminance > 50,
    )
    failureImage.recycle()
    reports.clear()
    ServerSocket(failedPort).use { recovered ->
      thread(isDaemon = true) {
        while (!recovered.isClosed) {
          val socket = runCatching { recovered.accept() }.getOrNull() ?: break
          thread(isDaemon = true) { serve(socket) }
        }
      }
      tap("Try again")
      waitFor("engine")
      assertNotNull(device.wait(Until.findObject(By.text("Export file")), 10000))
      assertFalse(device.hasObject(By.text("Could not open this page")))
    }
  }

  @Test
  fun scriptedAppLinksOpenTheDefaultAppButEmbeddedFramesDoNot() {
    val testPackage = instrumentation.context.packageName
    device.executeShellCommand(
      "pm set-app-links --package $testPackage 2 handoff.searchlauncher.test"
    )
    device.executeShellCommand(
      "pm set-app-links-user-selection --user 0 --package $testPackage true handoff.searchlauncher.test"
    )
    try {
      context.startActivity(BrowserActivity.createIntent(context, "$origin/app-link-frame"))
      assertNotNull(device.wait(Until.findObject(By.text("Embedded app link fixture")), 15000))
      SystemClock.sleep(1500)
      assertFalse(device.hasObject(By.textStartsWith("Received app link:")))
      context.startActivity(BrowserActivity.createIntent(context, "$origin/app-link-redirect"))
      assertNotNull(
        device.wait(
          Until.findObject(
            By.text(
              "Received app link: https://handoff.searchlauncher.test/app?state=exact%2Fvalue"
            )
          ),
          15000,
        )
      )
      saveScreenshot("app-link-handoff")
      device.pressBack()
    } finally {
      device.executeShellCommand(
        "pm set-app-links-user-selection --user 0 --package $testPackage false handoff.searchlauncher.test"
      )
      device.executeShellCommand(
        "pm set-app-links --package $testPackage 0 handoff.searchlauncher.test"
      )
    }
  }

  @Test
  fun recentTabsKeepTheirLiveDocumentAcrossHomeAndOtherTabs() {
    prepareHome()
    var firstId = 0L
    instrumentation.runOnMainSync { firstId = BrowserTabStore.tabs!!.active.id }
    tap("Remember in memory")
    waitFor("memory-set")
    val initialLoads = reports.count { it.startsWith("/report?engine:") }
    context.startActivity(BrowserActivity.createIntent(context, "$origin/second"))
    eventually { reports.count { it.startsWith("/report?engine:") } == initialLoads + 1 }
    var secondId = 0L
    instrumentation.runOnMainSync { secondId = BrowserTabStore.tabs!!.active.id }
    repeat(4) {
      instrumentation.runOnMainSync { BrowserTabTasks.openHome(context) }
      assertNotNull(device.wait(Until.findObject(By.desc("Settings")), 10000))
      instrumentation.runOnMainSync { BrowserTabTasks.open(context, firstId) }
      tap("Read memory")
      val count = it + 1
      eventually { reports.count { it == "/report?memory-present" } == count }
      instrumentation.runOnMainSync { BrowserTabTasks.open(context, secondId) }
      assertNotNull(device.wait(Until.findObject(By.text("Read memory")), 10000))
    }
    assertEquals(
      "Tab switches must not reload either document",
      initialLoads + 1,
      reports.count { it.startsWith("/report?engine:") },
    )
  }

  @Test
  fun downloadNotificationKeepsItsIdentityAndOpensDownloads() {
    val manager = context.getSystemService(NotificationManager::class.java)
    // Earlier fixture notifications would collapse this transfer into an automatic group.
    manager.cancelAll()
    context.startActivity(BrowserActivity.createIntent(context, "$origin/download"))
    val name = "direct-${server.localPort}.bin"
    eventually {
      manager.activeNotifications.any {
        it.notification.extras.getString("android.title") == name &&
          it.notification.extras.getInt("android.progress") in 1..99
      }
    }
    val active =
      manager.activeNotifications.first {
        it.notification.extras.getString("android.title") == name
      }
    assertTrue(active.isOngoing)
    assertEquals(100, active.notification.extras.getInt("android.progressMax"))
    assertTrue(device.openNotification())
    val progressNotification =
      device.wait(Until.findObject(By.text(name).pkg("com.android.systemui")), 10000)
    assertNotNull(progressNotification)
    SystemClock.sleep(300)
    saveScreenshot("notification-progress")
    progressNotification!!.click()
    assertNotNull(device.wait(Until.findObject(By.text("Downloads")), 10000))
    releaseDownload.countDown()
    eventually {
      manager.activeNotifications.any {
        it.tag == active.tag &&
          it.id == active.id &&
          it.notification.extras.getString("android.text") == "Download complete" &&
          !it.isOngoing
      }
    }
    val complete = manager.activeNotifications.first { it.tag == active.tag && it.id == active.id }
    assertEquals(0, complete.notification.extras.getInt("android.progressMax"))
    assertTrue(device.openNotification())
    val completedNotification =
      device.wait(Until.findObject(By.text(name).pkg("com.android.systemui")), 10000)
    assertNotNull(completedNotification)
    SystemClock.sleep(300)
    saveScreenshot("notification-complete")
    completedNotification!!.click()
    assertNotNull(device.wait(Until.findObject(By.text("Downloads")), 10000))
    tap("Done")
  }

  @Test
  fun downloadCompletionKeepsTheNewestCardAtTheTop() {
    context.startActivity(BrowserActivity.createIntent(context, "$origin/download"))
    val name = "direct-${server.localPort}.bin"
    assertNotNull(device.wait(Until.findObject(By.text(name)), 15000))
    SystemClock.sleep(1000) // Include the first history poll with older downloads.
    val top = device.findObject(By.text(name))!!.visibleBounds.top
    saveScreenshot("persistent-download-progress")
    releaseDownload.countDown()
    val deadline = SystemClock.uptimeMillis() + 2500
    var samples = 0
    while (SystemClock.uptimeMillis() < deadline) {
      val bounds = device.findObject(By.text(name))?.visibleBounds
      assertNotNull("Latest download must never disappear", bounds)
      assertEquals("The latest filename must stay visible in the same place", top, bounds!!.top)
      samples++
      SystemClock.sleep(16)
    }
    assertTrue(samples > 5)
    val completed =
      readBrowserDownloads(context.getSystemService(DownloadManager::class.java)).first {
        it.name == name
      }
    assertEquals(DownloadManager.STATUS_SUCCESSFUL, completed.status)
    assertEquals(downloadBytes.size.toLong(), completed.displayBytes)
    assertNotNull(device.wait(Until.findObject(By.text("Open file")), 10000))
    saveScreenshot("persistent-download-complete")
  }

  @Test
  fun browserSearchUsesTheHomeKeyboardAndReturnsToThePage() {
    prepareHome()
    device.wait(Until.findObject(By.text(origin.removePrefix("http://"))), 15000)!!.click()
    assertNotNull(device.wait(Until.findObject(By.desc("Space")), 10000))
    device.findObject(By.desc("q"))!!.click()
    assertEquals(
      "q",
      device.wait(Until.findObject(By.clazz("android.widget.EditText")), 10000)!!.text,
    )
    assertFalse(
      "The system IME must stay hidden for built-in search",
      device.executeShellCommand("dumpsys input_method").contains("mInputShown=true"),
    )
    saveScreenshot("browser-built-in-keyboard")
    device.pressBack()
    assertNotNull(device.wait(Until.findObject(By.text("Export file")), 10000))
    runBlocking { com.searchlauncher.app.ui.HomeKeyboardPreference.set(context, false) }
    try {
      device.wait(Until.findObject(By.text(origin.removePrefix("http://"))), 15000)!!.click()
      eventually { device.executeShellCommand("dumpsys input_method").contains("mInputShown=true") }
      assertFalse(device.hasObject(By.desc("Space").pkg(context.packageName)))
      device.pressBack()
      assertNotNull(device.wait(Until.findObject(By.text("Export file")), 10000))
    } finally {
      runBlocking { com.searchlauncher.app.ui.HomeKeyboardPreference.set(context, true) }
    }
  }

  @Test
  fun directDownloadShowsProgressAndClosesOnlyItsEmptyTab() {
    var sourceId = 0L
    instrumentation.runOnMainSync { sourceId = BrowserTabStore.tabs!!.active.id }
    context.startActivity(BrowserActivity.createIntent(context, "$origin/download"))
    assertNotNull(device.wait(Until.findObject(By.text("Downloads")), 15000))
    assertNotNull(device.wait(Until.findObject(By.text("direct-${server.localPort}.bin")), 10000))
    eventually {
      var progressing = false
      instrumentation.runOnMainSync {
        progressing =
          pendingPageDownloads.values.any { it.fraction?.let { f -> f > 0f && f < 1f } == true }
      }
      progressing
    }
    saveScreenshot("download-progress")
    var downloadId = 0L
    instrumentation.runOnMainSync { downloadId = BrowserTabStore.tabs!!.active.id }
    assertNotEquals(sourceId, downloadId)
    // Dismissing the blank download tab must not cancel its response stream.
    tap("Done")
    assertNotNull(device.wait(Until.findObject(By.text("Export file")), 10000))
    instrumentation.runOnMainSync {
      assertNull(BrowserTabStore.tab(downloadId))
      assertNotNull(BrowserTabStore.tab(sourceId))
    }
    releaseDownload.countDown()
    val manager = context.getSystemService(DownloadManager::class.java)
    var result: BrowserDownload? = null
    eventually {
      result =
        readBrowserDownloads(manager).firstOrNull { it.name == "direct-${server.localPort}.bin" }
      result?.status == DownloadManager.STATUS_SUCCESSFUL
    }
    assertEquals(downloadBytes.size.toLong(), result!!.displayBytes)
    manager.openDownloadedFile(result!!.id).use { descriptor ->
      android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).use {
        assertArrayEquals(downloadBytes, it.readBytes())
      }
    }
    assertEquals(1, reports.count { it == "download-request" })
    device.wait(Until.findObject(By.desc("Browser menu")), 10000)!!.click()
    menuItem("Downloads").click()
    val expectedSize =
      android.text.format.Formatter.formatShortFileSize(context, downloadBytes.size.toLong())
    assertNotNull(device.wait(Until.findObject(By.textStartsWith("$expectedSize ·")), 10000))
    saveScreenshot("download-complete")
    tap("Done")
    assertNotNull(device.wait(Until.findObject(By.text("Export file")), 10000))
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
    prepareHome()
    device.wait(Until.findObject(By.text("Export file")), 15000)
    assertFalse(device.hasObject(By.textContains("Gecko experiment")))
    var tabId = 0L
    instrumentation.runOnMainSync { tabId = BrowserTabStore.tabs!!.active.id }
    assertTrue(
      "Run this scenario with SearchLauncher Gecko selected as the default home app",
      context
        .getSystemService(android.app.role.RoleManager::class.java)
        .isRoleHeld(android.app.role.RoleManager.ROLE_HOME),
    )
    // Launch as the system/shell would; an app-originated explicit intent may itself be
    // matched to an existing STANDARD task and miss the real default-HOME configuration.
    device.executeShellCommand(
      "am start -a android.intent.action.MAIN -c android.intent.category.HOME " +
        "-n ${context.packageName}/com.searchlauncher.app.ui.MainActivity"
    )
    var homeTaskId = -1
    eventually {
      instrumentation.runOnMainSync {
        homeTaskId =
          ActivityLifecycleMonitorRegistry.getInstance()
            .getActivitiesInStage(Stage.RESUMED)
            .filterIsInstance<MainActivity>()
            .firstOrNull()
            ?.taskId ?: -1
      }
      homeTaskId != -1
    }
    assertNotNull(device.wait(Until.findObject(By.desc("Settings")), 10000))
    instrumentation.runOnMainSync { BrowserTabTasks.open(context, tabId) }
    // This setup launch has no app-drawn swipe preview. Let its normal task animation finish
    // before checking the subsequent gestures, including runs with 5x system animation scale.
    SystemClock.sleep(2000)
    repeat(4) {
      val button = device.wait(Until.findObject(By.descContains("open tab")), 10000)!!
      val bounds = button.visibleBounds
      val chrome = android.graphics.BitmapFactory.decodeFile(saveScreenshot("chrome").absolutePath)
      val sample = chrome.getPixel(bounds.left - 15, bounds.centerY())
      assertTrue(
        "Browser toolbar should stay light even in dark launcher theme",
        Color.red(sample) > 220 && Color.green(sample) > 220,
      )
      if (it > 0) {
        // This part of the fixture is plain white. A stale task screenshot can fade the
        // home keyboard over this otherwise empty viewport during the final handover.
        var homePixels = 0
        var samples = 0
        for (y in chrome.height * 3 / 4 until chrome.height * 17 / 20 step 8) {
          for (x in chrome.width / 5 until chrome.width * 4 / 5 step 8) {
            val pixel = chrome.getPixel(x, y)
            samples++
            if (Color.red(pixel) < 230 || Color.green(pixel) < 230 || Color.blue(pixel) < 230)
              homePixels++
          }
        }
        assertTrue(
          "Outgoing home must not fade its keyboard over the browser ($homePixels/$samples)",
          homePixels < samples / 50,
        )
      }
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
      instrumentation.runOnMainSync {
        val resumedHome =
          ActivityLifecycleMonitorRegistry.getInstance()
            .getActivitiesInStage(Stage.RESUMED)
            .filterIsInstance<MainActivity>()
            .single()
        assertEquals(
          "Swipe must resume the real HOME task, not create a STANDARD launcher task",
          homeTaskId,
          resumedHome.taskId,
        )
      }
      val homeSettings = device.wait(Until.findObject(By.desc("Settings")), 10000)
      saveScreenshot("swipe-home-$it")
      device.dumpWindowHierarchy(
        java.io.File(context.getExternalFilesDir(null), "gecko-swipe-home.xml")
      )
      assertNotNull("Home search bar must be visible", homeSettings)
      val homeBar = homeSettings!!.visibleBounds
      // Return with the actual home chrome gesture, not a programmatic tab launch.
      device.swipe(
        device.displayWidth / 5,
        homeBar.centerY(),
        device.displayWidth * 4 / 5,
        homeBar.centerY(),
        40,
      )
      assertNotNull(device.wait(Until.findObject(By.desc("Browser menu")), 10000))
      instrumentation.runOnMainSync {
        val preview = HomeSwipePreview.image
        assertNotNull("Home preview should be prepared before the swipe", preview)
        val bitmap =
          preview!!.asAndroidBitmap().copy(android.graphics.Bitmap.Config.ARGB_8888, false)
        var browserPixels = 0
        for (y in 0 until bitmap.height step 4) for (x in 0 until bitmap.width step 4) {
          val pixel = bitmap.getPixel(x, y)
          if (
            Color.alpha(pixel) > 240 &&
              Color.red(pixel) < 20 &&
              kotlin.math.abs(Color.green(pixel) - 150) < 15 &&
              kotlin.math.abs(Color.blue(pixel) - 136) < 15
          )
            browserPixels++
        }
        java.io
          .File(context.getExternalFilesDir(null), "gecko-home-preview-$it.png")
          .outputStream()
          .use { output ->
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output)
          }
        bitmap.recycle()
        assertTrue(
          "Home preview must not contain the browser fixture ($browserPixels teal pixels)",
          browserPixels < 100,
        )
      }
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
      if (path == "/restored") {
        reports += "restore-waiting"
        releaseRestore.await(30, java.util.concurrent.TimeUnit.SECONDS)
      }
      if (path == "/" && delayNextDocument.compareAndSet(true, false)) {
        reports += "reload-waiting"
        releaseReload.await(30, java.util.concurrent.TimeUnit.SECONDS)
      }
      if (path == "/app-link-frame" || path == "/app-link-redirect") {
        val body =
          if (path.endsWith("frame"))
            "<html><body>Embedded app link fixture<iframe src='https://handoff.searchlauncher.test/app'></iframe></body></html>"
          else
            "<html><body>Opening app<script>setTimeout(() => location.href='$origin/app-link-location', 800)</script></body></html>"
        val bytes = body.toByteArray()
        it
          .getOutputStream()
          .write(
            ("HTTP/1.1 200 OK\r\nContent-Type: text/html\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n")
              .toByteArray() + bytes
          )
        return
      }
      if (path == "/app-link-location") {
        it
          .getOutputStream()
          .write(
            "HTTP/1.1 302 Found\r\nLocation: https://handoff.searchlauncher.test/app?state=exact%2Fvalue\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
              .toByteArray()
          )
        return
      }
      if (path == "/download") {
        reports += "download-request"
        val output = it.getOutputStream()
        output.write(
          ("HTTP/1.1 200 OK\r\nContent-Type: application/octet-stream\r\n" +
              "Content-Disposition: attachment; filename=direct-${server.localPort}.bin\r\n" +
              "Content-Length: ${downloadBytes.size}\r\nConnection: close\r\n\r\n")
            .toByteArray()
        )
        output.write(downloadBytes, 0, 768 * 1024)
        output.flush()
        releaseDownload.await(45, java.util.concurrent.TimeUnit.SECONDS)
        output.write(downloadBytes, 768 * 1024, downloadBytes.size - 768 * 1024)
        output.flush()
        return
      }
      var mime = "text/html"
      val body =
        when {
          path.startsWith("/ad-resource") -> {
            reports += "ad-server-hit"
            "ad resource"
          }
          path == "/ad-test" ->
            """<!doctype html><meta name="viewport" content="width=device-width"><title>Ad filtering fixture</title>
            <button style="font-size:24px;padding:24px" onclick="fetch('http://127.0.0.2:${server.localPort}/ad-resource?t='+Date.now(),{mode:'no-cors'}).then(()=>report('ad-allowed'),()=>report('ad-blocked'))">Fetch ad</button>
            <script>function report(s){document.body.append(s);fetch('/report?'+s)}report('ads-ready')</script>"""
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
          <button onclick="window.volatileMarker=true;report('memory-set')">Remember in memory</button>
          <button onclick="report(window.volatileMarker?'memory-present':'memory-missing')">Read memory</button>
          <button onclick="history.pushState({},'','/next');report('route-changed')">Next route</button>
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
          "HTTP/1.1 200 OK\r\nContent-Type: $mime; charset=utf-8\r\nCache-Control: no-store\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n"
            .toByteArray()
        )
        write(bytes)
        flush()
      }
    }
  }
}
