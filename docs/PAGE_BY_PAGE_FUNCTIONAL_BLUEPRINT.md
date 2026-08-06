# SkillEdge Page-by-Page Functional Blueprint

This document explains what the current SkillEdge portal is doing, why each page exists, what data feeds it, how the backend transforms that data, and how the portal maps back to the original Python notebook idea.

The core design principle is simple:

- The browser only consumes the unified JSON endpoint.
- The backend does the expensive API fan-out, normalization, scoring, and dataset assembly.
- The pages are decision surfaces for a manager, not standalone data dumps.

Unified endpoint:

`GET /data/unified-manager-intelligence?email=<manager_email>`

Backend canonical datasets:

- `trainer_operations_df`
- `course_allocation_df`
- `trainer_timeline_df`
- `manager_action_df`
- `trainer_availability_engine_df`
- `custom_course_match_df`
- `data_health_df`

---

## 1. `index.html`

1. Page name  
Dashboard / Home

2. File path  
[`C:\Users\Aishw\OneDrive - Koenig Solutions Ltd\SkillEdge\index.html`](C:\Users\Aishw\OneDrive%20-%20Koenig%20Solutions%20Ltd\SkillEdge\index.html)

3. Business question answered  
Who reports to me, who is ready now, who needs coaching, and what should I pay attention to today?

4. Manager decision supported  
Pick trainers for delivery, identify coaching priorities, and spot allocation or certification pressure early.

5. APIs used directly or indirectly through unified JSON  
Directly through the backend:
- Get Direct Indirect Reportee
- Get Trainer Details
- Get Trainer Skills
- Get Utilization
- Get trainer Vendor Certification Count
- Get Negative Feedback Count
- Get HR Incident Positive Negative
- Course List
- Assignment API
- Trainer Resume Details
- Previous & Upcoming Assignments
- Trainer availability
- Get Trainer Free Schedule and Details
- Trainer RC Schedule
- Trainer_Last_3_Months_Utilization
- Get Trainer Feedback Details
- Get Unique Certifications Count Value
- Course Whitout Exam
- Exam Course Linked API

6. Backend datasets used  
- `trainer_operations_df`
- `trainer_availability_engine_df`
- `course_allocation_df`
- `manager_action_df`
- `data_health_df`

7. What the page shows  
Top KPI cards, readiness/capacity charts, trainer cards, a condensed action strip, and a data-health summary.

8. What calculations/scoring are used  
- readiness scoring from trainer details, assignments, certs, utilization, feedback, and HR
- risk-taker/growth scoring from skill diversity and future-skill signals
- allocation scoring from readiness, workload, and course skill fit
- manager action prioritization from readiness and risk signals

9. How certification mapping is handled  
Uses resume certifications when present, vendor certification count as a coverage signal, and certification source logic to distinguish `Resume Details API`, `Vendor Certification Count API`, or `Both`.

10. How future skill/course upgrade logic is handled  
Uses skills, current courses, assignment history, and readiness buckets to infer who can take next-step work, who needs prep, and who is not ready.

11. How custom course or risk-taker logic is connected  
The dashboard can surface risk-taker candidates and custom-batch candidates, but the detailed matching logic lives in the dedicated pages and backend datasets.

12. What is still missing or pending  
- Full course-domain and course-technology enrichment
- More verified course-first APIs for deeper allocation intelligence
- Smarter schedule interpretation where upstream APIs time out

13. What page should be built next and why  
`quality-risk.html`, because it closes the loop on why a trainer should or should not be trusted for live delivery.

---

## 2. `trainer-detail.html`

1. Page name  
Trainer 360

2. File path  
[`C:\Users\Aishw\OneDrive - Koenig Solutions Ltd\SkillEdge\trainer-detail.html`](C:\Users\Aishw\OneDrive%20-%20Koenig%20Solutions%20Ltd\SkillEdge\trainer-detail.html)

3. Business question answered  
Is this trainer safe to allocate, and why?

4. Manager decision supported  
Go / no-go allocation, coaching, certification follow-up, and readiness review.

5. APIs used directly or indirectly through unified JSON  
Same unified backend pipeline as the dashboard, especially:
- Reportee API
- Trainer Details
- Trainer Skills
- Utilization
- Resume Details
- Vendor Certification Count
- Negative Feedback Count
- HR Incident Positive Negative
- Assignment API
- Previous & Upcoming Assignments
- Trainer Feedback Details
- Trainer availability
- Get Trainer Free Schedule and Details
- Trainer RC Schedule

