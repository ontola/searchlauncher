package com.searchlauncher.app.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.datastore.preferences.core.edit
import coil.compose.AsyncImage
import com.google.android.material.color.utilities.Hct
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ThemeSettingsCard(onNavigateToHome: () -> Unit) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val themeColor by
          context.dataStore
                  .data
                  .map { it[MainActivity.PreferencesKeys.THEME_COLOR] ?: 0xFF00639B.toInt() }
                  .collectAsState(initial = 0xFF00639B.toInt())

  val themeSaturation by
          context.dataStore
                  .data
                  .map { it[MainActivity.PreferencesKeys.THEME_SATURATION] ?: 50f }
                  .collectAsState(initial = 50f)
  val darkMode by
          context.dataStore
                  .data
                  .map { it[MainActivity.PreferencesKeys.DARK_MODE] ?: 0 }
                  .collectAsState(initial = 0)
  val backgroundUriString by
          context.dataStore
                  .data
                  .map { it[MainActivity.PreferencesKeys.BACKGROUND_URI] }
                  .collectAsState(initial = null)
  val backgroundFolderUriString by
          context.dataStore
                  .data
                  .map { it[MainActivity.PreferencesKeys.BACKGROUND_FOLDER_URI] }
                  .collectAsState(initial = null)

  var showColorPickerDialog by remember { mutableStateOf(false) }
  var showImageColorPickerDialog by remember { mutableStateOf(false) }

  val launcher =
          rememberLauncherForActivityResult(
                  contract = ActivityResultContracts.OpenDocument(),
                  onResult = { uri: Uri? ->
                    uri?.let {
                      val flag = Intent.FLAG_GRANT_READ_URI_PERMISSION
                      context.contentResolver.takePersistableUriPermission(it, flag)
                      scope.launch {
                        context.dataStore.edit { preferences ->
                          preferences[MainActivity.PreferencesKeys.BACKGROUND_URI] = it.toString()
                          preferences.remove(MainActivity.PreferencesKeys.BACKGROUND_FOLDER_URI)
                        }
                        // Navigate after save completes
                        withContext(Dispatchers.Main) { onNavigateToHome() }
                      }
                    }
                  },
          )

  val folderLauncher =
          rememberLauncherForActivityResult(
                  contract = ActivityResultContracts.OpenDocumentTree(),
                  onResult = { uri: Uri? ->
                    uri?.let {
                      val flag = Intent.FLAG_GRANT_READ_URI_PERMISSION
                      context.contentResolver.takePersistableUriPermission(it, flag)
                      scope.launch {
                        context.dataStore.edit { preferences ->
                          preferences[MainActivity.PreferencesKeys.BACKGROUND_FOLDER_URI] =
                                  it.toString()
                          preferences.remove(MainActivity.PreferencesKeys.BACKGROUND_URI)
                        }
                        // Navigate after save completes
                        withContext(Dispatchers.Main) { onNavigateToHome() }
                      }
                    }
                  },
          )

  Card(modifier = Modifier.fillMaxWidth()) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
      // Background Image section (moved to top)
      Text(
              text = "Background Image",
              style = MaterialTheme.typography.titleMedium,
              modifier = Modifier.fillMaxWidth(),
      )
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
                onClick = { launcher.launch(arrayOf("image/*")) },
                modifier = Modifier.weight(1f),
        ) { Text("Pick Image") }
        OutlinedButton(onClick = { folderLauncher.launch(null) }, modifier = Modifier.weight(1f)) {
          Text("Pick Folder")
        }
      }

      if (backgroundUriString != null || backgroundFolderUriString != null) {
        OutlinedButton(
                onClick = {
                  scope.launch {
                    context.dataStore.edit { preferences ->
                      preferences.remove(MainActivity.PreferencesKeys.BACKGROUND_URI)
                      preferences.remove(MainActivity.PreferencesKeys.BACKGROUND_FOLDER_URI)
                    }
                    // Navigate after clear completes
                    withContext(Dispatchers.Main) { onNavigateToHome() }
                  }
                },
                modifier = Modifier.fillMaxWidth(),
        ) { Text("Clear Background") }
      }

      // Preview of selected image
      if (backgroundUriString != null) {
        AsyncImage(
                model = android.net.Uri.parse(backgroundUriString),
                contentDescription = "Background preview",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().height(120.dp).clip(MaterialTheme.shapes.medium),
        )
      }
      if (backgroundFolderUriString != null) {
        Text(
                text = "Folder selected. Images will rotate.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth(),
        )
      }

      HorizontalDivider()

      // Theme Color section
      Text(
              text = "Theme Color",
              style = MaterialTheme.typography.titleMedium,
              modifier = Modifier.fillMaxWidth(),
      )

      Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalAlignment = Alignment.CenterVertically,
      ) {
        // Current color circle
        Box(
                modifier =
                        Modifier.size(48.dp)
                                .clip(CircleShape)
                                .background(Color(themeColor))
                                .border(2.dp, MaterialTheme.colorScheme.outline, CircleShape)
                                .clickable { showColorPickerDialog = true }
        )

        // Pick from image button
        if (backgroundUriString != null) {
          OutlinedButton(
                  onClick = { showImageColorPickerDialog = true },
                  modifier = Modifier.weight(1f),
          ) { Text("Pick from Image") }
        }
      }

      HorizontalDivider()

      // Dark Mode section
      Text(
              text = "Dark Mode",
              style = MaterialTheme.typography.titleMedium,
              modifier = Modifier.fillMaxWidth(),
      )
      val modes = listOf("System", "Light", "Dark")
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        modes.forEachIndexed { index, mode ->
          OutlinedButton(
                  onClick = {
                    scope.launch {
                      context.dataStore.edit { preferences ->
                        preferences[MainActivity.PreferencesKeys.DARK_MODE] = index
                      }
                    }
                  },
                  modifier = Modifier.weight(1f),
                  colors =
                          if (darkMode == index) {
                            ButtonDefaults.outlinedButtonColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                          } else {
                            ButtonDefaults.outlinedButtonColors()
                          },
          ) { Text(mode) }
        }
      }
    }
  }

  // Color picker dialog with sliders
  if (showColorPickerDialog) {
    ColorPickerDialog(
            currentColor = themeColor,
            currentSaturation = themeSaturation,
            onDismiss = { showColorPickerDialog = false },
            onColorSelected = { color, saturation ->
              scope.launch {
                context.dataStore.edit { preferences ->
                  preferences[MainActivity.PreferencesKeys.THEME_COLOR] = color
                  preferences[MainActivity.PreferencesKeys.THEME_SATURATION] = saturation
                }
              }
              showColorPickerDialog = false
            },
    )
  }

  // Fullscreen image color picker dialog
  backgroundUriString?.let { uri ->
    if (showImageColorPickerDialog) {
      ImageColorPickerDialog(
              imageUri = uri,
              onDismiss = { showImageColorPickerDialog = false },
              onColorSelected = { color, saturation ->
                scope.launch {
                  context.dataStore.edit { preferences ->
                    preferences[MainActivity.PreferencesKeys.THEME_COLOR] = color
                    preferences[MainActivity.PreferencesKeys.THEME_SATURATION] = saturation
                  }
                }
                showImageColorPickerDialog = false
              },
      )
    }
  }
}

