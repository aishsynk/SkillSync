# AGENTS.md — AI Agent Guidelines & Operating Procedures

## Core Operating Principles

1. **Single Source of Truth**: Always start by reading `AI/PROGRESS.md` and treat it as the single source of truth for the project.
2. **Context & Decisions**:
   - Review `AI/CONTEXT.md` for durable, reusable project knowledge and architecture facts.
   - Review `AI/DECISIONS.md` for key architectural, design, business, or process decisions whenever additional context is required.
   - If any required `AI/*.md` file does not exist, create it based on the current project state and continue working.

---

# Session Handover Summary

- **Date and Time:** 2026-09-13T03:38:00+05:30
- **Model Used:** Antigravity (Gemini)
- **Tool/Agent Used:** Antigravity

## 1. What was completed previously
- Extracted business rules and factual logic to correct UI states (fixing "urgent demand" and "unstaffed delivery" assumptions).
- Compiled a validated `.apk` proving the build was stable.

## 2. What is currently in progress
- **Total Redesign (Light-First Enterprise Workspace):** The user mandated a complete redesign of the app away from the dark dashboard into a clean, modern enterprise product (like Linear or Notion). 
- **Today Screen Redesign:** We rewrote `ManagerCommandCentre.kt` with a completely new layout: "Needs your attention", "Today's operations", "Watchlist", and "Coming up".
- **Navigation Update:** We renamed the labels in the bottom navigation of `MainScreen.kt` to exactly match the requested standard: `Today`, `Plan`, `People`, `Delivery`, `More`.
- **Compilation Check:** The app successfully compiles via `:app:compileDebugKotlin`.

## 3. Files Modified
- `app/src/main/java/com/example/skillsync/feature/home/ManagerCommandCentre.kt` (Total Layout Redesign)
- `app/src/main/java/com/example/skillsync/feature/home/MainScreen.kt` (Bottom Nav label changes)

## 4. Current Status
- The `Today` screen has been radically simplified and rebuilt according to the new visual spec. It successfully compiles.
- **Assembling the APK:** `:app:assembleDebug` is currently running to generate the final APK.

## 5. Known Issues or Blockers
- **Icons:** We updated the text strings for the bottom navigation destinations, but the *icons* mapping those destinations might need an update to logically match (e.g., the icon for "More" vs old "Actions", or "Plan" vs old "Command").
- **Theme:** We hardcoded light colors into `ManagerCommandCentre.kt` for now. The global `Color.kt` and `Theme.kt` must be formally refactored next.
- **Tests:** `ScreenRenderTest.kt` or other UI tests may now fail because the UI hierarchy of `ManagerCommandCentre` has completely changed. They will need to be rewritten to assert against the new structure.

## 6. Next Recommended Actions
- Wait for `:app:assembleDebug` to complete.
- Complete the final `.apk` verification check using `aapt2 dump badging`.
- Move on to rebuilding the global Light-First theme in `Color.kt` and `Theme.kt`.
- Fix up the navigation icons.
- Check and fix any broken tests in `ScreenRenderTest.kt`.
- Begin planning for screens 2-9 (`Plan`, `People`, `Delivery`, etc.) after visual approval of `Today`.

---

# Session Handover Summary

- **Date and Time:** 2026-09-13 (session continuation)
- **Model Used:** Claude Sonnet 5
- **Tool/Agent Used:** Claude Code

## 1. Baseline for this session
- Stable, non-beta released version: **v3.80.3.178** ("Communication Intelligence Service & Wave 5 Commercial Opportunities"). This remains the protected production baseline — nothing in this session touched app code or that release.
- Working tree at session start already carried **uncommitted** changes from the prior (Antigravity/Gemini) session: light-first `ManagerCommandCentre.kt` / report-screen rewrite, nav label rename, plus untracked `fix*.py` / `*.ps1` scratch scripts. None of this is part of v3.80.3.178.

## 2. What was completed this session
- No production code changed. Produced two rounds of a **Design V2 visual proposal** (Artifact, not code): first a UX/UI/design-system audit + Version 2 vision, then an "Advanced" iteration explicitly fusing:
  - The previously-approved **Command Centre** direction (navy/royal/azure/brand/sky/cyan glass palette, dark-only theme, hero + KPI + action-queue layout for Today).
  - Widget vocabulary translated from **Color Admin** (seantheme.com admin dashboard v3): comparison-to-last-period stat cards, an Allocation Funnel (open demand → matched → scheduled → delivered), ranked lists with trailing stats, regional coverage breakdown (flag + bar + %), a certification-renewal campaign/progress card, and a dense sortable data table for tablet width.
  - iOS-discipline + Material-feedback motion rules (spring dismiss/haptics vs. ripple/shared-element transitions), explicitly split per interaction.
