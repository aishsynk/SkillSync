# SkillEdge Production Release v3.47.0 (Build 133)

- **Release Date**: 2026-08-30
- **Version Code**: `133`
- **Version Name**: `3.47.0`

## Why this release

Data-integrity pass after the full project review. Three things were presenting
invented or unmeasured data as real.

## What changed

### 1. Persistent state on Render (stops data loss on every deploy)
`render.yaml` now declares a 1 GB persistent disk at `/var/data` and sets
`SKILLEDGE_STATE_DIR=/var/data`. Previously the manager action inbox, the
session-revocation denylist (the v3.45.1 logout fix) and notification
seen-state all lived on Render's ephemeral filesystem and were wiped on every
redeploy.

### 2. No more fabricated team
`_build_fallback_manager_intelligence` (a hardcoded 8-trainer roster with
invented utilisation, courses, certs and pax) and the matching demo roster in
`team-capability` are removed. An account with no RMS-mapped reportees now
returns an honest empty state (`no_reportees: true` on the dashboard payload)
instead of a fake team.

### 3. Trainer Index is honest about what it measures
The Diamond/Platinum/Gold tier was computed with ~13 of its 20 criteria guessed
from the Qubits score (`mocks_taken = 2 if qubits >= 60`, invented tenure, etc.).
Those inputs are now real zeros, and the response carries
`confidence: "partial"`, `measured_criteria` (the 7 RMS-backed axes) and
`estimated_criteria` (the 13 RMS does not expose). Tier scores will drop for
some trainers — this reflects that only utilisation, quality index, first-time
delivery, certifications, HR incidents, instructor certs and negative feedback
are actually measurable today.

## Compatibility

- Android package and signing identity unchanged; build 133 installs over 132.
- APK code is unchanged from 132; version incremented to record the deployment.

## Operator action required

Add the persistent disk in the Render dashboard if the service is not a
Blueprint (Settings → Disks → Add Disk, name `skilledge-state`, mount
`/var/data`, 1 GB), and set `SKILLEDGE_STATE_DIR=/var/data` as an env var.
Without the disk, change 1 has no effect.

## Validation

- Backend pytest suite green (169).
- After deploy: dashboard for a no-reportee account returns `no_reportees: true`
  and empty arrays (not a fake team); `trainer_index` carries `confidence`.
