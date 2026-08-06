# SkillEdge / Manager OS — Decisions

Important decisions and their rationale. Add new entries at the top (newest first).

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
