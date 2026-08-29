package com.searchlauncher.app.util

import android.app.Activity
import android.app.role.RoleManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class CustomActionHandlerTest {

  private val context = ApplicationProvider.getApplicationContext<Context>()

  @Test
  fun `handleAction returns true for valid actions`() {
    val intentFlash = Intent("com.searchlauncher.action.TOGGLE_FLASHLIGHT")
    val intentRotate = Intent("com.searchlauncher.action.TOGGLE_ROTATION")

    assertTrue(CustomActionHandler.handleAction(context, intentFlash))
    assertTrue(CustomActionHandler.handleAction(context, intentRotate))
  }

  @Test
  fun `handleAction returns false for unknown actions`() {
    val intent = Intent("com.unknown.action")
    assertFalse(CustomActionHandler.handleAction(context, intent))
  }

  @Test
  fun `toggleRotation flips the system setting`() {
    val resolver = context.contentResolver

    // Set to 0 initially
    Settings.System.putInt(resolver, Settings.System.ACCELEROMETER_ROTATION, 0)

    // Trigger the toggle
    val intent = Intent("com.searchlauncher.action.TOGGLE_ROTATION")
    CustomActionHandler.handleAction(context, intent)

    // It should now be 1
    val newState = Settings.System.getInt(resolver, Settings.System.ACCELEROMETER_ROTATION)
    assertEquals(1, newState)

    //  Toggle again
    CustomActionHandler.handleAction(context, intent)

    // Should go back to 0
    assertEquals(0, Settings.System.getInt(resolver, Settings.System.ACCELEROMETER_ROTATION))
  }

  @Test
  fun `set default browser starts the role request for a result`() {
    val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
    val roleManager = activity.getSystemService(Context.ROLE_SERVICE) as RoleManager
    shadowOf(roleManager).addAvailableRole(RoleManager.ROLE_BROWSER)

    assertTrue(
      CustomActionHandler.handleAction(
        activity,
        Intent("com.searchlauncher.action.SET_DEFAULT_BROWSER"),
      )
    )

    val started = shadowOf(activity).nextStartedActivityForResult
    assertNotNull("role picker must be started for a result", started)
    assertEquals("android.app.role.action.REQUEST_ROLE", started.intent.action)
    assertTrue(
      started.intent.extras?.keySet()?.any {
        started.intent.getStringExtra(it) == RoleManager.ROLE_BROWSER
      } == true
    )
  }

  @Test
  fun `set default browser unwraps a themed context to start for result`() {
    val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
    val roleManager = activity.getSystemService(Context.ROLE_SERVICE) as RoleManager
    shadowOf(roleManager).addAvailableRole(RoleManager.ROLE_BROWSER)
    val wrapped = ContextWrapper(activity)

    assertEquals(activity, wrapped.findActivity())
    CustomActionHandler.handleAction(
      wrapped,
      Intent("com.searchlauncher.action.SET_DEFAULT_BROWSER"),
    )

    assertNotNull(shadowOf(activity).nextStartedActivityForResult)
  }

  @Test
  fun `set default browser without an activity opens default-apps settings`() {
    CustomActionHandler.handleAction(
      context,
      Intent("com.searchlauncher.action.SET_DEFAULT_BROWSER"),
    )

    val started = shadowOf(context as android.app.Application).nextStartedActivity
    assertEquals(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS, started.action)
    assertTrue(started.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
  }
}
