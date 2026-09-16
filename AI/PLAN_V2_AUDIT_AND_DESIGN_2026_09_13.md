# Plan V2 — Audit & Design (2026-09-13)

Audit + design proposal only. No Compose, no backend matching logic, no capability-draft
approvals, no percentages implemented, no commit/push/release. Grounded in code, not docs — every
claim below traces to a file:line from a direct code read this session.

---

## 1. Current Plan audit

**"Plan" is not a distinct product today — it's a label.** `HomeTab.DEMAND` is one enum value,
shown as "Plan" for managers and "Demand" for trainers (`MainScreen.kt:757`); it routes to a
single screen, `AllocationDeskContent` (`AllocationDeskScreen.kt`) driven by `AllocationViewModel`.
There is no separate Plan IA today — Pipeline Radar and Capacity Runway are *not* tabs, they're
screens pushed from elsewhere with no shared entry point.

What the Allocation Desk actually contains, in order: an 8-week `CapacityPlanningCard` (real bar
chart), a `GrowTeamCard` upskilling nudge, a search+filter+lens-chip demand header with a live
"RADAR (20s)" auto-refresh badge, a stat row (Global/Priority/At risk/Best match), a
Strong-fit/Partial/No-cover `DistributionBar`, then batches grouped by delivery mode
(FMAT→ILT→ILO) — **explicitly never re-sorted by match%** (a real, good existing constraint,
`AllocationDeskScreen.kt:121-124`).

## 2. Historical feature comparison

| Feature | Old Plan (pre-session) | Current Plan | Data source | Real/Partial/Fake |
|---|---|---|---|---|
| Batch list, mode-grouped | Yes | Yes | `allocation_desk` | Real |
| Match % on batch card | Yes | Yes | `_match_score` (string-based) | **Fake as "capability," real as a computed number** |
| Per-candidate sub-scores (Skill/Ready/Avail/Cert/Lang) shown as raw numbers | Yes | Yes | `_suitability_components` | Fake as capability evidence, real as a blend |
| 8-week capacity outlook | Yes | Yes | `/capacity plan` | Real |
| Coverage % on demand detail | Yes | Yes | `relevance` (from `_match_score`) | Fake as capability |
| Pipeline Radar "certified" flag | Yes | Yes | `_pipeline_build` line 14152 | **Fabricated** — true because course has an exam code, not because the trainer holds it |
| Pipeline Radar skill level | Yes | Yes | `_pipeline_build`, `s.get("skill_level", 8)` | **Fabricated default** — unknown silently becomes 8 |
| Capacity Runway (weeks/gap/upskilling) | Yes | Yes | `/planning/runway` | Real |
| "Assign trainer to batch" action | No | No | — | **Missing** — only RMS skill-marking dialogs exist today, no actual assignment action |
| Search wider trainer network | Yes | Yes | `getAlternativeTrainers` | Real |
| Eligibility gates (leave/DNC/visa/skill floor) | Yes | Yes | `GatedCandidatesSection` | Real |
| Canvas/heatmap/timeline chart | No | No | — | Never existed |
| Explainable STRONG/PARTIAL/WEAK band (no %) | No | No | — | **Never existed — always a number** |

## 3. Data/API inventory

