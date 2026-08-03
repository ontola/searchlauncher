package com.searchlauncher.app.ui.browser

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.lifecycleScope
import com.searchlauncher.app.SearchLauncherApp
import com.searchlauncher.app.data.FavoritesRepository
import com.searchlauncher.app.data.SearchResult
import com.searchlauncher.app.ui.MinIconSize
import com.searchlauncher.app.ui.PreferencesKeys
import com.searchlauncher.app.ui.SearchActivity
import com.searchlauncher.app.ui.components.BookmarkDialog
import com.searchlauncher.app.ui.components.FavoritesRow
import com.searchlauncher.app.ui.components.SearchChromeBar
import com.searchlauncher.app.ui.dataStore
import com.searchlauncher.app.ui.theme.SearchLauncherTheme
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

open class BrowserActivity : ComponentActivity() {
  private var navigationRequest by mutableStateOf<NavigationRequest?>(null)
  private var searchOverlayVisible by mutableStateOf(false)
  private var browserMenuRequest by mutableLongStateOf(0L)
  protected open val isPrivateMode: Boolean = false

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    navigationRequest = intent.toNavigationRequest(0)
    ContextCompat.registerReceiver(
      this,
      browserMenuReceiver,
      IntentFilter(ACTION_SHOW_BROWSER_MENU),
      ContextCompat.RECEIVER_NOT_EXPORTED,
    )
    enableEdgeToEdge()

