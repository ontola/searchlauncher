package com.searchlauncher.app.ui.browser

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.searchlauncher.app.SearchLauncherApp
import kotlin.coroutines.resume
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
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
  private val initialUrl = tab.url
  private val suppliedSession = GeckoEnvironment.take(tab.id)
  val session =
    suppliedSession
      ?: GeckoSession(GeckoSessionSettings.Builder().usePrivateMode(privateMode).build())
  private val favicons = if (privateMode) null else GeckoFavicons(activity, tab, session)
  var view: GeckoView? = null
  var loading by mutableStateOf(false)
  var progress by mutableStateOf(0)
  var canGoBack by mutableStateOf(false)
  var canGoForward by mutableStateOf(false)
  var fullscreen by mutableStateOf(false)
  var error by mutableStateOf<String?>(null)
  var showDownloads by mutableStateOf(false)
  private var closeDownloadTab = false
  private var hasRenderedDocument = false
  private var failedUrl: String? = null
  private var captureRunning = false
  private var navigationGeneration = 0
  private val captureCallbacks = mutableListOf<() -> Unit>()
  private var state: GeckoSession.SessionState? = null
  private val saved by lazy { activity.getSharedPreferences("gecko-tabs", Context.MODE_PRIVATE) }

  init {
    session.selectionActionDelegate = org.mozilla.geckoview.BasicSelectionActionDelegate(activity)
    session.progressDelegate =
      object : GeckoSession.ProgressDelegate {
        override fun onPageStart(session: GeckoSession, url: String) {
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
            view?.postOnAnimation { capture() }
          }
        }

        override fun onSessionStateChange(
          session: GeckoSession,
          sessionState: GeckoSession.SessionState,
        ) {
          state = sessionState
        }
      }
    session.contentDelegate =
      object : GeckoSession.ContentDelegate {
        override fun onTitleChange(session: GeckoSession, title: String?) {
          tab.title = title
          activity.publishTaskDescription(title, tab.favicon, tab.frameColorArgb)
        }

        override fun onFirstContentfulPaint(session: GeckoSession) {
          tab.pageDrawn = true
          if (tab.url != "about:blank") hasRenderedDocument = true
          view?.postOnAnimation { capture() }
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
          error = "The page process stopped. Reload to try again."
          loading = false
        }

        override fun onKill(session: GeckoSession) {
          error = "Android stopped this page to reclaim memory. Reload to continue."
          loading = false
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
            if (Uri.parse(tab.url).host != Uri.parse(url).host) tab.favicon = null
            tab.url = url
            if (tab.favicon == null) favicons?.restoreCached()
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
            if (request.hasUserGesture && openVerifiedAppLink(activity, uri))
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

  fun reload() {
    if (error != null) retry() else session.reload()
  }

  fun retry() {
    val url = failedUrl ?: tab.url
    failedUrl = null
    error = null
    session.loadUri(url)
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
    session.setActive(true)
    favicons?.restoreCached()
    if (favicons != null)
      GeckoEnvironment.attachIcons(activity, session, favicons) { loadInitialPage() }
    else loadInitialPage()
  }

  private fun loadInitialPage() {
    if (suppliedSession != null) return
    val restored =
      if (!privateMode)
        GeckoSession.SessionState.fromString(saved.getString("state:${tab.id}", null))
      else null
    if (restored != null) {
      tab.url = saved.getString("url:${tab.id}", tab.url) ?: tab.url
      session.restoreState(restored)
    } else session.loadUri(initialUrl)
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
    if (privateMode || !tab.pageDrawn || currentView?.isShown != true || !session.isOpen) {
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
            if (bitmap != null) {
              if (generation == navigationGeneration && currentView.isShown && tab.pageDrawn) {
                val width = bitmap.width.coerceAtMost(540)
                val height =
                  (bitmap.height.toFloat() * width / bitmap.width).toInt().coerceAtLeast(1)
                val preview =
                  android.graphics.Bitmap.createScaledBitmap(bitmap, width, height, true)
                tab.snapshot = preview
                if (preview !== bitmap) bitmap.recycle()
              } else bitmap.recycle()
            }
            complete()
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
      .putString("state:${tab.id}", state?.toString())
      .apply()
  }

  fun close() {
    favicons?.close()
    session.close()
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
}
