# Phase 3 — Current Direct API Caller Inventory

Re-scanned against the current tree (post Phase 1/2) per the operator's instruction not
to assume the Phase 0 list is still exact. Confirmed by `grep -rln "RetrofitClient.instance"`
across `SkillEdge_Android/app/src/main/java/com/example/skillsync`.

## Legitimate holders (repository/data-source layer — not violations)

| File | Classification | Notes |
|---|---|---|
| `core/data/DataRepository.kt` (`ManagerRepository`) | REPOSITORY | Primary manager/team-intelligence repository |
| `core/data/ScheduleRepository.kt` | REPOSITORY | Personal schedule domain |
| `core/data/SkillRequestsRepository.kt` | REPOSITORY | Skill-request-approval domain |
| `core/data/AuthRepository.kt` (new, this pass) | REPOSITORY | Sign-in/authentication domain |
| `core/data/BatchRepository.kt` (new, this pass) | REPOSITORY | Batch/delivery domain (broadcast message text) |
| `core/data/EligibilityRepository.kt` (new, this pass) | REPOSITORY | Certification/eligibility domain |
| `core/data/TrainerRepository.kt` (new, this pass) | REPOSITORY | Trainer domain (practice record + wider network + skill writes) |
| `core/data/AllocationRepository.kt` (new, review correction) | REPOSITORY | Allocation-recommendation domain — split out of `TrainerRepository` on review, matching `EligibilityRepository`'s precedent |
| `feature/communication/domain/CommunicationRepository.kt` | REPOSITORY | Communication domain (Phase 1) |
| `core/notification/MonitoringPass.kt` | OTHER (background poller) | Not yet classified in detail — flagged for the next inventory pass, not a UI-layer violation |
| `core/storage/ActionQueueManager.kt` | OTHER (offline-queue sync) | Same — not yet detail-classified, not a UI-layer violation |

**Already resolved by Phase 1/2** (no longer in this list — confirmed by this re-scan, not
assumed): `WeeklyReportScreen.kt`, `HrMonthlyReportScreen.kt`, `PrioritiesViewModel.kt`,
`WeeklyReportViewModel.kt`, `HrMonthlyReportViewModel.kt`.

Superseded by the living architecture map and "Remaining violations" table below — this
static snapshot was accurate only as of the first Phase 3 re-scan and is not updated
per-increment; treat the sections below as current.

## `GeneratedApiService.kt` — role check

Live consumer: `core/sync/SyncWorker.kt` only. Declares RMS `Kites/Operator/common`
passthrough calls (`addTrainerSkillIdp`, `assignmentApi`, etc.) — a distinct, RMS-specific
transport surface from `SkillEdgeApi`, not a competing general API. Kept separate per the
operator's instruction ("if it legitimately targets a different backend/system, keep the
boundary explicit"); not merged or deleted.

## Living architecture map (per-increment migration record)

