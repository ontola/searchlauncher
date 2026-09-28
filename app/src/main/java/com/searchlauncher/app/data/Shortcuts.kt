package com.searchlauncher.app.data

import android.graphics.drawable.Drawable

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
) {
  /** Fills [urlTemplate]'s `%s` placeholder with an encoded [query]. */
  fun urlForQuery(query: String): String =
    urlTemplate.replace("%s", java.net.URLEncoder.encode(query, "UTF-8"))

  /**
   * Apps whose icon may stand in for this shortcut's letter tile, first installed one wins. Only
   * the picture is borrowed: unlike [packageName], these never decide which app opens or whether
   * the shortcut is offered.
   */
  val iconPackages: List<String>
    get() = listOfNotNull(packageName) + DefaultShortcuts.iconPackages[id].orEmpty()

  /**
   * Says the shortcut searches inside a known app, so the result reads as more than a letter. The
   * key to type is drawn beside it, so only shortcuts without an app spell the alias out.
   */
  val searchHint: String
    get() =
      if (iconPackages.isEmpty()) "Type '$alias ' to search"
      else "Search inside ${shortLabel ?: description}"

  /**
   * The same result the search list builds for this shortcut, including the coloured letter icon
   * when [icon] is supplied by [SearchIconGenerator].
   */
  fun toSearchIntent(icon: Drawable? = null): SearchResult.SearchIntent =
    SearchResult.SearchIntent(
      id = id,
      namespace = SearchOptions.NAMESPACE,
      title = description,
      subtitle = searchHint,
      icon = icon,
      trigger = alias,
    )
}

/**
 * The search shortcut that searches inside the app [packageName], if any, so its row can offer it.
 */
fun List<SearchShortcut>.forApp(packageName: String): SearchShortcut? = firstOrNull {
  packageName in it.iconPackages
}

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
    val aliases: String? = null,
  ) : AppShortcut()
}

object DefaultShortcuts {
  private val searchShortcutOrderById by lazy {
    ShortcutCatalog.entries.mapIndexed { index, entry -> entry.shortcut.id to index }.toMap()
  }

  fun searchShortcutOrder(indexedId: String): Int =
    searchShortcutOrderById[indexedId.removePrefix("search_")] ?: Int.MAX_VALUE

  /**
   * Well-known apps behind the search shortcuts, keyed by shortcut id, so the results list can show
   * the app the user already knows instead of a bare letter. Comes from [ShortcutCatalog].
   */
  val iconPackages: Map<String, List<String>> by lazy {
    ShortcutCatalog.entries.filter { it.apps.isNotEmpty() }.associate { it.shortcut.id to it.apps }
  }

