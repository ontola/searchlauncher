package com.searchlauncher.app.ui.browser

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Keeps the process in the foreground while [ResumableDownloads] has work, so a large download
 * keeps going after the browser closes or the screen turns off. It shows the oldest working
 * download's own notification instead of a separate one, and stops once nothing is left.
 */
class DownloadService : Service() {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
  private var watcher: Job? = null
  private var primary: String? = null
  private var primaryNotification: DownloadNotification? = null
  private var foreground = false

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    // startForegroundService() requires startForeground() promptly, even if there is nothing left.
    goForeground(ResumableDownloads.working.value.firstOrNull())
    ResumableDownloads.load(this)
    if (intent?.action == ACTION_RESUME) {
      intent.getStringExtra(EXTRA_KEY)?.let { ResumableDownloads.resume(this, it) }
    } else ResumableDownloads.resumeInterrupted(this)
    if (watcher == null) watcher = scope.launch { ResumableDownloads.working.collect { sync(it) } }
    return START_NOT_STICKY
  }

  private fun sync(working: List<String>) {
    if (working.isEmpty()) {
      stop()
      return
    }
    if (primary !in working) goForeground(working.first())
  }

  private fun goForeground(key: String?) {
    val previous = primaryNotification
    val notification = key?.let { ResumableDownloads.notificationFor(it) }
    val built = notification?.current ?: placeholder()
    val id = notification?.id ?: PLACEHOLDER_ID
    try {
      ServiceCompat.startForeground(this, id, built, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
      foreground = true
    } catch (error: Exception) {
      // Android refuses when the app is in the background (Android 12+). Transfers still run
      // while the process lives; the next time the app is opened they are resumed.
      Log.w(TAG, "Could not keep downloads in the foreground", error)
    }
    primary = key
    primaryNotification = notification
    // Moving the foreground role removes the old notification: show its latest state again.
    if (previous != null && previous !== notification) previous.repost()
  }

  private fun stop() {
    if (foreground) ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_DETACH)
    foreground = false
    primaryNotification?.repost()
    primaryNotification = null
    primary = null
    getSystemService(NotificationManager::class.java).cancel(PLACEHOLDER_ID)
    stopSelf()
  }

  /** Android 15 limits data-sync services to six hours a day. */
  override fun onTimeout(startId: Int, fgsType: Int) {
    ResumableDownloads.pauseAll("Android paused it after a long time running. Tap Resume.")
    stop()
  }

  override fun onDestroy() {
    scope.cancel()
    watcher = null
    super.onDestroy()
  }

  private fun placeholder(): Notification {
    getSystemService(NotificationManager::class.java)
      .createNotificationChannel(
        NotificationChannel(
          DownloadNotification.CHANNEL,
          "Downloads",
          NotificationManager.IMPORTANCE_LOW,
        )
      )
    return NotificationCompat.Builder(this, DownloadNotification.CHANNEL)
      .setSmallIcon(android.R.drawable.stat_sys_download)
      .setContentTitle("Downloads")
      .setContentText("Preparing download…")
      .setOngoing(true)
      .setCategory(NotificationCompat.CATEGORY_PROGRESS)
      .build()
  }

  companion object {
    private const val TAG = "DownloadService"
    private const val PLACEHOLDER_ID = 0x3fffffff
    private const val ACTION_RESUME = "com.searchlauncher.app.download.RESUME"
    private const val EXTRA_KEY = "key"

    /** Start (or keep) the service; harmless when it already runs or Android refuses. */
    fun ensureRunning(context: Context) {
      try {
        ContextCompat.startForegroundService(context, Intent(context, DownloadService::class.java))
      } catch (error: Exception) {
        Log.w(TAG, "Could not start the download service", error)
      }
    }

    /** A notification action may start a foreground service even while the app is closed. */
    fun resumeIntent(context: Context, key: String): PendingIntent =
      PendingIntent.getForegroundService(
        context,
        key.hashCode(),
        Intent(context, DownloadService::class.java)
          .setAction(ACTION_RESUME)
          .putExtra(EXTRA_KEY, key),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )
  }
}

/** Pause and Cancel from a download notification. A broadcast works whether or not the UI runs. */
class DownloadActionReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    val key = intent.getStringExtra(EXTRA_KEY) ?: return
    ResumableDownloads.load(context)
    when (intent.action) {
      PAUSE -> ResumableDownloads.pause(key)
      CANCEL -> ResumableDownloads.cancel(key)
    }
  }

  companion object {
    const val PAUSE = "com.searchlauncher.app.download.PAUSE"
    const val CANCEL = "com.searchlauncher.app.download.CANCEL"
    private const val EXTRA_KEY = "key"

    fun intent(context: Context, key: String, action: String): PendingIntent =
      PendingIntent.getBroadcast(
        context,
        (action + key).hashCode(),
        Intent(context, DownloadActionReceiver::class.java)
          .setAction(action)
          .putExtra(EXTRA_KEY, key),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )
  }
}
