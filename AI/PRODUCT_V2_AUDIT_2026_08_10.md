# SkillEdge Version 2 Product and API Audit

Date: 2026-08-10  
Scope: all 37 files in `trainer_portal_api_details`, `backend.py`, Android API/repository/ViewModel/navigation/UI code, tests, deployment configuration and current production contracts.  
Constraint: this is an evidence review and redesign proposal. No product code was changed.

## Executive verdict

If SkillEdge were designed today using the full API estate, it should not retain its current five-tab, screen-per-dataset structure. Version 1 is a useful operational viewer with emerging decision support, but it is not yet a complete Manager Command Centre. It aggregates substantial RMS data, yet still organizes the experience around Dashboard, Team, Courses, Demand and Actions instead of the manager's recurring decisions: protect delivery, place capacity, close capability gaps, resolve risk and plan future supply.

The API estate is richer than the Android surface, but it is not uniformly production-ready. Of 37 documented capabilities:

- 27 are registered in the backend.
- Several registered APIs are dormant or have no Android workflow: upcoming assignments, course availability validation, active SC/commercial data, SCID lookup, recording details and the dedicated trainer-availability endpoint.
- 10 are absent from the backend integration: trainer RC schedule/free schedule, course-technology taxonomy, alternate course list, exam-course links, course-domain mapping, course content URL, course modules, latest course version, unique certification count and trainer free-schedule details.
- Some capabilities overlap or contradict one another and need a canonical semantic layer before use.
- Several documented response schemas contain placeholders rather than fields; they must be live-contract tested before product commitments.

The most important finding is not a missing chart. It is a foundation risk: RMS credentials are present in local documentation and as source-code fallbacks, Android login accepts an email address without proving identity, backend data/write routes do not enforce the issued session, and the session store is process memory only. No additional HR, participant, feedback, commercial or recording data should be exposed until authentication, authorization, secret rotation, audit logging and field-level access are corrected.

## What Version 1 actually is

The Android app exposes five primary destinations plus Trainer 360 and Demand Detail:

1. Dashboard: compact command-centre KPIs, charts, health/risk, demand and actions.
2. Team: two-column manager cards and filters.
3. Courses: team course ownership, certification mapping, direct/bulk skill assignment.
4. Demand: unallocated demand, ranking, international priority treatment and skill marking.
5. Actions: derived and manager-created action inbox.
6. Trainer 360: profile, utilisation, capability, certification gaps, delivery, availability, feedback and actions.
7. Demand Detail: opportunity evidence and candidates.

Android calls 15 SkillEdge backend endpoints. It never talks directly to RMS, which is the correct boundary. The backend already uses many RMS capabilities to synthesize manager datasets, but Android consumes only the normalized fields chosen for those endpoints. Search is course-only and contextual; Reports are limited to trainer export; Notifications are local rule outputs rather than a durable event system; Analytics have no historical warehouse; Resource Planning is a ranked demand list rather than a planning workspace.

## Complete API capability inventory

### Materially used in current product logic

