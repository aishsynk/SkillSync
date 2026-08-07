# SkillEdge / Manager OS — Decisions

Important decisions and their rationale. Add new entries at the top (newest first).

## 2026-08-07 — v1.17.0: App-level disk cache instead of relying on HTTP caching; trend projection instead of fabricated ML

- **Decision:** Add `LocalCache` — a small Gson/JSON disk cache keyed by email — as the explicit offline fallback for the dashboard and Trainer360, on top of (not replacing) the existing OkHttp HTTP cache.
- **Rationale:** OkHttp's cache is opaque to the ViewModel: it can't distinguish "live response" from "stale cache hit," and cache hits depend on exact request/query-param matching that a cold app start with a `refresh` flag flip can miss. A manager should never see a blank Error screen after a failed fetch if *any* prior successful payload exists on disk — `LocalCache` makes that a deliberate, testable fallback rather than an OkHttp implementation detail the app happens to benefit from sometimes.
- **Decision:** Both `DashboardState.Success` and `Trainer360State.Success` now carry `fromCache: Boolean` and `cachedAt: Long`, and the UI banners are driven by that flag rather than by `isNetworkAvailable()` alone.
- **Rationale:** A device can have a live network connection while the Render backend or an upstream RMS API is down — that looks identical to "offline" from the manager's chair. Deriving the banner from whether the *data on screen* actually came from cache is honest in both cases; deriving it from connectivity alone is not.
- **Decision:** For "predictive intelligence," implement only a transparent linear trend projection (`projectNextUtilization`) over the real `utilization_series` already in the payload, explicitly labelled in the UI as "a projection, not a prediction." Did not build a machine-learning risk/attrition/readiness predictor.
- **Rationale:** RMS provides genuine time-series data for exactly one metric (monthly utilization); feedback, HR incidents, and readiness are point-in-time only, with no history endpoint to train or project from. Fabricating a "risk forecast" without real historical signal would be exactly the kind of placeholder functionality the project's quality gate forbids, and would erode trust in the intelligence platform's other engines, which are all real backend computations. A slope-based projection over real numbers, honestly labelled, is useful; a black-box model with no underlying data would not be.

## 2026-08-06 — v1.4.0: Brand identity + premium UI/UX motion system

