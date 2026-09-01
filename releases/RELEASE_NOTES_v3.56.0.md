# SkillEdge Production Release v3.56.0 (Build 143)

- **Release Date**: 2026-09-01
- **Version Code**: `143`
- **Version Name**: `3.56.0`

## Why this release

This release ships the **Intelligent AI Mind Message Generation Overhaul** across all reportee 1-on-1 standpoints, HR monthly evaluations, team broadcasts, unallocated batch sharing, and Copilot intelligence, coupled with a complete **"Enterprise Intelligence Glass" UI/UX Modernization & Polish** across the Android app.

---

## What changed

### 1. Intelligent AI Mind Message Generation (4 Waves)
- **Reportee AI Mind (1-on-1 Standpoint Notes)**: Enriched `_reportee_message_facts` with 20+ live operational signals. 7-beat cross-referencing AI mind:
  - *Beat 1 (Load)*: Active delivery status, course, participants, utilization, and Qubits score.
  - *Beat 2 (Learner Themes)*: Cites real learner feedback themes (depth of knowledge, practical labs, pacing, engagement).
  - *Beat 3 (Demand ROI)*: Cross-references open batches on the demand board to quantify opportunity cost in participant-days and Trainer Index points gain (~200 points per exam).
  - *Beat 4 (Quality & Coaching)*: HR incident context and quality coaching asks.
  - *Beat 5 (Availability & Calendar)*: Days on bench, next available date, and leave balances.
  - *Beat 6 (Growth)*: Qubits upskilling push tied to specific demand.
  - *Beat 7 (Next Step)*: Concrete assignment or exam booking priority.
- **Team Intelligence & Group Safety**:
  - Forward-looking team briefs: delivery load, open demand, coverable batches, runway pressure, and benchmark cert gaps.
  - Backward-looking wrap-ups: delivered batches, pax, avg rating, feedback themes summary, and top performer recognition.
  - **Hard House Rule**: Group broadcasts state negative signals (bench, feedback flags, cert gaps) ONLY as aggregate counts. Individuals are named ONLY for positive recognition.
- **HR Monthly Report & Manager Evaluations**:
  - Structured 3-section format: `🟢 STRENGTH`, `🟠 AREA OF IMPROVEMENT`, and `🔵 MANAGER'S VERDICT`.
  - Trainer Index progression path, demand-linked growth ROI, and new-trainer ramp context.
- **Unallocated Batch Sharing & Allocation Outreach**:
  - Enriched outreach messages with candidate counts, top candidate recommendations, blockers (DNC, leave, skill match, cert gaps), and fast-track zero-exam tags.

---

### 2. "Enterprise Intelligence Glass" UI/UX Modernization
- **Aurora Mesh Ground Canvas (`AuroraBackground()`)**: Integrated across all core screens (`PrioritiesScreen`, `WeeklyReportScreen`, `HrMonthlyReportScreen`, `Trainer360Screen`, `BatchDetailScreen`), delivering a deep `#070B12` base with Royal Blue (`#1D4ED8`) and Cyan (`#06B6D4`) specular blooms.
- **Luminous Micro-Borders**: Standardized 1dp translucent alpha borders (`alpha = 0.28f` / `0.90f` specular top-edge) on cards and chips for contrast on OLED and IPS mobile displays.
- **Tabular Figures (`tnum`)**: Typography scale updated with `fontFeatureSettings = "tnum"` ensuring jitter-free numerical KPIs and scorecard metrics.
- **Frosted Glass Navigation**: Modernized week/month navigation bars into floating rounded pill containers with quick "Today" jump actions.
- **Copilot Intelligence UI**: Translucent glass modal bottom sheet with high-contrast user/assistant speech bubbles, confidence tier pills, and free-text team ask bar.

---

## Compatibility & Signing

- Android package and signing identity unchanged: signed with the production release keystore (`skillsync-release.jks` with v1/v2/v3 signing).
- Build 143 cleanly installs over Build 142.
- Backend routes fully backwards-compatible across all v1 and v2 API contracts.

---

## Validation & Test Suite

- **Backend Pytest Suite**: 261 passed (100% green) in 34.8s.
- **Android Unit Tests**: `./gradlew.bat testDebugUnitTest` (100% green).
- **Android Release Build**: `./gradlew.bat assembleRelease` (BUILD SUCCESSFUL).