- `allocation_desk` (backend.py:4852) → `_rank_batch`/`_match_score` — string-based, previously audited, unchanged.
- `/api/v2/planning/pipeline` → `_pipeline_build` (14089-14180) — real RMS pulls, **but contains a fabricated `certified` flag and a silent skill-level default**.
- `/api/v2/planning/runway` → `_capacity_runway_build` (11373-11547) — real computation throughout; reuses the same string-matching (`_runway_match_score`/`_runway_teaches`) as `_match_score`.
- `getAlternativeTrainers`, `getDemandContext` (v2 demand context, typed verified/unverified) — real, already honest (zero rows reported as unverified, per `test_v2_demand_context.py`).
- Capability foundation (this session's Phase 1): `CapabilityService`/`CapabilityStore` — exists, evidence-gated, **zero DRAFT profiles are APPROVED, nothing in Plan may read from it yet.**

## 4. Problems in current matching logic (confirmed live in the UI, not just backend)

Beyond the already-audited `_match_score` string comparator, **percentages and raw sub-scores are
shown directly to managers today** — `AllocationDeskScreen.kt` shows `${b.int("relevance")}%` and
`Chip("${c.int("match")}%")`; `BatchDetailScreen.kt` shows the same plus a headline coverage `%`
and a raw `"Skill X · Ready Y · Avail Z · Cert W · Lang V"` line. This is exactly the "black-box
score" pattern the capability foundation exists to retire — Plan V2 must remove every one of
these, not just avoid adding new ones. Additionally, Pipeline Radar's `certified: True` (course
has an exam code) and `skill_level: 8` (unknown defaulted, not reported unknown) are the same
fabrication class as the just-fixed `_capability_for()` bug — **not yet fixed**, flagged here for
a future scoped fix, not fixed in this design pass.

## 5. New Plan information architecture

Four real destinations under one Plan module (not one screen wearing many hats):

```
Plan
 ├─ Needs Allocation   (today's Allocation Desk, redesigned)
 ├─ Scheduled          (NEW — confirmed deliveries, operational view)
 ├─ Runway             (existing Capacity Runway, restyled to Design V2, extended)
 └─ Coverage           (NEW — explainable coverage bands, replaces Pipeline Radar's role)
```
Segmented control at the top (per the harvested-pattern work from earlier this session), Design V2
tokens throughout — no new visual system.

## 6. Needs Allocation design

