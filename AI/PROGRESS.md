# SkillEdge Project Progress

## Allocation Desk: full redesign — priority segregation, filters, UI/UX overhaul
### Release v1.20.0
- **Timestamp**: 2026-08-07T20:00:00+05:30
- **Agent/Tool Used**: Claude Code
- **Files Modified**:
  - `ui/batch/AllocationDeskScreen.kt` — full rewrite
  - `app/build.gradle.kts` — versionCode 28, versionName 1.20.0

- **Segregation logic (the core ask)**: unallocated batches are split into two
  visually distinct, independently collapsible sections rather than a single
  flat sort — "segregate" reads as grouping, not just ordering:
  - **"Priority — Instructor-Led (ILT)"** — everything whose `delivery_mode`
    does NOT match FMAT/ILO
  - **"Other Delivery Modes (FMAT / ILO)"** — always rendered below, regardless
    of date
  - Within each section, sorted by `start_date` **descending**, exactly as
    requested. Classification is case-insensitive substring match on
    `delivery_mode`; an unrecognised mode defaults to the priority tier rather
    than being silently demoted — an unrecognised value is a data-quality
    question, not grounds to bury it.

- **Filters added** (previously only a single "75%+ match" toggle existed):
  - Skill-match band: All / 75%+ Ready / 50-74% Partial / Under 50%
  - Delivery mode: multi-select, built from the **actual distinct values
    present in the live data** rather than a guessed/hardcoded list — RMS's
    delivery-mode strings have already proven inconsistent once this session
    (see the mislabeled-instruction-file finding in `AI/CONTEXT.md`), so
    guessing exact enum values risked a filter that silently matched nothing
  - Active filters surface as removable chips above the list; one-tap reset
  - Search (kept from before, restyled)

- **UI/UX overhaul**: page title + sort-order subtitle, restyled search bar
  with leading icon, icon-led summary stat pills, a "Filters (N)" button that
  opens a bottom sheet (consistent with the app's existing bottom-sheet
  pattern used elsewhere — DrillSheet, ProfileMenuBottomSheet), collapsible
  section headers with an animated chevron and per-section counts, redesigned
  batch cards (delivery-mode tag distinctly coloured for priority vs.
  deprioritised, cleaner metadata line, revenue/customer-priority as tinted
  tags instead of plain text).

- **Compatibility note**: `BatchCard` gained an `isPriority: Boolean = true`
  parameter (defaulted, so nothing else calling it breaks) to drive the
  priority-vs-other tag colour. `relevanceColor` (shared with
  `BatchDetailScreen.kt`) and the `AllocationDeskContent(data, newIds,
  onBatchClick)` signature are unchanged, so `MainScreen.kt`'s call site
  needed no edits.

- **One naming collision found and fixed**: `BatchDetailScreen.kt` (same
  package) already declares a file-private `Chip` composable. Kotlin resolves
  an unqualified same-package name before a wildcard import from a different
  package, so referencing the app-wide `Chip` from `ui.main.MainScreen.kt`
  failed with "cannot access: it is private in file" — not a missing import,
  a same-package name clash. Resolved by declaring a locally-scoped `MiniTag`
  composable instead of fighting resolution order.

- **⚠️ Could not visually verify on-device or in an emulator** — no Android
  SDK/emulator is available in this environment (confirmed absent earlier
  this session too: no `adb`, no `ANDROID_HOME`/`ANDROID_SDK_ROOT`, no Android
  Studio install). Verified via: (a) `assembleDebug` + `assembleRelease` both
  BUILD SUCCESSFUL with zero new warnings, (b) a full manual read-through of
  the composable tree for logical correctness (sort direction, partition
  correctness, filter predicates, parameter wiring). Per this project's own
  verification standard, a clean compile is not the same as a verified UI —
  **please check the actual look and feel on-device after installing
  v1.20.0** and report back anything that doesn't look right (spacing,
  colours, the bottom sheet, section collapse behaviour) so it can be
  corrected against a real screen rather than guessed at twice.
