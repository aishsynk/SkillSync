# SkillEdge API Integration and Data Design

## Purpose

This document is the implementation authority for using the 28 supplied Trainer Portal/RMS APIs in the redesigned SkillEdge manager operating system. It defines ownership, joins, filtering, source precedence, canonical records, derived decisions, and failure handling.

Credentials are deliberately excluded. All credentials must be rotated and supplied through a protected secret store or environment configuration.

## 1. Operating principles

1. The reportee API defines the manager's allowed trainer scope. No trainer record may be exposed merely because another API returns it.
2. IDs are preferred to names. Use normalized email only when a stable ID is unavailable.
3. Course ID is preferred to course name. Name matching is a controlled fallback with normalization and ambiguity reporting.
4. Assignment/schedule evidence determines current engagement. Utilization never proves that a trainer is teaching now.
5. Declared skills, demonstrated delivery, approvals, assessments, certifications, feedback, and availability remain separate signals.
6. Hard constraints override ranking scores.
7. Unknown is not zero, free, safe, or false.
8. Every derived record retains source API, source row ID, retrieval time, effective dates, and transformation version.
9. Read APIs may be refreshed automatically. The Add Trainer Skill API requires an explicit manager confirmation, validation, idempotency protection, and audit event.
10. Undocumented `"[...results...]"` responses require schema discovery and contract fixtures before production use.

## 2. Canonical keys and normalization

### Trainer key

Create a trainer identity bridge with:

- `trainer_id`: `TrainerId` from reportees;
- `employee_id`: `EmpId` / `employee_code` / `EmployeeCode`;
- `trainer_email`: lower-case trimmed `OffEmail`, `OffEmailId`, `TrainerEmail`, `Email`, or `EmailId`;
- `trainer_name`: display only, never the primary join;
- `trainer_key`: stable internal UUID mapped to the identifiers above.

Join priority:

1. trainer ID;
2. employee ID;
3. exact normalized official email;
4. quarantined manual identity match.

Never automatically join on trainer name alone.

### Course key

Create a course identity bridge with:

- `course_id`: normalized `Courseid`, `CourseID`, `course_id`, `Cid`, or `CId`;
- `course_code`;
- canonical course name;
- aliases from the various API spellings;
- vendor ID/name;
- technology ID/name;
- domain;
- status, duplicate and discontinued flags;
- URL, TOC, duration and exam policy.

Join priority:

1. course ID;
2. exact normalized course code;
3. exact normalized course name;
4. reviewed fuzzy alias match.

Name normalization should case-fold, trim, collapse whitespace, normalize punctuation and Unicode, but must not remove version numbers or product codes.

### Assignment/batch key

Use `AssignmentID` / `AssignmentId` as the canonical external assignment identifier. If a schedule API supplies a distinct batch/session ID, retain both and create a bridge. A date/course/trainer composite is only a provisional fallback and must be marked low confidence.

### Dates and time

- Parse all source dates into timezone-aware UTC timestamps while retaining original values.
- Interpret `StarDate` as source spelling for start date.
- Store manager, trainer, batch and customer timezone where available.
- Define "current" as `start_at <= now < end_at` plus an active assignment state.
- Use half-open time intervals to prevent midnight double-counting.

## 3. API catalogue and exact ownership

### 3.1 Scope and identity

#### API 82 — Get Direct Indirect Reportee

Request: `email`.

Fields: `TrainerName`, `TrainerId`, `EmpId`, `OffEmail`, `TrainerPlus`, `IsdirectReportee`, `Designation`.

Use:

- authoritative manager-to-trainer scope;
- seed the trainer identity bridge;
- separate direct and indirect reportees;
- restrict every downstream result to this set.

Filters:

- valid official email or stable trainer/employee ID;
- deduplicate by trainer ID, then employee ID, then email;
- retain rows with missing email for data-health repair but do not make email-based downstream calls.

Refresh: login, manual refresh, and scheduled hierarchy refresh.

### 3.2 Trainer capability and profile

#### API 75 — Get Trainer Details

Request: `email`.

Fields include course, vendor, Qubit score, skill level, official approval, future-skill flag/date, DM, technical-call rating, course assignment and delivery-window restrictions.

Grain: trainer-course capability row.

Use:

- verified/mapped capability;
- approval and assessment evidence;
- future-skill pipeline;
- delivery-window constraints;
- TOC candidate scoring.

Joins: trainer by official email; course through canonical course bridge; vendor through course/vendor bridge.

Filters:

