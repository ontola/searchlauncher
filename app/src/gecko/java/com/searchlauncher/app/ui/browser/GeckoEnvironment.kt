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
  private var iconExtension:
    org.mozilla.geckoview.GeckoResult<org.mozilla.geckoview.WebExtension>? =
    null
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

  fun attachMetadata(
    context: Context,
    session: GeckoSession,
    delegate: GeckoFavicons?,
    appearance: GeckoAppearance,
    ready: () -> Unit,
  ) {
    val runtime = runtime(context)
    val extension =
      iconExtension
        ?: runtime.webExtensionController
          .ensureBuiltIn("resource://android/assets/site-icons/", "site-icons@searchlauncher.eu")
          .then { installed ->
            runtime.webExtensionController.setAllowedInPrivateBrowsing(installed!!, true)
          }
          .also { iconExtension = it }
    extension.accept(
      { installed ->
        if (session.isOpen) {
          if (installed != null) {
            if (delegate != null)
              session.webExtensionController.setMessageDelegate(installed, delegate, "site_icons")
            session.webExtensionController.setMessageDelegate(
              installed,
              appearance,
              "page_appearance",
            )
          }
          ready()
        }
      },
      { failure ->
        android.util.Log.w("GeckoFavicons", "Could not install page icon bridge", failure)
        if (session.isOpen) ready()
      },
    )
  }

  // Visibility controls rendering/timers; priority controls how readily Android reclaims a page.
  // Keep a bounded working set protected when the user switches tabs or returns to the launcher.
  private val recentSessions = linkedSetOf<GeckoSession>()

  fun retainRecent(session: GeckoSession) {
    recentSessions.remove(session)
    recentSessions.add(session)
    session.setPriorityHint(GeckoSession.PRIORITY_HIGH)
    while (recentSessions.size > 6) {
      val oldest = recentSessions.first()
      recentSessions.remove(oldest)
      oldest.setPriorityHint(GeckoSession.PRIORITY_DEFAULT)
    }
  }

  fun releaseRecent(session: GeckoSession) {
    recentSessions.remove(session)
    session.setPriorityHint(GeckoSession.PRIORITY_DEFAULT)
  }

  fun take(id: Long): GeckoSession? = sessions.remove(id)

  // The opener must return an unopened session to Gecko, which loads it and preserves
  // window.opener.
  fun createPopup(id: Long, privateMode: Boolean): GeckoSession =
    GeckoSession(GeckoSessionSettings.Builder().usePrivateMode(privateMode).build()).also {
      sessions[id] = it
    }
}