Keeps: mode-grouping, never-resort-by-match, the eligibility gates, search-wider-network, the
8-week capacity strip (moved to Runway — see §8, so Needs Allocation stays focused on *this
week's* open items). Removes: the `%` stat row, `DistributionBar`'s implied precision (replaced
with a 4-band Coverage summary — see §9), all raw match/sub-score numbers. Each demand row shows:
course, client, dates, mode/location, a **Coverage band** (Covered/Partial/Uncovered/Unknown —
§9), priority, and blockers as short text (not a score). Tap → Demand Detail (§10).

## 7. Scheduled design

New. Confirmed/allocated deliveries the manager already has covered — currently invisible as a
distinct view (they're just absent from the Needs-Allocation list). Trainer drill-down and
delivery drill-down reuse Trainer 360 / Batch Detail respectively (no new detail screens).
Conflict detection: surface a delivery whose trainer has an overlapping assignment (data already
computed for eligibility gating — reuse `GatedCandidatesSection`'s conflict logic, don't
reimplement). Reallocation path: opens the same Demand Detail action sheet a Needs-Allocation item
would use.

## 8. Runway design

Existing `CapacityRunwayScreen` restyled to Design V2 (glass cards, comparison-stat header),
content largely kept — it's real, tested (`test_capacity_runway.py`), and already answers "where
is capacity tight over 4-8 weeks." Extend the existing `WeekBars` (already a real, working bar
chart) with a tap-to-drill interaction (tap a week → filtered Needs Allocation for that week) —
new interaction, not new data.

## 9. Coverage design

Replaces Pipeline Radar's "certified/team readiness" framing with four explainable bands, no
percentage:

```
COVERED       — mandatory capability requirements met by an available, eligible trainer
PARTIALLY COVERED — some but not all mandatory requirements met, or coverage exists but is tight
UNCOVERED     — no eligible trainer currently meets mandatory requirements
UNKNOWN       — no APPROVED capability profile exists for this course yet (§19 — this band is
                what most courses will show until Phase 2/curation lands, and that is the honest
                answer, not a bug to hide)
```
Until an `APPROVED` `CourseCapabilityProfile` exists (currently: none — all 8 are DRAFT), Coverage
for that course can only ever report `UNKNOWN` truthfully — this screen's whole value proposition
is gated on curation completing, and the design must show `UNKNOWN` as a legitimate, common state
from day one rather than reading as broken.

## 10. Demand Detail design

Restyled `BatchDetailScreen` sections, same information depth, percentages removed throughout:

```
Overview · Schedule · Client · Required capabilities · Preferred capabilities
Mandatory certification · Mode/Language/Location · Potential trainers (banded, not scored)
Conflicts · Allocation history · Notes · Actions
```
Actions, marked by what's real today vs. future:
- **Real now:** Ask trainer (skill-mark dialog flow, restyled), Search wider network, Open
  communication composer, Create task (dev-plan store already exists).
- **Future work, not implemented today:** Assign / Reassign as a direct booking action — no such
  write path exists in the backend today (§2); Escalate as a structured action beyond raising a
  manual task — the Action Centre inbox pattern from earlier this session could carry it, not
  built yet. Both marked explicitly as future in the UI copy ("Coming soon"-style labels are
  banned by your own governance rule, so these simply don't render as buttons until the backend
  supports them — no dead/disabled buttons either).

## 11. Trainer-fit explanation design

```
STRONG FIT
 Verified capability evidence · Available · No schedule conflict

PARTIAL FIT
 Some capability evidence, gaps in: <named capabilities> · Available

WEAK FIT
 Limited or no matching evidence · <availability state>

UNKNOWN
 No approved capability profile for this course yet — evidence cannot be evaluated
 (Availability confirmed / not confirmed shown separately, since that fact is still knowable)

NOT ELIGIBLE
 <hard blocker — visa, DNC, leave, language> named explicitly
```
No number, ever, per instruction. Until Phase 2 exists, essentially every candidate shows
`UNKNOWN` for capability with availability/eligibility still shown honestly — again, a legitimate
state, not a placeholder to disguise.

## 12. Charts

Keep and restyle: the 8-week capacity bar (`CapacityPlanningCard`), `WeekBars` (Runway). Add,
using the same `Box`-proportional technique already proven in this codebase (no `Canvas`
dependency needed): a coverage-by-band stacked bar (Covered/Partial/Uncovered/Unknown counts) on
the Coverage screen, and a simple trainer-availability strip (a row of day-cells) on Demand Detail
— both are direct extensions of patterns already working in this codebase, not new chart
infrastructure. Every chart supports tap-to-drill (week → filtered list, band → filtered list).

## 13. Filters

Keep: delivery mode, language, skill level, priority (all already real, built from live batch
data). Remove: the match-% band filter (75%+/50-74%/etc — meaningless once % is gone). Add:
date-range filter (real field, currently unused as a filter), vendor/domain filter (from
`_course_taxonomy`, already computed server-side, never exposed as a filter before). Saved
filters: worth doing, small scope, no backend change needed (client-side preference only).

## 14. Actions

Per §10 — Ask trainer, Search wider network, Message, Create task are real and wired today (in
some form) and get restyled, not rebuilt. Assign/Reassign/Escalate are named future work, not
implemented in this pass. Bulk allocation review and export/report: evaluated, **not
recommended** for this pass — no existing backend support and no evidence of manager demand for it
in the current screens' usage; would be new scope invented for a feature request that wasn't made.

## 15. Today → Plan navigation map

Fix a real, found gap: `ManagerCommandCentre`'s "Open demand" stat and "Needs you today" rows
currently switch to the generic Plan tab with **no batch or filter context carried over**
(`ManagerCommandCentre.kt:57`, a no-arg callback) — while a specific unallocated-demand row in the
same screen *does* carry a real `demand_id` into `BatchDetail`. Plan V2 fixes this: the "Open
demand" stat should land on the Needs-Allocation view (not a bare tab switch), and any
Today attention row about a specific batch already does the right thing and stays as-is. Capacity
warnings on Today → Runway, scoped to the relevant week once that plumbing exists (§8).

## 16. Phone/tablet layout

Phone: the four Plan destinations as a segmented control, one full-width view at a time — matches
Today's existing pattern, no new navigation idiom. Tablet/foldable: Needs Allocation (or Coverage)
as a master list on the left, selected Demand Detail / candidate shortlist on the right — direct
application of the master-detail pattern already specified for Trainer 360 style screens earlier
this session, not a new concept.

## 17. Empty/loading/error/offline states

```
No open demand                  → "Nothing needs allocation right now." (a genuinely good state)
High-demand period               → Needs Allocation list is simply long; no special state needed
Partial capability data           → Coverage shows PARTIALLY COVERED honestly, not hidden
No approved capability profile    → Coverage/fit shows UNKNOWN — see §9/§11, the common case today
Offline/cache                     → existing SkillSyncOfflineBanner pattern, reused
API error                         → existing SkillSyncErrorState pattern, reused
No candidates                     → "No trainer currently meets eligibility for this batch." + Search wider network CTA
Conflicting candidates             → both shown, conflict named explicitly (schedule overlap text), never silently hidden
Fully covered week                → Runway/Scheduled show it plainly — a quiet, unremarkable state, not celebrated with fake positivity
```

## 18. What can be implemented NOW (no capability-foundation dependency)

- Percentage/raw-score removal from Needs Allocation, Demand Detail, and candidate rows — this is
  a real, valuable fix independent of Phase 2, since the numbers being removed were never
  trustworthy capability evidence in the first place.
- Scheduled view (new, from already-available `batch_engagement_df`/confirmed-batch data).
- Runway restyle + week-tap drill-down.
- Fixing the Pipeline Radar `certified`/`skill_level` fabrication (§4) — a scoped backend bug fix,
  independent of the capability foundation, could be its own small release.
- Today → Plan navigation context fix (§15).
- Filter changes (§13).

## 19. What must wait for APPROVED capability data

- Coverage bands beyond `UNKNOWN` (§9) — cannot report Covered/Partial/Uncovered honestly without
  at least one `APPROVED` `CourseCapabilityProfile`.
- Trainer-fit STRONG/PARTIAL/WEAK bands (§11) beyond `UNKNOWN` — same dependency.
- Any actual "capability match" replacing today's string-based `_match_score` — that's Phase 2
  (matching engine) proper, already scoped in the earlier Plan-matching audit, not this redesign.

## 20. Exact KEEP / REMOVE / REDESIGN / ADD

| Item | Disposition |
|---|---|
| Mode-grouped batch list, never re-sorted by match | KEEP |
| Eligibility gates (`GatedCandidatesSection`) | KEEP |
| Search wider trainer network | KEEP |
| 8-week `CapacityPlanningCard` | KEEP (moves under Runway) |
| Capacity Runway computation + `WeekBars` | KEEP, restyle + add drill-down |
| Match %, relevance %, raw sub-score line | REMOVE |
| Match-band filter | REMOVE |
| `DistributionBar` (Strong/Partial/No-cover) | REDESIGN into the 4-band Coverage screen |
| Pipeline Radar `certified`/`skill_level` fabrication | REMOVE (bug fix, separate small scope) |
| Demand Detail sections (Overview/Schedule/etc.) | KEEP structure, REDESIGN visuals + remove % |
| Scheduled view | ADD |
| Coverage view (4-band) | ADD |
| Trainer-fit explanation card (STRONG/PARTIAL/WEAK/UNKNOWN/NOT ELIGIBLE) | ADD |
| Assign/Reassign/Escalate actions | ADD — future work, not this pass, no backend write path exists yet |
| Today "Open demand" stat context | REDESIGN (carry real navigation context) |
| Saved filters, vendor/domain filter | ADD |
| Bulk allocation review, export/report | NOT RECOMMENDED this pass |

---

**Stopping here per instruction. No Compose changed, no backend matching changed, no capability
drafts approved, no percentages implemented, nothing committed.** Awaiting approval before any
implementation — and per your own one-screen-per-release discipline, I'd suggest Needs Allocation
(percentage removal + coverage-band framing) as the first slice if approved, since it's both the
highest-visibility screen and entirely doable under §18 without waiting on Phase 2.
