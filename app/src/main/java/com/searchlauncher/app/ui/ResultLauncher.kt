package com.searchlauncher.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.searchlauncher.app.data.SearchRepository
import com.searchlauncher.app.data.SearchResult
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
    }

    if (reportUsage) {
      searchRepository.reportUsageAsync(result.namespace, result.id, query, wasFirstResult)
    }
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
        val parts = uri.substring("shortcut://".length).split("/")
        if (parts.size == 2) {
          val pkg = parts[0]
          val id = parts[1]
          val launcherApps =
            context.getSystemService(Context.LAUNCHER_APPS_SERVICE)
              as android.content.pm.LauncherApps
          launcherApps.startShortcut(pkg, id, null, null, android.os.Process.myUserHandle())
        }
      } else {
        launchIntent(Intent.parseUri(uri, Intent.URI_INTENT_SCHEME), query)
      }
    } catch (e: Exception) {
      e.printStackTrace()
      Toast.makeText(context, "Error launching shortcut", Toast.LENGTH_SHORT).show()
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
          intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
          context.startActivity(intent)
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
