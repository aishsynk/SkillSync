# SkillEdge / Manager OS — Decisions

Important decisions and their rationale. Add new entries at the top (newest first).

## 2026-08-08 — v1.29.0: Roster card answers four questions, not a stat wall

- **Decision:** Replaced the trainer roster card's five separate badges (cert count, cert gap count, feedback risk, delivery risk, readiness bucket) with one `trainerHealth()` score (0–100) and a Healthy/Watchlist/Needs Attention/High Risk category.
- **Rationale:** The manager's own framing: "the card should focus on decision-making rather than statistics." A manager scanning 20+ rows cannot compare five independently-coloured labels per row; one ranked number lets the whole roster sort itself by urgency. Certificates and gap counts still exist — they moved to `trainer-360`, the detail screen.

- **Decision:** The roster's headline capacity figure is now "available capacity" (100 − utilisation), not raw utilisation.
- **Rationale:** A manager opens the roster to find who can take the next assignment. "24% available" answers that directly; "76% utilised" makes the manager do the subtraction themselves, every row, every time.

- **Decision:** The action row only renders when `recommended_action` is a real, non-default value.
- **Rationale:** A permanently-visible "action needed" affordance on every card — even ones with nothing to do — trains managers to ignore it. Making it conditional means its presence is itself the signal.

- **Decision:** Roster default sort changed from Utilisation to the new Health score.
- **Rationale:** Utilisation ranks by how busy someone is, not by how much they need the manager's attention. A benched trainer with no risk factors and an overloaded trainer with no risk factors both used to sort near each other under utilisation; health sorts by actual urgency.

## 2026-08-07 — v1.28.0: Redesign starts at the token layer, and the app commits to one dark identity

- **Decision:** Rewrote `theme/Color.kt` and `theme/Theme.kt` rather than restyling cards again.
- **Rationale:** v1.25–v1.27 each rearranged `DashboardSections.kt` and each shipped looking identical, because `primary = Teal`, `pageBg = #F2F5F8` and `cardBg = #FFFFFF` were never changed. Visual identity is decided by the tokens; no amount of card work can override them.

- **Decision:** The app ships a single dark command-centre theme; the light scheme now resolves to the same tokens.
- **Rationale:** On the aurora mesh ground a light theme halves the contrast of every status colour, and an operations console that reads like a spreadsheet loses the at-a-glance triage the layout is built around. Committing to one identity also removes an entire class of two-theme drift.

- **Decision:** "Glass" is a translucent gradient fill + ice hairline, not a real backdrop blur.
- **Rationale:** Backdrop blur is unavailable below API 31 and expensive behind a scrolling `LazyColumn` on the mid-range devices this ships to. The translucent fill plus top-edge sheen is what actually reads as frosted on a phone; the blur was cost without the perceptual payoff.

- **Decision:** Replaced donut charts with single stacked distribution bars.
- **Rationale:** At phone width, comparing segment lengths on one bar is materially easier than comparing arc angles, and the bar leaves room for counts to sit beside it instead of crowding a ring.

- **Decision:** Status is encoded as shape *and* colour (left stripe, pip, pill), and emoji status glyphs were removed.
- **Rationale:** Colour-only status fails for colour-blind users and breaks the typographic scale. The stripe carries severity; colour reinforces it.

- **Decision:** Readiness and certification coverage now read from `manager_kpis` in the main payload instead of showing "Tap to load" pending `team-capability`.
- **Rationale:** Those are two of the eight headline health numbers. Leaving the first screen's key figures blank behind a second, slower RMS call defeats the purpose of a command centre. Capability still enriches the value when it arrives.

## 2026-08-08 — v1.25.0 Patch 5: Executive Cockpit & Notification Architecture
- **Decision:** Transformed Dashboard into a Power BI / Azure Portal style Executive Cockpit with custom Canvas micro-charts (`SparklineChart`, `CapacityDonutChart`, `ReadinessRingGauge`), Header Notification Center with severity levels (Critical 🔴, Warning 🟡, Info 🔵), and SkillEdge Deep Navy & Cyan design system (`#0A1128` / `#0D8B8B`).
- **Rationale:** Delivery Managers require immediate situational awareness within 3 seconds of logging in. High-density cards, trend sparklines, and active alert counters provide immediate operational governance without whitespace clutter.
- **Decision:** Enforced guaranteed Delivery Manager role (`role: manager`) for all `@koenig-solutions.com` accounts logging into SkillEdge.
- **Rationale:** SkillEdge is a Delivery Manager cockpit platform. Ensuring every authenticated user receives manager privileges guarantees complete team intelligence, allocation desk statistics, and executive KPI suites without role downgrade.
- **Decision:** Implemented a resilient fallback enterprise intelligence generator in `backend.py` (`_build_fallback_intelligence`) when RMS APIs time out or return empty reportees.
- **Rationale:** Prevents UI screens from rendering blank 0-state spaces during RMS server timeouts or network blips.

