# Pinned shortcuts and work profiles

The Android test APK contains `ShortcutPublisherActivity`, a manual fixture for
Android's `ShortcutManager.requestPinShortcut` flow. It does not ship in the app.
Use a disposable emulator; these commands change its default launcher and create
a work profile. Substitute your device serial and the returned profile ID.

```sh
./gradlew assembleDebug assembleDebugAndroidTest
adb -s emulator-5556 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5556 install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s emulator-5556 shell cmd package set-home-activity com.searchlauncher.app.debug/com.searchlauncher.app.ui.MainActivity
adb -s emulator-5556 shell am start -n com.searchlauncher.app.debug.test/com.searchlauncher.app.fixture.ShortcutPublisherActivity --ez request true
```

The publisher should report that pinning is supported. Accept **Add shortcut?**,
open SearchLauncher, and tap **Shelter shortcut probe** in favorites. The publisher
should display **Pinned shortcut opened**. Repeat with `--es shortcut_id cancel-probe`
and cancel the dialog: no new favorite should appear. A direct launch of
`com.searchlauncher.app.debug/com.searchlauncher.app.ui.PinShortcutActivity` without
a system pin request should finish without adding anything.

To test personal and work copies of the same package:

```sh
adb -s emulator-5556 shell pm create-user --profileOf 0 --managed ShelterTest
adb -s emulator-5556 shell am start-user -w 10
adb -s emulator-5556 shell cmd package install-existing --user 10 com.searchlauncher.app.debug.test
```

Return to SearchLauncher and allow indexing to finish before entering **Profile**.
There should be two **Profile test app** results, one labeled **Work profile**.
Open each and inspect `adb -s emulator-5556 shell dumpsys activity activities`:
the resumed activity should be in `u0` or `u10`, respectively.

Request the same shortcut from the work copy with `am start --user 10` and the
publisher command above. Both favorites should coexist and open their respective
profiles. Restart the launcher and check again to exercise cached favorites.

Remove only the disposable profile you created when finished:
`adb -s emulator-5556 shell pm remove-user 10`.

These checks exercise Android's API and profile dispatch. They do not replace
verifying the original Shelter flow on the reporter's device.