    setContent {
      val themeColor by preference(PreferencesKeys.THEME_COLOR, 0xFF5E6D4E.toInt())
      val themeSaturation by preference(PreferencesKeys.THEME_SATURATION, 50f)
      val darkMode by preference(PreferencesKeys.DARK_MODE, 0)
      val isOled by preference(PreferencesKeys.OLED_MODE, false)

      SearchLauncherTheme(themeColor, darkMode, themeSaturation, isOled) {
        BrowserScreen(
          navigationRequest = navigationRequest,
          privateMode = isPrivateMode,
          showLauncherChrome = !searchOverlayVisible,
          browserMenuRequest = browserMenuRequest,
          onBrowserMenuShown = { browserMenuRequest = 0L },
          onOpenSearch = ::openSearch,
          onClose = ::finish,
        )
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    navigationRequest = intent.toNavigationRequest(System.nanoTime())
  }

  override fun onResume() {
    super.onResume()
    searchOverlayVisible = false
  }

  override fun onDestroy() {
    unregisterReceiver(browserMenuReceiver)
    super.onDestroy()
  }

  @Composable
  private fun <T> preference(key: Preferences.Key<T>, default: T): State<T> =
    remember(key) { dataStore.data.map { it[key] ?: default } }.collectAsState(initial = default)

  private fun openSearch(startVoiceSearch: Boolean, chromeColorArgb: Int) {
    searchOverlayVisible = true
    startActivity(
      Intent(this, SearchActivity::class.java)
        .putExtra(SearchActivity.EXTRA_PRIVATE_WEB_RESULTS, isPrivateMode)
        .putExtra(SearchActivity.EXTRA_START_VOICE_SEARCH, startVoiceSearch)
        .putExtra(SearchActivity.EXTRA_BROWSER_SEARCH, true)
        .putExtra(SearchActivity.EXTRA_CHROME_COLOR, chromeColorArgb)
    )
  }

  private fun Intent.toNavigationRequest(sequence: Long): NavigationRequest? {
    val url = dataString ?: getStringExtra(EXTRA_URL) ?: return null
    return NavigationRequest(browserDestination(url), sequence)
  }

  companion object {
    const val ACTION_SHOW_BROWSER_MENU = "com.searchlauncher.app.action.SHOW_BROWSER_MENU"
    private const val EXTRA_URL = "browser_url"

    fun createIntent(context: Context, url: String): Intent =
      Intent(context, BrowserActivity::class.java).apply {
        data = Uri.parse(browserDestination(url))
        putExtra(EXTRA_URL, url)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }

    fun createPrivateIntent(context: Context, url: String): Intent =
      Intent(context, PrivateBrowserActivity::class.java).apply {
        data = Uri.parse(browserDestination(url))
        putExtra(EXTRA_URL, url)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
  }

  private val browserMenuReceiver =
    object : BroadcastReceiver() {
      override fun onReceive(context: Context?, intent: Intent?) {
        browserMenuRequest = System.nanoTime()
      }
    }
}

private data class NavigationRequest(val url: String, val sequence: Long)

/** Target of a long-press on web content: a link, an image, or a link wrapping an image. */
private data class LinkMenuTarget(val linkUrl: String?, val imageUrl: String?)

/** A bookmark awaiting title confirmation in [BookmarkDialog]. */
private data class BookmarkDraft(val url: String, val title: String)

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun BrowserScreen(
  navigationRequest: NavigationRequest?,
  privateMode: Boolean,
  showLauncherChrome: Boolean,
  browserMenuRequest: Long,
  onBrowserMenuShown: () -> Unit,
  onOpenSearch: (Boolean, Int) -> Unit,
  onClose: () -> Unit,
) {
  val context = LocalContext.current
  val searchRepository =
    if (privateMode) null else (context.applicationContext as SearchLauncherApp).searchRepository
  val favoritesRepository = rememberFavoritesRepository(privateMode)
  val favoriteIds by favoritesRepository.collectAsState()
  val favoriteApps by favoriteApps(favoriteIds)
  val rawHistoryItems =
    if (searchRepository != null) searchRepository.recentItems.collectAsState().value
    else emptyList()
  val historyLimit by
    remember { context.dataStore.data.map { it[PreferencesKeys.HISTORY_LIMIT] ?: -1 } }
      .collectAsState(initial = -1)
  val minIconSizeSetting by
    remember { MinIconSize.flow(context) }.collectAsState(initial = MinIconSize.cached(context))
  val recentApps =
    remember(rawHistoryItems, favoriteIds, historyLimit) {
      if (historyLimit == 0) emptyList()
      else {
        val apps =
          rawHistoryItems.filterIsInstance<SearchResult.App>().filterNot { it.id in favoriteIds }
        if (historyLimit >= 0) apps.take(historyLimit) else apps
      }
    }
  // Hidden by default while browsing: the page is the focus, and the row is a lot of UI.
  val showFavorites by
    remember {
        context.dataStore.data.map { preferences ->
          preferences[PreferencesKeys.BROWSER_SHOW_FAVORITES] ?: false
        }
      }
      .collectAsState(initial = false)
  val adBlockEnabled by
    remember {
        context.dataStore.data.map { preferences ->
          preferences[PreferencesKeys.AD_BLOCK_ENABLED] ?: true
        }
      }
      .collectAsState(initial = true)
  val coroutineScope = rememberCoroutineScope()
  val initialNavigationRequest = remember { navigationRequest }
  val defaultPageBackground = MaterialTheme.colorScheme.background
  val tabs = remember {
    BrowserTabs(initialNavigationRequest?.url ?: "about:blank").also {
      // Seed the first tab with the theme background so opening the app doesn't tween the
      // bottom section from the BrowserTab default (black) to the real color.
      it.active.pageBackgroundArgb = defaultPageBackground.toArgb()
    }
  }
  var handledNavigationSequence by remember {
    mutableLongStateOf(initialNavigationRequest?.sequence ?: Long.MIN_VALUE)
  }
  val activeTab = tabs.active
  var webView by remember { mutableStateOf<WebView?>(null) }
  var progress by remember { mutableIntStateOf(0) }
  var chromeHeightPx by remember { mutableIntStateOf(0) }
  var chromeHiddenByUser by rememberSaveable { mutableStateOf(false) }
  var phoneUserAgent by remember { mutableStateOf<String?>(null) }
  var showFindInPage by rememberSaveable { mutableStateOf(false) }
  var findBarHeightPx by remember { mutableIntStateOf(0) }
  var findQuery by rememberSaveable { mutableStateOf("") }
  var activeFindMatch by remember { mutableIntStateOf(0) }
  var findMatchCount by remember { mutableIntStateOf(0) }
  var showPageSettings by rememberSaveable { mutableStateOf(false) }
  var linkMenuTarget by remember { mutableStateOf<LinkMenuTarget?>(null) }
  var bookmarkDraft by remember { mutableStateOf<BookmarkDraft?>(null) }
  val siteSettingsStore = remember(privateMode) { BrowserSiteSettingsStore(context, privateMode) }
  var siteSettings by remember { mutableStateOf(siteSettingsStore.load(activeTab.url)) }
  var pageBackground by remember { mutableStateOf(Color(activeTab.pageBackgroundArgb)) }
  var fullscreenVideoView by remember { mutableStateOf<View?>(null) }
  var fullscreenVideoCallback by remember {
    mutableStateOf<WebChromeClient.CustomViewCallback?>(null)
  }
  var restoringSnapshot by remember { mutableStateOf(activeTab.snapshot) }
  var tabDragOffsetPx by remember { mutableFloatStateOf(0f) }
  // Set when the outgoing tab's state and snapshot were already captured at drag start, so the
  // commit path at the end of the swipe stays free of bitmap work.
  var dragTabStateSaved by remember { mutableStateOf(false) }
  // True from the start of a horizontal swipe until its settle animation completes. While set,
  // tab switches render pure snapshots and no WebView is built, so swipes can chain without
  // waiting on WebView construction.
  var tabsInMotion by remember { mutableStateOf(false) }
  var fingerOnTabDrag by remember { mutableStateOf(false) }
  var settleJob by remember { mutableStateOf<Job?>(null) }
  // A restored tab reloads its page, which would re-sample the background pre-CSS (usually
  // white) and make the persisted per-tab color flicker. Skip the early sample for restores;
  // onPageFinished still confirms the final color.
  var suppressCommitVisibleColor by remember { mutableStateOf(false) }
  var viewportWidthPx by remember { mutableIntStateOf(1) }
  // During tab motion the color snaps almost instantly — each tab keeps its own persisted color
  // and the slide itself is the transition. The slow tween is for in-page color changes only.
  val animatedPageBackground by
    animateColorAsState(
      pageBackground,
      tween(durationMillis = if (tabsInMotion) 80 else 450),
      label = "pageBackground",
    )
  val density = LocalDensity.current
  val webContentBottomInset =
    with(density) {
      when {
        showFindInPage -> findBarHeightPx.toDp()
        // When the user swiped the chrome away only the small reveal caret remains, floating
        // over full-bleed web content, so no inset is reserved.
        showLauncherChrome && !chromeHiddenByUser -> chromeHeightPx.toDp()
        else -> 0.dp
      }
    }

  fun exitFullscreenVideo() {
    fullscreenVideoCallback?.onCustomViewHidden()
    fullscreenVideoView = null
    fullscreenVideoCallback = null
  }

  fun activateTab(index: Int) {
    if (index !in tabs.items.indices || index == tabs.activeIndex) return
    exitFullscreenVideo()
    if (!dragTabStateSaved) webView?.let { saveWebViewIntoTab(it, tabs.active) }
    dragTabStateSaved = false
    val target = tabs.activate(index) ?: return
    webView = null
    progress = 0
    showFindInPage = false
    pageBackground = Color(target.pageBackgroundArgb)
    siteSettings = siteSettingsStore.load(target.url)
    restoringSnapshot = target.snapshot
  }

  fun createTab() {
    exitFullscreenVideo()
    settleJob?.cancel()
    tabsInMotion = true
    webView?.let { saveWebViewIntoTab(it, tabs.active) }
    tabs.add("about:blank").pageBackgroundArgb = defaultPageBackground.toArgb()
    webView = null
    progress = 0
    pageBackground = defaultPageBackground
    restoringSnapshot = null
    siteSettings = BrowserSiteSettings()
    // Slide the blank tab in from the right like a next-tab swipe, then open search for it.
    tabDragOffsetPx = viewportWidthPx.toFloat()
    settleJob =
      coroutineScope.launch {
        animate(
          initialValue = tabDragOffsetPx,
          targetValue = 0f,
          animationSpec =
            spring(
              dampingRatio = Spring.DampingRatioNoBouncy,
              stiffness = Spring.StiffnessMediumLow,
            ),
        ) { value, _ ->
          tabDragOffsetPx = value
        }
        tabsInMotion = false
        onOpenSearch(false, defaultPageBackground.toArgb())
      }
  }

  fun closeActiveTab() {
    if (tabs.items.size == 1) {
      onClose()
      return
    }
    exitFullscreenVideo()
    val target = tabs.closeActive() ?: return
    webView = null
    progress = 0
    pageBackground = Color(target.pageBackgroundArgb)
    siteSettings = siteSettingsStore.load(target.url)
    restoringSnapshot = target.snapshot
  }

  // Plays the same slide animation as a horizontal swipe, so menu-triggered tab switches teach
  // the gesture. A slower spring than the swipe settle keeps the motion legible.
  fun openLinkInNewTab(url: String) {
    exitFullscreenVideo()
    webView?.let { saveWebViewIntoTab(it, tabs.active) }
    val newTab = tabs.add(url)
    newTab.pageBackgroundArgb = defaultPageBackground.toArgb()
    webView = null
    progress = 0
    pageBackground = Color(newTab.pageBackgroundArgb)
    siteSettings = siteSettingsStore.load(newTab.url)
    restoringSnapshot = newTab.snapshot
  }

  fun animateToAdjacentTab(direction: Int) {
    if (tabs.adjacent(direction) == null) return
    settleJob?.cancel()
    tabsInMotion = true
    webView?.let { saveWebViewIntoTab(it, tabs.active) }
    dragTabStateSaved = true
    activateTab(tabs.activeIndex + direction)
    tabDragOffsetPx = direction * viewportWidthPx.toFloat()
    settleJob =
      coroutineScope.launch {
        animate(
          initialValue = tabDragOffsetPx,
          targetValue = 0f,
          animationSpec =
            spring(
              dampingRatio = Spring.DampingRatioNoBouncy,
              stiffness = Spring.StiffnessMediumLow,
            ),
        ) { value, _ ->
          tabDragOffsetPx = value
        }
        tabsInMotion = false
      }
  }

  LaunchedEffect(navigationRequest?.sequence) {
    val request = navigationRequest ?: return@LaunchedEffect
    if (request.sequence == handledNavigationSequence) return@LaunchedEffect
    handledNavigationSequence = request.sequence
    if (tabs.active.url == "about:blank" && tabs.active.webViewState == null) {
      tabs.active.url = request.url
      siteSettings = siteSettingsStore.load(request.url)
      webView?.loadUrl(request.url)
    } else {
      openLinkInNewTab(request.url)
    }
  }

  LaunchedEffect(activeTab.id) {
    pageBackground = Color(activeTab.pageBackgroundArgb)
    siteSettings = siteSettingsStore.load(activeTab.url)
    restoringSnapshot = activeTab.snapshot
  }

  // Loads the cached filter list (downloading it when missing or stale). Pages opened before this
  // finishes simply aren't filtered; nothing blocks on it.
  LaunchedEffect(adBlockEnabled) { if (adBlockEnabled) AdBlocker.ensureLoaded(context) }

  LaunchedEffect(animatedPageBackground) {
    (context as BrowserActivity).let { activity ->
      val isLightBackground = animatedPageBackground.luminance() > 0.5f
      activity.window.navigationBarColor = animatedPageBackground.toArgb()
      activity.window.isNavigationBarContrastEnforced = false
      WindowCompat.getInsetsController(activity.window, activity.window.decorView).apply {
        isAppearanceLightStatusBars = isLightBackground
        isAppearanceLightNavigationBars = isLightBackground
      }
    }
  }

  LaunchedEffect(fullscreenVideoView != null) {
    val window = (context as BrowserActivity).window
    val insetsController = WindowCompat.getInsetsController(window, window.decorView)
    if (fullscreenVideoView != null) {
      insetsController.systemBarsBehavior =
        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
      insetsController.hide(WindowInsetsCompat.Type.systemBars())
    } else {
      insetsController.show(WindowInsetsCompat.Type.systemBars())
    }
  }

  BackHandler {
    if (fullscreenVideoView != null) {
      exitFullscreenVideo()
    } else if (showFindInPage) {
      webView?.clearMatches()
      showFindInPage = false
      findQuery = ""
      activeFindMatch = 0
      findMatchCount = 0
    } else {
      val view = webView
      if (view?.canGoBack() == true) view.goBack()
      else if (tabs.items.size > 1) closeActiveTab() else onClose()
    }
  }

  val chromeBarColor = animatedPageBackground
  val chromeBarContentColor =
    if (chromeBarColor.luminance() > 0.5f) Color(0xFF1C1B1F) else Color(0xFFEDE8EE)

  // Single overflow-menu definition shared by the full chrome bar and the minimal pill, so both
  // stay wired identically (including the open-on-broadcast request from the search overlay).
  val browserOverflowMenu: @Composable () -> Unit = {
    BrowserOverflowButton(
      desktopMode = activeTab.desktopMode,
      showFavorites = showFavorites,
      tabCount = tabs.items.size,
      hasPreviousTab = tabs.activeIndex > 0,
      hasNextTab = tabs.activeIndex < tabs.items.lastIndex,
      menuColor = chromeBarColor,
      menuContentColor = chromeBarContentColor,
      openRequest = browserMenuRequest,
      onOpenRequestConsumed = onBrowserMenuShown,
      onReload = { webView?.reload() },
      onShare = { shareUrl(context, webView?.url ?: activeTab.url, webView?.title) },
      onCopyUrl = { copyUrl(context, webView?.url ?: activeTab.url) },
      onSaveBookmark =
        searchRepository?.let {
          {
            val url = (webView?.url ?: activeTab.url).takeUnless { it.isBlank() }
            if (url == null || url == "about:blank") {
              Toast.makeText(context, "Nothing to bookmark", Toast.LENGTH_SHORT).show()
            } else {
              // Confirm the title first; saving happens when the dialog is accepted.
              bookmarkDraft =
                BookmarkDraft(url = url, title = webView?.title ?: activeTab.title ?: "")
            }
          }
        },
      onToggleDesktopMode = {
        webView?.let { view ->
          activeTab.desktopMode = !activeTab.desktopMode
          view.setDesktopMode(activeTab.desktopMode, phoneUserAgent)
          view.reload()
        }
      },
      onOpenDownloads = { openDownloads(context) },
      onFindInPage = { showFindInPage = true },
      onPageSettings = { showPageSettings = true },
      onToggleFavorites = {
        coroutineScope.launch {
          context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.BROWSER_SHOW_FAVORITES] = !showFavorites
          }
        }
      },
      onNewTab = ::createTab,
      onCloseTab = ::closeActiveTab,
      onPreviousTab = { animateToAdjacentTab(-1) },
      onNextTab = { animateToAdjacentTab(1) },
    )
  }

