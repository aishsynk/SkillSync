# SkillSync Android App

Mobile application for SkillSync platform built with Kotlin & Jetpack Compose.

## Quick Start

### Prerequisites
- Android Studio (latest)
- Java 11+
- Gradle (included in Android Studio)

### Build

```bash
# Debug build
./gradlew assembleDebug

# Release build
./gradlew assembleRelease

# APK location
# - Debug: app/build/outputs/apk/debug/app-debug.apk
# - Release: app/build/outputs/apk/release/app-release.apk
```

### Install

```bash
# On emulator or connected device
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Or drag-drop APK to emulator window
```

## CI/CD Pipeline

Every push to `main` branch:
1. ✅ Builds debug APK
2. ✅ Builds release APK
3. ✅ Creates GitHub Release
4. ✅ Tags version (format: `SkillSync-v{YYYY.MM.DD.HHMM}.apk`)

Download APKs from: https://github.com/aishsynk/skillsync/releases

## Project Structure

```
skillsync/
├── app/                          # Main app module
│   ├── src/main/kotlin/com/koenig/skillsync/
│   │   ├── SkillSyncApp.kt       # Main application class
│   │   ├── screens/              # UI screens
│   │   │   ├── LoginScreen.kt
│   │   │   ├── DashboardScreen.kt
│   │   │   └── ...
│   │   ├── viewmodels/           # MVVM ViewModels
│   │   ├── models/               # Data models
│   │   ├── network/              # API integration
│   │   └── utils/                # Utilities
│   └── build.gradle.kts          # App build config
├── build.gradle.kts              # Root build config
├── settings.gradle.kts           # Gradle settings
└── .github/workflows/            # CI/CD
    └── build-and-release.yml
```

## Architecture

- **MVVM + Clean Architecture**
- **Jetpack Compose** for UI
- **Kotlin Coroutines** for async operations
- **Retrofit** for API calls
- **Hilt** for dependency injection

For detailed architecture, see: `ARCHITECTURE.md`

## Testing

```bash
# Run unit tests
./gradlew test

# Run instrumented tests
./gradlew connectedAndroidTest
```

## Releases

All releases are published automatically to GitHub with versioned APKs.

**Version Format:** `SkillSync-v{YYYY.MM.DD.HHMM}.apk`

**Download:** https://github.com/aishsynk/skillsync/releases

## Support

- Architecture details: `ARCHITECTURE.md`
- Implementation guide: `IMPLEMENTATION_GUIDE.md`
- Main README: `README.md`

---

**Status:** Phase 1 MVP  
**Last Updated:** 2026-08-06
