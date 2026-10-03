// Direction A: kinetic type. Dark brand green, words slam in beside the phone,
// the camera punches into the results and whips between beats.
import { prog, lerp, easeOut, easeOut5, easeIn, back, spring, keys, typing, phoneScreen, camera, RESULTS, LOGO, clamp } from "./lib.js";

export const DURATION = 7.6;

export const CSS = `
.bgA{position:absolute;inset:0;background:radial-gradient(1200px 900px at 70% 50%,#173627 0%,#0c1912 55%,#070d0a 100%)}
.gridA{position:absolute;inset:-200px;background-image:linear-gradient(rgba(127,211,168,.06) 1px,transparent 1px),linear-gradient(90deg,rgba(127,211,168,.06) 1px,transparent 1px);background-size:80px 80px}
.word{position:absolute;left:150px;font-weight:700;font-size:150px;line-height:1;letter-spacing:-4px;color:#eef6f1;white-space:nowrap;transform-origin:0 50%}
.word.acc{color:#7fd3a8}
.small{position:absolute;left:156px;font-family:"Source Sans 3";font-size:40px;color:#9fb7aa}
.big{position:absolute;font-weight:700;color:#7fd3a8;letter-spacing:-6px;white-space:nowrap}
.brand{position:absolute;left:150px;top:80px;display:flex;align-items:center;gap:16px;color:#eef6f1;font-size:30px;font-weight:600}
.brand .logo{width:44px;height:44px;color:#7fd3a8}
.bars{position:absolute;left:150px;bottom:90px;display:flex;gap:14px}
.bars div{width:90px;height:4px;background:rgba(238,246,241,.15);border-radius:2px;overflow:hidden}
.bars i{display:block;height:100%;background:#7fd3a8}
`;

// A word that slams in (big to normal) and leaves upward.
const slam = (t, text, y, tin, tout, acc = false, size = 150) => {
  if (t < tin || t > tout + 0.25) return "";
  const p = prog(t, tin, tin + 0.22);
  const o = prog(t, tout, tout + 0.18);
  const s = lerp(1.5, 1, easeOut5(p));
  const blur = (1 - p) * 14 + o * 10;
  const dy = -easeIn(o) * 90;
  return `<div class="word ${acc ? "acc" : ""}" style="top:${y}px;font-size:${size}px;opacity:${Math.min(p * 3, 1) * (1 - o)};transform:translateY(${dy}px) scale(${s});filter:blur(${blur}px)">${text}</div>`;
};

