# SkillEdge Android App - Implementation Guide

Complete step-by-step guide for building out the remaining screens and features. This document serves as the roadmap from MVP (60%) to production-ready (100%).

## Current Status

**MVP Phase 1: 60% Complete**
- ✅ Project structure, build config
- ✅ Theme system (colors, typography, dark mode)
- ✅ API client (Retrofit, OkHttp, caching)
- ✅ Models & data classes (complete)
- ✅ DI setup (Hilt)
- ✅ Login screen (UI + validation + ViewModel)
- ✅ Dashboard screen (KPIs + team summary + action queue)
- ✅ Navigation structure (placeholders for screens)

**To Complete: Phase 2 & 3 (40%)**

---

## Phase 2: Core Screens (Estimated 20-30 hours)

### 1. Team Roster Screen

**File:** `presentation/team/TeamScreen.kt` + `presentation/team/TeamViewModel.kt`

**Requirements:**
- Display all trainers from `trainerOperationsDf`
- Columns: Name, Email, Designation, Current Status, Utilization, Readiness
- Sorting: By name, status, utilization, readiness
- Filtering: By status, readiness level, utilization range
- Pagination: Load 20 per page, infinite scroll
- Search: Full-text on name + email
- Responsive: Stack columns on mobile, full table on tablet

**Implementation Steps:**
1. Create `TeamViewModel` with Flow of trainer list
2. Implement filtering/sorting logic (can use Kotlin sequences)
3. Create composable `TrainerRow` for each trainer
4. Implement LazyColumn with pagination
5. Add search TextField with debounce
6. Create filter UI (chips for status, dropdown for readiness)
7. Test on different screen sizes

**Design Notes:**
- Use Material 3 DataTable widget (or build custom)
- Color-code status (teal=teaching, green=free, red=blocked)
- Show avatar with trainer initials
- Clickable row → TrainerDetailScreen

**APIs Used:**
- Data: `intelligence.trainerOperationsDf`
- No new API calls needed

---

### 2. Trainer Detail Screen

**File:** `presentation/trainer_detail/TrainerDetailScreen.kt` + `presentation/trainer_detail/TrainerDetailViewModel.kt`

**Requirements:**
- Header: Trainer name, email, designation, avatar
- Profile section: Availability status, current batch, next batch
- Assignments: Past (scrollable), upcoming (with dates)
- Skills: Certified courses with vendors, QubitsScore
- Feedback: Positive/negative count, recent feedback items
- Readiness: Bucket, score, gaps
- Recommended actions: Skill gaps, growth path
- Action buttons: Close/escalate/reassign action (if applicable)

**Implementation Steps:**
1. Create `TrainerDetailViewModel` (receives trainer email from navArgs)
2. Extract relevant data from unified intelligence
3. Create composable `TrainerHeaderCard`
4. Create `AssignmentSection` with LazyColumn
5. Create `SkillsSection` with Chip layout
6. Create `FeedbackSection` (if data exists)
7. Create `ReadinessCard` with progress bar
8. Wire action buttons to `ActionRepository`

**Design Notes:**
- Vertical scroll for all sections
- Use cards for each section
- Status badges in header
- Color-code readiness (green/yellow/red)
- Animated expand/collapse for sections

**APIs Used:**
- Data: Extract from `intelligence` by filtering on trainer email
- Action: `POST /api/actions/{id}/{verb}` (close/escalate/reassign)

---

### 3. Action Detail & Lifecycle Screen

**File:** `presentation/actions/ActionDetailScreen.kt` + `presentation/actions/ActionsListScreen.kt`

**Requirements (List):**
- Show all open actions from `managerActionObjects`
- Columns: Trainer, Action Title, Category, Priority, Created Date
- Filtering: By category, priority, status
- Sorting: By priority, created date, status
- Search: Action title, trainer name
- Each row clickable → Action detail

**Requirements (Detail):**
- Header: Action title, category badge, priority badge
- Timeline: Show lifecycle (open → close/escalate/reassign)
- Details: Trainer info, reason, created date
- Notes section: Edit/add notes
- Action buttons: Close, Escalate, Reassign
- Input fields for notes/assignee
- Confirmation dialog before action

