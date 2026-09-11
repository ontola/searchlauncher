package com.searchlauncher.app.ui.browser

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.launch

internal fun canPeekLink(url: String): Boolean {
  val uri = Uri.parse(url)
  return (uri.scheme.equals("https", true) || uri.scheme.equals("http", true)) &&
    !uri.host.isNullOrBlank()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LinkPeekSheet(
  linkUrl: String,
  imageUrl: String?,
  privateMode: Boolean,
  adBlockEnabled: Boolean,
  siteSettingsStore: BrowserSiteSettingsStore,
  onOpenInNewTab: (String) -> Unit,
  onOpenPrivate: (String) -> Unit,
  onCopyUrl: (String) -> Unit,
  onShareUrl: (String) -> Unit,
  onDownloadImage: (String) -> Unit,
  onDismiss: () -> Unit,
) {
  var currentUrl by remember(linkUrl) { mutableStateOf(linkUrl) }
  var title by remember(linkUrl) { mutableStateOf("Link preview") }
  var progress by remember(linkUrl) { mutableIntStateOf(0) }
  var error by remember(linkUrl) { mutableStateOf<String?>(null) }
  var preview by remember { mutableStateOf<WebView?>(null) }
  val blockingEnabled = rememberUpdatedState(adBlockEnabled)
  val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
  DisposableEffect(preview, lifecycleOwner) {
    val view = preview
    val observer =
      androidx.lifecycle.LifecycleEventObserver { _, event ->
        when (event) {
          androidx.lifecycle.Lifecycle.Event.ON_PAUSE -> view?.onPause()
          androidx.lifecycle.Lifecycle.Event.ON_RESUME -> view?.onResume()
          else -> Unit
        }
      }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
  }

  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  val scope = rememberCoroutineScope()
  val dismissDistance = with(LocalDensity.current) { 64.dp.toPx() }
  var handleDrag by remember { mutableFloatStateOf(0f) }
  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    // The WebView owns page drags, including overscroll at either edge. Allowing the sheet's
    // nested-scroll/drag handlers to compete with it makes the preview move while reading.
    sheetGesturesEnabled = false,
    dragHandle = {
      Box(
        Modifier.fillMaxWidth()
          .height(48.dp)
          .semantics { contentDescription = "Swipe down to close preview" }
          .draggable(
            orientation = Orientation.Vertical,
            state =
              rememberDraggableState { delta ->
                handleDrag = (handleDrag + delta).coerceAtLeast(0f)
              },
            onDragStarted = { handleDrag = 0f },
            onDragStopped = { velocity ->
              if (handleDrag >= dismissDistance || velocity > 1000f) {
                scope.launch {
                  sheetState.hide()
                  if (!sheetState.isVisible) onDismiss()
                }
              }
            },
          ),
        contentAlignment = Alignment.Center,
      ) {
        BottomSheetDefaults.DragHandle()
      }
    },
  ) {
    Column(Modifier.fillMaxWidth().fillMaxHeight(0.88f).padding(horizontal = 16.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
          Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          Text(
            currentUrl,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Close preview") }
      }
      Spacer(Modifier.height(8.dp))
      Surface(
        modifier = Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(16.dp)),
        color = MaterialTheme.colorScheme.surfaceContainer,
      ) {
        Box {
          key(linkUrl) {
            AndroidView(
              modifier = Modifier.fillMaxSize(),
              factory = { context ->
                createLinkPeekWebView(
                    context,
                    linkUrl,
                    privateMode,
                    siteSettingsStore,
                    isAdBlockEnabled = { blockingEnabled.value },
                    onPageStarted = { url ->
                      currentUrl = url
                      title = "Link preview"
                      error = null
                    },
                    onTitle = { title = it },
                    onUrlChanged = { currentUrl = it },
                    onProgress = { progress = it },
                    onError = { error = it },
                  )
                  .also { preview = it }
              },
              onRelease = { view ->
                if (preview === view) preview = null
                view.stopLoading()
                view.webChromeClient = null
                view.destroy()
              },
            )
          }
          if (progress < 100 && error == null) {
            LinearProgressIndicator(
              progress = { progress / 100f },
              modifier = Modifier.fillMaxWidth(),
            )
          }
          error?.let { message ->
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surfaceContainer) {
              Column(
                Modifier.padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
              ) {
                Text(message, style = MaterialTheme.typography.bodyMedium)
                TextButton(
                  onClick = {
                    error = null
                    preview?.reload()
                  }
                ) {
                  Text("Retry")
                }
              }
            }
          }
        }
      }
      // Keep the menu separate from the page, with comfortable touch targets and visible labels.
      Spacer(Modifier.height(16.dp))
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        PeekAction("New tab", Icons.AutoMirrored.Filled.OpenInNew, Modifier.weight(1f)) {
          onDismiss()
          onOpenInNewTab(currentUrl)
        }
        PeekAction("Incognito", Icons.Default.VisibilityOff, Modifier.weight(1f)) {
          onDismiss()
          onOpenPrivate(currentUrl)
        }
      }
      Spacer(Modifier.height(8.dp))
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        PeekAction("Copy URL", Icons.Default.ContentCopy, Modifier.weight(1f)) {
          onDismiss()
          onCopyUrl(currentUrl)
        }
        PeekAction("Share", Icons.Default.Share, Modifier.weight(1f)) {
          onDismiss()
          onShareUrl(currentUrl)
        }
      }
      if (imageUrl != null) {
        Spacer(Modifier.height(8.dp))
        PeekAction("Download image", Icons.Default.Download, Modifier.fillMaxWidth()) {
          onDismiss()
          onDownloadImage(imageUrl)
        }
      }
      Spacer(Modifier.height(16.dp))
    }
  }
}