  // Tab-swipe handlers shared by the full chrome and the minimal pill.
  val tabDragStart = {
    settleJob?.cancel()
    tabsInMotion = true
    fingerOnTabDrag = true
    webView?.let { saveWebViewIntoTab(it, tabs.active) }
    dragTabStateSaved = true
  }
  val tabDrag = { delta: Float ->
    val proposed = tabDragOffsetPx + delta
    val direction = if (proposed < 0f) 1 else -1
    tabDragOffsetPx += if (tabs.adjacent(direction) != null) delta else delta * 0.16f
  }
  val tabDragEnd = {
    fingerOnTabDrag = false
    val startOffset = tabDragOffsetPx
    val direction = if (startOffset < 0f) 1 else -1
    val commit = tabs.adjacent(direction) != null && abs(startOffset) >= viewportWidthPx * 0.18f
    if (commit) {
      // Switch the model immediately and rebase the offset so the new active tab keeps its
      // current on-screen position; the next swipe can start right away instead of waiting
      // for the settle animation.
      activateTab(tabs.activeIndex + direction)
      tabDragOffsetPx = startOffset + direction * viewportWidthPx
    } else {
      dragTabStateSaved = false
    }
    settleJob =
      coroutineScope.launch {
        animate(
          initialValue = tabDragOffsetPx,
          targetValue = 0f,
          animationSpec =
            spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
        ) { value, _ ->
          tabDragOffsetPx = value
        }
        tabsInMotion = false
      }
  }

