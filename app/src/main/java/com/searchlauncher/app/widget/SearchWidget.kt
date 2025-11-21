package com.searchlauncher.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.RemoteViews
import com.searchlauncher.app.R
import com.searchlauncher.app.service.OverlayService

/**
 * Search widget that opens the overlay when tapped. Can be resized from a small icon to a full
 * search bar.
 */
class SearchWidget : AppWidgetProvider() {

        override fun onUpdate(
                context: Context,
                appWidgetManager: AppWidgetManager,
                appWidgetIds: IntArray
        ) {
                for (appWidgetId in appWidgetIds) {
                        updateAppWidget(context, appWidgetManager, appWidgetId)
                }
        }

        override fun onEnabled(context: Context) {
                // Enter relevant functionality for when the first widget is created
        }

        override fun onDisabled(context: Context) {
                // Enter relevant functionality for when the last widget is disabled
        }

        companion object {
                fun updateAppWidget(
                        context: Context,
                        appWidgetManager: AppWidgetManager,
                        appWidgetId: Int
                ) {
                        // Check if overlay permission is granted
                        val hasOverlayPermission =
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                        Settings.canDrawOverlays(context)
                                } else {
                                        true
                                }

                        val pendingIntent =
                                if (hasOverlayPermission) {
                                        // Open overlay service directly
                                        val intent =
                                                Intent(context, OverlayService::class.java).apply {
                                                        action = OverlayService.ACTION_SHOW_SEARCH
                                                }
                                        PendingIntent.getService(
                                                context,
                                                0,
                                                intent,
                                                PendingIntent.FLAG_UPDATE_CURRENT or
                                                        PendingIntent.FLAG_IMMUTABLE
                                        )
                                } else {
                                        // Open MainActivity to request permission
                                        val intent =
                                                Intent(
                                                                context,
                                                                com.searchlauncher.app.ui
                                                                                .MainActivity::class
                                                                        .java
                                                        )
                                                        .apply {
                                                                flags =
                                                                        Intent.FLAG_ACTIVITY_NEW_TASK or
                                                                                Intent.FLAG_ACTIVITY_CLEAR_TOP
                                                                action = Intent.ACTION_MAIN
                                                        }
                                        PendingIntent.getActivity(
                                                context,
                                                0,
                                                intent,
                                                PendingIntent.FLAG_UPDATE_CURRENT or
                                                        PendingIntent.FLAG_IMMUTABLE
                                        )
                                }

                        // Construct the RemoteViews object
                        val views = RemoteViews(context.packageName, R.layout.search_widget)
                        views.setOnClickPendingIntent(R.id.widget_container, pendingIntent)

                        // Instruct the widget manager to update the widget
                        appWidgetManager.updateAppWidget(appWidgetId, views)
                }
        }
}