- **Current Status**: Pushed. Awaiting on-device confirmation.
- **Next Actions**: If the exact `delivery_mode` string values RMS returns
  turn out not to contain "FMAT"/"ILO" as substrings (e.g. a different vendor
  code or abbreviation), the classification in `isDeprioritisedMode()` will
  need adjusting — easiest to confirm by opening the new Filters sheet, which
  lists every distinct mode string actually present.


## Real push notifications: allocation, mandatory feedback, unallocated demand
### Release v1.19.0
- **Timestamp**: 2026-08-07T19:00:00+05:30
- **Agent/Tool Used**: Claude Code
- **Files Modified**:
  - `util/NotificationStateStore.kt` (new) — SharedPreferences seen-set per manager email + first-run guard
  - `util/NotificationEngine.kt` (new) — pure delta detection over `batch_engagement_df`/`unallocated_demand_df`: new allocation, batch-just-completed (feedback mandatory), new unallocated demand
  - `util/SkillSyncNotificationWorker.kt` — rewritten from "always notify if pending count > 0" (fired the same notification every 15 min regardless of change) to real delta detection via the engine + seen-set
  - `ui/main/MainScreenViewModel.kt` — foreground 60s poll now uses the same engine/seen-set instead of its own narrow unallocated-only size-diff; `notification` flow changed from `String` to `(title, message)` pairs
  - `ui/main/MainScreen.kt` — updated notification collector for the new pair type
  - `util/LocalNotificationService.kt` — notifications now open the app on tap (previously had no content intent — tapping just dismissed) and use `BigTextStyle` so longer messages aren't truncated
  - `MainActivity.kt` — requests `POST_NOTIFICATIONS` at runtime on Android 13+; removed a dead no-op `LifecycleEventObserver` block
  - `app/build.gradle.kts` — versionCode 27, versionName 1.19.0

- **What this actually does**: three notification triggers, each backed by real fields already in the unified payload (no new backend work needed):
  1. **New batch assigned** — a reportee's `batch_engagement_df` row transitions to `current`/`upcoming` for an assignment not seen before
  2. **Feedback required (mandatory)** — a reportee's batch transitions to `engagement_state == "completed"` — fires once per completed assignment, framed as mandatory per the request
  3. **New unallocated batch** — a new row appears in `unallocated_demand_df`

- **Two real bugs fixed while wiring this, not introduced by it**:
  1. **Notifications were likely silently no-op'ing on all Android 13+ devices.** The manifest declared `POST_NOTIFICATIONS` but nothing ever called `requestPermission` — `LocalNotificationService.showNotification` checks the permission and returns early if it's not granted, and it defaults to denied until requested. Now requested once at app start.
  2. **The background worker could crash on first background run.** WorkManager can spawn a fresh process to run `SkillSyncNotificationWorker` without `MainActivity.onCreate()` ever executing (no `Application` subclass exists to guarantee init order), so `SessionManager`/`RetrofitClient` could be accessed before `.init()` ran, throwing `UninitializedPropertyAccessException`/`IllegalStateException`. Worker now defensively re-initializes both at the top of `doWork()` — idempotent, safe if already initialized.

- **Design note — one seen-set, two check paths**: the 60s foreground poll and the 15-min background WorkManager check both call `NotificationEngine.detect()` against the *same* `NotificationStateStore` (SharedPreferences), so an event fires exactly once no matter which path notices it first — no duplicate notifications from having both a foreground and background checker.

- **First-run safety**: a fresh login (or first-ever background check) seeds the seen-set from whatever already exists *without* notifying — otherwise every pre-existing batch on a manager's roster would fire one notification each on first use.

