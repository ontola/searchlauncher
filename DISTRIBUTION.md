# Distribution Guide

## Google Play Store

### 1. Build Artifact
The release bundle has been generated at:
`app/build/outputs/bundle/release/app-release.aab`

### 2. Upload
1. Go to the [Google Play Console](https://play.google.com/console).
2. Select your app (or create a new one).
3. Navigate to **Releases** > **Production** (or Testing).
4. Create a new release and upload the `app-release.aab` file.
5. Fill in the release notes and rollout.

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
