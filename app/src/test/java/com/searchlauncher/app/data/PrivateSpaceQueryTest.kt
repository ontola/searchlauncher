package com.searchlauncher.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivateSpaceQueryTest {

  @Test
  fun showControl_hiddenWhenLockedOmitsTheRow() {
    assertFalse(
      PrivateSpaceQuery.showControl(available = true, unlocked = false, hideWhenLocked = true)
    )
    assertTrue(
      PrivateSpaceQuery.showControl(available = true, unlocked = true, hideWhenLocked = true)
    )
    assertTrue(
      PrivateSpaceQuery.showControl(available = true, unlocked = false, hideWhenLocked = false)
    )
    assertFalse(
      PrivateSpaceQuery.showControl(available = false, unlocked = false, hideWhenLocked = false)
    )
  }

  @Test
  fun showApps_onlyWhileUnlocked() {
    assertFalse(PrivateSpaceQuery.showApps(available = true, unlocked = false))
    assertTrue(PrivateSpaceQuery.showApps(available = true, unlocked = true))
    assertFalse(PrivateSpaceQuery.showApps(available = false, unlocked = true))
  }

  @Test
  fun appId_isProfileQualified() {
    assertEquals("com.whatsapp#private", PrivateSpaceQuery.appId("com.whatsapp"))
  }

  @Test
  fun includeControl_matchesPrivateAndLockWords() {
    assertTrue(PrivateSpaceQuery.includeControl("private"))
    assertTrue(PrivateSpaceQuery.includeControl("Private Space"))
    assertTrue(PrivateSpaceQuery.includeControl("unlock"))
    assertTrue(PrivateSpaceQuery.includeControl("lock"))
  }

  @Test
  fun includeControl_doesNotMatchUnrelatedQueries() {
    assertFalse(PrivateSpaceQuery.includeControl("whatsapp"))
    assertFalse(PrivateSpaceQuery.includeControl("settings"))
    assertFalse(PrivateSpaceQuery.includeControl(""))
  }

  @Test
  fun includeApp_usesTheAppLabel() {
    assertTrue(PrivateSpaceQuery.includeApp("photos", "Photos"))
    assertTrue(PrivateSpaceQuery.includeApp("what", "WhatsApp"))
    assertFalse(PrivateSpaceQuery.includeApp("photos", "WhatsApp"))
  }

  @Test
  fun snapshotHelpers_agreeWithFlags() {
    val locked = PrivateSpaceSnapshot(available = true, unlocked = false, hideWhenLocked = false)
    val hiddenLocked =
      PrivateSpaceSnapshot(available = true, unlocked = false, hideWhenLocked = true)
    val unlocked = PrivateSpaceSnapshot(available = true, unlocked = true, hideWhenLocked = true)

    assertTrue(PrivateSpaceQuery.showControl(locked))
    assertFalse(PrivateSpaceQuery.showApps(locked))
    assertFalse(PrivateSpaceQuery.showControl(hiddenLocked))
    assertFalse(PrivateSpaceQuery.showApps(hiddenLocked))
    assertTrue(PrivateSpaceQuery.showControl(unlocked))
    assertTrue(PrivateSpaceQuery.showApps(unlocked))
  }
}
