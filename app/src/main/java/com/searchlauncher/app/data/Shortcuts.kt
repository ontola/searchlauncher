package com.searchlauncher.app.data

/** User-editable search shortcuts with customizable aliases */
data class SearchShortcut(
  val id: String, // Unique identifier
  val alias: String, // User-editable trigger/alias
  val urlTemplate: String,
  val description: String,
  val packageName: String? = null,
  val suggestionUrl: String? = null,
  val color: Long? = null,
  val shortLabel: String? = null,
)

/** App-defined shortcuts that are not user-editable */
sealed class AppShortcut {
  abstract val id: String
  abstract val description: String
  abstract val packageName: String?

  data class Action(
    override val id: String,
    val intentUri: String,
    override val description: String,
    override val packageName: String? = null,
  ) : AppShortcut()
}

object DefaultShortcuts {
  // App-defined actions and settings (not editable by user)
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
      "android.settings.action.MANAGE_WRITE_SETTINGS",
    )

  private fun generateSettingsShortcuts(): List<AppShortcut.Action> {
    return settingsActions.map { action ->
      val name =
        action
          .substringAfterLast(".")
          .replace("_", " ")
          .lowercase()
          .split(" ")
          .joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }
          .replace("Settings", "")
          .trim() + " Settings"

      AppShortcut.Action(
        id = "settings_$action",
        intentUri = "intent:#Intent;action=$action;end",
        description = name,
      )
    }
  }

  val appShortcuts =
    listOf(
      AppShortcut.Action(
        id = "reset_index",
        intentUri = "intent:#Intent;action=com.searchlauncher.RESET_INDEX;end",
        description = "Reset Search Index",
      ),
      AppShortcut.Action(
        id = "reset_app_data",
        intentUri = "intent:#Intent;action=com.searchlauncher.RESET_APP_DATA;end",
        description = "Reset App Data",
      ),
      AppShortcut.Action(
        id = "add_widget",
        intentUri = "intent:#Intent;action=com.searchlauncher.action.ADD_WIDGET;end",
        description = "Add Widget",
      ),
      AppShortcut.Action(
        id = "toggle_flashlight",
        intentUri = "intent:#Intent;action=com.searchlauncher.action.TOGGLE_FLASHLIGHT;end",
        description = "Toggle Flashlight",
      ),
      AppShortcut.Action(
        id = "selfie_camera",
        intentUri =
          "intent:#Intent;action=android.media.action.STILL_IMAGE_CAMERA;i.android.intent.extras.CAMERA_FACING=1;end",
        description = "Selfie Camera",
      ),
      AppShortcut.Action(
        id = "camera",
        intentUri = "intent:#Intent;action=android.media.action.STILL_IMAGE_CAMERA;end",
        description = "Camera",
      ),
      AppShortcut.Action(
        id = "video_camera",
        intentUri = "intent:#Intent;action=android.media.action.VIDEO_CAMERA;end",
        description = "Video Camera",
      ),
      AppShortcut.Action(
        id = "toggle_rotation",
        intentUri = "intent:#Intent;action=com.searchlauncher.action.TOGGLE_ROTATION;end",
        description = "Toggle Rotation Lock",
      ),
      AppShortcut.Action(
        id = "set_launcher",
        intentUri = "intent:#Intent;action=android.settings.HOME_SETTINGS;end",
        description = "Set as Launcher",
      ),
      AppShortcut.Action(
        id = "create_snippet",
        intentUri = "intent:#Intent;action=com.searchlauncher.action.CREATE_SNIPPET;end",
        description = "Create snippet",
      ),
      AppShortcut.Action(
        id = "settings",
        intentUri = "intent:#Intent;action=android.settings.SETTINGS;end",
        description = "System Settings",
        packageName = "com.android.settings",
      ),
      AppShortcut.Action(
        id = "restart_launcher",
        intentUri = "intent:#Intent;action=com.searchlauncher.action.RESTART;end",
        description = "Restart SearchLauncher",
      ),
      AppShortcut.Action(
        id = "reboot",
        intentUri = "intent:#Intent;action=com.searchlauncher.action.REBOOT;end",
        description = "Reboot Phone",
      ),
      AppShortcut.Action(
        id = "lock_screen",
        intentUri = "intent:#Intent;action=com.searchlauncher.action.LOCK_SCREEN;end",
        description = "Lock Screen",
      ),
      AppShortcut.Action(
        id = "power_menu",
        intentUri = "intent:#Intent;action=com.searchlauncher.action.POWER_MENU;end",
        description = "Power Menu",
      ),
      AppShortcut.Action(
        id = "screenshot",
        intentUri = "intent:#Intent;action=com.searchlauncher.action.SCREENSHOT;end",
        description = "Take Screenshot",
      ),
      AppShortcut.Action(
        id = "toggle_dark_mode",
        intentUri = "intent:#Intent;action=com.searchlauncher.action.TOGGLE_DARK_MODE;end",
        description = "Toggle Dark Mode",
      ),
      AppShortcut.Action(
        id = "launcher_settings",
        intentUri =
          "intent:#Intent;action=android.intent.action.MAIN;category=android.intent.category.LAUNCHER;component=com.searchlauncher.app/.ui.MainActivity;B.open_settings=true;end",
        description = "SearchLauncher Settings",
      ),
      AppShortcut.Action(
        id = "reset_onboarding",
        intentUri = "intent:#Intent;action=com.searchlauncher.action.RESET_ONBOARDING;end",
        description = "Start Onboarding",
      ),
    ) + generateSettingsShortcuts()

  // User-editable search shortcuts with default aliases
  val searchShortcuts =
    listOf(
      SearchShortcut(
        id = "google",
        alias = "g",
        urlTemplate = "https://www.google.com/search?q=%s",
        description = "Google Search",
        suggestionUrl = "http://suggestqueries.google.com/complete/search?client=firefox&q=%s",
        color = 0xFF4285F4,
        shortLabel = "Google",
      ),
      SearchShortcut(
        id = "gemini",
        alias = "gem",
        urlTemplate = "https://gemini.google.com/app?q=%s",
        description = "Gemini AI",
        color = 0xFF8E24AA, // Purple-ish
        shortLabel = "Gemini",
      ),
      SearchShortcut(
        id = "duckduckgo",
        alias = "dd",
        urlTemplate = "https://duckduckgo.com/?q=%s",
        description = "DuckDuckGo Search",
        suggestionUrl = "https://ac.duckduckgo.com/ac/?q=%s&type=list",
        color = 0xFFDE5833,
        shortLabel = "DuckDuckGo",
      ),
      SearchShortcut(
        id = "bing",
        alias = "bing",
        urlTemplate = "https://www.bing.com/search?q=%s",
        description = "Bing Search",
        suggestionUrl = "https://api.bing.com/osjson.aspx?query=%s",
        color = 0xFF008373,
        shortLabel = "Bing",
      ),
      SearchShortcut(
        id = "calendar",
        alias = "cal",
        urlTemplate =
          "intent:#Intent;action=android.intent.action.INSERT;type=vnd.android.cursor.item/event;S.title=%s;end",
        description = "Add Calendar Item",
        color = 0xFF3F51B5,
        shortLabel = "Calendar",
      ),
      SearchShortcut(
        id = "youtube",
        alias = "y",
        urlTemplate = "https://www.youtube.com/results?search_query=%s",
        description = "YouTube Search",
        suggestionUrl =
          "http://suggestqueries.google.com/complete/search?client=firefox&ds=yt&q=%s",
        color = 0xFFFF0000,
        shortLabel = "YouTube",
      ),
      SearchShortcut(
        id = "navigate",
        alias = "nav",
        urlTemplate = "geo:0,0?q=%s",
        description = "Navigate to",
        color = 0xFF009688,
        shortLabel = "Navigate",
      ),
      SearchShortcut(
        id = "maps",
        alias = "m",
        urlTemplate = "https://www.google.com/maps/search/%s",
        description = "Google Maps Search",
        color = 0xFF34A853,
        shortLabel = "Maps",
      ),
      SearchShortcut(
        id = "reddit",
        alias = "r",
        urlTemplate = "https://www.reddit.com/search/?q=%s",
        description = "Reddit Search",
        color = 0xFFFF4500,
        shortLabel = "Reddit",
      ),
      SearchShortcut(
        id = "wikipedia",
        alias = "w",
        urlTemplate = "https://en.wikipedia.org/w/index.php?search=%s",
        description = "Wikipedia Search",
        color = 0xFF808080,
        shortLabel = "Wikipedia",
      ),
      SearchShortcut(
        id = "chatgpt_ask",
        alias = "c",
        urlTemplate = "https://chatgpt.com/?q=%s",
        description = "Ask ChatGPT",
        color = 0xFF10A37F,
        shortLabel = "ChatGPT",
      ),
      SearchShortcut(
        id = "playstore",
        alias = "p",
        urlTemplate = "market://search?q=%s",
        description = "Play Store Search",
        color = 0xFF01875F,
        shortLabel = "Play Store",
      ),
      SearchShortcut(
        id = "spotify",
        alias = "s",
        urlTemplate = "spotify:search:%s",
        description = "Spotify Search",
        packageName = "com.spotify.music",
        color = 0xFF1DB954,
        shortLabel = "Spotify",
      ),
      SearchShortcut(
        id = "widget_search",
        alias = "widgets",
        urlTemplate = "internal://widget?q=%s", // custom interceptor logic
        description = "Search Widgets",
        color = 0xFFFF9800, // Orange
        shortLabel = "Widgets",
      ),
    )
}
