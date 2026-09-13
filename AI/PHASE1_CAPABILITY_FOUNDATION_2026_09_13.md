# Phase 1 — Capability Foundation Implementation (2026-09-13)

Status target reached: **PHASE 1 CAPABILITY FOUNDATION COMPLETE — READY FOR MATCHING ENGINE**
(with limitations noted in §7). No commit, push, release, or version bump made.

## Files created

- `domain/capability/__init__.py`, `domain/capability/models.py` — dataclasses/enums only, no I/O.
- `repositories/capability_store.py` — `CapabilityStore`, SQLite persistence, 6 tables.
- `services/capability/__init__.py`, `services/capability/capability_service.py` — `CapabilityService`.
- `scripts/seed_capability_foundation.py` — manual, one-off DRAFT seed for the 8 validation courses.
- `tests/test_capability_foundation.py` — 14 tests (8 as one parametrized subtest group).
- `AI/PHASE1_CAPABILITY_FOUNDATION_2026_09_13.md` — this file.

## Files modified

- `backend.py`:
  - Imports: `CapabilityStore`, `CapabilityService`.
  - New module-level instantiation: `_capability_repository`, `_capability_service` (next to
    `_opportunity_repository` et al.) — **not referenced by any route**, per instruction §7/§8.
  - `_capability_for()`: removed the 8 hardcoded named-trainer profiles, the generic AZ-104/MCT
    fallback, and the `_current_util(series) or 82` guessed-default. Added `avg_qubits: None`
    (was `0`) when no capability data exists, and `capability_data_available: bool` so callers
    can distinguish "measured, and it's low" from "no data."
- `AI/DECISIONS.md`: corrected the 2026-09-10 opportunity-matching entry (Phase 0); added a new
  entry documenting the fabrication removal.
- `AI/CONTEXT.md`: added the Capability Intelligence Foundation summary (durable reference).

## Schema implemented (SQLite, `skilledge_capability.sqlite3`, gitignored like the other stores)

```
capabilities(id PK, name, family, description, created_at)
capability_aliases(capability_id FK, alias, PK(capability_id, alias))
capability_relationships(from_id FK, to_id FK, type, PK(from_id, to_id, type))
course_capabilities(id PK autoinc, rms_cid, course_code, course_title, vendor,
                     requirements_json, required_certifications_json,
                     status, source, updated_by, updated_at)
  -- versioned: a new row per status transition, never overwritten in place,
  -- so "who approved this and when" is never lost. Index on (rms_cid, updated_at DESC).
trainer_capabilities(trainer_email, capability_id FK, proficiency, verified, last_verified_at,
                      PK(trainer_email, capability_id))
capability_evidence(id PK autoinc, trainer_email, capability_id FK, type, source_system,
                     source_record_id, captured_at, raw_json,
                     FK(trainer_email, capability_id) -> trainer_capabilities)
```

No ORM introduced — raw `sqlite3` + hand-written SQL, matching `OpportunityStore`/`DevPlanStore`/
`ActionStore` exactly. No graph or vector database — relationships are a plain edge table, queried
with normal SQL joins.

## Domain / repository / service boundaries

```
(future) Plan / People / Trainer 360 / Courses / Opportunities / Upskilling
        ↓
CapabilityService        (business rules: approval gating, evidence requirement)
        ↓
CapabilityStore          (persistence only — no business rules)
        ↓
SQLite
```
`backend.py` route handlers do not touch `CapabilityStore` directly anywhere (there are no
routes yet) — the one existing consumer, `_capability_for()`, was deliberately left as a
standalone RMS-facing function and NOT rewired onto the new store this phase, since its job
(building a *live* RMS capability snapshot for team-capability screens) is a different concern
from *curated, evidence-backed* capability truth. Merging the two is matching-engine-phase work,
not Phase 1.

## Fabrication removed — exact change, affected endpoints

`_capability_for()` (backend.py) previously substituted invented course/certification data for
8 named trainer emails, plus a generic AZ-104/MCT fallback for anyone else with empty RMS data,
plus a guessed `82%` utilisation default. All three removed. Affected endpoints (same three found
in Phase 0, all covered by the new endpoint-compatibility test):

- `/api/v2/data/team-capability` (+ legacy alias `/api/data/team-capability`)
- `/api/v2/capability/portfolio`
- `/api/v2/capability/cert-intel`

Downstream aggregation code in these routes (`kpis.team_readiness_score`, `avg_trainer_coverage_pct`,
etc.) was already null-safe (`if t["readiness_score"] is not None`) — confirmed by reading it, not
assumed — so no further changes were needed there; the fix was contained entirely to
`_capability_for()`.

## Validation-course records (seeded as DRAFT, not APPROVED)

