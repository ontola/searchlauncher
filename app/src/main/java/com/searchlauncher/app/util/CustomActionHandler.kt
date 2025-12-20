package com.searchlauncher.app.util

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast

object CustomActionHandler {

  fun handleAction(context: Context, intent: Intent): Boolean {
    return when (intent.action) {
      "com.searchlauncher.action.TOGGLE_FLASHLIGHT" -> {
        toggleFlashlight(context)
        true
      }
      "com.searchlauncher.action.TOGGLE_ROTATION" -> {
        toggleRotation(context)
        true
      }
      "com.searchlauncher.action.RESTART" -> {
        restartLauncher(context)
        true
      }
      "com.searchlauncher.action.REBOOT" -> {
        rebootPhone(context)
        true
      }
      "com.searchlauncher.action.LOCK_SCREEN" -> {
        lockScreen(context)
        true
      }
      "com.searchlauncher.action.POWER_MENU" -> {
        openPowerMenu(context)
        true
      }
      "com.searchlauncher.action.SCREENSHOT" -> {
        takeScreenshot(context)
        true
      }
      "com.searchlauncher.action.TOGGLE_DARK_MODE" -> {
        toggleDarkMode(context)
        true
      }
      "com.searchlauncher.RESET_APP_DATA" -> {
        resetAppData(context)
        true
      }
      "com.searchlauncher.action.SETTINGS_CUSTOM_SHORTCUTS" -> {
        openInternalSetting(context, "custom_shortcuts")
        true
      }
      "com.searchlauncher.action.SETTINGS_SNIPPETS" -> {
        openInternalSetting(context, "snippets")
        true
      }
      "com.searchlauncher.action.SETTINGS_HISTORY" -> {
        openInternalSetting(context, "history")
        true
      }
      "com.searchlauncher.action.SETTINGS_WALLPAPER" -> {
        openInternalSetting(context, "wallpaper")
        true
      }
      "com.searchlauncher.action.ADD_WALLPAPER" -> {
        openInternalSetting(context, "add_wallpaper")
        true
      }
      "com.searchlauncher.action.REMOVE_CURRENT_WALLPAPER" -> {
        openInternalSetting(context, "remove_current_wallpaper")
        true
      }
      else -> false
    }
  }

  private fun toggleFlashlight(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
      try {
        val cameraId =
          cameraManager.cameraIdList.firstOrNull { id ->
            val characteristics = cameraManager.getCameraCharacteristics(id)
            characteristics.get(
              android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE
            ) == true
          }

        if (cameraId != null) {
          cameraManager.registerTorchCallback(
            object : CameraManager.TorchCallback() {
              override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                super.onTorchModeChanged(cameraId, enabled)
                cameraManager.unregisterTorchCallback(this)
                try {
                  cameraManager.setTorchMode(cameraId, !enabled)
                } catch (e: Exception) {
                  e.printStackTrace()
                }
              }
            },
            null,
          )
        } else {
          Toast.makeText(context, "Flashlight not supported", Toast.LENGTH_SHORT).show()
        }
      } catch (e: Exception) {
        e.printStackTrace()
        Toast.makeText(context, "Error accessing camera", Toast.LENGTH_SHORT).show()
      }
    }
  }

  private fun toggleRotation(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      if (Settings.System.canWrite(context)) {
        try {
          val resolver = context.contentResolver
          val currentRotation =
            Settings.System.getInt(resolver, Settings.System.ACCELEROMETER_ROTATION, 0)
          val newRotation = if (currentRotation == 1) 0 else 1
          Settings.System.putInt(resolver, Settings.System.ACCELEROMETER_ROTATION, newRotation)
          val status = if (newRotation == 1) "Unlocked" else "Locked"
          Toast.makeText(context, "Rotation $status", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
          e.printStackTrace()
        }
      } else {
        Toast.makeText(context, "Grant Write Settings permission", Toast.LENGTH_LONG).show()
        val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS)
        intent.data = Uri.parse("package:" + context.packageName)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
      }
    }
  }

  private fun restartLauncher(context: Context) {
    try {
      val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
      val componentName = intent?.component
      val mainIntent = Intent.makeRestartActivityTask(componentName)
      context.startActivity(mainIntent)
      Runtime.getRuntime().exit(0)
    } catch (e: Exception) {
      e.printStackTrace()
      Toast.makeText(context, "Cloud not restart: ${e.message}", Toast.LENGTH_SHORT).show()
    }
  }

  private fun rebootPhone(context: Context) {
    if (!com.searchlauncher.app.service.GestureAccessibilityService.showPowerDialog()) {
      requestAccessibilityService(context)
    } else {
      Toast.makeText(context, "Select 'Restart' in the Power Menu", Toast.LENGTH_LONG).show()
    }
  }

  private fun openPowerMenu(context: Context) {
    if (!com.searchlauncher.app.service.GestureAccessibilityService.showPowerDialog()) {
      requestAccessibilityService(context)
    }
  }

  private fun lockScreen(context: Context) {
    if (!com.searchlauncher.app.service.GestureAccessibilityService.lockScreen()) {
      if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
        Toast.makeText(context, "Lock Screen requires Android 9+", Toast.LENGTH_SHORT).show()
      } else {
        requestAccessibilityService(context)
      }
    }
  }

  private fun takeScreenshot(context: Context) {
    if (!com.searchlauncher.app.service.GestureAccessibilityService.takeScreenshot()) {
      if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
        Toast.makeText(context, "Screenshot requires Android 9+", Toast.LENGTH_SHORT).show()
      } else {
        requestAccessibilityService(context)
      }
    }
  }

  private fun requestAccessibilityService(context: Context) {
    Toast.makeText(
        context,
        "Enable 'SearchLauncher Gesture Service' in Accessibility settings",
        Toast.LENGTH_LONG,
      )
      .show()
    try {
      val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
      intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      context.startActivity(intent)
    } catch (e: Exception) {
      val intent = Intent(Settings.ACTION_SETTINGS)
      intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      context.startActivity(intent)
    }
  }

  private fun toggleDarkMode(context: Context) {
    try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        // Toggling night mode system-wide usually requires system permission
        // We can at least try to toggle the app's or launch the setting
        val intent = Intent(Settings.ACTION_DISPLAY_SETTINGS)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        Toast.makeText(context, "Toggle Dark Mode in Display Settings", Toast.LENGTH_SHORT).show()
      } else {
        val intent = Intent(Settings.ACTION_DISPLAY_SETTINGS)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
      }
    } catch (e: Exception) {
      e.printStackTrace()
    }
  }

  private fun resetAppData(context: Context) {
    try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
        val activityManager =
          context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        activityManager.clearApplicationUserData()
        // The app will be killed automatically after clearing data
      } else {
        Toast.makeText(context, "Requires Android 4.4+", Toast.LENGTH_SHORT).show()
      }
    } catch (e: Exception) {
      e.printStackTrace()
      Toast.makeText(context, "Error clearing app data: ${e.message}", Toast.LENGTH_LONG).show()
    }
  }

  private fun openInternalSetting(context: Context, setting: String) {
    try {
      val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
      intent?.putExtra("open_setting_page", setting)
      intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
      context.startActivity(intent)
    } catch (e: Exception) {
      e.printStackTrace()
    }
  }
}
