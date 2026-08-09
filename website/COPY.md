# SearchLauncher website copy — pitch iterations

Working notes for the marketing site. Goal: cover every major feature and USP, then ship one coherent page voice.

---

## Audience

- People who type faster than they swipe (Raycast / Alfred / Spotlight refugees on Android)
- Privacy-minded users (F-Droid, open source, ad-blocking browser)
- Minimalists who want wallpaper-first homescreens
- Power users who live in shortcuts (`y cats`, `g …`, timers, snippets)
- Anyone who wants the search bar without ditching their current launcher (widget mode)

---

## Pitch directions (v1 — raw)

### A. Command palette
> Raycast for your pocket. Type once, do anything.

Strength: clear category. Risk: alienates non-Mac users who don’t know Raycast.

### B. Anti-scrolling
> Stop scrolling. Start executing.

Strength: punchy, existing brand line. Weak alone — doesn’t name the product job.

### C. Keyboard-first home
> Your homescreen has a keyboard now.

Strength: concrete, memorable, product-true. Best hero candidate.

### D. Search everything
> Search everything on your phone — and the web.

Strength: feature-complete. Flat as a headline; better as a section.

### E. Launcher + browser
> One bar for apps, contacts, settings, and a private ad-blocking browser.

Strength: unique combo USP. Dense for a hero.

---

## Pitch directions (v2 — refined)

| Angle | Headline | Subhead |
|---|---|---|
| **C+** | Your homescreen has a keyboard. | SearchLauncher is the keyboard-first Android launcher. Type to open apps, call contacts, run shortcuts, start timers, or jump into a private ad-blocking browser — without hunting through icons. |
| **A+** | Raycast energy. Android native. | A pocket command palette: fuzzy search, custom aliases, snippets, smart actions, and tabs that live under your thumb. Free and open source. |
| **E+** | Launch. Search. Browse. One bar. | Replace icon grids with instant search — and keep browsing in an ad-blocking browser that opens from the same home screen. |
| **B+** | Type faster than you swipe. | Favorites, learned ranking, and a always-ready keyboard turn your phone into something you command, not something you browse. |

**Chosen spine:** C+ as hero, A+ as social/proof line, E+ as browser section lead, B+ as closing CTA.

Privacy claim discipline: never absolute “zero tracking.” Prefer: on-device index, no SearchLauncher cloud, optional suggestions & crash reports, open source, ads blocked in-browser by default.

---

## Final page copy (v4 — ship)

Refinements from v3: tighter hero support (one sentence), sharper section leads, concrete command examples as the visual language of the product, store URLs locked.

### Meta
- **Title:** SearchLauncher — Your homescreen has a keyboard
- **Description:** Keyboard-first Android launcher. Search apps, contacts, settings, snippets, and the web. Built-in ad-blocking browser with tabs. Free and open source.

### Links
- Releases: `https://github.com/ontola/searchlauncher/releases/latest`
- GitHub: `https://github.com/ontola/searchlauncher`
- Privacy: `privacy.html`
- Contact: `mailto:info@ontola.io`
- Note: Play Store and F-Droid listings are not live yet; site CTAs use GitHub Releases.

### Nav
SearchLauncher · Features · Privacy · Download

### Hero
- **Brand:** SearchLauncher
- **Headline:** Your homescreen has a keyboard.
- **Support:** Type to launch apps, call contacts, run shortcuts, or open the web — from one always-ready bar.
- **Primary CTA:** Download SearchLauncher (GitHub Releases)
- **Secondary CTA:** View source
- **Secondary line:** Free · Open source · Android 10+
- **Alt micro-line (optional under CTA):** Use it as your launcher, or as a widget on any home screen.

### Proof strip
> Finally, a launcher for people who type faster than they swipe. It’s the power of Raycast, now in your pocket.

### Section 1 — Search everything
**Eyebrow / kicker:** Search
**Headline:** If it’s on your phone, type it.
**Support:** Fuzzy search across apps, shortcuts, contacts, and settings. Ranking learns what you pick — so the next time is faster.
**Proof points:**
- Apps and app shortcuts
- Contacts — call, text, email, WhatsApp, Signal, Telegram, and more
- System settings and actions: flashlight, dark mode, rotation lock, battery saver…
- Voice search when your hands are full