6. Backend datasets used  
- `trainer_operations_df`
- `trainer_timeline_df`
- `trainer_availability_engine_df`
- `data_health_df`

7. What the page shows  
Profile hero, score strip, certification intelligence, assignment timeline, skill/capability summary, and evidence accordion.

8. What calculations/scoring are used  
- trainer readiness score
- utilization trend
- quality risk signals
- delivery evidence rollups

9. How certification mapping is handled  
Shows trainer-visible certifications from Resume Details, with vendor cert counts as supporting evidence.

10. How future skill/course upgrade logic is handled  
Uses current skills, course history, and readiness to suggest whether the trainer is a candidate for adjacent or advanced work.

11. How custom course or risk-taker logic is connected  
Trainer 360 feeds the custom-course-match and risk-taker pages as the source of truth for one trainer’s profile and evidence.

12. What is still missing or pending  
- Full domain/technology mapping
- Better schedule certainty when upstream APIs are unavailable

13. What page should be built next and why  
`allocation-desk.html`, because Trainer 360 is the evidence layer that allocation decisions depend on.

---

## 3. `allocation-desk.html`

1. Page name  
Allocation Desk

2. File path  
[`C:\Users\Aishw\OneDrive - Koenig Solutions Ltd\SkillEdge\allocation-desk.html`](C:\Users\Aishw%20-%20Koenig%20Solutions%20Ltd\SkillEdge\allocation-desk.html)

3. Business question answered  
Which trainer should be allocated to which course right now?

4. Manager decision supported  
Course-to-trainer allocation, controlled rollout, and who can be matched safely.

5. APIs used directly or indirectly through unified JSON  
- Course List
- Trainer Details
- Trainer Skills
- Utilization
- Assignment API
- Resume Details
- Vendor Certification Count
- Trainer availability
- Trainer RC Schedule
- Previous & Upcoming Assignments
- Trainer Feedback Details
- Negative Feedback Count
- HR Incident Positive Negative

6. Backend datasets used  
- `course_allocation_df`
- `trainer_operations_df`
- `trainer_availability_engine_df`
- `data_health_df`

7. What the page shows  
Course-first allocation cards, best-match panels, course-trainer rankings, and evidence tables.

8. What calculations/scoring are used  
- allocation score from readiness, workload, and assignment history
- blocker detection from HR/feedback risk and low readiness

9. How certification mapping is handled  
Allocation can be blocked or softened by resume certifications, vendor cert coverage, and exam/no-exam course mapping where available.

10. How future skill/course upgrade logic is handled  
If no exact course match exists, the page can still surface adjacent trainers who are close enough to upgrade with prep.

11. How custom course or risk-taker logic is connected  
Custom course matching is the most specific version of the allocation problem and reuses the same readiness and skills logic.

12. What is still missing or pending  
- Deeper vendor/domain/technology mapping
- Additional course-first APIs for richer course intelligence

13. What page should be built next and why  
`custom-course-match.html`, because it is the most direct expression of the original notebook idea: upload a course and rank trainers.

---

## 4. `custom-course-match.html`

1. Page name  
Custom Course Match

2. File path  
[`C:\Users\Aishw\OneDrive - Koenig Solutions Ltd\SkillEdge\custom-course-match.html`](C:\Users\Aishw%20-%20Koenig%20Solutions%20Ltd\SkillEdge\custom-course-match.html)

3. Business question answered  
If I upload a custom course outline, who should I share it with, who can deliver, who can be upgraded, and who is risky?

4. Manager decision supported  
Share outline, choose a trainer, book prep, or avoid risky allocation.

5. APIs used directly or indirectly through unified JSON  
- Reportee API
- Trainer Details
- Trainer Skills
- Utilization
- Resume Details
- Vendor Certification Count
- Assignment API
- Previous & Upcoming Assignments
- Trainer availability
- Trainer RC Schedule
- Get Trainer Free Schedule and Details
- Get Trainer Feedback Details
- Negative Feedback Count
- HR Incident Positive Negative
- Course List
- Course Whitout Exam
- Exam Course Linked API
- Get Unique Certifications Count Value