  val adjacentDirection =
    when {
      tabDragOffsetPx < 0f -> 1
      tabDragOffsetPx > 0f -> -1
      else -> 0
    }
  val adjacentTab = if (adjacentDirection == 0) null else tabs.adjacent(adjacentDirection)
  // The chrome bar is the same on every tab, so instead of following the post-commit rebased
  // offset (which would jump it across the screen) it wraps around: slides out one side and
  // back in from the other.
  val chromeDragOffsetPx =
    when {
      tabDragOffsetPx > viewportWidthPx / 2f -> tabDragOffsetPx - viewportWidthPx
      tabDragOffsetPx < -viewportWidthPx / 2f -> tabDragOffsetPx + viewportWidthPx
      else -> tabDragOffsetPx
    }

  Box(
    modifier =
      Modifier.fillMaxSize().background(animatedPageBackground).onSizeChanged {
        viewportWidthPx = it.width
      }
  ) {
    if (adjacentTab != null && abs(tabDragOffsetPx) > 0.5f) {
      Box(
        modifier =
          Modifier.fillMaxSize()
            .statusBarsPadding()
            .padding(bottom = webContentBottomInset)
            .background(Color(adjacentTab.pageBackgroundArgb))
            .graphicsLayer { translationX = tabDragOffsetPx + adjacentDirection * viewportWidthPx }
      ) {
        adjacentTab.snapshot?.takeUnless(Bitmap::isRecycled)?.let { snapshot ->
          Image(
            bitmap = snapshot.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.FillBounds,
          )
        }
      }
    }

    key(activeTab.id) {
      // While tabs are being flung through, only snapshots are rendered; the WebView for the
      // landing tab is built once the motion settles, so chained swipes never wait on it.
      if (webView != null || !tabsInMotion)
        AndroidView(
          modifier =
            Modifier.fillMaxSize()
              .statusBarsPadding()
              .padding(bottom = webContentBottomInset)
              .graphicsLayer { translationX = tabDragOffsetPx },
          factory = { viewContext ->
            WebView(viewContext).apply {
              layoutParams =
                ViewGroup.LayoutParams(
                  ViewGroup.LayoutParams.MATCH_PARENT,
                  ViewGroup.LayoutParams.MATCH_PARENT,
                )
              settings.javaScriptEnabled = true
              settings.domStorageEnabled = true
              settings.setSupportZoom(true)
              settings.builtInZoomControls = true
              settings.displayZoomControls = false
              settings.javaScriptCanOpenWindowsAutomatically = false
              settings.setSupportMultipleWindows(false)
              if (privateMode) settings.cacheMode = WebSettings.LOAD_NO_CACHE
              phoneUserAgent = settings.userAgentString
              applySiteSettings(siteSettings)
              setFindListener { activeMatchOrdinal, numberOfMatches, _ ->
                activeFindMatch = activeMatchOrdinal
                findMatchCount = numberOfMatches
              }
              setOnLongClickListener {
                val hit = hitTestResult
                when (hit.type) {
                  WebView.HitTestResult.SRC_ANCHOR_TYPE -> {
                    val url = hit.extra
                    if (url.isNullOrBlank()) {
                      false
                    } else {
                      linkMenuTarget = LinkMenuTarget(linkUrl = url, imageUrl = null)
                      true
                    }
                  }
                  WebView.HitTestResult.IMAGE_TYPE -> {
                    val url = hit.extra
                    if (url.isNullOrBlank()) {
                      false
                    } else {
                      linkMenuTarget = LinkMenuTarget(linkUrl = null, imageUrl = url)
                      true
                    }
                  }
                  WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE -> {
                    // The hit only carries the image; the wrapping link arrives via a message.
                    val imageUrl = hit.extra
                    val handler =
                      android.os.Handler(android.os.Looper.getMainLooper()) { message ->
                        val href = message.data.getString("url")?.takeIf { it.isNotBlank() }
                        if (href != null || !imageUrl.isNullOrBlank()) {
                          linkMenuTarget = LinkMenuTarget(linkUrl = href, imageUrl = imageUrl)
                        }
                        true
                      }
                    requestFocusNodeHref(handler.obtainMessage())
                    true
                  }
                  else -> false
                }
              }

              webChromeClient =
                object : WebChromeClient() {
                  override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    progress = newProgress
                  }

                  override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                    if (view == null) return
                    if (fullscreenVideoView != null) {
                      callback?.onCustomViewHidden()
                      return
                    }
                    fullscreenVideoView = view
                    fullscreenVideoCallback = callback
                  }

                  override fun onHideCustomView() {
                    fullscreenVideoView = null
                    fullscreenVideoCallback = null
                  }

                  // searchRepository is null in private mode, so incognito browsing never writes
                  // favicons to disk.
                  override fun onReceivedIcon(view: WebView?, icon: Bitmap?) {
                    val pageUrl = view?.url ?: return
                    val pageIcon = icon ?: return
                    val repository = searchRepository ?: return
                    (context as BrowserActivity).lifecycleScope.launch {
                      repository.saveFavicon(pageUrl, pageIcon)
                    }
                  }
                }
              webViewClient =
                object : WebViewClient() {
                  // The previous page's color is kept while loading; updating only once the new
                  // page's background is known avoids flashing through the default color. The
                  // WebView's own canvas is kept in sync so pages without a painted background
                  // (blank tabs, load gaps) show the section color instead of WebView's white.
                  private fun applyPageBackground(view: WebView, argb: Int) {
                    view.setBackgroundColor(argb)
                    activeTab.pageBackgroundArgb = argb
                    pageBackground = Color(argb)
                  }

                  private fun refreshPageBackground(view: WebView, allowWhiteFallback: Boolean) {
                    view.evaluateJavascript(PAGE_BACKGROUND_SCRIPT) { result ->
                      val parsed = parseCssColor(result)
                      if (parsed != null) {
                        applyPageBackground(view, parsed)
                      } else if (allowWhiteFallback && view.url != "about:blank") {
                        // A fully loaded page with a transparent background is unstyled and
                        // assumes a white canvas (default black text).
                        applyPageBackground(view, 0xFFFFFFFF.toInt())
                      }
                    }
                  }

                  override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                    activeTab.url = url
                    activeTab.blockedRequestCount.set(0)
                    siteSettings = siteSettingsStore.load(url)
                    view.applySiteSettings(siteSettings)
                  }

                  // Runs on a WebView worker thread for every subresource, so it must stay cheap:
                  // AdBlocker.shouldBlock is a handful of binary searches over a long array.
                  override fun shouldInterceptRequest(
                    view: WebView,
                    request: WebResourceRequest,
                  ): WebResourceResponse? {
                    // Never block top-level navigation: the user asked for that page explicitly,
                    // and blocking it would make links appear broken rather than ad-free.
                    if (request.isForMainFrame) return null
                    if (!adBlockEnabled || !siteSettings.adBlockEnabled) return null
                    if (!AdBlocker.shouldBlock(request.url.toString())) return null
                    activeTab.blockedRequestCount.incrementAndGet()
                    return AdBlocker.blockedResponse()
                  }

                  override fun onPageCommitVisible(view: WebView, url: String) {
                    if (suppressCommitVisibleColor) {
                      suppressCommitVisibleColor = false
                    } else {
                      refreshPageBackground(view, allowWhiteFallback = false)
                    }
                    // Apply as soon as the document is usable so responsive CSS doesn't briefly
                    // lay out at the phone width before onPageFinished.
                    view.applyDesktopViewport(activeTab.desktopMode)
                  }

                  override fun onPageFinished(view: WebView, url: String) {
                    refreshPageBackground(view, allowWhiteFallback = true)
                    activeTab.url = url
                    activeTab.title = view.title
                    // Sites (and late-injected tags) can rewrite the viewport during load; re-assert
                    // the desktop width after the document is complete.
                    view.applyDesktopViewport(activeTab.desktopMode)
                    // Keep the snapshot up until the fully loaded page (CSS and fonts included)
                    // has actually been drawn, so tab switches never flash a half-styled page.
                    view.postVisualStateCallback(
                      0,
                      object : WebView.VisualStateCallback() {
                        override fun onComplete(requestId: Long) {
                          restoringSnapshot = null
                        }
                      },
                    )
                    if (
                      searchRepository != null &&
                        (url.startsWith("https://") || url.startsWith("http://"))
                    ) {
                      (context as BrowserActivity).lifecycleScope.launch {
                        searchRepository.indexWebUrl(url, view.title)
                      }
                    }
                  }

                  override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest,
                  ): Boolean {
                    if (openOutsideWebView(context, request.url)) return true
                    siteSettings = siteSettingsStore.load(request.url.toString())
                    view.applySiteSettings(siteSettings)
                    return false
                  }
                }
              // Paint the WebView canvas in the tab's color right away — WebView defaults to
              // white, which flashed on blank tabs and dark pages (worst in dark mode).
              setBackgroundColor(activeTab.pageBackgroundArgb)
              webView = this
              val restored = activeTab.webViewState?.let { restoreState(it) } != null
              suppressCommitVisibleColor = restored
              setDesktopMode(activeTab.desktopMode, phoneUserAgent)
              if (!restored) loadUrl(activeTab.url)
              // Safety net: if the restore never reaches a drawn onPageFinished (hung renderer,
              // stuck load), don't leave the stale snapshot covering the page forever.
              else postDelayed({ restoringSnapshot = null }, 5000)
            }
          },
          onRelease = { releasedWebView ->
            if (webView === releasedWebView) webView = null
            releasedWebView.destroy()
          },
        )
    }

    AnimatedVisibility(
      visible = restoringSnapshot?.isRecycled == false,
      modifier =
        Modifier.fillMaxSize()
          .statusBarsPadding()
          .padding(bottom = webContentBottomInset)
          .graphicsLayer { translationX = tabDragOffsetPx },
      enter = if (tabsInMotion) EnterTransition.None else fadeIn(),
      exit = fadeOut(),
    ) {
      restoringSnapshot?.takeUnless(Bitmap::isRecycled)?.let { snapshot ->
        Image(
          bitmap = snapshot.asImageBitmap(),
          contentDescription = null,
          modifier = Modifier.fillMaxSize(),
          contentScale = ContentScale.FillBounds,
        )
      }
    }

    if (progress in 1..99) {
      LinearProgressIndicator(
        progress = { progress / 100f },
        modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter).statusBarsPadding(),
      )
    }

    // The outer condition adds/removes the chrome instantly (e.g. around the search overlay) so
    // the bar never plays its own entrance; only the user's hide/reveal gesture animates.
    if (showLauncherChrome && !showFindInPage)
      AnimatedVisibility(
        visible = !chromeHiddenByUser,
        modifier = Modifier.align(Alignment.BottomCenter),
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
      ) {
        BrowserLauncherChrome(
          favoriteApps = favoriteApps,
          recentApps = recentApps,
          historyLimit = historyLimit,
          minIconSizeSetting = minIconSizeSetting,
          showFavorites = showFavorites,
          barColor = chromeBarColor,
          barContentColor = chromeBarContentColor,
          overflowMenu = browserOverflowMenu,
          onOpenSearch = { onOpenSearch(false, pageBackground.toArgb()) },
          onVoiceSearch = { onOpenSearch(true, pageBackground.toArgb()) },
          onTabDragStart = tabDragStart,
          onTabDrag = tabDrag,
          onTabDragEnd = tabDragEnd,
          onLaunchFavorite = { result ->
            context.packageManager.getLaunchIntentForPackage(result.packageName)?.let { intent ->
              intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
              context.startActivity(intent)
            }
          },
          onToggleFavorite = { result ->
            if (!privateMode) {
              (context.applicationContext as SearchLauncherApp)
                .favoritesRepository
                .toggleFavorite(result.id)
            }
          },
          onReorder = { ids ->
            if (!privateMode) {
              (context.applicationContext as SearchLauncherApp).favoritesRepository.updateOrder(ids)
            }
          },
          onHistoryCapacityChanged = { limit ->
            searchRepository?.updateObservedHistoryLimit(limit)
          },
          onHide = { chromeHiddenByUser = true },
          modifier =
            Modifier.onSizeChanged { chromeHeightPx = it.height }
              .graphicsLayer { translationX = chromeDragOffsetPx },
        )
      }

    AnimatedVisibility(
      visible = abs(tabDragOffsetPx) > 4f && adjacentTab != null,
      modifier =
        Modifier.align(Alignment.BottomCenter)
          .padding(bottom = with(density) { chromeHeightPx.toDp() } + 8.dp),
      enter = fadeIn(),
      exit = fadeOut(),
    ) {
      Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
        tonalElevation = 4.dp,
      ) {
        Text(
          text =
            "${tabs.activeIndex + (if (fingerOnTabDrag) adjacentDirection else 0) + 1} / ${tabs.items.size}",
          modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
          style = MaterialTheme.typography.labelMedium,
        )
      }
    }

    if (showLauncherChrome && !showFindInPage)
      AnimatedVisibility(
        visible = chromeHiddenByUser,
        // Right-aligned like the mic and menu icons in the full bar, so minimal mode keeps the
        // same corner of the screen for its controls.
        modifier = Modifier.align(Alignment.BottomEnd),
        enter = fadeIn(),
        exit = fadeOut(),
      ) {
        RevealBrowserChromeHandle(
          barColor = chromeBarColor,
          barContentColor = chromeBarContentColor,
          onReveal = { chromeHiddenByUser = false },
          onSearch = { onOpenSearch(false, pageBackground.toArgb()) },
          onVoiceSearch = { onOpenSearch(true, pageBackground.toArgb()) },
          onTabDragStart = tabDragStart,
          onTabDrag = tabDrag,
          onTabDragEnd = tabDragEnd,
          overflowMenu = browserOverflowMenu,
          modifier = Modifier.graphicsLayer { translationX = chromeDragOffsetPx },
        )
      }

    AnimatedVisibility(
      visible = showFindInPage,
      modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().imePadding(),
      enter = slideInVertically { it } + fadeIn(),
      exit = slideOutVertically { it } + fadeOut(),
    ) {
      FindInPageBar(
        query = findQuery,
        activeMatch = activeFindMatch,
        matchCount = findMatchCount,
        modifier = Modifier.onSizeChanged { findBarHeightPx = it.height },
        onQueryChange = { query ->
          findQuery = query
          if (query.isBlank()) {
            webView?.clearMatches()
            activeFindMatch = 0
            findMatchCount = 0
          } else {
            webView?.findAllAsync(query)
          }
        },
        onPrevious = { webView?.findNext(false) },
        onNext = { webView?.findNext(true) },
        onClose = {
          webView?.clearMatches()
          showFindInPage = false
          findQuery = ""
          activeFindMatch = 0
          findMatchCount = 0
        },
      )
    }

    fullscreenVideoView?.let { videoView ->
      key(videoView) {
        AndroidView(
          factory = { videoView },
          modifier = Modifier.fillMaxSize().background(Color.Black),
          onRelease = { released -> (released.parent as? ViewGroup)?.removeView(released) },
        )
      }
    }
  }

  linkMenuTarget?.let { target ->
    LinkContextMenuDialog(
      linkUrl = target.linkUrl,
      imageUrl = target.imageUrl,
      onOpenInNewTab = ::openLinkInNewTab,
      onOpenPrivate = { url ->
        context.startActivity(BrowserActivity.createPrivateIntent(context, url))
      },
      onCopyUrl = { url -> copyUrl(context, url) },
      onShareUrl = { url -> shareUrl(context, url, null) },
      onDownloadImage = { url -> downloadImage(context, url) },
      onDismiss = { linkMenuTarget = null },
    )
  }

  bookmarkDraft?.let { draft ->
    BookmarkDialog(
      initialTitle = draft.title,
      url = draft.url,
      isEditMode = false,
      onDismiss = { bookmarkDraft = null },
      onConfirm = { title ->
        bookmarkDraft = null
        coroutineScope.launch {
          val saved = searchRepository?.saveBookmark(draft.url, title) == true
          Toast.makeText(
              context,
              if (saved) "Bookmark saved" else "Could not save bookmark",
              Toast.LENGTH_SHORT,
            )
            .show()
        }
      },
    )
  }

  if (showPageSettings) {
    BrowserPageSettingsDialog(
      siteLabel = browserSiteLabel(webView?.url ?: activeTab.url),
      settings = siteSettings,
      blockedRequestCount = activeTab.blockedRequestCount.get(),
      onSettingsChange = { updatedSettings ->
        val url = webView?.url ?: activeTab.url
        siteSettings = updatedSettings
        siteSettingsStore.save(url, updatedSettings)
        webView?.applySiteSettings(updatedSettings)
        webView?.reload()
      },
      onClearSiteData = {
        val view = webView ?: return@BrowserPageSettingsDialog
        val url = view.url ?: activeTab.url
        siteSettingsStore.reset(url)
        siteSettings = BrowserSiteSettings()
        view.applySiteSettings(siteSettings)
        clearSiteData(view, url) {
          showPageSettings = false
          Toast.makeText(context, "Site data cleared", Toast.LENGTH_SHORT).show()
        }
      },
      onDismiss = { showPageSettings = false },
    )
  }

  DisposableEffect(Unit) {
    onDispose {
      webView?.apply {
        stopLoading()
        if (privateMode) {
          clearHistory()
          clearFormData()
          clearCache(true)
        }
      }
      webView = null
    }
  }
}

