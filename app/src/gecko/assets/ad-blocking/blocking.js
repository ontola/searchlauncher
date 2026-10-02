// Firefox supports an asynchronous blocking response, so the shared native matcher stays authoritative.
browser.webRequest.onBeforeRequest.addListener(async details => {
  // Match the existing browser: never block a deliberate top-level navigation.
  if (details.type === "main_frame") return {};
  try {
    let pageUrl = details.documentUrl || details.originUrl || "";
    if (details.tabId >= 0) {
      const tab = await browser.tabs.get(details.tabId);
      pageUrl = tab.url || pageUrl;
    }
    let timer;
    try {
      const blocked = await Promise.race([
        browser.runtime.sendNativeMessage("ad_blocking", { url: details.url, pageUrl }),
        new Promise(resolve => { timer = setTimeout(() => resolve(false), 1000); })
      ]);
      return { cancel: blocked === true };
    } finally {
      clearTimeout(timer);
    }
  } catch (_) {
    return {};
  }
}, { urls: ["http://*/*", "https://*/*"] }, ["blocking"]);
