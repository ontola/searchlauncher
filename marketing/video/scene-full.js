// The full demo video in direction B (bright and bouncy): light paper background,
// the phone in the middle, and whatever it finds pops out around it as cards.
import { prog, lerp, easeOut, easeOut5, easeIn, easeInOut, spring, typing, phoneScreen, camera, RESULTS, LOGO, clamp, page, CLOCK, calendarWidget, APP_LIST } from "./lib.js";

export const DURATION = 29.5;

export const CSS = `
.bgB{position:absolute;inset:0;background:#f3f6f2}
.dotsB{position:absolute;inset:-100px;background-image:radial-gradient(rgba(16,32,24,.13) 2px,transparent 2.5px);background-size:44px 44px}
.cap{position:absolute;left:0;right:0;top:64px;text-align:center;font-weight:700;font-size:76px;letter-spacing:-2px;color:#102018;white-space:nowrap}
.cap em{font-style:normal;color:#0f7a5a}
.fly{position:absolute;left:0;top:0;transform-origin:50% 50%;font-family:Roboto;color:#e8eaed}
.fly .row{left:0;right:0;top:0;box-shadow:0 18px 40px rgba(16,32,24,.28)}
.tag{position:absolute;left:10px;top:-30px;font-family:"Space Grotesk";font-weight:600;font-size:15px;color:#fff;background:#0f7a5a;border-radius:20px;padding:4px 12px;white-space:nowrap}
.sticker{position:absolute;font-family:"Space Grotesk";font-weight:700;color:#fff;border-radius:999px;padding:18px 34px;font-size:44px;box-shadow:0 14px 30px rgba(16,32,24,.25);white-space:nowrap}
.mini{position:relative;width:160px;height:327px;border-radius:14px;overflow:hidden;box-shadow:0 20px 44px rgba(16,32,24,.3)}
.mini .pg{width:360px;height:736px;transform:scale(.4444);transform-origin:0 0}
.keycap{width:100px;height:110px;border-radius:18px;background:#3b3e42;color:#eef0f1;font-size:58px;display:flex;align-items:center;justify-content:center;box-shadow:0 10px 0 #24272a,0 24px 40px rgba(16,32,24,.3);font-family:Roboto}
.appi{width:96px;height:96px;border-radius:50%;font-family:Roboto;font-weight:700;font-size:40px;color:#fff;display:flex;align-items:center;justify-content:center;box-shadow:0 16px 34px rgba(16,32,24,.28)}
.pill{font-family:"Roboto Mono",monospace;font-size:24px;font-weight:500;background:#fff;color:#102018;border:3px solid #102018;border-radius:12px;padding:8px 16px;white-space:nowrap}
.shield{width:200px;height:200px;border-radius:50%;background:#0f7a5a;display:flex;align-items:center;justify-content:center;box-shadow:0 20px 50px rgba(15,122,90,.35)}
.gp{display:inline-flex;align-items:center;gap:14px;background:#fff;color:#0f7a5a;border-radius:999px;padding:16px 34px;font-size:34px;font-weight:700}
`;

// The phone sits at stage (960, 600), scaled so it fits between the caption and the bottom edge.
const P = { x: 960, y: 600, s: 0.98 };
const stagePt = (px, py, s = P.s) => [P.x + (px - 180) * s, P.y + (py - 400) * s];

// Something that springs out of the phone to a spot on the stage, and later drops back in.
function fly(t, { start, back, from, to, scale = 1.5, rot = 0, w, h, html, fade = false }) {
  if (t < start) return "";
  const out = spring(t - start, 1.6, 0.5);
  const b = back == null ? 0 : easeIn(prog(t, back, back + 0.24));
  if (b >= 1) return "";
  const p = out * (1 - b);
  const [fx, fy] = stagePt(...from);
  const x = lerp(fx, to[0], p), y = lerp(fy, to[1], p);
  const sc = lerp(P.s, scale, p);
  const o = fade ? clamp(p * 3) * (1 - b) : clamp(p * 3);
  return `<div class="fly" style="width:${w}px;height:${h}px;transform:translate(${x - w / 2}px,${y - h / 2}px) scale(${sc}) rotate(${rot * p}deg);opacity:${o}">${html}</div>`;
}