- **Build Status**: ✓ v1.19.0 — `assembleDebug` and `assembleRelease` both BUILD SUCCESSFUL.
- **Current Status**: Pushed. Functionally testable only on-device (WorkManager timing + notification permission dialog can't be verified from a build log) — next real allocation/completion/unallocated-demand event on a live account should produce a real Android notification.
- **Next Actions**: Verify on-device after this reaches a signed release build; consider deep-linking the tap target to the specific trainer's profile instead of just opening the dashboard (currently opens the app generally).


## API audit + first two Tier-2 activations
### Release v1.18.0
- **Timestamp**: 2026-08-07T18:00:00+05:30
- **Agent/Tool Used**: Claude Code
- **Files Modified**:
  - `backend.py` — registered `trainerFeedback` (244) and `assignmentPax` (209) cache TTLs; wired both into `/api/data/trainer-360`; added `feedback.responses` and per-assignment `participants`; updated module docstring's schema-notes block
  - `Trainer360Screen.kt` — `FeedbackSection` gained a "Recent Feedback" subsection from `feedback.responses`; `AssignmentRow` shows a roster preview when `participants` is present
  - `app/build.gradle.kts` — versionCode 26, versionName 1.18.0

- **What led here**: Read all 36 files in `trainer_portal_api_details/`, cross-referenced against `backend.py`'s actual `_APIS` dict and call sites (not the files' claims alone). Found: 11 APIs active, 9 registered-but-never-called ("Tier 2"), 14 never wired at all ("Tier 3"), 2 confirmed dead ends already documented in backend.py's own header. Recorded the full breakdown in `AI/CONTEXT.md`. Also found `trainer_portal_api_details/Check Course Availability in RMS.txt` (no underscore) is mislabeled — its content is actually the "Trainer RC Schedule" API.

- **⚠️ IMPORTANT — unverified against live RMS**: `trainerFeedback` and `assignmentPax` field names come from the instruction files only, which this same codebase's header comment says "have proven wrong more than once." Per project verification standards, this must not be treated as confirmed from a compile alone. Concretely:
  - `feedback.responses` in the trainer-360 response is parsed defensively (`Question`/`TextAnswer`/`MCQAnswer`/`FeedBackDate` with lowercase fallbacks) but may come back empty if the real field names differ.
  - A **temporary** `feedback.responses_raw_sample` field (first 2 raw rows, unparsed) was added specifically so the next live trainer-360 call can be inspected to confirm or correct the field mapping — same empirical-discovery technique already used historically for the `unallocated` endpoint (see DECISIONS.md 2026-08-06). **Delete this field once confirmed.**
  - `participants` is fetched only for the current + next assignment (bounded, not the full delivery history) to avoid an N+1 RMS call explosion.

- **Build Status**: ✓ v1.18.0 — `assembleDebug` and `assembleRelease` both BUILD SUCCESSFUL, zero warnings.
- **Current Status**: Pushed. Functionally inert until a real trainer-360 call proves out the field names — the UI sections simply render nothing if the lists come back empty, so this ships safely regardless.
- **Next Actions**: Open Trainer360 for a real trainer post-deploy, inspect `feedback.responses_raw_sample` in the raw API response (e.g. via browser devtools or a temporary log), correct the field mapping in `backend.py` if needed, then delete the raw-sample scaffolding field. After that's confirmed, the same defensive pattern can extend to the remaining Tier-2 APIs (`last3MonthsUtil`, `trainerAvailability`, etc.) with more confidence.


## Phase 5 + 6: Offline Resilience & Trend Forecasting
### Release v1.17.0
- **Timestamp**: 2026-08-07T17:00:00+05:30
- **Agent/Tool Used**: Claude Code
- **Files Modified**:
  - `data/cache/LocalCache.kt` (new) — Gson-backed JSON disk cache in `filesDir/offline_cache/`
  - `MainActivity.kt` — `LocalCache.init(applicationContext)`
  - `MainScreenViewModel.kt` — `DashboardState.Success` gained `fromCache`/`cachedAt`; dashboard/profile/capability fetches persist to disk on success and fall back to disk on failure (only when no in-memory success already exists)
  - `Trainer360ViewModel.kt` — same `fromCache`/`cachedAt` contract, keyed per trainer email
  - `MainScreen.kt` — offline banner now driven by actual `fromCache` state instead of just connectivity; added `relativeAge()` helper; added `TeamCapacityForecastCard` to dashboard
  - `Trainer360Screen.kt` — added matching offline banner (previously had none); added trend-projection line to `UtilisationSection`
  - `DashboardSections.kt` — added `utilizationForecasts()`, `projectNextUtilization()` (shared), `TeamCapacityForecastCard`
  - `app/build.gradle.kts` — bumped to versionCode 25, versionName 1.17.0

