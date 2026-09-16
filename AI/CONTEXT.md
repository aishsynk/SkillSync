# SkillEdge / Manager OS — Project Context

## Product identity: SkillSync (corrected 2026-09-15 — InTouch Intelligence rebrand and the LinkedIn Capture feature were unauthorized and have been fully removed)

- **The Android application is SkillSync.** The `2026-09-14` "InTouch
  Intelligence branding" entry that previously lived here, and the
  `aishsynk/InTouch` release repository it pointed at, are **not
  authoritative** — see `AI/DECISIONS.md` (2026-09-15, "Production-
  contamination recovery") for the full correction. There is no pending
  rebrand in flight.
- **Canonical release destination (permanent):** all SkillSync releases —
  tags, GitHub Releases, APK/AAB assets — go to
  **`https://github.com/aishsynk/SkillSync/releases`**, and only there. See
  `AI/DECISIONS.md` (2026-09-15, "Canonical release destination"). Never
  `aishsynk/InTouch` or any other repository.
- **User-facing identity:** installed app label "SkillSync" (debug: "SkillSync
  Debug"), login wordmark "SkillEdge" with tagline "DELIVERY INTELLIGENCE",
  dashboard header "SKILLEDGE · EXECUTIVE CONSOLE", brand composable
  `SkillSyncLogo`/`SkillSyncWordmark` (`core/ui/Branding.kt`), notification
  channel "SkillSync Alerts". This internal/visual SkillEdge identity
  alongside the SkillSync product name is intentional, not a contradiction.
- **LinkedIn Capture is not a SkillSync feature.** It was introduced
  (`ec701f70`) and later compounded by the same rebrand commit chain, and has
  been removed in full — API client, parser, repository, ViewModel, screen,
  `LinkedInShareStore`, navigation routes, dashboard tile, manifest
  `<queries>`/SEND intent-filter, `LINKEDIN_BASE_URL` build config, and tests.
  Do not re-add a LinkedIn-shaped surface without an explicit new operator
  instruction.
- **Versioning: one continuous `3.80.x` patch train, never bump MINOR without
  explicit operator instruction** (established 2026-09-15 — see
  `AI/DECISIONS.md`).
  **The ONLY authoritative source for the current release/version baseline
  is `https://github.com/aishsynk/SkillSync/releases` — never
  `build.gradle.kts`, local git history/tags, `AI/PROGRESS.md`,
  `AI/CONTEXT.md`, `AI/DECISIONS.md`, memory, or prior chat text.** Before
  proposing, calculating, committing, tagging, or publishing any version:
  (1) read the highest published release at that URL, (2)
  `versionCode = highest published versionCode + 1`, (3) for a normal new
  feature release, also increment the `3.80.x` patch digit — reuse the same
  `versionName` with only a higher `versionCode` solely when explicitly
  told this is a rebuild/hotfix of the same released version. Any version
  number written into this file or any other doc is a historical snapshot
  at time of writing and decays immediately — re-check GitHub Releases
  every time, don't trust what's written here. Never change `applicationId`
  (`com.example.skillsync`) or the signing key (in-place-upgrade rule).
- **Android iconography:** launcher/adaptive/round assets are the original
  SkillSync set restored from `v3.80.7.182` (the last known-clean tag). The
  InTouch-generated `intouch_symbol`, `ic_notification_intouch`, and
  InTouch-generated foreground/monochrome launcher assets have been deleted.
  In-app logo = original SkillSync transparent symbol.
- **Honesty rule:** unit-test baseline is **not** zero — 244 run / 11
  pre-existing failures (7 `ScreenRenderTest` + 4 `PilotScreenshotTest`); the
  count dropped from 254 solely because the 4 LinkedIn-only test files were
  removed, not because any test was silently dropped from the real product
  surface. Gate-clean = 0 new failures, lint delta 0 or better. "All tests
  pass" must never be written. Current lint 6E/79W/3H (pre-existing,
  `ViewModelConstructorInComposable` in the Pilot screenshot test harness —
  unrelated to this recovery).

## Design V2 (Advanced) — reference visual direction (proposed 2026-09-13, not yet implemented)

- **Status:** Visual proposal only (published as a Claude Artifact), not yet reflected in any
  `.kt` source. It is the reference direction for future UI work and **conflicts with** the
  uncommitted light-first `ManagerCommandCentre.kt` WIP already in the working tree — see
  `AI/DECISIONS.md` for the unresolved-conflict note before doing further screen work.
- **Base palette:** dark-only "Command Centre" theme — navy `#0B1F5E` → royal `#144EA6` →
  azure `#1976D2` → brand `#2196F3` → sky `#42A5F5` → cyan `#00BCD4`/aqua `#26C6DA`, on
  elevation surfaces `#0D1117`/`#121826`/`#172030`/`#1E293B`. Status hues (warning amber
  `#F0A828`, critical rose `#F0556B`, opportunity violet `#B388FF`) are semantic-only, never
  reused as brand/accent. No light-theme variant — a deliberate single-theme commitment.
- **Widget vocabulary** (translated from Color Admin's admin-dashboard depth, not copied
  visually): comparison-to-last-period stat cards (every KPI shows a vs-last-week delta, not
  a bare number); an **Allocation Funnel** (open demand → matched → scheduled → delivered)
  replacing a flat KPI count; ranked lists with a trailing stat (top-utilised trainers,
  top-exposure courses); a regional coverage breakdown (flag + bar + %) for international
  exposure; a certification-renewal **campaign card** (progress bar + days-left + one CTA);
  a dense sortable data table reserved for tablet/foldable width only.
- **Nav architecture:** five bottom tabs — `Today` (command centre) / `Plan` (demand &
  capacity) / `People` (intelligence centre, drills into Trainer 360) / `Delivery`
  (operations console) / `More` (grouped operations drawer: Courses, Opportunities,
  Calendar, Tasks, Approvals, Reports, Communication, Skills & Certifications, Settings —
  global search/copilot lives in the top bar, not in `More`).
- **Motion split:** iOS-side rules (spring dismiss, rubber-band scroll, haptic on commit, no
  bounce on value count-up) and Material-side rules (ripple confined to tap target,
  shared-element container transform card→detail, FastOutSlowIn tab/enter transitions) are
  applied per-interaction, not blended into one generic animation style.
- **Artifact:** `https://claude.ai/code/artifact/c6a9ec1d-057d-4f72-b3c2-c500bb9599dc`
  ("Manager Operations OS") — full token table, ten-screen phone-frame mockups, and build
  order live there; this entry is the durable summary for future sessions that can't open it.

## Capability Intelligence Foundation, Phase 1 (implemented 2026-09-13)

- **New layer, backend only, no routes wired yet:** `domain/capability/models.py` (dataclasses:
  `Capability`, `CapabilityAlias`, `CapabilityRelationship`, `CourseCapabilityRequirement`,
  `CourseCapabilityProfile`, `CapabilityEvidence`, `TrainerCapability`; enums `RelationshipType`,
  `EvidenceType`, `MappingStatus`), `repositories/capability_store.py` (`CapabilityStore`, SQLite,
  6 tables, same `contextmanager`/WAL/`available` convention as `OpportunityStore`), and
  `services/capability/capability_service.py` (`CapabilityService` — the only path allowed to
  gate what counts as "authoritative"). Instantiated in `backend.py` as `_capability_repository`/
  `_capability_service`, next to the other stores — not consumed by any endpoint yet.
- **Course identity is the RMS numeric `Cid`, never title or `course_code`** — both were
  confirmed live (2026-09-13 Phase 0 probe) to be unreliable: `course_code` frequently blank/
  inconsistently cased, multiple catalogue rows can share a title (e.g. "DP-700T00..." vs. the
  1-day "DP-700 Exam Prep", different Cids).
- **Evidence-gated by construction, not convention:** `CapabilityStore.trainer_capabilities()`
  only ever returns rows with at least one linked `capability_evidence` row — there is no write
  path that creates a "verified" trainer capability without evidence attached in the same
  transaction. This is the literal implementation of "NO VERIFIED DATA → NO CAPABILITY CLAIM."
- **Approval lifecycle:** `DRAFT → REVIEW_REQUIRED → APPROVED → DEPRECATED`. Only `APPROVED`
  is in `MappingStatus.AUTHORITATIVE`; `CapabilityService.get_course_capability_profile(...,
  authoritative_only=True)` (the default, and what any future matcher must use) returns `None`
  for anything else. `approve_course_capability` refuses a blank approver.
- **8 validation courses seeded as DRAFT only** (`scripts/seed_capability_foundation.py`, run
  manually, not at import time): DP-700 (Cid 18301), DP-600 (15509), AI-102 (9716), AZ-104 (9055),
  SC-300 (9748), CCNA (11405), AWS Solutions Architect Associate (899), CISSP (742). Requirements
  drawn from each vendor's public exam-objectives pages, not from course titles.
  `source="agent_authored_pending_human_review"` — none are `APPROVED`; a named human curator
  must review each before it may influence any future allocation decision.
- **Fabrication removed:** `_capability_for()` (backend.py) no longer contains the 8 named-
  trainer hardcoded profiles or the generic AZ-104/MCT fallback. Empty RMS data now produces an
  honest state: `courses: []`, `avg_qubits: None` (not `0`), `utilization: None` (not a guessed
  `82`), `capability_data_available: False`. See `AI/DECISIONS.md` for the removal decision and
  `tests/test_capability_foundation.py` for the regression tests (including the two named
  fabrication tests and a static source-scan asserting the removed emails never reappear).
- **What Phase 1 explicitly does NOT do:** no scoring, no ranking, no semantic/LLM extraction, no
  Plan UI change, no bulk migration of existing RMS `trainerDetails` rows into
  `trainer_capabilities` (that requires normalization/confidence decisions deferred to the
  matching-engine phase — see `AI/PLAN_MATCHING_ENGINE_AUDIT_2026_09_13.md` §L).

## Manager-as-trainer personal calendar already exists server-side (`_personal_calendar_build`, backend.py:7131)

- `_personal_calendar_build(email)` is explicitly written to be identity-agnostic: "the same for
  a trainer or a manager who also delivers." It calls the real RMS `prevUpcoming` endpoint
  (api_key 16, "Previous & Upcomming Assignments" — request fields `Startdate/Enddate/Email`,
  response carries the misspelled `StarDate`, not `StartDate`; both are handled via fallback)
  and returns `{email, assignments, current, upcoming, past (last 10), off_bands, utilisation_series,
  generated_at}`, each assignment shaped `{course, vendor, mode, participants, start_date, end_date,
  location, state}`.
- Android already has a full screen for this — `MyScheduleScreen.kt` /
  `MyScheduleViewModel` calling `RetrofitClient.instance.trainerCalendar(email)` — reached via
  `onOpenMySchedule`, previously wired only into the profile menu.
- `onOpenProfile` on the Today header already opens **Trainer360Screen for the signed-in
  manager's own email** (`onTrainerClick(email, name)` in `MainScreen.kt`'s `DashboardTab`) — so
  "My Profile" for a manager who also trains was already Trainer 360, not a screen that needed
  building from scratch.
- 2026-09-13: `onOpenMySchedule` is now also forwarded into `ManagerCommandCentre` (Today) and
  surfaced as a "Your schedule" row directly on Today, not only inside the profile menu — see
  [[decision-manager-as-trainer-today-entry]].
- **RMS doc reliability warning** (from a full pass over
  `trainer_portal_api_details/*.txt`, 2026-09-13): roughly a third of those 36 files document
  their response as the literal placeholder `"[...results...]"` — no real field names at all
  (`Get_Utilization`, `Upcoming_Assignments`, `Get_Unique_Certifications_Count_Value`, and 8
  others). Do not write UI or backend code against an assumed schema for those without a live
  authenticated probe first — see [[feedback_verification_standards]].

