package com.searchlauncher.app.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp

/** Smallest width at which Android treats the device as a tablet / unfolded inner display. */
const val TABLET_SMALLEST_WIDTH_DP = 600

@Composable
fun isTabletLayout(configuration: Configuration = LocalConfiguration.current): Boolean =
  configuration.smallestScreenWidthDp >= TABLET_SMALLEST_WIDTH_DP

/**
 * On phones this is a no-op. On tablets the search chrome, results and settings sit at a readable
 * column width in the middle rather than stretching edge to edge.
 *
 * Other tablet-sized layouts worth considering later, once this column is not enough: a two-pane
 * settings screen, an app-list grid instead of a single column, and a split that keeps search
 * beside the hosted browser instead of covering it.
 */
@Composable
fun Modifier.contentMaxWidth(): Modifier {
  if (!isTabletLayout()) return this
  return fillMaxWidth().wrapContentWidth(Alignment.CenterHorizontally).widthIn(max = 720.dp)
}
