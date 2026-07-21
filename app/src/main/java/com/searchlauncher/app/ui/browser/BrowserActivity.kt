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
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
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
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Mic
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

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun BrowserScreen(
  navigationRequest: NavigationRequest?,
  privateMode: Boolean,
  showLauncherChrome: Boolean,
  browserMenuRequest: Long,
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
  val showFavorites by
    remember {
        context.dataStore.data.map { preferences ->
          preferences[PreferencesKeys.BROWSER_SHOW_FAVORITES] ?: true
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
      exitFullscreenVideo()
      webView?.let { saveWebViewIntoTab(it, tabs.active) }
      val newTab = tabs.add(request.url)
      newTab.pageBackgroundArgb = defaultPageBackground.toArgb()
      webView = null
      progress = 0
      pageBackground = Color(newTab.pageBackgroundArgb)
      siteSettings = siteSettingsStore.load(newTab.url)
      restoringSnapshot = newTab.snapshot
    }
  }

  LaunchedEffect(activeTab.id) {
    pageBackground = Color(activeTab.pageBackgroundArgb)
    siteSettings = siteSettingsStore.load(activeTab.url)
    restoringSnapshot = activeTab.snapshot
  }

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

              webChromeClient =
                object : WebChromeClient() {
                  override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    progress = newProgress
                    if (newProgress >= 35) restoringSnapshot = null
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
                    siteSettings = siteSettingsStore.load(url)
                    view.applySiteSettings(siteSettings)
                  }

                  override fun onPageCommitVisible(view: WebView, url: String) {
                    if (suppressCommitVisibleColor) {
                      suppressCommitVisibleColor = false
                    } else {
                      refreshPageBackground(view, allowWhiteFallback = false)
                    }
                  }

                  override fun onPageFinished(view: WebView, url: String) {
                    refreshPageBackground(view, allowWhiteFallback = true)
                    activeTab.url = url
                    activeTab.title = view.title
                    restoringSnapshot = null
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
              else postDelayed({ restoringSnapshot = null }, 220)
            }
          },
          onRelease = { releasedView ->
            if (webView === releasedView) webView = null
            releasedView.destroy()
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
          privateMode = privateMode,
          desktopMode = activeTab.desktopMode,
          showFavorites = showFavorites,
          barColor = chromeBarColor,
          barContentColor = chromeBarContentColor,
          browserMenuRequest = browserMenuRequest,
          tabCount = tabs.items.size,
          hasPreviousTab = tabs.activeIndex > 0,
          hasNextTab = tabs.activeIndex < tabs.items.lastIndex,
          onPreviousTab = { animateToAdjacentTab(-1) },
          onNextTab = { animateToAdjacentTab(1) },
          onOpenSearch = { onOpenSearch(false, pageBackground.toArgb()) },
          onVoiceSearch = { onOpenSearch(true, pageBackground.toArgb()) },
          onShare = { shareUrl(context, webView?.url ?: activeTab.url, webView?.title) },
          onCopyUrl = { copyUrl(context, webView?.url ?: activeTab.url) },
          onToggleDesktopMode = {
            val view = webView ?: return@BrowserLauncherChrome
            activeTab.desktopMode = !activeTab.desktopMode
            view.setDesktopMode(activeTab.desktopMode, phoneUserAgent)
            view.reload()
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
          onTabDragStart = {
            settleJob?.cancel()
            tabsInMotion = true
            fingerOnTabDrag = true
            webView?.let { saveWebViewIntoTab(it, tabs.active) }
            dragTabStateSaved = true
          },
          onTabDrag = { delta ->
            val proposed = tabDragOffsetPx + delta
            val direction = if (proposed < 0f) 1 else -1
            tabDragOffsetPx += if (tabs.adjacent(direction) != null) delta else delta * 0.16f
          },
          onTabDragEnd = {
            fingerOnTabDrag = false
            val startOffset = tabDragOffsetPx
            val direction = if (startOffset < 0f) 1 else -1
            val commit =
              tabs.adjacent(direction) != null && abs(startOffset) >= viewportWidthPx * 0.18f
            if (commit) {
              // Switch the model immediately and rebase the offset so the new active tab keeps
              // its current on-screen position; the next swipe can start right away instead of
              // waiting for the settle animation.
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
                    spring(
                      dampingRatio = Spring.DampingRatioNoBouncy,
                      stiffness = Spring.StiffnessMedium,
                    ),
                ) { value, _ ->
                  tabDragOffsetPx = value
                }
                tabsInMotion = false
              }
          },
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
        modifier = Modifier.align(Alignment.BottomCenter),
        enter = fadeIn(),
        exit = fadeOut(),
      ) {
        RevealBrowserChromeHandle(onReveal = { chromeHiddenByUser = false })
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

  if (showPageSettings) {
    BrowserPageSettingsDialog(
      siteLabel = browserSiteLabel(webView?.url ?: activeTab.url),
      settings = siteSettings,
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
  privateMode: Boolean,
  desktopMode: Boolean,
  showFavorites: Boolean,
  barColor: Color,
  barContentColor: Color,
  browserMenuRequest: Long,
  tabCount: Int,
  hasPreviousTab: Boolean,
  hasNextTab: Boolean,
  onPreviousTab: () -> Unit,
  onNextTab: () -> Unit,
  onOpenSearch: () -> Unit,
  onVoiceSearch: () -> Unit,
  onShare: () -> Unit,
  onCopyUrl: () -> Unit,
  onToggleDesktopMode: () -> Unit,
  onOpenDownloads: () -> Unit,
  onFindInPage: () -> Unit,
  onPageSettings: () -> Unit,
  onToggleFavorites: () -> Unit,
  onNewTab: () -> Unit,
  onCloseTab: () -> Unit,
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
    if (showFavorites && (favoriteApps.isNotEmpty() || recentApps.isNotEmpty())) {
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
      BrowserOverflowButton(
        desktopMode = desktopMode,
        showFavorites = showFavorites,
        tabCount = tabCount,
        hasPreviousTab = hasPreviousTab,
        hasNextTab = hasNextTab,
        menuColor = barColor,
        menuContentColor = barContentColor,
        onPreviousTab = onPreviousTab,
        onNextTab = onNextTab,
        openRequest = browserMenuRequest,
        onShare = onShare,
        onCopyUrl = onCopyUrl,
        onToggleDesktopMode = onToggleDesktopMode,
        onOpenDownloads = onOpenDownloads,
        onFindInPage = onFindInPage,
        onPageSettings = onPageSettings,
        onToggleFavorites = onToggleFavorites,
        onNewTab = onNewTab,
        onCloseTab = onCloseTab,
      )
    }
  }
}

@Composable
private fun RevealBrowserChromeHandle(onReveal: () -> Unit, modifier: Modifier = Modifier) {
  // Only the caret itself handles input; the web content around and behind it stays
  // fully interactive.
  Box(
    modifier = modifier.navigationBarsPadding().padding(bottom = 8.dp),
    contentAlignment = Alignment.Center,
  ) {
    Surface(
      modifier =
        Modifier.size(36.dp)
          .pointerInput(onReveal) {
            var upwardDrag = 0f
            detectDragGestures(
              onDragStart = { upwardDrag = 0f },
              onDrag = { change, dragAmount ->
                if (dragAmount.y < 0f) upwardDrag -= dragAmount.y
                change.consume()
              },
              onDragEnd = { if (upwardDrag >= 16.dp.toPx()) onReveal() },
            )
          }
          .clickable(onClick = onReveal),
      shape = CircleShape,
      color = MaterialTheme.colorScheme.surface.copy(alpha = 0.45f),
      tonalElevation = 1.dp,
    ) {
      Box(contentAlignment = Alignment.Center) {
        Icon(
          imageVector = Icons.Default.KeyboardArrowUp,
          contentDescription = "Show launcher controls",
          modifier = Modifier.size(20.dp),
          tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
        )
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
  settings.useWideViewPort = enabled
  settings.loadWithOverviewMode = enabled
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