- **Decision:** Drop Material You dynamic colour (`dynamicColor` was `true`, so on Android 12+ the whole app took its palette from the user's wallpaper) and lock the app to a brand scheme. SkillSync is a corporate tool; wallpaper-driven theming actively destroyed the teal/blue identity shared with the web dashboard.
- **Decision:** Extend Material's scheme with a `SkillColors` CompositionLocal (`MaterialTheme.skill.*`) rather than scattering hard-coded `Color(0xFF…)` constants through screens. Material has no slot for the dashboard's hero-card chrome, status hues or table borders; the CompositionLocal lets light/dark swap atomically. The previous `MainScreen.kt` declared its own private colour constants, which could not respond to dark mode at all.
- **Decision:** Generate the brand mark as a **Delaunay-triangulated VectorDrawable** via a committed script (`SkillEdge_Android/tools/gen_logo.py`), not a hand-authored path or a raster asset.
  - A `<clip-path>` over a smooth silhouette was tried first and rejected — it produces a *blob* edge. Real low-poly art needs the triangles themselves to form the outline, so the script resamples the brain outline into boundary vertices, scatters interior points, triangulates, and drops triangles whose centroid falls outside the polygon (which is what carves the brain-stem notch).
  - Two variants: `ic_logo.xml` (full detail, in-app) and `ic_launcher_foreground.xml` (fewer/larger facets, compact heavier mesh, sized to the adaptive-icon 66x66 safe zone) — the full mark turns to mush at 48dp.
  - `tools/preview_logo.py` rasterises the same geometry with PIL so the mark can be eyeballed without building the app.
- **Decision:** Centralise animation in `ui/components/Motion.kt` (`Appear`, `AnimatedCount`, `animateProgressFromZero`, `rememberShake`, `ShimmerBox`) instead of per-screen ad-hoc animations, so timing/easing stay consistent.
  - **Gotcha worth remembering:** `animateFloatAsState` seeds its animator with the *first* target value, so it does **not** animate on initial composition. Anything that should grow from zero on first paint needs an explicit `var started by remember { … }` + `LaunchedEffect(Unit)` gate. Both the utilisation bars and the deployment stats were silently snapping before this was fixed.
- **Decision:** Loading state is a **skeleton mirroring the real layout**, not a centred spinner, so the page doesn't reflow when data lands.
- **Decision:** `MainScreenViewModel` gained `refresh()` separate from `loadData()`. A failed refresh must not replace data the manager is already reading, and `loadData` is now idempotent per-email so returning to the screen doesn't refetch.
- **Layout gotcha:** inside `Modifier.verticalScroll`, `Arrangement.Center` is a no-op — the scroll container measures children with `minHeight = 0`, so the column wraps its content and centring has no space to act in. The login screen uses `BoxWithConstraints` + `heightIn(min = maxHeight)` so it centres when short and scrolls when the keyboard is up.

## 2026-08-06 — v1.3.0: Full Dashboard Redesign with Live RMS Data Model

- **Decision:** Completely rewrite `backend.py` (Render) and `MainScreen.kt` (Android) to return and render the full web-frontend data model — the same `trainer_operations_df`, `trainer_current_state_df`, `batch_engagement_df`, `unallocated_demand_df`, `trainer_feedback_summary_df`, `manager_action_objects`, `trainer_decision_objects` arrays that the SkillEdge web dashboard consumes.
- **Rationale:** The v1.2.x Android app only showed static trainer cards from a minimal response. The web dashboard has a rich, proven data model with KPI calculations already tested in production. Matching this model means one backend serves both web and mobile consistently.
- **Implementation:** Per-trainer parallel fetch (ThreadPoolExecutor, max_workers=8) calls 3 RMS APIs per trainer: utilization (key=55), negative feedback count (key=58), previous+upcoming assignments (key=16). These feed status detection (teaching_now / preparing / free / unknown) and readiness/risk scoring.
- **Android UI:** Mirrors the web Manager Command Dashboard layout: dark header cards for Team Deployment / Capacity Signal / Manager Control KPIs, per-trainer cards with avatar initials, color-coded status badges, utilization progress bars, current/next course display, feedback risk badges. Brand colors match web (Teal #00ACAC, Blue #348FE2, Amber #F59C1A, Red #FF5B57).
- **Key discovery:** Unallocated demand API (key=190) uses `Coursename` (not Course), `CourseSDate`/`CourseEDate` (not StarDate/EndDate), `"Delivery Mode"` (with space), `vendor` (not customer). Discovered empirically via /debug/unallocated endpoint — field names differ from documentation.
- **Backward compatibility:** Old `kpis`, `manager`, `trainers`, `actions` fields retained in response alongside new arrays so v1.2.x APKs continue working.

## 2026-08-06 — Login fix: @koenig-solutions.com domain check + RMS role verification

## 2026-08-06 — Android App: Kotlin + Jetpack Compose, feature-parity MVP scaffold

- **Decision:** Build native Kotlin Android app (not React Native, not Flutter, not cross-platform abstraction) using Jetpack Compose (not XML layouts), with complete feature parity to web app, LinkedIn-inspired design, and responsive layouts for mobile to 10" tablet.
- **Rationale:** 
  - **Kotlin & native:** Type-safe, null-safe, coroutines, deep Android ecosystem integration. No performance compromises. OEM skin compatibility out-of-the-box.
  - **Jetpack Compose:** Declarative UI (reactive, fewer bugs), hot-reload development, easier responsive design than XML, Material 3 built-in, modern architecture patterns enforced.
  - **Feature parity:** Users expect same intelligence, actions, allocations, copilot on phone as web. Backend APIs & models are identical; no duplication.
  - **LinkedIn design:** Clean, card-based, teal + amber palette, professional typography. Works well on all screen sizes & OEM skins (Samsung One UI, OnePlus OxygenOS, MIUI, stock Android).
  - **Responsive:** Single codebase handles compact (< 600dp), medium (600–840dp), expanded (> 840dp) layouts via Compose conditionals.
- **Architecture:** Clean Architecture (Presentation/Domain/Data/Core layers), MVVM + StateFlow, Hilt DI, Retrofit + OkHttp, Coroutines, Room (future offline). Mirrors backend cleanly.
- **Decision:** MVP Phase 1 (60%) delivered as **runnable scaffold**: complete project structure, theme system, API client, login screen, dashboard screen, navigation. Phase 2 (core screens: team, trainer detail, actions, allocation) ready to implement. Phase 3 (copilot, charts, offline) + Phase 4 (polish) follow after.
- **Why not alternatives:**
  - Cross-platform (React Native/Flutter): Compromises performance, design consistency, OEM skin support, ecosystem integration.
  - Web app wrapped (Cordova/Capacitor): Bloated, poor UX, native features hard to add later.
  - Incremental: Phased delivery (MVP now, Phase 2 later) beats multi-week delay before first release.

## 2026-08-06 — Architecture is stable; follow-ups prioritized by risk & value

- **Decision:** No changes to application code during 2026-08-06 architecture analysis. Focus remains on the five candidate follow-ups from 2026-08-03, in this priority order: (1) live smoke_test.py run with real RMS creds, (2) remove duplicated client-side _intelScore, (3) persist sessions beyond process lifetime, (4) wire or delete dead intelligence_engines/ & knowledge/ scaffolding, (5) fix _read_kb_jsonl parser.
- **Rationale:** Post-workstream-3, the architecture is coherent and data is aligned. Remaining work is: (a) verify end-to-end behavior against real RMS (smoke test), (b) clean up technical debt (duplicated scoring, dead scaffolding), (c) improve user experience (session persistence), (d) unblock future work (fix KB parser). No new bugs or critical gaps found in analysis; all known defects are already scoped and actionable without further investigation.
- **Documentation decision:** Use AI/PROGRESS.md as source of truth, updated after every change. Stable architecture knowledge lives in AI/CONTEXT.md (durable facts). Important decisions and rationale recorded in AI/DECISIONS.md. This keeps future AI sessions concise and scannable: read PROGRESS.md first (what happened), check CONTEXT.md if more detail needed, refer to DECISIONS.md for why.

## 2026-08-03 — Workstreams 1-3 delivered as one flow

- **Decision:** Implement the agreed fix order end-to-end: (1) backend data alignment + team-size calibration, (2) non-blocking auth, (3) deterministic agentic layer + learning loop, then tests and docs.
- **Rationale:** Delivering the three previously-agreed workstreams together avoids leaving the app in a half-fixed state and keeps the manager experience coherent (aligned data → instant login → an agent that actually answers from that data).
- **Decision:** Availability verdicts are never presented as confident when signals conflict — `availability_confidence` is capped at 60 and `contradictions` surfaced; "Busy but Strong Candidate" is downgraded to "Available but Needs Prep" when the calendar is Unknown.
- **Rationale:** A `conf 100 / Busy` claim for a 24.6%-utilized trainer (the observed Niharika case) is a false verdict; the manager should see uncertainty, not fabricated certainty.
- **Decision:** SPOF / OEM bench / executive risk are calibrated by `trainer_count`; teams of ≤2 report "Thin Bench" (Medium) instead of "Single Point of Failure" (High).
- **Rationale:** A 2-person team legitimately has one trainer per course; flagging every course as a crisis is alarm noise and erodes trust in the "Needs Attention" summary.
- **Decision:** Login never blocks on the intelligence build. Stale cache is served immediately with `refresh_pending: true` and a guarded background rebuild is scheduled.
- **Rationale:** The synchronous login build caused minutes of spinner and could 401 on flaky RMS; the dashboard must open instantly from whatever cache exists.
- **Decision:** The agentic layer is deterministic (rule/tool-based) with an LLM plug-in seam at `agent.answer`, and the learning loop feeds manager decisions back into scoring weights (versioned, clamped, renormalized).
- **Rationale:** No LLM credentials exist in the environment; a deterministic agent is honest ("Deterministic, not live AI"), and a closed feedback loop is the durable AI improvement even without an external model.
- **Decision:** Every lifecycle action (close/escalate/reassign, acknowledge/resolve/escalate) is auto-recorded as a learning example with outcome labels.
- **Rationale:** Manager decisions are the highest-quality labels available; capturing them for free turns the existing action/review persistence into a training signal.

## 2026-08-03 — Baseline AI/ tracking workspace

- **Decision:** Create `AI/PROGRESS.md`, `AI/CONTEXT.md`, `AI/DECISIONS.md` to give
  future AI sessions a durable, minimal on-ramp.
- **Rationale:** No such tracking existed; PROGRESS.md is the source of truth, CONTEXT.md
  holds stable project knowledge, DECISIONS.md records rationale — keeping history concise
  and AI-agnostic without storing logs or transcripts.
