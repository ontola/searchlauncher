package com.searchlauncher.app.ui.browser

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BrowserSiteSettingsStoreTest {
  private val context = ApplicationProvider.getApplicationContext<Context>()

  @Before
  fun clearPrefs() {
    context
      .getSharedPreferences("browser_site_settings", Context.MODE_PRIVATE)
      .edit()
      .clear()
      .commit()
  }

  @Test
  fun deviceAccessStartsUnsetSoTheBrowserWillAsk() {
    val store = BrowserSiteSettingsStore(context, privateMode = false)
    val settings = store.load("https://example.com/page")
    for (access in BrowserDeviceAccess.entries) {
      assertEquals(null, settings.allowed(access))
    }
  }

  @Test
  fun remembersAllowAndBlockPerOriginAndFeature() {
    val store = BrowserSiteSettingsStore(context, privateMode = false)
    store.save(
      "https://example.com/a",
      BrowserSiteSettings(deviceAccess = mapOf(BrowserDeviceAccess.MICROPHONE to true)),
    )
    store.save(
      "https://other.test/",
      BrowserSiteSettings(
        deviceAccess =
          mapOf(BrowserDeviceAccess.CAMERA to false, BrowserDeviceAccess.BLUETOOTH to true)
      ),
    )
    assertEquals(true, store.load("https://example.com/b").allowed(BrowserDeviceAccess.MICROPHONE))
    assertEquals(null, store.load("https://example.com/b").allowed(BrowserDeviceAccess.CAMERA))
    assertEquals(false, store.load("https://other.test/path").allowed(BrowserDeviceAccess.CAMERA))
    assertEquals(true, store.load("https://other.test/path").allowed(BrowserDeviceAccess.BLUETOOTH))
    assertEquals(null, store.load("https://unseen.test/").allowed(BrowserDeviceAccess.MICROPHONE))
  }

  @Test
  fun resetClearsDeviceAccessChoices() {
    val store = BrowserSiteSettingsStore(context, privateMode = false)
    store.save(
      "https://example.com",
      BrowserSiteSettings(
        deviceAccess =
          mapOf(BrowserDeviceAccess.MICROPHONE to true, BrowserDeviceAccess.LOCATION to false)
      ),
    )
    store.reset("https://example.com/later")
    val settings = store.load("https://example.com")
    assertEquals(null, settings.allowed(BrowserDeviceAccess.MICROPHONE))
    assertEquals(null, settings.allowed(BrowserDeviceAccess.LOCATION))
  }

  @Test
  fun privateModeKeepsTheChoiceOnlyInMemory() {
    val store = BrowserSiteSettingsStore(context, privateMode = true)
    store.save(
      "https://example.com",
      BrowserSiteSettings(deviceAccess = mapOf(BrowserDeviceAccess.CAMERA to true)),
    )
    assertEquals(true, store.load("https://example.com").allowed(BrowserDeviceAccess.CAMERA))
    val other = BrowserSiteSettingsStore(context, privateMode = true)
    assertEquals(null, other.load("https://example.com").allowed(BrowserDeviceAccess.CAMERA))
  }
}
