package com.searchlauncher.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.searchlauncher.app.data.FavoritesRepository
import com.searchlauncher.app.data.HistoryRepository
import com.searchlauncher.app.data.SearchRepository
import com.searchlauncher.app.data.SearchShortcutRepository
import com.searchlauncher.app.data.SnippetsRepository
import com.searchlauncher.app.data.WallpaperRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SearchLauncherApp : Application() {

  lateinit var searchRepository: SearchRepository
    private set

  lateinit var snippetsRepository: SnippetsRepository
    private set

  lateinit var favoritesRepository: FavoritesRepository
    private set

  lateinit var searchShortcutRepository: com.searchlauncher.app.data.SearchShortcutRepository
    private set

  lateinit var widgetRepository: com.searchlauncher.app.data.WidgetRepository
    private set

  lateinit var wallpaperRepository: WallpaperRepository
    private set

  lateinit var historyRepository: HistoryRepository
    private set

  override fun onCreate() {
    super.onCreate()
    searchRepository = SearchRepository(this)
    snippetsRepository = SnippetsRepository(this)
    favoritesRepository = FavoritesRepository(this)
    searchShortcutRepository = com.searchlauncher.app.data.SearchShortcutRepository(this)
    widgetRepository = com.searchlauncher.app.data.WidgetRepository(this)
    wallpaperRepository = WallpaperRepository(this)
    historyRepository = HistoryRepository(this)
    CoroutineScope(Dispatchers.IO).launch { searchRepository.initialize() }
    createNotificationChannel()
  }

  private fun createNotificationChannel() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val channel =
        NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "SearchLauncher Service",
            NotificationManager.IMPORTANCE_LOW,
          )
          .apply {
            description = "Keeps SearchLauncher running in the background"
            setShowBadge(false)
          }

      val notificationManager =
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
      notificationManager.createNotificationChannel(channel)
    }
  }

  companion object {
    const val NOTIFICATION_CHANNEL_ID = "searchlauncher_service"
    const val NOTIFICATION_ID = 1001
  }
}