| OLD PATH | NEW PATH | DOMAIN OWNER | REPOSITORY | VIEWMODEL/USE CASE | API ENDPOINT | CACHE/PERSISTENCE | TEST COVERAGE | CI COMMIT |
|---|---|---|---|---|---|---|---|---|
| `feature/auth/ui/LoginViewModel.kt` (direct `RetrofitClient.instance`) | `feature/auth/ui/LoginViewModel.kt` (via repository) | Auth | `core/data/AuthRepository.kt` | `LoginViewModel` (no use case — single-step calls, no orchestration) | `authCheck`, `login`, `setPassword` | None (session state via `SessionManager`, unchanged) | `LoginViewModelTest.kt` (3 tests) | Phase 3 increment 1 |
| `feature/training/ui/BatchDetailScreen.kt` (direct `RetrofitClient.instance.getBatchMessage`) | `feature/training/ui/BatchDetailScreen.kt` (via repository, no ViewModel — documented exception below) | Batch/delivery | `core/data/BatchRepository.kt` | None — stateless Composable, see exception note | `GET api/data/batch-message` (`getBatchMessage`) | None — ephemeral `serverMsg` Compose state, unchanged; falls back to `BatchShare` local composition on failure | None yet (pending — see Pending below) | Phase 3 increment 2 |
| `feature/training/ui/EligibilitySheet.kt` (direct `RetrofitClient.instance.getBatchEligibility`, 2 call sites) | `feature/training/ui/EligibilitySheet.kt` (via repository, no ViewModel — same documented exception) | Certification/eligibility | `core/data/EligibilityRepository.kt` | None — stateless Composable driven by `LaunchedEffect` | `GET api/v2/eligibility/batch` (`getBatchEligibility`) | None — local `data`/`loading`/`failed` Compose state, unchanged; retry-loop and stale-view-on-error behavior unchanged | None yet (same rationale as BatchRepository) | Phase 3 increment 3 |
| `feature/training/ui/TrainerPracticeScreen.kt`'s `TrainerPracticeViewModel` (direct `RetrofitClient.instance.trainerFeedbackLog`/`trainerRecordings`) | Same file, `TrainerPracticeViewModel(repository: TrainerRepository = TrainerRepository())` | Trainer | `core/data/TrainerRepository.kt` | `TrainerPracticeViewModel` (existing ViewModel, no use case needed — two independent reads, no orchestration) | `GET api/v2/trainer/feedback-log`/`trainerFeedbackLog`, `GET api/v2/trainer/recordings`/`trainerRecordings` | None — `MutableStateFlow` UI state, unchanged | `TrainerPracticeViewModelTest.kt` (1 test, both calls) | Phase 3 increment 4 (this commit) |
| `feature/training/ui/NetworkStaffingSheet.kt` (direct `RetrofitClient.instance.getNetworkTrainers`) | Same file, via repository, no ViewModel — same documented exception as BatchDetailScreen | Trainer/staffing | `core/data/TrainerRepository.kt` | None — stateless Composable driven by `LaunchedEffect` | `GET api/v2/network/trainers`/`getNetworkTrainers` | None — local `networkData`/`loading` Compose state, unchanged | None yet (same rationale as BatchRepository) | Phase 3 increment 4 |
| `feature/training/ui/Trainer360ViewModel.kt` (direct `RetrofitClient.instance` for `getTrainerSentiment`, `getTrainerIndex`, `endorseSkill`, `getTrainerReadiness`) | Same file, via `TrainerRepository` (ViewModel already existed, already partly used `ManagerRepository` for other calls) | Trainer | `core/data/TrainerRepository.kt` | `Trainer360ViewModel` (existing ViewModel, gains a second repository param alongside `ManagerRepository`) | `GET api/v2/trainer/sentiment`, `GET api/v2/trainer/trainer-index`, `POST api/v2/skills/endorse`, `GET api/v2/trainer/readiness` | None new — existing `MutableStateFlow` UI state unchanged; `RetrofitClient.isNetworkAvailable(context)` intentionally left as-is (a connectivity check, not a domain data call) | `Trainer360ViewModelTest.kt` (3 tests: sentiment, readiness, endorseSkill) | Phase 3 increment 5 |
| `feature/training/ui/AllocationViewModel.kt` (direct `RetrofitClient.instance` for `getAllocationCandidates`) | Same file, via new `AllocationRepository` (corrected from an initial, inconsistent placement in `TrainerRepository` — see review note below) | Allocation-recommendation (cross-cutting: trainer + availability + certification + travel for one batch) | `core/data/AllocationRepository.kt` | `AllocationViewModel` | `GET api/v2/allocation/candidates` | None new — existing `gatedCandidates` `MutableStateFlow` unchanged | `AllocationViewModelTest.kt` | Phase 3 increment 6, corrected same day (review) |
| `feature/training/ui/AllocationViewModel.kt` (direct `RetrofitClient.instance` for `getAlternativeTrainers`, `bulkAssignSkill`) | Same file, via `TrainerRepository` (ViewModel already used `ManagerRepository`, gains repository params) | Trainer lookup (read) + Trainer-skill-write (bulk assignment) | `core/data/TrainerRepository.kt` | `AllocationViewModel` | `GET api/data/alternative-trainers`, `POST api/v2/skills/bulk-assign` | None new — existing `MutableStateFlow` UI state (`globalSearchData`, `bulkResults`) unchanged | `AllocationViewModelTest.kt` | Phase 3 increment 6 |
| `feature/training/ui/AllocationViewModel.kt` (direct `RetrofitClient.instance.getDemandContext`) | Same file, via `BatchRepository` | Batch/demand | `core/data/BatchRepository.kt` | `AllocationViewModel` | `GET api/v2/operations/demand-context` | None new — existing `demandContext` `MutableStateFlow` unchanged | `AllocationViewModelTest.kt` (1 test) | Phase 3 increment 6 |
| `feature/home/GrowTeamCard.kt` (direct `RetrofitClient.instance.getUpskillMessage`) | Same file, via repository, no ViewModel — same documented exception as BatchDetailScreen | Trainer (server-composed upskill ask for one trainer) | `core/data/TrainerRepository.kt` | None — stateless Composable, `askText`/`askFor` local state | `GET api/data/upskill-message` (`getUpskillMessage`) | None — local dialog state, unchanged; falls back to a local plain-text ask on failure, unchanged | None yet (same rationale as BatchRepository — no ViewModel seam) | Phase 3 increment 7 |
| `feature/home/ActionsViewModel.kt` (direct `RetrofitClient.instance` for `setActionState`, `addActionNote`, `raiseAction`) | Same file, via `ManagerRepository` (already owned the read side, `actions()`) | Actions/Priorities (writes to the same inbox `ManagerRepository` already reads) | `core/data/DataRepository.kt` (`ManagerRepository`) | `ActionsViewModel` (existing ViewModel, no use case — three independent writes, no orchestration) | `POST api/v2/actions/{id}/state`, `POST api/v2/actions/{id}/note`, `POST api/v2/actions` | None new — existing optimistic-update/rollback `MutableStateFlow` logic unchanged | None yet — `ManagerRepository` is not `open`/subclassable, matching the existing convention (no ViewModel in this codebase fakes it; confirmed by grep, not assumed) | Phase 3 increment 8 |

