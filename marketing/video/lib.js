// Shared timing helpers and a recreated SearchLauncher phone UI.
// Everything is a pure function of time, so any frame can be rendered on its own.

export const clamp = (x, a = 0, b = 1) => Math.min(b, Math.max(a, x));
export const prog = (t, a, b) => clamp((t - a) / (b - a));
export const lerp = (a, b, x) => a + (b - a) * x;
export const easeOut = (x) => 1 - Math.pow(1 - x, 3);
export const easeOut5 = (x) => 1 - Math.pow(1 - x, 5);
export const easeIn = (x) => x * x * x;
export const easeInOut = (x) => (x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2);
export const back = (x) => {
  const c1 = 2.2, c3 = c1 + 1;
  return 1 + c3 * Math.pow(x - 1, 3) + c1 * Math.pow(x - 1, 2);
};
// Damped spring from 0 to 1, dt in seconds since start.
export const spring = (dt, freq = 2.4, damp = 0.42) => {
  if (dt <= 0) return 0;
  const w = 2 * Math.PI * freq, wd = w * Math.sqrt(1 - damp * damp);
  return 1 - Math.exp(-damp * w * dt) * (Math.cos(wd * dt) + ((damp * w) / wd) * Math.sin(wd * dt));
};
// Animate between keyframes [[t, value], ...] with an easing per segment.
export const keys = (t, frames, ease = easeInOut) => {
  if (t <= frames[0][0]) return frames[0][1];
  for (let i = 1; i < frames.length; i++) {
    const [t1, v1, e] = frames[i];
    const [t0, v0] = frames[i - 1];
    if (t <= t1) return lerp(v0, v1, (e || ease)(prog(t, t0, t1)));
  }
  return frames[frames.length - 1][1];
};

// Typing: returns the visible text and the key being pressed right now.
export const typing = (t, text, t0, cps) => {
  const n = clamp(Math.floor((t - t0) * cps) + 1, 0, text.length);
  const shown = t < t0 ? "" : text.slice(0, n);
  let pressed = null;
  if (t >= t0 && n > 0) {
    const since = t - (t0 + (n - 1) / cps);
    if (since < 0.11) pressed = text[n - 1];
  }
  return { shown, pressed, done: n >= text.length && t >= t0 };
};

export const LOGO = `<svg viewBox="0 0 128 128" fill="none"><g fill="currentColor"><path d="M10 58 L30.93 37.33 A39 39 0 0 0 30.93 78.67 Z"/><path d="M118 58 L97.07 37.33 A39 39 0 0 1 97.07 78.67 Z"/><path d="M40.49 71.26 L50.74 81.51 L31.65 100.6 L21.4 90.35 Z"/></g><circle cx="64" cy="58" r="27" stroke="currentColor" stroke-width="15"/></svg>`;

const GEAR = `<svg viewBox="0 0 24 24" width="22" height="22"><path fill="#fff" d="M19.4 13a7.5 7.5 0 0 0 0-2l2.1-1.6-2-3.5-2.5 1a7.6 7.6 0 0 0-1.7-1L15 3.3h-4l-.4 2.6a7.6 7.6 0 0 0-1.7 1l-2.5-1-2 3.5L6.6 11a7.5 7.5 0 0 0 0 2l-2.1 1.6 2 3.5 2.5-1a7.6 7.6 0 0 0 1.7 1l.4 2.6h4l.4-2.6a7.6 7.6 0 0 0 1.7-1l2.5 1 2-3.5zM13 15.5a3.5 3.5 0 1 1 0-7 3.5 3.5 0 0 1 0 7z" transform="translate(-1 0)"/></svg>`;
const PERSON = `<svg viewBox="0 0 24 24" width="24" height="24"><circle cx="12" cy="8" r="4.2" fill="#b9bec4"/><path d="M3.5 21c.8-4.3 4.2-6.5 8.5-6.5s7.7 2.2 8.5 6.5z" fill="#b9bec4"/></svg>`;
const GLOBE = `<svg viewBox="0 0 24 24" width="30" height="30"><circle cx="12" cy="12" r="10" fill="#5f6368"/><path d="M12 2a10 10 0 0 0 0 20M2.5 9h19M2.5 15h19M12 2c-3 3-3 17 0 20M12 2c3 3 3 17 0 20" stroke="#cfd3d7" stroke-width="1.3" fill="none"/></svg>`;

