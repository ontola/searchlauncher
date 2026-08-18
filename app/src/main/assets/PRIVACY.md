# Privacy Policy

**Last updated: August 2026**

This Privacy Policy describes how Ontola, the company behind SearchLauncher ("we", "us", or "our") collects, uses, and shares information when you use our mobile application.

## Information Collection and Use

### Search Shortcuts & Suggestions

SearchLauncher includes **search shortcuts** — custom keywords (e.g., "g" for Google, "y" for YouTube) that let you quickly search third-party websites. When you use a search shortcut and press enter, your **search query is sent directly to the corresponding third-party service** (such as Google, YouTube, DuckDuckGo, Wikipedia, etc.). This is a user-initiated action — it only happens when you explicitly perform a search.

Additionally, some shortcuts provide **live search suggestions** as you type. When enabled, your partial query is sent to the suggestion provider (e.g., Google's suggestion API) in real time to display autocomplete results. Unlike shortcuts, **suggestions are sent automatically** as you type, without requiring you to press enter.

**What is shared with third parties when using shortcuts or suggestions:**
- Your search query text (or partial query for suggestions)
- Standard HTTP request metadata (IP address, user agent, etc.)

These requests are made directly from your device to the third-party service. We do not proxy, intercept, or store this data on our servers. The third-party service's own privacy policy governs how they handle your data.

**You are in control:** Search suggestions can be disabled at any time in **Settings → Privacy**. When disabled, no autocomplete queries are sent to external services as you type. Search shortcuts themselves remain available since they only send data when you explicitly perform a search.

### Built-in Browser

SearchLauncher can open web results in its own browser instead of handing them to another app. Everything that browser remembers stays on your device:

- **History and bookmarks** are stored locally so you can search them from the search bar. You can clear history and remove bookmarks in **Settings → Browser**.
- **Site icons** are saved from the pages you visit; they are not requested from any third-party icon service.
- **Cookies and site data** are handled by Android's system WebView. A private window keeps its cookies and storage in a separate area and wipes them when you close it.
- **Microphone, camera, location, Bluetooth, and similar device access** is only granted after a site asks and you allow it. That choice is stored on the device for that site (or kept only in memory in a private window). Matching system permissions are requested if the app does not already have them.

Pages you open naturally contact the servers of the sites you are visiting, exactly as any browser would. Those sites' own privacy policies apply.

**Ad and tracker blocking** is on by default. To do it, the app downloads a public blocklist (the StevenBlack unified hosts list) about once a week and stores it on your device. That download tells GitHub your IP address, and nothing about what you browse — the matching happens entirely on your device. You can turn blocking off, or allow it per site, in **Settings → Browser**.

### Error Logs and Diagnostics
We collect error logs and crash reports to identify and fix issues in the application. This data helps us improve the stability and performance of the app.

We use **GlitchTip** (a Sentry-compatible service) to collect this information. The data collected involves:
- Stack traces of crashes
- Device information (model, OS version)
- App version

We **do not** collect:
- Personally Identifiable Information (PII) such as your name, email, or phone number.
- Search queries.
- Any other user inputs.

## User Consent
- **Error logging** is optional. You will be asked for permission to enable this feature when you first open the app. You can change your preference at any time in **Settings → Privacy**.
- **Search suggestions** are enabled by default but can be disabled at any time in **Settings → Privacy**.

## Contact Us
If you have any questions about this Privacy Policy, please contact us at info@ontola.io.