| RMS capability | Key | Useful fields/capability | Current use | Important gap |
|---|---:|---|---|---|
| Direct/Indirect Reportees | 82 | trainer identity, employee/trainer IDs, reporting relationship, designation, Trainer Plus | Team scope, identity and login role check | No hierarchy visualization, delegated teams, span-of-control analytics or explicit access policy |
| Trainer Details | 75 | course/vendor, Qubits, skill level, approval, future skill/date, tech-call rating, course assignment, multiple off-date types | Capability, readiness and off-date evidence | Several fields are not consistently visible: future-skill date, tech-call rating, delivery-window-specific off dates, DM and course-assignment evidence |
| Utilisation | 55 | monthly load/utilisation series | Team KPIs, capacity and status | No retained history, forecast, plan-vs-actual or utilization confidence dashboard |
| Previous & Upcoming Assignments | 16 | course, mode, pax, dates/times, trainer, vendor, assignment, location | Current/upcoming state, Trainer 360 delivery and availability | No calendar-first operations view, conflict resolution or assignment lifecycle workspace |
| Unallocated Assignments | 190 | demand, course, dates, mode, city, pax, assignment, TOC/course URL and commercial/SC text | Demand and Dashboard | No scenario planner, allocation write-back, SLA aging, owner, workflow state or demand history |
| Course Without Exam | 213 | course, exam requirement, status, vendor | Certification-gap denominator | Course status is not surfaced; discontinued/obsolete learning paths can still confuse users |
| Assignment API | 15 | assignment/course/trainer references | Read-only fallback when calendar API fails | No dates; correctly not treated as availability, but health/source status is not manager-visible |
| Negative Feedback Count | 58 | trainer and count | Team risk, actions and notifications | Count is blunt; denominator, trend, recency and severity are missing |
| Trainer Feedback Details | 244 | question, MCQ/text answer, date, assignment and SC | Trainer 360 responses | Live response remains unverified; raw sample scaffolding exists and should not ship long-term |
| HR Positive/Negative Incidents | 59 | positive and negative incident counts | Trainer 360 risk calculation | Sensitive data has no field-level authorization, provenance or manager-facing explanation |
| Trainer Negative Feedback | 218 | client, CSM, SC, question/answer, dates, mode and assignment | Demand restrictions and Trainer 360 detail | Needs case workflow, resolution state, severity and privacy controls rather than raw rows |
| Trainer Skills | 217 | employee/course mapping and duplicate/discontinued flags | Skill register and verified write read-back | Duplicate/discontinued indicators are not a first-class cleanup workflow |
| Vendor Certification Count | 57 | teaching accreditations by body | Certification intelligence | This is accreditation, not exams passed; UI must keep the distinction consistently clear |
| Last 3 Months Utilisation | 39 | employee/month/utilisation | Trainer 360 trend | Only three months and no persistent snapshots; insufficient for seasonality and forecast |
| Trainer Resume | 87 | image, languages, certifications, summary, experience, skills, clients delivered for, feedback, interests | Profile, languages, certifications and matching | Interests, client experience, experience narrative and delivery domains are underused in matching and succession planning |
| Course Syllabus/TOC index | 248 | large course-to-syllabus link index | Syllabus lookup | Document availability/age/version is not governed |
| Course Catalogue | 70 | ID, name/code, vendor, duration, course page and TOC | Course search/intelligence | No canonical course entity screen or lifecycle view |
| Course Schedule | 246 | country, region, delivery mode and schedule dates | Course intelligence | Not connected to trainer development plans, demand forecast or certification preparation |
| In-house/Freelance Trainers by Course | 157 | external trainer network lookup | Endpoint exposed as unavailable | RMS rejects undocumented TrainerType values; cannot be treated as an empty market |
| Add Trainer Skill/IDP | 255 | trainer, course, level, approval and effective date | Direct and bulk skill marking with read-back | No bulk transaction endpoint, partial-result model, removal endpoint or approval/audit workflow |
| Assignment Pax | 209 | participant names/emails | Bounded Trainer 360 active/upcoming assignment detail | Personally identifiable data should be hidden by default and only exposed for operational need |

### Registered in backend but effectively dormant or not productized

| RMS capability | Key | Opportunity | Current problem |
|---|---:|---|---|
| Upcoming Assignments | 93 | efficient future-allocation/calendar queries by trainer/date | Configured but not called; Version 1 relies on key 16 instead |
| Trainer Availability | 90 | course/date candidate set with language, skill, assignment count and MTI issue | Configured but not called; its availability semantics and response quality need live verification |
| SCID Lookup | 173 | connect assignments to service cards and downstream operations | Configured but unused |
| Active SC Date | 13 | course, CSM, assignment, SC date, fee and currency | Configured but unused; enables revenue pipeline and CSM accountability if authorization is added |
| Recording Details | 278 | assignment recording link | Configured but unused; enables delivery QA/coaching but is highly access-sensitive |
| Check Course Availability in RMS | 104 | availability, duplicate, discontinued and status validation | Configured but not called before skill write/search selection |

### Not integrated into backend

