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
import android.graphics.drawable.ColorDrawable
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
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import coil.compose.AsyncImage
import com.searchlauncher.app.SearchLauncherApp
import com.searchlauncher.app.data.Prefs
import com.searchlauncher.app.data.SearchResult
import com.searchlauncher.app.data.favoriteKey
import com.searchlauncher.app.ui.MainActivity
import com.searchlauncher.app.ui.MinIconSize
import com.searchlauncher.app.ui.PreferencesKeys
import com.searchlauncher.app.ui.ResultLauncher
import com.searchlauncher.app.ui.SearchActivity
import com.searchlauncher.app.ui.components.BookmarkDialog
import com.searchlauncher.app.ui.components.FavoritesRow
import com.searchlauncher.app.ui.components.SearchChromeBar
import com.searchlauncher.app.ui.dataStore
import com.searchlauncher.app.ui.theme.SearchLauncherTheme
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

open class BrowserActivity : ComponentActivity() {
  private var navigationRequest by mutableStateOf<NavigationRequest?>(null)
  private var searchOverlayVisible by mutableStateOf(false)
  private var browserMenuRequest by mutableLongStateOf(0L)
  private var tabActivationRequest by mutableStateOf<TabActivationRequest?>(null)
  /** Set between launching the search overlay and it taking window focus. */
  private var pendingSearchLaunch = false
  protected open val isPrivateMode: Boolean = false

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    navigationRequest = intent.toNavigationRequest(0)
    // Applied before the first composition so the browser is built around the right tab instead
    // of building a WebView for the previous one and switching a frame later.
    intent.requestedTabIndex()?.let(BrowserTabStore::activate)
    // The theme suppresses the starting window, but this one still paints in the window
    // background for the frames between here and Compose's first draw. Dressing it in the tab's
    // own colour means those frames look like the page arriving early rather than a white gap in
    // the middle of the handover.
    BrowserTabStore.tabs?.active?.pageBackgroundArgb?.let {
      window.setBackgroundDrawable(ColorDrawable(it))
    }
    ContextCompat.registerReceiver(
      this,
      browserActionReceiver,
      IntentFilter().apply {
        addAction(ACTION_SHOW_BROWSER_MENU)
        addAction(ACTION_CLOSE_BROWSER)
      },
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
          tabActivationRequest = tabActivationRequest,
          onOpenSearch = { voice, color, query -> openSearch(voice, color, query) },
          onClose = ::finish,
        )
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    navigationRequest = intent.toNavigationRequest(System.nanoTime())
    intent.requestedTabIndex()?.let {
      tabActivationRequest = TabActivationRequest(it, System.nanoTime())
    }
  }

  override fun onResume() {
    super.onResume()
    searchOverlayVisible = false
    pendingSearchLaunch = false
  }

  /**
   * The search overlay is a translucent activity, so this one is never stopped and never learns
   * directly that it is covered. Losing window focus is the first reliable sign the overlay is
   * actually up, and that is when the chrome can go: dropping it at the moment of the tap blinked
   * the bar out for the frames before the overlay drew its own — identical — one, which is what
   * made opening search feel like a jump rather than a handover.
   */
  override fun onWindowFocusChanged(hasFocus: Boolean) {
    super.onWindowFocusChanged(hasFocus)
    if (!hasFocus && pendingSearchLaunch) {
      pendingSearchLaunch = false
      searchOverlayVisible = true
    }
  }

  override fun onDestroy() {
    unregisterReceiver(browserActionReceiver)
    super.onDestroy()
  }

  @Composable
  private fun <T> preference(key: Preferences.Key<T>, default: T): State<T> =
    remember(key) { dataStore.data.map { it[key] ?: default } }.collectAsState(initial = default)

  private fun openSearch(
    startVoiceSearch: Boolean,
    chromeColorArgb: Int,
    initialQuery: String = "",
  ) {
    pendingSearchLaunch = true
    startActivity(
      Intent(this, SearchActivity::class.java)
        // No window transition: the overlay opens onto a bar in the same place and shape as the
        // one being tapped, so a fade over the top of that only muddies the handover.
        .addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
        .putExtra(SearchActivity.EXTRA_PRIVATE_WEB_RESULTS, isPrivateMode)
        .putExtra(SearchActivity.EXTRA_START_VOICE_SEARCH, startVoiceSearch)
        .putExtra(SearchActivity.EXTRA_BROWSER_SEARCH, true)
        .putExtra(SearchActivity.EXTRA_CHROME_COLOR, chromeColorArgb)
        .putExtra(SearchActivity.EXTRA_INITIAL_QUERY, initialQuery)
    )
  }

  private fun Intent.toNavigationRequest(sequence: Long): NavigationRequest? {
    val url = dataString ?: getStringExtra(EXTRA_URL) ?: return null
    return NavigationRequest(browserDestination(url), sequence)
  }

  /** The tab a resume intent asks for, or null when this is not a resume intent. */
  private fun Intent.requestedTabIndex(): Int? {
    if (isPrivateMode) return null
    return getIntExtra(EXTRA_TAB_INDEX, NO_TAB_REQUEST).takeIf { it != NO_TAB_REQUEST }
  }

  companion object {
    const val ACTION_SHOW_BROWSER_MENU = "com.searchlauncher.app.action.SHOW_BROWSER_MENU"

    /**
     * Asks a running browser to close itself, sent when the last tab is closed from outside it.
     * Harmless when no browser is running.
     */
    const val ACTION_CLOSE_BROWSER = "com.searchlauncher.app.action.CLOSE_BROWSER"
    private const val EXTRA_URL = "browser_url"
    private const val EXTRA_TAB_INDEX = "browser_tab_index"
    private const val NO_TAB_REQUEST = Int.MIN_VALUE

    /** Passed as [createResumeIntent]'s index to mean "whichever tab is newest". */
    const val NEWEST_TAB = -1

    /**
     * Resumes the existing tabs on [tabIndex], with no window animation: the launcher has already
     * slid that tab across the screen (or faded its overview away), and a second transition on top
     * of that would break the illusion of one continuous gesture.
     */
    fun createResumeIntent(context: Context, tabIndex: Int = NEWEST_TAB): Intent =
      Intent(context, BrowserActivity::class.java).apply {
        putExtra(EXTRA_TAB_INDEX, tabIndex)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
      }

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

  private val browserActionReceiver =
    object : BroadcastReceiver() {
      override fun onReceive(context: Context?, intent: Intent?) {
        when (intent?.action) {
          ACTION_SHOW_BROWSER_MENU -> browserMenuRequest = System.nanoTime()
          // The launcher closed the last tab. This window is showing a list that no longer exists
          // anywhere else, and there is nothing left to browse, so it goes with them. Incognito
          // keeps its own tabs in its own process and is none of this broadcast's business.
          ACTION_CLOSE_BROWSER -> if (!isPrivateMode) finish()
        }
      }
    }
}