## Manager Communication Architecture (effective 2026-09-15 — supersedes the
## 2026-09-12 "User Message / My Message Precedence" entry below; do not
## relitigate this)

- **There is no external `[User Message]` input in manager communication.**
  Aishwar (or whichever manager account is signed in) is always the sender.
  The 2026-09-12 entry's "`user_message` is the PRIMARY conversational
  intent" rule was the exact anti-pattern the Phase 1/2 architecture
  restructuring (2026-09-15, branch `claude/nifty-shannon-yrkzvc`) removed:
  a caller-supplied free-text field was able to drive what the system
  claimed as business fact. It is retired everywhere confirmed reachable by
  a repo-wide search (Android `feature/communication/*`, backend
  `services/communication/*`, `backend.py`'s `/api/v2/message/compose` and
  `/api/v2/communication/generate` routes) — see
  `docs/phase2-communication-classification.md` for the full audit and file-
  level evidence.
- **Canonical pipeline**:
  verified domain facts (repository/backend-computed) → deterministic
  calculations (owned by the domain/KPI layer, never by communication) →
  structured business insights → `CommunicationContext`/`CommunicationRequest`
  → `CommunicationPurpose` → `CommunicationPolicy`
  (`CommunicationContextFilter` sanitisation + `CommunicationValidator`
  factual-integrity checks) → message composition
  (`CommunicationComposer`/`ManagerCommunicationComposer`) → channel
  rendering (Teams/Viber-specific emphasis markers) → delivery (share
  intent/clipboard/Viber queue — a separate concern from composition).
- **Manager instruction is optional and subordinate to verified facts.**
  `managerInstruction`/`my_message` may shape tone, emphasis, which purpose
  to lead with, or a specific point to mention. It can never override,
  replace, or invent a verified fact (utilisation, cert gaps, learner
  rating, batch/delivery/certification data, deadlines, responsibilities).
  `ManagerCommunicationComposer` always composes evidence-derived sentences
  first and can only append one instruction-derived sentence; the shared
  `CommunicationValidator.validate`/`validateFactualIntegrity` (and its
  Python mirror) reject a composed message that states a course/day/number/
  name absent from verified facts or the instruction text itself. Covered
  by `ManagerCommunicationComposerTest`, `CommunicationEngineTest`,
  `CommunicationViewModelTest`.
- **Communication does not calculate authoritative KPI values and does not
  fetch arbitrary trainer/KPI/batch data itself.** It consumes evidence
  already prepared by the domain/repository layer that called it
  (`CommunicationEvidence`, `verified_context`). The one Android-side
  deterministic calculation identified anywhere in the codebase
  (`projectNextUtilization`/`averageMonthOverMonthDelta`, a linear trend
  projection) lives in `feature/home/DashboardSections.kt`, not in any
  communication file — flagged as architecturally misplaced but out of
  Phase 2's scope (see `docs/architecture-assessment.md`).
- **Composition and delivery are separate.** `BatchShare`/`BulkBatchShare`
  own actual send/queue mechanics (`copyMessage`/`shareAnywhere`/`openUrl`)
  separately from their message-building functions; `ManagerCommunicationComposer`/
  `CommunicationComposer` only ever return text, never send anything.
- **Structured operational exports are not automatically manager prose
  generation.** `BatchShare`/`BulkBatchShare` produce a fixed, mandated,
  labelled-field broadcast ("Course :", "Schedule :", "Delivery Mode :", …
  — "the RMS allocation broadcast, applied literally") for trainer-facing
  assignment notifications. This is classified `STRUCTURED OPERATIONAL
  SHARE`, a different responsibility from manager-to-team/reportee prose
  communication, and is correctly not routed through
  `CommunicationComposer`. It must not independently decide tone, manager
  intent, appreciation/correction framing, performance interpretation, or
  KPI meaning — confirmed by direct code review it does not (its only
  "decision" is a fixed field-presence check: a label prints only when RMS
  actually returned that field). If either `BatchShare` implementation is
  ever extended to make a tone/intent/performance decision, that
  responsibility must be extracted into the canonical communication
  pipeline; the structured field-assembly and delivery mechanics may stay
  separate.
- **Legacy pre-2026-09-15 pipeline description (retained for history, not
  current instruction)**: `User Message + My Message + Current Context` →
  `Intent Analysis` → `Recipient/Relationship Understanding` → `Situation
  Evaluation` → `Important Fact Selection` → `Irrelevant Fact Rejection` →
  `Sensitive Fact Rejection` → `Action Determination` → `Message Planning` →
  `Natural Generation (LLM or Intelligent Native Engine)` → `Policy/
  Formatting Validation` → `Final Message`, with `user_message` as PRIMARY
  conversational intent when present. Do not restore this.
- **Clean Separation of Concerns**:
  `ContextSelector` outputs structured meaning only (`CommunicationPlan` / `ContextSelectionPlan`) with zero finished prose or markdown sentences. `Composer` generates the natural language.
- **Single-Priority Focus vs. Metric Dump**:
  Automated communications evaluate the entire operational situation (demand, capacity, capability, quality risk) and select ONLY the 1–2 critical actionable facts. Irrelevant facts (e.g. participant counts or certification gaps during an unstaffed batch alert) are rejected into `rejected_facts`.
- **Sensitive Fact Screening**:
  All sensitive keys (`salary`, `margin`, `billing_rate`, `client_billing_key`, `retention_flag`, etc.) are removed before message planning and reported in `sensitive_facts_removed`.
- **Authoritative Formatting Policy (MS Teams & Viber)**:
  - Layout: Exact 3-part layout (Line 1: Greeting, Line 2: Main message, Line 3: Closing) separated by blank lines (`\n\n`).
  - Names: Italics only (`*Name*`).
  - Key Action: Bold only (`**Action**`), max 1 bold action per message. Never bold the entire message.
  - Dates & Timeframes: Bold + Underlined (`__**Friday**__` or `__**next week**__`). Max 1 date/time reference per message. Never underline non-date text.
  - Prohibitions: Zero emojis, zero bullet points, zero numbered lists, no conversational filler.
- **Intelligent Suppression**:
  When baseline operations are steady with no unstaffed batches, critical blockers, or immediate actions required, automated messages return `NO_MEANINGFUL_MESSAGE` with `requires_communication=False` and an explicit `no_message_reason`.
- **Generator Reality Transparency**:
  In standard environments without OpenAI or Azure keys configured, the service honestly reports `generation_mode="DETERMINISTIC_GENERATOR"`.
- **Parity Across Backend & Android**:
  The Python service (`services/communication/`) and Android native engine (`feature/communication/engine/`) share identical architecture, models, selection rules, and validation logic.

## Demand detail — assignment level & team-skill panel (effective v3.72.0)

- RMS `unallocated` field **`assignment_sl`** (a number 1-10) = the skill level a trainer must
  hold for the batch. `_demand_rows` maps it to **`assignment_level`**. Shown on the demand
  card ("Level N"), the batch-detail fact grid, the skill-level filter, and the share message.
- `_rank_batch` records each matched trainer's held RMS `SkillLevel` (from `trainerDetails`,
  key 75) on the matched course. Each candidate carries `held_skill_level`,
  `required_skill_level`, `meets_required_level`. Under-levelled trainers are penalised in
  ranking, never hidden. The skill gate is numeric (old "expert/advanced" text buckets were
  dead — RMS sends a number).
- `_team_course_skill(team, course, vendor, required_level)` → per demand row as `team_skill`:
  every reportee (matched or not), `{trainer_name, trainer_email, is_self, held_skill_level,
  has_skill, meets_required}`, sorted eligible → holds-below-level → no-skill.
- Android `BatchDetailScreen` renders this as the **Team skill on this course** panel with a
  per-row Mark/Raise that opens `MarkSkillDialog` pre-selected + pre-filled to the required
  level. `MarkSkillDialog(initialLevel=…)`.

## Secrets, capability matrix & canonical routes (effective 2026-09-04)

- RMS service-account fallback credentials live only in `rms_service_credentials.py`
  (`FALLBACKS` dict). `backend.py` `_ev(name)` reads the env var, then that map. No plaintext
  secrets in `backend.py` or `trainer_portal_api_details/*.txt`. `.env.example` lists every
  `SKILLEDGE_RMS_*` var. Set `SKILLEDGE_REQUIRE_SECRET_CREDS=1` to make `_validate_credentials()`
  hard-fail on any unset var; unset, it only warns. Render secret provisioning + RMS password
  rotation are still pending operator actions.
- `SessionManager.canManageTeam()` (manager / assistant_manager / trainer_plus) is the single
  client predicate gating every cross-person write control; backend routes gate via
  `_v2_manager_session(manager_only=True)`.
- Canonical API routes are `/api/v2/data/*` and `/api/v2/action/*`. `/api/data/*` and
  `/api/action/*` persist only as deprecated aliases; the Android client uses v2 exclusively.

## Evidence-only manager analytics (effective v3.69.3)

- Trainer 360 renders the server Trainer Index response and never calculates a substitute score from UI defaults. The current RMS-backed index measures 7 of 20 criteria and is labelled partial/floor-level with the server confidence note visible.
- With no learner feedback, sentiment percentage, classification, praise themes, and growth themes stay unavailable/empty; production responses and UI fallbacks contain no demonstration values.
- Company benchmark ratings and feedback-incident baselines stay unknown when the company feedback API is unavailable; no fallback constant or zero baseline is permitted.
- Delivery alerts navigate to Work/Delivery. Assignment IDs are not demand IDs and must not be opened as Demand detail identifiers.

## Demand evidence reconciliation (effective v3.69.4)

- RMS key 171 is a course-specific free-schedule source, not the authoritative trainer-skill inventory. An empty key-171 response means course/date availability is unknown; it never proves that nobody holds the course.
- Course-matched trainers come from the capability/candidate path (including key 75). A demand card must not display an absence-of-skill warning when it contains matched candidates. Empty availability data is labelled “Course availability not verified” and the batch-detail check is recommended.

## Delivery roster and action-queue semantics (effective v3.69.1)

- `assignmentPax` is a current roster snapshot. Comparing it with an assignment's expected
  participant count proves only a roster shortfall; it does not prove that participants
  dropped because RMS supplies no previous roster snapshot in this flow.
- Action `Start`, `Close`, `Escalate`, `Reassign`, and `Reopen` operations update the durable,
  audited SkillEdge action queue only. They do not allocate trainers, update RMS source
  records, send messages, book exams, or otherwise execute the underlying operational work.
- Action mutations are manager-only. A reportee using the shared shell may read their scoped
  queue, but mutation controls must not be presented.

## Assigned trainer skill level (effective v3.69.2)

- Neither unallocated demand (RMS 190) nor previous/upcoming assignments (RMS 16) returns a
  skill-level field in live responses. Do not describe a skill level as an assignment requirement.
- Auto Tall's trainer/course level comes from `trainerDetails.SkillLevel` (RMS 75). Assigned-batch
  messages may show `Trainer skill level: Lx` only after an exact normalised trainer-course match;
  otherwise omit it and mark the source unavailable.

Stable, high-level knowledge about the project. Update only for durable facts.

## Manager-first product contract
SkillEdge is a manager decision system, not a trainer/skill/batch record browser. Every primary screen must lead with the management question it resolves: attention, capacity, overload, allocation, delivery risk, capability gap or required action. Production readiness includes real-phone layout geometry (order, bounds, overlap, viewport density), not only compilation and text-presence tests.

