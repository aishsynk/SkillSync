# SkillEdge V2 — The Intelligence Layer
**Date:** 2026-08-12 · **Status:** design, no code · **Basis:** live validated APIs only
**Product framing:** Delivery Intelligence and Resource Readiness Platform. **Not** finance, CRM, payroll or revenue.

> **Governing principle, from the operator and now the spine of every model below:**
> **Utilisation is not availability.** A trainer at 80% can be free on the dates that matter. A trainer at 40% can be unavailable because of leave, travel, an existing assignment, a time-zone conflict or a client exclusion. Every model here is built on *real dates*, not on utilisation inference.

---

## 1 · Validation report

Full detail in [`API_VALIDATION_2026_08_11.md`](API_VALIDATION_2026_08_11.md). Summary of all 14 unused/dormant APIs:

| API | Verdict | Evidence |
|---|---|---|
| **171** Trainer Free Schedule | ✅ **Production ready, highest value** | 37 rows for AZ-305, 21 for CKA |
| **111** Trainer RC Schedule | ✅ **Production ready, highest value** | 61 rows × 35 fields, one trainer, two months |
| **114** Course & Technology | ✅ Production ready | 19,921 rows, 1,068 technologies |
| **164** Course List | ✅ Production ready | 12,103 rows with vendor |
| **206** Course Module | ✅ Production ready | 17 chapters for Cid 17 |
| **156** Course Content URL | ✅ Production ready | Returns ContentURL |
| **13** Active SC Date | ✅ Works — **operational fields only** | CSM, SCId, SCCreatedDate. Fee/Currency **stripped at backend** |
| **90** Trainer availability | ❌ Empty × 3 param sets | Likely superseded by 171 |
| **172** Latest Course Version | ❌ Empty × 5 course names | Ranked ★★★★★ on docs; worthless live |
| **205** Course and Domain | ❌ Empty with real TechName | Vocabulary mismatch suspected |
| **72** Unique Cert Count | ❌ Empty × 2 emails | |
| **93** Upcoming Assignments | ❌ Empty × 2 param sets | Superseded by 111 |
| **215** Exam Course Linked | ⛔ **Mutation, not a lookup** | Returns "Exam and course linked successfully" |
| **255** Add Trainer Skill | ⛔ Write — not probed | |

---

## 2 · Response samples and findings

### 2.1 API 171 — the allocation primitive

Query is **course-first**: `{"course": "<exact catalogue name>"}`

```
rows = 37  (AZ-305)          rows = 21  (CKA)
TrainerTimezone     37/37    ← 100% populated
Trainer Free Date   37/37    ← 100%, 150–175 free days each over the window
Visa                18/37    ← ~48% of trainers carry visa records
NearestCity         22 distinct cities for one course
Skill Level         1 … 10
```

**Visa is richer than first seen — it carries associate countries:**

```json
[{"Country":"Australia","VisaExpiryDate":"12 Mar 2030","StayPeriod":"90 Days",
  "AssociateCountries":"Philippines,Egypt"},
 {"Country":"Canada","VisaExpiryDate":"…"}]
```

**Free dates are a literal calendar:**

```
"2026-08-15,2026-08-16,2026-08-17,2026-08-18,…"   (per trainer, ~155 days)
```

**Future Skill is embedded in the Skill Level string, not the separate column:**

```
"Skill Level": "1 (Future Skill: 08-Sep-2026)"
```

**Critical integration constraint:** the `course` parameter needs an **exact catalogue match**. `AZ-305T00: …` returned 37 rows; `AI-102T00: …`, `AZ-104T00: …` and `CCNA - Cisco Certified Network Associate` all returned **0**. Course names must be resolved through API 70/164 before calling 171, and a miss must be reported as "cannot verify", never as "nobody available".

**Semantics confirmed: 171 returns the whole skilled candidate pool, not only free trainers.** It can therefore drive the entire allocation model on its own.

### 2.2 API 111 — the operational calendar

61 rows × **35 fields** for one trainer over two months.

| Signal | Population | Sample |
|---|---|---|
| `LeaveStatus` / `LeaveAppliedDate` / `LeaveApprovedDate` / `LeaveApprovedBy` | 4/61 | `Auto Approved` |
| `AssociatedType` | 61/61 | `Free` |
| `QuotationStatus` | populated | `confirmed` |
| `DeliveryMode` | 7/61 | `ILO` |
| `QubitScore` | 4/61 | `98` |
| `Exam` | 4/61 | `Microsoft Certified: Azure AI Apps and Agents Developer Associate` |
| `SpecifiedTrainer` / `DNC` | schema present | client preference / exclusion |
| `HrsPerDay`, `StartTime`, `EndTime` | populated | `11:30`–`19:30` |
| `TravelDetails`, `TimeZone` | 0/61 | present in schema, empty for this trainer |

