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

### 1. Prerequisite
F-Droid builds from source. Ensure your repository is public and the latest code is pushed.

### 2. Submission Process
1. Fork the [fdroiddata repository](https://gitlab.com/fdroid/fdroiddata).
3. **Run the preparation script**:
   ```bash
   ./prepare_fdroid.sh
   ```
   This will create a `fdroid_submission/` directory with the correct structure.

4. **Copy files to your fork**:
   Run the following command (replace `/path/to/fdroiddata` with the actual path to your fork):
   ```bash
   cp -R fdroid_submission/metadata/* /path/to/fdroiddata/metadata/
   ```
   *Note: This will merge your new files into the existing folder without deleting other files.*

5. **Tag your release** in git:
   ```bash
   git tag v0.0.1-beta
   git push origin v0.0.1-beta
   ```
6. Submit a Merge Request to the `fdroiddata` repository.
