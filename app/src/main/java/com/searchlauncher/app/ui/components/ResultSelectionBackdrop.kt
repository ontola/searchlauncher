package com.searchlauncher.app.ui.components

import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.lazy.LazyListState

/** Keep visible rows still; reveal only the part of a selection that falls outside the viewport. */
internal suspend fun LazyListState.revealResult(index: Int) {
  val layout = layoutInfo
  val item = layout.visibleItemsInfo.firstOrNull { it.index == index }
  if (item == null) {
    animateScrollToItem(index)
    return
  }
  val start = 0
  val end = layout.viewportEndOffset - layout.afterContentPadding
  val delta =
    when {
      item.offset < start -> item.offset - start
      item.offset + item.size > end -> item.offset + item.size - end
      else -> 0
    }
  if (delta != 0) animateScrollBy(delta.toFloat())
}
