package com.searchlauncher.app.data

data class CustomShortcut(
        val trigger: String,
        val urlTemplate: String,
        val description: String,
        val packageName: String? = null
)

object CustomShortcuts {
    val shortcuts =
            listOf(
                    CustomShortcut("g", "https://www.google.com/search?q=%s", "Google Search"),
                    CustomShortcut(
                            "ff",
                            "https://www.google.com/search?q=%s",
                            "Firefox Search",
                            "org.mozilla.firefox"
                    ),
                    CustomShortcut(
                            "yt",
                            "https://www.youtube.com/results?search_query=%s",
                            "YouTube Search"
                    ),
                    CustomShortcut("maps", "https://www.google.com/maps/search/%s", "Google Maps"),
                    CustomShortcut(
                            "play",
                            "https://play.google.com/store/search?q=%s",
                            "Play Store Search"
                    ),
                    CustomShortcut("r", "https://www.reddit.com/search/?q=%s", "Reddit Search"),
                    CustomShortcut(
                            "w",
                            "https://en.wikipedia.org/w/index.php?search=%s",
                            "Wikipedia Search"
                    ),
                    CustomShortcut(
                            "gpt",
                            "https://chatgpt.com/?q=%s",
                            "ChatGPT"
                    ) // Direct query might not work for all, but opens the site
            )
}