export const icon = (kind, letter, bg) => {
  if (kind === "gear") return `<div class="ic round" style="background:#3f7fe0">${GEAR}</div>`;
  if (kind === "person") return `<div class="ic round" style="background:#3c4146">${PERSON}</div>`;
  if (kind === "globe") return `<div class="ic round" style="background:transparent">${GLOBE}</div>`;
  if (kind === "calc") return `<div class="ic" style="background:#0d0f10;color:#fff;font-size:22px">=</div>`;
  return `<div class="ic" style="background:${bg}">${letter}</div>`;
};

export const RESULTS = {
  mar: [
    { title: "Google Maps Search: mar", sub: "Type 'm' to search", icon: icon("tile", "M", "#2e9e4f") },
    { title: "Maps: Work", sub: "Shortcut · Maps", icon: icon("tile", "◆", "#1f8a3c") },
    { title: "Mae Jemison", sub: "Contact", icon: icon("person") },
    { title: "openstreetmap.org", sub: "Browser history", icon: icon("globe") },
    { title: "Manage Write Settings", sub: "Action", icon: icon("gear") },
    { title: "Marcus Webb", sub: "Contact", icon: icon("person") },
  ],
  lofi: [
    { title: "Google Search", sub: "Type 'g' to search", icon: icon("tile", "G", "#3f7fe0") },
    { title: "lofi study music", sub: "YouTube Search Suggestion", icon: icon("tile", "Y", "#e3262b") },
    { title: "lofi asmr", sub: "YouTube Search Suggestion", icon: icon("tile", "Y", "#e3262b") },
    { title: "lofi beats", sub: "YouTube Search Suggestion", icon: icon("tile", "Y", "#e3262b") },
    { title: "lofi music", sub: "YouTube Search Suggestion", icon: icon("tile", "Y", "#e3262b") },
    { title: "YouTube Search: lofi", sub: "Search Shortcut", icon: icon("tile", "Y", "#e3262b") },
  ],
  calc: [{ title: "456250", sub: "Calculation result (Tap to copy)", icon: icon("calc") }],
};

const CHIPS = [
  ["G", "#3f7fe0"], ["Y", "#e3262b"], ["GEM", "#9a3fd0"], ["DD", "#e2582b"], ["BING", "#1aa085"],
  ["CAL", "#3f6fd8"], ["NAV", "#1f8a8a"], ["M", "#2e9e4f"], ["R", "#f05a22"], ["W", "#6b6f73"],
];
const FAVS = ["#4285f4", "#34a853", "#ea4335", "#5c9cf0", "#1e88e5", "#ff0000", "#fbbc04", "#e8453c"];

const MIC = `<svg viewBox="0 0 24 24" width="17" height="17" style="vertical-align:middle"><path fill="currentColor" d="M12 14a3 3 0 0 0 3-3V5a3 3 0 0 0-6 0v6a3 3 0 0 0 3 3zm5-3a5 5 0 0 1-10 0H5a7 7 0 0 0 6 6.9V21h2v-3.1a7 7 0 0 0 6-6.9z"/></svg>`;
const ROWS = ["qwertyuiop", "asdfghjkl", "zxcvbnm"];

