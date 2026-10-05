# Browser keyboard entrance

The search overlay independently collected four appearance preferences with default initial values. On each new activity it could render and begin its keyboard entrance in the system/default palette before the saved dark mode, OLED mode and colors arrived. ThemePreferences now shares a complete, eagerly observed snapshot across the home, browser and overlay. On cold start, composition waits for the real snapshot instead of drawing a placeholder palette. Keyboard entrance starts only once the theme is available; later appearance changes remain live.

A separate flash was visible behind the moving search field: hiding the browser toolbar resized Gecko's viewport, exposing a white region before the page repainted. Preserve the hidden toolbar's measured height while the translucent search overlay is open. Fullscreen, webpage IME and picture-in-picture retain their existing sizing behavior.

Validation: Android 15 ARM64 Phone_A35 emulator, optimized Gecko APK, built-in keyboard, explicit app dark mode and OLED enabled. Recorded browser-search opening at 5x animator duration, inspected frames at 15 fps, then restored animation scale to 1. Contact sheets show the bottom 1000 pixels. The earlier build's white strip is visible in before.png; after.png keeps the keyboard and search field dark throughout and removes the strip. This is emulator evidence, not a Xiaomi hardware measurement.

Vimeo investigation: vimeo.com's public homepage trailer (player video 1070507470) played after allowing protected media in this emulator. The reported phone-specific rights error is not reproduced or fixed by this change.
