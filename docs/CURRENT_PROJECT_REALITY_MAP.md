# CURRENT PROJECT REALITY MAP

This document is a factual snapshot of the current SkillEdge portal. It maps the original Python agenda to what is actually built today, using the real backend, the real datasets, and the real browser pages.

It intentionally does **not** propose a new platform vision, new pages, or new code. It only records current reality.

## 1) Current API inventory

The project currently knows about three API groups:

1. APIs that are actually wired into `intelligence.py` and the unified backend.
2. APIs that are partially integrated or schema-inferred in code.
3. APIs that are only planned or discussed in specs and page documents.

### A. APIs currently wired in backend config

| API name | API key | Input parameters | Response fields / shape | Verified status | Current use in Python | Dataset impacted | Page impacted | Business purpose |
|---|---:|---|---|---|---|---|---|---|
| Get Direct / Indirect Reportee | 82 | `email` | `TrainerName`, `TrainerId`, `EmpId`, `OffEmail`, `TrainerPlus`, `IsdirectReportee`, `Designation` | verified live | root scope for manager team | `trainer_operations_df` | `index.html`, `team.html`, `trainer-detail.html` | define the manager’s scoped reportee list |
| Get Trainer Details | 75 | `email` | `CourseName`, `VendorName`, `QubitsScore`, `SkillLevel`, `OfficiallyApproved`, `Is Future Skill`, `Future Skill Date`, `DM`, `techcallrating`, `Course Assignment` | verified live | readiness/capability inputs | `trainer_operations_df`, `trainer_timeline_df` | `index.html`, `trainer-detail.html`, `capability-builder.html`, `team.html` | trainer strength, approval, delivery potential |
| Get Trainer Skills | 217 | `employee_id` | `employee_name`, `employee_code`, `course_id`, `course_name`, `is_duplicate_course`, `is_discontinue_course` | verified live | skill breadth and course mapping | `trainer_operations_df`, `course_allocation_df`, `custom_course_match_df` | `index.html`, `allocation-desk.html`, `custom-course-match.html`, `capability-builder.html` | capability breadth and course fit |
| Get Utilization | 55 | `email` | utilization string, monthly columns parsed defensively | verified live | current utilization signal | `trainer_operations_df`, `trainer_availability_engine_df` | `index.html`, `team.html`, `trainer-detail.html`, `allocation-desk.html`, `data-health.html` | capacity/load |
| Get trainer Vender Certification Count | 57 | `email` | vendor boolean columns and `Certificate Count` | verified live | vendor coverage count | `trainer_operations_df`, `trainer_availability_engine_df` | `index.html`, `trainer-detail.html`, `team.html`, `capability-builder.html`, `custom-course-match.html` | certification coverage count |
| Trainer Resume Details | 87 | `email` | `TrainerName`, `TrainerEmail`, `TrainerImage`, `Languages`, `Certifications`, `Summary`, `Experience`, `Skill`, `TrainingsDeliveredFor`, `Feedback`, `Interest` | verified live | profile enrichment and certification text | `trainer_operations_df`, `trainer_availability_engine_df`, `trainer_timeline_df` | `trainer-detail.html`, `index.html`, `team.html`, `allocation-desk.html`, `capability-builder.html`, `custom-course-match.html` | profile-level certification and delivery evidence |
| Get Negative Feedback Count | 58 | `email` | `Trainer`, `Email`, `Total` | verified live | quality risk input | `trainer_operations_df`, `trainer_availability_engine_df`, `manager_action_df` | `index.html`, `trainer-detail.html`, `actions.html`, `data-health.html` | negative feedback risk |
| Get HR Incident Positive Negative | 59 | `email` | `Trainer`, `EmailId`, `Positive Count`, `Negative Count` | verified live | HR safety input | `trainer_operations_df`, `trainer_availability_engine_df`, `manager_action_df` | `index.html`, `trainer-detail.html`, `actions.html`, `data-health.html` | HR risk / safety |
| Trainer availability | 90 | `email` | calendar-style availability signal, but not fully stable | best-effort | availability engine input | `trainer_availability_engine_df` | `index.html`, `trainer-detail.html`, `team.html`, `allocation-desk.html`, `data-health.html` | calendar availability |
| Get Trainer Free Shedule and Details | 171 | `email` | free/busy schedule details | best-effort | availability engine input | `trainer_availability_engine_df` | `index.html`, `trainer-detail.html`, `allocation-desk.html`, `data-health.html` | free schedule/capacity |
| Previous & Upcomming Assignments | 16 | `email` | past and upcoming assignment rows | best-effort | workload and delivery history | `trainer_availability_engine_df`, `trainer_timeline_df` | `index.html`, `trainer-detail.html`, `actions.html`, `data-health.html` | workload and experience |
| Trainer_Last_3_Months_Utilization | 39 | `email` | utilization trend / monthly history | best-effort | utilization trend enrichment | `trainer_availability_engine_df`, `trainer_timeline_df` | `index.html`, `trainer-detail.html`, `team.html`, `capability-builder.html`, `data-health.html` | trend signal |
| Get Trainer Feedback Details | 244 | `email` | detailed trainer feedback records | best-effort | richer quality context | `trainer_availability_engine_df`, `trainer_timeline_df` | `index.html`, `trainer-detail.html`, `actions.html`, `data-health.html` | why quality risk exists |
| Trainer RC Schedule | 111 | `email` | RC schedule / blocked schedule evidence | best-effort | availability engine input | `trainer_availability_engine_df` | `index.html`, `trainer-detail.html`, `allocation-desk.html`, `data-health.html` | schedule blockage |
| Course Whitout Exam | 213 | course-related payload | course-exam exemption mapping | best-effort | certification gating support | `trainer_operations_df`, `trainer_availability_engine_df` | `trainer-detail.html`, `allocation-desk.html`, `capability-builder.html`, `custom-course-match.html` | avoid false cert gaps |
| Exam Course Linked API | 215 | course-related payload | exam-to-course mapping | best-effort | certification gating support | `trainer_operations_df`, `trainer_availability_engine_df` | `trainer-detail.html`, `allocation-desk.html`, `capability-builder.html`, `custom-course-match.html` | exam linkage |
| Get Unique Certifications Count Value | 72 | course/trainer related payload | unique cert count | best-effort | certification breadth | `trainer_operations_df`, `trainer_availability_engine_df` | `index.html`, `trainer-detail.html`, `capability-builder.html`, `data-health.html` | unique coverage |
| Course List | 164 | none | `Course`, `Courseid`, `vendor_name`, `vendor_id`, `course_url` | verified live | global course master | `course_allocation_df`, `custom_course_match_df` | `index.html`, `allocation-desk.html`, `custom-course-match.html`, `capability-builder.html` | course master |
| Assignment API | 15 | `TrainerEmailAddres`, `PageNumber`, `PageSize` | assignment rows with trainer/course identifiers | verified live | delivery evidence and allocation inputs | `trainer_operations_df`, `trainer_timeline_df`, `course_allocation_df` | `index.html`, `trainer-detail.html`, `actions.html`, `allocation-desk.html`, `data-health.html` | delivery history |

