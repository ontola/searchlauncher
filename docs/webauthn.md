# Browser passkeys / WebAuthn

The regular WebView build's browser tabs (including private tabs) and link previews opt into Android WebView's
native `WEB_AUTHENTICATION_SUPPORT_FOR_BROWSER` before loading the page. Chromium
handles secure-context, relying-party ID and origin validation and credential UI;
SearchLauncher does not inject a JavaScript bridge or handle credential responses.

Requirements:
- Android 14 or newer and the manifest's `CREDENTIAL_MANAGER_SET_ORIGIN` permission.
- A system WebView exposing AndroidX's `WEB_AUTHENTICATION` feature.
- A credential provider that accepts SearchLauncher as a privileged browser.

Older or unsupported devices retain WebView's default unsupported behavior. Use
another sign-in method or open the site in an external browser. There is no
`FOR_APP` fallback: Digital Asset Links for our own domain cannot authorize logins
for arbitrary websites. Private browsing does not isolate the system credential
provider; a passkey explicitly saved by the user persists in that provider.

## Google Password Manager approval (outstanding)

Follow https://developer.android.com/identity/sign-in/privileged-apps and its
request form. Request privileged browser access for `com.searchlauncher.app` using
the SHA-256 signing certificate of the distributed app. Play App Signing and
GitHub builds may have different certificates; verify both in the actual release
channels. The debug package `com.searchlauncher.app.debug` and debug certificate
are distinct. Never assume debug success proves release approval, or vice versa.

Suggested description: SearchLauncher is an Android home-screen launcher and web
browser. Users open arbitrary HTTPS websites in browser tabs and need to create
and use passkeys for those websites. The browser uses Android WebView's native
WebAuthn browser mode, preserving the current website origin and relying-party
validation, with credential consent handled by the system provider.

## Acceptance checks on a device

1. Use an approved package/certificate and current WebView with a test HTTPS site.
2. Create a test passkey, then authenticate with it; confirm the correct domain
   appears in provider UI and the server accepts the assertion.
3. Cancel the provider dialog and verify the page remains usable.
4. Repeat in a second tab, a link preview, and private browsing.
5. Test a mismatched relying-party ID and insecure HTTP origin: neither may gain
   access to the HTTPS site's credentials.
6. Check a device with unsupported WebView and Android 13: browsing still works.

Conditional mediation/autofill support depends on WebView; do not promise Chrome
feature parity. Unit tests verify configuration guards, not real authentication.


## Experimental Gecko build

Build 14 includes `CREDENTIAL_MANAGER_QUERY_CANDIDATE_CREDENTIALS`, required by Gecko's Android
14+ credential-query implementation. Without it, starting a passkey request could crash the app.
Native Gecko WebAuthn handles origin and relying-party checks; no JavaScript credential bridge
is injected.

### KeePassDX setup

1. Install build 14 or newer and KeePassDX on Android 14 or newer.
2. Enable KeePassDX as a password/passkey provider in Android settings. If its passkey settings
   are disabled, select KeePassDX as the preferred autofill service during setup.
3. In KeePassDX, open **Settings → Form filling → Passkeys settings → Privileged apps**.
4. Select `com.searchlauncher.app.gecko` for the experimental app and confirm. This explicitly
   trusts that installed browser and its signing certificate to supply website origins. Only
   select the browser you actually installed and trust; the regular package is a separate entry.
5. Register a new test passkey on [WebAuthn.io](https://webauthn.io), then choose **Authenticate**.
   Confirm the relying party is `webauthn.io` and the website reports a successful login.

A passkey created before the browser was trusted may fail authentication with a signature
error. Prefer a fresh test registration after configuring trust. KeePassDX also documents repair
of existing entries in its [passkey guide](https://github.com/Kunzisoft/KeePassDX/wiki/Passkeys#bad-signature).
Do not delete a real account's only sign-in method to run this test.

Verified 2026-10-03: experimental build 14 (`16620ff`), Android 15 Phone_A35 emulator, KeePassDX
4.5.5 Free. WebAuthn.io accepted registration and two separate sign-ins, including unlocking the
vault again for the second sign-in. The same build failed signature verification before browser
trust was configured. This does not establish physical Xiaomi or every-site compatibility.

### Proton Pass and browser approval

Proton validates the calling browser's origin against a bundled package/signing-certificate
allowlist. On 2026-10-03 neither `com.searchlauncher.app` nor `com.searchlauncher.app.gecko` was
in Proton's list or Google's live list. Installing a different engine does not change this
provider-side decision, and successful password autofill does not establish passkey approval.

Sources:
- [Proton browser origin verifier](https://github.com/protonpass/android-pass/blob/main/pass/features/credentials/src/main/kotlin/proton/android/pass/features/credentials/shared/passkeys/search/PasskeyOriginVerifier.kt)
- [Proton bundled allowlist](https://github.com/protonpass/android-pass/blob/main/pass/browser-allowlist/impl/src/main/res/raw/passkey_privileged_browsers_allowlist.json)
- [Google published browser list](https://www.gstatic.com/gpm-passkeys-privileged-apps/apps.json)

Provider submissions must use the actual distributed package and certificate. Experimental,
GitHub release and Play signing identities must be checked separately. Do not submit a local
debug certificate as the production identity or bypass origin validation to make a test pass.

### Conditional passkey autofill

Gecko Android currently reports conditional mediation unavailable. Explicit registration and
sign-in can work even when a site refuses to show its passkey controls after checking that
optional capability. The Google passkeys demo checks conditional support as well as platform
authenticator support; its “device does not support passkeys” message is not a comprehensive
WebAuthn compatibility test. Do not spoof this capability as enabled.
