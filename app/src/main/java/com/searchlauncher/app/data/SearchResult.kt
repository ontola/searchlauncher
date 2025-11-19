package com.searchlauncher.app.data

import android.graphics.drawable.Drawable

sealed class SearchResult {
        abstract val id: String
        abstract val namespace: String
        abstract val title: String
        abstract val subtitle: String?
        abstract val icon: Drawable?

        data class App(
                override val id: String,
                override val namespace: String = "apps",
                override val title: String,
                override val subtitle: String?,
                override val icon: Drawable?,
                val packageName: String
        ) : SearchResult()

        data class Content(
                override val id: String,
                override val namespace: String = "custom_shortcuts",
                override val title: String,
                override val subtitle: String?,
                override val icon: Drawable?,
                val packageName: String,
                val deepLink: String?
        ) : SearchResult()

        data class Shortcut(
                override val id: String,
                override val namespace: String = "shortcuts",
                override val title: String,
                override val subtitle: String?,
                override val icon: Drawable?,
                val packageName: String,
                val intentUri: String
        ) : SearchResult()

        data class SearchIntent(
                override val id: String,
                override val namespace: String = "custom_shortcuts",
                override val title: String,
                override val subtitle: String?,
                override val icon: Drawable?,
                val trigger: String
        ) : SearchResult()
}