- exclude discontinued/duplicate courses from assignable capability;
- do not discard them from history;
- treat `OfficiallyApproved` as a separate mandatory/optional constraint, not as a skill score;
- parse Qubit and technical-call rating independently;
- future-skill rows cannot count as ready-now capability without verification.

#### API 217 — Get Trainer Skills

Request: `employee_id` from reportee `EmpId`.

Fields: employee identity, course ID/name, duplicate and discontinued flags.

Grain: trainer-course mapping.

Use:

- mapped course capability;
- course coverage and missing-course analysis;
- candidate retrieval for standard demand;
- upgrade adjacency seed.

Joins: employee code to trainer identity; course ID to course master.

Filters: active, non-duplicate mappings for current allocation; retain excluded rows with exclusion reason for audit.

#### API 87 — Trainer Resume Details

Request: `email`.

Fields: languages, certifications, summary, experience, skills, deliveries, feedback text and interests.

Grain: trainer profile snapshot.

Use:

- declared skills and interests;
- language/context fit;
- certification names;
- candidate retrieval and explanation enrichment.

Policy: resume evidence is declared evidence. It cannot by itself prove delivery readiness. Parse into normalized child records while preserving the original strings.

#### API 57 — Trainer Vendor Certification Count

Request: `email`; response schema must be captured from live fixtures.

Use: vendor-level certification coverage and count cross-check.

Policy: count-only evidence cannot identify a certification or prove course-specific accreditation.

#### API 72 — Unique Certifications Count

Request: `email`; response schema must be captured.

Use: independent certification-count reconciliation and data-health check.

Policy: discrepancies among resume, vendor count and unique count create a certification data-quality warning; they must not be silently merged into the largest number.

#### API 255 — Add Trainer Skill (IDP)

Request: course ID, trainer email, skill level, official approval, effective date.

Type: write/mutation.

Use only after a manager approves a development plan or authorized skill update.

Controls:

- validate trainer is in manager scope;
- validate course is active and canonical;
- validate allowed skill levels and ISO date;
- prohibit the UI from directly calling RMS;
- show a confirmation summary;
- use idempotency key and check existing mapping;
- record actor, before/after state, request hash, RMS response and timestamp;
- never set official approval merely because AI recommended an upgrade.

### 3.3 Delivery, batch and availability

#### API 15 — Assignment API

Request: trainer email, page number, page size.

Fields: assignment ID, course ID/name, trainer name/email.

Grain: trainer-assignment index.

Use:

- obtain stable assignment IDs;
- establish assignment/course ownership;
- drive paginated retrieval and feedback-detail calls.

Filters: paginate until an empty page or total boundary; deduplicate on assignment ID; scope trainer before storing.

Limitation: no dates, so it cannot determine current engagement alone.

#### API 16 — Previous & Upcoming Assignments

Request: start date, end date, email.

Fields: course, mode, participants, start/end date, trainer, vendor, assignment ID, start/end time and location.

Grain: scheduled assignment/batch.

Use:

- primary source for current, previous and upcoming engagement;
- batch timeline;
- date-aware availability and load;
- similar delivery evidence;
- feedback join using assignment ID.

Filters:

- fetch a bounded historical and future window;
- classify by actual timestamps, not API label;
- retain cancelled/invalid states if the API exposes them after schema verification;
- deduplicate by assignment ID plus session timing where one assignment has multiple sessions.

#### API 93 — Upcoming Assignments

Request: date range, trainer ID, employee code; response schema must be discovered.

Use:

- future assignment confirmation;
- reconcile API 16 and detect missing upcoming rows;
- capacity for 7/14/30-day horizons.

Join: trainer ID and employee code from scope; assignment ID where returned.

Precedence: use as confirmation/future-specialist source. A conflict with API 16 is surfaced, not overwritten.

#### API 111 — Trainer RC Schedule

Request: trainer email and date range; response schema must be discovered.

Use:

- time-block and restricted-calendar evidence;
- conflict detection;
- availability reconciliation.

Do not confuse this with API 104 despite the misleading duplicate filename "Check Course Availability in RMS.txt".

#### API 90 — Trainer Availability

Request: course name, start date, end date.

Fields: trainer/email, course and total assignments, language, skill and MTI issue.

Grain: course-and-date candidate availability result.

Use:

- demand-time candidate retrieval;
- language and MTI constraint evidence;
- course/date workload signal.

Important: this API is course-first, not trainer-current-status. Do not call it once per trainer using an arbitrary first course. Call it for a concrete course/TOC candidate and requested dates.

#### API 171 — Get Trainer Free Schedule and Details

Request: course; response schema must be discovered.

Use:

- course-first free-trainer candidate discovery;
- cross-check candidate pool from API 90.

