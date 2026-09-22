package com.searchlauncher.app.util

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.webkit.WebView
import com.searchlauncher.app.BuildConfig
import com.searchlauncher.app.SearchLauncherApp
import io.sentry.Sentry
import io.sentry.SentryLevel
import io.sentry.protocol.Feedback
import io.sentry.protocol.SentryId
import io.sentry.protocol.User

/**
 * Crash reports already go to GlitchTip through the Sentry Android SDK. GlitchTip stores a Sentry
 * feedback envelope as a user report and keeps only the message, name, and email — device context
 * on that envelope is dropped. A normal event keeps the SDK's device, OS, and app context, so
 * feedback is sent as both: the event for the diagnostics, and a feedback item so the address the
 * user typed is attached to it.
 */
internal data class FeedbackDiagnostics(
  val versionName: String,
  val versionCode: Int,
  val gitHash: String,
  val buildDate: String,
  val debuggable: Boolean,
  val manufacturer: String,
  val brand: String,
  val model: String,
  val device: String,
  val androidRelease: String,
  val sdkInt: Int,
  val securityPatch: String,
  val abis: String,
  val locale: String,
  val screenWidthPx: Int,
  val screenHeightPx: Int,
  val densityDpi: Int,
  val webViewVersion: String,
  val installer: String,
  val defaultLauncher: Boolean,
  val defaultBrowser: Boolean,
  val availMemoryMb: Long,
  val totalMemoryMb: Long,
) {
  fun format(): String =
    listOf(
        "app: $versionName ($versionCode) $gitHash $buildDate debug=$debuggable",
        "device: $manufacturer $model ($brand/$device)",
        "android: $androidRelease (API $sdkInt) patch $securityPatch",
        "abi: $abis",
        "locale: $locale",
        "screen: ${screenWidthPx}x$screenHeightPx @${densityDpi}dpi",
        "webview: $webViewVersion",
        "installer: $installer",
        "default launcher: $defaultLauncher",
        "default browser: $defaultBrowser",
        "memory: $availMemoryMb/$totalMemoryMb MB free",
      )
      .joinToString("\n")

  fun tags(): Map<String, String> =
    mapOf(
      "feedback" to "user",
      "git_hash" to tagValue(gitHash),
      "android_release" to tagValue(androidRelease),
      "android_sdk" to sdkInt.toString(),
      "device_model" to tagValue(model),
      "device_manufacturer" to tagValue(manufacturer),
      "webview" to tagValue(webViewVersion),
      "default_launcher" to defaultLauncher.toString(),
      "default_browser" to defaultBrowser.toString(),
    )

  fun extras(): Map<String, String> =
    mapOf(
      "version_name" to versionName,
      "version_code" to versionCode.toString(),
      "git_hash" to gitHash,
      "build_date" to buildDate,
      "debuggable" to debuggable.toString(),
      "manufacturer" to manufacturer,
      "brand" to brand,
      "model" to model,
      "device" to device,
      "android_release" to androidRelease,
      "android_sdk" to sdkInt.toString(),
      "security_patch" to securityPatch,
      "abis" to abis,
      "locale" to locale,
      "screen" to "${screenWidthPx}x$screenHeightPx @${densityDpi}dpi",
      "webview" to webViewVersion,
      "installer" to installer,
      "default_launcher" to defaultLauncher.toString(),
      "default_browser" to defaultBrowser.toString(),
      "memory" to "$availMemoryMb/$totalMemoryMb MB free",
    )

  companion object {
    fun collect(context: Context): FeedbackDiagnostics {
      val metrics = context.resources.displayMetrics
      val memory = deviceMemory(context)
      return FeedbackDiagnostics(
        versionName = BuildConfig.VERSION_NAME,
        versionCode = BuildConfig.VERSION_CODE,
        gitHash = BuildConfig.GIT_HASH.ifBlank { "unknown" },
        buildDate = BuildConfig.BUILD_DATE.ifBlank { "unknown" },
        debuggable = BuildConfig.DEBUG,
        manufacturer = Build.MANUFACTURER.orUnknown(),
        brand = Build.BRAND.orUnknown(),
        model = Build.MODEL.orUnknown(),
        device = Build.DEVICE.orUnknown(),
        androidRelease = Build.VERSION.RELEASE.orUnknown(),
        sdkInt = Build.VERSION.SDK_INT,
        securityPatch = Build.VERSION.SECURITY_PATCH.orUnknown(),
        abis = Build.SUPPORTED_ABIS.joinToString().ifBlank { "unknown" },
        locale = context.resources.configuration.locales[0]?.toLanguageTag().orUnknown(),
        screenWidthPx = metrics.widthPixels,
        screenHeightPx = metrics.heightPixels,
        densityDpi = metrics.densityDpi,
        webViewVersion = webViewVersion(),
        installer = installerPackage(context),
        defaultLauncher = isDefaultHome(context),
        defaultBrowser = isDefaultBrowser(context),
        availMemoryMb = memory.first,
        totalMemoryMb = memory.second,
      )
    }
  }
}

