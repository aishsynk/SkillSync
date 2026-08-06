# SkillEdge Intelligence Engine Specification

**Status:** Constitution / single source of truth. Every future page, API, recommendation, model and AI feature must trace back to this document. If a feature cannot be traced here, it is not implemented.

**Grounding:** This spec describes ONLY what is verifiable today from — the RMS API instruction sheets, the current `intelligence.py` pipeline, the unified `/data/unified-manager-intelligence` endpoint, the emitted datasets, and the original `TrainerCertificateMapping.ipynb` logic. Anything beyond that is explicitly marked **[Future Capability]** or **[Requires Additional Data]**. Nothing is claimed to exist that does not.

**Truth labels used throughout**
- ✅ **Live** — implemented and returning real data now.
- 🟡 **Best-effort** — integrated in code, schema inferred, degrades to empty on failure (not schema-verified).
- ⛔ **Blocked** — needs a verified API / field that does not reliably return today.
- 🔮 **Future** — ML / LLM / market integration; not built.

> **Known implementation issue (must fix before this engine is trusted):** `intelligence.py` line ~948 references `has_avail`, which is never defined (the real variable is `has_calendar_evidence`). Any *fresh* build (`?refresh=1` or cache expiry) will raise `NameError` and return HTTP 500. It is currently masked because the server serves the 4-hour per-manager cache built under an earlier code version. **This blocks trustworthy rebuilds and must be corrected.**

---

## 1. PRODUCT VISION

**SkillEdge is an AI-assisted Delivery Intelligence Platform for training delivery managers.** It converts raw RMS trainer/course signals into *delivery decisions with evidence*.

**What it is NOT:**
- **Not an LMS** — it does not host content, courses, or learner progress.
- **Not a CRM** — it does not manage customers, leads, or sales pipeline.
- **Not a Trainer Portal** — trainers are not the users; the *manager* is.
- **Not a Reporting Dashboard** — it does not just display numbers; it produces ranked, explained recommendations.
- **Not a generic BI tool** — it is opinionated around one domain: *can this trainer deliver this course, and what should the manager do?*

**Core value:** the manager opens SkillEdge and immediately knows who can deliver, who cannot, who to grow, who to hold, and why — each answer carrying evidence, confidence, and missing-signal disclosure.

**Long-term vision:** Rule Engine → Machine Learning → Generative AI → Agentic AI (see §14). The UI is the last layer; the intelligence engine is the product.

**Problems it solves:** delivery readiness, allocation, capability gaps, certification value, stretch-risk decisions, data-trust.
**Problems it intentionally does NOT solve:** content authoring, scheduling execution, payroll/HR case management, customer sales, learner delivery.

---

## 2. BUSINESS OBJECT MODEL

Every entity below lists: purpose · owner · key relationships · source API · derived fields · future fields.