@Composable
private fun PeekAction(label: String, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
  FilledTonalButton(
    onClick = onClick,
    modifier = modifier.heightIn(min = 48.dp),
    shape = RoundedCornerShape(12.dp),
    contentPadding = PaddingValues(horizontal = 12.dp),
  ) {
    Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
    Spacer(Modifier.width(8.dp))
    Text(label, style = MaterialTheme.typography.labelMedium)
  }
}

@SuppressLint("SetJavaScriptEnabled")
internal fun createLinkPeekWebView(
  context: android.content.Context,
  url: String,
  privateMode: Boolean,
  siteSettingsStore: BrowserSiteSettingsStore,
  isAdBlockEnabled: () -> Boolean,
  onPageStarted: (String) -> Unit,
  onTitle: (String) -> Unit,
  onUrlChanged: (String) -> Unit,
  onProgress: (Int) -> Unit,
  onError: (String) -> Unit,
): WebView =
  WebView(context).apply {
    setBackgroundColor(android.graphics.Color.WHITE)
    enableBrowserWebAuthn()
    settings.domStorageEnabled = true
    settings.allowFileAccess = false
    settings.allowContentAccess = false
    settings.setSupportZoom(true)
    settings.builtInZoomControls = true
    settings.displayZoomControls = false
    settings.javaScriptCanOpenWindowsAutomatically = false
    settings.setSupportMultipleWindows(false)
    if (privateMode) settings.cacheMode = WebSettings.LOAD_NO_CACHE
    fun applySettings(target: String): BrowserSiteSettings {
      val policy = siteSettingsStore.load(target)
      settings.javaScriptEnabled = policy.javaScriptEnabled
      CookieManager.getInstance().setAcceptThirdPartyCookies(this, policy.thirdPartyCookiesEnabled)
      return policy
    }
    webChromeClient =
      object : WebChromeClient() {
        override fun onProgressChanged(view: WebView, newProgress: Int) = onProgress(newProgress)

        override fun onReceivedTitle(view: WebView, title: String?) {
          title?.takeIf { it.isNotBlank() }?.let(onTitle)
        }
      }
    webViewClient =
      object : WebViewClient() {
        @Volatile private var pageSettings = applySettings(url)

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
          if (!canPeekLink(request.url.toString())) {
            if (request.isForMainFrame)
              onError("Open this link in a tab to continue in another app.")
            return true
          }
          if (request.isForMainFrame) pageSettings = applySettings(request.url.toString())
          return false
        }

        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
          pageSettings = applySettings(url)
          onPageStarted(url)
        }

        override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
          // Address changes within the preview never alter the underlying tab or saved history.
          if (canPeekLink(url)) onUrlChanged(url)
        }

        override fun onReceivedError(
          view: WebView,
          request: WebResourceRequest,
          error: WebResourceError,
        ) {
          if (request.isForMainFrame)
            onError("Couldn’t load this preview. Try again or open it in a tab.")
        }

        override fun shouldInterceptRequest(
          view: WebView,
          request: WebResourceRequest,
        ): WebResourceResponse? {
          if (
            !request.isForMainFrame &&
              isAdBlockEnabled() &&
              pageSettings.adBlockEnabled &&
              AdBlocker.shouldBlock(request.url.toString())
          )
            return AdBlocker.blockedResponse()
          return null
        }
      }
    setDownloadListener { _, _, _, _, _ ->
      onError("Open this link in a tab to download the file.")
    }
    loadUrl(url)
  }
