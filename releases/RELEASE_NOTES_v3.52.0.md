# SkillEdge Production Release v3.52.0 (Build 139)

- **Release Date**: 2026-08-31
- **Version Code**: `139`
- **Version Name**: `3.52.0`

## Why this release

Manager-view wave 2. Three features, built in parallel, each closing a gap
between "here is the situation" and "here is what to do." Everything discussed in
this cycle is now built with real screens.

## What changed

### "This Week" screen
A new screen (reached from a "This Week" card below the dashboard briefing) that
renders `GET /api/v2/manager/priorities` as one ranked worklist: unstaffed
batches by deadline, trainers who need a one to one, overloaded trainers,
certification gaps, overdue manager actions. Each item has a severity stripe,
a due date, and a tap-through to the batch, the trainer's 360, or the action
inbox. Offline-first with pull-to-refresh.

### Eligibility-gap closer (Batch Detail)
`GET /api/v2/eligibility/batch` and a "Why my team isn't eligible" sheet on the
Batch Detail screen. For one open batch it shows which of the manager's trainers
are already eligible, and for each blocked trainer exactly what is in the way —
skill level below the floor, missing certification, uncleared mock,
unconfirmed availability, client do-not-call — tagged with the fix the manager
is allowed to make. Where the block is skill level, a "Mark skill" action runs
the existing RMS write. Koenig's algorithm still owns allocation; this only
helps the manager prepare their trainer to be picked.

### Certification intelligence (Capability tab)
- The capability portfolio now groups the team's courses by real **technology
  and domain** (wiring RMS `courseTechnology` key 114 and `courseDomain` key
  205), not the vendor fallback.
- A new "Certification priorities" section: which exams unlock the most open
  demand and how many of your trainers are missing them. Certification expiry is
  shown honestly as "RMS does not expose expiry dates" rather than inventing
  them.

## Compatibility

- Android package and signing identity unchanged; build 139 installs over 138.

## Validation

- Fixed: `_skills()` rows are keyed `course`, but the opportunity/message code read `course_name` (always None) — added the alias, so per-reportee messages and the opportunity KPI now match real skills.
- Backend pytest suite green (203, +13: `test_batch_eligibility.py`,
  `test_cert_intel.py`).
- Android unit tests, lint-release and signed release assembly green.
- Verified end to end against `aishwar_c@koenig-solutions.com` (real 2-person
  team).
