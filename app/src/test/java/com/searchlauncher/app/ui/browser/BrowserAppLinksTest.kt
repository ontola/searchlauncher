package com.searchlauncher.app.ui.browser

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BrowserAppLinksTest {
  private val context = mockk<Context>(relaxed = true)

  @Test
  fun handsOffExactUrlWithoutAllowingBrowserOrChooser() {
    val launched = slot<Intent>()
    every { context.startActivity(capture(launched)) } returns Unit
    val uri = Uri.parse("https://app.example.org/login?state=unchanged%2Fvalue")
    assertTrue(openVerifiedAppLink(context, uri))
    assertEquals(uri, launched.captured.data)
    assertTrue(launched.captured.hasCategory(Intent.CATEGORY_BROWSABLE))
    assertTrue(launched.captured.flags and Intent.FLAG_ACTIVITY_REQUIRE_NON_BROWSER != 0)
    assertTrue(launched.captured.flags and Intent.FLAG_ACTIVITY_REQUIRE_DEFAULT != 0)
    assertNull(launched.captured.component)
  }

  @Test
  fun ordinaryWebPageStaysInWebView() {
    every { context.startActivity(any()) } throws ActivityNotFoundException()
    assertFalse(openVerifiedAppLink(context, Uri.parse("https://example.org")))
  }

  @Test
  fun deniedAppStaysInWebView() {
    every { context.startActivity(any()) } throws SecurityException()
    assertFalse(openVerifiedAppLink(context, Uri.parse("https://example.org")))
  }

  @Test
  fun nonHttpsIsNotDispatchedByAppLinkHandler() {
    assertFalse(openVerifiedAppLink(context, Uri.parse("http://example.org")))
    assertFalse(openVerifiedAppLink(context, Uri.parse("javascript:alert(1)")))
    verify(exactly = 0) { context.startActivity(any()) }
  }

  @Test
  @Config(sdk = [29])
  fun olderAndroidDoesNotLaunchAnotherBrowser() {
    assertFalse(openVerifiedAppLink(context, Uri.parse("https://example.org")))
    verify(exactly = 0) { context.startActivity(any()) }
  }
}