**Implementation Steps:**
1. Create `ActionsListViewModel` with filtering/sorting
2. Create `ActionDetailViewModel` (receives action ID)
3. Build `ActionList` composable with pagination
4. Build `ActionDetail` composable with form
5. Implement close/escalate/reassign buttons
6. Add confirmation dialogs
7. Wire to `ActionRepository`

**Design Notes:**
- Use Material 3 buttons for actions
- Status badges: color-coded (red=blocked, amber=pending)
- Timeline view or simple state display
- Form validation before submit

**APIs Used:**
- Fetch: From `intelligence.managerActionObjects`
- Update: `POST /api/actions/{id}/close`, `.../escalate`, `.../reassign`

---

### 4. Allocation Desk (Demand Matching)

**File:** `presentation/allocation/AllocationDeskScreen.kt` + `presentation/allocation/AllocationViewModel.kt`

**Requirements:**
- List all unallocated demand from `unallocatedDemandDf`
- For each demand: Course name, dates, delivery mode, customer, required skills
- Match button → Show ranked trainers (via `custom_course_match_service`)
- Display top 5 trainer suggestions with fit scores
- Click trainer → Quick allocate or view full profile
- Confirmation dialog before allocating
- Feedback: Success/error notification

**Implementation Steps:**
1. Create `AllocationViewModel`
2. Create `AllocationDeskScreen` with LazyColumn of demand cards
3. Create `DemandCard` composable (course, dates, mode)
4. Create `MatchResultsDialog` (ranked trainers with fit scores)
5. Create `ConfirmAllocationDialog`
6. Wire match button to API call (or mock for now)
7. Handle success/error states

**Design Notes:**
- Use Material 3 dialogs
- Show fit score as progress bar (0-100)
- Color-code by fit tier (high/medium/low)
- Trainer card in results: name, current util, readiness

**APIs Used:**
- Fetch: `intelligence.unallocatedDemandDf`
- Match: Could integrate `custom_course_match_service` or call backend endpoint (if it exists)

---

### 5. Capability Builder (Skills Roadmap)

**File:** `presentation/capability_builder/CapabilityBuilderScreen.kt`

**Requirements:**
- Show suggested skills from `futureSkillRoadmapDf`
- Group by trainer
- Display: Trainer, suggested skill, confidence, recommendation
- Tab view: By trainer, by skill, by priority
- Filter: By trainer, by confidence
- Sort: By confidence, by name

**Implementation Steps:**
1. Create `CapabilityBuilderViewModel`
2. Create `CapabilityBuilderScreen` with tabs (Trainer/Skill/Priority)
3. Create `TrainerCapabilityCard` for each trainer's roadmap
4. Create `SkillRecommendationCard`
5. Implement grouping logic
6. Add filtering UI

**Design Notes:**
- Use Material 3 TabRow
- Cards with confidence badge
- Collapsible sections by trainer
- Color-code confidence (green=high, yellow=medium, red=low)

**APIs Used:**
- Data: `intelligence.futureSkillRoadmapDf`

---

### 6. Certifications View

**File:** `presentation/certifications/CertificationsScreen.kt`

**Requirements:**
- Show cert landscape from `certificationSummary`
- Cards: Total certs, needed certs, coverage %
- Table: Certs by vendor with trainer count
- Show gaps: Missing certifications
- Filter: By vendor, by trainer
- Responsive table → Stacked cards on mobile

**Implementation Steps:**
1. Create `CertificationsViewModel`
2. Create `CertificationsScreen`
3. Create summary cards
4. Create vendor breakdown table (or card layout)
5. Add filter chips
6. Responsive layout logic

**Design Notes:**
- Use cards for summary metrics
- Table for vendor breakdown (scrollable)
- Pie/donut chart for cert distribution (optional, Phase 3)

**APIs Used:**
- Data: `intelligence.certificationSummary`, `intelligence.vendorStrengthDf`

---

### 7. Settings Screen

**File:** `presentation/settings/SettingsScreen.kt` + `presentation/settings/SettingsViewModel.kt`

