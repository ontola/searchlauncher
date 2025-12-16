package com.searchlauncher.app.ui.components

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.datastore.preferences.core.edit
import coil.compose.AsyncImage
import com.searchlauncher.app.service.GestureAccessibilityService
import com.searchlauncher.app.ui.MainActivity
import com.searchlauncher.app.ui.WidgetHostViewFactory
import com.searchlauncher.app.ui.dataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@Composable
fun WallpaperBackground(
  showBackgroundImage: Boolean,
  bottomPadding: Dp,
  modifier: Modifier = Modifier,
  folderImages: List<Uri> = emptyList(),
  lastImageUriString: String? = null,
  onOpenAppDrawer: () -> Unit = {},
  onLongPress: (Offset) -> Unit = {},
  onTap: () -> Unit = {},
) {
  val context = LocalContext.current

  val contentModifier = Modifier.fillMaxSize().padding(bottom = bottomPadding)
  val pagerState =
    rememberPagerState(pageCount = { if (folderImages.isNotEmpty()) Int.MAX_VALUE else 0 })

  // Scroll to saved page or random page initially when images are loaded
  LaunchedEffect(folderImages) {
    if (folderImages.isNotEmpty()) {
      val targetIndex =
        if (lastImageUriString != null) {
          val uri = Uri.parse(lastImageUriString)
          val index = folderImages.indexOf(uri)
          if (index != -1) index else 0
        } else {
          0
        }

      // Calculate a page in the middle of MAX_VALUE that maps to targetIndex
      val startIndex = Int.MAX_VALUE / 2
      val startOffset = startIndex % folderImages.size
      val initialPage = startIndex - startOffset + targetIndex

      pagerState.scrollToPage(initialPage)
    }
  }

  // Save current image URI when page changes
  LaunchedEffect(pagerState) {
    snapshotFlow { pagerState.currentPage }
      .collect { page ->
        if (folderImages.isNotEmpty()) {
          val actualIndex = page % folderImages.size
          val currentUri = folderImages[actualIndex].toString()
          if (currentUri != lastImageUriString) {
            context.dataStore.edit { prefs ->
              prefs[MainActivity.PreferencesKeys.BACKGROUND_LAST_IMAGE_URI] = currentUri
            }
          }
        }
      }
  }

  // Visibility toggle for widgets
  val showWidgetsFlow =
    remember(context) {
      context.dataStore.data.map { preferences ->
        preferences[MainActivity.PreferencesKeys.SHOW_WIDGETS] ?: true
      }
    }
  val showWidgets by showWidgetsFlow.collectAsState(initial = true)

  Box(
    modifier =
      modifier
        .fillMaxSize()
        .pointerInput(Unit) {
          detectDragGestures { change, dragAmount ->
            if (dragAmount.y > 20) {
              val isLeft = change.position.x < size.width / 2
              if (isLeft) {
                if (!GestureAccessibilityService.openNotifications()) {
                  com.searchlauncher.app.util.SystemUtils.expandNotifications(context)
                }
              } else {
                if (!GestureAccessibilityService.openQuickSettings()) {
                  com.searchlauncher.app.util.SystemUtils.expandQuickSettings(context)
                }
              }
            } else if (dragAmount.y < -20) {
              onOpenAppDrawer()
            }
          }
        }
        .pointerInput(Unit) {
          detectTapGestures(
            onTap = {
              val newState = !showWidgets
              val scope = CoroutineScope(Dispatchers.IO)
              scope.launch {
                context.dataStore.edit { preferences ->
                  preferences[MainActivity.PreferencesKeys.SHOW_WIDGETS] = newState
                }
              }
              onTap()
            },
            onLongPress = { offset -> onLongPress(offset) },
          )
        }
  ) {
    if (showBackgroundImage && folderImages.isNotEmpty()) {
      HorizontalPager(
        state = pagerState,
        modifier = Modifier.fillMaxSize(),
        userScrollEnabled = folderImages.size > 1,
      ) { page ->
        val imageIndex = page % folderImages.size
        Box(modifier = Modifier.fillMaxSize()) {
          AsyncImage(
            model = folderImages[imageIndex],
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = contentModifier,
          )
        }
      }
    } else {
      // Solid background when no image - semi-transparent so app underneath shows through
      Box(
        modifier =
          contentModifier.background(MaterialTheme.colorScheme.background.copy(alpha = 0.7f))
      )
    }

    // Widget Layer

    val app = context.applicationContext as com.searchlauncher.app.SearchLauncherApp
    val widgets by app.widgetRepository.widgets.collectAsState(initial = emptyList())

    var activeWidgetId by remember { mutableIntStateOf(-1) }
    var showResizeDialog by remember { mutableStateOf(false) }
    var resizeHeight by remember { mutableStateOf(400f) }

    val scope = rememberCoroutineScope()

    // Nested scroll for handling "Pull Down" on widgets to open notifications
    var accumulatedPull by remember { mutableFloatStateOf(0f) }
    val nestedScrollConnection = remember {
      object : NestedScrollConnection {
        override fun onPostScroll(
          consumed: Offset,
          available: Offset,
          source: NestedScrollSource,
        ): Offset {
          if (available.y > 0) {
            accumulatedPull += available.y
            if (accumulatedPull > 150f) { // Threshold
              accumulatedPull = 0f
              com.searchlauncher.app.util.SystemUtils.expandNotifications(context)
            }
          } else {
            accumulatedPull = 0f
          }
          return super.onPostScroll(consumed, available, source)
        }
      }
    }

    if (widgets.isNotEmpty()) {
      AnimatedVisibility(visible = showWidgets, enter = fadeIn(), exit = fadeOut()) {
        val isAnyWidgetActive = activeWidgetId != -1
        if (isAnyWidgetActive) {
          BackHandler { activeWidgetId = -1 }
        }

        Box(modifier = Modifier.fillMaxSize()) {
          if (isAnyWidgetActive) {
            Box(
              modifier =
                Modifier.fillMaxSize()
                  .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.5f))
                  .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                  ) {
                    activeWidgetId = -1
                  }
                  .zIndex(1f) // Backdrop above list? No, sibling of Column below?
            )
          }

          Column(
            modifier =
              Modifier.fillMaxWidth()
                .padding(top = 24.dp, start = 16.dp, end = 16.dp, bottom = bottomPadding + 80.dp)
                .nestedScroll(nestedScrollConnection)
                .verticalScroll(rememberScrollState())
                .zIndex(2f), // List above backdrop?
            verticalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            widgets.forEachIndexed { index, widget ->
              androidx.compose.runtime.key(widget.id) {
                class WidgetContainerView(context: android.content.Context) :
                  android.widget.FrameLayout(context) {
                  private var onLongPressListener: (() -> Unit)? = null
                  private val gestureDetector =
                    android.view.GestureDetector(
                      context,
                      object : android.view.GestureDetector.SimpleOnGestureListener() {
                        override fun onLongPress(e: android.view.MotionEvent) {
                          onLongPressListener?.invoke()
                          // Cancel child touches by sending ACTION_CANCEL
                          val cancelEvent =
                            android.view.MotionEvent.obtain(
                              e.downTime,
                              e.eventTime,
                              android.view.MotionEvent.ACTION_CANCEL,
                              e.x,
                              e.y,
                              0,
                            )
                          (0 until childCount).forEach { i ->
                            getChildAt(i).dispatchTouchEvent(cancelEvent)
                          }
                          cancelEvent.recycle()
                        }
                      },
                    )

                  fun setOnLongPressAction(action: () -> Unit) {
                    onLongPressListener = action
                  }

                  override fun onInterceptTouchEvent(ev: android.view.MotionEvent): Boolean {
                    gestureDetector.onTouchEvent(ev)
                    return super.onInterceptTouchEvent(ev)
                  }

                  override fun dispatchTouchEvent(ev: android.view.MotionEvent): Boolean {
                    gestureDetector.onTouchEvent(ev)
                    return super.dispatchTouchEvent(ev)
                  }
                }

                val isResizing = activeWidgetId == widget.id
                val heightModifier =
                  if (isResizing) {
                    Modifier.height(resizeHeight.dp)
                  } else if (widget.height != null) {
                    Modifier.height(widget.height.dp)
                  } else {
                    Modifier.wrapContentHeight()
                  }

                // If active, lift up. If not active but something else is, fade out.
                val zIndex = if (isResizing) 10f else 0f
                val alpha = if (activeWidgetId != -1 && !isResizing) 0.3f else 1f

                Box(
                  modifier =
                    Modifier.fillMaxWidth().then(heightModifier).zIndex(zIndex).alpha(alpha)
                ) {
                  // Drag Logic for Resize
                  val density = androidx.compose.ui.platform.LocalDensity.current

                  val androidViewModifier =
                    if (isResizing || widget.height != null) {
                      Modifier.fillMaxSize()
                    } else {
                      Modifier.fillMaxWidth()
                    }

                  AndroidView(
                    factory = { ctx ->
                      val container = WidgetContainerView(ctx)
                      container.setOnLongPressAction {
                        activeWidgetId = widget.id
                        resizeHeight = widget.height?.toFloat() ?: 400f
                      }

                      val activity = ctx as? MainActivity
                      if (activity != null) {
                        val widgetView =
                          WidgetHostViewFactory.createWidgetView(
                            ctx,
                            widget.id,
                            activity.appWidgetHost,
                            activity.appWidgetManager,
                          )
                        container.addView(widgetView)
                      }
                      container
                    },
                    update = { container ->
                      container.setOnLongPressAction {
                        activeWidgetId = widget.id
                        resizeHeight = widget.height?.toFloat() ?: 400f
                      }
                    },
                    modifier = androidViewModifier,
                  )

                  // Edit Overlay (Border + Handles + Toolbar)
                  if (activeWidgetId == widget.id) {
                    // Border
                    Box(
                      modifier =
                        Modifier.fillMaxSize()
                          .background(Color.Transparent)
                          .border(
                            width = 2.dp,
                            color = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(16.dp),
                          )
                    )

                    // Resize Handle (Bottom)
                    Box(
                      modifier =
                        Modifier.align(Alignment.BottomCenter)
                          .padding(bottom = 6.dp)
                          .size(40.dp, 24.dp)
                          .background(
                            color = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(12.dp),
                          )
                          .pointerInput(Unit) {
                            detectDragGestures(
                              onDragEnd = {
                                scope.launch {
                                  app.widgetRepository.updateWidgetHeight(
                                    widget.id,
                                    resizeHeight.toInt(),
                                  )
                                }
                              }
                            ) { change, dragAmount ->
                              change.consume()
                              val newHeight = resizeHeight + (dragAmount.y / density.density)
                              resizeHeight = newHeight.coerceIn(50f, 1000f)
                            }
                          },
                      contentAlignment = Alignment.Center,
                    ) {
                      Icon(
                        imageVector = Icons.Default.ArrowDownward,
                        contentDescription = "Resize",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(16.dp),
                      )
                    }

                    // Toolbar (Top Right)
                    WidgetEditToolbar(
                      showMoveUp = index > 0,
                      showMoveDown = index < widgets.size - 1,
                      onMoveUp = { scope.launch { app.widgetRepository.moveWidgetUp(widget.id) } },
                      onMoveDown = {
                        scope.launch { app.widgetRepository.moveWidgetDown(widget.id) }
                      },
                      onDelete = {
                        scope.launch {
                          app.widgetRepository.removeWidgetId(widget.id)
                          (context as? MainActivity)?.appWidgetHost?.deleteAppWidgetId(widget.id)
                          activeWidgetId = -1
                        }
                      },
                      onDone = { activeWidgetId = -1 },
                      modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                    )
                  }
                }
              }
            }
          }
        }
      }
    }
  }
}
