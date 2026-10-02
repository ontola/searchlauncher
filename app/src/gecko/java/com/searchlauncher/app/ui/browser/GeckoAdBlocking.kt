package com.searchlauncher.app.ui.browser

import android.app.Application
import android.content.Context
import android.util.Log
import com.searchlauncher.app.ui.PreferencesKeys
import com.searchlauncher.app.ui.dataStore
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.WebExtension

/** Routes Gecko's network requests through the same filter list and settings as WebView. */
internal object GeckoAdBlocking {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
  private var installation: Deferred<Unit>? = null
  private var enabled = true

  fun prepare(context: Context, ready: () -> Unit) {
    val app = context.applicationContext
    val pending =
      installation
        ?: scope
          .async {
            enabled = app.dataStore.data.first()[PreferencesKeys.AD_BLOCK_ENABLED] ?: true
            if (enabled) AdBlocker.ensureLoaded(app)
            scope.launch {
              app.dataStore.data.collect {
                enabled = it[PreferencesKeys.AD_BLOCK_ENABLED] ?: true
                if (enabled) AdBlocker.ensureLoaded(app)
              }
            }
            val privateMode = Application.getProcessName().endsWith(":incognito")
            val settings = BrowserSiteSettingsStore(app, privateMode)
            val controller = GeckoEnvironment.runtime(app).webExtensionController
            val extension =
              controller
                .ensureBuiltIn(
                  "resource://android/assets/ad-blocking/",
                  "ad-blocking@searchlauncher.eu",
                )
                .awaitResult()
            extension.setMessageDelegate(
              object : WebExtension.MessageDelegate {
                override fun onMessage(
                  nativeApp: String,
                  message: Any,
                  sender: WebExtension.MessageSender,
                ): GeckoResult<Any>? {
                  // Only this extension's privileged background script can ask; no content-script
                  // bridge.
                  if (
                    nativeApp != "ad_blocking" || sender.session != null || message !is JSONObject
                  )
                    return null
                  val blocked =
                    enabled &&
                      settings.load(message.optString("pageUrl")).adBlockEnabled &&
                      AdBlocker.shouldBlock(message.optString("url"))
                  return GeckoResult.fromValue(blocked as Any)
                }
              },
              "ad_blocking",
            )
            controller.setAllowedInPrivateBrowsing(extension, true).awaitResult()
            Unit
          }
          .also { installation = it }
    scope.launch {
      runCatching { pending.await() }
        .onFailure { Log.e("GeckoAdBlocking", "Could not initialize ad filtering", it) }
      ready()
    }
  }
}

private suspend fun <T : Any> GeckoResult<T>.awaitResult(): T =
  suspendCancellableCoroutine { continuation ->
    accept(
      { value ->
        if (continuation.isActive) {
          if (value != null) continuation.resume(value)
          else
            continuation.resumeWithException(IllegalStateException("Extension returned no result"))
        }
      },
      {
        if (continuation.isActive)
          continuation.resumeWithException(it ?: IllegalStateException("Extension failed"))
      },
    )
  }
