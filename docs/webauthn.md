# Browser passkeys / WebAuthn

Browser tabs (including private tabs) and link previews opt into Android WebView's
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
