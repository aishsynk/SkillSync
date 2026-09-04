# SkillEdge Application Flow and RMS API Audit

**Date:** 2026-09-04  
**Release state:** Local review candidate only; publication is blocked.

## Scope and evidence

- Reviewed the manager/reportee application shell, notification engine, action lifecycle,
  Trainer 360, reporting, Retrofit declarations, backend routes, `_APIS`, and all supplied
  `trainer_portal_api_details/` instruction files.
- Re-ran live, read-only probes for unallocated demand (key 190) and previous/upcoming
  assignments (key 16). Neither response contains a skill-level field.
- Confirmed Auto Tall's trainer/course level is available from `trainerDetails.SkillLevel`
  (key 75). Assignment messages may show that value only after an exact normalised course
  match, labelled as the **trainer's skill level**, with source `trainer_details`.

## Current end-to-end flow

1. Every account signs in with email and password; first login may bootstrap from employee code.
2. Manager and reportee use the same `Main` shell. Reportee data is scoped to self and
   manager-only mutations must be hidden and rejected server-side.
3. Dashboard polling obtains unified intelligence and drives Today, People/My 360, demand,
   calendar, action queue, and local notifications.
4. Heavy endpoints may return retained/partial data while a guarded background warm completes.
5. Manager actions persist in SQLite with an append-only audit trail. Lifecycle changes affect
   SkillEdge queue state only; they do not perform the operational work in RMS.
6. RMS writes are limited to the verified add-skill flow. Allocation remains recommendation-only.

## Publish blockers: existing behavior that must be corrected

| Priority | Gap | Evidence / impact | Required correction |
|---|---|---|---|
| Fixed locally | Empty course free-schedule was treated as proof that no trainer held the skill | The same card could show “No trainer holds this course” and list Skill 100 candidates because availability key 171 and capability key 75 were interpreted independently. v3.69.4 treats an empty free-schedule only as unknown course-date availability and preserves the key-75 course matches. |
| P0 (in-repo done; rotation pending) | Plaintext RMS credentials remain in source and supplied API documents | Startup reports 74 missing environment variables and falls back to embedded credentials. Repository disclosure gives direct RMS access. | Provision all RMS credentials as deployment secrets, verify every key, remove source/document values, and rotate exposed credentials. **2026-09-04 Claude Sonnet 5:** literals moved out of `backend.py` into single tracked `rms_service_credentials.py`; `trainer_portal_api_details/*.txt` password lines scrubbed; `.env.example` added; `SKILLEDGE_REQUIRE_SECRET_CREDS=1` hard-fail gate added. Render secret provisioning + RMS password rotation remain operator actions. |
| Fixed locally | Trainer 360 fabricated Trainer Index inputs | v3.69.3 renders only the backend result, labels its 7/20 RMS-backed criteria as partial, and shows unavailable when the API fails. |
| Fixed locally | Learner sentiment fabricated 94% / High Performer and default themes | v3.69.3 returns and renders unavailable/unclassified with empty themes when no feedback evidence exists. |
| Fixed locally | Benchmark fabricated company baselines on API failure | v3.69.3 preserves missing company baselines as unknown and suppresses verdicts. |
| P1 (mostly done) | Manager/reportee shared-shell permissions are not centrally modelled | Action queue was one confirmed mismatch: reportees saw buttons that server rejected. Similar manager-only affordances are gated independently across screens. | Create one capability matrix from session role and verify every visible write control against its backend gate. Do not widen server authority to match UI. **2026-09-04 Claude Sonnet 5:** `SessionManager.canManageTeam()` added as the single predicate; ActionsInbox, BatchDetail Reportee skill-mark and Trainer 360 endorse now gate on it. Full screen-by-screen sweep of remaining write controls still open. |
| Fixed locally | Delivery notification navigation target was inconsistent | v3.69.3 routes individual and summary delivery alerts to the existing Work/Delivery workspace; it no longer passes assignment IDs to Demand detail. |
| P1 (done) | Android still consumes legacy `/api/data` and `/api/action` aliases | Backend contract says `/api/v2` is canonical, but core Android calls remain on compatibility routes. Removal would break the app. | Move every active client call to canonical V2 routes, test, then deprecate aliases separately. **2026-09-04 Claude Sonnet 5:** 12 `/api/v2/data|action/*` aliases added in `backend.py`; all 10 `SkillEdgeApi.kt` calls migrated to v2; legacy routes kept as deprecated aliases. |
| P1 (partly done) | API and role documentation is stale | Backend header still says login is manager/Trainer Plus only, while current behavior supports all resolved roles with passwords. | Reconcile code comments, public API contract, tests, and durable context with the current auth model. **2026-09-04 Claude Sonnet 5:** `backend.py` auth header comment + `AI/CONTEXT.md` reconciled to the two-step check/login, mandatory-password, all-roles model. |
| P1 | Notification meaning and source are not consistently visible | Some messages present derived signals without source/freshness or distinguish trainer capability from assignment facts. | Carry source and freshness on decision-relevant notifications and omit unavailable values. |
| P2 | Date-sensitive tests use fixed “active” windows | One full-suite test expired on 2026-09-04 and failed despite unchanged production behavior. | Use dates relative to test execution; corrected for delivery compliance in this review. |