function keyboard(pressed) {
  const k = (c, w = 32, cls = "") =>
    `<div class="key ${cls} ${pressed && pressed.toLowerCase() === c ? "down" : ""}" style="width:${w}px">${c}${
      pressed && pressed.toLowerCase() === c && c.length === 1 ? `<div class="pop">${c}</div>` : ""
    }</div>`;
  const num = "1234567890";
  const r0 = [...ROWS[0]].map((c, i) => k(c).replace("</div>", `<i>${num[i]}</i></div>`)).join("");
  const r1 = [...ROWS[1]].map((c) => k(c)).join("");
  const r2 = k("⇧", 46, "fn") + [...ROWS[2]].map((c) => k(c)).join("") + k("⌫", 46, "fn");
  const sym = pressed && "*0123456789".includes(pressed) ? pressed : null;
  const r3 =
    k("?123", 52, "fn") + k(",", 32, "fn") + `<div class="key ${sym ? "down" : ""}" style="width:150px">${sym ? `<div class="pop">${sym}</div>` : ""}</div>` + k(".", 32, "fn") + k("→", 52, "go");
  return `<div class="kb"><div class="kr">${r0}</div><div class="kr">${r1}</div><div class="kr">${r2}</div><div class="kr">${r3}</div></div>`;
}

// state: { query, caret, pressed, results, rowP: fn(i)->0..1, chip, wallpaper }
function searchScreen(s) {
  const results = s.results || [];
  const n = results.length;
  const rows = results
    .map((r, i) => {
      const p = s.rowP ? s.rowP(i, n) : 1;
      const fromBottom = n - 1 - i;
      const y = 436 - (fromBottom + 1) * 56;
      const hl = i === n - 1 ? "hl" : "";
      const g = s.rowGlow ? s.rowGlow(i) : 0;
      const glow = g > 0 ? `box-shadow:0 0 0 ${2 * g}px rgba(127,211,168,${g}),0 0 ${30 * g}px rgba(127,211,168,${0.5 * g});` : "";
      return `<div class="row ${hl}" style="top:${y + (1 - p) * 18}px;opacity:${p};${glow}">${r.icon}<div class="txt"><b>${r.title}</b><span>${r.sub}</span></div><div class="dots">⋮</div></div>`;
    })
    .join("");
  const hasQuery = !!s.query;
  const chips = hasQuery
    ? CHIPS.map(([l, c]) => `<div class="chip" style="background:${c}">${l}</div>`).join("")
    : FAVS.map((c) => `<div class="fav" style="background:${c}"></div>`).join("");
  const q = s.query || "";
  const chip = s.chip ? `<span class="qchip">${s.chip}</span>` : "";
  const caret = s.caret === false ? "" : `<span class="caret"></span>`;
  const field = hasQuery || s.chip ? `${chip}<span class="q">${q}</span>${caret}` : `${caret}<span class="ph">Search anything…</span>`;
  return `<div class="screen" style="background:${s.wallpaper || "var(--wall)"}">
    <div class="status"><span>9:41</span><span>▾ ▮</span></div>
    ${rows}
    <div class="chips">${chips}</div>
    <div class="bar">${field}<div class="baricons">${hasQuery ? "✕" : MIC}</div></div>
    ${keyboard(s.pressed)}
    <div class="handle"></div>
    ${touch(s.touch)}
  </div>`;
}


// Finger indicator: { x, y, o } in phone coordinates.
export const touch = (p) =>
  p && p.o > 0 ? `<div class="touch" style="left:${p.x - 22}px;top:${p.y - 22}px;opacity:${p.o};transform:scale(${lerp(1.4, 1, clamp(p.o * 1.5))})"></div>` : "";

const PHONE_SVG = `<svg viewBox="0 0 24 24" width="18" height="18"><path fill="#fff" d="M6.6 10.8a15.1 15.1 0 0 0 6.6 6.6l2.2-2.2c.3-.3.7-.4 1-.2 1.1.4 2.3.6 3.6.6.6 0 1 .4 1 1V20c0 .6-.4 1-1 1A17 17 0 0 1 3 4c0-.6.4-1 1-1h3.5c.6 0 1 .4 1 1 0 1.3.2 2.5.6 3.6.1.3 0 .7-.2 1z"/></svg>`;
RESULTS.phone = [{ title: "Call +31 6 12345678", sub: "Phone number", icon: `<div class="ic round" style="background:#1f9d55">${PHONE_SVG}</div>` }];
RESULTS.url = [{ title: "searchlauncher.eu", sub: "Open in browser", icon: icon("globe") }];