| Entity | Purpose | Owner | Relationships | Source API | Derived today | Future |
|---|---|---|---|---|---|---|
| **Manager** | Scope root; the user | RMS hierarchy | owns Trainers (direct/indirect) | Reportee (82) ✅ | manager_email, scope set | copilot session, preferences 🔮 |
| **Trainer** | Person who delivers | Manager | has Skills, Assignments, Certs, Feedback, HR, Utilization | Reportee (82) ✅ + all child APIs | `trainer_operations_df` row | ML readiness, embeddings 🔮 |
| **Course** | Deliverable unit | RMS catalog | mapped to Trainers, Vendor, Exam | Course List (164) ✅ | courses[], allocation rows | domain/tech tags ⛔ |
| **OEM / Vendor** | Course owner (Microsoft, AWS…) | RMS | groups Courses, Certs | vendor_name in Details/Course List ✅ | delivery_vendor_mix | OEM lifecycle 🔮 |
| **Technology / Knowledge Area** | Skill cluster | — | groups Courses/Skills | — | ⛔ (no domain/tech API) | Course&Tech API 🟡 |
| **Skill** | Trainer capability | Trainer | maps to Courses | Trainer Skills (217) ✅ | skills_count, current_courses, dup/discontinued | adjacency, decay 🔮 |
| **Certification** | Proof of capability | Trainer | maps to Vendor/Exam/Course | Vendor Cert (57) ✅, Resume (87) 🟡, Unique Cert (72) 🟡, Exam-Course-Linked (215) 🟡, Course-Without-Exam (213) 🟡 | certified_vendors, resume_certifications, certification_source/status | ROI, unlock matrix 🔮 |
| **Assignment / Batch** | Delivery event | Trainer×Course | past & upcoming | Assignment (15) ✅, Prev&Upcoming (16) 🟡 | assignment_count, current/last, upcoming rows | batch type, calendar 🟡 |
| **Feedback** | Delivery quality signal | Trainer | per Assignment | Neg Feedback (58) ✅, Feedback Details (244) 🟡 | negative_feedback_count, feedback_risk | sentiment, themes 🔮 |
| **HR Incident** | Conduct signal | Trainer | — | HR Incident (59) ✅ | hr_positive/negative_count, hr_risk | — |
| **Utilization** | Load/util history | Trainer | monthly | Utilization (55) ✅, Last-3-Mo (39) 🟡 | current, 3-mo avg, trend | forecast 🔮 |
| **Availability** | *Delivery* availability | Trainer | calendar+capacity+quality | Trainer Avail (90) 🟡, Free Sched (171) 🟡, RC Sched (111) 🟡 + derived | `trainer_availability_engine_df` | true calendar ⛔ |
| **Demand / Market Trend** | External signal | Market | Course/Cert/OEM | — | none | Learn/AWS/etc. 🔮 |
| **Custom Course** | Uploaded outline | Manager | matched to Trainers | file upload | heuristic keyword match (client-side) 🟡 | NLP parser 🔮 |
| **Recommendation** | Decision output | Engine | Trainer/Course/Action | derived | recommended_action + reason + priority | ML-ranked 🔮 |
| **Opportunity** | Stretch/course fit | Engine | Trainer×Course | derived | allocation_status, growth_bucket | — |
| **Action** | Manager to-do | Manager | Trainer | derived | `manager_action_df` | copilot-generated 🔮 |
| **Business Risk** | Capability exposure | Manager | OEM/Course | — | partial (data_gaps, at_risk) | OEM gap model 🔮 |

---

## 3. DELIVERY INTELLIGENCE ENGINE

**Question:** *Can this trainer successfully deliver this course?*

**Implementation:** `intelligence.py :: _score_trainer()` → `overall_readiness_score` + `readiness_bucket`.

