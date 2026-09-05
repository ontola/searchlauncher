package com.searchlauncher.app.ui

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import androidx.test.core.app.ApplicationProvider
import com.searchlauncher.app.data.Prefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PortraitLockTest {

  private val context = ApplicationProvider.getApplicationContext<Context>()

  @Before
  fun clearCache() {
    context
      .getSharedPreferences(Prefs.Launcher.FILE, Context.MODE_PRIVATE)
      .edit()
      .remove(Prefs.Launcher.LOCK_PORTRAIT)
      .commit()
  }

  @Test
  fun cached_defaultsToUnlocked() {
    assertFalse(PortraitLock.cached(context))
  }

  @Test
  fun updateCache_roundTrips() {
    PortraitLock.updateCache(context, true)
    assertTrue(PortraitLock.cached(context))
    PortraitLock.updateCache(context, false)
    assertFalse(PortraitLock.cached(context))
  }

  @Test
  fun apply_locksToPortrait() {
    val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
    PortraitLock.apply(activity, locked = true)
    assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, activity.requestedOrientation)
  }

  @Test
  fun apply_clearsToSystemDefaultWhenUnlocked() {
    val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
    PortraitLock.apply(activity, locked = true)
    PortraitLock.apply(activity, locked = false)
    assertEquals(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED, activity.requestedOrientation)
  }
}