### B. APIs discussed in project docs but not cleanly integrated

These are referenced in the plan/spec documents and page instructions. They are not part of the stable core backend path unless explicitly noted.

| API name | API key | Input parameters | Response fields / shape | Verified status | Current use in Python | Dataset impacted | Page impacted | Business purpose |
|---|---:|---|---|---|---|---|---|---|
| Get Inhouse and FL Trainers Of Courses | not in backend config | course input | course-to-trainer mapping | not integrated | none | `course_allocation_df` target | `allocation-desk.html`, `custom-course-match.html`, `capability-builder.html` | course-first allocation |
| Get Course Schedule | not in backend config | course input | course schedule rows | not integrated | none | `course_allocation_df` target | `allocation-desk.html`, `capability-builder.html` | course availability |
| Check Course Availability in RMS | not in backend config | course input | availability flag | not integrated | none | `course_allocation_df` target | `allocation-desk.html`, `capability-builder.html` | course schedulability |
| Get Course and Domain | not in backend config | course input | course/domain mapping | blocked / mismatch | none | `course_allocation_df` target | `index.html`, `allocation-desk.html`, `custom-course-match.html`, `capability-builder.html` | domain mapping |
| Get Course Name | not in backend config | course input | readable course name | blocked / mismatch | none | course display helper | `allocation-desk.html`, `custom-course-match.html`, `capability-builder.html` | course normalization |
| Course & Technology List | not in backend config | course/technology payload | technology-to-course map | blocked / mismatch | none | `course_allocation_df` target | `custom-course-match.html`, `capability-builder.html` | technology matching |
| Upcoming Assignments | not in backend config | trainer/email | upcoming assignment rows | not integrated | none | `trainer_availability_engine_df` target | `actions.html`, `allocation-desk.html`, `data-health.html` | future workload |
| Trainer availability | in backend config, but unstable | trainer/email | calendar availability | best-effort | availability engine | `trainer_availability_engine_df` | `index.html`, `team.html`, `trainer-detail.html` | safe allocation |

