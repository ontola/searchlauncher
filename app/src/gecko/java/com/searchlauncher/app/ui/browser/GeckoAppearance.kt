package com.searchlauncher.app.ui.browser

import android.graphics.Color
import android.net.Uri
import org.json.JSONObject
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.WebExtension

/** Apply only metadata from this session's current top-level document, including private tabs. */
internal class GeckoAppearance(
  private val activity: BrowserActivity,
  private val tab: BrowserTab,
  private val session: GeckoSession,
) : WebExtension.MessageDelegate {
  override fun onMessage(
    nativeApp: String,
    message: Any,
    sender: WebExtension.MessageSender,
  ): GeckoResult<Any>? {
    if (
      nativeApp != "page_appearance" ||
        sender.session !== session ||
        !sender.isTopLevel ||
        message !is JSONObject
    )
      return null
    val url = message.optString("url")
    if (Uri.parse(url).scheme !in listOf("http", "https") || sender.url != url || tab.url != url)
      return null
    val background = color(message.optString("background")) ?: return null
    val theme = color(message.optString("theme"))
    if (tab.pageBackgroundArgb != background || tab.themeColorArgb != theme) {
      tab.pageBackgroundArgb = background
      tab.themeColorArgb = theme
      activity.publishTaskDescription(tab.title, tab.favicon, tab.frameColorArgb)
    }
    return null
  }

  private fun color(value: String): Int? =
    if (value.matches(Regex("#[0-9a-fA-F]{6}"))) Color.parseColor(value) else null
}
