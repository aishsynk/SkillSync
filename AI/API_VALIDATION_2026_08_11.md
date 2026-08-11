# RMS API Validation — Live Probe Results
**Date:** 2026-08-11 · **Method:** live authenticated calls against `api.koenig-solutions.com` using the credentials in `trainer_portal_api_details` · **Scope:** the 14 unused and dormant APIs

> Evidence, not documentation. Every result below came from a real call. Personal data was redacted at capture; what is recorded is endpoint health, row counts, field names and value shapes.
>
> **API 255 (Add Trainer Skill) was excluded — it writes to production RMS.**

---

## Headline results

| Verdict | Count | APIs |
|---|---|---|
| **Working, high value** | 6 | 171, 111, 114, 164, 206, 156 |
| **Working, value limited by scope decision** | 1 | 13 |
| **Returns empty for every parameter tried** | 5 | 90, 172, 205, 72, 93 |
| **Not a lookup — it mutates** | 1 | 215 |
| Excluded (write) | 1 | 255 |

---

## 1 · API 171 — Trainer Free Schedule ★★★★★

**This is the single most valuable endpoint in the estate, and it is completely unwired.**

Query is **course-first** (`{"course": "<name>"}`) — the exact shape of the allocation question, which our trainer-first model cannot express.

| Field | Live sample | Why it matters |
|---|---|---|
| `Visa` | `[{"Country":"South Africa","VisaExpiryDate":"29 Mar 2027","StayPeriod":"90 Days"}]` | **Answers your Decision 4 directly: visa data DOES exist in RMS.** Country, expiry and permitted stay, per trainer. |
| `Trainer Free Date` | `2026-08-15,2026-08-16,…` (comma-separated day list) | **A real availability calendar**, not a derived guess. |
| `TrainerTimezone` | `India Standard Time` | Time-zone fit for ILO delivery. |
| `NearestCity` | `Durgapur` | Travel feasibility and cost proximity. |
| `Skill Level` | `9` | Objective proficiency for this course. |
| `#Assignment for the Course` | `2` | Course-specific experience, distinct from total load. |
| `Total #Assignment` | `75` | Overall delivery experience. |
| `Location`, `Future Skill` | populated / null | Base location; declared learning intent. |

**Impact:** international suitability, travel readiness, time-zone fit and true availability — the four things §1.3 of the audit identified as missing from `_rank_batch` — are all in this one call.

---

## 2 · API 111 — Trainer RC Schedule ★★★★★

61 rows across two months for one trainer. **35 fields.** A genuine day-level operational calendar.

| Field group | Fields | Value |
|---|---|---|
| **Leave** | `LeaveStatus` (e.g. `Auto Approved`), `LeaveAppliedDate`, `LeaveApprovedDate`, `LeaveApprovedBy` | **Real absence data.** Availability today is inferred from assignments; this is the actual answer. 4/61 rows populated. |
| **Schedule** | `Date`, `AssociatedType` (e.g. `Free`), `CourseSDate`, `CourseEDate`, `StartTime`, `EndTime`, `HrsPerDay` | Day-level calendar with working hours. |
| **Delivery** | `DeliveryMode` (`ILO`), `NoOfStudent`, `Location`, `HomeLVC`, `LabNo`, `LabSetUp` | Mode and logistics per engagement. |
| **Commercial context** | `QuotationId`, `QuotationStatus` (`confirmed`) | Confirmed vs tentative — distinguishes firm commitments from provisional ones. |
| **Quality** | `QubitScore` (98), `Exam`, `MockRemark`, `DVT` | Per-assignment proficiency and the linked certification. |
| **People** | `ManagerName`, `TrainerRole`, `SpecifiedTrainer`, `DNC` | `SpecifiedTrainer` and `DNC` are client-level trainer preferences and exclusions. |
| **Travel** | `TravelDetails` | Present in schema; empty for this trainer/window. |

**Impact:** replaces inferred availability with actual leave and schedule, adds confirmed-vs-tentative, and exposes client preference (`SpecifiedTrainer`) and exclusion (`DNC`) — neither of which the matching engine knows about today.

---

## 3 · Working catalogue APIs

