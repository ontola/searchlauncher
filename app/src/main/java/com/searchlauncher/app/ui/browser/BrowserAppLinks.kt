package com.searchlauncher.app.ui.browser

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build

/**
 * Let Android hand HTTPS app links to the user's default app. Requiring a default non-browser
 * handler prevents a browser loop or a chooser on ordinary web navigation. The caller must only
 * invoke this for main-frame navigation, never for frames embedded by a page.
 */
internal fun openVerifiedAppLink(context: Context, uri: Uri): Boolean {
  if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || uri.scheme != "https") return false
  val intent =
    Intent(Intent.ACTION_VIEW, uri)
      .addCategory(Intent.CATEGORY_BROWSABLE)
      .addFlags(Intent.FLAG_ACTIVITY_REQUIRE_NON_BROWSER or Intent.FLAG_ACTIVITY_REQUIRE_DEFAULT)
  return try {
    context.startActivity(intent)
    true
  } catch (_: ActivityNotFoundException) {
    // No default app: keep the same navigation in this WebView.
    false
  } catch (_: SecurityException) {
    false
  }
}
