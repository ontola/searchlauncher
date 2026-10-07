// Runs in an isolated extension world. Only top-level icon metadata crosses the native bridge.
(() => {
  let last = "";
  let timer;
  function report() {
    const links = [...document.querySelectorAll('link[rel][href]')]
      .filter(link => link.relList.contains('icon') || link.relList.contains('apple-touch-icon'))
      .sort((a, b) => {
        const size = link => link.sizes?.value === 'any' ? 192 : parseInt(link.sizes?.value, 10) || 32;
        return size(b) - size(a);
      })
      .map(link => link.href)
      .filter(url => /^(https?:|data:image\/)/i.test(url) && url.length <= 350000);
    const icons = [...new Set(links)].slice(0, 8);
    icons.push(new URL('/favicon.ico', location.origin).href);
    const message = { url: location.href, icons };
    const key = JSON.stringify(message);
    if (key === last) return;
    last = key;
    browser.runtime.sendNativeMessage('site_icons', message).catch(() => {});
  }
  function schedule() { clearTimeout(timer); timer = setTimeout(report, 100); }
  report();
  addEventListener('pageshow', schedule);
  new MutationObserver(schedule).observe(document.head || document.documentElement, {
    subtree: true, childList: true, attributes: true, attributeFilter: ['href', 'rel', 'sizes']
  });
})();
