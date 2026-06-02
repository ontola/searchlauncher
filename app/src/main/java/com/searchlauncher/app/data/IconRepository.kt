package com.searchlauncher.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.LruCache
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class IconRepository(private val context: Context) {
  private val memoryCache =
    object : LruCache<String, Drawable>(ICON_CACHE_MAX_KB) {
      override fun sizeOf(key: String, value: Drawable): Int {
        val bitmap = (value as? BitmapDrawable)?.bitmap
        if (bitmap != null && !bitmap.isRecycled) {
          return (bitmap.allocationByteCount / 1024).coerceAtLeast(1)
        }

        val width = value.intrinsicWidth.takeIf { it > 0 } ?: ICON_SIZE_PX
        val height = value.intrinsicHeight.takeIf { it > 0 } ?: ICON_SIZE_PX
        return (width * height * 4 / 1024).coerceAtLeast(1)
      }
    }

  fun getMemory(key: String): Drawable? = memoryCache.get(key)

  fun putMemory(key: String, icon: Drawable) {
    memoryCache.put(key, icon)
  }

  fun clearMemory() {
    memoryCache.evictAll()
  }

  fun hasOnDisk(id: String): Boolean = File(getIconDir(), "${sanitizeId(id)}.png").exists()

  fun saveToDisk(id: String, drawable: Drawable?, force: Boolean = true) {
    if (drawable == null || !force) return
    val targetFile = File(getIconDir(), "${sanitizeId(id)}.png")
    val tmpFile = File(getIconDir(), "${sanitizeId(id)}.tmp")

    try {
      val bitmap = Bitmap.createBitmap(ICON_SIZE_PX, ICON_SIZE_PX, Bitmap.Config.ARGB_8888)
      val canvas = Canvas(bitmap)
      drawable.setBounds(0, 0, canvas.width, canvas.height)
      drawable.draw(canvas)

      FileOutputStream(tmpFile).use { out ->
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        out.flush()
      }
      bitmap.recycle()

      if (tmpFile.exists() && tmpFile.length() > 0) {
        if (targetFile.exists()) {
          targetFile.delete()
        }
        tmpFile.renameTo(targetFile)
      }
    } catch (e: Exception) {
      e.printStackTrace()
      tmpFile.delete()
    }
  }

  fun loadFromDisk(id: String): Drawable? {
    val file = File(getIconDir(), "${sanitizeId(id)}.png")
    if (!file.exists()) return null
    return try {
      val bitmap = BitmapFactory.decodeFile(file.absolutePath)
      BitmapDrawable(context.resources, bitmap)
    } catch (e: Exception) {
      null
    }
  }

  suspend fun cacheAppIcon(packageName: String, customKey: String? = null) =
    withContext(Dispatchers.IO) {
      try {
        val key = customKey ?: "appicon_$packageName"
        val icon = context.packageManager.getApplicationIcon(packageName)
        putMemory(key, icon)
        saveToDisk(key, icon, force = true)
        android.util.Log.d("IconRepository", "Cached icon for $packageName as $key")
      } catch (e: Exception) {
        android.util.Log.e("IconRepository", "Failed to cache icon for $packageName", e)
      }
    }

  fun clearDisk() {
    getIconDir().deleteRecursively()
    getIconDir().mkdirs()
  }

  private fun getIconDir() = File(context.filesDir, "favorite_icons").apply { mkdirs() }

  private fun sanitizeId(id: String) = id.replace("/", "_").replace(":", "_")

  companion object {
    private const val ICON_SIZE_PX = 192
    private const val ICON_CACHE_MAX_KB = 24 * 1024
  }
}
