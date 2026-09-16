# Infrastructure follow-up — backend state is ephemeral on Render Free

**Status:** OPEN — documentation only. No storage implementation was changed in this commit.
**Raised:** 2026-09-16, during the Communication Intelligence provider work.
**Owner:** unassigned.

## The finding

The backend runs as a **Render free web service** (`render.yaml`): 512 MB RAM, 0.1 CPU,
spun down after 15 minutes idle, **ephemeral filesystem**, and free web services
**cannot attach persistent disks**. The `disk:` block that previously claimed a 1 GB
volume at `/var/data` is therefore inactive and is now commented out.

Everything the backend writes under `SKILLEDGE_STATE_DIR` (set to `/var/data` in
`render.yaml`, defaulting to `.`) is lost on **every restart, redeploy and spin-down** —
which on the free plan happens daily, not rarely. The code already tolerates a missing or
read-only path (stores create their directory and requests do not fail), so this is
silent data loss, not an outage.

## State inventory

| # | State | Written by | File under `SKILLEDGE_STATE_DIR` | Classification | Why |
|---|---|---|---|---|---|
| 1 | Action inbox / lifecycle | `ActionStore` (`backend.py` `_action_repository`) | `skilledge_actions.sqlite3` (+ legacy `action_state.json`) | **MUST PERSIST** | Manager-authored decisions: acknowledged/snoozed/done. Losing it resurrects closed actions. `backend.py` already carries a comment saying this needs a real datastore. |
| 2 | Session revocation denylist | `SessionRevocationStore` | `skilledge_session_revocations.sqlite3` | **MUST PERSIST** | Security state. If it resets, a revoked session token is accepted again until its 30-day TTL expires. |
| 3 | Communication history | `CommunicationStore` | `skilledge_communication.sqlite3` | **MUST PERSIST** | The audit trail of what a manager copied/shared, including `SHARED_EXTERNALLY` records. Also backs the Communication screen's HISTORY list. |
| 4 | Development plans | `DevPlanStore` | `skilledge_devplans.sqlite3` | **MUST PERSIST** | Manager-adopted plans. Suggested items are recomputed live, but adopted ones exist only here. |
| 5 | Opportunity guardian records | `OpportunityStore` | `skilledge_opportunities.sqlite3` | **MUST PERSIST** | Decisions/verdicts per opportunity, and `/api/v2/communication/generate` reads it as verified context. |
| 6 | Capability requirements & evidence | `CapabilityStore` | `skilledge_capability.sqlite3` | **MUST PERSIST** (not yet live) | Evidence- and approval-gated capability data. No route consumes it yet (Phase 1), so nothing breaks today — but it is not cache. |
| 7 | Reportee roster snapshot | `ReporteeStore` | `skilledge_reportees.sqlite3` | **CACHE / MAY RESET** | Rebuildable from RMS; only costs a slower first request after a restart. |
| 8 | Warm payload caches (`_warm_payload_cache`, `_allocation_payload_cache`, `_token_cache`) | `backend.py`, in-process | none (memory) | **CACHE / MAY RESET** | Already memory-only and rebuilt on demand. |
| 9 | Notification state (`_manager_notifications`, `_reportee_notifications`) | `backend.py`, in-process | none (memory) | **CACHE / MAY RESET** on the backend | Backend-side is memory-only and already resets on restart. The durable per-device seen-state lives in the Android `NotificationStateStore`. |
| 10 | Active sessions (`_sessions`) | `backend.py`, in-process | none (memory) | **UNKNOWN / REVIEW** | A restart signs everyone out. Acceptable today, but it interacts with #2: the denylist matters only while sessions survive. Decide both together. |
| 11 | Viber outbox / dispatch state | Android `ViberOutboxStore` | none (device) | **CACHE / MAY RESET** (device-side) | Listed for completeness; not backend state. |

## Morning Note — deliberately unaffected

The Morning Note's daily draft and its recent-greeting history are stored **on the device**
in the Android `DigestStateStore`, and that must stay so. The backend composes a greeting
on request and keeps nothing; a Render sleep, redeploy or cold start therefore cannot
change the greeting a manager already saw today. Do not "fix" this by moving the daily
draft server-side while backend storage is ephemeral.

## Follow-up (separate task, not this commit)

1. Decide the durable store for rows 1–6: Render Postgres (free tier available), or a
   paid instance type with a real disk, or an external managed database.
2. Migrate `ActionStore`, `SessionRevocationStore`, `CommunicationStore`, `DevPlanStore`,
   `OpportunityStore` and `CapabilityStore` behind their existing repository interfaces —
   they are already the only write paths, so the blast radius is contained.
3. Decide row 10 (sessions) alongside row 2.
4. Only then re-enable a disk or drop `SKILLEDGE_STATE_DIR` entirely.