data class NavigationRequest(val url: String, val sequence: Long)

/** A request to bring an already-running browser to a particular tab; -1 means the newest one. */
internal data class TabActivationRequest(val index: Int, val sequence: Long)

/** Target of a long-press on web content: a link, an image, or a link wrapping an image. */
private data class LinkMenuTarget(val linkUrl: String?, val imageUrl: String?)

/** A bookmark awaiting title confirmation in [BookmarkDialog]. */
private data class BookmarkDraft(val url: String, val title: String)

@OptIn(ExperimentalLayoutApi::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun BrowserScreen(
  navigationRequest: NavigationRequest?,
  privateMode: Boolean,
  showLauncherChrome: Boolean,
  browserMenuRequest: Long,
  onBrowserMenuShown: () -> Unit,
  tabActivationRequest: TabActivationRequest?,
  onOpenSearch: (Boolean, Int, String) -> Unit,
  onClose: () -> Unit,
  /**
   * Set when the launcher is hosting this screen itself rather than running it as its own activity.
   * Leaving for the launcher is then a state change in the same composition instead of a task
   * change, so there is no window handover to see and the slide direction is ours to choose.
   */
  onReturnToLauncher: (() -> Unit)? = null,
  /**
   * Given the drag that leads out of the leftmost tab, when the launcher is hosting this screen.
   * The browser used to answer that drag by sliding its own page off the edge and drawing a
   * stand-in launcher beside it, because the real one was in another task. Hosted, the real home
   * screen is a sibling — it is simply held a screen to the right — so the honest thing is to hand
   * the drag over and let one movement carry both.
   */
  /**
   * True while the host is carrying this screen away. The keyboard it is raising belongs to the
   * home screen arriving behind, not to this page, so the page stops reserving room for it — a
   * reflow on the way out is a visible step in something that should simply be leaving.
   */
  leaving: Boolean = false,
  onLauncherDrag: ((Float) -> Unit)? = null,
  /** The finger left during such a drag, travelling at this many pixels a second. */
  onLauncherDragEnd: ((Float) -> Unit)? = null,
) {
  val context = LocalContext.current
  val app = context.applicationContext as SearchLauncherApp
  // Read the shared favorites flow so the browser strip matches search — but only when there is
  // one to read. The private browser is a separate process with no repositories at all, so these
  // are absent there rather than merely read-only, and the strip falls back to empty.
  val sharedSearchRepository = app.searchRepositoryOrNull
  // Null in private mode so browsing never writes index/history/favicons.
  val searchRepository = if (privateMode) null else sharedSearchRepository
  val favoritesRepository = app.favoritesRepositoryOrNull
  val noIds = remember { MutableStateFlow(emptyList<String>()) }
  val noResults = remember { MutableStateFlow(emptyList<SearchResult>()) }
  val favoriteIds by (favoritesRepository?.favoriteIds ?: noIds).collectAsState()
  val favorites by (sharedSearchRepository?.favorites ?: noResults).collectAsState()
  val allRecentItems by (sharedSearchRepository?.recentItems ?: noResults).collectAsState()
  val historyLimit by
    remember { context.dataStore.data.map { it[PreferencesKeys.HISTORY_LIMIT] ?: -1 } }
      .collectAsState(initial = -1)
  val minIconSizeSetting by
    remember { MinIconSize.flow(context) }.collectAsState(initial = MinIconSize.cached(context))
  val historyItems =
    remember(allRecentItems, favoriteIds, historyLimit, privateMode) {
      if (privateMode || historyLimit == 0) emptyList()
      else {
        val favoriteKeys = favoriteIds.toSet()
        val filtered = allRecentItems.filter { it.favoriteKey !in favoriteKeys }
        if (historyLimit >= 0) filtered.take(historyLimit) else filtered
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
  val resultLauncher =
    remember(context, sharedSearchRepository, coroutineScope) {
      sharedSearchRepository?.let {
        ResultLauncher(context = context, searchRepository = it, scope = coroutineScope)
      }
    }
  val initialNavigationRequest = remember { navigationRequest }
  val defaultPageBackground = MaterialTheme.colorScheme.background
  // Private browsing keeps its tabs in the composition (its process is torn down with the window
  // and nothing outside it may see the previews); normal browsing adopts the process-wide tabs so
  // the launcher can swipe back into them.
  val restoredTabs = remember { if (privateMode) null else BrowserTabStore.tabs }
  val tabs = remember {
    restoredTabs
      ?: BrowserTabs(initialNavigationRequest?.url ?: "about:blank").also {
        // Seed the first tab with the theme background so opening the app doesn't tween the
        // bottom section from the BrowserTab default (black) to the real color.
        it.active.pageBackgroundArgb = defaultPageBackground.toArgb()
        if (!privateMode) BrowserTabStore.adopt(it)
      }
  }
  var handledNavigationSequence by remember {
    // A freshly seeded tab already holds the requested URL, but tabs restored from a previous
    // browser session do not — that request still has to open, as a new tab.
    mutableLongStateOf(
      if (restoredTabs == null) initialNavigationRequest?.sequence ?: Long.MIN_VALUE
      else Long.MIN_VALUE
    )
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
  // Set while a tapped card in the overview is growing into the page, so the bars can travel with
  // it rather than after it.
  var tabExpanding by remember { mutableStateOf(false) }
  // Set when the outgoing tab's state and snapshot were already captured at drag start, so the
  // commit path at the end of the swipe stays free of bitmap work.
  var dragTabStateSaved by remember { mutableStateOf(false) }
  // True from the start of a horizontal swipe until its settle animation completes. While set,
  // tab switches render pure snapshots and no WebView is built, so swipes can chain without
  // waiting on WebView construction.
  var tabsInMotion by remember { mutableStateOf(false) }
  var fingerOnTabDrag by remember { mutableStateOf(false) }
  /** Set while a sideways drag is being answered by the host rather than by this screen. */
  var launcherDragActive by remember { mutableStateOf(false) }
  var settleJob by remember { mutableStateOf<Job?>(null) }
  // A restored tab reloads its page, which would re-sample the background pre-CSS (usually
  // white) and make the persisted per-tab color flicker. Skip the early sample for restores;
  // onPageFinished still confirms the final color.
  var suppressCommitVisibleColor by remember { mutableStateOf(false) }
  var viewportWidthPx by remember { mutableIntStateOf(1) }
  var viewportHeightPx by remember { mutableIntStateOf(1) }
  var tabsOverviewOpen by remember { mutableStateOf(false) }
  // Kept composed until the closing animation has actually finished. Deliberately separate from
  // the progress value below, which is only ever read inside graphicsLayer blocks so that a
  // running transition redraws without recomposing this (large) screen every frame.
  var tabsOverviewRendered by remember { mutableStateOf(false) }
  // 0 = browsing, 1 = the chrome bar has finished travelling to the top and the tab strip is in
  // place. Everything the transition animates reads this single value, which is what makes
  // closing the overview exactly the inverse of opening it.
  val overviewProgress by
    animateFloatAsState(
      targetValue = if (tabsOverviewOpen) 1f else 0f,
      animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 380f),
      label = "tabsOverview",
      finishedListener = { settled -> if (settled == 0f) tabsOverviewRendered = false },
    )
  // The launcher sits one screen to the right of the newest tab: swiping past the end of the tab
  // strip leaves the browser, and swiping back on the launcher's chrome bar returns here. Private
  // browsing is its own task and has no such neighbour.
  val launcherIsNextNeighbour = !privateMode && tabs.activeIndex == tabs.items.lastIndex
  val adjacentDirection =
    when {
      tabDragOffsetPx < 0f -> 1
      tabDragOffsetPx > 0f -> -1
      else -> 0
    }
  val adjacentTab = if (adjacentDirection == 0) null else tabs.adjacent(adjacentDirection)

  // In-page color changes ease; a swipe does not use this at all. Snapping during motion is what
  // lets the blend below hand back to it at the end of a settle without the color stepping.
  val settledPageBackground by
    animateColorAsState(
      pageBackground,
      when {
        tabsInMotion -> snap()
        // Borrowed wholesale from the growing card so the two are one movement. Selecting a tab
        // used to leave the bars on the old tab's color for the whole growth and only start them
        // once it had finished, which read as the page arriving and the frame around it catching
        // up afterwards.
        tabExpanding -> tween(durationMillis = TAB_EXPAND_DURATION_MS, easing = FastOutSlowInEasing)
        else -> tween(durationMillis = 450)
      },
      label = "pageBackground",
    )
  // How far the neighbouring tab has come across, 0 to 1.
  val tabSwipeProgress =
    if (viewportWidthPx <= 0) 0f else (abs(tabDragOffsetPx) / viewportWidthPx).coerceIn(0f, 1f)
  val neighbourBackground =
    adjacentTab?.let { Color(it.pageBackgroundArgb) }
      // Swiping off the end hands over to the home screen, which is on the theme background.
      ?: defaultPageBackground.takeIf { launcherIsNextNeighbour && tabDragOffsetPx < 0f }
  // Driven by where the swipe is rather than by a clock of its own. A duration to match the slide
  // would only ever be approximately right — the slide is a spring, and the same gesture settles
  // differently depending on how it was released — whereas position is exactly right for free, and
  // tracks the finger during the drag too instead of waiting for the release.
  //
  // Continuous across the commit in the middle of a swipe, where the active tab becomes the
  // neighbour and the offset is rebased a screen over: both sides of that swap describe the same
  // blend of the same two colors, so nothing steps.
  val animatedPageBackground =
    if (neighbourBackground != null && tabSwipeProgress > 0f) {
      lerp(pageBackground, neighbourBackground, tabSwipeProgress)
    } else {
      settledPageBackground
    }
  val density = LocalDensity.current
  val statusBarTopPx = WindowInsets.statusBars.getTop(density)
  // The page has to clear the keyboard as well as the browser's own chrome. An edge-to-edge window
  // is not resized by the IME — the manifest's adjustResize stops applying the moment
  // enableEdgeToEdge turns off decor fitting — so the inset has to be applied here. Without it a
  // bottom-anchored input on the page (an AI chat box, a comment field) sits under the keys while
  // the user types into it.
  //
  // This follows the animation's target rather than its current value: every change relays out the
  // WebView and reflows the page, which is far too expensive to repeat on every frame of the
  // keyboard's entrance. The page resizes once and the keys slide up over it.
  // Ignored on the way out: see [leaving]. The keyboard rising then is the launcher's.
  val imeInsets = if (leaving) WindowInsets(bottom = 0) else WindowInsets.imeAnimationTarget
  val webContentInsets =
    when {
      // The find bar rides above the keyboard, so the page clears the two of them stacked.
      showFindInPage -> imeInsets.add(WindowInsets(bottom = findBarHeightPx))
      // The chrome bar stays put and ends up behind the keyboard rather than above it, so the
      // taller of the two wins. When the user has swiped the chrome away only the small reveal
      // caret remains, floating over full-bleed web content, so no inset is reserved for it.
      showLauncherChrome && !chromeHiddenByUser ->
        imeInsets.union(WindowInsets(bottom = chromeHeightPx))
      else -> imeInsets
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
    tabsOverviewOpen = false
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
        onOpenSearch(false, defaultPageBackground.toArgb(), "")
      }
  }

  fun closeActiveTab() {
    if (tabs.items.size == 1) {
      // The browser is empty now, so forget the tabs: the launcher must not offer to swipe back
      // into something the user just closed.
      if (!privateMode) BrowserTabStore.clear()
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

  /**
   * Refreshes the active tab's preview from the live WebView. Whether there is anything worth
   * capturing is [captureTabSnapshot]'s call, so every path that leaves a tab can ask freely.
   */
  fun captureActiveTabPreview() {
    webView?.let { saveWebViewIntoTab(it, tabs.active) }
  }

  fun openTabsOverview() {
    if (tabsOverviewOpen) return
    exitFullscreenVideo()
    // The strip shows the page as it is right now, not as it was when the tab was last left.
    captureActiveTabPreview()
    tabsOverviewRendered = true
    tabsOverviewOpen = true
  }

  /**
   * The tap itself, before the card has grown. Only the color is settled here — the selection
   * proper waits for the growth to finish — because this is the part the user watches happen.
   */
  fun startTabSelection(index: Int) {
    tabs.items.getOrNull(index)?.let { pageBackground = Color(it.pageBackgroundArgb) }
    tabExpanding = true
  }

  fun selectTabFromOverview(index: Int) {
    tabExpanding = false
    activateTab(index)
    tabsOverviewOpen = false
  }

  fun closeTabFromOverview(index: Int) {
    // Closing the tab you are on has to hand the WebView over to its neighbour, which is exactly
    // what the menu's Close tab does; the others are pure list edits. Closing the only tab leaves
    // nothing to look at, so the browser goes away with it.
    if (index == tabs.activeIndex) closeActiveTab() else tabs.close(index)
  }

  fun closeAllTabs() {
    exitFullscreenVideo()
    if (!privateMode) BrowserTabStore.clear()
    onClose()
  }

  // Plays the same slide animation as a horizontal swipe, so menu-triggered tab switches teach
  // the gesture. A slower spring than the swipe settle keeps the motion legible.
  fun openLinkInNewTab(url: String) {
    exitFullscreenVideo()
    // Opening a page is a request to look at that page, so the overview gets out of the way even
    // when it was left up in an earlier visit to the browser.
    tabsOverviewOpen = false
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
      tabsOverviewOpen = false
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

  // A swipe or a tap in the launcher's tabs overview reopens an already-running browser through
  // onNewIntent, which lands here rather than in onCreate.
  LaunchedEffect(tabActivationRequest?.sequence) {
    val request = tabActivationRequest ?: return@LaunchedEffect
    tabsOverviewOpen = false
    activateTab(if (request.index < 0) tabs.items.lastIndex else request.index)
  }

  // The overview lives around the chrome bar, so it cannot outlast it: opening search from the
  // bar takes the bar away, which would strand the tab strip with no header.
  LaunchedEffect(showLauncherChrome) { if (!showLauncherChrome) tabsOverviewOpen = false }

  // Leaving the browser (home button, the search overlay, a swipe back to the launcher) is exactly
  // when the preview has to be current: it is what the launcher shows to swipe back in with.
  val lifecycleOwner = LocalLifecycleOwner.current
  DisposableEffect(lifecycleOwner) {
    val observer = LifecycleEventObserver { _, event ->
      when (event) {
        Lifecycle.Event.ON_PAUSE -> captureActiveTabPreview()
        // A swipe out to the launcher parks the page off the edge and hands the launcher control
        // partway through the settle, so that animation is still running as this arrives —
        // cancelling it first is what stops it writing the off-screen offset back afterwards and
        // leaving the browser stuck behind a full-screen launcher stand-in that eats every touch.
        // Resuming resets again in case the browser was never fully stopped.
        Lifecycle.Event.ON_STOP,
        Lifecycle.Event.ON_RESUME -> {
          settleJob?.cancel()
          tabDragOffsetPx = 0f
          // Cleared with it: while this is set no WebView is built at all, so a cancelled settle
          // would otherwise leave the browser showing nothing but snapshots.
          tabsInMotion = false
          fingerOnTabDrag = false
          dragTabStateSaved = false
        }
        else -> Unit
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
  }

  // Safety net: a page that never reaches a drawn onPageFinished — hung renderer, stuck load —
  // must not leave the stale snapshot covering it forever. Keyed on the tab so a late timeout
  // cannot uncover a different one.
  LaunchedEffect(activeTab.id, restoringSnapshot) {
    if (restoringSnapshot != null) {
      delay(5000)
      restoringSnapshot = null
    }
  }

  // Loads the cached filter list (downloading it when missing or stale). Pages opened before this
  // finishes simply aren't filtered; nothing blocks on it.
  LaunchedEffect(adBlockEnabled) { if (adBlockEnabled) AdBlocker.ensureLoaded(context) }

  // Kept in step with the page rather than set once, because the window background is what the
  // system paints whenever it presents this activity — and it presents it again on every return
  // from the launcher.
  val windowBackground = remember { ColorDrawable(pageBackground.toArgb()) }
  LaunchedEffect(Unit) {
    (context as ComponentActivity).window.setBackgroundDrawable(windowBackground)
  }

  LaunchedEffect(animatedPageBackground) {
    (context as ComponentActivity).let { activity ->
      val isLightBackground = animatedPageBackground.luminance() > 0.5f
      activity.window.navigationBarColor = animatedPageBackground.toArgb()
      activity.window.isNavigationBarContrastEnforced = false
      // The onCreate attempt at this cannot work on a cold start: the tab store is adopted from
      // inside the composition, so it is still empty that early and the window keeps the theme's
      // light background. Compose then draws the right colour over it and everything looks fine —
      // until the browser is swiped back to from the launcher, when the system shows the window
      // itself for a few frames before the content composites, and that stale white is the flash.
      windowBackground.color = animatedPageBackground.toArgb()
      WindowCompat.getInsetsController(activity.window, activity.window.decorView).apply {
        isAppearanceLightStatusBars = isLightBackground
        isAppearanceLightNavigationBars = isLightBackground
      }
    }
  }

  LaunchedEffect(fullscreenVideoView != null) {
    val window = (context as ComponentActivity).window
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
    if (tabsOverviewOpen) {
      tabsOverviewOpen = false
    } else if (fullscreenVideoView != null) {
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

  // Read the same way the launcher reads it, straight off disk, so the stand-in below reserves
  // exactly the strip the home screen will.
  val launcherKeyboardReservePx = remember {
    if (privateMode) 0
    else
      context
        .getSharedPreferences(Prefs.Window.FILE, Context.MODE_PRIVATE)
        .getInt(Prefs.Window.KEYBOARD_HEIGHT, 0)
  }
  val launcherWallpaperUri by
    remember(privateMode) {
        if (privateMode) flowOf(null)
        else context.dataStore.data.map { it[PreferencesKeys.BACKGROUND_LAST_IMAGE_URI] }
      }
      .collectAsState(initial = null)

  fun returnToLauncher() {
    onReturnToLauncher?.let {
      // Parking the page off the left edge was for the activity handover: it had to stay there
      // until this activity stopped, or a frame of the browser showed before the launcher took
      // over. Hosted, nothing stops — the launcher slides this whole screen out itself — so the
      // parked offset would simply remain, and the next visit would arrive to find the page still
      // a screen to the left and nothing but the background where it should be.
      settleJob?.cancel()
      tabDragOffsetPx = 0f
      tabsInMotion = false
      it()
      return
    }
    context.startActivity(
      Intent(context, MainActivity::class.java)
        .putExtra(MainActivity.EXTRA_FOCUS_SEARCH, true)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
    )
  }

  // Tab-swipe handlers shared by the full chrome and the minimal pill.
  val tabDragVelocity = remember { VelocityTracker() }
  val tabDragStart = {
    settleJob?.cancel()
    tabsInMotion = true
    fingerOnTabDrag = true
    tabDragVelocity.resetTracking()
    webView?.let { saveWebViewIntoTab(it, tabs.active) }
    dragTabStateSaved = true
  }
  val tabDrag = { change: PointerInputChange, delta: Float ->
    tabDragVelocity.addPointerInputChange(change)
    val proposed = tabDragOffsetPx + delta
    val direction = if (proposed < 0f) 1 else -1
    // Past the last tab there is the launcher, and when it is hosting this screen it moves itself
    // rather than being impersonated. This screen then stays exactly where it is: the host slides
    // it, so moving here as well would be the same journey made twice.
    val leavingForHost =
      onLauncherDrag != null &&
        direction == 1 &&
        launcherIsNextNeighbour &&
        tabs.adjacent(1) == null &&
        tabDragOffsetPx <= 0f
    if (leavingForHost) {
      launcherDragActive = true
      onLauncherDrag.invoke(delta)
    } else {
      val hasNeighbour =
        tabs.adjacent(direction) != null || (direction == 1 && launcherIsNextNeighbour)
      tabDragOffsetPx += if (hasNeighbour) delta else delta * 0.16f
    }
  }
  val tabDragEnd = {
    fingerOnTabDrag = false
    if (launcherDragActive) {
      launcherDragActive = false
      tabsInMotion = false
      dragTabStateSaved = false
      onLauncherDragEnd?.invoke(tabDragVelocity.calculateVelocity().x)
      Unit
    } else {
      val startOffset = tabDragOffsetPx
      val direction = if (startOffset < 0f) 1 else -1
      val farEnough =
        shouldCommitTabSwipe(
          offsetPx = startOffset,
          velocityPxPerSecond = tabDragVelocity.calculateVelocity().x,
          viewportWidthPx = viewportWidthPx,
          commitFraction = TAB_COMMIT_FRACTION,
          commitDistanceCapPx = with(density) { TAB_COMMIT_MAX_DISTANCE.toPx() },
          flingVelocityPx = with(density) { TAB_FLING_VELOCITY.toPx() },
        )
      val commit = tabs.adjacent(direction) != null && farEnough
      val toLauncher = direction == 1 && launcherIsNextNeighbour && farEnough
      if (commit) {
        // Switch the model immediately and rebase the offset so the new active tab keeps its
        // current on-screen position; the next swipe can start right away instead of waiting
        // for the settle animation.
        activateTab(tabs.activeIndex + direction)
        tabDragOffsetPx = startOffset + direction * viewportWidthPx
      } else {
        dragTabStateSaved = false
      }
      var launcherStarted = false
      settleJob =
        coroutineScope.launch {
          animate(
            // Leaving for the launcher carries on off the edge instead of settling back, and the
            // offset stays there until this activity stops, so no frame of the browser shows
            // between the swipe and the launcher taking over.
            initialValue = tabDragOffsetPx,
            targetValue = if (toLauncher) -viewportWidthPx.toFloat() else 0f,
            animationSpec =
              spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
          ) { value, _ ->
            tabDragOffsetPx = value
            // Started before the slide finishes rather than after it, so the launcher spends the
            // tail of the animation waking up, focusing its field and asking for the keyboard.
            // Doing
            // it at the end meant the swipe landed on a bare wallpaper and everything else — bar,
            // keyboard — arrived visibly later. Waiting until most of the travel is done keeps the
            // jump small on the occasions the launcher is ready immediately.
            if (
              toLauncher &&
                !launcherStarted &&
                abs(value) >= viewportWidthPx * LAUNCHER_HANDOVER_FRACTION
            ) {
              launcherStarted = true
              returnToLauncher()
            }
          }
          tabsInMotion = false
          if (toLauncher && !launcherStarted) returnToLauncher()
        }
    }
  }

  // The chrome bar is the same on every tab, so instead of following the post-commit rebased
  // offset (which would jump it across the screen) it wraps around: slides out one side and
  // back in from the other.
  val chromeDragOffsetPx =
    when {
      // On the way to the launcher there is no bar arriving from the other side to wrap into, so
      // the browser's own leaves with the page and the whole screen travels as one.
      launcherIsNextNeighbour && tabDragOffsetPx < 0f -> tabDragOffsetPx
      tabDragOffsetPx > viewportWidthPx / 2f -> tabDragOffsetPx - viewportWidthPx
      tabDragOffsetPx < -viewportWidthPx / 2f -> tabDragOffsetPx + viewportWidthPx
      else -> tabDragOffsetPx
    }

  Box(
    modifier =
      Modifier.fillMaxSize().background(animatedPageBackground).onSizeChanged {
        viewportWidthPx = it.width
        viewportHeightPx = it.height
      }
  ) {
    // Standing in for the launcher itself: its wallpaper on its theme background, close enough
    // that the swipe reads as the home screen sliding back in.
    //
    // Still needed when the launcher hosts this screen, though it looks as if it should not be.
    // The real home screen is a sibling of this one, but while the browser is open the host holds
    // it a full screen to the right — so during the drag there is nothing beside the page but this
    // screen's own background, and removing this left exactly that: a colour where the home screen
    // should be, until the swipe finished and it appeared all at once.
    if (onLauncherDrag == null && launcherIsNextNeighbour && tabDragOffsetPx < -0.5f) {
      Box(
        modifier =
          Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).graphicsLayer {
            translationX = tabDragOffsetPx + viewportWidthPx
          }
      ) {
        launcherWallpaperUri?.let { uri ->
          AsyncImage(
            model = uri,
            contentDescription = null,
            // The launcher holds this much back for the keyboard, so its wallpaper stops short of
            // the bottom. Drawing it full-bleed here meant the image visibly shrank the instant the
            // real home screen took over.
            modifier =
              Modifier.fillMaxSize()
                .padding(bottom = with(density) { launcherKeyboardReservePx.toDp() }),
            contentScale = ContentScale.Crop,
          )
        }
      }
    }

    if (adjacentTab != null && abs(tabDragOffsetPx) > 0.5f) {
      Box(
        modifier =
          Modifier.fillMaxSize()
            .statusBarsPadding()
            .windowInsetsPadding(webContentInsets)
            .background(Color(adjacentTab.pageBackgroundArgb))
            .graphicsLayer { translationX = tabDragOffsetPx + adjacentDirection * viewportWidthPx }
      ) {
        adjacentTab.snapshot?.takeUnless(Bitmap::isRecycled)?.let { snapshot ->
          Image(
            bitmap = snapshot.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            // Never stretched: a capture taken at a different height — behind the keyboard, before
            // a rotation — keeps its proportions and leaves the tab's colour showing beneath.
            alignment = Alignment.TopCenter,
            contentScale = ContentScale.FillWidth,
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
              .windowInsetsPadding(webContentInsets)
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
                    // The WebView owns this bitmap and may recycle it, so the tabs overview gets
                    // its own copy to draw from.
                    activeTab.favicon =
                      runCatching { pageIcon.copy(Bitmap.Config.ARGB_8888, false) }.getOrNull()
                    val repository = searchRepository ?: return
                    (context as ComponentActivity).lifecycleScope.launch {
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

                  /**
                   * [allowDrawnFallback] is for the one moment the page is known to have been
                   * painted: only then is there anything to read a colour back off.
                   */
                  private fun refreshPageBackground(view: WebView, allowDrawnFallback: Boolean) {
                    view.evaluateJavascript(PAGE_BACKGROUND_SCRIPT) { result ->
                      val parsed = parseCssColor(result)
                      if (parsed != null) {
                        applyPageBackground(view, parsed)
                      } else if (allowDrawnFallback && view.url != "about:blank") {
                        // The page sets no background of its own, so it is sitting on whichever
                        // canvas Chromium chose for it — and script cannot tell us which, since a
                        // white canvas and a `color-scheme: dark` one both compute to transparent.
                        // Reading it off the painted page is the only way to get this right; the
                        // old guess of white washed the bars around every such dark page white.
                        // Null means no colour dominates the page, and the current one stands.
                        sampleDrawnBackgroundColor(view)?.let { applyPageBackground(view, it) }
                      }
                    }
                  }

                  override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                    activeTab.url = url
                    activeTab.pageDrawn = false
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
                    val restoring = suppressCommitVisibleColor
                    if (restoring) {
                      suppressCommitVisibleColor = false
                    } else {
                      refreshPageBackground(view, allowDrawnFallback = false)
                    }
                    // Apply as soon as the document is usable so responsive CSS doesn't briefly
                    // lay out at the phone width before onPageFinished.
                    view.applyDesktopViewport(activeTab.desktopMode)
                    val needsSnapshot = activeTab.snapshot == null
                    coroutineScope.launch {
                      // The commit means the paint is ready, not that it has landed: read or
                      // capture on the callback itself and you get the frame before the page.
                      withFrameNanos {}
                      withFrameNanos {}
                      if (webView !== view) return@launch
                      // First paint is the earliest the page can be read off the screen, and a
                      // page with no background of its own has nothing else to offer. Waiting for
                      // onPageFinished instead left it sitting on the previous colour for the
                      // whole of the load — the bars around a dark page staying light until it
                      // completed. A restore keeps its persisted colour and re-reads at the end.
                      if (!restoring) refreshPageBackground(view, allowDrawnFallback = true)
                      // Unconditionally, because this is simply the truth as of this frame: the
                      // page has been painted. onPageStarted clears it on every navigation and
                      // only a completed load used to restore it, so a tab whose page renders but
                      // never finishes — an SPA holding a connection open, a stalled third-party
                      // script — sat at false indefinitely, and captureTabSnapshot refuses every
                      // capture while it is. That is what left previews showing the page before
                      // last: even the one taken on the way out at ON_PAUSE was dropped.
                      activeTab.pageDrawn = true
                      // A tab with no preview at all has nothing to cover its WebView when the
                      // user comes back to it, so it reloads in full view — the white flash. First
                      // paint fills that gap; the fully-loaded capture in onPageFinished still
                      // replaces it with a better one.
                      if (needsSnapshot) captureTabSnapshot(view, activeTab)
                    }
                  }

                  override fun onPageFinished(view: WebView, url: String) {
                    // Styled pages settle their colour here, off CSS alone. Pages with no
                    // background of their own need a painted frame to read one off, so they wait
                    // for the visual state callback below rather than being guessed at now.
                    refreshPageBackground(view, allowDrawnFallback = false)
                    activeTab.url = url
                    activeTab.title = view.title
                    // Sites (and late-injected tags) can rewrite the viewport during load;
                    // re-assert
                    // the desktop width after the document is complete.
                    view.applyDesktopViewport(activeTab.desktopMode)
                    // Keep the snapshot up until the fully loaded page (CSS and fonts included)
                    // has actually been drawn, so tab switches never flash a half-styled page.
                    view.postVisualStateCallback(
                      0,
                      object : WebView.VisualStateCallback() {
                        override fun onComplete(requestId: Long) {
                          // Set here rather than below because everything below can be skipped:
                          // withFrameNanos never resumes while the browser is in the background,
                          // and the guard drops the rest if the user switches tab first. Leaving
                          // this false in those cases silently rejected every later capture of the
                          // tab, so its preview froze on whatever it had.
                          activeTab.pageDrawn = true
                          // The callback means the page is *ready* to be drawn, not that it has
                          // been. Acting on it directly uncovers the WebView while it still shows
                          // its blank background — the white flash between the outgoing snapshot
                          // and content identical to it — and captures that blank frame as the new
                          // preview. Letting a couple of frames pass first means both the uncover
                          // and the capture see the real page.
                          coroutineScope.launch {
                            withFrameNanos {}
                            withFrameNanos {}
                            // Bail out if the tab was switched away, or this WebView released, in
                            // the meantime: neither the cover nor the preview is ours any more.
                            if (webView !== view) return@launch
                            // The page is on screen now, so a page that gave CSS nothing to go on
                            // can finally have its colour read off the pixels it actually drew.
                            refreshPageBackground(view, allowDrawnFallback = true)
                            captureTabSnapshot(view, activeTab)
                            restoringSnapshot = null
                          }
                        }
                      },
                    )
                    if (
                      searchRepository != null &&
                        (url.startsWith("https://") || url.startsWith("http://"))
                    ) {
                      (context as ComponentActivity).lifecycleScope.launch {
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
              // A rebuilt WebView starts blank whatever the tab last managed to paint.
              activeTab.pageDrawn = false
              webView = this
              val restored = activeTab.webViewState?.let { restoreState(it) } != null
              suppressCommitVisibleColor = restored
              setDesktopMode(activeTab.desktopMode, phoneUserAgent)
              if (!restored) loadUrl(activeTab.url)
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
          .windowInsetsPadding(webContentInsets)
          .graphicsLayer { translationX = tabDragOffsetPx },
      // Never fades in: this exists to hide a WebView that has not drawn yet, so easing it in
      // just means watching the blank page through it.
      enter = EnterTransition.None,
      // Short, because by now the page underneath is drawn and identical: this only has to cover
      // the seam, and a slow dissolve would draw attention to a swap nobody should notice.
      exit = fadeOut(tween(durationMillis = 120)),
    ) {
      restoringSnapshot?.takeUnless(Bitmap::isRecycled)?.let { snapshot ->
        // Backed by the page colour, because this is here to hide a WebView that has not drawn: a
        // capture shorter than the screen must not leave a strip of blank page showing under it.
        Box(modifier = Modifier.fillMaxSize().background(animatedPageBackground)) {
          Image(
            bitmap = snapshot.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            alignment = Alignment.TopCenter,
            contentScale = ContentScale.FillWidth,
          )
        }
      }
    }

    if (progress in 1..99) {
      LinearProgressIndicator(
        progress = { progress / 100f },
        modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter).statusBarsPadding(),
      )
    }

    if (tabsOverviewRendered) {
      BrowserTabsOverviewLayer(
        tabs = tabs.items,
        activeIndex = tabs.activeIndex,
        progress = { overviewProgress },
        scrimColor = animatedPageBackground,
        contentColor = chromeBarContentColor,
        cardWidth = with(density) { (viewportWidthPx * TAB_CARD_WIDTH_FRACTION).toDp() },
        // Previews are captured from the WebView, which is inset by the status bar and the
        // chrome; matching that here keeps the card an undistorted miniature of the page.
        previewAspectRatio =
          viewportWidthPx.toFloat() /
            (viewportHeightPx - statusBarTopPx - chromeHeightPx).coerceAtLeast(1),
        maxPreviewHeight =
          with(density) {
            (viewportHeightPx - statusBarTopPx - chromeHeightPx).toDp() - TAB_STRIP_LABEL_HEIGHT
          },
        bottomInset = with(density) { chromeHeightPx.toDp() },
        // Exactly the rect the page occupies, so the card lands where the WebView will draw.
        expandTarget =
          Rect(
            left = 0f,
            top = statusBarTopPx.toFloat(),
            right = viewportWidthPx.toFloat(),
            bottom = (viewportHeightPx - webContentInsets.getBottom(density)).toFloat(),
          ),
        onDismiss = { tabsOverviewOpen = false },
        onSelectStart = ::startTabSelection,
        onSelect = ::selectTabFromOverview,
        onCloseTab = ::closeTabFromOverview,
        onCloseAll = ::closeAllTabs,
      )
    }

    // The outer condition adds/removes the chrome instantly (e.g. around the search overlay) so
    // the bar never plays its own entrance; only the user's hide/reveal gesture animates.
    //
    // A pure cross-fade with the minimal pill, no slide: the two share their trailing icons (see
    // StationaryChromeActions), which are anchored in place, so the bar has to collapse around
    // them rather than travel out from under them.
    if (showLauncherChrome && !showFindInPage)
      AnimatedVisibility(
        visible = !chromeHiddenByUser,
        modifier = Modifier.align(Alignment.BottomCenter),
        enter = fadeIn(),
        exit = fadeOut(),
      ) {
        BrowserLauncherChrome(
          favorites = favorites,
          historyItems = historyItems,
          historyLimit = historyLimit,
          minIconSizeSetting = minIconSizeSetting,
          showFavorites = showFavorites,
          barColor = chromeBarColor,
          barContentColor = chromeBarContentColor,
          onOpenSearch = { onOpenSearch(false, pageBackground.toArgb(), "") },
          onTabDragStart = tabDragStart,
          onTabDrag = tabDrag,
          onTabDragEnd = tabDragEnd,
          onLaunchFavorite = { result ->
            if (result is SearchResult.SearchIntent) {
              onOpenSearch(false, pageBackground.toArgb(), result.trigger + " ")
            } else {
              resultLauncher?.launch(result, reportUsage = !privateMode)
            }
          },
          onToggleFavorite = { result ->
            if (!privateMode) {
              favoritesRepository?.toggleFavorite(result)
            }
          },
          onReorder = { ids ->
            if (!privateMode) {
              favoritesRepository?.updateOrder(ids)
            }
          },
          onHistoryCapacityChanged = { limit ->
            searchRepository?.updateObservedHistoryLimit(limit)
          },
          onHide = { chromeHiddenByUser = true },
          tabsOverviewOpen = tabsOverviewOpen,
          onOpenTabsOverview = ::openTabsOverview,
          onCloseTabsOverview = { tabsOverviewOpen = false },
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
          onSearch = { onOpenSearch(false, pageBackground.toArgb(), "") },
          onTabDragStart = tabDragStart,
          onTabDrag = tabDrag,
          onTabDragEnd = tabDragEnd,
          modifier = Modifier.graphicsLayer { translationX = chromeDragOffsetPx },
        )
      }

    // Drawn once, over both the full bar and the minimal pill, which reserve its space instead of
    // holding their own copies.
    if (showLauncherChrome && !showFindInPage)
      StationaryChromeActions(
        barContentColor = chromeBarContentColor,
        tabCount = tabs.items.size,
        onOpenTabs = { if (tabsOverviewOpen) tabsOverviewOpen = false else openTabsOverview() },
        onVoiceSearch = { onOpenSearch(true, pageBackground.toArgb(), "") },
        overflowMenu = browserOverflowMenu,
        modifier =
          Modifier.align(Alignment.BottomEnd).graphicsLayer { translationX = chromeDragOffsetPx },
      )

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
private fun BrowserLauncherChrome(
  favorites: List<SearchResult>,
  historyItems: List<SearchResult>,
  historyLimit: Int,
  minIconSizeSetting: Int,
  showFavorites: Boolean,
  barColor: Color,
  barContentColor: Color,
  onOpenSearch: () -> Unit,
  onTabDragStart: () -> Unit,
  onTabDrag: (PointerInputChange, Float) -> Unit,
  onTabDragEnd: () -> Unit,
  onLaunchFavorite: (SearchResult) -> Unit,
  onToggleFavorite: (SearchResult) -> Unit,
  onReorder: (List<String>) -> Unit,
  onHistoryCapacityChanged: (Int) -> Unit,
  onHide: () -> Unit,
  tabsOverviewOpen: Boolean,
  onOpenTabsOverview: () -> Unit,
  onCloseTabsOverview: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier =
      modifier
        .fillMaxWidth()
        // Tab-swipe, hide and tabs-overview gestures cover the whole chrome section, favorites
        // row included. Favorites taps and long-press reordering consume their own events first,
        // so they win.
        .pointerInput(
          onHide,
          onTabDragStart,
          onTabDrag,
          onTabDragEnd,
          tabsOverviewOpen,
          onOpenTabsOverview,
          onCloseTabsOverview,
        ) {
          val threshold = 24.dp.toPx()
          var downwardDrag = 0f
          var upwardDrag = 0f
          var horizontalGesture: Boolean? = null

          fun settle() {
            when {
              // While the overview is up the bar is a header, not a tab strip: only the gesture
              // that puts it back down means anything.
              tabsOverviewOpen -> if (downwardDrag >= threshold) onCloseTabsOverview()
              horizontalGesture == true -> onTabDragEnd()
              // Whichever direction the finger travelled furthest in wins, so a wobbly swipe
              // still does the one thing it was mostly aiming at.
              upwardDrag >= threshold && upwardDrag > downwardDrag -> onOpenTabsOverview()
              downwardDrag >= threshold -> onHide()
            }
            downwardDrag = 0f
            upwardDrag = 0f
            horizontalGesture = null
          }

          detectDragGestures(
            onDragStart = {
              downwardDrag = 0f
              upwardDrag = 0f
            },
            onDragEnd = { settle() },
            onDragCancel = { settle() },
            onDrag = { change, dragAmount ->
              // detectDragGestures already waited for touch slop, so the first post-slop delta
              // is enough to classify the gesture. Requiring a second slop distance made short
              // flicks (especially downward hides) go unregistered.
              if (horizontalGesture == null) {
                horizontalGesture = abs(dragAmount.x) > abs(dragAmount.y)
                if (horizontalGesture == true && !tabsOverviewOpen) onTabDragStart()
              }
              if (horizontalGesture == true && !tabsOverviewOpen) {
                onTabDrag(change, dragAmount.x)
              } else {
                if (dragAmount.y > 0f) downwardDrag += dragAmount.y else upwardDrag -= dragAmount.y
              }
              change.consume()
            },
          )
        }
        .navigationBarsPadding()
        .padding(top = 8.dp, bottom = 4.dp)
  ) {
    AnimatedVisibility(
      visible = showFavorites && (favorites.isNotEmpty() || historyItems.isNotEmpty()),
      enter = expandVertically() + fadeIn(),
      exit = shrinkVertically() + fadeOut(),
    ) {
      Column {
        FavoritesRow(
          favorites = favorites,
          history = historyItems,
          historyLimit = historyLimit,
          minIconSizeSetting = minIconSizeSetting,
          onLaunch = onLaunchFavorite,
          onToggleFavorite = onToggleFavorite,
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
      // Space for the mic and menu, which StationaryChromeActions draws on top of this bar.
      Spacer(modifier = Modifier.width(CHROME_ACTIONS_WIDTH))
    }
  }
}

@Composable
private fun RevealBrowserChromeHandle(
  barColor: Color,
  barContentColor: Color,
  onReveal: () -> Unit,
  onSearch: () -> Unit,
  onTabDragStart: () -> Unit,
  onTabDrag: (PointerInputChange, Float) -> Unit,
  onTabDragEnd: () -> Unit,
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
                  onTabDrag(change, dragAmount.x)
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
        // Space for the mic and menu, which StationaryChromeActions draws on top of this pill.
        Spacer(modifier = Modifier.width(CHROME_ACTIONS_WIDTH))
      }
    }
  }
}

/**
 * The mic and overflow buttons, drawn once over whichever chrome is showing.
 *
 * They occupy the identical spot in the full bar and in the minimal pill, so keeping a copy inside
 * each meant that swapping one for the other animated two identical icons out and back in — they
 * appeared to slide away for no reason. Anchored here instead, they simply stay put while the bar
 * behind them collapses or expands. Both bars reserve [CHROME_ACTIONS_WIDTH] for them, and the
 * padding below mirrors BrowserLauncherChrome's own so the icons land exactly where the bars would
 * have drawn them.
 */
@Composable
private fun StationaryChromeActions(
  barContentColor: Color,
  tabCount: Int,
  onOpenTabs: () -> Unit,
  onVoiceSearch: () -> Unit,
  overflowMenu: @Composable () -> Unit,
  modifier: Modifier = Modifier,
) {
  Box(modifier = modifier.navigationBarsPadding().padding(bottom = 4.dp, end = 32.dp)) {
    CompositionLocalProvider(LocalContentColor provides barContentColor) {
      Row(
        modifier = Modifier.heightIn(min = 40.dp).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        IconButton(onClick = onVoiceSearch, modifier = Modifier.size(32.dp).padding(4.dp)) {
          Icon(
            imageVector = Icons.Default.Mic,
            contentDescription = "Voice Search",
            tint = LocalContentColor.current,
          )
        }
        BrowserTabsButton(tabCount = tabCount, onClick = onOpenTabs)
        overflowMenu()
      }
    }
  }
}

/** Width of the mic, tab counter and overflow buttons together, reserved by both chrome layouts. */
private val CHROME_ACTIONS_WIDTH = 96.dp

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

/** Below this the page would be clamped back up by the default floor and overflow again. */
private const val DEFAULT_MINIMUM_SCALE = 0.25f

/**
 * The zoom that fits a [width]-CSS-px desktop layout into a viewport [viewportWidthPx] device px
 * across at [density]. Never above 1: a screen already wide enough shows the page at its own size
 * rather than magnifying it.
 */
internal fun desktopViewportScale(
  viewportWidthPx: Int,
  density: Float,
  width: Int = DESKTOP_VIEWPORT_WIDTH,
): Float {
  if (viewportWidthPx <= 0 || density <= 0f || width <= 0) return 1f
  return ((viewportWidthPx / density) / width).coerceAtMost(1f)
}

/**
 * Carries a scale as well as a width, because the two settings that would otherwise supply it —
 * `useWideViewPort` and `loadWithOverviewMode` — only zoom to fit at page load, and this tag is
 * rewritten afterwards. Without it the page lays out 980 px wide and stays at 1:1, leaving the user
 * on the top-left corner of a page they asked to see the whole of.
 */
internal fun desktopViewportMetaContent(
  width: Int = DESKTOP_VIEWPORT_WIDTH,
  scale: Float = 1f,
): String {
  val fit = (scale * 1000).roundToInt() / 1000f
  // Lowered only when the fit is under the default floor, so wide screens keep the usual room to
  // pinch out. Pinching in is unaffected either way.
  val minimum = minOf(fit, DEFAULT_MINIMUM_SCALE)
  return "width=$width, initial-scale=$fit, minimum-scale=$minimum"
}

/**
 * Force a desktop CSS viewport on small devices. UA spoofing alone is not enough: responsive sites
 * with `width=device-width` still match phone media queries. WebView has no API to ignore viewport
 * meta like Chrome RDS, so we rewrite (or create) the tag to a fixed desktop width, zoomed to fit.
 */
private fun WebView.applyDesktopViewport(enabled: Boolean) {
  if (!enabled) return
  val scale = desktopViewportScale(width, resources.displayMetrics.density)
  evaluateJavascript(desktopViewportScript(desktopViewportMetaContent(scale = scale)), null)
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

// Wide enough that a preview is readable, narrow enough that the neighbouring tabs peek in and
// advertise that the strip scrolls.
internal const val TAB_CARD_WIDTH_FRACTION = 0.42f

/** Room the strip's own header, title and address lines take beside a preview. */
internal val TAB_STRIP_LABEL_HEIGHT = 100.dp

/**
 * How far a slow drag carries a tab before it switches on release, as a share of the viewport.
 * Proportional so the gesture stays in step with the travel the user can see.
 */
internal const val TAB_COMMIT_FRACTION = 0.18f

/**
 * The ceiling on that distance, and deliberately what [TAB_COMMIT_FRACTION] already works out to on
 * a phone — a 400 dp-wide screen gives 72 dp. So phones keep the gesture they have, and anything
 * wider stops the threshold growing with the glass instead of asking for a reach across a tablet.
 */
internal val TAB_COMMIT_MAX_DISTANCE = 72.dp

/**
 * Fling speed, in dp per second, past which a swipe switches tabs however short it was. Matches the
 * minimum fling ViewPager and the photo viewers built on it use, so a flick here costs what a flick
 * costs everywhere else on the device.
 */
internal val TAB_FLING_VELOCITY = 400.dp

private const val PAGE_BACKGROUND_SCRIPT =
  """
  (() => {
    const transparent = 'rgba(0, 0, 0, 0)';
    const body = document.body ? getComputedStyle(document.body).backgroundColor : transparent;
    if (body && body !== transparent) return body;
    return getComputedStyle(document.documentElement).backgroundColor;
  })()
  """

private fun desktopViewportScript(metaContent: String) =
  """
  (() => {
    const content = '$metaContent';
    if (!document.documentElement) return;
    let meta = document.querySelector('meta[name="viewport"]');
    if (!meta) {
      meta = document.createElement('meta');
      meta.setAttribute('name', 'viewport');
      (document.head || document.documentElement).appendChild(meta);
    }
    if (meta.getAttribute('content') !== content) {
      meta.setAttribute('content', content);
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
