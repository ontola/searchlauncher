package com.searchlauncher.app.ui.browser

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

/** One notification identity from the first byte through completion; never launches an APK. */
internal class DownloadNotification(context: Context, private val key: String, name: String) {
  private val app = context.applicationContext
  private val manager = app.getSystemService(NotificationManager::class.java)
  private var lastUpdate = 0L
  private var title = name
  private var determinate = false
  private var latest: Notification? = null

  init {
    manager.createNotificationChannel(
      NotificationChannel(CHANNEL, "Downloads", NotificationManager.IMPORTANCE_LOW)
    )
    progress(name, null)
    if (Build.VERSION.SDK_INT >= 33 && !allowed()) {
      val activity =
        generateSequence(context) { (it as? ContextWrapper)?.baseContext }
          .filterIsInstance<ComponentActivity>()
          .firstOrNull()
      val preferences = app.getSharedPreferences("download-notifications", Context.MODE_PRIVATE)
      if (activity != null && !preferences.getBoolean("asked", false)) {
        preferences.edit().putBoolean("asked", true).apply()
        lateinit var launcher: androidx.activity.result.ActivityResultLauncher<String>
        launcher =
          activity.activityResultRegistry.register(
            "download-notification:$key",
            ActivityResultContracts.RequestPermission(),
          ) { granted ->
            launcher.unregister()
            if (granted) post()
          }
        launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        // Registration is owned by this Activity; no Activity is held by the download.
      }
    }
  }

  fun progress(name: String, fraction: Float?) {
    val now = SystemClock.elapsedRealtime()
    if (
      latest != null && name == title && determinate == (fraction != null) && now - lastUpdate < 500
    )
      return
    determinate = fraction != null
    lastUpdate = now
    title = name
    val percent = fraction?.let { (it.coerceIn(0f, 1f) * 100).toInt() }
    show(
      builder()
        .setContentText(percent?.let { "Downloading · $it%" } ?: "Downloading…")
        .setOngoing(true)
        .setProgress(100, percent ?: 0, percent == null)
    )
  }

  fun complete(name: String) {
    title = name
    show(
      builder()
        .setSmallIcon(android.R.drawable.stat_sys_download_done)
        .setContentText("Download complete")
        .setAutoCancel(true)
    )
  }

  fun cancel() {
    latest = null
    manager.cancel("download:$key", 1)
  }

  fun failed() {
    show(builder().setContentText("Download failed").setAutoCancel(true))
  }

  private fun builder() =
    NotificationCompat.Builder(app, CHANNEL)
      .setSmallIcon(android.R.drawable.stat_sys_download)
      .setContentTitle(title)
      .setOnlyAlertOnce(true)
      .setCategory(NotificationCompat.CATEGORY_PROGRESS)
      .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
      .setContentIntent(
        PendingIntent.getActivity(
          app,
          0,
          Intent(app, DownloadsActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
          PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
      )

  private fun show(builder: NotificationCompat.Builder) {
    latest = builder.build()
    post()
  }

  private fun allowed() =
    Build.VERSION.SDK_INT < 33 ||
      ContextCompat.checkSelfPermission(app, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED

  private fun post() {
    if (allowed())
      latest?.let {
        // Permission can be revoked between the check and posting; never fail the file transfer.
        try {
          manager.notify("download:$key", 1, it)
        } catch (_: SecurityException) {}
      }
  }

  companion object {
    const val CHANNEL = "browser-downloads"
  }
}
