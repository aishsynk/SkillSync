# SkillEdge / Manager OS — Decisions

## 2026-09-17 — Design V3 Phase 1: canonical component family + foundation motion/chart upgrade

**Decision: `theme/DesignSystem.kt` + `theme/Surfaces.kt` + `core/ui/*` are the
canonical VISUAL/PRIMITIVE foundation** (glass surfaces, radius/spacing
ladder, `Severity`/`ToneChip` semantics, chart primitives). This was already
true in practice — both families already build their surfaces from
`Modifier.glassSurface()`/`accentGlass()` in `Surfaces.kt`, so this is a
formalization, not a rewrite.

**Usage/migration matrix** (real grep counts, external call sites only —
excludes the defining files themselves):

| Concept | Family A | Family B | Call sites A | Call sites B | Capability diff | Verdict |
|---|---|---|---|---|---|---|
| Card | `SkillCard` (`DesignSystem.kt`) | `SkillSyncCard` (`SkillSyncComponents.kt`) | 11 files / 28 | 9 files / 33 | B adds `onClick`+`pressable`; both already build on `glassSurface()`/`accentGlass()` — same visual, not two languages | **MIGRATE B → thin wrapper over A** (done this pass — see below) |
| Section header | `SectionHeading` | `SkillSyncSection` | 8 files / 13 | 1 file / 11 | B bundles a `Column` content slot **and a composable `trailing` slot** (a button/badge, not just text) — `SectionHeading`'s `trailing` is a bare string. Not a like-for-like wrapper | **KEEP BOTH — B is not a duplicate**, it's a higher-capability variant. Forcing it onto `SectionHeading` would silently drop the composable-trailing capability at its 11 call sites; deferred rather than risked this pass |
| Metric | `Figure`, `MetricSparkline`, `MetricProgress`, `HeroRing` (core/ui `Editorial.kt` + `DesignSystem.kt`) | `SkillSyncMetric` | 8+ files | 4 files / 13 | No capability gap found; different named use-cases (headline figure vs. inline metric row) | **UNIQUE — KEEP both**, no forced merge |
| Chip/status | `ToneChip`, `Severity` | `SkillSyncStatusChip`, `SkillSyncChip` | 19 files / 91 | 1 file / 7 + 1 file / 4 | `ToneChip` is the dominant, established primitive by a wide margin | **DEPRECATE B on next touch** (not migrated this pass — no call sites outside `SkillSyncComponents.kt` itself blocked anything; left alone per "don't mass-migrate") |
| Button | *(none in Family A)* | `SkillSyncPrimaryButton`, `SkillSyncSecondaryButton` | 0 | 1 file / 4 + 1 file / 3 | No competing implementation exists | **UNIQUE — KEEP**, adopt as canonical buttons going forward |
| Search | *(none in Family A)* | `SkillSyncSearchBar` | 0 | 2 files / 3 | No competitor | **UNIQUE — KEEP** |
| Empty state | `StateNote` (plain message only) | `SkillSyncEmptyState` (icon+title+description+action, built on `glassSurface()`) | small | 7 files / 9 | B is strictly more capable and already uses the canonical surface primitive | **KEEP B AS HIGH-LEVEL WRAPPER — canonical for empty states.** `StateNote` reserved for one-line inline notes only |
| Error state | *(none dedicated)* | `SkillSyncErrorState` | 0 | 2 files / 3 | No competitor | **UNIQUE — KEEP** |
| Loading | `ShimmerBox` (`core/ui/Motion.kt`) | `SkillSyncLoadingState` | 26 sites (dominant) | 3 files / 4 | Different roles: `ShimmerBox` is a per-field skeleton primitive, `SkillSyncLoadingState` is a whole-screen wrapper around it | **KEEP BOTH** — `SkillSyncLoadingState` is a high-level wrapper, not a duplicate |
| Segmented control | *(none)* | `SegmentedSelector` | 0 | 2 files / 3 | `TeamCalendarScreen`'s Month/Week/Day tab row was a **third, hand-rolled implementation** — a real duplicate, not counted in either family | **CONSOLIDATE: `TeamCalendarScreen` migrated onto `SegmentedSelector` this pass** (see §13 motion patch below) |
| List item | *(none)* | `SkillSyncListItem` | 0 | 2 files / 4 | No competitor | **UNIQUE — KEEP** |
| Page/screen container | `SkillSyncScreen`, `SkillSyncTopBar`, `SkillSyncPageHeader` (despite the "SkillSync" prefix, these live in `SkillSyncComponents.kt` with no Family A competitor) | — | 0 | `SkillSyncTopBar`: 12 files / 13 (heavily adopted); `SkillSyncScreen`/`SkillSyncPageHeader`: 1–2 sites | No competing implementation; `SkillSyncTopBar` is already the de facto canonical secondary-screen header | **UNIQUE — KEEP**, `SkillSyncTopBar` is canonical for any screen that isn't the root shell (which uses `ExecutiveHeader` instead — that split is intentional: root tab shell vs. pushed detail screen) |

**What actually changed this pass** (trivial, low-risk only — no mass
migration of the other 40+ screens):
- `SkillCard` gained an optional `onClick` param (reuses `Modifier.pressable`),
  and its surface-building logic was extracted into a shared
  `Modifier.cardSurface()`. `SkillSyncCard` now calls that same helper instead
  of duplicating the severity/glass/press branching — same signature, same
  `Space.sm` internal spacing it already had, so none of its 9 call sites
  needed to change; only the risk of the two surfaces drifting apart is gone.
- `SkillSyncSection` was inspected and left unchanged — see the matrix row
  above for why forcing it onto `SectionHeading` would have dropped real
  capability rather than removed real duplication.
- `TeamCalendarScreen`'s hand-rolled Month/Week/Day tab `Row` was replaced
  with the canonical `SegmentedSelector`, with an `AnimatedContent` transition
  added on top (this also fulfills the Delivery Operations motion patch).

**Permanent rule:** *New UI must not introduce a third component family.*
Use `theme/DesignSystem.kt`/`Surfaces.kt`/`core/ui/*` primitives directly, or
a `SkillSync*`-prefixed high-level wrapper from `SkillSyncComponents.kt` if
one already covers the need (buttons, search bar, empty/error/loading
wrappers, list item, segmented selector). If neither covers a new need, add
to the existing family that already owns the closest primitive — do not
start a new naming convention or a new surface-drawing approach.

### Motion system — springs formalized, not replaced

`theme/SkillMotion.kt`'s three-spring vocabulary (`snappy`/`gentle`/`flow`/
`press`) is unchanged in value, only more explicitly documented for intended
use:
- **snappy** — chips, toggles, segmented-control selection, small
  discrete-state changes.
- **gentle** — card/content entrance, section reveals, status-color changes.
- **flow** — larger layout/container transforms (e.g. the collapsing Today
  brief) — meant to track a continuous gesture, not perform a fixed beat.
- **press** — touch-down feedback only (`Modifier.pressable`).

### Reduced motion — centralized, not scattered

Added `core/ui/ReducedMotion.kt`: a `LocalReducedMotion` `CompositionLocal`
resolved once from `Settings.Global.ANIMATOR_DURATION_SCALE` (system
"Remove animations" / animation-scale-0 setting) and provided at the theme
root. `animateProgressFromZero`, `AnimatedCount`, and `Appear` in
`core/ui/Motion.kt` all consult it and resolve immediately to final state
when true, instead of each screen re-implementing its own accessibility
check.

### Chart interaction — one shared tooltip, added to the two suitable charts

Added `ChartTooltip` (one composable, canonical dark-surface + semantic dot +
label/value styling) plus `pointerInput`-based tap/drag nearest-point
resolution to `TrendChart` and `BarChart` only (`core/ui/Charts.kt`) — the
two primitives explicitly identified as chart-scale rather than decorative.
`Sparkline`, `MeterRow`, and the small progress/radial primitives were left
non-interactive on purpose (too small on a phone for a precise tap target).
No chart library was added — this is native `Canvas`/`pointerInput`, per the
"do not add MPAndroidChart/Vico" rule already documented in `Charts.kt`'s own
file header.

### Open navigation-architecture question — explicitly deferred, no implementation

Should `Opportunities`/`Search` remain primary bottom-nav destinations, or
move into a future `More`/global-search surface? **Not decided.** No `More`
screen exists yet and none was built this pass. Requires a separate
operator-reviewed IA decision before any bottom-nav change.

---

## 2026-09-15 — Versioning rule replaced: build number (4th component) increments, not the patch digit

- **Decision (operator instruction, supersedes the `2026-09-15` "Versioning
  correction" entry below on this one point — do not relitigate):** SkillSync
  releases are identified by four components, `MAJOR.MINOR.PATCH.BUILD` (e.g.
  `3.80.13.188` = `versionName "3.80.13"` + `versionCode 188`). For every
  normal release, only the **rightmost** component (`versionCode`, the
  4th/BUILD number) increments — `versionName` (`3.80.13`) stays **fixed**.
  Example: `3.80.13.188` → `3.80.13.189` → `3.80.13.190` → … → `3.80.13.999`.
  Only when `versionCode`'s portion reaches `999` does the next-higher
  component roll over (`3.80.13.999` → `3.80.14.000` → `3.80.14.001`, …),
  same right-to-left principle repeating.
- **This replaces, for this one axis, the earlier "patch digit increments
  every release" rule** (the `versionName`'s third number used to move with
  every release, e.g. `3.80.12` → `3.80.13` → `3.80.14`; it no longer does).
  The earlier entry's other point — never bump `MINOR` without explicit
  instruction, no accidental jump to `3.81.0` — still stands unchanged.
- **Correction applied this session:** commit had incorrectly set
  `versionCode 189` / `versionName "3.80.14"` for the next test candidate
  (still applying the old patch-digit-every-release rule). Corrected in
  place to `versionCode 189` / `versionName "3.80.13"` (unchanged from the
  last published `v3.80.13.188`) before any further release action.
- **Always check `https://github.com/aishsynk/SkillSync/releases` before
  calculating any version** — it is the sole source of truth, never
  `build.gradle.kts`, `AI/PROGRESS.md`, git tags alone, or memory.

## 2026-09-15 — Canonical release destination: always `https://github.com/aishsynk/SkillSync/releases`

- **Decision (operator instruction, permanent, applies to every future
  session):** all SkillSync Android releases — tags, GitHub Releases, and
  release APK/AAB assets — are published to
  **`https://github.com/aishsynk/SkillSync/releases`**, and only there.
  This is already what `.github/workflows/android-release.yml` does
  (`softprops/action-gh-release` targeting this repo on push to `main`); no
  workflow change was needed, only recording the instruction so it is never
  second-guessed or redirected in a future session.
- **Supersedes:** the `2026-09-14` "external release repo is
  `aishsynk/InTouch`" entry below, which the `2026-09-15` contamination-
  recovery entry immediately under this one had already reversed
  (`aishsynk/InTouch` is explicitly **not** a release repository). This
  entry is the final word: do not revisit `aishsynk/InTouch`, or any other
  repository, as a release destination without new, explicit operator
  authorization.
- **Practical rule for every session:** when asked to "release", "cut a
  release", "where is my release", etc., the answer is always a tag +
  GitHub Release on `aishsynk/SkillSync`, produced by pushing the version
  bump to `main` (triggering `android-release.yml`) or by an explicit,
  operator-authorized `workflow_dispatch` of that workflow — never a
  manually-assembled/uploaded APK link, never a different repository.

