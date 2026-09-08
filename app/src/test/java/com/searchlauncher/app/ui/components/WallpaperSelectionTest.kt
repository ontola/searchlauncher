package com.searchlauncher.app.ui.components

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.searchlauncher.app.ui.PreferencesKeys
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class WallpaperSelectionTest {
  @get:Rule val folder = TemporaryFolder()

  @Test
  fun `every wallpaper stays selected across repeated forward and backward cycles`() = runBlocking {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val store =
      PreferenceDataStoreFactory.create(scope = scope) {
        File(folder.root, "wallpaper.preferences_pb")
      }
    try {
      val images = listOf("file:///a.jpg", "file:///b.jpg", "file:///c.jpg", "file:///d.jpg")
      persistWallpaperSelection(store, images.first())
      repeat(5) {
        for (uri in images + images.reversed()) {
          persistWallpaperSelection(store, uri)
          assertEquals(uri, store.data.first()[PreferencesKeys.BACKGROUND_LAST_IMAGE_URI])
        }
      }
    } finally {
      scope.cancel()
    }
  }
}