Limitation: without explicit requested dates in the documented request, it cannot prove date-specific availability unless the discovered response includes a reliable time window.

#### API 246 — Get Course Schedule

Request: course, country, region, delivery mode; response schema must be discovered.

Use:

- available course schedule/demand context;
- location and delivery-mode fit;
- standard-course planning.

This is course schedule information, not trainer schedule, unless live schema proves otherwise.

#### API 39 — Last Three Months Utilization

Request: employee code.

Fields: employee identity, manager, utilization and month.

Grain: trainer-month.

Use:

- workload trend;
- under/over-utilization;
- growth timing and bench opportunity;
- ranking modifier.

Filters: canonical month parsing; deduplicate employee/month; report gaps; never use utilization as a live-batch indicator.

#### API 55 — Get Utilization

Request: email; response schema must be captured.

Use: current utilization snapshot.

Reconcile with API 39 by effective period. Store both raw measures if definitions differ.

### 3.4 Feedback and people risk

#### API 244 — Get Trainer Feedback Details

Request: trainer email, assignment ID and SCID.

Fields: feedback date, question, MCQ answer and text answer plus assignment/trainer identity.

Grain: response to a feedback question for an assignment/session.

Use:

- evidence-backed feedback history;
- question-level scoring;
- theme and sentiment analysis;
- course/domain-specific quality fit;
- coaching recommendations.

Joins: assignment ID to batch; trainer email to identity; SCID retained as session/customer-course key after schema discovery.

Filters:

- do not classify blank answers;
- interpret MCQ using question-specific scale metadata, never a universal numeric assumption;
- separate learner comments from system/admin text where identifiable;
- redact personal data where required;
- AI summaries must cite feedback row IDs.

#### API 58 — Negative Feedback Count

Request: email.

Fields: trainer/email and total.

Use: fast risk flag and reconciliation with detailed feedback.

Policy: count alone cannot explain risk, severity, course relevance or recency. It triggers review; it does not automatically block unless an approved policy threshold and detailed evidence support it.

#### API 59 — HR Incident Positive/Negative

Request: email.

Fields: positive and negative counts.

Use: permission-controlled people/delivery safety signal.

Policy:

- keep HR data in a restricted model;
- expose only the minimum decision signal to ordinary managers;
- never send sensitive incident content to an external AI model without approved governance;
- count-only data produces a review requirement, not an invented explanation.

### 3.5 Course, technology, domain and certification graph

#### API 164 — Course List

No request fields.

Fields: course ID/name, vendor ID/name and URL.

Use: primary active course catalogue and vendor bridge.

Refresh: scheduled reference-data refresh; version snapshots.

#### API 70 — Get Course Name

No request fields.

Fields: ID, name, code, vendor, duration, page and TOC.

Use:

- enrich API 164 with code, duration and TOC;
- source official TOCs for standard-course matching;
- build the course alias bridge.

Reconciliation: join by course ID; log name/vendor differences instead of choosing silently.

#### API 114 — Course & Technology List

No request fields.

Fields: technology and course IDs/names.

Grain: course-technology edge.

Use: core technology graph, TOC retrieval, adjacent-course planning and team coverage.

Filters: active canonical courses only for recommendations; preserve historical edges.

#### API 205 — Get Course and Domain

Request: technology name.

Fields: course ID/name and domain.

Use: course-domain edge and technology/domain expansion.

Calling strategy: seed with distinct normalized technologies from API 114; cache results; do not call per page load.

#### API 104 — Check Course Availability in RMS

Request: course name.

Fields: availability, duplicate, discontinued and status.

Use:

- validate a TOC-extracted or Sales-entered course against RMS;
- exclude duplicate/discontinued courses from new mappings;
- choose existing-course versus custom-course workflow.

Do not use it for trainer availability.

#### API 213 — Course Without Exam

No request fields.

Fields: course ID/name, exam requirement, status and vendor.

Use: prevent false certification blockers and enrich course policy.

#### API 215 — Exam Course Linked

Request supports course ID, exam ID and without-exam flag; response schema must be discovered.

Use: explicit course-exam-certification relationship.

Policy: an exam/certification can become a mandatory blocker only when the course policy and organizational rule require it.

### 3.6 Demand and candidate discovery

#### API 190 — Unallocated Assignment

No request fields; response schema must be discovered.

Use:

- live Sales/delivery demand inbox;
- unfulfilled assignments requiring matching;
- demand-driven capability-gap analysis.

Required discovered fields should include assignment/demand ID, course, dates, customer/location/mode, language, status and urgency. Until confirmed, this API cannot be treated as a production demand contract.

