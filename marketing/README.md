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
- **A new canvas size** — add to `devices`. A landscape size (width > height) switches
  the layout: text moves to the left and the device sits on the right, sized to the
  canvas height. Portrait stacks the headline above the device, which bleeds off the
  bottom edge.

Source screenshots are the plain captures in `../screenshots`, which are also what
F-Droid shows. Those are taken on a real device, not composed here.

## Tablet screenshots are missing

The `tablet10` set is written out in `listing.json` but has no sources, so
`generate.py` reports them as missing and skips them. It needs real tablet captures in
`screenshots-tablet/`.

Two ways to get them, neither done yet:

1. **Resize a phone's display.** `adb shell wm size 1600x2560 && adb shell wm density 320`
   makes the app lay out as a large screen on real hardware; capture with
   `adb exec-out screencap -p`, then **`adb shell wm size reset && adb shell wm density reset`**.
   Reset it afterwards or the phone stays that way.
2. **A real tablet**, over adb.

The Pixel Tablet emulator (`Pixel_Tablet_API36`) is not an option on this machine: it
logs `hvf is not enabled on this aarch64 host` and never finishes booting — tried twice,
the second time for an hour and a half.

Composing a *phone* screenshot onto a tablet-sized canvas is possible with the existing
config, but the listing would then show no real large-screen layout, which is the thing
tablet screenshots exist to demonstrate.

## Store requirements

Check the current rules before uploading — they move. At the time of writing Play wants
between 2 and 8 screenshots per form factor, 16:9 or 9:16, with each side between 320px
and 3840px, and separate sets for 7-inch and 10-inch tablets if the app should be
treated as large-screen ready. The phone canvas here is 1080x1920.
