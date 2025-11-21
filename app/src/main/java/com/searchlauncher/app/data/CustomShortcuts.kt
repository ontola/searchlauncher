package com.searchlauncher.app.data

sealed class CustomShortcut {
    abstract val description: String
    abstract val packageName: String?

    data class Search(
            val trigger: String,
            val urlTemplate: String,
            override val description: String,
            override val packageName: String? = null,
            val suggestionUrl: String? = null,
            val color: Long? = null
    ) : CustomShortcut()

    data class Action(
            val intentUri: String,
            override val description: String,
            override val packageName: String? = null
    ) : CustomShortcut()
}

object CustomShortcuts {
    private val settingsActions =
            listOf(
                    "android.settings.ACCESSIBILITY_SETTINGS",
                    "android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS",
                    "android.settings.ADD_ACCOUNT_SETTINGS",
                    "android.settings.AIRPLANE_MODE_SETTINGS",
                    "android.settings.APN_SETTINGS",
                    "android.settings.APPLICATION_DETAILS_SETTINGS",
                    "android.settings.APPLICATION_DEVELOPMENT_SETTINGS",
                    "android.settings.APPLICATION_SETTINGS",
                    "android.settings.APP_NOTIFICATION_SETTINGS",
                    "android.settings.BATTERY_SAVER_SETTINGS",
                    "android.settings.BLUETOOTH_SETTINGS",
                    "android.settings.CAPTIONING_SETTINGS",
                    "android.settings.CAST_SETTINGS",
                    "android.settings.CHANNEL_NOTIFICATION_SETTINGS",
                    "android.settings.DATA_ROAMING_SETTINGS",
                    "android.settings.DATA_USAGE_SETTINGS",
                    "android.settings.DATE_SETTINGS",
                    "android.settings.DEVICE_INFO_SETTINGS",
                    "android.settings.DISPLAY_SETTINGS",
                    "android.settings.DREAM_SETTINGS",
                    "android.settings.ENTERPRISE_PRIVACY_SETTINGS",
                    "android.settings.FINGERPRINT_ENROLL",
                    "android.settings.HARD_KEYBOARD_SETTINGS",
                    "android.settings.HOME_SETTINGS",
                    "android.settings.IGNORE_BACKGROUND_DATA_RESTRICTIONS_SETTINGS",
                    "android.settings.IGNORE_BATTERY_OPTIMIZATION_SETTINGS",
                    "android.settings.INPUT_METHOD_SETTINGS",
                    "android.settings.INPUT_METHOD_SUBTYPE_SETTINGS",
                    "android.settings.INTERNAL_STORAGE_SETTINGS",
                    "android.settings.LOCALE_SETTINGS",
                    "android.settings.LOCATION_SOURCE_SETTINGS",
                    "android.settings.MANAGE_ALL_APPLICATIONS_SETTINGS",
                    "android.settings.MANAGE_APPLICATIONS_SETTINGS",
                    "android.settings.MANAGE_DEFAULT_APPS_SETTINGS",
                    "android.settings.MANAGE_UNKNOWN_APP_SOURCES",
                    "android.settings.MEMORY_CARD_SETTINGS",
                    "android.settings.NETWORK_OPERATOR_SETTINGS",
                    "android.settings.NFCSHARING_SETTINGS",
                    "android.settings.NFC_PAYMENT_SETTINGS",
                    "android.settings.NFC_SETTINGS",
                    "android.settings.NIGHT_DISPLAY_SETTINGS",
                    "android.settings.NOTIFICATION_POLICY_ACCESS_SETTINGS",
                    "android.settings.PRIVACY_SETTINGS",
                    "android.settings.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS",
                    "android.settings.REQUEST_SET_AUTOFILL_SERVICE",
                    "android.settings.SECURITY_SETTINGS",
                    "android.settings.SHOW_REGULATORY_INFO",
                    "android.settings.SOUND_SETTINGS",
                    "android.settings.STORAGE_VOLUME_ACCESS_SETTINGS",
                    "android.settings.SYNC_SETTINGS",
                    "android.settings.USAGE_ACCESS_SETTINGS",
                    "android.settings.VPN_SETTINGS",
                    "android.settings.VR_LISTENER_SETTINGS",
                    "android.settings.WEBVIEW_SETTINGS",
                    "android.settings.WIFI_SETTINGS",
                    "android.settings.WIRELESS_SETTINGS",
                    "android.settings.ZEN_MODE_PRIORITY_SETTINGS",
                    "android.settings.action.MANAGE_OVERLAY_PERMISSION",
                    "android.settings.action.MANAGE_WRITE_SETTINGS"
            )

