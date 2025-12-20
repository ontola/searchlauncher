package com.searchlauncher.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.searchlauncher.app.data.SearchShortcut
import java.util.UUID

@Composable
fun ShortcutDialog(
  shortcut: SearchShortcut?,
  existingAliases: List<String>,
  onDismiss: () -> Unit,
  onSave: (SearchShortcut) -> Unit,
) {
  var id by remember { mutableStateOf(shortcut?.id ?: UUID.randomUUID().toString()) }
  var alias by remember { mutableStateOf(shortcut?.alias ?: "") }
  var urlTemplate by remember { mutableStateOf(shortcut?.urlTemplate ?: "") }
  var description by remember { mutableStateOf(shortcut?.description ?: "") }
  var shortLabel by remember { mutableStateOf(shortcut?.shortLabel ?: "") }
  var colorHex by remember {
    mutableStateOf((shortcut?.color ?: 0xFF000000).toString(16).padStart(8, '0'))
  }
  var error by remember { mutableStateOf<String?>(null) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(if (shortcut != null) "Edit Shortcut" else "New Search Shortcut") },
    text = {
      Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        OutlinedTextField(
          value = description,
          onValueChange = { description = it },
          label = { Text("Description (e.g. YouTube Search)") },
          modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
          value = shortLabel,
          onValueChange = { shortLabel = it },
          label = { Text("Preview Name (e.g. YouTube)") },
          placeholder = { Text("Shown in chip") },
          modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
          value = alias,
          onValueChange = {
            alias = it
            error = null
          },
          label = { Text("Alias (e.g., 'r', 'yt')") },
          modifier = Modifier.fillMaxWidth(),
          singleLine = true,
          isError = error != null,
          supportingText = {
            if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error)
          },
        )
        OutlinedTextField(
          value = urlTemplate,
          onValueChange = { urlTemplate = it },
          label = { Text("URL Template (use %s for query)") },
          modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
          value = colorHex,
          onValueChange = { colorHex = it },
          label = { Text("Color (Hex, e.g., FF4285F4)") },
          modifier = Modifier.fillMaxWidth(),
        )
      }
    },
    confirmButton = {
      Button(
        onClick = {
          if (alias.isBlank()) {
            error = "Alias cannot be empty"
            return@Button
          }
          if (
            existingAliases.any { it.equals(alias, ignoreCase = true) } &&
              !alias.equals(shortcut?.alias, ignoreCase = true)
          ) {
            error = "Alias '$alias' is already in use"
            return@Button
          }

          val color =
            try {
              colorHex.toLong(16)
            } catch (e: Exception) {
              0xFF000000
            }

          val newShortcut =
            SearchShortcut(
              id = id,
              alias = alias,
              urlTemplate = urlTemplate,
              description = description,
              color = color,
              shortLabel = shortLabel.takeIf { it.isNotBlank() },
              suggestionUrl = shortcut?.suggestionUrl,
              packageName = shortcut?.packageName,
            )
          onSave(newShortcut)
        }
      ) {
        Text("Save")
      }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
  )
}
