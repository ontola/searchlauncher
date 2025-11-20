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

            "com.searchlauncher.RESET_APP_DATA" -> {
                resetAppData(context)
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
                            android.hardware.camera2.CameraCharacteristics
                                .FLASH_INFO_AVAILABLE
                        ) == true
                    }

                if (cameraId != null) {
                    cameraManager.registerTorchCallback(
                        object : CameraManager.TorchCallback() {
                            override fun onTorchModeChanged(
                                cameraId: String,
                                enabled: Boolean
                            ) {
                                super.onTorchModeChanged(cameraId, enabled)
                                cameraManager.unregisterTorchCallback(this)
                                try {
                                    cameraManager.setTorchMode(cameraId, !enabled)
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                        },
                        null
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
                        Settings.System.getInt(
                            resolver,
                            Settings.System.ACCELEROMETER_ROTATION,
                            0
                        )
                    val newRotation = if (currentRotation == 1) 0 else 1
                    Settings.System.putInt(
                        resolver,
                        Settings.System.ACCELEROMETER_ROTATION,
                        newRotation
                    )
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

    private fun resetAppData(context: Context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                val activityManager =
                    context.getSystemService(Context.ACTIVITY_SERVICE) as
                            android.app.ActivityManager
                activityManager.clearApplicationUserData()
                // The app will be killed automatically after clearing data
            } else {
                Toast.makeText(context, "Requires Android 4.4+", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Error clearing app data: ${e.message}", Toast.LENGTH_LONG)
                .show()
        }
    }
}