## API coverage and missed data

All 37 supplied RMS endpoints are registered. The issue is utilization quality, not registration.

### Registered but still not used in production flow

| RMS capability | Key | Status / next verification |
|---|---:|---|
| Course availability | 104 | Probe schema; use only to correct stale/duplicate/discontinued catalogue records. |
| Course content URL | 156 | Probe schema; compare with existing CourseURL/TOC fields before adding another source. |
| Course modules | 206 | Probe schema; candidate for existing curriculum detail, not a new top-level feature. |
| Exam-course links | 215 | Probe schema; required to make current certification-gap guidance exact. |
| Latest course version | 172 | Probe schema; required to validate current trainer/course currency claims. |
| Unique certification count | 72 | Prior live shapes returned zero rows; keep unavailable until RMS confirms a working request. |

### Data already available but incompletely carried through

| Source | Missed fields / combination | Existing flow improved by using it |
|---|---|---|
| `trainerDetails` 75 | `techcallrating`, Future Skill Date, DM, five shift-band off-date lists | Trainer 360 evidence, availability explanations, and allocation eligibility. |
| `assignmentPax` 209 | Student roster rows | Existing roster-health alert and batch detail. PII must remain scoped and must not be placed in notifications. |
| `recordingDetails` 278 | Confirmed download-link field shape | Existing recordings library; remove guessed fallbacks after live confirmation. |
| `activeSCDate` 13 | CSM and commercial context | Existing pipeline/account analysis; never expose fees in trainer-facing surfaces. |
| `courseSchedule` 246 | Country, region and delivery mode | Existing demand/capacity planning after live schema validation. |
| `trainerFeedback` 244 | Question/text pairs | Existing feedback evidence and coaching; preserve verbatim text only in authorised scope. |

## Assignment skill-level correction

- Key 16 and key 190 do not provide assignment skill level.
- For an assigned batch, join its trainer and course to key 75 and expose
  `skill_level` plus `skill_level_source=trainer_details` only on an exact normalised match.
- Message wording is `Trainer skill level: Lx`; it must never say “required assignment level.”
- If the join fails, omit the level and report the source as unavailable.
- For an unallocated batch there is no assigned trainer and no required-level field in the live
  response, so the app must not invent one. The existing self-mark ceiling is a workflow rule,
  not the batch's required skill level.

## Validation required before publication

1. Resolve every P0 item and every user-raised review point.
2. Run the full backend suite with zero failures and the full Android unit/render suite.
3. Build one signed release candidate and record its checksum.
4. Validate manager and reportee journeys on-device, including permissions, offline/cache state,
   notification text/taps, action transitions, and every RMS write confirmation.
5. Deploy that exact package to Development only, validate against live RMS with safe read paths
   and controlled write cases, then present results to the operator.
6. Publish to Production only after explicit operator confirmation.
