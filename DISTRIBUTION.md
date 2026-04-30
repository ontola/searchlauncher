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

### 1. Prerequisites
F-Droid builds from source. Ensure your repository is public and the release commit is tagged.

The metadata in `fdroid/com.searchlauncher.app.yml` currently targets:

- Version: `0.0.3`
- Version code: `180`
- Commit/tag: `v0.0.3`

### 2. Submission Process
1. Fork the [fdroiddata repository](https://gitlab.com/fdroid/fdroiddata).
2. **Run the preparation script**:
   ```bash
   ./prepare_fdroid.sh
   ```
   This will create a `fdroid_submission/` directory with the correct structure.

3. **Copy files to your fork**:
   Run the following command (replace `/path/to/fdroiddata` with the actual path to your fork):
   ```bash
   cp -R fdroid_submission/metadata/* /path/to/fdroiddata/metadata/
   ```
   *Note: This will merge your new files into the existing folder without deleting other files.*

4. **Validate in fdroiddata**:
   ```bash
   cd /path/to/fdroiddata
   fdroid readmeta
   fdroid rewritemeta com.searchlauncher.app
   fdroid lint com.searchlauncher.app
   fdroid build com.searchlauncher.app
   ```

5. Submit a Merge Request to the `fdroiddata` repository.

For a new release, update the build block in `fdroid/com.searchlauncher.app.yml`, add a matching Fastlane changelog under `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`, tag the release commit, and push the tag before submitting the fdroiddata update.
