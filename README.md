# SearchLauncher

Android app that lets you search everything on your phone, and more.

## Features

- **Search everything on your phone** - Apps, their shortcuts, device settings
- **Search everything on the web** - Youtube, google, bing, maps, spotify... Or add your own custom shortcuts!
- **QuickCopy** - Fast access to frequently used text snippets
- **Custom shortcuts** - Directly call actions from android using Intents (flashlight, rotation)
- **Speed** - Lightweight, fast!
- **Smart sorting** - Apps sorted by recent usage (with optional usage stats permission)
- **Guided onboarding** - Step-by-step permission setup
- **Background image & Theme** - Customize color theme (including system dark mode) and use your own background image
- **Quick Access to notification bar & quick settings** - With a swipe gesture from middle to the bottom, open the notification bar & quick settings.

## Use as Launcher, Widget, or Overlay

- **Launcher** - Use it as your default homescreen
- **Widget** - Add the most powerful search bar to your existing launcher / homescreen
- **Swipe gesture (Overlay)** - With a gesture from the side, open the search bar from any screen.

## Architecture

- **Kotlin** - 100% Kotlin codebase
- **Jetpack Compose** - Modern declarative UI
- **Material 3** - Latest Material Design components
- **AppSearch API** - For efficient content indexing and search
- **DataStore** - For preferences management
- **Overlay Service** - For gesture detection and floating UI
- **Accessibility Service** - For system-wide gesture detection

## Custom Shortcuts

If you want

## Requirements

- Android 10 (API 29) or higher
- Permissions:
  - Display over other apps (required)
  - Accessibility service (required for gesture detection)
  - Usage stats (optional, for smart app sorting)

## Building

### Prerequisites

- JDK 17 (LTS, is recommended for Android development)
- Android SDK with API 36

### Build Instructions

```bash
git clone https://github.com/joepio/searchlauncher.git
cd searchlauncher
# Install to connected device over ADB
./gradlew installDebug
# Run tests
./gradlew test
# Build APK
./gradlew assembleRelease
# Sign APK
./gradlew signRelease
# Install signed APK
./gradlew installRelease
```

## Known Limitations

- Content search within apps requires apps to implement AppSearch indexing
- Gesture detection may conflict with other gesture-based apps (e.g. Samsung's One UI)
- Some manufacturers' custom Android builds may restrict overlay permissions

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## License

This project is open source. See LICENSE file for details.
