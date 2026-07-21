package com.searchlauncher.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun SearchChromeBar(
  isIndexing: Boolean,
  modifier: Modifier = Modifier,
  color: Color = MaterialTheme.colorScheme.surface,
  contentColor: Color = contentColorFor(color),
  tonalElevation: Dp = 3.dp,
  shadowElevation: Dp = 0.dp,
  content: @Composable RowScope.() -> Unit,
) {
  Surface(
    modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp),
    shape = RoundedCornerShape(16.dp),
    color = color,
    contentColor = contentColor,
    tonalElevation = tonalElevation,
    shadowElevation = shadowElevation,
  ) {
    Box {
      AnimatedVisibility(
        visible = isIndexing,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier.align(Alignment.TopCenter),
      ) {
        LinearProgressIndicator(
          modifier =
            Modifier.fillMaxWidth()
              .height(2.dp)
              .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)),
          color = MaterialTheme.colorScheme.primary,
          trackColor = androidx.compose.ui.graphics.Color.Transparent,
        )
      }

      Row(
        modifier =
          Modifier.fillMaxWidth()
            .heightIn(min = 40.dp)
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
      )
    }
  }
}
