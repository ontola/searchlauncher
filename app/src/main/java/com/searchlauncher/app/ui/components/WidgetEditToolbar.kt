package com.searchlauncher.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun WidgetEditToolbar(
  showMoveUp: Boolean,
  showMoveDown: Boolean,
  onMoveUp: () -> Unit,
  onMoveDown: () -> Unit,
  onDelete: () -> Unit,
  onDone: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Row(
    modifier =
      modifier
        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(24.dp))
        .padding(4.dp),
    horizontalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    // Move Up
    if (showMoveUp) {
      FilledIconButton(
        onClick = onMoveUp,
        modifier = Modifier.size(32.dp),
        colors =
          IconButtonDefaults.filledIconButtonColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
          ),
      ) {
        Icon(Icons.Default.ArrowUpward, null, modifier = Modifier.size(16.dp))
      }
    }

    // Move Down
    if (showMoveDown) {
      FilledIconButton(
        onClick = onMoveDown,
        modifier = Modifier.size(32.dp),
        colors =
          IconButtonDefaults.filledIconButtonColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
          ),
      ) {
        Icon(Icons.Default.ArrowDownward, null, modifier = Modifier.size(16.dp))
      }
    }

    // Delete
    FilledIconButton(
      onClick = onDelete,
      modifier = Modifier.size(32.dp),
      colors =
        IconButtonDefaults.filledIconButtonColors(
          containerColor = MaterialTheme.colorScheme.errorContainer
        ),
    ) {
      Icon(
        Icons.Default.Delete,
        null,
        modifier = Modifier.size(16.dp),
        tint = MaterialTheme.colorScheme.onErrorContainer,
      )
    }

    // Done (Check)
    FilledIconButton(
      onClick = onDone,
      modifier = Modifier.size(32.dp),
      colors =
        IconButtonDefaults.filledIconButtonColors(
          containerColor = MaterialTheme.colorScheme.primary
        ),
    ) {
      Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp))
    }
  }
}