### 2.3 API 213 — exam policy (already wired, under-used)

```
11,007 courses
  Exam Required            1,446  (13%)
  Exam Not Required        9,561  (87%)
CourseStatus: Standard 7,625 · Custom 3,365
```

**Verified:** `backend.py` already consults this policy when computing gaps (line ~1796), so we are **not** over-reporting gaps. Good. What is missing is not *whether* an exam is required but *which exam* — see §9.

---

## 3 · Which APIs are production ready

**Integrate now (7):** 171, 111, 114, 164, 206, 156, 13 (operational fields only).
**Do not integrate (5):** 90, 172, 205, 72, 93 — return nothing; take to the RMS team.
**Never call as a read (2):** 215, 255.

---

## 4 · Highest business value

| Rank | API | Business capability unlocked |
|---|---|---|
| 1 | **171** | Real availability · international eligibility · time zone · location · course-specific experience |
| 2 | **111** | Leave · confirmed vs tentative · client preference and exclusion · mode history |
| 3 | **213** (existing) | Exam policy — which courses genuinely require certification |
| 4 | **114 + 164** | Technology and vendor portfolio structure |
| 5 | **206 + 156** | Enablement content to close a specific gap |
| 6 | **13** | CSM ownership and demand age |

---

## 5 · Integration order

1. **171** — unlocks availability, international and allocation in one call.
2. **111** — adds leave, tentative work, client rules.
3. **Course-name resolver** (70/164/114) — a prerequisite for 171, not optional.
4. **213** promotion — from a gap filter to a first-class certification signal.
5. **114/164** — portfolio.
6. **13** — CSM and demand age, fee stripped.
7. **206/156** — enablement.

---

## 6 · Allocation and Recommendation Model V2

### 6.1 What is wrong today

`_rank_batch` produces one opaque `relevance` from skill match, Qubits, utilisation, language, level and feedback. **Utilisation stands in for availability, which is the core error.**

### 6.2 The new model: hard gates, then weighted fit

**Stage 1 — Eligibility gates (binary; a failure removes the candidate and states why).**

| Gate | Source | Rule |
|---|---|---|
| Date availability | 171 `Trainer Free Date` | Every batch delivery day ∈ free-date set |
| Leave conflict | 111 `LeaveStatus` + dates | No approved/applied leave overlapping the batch |
| Existing commitment | 111 `AssociatedType`, `QuotationStatus` = confirmed | No confirmed assignment overlap |
| Client exclusion | 111 `DNC` | Trainer not on this client's do-not-call list |
| Skill floor | 171 `Skill Level` | ≥ batch required level |
| International eligibility | §7 | For FMAT/ILT only |

