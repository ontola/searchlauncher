package com.searchlauncher.app.ui.components

import android.net.Uri
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.datastore.preferences.core.edit
import androidx.test.platform.app.InstrumentationRegistry
import com.searchlauncher.app.ui.PreferencesKeys
import com.searchlauncher.app.ui.dataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class WallpaperPagerMotionTest {
  @get:Rule val compose = createComposeRule()

  private val context = InstrumentationRegistry.getInstrumentation().targetContext
  private var originalUri: String? = null

  @Before
  fun savePreference() {
    runBlocking {
      originalUri = context.dataStore.data.first()[PreferencesKeys.BACKGROUND_LAST_IMAGE_URI]
    }
  }

  @After
  fun restorePreference() {
    runBlocking {
      context.dataStore.edit {
        if (originalUri == null) it.remove(PreferencesKeys.BACKGROUND_LAST_IMAGE_URI)
        else it[PreferencesKeys.BACKGROUND_LAST_IMAGE_URI] = originalUri!!
      }
    }
  }

  @Test
  fun wallpaperFinishesWithoutAnEarlyJump() {
    val images = listOf(Uri.parse("file:///fixture-a.jpg"), Uri.parse("file:///fixture-b.jpg"))
    runBlocking {
      context.dataStore.edit {
        it[PreferencesKeys.BACKGROUND_LAST_IMAGE_URI] = images[0].toString()
      }
    }
    lateinit var state: PagerState
    var request by mutableIntStateOf(0)
    var publishedDuringMotion = false
    val motion = mutableListOf<Boolean>()
    compose.setContent {
      val prefs by context.dataStore.data.collectAsState(initial = null)
      state = rememberPagerState(initialPage = 1073741822, pageCount = { Int.MAX_VALUE })
      if (prefs != null)
        MaterialTheme {
          WallpaperPager(
            images,
            prefs!![PreferencesKeys.BACKGROUND_LAST_IMAGE_URI],
            Modifier.fillMaxSize(),
            { if (state.isScrollInProgress) publishedDuringMotion = true },
            request,
            state,
            { motion += it },
          )
        }
    }
    compose.waitForIdle()
    compose.mainClock.autoAdvance = false
    val origin = state.currentPage
    compose.runOnUiThread { request++ }
    val samples = mutableListOf<Float>()
    repeat(80) {
      compose.mainClock.advanceTimeByFrame()
      compose.runOnUiThread {
        samples += (state.currentPage - origin + state.currentPageOffsetFraction)
      }
      Thread.sleep(5)
    }
    println("WALLPAPER_FRAMES=" + samples.joinToString(","))
    assertFalse(
      "Selection must not trigger theme extraction or preview capture during motion",
      publishedDuringMotion,
    )
    assertEquals(listOf(false, true, false), motion)
    assertEquals(
      images[1].toString(),
      runBlocking { context.dataStore.data.first()[PreferencesKeys.BACKGROUND_LAST_IMAGE_URI] },
    )
    assertEquals(1f, samples.last(), 0.001f)
    val beforeFinish = samples.lastOrNull { it < 0.999f } ?: error("No motion")
    assertTrue("Final step must not skip the tail: $beforeFinish", beforeFinish > 0.99f)
  }

  @Test
  fun reversingADragDoesNotPublishTheWallpaperPassedHalfway() {
    val images = listOf(Uri.parse("file:///fixture-a.jpg"), Uri.parse("file:///fixture-b.jpg"))
    runBlocking {
      context.dataStore.edit {
        it[PreferencesKeys.BACKGROUND_LAST_IMAGE_URI] = images[0].toString()
      }
    }
    lateinit var state: PagerState
    val selected = mutableListOf<Uri>()
    compose.setContent {
      val prefs by context.dataStore.data.collectAsState(initial = null)
      state = rememberPagerState(initialPage = 1073741822, pageCount = { Int.MAX_VALUE })
      if (prefs != null)
        MaterialTheme {
          WallpaperPager(
            images,
            prefs!![PreferencesKeys.BACKGROUND_LAST_IMAGE_URI],
            Modifier.fillMaxSize(),
            { selected += it },
            0,
            state,
          )
        }
    }
    compose.waitForIdle()
    compose.mainClock.autoAdvance = false
    val origin = state.currentPage
    compose.onRoot().performTouchInput {
      down(center)
      moveBy(androidx.compose.ui.geometry.Offset(-width * 0.65f, 0f), delayMillis = 180)
    }
    repeat(5) {
      compose.mainClock.advanceTimeByFrame()
      Thread.sleep(5)
    }
    assertEquals(origin + 1, state.currentPage)
    assertEquals("Crossing halfway must not save a selection", listOf(images[0]), selected)
    compose.onRoot().performTouchInput {
      moveBy(androidx.compose.ui.geometry.Offset(width * 0.6f, 0f), delayMillis = 180)
      up()
    }
    repeat(80) {
      compose.mainClock.advanceTimeByFrame()
      Thread.sleep(5)
    }
    assertEquals(origin, state.currentPage)
    assertEquals(0f, state.currentPageOffsetFraction, 0.001f)
    assertEquals("A reversed swipe must not recolor the home screen", listOf(images[0]), selected)
  }
}
