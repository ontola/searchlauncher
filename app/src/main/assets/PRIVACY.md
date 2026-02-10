# Privacy Policy

**Last updated: Feb 10th 2026**

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
