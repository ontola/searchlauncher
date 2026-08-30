package com.searchlauncher.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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

  private fun saveUriAsDisplayWallpaper(uri: Uri): Uri? {
    val bitmap = decodeSampledBitmap(uri, maxWallpaperDimension()) ?: return null
    return saveBitmapAsWallpaper(bitmap, recycleWhenDone = true)
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
    prefix: String = "wp",
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
      "Wallpaper saved to ${targetFile.absolutePath}, size: ${targetFile.length()} bytes",
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

      // Vibrant first: "dominant" on a photo is often a muddy grey, which makes auto-from-wallpaper
      // look like it is doing nothing. Vibrant/muted-vibrant tracks the colour people actually see.
      val swatch =
        palette.vibrantSwatch
          ?: palette.lightVibrantSwatch
          ?: palette.darkVibrantSwatch
          ?: palette.dominantSwatch
          ?: palette.mutedSwatch

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
