package com.searchlauncher.app.ui.browser

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import org.mozilla.geckoview.WebNotification
import org.mozilla.geckoview.WebNotificationDelegate

/**
 * Local/service-worker notifications only. Remote Web Push needs a separately provisioned service.
 */
internal class GeckoNotifications(private val context: Context) : WebNotificationDelegate {
  private val handler = Handler(Looper.getMainLooper())

  override fun onShowNotification(notification: WebNotification) {
    handler.post {
      val manager = context.getSystemService(NotificationManager::class.java)
      if (
        notification.privateBrowsing ||
          !manager.areNotificationsEnabled() ||
          (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
              PackageManager.PERMISSION_GRANTED)
      ) {
        notification.dismiss()
        return@post
      }
      manager.createNotificationChannel(
        NotificationChannel(
          CHANNEL,
          "Website notifications",
          NotificationManager.IMPORTANCE_DEFAULT,
        )
      )
      val key = notification.origin + "\n" + notification.tag
      val data =
        Uri.Builder().scheme("gecko-notification").authority("event").appendPath(key).build()
      val click =
        Intent(context, GeckoNotificationActivity::class.java)
          .setData(data)
          .putExtra(EXTRA, notification)
      val dismiss =
        Intent(context, GeckoNotificationDismissReceiver::class.java)
          .setData(data)
          .putExtra(EXTRA, notification)
      val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
      manager.notify(
        key,
        1,
        NotificationCompat.Builder(context, CHANNEL)
          .setSmallIcon(android.R.drawable.ic_dialog_info)
          .setContentTitle(notification.title ?: "Website notification")
          .setContentText(notification.text)
          .setSubText(Uri.parse(notification.source ?: notification.origin).host)
          .setStyle(NotificationCompat.BigTextStyle().bigText(notification.text))
          .setAutoCancel(true)
          .setSilent(notification.silent)
          .setContentIntent(PendingIntent.getActivity(context, 0, click, flags))
          .setDeleteIntent(PendingIntent.getBroadcast(context, 0, dismiss, flags))
          .build(),
      )
      notification.show()
    }
  }

  override fun onCloseNotification(notification: WebNotification) {
    handler.post {
      context
        .getSystemService(NotificationManager::class.java)
        .cancel(notification.origin + "\n" + notification.tag, 1)
      notification.dismiss()
    }
  }

  companion object {
    const val CHANNEL = "gecko-websites"
    const val EXTRA = "web_notification"
  }
}

class GeckoNotificationActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    @Suppress("DEPRECATION")
    val notification = intent.getParcelableExtra<WebNotification>(GeckoNotifications.EXTRA)
    if (notification != null) {
      GeckoEnvironment.runtime(this)
      notification.click()
      notification.dismiss()
      // A safe fallback also works if the originating tab or process has gone away.
      val source = notification.source
      if (source != null && Uri.parse(source).scheme in listOf("http", "https")) {
        val app = InstalledWebApps.forUrl(this, source)
        val existing = BrowserTabStore.tabs?.items?.firstOrNull { it.url == source }
        if (app != null) startActivity(InstalledWebApps.launchIntent(this, app))
        else if (existing != null) BrowserTabTasks.open(this, existing.id)
        else startActivity(BrowserActivity.createIntent(this, source))
      }
    }
    finish()
  }
}

class GeckoNotificationDismissReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    @Suppress("DEPRECATION")
    val notification = intent.getParcelableExtra<WebNotification>(GeckoNotifications.EXTRA)
    GeckoEnvironment.runtime(context)
    notification?.dismiss()
  }
}