## Demand read-safety contract (effective v1.43.0)
`GET /api/data/allocation-desk` and every recommendation helper it calls must be side-effect free. Aishwar's qualifying international FMAT/ILT result is a non-persisted manager recommendation with suggested Skill Level 8 and availability metadata. Only the explicit `POST /api/action/mark-skill` route may write a trainer skill to RMS.

## Aishwar international-demand policy (effective v1.42.0)
For the exact account `aishwar_v@koenig-solutions.com`, a foreign-location FMAT or ILT batch with at least 75% course match is automatically recommended with Skill Level 8 and the next verified available weekend. This is recommendation metadata only and never writes to RMS. It does not apply to other trainers, India/domestic batches, ILO, unknown locations, or matches below 75%.

## AI Mind message composition contract (effective v3.56.0)
All manager-to-reportee and team-level generated messages, standpoints, evaluations, and digests must cross-reference multi-dimensional signals rather than using generic filler:
- **Cert Gaps + Live Demand**: Quantify opportunity cost in participant-days and batches unlocked, as well as Trainer Index points gain (~200 points per exam).
- **Learner Feedback Themes**: Extract and cite deterministic theme clusters (depth of knowledge, practical labs, clarity, pacing) for strengths and coaching.
- **Career & Readiness**: Reference Qubits knowledge score, Trainer Index tier standings (Platinum/Gold/Silver/Bronze), leave balances, and ramp stages (`onboarding`, `first-deliveries`, `established`, `stalled`).
- **Group Broadcast Safety (Hard Rule)**: Team broadcasts must NEVER name an individual for negative signals (bench, feedback flags, cert gaps). Names appear ONLY for positive recognition.
- **Teams/Viber Prose Formatting**: Greeting `Hello _First_,`, blank line, sanitised body with at most one `**bold**` action and one `__underlined__` time reference, blank line, italicized closing `_Thank you..._`, maximum 1000 characters, no hyphens, bullets, emojis, or dashes.

## Sign-in & roles (password gate restored 2026-09-11)

Sign-in is by **work ID** (initials, e.g. `aishwar.c`; client appends
`@koenig-solutions.com`) **plus a password**. `POST /api/auth/check` returns the role with
`needs_password: true` for every privileged role; `POST /api/auth/login` requires the
password — first time it is the RMS employee code (bootstrap, `must_change`), then the
account owner sets their own via `/api/auth/set-password`. `_classify_identity`:
- **manager** — owns a non-empty RMS `reportees` roster, OR anything not positively flagged
  otherwise, OR any case where the RMS roster call did not answer (**fail-open** — an RMS
  blip must never strip a manager to an empty view).
- **trainer_plus** — positively flagged `TrainerPlus=Yes` in a roster.
- **assistant_manager** — `Designation` in a roster reads as a manager title.
- No account is ever `reportee`. The password / employee-code path and the
  `reportee_store.py` credential table are the live password mechanism (reversed the
  2026-09-04 decision that made them dead code). Android never attempts a silent email-only
  re-auth: a 401 clears the stale session and routes the user back to Login.

The reportee-scoped routes (`/api/v2/reportee/*`, skill-request approval flow, self-mark
ceiling) still exist but nothing routes to them — no session carries `role == "reportee"`.
`reportee_store.py` is still used for its non-credential job: caching the RMS roster
(`remember_roster`) so `_resolve_manager_email` / directory lookups stay warm.

## What it is

AI-assisted delivery-intelligence workspace for Koenig Solutions delivery managers.
A manager signs in with their official email; the backend fetches scoped reportees from
live RMS APIs, runs the intelligence engines, and serves a unified payload that powers
the **Android app** — the shipped product. The SeanTheme web dashboard it once served
is legacy and lives under `SkillEdge_Local/`.

**Product scope (operator decision, 2026-08-11):** a Delivery Intelligence and Resource
Readiness Platform. Delivery intelligence, resource planning, trainer readiness,
capability management, demand coverage, allocation, certification intelligence and
capacity planning. **Not** finance, payroll, sales, CRM or revenue: `Total Fee` and
`Currency` are stripped at the backend boundary and must never reach the device.

## Stack and entry points (corrected 2026-08-12)

The shipped product is an **Android app** backed by a **single-file Flask service**.
This section previously described `server.py` + `backend/app.py` + a SeanTheme
Color Admin web frontend; that layout now lives only under `SkillEdge_Local/`
and is **not** what is deployed. It is **not a .NET project** — there is no
`.csproj` and no MSBuild step.

**Backend** — `backend.py` at the repo root, deployed to Render.

```bash
python -m pytest tests/ -q          # 123 tests
```

Production: `https://skilledge-backend-fpcl.onrender.com`, auto-deploys on push
to `main`. `SKILLEDGE_ENV=production` requires the `SKILLEDGE_RMS_*_USER` /
`_PASS` env vars; plaintext fallbacks in `_APIS` remain as migration defaults
until those are set as Render secrets.

## Heavy endpoints: partial-first + background warm (effective 2026-08-30)

`unified-manager-intelligence` (dashboard), `capability/portfolio`, `hr/monthly-report`,
`report/weekly` and `team/calendar` each fan out many per-trainer RMS calls and cannot
answer inside the client's 60s read timeout on a cold cache — which is why screens sat on
a spinner. All five now use `_serve_or_warm(cache_key, view_func, build_path, fast_payload)`
(near the cache helpers in `backend.py`), mirroring the older `allocation-desk` pattern:

- The retained last-complete payload is served immediately with `refresh_in_progress` +
  `cache_age_seconds`; a rebuild runs in a daemon thread via an internal `?_build=1`
  request when the payload is older than `_WARM_TTL` (150s) or `?refresh=1` is passed.
- A cold call (nothing retained) waits up to `_WARM_FIRST_WAIT` (45s) for the first build,
  then falls back to a cheap skeleton payload carrying `loading: true`.
- The `?_build=1` path of each endpoint ends by calling `_warm_store(cache_key, payload)`.
- `?refresh=1` calls `_warm_purge(needle)`. A failed background rebuild never overwrites
  the retained payload. `loading: true` bodies must never overwrite the client's local
  snapshot (already enforced in `ManagerRepository.cachedMap`).

## Manager-view features (effective v3.51.0)

- **Opportunity cost** - `_team_opportunity_cost(team_trainers, demand_rows)` -> `manager_kpis.opportunity_cost`
  (+ top-level) on `unified-manager-intelligence`: `open_batches_coverable`/`open_batches_total`,
  `trainer_days_at_stake`, `by_cause`, `top_courses`. `_build_trainer` now exposes `skill_courses`/
  `skill_vendors` per trainer for the match. Dashboard shows it as the "Demand left on the table" KPI.
- **`GET /api/v2/manager/priorities?manager=`** - `_priorities_build` -> ranked worklist (kinds:
  `unstaffed_demand`, `one_to_one`, `overload`, `cert_gap`, `action_overdue`), each with
  `severity`/`due`/`target_type`/`target_id`/`rank_score`. `_serve_or_warm` cache `priorities::<mgr>`.
  UI ("This Week" screen) is pending - v3.52.0.
- **Feedback analytics** - `_feedback_analytics(email, months=12)` (shares the key-244 cached fetch)
  adds `trend` (monthly {month, avg_rating, count}), `trend_direction` (improving|declining|steady,
  last 3 vs prior 3 months, +/-0.2), `themes` (up to 5 keyword clusters: pace / depth / labs-hands-on /
  clarity-communication / knowledge / engagement, each {theme, mentions, sentiment, sample}).
  Spread into `_trainer_feedback_detail`; surfaced on Trainer 360 `feedback` block as
  `feedback_trend` / `feedback_trend_direction` / `feedback_themes`.

## Manager messages are composed prose (effective v3.50.0)

Every manager-to-team message is produced by `_compose_manager_message(scope, cadence, facts,
my_message)` in `backend.py` (helpers: `_reportee_message_facts`, `_open_opportunities_for`,
`_bold_first_action`, `_underline_one_timeref`). Output is house-style Teams/Viber prose:
greeting line, blank line, prose body, blank line, closing line; no emojis/bullets/hyphens;
`**` marks the one key action, `__` the one time reference; italics only for a name; <=1000 chars;
tone (appreciative / advisory / corrective / urgent) chosen from the data. Phrasing varies
deterministically by `_msg_seed(subject, period)`.

- `standpoint_note` (weekly reportee), `team_digest` (weekly + monthly team) and the HR-monthly
  `structured_feedback.message` are all this composer's output — the old labelled fact lists are gone.
- Facts fed in: current + upcoming delivery, utilisation, Qubits, learner rating + one dated quote,
  named cert gaps, negative feedback / HR counts, and **opportunity cost** — open unallocated demand
  (`_open_opportunities_for`) whose course the trainer/team already teaches but is not assigned to.
- `GET /api/v2/message/compose` (`manager`, `cadence` weekly|monthly, `target` = reportee email or
  omit for team, `my_message`) returns the composed message, reusing the warm-cached report (no extra
  RMS calls). The Android Weekly/HR compose buttons call this; `my_message` is woven in as the lead.
- Still deterministic — no LLM (project rule). The older `/api/v2/message/rewrite` +
  `_compose_rewritten` (v3.48.0) remains for rewriting free-typed manager text.

## Report messages are evidence-only (effective v3.46.0)

`_generate_manager_evaluation` (HR monthly `structured_feedback` + trainer-evaluation),
the weekly `standpoint_note`, and the Trainer 360 feedback block state **only what the
data supports**: real learner rating/trend (`_trainer_feedback_detail`, RMS key 244),
short dated learner excerpts attributed to "learner feedback", named cert gaps,
utilisation and HR-incident counts. No generic behavioural boilerplate — a dimension with
no evidence says "No … flagged from evidence this cycle." Do not reintroduce
template sentences that apply to every trainer.

`_trainer_feedback_detail(email, days=, until=)` — RMS key 244 (`trainerFeedback`,
verified live 2026-08-30): fields `AssignmentId/SCID/FeedBackDate/TrainerName/
TrainerEmail/Question/MCQAnswer(1-5)/TextAnswer`. **The endpoint ignores its
`TrainerEmail` filter and returns the whole recent set — always filter by email.** Text
rows usually carry no MCQ, so a quote's sentiment comes from the trainer's overall
average, not from the words. Quotes are cleaned of RMS speaker-label prefixes/concatenation
and require a session/trainer/content signal word.

## RMS APIs still unused after the 2026-08-30 audit
Probed live: `examCourseLinked` (215) is a link-check not a query; `trainerAvailability`
(90) returns empty for every course/date; `uniqueCertsCount` (72) returns empty (known);
`upcomingAssignments` (93) 500s and overlaps `prevUpcoming`. **Usable, not yet wired:**
`courseTechnology` (114, 21k course→technology rows), `courseDomain` (205, course→domain by
TechName), `courseList` (164). Planned for the capability-portfolio taxonomy (next release).

## Client offline-first + always-on monitoring (effective v3.46.0 / build 130)

- **Offline-first screens:** `HrMonthlyReportViewModel` and `WeeklyReportViewModel` read
  the last snapshot for the selected period from `LocalCache` and render it immediately,
  then refresh in the background and poll (≤12×3s) while the payload reports `loading`.
  They keep the last snapshot visible on a failed refresh and work offline. Cache keys:
  `hr_report_<email>_<YYYY-MM>`, `weekly_report_<email>_<mondayISO>` (via new
  `ManagerRepository.hrMonthlyReport` / `weeklyReport`). Dashboard, Demand, Trainer 360
  and the capability tabs were already offline-first.
- **`MonitoringService`** (`util/`, `foregroundServiceType=dataSync`): a foreground
  service that runs `MonitoringPass.run()` every 90s while a manager is signed in, so
  delivery alerts keep firing after the app is closed / during Doze. Shows a permanent
  `IMPORTANCE_MIN` notification on the `skilledge_monitoring` channel. Started from
  `MainScreen` (guaranteed foreground) and `BootReceiver`; stopped on logout and when
  `MonitoringPass` reports not-logged-in.