internal data class FeedbackReport(
  val eventMessage: String,
  val contactEmail: String?,
  val tags: Map<String, String>,
  val extras: Map<String, String>,
) {
  companion object {
    fun build(
      userMessage: String,
      contactEmail: String?,
      diagnostics: FeedbackDiagnostics,
    ): FeedbackReport? {
      val message = userMessage.trim()
      if (message.isEmpty()) return null
      val email = contactEmail?.trim()?.take(EMAIL_LIMIT)?.ifEmpty { null }
      val details = diagnostics.format()
      val separator = "\n\n--\n"
      val roomForMessage = (MESSAGE_LIMIT - details.length - separator.length).coerceAtLeast(200)
      val body =
        if (message.length > roomForMessage) {
          message.take(roomForMessage - 1).trimEnd() + "…"
        } else {
          message
        }
      return FeedbackReport(
        eventMessage = (body + separator + details).take(MESSAGE_LIMIT),
        contactEmail = email,
        tags = diagnostics.tags(),
        extras = diagnostics.extras(),
      )
    }

    private const val MESSAGE_LIMIT = 4096
    private const val EMAIL_LIMIT = 254
  }
}

internal enum class FeedbackResult {
  Sent,
  Empty,
  Failed,
}

internal object FeedbackReporter {
  fun submit(context: Context, userMessage: String, contactEmail: String?): FeedbackResult {
    val diagnostics =
      runCatching { FeedbackDiagnostics.collect(context) }
        .getOrElse {
          return FeedbackResult.Failed
        }
    val report =
      FeedbackReport.build(userMessage, contactEmail, diagnostics) ?: return FeedbackResult.Empty
    val app = context.applicationContext as SearchLauncherApp
    if (!Sentry.isEnabled()) {
      app.ensureCrashReporter()
    }
    return try {
      var eventId = SentryId.EMPTY_ID
      Sentry.withScope { scope ->
        report.tags.forEach { (key, value) -> scope.setTag(key, value) }
        report.extras.forEach { (key, value) -> scope.setExtra(key, value) }
        report.contactEmail?.let { address -> scope.user = User().apply { email = address } }
        eventId = Sentry.captureMessage(report.eventMessage, SentryLevel.INFO)
        if (eventId != SentryId.EMPTY_ID) {
          val feedback = Feedback(report.eventMessage)
          feedback.setContactEmail(report.contactEmail)
          feedback.setAssociatedEventId(eventId)
          Sentry.captureFeedback(feedback)
        }
      }
      Sentry.flush(15_000)
      if (!app.isConsentGranted()) {
        Sentry.close()
      }
      if (eventId == SentryId.EMPTY_ID) FeedbackResult.Failed else FeedbackResult.Sent
    } catch (_: Exception) {
      if (!app.isConsentGranted()) {
        runCatching { Sentry.close() }
      }
      FeedbackResult.Failed
    }
  }
}

private fun tagValue(value: String): String =
  value.replace('\n', ' ').trim().take(200).ifEmpty { "unknown" }

private fun String?.orUnknown(): String = this?.trim()?.ifEmpty { null } ?: "unknown"

private fun deviceMemory(context: Context): Pair<Long, Long> =
  runCatching {
      val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
      val info = ActivityManager.MemoryInfo()
      activityManager.getMemoryInfo(info)
      info.availMem / (1024 * 1024) to info.totalMem / (1024 * 1024)
    }
    .getOrDefault(0L to 0L)

private fun webViewVersion(): String =
  runCatching { WebView.getCurrentWebViewPackage()?.versionName }.getOrNull().orUnknown()

private fun installerPackage(context: Context): String =
  runCatching {
      val packageName = context.packageName
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        context.packageManager.getInstallSourceInfo(packageName).installingPackageName
      } else {
        @Suppress("DEPRECATION") context.packageManager.getInstallerPackageName(packageName)
      }
    }
    .getOrNull()
    .orUnknown()

private fun isDefaultHome(context: Context): Boolean =
  runCatching {
      val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
      val resolved =
        context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
      resolved?.activityInfo?.packageName == context.packageName
    }
    .getOrDefault(false)

private fun isDefaultBrowser(context: Context): Boolean =
  runCatching {
      val roles = context.getSystemService(android.app.role.RoleManager::class.java)
      if (roles != null && roles.isRoleAvailable(android.app.role.RoleManager.ROLE_BROWSER)) {
        roles.isRoleHeld(android.app.role.RoleManager.ROLE_BROWSER)
      } else {
        val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://example.com"))
        val resolved =
          context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        resolved?.activityInfo?.packageName == context.packageName
      }
    }
    .getOrDefault(false)
