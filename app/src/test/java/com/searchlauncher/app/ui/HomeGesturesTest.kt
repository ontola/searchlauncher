package com.searchlauncher.app.ui

import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.preferencesOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeGesturesTest {
  @Test
  fun defaultsMatchTheOldFixedGestures() {
    val split = resolveGestureBindings(preferencesOf(), separateShade = true)
    assertEquals(GestureAction.AppDrawer, split.getValue(HomeGesture.SwipeUp).action)
    assertEquals(GestureAction.Notifications, split.getValue(HomeGesture.SwipeDownLeft).action)
    assertEquals(GestureAction.QuickSettings, split.getValue(HomeGesture.SwipeDownRight).action)
    assertEquals(GestureAction.NextWallpaper, split.getValue(HomeGesture.SwipeLeft).action)
    assertEquals(GestureAction.PreviousWallpaper, split.getValue(HomeGesture.SwipeRight).action)
    assertTrue(sidewaysSwipesPageWallpapers(split))

    val oneTray = resolveGestureBindings(preferencesOf(), separateShade = false)
    assertEquals(GestureAction.Notifications, oneTray.getValue(HomeGesture.SwipeDownRight).action)
  }

  @Test
  fun storedChoiceWins() {
    val prefs =
      mutablePreferencesOf().apply {
        this[HomeGesture.SwipeDownRight.actionKey] = GestureAction.Nothing.id
        this[HomeGesture.SwipeLeft.actionKey] = GestureAction.OpenApp.id
        this[HomeGesture.SwipeLeft.appKey] = "com.example.camera"
      }
    val bindings = resolveGestureBindings(prefs, separateShade = true)
    assertEquals(GestureAction.Nothing, bindings.getValue(HomeGesture.SwipeDownRight).action)
    assertEquals(
      GestureBinding(GestureAction.OpenApp, "com.example.camera"),
      bindings.getValue(HomeGesture.SwipeLeft),
    )
    assertFalse(sidewaysSwipesPageWallpapers(bindings))
  }

  @Test
  fun openAppWithoutAnAppDoesNothing() {
    val prefs =
      mutablePreferencesOf().apply {
        this[HomeGesture.SwipeUp.actionKey] = GestureAction.OpenApp.id
      }
    assertEquals(
      GestureAction.Nothing,
      resolveGestureBindings(prefs, separateShade = true).getValue(HomeGesture.SwipeUp).action,
    )
  }

  @Test
  fun unknownStoredIdFallsBackToDefault() {
    val prefs = mutablePreferencesOf().apply { this[HomeGesture.SwipeUp.actionKey] = "bogus" }
    assertEquals(
      GestureAction.AppDrawer,
      resolveGestureBindings(prefs, separateShade = true).getValue(HomeGesture.SwipeUp).action,
    )
  }

  @Test
  fun notificationsLessonKeepsDownSwipesOnNotifications() {
    assertEquals(
      GestureAction.Notifications,
      effectiveGestureAction(GestureAction.QuickSettings, teachingNotifications = true),
    )
    assertEquals(
      GestureAction.QuickSettings,
      effectiveGestureAction(GestureAction.QuickSettings, teachingNotifications = false),
    )
  }
}