6. Backend datasets used  
- `custom_course_match_df` when populated later
- `trainer_operations_df`
- `course_allocation_df`
- `trainer_availability_engine_df`
- `data_health_df`

7. What the page shows  
Upload/paste inputs, extracted course intelligence placeholder, ranked trainer cards, risk-taker discovery panel, charts, and a preparation timeline.

8. What calculations/scoring are used  
- match score
- readiness score
- upgrade effort
- confidence level
- risk-taker classification

9. How certification mapping is handled  
Avoids false cert gaps by relying on resume certifications, vendor cert counts, and exam/no-exam course logic where available.

10. How future skill/course upgrade logic is handled  
Ranks trainers by how close they are to the course, what prep they need, and whether the course is a stretch or a safe fit.

11. How custom course or risk-taker logic is connected  
This is the direct notebook-to-portal translation of custom matching and stretch candidate discovery.

12. What is still missing or pending  
- Real parser-backed course extraction from uploaded files
- Persisted `custom_course_match_df` generation

13. What page should be built next and why  
`actions.html`, because once a trainer/course is identified, the manager needs concrete next actions.

---

## 5. `actions.html`

1. Page name  
Action Center

2. File path  
[`C:\Users\Aishw\OneDrive - Koenig Solutions Ltd\SkillEdge\actions.html`](C:\Users\Aishw%20-%20Koenig%20Solutions%20Ltd\SkillEdge\actions.html)

3. Business question answered  
What exactly should I do today for my trainers and courses?

4. Manager decision supported  
Prioritize coaching, allocation, verification, or monitoring actions.

5. APIs used directly or indirectly through unified JSON  
- All trainer-level APIs through the unified backend
- `manager_action_df` is the primary dataset

6. Backend datasets used  
- `manager_action_df`
- `trainer_operations_df`
- `course_allocation_df`
- `data_health_df`

7. What the page shows  
An inbox-style action list, grouped priorities, action detail modal, filters, and a preparation timeline.

8. What calculations/scoring are used  
- action priority derived from readiness, risk, and allocation pressure
- action confidence from signal completeness

9. How certification mapping is handled  
Actions can include verify-cert steps when certification evidence is weak or inconsistent.

10. How future skill/course upgrade logic is handled  
The page uses actions to bridge from current state to the next upgrade or allocation step.

11. How custom course or risk-taker logic is connected  
Actions are the operational follow-through after a custom-course or risk-taker decision.

12. What is still missing or pending  
- Persisted task completion states
- Richer action state machine beyond recommended actions

13. What page should be built next and why  
`team.html`, because the manager needs a compact team operating view after the action queue.

---

## 6. `team.html`

1. Page name  
My Team

2. File path  
[`C:\Users\Aishw\OneDrive - Koenig Solutions Ltd\SkillEdge\team.html`](C:\Users\Aishw%20-%20Koenig%20Solutions%20Ltd\SkillEdge\team.html)

3. Business question answered  
What is the current shape of my team?

4. Manager decision supported  
See the team split, compare trainers, and filter quickly by readiness, availability, and risk.

5. APIs used directly or indirectly through unified JSON  
- Reportee API
- Trainer Details
- Trainer Skills
- Utilization
- Resume Details
- Assignment API
- Negative Feedback Count
- HR Incident Positive Negative
- Vendor Certification Count
- Trainer availability
- Trainer RC Schedule
- Trainer Feedback Details

6. Backend datasets used  
- `trainer_operations_df`
- `trainer_availability_engine_df`
- `manager_action_df`
- `data_health_df`

7. What the page shows  
KPI cards, trainer cards, filters, mix charts, quick comparison views, and collapsed evidence.

8. What calculations/scoring are used  
- readiness mix
- availability mix
- risk mix
- certification summary badges

9. How certification mapping is handled  
Uses resume certifications and vendor count to show certification state at the team level.

10. How future skill/course upgrade logic is handled  
Surfaces growth candidates and stretch candidates as part of the team’s composition.

11. How custom course or risk-taker logic is connected  
Feeds team-level candidate identification for custom courses and stretch work.

12. What is still missing or pending  
- Deeper team comparison matrix against future skills and course domains

13. What page should be built next and why  
`capability-builder.html`, because team composition should flow into upskill planning.

