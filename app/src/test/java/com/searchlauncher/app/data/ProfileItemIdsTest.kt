package com.searchlauncher.app.data

import android.content.Context
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ProfileItemIdsTest {
  private val users = mockk<UserManager>()
  private val context =
    mockk<Context> { every { getSystemService(UserManager::class.java) } returns users }

  @Test
  fun personalIdsStayCompatibleWithExistingFavorites() {
    assertEquals(
      "org.example",
      ProfileItemIds.packageKey(context, "org.example", Process.myUserHandle()),
    )
    assertEquals(Process.myUserHandle(), ProfileItemIds.user(context, "org.example/chat/id@123"))
  }

  @Test
  fun workIdentityUsesStableSerialAndPreservesArbitraryShortcutId() {
    val work = UserHandle.getUserHandleForUid(1000000)
    every { users.getSerialNumberForUser(work) } returns 42L
    every { users.getUserForSerialNumber(42L) } returns work
    val id = ProfileItemIds.packageKey(context, "org.example", work) + "/chat/id@123"
    assertEquals("org.example@42/chat/id@123", id)
    assertEquals("org.example", ProfileItemIds.packageName(id))
    assertEquals(work, ProfileItemIds.user(context, id))
  }

  @Test
  fun deletedProfileNeverFallsBackToPersonalProfile() {
    every { users.getUserForSerialNumber(42L) } returns null
    assertNull(ProfileItemIds.user(context, "org.example@42/chat/id"))
    assertNull(ProfileItemIds.user(context, "org.example@invalid"))
  }
}
