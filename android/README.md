# SkillEdge Android App

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

## Project Structure

```
android/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/koenig/skilledge/
│   │   │   │   ├── core/
│   │   │   │   │   ├── theme/
│   │   │   │   │   ├── components/
│   │   │   │   │   ├── di/
│   │   │   │   │   └── navigation/
│   │   │   │   ├── data/
│   │   │   │   │   ├── api/
│   │   │   │   │   ├── database/
│   │   │   │   │   ├── repository/
│   │   │   │   │   └── cache/
│   │   │   │   ├── domain/
│   │   │   │   │   ├── models/
│   │   │   │   │   ├── usecases/
│   │   │   │   │   └── repository/
│   │   │   │   ├── presentation/
│   │   │   │   │   ├── login/
│   │   │   │   │   ├── dashboard/
│   │   │   │   │   ├── team/
│   │   │   │   │   ├── trainer_detail/
│   │   │   │   │   ├── actions/
│   │   │   │   │   ├── allocation/
│   │   │   │   │   ├── copilot/
│   │   │   │   │   ├── settings/
│   │   │   │   │   └── common/
│   │   │   │   └── MainActivity.kt
│   │   │   ├── res/
│   │   │   │   ├── drawable/
│   │   │   │   ├── values/
│   │   │   │   └── values-v31/
│   │   │   └── AndroidManifest.xml
│   │   └── test/
│   ├── build.gradle.kts
│   └── proguard-rules.pro
├── build.gradle.kts
├── settings.gradle.kts
└── local.properties
```

## Design Principles

### LinkedIn-Inspired Design
- Clean, card-based layouts
- Professional sans-serif typography (using Google Fonts)
- Subtle shadows and borders for depth
- Ample whitespace and breathing room
- Accent colors: Primary teal (#0D8B8B), Secondary accent (#D97706)
- Neutral ground: Light (#F8FAFC) / Dark (#0F172A)

### Responsive Design
- **Compact (< 600dp):** Single column, full-width cards, bottom navigation
- **Medium (600-840dp):** Two columns where appropriate, top navigation
- **Expanded (> 840dp):** Three columns, master-detail layout

### Smooth Performance
- Lazy loading of lists with pagination
- Efficient image loading with Coil
- Coroutines for non-blocking operations
- Database-backed caching with Room
- Smart refresh logic with stale-while-refresh pattern

## Getting Started

### Prerequisites
- Android Studio Arctic Fox or newer
- JDK 11 or higher
- Android SDK 21+ (targeting 34)

### Setup
```bash
cd android
./gradlew build
./gradlew installDebug
```

### Environment Configuration
Create `local.properties`:
```properties
sdk.dir=/path/to/Android/sdk
SKILLEDGE_API_BASE=http://localhost:8765
SKILLEDGE_API_TIMEOUT=30
```

## Build Variants

- **Debug:** Local development, verbose logging
- **Release:** Optimized, ProGuard enabled, crash reporting

## Key Screens

1. **Login** — Email entry, session initialization
2. **Dashboard** — KPIs, team table, charts, calendar, action queue, copilot
3. **Team** — Trainer roster with filters and sorting
4. **Trainer Detail** — Deep dive: profile, assignments, skills, feedback, readiness
5. **Actions** — Manager decisions (close/escalate/reassign) with persistence
6. **Allocation Desk** — Match trainers to unallocated demand
7. **Capability Builder** — Skills roadmap and training recommendations
8. **Copilot** — Chat interface + daily briefing
9. **Settings** — Theme, notifications, data preferences

## API Integration

All API calls go through `SkillEdgeApiService` (Retrofit):
- `POST /auth/login` — Session initiation
- `GET /data/unified-manager-intelligence` — Unified payload (cached)
- `POST /rms/{api}` — RMS API proxy
- `POST /api/actions/{id}/{verb}` — Action lifecycle
- `POST /api/review-flags/{id}/{verb}` — Review flag lifecycle
- `GET/POST /api/agent/*` — Agentic endpoints

## Caching Strategy

- **In-memory cache:** ViewModel + LiveData
- **Disk cache:** Room database
- **HTTP cache:** OkHttp interceptor (4-hour TTL)
- **Smart refresh:** Background sync when cache is stale

## Testing

```bash
./gradlew test              # Unit tests
./gradlew connectedAndroidTest  # Integration tests
./gradlew testRelease       # Full build + test suite
```

## Deployment

1. Build release APK: `./gradlew bundleRelease`
2. Upload to Google Play Console
3. Configure app store listing with LinkedIn-inspired screenshots
4. A/B test launch with 5% → 50% → 100% rollout

## Version History

- **1.0.0** — Initial release with complete web app feature parity

## Support

For issues, refer to [SkillEdge main README](../README.md) or contact dev team.

## License

Proprietary — Koenig Solutions Ltd

---

**Last updated:** 2026-08-06  
**Status:** In development
