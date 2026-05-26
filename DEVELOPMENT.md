# Development Guide

## Project Overview

SearchLauncher is an Android launcher/search application built using modern Android development practices with Kotlin and Jetpack Compose.

## Technology Stack

- **Language**: Kotlin 1.9.20
- **UI Framework**: Jetpack Compose with Material 3
- **Build System**: Gradle (Kotlin DSL)
- **Minimum SDK**: API 29 (Android 10)
- **Target SDK**: API 34 (Android 14)

## Key Components

### 1. Application Layer
- `SearchLauncherApp`: Main application class, handles notification channel creation

### 2. Data Layer
- `SearchRepository`: Manages app search and content search functionality
- `AppInfo`: Data model for installed applications
- `SearchResult`: Sealed class representing search results (Apps and Content)

### 3. UI Layer
- `MainActivity`: Main entry point for search, settings, widgets, import/export, and app drawer flows
- Compose UI for search interface with Material 3 design

## Architecture Decisions

### Search Implementation
Search is performed in two stages:
1. **App Search**: Queries `PackageManager` for installed launcher activities
2. **Content Search**: Uses Android's AppSearch API (currently limited by app support)

## Development Setup

### Prerequisites
```bash
# Install Android Studio
# Configure Android SDK with API 34
# Install JDK 17
```

### First Time Setup
```bash
# Clone the repository
git clone https://github.com/joepio/searchlauncher.git
cd searchlauncher

# Open in Android Studio
# Let Gradle sync complete
# Connect Android device or start emulator

# Build and run
./gradlew installDebug
```

### Testing on Device
For best testing experience:
1. Use a physical Android device (API 29+)
2. Enable Developer Options and USB Debugging
3. Test launcher, widget, search, and settings flows

## Code Style

- Follow [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html)
- Use meaningful variable and function names
- Keep functions small and focused
- Add comments for complex logic
- Use Compose best practices

## Common Development Tasks

### Modifying Search Logic
Edit `SearchRepository.kt`:
- `searchApps()`: Modify app search behavior
- `searchContent()`: Modify content search with AppSearch
- `sortAppsByUsage()`: Change app ranking logic

### Customizing UI
Edit Compose files:
- `MainActivity.kt`: Home screen
- `theme/Theme.kt`: App theming

## Debugging Tips

### Permission Issues
- Check Settings > Apps > SearchLauncher > Permissions
- Check Settings > Apps > Special app access > Usage Access if usage-based ranking is not working

## Performance Considerations

- **Search Speed**: Implement debouncing (300ms) for search queries
- **UI Responsiveness**: Perform search operations on background threads

## Security Considerations

- Only request necessary permissions
- Clear explanation for each permission
- Don't access or store sensitive data
- Respect user privacy in search indexing

## Building for Release

```bash
# Create release build
./gradlew assembleRelease

# Sign the APK
# (Configure signing in app/build.gradle.kts)

# Test release build thoroughly
adb install app/build/outputs/apk/release/app-release.apk
```

## Troubleshooting

### Build Failures
- Clean and rebuild: `./gradlew clean build`
- Invalidate Android Studio caches
- Update Gradle dependencies

### Runtime Crashes
- Check logcat for stack traces
- Verify all permissions are granted
- Test on different Android versions

### UI Issues
- Test on different screen sizes
- Verify Compose preview renders
- Check Material 3 theme application

## Resources

- [Android Developers](https://developer.android.com/)
- [Jetpack Compose](https://developer.android.com/jetpack/compose)
- [Material 3](https://m3.material.io/)
- [AppSearch](https://developer.android.com/guide/topics/search/appsearch)
- [Kotlin Documentation](https://kotlinlang.org/docs/home.html)
