package com.searchlauncher.app.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.SpeechRecognizer
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.edit
import com.searchlauncher.app.data.Prefs
import com.searchlauncher.app.data.SearchIconGenerator
import com.searchlauncher.app.data.SearchOptions
import com.searchlauncher.app.data.SearchRepository
import com.searchlauncher.app.data.SearchResult
import com.searchlauncher.app.data.SearchShortcut
import com.searchlauncher.app.data.favoriteKey
import com.searchlauncher.app.data.isFavoritable
import com.searchlauncher.app.ui.browser.BrowserActivity
import com.searchlauncher.app.ui.browser.BrowserTab
import com.searchlauncher.app.ui.browser.BrowserTabStore
import com.searchlauncher.app.ui.browser.BrowserTabSwipePreview
import com.searchlauncher.app.ui.browser.BrowserTabTasks
import com.searchlauncher.app.ui.browser.BrowserTabs
import com.searchlauncher.app.ui.browser.BrowserTabsButton
import com.searchlauncher.app.ui.browser.BrowserTabsOverviewLayer
import com.searchlauncher.app.ui.browser.TAB_CARD_WIDTH_FRACTION
import com.searchlauncher.app.ui.browser.TAB_STRIP_LABEL_HEIGHT
import com.searchlauncher.app.ui.browser.browserDestination
import com.searchlauncher.app.ui.browser.browserTabSwipe
import com.searchlauncher.app.ui.browser.indexOfTabShowing
import com.searchlauncher.app.ui.browser.rememberBrowserTabSwipeState
import com.searchlauncher.app.ui.components.BookmarkDialog
import com.searchlauncher.app.ui.components.ConsentDialog
import com.searchlauncher.app.ui.components.FAVORITES_MAX_ROWS_AUTO
import com.searchlauncher.app.ui.components.FavoritesRow
import com.searchlauncher.app.ui.components.PrivacyPolicyDialog
import com.searchlauncher.app.ui.components.ResultMenuActions
import com.searchlauncher.app.ui.components.SearchChromeBar
import com.searchlauncher.app.ui.components.SearchResultItem
import com.searchlauncher.app.ui.components.ShortcutDialog
import com.searchlauncher.app.ui.components.SnippetDialog
import com.searchlauncher.app.ui.components.WallpaperBackground
import com.searchlauncher.app.ui.components.loadPrivacyPolicyText
import com.searchlauncher.app.ui.onboarding.OnboardingManager
import com.searchlauncher.app.ui.onboarding.OnboardingStep
import com.searchlauncher.app.ui.onboarding.TutorialOverlay
import com.searchlauncher.app.ui.theme.SearchLauncherTheme
import com.searchlauncher.app.util.MathEvaluator
import com.searchlauncher.app.util.SystemUtils
import com.searchlauncher.app.util.traceSection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@Composable
fun SearchScreen(
  query: String,
  onQueryChange: (String) -> Unit,
  onDismiss: () -> Unit,
  onOpenSettings: () -> Unit,
  onOpenAppDrawer: () -> Unit,
  searchRepository: SearchRepository,
  focusTrigger: Long = 0L,
  showBackgroundImage: Boolean = false,
  folderImages: List<Uri> = emptyList(),
  lastImageUriString: String? = null,
  savedUriResolved: Boolean = true,
  onAddWidget: () -> Unit = {},
  isActive: Boolean = true,
  privateWebResults: Boolean = false,
  startVoiceSearchOnOpen: Boolean = false,
  fixedHint: String? = null,
  onOpenBrowserContext: (() -> Unit)? = null,
  /** Set when this screen is the search overlay of a browser tab's window; that tab's id. */
  browserTabId: Long? = null,
  chromeBarColor: Color? = null,
  /** Home screen only: drag the chrome bar sideways to pull the newest browser tab back in. */
  browserTabSwipeEnabled: Boolean = false,
  /** Moves only on a real home intent; [focusTrigger] also moves on any return of focus. */
  homeTrigger: Long = 0L,
  /**
   * Let the chrome bar rise with the keyboard instead of reserving its height up front. Right for
   * the search overlay, which opens as the keyboard comes up; the home screen keeps the space
   * reserved so its layout doesn't shift underneath the user.
   */
  riseWithKeyboard: Boolean = false,
) {
  var searchResults by remember { mutableStateOf<List<SearchResult>>(emptyList()) }
  var keyboardSelectedIndex by remember { mutableIntStateOf(0) }
  var isLoading by remember { mutableStateOf(false) }
  var isFallbackMode by remember { mutableStateOf(false) }
  val context = LocalContext.current
  val inPip = (context as? PipCapable)?.inPictureInPicture == true
  val app = context.applicationContext as com.searchlauncher.app.SearchLauncherApp
  val scope = rememberCoroutineScope()
  val focusRequester = remember { FocusRequester() }
  val favoriteIds by app.favoritesRepository.favoriteIds.collectAsState()
  val searchOptionIds by app.favoritesRepository.searchOptionIds.collectAsState()
  val isIndexing by searchRepository.isIndexing.collectAsState(initial = false)
  val privateSpaceSnapshot by searchRepository.privateSpace.snapshot.collectAsState()

  val favorites by searchRepository.favorites.collectAsState()
  var showSnippetDialog by remember { mutableStateOf(false) }
  var snippetEditMode by remember { mutableStateOf(false) }
  var snippetItemToEdit by remember { mutableStateOf<SearchResult.Snippet?>(null) }
  var snippetInitialContent by remember { mutableStateOf("") }
  var showResetConfirmation by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }
  var showConsentDialog by remember { mutableStateOf(!app.hasAskedForConsent()) }
  var showDefaultLauncherDialog by remember { mutableStateOf(false) }
  var showPrivacyPolicy by remember { mutableStateOf(false) }
  // Offered, not merely defined: a shortcut whose app is gone is not a search option.
  val searchShortcuts by app.searchShortcutRepository.launchable.collectAsState()
  val iconGenerator = remember { SearchIconGenerator(context) }
  // Re-read after every launch so the fill slots below reflect the count that tap just bumped.
  val usageRevision by searchRepository.usageRevision.collectAsState()
  val (searchOptionFavorites, searchOptionExtras) =
    remember(searchShortcuts, searchOptionIds, usageRevision) {
      val (favored, extras) = SearchOptions.partition(searchShortcuts, searchOptionIds)
      fun intents(list: List<SearchShortcut>) =
        list.map { it.toSearchIntent(iconGenerator.getColoredSearchIcon(it.color, it.alias)) }
      // Same usage order as the shortcut results appended below the query. [favored] stays in
      // drag order; only the fill slots move when a shortcut is used.
      val ranked =
        SearchOptions.byUsage(extras) {
          searchRepository.globalUsage(SearchOptions.NAMESPACE, it.id)
        }
      intents(favored) to intents(ranked)
    }
  var showShortcutDialog by remember { mutableStateOf(false) }
  var bookmarkDialogTarget by remember { mutableStateOf<BookmarkDialogTarget?>(null) }
  // Bumped whenever something a result was built from changes — a bookmark's title, a tab being
  // closed — so the visible list catches up without the user having to clear their query.
  var resultsRefreshTick by remember { mutableIntStateOf(0) }
  var editingShortcut by remember {
    mutableStateOf<com.searchlauncher.app.data.SearchShortcut?>(null)
  }
  val listState = androidx.compose.foundation.lazy.rememberLazyListState()
  val rawHistoryItems by searchRepository.recentItems.collectAsState()
  val historyLimit by
    remember { context.dataStore.data.map { it[PreferencesKeys.HISTORY_LIMIT] ?: -1 } }
      .collectAsState(initial = -1)
  // "Autocomplete suggestions" setting. Gates the network fetch of query suggestions while typing
  // a shortcut search (e.g. "g cats"). Stored under SEARCH_SHORTCUTS_ENABLED for historical
  // reasons.
  val suggestionsEnabled by
    remember {
        context.dataStore.data.map { it[PreferencesKeys.SEARCH_SHORTCUTS_ENABLED] ?: false }
      }
      .collectAsState(initial = false)
  val minIconSizeSetting by
    remember { MinIconSize.flow(context) }.collectAsState(initial = MinIconSize.cached(context))
  val favoritesMaxRows by
    remember {
        context.dataStore.data.map {
          it[PreferencesKeys.FAVORITES_MAX_ROWS] ?: FAVORITES_MAX_ROWS_AUTO
        }
      }
      .collectAsState(initial = FAVORITES_MAX_ROWS_AUTO)
  val autocorrectEnabled by
    remember { context.dataStore.data.map { it[PreferencesKeys.SEARCH_AUTOCORRECT] ?: false } }
      .collectAsState(initial = false)
  val defaultSearchEngineId by
    remember {
        context.dataStore.data.map { it[PreferencesKeys.DEFAULT_SEARCH_ENGINE] ?: "google" }
      }
      .collectAsState(initial = "google")

  // Sync back to the boot cache so the next cold start renders at this size immediately.
  LaunchedEffect(minIconSizeSetting) { MinIconSize.updateCache(context, minIconSizeSetting) }

  val historyItems =
    remember(rawHistoryItems, favoriteIds, historyLimit) {
      if (historyLimit == 0) emptyList()
      else {
        val favoriteKeys = favoriteIds.toSet()
        val filtered = rawHistoryItems.filter { it.favoriteKey !in favoriteKeys }
        if (historyLimit >= 0) filtered.take(historyLimit) else filtered
      }
    }

  val themeColor by
    remember {
        context.dataStore.data.map { it[PreferencesKeys.THEME_COLOR] ?: 0xFF5E6D4E.toInt() }
      }
      .collectAsState(initial = 0xFF5E6D4E.toInt())
  val themeSaturation by
    remember { context.dataStore.data.map { it[PreferencesKeys.THEME_SATURATION] ?: 50f } }
      .collectAsState(initial = 50f)
  val darkMode by
    remember { context.dataStore.data.map { it[PreferencesKeys.DARK_MODE] ?: 0 } }
      .collectAsState(initial = 0)
  val isOled by
    remember { context.dataStore.data.map { it[PreferencesKeys.OLED_MODE] ?: false } }
      .collectAsState(initial = false)
  val showWidgetsSetting by
    remember { context.dataStore.data.map { it[PreferencesKeys.SHOW_WIDGETS] ?: true } }
      .collectAsState(initial = true)

  // Check if this app is the default launcher (needed by onboarding logic below)
  val isDefaultLauncher = remember {
    val intent = Intent(Intent.ACTION_MAIN)
    intent.addCategory(Intent.CATEGORY_HOME)
    val resolveInfo = context.packageManager.resolveActivity(intent, 0)
    resolveInfo?.activityInfo?.packageName == context.packageName
  }

  // Onboarding Logic
  val onboardingManager = remember { OnboardingManager(context) }

  val browserTabSwipe = rememberBrowserTabSwipeState()
  var openingTab by remember { mutableStateOf(false) }
  /** The tab an overview card is growing into, opened once the growth lands. */
  var pendingOverviewTab by remember { mutableStateOf<BrowserTab?>(null) }

  val view = LocalView.current
  val focusManager = androidx.compose.ui.platform.LocalFocusManager.current

  // Use InputMethodManager for more reliable keyboard control
  val imm = remember {
    context.getSystemService(Context.INPUT_METHOD_SERVICE)
      as android.view.inputmethod.InputMethodManager
  }

  fun retractKeyboardForTab() {
    openingTab = true
    // Focus goes with it. Asking only the keyboard to leave was right when the browser was another
    // activity — the launcher was on its way to the background and the field's focus went with it.
    // Hosted, the launcher never leaves, and this window is set to show the IME whenever it is
    // resumed: a focused field is then all it takes for the keyboard to come straight back, which
    // is what happened when a result was picked from the in-tab search and the page opened with the
    // keyboard still up.
    focusManager.clearFocus()
    // Straight to the IMM like the rest of this screen's keyboard handling: the field keeps focus
    // (the launcher is still the foreground app until the browser arrives), so only the keyboard
    // itself is asked to go.
    imm.hideSoftInputFromWindow(view.windowToken, 0)
  }

  // Every browser tab is a window of its own, so that the system's app switcher lists tabs the way
  // it lists apps — see [BrowserTabTasks]. The launcher's part is the way in: the swipe below moves
  // the tab's preview across the screen, and its window opens onto that image with no transition of
  // its own, so the handover between the two tasks is not something the user has to see.
  //
  // The tabs were briefly hosted in this composition instead, which made the movement seamless but
  // put every page inside the launcher's own home task — the one task the app switcher never shows.
  // That is the trade this makes the other way.
  val shortcutHost = context as? KeyShortcutHost
  val searchShortcutHandler = rememberUpdatedState { event: android.view.KeyEvent ->
    when {
      KeyShortcuts.matches(event, android.view.KeyEvent.KEYCODE_ESCAPE) -> {
        if (query.isNotEmpty()) onQueryChange("") else onDismiss()
        true
      }
      KeyShortcuts.matches(event, android.view.KeyEvent.KEYCODE_L, ctrl = true) ||
        KeyShortcuts.matches(event, android.view.KeyEvent.KEYCODE_K, ctrl = true) ||
        KeyShortcuts.matches(event, android.view.KeyEvent.KEYCODE_F, ctrl = true) -> {
        focusRequester.requestFocus()
        true
      }
      searchResults.isNotEmpty() &&
        KeyShortcuts.matches(event, android.view.KeyEvent.KEYCODE_DPAD_UP) -> {
        // reverseLayout: index 0 sits next to the search bar, higher indices are above it.
        keyboardSelectedIndex = (keyboardSelectedIndex + 1).coerceAtMost(searchResults.lastIndex)
        scope.launch { listState.scrollToItem(keyboardSelectedIndex) }
        true
      }
      searchResults.isNotEmpty() &&
        KeyShortcuts.matches(event, android.view.KeyEvent.KEYCODE_DPAD_DOWN) -> {
        keyboardSelectedIndex = (keyboardSelectedIndex - 1).coerceAtLeast(0)
        scope.launch { listState.scrollToItem(keyboardSelectedIndex) }
        true
      }
      else -> false
    }
  }
  DisposableEffect(shortcutHost) {
    shortcutHost?.keyShortcutHandler = { searchShortcutHandler.value(it) }
    onDispose { shortcutHost?.keyShortcutHandler = null }
  }
  /**
   * Whether a tab's preview is on screen to any degree, part way in under a finger or fully across
   * with its window on the way. Derived so that a swipe, which moves the offset every frame, only
   * wakes anything watching this when the answer actually changes.
   */
  val browserShowing by remember { derivedStateOf { browserTabSwipe.offsetPx > 0.5f } }

  /**
   * Brings the browser across from the left, which is where the launcher keeps it and the direction
   * the swipe already pulls it in from. Driven by the swipe's own offset so that arriving by tap
   * and arriving by gesture are the same movement, and so that a tap landing mid-swipe carries on
   * from wherever the finger left it rather than restarting.
   */
  var tabsOverviewOpen by remember { mutableStateOf(false) }
  var tabsOverviewRendered by remember { mutableStateOf(false) }

  /**
   * Brings [tab] in from the left — the side the launcher keeps its tabs on, and the side the swipe
   * already pulls them in from — and opens its window onto the image at the end of the movement.
   *
   * The preview and the real page are the same picture in the same place, and the window opens
   * without a transition of its own, so the tab arrives by one continuous movement rather than by
   * the system's own animation for a new app coming in from the right.
   */
  fun slideBrowserIn(tab: BrowserTab?) {
    if (tab == null) return
    // The keyboard goes as the tab arrives, not once it has: this also sets openingTab, which is
    // what stops the request loop below from asking for it back mid-slide.
    retractKeyboardForTab()
    browserTabSwipe.tab = tab
    scope.launch {
      animate(
        initialValue = browserTabSwipe.offsetPx,
        targetValue = browserTabSwipe.viewportWidthPx.toFloat(),
        animationSpec =
          spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
      ) { value, _ ->
        browserTabSwipe.offsetPx = value
      }
      BrowserTabTasks.open(context, tab.id)
    }
  }

  /**
   * The overview's own card carries the movement here — it grows into the page — so the browser
   * only has to be ready underneath by the time it lands. Started when the card starts rather than
   * when it finishes, which is what left the bar arriving a beat after the screen it belongs to.
   */
  fun prepareBrowserForOverviewTab(index: Int) {
    retractKeyboardForTab()
    pendingOverviewTab = BrowserTabStore.tabs?.items?.getOrNull(index)
  }

  /** The card has landed on it; nothing left to animate. */
  fun revealBrowserFromOverview() {
    // Taken down rather than faded. Closing the strip normally animates it away, which is right
    // when it is being dismissed back to the home screen — but here the card has just become the
    // page, and animating the strip out on top of that page leaves its card labels sliding about
    // in the gap above the search bar, reading as some other window moving behind the new one.
    // The growth was the transition; there is nothing left for the strip to do but go.
    tabsOverviewRendered = false
    tabsOverviewOpen = false
    val tab = pendingOverviewTab ?: return
    pendingOverviewTab = null
    // The card has already grown into the page's place, so the preview simply stands where it
    // landed while the tab's own window opens onto it.
    browserTabSwipe.tab = tab
    browserTabSwipe.offsetPx = browserTabSwipe.viewportWidthPx.toFloat()
    BrowserTabTasks.open(context, tab.id)
  }

  /**
   * Sends the browser back where it came from: off to the left, with the home screen arriving from
   * the right. The mirror of [slideBrowserIn], and the reason both are written in terms of the same
   * offset — a gesture and a button press should leave by the same door.
   */
  fun openBrowserTab(index: Int) {
    tabsOverviewOpen = false
    slideBrowserIn(BrowserTabStore.tabs?.items?.getOrNull(index))
  }

  /** Opens [url] in the tab already showing it, or in a new tab of its own. */
  fun openInBrowser(url: String) {
    // Only the launcher hosts a browser. Asked from the search overlay — its own translucent
    // activity, with no browser in it — this hands the page to the launcher instead, which used to
    // happen by starting the browser activity and is the last thing that would have brought the
    // task switch back.
    if (!browserTabSwipeEnabled) {
      // No chrome bar here to slide a preview across, so a window is opened outright — the one
      // belonging to the tab that asked, when there is one, so that searching from a blank new tab
      // fills in that tab instead of leaving it behind empty.
      context.startActivity(
        browserTabId?.let { BrowserActivity.createNavigateIntent(context, it, url) }
          ?: BrowserActivity.createIntent(context, url)
      )
      onDismiss()
      return
    }
    // A page already open is somewhere to go back to, not something to open a second copy of —
    // which is what tapping the same favourite twice used to do. Tabs are capped and evict the
    // oldest to make room, so the duplicates cost real tabs, and switching is what tapping an
    // open-tab result already does; a bookmark for the same page should not disagree with it.
    val openTab =
      BrowserTabStore.tabs?.items?.let { items -> indexOfTabShowing(items.map { it.url }, url) }
    if (openTab != null && openTab >= 0) {
      openBrowserTab(openTab)
      return
    }
    slideBrowserIn(
      BrowserTabStore.addBackgroundTab(browserDestination(url)) { evicted ->
        BrowserTabTasks.close(context, evicted.id)
      }
    )
  }

  // One description of what a result can do, built in a single place and handed to every list that
  // shows results. The favourites bar used to assemble its own much shorter menu, so the same app
  // offered a dozen actions in the results and two in the favourites bar; sharing this is what
  // keeps the two honest. [index] is only read for usage reporting, where -1 means "not from the
  // results list, so never the top hit".
  val menuActionsFor: (SearchResult, Int) -> ResultMenuActions = { result, index ->
    val webUrl = webUrlForResult(result, query, searchShortcuts)
    val openTab = result as? SearchResult.BrowserTab
    ResultMenuActions(
      onToggleFavorite =
        if (result.isFavoritable()) {
          {
            app.favoritesRepository.toggleFavorite(result)
            onQueryChange("")
            scope.launch { onboardingManager.markStepComplete(OnboardingStep.AddFavorite) }
          }
        } else null,
      onRemoveBookmark = {
        scope.launch {
          searchRepository.removeBookmark(result.id, result.namespace)
          // Refreshes the list rather than clearing the query: acting on one
          // result should not throw away what the user typed to find it.
          resultsRefreshTick++
        }
      },
      onEditBookmark =
        webUrl?.let { url ->
          { bookmarkDialogTarget = BookmarkDialogTarget(url, result.title, isEditMode = true) }
        },
      onAddBookmark =
        webUrl?.let { url ->
          {
            bookmarkDialogTarget =
              BookmarkDialogTarget(
                url = url,
                title = result.title,
                isEditMode = false,
                replacesHistoryId = result.id.takeIf { result.namespace == "web_bookmarks" },
              )
          }
        }
          ?: openTab?.let { tab ->
            {
              bookmarkDialogTarget =
                BookmarkDialogTarget(url = tab.url, title = tab.title, isEditMode = false)
            }
          },
      onCloseTab =
        openTab?.let { tab ->
          {
            BrowserTabTasks.close(context, tab.tabId)
            if (BrowserTabStore.close(tab.tabId)) closeBrowserWindow(context)
            resultsRefreshTick++
          }
        },
      onCloseAllTabs =
        openTab?.let {
          {
            BrowserTabStore.clear()
            BrowserTabTasks.closeAll(context)
            closeBrowserWindow(context)
            resultsRefreshTick++
          }
        },
      onCopyUrl = openTab?.let { tab -> { copyUrlToClipboard(context, tab.url) } },
      onClearSearchResults = { onQueryChange("") },
      onOpenTab =
        webUrl?.let { url ->
          {
            openInBrowser(url)
            searchRepository.reportUsageAsync(result.namespace, result.id, query, index == 0)
            onDismiss()
          }
        },
      onOpenPrivate =
        webUrl?.let { url ->
          {
            context.startActivity(BrowserActivity.createPrivateIntent(context, url))
            onDismiss()
          }
        },
      onContactChatAction = { contact, action ->
        if (searchRepository.launchContactChatAction(contact, action)) {
          searchRepository.reportUsageAsync(contact.namespace, contact.id, query, index == 0)
          onDismiss()
        } else {
          Toast.makeText(context, "Cannot open ${action.label}", Toast.LENGTH_SHORT).show()
        }
      },
      onEditSnippet =
        if (result is SearchResult.Snippet) {
          {
            snippetItemToEdit = result
            snippetEditMode = true
            showSnippetDialog = true
          }
        } else null,
      onEditShortcut =
        if (result is SearchResult.Shortcut) {
          {
            val shortcut = searchShortcuts.find { it.id == result.id }
            if (shortcut != null) {
              editingShortcut = shortcut
              showShortcutDialog = true
            }
          }
        } else if (result is SearchResult.SearchIntent) {
          {
            val shortcut = searchShortcuts.find { it.alias == result.trigger }
            if (shortcut != null) {
              editingShortcut = shortcut
              showShortcutDialog = true
            }
          }
        } else if (result is SearchResult.Content && result.namespace == "search_shortcuts") {
          {
            val alias = result.id.removePrefix("shortcut_")
            val shortcut = searchShortcuts.find { it.alias == alias }
            if (shortcut != null) {
              editingShortcut = shortcut
              showShortcutDialog = true
            }
          }
        } else null,
      onDeleteShortcut =
        if (result is SearchResult.Shortcut) {
          {
            scope.launch {
              app.searchShortcutRepository.removeShortcut(result.id)
              Toast.makeText(context, "Shortcut removed", Toast.LENGTH_SHORT).show()
              resultsRefreshTick++
            }
          }
        } else if (result is SearchResult.SearchIntent) {
          {
            scope.launch {
              // Find the shortcut first to get its ID
              val shortcut = searchShortcuts.find { it.alias == result.trigger }
              if (shortcut != null) {
                app.searchShortcutRepository.removeShortcut(shortcut.id)
                Toast.makeText(context, "Shortcut removed", Toast.LENGTH_SHORT).show()
                resultsRefreshTick++
              }
            }
          }
        } else if (result is SearchResult.Content && result.namespace == "search_shortcuts") {
          {
            scope.launch {
              val alias = result.id.removePrefix("shortcut_")
              val shortcut = searchShortcuts.find { it.alias == alias }
              if (shortcut != null) {
                app.searchShortcutRepository.removeShortcut(shortcut.id)
                Toast.makeText(context, "Shortcut removed", Toast.LENGTH_SHORT).show()
                resultsRefreshTick++
              }
            }
          }
        } else null,
      onCreateSnippet = {
        snippetEditMode = false
        snippetInitialContent = ""
        showSnippetDialog = true
      },
    )
  }
  val completedSteps by onboardingManager.completedSteps.collectAsState(initial = null)
  val resultLauncher =
    remember(context, searchRepository, scope, onQueryChange, onAddWidget, onboardingManager) {
      ResultLauncher(
        context = context,
        searchRepository = searchRepository,
        scope = scope,
        onQueryChange = onQueryChange,
        onBindWidgetIntent = { intent ->
          val activity = context as? MainActivity
          if (activity != null) {
            activity.handleWidgetIntent(intent)
            true
          } else {
            false
          }
        },
        onAddWidgetSearch = onAddWidget,
        onboardingManager = onboardingManager,
        // Tapping a result was the last way into the browser still going through an activity, which
        // is why it kept arriving from the right while the swipe came from the left.
        onOpenInBrowser = { url -> openInBrowser(url) },
        onOpenBrowserTab = { index -> openBrowserTab(index) },
      )
    }

  // Determine current step using derivedStateOf so that intermediate state changes
  // (e.g. completedSteps updating async from DataStore) only trigger recomposition
  // when the actual computed step changes — avoids brief flashes of wrong steps.
  val currentOnboardingStep by
    remember(query, folderImages, isActive) {
      derivedStateOf {
        if (!isActive) return@derivedStateOf null
        val steps = completedSteps ?: return@derivedStateOf null
        if (query.isNotEmpty()) {
          if (
            !steps.contains(OnboardingStep.AddFavorite) &&
              searchResults.isNotEmpty() &&
              favorites.isEmpty()
          )
            OnboardingStep.AddFavorite
          else null
        } else {
          if (!steps.contains(OnboardingStep.SwipeBackground) && folderImages.size > 1)
            OnboardingStep.SwipeBackground
          else if (!steps.contains(OnboardingStep.SwipeNotifications))
            OnboardingStep.SwipeNotifications
          else if (!steps.contains(OnboardingStep.SwipeQuickSettings))
            OnboardingStep.SwipeQuickSettings
          else if (!steps.contains(OnboardingStep.SwipeAppDrawer)) OnboardingStep.SwipeAppDrawer
          else if (!steps.contains(OnboardingStep.LongPressBackground))
            OnboardingStep.LongPressBackground
          else if (!steps.contains(OnboardingStep.SearchYoutube)) OnboardingStep.SearchYoutube
          else if (!steps.contains(OnboardingStep.SearchGoogle)) OnboardingStep.SearchGoogle
          else if (!steps.contains(OnboardingStep.SetTimer)) OnboardingStep.SetTimer
          else if (!steps.contains(OnboardingStep.ReorderFavorites) && favorites.size >= 2)
            OnboardingStep.ReorderFavorites
          else if (!steps.contains(OnboardingStep.OpenSettings)) OnboardingStep.OpenSettings
          // AddFavorite is situational, shown when search results exist
          else null
        }
      }
    }

  // Effect to mark steps complete based on state
  LaunchedEffect(query, completedSteps) {
    val steps = completedSteps ?: return@LaunchedEffect
    if (
      !steps.contains(OnboardingStep.SearchYoutube) &&
        query.trimStart().startsWith("y ", ignoreCase = true)
    ) {
      onboardingManager.markStepComplete(OnboardingStep.SearchYoutube)
    }

    if (
      !steps.contains(OnboardingStep.SearchGoogle) &&
        query.trimStart().startsWith("g ", ignoreCase = true)
    ) {
      onboardingManager.markStepComplete(OnboardingStep.SearchGoogle)
    }

    if (
      !steps.contains(OnboardingStep.SetTimer) &&
        Regex(
            """^\d+\s*(h|hr|hrs|hour|hours|m|min|mins|minute|minutes|s|sec|secs|second|seconds)(\s+.+)?$""",
            RegexOption.IGNORE_CASE,
          )
          .matches(query.trim())
    ) {
      onboardingManager.markStepComplete(OnboardingStep.SetTimer)
    }
  }

  // Auto-complete steps based on existing state
  LaunchedEffect(favorites, completedSteps) {
    val steps = completedSteps ?: return@LaunchedEffect
    if (favorites.isNotEmpty() && !steps.contains(OnboardingStep.AddFavorite)) {
      onboardingManager.markStepComplete(OnboardingStep.AddFavorite)
    }
    if (favorites.size >= 2 && !steps.contains(OnboardingStep.ReorderFavorites)) {
      onboardingManager.markStepComplete(OnboardingStep.ReorderFavorites)
    }
  }

  LaunchedEffect(searchResults) {
    keyboardSelectedIndex = 0
    if (searchResults.isNotEmpty()) {
      listState.scrollToItem(0)
    }
  }

  // Ask for the keyboard on activation. The IME can only be shown once the window actually
  // holds focus — returning to the home screen often delivers focus late, so wait for it
  // instead of guessing with fixed retries, then keep asking until the IME is really visible.
  val windowInfo = androidx.compose.ui.platform.LocalWindowInfo.current
  LaunchedEffect(isActive, focusTrigger, browserShowing, openingTab) {
    if (isActive && !openingTab && !browserShowing) {
      snapshotFlow { windowInfo.isWindowFocused }.first { it }
      repeat(10) {
        focusRequester.requestFocus()
        imm.showSoftInput(view, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
        kotlinx.coroutines.delay(120)
        val imeVisible =
          androidx.core.view.ViewCompat.getRootWindowInsets(view)
            ?.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime()) == true
        if (imeVisible) return@LaunchedEffect
      }
    }
  }

  // Tapping the field when it is already focused produces no focus change, so nothing would
  // re-open a dismissed keyboard; show it explicitly on every press.
  val searchFieldInteractionSource = remember { MutableInteractionSource() }
  LaunchedEffect(searchFieldInteractionSource) {
    searchFieldInteractionSource.interactions.collect { interaction ->
      if (interaction is androidx.compose.foundation.interaction.PressInteraction.Release) {
        focusRequester.requestFocus()
        imm.showSoftInput(view, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
      }
    }
  }

  LaunchedEffect(query, suggestionsEnabled, isIndexing, resultsRefreshTick, privateSpaceSnapshot) {
    traceSection("SL:SearchScreen.queryEffect") {
      searchRepository.noteInteractiveSearch(query)
      if (query.isEmpty()) {
        searchResults = emptyList()
        isFallbackMode = false
      } else {
        val results =
          traceSection("SL:SearchScreen.searchApps") {
            searchRepository
              .searchApps(
                query,
                limit = LIVE_SEARCH_RESULT_LIMIT,
                includeSuggestions = suggestionsEnabled,
                includeSearchShortcuts = true,
              )
              .getOrElse { emptyList() }
          }
        currentCoroutineContext().ensureActive()

        // Always append search shortcuts to the end of the results
        // Keep this small in the live typing path; richer actions can load after selection.
        val shortcuts =
          traceSection("SL:SearchScreen.getSearchShortcuts") {
            searchRepository.getSearchShortcuts(
              limit =
                if (results.isEmpty()) {
                  FALLBACK_SEARCH_SHORTCUT_LIMIT
                } else {
                  LIVE_SEARCH_SHORTCUT_LIMIT
                }
            )
          }
        currentCoroutineContext().ensureActive()

        val baseResults =
          traceSection("SL:SearchScreen.mergeResults") {
            val defaultActions = listOf(createSnippetFallbackResult(context, query))
            val resultKeys = results.map { it.stableListKey }.toSet()
            val fallbackResults =
              (shortcuts + defaultActions).filter { !resultKeys.contains(it.stableListKey) }
            (results + fallbackResults).distinctBy { it.stableListKey }
          }
        isFallbackMode = results.isEmpty()

        // Only surface the indexing row when there are no live results to show. Background
        // rebuilds keep the previous snapshot searchable and swap when ready.
        val resultsWithIndexing =
          if (isIndexing && baseResults.isEmpty()) {
            listOf(SearchResult.IndexingIndicator()) + baseResults
          } else {
            baseResults
          }

        // Calculator injection, except for the numbers that only parse as sums by accident: a
        // phone number typed with dashes is a subtraction to the evaluator, and answering it puts
        // a meaningless total above the contact that was being looked for.
        if (MathEvaluator.isExpression(query) && !MathEvaluator.looksLikePhoneNumber(query)) {
          val eval = MathEvaluator.evaluate(query)
          if (eval != null) {
            // Round to avoid long decimals if possible, or show as is
            val formattedResult =
              if (eval % 1.0 == 0.0) eval.toLong().toString() else eval.toString()
            val calcResult =
              SearchResult.Content(
                id = "calculator_result",
                namespace = "calculator",
                title = formattedResult,
                subtitle = "Calculation result (Tap to copy)",
                icon =
                  searchRepository.getColoredSearchIcon(themeColor.toLong() and 0xFFFFFFFFL, "="),
                packageName = "android",
                deepLink = "calculator://copy?text=$formattedResult",
              )
            searchResults =
              if (MathEvaluator.isUnambiguouslyArithmetic(query)) {
                // Contacts are indexed on their phone numbers, so a query like "1234*56" drags in
                // whoever happens to share those digits. Nothing but the sum can be meant here.
                listOf(calcResult)
              } else {
                (listOf(calcResult) + resultsWithIndexing).distinctBy { it.stableListKey }
              }
          } else {
            searchResults = resultsWithIndexing
          }
        } else {
          searchResults = resultsWithIndexing
        }
      }
    }
  }

  // Hint Logic
  val snippetItems by app.snippetsRepository.items.collectAsState()

  // Show default launcher dialog on first run if not already the default
  LaunchedEffect(Unit) {
    if (!isDefaultLauncher && !app.hasAskedDefaultLauncher()) {
      showDefaultLauncherDialog = true
    }
  }

  // Check if contacts permission is granted
  val hasContactsPermission = remember {
    context.checkSelfPermission(android.Manifest.permission.READ_CONTACTS) ==
      android.content.pm.PackageManager.PERMISSION_GRANTED
  }

  val hintManager =
    remember(
      folderImages,
      snippetItems,
      isDefaultLauncher,
      hasContactsPermission,
      searchShortcuts,
    ) {
      val shortcutHints =
        searchShortcuts.map { "Type '${it.alias} ' to ${it.description.lowercase()}" }
      HintManager(
        isWallpaperFolderSet = { folderImages.isNotEmpty() },
        isSnippetsSet = { snippetItems.isNotEmpty() },
        isDefaultLauncher = { isDefaultLauncher },
        isContactsAccessGranted = { hasContactsPermission },
        shortcutHints = shortcutHints,
      )
    }
  var currentHint by remember { mutableStateOf("Search apps and content…") }

  LaunchedEffect(hintManager, fixedHint) {
    if (fixedHint != null) currentHint = fixedHint
    else hintManager.getHintsFlow().collect { hint -> currentHint = hint }
  }

  // Use SharedPreferences for synchronous read to avoid initial jump
  val sharedPrefs = remember {
    context.getSharedPreferences(Prefs.Window.FILE, Context.MODE_PRIVATE)
  }
  val density = LocalDensity.current
  val imeHeightPx = WindowInsets.ime.getBottom(density)

  /**
   * Ceiling on what we are willing to believe a keyboard measures.
   *
   * The height is remembered so the bar can hold the keyboard's place before the IME reports
   * itself, but a bogus reading could get in there and stick: the space reserved below is the
   * *larger* of the stored and live values, so anything too big survives every later correction.
   * Some IME windows span nearly the whole screen while only their lower part is keys, and a
   * reading taken from one of those left the home screen squeezed into the top quarter with a black
   * band beneath it until the IME settled and overwrote it.
   */
  val maxPlausibleKeyboardPx = (windowInfo.containerSize.height * 0.5f).toInt()

  // Read synchronously for initial value
  var storedKeyboardHeight by remember {
    mutableStateOf(
      sharedPrefs.getInt(Prefs.Window.KEYBOARD_HEIGHT, 0).coerceAtMost(maxPlausibleKeyboardPx)
    )
  }

  val isMultiWindow = (context as? android.app.Activity)?.isInMultiWindowMode == true

  /**
   * Set the moment a tab is tapped in the overview, which is when the launcher stops holding space
   * for a keyboard it is dismissing. Until then the home screen reserves that height whether the
   * IME is up or not, so releasing it is what lets the chrome bar travel down with the keys rather
   * than sitting in mid-air until the browser finally takes over.
   */
  LaunchedEffect(imeHeightPx) {
    if (imeHeightPx > 100 && imeHeightPx <= maxPlausibleKeyboardPx && !isMultiWindow) {
      // Wait for animation to settle (debounce)
      kotlinx.coroutines.delay(300)
      // If we are still active (didn't get cancelled by new value), save it
      storedKeyboardHeight = imeHeightPx
      sharedPrefs.edit().putInt(Prefs.Window.KEYBOARD_HEIGHT, imeHeightPx).apply()
    }
  }

  // The effective padding is the max of current IME or stored IME height
  // In multi-window/floating mode, we ignore stored height to avoid unnecessary gaps
  val navigationBarBottomPx = WindowInsets.navigationBars.getBottom(density)
  val bottomPadding =
    with(density) {
      when {
        // Tracks the IME inset frame by frame as the keyboard animates in, so the bar travels up
        // with the keys. Reserving the stored height instead would park it at the final position
        // before the keyboard has even started to appear. At rest it lands exactly on the bar it
        // replaces, so the overlay opens without the bar hopping.
        riseWithKeyboard ->
          kotlin.math
            .max(imeHeightPx, navigationBarBottomPx - BROWSER_CHROME_BAR_OFFSET.roundToPx())
            .coerceAtLeast(0)
            .toDp()
        isMultiWindow -> imeHeightPx.toDp()
        // Follows the IME down frame by frame while a tab opens, so the bar rides the keyboard
        // out instead of dropping once it has gone.
        openingTab -> imeHeightPx.toDp()
        else -> kotlin.math.max(imeHeightPx, storedKeyboardHeight).toDp()
      }
    }

  /**
   * What the wallpaper reserves at the bottom, which is not what the chrome bar reserves.
   *
   * The bar follows the keyboard down frame by frame as a tab opens, so that it rides the keyboard
   * out rather than dropping once it has gone. A full-screen picture cannot do that: the same
   * padding shrinking under it resizes the image, and the wallpaper visibly grows during the very
   * transition that is meant to be carrying it off to one side. So it keeps holding the keyboard's
   * room throughout, exactly as it does at rest.
   */
  val wallpaperBottomPadding =
    if (openingTab) {
      with(density) { kotlin.math.max(imeHeightPx, storedKeyboardHeight).toDp() }
    } else {
      bottomPadding
    }

  var chromeBarHeightPx by remember { mutableIntStateOf(0) }
  var favoritesRowHeightPx by remember { mutableIntStateOf(0) }
  val showingSearchOptions = query.isNotBlank()
  val favoritesRowVisible =
    if (showingSearchOptions) {
      searchOptionFavorites.isNotEmpty() || searchOptionExtras.isNotEmpty()
    } else {
      favorites.isNotEmpty() || historyItems.isNotEmpty()
    }
  /** Everything pinned to the bottom of the home screen: the favorites row and the chrome bar. */
  val bottomSectionHeightPx =
    chromeBarHeightPx + if (favoritesRowVisible) favoritesRowHeightPx else 0
  // Tabs overview, opened by the same up-swipe on the chrome bar that opens it in the browser.
  // The tabs are the browser's own live objects, so closing one here closes it there too.
  var overviewTabs by remember { mutableStateOf<BrowserTabs?>(null) }
  // Live: the store holds its tab list in Compose state, so opening or closing a tab in the
  // browser updates this counter without the launcher being touched.
  val openTabCount = BrowserTabStore.tabs?.items?.size ?: 0
  val tabsOverviewProgress by
    animateFloatAsState(
      targetValue = if (tabsOverviewOpen) 1f else 0f,
      animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 380f),
      label = "tabsOverview",
      finishedListener = { settled -> if (settled == 0f) tabsOverviewRendered = false },
    )

  fun openTabsOverview() {
    // Opens even with nothing to show — the strip has an empty state, and a gesture that silently
    // does nothing reads as broken rather than as "no tabs".
    overviewTabs = BrowserTabStore.tabs?.takeIf { it.items.isNotEmpty() }
    tabsOverviewRendered = true
    tabsOverviewOpen = true
  }

  /**
   * Run at the moment a tab is committed to — the tap in the overview, the lift at the end of a
   * swipe — rather than when the animation that follows it lands. Both are handing the screen over
   * to the browser, and from the instant that is decided the keyboard is on its way out; it should
   * be leaving during the animation rather than snapping away once the browser is already up.
   */
  /**
   * [returnHome] when the tabs went because the user threw the last one away, rather than because
   * they asked for all of them to go.
   *
   * Close all is a deliberate bulk action, and leaving the strip up on its empty state confirms it
   * happened instead of dropping the user onto the home screen wondering whether the tap
   * registered. Flicking the last card away is the opposite: it is a gesture for getting rid of
   * something, and what it left behind was a placeholder low on the screen under a near-opaque
   * scrim, which reads as an empty screen rather than as an answer.
   */
  fun forgetAllTabs(returnHome: Boolean = false) {
    BrowserTabStore.clear()
    // Every tab's window goes with its tab, so no card is left in the app switcher promising a
    // page that nothing can bring back.
    BrowserTabTasks.closeAll(context)
    closeBrowserWindow(context)
    overviewTabs = null
    if (!returnHome) return
    tabsOverviewOpen = false
    // The strip can be raised from either side; if it was raised from the browser then the browser
    // is still holding the screen and has to be shown out.
    browserTabSwipe.reset()
  }

  fun closeBrowserTab(index: Int) {
    val stored = overviewTabs ?: return
    // The last tab going away means there is no browser left to return to.
    if (stored.items.size == 1) {
      forgetAllTabs(returnHome = true)
      return
    }
    val closing = stored.items.getOrNull(index)
    stored.close(index)
    closing?.let { BrowserTabTasks.close(context, it.id) }
  }

  BackHandler(enabled = tabsOverviewOpen) { tabsOverviewOpen = false }

  // Pressing home while already on the launcher never stops the activity, so the lifecycle reset
  // below cannot catch it. The launcher bumps this instead whenever a home intent arrives, and
  // "take me home" has to mean a clean home screen, tab strip included.
  LaunchedEffect(focusTrigger) {
    if (focusTrigger != 0L) {
      tabsOverviewOpen = false
      openingTab = false
      // Including the browser, now that it is a screen of this composition rather than another
      // task the system would have switched away from. Without this, home left the page up and
      // merely raised the launcher's own bar and keyboard over the top of it.
    }
  }
  // The preview is deliberately left covering the screen while the browser starts, so it has to be
  // cleared once the launcher is out of sight (or back in front, if the browser never took over).
  val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
  DisposableEffect(lifecycleOwner) {
    val observer =
      androidx.lifecycle.LifecycleEventObserver { _, event ->
        if (
          event == androidx.lifecycle.Lifecycle.Event.ON_STOP ||
            event == androidx.lifecycle.Lifecycle.Event.ON_RESUME
        ) {
          // Clears whatever a swipe left parked: a tab opened at the end of one leaves its preview
          // standing a full screen across so that its window has something to arrive onto, and this
          // is where that is taken down once the launcher is out of sight or back in front.
          browserTabSwipe.reset()
          // A tab's card can be dismissed in the app switcher while the launcher is away, which is
          // the user closing that tab; the surviving windows are the honest account of which tabs
          // are left.
          BrowserTabTasks.forgetDismissedTabs(context)
          // Leaving the launcher ends the overview with it, so coming back later never lands on a
          // tab strip describing whatever the browser was doing minutes ago.
          tabsOverviewOpen = false
          // The tab either opened or it did not; either way the home screen is a home screen again
          // and goes back to holding space for its keyboard.
          openingTab = false
        }
      }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
  }

  SearchLauncherTheme(
    themeColor = themeColor,
    darkThemeMode = darkMode,
    chroma = themeSaturation,
    isOled = isOled,
  ) {
    // Ramped rather than applied outright, so the dim arrives together with the window blur behind
    // it instead of snapping on a frame before it.
    val backdropDim = remember { Animatable(if (chromeBarColor != null) 0f else 1f) }
    LaunchedEffect(chromeBarColor != null) {
      if (chromeBarColor != null) backdropDim.animateTo(1f, tween(durationMillis = 250))
    }

    Box(
      modifier =
        Modifier.fillMaxSize()
          .then(
            // As a browser overlay, dim the page behind so the search UI reads as a layer above
            // it even when the page shares its exact color. Drawn rather than composed so the
            // ramp costs a redraw instead of recomposing this whole screen every frame.
            if (chromeBarColor != null) {
              Modifier.drawBehind {
                drawRect(
                  androidx.compose.ui.graphics.Color.Black,
                  alpha = 0.35f * backdropDim.value,
                )
              }
            } else {
              Modifier
            }
          )
    ) {
      var isListening by remember { mutableStateOf(false) }
      val speechRecognizer: android.speech.SpeechRecognizer = remember {
        SpeechRecognizer.createSpeechRecognizer(context)
      }

      val permissionLauncher =
        androidx.activity.compose.rememberLauncherForActivityResult(
          androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
        ) { isGranted ->
          if (isGranted) {
            try {
              val intent =
                Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                  putExtra(
                    android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
                  )
                  putExtra(android.speech.RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                }
              speechRecognizer.startListening(intent)
              isListening = true
            } catch (e: Exception) {
              Toast.makeText(context, "Voice search error", Toast.LENGTH_SHORT).show()
              isListening = false
            }
          } else {
            Toast.makeText(context, "Permission needed for voice search", Toast.LENGTH_SHORT).show()
          }
        }

      val startOrStopVoiceSearch: () -> Unit = {
        if (isListening) {
          speechRecognizer.stopListening()
          isListening = false
        } else if (
          context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
          try {
            val intent =
              Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                  android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                  android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
                )
                putExtra(android.speech.RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
              }
            speechRecognizer.startListening(intent)
            isListening = true
          } catch (e: Exception) {
            Toast.makeText(context, "Voice search error", Toast.LENGTH_SHORT).show()
            isListening = false
          }
        } else {
          permissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
        }
      }

      var voiceSearchStartedOnOpen by remember { mutableStateOf(false) }
      LaunchedEffect(startVoiceSearchOnOpen) {
        if (startVoiceSearchOnOpen && !voiceSearchStartedOnOpen) {
          voiceSearchStartedOnOpen = true
          startOrStopVoiceSearch()
        }
      }

      DisposableEffect(Unit) {
        val listener: android.speech.RecognitionListener =
          object : android.speech.RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}

            override fun onBeginningOfSpeech() {}

            override fun onRmsChanged(rmsdB: Float) {}

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
              isListening = false
            }

            override fun onError(error: Int) {
              isListening = false
            }

            override fun onResults(results: Bundle?) {
              val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
              if (!matches.isNullOrEmpty()) {
                onQueryChange(matches[0])
              }
              isListening = false
            }

            override fun onPartialResults(partialResults: Bundle?) {
              // Optional: update query in real-time
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
          }
        speechRecognizer.setRecognitionListener(listener)
        onDispose { speechRecognizer.destroy() }
      }

      var currentWallpaperUri by remember { mutableStateOf<Uri?>(null) }
      var showBackgroundMenu by remember { mutableStateOf(false) }
      var menuOffset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }

      val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris
          ->
          if (uris.isNotEmpty()) {
            scope.launch {
              var lastAddedUri: Uri? = null
              uris.forEach { uri ->
                val added = app.wallpaperRepository.addWallpaper(uri)
                if (added != null) lastAddedUri = added
              }
              lastAddedUri?.let { newUri ->
                context.dataStore.edit { prefs ->
                  prefs[PreferencesKeys.BACKGROUND_LAST_IMAGE_URI] = newUri.toString()
                }
              }
            }
          }
        }

      // The wallpaper, widgets, results and chrome bar all ride the tab swipe together, so the
      // launcher leaves as one screen rather than as a hole opening around a floating preview.
      val homeSwipeOffset = Modifier.graphicsLayer { translationX = browserTabSwipe.offsetPx }

      if (!inPip) {
        Box(modifier = Modifier.fillMaxSize().then(homeSwipeOffset)) {
          WallpaperBackground(
            showBackgroundImage = showBackgroundImage,
            bottomPadding = wallpaperBottomPadding,
            folderImages = folderImages,
            lastImageUriString = lastImageUriString,
            savedUriResolved = savedUriResolved,
            modifier = Modifier.fillMaxSize(),
            onOpenAppDrawer = {
              scope.launch { onboardingManager.markStepComplete(OnboardingStep.SwipeAppDrawer) }
              onOpenAppDrawer()
            },
            onLongPress = { offset ->
              menuOffset = offset
              showBackgroundMenu = true
              scope.launch {
                onboardingManager.markStepComplete(OnboardingStep.LongPressBackground)
              }
            },
            onTap = {
              onDismiss()
              // Tapping background also swipes? No, just dismiss.
              // But if we want to complete "Swipe Background", we need to detect the swipe in
              // WallpaperBackground
            },
            onPageChanged = { uri ->
              currentWallpaperUri = uri
              scope.launch { onboardingManager.markStepComplete(OnboardingStep.SwipeBackground) }
            },
            onSwipeDownLeft = {
              scope.launch { onboardingManager.markStepComplete(OnboardingStep.SwipeNotifications) }
              com.searchlauncher.app.util.SystemUtils.expandNotifications(context)
            },
            onSwipeDownRight = {
              scope.launch { onboardingManager.markStepComplete(OnboardingStep.SwipeQuickSettings) }
              com.searchlauncher.app.util.SystemUtils.expandQuickSettings(context)
            },
          )

          TutorialOverlay(
            currentStep = currentOnboardingStep,
            bottomPadding = bottomPadding,
            onSkip = { scope.launch { onboardingManager.skipAll() } },
          )

          if (showDefaultLauncherDialog) {
            AlertDialog(
              onDismissRequest = {
                app.setAskedDefaultLauncher()
                showDefaultLauncherDialog = false
              },
              title = { Text("Set as Default Launcher?") },
              text = { Text("Would you like to use SearchLauncher as your home screen?") },
              confirmButton = {
                Button(
                  onClick = {
                    app.setAskedDefaultLauncher()
                    showDefaultLauncherDialog = false
                    val intent = Intent(android.provider.Settings.ACTION_HOME_SETTINGS)
                    context.startActivity(intent)
                  }
                ) {
                  Text("Yes")
                }
              },
              dismissButton = {
                TextButton(
                  onClick = {
                    app.setAskedDefaultLauncher()
                    showDefaultLauncherDialog = false
                  }
                ) {
                  Text("Not now")
                }
              },
            )
          } else if (showConsentDialog) {
            ConsentDialog(
              onChoicesSaved = { allowAutocompleteSuggestions, allowCrashReporting ->
                app.setConsent(allowCrashReporting)
                scope.launch {
                  context.dataStore.edit { preferences ->
                    preferences[PreferencesKeys.SEARCH_SHORTCUTS_ENABLED] =
                      allowAutocompleteSuggestions
                  }
                }
                showConsentDialog = false
              },
              onViewPrivacyPolicy = { showPrivacyPolicy = true },
            )
          }

          if (showPrivacyPolicy) {
            PrivacyPolicyDialog(
              onDismiss = { showPrivacyPolicy = false },
              policyText = loadPrivacyPolicyText(context),
            )
          }
        }
      }

      if (browserTabSwipeEnabled) {
        // Measured here rather than inside the preview, which is only composed while the browser is
        // not. The browser's position is one screen minus this, so a placeholder width put it at
        // roughly zero — covering everything in the page's own colour for the few frames before the
        // real width arrived, which is the black flash at the start of the swipe. This box is
        // composed either way, so the width is known before anything needs it.
        Box(
          modifier =
            Modifier.fillMaxSize().onSizeChanged {
              browserTabSwipe.viewportWidthPx = it.width.coerceAtLeast(1)
              browserTabSwipe.viewportHeightPx = it.height.coerceAtLeast(1)
            }
        )
        // The tab's page as it was last left, sliding in under the finger. The real page arrives
        // in a window of its own the moment the movement lands on it — see [slideBrowserIn] — so
        // this is the whole of the browser the launcher ever draws, and no WebView is built here.
        BrowserTabSwipePreview(
          state = browserTabSwipe,
          chromeHeight = with(density) { chromeBarHeightPx.toDp() },
        )
      }

      // Above the wallpaper but below the chrome bar, which keeps working while the strip is up —
      // exactly how the overview sits in the browser.
      if (tabsOverviewRendered) {
        // Null once every tab is gone, which the strip renders as its empty state.
        val tabs = overviewTabs
        // Cards are sized for the browser's content area, not the launcher's, so a preview here
        // is the same shape as the one the tab will open into.
        val statusBarTopPx = WindowInsets.statusBars.getTop(density)
        val browserChromePx =
          WindowInsets.navigationBars.getBottom(density) +
            chromeBarHeightPx +
            with(density) { 12.dp.roundToPx() }
        val bottomInset = bottomPadding + with(density) { bottomSectionHeightPx.toDp() } + 12.dp
        BrowserTabsOverviewLayer(
          tabs = tabs?.items.orEmpty(),
          activeIndex = tabs?.activeIndex ?: 0,
          progress = { tabsOverviewProgress },
          // Not quite opaque: the wallpaper stays faintly visible, so the overview reads as a
          // layer over the home screen rather than as another app.
          scrimColor = MaterialTheme.colorScheme.background.copy(alpha = 0.94f),
          contentColor = MaterialTheme.colorScheme.onBackground,
          cardWidth =
            with(density) { (browserTabSwipe.viewportWidthPx * TAB_CARD_WIDTH_FRACTION).toDp() },
          previewAspectRatio =
            browserTabSwipe.viewportWidthPx.toFloat() /
              (browserTabSwipe.viewportHeightPx - statusBarTopPx - browserChromePx).coerceAtLeast(
                1
              ),
          // The launcher's bar sits above the keyboard reserve, leaving the strip less room than
          // it has in the browser.
          maxPreviewHeight =
            with(density) { browserTabSwipe.viewportHeightPx.toDp() } -
              bottomInset -
              with(density) { statusBarTopPx.toDp() } -
              TAB_STRIP_LABEL_HEIGHT,
          bottomInset = bottomInset,
          // The rect the browser will draw the page into, not the launcher's own layout: the card
          // is growing towards the browser, so it should arrive already the right shape.
          expandTarget =
            Rect(
              left = 0f,
              top = statusBarTopPx.toFloat(),
              right = browserTabSwipe.viewportWidthPx.toFloat(),
              bottom = (browserTabSwipe.viewportHeightPx - browserChromePx).toFloat(),
            ),
          onDismiss = { tabsOverviewOpen = false },
          onSelectStart = ::prepareBrowserForOverviewTab,
          onSelect = { revealBrowserFromOverview() },
          onCloseTab = ::closeBrowserTab,
          onCloseAll = ::forgetAllTabs,
        )
      }

      if (showBackgroundMenu) {
        DropdownMenu(
          expanded = showBackgroundMenu,
          onDismissRequest = { showBackgroundMenu = false },
          offset =
            androidx.compose.ui.unit.DpOffset(
              x = with(LocalDensity.current) { menuOffset.x.toDp() },
              y = with(LocalDensity.current) { menuOffset.y.toDp() },
            ),
        ) {
          DropdownMenuItem(
            text = { Text("Add Wallpapers") },
            onClick = {
              showBackgroundMenu = false
              launcher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
              )
            },
            leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
          )
          if (currentWallpaperUri != null) {
            DropdownMenuItem(
              text = { Text("Remove Current Wallpaper") },
              onClick = {
                showBackgroundMenu = false
                currentWallpaperUri?.let { uri ->
                  scope.launch { app.wallpaperRepository.removeWallpaper(uri) }
                }
              },
              leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
            )
          }
          DropdownMenuItem(
            text = { Text("Add Widget") },
            onClick = {
              showBackgroundMenu = false
              onAddWidget()
            },
            // Not the plain Add the wallpaper entry uses — two identical plus icons in one menu
            // would say nothing about which is which.
            leadingIcon = { Icon(Icons.Default.Widgets, contentDescription = null) },
          )
          val widgets by app.widgetRepository.widgets.collectAsState(initial = emptyList())
          if (showWidgetsSetting && widgets.isNotEmpty()) {
            DropdownMenuItem(
              text = { Text("Clear Widgets") },
              onClick = {
                showBackgroundMenu = false
                val idsToClear = widgets.map { it.id } // Copy list of IDs
                scope.launch {
                  // Remove from Repo
                  app.widgetRepository.clearAllWidgets()

                  // Remove from Host
                  val activity = context as? com.searchlauncher.app.ui.MainActivity
                  activity?.let { act ->
                    idsToClear.forEach { id -> act.appWidgetHost.deleteAppWidgetId(id) }
                  }
                  Toast.makeText(context, "Widgets cleared", Toast.LENGTH_SHORT).show()
                }
              },
              leadingIcon = { Icon(imageVector = Icons.Default.Delete, contentDescription = null) },
            )
          }
        }
      }

      if (!inPip) {
        Column(
          modifier =
            Modifier.fillMaxSize()
              .then(homeSwipeOffset)
              .padding(bottom = bottomPadding) // Push content up by reserved space
              .padding(top = 16.dp, bottom = 12.dp),
          verticalArrangement = Arrangement.Bottom,
        ) {
          if (searchResults.isNotEmpty()) {
            // When opened from the browser, the results panel takes the page color like the rest
            // of the chrome. SearchResultItem reads onSurface/onSurfaceVariant from the theme, so
            // override those locally for contrast on arbitrary page colors.
            val resultsColor = chromeBarColor ?: MaterialTheme.colorScheme.surface
            val resultsContentColor =
              chromeBarColor?.let {
                if (it.luminance() > 0.5f) Color(0xFF1C1B1F) else Color(0xFFEDE8EE)
              } ?: MaterialTheme.colorScheme.onSurface
            val resultsColorScheme =
              if (chromeBarColor != null) {
                MaterialTheme.colorScheme.copy(
                  surface = resultsColor,
                  surfaceVariant = resultsColor,
                  onSurface = resultsContentColor,
                  onSurfaceVariant = resultsContentColor.copy(alpha = 0.8f),
                )
              } else {
                MaterialTheme.colorScheme
              }
            MaterialTheme(colorScheme = resultsColorScheme) {
              Surface(
                modifier =
                  Modifier.fillMaxWidth()
                    .contentMaxWidth()
                    .weight(1f, fill = false)
                    .padding(horizontal = 16.dp)
                    .clickable(
                      indication = null,
                      interactionSource = remember { MutableInteractionSource() },
                    ) {},
                shape = RoundedCornerShape(16.dp),
                color = resultsColor,
                contentColor = resultsContentColor,
                tonalElevation = if (chromeBarColor != null) 0.dp else 2.dp,
                shadowElevation = if (chromeBarColor != null) 8.dp else 0.dp,
              ) {
                if (isLoading) {
                  Box(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center,
                  ) {
                    CircularProgressIndicator()
                  }
                } else {
                  LazyColumn(
                    state = listState,
                    reverseLayout = true,
                    contentPadding = PaddingValues(vertical = 8.dp),
                  ) {
                    itemsIndexed(
                      searchResults,
                      key = { index, item -> "$index/${item.stableListKey}" },
                    ) { index, result ->
                      SearchResultItem(
                        result = result,
                        highlighted = index == keyboardSelectedIndex,
                        isFavorite = app.favoritesRepository.isFavorite(result),
                        actions = menuActionsFor(result, index),
                        onClick = {
                          if (result is SearchResult.SearchIntent) {
                            // If the title implies a direct search (or we
                            // are in fallback mode with query), perform
                            // search
                            // OR if the result title was modified to
                            // include "Search ... on ..."
                            // A better check: if query is not empty AND
                            // it's not just the trigger itself.
                            // The logic below handles both cases.

                            // If it's a "Search X on Y" action (inferred
                            // from query context)
                            if (
                              query.isNotEmpty() &&
                                !result.trigger.equals(query.trim(), ignoreCase = true) &&
                                !result.title.contains(query.trim(), ignoreCase = true)
                            ) {
                              // Perform Search
                              val shortcut =
                                app.searchShortcutRepository.items.value
                                  .filterIsInstance<com.searchlauncher.app.data.SearchShortcut>()
                                  .find { it.alias == result.trigger }

                              if (shortcut != null) {
                                launchShortcutSearch(
                                  context = context,
                                  searchRepository = searchRepository,
                                  shortcut = shortcut,
                                  result = result,
                                  query = query,
                                  privateWebResults = privateWebResults,
                                  wasFirstResult = index == 0,
                                  openInBrowser = { openInBrowser(it) },
                                  onDismiss = onDismiss,
                                )
                              }
                            } else {
                              // Enter sub-search mode (append trigger)
                              onQueryChange(result.trigger + " ")
                            }
                          } else {
                            if (
                              result is SearchResult.Content &&
                                result.deepLink?.startsWith(
                                  "intent:#Intent;action=com.searchlauncher.action.CREATE_SNIPPET"
                                ) == true
                            ) {
                              snippetEditMode = false
                              snippetInitialContent =
                                Regex("S\\.content=([^;]*)")
                                  .find(result.deepLink)
                                  ?.groupValues
                                  ?.get(1)
                                  ?.let { Uri.decode(it) } ?: ""
                              showSnippetDialog = true
                            } else {
                              launchResultPreferringPrivateBrowser(
                                context = context,
                                result = result,
                                query = query,
                                searchShortcuts = searchShortcuts,
                                privateWebResults = privateWebResults,
                                resultLauncher = resultLauncher,
                                wasFirstResult = index == 0,
                              )
                              val keepSearchOpen =
                                result is SearchResult.Content &&
                                  (result.deepLink ==
                                    "intent:#Intent;action=com.searchlauncher.action.APPEND_SPACE;end" ||
                                    result.deepLink ==
                                      "intent:#Intent;action=com.searchlauncher.action.ADD_WIDGET;end")
                              if (keepSearchOpen) {
                                // Do nothing (keep search open)
                              } else {
                                onDismiss()
                              }
                            }
                          }
                        },
                      )
                    }
                  }
                }
              }
            }

            Spacer(modifier = Modifier.height(4.dp))
          }

          // Measured as a block: the tabs overview stacks on top of the whole bottom section, so an
          // open favorites row has to push the strip up with it rather than be drawn over.
          if (favoritesRowVisible) {
            Column(
              modifier =
                Modifier.contentMaxWidth().onSizeChanged { favoritesRowHeightPx = it.height }
            ) {
              if (showingSearchOptions) {
                FavoritesRow(
                  favorites = searchOptionFavorites,
                  history = searchOptionExtras,
                  minIconSizeSetting = minIconSizeSetting,
                  maxRows = favoritesMaxRows,
                  expandToFill = true,
                  reverseHistory = false,
                  drawDivider = false,
                  onLaunch = { result ->
                    val intent = result as? SearchResult.SearchIntent
                    val shortcut =
                      searchShortcuts.find { it.id == result.id || it.alias == intent?.trigger }
                    val term = SearchOptions.searchTerm(query, searchShortcuts)
                    if (shortcut != null && intent != null && term.isNotBlank()) {
                      launchShortcutSearch(
                        context = context,
                        searchRepository = searchRepository,
                        shortcut = shortcut,
                        result = intent,
                        query = term,
                        privateWebResults = privateWebResults,
                        wasFirstResult = false,
                        openInBrowser = { openInBrowser(it) },
                        onDismiss = onDismiss,
                      )
                    } else if (intent != null) {
                      searchRepository.reportUsageAsync(intent.namespace, intent.id)
                      onQueryChange(intent.trigger + " ")
                    }
                  },
                  onToggleFavorite = { result ->
                    app.favoritesRepository.toggleSearchOption(result)
                  },
                  onReorder = { newOrder ->
                    app.favoritesRepository.updateSearchOptionOrder(newOrder)
                  },
                  onCapacityChanged = {},
                  // The same menu the results list offers, so long-pressing an option here and
                  // long-pressing it in the results are the same gesture with the same answer.
                  // Only pinning differs: in this row "favourite" means the search-options bar,
                  // not the app favourites, and it must not clear the query it is searching.
                  menuActions = { result ->
                    menuActionsFor(result, -1)
                      .copy(
                        onToggleFavorite = { app.favoritesRepository.toggleSearchOption(result) }
                      )
                  },
                )
              } else {
                FavoritesRow(
                  favorites = favorites,
                  history = historyItems,
                  historyLimit = historyLimit,
                  minIconSizeSetting = minIconSizeSetting,
                  maxRows = favoritesMaxRows,
                  onLaunch = { result ->
                    if (result is SearchResult.SearchIntent) {
                      searchRepository.reportUsageAsync(result.namespace, result.id)
                      onQueryChange(result.trigger + " ")
                    } else {
                      resultLauncher.launch(result, reportUsage = true)
                      onDismiss()
                    }
                  },
                  onToggleFavorite = { result -> app.favoritesRepository.toggleFavorite(result) },
                  onReorder = { newOrder ->
                    app.favoritesRepository.updateOrder(newOrder)
                    scope.launch {
                      onboardingManager.markStepComplete(OnboardingStep.ReorderFavorites)
                    }
                  },
                  onCapacityChanged = { limit ->
                    searchRepository.updateObservedHistoryLimit(limit)
                  },
                  // The same menu the results list offers, so long-pressing an app here and
                  // long-pressing it in the results are the same gesture with the same answer.
                  menuActions = { result -> menuActionsFor(result, -1) },
                )
              }
              Spacer(modifier = Modifier.height(2.dp))
            }
          }

          SearchChromeBar(
            isIndexing = isIndexing,
            modifier =
              Modifier.onSizeChanged { chromeBarHeightPx = it.height }
                .browserTabSwipe(
                  state = browserTabSwipe,
                  enabled = browserTabSwipeEnabled,
                  tabsOverviewOpen = tabsOverviewOpen,
                  onOpenTabsOverview = ::openTabsOverview,
                  onCloseTabsOverview = { tabsOverviewOpen = false },
                  onCommitLastTab = ::retractKeyboardForTab,
                  // The preview has finished travelling and is standing where the page belongs;
                  // the tab's own window opens onto it without a transition of its own.
                  onOpenLastTab = { BrowserTabTasks.openNewestTab(context) },
                ),
            color = chromeBarColor ?: MaterialTheme.colorScheme.surface,
            contentColor =
              chromeBarColor?.let {
                if (it.luminance() > 0.5f) Color(0xFF1C1B1F) else Color(0xFFEDE8EE)
              } ?: MaterialTheme.colorScheme.onSurface,
            // As a browser overlay the bar floats over the page, which may be the exact same
            // color — a shadow keeps it readable as its own layer.
            shadowElevation = if (chromeBarColor != null) 8.dp else 0.dp,
          ) {
            val activeShortcut =
              remember(query) {
                var shortcut =
                  app.searchShortcutRepository.items.value.find {
                    query.startsWith("${it.alias} ", ignoreCase = true)
                  }
                if (shortcut == null) {
                  shortcut =
                    com.searchlauncher.app.data.DefaultShortcuts.searchShortcuts.find {
                      query.startsWith("${it.alias} ", ignoreCase = true)
                    }
                }
                shortcut
              }

            if (activeShortcut != null) {
              Surface(
                color = androidx.compose.ui.graphics.Color(activeShortcut.color ?: 0xFF808080),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.padding(end = 8.dp),
              ) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                  val defaultShortcut =
                    com.searchlauncher.app.data.DefaultShortcuts.searchShortcuts.find {
                      it.alias == activeShortcut.alias
                    }
                  val label =
                    (activeShortcut.shortLabel
                        ?: defaultShortcut?.shortLabel
                        ?: activeShortcut.description)
                      .replace("Search ", "", ignoreCase = true)
                      .replace("Ask ", "", ignoreCase = true)
                      .trim()
                  Text(
                    text = label,
                    color = androidx.compose.ui.graphics.Color.White,
                    fontSize = 14.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                  )
                }
              }
            }

            val displayQuery =
              if (activeShortcut != null) {
                query.substring("${activeShortcut.alias} ".length)
              } else {
                query
              }

            var textFieldValue by remember {
              mutableStateOf(
                androidx.compose.ui.text.input.TextFieldValue(
                  text = displayQuery,
                  selection = androidx.compose.ui.text.TextRange(displayQuery.length),
                )
              )
            }

            // Update TextFieldValue when displayQuery changes externally (e.g. from "Add Widget")
            LaunchedEffect(displayQuery) {
              if (textFieldValue.text != displayQuery) {
                textFieldValue =
                  textFieldValue.copy(
                    text = displayQuery,
                    selection = androidx.compose.ui.text.TextRange(displayQuery.length),
                  )
              }
            }

            BasicTextField(
              value = textFieldValue,
              onValueChange = { newValue ->
                textFieldValue = newValue
                val newText = newValue.text
                if (activeShortcut != null) {
                  onQueryChange("${activeShortcut.alias} $newText")
                } else {
                  onQueryChange(newText)
                }
              },
              modifier =
                Modifier.weight(1f).focusRequester(focusRequester).onKeyEvent { event ->
                  if (
                    event.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_DEL &&
                      displayQuery.isEmpty() &&
                      activeShortcut != null
                  ) {
                    onQueryChange("")
                    true
                  } else {
                    false
                  }
                },
              textStyle =
                LocalTextStyle.current.copy(fontSize = 16.sp, color = LocalContentColor.current),
              // Autocorrect off keeps the query literal: package names, commands and URL fragments
              // are not dictionary words, and a silent rewrite is harder to notice than a typo.
              keyboardOptions =
                KeyboardOptions(imeAction = ImeAction.Go, autoCorrectEnabled = autocorrectEnabled),
              keyboardActions =
                KeyboardActions(
                  onGo = {
                    val topResult =
                      searchResults.getOrNull(keyboardSelectedIndex) ?: searchResults.firstOrNull()
                    if (topResult != null) {
                      if (topResult is SearchResult.SearchIntent) {
                        if (isFallbackMode && query.isNotEmpty()) {
                          // In fallback mode (e.g. random
                          // text), 'Go' should perform the
                          // search
                          // using the top shortcut, instead
                          // of just expanding the filter.
                          val shortcut =
                            app.searchShortcutRepository.items.value.find {
                              it.alias == topResult.trigger
                            }

                          if (shortcut != null) {
                            launchShortcutSearch(
                              context = context,
                              searchRepository = searchRepository,
                              shortcut = shortcut,
                              result = topResult,
                              query = query,
                              privateWebResults = privateWebResults,
                              wasFirstResult = keyboardSelectedIndex == 0,
                              openInBrowser = { openInBrowser(it) },
                              onDismiss = onDismiss,
                            )
                          } else {
                            // Should not happen if data
                            // integrity is good, but
                            // fallback:
                            onQueryChange(topResult.trigger + " ")
                          }
                        } else {
                          // Normal mode: pressing enter on a
                          // shortcut expands it (sub-search)
                          onQueryChange(topResult.trigger + " ")
                        }
                      } else {
                        launchResultPreferringPrivateBrowser(
                          context = context,
                          result = topResult,
                          query = query,
                          searchShortcuts = searchShortcuts,
                          privateWebResults = privateWebResults,
                          resultLauncher = resultLauncher,
                          wasFirstResult = keyboardSelectedIndex == 0,
                        )
                        onDismiss()
                      }
                    }
                  }
                ),
              cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
              interactionSource = searchFieldInteractionSource,
              decorationBox = { innerTextField ->
                Box(contentAlignment = Alignment.CenterStart) {
                  if (displayQuery.isEmpty() && activeShortcut == null) {
                    if (isListening) {
                      Text(
                        text = "Listening...",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                      )
                    } else {
                      AnimatedContent(
                        targetState = currentHint,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "HintAnimation",
                      ) { targetHint ->
                        Text(
                          text = targetHint,
                          color = LocalContentColor.current.copy(alpha = 0.72f),
                          fontSize = 16.sp,
                          maxLines = 1,
                          overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        )
                      }
                    }
                  }
                  innerTextField()
                }
              },
            )

            if (query.isNotEmpty()) {
              // Resolved once and shared by the badge, the tap and the menu, so what the button
              // shows, what it does and what the menu ticks cannot drift apart.
              val engines =
                remember(searchShortcuts) {
                  (com.searchlauncher.app.data.DefaultShortcuts.searchShortcuts + searchShortcuts)
                    .filter { it.urlTemplate.startsWith("http") }
                    .distinctBy { it.id }
                }
              val engine =
                remember(engines, defaultSearchEngineId) {
                  engines.firstOrNull { it.id == defaultSearchEngineId }
                    ?: engines.firstOrNull { it.id == "google" }
                    ?: engines.first()
                }
              var engineMenuOpen by remember { mutableStateOf(false) }

              Box {
                // Drawn here rather than scaled down from the generator's 40dp bitmap, which at
                // this
                // size rounded off into a circle. Same recipe as the badges in the result list —
                // the
                // engine's colour, its alias on it — at a fifth of the side, which is the corner
                // they
                // are drawn with, so the two read as the same shape.
                Surface(
                  color = Color(engine.color ?: 0xFF808080),
                  shape = RoundedCornerShape(percent = 20),
                  modifier =
                    Modifier.size(24.dp)
                      .combinedClickable(
                        onClick = {
                          openBrowser(context, engine.urlForQuery(query), privateWebResults) {
                            openInBrowser(it)
                          }
                          onDismiss()
                        },
                        onLongClick = { engineMenuOpen = true },
                      ),
                ) {
                  Box(contentAlignment = Alignment.Center) {
                    Text(
                      text = engine.alias.uppercase(),
                      color = Color.White,
                      fontSize = 11.sp,
                      fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                      maxLines = 1,
                    )
                  }
                }

                // The same list the settings page offers, written to the same preference, so
                // changing
                // it here is changing it there.
                DropdownMenu(
                  expanded = engineMenuOpen,
                  onDismissRequest = { engineMenuOpen = false },
                ) {
                  Text(
                    text = "Set default search engine",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                  )
                  engines.forEach { candidate ->
                    DropdownMenuItem(
                      text = { Text(candidate.shortLabel ?: candidate.description) },
                      onClick = {
                        engineMenuOpen = false
                        scope.launch {
                          context.dataStore.edit { preferences ->
                            preferences[PreferencesKeys.DEFAULT_SEARCH_ENGINE] = candidate.id
                          }
                        }
                      },
                      leadingIcon = {
                        Surface(
                          color = Color(candidate.color ?: 0xFF808080),
                          shape = RoundedCornerShape(percent = 20),
                          modifier = Modifier.size(24.dp),
                        ) {
                          Box(contentAlignment = Alignment.Center) {
                            Text(
                              text = candidate.alias.uppercase(),
                              color = Color.White,
                              fontSize = 11.sp,
                              fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                              maxLines = 1,
                            )
                          }
                        }
                      },
                      trailingIcon = {
                        if (candidate.id == engine.id) {
                          Icon(Icons.Default.Check, contentDescription = "Current default")
                        }
                      },
                    )
                  }
                }
              }
              IconButton(
                onClick = { onQueryChange("") },
                modifier = Modifier.size(32.dp).padding(4.dp),
              ) {
                Icon(
                  imageVector = Icons.Default.Close,
                  contentDescription = "Clear",
                  tint = LocalContentColor.current,
                )
              }
            } else {
              // First, because it is the one that comes and goes: only once the browser has tabs to
              // show. Unlike in the browser, where there is always at least one, a launcher that
              // has
              // never opened a page has nothing to count. Sitting between the other two, it shoved
              // the mic sideways the moment a tab appeared, so the two buttons that are always
              // there
              // never settled anywhere. Leading the row, it grows away from them instead.
              if (browserTabSwipeEnabled && openTabCount > 0) {
                BrowserTabsButton(
                  tabCount = openTabCount,
                  onClick = {
                    if (tabsOverviewOpen) tabsOverviewOpen = false else openTabsOverview()
                  },
                )
              }

              IconButton(
                onClick = startOrStopVoiceSearch,
                modifier = Modifier.size(32.dp).padding(4.dp),
              ) {
                Icon(
                  imageVector = Icons.Default.Mic,
                  contentDescription = "Voice Search",
                  tint =
                    if (isListening) MaterialTheme.colorScheme.primary
                    else LocalContentColor.current,
                )
              }

              if (onOpenBrowserContext != null) {
                IconButton(
                  onClick = onOpenBrowserContext,
                  modifier = Modifier.size(32.dp).padding(4.dp),
                ) {
                  Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Browser menu",
                    tint = LocalContentColor.current,
                  )
                }
              } else {
                IconButton(
                  onClick = {
                    scope.launch { onboardingManager.markStepComplete(OnboardingStep.OpenSettings) }
                    onOpenSettings()
                  },
                  modifier = Modifier.size(32.dp).padding(4.dp),
                ) {
                  Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = LocalContentColor.current,
                  )
                }
              }
            }
          }
        }
      }
    }
  }

  bookmarkDialogTarget?.let { target ->
    BookmarkDialog(
      initialTitle = target.title,
      url = target.url,
      isEditMode = target.isEditMode,
      onDismiss = { bookmarkDialogTarget = null },
      onConfirm = { title ->
        bookmarkDialogTarget = null
        scope.launch {
          val saved = searchRepository.saveBookmark(target.url, title)
          if (saved) {
            target.replacesHistoryId?.let { searchRepository.removeBookmark(it, "web_bookmarks") }
            resultsRefreshTick++
          }
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

  if (showSnippetDialog) {
    SnippetDialog(
      initialAlias =
        if (snippetEditMode && snippetItemToEdit != null) snippetItemToEdit!!.alias else "",
      initialContent =
        if (snippetEditMode && snippetItemToEdit != null) snippetItemToEdit!!.content
        else snippetInitialContent,
      isEditMode = snippetEditMode,
      onDismiss = { showSnippetDialog = false },
      onConfirm = { alias, content ->
        scope.launch(Dispatchers.IO) {
          if (snippetEditMode && snippetItemToEdit != null) {
            app.snippetsRepository.updateItem(snippetItemToEdit!!.alias, alias, content)
          } else {
            app.snippetsRepository.addItem(alias, content)
          }
          app.searchRepository.indexSnippets()
        }
        showSnippetDialog = false
      },
    )
  }

  if (showShortcutDialog) {
    ShortcutDialog(
      shortcut = editingShortcut,
      existingAliases =
        searchShortcuts.map { it.alias } +
          com.searchlauncher.app.data.DefaultShortcuts.searchShortcuts.map { it.alias },
      onDismiss = { showShortcutDialog = false },
      onSave = { newShortcut ->
        scope.launch {
          if (editingShortcut != null) {
            app.searchShortcutRepository.updateShortcut(newShortcut)
          } else {
            app.searchShortcutRepository.addShortcut(newShortcut)
          }
          showShortcutDialog = false
          app.searchRepository.indexCustomShortcuts()
        }
      },
    )
  }

  if (showResetConfirmation != null) {
    AlertDialog(
      onDismissRequest = { showResetConfirmation = null },
      title = { Text("Are you sure?") },
      text = { Text(showResetConfirmation?.first ?: "") },
      confirmButton = {
        TextButton(
          onClick = {
            showResetConfirmation?.second?.invoke()
            showResetConfirmation = null
          }
        ) {
          Text("Confirm", color = MaterialTheme.colorScheme.error)
        }
      },
      dismissButton = { TextButton(onClick = { showResetConfirmation = null }) { Text("Cancel") } },
    )
  }
}

internal fun Drawable.toImageBitmap(): ImageBitmap? {
  try {
    val bitmap =
      if (this is BitmapDrawable) {
        if (this.bitmap != null && !this.bitmap.isRecycled) {
          this.bitmap
        } else {
          null
        }
      } else {
        // Enforce a minimum reasonable size if intrinsic dimensions are missing
        val validWidth = intrinsicWidth.takeIf { it > 0 } ?: 192
        val validHeight = intrinsicHeight.takeIf { it > 0 } ?: 192

        // Ensure we don't end up with a tiny 1x1 bitmap if dimensions were missing
        val width = validWidth
        val height = validHeight

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Use a defensive copy of the drawable to avoid modifying the shared instance from cache
        val drawableToDraw = constantState?.newDrawable()?.mutate() ?: this

        val oldBounds = if (drawableToDraw === this) bounds else null

        drawableToDraw.setBounds(0, 0, canvas.width, canvas.height)
        drawableToDraw.draw(canvas)

        if (oldBounds != null) {
          drawableToDraw.bounds = oldBounds
        }

        bitmap
      }
    return bitmap?.asImageBitmap()
  } catch (e: Exception) {
    return null
  }
}

private val SearchResult.stableListKey: String
  get() = "$namespace/$id"

/** A bookmark being created or re-titled from a search result. */
/**
 * Tells a running browser to close, after its tabs have been dropped from the shared store. A
 * browser activity holds its own reference to that list, so without this it would carry on showing
 * tabs that no longer exist anywhere else. Harmless when no browser is running.
 */
private fun closeBrowserWindow(context: Context) {
  context.sendBroadcast(
    Intent(BrowserActivity.ACTION_CLOSE_BROWSER).setPackage(context.packageName)
  )
}

private fun copyUrlToClipboard(context: Context, url: String) {
  SystemUtils.copyUrlToClipboard(context, url, label = "Page URL")
}

/**
 * Opens [url]. A private page gets its own browser activity; an ordinary one goes to
 * [openInBrowser], the browser the launcher hosts itself, so it does not become a task of its own.
 */
private fun openBrowser(
  context: Context,
  url: String,
  private: Boolean,
  openInBrowser: (String) -> Unit,
) {
  if (private) {
    context.startActivity(BrowserActivity.createPrivateIntent(context, url))
  } else {
    openInBrowser(url)
  }
}

private fun launchShortcutSearch(
  context: Context,
  searchRepository: SearchRepository,
  shortcut: SearchShortcut,
  result: SearchResult.SearchIntent,
  query: String,
  privateWebResults: Boolean,
  wasFirstResult: Boolean,
  openInBrowser: (String) -> Unit,
  onDismiss: () -> Unit,
) {
  try {
    val url = shortcut.urlForQuery(query)
    // Not every shortcut template is a web URL — market:, spotify: and geo: ones name an app, and
    // the WebView can only answer those with ERR_UNKNOWN_URL_SCHEME.
    if (url.startsWith("http://") || url.startsWith("https://")) {
      openBrowser(context, url, privateWebResults, openInBrowser)
    } else {
      context.startActivity(
        Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      )
    }
    if (!privateWebResults) {
      searchRepository.reportUsageAsync(result.namespace, result.id, query, wasFirstResult)
    }
    onDismiss()
  } catch (e: Exception) {
    Toast.makeText(context, "Cannot open: ${result.title}", Toast.LENGTH_SHORT).show()
  }
}

private fun launchResultPreferringPrivateBrowser(
  context: Context,
  result: SearchResult,
  query: String,
  searchShortcuts: List<SearchShortcut>,
  privateWebResults: Boolean,
  resultLauncher: ResultLauncher,
  wasFirstResult: Boolean,
) {
  val privateUrl = if (privateWebResults) webUrlForResult(result, query, searchShortcuts) else null
  if (privateUrl != null) {
    context.startActivity(BrowserActivity.createPrivateIntent(context, privateUrl))
  } else {
    resultLauncher.launch(result, query = query, wasFirstResult = wasFirstResult)
  }
}

private data class BookmarkDialogTarget(
  val url: String,
  val title: String,
  val isEditMode: Boolean,
  /** History entry this bookmark replaces, removed on save so the page isn't listed twice. */
  val replacesHistoryId: String? = null,
)

/**
 * How much deeper the launcher's bottom padding is than the browser chrome's, so that the search
 * overlay comes up on exactly the bar it replaces rather than 8dp above it.
 */
private val BROWSER_CHROME_BAR_OFFSET = 8.dp

private fun webUrlForResult(
  result: SearchResult,
  query: String,
  searchShortcuts: List<com.searchlauncher.app.data.SearchShortcut>,
): String? {
  if (result is SearchResult.Content) {
    return result.deepLink?.takeIf { it.startsWith("https://") || it.startsWith("http://") }
  }

  if (
    result !is SearchResult.SearchIntent ||
      query.isBlank() ||
      result.trigger.equals(query.trim(), ignoreCase = true)
  ) {
    return null
  }

  val shortcut = searchShortcuts.find { it.alias == result.trigger } ?: return null
  // Same as above: only web URLs belong in the private browser; app schemes go to the app.
  return shortcut.urlForQuery(query).takeIf {
    it.startsWith("https://") || it.startsWith("http://")
  }
}

private fun createSnippetFallbackResult(context: Context, query: String): SearchResult.Content {
  val encodedContent = Uri.encode(query.trim())
  return SearchResult.Content(
    id = "add_snippet_from_query",
    namespace = "default_actions",
    title = "Add snippet",
    subtitle = "Save \"${query.trim()}\" as a snippet",
    icon = context.getDrawable(android.R.drawable.ic_menu_edit),
    packageName = "com.searchlauncher.app",
    deepLink =
      "intent:#Intent;action=com.searchlauncher.action.CREATE_SNIPPET;S.content=$encodedContent;end",
  )
}

private const val LIVE_SEARCH_RESULT_LIMIT = 16
private const val LIVE_SEARCH_SHORTCUT_LIMIT = 6
private const val FALLBACK_SEARCH_SHORTCUT_LIMIT = 100

// FavoritesRow extracted to components/FavoritesRow.kt

// SnippetDialog extracted to components/SnippetDialog.kt
