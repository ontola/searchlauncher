package com.searchlauncher.app.ui.browser

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import java.net.URISyntaxException

/**
 * Where a navigation that the WebView should not render itself has to go.
 *
 * [Proceed] lets the WebView load the original URL. [Load] does the same for a different http(s)
 * URL, which is how an `intent:` link falls back into this browser instead of a system chooser.
 * [Consumed] means another app was opened, or the navigation was dropped. [NoApp] and [Failed] are
 * the two toasts the browser already shows for links nothing can open.
 */
internal sealed interface OutsideNavigation {
  data object Proceed : OutsideNavigation

  data object Consumed : OutsideNavigation

  data class Load(val url: String) : OutsideNavigation

  data object NoApp : OutsideNavigation

  data object Failed : OutsideNavigation
}

/**
 * Hands an https link to the user's chosen non-browser app, such as the GitHub app when they have
 * set it to open github.com.
 *
 * Anything else stays in this WebView. An implicit [Intent.ACTION_VIEW] is not used: with no
 * default, or when the default is a browser, some phones ignore
 * [Intent.FLAG_ACTIVITY_REQUIRE_NON_BROWSER] and [Intent.FLAG_ACTIVITY_REQUIRE_DEFAULT] and pop a
 * browser sheet. That sheet lists the other browsers and leaves this one out, which is what tapping
 * an ordinary result (github.com from a search page, for example) was doing. Pinning a concrete
 * component means the system has nothing to disambiguate.
 *
 * Only main-frame navigations may call this. A frame embedded by a page must not be able to open
 * another app.
 */
internal fun openVerifiedAppLink(context: Context, uri: Uri): Boolean {
  if (uri.scheme != "https") return false
  val target = nonBrowserDefaultTarget(context, uri) ?: return false
  val intent =
    Intent(Intent.ACTION_VIEW, uri)
      .addCategory(Intent.CATEGORY_BROWSABLE)
      .setComponent(target)
      .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
  return try {
    context.startActivity(intent)
    true
  } catch (_: ActivityNotFoundException) {
    false
  } catch (_: SecurityException) {
    false
  }
}

/**
 * Non-http(s) navigations: `mailto:`, `tel:`, and `intent:` links a page uses to jump into an app.
 *
 * An `intent:` whose real target is a web URL is never started as an implicit view. That is the
 * same browser sheet as [openVerifiedAppLink]. The web URL is returned as [OutsideNavigation.Load]
 * so the page stays here, unless a specific non-browser app was named or is the user's default.
 */
internal fun dispatchOutsideWebView(context: Context, uri: Uri): OutsideNavigation {
  val scheme = uri.scheme
  if (scheme == null || scheme == "http" || scheme == "https") return OutsideNavigation.Proceed
  if (scheme != "intent") return launch(context, Intent(Intent.ACTION_VIEW, uri))
  val intent =
    try {
      Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME)
    } catch (_: URISyntaxException) {
      return OutsideNavigation.Failed
    }
  // URI_INTENT_SCHEME already refuses a component, and a page must not be able to add one back.
  intent.component = null
  intent.selector = null
  intent.addCategory(Intent.CATEGORY_BROWSABLE)
  val web = intent.data?.takeIf { it.scheme == "http" || it.scheme == "https" }
  val fallback = intent.getStringExtra("browser_fallback_url")?.takeIf(::isHttpUrl)
  if (web != null) {
    if (launchNamedApp(context, intent)) return OutsideNavigation.Consumed
    val app = nonBrowserDefaultTarget(context, web)
    if (app != null) {
      val handoff =
        Intent(Intent.ACTION_VIEW, web)
          .addCategory(Intent.CATEGORY_BROWSABLE)
          .setComponent(app)
          .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      if (launch(context, handoff) == OutsideNavigation.Consumed) return OutsideNavigation.Consumed
    }
    return OutsideNavigation.Load(fallback ?: web.toString())
  }
  val named = intent.`package`
  if (named == context.packageName || (named != null && isGeneralBrowser(context, named))) {
    intent.`package` = null
  }
  val opened = launch(context, intent)
  if (opened == OutsideNavigation.Consumed) return opened
  return fallback?.let(OutsideNavigation::Load) ?: opened
}