// Web pages shown in the built-in browser, 360x736. ads: [p, p] blocking progress per ad.
export function page(kind, ads = [0, 0]) {
  if (kind === "wiki") {
    const ad = (p, label) => {
      const collapse = easeInOut(clamp((p - 0.45) / 0.55));
      const stamp = clamp(p * 4);
      return `<div class="ad" style="height:${86 * (1 - collapse)}px;margin:${10 * (1 - collapse)}px 0;opacity:${1 - collapse}">
        <div class="adin"><b>${label}</b><span>Sponsored</span></div>
        ${p > 0 ? `<div class="stamp" style="opacity:${stamp};transform:rotate(-8deg) scale(${lerp(1.8, 1, easeOut(stamp))})">BLOCKED</div>` : ""}</div>`;
    };
    return `<div class="pg wiki"><div class="pstat"><span>9:41</span><span>▾ ▮</span></div>
      <div class="wbar"><span>☰</span><span class="wlogo">Wikipedia</span><span>⌕</span></div>
      <h1>Free software</h1><div class="wtabs"><u>Article</u><span>Talk</span></div>
      <p><b>Free software</b> is computer software distributed under terms that let users run it for any purpose, and study, change and share it.</p>
      ${ad(ads[0], "WIN A NEW PHONE!")}
      <p>Free software is a matter of liberty, not price: users are free to do what they want with their copies.</p>
      ${ad(ads[1], "Hot deals near you")}
      <p>The right to study and modify the source code is central to free software.</p>
      <p>Computer programs are deemed free if they give end users ultimate control over the software.</p></div>`;
  }
  if (kind === "osm") {
    return `<div class="pg osm"><div class="pstat"><span>9:41</span><span>▾ ▮</span></div>
      <div class="obar"><b>OpenStreetMap</b></div>
      <svg viewBox="0 0 360 690" width="360" height="690" style="position:absolute;top:46px;left:0">
        <rect width="360" height="690" fill="#f2efe9"/>
        <path d="M0 470 C80 430 140 520 220 480 S330 420 360 450 V690 H0Z" fill="#aad3df"/>
        <path d="M30 60 h110 v90 h-110z M210 140 h120 v70 h-120z M60 260 h80 v110 h-80z" fill="#c8e6b0"/>
        <path d="M0 220 L360 180 M120 0 L170 690 M0 380 C120 350 240 400 360 330 M260 0 L230 470" stroke="#fff" stroke-width="9" fill="none"/>
        <path d="M0 220 L360 180 M120 0 L170 690" stroke="#f7c27a" stroke-width="5" fill="none"/>
        <circle cx="185" cy="300" r="10" fill="#e3262b" stroke="#fff" stroke-width="3"/>
      </svg></div>`;
  }
  // video page
  return `<div class="pg vid"><div class="pstat" style="color:#eee"><span>9:41</span><span>▾ ▮</span></div>
    <div class="thumb"><div class="play"></div></div>
    <h2>lofi hip hop radio, beats to relax/study to</h2><div class="meta">Live · 31K watching</div>
    <div class="lines"><i style="width:80%"></i><i style="width:64%"></i><i style="width:72%"></i></div>
    <div class="thumb small"></div><div class="lines"><i style="width:70%"></i><i style="width:50%"></i></div></div>`;
}

const browserBar = (count) => `<div class="bbar"><span class="ph">Search anything…</span><span class="bi">${MIC}</span><span class="tabn">${count}</span><span class="bi">⋮</span></div>`;

// state: { pages: [{kind, x}], ads, touch, count }
function browserScreen(s) {
  const pages = (s.pages || [{ kind: "wiki", x: 0 }])
    .map((p) => `<div class="pwrap" style="transform:translateX(${p.x}px)">${page(p.kind, s.ads)}</div>`)
    .join("");
  return `<div class="screen" style="background:#000">${pages}${browserBar(s.count || 3)}<div class="handle"></div>${touch(s.touch)}</div>`;
}