@Composable
@android.annotation.SuppressLint("RestrictedApi")
private fun ColorPickerDialog(
        currentColor: Int,
        currentSaturation: Float,
        onDismiss: () -> Unit,
        onColorSelected: (Int, Float) -> Unit,
) {
  val hct = remember(currentColor) { Hct.fromInt(currentColor) }
  var hue by remember(currentColor) { mutableStateOf(hct.hue.toFloat()) }
  var saturation by remember(currentSaturation) { mutableStateOf(currentSaturation) }

  AlertDialog(
          onDismissRequest = onDismiss,
          title = { Text("Pick a Color") },
          text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
              Text("Hue", style = MaterialTheme.typography.titleSmall)
              Box(contentAlignment = Alignment.Center) {
                Box(
                        modifier =
                                Modifier.fillMaxWidth()
                                        .height(12.dp)
                                        .clip(CircleShape)
                                        .background(
                                                brush =
                                                        Brush.horizontalGradient(
                                                                colors =
                                                                        listOf(
                                                                                Color.Red,
                                                                                Color.Yellow,
                                                                                Color.Green,
                                                                                Color.Cyan,
                                                                                Color.Blue,
                                                                                Color.Magenta,
                                                                                Color.Red,
                                                                        )
                                                        )
                                        )
                )
                Slider(
                        value = hue,
                        onValueChange = { hue = it },
                        valueRange = 0f..360f,
                        colors =
                                SliderDefaults.colors(
                                        thumbColor = Color.White,
                                        activeTrackColor = Color.Transparent,
                                        inactiveTrackColor = Color.Transparent,
                                ),
                )
              }

              Text("Saturation", style = MaterialTheme.typography.titleSmall)
              Slider(value = saturation, onValueChange = { saturation = it }, valueRange = 0f..100f)
            }
          },
          confirmButton = {
            TextButton(
                    onClick = {
                      val newColor = Hct.from(hue.toDouble(), 48.0, 40.0).toInt()
                      onColorSelected(newColor, saturation)
                    }
            ) { Text("Apply") }
          },
          dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
  )
}

