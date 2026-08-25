# Store listing images

Generates the images uploaded to app stores: a headline over a framed device
screenshot, one proposition per image. Nothing here is drawn by hand, so the copy can
be rewritten and the whole set rebuilt in seconds.

## Running it

```bash
cd marketing
python3 -m venv .venv && .venv/bin/pip install -r requirements.txt
.venv/bin/python generate.py            # every set in listing.json
.venv/bin/python generate.py phone      # just one
```

Output goes to `out/<set>/`, and is committed so the images can be uploaded without
running anything.

## Changing things

Everything that decides how an image looks is in **`listing.json`**. `generate.py` only
knows how to draw; it has no copy or colours in it.

- **Copy** — edit `headline` and `sub` on any entry. Headlines wrap automatically, so
  length is free, but two lines is the most that looks right on a phone canvas.
- **Colour** — `palette` on a set picks from `palettes`. `carbon` is the current choice:
  near-black, so each screenshot's own colours carry the image. `green` matches the app
  icon and `indigo` matches the wallpaper in the screenshots.
- **A new set** — add to `sets` with a `device` from `devices`, a `palette`, a `source`
  directory of screenshots, and the list of images.
- **A new canvas size** — add to `devices`. A landscape canvas holding a portrait
  screenshot puts the text on the left and the device on the right; anything else stacks
  the headline above the device. Either way the device is fitted to the space left over,
  so it never runs off an edge.

Both sets say the same six things in the same order, so a copy change means editing two
entries rather than two sets of images. Renaming a source screenshot leaves the old
render behind in `out/`; delete it by hand.

## Where the screenshots come from

`../screenshots` (phone) and `../screenshots-tablet` (tablet) hold the plain captures.
Both are taken on emulators seeded with fictional data — never on a real phone, so no
real contact, wallpaper or browsing history can reach a store listing.

Two AVDs on the `android-35` `google_apis` image, which boots on this machine where
`android-36.1` freezes partway through:

```bash
avdmanager create avd -n Phone_A35  -k "system-images;android-35;google_apis;arm64-v8a" -d pixel_8
avdmanager create avd -n Tablet_A35 -k "system-images;android-35;google_apis;arm64-v8a" -d pixel_tablet
emulator -avd Phone_A35 -no-snapshot-load -no-boot-anim
```

Then, per emulator:

1. `./gradlew :app:installDebug` with `ANDROID_SERIAL` set, and
   `adb shell cmd package set-home-activity com.searchlauncher.app.debug/com.searchlauncher.app.ui.MainActivity`.
2. Walk onboarding, granting contacts and calendar and turning autocomplete on — the
   YouTube suggestions in `03_search_youtube` need it.
3. Seed contacts (Ada Lovelace, Grace Hopper, Alan Turing, Katherine Johnson, Mae
   Jemison, Maya Chen, Marcus Webb) with `content insert` against
   `content://com.android.contacts`.
4. Add an analog clock and a calendar month widget from the launcher's own
   **Add Widget** menu, long-pressed on the wallpaper.

The launcher's whole state — wallpapers, favourites, custom shortcuts — moves between
emulators in one step, which is how the phone was set up from the tablet:

```bash
adb -s <from> exec-out run-as com.searchlauncher.app.debug tar -c files shared_prefs > /tmp/sl.tar
adb -s <to> push /tmp/sl.tar /data/local/tmp/
adb -s <to> shell 'run-as com.searchlauncher.app.debug sh -c "rm -rf files shared_prefs && tar -xf /data/local/tmp/sl.tar"'
```

Widget IDs do not survive the copy: the launcher shows a "Widget unavailable" card for
each one, and they have to be removed and added again.

`fastlane/metadata/android/en-US/images/phoneScreenshots/` is a separate copy, and is
what F-Droid actually publishes. It is not kept in sync automatically.

## Store requirements

Check the current rules before uploading — they move. At the time of writing Play wants
between 2 and 8 screenshots per form factor, 16:9 or 9:16, with each side between 320px
and 3840px, and separate sets for 7-inch and 10-inch tablets if the app should be
treated as large-screen ready. The phone canvas here is 1080x1920 and the tablet one
2560x1600.
