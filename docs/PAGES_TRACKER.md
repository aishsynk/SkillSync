# SkillEdge Android — Page Transformation Tracker

Per-page record for the Phase 0 UI/UX Transformation. Each page is redone one at a time
(foundation → shell → Home first), with all gates green (`compileDebugKotlin`,
`testDebugUnitTest` ≥ 195 passing, `compileDebugAndroidTestKotlin`, `assembleDebug`,
`lintDebug`) and a stop-and-report after each page.

Status legend: **PENDING** → **IN PROGRESS** → **DONE** (tokens + states + gates);
**ADOPTED** = flag from audit (needs restore-against-history before redesign).

| # | Screen | File | Audit flags | Status |
|---|---|---|---|---|
| 1 | Login | `feature/auth/ui/LoginScreen.kt` | none | PENDING |
| 2 | Today / Manager Brief | `feature/home/ManagerCommandCentre.kt` (+`DashboardSections.kt`) | 69 raw hex; 9sp badges; 12 duplicate ExecDeck tiles | **DONE** (tokens + DeckTile + 9sp→labelSmall; **V2 modernisation**: AnimatedCount on Pulse/hero/badge, press feedback unify, shimmer loading; gates green) |
| 3 | People & Capability | `feature/home/TeamTab.kt`, `TeamMemberCard.kt`, `TeamCalendarScreen.kt` | hex/emoji; 9sp; KPI dup with Home | PENDING |
| 4 | Demand & Planning | `feature/training/ui/AllocationDeskScreen.kt` | raw field labels | PENDING |
| 5 | Capability Marketplace | `feature/home/CoursesTab.kt` + curriculum sheet | emoji; jargon | PENDING |
| 6 | Manager Actions | `feature/home/ActionsInbox.kt` | none | PENDING |
| 7 | Search & Command | `feature/home/Version2Workspaces.kt` | emoji prompts; scope tokens | PENDING |
| 8 | Delivery Operations | `feature/home/DeliveryOperationsWorkspace.kt` | in-shell | PENDING |
| 9 | Opportunities (tab) | `feature/home/MainScreen.kt` OPPORTUNITIES branch | **ADOPTED** — no-op filters & actions | PENDING |
| 10 | This Week (Priorities) | `feature/report/ui/PrioritiesScreen.kt` | `onOpenX={}` 4 no-ops | PENDING |
| 11 | Batch Detail | `feature/training/ui/BatchDetailScreen.kt` | hardcoded shape; raw fields; emoji | PENDING |
| 12 | Trainer 360 | `feature/training/ui/Trainer360Screen.kt` | monolith; hardcoded date + literals | PENDING |
| 13 | Trainer Practice | `feature/training/ui/TrainerPracticeScreen.kt` | none major | PENDING |
| 14 | Copilot | `feature/ai/ui/CopilotScreen.kt` | token-pure reference | PENDING |
| 15 | Weekly Report | `feature/report/ui/WeeklyReportScreen.kt` | debug labels; raw hex; no empty state | PENDING |
| 16 | HR Monthly Report | `feature/report/ui/HrMonthlyReportScreen.kt` | hardcoded date, "Raw Value", "Qubits", 10sp | PENDING |
| 17 | Benchmark | `feature/report/ui/BenchmarkScreen.kt` | raw verdicts | PENDING |
| 18 | Accounts | `feature/report/ui/AccountsScreen.kt` | blank-email dead nav; 8×4dp chips | PENDING |
| 19 | Pipeline Radar | `feature/report/ui/PipelineRadarScreen.kt` | `sc_id`/CSM jargon; <48dp chips | PENDING |
| 20 | Delivery Compliance | `feature/report/ui/DeliveryComplianceScreen.kt` | "Assignment #id" jargon | PENDING |
| 21 | Capacity Runway | `feature/report/ui/CapacityRunwayScreen.kt` | 9–10sp labels | PENDING |
| 22 | Ramp | `feature/report/ui/RampScreen.kt` | 9sp; partial clickability | PENDING |
| 23 | Skill Requests | `feature/report/ui/SkillRequestsScreen.kt` | fallback course_id jargon | PENDING |
| 24 | My Schedule | `feature/training/ui/MyScheduleScreen.kt` | `off_bands` raw keys; missing from BackHandler | PENDING |
| 25 | Viber Automation | `feature/viber/ui/ViberAutomationScreen.kt` | console styling; 23 hex; no error/empty | PENDING |
| 26 | Opportunity Guardian | `feature/guardian/ui/OpportunityGuardianScreen.kt` | **ADOPTED** — match never called; noops | PENDING |
| 27 | Opportunity List | `feature/opportunity/ui/OpportunityListScreen.kt` | **ADOPTED** — only detail wired; Color.Red/Yellow | PENDING |
| 28 | Opportunity Detail | `feature/opportunity/ui/OpportunityDetailScreen.kt` | **ADOPTED** — Accept/Decline/Snooze/MarkSeen no-ops | PENDING |
| 29 | Communication Intelligence | `feature/communication/ui/*.kt` | shipped; labels polished only | PENDING |
| 30 | Capability Graph | `feature/capability/ui/CapabilityGraphScreen.kt` | **ADOPTED** — hardcoded "Aishwar"; emoji | PENDING |
| 31 | Skill Profile | `feature/capability/ui/SkillProfileScreen.kt` | raw topic names; block-char bars | PENDING |

## Shell & system (not pages)

- `app/MainActivity.kt` — edge-to-edge, notification permission. No change needed.
- `feature/home/MainScreen.kt` — **DONE**: raw-hex retired in TopAppBar/AppNavBar/ActionsTab;
  dead `if(false)` block + orphaned helpers removed; no version bump. Remaining: `fontSize 9.sp`
  overrides at 1213/1227/1438/1674 (badge labels; next pass).
- `navigation/NavigationKeys.kt` / `Navigation.kt` — **DONE**: `MySchedule` added to BackHandler
  (20 pushed screens now covered).
- `theme/*.kt` — token source. Aurora plum/brass open decision documented in DESIGN_SYSTEM.md.

## Follow-up (queued)

- `DashboardSections.kt` — orphaned legacy composables now unreferenced (ProfileHeader,
  CommandHero, TeamReadinessSummaryCard, TeamRiskSummaryCard, TeamCapacityAlertCard, TeamAnalytics,
  TeamCapacityForecastCard) → delete in dedicated dead-code sweep (~900 lines, not rendered).
- Remaining 9sp/pdf-hex: TeamTab, TeamCalendarScreen (26 hex + emoji), TeamMemberCard (13 hex),
  NotificationCenter, ActionsInbox, Version2Workspaces, CourseCurriculumSheet.

## Per-page checklist (run every page)

1. Inspect: read the file end-to-end; purpose, data source.
2. Swap raw `Color(0x…)` / emoji / 9–10sp / off-token `colorScheme.primary` → tokens + design
   components; keep values and spacing identical unless a state needs real content.
3. Implement every missing state (LOADING / CONTENT / EMPTY / ERROR / OFFLINE) with real data.
4. Gates: compileDebugKotlin → testDebugUnitTest (≥195) → compileDebugAndroidTestKotlin →
   assembleDebug → lintDebug.
5. Small-screen check + a11y (48dp, contentDescription, no <11sp).
6. Document: update this tracker + PROGRESS.md; before/after summary to the user; stop.
7. CRLF discipline: never stage pure line-ending changes; commit only the page diff.