const rowHtml = (r, label) =>
  `<div class="row">${r.icon}<div class="txt"><b>${r.title}</b><span>${r.sub}</span></div><div class="dots">⋮</div></div>${label ? `<div class="tag">${label}</div>` : ""}`;
// y of row i (of n) centre inside the phone
const rowY = (i, n) => 436 - (n - i) * 56 + 27;

function caption(t, html, tin, tout) {
  if (t < tin || t > tout + 0.3) return "";
  const p = spring(t - tin, 2.2, 0.5);
  const o = easeIn(prog(t, tout, tout + 0.2));
  return `<div class="cap" style="opacity:${clamp(p * 2) * (1 - o)};transform:translateY(${(1 - p) * 50 - o * 40}px) scale(${lerp(0.9, 1, clamp(p))})">${html}</div>`;
}

function sticker(t, label, color, x, y, rot, tin, tout) {
  if (t < tin) return "";
  const p = spring(t - tin, 2.0, 0.42) * (1 - easeIn(prog(t, tout, tout + 0.2)));
  return `<div class="sticker" style="left:${x - 130}px;top:${y - 45}px;background:${color};transform:scale(${p}) rotate(${rot}deg)">${label}</div>`;
}

const stagger = (t, t0, d = 0.04) => (i, n) => easeOut(prog(t, t0 + (n - 1 - i) * d, t0 + 0.2 + (n - 1 - i) * d));
const blink = (t, pressed) => Math.floor(t * 2.5) % 2 === 0 || !!pressed;
const slide = (t, a, b) => easeInOut(prog(t, a, b));
// a finger that presses at (x0,y0) at time a and drags to (x1,y1) by time b
const swipe = (t, a, b, x0, y0, x1, y1) => {
  if (t < a - 0.12 || t > b + 0.2) return null;
  const p = slide(t, a, b);
  const o = clamp(prog(t, a - 0.12, a)) * (1 - prog(t, b, b + 0.2));
  return { x: lerp(x0, x1, p), y: lerp(y0, y1, p), o };
};

const BEATS = [0, 3.3, 6.0, 10.0, 14.0, 18.0, 21.6, 25.2];

