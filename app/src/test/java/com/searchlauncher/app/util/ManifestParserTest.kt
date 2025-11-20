package com.searchlauncher.app.util

import android.content.res.XmlResourceParser
import com.searchlauncher.app.util.AppCapabilityScanner.parseManifest
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test
import org.xmlpull.v1.XmlPullParser

class ManifestParserTest {

    @Test
    fun `parses exported activity with data correctly`() {
        val parser = mockk<XmlResourceParser>(relaxed = true)
        var step = 0

        // Defines the flow: Start Doc -> Activity -> Action -> Data -> End Activity -> End Doc
        val events = listOf(
            XmlPullParser.START_DOCUMENT, // step 0
            XmlPullParser.START_TAG,      // step 1 (activity)
            XmlPullParser.START_TAG,      // step 2 (action)
            XmlPullParser.START_TAG,      // step 3 (data)
            XmlPullParser.END_TAG,        // step 4 (activity)
            XmlPullParser.END_DOCUMENT    // step 5
        )

        // 1. Initial Event Type (Called before loop)
        every { parser.eventType } answers { events[step] }

        // 2. Next Event Type (Called inside loop)
        every { parser.next() } answers {
            step++
            if (step < events.size) events[step] else XmlPullParser.END_DOCUMENT
        }

        // 3. Mock Names based on step
        every { parser.name } answers {
            when (step) {
                1, 4 -> "activity"
                2 -> "action"
                3 -> "data"
                else -> ""
            }
        }

        // 4. Mock Attributes
        every { parser.getAttributeValue(any(), any()) } answers {
            val attrName = secondArg<String>()
            when (step) {
                1 -> { // Activity tag
                    when (attrName) {
                        "name" -> ".MainActivity"
                        "exported" -> "true"
                        else -> null
                    }
                }

                2 if attrName == "name" -> { // Action tag
                    "android.intent.action.VIEW"
                }

                3 -> { // Data tag
                    when (attrName) {
                        "scheme" -> "https"
                        "mimeType" -> null
                        else -> null
                    }
                }

                else -> {
                    null
                }
            }
        }

        val result = parseManifest("com.example", parser)

        assertEquals(1, result.size)
        assertEquals("com.example.MainActivity", result[0].activityName)
        assertEquals("https", result[0].schemes.first())
    }

    @Test
    fun `ignores non-exported activities`() {
        val parser = mockk<XmlResourceParser>(relaxed = true)
        var step = 0

        // Flow: Start -> Activity (Not Exported) -> Data -> End Activity -> End Doc
        val events = listOf(
            XmlPullParser.START_DOCUMENT,
            XmlPullParser.START_TAG, // Activity
            XmlPullParser.START_TAG, // Data (It has data, but isn't exported)
            XmlPullParser.END_TAG,   // End Activity
            XmlPullParser.END_DOCUMENT
        )

        every { parser.eventType } answers { events[step] }

        every { parser.next() } answers {
            step++
            if (step < events.size) events[step] else XmlPullParser.END_DOCUMENT
        }

        every { parser.name } answers {
            when (step) {
                1, 3 -> "activity"
                2 -> "data"
                else -> ""
            }
        }

        every { parser.getAttributeValue(any(), any()) } answers {
            val attrName = secondArg<String>()
            if (step == 1) {
                if (attrName == "exported") "false" else ".PrivateActivity"
            } else if (step == 2 && attrName == "scheme") {
                "http"
            } else {
                null
            }
        }

        val result = parseManifest("com.example", parser)

        // Should be empty because exported="false"
        assertEquals(0, result.size)
    }
}