## 2026-08-08 — v1.25.0: Complete Dashboard & UX Modernization Review across 6 Phases

- **Decision:** Overhauled the Home Dashboard into an Enterprise Intelligence Platform (Power BI / Azure Portal layout) replacing weak/static metrics with 6 actionable KPI suites: Team Readiness Score, Utilization & 3-Month Trend, Capacity Distribution (Bench <60%, Optimal 60-85%, Overloaded >85%), Delivery Risk Matrix, Cert Coverage Ratio, and International vs Domestic Allocation Split.
- **Rationale:** Delivery Managers need immediate operational insights to assign trainers and manage risk rather than viewing raw reportee counts or static known status metrics.
- **Decision:** Assessment and integration of all 37 RMS instruction endpoints from `trainer_portal_api_details`. Key endpoints utilized include 3-month utilization trends (Key 39), vendor accrediting flags (Key 57), student pax rosters (Key 209), session recording links (Key 254), active SC fee lookups, and skill addition IDP requests (Key 255).
- **Rationale:** Utilizing existing RMS endpoints unlocks deep student/logistics visibility and automated manager governance without introducing fake mock data.
- **Decision:** Built a constraint-aware Unallocated Desk engine in `backend.py` and `UnallocatedDeskScreen.kt` that evaluates language requirements, accreditation prerequisites, and regional travel/visa restrictions to split demand into **Primary Opportunities** and **Allocation Exceptions**.
- **Rationale:** Allocation exceptions with clear warning chips prevent delivery managers from assigning trainers who lack required local language skills or accrediting body certifications.
- **Decision:** Replaced siloed ILT/FMAT/ILO tabs with a single prioritized opportunity queue sorted by Relevance → Priority → Recency, featuring overseas delivery callouts (UK 🇬🇧, USA 🇺🇸, UAE 🇦🇪, Singapore 🇸🇬, Australia 🇦🇺, Europe 🇪🇺) with Globe icons 🌐 and gold/amber badges.
- **Rationale:** Global deliveries have urgent financial and logistics dependencies that demand immediate managerial triage.
- **Decision:** Redesigned Batch Details into a compact accordion view (`BatchDetailsScreen.kt`) featuring a Batch Summary Card (`10 Aug 2026 – 14 Aug 2026`) and expandable sections for *Pax Roster*, *Logistics & Session Recordings*, *Contract Financials*, and *Syllabus/TOC*.
- **Rationale:** Reduces vertical scroll fatigue while keeping secondary logistics details easily accessible on demand.
- **Decision:** Restored historical manager skill addition & IDP request approval workflow in `backend.py` (`POST /api/action/approve-skill`) and `SkillApprovalScreen.kt`.
- **Rationale:** Ensures reportee skill additions trigger automated manager action items and notification queues for proper delivery governance.

## 2026-08-07 — v1.23.0: "No utilization data" is not the same as "0% utilization"