@Composable
private fun rememberFavoritesRepository(privateMode: Boolean): StateFlow<List<String>> {
  val context = LocalContext.current
  return remember(privateMode) {
    if (privateMode) FavoritesRepository(context).favoriteIds
    else (context.applicationContext as SearchLauncherApp).favoritesRepository.favoriteIds
  }
}

@Composable
private fun favoriteApps(favoriteIds: List<String>): State<List<SearchResult.App>> {
  val context = LocalContext.current
  return produceState(emptyList(), favoriteIds) {
    value =
      withContext(Dispatchers.IO) {
        favoriteIds.mapNotNull { packageName ->
          val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
          if (launchIntent == null) return@mapNotNull null
          val appInfo =
            runCatching { context.packageManager.getApplicationInfo(packageName, 0) }.getOrNull()
              ?: return@mapNotNull null
          SearchResult.App(
            id = packageName,
            title = context.packageManager.getApplicationLabel(appInfo).toString(),
            subtitle = null,
            icon = context.packageManager.getApplicationIcon(appInfo),
            packageName = packageName,
          )
        }
      }
  }
}

@Composable
private fun BrowserLauncherChrome(
  favoriteApps: List<SearchResult.App>,
  recentApps: List<SearchResult.App>,
  historyLimit: Int,
  minIconSizeSetting: Int,
  showFavorites: Boolean,
  barColor: Color,
  barContentColor: Color,
  overflowMenu: @Composable () -> Unit,
  onOpenSearch: () -> Unit,
  onVoiceSearch: () -> Unit,
  onTabDragStart: () -> Unit,
  onTabDrag: (Float) -> Unit,
  onTabDragEnd: () -> Unit,
  onLaunchFavorite: (SearchResult.App) -> Unit,
  onToggleFavorite: (SearchResult.App) -> Unit,
  onReorder: (List<String>) -> Unit,
  onHistoryCapacityChanged: (Int) -> Unit,
  onHide: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier =
      modifier
        .fillMaxWidth()
        // Tab-swipe and hide gestures cover the whole chrome section, favorites row included.
        // Favorites taps and long-press reordering consume their own events first, so they win.
        .pointerInput(onHide, onTabDragStart, onTabDrag, onTabDragEnd) {
          var downwardDrag = 0f
          var horizontalGesture: Boolean? = null
          detectDragGestures(
            onDragStart = { downwardDrag = 0f },
            onDragEnd = {
              if (horizontalGesture == true) onTabDragEnd()
              else if (downwardDrag >= 24.dp.toPx()) onHide()
              downwardDrag = 0f
              horizontalGesture = null
            },
            onDragCancel = {
              if (horizontalGesture == true) onTabDragEnd()
              downwardDrag = 0f
              horizontalGesture = null
            },
            onDrag = { change, dragAmount ->
              // detectDragGestures already waited for touch slop, so the first post-slop delta
              // is enough to classify the gesture. Requiring a second slop distance made short
              // flicks (especially downward hides) go unregistered.
              if (horizontalGesture == null) {
                horizontalGesture = abs(dragAmount.x) > abs(dragAmount.y)
                if (horizontalGesture == true) onTabDragStart()
              }
              if (horizontalGesture == true) {
                onTabDrag(dragAmount.x)
              } else {
                if (dragAmount.y > 0f) downwardDrag += dragAmount.y
              }
              change.consume()
            },
          )
        }
        .navigationBarsPadding()
        .padding(top = 8.dp, bottom = 4.dp)
  ) {
    AnimatedVisibility(
      visible = showFavorites && (favoriteApps.isNotEmpty() || recentApps.isNotEmpty()),
      enter = expandVertically() + fadeIn(),
      exit = shrinkVertically() + fadeOut(),
    ) {
      Column {
        FavoritesRow(
          favorites = favoriteApps,
          history = recentApps,
          historyLimit = historyLimit,
          minIconSizeSetting = minIconSizeSetting,
          onLaunch = { onLaunchFavorite(it as SearchResult.App) },
          onToggleFavorite = { onToggleFavorite(it as SearchResult.App) },
          onReorder = onReorder,
          onCapacityChanged = onHistoryCapacityChanged,
        )
        Spacer(modifier = Modifier.height(2.dp))
      }
    }

    SearchChromeBar(
      isIndexing = false,
      color = barColor,
      contentColor = barContentColor,
      // Zero tonal elevation: when the page color happens to equal the theme surface, Material
      // would tint the bar and it would stop matching the screen background exactly.
      tonalElevation = 0.dp,
    ) {
      Box(
        modifier =
          Modifier.weight(1f)
            .heightIn(min = 32.dp)
            // No ripple: a highlight would outline the bar as its own element instead of a
            // seamless part of the bottom section.
            .clickable(
              interactionSource = remember { MutableInteractionSource() },
              indication = null,
              onClick = onOpenSearch,
            ),
        contentAlignment = Alignment.CenterStart,
      ) {
        Text(
          text = "Search anything…",
          color = LocalContentColor.current.copy(alpha = 0.72f),
          fontSize = 16.sp,
        )
      }
      IconButton(onClick = onVoiceSearch, modifier = Modifier.size(32.dp).padding(4.dp)) {
        Icon(
          imageVector = Icons.Default.Mic,
          contentDescription = "Voice Search",
          tint = LocalContentColor.current,
        )
      }
      overflowMenu()
    }
  }
}

