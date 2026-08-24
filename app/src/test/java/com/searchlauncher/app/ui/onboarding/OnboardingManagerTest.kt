package com.searchlauncher.app.ui.onboarding

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.searchlauncher.app.SearchLauncherApp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = SearchLauncherApp::class)
class OnboardingManagerTest {
  private lateinit var context: Context
  private lateinit var manager: OnboardingManager

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    manager = OnboardingManager(context)
  }

  @After fun tearDown() = runBlocking { manager.resetOnboarding() }

  @Test
  fun `nothing is complete to begin with`() = runBlocking {
    assertTrue(manager.completedSteps.first().isEmpty())
  }

  @Test
  fun `a completed step is remembered and the others are not`() = runBlocking {
    manager.markStepComplete(OnboardingStep.SearchGoogle)

    val steps = manager.completedSteps.first()
    assertEquals(setOf(OnboardingStep.SearchGoogle), steps)
  }

  @Test
  fun `skipping completes every step`() = runBlocking {
    manager.skipAll()

    assertEquals(OnboardingStep.entries.toSet(), manager.completedSteps.first())
  }

  @Test
  fun `resetting after a skip brings the hints back`() = runBlocking {
    manager.skipAll()
    manager.resetOnboarding()

    assertTrue(manager.completedSteps.first().isEmpty())
  }

  @Test
  fun `every step has a distinct stored key`() = runBlocking {
    // A shared key would make one step complete another, which is the sort of thing that only
    // shows up as a hint mysteriously never appearing.
    val keys = OnboardingStep.entries.map { OnboardingManager.keyFor(it) }

    assertEquals(keys.size, keys.distinct().size)
  }

  @Test
  fun `marking one step complete leaves the rest alone`() = runBlocking {
    manager.markStepComplete(OnboardingStep.SwipeAppDrawer)

    val steps = manager.completedSteps.first()
    OnboardingStep.entries
      .filter { it != OnboardingStep.SwipeAppDrawer }
      .forEach { assertFalse("$it should still be pending", steps.contains(it)) }
  }
}
