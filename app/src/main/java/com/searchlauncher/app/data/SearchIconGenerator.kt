package com.searchlauncher.app.data

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable

class SearchIconGenerator(private val context: Context) {

  /**
   * The icon a search shortcut shows in the results list: the installed app it searches with the
   * shortcut's letter tile as a small badge in the corner, or just the letter tile when none of its
   * [SearchShortcut.iconPackages] is installed.
   */
  fun getShortcutIcon(shortcut: SearchShortcut): Drawable? {
    val letterTile = getColoredSearchIcon(shortcut.color ?: 0xFF808080, shortcut.alias)
    val appIcon = installedAppIcon(shortcut.iconPackages) ?: return letterTile
    if (letterTile == null) return appIcon

    val density = context.resources.displayMetrics.density
    val size = (40 * density).toInt()
    val bitmap =
      android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)

    // Leave room at the bottom right so the badge overlaps the app icon rather than covering it.
    val appSize = (size * 0.86f).toInt()
    appIcon.mutate().setBounds(0, 0, appSize, appSize)
    appIcon.draw(canvas)

    // The results list clips shortcut icons to 8dp corners; keep the badge clear of that curve.
    val badgeSize = (size * 0.48f).toInt()
    val badgeEnd = size - (1.5f * density).toInt()
    letterTile.setBounds(badgeEnd - badgeSize, badgeEnd - badgeSize, badgeEnd, badgeEnd)
    letterTile.draw(canvas)

    return android.graphics.drawable.BitmapDrawable(context.resources, bitmap)
  }

  private fun installedAppIcon(packages: List<String>): Drawable? =
    packages.firstNotNullOfOrNull { pkg ->
      try {
        context.packageManager.getApplicationIcon(pkg)
      } catch (e: android.content.pm.PackageManager.NameNotFoundException) {
        null
      }
    }

  fun getColoredSearchIcon(color: Long?, text: String? = null): Drawable? {
    if (color == null) return null

    if (text != null) {
      val density = context.resources.displayMetrics.density
      // 40dp to pixels - matching the icon size in the UI
      val size = (40 * density).toInt()

      val bitmap =
        android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
      val canvas = android.graphics.Canvas(bitmap)

      // Draw rounded background
      val paint =
        android.graphics.Paint().apply {
          this.color = color.toInt()
          this.isAntiAlias = true
          this.style = android.graphics.Paint.Style.FILL
        }

      // Draw rounded rect for background
      val rect = android.graphics.RectF(0f, 0f, size.toFloat(), size.toFloat())
      val cornerRadius = 8 * density
      canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint)

      // Draw text
      val textPaint =
        android.graphics.Paint().apply {
          this.color = Color.WHITE
          // Adjust text size to be roughly 50% of the box height
          this.textSize = size * 0.5f
          this.isAntiAlias = true
          this.textAlign = android.graphics.Paint.Align.CENTER
          this.typeface =
            android.graphics.Typeface.create(
              android.graphics.Typeface.DEFAULT,
              android.graphics.Typeface.BOLD,
            )
        }

      val displayText = text.uppercase()
      val maxTextWidth = size * 0.85f
      var currentTextSize = size * 0.5f
      textPaint.textSize = currentTextSize

      // Dynamic text scaling
      while (textPaint.measureText(displayText) > maxTextWidth && currentTextSize > 10f) {
        currentTextSize -= 2f
        textPaint.textSize = currentTextSize
      }

      // Center text both horizontally and vertically
      val xPos = size / 2f
      val yPos = (size / 2f) - ((textPaint.descent() + textPaint.ascent()) / 2f)

      canvas.drawText(displayText, xPos, yPos, textPaint)

      return android.graphics.drawable.BitmapDrawable(context.resources, bitmap)
    }

    val background = GradientDrawable()
    background.shape = GradientDrawable.RECTANGLE
    // Match the 8.dp corner radius used for Contact icons
    val cornerRadius = 8 * context.resources.displayMetrics.density
    background.cornerRadius = cornerRadius
    background.setColor(color.toInt())

    val icon = context.getDrawable(com.searchlauncher.app.R.drawable.ic_search)?.mutate()
    icon?.setTint(Color.WHITE)

    if (icon == null) return background

    val layers = arrayOf(background, icon)
    val layerDrawable = LayerDrawable(layers)

    val inset = (6 * context.resources.displayMetrics.density).toInt()
    layerDrawable.setLayerInset(1, inset, inset, inset, inset)

    return layerDrawable
  }
}
