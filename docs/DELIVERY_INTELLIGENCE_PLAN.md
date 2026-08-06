# SkillEdge → Delivery Intelligence System — Implementation Plan

Status: **awaiting approval. No pipeline/UI code until approved.**

---

## 0. Two hard realities before anything

### 0.1 Architecture reality (this is not a pure-Python project)
This is an **HTML/JS front-end + Python server (`server.py`)**. The Jupyter notebook was the
original prototype. The spec's `config.py / api_client.py / normalize.py / pipeline.py / scoring.py`
map cleanly onto the **Python server side**, and the front-end (`js/api.js`) has already been
pointed at a server endpoint `GET /data/unified-manager-intelligence`.

**Decision this forces (solves your "don't hit APIs every refresh" problem at the root):**

```
Python server builds the 6 datasets ONCE per manager, caches to disk (JSON),
exposes them as a single endpoint.  Front-end pages read that ONE JSON. No page
ever calls the 9 RMS APIs directly again.
```

- Refresh a page → 0 RMS calls (reads cached JSON).
- "Refresh data" button / cache older than TTL → server rebuilds once, all pages benefit.
- Per-manager cache key → no org-wide leakage, no cross-manager bleed.

### 0.2 The 16 "new" APIs are NOT usable yet (verified today)
Per `api_verification_results.md` + `api_alignment_with_existing_pipeline.md`, **every one of the
16 new instruction files is polluted / mismatched** — the body text describes unrelated APIs (OTP,
invoice-status, trainee-question docs). We have **no verified endpoint, request body, or response
schema** for any of them. Integrating them now = inventing data, which you explicitly forbade.

**So Phase 1 is built on the 9 verified APIs only.** The 6 datasets are designed with the new-API
columns present but marked `BLOCKED` until you supply clean instruction sheets (same format as the
9 you just gave). The moment a clean sheet arrives, that column lights up — no redesign.

---

## 1. API alignment table (9 verified APIs)

Schemas below are **confirmed** from the official instruction sheets.

| # | API | key | Input | Confirmed output fields | Scope | Stage | Feeds dataset |
|---|-----|-----|-------|-------------------------|-------|-------|---------------|
| 1 | Direct/Indirect Reportee | 82 | `{email}` | TrainerName, TrainerId, EmpId, OffEmail, TrainerPlus, IsdirectReportee, Designation | **root** | scope | all (identity) |
| 2 | Trainer Details | 75 | `{email}` | CourseName, VendorName, QubitsScore, SkillLevel, OfficiallyApproved, "Is Future Skill", "Future Skill Date", DM, techcallrating, "Course Assignment", *OffDates | trainer | capability | ops, allocation, timeline |
| 3 | Trainer Skills | 217 | `{employee_id}` | employee_name, employee_code, course_id, course_name, is_duplicate_course, is_discontinue_course | trainer | capability | ops, allocation, custom-match |
| 4 | Utilization | 55 | `{email}` | *(opaque string — needs parse)* | trainer | capacity | ops |
| 5 | Vendor Cert Count | 57 | `{email}` | *(opaque string — count)* | trainer | certification | ops |
| 6 | Negative Feedback Count | 58 | `{email}` | Trainer, Email, Total | trainer | quality | ops, timeline |
| 7 | HR Incident +/- | 59 | `{email}` | Trainer, EmailId, "Positive Count", "Negative Count" | trainer | quality | ops, timeline |
| 8 | Course List | 164 | `{}` | Course, Courseid, vendor_name, vendor_id, course_url | **global** | master | allocation, custom-match |
| 9 | Assignment API | 15 | `{TrainerEmailAddres,PageNumber,PageSize}` | AssignmentID, CourseID, Course, TrainerName, OffEmailId | trainer | delivery | ops, allocation, timeline |

Two fields need care:
- **#4 Utilization** returns a JSON *string* — parser must be written defensively; only a single
  point (no trend → `utilization_trend` stays BLOCKED until `Trainer_Last_3_Months_Utilization`).
- **#5 Vendor Cert** returns opaque content → we get a **count only**, not cert names.
  `certification_status`/names stay BLOCKED (need `Unique Certifications` + `Exam Course Linked`).

---

## 2. Data model — 6 datasets, per-column source & status

Legend: ✅ derivable from 9 APIs · ⛔ BLOCKED (needs a verified new API) · 🧮 computed/scored