---

## 7. `capability-builder.html`

1. Page name  
Capability Builder

2. File path  
[`C:\Users\Aishw\OneDrive - Koenig Solutions Ltd\SkillEdge\capability-builder.html`](C:\Users\Aishw%20-%20Koenig%20Solutions%20Ltd\SkillEdge\capability-builder.html)

3. Business question answered  
Who can be upgraded, to which course or technology, and what should I do to make them ready?

4. Manager decision supported  
Upskill, coach, certify, or hold candidates before assigning them.

5. APIs used directly or indirectly through unified JSON  
- Reportee API
- Trainer Details
- Trainer Skills
- Utilization
- Resume Details
- Assignment API
- Trainer Feedback Details
- Negative Feedback Count
- HR Incident Positive Negative
- Course List
- Trainer availability
- Trainer RC Schedule
- Previous & Upcoming Assignments
- Trainer_Last_3_Months_Utilization
- Get Unique Certifications Count Value
- Course Whitout Exam

6. Backend datasets used  
- `trainer_operations_df`
- `course_allocation_df`
- `manager_action_df`
- `trainer_timeline_df`
- `data_health_df`

7. What the page shows  
Capability KPIs, upgrade candidate cards, future skill/course opportunities, gap charts, an upgrade timeline, and development actions.

8. What calculations/scoring are used  
- growth candidate classification
- readiness bucket
- risk-taker score
- course opportunity ranking

9. How certification mapping is handled  
Certification attention is driven by resume cert visibility and vendor coverage, with exam/no-exam course mapping as an exception rule.

10. How future skill/course upgrade logic is handled  
This page is the upgrade pipeline: identify a candidate, share a course, coach, verify, and allocate later.

11. How custom course or risk-taker logic is connected  
It reuses the same candidate-ranking logic as the custom course and risk-taker pages, but with a stronger focus on growth.

12. What is still missing or pending  
- Course technology/domain enrichment
- Richer future skill recommendations from verified course metadata

13. What page should be built next and why  
`data-health.html`, because all upgrade decisions depend on trusting the signals.

---

## 8. `data-health.html`

1. Page name  
Data Health

2. File path  
[`C:\Users\Aishw\OneDrive - Koenig Solutions Ltd\SkillEdge\data-health.html`](C:\Users\Aishw%20-%20Koenig%20Solutions%20Ltd\SkillEdge\data-health.html)

3. Business question answered  
Can I trust this intelligence, and what is incomplete?

4. Manager decision supported  
Decide whether to trust, defer, or manually verify a signal before allocation or coaching.

5. APIs used directly or indirectly through unified JSON  
- All APIs indirectly, because the health view is built from the unified payload and its health rows

6. Backend datasets used  
- `data_health_df`
- `trainer_availability_engine_df`
- `trainer_operations_df`

7. What the page shows  
Health KPIs, trust summary, availability signal health, issue cards, business impact, charts, and a collapsed evidence table.

8. What calculations/scoring are used  
- issue severity grouping
- affected trainer counting
- affected dataset distribution
- trust framing from missing or timed-out signals

9. How certification mapping is handled  
Shows when certification data is incomplete or missing and whether the portal is relying on resume certs or vendor counts.

10. How future skill/course upgrade logic is handled  
Indirectly, by flagging which signals are too incomplete to support safe upgrade recommendations.

11. How custom course or risk-taker logic is connected  
If the underlying signals are stale or broken, custom matching and risk-taking decisions should be treated as tentative.

12. What is still missing or pending  
- Cleaner elimination of external theme font warnings
- More explicit schema mismatch classification for some upstream APIs

13. What page should be built next and why  
`risk-takers.html`, because once trust is established, the next highest-value surface is stretch candidate discovery.

---

## 9. Planned `risk-takers.html`

1. Page name  
Risk-Taker Candidates

2. File path  
[`C:\Users\Aishw\OneDrive - Koenig Solutions Ltd\SkillEdge\risk-takers.html`](C:\Users\Aishw%20-%20Koenig%20Solutions%20Ltd\SkillEdge\risk-takers.html)

3. Business question answered  
Who are my hidden talent, growth, and stretch candidates for future or custom delivery?

4. Manager decision supported  
Choose safe experts, growth candidates, or risk-takers for stretch work.