**Stage 2 — Weighted fit (0–100, every factor's contribution exposed).**

| Factor | Weight | Source |
|---|---|---|
| Course-specific experience | 20 | 171 `#Assignment for the Course` |
| Skill level above the floor | 18 | 171 `Skill Level` |
| Qubits / proficiency | 15 | existing + 111 `QubitScore` |
| Delivery-mode fit | 12 | 111 `DeliveryMode` history vs batch mode |
| Time-zone fit | 10 | §7 |
| Client preference | 10 | 111 `SpecifiedTrainer` (positive boost) |
| Feedback quality | 8 | existing |
| Load headroom | 7 | utilisation — **demoted from gate to tiebreaker** |

**Stage 3 — Output contract.** Never a bare number:

```
{ trainer, eligible: true,
  fit: 87,
  factors: [ {name:"Course experience", contribution:+18, evidence:"12 prior deliveries"},
             {name:"Time zone",         contribution:+6,  evidence:"IST vs GMT+4, 1.5h offset"},
             {name:"Load headroom",     contribution:-3,  evidence:"88% utilised"} ],
  blockers: [] }
```

A manager who disagrees can disagree with a **specific factor**, and that disagreement is a training label for the learning loop.

---

## 7 · International Suitability Model

Replaces the cosmetic globe badge with a computed verdict.

**Gate A — Visa eligibility** (171 `Visa`)
- Batch country ∈ `Country` **or** ∈ `AssociateCountries` → eligible
- `VisaExpiryDate` > batch end date → else **Expiring/Expired**
- `StayPeriod` ≥ batch duration → else **Stay too short**
- **No visa record → `Unknown`, never `Ineligible`.** Only ~48% of trainers carry visa data; treating absence as refusal would silently hide half the bench. `Unknown` routes to a manager check.

**Gate B — Travel window** — `InternationaRoamingOffDates` (already fetched, never surfaced) must not overlap the batch.

**Gate C — Time-zone fit** (171 `TrainerTimezone` vs batch region)

| Offset | Verdict |
|---|---|
| ≤ 3h | Comfortable |
| 3–6h | Workable, unsocial hours |
| > 6h | Requires night/early shift → cross-check `NightILOffDates` / `MorningILOffDates` / `EveningILOffDates` |

**Gate D — Proximity** — 171 `NearestCity` for travel cost and feasibility.

**Output:** `InternationalReadiness = Ready | Ready with checks | Blocked | Unknown`, each with the specific reason. **The UI then has something true to express**, and the globe treatment becomes a report of a computed verdict rather than decoration.

---

## 8 · Availability Intelligence Model

The direct implementation of *utilisation is not availability*.

```
AvailabilityVerdict(trainer, dateRange) =
    freeDates    ← 171 "Trainer Free Date"        (authoritative calendar)
    leave        ← 111 LeaveStatus + applied/approved dates
    commitments  ← 111 AssociatedType + QuotationStatus
    offDates     ← Get Trainer Details roaming / IL off-dates

    required = every delivery day in dateRange
    blocked  = required ∩ (leave ∪ confirmedCommitments ∪ offDates)
    free     = required ⊆ freeDates  AND blocked = ∅

    → Available            free
    → Partially available  some days free   (list the conflicting days)
    → Unavailable          with the specific reason
    → Unknown              171 returned no row for this course
```

**Tentative vs confirmed matters:** a `QuotationStatus` that is not `confirmed` is a **soft** conflict — surfaced as "provisionally booked", not as unavailable. Today we cannot tell the difference, so we either over- or under-commit people.

**Replaces:** `capacity_bucket`, `current_status`, and every "is X free?" answer in the agent.

---

## 9 · Certification Intelligence Model

Honestly the weakest of the four models, because the endpoint that should carry course→exam mapping (215) turned out to be a mutation.

**What we can build now:**

| Layer | Source | Confidence |
|---|---|---|
| Does this course require an exam? | **213** — 1,446 of 11,007 do | High, validated |
| Which exam did a delivery link to? | **111 `Exam`** — real exam names per assignment | Medium; 4/61 populated |
| What does the trainer hold? | existing `_certifications`, `vendorCertCount` | High |
| Is it a real gap? | 213 required ∧ ¬held ∧ teaches it | High |
| Which exam should they sit? | **mined from 111 `Exam` across the org** for that course | **Medium — inferred, must be labelled as such** |
| How do they prepare? | **206** chapters + **156** content URL | High |

**Gap priority** = demand for the course (unallocated batches needing it) × trainers blocked by the gap × exam-required flag.
That answers *"which certification should I fund next"* without any financial data.

**Open dependency:** a definitive course→exam mapping. Ask RMS whether a read-only equivalent of 215 exists. Until then, exam identity is inferred and **must be shown as inferred**.

---

## 10 · V2 Architecture

```
┌─ RMS (validated estate) ──────────────────────────────────────────┐
│  171 free schedule · 111 RC schedule · 213 exam policy            │
│  70/164/114 catalogue · 206/156 content · 13 ops · existing 20    │
└───────────────────────────┬───────────────────────────────────────┘
                            │
┌─ Intelligence layer (backend.py — NEW, the whole of Phase 1) ─────┐
│  CourseResolver      exact-name resolution, required by 171       │
│  AvailabilityEngine  §8 — real dates, leave, commitments          │
│  InternationalEngine §7 — visa, travel, time zone, proximity      │
│  CertificationEngine §9 — policy, gaps, priority, enablement      │
│  AllocationEngine    §6 — gates then weighted fit, factor-level   │
│  Fee/Currency stripped at this boundary. Never leaves the server. │
└───────────────────────────┬───────────────────────────────────────┘
                            │  typed, versioned, provenance-tagged
┌─ Android (rendering client) ──────────────────────────────────────┐
│  Screens render verdicts and factor breakdowns. No re-derivation. │
│  Agent consumes the same engines — one source of truth.           │
└───────────────────────────────────────────────────────────────────┘
```

**Principles:** every value carries `source` + `fetchedAt`; `Unknown` is a first-class state distinct from zero and from false; every score exposes its factors; the mobile client computes nothing it could get wrong.

---

## 11 · What becomes obsolete

| Obsolete | Replaced by |
|---|---|
| `capacity_bucket` (Bench/Light/Balanced/Stretched) as an availability proxy | §8 real availability verdict |
| `current_status` free/busy | §8, with day-level conflicts |
| The opaque `relevance` score | §6 factor-level breakdown |
| `InternationalBadge` / `GlobalPriorityRibbon` as decoration | §7 computed verdict driving the treatment |
| "Available capacity" KPI tile | "Available for *this* batch" — availability is per-demand, not global |
| Dashboard `TeamCapacityForecastCard` (utilisation projection) | Forecast from real free dates and confirmed commitments |
| Agent's `whoIsAvailable` (reads `current_status`) | §8 |
| Trainer 360 `AvailabilitySection` (off-dates only) | 171 calendar + 111 leave |
| Alternative-trainers lookup | 171, which returns the full skilled pool natively |

**Screens that must be rebuilt after the layer lands — not before:** Demand/Allocation, Trainer 360 availability and readiness, Dashboard capacity sections, Agent availability tools.

---

## 12 · Calculations to replace with real data

| Today (derived/guessed) | Replace with |
|---|---|
| Availability inferred from utilisation | 171 free dates |
| Absence unknown | 111 leave status |
| All bookings equal | 111 confirmed vs tentative |
| International = delivery mode is FMAT/ILT | §7 visa + travel + time zone |
| Time zone ignored | 171 `TrainerTimezone` |
| Location ignored | 171 `NearestCity` |
| Experience = total assignments | 171 `#Assignment for the Course` |
| Skill level from Qubits proxy | 171 `Skill Level` 1–10 |
| Client relationships invisible | 111 `SpecifiedTrainer` / `DNC` |
| Future skill invisible | 171 `Skill Level` embedded future-skill date |
| Course catalogue flat | 114 technology / 164 vendor |

---

## 13 · Manager workflows that improve

| Workflow | Before | After |
|---|---|---|
| "Who can take this batch?" | Guess from utilisation, then check manually | Eligible pool with blockers named per person |
| "Is Priya free on the 18th?" | Infer from a percentage | Answer from her calendar |
| "Can we staff Dubai?" | Check visas by asking around | Visa-eligible, travel-clear, time-zone-fit list |
| "Why did the system pick him?" | Unanswerable | Factor breakdown with evidence |
| "Which certification next?" | Anecdote | Ranked by blocked demand |
| "Who is on leave?" | Not in the product | First-class signal |
| "This client refused X" | Tribal knowledge | `DNC` enforced as a gate |
| "This client asks for Y" | Tribal knowledge | `SpecifiedTrainer` boosts fit |
| Assign a skill to several people | One-by-one search | Skill → select members → bulk assign |

---

## 14 · Roadmap

Aligned to your phases. **No UI redesign until Phase 3.**

### Phase 1 — Data Unlock & Intelligence (backend-led)
| # | Deliverable | Notes |
|---|---|---|
| 1.1 | Course-name resolver | **Blocks 171.** Exact-match constraint proven live |
| 1.2 | Wire 171 + provenance | Visa, free dates, time zone, city, level |
| 1.3 | Wire 111 | Leave, tentative, `SpecifiedTrainer`, `DNC` |
| 1.4 | **AvailabilityEngine** (§8) | The "utilisation ≠ availability" fix |
| 1.5 | **InternationalEngine** (§7) | Visa/travel/time zone verdicts |
| 1.6 | Expose hidden fields | Roaming + IL off-dates, Qubits, future skill |
| 1.7 | **CertificationEngine** (§9) | 213 promoted; exam identity labelled inferred |
| 1.8 | Strip fee/currency at boundary; wire 13 ops fields | CSM, demand age |
| 1.9 | RMS question list | 90, 172, 205, 72, 93, and 215's true contract |

### Phase 2 — Engines
2.1 **AllocationEngine V2** (gates + weighted fit + factor output) · 2.2 Demand Intelligence V2 · 2.3 Recommendation Engine V2 consuming all engines · 2.4 Agent tools re-pointed at the engines

### Phase 3 — Screens (only now)
3.1 Demand & Allocation V2 (international treatment expressing a real verdict) · 3.2 Dashboard V2 · 3.3 Team Intelligence V2 · 3.4 Trainer 360 V2

### Phase 4 — Capability & foresight
4.1 Skill → Select Members → Assign (bulk, preview, undo, remove, edit level) · 4.2 Capability portfolio (114/164/206/156) · 4.3 Manager Command Centre · 4.4 Predictive readiness and capacity forecasting from real free dates

---

## Open questions for you and for RMS

**For RMS:**
1. Read-only equivalent of 215 for course→exam mapping?
2. Correct parameters for 90, 172, 205, 72, 93 — or confirmation they are deprecated.
3. Is `Visa` absence meaningful (no visa) or just unrecorded? This decides whether `Unknown` is safe.
4. Are `TravelDetails` / `TimeZone` on 111 populated for other trainers, or dead columns?
5. Rate limits — 171 is per-course; a demand board of 40 batches implies 40 calls.

**For you:**
6. `DNC` as a **hard gate** — confirm a do-not-call is absolute and never overridden by a high fit score.
7. When visa status is `Unknown`, should the trainer appear in international results flagged for checking, or be hidden? I recommend **shown and flagged**, since ~52% have no visa record.
8. Phase 1 has no visible UI change beyond better answers. Confirm you are content with a release that is almost entirely backend.
