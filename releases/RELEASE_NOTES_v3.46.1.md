# SkillEdge Production Release v3.46.1 (Build 131)

- **Release Date**: 2026-08-30
- **Version Code**: `131`
- **Version Name**: `3.46.1`

## Why this release

Hotfix for v3.46.0. Production validation of the warm-endpoint work found that
`/api/data/trainer-360` returned HTTP 502 on a cold Render instance: the screen
was already one of the heaviest (per-trainer RMS fan-out), and v3.46.0 added a
learner-feedback fetch on top, pushing the total past Render's 60s proxy limit.

## What changed

- `trainer_360` now uses the same partial-first + background-warm pattern as the
  other heavy endpoints (`_serve_or_warm`, key `trainer360::<email>::<manager>`):
  the retained profile is served instantly, a rebuild runs in a background
  thread, a cold call waits a bounded 45s, otherwise a `loading` skeleton is
  returned.
- `Trainer360ViewModel` polls briefly while the backend reports `loading`,
  keeping any cached profile on screen.
- No change to the v3.46.0 feature set (partial-first endpoints, offline-first
  reports, always-on monitoring, evidence-only feedback messages).

## Compatibility

- Android package and signing identity unchanged; build 131 installs over 130.

## Validation

- Backend pytest suite green (169).
- Android unit tests, lint-release and signed release assembly green in CI.
- Production re-probe of `/api/data/trainer-360` must return 200 with real
  `learner_rating` and quotes.
