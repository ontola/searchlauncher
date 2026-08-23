package com.searchlauncher.app.ui.browser

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.webkit.PermissionRequest
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BrowserDeviceAccessTest {
  @Test
  fun mapsKnownWebViewResources() {
    assertEquals(
      BrowserDeviceAccess.MICROPHONE,
      deviceAccessForResource(PermissionRequest.RESOURCE_AUDIO_CAPTURE),
    )
    assertEquals(
      BrowserDeviceAccess.CAMERA,
      deviceAccessForResource(PermissionRequest.RESOURCE_VIDEO_CAPTURE),
    )
    assertEquals(
      BrowserDeviceAccess.MIDI,
      deviceAccessForResource(PermissionRequest.RESOURCE_MIDI_SYSEX),
    )
    assertNull(deviceAccessForResource(PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID))
    assertEquals(
      BrowserDeviceAccess.BLUETOOTH,
      deviceAccessForResource("android.webkit.resource.BLUETOOTH"),
    )
    assertEquals(BrowserDeviceAccess.OTHER, deviceAccessForResource("android.webkit.resource.USB"))
  }

  @Test
  fun drmIsGrantedWithoutAskingAndBlockedTypesAreDropped() {
    val allowed: (BrowserDeviceAccess) -> Boolean? = { access ->
      when (access) {
        BrowserDeviceAccess.MICROPHONE -> true
        BrowserDeviceAccess.CAMERA -> false
        else -> null
      }
    }
    val decision =
      decideWebResources(
        arrayOf(
          PermissionRequest.RESOURCE_AUDIO_CAPTURE,
          PermissionRequest.RESOURCE_VIDEO_CAPTURE,
          PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID,
        ),
        allowed,
      )
    assertEquals(emptyList<BrowserDeviceAccess>(), decision.ask)
    assertEquals(
      listOf(
        PermissionRequest.RESOURCE_AUDIO_CAPTURE,
        PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID,
      ),
      decision.grantNow,
    )
  }

  @Test
  fun asksForTypesTheOriginHasNotDecided() {
    val decision =
      decideWebResources(
        arrayOf(PermissionRequest.RESOURCE_AUDIO_CAPTURE, PermissionRequest.RESOURCE_VIDEO_CAPTURE)
      ) {
        null
      }
    assertEquals(listOf(BrowserDeviceAccess.MICROPHONE, BrowserDeviceAccess.CAMERA), decision.ask)
    assertEquals(emptyList<String>(), decision.grantNow)
  }

  @Test
  fun grantableResourcesFollowStoredAllow() {
    val settings =
      BrowserSiteSettings(
        deviceAccess =
          mapOf(BrowserDeviceAccess.MICROPHONE to true, BrowserDeviceAccess.CAMERA to false)
      )
    assertEquals(
      listOf(
        PermissionRequest.RESOURCE_AUDIO_CAPTURE,
        PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID,
      ),
      grantableWebResources(
          arrayOf(
            PermissionRequest.RESOURCE_AUDIO_CAPTURE,
            PermissionRequest.RESOURCE_VIDEO_CAPTURE,
            PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID,
          )
        ) {
          settings.allowed(it)
        }
        .toList(),
    )
  }

  @Test
  fun cameraAndMicrophoneNeedMatchingOsPermissions() {
    assertEquals(
      setOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA),
      androidPermissionsFor(listOf(BrowserDeviceAccess.MICROPHONE, BrowserDeviceAccess.CAMERA))
        .toSet(),
    )
  }

  @Test
  fun microphoneCaptureDeclaresModifyAudioSettings() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val requested =
      context.packageManager
        .getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
        .requestedPermissions
        .toSet()
    // Chromium refuses to list a recording device unless both of these are in the manifest.
    assertTrue(requested.contains(Manifest.permission.RECORD_AUDIO))
    assertTrue(requested.contains(Manifest.permission.MODIFY_AUDIO_SETTINGS))
  }

  @Test
  fun bluetoothUsesConnectScanOnModernAndroid() {
    assertEquals(
      setOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT),
      androidPermissionsFor(listOf(BrowserDeviceAccess.BLUETOOTH), sdk = Build.VERSION_CODES.S)
        .toSet(),
    )
  }

  @Test
  fun bluetoothScanOnOlderAndroidNeedsLocation() {
    val permissions =
      androidPermissionsFor(listOf(BrowserDeviceAccess.BLUETOOTH), sdk = Build.VERSION_CODES.R)
        .toSet()
    assertTrue(permissions.contains(Manifest.permission.BLUETOOTH))
    assertTrue(permissions.contains(Manifest.permission.ACCESS_FINE_LOCATION))
  }

  @Test
  fun promptCopyNamesOneTwoAndManyFeatures() {
    assertEquals("Use camera?", deviceAccessPromptTitle(listOf(BrowserDeviceAccess.CAMERA)))
    assertEquals(
      "Use camera and microphone?",
      deviceAccessPromptTitle(listOf(BrowserDeviceAccess.CAMERA, BrowserDeviceAccess.MICROPHONE)),
    )
    assertEquals(
      "example.com wants to use your camera, microphone, and location.",
      deviceAccessPromptText(
        "example.com",
        listOf(
          BrowserDeviceAccess.CAMERA,
          BrowserDeviceAccess.MICROPHONE,
          BrowserDeviceAccess.LOCATION,
        ),
      ),
    )
  }
}
