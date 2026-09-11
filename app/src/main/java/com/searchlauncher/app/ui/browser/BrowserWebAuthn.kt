package com.searchlauncher.app.ui.browser

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.webkit.WebView
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature

/** Use Chromium's origin-checked WebAuthn implementation, never a JavaScript credential bridge. */
internal fun WebView.enableBrowserWebAuthn(): Boolean {
  // Browser mode forwards the website origin to Credential Manager (Android 14+). Without this
  // permission Chromium can crash when a page requests a credential. Older devices keep the
  // default unsupported behavior so websites can offer another sign-in method.
  if (
    Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
      context.checkSelfPermission(Manifest.permission.CREDENTIAL_MANAGER_SET_ORIGIN) !=
        PackageManager.PERMISSION_GRANTED ||
      !WebViewFeature.isFeatureSupported(WebViewFeature.WEB_AUTHENTICATION)
  )
    return false

  WebSettingsCompat.setWebAuthenticationSupport(
    settings,
    WebSettingsCompat.WEB_AUTHENTICATION_SUPPORT_FOR_BROWSER,
  )
  // A provider may decline support. Never substitute FOR_APP: these are arbitrary websites,
  // not domains associated with SearchLauncher through Digital Asset Links.
  return WebSettingsCompat.getWebAuthenticationSupport(settings) ==
    WebSettingsCompat.WEB_AUTHENTICATION_SUPPORT_FOR_BROWSER
}