/**
 * The activity that should open [uri] outside the browser, or null when the WebView should keep it.
 *
 * Null covers the usual cases: no handler, several handlers and no default (the system resolver), a
 * browser (including this app), and our own package. Only a preferred or sole non-browser app gets
 * through, which is what "open in the GitHub app" means when the user has actually chosen that.
 */
internal fun nonBrowserDefaultTarget(context: Context, uri: Uri): ComponentName? {
  val scheme = uri.scheme
  if (scheme != "https" && scheme != "http") return null
  val resolved =
    context.packageManager.resolveActivity(
      Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE),
      PackageManager.MATCH_DEFAULT_ONLY,
    ) ?: return null
  val info = resolved.activityInfo ?: return null
  val packageName = info.packageName?.takeIf { it.isNotEmpty() } ?: return null
  val className = info.name?.takeIf { it.isNotEmpty() } ?: return null
  if (packageName == context.packageName) return null
  if (isDisambiguation(packageName, className)) return null
  // A browser handles every https host. The GitHub app does not, so it can still take github.com
  // when the user has chosen it. handleAllWebDataURI would say the same thing, but it is not a
  // public SDK field.
  if (isGeneralBrowser(context, packageName)) return null
  return ComponentName(packageName, className)
}

/**
 * True when [packageName] handles arbitrary https URLs, which is what makes an app a browser.
 * Host-specific apps (the GitHub app, for github.com only) do not.
 */
private fun isGeneralBrowser(context: Context, packageName: String): Boolean {
  if (packageName == context.packageName) return true
  val probe =
    Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com"))
      .addCategory(Intent.CATEGORY_BROWSABLE)
      .setPackage(packageName)
  val matches =
    try {
      context.packageManager.queryIntentActivities(probe, PackageManager.MATCH_ALL)
    } catch (_: RuntimeException) {
      // Package visibility or a broken resolver: treat it as a browser so the page stays here
      // rather than being thrown at a chooser.
      return true
    }
  return matches.isNotEmpty()
}

private fun isDisambiguation(packageName: String, className: String): Boolean {
  val simple = className.substringAfterLast('.').lowercase()
  if (
    simple.contains("resolver") || simple.contains("chooser") || simple.contains("disambiguation")
  ) {
    return true
  }
  val pkg = packageName.lowercase()
  return pkg == "android" ||
    pkg == "com.android.intentresolver" ||
    pkg.startsWith("com.android.internal.")
}

/** Starts [intent] when it names a package that is an app rather than a browser. */
private fun launchNamedApp(context: Context, intent: Intent): Boolean {
  val named = intent.`package` ?: return false
  if (named == context.packageName || isGeneralBrowser(context, named)) {
    intent.`package` = null
    return false
  }
  return launch(context, intent) == OutsideNavigation.Consumed
}

private fun launch(context: Context, intent: Intent): OutsideNavigation =
  try {
    context.startActivity(intent)
    OutsideNavigation.Consumed
  } catch (_: ActivityNotFoundException) {
    OutsideNavigation.NoApp
  } catch (_: SecurityException) {
    OutsideNavigation.Failed
  } catch (_: Exception) {
    OutsideNavigation.Failed
  }

private fun isHttpUrl(url: String): Boolean {
  val scheme = Uri.parse(url).scheme
  return scheme == "http" || scheme == "https"
}

/**
 * True when [current] and [next] are the same document. Used so an `intent:` fallback does not
 * reload the page that just emitted it. Query and fragment are ignored; a trailing slash is not a
 * different page.
 */
internal fun sameWebPage(current: String?, next: String): Boolean {
  val left = pageKey(current) ?: return false
  val right = pageKey(next) ?: return false
  return left == right
}

private fun pageKey(url: String?): String? {
  if (url.isNullOrBlank()) return null
  val uri = Uri.parse(url)
  val scheme = uri.scheme?.lowercase() ?: return null
  val host = uri.host?.lowercase() ?: return null
  val path = uri.path?.trimEnd('/').orEmpty()
  return "$scheme://$host$path"
}
