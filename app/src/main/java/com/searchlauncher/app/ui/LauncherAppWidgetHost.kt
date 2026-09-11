package com.searchlauncher.app.ui

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetProviderInfo
import android.content.Context

/**
 * Hosts home-screen widgets with a view that does not clip collection children.
 *
 * List/grid widgets (Smartspacer's multi-line widget, ToDo Agenda) inflate an AdapterView inside
 * the host. Clipping the host to its padding box, or giving the provider an empty
 * [android.appwidget.AppWidgetManager.OPTION_APPWIDGET_SIZES] list, leaves that AdapterView with
 * nothing to draw.
 */
class LauncherAppWidgetHost(context: Context, hostId: Int) : AppWidgetHost(context, hostId) {
  override fun onCreateView(
    context: Context,
    appWidgetId: Int,
    appWidget: AppWidgetProviderInfo?,
  ): AppWidgetHostView = LauncherAppWidgetHostView(context)
}

class LauncherAppWidgetHostView(context: Context) : AppWidgetHostView(context) {
  init {
    clipChildren = false
    clipToPadding = false
  }
}
