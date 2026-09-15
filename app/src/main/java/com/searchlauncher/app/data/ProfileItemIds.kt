package com.searchlauncher.app.data

import android.content.Context
import android.os.Process
import android.os.UserHandle
import android.os.UserManager

/** Keep legacy personal IDs; other profiles use stable serials, never reusable Android user IDs. */
object ProfileItemIds {
  fun packageKey(context: Context, packageName: String, user: UserHandle): String {
    if (user == Process.myUserHandle()) return packageName
    val serial = context.getSystemService(UserManager::class.java).getSerialNumberForUser(user)
    require(serial >= 0) { "Profile is no longer available" }
    return "$packageName@$serial"
  }

  fun packageName(id: String): String = id.substringBefore('/').substringBefore('@')

  fun hasProfile(id: String): Boolean = '@' in id.substringBefore('/')

  fun user(context: Context, id: String): UserHandle? {
    if (!hasProfile(id)) return Process.myUserHandle()
    val serial = id.substringBefore('/').substringAfter('@').toLongOrNull() ?: return null
    return context.getSystemService(UserManager::class.java).getUserForSerialNumber(serial)
  }
}
