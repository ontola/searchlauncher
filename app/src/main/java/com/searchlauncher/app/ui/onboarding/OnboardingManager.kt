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
    val KEY_SET_TIMER = booleanPreferencesKey("onboarding_set_timer")

    /**
     * The one place a step is tied to its stored key. Reading, writing and skipping all go through
     * this, so adding or removing a step is a change to [OnboardingStep] and this list, and nothing
     * can be left half-wired.
     */
    fun keyFor(step: OnboardingStep): Preferences.Key<Boolean> =
      when (step) {
        OnboardingStep.SwipeBackground -> KEY_SWIPE_BACKGROUND
        OnboardingStep.SwipeNotifications -> KEY_SWIPE_NOTIFICATIONS
        OnboardingStep.SwipeQuickSettings -> KEY_SWIPE_QUICK_SETTINGS
        OnboardingStep.SwipeAppDrawer -> KEY_SWIPE_APP_DRAWER
        OnboardingStep.LongPressBackground -> KEY_LONG_PRESS_BACKGROUND
        OnboardingStep.SearchYoutube -> KEY_SEARCH_YOUTUBE
        OnboardingStep.SearchGoogle -> KEY_SEARCH_GOOGLE
        OnboardingStep.AddFavorite -> KEY_ADD_FAVORITE
        OnboardingStep.ReorderFavorites -> KEY_REORDER_FAVORITES
        OnboardingStep.OpenSettings -> KEY_OPEN_SETTINGS
        OnboardingStep.SetTimer -> KEY_SET_TIMER
      }
  }

  val completedSteps: Flow<Set<OnboardingStep>> =
    dataStore.data.map { prefs ->
      OnboardingStep.entries.filterTo(mutableSetOf()) { prefs[keyFor(it)] == true }
    }

  suspend fun markStepComplete(step: OnboardingStep) {
    dataStore.edit { prefs -> prefs[keyFor(step)] = true }
  }

  /**
   * Marks every step done at once, for the user who would rather find their own way around. The
   * hints stop immediately; searching "Start Onboarding" brings them back.
   */
  suspend fun skipAll() {
    dataStore.edit { prefs -> OnboardingStep.entries.forEach { prefs[keyFor(it)] = true } }
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
  SetTimer,
}
