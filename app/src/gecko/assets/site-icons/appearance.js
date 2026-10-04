// Isolated, top-level metadata only. Never sample page pixels or watch scroll events.
(() => {
  let last = "";
  let timer;
  let header;
  let headerObserver;
  const mediaQueries = new Map();
  const canvas = document.createElement("canvas");
  canvas.width = canvas.height = 1;
  const ctx = canvas.getContext("2d", { willReadFrequently: true });
  const hex = () => "#" + [...ctx.getImageData(0, 0, 1, 1).data].slice(0, 3)
    .map(value => value.toString(16).padStart(2, "0")).join("");
  function watchMedia(query) {
    if (!mediaQueries.has(query)) {
      const media = matchMedia(query);
      media.addEventListener("change", schedule);
      mediaQueries.set(query, media);
    }
    return mediaQueries.get(query).matches;
  }
  function headerTheme() {
    // No pixel sampling, network request or scroll listener. Only a broad, solid main header
    // near the document top can supply a fallback; article headers and overlays cannot.
    if (!header || !header.isConnected) {
      headerObserver?.disconnect();
      header = [...document.querySelectorAll('header, nav, [role="banner"]')].find(element => {
        if (element.closest('article, main, aside, footer, dialog, [role="dialog"]')) return false;
        const rect = element.getBoundingClientRect();
        const style = getComputedStyle(element);
        const top = rect.top + (style.position === "fixed" ? 0 : scrollY);
        if (top < -1 || top > 180 || rect.width < innerWidth * 0.85 ||
          rect.height < 32 || rect.height > 180 || style.visibility !== "visible" ||
          Number(style.opacity) < 1 || style.backgroundImage !== "none") return false;
        ctx.clearRect(0, 0, 1, 1);
        ctx.fillStyle = style.backgroundColor;
        ctx.fillRect(0, 0, 1, 1);
        return ctx.getImageData(0, 0, 1, 1).data[3] === 255;
      });
      if (header) {
        headerObserver = new MutationObserver(schedule);
        headerObserver.observe(header, { attributes: true, attributeFilter: ["class", "style"] });
      }
    }
    if (!header) return null;
    const style = getComputedStyle(header);
    if (style.backgroundImage !== "none" || style.display === "none") return null;
    ctx.clearRect(0, 0, 1, 1);
    ctx.fillStyle = style.backgroundColor;
    ctx.fillRect(0, 0, 1, 1);
    return ctx.getImageData(0, 0, 1, 1).data[3] === 255 ? hex() : null;
  }
  function report() {
    // CSS Canvas also covers pages using color-scheme without an explicit body background.
    const probe = document.createElement("span");
    probe.style.cssText = "display:none;color:Canvas";
    document.documentElement.append(probe);
    const base = getComputedStyle(probe).color;
    probe.remove();
    ctx.fillStyle = "white";
    ctx.fillRect(0, 0, 1, 1);
    for (const color of [base, getComputedStyle(document.documentElement).backgroundColor,
      document.body && getComputedStyle(document.body).backgroundColor]) {
      if (!color) continue;
      ctx.fillStyle = color;
      ctx.fillRect(0, 0, 1, 1);
    }
    const background = hex();
    let theme = null;
    let declaredTheme = false;
    const usedQueries = new Set(["(prefers-color-scheme: dark)"]);
    // Keep listening to all declared media queries, not only the currently matching tag.
    const metas = [...document.querySelectorAll('meta[name="theme-color" i]')];
    for (const meta of metas) {
      const query = meta.media;
      if (query) { usedQueries.add(query); watchMedia(query); }
    }
    for (const meta of metas) {
      if (meta.media && !watchMedia(meta.media)) continue;
      const color = meta.content.trim();
      if (!color || !CSS.supports("color", color)) continue;
      ctx.clearRect(0, 0, 1, 1);
      ctx.fillStyle = color;
      ctx.fillRect(0, 0, 1, 1);
      if (ctx.getImageData(0, 0, 1, 1).data[3] === 0) continue;
      ctx.fillStyle = background;
      ctx.fillRect(0, 0, 1, 1);
      ctx.fillStyle = color;
      ctx.fillRect(0, 0, 1, 1);
      theme = hex();
      declaredTheme = true;
      // Some sites (including NOS) keep a generic white theme-color in their dark layout.
      // Honor explicit media-qualified colors, but don't put white chrome around a dark page.
      const channels = background.match(/[0-9a-f]{2}/g).map(value => {
        const c = parseInt(value, 16) / 255;
        return c <= 0.04045 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4;
      });
      const luminance = channels[0] * 0.2126 + channels[1] * 0.7152 + channels[2] * 0.0722;
      if (!meta.media && theme === "#ffffff" && luminance < 0.18) theme = null;
      break;
    }
    if (!declaredTheme) theme = headerTheme();
    for (const [query, media] of mediaQueries) {
      if (!usedQueries.has(query)) {
        media.removeEventListener("change", schedule);
        mediaQueries.delete(query);
      }
    }
    const message = { url: location.href, background, theme };
    const key = JSON.stringify(message);
    if (key === last) return;
    last = key;
    browser.runtime.sendNativeMessage("page_appearance", message).catch(() => {});
  }
  function schedule() { cancelAnimationFrame(timer); timer = requestAnimationFrame(report); }
  const rootObserver = new MutationObserver(schedule);
  rootObserver.observe(document.documentElement, { attributes: true });
  if (document.body) rootObserver.observe(document.body, { attributes: true });
  if (document.head) new MutationObserver(schedule).observe(document.head, {
    subtree: true, childList: true, characterData: true, attributes: true,
    attributeFilter: ["content", "media", "name", "href", "rel", "disabled", "class", "style"]
  });
  watchMedia("(prefers-color-scheme: dark)");
  for (const event of ["pageshow", "popstate", "hashchange", "resize"]) addEventListener(event, schedule);
  // External stylesheets may finish after document_end.
  document.addEventListener("load", event => { if (event.target.tagName === "LINK") schedule(); }, true);
  document.addEventListener("transitionend", event => {
    if (event.target === document.body || event.target === document.documentElement) schedule();
  }, true);
  report();
})();
