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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import com.searchlauncher.app.data.SearchRepository
import com.searchlauncher.app.data.SearchResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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

        // Refresh shortcuts when showing
        scope.launch { searchRepository.indexShortcuts() }

        lifecycleOwner = MyLifecycleOwner()
        lifecycleOwner?.onCreate()
        lifecycleOwner?.onStart()
        lifecycleOwner?.onResume()

        // Reset query state
        queryState.value = ""

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
                                searchRepository = searchRepository
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
                            gravity = Gravity.TOP
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
            searchRepository: SearchRepository
    ) {
        val query = queryState.value
        var searchResults by remember { mutableStateOf<List<SearchResult>>(emptyList()) }
        var isLoading by remember { mutableStateOf(false) }
        val context = LocalContext.current

        // Ensure focus is requested when UI appears
        LaunchedEffect(Unit) { onFocusRequest() }

        LaunchedEffect(query) {
            if (query.isEmpty()) {
                searchResults = searchRepository.searchApps("")
            } else {
                delay(300) // Debounce
                searchResults = searchRepository.searchApps(query)
            }
        }

        Box(
                modifier =
                        Modifier.fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.7f))
                                .clickable { onDismiss() }
        ) {
            Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp).clickable(enabled = false) {}
            ) {
                // Search bar
                Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(28.dp),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 3.dp
                ) {
                    Row(
                            modifier =
                                    Modifier.fillMaxWidth()
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
                                Text(
                                        text = query,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        }

                        if (query.isNotEmpty()) {
                            IconButton(onClick = { onQueryChange("") }) {
                                Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Clear"
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Results
                Surface(
                        modifier = Modifier.fillMaxWidth(),
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
                        LazyColumn(modifier = Modifier.heightIn(max = 500.dp)) {
                            items(searchResults) { result ->
                                SearchResultItem(
                                        result = result,
                                        onClick = {
                                            launchResult(context, result)
                                            onDismiss()
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
                    val intent =
                            Intent(Intent.ACTION_VIEW).apply {
                                data = android.net.Uri.parse(deepLink)
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                    try {
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        e.printStackTrace()
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
        }
    }
}
