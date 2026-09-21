package com.searchlauncher.app.data

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SmartActionManagerTest {

  private val context: Context = ApplicationProvider.getApplicationContext()
  private val manager = SmartActionManager(context)

  @Test
  fun `phone number includes add contact action with prefilled number`() {
    val results = manager.checkSmartActions("0612345678")

    val addContact = results.firstOrNull { it.id == "smart_action_add_contact_0612345678" }
    assertNotNull(addContact)
    addContact as SearchResult.Content

    assertEquals("Add 0612345678 to contacts", addContact.title)
    assertEquals("Contact", addContact.subtitle)

    val intent = Intent.parseUri(addContact.deepLink, Intent.URI_INTENT_SCHEME)
    assertEquals(Intent.ACTION_INSERT, intent.action)
    assertEquals(android.provider.ContactsContract.RawContacts.CONTENT_TYPE, intent.type)
    assertEquals(
      "0612345678",
      intent.getStringExtra(android.provider.ContactsContract.Intents.Insert.PHONE),
    )
  }

  @Test
  fun `explicit sms trigger does not include add contact action`() {
    val results = manager.checkSmartActions("text 0612345678")

    assertTrue(results.any { it.id == "smart_action_sms_0612345678" })
    assertTrue(results.none { it.id == "smart_action_add_contact_0612345678" })
  }

  @Test
  fun `plain text does not create add snippet smart action`() {
    val results = manager.checkSmartActions("random query")

    assertTrue(results.none { it.id == "smart_action_add_snippet" })
  }

  @Test
  fun `multi label domain opens as a website`() {
    val results = manager.checkSmartActions("ontola.staging.atomicserver.eu")

    val open = results.firstOrNull { it.id == "smart_action_url_ontola.staging.atomicserver.eu" }
    assertNotNull(open)
    open as SearchResult.Content
    assertEquals("Open ontola.staging.atomicserver.eu", open.title)
    assertEquals("Website", open.subtitle)
    assertEquals("https://ontola.staging.atomicserver.eu", open.deepLink)
  }

  @Test
  fun `explicit http url keeps its scheme`() {
    val query = "http://ontola.staging.atomicserver.eu/path"
    val results = manager.checkSmartActions(query)

    val open = results.first { it.id == "smart_action_url_$query" } as SearchResult.Content
    assertEquals(query, open.deepLink)
  }

  @Test
  fun `plain text is not a website`() {
    val results = manager.checkSmartActions("random query")

    assertTrue(results.none { it.id.startsWith("smart_action_url_") })
  }
}
