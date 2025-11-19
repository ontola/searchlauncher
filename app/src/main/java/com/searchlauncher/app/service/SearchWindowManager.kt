package com.searchlauncher.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.Build
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.searchlauncher.app.data.CustomShortcut
import com.searchlauncher.app.data.CustomShortcuts
import com.searchlauncher.app.data.SearchRepository
import com.searchlauncher.app.data.SearchResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SearchWindowManager(private val context: Context, private val windowManager: WindowManager) {
    private var searchView: View? = null
    private var lifecycleOwner: MyLifecycleOwner? = null
    private val searchRepository = SearchRepository(context)
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    private var closeSystemDialogsReceiver: BroadcastReceiver? = null

    // State for the proxy input
    private var hiddenEditText: EditText? = null
    private var queryState = mutableStateOf("")

    init {
        scope.launch { searchRepository.initialize() }
    }

    fun show() {
        if (searchView != null) return

        // Removed re-indexing on show for performance
        // scope.launch {
        //    searchRepository.indexShortcuts()
        //    searchRepository.indexCustomShortcuts()
        // }

        lifecycleOwner = MyLifecycleOwner()
        lifecycleOwner?.onCreate()
        lifecycleOwner?.onStart()
        lifecycleOwner?.onResume()

        // Reset query state
        queryState.value = ""
        var topResult: SearchResult? = null

        // 1. Container
        val rootLayout = FrameLayout(context)
        rootLayout.setViewTreeLifecycleOwner(lifecycleOwner)
        rootLayout.setViewTreeSavedStateRegistryOwner(lifecycleOwner)

        // 2. Hidden EditText (The actual input receiver)
        hiddenEditText =
                EditText(context).apply {
                    layoutParams = FrameLayout.LayoutParams(1, 1)
                    alpha = 0f
                    inputType = android.text.InputType.TYPE_CLASS_TEXT
                    imeOptions = EditorInfo.IME_ACTION_SEARCH
                    isFocusable = true
                    isFocusableInTouchMode = true

                    addTextChangedListener(
                            object : TextWatcher {
                                override fun beforeTextChanged(
                                        s: CharSequence?,
                                        start: Int,
                                        count: Int,
                                        after: Int
                                ) {}
                                override fun onTextChanged(
                                        s: CharSequence?,
                                        start: Int,
                                        before: Int,
                                        count: Int
                                ) {
                                    queryState.value = s?.toString() ?: ""
                                }
                                override fun afterTextChanged(s: Editable?) {}
                            }
                    )

                    setOnEditorActionListener { _, actionId, _ ->
                        if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                            val imm =
                                    context.getSystemService(Context.INPUT_METHOD_SERVICE) as
                                            InputMethodManager
                            imm.hideSoftInputFromWindow(windowToken, 0)

                            // Launch top result if available
                            topResult?.let {
                                if (it is SearchResult.SearchIntent) {
                                    // For intents, we update the query instead of launching
                                    // But since we are in the listener, updating EditText is tricky
                                    // if we want to keep keyboard open
                                    // Actually, user pressed Enter, so they probably expect action.
                                    // If it's an intent discovery, maybe we should just "select"
                                    // it.
                                    // Let's update the text.
                                    setText(it.trigger + " ")
                                    setSelection(text.length)
                                    // Don't hide UI
                                } else {
                                    launchResult(context, it)
                                    hide()
                                    scope.launch {
                                        searchRepository.reportUsage(it.namespace, it.id)
                                    }
                                }
                            }
                            true
                        } else false
                    }
                }
        rootLayout.addView(hiddenEditText)

        // 3. ComposeView (The UI)
        val composeView =
                ComposeView(context).apply {
                    setViewTreeLifecycleOwner(lifecycleOwner)
                    setViewTreeSavedStateRegistryOwner(lifecycleOwner)

                    setContent {
                        SearchUI(
                                queryState = queryState,
                                onQueryChange = { newText ->
                                    hiddenEditText?.setText(newText)
                                    hiddenEditText?.setSelection(newText.length)
                                },
                                onDismiss = { hide() },
                                onFocusRequest = {
                                    hiddenEditText?.requestFocus()
                                    val imm =
                                            context.getSystemService(
                                                    Context.INPUT_METHOD_SERVICE
                                            ) as
                                                    InputMethodManager
                                    imm.showSoftInput(
                                            hiddenEditText,
                                            InputMethodManager.SHOW_IMPLICIT
                                    )
                                },
                                searchRepository = searchRepository,
                                onTopResultChange = { result -> topResult = result }
                        )
                    }
                }
        rootLayout.addView(composeView)

        val params =
                WindowManager.LayoutParams(
                                WindowManager.LayoutParams.MATCH_PARENT,
                                WindowManager.LayoutParams.MATCH_PARENT,
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                                } else {
                                    WindowManager.LayoutParams.TYPE_PHONE
                                },
                                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                                        WindowManager.LayoutParams.FLAG_DIM_BEHIND,
                                PixelFormat.TRANSLUCENT
                        )
                        .apply {
                            gravity = Gravity.BOTTOM
                            dimAmount = 0.5f
                            softInputMode =
                                    WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE or
                                            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                        }

        windowManager.addView(rootLayout, params)
        searchView = rootLayout

        // Initial Focus
        rootLayout.postDelayed(
                {
                    hiddenEditText?.requestFocus()
                    val imm =
                            context.getSystemService(Context.INPUT_METHOD_SERVICE) as
                                    InputMethodManager
                    imm.showSoftInput(hiddenEditText, InputMethodManager.SHOW_IMPLICIT)
                },
                100
        )

        // Register receiver for Home/Recents
        closeSystemDialogsReceiver =
                object : BroadcastReceiver() {
                    override fun onReceive(context: Context, intent: Intent) {
                        if (Intent.ACTION_CLOSE_SYSTEM_DIALOGS == intent.action) {
                            hide()
                        }
                    }
                }
        context.registerReceiver(
                closeSystemDialogsReceiver,
                IntentFilter(Intent.ACTION_CLOSE_SYSTEM_DIALOGS),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    Context.RECEIVER_EXPORTED
                } else {
                    0
                }
        )
    }

    fun hide() {
        // Unregister receiver
        closeSystemDialogsReceiver?.let {
            try {
                context.unregisterReceiver(it)
            } catch (e: Exception) {
                // Ignore if already unregistered
            }
            closeSystemDialogsReceiver = null
        }

        searchView?.let {
            windowManager.removeView(it)
            searchView = null
            hiddenEditText = null

            lifecycleOwner?.onPause()
            lifecycleOwner?.onStop()
            lifecycleOwner?.onDestroy()
            lifecycleOwner = null
        }
    }

    @Composable
    private fun SearchUI(
            queryState: State<String>,
            onQueryChange: (String) -> Unit,
            onDismiss: () -> Unit,
            onFocusRequest: () -> Unit,
            searchRepository: SearchRepository,
            onTopResultChange: (SearchResult?) -> Unit
    ) {
        val query = queryState.value
        var searchResults by remember { mutableStateOf<List<SearchResult>>(emptyList()) }
        var isLoading by remember { mutableStateOf(false) }
        val context = LocalContext.current

        // Ensure focus is requested when UI appears
        LaunchedEffect(Unit) { onFocusRequest() }

        LaunchedEffect(query) {
            if (query.isEmpty()) {
                searchResults = searchRepository.searchApps("", limit = 50)
            } else {
                delay(50) // Debounce
                searchResults = searchRepository.searchApps(query)
            }
            onTopResultChange(searchResults.firstOrNull())
        }

        Box(
                modifier =
                        Modifier.fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.7f))
                                .clickable { onDismiss() }
        ) {
            Column(
                    modifier =
                            Modifier.fillMaxSize()
                                    .padding(16.dp)
                                    .navigationBarsPadding()
                                    .imePadding(),
                    verticalArrangement = Arrangement.Bottom
            ) {
                // Results (displayed above search bar)
                if (searchResults.isNotEmpty() || (query.isNotEmpty() && !isLoading)) {
                    Surface(
                            modifier =
                                    Modifier.fillMaxWidth().weight(1f, fill = false).clickable(
                                                    indication = null,
                                                    interactionSource =
                                                            remember { MutableInteractionSource() }
                                            ) {},
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surface,
                            tonalElevation = 2.dp
                    ) {
                        if (isLoading) {
                            Box(
                                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                                    contentAlignment = Alignment.Center
                            ) { CircularProgressIndicator() }
                        } else {
                            LazyColumn(reverseLayout = true) {
                                items(searchResults) { result ->
                                    SearchResultItem(
                                            result = result,
                                            onClick = {
                                                if (result is SearchResult.SearchIntent) {
                                                    onQueryChange(result.trigger + " ")
                                                } else {
                                                    launchResult(context, result)
                                                    onDismiss()

                                                    // Report usage for ranking
                                                    scope.launch {
                                                        searchRepository.reportUsage(
                                                                result.namespace,
                                                                result.id
                                                        )
                                                    }
                                                }
                                            }
                                    )
                                }

                                if (searchResults.isEmpty() && query.isNotEmpty()) {
                                    item {
                                        Text(
                                                text = "No results found",
                                                modifier = Modifier.padding(32.dp),
                                                style = MaterialTheme.typography.bodyLarge,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Search bar (at bottom)
                Surface(
                        modifier =
                                Modifier.fillMaxWidth().clickable(
                                                indication = null,
                                                interactionSource =
                                                        remember { MutableInteractionSource() }
                                        ) {},
                        shape = RoundedCornerShape(28.dp),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 3.dp
                ) {
                    Row(
                            modifier =
                                    Modifier.fillMaxWidth()
                                            .heightIn(min = 48.dp) // Ensure constant height
                                            .padding(horizontal = 16.dp, vertical = 8.dp)
                                            .clickable {
                                                onFocusRequest()
                                            }, // Redirect tap to EditText
                            verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        // Read-only TextField that mirrors the EditText state
                        // Read-only text display that mirrors the EditText state
                        Box(
                                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                                contentAlignment = Alignment.CenterStart
                        ) {
                            if (query.isEmpty()) {
                                Text(
                                        text = "Search apps and content…",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodyLarge
                                )
                            } else {
                                val shortcutMatch =
                                        CustomShortcuts.shortcuts.filterIsInstance<
                                                        CustomShortcut.Search>()
                                                .find { query.startsWith("${it.trigger} ") }

                                if (shortcutMatch != null) {
                                    val chipColor =
                                            if (shortcutMatch.color != null) {
                                                Color(shortcutMatch.color.toInt())
                                            } else {
                                                MaterialTheme.colorScheme.primaryContainer
                                            }
                                    val onChipColor =
                                            if (shortcutMatch.color != null) {
                                                Color.White
                                            } else {
                                                MaterialTheme.colorScheme.onPrimaryContainer
                                            }

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Surface(
                                                color = chipColor,
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier.padding(end = 8.dp)
                                        ) {
                                            Text(
                                                    text = shortcutMatch.trigger,
                                                    modifier =
                                                            Modifier.padding(
                                                                    horizontal = 8.dp,
                                                                    vertical = 2.dp
                                                            ),
                                                    style = MaterialTheme.typography.labelLarge,
                                                    color = onChipColor
                                            )
                                        }
                                        Text(
                                                text =
                                                        query.substring(
                                                                shortcutMatch.trigger.length + 1
                                                        ),
                                                style = MaterialTheme.typography.bodyLarge,
                                                color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                } else {
                                    Text(
                                            text = query,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            style = MaterialTheme.typography.bodyLarge
                                    )
                                }
                            }
                        }

                        if (query.isNotEmpty()) {
                            IconButton(
                                    onClick = { onQueryChange("") },
                                    modifier = Modifier.size(32.dp).padding(4.dp)
                            ) {
                                Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Clear",
                                        modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun SearchResultItem(result: SearchResult, onClick: () -> Unit) {
        Row(
                modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon would be shown here if we had a way to render Drawable in Compose
            // For now, we'll use a placeholder

            Column(modifier = Modifier.weight(1f).padding(start = 16.dp)) {
                Text(
                        text = result.title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                )

                result.subtitle?.let { subtitle ->
                    Text(
                            text = subtitle,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    private fun launchResult(context: Context, result: SearchResult) {
        when (result) {
            is SearchResult.App -> {
                val launchIntent =
                        context.packageManager.getLaunchIntentForPackage(result.packageName)
                launchIntent?.let {
                    it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(it)
                }
            }
            is SearchResult.Content -> {
                // Launch deep link if available
                result.deepLink?.let { deepLink ->
                    try {
                        val intent =
                                if (deepLink.startsWith("intent:")) {
                                    Intent.parseUri(deepLink, Intent.URI_INTENT_SCHEME)
                                } else {
                                    Intent(Intent.ACTION_VIEW, android.net.Uri.parse(deepLink))
                                }

                        if (intent.action == "com.searchlauncher.RESET_INDEX") {
                            scope.launch {
                                searchRepository.resetIndex()
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(
                                                    context,
                                                    "Search Index Reset",
                                                    Toast.LENGTH_SHORT
                                            )
                                            .show()
                                }
                            }
                        } else if (intent.action ==
                                        "com.searchlauncher.action.TOGGLE_FLASHLIGHT"
                        ) {
                            toggleFlashlight(context)
                        } else {
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(intent)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            is SearchResult.Shortcut -> {
                try {
                    val uri = result.intentUri
                    if (uri.startsWith("shortcut://")) {
                        val parts = uri.substring("shortcut://".length).split("/")
                        if (parts.size == 2) {
                            val pkg = parts[0]
                            val id = parts[1]
                            val launcherApps =
                                    context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as
                                            android.content.pm.LauncherApps
                            launcherApps.startShortcut(
                                    pkg,
                                    id,
                                    null,
                                    null,
                                    android.os.Process.myUserHandle()
                            )
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            is SearchResult.SearchIntent -> {
                // Handled in UI callback, but we need to pass the callback down or handle it here
                // if we have access to state
                // launchResult doesn't have access to onQueryChange.
                // We need to refactor SearchResultItem onClick to handle this specific case in
                // SearchUI
            }
        }
    }

    private fun toggleFlashlight(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val cameraManager =
                    context.getSystemService(Context.CAMERA_SERVICE) as
                            android.hardware.camera2.CameraManager
            try {
                val cameraId =
                        cameraManager.cameraIdList.firstOrNull { id ->
                            val characteristics = cameraManager.getCameraCharacteristics(id)
                            characteristics.get(
                                    android.hardware.camera2.CameraCharacteristics
                                            .FLASH_INFO_AVAILABLE
                            ) == true
                        }

                if (cameraId != null) {
                    cameraManager.registerTorchCallback(
                            object : android.hardware.camera2.CameraManager.TorchCallback() {
                                override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                                    super.onTorchModeChanged(cameraId, enabled)
                                    cameraManager.unregisterTorchCallback(this)
                                    try {
                                        cameraManager.setTorchMode(cameraId, !enabled)
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                        Toast.makeText(
                                                        context,
                                                        "Error toggling flashlight",
                                                        Toast.LENGTH_SHORT
                                                )
                                                .show()
                                    }
                                }
                            },
                            null
                    )
                } else {
                    Toast.makeText(context, "No flash available", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(context, "Error accessing camera", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(
                            context,
                            "Flashlight not supported on this device",
                            Toast.LENGTH_SHORT
                    )
                    .show()
        }
    }
}
