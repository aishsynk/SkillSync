# Release Notes — Phase 1 Capability Foundation (2026-09-13)

**Status: PHASE 1 FOUNDATION RELEASED — FACTUAL CAPABILITY DATA ENFORCED**

This is a backend data-integrity and foundation release. It does **not** enable trainer matching,
scoring, or AI-driven skill recommendations of any kind — those remain future, unapproved work.

## What changed

- **Removed fabricated trainer capability/certification fallbacks.** `_capability_for()`
  (backend.py) no longer substitutes invented course/certification data for 8 named trainer
  email addresses, nor a generic AZ-104/MCT fallback for any trainer with empty RMS data, nor a
  guessed 82% utilisation default. A trainer with no RMS capability data now correctly shows no
  data, not invented data.
- **Introduced the capability-domain foundation.** New `domain/capability/`,
  `repositories/capability_store.py`, `services/capability/` — models, SQLite persistence, and a
  service layer for course-capability requirements and trainer-capability evidence, with an
  explicit `DRAFT → REVIEW_REQUIRED → APPROVED → DEPRECATED` curation lifecycle. Nothing here is
  wired to any API route or app screen yet.
- **Added draft capability profiles for future validation** — 8 courses (DP-700, DP-600, AI-102,
  AZ-104, SC-300, CCNA, AWS Solutions Architect Associate, CISSP), each confirmed live against RMS
  and keyed on the stable numeric course ID (`Cid`), not title or course code. All 8 are `DRAFT`,
  authored from public vendor exam-objective pages, and require human review before they may
  influence anything.
- **No trainer matching/scoring enabled.** No route consumes the new capability layer. No score,
  percentage, or ranking is produced by any of this work.

## What did NOT change (verified, not assumed)

- No Android/UI code was modified. Direct inspection of every screen that consumes the 3 affected
  endpoints (`/api/v2/data/team-capability` + legacy alias, `/api/v2/capability/portfolio`,
  `/api/v2/capability/cert-intel`) found that the specific fields changed by this release
  (per-trainer `avg_qubits`, `utilization`) are not currently rendered by any screen — the UI reads
  `readiness_score`/`readiness_bucket` (already `intOrNull`-safe and already hidden when null,
  `TeamMemberCard.kt`) and course-level rollups that are naturally absent, not zeroed, for a
  trainer with no data. No "0%", no crash, no misleading placeholder was found or introduced.
- No new API route is exposed. `_capability_repository`/`_capability_service` are instantiated in
  `backend.py` and used only by tests and the manual seed script.
- The 8 seed profiles remain `DRAFT` — confirmed by test
  (`test_approval_lifecycle_and_authoritative_gating` and the `authoritative_only=True` default on
  every read path) and not consumed as authoritative by anything.

## Verification performed

- **Backend:** full suite, `python -m pytest tests/` → **358 passed**, 0 failed (includes 14 new
  capability-foundation tests).
- **Android unit tests:** `./gradlew :app:testDebugUnitTest` → 199 run, **12 pre-existing failures,
  all in `ScreenRenderTest.kt`**, unchanged in count and location from before this release (a
  richer Dashboard spec gap documented in prior sessions' handovers — unrelated to this backend-
  only change, since no Android source was touched). Zero new Android failures.
- **Android/UI screen audit:** every consumer of the 3 affected endpoints traced by direct code
  read (`TeamTab.kt`, `TeamMemberCard.kt`, `CoursesTab.kt`, `MainScreenViewModel.kt`) — see "What
  did NOT change" above.
- **Release/APK build:** not performed. No Android source changed this release, so there is no
  user-facing app change to package — building and shipping an identical APK would not exercise
  anything this release touched and was judged unnecessary. Flagged here explicitly rather than
  silently skipped, so it is a visible decision, not an omission.
- **Capability seed:** confirmed `DRAFT`-only via the automated test suite; not auto-run at
  backend startup, only via the manual `scripts/seed_capability_foundation.py`.
- **No accidental route exposure:** confirmed via source grep — zero `@app.route` decorators
  reference `_capability_service`/`_capability_repository`.

## Deployed Git commit

See the commit this file was released alongside (git log for the exact hash — recorded in
`AI/PROGRESS.md`'s handover entry for this date).

## User-facing benefit

None directly visible in the app yet — this is a correctness/integrity fix plus foundation work.
The concrete benefit: three production endpoints (team-capability, capability portfolio,
cert-intel) that could previously show fabricated skill/certification data for 8 specific
individuals (or a generic invented AZ-104/MCT record for anyone with no RMS data) now show
accurate, honest data instead.

## Explicitly not claimed

This release does **not** ship: capability intelligence completion, a new matching engine, AI
skill matching, or any change to Plan. Phase 2 (matching engine) has not started.
