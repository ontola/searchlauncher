// Direction B: bright and bouncy. Light paper background, the results pop out
// of the phone as floating cards with springy motion.
import { prog, lerp, easeOut, easeOut5, easeIn, spring, keys, typing, phoneScreen, camera, RESULTS, LOGO, clamp } from "./lib.js";

export const DURATION = 7.2;

export const CSS = `
.bgB{position:absolute;inset:0;background:#f3f6f2}
.dotsB{position:absolute;inset:-100px;background-image:radial-gradient(rgba(16,32,24,.13) 2px,transparent 2.5px);background-size:44px 44px}
.cap{position:absolute;left:0;right:0;text-align:center;font-weight:700;font-size:76px;letter-spacing:-2px;color:#102018;white-space:nowrap}
.cap em{font-style:normal;color:#0f7a5a}
.card{position:absolute;left:0;top:0;width:340px;height:54px;transform-origin:50% 50%;font-family:Roboto;color:#e8eaed}
.card .row{left:0;right:0;top:0;box-shadow:0 18px 40px rgba(16,32,24,.28)}
.tag{position:absolute;left:10px;top:-30px;font-family:"Space Grotesk";font-weight:600;font-size:15px;color:#fff;background:#0f7a5a;border-radius:20px;padding:4px 12px}
.sticker{position:absolute;font-family:"Space Grotesk";font-weight:700;color:#fff;border-radius:999px;padding:18px 34px;font-size:44px;box-shadow:0 14px 30px rgba(16,32,24,.25);white-space:nowrap}
`;

const rowCard = (r, label) =>
  `<div class="row">${r.icon}<div class="txt"><b>${r.title}</b><span>${r.sub}</span></div><div class="dots">⋮</div></div>${label ? `<div class="tag">${label}</div>` : ""}`;

const caption = (t, html, tin, tout, y = 60) => {
  if (t < tin || t > tout + 0.3) return "";
  const p = spring(t - tin, 2.2, 0.5);
  const o = easeIn(prog(t, tout, tout + 0.2));
  return `<div class="cap" style="top:${y}px;opacity:${clamp(p * 2) * (1 - o)};transform:translateY(${(1 - p) * 50 - o * 40}px) scale(${lerp(0.9, 1, clamp(p))})">${html}</div>`;
};