| RMS capability | Key | Potential use | Required validation |
|---|---:|---|---|
| Trainer RC Schedule | 111 | date-bounded trainer schedule | Documentation is mislabeled and response is unspecified; live contract required |
| Course & Technology List | 114 | capability taxonomy and technology rollups | Resolve overlap with keys 70 and 205; establish canonical IDs |
| Course List | 164 | course/vendor catalogue and URLs | Likely redundant with key 70; compare freshness and completeness before adoption |
| Exam Course Linked | 215 | explicit exam-to-course graph | Response fields are not documented; live verification required |
| Course and Domain | 205 | domain hierarchy, practice-level capability | Can power Practice Head views and capability heatmaps |
| Course Content URL | 156 | direct learning/content entry | Response unspecified; permissions and link lifetime must be tested |
| Course Module | 206 | chapter/module decomposition | Can enable granular gap and adjacent-skill recommendations |
| Latest Version of Courses | 172 | course obsolescence and migration | Response unspecified; essential for stale-skill and upgrade-path alerts after validation |
| Unique Certification Count | 72 | certification summary | Backend notes show zero rows for every tested body shape; unavailable, not zero |
| Trainer Free Schedule and Details | 171 | course-based free-trainer search | Response unspecified and overlaps keys 90/111/16; must be validated rather than assumed |

## Fields and insights currently left on the table

1. `QubitsScore`, `techcallrating`, approval status and future-skill date can form evidence-based capability confidence, not just a skill count.
2. Course/module/domain/technology relationships can form a capability graph: domains → technologies → courses → modules → exams → certified trainers.
3. Assignment participants, feedback questions, negative-feedback cases and recordings can form a delivery-quality case view, but only under strict role and privacy controls.
4. SC created date, total fee and currency can form revenue opportunity, aging and pipeline metrics after assignment/SC reconciliation.
5. Resume languages, interests, prior clients and experience can improve matching beyond English yes/no.
6. Course status, duplicate/discontinued flags and latest-version APIs can identify stale skills and migration plans.
7. Assignment dates/times, multiple off-date types and schedule APIs can produce day/shift-level conflicts instead of broad availability labels.
8. Reportee type/designation/Trainer Plus can support workforce segmentation and delegated management.
9. Course schedules can connect demand, learning plans and readiness deadlines.
10. Repeated snapshots can create trends that RMS does not directly provide: demand aging, fill rate, time-to-allocate, readiness movement, certification coverage movement and capacity forecast accuracy.

Not currently possible from verified APIs: certification expiry dates, leave approval state, true real-time calendar events, allocation write-back, skill removal, durable action workflow in RMS, or record-level delta synchronization. These must not be fabricated from nearby fields.

## Module-by-module Version 2 review

### 1. Dashboard

**Current state:** A compact manager dashboard with executive KPIs, team health, charts, top performers, demand and actions. It is more useful than the earlier card wall but still combines operational summaries from datasets with different confidence and freshness.

**API capabilities available:** reportees, utilisation/history, assignments, unallocated demand, certifications/accreditations, feedback/incidents, SC fee/currency, course schedules and action derivation.

**Missing:** decision SLA, demand aging, fill rate, revenue-at-risk, forecast capacity, confidence/data health, manager span, readiness movement and trend persistence.

**Remove:** duplicate KPI summaries, decorative charts without a decision target, “top performer” rankings based on utilisation alone, and any readiness number derived from capacity rather than capability.

**Redesign:** a role-adaptive Today view with four questions: Protect Delivery, Place Capacity, Close Capability Gaps and Capture Opportunity. Every tile must show value, direction, confidence, target and direct action. Add a 14/30/90-day horizon switch.

**New features:** daily briefing, delivery-risk queue, available-capacity bands, revenue opportunity pipeline, demand SLA/aging, data-confidence panel, scenario alerts and a “what changed since yesterday” digest.

**UI/UX:** dense desktop-quality grid adapted to phone; one-line KPI strips, bullet charts and exception tables; charts open to evidence rather than separate generic pages.

**Business value:** managers see the next best decision in seconds, not merely team statistics.

### 2. Team

**Current state:** two-column cards with utilisation, readiness/capacity, certifications, gaps, assignment, availability, risk and actions.