5. APIs used directly or indirectly through unified JSON  
- Reportee API
- Trainer Details
- Trainer Skills
- Utilization
- Resume Details
- Assignment API
- Trainer Feedback Details
- Negative Feedback Count
- HR Incident Positive Negative
- Previous & Upcoming Assignments

6. Backend datasets used  
- `trainer_operations_df`
- `course_allocation_df`
- `manager_action_df`
- `trainer_timeline_df`
- `data_health_df`

7. What the page should show  
Candidate cards, risk vs opportunity charts, preparation journey, and manager moves.

8. What calculations/scoring are used  
- risk-taker score
- growth bucket
- readiness vs risk tradeoff

9. Certification mapping  
Used as one input to decide whether a risk-taker candidate is cert-ready or needs a cert step.

10. Future skill/course upgrade logic  
This page is a direct consumer of upgrade candidates and stretch-course planning.

11. Custom course or risk-taker logic connection  
This is one of the most direct surfaces for notebook-style stretch candidate logic.

12. What is still missing or pending  
- The page itself
- More explicit course-domain/technology matching

13. What page should be built next and why  
`risk-takers.html` should come immediately after the trust layer, because it turns the scoring model into talent discovery.

---

## 10. Planned `quality-risk.html`

1. Page name  
Quality & Risk

2. File path  
[`C:\Users\Aishw\OneDrive - Koenig Solutions Ltd\SkillEdge\quality-risk.html`](C:\Users\Aishw%20-%20Koenig%20Solutions%20Ltd\SkillEdge\quality-risk.html)

3. Business question answered  
Who has quality or HR risk that should block or slow allocation?

4. Manager decision supported  
Hold, coach, review, or clear a trainer for delivery.

5. APIs used directly or indirectly through unified JSON  
- Negative Feedback Count
- HR Incident Positive Negative
- Trainer Feedback Details
- Resume Details
- Trainer Details
- Assignment API
- Trainer availability
- Previous & Upcoming Assignments

6. Backend datasets used  
- `trainer_operations_df`
- `manager_action_df`
- `trainer_timeline_df`
- `data_health_df`

7. What the page should show  
Risk inbox, feedback evidence cards, HR risk widgets, quality trend charts, and incident timeline.

8. What calculations/scoring are used  
- feedback risk
- HR risk
- risk severity
- incident counts

9. Certification mapping  
Certification can be used to judge whether quality risk is a temporary gap or a broader readiness issue.

10. Future skill/course upgrade logic  
If quality risk is low but readiness is weak, the page should point toward coaching rather than blocking.

11. Custom course or risk-taker logic connection  
Risk status influences whether a stretch candidate should actually be used.

12. What is still missing or pending  
- The page itself

13. What page should be built next and why  
`quality-risk.html`, because it is the other side of readiness: capability without quality is not safe delivery.

---

## 11. Planned `timeline.html`

1. Page name  
Timeline

2. File path  
[`C:\Users\Aishw\OneDrive - Koenig Solutions Ltd\SkillEdge\timeline.html`](C:\Users\Aishw%20-%20Koenig%20Solutions%20Ltd\SkillEdge\timeline.html)

3. Business question answered  
What has happened, what is happening, and what comes next across trainers and delivery?

4. Manager decision supported  
Plan delivery sequencing, coaching order, and assignment timing.

5. APIs used directly or indirectly through unified JSON  
- Assignment API
- Previous & Upcoming Assignments
- Trainer availability
- Trainer RC Schedule
- Trainer_Last_3_Months_Utilization
- Trainer Feedback Details
- Negative Feedback Count

6. Backend datasets used  
- `trainer_timeline_df`
- `trainer_availability_engine_df`
- `data_health_df`

7. What the page should show  
Trainer activity timeline, assignment events, upcoming delivery calendar, feedback events, and utilization trend events.

8. What calculations/scoring are used  
- event sequencing
- timeline grouping
- utilization trend framing

9. Certification mapping  
Can be shown as a timeline event when certifications are loaded or verified.

10. Future skill/course upgrade logic  
Can show the progression from candidate identification to allocation readiness.

11. Custom course or risk-taker logic connection  
Useful for showing the pre-delivery journey for custom and stretch candidates.

12. What is still missing or pending  
- The page itself