- Artifact published/updated at: `https://claude.ai/code/artifact/c6a9ec1d-057d-4f72-b3c2-c500bb9599dc` ("Manager Operations OS").
- Reviewed `AI/PROGRESS.md`, `AI/CONTEXT.md`, `AI/DECISIONS.md`, `AGENTS.md` per operating protocol.

## 3. Files Modified
- None in the app tree. Only `AI/PROGRESS.md`, `AI/CONTEXT.md`, `AI/DECISIONS.md` updated (this handover).
- Design deliverable lives outside the repo as a published Claude Artifact (see link above) — not yet materialized as `.kt` source.

## 4. Current Status
- **Direction conflict, unresolved:** the uncommitted light-first (white/Notion-style) `ManagerCommandCentre.kt` WIP in the working tree and the newly-approved dark Design V2 direction are mutually exclusive visual directions. Do not implement further UI on either path until this is explicitly resolved with the operator.
- Design V2 (Advanced) is a proposal only — no `Color.kt`/`Theme.kt`/screen changes have been made against it yet.

## 5. Known Issues / Blockers
- Working tree is dirty against v3.80.3.178 (see `git status`): modified `build.gradle.kts`, `AndroidManifest.xml`, `HrMonthlyReportScreen.kt`, `PrioritiesScreen.kt`, `WeeklyReportScreen.kt`, `ScreenRenderTest.kt`, plus untracked scratch scripts at repo root and under `SkillEdge_Android/`. None of this has been reconciled with the light-first WIP's own known issues (broken `ScreenRenderTest.kt`, unmapped nav icons) noted in the entry above.
- No `.apk` was built or verified this session.

## 6. Next Recommended Actions
1. Get an explicit operator decision: keep building the uncommitted light-first WIP, or discard it in favor of implementing the new dark Design V2 (Advanced) direction — do not proceed on both.
2. Once decided, either (a) finish/fix the light-first WIP (nav icons, `ScreenRenderTest.kt`, verify APK), or (b) start implementing Design V2's token layer (`Color.kt`/`Theme.kt`) and the Today screen's new widgets (comparison stat card, Allocation Funnel, campaign card) against it.
3. Clean up or intentionally commit the untracked `fix*.py`/`*.ps1` scratch scripts — leaving them untracked indefinitely risks accidental loss.
4. Keep v3.80.3.178 as the deployable fallback; any new work stays in Local Development → Development → Validation before it is ever considered for Production, per the standard pipeline in `AGENTS.md`.

---

# Session Handover Summary — Design V2 implementation started

- **Date and Time:** 2026-09-13 (session continuation)
- **Model Used:** Claude Sonnet 5
- **Tool/Agent Used:** Claude Code

## 1. Decision resolved
- Operator chose to proceed with **Design V2 (dark Command Centre)** over the uncommitted
  light-first WIP. `ManagerCommandCentre.kt` (Today screen) was rewritten accordingly; the
  light-first direction is superseded, not merged.

## 2. What was completed
- Discovered the app already had a full dark "Command Centre" design-system layer
  (`theme/Color.kt`, `theme/Theme.kt`, `theme/DesignSystem.kt`, `theme/SkillSyncComponents.kt` —
  `SkillCard`/`Figure`/`SectionHeading`/`ToneChip`/`Severity`/`glassSurface`/`pressable`, etc.)
  that already matches the Design V2 token spec almost exactly. No token/theme file changes were
  needed — only `ManagerCommandCentre.kt` was rewritten to use them instead of the light-first
  hardcoded white layout.
- New `ManagerCommandCentre.kt` (Today) renders, from real backend fields only (`manager_kpis`:
  `team_readiness_score`, `readiness_trend`, `avg_team_utilization`, `utilization_trend`,
  `open_demand`, `high_risk_trainers`/`delivery_risk_count`, `bench_trainers`/`optimal_trainers`/
  `stretched_trainers`, `cert_coverage_pct`, `unread_notifications`): a comparison-style KPI row
  (delta shown only when the backend supplies a non-blank trend — `readiness_trend` is often
  intentionally blank, so no delta is fabricated), a Capacity Balance stacked bar, a "Needs you
  today" severity-ranked list built from real unallocated demand + pending skill requests,
  "Today's operations" from real active batches, and a certification-coverage progress card.
