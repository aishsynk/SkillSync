# Phase 0 — Capability Foundation Fact Verification (2026-09-13)

Fact verification only. No schema, no mappings, no scoring, no seed data, no framework, no
release. All claims below are live-verified against production RMS or exact source lines — none
inferred.

## 1. Verified RMS courses and metadata

Live-called RMS `courseCatalogue` (role "Get Course Name", key 70) and `courseTechnology`
(role "Course & Technology List", key 114) directly, via the exact credential wiring already in
`backend.py`'s `_APIS`. Results:

- `courseCatalogue`: **200 OK, 12,817 rows.** Fields confirmed live:
  `Cid, Course, course_code, vendor_of_course, course_duration, Course_Page, TOC`.
- `courseTechnology`: **200 OK, 22,323 rows.** Fields confirmed live:
  `technology_name, course_name, course_id, technology_id`.

All five example course codes used throughout this design thread are **confirmed real, live,
trainable courses** — not assumptions:

| Code | Cid | Title (as RMS returns it) | course_code | Vendor | Duration | Technology tags | Syllabus (TOC PDF) |
|---|---|---|---|---|---|---|---|
| DP-700 | 18301 | DP-700T00: Microsoft Fabric Data Engineer | DP-700 | Microsoft | 4 days | Data Engineer, Microsoft Fabric, Microsoft Data Engineering | [PDF](https://www.koenig-solutions.com/CourseContent/custom/20241219421-DP700ImplementingDataEngineeringSolutionsUsingMicrosoftFabric.pdf) |
| DP-600 | 15509 | DP-600T00: Microsoft Fabric Analytics Engineer | dp-600t00 | Microsoft | 4 days | Azure Database, Microsoft Fabric, Microsoft Data Engineering, Big Data | [PDF](https://www.koenig-solutions.com/CourseContent/custom/20241026168-DP600T00MicrosoftFabricAnalyticsEngineer.pdf) |
| AI-102 | 9716 | AI-102T00: Develop AI Solutions in Azure | AI-102T00 | Microsoft | 5 days | Enterprise AI Architecture | [PDF](https://www.koenig-solutions.com/CourseContent/custom/20251110119-Ai1022025.pdf) |
| AZ-104 | 9055 | AZ-104T00-A: Microsoft Azure Administrator | AZ-104T00 | Microsoft | 4 days | Microsoft Azure, Azure Infrastructure | [PDF](https://www.koenig-solutions.com/CourseContent/custom/2026119659-AZ104T00AMicrosoftAzureAdministrator251.pdf) |
| SC-300 | 9748 | SC-300T00: Microsoft Identity and Access Administrator | SC-300T00 | Microsoft | 4 days | Microsoft IAM | [PDF](https://www.koenig-solutions.com/CourseContent/custom/2024122329-SC300TOC.pdf) |

Non-Microsoft courses confirmed live in the same catalogue (for domain diversity):

| Code/Name | Cid | Title | Vendor | Duration |
|---|---|---|---|---|
| CCNA | 11405 | Cisco Certified Network Associate (200-301 CCNA) Extended | Cisco Non Standard | 8 days |
| AWS SAA | 899 | AWS Certified Solutions Architect - Associate (Architecting on AWS) | AWS | 3 days |
| CISSP | 742 | Certified Information Systems Security Professional (CISSP) | ISC2 | 5 days |

**Data-quality notes surfaced by the live probe itself** (not assumed): `course_code` is
frequently blank or lowercase/inconsistent (e.g. DP-600's real catalogue entry carries
`"dp-600t00"`, not `"DP-600"`); duplicate/near-duplicate title rows exist per code (e.g. two
DP-700 rows — the real course and a 1-day "Exam Prep" variant); `Course_Page`/`TOC` are
sometimes `null`. Any course-capability sourcing work must dedupe/normalize against `Cid`
(the stable numeric ID), never against the free-text title or the inconsistent `course_code`.

## 2. Selected validation courses (8, confirmed real, spanning 5 domains)

```
DP-700  — Data Engineering (Microsoft Fabric)
DP-600  — Data Analytics (Microsoft Fabric)
AI-102  — AI Solutions (Azure)
AZ-104  — Cloud Infrastructure/Admin (Azure)
SC-300  — Identity & Access Management (Microsoft)
CCNA    — Networking (Cisco, non-Microsoft)
AWS SAA — Cloud Architecture (AWS, non-Microsoft)
CISSP   — Security certification (vendor-neutral, ISC2)
```
These are validation fixtures only, per instruction — not seeded into any table this phase.

## 3. Available syllabus/curriculum sources

Confirmed, live: every catalogue row can carry a `TOC` field — but **it is a link to a marketing
PDF on koenig-solutions.com, not structured module/lesson/skill data** (consistent with the
earlier audit's finding that no RMS endpoint returns TOC *content*). All five candidate courses
above have a live TOC PDF link captured. This is usable only as a future **input to a manual or
text-extraction curation step** (instruction §7's "automated suggestions" path) — it is not, by
itself, an authoritative capability source, and no PDF was parsed or acted on in this phase.

## 4. `DECISIONS.md` correction — done

Corrected the 2026-09-10 "Opportunity matching is evidence-based" entry in-place: added a
2026-09-13 correction note stating what's actually live in `match_opportunity`
(course/certification evidence item + international+critical escalation only) versus what was
only ever a design intent (dates/location/mode/participants/documents evidence — never built,
never regressed, confirmed via the only commit that has ever touched the function). Original text
preserved for history, not deleted.

## 5. `_capability_for()` findings — audited, removal plan defined, not yet executed

Worse than the prior pass's summary suggested. `backend.py:3818-3917` contains:
- **8 named trainer email addresses**, each with a fully fabricated list of courses
  (name/vendor/qubits_score/skill_level/approved/delivered — all invented numbers) and a
  fabricated held-certifications list, substituted in whenever RMS returns no data for them.
- A **generic fallback** applied to *any other* trainer with empty RMS capability data: a single
  invented `AZ-104` course record and `{"name": "AZ-104"}, {"name": "MCT"}` certifications.
- **Blast radius, confirmed by call-site grep:** three live endpoints —
  `/api/v2/data/team-capability` (+ legacy `/api/data/team-capability`),
  `/api/v2/capability/portfolio`, `/api/v2/capability/cert-intel`.
- **This directly violates the existing "Nah-fabrication rule"** already recorded in
  `AI/DECISIONS.md` (2026-09-10): capability screens must show honest empty states, never guessed
  values.

**Removal plan (defined now, not executed — per this phase's DO NOT list):** when `_skills()`/
`_certifications()` return empty, `_capability_for()` should return the same zero/empty shape the
rest of the capability surface already uses (`courses: [], course_count: 0, certification:` with
an explicit "no data" status) instead of the `known_courses`/`known_certs` dicts. This is a
deletion (~75 lines) plus a shape check, not a redesign — but it is explicitly **in scope for the
capability-intelligence foundation implementation phase**, not done here, since touching
`_capability_for()`'s output shape belongs with the broader `TrainerCapabilityProfile` work, not
as an isolated hotfix that could conflict with it.

## 6. Remaining data gaps (unchanged from the prior audit, now with live numbers)

- **No course → required-skill mapping exists anywhere**, confirmed again live: `courseCatalogue`
  and `courseTechnology` — the two richest course sources actually reachable — carry catalogue
  metadata (title/vendor/duration/TOC-link) and coarse technology tags only, never skill-level
  requirements.
- **`course_code` is unreliable as a join key** (blank/inconsistent case, confirmed live this
  phase) — `Cid` must be the stable identifier any future `CourseCapabilityProfile` keys on.
- Certification-requirement-per-course, per-skill evidence, and opportunity documents remain
  exactly as absent as the prior audit found — nothing in this phase's live probing surfaced a
  hidden source for any of them.

## 7. Exact recommendation for building the authoritative Course → Capability source

Given confirmed live data (catalogue metadata + coarse technology tags + a PDF syllabus link) and
confirmed absence of anything richer:

1. **Do not attempt automated skill extraction from the TOC PDFs yet** — that's new engineering
   scope (PDF fetch + text extraction + suggestion pipeline) and produces `SUGGESTED`, not
   `APPROVED`, data per the earlier foundation design (§7/§12 of that document) — worth doing
   later, not as the seed mechanism.
2. **Seed source = admin/human curation**, starting from the 8 validation courses above, each
   already carrying live `Cid`, verified title, vendor, and a real syllabus PDF a curator can
   actually read. A person with course knowledge writes the `CourseCapabilityProfile` for these 8
   by hand, through the review workflow already designed (status `IMPORTED → REVIEW_REQUIRED →
   APPROVED`) — not by an agent inferring skills from the title, which is exactly what this whole
   initiative exists to stop.
3. **Key every mapping on `Cid`**, never on title or `course_code`, given the data-quality issues
   surfaced in §1.
4. Proceed to schema design (the previously-proposed `Capability`/`CourseCapabilityProfile`
   tables) only once this curation step for the 8 validation courses is scheduled/assigned —
   building the schema before anyone is lined up to populate it just produces empty tables.

---

## Status

**PHASE 0 VERIFIED — READY FOR CAPABILITY FOUNDATION IMPLEMENTATION**, contingent on:
- Confirming who will do the human curation of the 8 validation courses' `CourseCapabilityProfile`
  (this phase deliberately did not write one, per "do not seed DP-700 or any other course
  manually").
- Explicit approval to proceed to Phase 1 (schema + admin curation surface, no data yet) from the
  previously-proposed phased plan.

No schema created. No data inserted. No `_capability_for()` code changed yet (finding documented,
removal deferred to the implementation phase as scoped in §5). No commit, push, or version bump.
