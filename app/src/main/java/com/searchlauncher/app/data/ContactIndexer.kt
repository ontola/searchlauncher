package com.searchlauncher.app.data

import android.content.Context
import android.provider.ContactsContract
import com.searchlauncher.app.util.ContactUtils

/**
 * Builds AppSearch documents for the device's contacts.
 *
 * This is a pure reader: it queries the contacts provider and returns documents. Persisting them
 * (and checking the READ_CONTACTS permission) is the caller's responsibility.
 */
class ContactIndexer(private val context: Context) {

  /**
   * Reads contacts (with their phone numbers and emails) and returns one document per named
   * contact. [pauseCheck] is invoked between rows so indexing yields to active searches.
   */
  suspend fun buildDocuments(pauseCheck: suspend () -> Unit): List<AppSearchDocument> {
    val contacts = mutableListOf<AppSearchDocument>()

    // 1. Fetch search data (Phone numbers and Emails)
    val searchDataMap = mutableMapOf<Long, StringBuilder>()
    val dataCursor =
      context.contentResolver.query(
        ContactsContract.Data.CONTENT_URI,
        arrayOf(
          ContactsContract.Data.CONTACT_ID,
          ContactsContract.Data.MIMETYPE,
          ContactsContract.Data.DATA1,
        ),
        "${ContactsContract.Data.MIMETYPE} IN (?, ?)",
        arrayOf(
          ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE,
          ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE,
        ),
        null,
      )

    dataCursor?.use {
      val contactIdIndex = it.getColumnIndex(ContactsContract.Data.CONTACT_ID)
      val mimeTypeIndex = it.getColumnIndex(ContactsContract.Data.MIMETYPE)
      val dataIndex = it.getColumnIndex(ContactsContract.Data.DATA1)

      while (it.moveToNext()) {
        pauseCheck()
        val contactId = it.getLong(contactIdIndex)
        val mimeType = it.getString(mimeTypeIndex)
        val data = it.getString(dataIndex)

        if (data != null) {
          val sb = searchDataMap.getOrPut(contactId) { StringBuilder() }
          // Use space as separator for better tokenization
          sb.append(" ").append(data)

          // Normalize phone numbers and add variants (e.g. 06... for +31...)
          if (mimeType == ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE) {
            ContactUtils.getIndexableVariants(data).forEach { sb.append(" ").append(it) }
          }
        }
      }
    }

    // 2. Fetch Contacts and merge
    val cursor =
      context.contentResolver.query(
        ContactsContract.Contacts.CONTENT_URI,
        arrayOf(
          ContactsContract.Contacts._ID,
          ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
          ContactsContract.Contacts.LOOKUP_KEY,
          ContactsContract.Contacts.PHOTO_THUMBNAIL_URI,
        ),
        null,
        null,
        null,
      )

    cursor?.use {
      val idIndex = it.getColumnIndex(ContactsContract.Contacts._ID)
      val nameIndex = it.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)
      val lookupIndex = it.getColumnIndex(ContactsContract.Contacts.LOOKUP_KEY)
      val photoIndex = it.getColumnIndex(ContactsContract.Contacts.PHOTO_THUMBNAIL_URI)

      while (it.moveToNext()) {
        pauseCheck()
        val id = it.getLong(idIndex)
        val name = it.getString(nameIndex)
        val lookupKey = it.getString(lookupIndex)
        val photoUri = it.getString(photoIndex)

        if (name != null) {
          val extraData = searchDataMap[id]?.toString() ?: ""
          // Store photoUri + delimiter + searchable text
          val description = "${photoUri ?: ""}|$extraData"

          contacts.add(
            AppSearchDocument(
              namespace = "contacts",
              id = "$lookupKey/$id",
              name = name,
              description = description,
              score = 4,
              intentUri = "content://com.android.contacts/contacts/lookup/$lookupKey/$id",
            )
          )
        }
      }
    }

    return contacts
  }
}
