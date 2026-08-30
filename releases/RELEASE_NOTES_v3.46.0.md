# SkillEdge Production Release v3.46.0 (Build 130)

- **Release Date**: 2026-08-30
- **Version Code**: `130`
- **Version Name**: `3.46.0`

## Why this release

Managers reported that many screens sat permanently on a loading spinner, and
that background alerts stopped once the app was closed.

Root causes:
- The dashboard, capability/Courses, HR monthly report and weekly report
  endpoints ran a full per-trainer RMS fan-out synchronously. On a Render cold
  start this exceeded the app's 60s network timeout, so the screen never left
  its skeleton.
- Background monitoring was WorkManager-only. Doze and OEM battery managers
  defer or kill chained work, so "app closed" monitoring was unreliable.

## What changed

### Backend — partial-first, warm in background
- `unified-manager-intelligence`, `capability/portfolio`, `hr/monthly-report`,
  `report/weekly` and `team/calendar` now retain the last complete payload,
  rebuild it in a background thread, and answer immediately — instantly on a
  warm cache, and within a bounded wait on a cold one, otherwise returning a
  skeleton flagged `loading` while the full payload finishes.
- `?refresh=1` purges and rebuilds. A failed rebuild never discards the last
  good payload.

### Android — offline-first screens
- HR Monthly Report and Weekly Report now render the last saved snapshot for
  that period instantly from the on-device cache, refresh in the background,
  and poll briefly while the backend reports `loading` — no more blank spinner,
  and they still work offline.

### Reports — genuine feedback, no boilerplate

- Wired RMS key 244 (`trainerFeedback`) — real per-assignment learner ratings
  (1–5) and free-text comments. The endpoint ignores its email filter, so rows
  are filtered per trainer in the backend (this also fixed Trainer 360 showing
  other trainers' feedback).
- The HR monthly `structured_feedback`, the weekly `standpoint_note`, and the
  Trainer 360 feedback block are now built **only from evidence on record**:
  real learner rating and trend, short dated learner excerpts, named
  certification gaps, utilisation and HR-incident counts. The generic
  behavioural sentences that were asserted for every trainer regardless of data
  ("articulation remains the primary growth area", "hesitation and slight panic
  are visible", `mock_summary` hardcoded to "Composure: Improving") are gone. A
  dimension with no evidence this cycle now says so.

### Android — always-on monitoring
- New `dataSync` foreground service keeps the delivery-alert pipeline running
  after the app is closed or the device is dozing, shown by a permanent
  low-priority "SkillEdge is monitoring delivery activity" notification.
- A boot receiver restarts monitoring after a reboot or app update.
- A one-time prompt asks to exempt SkillEdge from battery optimisation.
- WorkManager remains the 15-minute backstop; the aggressive ~60s self-chaining
  pass was removed in favour of the service.

## Compatibility

- Android package and signing identity unchanged; build 130 installs over
  build 129 while preserving user data.
- New permissions: `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC`,
  `RECEIVE_BOOT_COMPLETED`, `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`.

## Validation

- Backend: full pytest suite green, including new `tests/test_warm_endpoints.py`.
  Live RMS probe confirms first-call latency is bounded and repeat calls are
  instant for all five endpoints.
- Android: unit/render tests, release lint and signed release assembly must
  pass in CI before publication.
- Device checks pending an ADB-connected phone: install-over-130, force-stop →
  persistent notification stays and alerts still fire, reboot → service returns.
