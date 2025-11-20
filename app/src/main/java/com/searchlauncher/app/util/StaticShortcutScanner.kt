package com.searchlauncher.app.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.content.res.XmlResourceParser
import org.xmlpull.v1.XmlPullParser

data class StaticShortcut(
    val packageName: String,
    val id: String,
    val shortLabel: String,
    val longLabel: String?,
    val iconResId: Int,
    val intent: Intent
)

object StaticShortcutScanner {
    fun scan(context: Context): List<StaticShortcut> {
        val shortcuts = mutableListOf<StaticShortcut>()
        val pm = context.packageManager

        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val activities = pm.queryIntentActivities(intent, PackageManager.GET_META_DATA)

        for (resolveInfo in activities) {
            val activityInfo = resolveInfo.activityInfo ?: continue
            val metaData = activityInfo.metaData ?: continue

            val resourceId = metaData.getInt("android.app.shortcuts", 0)
            if (resourceId == 0) continue

            try {
                val resources = pm.getResourcesForApplication(activityInfo.packageName)
                val parser = resources.getXml(resourceId)

                shortcuts.addAll(parseShortcuts(activityInfo.packageName, resources, parser))
                parser.close()
            } catch (_: Exception) {
                // Ignore
            }
        }
        return shortcuts
    }

    private fun parseShortcuts(
        packageName: String,
        resources: Resources,
        parser: XmlResourceParser
    ): List<StaticShortcut> {
        val shortcuts = mutableListOf<StaticShortcut>()
        var currentShortcutId: String? = null
        var currentShortLabel: String? = null
        var currentLongLabel: String? = null
        var currentIconResId = 0
        var currentIntent: Intent? = null

        try {
            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        if (parser.name == "shortcut") {
                            // Reset
                            currentShortcutId = null
                            currentShortLabel = null
                            currentLongLabel = null
                            currentIconResId = 0
                            currentIntent = null

                            for (i in 0 until parser.attributeCount) {
                                val name = parser.getAttributeName(i)
                                val valResId = parser.getAttributeResourceValue(i, 0)

                                when (name) {
                                    "shortcutId" -> {
                                        currentShortcutId = parser.getAttributeValue(i)
                                    }

                                    "shortcutShortLabel" -> {
                                        currentShortLabel = if (valResId != 0) {
                                            try {
                                                resources.getString(valResId)
                                            } catch (_: Exception) {
                                                null
                                            }
                                        } else {
                                            parser.getAttributeValue(i)
                                        }
                                    }

                                    "shortcutLongLabel" -> {
                                        currentLongLabel = if (valResId != 0) {
                                            try {
                                                resources.getString(valResId)
                                            } catch (_: Exception) {
                                                null
                                            }
                                        } else {
                                            parser.getAttributeValue(i)
                                        }
                                    }

                                    "icon" -> {
                                        currentIconResId = valResId
                                    }
                                }
                            }
                        } else if (parser.name == "intent") {
                            if (currentShortcutId != null && currentIntent == null) {
                                var action: String? = null
                                var targetPackage: String? = packageName
                                var targetClass: String? = null
                                var data: String? = null

                                for (i in 0 until parser.attributeCount) {
                                    val name = parser.getAttributeName(i)
                                    val value = parser.getAttributeValue(i)
                                    when (name) {
                                        "action" -> action = value
                                        "targetPackage" -> targetPackage = value
                                        "targetClass" -> targetClass = value
                                        "data" -> data = value
                                    }
                                }

                                if (action != null) {
                                    currentIntent = Intent(action)
                                    if (targetClass != null) {
                                        currentIntent.component =
                                            ComponentName(targetPackage!!, targetClass)
                                    } else {
                                        currentIntent.setPackage(targetPackage)
                                    }
                                    if (data != null) {
                                        currentIntent.data = android.net.Uri.parse(data)
                                    }
                                }
                            }
                        }
                    }

                    XmlPullParser.END_TAG -> {
                        if (parser.name == "shortcut") {
                            if (currentShortcutId != null && currentIntent != null && currentShortLabel != null) {
                                shortcuts.add(
                                    StaticShortcut(
                                        packageName,
                                        currentShortcutId,
                                        currentShortLabel,
                                        currentLongLabel,
                                        currentIconResId,
                                        currentIntent
                                    )
                                )
                            }
                            // Reset again
                            currentShortcutId = null
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return shortcuts
    }
}