**API capabilities available:** hierarchy, designation, direct/indirect, Trainer Plus, resume/languages/interests/clients, utilisation, assignments, off dates, skills, certifications, feedback and HR signals.

**Missing:** roster completeness/data confidence, workforce segmentation, bench duration, overload duration, upcoming capacity curve, succession coverage and comparison cohorts.

**Remove:** directory-like card chrome, repeated labels and any two-column phone design that makes decision data unreadably small. The requirement to fit two cards must not override legibility; use compact paired rows or a responsive matrix.

**Redesign:** Team Portfolio with saved views: Needs Action, Available Now, Overloaded, Capability Gaps, International Ready and Data Incomplete. Cards become compact decision rows; tapping opens a side-sheet/Trainer 360.

**New features:** bulk action assignment, bulk development plan, compare trainers, capability cluster filters, direct/indirect toggles and workforce export.

**UI/UX:** manager table semantics on tablets; compact paired cells on phones; sticky sort/filter bar and persistent selection mode.

**Business value:** enables portfolio management of 20–50 people instead of browsing individuals.

### 3. Trainer 360

**Current state:** the richest Android screen: identity, readiness/risk, utilisation, skills, certification gaps, delivery, availability, feedback and real Actions.

**API capabilities available:** virtually every trainer-level dataset, including resume, language, experience, clients, detailed feedback, participant roster, multiple off-date classes and assignment history.

**Missing:** temporal narrative, target plan, manager notes/history, quality trend, client/domain experience, course-version risk, evidence freshness and action outcomes.

**Remove:** equal-weight section stacking and raw sensitive feedback/participant data from the default view. Remove temporary raw feedback samples from production payloads once verified.

**Redesign:** an “Executive Trainer Brief” first screen: Health, Current Work, 90-day Capacity, Capability Confidence, Certification Exposure, Delivery Quality, Open Actions. Tabs below: Timeline, Capability, Delivery Quality, Career/IDP and Evidence.

**New features:** development plan, mentoring readiness, international readiness passport, stale-skill cleanup, course-version migration, assignment quality case, trainer comparison and evidence-based manager briefing.

**UI/UX:** chronological timeline and risk callouts; reveal raw evidence progressively; label unavailable versus zero consistently.

**Business value:** supports allocation, coaching, development and risk management from one authoritative brief.

### 4. Courses

**Current state:** course ownership/certification coverage plus search, intelligence and team skill assignment.

**API capabilities available:** catalogues, technology/domain, modules, syllabus/content, schedule, exam policy/link, current/latest version, duplicate/discontinued status, internal/freelance trainer search and skill write.

**Missing:** canonical course page, lifecycle/version status, domain graph, module-level capability, exam pathway, demand linkage, learning schedule and cleanup workflow.

**Remove:** trainer-first record-management flows and duplicate catalogue representations.

**Redesign:** Capability Marketplace. Start from Domain/Technology/Course. Each course page shows business demand, team coverage, certified coverage, readiness depth, upcoming schedules, version status, syllabus and “Assign to Team.”

**New features:** multi-select assignment, select all, level/effective date, partial-result summary, approval workflow, duplicate/discontinued cleanup, latest-version migration, exam path and suggested trainers based on adjacent modules.

**UI/UX:** searchable taxonomy with coverage heatmap; persistent team-selection drawer; clear difference between skill, approved skill, accreditation and certification.

**Business value:** transforms course data into capability supply planning.

### 5. Demand

**Current state:** prioritized FMAT → ILT → ILO → Unknown desk, international priority lane, weighted candidates and Aishwar rule.

**API capabilities available:** demand details, dates/mode/location/pax/vendor, course metadata/content, team/global trainer candidates, schedules, assignments/off dates, languages, readiness, feedback restrictions and potential SC commercial values.

**Missing:** demand ownership, SLA/age, lifecycle, allocation write-back, side-by-side candidate evidence, scenario planning, travel readiness, revenue-normalized value and resolution tracking.

**Remove:** percentage-first recommendation design, repeated decorative badges and any implication that unknown availability means available.