| Cid | Course | Required capabilities (mandatory) | Preferred | Required cert |
|---|---|---|---|---|
| 18301 | DP-700T00: Microsoft Fabric Data Engineer | Microsoft Fabric, Lakehouse, OneLake, Fabric Pipelines, Fabric Notebooks, Apache Spark, Data Ingestion, Data Transformation | Delta Lake, Fabric Data Warehouse | DP-700 |
| 15509 | DP-600T00: Microsoft Fabric Analytics Engineer | Microsoft Fabric, Lakehouse, Power BI Semantic Models, DAX, Data Transformation | Fabric Data Governance, OneLake | DP-600 |
| 9716 | AI-102T00: Develop AI Solutions in Azure | Azure AI Services, Computer Vision, NLP (Azure AI Language), Azure AI Search, Responsible AI | Azure OpenAI | AI-102 |
| 9055 | AZ-104T00-A: Microsoft Azure Administrator | Azure Identity & Governance, Azure Storage, Azure Compute, Azure Virtual Networking, Azure Monitor & Backup | — | AZ-104 |
| 9748 | SC-300T00: Microsoft Identity and Access Administrator | Microsoft Entra ID, Identity Governance, Access Management, Identity Security | — | SC-300 |
| 11405 | Cisco CCNA (200-301) Extended | Network Fundamentals, Network Access, IP Connectivity, IP Services, Network Security Fundamentals | Network Automation | CCNA |
| 899 | AWS Solutions Architect - Associate | Resilient / High-Performing / Secure / Cost-Optimized Architecture design | — | AWS-SAA |
| 742 | CISSP | all 8 CISSP domains (Security & Risk Mgmt, Asset Security, Security Architecture, Comms/Network Security, IAM, Security Assessment, Security Operations, Software Dev Security) | — | CISSP |

Sourced from each vendor's public exam-skills-measured pages, not from course titles — run via
`python scripts/seed_capability_foundation.py`. Verified locally: all 8 write as `DRAFT` /
`source=agent_authored_pending_human_review`.

## Curation workflow (implemented, not yet UI-exposed)

`CapabilityService.submit_course_capability_draft` (→ DRAFT) → `request_review` (→
REVIEW_REQUIRED) → `approve_course_capability(approved_by=...)` (→ APPROVED, refuses a blank
approver) → `deprecate_course_capability` (→ DEPRECATED). Every transition is a new row in
`course_capabilities`, never an in-place overwrite — full history retained. No admin screen exists
yet to drive this from the UI; it is callable today only from Python (backend console / scripts /
future routes).

## Tests / results

`tests/test_capability_foundation.py` — 14 tests (`CapabilityStoreTests`: 10; plus
`CapabilityForFabricationRemovedTests`: 4, one of which runs as 8 subtests for the 8 previously-
hardcoded emails). All pass. Covers, per instruction §9: Cid identity vs. duplicate-title rows,
duplicate-insert prevention, case-insensitive exact-only alias resolution (explicitly asserts a
near-miss does NOT resolve), `RELATED_TO` vs `CHILD_OF` distinctness, parent-relationship does
NOT auto-satisfy a course requirement, evidence provenance round-trips exactly, full approval
lifecycle including the "still not authoritative at REVIEW_REQUIRED" check and blank-approver
refusal, unverified trainer → empty list, and the critical fabrication-removal set (8 named
emails + generic-unknown trainer + a static source-scan asserting the removed identifiers never
reappear + a live endpoint round-trip proving `/api/v2/data/team-capability` still returns 200).

**Full existing suite re-run:** `python -m pytest tests/` → **358 passed**, no regressions. One
cross-test cache collision was found and fixed *during this work* (my own new endpoint test was
polluting a warm-cache key another test file's fixture email also used — `_serve_or_warm`'s
`capability::<email>` cache is process-global) — fixed by giving the new test its own email and
purging the cache in `tearDown`; documented in the test file's own docstring as a caution for
future tests against this route.

## Remaining limitations

- No route exposes `CapabilityService` yet — by design (§7/§8 of the instruction). Plan/People/
  Trainer 360/etc. still show only the pre-existing RMS-derived capability view.
- No bulk migration of existing RMS `trainerDetails` rows into `trainer_capabilities` — that needs
  normalization/confidence-threshold decisions that belong to the matching-engine phase, not this
  one (see the earlier Plan-matching audit's §10 "Legacy RMS migration strategy").
- The 8 seeded course profiles are DRAFT, authored by this session from public vendor exam pages
  — they still need a **named human curator** to review and `approve_course_capability(...)`
  before any future matcher may treat them as authoritative. No such curator has been assigned in
  this conversation.
- No admin UI exists to drive the curation workflow — it is Python-callable only.
- `_capability_for()` itself was not rewired onto the new capability store — it still builds its
  snapshot live from RMS each time, just without the fabricated substitutions. Unifying the two
  (so a "verified" capability screen can show curated + RMS-observed facts together) is
  matching-engine-phase scope.

## Exact readiness for Phase 2 (matching-engine implementation)

**Ready to start, contingent on:**
1. A named human curator reviewing and approving (or correcting) the 8 seeded DRAFT profiles —
   Phase 2's matching engine has nothing authoritative to consume until at least one profile is
   `APPROVED`.
2. An explicit decision on the legacy-RMS-migration confidence threshold (how a free-text
   `trainerDetails` course row becomes a normalized `TrainerCapability` + evidence row) — flagged,
   not decided, in this phase.
3. Explicit approval to proceed, per this instruction's own phased-approval pattern.

**PHASE 1 CAPABILITY FOUNDATION COMPLETE — READY FOR MATCHING ENGINE** (pending the above).
