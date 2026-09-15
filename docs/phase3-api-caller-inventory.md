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
| `feature/communication/domain/CommunicationRepository.kt` | REPOSITORY | Communication domain (Phase 1) |
| `core/notification/MonitoringPass.kt` | OTHER (background poller) | Not yet classified in detail — flagged for the next inventory pass, not a UI-layer violation |
| `core/storage/ActionQueueManager.kt` | OTHER (offline-queue sync) | Same — not yet detail-classified, not a UI-layer violation |

## Confirmed violations (still open)

| File | Classification | Domain | Status |
|---|---|---|---|
| `feature/auth/ui/LoginViewModel.kt` | VIEWMODEL | Auth | **Migrated this pass** → `AuthRepository` |
| `feature/home/ActionsViewModel.kt` | VIEWMODEL | Actions/Priorities | Open |
| `feature/home/CourseCurriculumSheet.kt` | SCREEN/COMPOSABLE | Course/Curriculum | Open |
| `feature/home/GrowTeamCard.kt` | SCREEN/COMPOSABLE | Trainer (share flow) | Open |
| `feature/home/MainScreen.kt` | SCREEN/COMPOSABLE | Today/dashboard | Open |
| `feature/home/MainScreenViewModel.kt` | VIEWMODEL | Today/dashboard (KPI/trainer) | Open |
| `feature/home/Version2Workspaces.kt` | SCREEN/COMPOSABLE | Unclear — flagged as possibly-dead in the Phase 0 assessment; confirm before migrating | Open |
| `feature/training/ui/AllocationViewModel.kt` | VIEWMODEL | Batch/allocation | Open (spans 3 domains — see notes below) |
| `feature/training/ui/CopilotViewModel.kt` | VIEWMODEL | AI/Copilot | Open |
| `feature/training/ui/EligibilitySheet.kt` | SCREEN/COMPOSABLE | Certification/eligibility | Open |
| `feature/training/ui/NetworkStaffingSheet.kt` | SCREEN/COMPOSABLE | Batch/staffing | Open |
| `feature/training/ui/Trainer360ViewModel.kt` | VIEWMODEL | Trainer | Open |
| `feature/training/ui/TrainerPracticeScreen.kt` | SCREEN/COMPOSABLE | Trainer/capability | Open |

**Already resolved by Phase 1/2** (no longer in this list — confirmed by this re-scan, not
assumed): `WeeklyReportScreen.kt`, `HrMonthlyReportScreen.kt`, `PrioritiesViewModel.kt`,
`WeeklyReportViewModel.kt`, `HrMonthlyReportViewModel.kt`.

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
| `feature/training/ui/BatchDetailScreen.kt` (direct `RetrofitClient.instance.getBatchMessage`) | `feature/training/ui/BatchDetailScreen.kt` (via repository, no ViewModel — documented exception below) | Batch/delivery | `core/data/BatchRepository.kt` | None — stateless Composable, see exception note | `GET api/data/batch-message` (`getBatchMessage`) | None — ephemeral `serverMsg` Compose state, unchanged; falls back to `BatchShare` local composition on failure | None yet (pending — see Pending below) | Phase 3 increment 2 (this commit) |

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
| `feature/home/ActionsViewModel.kt` | VIEWMODEL | Actions/Priorities | Open |
| `feature/home/CourseCurriculumSheet.kt` | SCREEN/COMPOSABLE | Course/Curriculum | Open |
| `feature/home/GrowTeamCard.kt` | SCREEN/COMPOSABLE | Trainer (share flow) | Open |
| `feature/home/MainScreen.kt` | SCREEN/COMPOSABLE | Today/dashboard | Open (deferred until underlying repositories exist) |
| `feature/home/MainScreenViewModel.kt` | VIEWMODEL | Today/dashboard (KPI/trainer) | Open (deferred until underlying repositories exist) |
| `feature/home/Version2Workspaces.kt` | SCREEN/COMPOSABLE | Unclear — flagged as possibly-dead; confirm before migrating | Open |
| `feature/training/ui/AllocationViewModel.kt` | VIEWMODEL | Batch/demand, Trainer/candidate, Trainer-skill-write (3 domains) | Open |
| `feature/training/ui/CopilotViewModel.kt` | VIEWMODEL | AI/Copilot | Open |
| `feature/training/ui/EligibilitySheet.kt` | SCREEN/COMPOSABLE | Certification/eligibility (`getBatchEligibility`) — cross-domain, backend-authoritative; candidate for its own `EligibilityRepository` | Open |
| `feature/training/ui/NetworkStaffingSheet.kt` | SCREEN/COMPOSABLE | Trainer/staffing (`getNetworkTrainers`) | Open |
| `feature/training/ui/Trainer360ViewModel.kt` | VIEWMODEL | Trainer | Open |
| `feature/training/ui/TrainerPracticeScreen.kt` | SCREEN/COMPOSABLE | Trainer/capability (`trainerFeedbackLog`, `trainerRecordings`) | Open |

Next increment: Eligibility (`EligibilitySheet.kt` → new `EligibilityRepository`), then the
Trainer/Staffing cluster (`NetworkStaffingSheet.kt` + `TrainerPracticeScreen.kt` +
`AllocationViewModel`'s trainer-domain calls → a `TrainerRepository`, name TBD after further
investigation).
