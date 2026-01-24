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
    android.util.Log.d("WallpaperRepository", "addSystemWallpaper() called")
    return try {
      val wallpaperManager = WallpaperManager.getInstance(context)
      android.util.Log.d("WallpaperRepository", "WallpaperManager instance obtained")

      // First, try getWallpaperFile() API (API 24+) which uses a different permission path
      if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
        try {
          // Try system wallpaper
          var pfd = wallpaperManager.getWallpaperFile(WallpaperManager.FLAG_SYSTEM)
          if (pfd != null) {
            android.util.Log.d(
              "WallpaperRepository",
              "Got ParcelFileDescriptor from getWallpaperFile (FLAG_SYSTEM)",
            )
            val bitmap = android.graphics.BitmapFactory.decodeFileDescriptor(pfd.fileDescriptor)
            pfd.close()
            if (bitmap != null) {
              android.util.Log.d(
                "WallpaperRepository",
                "Decoded bitmap from PFD (FLAG_SYSTEM): ${bitmap.width}x${bitmap.height}",
              )
              return saveBitmapAsWallpaper(bitmap)
            }
          } else {
            android.util.Log.w(
              "WallpaperRepository",
              "getWallpaperFile (FLAG_SYSTEM) returned null, trying FLAG_LOCK",
            )
          }

          // Fallback to lock screen wallpaper
          pfd = wallpaperManager.getWallpaperFile(WallpaperManager.FLAG_LOCK)
          if (pfd != null) {
            android.util.Log.d(
              "WallpaperRepository",
              "Got ParcelFileDescriptor from getWallpaperFile (FLAG_LOCK)",
            )
            val bitmap = android.graphics.BitmapFactory.decodeFileDescriptor(pfd.fileDescriptor)
            pfd.close()
            if (bitmap != null) {
              android.util.Log.d(
                "WallpaperRepository",
                "Decoded bitmap from PFD (FLAG_LOCK): ${bitmap.width}x${bitmap.height}",
              )
              return saveBitmapAsWallpaper(bitmap)
            }
          } else {
            android.util.Log.w(
              "WallpaperRepository",
              "getWallpaperFile (FLAG_LOCK) returned null, trying drawable fallback",
            )
          }
        } catch (e: Exception) {
          android.util.Log.w(
            "WallpaperRepository",
            "getWallpaperFile failed: ${e.message}, trying drawable fallback",
          )
        }
      }

      // Fallback: Try drawable first, then peekDrawable
      var drawable = wallpaperManager.drawable
      if (drawable == null) {
        android.util.Log.w(
          "WallpaperRepository",
          "wallpaperManager.drawable is null, trying peekDrawable",
        )
        drawable = wallpaperManager.peekDrawable()
      }

      if (drawable == null) {
        android.util.Log.e("WallpaperRepository", "Both drawable and peekDrawable returned null")
        return null
      }

      android.util.Log.d(
        "WallpaperRepository",
        "Drawable obtained, class: ${drawable.javaClass.name}",
      )

      // Try to get bitmap - handle different drawable types
      val bitmap: Bitmap? =
        when (drawable) {
          is BitmapDrawable -> {
            android.util.Log.d("WallpaperRepository", "Drawable is BitmapDrawable")
            drawable.bitmap
          }
          else -> {
            android.util.Log.d(
              "WallpaperRepository",
              "Drawable is ${drawable.javaClass.name}, converting to Bitmap",
            )
            // Convert any drawable to bitmap
            val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: 1080
            val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: 1920
            android.util.Log.d(
              "WallpaperRepository",
              "Creating bitmap with size ${width}x${height}",
            )
            val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bmp)
            drawable.setBounds(0, 0, width, height)
            drawable.draw(canvas)
            bmp
          }
        }

      if (bitmap == null) {
        android.util.Log.e("WallpaperRepository", "Failed to get/create bitmap from drawable")
        return null
      }

      saveBitmapAsWallpaper(bitmap)
    } catch (e: SecurityException) {
      android.util.Log.e(
        "WallpaperRepository",
        "Permission denied for system wallpaper, trying built-in fallback",
        e,
      )
      // Xiaomi MIUI blocks WallpaperManager APIs with READ_EXTERNAL_STORAGE check
      // Try getBuiltInDrawable() which doesn't require permissions
      tryGetBuiltInWallpaper()
    } catch (e: Exception) {
      android.util.Log.e("WallpaperRepository", "Failed to add system wallpaper", e)
      null
    }
  }

  /**
   * Try to get the built-in device wallpaper, which doesn't require storage permissions. This is a
   * fallback for Xiaomi/MIUI devices that block WallpaperManager.getDrawable().
   */
  private fun tryGetBuiltInWallpaper(): Uri? {
    return try {
      val wallpaperManager = WallpaperManager.getInstance(context)
      android.util.Log.d("WallpaperRepository", "Trying getBuiltInDrawable() fallback")

      // Get the built-in (factory default) wallpaper - doesn't require permissions
      val drawable = wallpaperManager.builtInDrawable
      if (drawable == null) {
        android.util.Log.w("WallpaperRepository", "builtInDrawable returned null")
        return null
      }

      android.util.Log.d("WallpaperRepository", "Got builtInDrawable: ${drawable.javaClass.name}")

      val bitmap: Bitmap =
        when (drawable) {
          is BitmapDrawable -> drawable.bitmap
          else -> {
            val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: 1080
            val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: 1920
            val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bmp)
            drawable.setBounds(0, 0, width, height)
            drawable.draw(canvas)
            bmp
          }
        }

      saveBitmapAsWallpaper(bitmap)
    } catch (e: Exception) {
      android.util.Log.e("WallpaperRepository", "Built-in wallpaper fallback also failed", e)
      null
    }
  }

  private fun saveBitmapAsWallpaper(bitmap: Bitmap): Uri? {
    android.util.Log.d("WallpaperRepository", "Bitmap obtained: ${bitmap.width}x${bitmap.height}")
    val filename = "wp_system_${System.currentTimeMillis()}.jpg"
    val targetFile = File(wallpaperDir, filename)

    FileOutputStream(targetFile).use { output ->
      bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output)
    }
    android.util.Log.d(
      "WallpaperRepository",
      "System wallpaper saved to ${targetFile.absolutePath}, size: ${targetFile.length()} bytes",
    )
    reload()
    return Uri.fromFile(targetFile)
  }

  fun clearAll() {
    wallpaperDir.listFiles()?.forEach { it.delete() }
    _wallpapers.value = emptyList()
  }

  fun getWallpapersTotalSize(): Long {
    return wallpaperDir.listFiles()?.filter { it.isFile && isImage(it) }?.sumOf { it.length() }
      ?: 0L
  }

  /**
   * Extract dominant color from a wallpaper URI using Android Palette. Returns the dominant color
   * as an ARGB Int, or null if extraction fails.
   */
  fun extractDominantColor(uri: Uri): Int? {
    return try {
      android.util.Log.d("WallpaperRepository", "Extracting color from: $uri")
      val inputStream = context.contentResolver.openInputStream(uri) ?: return null
      val bitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
      inputStream.close()

      if (bitmap == null) {
        android.util.Log.w("WallpaperRepository", "Failed to decode bitmap from $uri")
        return null
      }

      // Use Palette to extract dominant color
      val palette = androidx.palette.graphics.Palette.from(bitmap).generate()

      // Try dominant swatch first, then vibrant, then muted
      val swatch = palette.dominantSwatch ?: palette.vibrantSwatch ?: palette.mutedSwatch

      if (swatch != null) {
        android.util.Log.d(
          "WallpaperRepository",
          "Extracted color: ${Integer.toHexString(swatch.rgb)}",
        )
        swatch.rgb
      } else {
        android.util.Log.w("WallpaperRepository", "No color swatch found")
        null
      }
    } catch (e: Exception) {
      android.util.Log.e("WallpaperRepository", "Error extracting color from $uri", e)
      null
    }
  }
}