#### API 157 — In-house and Freelance Trainers of Courses

Request: course and trainer type; response schema must be discovered.

Use:

- broaden candidate retrieval beyond direct reportees only when policy permits;
- compare in-house and freelance coverage;
- identify backup supply.

Security: results must be filtered to the manager's authorized population or presented as anonymized availability. This API must never bypass scope.

## 4. Source precedence by business question

### What is a trainer doing now?

1. API 16 dated assignment/session containing current time.
2. API 93 dated upcoming/current evidence after schema validation.
3. API 111 dated RC/calendar block.
4. API 15 assignment identity without date: supporting evidence only.
5. API 55/39 utilization: contextual evidence only.

Output statuses:

- `teaching_now`: active dated batch;
- `scheduled_today`: dated batch later today;
- `preparing`: upcoming batch within policy window and no current batch;
- `blocked`: dated calendar/RC restriction;
- `free`: complete schedule evidence shows no conflict for the defined interval;
- `unknown`: insufficient or conflicting evidence.

### Can this trainer deliver a course?

Evidence order:

1. successful similar delivery history;
2. active approved course mapping;
3. assessment/Qubit/technical evidence;
4. certification/accreditation where required;
5. resume/declaration;
6. technology adjacency.

Adjacency and resume evidence can create a development candidate, not a safe primary by themselves.

### Is this trainer available for requested dates?

Combine API 16, 93, 111, 90 and validated 171 responses. Any overlap with a confirmed assignment/restriction is a blocker unless the source explicitly supports partial-day capacity and times do not overlap.

### How is the trainer performing?

Use detailed feedback API 244 as evidence, API 58 as reconciliation, delivery history and approved technical assessment signals. HR API 59 remains a separate restricted risk dimension.

## 5. Canonical data products

The ingestion layer should create normalized source tables, then the following authoritative products:

### `manager_trainer_scope`

One row per manager/trainer relationship with directness, identity and effective dates.

### `trainer_profile`

One row per trainer with designation, languages, experience, interests and profile freshness.

### `trainer_course_capability`

One row per trainer/course containing mapped skill, approval, future-skill state, assessments, certification requirement/status, delivery proof, confidence and exclusions.

### `batch_engagement`

One row per assignment/session with course, trainer, dates, mode, location, participants and source reconciliation.

### `trainer_current_state`

One row per trainer with current status, current batch, next batch, conflicts, confidence, evidence IDs and refresh time.

### `trainer_feedback_fact`

Question/answer-level feedback with assignment, date, normalized theme, sentiment, severity and source evidence.

### `trainer_performance_summary`

Time- and course-aware strengths, recurring issues, feedback trend, coaching state and confidence.

### `course_master`

Canonical course plus aliases, vendor, technology, domain, duration, TOC, lifecycle and exam policy.

### `demand_request`

Sales/unallocated/custom demand with dates, constraints, TOC, extraction, status and owner.

### `candidate_match_decision`

Versioned trainer-demand ranking with decomposed scores, hard blockers, evidence, gaps, confidence, preparation plan and role.

### `development_plan`

Manager-approved upgrade target, gaps, actions, verification, due dates, progress and optional IDP writeback.

## 6. API orchestration

### Manager refresh

1. Fetch reportees (82).
2. Build/refresh identity scope.
3. In parallel per scoped trainer, fetch details (75), skills (217), resume (87), utilization (55/39), certification counts (57/72), assignment index (15), dated assignments (16/93), RC schedule (111), feedback count/details (58/244), and HR signal (59) subject to access.
4. Fetch reference data (164, 70, 114, 213) from shared cache, not per trainer.
5. Expand domains via 205 and exam links via 215 in background reference jobs.
6. Normalize, reconcile and publish canonical records atomically.
7. Preserve the previous good snapshot if a source refresh fails, but label stale data explicitly.

### Command Center request

Read only canonical records. Do not fan out to RMS from the browser or recompute status in JavaScript.

### Standard course allocation

1. Resolve course through course master and API 104.
2. Retrieve scoped candidates from capability/delivery evidence.
3. Query course/date availability through 90 and validated 171, then reconcile schedules.
4. Apply requirements, ranking and blocker policy.
5. Return best, backup, verification and development candidates.

### Custom TOC/Sales request

1. Accept secure upload or paste and requested delivery constraints.
2. Extract topics, technologies, level, labs, prerequisites, duration, language, mode and certification alignment.
3. Map exact/alias concepts using APIs 70, 114, 164 and 205; use API 104 to determine whether this is an existing RMS course.
4. Retrieve candidates using demonstrated deliveries, mappings, resume skills and technology adjacency.
5. Reconcile requested-date availability.
6. Score and explain candidates using the versioned ranking policy.
7. Require manager confirmation; store decision and later outcome.

