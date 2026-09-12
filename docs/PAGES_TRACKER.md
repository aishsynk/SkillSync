# SkillSync — Product Redesign & Workflow Transformation Tracker

Master progress record for the full-product transformation across all **31 user-visible screens**, **12 inner/modal surfaces**, **6 core workflows**, and **9 communication surfaces**.

Baseline Release: **SkillSync Release v3.80.1.176** (Build 176)

### Lifecycle State Vocabulary
- `NOT AUDITED` — Screen/workflow not yet evaluated.
- `AUDITED` — Information architecture, state models, and disclosure risks mapped.
- `FOUNDATION READY` — Global tokens, shell, and shared component primitives established.
- `PARENT PASS` — Top-level tab/workspace visually modernized; inner workflows pending.
- `INNER FLOW IN PROGRESS` — Detail screens, dialogs, sheets, and actions actively being modernized.
- `WORKFLOW COMPLETE` — Entire parent-to-inner journey, actions, and communication modernized.
- `VERIFIED` — All 5 Android build gates green + backend test suite passing.
- `RELEASED` — Shipped in an approved production build.

---

## WAVE 1 — Product Design Foundation & Application Shell (ACTIVE)
| Component | Scope / Path | State | Verification / Notes |
|---|---|---|---|
| Design Tokens & Color Ramp | `theme/Color.kt`, `theme/Surfaces.kt` | **FOUNDATION READY** | Retired legacy plum/brass in Aurora & glass; deep slate + quiet blue/cyan sheen |
| Shared Component System | `theme/SkillSyncComponents.kt` | **FOUNDATION READY** | Primitives for TopBar, Header, Card, Metric, Chip, Button, Input, List, States |
| Application Shell | `feature/home/MainScreen.kt` | **FOUNDATION READY** | 11sp navbar typography, 48dp touch targets, notification bell action wired |
| Global Operational States | `theme/SkillSyncComponents.kt` | **FOUNDATION READY** | Loading (shimmer), Empty (actionable), Error (retry), Offline banner, Info banner |
| Communication Context Policy | `feature/communication/engine/CommunicationContextPolicy.kt` | **FOUNDATION READY** | `AVAILABLE DATA != MESSAGE CONTENT` allowlists & denylists established |
| Visual Catalog & Previews | `theme/SkillSyncDesignCatalog.kt` | **FOUNDATION READY** | Compose preview surface covering all Wave 1 components and states |

---

## WAVE 2 — Plan / Operational Batch Fulfillment (VERIFIED)
| Screen / Surface | Type | File Path | State | Verification / Notes |
|---|---|---|---|---|
| Plan (Allocation Desk) | Parent Tab | `feature/training/ui/AllocationDeskScreen.kt` | **VERIFIED** | Modernized workspace, token pure, pressable tactile feedback |
| Batch Detail | Inner Screen | `feature/training/ui/BatchDetailScreen.kt` | **VERIFIED** | Consolidated Decision Hub, SkillSyncTopBar, intent-aware MessagePreviewDialog |
| Eligibility & Match Verification | Inner Sheet | `feature/training/ui/EligibilitySheet.kt` | **VERIFIED** | Honest blocker analysis, token pure, clean dismiss button |
| Mark Skill Certification | Modal Dialog | `feature/training/ui/MarkSkillDialog.kt` | **VERIFIED** | >= 11sp typography, clear explanation of RMS record effect |
| Batch Broadcast Share | Communication Engine | `feature/training/ui/BatchShare.kt` | **VERIFIED** | Intent composition, CommunicationContextPolicy allowlist/denylist sanitization |
| External Vendor Staffing | Modal Sheet | `feature/training/ui/NetworkStaffingSheet.kt` | **VERIFIED** | Prefilled policy-sanitized staffing request body, no emoji buttons |
| Capacity Runway | Inner Screen | `feature/report/ui/CapacityRunwayScreen.kt` | **VERIFIED** | SkillSyncTopBar, >= 11sp typography, zero raw Color.White |
| My Schedule | Inner Screen | `feature/training/ui/MyScheduleScreen.kt` | **VERIFIED** | SkillSyncTopBar, design token typography and list styling |

---