### Section 2 — Shortcuts & snippets
**Eyebrow:** Commands
**Headline:** Aliases for how you actually work.
**Support:** Built-in shortcuts like `y` for YouTube, `g` for Google, `c` for ChatGPT, `m` for Maps — or invent your own. Save snippets and copy them from the same bar.
**Command rail (show as typed examples):**
- `y cats` → YouTube
- `g weather` → Google
- `10m rice` → named timer
- Your IBAN snippet → copy

### Section 3 — Smart parsing
**Eyebrow:** Smart input
**Headline:** The bar knows what you meant.
**Support:** Paste a number, email, URL, or equation. SearchLauncher offers the right action — call, text, email, open, or copy the answer.
**Moments:** `1+1` · phone number · email · URL · `2h` / `20sec`

### Section 4 — Browser
**Eyebrow:** Browser
**Headline:** Browse from the same bar.
**Support:** Web results open in SearchLauncher’s built-in browser. Ads and trackers blocked by default. Tabs live under your thumb — swipe the search bar sideways to switch, or up for live previews. From the home screen too.
**Proof points:**
- Ad & tracker blocking on by default
- Private browsing in an isolated process — no history written
- Bookmarks and searchable history, with site icons
- Optional: set as your default browser

### Section 5 — Home screen
**Eyebrow:** Home
**Headline:** Reclaim the screen.
**Support:** When you’re not searching, the UI steps aside. Swipe a wallpaper album, host widgets, keep favorites and recents above the bar.
**Proof points:**
- Swipe wallpapers; theme colors pulled from your background
- Widgets you can resize, reorder, and hide with a tap on the background
- Favorites you pin; history that follows how you work
- Gestures for notifications, quick settings, and the app drawer
- OLED true-black when you want it

### Section 6 — Fits your setup
**Eyebrow:** Your way
**Headline:** Full launcher — or just the search bar.
**Support:** Set SearchLauncher as default, or keep your current launcher and add the widget. Same power either way. Export and import a backup when you switch phones.

### Section 7 — Trust
**Eyebrow:** Privacy
**Headline:** Open by design. Yours stays yours.
**Support:** Indexing and ranking run on your device. There is no SearchLauncher cloud for your queries. Free, MIT-licensed, auditable on GitHub.
**Proof points:**
- On-device AppSearch index — your catalog never phones home
- Web searches go straight to the provider you chose
- Suggestions and crash reports are optional
- In-browser ads and trackers blocked by default
- MIT-licensed source on GitHub

### Closing CTA
**Headline:** Stop scrolling. Start executing.
**Support:** Free on GitHub. Android 10 or newer.
**CTA:** Download latest release

### Footer
Made with love by Ontola · GitHub · Privacy · Releases · Source · info@ontola.io

### Alternate lines bank (unused but strong)
- Stop hunting icons.
- Launch. Search. Browse. One bar.
- Raycast energy. Android native.
- Type faster than you swipe.
---

## Feature coverage checklist

| Feature / USP | Where it lands |
|---|---|
| Keyboard-first homescreen | Hero |
| Launcher *or* widget | Hero + Fits your setup |
| Apps / shortcuts / contacts / settings | Search everything |
| Fuzzy + learned ranking | Search everything |
| Voice search | Search everything |
| Custom aliases (`y`, `g`, …) | Shortcuts & snippets |
| Snippets | Shortcuts & snippets |
| Timers | Shortcuts + Smart parsing |
| Calculator / phone / email / URL | Smart parsing |
| Contact deep links (WhatsApp etc.) | Search everything |
| Ad-blocking browser | Browser |
| Tabs from home search bar | Browser |
| Private/incognito isolated process | Browser |
| Bookmarks & searchable history | Browser |
| Default browser option | Browser |
| Wallpaper album | Home screen |
| Widgets + toggle visibility | Home screen |
| Favorites & history row | Home screen |
| Notification / QS / drawer gestures | Home screen |
| Theme / OLED | Home screen |
| Backup export/import | Fits your setup |
| Free, MIT, Play + F-Droid | Hero + Trust + CTA |
| On-device / optional network | Trust |
| Raycast metaphor | Proof quote |

---

## Rejected / deferred lines

- “Zero Tracking. No ads. No analytics. No telemetry.” — too absolute vs optional GlitchTip + suggestions; replaced with accurate opt-in framing.
- Heavy Nova/Niagara dunking — no in-repo comparison copy; stay product-positive.
- Feature-dump hero — browser, timers, widgets stay out of the first viewport.
