package com.searchlauncher.app.ui

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout

object WidgetHostViewFactory {
  /**
   * Whether [appWidgetId] still names a widget this host can draw.
   *
   * An id restored from a backup is the common `false`: ids are handed out by the host, so one from
   * a previous install belongs to nothing here and [AppWidgetManager.getAppWidgetInfo] comes back
   * null. Callers show an error in the widget's place rather than an empty view, which would take
   * up its space while looking like nothing is there.
   */
  fun canRender(appWidgetManager: AppWidgetManager, appWidgetId: Int): Boolean =
    try {
      appWidgetManager.getAppWidgetInfo(appWidgetId) != null
    } catch (e: Exception) {
      android.util.Log.w("WidgetHostViewFactory", "Cannot read widget $appWidgetId", e)
      false
    }

  fun createWidgetView(
    context: Context,
    appWidgetId: Int,
    appWidgetHost: AppWidgetHost,
    appWidgetManager: AppWidgetManager,
  ): View {
    return try {
      val appWidgetInfo = appWidgetManager.getAppWidgetInfo(appWidgetId) ?: return View(context)
      val hostView = appWidgetHost.createView(context, appWidgetId, appWidgetInfo)
      hostView.setAppWidget(appWidgetId, appWidgetInfo)

      // Enforce minimum height (often crucial for list widgets like Calendar).
      //
      // [AppWidgetProviderInfo.minHeight] is already in pixels — the framework resolves the
      // provider's `android:minHeight` dimension against the display when it parses the manifest.
      // Scaling it by the density again made every widget as many times too tall as the screen is
      // dense: Chrome's Dino asks for 110dp, arrives as 358px, and was being given 1163px.
      hostView.minimumHeight = appWidgetInfo.minHeight

      // Wrap in a FrameLayout for layout params or padding if needed
      val frameLayout = FrameLayout(context)
      frameLayout.addView(
        hostView,
        FrameLayout.LayoutParams(
          ViewGroup.LayoutParams.MATCH_PARENT,
          ViewGroup.LayoutParams.WRAP_CONTENT,
        ),
      )
      frameLayout
    } catch (e: Exception) {
      android.util.Log.e("WidgetHostViewFactory", "Error creating widget view", e)
      val errorView = View(context)
      errorView.setBackgroundColor(android.graphics.Color.RED)
      errorView.layoutParams = ViewGroup.LayoutParams(100, 100)
      errorView
    }
  }
}
