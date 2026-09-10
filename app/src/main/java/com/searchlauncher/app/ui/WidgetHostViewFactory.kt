package com.searchlauncher.app.ui

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.FrameLayout
import com.searchlauncher.app.data.WidgetRepository
import java.util.WeakHashMap

object WidgetHostViewFactory {
  private val lastReportedSize = WeakHashMap<AppWidgetHostView, Pair<Int, Int>>()
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

  /**
   * Size options to store on a newly bound widget before its first layout.
   *
   * Collection providers (list/grid) measure item RemoteViews from
   * [AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH] / `MAX_HEIGHT`. Leaving those at 0 — the default
   * until the view is laid out — produces empty rows. Android 12+ also needs a non-empty
   * [AppWidgetManager.OPTION_APPWIDGET_SIZES] list; the deprecated four-int
   * [AppWidgetHostView.updateAppWidgetSize] call writes an empty list and responsive layouts then
   * refuse to apply.
   */
  fun sizeOptions(widthDp: Int, heightDp: Int): Bundle {
    val width = widthDp.coerceAtLeast(1)
    val height = heightDp.coerceAtLeast(1)
    val options = Bundle()
    options.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, width)
    options.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, width)
    options.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, height)
    options.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, height)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      options.putParcelableArrayList(
        AppWidgetManager.OPTION_APPWIDGET_SIZES,
        arrayListOf(SizeF(width.toFloat(), height.toFloat())),
      )
    }
    return options
  }

  fun defaultSizeOptions(context: Context): Bundle {
    val density = context.resources.displayMetrics.density
    val widthDp = (context.resources.displayMetrics.widthPixels / density).toInt() - 32
    return sizeOptions(widthDp.coerceAtLeast(1), WidgetRepository.DEFAULT_WIDGET_HEIGHT_DP)
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
      hostView.clipChildren = false
      hostView.clipToPadding = false

      // Responsive/list providers need the actual allocated size, including after a resize.
      // Post outside layout and coalesce changes so provider updates cannot re-enter measurement.
      val reportSize = Runnable { reportAllocatedSize(hostView, appWidgetManager, appWidgetId) }
      hostView.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
        hostView.removeCallbacks(reportSize)
        hostView.post(reportSize)
      }

      // Enforce minimum height (often crucial for list widgets like Calendar).
      //
      // [AppWidgetProviderInfo.minHeight] is already in pixels — the framework resolves the
      // provider's `android:minHeight` dimension against the display when it parses the manifest.
      // Scaling it by the density again made every widget as many times too tall as the screen is
      // dense: Chrome's Dino asks for 110dp, arrives as 358px, and was being given 1163px.
      hostView.minimumHeight = appWidgetInfo.minHeight

      // Wrap in a FrameLayout for layout params or padding if needed
      val frameLayout = FrameLayout(context)
      frameLayout.clipChildren = false
      frameLayout.clipToPadding = false
      frameLayout.addView(
        hostView,
        FrameLayout.LayoutParams(
          ViewGroup.LayoutParams.MATCH_PARENT,
          ViewGroup.LayoutParams.MATCH_PARENT,
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

  /**
   * Pushes [container]'s laid-out pixel size to the hosted [AppWidgetHostView], if any.
   *
   * Compose knows the allocated box before the host view's own layout listener runs, which is when
   * collection adapters first ask for item RemoteViews.
   */
  fun reportContainerSize(
    container: ViewGroup,
    appWidgetManager: AppWidgetManager,
    widthPx: Int,
    heightPx: Int,
  ) {
    val hostView = findHostView(container) ?: return
    reportAllocatedSize(hostView, appWidgetManager, hostView.appWidgetId, widthPx, heightPx)
  }

  internal fun reportAllocatedSize(
    hostView: AppWidgetHostView,
    appWidgetManager: AppWidgetManager,
    appWidgetId: Int,
    widthPx: Int = hostView.width,
    heightPx: Int = hostView.height,
  ): Pair<Int, Int>? {
    val density = hostView.resources.displayMetrics.density
    val resolvedWidth =
      widthPx.takeIf { it > 0 }
        ?: hostView.width.takeIf { it > 0 }
        ?: (hostView.parent as? View)?.width
        ?: 0
    val resolvedHeight =
      heightPx.takeIf { it > 0 }
        ?: hostView.height.takeIf { it > 0 }
        ?: (hostView.parent as? View)?.height
        ?: 0
    val size = (resolvedWidth / density).toInt() to (resolvedHeight / density).toInt()
    if (size.first <= 0 || size.second <= 0 || size == lastReportedSize[hostView])
      return lastReportedSize[hostView]
    try {
      val options =
        try {
          appWidgetManager.getAppWidgetOptions(appWidgetId) ?: Bundle()
        } catch (_: Exception) {
          Bundle()
        }
      options.putAll(sizeOptions(size.first, size.second))
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        hostView.updateAppWidgetSize(
          options,
          listOf(SizeF(size.first.toFloat(), size.second.toFloat())),
        )
      } else {
        @Suppress("DEPRECATION")
        hostView.updateAppWidgetSize(options, size.first, size.second, size.first, size.second)
      }
      lastReportedSize[hostView] = size
      refreshCollectionAdapters(hostView, appWidgetManager, appWidgetId)
    } catch (e: Exception) {
      android.util.Log.w("WidgetHostViewFactory", "Cannot report widget $appWidgetId size", e)
      return lastReportedSize[hostView]
    }
    return size
  }

  internal fun findHostView(container: ViewGroup): AppWidgetHostView? {
    if (container is AppWidgetHostView) return container
    for (i in 0 until container.childCount) {
      when (val child = container.getChildAt(i)) {
        is AppWidgetHostView -> return child
        is ViewGroup ->
          findHostView(child)?.let {
            return it
          }
      }
    }
    return null
  }

  /**
   * Collection widgets bind their RemoteViewsService when first inflated, often at 0×0. After the
   * host has a real size, tell each AdapterView to reload so list items are measured against the
   * reported dp rather than staying empty.
   */
  internal fun refreshCollectionAdapters(
    hostView: AppWidgetHostView,
    appWidgetManager: AppWidgetManager,
    appWidgetId: Int,
  ) {
    val viewIds = linkedSetOf<Int>()
    fun walk(view: View) {
      if (view is AdapterView<*> && view.id != View.NO_ID) viewIds += view.id
      if (view is ViewGroup) {
        for (i in 0 until view.childCount) walk(view.getChildAt(i))
      }
    }
    walk(hostView)
    viewIds.forEach { viewId ->
      try {
        @Suppress("DEPRECATION")
        appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, viewId)
      } catch (e: Exception) {
        android.util.Log.w(
          "WidgetHostViewFactory",
          "Cannot refresh collection $viewId on widget $appWidgetId",
          e,
        )
      }
    }
  }
}