## 2026-09-15 — Production-contamination recovery: InTouch Intelligence rebrand and LinkedIn Capture removed; SkillSync is the product

- **Decision (operator correction, supersedes the `2026-09-14` "Product is
  branded InTouch Intelligence" entry below — do not relitigate this):** the
  Android application is **SkillSync**. "InTouch Intelligence" was never an
  authorized product identity and must not exist anywhere in this repository
  or release line. The existing internal/visual **SkillEdge** identity
  (login wordmark "SkillEdge", tagline "DELIVERY INTELLIGENCE", dashboard
  header "SKILLEDGE · EXECUTIVE CONSOLE") is intentional and correct — it is
  not being replaced by anything.
- **Decision:** the LinkedIn Capture feature (`feat: add linkedin capture
  workflow to android`, origin commit `ec701f70`) is not part of SkillSync
  and has been removed in full: API client, parser/engine, repository,
  ViewModel, screen, `LinkedInShareStore`, navigation routes, the dashboard
  entry tile, the `MainActivity` SEND-intent handling, the manifest
  `<queries>`/intent-filter block, `LINKEDIN_BASE_URL` build-config plumbing,
  and its dedicated tests. This is a removal, not a deprecation — no
  LinkedIn-shaped surface remains in the app.
- **Decision:** `https://github.com/aishsynk/InTouch` is **not** a release
  repository for this product and must not receive further pushes under
  that identity. Package `com.example.skillsync` and the existing release
  signing key are unchanged (per the `2026-09-14` entry's own internal-IDs
  clause, which remains correct even though the brand decision above it does
  not).
- **Why:** operator identified this as a production-contamination incident —
  both the rebrand and the LinkedIn feature were introduced and, in one
  case, pushed to `main` (`v3.80.10.185`) without being the authorized
  product. Recovery was performed on a dedicated branch
  (`recovery-skillsync-identity`), file-by-file against a recovery manifest,
  restoring brand strings/assets from the last known-clean tag
  (`v3.80.7.182`) while preserving all legitimate work layered on top since
  then (Design V2 visual system, Communication Intelligence C0–C4). See
  `AI/PROGRESS.md` "Phase 6D — Production contamination recovery" for the
  full file-level record; that log entry is appended, not a rewrite of the
  Phase 6B/6C history, which stays as the accurate record of what actually
  shipped and when.

## 2026-09-15 — Communication Intelligence: fact-selection layer gets a recipient-resolution step, not a new engine

- **Decision:** the fix for nonsensical aggregate-KPI messages ("There are 5
  open batches... and 2 of us are free") is a new `CommunicationPlanner`
  object that resolves WHO to message from real per-trainer facts
  (capability match + verified availability), feeding the existing
  `ContextSelectionPlan` → `CommunicationComposer` → `CommunicationValidator`
  pipeline unchanged. It is explicitly not a parallel/competing engine.
- **Why:** audit (Phase C0, this session) found the real defect wasn't
  missing intelligence — `CommunicationContextSelector`'s auto-mode (Flow B)
  already picks a sensible purpose/action/facts — it never resolved
  recipients. Every caller hardcodes `recipientType = "TEAM"`, so an
  aggregate count got spoken as if it named specific available people.
- **Also confirmed by the same audit, not yet fixed:** there are 4-5
  independent message-template systems in the app (the engine above,
  `WeeklyMessage.kt`, `MessageRewriter.kt`, `BatchShare.kt`/
  `BulkBatchShare.kt`, and backend `_viber_queue_build`), each with its own
  purpose/tone vocabulary. Consolidating them is explicitly deferred to a
  later phase (C3/C4) — this decision covers only the recipient-resolution
  fix, not full consolidation.
- **Availability semantics:** `AvailabilityState` has three states —
  `AVAILABLE` (verified), `COMMITTED` (verified booked/on leave — never
  contacted), `UNKNOWN` (not checked). A capability match with `UNKNOWN`
  availability is asked to confirm, never told they are available. No
  candidate data at all produces an honest team message that says
  availability needs confirming — never a claim that N people are free.
- **Known, separate bug this decision does NOT fix:** `backend.py`'s
  `_viber_dispatch_item` returns `"status": "SENT"` even on the
  no-token/simulated path, and `ViberDispatcher.kt`'s Accessibility/Intent
  mode marks an item `STATUS_SENT` immediately after firing a share Intent,
  with no delivery confirmation — both misrepresent "opened a share sheet"
  as "sent". `CommunicationScreen.kt`'s own manual flow already gets this
  right (`SHARED_EXTERNALLY`, not `SENT`) — the Viber Automation path needs
  to adopt the same honest status, in a later phase (C5/C6).

## 2026-09-14 — Product is branded InTouch Intelligence; external release repo is aishsynk/InTouch

- **Decision:** public product name is **InTouch Intelligence**. Naming hierarchy:
  1. full brand "InTouch Intelligence" (app label, login, formal docs);
  2. worn mark "InTouch" + symbol (launcher, compact surfaces, notifications);
  3. internal identifiers **unchanged** (package `com.example.skillsync`, prefs
     `skilledge_session`/`skillsync_cache.db`/`skillsync_seen_batches`, worker names,
     channel IDs `skillsync_alerts_v3`/`skilledge_monitoring`) — renaming these breaks
     user-data compat for zero user value.
- **Decision:** `https://github.com/aishsynk/InTouch` (PRIVATE) is the canonical release
  repository (version ledger, release records, governance, brand masters). Source remains in
  private working repos. Ledger is **append-only**; corrections are appended, never in place.

## 2026-09-15 — Versioning correction: NEVER bump MINOR without explicit instruction; sequence is one continuous patch train

- **Decision (operator correction, stated forcefully — do not relitigate this):**
  `versionName` stays in the **same `3.80.x` sequence, patch digit only**,
  every release, indefinitely, until the operator explicitly says otherwise.
  `versionCode` is always `previous + 1`. The `2026-09-14` entry below
  proposing a jump to **`3.81.0`** for the next (InTouch-branded) RC was
  **wrong** and is superseded by this entry. A rebrand is not, by itself, a
  reason to bump `MINOR` — nothing in `MAJOR.MINOR.PATCH` semantics requires
  it, and the operator does not want it.
- **Correct next version:** `versionCode 185` / `versionName "3.80.10"` —
  continuing directly from the last published `184` / `3.80.9`
  (`v3.80.9.184`, commit `55b1f88`). Not `3.81.0`.
- **Why this matters:** two independent work threads in this repo (the Today
  Design V2 release train and the InTouch rebrand/governance thread) each
  proposed their own versioning continuation, and they disagreed. Only one
  sequence exists. Before any thread bumps the version, it must check the
  latest published release tag/`build.gradle.kts` on `main` — never plan a
  version number from a stale docs snapshot.
- Bump happens only at the RC/release cut, never to "show progress".
- Same `applicationId` (`com.example.skillsync`) + same signing key forever → in-place
  upgrade without an uninstall prompt. Do not change `targetSdk` behaviour related to version.
- **Why:** production installs exist; a version bump is meaningful only when a candidate is
  actually cut. Bumping during development creates false release records.

## 2026-09-14 — Android brand iconography derives from the circular symbol master

- **Decision:** adaptive icon background = flat white; foreground = symbol at ~62 dp on the
  108 dp canvas (inside safe zone); monochrome = white silhouette (L-channel alpha mask so
  Pillow resize never fringes RGB); legacy `ic_launcher.png` white-background full-bleed +
  circular-masked `ic_launcher_round.png`; notification small icon = white silhouette
  `ic_notification_intouch` (24/36/48/72/96 px buckets). Login logo = transparent symbol.
  Masters live in the InTouch release repo (`assets/branding/`).
- **Why:** full-brand identity was explicitly authorized; the White BG + symbol keeps the mark
  legible in every launcher shape. Notification channel IDs and worker names are preserved.
- Verification is programmatic today; device visual QA is a documented PENDING gate.

## 2026-09-14 — Real-emulator screenshots are the only screenshot-validation path; Robolectric captureToImage is retired

- **Decision:** all future visual/screenshot validation uses an instrumented test
  (`createAndroidComposeRule`) run on a real Android emulator (`reactivecircus/android-emulator-
  runner`, API 34/`google_apis`/x86_64/`pixel_6`) via Android Test Orchestrator + Test Storage.
  Never use JVM/Robolectric `captureToImage()` for this project again.
- **Why:** `captureToImage()` deadlocks in `WindowCapture.forceRedraw`/`PixelCopy` under
  Robolectric — confirmed identically on Windows (local) and an `ubuntu-latest` CI runner. This
  rules out an OS-specific cause; it is a Robolectric limitation, not something more config can
  fix. A plain `adb pull` of the instrumented test's output afterwards also fails, because
  Gradle uninstalls the app-under-test as soon as `connectedDebugAndroidTest` finishes — Test
  Orchestrator/Test Storage copy files off the device before that uninstall.
- **Impact:** `PilotScreenshotTest` (JVM) is kept only as a documented, permanently-failing
  baseline (4 known failures) that proves the limitation; `PilotScreenshotInstrumentedTest`
  (androidTest) is the real, passing pipeline. Any new screen wanting screenshot coverage should
  extend the instrumented test, not the JVM one.

## 2026-09-14 — Domain-specific repositories over one growing ManagerRepository

- **Decision:** when a ViewModel is found bypassing the repository layer (calling
  `RetrofitClient.instance` directly), fix it with a small, domain-specific repository
  (`ScheduleRepository`, `SkillRequestsRepository`) — not another method appended to
  `ManagerRepository`, which already owns most manager/team-intelligence domains.
- **Why:** a personal schedule and a skill-request-approval workflow are not the same domain as
  manager/team intelligence; growing one repository to own everything makes ownership illegible
  and turns every future fix into "which method do I add to the big file."
- **Convention:** these small repositories take `apiProvider: () -> SkillEdgeApi` as a
  constructor default (same as `ManagerRepository`) and are `open class`/`open fun` specifically
  so a unit test can fake them directly instead of implementing the ~80-method `SkillEdgeApi`
  Retrofit interface for one or two calls.

## 2026-09-14 — capacity_bucket is typed; UNKNOWN must never read as Good/healthy

- **Decision:** `capacity_bucket` (two independent backend producers, seven possible values
  between them: Unknown/Stretched/Balanced/Light/On Bench/Delivering/Steady) is mapped at
  consumption points through a `CapacityBand` enum (`feature/home/TeamTab.kt`) rather than raw
  string comparison. `CapacityBand.from(raw)` maps anything unrecognized to `UNKNOWN` — never to
  a known, positive-looking state — and `UNKNOWN` is explicitly routed to `Severity.Watch`
  (never `Severity.Good`) everywhere it drives a manager-facing severity color.
- **Why:** the exact same root cause as the `engagement_state` "active" vs. "current" production
  bug (a magic string mismatch) was found live in `TeamMemberCard.kt`/`WeeklyReportScreen.kt`,
  where an unrecognized bucket silently fell through to "Good" — telling a manager a trainer's
  load was fine when it was simply never measured. This is the project's now-established pattern
  for any business-significant status field: verify canonical backend values from source, type
  the ones that drive classification/color/filtering/navigation decisions, and always give
  "unknown" its own explicit, honest treatment.
- **Scope boundary:** this is not a mandate to type every backend string — only ones proven to
  affect a manager decision. `delivery_mode` (free-form RMS text, never compared against a fixed
  set) and `lifecycle_state` (self-consistent, Android-owned CRUD) were explicitly left as raw
  strings after the same audit, because typing them would solve no real problem.

## 2026-09-14 — D3 design rule: Today is the quality benchmark, not a layout template

- **Decision:** when Pipeline Radar, Delivery Ops, Skill Requests, and Live Sentinel are built,
  they inherit Today's Design V2 system (typography, semantic color, icons, motion, state
  components — `heroSurface`/`accentGlass`/`ActionRow`/`TimelineItem`/`IconSlot`/`Avatar`/etc.)
  but must each have their own information architecture and identity, not copy Today's section
  layout. Today = executive command centre; This Week = action inbox; Capacity Runway =
  planning/analytics; Pipeline Radar = future-demand intelligence; Delivery Ops =
  scheduling/operations workspace; Skill Requests = request-decision workflow; Live Sentinel =
  monitoring/compliance.
- **Why:** the original Design V2 complaint this session started from was screens becoming "three
  dark pages full of cards" — reusing components is correct, reusing layout is the same mistake
  in a new coat of paint.

## 2026-09-14 - Phase 6B LinkedIn capture client: human-in-the-loop, metadata-only telemetry, no auto-posting

- **Scope:** Android client consuming the Phase 6A engine (`POST /api/v1/captures/analyse`). The
  app proposes reactions/comments/copies; a human always executes. No post action is ever
  performed automatically (no accessibility automation — Phase 7, deferred).
- **Entries are (1) the share intent and (2) a dashboard card** — no floating button, no
  notification route yet. Share intent lands via `LinkedInShareStore.accept()` in `MainActivity`
  (`onCreate` + `onNewIntent`), consumed once after nav routes; the store is transient
  in-memory, deliberately not persisted.
- **Backend address is a build-time config, not runtime:** `LINKEDIN_BASE_URL` BuildConfig field
  fed by gradle property `-PlinkedinBackendBaseUrl`. Empty default ⇒ the UI shows a "not
  configured" hint — no placeholder host, no silent prod-shaped endpoint. Cleartext HTTP
  (`10.0.2.2`/`localhost`/`127.0.0.1`) is **debug variant only** via
  `network_security_config.xml`; release is HTTPS-only.
- **URI-only captures are blocked on the client too:** the exact backend 422 contract string
  (`CAPTURE_TEXT_REQUIRED: LinkedIn shared only a post link. …`) is surfaced client-side before
  any request, so the user is told to paste the visible post text up front.
- **Relationship/interaction context is omitted from requests** (backend derives `UNKNOWN`):
  the debug build has no session/token plumbing to the capture API and there is no trusted
  relationship graph on-device. Post text and author name are editable preview state only.
- **Telemetry is metadata-only:** `Log` events carry `capture_id`, `text_length`, `content_hash`
  — never the post text — matching the Phase 6A CAPTURE_METADATA-only rule. `LocalClipboardManager`
  (deprecated API, used elsewhere in the app) accepted for copy feedback.
- **Gate evidence:** 254 unit tests / 11 pre-existing failures (0 new); lint delta 0
  (6E/79W/3H baseline); `assembleDebug` green. New-warning fixes this session: `<queries>`
  manifest block for guarded `ACTION_VIEW`, KTX `String.toUri()`.
- **Not decided yet / open:** (a) when to cut Phase 6B as a numbered release once a backend
  URL exists; (b) whether accessibility-service capture (Phase 7) will ship against the same
  422-policy.

- **Finding:** `v2_team_readiness` (backend.py) contained a hardcoded 8-named-person fallback
  roster used whenever RMS returned no reportees for a manager, and a synthetic leave date
  injected specifically for `neha.sharma@koenig-solutions.com` when RMS showed none. This is the
  same fabrication class already removed from `_capability_for()` (2026-09-13 entry above), found
  while wiring this exact endpoint's data into Today's "Who is actually free" section.
- **Fix:** both removed. An empty RMS roster now returns an honestly empty `trainers: []` /
  `counts.roster: 0`, not 8 invented people; a trainer with no recorded leave shows
  `leave_days: 0`, regardless of email.
- **Regression tests added:** `test_empty_roster_is_honest_not_a_fabricated_fallback`,
  `test_no_synthetic_leave_is_injected_for_any_named_trainer`
  (`tests/test_certification_and_allocation.py`). Full backend suite: 360/360 passing.
- **How to apply:** any UI reading `/api/v2/team/readiness` (Today's availability section,
  Team Health) must already treat an empty/zero response as a legitimate "nothing to report"
  state, not a bug — it already does (checked-count-gated rendering).

## 2026-09-13 - Technical debt: no deployed build identifier — `/healthz` cannot confirm which commit is live {#build-info-tech-debt}

- **Gap found during Phase 1 production verification:** `/healthz` returns a hand-written static
  `"version": "6.1.0"` string, not a git commit SHA or build timestamp. There is no way, from the
  running service alone, to confirm which commit is actually deployed — verification of the
  2026-09-13 capability-foundation release (`4bf55d2`/`642cb6d`) is blocked on this for item 8
  specifically (see `AI/PHASE1_CAPABILITY_FOUNDATION_2026_09_13.md` and the production-verification
  turn in session history). Render dashboard access or an API token can answer it manually today;
  the service itself cannot.
- **Decision:** record as technical debt only. **Not implemented now** — this is a hold-period
  documentation entry, not a code change.
- **Proposed future mechanism (design only, not built):** extend `/healthz`'s response with a
  small, explicitly non-sensitive `build` object:
  ```json
  {
    "status": "ok", "service": "SkillSync Backend", "version": "6.1.0",
    "build": {
      "app_version": "6.1.0",
      "git_commit": "4bf55d2",
      "build_timestamp": "2026-09-13T18:40:00Z",
      "environment": "production"
    }
  }
  ```
  - `git_commit`: short SHA, populated from Render's own `RENDER_GIT_COMMIT` env var (Render sets
    this automatically per deploy — no new secret or manual step needed) via `_ev`-style env read
    with an empty-string fallback so local dev without that var doesn't break.
  - `build_timestamp`: set once at process start (`datetime.utcnow().isoformat()` captured at
    import time into a module-level constant), not recomputed per request — a build time, not a
    request time.
  - `environment`: from an existing or new `SKILLEDGE_ENV` env var (`production`/`staging`/`dev`),
    defaulting to `"unknown"` rather than guessing.
  - **Explicitly excluded, per "non-sensitive metadata only":** no RMS credential state, no
    `_ev_fallbacks` list (that already exists behind `?rms=1` and is a separate, intentionally
    gated diagnostic), no database paths, no internal hostnames.
