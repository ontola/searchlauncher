# Gecko protected-media playback

Gecko experimental 17 prepares Android Widevine after the user allows protected media,
before resolving Gecko's `PERMISSION_MEDIA_KEY_SYSTEM_ACCESS` request. The readiness check
opens a real DRM session and creates a `MediaCrypto`, then releases both. If provisioning
is required, it uses Android's provision request and HTTPS endpoint on an IO dispatcher.
Concurrent tabs serialize setup; no request is sent before consent. Failures deny the
request with a retry message. Closing the page cancels the pending permission operation.

## Why this is necessary

On the Android 15 ARM64 emulator, a fresh install of Gecko 16 reproduced RTL's
`PLAYBACK_VIDEO_DECODING_ERROR` on:

https://www.rtl.nl/nieuws/video/video/a5214320-3ada-4f92-8c01-5ef1d5db39fa/ai-ontspoort-staat-de-stopknop-veldhoven

Both the AAC and H.264 decoders reported:

```
queuing secure buffer without mCrypto or mDescrambler!
NS_ERROR_DOM_MEDIA_FATAL_ERR
```

Gecko 157's `GeckoMediaDrmBridge` starts asynchronous provisioning in its constructor.
`Codec.configureCodec` can obtain a null `MediaCrypto` before that completes; the codec
keeps that configuration even when the keys arrive. After provisioning and reopening the
page, the same unmodified build played successfully. This is an initialization ordering
problem, not evidence that the phone lacks an H.264 decoder.

The app-level check avoids that first-use race without modifying Gecko, turning off
hardware decoding, replacing the player, or bypassing the site's DRM. It is limited to
Widevine readiness; other protected-media failures can still occur.

## Validation

A separate diagnostic application identity (`.geckomediatest`, not distributed) ensured
an unprovisioned app identity on Phone_A35. The fixed build logged first-use provisioning
and visibly played the exact RTL video without a reload. The first automation assertion
incorrectly expected MediaSession position events to tick continuously; the recording and
screenshots showed playback, and the test was corrected to pause and read a fresh position.

The live regression is opt-in because RTL and its network services are external:

```sh
./gradlew assembleGecko assembleGeckoAndroidTest -PtestBuildType=gecko
adb install -r app/build/outputs/apk/gecko/app-gecko.apk
adb install -r app/build/outputs/apk/androidTest/gecko/app-gecko-androidTest.apk
adb shell am instrument -w -e liveRtl true \
  -e class 'com.searchlauncher.app.ui.browser.GeckoBrowserDeviceTest#protectedRtlVideoPlaysAndReloads' \
  com.searchlauncher.app.gecko.test/androidx.test.runner.AndroidJUnitRunner
```

The final live regression passed: playback advanced beyond three seconds on both the
initial load and after reloading. `spotlessCheck` and all 449 unit tests also passed
(3 skipped). It checks playback advances, then reloads the page and repeats. RTL's canvas player has
no accessible play control, so its tap position is specific to the Phone_A35 layout.
Use a fresh test app identity to exercise provisioning; an already provisioned install
only exercises the ready path. No physical Xiaomi was connected for this validation.
