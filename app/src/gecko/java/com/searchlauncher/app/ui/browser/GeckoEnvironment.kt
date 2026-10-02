package com.searchlauncher.app.ui.browser

import android.app.Application
import android.content.Context
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings

/** Created on first browser use, never on the launcher's startup path. One runtime per process. */
internal object GeckoEnvironment {
  private var instance: GeckoRuntime? = null
  private val sessions = mutableMapOf<Long, GeckoSession>()

  fun runtime(context: Context): GeckoRuntime =
    instance
      ?: run {
        val privateProcess = Application.getProcessName().endsWith(":incognito")
        val profile =
          java.io.File(context.filesDir, if (privateProcess) "gecko-private" else "gecko")
        profile.mkdirs()
        GeckoRuntime.create(
            context.applicationContext,
            GeckoRuntimeSettings.Builder()
              .arguments(arrayOf("-profile", profile.absolutePath))
              .remoteDebuggingEnabled(false)
              .consoleOutput(true)
              .build(),
          )
          .also {
            if (!privateProcess)
              it.webNotificationDelegate = GeckoNotifications(context.applicationContext)
            // Explicitly decline remote subscriptions until a transport is configured. Register
            // the controller so service-worker cleanup can still unsubscribe without hanging.
            it.webPushController.setDelegate(object : org.mozilla.geckoview.WebPushDelegate {})
            instance = it
          }
      }

  fun take(id: Long): GeckoSession? = sessions.remove(id)

  // The opener must return an unopened session to Gecko, which loads it and preserves
  // window.opener.
  fun createPopup(id: Long, privateMode: Boolean): GeckoSession =
    GeckoSession(GeckoSessionSettings.Builder().usePrivateMode(privateMode).build()).also {
      sessions[id] = it
    }
}
