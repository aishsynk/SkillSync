# SkillSync Architecture Audit — 2026-09-13

Audit only. No code changed. Awaiting explicit approval before any migration work starts.

## 1. Current architecture audit

**Backend:** `backend.py` is a single 15,077-line Flask file with 110 `@app.route` registrations.
Sampled handlers (`unified_intelligence` ~490 lines, `trainer_360` ~600 lines, `allocation_desk`
~185 lines) each mix, in one function: session/auth resolution, cache-key construction, RMS I/O,
inline scoring/business logic, and response-JSON assembly. ~80+ private `_`-prefixed helper
functions (`_risk_score`, `_rank_batch`, `_suitability_components`, `_cert_intelligence`, etc.)
give partial decomposition, but nothing enforces a route → service → repository boundary — a
handler can and does call RMS directly, score inline, and format the response, all in one place.

One exception already exists: a proper layered slice for **communication/opportunity** —
`domain/communication/models.py`, `repositories/{communication_store,opportunity_store}.py`,
`services/communication/{composer,context_selector,intent,policy,service,validator}.py`. This is
the one part of the codebase that already looks like the target architecture. It is not used by
backend.py's other ~100 routes.

**Android:** Compose UI. `sealed class ... State` is already used (14 occurrences) for UI state.
No `domain/` package, no `data/repository` package, no `Repository` interface (zero hits), no
usecase/interactor naming convention. No DI framework — `build.gradle.kts` has no Hilt/Koin/Dagger;
a comment in `core/sync/SyncWorker.kt` explicitly records that DI was considered and rejected
("using Hilt/Dagger... we initialize directly").

## 2. Dependency / coupling problems

- **UI bound directly to raw maps, not domain models.** ~300 occurrences of `Map<*, *>` /
  `Map<String, Any>` inside `feature/*/ui` files across the three largest features alone — home
  (13 files/104 hits), training (17/104), report (13/60). Composables read fields via
  `it.str("month")`, `it.int("utilization")`-style lookups against JSON-shaped maps. A backend
  field rename is a silent runtime break, not a compile error.
- **Business-rule shaping logic lives inside Composable screens, not ViewModels.** `.take(3)` and
  similar shaping appears directly in `PipelineRadarScreen.kt`, `AllocationDeskScreen.kt`,
  `ReadinessSection.kt`, and three places in `Trainer360Screen.kt` (2565 lines) — only one such
  hit was inside an actual ViewModel (`HrMonthlyReportViewModel.kt`). Numeric threshold logic
  (e.g. utilisation cutoffs) is not present client-side at all — it's presumably embedded in
  backend.py's scoring helpers, which is its own coupling problem (§1) but at least keeps the
  client thin for *that* class of rule.
