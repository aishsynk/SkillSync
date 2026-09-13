# SkillSync Capability Intelligence Foundation — Data-First Audit (2026-09-13)

Audit + design proposal only. No production code, DB migration, seed data, or commit made.
Every finding below is labeled **EXISTS TODAY**, **DERIVABLE FROM EXISTING DATA**,
**REQUIRES NEW DATA**, or **PROPOSED**, per instruction.

---

## 1. Complete existing capability-data inventory

**Local persistence (backend, SQLite, no ORM):** five hand-rolled sqlite stores exist —
`action_store.py`, `dev_plan_store.py`, `reportee_store.py`, `repositories/opportunity_store.py`,
`repositories/communication_store.py`. **None stores course metadata, skills, or
certifications.** The closest is `reportee_store.py`'s `skill_requests` table
(`course_id, course_name, requested_level, status`) — but it records a trainer's *request* to be
skilled, not an authoritative capability. **[EXISTS TODAY, but not capability data]**

**Qubits (`Qubits/qubitcourses.xlsx`):** a question-authoring tracker (completion %, selection
score, recency) for the quiz-bank pipeline, keyed by course *title* only, no course_id, no skill
content. **Not a capability source.** [EXISTS TODAY, wrong purpose]

**RMS course calls actually reachable:** `courseWithoutExam` (exam-required flag + vendor),
`courseTechnology` + `courseDomain` (course → technology → business-domain, ~21k rows — coarse
labels, not skills), `courseSyllabus` (a **PDF link**, not TOC text — code comment explicitly
states no endpoint in this RMS integration returns TOC content), `courseCatalogue`
(`course_id, course_name, course_code, vendor, duration_days`, links), `courseSchedule`
(dates/mode). **No RMS endpoint returns module/lesson/skill-level content for any course.**
[EXISTS TODAY: catalogue metadata + coarse taxonomy. REQUIRES NEW DATA: anything skill-level.]

**Certifications:** `_CERT_CATALOG` — a **hand-written dict of 30 Microsoft exam codes** with
title-match phrases, plus a separately hand-written prerequisite/adjacency map
(`"DP-203": ["DP-600","DP-700"]` etc.). The code's own comments admit this covers Microsoft only
and was blind to Cisco/AWS/Oracle/RedHat/SAP until a workaround was added. **No table maps
"course requires certification X"** — RMS itself reportedly 403s on that specific linkage.
[EXISTS TODAY: curated MS-only cert list. REQUIRES NEW DATA: non-MS coverage, course→cert table.]

**Delivery/assessment evidence:** "delivered" = a raw `Course Assignment` count from RMS
`trainerDetails`, not graded. `QubitsScore` is an RMS-supplied per-course number of undocumented
derivation. Feedback is a coarse 0/55/100 bucket, not per-skill. **No per-skill assessment/exam
score exists anywhere.** [EXISTS TODAY: coarse signals only. REQUIRES NEW DATA: real per-skill
evidence.]

**Opportunity documents:** purely a `document_status` string flag + a `documentation_mentioned`
list flag from text extraction — **no file storage, attachment table, or extractable requirement
text exists.** [REQUIRES NEW DATA — currently a UI/status concept with nothing behind it.]

**Course catalogue ground truth:** no static file enumerates which course codes SkillEdge
actually trains. DP-700/DP-600/AI-102/AZ-104/SC-300 appear in code only inside the hand-written
cert catalog, the prerequisite map, or hardcoded demo data — **none of that confirms live RMS
catalogue membership.** [REQUIRES VERIFICATION: a live `courseCatalogue`/`courseTechnology` probe
before treating any example course as real.]