  // App-defined actions and settings (not editable by user)
  private val settingsActions =
    listOf(
      "android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS",
      "android.settings.ADD_ACCOUNT_SETTINGS",
      "android.settings.AIRPLANE_MODE_SETTINGS",
      "android.settings.APN_SETTINGS",
      "android.settings.APPLICATION_DETAILS_SETTINGS",
      "android.settings.APPLICATION_DEVELOPMENT_SETTINGS",
      "android.settings.APPLICATION_SETTINGS",
      "android.settings.APP_NOTIFICATION_SETTINGS",
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
        id = "launcher_reset_index",
        intentUri = "intent:#Intent;action=com.searchlauncher.RESET_INDEX;end",
        description = "Reset Search Index",
      ),
      AppShortcut.Action(
        id = "launcher_reset_app_data",
        intentUri = "intent:#Intent;action=com.searchlauncher.RESET_APP_DATA;end",
        description = "Reset App Data",
      ),
      AppShortcut.Action(
        id = "launcher_add_widget",
        intentUri = "intent:#Intent;action=com.searchlauncher.action.ADD_WIDGET;end",
        description = "Add Widget",
      ),
      AppShortcut.Action(
        id = "launcher_toggle_flashlight",
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
        id = "launcher_toggle_rotation",
        intentUri = "intent:#Intent;action=com.searchlauncher.action.TOGGLE_ROTATION;end",
        description = "Toggle Rotation Lock",
      ),
      AppShortcut.Action(
        id = "set_launcher",
        intentUri = "intent:#Intent;action=android.settings.HOME_SETTINGS;end",
        description = "Set as Launcher",
      ),
      AppShortcut.Action(
        id = "launcher_create_snippet",
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
        id = "launcher_restart",
        intentUri = "intent:#Intent;action=com.searchlauncher.action.RESTART;end",
        description = "Restart SearchLauncher",
      ),
      AppShortcut.Action(
        id = "launcher_toggle_dark_mode",
        intentUri = "intent:#Intent;action=com.searchlauncher.action.TOGGLE_DARK_MODE;end",
        description = "Toggle Dark Mode",
      ),
      AppShortcut.Action(
        id = "launcher_custom_shortcuts",
        intentUri = "intent:#Intent;action=com.searchlauncher.action.SETTINGS_CUSTOM_SHORTCUTS;end",
        description = "Custom Shortcuts",
      ),
      AppShortcut.Action(
        id = "launcher_snippets",
        intentUri = "intent:#Intent;action=com.searchlauncher.action.SETTINGS_SNIPPETS;end",
        description = "Snippets",
      ),
      AppShortcut.Action(
        id = "launcher_history",
        intentUri = "intent:#Intent;action=com.searchlauncher.action.SETTINGS_HISTORY;end",
        description = "Search History",
      ),
      AppShortcut.Action(
        id = "launcher_wallpaper",
        description = "Wallpaper Management",
        aliases = "wallpapers backgrounds manage wallpaper background",
        intentUri = "intent:#Intent;action=com.searchlauncher.action.SETTINGS_WALLPAPER;end",
      ),
      AppShortcut.Action(
        id = "launcher_add_wallpaper",
        description = "Add Wallpapers",
        aliases = "upload wallpaper background import",
        intentUri = "intent:#Intent;action=com.searchlauncher.action.ADD_WALLPAPER;end",
      ),
      AppShortcut.Action(
        id = "launcher_remove_current_wallpaper",
        description = "Remove Current Wallpaper",
        aliases = "delete background trash current wallpaper",
        intentUri = "intent:#Intent;action=com.searchlauncher.action.REMOVE_CURRENT_WALLPAPER;end",
      ),
      AppShortcut.Action(
        id = "launcher_export_backup",
        intentUri =
          "intent:#Intent;action=com.searchlauncher.action.EXPORT_BACKUP;component=com.searchlauncher.app/.ui.MainActivity;end",
        description = "Export Backup",
      ),
      AppShortcut.Action(
        id = "launcher_import_backup",
        intentUri =
          "intent:#Intent;action=com.searchlauncher.action.IMPORT_BACKUP;component=com.searchlauncher.app/.ui.MainActivity;end",
        description = "Import Backup",
      ),
      AppShortcut.Action(
        id = "launcher_onboarding",
        intentUri =
          "intent:#Intent;action=com.searchlauncher.action.RESET_ONBOARDING;component=com.searchlauncher.app/.ui.MainActivity;end",
        description = "Start Onboarding",
      ),
      AppShortcut.Action(
        id = "launcher_refresh_icons",
        intentUri =
          "intent:#Intent;action=com.searchlauncher.action.REFRESH_ICONS;component=com.searchlauncher.app/.ui.MainActivity;end",
        description = "Refresh Icons",
      ),
      AppShortcut.Action(
        id = "settings_battery_saver",
        intentUri = "intent:#Intent;action=android.settings.BATTERY_SAVER_SETTINGS;end",
        description = "Battery Saver Settings",
        aliases = "battery saver mode power save status battery saver settings",
      ),
      AppShortcut.Action(
        id = "settings_wireless_debugging",
        intentUri = "intent:#Intent;action=com.searchlauncher.action.WIRELESS_DEBUGGING;end",
        description = "Wireless Debugging Settings",
        aliases =
          "wireless debugging adb wifi debug developers developer options status settings wireless debugging settings",
      ),
      AppShortcut.Action(
        id = "launcher_browser_settings",
        intentUri = "intent:#Intent;action=com.searchlauncher.action.SETTINGS_BROWSER;end",
        description = "Browser Settings",
        aliases = "browser settings default browser search engine web history tabs",
      ),
      AppShortcut.Action(
        id = "set_default_browser",
        intentUri = "intent:#Intent;action=com.searchlauncher.action.SET_DEFAULT_BROWSER;end",
        description = "Set Default Browser",
        aliases = "default browser set make browser role open links in browser change browser",
      ),
    ) + generateSettingsShortcuts()

  /**
   * User-editable search shortcuts every install starts with. The full list, including shortcuts
   * that only appear once their app is installed, is in `search_shortcuts.json`.
   */
  val searchShortcuts: List<SearchShortcut> by lazy {
    ShortcutCatalog.entries.filterNot { it.onlyWhenInstalled }.map { it.shortcut }
  }

  /** Shortcuts that join the user's list by themselves once their app is installed. */
  val installableShortcuts: List<ShortcutCatalog.Entry> by lazy {
    ShortcutCatalog.entries.filter { it.onlyWhenInstalled }
  }
}
