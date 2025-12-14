package com.searchlauncher.app.ui.components

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.datastore.preferences.core.edit
import coil.compose.AsyncImage
import com.searchlauncher.app.ui.MainActivity
import com.searchlauncher.app.ui.dataStore

@Composable
fun WallpaperBackground(
  showBackgroundImage: Boolean,
  bottomPadding: Dp,
  modifier: Modifier = Modifier,
  folderImages: List<Uri> = emptyList(),
  lastImageUriString: String? = null,
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

  Box(modifier = modifier.fillMaxSize()) {
    if (showBackgroundImage && folderImages.isNotEmpty()) {
      HorizontalPager(
        state = pagerState,
        modifier = Modifier.fillMaxSize(),
        userScrollEnabled = folderImages.size > 1,
        // Allow swiping beyond bounds? Default is fine.
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
  }
}
