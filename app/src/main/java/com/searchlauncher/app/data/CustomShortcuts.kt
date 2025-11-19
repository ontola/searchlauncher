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
                        CustomShortcut("call", "tel:%s", "Call"),
                        CustomShortcut("sms", "sms:%s", "Send SMS"),
                        CustomShortcut("mailto", "mailto:%s", "Send Email to"),
                        CustomShortcut("gmail", "gmail://search/%s", "Gmail Search"),
                        CustomShortcut("tg", "tg://search?q=%s", "Telegram Search"),
                        CustomShortcut(
                                "adbw",
                                "intent:#Intent;action=android.settings.WIRELESS_DEBUGGING_SETTINGS;end",
                                "ADB Wireless Debugging Settings"
                        ),
                        CustomShortcut(
                                "wifi",
                                "intent:#Intent;action=android.settings.WIFI_SETTINGS;end",
                                "Wi-Fi Settings"
                        ),
                        CustomShortcut(
                                "bt",
                                "intent:#Intent;action=android.settings.BLUETOOTH_SETTINGS;end",
                                "Bluetooth Settings"
                        ),
                        CustomShortcut(
                                "noti",
                                "intent:#Intent;action=android.settings.APP_NOTIFICATION_SETTINGS;end",
                                "Notification Settings"
                        ),
                        CustomShortcut(
                                "apps",
                                "intent:#Intent;action=android.settings.APPLICATION_SETTINGS;end",
                                "App Settings"
                        ),
                        CustomShortcut(
                                "pkg",
                                "intent:#Intent;action=android.settings.APPLICATION_DETAILS_SETTINGS;end",
                                "App Details"
                        ),
                        CustomShortcut(
                                "cal",
                                "intent:#Intent;action=android.intent.action.INSERT;type=vnd.android.cursor.item/event;S.title=%s;end",
                                "Add Calendar Item"
                        ),
                        CustomShortcut(
                                "ff",
                                "https://www.google.com/search?q=%s",
                                "Firefox Search",
                                "org.mozilla.firefox"
                        ),
                        CustomShortcut("yt", "vnd.youtube:%s", "YouTube Search"),
                        CustomShortcut("maps", "geo:0,0?q=%s", "Google Maps"),
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
                        ), // Direct query might not work for all, but opens the site
                        CustomShortcut(
                                "s",
                                "spotify:search:%s",
                                "Spotify Search",
                                "com.spotify.music"
                        )
                )
}