**Requirements:**
- Theme selection (Light/Dark/System)
- Notification preferences (on/off toggles)
- Data sync settings (auto-refresh interval)
- Cache management (clear cache button)
- App info (version, build)
- About (copyright, links)
- Logout button

**Implementation Steps:**
1. Create `SettingsViewModel` with DataStore preferences
2. Create `SettingsScreen` with LazyColumn
3. Add theme toggle (restart app or use CompositionLocal)
4. Add notification switches
5. Add sync interval picker (dropdown)
6. Add clear cache button (confirmation dialog)
7. Add logout button (confirmation dialog + nav to login)

**Design Notes:**
- Use Material 3 Switches, OutlinedButtons
- Section dividers
- Settings persist to DataStore
- Logout clears session + navigates to login

**APIs Used:**
- Logout: `POST /auth/logout` (if needed)

---

## Phase 3: Advanced Features (Estimated 15-20 hours)

### 1. Copilot Chat Interface

**File:** `presentation/copilot/CopilotScreen.kt` + `presentation/copilot/CopilotViewModel.kt`

**Requirements:**
- Chat UI: Message list (scrollable) + input field at bottom
- Message types: User (right-aligned), Agent (left-aligned)
- Daily briefing: Special card at top with action items
- Questions: Pre-built quick actions (e.g., "Who's available?" "Show risks")
- Real-time responses: Show typing indicator
- Persistence: Save chat history locally (optional)

**Implementation Steps:**
1. Create `CopilotViewModel` with message state
2. Create `CopilotScreen` with LazyColumn for messages
3. Create `MessageBubble` composables (user + agent)
4. Create `ChatInputField` with send button
5. Create `BriefingCard` (shows at top initially)
6. Integrate `AgentRepository.askAgent()`
7. Add typing indicator while waiting for response

**Design Notes:**
- Message bubbles with subtle shadows
- Avatar icons for agent vs. user
- Timestamp on messages (optional)
- Smooth scroll to latest message
- Input field slides up with keyboard

**APIs Used:**
- Briefing: `GET /api/agent/briefing`
- Ask: `POST /api/agent/ask`

### 2. Charts & Visualizations

**File:** `presentation/dashboard/Charts.kt` (extend DashboardScreen)

**Charts to Add:**
1. **Utilization Distribution** (Bar chart)
   - X-axis: Trainer names
   - Y-axis: Utilization % (0-100)
   - Color by tier (green <60%, yellow 60-85%, red >85%)

2. **Readiness Breakdown** (Pie/Donut)
   - Segments: Ready (green), Prep (yellow), Blocked (red), Unknown (gray)
   - Show count + percentage

3. **Delivery Pipeline** (Stacked bar)
   - Categories: Live, Upcoming, Unallocated, Unknown
   - Show count per category

4. **Trainer Status Timeline** (Sparkline)
   - Mini chart showing status trend over past 7 days

**Implementation:**
- Use MPAndroidChart or Compose native (if lightweight)
- Add chart composables to DashboardScreen
- Responsive sizing based on screen width

### 3. Offline Support & Sync

**Files:** `data/database/SkillEdgeDatabase.kt`, new Room entities

**Requirements:**
- Cache intelligence payload in Room database
- Auto-sync when online (background job)
- Offline mode: Serve from database with "offline" badge
- Sync indicator: Show last sync time

**Implementation:**
1. Define Room entities mirroring domain models
2. Create SkillEdgeDatabase
3. Extend repositories with Room read/write
4. Add WorkManager job for background sync
5. Check internet connectivity before API calls

---

## Phase 4: Polish & Production (Estimated 10-15 hours)

### 1. Animation & Transitions

- Fade/slide transitions between screens
- Loading animations (skeleton loaders)
- Action success/error animations
- Ripple effects on buttons
- Scroll animations (parallax for headers)

### 2. Accessibility

- Content descriptions for all images
- Minimum touch target sizes (48dp)
- Color contrast compliance (WCAG AA)
- Screen reader support
- Keyboard navigation

### 3. Performance

