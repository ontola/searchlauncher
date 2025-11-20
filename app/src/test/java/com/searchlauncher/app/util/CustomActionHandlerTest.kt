package com.searchlauncher.app.util

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
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
}