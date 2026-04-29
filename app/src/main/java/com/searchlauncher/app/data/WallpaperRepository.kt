package com.searchlauncher.app.data

import android.annotation.SuppressLint
import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import java.io.File
import java.io.FileInputStream
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

  fun normalizeStoredWallpapers() {
    val files = wallpaperDir.listFiles()?.filter { it.isFile && isImage(it) } ?: emptyList()
    val maxDimension = maxWallpaperDimension()
    var changed = false

    files.forEach { file ->
      try {
        if (isWithinBounds(file, maxDimension)) return@forEach

        val bitmap = decodeSampledBitmap(Uri.fromFile(file), maxDimension) ?: return@forEach
        val tmpFile = File(wallpaperDir, "${file.name}.tmp")
        try {
          FileOutputStream(tmpFile).use { output ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 88, output)
          }
          if (tmpFile.length() > 0) {
            if (file.delete() && tmpFile.renameTo(file)) {
              changed = true
            }
          }
        } finally {
          if (!bitmap.isRecycled) bitmap.recycle()
          if (tmpFile.exists()) tmpFile.delete()
        }
      } catch (e: Exception) {
        android.util.Log.w("WallpaperRepository", "Failed to normalize ${file.name}", e)
      }
    }

    if (changed) reload()
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
      saveUriAsDisplayWallpaper(uri)
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
            android.util.Log.d("WallpaperRepository", "Got wallpaper file (FLAG_SYSTEM)")
            pfd.use {
              return savePfdAsDisplayWallpaper(it.fileDescriptor)
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
            android.util.Log.d("WallpaperRepository", "Got wallpaper file (FLAG_LOCK)")
            pfd.use {
              return savePfdAsDisplayWallpaper(it.fileDescriptor)
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

      saveBitmapAsWallpaper(bitmap, recycleWhenDone = drawable !is BitmapDrawable)
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

      saveBitmapAsWallpaper(bitmap, recycleWhenDone = drawable !is BitmapDrawable)
    } catch (e: Exception) {
      android.util.Log.e("WallpaperRepository", "Built-in wallpaper fallback also failed", e)
      null
    }
  }

  private fun saveUriAsDisplayWallpaper(uri: Uri): Uri? {
    val bitmap = decodeSampledBitmap(uri, maxWallpaperDimension()) ?: return null
    return saveBitmapAsWallpaper(bitmap, recycleWhenDone = true, prefix = "wp")
  }

  private fun savePfdAsDisplayWallpaper(fileDescriptor: java.io.FileDescriptor): Uri? {
    val tmpFile = File(wallpaperDir, "wp_import_${System.currentTimeMillis()}.tmp")
    return try {
      FileInputStream(fileDescriptor).use { input ->
        FileOutputStream(tmpFile).use { output -> input.copyTo(output) }
      }
      val bitmap =
        decodeSampledBitmap(Uri.fromFile(tmpFile), maxWallpaperDimension()) ?: return null
      saveBitmapAsWallpaper(bitmap, recycleWhenDone = true)
    } finally {
      tmpFile.delete()
    }
  }

  private fun maxWallpaperDimension(): Int {
    val metrics = context.resources.displayMetrics
    return (maxOf(metrics.widthPixels, metrics.heightPixels) * 1.15f).toInt().coerceIn(1600, 3200)
  }

  private fun isWithinBounds(file: File, maxDimension: Int): Boolean {
    val options =
      BitmapFactory.Options().apply {
        inJustDecodeBounds = true
        BitmapFactory.decodeFile(file.absolutePath, this)
      }
    if (options.outWidth <= 0 || options.outHeight <= 0) return true
    return maxOf(options.outWidth, options.outHeight) <= maxDimension
  }

  private fun decodeSampledBitmap(uri: Uri, maxDimension: Int): Bitmap? {
    val bounds =
      BitmapFactory.Options().apply {
        inJustDecodeBounds = true
        context.contentResolver.openInputStream(uri)?.use {
          BitmapFactory.decodeStream(it, null, this)
        }
      }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    val options =
      BitmapFactory.Options().apply {
        inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxDimension)
        inPreferredConfig = Bitmap.Config.ARGB_8888
      }
    val decoded =
      context.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, options)
      } ?: return null

    val largestSide = maxOf(decoded.width, decoded.height)
    if (largestSide <= maxDimension) return decoded

    val scale = maxDimension.toFloat() / largestSide
    val scaled =
      Bitmap.createScaledBitmap(
        decoded,
        (decoded.width * scale).toInt().coerceAtLeast(1),
        (decoded.height * scale).toInt().coerceAtLeast(1),
        true,
      )
    if (scaled != decoded) decoded.recycle()
    return scaled
  }

  private fun calculateInSampleSize(width: Int, height: Int, maxDimension: Int): Int {
    var inSampleSize = 1
    var halfWidth = width / 2
    var halfHeight = height / 2
    while (maxOf(halfWidth, halfHeight) / inSampleSize >= maxDimension) {
      inSampleSize *= 2
    }
    return inSampleSize
  }

  private fun saveBitmapAsWallpaper(
    bitmap: Bitmap,
    recycleWhenDone: Boolean,
    prefix: String = "wp_system",
  ): Uri? {
    android.util.Log.d("WallpaperRepository", "Bitmap obtained: ${bitmap.width}x${bitmap.height}")
    val filename = "${prefix}_${System.currentTimeMillis()}.jpg"
    val targetFile = File(wallpaperDir, filename)

    try {
      FileOutputStream(targetFile).use { output ->
        bitmap.compress(Bitmap.CompressFormat.JPEG, 88, output)
      }
    } finally {
      if (recycleWhenDone && !bitmap.isRecycled) {
        bitmap.recycle()
      }
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
      val bitmap = decodeSampledBitmap(uri, 128)

      if (bitmap == null) {
        android.util.Log.w("WallpaperRepository", "Failed to decode bitmap from $uri")
        return null
      }

      val palette =
        try {
          androidx.palette.graphics.Palette.from(bitmap).generate()
        } finally {
          if (!bitmap.isRecycled) bitmap.recycle()
        }

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