- **`MonitoringPass`** is the single shared body for both `MonitoringService` and the
  `SkillSyncNotificationWorker` WorkManager backstop (15-min periodic). The aggressive
  ~60s `enqueueRapidChain` self-chain was removed — the service owns real-time now.
- **`BootReceiver`** restarts `SyncScheduler` + `MonitoringService` on `BOOT_COMPLETED` /
  `MY_PACKAGE_REPLACED` when logged in. **`BatteryOptimization.requestOnce()`** shows the
  OS exemption dialog once.
- New manifest permissions: `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC`,
  `RECEIVE_BOOT_COMPLETED`, `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`.

**Android** — `SkillEdge_Android/`, Gradle, Kotlin + Compose.

```bash
cd SkillEdge_Android
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Release APKs are built and published **only by CI** (`.github/workflows/android-release.yml`)
so every build carries the same signing key. Never attach an APK to a chat.

## Key structure

- `backend.py` — the whole service: auth, RMS relay, intelligence layer, all routes
- `action_store.py` — SQLite persistence for the manager action inbox
- `tests/` — pytest suite covering routes, security gates and the intelligence layer
- `trainer_portal_api_details/` — the 37 RMS API contract documents (credentials live here)
- `SkillEdge_Android/app/src/main/java/com/example/skillsync/`
  - `theme/` — design tokens, `DesignSystem.kt`, severity, motion
  - `ui/main/` — dashboard, team, courses, actions
  - `ui/batch/` — demand board, batch detail, availability intelligence
  - `ui/trainer/` — Trainer 360
  - `ui/report/` — weekly copy-and-send messages
  - `ui/ai/` + `ai/` — the deterministic delivery agent and its learning loop
  - `ui/components/` — charts, notifications, shared primitives
- `SkillEdge_Local/` — **legacy** local web app (SeanTheme Color Admin). Not deployed.

## The intelligence layer (added 2026-08-12)

Availability is computed from real RMS dates, never inferred from utilisation.
Lives in `backend.py`; Android renders verdicts and never re-derives them.

- `_free_schedule` (RMS key 171) — per-course candidate pool: free-date calendar,
  visa incl. associate countries, timezone, nearest city, skill level
- `_rc_schedule` (RMS key 111) — leave, confirmed vs tentative, `SpecifiedTrainer`, `DNC`
- `availability_verdict` / `international_verdict` / `evaluate_candidate`
- `certification_verdict` / `certification_priority`
- `enrich_demand_with_availability` — additive overlay on the demand board
- `GET /api/v2/allocation/candidates` — full gated evaluation for one batch

## Key endpoints

- `GET /healthz` — liveness + session stats
- `POST /auth/login` — session cookie for a manager email
- `GET /auth/session`, `GET /auth/logout`
- `GET /data/unified-manager-intelligence` — unified payload (session-scoped)
- `POST /rms/<api>` — server-side RMS relay (credentials never reach the browser)

## RMS integrations

RMS credentials are configured per-endpoint as user/pass env pairs
(`SKILLEDGE_RMS_<NAME>_USER` / `_PASS`). Endpoints cover assignments, course lists,
schedules, trainer details/skills/availability, feedback, incidents, utilization,
certification counts, vendor certs, and an extended catalogue (upcoming/unallocated
assignments, course technology/domain/names/availability/schedule/trainers).

## Tests

Start the server, then run `python tests/smoke_test.py`.

## SkillSync Android App (separate product, same backend data)

- **Repo subfolder:** `SkillEdge_Android/` — Kotlin/Jetpack Compose, package `com.example.skillsync`
- **Backend:** `backend.py` (root) — single-file Flask, deployed to Render at `https://skilledge-backend-fpcl.onrender.com/`
- **Auth pattern:** POST `/api/auth/login` (email-only, `@koenig-solutions.com` domain check + RMS role verification). Returns `session_id`, `email`, `role`.
- **Dashboard data:** GET `/api/data/unified-manager-intelligence?email=EMAIL` — returns full web-frontend data model arrays (trainer_operations_df, trainer_current_state_df, batch_engagement_df, unallocated_demand_df, manager_action_objects, etc.) + backward-compat KPI fields.
- **Design system (v1.4.0+):**
  - `theme/Color.kt` — brand palette + `SkillColors` (light/dark) for colours Material has no slot for
  - `theme/Theme.kt` — `SkillSyncTheme` (dynamic colour deliberately OFF), `MaterialTheme.skill.*` accessor, `StatusBarIcons()` per-screen helper
  - `theme/Type.kt` — dense dashboard-oriented type scale
  - `ui/components/Motion.kt` — `Appear` (staggered entrance), `AnimatedCount`, `animateProgressFromZero`, `rememberShake`, `ShimmerBox`
  - `ui/components/Branding.kt` — `SkillSyncLogo` (floating/breathing), `SkillSyncWordmark`
  - `tools/gen_logo.py` — regenerates `ic_logo.xml` + `ic_launcher_foreground.xml`; `tools/preview_logo.py` rasterises a PNG preview (needs numpy/scipy/Pillow)
- **Key Android files:**
  - `NavigationKeys.kt` — `Login` + `Main(email)` NavKey data classes
  - `Navigation.kt` — Navigation3 based nav with currentScreen state
  - `ui/auth/LoginScreen.kt` + `LoginViewModel.kt` — email-only login, role-aware errors
  - `ui/main/MainScreen.kt` — rich dashboard: dark KPI cards, trainer roster, attention queue, demand queue
  - `ui/main/MainScreenViewModel.kt` — calls `loadData(email)` lazily
  - `data/api/SkillEdgeApi.kt` — Retrofit interface + response models
  - `data/api/RetrofitClient.kt` — OkHttp with 60s timeout (Render cold start)
- **Versioning:** versionCode=10, versionName=1.3.0 (as of 2026-08-06)
- **CI/CD:** `.github/workflows/android-release.yml` — builds release APK → GitHub Release `SkillEdge-v{versionName}.{versionCode}.apk`
- **Unallocated demand API field names (key=190, discovered empirically):** `Coursename`, `CourseSDate`, `CourseEDate`, `"Delivery Mode"` (space!), `vendor`, `"Assignment City"`, `NoOfParticipants`, `AssignmentID`
- **Utilization API (key=55):** returns single row per trainer with monthly columns `"Jun 2026": "75.77 / 43.05"` (load%/util%). Parse: split `/`, take index [1] as util%, average last 3 months.
- **Assignments API (key=16 prevUpcoming):** fields `StarDate` (typo), `EndDate`, `Course`, `Mode`, `Location`, `AssignmentId`, `NoOfParticipants`, `Vendor`.


## RMS write semantics (verified live 2026-08-07)

`Add Trainer Skill (IDP)` (key 255) — and, by strong implication, every other RMS
write — returns **HTTP 200 even when it refuses the write**. The outcome lives in
a single-key envelope named for a SQL Server FOR JSON column, whose value is a
JSON *string*, not an object:

```json
[{"JSON_F52E2B61-18A1-11d1-B105-00805F49916B":
  "{\"Status\":\"Error\",\"Message\":\"Skill already mapped for this trainer\",\"TrainerId\":7712,\"CourseId\":11232}"}]
```

The instruction file documents that value as `null`. Observed messages so far:
`"Skill already mapped for this trainer"`, `"Course not found"`. Parse it
(`backend.py::_write_status`) **and** verify by re-reading the relevant register —
neither alone is sufficient.

## Trainer Resume Details (key 87) — the only profile endpoint

The one RMS endpoint that returns a person rather than a list of their courses.
Body `{"email": "..."}`. Fields: `TrainerName`, `TrainerEmail`, `TrainerImage`,
`Languages`, `Certifications`, `Summary`, `Experience`, `Skill`,
`TrainingsDeliveredFor`, `Feedback`, `Interest`.

- List-valued fields are `#`-delimited strings.
- Absent values are the **literal string `"None"`**, not null.
- Each certification entry is `"<title>: <logo url>"`. Titles contain colons
  ("Microsoft Certified: Azure AI Engineer Associate") and logo filenames contain
  spaces ("DP_700 Logo.png") — split on `": http"` and take the URL greedily to
  end of string.
- `Summary` / `Experience` are HTML blobs. `Experience` has at least two shapes:
  a `<strong>` role heading, or a `Company:/Designation:/Duration:` block.
- Photo URLs contain spaces and must be percent-encoded before use.

There is still **no** API returning the signed-in user's own designation; the
resume Experience heading is the closest real signal.

## Certification vs accreditation

Two different things, both called "certification" in RMS, and not interchangeable:

- `vendorCertCount` (57) — accrediting **bodies** (MCT, CCSI, VCI, RHCI…). The
  right to teach a vendor's material. One `"True"`/`"False"` column per body.
- `trainerResume` (87) `Certifications` — the **exams** the person has passed.

Gap analysis needs the second. Course titles carry the exam code
(`PL-300T00: …` → PL-300) while certifications are stored under marketing names
(`Microsoft Certified: Power BI Data Analyst Associate`), so the two are joined
through the catalogue in `backend.py::_CERT_CATALOG`.

## Endpoints added in v6.0.0

- `GET /api/data/manager-profile?email=` — signed-in identity (3 RMS calls)
- `GET /api/data/team-capability?email=` — course catalogue + certification gaps
  (3 RMS calls **per trainer**; the Android client loads it lazily for this reason)
- `GET /api/data/trainer-skills?email=` — RMS skill register; the write read-back
- `manager_kpis` block on `unified-manager-intelligence`
- `session_time` on demand rows, parsed from the `TOTRecords` HTML

## Deployment

See `DEPLOYMENT.md` for deployment details; `docs/` holds architecture and status.


## Message rewrite — Teams/Viber house style (effective v3.48.0)
Inputs `[User Message: …]` and/or `[My Message: …]` (at least one) are rewritten into a short natural Teams/Viber message that sounds professional, direct and human. Hinglish (kal/parso/jaldi/thoda/bhejo/plz/sir etc.), informal phrasing, intent, authority, firmness, urgency, time sensitivity, assignment/delivery context and sender–receiver relationship are interpreted deterministically (no LLM required; the `POST /api/v2/message/rewrite` seam is preserved for a future model). Output is strictly house-style:

- greeting on its own line (`Hello _First_,` italicised in Teams, or `Hello team,`), body on the next line, closing on its own line with light emphasis (`_Please confirm once done._` etc. chosen by tone: urgent/corrective/appreciative/advisory/professional), total ≤1000 chars with sentence-boundary trimming
- simple professional English, complete sentences, full word forms only; no emojis, hyphens (except course codes like AZ-305 which are held aside), dashes, bullet points or decorative symbols
- italics only for names when clarity requires, bold only for the single key action/expectation/decision, underline only for time references (≤2), never combined unless unavoidable

Kotlin `ui/report/MessageRewriter.kt` mirrors Python `backend.py::_compose_rewritten`; the Android studio (`WeeklyReportScreen` team + per-reportee, `HrMonthlyReportScreen` per-reportee) first tries `POST /api/v2/message/rewrite` with `evidence_context` (cert gaps, learner rating, utilisation) as one supporting evidence sentence, then falls back to the local engine so offline works and the two sides never diverge.