### 2.1 `trainer_operations_df` — one row per scoped trainer
| Group | Columns | Source |
|---|---|---|
| Identity | trainer_key, trainer_name, trainer_id, employee_id, official_email, designation, direct_or_indirect, trainer_plus_status | ✅ #1 |
| Capability | skills_count, mapped_courses_count, current_skills, current_courses, duplicate_course_count, discontinued_course_count | ✅ #3, #2 |
| Capability | domain_strengths, technology_strengths | ⛔ Course&Tech / Course-Domain |
| Certification | vendor_certification_count | ✅ #5 |
| Certification | unique_certification_count, certification_status, exam_required_course_count, course_without_exam_count, certification_gap_flag, certification_detail_available_flag | ⛔ Unique Cert / Exam-Linked / Course-w/o-Exam |
| Delivery | assignment_count, current_assignment, last_delivered_course, delivery_vendor_mix | ✅ #9 (+#2 "Course Assignment") |
| Delivery | previous_assignment_count, upcoming_assignment_count, next_assignment | ⛔ Prev&Upcoming / Upcoming |
| Capacity | current_utilization, utilization_status | ✅ #4 (parsed) |
| Capacity | last_3_month_utilization, utilization_trend, availability_status, free_schedule_status, rc_schedule_status | ⛔ Last-3-mo / Availability / Free-Sched / RC |
| Quality | negative_feedback_count | ✅ #6 |
| Quality | feedback_detail_count, latest_feedback_date, feedback_risk | ⛔ Feedback-Details (risk 🧮 from count as interim) |
| Quality | hr_positive_count, hr_negative_count, hr_risk | ✅ #7 (risk 🧮) |
| Readiness | overall_readiness_score, approval_risk_score, live_delivery_confidence, delivery_status, readiness_bucket | 🧮 §3 |
| Action | recommended_action, action_reason, action_priority, confidence, expected_outcome | 🧮 §4 |

### 2.2 `course_allocation_df` — one row per course×trainer candidate
Course cols (course_id/name/vendor/url) ✅ #8; domain/technology/exam/availability/duration ⛔.
Trainer cols ✅. Match scores 🧮 (skill/assignment/utilization/feedback/hr = ✅ inputs;
domain/technology/availability/certification-match = ⛔ inputs → reduced confidence).

### 2.3 `trainer_timeline_df` — multi-row per trainer
Events buildable now ✅: delivered course (#9), future-skill identified (#2), negative feedback (#6),
HR signal (#7), utilization snapshot (#4). Blocked ⛔: upcoming course, utilization-trend change.

### 2.4 `manager_action_df` — one row per action
Fully 🧮 from the above. Action types available now: Coach, Book Mock, Hold Delivery,
Monitor Feedback, Upskill, Review Data Gap, Allocate (interim). "Certify / Verify Certification /
Review Availability" fire only when their ⛔ inputs unlock.

### 2.5 `custom_course_match_df` — backend structure now, upload UI later
Skill/assignment/feedback/hr/utilization matching ✅ against #2/#3/#9. Domain/technology/cert
matching ⛔. File-parsing + skill-vector module (`custom_course.py`) is designed now, wired later.

### 2.6 `data_health_df` — real issues only
✅ fully — this is where every ⛔ becomes an explicit, visible row ("Availability API unavailable",
"Certification detail unavailable", "Utilization unparseable for X") rather than silent fake data.

---

## 3. Scoring (explainable — every score carries value+bucket+evidence+missing+confidence)

Interim weights using only verified inputs (renormalised so BLOCKED inputs don't silently score 0):

```
overall_readiness =
    skills_score*0.25 + assignment_experience*0.15 + certification_count_score*0.15
  + utilization_score*0.10 + feedback_quality*0.15 + hr_safety*0.10 + trainer_details*0.10
confidence -= penalty for each BLOCKED input actually needed by that trainer
```
`custom_course_fit` and `risk_taker` scores: same structure, blocked components excluded +
confidence flagged. Full weights from your spec kept as the target once new APIs land.

---

## 4. Decision logic (Signal → Meaning → Decision → Action) — encoded as rules table
Each rule = predicate over ops-df columns → action + reason + priority + confidence. Ships as a
readable ruleset (not black box), e.g. `util<40 & readiness<70 → Hold+Book Mock`.

---

## 5. Flow (text)

```
LOGIN(email) → Reportee API (scope guard: empty ⇒ "not a manager")
  └─ scoped trainers only ──► per-trainer: Details, Skills, Util, Certs, NegFb, HR, Assignments
                              global once:  Course List
        ▼ normalize (fix "Positive Count" spaces, snake_case skills, parse util string)
        ▼ aggregate → 6 datasets
        ▼ score (explainable) → recommend (rules)
        ▼ WRITE cache JSON per manager  ──►  GET /data/unified-manager-intelligence
                                              ▲
        every HTML page reads this ONE JSON ─┘   (0 RMS calls on navigation/refresh)
```

---

## 6. Backlog

**Phase 1 — data spine (9 APIs, server-side):** config, api_client (reuse token pattern),
normalizers, build 6 datasets, per-manager disk cache, `/data/unified-manager-intelligence`
endpoint, `data_health_df` surfacing every gap.
**Phase 2 — intelligence:** explainable scoring + confidence, rules engine, risk-taker + growth
buckets, custom-course matching backend (`custom_course.py`, no UII yet).
**Phase 3 — dashboard rebuild:** all SeanTheme pages consume the unified JSON; index becomes the
10-section Morning-Brief decision cockpit; readiness/certs/allocation pages rebuilt from datasets.
**Phase 4 — custom course upload UI** + wire to Phase-2 backend.
**Phase 0 (parallel, needs you):** supply clean instruction sheets for the 16 APIs → each unblocks
its columns with zero redesign.
