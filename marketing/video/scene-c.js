// Direction C: the dive. No title cards at first; the camera lives inside the
// interface at huge scale, rides over the results and only pulls back at the end.
import { prog, lerp, easeOut, easeOut5, easeIn, easeInOut, spring, keys, typing, phoneScreen, camera, RESULTS, LOGO, clamp } from "./lib.js";

export const DURATION = 7.0;

export const CSS = `
.bgC{position:absolute;inset:0;background:radial-gradient(900px 700px at 50% 55%,#12301f 0%,#08120d 60%,#030605 100%)}
.wordC{position:absolute;font-weight:700;letter-spacing:-3px;color:#eef6f1;white-space:nowrap}
.wordC em{font-style:normal;color:#7fd3a8}
.flash{position:absolute;inset:0;background:#7fd3a8;mix-blend-mode:screen}
`;

export function render(t) {
  let st;
  const beat = t < 3.45 ? 1 : 2;
  if (beat === 1) {
    const ty = typing(t, "mar", 0.45, 4.5);
    st = {
      query: ty.shown, pressed: ty.pressed,
      results: t > 1.05 ? RESULTS.mar : [],
      rowP: (i, n) => easeOut(prog(t, 1.05 + (n - 1 - i) * 0.07, 1.35 + (n - 1 - i) * 0.07)),
    };
  } else {
    const ty = typing(t, "1250*365", 3.6, 16);
    st = { query: ty.shown, pressed: ty.pressed, results: t > 4.15 ? RESULTS.calc : [], rowP: () => easeOut(prog(t, 4.15, 4.35)) };
  }
  st.caret = Math.floor(t * 2.5) % 2 === 0 || !!st.pressed;

  // key presses punch the camera in a little
  const ty1 = beat === 1 ? typing(t, "mar", 0.45, 4.5) : typing(t, "1250*365", 3.6, 16);
  const punch = ty1.pressed ? 0.06 : 0;

  // camera path: [time, scale, focus x, focus y, stage x]
  const s = keys(t, [[0, 4.4], [1.0, 4.6], [1.35, 3.1, easeOut5], [2.2, 2.9], [2.75, 1.05, easeInOut], [3.3, 1.05], [3.55, 4.4, easeIn], [4.1, 4.6], [4.4, 6.0, easeOut5], [4.95, 6.2], [5.35, 0.7, easeInOut]]);
  const fx = keys(t, [[0, 180], [2.2, 180], [2.75, 180], [3.55, 180], [4.1, 180], [4.4, 110, easeOut5], [4.95, 110], [5.35, 180, easeInOut]]);
  const fy = keys(t, [[0, 494], [1.0, 494], [1.35, 380, easeOut5], [2.2, 150], [2.75, 400, easeInOut], [3.3, 400], [3.55, 494, easeIn], [4.1, 494], [4.4, 405, easeOut5], [4.95, 405], [5.35, 400, easeInOut]]);
  const sx = keys(t, [[0, 960], [2.2, 960], [2.75, 1300, easeInOut], [3.3, 1300], [3.55, 960, easeIn], [5.0, 960], [5.35, 960]]);
  const sy = keys(t, [[0, 540], [5.0, 540], [5.35, 540], [5.6, 1500, easeIn]]);
  const speed = Math.abs(keys(t + 0.02, [[2.2, 2.9], [2.75, 1.05, easeInOut], [3.3, 1.05], [3.55, 4.4, easeIn]]) - keys(t - 0.02, [[2.2, 2.9], [2.75, 1.05, easeInOut], [3.3, 1.05], [3.55, 4.4, easeIn]]));
  const blur = Math.min(10, speed * 30);

  // big words
  let words = "";
  if (t > 2.6 && t < 3.4) {
    const p = easeOut5(prog(t, 2.6, 2.85));
    const o = easeIn(prog(t, 3.25, 3.4));
    words += `<div class="wordC" style="left:150px;top:330px;font-size:130px;line-height:1.05;opacity:${p * (1 - o)};transform:translateX(${(1 - p) * -80}px)">Search<br><em>everything.</em></div>`;
  }
  // flash on the calculator answer
  const flash = Math.max(0, 1 - Math.abs(t - 4.4) / 0.12) * 0.35;

  let end = "";
  if (t > 5.45) {
    const p = spring(t - 5.45, 1.8, 0.55);
    const p2 = easeOut5(prog(t, 5.65, 6.0));
    const p3 = easeOut5(prog(t, 5.85, 6.2));
    end = `<div class="abs" style="left:0;right:0;top:300px;display:flex;flex-direction:column;align-items:center">
      <div class="logo" style="width:200px;height:200px;color:#7fd3a8;transform:scale(${p})">${LOGO}</div>
      <div style="margin-top:36px;font-size:120px;font-weight:700;letter-spacing:-3px;color:#eef6f1;opacity:${p2};transform:translateY(${(1 - p2) * 40}px)">SearchLauncher</div>
      <div style="margin-top:14px;font-family:'Source Sans 3';font-size:40px;color:#9fb7aa;opacity:${p3};transform:translateY(${(1 - p3) * 30}px)">Search everything. Free, no ads, open source.</div>
    </div>`;
  }

  return `<div class="bgC"></div>
    <div class="phone" style="${camera(sx, sy, fx, fy, s * (1 + punch))};filter:blur(${blur}px)">${phoneScreen(st)}</div>
    ${words}${flash ? `<div class="flash" style="opacity:${flash}"></div>` : ""}${end}`;
}
