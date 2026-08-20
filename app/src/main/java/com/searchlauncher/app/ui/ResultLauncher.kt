package com.searchlauncher.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.searchlauncher.app.data.SearchRepository
import com.searchlauncher.app.data.SearchResult
import com.searchlauncher.app.ui.browser.BrowserActivity
import com.searchlauncher.app.ui.browser.BrowserTabStore
import com.searchlauncher.app.ui.onboarding.OnboardingManager
import com.searchlauncher.app.util.CustomActionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ResultLauncher(
  private val context: Context,
  private val searchRepository: SearchRepository,
  private val scope: CoroutineScope,
  private val onQueryChange: ((String) -> Unit)? = null,
  private val onBindWidgetIntent: ((Intent) -> Boolean)? = null,
  private val onAddWidgetSearch: (() -> Unit)? = null,
  private val onboardingManager: OnboardingManager? = null,
  /**
   * Set when the launcher is hosting the browser itself. Opening a page is then a move within one
   * window, which it can animate in the direction the browser actually lies — to the left of home —
   * rather than an activity start the system slides in from the right like any other app.
   */
  private val onOpenInBrowser: ((String) -> Unit)? = null,
  /** As [onOpenInBrowser], for a tab that already exists. */
  private val onOpenBrowserTab: ((Int) -> Unit)? = null,
) {
  fun launch(
    result: SearchResult,
    query: String = "",
    wasFirstResult: Boolean = false,
    reportUsage: Boolean = true,
  ) {
    when (result) {
      is SearchResult.App -> launchApp(result)
      is SearchResult.Content -> launchContent(result, query)
      is SearchResult.Shortcut -> launchShortcut(result, query)
      is SearchResult.SearchIntent -> {
        // Search intents are handled by SearchScreen because they affect query mode.
      }
      is SearchResult.Contact -> launchContact(result)
      is SearchResult.Snippet -> copySnippet(result)
      is SearchResult.BrowserTab -> launchBrowserTab(result)
      is SearchResult.IndexingIndicator -> {
        // Do nothing
      }
    }

    // Tabs are skipped: their ids last only as long as the tab does, so recording usage against
    // one teaches the ranker nothing and grows the usage store without bound.
    if (
      reportUsage && result !is SearchResult.IndexingIndicator && result !is SearchResult.BrowserTab
    ) {
      searchRepository.reportUsageAsync(result.namespace, result.id, query, wasFirstResult)
    }
  }

  /**
   * Resolves the tab by id rather than trusting the position captured when the result was built:
   * closing a tab elsewhere shifts every index after it. A tab that has since gone away reopens as
   * a fresh one, which is still the page the user asked for.
   */
  private fun launchBrowserTab(result: SearchResult.BrowserTab) {
    val index = BrowserTabStore.indexOfTab(result.tabId)
    if (index >= 0) {
      onOpenBrowserTab?.let {
        it(index)
        return
      }
    } else {
      onOpenInBrowser?.let {
        it(result.url)
        return
      }
    }
    context.startActivity(
      if (index >= 0) BrowserActivity.createResumeIntent(context, index)
      else BrowserActivity.createIntent(context, result.url)
    )
  }

  private fun launchApp(result: SearchResult.App) {
    val launchIntent = context.packageManager.getLaunchIntentForPackage(result.packageName)
    launchIntent?.let {
      it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      context.startActivity(it)
    }
  }

  private fun launchContent(result: SearchResult.Content, query: String) {
    val deepLink = result.deepLink ?: return

    if (deepLink.startsWith("calculator://copy")) {
      val textToCopy = Uri.parse(deepLink).getQueryParameter("text") ?: result.title
      val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
      val clip = ClipData.newPlainText("Calculator Result", textToCopy)
      clipboard.setPrimaryClip(clip)
      Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
      return
    }

    if (deepLink.startsWith("timer://set")) {
      val uri = Uri.parse(deepLink)
      val seconds = uri.getQueryParameter("seconds")?.toIntOrNull() ?: return
      val name = uri.getQueryParameter("name")
      val timerIntent =
        Intent(android.provider.AlarmClock.ACTION_SET_TIMER).apply {
          putExtra(android.provider.AlarmClock.EXTRA_LENGTH, seconds)
          if (name != null) putExtra(android.provider.AlarmClock.EXTRA_MESSAGE, name)
          addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
      try {
        context.startActivity(timerIntent)
      } catch (e: android.content.ActivityNotFoundException) {
        Toast.makeText(context, "No timer app found", Toast.LENGTH_SHORT).show()
      }
      return
    }

    try {
      val intent =
        if (deepLink.startsWith("intent:")) {
          Intent.parseUri(deepLink, Intent.URI_INTENT_SCHEME)
        } else {
          Intent(Intent.ACTION_VIEW, Uri.parse(deepLink))
        }
      val uri = intent.data
      if (uri?.scheme == "content") {
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val mime = result.packageName.takeIf { it.contains('/') }
        if (mime != null && intent.type == null) {
          intent.setDataAndType(uri, mime)
        }
      }
      launchIntent(intent, query)
    } catch (e: Exception) {
      e.printStackTrace()
      Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
    }
  }

  private fun launchShortcut(result: SearchResult.Shortcut, query: String) {
    try {
      val uri = result.intentUri
      if (uri.startsWith("shortcut://")) {
        // Only the package is delimited: shortcut ids are arbitrary app-chosen strings and often
        // contain slashes themselves (chat shortcuts tend to embed a conversation path), so
        // everything after the first slash belongs to the id.
        val target = uri.substring("shortcut://".length)
        val pkg = target.substringBefore('/')
        val id = target.substringAfter('/', "")
        if (pkg.isEmpty() || id.isEmpty()) {
          Toast.makeText(context, "Error launching shortcut", Toast.LENGTH_SHORT).show()
          return
        }
        val launcherApps =
          context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as android.content.pm.LauncherApps
        // Android only lets the active home app start another app's shortcut.
        if (!launcherApps.hasShortcutHostPermission()) {
          Toast.makeText(
              context,
              "Set SearchLauncher as your default launcher to open app shortcuts",
              Toast.LENGTH_LONG,
            )
            .show()
          return
        }
        launcherApps.startShortcut(pkg, id, null, null, android.os.Process.myUserHandle())
      } else {
        launchIntent(Intent.parseUri(uri, Intent.URI_INTENT_SCHEME), query)
      }
    } catch (e: Exception) {
      e.printStackTrace()
      Toast.makeText(context, "Error launching shortcut: ${e.message}", Toast.LENGTH_LONG).show()
    }
  }

  private fun launchIntent(intent: Intent, query: String) {
    when (intent.action) {
      ACTION_BIND_WIDGET -> {
        val handled = onBindWidgetIntent?.invoke(intent) == true
        if (!handled) {
          Toast.makeText(context, "Cannot bind widget: Activity not found", Toast.LENGTH_SHORT)
            .show()
        }
      }
      ACTION_APPEND_SPACE -> onQueryChange?.invoke(query + " ")
      ACTION_ADD_WIDGET -> onAddWidgetSearch?.invoke()
      ACTION_RESET_INDEX -> resetIndex()
      ACTION_RESET_APP_DATA -> resetAppData()
      ACTION_RESET_ONBOARDING -> resetOnboarding()
      else -> {
        if (!CustomActionHandler.handleAction(context, intent)) {
          val uri = intent.data
          if (
            intent.action == Intent.ACTION_VIEW &&
              uri != null &&
              (uri.scheme == "http" || uri.scheme == "https")
          ) {
            onOpenInBrowser?.invoke(uri.toString())
              ?: context.startActivity(BrowserActivity.createIntent(context, uri.toString()))
          } else {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
          }
        }
      }
    }
  }

  private fun resetIndex() {
    scope.launch {
      searchRepository.resetIndex()
      withContext(Dispatchers.Main) {
        Toast.makeText(context, "Search Index Reset", Toast.LENGTH_SHORT).show()
      }
    }
  }

  private fun resetAppData() {
    scope.launch {
      searchRepository.resetAppData()
      withContext(Dispatchers.Main) {
        Toast.makeText(context, "App Data Reset", Toast.LENGTH_SHORT).show()
      }
    }
  }

  private fun resetOnboarding() {
    onQueryChange?.invoke("")
    scope.launch {
      onboardingManager?.resetOnboarding()
      withContext(Dispatchers.Main) {
        Toast.makeText(context, "Onboarding Reset", Toast.LENGTH_SHORT).show()
      }
    }
  }

  private fun launchContact(result: SearchResult.Contact) {
    try {
      val intent = Intent(Intent.ACTION_VIEW)
      val uri =
        Uri.withAppendedPath(
          android.provider.ContactsContract.Contacts.CONTENT_LOOKUP_URI,
          result.lookupKey,
        )
      intent.data = uri
      intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      context.startActivity(intent)
    } catch (e: Exception) {
      e.printStackTrace()
      Toast.makeText(context, "Error opening contact", Toast.LENGTH_SHORT).show()
    }
  }

  private fun copySnippet(result: SearchResult.Snippet) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText(result.alias, result.content)
    clipboard.setPrimaryClip(clip)
    Toast.makeText(context, "Copied ${result.content}", Toast.LENGTH_SHORT).show()
  }

  companion object {
    const val ACTION_BIND_WIDGET = "com.searchlauncher.action.BIND_WIDGET"
    const val ACTION_APPEND_SPACE = "com.searchlauncher.action.APPEND_SPACE"
    const val ACTION_ADD_WIDGET = "com.searchlauncher.action.ADD_WIDGET"
    const val ACTION_RESET_INDEX = "com.searchlauncher.RESET_INDEX"
    const val ACTION_RESET_APP_DATA = "com.searchlauncher.RESET_APP_DATA"
    const val ACTION_RESET_ONBOARDING = "com.searchlauncher.action.RESET_ONBOARDING"
  }
}