@Composable
private fun RevealBrowserChromeHandle(
  barColor: Color,
  barContentColor: Color,
  onReveal: () -> Unit,
  onSearch: () -> Unit,
  onVoiceSearch: () -> Unit,
  onTabDragStart: () -> Unit,
  onTabDrag: (Float) -> Unit,
  onTabDragEnd: () -> Unit,
  overflowMenu: @Composable () -> Unit,
  modifier: Modifier = Modifier,
) {
  // A compact pill mirroring the full bar: same shape, colors, icon sizes, and right-edge
  // position, with the mic and menu in their usual rightmost spots. Only the pill itself
  // handles input; the web content around it stays fully interactive.
  Box(modifier = modifier.navigationBarsPadding().padding(bottom = 4.dp, end = 16.dp)) {
    Surface(
      shape = RoundedCornerShape(16.dp),
      color = barColor.copy(alpha = 0.8f),
      contentColor = barContentColor,
      tonalElevation = 0.dp,
    ) {
      Row(
        modifier =
          Modifier.heightIn(min = 40.dp).padding(horizontal = 16.dp, vertical = 4.dp).pointerInput(
            onReveal,
            onTabDragStart,
            onTabDrag,
            onTabDragEnd,
          ) {
            var upwardDrag = 0f
            var horizontalGesture: Boolean? = null
            detectDragGestures(
              onDragStart = { upwardDrag = 0f },
              onDragEnd = {
                if (horizontalGesture == true) onTabDragEnd()
                else if (upwardDrag >= 16.dp.toPx()) onReveal()
                upwardDrag = 0f
                horizontalGesture = null
              },
              onDragCancel = {
                if (horizontalGesture == true) onTabDragEnd()
                upwardDrag = 0f
                horizontalGesture = null
              },
              onDrag = { change, dragAmount ->
                if (horizontalGesture == null) {
                  horizontalGesture = abs(dragAmount.x) > abs(dragAmount.y)
                  if (horizontalGesture == true) onTabDragStart()
                }
                if (horizontalGesture == true) {
                  onTabDrag(dragAmount.x)
                } else if (dragAmount.y < 0f) {
                  upwardDrag -= dragAmount.y
                }
                change.consume()
              },
            )
          },
        verticalAlignment = Alignment.CenterVertically,
      ) {
        IconButton(onClick = onReveal, modifier = Modifier.size(32.dp).padding(4.dp)) {
          Icon(
            imageVector = Icons.Default.KeyboardArrowUp,
            contentDescription = "Show launcher controls",
            tint = LocalContentColor.current,
          )
        }
        IconButton(onClick = onSearch, modifier = Modifier.size(32.dp).padding(4.dp)) {
          Icon(
            imageVector = Icons.Default.Search,
            contentDescription = "Search",
            tint = LocalContentColor.current,
          )
        }
        IconButton(onClick = onVoiceSearch, modifier = Modifier.size(32.dp).padding(4.dp)) {
          Icon(
            imageVector = Icons.Default.Mic,
            contentDescription = "Voice Search",
            tint = LocalContentColor.current,
          )
        }
        overflowMenu()
      }
    }
  }
}