- Fixed a real bug in the process: the previous light-first `ManagerCommandCentre` was already
  being invoked as a single `item {}` inside `MainScreen.kt`'s own `LazyColumn`; the initial
  Design V2 draft used a `LazyColumn` internally, which would have reintroduced the exact
  nested-scrolling-list crash that commit `064a4c6` had already fixed once. Corrected to a plain
  `Column` before compiling.
- `:app:compileDebugKotlin` and `:app:compileDebugUnitTestKotlin` both succeed.

## 3. Files Modified
- `SkillEdge_Android/app/src/main/java/com/example/skillsync/feature/home/ManagerCommandCentre.kt`
  (full rewrite, Today screen only — no other screen touched this pass, per the one-screen-per-
  release rule).

## 4. Current Status — tests, not fully green
- `:app:testDebugUnitTest --tests ScreenRenderTest` : 35 run, **12 failing**. Verified these are
  **pre-existing**, not introduced by this change: they assert on a materially richer Dashboard
  spec — a readiness hero, an "Explore the detail" expandable capability panel, a real-availability
  ("WHO IS ACTUALLY FREE") section, an "INTERNATIONAL" demand badge, an "Allocate" CTA, team-strength
  drill-through — none of which existed in the light-first `ManagerCommandCentre` either. This is a
  separate, larger restoration than the Design V2 token/widget pass done this session.
- No `.apk` built this session (not requested; token-and-widget layer was the scope).

## 5. Known Issues / Blockers
- The 12 `ScreenRenderTest` failures above remain open — they pre-date this session and describe a
  richer "briefing" Dashboard (hero + drill-through + explore-detail + availability + international
  demand) than either the light-first or the current Design V2 pass implements.
- Working tree still carries the other pre-existing uncommitted changes (report screens, manifest,
  gradle file, scratch scripts) noted in the prior handover — untouched this pass.
- Nothing committed this session (no explicit commit request from the operator).

## 6. Next Recommended Actions
1. Decide whether to restore the richer pre-existing Dashboard spec (hero/explore-detail/
   availability/international/drill-through) as the *next* one-screen-per-release item, or treat
   `ScreenRenderTest`'s expectations as stale and update the tests to match the shipped Design V2
   Today screen — this needs an explicit operator call, not an agent assumption.
2. Once Today is fully signed off (including its tests), move to the next screen per the build
   order in `AI/CONTEXT.md` (Calendar/Tasks-equivalent, then People, then Plan/Demand).
3. Still pending from the prior handover: reconcile the uncommitted report-screen/gradle/manifest
   changes and the untracked scratch scripts; keep v3.80.3.178 as the deployable fallback.

---

# Session Handover Summary — Phase 1 Capability Foundation released

- **Date and Time:** 2026-09-13 (session continuation)
- **Model Used:** Claude Sonnet 5
- **Tool/Agent Used:** Claude Code

## 1. What was completed
- Full Capability Intelligence Foundation, Phase 0 (live RMS verification) → Phase 1
  (implementation) → release gate, all in this session. See `AI/PHASE0_CAPABILITY_VERIFICATION_2026_09_13.md`,
  `AI/PHASE1_CAPABILITY_FOUNDATION_2026_09_13.md`, `AI/RELEASE_NOTES_PHASE1_CAPABILITY_FOUNDATION_2026_09_13.md`.
- Removed a real, live production fabrication bug: `_capability_for()` (backend.py) no longer
  invents capability/certification data for 8 named trainer emails or a generic AZ-104/MCT
  fallback for anyone else with empty RMS data; no more guessed 82% utilisation default.
- Added `domain/capability/`, `repositories/capability_store.py`, `services/capability/` — a new,
  unwired (no routes reference it) foundation layer with an evidence-gated, approval-gated data
  model. 8 course profiles seeded as DRAFT (not authoritative) via
  `scripts/seed_capability_foundation.py`, keyed on live-verified RMS `Cid` values.
- Release-gate audit (this turn): traced every Android screen consuming the 3 affected endpoints
  (`team-capability`/`capability/portfolio`, `cert-intel`) by direct code read. Finding: the
  specific fields changed (per-trainer `avg_qubits`, `utilization`) are not currently rendered by
  any screen at all — the UI already reads `readiness_score` (null-safe, hidden when null) and
  course-level rollups (naturally absent, not zeroed). **No Android code change was needed or
  made.**
