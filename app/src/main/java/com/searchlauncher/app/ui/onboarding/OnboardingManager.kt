package com.searchlauncher.app.ui.onboarding

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.onboardingDataStore: DataStore<Preferences> by
androidx.datastore.preferences.preferencesDataStore(name = "onboarding")

class OnboardingManager(private val context: Context) {

  private val dataStore = context.onboardingDataStore

  companion object {
    val KEY_SWIPE_BACKGROUND = booleanPreferencesKey("onboarding_swipe_background")
    val KEY_SWIPE_NOTIFICATIONS = booleanPreferencesKey("onboarding_swipe_notifications")
    val KEY_SWIPE_QUICK_SETTINGS = booleanPreferencesKey("onboarding_swipe_quick_settings")
    val KEY_SWIPE_APP_DRAWER = booleanPreferencesKey("onboarding_swipe_app_drawer")
    val KEY_LONG_PRESS_BACKGROUND = booleanPreferencesKey("onboarding_long_press_background")
    val KEY_SEARCH_YOUTUBE = booleanPreferencesKey("onboarding_search_youtube")
    val KEY_SEARCH_GOOGLE = booleanPreferencesKey("onboarding_search_google")
    val KEY_ADD_FAVORITE = booleanPreferencesKey("onboarding_add_favorite")
    val KEY_REORDER_FAVORITES = booleanPreferencesKey("onboarding_reorder_favorites")
    val KEY_OPEN_SETTINGS = booleanPreferencesKey("onboarding_open_settings")
    val KEY_SET_DEFAULT_LAUNCHER = booleanPreferencesKey("onboarding_set_default_launcher")
  }

  val completedSteps: Flow<Set<OnboardingStep>> =
    dataStore.data.map { prefs ->
      val steps = mutableSetOf<OnboardingStep>()
      if (prefs[KEY_SWIPE_BACKGROUND] == true) steps.add(OnboardingStep.SwipeBackground)
      if (prefs[KEY_SWIPE_NOTIFICATIONS] == true) steps.add(OnboardingStep.SwipeNotifications)
      if (prefs[KEY_SWIPE_QUICK_SETTINGS] == true) steps.add(OnboardingStep.SwipeQuickSettings)
      if (prefs[KEY_SWIPE_APP_DRAWER] == true) steps.add(OnboardingStep.SwipeAppDrawer)
      if (prefs[KEY_LONG_PRESS_BACKGROUND] == true) steps.add(OnboardingStep.LongPressBackground)
      if (prefs[KEY_SEARCH_YOUTUBE] == true) steps.add(OnboardingStep.SearchYoutube)
      if (prefs[KEY_SEARCH_GOOGLE] == true) steps.add(OnboardingStep.SearchGoogle)
      if (prefs[KEY_ADD_FAVORITE] == true) steps.add(OnboardingStep.AddFavorite)
      if (prefs[KEY_REORDER_FAVORITES] == true) steps.add(OnboardingStep.ReorderFavorites)
      if (prefs[KEY_OPEN_SETTINGS] == true) steps.add(OnboardingStep.OpenSettings)
      if (prefs[KEY_SET_DEFAULT_LAUNCHER] == true) steps.add(OnboardingStep.SetDefaultLauncher)
      steps
    }

  suspend fun markStepComplete(step: OnboardingStep) {
    dataStore.edit { prefs ->
      when (step) {
        OnboardingStep.SwipeBackground -> prefs[KEY_SWIPE_BACKGROUND] = true
        OnboardingStep.SwipeNotifications -> prefs[KEY_SWIPE_NOTIFICATIONS] = true
        OnboardingStep.SwipeQuickSettings -> prefs[KEY_SWIPE_QUICK_SETTINGS] = true
        OnboardingStep.SwipeAppDrawer -> prefs[KEY_SWIPE_APP_DRAWER] = true
        OnboardingStep.LongPressBackground -> prefs[KEY_LONG_PRESS_BACKGROUND] = true
        OnboardingStep.SearchYoutube -> prefs[KEY_SEARCH_YOUTUBE] = true
        OnboardingStep.SearchGoogle -> prefs[KEY_SEARCH_GOOGLE] = true
        OnboardingStep.AddFavorite -> prefs[KEY_ADD_FAVORITE] = true
        OnboardingStep.ReorderFavorites -> prefs[KEY_REORDER_FAVORITES] = true
        OnboardingStep.OpenSettings -> prefs[KEY_OPEN_SETTINGS] = true
        OnboardingStep.SetDefaultLauncher -> prefs[KEY_SET_DEFAULT_LAUNCHER] = true
      }
    }
  }

  suspend fun resetOnboarding() {
    dataStore.edit { prefs -> prefs.clear() }
  }
}

enum class OnboardingStep {
  SwipeBackground,
  SwipeNotifications,
  SwipeQuickSettings,
  SwipeAppDrawer,
  LongPressBackground,
  SearchYoutube,
  SearchGoogle,
  AddFavorite,
  ReorderFavorites,
  OpenSettings,
  SetDefaultLauncher,
}
