package com.searchlauncher.app.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

@Composable
fun rememberPermissionState(check: () -> Boolean): State<Boolean> {
    val lifecycleOwner = LocalLifecycleOwner.current
    val state = remember { mutableStateOf(check()) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                state.value = check()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    return state
}

@Composable
fun OnboardingScreen(onComplete: () -> Unit) {
    var currentStep by remember { mutableStateOf(0) }

    Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
    ) {
        Spacer(modifier = Modifier.height(32.dp))

        when (currentStep) {
            0 -> WelcomeStep()
            1 -> OverlayPermissionStep()
            2 -> AccessibilityPermissionStep()
            3 -> UsageStatsPermissionStep()
            4 -> AdvancedFeaturesStep()
            5 -> CompleteStep()
        }

        Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Progress indicator
            Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(bottom = 24.dp)
            ) {
                repeat(6) { index ->
                    Box(
                            modifier =
                                    Modifier.size(8.dp)
                                            .then(if (index == currentStep) Modifier else Modifier)
                    ) {
                        if (index == currentStep) {
                            Surface(
                                    modifier = Modifier.fillMaxSize(),
                                    color = MaterialTheme.colorScheme.primary,
                                    shape = MaterialTheme.shapes.small
                            ) {}
                        } else {
                            Surface(
                                    modifier = Modifier.fillMaxSize(),
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = MaterialTheme.shapes.small
                            ) {}
                        }
                    }
                }
            }

            Button(
                    onClick = {
                        if (currentStep < 5) {
                            currentStep++
                        } else {
                            onComplete()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                        text =
                                when (currentStep) {
                                    0 -> "Get Started"
                                    5 -> "Finish"
                                    else -> "Continue"
                                }
                )
            }

            if (currentStep > 0 && currentStep < 5) {
                TextButton(onClick = { currentStep++ }, modifier = Modifier.fillMaxWidth()) {
                    Text("Skip")
                }
            }
        }
    }
}

@Composable
fun WelcomeStep() {
    Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.primary
        )

        Text(
                text = "Welcome to SearchLauncher",
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center
        )

        Text(
                text = "Search everything on your phone from any screen with a simple gesture",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun OverlayPermissionStep() {
    val context = LocalContext.current
    val isGranted = rememberPermissionState {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else true
    }

    PermissionStep(
            icon = Icons.Default.Settings,
            title = "Display Over Other Apps",
            description =
                    "This permission allows SearchLauncher to show the search bar on top of other apps. This is essential for the app to work.",
            isGranted = isGranted.value,
            onGrantClick = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val intent =
                            Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:${context.packageName}")
                            )
                    context.startActivity(intent)
                }
            }
    )
}

@Composable
fun AccessibilityPermissionStep() {
    val context = LocalContext.current
    val isGranted = rememberPermissionState { isAccessibilityServiceEnabled(context) }

    PermissionStep(
            icon = Icons.Default.Lock,
            title = "Accessibility Service",
            description =
                    "This permission allows SearchLauncher to detect gestures from any screen. Without this, you'll need to open the app manually each time.",
            isGranted = isGranted.value,
            onGrantClick = {
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                context.startActivity(intent)
            }
    )
}

@Composable
fun UsageStatsPermissionStep() {
    val context = LocalContext.current
    val isGranted = rememberPermissionState { hasUsageStatsPermission(context) }

    PermissionStep(
            icon = Icons.Default.Settings,
            title = "Usage Access (Optional)",
            description =
                    "This optional permission helps sort apps by most recently used, making search results more relevant to you.",
            isGranted = isGranted.value,
            isOptional = true,
            onGrantClick = {
                val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                context.startActivity(intent)
            }
    )
}

@Composable
fun CompleteStep() {
    Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.primary
        )

        Text(
                text = "You're All Set!",
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center
        )

        Text(
                text = "Swipe from the edge and back to open the search bar from any screen.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun PermissionStep(
        icon: androidx.compose.ui.graphics.vector.ImageVector,
        title: String,
        description: String,
        isGranted: Boolean,
        isOptional: Boolean = false,
        onGrantClick: () -> Unit
) {
    Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary
        )

        Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center
        )

        if (isOptional) {
            Text(
                    text = "Optional",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary
            )
        }

        Text(
                text = description,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (isGranted) {
            Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors =
                            CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer
                            )
            ) {
                Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                            text = "Permission Granted",
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        } else {
            Button(onClick = onGrantClick, modifier = Modifier.fillMaxWidth()) {
                Text("Grant Permission")
            }
        }
    }
}

@Composable
fun AdvancedFeaturesStep() {
    val context = LocalContext.current

    Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary
        )

        Text(
                text = "Advanced Features (Optional)",
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center
        )

        Text(
                text = "Enable extra capabilities like Rotation Lock.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Rotation
        val writeSettingsGranted =
                rememberPermissionState {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                                    Settings.System.canWrite(context)
                            else true
                        }
                        .value

        PermissionStatusItem(
                title = "Modify System Settings",
                granted = writeSettingsGranted,
                onGrant = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS)
                        intent.data = Uri.parse("package:${context.packageName}")
                        context.startActivity(intent)
                    }
                }
        )
    }
}

@Composable
fun PermissionStatusItem(title: String, granted: Boolean, onGrant: () -> Unit) {
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
                Text(text = title, style = MaterialTheme.typography.bodyMedium)
                Text(
                        text = if (granted) "Granted" else "Not granted",
                        style = MaterialTheme.typography.bodySmall,
                        color =
                                if (granted) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.error
                )
            }
            if (!granted) {
                TextButton(onClick = onGrant) { Text("Grant") }
            } else {
                Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