### C. API status summary

- **Verified live:** the 9 core APIs plus Trainer Resume Details.
- **Best-effort:** the availability and trend APIs that sometimes time out or return partial data.
- **Not integrated:** the course-first and course-metadata APIs that are discussed but not wired into the stable backend path.
- **Blocked / mismatch:** course-domain and course-technology style APIs remain unresolved as clean instruction sheets.

## 2) Original Python agenda

The original notebook was not a dashboard mockup. It was a trainer intelligence system.

### Original goals

- certification mapping
- future certification recommendation
- trainer skill gap detection
- missing course detection
- course upgrade recommendation
- trainer readiness scoring
- Qubit-based trainer strength
- risk-taker trainer identification
- custom course matching
- AI/ML-style trainer-course recommendation
- trainer availability and capacity intelligence
- feedback and HR risk
- manager action recommendation

### What those goals mean in the current build

- **Certification mapping:** now represented by vendor cert count, resume certifications, unique cert count, and exam/course linkage signals.
- **Future certification recommendation:** partially represented by certification attention flags and action recommendations, but not a true roadmap engine.
- **Trainer skill gap:** represented by current skills, missing skills, adjacent skills, and mapped courses.
- **Missing course detection:** represented mainly by course allocation and custom course match heuristics.
- **Course upgrade recommendation:** partly represented by allocation and capability pages.
- **Trainer readiness scoring:** implemented in the backend as readiness score and readiness bucket.
- **Qubit-based strength:** Qubit score is a first-class signal from Trainer Details.
- **Risk-taker identification:** implemented as a rule-based growth/risk bucket, not ML.
- **Custom course matching:** currently works in browser heuristically, not as a server-scored engine.
- **AI/ML-style matching:** not truly ML yet; current matching is rule-based and explainable.
- **Availability intelligence:** exists as a composite engine, not a true calendar system.
- **Feedback / HR risk:** implemented from negative feedback and HR incident APIs.
- **Manager action recommendation:** implemented as deterministic action rows.

## 3) What we have already built

| Item | Backend status | Frontend/page status | Data source | Working | Demo-ready | Production-ready |
|---|---|---|---|---|---|---|
| Unified backend endpoint | built | used by all active pages | `GET /data/unified-manager-intelligence?email=...` | yes | yes | partly |
| Manager-scoped reportee pipeline | built | hidden behind unified endpoint | reportee API + child APIs | yes | yes | partly |
| `trainer_operations_df` | built | used on most pages | backend aggregation | yes | yes | partly |
| `course_allocation_df` | built | used in allocation and course match pages | course list + trainer inputs | yes | yes | partly |
| `trainer_timeline_df` | built | used on trainer detail, actions, capability pages | trainer history and signals | yes | yes | partly |
| `manager_action_df` | built | used on actions and dashboard pages | derived rules | yes | yes | partly |
| `custom_course_match_df` | backend placeholder only | browser-side heuristic match page | unified JSON + local parser | partially | yes | no |
| `data_health_df` | built | used on data-health page and summaries | backend health checks | yes | yes | partly |
| `trainer_availability_engine_df` | built | used on team, trainer detail, dashboard, allocation | composite availability engine | yes | yes | partly |
| `index.html` | frontend built | dashboard cockpit | unified JSON | yes | yes | partly |
| `trainer-detail.html` | frontend built | trainer 360 page | unified JSON | yes | yes | partly |
| `allocation-desk.html` | frontend built | allocation page | unified JSON | yes | yes | partly |
| `custom-course-match.html` | frontend built | browser matching page | unified JSON + local parsing | yes | yes | no |
| `actions.html` | frontend built | action center | unified JSON | yes | yes | partly |
| `team.html` | frontend built | team summary page | unified JSON | yes | yes | partly |
| `capability-builder.html` | frontend built | capability / growth page | unified JSON | yes | yes | partly |
| `data-health.html` | frontend built | health / trust page | unified JSON | yes | yes | partly |