private fun openOutsideWebView(context: Context, uri: Uri): Boolean {
  if (uri.scheme == "http" || uri.scheme == "https") return false

  return try {
    val intent =
      if (uri.scheme == "intent") Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME)
      else Intent(Intent.ACTION_VIEW, uri)
    context.startActivity(intent)
    true
  } catch (_: ActivityNotFoundException) {
    Toast.makeText(context, "No app can open this link", Toast.LENGTH_SHORT).show()
    true
  } catch (_: Exception) {
    Toast.makeText(context, "Cannot open this link", Toast.LENGTH_SHORT).show()
    true
  }
}

private fun shareUrl(context: Context, url: String, title: String?) {
  if (url.isBlank()) return
  val shareIntent =
    Intent(Intent.ACTION_SEND).apply {
      type = "text/plain"
      putExtra(Intent.EXTRA_TEXT, url)
      if (!title.isNullOrBlank()) putExtra(Intent.EXTRA_SUBJECT, title)
    }
  context.startActivity(Intent.createChooser(shareIntent, "Share webpage"))
}

private fun copyUrl(context: Context, url: String) {
  if (url.isBlank()) return
  val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
  clipboard.setPrimaryClip(ClipData.newPlainText("Webpage URL", url))
  Toast.makeText(context, "URL copied", Toast.LENGTH_SHORT).show()
}