// state: { tabsP (0..1 shrink of current page into its card), current: kind, touch }
const CARD = (i) => ({ x: 14 + i * 172 - 172, y: 92, w: 160, h: 327 });
function tabsScreen(s) {
  const kinds = ["wiki", "osm", "vid"];
  const titles = [["Free software", "en.wikipedia.org"], ["OpenStreetMap", "openstreetmap.org"], ["lofi hip hop radio", "youtube.com"]];
  const p = easeOut5(clamp(s.tabsP));
  const cards = kinds
    .map((k, i) => {
      const c = CARD(i);
      const hide = k === s.current && p < 1 ? 0 : 1;
      return `<div class="tcard" style="left:${c.x}px;top:${c.y}px;opacity:${hide}"><div class="tmini">${page(k)}</div>
        <div class="tclose">✕</div><div class="ttl"><b>${titles[i][0]}</b><span>${titles[i][1]}</span></div></div>`;
    })
    .join("");
  const ci = kinds.indexOf(s.current);
  const c = CARD(ci);
  const fly = p < 1 ? `<div class="pwrap" style="transform:translate(${lerp(0, c.x, p)}px,${lerp(0, c.y, p)}px) scale(${lerp(1, c.w / 360, p)});transform-origin:0 0;border-radius:${lerp(0, 30, p)}px;overflow:hidden">${page(s.current)}</div>` : "";
  return `<div class="screen" style="background:#141617;color:#e8eaed">
    <div class="status"><span>9:41</span><span>▾ ▮</span></div>
    <div class="thead" style="opacity:${p}"><span>3 tabs</span><span>Close all</span></div>
    <div style="opacity:${p}">${cards}
      <div class="chips">${FAVS.map((c) => `<div class="fav" style="background:${c}"></div>`).join("")}</div>
      <div class="bar"><span class="caret"></span><span class="ph">Search anything…</span><div class="baricons">${MIC}</div></div>
      ${keyboard(null)}</div>
    ${fly}
    ${p < 1 ? `<div style="opacity:${1 - p}">${browserBar(3)}</div>` : ""}
    <div class="handle"></div>${touch(s.touch)}</div>`;
}

const WALLS = [
  "linear-gradient(170deg,#2f5a45 0%,#1a2e24 50%,#0f1713 80%)",
  "linear-gradient(170deg,#5b4bb0 0%,#2d2466 50%,#16122f 80%)",
  "linear-gradient(170deg,#f0915a 0%,#a8434f 45%,#2b1630 80%)",
];
export const WALL = WALLS;
const APPS = [
  ["Calendar", "#3f7fe0"], ["Camera", "#5f6368"], ["Chrome", "#e8453c"], ["Clock", "#1a73e8"],
  ["Contacts", "#1e88e5"], ["Drive", "#fbbc04"], ["Files", "#34a853"], ["Gmail", "#ea4335"],
  ["Maps", "#2e9e4f"], ["Messages", "#1a73e8"], ["Phone", "#1f9d55"], ["Photos", "#f29900"],
  ["Play Store", "#01875f"], ["Settings", "#5f6368"], ["Spotify", "#1db954"], ["YouTube", "#e3262b"],
];
export const APP_LIST = APPS;

function clockWidget(t) {
  const h = (9 + 41 / 60) * 30, m = 41 * 6 + (t || 0) * 6;
  return `<svg viewBox="0 0 160 160" width="160" height="160"><circle cx="80" cy="80" r="76" fill="rgba(20,28,24,.75)"/>
    <text x="80" y="44" text-anchor="middle" font-size="34" font-weight="700" fill="#cfe9dc" font-family="Space Grotesk">12</text>
    <line x1="80" y1="80" x2="${80 + 38 * Math.sin((h * Math.PI) / 180)}" y2="${80 - 38 * Math.cos((h * Math.PI) / 180)}" stroke="#eef6f1" stroke-width="7" stroke-linecap="round"/>
    <line x1="80" y1="80" x2="${80 + 58 * Math.sin((m * Math.PI) / 180)}" y2="${80 - 58 * Math.cos((m * Math.PI) / 180)}" stroke="#7fd3a8" stroke-width="4" stroke-linecap="round"/>
    <circle cx="80" cy="80" r="6" fill="#7fd3a8"/></svg>`;
}
export function calendarWidget() {
  let cells = "";
  for (let i = 0; i < 35; i++) {
    const d = ((i + 27) % 31) + 1;
    cells += `<span class="${d === 3 && i > 4 ? "today" : ""}">${d}</span>`;
  }
  return `<div class="cal"><div class="calh"><b>October</b><span>‹ ›</span></div><div class="calg">${cells}</div></div>`;
}
export const CLOCK = clockWidget;