- **How to apply:** implement only when explicitly requested — not part of this hold, not part of
  Phase 1 closure, and not a prerequisite for it (Phase 1 closure per the operator's own five
  conditions relies on manual Render dashboard verification this hold period, not on this).

## 2026-09-13 - `_capability_for()` fabricates capability data for 8 named trainers and any trainer with empty RMS data — flagged, not yet fixed {#capability-intelligence-foundation}

- **Finding (Phase 0 of the capability-intelligence foundation work):** `_capability_for()`
  (backend.py:3818-3917) — used by three live endpoints, `/api/v2/data/team-capability` (+legacy
  `/api/data/team-capability`), `/api/v2/capability/portfolio`, and `/api/v2/capability/cert-intel`
  — contains a hardcoded `known_courses`/`known_certs` dict of **8 named trainer email
  addresses**, each with fully invented course/vendor/qubits-score/skill-level/cert data, used
  whenever RMS returns no capability rows for that trainer (`if not caps:` / `if not held_certs:`).
  Worse: both dicts have a **generic fallback** (`known_courses.get(email, [{"course": "AZ-104"...
  }])` / `known_certs.get(email, [{"name": "AZ-104"}, {"name": "MCT"}])`) that hands **any**
  trainer with empty RMS data the same fabricated AZ-104/MCT record, not just the 8 named ones.
- **This directly contradicts the existing "Nah-fabrication rule" decision below** (capability
  exports must show honest empty states, never guessed values) — it predates that rule and was
  missed when it was written, or was added after and not caught.
- **Not fixed yet — decision is to remove, not patch, and not replace with new fabrication:**
  when `caps`/`held_certs` come back empty, the correct behavior is to return the same honest-
  empty-state shape (`courses: [], course_count: 0, certification: {..., "status": "no_data"}` or
  equivalent) that the rest of the capability-reporting surface already uses, per the existing
  Nah-fabrication decision — not a new hardcoded substitute. Removal is in-scope for the
  capability-intelligence foundation work, not a separate fix, since it sits inside the same
  function this initiative is redesigning.
- **How to apply:** do not read `/api/v2/data/team-capability`, `/api/v2/capability/portfolio`,
  or `/api/v2/capability/cert-intel` output as ground truth for the 8 listed emails (or for any
  trainer whose RMS `trainerDetails` legitimately has no rows) until this is removed — their
  "capability" data may currently be fabricated rather than absent.

## 2026-09-13 - Surface "Your schedule" directly on Today instead of building a new manager-as-trainer feature {#decision-manager-as-trainer-today-entry}

- **Decision:** Before building anything new for "the manager is also a trainer" (his own
  deliveries/leave/mocks belonging on his calendar and profile like any trainer's), checked
  whether it already existed. It did: `backend.py`'s `_personal_calendar_build` is explicitly
  written identity-agnostic ("the same for a trainer or a manager who also delivers"), Android
  already has `MyScheduleScreen`/`MyScheduleViewModel` calling it, and the Today header's
  `onOpenProfile` already opens Trainer 360 for the signed-in manager's own email. None of that
  needed rebuilding.
- **What was actually missing:** `onOpenMySchedule` was wired into `DashboardTab` but never
  forwarded into `ManagerCommandCentre` (Today), so the only way to reach a manager's own
  schedule was the profile-menu dropdown — invisible unless you already knew to look. Fixed by
  forwarding the callback and adding a "Your schedule" row directly on Today
  (`ManagerCommandCentre.kt`).
- **Rationale:** Building a parallel new screen would have duplicated a feature that already
  exists, was already correctly designed for this exact dual-role case, and already calls a
  verified real RMS-backed endpoint. The gap was navigational, not architectural.
- **Superseded temptation:** the Design V2 Artifact proposal's "My Profile" mockup (with a
  combined Manager+Trainer panel) is *design inspiration only* for how Trainer 360 could look
  when `managerEmail == trainerEmail`; it is not a new screen to build, since Trainer 360 already
  serves this role structurally.

## 2026-09-13 - Design V2 conflict resolved: implement the dark Command Centre, supersede the light-first Today screen

- **Decision:** Operator instructed to proceed with implementation. `ManagerCommandCentre.kt`
  (Today) was rewritten on the existing dark token/component system (`theme/Color.kt`,
  `theme/DesignSystem.kt`, `theme/SkillSyncComponents.kt`) rather than the light-first white
  layout from the prior session. The light-first direction is superseded on this screen.
- **Scope discipline:** only the Today screen was touched this pass, per the existing
  [[feedback_one_page_per_release]] rule — no other screen was redesigned in the same pass.
