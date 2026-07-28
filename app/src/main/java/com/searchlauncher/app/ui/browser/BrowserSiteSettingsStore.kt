package com.searchlauncher.app.ui.browser

import android.content.Context
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
    )
  }

  fun save(url: String, settings: BrowserSiteSettings) {
    val origin = browserOrigin(url) ?: return
    ephemeralSettings[origin] = settings
    preferences
      ?.edit()
      ?.putBoolean(key(origin, "javascript"), settings.javaScriptEnabled)
      ?.putBoolean(key(origin, "popups"), settings.popupsEnabled)
      ?.putBoolean(key(origin, "third_party_cookies"), settings.thirdPartyCookiesEnabled)
      ?.putBoolean(key(origin, "ad_block"), settings.adBlockEnabled)
      ?.apply()
  }

  fun reset(url: String) {
    val origin = browserOrigin(url) ?: return
    ephemeralSettings.remove(origin)
    preferences
      ?.edit()
      ?.remove(key(origin, "javascript"))
      ?.remove(key(origin, "popups"))
      ?.remove(key(origin, "third_party_cookies"))
      ?.remove(key(origin, "ad_block"))
      ?.apply()
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
