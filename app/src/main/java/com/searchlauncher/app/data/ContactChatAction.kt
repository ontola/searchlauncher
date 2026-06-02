package com.searchlauncher.app.data

import android.graphics.drawable.Drawable

data class ContactChatAction(
  val label: String,
  val packageName: String,
  val dataId: Long? = null,
  val phoneNumber: String? = null,
  val icon: Drawable? = null,
)
