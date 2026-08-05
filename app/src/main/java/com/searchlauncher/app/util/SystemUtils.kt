package com.searchlauncher.app.util

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import java.lang.reflect.Method

object SystemUtils {

  fun copyUrlToClipboard(context: Context, url: String, label: String = "URL") {
    if (url.isBlank()) return
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, url))
    Toast.makeText(context, "URL copied", Toast.LENGTH_SHORT).show()
  }

  @SuppressLint("WrongConstant")
  fun expandNotifications(context: Context) {
    try {
      val statusBarService = context.getSystemService("statusbar")
      val statusBarManager = Class.forName("android.app.StatusBarManager")
      val method: Method = statusBarManager.getMethod("expandNotificationsPanel")
      method.invoke(statusBarService)
    } catch (e: Exception) {
      e.printStackTrace()
    }
  }

  @SuppressLint("WrongConstant")
  fun expandQuickSettings(context: Context) {
    try {
      val statusBarService = context.getSystemService("statusbar")
      val statusBarManager = Class.forName("android.app.StatusBarManager")
      val method: Method = statusBarManager.getMethod("expandSettingsPanel")
      method.invoke(statusBarService)
    } catch (e: Exception) {
      e.printStackTrace()
    }
  }

  fun logError(tag: String, message: String, e: Throwable) {
    if (e is kotlinx.coroutines.CancellationException) throw e
    android.util.Log.e(tag, message, e)
    io.sentry.Sentry.captureException(e)
  }
}