- Lazy load images with Coil
- Optimize recompositions (remember {}, keys in LazyColumn)
- Prefetch data for smooth navigation
- Database indexes for Room
- Measure frame rates with Android Studio Profiler

### 4. Analytics & Crash Reporting

- Integrate Firebase Analytics
- Log key events: login, action, navigation
- Crash Crashlytics for error tracking
- Performance Monitoring for API latency

### 5. Testing

- Unit tests for ViewModels & Repositories
- Integration tests with MockWebServer
- UI tests with ComposeTestRule
- Manual testing on various devices/OEM skins

### 6. Google Play Submission

- Generate signing key
- Build release APK/AAB
- Create screenshots (portrait + landscape)
- Write store listing (title, description, keywords)
- Set age rating, content rating
- Configure rollout strategy (5% → 50% → 100%)

---

## Code Structure Template

### Adding a New Screen

**Step 1: Create ViewModel**
```kotlin
@HiltViewModel
class MyScreenViewModel @Inject constructor(
    private val repository: IntelligenceRepository
) : ViewModel() {
    private val _state = MutableStateFlow<UiState<Data>>(UiState.Loading)
    val state: StateFlow<UiState<Data>> = _state

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            repository.getData().collectLatest {
                _state.value = it
            }
        }
    }
}
```

**Step 2: Create Screen Composable**
```kotlin
@Composable
fun MyScreen(
    viewModel: MyScreenViewModel = hiltViewModel(),
    onNavigate: (String) -> Unit = {}
) {
    val state by viewModel.state.collectAsState()

    when (state) {
        is UiState.Loading -> LoadingState()
        is UiState.Success -> SuccessState(state.data, onNavigate)
        is UiState.Error -> ErrorState(state.error)
        is UiState.Empty -> {}
    }
}
```

**Step 3: Add Navigation**
```kotlin
composable("my_screen/{param}") {
    MyScreen(
        onNavigate = { route -> navController.navigate(route) }
    )
}
```

---

## Testing Checklist

- [ ] Login screen: Email validation, error handling
- [ ] Team roster: Sorting, filtering, pagination, responsive
- [ ] Trainer detail: All sections load, actions work
- [ ] Actions: CRUD operations, confirmation dialogs
- [ ] Allocation desk: Matching algorithm, allocation persist
- [ ] Capability builder: Grouping, filtering
- [ ] Copilot: Messages send/receive, briefing loads
- [ ] Settings: Theme changes, logout works
- [ ] Dark mode: All screens look good
- [ ] Responsive: Mobile (360dp), tablet (600dp), large (1200dp+)
- [ ] Network: API errors handled gracefully
- [ ] Offline: Cached data served when online unreachable
- [ ] Performance: < 16ms frame time, no jank
- [ ] Accessibility: All images have descriptions, minimum touch targets

---

## Deployment Checklist

- [ ] Version bump (1.0.0 → 1.1.0)
- [ ] Update CHANGELOG
- [ ] Generate release APK/AAB
- [ ] Test on real devices (at least 2 phones, 1 tablet)
- [ ] Test on different OEM skins (Samsung, OnePlus, MIUI)
- [ ] Create Google Play store listing
- [ ] Prepare screenshots (10 per orientation minimum)
- [ ] Write compelling description & keywords
- [ ] Set rating, content, target age
- [ ] Upload signed AAB
- [ ] Configure staged rollout (5% → 50% → 100%)
- [ ] Monitor crash reports & user feedback
- [ ] Iterate based on ratings & reviews

---

## Key Resources

- **Jetpack Compose:** https://developer.android.com/compose
- **Material 3:** https://m3.material.io
- **Hilt:** https://dagger.dev/hilt
- **Retrofit:** https://square.github.io/retrofit/
- **Coroutines:** https://kotlinlang.org/docs/coroutines-overview.html
- **Room:** https://developer.android.com/training/data-storage/room
- **MPAndroidChart:** https://github.com/PhilJay/MPAndroidChart

---

**Status:** 2026-08-06  
**Last Updated By:** Claude (claude-haiku-4-5-20251001)  
**Maintenance:** Keep this guide updated as screens are implemented.
