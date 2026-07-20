package com.searchlauncher.app.ui.browser

import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView

class PrivateBrowserActivity : BrowserActivity() {
  override val isPrivateMode: Boolean = true

  override fun onCreate(savedInstanceState: Bundle?) {
    // A process-specific data directory keeps private cookies and storage separate from normal
    // browsing. This must happen before the first WebView is created in the incognito process.
    synchronized(PrivateBrowserActivity::class.java) {
      if (!dataDirectoryConfigured) {
        WebView.setDataDirectorySuffix("incognito")
        dataDirectoryConfigured = true
      }
    }
    super.onCreate(savedInstanceState)
  }

  override fun onDestroy() {
    super.onDestroy()
    // Only wipe the private session when the activity is actually going away, not when the
    // system recreates it for a configuration change such as a screen rotation.
    if (isFinishing) {
      CookieManager.getInstance().removeAllCookies(null)
      CookieManager.getInstance().flush()
      WebStorage.getInstance().deleteAllData()
    }
  }

  companion object {
    private var dataDirectoryConfigured = false
  }
}
