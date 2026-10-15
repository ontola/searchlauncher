# Distribution Guide

## Google Play Store

The public listing is
[https://play.google.com/store/apps/details?id=com.searchlauncher.app](https://play.google.com/store/apps/details?id=com.searchlauncher.app).

Play wants a bundle rather than an APK, and its own set of listing assets. Everything
here except the console forms is already in the repo.

### The bundle

Tagging a release uploads it. The `play` job in
[`.github/workflows/android.yml`](.github/workflows/android.yml) builds the bundle and
hands it to the **production** track with `fastlane supply`, taking the release notes from
`fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`. Without the
`PLAY_SERVICE_ACCOUNT_JSON` secret the job skips rather than fails, so a fork still gets a
green build.

The job never uploads the listing text or the images. The screenshots under
`fastlane/metadata` are the plain captures F-Droid shows, and pushing those to Play would
quietly replace the composed listing with them. Listing changes stay manual, which is
fine: they change once a release, not once a build.

To build one by hand:

```bash
./gradlew :app:bundleRelease     # app/build/outputs/bundle/release/app-release.aab
```

It is signed with `app/upload.jks`, alias `upload`, which is gitignored.
Play re-signs with its own app signing key, so this one only proves the upload is yours:
losing it means asking Google to reset the upload key, not losing the app. The path and
passwords can be overridden with `SIGNING_KEY_STORE_PATH`, `SIGNING_STORE_PASSWORD`,
`SIGNING_KEY_ALIAS` and `SIGNING_KEY_PASSWORD`.

The bundle Play receives is not the APK F-Droid builds, and does not need to be
reproducible — F-Droid verifies the legacy `app-release.apk`, which remains a separate artifact.

### GitHub and Play share the installed app

After the Play upload, CI downloads Google's **signed universal APK** using
[`generatedapks.list`](https://developers.google.com/android-publisher/api-ref/rest/v3/generatedapks/list)
and [`generatedapks.download`](https://developers.google.com/android-publisher/api-ref/rest/v3/generatedapks/download).
`scripts/download-play-apk.py` checks its APK signature against the certificate returned by the
authenticated Play API and checks the package ID and version code before publishing it as
**`app-play.apk`** on GitHub. Both channels then use the same installed-app identity. Android's
normal version-code rules still apply: an older Play rollout cannot downgrade a newer sideload.

The GitHub release waits for this verified artifact. Missing credentials, API errors, ambiguous
signing keys, or a missing universal APK fail the job; there is no fallback to the incompatible
upload-key APK. `PLAY_APP_SIGNING_SHA256` can pin a particular Play signing certificate; it is
required if Google returns universal APKs for multiple keys. `app-play.json` records the package,
version, certificate and APK SHA-256. No token, service-account key or download credential is saved.

`app-release.apk` is retained for existing legacy GitHub/F-Droid users and reproducible builds.
It keeps its pinned signing key and existing F-Droid binary URL. Release notes and asset labels
identify it as incompatible with Play. New installs should choose `app-play.apk`; migrating an old
legacy install still requires a one-time export/uninstall/import across all Android profiles.
Do not overwrite old released APKs with differently signed files.

The **Verify Play APK compatibility** workflow tests the API against already uploaded version
304 without submitting a build or changing any Play track. It runs for relevant internal PRs and
can be dispatched with another existing version; `PLAY_APK_SMOKE_VERSION_CODE` changes the PR fixture.

### What to upload where

| Console field | File |
| --- | --- |
| App name (max 30) | `fastlane/metadata/android/en-US/title.txt` |
| Short description (max 80) | `fastlane/metadata/android/en-US/short_description.txt` |
| Full description (max 4000) | `fastlane/metadata/android/en-US/full_description.txt` |

Those three are generated. Edit [`marketing/store-copy.md`](marketing/store-copy.md) and
run `marketing/copy.py`, so the text Play shows and the text F-Droid shows stay the same.

| Image | File |
| --- | --- |
| App icon, 512x512 | `fastlane/metadata/android/en-US/images/icon.png` |
| Feature graphic, 1024x500 | `marketing/out/feature/01_widgets.png` |
| Phone screenshots | `marketing/out/phone/*.png` (6, 1080x1920) |
| 7-inch tablet screenshots | `marketing/out/tablet7/*.png` (6, 1920x1080) |
| 10-inch tablet screenshots | `marketing/out/tablet10/*.png` (6, 2560x1440) |
| Release notes | `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt` |

One changelog file serves both stores, so write it about the app rather than about a
listing page, and keep it under Play's 500-character limit. `marketing/copy.py` enforces
that for the version being released, and CI runs it.

Upload the composed images from `marketing/out/`, not the plain captures — the plain ones
are what F-Droid shows. See [marketing/README.md](marketing/README.md).

Play accepts at most 8 screenshots per form factor and needs at least 2. Both tablet
slots are required once the app is not phone-only, and both want a **16:9 or 9:16**
canvas — a 16:10 one is refused, which is why the tablet canvases are 1920x1080 and
2560x1440 rather than matching the 16:10 shape of the captures inside them. The 10-inch
slot also has a higher floor: every side must be at least 1080px.

`marketing/check.py` checks the generated images against all of this, so run it before an
upload rather than finding out from the console.

### Signing keys

Three keys are involved, and only one of them can ever change.

| Key | SHA-256 | What it does |
| --- | --- | --- |
| Release key | `553f6280…` | Signs legacy `app-release.apk`, F-Droid verification and CI previews |
| Play app signing key | `c016abdc…` | Signs Play installs and GitHub `app-play.apk` |
| Play upload key | — | Authenticates uploads. Never reaches a device |

**The release key can never change.** F-Droid pins it in `AllowedAPKSigningKeys`, and existing legacy
GitHub/F-Droid installations are signed with it. Swapping it would break reproducible-build verification
and make the app un-updatable for existing users, because Android refuses an update signed
by a different key. It lives in the `SIGNING_KEY_STORE_BASE64` secret.

**Where the release key lives.** In the maintainer's password manager, keystore and
passwords together. Until August 2026 it existed only as the `SIGNING_KEY_STORE_BASE64`,
`SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS` and `SIGNING_KEY_PASSWORD` secrets, which
GitHub will not let anyone read back — so the one irreplaceable thing in this project had
no recoverable copy, and CI could sign releases when nobody else could. Recovering it
needed a temporary `workflow_dispatch` job that encrypted the secrets to a throwaway
certificate and uploaded only the ciphertext, since the repository is public. That job is
in the history at `4d0a615` if it is ever needed again; it was deleted straight afterwards,
because a job that decants the signing key on demand is worse than the problem it solved.

**Play and legacy F-Droid use different signatures.** Google generated the Play signing key
at enrolment; it stays with Google. We do not need to export that private key or rotate either key
to make GitHub compatible: Google provides a signed universal APK for redistribution. The
legacy APK remains available for users who already depend on the F-Droid signing identity.

**The upload key is the release key.** Play originally registered a different one, because
the first bundle it ever received was built locally with `app/upload.jks` rather than by
CI. That was corrected with *App integrity > Request upload key reset*, using the
certificate from a published APK:

```bash
apksigner verify --print-certs-pem app-release.apk \
  | awk '/BEGIN CERTIFICATE/,/END CERTIFICATE/' > upload-key.pem
```

The APK is signed with v2/v3 schemes only, so there is no `META-INF/*.RSA` to read and
`keytool -printcert -jarfile` prints nothing. The certificate is in the APK Signing Block,
which is what `--print-certs-pem` reads.

If the upload ever fails with *"signed with the wrong key"*, compare the two fingerprints
in the error against the table above before changing anything. Making the release key
match Play would be the wrong repair.

### Installation conflicts and recovery

Android matches apps by **application ID and signing certificate**, not by the visible name.
These identities are intentional:

| Build | Name | Application ID | Signing identity |
| --- | --- | --- | --- |
| Google Play / GitHub `app-play.apk` | SearchLauncher | `com.searchlauncher.app` | Google Play app signing key |
| Legacy GitHub/F-Droid `app-release.apk` | SearchLauncher | `com.searchlauncher.app` | Pinned distribution/upload key |
| CI preview | SearchLauncher (Debug Preview) | `com.searchlauncher.app.debug.preview` | Pinned distribution/upload key |
| Local/fork preview | SearchLauncher (Debug Preview) | `com.searchlauncher.app.debug.preview.local` | Local debug key |
| Local debug | SearchLauncher (Debug) | `com.searchlauncher.app.debug` | Local debug key |
| Optimized development build | SearchLauncher (Debug Gecko) | `com.searchlauncher.app.gecko` | Local debug key |

Use `installGecko` for device testing: it runs the optimized production engine with a separate
identity and keeps updating existing Gecko test installs. `installRelease` is blocked by default;
`-PallowNonPlayReleaseInstall=true` explicitly allows distribution testing on an isolated device.
PR/main CI artifacts use the separate preview identity; only version tags produce the stable
release APK. Signed release/preview builds reject certificates other than the pinned distribution key. Unsigned
release builds remain available for F-Droid's reproducible-build process.

**“Another user has already installed an incompatible version”** means a conflicting copy
remains in another Android user/profile. Removing the app only in the personal profile does not
remove a work-profile/Second Space copy. Keeping app data during uninstall can also retain the
old package's signing identity.

To move from a legacy GitHub/F-Droid copy to Play or `app-play.apk`:

1. Export any SearchLauncher data you want to keep in **each** profile containing it.
2. Check Android's users, work profile, Xiaomi Second Space, and Dual Apps profiles. Remove the
   regular SearchLauncher copy there too, or use **Uninstall for all users** from App info if
   available. Leave Debug/Debug Gecko installs alone; their IDs do not conflict with Play.
3. Install from Google Play, then import the exported launcher data. Website sessions may need
   signing in again.

With USB debugging connected, first inspect without changing anything:

```bash
adb shell pm list users
adb shell dumpsys package com.searchlauncher.app
# Repeat for each user ID shown above (0 is normally the personal profile):
adb shell pm list packages --user 0 com.searchlauncher.app
```

The package dump's per-user `installed` state identifies the remaining copy. Do not uninstall a
whole work profile or erase data merely to remove this app. Only after the owner has exported
needed data and confirmed removal from **all** profiles, `adb uninstall com.searchlauncher.app`
removes the conflicting package globally. Do not use `-k`, which preserves data/signing state.

### Developer verification

Google's developer verification asks for an APK signed with the private key behind a
fingerprint it shows in the console. That fingerprint is `553f6280…`, so it is the
**release key** it wants, not the Play app signing key Google holds itself. The release
key is the one in `SIGNING_KEY_STORE_BASE64`, so CI can sign the verification APK and
nothing has to be built by hand.

`app/src/main/assets/adi-registration.properties` carries the account identifier the
console hands out. It has to sit in `assets` under that exact name, and the APK is only
proof of key ownership: any release APK built from this tree will do, and it does not
need to be the one that ships.

For production developer verification, use `app-release.apk` from a version-tag release. PR/main
CI artifacts are intentionally separate Debug Preview apps and must not be uploaded as proof of
the production package. The tagged release APK is signed with the release key.

### Connecting CI to Play, once

The upload needs a service account, which is four things in two consoles:

1. **Google Cloud** — in any project, enable the *Google Play Android Developer API*, create
   a service account under *IAM & Admin*, and download a JSON key for it. It needs no
   project roles; its access comes from Play, not from Cloud. The key downloads once and
   cannot be downloaded again. Linking the project to your Play account used to be
   required and no longer is.
2. **Play Console** — *Users and permissions* > *Invite new users*, paste the service
   account's email, and give it access to this app with *Release to production, exclude devices, and use Play App Signing*.
   Permissions can take a few minutes to apply.
3. **GitHub** — add the whole JSON file as a repository secret named
   `PLAY_SERVICE_ACCOUNT_JSON`.
4. **Upload one bundle by hand first.** Play does not know the package until a bundle has
   been uploaded through the console, and the API answers `404 Package not found` until
   then, however correct the credentials are.

Check the credentials without tagging anything:

```bash
python3 scripts/check-play-key.py ~/Downloads/your-key.json
```

It tells the three failure modes apart, which the error messages alone do not: a key the
API rejects means the *Google Play Android Developer API* is not enabled; `404 Package not
found` means no bundle has been uploaded by hand yet; `401` or `403` means the service
account has not been invited in *Users and permissions*, or the invitation has not
propagated yet.

After that, `git tag v0.0.23 && git push origin v0.0.23` is the whole release: GitHub gets
the APK, and Play's production track gets the bundle and the changes are submitted for review.

Google may review an update before it becomes public. Managed publishing, if enabled
in Play Console, can also hold an approved update. CI deliberately fails if Play requires
manual review submission instead of silently leaving the changes unsubmitted.

For a version already uploaded to internal testing, run **Promote Play release** in
GitHub Actions with its Android version code. This promotes that existing bundle to
production without rebuilding or uploading a duplicate version. It preserves the store
listing and its screenshots. Internal testing does not count towards the 14-day
closed-testing requirement below.

### Console forms

**Privacy policy** — <https://searchlauncher.eu/privacy.html>, from
[`website/privacy.html`](website/privacy.html).

**Data safety.** What the app actually does, which is what the form should say:

- Nothing is collected by default. Both network features are off until the user turns
  them on in onboarding or Settings.
- *Search suggestions*, when enabled, send what is typed to whichever provider the
  shortcut names — DuckDuckGo, Bing, Google, Wikipedia and YouTube endpoints are in the
  source. This is "shared, not collected": it goes to a third party, not to us.
- *Crash reporting*, when enabled, sends stack traces to Sentry (sentry.io). Declare it as crash
  logs, optional, not used for tracking. Reports include device model, Android version,
  app version, build identifier, and WebView version.
- *Send feedback* (Settings) is a separate, user-initiated report to the same Sentry
  project. It includes the message, those same device details, and an email only if the
  user types one. It does not enable ongoing crash reporting.
- Contacts and calendar are read on the device to answer a query and are never sent
  anywhere. They are permissions, not collected data. Custom wallpapers are chosen
  with the system photo picker; the app does not request storage or photos access.
- The browser downloads a blocklist from the StevenBlack hosts project. That is an
  inbound fetch with no user data in it.

**Content rating** — a launcher with a browser in it. Answer the questionnaire honestly
about user-generated content and web browsing; the browser makes the rating higher than
the launcher alone would.

**Target API** — Play requires 35 or later; this app targets 36.

### Production

The listing is live. A personal developer account created after November 2023 has to
run a closed test with at least 12 testers who stay opted in for 14 days before
production opens up; organisation accounts are exempt. That clock is already done for
this app.

## F-Droid

F-Droid builds from source, so a release is a public tag plus one YAML file in
[fdroiddata](https://gitlab.com/fdroid/fdroiddata).

### What lives where

Only the build recipe goes into fdroiddata. Everything a user reads — the name, summary,
description, changelogs and screenshots — is pulled from `fastlane/metadata/android/en-US/` in
this repository at build time. Sending those to fdroiddata as well is what got the [first
attempt](https://gitlab.com/fdroid/fdroiddata/-/merge_requests/39312) rejected.

| What | Where |
| --- | --- |
| Build recipe (`Builds`, categories, anti-features) | `fdroid/com.searchlauncher.app.yml` |
| Name, summary, description | `fastlane/metadata/android/en-US/{title,short_description,full_description}.txt` |
| Changelog per release | `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt` |
| Screenshots | `fastlane/metadata/android/en-US/images/phoneScreenshots/` |

The summary has to stay under 80 characters and must not end in punctuation, or `fdroid lint`
fails.

### Cutting a release

1. Bump `versionCode` and `versionName` in `app/build.gradle.kts`. They are plain literals on
   purpose: F-Droid greps them out of the file to notice new tags, so a computed value means
   `fdroid checkupdates` cannot tell that a release happened.
2. Add `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`, under 500 characters.
3. Commit, then tag and push: `git tag v<versionName> && git push origin v<versionName>`.

### First submission

Only the first release needs a hand-written merge request. After that `AutoUpdateMode: Version`
picks up new tags on its own.

1. Fork and clone [fdroiddata](https://gitlab.com/fdroid/fdroiddata).
2. Point the build block in `fdroid/com.searchlauncher.app.yml` at the release, then run
   `./prepare_fdroid.sh`. It resolves the tag to a full commit hash (F-Droid rejects tag names)
   and refuses to run if the tag is missing or disagrees with the metadata.
3. `cp fdroid_submission/metadata/com.searchlauncher.app.yml /path/to/fdroiddata/metadata/`
4. Reproduce the fdroiddata pipeline locally — these are the jobs that have to be green:
   ```bash
   cd /path/to/fdroiddata
   fdroid readmeta
   fdroid rewritemeta com.searchlauncher.app && git diff --exit-code  # formatting/field order
   fdroid lint com.searchlauncher.app
   fdroid checkupdates --allow-dirty com.searchlauncher.app           # tag parsing
   fdroid build com.searchlauncher.app:<versionCode>
   ```
5. Open the merge request with the **App Inclusion** template and tick its boxes.