@Composable
@android.annotation.SuppressLint("RestrictedApi")
private fun ImageColorPickerDialog(
        imageUri: String,
        onDismiss: () -> Unit,
        onColorSelected: (Int, Float) -> Unit,
) {
  val context = LocalContext.current
  var imageBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
  var imageSize by remember { mutableStateOf<Size?>(null) }

  // Load bitmap in background
  LaunchedEffect(imageUri) {
    withContext(Dispatchers.IO) {
      try {
        val uri = android.net.Uri.parse(imageUri)
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
          val bitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
          withContext(Dispatchers.Main) { imageBitmap = bitmap }
        }
      } catch (e: Exception) {
        // Silently fail
      }
    }
  }

  Dialog(
          onDismissRequest = onDismiss,
          properties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
      AsyncImage(
              model = android.net.Uri.parse(imageUri),
              contentDescription = "Pick color from image",
              contentScale = ContentScale.Fit,
              modifier =
                      Modifier.fillMaxSize()
                              .pointerInput(imageBitmap) {
                                if (imageBitmap == null) return@pointerInput

                                detectTapGestures { offset ->
                                  imageSize?.let { size ->
                                    imageBitmap?.let { bitmap ->
                                      val x =
                                              (offset.x / size.width * bitmap.width)
                                                      .toInt()
                                                      .coerceIn(0, bitmap.width - 1)
                                      val y =
                                              (offset.y / size.height * bitmap.height)
                                                      .toInt()
                                                      .coerceIn(0, bitmap.height - 1)

                                      val pixelColor = bitmap.getPixel(x, y)
                                      val hct = Hct.fromInt(pixelColor)

                                      val newColor =
                                              Hct.from(
                                                              hct.hue,
                                                              hct.chroma.coerceIn(0.0, 100.0),
                                                              40.0
                                                      )
                                                      .toInt()
                                      val saturationPercent =
                                              (hct.chroma / 100.0 * 100.0)
                                                      .toFloat()
                                                      .coerceIn(0f, 100f)

                                      onColorSelected(newColor, saturationPercent)
                                    }
                                  }
                                }
                              }
                              .onGloballyPositioned { coordinates ->
                                imageSize = coordinates.size.toSize()
                              },
      )

      // Close button
      IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)) {
        Icon(
                Icons.Default.Close,
                contentDescription = "Close",
                tint = Color.White,
                modifier = Modifier.size(32.dp),
        )
      }

      // Instruction text
      Text(
              text = "Tap anywhere on the image to pick a color",
              color = Color.White,
              style = MaterialTheme.typography.bodyLarge,
              modifier =
                      Modifier.align(Alignment.BottomCenter)
                              .padding(32.dp)
                              .background(
                                      Color.Black.copy(alpha = 0.7f),
                                      MaterialTheme.shapes.medium
                              )
                              .padding(16.dp),
      )
    }
  }
}
