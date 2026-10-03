# Browser API audit — Gecko experiment

Checked 3 October 2026 against experimental build 10, GeckoView 157 on Android 15 ARM64.
The build 11 changes do not change these API integrations. This describes the Gecko experiment,
not the regular Android WebView build.

A secure localhost page checked the actual installed engine. “Exposed” below means the API exists;
it is not a claim that every permission, operating-system integration, or third-party service works.

| API from the article | Current status |
| --- | --- |
| Web Share | Exposed, but the app has no `onSharePrompt` handler. Website `navigator.share()` still needs Android sharesheet integration. Browser-menu Share is separate and implemented. |
| Vibration | Not exposed. |
| Broadcast Channel | Verified: two channels exchanged a message. |
| Screen Wake Lock | Verified: acquired and released a screen lock. |
| Page Visibility | Exposed. Hidden tabs are marked inactive. |
| Clipboard | Read/write API exposed; Gecko's selection delegate supplies clipboard permission UI. Clipboard operations not tested in this audit. |
| Web Speech | Speech recognition is absent. Speech synthesis is exposed; spoken output not verified. |
| Battery Status | Not exposed. |
| Network Information | Not exposed. |
| Payment Request | Not exposed. |
| Resize Observer | Exposed. |
| Credential Management | `navigator.credentials` and WebAuthn `PublicKeyCredential` are exposed; `PasswordCredential` is absent. Provider compatibility is a separate issue. |
| Screen Orientation | Orientation and lock methods exposed; actual locking not verified. |
| Idle Detection | Not exposed. |
| File System Access pickers | `showOpenFilePicker`, `showSaveFilePicker`, and `showDirectoryPicker` are absent. See filesystem details below. |
| EyeDropper | Not exposed. |
| WebOTP | Not exposed. |
| Contact Picker | Not exposed. Native launcher contact search does not expose contacts to websites. |
| Barcode Detection | Not exposed. A website may implement scanning using camera access and a JavaScript library. |
| Geolocation | Exposed; site permission and Android permission handling implemented. Not location-tested in this audit. |
| Notifications | Local website/service-worker notifications are implemented and covered by an emulator test. Remote Web Push delivery is not configured, even though `PushManager` exists. |

## Filesystem

- Ordinary website file uploads use Android's document picker (single/multiple files). Folder
  upload requests are currently dismissed.
- Downloads and JavaScript exports are implemented.
- Origin Private File System (OPFS) works: creating, writing, reading, and deleting a file were
  verified. This is private website storage, not access to arbitrary user folders.
- The article's interactive File System Access picker API is unavailable in this Gecko build.

## Bluetooth and other devices

`navigator.bluetooth`, `navigator.usb`, `navigator.serial`, and `navigator.hid` are all absent.
Adding an Android permission would not implement these web APIs. Supporting them would require
engine support or a substantial, origin-aware native bridge with device selection and permission UI.

## Priorities

The actionable host-integration gaps are website sharing and remote Web Push. Continue testing
real passkey providers separately. Hardware APIs and unrestricted filesystem pickers are larger
engine/platform compatibility decisions.

## References

- [The 21-API article](https://dev.to/lingodotdev/21-native-browser-apis-you-might-not-have-used-before-1nbp)
- [Mozilla: File System API](https://developer.mozilla.org/en-US/docs/Web/API/File_System_API)
- [Mozilla: Web Bluetooth](https://developer.mozilla.org/en-US/docs/Web/API/Web_Bluetooth_API)
- [GeckoView host delegates](https://firefox-source-docs.mozilla.org/mobile/android/geckoview/contributor/geckoview-architecture.html)
- [GeckoView prompt callbacks](https://mozilla.github.io/geckoview/javadoc/mozilla-central/org/mozilla/geckoview/GeckoSession.PromptDelegate.html)