## 4) What is actually complete?

### Fully working

- unified backend endpoint
- reportee-scoped pipeline
- trainer operations aggregation
- readiness scoring
- manager actions
- data health surfacing
- dashboard, trainer detail, allocation, team, actions, capability builder, data health pages as live pages

### Working but needs polish

- availability labels and explanations
- certification source logic
- some page-level evidence cards
- filter consistency across pages

### Partially working

- custom course matching
- availability engine
- timeline depth
- detailed feedback evidence

### Backend exists but UI incomplete

- server-side custom course matching
- some future certification planning logic
- course-first allocation intelligence

### UI exists but backend/local logic only

- custom-course-match.html browser-side outline parsing and ranking

### Blocked by API/data

- true course domain mapping
- true course technology taxonomy
- reliable free/busy calendar certainty
- exam mandate certainty for every course
- market/trend-aligned external skill planning

## 5) What should NOT be built anymore?

These are the wrong directions for the current project:

- new pages that do not support trainer capability intelligence
- a separate platform vision unrelated to the original notebook
- UI-only concepts without a backend signal
- fake market intelligence without real external feeds
- pure calendar or LMS features
- redundant dashboard variants that duplicate the same signals
- new score types that do not map to trainer readiness, allocation, risk, or action
- any feature that hides missing data instead of surfacing it
- any page that bypasses the unified endpoint

## 6) What is still genuinely needed?

Only items that directly support the original agenda:

- fix any remaining page-loading or cache rebuild regression
- keep cleaning `data_health_df` so it explains missing or unstable signals clearly
- stabilize availability labels and reasons
- move custom course matching server-side if the current browser heuristic is not enough
- deepen certification intelligence where the data is actually available
- complete timeline and action evidence only if they improve the manager decision path
- reduce external font/CORS noise if it hurts demo quality
- continue normalizing names, signals, and missing-data explanations

## 7) Page-by-page truth table

| Page | Purpose | Dataset used | APIs behind it | Status | Known issue | Next action |
|---|---|---|---|---|---|---|
| `index.html` | manager dashboard cockpit | `trainer_operations_df`, `manager_action_df`, `course_allocation_df`, `trainer_timeline_df`, `data_health_df` | reportee, trainer details, skills, utilization, vendor certs, feedback, HR, assignments, resume details | working | some summary logic still depends on imperfect availability/cert data | keep polishing consistency |
| `trainer-detail.html` | one trainer 360 view | `trainer_operations_df`, `course_allocation_df`, `trainer_timeline_df`, `manager_action_df`, `data_health_df` | same as above plus resume details | working | some sections rely on partial signals | keep polishing evidence and consistency |
| `allocation-desk.html` | course-to-trainer allocation | `course_allocation_df`, `trainer_operations_df`, `trainer_availability_engine_df`, `data_health_df` | reportee, skills, assignments, availability, course list | working | course-domain and course-technology support is still weak | refine blocker logic |
| `custom-course-match.html` | custom outline to trainer match | browser heuristic + unified JSON, mainly `trainer_operations_df`, `trainer_availability_engine_df` | reportee, skills, availability, resume details, course list | partly working | parser is still browser-side heuristic | move backend if needed |
| `actions.html` | manager action center | `manager_action_df`, `trainer_operations_df`, `course_allocation_df`, `data_health_df` | derived from all core signals | working | action persistence is not a real workflow engine | keep recommendation-focused |
| `team.html` | team summary and filters | `trainer_operations_df`, `trainer_availability_engine_df`, `manager_action_df`, `data_health_df` | reportee, details, utilization, feedback, HR | working | depends on availability signal quality | stabilize availability labels |
| `capability-builder.html` | growth and upgrade planning | `trainer_operations_df`, `course_allocation_df`, `manager_action_df`, `trainer_timeline_df`, `data_health_df` | core trainer signals and recommendations | working | future skill planning is still rule-based | keep as development-planning view |
| `data-health.html` | trust and completeness view | `data_health_df`, `trainer_availability_engine_df`, `trainer_operations_df` | all backend health checks | working | some upstream APIs are still unstable | continue cleaning status rows |
| `risk-takers.html` | planned growth/risk exploration | `trainer_operations_df`, `trainer_availability_engine_df`, `course_allocation_df`, `manager_action_df`, `data_health_df` | same core signals | built or planned depending on current branch state | may overlap with capability-builder | avoid expanding unless clearly necessary |
| `quality-risk.html` | planned quality-risk detail | likely `trainer_operations_df`, `manager_action_df`, `data_health_df` | feedback / HR | not clearly established | redundant with trainer detail and actions | only build if it adds unique value |
| `timeline.html` | planned chronology page | likely `trainer_timeline_df`, `trainer_availability_engine_df`, `data_health_df` | assignments, feedback, utilization | not clearly established | timeline may duplicate trainer detail | build only if chronology adds value |

