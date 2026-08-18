package com.searchlauncher.app.ui.browser

import android.Manifest
import android.os.Build
import android.webkit.PermissionRequest

/**
 * Device features a page can ask this browser for. Each one is remembered per origin as ask / allow
 * / block. [OTHER] is the bucket for resources WebView adds later (or names we do not model yet).
 */
internal enum class BrowserDeviceAccess(val prefKey: String, val settingsLabel: String) {
  MICROPHONE("microphone", "Microphone"),
  CAMERA("camera", "Camera"),
  LOCATION("location", "Location"),
  MIDI("midi", "MIDI"),
  BLUETOOTH("bluetooth", "Bluetooth"),
  OTHER("other", "Other devices");

  /** Phrase used in "Use …?" / "wants to use …". */
  val noun: String
    get() =
      when (this) {
        MICROPHONE -> "microphone"
        CAMERA -> "camera"
        LOCATION -> "location"
        MIDI -> "MIDI devices"
        BLUETOOTH -> "Bluetooth"
        OTHER -> "additional device access"
      }

  val canUsePhrase: String
    get() =
      when (this) {
        MICROPHONE -> "the microphone"
        CAMERA -> "the camera"
        LOCATION -> "your location"
        MIDI -> "MIDI devices"
        BLUETOOTH -> "Bluetooth"
        OTHER -> "other device features"
      }
}

internal fun BrowserSiteSettings.allowed(access: BrowserDeviceAccess): Boolean? =
  deviceAccess[access]

internal fun BrowserSiteSettings.withAllowed(
  access: BrowserDeviceAccess,
  allowed: Boolean?,
): BrowserSiteSettings =
  copy(
    deviceAccess =
      if (allowed == null) deviceAccess - access else deviceAccess + (access to allowed)
  )

internal fun BrowserSiteSettings.withAllowed(
  accesses: Collection<BrowserDeviceAccess>,
  allowed: Boolean,
): BrowserSiteSettings = copy(deviceAccess = deviceAccess + accesses.associateWith { allowed })

/**
 * Maps a WebView resource string to the site setting that governs it. `null` means grant without
 * asking — today that is only the protected-media identifier used for DRM.
 */
internal fun deviceAccessForResource(resource: String): BrowserDeviceAccess? =
  when (resource) {
    PermissionRequest.RESOURCE_AUDIO_CAPTURE -> BrowserDeviceAccess.MICROPHONE
    PermissionRequest.RESOURCE_VIDEO_CAPTURE -> BrowserDeviceAccess.CAMERA
    PermissionRequest.RESOURCE_MIDI_SYSEX -> BrowserDeviceAccess.MIDI
    PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID -> null
    else ->
      when {
        resource.contains("BLUETOOTH", ignoreCase = true) -> BrowserDeviceAccess.BLUETOOTH
        else -> BrowserDeviceAccess.OTHER
      }
  }

internal data class WebResourceDecision(
  /** Types the user has not decided yet; empty means we can settle immediately. */
  val ask: List<BrowserDeviceAccess>,
  /** Resources already covered by Allow, plus DRM identifiers that never prompt. */
  val grantNow: List<String>,
)

internal fun decideWebResources(
  requested: Array<out String>,
  allowed: (BrowserDeviceAccess) -> Boolean?,
): WebResourceDecision {
  val grantNow = mutableListOf<String>()
  val ask = linkedSetOf<BrowserDeviceAccess>()
  for (resource in requested) {
    val access = deviceAccessForResource(resource)
    if (access == null) {
      grantNow += resource
      continue
    }
    when (allowed(access)) {
      true -> grantNow += resource
      false -> Unit
      null -> ask += access
    }
  }
  return WebResourceDecision(ask = ask.toList(), grantNow = grantNow)
}

internal fun grantableWebResources(
  requested: Array<out String>,
  allowed: (BrowserDeviceAccess) -> Boolean?,
): Array<String> =
  requested
    .filter { resource ->
      val access = deviceAccessForResource(resource)
      access == null || allowed(access) == true
    }
    .toTypedArray()

internal fun androidPermissionsFor(
  accesses: Collection<BrowserDeviceAccess>,
  sdk: Int = Build.VERSION.SDK_INT,
): Array<String> {
  val permissions = linkedSetOf<String>()
  for (access in accesses) {
    when (access) {
      BrowserDeviceAccess.MICROPHONE -> permissions += Manifest.permission.RECORD_AUDIO
      BrowserDeviceAccess.CAMERA -> permissions += Manifest.permission.CAMERA
      BrowserDeviceAccess.LOCATION -> {
        permissions += Manifest.permission.ACCESS_FINE_LOCATION
        permissions += Manifest.permission.ACCESS_COARSE_LOCATION
      }
      BrowserDeviceAccess.BLUETOOTH ->
        if (sdk >= Build.VERSION_CODES.S) {
          permissions += Manifest.permission.BLUETOOTH_SCAN
          permissions += Manifest.permission.BLUETOOTH_CONNECT
        } else {
          permissions += Manifest.permission.BLUETOOTH
          permissions += Manifest.permission.BLUETOOTH_ADMIN
          // BLE scans on Android 11 and below are location scans.
          permissions += Manifest.permission.ACCESS_FINE_LOCATION
        }
      BrowserDeviceAccess.MIDI,
      BrowserDeviceAccess.OTHER -> Unit
    }
  }
  return permissions.toTypedArray()
}

internal fun androidPermissionsForResources(
  resources: Array<out String>,
  sdk: Int = Build.VERSION.SDK_INT,
): Array<String> =
  androidPermissionsFor(resources.mapNotNull(::deviceAccessForResource).toSet(), sdk)

internal fun englishList(items: List<String>): String =
  when (items.size) {
    0 -> ""
    1 -> items[0]
    2 -> "${items[0]} and ${items[1]}"
    else -> items.dropLast(1).joinToString(", ") + ", and " + items.last()
  }

internal fun deviceAccessPromptTitle(types: Collection<BrowserDeviceAccess>): String =
  "Use ${englishList(types.map { it.noun })}?"

internal fun deviceAccessPromptText(
  siteLabel: String,
  types: Collection<BrowserDeviceAccess>,
): String = "$siteLabel wants to use your ${englishList(types.map { it.noun })}."
