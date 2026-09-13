# Plan / Trainer Allocation Intelligence — Audit & Proposed Design (2026-09-13)

Audit + proposal only. No code changed. Implementation gated on evidence review below (§M) and explicit approval.

## A. Current Plan matching algorithm

There is no structured skill-matching engine today. The entire pipeline is **course-name string
similarity**, computed once (`_match_score`) and reused everywhere as if it were skill evidence:

```python
# backend.py:1474-1493
def _match_score(batch_course, batch_vendor, cap_course, cap_vendor):
    a, b = _norm(batch_course), _norm(cap_course)
    if a == b: return 100                                    # exact string match
    ca, cb = _course_code(batch_course), _course_code(cap_course)
    if ca and ca == cb: return 92                             # course-code string match
    ta, tb = _tokens(batch_course), _tokens(cap_course)
    jaccard = len(ta & tb) / len(ta | tb)                     # word-overlap on course TITLE
    score = jaccard * 78
    if _norm(batch_vendor) == _norm(cap_vendor): score += 10  # vendor-name string match
    return int(min(100, round(score)))
```

This is exactly the anti-pattern the instruction names: "DP-700" in both places, or title-word
overlap, is treated as capability evidence. There is no skill ID, no taxonomy lookup, no
evidence/provenance anywhere in this function.

## B. Exact files/functions responsible

All in `backend.py`:

| Function | Lines | Role |
|---|---|---|
| `_match_score` | 1474-1493 | Atomic course-name-string comparator (100/92/≤78+10 scoring) |
| `_rank_batch` | 1757-1922 | Ranks a team for one batch; `best = max(_match_score(...))` per candidate, no independent skill check |
| `_suitability_components` | 1661-1711 | Weighted blend — `skill` input (0.35 weight) is the same `_match_score` output re-used |
| `_team_course_skill` | 1714-1754 | "Has this skill?" == `_match_score(...) >= 60` |
| `v2_reportee_demand` | ~7115 | Same `_match_score >= 60` pattern, third call site |
| `_cert_intelligence` | 2412+ | The one structured piece — works on certification **codes**, not course-name text |
| `_runway_match_score` | 11400 | A second, separate, near-duplicate string-based scorer for career-runway matching |
| `match_opportunity` | 12425-12554 | Opportunity "course match" evidence = cert-code / technology-name string containment |

Four independent call sites (`_rank_batch`, `_team_course_skill`, `v2_reportee_demand`,
`_suitability_components`) all funnel through the same one string comparator — good news for
migration: replacing `_match_score`'s *meaning* (not necessarily its call sites on day one) fixes
all four at once.

## C. Current trainer capability data sources

`_skills(email)` (`backend.py:1298-1325`) — the single source of trainer "skills," from RMS
`trainerDetails`. Shape, one row per course the trainer has any RMS record for:

```
{ course, course_name, vendor, qubits_score, skill_level, approved (OfficiallyApproved=="yes"),
  future_skill, delivered }
```

That's it: **a flat list of (course name, vendor, numeric level, approval flag)** — no skill IDs,
no evidence trail beyond "RMS has a row for this course." `skill_courses`/`skill_vendors`
(2883-2884) are just projections of the same rows. `_skill_register` (2647) is a parallel
`{course_id, course_name}` list keyed by employee code, same shape. `_cert_intelligence` is the
one place structured certification **codes** (not course names) are used.

**No taxonomy, alias table, or canonical-skill mapping exists for matching.** The one taxonomy-
shaped thing found, `_course_taxonomy()`/`_taxonomy_for_course()` (2331-2409, from RMS
`courseTechnology`+`courseDomain`), maps course → business domain/technology **group** for
dashboard reporting only — it is never read by `_match_score`, `_rank_batch`, or
`match_opportunity`. Building on it is possible but it is coarse (domain-level, not skill-level)
and currently orphaned from matching entirely.

## D. Current course requirement data sources

A course, as matching sees it, is **a name + vendor string pair** — `batch.get("course_name","")`
+ `batch.get("customer","")`. Nothing richer is consulted by the ranking functions.

