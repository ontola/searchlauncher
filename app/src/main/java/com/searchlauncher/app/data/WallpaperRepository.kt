package com.searchlauncher.app.data

import android.content.Context
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
    loadWallpapers()
  }

  private fun loadWallpapers() {
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

  fun addWallpaper(uri: Uri): Boolean {
    return try {
      val contentResolver = context.contentResolver
      val inputStream = contentResolver.openInputStream(uri) ?: return false

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
      loadWallpapers()
      true
    } catch (e: Exception) {
      e.printStackTrace()
      false
    }
  }

  fun removeWallpaper(uri: Uri): Boolean {
    return try {
      val file = File(uri.path ?: return false)
      if (file.exists() && file.parentFile == wallpaperDir) {
        val deleted = file.delete()
        if (deleted) {
          loadWallpapers()
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
}
