package com.searchlauncher.app.ui.browser

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import com.searchlauncher.app.SearchLauncherApp
import kotlin.coroutines.resume
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.yield
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings
import org.mozilla.geckoview.GeckoView
import org.mozilla.geckoview.WebRequestError
import org.mozilla.geckoview.WebResponse

internal class GeckoPage(
  private val activity: BrowserActivity,
  val tab: BrowserTab,
  private val privateMode: Boolean,
  private val onClose: () -> Unit,
) {
  private val saved by lazy { activity.getSharedPreferences("gecko-tabs", Context.MODE_PRIVATE) }
  private val initialUrl =
    if (!privateMode && tab.url == "about:blank")
      saved.getString("url:${tab.id}", null) ?: tab.installedApp?.startUrl ?: tab.url
    else tab.url
  /** The same tab's page from the window this one replaces, if that window was only recreated. */
  private var predecessor: GeckoPage? = if (privateMode) null else handedOver.remove(tab.id)
  private var awaitingInitialLocation: Boolean =
    predecessor?.awaitingInitialLocation ?: (initialUrl != "about:blank")
  private val suppliedSession: GeckoSession? = predecessor?.session ?: GeckoEnvironment.take(tab.id)
  val session: GeckoSession =
    suppliedSession
      ?: GeckoSession(
        GeckoSessionSettings.Builder()
          .usePrivateMode(privateMode)
          .displayMode(appDisplayMode(initialUrl))
          .build()
      )
  private val favicons = if (privateMode) null else GeckoFavicons(activity, tab, session)
  private val appearance = GeckoAppearance(activity, tab, session)
  var view: GeckoView? = null
  var awaitingPaint: Boolean by mutableStateOf(predecessor?.awaitingPaint ?: true)
    private set

  // A live session reports these on change only, so a page taking one over starts from what its
  // predecessor last heard rather than from defaults Gecko will never correct.
  var loading: Boolean by mutableStateOf(predecessor?.loading ?: false)
  var progress: Int by mutableStateOf(predecessor?.progress ?: 0)
  var canGoBack: Boolean by mutableStateOf(predecessor?.canGoBack ?: false)
  var canGoForward: Boolean by mutableStateOf(predecessor?.canGoForward ?: false)
  var fullscreen: Boolean by mutableStateOf(predecessor?.fullscreen ?: false)
  var webAppManifest: InstalledWebApp? by mutableStateOf(predecessor?.webAppManifest)
    private set

  val inAppScope: Boolean
    get() = !privateMode && tab.installedApp?.contains(tab.url) == true

  var error: String? by mutableStateOf(predecessor?.error)
  var showDownloads by mutableStateOf(false)
  private var closeDownloadTab = false
  private var hasRenderedDocument: Boolean = predecessor?.hasRenderedDocument ?: false
  private var failedUrl: String? = predecessor?.failedUrl
  private var captureRunning = false
  private var scrollCapture: Job? = null
  private var touching = false
  private var previewDirty = false
  private var navigationGeneration = 0
  private var loadGeneration = 0
  private val captureCallbacks = mutableListOf<() -> Unit>()
  private var state: GeckoSession.SessionState? = predecessor?.state
  private var killed: Boolean = predecessor?.killed ?: false
  private var lastAutomaticRecoveryAt: Long? = null
  private var recoveryJob: Job? = null
  private var closed = false
  /** When this page was last hidden, for the reload log. */
  private var hiddenSince: Long? = null

  init {
    // Its state has been copied; holding on would keep the destroyed window alive.
    predecessor = null
    tab.url = initialUrl
    session.scrollDelegate =
      object : GeckoSession.ScrollDelegate {
        override fun onScrollChanged(session: GeckoSession, scrollX: Int, scrollY: Int) {
          schedulePreview()
        }
      }
    session.selectionActionDelegate = org.mozilla.geckoview.BasicSelectionActionDelegate(activity)
    session.progressDelegate =
      object : GeckoSession.ProgressDelegate {
        override fun onPageStart(session: GeckoSession, url: String) {
          if (awaitingInitialLocation && url == "about:blank") return
          webAppManifest = null
          favicons?.onNavigation()
          if (Uri.parse(tab.url).host != Uri.parse(url).host) tab.favicon = null
          appearance.onNavigation(url)
          tab.url = url
          session.settings.displayMode = appDisplayMode(url)
          navigationGeneration++
          tab.pageDrawn = false
          loading = true
          error = null
          failedUrl = null
          progress = 0
        }

        override fun onProgressChange(session: GeckoSession, value: Int) {
          progress = value
        }

        override fun onPageStop(session: GeckoSession, success: Boolean) {
          loading = false
          if (success) {
            recordHistory()
            schedulePreview()
          }
        }

        override fun onSessionStateChange(
          session: GeckoSession,
          sessionState: GeckoSession.SessionState,
        ) {
          if (!(awaitingInitialLocation && sessionState.currentUrl() == "about:blank")) {
            state = sessionState
            if (sessionState.currentUrl() == tab.url) persist()
          }
        }
      }
    session.contentDelegate =
      object : GeckoSession.ContentDelegate {
        override fun onWebAppManifest(session: GeckoSession, manifest: org.json.JSONObject) {
          if (!privateMode) webAppManifest = InstalledWebApp.fromManifest(manifest, tab.url)
        }

        override fun onTitleChange(session: GeckoSession, title: String?) {
          tab.title = title
          activity.publishTaskDescription(title, tab.favicon, tab.frameColorArgb)
        }

        override fun onPaintStatusReset(session: GeckoSession) {
          awaitingPaint = true
          tab.pageDrawn = false
        }

        override fun onFirstComposite(session: GeckoSession) {
          // Resuming an already rendered document can reuse its layers without another FCP.
          // FIRST_PAINT is also the signal GeckoView uses to remove coverUntilFirstPaint.
          if (hasRenderedDocument && !loading && !awaitingInitialLocation) {
            awaitingPaint = false
            tab.pageDrawn = true
            schedulePreview()
          }
        }

        override fun onFirstContentfulPaint(session: GeckoSession) {
          if (awaitingInitialLocation) return
          awaitingPaint = false
          tab.pageDrawn = true
          if (tab.url != "about:blank") hasRenderedDocument = true
          schedulePreview()
        }

        override fun onFullScreen(session: GeckoSession, fullScreen: Boolean) {
          fullscreen = fullScreen
        }

        override fun onCloseRequest(session: GeckoSession) {
          activity.finishAndRemoveTask()
        }

        override fun onExternalResponse(session: GeckoSession, response: WebResponse) {
          saveGeckoDownload(activity, response)
          loading = false
          progress = 100
          error = null
          closeDownloadTab = !hasRenderedDocument
          showDownloads = true
        }

        override fun onCrash(session: GeckoSession) {
          processStopped("The page process crashed. Reload to try again.", wasKilled = false)
        }

        override fun onKill(session: GeckoSession) {
          // onKill does not tell us why the process was terminated (including OEM policies).
          processStopped("The page was stopped. Reload to continue.", wasKilled = true)
          recoverIfNeeded()
        }

        override fun onContextMenu(
          session: GeckoSession,
          screenX: Int,
          screenY: Int,
          element: GeckoSession.ContentDelegate.ContextElement,
        ) {
          val link = element.linkUri ?: return
          if (Uri.parse(link).scheme !in listOf("http", "https")) return
          AlertDialog.Builder(activity)
            .setTitle(link)
            .setItems(arrayOf("Open in new tab", "Copy link")) { _, index ->
              if (index == 0 && !privateMode)
                activity.startActivity(BrowserActivity.createIntent(activity, link))
              else if (index == 0) session.loadUri(link)
              else
                (activity.getSystemService(Context.CLIPBOARD_SERVICE)
                    as android.content.ClipboardManager)
                  .setPrimaryClip(android.content.ClipData.newPlainText("Link", link))
            }
            .show()
        }
      }
    session.navigationDelegate =
      object : GeckoSession.NavigationDelegate {
        override fun onLocationChange(
          session: GeckoSession,
          url: String?,
          perms: MutableList<GeckoSession.PermissionDelegate.ContentPermission>,
          hasUserGesture: Boolean,
        ) {
          if (url != null) {
            // Gecko announces its empty startup document before load/restore commits.
            if (awaitingInitialLocation && url == "about:blank") return
            awaitingInitialLocation = false
            appearance.onNavigation(url)
            if (Uri.parse(tab.url).host != Uri.parse(url).host) tab.favicon = null
            tab.url = url
            session.settings.displayMode = appDisplayMode(url)
            if (tab.favicon == null) favicons?.restoreCached()
            retainIfFavorite()
            session.flushSessionState()
          }
        }

        override fun onCanGoBack(session: GeckoSession, value: Boolean) {
          canGoBack = value
        }

        override fun onCanGoForward(session: GeckoSession, value: Boolean) {
          canGoForward = value
        }

        override fun onLoadRequest(
          session: GeckoSession,
          request: GeckoSession.NavigationDelegate.LoadRequest,
        ): GeckoResult<AllowOrDeny>? {
          val uri = Uri.parse(request.uri)
          if (uri.scheme in listOf("http", "https")) {
            // loadUri is an explicit choice to use this browser (pasted URL, search result,
            // external browser intent). Redispatching it can open a site's app, which may then
            // download in its own Chrome Custom Tab. Only website-initiated app links may leave.
            // Script/redirect handoffs remain allowed for login flows such as DigiD.
            if (!request.isDirectNavigation && openVerifiedAppLink(activity, uri))
              return GeckoResult.deny()
            return null
          }
          if (uri.scheme in listOf("about", "data", "blob", "resource")) return null
          if (uri.scheme in listOf("file", "content", "javascript")) return GeckoResult.deny()
          val answer = GeckoResult<AllowOrDeny>()
          AlertDialog.Builder(activity)
            .setTitle("Open another app?")
            .setMessage("${Uri.parse(tab.url).host ?: "This page"} wants to open an external app.")
            .setNegativeButton("Cancel") { _, _ -> answer.complete(AllowOrDeny.DENY) }
            .setOnCancelListener { answer.complete(AllowOrDeny.DENY) }
            .setPositiveButton("Open") { _, _ ->
              val opened =
                runCatching {
                    val external =
                      if (uri.scheme == "intent")
                        Intent.parseUri(request.uri, Intent.URI_INTENT_SCHEME)
                      else Intent(Intent.ACTION_VIEW, uri)
                    external.component = null
                    external.selector = null
                    external.action = Intent.ACTION_VIEW
                    external.flags = 0
                    external.addCategory(Intent.CATEGORY_BROWSABLE)
                    require(external.data?.scheme !in listOf("file", "content", "javascript"))
                    activity.startActivity(external)
                  }
                  .isSuccess
              if (!opened)
                Toast.makeText(activity, "No app could open this link", Toast.LENGTH_LONG).show()
              answer.complete(AllowOrDeny.DENY)
            }
            .show()
          return answer
        }

        override fun onNewSession(session: GeckoSession, uri: String): GeckoResult<GeckoSession>? {
          // Keep private data in this process; multi-window private browsing is a later experiment.
          if (privateMode) {
            Toast.makeText(
                activity,
                "Private popup windows are not supported in this experiment",
                Toast.LENGTH_LONG,
              )
              .show()
            return null
          }
          val next =
            BrowserTabStore.addBackgroundTab(uri) { BrowserTabTasks.close(activity, it.id) }
          val popup = GeckoEnvironment.createPopup(next.id, false)
          BrowserTabTasks.open(activity, next.id)
          return GeckoResult.fromValue(popup)
        }

        override fun onLoadError(
          session: GeckoSession,
          uri: String?,
          failure: WebRequestError,
        ): GeckoResult<String>? {
          failedUrl = uri ?: tab.url
          tab.url = failedUrl!!
          error = "${failedUrl}\n\nCheck your connection and try again. (Error ${failure.code})"
          loading = false
          return null
        }
      }
  }

  fun appDisplayMode(url: String = tab.url): Int {
    val app = tab.installedApp?.takeIf { !privateMode && it.contains(url) }
    return when (app?.display) {
      "fullscreen" -> GeckoSessionSettings.DISPLAY_MODE_FULLSCREEN
      "standalone" -> GeckoSessionSettings.DISPLAY_MODE_STANDALONE
      else -> GeckoSessionSettings.DISPLAY_MODE_BROWSER
    }
  }

  fun reload() {
    if (error != null || !session.isOpen) retry()
    else if (!awaitingInitialLocation) session.reload()
  }

  fun retry() {
    val url = failedUrl ?: tab.url
    navigate(url, restoreHistory = true)
  }

  private fun processStopped(message: String, wasKilled: Boolean) {
    killed = wasKilled
    scrollCapture?.cancel()
    android.util.Log.w(
      "GeckoRecovery",
      "Page process ${if (wasKilled) "killed" else "crashed"}; resumed=${activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)}",
    )
    BrowserReloadLog.record(
      activity,
      if (wasKilled) "page process killed" else "page process crashed",
      reloadContext(),
    )
    failedUrl = failedUrl ?: tab.url
    navigationGeneration++
    loadGeneration++
    tab.pageDrawn = false
    fullscreen = false
    loading = false
    error = message
  }

  /** Restore discarded pages on demand, never respawn background tabs or loop on a bad page. */
  fun recoverIfNeeded() {
    if (!killed || closed || recoveryJob?.isActive == true) return
    recoveryJob =
      activity.lifecycleScope.launch {
        // Let Gecko finish closing the old session before reopening it.
        yield()
        if (
          !killed || closed || !activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        )
          return@launch
        val now = SystemClock.elapsedRealtime()
        if (lastAutomaticRecoveryAt?.let { now - it < 30_000 } == true) return@launch
        lastAutomaticRecoveryAt = now
        android.util.Log.i("GeckoRecovery", "Restoring killed foreground page")
        retry()
      }
  }

  /** Pinned sites stay at high priority while hidden, for as long as Android has the memory. */
  private fun retainIfFavorite() {
    if (!privateMode) GeckoEnvironment.retainFavorite(session, FavoriteSites.covers(tab))
  }

  private fun reloadContext(): String {
    val hidden = hiddenSince?.let { "hidden ${(SystemClock.elapsedRealtime() - it) / 1000}s" }
    val memory =
      runCatching {
          val info = android.app.ActivityManager.MemoryInfo()
          (activity.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager)
            .getMemoryInfo(info)
          "free ${info.availMem / (1024 * 1024)}MB${if (info.lowMemory) " (low)" else ""}"
        }
        .getOrNull()
    val favorite = if (FavoriteSites.covers(tab)) "favorite" else null
    val tabs = BrowserTabStore.tabs?.items?.size?.let { "$it tabs" }
    return listOfNotNull(hidden ?: "visible", favorite, tabs, memory).joinToString(", ")
  }

  fun setVisible(visible: Boolean) {
    hiddenSince = if (visible) null else hiddenSince ?: SystemClock.elapsedRealtime()
    retainIfFavorite()
    if (visible) GeckoEnvironment.retainRecent(session)
    else {
      touching = false
      scrollCapture?.cancel()
    }
    if (session.isOpen) session.setActive(visible)
  }

  /** A killed/crashed Gecko session is closed: loadUri alone just queues forever. */
  fun navigate(url: String, restoreHistory: Boolean = false) {
    killed = false
    val reopening = !session.isOpen
    val history = state?.takeIf { reopening && restoreHistory && it.currentUrl() == url }
    val generation = ++loadGeneration
    failedUrl = null
    error = null
    loading = true
    progress = 0
    if (Uri.parse(tab.url).host != Uri.parse(url).host) tab.favicon = null
    tab.url = url
    awaitingInitialLocation = url != "about:blank"
    if (reopening) {
      navigationGeneration++
      tab.pageDrawn = false
      view?.releaseSession()
      session.open(GeckoEnvironment.runtime(activity))
      view?.setSession(session)
      view?.coverUntilFirstPaint(tab.pageBackgroundArgb)
      setVisible(activity.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
      preparePage {
        if (generation == loadGeneration) {
          if (history != null) session.restoreState(history) else session.loadUri(url)
        }
      }
    } else session.loadUri(url)
  }

  fun dismissDownloads() {
    showDownloads = false
    if (closeDownloadTab) {
      closeDownloadTab = false
      activity.finishAndRemoveTask()
    }
  }

  fun start() {
    val runtime = GeckoEnvironment.runtime(activity)
    if (!session.isOpen) session.open(runtime)
    setVisible(activity.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
    favicons?.restoreCached()
    val generation = ++loadGeneration
    preparePage { if (generation == loadGeneration) loadInitialPage() }
  }

  private fun preparePage(ready: () -> Unit) {
    activity.lifecycleScope.launch {
      GeckoEnvironment.prepareAppearance(activity)
      GeckoAdBlocking.prepare(activity) {
        if (session.isOpen && !closed) {
          GeckoEnvironment.attachMetadata(activity, session, favicons, appearance, ready)
        }
      }
    }
  }

  private fun loadInitialPage() {
    if (suppliedSession != null) return
    val restored =
      if (!privateMode)
        runCatching {
            GeckoSession.SessionState.fromString(saved.getString("state:${tab.id}", null))
          }
          .getOrNull()
      else null
    // An empty/stale history snapshot must not replace the separately saved destination.
    if (restored != null && restored.currentUrl() == initialUrl) {
      // This tab had a live page before; whatever ended it, the page now loads again.
      BrowserReloadLog.record(activity, "tab restored from saved state", reloadContext())
      session.restoreState(restored)
    } else session.loadUri(initialUrl)
  }

  /**
   * Observe without consuming: Gecko still owns scrolling, pinch zoom, links and text selection.
   */
  fun onTouch(event: android.view.MotionEvent) {
    when (event.actionMasked) {
      android.view.MotionEvent.ACTION_DOWN -> {
        touching = true
        scrollCapture?.cancel()
      }
      android.view.MotionEvent.ACTION_UP,
      android.view.MotionEvent.ACTION_CANCEL -> {
        touching = false
        if (previewDirty) schedulePreview()
      }
    }
  }

  private fun schedulePreview() {
    previewDirty = true
    scrollCapture?.cancel()
    if (touching || closed || privateMode) return
    scrollCapture =
      activity.lifecycleScope.launch {
        // A paused finger is not an idle page. Fling scroll callbacks also postpone this work.
        delay(500)
        if (!touching && activity.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
          previewDirty = false
          capture()
        }
      }
  }

  /** Capture while visible, before another window/overlay can suspend the compositor. */
  suspend fun captureBeforeTransition() {
    withTimeoutOrNull(500) {
      suspendCancellableCoroutine<Unit> { continuation ->
        capture { if (continuation.isActive) continuation.resume(Unit) }
      }
    }
  }

  fun capture(onComplete: () -> Unit = {}) {
    if (captureRunning) {
      captureCallbacks += onComplete
      return
    }
    val currentView = view
    if (
      closed || privateMode || !tab.pageDrawn || currentView?.isShown != true || !session.isOpen
    ) {
      onComplete()
      return
    }
    captureRunning = true
    captureCallbacks += onComplete
    val generation = navigationGeneration
    fun complete() {
      captureRunning = false
      val callbacks = captureCallbacks.toList()
      captureCallbacks.clear()
      callbacks.forEach { it() }
    }
    try {
      currentView
        .capturePixels()
        .accept(
          { bitmap ->
            if (bitmap == null) complete()
            else {
              // Pixel readback is asynchronous, but resizing used to run on the UI thread.
              // Keep bitmap ownership explicit even if navigation/destruction cancels the job.
              activity.lifecycleScope.launch(start = CoroutineStart.UNDISPATCHED) {
                var preview: android.graphics.Bitmap? = null
                var adopted = false
                try {
                  withContext(Dispatchers.Default) {
                    val width = bitmap.width.coerceAtMost(540)
                    val height =
                      (bitmap.height.toFloat() * width / bitmap.width).toInt().coerceAtLeast(1)
                    preview =
                      android.graphics.Bitmap.createScaledBitmap(bitmap, width, height, true)
                  }
                  if (
                    !closed &&
                      generation == navigationGeneration &&
                      currentView.isShown &&
                      tab.pageDrawn
                  ) {
                    if (touching) schedulePreview()
                    else {
                      tab.snapshot = preview
                      adopted = true
                    }
                  }
                } finally {
                  if (!adopted && preview !== bitmap) preview?.recycle()
                  if (!adopted || preview !== bitmap) bitmap.recycle()
                  complete()
                }
              }
            }
          },
          { failure ->
            android.util.Log.w("GeckoPreview", "Could not capture tab preview", failure)
            // Retain the last drawn frame; never replace it with a blank capture on suspension.
            complete()
          },
        )
    } catch (failure: Exception) {
      android.util.Log.w("GeckoPreview", "Compositor unavailable for preview", failure)
      complete()
    }
  }

  fun persist() {
    if (privateMode) return
    saved
      .edit()
      .putString("url:${tab.id}", tab.url)
      .putString("state:${tab.id}", state?.takeIf { it.currentUrl() == tab.url }?.toString())
      .apply()
  }

  fun close() {
    closed = true
    recoveryJob?.cancel()
    scrollCapture?.cancel()
    favicons?.close()
    GeckoEnvironment.releaseRecent(session)
    session.close()
  }

  /**
   * Keeps this tab's page alive for the window that is about to replace this one.
   *
   * Android destroys and recreates a window for configuration changes it does not hand to the
   * activity (and for theme overlay changes, which cannot be handed over at all). Closing the
   * session there meant every such recreation reloaded the page from its saved history: lost scroll
   * position, form input and app state, which is what an open tab "randomly reloading" looked like.
   * The session is not tied to a window, so the next page for this tab adopts it.
   *
   * A page nobody claims (the tab was closed meanwhile) is closed after a grace period.
   */
  fun handOver() {
    if (privateMode || !session.isOpen) {
      close()
      return
    }
    closed = true
    recoveryJob?.cancel()
    scrollCapture?.cancel()
    favicons?.close()
    view = null
    handedOver.put(tab.id, this)?.takeIf { it !== this }?.close()
    android.os
      .Handler(android.os.Looper.getMainLooper())
      .postDelayed(
        { if (handedOver[tab.id] === this) handedOver.remove(tab.id)?.close() },
        UNCLAIMED_HANDOVER_MS,
      )
  }

  fun forget() {
    saved.edit().remove("url:${tab.id}").remove("state:${tab.id}").apply()
  }

  private fun recordHistory() {
    if (privateMode || Uri.parse(tab.url).scheme !in listOf("http", "https")) return
    val url = tab.url
    val title = tab.title
    activity.lifecycleScope.launch {
      (activity.application as SearchLauncherApp).searchRepositoryOrNull?.indexWebUrl(url, title)
    }
  }

  companion object {
    private const val UNCLAIMED_HANDOVER_MS = 10_000L
    private val handedOver = mutableMapOf<Long, GeckoPage>()
  }
}

private fun GeckoSession.SessionState.currentUrl(): String? =
  runCatching { getOrNull(currentIndex)?.uri }.getOrNull()
