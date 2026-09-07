package com.searchlauncher.app.ui.components

import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.gestures.stopScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class KeyboardResultListMotionTest {
  @get:Rule val compose = createComposeRule()

  @Test
  fun keyboardMovesActualRowsEvenWhenAllResultsFit() {
    compose.setContent {
      MaterialTheme {
        val state = rememberLazyListState()
        val scope = rememberCoroutineScope()
        val flingBehavior = ScrollableDefaults.flingBehavior()
        Column {
          LazyColumn(
            state = state,
            reverseLayout = true,
            modifier = Modifier.height(200.dp).testTag("results"),
            contentPadding = PaddingValues(top = 144.dp),
          ) {
            items(3, key = { it }) {
              Text(
                "Result $it",
                Modifier.animateItem(
                    fadeInSpec = tween(180, delayMillis = it * 16),
                    placementSpec = tween(140),
                    fadeOutSpec = tween(90),
                  )
                  .height(56.dp)
                  .fillMaxWidth(),
              )
            }
          }
          HomeSearchKeyboard(
            {},
            {},
            {},
            Modifier.height(243.dp),
            onScrollResults = { state.dispatchRawDelta(it) },
            onKeyboardTouch = {
              scope.launch(start = CoroutineStart.UNDISPATCHED) { state.stopScroll() }
            },
            onResultFling = { velocity ->
              state.scroll { with(flingBehavior) { performFling(velocity) } }
            },
          )
        }
      }
    }
    val viewport = compose.onNodeWithTag("results").fetchSemanticsNode().boundsInRoot
    compose.onNodeWithText("g").performTouchInput {
      down(center)
      moveBy(Offset(0f, 40f), delayMillis = 40)
    }
    val before = compose.onNodeWithText("Result 1").fetchSemanticsNode().positionInRoot.y
    compose.onNodeWithText("g").performTouchInput { moveBy(Offset(0f, 3f), delayMillis = 16) }
    val after = compose.onNodeWithText("Result 1").fetchSemanticsNode().positionInRoot.y
    assertEquals(3f, after - before, 0.1f)
    compose.onNodeWithText("g").performTouchInput { moveBy(Offset(0f, -2f), delayMillis = 16) }
    val reversed = compose.onNodeWithText("Result 1").fetchSemanticsNode().positionInRoot.y
    assertEquals(-2f, reversed - after, 0.1f)
    compose.onNodeWithText("g").performTouchInput { cancel() }
    assertEquals(viewport, compose.onNodeWithTag("results").fetchSemanticsNode().boundsInRoot)
    compose.onNodeWithTag("results").performTouchInput { swipeUp(durationMillis = 160) }
    compose.onNodeWithText("g").performTouchInput {
      down(center)
      moveBy(Offset(0f, 30f), delayMillis = 40)
      cancel()
    }
    assertEquals(viewport, compose.onNodeWithTag("results").fetchSemanticsNode().boundsInRoot)
  }
}
