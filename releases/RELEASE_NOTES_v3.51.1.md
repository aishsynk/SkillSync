# SkillEdge Production Release v3.51.1 (Build 138)

- **Release Date**: 2026-08-31
- **Version Code**: `138`
- **Version Name**: `3.51.1`

## Why this release

Critical fix. The app appeared empty for real managers because the login email
and the form RMS uses for `reportees` (key 82) do not always share the same
local-part separator — `aishwar_c@koenig-solutions.com` returns no roster, while
`aishwar.c@koenig-solutions.com` returns the real team. Every manager-view
feature (composed messages, opportunity cost, priorities, feedback trends) needs
a roster, so all of them looked broken.

## What changed

- **`_resolve_manager_email`** — at login the email is normalised to whichever
  local-part form RMS answers for (tries `_`/`.`/`-` separator swaps, original
  first, falls back to the original for a genuinely reportee-less account). The
  session then carries that canonical form and every downstream endpoint sees
  the real roster.
- `_v2_manager_session` accepts any separator variant of the signed-in email, so
  a client holding the pre-fix form is not locked out with a 403.
- **Priorities**: an unstaffed batch the team can already cover while trainers
  sit on the bench is bumped one severity level and annotated ("Your team can
  cover this and Abhinav is on the bench") — an idle team plus open demand is a
  stronger call than a distant unstaffed batch.

## Compatibility

- Android package and signing identity unchanged; build 138 installs over 137.
- No app-code change; version incremented to record the backend deployment.

## Validation

- Backend pytest suite green (190, +3 `test_email_resolver.py`).
- Verified: login `aishwar_c@` resolves to `aishwar.c@`, dashboard shows the real
  2-person team, opportunity cost `1 / 2` coverable, composed weekly messages
  render per reportee.