- Verified: backend full suite 358/358 passing; Android unit tests 199 run, 12 pre-existing
  failures (all `ScreenRenderTest.kt`, unchanged in count/location from before — a known, unrelated
  gap) — zero new failures anywhere.

## 2. Current Status
**PHASE 1 FOUNDATION RELEASED — FACTUAL CAPABILITY DATA ENFORCED.** No matching engine, no
scoring, no Plan changes. Phase 2 (matching engine) has explicitly not started and needs a named
human curator to review the 8 DRAFT profiles first.

## 3. Files Modified/Created This Session (capability-foundation work only)
- Modified: `backend.py` (`_capability_for()` fabrication removal + imports + store/service
  instantiation), `AI/DECISIONS.md`, `AI/CONTEXT.md`.
- Created: `domain/capability/__init__.py`, `domain/capability/models.py`,
  `repositories/capability_store.py`, `services/capability/__init__.py`,
  `services/capability/capability_service.py`, `scripts/seed_capability_foundation.py`,
  `tests/test_capability_foundation.py`, and the four `AI/*CAPABILITY*`/`AI/PHASE*` docs.
- **Not touched:** any Android/Kotlin source, `Plan`/allocation matching logic, any existing route
  registration.

## 4. Known Issues / Blockers
- Everything from the prior handover (uncommitted report-screen/gradle/manifest changes, untracked
  `fix*.py`/`*.ps1` scratch scripts, the unresolved light-first-vs-Design-V2 direction question)
  is **still open and untouched** — this session's commit for the capability work was scoped to
  only the capability-foundation files, deliberately not bundled with that unrelated pending work.
- The 12 pre-existing `ScreenRenderTest.kt` failures remain open (unrelated to this work).
- No named human curator has been assigned to review/approve the 8 DRAFT course profiles — Phase 2
  cannot meaningfully start until at least one profile is `APPROVED`.

## 5. Next Recommended Actions
1. Assign a human curator for the 8 DRAFT capability profiles; only after at least one is
   `APPROVED` does Phase 2 (matching engine) have real data to build against.
2. Separately, still pending: resolve the light-first-vs-Design-V2 Today-screen direction, and the
   uncommitted report-screen/scratch-script cleanup from the 2026-09-13T03:38 handover — neither
   was touched by the capability-foundation work, and neither was committed/pushed this turn
   (deliberately scoped out of this release).
3. Do not start Phase 2 (matching engine) without explicit approval, per the release-gate
   instruction that established this checkpoint.

## 6. Release record
- **Committed:** `4bf55d2` — "feat: Capability Intelligence Foundation Phase 1 — remove
  fabricated trainer data" (17 files, capability-foundation scope only; the pre-existing
  uncommitted Android/report-screen/scratch-script changes were deliberately left out of this
  commit).
- **Tagged:** `phase1-capability-foundation-2026-09-13` (annotated).
- **Pushed:** `origin/main` (`064a4c6..4bf55d2`) and the tag, both confirmed pushed successfully.
- **Not independently verified this session:** live Render backend deployment/health after the
  push (this session used no deployment or log-access tool) and no APK was built (none needed —
  no Android source changed). If `backend.py` pushes auto-deploy to Render per the existing
  2026-09-04 publication authorization, that deploy would now be in flight; confirm its outcome
  separately before treating the fabrication fix as live in production.

## 7. Official numbered releases this session (v3.80.4.179 → v3.80.6.181)

Three official releases were cut this session, each scoped to exactly one slice, per the CI
process already established (`.github/workflows/android-release.yml`: push to `main` with a
non-ignored path change → build → sign → tag → GitHub Release, fully automated):

| Release | Commit | Scope | Android tests | Backend tests |
|---|---|---|---|---|
| v3.80.4.179 | `4bf55d2`/`642cb6d` | Capability foundation (backend only, no Android change) | unchanged (12 pre-existing failures) | 358/358 |
| v3.80.5.180 | `a388daf` | Today screen rebuilt on Design V2 (dark Command Centre) | 198 run, 12 pre-existing failures, 0 new | n/a (no backend change) |
| **v3.80.6.181** | **`e7a79c8`** | **Today Communication Command Centre** (Communicate section: Team/Trainer/Weekly/Monthly, Ask Availability, Share→SHARED_EXTERNALLY) | **203 run (198 baseline + 5 new), 12 pre-existing failures, 0 new** | **358/358** |