## Weekly/monthly messages are evidence-only (effective v3.48.0)
`standpoint_note` (weekly) and `structured_feedback` (monthly `strength`/`area_of_improvement`/`other_feedback`/`trajectory`/`mock_summary`/`formatted_text`) state only what RMS proves: utilisation, learner rating/quotes (`_trainer_feedback_detail`, RMS key 244, filtered by email, quotes ≥45 chars with session/trainer/content signal word), named cert gaps (`_exam_policy`/213 + `_CERT_CATALOG`), HR/negative counts and Qubits. No generic behavioural boilerplate. A dimension with no evidence says so (`no feedback on record`, `no improvement areas are flagged from evidence this cycle`, `no standout strengths are on record`, `none. Steady, no flags this week`). Weekly `standpoint_note` is bullet/hyphen free to meet the house style. `GET /api/data/trainer-360` now ships `manager_evaluation` computed server-side from the same evidence set (`_generate_manager_evaluation`), so Trainer 360, weekly and monthly agree and the device no longer fabricates generic coaching prose (`pacing & articulation`, `hesitation`, `Goal → Steps → Verify` guidance).

## Analysis notes (2026-08-03)

- **Request flow:** login builds+caches intelligence → `/data/unified-manager-intelligence` serves cache with freshness metadata (`cache_status`, `cache_age_minutes`, `last_refresh_*`) + per-request lifecycle overlays; `POST /rms/<api>` is a credentialed server-side relay.
- **Unified payload:** ~40 keys — 6 canonical datasets plus `vendor_strength_df`, `growth_intelligence_df`, `certification_*`, `delivery_*`, `allocation_*`, `organization_*`, `executive_*`, `feedback_coaching_df`, `future_skill/certification_roadmap_df`, `data_health_df`, and `trainer_decision_objects` / `allocation_decision_objects` / `manager_action_objects` (contract v3, `entity_type:sha1[:12]` ids).
- **Real logic homes:** `services/trainer_fetch_service.py`, `services/decision_objects.py`, `services/custom_course_match_service.py` (+ `local_ml_service.py` TF-IDF), `shared/*_intelligence.py`. `intelligence_engines/` and `knowledge/` are unused stubs.
- **Persistence:** manager actions → `backend/data/action_state.json`; review flags → `backend/data/review_flag_state.json` (atomic writes); refresh state → `runtime/refresh/`; KB JSONL → `runtime/knowledge_base/`; per-manager cache → `runtime/cache/` (TTL 4h).
- **Known defect:** `app.py::_read_kb_jsonl` mis-parses multi-line JSONL — knowledge search/lookup endpoints return empty for populated KB files.

## AI/ML reality (2026-08-03)

- **No agentic AI / LLM** in the codebase. The "copilot" (`frontend/js/manager-copilot.js`) is a deterministic 15-question keyword answer engine, mounted only on trainer-intelligence.html; the UI labels itself "Deterministic, not live AI".
- **Only ML:** hand-rolled TF-IDF + cosine (`backend/services/local_ml_service.py`), used solely for custom-course matching. No model persistence, versions, eval, or metrics.
- **No learning loop:** manager lifecycle actions (close/escalate/reassign, acknowledge/resolve review flags) are persisted (`backend/data/*.json`) but never feed back into scoring or recommendations.
- **Two divergent scoring models:** backend `shared/scoring.py` (qubit/assignments/certs/util/feedback/tech weights) vs client-side `_intelScore` in `frontend/js/app.js` (Qubit 30/Certs 20/Feedback 20/75·15/HR 15). Pages re-deriving buckets from raw strings (e.g. capability-builder.html) can disagree with backend `classification`.
- **Known misalignment patterns in the unified payload:** `upcoming_assignment_count`/`current_assignment` are derived from multiple sources and can differ across frames; availability engine can assert `Busy`/conf 100 where utilization says underutilized; SPOF/bench/executive risk formulas are not calibrated by `trainer_count` (a 2-trainer team shows 27 SPOF courses).

## Agentic layer & learning loop (2026-08-03)

- **Deterministic agent (no LLM required):** `backend/agentic/` — `tools.py` (12 retrieval tools over the unified payload), `agent.py` (`classify_intent`, `answer()`, `build_briefing()`), `context.py` (`build_agent_context` reads payload + `runtime/knowledge_base/*.jsonl`), `learning.py`. `agent.answer` is the single seam where an LLM provider could be plugged in later.
- **Endpoints (session-cookie auth, cache-first non-blocking):** `GET /api/agent/briefing`, `GET /api/agent/learning`, `POST /api/agent/ask`, `POST /api/agent/feedback`, `POST /api/agent/tune`.
- **Learning loop is closed:** every manager lifecycle decision (`POST /api/actions/<id>/<close|escalate|reassign>`, `POST /api/review-flags/<id>/<acknowledge|resolve|escalate>`) is auto-recorded as a labeled example; `tune_weights` applies gradient updates (clamped 0.03–0.60, renormalized) and bumps a versioned model. Persistence: `runtime/learning/feedback.jsonl` + `runtime/learning/model_weights.json`.
- **Frontend:** `frontend/js/agent-copilot.js` mounts a server-backed chat + daily briefing on the dashboard (`frontend/pages/index.html`). The older `frontend/js/manager-copilot.js` remains the per-trainer client-side rule engine on trainer-intelligence.html.
- **Auth is non-blocking:** login issues the session cookie immediately; stale cache is served with `refresh_pending: true` and a guarded background rebuild (`_schedule_background_refresh` in `app.py`).
- **Scoring calibration:** SPOF/bench/executive risk is calibrated by `trainer_count` (teams ≤2 show "Thin Bench"/Medium instead of 27 SPOF alarms); availability confidence is capped ≤60 and contradictions surfaced when calendar and capacity signals disagree.

## Architecture & data flow map (2026-08-06)

### 3-tier system architecture

```
Browser (frontend/)
  ↓ (HTTP, no secrets)
Local Server (backend/app.py, port 8765)
  ↓ (Credentials in env vars only)
External RMS APIs (https://api.koenig-solutions.com)
```

### Login → Intelligence → Dashboard (complete flow)

1. User enters email on `/login.html`
2. `POST /auth/login` → `auth_service.authenticate_manager(email)` → validates in RMS
3. Session cookie issued immediately (non-blocking; stale cache served if available)
4. Browser calls `GET /data/unified-manager-intelligence?email=...`
5. Backend checks cache (4-hour TTL at `runtime/cache/skilledge_{email}.json`)
6. **Cache hit:** return immediately + maybe schedule background refresh
7. **Cache miss/stale:** call `intelligence.build_unified(email)` — 10-step pipeline:
   - Step 1: Validate manager scope
   - Step 2: Fetch reportees (direct + indirect) via RMS
   - Step 3–9: Parallel fetch per trainer (Details, Skills, Utilization, Certs, HR, Feedback, Assignments)
   - Step 4: Global course list (once per manager)
   - Step 5–10: Normalize, score (7 engines), build graphs (4 graphs), assemble payload (15+ datasets), cache
8. Response includes freshness metadata: `generated_at`, `cache_status`, `cache_age_minutes`, `refresh_pending`, `last_refresh_error`
9. Frontend renders dashboard: KPIs, team table, charts, calendar, action queue, copilot

### 9 verified RMS APIs (active in intelligence pipeline)

| API | Key | Credentials | Purpose |
|-----|-----|-------------|---------|
| Reportees | 1 | AISHWAR_GetReportees / 7zCheFM$Cc$t | Direct/indirect reports |
| Trainer Details | 75 | AISHWAR_GetTrainerDetai / 7zCheFM$Cc$t | Skills, courses, QubitsScore, certs |
| Trainer Skills | 5 | AISHWAR_TrainerSkill / 7zCheFM$Cc$t | Certified courses by employee ID |
| Utilization | 12 | AISHWAR_Utilization / 7zCheFM$Cc$t | % workload utilization |
| Vendor Certs | 11 | AISHWAR_VendorCert / 7zCheFM$Cc$t | Count of vendor certifications |
| Negative Feedback | 10 | AISHWAR_NegativeFeed / 7zCheFM$Cc$t | Feedback complaint count |
| HR Incidents | 9 | AISHWAR_HRIncident / 7zCheFM$Cc$t | Positive & negative events |
| Course List | 2 | AISHWAR_CourseList / 7zCheFM$Cc$t | Master course catalog (once per manager) |
| Assignments | 15 | AISHWAR_AssignmentAPI / 4PV6aCe6Sc8! | Past and upcoming deliveries |

Each API requires: (1) POST to `/api/Kites/Operator/GetToken` with username/password/role → get accessToken + deviceToken; (2) POST to `/api/Kites/Operator/common?apikey={key}&accessToken={token}&deviceToken={device_token}` with request body.

### 14 active frontend pages (data sources from unified intelligence)

- `index.html` — Dashboard; uses trainer_operations_df, trainer_current_state_df, batch_engagement_df, manager_action_objects, unallocated_demand_df, data_health_df, future_skill_roadmap_df
- `login.html` — Email entry; calls API.login then getUnifiedManagerIntelligence
- `team.html` — Trainer roster 360 view; uses trainer_operations_df
- `trainer-intelligence.html` — Individual trainer deep-dive; targeted API calls
- `trainer-detail.html` — Alt trainer view (similar to trainer-intelligence)
- `actions.html` — Manager action queue; uses manager_action_objects (close/escalate/reassign)
- `allocation-desk.html` — Match trainers to unallocated demand; uses custom_course_match_service
- `custom-course-match.html` — Ranked trainer suggestions for a course
- `capability-builder.html` — Skills roadmap, training recommendations; uses future_skill_roadmap_df
- `certifications.html` — Certification landscape, gaps; uses certification_summary
- `risk-takers.html` — High-risk trainers; uses trainer_classification
- `data-health.html` — Data quality issues, API errors; uses data_health_df
- `settings.html` — Theme/preference settings (localStorage)
- `coming-soon.html` — Placeholder for future features

### 20+ backend HTTP routes

**Auth:** `POST /auth/login`, `GET /auth/session`, `GET /auth/logout`

**Data & Intelligence:** `GET /data/unified-manager-intelligence?email=...`, `POST /api/refresh/run`, `GET /api/refresh/status`

**RMS Relay:** `POST /rms/{api_name}` (all 9 APIs proxied here; credentials handled server-side)

**Action Lifecycle:** `POST /api/actions/{id}/close|escalate|reassign`, `GET /api/actions`

**Review Flags:** `POST /api/review-flags/{id}/acknowledge|resolve|escalate`, `GET /api/review-flags`

**Agentic:** `GET /api/agent/briefing`, `GET /api/agent/learning`, `POST /api/agent/ask`, `POST /api/agent/feedback`, `POST /api/agent/tune`

**Diagnostics:** `GET /healthz` (liveness + session stats)

**Static:** `/`, `/login.html`, `/js/api.js`, `/js/agent-copilot.js`, `/assets/*`

### 7 intelligence engines (explainable scoring)

1. **Availability Engine** — Status (teaching_now, preparing, scheduled_today, free, blocked, unknown) + confidence; looks at current/next batch, roaming/IL dates
2. **Readiness Engine** — Bucket (Ready ≥75%, Prep ≥55%, Blocked <55%); factors: skills, certs, feedback, utilization
3. **Capability Engine** — Fit score (0–100) for trainer vs specific course; factors: skill match, certs, delivery experience, availability
4. **Trust Engine** — Risk level (Low, Medium, High); factors: negative feedback, HR incidents, consistency trend
5. **Recommendation Engine** — Suggests: next actions, skill gaps, career paths; returns ranked with priority
6. **Action Engine** — Identifies manager decisions needed (blockers, feedback cases, skill gaps)
7. **Custom Course Matching** — Ranks all trainers by fit score for unallocated demand

### 4 knowledge graphs (built per refresh)

1. **Trainer Graph** — Trainer → Skills → Certs → Courses → Domains
2. **Certification Graph** — Cert → Vendor → Courses → Trainers
3. **Course Graph** — Course → Modules → Topics → Prerequisites
4. **Technology Graph** — Tech → Products → Domains → Skills