**Inputs / signals (each renormalised so missing signals don't score 0):**
| Signal | Source | Weight | Score rule |
|---|---|---|---|
| Qubit (or skills fallback) | Details.QubitsScore ✅ | 0.25 | avg Qubit; else `skills_active×12` capped 100 |
| Assignment experience | Assignment API ✅ | 0.15 | `count×10` cap 100 |
| Certification | Vendor Cert ✅ | 0.15 | `cert_count×20` cap 100 |
| Utilization | Utilization ✅ | 0.10 | current util % |
| Feedback quality | Neg Feedback ✅ | 0.15 | `100 − neg×20` |
| HR safety | HR Incident ✅ | 0.10 | `pos/(pos+neg)×100`, else 100 |
| Tech-call rating | Details.techcallrating ✅ | 0.10 | `avg×20` cap 100 |

**Decision rules (buckets):** `Ready Now ≥80` · `Can Deliver with Prep ≥65` · `Needs Coaching ≥45` · `Not Recommended <45` · `Data Incomplete` when both Qubit and utilization are null.

**Confidence rule:** `100 − 12 × (missing signals)` where missing ∈ {utilization, qubit, tech_rating}.
**Evidence rule:** every row carries `evidence{skills_active, assignments, certs, neg_feedback, hr_pos, hr_neg, avg_qubit, utilization, diversity}` and `missing_signals[]`.
**Missing-data rule:** a null signal is dropped from the weighted average (not treated as 0) and disclosed in `missing_signals`; it lowers confidence, never silently the score.
**Explainability:** bucket + score + weighted evidence + missing list are all emitted; the UI must show the "why", never the score alone.

⛔ **Not yet:** true course-specific match (readiness is trainer-level, not per target course); domain/technology fit; exam-mandated gating.

---

## 4. AVAILABILITY ENGINE (Delivery Availability, not calendar)

**Implementation:** `intelligence.py` engine loop → `trainer_availability_engine_df`. Availability is a *composite*, not a calendar lookup.

**Combined signals:** calendar evidence (Trainer Avail 90 🟡 / Free Sched 171 🟡 / RC Sched 111 🟡), capacity (Utilization ✅ + trend), workload (assignments + upcoming 🟡), readiness (§3), feedback+HR (✅), certification (§5), Qubit (✅), custom-batch experience (resume 🟡).

**Intermediate labels produced:**
- `calendar_status` → Blocked / Busy / Available / Unknown (from MTI_Issue flags + upcoming signal + calendar evidence).
- `capacity_status` → Underused / Balanced / Overloaded, overridden by Trend Up/Down when util trend exists.
- `workload_status` → Heavy / Moderate / Light / Unknown.
- `readiness_status` → Ready Now / Can Deliver with Prep / Needs Mock / Hold.
- `feedback_status` → Strong feedback / Review needed / Risky.
- `certification_status` → Both / Certification visible in resume / Count only / Exam not required / Certification unknown.

**Output — `final_availability_status`:**
`Ready for Live Delivery` · `Available but Needs Prep` · `Available but Risky` · `Busy but Strong Candidate` · `Overloaded` · `Hold / Do Not Allocate` · `Data Incomplete`.

Each row also emits `recommended_use`, `recommended_action`, `availability_confidence` (`100 − 15×missing`, floor 35), `availability_reason`, `confidence_reason`, `evidence_used[]`, `evidence_missing[]`, and a `batch_type_fit` matrix (ILT / FMAT / Corporate / Custom / First-time / Qubit-style / Stretch).

> The spec's requested seven labels (Ready Now / Ready with Prep / Ready as Backup / Busy / Overloaded / Do Not Allocate / Data Incomplete) map onto the above; **"Ready as Backup"** is not yet a distinct output ⛔ — add as an explicit state.
> Calendar signals are 🟡 (schedule APIs often time out at 5s → `calendar_status=Unknown`); the engine deliberately does **not** assert "Busy" from capacity alone.

---

## 5. CERTIFICATION INTELLIGENCE ENGINE

| Capability | Status | Source / rule |
|---|---|---|
| Current vendor certifications (names + count) | ✅ | Vendor Cert (57) boolean columns → `certified_vendors`, `vendor_certification_count` |
| Resume certifications (with images) | 🟡 | Resume Details (87) `Certifications` → `resume_certifications` |
| Unique certification count | 🟡 | Unique Cert (72) → `unique_certification_count` |
| Certification source blend | ✅ | `certification_source` = Both / Resume / Vendor / Not Available |
| Certification status (in availability engine) | ✅/🟡 | Both / visible-in-resume / count-only / exam-not-required / unknown |
| Course-without-exam awareness | 🟡 | Course Without Exam (213) → avoids false "gap" when no exam exists |
| Exam↔Course linkage | 🟡 | Exam Course Linked (215) fetched globally; **not yet joined into per-course cert gating** ⛔ |
| Mandatory / future certifications | ⛔ | no field marks "mandatory"; future-skill flag exists but not cert-mandate |
| Certification ROI / impact / unlock matrix | 🔮 | needs demand + value model |
| Certification recommendation logic | 🟡 | only via availability `recommended_action` (e.g. "verify certification") |
| Certification roadmap | 🔮 | future |

**Explainability today:** cert status + source + counts are shown with the trainer; **ROI and "highest-value certification" are not computable** without market/demand data.

---

## 6. SKILL EVOLUTION ENGINE

| Capability | Status | Notes |
|---|---|---|
| Current skills | ✅ | Trainer Skills (217): `skills_count`, `current_courses`, duplicate/discontinued flags |
| Future-skill flag | ✅ | Details `Is Future Skill` / `Future Skill Date` → timeline event |
| Skill diversity | ✅ | distinct course count → feeds risk-taker score |
| Adjacent / transferable skills | ⛔ | no skill-graph; would need domain/technology taxonomy |
| Emerging / deprecated skills | ⛔ | `is_discontinue_course` gives *deprecated course* only, not skill trend |
| Technology / OEM / Vendor evolution | 🔮 | needs market feeds |
| Skill decay / learning velocity / maturity / future readiness | 🔮 | needs historical time-series ML |

**Today this is "current skills + future-skill flags", not true evolution.** Everything predictive is 🔮.

---

## 7. CUSTOM COURSE INTELLIGENCE ENGINE

**Question:** *Given an uploaded custom course, who can deliver / grow / stretch / is risky?*

**Today (`custom-course-match.html`, client-side):**
- File upload placeholder (PDF/DOCX/XLSX/CSV/TXT) — **parser not wired** 🔮.
- Paste-outline path 🟡: heuristic keyword extraction (topics, tools, cert codes via regex) — real, from user text.
- Match: intersect course keywords with each trainer's live course vocabulary (`current_courses`, vendors, certs) → coverage %, blended with readiness + availability under a risk-appetite weight (Safe/Balanced/Risk-taker).
- Output buckets: Safe Expert / Can Deliver with Prep / Growth Candidate / Risk-Taker / Not Recommended / Data Incomplete — each with matching skills, missing skills, upgrade effort, action. **No matches shown until input is provided (no fake data).**

**Backend dataset:** `custom_course_match_df` exists in the payload but is **empty** — server-side matching/parsing is not built. The page computes matches in the browser as a heuristic **preview**.

**Blocked / future:** document parsing (PDF/PPT/Word/Notebook) 🔮; extraction of difficulty/OEM/labs/duration/prerequisites/learning-objectives 🔮; server-side scored `custom_course_match_df` ⛔.

---

## 8. RISK-TAKER ENGINE

**Definition:** *positive delivery/growth risk* — can a trainer safely take a new/stretch course — NOT HR/feedback risk (those are penalties).

**Implementation:** `_score_trainer()` → `risk_taker_score` + `growth_bucket`.
`risk_taker_score = min(100, diversity×8 + (future_skill?20) + (trainer_plus?15) + spare_capacity×0.15) − neg_feedback×5 − hr_neg×5`.
**Buckets:** `Safe Expert` (readiness≥80 & no neg feedback) · `Risk Taker` (risk_taker_score≥60 & readiness≥45) · `Growth Candidate` (readiness≥55) · `Do Not Risk` (neg>2 or hr_neg>0) · `Data Incomplete`.

Surfaced in `risk-takers.html` with per-card evidence (why this classification), suggested stretch area (from availability `recommended_use` or top allocation), first manager move, and a candidate detail modal.

| Requested sub-index | Status |
|---|---|
| Risk-Taker Index | ✅ (`risk_taker_score`) |
| Adaptability / Learning Velocity / Technology Transfer / Innovation Score / Growth Confidence | ⛔/🔮 — no historical or cross-tech signal exists; would need ML |
| "When to take the risk / avoid it" | ✅ rule-based (bucket + availability `recommended_action`) |

---

## 9. DEMAND & MARKET INTELLIGENCE  — 🔮 entirely future

No external market signal is integrated today. Microsoft Learn, AWS, Google, Cisco, RedHat, Snowflake, Databricks, HashiCorp, OpenAI, GitHub feeds, course/cert popularity, OEM/technology lifecycle: **all [Future Capability]**.

**Intended function (not built):** compare external demand vs internal capability (from `trainer_operations_df` / `course_allocation_df`) to surface "build capability before demand arrives" and "weakening OEM capability". Today only *internal* coverage exists; **demand-vs-capability cannot be computed** (`Requires Additional Data`).

---

## 10. RECOMMENDATION ENGINE

**Implementation:** `_recommend()` (trainer-level) + availability-engine `recommended_action` + allocation `allocation_status`.

| Recommendation type | Status | Source |
|---|---|---|
| Trainer action (Allocate/Coach/Book Mock/Hold/Upskill/Review) | ✅ | `_recommend()` → `manager_action_df` |
| Allocation (best trainer per course) | ✅ | `course_allocation_df` (readiness 0.5 + experience 0.2 + availability 0.3) |
| Availability-based use | ✅/🟡 | engine `recommended_use` + `recommended_action` |
| Certification recommendation | 🟡 | only "verify certification" style prompts |
| Course / learning / mentoring / growth recommendation | ⛔/🔮 | no learning-path or mentor graph |

**Every recommendation today carries:** evidence, confidence, missing signals. **Not yet:** explicit alternatives, trade-offs, and quantified *expected business impact* (⛔ — add as required fields; impact needs demand model 🔮).

---

## 11. MANAGER COPILOT — 🔮 future

No natural-language layer exists. All example queries ("Who should deliver DP-700?", "Which Fabric trainers are underutilized?", "Generate learning plan", "Predict readiness") are **[Future Capability]**, dependent on: an LLM, the Knowledge Graph (§12), and for demand-style questions the market feeds (§9). The unified datasets are the retrieval substrate a future copilot would query.

---

## 12. KNOWLEDGE GRAPH

**Today: implicit and partial** — relationships exist as joins across datasets keyed by trainer email, not as a graph store.

```
Manager ─owns→ Trainer ─has→ Skills ─map→ Courses ─owned_by→ OEM/Vendor
                     │           │                     │
                     ├─delivered→ Assignments ─for→ Courses
                     ├─holds→ Certifications ─prove→ Vendor/Exam
                     ├─received→ Feedback / HR (risk)
                     ├─has→ Utilization / Availability (capacity)
                     └─scored→ Readiness / Growth / Availability engines
                                           │
                                           └→ Recommendations → Manager Actions → (Business Outcomes 🔮)
```
✅ Live edges: Manager→Trainer→{Skills, Assignments, Certs, Feedback, HR, Utilization}→Readiness→Recommendation→Action.
⛔ Missing edges: Course→Domain/Technology, Course→Exam gating, Trainer→adjacent skills, Action→measured Business Outcome.
🔮 A real graph DB + embeddings would power copilot and similarity.

---

## 13. RAW vs DERIVED vs AI (field classification)

| Layer | Examples |
|---|---|
| **Raw API** ✅ | QubitsScore, SkillLevel, course_name, vendor_name, Certificate Count + vendor booleans, Positive/Negative Count, Total (feedback), monthly utilization strings, AssignmentID/Course, reportee identity |
| **Derived (rule)** ✅ | overall_readiness_score, readiness_bucket, confidence, risk_taker_score, growth_bucket, utilization current/3-mo/trend, availability engine (all `*_status`, batch_type_fit, final_availability_status), allocation score/status, certification_source/status, manager actions |
| **Rule Engine** ✅ | §3, §4, §8, §10 decision tables |
| **ML** 🔮 | skill decay, learning velocity, readiness prediction, demand forecast, recommendation ranking |
| **LLM** 🔮 | custom-course document parsing, feedback theme extraction, Manager Copilot NL |
| **Impossible today** ⛔ | course domain/technology tags, exam-mandate gating, true calendar free/busy, business-outcome measurement |
| **Future integration** 🔮 | market/OEM demand feeds (§9) |

---

## 14. ML ROADMAP (keep layers separate)

1. **Rule Engine (now)** ✅ — all scoring/classification/recommendation is deterministic and explainable. This layer must remain the auditable backbone.
2. **Machine Learning** 🔮 — only after enough historical outcome data: readiness prediction, utilization forecast, skill-decay, recommendation ranking. Trains on accumulated `trainer_operations` snapshots + delivery outcomes (not yet captured).
3. **Generative AI (LLM)** 🔮 — document parsing (custom course), feedback summarisation, natural-language explanations.
4. **Agentic AI** 🔮 — autonomous manager copilot that proposes and (with approval) schedules actions.

**Rule:** never let ML/LLM silently override rule-engine outputs; they augment and must show provenance. Do not mix layers in one opaque score.

---

## 15. PRODUCT ROADMAP

| Phase | Contents |
|---|---|
| **Demo (now)** | Unified endpoint, 6+ datasets, Dashboard, Trainer 360, Allocation Desk, Custom Match (heuristic), Risk-Takers, availability engine, rule-based recommendations |
| **Production V1** | Fix rebuild bug; add "Ready as Backup"; standardise UI system; verify the 11 🟡 APIs; server-side `custom_course_match_df`; remaining pages (team, actions, quality-risk, timeline, data-health, capability-builder) |
| **Production V2** | Exam-mandate gating; domain/technology taxonomy; certification ROI; outcome capture for ML |
| **Enterprise** | Multi-manager rollups, audit, RBAC, historical snapshots |
| **AI Phase** | ML predictions; LLM document parsing + explanations |
| **Agentic Phase** | Manager Copilot, autonomous action proposals |
| **Future Vision** | Market/OEM demand integration; capability-vs-demand planning |

---

## 16. IMPLEMENTATION PRIORITY (by intelligence capability, not by page)

1. **Intelligence/Scoring Engine** — fix the `has_avail` rebuild crash; lock the readiness model + confidence/evidence contract.
2. **Availability Engine** — verify the 🟡 schedule/utilization APIs; add "Ready as Backup"; stabilise calendar timeouts.
3. **Recommendation Engine** — add alternatives, trade-offs, expected-impact fields.
4. **Custom Course Intelligence** — server-side `custom_course_match_df` + document parser.
5. **Trainer 360 enrichment** — resume, feedback details, exam-linkage joins.
6. **Allocation Engine** — course-specific (not trainer-level) readiness; domain/tech tags.
7. **Risk-Taker Engine** — add adaptability/velocity once history exists.
8. **Manager Copilot** — after Knowledge Graph + LLM.
9. **UI Pages** — last; every page validated against §1–§12.

---

## FINAL OUTPUT — capability ledger

### 1. Features already supported ✅
Reportee-scoped team load; per-trainer 360 (skills, Qubit, utilization trend, vendor+resume certs, feedback, HR, assignments); readiness scoring with confidence + evidence + missing-signal disclosure; growth/risk-taker classification; delivery-availability engine with composite status + batch-fit matrix; course allocation (best trainer per course); rule-based manager actions; timeline events; data-health surfacing; 4-hour per-manager server cache; unified single-endpoint contract (no RMS calls from HTML).

### 2. Features partially supported 🟡
Certification intelligence (names/count/source ✅ but no ROI/mandate); resume, unique-cert, availability, free-schedule, RC-schedule, prev/upcoming, last-3-month, feedback-details, course-without-exam, exam-course-linked APIs (called, schema-inferred, degrade to empty); custom-course matching (client-side heuristic only); calendar availability (often Unknown due to 5s timeouts).

### 3. Features blocked by missing / unverified APIs ⛔
Course **domain/technology** tags; **exam-mandate** gating per course; true **free/busy calendar**; "Ready as Backup" state; server-side scored `custom_course_match_df`; explicit alternatives/trade-offs/expected-impact in recommendations.

### 4. Features requiring ML 🔮
Readiness prediction; skill decay / learning velocity / maturity; utilization forecast; recommendation ranking; adaptability & innovation indices.

### 5. Features requiring LLM 🔮
Custom-course document parsing (PDF/PPT/Word/Notebook); feedback theme/sentiment extraction; Manager Copilot natural-language Q&A and plan generation.

### 6. Features requiring future market integrations 🔮
Microsoft Learn / AWS / Google / Cisco / RedHat / Snowflake / Databricks / HashiCorp / OpenAI / GitHub demand & popularity; OEM/technology lifecycle; capability-vs-demand and "weakening OEM" analysis; certification ROI ranking.

### 7. Recommended implementation order
Engine bugfix & contract → Availability verification → Recommendation enrichment → Custom-course backend → Trainer 360 enrichment → Allocation (course-specific) → Risk-Taker depth → Copilot → UI. **UI is the final layer.**

---

*This document is the single source of truth. Any feature not traceable to a section here must not be implemented until added here and approved.*