**v3.80.6.181 — verified facts, not assumed:**
- Tag `v3.80.6.181` and GitHub Release "SkillSync Release v3.80.6.181" exist:
  https://github.com/aishsynk/SkillSync/releases/tag/v3.80.6.181
- Asset `SkillEdge-v3.80.6.181.apk`, 13,827,626 bytes, SHA-256
  `87877abedf594fbf7b4f13d22844da5ddcfe8c4fac6e46e27c1222d3db594c5d` — downloaded and verified
  independently this session (not just trusted from Gradle source).
- `aapt dump badging` on the downloaded APK confirms `versionCode='181'`,
  `versionName='3.80.6'`, `package='com.example.skillsync'` — matches source, verified at the
  binary level.
- `apksigner verify --print-certs` confirms the same signing certificate SHA-256
  (`c6868b14bec9982642d908a5d4f535116daaf4e932a1e5ac27ed957671a41808`) as v3.80.5.180 — seamless
  update over the previous install is confirmed, not assumed.
- Files included: 7 Communication/navigation Kotlin files + `build.gradle.kts` (version bump) +
  1 new AI doc. Deliberately excluded: `AI/PLAN_V2_AUDIT_AND_DESIGN_2026_09_13.md` (out-of-scope
  doc), all `fix*.py`/`*.ps1`/`update_mcc.py` scratch files (still untracked, untouched).
