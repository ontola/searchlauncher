package com.searchlauncher.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.searchlauncher.app.SearchLauncherApp
import com.searchlauncher.app.data.SearchShortcut
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomShortcutsScreen(onBack: () -> Unit) {
        val context = LocalContext.current
        val app = context.applicationContext as SearchLauncherApp
        val shortcuts by app.searchShortcutRepository.items.collectAsState()
        val scope = rememberCoroutineScope()

        var showDialog by remember { mutableStateOf(false) }
        var editingShortcut by remember { mutableStateOf<SearchShortcut?>(null) }

        val snackbarHostState = remember { SnackbarHostState() }
        var deletedShortcut by remember { mutableStateOf<SearchShortcut?>(null) }

        Scaffold(
                topBar = {
                        TopAppBar(
                                title = { Text("Search Shortcuts") },
                                navigationIcon = {
                                        IconButton(onClick = onBack) {
                                                Icon(
                                                        Icons.AutoMirrored.Filled.ArrowBack,
                                                        contentDescription = "Back"
                                                )
                                        }
                                },
                                actions = {
                                        TextButton(
                                                onClick = {
                                                        scope.launch {
                                                                app.searchShortcutRepository
                                                                        .resetToDefaults()
                                                        }
                                                }
                                        ) { Text("Reset Defaults") }
                                }
                        )
                },
                floatingActionButton = {
                        FloatingActionButton(
                                onClick = {
                                        editingShortcut = null
                                        showDialog = true
                                }
                        ) { Icon(Icons.Default.Add, contentDescription = "Add Shortcut") }
                },
                snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { padding ->
                LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                        items(shortcuts) { shortcut ->
                                ShortcutItem(
                                        shortcut = shortcut,
                                        onEdit = {
                                                editingShortcut = shortcut
                                                showDialog = true
                                        },
                                        onDelete = {
                                                deletedShortcut = shortcut
                                                scope.launch {
                                                        app.searchShortcutRepository.removeShortcut(
                                                                shortcut.id
                                                        )
                                                        val result =
                                                                snackbarHostState.showSnackbar(
                                                                        message =
                                                                                "Shortcut deleted",
                                                                        actionLabel = "Undo",
                                                                        duration =
                                                                                SnackbarDuration
                                                                                        .Short
                                                                )
                                                        if (result == SnackbarResult.ActionPerformed
                                                        ) {
                                                                deletedShortcut?.let {
                                                                        app.searchShortcutRepository
                                                                                .addShortcut(it)
                                                                }
                                                        }
                                                        deletedShortcut = null
                                                }
                                        }
                                )
                        }
                }
        }

        if (showDialog) {
                ShortcutDialog(
                        shortcut = editingShortcut,
                        onDismiss = { showDialog = false },
                        onSave = { newShortcut ->
                                scope.launch {
                                        if (editingShortcut != null) {
                                                // Update existing shortcut
                                                app.searchShortcutRepository.updateAlias(
                                                        newShortcut.id,
                                                        newShortcut.alias
                                                )
                                        } else {
                                                app.searchShortcutRepository.addShortcut(
                                                        newShortcut
                                                )
                                        }
                                        showDialog = false
                                        // Re-index shortcuts after changes
                                        app.searchRepository.indexCustomShortcuts()
                                }
                        }
                )
        }
}

@Composable
fun ShortcutItem(shortcut: SearchShortcut, onEdit: () -> Unit, onDelete: () -> Unit) {
        Card(
                modifier = Modifier.fillMaxWidth(),
                colors =
                        CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
        ) {
                Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                ) {
                        Column(modifier = Modifier.weight(1f)) {
                                Text(
                                        text = shortcut.description,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                                )
                                Text(
                                        text = "Alias: ${shortcut.alias}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                        text = shortcut.urlTemplate,
                                        style = MaterialTheme.typography.bodySmall,
                                        color =
                                                MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                                        alpha = 0.7f
                                                )
                                )
                        }
                        Row {
                                IconButton(onClick = onEdit) {
                                        Icon(Icons.Default.Edit, contentDescription = "Edit")
                                }
                                IconButton(onClick = onDelete) {
                                        Icon(
                                                Icons.Default.Close,
                                                contentDescription = "Delete",
                                                tint = MaterialTheme.colorScheme.error
                                        )
                                }
                        }
                }
        }
}

@Composable
fun ShortcutDialog(
        shortcut: SearchShortcut?,
        onDismiss: () -> Unit,
        onSave: (SearchShortcut) -> Unit
) {
        var id by remember {
                mutableStateOf(shortcut?.id ?: java.util.UUID.randomUUID().toString())
        }
        var alias by remember { mutableStateOf(shortcut?.alias ?: "") }
        var urlTemplate by remember { mutableStateOf(shortcut?.urlTemplate ?: "") }
        var description by remember { mutableStateOf(shortcut?.description ?: "") }
        var colorHex by remember {
                mutableStateOf((shortcut?.color ?: 0xFF000000).toString(16).padStart(8, '0'))
        }

        AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text(if (shortcut != null) "Edit Alias" else "New Search Shortcut") },
                text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (shortcut != null) {
                                        // Editing: only allow changing the alias
                                        Text(
                                                text =
                                                        "Edit the alias/trigger for this search shortcut",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        OutlinedTextField(
                                                value = alias,
                                                onValueChange = { alias = it },
                                                label = { Text("Alias (e.g., 'r', 'yt')") },
                                                modifier = Modifier.fillMaxWidth(),
                                                singleLine = true
                                        )
                                } else {
                                        // New shortcut: allow all fields
                                        OutlinedTextField(
                                                value = description,
                                                onValueChange = { description = it },
                                                label = { Text("Description") },
                                                modifier = Modifier.fillMaxWidth()
                                        )
                                        OutlinedTextField(
                                                value = alias,
                                                onValueChange = { alias = it },
                                                label = { Text("Alias (e.g., 'r', 'yt')") },
                                                modifier = Modifier.fillMaxWidth(),
                                                singleLine = true
                                        )
                                        OutlinedTextField(
                                                value = urlTemplate,
                                                onValueChange = { urlTemplate = it },
                                                label = { Text("URL Template (use %s for query)") },
                                                modifier = Modifier.fillMaxWidth()
                                        )
                                        OutlinedTextField(
                                                value = colorHex,
                                                onValueChange = { colorHex = it },
                                                label = { Text("Color (Hex, e.g., FF4285F4)") },
                                                modifier = Modifier.fillMaxWidth()
                                        )
                                }
                        }
                },
                confirmButton = {
                        Button(
                                onClick = {
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
                                                        suggestionUrl = shortcut?.suggestionUrl,
                                                        packageName = shortcut?.packageName
                                                )
                                        onSave(newShortcut)
                                }
                        ) { Text("Save") }
                },
                dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
        )
}
