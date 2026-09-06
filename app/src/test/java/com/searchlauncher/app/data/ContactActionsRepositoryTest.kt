package com.searchlauncher.app.data

import android.Manifest
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.database.MatrixCursor
import android.provider.ContactsContract
import androidx.test.core.app.ApplicationProvider
import com.searchlauncher.app.SearchLauncherApp
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = SearchLauncherApp::class)
class ContactActionsRepositoryTest {

  private lateinit var repo: ContactActionsRepository

  @Before
  fun setUp() {
    val context: Context = ApplicationProvider.getApplicationContext()
    repo = ContactActionsRepository(context)
  }

  private fun contact() =
    SearchResult.Contact(
      id = "lookup123/42",
      title = "Jane Doe",
      subtitle = null,
      icon = null,
      lookupKey = "lookup123",
      contactId = 42L,
      photoUri = null,
    )

  private fun repositoryWithContactData(hasWhatsApp: Boolean): ContactActionsRepository {
    val context = mockk<Context>(relaxed = true)
    val resolver = mockk<ContentResolver>()
    every { context.checkSelfPermission(Manifest.permission.READ_CONTACTS) } returns
      PackageManager.PERMISSION_GRANTED
    every { context.contentResolver } returns resolver
    every { context.packageManager.getPackageInfo("com.whatsapp", 0) } returns PackageInfo()
    every { context.getSharedPreferences(any(), any()).getString(any(), any()) } returns
      "com.whatsapp"
    every { resolver.query(ContactsContract.Data.CONTENT_URI, any(), any(), any(), any()) } answers
      {
        MatrixCursor(
            arrayOf(
              ContactsContract.Data._ID,
              ContactsContract.Data.MIMETYPE,
              ContactsContract.Data.DATA1,
            )
          )
          .apply {
            addRow(
              arrayOf<Any>(
                1L,
                ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE,
                "+15551234567",
              )
            )
            if (hasWhatsApp)
              addRow(
                arrayOf<Any>(
                  99L,
                  "vnd.android.cursor.item/vnd.com.whatsapp.profile",
                  "+15551234567",
                )
              )
          }
      }
    every {
      resolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, any(), any(), any(), any())
    } answers
      {
        MatrixCursor(arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER)).apply {
          addRow(arrayOf("+15551234567"))
        }
      }
    every {
      resolver.query(ContactsContract.CommonDataKinds.Email.CONTENT_URI, any(), any(), any(), any())
    } answers { MatrixCursor(arrayOf(ContactsContract.CommonDataKinds.Email.ADDRESS)) }
    return ContactActionsRepository(context)
  }

  @Test
  fun `installed WhatsApp and previous use do not add an action without contact account data`() =
    runBlocking {
      val actions = repositoryWithContactData(false).getContactActions(contact())
      assertEquals(
        listOf(ContactActionsRepository.SMS_ACTION_KEY, ContactActionsRepository.CALL_ACTION_KEY),
        actions.map { it.packageName },
      )
    }

  @Test
  fun `WhatsApp contact account keeps its action and specific data row`() = runBlocking {
    val actions = repositoryWithContactData(true).getContactActions(contact())
    assertEquals("com.whatsapp", actions.first().packageName)
    assertEquals(99L, actions.first().dataId)
    assertEquals(3, actions.size)
  }

  // --- chatPackageFromMimeType ---

  @Test
  fun `detects Telegram from data mime type`() {
    val mime = "vnd.android.cursor.item/vnd.org.telegram.messenger.android.profile"
    assertEquals("org.telegram.messenger", repo.chatPackageFromMimeType(mime))
  }

  @Test
  fun `detects WhatsApp from data mime type`() {
    val mime = "vnd.android.cursor.item/vnd.com.whatsapp.profile"
    assertEquals("com.whatsapp", repo.chatPackageFromMimeType(mime))
  }

  @Test
  fun `detects Signal from data mime type`() {
    val mime = "vnd.android.cursor.item/vnd.org.thoughtcrime.securesms.profile"
    assertEquals("org.thoughtcrime.securesms", repo.chatPackageFromMimeType(mime))
  }

  @Test
  fun `mime detection is case insensitive`() {
    val mime = "VND.ANDROID.CURSOR.ITEM/VND.ORG.TELEGRAM.MESSENGER.ANDROID.PROFILE"
    assertEquals("org.telegram.messenger", repo.chatPackageFromMimeType(mime))
  }

  @Test
  fun `non-chat mime types return null`() {
    assertNull(repo.chatPackageFromMimeType("vnd.android.cursor.item/phone_v2"))
    assertNull(repo.chatPackageFromMimeType("vnd.android.cursor.item/email_v2"))
  }

  // --- buildContactActionIntents ---

  @Test
  fun `call action dials the number then falls back to the contact`() {
    val action =
      ContactChatAction(
        label = "Call",
        packageName = ContactActionsRepository.CALL_ACTION_KEY,
        phoneNumber = "+1 555-1234",
      )
    val intents = repo.buildContactActionIntents(contact(), action)

    assertEquals(2, intents.size)
    assertEquals(Intent.ACTION_DIAL, intents[0].action)
    assertEquals("tel", intents[0].data?.scheme)
    // The list always ends with a generic contact-lookup fallback.
    assertEquals(Intent.ACTION_VIEW, intents.last().action)
  }

  @Test
  fun `sms action uses smsto sendto`() {
    val action =
      ContactChatAction(
        label = "SMS",
        packageName = ContactActionsRepository.SMS_ACTION_KEY,
        phoneNumber = "5551234",
      )
    val intents = repo.buildContactActionIntents(contact(), action)

    assertEquals(Intent.ACTION_SENDTO, intents[0].action)
    assertEquals("smsto", intents[0].data?.scheme)
  }

  @Test
  fun `email action uses mailto sendto`() {
    val action =
      ContactChatAction(
        label = "Email",
        packageName = ContactActionsRepository.EMAIL_ACTION_KEY,
        phoneNumber = "jane@example.com",
      )
    val intents = repo.buildContactActionIntents(contact(), action)

    assertEquals(Intent.ACTION_SENDTO, intents[0].action)
    assertEquals("mailto", intents[0].data?.scheme)
  }

  @Test
  fun `whatsapp action targets wa_me with a digits-only number`() {
    val action =
      ContactChatAction(
        label = "WhatsApp",
        packageName = ContactActionsRepository.WHATSAPP_PACKAGE,
        phoneNumber = "+1 (555) 123-4567",
      )
    val intents = repo.buildContactActionIntents(contact(), action)

    val waIntent = intents.first { it.data?.toString()?.startsWith("https://wa.me/") == true }
    assertEquals("https://wa.me/15551234567", waIntent.data.toString())
    assertEquals(ContactActionsRepository.WHATSAPP_PACKAGE, waIntent.`package`)
  }

  @Test
  fun `dataId opens the specific data row scoped to the package`() {
    val action =
      ContactChatAction(label = "Telegram", packageName = "org.telegram.messenger", dataId = 99L)
    val intents = repo.buildContactActionIntents(contact(), action)

    val dataIntent = intents.first { it.data?.toString()?.endsWith("/99") == true }
    assertEquals(Intent.ACTION_VIEW, dataIntent.action)
    assertEquals("org.telegram.messenger", dataIntent.`package`)
  }

  @Test
  fun `falls back to the contact lookup when the action has no actionable data`() {
    // Call key but no phone number, and no dataId: only the generic fallback remains.
    val action =
      ContactChatAction(
        label = "Call",
        packageName = ContactActionsRepository.CALL_ACTION_KEY,
        phoneNumber = null,
      )
    val intents = repo.buildContactActionIntents(contact(), action)

    assertEquals(1, intents.size)
    assertEquals(Intent.ACTION_VIEW, intents[0].action)
    assertEquals(ContactActionsRepository.CALL_ACTION_KEY, intents[0].`package`)
    assertTrue(intents[0].data.toString().contains("lookup123"))
  }

  // --- orderByLastUsed ---

  private fun action(pkg: String) = ContactChatAction(label = pkg, packageName = pkg)

  @Test
  fun `last used ordering keeps order when there is no last package`() {
    val actions = listOf(action("a"), action("b"), action("c"))
    assertEquals(actions, repo.orderByLastUsed(actions, null))
  }

  @Test
  fun `last used ordering moves the last package to the front, others stable`() {
    val actions = listOf(action("a"), action("b"), action("c"))
    val ordered = repo.orderByLastUsed(actions, "c")
    assertEquals(listOf("c", "a", "b"), ordered.map { it.packageName })
  }

  @Test
  fun `last used ordering leaves order unchanged when last package is absent`() {
    val actions = listOf(action("a"), action("b"))
    assertEquals(actions, repo.orderByLastUsed(actions, "zzz"))
  }
}