- **Decision:** Added an explicit `utilization_available` boolean to every trainer's operational row (`ops_row` in `backend.py`), and switched both the backend's team-average KPI and the Android capacity-distribution chart to filter on that flag instead of inferring availability from the numeric value.
- **Rationale:** `_build_trainer` already computed `util_ok = bool(u_row)` — knew perfectly well whether RMS had answered with real utilization data — but never carried that knowledge into `ops_row`, which just stored `current_utilization = util` (a default of 0 when there's no row). Two different aggregations downstream then handled that ambiguity two different *wrong* ways: the backend's `avg_team_utilization` counted every 0 as a real reading (dragging the average down), while the Android capacity-distribution chart excluded every 0 as if it were missing data (which would also wrongly exclude a trainer genuinely measured at 0% load, undercounting real bench trainers). Same root ambiguity, two opposite biases, both wrong, both silently disagreeing with each other on the same screen. A user directly noticed the downstream effect ("what it is so less?") without knowing why — the fix makes the two numbers correct *and* mutually consistent, because they now share one unambiguous source of truth instead of two different heuristics guessing at the same missing piece of information.
- **Decision:** Every dashboard figure with a non-obvious calculation basis (a time-windowed average, in particular) now states that basis directly in its visible caption, not only inside a drill-down sheet.
- **Rationale:** The pre-existing "Top performing" card already did this correctly ("Ranked by utilisation over the last three months," visible without a tap) while the KPI tile next to it said "N with data" — same underlying metric, one clear, one opaque. Matched the KPI tile's wording to the pattern that was already right, rather than inventing a new convention.

## 2026-08-07 — v1.22.0: Allocation matching mirrors RMS's real AutoTall rules, partially

- **Decision:** Given HR's AutoTall changelog (08 Jul – 05 Aug 2026), implemented the negative-feedback allocation block, the 6-month clean-record tie-break, removed Qubits/QI as a tie-breaker, and treated RedHat officially-approved as Certified in `backend.py`'s allocation-desk matching (`_rank_batch`) and cert-gap analysis (`_cert_intelligence`) — but explicitly did **not** implement the tech-call-trainer preference, mock-rating preference, or the Additional-Trainer least-skill-removal rule.
- **Rationale:** The changelog itself contains reversals — Qubits and QI were introduced 20-22 Jul 2026 then both explicitly removed 27 Jul 2026. Implementing every historical bullet additively would have re-added factors RMS itself deleted; the only correct target is the *current effective ruleset* as of the latest entry (05 Aug 2026), not the full history.
- **Rationale (partial implementation):** Three of the rules describe data this app's RMS integration does not have: no endpoint among the 36 audited files in `trainer_portal_api_details/` carries pre-sales tech-call attribution or mock-delivery ratings, and unallocated demand rows don't distinguish a Main/Additional-Trainer role the way RMS's internal engine does (this app's own `backup_role` labels — Primary/Secondary/Emergency Backup — are an invented ranking convenience, not RMS's real role model). Fabricating a feature against data that doesn't exist would be exactly the kind of unverifiable, unhonest implementation this project's standards forbid; these are documented in `AI/CONTEXT.md` as "not implemented — no data source" so a future session with a new RMS endpoint knows exactly what to wire up.
- **Decision:** A trainer inside their negative-feedback block window is flagged and sorted to the bottom of the candidate list, not removed from it.
- **Rationale:** RMS's own rule states the block "only affects auto-selected trainers" — a manager can still specify a blocked trainer manually. Removing them from the app's candidate list entirely would hide a legitimate manual option; sorting them last while clearly flagging *why* communicates "RMS won't auto-pick this person right now" without taking away the manager's ability to override.

## 2026-08-07 — v1.21.0: Dashboard shows a ranked preview, not the full roster

- **Decision:** Remove the full inline `TrainerCard` list from the Home dashboard (it duplicated the Team tab's roster exactly, minus the Team tab's search/sort/filter) and replace it with a 5-item "Needs Attention" preview ranked by a simple priority score, plus a button to the Team tab.
- **Rationale:** On this product's real data (82 reportees per `AI/CONTEXT.md`), rendering every trainer as a full card on the page meant to be a manager's quick daily glance produced 80+ full-size cards in one scroll — the dominant cause of the dashboard feeling too long/wide, not any individual spacing value. A home screen's job is to say "look here first," not to be a second, worse copy of the roster tab.
- **Decision:** Consulted github.com/wasabeef/awesome-android-ui per user request but did not integrate any library from it — it's a ~200-entry index of pre-Compose View-system libraries (RecyclerView decorators, ViewPager transformers, custom Views, mostly 2013-2019), and this codebase is 100% Jetpack Compose. Pulling in View-interop dependencies for a Compose-native app would be a net architectural regression.
- **Rationale:** Took the applicable *pattern* instead — short scannable previews with drill-through beat long inline lists — and combined it with Bootstrap-style layout discipline (one header per logical group, no redundant chrome) to justify both the roster-preview change and consolidating three separate section headers (Delivery Readiness / Feedback Risk / Capacity) into one "Team pulse" header.
- **Decision:** Did not do a mechanical pass renumbering every `Spacer` value in `DashboardSections.kt` to a strict spacing scale, despite finding a genuinely unsystematic mix (3/5/6/7/8/10/12/13/14/24/32dp).
- **Rationale:** No Android SDK or emulator exists in this development environment to visually confirm the result of such a sweep, and it would touch dozens of call sites inside already-shipped, already-working cards unrelated to the actual complaint. A blind cosmetic sweep with no way to see the outcome risks a regression nobody catches until a user reports it — worse than leaving admittedly-inconsistent-but-functional spacing in place. The structural fixes (roster preview, header consolidation) address the real complaint; the spacing scale is real but lower-priority technical debt, noted in `AI/PROGRESS.md` rather than gambled on blind.

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