    private fun generateSettingsShortcuts(): List<CustomShortcut.Action> {
        return settingsActions.map { action ->
            val name =
                    action.substringAfterLast(".")
                            .replace("_", " ")
                            .lowercase()
                            .split(" ")
                            .joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }
                            .replace("Settings", "")
                            .trim() + " Settings"

            CustomShortcut.Action(
                    intentUri = "intent:#Intent;action=$action;end",
                    description = name
            )
        }
    }

    // DEPRECATED: This is kept for backward compatibility with backup/restore only
    // New shortcuts should use DefaultShortcuts.searchShortcuts and DefaultShortcuts.appShortcuts
    val shortcuts = emptyList<CustomShortcut>()

    // Old default shortcuts (kept for reference, but not used)
    private val oldShortcuts =
            listOf(
                    CustomShortcut.Search(
                            trigger = "g",
                            urlTemplate = "https://www.google.com/search?q=%s",
                            description = "Google Search",
                            suggestionUrl =
                                    "http://suggestqueries.google.com/complete/search?client=firefox&q=%s",
                            color = 0xFF4285F4
                    ),
                    CustomShortcut.Search(
                            trigger = "dd",
                            urlTemplate = "https://duckduckgo.com/?q=%s",
                            description = "DuckDuckGo Search",
                            suggestionUrl = "https://ac.duckduckgo.com/ac/?q=%s&type=list",
                            color = 0xFFDE5833
                    ),
                    CustomShortcut.Search(
                            trigger = "bing",
                            urlTemplate = "https://www.bing.com/search?q=%s",
                            description = "Bing Search",
                            suggestionUrl = "https://api.bing.com/osjson.aspx?query=%s",
                            color = 0xFF008373
                    ),
                    CustomShortcut.Search(
                            trigger = "call",
                            urlTemplate = "tel:%s",
                            description = "Call",
                            color = 0xFF4CAF50
                    ),
                    CustomShortcut.Search(
                            trigger = "sms",
                            urlTemplate = "sms:%s",
                            description = "Send SMS",
                            color = 0xFF2196F3
                    ),
                    CustomShortcut.Search(
                            trigger = "mailto",
                            urlTemplate = "mailto:%s",
                            description = "Send Email to",
                            color = 0xFFF44336
                    ),
                    CustomShortcut.Search(
                            trigger = "cal",
                            urlTemplate =
                                    "intent:#Intent;action=android.intent.action.INSERT;type=vnd.android.cursor.item/event;S.title=%s;end",
                            description = "Add Calendar Item",
                            color = 0xFF3F51B5
                    ),
                    CustomShortcut.Search(
                            trigger = "ff",
                            urlTemplate = "https://www.google.com/search?q=%s",
                            description = "Firefox Search",
                            packageName = "org.mozilla.firefox",
                            color = 0xFFFF9800
                    ),
                    CustomShortcut.Search(
                            trigger = "yt",
                            urlTemplate = "https://www.youtube.com/results?search_query=%s",
                            description = "YouTube Search",
                            suggestionUrl =
                                    "http://suggestqueries.google.com/complete/search?client=firefox&ds=yt&q=%s",
                            color = 0xFFFF0000
                    ),
                    CustomShortcut.Search(
                            trigger = "nav",
                            urlTemplate = "geo:0,0?q=%s",
                            description = "Navigate to",
                            color = 0xFF009688
                    ),
                    CustomShortcut.Search(
                            trigger = "maps",
                            urlTemplate = "https://www.google.com/maps/search/%s",
                            description = "Google Maps Search",
                            color = 0xFF34A853
                    ),
                    CustomShortcut.Search(
                            trigger = "r",
                            urlTemplate = "https://www.reddit.com/search/?q=%s",
                            description = "Reddit Search",
                            color = 0xFFFF4500
                    ),
                    CustomShortcut.Search(
                            trigger = "tt",
                            urlTemplate = "https://www.tiktok.com/search?q=%s",
                            description = "TikTok Search",
                            color = 0xFF000000
                    ),
                    CustomShortcut.Search(
                            trigger = "w",
                            urlTemplate = "https://en.wikipedia.org/w/index.php?search=%s",
                            description = "Wikipedia Search",
                            color = 0xFF808080
                    ),
                    CustomShortcut.Search(
                            trigger = "gpt",
                            urlTemplate = "https://chatgpt.com/?q=%s",
                            description = "ChatGPT",
                            color = 0xFF10A37F
                    ),
                    CustomShortcut.Search(
                            trigger = "c",
                            urlTemplate = "https://chatgpt.com/?q=%s",
                            description = "Ask ChatGPT",
                            color = 0xFF10A37F
                    ),
                    CustomShortcut.Search(
                            trigger = "p",
                            urlTemplate = "market://search?q=%s",
                            description = "Play Store Search",
                            color = 0xFF01875F
                    ),
                    CustomShortcut.Search(
                            trigger = "s",
                            urlTemplate = "spotify:search:%s",
                            description = "Spotify Search",
                            packageName = "com.spotify.music",
                            color = 0xFF1DB954
                    ),
                    CustomShortcut.Action(
                            intentUri =
                                    "intent:#Intent;action=org.mozilla.fenix.OPEN_PRIVATE_TAB;package=org.mozilla.firefox;component=org.mozilla.firefox/org.mozilla.fenix.IntentReceiverActivity;end",
                            description = "Firefox Private Tab",
                            packageName = "org.mozilla.firefox"
                    ),
                    CustomShortcut.Action(
                            intentUri = "intent:#Intent;action=com.searchlauncher.RESET_INDEX;end",
                            description = "Reset Search Index"
                    ),
                    CustomShortcut.Action(
                            intentUri =
                                    "intent:#Intent;action=com.searchlauncher.RESET_APP_DATA;end",
                            description = "Reset App Data"
                    ),
                    CustomShortcut.Action(
                            intentUri =
                                    "intent:#Intent;action=com.searchlauncher.action.TOGGLE_FLASHLIGHT;end",
                            description = "Toggle Flashlight"
                    ),
                    CustomShortcut.Action(
                            intentUri =
                                    "intent:#Intent;action=android.media.action.STILL_IMAGE_CAMERA;i.android.intent.extras.CAMERA_FACING=1;end",
                            description = "Selfie Camera"
                    ),
                    CustomShortcut.Action(
                            intentUri =
                                    "intent:#Intent;action=com.searchlauncher.action.TOGGLE_ROTATION;end",
                            description = "Toggle Rotation Lock"
                    ),
                    CustomShortcut.Action(
                            intentUri = "intent:#Intent;action=android.settings.HOME_SETTINGS;end",
                            description = "Set as Launcher"
                    ),
                    CustomShortcut.Action(
                            intentUri =
                                    "intent:#Intent;action=com.searchlauncher.action.CREATE_QUICK_COPY;end",
                            description = "Create Quick Copy"
                    ),
                    CustomShortcut.Action(
                            intentUri = "intent:#Intent;action=android.settings.SETTINGS;end",
                            description = "Settings",
                            packageName = "com.android.settings"
                    )
            ) // + generateSettingsShortcuts() - removed to keep old list minimal
}