**Redesign:** Resource Allocation Workbench with lanes: Global Priority, At Risk, Ready to Allocate, Capability Build and Unverified. Selecting demand opens a comparison workspace showing qualified, near-match and excluded trainers with explicit reasons.

**New features:** shortlist, compare, reserve, request validation, create development action, escalate external search, conflict simulation and “fill by” SLA. Allocation itself remains a recommendation until a verified write API exists.

**UI/UX:** dense opportunity rows with priority/revenue/risk/date; global opportunities remain visually premium but business evidence outranks decoration.

**Business value:** improves fill rate, reduces allocation time and makes recommendation reasoning auditable.

### 6. Actions

**Current state:** derived plus manager-created items with open/in-progress/done states, notes and due dates; stored in a local backend JSON file.

**API capabilities available:** risks across certifications, utilisation, assignments, demand, feedback and HR incidents.

**Missing:** durable database, owner/assignee, SLA, escalation, history, comments, attachments, linkage to entity/evidence, deduplication, notification preference and offline-safe mutation queue for all action types.

**Remove:** duplicate static recommendations and action-like labels outside the Action domain.

**Redesign:** unified Work Queue grouped by Today, Overdue, Waiting and Watching. Every risk insight either becomes an action or remains an insight—never both without linkage.

**New features:** bulk assign, snooze, recurring follow-up, escalation, completion evidence, templates and outcome analytics.

**UI/UX:** inbox density with one-tap state changes and entity preview.

**Business value:** converts analytics into accountable operational execution.

### 7. Notifications

**Current state:** local notifications derived from snapshot differences and periodic worker checks; read state is local.

**API capabilities available:** assignment/demand changes, feedback incidents, utilization thresholds, actions, course/version and capability changes.

**Missing:** server event log, delivery guarantees, deduplication across devices, preference center, quiet hours, escalation, deep-link reliability and notification audit.

**Remove:** generic “new data” noise and notifications that duplicate open actions without a state relationship.

**Redesign:** server-owned Notification Centre with categories: Delivery, Capacity, Demand, Capability, Quality and System/Data. Local WorkManager becomes transport fallback, not event truth.

**New features:** daily digest, immediate critical alerts, subscriptions, acknowledgement and manager delegation.

**UI/UX:** grouped timeline with reason, impact and primary action.

**Business value:** high signal with lower alert fatigue and reliable accountability.

### 8. Search

**Current state:** course search exists inside Courses/Demand; there is no global search destination.

**API capabilities available:** trainers, courses, technologies, domains, modules, assignments, SCIDs, clients and demand.

**Missing:** entity-wide search, filters, recent searches, saved searches, synonyms/course aliases and action shortcuts.

**Remove:** separate ad hoc search fields that return incompatible models.

**Redesign:** universal command/search overlay: “Find trainer/course/demand/assignment/action” plus intent commands such as “available AI trainers next month.”

**New features:** faceted results, role-aware PII redaction, recent/favorite entities and natural-language query translated into verified filters.

**UI/UX:** one persistent search affordance; grouped results with quick actions.

**Business value:** dramatically reduces navigation time for large teams and demand volumes.

### 9. Reports

**Current state:** Trainer 360 has a PDF/report export; no manager report centre exists.

**API capabilities available:** team, utilization, assignments, demand, capability, certification, feedback and commercial SC data.

**Missing:** scheduled reports, period filters, definitions, data freshness, drill-through, export governance and portfolio reports.

**Remove:** screenshot-like exports and undocumented metrics.

**Redesign:** Report Centre with operational packs: Weekly Delivery, Capacity Forecast, Demand Coverage, Certification Exposure, Quality/Risk and Executive Portfolio.

**New features:** PDF/XLSX, scheduled delivery, saved filters, metric glossary, evidence appendix and role-based redaction.

**UI/UX:** report templates with preview and clear as-of timestamp.

**Business value:** supports governance, reviews and stakeholder communication without manual spreadsheet assembly.

### 10. Analytics

**Current state:** calculations run over current snapshots; a few three-month utilization points are charted.

**API capabilities available:** enough operational facts to create a historical analytical model if snapshots/events are persisted.

**Missing:** warehouse, semantic metric definitions, cohorting, targets, baselines, attribution and long-term history.