export function render(t) {
  const phoneScale = 0.98;
  const px = 960, py = 600;
  const pop = spring(t - 0.05, 1.7, 0.45);
  let st;
  const beat = t < 3.25 ? 1 : 2;
  if (beat === 1) {
    const ty = typing(t, "mar", 0.75, 8);
    st = {
      query: ty.shown, pressed: ty.pressed,
      results: t > 0.9 ? RESULTS.mar : [],
      rowP: (i, n) => easeOut(prog(t, 0.9 + (n - 1 - i) * 0.04, 1.1 + (n - 1 - i) * 0.04)),
    };
  } else {
    const ty = typing(t, "lofi", 3.4, 12);
    st = {
      chip: "YouTube", query: ty.shown, pressed: ty.pressed,
      results: t > 3.6 ? RESULTS.lofi : [],
      rowP: (i, n) => easeOut(prog(t, 3.6 + (n - 1 - i) * 0.035, 3.8 + (n - 1 - i) * 0.035)),
    };
  }
  st.caret = Math.floor(t * 2.5) % 2 === 0 || !!st.pressed;
  st.wallpaper = "linear-gradient(180deg,#2a4a3b 0%,#1a2b23 45%,#121815 75%)";

  // phone position: pops in, bounces on key presses, shrinks away at the end
  const bump = st.pressed ? 0.012 : 0;
  const away = easeIn(prog(t, 5.0, 5.35));
  const s = phoneScale * lerp(0.4, 1, pop) * (1 + bump) * (1 - away * 0.9);
  const phone = `<div class="phone" style="${camera(px, py + away * 40, 180, 400, s)}">${phoneScreen(st)}</div>`;

  // cards that leave the phone: [result index, target x, target y, rotation, label]
  const flights = beat === 1
    ? [[1, 420, 380, -5, "App shortcut"], [2, 420, 700, 4, "Contact"], [3, 1500, 380, 5, "Browser history"], [4, 1500, 700, -4, "Setting"]]
    : [[1, 1500, 330, 4, ""], [2, 1500, 520, -3, ""], [3, 1500, 710, 5, ""]];
  const t0 = beat === 1 ? 1.4 : 3.95;
  const tBack = beat === 1 ? 2.95 : 4.85;
  const res = beat === 1 ? RESULTS.mar : RESULTS.lofi;
  const n = res.length;
  let cards = "";
  flights.forEach(([i, tx, ty, rot, label], k) => {
    const start = t0 + k * 0.08;
    if (t < start) return;
    const out = spring(t - start, 1.6, 0.5);
    const back = easeIn(prog(t, tBack + k * 0.03, tBack + 0.22 + k * 0.03));
    // row centre in stage coordinates
    const rowY = 436 - (n - i) * 56 + 27;
    const sx = px + (180 - 180) * s, sy = py + (rowY - 400) * s;
    const p = out * (1 - back);
    const cx = lerp(sx, tx, p), cy = lerp(sy, ty, p);
    const sc = lerp(s, 1.55, p);
    cards += `<div class="card" style="transform:translate(${cx - 170}px,${cy - 27}px) scale(${sc}) rotate(${rot * p}deg);opacity:${clamp(p * 3)}">${rowCard(res[i], label)}</div>`;
  });

  // web stickers in beat 2
  let stickers = "";
  if (beat === 2) {
    const items = [["YouTube", "#e3262b", 400, 330, -6], ["Google", "#3f7fe0", 330, 520, 4], ["Maps", "#2e9e4f", 470, 700, -3], ["+ your own", "#102018", 380, 880, 3]];
    items.forEach(([label, c, x, y, r], k) => {
      const a = 4.0 + k * 0.1;
      if (t < a) return;
      const p = spring(t - a, 2.0, 0.42) * (1 - easeIn(prog(t, 4.85 + k * 0.03, 5.05 + k * 0.03)));
      stickers += `<div class="sticker" style="left:${x - 120}px;top:${y - 45}px;background:${c};transform:scale(${p}) rotate(${r}deg)">${label}</div>`;
    });
  }

  let caps = "";
  caps += caption(t, "One search bar.", 0.3, 1.3);
  caps += caption(t, "Everything on <em>your phone</em>.", 1.45, 3.0);
  caps += caption(t, "And all of <em>the web</em>.", 3.4, 4.85);

  // end card: green circle wipe
  let end = "";
  if (t > 5.15) {
    const r = easeOut5(prog(t, 5.15, 5.6)) * 1300;
    const p = spring(t - 5.4, 1.8, 0.5);
    const p2 = easeOut5(prog(t, 5.6, 5.95));
    const p3 = easeOut5(prog(t, 5.8, 6.15));
    end = `<div class="layer" style="background:#0f7a5a;clip-path:circle(${r}px at 960px 600px)">
      <div class="abs" style="left:0;right:0;top:290px;display:flex;flex-direction:column;align-items:center;color:#fff">
        <div class="logo" style="width:190px;height:190px;transform:scale(${p})">${LOGO}</div>
        <div style="margin-top:34px;font-size:118px;font-weight:700;letter-spacing:-3px;opacity:${p2};transform:translateY(${(1 - p2) * 40}px)">SearchLauncher</div>
        <div style="margin-top:12px;font-family:'Source Sans 3';font-size:40px;color:#d5ece2;opacity:${p3};transform:translateY(${(1 - p3) * 30}px)">Search everything. Free, no ads, open source.</div>
      </div></div>`;
  }

  return `<div class="bgB"></div><div class="dotsB" style="transform:translate(${-t * 12}px,${-t * 20}px)"></div>
    ${phone}${stickers}${cards}${caps}${end}`;
}
