package com.searchlauncher.app.data

import android.annotation.SuppressLint
import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class WallpaperRepository(private val context: Context) {
  private val wallpaperDir = File(context.filesDir, "wallpapers").apply { if (!exists()) mkdirs() }

  private val _wallpapers = MutableStateFlow<List<Uri>>(emptyList())
  val wallpapers: StateFlow<List<Uri>> = _wallpapers.asStateFlow()

  init {
    reload()
  }

  fun reload() {
    val files = wallpaperDir.listFiles()?.filter { it.isFile && isImage(it) } ?: emptyList()
    _wallpapers.value = files.map { Uri.fromFile(it) }.sortedBy { it.path }
  }

  private fun isImage(file: File): Boolean {
    val name = file.name.lowercase()
    return name.endsWith(".jpg") ||
      name.endsWith(".jpeg") ||
      name.endsWith(".png") ||
      name.endsWith(".webp")
  }

  fun addWallpaper(uri: Uri): Uri? {
    return try {
      val contentResolver = context.contentResolver
      val inputStream = contentResolver.openInputStream(uri) ?: return null

      // Generate a unique filename
      val extension =
        when (contentResolver.getType(uri)) {
          "image/png" -> "png"
          "image/webp" -> "webp"
          else -> "jpg"
        }
      val filename = "wp_${System.currentTimeMillis()}.$extension"
      val targetFile = File(wallpaperDir, filename)

      inputStream.use { input ->
        FileOutputStream(targetFile).use { output -> input.copyTo(output) }
      }
      reload()
      Uri.fromFile(targetFile)
    } catch (e: Exception) {
      e.printStackTrace()
      null
    }
  }

  fun removeWallpaper(uri: Uri): Boolean {
    return try {
      val file = File(uri.path ?: return false)
      if (file.exists() && file.parentFile == wallpaperDir) {
        val deleted = file.delete()
        if (deleted) {
          reload()
        }
        deleted
      } else {
        false
      }
    } catch (e: Exception) {
      e.printStackTrace()
      false
    }
  }

  @SuppressLint("MissingPermission")
  fun addSystemWallpaper(): Uri? {
    return try {
      val wallpaperManager = WallpaperManager.getInstance(context)
      val drawable = wallpaperManager.drawable ?: return null
      val bitmap = (drawable as? BitmapDrawable)?.bitmap ?: return null

      val filename = "wp_system_${System.currentTimeMillis()}.jpg"
      val targetFile = File(wallpaperDir, filename)

      FileOutputStream(targetFile).use { output ->
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output)
      }
      reload()
      Uri.fromFile(targetFile)
    } catch (e: SecurityException) {
      android.util.Log.w("WallpaperRepository", "Permission denied for system wallpaper", e)
      null
    } catch (e: Exception) {
      null
    }
  }

  fun clearAll() {
    wallpaperDir.listFiles()?.forEach { it.delete() }
    _wallpapers.value = emptyList()
  }

  fun getWallpapersTotalSize(): Long {
    return wallpaperDir.listFiles()?.filter { it.isFile && isImage(it) }?.sumOf { it.length() }
      ?: 0L
  }
}
