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

/**
 * One notification identity from the first byte through completion; never launches an APK.
 *
 * A [foreground] notification is posted without a tag under [id], so [DownloadService] can make it
 * its foreground notification, and carries Pause, Resume and Cancel actions.
 */
internal class DownloadNotification(
  context: Context,
  private val key: String,
  name: String,
  private val foreground: Boolean = false,
) {
  private val app = context.applicationContext

  /** The id this notification is posted under; stable for the download's lifetime. */
  val id: Int = if (foreground) foregroundNotificationId(key) else 1

  /** The notification as last built, for [DownloadService.startForeground]. */
  val current: Notification?
    get() = latest

  private val manager = app.getSystemService(NotificationManager::class.java)
  private var lastUpdate = 0L
  private var title = name
  private var determinate = false
  private var latest: Notification? = null
  private var statusShown = false

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

  fun progress(
    name: String,
    fraction: Float?,
    transfer: DownloadTransfer? = null,
    pausable: Boolean = false,
  ) {
    val now = SystemClock.elapsedRealtime()
    if (
      latest != null &&
        name == title &&
        determinate == (fraction != null) &&
        now - lastUpdate < 500 &&
        !statusShown
    )
      return
    statusShown = false
    determinate = fraction != null
    lastUpdate = now
    title = name
    val percent = fraction?.let { (it.coerceIn(0f, 1f) * 100).toInt() }
    show(
      builder()
        .setContentText(
          transfer?.description() ?: percent?.let { "Downloading · $it%" } ?: "Downloading…"
        )
        // Android hides contentText in its collapsed progress template.
        .setSubText(transfer?.description())
        .setOngoing(true)
        .setProgress(100, percent ?: 0, percent == null)
        .apply { if (pausable) addActions(working = true) }
    )
  }

  /**
   * A state without live progress: waiting for the network, retrying, paused or failed. An
   * [ongoing] one still belongs to a running download; otherwise it offers Resume.
   */
  fun status(name: String, text: String, fraction: Float?, ongoing: Boolean) {
    title = name
    statusShown = true
    lastUpdate = SystemClock.elapsedRealtime()
    val percent = fraction?.let { (it.coerceIn(0f, 1f) * 100).toInt() }
    show(
      builder().setContentText(text).setSubText(text.takeIf { ongoing }).setOngoing(ongoing).apply {
        if (ongoing) setProgress(100, percent ?: 0, percent == null)
        if (!ongoing) setSmallIcon(android.R.drawable.stat_sys_warning)
        addActions(working = ongoing)
      }
    )
  }

  /** Post the last state again, so it is no longer tied to the foreground service. */
  fun repost() = post()

  private fun NotificationCompat.Builder.addActions(working: Boolean) {
    if (!foreground) return
    if (working)
      addAction(0, "Pause", DownloadActionReceiver.intent(app, key, DownloadActionReceiver.PAUSE))
    else addAction(0, "Resume", DownloadService.resumeIntent(app, key))
    addAction(0, "Cancel", DownloadActionReceiver.intent(app, key, DownloadActionReceiver.CANCEL))
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
    if (foreground) manager.cancel(id) else manager.cancel("download:$key", 1)
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
          if (foreground) manager.notify(id, it) else manager.notify("download:$key", 1, it)
        } catch (_: SecurityException) {}
      }
  }

  companion object {
    const val CHANNEL = "browser-downloads"

    /** Kept clear of small ids other notifications in the app use. */
    fun foregroundNotificationId(key: String): Int = (key.hashCode() and 0x3fffffff) or 0x40000000
  }
}