- **No fabricated data:** the backend intentionally leaves `readiness_trend` blank when it has
  no history (see `backend.py` comment at the `manager_kpis` dict). The new comparison-KPI
  widget renders no delta at all when the trend string is blank, rather than inventing one —
  consistent with the existing evidence-only / no-fabrication policy elsewhere in this file.
- **Bug caught before it shipped:** the initial draft used a `LazyColumn` inside
  `ManagerCommandCentre`, which is already rendered as a single item inside `MainScreen.kt`'s
  own `LazyColumn` — that would have reintroduced the nested-scrolling-list crash commit
  `064a4c6` had already fixed once. Corrected to a plain `Column` before compiling.
- **Open item, not resolved here:** `ScreenRenderTest` has 12 pre-existing failures asserting a
  richer Dashboard spec (hero, drill-through, "Explore the detail", real-availability section,
  international demand badge, Allocate CTA) that neither the light-first version nor this Design
  V2 pass implements. Whether to build that spec next or update the tests is an operator call,
  not assumed here — see `AI/PROGRESS.md` handover.

## 2026-09-13 - Design V2 (Advanced) dark Command Centre adopted as the reference UI direction — conflicts with in-flight light-first WIP, unresolved

- **Decision:** A dark, glass "Command Centre" visual system (navy/royal/azure/brand/sky/cyan,
  single dark theme, no light variant) fused with Color Admin's widget depth (comparison stat
  cards, allocation funnel, ranked lists, regional breakdown, campaign card) and split
  iOS/Material motion rules is adopted as the **reference design direction** for the manager
  app going forward. Delivered as a Claude Artifact proposal, not yet implemented in code. Full
  spec recorded in `AI/CONTEXT.md`.
- **Conflict, explicitly flagged, not resolved:** the working tree already carries an
  **uncommitted** "light-first enterprise" rewrite of `ManagerCommandCentre.kt` (white/Notion-
  style, from the prior Antigravity/Gemini session, 2026-09-13T03:38) built on the opposite
  premise (light theme, not dark). The two directions are mutually exclusive. Do not implement
  further screens against either direction until the operator explicitly picks one — building
  on both wastes work and risks shipping an inconsistent app.
- **Rationale:** The operator asked for a premium, admin-panel-depth manager OS and reviewed/
  approved the dark Command Centre proposal in this session; that supersedes the prior session's
  light-first instruction *as a design direction*, but since the light-first code was never
  reverted or committed, the conflict must be resolved by the operator, not assumed by an agent.
- **Baseline unaffected:** v3.80.3.178 (Communication Intelligence Service & Wave 5) remains the
  last stable, non-beta released version. Neither the light-first WIP nor Design V2 has shipped;
  both are pre-release work sitting ahead of that baseline.

## 2026-09-10 - Opportunity matching is evidence-based, not score-threshold based (supersedes the 2026-09-05 mapping)

- **CORRECTED 2026-09-13** (Phase 0 fact-check, [[capability-intelligence-foundation]]): this
  entry originally described a design intent, not shipped behavior, and was inaccurate as a
  record of what `match_opportunity` (backend.py:12426-12554) actually does. Verified against the
  only commit that has ever touched the function (`00025ed`, 2026-09-10 — the same day as this
  entry) and a repo-wide search for the other evidence dimensions: no richer version was ever
  implemented or later removed. **What is actually live:** one evidence item per topic
  (course-match / certification-code / technology-name match, strength `STRONG | MODERATE | GAP`),
  plus the `escalate` rule (fires only when the opportunity is both international and critical).
  **Not implemented, anywhere in the repo:** separate evidence items for dates, location/travel,
  mode, participants, or documents — despite being named below. `decision` values
  `accept | decline | pending | insufficient_evidence | escalate` are real.
- **Original (aspirational) decision text, kept for history:** "`match_opportunity` no longer
  maps one number to a verdict. Each demand aspect (course match, dates, location/travel, mode,
  participants, documents, skill level, international/critical flags) produces an evidence item
  with strength `STRONG | MODERATE | GAP`..."
- **Rationale (still valid for the part that shipped):** a percentage alone hid *why* an
  opportunity was or was not accepted; the course/cert evidence item plus the escalate rule give
  an auditable reason for that one dimension. The broader multi-dimension evidence trail described
  above remains a legitimate future improvement, not a regression to chase — it was never built.
- **Supersedes:** the 2026-09-05 "Opportunity-friendly decision model" entry (score buckets 90/75/60/40).
- **How to apply:** don't cite the original decision text as current backend behavior. If the
  fuller evidence model is wanted, it's new work (dates/location/mode/participants/documents each
  need their own evidence-building logic added to `match_opportunity`), not a bug fix.

## 2026-09-10 - Critical opportunities are never silenced by quiet hours

- **Decision:** During quiet hours only normal messages and high-value opportunities respect the quiet-hours toggle. Critical opportunities always reach the manager; `quiet_hours_critical_opportunities` now controls *escalation persistence* (persistent on-screen alarm via `showEscalation` vs. standard alert), not delivery.
- **Rationale:** The cost of missing a critical international opportunity is higher than the cost of a disruption at night. Silencing critical alerts by default would defeat the Guardian's purpose.

## 2026-09-10 - Nah-fabrication rule: capability exports and list screens show honest empty states

- **Decision:** `CapabilityGraphScreen`, `SkillProfileScreen`, `OpportunityGuardianScreen`, and the skill-profile endpoint show real data only (`experience_years: 0`, empty `labs_projects`, empty roster messages) and never fall back to guessed values or placeholder names.
- **Rationale:** The module is a decision system; fabricated scores or names would be acted on by managers. Consistent with the existing evidence-only report policy.

## 2026-09-05 - Opportunity Guardian becomes a major SkillEdge module (not a separate app)

- **Decision:** The Opportunity Guardian concept is implemented as a **major layer inside SkillEdge**, not as a separate application. SkillEdge already revolves around skills, qualifications/certifications, opportunities, training, recommendations, applications and notifications — the new feature is a natural extension, not a bolt-on.
- **Rationale:** Building a separate app would duplicate the skill profile, notification system, and user management. SkillEdge's existing Android client (Kotlin/Compose) provides the platform capability for Viber notification listening. The web frontend (React/Vite + Node/Express + MongoDB) provides the data layer.
- **Architecture:** Android native Kotlin/Compose for notification listening (platform capability); React/Vite + Node/Express + MongoDB for the web backend and data persistence; Flask Python (`backend.py`) continues as the existing API server.

## 2026-09-05 - Capability Graph replaces flat skill tags

- **Decision:** The SkillEdge skill profile evolves from a flat list of skills (`"SQL, Azure, Power BI"`) to a real **Capability Graph** with three dimensions: Certified (RMS certifications), Delivered (courses delivered), Built (labs/projects). Each dimension feeds a Strong / Moderate / Gap classification per topic.
- **Rationale:** A flat skill list cannot express the nuance needed for opportunity matching. A graph with evidence dimensions enables confidence scoring, topic-level gap analysis, and the "why yes?" evidence trail.

## 2026-09-05 - Opportunity-friendly decision model

- **Decision:** SkillEdge uses an opportunity-friendly scoring model — STRONGLY ACCEPT (90–100%), ACCEPT (75–89%), CONDITIONAL ACCEPT (60–74%), HIGH RISK (40–59%), DECLINE (0–39%). It does not become overly conservative.
- **Rationale:** The product goal is to ensure opportunities are not lost unnecessarily. A conservative model would decline too many opportunities. The model provides evidence-based recommendations with preparation estimates and conditional acceptance.

## 2026-09-04 - Publication block lifted for v3.70.0 / Build 162

- **Decision:** The operator explicitly authorised "build and publish like always on git a/c".
  v3.70.0 / Build 162 was cut: full validated tree committed and pushed to `main`, CI release +
  Render backend deploy proceed as normal.
- **Superseded:** the earlier same-day block below. Residual items (RMS password rotation, Render
  secret provisioning, notification/freshness pass, capability sweep) move to normal backlog and
  no longer gate releases unless the operator reinstates a block.

## 2026-09-04 - Publication was blocked until review points resolved (superseded same day)

- **Decision:** Do not publish or deploy any package to Development or Production while review
  points remain open. Publication requires all identified points to be resolved, validation to
  complete, and an explicit final confirmation with the operator.
- **Rationale:** The current review is iterative and the operator wants one fully reviewed,
  confirmed package rather than partial releases between fixes.

Important decisions and their rationale. Add new entries at the top (newest first).

## 2026-09-04 - Retire the V3/V4 editorial theme; compose the allocation message server-side

- **Decision:** The warm-graphite + brass + Fraunces-serif design (V3.60–V3.72) is reverted to
  the pre-V3 clean blue console. Only token *values* in `Color.kt`/`Type.kt` change; every
  token name is kept so no screen file is touched. Status hues follow the web convention
  (info-blue / success-green / warning-amber / danger-rose). Operator instruction 2026-09-04.
- **Decision:** The unallocated-batch broadcast is composed by the backend
  (`_compose_batch_message` / `GET /api/data/batch-message`), not the Android `BatchShare`
  class. The app renders it verbatim with `BatchShare` as offline fallback. Rationale: the
  wording has changed repeatedly and each change needed a full APK release; server-side ends
  that loop.
- **Correction:** RMS `globalTrainers` (key 157, "Get Inhouse and FL Trainers Of Courses")
  **works** with `TrainerType` = "Inhouse" or "FL". The prior CONTEXT/audit note that no value
  was accepted was wrong. The Wider Trainer Network feature is now real. `get_alternative_trainers`
  queries both types then expands to the 3 closest sibling courses (title token-Jaccard ≥
  `?related=`, default 0.55) from `_course_catalogue_index()`; trainers carry
  `match: exact|related` + `via_course`.

## 2026-09-11 - Password gate restored for every privileged role (reverses 2026-09-04 password-less login)

- **Decision:** Every privileged role (manager / assistant_manager / trainer_plus) signs in
  with a password. First sign-in bootstraps with the RMS employee code (`must_change`), then
  the account owner sets their own password via `/api/auth/set-password`. `_needs_password`
  is True for the privileged set; the `reportee_store.py` credential table is the live
  mechanism again (previously "dead code for rollback only").
- **Rationale:** Operator directive 2026-09-11: "no one can login there be security measures,
  only manager/trainer+ use password protection." Reverses half of the 2026-09-04 decision
  (the password wall) while keeping the other half (no `reportee` role; `_classify_identity`
  still fails open to the manager app on RMS blips — the fail-open now routes to a full
  password sign-in rather than to a bare work-ID button).
- **Decision:** Android dropped the silent email-only re-auth interceptor. A 401 now clears
  the stale session and routes back to Login (email alone can no longer mint a session).
- **Supersedes:** the password-less portion of the 2026-09-04 "withdrawn; login fails open"
  decision below.

## 2026-09-04 - Reportee self-service tier withdrawn; login fails open to the manager app

- **Decision:** No account is classified `reportee` any more. A recognised
  `@koenig-solutions.com` email is a `manager` unless RMS positively flags it `trainer_plus`
  or a titled manager inside a roster. Sign-in is by work ID alone (`_needs_password` is
  always False); the password / employee-code path is dead code kept only for rollback.
- **Decision:** `_classify_identity` fails **open** — when the RMS roster call does not
  answer, the account still gets the manager app, never a restricted empty view.
