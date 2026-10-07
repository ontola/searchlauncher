package com.searchlauncher.app.ui.browser

import android.media.MediaCrypto
import android.media.MediaDrm
import android.media.NotProvisionedException
import android.util.Log
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Gecko 157 starts its decoder before first-use MediaDrm provisioning has finished. That decoder
 * keeps a null MediaCrypto even after the license arrives, and queueSecureInputBuffer then fails.
 * Prepare Android's DRM session after user consent, before granting Gecko access to the key system.
 */
internal object GeckoProtectedMedia {
  private val widevine = UUID(0xedef8ba979d64aceUL.toLong(), 0xa3c827dcd51d21edUL.toLong())
  private val mutex = Mutex()

  // Recheck each time: provisioning can expire. Concurrent tabs must not provision simultaneously.
  suspend fun prepare() =
    mutex.withLock {
      withContext(Dispatchers.IO) {
        // Other key systems (for example Clear Key) do not need Android Widevine provisioning.
        if (!MediaDrm.isCryptoSchemeSupported(widevine)) return@withContext
        MediaDrm(widevine).use { drm ->
          val session =
            try {
              drm.openSession()
            } catch (_: NotProvisionedException) {
              Log.i("GeckoProtectedMedia", "Preparing first-use protected media")
              val request = drm.provisionRequest
              drm.provideProvisionResponse(provision(request.defaultUrl, request.data))
              drm.openSession()
            }
          try {
            // Match Gecko's readiness requirement, not just isCryptoSchemeSupported().
            MediaCrypto(widevine, session).release()
          } finally {
            drm.closeSession(session)
          }
        }
      }
    }

  private fun provision(defaultUrl: String, data: ByteArray): ByteArray {
    val endpoint = URI(defaultUrl)
    require(endpoint.scheme.equals("https", ignoreCase = true)) {
      "Protected-media provisioning requires HTTPS"
    }
    val separator = if (endpoint.rawQuery == null) "?" else "&"
    val signedRequest = URLEncoder.encode(String(data, Charsets.UTF_8), "UTF-8")
    val connection =
      URI("$defaultUrl${separator}signedRequest=$signedRequest").toURL().openConnection()
        as HttpURLConnection
    try {
      connection.requestMethod = "POST"
      connection.connectTimeout = 15000
      connection.readTimeout = 15000
      connection.instanceFollowRedirects = false
      connection.setRequestProperty("Content-Type", "application/json")
      check(connection.responseCode == HttpURLConnection.HTTP_OK) {
        "Protected-media provisioning failed (HTTP ${connection.responseCode})"
      }
      return connection.inputStream.use { input ->
        // Provisioning responses are small; don't allow an unbounded allocation.
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
          val count = input.read(buffer)
          if (count == -1) break
          check(output.size() + count <= 1024 * 1024) {
            "Invalid protected-media provisioning response"
          }
          output.write(buffer, 0, count)
        }
        check(output.size() > 0) { "Empty protected-media provisioning response" }
        output.toByteArray()
      }
    } finally {
      connection.disconnect()
    }
  }
}
