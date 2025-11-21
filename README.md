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

- Android Studio Hedgehog (2023.1.1) or newer
- JDK 17 or newer
- Android SDK with API 34

### Build Instructions

1. Clone the repository:
```bash
git clone https://github.com/joepio/searchlauncher.git
cd searchlauncher
```

2. Open the project:
   - **VSCode**: Just open the folder (`code .`)
   - **Android Studio**: Use "Open" and select the folder

3. Install on device:
```bash
./gradlew installDebug
```

**Note**: You can edit and build in VSCode, but Android Studio provides better Android-specific tooling (layout preview, APK analyzer, etc.).

## Usage

1. **First Launch**: The app will guide you through the onboarding process
2. **Grant Permissions**: All are optional.
3. **Start Service**: The overlay service will start automatically after onboarding
4. **Use Gesture**: Swipe from the edge of the screen and back to open search
5. **Search**: Type to search apps and content, tap to launch

## How It Works

1. **Overlay Service** runs in the background with a foreground notification
2. **Edge Detector** listens for swipe gestures at the screen edge
3. **Search Window** appears as an overlay when gesture is detected
4. **Search Repository** queries installed apps and AppSearch database
5. **Results** are displayed in real-time as you type

## Permissions Explained

- **Display Over Other Apps**: Allows the search bar to appear over other apps
- **Accessibility Service**: Enables detection of swipe gestures system-wide
- **Usage Stats**: Helps sort apps by most recently used (optional)

## Project Structure

```
app/src/main/java/com/searchlauncher/app/
├── SearchLauncherApp.kt           # Application class
├── data/
│   ├── AppInfo.kt                  # Data model for apps
│   ├── SearchResult.kt             # Search result sealed class
│   └── SearchRepository.kt         # Search logic and app queries
├── service/
│   ├── OverlayService.kt           # Background service for overlay
│   ├── GestureAccessibilityService.kt  # Accessibility service
│   └── SearchWindowManager.kt      # Manages search overlay window
└── ui/
    ├── MainActivity.kt             # Main activity and home screen
    ├── OnboardingScreen.kt         # Permission onboarding flow
    └── theme/
        ├── Theme.kt                # Material 3 theme
        └── Type.kt                 # Typography definitions
```

## Known Limitations

- Content search within apps requires apps to implement AppSearch indexing
- Gesture detection may conflict with other gesture-based apps
- Some manufacturers' custom Android builds may restrict overlay permissions

## Future Enhancements

- Contact search
- Search history
- Favorites/pinned apps

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## License

This project is open source. See LICENSE file for details.
