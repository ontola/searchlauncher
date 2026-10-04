package com.searchlauncher.app.ui.browser

import android.app.AlertDialog as NativeAlertDialog
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.view.View
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.searchlauncher.app.SearchLauncherApp
import com.searchlauncher.app.ui.PreferencesKeys
import com.searchlauncher.app.ui.components.SearchChromeBar
import com.searchlauncher.app.ui.dataStore
import com.searchlauncher.app.ui.theme.rememberBrowserSurfaceColors
import com.searchlauncher.app.util.displayPageAddress
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.mozilla.geckoview.GeckoSessionSettings
import org.mozilla.geckoview.GeckoView
import org.mozilla.geckoview.StorageController

internal object BrowserEngine {
  const val isGecko = true

  @Composable
  fun Content(
    navigationRequest: NavigationRequest?,
    privateMode: Boolean,
    showLauncherChrome: Boolean,
    browserMenuRequest: Long,
    onBrowserMenuShown: () -> Unit,
    tabActivationRequest: TabActivationRequest?,
    pinnedTabId: Long?,
    onOpenSearch: (Boolean, Int, String) -> Unit,
    onClose: () -> Unit,
    inPictureInPicture: Boolean,
  ) {
    val activity = LocalContext.current as BrowserActivity
    val scope = rememberCoroutineScope()
    val density = androidx.compose.ui.platform.LocalDensity.current
    // Website editing needs the full viewport above the IME, without browser controls
    // riding on top of it.
    val imeInsets = WindowInsets.ime
    val websiteKeyboardVisible = imeInsets.getBottom(density) > 0
    val localTab = remember { BrowserTab(navigationRequest?.url ?: "about:blank") }
    val tab = if (privateMode) localTab else pinnedTabId?.let(BrowserTabStore::tab) ?: return
    val page = remember(tab.id) { GeckoPage(activity, tab, privateMode, onClose) }
    val frameColor by
      animateColorAsState(
        Color(tab.frameColorArgb),
        animationSpec = tween(140),
        label = "browser frame color",
      )
    val frameContentColor = browserChromeContentColor(frameColor)
    val menuScheme = rememberBrowserSurfaceColors(Color(tab.frameColorArgb))
    val disabledContentColor = browserChromeDisabledColor(menuScheme.surface, menuScheme.onSurface)
    val menuColors =
      MenuDefaults.itemColors(
        textColor = menuScheme.onSurface,
        leadingIconColor = menuScheme.onSurface,
        disabledTextColor = disabledContentColor,
        disabledLeadingIconColor = disabledContentColor,
      )
    val swipe = rememberGeckoChromeSwipe(page, privateMode)
    fun goHome() {
      scope.launch {
        page.captureBeforeTransition()
        BrowserTabTasks.openHome(activity)
      }
    }
    val showFavorites by
      remember(activity) {
          activity.dataStore.data.map { it[PreferencesKeys.BROWSER_SHOW_FAVORITES] ?: false }
        }
        .collectAsState(initial = false)
    val chromeLayer = rememberGraphicsLayer()
    var chromeSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    val tabCount = BrowserTabStore.tabs?.items?.size ?: 1
    LaunchedEffect(
      tab.url,
      frameColor,
      showFavorites,
      tabCount,
      chromeSize,
      websiteKeyboardVisible,
    ) {
      if (
        !privateMode && !websiteKeyboardVisible && chromeSize.width > 0 && chromeSize.height > 0
      ) {
        // Capture the settled toolbar, never during the swipe or on every animation frame.
        kotlinx.coroutines.delay(100)
        tab.chromeSnapshot = chromeLayer.toImageBitmap()
      }
    }
    var menu by remember { mutableStateOf(false) }
    var overview by remember { mutableStateOf(false) }
    var find by remember { mutableStateOf(false) }
    var findText by remember { mutableStateOf("") }
    var showInfo by remember { mutableStateOf(false) }
    var fileReply by remember { mutableStateOf<((android.app.Activity?, Intent?) -> Unit)?>(null) }
    var permissionReply by remember { mutableStateOf<((Map<String, Boolean>) -> Unit)?>(null) }
    val fileLauncher =
      rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result
        ->
        val reply = fileReply
        fileReply = null
        reply?.invoke(
          if (result.resultCode == android.app.Activity.RESULT_OK) activity else null,
          result.data,
        )
      }
    val permissionLauncher =
      rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        val reply = permissionReply
        permissionReply = null
        reply?.invoke(it)
      }
    val prompts =
      remember(page) {
        GeckoPrompts(
          activity,
          { tab.url },
          chooseFile = { intent, reply ->
            fileReply?.invoke(null, null)
            fileReply = reply
            fileLauncher.launch(intent)
          },
          requestPermissions = { permissions, reply ->
            permissionReply?.invoke(emptyMap())
            permissionReply = reply
            permissionLauncher.launch(permissions)
          },
          privateMode = privateMode,
        )
      }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(page, lifecycle) {
      page.session.promptDelegate = prompts
      page.session.permissionDelegate = prompts.permissions
      val observer = LifecycleEventObserver { _, event ->
        when (event) {
          Lifecycle.Event.ON_START -> page.setVisible(true)
          Lifecycle.Event.ON_RESUME -> page.recoverIfNeeded()
          Lifecycle.Event.ON_PAUSE -> {
            page.persist()
            page.capture()
          }
          // Paused pages can still be visible (for example behind a permission/provider prompt).
          // Lower their process priority only once the browser is actually hidden.
          Lifecycle.Event.ON_STOP -> page.setVisible(false)
          else -> Unit
        }
      }
      lifecycle.addObserver(observer)
      page.start()
      onDispose {
        lifecycle.removeObserver(observer)
        fileReply?.invoke(null, null)
        fileReply = null
        permissionReply?.invoke(emptyMap())
        permissionReply = null
        prompts.close()
        page.persist()
        page.view?.releaseSession()
        page.close()
        if (activity.isFinishing && !privateMode) page.forget()
      }
    }
    LaunchedEffect(navigationRequest?.sequence) {
      navigationRequest?.let {
        if (it.url != page.tab.url || !page.session.isOpen) page.navigate(it.url)
      }
    }
    LaunchedEffect(browserMenuRequest) {
      if (browserMenuRequest != 0L) {
        menu = true
        onBrowserMenuShown()
      }
    }
    val revealingHome by
      remember(swipe) {
        derivedStateOf { swipe.inMotion && swipe.offset < 0f && swipe.neighbour(1) == null }
      }
    val systemBarColor = if (page.showDownloads) MaterialTheme.colorScheme.surface else frameColor
    DisposableEffect(activity) {
      // Keep the window configuration stable during a drag. The opaque page panel covers
      // the wallpaper except where the incoming home preview is being revealed.
      activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
      activity.window.setFormat(android.graphics.PixelFormat.TRANSLUCENT)
      activity.window.setBackgroundDrawable(
        android.graphics.drawable.ColorDrawable(AndroidColor.TRANSPARENT)
      )
      activity.window.isNavigationBarContrastEnforced = false
      onDispose {}
    }
    LaunchedEffect(systemBarColor) {
      activity.window.navigationBarColor = systemBarColor.toArgb()
      WindowCompat.getInsetsController(activity.window, activity.window.decorView).apply {
        isAppearanceLightStatusBars = browserChromeUsesDarkIcons(systemBarColor)
        isAppearanceLightNavigationBars = browserChromeUsesDarkIcons(systemBarColor)
      }
    }
    LaunchedEffect(page.fullscreen) {
      val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
      if (page.fullscreen) {
        controller.systemBarsBehavior =
          WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
      } else controller.show(WindowInsetsCompat.Type.systemBars())
    }
    BackHandler {
      when {
        overview -> overview = false
        find -> find = false
        page.fullscreen -> page.session.exitFullScreen()
        page.canGoBack -> page.session.goBack()
        else -> onClose()
      }
    }
    BoxWithConstraints(
      Modifier.fillMaxSize().drawBehind {
        // Transparent SrcOver is a no-op and can retain old pixels as the page moves away.
        // Replace the root pixels, revealing wallpaper only during a home swipe. Read the
        // state here so clearing and the translated page update in the same draw.
        drawRect(
          if (revealingHome) Color.Transparent else frameColor,
          blendMode = androidx.compose.ui.graphics.BlendMode.Src,
        )
      }
    ) {
      val screenWidth = maxWidth
      val screenHeight = maxHeight
      swipe.widthPx = with(density) { screenWidth.roundToPx() }
      GeckoSwipeDestination(swipe, frameColor)
      Column(
        Modifier.fillMaxSize()
          .graphicsLayer { translationX = swipe.offset }
          .background(frameColor)
          .windowInsetsPadding(if (page.fullscreen) WindowInsets(0) else WindowInsets.safeDrawing)
          .imePadding()
      ) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
          AndroidView(
            factory = { context ->
              GeckoView(context).also {
                // TextureView participates in the app's composition: it follows horizontal
                // transforms and does not punch SurfaceView holes through a home transition.
                it.setViewBackend(GeckoView.BACKEND_TEXTURE_VIEW)
                it.setBackgroundColor(AndroidColor.WHITE)
                it.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_YES
                it.setSession(page.session)
                it.setOnTouchListener { _, event ->
                  page.onTouch(event)
                  false
                }
                // Paint Gecko's compositor surface, not only the FrameLayout behind it.
                it.coverUntilFirstPaint(AndroidColor.WHITE)
                page.view = it
              }
            },
            update = { view ->
              view.visibility =
                if (overview || page.showDownloads || page.error != null) View.INVISIBLE
                else View.VISIBLE
            },
            modifier =
              Modifier.fillMaxSize().onGloballyPositioned {
                // Compose positions AndroidView using transforms. Notify Gecko after placement so
                // its screen-space accessibility and input bounds include the inset offset.
                page.view?.let { view ->
                  view.post { view.gatherTransparentRegion(android.graphics.Region()) }
                }
              },
          )
          if (page.awaitingPaint && page.error == null && !overview && !page.showDownloads) {
            // Retain the same page preview used by home until Gecko has painted on resume.
            // Swap directly; a fade would expose the compositor's temporary white surface.
            GeckoTabPreview(tab, Modifier.fillMaxSize())
          }
          page.error?.let { message ->
            Surface(Modifier.fillMaxSize()) {
              Column(Modifier.padding(24.dp)) {
                Text("Could not open this page", style = MaterialTheme.typography.titleLarge)
                Text(message, modifier = Modifier.padding(vertical = 16.dp))
                Button(onClick = page::retry) { Text("Try again") }
              }
            }
          }
          if (page.loading)
            LinearProgressIndicator(
              progress = { page.progress / 100f },
              modifier = Modifier.fillMaxWidth(),
            )
        }
        if (find) {
          OutlinedTextField(
            value = findText,
            onValueChange = {
              findText = it
              page.session.finder.find(it, 0)
            },
            label = { Text("Find in page") },
            singleLine = true,
            trailingIcon = {
              TextButton(onClick = { page.session.finder.find(findText, 0) }) { Text("Next") }
            },
            modifier = Modifier.fillMaxWidth().padding(8.dp),
          )
        }
        if (!page.fullscreen && showLauncherChrome && !websiteKeyboardVisible) {
          Column(
            Modifier.fillMaxWidth()
              .background(frameColor)
              .onSizeChanged { chromeSize = it }
              .drawWithContent {
                chromeLayer.record { this@drawWithContent.drawContent() }
                drawLayer(chromeLayer)
              }
          ) {
            if (showFavorites && !privateMode)
              GeckoFavoritesStrip(
                onOpenSearch = { query -> onOpenSearch(false, tab.frameColorArgb, query) },
                modifier = Modifier.geckoChromeSwipe(swipe),
              )
            SearchChromeBar(
              isIndexing = false,
              color = frameColor,
              contentColor = frameContentColor,
              tonalElevation = 0.dp,
              modifier = Modifier.padding(top = 8.dp, bottom = 4.dp).geckoChromeSwipe(swipe),
            ) {
              if (privateMode) {
                Icon(
                  Icons.Default.VisibilityOff,
                  "Private browsing",
                  modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
              }
              Box(
                modifier =
                  Modifier.weight(1f)
                    .heightIn(min = 32.dp)
                    .combinedClickable(
                      interactionSource =
                        remember {
                          androidx.compose.foundation.interaction.MutableInteractionSource()
                        },
                      indication = null,
                      onLongClickLabel = "Copy URL",
                      onLongClick = {
                        com.searchlauncher.app.util.SystemUtils.copyUrlToClipboard(
                          activity,
                          tab.url,
                        )
                      },
                      onClick = {
                        // Opening search must not wait up to 500 ms for a compositor screenshot.
                        page.capture()
                        onOpenSearch(false, tab.frameColorArgb, "")
                      },
                    ),
                contentAlignment = androidx.compose.ui.Alignment.CenterStart,
              ) {
                Text(
                  displayPageAddress(tab.url).ifBlank { "Search anything…" },
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                  style = MaterialTheme.typography.bodyLarge,
                )
              }
              IconButton(
                onClick = {
                  page.capture()
                  onOpenSearch(true, tab.frameColorArgb, "")
                },
                modifier = Modifier.size(36.dp),
              ) {
                Icon(Icons.Default.Mic, "Voice search")
              }
              if (!privateMode)
                BrowserTabsButton(
                  tabCount = BrowserTabStore.tabs?.items?.size ?: 1,
                  onClick = {
                    scope.launch {
                      page.captureBeforeTransition()
                      overview = true
                    }
                  },
                )
              Box {
                IconButton(onClick = { menu = true }, modifier = Modifier.size(36.dp)) {
                  Icon(Icons.Default.MoreVert, "Browser menu")
                }
                MaterialTheme(colorScheme = menuScheme) {
                  DropdownMenu(
                    expanded = menu,
                    onDismissRequest = { menu = false },
                    modifier = Modifier.heightIn(max = minOf(420.dp, screenHeight * 0.6f)),
                    containerColor = menuScheme.surface,
                    tonalElevation = 0.dp,
                  ) {
                    fun run(action: () -> Unit) {
                      menu = false
                      action()
                    }
                    DropdownMenuItem(
                      text = { Text("Back") },
                      leadingIcon = { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) },
                      colors = menuColors,
                      enabled = page.canGoBack,
                      onClick = { run { page.session.goBack() } },
                    )
                    DropdownMenuItem(
                      text = { Text("Forward") },
                      leadingIcon = { Icon(Icons.AutoMirrored.Filled.ArrowForward, null) },
                      colors = menuColors,
                      enabled = page.canGoForward,
                      onClick = { run { page.session.goForward() } },
                    )
                    DropdownMenuItem(
                      text = { Text("Reload") },
                      leadingIcon = { Icon(Icons.Default.Refresh, null) },
                      colors = menuColors,
                      onClick = { run { page.reload() } },
                    )
                    DropdownMenuItem(
                      text = { Text("New tab") },
                      leadingIcon = { Icon(Icons.Default.Add, null) },
                      colors = menuColors,
                      onClick = {
                        run {
                          if (privateMode) page.session.loadUri("about:blank")
                          else
                            activity.startActivity(
                              BrowserActivity.createIntent(activity, "about:blank")
                            )
                        }
                      },
                    )
                    DropdownMenuItem(
                      text = { Text("Close tab") },
                      leadingIcon = { Icon(Icons.Default.Close, null) },
                      colors = menuColors,
                      onClick = { run { activity.finishAndRemoveTask() } },
                    )
                    DropdownMenuItem(
                      text = { Text("Home") },
                      leadingIcon = { Icon(Icons.Default.Home, null) },
                      colors = menuColors,
                      onClick = { run { goHome() } },
                    )
                    DropdownMenuItem(
                      text = { Text("Downloads") },
                      leadingIcon = { Icon(Icons.Default.Download, null) },
                      colors = menuColors,
                      onClick = { run { page.showDownloads = true } },
                    )
                    DropdownMenuItem(
                      text = { Text("Find in page") },
                      leadingIcon = { Icon(Icons.Default.Search, null) },
                      colors = menuColors,
                      onClick = { run { find = !find } },
                    )
                    DropdownMenuItem(
                      text = { Text(if (tab.desktopMode) "Mobile site" else "Desktop site") },
                      leadingIcon = { Icon(Icons.Default.Computer, null) },
                      colors = menuColors,
                      onClick = {
                        run {
                          tab.desktopMode = !tab.desktopMode
                          page.session.settings.userAgentMode =
                            if (tab.desktopMode) GeckoSessionSettings.USER_AGENT_MODE_DESKTOP
                            else GeckoSessionSettings.USER_AGENT_MODE_MOBILE
                          page.session.settings.viewportMode =
                            if (tab.desktopMode) GeckoSessionSettings.VIEWPORT_MODE_DESKTOP
                            else GeckoSessionSettings.VIEWPORT_MODE_MOBILE
                          page.session.reload()
                        }
                      },
                    )
                    if (!privateMode) {
                      DropdownMenuItem(
                        text = { Text("Save bookmark") },
                        leadingIcon = { Icon(Icons.Default.BookmarkAdd, null) },
                        colors = menuColors,
                        onClick = {
                          run {
                            scope.launch {
                              val repository =
                                (activity.application as SearchLauncherApp).searchRepositoryOrNull
                              val saved = repository?.saveBookmark(tab.url, tab.title) == true
                              Toast.makeText(
                                  activity,
                                  if (saved) "Bookmark saved" else "Could not save bookmark",
                                  Toast.LENGTH_SHORT,
                                )
                                .show()
                            }
                          }
                        },
                      )
                      DropdownMenuItem(
                        text = { Text("Favorite website") },
                        leadingIcon = { Icon(Icons.Default.Star, null) },
                        colors = menuColors,
                        onClick = {
                          run {
                            scope.launch {
                              (activity.application as SearchLauncherApp)
                                .searchRepositoryOrNull
                                ?.saveAndFavoriteBookmark(tab.url, tab.title)
                            }
                          }
                        },
                      )
                    }
                    DropdownMenuItem(
                      text = { Text("Share") },
                      leadingIcon = { Icon(Icons.Default.Share, null) },
                      colors = menuColors,
                      onClick = {
                        run {
                          activity.startActivity(
                            Intent.createChooser(
                              Intent(Intent.ACTION_SEND)
                                .setType("text/plain")
                                .putExtra(Intent.EXTRA_TEXT, tab.url),
                              "Share page",
                            )
                          )
                        }
                      },
                    )
                    DropdownMenuItem(
                      text = { Text("Clear this site's data") },
                      leadingIcon = { Icon(Icons.Default.DeleteOutline, null) },
                      colors = menuColors,
                      onClick = {
                        run {
                          val host = Uri.parse(tab.url).host
                          if (host != null)
                            NativeAlertDialog.Builder(activity)
                              .setTitle("Clear data for $host?")
                              .setMessage(
                                "This signs you out and removes this site's stored data and permissions."
                              )
                              .setNegativeButton("Cancel", null)
                              .setPositiveButton("Clear") { _, _ ->
                                GeckoEnvironment.runtime(activity)
                                  .storageController
                                  .clearDataFromHost(host, StorageController.ClearFlags.ALL)
                                  .accept(
                                    { page.session.reload() },
                                    {
                                      Toast.makeText(
                                          activity,
                                          "Could not clear site data",
                                          Toast.LENGTH_LONG,
                                        )
                                        .show()
                                    },
                                  )
                              }
                              .show()
                        }
                      },
                    )
                    if (!privateMode)
                      DropdownMenuItem(
                        text = { Text(if (showFavorites) "Hide favorites" else "Show favorites") },
                        leadingIcon = { Icon(Icons.Default.StarOutline, null) },
                        colors = menuColors,
                        onClick = {
                          run {
                            scope.launch {
                              activity.dataStore.edit {
                                it[PreferencesKeys.BROWSER_SHOW_FAVORITES] = !showFavorites
                              }
                            }
                          }
                        },
                      )
                    DropdownMenuItem(
                      text = { Text("About this experiment") },
                      leadingIcon = { Icon(Icons.Default.Info, null) },
                      colors = menuColors,
                      onClick = { run { showInfo = true } },
                    )
                  }
                }
              }
            }
          }
        }
      }
      if (overview && !privateMode) {
        val tabs = BrowserTabStore.tabs
        if (tabs != null)
          BrowserTabsOverviewLayer(
            tabs = tabs.items,
            activeIndex = tabs.indexOfFirst(tab.id),
            progress = { 1f },
            scrimColor = frameColor,
            contentColor = frameContentColor,
            cardWidth = screenWidth * TAB_CARD_WIDTH_FRACTION,
            previewAspectRatio = 0.55f,
            maxPreviewHeight = screenHeight * 0.7f,
            bottomInset = 80.dp,
            expandTarget =
              with(density) { Rect(0f, 0f, screenWidth.toPx(), (screenHeight - 80.dp).toPx()) },
            onDismiss = { overview = false },
            onSelect = { index ->
              tabs.items.getOrNull(index)?.let { BrowserTabTasks.open(activity, it.id) }
              overview = false
            },
            onCloseTab = { index ->
              tabs.items.getOrNull(index)?.let {
                BrowserTabStore.close(it.id)
                BrowserTabTasks.close(activity, it.id)
                if (it.id == tab.id) onClose()
              }
            },
            onCloseAll = {
              BrowserTabTasks.closeAll(activity)
              BrowserTabStore.clear()
              onClose()
            },
          )
      }
      AnimatedVisibility(visible = page.showDownloads, enter = fadeIn(), exit = fadeOut()) {
        BrowserDownloadsScreen(onDismiss = page::dismissDownloads)
      }
      if (showInfo)
        AlertDialog(
          onDismissRequest = { showInfo = false },
          title = { Text("SearchLauncher Gecko") },
          text = {
            Text(
              "Experimental Firefox engine (GeckoView 157). Separate app and website data.\n\nLocal website notifications are supported while Gecko is running. Remote push delivery is not configured.\n\nPasskey providers, background media, full gesture parity, and storage size reporting still need validation or integration."
            )
          },
          confirmButton = { TextButton(onClick = { showInfo = false }) { Text("Done") } },
        )
    }
  }
}
