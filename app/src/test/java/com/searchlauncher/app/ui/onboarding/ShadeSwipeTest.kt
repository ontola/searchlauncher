package com.searchlauncher.app.ui.onboarding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShadeSwipeTest {
  @Test
  fun samsungDefaultsToOneTray() {
    assertFalse(manufacturerLikelyHasSeparateShade("samsung", "samsung"))
    assertFalse(manufacturerLikelyHasSeparateShade("Samsung", "Galaxy"))
  }

  @Test
  fun googleDefaultsToTwoTrays() {
    assertTrue(manufacturerLikelyHasSeparateShade("Google", "google"))
    assertTrue(manufacturerLikelyHasSeparateShade("google", "pixel"))
  }

  @Test
  fun storedAnswerOverridesManufacturer() {
    assertTrue(resolveSeparateShade(true, "samsung", "samsung"))
    assertFalse(resolveSeparateShade(false, "Google", "google"))
    assertFalse(resolveSeparateShade(null, "samsung"))
    assertTrue(resolveSeparateShade(null, "Google"))
  }

  @Test
  fun rightSwipeOpensQuickSettingsOnlyWhenShadeIsSplit() {
    assertTrue(shouldOpenQuickSettings(isLeft = false, currentStep = null, separateShade = true))
    assertFalse(shouldOpenQuickSettings(isLeft = true, currentStep = null, separateShade = true))
    assertFalse(shouldOpenQuickSettings(isLeft = false, currentStep = null, separateShade = false))
  }

  @Test
  fun notificationsStepKeepsRightSwipeOnNotifications() {
    assertFalse(
      shouldOpenQuickSettings(
        isLeft = false,
        currentStep = OnboardingStep.SwipeNotifications,
        separateShade = true,
      )
    )
  }

  @Test
  fun oneTraySkipsQuickSettings() {
    val completed = setOf(OnboardingStep.SwipeNotifications)
    assertEquals(OnboardingStep.SwipeAppDrawer, nextHomeStep(completed, separateShade = false))
  }

  @Test
  fun twoTraysTeachesQuickSettingsRightAfterNotifications() {
    val completed = setOf(OnboardingStep.SwipeNotifications)
    assertEquals(OnboardingStep.SwipeQuickSettings, nextHomeStep(completed, separateShade = true))
  }

  @Test
  fun searchingShowsAddFavoriteWhenThereAreResultsAndNoFavorites() {
    assertEquals(
      OnboardingStep.AddFavorite,
      nextOnboardingStep(
        completed = emptySet(),
        queryIsEmpty = false,
        hasMultipleWallpapers = false,
        hasSearchResults = true,
        favoritesCount = 0,
        separateShade = true,
      ),
    )
    assertNull(
      nextOnboardingStep(
        completed = emptySet(),
        queryIsEmpty = false,
        hasMultipleWallpapers = false,
        hasSearchResults = true,
        favoritesCount = 1,
        separateShade = true,
      )
    )
  }

  private fun nextHomeStep(
    completed: Set<OnboardingStep>,
    separateShade: Boolean,
  ): OnboardingStep? =
    nextOnboardingStep(
      completed = completed,
      queryIsEmpty = true,
      hasMultipleWallpapers = false,
      hasSearchResults = false,
      favoritesCount = 0,
      separateShade = separateShade,
    )
}