// state: { wallX, widgetP, drawerP, touch, kb, t }
function homeScreen(s) {
  const wx = s.wallX || 0;
  const walls = WALLS.map((w, i) => `<div class="wall" style="background:${w};transform:translateX(${(i - wx) * 360}px)"></div>`).join("");
  const wp = s.widgetP || 0;
  const widgets = wp > 0
    ? `<div class="wclock" style="transform:scale(${wp});opacity:${clamp(wp * 2)}">${clockWidget(s.t)}</div>
       <div class="wcal" style="transform:scale(${wp});opacity:${clamp(wp * 2)}">${calendarWidget()}</div>`
    : "";
  const dp = s.drawerP || 0;
  const drawer = dp > 0
    ? `<div class="drawer" style="top:${lerp(800, 36, dp)}px"><div class="dgrab"></div>${APPS.map(([n, c]) => `<div class="app"><i style="background:${c}">${n[0]}</i><span>${n}</span></div>`).join("")}</div>`
    : "";
  return `<div class="screen">${walls}${widgets}
    <div class="status"><span>9:41</span><span>▾ ▮</span></div>
    <div class="chips">${FAVS.map((c) => `<div class="fav" style="background:${c}"></div>`).join("")}</div>
    <div class="bar"><span class="caret"></span><span class="ph">Search anything…</span><div class="baricons">${MIC}</div></div>
    ${keyboard(null)}${drawer}<div class="handle"></div>${touch(s.touch)}</div>`;
}

export function phoneScreen(s) {
  if (s.mode === "browser") return browserScreen(s);
  if (s.mode === "tabs") return tabsScreen(s);
  if (s.mode === "home") return homeScreen(s);
  return searchScreen(s);
}