- **Not verified this session:** Render backend deployment status (no backend file changed in
  this release, so no backend deploy was expected to trigger — `backend.py` is in the CI
  workflow's `paths-ignore` list and this release touched no backend file).

# Session Handover Summary - Phase 6B LinkedIn Capture client implemented (unreleased)

## 1. What was completed

The Android client for the Phase 6A LinkedIn capture backend was implemented, wired, and tested
against the existing gates — **a work-in-progress feature, deliberately not cut as a release and
not pushed**. Human-in-the-loop only: the app never posts, reacts, or comments on its own.

- **Entries (2):** a `SEND`/`text/plain` share-intent route on `MainActivity`
  (`LinkedInShareStore.accept` in `onCreate` + `onNewIntent`, transient in-memory
  `MutableStateFlow`, consumed immediately after navigation routes), and a glass "Analyse a
  LinkedIn post" card added as a normal item in the dashboard `LazyColumn`
  (`DashboardTab`, ManagerCommandCentre untouched).
- **Flow:** capture (share or paste) → editable preview (`LinkedInCaptureScreen` Editing stage,
  author/post-URL fields, validation chips, existing-client-side URL-only block with the exact
  backend 422 contract string `CAPTURE_TEXT_REQUIRED: LinkedIn shared only a post link...`) →
  `POST api/v1/captures/analyse` via a dedicated `LinkedInApiClient` (own OkHttpClient,
  40/60/40s timeouts, `isConfigured` guard) → Result stage renders action/reaction/comment
  with manual-review and sensitive-content banners, copy-comment (clipboard + "Copied"
  feedback), open-post (guarded `ACTION_VIEW`, `<queries>` block in manifest), analyse-another,
  done (backs to previous tab / dashboard).
- **Backend integration details:** base URL via `buildConfigField("LINKEDIN_BASE_URL")` from
  gradle property `-PlinkedinBackendBaseUrl` (empty default ⇒ "not configured" UI error; no
  placeholder host); debug-only cleartext via `app/src/debug/res/xml/network_security_config.xml`
  + debug manifest overlay for `10.0.2.2`/`localhost`/`127.0.0.1`; relationship/interaction
  omitted (backend `UNKNOWN`); traceability is metadata-only `Log` (`capture_id`, `text_length`,
  `content_hash` — never the post text).
- **Tests (31 new, all green):** `LinkedInCaptureParserTest` (12), `LinkedInCaptureRepositoryTest`
  (4, Retrofit-style fake `Call`, `runTest`), `LinkedInCaptureViewModelTest` (10, dispatcher-cont
  rolled, `StandardTestDispatcher` + `advanceUntilIdle`), `LinkedInCaptureScreenRenderTest` (5,
  Robolectric + compose rule directly invoking `internal` status content composables; includes a
  dashboard entry-card click test against the real `DashboardTab`).

## 2. Current Status

- `:app:compileDebugKotlin` + `:app:compileDebugUnitTestKotlin`: pass (one accepted pre-existing
  deprecation warning: `LocalClipboardManager`, matches existing repo usage).
- `:app:testDebugUnitTest`: **254 run, 11 failed — exactly the pre-existing baseline set**
  (7 `ScreenRenderTest` dashboard spec + 4 `PilotScreenshotTest` Compose timeouts), **0 new
  failures**, all 31 new LinkedIn tests pass.
- `:app:lintDebug`: **delta 0** vs established baseline (6 errors / 79 warnings / 3 hints) — two
  new warnings introduced then fixed (QueryPermissionsNeeded → manifest `<queries>`; UseKtx →
  `String.toUri()`).
- `:app:assembleDebug`: pass.
- Not cut as a release; no tag, no push.

## 3. Files Modified/Created This Session (LinkedIn capture scope only)

- New main source: `core/network/LinkedInApi.kt` (DTOs, snake_case Gson),
  `core/network/LinkedInApiClient.kt`, `feature/linkedin/engine/LinkedInAnalysis.kt`,
  `feature/linkedin/engine/LinkedInCaptureParser.kt` (normalize/extract/detect/`buildRequest` +
  `LinkedInAnalysisMapper` + `LinkedInLabels`), `feature/linkedin/data/LinkedInCaptureRepository.kt`,
  `feature/linkedin/ui/LinkedInCaptureViewModel.kt`, `feature/linkedin/ui/LinkedInCaptureScreen.kt`,
  `core/storage/LinkedInShareStore.kt`.
- Modified: `navigation/NavigationKeys.kt` (`LinkedInCapture` + `LinkedInCaptureSource`),
  `navigation/Navigation.kt`, `app/MainActivity.kt`, `app/src/main/AndroidManifest.xml` (SEND
  filter + `<queries>`), `app/build.gradle.kts` (buildConfig + `LINKEDIN_BASE_URL` field),
  `feature/home/MainScreen.kt`/`DashboardTab` (entry card + `onOpenLinkedInCapture`).
- Debug-only: `app/src/debug/AndroidManifest.xml`, `app/src/debug/res/xml/network_security_config.xml`.
- New tests: `app/src/test/java/com/example/skillsync/feature/linkedin/` (4 files listed above).
- Deliberately untouched: backend (`Personal\incipit\personal\Linkedin\` remains read-only phase
  6A reference), `AgentTest`/pilot/screenshot fixtures, AI/scratch scripts, `AI/DECISIONS.md` updated
  separately this session.

## 4. Known Issues / Blockers

- **No device or emulator available this session:** manual/E2E scenarios (share-intent landing,
  back-handling, result actions, cold-start clipboard, real backend round-trip) are **not
  verified** — unit/render-test coverage is the evidence so far.
- **Backend integration not live:** the backend has no deployed base URL yet; integration is only
  exercised via hand-rolled fake `Call` in the repository tests. A real round-trip needs
  `./gradlew … -PlinkedinBackendBaseUrl=…` against a deployed Phase 6A backend.
- Accessibility-service capture (reading a visible post without share) is Phase 7 and **out of
  scope** for this work; notifications/live-capture phases remain open.

## 5. Next Recommended Actions

1. Debug-build integration smoke: run the app with `-PlinkedinBackendBaseUrl` against a local or
   deployed Phase 6A backend, then record real capture_id-based traceability in logs.
2. Decide when/where to cut Phase 6B as a numbered release with the CI pipeline.
3. Phase 7 spec review: accessibility-service capture scope, notification tap routes, and the
   no-text-persistence guarantee, before any implementation turns.

## 6. Baseline record (reconfirmed this session)

- Pre-existing unit-test failures (11, never fixed/masked): 7 `ScreenRenderTest`
  dashboard_* spec gaps; 4 `PilotScreenshotTest` Compose timeouts.
- Lint baseline: 6 errors / 79 warnings / 3 hints (all pre-existing, unchanged).

## 7. Phase 6A backend reference (recap, read-only)

`Personal\incipit\personal\Linkedin\` holds the implemented Phase 6A engine
(`docs/LINKEDIN_CAPTURE_INTEGRATION.md`): `/api/v1/captures/analyse`, capture lifecycle
(CAPTURE_METADATA-only telemetry), decision pipeline (HUMAN → RELATIONSHIP → INTERACTION →
COMMENT_VALIDATION), `comment_validation=PASS`, and the 422 contract for URL-only captures. The
Android DTOs/mappers mirror that contract exactly.
