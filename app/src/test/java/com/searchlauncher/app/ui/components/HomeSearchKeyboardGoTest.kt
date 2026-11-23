package com.searchlauncher.app.ui.components

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Go must run the latest onGo. SearchScreen passes `::submitSearch`, a reference to a local
 * function: each recomposition makes a new one, bound to the current results, yet equal() to the
 * last.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class HomeSearchKeyboardGoTest {
  @get:Rule val compose = createComposeRule()

  private var opened: String? = null

  @Test
  fun goOpensTheResultForWhatWasTyped() {
    compose.setContent {
      var query by remember { mutableStateOf("") }
      val results = remember(query) { listOf("result:$query") }
      fun submit() {
        opened = results.firstOrNull()
      }
      MaterialTheme {
        HomeSearchKeyboard(
          { query += it },
          {},
          ::submit,
          Modifier.requiredWidth(400.dp).height(243.dp),
        )
      }
    }
    compose.onNodeWithContentDescription("s").performTouchInput {
      down(center)
      up()
    }
    compose.onNodeWithContentDescription("p").performTouchInput {
      down(center)
      up()
    }
    compose.waitForIdle()
    compose.onNodeWithContentDescription("Go: open search result").performTouchInput {
      down(center)
      up()
    }
    compose.runOnIdle { assertEquals("result:sp", opened) }
  }
}