### Unallocated demand

Fetch API 190 into `demand_request`, enrich course and constraints, run the same matching pipeline, and show it in the manager Demand Inbox.

## 7. Candidate ranking policy

Initial dimensions:

| Dimension | Weight | Primary APIs |
|---|---:|---|
| Demonstrated skill/topic fit | 30 | 16, 75, 217, 70, 114, 205 |
| Similar delivery history | 20 | 15, 16 |
| Requested-date availability | 15 | 16, 93, 111, 90, 171 |
| Feedback and delivery quality | 12 | 244, 58 |
| Approval/certification | 10 | 75, 57, 72, 87, 213, 215 |
| Technical assessment | 8 | 75 |
| Language/context/mode fit | 5 | 87, 90, 16, 246 |

Hard blockers include confirmed date conflict, required approval missing, mandatory accreditation missing, discontinued course mapping, restricted HR/delivery policy, or insufficient identity/scope.

Optional missing signals reduce confidence. Mandatory missing signals block or require manual verification. Scores must not hide missing evidence.

## 8. Filters that must always be applied

- manager scope before exposure;
- canonical active course status for new allocations;
- duplicate/discontinued exclusion for current skills;
- effective date/window for schedules, utilization, approvals and future skills;
- cancellation/status filtering after response schema discovery;
- timezone-aware overlap detection;
- assignment/course relevance for feedback;
- permission filters for HR data;
- minimum evidence thresholds for primary recommendations;
- freshness thresholds by source;
- deterministic deduplication with quarantine for ambiguous joins.

## 9. Missing contracts and verification backlog

The following APIs have undocumented response bodies and require a read-only schema probe, sanitized fixture and contract test before relying on them:

- 39/55 if live shape differs from documented normalization;
- 57 and 72 certification counts;
- 93 upcoming assignments;
- 111 trainer RC schedule;
- 171 free schedule;
- 246 course schedule;
- 157 in-house/freelance trainers;
- 190 unallocated assignments;
- 215 exam-course links.

For each, capture:

- actual envelope and content type;
- field names/types/nullability;
- date/time format and timezone;
- stable IDs;
- pagination and limits;
- status/cancellation values;
- empty/error behavior;
- representative sanitized fixture.

No production logic should guess these schemas.

## 10. Product surfaces powered by the model

### Command Center

- reportee current state and current batch;
- today/next-7-days batches;
- free/blocked/unknown trainers;
- clashes, feedback risks, stale data and actions;
- unallocated demand.

### Team & Trainer 360

- identity/profile;
- current and historical engagement;
- capabilities and evidence;
- feedback themes and performance trend;
- certifications, risks, upgrade plan and manager actions.

### Demand & Allocation

- unallocated assignments;
- standard course search;
- custom TOC upload;
- ranked candidate comparison;
- availability and blocker evidence;
- primary/backup/mentor/development decision.

### Capability Planning

- current coverage by vendor/technology/domain;
- demand gaps;
- trainer upgrade paths;
- IDP approval/writeback;
- backup/succession development.

### Data Health and Audit

- source freshness/failure;
- identity/course join exceptions;
- cross-source conflicts;
- decision versions, overrides and RMS write history.

## 11. Implementation sequence

1. Rotate secrets and remove source credentials.
2. Add missing API configurations without calling mutation API 255.
3. Implement schema-probe tests and sanitized fixtures for undocumented responses.
4. Build identity and course bridges.
5. Build canonical batch engagement and trainer current state.
6. Replace current frontend status calculations with backend records.
7. Build detailed feedback facts and summaries.
8. Build capability evidence and demand-aware upgrades.
9. Build standard allocation from the canonical model.
10. Add secure TOC ingestion and custom demand matching.
11. Add unallocated-demand inbox and outcome capture.
12. Enable confirmed IDP writeback with audit controls.

## 12. Definition of done

The makeover is complete only when a manager can:

- see every scoped reportee's current and next engagement with confidence and freshness;
- open the exact batches and evidence behind that status;
- understand feedback, risks, skills and certifications without contradictory scores;
- see realistic, demand-linked upgrade paths;
- upload a TOC with delivery constraints and receive an auditable shortlist;
- compare primary, backup, mentor and development candidates;
- understand every match and gap;
- approve/override a decision with an audit trail;
- track whether the eventual delivery succeeded;
- trust that missing or stale data is never presented as certainty.
