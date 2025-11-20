package com.searchlauncher.app.util

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.XmlResourceParser
import org.xmlpull.v1.XmlPullParser

data class AppCapability(
    val packageName: String,
    val activityName: String,
    val schemes: List<String>,
    val mimeTypes: List<String>,
    val actions: List<String>
)

object AppCapabilityScanner {
    private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"

    fun scan(context: Context): List<AppCapability> {
        val capabilities = mutableListOf<AppCapability>()
        val pm = context.packageManager
        // Get all installed packages
        val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)

        for (appInfo in packages) {

            try {
                val pkgContext = context.createPackageContext(appInfo.packageName, 0)
                val assets = pkgContext.assets
                val parser = assets.openXmlResourceParser("AndroidManifest.xml")

                val appCaps = parseManifest(appInfo.packageName, parser)
                if (appCaps.isNotEmpty()) {
                    capabilities.addAll(appCaps)
                }
                parser.close()
            } catch (_: Exception) {
                // Ignore errors (e.g. restricted packages)
            }
        }
        return capabilities
    }

    fun parseManifest(packageName: String, parser: XmlResourceParser): List<AppCapability> {
        val appCapabilities = mutableListOf<AppCapability>()
        var currentActivityName: String? = null
        val currentSchemes = mutableListOf<String>()
        val currentMimeTypes = mutableListOf<String>()
        val currentActions = mutableListOf<String>()
        var isExported = false

        try {
            var type = parser.eventType
            while (type != XmlPullParser.END_DOCUMENT) {
                when (type) {
                    XmlPullParser.START_TAG -> {
                        when (parser.name) {
                            "activity", "activity-alias" -> {
                                // Reset state for new activity
                                val rawName = parser.getAttributeValue(ANDROID_NS, "name")
                                currentActivityName = resolveActivityName(packageName, rawName)
                                isExported =
                                    parser.getAttributeValue(ANDROID_NS, "exported") == "true"

                                currentSchemes.clear()
                                currentMimeTypes.clear()
                                currentActions.clear()
                            }

                            "action" -> {
                                parser.getAttributeValue(ANDROID_NS, "name")
                                    ?.let { currentActions.add(it) }
                            }

                            "data" -> {
                                parser.getAttributeValue(ANDROID_NS, "scheme")
                                    ?.let { currentSchemes.add(it) }
                                parser.getAttributeValue(ANDROID_NS, "mimeType")
                                    ?.let { currentMimeTypes.add(it) }
                            }
                        }
                    }

                    XmlPullParser.END_TAG -> {
                        if (parser.name == "activity" || parser.name == "activity-alias") {
                            if (isExported && currentActivityName != null &&
                                (currentSchemes.isNotEmpty() || currentMimeTypes.isNotEmpty())
                            ) {

                                appCapabilities.add(
                                    AppCapability(
                                        packageName = packageName,
                                        activityName = currentActivityName,
                                        schemes = currentSchemes.toList(),
                                        mimeTypes = currentMimeTypes.toList(),
                                        actions = currentActions.toList()
                                    )
                                )
                            }
                            currentActivityName = null
                        }
                    }
                }
                type = parser.next()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return appCapabilities
    }

    private fun resolveActivityName(pkg: String, rawName: String?): String? {
        if (rawName == null) return null
        return when {
            rawName.startsWith(".") -> "$pkg$rawName"
            !rawName.contains(".") -> "$pkg.$rawName"
            else -> rawName
        }
    }
}
