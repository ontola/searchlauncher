package com.searchlauncher.app.ui.components

import android.app.Activity
import androidx.test.core.app.ApplicationProvider
import com.searchlauncher.app.SearchLauncherApp
import com.searchlauncher.app.ui.MainActivity
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = SearchLauncherApp::class)
class HomeWidgetsEnabledTest {

  @Test
  fun homeScreenCanHostWidgets() {
    assertTrue(homeWidgetsEnabled(mockk<MainActivity>(relaxed = true)))
  }

  @Test
  fun overlayAndOtherActivitiesCannot() {
    assertFalse(homeWidgetsEnabled(ApplicationProvider.getApplicationContext()))
    assertFalse(homeWidgetsEnabled(mockk<Activity>(relaxed = true)))
  }
}
