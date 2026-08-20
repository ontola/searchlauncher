package com.searchlauncher.app.ui.browser

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.webkit.ValueCallback
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BrowserFileChooserTest {

  @Test
  fun start_returnsFalseWhenTheSystemPickerCannotRun() {
    val chooser = BrowserFileChooser()
    var cancelled = false
    val callback = ValueCallback<Array<Uri>> { value -> if (value == null) cancelled = true }

    assertFalse(chooser.start(callback, null))
    assertTrue(cancelled)
  }

  @Test
  fun deliver_isSafeWhenNoChooserIsOpen() {
    BrowserFileChooser().deliver(Activity.RESULT_CANCELED, Intent())
  }
}