**A finding not asked for but load-bearing:** `_capability_for()` (backend.py:3841-3896) contains
**hardcoded fallback capability/certification data for specific named individuals**
(e.g. a literal course/cert list keyed to two named trainers' email addresses), used when RMS
returns empty for them. This is fabricated-looking demo data live in production code today — see
§16 Risks. [EXISTS TODAY — and is itself a problem the foundation must not repeat.]

---

## 2. Source-of-truth matrix

| Data | Source | Authoritative? | Quality |
|---|---|---|---|
| Course code | RMS `courseCatalogue` | Yes | Good — structured field |
| Course title | RMS `courseCatalogue` | Yes | Good |
| Course technology/domain | RMS `courseTechnology`+`courseDomain` | Yes, but coarse | Group-level only, not skill-level |
| Course modules/TOC | **None** | — | Does not exist; syllabus is a PDF link only |
| Required skills (per course) | **None** | — | Does not exist anywhere |
| Trainer skills (per course) | RMS `trainerDetails` | Yes, but flat | Course-name string + numeric level, no skill ID |
| Skill level | RMS `trainerDetails.SkillLevel` | Yes | Numeric, but not tied to a canonical skill |
| Skill approval | RMS `trainerDetails.OfficiallyApproved` | Yes | Boolean flag, course-level not skill-level |
| Certification | Hand-written `_CERT_CATALOG` (MS only) | Partial | Curated but incomplete, no course linkage table |
| Delivery history | RMS `trainerDetails.Course Assignment` (count) | Yes | Coarse count, not evidence of specific skill use |
| Assessment | **None** (Qubits xlsx is question-bank ops, not trainer scores) | — | Does not exist |
| Availability | RMS off-bands / assignment dates | Yes | Good, already used |
| Location | RMS trainer profile fields | Yes | Good, already used |
| Language | RMS trainer profile fields | Yes | Good, already used |
| Delivery mode | RMS course/batch fields | Yes | Good, already used |

## 3. Missing-data matrix (explicit, per instruction — do not assume, this is confirmed absence)

| Missing | Confirmed absent from | Impact |
|---|---|---|
| Canonical skill taxonomy (IDs, aliases, relationships) | Entire repo, backend + Android | No structured matching possible without it |
| Course → required-skill mapping | RMS (no such endpoint), local DB (no such table), Qubits (wrong data) | `CourseCapabilityProfile` has nothing to be built from |
| Course → required-certification table | RMS (403 on that linkage per code comment), local DB | Cert-course linkage is inferred by title string-match only |
| Per-skill evidence/provenance | RMS (course-level only), local DB | Cannot say *why* a skill is verified beyond "RMS has a row" |
| Non-Microsoft certification coverage | `_CERT_CATALOG` (MS-only, hand-written) | Systematic blind spot for Cisco/AWS/Oracle/RedHat/SAP |
| Opportunity requirement documents | Opportunity store (opaque JSON, no file/table) | "Documents" evidence dimension has no data to evaluate |
| Live course-catalogue ground truth file | Nowhere — must be queried live each time | Cannot pick validation courses without a live RMS probe first |

---

## 4. Proposed canonical capability taxonomy model — PROPOSED

```
Capability { id, canonicalName, family, description }
CapabilityAlias { capabilityId, alias }
CapabilityRelationship { fromId, toId, type }   # see §5
```

Seed shape (illustrative, not final — depends on §11 sourcing):

```
DATA_ENGINEERING (family)
 ├─ MICROSOFT_FABRIC        aliases: Fabric, MS Fabric
 │   ├─ LAKEHOUSE           (CHILD_OF MICROSOFT_FABRIC)
 │   ├─ ONELAKE             (CHILD_OF MICROSOFT_FABRIC)
 │   └─ FABRIC_PIPELINES    (CHILD_OF MICROSOFT_FABRIC, RELATED_TO DATA_PIPELINES)
 ├─ APACHE_SPARK            aliases: Spark, PySpark, Spark SQL
 └─ DELTA_LAKE              (RELATED_TO APACHE_SPARK — not equivalent)
```

## 5. Proposed relationship semantics — PROPOSED

```
IS_A               — capability is a specific instance of a broader one
PARENT_OF / CHILD_OF — hierarchical containment (Fabric → Lakehouse)
RELATED_TO         — associated technology, NEVER counted as direct evidence of competence
REQUIRES           — a course/capability that presupposes another (prerequisite)
SPECIALIZATION_OF  — a narrower, deeper variant of a broader capability
```
`RELATED_TO` must always surface in the UI as "related evidence," never as a match — matching
this instruction's explicit constraint and the earlier Plan-audit's evidence hierarchy.

## 6. CourseCapabilityProfile design — PROPOSED (structure); data itself REQUIRES NEW DATA

```
CourseCapabilityProfile {
  courseId, courseCode, title,
  requiredCapabilities: [{ capabilityId, minimumLevel, mandatory: true }],
  preferredCapabilities: [{ capabilityId, minimumLevel, mandatory: false }],
  requiredCertifications: [certificationId],
  status: IMPORTED | SUGGESTED | REVIEW_REQUIRED | APPROVED | DEPRECATED,
  source, updatedBy, updatedAt
}
```
Given §3's finding (no module/skill RMS data exists), this profile **cannot be populated
authoritatively from RMS today** — it must start from admin-curated entries (§11-12), not an
automated import, for anything beyond the coarse `courseTechnology`/`courseDomain` labels.

## 7. TrainerCapabilityProfile design — PROPOSED

```
TrainerCapabilityProfile {
  trainerId,
  capabilities: [{
    capabilityId, proficiency, evidence: [CapabilityEvidence], lastVerified
  }]
}
CapabilityEvidence {
  type: VERIFIED_SKILL | CERTIFICATION | ASSESSMENT | DELIVERY_HISTORY | TRAINING_COMPLETION | MANAGER_APPROVAL,
  sourceSystem, sourceRecordId, capturedAt, raw (original RMS row, kept for provenance)
}
```
Per §3, only `VERIFIED_SKILL` (RMS `trainerDetails` row), `CERTIFICATION` (against the
MS-only `_CERT_CATALOG`), and a coarse `DELIVERY_HISTORY` (the `Course Assignment` count) are
populatable today. `ASSESSMENT` and `MANAGER_APPROVAL` (beyond the existing `OfficiallyApproved`
flag) have no data source yet — evidence types must be honest about which are live vs. aspirational
per trainer record, not silently defaulted.

## 8. Evidence/provenance model — PROPOSED, strength ordering per prior audit, unchanged here

```
1. Certification (structured code, MS-only today)
2. Completed delivery (Course Assignment count — coarse, no per-skill breakdown yet)
3. RMS-approved skill (OfficiallyApproved=yes + SkillLevel)
4. RMS-recorded skill, unapproved/no level
5. Related-skill inference (taxonomy) — surfaced as related, never as proof
6. Semantic/text similarity — candidate discovery only, never scored as evidence
```

## 9. Proposed relational schema — PROPOSED, SQLite, following the existing per-module store-class pattern (§13 detail below)

## 10. Legacy RMS migration strategy — PROPOSED

```
RMS trainerDetails row (course_name, vendor, SkillLevel, OfficiallyApproved)
  → normalize course_name against Capability/CapabilityAlias
  → candidate canonical capability + confidence score
  → confidence ≥ threshold AND course has an APPROVED CourseCapabilityProfile
       → auto-create TrainerCapability evidence (VERIFIED_SKILL), original RMS row kept as `raw`
  → confidence < threshold OR course has no APPROVED profile
       → land in CapabilityMappingReview queue, no TrainerCapability created yet
```
Original RMS rows are never deleted or rewritten — the mapping is additive, RMS stays the
system of record for the raw fact, this foundation adds a *canonical interpretation* of it.

## 11. Course-capability sourcing strategy — PROPOSED, priority order per instruction

1. **Existing authoritative data**: `courseCatalogue` (code/title/vendor) + `courseTechnology`/
   `courseDomain` (coarse group) — usable as a starting scaffold, not as skill-level truth.
2. **Structured curriculum/module data**: confirmed absent (§1, §3) — skip, nothing to source.
3. **Official catalogue metadata**: same as (1), already the ceiling of what RMS exposes.
4. **Controlled administrative mapping**: the primary real source, given (2)/(3) are dead ends —
   an admin/manager populates `CourseCapabilityProfile` per course through the review workflow
   (§12), starting from the coarse technology/domain tag as a hint, not a fabricated inference.
5. **Curated seed data for validation**: §14's representative course set, hand-built and
   explicitly marked `status = IMPORTED` pending admin `APPROVED` review — validation fixtures,
   never treated as production-authoritative until approved, exactly as instructed.

The syllabus PDF link (`courseSyllabus`) is a plausible **future** input to a text-extraction
suggestion step (§ "automated suggestions," instruction section 7) — but PDF-text extraction is
new engineering scope, explicitly optional/later, never a substitute for admin approval.

## 12. Administrative review workflow — PROPOSED

```
Course detail (existing screen) gains a "Capabilities" panel:
  Required / Preferred capabilities, required certifications, minimum proficiency
  Actions: Add / Remove / Edit / Approve / Review
  Each change stamped: source, updatedBy, updatedAt, status
Status lifecycle: IMPORTED → SUGGESTED → REVIEW_REQUIRED → APPROVED (or → DEPRECATED)
```
Only `APPROVED` profiles feed high-confidence allocation ranking (§9 of the prior Plan audit) —
`SUGGESTED`/`REVIEW_REQUIRED` profiles are visible to admins but excluded from scoring, per
instruction's "AI suggestion ≠ verified capability requirement."

## 13. Representative validation-course selection — PROPOSED, pending live verification

Per §1's finding that **no static ground truth confirms which course codes are live**, the
correct next step (not yet done — explicitly flagged, not skipped) is a live
`courseCatalogue`/`courseTechnology` probe for DP-700, DP-600, AI-102, AZ-104, SC-300 (the
examples used throughout this design thread) plus 2-3 non-Microsoft courses if the live
catalogue contains any, before committing to them as validation fixtures. **Do not treat their
appearance in `_CERT_CATALOG` or the prerequisite map as proof they're deliverable courses** —
that dict is a certification list, not a course catalogue.

## 14. Shared-domain integration map

Unchanged from the prior Plan-matching audit's §K — one capability engine, five consuming
questions (Plan, Opportunity, Trainer 360, Courses, Upskilling), now explicitly extended per this
instruction to include **Today** ("is an upcoming allocation genuinely exposed?" — reads
`missingSkills`/`EligibilityResult` for batches on the Today horizon) and **Reports**
("where are our capability risks?" — aggregates `EvidenceEvaluator` gaps org-wide). Seven
consumers total, one capability graph, per instruction's "one capability truth" requirement.

## 15. Opportunity documentation-drift findings — RESOLVED this session

Investigated per instruction §12. Conclusion: **Option C** — the richer evidence model
(dates/location/mode/participants/documents) described in `AI/DECISIONS.md`'s 2026-09-10 entry
was **never implemented**, anywhere in the repo, at any point in git history.
- `match_opportunity` (backend.py:12426-12554) has had exactly one commit touch it since it was
  created (`00025ed`, 2026-09-10, the same day as the decision entry) — that commit's original
  diff already shows only the course/certification-evidence loop and the international+critical
  escalation rule. No richer version ever existed to regress from (ruling out Option B).
- No other file/function in the repo builds date/location/mode/participant/document evidence
  items for opportunity matching (ruling out Option A) — grepped repo-wide, zero hits.
- No superseding function name exists (ruling out Option D).
- **Conclusion: the decision log recorded a design intent at commit time; only part of it
  shipped.** `AI/DECISIONS.md`'s entry should be corrected to state which evidence dimensions are
  actually live (course/certification match, international+critical escalation) versus intended-
  but-not-built (dates, location/travel, mode, participants, documents, skill level) — a doc
  correction, not a behavior change, per instruction's "update documentation only after factual
  resolution."

## 16. Risks

- **Course-capability data does not exist and cannot be derived from RMS** (§1, §3) — the single
  largest risk to this whole initiative is treating admin curation as a formality; it is the
  *entire* data source for anything beyond coarse technology/domain tags. Understaff or rush this
  and the taxonomy either stays empty or gets rubber-stamped without real review, reproducing
  today's string-matching problem one layer deeper.
- **Certification coverage is Microsoft-only by construction** (`_CERT_CATALOG`) — any course
  requiring a Cisco/AWS/Oracle/RedHat/SAP certification will show a false "no certification
  requirement" today; this foundation must not silently inherit that blind spot into
  `requiredCertifications`.
- **Hardcoded per-individual fallback capability data exists in production** (`_capability_for()`,
  backend.py:3841-3896, two named trainers). This predates this initiative but sits directly in
  the code path this foundation replaces — it should be removed or converted to a properly
  provenance-tagged (`source="manual_fallback"`) record as part of this work, not left as silent
  fabrication feeding whatever replaces `_capability_for()`.
- **No static course-catalogue ground truth** means every validation-course choice needs a live
  RMS probe first (§13) — skipping that risks building fixtures around courses that aren't
  actually in the live, trainable catalogue.
- **Opportunity "documents" has zero backing data** — if Opportunity matching is expected to use
  document evidence per the original (aspirational) decision, that requires new file-storage
  scope entirely separate from the capability taxonomy work; don't conflate the two efforts.

## 17. Phased implementation plan

```
Phase 0 — Verification (no code): live-probe courseCatalogue for the candidate validation
          courses; confirm which are real. Correct AI/DECISIONS.md per §15.
Phase 1 — Schema + admin curation surface: Capability/CapabilityAlias/CapabilityRelationship
          tables; CourseCapabilityProfile + review workflow (§12), no matching engine yet.
Phase 2 — Seed 5-10 verified validation courses (§13) through the admin workflow to APPROVED,
          by a human with real course knowledge — not generated, not inferred.
Phase 3 — TrainerCapabilityProfile + legacy RMS migration (§10) against the now-seeded taxonomy,
          confidence-scored, ambiguous rows routed to review.
Phase 4 — Matching engine (per the separately-approved Plan audit) built against real, approved
          data from Phases 1-3 — not before.
Phase 5 — Shared-domain rollout to the other six consumers (§14), one at a time.
```

---

**Awaiting approval before any implementation. No schema created, no data inserted, no code
changed. Recommending Phase 0 (live catalogue verification + DECISIONS.md correction) as the
first concrete action, since it requires no design decisions, just fact-checking.**
