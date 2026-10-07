package com.searchlauncher.app.ui.browser

import android.content.Context
import android.content.Intent
import java.net.URI
import org.json.JSONObject

/** A user-installed manifest, never a page's unauthenticated request to hide browser controls. */
internal data class InstalledWebApp(
  val id: String,
  val name: String,
  val startUrl: String,
  val scope: String,
  val display: String,
) {
  fun contains(url: String): Boolean = inScope(url, scope)

  fun toJson(): JSONObject =
    JSONObject()
      .put("id", id)
      .put("name", name)
      .put("start_url", startUrl)
      .put("scope", scope)
      .put("display", display)

  companion object {
    // Gecko has already resolved manifest-relative URLs. Revalidate the trust boundary here:
    // persisted records and intents must not be able to hide a different site's identity.
    fun fromManifest(manifest: JSONObject, documentUrl: String): InstalledWebApp? =
      runCatching {
          val document = webUri(documentUrl) ?: return null
          val start = webUri(manifest.optString("start_url")) ?: return null
          if (origin(start) != origin(document)) return null
          val scope = webUri(manifest.optString("scope")) ?: return null
          if (!inScope(start.toString(), scope.toString())) return null
          val display = manifest.optString("display")
          if (display !in setOf("standalone", "fullscreen")) return null
          val name =
            manifest.optString("name").ifBlank { manifest.optString("short_name") }.trim().take(120)
          if (name.isBlank()) return null
          val id = webUri(manifest.optString("id"))?.takeIf { origin(it) == origin(start) } ?: start
          InstalledWebApp(
            withoutFragment(id),
            name,
            withoutFragment(start),
            URI(scope.scheme, null, scope.host, scope.port, scope.path, null, null).toString(),
            display,
          )
        }
        .getOrNull()

    private fun withoutFragment(uri: URI) = uri.toString().substringBefore('#')

    private fun origin(uri: URI) =
      Triple(
        uri.scheme.lowercase(),
        uri.host.lowercase(),
        if (uri.port == -1) if (uri.scheme == "https") 443 else 80 else uri.port,
      )

    private fun webUri(value: String): URI? =
      runCatching {
          URI(value).normalize().takeIf {
            it.host != null &&
              it.rawUserInfo == null &&
              (it.scheme == "https" ||
                (it.scheme == "http" && it.host in setOf("localhost", "127.0.0.1", "[::1]")))
          }
        }
        .getOrNull()

    private fun inScope(value: String, scopeValue: String): Boolean =
      runCatching {
          val url = webUri(value) ?: return false
          val scope = webUri(scopeValue) ?: return false
          // Decode before normalization so encoded dot segments cannot escape the installed scope.
          fun path(uri: URI) = URI(null, null, uri.path.ifEmpty { "/" }, null).normalize().path
          origin(url) == origin(scope) && path(url).startsWith(path(scope))
        }
        .getOrDefault(false)
  }
}

/** Installation persists independently of tabs; closing a task does not uninstall its app. */
internal object InstalledWebApps {
  const val EXTRA_APP_ID = "installed_web_app_id"

  private fun preferences(context: Context) =
    context.applicationContext.getSharedPreferences("installed-web-apps", Context.MODE_PRIVATE)

  fun get(context: Context, id: String?): InstalledWebApp? {
    if (id == null) return null
    val raw = preferences(context).getString(id, null) ?: return null
    return runCatching {
        val json = JSONObject(raw)
        InstalledWebApp.fromManifest(json, json.getString("start_url"))
      }
      .getOrNull()
  }

  fun forStartUrl(context: Context, url: String): InstalledWebApp? {
    if (!BrowserEngine.isGecko) return null
    return preferences(context)
      .all
      .keys
      .asSequence()
      .mapNotNull { get(context, it) }
      .firstOrNull { it.startUrl == url.substringBefore('#') }
  }

  fun forUrl(context: Context, url: String): InstalledWebApp? {
    if (!BrowserEngine.isGecko) return null
    return preferences(context)
      .all
      .keys
      .mapNotNull { get(context, it) }
      .filter { it.contains(url) }
      .maxByOrNull { it.scope.length }
  }

  fun save(context: Context, app: InstalledWebApp) {
    preferences(context).edit().putString(app.id, app.toJson().toString()).apply()
  }

  fun remove(context: Context, app: InstalledWebApp) {
    preferences(context).edit().remove(app.id).apply()
    BrowserTabStore.tabs
      ?.items
      ?.filter { it.installedApp?.id == app.id }
      ?.forEach { it.installedApp = null }
  }

  fun launchIntent(context: Context, app: InstalledWebApp): Intent {
    val existing = BrowserTabStore.tabs?.items?.firstOrNull { it.installedApp?.id == app.id }
    val tab =
      existing
        ?: BrowserTabStore.addBackgroundTab(app.startUrl) { BrowserTabTasks.close(context, it.id) }
          .also { it.installedApp = app }
    return BrowserTabTasks.intentFor(context, tab.id)
  }
}
