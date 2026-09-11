package com.searchlauncher.app.ui.browser

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BrowserWebAuthnTest {
  private val context = mockk<Context>()
  private val settings = mockk<WebSettings>()
  private val view = mockk<WebView>()

  @Before
  fun setUp() {
    mockkStatic(WebViewFeature::class)
    mockkStatic(WebSettingsCompat::class)
    every { view.context } returns context
    every { view.settings } returns settings
    every { context.checkSelfPermission(Manifest.permission.CREDENTIAL_MANAGER_SET_ORIGIN) } returns
      PackageManager.PERMISSION_GRANTED
    every { WebViewFeature.isFeatureSupported(WebViewFeature.WEB_AUTHENTICATION) } returns true
    every { WebSettingsCompat.setWebAuthenticationSupport(settings, any()) } returns Unit
    every { WebSettingsCompat.getWebAuthenticationSupport(settings) } returns
      WebSettingsCompat.WEB_AUTHENTICATION_SUPPORT_FOR_BROWSER
  }

  @After fun tearDown() = unmockkAll()

  @Test
  fun enablesWebsiteOriginMode() {
    assertTrue(view.enableBrowserWebAuthn())
    verify(exactly = 1) {
      WebSettingsCompat.setWebAuthenticationSupport(
        settings,
        WebSettingsCompat.WEB_AUTHENTICATION_SUPPORT_FOR_BROWSER,
      )
    }
  }

  @Test
  fun missingOriginPermissionDoesNotEnableWebAuthn() {
    every { context.checkSelfPermission(Manifest.permission.CREDENTIAL_MANAGER_SET_ORIGIN) } returns
      PackageManager.PERMISSION_DENIED
    assertFalse(view.enableBrowserWebAuthn())
    verify(exactly = 0) { WebSettingsCompat.setWebAuthenticationSupport(any(), any()) }
  }

  @Test
  fun unsupportedWebViewDoesNotCallSettingsApi() {
    every { WebViewFeature.isFeatureSupported(WebViewFeature.WEB_AUTHENTICATION) } returns false
    assertFalse(view.enableBrowserWebAuthn())
    verify(exactly = 0) { WebSettingsCompat.setWebAuthenticationSupport(any(), any()) }
  }

  @Test
  @Config(sdk = [33])
  fun olderAndroidDoesNotEnableBrowserMode() {
    assertFalse(view.enableBrowserWebAuthn())
    verify(exactly = 0) { WebSettingsCompat.setWebAuthenticationSupport(any(), any()) }
  }

  @Test
  fun declinedSupportIsNotReportedAsAvailable() {
    every { WebSettingsCompat.getWebAuthenticationSupport(settings) } returns
      WebSettingsCompat.WEB_AUTHENTICATION_SUPPORT_NONE
    assertFalse(view.enableBrowserWebAuthn())
  }
}
