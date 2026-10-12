package com.searchlauncher.app.ui.browser

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class DownloadNotificationTest {
  private val app: Application = RuntimeEnvironment.getApplication()
  private val manager = app.getSystemService(NotificationManager::class.java)

  @Test
  fun progressShowsSpeedAndRemainingThenClearsOnCompletion() {
    shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    val notification = DownloadNotification(app, "stats", "file.zip")
    notification.progress("file.zip", 0.25f, DownloadTransfer(1_000_000, 4_000_000, 500_000.0))
    val active = manager.activeNotifications.single().notification
    assertTrue(active.extras.getString("android.text")!!.contains("KB/s"))
    assertTrue(active.extras.getString("android.text")!!.contains("MB left"))
    assertTrue(active.extras.getString("android.subText")!!.contains("MB left"))
    assertEquals(25, active.extras.getInt("android.progress"))
    notification.complete("file.zip")
    assertEquals(
      "Download complete",
      manager.activeNotifications.single().notification.extras.getString("android.text"),
    )
    notification.cancel()
    assertTrue(manager.activeNotifications.isEmpty())
  }

  @Test
  fun parallelTransfersAndFailureDoNotOverwriteEachOther() {
    shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    val first = DownloadNotification(app, "first", "one.apk")
    val second = DownloadNotification(app, "second", "two.apk")
    first.progress("one.apk", 0.5f)
    second.failed()
    val notifications = manager.activeNotifications.associateBy { it.tag }
    assertEquals(2, notifications.size)
    assertEquals(
      50,
      notifications.getValue("download:first").notification.extras.getInt("android.progress"),
    )
    assertTrue(notifications.getValue("download:first").isOngoing)
    assertEquals(
      "Download failed",
      notifications.getValue("download:second").notification.extras.getString("android.text"),
    )
    assertFalse(notifications.getValue("download:second").isOngoing)
  }

  @Test
  fun deniedPermissionDoesNotPreventCompletion() {
    shadowOf(app).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
    val notification = DownloadNotification(app, "denied", "file.apk")
    notification.progress("file.apk", 0.5f)
    notification.complete("file.apk")
    assertTrue(manager.activeNotifications.isEmpty())
  }
}
