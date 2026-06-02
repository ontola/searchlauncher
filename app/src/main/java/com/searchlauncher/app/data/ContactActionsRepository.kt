package com.searchlauncher.app.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ContactActionsRepository(private val context: Context) {
  suspend fun getContactActions(contact: SearchResult.Contact): List<ContactChatAction> =
    withContext(Dispatchers.IO) {
      val permission = context.checkSelfPermission(android.Manifest.permission.READ_CONTACTS)
      if (permission != android.content.pm.PackageManager.PERMISSION_GRANTED) {
        return@withContext emptyList()
      }

      val actionsByPackage = linkedMapOf<String, ContactChatAction>()
      val dataCursor =
        context.contentResolver.query(
          android.provider.ContactsContract.Data.CONTENT_URI,
          arrayOf(
            android.provider.ContactsContract.Data._ID,
            android.provider.ContactsContract.Data.MIMETYPE,
            android.provider.ContactsContract.Data.DATA1,
          ),
          "${android.provider.ContactsContract.Data.CONTACT_ID} = ?",
          arrayOf(contact.contactId.toString()),
          null,
        )

      dataCursor?.use {
        val idIndex = it.getColumnIndex(android.provider.ContactsContract.Data._ID)
        val mimeIndex = it.getColumnIndex(android.provider.ContactsContract.Data.MIMETYPE)
        val data1Index = it.getColumnIndex(android.provider.ContactsContract.Data.DATA1)
        while (it.moveToNext()) {
          val mimeType = it.getString(mimeIndex) ?: continue
          val packageName = chatPackageFromMimeType(mimeType) ?: continue
          if (!isPackageInstalled(packageName)) continue
          actionsByPackage.putIfAbsent(
            packageName,
            ContactChatAction(
              label = getApplicationLabel(packageName) ?: packageName,
              packageName = packageName,
              dataId = it.getLong(idIndex),
              phoneNumber = it.getString(data1Index),
              icon = getApplicationIcon(packageName),
            ),
          )
        }
      }

      val phoneNumber = getPrimaryPhoneNumber(contact.contactId)
      if (phoneNumber != null && isPackageInstalled(WHATSAPP_PACKAGE)) {
        actionsByPackage.putIfAbsent(
          WHATSAPP_PACKAGE,
          ContactChatAction(
            label = getApplicationLabel(WHATSAPP_PACKAGE) ?: "WhatsApp",
            packageName = WHATSAPP_PACKAGE,
            phoneNumber = phoneNumber,
            icon = getApplicationIcon(WHATSAPP_PACKAGE),
          ),
        )
      }
      if (phoneNumber != null) {
        actionsByPackage.putIfAbsent(
          SMS_ACTION_KEY,
          ContactChatAction(
            label = "SMS",
            packageName = SMS_ACTION_KEY,
            phoneNumber = phoneNumber,
            icon = context.getDrawable(android.R.drawable.sym_action_chat),
          ),
        )
        actionsByPackage.putIfAbsent(
          CALL_ACTION_KEY,
          ContactChatAction(
            label = "Call",
            packageName = CALL_ACTION_KEY,
            phoneNumber = phoneNumber,
            icon = context.getDrawable(android.R.drawable.ic_menu_call),
          ),
        )
      }
      val emailAddress = getPrimaryEmailAddress(contact.contactId)
      if (emailAddress != null) {
        actionsByPackage.putIfAbsent(
          EMAIL_ACTION_KEY,
          ContactChatAction(
            label = "Email",
            packageName = EMAIL_ACTION_KEY,
            phoneNumber = emailAddress,
            icon = context.getDrawable(android.R.drawable.ic_dialog_email),
          ),
        )
      }

      val actions = actionsByPackage.values.toList()
      val lastPackage = getLastContactActionPackage(contact.id)
      if (lastPackage == null) {
        actions
      } else {
        actions.sortedBy { if (it.packageName == lastPackage) 0 else 1 }
      }
    }

  fun launchContactAction(contact: SearchResult.Contact, action: ContactChatAction): Boolean {
    val intents = buildList {
      if (action.packageName == CALL_ACTION_KEY && action.phoneNumber != null) {
        add(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(action.phoneNumber)}")))
      }

      if (action.packageName == SMS_ACTION_KEY && action.phoneNumber != null) {
        add(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${Uri.encode(action.phoneNumber)}")))
      }

      if (action.packageName == EMAIL_ACTION_KEY && action.phoneNumber != null) {
        add(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${Uri.encode(action.phoneNumber)}")))
      }

      if (action.dataId != null) {
        add(
          Intent(
              Intent.ACTION_VIEW,
              Uri.withAppendedPath(
                android.provider.ContactsContract.Data.CONTENT_URI,
                action.dataId.toString(),
              ),
            )
            .setPackage(action.packageName)
        )
      }

      if (action.packageName == WHATSAPP_PACKAGE && action.phoneNumber != null) {
        val normalizedPhone =
          com.searchlauncher.app.util.ContactUtils.normalizePhoneNumber(action.phoneNumber)
        if (normalizedPhone != null) {
          add(
            Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$normalizedPhone"))
              .setPackage(WHATSAPP_PACKAGE)
          )
        }
      }

      add(
        Intent(
            Intent.ACTION_VIEW,
            Uri.withAppendedPath(
              android.provider.ContactsContract.Contacts.CONTENT_LOOKUP_URI,
              contact.lookupKey,
            ),
          )
          .setPackage(action.packageName)
      )
    }

    for (intent in intents) {
      try {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        setLastContactActionPackage(contact.id, action.packageName)
        return true
      } catch (e: Exception) {
        // Try the next route.
      }
    }
    return false
  }

  internal fun chatPackageFromMimeType(mimeType: String): String? =
    KNOWN_CHAT_PACKAGES.firstOrNull { packageName ->
      mimeType.contains(packageName, ignoreCase = true)
    }

  private fun getPrimaryPhoneNumber(contactId: Long): String? {
    val cursor =
      context.contentResolver.query(
        android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
        arrayOf(android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER),
        "${android.provider.ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
        arrayOf(contactId.toString()),
        "${android.provider.ContactsContract.CommonDataKinds.Phone.IS_PRIMARY} DESC",
      )

    cursor?.use {
      val numberIndex =
        it.getColumnIndex(android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER)
      while (it.moveToNext()) {
        val number = it.getString(numberIndex)
        if (!number.isNullOrBlank()) return number
      }
    }
    return null
  }

  private fun getPrimaryEmailAddress(contactId: Long): String? {
    val cursor =
      context.contentResolver.query(
        android.provider.ContactsContract.CommonDataKinds.Email.CONTENT_URI,
        arrayOf(android.provider.ContactsContract.CommonDataKinds.Email.ADDRESS),
        "${android.provider.ContactsContract.CommonDataKinds.Email.CONTACT_ID} = ?",
        arrayOf(contactId.toString()),
        "${android.provider.ContactsContract.CommonDataKinds.Email.IS_PRIMARY} DESC",
      )

    cursor?.use {
      val addressIndex =
        it.getColumnIndex(android.provider.ContactsContract.CommonDataKinds.Email.ADDRESS)
      while (it.moveToNext()) {
        val address = it.getString(addressIndex)
        if (!address.isNullOrBlank()) return address
      }
    }
    return null
  }

  private fun isPackageInstalled(packageName: String): Boolean =
    try {
      context.packageManager.getPackageInfo(packageName, 0)
      true
    } catch (e: Exception) {
      false
    }

  private fun getApplicationLabel(packageName: String): String? =
    try {
      val appInfo = context.packageManager.getApplicationInfo(packageName, 0)
      context.packageManager.getApplicationLabel(appInfo).toString()
    } catch (e: Exception) {
      null
    }

  private fun getApplicationIcon(packageName: String) =
    try {
      context.packageManager.getApplicationIcon(packageName)
    } catch (e: Exception) {
      null
    }

  private fun getLastContactActionPackage(contactId: String): String? =
    context
      .getSharedPreferences(CONTACT_ACTION_PREFS, Context.MODE_PRIVATE)
      .getString(contactId, null)

  private fun setLastContactActionPackage(contactId: String, packageName: String) {
    context
      .getSharedPreferences(CONTACT_ACTION_PREFS, Context.MODE_PRIVATE)
      .edit()
      .putString(contactId, packageName)
      .apply()
  }

  companion object {
    private const val CONTACT_ACTION_PREFS = "contact_chat_actions"
    private const val CALL_ACTION_KEY = "com.searchlauncher.action.CALL_CONTACT"
    private const val SMS_ACTION_KEY = "com.searchlauncher.action.SMS_CONTACT"
    private const val EMAIL_ACTION_KEY = "com.searchlauncher.action.EMAIL_CONTACT"
    private const val WHATSAPP_PACKAGE = "com.whatsapp"
    private val KNOWN_CHAT_PACKAGES =
      setOf(
        WHATSAPP_PACKAGE,
        "com.whatsapp.w4b",
        "org.telegram.messenger",
        "org.thunderdog.challegram",
        "org.telegram.plus",
        "com.facebook.orca",
        "com.viber.voip",
        "com.signal",
        "org.thoughtcrime.securesms",
        "com.google.android.apps.messaging",
        "com.skype.raider",
      )
  }
}