export function render(t) {
  // --- phone state per beat -------------------------------------------------
  let st = { query: "", results: [] };
  const beat = t < 2.97 ? 1 : t < 4.75 ? 2 : 3;
  if (beat === 1) {
    const ty = typing(t, "mar", 0.85, 8);
    st = {
      query: ty.shown, pressed: ty.pressed,
      results: t > 1.0 ? RESULTS.mar : [],
      rowP: (i, n) => easeOut(prog(t, 1.0 + (n - 1 - i) * 0.04, 1.22 + (n - 1 - i) * 0.04)),
      rowGlow: (i) => {
        const at = { 1: 1.85, 2: 2.05, 4: 2.25, 3: 2.45 }[i];
        return at ? clamp(prog(t, at, at + 0.1) - prog(t, 2.8, 2.95)) : 0;
      },
    };
  } else if (beat === 2) {
    const ty = typing(t, "1250*365", 3.12, 18);
    st = { query: ty.shown, pressed: ty.pressed, results: t > 3.6 ? RESULTS.calc : [], rowP: () => easeOut(prog(t, 3.6, 3.78)) };
  } else {
    const ty = typing(t, "lofi", 4.95, 12);
    st = {
      chip: "YouTube", query: ty.shown, pressed: ty.pressed,
      results: t > 5.15 ? RESULTS.lofi : [],
      rowP: (i, n) => easeOut(prog(t, 5.15 + (n - 1 - i) * 0.035, 5.35 + (n - 1 - i) * 0.035)),
    };
  }
  st.caret = Math.floor(t * 2.5) % 2 === 0 || !!st.pressed;

  // --- camera ---------------------------------------------------------------
  const enter = spring(t - 0.05, 1.6, 0.6);
  let s = keys(t, [[0, 1.12], [1.5, 1.12], [1.85, 1.95, easeOut5], [2.8, 2.05], [2.97, 1.12, easeIn], [5.7, 1.12], [6.1, 0.55, easeIn]]);
  let cy = keys(t, [[0, 400], [1.5, 400], [1.85, 270, easeOut5], [2.8, 255], [2.97, 400, easeIn]]);
  let x = keys(t, [[0, 1340], [1.5, 1340], [1.85, 1300, easeOut5], [5.7, 1340], [6.1, 2400, easeIn]]);
  const y = 540 + (1 - enter) * 900;
  // zoom punch on the calculator result
  if (beat === 2) {
    s = keys(t, [[3.0, 1.12], [3.62, 1.12], [3.78, 1.32, easeOut5], [4.6, 1.36]]);
    cy = keys(t, [[3.0, 400], [3.62, 400], [3.78, 405, easeOut5]]);
  }
  const shake = st.pressed ? Math.sin(t * 120) * 2 : 0;

  // whip transitions blur and slide everything
  const whip = (a) => Math.max(0, 1 - Math.abs(t - a) / 0.09);
  const w = Math.max(whip(2.97), whip(4.75));
  const wx = t < 2.97 ? -prog(t, 2.88, 2.97) : t < 3.06 ? 1 - prog(t, 2.97, 3.06) : t < 4.75 ? -prog(t, 4.66, 4.75) : 1 - prog(t, 4.75, 4.84);
  const sceneX = (t > 2.85 && t < 3.08) || (t > 4.63 && t < 4.86) ? wx * 500 : 0;

  // --- words ----------------------------------------------------------------
  let words = "";
  words += slam(t, "Three", 300, 0.15, 1.5);
  words += slam(t, "letters.", 460, 0.3, 1.52, true);
  words += slam(t, "Apps.", 220, 1.85, 2.82);
  words += slam(t, "Contacts.", 380, 2.05, 2.84);
  words += slam(t, "Settings.", 540, 2.25, 2.86);
  words += slam(t, "The web.", 700, 2.45, 2.88, true);
  words += slam(t, "Do the", 200, 3.06, 4.62, false, 120);
  words += slam(t, "math.", 330, 3.14, 4.64, true, 120);
  words += slam(t, "Search", 300, 4.85, 5.65);
  words += slam(t, "any site.", 460, 4.98, 5.67, true);

  // flying calculator answer
  let big = "";
  if (t > 3.8 && t < 4.75) {
    const p = easeOut5(prog(t, 3.8, 4.15));
    const size = lerp(40, 230, p);
    big = `<div class="big" style="left:${lerp(1100, 150, p)}px;top:${lerp(600, 560, p)}px;font-size:${size}px;opacity:${Math.min(1, p * 4)}">456,250</div>`;
  }

  // end card
  let end = "";
  if (t > 5.8) {
    const p = spring(t - 5.85, 1.8, 0.55);
    const p2 = easeOut5(prog(t, 6.1, 6.45));
    const p3 = easeOut5(prog(t, 6.35, 6.7));
    end = `<div class="abs" style="left:0;right:0;top:300px;display:flex;flex-direction:column;align-items:center">
      <div class="logo" style="width:200px;height:200px;color:#7fd3a8;transform:scale(${p}) rotate(${(1 - p) * -90}deg)">${LOGO}</div>
      <div style="margin-top:36px;font-size:120px;font-weight:700;letter-spacing:-3px;color:#eef6f1;opacity:${p2};transform:translateY(${(1 - p2) * 40}px)">SearchLauncher</div>
      <div style="margin-top:14px;font-family:'Source Sans 3';font-size:40px;color:#9fb7aa;opacity:${p3};transform:translateY(${(1 - p3) * 30}px)">Search everything. Free, no ads, open source.</div>
    </div>`;
  }

  const progress = [prog(t, 0, 2.97), prog(t, 2.97, 4.75), prog(t, 4.75, 5.8), prog(t, 5.8, 7.6)];
  const chrome = t < 5.8
    ? `<div class="brand"><div class="logo">${LOGO}</div>SearchLauncher</div>
       <div class="bars">${progress.map((p) => `<div><i style="width:${p * 100}%"></i></div>`).join("")}</div>`
    : "";

  return `<div class="bgA"></div>
    <div class="gridA" style="transform:translate(${-t * 30}px,${-t * 18}px)"></div>
    <div class="layer" style="transform:translateX(${sceneX}px);filter:blur(${w * 18}px)">
      ${words}${big}
      <div class="phone" style="${camera(x + shake, y, 180, cy, s)}">${phoneScreen(st)}</div>
    </div>
    ${chrome}${end}`;
}
