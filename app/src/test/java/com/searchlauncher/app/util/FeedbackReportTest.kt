package com.searchlauncher.app.util

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class FeedbackReportTest {

  @Test
  fun reportKeepsTheMessageAndLeavesEmailOutOfTheBody() {
    val report =
      FeedbackReport.build(
        userMessage = "The keyboard covers the first result",
        contactEmail = " ada@example.com ",
        diagnostics = sample(),
      )

    assertTrue(report!!.eventMessage.startsWith("The keyboard covers the first result"))
    assertTrue(report.eventMessage.contains("device: Google Pixel 8 (google/shiba)"))
    assertTrue(report.eventMessage.contains("android: 14 (API 34) patch 2026-09-01"))
    assertTrue(report.eventMessage.contains("webview: 128.0.6613.88"))
    assertTrue(report.eventMessage.contains("default launcher: true"))
    assertFalse(report.eventMessage.contains("ada@example.com"))
    assertEquals("ada@example.com", report.contactEmail)
    assertEquals("Pixel 8", report.tags["device_model"])
    assertEquals("14", report.tags["android_release"])
    assertEquals("128.0.6613.88", report.extras["webview"])
    assertEquals("0.0.44", report.extras["version_name"])
  }

  @Test
  fun blankMessageIsRejectedAndBlankEmailIsOmitted() {
    assertNull(FeedbackReport.build("   ", "ada@example.com", sample()))
    val report = FeedbackReport.build("Hello", "  ", sample())
    assertNull(report!!.contactEmail)
  }

  @Test
  fun longMessageKeepsTheDeviceBlock() {
    val report = FeedbackReport.build("x".repeat(8000), null, sample())
    assertTrue(report!!.eventMessage.length <= 4096)
    assertTrue(report.eventMessage.endsWith(sample().format()))
    assertTrue(report.eventMessage.contains("…"))
  }

  @Test
  fun collectReadsThisDevice() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val diagnostics = FeedbackDiagnostics.collect(context)

    assertTrue(diagnostics.versionName.isNotBlank())
    assertTrue(diagnostics.sdkInt >= 33)
    assertTrue(diagnostics.model.isNotBlank())
    assertTrue(diagnostics.format().contains("android:"))
    assertTrue(diagnostics.tags()["feedback"] == "user")
  }

  private fun sample() =
    FeedbackDiagnostics(
      versionName = "0.0.44",
      versionCode = 282,
      gitHash = "abc123",
      buildDate = "2026-09-21",
      debuggable = false,
      manufacturer = "Google",
      brand = "google",
      model = "Pixel 8",
      device = "shiba",
      androidRelease = "14",
      sdkInt = 34,
      securityPatch = "2026-09-01",
      abis = "arm64-v8a",
      locale = "en-US",
      screenWidthPx = 1080,
      screenHeightPx = 2400,
      densityDpi = 440,
      webViewVersion = "128.0.6613.88",
      installer = "com.android.vending",
      defaultLauncher = true,
      defaultBrowser = false,
      availMemoryMb = 2400,
      totalMemoryMb = 7600,
    )
}
