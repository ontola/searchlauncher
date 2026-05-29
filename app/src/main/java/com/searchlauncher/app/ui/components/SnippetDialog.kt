package com.searchlauncher.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SnippetDialog(
  initialAlias: String,
  initialContent: String,
  isEditMode: Boolean,
  onDismiss: () -> Unit,
  onConfirm: (String, String) -> Unit,
) {
  var alias by remember { mutableStateOf(initialAlias) }
  var content by remember { mutableStateOf(initialContent) }
  var aliasError by remember { mutableStateOf(false) }
  var contentError by remember { mutableStateOf(false) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(if (isEditMode) "Edit Snippet" else "Add Snippet") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
          "Use snippets to store data you often want to copy, like your address.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
          value = alias,
          onValueChange = {
            alias = it
            aliasError = false
          },
          label = { Text("Alias") },
          placeholder = { Text("e.g., 'bank', 'meet'") },
          isError = aliasError,
          supportingText = {
            if (aliasError) {
              Text("Alias is required")
            } else {
              Text("An alias is the name of the snippet, shown in the search results.")
            }
          },
          modifier = Modifier.fillMaxWidth(),
          singleLine = true,
        )
        OutlinedTextField(
          value = content,
          onValueChange = {
            content = it
            contentError = false
          },
          label = { Text("Content") },
          placeholder = { Text("Text to copy when selected") },
          isError = contentError,
          supportingText =
            if (contentError) {
              { Text("Content is required") }
            } else null,
          modifier = Modifier.fillMaxWidth(),
          minLines = 3,
          maxLines = 5,
        )
      }
    },
    confirmButton = {
      TextButton(
        onClick = {
          val trimmedAlias = alias.trim()
          val trimmedContent = content.trim()
          when {
            trimmedAlias.isEmpty() -> aliasError = true
            trimmedContent.isEmpty() -> contentError = true
            else -> onConfirm(trimmedAlias, trimmedContent)
          }
        }
      ) {
        Text(if (isEditMode) "Update" else "Add")
      }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
  )
}
