package com.searchlauncher.app.ui.browser

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.EditText
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSession.PermissionDelegate
import org.mozilla.geckoview.GeckoSession.PromptDelegate

/** Resolve each prompt exactly once, including navigation and activity teardown. */
internal class GeckoPrompts(
  private val activity: Activity,
  private val currentUrl: () -> String,
  private val chooseFile: (Intent, (Activity?, Intent?) -> Unit) -> Unit,
  private val requestPermissions: (Array<String>, (Map<String, Boolean>) -> Unit) -> Unit,
  private val privateMode: Boolean,
) : PromptDelegate {
  private val dialogs = mutableSetOf<AlertDialog>()
  private var closed = false

  private fun confirm(message: String, reply: (Boolean) -> Unit) {
    if (closed || activity.isFinishing) {
      reply(false)
      return
    }
    var settled = false
    fun finish(allowed: Boolean) {
      if (!settled) {
        settled = true
        reply(allowed)
      }
    }
    val dialog =
      AlertDialog.Builder(activity)
        .setTitle(Uri.parse(currentUrl()).host ?: "Website request")
        .setMessage(message)
        .setPositiveButton("Allow") { _, _ -> finish(true) }
        .setNegativeButton("Block") { _, _ -> finish(false) }
        .create()
    dialog.setOnDismissListener {
      dialogs.remove(dialog)
      finish(false)
    }
    dialogs += dialog
    dialog.show()
  }

  private fun prompt(
    request: PromptDelegate.BasePrompt,
    configure: (AlertDialog.Builder, (PromptDelegate.PromptResponse) -> Unit) -> Unit,
  ): GeckoResult<PromptDelegate.PromptResponse> {
    val result = GeckoResult<PromptDelegate.PromptResponse>()
    if (closed) {
      result.complete(request.dismiss())
      return result
    }
    var settled = false
    fun finish(value: PromptDelegate.PromptResponse) {
      if (!settled) {
        settled = true
        result.complete(value)
      }
    }
    val builder = AlertDialog.Builder(activity).setTitle(Uri.parse(currentUrl()).host ?: "Website")
    configure(builder) { finish(it) }
    val dialog = builder.create()
    dialog.setOnDismissListener {
      dialogs.remove(dialog)
      if (!settled && !request.isComplete) finish(request.dismiss())
    }
    dialogs += dialog
    dialog.show()
    return result
  }

  override fun onAlertPrompt(session: GeckoSession, request: PromptDelegate.AlertPrompt) =
    prompt(request) { builder, finish ->
      builder.setMessage(request.message).setPositiveButton("OK") { _, _ ->
        finish(request.dismiss())
      }
    }

  override fun onButtonPrompt(session: GeckoSession, request: PromptDelegate.ButtonPrompt) =
    prompt(request) { builder, finish ->
      builder
        .setMessage(request.message)
        .setPositiveButton("OK") { _, _ ->
          finish(request.confirm(PromptDelegate.ButtonPrompt.Type.POSITIVE))
        }
        .setNegativeButton("Cancel") { _, _ -> finish(request.dismiss()) }
    }

  override fun onTextPrompt(session: GeckoSession, request: PromptDelegate.TextPrompt) =
    prompt(request) { builder, finish ->
      val input = EditText(activity).apply { setText(request.defaultValue) }
      builder
        .setMessage(request.message)
        .setView(input)
        .setPositiveButton("OK") { _, _ -> finish(request.confirm(input.text.toString())) }
        .setNegativeButton("Cancel") { _, _ -> finish(request.dismiss()) }
    }

  override fun onAuthPrompt(session: GeckoSession, request: PromptDelegate.AuthPrompt) =
    prompt(request) { builder, finish ->
      val user =
        EditText(activity).apply {
          hint = "Username"
          setText(request.authOptions.username)
        }
      val password =
        EditText(activity).apply {
          hint = "Password"
          inputType = 129
        }
      val fields =
        LinearLayout(activity).apply {
          orientation = LinearLayout.VERTICAL
          addView(user)
          addView(password)
        }
      builder
        .setMessage(request.message)
        .setView(fields)
        .setPositiveButton("Sign in") { _, _ ->
          finish(request.confirm(user.text.toString(), password.text.toString()))
        }
        .setNegativeButton("Cancel") { _, _ -> finish(request.dismiss()) }
    }

  override fun onChoicePrompt(session: GeckoSession, request: PromptDelegate.ChoicePrompt) =
    prompt(request) { builder, finish ->
      fun flatten(
        choices: Array<PromptDelegate.ChoicePrompt.Choice>
      ): List<PromptDelegate.ChoicePrompt.Choice> =
        choices
          .flatMap { if (it.items != null) flatten(it.items!!) else listOf(it) }
          .filter { !it.disabled && !it.separator }
      val choices = flatten(request.choices)
      val labels = choices.map { it.label }.toTypedArray()
      if (request.type == PromptDelegate.ChoicePrompt.Type.MULTIPLE) {
        val selected = choices.map { it.selected }.toBooleanArray()
        builder
          .setMultiChoiceItems(labels, selected) { _, index, checked -> selected[index] = checked }
          .setPositiveButton("Done") { _, _ ->
            finish(
              request.confirm(choices.filterIndexed { index, _ -> selected[index] }.toTypedArray())
            )
          }
      } else {
        builder.setItems(labels) { _, index -> finish(request.confirm(choices[index])) }
      }
      builder.setNegativeButton("Cancel") { _, _ -> finish(request.dismiss()) }
    }

  override fun onFilePrompt(
    session: GeckoSession,
    request: PromptDelegate.FilePrompt,
  ): GeckoResult<PromptDelegate.PromptResponse> {
    val result = GeckoResult<PromptDelegate.PromptResponse>()
    if (closed || request.type == PromptDelegate.FilePrompt.Type.FOLDER) {
      result.complete(request.dismiss())
      return result
    }
    val types = request.mimeTypes?.filter { it.contains('/') }?.toTypedArray() ?: emptyArray()
    val intent =
      Intent(Intent.ACTION_OPEN_DOCUMENT)
        .addCategory(Intent.CATEGORY_OPENABLE)
        .setType(types.singleOrNull() ?: "*/*")
        .putExtra(
          Intent.EXTRA_ALLOW_MULTIPLE,
          request.type == PromptDelegate.FilePrompt.Type.MULTIPLE,
        )
    if (types.size > 1) intent.putExtra(Intent.EXTRA_MIME_TYPES, types)
    runCatching {
        chooseFile(intent) { owner, data ->
          if (!request.isComplete) {
            val uris =
              data?.clipData?.let { clip ->
                (0 until clip.itemCount).map { clip.getItemAt(it).uri }
              } ?: listOfNotNull(data?.data)
            result.complete(
              if (owner == null || uris.isEmpty()) request.dismiss()
              else request.confirm(activity, uris.toTypedArray())
            )
          }
        }
      }
      .onFailure { if (!request.isComplete) result.complete(request.dismiss()) }
    return result
  }

  override fun onPopupPrompt(
    session: GeckoSession,
    request: PromptDelegate.PopupPrompt,
  ): GeckoResult<PromptDelegate.PromptResponse> {
    val result = GeckoResult<PromptDelegate.PromptResponse>()
    confirm("Allow this site to open a popup window?") {
      if (!request.isComplete)
        result.complete(request.confirm(if (it) AllowOrDeny.ALLOW else AllowOrDeny.DENY))
    }
    return result
  }

  override fun onRepostConfirmPrompt(
    session: GeckoSession,
    request: PromptDelegate.RepostConfirmPrompt,
  ): GeckoResult<PromptDelegate.PromptResponse> {
    val result = GeckoResult<PromptDelegate.PromptResponse>()
    confirm("Send this form again?") {
      if (!request.isComplete)
        result.complete(request.confirm(if (it) AllowOrDeny.ALLOW else AllowOrDeny.DENY))
    }
    return result
  }

  val permissions =
    object : PermissionDelegate {
      override fun onAndroidPermissionsRequest(
        session: GeckoSession,
        permissions: Array<out String>?,
        callback: PermissionDelegate.Callback,
      ) {
        if (closed || permissions.isNullOrEmpty()) {
          callback.reject()
          return
        }
        val requested = permissions.map { it }.toTypedArray()
        requestPermissions(requested) { grants ->
          if (
            requested.all {
              grants[it] == true ||
                ContextCompat.checkSelfPermission(activity, it) == PackageManager.PERMISSION_GRANTED
            }
          )
            callback.grant()
          else callback.reject()
        }
      }

      override fun onContentPermissionRequest(
        session: GeckoSession,
        permission: PermissionDelegate.ContentPermission,
      ): GeckoResult<Int> {
        val result = GeckoResult<Int>()
        fun finish(allowed: Boolean) {
          result.complete(
            if (allowed) PermissionDelegate.ContentPermission.VALUE_ALLOW
            else PermissionDelegate.ContentPermission.VALUE_DENY
          )
        }
        val description =
          when (permission.permission) {
            PermissionDelegate.PERMISSION_DESKTOP_NOTIFICATION -> "show notifications"
            PermissionDelegate.PERMISSION_GEOLOCATION -> "access your location"
            PermissionDelegate.PERMISSION_PERSISTENT_STORAGE -> "keep website data on this device"
            PermissionDelegate.PERMISSION_AUTOPLAY_INAUDIBLE -> {
              finish(true)
              return result
            }
            PermissionDelegate.PERMISSION_AUTOPLAY_AUDIBLE -> {
              // Browsers block unsolicited audio; a user's play gesture can still start playback.
              finish(false)
              return result
            }
            PermissionDelegate.PERMISSION_MEDIA_KEY_SYSTEM_ACCESS -> "play protected media"
            else -> {
              finish(false)
              return result
            }
          }
        if (
          privateMode && permission.permission == PermissionDelegate.PERMISSION_DESKTOP_NOTIFICATION
        ) {
          finish(false)
          return result
        }
        confirm("Allow ${permission.uri} to $description?") { allowed ->
          if (
            allowed &&
              permission.permission == PermissionDelegate.PERMISSION_DESKTOP_NOTIFICATION &&
              Build.VERSION.SDK_INT >= 33
          ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS)) {
              finish(
                ContextCompat.checkSelfPermission(
                  activity,
                  Manifest.permission.POST_NOTIFICATIONS,
                ) == PackageManager.PERMISSION_GRANTED
              )
            }
          } else finish(allowed)
        }
        return result
      }

      override fun onMediaPermissionRequest(
        session: GeckoSession,
        uri: String,
        video: Array<out PermissionDelegate.MediaSource>?,
        audio: Array<out PermissionDelegate.MediaSource>?,
        callback: PermissionDelegate.MediaCallback,
      ) {
        val camera = video?.firstOrNull()
        val microphone = audio?.firstOrNull()
        val osAllowed =
          (camera == null ||
            ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA) ==
              PackageManager.PERMISSION_GRANTED) &&
            (microphone == null ||
              ContextCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED)
        if (!osAllowed || closed) {
          callback.reject()
          return
        }
        val access =
          listOfNotNull(
              if (camera != null) "camera" else null,
              if (microphone != null) "microphone" else null,
            )
            .joinToString(" and ")
        confirm("Allow $uri to use your $access?") {
          if (it) callback.grant(camera, microphone) else callback.reject()
        }
      }
    }

  fun close() {
    closed = true
    dialogs.toList().forEach { it.dismiss() }
    dialogs.clear()
  }
}
