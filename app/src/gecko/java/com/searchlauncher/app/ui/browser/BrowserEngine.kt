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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.searchlauncher.app.SearchLauncherApp
import com.searchlauncher.app.ui.MainActivity
import com.searchlauncher.app.ui.components.SearchChromeBar
import com.searchlauncher.app.util.displayPageAddress
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
    val localTab = remember { BrowserTab(navigationRequest?.url ?: "about:blank") }
    val tab = if (privateMode) localTab else pinnedTabId?.let(BrowserTabStore::tab) ?: return
    val page = remember(tab.id) { GeckoPage(activity, tab, privateMode, onClose) }
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
          Lifecycle.Event.ON_RESUME -> page.session.setActive(true)
          Lifecycle.Event.ON_PAUSE -> {
            page.capture()
            page.persist()
            page.session.setActive(false)
          }
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
        page.session.close()
        if (activity.isFinishing && !privateMode) page.forget()
      }
    }
    LaunchedEffect(navigationRequest?.sequence) {
      navigationRequest?.let { if (it.url != page.tab.url) page.session.loadUri(it.url) }
    }
    LaunchedEffect(browserMenuRequest) {
      if (browserMenuRequest != 0L) {
        menu = true
        onBrowserMenuShown()
      }
    }
    LaunchedEffect(page.fullscreen) {
      val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
      controller.isAppearanceLightStatusBars = true
      controller.isAppearanceLightNavigationBars = true
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
    BoxWithConstraints(Modifier.fillMaxSize()) {
      val screenWidth = maxWidth
      val screenHeight = maxHeight
      Column(
        Modifier.fillMaxSize()
          .windowInsetsPadding(if (page.fullscreen) WindowInsets(0) else WindowInsets.safeDrawing)
          .imePadding()
      ) {
        if (!page.fullscreen) {
          Text(
            if (privateMode) "Gecko experiment · Private" else "Gecko experiment",
            style = MaterialTheme.typography.labelSmall,
            modifier =
              Modifier.fillMaxWidth()
                .clickable { showInfo = true }
                .padding(horizontal = 16.dp, vertical = 2.dp),
          )
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
          AndroidView(
            factory = { context ->
              GeckoView(context).also {
                it.setBackgroundColor(AndroidColor.WHITE)
                it.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_YES
                it.setSession(page.session)
                page.view = it
              }
            },
            modifier =
              Modifier.fillMaxSize().onGloballyPositioned {
                // Compose positions AndroidView using transforms. Notify Gecko after placement so
                // its screen-space accessibility and input bounds include the header/inset offset.
                page.view?.let { view ->
                  view.post { view.gatherTransparentRegion(android.graphics.Region()) }
                }
              },
          )
          page.error?.let { message ->
            Surface(Modifier.fillMaxSize()) {
              Column(Modifier.padding(24.dp)) {
                Text("Could not open this page", style = MaterialTheme.typography.titleLarge)
                Text(message, modifier = Modifier.padding(vertical = 16.dp))
                Button(
                  onClick = {
                    page.error = null
                    page.session.reload()
                  }
                ) {
                  Text("Try again")
                }
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
        if (!page.fullscreen && showLauncherChrome) {
          SearchChromeBar(isIndexing = false, modifier = Modifier.padding(vertical = 8.dp)) {
            Text(
              displayPageAddress(tab.url).ifBlank { "Search anything…" },
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              modifier =
                Modifier.weight(1f)
                  .clickable {
                    page.capture()
                    onOpenSearch(false, tab.frameColorArgb, "")
                  }
                  .padding(vertical = 12.dp),
            )
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
              IconButton(
                onClick = {
                  page.capture()
                  overview = true
                },
                modifier = Modifier.size(36.dp),
              ) {
                Text((BrowserTabStore.tabs?.items?.size ?: 1).toString(), modifier = Modifier)
              }
            Box {
              IconButton(onClick = { menu = true }, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.MoreVert, "Browser menu")
              }
              DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                fun run(action: () -> Unit) {
                  menu = false
                  action()
                }
                DropdownMenuItem(
                  text = { Text("Back") },
                  enabled = page.canGoBack,
                  onClick = { run { page.session.goBack() } },
                )
                DropdownMenuItem(
                  text = { Text("Forward") },
                  enabled = page.canGoForward,
                  onClick = { run { page.session.goForward() } },
                )
                DropdownMenuItem(
                  text = { Text("Reload") },
                  onClick = { run { page.session.reload() } },
                )
                DropdownMenuItem(
                  text = { Text("New tab") },
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
                  onClick = { run { activity.finishAndRemoveTask() } },
                )
                DropdownMenuItem(
                  text = { Text("Home") },
                  onClick = {
                    run {
                      page.capture()
                      activity.startActivity(
                        Intent(activity, MainActivity::class.java)
                          .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                      )
                    }
                  },
                )
                DropdownMenuItem(
                  text = { Text("Downloads") },
                  onClick = {
                    run { activity.startActivity(Intent(activity, DownloadsActivity::class.java)) }
                  },
                )
                DropdownMenuItem(
                  text = { Text("Find in page") },
                  onClick = { run { find = !find } },
                )
                DropdownMenuItem(
                  text = { Text(if (tab.desktopMode) "Mobile site" else "Desktop site") },
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
                DropdownMenuItem(
                  text = { Text("About this experiment") },
                  onClick = { run { showInfo = true } },
                )
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
            scrimColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
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