- **Phase 5 (Offline-First & Resilience) — what changed and why**:
  - Previously, offline resilience relied entirely on OkHttp's HTTP cache, which is opaque to app logic — a ViewModel had no way to know if a response was a live hit or a stale cache hit, and a cold app start with no network could fail entirely if the exact cached request didn't match.
  - `LocalCache` is now the explicit, queryable source of truth for "last known good data" per manager email / trainer email. A failed fetch tries disk before ever showing an Error screen.
  - Both the Dashboard and Trainer360 screens now say plainly when they're showing offline data and how old it is ("Offline — showing data from 3 hours ago") instead of either silently serving stale HTTP-cache data or blanking to an error.

- **Phase 6 (Predictive Intelligence) — scope and honesty constraint**:
  - The only real time-series signal RMS provides is per-trainer `utilization_series` (monthly). No history exists for feedback/risk/readiness — those are point-in-time only.
  - Built a transparent linear trend projection (`projectNextUtilization`) from that real series — explicitly labelled in the UI as "a projection, not a prediction" to avoid overclaiming intelligence that isn't there.
  - `TeamCapacityForecastCard` on the dashboard surfaces trainers trending toward overload or bench *next* month, before their capacity bucket actually flips — proactive rather than reactive.
  - Trainer360's utilisation section now shows the same one-line projection for that individual.
  - Deliberately did NOT build a fake ML/AI risk predictor — no training data or model exists, and CLAUDE.md's quality gate forbids placeholder functionality.

- **Build Status**: ✓ v1.17.0 — `assembleDebug` and `assembleRelease` both BUILD SUCCESSFUL, zero errors, one pre-existing non-blocking warning.
- **Current Status**: Phase 5 + 6 complete and pushed.
- **Next Actions**: Live smoke test against real RMS to confirm forecast card behaves correctly with actual multi-month utilization data; consider Phase 7 (manager workflows / batch actions) per NEXT_ACTIONS.md roadmap.


## Installation Issue — RESOLVED ✓
### 2026-08-07T14:50:00+05:30
- **Issue**: "Not updated" error when trying to install v1.11.0
- **Root Cause**: User was installing unsigned debug APK over signed release APK (security block)
- **Resolution**: Downloaded signed v1.11.0.20 APK from GitHub Releases → Installation successful
- **Learning**: Always distribute signed release APKs from GitHub Releases; debug APKs are for local development only
- **Status**: App v1.11.0.20 verified working on device

## Phase 4 — Streams 2-6: Intelligence Engines Complete ✓
### Final Release v1.16.0
- **Timestamp**: 2026-08-07T16:00:00+05:30
- **Agent/Tool Used**: Claude Code
- **Work Completed** (All 4 streams in one push):

### **Stream 2: Risk Engine (v1.12.0)**
- Team Risk Summary Card with High/Medium/Low distribution
- Feedback Risk Badge in TrainerCard with incident count
- Trainer360 Risk Section with risk_score gauge & HR flags

### **Stream 3: SPOF Engine (v1.13.0)**
- Strategic Impact & Actions section in Trainer360
- Critical skill owner detection (advanced/architect/expert courses)
- Succession planning recommendations
- Key-person risk flagging

### **Stream 4: Bench Risk Engine (v1.14.0)**  
- Team Capacity Optimization Card
- Bench utilization analysis (On Bench, Light, Balanced, Stretched)
- Capacity distribution with actionable statistics
- Alerts for underutilized team capacity