- **Rationale:** The reportee tier plus mandatory password turned every RMS connectivity
  blip into "the whole app is placeholder data and I am not a manager". The product only
  ever needed the manager / trainer+ experience. Operator instruction 2026-09-04.
- **Decision:** Keep RMS credential fallbacks inline in `_APIS` (not only in
  `rms_service_credentials.py`) so an import failure on the host cannot blank every
  credential at once. Rotation is still the real fix (operator-pending).
- **Supersedes:** the 2026-09-02 "Reportee self-service role + initials-only login" and
  2026-09-02 "password entry is mandatory" decisions below.

## 2026-09-04 - RMS credentials isolated to one file; opt-in enforcement; single capability predicate

- **Decision:** All RMS service-account fallback credentials live only in
  `rms_service_credentials.py` (`FALLBACKS` map), never in `backend.py`. The file stays tracked
  so Render still boots before secrets are provisioned; production is expected to set every
  `SKILLEDGE_RMS_*` env var (see `.env.example`). `_validate_credentials()` hard-fails only when
  `SKILLEDGE_REQUIRE_SECRET_CREDS` is set — an explicit switch the operator flips once secrets
  are in place — otherwise it warns. Rationale: removes plaintext secrets from the main module
  and shrinks rotation to one file without handing the operator a broken deploy.
- **Decision:** `SessionManager.canManageTeam()` (manager / assistant_manager / trainer_plus) is
  the only client-side predicate for "may act on other people". Every cross-person write
  affordance must gate on it; the backend gates the matching routes via
  `_v2_manager_session(manager_only=True)`.
- **Decision:** `/api/v2/data/*` and `/api/v2/action/*` are the canonical routes. Legacy
  `/api/data/*` and `/api/action/*` remain only as deprecated aliases; new clients use v2.

## 2026-09-02 - Reportee self-service role + initials-only login

- **Decision:** Add a second role. Login takes the work-ID local part only; the client
  appends `@koenig-solutions.com`. The backend classifies the identity: roster owner (or
  unknown) → `manager` (unchanged, no password); an email inside some manager's roster with
  no roster of its own → `reportee`, authenticated by password. First reportee password is
  the RMS employee code, then a forced change; PBKDF2 hashes in a new
  `reportee_store.py` sqlite DB, never plaintext, never written to RMS.
- **Decision:** A reportee sees only their own Trainer 360, their skill-matched unallocated
  demand, and an updates feed. `_v2_manager_session` hard-rejects reportee tokens;
  `_profile_session` widens per-person reads to "self or manager-in-scope".
- **Decision:** A reportee may self-certify a skill only up to **level 4**. Anything higher
  is blocked from RMS and raised as a `skill_requests` row that notifies the manager and the
  reportee; the manager approves (performing the real verified write at the requested level,
  `OfficiallyApproved=Yes`) or denies. Rationale: keeps self-service useful for low-stakes
  skills while a human owns every senior-level claim, and preserves the existing
  "only a manager writes an official skill" guarantee.
- **Decision:** The reportee→manager mapping is derived from manager roster loads only (no
  reverse RMS lookup exists). A reportee therefore cannot sign in until their manager has
  used the app once. Accepted because the manager is the primary user.

## 2026-09-01 - Android Viber Background Automation & Silent Auto-Dispatch
- **Decision:** Implement a 3-tier background automation architecture to automatically generate candidate-matched unallocated demand messages, Monday weekly standpoint notes, and compliance recording nudges, and dispatch them to Viber without manual manager copy-paste:
  1. *Backend Queue Builder (`GET /api/v2/viber/queue`)*: Automatically cross-references certified reportees against unallocated batches and evaluates weekly standpoint notes, generating candidate-specific house-style Viber messages.
  2. *Hybrid Multi-Strategy Dispatcher (`ViberDispatcher`)*:
     - **Tier 1 (Bot REST API / Webhook)**: Silent background HTTP dispatch (`POST /api/v2/viber/dispatch` or direct to Viber Public Account API).
     - **Tier 2 (On-Device Accessibility UI Automation)**: Android `AccessibilityService` (`ViberAutomationService`) targeting `com.viber.voip` to automatically populate input fields and click send.
     - **Tier 3 (1-Tap Fast Intent Share)**: High-priority heads-up action button and dialog for rapid review and dispatch.
  3. *Background Monitoring Loop (`ViberAutomationEngine`)*: Evaluates queue items and processes auto-send rules every 90 seconds in `MonitoringPass.run()`.
  4. *Manager Cockpit & Direct Action Shortcuts*: Dedicated `ViberAutomationScreen` in the Bento Grid, with 1-tap **"🚀 Auto-Send"** buttons embedded directly into `WeeklyReportScreen` and `PrioritiesScreen`.
- **Rationale:** Removes the tedious, error-prone manual copy-and-pasting friction for delivery managers, ensuring open batches and weekly standpoint updates reach instructors immediately across Viber.

## 2026-09-01 - Executive Mobile UI/UX Overhaul: Floating Dock, Bento Grid & Roster Elevation
- **Decision:** Transform the main app shell, navigation bar, command dashboard, and trainer roster into an executive-grade glass console:
  1. *Floating Frosted Glass Dock (`SkillSyncNavBar`)*: Replace the flat bottom bar with a floating dark acrylic dock with top specular cyan highlight, active animated gradient pill container (`#2563EB` to `#06B6D4`), and high-contrast typography.
  2. *Transparent Top App Bar*: Remove opaque background to seamlessly blend into `AuroraBackground()`, adding an online green pulse beacon dot (`#10B981`) to the manager profile avatar.
  3. *8-Tile Bento Command Grid (`ManagerCommandCentre.kt`)*: Overhaul executive operations into an 8-tile Bento Grid launching all manager consoles: This Week Priorities, Pre-Demand Radar, Live Delivery Sentinel, Weekly Standpoint, HR Review, Capacity Runway, Customer Accounts, and Copilot AI.
  4. *Trainer Roster Card Modernization (`TeamMemberCard.kt`)*: Add a live real-time Delivery Status Pill (*Delivering*, *Scheduled*, *Preparing*, *Available*, *On Leave*) with matching illuminated dot, translucent specular borders, and high-contrast micro KPI metrics.
- **Rationale:** Delivers a modern, executive-grade mobile interface with immediate operational clarity, frictionless navigation, and direct access to all strategic manager capabilities.
- **Decision:** Implement 4 high-value managerial capabilities leveraging untapped RMS APIs:
  1. *Pre-Demand Pipeline Radar (`activeSCDate` - Key 13)*:
     - Provide early sight into signed Service Confirmations (14–30 day lead time).
     - **Confidentiality boundary**: `Total Fee` and `Currency` are strictly stripped at the backend boundary and never exposed to the client.
     - Automatically matches reportee certified skills to compute candidate counts and actionable reservation recommendations.
  2. *Live Delivery Compliance & Daily Recording Sentinel (`recordingDetails` - Key 278)*:
     - Monitor active delivering reportees in real-time.
     - Calculates expected session recordings based on elapsed days of the batch.
     - Flags missing uploads (`RECORDING_MISSING_URGENT`) and prepares a pre-composed 1-tap Teams/Viber nudge message for the manager.
  3. *1-Tap IDP Skill Endorsement (`addTrainerSkill` - Key 255)*:
     - Allow managers to officially endorse completed development goals/certifications directly into RMS.
     - Enforces strict manager-scope authorization (trainer must be an official reportee).
     - Automatically marks linked `DevPlanStore` goals as `done` and logs an immutable audit event in `ActionStore`.
  4. *Learner Voice Word-Cloud & Sentiment Engine (`trainerFeedback` - Key 244 & `trainerNegFeedback` - Key 218)*:
     - Extracts positive sentiment ratio (praise %), top praise keyword themes (e.g. "hands-on labs", "deep knowledge"), growth coaching themes (e.g. "pacing & speed"), and categorized verbatim student quotes.
- **Rationale:** Empowers delivery managers to shift from reactive escalations to proactive advance planning, real-time quality assurance, seamless skill progression, and deep qualitative learner insight.

## 2026-09-01 - "Enterprise Intelligence Glass" UI/UX Modernization & Polish
- **Decision:** Elevate the entire visual system and screen ergonomics into an executive-grade "Enterprise Intelligence Glass" console:
  1. *Aurora Mesh Ground*: Deploy multi-point radial aurora lighting (`AuroraBackground()`) across all core screens (`PrioritiesScreen`, `WeeklyReportScreen`, `HrMonthlyReportScreen`, `Trainer360Screen`, `BatchDetailScreen`), creating a luminous, deep midnight cobalt canvas.
  2. *Luminous Micro-Borders*: Standardize 1dp translucent alpha borders (`alpha = 0.28f` surface / `0.90f` specular top-edge) on all cards and chips for crisp contrast on OLED and IPS mobile screens.
  3. *Tabular Numerals (`tnum`)*: Enforce `fontFeatureSettings = "tnum"` across typography tokens for all metrics and numbers to eliminate width jitter.
  4. *Ergonomic Hierarchy & Rhythm*: Modernize navigation bars into sleek floating glass pills, clean up 2x3 pulse metric grids, and elevate the 3-section structured feedback cards (`Strength`, `Area of Improvement`, `Manager's Verdict`).
- **Rationale:** Deliver an executive, world-class mobile interface matching enterprise design standards, ensuring high readability, visual polish, and frictionless decision making for delivery managers.

## 2026-09-01 - Intelligent AI Mind Message Generation Overhaul (4 Waves)
- **Decision:** Overhaul message generation across reportee 1-on-1 notes, HR monthly evaluations, team broadcasts, unallocated batch sharing, and Copilot answers to act as a deeply cross-referencing "AI Mind".
  1. *Signal Cross-Referencing*: Connect cert gaps directly to live open demand to quantify opportunity cost in participant-days, batches unlocked, and Trainer Index point gains.
  2. *Feedback Intelligence*: Clustered deterministic learner feedback themes (depth of knowledge, practical labs, engagement, clarity, pacing) are embedded directly into strengths and coaching areas.
  3. *Career Progression*: Integrate Trainer Index points, tier milestones, Qubits scores, and ramp stages (onboarding/stalled/first-deliveries) into weekly/monthly standpoint notes.
  4. *House Rules*: Strictly enforce group-safety house rule (never name an individual negatively in team broadcasts) and Teams/Viber prose format (Greeting `Hello _First_,`, blank line, sanitised body with at most one `**bold**` action and one `__underlined__` time ref, blank line, italicized closing `_Thank you..._`, max 1000 chars, no banned formatting/emojis/bullets/hyphens).
- **Rationale:** Messages were previously generic templates that failed to leverage the rich 20+ signals available in the backend. Quantifying the ROI of closing cert gaps and referencing real learner feedback themes drives actionable managerial coaching and career growth for both manager and reportees.