export const PHONE_CSS = `
.phone{position:absolute;left:0;top:0;width:360px;height:800px;transform-origin:0 0;border-radius:46px;background:#0b0c0d;
  box-shadow:0 0 0 9px #1c1f1e,0 0 0 10px #343937,0 60px 120px rgba(0,0,0,.45);}
.screen{position:absolute;inset:0;border-radius:44px;overflow:hidden;font-family:Roboto,system-ui,sans-serif;color:#e8eaed;--wall:linear-gradient(180deg,#22312a 0%,#16211c 40%,#111614 70%)}
.status{position:absolute;left:24px;right:24px;top:12px;display:flex;justify-content:space-between;font-size:13px;font-weight:500;color:#e8eaed}
.row{position:absolute;left:10px;right:10px;height:54px;display:flex;align-items:center;gap:14px;padding:0 10px;border-radius:14px;background:rgba(32,35,38,.92)}
.row.hl{background:rgba(58,64,70,.96)}
.row .txt{flex:1;display:flex;flex-direction:column;min-width:0}
.row b{font-weight:500;font-size:15px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
.row span{font-size:11.5px;color:#a8adb3;margin-top:2px}
.row .dots{color:#9aa0a6;font-size:18px}
.ic{width:32px;height:32px;border-radius:8px;display:flex;align-items:center;justify-content:center;font-weight:700;font-size:15px;color:#fff;flex:none}
.ic.round{border-radius:50%}
.chips{position:absolute;left:12px;right:12px;top:440px;height:28px;display:flex;gap:5px;justify-content:center;align-items:center}
.chip{width:28px;height:24px;border-radius:6px;font-size:7.5px;font-weight:800;color:#fff;display:flex;align-items:center;justify-content:center}
.fav{width:26px;height:26px;border-radius:50%;box-shadow:inset 0 0 0 3px rgba(255,255,255,.85)}
.bar{position:absolute;left:12px;right:12px;top:474px;height:40px;border-radius:20px;background:#2a2e31;display:flex;align-items:center;padding:0 14px;font-size:16px;gap:4px}
.bar .ph{color:#8d9399}
.bar .q{color:#f1f3f4;white-space:pre}
.qchip{background:#e3262b;color:#fff;border-radius:12px;padding:3px 9px;font-size:13px;font-weight:500;margin-right:6px}
.caret{display:inline-block;width:2px;height:20px;background:#7fd3a8}
.baricons{margin-left:auto;color:#c4c8cc;font-size:15px}
.kb{position:absolute;left:0;right:0;top:526px;height:250px;background:#1b1d1f;padding:10px 4px 0;display:flex;flex-direction:column;gap:9px}
.kr{display:flex;justify-content:center;gap:3px}
.key{position:relative;height:46px;border-radius:7px;background:#3b3e42;color:#eef0f1;font-size:21px;display:flex;align-items:center;justify-content:center}
.key i{position:absolute;right:4px;top:2px;font-size:9px;font-style:normal;color:#9aa0a6}
.key.fn{background:#2c2f32;font-size:15px}
.key.go{background:#7fd3a8;color:#0c2a1c;font-size:20px}
.key.down{background:#6c7177}
.pop{position:absolute;bottom:40px;left:50%;transform:translateX(-50%);width:48px;height:62px;border-radius:10px;background:#5f646a;font-size:30px;display:flex;align-items:flex-start;justify-content:center;padding-top:8px;box-shadow:0 6px 18px rgba(0,0,0,.45)}
.handle{position:absolute;bottom:9px;left:50%;width:110px;height:4px;margin-left:-55px;border-radius:2px;background:#d0d3d6}
.touch{position:absolute;width:44px;height:44px;border-radius:50%;background:rgba(255,255,255,.55);box-shadow:0 0 0 6px rgba(255,255,255,.22),0 4px 14px rgba(0,0,0,.3);z-index:20}
.pwrap{position:absolute;left:0;top:0;width:360px;height:736px;overflow:hidden}
.pg{position:absolute;inset:0;background:#fff;color:#202122;overflow:hidden}
.pstat{height:36px;display:flex;justify-content:space-between;align-items:center;padding:0 24px;font-size:13px;font-weight:500;color:#202122}
.wbar{display:flex;align-items:center;gap:14px;padding:6px 16px;background:#f8f9fa;border-bottom:1px solid #ddd;font-size:20px;color:#444}
.wlogo{flex:1;font-family:Georgia,serif;font-size:21px;letter-spacing:1px;color:#202122}
.wiki h1{font-family:Georgia,serif;font-weight:400;font-size:28px;margin:16px 16px 6px}
.wtabs{display:flex;gap:16px;margin:0 16px 8px;padding-bottom:6px;border-bottom:1px solid #c8ccd1;font-size:13px;color:#555}
.wtabs u{text-decoration:none;border-bottom:2px solid #202122;padding-bottom:5px;color:#202122}
.wiki p{margin:8px 16px;font-family:Georgia,serif;font-size:14.5px;line-height:1.5}
.ad{position:relative;margin:10px 0;overflow:hidden}
.adin{margin:0 16px;height:86px;border-radius:8px;background:repeating-linear-gradient(45deg,#ffe066,#ffe066 12px,#ffd43b 12px,#ffd43b 24px);display:flex;flex-direction:column;justify-content:center;padding:0 16px;font-family:Roboto}
.adin b{font-size:17px;color:#7a2d00}.adin span{font-size:11px;color:#7a5200;margin-top:3px}
.stamp{position:absolute;left:50%;top:50%;margin:-22px 0 0 -88px;width:176px;height:44px;border:4px solid #e3262b;border-radius:8px;color:#e3262b;font-family:"Space Grotesk";font-weight:700;font-size:26px;display:flex;align-items:center;justify-content:center;background:rgba(255,255,255,.9)}
.obar{height:46px;display:flex;align-items:center;padding:0 16px;font-size:15px;background:#fff;border-bottom:1px solid #ddd}
.vid{background:#0f0f0f;color:#f1f1f1}
.thumb{margin-top:6px;height:202px;background:linear-gradient(135deg,#ff8a65,#ba68c8 50%,#4a148c);position:relative}
.thumb.small{height:120px;margin:16px;border-radius:10px;background:linear-gradient(135deg,#4db6ac,#1a237e)}
.play{position:absolute;left:50%;top:50%;margin:-20px 0 0 -30px;width:60px;height:40px;border-radius:12px;background:#e3262b}
.play:after{content:"";position:absolute;left:24px;top:12px;border-left:14px solid #fff;border-top:8px solid transparent;border-bottom:8px solid transparent}
.vid h2{font-family:Roboto;font-size:17px;font-weight:500;margin:12px 16px 4px;line-height:1.3}
.meta{font-family:Roboto;font-size:12px;color:#aaa;margin:0 16px}
.lines{margin:14px 16px;display:flex;flex-direction:column;gap:9px}.lines i{height:10px;border-radius:5px;background:#2a2a2a}
.bbar{position:absolute;left:0;right:0;top:736px;height:64px;background:#111315;display:flex;align-items:center;gap:14px;padding:0 20px 14px;font-size:16px;color:#d6d9dc}
.bbar .ph{flex:1;color:#9aa0a6}
.tabn{width:20px;height:20px;border:2px solid #d6d9dc;border-radius:5px;font-size:11px;display:flex;align-items:center;justify-content:center}
.thead{position:absolute;left:20px;right:20px;top:52px;display:flex;justify-content:space-between;font-size:15px}
.tcard{position:absolute;width:160px}
.tmini{position:relative;width:160px;height:327px;border-radius:14px;overflow:hidden;box-shadow:0 0 0 2px #3a3f44}
.tmini .pg{width:360px;height:736px;transform:scale(.4444);transform-origin:0 0}
.tclose{position:absolute;right:6px;top:6px;width:24px;height:24px;border-radius:50%;background:#202124;color:#fff;font-size:12px;display:flex;align-items:center;justify-content:center}
.ttl{display:flex;flex-direction:column;margin-top:6px;font-size:11px}.ttl b{font-weight:500;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}.ttl span{color:#9aa0a6;font-size:10px}
.wall{position:absolute;inset:0}
.wclock{position:absolute;left:100px;top:56px;transform-origin:50% 50%}
.wcal{position:absolute;left:20px;top:236px;width:320px;transform-origin:50% 50%}
.cal{background:rgba(20,28,24,.78);border-radius:22px;padding:12px 16px;color:#e8eaed}
.calh{display:flex;justify-content:space-between;font-size:15px;margin-bottom:6px}
.calg{display:grid;grid-template-columns:repeat(7,1fr);gap:3px 0;text-align:center;font-size:12px;color:#c4c9cd}
.calg .today{background:#7fd3a8;color:#0c2a1c;border-radius:50%;font-weight:700}
.drawer{position:absolute;left:0;right:0;bottom:0;background:rgba(22,26,24,.97);border-radius:28px 28px 0 0;display:grid;grid-template-columns:repeat(4,1fr);align-content:start;gap:18px 0;padding:40px 10px 0;z-index:10}
.dgrab{position:absolute;left:50%;top:12px;width:40px;height:4px;margin-left:-20px;border-radius:2px;background:#666}
.app{display:flex;flex-direction:column;align-items:center;gap:6px;font-size:11.5px;color:#dfe3e6}
.app i{width:50px;height:50px;border-radius:50%;font-style:normal;font-weight:700;font-size:20px;color:#fff;display:flex;align-items:center;justify-content:center}
`;

// Place a 360x800 phone so that phone-point (cx,cy) lands at stage point (x,y) at scale s.
export const camera = (x, y, cx, cy, s, rot = 0) =>
  `transform:translate(${x - cx * s}px,${y - cy * s}px) scale(${s}) rotate(${rot}deg)`;
