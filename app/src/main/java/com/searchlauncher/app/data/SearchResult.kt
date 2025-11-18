package com.searchlauncher.app.data

import android.graphics.drawable.Drawable

sealed class SearchResult {
    abstract val id: String
    abstract val title: String
    abstract val subtitle: String?
    abstract val icon: Drawable?

    data class App(
            override val id: String,
            override val title: String,
            override val subtitle: String?,
            override val icon: Drawable?,
            val packageName: String
    ) : SearchResult()

    data class Content(
            override val id: String,
            override val title: String,
            override val subtitle: String?,
            override val icon: Drawable?,
            val packageName: String,
            val deepLink: String?
    ) : SearchResult()

    data class Shortcut(
            override val id: String,
            override val title: String,
            override val subtitle: String?,
            override val icon: Drawable?,
            val packageName: String,
            val intentUri: String
    ) : SearchResult()
}
