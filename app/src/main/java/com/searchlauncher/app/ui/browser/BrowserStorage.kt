package com.searchlauncher.app.ui.browser

import android.webkit.WebStorage
import androidx.webkit.WebStorageCompat
import androidx.webkit.WebViewFeature
import java.net.URI
import java.util.Locale
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout

internal data class SiteStorage(val domain: String, val bytes: Long)

/**
 * Group protocols and ports of the same host; never guess registrable domains by splitting dots.
 */
internal fun groupSiteStorage(origins: List<Pair<String, Long>>): List<SiteStorage> =
  origins
    .mapNotNull { (origin, bytes) ->
      val uri = runCatching { URI(origin) }.getOrNull()
      val host = uri?.host?.lowercase(Locale.ROOT)
      if (uri?.scheme !in listOf("http", "https") || host.isNullOrBlank()) null
      else SiteStorage(host, bytes.coerceAtLeast(0))
    }
    .groupBy { it.domain }
    .map { (domain, entries) -> SiteStorage(domain, entries.sumOf { it.bytes }) }
    .sortedWith(compareByDescending<SiteStorage> { it.bytes }.thenBy { it.domain })

/** Called on the UI thread. Only accesses the normal browsing process's WebStorage. */
internal class BrowserStorage {
  val canClear: Boolean
    get() = WebViewFeature.isFeatureSupported(WebViewFeature.DELETE_BROWSING_DATA)

  suspend fun load(): List<SiteStorage> =
    withTimeout(15_000) {
      suspendCancellableCoroutine { continuation ->
        WebStorage.getInstance().getOrigins { values ->
          val origins = values.values.filterIsInstance<WebStorage.Origin>()
          if (continuation.isActive) {
            continuation.resume(groupSiteStorage(origins.map { it.origin to it.usage }))
          }
        }
      }
    }

  suspend fun clear(domain: String?) =
    withTimeout(30_000) {
      check(canClear) { "Update Android System WebView to clear website data." }
      suspendCancellableCoroutine<Unit> { continuation ->
        val complete = Runnable { if (continuation.isActive) continuation.resume(Unit) }
        if (domain == null) WebStorageCompat.deleteBrowsingData(WebStorage.getInstance(), complete)
        else WebStorageCompat.deleteBrowsingDataForSite(WebStorage.getInstance(), domain, complete)
      }
    }
}