- **Cross-feature coupling is, encouragingly, already low.** Zero `feature.*` → `feature.*`
  imports found between `home`, `training`, and `report`. Shared code already flows through
  `core/` and `theme/`. This means feature isolation (constraint #9) is largely already true at
  the import level — the coupling problem is UI↔data-shape, not feature↔feature.
- **`core/network/SkillEdgeApi.kt` (771 lines) and `core/network/ApiModels.kt` (508 lines)** are
  the closest thing to a data-access layer today, but they sit directly under `core`, are called
  straight from ViewModels/Composables, and return the same map-shaped payloads rather than
  mapped domain types — there is no repository interface between them and their callers.

## 3. Oversized-file report

| Lines | File | Concern |
|---|---|---|
| 15,077 | `backend.py` | Monolith; 110 routes, all layers co-located |
| 2,565 | `feature/training/ui/Trainer360Screen.kt` | Single Composable file >5x the flag threshold |
| 1,711 | `feature/home/MainScreen.kt` | Nav shell + DashboardTab + ManagerCommandCentre wiring in one file |
| 1,190 | `feature/training/ui/AllocationDeskScreen.kt` | Screen + inline candidate-shaping logic |
| 1,178 | `feature/home/TeamCalendarScreen.kt` | |
| 1,074 | `feature/report/ui/WeeklyReportScreen.kt` | |
| 1,043 | `feature/training/ui/BatchDetailScreen.kt` | |
| 963 | `feature/report/ui/HrMonthlyReportScreen.kt` | |
| 903 | `feature/home/ActionsInbox.kt` | |
| 877 | `feature/home/TeamTab.kt` | |
| 843 | `feature/home/CoursesTab.kt` | |

11 files exceed 500 lines, 6 exceed 800. `Trainer360Screen.kt` and `MainScreen.kt` are the two
most urgent splits on the Android side; `backend.py` is the dominant one overall.

## 4. Proposed module/folder hierarchy

Adopt the structure already specified in the request, added incrementally alongside the existing
tree (not a big-bang move):

```
app/            navigation, startup, DI wiring
core/           ui, design-system, networking, database, auth, notifications, analytics, common, testing
domain/         models, repositories (interfaces), usecases, policies
data/           remote, local, dto, mapper, repository-impl
features/       today, plan, people, trainer360, delivery, courses, opportunities, actions, calendar, tasks, reports, communication, settings
```
Backend mirrors this as `backend/{api, application, domain, repositories, persistence, policies, tests}`,
built up around `backend.py` rather than replacing it on day one — routes get thinned into `api/`,
their extracted logic moves into `application/` (use cases) and `domain/` (scoring/policy), leaving
`backend.py` as a thin route registry once each route is migrated.

The existing `services/communication/*` + `repositories/*_store.py` + `domain/communication/*`
slice is the template — it already matches this target shape. Reuse its layering conventions
rather than inventing new ones for the other 12 features.

## 5. Domain boundaries

Thirteen feature boundaries, matching the nav map already agreed in the Design V2 work: Today,
Plan, People, Trainer360, Delivery, Courses, Opportunities, Actions, Calendar, Tasks, Reports,
Communication, Settings. Each owns its own `domain/models`, `usecases`, and consumes shared
`domain/repositories` interfaces (a repository is a domain boundary, not a feature boundary —
`TrainerRepository` is used by People, Trainer360, Delivery *and* Opportunities, so it lives in
`domain/repositories`, not inside one feature).

## 6. Repository interfaces (initial set)

`TrainerRepository`, `DemandRepository`, `DeliveryRepository`, `OpportunityRepository`,
`CommunicationRepository`, `CalendarRepository` (backs both Calendar and My Schedule —
`_personal_calendar_build`), `CourseRepository`, `ActionRepository`, `CapabilityRepository`
(skills/certifications — currently spread across `capability` payload + `capKpis`).
Each gets exactly one `data/repository-impl` backed by `SkillEdgeApi`/Retrofit today; a local-cache
implementation can be added later without touching use cases, since callers depend on the
interface.

## 7. Use-case map (Today, as the pilot feature)

- `GetManagerTodayBriefUseCase` → assembles `ManagerTodayBrief { attentionItems, operations, watchlist, upcoming, capacityBalance, summary }`
- `GetAttentionItemsUseCase` → unallocated demand + pending skill requests + escalations, severity-ranked (replaces the `.take(3)` + inline severity logic currently in the Composable)
- `GetActiveDeliveriesUseCase` → today's active batches
- `GetTrainerCapacityUseCase` → bench/optimal/stretched split
- `GetPendingApprovalsUseCase` → open actions queue count + top items
- `GetOwnScheduleSummaryUseCase` → wraps `_personal_calendar_build`'s Android counterpart for the "Your schedule" entry point added this session, so Today can eventually show "you're delivering today" without ManagerCommandCentre reaching into raw calendar data itself

`ManagerCommandCentre.kt`'s current inline logic (unallocated-demand filtering, active-batch
filtering, capacity totals) is exactly the code that moves into these use cases — the Composable
already receives clean-ish inputs (`kpis`, `demand`, `batches` maps) but does the shaping itself;
this audit confirms Today is a representative, tractable pilot.

## 8. Refactoring priorities

1. `backend.py` — highest risk, highest payoff, but also highest blast radius (110 routes); must
   be incremental, one route/domain-slice at a time, starting from routes Today already depends on.
