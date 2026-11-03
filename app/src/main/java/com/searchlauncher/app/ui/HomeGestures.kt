package com.searchlauncher.app.ui

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey

/** The home-screen swipes a user can rebind (issue #161). */
enum class HomeGesture(val label: String, prefName: String) {
  SwipeUp("Swipe up", "gesture_swipe_up"),
  SwipeDownLeft("Swipe down on the left", "gesture_swipe_down_left"),
  SwipeDownRight("Swipe down on the right", "gesture_swipe_down_right"),
  SwipeLeft("Swipe left", "gesture_swipe_left"),
  SwipeRight("Swipe right", "gesture_swipe_right");

  val actionKey = stringPreferencesKey(prefName)
  /** Package name of the app to open, only read when the action is [GestureAction.OpenApp]. */
  val appKey = stringPreferencesKey("${prefName}_app")
}

enum class GestureAction(val id: String, val label: String) {
  Nothing("nothing", "Nothing"),
  Notifications("notifications", "Notifications"),
  QuickSettings("quick_settings", "Quick settings"),
  AppDrawer("app_drawer", "App drawer"),
  NextWallpaper("next_wallpaper", "Next background"),
  PreviousWallpaper("previous_wallpaper", "Previous background"),
  OpenApp("open_app", "Open an app");

  companion object {
    fun fromId(id: String?): GestureAction? = entries.firstOrNull { it.id == id }
  }
}

data class GestureBinding(val action: GestureAction, val appPackage: String? = null)

/**
 * What each gesture did before it could be changed: up for the app drawer, down on the left for
 * notifications, down on the right for Quick Settings only on phones with a separate tray, and
 * sideways to page through the background images.
 */
fun defaultGestureAction(gesture: HomeGesture, separateShade: Boolean): GestureAction =
  when (gesture) {
    HomeGesture.SwipeUp -> GestureAction.AppDrawer
    HomeGesture.SwipeDownLeft -> GestureAction.Notifications
    HomeGesture.SwipeDownRight ->
      if (separateShade) GestureAction.QuickSettings else GestureAction.Notifications
    HomeGesture.SwipeLeft -> GestureAction.NextWallpaper
    HomeGesture.SwipeRight -> GestureAction.PreviousWallpaper
  }

/**
 * The wallpaper pager can follow the finger only while both sideways swipes keep their default
 * wallpaper actions. Otherwise sideways swipes are recognised as gestures like the vertical ones.
 */
fun sidewaysSwipesPageWallpapers(bindings: Map<HomeGesture, GestureBinding>): Boolean =
  bindings[HomeGesture.SwipeLeft]?.action == GestureAction.NextWallpaper &&
    bindings[HomeGesture.SwipeRight]?.action == GestureAction.PreviousWallpaper

/**
 * The stored choice per gesture, falling back to [defaultGestureAction]. An "open an app" choice
 * with no app saved yet acts as nothing rather than guessing one.
 */
fun resolveGestureBindings(
  prefs: Preferences,
  separateShade: Boolean,
): Map<HomeGesture, GestureBinding> =
  HomeGesture.entries.associateWith { gesture ->
    val action =
      GestureAction.fromId(prefs[gesture.actionKey]) ?: defaultGestureAction(gesture, separateShade)
    if (action == GestureAction.OpenApp) {
      val app = prefs[gesture.appKey]?.takeIf { it.isNotBlank() }
      if (app == null) GestureBinding(GestureAction.Nothing) else GestureBinding(action, app)
    } else {
      GestureBinding(action)
    }
  }

/**
 * While onboarding teaches the notifications swipe, any downward swipe that would open Quick
 * Settings opens notifications instead, so the hint can be followed on either side.
 */
fun effectiveGestureAction(action: GestureAction, teachingNotifications: Boolean): GestureAction =
  if (teachingNotifications && action == GestureAction.QuickSettings) GestureAction.Notifications
  else action
