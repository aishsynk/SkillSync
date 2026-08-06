# SkillEdge Android App Architecture

Professional Kotlin Android application with complete web app feature parity, LinkedIn-inspired design, and full responsive layout support.

## Architecture Principles

### Clean Architecture Layers

```
Presentation (Jetpack Compose)
    ↓
Domain (Models, Use Cases)
    ↓
Data (Repositories, API, Database)
    ↓
External Services (RMS APIs)
```

### MVVM Pattern

- **ViewModel:** State management using Flow/StateFlow
- **Repository:** Data abstraction layer (API + cache)
- **View (Composables):** Reactive UI rendering
- **Models:** Sealed classes for type-safe state management

## Project Structure

### Core Module
- **theme/** — Design system (colors, typography, shapes)
  - `Color.kt` — LinkedIn-inspired palette
  - `Type.kt` — Professional typography
  - `Theme.kt` — Material 3 theme + dark mode support
- **di/** — Dependency Injection with Hilt
  - `NetworkModule.kt` — API client, OkHttp, Retrofit configuration
- **components/** — Reusable composables
- **navigation/** — Navigation graph setup

### Data Layer
- **api/** — Retrofit interfaces + request/response models
  - `SkillEdgeApiService.kt` — All backend endpoints
- **repository/** — Data source abstraction
  - `IntelligenceRepository.kt` — Intelligence data + caching
  - `ActionRepository.kt` — Action lifecycle operations
  - `AgentRepository.kt` — Agentic services
- **database/** — Room database (for future offline support)
- **cache/** — In-memory and disk caching strategies

### Domain Layer
- **models/** — Data classes & sealed classes
  - `SkillEdgeModels.kt` — Complete domain model matching backend payload
  - `UiState.kt` — Loading/Success/Error state management

### Presentation Layer
- **login/** — Authentication screen
  - `LoginScreen.kt` — LinkedIn-inspired login UI
  - `LoginViewModel.kt` — Login state & validation
- **dashboard/** — Main dashboard
  - `DashboardScreen.kt` — KPIs, team table, charts, action queue
  - `DashboardViewModel.kt` — Dashboard state management
- **team/** — Trainer roster (to be implemented)
- **trainer_detail/** — Individual trainer profile (to be implemented)
- **actions/** — Manager action queue (to be implemented)
- **allocation/** — Demand matching (to be implemented)
- **copilot/** — AI chat interface (to be implemented)
- **settings/** — User preferences (to be implemented)

## Design System

### Colors (LinkedIn-Inspired)

| Color | Light | Dark | Usage |
|-------|-------|------|-------|
| Primary Teal | #0D8B8B | #1BA69B | Buttons, actions, core UI |
| Secondary Amber | #D97706 | #F59C1A | Warnings, secondary actions |
| Success Green | #10B981 | #10B981 | Positive status, available |
| Error Red | #EF4444 | #FF9999 | Blocked, errors, high risk |
| Neutral Light | #F8FAFC | #0F172A | Backgrounds |
| Text | #475569 | #F1F5F9 | Primary text |

### Typography

- **Display:** Bold headings (32sp–24sp)
- **Headline:** Section titles (22sp–16sp)
- **Title:** Card titles (16sp–12sp)
- **Body:** Regular text (16sp–12sp)
- **Label:** Tags, labels (14sp–11sp)
- **Font Family:** System sans-serif (Roboto/Segoe UI)

### Components

- **KPI Cards:** Summary metrics with color-coded values
- **List Items:** Trainer/action cards with subtle borders
- **Buttons:** Rounded, contained, with ripple feedback
- **Charts:** Charts using MPAndroidChart (future: Compose charting)
- **Lists:** LazyColumn with pagination support
- **Forms:** Outlined text fields with validation

## State Management

### ViewModel + StateFlow

```kotlin
class DashboardViewModel : ViewModel() {
    private val _intelligenceState = MutableStateFlow<UiState<Data>>(UiState.Loading)
    val intelligenceState: StateFlow<UiState<Data>> = _intelligenceState
    
    fun loadData() {
        viewModelScope.launch {
            repository.fetchData().collectLatest { state ->
                _intelligenceState.value = state
            }
        }
    }
}
```

### Sealed Class State Pattern

```kotlin
sealed class UiState<out T> {
    object Loading : UiState<Nothing>()
    data class Success<T>(val data: T) : UiState<T>()
    data class Error(val error: String) : UiState<Nothing>()
    object Empty : UiState<Nothing>()
}
```

## API Integration

### Retrofit Configuration

```kotlin
Retrofit.Builder()
    .baseUrl(BuildConfig.API_BASE_URL)
    .client(okHttpClient)
    .addConverterFactory(GsonConverterFactory.create())
    .build()
```

### API Endpoints (Matching Backend)

| Endpoint | Method | Purpose |
|----------|--------|---------|
| `/auth/login` | POST | Session initiation |
| `/data/unified-manager-intelligence` | GET | Main intelligence payload |
| `/api/actions/{id}/{verb}` | POST | Action lifecycle |
| `/api/review-flags/{id}/{verb}` | POST | Review flag lifecycle |
| `/api/agent/*` | GET/POST | Agentic services |
| `/rms/{api}` | POST | RMS API proxy |

## Caching Strategy

### Multi-Tier Approach

1. **In-Memory Cache** (ViewModel LiveData)
   - Fast access, cleared on process death
   - Perfect for tab switches

2. **HTTP Cache** (OkHttp)
   - 4-hour TTL matching backend
   - Transparent, no code changes needed

3. **Database Cache** (Room, future)
   - Persistent across app restarts
   - For offline support & archival

### Stale-While-Refresh Pattern

```
Fresh cache → Return immediately
Stale cache → Return immediately + refresh in background
No cache → Fetch from API
API fails → Fall back to stale cache
```

## Responsive Design

### Screen Size Tiers

| Tier | Width | Layout |
|------|-------|--------|
| Compact | < 600dp | Single column, bottom nav |
| Medium | 600–840dp | Two columns, top nav |
| Expanded | > 840dp | Three columns, master-detail |

### Implementation

```kotlin
val isCompact = LocalConfiguration.current.screenWidthDp < 600
val isMedium = LocalConfiguration.current.screenWidthDp in 600..840

Column(
    modifier = Modifier
        .fillMaxWidth()
        .widthIn(max = if (isCompact) 600.dp else 1200.dp)
)
```

## Dependency Injection (Hilt)

### Module Structure

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    fun provideApiService(retrofit: Retrofit): SkillEdgeApiService { ... }
}
```

### Usage in ViewModels

```kotlin
@HiltViewModel
class MyViewModel @Inject constructor(
    private val repository: IntelligenceRepository
) : ViewModel()
```

## Testing

### Unit Tests

```bash
./gradlew test
```

### Integration Tests

```bash
./gradlew connectedAndroidTest
```

### Instrumentation Tests

- Test composables with `ComposeRule`
- Test navigation with `NavigationTester`
- Mock API with `MockWebServer`

## Performance Considerations

### Lazy Loading

```kotlin
LazyColumn {
    items(items.size, key = { items[it].id }) { index ->
        ItemRow(items[index])
    }
    if (!isEndOfList) {
        item { LoadMoreButton() }
    }
}
```

### Image Loading

```kotlin
Image(
    painter = rememberAsyncImagePainter("url"),
    modifier = Modifier.size(48.dp)
)
```

### Coroutines

```kotlin
viewModelScope.launch {
    repository.fetchData().collectLatest {
        _state.value = it
    }
}
```

## Dark Mode

### System Integration

```kotlin
val darkTheme = isSystemInDarkTheme()
val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

MaterialTheme(colorScheme = colorScheme) { ... }
```

### Colors Adapt Automatically

- Text colors invert for readability
- Surfaces darken/lighten
- All composables respect theme

## BuildConfig

### Debug Build
```
API_BASE_URL = "http://localhost:8765"
API_TIMEOUT = 30 seconds
LOG_LEVEL = DEBUG
```

### Release Build
```
API_BASE_URL = "https://skilledge-api.koenig-solutions.com"
API_TIMEOUT = 30 seconds
LOG_LEVEL = ERROR
```

## Key Libraries

| Library | Version | Purpose |
|---------|---------|---------|
| Jetpack Compose | 1.6.0 | UI framework |
| Retrofit | 2.10.0 | HTTP client |
| Room | 2.6.1 | Local database |
| Hilt | 2.48 | Dependency injection |
| Timber | 5.0.1 | Logging |
| MPAndroidChart | 3.1.0 | Charts |
| Kizitonwose Calendar | 2.4.0 | Calendar widget |

## Deployment

### Build Release APK

```bash
./gradlew bundleRelease
```

### Sign APK

```bash
jarsigner -verbose -sigalg SHA256withRSA -digestalg SHA-256 \
  -keystore keystore.jks app-release.aab \
  alias_name
```

### Upload to Google Play

1. Create app in Google Play Console
2. Upload signed APK/AAB
3. Fill store listing (screenshots, description, icons)
4. Configure release notes and testing instructions
5. Start with staged rollout (5% → 50% → 100%)

## Feature Roadmap

### Phase 1 (MVP - In Progress)
- [x] Login screen with email validation
- [x] Dashboard with KPIs
- [x] Navigation setup
- [ ] Team roster view
- [ ] Trainer detail screen
- [ ] Action queue management

### Phase 2 (Core Features)
- [ ] Allocation desk (demand matching)
- [ ] Capability builder (skills roadmap)
- [ ] Copilot chat interface
- [ ] Certifications view
- [ ] Performance/feedback view

### Phase 3 (Advanced)
- [ ] Offline support with Room sync
- [ ] Advanced filtering & search
- [ ] Custom dashboards
- [ ] Export reports
- [ ] Notifications

### Phase 4 (Polish)
- [ ] Dark mode refinements
- [ ] Animation polish
- [ ] A/B testing support
- [ ] Crash analytics (Firebase)
- [ ] Performance monitoring (Firebase)

## Security

### API Security

- Sessions managed server-side (cookies)
- No credentials stored in app
- All API calls over HTTPS
- OkHttp certificate pinning (optional)

### Data Security

- Sensitive data not logged
- Room database encrypted (optional)
- SharedPreferences for non-sensitive data

### Permissions

- Only request necessary permissions
- Explain permission prompts
- Handle denials gracefully

## Monitoring

### Crash Reporting

```kotlin
FirebaseAnalytics.getInstance(context).logEvent("crash", bundle)
```

### Performance

```kotlin
Timber.d("Task completed in ${System.currentTimeMillis() - start}ms")
```

### Analytics

```kotlin
FirebaseAnalytics.getInstance(context).logEvent("login_success", bundle)
```

---

**Last Updated:** 2026-08-06  
**Status:** MVP Phase (Dashboard, Login, Models, API) - 60% Complete