### **Stream 5: Performance Analytics (v1.15.0)**
- Integrated into TeamAnalytics (existing)
- Capacity distribution donut chart
- Deployment status stacked bar
- Certification coverage by trainer
- Utilization trend over last 3 months

### **Stream 6: Intelligent Actions (v1.16.0)**
- Smart recommendations in SPOFAndActionsSection
- Context-aware action suggestions
- Succession planning alerts
- Knowledge transfer recommendations
- Quarterly skill validation reminders

- **Build Status**: ✓ v1.16.0 Release APK built successfully
  - Debug APK: 16.7 MB
  - Release APK: 12.1 MB (unsigned)
  - All 4 Kotlin/Compose files modified
  - Zero compilation errors, warnings only

- **Current Status**: Phase 4 complete. All intelligence engines (Delivery, Risk, SPOF, Bench, Analytics, Actions) implemented and shipped. v1.16.0 ready for GitHub Actions release.
- **Next Actions**: Phase 5 (if planned) or production stabilization

---

## Phase 4 — Stream 1: Delivery Readiness Engine
### Release v1.11.0
- **Timestamp**: 2026-08-07T13:04:00+05:30
- **Agent/Tool Used**: AntiGravity IDE (Gemini)
- **Files Modified**:
  - `DashboardSections.kt` — added `TeamReadinessSummaryCard` + `CapacityStat`
  - `MainScreen.kt` — wired readiness card into `DashboardTab`; added `delivery` param to `TrainerCard`; delivery/capacity/risk badges
  - `TeamTab.kt` — added `deliveryMap` lookup; passes `delivery` row to each `TrainerCard`
  - `Trainer360Screen.kt` — added `DeliveryReadinessSection` with gauge, strengths, constraints, recommendations
  - `app/build.gradle.kts` — bumped to versionCode 20, versionName 1.11.0
- **Work Completed**:
  - Surfaced `delivery_intelligence_df` data that was already computed by backend but never shown in Android
  - Dashboard: Team Delivery Readiness card — 4 bands (Ready/Ready with Prep/Needs Mentoring/Hold) + animated progress bars + capacity split
  - TrainerCard: delivery readiness badge + capacity badge + ⚠️ High Risk indicator — no extra API call
  - Trainer360: new Delivery Readiness section with readiness score gauge, label, capacity, risk, strengths, constraints and actionable manager recommendations
  - Built `SkillEdge-v1.11.0.apk` (BUILD SUCCESSFUL)
  - Pushed to GitHub; created release at https://github.com/aishsynk/SkillSync/releases/tag/v1.11.0
- **Current Status**: v1.11.0 shipped. Phase 4 Stream 1 complete.
- **Next Actions**: Stream 2 — Risk Engine (v1.12.0): SPOF alerts, risk radar, risk indicators in TrainerCard and Trainer360

---

## Pre-Phase 4 Foundations
### August 07, 2026
- **Agent/Tool Used**: AntiGravity IDE
- **Files Modified**: 
  - `Navigation.kt`
  - `task.md`
  - `BatchDetailScreen.kt`
  - `MainScreen.kt`
  - `RetrofitClient.kt`
  - `SessionManager.kt`
  - `MainScreenViewModel.kt`
- **Work Completed**:
  - Implemented automatic UI refresh upon marking a skill (Priority 1A).
  - Completed Batch Intelligence UI (Priority 1B) to display match category in candidates list.
  - Replaced Logout icon with text button (Priority 2).
  - Intercepted Retrofit client with a caching mechanism for offline support (Priority 3).
  - Added "Last Synced" and "Offline Mode" indicators to MainScreen (Priority 3).
  - Implemented `LifecycleEventObserver` for ON_RESUME refresh (Priority 4).
  - Created polling loop (60s) for batch notification using Coroutines and Toast (Priority 5).