## 8) API-to-dataset-to-page matrix

| API | Dataset | Page | Business decision |
|---|---|---|---|
| Get Direct / Indirect Reportee | `trainer_operations_df` | `index.html`, `team.html`, `trainer-detail.html` | who is in scope |
| Get Trainer Details | `trainer_operations_df`, `trainer_timeline_df` | `index.html`, `trainer-detail.html`, `capability-builder.html` | readiness and trainer strength |
| Get Trainer Skills | `trainer_operations_df`, `course_allocation_df` | `index.html`, `allocation-desk.html`, `custom-course-match.html`, `capability-builder.html` | skill fit and missing skills |
| Get Utilization | `trainer_operations_df`, `trainer_availability_engine_df` | `index.html`, `team.html`, `trainer-detail.html`, `allocation-desk.html`, `data-health.html` | capacity and overload risk |
| Get trainer Vender Certification Count | `trainer_operations_df`, `trainer_availability_engine_df` | `index.html`, `trainer-detail.html`, `team.html`, `capability-builder.html`, `custom-course-match.html` | certification coverage |
| Trainer Resume Details | `trainer_operations_df`, `trainer_availability_engine_df`, `trainer_timeline_df` | `trainer-detail.html`, `index.html`, `team.html`, `allocation-desk.html`, `capability-builder.html`, `custom-course-match.html` | visible certs, experience, profile evidence |
| Get Negative Feedback Count | `trainer_operations_df`, `trainer_availability_engine_df`, `manager_action_df` | `index.html`, `trainer-detail.html`, `actions.html`, `data-health.html` | quality risk |
| Get HR Incident Positive Negative | `trainer_operations_df`, `trainer_availability_engine_df`, `manager_action_df` | `index.html`, `trainer-detail.html`, `actions.html`, `data-health.html` | HR risk |
| Trainer availability | `trainer_availability_engine_df` | `index.html`, `team.html`, `trainer-detail.html`, `allocation-desk.html`, `data-health.html` | can we use this trainer safely |
| Get Trainer Free Shedule and Details | `trainer_availability_engine_df` | `index.html`, `trainer-detail.html`, `allocation-desk.html`, `data-health.html` | schedule capacity |
| Previous & Upcomming Assignments | `trainer_availability_engine_df`, `trainer_timeline_df` | `index.html`, `trainer-detail.html`, `actions.html`, `data-health.html` | workload and future evidence |
| Trainer_Last_3_Months_Utilization | `trainer_availability_engine_df` | `index.html`, `trainer-detail.html`, `team.html`, `capability-builder.html`, `data-health.html` | trend-based capacity |
| Get Trainer Feedback Details | `trainer_availability_engine_df`, `trainer_timeline_df` | `index.html`, `trainer-detail.html`, `actions.html`, `data-health.html` | why quality risk exists |
| Trainer RC Schedule | `trainer_availability_engine_df` | `index.html`, `trainer-detail.html`, `allocation-desk.html`, `data-health.html` | blocked or constrained schedule |
| Course List | `course_allocation_df`, `custom_course_match_df` | `index.html`, `allocation-desk.html`, `custom-course-match.html`, `capability-builder.html` | course master and candidate list |
| Assignment API | `trainer_operations_df`, `trainer_timeline_df`, `course_allocation_df` | `index.html`, `trainer-detail.html`, `actions.html`, `allocation-desk.html`, `data-health.html` | delivery history |
| Course Whitout Exam | certification logic | `trainer-detail.html`, `allocation-desk.html`, `capability-builder.html`, `custom-course-match.html` | avoid false cert gaps |
| Exam Course Linked API | certification logic | `trainer-detail.html`, `allocation-desk.html`, `capability-builder.html`, `custom-course-match.html` | exam-gated courses |
| Get Unique Certifications Count Value | certification logic | `index.html`, `trainer-detail.html`, `capability-builder.html`, `data-health.html` | unique cert breadth |

