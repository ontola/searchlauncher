package com.searchlauncher.app.data

import android.content.ComponentName
import android.graphics.drawable.Drawable
import android.os.UserHandle

sealed class SearchResult {
  abstract val id: String
  abstract val namespace: String
  abstract val title: String
  abstract val subtitle: String?
  abstract val icon: Drawable?
  abstract val rankingScore: Int

  data class App(
    override val id: String,
    override val namespace: String = "apps",
    override val title: String,
    override val subtitle: String?,
    override val icon: Drawable?,
    override val rankingScore: Int = 0,
    val packageName: String,
    val isPrivate: Boolean = false,
    val userHandle: UserHandle? = null,
    val componentName: ComponentName? = null,
  ) : SearchResult()

  data class PrivateSpace(
    override val id: String = PrivateSpaceQuery.CONTROL_ID,
    override val namespace: String = PrivateSpaceQuery.NAMESPACE,
    override val title: String,
    override val subtitle: String?,
    override val icon: Drawable?,
    override val rankingScore: Int = 0,
    val unlocked: Boolean,
  ) : SearchResult()

  data class Content(
    override val id: String,
    override val namespace: String = "custom_shortcuts",
    override val title: String,
    override val subtitle: String?,
    override val icon: Drawable?,
    override val rankingScore: Int = 0,
    val packageName: String,
    val deepLink: String?,
  ) : SearchResult()

  data class Shortcut(
    override val id: String,
    override val namespace: String = "shortcuts",
    override val title: String,
    override val subtitle: String?,
    override val icon: Drawable?,
    override val rankingScore: Int = 0,
    val packageName: String,
    val intentUri: String,
    val appIcon: Drawable? = null,
  ) : SearchResult()

  data class SearchIntent(
    override val id: String,
    override val namespace: String = "custom_shortcuts",
    override val title: String,
    override val subtitle: String?,
    override val icon: Drawable?,
    override val rankingScore: Int = 0,
    val trigger: String,
  ) : SearchResult()

  data class Contact(
    override val id: String,
    override val namespace: String = "contacts",
    override val title: String,
    override val subtitle: String?,
    override val icon: Drawable?,
    override val rankingScore: Int = 0,
    val lookupKey: String,
    val contactId: Long,
    val photoUri: String?,
  ) : SearchResult()

  data class Snippet(
    override val id: String,
    override val namespace: String = "snippets",
    override val title: String,
    override val subtitle: String?,
    override val icon: Drawable?,
    override val rankingScore: Int = 0,
    val alias: String,
    val content: String,
  ) : SearchResult()

  /**
   * A page currently open in the browser. Not indexed like history is — these are read live from
   * the open tabs, so they come and go as the user browses.
   */
  data class BrowserTab(
    override val id: String,
    override val namespace: String = "browser_tabs",
    override val title: String,
    override val subtitle: String?,
    override val icon: Drawable?,
    override val rankingScore: Int = 0,
    /** Identifies the tab across list changes, unlike its position. */
    val tabId: Long,
    /** Reopened as a new tab if this one is gone by the time the result is tapped. */
    val url: String,
  ) : SearchResult()

  data class IndexingIndicator(
    override val id: String = "indexing_indicator",
    override val namespace: String = "system",
    override val title: String = "Indexing, results loading...",
    override val subtitle: String? = null,
    override val icon: Drawable? = null,
    override val rankingScore: Int = Int.MAX_VALUE,
  ) : SearchResult()
}
