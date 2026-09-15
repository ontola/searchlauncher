package com.searchlauncher.app.data

import android.content.ComponentName
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AppProfileIndexerTest {
  @Test
  fun samePackageInTwoProfilesProducesSeparateSearchEntriesAndFingerprint() = runBlocking {
    val personal = Process.myUserHandle()
    val work = UserHandle.getUserHandleForUid(1000000)
    val users = mockk<UserManager> { every { getSerialNumberForUser(work) } returns 42L }
    val info =
      mockk<LauncherActivityInfo> {
        every { label } returns "Example"
        every { componentName } returns ComponentName("org.example", "org.example.Main")
        every { applicationInfo } returns ApplicationInfo()
      }
    val apps =
      mockk<LauncherApps> {
        every { profiles } returns listOf(personal, work)
        every { getActivityList(null, any()) } returns listOf(info, info)
      }
    val context =
      mockk<Context> {
        every { getSystemService(Context.LAUNCHER_APPS_SERVICE) } returns apps
        every { getSystemService(UserManager::class.java) } returns users
      }
    val indexer = AppIndexer(context)
    val docs = indexer.buildDocuments({})
    assertEquals(setOf("org.example", "org.example@42"), docs.map { it.id }.toSet())
    assertEquals(2, docs.size)
    assertTrue(docs.last().description!!.contains("Work profile"))
    val both = indexer.readFingerprint()
    every { apps.profiles } returns listOf(personal)
    assertNotEquals(both, indexer.readFingerprint())
  }
}
