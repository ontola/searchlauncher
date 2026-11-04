package com.searchlauncher.app.ui.browser

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
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BuiltInBrowserTest {
  private val pm = mockk<PackageManager>()
  private val context = mockk<Context>(relaxed = true)
  private val self = handler("com.searchlauncher.app.debug", BrowserActivity::class.java.name)
  private val chrome = handler("com.android.chrome", "ChromeActivity")
  private val firefox = handler("org.mozilla.firefox", "FirefoxActivity")
  private val launched = slot<Intent>()
  private val url = "https://www.google.com/search?q=hello%20world&source=launcher"

  init {
    every { context.packageManager } returns pm
    every { context.packageName } returns self.activityInfo.packageName
    every { context.startActivity(capture(launched)) } returns Unit
  }

  private fun configure(handlers: List<ResolveInfo>, preferred: ResolveInfo = self) {
    every { pm.resolveActivity(any<Intent>(), any<Int>()) } returns preferred
    // Reproduce Android's browser filtering: without MATCH_ALL only the default is returned.
    every { pm.queryIntentActivities(any<Intent>(), any<Int>()) } answers
      {
        if (secondArg<Int>() and PackageManager.MATCH_ALL != 0) handlers else listOf(preferred)
      }
  }

  @Test
  fun defaultLauncherBrowserStillOffersOtherInstalledBrowsers() {
    configure(
      listOf(
        self,
        chrome,
        firefox,
        handler("com.duckduckgo.mobile.android", "DuckActivity"),
        handler("com.mi.globalbrowser", "MiActivity"),
      )
    )
    BuiltInBrowser.openExternally(context, url)
    assertEquals(
      ComponentName(context, ExternalBrowserChooserActivity::class.java),
      launched.captured.component,
    )
    val choices =
      launched.captured.getParcelableArrayExtra(Intent.EXTRA_INITIAL_INTENTS)!!.map { it as Intent }
    assertEquals(
      setOf(
        "com.android.chrome",
        "org.mozilla.firefox",
        "com.duckduckgo.mobile.android",
        "com.mi.globalbrowser",
      ),
      choices.map { it.component!!.packageName }.toSet(),
    )
    assertTrue(choices.all { it.data == Uri.parse(url) })
    assertTrue(choices.all { it.hasCategory(Intent.CATEGORY_BROWSABLE) })
    assertFalse(choices.any { it.component!!.packageName == context.packageName })
    assertTrue(launched.captured.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
  }

  @Test
  fun singleExternalBrowserOpensExplicitlyEvenWhenLauncherIsDefault() {
    configure(listOf(self, firefox))
    BuiltInBrowser.openExternally(context, url)
    assertEquals(
      ComponentName("org.mozilla.firefox", "FirefoxActivity"),
      launched.captured.component,
    )
    assertEquals(Uri.parse(url), launched.captured.data)
  }

  @Test
  fun existingExternalDefaultIsRespected() {
    configure(listOf(self, chrome, firefox), chrome)
    BuiltInBrowser.openExternally(context, url)
    assertEquals(Intent.ACTION_VIEW, launched.captured.action)
    assertEquals(Uri.parse(url), launched.captured.data)
    assertNull(launched.captured.component)
  }

  private fun handler(pkg: String, activity: String) =
    ResolveInfo().apply {
      activityInfo =
        ActivityInfo().apply {
          packageName = pkg
          name = activity
        }
    }
}