A real curriculum endpoint exists — `v2_course_curriculum` (13754-13831) returns
`modules/content_urls/schedule_info/has_curriculum` — but it is display-only (feeds
`CourseCurriculumSheet.kt`), never referenced by any ranking function. No local
JSON/CSV/DB course→required-skills mapping exists anywhere in the repo. **The
`CapabilityRequirementProfile` this instruction asks for has no backing data source today** —
this is the single most important finding (see §M and §L).

## E. Where course-code/title matching stands in for skill matching

Every one of these is a live production call site, not dead code:

- `backend.py:1730` — `if _match_score(course, vendor, c["course"], c["vendor"]) >= 60:`
- `backend.py:1775` — `s = _match_score(course, vendor, c["course"], c["vendor"])`
- `backend.py:7115-7116` — `best = max(best, _match_score(...)); if best >= 60:`
- `backend.py:12482` — `elif any(c in t.upper() for c in cert_codes):` (opportunity matching, substring containment)

No fuzzy-matching library (`difflib`, `fuzzywuzzy`, `rapidfuzz`) is used anywhere — the "fuzzy"
behavior is entirely the home-grown Jaccard/token-overlap in `_match_score`.

## F. Proposed canonical taxonomy (design, pending data audit — see §M)

```
SkillTaxonomy
  CanonicalSkill { id, name, family, parentId?, relatedIds[] }
  SkillAlias     { skillId, alias }              # "PySpark" -> APACHE_SPARK
```

Seed hierarchy example (Data Engineering family), matching the instruction's DP-700 illustration:

```
DATA_ENGINEERING
 ├─ MICROSOFT_FABRIC   (aliases: Fabric, MS Fabric, Fabric Analytics)
 │   ├─ LAKEHOUSE
 │   ├─ ONELAKE
 │   ├─ FABRIC_PIPELINES   (related: DATA_PIPELINES)
 │   └─ FABRIC_NOTEBOOKS
 ├─ APACHE_SPARK       (aliases: Spark, PySpark, Spark SQL)
 └─ DELTA_LAKE
```

