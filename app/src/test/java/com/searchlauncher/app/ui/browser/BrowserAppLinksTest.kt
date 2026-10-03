package com.searchlauncher.app.ui.browser

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BrowserAppLinksTest {
  private val context = mockk<Context>(relaxed = true)
  private val packageManager = mockk<PackageManager>()

  @Test
  fun handsOffToTheUsersDefaultNonBrowserAppWithoutAChooser() {
    val github = handler("com.github.android", "com.github.android.MainActivity")
    stubResolution(github)
    val launched = slot<Intent>()
    every { context.startActivity(capture(launched)) } returns Unit
    val uri = Uri.parse("https://github.com/org/repo?state=unchanged%2Fvalue")

    assertTrue(openVerifiedAppLink(context, uri))

    assertEquals(uri, launched.captured.data)
    assertEquals(
      ComponentName("com.github.android", "com.github.android.MainActivity"),
      launched.captured.component,
    )
    assertTrue(launched.captured.hasCategory(Intent.CATEGORY_BROWSABLE))
    assertTrue(launched.captured.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
  }

  @Test
  fun ordinaryWebPageStaysInWebView() {
    stubResolution(null)
    assertFalse(openVerifiedAppLink(context, Uri.parse("https://github.com")))
    verify(exactly = 0) { context.startActivity(any()) }
  }

  @Test
  fun browserSheetIsNotShownWhenTheSystemWouldAsk() {
    stubResolution(handler("android", "com.android.internal.app.ResolverActivity"))
    assertFalse(openVerifiedAppLink(context, Uri.parse("https://github.com")))
    verify(exactly = 0) { context.startActivity(any()) }
  }

  @Test
  fun defaultBrowserStaysInWebView() {
    val chrome = handler("com.android.chrome", "com.google.android.apps.chrome.Main")
    stubResolution(chrome, browsers = listOf(chrome))
    assertFalse(openVerifiedAppLink(context, Uri.parse("https://github.com")))
    verify(exactly = 0) { context.startActivity(any()) }
  }

  @Test
  fun ownPackageStaysInWebView() {
    stubResolution(
      handler("com.searchlauncher.app", "com.searchlauncher.app.ui.browser.BrowserActivity")
    )
    assertFalse(openVerifiedAppLink(context, Uri.parse("https://github.com")))
    verify(exactly = 0) { context.startActivity(any()) }
  }

  @Test
  fun missingDefaultAppStaysInWebView() {
    val github = handler("com.github.android", "com.github.android.MainActivity")
    stubResolution(github)
    every { context.startActivity(any()) } throws ActivityNotFoundException()
    assertFalse(openVerifiedAppLink(context, Uri.parse("https://github.com")))
  }

  @Test
  fun deniedAppStaysInWebView() {
    val github = handler("com.github.android", "com.github.android.MainActivity")
    stubResolution(github)
    every { context.startActivity(any()) } throws SecurityException()
    assertFalse(openVerifiedAppLink(context, Uri.parse("https://github.com")))
  }

  @Test
  fun nonHttpsIsNotDispatchedByAppLinkHandler() {
    assertFalse(openVerifiedAppLink(context, Uri.parse("http://example.org")))
    assertFalse(openVerifiedAppLink(context, Uri.parse("javascript:alert(1)")))
    verify(exactly = 0) { context.startActivity(any()) }
  }

  @Test
  fun webIntentWithoutADedicatedAppLoadsInThisBrowser() {
    stubResolution(handler("android", "com.android.internal.app.ResolverActivity"))
    val navigation =
      dispatchOutsideWebView(
        context,
        Uri.parse(
          "intent://github.com/org/repo#Intent;scheme=https;" +
            "S.browser_fallback_url=https%3A%2F%2Fgithub.com%2Forg%2Frepo;end"
        ),
      )
    assertEquals(OutsideNavigation.Load("https://github.com/org/repo"), navigation)
    verify(exactly = 0) { context.startActivity(any()) }
  }

  @Test
  fun webIntentNamingAnInstalledAppOpensThatApp() {
    stubResolution(null)
    val launched = slot<Intent>()
    every { context.startActivity(capture(launched)) } returns Unit
    val navigation =
      dispatchOutsideWebView(
        context,
        Uri.parse("intent://github.com/org/repo#Intent;scheme=https;package=com.github.android;end"),
      )
    assertEquals(OutsideNavigation.Consumed, navigation)
    assertEquals("com.github.android", launched.captured.`package`)
    assertEquals(Uri.parse("https://github.com/org/repo"), launched.captured.data)
    assertNull(launched.captured.component)
  }

  @Test
  fun webIntentNamingAMissingAppFallsBackIntoThisBrowser() {
    stubResolution(null)
    every { context.startActivity(any()) } throws ActivityNotFoundException()
    val navigation =
      dispatchOutsideWebView(
        context,
        Uri.parse(
          "intent://github.com/org/repo#Intent;scheme=https;package=com.github.android;" +
            "S.browser_fallback_url=https%3A%2F%2Fgithub.com%2Forg%2Frepo;end"
        ),
      )
    assertEquals(OutsideNavigation.Load("https://github.com/org/repo"), navigation)
  }

  @Test
  fun mailtoStillOpensAnApp() {
    val launched = slot<Intent>()
    every { context.startActivity(capture(launched)) } returns Unit
    val uri = Uri.parse("mailto:test@example.com")
    assertEquals(OutsideNavigation.Consumed, dispatchOutsideWebView(context, uri))
    assertEquals(uri, launched.captured.data)
    assertEquals(Intent.ACTION_VIEW, launched.captured.action)
  }

  @Test
  fun missingAppIsReported() {
    every { context.startActivity(any()) } throws ActivityNotFoundException()
    assertEquals(
      OutsideNavigation.NoApp,
      dispatchOutsideWebView(context, Uri.parse("mailto:test@example.com")),
    )
  }

  @Test
  fun browserActivityIsRegisteredForLinksAndAsABrowserApp() {
    val manifest =
      listOf(File("src/main/AndroidManifest.xml"), File("app/src/main/AndroidManifest.xml"))
        .first { it.exists() }
        .readText()
    val browserActivity = manifest.substringAfter("android:name=\".ui.browser.BrowserActivity\"")
    val filters = browserActivity.substringBefore("<activity")
    assertTrue(filters.contains("android.intent.action.VIEW"))
    assertTrue(filters.contains("android.intent.category.BROWSABLE"))
    assertTrue(filters.contains("android:scheme=\"http\""))
    assertTrue(filters.contains("android:scheme=\"https\""))
    assertTrue(filters.contains("android.intent.category.APP_BROWSER"))
    assertFalse(filters.contains("android.intent.category.LAUNCHER"))
  }

  @Test
  fun samePageIgnoresTrailingSlashAndFragment() {
    assertTrue(sameWebPage("https://github.com/", "https://github.com"))
    assertTrue(sameWebPage("https://github.com/org/repo#readme", "https://GitHub.com/org/repo"))
    assertFalse(sameWebPage("https://github.com/org/repo", "https://github.com/org/other"))
    assertFalse(sameWebPage(null, "https://github.com"))
  }

  private fun stubResolution(resolved: ResolveInfo?, browsers: List<ResolveInfo> = emptyList()) {
    every { context.packageName } returns "com.searchlauncher.app"
    every { context.packageManager } returns packageManager
    every { packageManager.resolveActivity(any(), any<Int>()) } returns resolved
    every { packageManager.queryIntentActivities(any(), any<Int>()) } returns browsers
  }

  private fun handler(packageName: String, className: String): ResolveInfo =
    ResolveInfo().apply {
      activityInfo =
        ActivityInfo().apply {
          this.packageName = packageName
          name = className
        }
    }
}
