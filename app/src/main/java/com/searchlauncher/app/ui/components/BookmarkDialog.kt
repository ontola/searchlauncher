package com.searchlauncher.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Confirms a bookmark before it is stored, letting the page title be replaced with something the
 * user will actually recognise later. Used for saving from the browser, re-titling an existing
 * bookmark, and promoting a history entry.
 */
@Composable
fun BookmarkDialog(
  initialTitle: String,
  url: String,
  isEditMode: Boolean,
  onDismiss: () -> Unit,
  onConfirm: (String) -> Unit,
) {
  // Select the prefilled title so typing replaces it: page titles are often long and the common
  // case is writing a shorter name from scratch.
  var title by
    remember(initialTitle) {
      mutableStateOf(TextFieldValue(initialTitle, TextRange(0, initialTitle.length)))
    }
  val focusRequester = remember { FocusRequester() }
  val confirm = { onConfirm(title.text.trim().ifEmpty { url }) }

  LaunchedEffect(Unit) { focusRequester.requestFocus() }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(if (isEditMode) "Edit bookmark" else "Save bookmark") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
          value = title,
          onValueChange = { title = it },
          label = { Text("Title") },
          modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
          singleLine = true,
          keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
          keyboardActions = KeyboardActions(onDone = { confirm() }),
        )
        Text(
          text = url,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
      }
    },
    confirmButton = { TextButton(onClick = confirm) { Text("Save") } },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
  )
}
