package com.searchlauncher.app.data

import android.graphics.drawable.Drawable

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
        val packageName: String
    ) : SearchResult()

    data class Content(
        override val id: String,
        override val namespace: String = "custom_shortcuts",
        override val title: String,
        override val subtitle: String?,
        override val icon: Drawable?,
        override val rankingScore: Int = 0,
        val packageName: String,
        val deepLink: String?
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
        val appIcon: Drawable? = null
    ) : SearchResult()

    data class SearchIntent(
        override val id: String,
        override val namespace: String = "custom_shortcuts",
        override val title: String,
        override val subtitle: String?,
        override val icon: Drawable?,
        override val rankingScore: Int = 0,
        val trigger: String
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
        val photoUri: String?
    ) : SearchResult()

    data class QuickCopy(
        override val id: String,
        override val namespace: String = "quickcopy",
        override val title: String,
        override val subtitle: String?,
        override val icon: Drawable?,
        override val rankingScore: Int = 0,
        val alias: String,
        val content: String
    ) : SearchResult()
}
