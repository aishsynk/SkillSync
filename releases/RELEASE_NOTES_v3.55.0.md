# SkillEdge Production Release v3.55.0 (Build 142)

- **Release Date**: 2026-08-31
- **Version Code**: `142`
- **Version Name**: `3.55.0`

## Why this release

The last four items from the manager-view enhancement list. Same rule throughout:
the manager's lever is preparation and coaching, never batch allocation.

## What changed

### New-trainer ramp tracking
A screen off the "This Week" header showing every reportee who joined in the last
12 months: tenure, batches delivered, days to first batch, learner rating, and a
ramp stage — onboarding / first-deliveries / established, with a **stalled** flag
when someone is 3+ months in with no batch and low utilisation. Each card ends in
one concrete next step naming a course tied to open demand.
Backend: `GET /api/v2/ramp`.

### Accounts / customer view
A 5th tile on the command deck opens the team's customer book: every past
delivery and open demand grouped by account — batches delivered / upcoming /
open, the trainers and courses per account, and a concentration warning when one
account is more than half the team's delivery.
Backend: `GET /api/v2/accounts`.

### Manager benchmarking
Off the HR monthly report: how the team's utilisation, bench rate, learner
rating, certification coverage and feedback-incident rate compare to a baseline.
The baseline is stated in plain text on the screen — the two feedback metrics use
the real company-wide learner-feedback population; the rest compare against
documented Koenig delivery thresholds. **No fabricated "peer manager average".**
Backend: `GET /api/v2/benchmark`.

### Development plans
A persisted, per-trainer development plan inside Trainer 360: manager-authored
goals (certification / coaching / portfolio / other) with a status cycler, plus a
"Suggested" list the system computes from real signals (cert gaps tied to demand,
weak feedback, a thin course portfolio) that the manager adopts with one tap.
Stored in a new SQLite store on the persistent disk, same pattern as the action
inbox. Backend: `GET/POST/PATCH /api/v2/devplan`.

## Compatibility

- Android package and signing identity unchanged; build 142 installs over 141.
- Requires the Render persistent disk (`SKILLEDGE_STATE_DIR`) for dev-plan
  storage — already declared in `render.yaml`; plans degrade to read-only if the
  filesystem is not writable.

## Validation

- Backend pytest 254 pass (241 after ramp+accounts, +5 benchmark, +8 dev plans).
- Android testDebugUnitTest + lintRelease + assembleRelease green.
