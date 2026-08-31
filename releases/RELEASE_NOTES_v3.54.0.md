# SkillEdge Production Release v3.54.0 (Build 141)

- **Release Date**: 2026-08-31
- **Version Code**: `141`
- **Version Name**: `3.54.0`

## Why this release

Three manager-view enhancements land together, all built on the same rule the app
already follows: the manager's lever is preparation, never batch allocation.

## What changed

### Capacity Runway (planning)
A new screen off the "This Week" header (trend icon) shows the next 8 weeks of
incoming demand against the team's free capacity, week by week: the demand bar,
the capacity marker, and the gap tinted amber (short 1) or red (short 2+). Below
it, a ranked upskilling list - the courses that would open the most currently
uncoverable batches, each naming the closest-skilled trainer.
Backend: `GET /api/v2/planning/runway`.

### Team Copilot
The Copilot sheet, opened without a specific trainer, now answers team-level
questions - "who is free next week for AZ-104", "biggest coverage risk this
month", "top 3 upskills", "who is on the bench", "who is stretched", "who needs a
1:1" - with evidence and a confidence badge. Deterministic keyword routing, no
LLM. Per-trainer Copilot is unchanged.
Backend: `POST /api/v2/copilot/team`.

### Proactive digests + delivery-quality early warnings
- A morning brief (local 7-9am, once/day) and a Friday wrap (4-6pm) post from the
  monitoring pass: "Your day: N items need you" / the weekend team digest.
  Backend: `GET /api/v2/digest?kind=morning|weekly`.
- The dashboard now carries `delivery_alerts`: session recording not yet
  submitted for a running batch, roster dropped well below expected headcount, or
  a batch starting within 7 days with no trainer. Each raises its own
  notification in a new Delivery bucket.

### Fix
- `/api/v2/upskilling/demand-opportunities` referenced two undefined helpers
  (`_team`, `_fuzzy_match`) and would 500 on any call - now builds the roster
  inline and scores adjacency by course-token overlap.

## Compatibility

- Android package and signing identity unchanged; build 141 installs over 140.

## Validation

- Backend pytest 226 pass (208 baseline + 18 across the three features).
- Android testDebugUnitTest + lintRelease + assembleRelease green.