| API | Rows | Fields | Use |
|---|---|---|---|
| **114** Course & Technology List | **19,921** | `technology_name`, `course_name`, `course_id`, `technology_id` | **1,068 distinct technologies.** Enables a technology-portfolio view and structural gap analysis. |
| **164** Course List | **12,103** | `Course`, `Courseid`, `vendor_name`, `vendor_id`, `course_url` | Vendor-level capability strategy. |
| **206** Get Course Module | 17 for Cid 17 | `ChapterName`, `CourseId` | Chapter-level enablement plan for closing a specific gap. |
| **156** Get Course Content URL | 1 | `ContentURL`, `Message` | Direct enablement material. |

---

## 4 · API 13 — works, but scope-limited by your decision

Returns `CourseName`, `CSM`, `AssignmentId`, `SCId`, `SCCreatedDate`, `Total Fee`, `Currency`.

Per your Decision 1, **fee and currency are out of scope and will not be surfaced.** The non-financial fields remain useful: **`CSM`** gives the account owner for a batch (who to talk to), and `SCCreatedDate` gives demand age — how long a batch has been waiting. I recommend wiring this endpoint **with the fee and currency fields dropped at the backend boundary**, so they never reach the device.

---

## 5 · Returned empty for every parameter set tried

Not proven broken — proven *not usable with the parameters available to us*. Each needs an RMS-team answer before we design on it.

| API | Attempts | Question for RMS |
|---|---|---|
| **90** Trainer availability | 3 course names × 3 date formats | Is this superseded by 171? What `cname` format does it expect? |
| **172** Latest Version Of Courses | 5 course names incl. AZ-305, AI-102, AZ-104, SC-200 | What is a valid `CName`? Is version tracking populated at all? |
| **205** Get Course and Domain | Real `technology_name` ("Android") seeded from 114 | Is `TechName` a different vocabulary from `technology_name`? |
| **72** Unique Certifications Count | Manager and reportee emails | Is this account scoped out of it? |
| **93** Upcoming Assignments | `trainerID` and `empCode`, two date formats | Which identifier does it key on? Superseded by 111? |

**My audit ranked 172 at ★★★★★ on documentation alone. Live, it returns nothing.** That is precisely the failure mode you warned about, and it is why this validation was worth doing before any design work.

---

## 6 · API 215 — a mutation, not a lookup ⚠️

My audit listed this as "maps course → required exam, the missing link in every certification recommendation". **That was wrong.**

Live response:

```json
{ "Status": 1, "Message": "Exam and course linked successfully." }
```

It **performs a link**, it does not return a mapping. It must never be called as a read.

**Disclosure:** I called it once during discovery, believing the documentation described a lookup, with `{"courseid": "17", "examid": "", "iswithoutexam": ""}`. Because `examid` was empty I expect it was a no-op, but I cannot prove that from the response. **Please have the RMS team confirm no unintended exam link was created against course id 17.** I have not called it again, and it is now on the same excluded list as 255.

The certification-path mapping we actually need therefore has **no confirmed source yet**. `Exam` on API 111 gives the exam per assignment, which is a partial substitute worth exploring.

---

## 7 · What this changes in the matching engine

`_rank_batch` today scores: skill match, Qubits, utilisation, language, skill level, feedback risk.

Newly available and provably real:

| Parameter | Source | Effect on allocation |
|---|---|---|
| Visa country + expiry + stay period | 171 | Hard eligibility filter for international delivery |
| Trainer free dates | 171 | True availability instead of inferred |
| Trainer time zone | 171 | ILO shift fit |
| Nearest city / location | 171 | Travel feasibility |
| Course-specific assignment count | 171 | Experience on *this* course, not in general |
| Leave status and dates | 111 | Hard availability conflict |
| Confirmed vs tentative | 111 | Do not treat a provisional booking as a commitment |
| `SpecifiedTrainer` / `DNC` | 111 | Client preference and exclusion — currently invisible |
| Delivery mode history | 111 | Mode-specific suitability |

**Conclusion: the international matching problem is now solvable with data we can fetch today.** It requires no new RMS work, only wiring 171 and 111 and rewriting the ranker to a transparent multi-factor model.

---

## Recommended Phase 1, revised on evidence

1. Wire **171** and **111** — the two endpoints that carry visa, availability, leave, time zone and client preference.
2. Rewrite `_rank_batch` as a transparent weighted model with per-factor contributions, adding the nine parameters above.
3. Wire **114** and **164** for the capability portfolio.
4. Wire **13** with fee and currency stripped at the backend boundary, for `CSM` and demand age.
5. Take the five empty APIs and API 215's true contract to the RMS team as one consolidated question list.
