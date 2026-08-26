# Distribution Guide

## Google Play Store

Play wants a bundle rather than an APK, and its own set of listing assets. Everything
here except the console forms is already in the repo.

### The bundle

Tagging a release uploads it. The `play` job in
[`.github/workflows/android.yml`](.github/workflows/android.yml) builds the bundle and
hands it to the **internal** track with `fastlane supply`, taking the release notes from
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

It is signed with `upload.jks` at the repo root, alias `upload`, which is gitignored.
Play re-signs with its own app signing key, so this one only proves the upload is yours:
losing it means asking Google to reset the upload key, not losing the app. The path and
passwords can be overridden with `SIGNING_KEY_STORE_PATH`, `SIGNING_STORE_PASSWORD`,
`SIGNING_KEY_ALIAS` and `SIGNING_KEY_PASSWORD`.

The bundle Play receives is not the APK F-Droid builds, and does not need to be
reproducible — F-Droid verifies the GitHub release APK, which is a separate artifact.

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

Upload the composed images from `marketing/out/`, not the plain captures — the plain ones
are what F-Droid shows. See [marketing/README.md](marketing/README.md).

Play accepts at most 8 screenshots per form factor and needs at least 2. Both tablet
slots are required once the app is not phone-only, and both want a **16:9 or 9:16**
canvas — a 16:10 one is refused, which is why the tablet canvases are 1920x1080 and
2560x1440 rather than matching the 16:10 shape of the captures inside them. The 10-inch
slot also has a higher floor: every side must be at least 1080px.

`marketing/check.py` checks the generated images against all of this, so run it before an
upload rather than finding out from the console.

### Connecting CI to Play, once

The upload needs a service account, which is four things in two consoles:

1. **Google Cloud** — in any project, enable the *Google Play Android Developer API*, create
   a service account under *IAM & Admin*, and download a JSON key for it. It needs no
   project roles; its access comes from Play, not from Cloud. The key downloads once and
   cannot be downloaded again. Linking the project to your Play account used to be
   required and no longer is.
2. **Play Console** — *Users and permissions* > *Invite new users*, paste the service
   account's email, and give it access to this app with *Release apps to testing tracks*.
   Permissions can take a few minutes to apply.
3. **GitHub** — add the whole JSON file as a repository secret named
   `PLAY_SERVICE_ACCOUNT_JSON`.
4. **Upload one bundle by hand first.** Play will not accept an API upload for an app that
   has never had a manual one, and the app has to exist in the console before any of this
   works anyway.

After that, `git tag v0.0.23 && git push origin v0.0.23` is the whole release: GitHub gets
the APK, and Play's internal track gets the bundle.

To promote further than internal, change `--track` in the workflow, or promote in the
console. Note that **internal testing does not count towards the 14-day closed-testing
requirement** below.

### Console forms

**Privacy policy** — <https://searchlauncher.eu/privacy.html>, from
[`website/privacy.html`](website/privacy.html).

**Data safety.** What the app actually does, which is what the form should say:

- Nothing is collected by default. Both network features are off until the user turns
  them on in onboarding or Settings.
- *Search suggestions*, when enabled, send what is typed to whichever provider the
  shortcut names — DuckDuckGo, Bing, Google, Wikipedia and YouTube endpoints are in the
  source. This is "shared, not collected": it goes to a third party, not to us.
- *Crash reporting*, when enabled, sends stack traces to GlitchTip. Declare it as crash
  logs, optional, not used for tracking.
- Contacts, calendar and photos are read on the device to answer a query and are never
  sent anywhere. They are permissions, not collected data.
- The browser downloads a blocklist from the StevenBlack hosts project. That is an
  inbound fetch with no user data in it.

**Content rating** — a launcher with a browser in it. Answer the questionnaire honestly
about user-generated content and web browsing; the browser makes the rating higher than
the launcher alone would.

**Target API** — Play requires 35 or later; this app targets 36.

### Before the first production release

A personal developer account created after November 2023 has to run a closed test with
at least 12 testers who stay opted in for 14 days before production opens up. Organisation
accounts are exempt. Start that clock early: it gates the release, not the review.

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