13. What page should be built next and why  
`timeline.html`, because it makes the journey visible rather than just showing a static score.

---

## Original Notebook Logic Mapping

The original Python notebook was not just a dashboard generator. It was a trainer intelligence engine.

### Skill gap
Mapped today through:
- `trainer_operations_df.skills_count`
- `trainer_operations_df.mapped_courses_count`
- `trainer_operations_df.resume_skills`
- `course_allocation_df`

Meaning:
- Which skills are present
- Which courses are already mapped
- Which skill gaps still exist

### Course recommendation
Mapped today through:
- `course_allocation_df`
- `manager_action_df`
- `custom_course_match_df` placeholder

Meaning:
- Which trainer is the best fit for a course
- Who is close enough to be upgraded
- Who should not be recommended yet

### Readiness scoring
Mapped today through:
- `trainer_operations_df.overall_readiness_score`
- `trainer_operations_df.readiness_bucket`
- `trainer_availability_engine_df.readiness_status`

Meaning:
- Who is ready now
- Who needs coaching
- Who needs mock
- Who should be held back

### Certification gap
Mapped today through:
- `resume_certifications`
- `vendor_certification_count`
- `unique_certification_count`
- `certification_source`
- `data_health_df`

Meaning:
- Whether cert intelligence is visible from the resume
- Whether coverage exists only as a count
- Whether cert details are incomplete

### Future course planning
Mapped today through:
- future-skill flags in trainer details
- readiness and growth buckets
- manager actions
- the planned custom course matcher

Meaning:
- What to share next
- Who can be stretched
- Where prep is needed before allocation

### Trainer upgrade path
Mapped today through:
- `capability-builder.html`
- `manager_action_df`
- `trainer_timeline_df`
- readiness and growth buckets

Meaning:
- Identify candidate
- share course
- coach
- verify
- allocate

### ML / ranking logic
Mapped today through:
- explainable scoring in `intelligence.py`
- readiness and risk-taker scores
- allocation ranking
- confidence and evidence fields

Meaning:
- Trainers are ranked by evidence, not by one raw score alone
- The model is explainable and manager-facing

### Custom course matching
Mapped today through:
- `custom-course-match.html`
- `custom_course_match_df`
- `course_allocation_df`
- `trainer_availability_engine_df`

Meaning:
- Which trainers should receive a custom course outline
- Who is a safe expert, growth candidate, or risk-taker

### Risk-taker score
Mapped today through:
- `trainer_operations_df.growth_bucket`
- `trainer_operations_df.risk_taker_score`
- `trainer_availability_engine_df.batch_type_fit`

Meaning:
- Who can stretch safely
- Who is a hidden talent
- Who is not worth the risk yet

---

## API-to-Page Matrix

This is the functional map from every major API to the datasets and pages that use it.

