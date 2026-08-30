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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clipToBounds
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
import com.searchlauncher.app.data.WidgetData
import com.searchlauncher.app.ui.MainActivity
import com.searchlauncher.app.ui.PreferencesKeys
import com.searchlauncher.app.ui.WidgetHostViewFactory
import com.searchlauncher.app.ui.dataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Widgets are hosted by [MainActivity]'s [android.appwidget.AppWidgetHost]. The browser's
 * translucent search overlay is a different activity, so drawing them there produced "Widget
 * unavailable" cards for every bound id.
 */
internal fun homeWidgetsEnabled(context: android.content.Context): Boolean = context is MainActivity

/**
 * Page of the endlessly looping pager that shows [uriString], defaulting to the first image.
 *
 * The loop is centred on the middle of the index space so swiping backwards works from the start.
 */
private fun pageShowing(images: List<Uri>, uriString: String?): Int {
  val startIndex = Int.MAX_VALUE / 2
  val imageIndex = uriString?.let { images.indexOf(Uri.parse(it)) }?.takeIf { it >= 0 } ?: 0
  return startIndex - (startIndex % images.size) + imageIndex
}

@Composable
private fun WallpaperPager(
  folderImages: List<Uri>,
  lastImageUriString: String?,
  contentModifier: Modifier,
  onPageChanged: (Uri) -> Unit,
) {
  val context = LocalContext.current
  // Only read when the state is first created, which is why this composable waits for the saved
  // URI: the pager opens on the right image instead of correcting itself a frame later.
  val pagerState =
    rememberPagerState(
      initialPage = remember { pageShowing(folderImages, lastImageUriString) },
      pageCount = { Int.MAX_VALUE },
    )

  // Follow the saved URI when something else changes it, such as adding a wallpaper in settings.
  LaunchedEffect(folderImages, lastImageUriString) {
    val targetPage = pageShowing(folderImages, lastImageUriString)
    // Already showing the wanted image: the saved URI is rewritten whenever a page settles, and
    // that must not yank the pager back to the canonical page.
    if (pagerState.currentPage % folderImages.size != targetPage % folderImages.size) {
      pagerState.scrollToPage(targetPage)
    }
  }

  val currentOnPageChanged by androidx.compose.runtime.rememberUpdatedState(onPageChanged)

  // Save current image URI when page changes
  LaunchedEffect(pagerState, folderImages) {
    snapshotFlow { pagerState.currentPage }
      .collect { page ->
        val currentUri = folderImages[page % folderImages.size]
        currentOnPageChanged(currentUri)

        if (currentUri.toString() != lastImageUriString) {
          context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.BACKGROUND_LAST_IMAGE_URI] = currentUri.toString()
          }
        }
      }
  }

  HorizontalPager(
    state = pagerState,
    modifier = Modifier.fillMaxSize(),
    userScrollEnabled = folderImages.size > 1,
  ) { page ->
    Box(modifier = Modifier.fillMaxSize()) {
      AsyncImage(
        model = folderImages[page % folderImages.size],
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = contentModifier,
      )
    }
  }
}

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
  onPageChanged: (Uri) -> Unit = {},
  onSwipeDownLeft: () -> Unit = {},
  onSwipeDownRight: () -> Unit = {},
  savedUriResolved: Boolean = true,
  /**
   * Measured favorites bar plus search chrome. Extra favorites rows grow this; a one-row guess left
   * widgets sitting under a taller bar.
   */
  bottomSectionHeight: Dp = WIDGET_BOTTOM_SECTION_FALLBACK,
  /**
   * IME / nav inset the chrome bar actually sits on. Widgets have to use this, not the wallpaper's
   * stored keyboard height, or they slide under the live favorites bar.
   */
  chromeBottomPadding: Dp = bottomPadding,
) {
  val context = LocalContext.current
  // The browser overlay is a different activity and has no AppWidgetHost. Drawing widgets there
  // produced an error card for every bound id; they belong on the home screen only.
  val widgetsEnabled = homeWidgetsEnabled(context)

  val contentModifier = Modifier.fillMaxSize().padding(bottom = bottomPadding)

  // Visibility toggle for widgets
  val showWidgetsFlow =
    remember(context) {
      context.dataStore.data
        .map { preferences -> preferences[PreferencesKeys.SHOW_WIDGETS] ?: true }
        .distinctUntilChanged()
    }
  val showWidgets by showWidgetsFlow.collectAsState(initial = true)

  // Hoisted above the background's gestures because a tap means different things depending on
  // whether a widget is being edited.
  var activeWidgetId by remember { mutableIntStateOf(-1) }

  Box(
    modifier =
      modifier
        .fillMaxSize()
        .pointerInput(Unit) {
          detectDragGestures { change, dragAmount ->
            if (dragAmount.y > 20) {
              val isLeft = change.position.x < size.width / 2
              if (isLeft) {
                onSwipeDownLeft()
              } else {
                onSwipeDownRight()
              }
            } else if (dragAmount.y < -20) {
              onOpenAppDrawer()
            }
          }
        }
        .pointerInput(Unit) {
          detectTapGestures(
            onTap = {
              if (activeWidgetId != -1) {
                // Tapping away from a widget being edited means "done". It leaves the editor and
                // stops there: hiding the widgets in the same gesture would take away the thing
                // that was just being arranged. A second tap then toggles them as it always does.
                activeWidgetId = -1
              } else {
                if (widgetsEnabled) {
                  val newState = !showWidgets
                  val scope = CoroutineScope(Dispatchers.IO)
                  scope.launch {
                    context.dataStore.edit { preferences ->
                      preferences[PreferencesKeys.SHOW_WIDGETS] = newState
                    }
                  }
                }
                onTap()
              }
            },
            onLongPress = { offset -> onLongPress(offset) },
          )
        }
  ) {
    // Held back until the saved wallpaper is known: starting the pager before then showed the
    // first image and then jumped to the saved one, which read as the background flickering
    // between two pictures during start-up.
    if (showBackgroundImage && folderImages.isNotEmpty() && savedUriResolved) {
      WallpaperPager(
        folderImages = folderImages,
        lastImageUriString = lastImageUriString,
        contentModifier = contentModifier,
        onPageChanged = onPageChanged,
      )
    } else {
      // No custom images: the theme window is transparent with FLAG_SHOW_WALLPAPER, so the
      // system wallpaper shows through without the app reading storage or photos.
      Box(modifier = contentModifier.background(androidx.compose.ui.graphics.Color.Transparent))
    }

    // Widget Layer

    val app = context.applicationContext as com.searchlauncher.app.SearchLauncherApp
    val widgets by app.widgetRepository.widgets.collectAsState(initial = emptyList())

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

    if (widgetsEnabled && widgets.isNotEmpty()) {
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

          BoxWithConstraints(modifier = Modifier.fillMaxSize().zIndex(2f)) {
            val columnSpacing = 8.dp
            val topPadding = 24.dp
            val listBottomPadding =
              widgetsListBottomPadding(chromeBottomPadding, bottomSectionHeight)
            // As many columns as fit at the maximum width, so a phone keeps its single column and
            // a tablet stops stretching one widget across the whole display.
            val columnCount =
              ((maxWidth + columnSpacing) / (WIDGET_COLUMN_MAX_WIDTH + columnSpacing))
                .toInt()
                .coerceAtLeast(1)
            val widgetListHeight = (maxHeight - topPadding - listBottomPadding).coerceAtLeast(0.dp)
            val widgetColumns =
              remember(widgets, columnCount, widgetListHeight) {
                packWidgetsIntoColumns(widgets, columnCount, widgetListHeight)
              }

            Row(
              modifier =
                Modifier.padding(top = topPadding)
                  .fillMaxWidth()
                  .height(widgetListHeight)
                  .padding(start = 16.dp, end = 16.dp)
                  .clipToBounds(),
              horizontalArrangement = Arrangement.spacedBy(columnSpacing),
            ) {
              widgetColumns.forEachIndexed { columnIndex, columnWidgets ->
                Column(
                  modifier =
                    Modifier.weight(1f)
                      .fillMaxHeight()
                      .then(
                        // Only the last column can hold more than it has room for, so it is the
                        // only one that needs to scroll.
                        if (columnIndex == widgetColumns.lastIndex) {
                          Modifier.nestedScroll(nestedScrollConnection)
                            .verticalScroll(rememberScrollState())
                        } else {
                          Modifier
                        }
                      ),
                  verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                  columnWidgets.forEach { widget ->
                    androidx.compose.runtime.key(widget.id) {
                      class WidgetContainerView(context: android.content.Context) :
                        android.widget.FrameLayout(context) {
                        init {
                          clipChildren = true
                          clipToPadding = true
                        }

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
                      val configuration = androidx.compose.ui.platform.LocalConfiguration.current
                      val maxWidgetHeight =
                        configuration.screenHeightDp.dp - listBottomPadding - 40.dp

                      val heightModifier =
                        if (isResizing) {
                          Modifier.height(resizeHeight.dp.coerceAtMost(maxWidgetHeight))
                        } else if (widget.height != null) {
                          Modifier.height(widget.height.dp.coerceAtMost(maxWidgetHeight))
                        } else {
                          Modifier.heightIn(max = maxWidgetHeight).wrapContentHeight()
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

                        val activity = context as? MainActivity
                        // Asked once per id: a widget that cannot be drawn now will not start
                        // working
                        // while the launcher is open, and the answer costs a binder call.
                        val canRender =
                          remember(widget.id, activity) {
                            activity != null &&
                              WidgetHostViewFactory.canRender(activity.appWidgetManager, widget.id)
                          }

                        if (!canRender) {
                          MissingWidget(
                            onRemove = {
                              scope.launch {
                                app.widgetRepository.removeWidgetId(widget.id)
                                activity?.appWidgetHost?.deleteAppWidgetId(widget.id)
                                if (activeWidgetId == widget.id) activeWidgetId = -1
                              }
                            },
                            modifier = Modifier.fillMaxWidth(),
                          )
                        } else {
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
                        }

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
                                    resizeHeight = newHeight.coerceIn(50f, maxWidgetHeight.value)
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

                          // Toolbar (Top Right). Move up and down reorder the whole list, which
                          // is what decides both the order within a column and which column a
                          // widget lands in, so the position here is the one in `widgets` rather
                          // than in the column being drawn.
                          val orderIndex = widgets.indexOfFirst { it.id == widget.id }
                          WidgetEditToolbar(
                            showMoveUp = orderIndex > 0,
                            showMoveDown = orderIndex < widgets.size - 1,
                            onMoveUp = {
                              scope.launch { app.widgetRepository.moveWidgetUp(widget.id) }
                            },
                            onMoveDown = {
                              scope.launch { app.widgetRepository.moveWidgetDown(widget.id) }
                            },
                            onDelete = {
                              scope.launch {
                                app.widgetRepository.removeWidgetId(widget.id)
                                (context as? MainActivity)
                                  ?.appWidgetHost
                                  ?.deleteAppWidgetId(widget.id)
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
    }
  }
}

/**
 * Stands in for a widget the host cannot draw, most often one whose id came back from a backup and
 * no longer belongs to anything.
 *
 * It exists because the alternative was worse: an empty view still claimed the widget's space, so
 * the home screen had a silent gap in it and no way to work out what was wrong or clear it. This
 * says what happened and offers the removal, since the widget cannot be recovered — the id is gone,
 * and rebinding one needs a provider this launcher never stored.
 */
@Composable
private fun MissingWidget(onRemove: () -> Unit, modifier: Modifier = Modifier) {
  Box(
    modifier =
      modifier
        .padding(horizontal = 16.dp)
        .background(
          MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f),
          RoundedCornerShape(16.dp),
        )
        .padding(16.dp)
  ) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
          imageVector = Icons.Default.BrokenImage,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onErrorContainer,
          modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.size(8.dp))
        Text(
          text = "Widget unavailable",
          style = MaterialTheme.typography.titleSmall,
          color = MaterialTheme.colorScheme.onErrorContainer,
        )
      }
      Text(
        text =
          "Its app is gone, or it was restored from a backup. Widgets cannot be restored, so this " +
            "one has to be added again.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onErrorContainer,
      )
      TextButton(onClick = onRemove, contentPadding = PaddingValues(0.dp)) {
        Text(text = "Remove", color = MaterialTheme.colorScheme.onErrorContainer)
      }
    }
  }
}

/**
 * Widest a widget column is allowed to get.
 *
 * A single column stretched across a tablet makes every widget the width of the display, which is
 * neither what the widget was designed for nor a good use of the room. Past this width the screen
 * gets another column instead.
 */
private val WIDGET_COLUMN_MAX_WIDTH = 420.dp

/** Height assumed for a widget that has never been resized, matching the default it is given. */
private val WIDGET_DEFAULT_HEIGHT = 200.dp

/**
 * Gap between the widget list and the favorites / chrome block, matching the chrome column's own
 * bottom padding.
 */
internal val WIDGET_BOTTOM_SECTION_GAP = 12.dp

/**
 * Used until the favorites bar and chrome have been measured, and as a floor so a missing
 * measurement does not drop widgets onto the search bar.
 */
internal val WIDGET_BOTTOM_SECTION_FALLBACK = 80.dp

/**
 * How far the widget list sits above the bottom of the home screen.
 *
 * [keyboardInset] is the IME / nav inset the chrome bar sits on. [bottomSection] is the measured
 * favorites bar plus search chrome — extra rows grow it, which is why a hardcoded 80.dp left the
 * bottom of a widget behind a two-row favorites bar.
 */
internal fun widgetsListBottomPadding(
  keyboardInset: Dp,
  bottomSection: Dp,
  gap: Dp = WIDGET_BOTTOM_SECTION_GAP,
  minReserve: Dp = WIDGET_BOTTOM_SECTION_FALLBACK,
): Dp = keyboardInset + (bottomSection + gap).coerceAtLeast(minReserve)

/**
 * Fills each column before moving to the next, so widgets keep the order they were added in and
 * only spill sideways once a column is full.
 *
 * Anything still left over stays in the last column, which is the one that scrolls: better to have
 * one column longer than the screen than to silently drop a widget the user added.
 */
internal fun packWidgetsIntoColumns(
  widgets: List<WidgetData>,
  columnCount: Int,
  columnHeight: Dp,
  spacing: Dp = 4.dp,
): List<List<WidgetData>> {
  if (columnCount <= 1 || widgets.isEmpty()) return listOf(widgets)

  val columns = mutableListOf<MutableList<WidgetData>>(mutableListOf())
  var used = 0.dp
  for (widget in widgets) {
    val height = widget.height?.dp ?: WIDGET_DEFAULT_HEIGHT
    val needed = if (columns.last().isEmpty()) height else height + spacing
    // Move on only while there are columns left to move to; the last one takes the remainder.
    if (used + needed > columnHeight && columns.size < columnCount && columns.last().isNotEmpty()) {
      columns.add(mutableListOf())
      used = 0.dp
    }
    columns.last().add(widget)
    used += if (columns.last().size == 1) height else height + spacing
  }
  return columns
}
