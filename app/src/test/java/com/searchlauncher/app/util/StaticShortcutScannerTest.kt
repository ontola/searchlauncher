package com.searchlauncher.app.util

import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.content.res.Resources
import android.content.res.XmlResourceParser
import android.os.Bundle
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.xmlpull.v1.XmlPullParser

@RunWith(RobolectricTestRunner::class)
class StaticShortcutScannerTest {

    @Test
    fun `scan parses shortcuts correctly from XML`() {
        // Setup
        val context = mockk<Context>(relaxed = true)
        val pm = mockk<PackageManager>()
        val resources = mockk<Resources>()
        val parser = mockk<XmlResourceParser>(relaxed = true)

        //  Create a Fake Activity with MetaData
        val packageName = "com.fake.app"
        val activityInfo = ActivityInfo().apply {
            this.packageName = packageName
            this.applicationInfo = ApplicationInfo().apply { this.packageName = packageName }
            this.metaData = Bundle().apply { putInt("android.app.shortcuts", 123) }
        }
        val resolveInfo = ResolveInfo().apply { this.activityInfo = activityInfo }

        // Context -> PM -> ResolveInfo -> Resources -> Parser
        every { context.packageManager } returns pm
        every { pm.queryIntentActivities(any(), any<Int>()) } returns listOf(resolveInfo)
        every { pm.getResourcesForApplication(packageName) } returns resources
        every { resources.getXml(123) } returns parser

        var step = 0
        val events = listOf(
            XmlPullParser.START_DOCUMENT,
            XmlPullParser.START_TAG, // shortcut
            XmlPullParser.START_TAG, // intent
            XmlPullParser.END_TAG,   // shortcut
            XmlPullParser.END_DOCUMENT
        )

        every { parser.eventType } answers { events[step] }
        every { parser.next() } answers {
            step++
            if (step < events.size) events[step] else XmlPullParser.END_DOCUMENT
        }

        // Control Tag Names
        every { parser.name } answers {
            when (step) {
                1, 3 -> "shortcut"
                2 -> "intent"
                else -> ""
            }
        }

        every { parser.attributeCount } answers { if (step == 1) 2 else if (step == 2) 1 else 0 }

        every { parser.getAttributeName(any()) } answers {
            val index = firstArg<Int>()
            if (step == 1) { // shortcut tag
                if (index == 0) "shortcutId" else "shortcutShortLabel"
            } else { // intent tag
                "action"
            }
        }

        every { parser.getAttributeValue(any<Int>()) } answers {
            val index = firstArg<Int>()
            if (step == 1) {
                if (index == 0) "shortcut_1" else "My Shortcut"
            } else {
                "android.intent.action.VIEW"
            }
        }

        every { parser.getAttributeResourceValue(any(), any()) } returns 0

        //  Run
        val results = StaticShortcutScanner.scan(context)

        // Assert
        assertEquals(1, results.size)
        assertEquals("shortcut_1", results[0].id)
        assertEquals("My Shortcut", results[0].shortLabel)
        assertEquals("android.intent.action.VIEW", results[0].intent.action)
    }
}