package com.searchlauncher.app.ui.browser

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Each browser tab is its own Android task, so the system's app switcher lists tabs the way it
 * lists apps — one card per page, with its own title, icon and preview.
 *
 * Tabs used to live inside one window (and, for a while, inside the launcher's own home task, which
 * the switcher never shows at all), so getting back to a particular page from another app meant
 * finding the browser and then finding the tab. A task per tab is what Android gives us for
 * "several windows of the same app", and it is what makes returning to a page a single gesture.
 *
 * The task itself is the record: [BrowserTabStore] holds the page state, but which tasks exist is
 * the system's answer, not ours, because the user can dismiss a card without telling the app.
 */
internal object BrowserTabTasks {
  /**
   * Distinguishes one tab's task from another's. Document tasks are keyed by their intent's
   * component and data, so the tab id has to be in the URI — an extra alone would put every tab in
   * the same task.
   */
  private const val TAB_SCHEME = "searchlauncher-tab"

  fun tabUri(tabId: Long): Uri = Uri.fromParts(TAB_SCHEME, tabId.toString(), null)

  /** True for [tabUri] results, which name a tab rather than a page and must never be navigated. */
  fun isTabUri(uri: String?): Boolean = uri?.startsWith("$TAB_SCHEME:") == true

  fun tabIdOf(intent: Intent): Long? {
    val data = intent.data ?: return null
    if (data.scheme != TAB_SCHEME) return null
    return data.schemeSpecificPart?.toLongOrNull()
  }

  /**
   * The intent that owns [tabId]'s task. Launching it either creates that task or brings the
   * existing one forward with the intent delivered to onNewIntent — `intoExisting` document mode
   * does the matching for us, keyed on the URI above.
   *
   * No window animation: whoever launches this has already moved the tab across the screen itself
   * (a swipe, a card growing out of the overview), and a second transition on top of that breaks
   * the illusion of one continuous movement.
   */
  fun intentFor(context: Context, tabId: Long): Intent =
    Intent(context, BrowserActivity::class.java).apply {
      data = tabUri(tabId)
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
    }

  /** Brings [tabId]'s window to the front, opening it if the tab has never had one. */
  fun open(context: Context, tabId: Long) {
    context.startActivity(intentFor(context, tabId))
  }

  /** Opens the newest tab, which is where a swipe in from the launcher always lands. */
  fun openNewestTab(context: Context): Boolean {
    val tabId = BrowserTabStore.lastTab()?.id ?: return false
    open(context, tabId)
    return true
  }

  /**
   * Takes [tabId]'s card out of the switcher, for a tab closed from somewhere other than its own
   * window — the launcher's overview, a search result's menu, or the tab cap evicting it.
   *
   * Silent when there is no such task: a tab that has never been opened has no card to remove, and
   * the caller should not have to know which.
   */
  fun close(context: Context, tabId: Long) {
    forEachTabTask(context) { task, id -> if (id == tabId) task.finishAndRemoveTask() }
  }

  fun closeAll(context: Context) {
    forEachTabTask(context) { task, _ -> task.finishAndRemoveTask() }
  }

  /**
   * Drops tabs whose card the user has swiped away in the switcher.
   *
   * Dismissing a card is how Android says "close this window", and for a tab that has to mean the
   * tab is gone — otherwise the launcher goes on offering a page in its overview that nothing can
   * be brought back to. The activity normally reports its own dismissal, but a card dismissed while
   * its window was never built has no activity to report it, so the surviving tasks are the honest
   * account and this reconciles against them.
   *
   * Only tabs that have actually had a window are judged: one created a moment ago, whose task is
   * still being made, has no card yet and is not abandoned.
   */
  fun forgetDismissedTabs(context: Context) {
    val tabs = BrowserTabStore.tabs ?: return
    val live = mutableSetOf<Long>()
    forEachTabTask(context) { _, id -> live += id }
    val dismissed = tabs.items.filter { it.hasOwnTask && it.id !in live }.map { it.id }
    dismissed.forEach { BrowserTabStore.close(it) }
  }

  private inline fun forEachTabTask(
    context: Context,
    action: (ActivityManager.AppTask, Long) -> Unit,
  ) {
    val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return
    // Guarded wholesale: listing the tasks and reading the info behind each one both throw if the
    // system retires a task in between, and losing the whole tab list to that race would be a worse
    // answer than skipping one pass.
    runCatching {
      for (task in manager.appTasks) {
        val id = task.taskInfo?.baseIntent?.let(::tabIdOf) ?: continue
        action(task, id)
      }
    }
  }
}
