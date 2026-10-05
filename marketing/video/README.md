# Demo video

A motion-graphic demo of SearchLauncher, drawn in code rather than recorded from a device.
The phone UI is recreated in HTML (`lib.js`), each scene is a pure function of time
(`render(t)` in `scene-*.js`), and `render.mjs` screenshots every frame with Playwright and
pipes them into ffmpeg. Change copy or timing, re-render, done.

- `scene-full.js`: the 39 second video (light, bouncy style: results pop out of the phone).
- `scene-a.js`, `scene-b.js`, `scene-c.js`: the three 7 second style studies it was picked from.

## Rendering

Needs Node, ffmpeg and a Chromium that Playwright can launch.

```bash
cd marketing/video
npm install
node render.mjs full out/searchlauncher-demo.mp4        # 1920x1080, 30 fps
node render.mjs full --stills out 2.2 12.9               # single frames, for checking
```

Open `index.html?scene=full` through any static server to watch a live, looping preview.

App icons are brand logos from the Iconify `logos` set (CC0), inlined in `icons.js`.

Fonts (Space Grotesk, Source Sans 3, Roboto; SIL Open Font License) are bundled in `fonts/`
so renders don't depend on network access.