| API | Dataset / Normalization | Primary Page(s) | Business Use | Decision Supported |
|---|---|---|---|---|
| Get Direct Indirect Reportee | `trainer_operations_df` base scope | `index.html`, `team.html`, `trainer-detail.html` | Define the manager’s scoped team | Who is in my team? |
| Get Trainer Details | `trainer_operations_df` readiness fields | `index.html`, `trainer-detail.html`, `capability-builder.html` | Qubit, skill level, approval, tech-call rating | Ready now vs needs prep |
| Get Trainer Skills | `trainer_operations_df` skills fields | `index.html`, `allocation-desk.html`, `custom-course-match.html`, `capability-builder.html` | Skill breadth and mapped courses | Course fit and upgrade fit |
| Get Utilization | `trainer_operations_df` and availability engine | `index.html`, `team.html`, `trainer-detail.html`, `allocation-desk.html`, `data-health.html` | Current load signal | Available vs overloaded |
| Get trainer Vender Certification Count | `trainer_operations_df` and cert source logic | `index.html`, `trainer-detail.html`, `team.html`, `capability-builder.html`, `custom-course-match.html` | Coverage and cert count | Is certification coverage present? |
| Get Negative Feedback Count | `trainer_operations_df`, `trainer_availability_engine_df`, `manager_action_df` | `index.html`, `trainer-detail.html`, `actions.html`, `quality-risk.html`, `data-health.html` | Quality risk count | Hold / review / clear |
| Get HR Incident Positive Negative | `trainer_operations_df`, availability engine | `index.html`, `trainer-detail.html`, `actions.html`, `quality-risk.html`, `data-health.html` | HR risk count | Safe vs risky delivery |
| Course List | `course_allocation_df`, course master | `index.html`, `allocation-desk.html`, `custom-course-match.html`, `capability-builder.html` | Course catalog and vendor mapping | Which course can be assigned? |
| Assignment API | `trainer_operations_df`, `trainer_timeline_df`, `course_allocation_df` | `index.html`, `trainer-detail.html`, `actions.html`, `allocation-desk.html`, `timeline.html` | Current and past delivery evidence | Has delivered similar work? |
| Trainer Resume Details | `trainer_operations_df`, `trainer_timeline_df`, availability engine | `trainer-detail.html`, `index.html`, `team.html`, `allocation-desk.html`, `capability-builder.html`, `custom-course-match.html` | Resume profile enrichment, certification text, feedback text | What does the trainer actually list? |
| Previous & Upcoming Assignments | `trainer_availability_engine_df`, `trainer_timeline_df` | `index.html`, `trainer-detail.html`, `actions.html`, `timeline.html`, `data-health.html` | Past and future workload | Busy, available, or blocked? |
| Trainer availability | `trainer_availability_engine_df` | `index.html`, `trainer-detail.html`, `team.html`, `allocation-desk.html`, `data-health.html` | Calendar availability signal | Can this trainer be safely used? |
| Get Trainer Free Schedule and Details | `trainer_availability_engine_df` | `index.html`, `trainer-detail.html`, `allocation-desk.html`, `data-health.html` | Free/busy slot detail | Is there capacity right now? |
| Trainer RC Schedule | `trainer_availability_engine_df` | `index.html`, `trainer-detail.html`, `allocation-desk.html`, `data-health.html` | RC schedule availability signal | Is the trainer blocked or busy? |
| Trainer_Last_3_Months_Utilization | `trainer_availability_engine_df`, utilization trend | `index.html`, `trainer-detail.html`, `team.html`, `capability-builder.html`, `data-health.html` | Trend analysis | Trend up or down? |
| Get Trainer Feedback Details | `trainer_availability_engine_df`, `trainer_timeline_df` | `index.html`, `trainer-detail.html`, `actions.html`, `quality-risk.html`, `data-health.html` | Detailed quality evidence | Why is there risk or review? |
| Get Unique Certifications Count Value | `trainer_operations_df`, availability engine | `index.html`, `trainer-detail.html`, `capability-builder.html`, `data-health.html` | Unique cert count check | Is cert coverage broad enough? |
| Course Whitout Exam | `trainer_operations_df`, availability engine | `trainer-detail.html`, `allocation-desk.html`, `capability-builder.html`, `custom-course-match.html` | Avoid false cert gaps | Is an exam required at all? |
| Exam Course Linked API | `trainer_operations_df`, availability engine | `trainer-detail.html`, `allocation-desk.html`, `capability-builder.html`, `custom-course-match.html` | Exam-to-course mapping | Does this course need exam mapping? |
| Trainer Resume Details: Certifications | `trainer_operations_df.resume_certifications` | `trainer-detail.html`, `index.html`, `team.html`, `allocation-desk.html`, `capability-builder.html`, `custom-course-match.html` | Trainer-visible cert detail | What certs are visible in the profile? |

Note:
- Some APIs are not yet visually exposed in every page, but they still influence the backend scoring and the unified payload.
- The portal is intentionally fronted by one endpoint so the browser does not fan out directly to the RMS systems.

---

## Gaps Identified

- `quality-risk.html` is still planned, not built.
- `risk-takers.html` is still planned, not built.
- `timeline.html` is still planned, not built.
- `trainer_availability_engine_df` still depends on some upstream APIs that can timeout or return partial data.
- Course domain and course technology mapping are still not fully implemented because those APIs are not yet part of the verified stable path.
- Custom course upload parsing is still a future backend step; the page shell exists, but the extracted intelligence backend is not complete.

---

## Recommended Next Page

`quality-risk.html`

Why:
- It is the natural complement to readiness.
- It explains why a trainer should be held back, reviewed, or cleared.
- It closes the loop between capability and trust.
- It gives the manager a direct way to act on the quality and HR signals already present in the backend.

