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
| `feature/training/ui/AllocationViewModel.kt` | VIEWMODEL | Batch/allocation | Open |
| `feature/training/ui/BatchDetailScreen.kt` | SCREEN/COMPOSABLE | Batch | Open |
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

## This increment

**Migrated:** `LoginViewModel` → new `AuthRepository` (`core/data/AuthRepository.kt`),
following the exact `ScheduleRepository`/`SkillRequestsRepository` convention (`apiProvider`
constructor default, `open suspend fun` per call so a test can fake it directly). All three
call sites (`authCheck`, `login`, `setPassword`) moved; error handling in the ViewModel is
byte-for-byte unchanged (the repository only relocates the Retrofit call, not the
try/catch logic). New `LoginViewModelTest.kt` (3 tests) confirms each sign-in step now goes
through the repository, following the established `MyScheduleViewModelTest` fake-repository
pattern.

**Not done this increment** (explicitly not one giant commit, per instruction): the
remaining 13 violations above. Next increments should group by domain (e.g. Trainer:
`Trainer360ViewModel`+`TrainerPracticeScreen`+`GrowTeamCard`; Batch:
`AllocationViewModel`+`BatchDetailScreen`+`EligibilitySheet`+`NetworkStaffingSheet`;
Today/dashboard: `MainScreen`+`MainScreenViewModel`) once each domain's existing repository
ownership (or lack of one) is confirmed, per the migration rules.