## WAVE 3 — People / Trainer Capability & Readiness (VERIFIED)
| Screen / Surface | Type | File Path | State | Verification / Notes |
|---|---|---|---|---|
| People (Team Directory) | Parent Tab | `feature/home/TeamTab.kt` | **VERIFIED** | Clean search/filters, >= 11sp typography, zero raw Color.White |
| Team Member Card | Component | `feature/home/TeamMemberCard.kt` | **VERIFIED** | Evidence-based status, honest readiness pills, pressable feedback |
| Team Calendar | Inner Screen | `feature/home/TeamCalendarScreen.kt` | **VERIFIED** | Category emojis excised (🌴, 📦, 🎯, 🎤, 🏖️ replaced with ToneChips), >= 11sp typography |
| Trainer 360 | Inner Screen | `feature/training/ui/Trainer360Screen.kt` | **VERIFIED** | SkillSyncTopBar, emoji-free verdicts, sanitized manager evaluation via CommunicationContextFilter |
| Skill Assign Flow | Modal Sheet | `feature/home/SkillAssignFlow.kt` | **VERIFIED** | Explicit RMS Key 255 provenance banner, exact write-warning contract preserved |
| Skill Profile | Inner Screen | `feature/capability/ui/SkillProfileScreen.kt` | **VERIFIED** | Evidence badges (CERTIFIED, DELIVERED, BUILT), Compose ProgressIndicators, "Insufficient evidence" fallback |
| Trainer Practice | Inner Screen | `feature/training/ui/TrainerPracticeScreen.kt` | **VERIFIED** | SkillSyncTopBar, SkillSyncEmptyState, learner voice scope banner |
| Capability Graph | Inner Screen | `feature/capability/ui/CapabilityGraphScreen.kt` | **VERIFIED** | Full card-based network breakdown, ToneChips with theme tokens, honest gap indicators |

---

## WAVE 4 — Courses / Capability Marketplace (VERIFIED)
| Screen / Surface | Type | File Path | State | Verification / Notes |
|---|---|---|---|---|
| Courses (Catalog) | Parent Tab | `feature/home/CoursesTab.kt` | **VERIFIED** | ToneChips, SkillSyncCards, evidence tags (CERTIFIED/DELIVERED), 100% test contract anchors preserved |
| Course Curriculum Drawer | Modal Sheet | `feature/home/CourseCurriculumSheet.kt` | **VERIFIED** | 4-tab structure (Modules, Capability & Readiness, Public Schedules, Resources), single-owner SPOF alerts, sanitized PreparationRequestDialog |
| Course Communication Policy | Communication Engine | `feature/communication/engine/CommunicationContextPolicy.kt` | **VERIFIED** | Added COURSE_PREPARATION_REQUEST, CAPABILITY_DEVELOPMENT_REQUEST, CURRICULUM_SHARE with strict allowlists/denylists |

---

## WAVE 5 — Commercial Opportunities & Pipeline
| Screen / Surface | Type | File Path | State |
|---|---|---|---|
| Opportunities (List) | Parent Tab | `feature/opportunity/ui/OpportunityListScreen.kt` | **PARENT PASS** |
| Opportunity Detail | Inner Screen | `feature/opportunity/ui/OpportunityDetailScreen.kt` | **PARENT PASS** |
| Opportunity Guardian | Inner Screen | `feature/opportunity/ui/OpportunityGuardianScreen.kt` | AUDITED |
| Pipeline Radar | Inner Screen | `feature/pipeline/ui/PipelineRadarScreen.kt` | AUDITED |

---

## WAVE 6 — Today / Executive Briefing Cockpit
| Screen / Surface | Type | File Path | State |
|---|---|---|---|
| Today (Command Centre) | Parent Tab | `feature/home/ManagerCommandCentre.kt` | **PARENT PASS** |
| Weekly Executive Report | Inner Screen | `feature/report/ui/WeeklyReportScreen.kt` | AUDITED |
| HR Monthly Report | Inner Screen | `feature/report/ui/HrMonthlyReportScreen.kt` | AUDITED |
| Priorities Radar | Inner Screen | `feature/priorities/ui/PrioritiesScreen.kt` | AUDITED |
| Copilot Chat Sheet | Floating Sheet | `feature/copilot/ui/CopilotChatSheet.kt` | AUDITED |
| Notification Center | Modal Sheet | `feature/home/NotificationCenter.kt` | AUDITED |

---

## WAVE 7 — Delivery Operations & Governance
| Screen / Surface | Type | File Path | State |
|---|---|---|---|
| Delivery Operations | Parent Tab | `feature/delivery/ui/DeliveryOperationsWorkspace.kt` | AUDITED |
| Delivery Compliance | Inner Screen | `feature/compliance/ui/DeliveryComplianceScreen.kt` | AUDITED |
| Viber Automation | Inner Screen | `feature/viber/ui/ViberAutomationScreen.kt` | AUDITED (Legacy regex dispatch) |
| Accounts Directory | Inner Screen | `feature/accounts/ui/AccountsScreen.kt` | AUDITED |
| Competency Benchmark | Inner Screen | `feature/benchmark/ui/BenchmarkScreen.kt` | AUDITED |

---

## WAVE 8 — Cross-Product Command & Supporting Surfaces
| Screen / Surface | Type | File Path | State |
|---|---|---|---|
| Actions Inbox | Supporting Tab | `feature/home/ActionsInbox.kt` | AUDITED |
| Universal Command Search | Omnibox | `feature/search/ui/UniversalCommandSearch.kt` | AUDITED |
| Login / Authentication | Auth Screen | `feature/auth/ui/LoginScreen.kt` | AUDITED |
