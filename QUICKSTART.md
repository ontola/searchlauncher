# Quick Start Guide

Get SearchLauncher up and running in 5 minutes!

## Step 1: Build the App

### Option A: Using VSCode
1. Open VSCode
2. File → Open Folder → select `searchlauncher`
3. Open terminal in VSCode (Ctrl/Cmd + `)
4. Build and install:
```bash
./gradlew installDebug
```

### Option B: Using Android Studio
1. Open Android Studio
2. Click "Open" and select the `searchlauncher` folder
3. Wait for Gradle sync to complete
4. Click the green "Run" button (▶️)
5. Select your device or emulator

### Option C: Using Command Line
```bash
# Navigate to project directory
cd searchlauncher

# Build and install
./gradlew installDebug

# Or on Windows
gradlew.bat installDebug
```

## Step 2: Open SearchLauncher

Set SearchLauncher as your default launcher, or add the SearchLauncher widget to your existing launcher.

Optional permissions can be granted from Settings:
- Usage Access: helps sort apps by usage.
- Contacts: enables contact search.
- Media images: lets SearchLauncher import wallpapers.

## Step 3: Use the App

### From Home Screen
1. Open SearchLauncher app
2. Type to search
3. Tap a result to launch it

## Step 4: Search and Launch

1. **Type to search** - results appear instantly
2. **Tap any result** to launch the app
3. Use custom shortcuts for web searches, snippets, settings, and smart actions

## Tips

- Search works on app names and package names
- Results are sorted by recent usage (if permission granted)
- Contacts, snippets, app shortcuts, and settings are indexed too

## Troubleshooting

### App not appearing in search?
- Only shows apps that can be launched (have launcher intent)
- System apps are filtered out (unless updated)
- Try searching for the exact app name

## What's Next?

- Explore all installed apps by searching with empty query
- Try different search terms
- Adjust permissions if needed in Settings
- Check the main app for service status

## Need Help?

- Check [DEVELOPMENT.md](DEVELOPMENT.md) for technical details
- Review [README.md](README.md) for full documentation
- Check logcat for error messages: `adb logcat -s SearchLauncher`

---

**Enjoy your new search launcher!** 🚀
