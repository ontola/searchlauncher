package com.searchlauncher.app.ui

import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Issue 149: clearing the query dropped the string the results use, but a field that still had an
 * IME composition kept drawing the old text.
 */
@RunWith(AndroidJUnit4::class)
class SearchFieldExternalTextTest {
  @get:Rule val compose = createComposeRule()

  @Test
  fun clearingAComposingQueryRemovesTheText() {
    val query = mutableStateOf("hello")
    compose.setContent {
      val displayQuery = query.value
      var value by remember {
        mutableStateOf(
          TextFieldValue(
            displayQuery,
            TextRange(displayQuery.length),
            TextRange(0, displayQuery.length),
          )
        )
      }
      if (value.text != displayQuery) {
        value = value.applyExternalText(displayQuery)
      }
      BasicTextField(
        value = value,
        onValueChange = { value = it },
        modifier = Modifier.testTag("field"),
      )
    }
    compose.onNodeWithTag("field").assertTextEquals("hello")
    query.value = ""
    compose.waitForIdle()
    compose.onNodeWithTag("field").assertTextEquals("")
  }
}
