package com.searchlauncher.app.ui.browser

import android.graphics.Bitmap
import android.net.Uri
import android.util.Base64
import android.util.Log
import androidx.lifecycle.lifecycleScope
import com.searchlauncher.app.SearchLauncherApp
import com.searchlauncher.app.data.faviconHost
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import org.mozilla.gecko.util.ImageDecoder
import org.mozilla.geckoview.*

/** Connects Gecko's page metadata to the same icons used by launcher favorites and recents. */
internal class GeckoFavicons(
  private val activity: BrowserActivity,
  private val tab: BrowserTab,
  private val session: GeckoSession,
) : WebExtension.MessageDelegate {
  private var job: Job? = null
  private var requestKey: String? = null

  fun restoreCached() {
    val url = tab.url
    activity.lifecycleScope.launch {
      val icon = repository()?.loadFavicon(url) ?: return@launch
      if (faviconHost(tab.url) == faviconHost(url) && tab.favicon == null) applyIcon(icon)
    }
  }

  override fun onMessage(
    nativeApp: String,
    message: Any,
    sender: WebExtension.MessageSender,
  ): GeckoResult<Any>? {
    if (
      nativeApp != "site_icons" ||
        sender.session !== session ||
        !sender.isTopLevel ||
        message !is JSONObject
    )
      return null
    val url = message.optString("url")
    if (Uri.parse(url).scheme !in listOf("http", "https") || sender.url != url || tab.url != url)
      return null
    val array = message.optJSONArray("icons") ?: return null
    val urls =
      (0 until minOf(array.length(), 9))
        .map { array.optString(it) }
        .filter {
          (Uri.parse(it).scheme in listOf("http", "https") && it.length <= 8192) ||
            (it.startsWith("data:image/") && it.length <= 350000)
        }
        .distinct()
    val key = "$url|${urls.joinToString()}"
    if (requestKey == key) return null
    requestKey = key
    job?.cancel()
    job =
      activity.lifecycleScope.launch {
        for (iconUrl in urls) {
          val icon = withTimeoutOrNull(5000) { loadIcon(iconUrl) } ?: continue
          if (tab.url != url || !session.isOpen) return@launch
          applyIcon(icon)
          repository()?.saveFavicon(url, icon)
          break
        }
      }
    return null
  }

  private fun repository() = (activity.application as SearchLauncherApp).searchRepositoryOrNull

  private fun applyIcon(icon: Bitmap) {
    tab.favicon = icon
    activity.publishTaskDescription(tab.title, icon, tab.frameColorArgb)
  }

  private suspend fun loadIcon(url: String): Bitmap? {
    return try {
      val data =
        if (url.startsWith("data:image/")) url
        else {
          val response =
            GeckoWebExecutor(GeckoEnvironment.runtime(activity))
              .fetch(WebRequest.Builder(url).build(), GeckoWebExecutor.FETCH_FLAGS_ANONYMOUS)
              .awaitIconResult()
          val body = response.body ?: return null
          val bytes =
            withContext(Dispatchers.IO) {
              body.use { stream ->
                if (response.statusCode !in 200..299) return@use null
                val result = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                  val count = stream.read(buffer)
                  if (count < 0) break
                  if (result.size() + count > 256 * 1024) return@use null
                  result.write(buffer, 0, count)
                }
                result.toByteArray()
              }
            } ?: return null
          // Gecko's decoder supports ICO and SVG in addition to Android's bitmap formats.
          val mime =
            response.headers.entries
              .firstOrNull { it.key.equals("content-type", true) }
              ?.value
              ?.substringBefore(';')
              ?.takeIf { it.startsWith("image/") } ?: "image/x-icon"
          "data:$mime;base64,${Base64.encodeToString(bytes, Base64.NO_WRAP)}"
        }
      ImageDecoder.instance().decode(data, 96).awaitIconResult()
    } catch (cancelled: kotlinx.coroutines.CancellationException) {
      throw cancelled
    } catch (failure: Exception) {
      // Broken candidates are ordinary: try the next declared icon or /favicon.ico.
      Log.d("GeckoFavicons", "Icon candidate unavailable", failure)
      null
    }
  }

  fun close() {
    job?.cancel()
  }
}

internal suspend fun <T> GeckoResult<T>.awaitIconResult(): T =
  suspendCancellableCoroutine { continuation ->
    accept(
      { value ->
        if (continuation.isActive) {
          if (value != null) continuation.resume(value)
          else continuation.resumeWithException(IllegalStateException("Empty Gecko result"))
        } else {
          // A timeout/navigation can win while Gecko finishes a fetch or decode.
          if (value is WebResponse) value.body?.close()
          if (value is Bitmap) value.recycle()
        }
      },
      { failure ->
        if (continuation.isActive)
          continuation.resumeWithException(
            failure ?: IllegalStateException("Gecko operation failed")
          )
      },
    )
  }
