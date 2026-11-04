package com.searchlauncher.app.ui.onboarding

/**
 * Pixel-style skins open notifications from the left of the status bar and Quick Settings from the
 * right. Samsung One UI uses one tray unless the user opts into split panels, so teaching both
 * swipes there is misleading.
 */
fun manufacturerLikelyHasSeparateShade(manufacturer: String, brand: String = ""): Boolean {
  val id = "$manufacturer $brand".lowercase()
  return !id.contains("samsung")
}

/** Stored answer if the user has chosen; otherwise the manufacturer default. */
fun resolveSeparateShade(preference: Boolean?, manufacturer: String, brand: String = ""): Boolean =
  preference ?: manufacturerLikelyHasSeparateShade(manufacturer, brand)

/**
 * Which home-screen hint to show, or null when onboarding is finished for the current state.
 *
 * Whether the Quick Settings swipe is taught follows [separateShade] (the manufacturer default, or
 * the setting if the user changed it), so onboarding never asks how many trays the phone has. A
 * swipe the user moved to another action is not taught at all ([teachNotifications],
 * [teachAppDrawer], [teachBackground]).
 */
fun nextOnboardingStep(
  completed: Set<OnboardingStep>,
  queryIsEmpty: Boolean,
  hasMultipleWallpapers: Boolean,
  hasSearchResults: Boolean,
  favoritesCount: Int,
  separateShade: Boolean,
  teachNotifications: Boolean = true,
  teachAppDrawer: Boolean = true,
  teachBackground: Boolean = true,
): OnboardingStep? {
  if (!queryIsEmpty) {
    return if (
      !completed.contains(OnboardingStep.AddFavorite) && hasSearchResults && favoritesCount == 0
    ) {
      OnboardingStep.AddFavorite
    } else {
      null
    }
  }

  return when {
    !completed.contains(OnboardingStep.SwipeBackground) &&
      hasMultipleWallpapers &&
      teachBackground -> OnboardingStep.SwipeBackground
    !completed.contains(OnboardingStep.SwipeNotifications) && teachNotifications ->
      OnboardingStep.SwipeNotifications
    !completed.contains(OnboardingStep.SwipeQuickSettings) && separateShade ->
      OnboardingStep.SwipeQuickSettings
    !completed.contains(OnboardingStep.SwipeAppDrawer) && teachAppDrawer ->
      OnboardingStep.SwipeAppDrawer
    !completed.contains(OnboardingStep.LongPressBackground) -> OnboardingStep.LongPressBackground
    !completed.contains(OnboardingStep.SearchYoutube) -> OnboardingStep.SearchYoutube
    !completed.contains(OnboardingStep.SearchGoogle) -> OnboardingStep.SearchGoogle
    !completed.contains(OnboardingStep.SetTimer) -> OnboardingStep.SetTimer
    !completed.contains(OnboardingStep.ReorderFavorites) && favoritesCount >= 2 ->
      OnboardingStep.ReorderFavorites
    !completed.contains(OnboardingStep.OpenSettings) -> OnboardingStep.OpenSettings
    else -> null
  }
}