**Remove:** trend arrows without retained history and composite scores whose inputs are not visible.

**Redesign:** governed analytics layer with facts for trainer-day capacity, assignment, demand state transition, certification coverage, feedback case and action lifecycle.

**New features:** fill rate, time-to-allocate, forecast accuracy, utilization distribution, readiness movement, risk recurrence and capability investment ROI.

**UI/UX:** Power BI-style drill paths but mobile summaries first; metric definitions available inline.

**Business value:** moves management from snapshot reaction to continuous improvement.

### 11. Resource Planning

**Current state:** availability evidence and candidate ranking exist, but no planning canvas.

**API capabilities available:** assignment windows/times, upcoming schedules, off-date classes, course/language/skill signals, utilization and demand.

**Missing:** timeline capacity, tentative holds, scenario versions, conflict resolution, shift/time-zone logic and allocation write-back.

**Remove:** utilization-as-availability remnants and single-date availability labels without source detail.

**Redesign:** 90-day capacity board by trainer and week, overlaid with demand; drag-to-simulate, not drag-to-commit.

**New features:** best-fit scenario, load balancing, international/travel readiness, backup coverage and capacity shortfall forecast.

**UI/UX:** horizontal timeline on tablets/web and week cards on phones; visible confidence for every free window.

**Business value:** reduces clashes and exposes future shortages before delivery is at risk.

### 12. Certification Intelligence

**Current state:** held certifications, teaching accreditations, exam-required course gaps and coverage are shown.

**API capabilities available:** resume certifications, accreditation bodies, exam-required course policy, exam-course links, course version/status and skills.

**Missing:** verified exam-course graph, version migration, certification evidence provenance, expiry dates and target dates.

**Remove:** any “expiring certification” claim until a verified expiry-date source exists; do not conflate accreditation with certification.

**Redesign:** Certification Exposure Matrix: required by active/future demand, held, accredited, missing, unverified and obsolete-course-linked.

**New features:** gap priority by demand/revenue, learning schedule suggestions, version migration and certification action plans.

**UI/UX:** vendor/domain heatmap and trainer drill-through; every gap shows why it matters.

**Business value:** directs certification investment toward measurable demand coverage.

### 13. Delivery Intelligence

**Current state:** current/upcoming/history, participant data, negative feedback and basic risk appear across Dashboard and Trainer 360.

**API capabilities available:** assignment dates/times/mode/location/pax, SC/CSM, detailed feedback, negative cases and recordings.

**Missing:** delivery-level cockpit, quality trajectory, preflight checklist, feedback denominator, recording review workflow and post-delivery closure.

**Remove:** trainer-level feedback counts without assignment/client context as the primary quality measure.

**Redesign:** Delivery Operations board with Preflight, Live, Closing and Escalated lanes. Each delivery has readiness, trainer, participant count, location/time-zone, content, SC/CSM and quality evidence.

**New features:** preflight exceptions, recording review assignment, feedback case management, follow-up action and client/course recurrence analysis.

**UI/UX:** calendar plus operational queue; sensitive details behind role checks.

**Business value:** protects delivery quality and shortens escalation response.

### 14. Capacity Planning

**Current state:** current utilization, three-month trend, capacity buckets and demand counts.

**API capabilities available:** utilization series, assignments, demand dates/duration/mode, off dates and course schedules.

**Missing:** trainer-day capacity model, weighted delivery effort, preparation/travel time, seasonality, target bands and demand scenarios.

**Remove:** headcount-as-capacity and a single utilization percentage as a forecast.

**Redesign:** Supply vs Demand forecast by week, domain and delivery mode for 30/60/90 days.

**New features:** projected utilization, shortage/surplus, hiring/freelance escalation, cross-skill scenario and capacity confidence bands.

**UI/UX:** stacked supply/demand chart, shortage heatmap and scenario controls.

**Business value:** informs staffing, upskilling and external-capacity decisions before gaps become emergencies.

## Recommended Version 2 information architecture

The five fixed tabs should become four outcome spaces plus universal search:

1. **Today** — executive brief, changes, risks and actions.
2. **People & Capability** — Team Portfolio, Trainer 360, skills and certification intelligence.
3. **Demand & Planning** — allocation workbench, capacity forecast and scenario planning.
4. **Delivery Operations** — calendar, preflight, live delivery, quality and closure.
5. **Universal Search/Command** — trainers, courses, demand, assignments, actions and reports.

Reports and Analytics should be contextual destinations and an executive Report Centre, not permanent phone tabs. Notifications should feed Today and Actions. Courses should be a capability domain inside People & Capability, not an isolated catalogue tab.

## Canonical Version 2 data model

Do not let screens compose raw RMS records. Build stable entities:

- Person / ReportingRelationship
- Capability / SkillEvidence / Approval
- Course / Technology / Domain / Module / CourseVersion
- Certification / Accreditation / ExamRequirement
- Assignment / Delivery / ScheduleWindow / ParticipantSummary
- Demand / Opportunity / AllocationCandidate / RecommendationEvidence
- AvailabilityWindow / Conflict / OffDate
- FeedbackCase / HRIncident / QualityEvidence
- ServiceCard / CommercialValue
- Action / Notification / AuditEvent

Every entity needs source, source timestamp, confidence, availability status, and access classification. Missing data must remain `unknown`, never silently become zero or false.

## Automation opportunities

1. Generate an action when a future demand has no certified coverage by its readiness deadline.
2. Suggest course-version migration when a trainer owns a duplicate/discontinued/old-version course.
3. Detect capacity conflicts and propose ranked alternatives before allocation.
4. Build a daily “what changed” manager briefing from snapshot transitions.
5. Escalate unresolved feedback cases based on severity and SLA.
6. Suggest cross-skilling from adjacent modules/domains and upcoming demand.
7. Flag international FMAT/ILT opportunities lacking verified language/travel/availability evidence.
8. Recommend underutilized qualified trainers while preventing over-allocation.
9. Detect stale/unverified skill evidence and request manager confirmation.
10. Build a revenue-at-risk queue from SC value, demand status and delivery risk—only after commercial authorization and mapping validation.

## P0 foundations before Version 2 feature expansion

1. Rotate every RMS credential found in repository history and documentation; remove source-code fallback secrets and store credentials only in a managed secret store.
2. Replace email-only login with enterprise identity (Microsoft Entra ID/OIDC) and validate signed tokens server-side.
3. Require authorization on every read/write route; enforce manager scope, Trainer Plus permissions and field-level access.
4. Replace process-memory sessions and JSON action storage with a durable database, audit log and idempotency keys.
5. Classify and minimize PII: participant emails, feedback text, HR incidents, recordings and commercial values.
6. Add a backend-for-frontend semantic layer with typed/versioned contracts; Android must not depend on loose `Map<String, Any>` payloads.
7. Validate all dormant/undocumented RMS response shapes with read-only contract tests before UI commitments.
8. Add API observability: per-source latency/error/freshness, circuit breakers, rate controls and data-health reporting.
9. Add historical/event persistence for analytics and reliable server-owned notifications.
10. Add ETag/change-token endpoints for genuine incremental synchronization.

## Proposed delivery sequence

### Foundation release

Identity, authorization, secret rotation, typed contracts, durable database/actions, audit logs, PII controls and data-source health. This is a release blocker for broader API exposure.

### Manager Operations release

Today brief, Team Portfolio, unified Actions, universal search and Delivery Operations using existing verified sources.

### Planning release

Demand Allocation Workbench, 90-day resource/capacity planner, scenario comparison and confidence-aware availability.

### Capability release

Canonical course/domain/module/exam graph, Capability Marketplace, skill lifecycle, course-version cleanup and certification exposure.

### Analytics release

Historical facts, governed metrics, reports, forecasts, revenue-at-risk and Practice Head portfolio views.

## Final product principle

Version 2 should not expose more APIs merely because they exist. It should expose fewer, clearer decisions backed by more evidence. The manager should see what changed, why it matters, what can be done, what evidence supports it and how confident the system is. Raw API breadth belongs in the semantic platform; the mobile product should remain a focused command centre.