### 15+ output datasets (in unified intelligence JSON)

- `trainer_operations_df` — all trainers + stats
- `trainer_current_state_df` — current status, current/next batch, confidence
- `batch_engagement_df` — current & upcoming training batches
- `manager_action_objects` — decisions manager needs to make
- `trainer_feedback_summary_df` — feedback sentiment, positive/negative, risk
- `unallocated_demand_df` — sales requests waiting allocation
- `trainer_decision_objects` — trainer-specific decisions (gaps, risks, recommendations)
- `future_skill_roadmap_df` — recommended training paths
- `data_health_df` — data quality issues, missing fields, API errors
- `certification_summary` — certs by vendor, gaps, coverage
- `delivery_intelligence` — delivery schedule, pipeline, capacity
- `allocation_intelligence` — demand matching, fit scores, blockers
- `organization_intelligence` — team structure, span of control, capacity
- `growth_intelligence` — skill progression, career paths, upgrades
- `vendor_strength_df`, `executive_intelligence`, `feedback_coaching_df`, `future_certification_roadmap_df`, etc.

### Caching & freshness strategy

- **Per-manager cache:** `runtime/cache/skilledge_{email_normalized}.json` (4-hour TTL)
- **Freshness metadata attached to every response:** `generated_at` (Unix timestamp when built), `served_at` (now), `cache_status` (live | cached | stale | refresh_failed), `cache_age_minutes`, `refresh_pending` (bool), `last_refresh_error` (string)
- **Stale cache handling:** serves old cache immediately + schedules non-blocking background refresh via `_schedule_background_refresh` (daemon thread, per-email guard)
- **Build failure fallback:** 3 retry attempts (wait 1s between) → if all fail and old cache exists: serve stale + record error; if no cache: raise error to user

### Security model

- **Secrets server-only:** RMS credentials stored as environment variables (`SKILLEDGE_RMS_<API>_USER` / `_PASS`), never in code or browser
- **Frontend proxy pattern:** browser calls `POST /rms/{api_name}`, backend adds auth tokens and calls external API, unwraps response, returns clean data
- **Session in-memory:** cookie-based, expires on logout (no persistent tokens in browser)
- **Auth non-blocking:** login issues session immediately; old cache served while rebuild runs in background

### Error handling & recovery

- **Intelligence build fails (3 retries):** Attempt 1 fails → wait 1s → Attempt 2 → wait 1s → Attempt 3 → if all fail: fall back to old cache (if exists) with `last_refresh_error` set; if no cache: error to user
- **Session expiration:** HTTP 401/403 → redirect to /login.html?expired=1 → clear sessionStorage
- **API unreachable:** fallback to stale cache with honest freshness metadata; frontend can judge data age

### Known gaps (2026-08-03, candidate follow-ups)

1. **Live smoke test:** `tests/smoke_test.py` needs real RMS creds + running server to verify end-to-end journey
2. **Duplicated scoring:** client-side `_intelScore` (app.js) should be removed; scoring is canonical backend-side (shared/scoring.py)
3. **Session persistence:** sessions lost on restart; could use database backing
4. **Dead scaffolding:** `intelligence_engines/` and `knowledge/` stubs (7 + 4 files) have zero imports; wire or delete
5. **KB parser bug:** `app.py::_read_kb_jsonl` mis-parses multi-line JSONL; knowledge search endpoints return empty

### `trainer_portal_api_details/` — full audit (2026-08-07, Android backend `backend.py`)

