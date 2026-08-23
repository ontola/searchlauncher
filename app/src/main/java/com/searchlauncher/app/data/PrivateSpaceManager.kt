package com.searchlauncher.app.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.LauncherApps
import android.content.pm.LauncherUserInfo
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.UserHandle
import android.os.UserManager
import android.util.Log
import androidx.annotation.RequiresApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Tracks Android 15 Private Space for the default home app: lock state, the unlock control, and the
 * private-profile app list. Nothing here is written to AppSearch.
 */
class PrivateSpaceManager(private val context: Context) {
  private val _snapshot = MutableStateFlow(PrivateSpaceSnapshot())
  val snapshot: StateFlow<PrivateSpaceSnapshot> = _snapshot.asStateFlow()

  private val _apps = MutableStateFlow<List<PrivateAppInfo>>(emptyList())
  val apps: StateFlow<List<PrivateAppInfo>> = _apps.asStateFlow()

  @Volatile private var started = false

  private val profileReceiver =
    object : BroadcastReceiver() {
      override fun onReceive(context: Context?, intent: Intent?) {
        refresh()
      }
    }

  fun start() {
    if (started) return
    started = true
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      val filter =
        IntentFilter().apply {
          addAction(Intent.ACTION_PROFILE_AVAILABLE)
          addAction(Intent.ACTION_PROFILE_UNAVAILABLE)
        }
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        context.registerReceiver(profileReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
      } else {
        @Suppress("UnspecifiedRegisterReceiverFlag")
        context.registerReceiver(profileReceiver, filter)
      }
    }
    refresh()
  }

  fun refresh() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
      _snapshot.value = PrivateSpaceSnapshot()
      _apps.value = emptyList()
      return
    }
    try {
      refreshInternal()
    } catch (e: Exception) {
      Log.w(TAG, "Failed to refresh Private Space", e)
    }
  }

  @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
  private fun refreshInternal() {
    val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
    val userManager = context.getSystemService(UserManager::class.java)
    val privateUser = findPrivateUser(launcherApps)
    if (privateUser == null || userManager == null) {
      _snapshot.value = PrivateSpaceSnapshot()
      _apps.value = emptyList()
      return
    }

    val userInfo = launcherApps.getLauncherUserInfo(privateUser)
    val hideWhenLocked =
      userInfo?.userConfig?.getBoolean(LauncherUserInfo.PRIVATE_SPACE_ENTRYPOINT_HIDDEN, false)
        ?: false
    val unlocked = !userManager.isQuietModeEnabled(privateUser)
    _snapshot.value =
      PrivateSpaceSnapshot(
        available = true,
        unlocked = unlocked,
        hideWhenLocked = hideWhenLocked,
        userHandle = privateUser,
      )
    _apps.value = if (unlocked) readPrivateApps(launcherApps, privateUser) else emptyList()
  }

  @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
  private fun findPrivateUser(launcherApps: LauncherApps): UserHandle? {
    for (user in launcherApps.profiles) {
      if (PrivateSpaceProfiles.isPrivate(launcherApps, user)) return user
    }
    return null
  }

  private fun readPrivateApps(launcherApps: LauncherApps, user: UserHandle): List<PrivateAppInfo> {
    return try {
      launcherApps.getActivityList(null, user).map { info ->
        PrivateAppInfo(
          packageName = info.componentName.packageName,
          label = info.label.toString(),
          componentName = info.componentName,
          userHandle = info.user,
        )
      }
    } catch (e: Exception) {
      Log.w(TAG, "Failed to list Private Space apps", e)
      emptyList()
    }
  }

  /** [uiContext] should be an Activity so the system credential sheet can attach when unlocking. */
  fun requestLocked(locked: Boolean, uiContext: Context) {
    val user = _snapshot.value.userHandle ?: return
    val userManager = uiContext.getSystemService(UserManager::class.java) ?: return
    try {
      userManager.requestQuietModeEnabled(locked, user)
    } catch (e: Exception) {
      Log.w(TAG, "requestQuietModeEnabled($locked) failed", e)
    }
  }

  fun toggleLock(uiContext: Context) {
    val snap = _snapshot.value
    if (!snap.available || snap.userHandle == null) return
    requestLocked(snap.unlocked, uiContext)
  }

  fun searchHits(query: String): List<SearchResult> {
    val snap = _snapshot.value
    if (!PrivateSpaceQuery.showControl(snap)) return emptyList()

    val results = mutableListOf<SearchResult>()
    if (PrivateSpaceQuery.includeControl(query)) {
      controlResult(snap, PrivateSpaceQuery.controlScore(query))?.let { results.add(it) }
    }
    if (PrivateSpaceQuery.showApps(snap)) {
      for (app in _apps.value) {
        if (PrivateSpaceQuery.includeApp(query, app.label)) {
          results.add(appResult(app, PrivateSpaceQuery.appScore(query, app.label)))
        }
      }
    }
    return results
  }

  fun controlResult(
    snap: PrivateSpaceSnapshot = _snapshot.value,
    matchScore: Int = RankingScores.NAMESPACE_BOOST_APPS,
  ): SearchResult.PrivateSpace? {
    if (!PrivateSpaceQuery.showControl(snap)) return null
    return SearchResult.PrivateSpace(
      id = PrivateSpaceQuery.CONTROL_ID,
      namespace = PrivateSpaceQuery.NAMESPACE,
      title = "Private Space",
      subtitle = if (snap.unlocked) "Unlocked · tap to lock" else "Locked · tap to unlock",
      icon = controlIcon(),
      rankingScore = RankingScores.NAMESPACE_BOOST_APPS + matchScore,
      unlocked = snap.unlocked,
    )
  }

  fun appResults(): List<SearchResult.App> {
    if (!PrivateSpaceQuery.showApps(_snapshot.value)) return emptyList()
    return _apps.value.map { appResult(it, RankingScores.NAMESPACE_BOOST_APPS) }
  }

  private fun appResult(app: PrivateAppInfo, matchScore: Int): SearchResult.App {
    return SearchResult.App(
      id = PrivateSpaceQuery.appId(app.packageName),
      namespace = "apps",
      title = app.label,
      subtitle = "Private",
      icon = loadPrivateAppIcon(app),
      rankingScore = RankingScores.NAMESPACE_BOOST_APPS + matchScore,
      packageName = app.packageName,
      isPrivate = true,
      userHandle = app.userHandle,
      componentName = app.componentName,
    )
  }

  private fun loadPrivateAppIcon(app: PrivateAppInfo): Drawable? {
    return try {
      val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
      val info =
        launcherApps.getActivityList(app.packageName, app.userHandle).firstOrNull {
          it.componentName == app.componentName
        } ?: launcherApps.getActivityList(app.packageName, app.userHandle).firstOrNull()
      info?.getIcon(context.resources.displayMetrics.densityDpi)
    } catch (_: Exception) {
      null
    }
  }

  private fun controlIcon(): Drawable? =
    context.getDrawable(com.searchlauncher.app.R.drawable.ic_private_space)

  companion object {
    private const val TAG = "PrivateSpace"
  }
}
