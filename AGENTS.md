# AGENTS.md

## Cursor Cloud specific instructions

SearchLauncher is a single native **Android** app (Kotlin + Jetpack Compose + Gradle). There is **no backend/server, database, or docker service** — everything runs on-device. "Running the app" means building an APK and running it on an Android emulator.

### Toolchain (already provisioned in the VM snapshot)
- **JDK 17** and the **Android SDK** (cmdline-tools, `platform-tools`, `platforms;android-36`, `build-tools;36.0.0`, `emulator`) are pre-installed.
- `~/.bashrc` exports `JAVA_HOME` (JDK 17) and `ANDROID_HOME`/`ANDROID_SDK_ROOT` (`~/Android/Sdk`) and adds their `bin`/`platform-tools`/`emulator` dirs to `PATH`. New shells pick these up automatically.
- `~/.gradle/gradle.properties` pins `org.gradle.java.home` to JDK 17, so Gradle builds on JDK 17 even if a shell's `java` resolves to the system JDK 21. Do **not** rely on the system default `java` (it is 21; the project needs 17).

### Standard commands (see `README.md`)
Run from the repo root with the Gradle wrapper:
- Lint/format check: `./gradlew spotlessCheck` (auto-fix with `./gradlew spotlessApply`). CI enforces this.
- Unit tests: `./gradlew test` (Robolectric/MockK/JUnit).
- Build debug APK: `./gradlew assembleDebug`; install to a running device/emulator: `./gradlew installDebug`.

### Running end-to-end on an emulator (important, non-obvious)
The VM has **no KVM** (`/dev/kvm` is absent), so the emulator must run in **software (TCG) mode**. This works but is slow, and requires the following to be usable:

- **Use an AOSP (`default`) system image, not `google_apis`.** The Google APIs image thrashes under software emulation (guest load ~40, never settles, persistent "isn't responding" ANR dialogs). An AOSP image (e.g. `system-images;android-30;default;x86_64`) is far lighter and settles to a responsive state. An AVD named `aosp30` is already created in the snapshot.
- Launch flags that work: `emulator -avd aosp30 -no-snapshot -no-audio -no-boot-anim -gpu swiftshader_indirect -accel off -qemu -m 3072` (run it in a persistent tmux session; the host stays near load ~1 while the guest emulates).
- Boot and the app's first-run **AppSearch indexing** spike CPU and can trigger system "isn't responding" (ANR) dialogs. Mitigation: `adb shell settings put global hide_error_dialogs 1`, then `adb reboot` once to clear any stuck ANR dialog. After the guest settles (`adb shell cat /proc/loadavg` 1‑min avg below ~3) the UI is responsive. Disable animations: `adb shell settings put global {window,transition,animator}_*_scale 0`.
- `adb shell input tap X Y` uses **device pixel** coordinates (this AVD is 1080x2400), **not** the scaled screenshot coordinates. Get exact element bounds with `adb shell uiautomator dump /sdcard/ui.xml && adb shell cat /sdcard/ui.xml`.
- SearchLauncher is a launcher app: on first launch it shows one-time onboarding dialogs ("Set as Default Launcher?", "Privacy choices") and may prompt for permissions. Pre-grant runtime perms to avoid prompts: `adb shell pm grant com.searchlauncher.app android.permission.READ_CONTACTS` (and `RECORD_AUDIO`). Launch the search UI directly with `adb shell am start -n com.searchlauncher.app/.ui.MainActivity`.
- Core "hello world": type a query (e.g. `settings`) into the search bar and tap the matching app result — SearchLauncher launches the target app (verified with the Android Settings app).

### Notes
- Release signing is optional; debug builds/tests/emulator runs need no signing keystore or secrets.
- Gradle dependencies are fetched on the first build (no separate install step). The startup update script only runs `./gradlew --version` to warm the wrapper.
