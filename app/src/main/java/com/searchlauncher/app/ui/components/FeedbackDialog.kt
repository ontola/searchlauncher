package com.searchlauncher.app.ui.components

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.searchlauncher.app.util.FeedbackDiagnostics
import com.searchlauncher.app.util.FeedbackReporter
import com.searchlauncher.app.util.FeedbackResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun FeedbackDialog(onDismiss: () -> Unit) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  var message by remember { mutableStateOf("") }
  var email by remember { mutableStateOf("") }
  var showDetails by remember { mutableStateOf(false) }
  var messageError by remember { mutableStateOf(false) }
  var sending by remember { mutableStateOf(false) }
  val details = remember { FeedbackDiagnostics.collect(context).format() }

  AlertDialog(
    onDismissRequest = { if (!sending) onDismiss() },
    title = { Text("Send feedback") },
    text = {
      Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Text(
          text =
            "Include what you expected to happen. Device model, Android version, app version, and WebView version are attached so the report can be reproduced. This is sent to GlitchTip and does not turn on crash reporting.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
          value = message,
          onValueChange = {
            message = it
            messageError = false
          },
          label = { Text("Message") },
          isError = messageError,
          supportingText =
            if (messageError) {
              { Text("Message is required") }
            } else {
              null
            },
          modifier = Modifier.fillMaxWidth(),
          minLines = 3,
          maxLines = 6,
          enabled = !sending,
        )
        OutlinedTextField(
          value = email,
          onValueChange = { email = it },
          label = { Text("Email (optional)") },
          placeholder = { Text("Only if you want a reply") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
          enabled = !sending,
        )
        TextButton(onClick = { showDetails = !showDetails }, enabled = !sending) {
          Text(if (showDetails) "Hide device details" else "Show device details")
        }
        if (showDetails) {
          Text(
            text = details,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    },
    confirmButton = {
      TextButton(
        enabled = !sending,
        onClick = {
          if (message.isBlank()) {
            messageError = true
            return@TextButton
          }
          sending = true
          val appContext = context.applicationContext
          scope.launch {
            val result =
              withContext(Dispatchers.IO + NonCancellable) {
                FeedbackReporter.submit(appContext, message, email)
              }
            sending = false
            when (result) {
              FeedbackResult.Sent -> {
                Toast.makeText(appContext, "Feedback sent", Toast.LENGTH_SHORT).show()
                onDismiss()
              }
              FeedbackResult.Failed ->
                Toast.makeText(appContext, "Could not send feedback", Toast.LENGTH_LONG).show()
              FeedbackResult.Empty -> messageError = true
            }
          }
        },
      ) {
        Text(if (sending) "Sending…" else "Send")
      }
    },
    dismissButton = { TextButton(onClick = onDismiss, enabled = !sending) { Text("Cancel") } },
  )
}