36 instruction files, verified against `backend.py`'s actual `_APIS` dict and call
sites (not taken at face value — see the file's own header: "verified against
live responses, not the instruction files, which have proven wrong more than
once"). Splits into four tiers:

**Tier 1 — Active (11):** `reportees`, `trainerDetails`, `utilization`,
`prevUpcoming`, `unallocated`, `negFeedbackCount`, `hrIncident`,
`trainerNegFeedback`, `trainerSkills`, `vendorCertCount`, `trainerResume`,
`addTrainerSkill`.

**Tier 2 — Wired but dormant (9):** credentials already registered in `_APIS`,
zero call sites:
- `trainerFeedback` (244) — per-question feedback text (question/answer/date),
  richer than the count-only `negFeedbackCount`
- `assignmentPax` (209) — participant roster per assignment
- `last3MonthsUtil` (39) — utilization in **long format** (one row per month:
  `EmployeeCode/EmployeeName/Utilization/MonthName`), vs. the wide-format
  `utilization` (55) that requires splitting `"Jun 2026": "75.77 / 43.05"`
  strings — worth a live A/B check as a cleaner trend-data source
- `trainerAvailability` (90), `upcomingAssignments` (93), `assignment` (15),
  `recordingDetails` (278), `activeSCDate` (13), `courseAvailability` (104)

**Tier 3 — Never wired (14):** no credentials in `backend.py` at all, course
catalogue/syllabus metadata mostly: `Course_List` (164, full catalogue with
vendor + course_url — distinct from `trainerDetails`'s per-trainer capability
rows), `Course_Module`, `Course_Syllabus_TOC`, `Course_Content_URL`,
`Course_and_Domain`, `Course_&_Technology_List`, `Course_Name`,
`Course_Schedule`, `Course_Whitout_Exam`, `Exam_Course_Linked_API`,
`Latest_Version_Of_Courses`, `Inhouse_and_FL_Trainers_Of_Courses`, `SCID` (173),
`Trainer_Free_Shedule_and_Details` (171).

**Tier 4 — Confirmed dead ends, do not retry:** `uniqueCerts` (72) returns zero
rows for every body shape tried (already in `backend.py`'s header comment); no
leave/absence endpoint exists anywhere in the catalogue.

**Data-quality flag:** `Check Course Availability in RMS.txt` (no underscore,
distinct from `Check_Course_Availability_in_RMS_Instructions.txt`) is
mislabeled — its actual body is the "Trainer RC Schedule" API (key 111), not
course availability. Another concrete instance of the instruction files being
wrong; do not trust a filename over its content.

### RMS "Auto Tall" allocation-engine rules — HR changelog audit (2026-08-07)

HR supplied the RMS AutoTall (real trainer-allocation engine) rule changelog,
08 Jul – 05 Aug 2026. Several rules were introduced then explicitly reversed
by later entries in the same changelog (Qubits and QI were added 20-22 Jul,
then both removed 27 Jul) — the *current effective ruleset* is what matters,
not every historical bullet. This app's own allocation-desk matching
(`backend.py::_rank_batch`) is a separate, simpler engine (course/vendor text
match only) that predates this changelog and didn't reflect any of it before
this audit. Three tiers:

**Mirrored in `_rank_batch`/`_cert_intelligence` (this app has the data):**
- Negative-feedback allocation block (16/20 Jul 2026): 3-day grace period,
  then not auto-allocated until 14 days after the feedback was marked.
  Candidates inside that window are flagged `blocked`/`blocked_until` and
  sorted below every available candidate rather than removed — mirrors "only
  affects auto-selected trainers," so a manager can still pick them manually.
- 6-month clean-record soft preference (05 Aug 2026, the *current* rule):
  among candidates tied on match score, one with no negative feedback in the
  trailing 6 months sorts first.
- Qubits score / QI category removed as tie-breakers (27 Jul 2026): the
  matching engine's old `-qubits_score` secondary sort key was deleted.
  Qubits is still returned for display, just no longer breaks ties.
- RedHat officially-approved ≈ Certified (22 Jul 2026, same precedent as
  CLC): `_cert_intelligence` no longer flags an approved-but-unexamined
  RedHat course as a certification gap.

**Confirmed already consistent, no change needed:**
- "4-day free" / weekly-busyness removed (27 Jul 2026) — `_rank_batch` never
  used utilization in its matching in the first place.

**Auto Tall 9-Update Allocation Intelligence Pipeline (Effective Aug 2026):**
- **Certified Mock Waiver (14 Aug 2026)**: Certified trainers are never blocked or penalized for missing mock records on 1st-time delivery.
- **Cancelled Batch Priority (12 Aug 2026)**: 14-day priority slot for trainers whose batch was cancelled by the client.
- **6-Month Clean Record (05 Aug 2026)**: Trailing 6 months with 0 negative feedback preferred on tie-breaks.
- **Additional Trainer Parity (30 Jul 2026)**: Lowest skill preference removed; neutral selection.
- **Tech Call Continuity (30 Jul 2026)**: Main trainer preference for pre-sales conversion calls.
- **4-Day Free Rule Removed (27 Jul 2026)**: Weekly busyness penalty eliminated.
- **Qubits/QI Neutrality (27 Jul 2026)**: Removed from candidate ranking and email tables.
- **Mock Step Transparency (27 Jul 2026)**: Auditable first-time delivery step.
- **OEM Header Display (27 Jul 2026)**: OEM vendor shown above Course Name.

### Availability: one source of truth (fixed 2026-09-15)

**The bug.** Recommended Trainers could show a candidate as `Availability
unknown` (a genuine "cannot determine" status) directly beside `Avail 100` (a
perfect availability score) — an internal contradiction, since unknown
availability can never legitimately be worth full marks.

**Root cause**, found by tracing both allocation engines end to end
(`backend.py::_rank_batch`, the demand-board / Recommended Trainers path, and
`backend.py::evaluate_candidate`, the gated `/api/v2/allocation/candidates`
path): the board computes `suitability_score` from `_availability_evidence`
(an early pass over the RMS assignment feed and off-dates — "no recorded
conflict" is promoted straight to `"available"` → 100) *before*
`enrich_demand_with_availability` computes the later, course-specific
`real_availability` verdict (from RMS key 171's free-schedule data) that the
UI's status chip actually reads. The two never reconciled, so the chip and
the number could disagree.

**What `real_availability` actually means — corrected, precise claim.** A
follow-up review challenged the word "authoritative" as originally used
here, and correctly so: `real_availability` is course-specific free-schedule
availability, not full operational availability. Traced what feeds it,
classified per item:

| Input | Status | Evidence |
|---|---|---|
| Course-specific free dates (RMS key 171) | **INCLUDED** | `availability_verdict`'s `free_dates` param, `backend.py:_free_schedule` |
| Assignment dates (RMS `prevUpcoming`) | **NOT INCLUDED** | `enrich_demand_with_availability` never reads `_availability_evidence`'s assignment feed when building `real_availability` — only key 171 |
| Time-of-day | **UNAVAILABLE FROM SOURCE DATA** | no hour field anywhere in `prevUpcoming`, `trainerDetails`, or key 171 |
| Leave / blocked dates | **DEFERRED CHECK** | `enrich_demand_with_availability` passes an *empty* `schedule` dict to `availability_verdict` (`backend.py`, the `{}` argument) — leave only enters the richer `_rc_schedule` signal used by the separate gated-candidates engine (`evaluate_candidate`), never the board. Recorded explicitly on the payload as `"leave_checked": False`. |
| Client exclusions (DNC) | **DEFERRED CHECK** | same reason; recorded as `"dnc_checked": False`. DNC is applied to the board separately, as a post-hoc `suitability = 0` override in `_rank_batch`, not through `real_availability`. |
| Travel constraints / existing trips | **NOT INCLUDED** | computed independently as `international_verdict`, not folded into `real_availability` at all |

**Corrected statement**: `real_availability` is the **authoritative
currently-known course-specific free-schedule verdict** — authoritative for
that one dimension, not a full operational-availability answer. Leave,
DNC and travel remain genuinely deferred to the per-batch gated-candidates
evaluation, which the board's own `"Client exclusions and leave are checked
when you open this batch"` notice (`AvailabilityIntelligence.kt::UncheckedNotice`)
already says honestly — that notice was correct before this fix and remains
correct after it; this section just makes explicit what "authoritative"
was and was not claiming.

**The fix.** `backend.py::reconcile_availability(candidate, verdict)` makes
`real_availability` the single source of truth, for the dimension it does
cover, for two fields: `candidate["availability_status"]` (what the chip
reads) and `suitability_components["availability"]` (what the "Avail N"
number reads), plus the weighted `suitability_score` total, all recomputed
from the same status through one shared table, `_AVAILABILITY_SCORE`:

| status | score | meaning |
|---|---|---|
| `available` | 100 | free on every requested day |
| `available_with_conflicts` | 60 | free, but with provisional (tentative) bookings |
| `partially_available` | 40 | free on some but not all requested days |
| `unverified` / `unknown` | 45 | **genuine insufficient data — never 100** |
| `conflict` / `unavailable` | 0 | not free |

**Why 45, not null/excluded/some other representation.** A follow-up review
asked whether 45 is an arbitrary number invented to keep the formula
numeric. It is not invented by this fix: `"unverified": 45` already existed
in `_suitability_components` before this session touched the file (git
history: commit `701c299`), and every sibling factor in that same formula
already uses the identical convention — a modest, non-perfect default when
evidence is missing, never a confident number (`utilization_score = 50` when
utilization is `None`; `location_score = 50` for an unverified location;
`certification_score = 70` when no certification requirement is mapped).
Introducing a different representation for availability alone (null,
excluded-from-formula, a separate confidence field with its own weight)
would be new formula policy this session has no business inventing.
Instead, `reconcile_availability` now also sets
`candidate["availability_verified"]` (`True` only for the five conclusive
statuses; `False` for `unknown`/`unverified`) — the exact same "score
carries a default, verified flag carries the truth" pattern
`location_verified`/`utilization_verified` already use two lines away in
the same function. This is the honest answer to "unknown must not falsely
increase recommendation confidence": confidence is not encoded in the
number at all, it is encoded in the flag, and two real, already-existing
consumers already gate on it —
`_match_trainers_for_demand` (`backend.py`, never reports AVAILABLE/COMMITTED
unless `verified`) and `_capacity_plan_from_allocation`'s
`verified_available_candidates`/`availability_unknown_candidates` buckets.
**Both were found, during this review, to have the same stale-flag bug as
the score**: `reconcile_availability` originally updated `availability_status`
but not `availability_verified`, so a candidate could still read
`verified: True` (stale from the early evidence pass) after the
authoritative verdict said `unknown`. Fixed in the same function, same
commit. Regression coverage: `tests/test_availability_reconciliation.py`
(15 tests, including two that exercise the real
`enrich_demand_with_availability` entry point end to end, not just the
`reconcile_availability` helper).

This is the durable invariant: **`unknown`/`unverified` availability status
can never resolve to a perfect availability score, and can never be
reported as a verified-available candidate**, by construction — every call
site that produces a score or a verified flag reads the same two tables, so
a new status can't be added to one without both being defined.

**What this fix does not change**: Android renders these two fields as-is
(`AvailabilityIntelligence.kt::AvailabilityChip` for the status text,
`AllocationDeskScreen.kt`'s `suitability_components` line for the number) —
it was never computing either value itself, so no Android change was
needed; they simply agree now because the backend does. Verified by reading
every render site (`AllocationDeskScreen.kt`, `AvailabilityIntelligence.kt`)
and confirming no other screen (`GatedCandidates.kt`, `EligibilitySheet.kt`,
`Trainer360*.kt`) references `real_availability`/`availability_status`/
`suitability_components` at all — they consume the structurally different,
already-gated `evaluate_candidate` engine's `fit`/`eligible` fields, which
never had this contradiction to begin with.

**Known, explicitly deferred, gap** (found during this trace, not fixed
here — do not assume otherwise):
- **Date-level, not datetime-level, overlap.** Both `_availability_evidence`
  and `availability_verdict` compare whole calendar days
  (`backend.py:1410`'s `st <= end and en >= start`, and a `set[date]`
  intersection). Re-searched on review, beyond the original grep, across
  batch detail payloads, schedule endpoints, trainer schedule APIs, local
  cached schedule data, and RMS responses generally: no hour-of-day field
  exists anywhere in the RMS integration this repo calls (`prevUpcoming`,
  `trainerDetails`, key 171, key 111/`trainerRCSchedule`). A same-day,
  non-overlapping-hours batch (e.g. 09:30 on a day where the trainer's only
  other commitment ends at 08:00) is therefore still treated as a full-day
  conflict. Implementing true datetime overlap would require fabricating a
  time field RMS does not expose; per the "do not invent missing data"
  rule, this is recorded as a **missing upstream data requirement**, not
  closed, and not silently dropped.

**Corrected: skill-family matching — a real taxonomy exists and is now
used.** The original claim in this section, "there is no course→skill
taxonomy in this codebase," was **wrong**, caught by a follow-up review
that specifically asked not to accept that conclusion without re-checking
this codebase's own capability/skill-profile functionality. What actually
exists:
- `domain/capability/models.py` + `repositories/capability_store.py` +
  `services/capability/capability_service.py`: a full curated capability
  graph schema (courses, capabilities, families, aliases, relationships),
  seeded by `scripts/seed_capability_foundation.py` — but every row lands
  `DRAFT`/pending human review, and the live SQLite database
  (`skilledge_capability.sqlite3`) has zero rows in this checkout. Real
  schema, not yet populated data — **not usable today**.
- `backend.py::_course_taxonomy()` (RMS key 114 `courseTechnology` + key
  205 `courseDomain`, 6h cache): a working, **ID-joined**
  course→technology→domain map — `{"id:<course_id>" | normalised name ->
  {"technology", "domain"}}` — already consumed by `_capability_portfolio`,
  `_skill_profile_from_capability`, and cert-intelligence. This is real,
  live, and usable **today**.
- `_match_score` (allocation-time skill matching) consumed neither of the
  above — confirmed still true. That specific gap is now closed: `_match_score`
  takes an optional `taxonomy` param (a `_course_taxonomy()` map) and
  `_rank_batch` now builds and passes one. Text/code matching (exact title
  100, shared vendor course code 92, Jaccard token overlap) is tried first
  and always wins when it finds anything; only when text finds a genuine
  zero (different title, different code, zero shared tokens) does the
  taxonomy fallback apply — if both courses resolve to the same RMS
  `technology`, that scores 60 (landing in "Available with Upskilling", not
  "Best Match" — a real but weaker signal than a textual match), rescuing
  the "PL-300 must map to its Power BI capability, not literal course-title
  equality" case using real, already-live RMS data rather than a guessed
  taxonomy. Trainer capability rows (`_skills`, from RMS `trainerDetails`)
  never carry a `course_id`, only `CourseName` — confirmed by reading the
  parser — so the trainer side of this join can only use the existing
  normalised-name lookup (`_norm_course`, the same join every other
  taxonomy consumer in this file already uses), not an ID join; the batch
  side does carry a real `course_id` (RMS `CourseId`, confirmed at the
  unallocated-batch builder) and uses it. Omitting `taxonomy` (the default
  for every pre-existing caller) preserves the exact prior text-only
  behaviour — this is additive, not a rewrite. Regression coverage:
  `tests/test_skill_taxonomy_matching.py` (8 tests, including two through
  the real `_rank_batch` entry point).
- The curated capability-graph schema (courses/capabilities/families with
  human-reviewed relationships) remains a genuinely open, larger
  initiative — it is real, unfinished infrastructure (empty database,
  DRAFT rows), not a fabricated one, and populating it is a data/product
  investment distinct from this bug fix. Not claimed complete.

### Auto Tall policy updates — Aug/Sep 2026 (implemented 2026-09-15)

Four further HR-supplied Auto Tall rules, added to `evaluate_candidate`
(the gated `/api/v2/allocation/candidates` engine, which already has the
hard-eligibility-vs-soft-preference split these rules need — the
Recommended Trainers board (`_rank_batch`) has no such split and was not
extended with these, consistent with not duplicating a policy engine twice):

- **Omnissa officially-approved ≈ Certified (07 Sep 2026)**: for Omnissa
  assignments only, a candidate's RMS `OfficiallyApproved` flag satisfies
  the certification requirement the same way `RedHat` approval already does
  in `_cert_intelligence` (same precedent, explicitly scoped per-vendor —
  never generalised to "Approved == Certified" for every technology).
- **Multi-assignment trip-history preference (07 Sep 2026)**: for ILT/FMAT,
  a trainer with a proven history of taking a second compatible assignment
  while already travelling on the same trip earns a ranking preference.
  Preference only — never a gate; a trainer without this history remains
  fully eligible.
- **International vaccination preference (24 Aug 2026)**: for international
  ILT/FMAT, Yellow Fever + Polio coverage on record earns a ranking
  preference. Preference only — missing vaccination data does not affect
  eligibility, per the explicit HR instruction that this is not yet a hard
  allocation blocker.
- **2-hour / alternate 4-hour batches participate in allocation (27 Aug
  2026)**: confirmed by trace that no duration-based exclusion exists
  anywhere in either allocation engine (`_rank_batch` or
  `evaluate_candidate`) — this was already correct, and is now locked in by
  regression tests (`test_two_hour_batch_participates_in_allocation`,
  `test_alternate_four_hour_batch_participates_in_allocation` in
  `tests/test_auto_tall_policy_sept2026.py`) rather than left unverified.

All four are read from plain fields on the candidate/batch dicts
(`approved`, `multi_assignment_trip_history`, `vaccinations`,
`duration_hours`) — consistent with how the existing Aug-2026 rules
(`cancelled_batch_priority`, `is_tech_call_trainer`, `clean_record_6mo`)
are wired: this file does not fabricate the RMS plumbing that populates
them, only the policy logic that consumes them once populated. Tests:
`tests/test_auto_tall_policy_sept2026.py`.

**Pipeline position, proven not assumed.** A follow-up review asked for
proof that each policy sits where it claims — preference vs. gate, not just
"the constant exists." `evaluate_candidate` (`backend.py`) computes and
returns every hard-gate `blockers` list *before* the `# ── Weighted fit`
section that computes `factors` even begins (structural evidence: the
function returns `eligible: False, fit: 0, factors: []` immediately if
`blockers` is non-empty, at two separate points — after the first gate
batch and after the mock gate — both strictly before any of the four new
rules' code, which lives entirely inside the weighted-fit section).
Behavioural proof, per rule:
- `test_trip_history_is_a_preference_not_a_requirement`: a candidate with
  no trip history is still `eligible: True`; history only ever raises
  `fit`, never appears in `blockers`.
- `test_no_vaccination_information_does_not_block_eligibility`: no
  `vaccinations` key at all still returns `eligible: True`.
- `test_omnissa_...` (3 tests): the certification credit only ever appears
  in `factors` (post-eligibility), is Omnissa-vendor-scoped (a non-Omnissa
  batch with the same `approved=True` candidate gets no credit), and
  requires both the vendor match and the flag.
- `test_two_hour_batch_participates_in_allocation` /
  `test_alternate_four_hour_batch_participates_in_allocation`: `eligible:
  True` for both durations — proving no gate rejects on duration, not just
  that no code mentions duration.

### Hard eligibility vs. suitability — skill (item re-verified 2026-09-15)

A follow-up review raised the screenshot pairing "Skill 11 → suitability 59"
vs. "Skill 1 → suitability 58" and asked whether a missing mandatory skill
could be masked by unrelated factors (Avail/Ready/Lang/Cert) into an
apparently-recommendable trainer, on the Recommended Trainers board
(`_rank_batch`, not the already-gated `evaluate_candidate`).

**Re-read `_rank_batch`'s matching loop directly**: a trainer only enters
`matched`/`candidates` at all `if best > 0` (`backend.py`, inside the
per-team-member loop, before any of readiness/availability/language/
certification is even fetched) — `best` is `_match_score`'s output. A
trainer with **zero** capability-course overlap with the batch's course
(now including the taxonomy fallback above) never appears in the
Recommended Trainers list, regardless of how strong their Availability,
Language, Certification or Readiness would otherwise be. This is a real,
structural gate, not a compensable weight — confirmed unchanged by this
session's edits (the gate predates this session; the taxonomy fallback
only widens what counts as `best > 0`, it does not weaken the gate itself).

Once past that gate, `match`/`skill` legitimately participates in the
weighted `suitability_score` at 0.35 — the single highest weight in
`_SUITABILITY_WEIGHTS`, already dominant over every other factor by
design, and the screenshot pairing (11→59, 1→58) is consistent with that:
the higher-skill candidate outranks the lower-skill one, as intended. Low
but nonzero skill matches are not hidden either: the board's own coverage
bucketing (`"No Coverage"` below 50, `"Available with Upskilling"` 50-89)
and the Android UI's `"Upskilling required"` label (shown whenever
`match < 75`) already surface a weak match honestly rather than
disguising it as a strong recommendation. No code change was needed here —
this was re-verification of an existing, correct invariant, not a fix.

### Repository ownership review (Android, item re-verified 2026-09-15)

A follow-up review challenged Phase 3 increment 6's placement of
`getAllocationCandidates` inside `TrainerRepository`, and asked whether
that repository was starting to absorb every endpoint whose payload merely
*contains* trainer data (the god-repository risk `ManagerRepository` was
being broken up to avoid).

**Re-examined against the precedent this migration already set**:
`EligibilitySheet`'s `getBatchEligibility` (`api/v2/eligibility/batch`) was
deliberately given its own `EligibilityRepository` in increment 3, reasoned
as "a cross-cutting, backend-authoritative evaluation... kept as its own
domain rather than folded into Batch or Trainer." `getAllocationCandidates`
(`api/v2/allocation/candidates`, backend `evaluate_candidate`/
`_evaluate_team_against_batch`) is structurally the same kind of endpoint —
a gated recommendation combining trainer capability, availability,
certification and travel for one specific batch, not a fact the trainer
owns. Placing it in `TrainerRepository` was inconsistent with the
repository's own precedent, caught by this review.

**Corrected**: moved to a new `core/data/AllocationRepository.kt`, holding
only `candidates(...)`. `TrainerRepository` keeps `alternativeTrainers`
(a genuine trainer lookup) and `bulkAssignSkill`/`endorseSkill` (both
trainer-skill-record writes — the trainer's own data, mutated), reviewed
against the same test: does the *operation* belong to the trainer, not
whether the *response* mentions one. `demandContext` stays in
`BatchRepository` — Batch/demand-owned, uncontested by this review.
`AllocationRepository` deliberately holds nothing else: not a general
dumping ground, just the one cross-cutting recommendation read, matching
`EligibilityRepository`'s scope. `AllocationViewModel` now takes four
repository params (`ManagerRepository`, `TrainerRepository`,
`BatchRepository`, `AllocationRepository`); `AllocationViewModelTest.kt`
updated to match. Verified by CI (compile/assemble green, no new test/lint
regressions) before being accepted.

## Koenig HR Trainer Index Policy (TI – 13/08/26)

Official Koenig HR scoring formula implemented in `_calculate_trainer_index` across 20 weighted pillars:
1. **Utilization**: Max 15% non-SC; 10 pts per 1% >60%, -10 pts per 1% <60%, +50 pts bonus if >60% in all 4 quarters, -25 pts per quarter <60% (Cap: 550 pts).
2. **Beast AI Delivery**: 10 pts per Beast AI delivery, 20 pts per SaaS delivery (Cap: 200 pts); >=10 SaaS deliveries earns *Forward Deployed Engineer (FDE)* designation.
3. **Quality Index (QI)**: 2.5 pts per 1.0 QI point (Cap: 300 pts).
4. **Knowledge Sharing**: 5 pts per TBT & Mock, 10 pts per Internal Training (Cap: 100 pts).
5. **1st Time Course / Cert**: 20 pts per first-time delivery or certified delivery (Cap: 200 pts).
6. **Auto-Resume Certifications (AI Difficulty)**: Easy = 1 pt, Moderate = 3 pts, Hard = 5 pts (Cap: 200 pts).
7. **Roaming Hours L12M**: 0.75 pts per hour (Cap: 100 pts).
8. **Night ILO Hours L12M**: 0.25 pts per hour (9:01 PM – 6:59 AM) (Cap: 100 pts).
9. **HR Incidents & Audits**: +10 pts positive recognition, -20 pts negative incident.
10. **Instructor Certifications**: 100 pts premier (AAI/CCSI/VCI/RHCI), 20 pts other (MCT/CTT+) (Cap: 200 pts).
11. **Trainer Developed**: 50 pts per trainer developed (Cap: 500 pts).
12. **Customer Orientation**: Sales rating score * 16 (Cap: 400 pts).
13. **Solution Selling**: 50 pts per solution designed (Cap: 100 pts).
14. **Skill Takeover**: 10 pts per resigned trainer skill taken over prior to LWD (Cap: 100 pts).
15. **-ve Feedback**: -100 pts deduction per negative assignment.
16. **Centre Improvements**: +10 pts per center issue reported.
17. **Tech Call Conversion**: 20 pts per call converted.
18. **Tenure with Koenig**: 0.2 pts per completed month (Cap: 50 pts).
19. **Prior Experience**: 0.1 pts per month prior to Koenig (Cap: 50 pts).
20. **Overseas Visa Commitment**: 100 pts if commitment valid >= 3 months.

### Standpoint & Standing Tiers:
- **Tier 1: Diamond (TI ≥ 1200)**: 👑 Elite Global Deployable Lead
- **Tier 2: Platinum (TI 900–1199)**: ⭐ Strong Performer / Multi-Domain Lead
- **Tier 3: Gold (TI 600–899)**: 🔷 Core Delivery / Steady Anchor
- **Tier 4: Silver (TI 300–599)**: 🔶 Developing / Upskilling Focus
- **Tier 5: Bronze (TI < 300)**: ⚠️ At Risk / Quality & Util Recovery

## Opportunity Guardian module (effective 2026-09-05)

SkillEdge now includes an **Opportunity Guardian** module that watches for training/delivery opportunities and evaluates them against the manager's skill profile. It is not a separate application — it is a major layer inside SkillEdge.

### Architecture

```
                 SKILLEDGE ECOSYSTEM

              ┌────────────────────┐
              │   SkillEdge Web    │
              │   React / Vite     │
              └─────────┬──────────┘
                        │
┌────────────────┐      │       ┌────────────────────┐
│ SkillEdge      │──────┼───────│ Node / Express API │
│ Android        │      │       └─────────┬──────────┘
│ Kotlin/Compose │      │                 │
└───────┬────────┘      │            ┌────┴────┐
        │               │            │ MongoDB │
        │               │            └────┬────┘
 Android APIs           │                 │
        │               │            ┌────┴──────┐
 Notification Listener   │            │ AI Engine │
 Biometrics              │            │ RAG       │
 Background Work         │            │ Matching  │
 Local Database          │            │ Documents │
 Push Notifications      │            └───────────┘
```

The **Android client** is native Kotlin + Jetpack Compose because notification listening (Viber, other messaging apps) is an Android platform capability and belongs on-device. The **web frontend** is React/Vite + Node/Express + MongoDB.

### Module structure

```
SKILLEDGE
│
├── My Skill Profile
│   ├── Certifications
│   ├── Technologies
│   ├── Courses delivered
│   ├── Experience
│   ├── Labs/projects
│   └── Confidence by topic
│
├── Opportunities
│   ├── Manual opportunities
│   ├── Detected opportunities        ← Opportunity Guardian
│   ├── Accepted
│   ├── Declined
│   └── Missed
│
├── Opportunity Guardian              ← NEW
│   ├── Trusted Sources
│   ├── Notification Listener
│   ├── Message Classification
│   ├── Document Analysis
│   ├── Skill Matching
│   ├── Opportunity Score
│   └── Escalation Rules
│
├── Learning
│   └── Skill gaps discovered from opportunities
│
└── AI Copilot
    └── "Can I deliver this?"
```

### Capability Graph

The SkillEdge profile is no longer a flat list of skills (`"SQL, Azure, Power BI"`). It is a **Capability Graph**:

```text
                 Aishwar

                    │
       ┌────────────┼────────────┐
       │            │            │
   Certified     Delivered     Built
       │            │            │
    AI-102      AI courses     RAG labs
    DP-600      DP courses     Chatbots
    DP-700      SQL Admin      APIs
    DP-750      Fabric         MLOps
       │            │            │
       └────────────┼────────────┘
                    │
                 SKILLS
                    │
       ┌────────────┼────────────┐
       │            │            │
   Strong      Moderate       Gap
```

Every new opportunity makes the graph smarter: opportunity → accept → prepare → deliver → evidence → score increases → better recommendations.

### Decision model

SkillEdge uses an opportunity-friendly decision model — it does not unnecessarily lose opportunities:

| Score | Verdict | Action |
|-------|---------|--------|
| 90–100% | STRONGLY ACCEPT | Accept immediately |
| 75–89% | ACCEPT | Accept, preparation required |
| 60–74% | CONDITIONAL ACCEPT | Check preparation time |
| 40–59% | HIGH RISK | Manual review |
| 0–39% | DECLINE | Do not pursue |

### Viber notification listener flow

```text
Viber Notification
        ↓
SkillEdge Android Listener
        ↓
Package = Viber?
        ↓
Group = Trailblazers?
        ↓
Sender = Gaurav Joshi?
        ↓
Relevant delivery request?
        ↓
YES
        ↓
Create SkillEdge Opportunity
        ↓
Analyse request/document
        ↓
Compare against SkillEdge Skill Profile
        ↓
Generate recommendation
        ↓
Critical alert
```

**Settings → Opportunity Guardian** configuration:
- Trusted Source (App, Group, Sender)
- Trigger keywords (Can anyone deliver, Can you deliver, Availability, Training requirement, Trainer needed, Travel opportunity, International delivery, Course / TOC attached)
- Quiet Hours (11:00 PM – 7:00 AM) with escalation overrides:
  - Normal messages → Ignore during quiet hours
  - High opportunities → Persistent alert
  - Critical opportunities → Alarm-style escalation

### Backend API patterns

All new endpoints follow the existing V2 canonical route pattern (`/api/v2/opportunity/*`, `/api/v2/skill-profile/*`). They run through `_v2_manager_session(manager_only=True)` for write operations. The existing `backend.py` is the deployment target on Render (`https://skilledge-backend-fpcl.onrender.com`).

### Existing Android infrastructure for Opportunity Guardian

- `ViberAutomationEngine.kt` — background automation pass that processes Viber outbox items; can be extended for opportunity detection
- `ViberConfigStore.kt` — persists Viber configuration per manager
- `ViberOutboxStore.kt` — persists outbox items
- `ViberAutomationScreen.kt` / `ViberAutomationViewModel.kt` — existing settings screen
- `NotificationCenter.kt` / `NotificationDestinationStore` — handles notification routing
- `MonitoringService` / `MonitoringPass` — foreground service for background processing
- `SyncScheduler` — background sync scheduling
- `ManagerRepository` — data repository pattern

These provide the foundation. The Opportunity Guardian adds a new `ui/opportunity/` package alongside the existing `ui/report/`, `ui/main/`, `ui/batch/`, `ui/trainer/` packages.
