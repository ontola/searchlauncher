package com.searchlauncher.app.data

import android.content.ComponentName
import android.content.pm.LauncherApps
import android.os.Build
import android.os.UserHandle
import android.os.UserManager
import com.searchlauncher.app.util.FuzzyMatch

/**
 * Live Private Space state. The durable AppSearch index never stores these apps; lock must be able
 * to make them undiscoverable without waiting on a purge.
 */
data class PrivateSpaceSnapshot(
  val available: Boolean = false,
  val unlocked: Boolean = false,
  val hideWhenLocked: Boolean = false,
  val userHandle: UserHandle? = null,
)

data class PrivateAppInfo(
  val packageName: String,
  val label: String,
  val componentName: ComponentName,
  val userHandle: UserHandle,
)

object PrivateSpaceQuery {
  const val CONTROL_ID = "private_space"
  const val NAMESPACE = "private_space"
  const val APP_ID_SUFFIX = "#private"

  private val CONTROL_TARGETS =
    listOf("private space", "unlock private space", "lock private space", "private")

  fun showControl(available: Boolean, unlocked: Boolean, hideWhenLocked: Boolean): Boolean =
    available && (unlocked || !hideWhenLocked)

  fun showControl(snapshot: PrivateSpaceSnapshot): Boolean =
    showControl(snapshot.available, snapshot.unlocked, snapshot.hideWhenLocked)

  fun showApps(available: Boolean, unlocked: Boolean): Boolean = available && unlocked

  fun showApps(snapshot: PrivateSpaceSnapshot): Boolean =
    showApps(snapshot.available, snapshot.unlocked)

  fun appId(packageName: String): String = "$packageName$APP_ID_SUFFIX"

  fun controlScore(query: String): Int {
    if (query.isBlank()) return 0
    return CONTROL_TARGETS.maxOf { FuzzyMatch.calculateScore(query, it) }
  }

  fun includeControl(query: String): Boolean =
    controlScore(query) > RankingScores.MIN_CANDIDATE_SCORE

  fun appScore(query: String, label: String): Int = FuzzyMatch.calculateScore(query, label)

  fun includeApp(query: String, label: String): Boolean =
    appScore(query, label) > RankingScores.MIN_CANDIDATE_SCORE
}

object PrivateSpaceProfiles {
  fun isPrivate(launcherApps: LauncherApps, user: UserHandle): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return false
    return try {
      launcherApps.getLauncherUserInfo(user)?.userType == UserManager.USER_TYPE_PROFILE_PRIVATE
    } catch (_: Exception) {
      false
    }
  }
}