### Documented exception: `BatchDetailScreen.kt` has no ViewModel

`BatchDetailScreen` is a large, stateless, parameter-driven `@Composable` (`batch: Map<String, Any>`
passed in) with no existing ViewModel. Introducing one solely to hold a single repository
reference would be a much larger structural change than this increment's scope (the target
chain calls for a ViewModel/UseCase layer where there is real orchestration — there is none
here: one lazy fetch, one map read, one fallback). The Composable now calls
`BatchRepository` directly (`val batchRepository = remember { BatchRepository() }`), which
still removes the direct `RetrofitClient.instance` transport violation and gets the call
behind a domain-owned, testable seam. Revisit if/when this screen is restructured for other
reasons.

**Pending for this increment:** a focused test for `BatchRepository`/the migrated call path
was not added — Composable-level testing here would require a Compose UI test harness this
repo doesn't yet use for this screen; a plain unit test on `BatchRepository` itself (fake
`SkillEdgeApi` via `apiProvider`) is straightforward and should be added as a fast follow
before this increment is considered fully closed.

## Not done this increment (Batch — increment 2)

The rest of the Batch/allocation cluster in `AllocationViewModel.kt` was investigated but
**not migrated** in this pass — its four call sites span three different domains, not one:

- `getAllocationCandidates(manager, course, start, end, country, customer)` — Trainer/candidate domain
- `getDemandContext(manager, demandId, courseName)` — Batch/demand domain (candidate for `BatchRepository`)
- `getAlternativeTrainers(course)` — Trainer domain
- `bulkAssignSkill(BulkAssignRequest(...))` — a WRITE/mutation, likely Capability or Trainer-skill-write domain, needs separate handling from the read-only calls above

Splitting `AllocationViewModel` across repositories by endpoint domain is planned as its own
increment, not bundled into this one.

## Remaining violations (still open, post this increment)

| File | Classification | Domain | Status |
|---|---|---|---|
| `feature/home/CourseCurriculumSheet.kt` | SCREEN/COMPOSABLE | Course/Curriculum | Open |
| `feature/home/MainScreen.kt` | SCREEN/COMPOSABLE | Today/dashboard | Open (deferred until underlying repositories exist) |
| `feature/home/MainScreenViewModel.kt` | VIEWMODEL | Today/dashboard (KPI/trainer) | Open (deferred until underlying repositories exist) |
| `feature/home/Version2Workspaces.kt` | SCREEN/COMPOSABLE | Unclear — flagged as possibly-dead; confirm before migrating | Open |
| `feature/training/ui/CopilotViewModel.kt` | VIEWMODEL | AI/Copilot | Open |

Next increment:
`CourseCurriculumSheet.kt`, `CopilotViewModel.kt`, `Version2Workspaces.kt` (confirm liveness
first). Today/dashboard
(`MainScreen`/`MainScreenViewModel`) stays last, as an orchestrator once its underlying
repositories exist.
