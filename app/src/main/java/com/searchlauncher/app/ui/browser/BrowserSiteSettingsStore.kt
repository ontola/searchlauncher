package com.searchlauncher.app.ui.browser

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import java.net.URI

internal class BrowserSiteSettingsStore(context: Context, privateMode: Boolean) {
  private val preferences =
    if (privateMode) null
    else context.getSharedPreferences("browser_site_settings", Context.MODE_PRIVATE)
  private val ephemeralSettings = mutableMapOf<String, BrowserSiteSettings>()

  fun load(url: String): BrowserSiteSettings {
    val origin = browserOrigin(url) ?: return BrowserSiteSettings()
    ephemeralSettings[origin]?.let {
      return it
    }
    val prefs = preferences ?: return BrowserSiteSettings()
    return BrowserSiteSettings(
      javaScriptEnabled = prefs.getBoolean(key(origin, "javascript"), true),
      popupsEnabled = prefs.getBoolean(key(origin, "popups"), false),
      thirdPartyCookiesEnabled = prefs.getBoolean(key(origin, "third_party_cookies"), false),
      adBlockEnabled = prefs.getBoolean(key(origin, "ad_block"), true),
      deviceAccess = loadDeviceAccess(prefs, origin),
    )
  }

  fun save(url: String, settings: BrowserSiteSettings) {
    val origin = browserOrigin(url) ?: return
    ephemeralSettings[origin] = settings
    val editor = preferences?.edit() ?: return
    editor
      .putBoolean(key(origin, "javascript"), settings.javaScriptEnabled)
      .putBoolean(key(origin, "popups"), settings.popupsEnabled)
      .putBoolean(key(origin, "third_party_cookies"), settings.thirdPartyCookiesEnabled)
      .putBoolean(key(origin, "ad_block"), settings.adBlockEnabled)
    for (access in BrowserDeviceAccess.entries) {
      val accessKey = key(origin, access.prefKey)
      val allowed = settings.deviceAccess[access]
      if (allowed == null) editor.remove(accessKey) else editor.putBoolean(accessKey, allowed)
    }
    editor.apply()
  }

  fun reset(url: String) {
    val origin = browserOrigin(url) ?: return
    ephemeralSettings.remove(origin)
    val editor = preferences?.edit() ?: return
    editor
      .remove(key(origin, "javascript"))
      .remove(key(origin, "popups"))
      .remove(key(origin, "third_party_cookies"))
      .remove(key(origin, "ad_block"))
    for (access in BrowserDeviceAccess.entries) {
      editor.remove(key(origin, access.prefKey))
    }
    editor.apply()
  }

  private fun loadDeviceAccess(
    prefs: SharedPreferences,
    origin: String,
  ): Map<BrowserDeviceAccess, Boolean> = buildMap {
    for (access in BrowserDeviceAccess.entries) {
      val accessKey = key(origin, access.prefKey)
      if (prefs.contains(accessKey)) put(access, prefs.getBoolean(accessKey, false))
    }
  }

  private fun key(origin: String, setting: String): String = "${Uri.encode(origin)}.$setting"
}

internal fun browserOrigin(url: String): String? {
  val uri = runCatching { URI(url) }.getOrNull() ?: return null
  val scheme = uri.scheme?.lowercase() ?: return null
  val host = uri.host?.lowercase() ?: return null
  if (scheme != "http" && scheme != "https") return null
  val defaultPort = if (scheme == "https") 443 else 80
  val portSuffix = if (uri.port == -1 || uri.port == defaultPort) "" else ":${uri.port}"
  return "$scheme://$host$portSuffix"
}

internal fun browserSiteLabel(url: String): String =
  runCatching { URI(url).host }.getOrNull()?.takeIf { it.isNotBlank() } ?: "this site"