## 2026-08-31 - Manager-view wave 4: ramp / accounts / benchmark / dev-plans (v3.55.0)
- **Decision:** Four more read/light-write surfaces, completing the 14-item list. **Ramp** and **Accounts** are pure deterministic aggregations over `reportees` + `prevUpcoming` + `demand`. **Benchmark** compares the team to a baseline whose composition is printed on the screen (`baseline_source`): the two learner-feedback metrics use the genuine company-wide RMS-244 population (that endpoint returns every recent trainer, not just this manager's), the other three use delivery thresholds already constant in the codebase — never a fabricated peer-manager average, and the headline says "Baseline not available" when team data is too thin. **Development plans** get a real persistence layer: `DevPlanStore`, a SQLite file under `SKILLEDGE_STATE_DIR` built exactly like the existing `ActionStore` (per-manager rows, WAL, read-only-filesystem tolerant, degrades to returning the unsaved item). Dev-plan `suggested` items are recomputed from live signals every call and never persisted until the manager adopts one; writes are manager-scoped to the manager's own reportees.
- **Rationale:** Ramp and Accounts needed no new infrastructure — they are new lenses on data already fetched elsewhere. Benchmark is the one place the "no fake data" rule bites hardest: there is no multi-manager API, so rather than invent a peer average the screen is honest about comparing against a real feedback population plus documented thresholds. Dev-plans are the first manager-authored persisted content in the app; reusing the ActionStore pattern (rather than a new mechanism) means the same disk, the same backup story, the same read-only-fs behaviour.
- **Alternatives considered for benchmark:** a synthetic peer average derived from percentile assumptions (rejected — indistinguishable from made-up); hiding the feature until a peer API exists (rejected — the threshold comparison is genuinely useful and the caption makes its basis clear).
- **Alternatives considered for dev-plans:** storing plans in RMS (no writable field), client-only storage (lost on reinstall, invisible to any other surface).

## 2026-08-31 - Manager-view wave 3: planning + advice surfaces, still preparation-only (v3.54.0)
- **Decision:** Three new read surfaces. **Capacity Runway** (`/api/v2/planning/runway`) projects 8 weeks of demand against per-week free capacity and ends in an upskilling list, not an allocation action. **Team Copilot** (`/api/v2/copilot/team`) answers 7 team questions by deterministic keyword routing (no LLM) with evidence + a confidence badge, mirroring the per-trainer Copilot. **Digests** (`/api/v2/digest`) and **delivery alerts** (`delivery_alerts` on the dashboard) are pushed from the existing `MonitoringPass` — a morning brief, a Friday wrap, and early warnings for recording gaps / roster drop / imminent unstaffed starts — reusing `_priorities_build`, the warm weekly report, and `NotificationEngine` buckets rather than adding a scheduler.
- **Rationale:** All three answer "where will my team be short, and what do I do about it" without touching allocation — the runway's output is a ranked *upskilling* list, the Copilot names free trainers but tells the manager to confirm calendars in RMS and commit the batch there, and the alerts are notifications. Deterministic routing keeps the Copilot testable and honest without credentials. Pushing digests through the monitoring pass avoids a second background mechanism and inherits its Doze-resilient foreground service.
- **Alternatives considered:** LLM Copilot (no credentials, non-deterministic); a dedicated digest WorkManager job (duplicate of the foreground pass); a runway screen that offered a "staff this week" button (violates the allocation boundary).
- **Fixed in passing:** `/api/v2/upskilling/demand-opportunities` referenced undefined `_team()` / `_fuzzy_match()` and 500'd on every call; roster now built inline, adjacency scored by token-overlap Jaccard.

## 2026-08-31 - Manager messages have four cadences; a group broadcast never names an individual negatively (v3.53.0)
- **Decision:** Every composed message — team broadcast and per-reportee — exists in four cadences: **weekly** (Monday, week ahead), **weekend** (Friday, the wrap), **monthly** (1st, month plan), **monthend** (last day, the review). `_MSG_CADENCE` carries each cadence's noun/time-ref/deadline and a `review` flag; weekend and month-end compose backward-looking prose (delivered batch count, learner feedback, recognition, thanks), weekly and monthly compose forward-looking prose (delivery load, open demand, cert gaps, one ask). The backend prebuilds all four for the team and each reportee; the client toggles between them and `/api/v2/message/compose` returns the requested one.
- **House rule (hard):** a **team** message states anything negative — bench, feedback flags, certification gaps — only as an **aggregate count** ("2 of us are free this week", "1 feedback point is being handled individually"). A person is named in a group message **only** as recognition (`top_performers` = actively delivering + learner rating ≥ 4.3 + no open flags). Per-reportee messages are 1:1 and may of course discuss that person directly.
- **Rationale:** The operator flagged that a group broadcast saying "one of you is on the bench" — with a name — is not something a manager would ever post to a team channel; it singles someone out in front of peers. Aggregate framing conveys the same operational fact (spare capacity, feedback being handled) without exposing an individual. Splitting weekend/month-end from weekly/monthly matches what managers actually send at each point in the cycle instead of reusing one all-purpose paragraph.
- **Alternatives considered:** one message with a tone switch (still leaked names); euphemising the name ("a couple of folks") — rejected as still identifiable on a 2-person team.

## 2026-08-31 - Weekly/monthly messages are genuine and the rewrite is deterministic NLP + agentic (v3.48.0)
- **Decision:** Weekly `standpoint_note` and monthly `structured_feedback` are evidence-only (utilisation, learner rating/quotes from RMS key 244 filtered by email, named cert gaps, HR/negative counts, Qubits) and bullet/hyphen/em-dash free. Trainer 360 no longer fabricates generic coaching on device; the deep profile now ships `manager_evaluation` computed server-side by `_generate_manager_evaluation`, so the three surfaces agree. The Teams/Viber house style is enforced mechanically: inputs are `[User Message: …]` and/or `[My Message: …]` (at least one), Hinglish is normalized, intent/urgency/firmness/tone/assignment/time context are detected deterministically, and output is greeting + body + closing ≤1000 chars with at most one **bold** action, up to two __underlined__ time refs, italics only for names, and course codes like AZ-305 held aside so the hyphen survives. The same engine lives in two places — Python `backend.py::_compose_rewritten` and Kotlin `ui/report/MessageRewriter.kt` — and the Android rewrite studio (team + per-reportee weekly, per-reportee monthly HR) tries `POST /api/v2/message/rewrite` first and falls back locally, so it works offline and the two sides never diverge.
- **Rationale:** Managers reported the old weekly/monthly prose was generic and inattributable (the same “pacing & articulation” boilerplate for every trainer) and the prior Trainer 360 card invented coaching on device, violating the 2026-08-30 evidence-only contract. Separately, the new house style is a strict rewrite spec (greeting/body/closing, no emojis/bullets/hyphens, Hinglish-tolerant, ≤1000) that cannot be left to ad-hoc `StringBuilder` composition. A deterministic, testable engine with a single LLM seam is honest without credentials, handles Hinglish deterministically, and keeps the backend and the device in lockstep; evidence is injected as one supporting sentence rather than being re-invented, so the message stays both genuine and style-compliant.
- **Alternatives considered:** server-only rewrite (would leave offline screens broken), client-only rewrite (would drift from the server and skip the evidence context), LLM-only rewrite (no credentials, non-deterministic, untestable against the house style). Rejected.


## 2026-08-30 - Report messages must be evidence-only; wire trainerFeedback (RMS 244)
- **Decision:** Replace the templated behavioural prose in HR-monthly `structured_feedback`, the weekly `standpoint_note` and the Trainer 360 feedback block with content derived only from real signals — learner ratings and dated excerpts (RMS key 244), named certification gaps, utilisation, HR incident counts. When a dimension has no evidence, say so. `_trainer_feedback_detail` filters key 244 by email (the endpoint ignores its own filter) and classifies text by the trainer's overall rating.
- **Rationale:** The old generator asserted the same specific observations for every trainer ("articulation remains the primary growth area", "hesitation and slight panic are visible", `mock_summary` = "Composure: Improving") with nothing behind them — the "random / makes no sense" text the operator flagged. RMS key 244 provides genuine learner ratings and comments, so the message can be true instead of plausible. It also fixed Trainer 360 rendering other trainers' feedback (unfiltered endpoint).
- **Not wired (probed live, not viable now):** `examCourseLinked` (link-check), `trainerAvailability` (empty), `uniqueCertsCount` (empty), `upcomingAssignments` (500). `courseTechnology`/`courseDomain`/`courseList` are viable and deferred to the taxonomy release.

## 2026-08-30 - Always-on monitoring uses a dataSync foreground service, not WorkManager alone
- **Decision:** Add a `foregroundServiceType=dataSync` `MonitoringService` with a permanent `IMPORTANCE_MIN` notification that runs the notification-detection pass every 90s while a manager is signed in. WorkManager stays as the 15-minute backstop; the ~60s `enqueueRapidChain` self-chain is removed. A `BootReceiver` restarts it after reboot/update and a one-time dialog requests battery-optimisation exemption.
- **Rationale:** Managers reported alerts stopped when the app was closed. Chained one-time WorkManager passes are deferred or killed by Doze and OEM battery managers, so real-time "app closed" monitoring was not actually delivered. A foreground service is the only Android-supported way to guarantee it; the persistent notification is the required, accepted cost. `MonitoringPass` is shared by the service and the worker so events de-dupe through one seen-set.

## 2026-08-30 - HR and Weekly report screens are offline-first with a loading-poll
- **Decision:** `HrMonthlyReportViewModel` / `WeeklyReportViewModel` render the last per-period `LocalCache` snapshot immediately, refresh in the background, poll while the backend reports `loading`, and keep the last snapshot on failure — the same pattern the dashboard and demand screens already use.
- **Rationale:** These two screens set `state = Loading` unconditionally on every entry and had no local cache, so a slow or cold backend left them spinning indefinitely. Pairing them with the new partial-first backend removes the dead-end spinner without changing their data contract.

## 2026-08-30 - Heavy manager endpoints answer partial-first, warm the full payload in background
- **Decision:** `unified-manager-intelligence`, `capability/portfolio`, `hr/monthly-report`, `report/weekly` and `team/calendar` no longer run their per-trainer RMS fan-out synchronously inside the request. A shared `_serve_or_warm` helper retains the last complete payload, rebuilds it in a daemon thread via an internal `?_build=1` request, serves the retained copy immediately (`refresh_in_progress`), and on a cold cache waits a bounded 45s before returning a `loading:true` skeleton. This generalises the pattern `allocation-desk` already used.
- **Rationale:** Each endpoint issued N×(2–5) RMS round-trips at 2–5s each; on a Render cold start this exceeded the mobile client's 60s read timeout, so "many pages were permanently stuck on a loading spinner." Retaining and background-refreshing the payload makes repeat loads instant and the first load bounded, without changing any screen's data contract. Client-side offline-first cache + a foreground monitoring service follow as a second release.

## 2026-08-22 - Durable HMAC Sessions, Resilient Interceptor & Managerial Coaching Intelligence
- **Decision:** Implemented cryptographic HMAC-SHA256 session tokens (`base64(email:role:timestamp).hmac_sha256`) on the backend and silent re-authentication in the Android OkHttp interceptor. Replaced flat batch timeline in `TeamCalendarScreen` with an Outlook / Bootstrap 5 styled interactive monthly delivery calendar grid with green active indicators. Added cross-domain peer benchmarking intelligence in `WeeklyMessage` and `Recommender`.
- **Rationale:** Render backend cold starts / process restarts wiped the in-memory `_sessions` dictionary, resulting in false 401s that triggered `SessionManager.clearSession()` and booted users out to the login screen during background polling and notification navigation. Cryptographic HMAC validation allows the backend to verify and revive valid session tokens across restarts without state loss. The Outlook calendar provides managers with immediate visual clarity on who is actively delivering each day, and the managerial coaching engine equips managers to upskill low-utilization reportees against high-demand cross-domain pipelines.

## 2026-08-09 - Product completion is a manager outcome plus verified phone geometry
- **Decision:** Screens are designed and accepted by the decision a 20-50-person delivery manager can make, not by the number of fields/cards implemented. Dashboard, Team, Courses, Demand, Trainer 360 and Actions must lead with attention, availability, overload, allocation, risk, readiness and interventions. Phone-layout tests must assert vertical order/non-overlap and density-sensitive geometry; text presence alone is insufficient.
- **Rationale:** v1.53.0 contained the requested sections and passed 34 render tests, yet a real device showed every section overlapping in one malformed list item. Technical presence without usable hierarchy is a product failure.

## 2026-08-09 - Demand recommendations must never write to RMS
- **Decision:** All Demand GET paths are read-only. The Aishwar international FMAT/ILT rule produces recommendation metadata only; Skill Level 8 and suggested weekend are decision-support values, not persisted skill records. The earlier v1.42.0 auto-write decision is superseded.
- **Rationale:** Page loading and polling must be safe and repeatable. A staffing recommendation is not authorization to alter the production skill register.

## 2026-08-09 - Automatic RMS skill writes are exact-account and idempotent
- **Decision:** Automatic skill marking applies only to `aishwar_v@koenig-solutions.com`, only for explicitly international FMAT/ILT demand, and only when that trainer's course match is at least 75%. The record uses skill level 8 and the next Saturday on or after the current date. The backend reads the RMS skill register before writing and never rewrites an existing skill; every write is read back and surfaced as verified/unverified.
- **Rationale:** The requested automation changes production RMS data. Exact identity, narrow delivery/location/match gates, idempotency, and verification prevent the rule from silently expanding to reportees, domestic work, weaker matches, or duplicate records.

## 2026-08-08 — v1.32.0: No invented data, ever; "current" utilisation means current

- **Decision:** Deleted the synthetic fallback team and demand, the hardcoded notification feed, and every hardcoded KPI fallback (`avg_team_utilization` 76, `utilization_trend` "+4.2%", `utilization_history` [68,71,74,72,76], `readiness_trend` "+2.4%", `open_actions or 2`, `completion_rate` 95, `deployable_pct` 90).
- **Rationale:** These were not graceful degradation, they were fiction indistinguishable from measurement. An account with no reportees — which `aishwar_v@koenig-solutions.com` genuinely is — rendered ten invented trainers with names, utilisation figures, current batches and locations, plus three "CRITICAL" alerts about them. There is no framing in which a manager staffing a batch against "Subhash Verma, 92% utilised, London" is acceptable when that person does not exist. An empty state that says "no reportees returned" is strictly better than a plausible lie, and the app already had one.

- **Decision:** `current_utilization` now means the most recent month that carried load; the three-month average moved to `utilization_avg_3m`.
- **Rationale:** RMS reports a rolling window, so the trailing months of someone just off bench are zeros. Averaging them reported the live test team at 39% and 26% when they were actually at 23% and 7% — both far more available than the app claimed. The offline project already made this distinction (`parse_utilization` returns `current` and `avg_3m` separately); Android had collapsed the two and kept the wrong label.

- **Decision:** `None` is preserved end-to-end for missing utilisation rather than defaulting to 0.
- **Rationale:** "RMS knows nothing about this trainer" and "this trainer is idle" are different facts with opposite staffing implications, and team averages that count the former as 0% skew low.

- **Decision:** Implemented `delivery_intelligence_df` in the backend rather than deleting the UI that reads it.
- **Rationale:** The roster card's readiness/capacity/risk branches were written against the offline payload and had been dead since authored. The information is genuinely useful to a manager and the inputs were already fetched, so emitting it costs nothing and recovers UI that was already built and paid for. Thresholds copied from `shared/delivery_intelligence.py` so a trainer reads identically on both products.

- **Decision:** Certification gaps now driven by RMS key 213 (`courseWithoutExam`) across all 438 vendors, while `_CERT_CATALOG` is retained only to *name* the specific Microsoft exams it knows.
- **Rationale:** The hardcoded 30-entry map meant every Cisco, AWS, Oracle, RedHat and SAP course a trainer delivered was invisible to the gap analysis. Key 213 answers "does this course require an exam" for the whole catalogue and is live-verified. Key 215, which would give the specific exam code, returns 403 for our credentials — so vendor-wide gaps are reported honestly as "«Vendor» certification" with no code, rather than either inventing one or continuing to hide the gap.

- **Decision:** Live-probe every RMS API before planning against it, and record the failures as durable findings.
- **Rationale:** The documented schemas in `trainer_portal_api_details/` are null-filled placeholders that describe nothing. Probing found three usable APIs, eight 403s, and one (key 205) that returns *misaligned* data — ".NET MAUI" mapped to the "Salesforce" domain — which is worse than an error because it would have been shipped as fact. The 403 on key 171 (`freeSchedule`) also invalidates the Phase 3 "real availability" plan until access is provisioned.

## 2026-08-08 — v1.31.0: Demand order is RMS order; matching gets skill→readiness→availability→language

- **Decision:** `allocation-desk` no longer re-sorts unallocated batches by match%; the order RMS returns is the order the app shows.
- **Rationale:** The manager's own framing: business priority and arrival order are how demand is actually worked, and a match%-sorted list hid a high-priority, low-coverage batch at the bottom exactly when it most needed attention. Coverage is now a per-card signal (tri-state + risk), not a reordering key.

- **Decision:** Trainer-candidate ranking is (1) skill match, (2) readiness — the Qubits score of the matched course, (3) English-speaking class before non-English, (4) utilisation ascending (more available first), (5) clean 6-month feedback tie-break — with blocked trainers always last regardless of the rest.
- **Rationale:** Direct instruction, in that order. "Readiness" is defined as the matched course's own Qubits score rather than a generic trainer-level number, because that's the one readiness signal tied to the specific course being staffed rather than the trainer's whole catalogue.

- **Decision:** A trainer with no recorded language on their resume is treated as English-capable, not unknown.
- **Rationale:** English is the default working language across the pool; most resumes never bother listing it because it's assumed. Treating "not listed" as "can't speak English" would silently demote every trainer with an incomplete profile below one who happened to type "English: Fluent" — a data-completeness artifact, not a real signal.

- **Decision:** The signed-in manager is added as a matching candidate on every batch, unless they'd somehow already be their own reportee.
- **Rationale:** Direct instruction — managers deliver strategic, premium, escalated or specialized batches themselves, and a matching engine that only ever looks at reportees made the manager invisible as an option.

- **Decision:** `is_priority` = ILT/FMAT delivery mode **and** international location (an India-marker heuristic on the location string — no external country database). `revenue_potential` is a High/Medium/Low band from participants + mode + international, not a fabricated currency figure.
- **Rationale:** No RMS field in this integration carries reliable revenue/fee data — `batch-details`'s `total_fee` fallback is literally hardcoded mock data ("₹ 1,50,000") elsewhere in this codebase, which is exactly the kind of dishonest placeholder this project has been actively removing. A qualitative band from real signals is truthful; a fake number is not, no matter how it's labeled.

- **Decision:** Coverage is shown as a three-state read (Best Match / Available with Upskilling / No Coverage) with an icon, not a raw percentage, as the primary card signal. The percentage is still available as a detail-page stat.
- **Rationale:** Direct instruction — "Instead of simply showing Match %, show: Best Match / Available with Upskilling / No Coverage." A percentage forces a manager to interpret a number under time pressure; three states are a decision, not a reading.

## 2026-08-08 — Release keystore rotated; local `gh release create` was a policy violation

- **Decision:** Generated a new release keystore (`skillsync-release.jks`) and rotated the four CI signing secrets to it, retiring the previously-committed `release.jks`.
- **Rationale:** The old `release.jks` was committed to git in commit `93bde7d` because `.gitignore` had a UTF-16-encoded entry for it that git silently never matched. A keystore that has been on a public GitHub remote must be treated as compromised — reusing it for production signing regardless of whether the password ever leaked would be indefensible.

- **Decision:** Every release from v1.30.0 onward goes through `.github/workflows/android-release.yml` only. Local `assembleRelease`/`assembleDebug` is verification-only and is never attached to a GitHub Release.
- **Rationale:** v1.28.0 and v1.29.0 were built locally with `assembleDebug` and pushed straight to a GitHub Release via `gh release create`, which is exactly what [[feedback_workflow_and_release_policy]] already prohibited — a locally-built APK is debug-signed, has no CI provenance, and cannot update over a CI-signed install. That rule existed before this session; it just wasn't followed for two releases. Fixed by using the actual pipeline going forward.

- **Decision:** `app/build.gradle.kts` gained a `signingConfigs` block that reads `keystore.properties` (git-ignored) if present, and no-ops if absent.
- **Rationale:** Lets a developer machine produce a real release-signed APK for local verification (confirming the signature matches what CI will produce) without ever touching the CI secrets or requiring the properties file to exist in the repo. CI itself doesn't use this block at all — it signs via AGP's `-Pandroid.injected.signing.*` flags, which is untouched by this change.

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

## 2026-08-12 — Skill management can only add; RMS has no remove or update

- **Decision:** The Skill to Select Members to Assign flow (§7.6) ships without Remove Skill and Edit Skill Level. The preview states the limitation and warns that the write cannot be undone.
- **Rationale:** The RMS estate has exactly one skill write, `Add Trainer Skill` (key 255). A search of all 37 portal documents found no remove, delete or update skill endpoint. Shipping the buttons the design asks for would mean shipping controls that silently fail against production data, which is worse than an honest absence. Re-assigning at a different level was also rejected as an "edit": it appends a second record rather than changing the first, and presenting that as an edit would misrepresent what RMS stores.
- **Bulk writes:** fan-out happens server-side at four concurrent writes, not N round trips from the phone, and every row returns its own outcome because partial failure is the normal case.
- **To revisit:** ask the RMS team for a remove/update skill endpoint. Until one exists, a wrong entry has to be corrected by them directly.

## 2026-08-11 — The delivery agent is deterministic, and says so

- **Decision:** Build the agentic layer as tool-based reasoning over a fused RMS fact base, with a weight-learning loop fed by manager accept/dismiss decisions. No language model. Every answer carries evidence and a confidence; unmatched questions are refused with an explanation rather than answered.
- **Rationale:** No LLM provider or credentials exist in this project, and `POST /api/agent/ask` has never been implemented. A fluent agent without grounding would produce plausible delivery data the manager could not distinguish from real readings, and allocation decisions would be made on it. A narrow agent that answers nine intents well and refuses the rest is honest and immediately useful. `Agent.ask` is the seam an LLM slots into later without changing callers.
- **Learning scope:** it learns ranking only, from real decisions, clamped and renormalised so the scale cannot drift. It cannot discover new suggestion kinds. Weights are per device in `LocalCache`; cross-device or org-wide pooling needs a backend table and is not built.

## 2026-08-11 — Trainer 360 Copilot entry point withheld until its route exists

- **Decision:** Remove the ✨ Copilot FAB and chat sheet from `Trainer360Screen.kt`. `CopilotChatSheet`, `CopilotViewModel` and the `agentAsk` Retrofit declaration stay in the tree.
- **Rationale:** The sheet posts to `POST /api/agent/ask`, which `backend.py` has never implemented — a live probe of every endpoint the app declares returned 404 for that one route and 401/405 (correctly gated) for all nineteen others. Every question a manager asked came back as a 404 error bubble. A visible entry point that cannot succeed is worse than no entry point, and it violates the standing rule that nothing ships broken or partially implemented. The 2026-08-05 decision describing a deterministic agentic layer with an `agent.answer` seam was never landed in `backend.py`, so there is no route to repoint the client at.
- **Restoring it:** re-add the `floatingActionButton` block and the `showCopilot` state — a one-line change once the backend route ships.

## 2026-08-03 — Baseline AI/ tracking workspace

- **Decision:** Create `AI/PROGRESS.md`, `AI/CONTEXT.md`, `AI/DECISIONS.md` to give
  future AI sessions a durable, minimal on-ramp.
- **Rationale:** No such tracking existed; PROGRESS.md is the source of truth, CONTEXT.md
  holds stable project knowledge, DECISIONS.md records rationale — keeping history concise
  and AI-agnostic without storing logs or transcripts.

## 2026-08-17 - Tri-state session login observation to prevent cold-start auto-logout

- **Decision:** Change `SessionManager.loginState` to `StateFlow<Boolean?>` (`null` = uninitialized/cold start, `true` = authenticated, `false` = signed out). Update `Navigation.kt` to only navigate to Login when `loginState == false`.
- **Rationale:** Previously, `loginState` was initialized to `false` by default. On Android cold start, Compose renders `Navigation.kt` before `SessionManager.init()` reads disk preferences. This triggered a `LaunchedEffect(loginState)` race condition that immediately replaced the authenticated `Main` destination with `Login`. Tri-state eliminates false logouts while preserving graceful sign-out UX.

## 2026-08-17 - Restore Trainer 360 Copilot FAB with deterministic backend agent

- **Decision:** Implement `POST /api/agent/ask` in `backend.py` with deterministic rule-based evaluation over cached manager and Trainer 360 intelligence, and restore the Copilot FAB on `Trainer360Screen.kt`.
- **Rationale:** The route was previously a 404 gap preventing the on-device Copilot sheet from functioning. Implementing the deterministic backend handler satisfies the delivery copilot contract without requiring external LLM keys or extra network latency.
## 2026-08-30 — Signed sessions require a hashed server-side revocation denylist

- **Decision:** Keep durable HMAC-signed 30-day sessions, but check a process-safe SQLite denylist before accepting either an in-memory or reconstructed token. Logout stores only the bearer token's SHA-256 digest until its natural expiry.
- **Rationale:** Removing a signed token from process memory does not revoke it; signature verification immediately reconstructs it. A shared hashed denylist makes logout effective across workers and process restarts without persisting raw bearer tokens or credentials. Durability across full host replacement still requires `SKILLEDGE_STATE_DIR` on persistent storage.

## 2026-08-30 - Render runs gunicorn with 1 worker / 8 threads / 120s timeout
- **Decision:** Start command `gunicorn backend:app --workers 1 --threads 8 --worker-class gthread --timeout 120` (render.yaml + Procfile), replacing the bare `gunicorn backend:app`.
- **Rationale:** The bare command uses gunicorn's 30s default timeout, which killed the worker mid-request for any cold build over 30s (trainer-360 ~43s, capability ~22s) and took the `_serve_or_warm` background thread down with it — the source of the persistent trainer-360 502. One worker is required for the in-process warm/response caches (`_warm_payload_cache`, `_allocation_payload_cache`) to be shared rather than duplicated per worker; the service is I/O-bound on RMS calls, so 8 threads provide concurrency without the memory cost of extra workers on the free plan.

## 2026-08-30 - No fabricated fallback team; Trainer Index declares partial confidence
- **Decision:** Delete `_build_fallback_manager_intelligence` and the demo roster in `team_capability`; a no-reportee account returns `no_reportees: true` + empty arrays. The Trainer Index passes real zeros for the 13 criteria RMS does not expose (previously Qubits-derived guesses) and returns `confidence: "partial"` with `measured_criteria` / `estimated_criteria`.
- **Rationale:** Both violated the 2026-08-08 "no invented data, ever" decision — a fake 8-trainer team in production for `aishwar_v@…`, and a flagship performance tier computed mostly from a scoring veneer over utilisation + Qubits. An honest empty state and an explicitly-partial tier are correct; a plausible fake is not.

## 2026-08-30 - Server state lives on a Render persistent disk
- **Decision:** `render.yaml` declares a 1 GB disk at `/var/data` and sets `SKILLEDGE_STATE_DIR=/var/data`.
- **Rationale:** The action inbox, the session-revocation denylist and notification seen-state were on Render's ephemeral filesystem and lost on every redeploy — silently breaking the v3.45.1 logout fix and discarding managers' triaged actions. Single-worker gunicorn already assumes one host; a disk makes that state durable.

## 2026-08-31 - Auto-generated team messages go through one deterministic composer
- **Decision:** All manager-to-team text (weekly/monthly, per-reportee and team group) is produced by `_compose_manager_message(scope, cadence, facts, my_message)`, which emits house-style Teams/Viber prose (greeting line, prose body, closing line; no emojis/bullets/hyphens; one bold action; one underlined time reference; <=1000 chars; tone from the data). `standpoint_note`, `team_digest` and the HR-monthly `structured_feedback.message` are all this composer's output. `GET /api/v2/message/compose` exposes it with an optional manager note. Phrasing varies deterministically by a seed of (subject, period).
- **Rationale:** The v3.48.0 rewrite engine only rewrote hand-typed input; the report text itself was still a labelled fact list ("Immediate Focus: ... No action needed") that read as machine output. Managers need something they can paste and send. Kept deterministic (no LLM) per the standing project rule; the composer uses varied sentence templates + the existing `_professional_rephrase` / `_extract_time_refs` helpers. New signal: opportunity cost — open unallocated demand a trainer/team already has the skills for but is not assigned to.

## 2026-08-31 - Manager-view wave 1: opportunity cost, priorities, feedback analytics
- **Decision:** Add three deterministic, data-reusing features. (1) `manager_kpis.opportunity_cost` on the dashboard - open unallocated batches the team can cover but is not on, + trainer-days at stake + by_cause. (2) `GET /api/v2/manager/priorities` - one ranked worklist (unstaffed demand, 1:1s, overload, cert gaps, overdue actions) with severity/due/rank_score, via `_serve_or_warm`. (3) `_feedback_analytics` adds `trend` (monthly rating series), `trend_direction` (last 3 vs prior 3, +/-0.2), and keyword-clustered `themes` to `_trainer_feedback_detail`, surfaced on Trainer 360.
- **Rationale:** The tool was a strong diagnostic that stopped short of telling the manager what to do. These three are the cheapest high-lift steps toward that: they reuse RMS data already fetched, need no new infra, stay deterministic (project rule), and each maps to a real manager decision (cover the pipeline / run my week / coach with evidence). Auto-allocation write-back was explicitly ruled out - Koenig's algorithm owns allocation.

## 2026-08-31 - Manager-view wave 2: This Week screen, eligibility-gap closer, cert intelligence
- **Decision:** (1) A "This Week" screen consuming `GET /api/v2/manager/priorities` - the manager's single ranked worklist. (2) `GET /api/v2/eligibility/batch` + a Batch Detail sheet: per open batch, what blocks each trainer (skill floor / cert / mock / availability / DNC) tagged with the fix the manager may make (`mark_skill` has a live write; `book_exam`/`confirm_availability` are hints for now). Extracted `_evaluate_team_against_batch` so this and `allocation/candidates` share one evaluator. (3) Wired `courseTechnology` (114) + `courseDomain` (205) into `_capability_portfolio` (`by_domain`/`by_technology`, `domain_taxonomy_available` -> true) via a long-cached `_course_taxonomy()`; new `GET /api/v2/capability/cert-intel` ranks exams by open demand they unlock and reports expiry honestly as unavailable.
- **Rationale:** Managers cannot allocate (Koenig's algorithm owns it), so the product's job is to make their trainer the top eligible candidate and to tell them what to do first. These three turn the existing engines' data into the "what now" the tool was missing. All deterministic, all reuse RMS data already fetched. Cert expiry dates are not in RMS - the feature says so rather than fabricating a calendar.

## 2026-09-16 - Plan and Demand Details own separate jobs; person-level matching never lives on Plan
- **Decision:** The Plan / Demand & Planning screen answers "what demand needs planning?" — an unallocated-batch command centre (KPI strip, planning focus insight, filters, expandable batch cards). It must never contain trainer/reportee candidate matching, ranked candidate cards, "Grow the Team", or manager-recommendation banners. Demand Details (`BatchDetailScreen.kt`) answers "who can handle this demand?" and owns all of that — candidate ranking, verified availability, Grow the Team, assignment actions. Plan does not fetch data (e.g. upskilling/Grow-the-Team) that only Demand Details displays.
- **Rationale:** A manager can have dozens of open unallocated batches; doing person-level matching inline on that list makes scrolling effectively unbounded, increases rendering/fetch cost for content most views never need, and mixes two different workflows (triage vs. staffing one batch) into one screen. This boundary is intentional and should not be reversed by a future redesign that tries to "make Plan richer" by pulling matching back in.
## 2026-09-17 — Design V4 Milestone & International Priority Rule

**Decision: Design V4 / Color Admin Admin-Portal UX**
The manager experience has fully transitioned to the Design V4 standard. The design system explicitly enforces density, 4dp/8dp/12dp spacing constraints, non-overlapping glassSurface applications, and distinct primitive usage (tables/list-groups over homogeneous rounded cards).

**Decision: International ILT / FMAT Priority (MANDATORY)**
A strict business rule has been implemented in the Plan Command Centre (AllocationDeskScreen):
- Any demand with is_international == true AND delivery_mode containing ILT or FMAT will **always** render in a dedicated, high-priority zone above all other demand.
- Normal sorting mechanisms (by urgency, date, etc.) operate exclusively within the partitioned zones (Priority vs. Ordinary) and never intertwine them.
- Visuals use a specific Royal -> Azure -> Cyan header sweep and globe vectors to signify strategic importance.
- Truthfulness bounds (unknown readiness, missing capability blockers) remain unconditionally respected inside this priority zone.

## 2026-09-18 - No certification campaign on Plan; no person-matching language on Plan
- **Decision:** `CertificationCampaignCard` is removed and must not return. `CapacitySummary.coveragePct` is demand-coverage percent (coverage_status != "No Coverage"), not certification coverage; the skill-edge API exposes no cert-renewal/expiry fields, so a "CERTIFICATION RENEWAL CAMPAIGN" card heading rendered from it was a mislabel. It is replaced by `TopExposureCard`, which ranks courses by open-batch count from real batch fields only. Related weld: `AllocationFunnelCard` and `RegionalCoverageCard` use coverage wording ("COVERED", "N/N covered") instead of "MATCHED"/"matched" so the Plan screen keeps zero trainer-matching vocabulary, per the 2026-09-16 Plan vs Demand Details boundary decision.
- **Rationale:** The honesty contract (2026-08-08 "no invented data, ever") forbids presenting demand coverage as certification health, and the 2026-09-16 decision confines all person-level matching language to Demand Details. Visual richness on Plan must come from real demand-planning signals (funnel, regional coverage, exposure ranking), not relabelled metrics or matching terms.
