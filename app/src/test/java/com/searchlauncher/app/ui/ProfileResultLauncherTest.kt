package com.searchlauncher.app.ui

import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.os.UserHandle
import android.os.UserManager
import androidx.test.core.app.ApplicationProvider
import com.searchlauncher.app.data.SearchRepository
import com.searchlauncher.app.data.SearchResult
import io.mockk.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ProfileResultLauncherTest {
  @Test
  fun cachedWorkAppAndShortcutLaunchInTheirOriginalProfile() {
    val user = UserHandle.getUserHandleForUid(1000000)
    val component = ComponentName("org.example", "org.example.Main")
    val users = mockk<UserManager> { every { getUserForSerialNumber(42L) } returns user }
    val info = mockk<LauncherActivityInfo> { every { componentName } returns component }
    val apps =
      mockk<LauncherApps>(relaxed = true) {
        every { getActivityList("org.example", user) } returns listOf(info)
        every { hasShortcutHostPermission() } returns true
      }
    val context = spyk(ApplicationProvider.getApplicationContext<Context>())
    every { context.getSystemService(UserManager::class.java) } returns users
    every { context.getSystemService(Context.LAUNCHER_APPS_SERVICE) } returns apps
    val launcher =
      ResultLauncher(
        context,
        mockk<SearchRepository>(relaxed = true),
        CoroutineScope(Dispatchers.Unconfined),
      )
    launcher.launch(
      SearchResult.App(
        "org.example@42",
        title = "Example",
        subtitle = null,
        icon = null,
        packageName = "org.example",
      ),
      reportUsage = false,
    )
    launcher.launch(
      SearchResult.Shortcut(
        "org.example@42/chat/path",
        title = "Chat",
        subtitle = null,
        icon = null,
        packageName = "org.example",
        intentUri = "shortcut://org.example/chat/path",
      ),
      reportUsage = false,
    )
    verify { apps.startMainActivity(component, user, null, null) }
    verify { apps.startShortcut("org.example", "chat/path", null, null, user) }
  }
}