export function render(t) {
  let st = {}, extra = "", caps = "";

  if (t < 3.3) {
    // 1. One search bar: three letters find apps, contacts, history, settings
    const ty = typing(t, "mar", 0.75, 8);
    st = { query: ty.shown, pressed: ty.pressed, results: t > 0.9 ? RESULTS.mar : [], rowP: stagger(t, 0.9) };
    const n = RESULTS.mar.length;
    [[1, 420, 380, -5, "App shortcut"], [2, 420, 700, 4, "Contact"], [3, 1500, 380, 5, "Browser history"], [4, 1500, 700, -4, "Setting"]].forEach(([i, x, y, rot, label], k) => {
      extra += fly(t, { start: 1.4 + k * 0.08, back: 2.95 + k * 0.03, from: [180, rowY(i, n)], to: [x, y], rot, w: 340, h: 54, html: rowHtml(RESULTS.mar[i], label) });
    });
    caps += caption(t, "One search bar.", 0.3, 1.3);
    caps += caption(t, "Everything on <em>your phone</em>.", 1.45, 3.05);
  } else if (t < 6.0) {
    // 2. The web: search shortcuts with live suggestions
    const ty = typing(t, "lofi", 3.5, 12);
    st = { chip: "YouTube", query: ty.shown, pressed: ty.pressed, results: t > 3.75 ? RESULTS.lofi : [], rowP: stagger(t, 3.75, 0.035) };
    const n = RESULTS.lofi.length;
    [[1, 1500, 330, 4], [2, 1500, 520, -3], [3, 1500, 710, 5]].forEach(([i, x, y, rot], k) => {
      extra += fly(t, { start: 4.0 + k * 0.08, back: 5.7 + k * 0.03, from: [180, rowY(i, n)], to: [x, y], rot, w: 340, h: 54, html: rowHtml(RESULTS.lofi[i]) });
    });
    [["YouTube", "#e3262b", 420, 320, -6], ["Google", "#3f7fe0", 330, 480, 4], ["Maps", "#2e9e4f", 480, 630, -3], ["Spotify", "#1db954", 340, 780, 5], ["+ your own", "#102018", 470, 920, -2]].forEach(([l, c, x, y, r], k) => {
      extra += sticker(t, l, c, x, y, r, 4.1 + k * 0.1, 5.7 + k * 0.03);
    });
    caps += caption(t, "And all of <em>the web</em>.", 3.4, 5.75);
  } else if (t < 10.0) {
    // 3. Smart input: sums, phone numbers and web addresses
    const parts = [
      ["1250*365", 6.15, 18, "calc", 430, 380, -4, "Math"],
      ["+31612345678", 7.4, 32, "phone", 1490, 470, 4, "Phone number"],
      ["searchlauncher.eu", 8.55, 38, "url", 430, 660, 3, "Web address"],
    ];
    let cur = parts[0];
    for (const p of parts) if (t >= p[1] - 0.1) cur = p;
    const [text, t0, cps, key] = cur;
    const ty = typing(t, text, t0, cps);
    const shown = t0 + text.length / cps + 0.05;
    st = { query: ty.shown, pressed: ty.pressed, results: t > shown ? RESULTS[key] : [], rowP: () => easeOut(prog(t, shown, shown + 0.18)) };
    parts.forEach(([text, t0, cps, key, x, y, rot, label], k) => {
      const shownAt = t0 + text.length / cps + 0.05;
      extra += fly(t, { start: shownAt + 0.15, back: 9.7 + k * 0.03, from: [180, rowY(0, 1)], to: [x, y], rot, scale: 1.6, w: 340, h: 54, html: rowHtml(RESULTS[key][0], label) });
    });
    caps += caption(t, "It knows <em>what you mean</em>.", 6.1, 9.75);
  } else if (t < 14.0) {
    // 4. Built-in browser that blocks ads and trackers
    st = { mode: "browser", pages: [{ kind: "wiki", x: 0 }], ads: [easeInOut(prog(t, 10.8, 11.4)), easeInOut(prog(t, 11.15, 11.75))], count: 1 };
    caps += caption(t, "A browser that <em>blocks ads</em>.", 10.1, 11.95);
    caps += caption(t, "And <em>trackers</em>, too.", 12.05, 13.8);
    if (t > 12.0) {
      const p = spring(t - 12.05, 1.8, 0.45) * (1 - easeIn(prog(t, 13.7, 13.9)));
      const shield = `<svg viewBox="0 0 24 24" width="110" height="110"><path fill="#fff" d="M12 2 4 5v6c0 5 3.4 9.7 8 11 4.6-1.3 8-6 8-11V5z"/><path d="m8.5 12 2.5 2.5 4.8-5" stroke="#0f7a5a" stroke-width="2.2" fill="none" stroke-linecap="round"/></svg>`;
      extra += `<div class="fly" style="width:200px;height:200px;transform:translate(1440px,460px) scale(${p})"><div class="shield">${shield}</div></div>`;
      ["tracker.js", "pixel.gif", "analytics", "fingerprint.js", "ad-sdk"].forEach((name, k) => {
        const a = 12.35 + k * 0.22;
        if (t < a || t > a + 0.75) return;
        const q = easeInOut(prog(t, a, a + 0.65));
        const [sx, sy] = stagePt(120 + (k % 3) * 70, 160 + k * 90);
        const x = lerp(sx, 1540, q), y = lerp(sy, 560, q) - Math.sin(q * Math.PI) * 160;
        const sc = lerp(0.8, 0.3, easeIn(q)) * (q < 0.15 ? q / 0.15 : 1);
        extra += `<div class="fly" style="transform:translate(${x - 90}px,${y - 25}px) scale(${sc});opacity:${1 - prog(q, 0.85, 1)}"><div class="pill" style="${q > 0.6 ? "text-decoration:line-through;color:#e3262b;border-color:#e3262b" : ""}">${name}</div></div>`;
      });
    }
  } else if (t < 18.0) {
    // 5. Tabs at your thumb: swipe the bar sideways, then up for all tabs
    const a = slide(t, 14.55, 14.9), b = slide(t, 15.25, 15.6);
    if (t < 16.3) {
      st = { mode: "browser", count: 3, pages: [{ kind: "wiki", x: -360 * a }, { kind: "osm", x: 360 * (1 - a) - 360 * b }, { kind: "vid", x: 360 * (1 - b) }] };
      st.touch = swipe(t, 14.55, 14.9, 300, 768, 70, 768) || swipe(t, 15.25, 15.6, 300, 768, 70, 768) || swipe(t, 16.0, 16.3, 180, 768, 180, 600);
    } else {
      st = { mode: "tabs", current: "vid", tabsP: prog(t, 16.3, 16.75), touch: swipe(t, 16.0, 16.3, 180, 768, 180, 600) };
    }
    caps += caption(t, "Swipe the bar to <em>switch tabs</em>.", 14.1, 15.95);
    caps += caption(t, "Swipe up to <em>see them all</em>.", 16.1, 17.8);
    [["wiki", 14 - 172, 420, -7], ["osm", 14, 1500, 6]].forEach(([kind, cx, x, rot], k) => {
      extra += fly(t, { start: 16.85 + k * 0.1, back: 17.6, from: [cx + 80, 92 + 163], to: [x, 560], rot, scale: 1.7, w: 160, h: 327, html: `<div class="mini">${page(kind)}</div>` });
    });
  } else if (t < 21.6) {
    // 6. Built-in keyboard, already up; swipe up for the app drawer
    st = { mode: "home", drawerP: easeOut5(prog(t, 20.0, 20.4)), touch: swipe(t, 19.95, 20.3, 180, 720, 180, 480) };
    caps += caption(t, "The keyboard is <em>already up</em>.", 18.1, 19.7);
    caps += caption(t, "Swipe up for <em>all your apps</em>.", 19.8, 21.4);
    const kx = [17, 50, 83, 116, 149, 182];
    [["S", 330, 640, -6, 1], ["E", 480, 560, 4, 0], ["A", 630, 680, -3, 1], ["R", 1290, 640, 5, 0], ["C", 1440, 560, -4, 2], ["H", 1590, 680, 6, 1]].forEach(([c, x, y, rot, row], k) => {
      extra += fly(t, { start: 18.4 + k * 0.07, back: 19.55 + k * 0.02, from: [kx[k] + 20, 560 + row * 55], to: [x, y], rot, scale: 1.3, w: 100, h: 110, html: `<div class="keycap">${c.toLowerCase()}</div>` });
    });
    const spots = [[420, 330], [600, 470], [380, 600], [590, 760], [1340, 330], [1520, 470], [1330, 620], [1530, 770]];
    spots.forEach(([x, y], k) => {
      const [name, color] = APP_LIST[[0, 7, 8, 13, 15, 14, 2, 11][k]];
      const col = [0, 3, 0, 1, 3, 2, 2, 3][k], row = [0, 1, 2, 3, 3, 3, 0, 2][k];
      extra += fly(t, { start: 20.5 + k * 0.05, back: 21.3 + k * 0.01, from: [45 + col * 85, 80 + row * 90], to: [x, y], rot: (k % 2 ? 1 : -1) * 6, scale: 1, w: 96, h: 96, html: `<div class="appi" style="background:${color}">${name[0]}</div>` });
    });
  } else {
    // 7. Your home screen: widgets and swipeable wallpapers
    const wallX = slide(t, 23.65, 24.0) + slide(t, 24.35, 24.7);
    st = { mode: "home", t: t - 21.6, widgetP: spring(t - 21.8, 1.8, 0.5), wallX, touch: swipe(t, 23.6, 23.95, 300, 300, 60, 300) || swipe(t, 24.3, 24.65, 300, 300, 60, 300) };
    caps += caption(t, "Widgets on your <em>home screen</em>.", 21.7, 23.3);
    caps += caption(t, "Swipe through <em>wallpapers</em>.", 23.4, 25.0);
    extra += fly(t, { start: 22.25, back: 23.1, from: [180, 136], to: [440, 520], rot: -5, scale: 1.7, w: 160, h: 160, html: CLOCK(t - 21.6) });
    extra += fly(t, { start: 22.35, back: 23.15, from: [180, 321], to: [1480, 560], rot: 4, scale: 1.35, w: 320, h: 170, html: calendarWidget() });
  }
  st.caret = blink(t, st.pressed);
  if (!st.mode) st.wallpaper = "linear-gradient(180deg,#2a4a3b 0%,#1a2b23 45%,#121815 75%)";

  // phone: pops in, dips at each new beat and on key presses, shrinks away at the end
  const pop = spring(t - 0.05, 1.7, 0.45);
  let dip = 0;
  for (const b of BEATS.slice(1, -1)) {
    const d = t - b;
    if (d > 0 && d < 0.5) dip = 0.05 * Math.sin((d / 0.5) * Math.PI) * (1 - d / 0.5);
  }
  const bump = st.pressed ? 0.01 : 0;
  const away = easeIn(prog(t, 25.0, 25.35));
  const s = P.s * lerp(0.4, 1, pop) * (1 - dip + bump) * (1 - away * 0.9);
  const phone = `<div class="phone" style="${camera(P.x, P.y + away * 40, 180, 400, s)}">${phoneScreen(st)}</div>`;

  // end card: green circle wipe
  let end = "";
  if (t > 25.15) {
    const r = easeOut5(prog(t, 25.15, 25.6)) * 1300;
    const p = spring(t - 25.4, 1.8, 0.5);
    const p2 = easeOut5(prog(t, 25.6, 25.95));
    const p3 = easeOut5(prog(t, 25.8, 26.15));
    const p4 = spring(t - 26.3, 2.0, 0.45);
    end = `<div class="layer" style="background:#0f7a5a;clip-path:circle(${r}px at 960px 600px)">
      <div class="abs" style="left:0;right:0;top:230px;display:flex;flex-direction:column;align-items:center;color:#fff">
        <div class="logo" style="width:190px;height:190px;transform:scale(${p})">${LOGO}</div>
        <div style="margin-top:34px;font-size:118px;font-weight:700;letter-spacing:-3px;opacity:${p2};transform:translateY(${(1 - p2) * 40}px)">SearchLauncher</div>
        <div style="margin-top:12px;font-family:'Source Sans 3';font-size:40px;color:#d5ece2;opacity:${p3};transform:translateY(${(1 - p3) * 30}px)">Search everything. Free, no ads, open source.</div>
        <div style="margin-top:56px;transform:scale(${p4})"><div class="gp">Get it on Google Play <span style="opacity:.5">·</span> searchlauncher.eu</div></div>
      </div></div>`;
  }

  return `<div class="bgB"></div><div class="dotsB" style="transform:translate(${(-t * 12) % 44}px,${(-t * 20) % 44}px)"></div>
    ${phone}${extra}${caps}${end}`;
}
