# SkillSync Android App

Professional Kotlin Android application for Koenig Solutions trainer intelligence platform.

## Tech Stack

- **Language:** Kotlin
- **UI Framework:** Jetpack Compose (modern, declarative, LinkedIn-inspired)
- **Architecture:** MVVM + Clean Architecture
- **Networking:** Retrofit + OkHttp
- **Local Storage:** Room Database + DataStore
- **Dependency Injection:** Hilt
- **State Management:** ViewModel + LiveData/Flow
- **Async:** Coroutines

## Features

- ✅ Non-blocking authentication with session management
- ✅ Dashboard with real-time KPIs and charts
- ✅ Trainer 360 view with detailed profiles
- ✅ Manager action queue with lifecycle management
- ✅ Allocation desk for demand matching
- ✅ Copilot AI chat + daily briefing
- ✅ Capability builder (skills roadmap)
- ✅ Certifications & performance tracking
- ✅ Responsive design (phone to tablet)
- ✅ Multi-tier caching with freshness metadata
- ✅ Dark mode support
- ✅ Offline support with sync

## Quick Start

### Prerequisites
- Android Studio Arctic Fox or newer
- JDK 11 or higher
- Android SDK 21+ (targeting 34)

### Build & Install
```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Release Build
```bash
./gradlew assembleRelease
# APK: app/build/outputs/apk/release/app-release.apk
```

## CI/CD Pipeline

Every push to `main`:
1. ✅ Builds debug & release APKs
2. ✅ Creates GitHub Release
3. ✅ Auto-versioned: `SkillSync-v{YYYY.MM.DD.HHMM}.apk`

Download from: https://github.com/aishsynk/skillsync/releases

## Project Structure

```
skillsync/
├── app/src/main/java/com/koenig/skilledge/
│   ├── presentation/      # Jetpack Compose UI screens
│   ├── viewmodels/        # MVVM ViewModels
│   ├── data/              # API & repository layer
│   ├── domain/            # Data models
│   ├── core/              # Theme, DI, navigation
│   └── ...
├── .github/workflows/     # CI/CD (GitHub Actions)
├── build.gradle.kts       # App build config
└── settings.gradle.kts
```

## Key Screens

1. **Login** — Email entry, session initialization
2. **Dashboard** — KPIs, team view, action queue
3. **Team** — Trainer roster with filters
4. **Trainer Detail** — Detailed trainer profile
5. **Actions** — Manager decisions & action management
6. **Settings** — Theme, preferences

## Design

- LinkedIn-inspired UI with teal (#0D8B8B) primary color
- Dark mode support (light/dark themes)
- Responsive layouts for phone to tablet
- Smooth animations & transitions

## Testing

```bash
./gradlew test                    # Unit tests
./gradlew connectedAndroidTest    # Integration tests
```

## Deployment

APKs are built and released automatically via GitHub Actions on every push to `main`.

## Support

Refer to SETUP.md, ARCHITECTURE.md, or IMPLEMENTATION_GUIDE.md for detailed docs.

---

**Status:** Phase 1 MVP  
**Last updated:** 2026-08-06