private fun downloadImage(context: Context, url: String) {
  if (!url.startsWith("https://") && !url.startsWith("http://")) {
    Toast.makeText(context, "Cannot download this image", Toast.LENGTH_SHORT).show()
    return
  }
  try {
    val fileName = URLUtil.guessFileName(url, null, null)
    val request =
      DownloadManager.Request(Uri.parse(url))
        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
        .setTitle(fileName)
    CookieManager.getInstance().getCookie(url)?.let { request.addRequestHeader("Cookie", it) }
    (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
    Toast.makeText(context, "Downloading $fileName", Toast.LENGTH_SHORT).show()
  } catch (_: Exception) {
    Toast.makeText(context, "Download failed", Toast.LENGTH_SHORT).show()
  }
}

private fun openDownloads(context: Context) {
  try {
    context.startActivity(Intent(DownloadManager.ACTION_VIEW_DOWNLOADS))
  } catch (_: ActivityNotFoundException) {
    Toast.makeText(context, "Downloads app is unavailable", Toast.LENGTH_SHORT).show()
  }
}

@SuppressLint("SetJavaScriptEnabled")
private fun WebView.applySiteSettings(siteSettings: BrowserSiteSettings) {
  settings.javaScriptEnabled = siteSettings.javaScriptEnabled
  settings.javaScriptCanOpenWindowsAutomatically = siteSettings.popupsEnabled
  CookieManager.getInstance()
    .setAcceptThirdPartyCookies(this, siteSettings.thirdPartyCookiesEnabled)
}

private fun WebView.setDesktopMode(enabled: Boolean, phoneUserAgent: String?) {
  settings.userAgentString =
    if (enabled) desktopUserAgent(phoneUserAgent ?: settings.userAgentString) else phoneUserAgent
  // Wide viewport + overview mode let a forced desktop layout width (see applyDesktopViewport)
  // paint at ~980 CSS px and zoom to fit the physical screen on phones.
  settings.useWideViewPort = enabled
  settings.loadWithOverviewMode = enabled
}

/** Chromium's default desktop layout width when Request Desktop Site ignores viewport meta. */
internal const val DESKTOP_VIEWPORT_WIDTH = 980

internal fun desktopViewportMetaContent(width: Int = DESKTOP_VIEWPORT_WIDTH): String = "width=$width"

/**
 * Force a desktop CSS viewport on small devices. UA spoofing alone is not enough: responsive
 * sites with `width=device-width` still match phone media queries. WebView has no API to ignore
 * viewport meta like Chrome RDS, so we rewrite (or create) the tag to a fixed desktop width.
 */
private fun WebView.applyDesktopViewport(enabled: Boolean) {
  if (!enabled) return
  evaluateJavascript(DESKTOP_VIEWPORT_SCRIPT, null)
}

internal fun desktopUserAgent(phoneUserAgent: String): String =
  phoneUserAgent
    .replace(Regex("""\([^)]*\)"""), "(X11; Linux x86_64)")
    .replace("; wv", "")
    .replace(" Version/4.0", "")
    .replace(" Mobile", "")

private fun clearSiteData(webView: WebView, url: String, onComplete: () -> Unit) {
  browserOrigin(url)?.let { WebStorage.getInstance().deleteOrigin(it) }

  val cookieManager = CookieManager.getInstance()
  cookieManager
    .getCookie(url)
    ?.split(';')
    ?.mapNotNull { cookie -> cookie.substringBefore('=').trim().takeIf(String::isNotEmpty) }
    ?.distinct()
    ?.forEach { cookieName ->
      cookieManager.setCookie(
        url,
        "$cookieName=; Expires=Thu, 01 Jan 1970 00:00:00 GMT; Max-Age=0; Path=/",
      )
    }
  cookieManager.flush()

  webView.evaluateJavascript(CLEAR_SITE_STORAGE_SCRIPT, null)
  webView.postDelayed(
    {
      webView.reload()
      onComplete()
    },
    250,
  )
}

internal fun parseCssColor(value: String): Int? {
  val components =
    Regex("""rgba?\(\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)(?:\s*,\s*([\d.]+))?\s*\)""")
      .find(value.trim().trim('"'))
      ?.groupValues ?: return null
  val alpha = components.getOrNull(4)?.toFloatOrNull() ?: 1f
  if (alpha <= 0f) return null
  val a = (alpha.coerceIn(0f, 1f) * 255).roundToInt()
  val r = components[1].toInt().coerceIn(0, 255)
  val g = components[2].toInt().coerceIn(0, 255)
  val b = components[3].toInt().coerceIn(0, 255)
  return (a shl 24) or (r shl 16) or (g shl 8) or b
}

private const val PAGE_BACKGROUND_SCRIPT =
  """
  (() => {
    const transparent = 'rgba(0, 0, 0, 0)';
    const body = document.body ? getComputedStyle(document.body).backgroundColor : transparent;
    if (body && body !== transparent) return body;
    return getComputedStyle(document.documentElement).backgroundColor;
  })()
  """

private val DESKTOP_VIEWPORT_SCRIPT =
  """
  (() => {
    const content = '${desktopViewportMetaContent()}';
    const ensure = () => {
      if (!document.documentElement) return null;
      let meta = document.querySelector('meta[name="viewport"]');
      if (!meta) {
        meta = document.createElement('meta');
        meta.setAttribute('name', 'viewport');
        (document.head || document.documentElement).appendChild(meta);
      }
      if (meta.getAttribute('content') !== content) {
        meta.setAttribute('content', content);
      }
      return meta;
    };
    const meta = ensure();
    if (!meta || document.documentElement.dataset.slDesktopViewport === '1') return;
    document.documentElement.dataset.slDesktopViewport = '1';
    new MutationObserver(ensure).observe(meta, {
      attributes: true,
      attributeFilter: ['content'],
    });
    if (document.head) {
      new MutationObserver(ensure).observe(document.head, { childList: true });
    }
  })()
  """

private const val CLEAR_SITE_STORAGE_SCRIPT =
  """
  (() => {
    try { localStorage.clear(); } catch (_) {}
    try { sessionStorage.clear(); } catch (_) {}
    try {
      if (window.caches) caches.keys().then(keys => keys.forEach(key => caches.delete(key)));
    } catch (_) {}
    try {
      if (navigator.serviceWorker) {
        navigator.serviceWorker.getRegistrations().then(items => items.forEach(item => item.unregister()));
      }
    } catch (_) {}
    try {
      if (indexedDB.databases) {
        indexedDB.databases().then(items => items.forEach(item => item.name && indexedDB.deleteDatabase(item.name)));
      }
    } catch (_) {}
  })()
  """
