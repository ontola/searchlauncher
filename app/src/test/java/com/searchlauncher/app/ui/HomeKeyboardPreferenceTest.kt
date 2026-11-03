package com.searchlauncher.app.ui

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import com.searchlauncher.app.data.Prefs
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class HomeKeyboardPreferenceTest {
  @Test
  fun defaultsToBuiltInAndPersistsOptOutInBothStores() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    context.dataStore.edit { it.remove(PreferencesKeys.BUILT_IN_KEYBOARD) }
    context
      .getSharedPreferences(Prefs.Launcher.FILE, Context.MODE_PRIVATE)
      .edit()
      .remove("built_in_keyboard")
      .commit()
    assertTrue(HomeKeyboardPreference.cached(context))
    assertTrue(HomeKeyboardPreference.flow(context).first())
    try {
      HomeKeyboardPreference.set(context, false)
      assertFalse(HomeKeyboardPreference.cached(context))
      assertFalse(HomeKeyboardPreference.flow(context).first())
      // Restoring DataStore preferences must also refresh the startup cache.
      context.dataStore.edit { it[PreferencesKeys.BUILT_IN_KEYBOARD] = true }
      assertTrue(HomeKeyboardPreference.flow(context).first())
      assertTrue(HomeKeyboardPreference.cached(context))
    } finally {
      HomeKeyboardPreference.set(context, true)
    }
  }

  @Test
  fun numberRowDefaultsOffAndPersistsInBothStores() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    context.dataStore.edit { it.remove(PreferencesKeys.KEYBOARD_NUMBER_ROW) }
    context
      .getSharedPreferences(Prefs.Launcher.FILE, Context.MODE_PRIVATE)
      .edit()
      .remove("keyboard_number_row")
      .commit()
    assertFalse(KeyboardNumberRowPreference.cached(context))
    assertFalse(KeyboardNumberRowPreference.flow(context).first())
    try {
      KeyboardNumberRowPreference.set(context, true)
      assertTrue(KeyboardNumberRowPreference.cached(context))
      assertTrue(KeyboardNumberRowPreference.flow(context).first())
    } finally {
      KeyboardNumberRowPreference.set(context, false)
    }
  }
}
