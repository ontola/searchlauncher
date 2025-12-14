package com.searchlauncher.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.searchlauncher.app.data.SearchRepository
import com.searchlauncher.app.data.SearchResult
import com.searchlauncher.app.ui.components.SearchResultItem
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppListScreen(
  searchRepository: SearchRepository,
  onAppClick: (SearchResult) -> Unit,
  onBack: () -> Unit,
) {
  val apps by searchRepository.allApps.collectAsState(initial = emptyList())

  val groupedApps =
    remember(apps) { apps.groupBy { it.title.firstOrNull()?.uppercaseChar() ?: '#' }.toSortedMap() }

  val listState = rememberLazyListState()
  val scope = rememberCoroutineScope()
  val view = androidx.compose.ui.platform.LocalView.current
  val letters = groupedApps.keys.toList()
  var scrollerHeight by remember { mutableStateOf(0) }

  // Vertical offset for drag-to-dismiss
  val offsetY = remember { Animatable(0f) }

  // Nested scroll connection for swipe-down-to-close with translation
  val nestedScrollConnection = remember {
    object : NestedScrollConnection {
      override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        if (
          available.y > 0 &&
            listState.firstVisibleItemIndex == 0 &&
            listState.firstVisibleItemScrollOffset == 0
        ) {
          // Dragging down at top: consume and move offset
          scope.launch { offsetY.snapTo((offsetY.value + available.y).coerceAtLeast(0f)) }
          return available
        }
        return Offset.Zero
      }

      override suspend fun onPreFling(available: Velocity): Velocity {
        val isAtTop =
          listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
        if ((offsetY.value > 150f) || (isAtTop && available.y > 1000f)) {
          // If dragged enough or flung down while at top, close
          onBack()
          return available
        } else {
          // Otherwise snap back
          if (offsetY.value > 0f) {
            offsetY.animateTo(0f)
          }
        }
        return super.onPreFling(available)
      }
    }
  }

  // Adjust content position based on offsetY
  Box(
    modifier =
      Modifier.fillMaxSize()
        .offset { IntOffset(0, offsetY.value.roundToInt()) }
        .background(MaterialTheme.colorScheme.background)
        .nestedScroll(nestedScrollConnection)
        .pointerInput(Unit) {
          awaitPointerEventScope {
            while (true) {
              val event =
                awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Final)
              if (event.changes.all { !it.pressed }) {
                // Finger lifted. Check if we need to snap.
                // If onPreFling handled it, velocity might be high.
                // If velocity was low, onPreFling might have been called with 0 velocity, OR not
                // called if not a fling.
                // We check offset. If it's valid (not 0), we snap.
                val currentOffset = offsetY.value
                if (currentOffset > 0f) {
                  if (currentOffset > 150f) {
                    onBack()
                  } else {
                    scope.launch { offsetY.animateTo(0f) }
                  }
                }
              }
            }
          }
        }
        .windowInsetsPadding(WindowInsets.statusBars)
  ) {
    if (apps.isEmpty()) {
      CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
    } else {
      LazyColumn(
        state = listState,
        contentPadding =
          androidx.compose.foundation.layout.PaddingValues(
            bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 100.dp
          ),
        modifier = Modifier.fillMaxSize().padding(end = 24.dp), // Leave space for scroller
      ) {
        groupedApps.forEach { (letter, appList) ->
          stickyHeader {
            Box(
              modifier =
                Modifier.fillMaxWidth()
                  .background(MaterialTheme.colorScheme.surface)
                  .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
              Text(
                text = letter.toString(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
              )
            }
          }
          items(appList, key = { it.id }) { app ->
            SearchResultItem(result = app, onClick = { onAppClick(app) })
          }
        }
      }

      // Fast Scroller
      if (letters.isNotEmpty()) {
        Column(
          modifier =
            Modifier.align(Alignment.CenterEnd)
              .padding(vertical = 16.dp)
              .width(40.dp) // Wider touch target
              .background(
                MaterialTheme.colorScheme.surface.copy(alpha = 0.0f)
              ) // Transparent but hits tests? No, needs content or generic input
              .onGloballyPositioned { coordinates -> scrollerHeight = coordinates.size.height }
              // Add a separate tap detector?
              // detectVerticalDragGestures waits for slop.
              // To support TAP, we can add .pointerInput(Unit) { detectTapGestures ... } ?
              // If we use 'forEachGesture' manually it's better.
              // Let's use a robust implementation.
              .pointerInput(Unit) {
                awaitPointerEventScope {
                  var lastIndex = -1
                  while (true) {
                    val event = awaitPointerEvent()
                    val down = event.changes.firstOrNull { it.pressed }
                    if (down != null) {
                      // On any touch down or move that is pressed
                      val index = getIndexFromOffset(down.position.y, letters.size, scrollerHeight)

                      if (index != lastIndex) {
                        scope.launch { scrollToLetter(index, letters, groupedApps, listState) }
                        view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                        lastIndex = index
                      }
                    } else {
                      lastIndex = -1
                    }
                  }
                }
              },
          verticalArrangement = Arrangement.SpaceBetween,
          horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          letters.forEach { letter ->
            Text(
              text = letter.toString(),
              style = MaterialTheme.typography.labelSmall,
              modifier = Modifier.padding(2.dp),
              color = MaterialTheme.colorScheme.primary,
            )
          }
        }
      }
    }
  }
}

private fun getIndexFromOffset(y: Float, count: Int, height: Int): Int {
  if (height == 0) return 0
  return ((y / height) * count).toInt().coerceIn(0, count - 1)
}

private suspend fun scrollToLetter(
  letterIndex: Int,
  letters: List<Char>,
  groupedApps: Map<Char, List<SearchResult>>,
  listState: LazyListState,
) {
  if (letterIndex < 0 || letterIndex >= letters.size) return
  val letter = letters[letterIndex]

  // Calculate index in LazyColumn
  // Each group has 1 header + N items
  var scrollIndex = 0
  for ((key, value) in groupedApps) {
    if (key == letter) break
    scrollIndex += 1 + value.size
  }
  listState.scrollToItem(scrollIndex)
}

@Composable
fun Modifier.detectVerticalDragGestures(
  onDragStart: (Offset) -> Unit,
  onVerticalDrag: (PointerInputChange, Float) -> Unit,
): Modifier =
  pointerInput(Unit) {
    detectVerticalDragGestures(onDragStart = onDragStart, onVerticalDrag = onVerticalDrag)
  }
