package com.searchlauncher.app.data

import android.graphics.drawable.Drawable

data class ContactChatAction(
  val label: String,
  val packageName: String,
  val dataId: Long? = null,
  val phoneNumber: String? = null,
  /** The messaging app's own launcher icon. Null for the actions Android itself provides. */
  val icon: Drawable? = null,
  /**
   * Set instead of [icon] for call, SMS and email, which are capabilities of the phone rather than
   * of any installed app and so have no icon to borrow.
   *
   * Named rather than supplied as a drawable because the two are drawn differently: an app icon is
   * its own artwork and is drawn as-is, while these are glyphs and have to take the current content
   * colour to stay legible on either theme. The framework's `ic_menu_call` and friends did neither
   * — hardcoded near-white line art from the Gingerbread menu set, invisibly thin on a light
   * background and mismatched against every Material icon beside them.
   */
  val glyph: ContactActionGlyph? = null,
)

enum class ContactActionGlyph {
  CALL,
  MESSAGE,
  EMAIL,
}