## 9) Original agenda completion matrix

| Original goal | Built? | Where built | Data used | Gap | Next action |
|---|---|---|---|---|---|
| Certification mapping | yes, partial | `trainer-detail.html`, `index.html`, `capability-builder.html` | vendor certs, resume certs, unique cert count | exam-mandate and ROI still weak | keep refining certification source logic |
| Future certification recommendation | partial | `actions.html`, `capability-builder.html` | cert counts, resume certs, course allocation | not a true roadmap engine | keep as decision support only |
| Trainer skill gap | yes | `trainer-detail.html`, `custom-course-match.html`, `capability-builder.html` | skills, courses, availability | taxonomy is still simple | maintain normalized skill labels |
| Missing course detection | partial | `allocation-desk.html`, `custom-course-match.html` | course list, skills, readiness | course-domain data missing | keep with honest limitations |
| Course upgrade recommendation | partial | `capability-builder.html`, `actions.html` | readiness, allocation, actions | not fully course-specific | refine only if it improves manager decisions |
| Trainer readiness scoring | yes | `index.html`, `trainer-detail.html`, `team.html` | details, skills, assignment, certs, feedback, HR, utilization | depends on missing signals | stabilize confidence / missing-signal disclosure |
| Qubit-based trainer strength | yes | `index.html`, `trainer-detail.html` | Trainer Details | Qubit alone is not enough | keep in evidence panel |
| Risk-taker trainer identification | yes, rule-based | `risk-takers.html`, `capability-builder.html`, `custom-course-match.html` | readiness, diversity, feedback, HR, availability | not ML | keep explainable |
| Custom course matching | partial | `custom-course-match.html` | browser parser + unified JSON | server-side parser missing | backend only if needed |
| AI/ML-style trainer-course recommendation | partial | `custom-course-match.html`, `allocation-desk.html` | rule-based ranking only | not actual ML | do not overstate this as ML |
| Trainer availability / capacity intelligence | yes, partial | `trainer_availability_engine_df`, `index.html`, `team.html`, `trainer-detail.html` | utilization, assignment, schedule, feedback, HR | calendar signals can be unstable | stabilize and explain unknowns |
| Feedback / HR risk | yes | `index.html`, `trainer-detail.html`, `actions.html`, `data-health.html` | negative feedback, HR incident, feedback details | quality details still partial | keep surfacing evidence |
| Manager action recommendation | yes | `actions.html`, `index.html`, `trainer-detail.html` | all core trainer signals | not a persistence workflow | keep as recommended action list |

## 10) Final recommendation

### Current completion estimate

**We are roughly 80% done.**

That estimate is based on the fact that the core spine is working:
- unified backend exists
- the manager-scoped pipeline exists
- the main datasets exist
- the main pages exist
- readiness, availability, allocation, actions, and health are all represented

What is still incomplete is not the whole product. It is the trust layer and a few deeper intelligence pieces.

### Top 5 fixes before demo

1. Clean up any remaining page-loading or cache rebuild regression.
2. Stabilize availability labels so they are not contradictory.
3. Tighten `data_health_df` so the missing or unstable signals are plain and honest.
4. Reduce the external font/CORS noise if it affects the demo experience.
5. Make the certification source logic consistent across trainer detail, dashboard, and team views.

### Top 5 fixes before production

1. Move custom course matching server-side if browser-only heuristics are not enough.
2. Finish the deeper availability engine rules and confidence explanations.
3. Improve certification gating where the API evidence actually supports it.
4. Expand timeline and action evidence only where it adds real manager value.
5. Add stronger backend validation for unstable or mismatched API responses.

### What should be paused

- any new page that does not directly support trainer intelligence
- any new scoring concept that cannot be traced back to the original agenda
- any separate platform vision
- any fake ML framing
- any UI work that is not backed by a real data signal