- **Current Status**: Pre-Phase 4 Foundations implemented. Pending Priority 6 and full build/test validation.
- **Next Actions**: 
  - Enhance Manager Metrics in Dashboard (Priority 6).
  - Run a clean build and generate the signed APK.
### Release v1.9.0
- **Timestamp**: 2026-08-07T11:35:00+05:30
- **Agent/Tool Used**: AntiGravity IDE
- **Files Modified**: `app/build.gradle.kts`, `AI/PROGRESS.md`
- **Work Completed**:
  - Fixed syntax error in `MainScreen.kt` and successfully compiled the project.
  - Bumped version to 1.9.0.
  - Built and generated `SkillEdge-v1.9.0.apk`.
  - Pushed changes to GitHub.
  - Created GitHub release v1.9.0 with the APK.
- **Current Status**: Pre-Phase 4 Foundations deployed and released.
### Final Platform Foundations Complete
- **Timestamp**: 2026-08-07T12:16:20+05:30
- **Agent/Tool Used**: AntiGravity IDE
- **Files Modified**: `app/build.gradle.kts`, `AndroidManifest.xml`, `SkillSyncNotificationWorker.kt`, `LocalNotificationService.kt`, `TopBannerNotification.kt`, `MainScreen.kt`, `MainActivity.kt`, `AllocationDeskScreen.kt`
- **Work Completed**:
  - Implemented background `WorkManager` for local system Push Notifications.
  - Implemented sleek `TopBannerNotification` for in-app alerts.
  - Integrated Batch Intelligence missing skills and upskilling warnings into `AllocationDeskScreen.kt`.
  - Finalized and tested Last Sync/Offline cache visual state in `MainScreen.kt`.
- **Current Status**: ALL Pre-Phase 4 Mandatory Foundations are implemented and compiling perfectly.
- **Next Actions**: 
  - Release v1.10.0 APK with these changes.
  - Proceed to Phase 4 (Readiness/Risk Engine).
 # # #   L o g o   U p d a t e   ( v 1 . 1 0 . 1 ) 
 -   * * T i m e s t a m p * * :   2 0 2 6 - 0 8 - 0 7 T 1 2 : 3 8 : 4 6 + 0 5 : 3 0 
 -   * * A g e n t / T o o l   U s e d * * :   A n t i G r a v i t y   I D E 
 -   * * F i l e s   M o d i f i e d * * :    p p / b u i l d . g r a d l e . k t s ,   B r a n d i n g . k t ,   m i p m a p - * / i c _ l a u n c h e r . p n g ,   m i p m a p - * / i c _ l a u n c h e r _ r o u n d . p n g 
 -   * * W o r k   C o m p l e t e d * * : 
     -   P r o c e s s e d   u p l o a d e d   l o w - p o l y   b r a i n   l o g o   t o   m a k e   t h e   w h i t e   b a c k g r o u n d   p e r f e c t l y   t r a n s p a r e n t   v i a   P y t h o n   P i l l o w . 
     -   U p d a t e d   a l l   A n d r o i d   L a u n c h e r   i c o n s   ( m i p m a p   d i r e c t o r i e s )   w i t h   t h e   n e w   t r a n s p a r e n t   l o g o . 
     -   R e p l a c e d   t h e   i n t e r n a l   S k i l l S y n c L o g o   c o m p o s a b l e   t o   n a t i v e l y   r e n d e r   t h e   t r a n s p a r e n t   l o g o   a s s e t . 
     -   B u i l t   A P K ,   b u m p e d   v e r s i o n   t o   1 . 1 0 . 1 ,   p u s h e d   t o   G i t H u b ,   a n d   c r e a t e d   r e l e a s e . 
 -   * * C u r r e n t   S t a t u s * * :   A p p   v i s u a l   b r a n d i n g   i s   f i n a l i z e d   w i t h   t h e   n e w   t r a n s p a r e n t   l o g o . 
 -   * * N e x t   A c t i o n s * * :   P r o c e e d   t o   P h a s e   4   ( R e a d i n e s s / R i s k   E n g i n e ) .  
 