2. `Trainer360Screen.kt` (2565 lines) — worst single UI file; also the most reused screen (trainer
   view, manager's own profile, Trainer 360 from People) so a domain-model boundary here pays off
   across three call sites at once.
3. `MainScreen.kt` (1711 lines) — nav shell, DashboardTab, and screen wiring should split into
   `app/navigation` + a thinner `features/today` entry point.
4. The ~300 raw-map occurrences in home/training/report — introduce DTOs + mappers feature by
   feature, starting with Today (smallest surface, already partially audited this session).
5. `AllocationDeskScreen.kt`, `BatchDetailScreen.kt` — candidate-shaping/business logic extraction
   into `MatchTrainerForRequirementUseCase` + strategy objects, once `TrainerRepository` exists.

## 9. Migration sequence

Exactly the 12-feature order given, with one addition: **Today is already the most-touched
screen this quarter** (three redesign passes), so use it as the architecture pilot before
touching Plan/People — validate the use-case/repository pattern once, cheaply, before repeating
it 11 more times. Sequence: Today → Trainer360 (highest file-size payoff + reused across
People/Delivery/own-profile) → People → Plan → Delivery → Courses → Opportunities → Actions →
Calendar → Tasks → Reports → Communication (already closest to target shape — mostly a rename/
consolidation into the shared `domain/`+`data/` tree, not a rebuild).

## 10. Testing strategy

- **Backend:** 41 existing Python test files already exercise route/business logic directly
  against `backend.py` — real coverage exists despite the missing service layer. As each route is
  split into `application/`+`domain/`, re-point or duplicate the matching tests to call the new
  use case/domain function directly (fast, no Flask test client needed), keeping the existing
  route-level test as a thinner integration/contract check.
- **Android:** 21 unit test files exist, split between business-logic tests (communication,
  Viber automation, guardian) and one large UI-rendering test (`ScreenRenderTest.kt`, 1046 lines,
  currently carrying 12 pre-existing failures per the last session's handover — unrelated to this
  audit but should be resolved as part of, not despite, the Today migration, since `ScreenRenderTest`
  asserts on exactly the content a `GetManagerTodayBriefUseCase` should produce). New: one unit
  test per use case (pure functions/classes, no Compose, no Robolectric) — this is the coverage
  category that doesn't exist today and is cheapest to add once logic moves out of Composables.
  1 instrumented test file only (`MainScreenTest.kt`, 27 lines) — thin; not a priority to expand
  until the domain layer exists to test against.

## 11. Risks

- **`backend.py` at 15k lines has no per-route ownership boundary today** — extracting one route's
  logic risks missing a shared `_helper` another route also depends on. Mitigate by grepping every
  extracted helper's call sites before moving it, not assuming single-use.
- **~300 raw-map UI call sites mean a mapper-introduction pass touches a lot of files for
  Today/Trainer360/Reports** — real regression risk if done as one large PR. Mitigate: one
  feature at a time, as specified, with `ScreenRenderTest`-style checks before/after each.
  Trainer 360 in particular is called from three places (People, Delivery drill-in, own profile
  via "My Profile") — a domain-model change there has a wider blast radius than its own feature.
- **No DI framework exists, and one was previously, deliberately rejected** (see
  `SyncWorker.kt` comment). Introducing Hilt/Koin now is itself an architecture decision, not a
  neutral scaffolding step — needs an explicit yes, not an assumption, per the "do not add
  unnecessary frameworks" constraint. Manual constructor injection is viable without one for a
  13-feature app this size.
- **Two other project trees exist at the repo root** (`SkillEdge_Local/`, `Qubits/`) that were not
  deep-dived — before migrating anything, confirm neither is a second live deployment that would
  also need the same repository interfaces, or scope creeps silently.
- **Scratch/dead files present**: `feature/home/Version2Workspaces.kt` (561 lines, name suggests
  superseded), `SkillEdge_Android/fix_tests.py` and sibling `fix*.py`/`*.ps1` scripts at repo root
  (already flagged in the prior session's handover, still uncommitted/untracked) — should be
  resolved (deleted or intentionally kept) before or during migration so they aren't mistaken for
  live code.

## 12. Files to split / move / delete / retain

- **Split:** `backend.py` (route-by-route, per migration sequence), `Trainer360Screen.kt`,
  `MainScreen.kt`, `AllocationDeskScreen.kt`, `BatchDetailScreen.kt`.
- **Move (no behaviour change):** `services/communication/*`, `repositories/*_store.py`,
  `domain/communication/*` — already correctly shaped, just needs folding into the unified
  `backend/{application,domain,repositories}` tree so it's the template, not an outlier.
- **Delete or explicitly retain-with-reason:** `feature/home/Version2Workspaces.kt` (confirm
  still referenced before deciding), `SkillEdge_Android/fix*.py`, repo-root `fix*.ps1` /
  `get_apk_info.ps1` / `update_mcc.py` scratch scripts, `.claude/worktrees/agent-*` stale
  directories (repo hygiene, not architecture, but noise during migration).
- **Retain as-is for now:** everything under `core/` and `theme/` — already the shared layer the
  feature folders correctly depend on; no evidence of misplaced business logic there from this
  pass.

---

**Awaiting approval to begin migration, starting with Today per §9. No files have been changed.**