**Where this must come from:** not hand-authored per-course in Kotlin (explicitly forbidden).
Options, in order of preference, pending §M's data audit: (1) a maintained backend table seeded
from the existing `_course_taxonomy()` domain/technology groups plus a manually curated
skill-alias layer for the highest-volume courses first; (2) an admin-maintainable
`course_capabilities` table (matches the instruction's "controlled administration" ask) rather
than a static file, so Ops can correct/extend it without a deploy. This is new data entry either
way — it does not exist today (§D) and cannot be inferred from current fields without
fabrication.

## G. Proposed scoring model (bands, not false precision)

```
EXCELLENT FIT   ≥95% mandatory coverage, high proficiency, recent verified evidence
STRONG FIT      100% mandatory coverage, ≥1 supporting evidence item
PARTIAL FIT     mandatory coverage 40-99%, or coverage met but evidence thin/stale
WEAK FIT        mandatory coverage <40%
NOT ELIGIBLE    hard eligibility blocker present (§H) — never reaches capability scoring
```

Internal explainable weighting (example, per instruction's caveat — **must be validated against
what §C's data actually supports before being fixed**, since e.g. "certification evidence" only
exists as cert-code data, not per-skill certification evidence):

```
mandatory skill coverage      45%
proficiency (RMS SkillLevel)  15%
preferred capability coverage 10%
certification evidence        10%   # only as granular as _cert_intelligence's codes allow
relevant delivery evidence    15%   # from _skills()'s "delivered" flag + assignment history
evidence recency               5%   # needs a "last verified" date — not currently captured per-skill
```

Two of six weighted inputs (certification evidence, evidence recency) need data that is coarser
or absent today — flagged honestly rather than assumed away.

## H. Proposed eligibility rules (hard gate, separate from capability score)

```
EligibilityResult { eligible: Bool, blockers: [BlockerReason] }

BlockerReason =
  MissingMandatoryCertification
  LanguageMismatch          # already exists today: backend.py:1828-1831 language hard-zero
  LocationOrVisaRestriction
  DeliveryModeUnsupported
  HardScheduleConflict      # already exists as availability data in _rank_batch
  DoNotContactBlock         # already exists: "DNC-block" adjustment, backend.py:1869-1873
```

Half of these already exist as ad hoc checks inside `_rank_batch` — this is consolidation into an
explicit `EligibilityPolicy`, not new business logic invented from nothing.

## I. Proposed evidence hierarchy (strongest → weakest, per instruction's provenance requirement)

```
1. Verified certification (structured code, from _cert_intelligence)
2. Completed delivery of this exact capability (RMS assignment history, "delivered" flag)
3. RMS-recorded skill with OfficiallyApproved = yes and a numeric SkillLevel
4. RMS-recorded skill, not yet approved / no level
5. Related-skill inference via taxonomy (parent/child or curated related-skill — NEVER counted as direct evidence, always surfaced as "related," per instruction's Delta Lake example)
6. Semantic/textual similarity — candidate discovery only, NEVER scored as evidence (per instruction's hard constraint)
```

## J. Proposed domain classes/interfaces

```
domain/matching/
  models/        CapabilityRequirementProfile, SkillRequirement, TrainerCapabilityProfile,
                 TrainerSkill, EligibilityResult, CapabilityMatchResult,
                 AllocationSuitabilityResult, MatchExplanation
  policies/      EligibilityPolicy
  strategies/    ExactSkillMatchStrategy, AliasMatchStrategy, HierarchyMatchStrategy,
                 RelatedSkillMatchStrategy, SemanticCandidateStrategy (discovery-only)
  services/      SkillNormalizer, CapabilityRelationshipResolver, CapabilityMatcher,
                 EvidenceEvaluator, AllocationSuitabilityEvaluator, MatchExplanationBuilder
  usecases/      MatchTrainerToRequirementUseCase, RankTrainersForRequirementUseCase
repositories (interfaces, domain/repositories):
  SkillTaxonomyRepository, CourseCapabilityRepository, TrainerCapabilityRepository
```

Backend mirrors this under `backend/domain/matching/` — the engine lives once, server-side (RMS
access, taxonomy data, and evidence all live there today); Android's `TrainerCapabilityRepository`
etc. are thin clients over the backend's computed result, not a second implementation of the
scoring logic. This matches §K's single-engine requirement structurally, not just by convention.

## K. Reuse plan across Plan / Opportunity / Courses / Trainer 360 / Upskilling

One engine, five questions over the same capability graph — confirmed feasible because all five
already read from the same two underlying sources (`_skills()`/RMS trainerDetails for trainers,
a course-capability source still to be built for courses):

| Feature | Question | Reuses |
|---|---|---|
| Plan | Which trainer can deliver this course? | `RankTrainersForRequirementUseCase` |
| Opportunity | Can we deliver this requirement? | `MatchTrainerToRequirementUseCase` fed by opportunity's implied `CapabilityRequirementProfile`, replacing `match_opportunity`'s current string-containment "course match" evidence item only — its other evidence dimensions (dates/location/mode/docs, per DECISIONS.md 2026-09-10) are untouched, orthogonal, and **not currently implemented in code despite the decision log describing them** (real drift found this session — see §L) |
| Trainer 360 | Which courses is this trainer ready for? | Same `CapabilityMatcher`, inverted query (trainer → all requirement profiles) |
| Courses | Do we have enough trainer coverage? | Aggregate `RankTrainersForRequirementUseCase` results across a course's demand |
| Upskilling | What capabilities are missing? | `EvidenceEvaluator`'s `missingSkills`/`partiallyMatchedSkills` output, aggregated |

## L. Migration risks

- **`CapabilityRequirementProfile`'s core input — course → required skills — has no data source
  today (§D).** This is not a refactor of existing logic; it is new data that must be sourced or
  curated before the engine can produce a single real result. Any implementation before this
  exists would either (a) fabricate skill requirements from course titles — exactly what the
  instruction forbids — or (b) ship an empty/no-op taxonomy that silently falls back to today's
  string matching, which is worse than being explicit about the gap.
- **Four production call sites** (`_rank_batch`, `_team_course_skill`, `v2_reportee_demand`,
  `_suitability_components`) depend on `_match_score`'s current behavior — replacing its meaning
  changes ranking order across Plan, the reportee-demand view, and the suitability blend
  simultaneously. Needs a shadow-mode comparison (compute both scores, log divergence, don't
  switch ranking) before cutover, not a direct swap.
- **Decision-log drift found:** `AI/DECISIONS.md`'s 2026-09-10 entry describes `match_opportunity`
  as producing a full evidence set (course match, dates, location/travel, mode, participants,
  documents, skill level, international/critical flags). The actual function only implements the
  course-match/cert-code evidence item and the international+critical escalation flag — the
  other evidence dimensions are not in the code. Either the decision was never fully implemented,
  or it regressed. This should be corrected in DECISIONS.md and resolved (implement the missing
  evidence dimensions, or update the record) independently of the matching-engine rebuild.
- **Certification and recency evidence are coarser than the proposed scoring model wants** (§G)
  — proficiency/recency-weighted scoring needs a "last verified" timestamp per skill that RMS's
  `trainerDetails` does not currently provide per-course.

## M. Tests to prove the old false-match behaviour is eliminated

Per the instruction's required set, plus the two critical cases named explicitly:

1. Exact canonical match (Fabric ↔ Fabric)
2. Alias match (PySpark ↔ APACHE_SPARK)
3. Parent/child relationship (Fabric Pipelines satisfies Data Pipelines requirement)
4. Related-skill partial match, surfaced as PARTIAL not MATCH (Delta Lake example)
5. Missing mandatory capability → correct band (WEAK/NOT ELIGIBLE, never STRONG)
6. Certification blocker → `EligibilityResult.eligible = false` with explicit `blockers`
7. Strong technical fit + unavailable → `CapabilityMatchResult` STRONG, `AllocationSuitabilityResult` LOW, scores never blended into one number
8. Available trainer, weak technical fit → correctly ranked below an unavailable strong-fit trainer's capability score, but explained separately
9. Multiple trainers ranked correctly, deterministic tie-break
10. No qualified trainer → explicit empty result with reason, not a lowest-score trainer surfaced as if acceptable
11. Stale evidence → recency band downgrade, explained
12. Unsupported semantic-only similarity → candidate surfaced for review, never auto-scored as a match
13. **Critical:** trainer profile contains literal string "DP-700" but has no capability evidence → NOT a strong match (this must fail today against `_match_score`'s exact-string-equality branch, which is the regression test that proves the old behavior is gone)
14. **Critical:** trainer has strong Fabric/Spark/Lakehouse evidence but was never tagged "DP-700" → ranks as strong capability fit anyway (this must fail today, since `_match_score` requires string/token overlap with the course name)

Tests 13 and 14 are the two the instruction calls out by name — they should be written **first**,
against the *current* `_match_score`, to concretely demonstrate today's false-match and
false-negative behavior before any new code is written, then re-run against the new engine as the
acceptance gate.

---

## Where this leaves implementation

Per the instruction's own sequencing (§ IMPLEMENTATION SEQUENCE, steps 1-4 = audit, which this
document completes) and its closing line — **"proceed with implementation only if the evidence
needed by the design actually exists"** — step 6 (canonical taxonomy) and step 7
(`CourseCapabilityProfile`) cannot be populated with real data today without curation work that
hasn't happened yet (§D, §L). Recommending: approve a small, explicit **data-sourcing task**
(seed taxonomy + course-capability table for a first slice of courses — DP-700 and a handful of
others already used as examples throughout this thread) as step 0 of implementation, before any
Kotlin/Python matching code is written, so the engine is built against real seed data rather than
placeholders.

**Awaiting approval on this audit, and specifically on how the taxonomy/course-capability seed
data should be sourced, before implementation begins.**
