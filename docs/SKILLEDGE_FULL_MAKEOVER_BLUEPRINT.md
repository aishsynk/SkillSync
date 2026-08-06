# SkillEdge Full Makeover Blueprint

## 1. Product definition

SkillEdge is the Delivery Manager's operating system for people, delivery, growth, and incoming demand.

It must answer five questions reliably:

1. What is each reportee doing now?
2. Which current and upcoming batches are they engaged in?
3. How are they performing, and where is intervention needed?
4. Based on demonstrated skills and evidence, what can they upgrade to next?
5. When Sales raises a new requirement, who is the best trainer, backup trainer, and development candidate?

The product is not a collection of dashboards. It is a decision system. Every conclusion must show its evidence, confidence, missing data, and recommended manager action.

## 2. Primary user journeys

### A. Start my day

The manager opens one Home view and immediately sees:

- reportees teaching now, preparing, free, on leave, blocked, or unknown;
- batches in progress today and starting soon;
- clashes, overload, unassigned demand, quality risks, and missing data;
- actions due today;
- changes since the previous refresh.

The Home view should answer "Where do I need to intervene?", not merely display totals.

### B. Understand a reportee

The manager opens a trainer profile and sees one chronological, evidence-backed record:

- current batch and schedule;
- upcoming and previous batches;
- utilization and availability;
- courses delivered and mapped;
- demonstrated skills, resume skills, approvals, Qubit/technical evidence;
- certifications and gaps;
- feedback history and themes;
- HR or delivery-risk signals with access controls;
- current readiness, possible upgrades, and development plan;
- manager actions, notes, and decisions.

### C. Plan capability growth

The system recommends realistic next capabilities using:

- current skills and adjacent technologies;
- courses already delivered;
- course mappings and approvals;
- certifications held and certification gaps;
- assessment, Qubit, mock, and technical-call evidence;
- feedback quality and recent utilization;
- market/Sales demand and coverage gaps in the manager's team.

Recommendations must distinguish:

- ready now;
- ready after verification;
- ready after short preparation;
- long-term development path;
- not recommended, with reasons.

### D. Fulfil a Sales request

The manager uploads or pastes a TOC, course outline, RFP, or customer requirement. The system:

1. extracts title, technologies, topics, labs, level, prerequisites, delivery format, duration, and certification alignment;
2. maps extracted concepts to a controlled skill/course/technology graph;
3. retrieves candidates from the manager's permitted trainer pool;
4. evaluates capability, demonstrated experience, quality, availability, certification, customer/context fit, and risk;
5. returns a ranked shortlist with best trainer, backup, mentor, and development candidate;
6. explains strengths, gaps, blockers, preparation needed, confidence, and evidence freshness;
7. allows the manager to approve, reject, compare, or create a preparation action;
8. records the outcome so future rankings can learn from actual delivery success.

## 3. Target product structure

Use four primary workspaces plus administration. Do not create a page for every dataset.

### 1. Command Center

Purpose: daily operating picture.

Modules:

- Live team status
- Batches now and next
- Exceptions and risks
- Manager action queue
- Team capacity for the next 7/14/30 days
- Recent changes

### 2. Team & Trainer 360

Purpose: understand people and intervene.

Modules:

- searchable team table
- trainer profile
- delivery timeline
- skills and evidence
- feedback and risk
- capability pathway
- manager action history

### 3. Demand & Allocation

Purpose: match standard or custom demand to people.

Modes:

- Standard course search
- TOC/requirement upload
- Candidate comparison
- Allocation decision and approval
- Coverage and backup analysis

### 4. Capability Planning

Purpose: build future capacity based on real demand.

Modules:

- upgrade recommendations
- team skill coverage
- upcoming demand gaps
- certification and accreditation plan
- mentor/backup development
- progress tracking

### Administration

Data health, source freshness, access, configuration, model/version information, and audit history. This is not primary manager navigation.

## 4. Canonical intelligence model

The backend must produce one authoritative `TrainerIntelligenceRecord` for every reportee.

### Identity and scope

- trainer ID, employee ID, name, email, designation
- direct/indirect reportee relationship
- manager scope and access policy

### Current engagement

- normalized status: `teaching_now`, `scheduled_today`, `preparing`, `free`, `leave`, `blocked`, `unknown`
- current batch ID, course, customer, start/end, timezone, delivery mode
- source, last refreshed time, confidence, and conflicts

"Teaching now" must require dated schedule/batch evidence. Utilization alone must never imply a live batch.

### Delivery history

- previous, current, and upcoming batches
- course/customer/vendor history
- delivery frequency, recency, and success evidence

### Capability

- normalized skills with provenance
- demonstrated versus declared skills
- mapped courses and approvals
- certifications, assessments, Qubit/mock/technical-call evidence
- technology adjacency and skill depth

### Performance and risk

- feedback records and themes
- negative-feedback trend, not only total count
- HR/delivery incidents with permission-aware visibility
- readiness blockers and remediation

### Availability and capacity

- calendar availability
- upcoming load and clashes
- utilization trend
- 7/14/30-day capacity
- freshness and confidence

### Growth

- recommended target capability/course
- fit, gap, preparation effort, evidence, confidence
- reason the upgrade matters to current demand/team coverage
- manager-approved development plan and progress

## 5. TOC intelligence engine

### Input processing

Support PDF, DOCX, PPTX, Markdown, text, and URL where permitted. Store the original securely and create a versioned extraction result.

Extract:

- technologies and products;
- topics and subtopics;
- learning objectives;
- practical labs and required depth;
- audience and prerequisites;
- difficulty and expected duration;
- vendor/course/certification candidates;
- domain, industry, delivery mode, and language requirements.

Every extracted item should retain its source passage/page so the manager can verify it.

### Candidate retrieval

Retrieve broadly using exact course mappings, normalized skills, semantic similarity, adjacent technologies, historical deliveries, certifications, and approvals. Retrieval should maximize recall; ranking applies business constraints afterward.

### Ranking dimensions

The score must be decomposable. Initial policy:

| Dimension | Purpose | Initial weight |
|---|---|---:|
| Demonstrated topic/skill fit | Evidence the trainer can teach the TOC | 30% |
| Similar delivery history | Recency and success on related courses | 20% |
| Availability/capacity | Ability to take the requested dates | 15% |
| Quality/feedback | Delivery quality and relevant feedback | 12% |
| Approval/certification | Required accreditation or verified knowledge | 10% |
| Technical evidence | Qubit, mock, assessment, technical-call evidence | 8% |
| Customer/context fit | Language, industry, mode, customer history | 5% |

Weights must be configurable and renormalized only when a signal is optional. A mandatory missing requirement becomes a blocker, not a reduced weight.

### Output buckets

- Best fit: safe primary choice
- Strong alternative: credible backup
- Ready after verification: needs mock/SME validation
- Development candidate: valuable stretch option with a plan
- Not suitable: material blocker or insufficient evidence

Each candidate result must include:

- overall score and per-dimension scores;
- mandatory blockers;
- matched topics and missing topics;
- evidence links and dates;
- availability for requested dates;
- preparation estimate with assumptions;
- confidence and missing signals;
- recommended manager action;
- model/rules version.

The AI extracts and explains. Deterministic policy enforces access, mandatory constraints, blocking rules, and the final auditable score.

## 6. Feedback intelligence

Do not reduce feedback to a negative count. Build:

- individual feedback evidence;
- theme classification: technical depth, explanation, pace, labs, engagement, communication, setup, professionalism;
- sentiment and severity;
- trend by course/domain and time;
- recurring versus isolated issues;
- positive strengths;
- coaching action and verification state.

AI-generated summaries must cite underlying records and must never invent missing feedback.

## 7. Upgrade recommendation policy

An upgrade recommendation must answer:

- Upgrade to what?
- Why this trainer?
- Why now?
- What evidence supports it?
- What exact gaps remain?
- How long might preparation take, and on what assumptions?
- What verification is required?
- Which future demand or team coverage gap will it address?

Upgrade paths should be generated from a technology graph plus real organizational demand. Adjacency alone is insufficient; the recommendation must consider performance, learning evidence, availability, interest, and business value.

## 8. Decision and audit requirements

- Python/backend owns all business decisions.
- Frontend renders authoritative objects and never silently recalculates them.
- Every decision includes source IDs, timestamps, confidence, missing evidence, and policy version.
- Hard blockers always override scores.
- Unknown is distinct from zero, free, safe, or not applicable.
- Manager overrides require a reason and are audited.
- Sensitive HR information is permission-controlled and minimized.
- Refresh status and data staleness are visible.
- Historical snapshots preserve what the manager knew when a decision was made.

## 9. Immediate data gaps to resolve

The current APIs cover much of the required foundation, but the following must be verified or added:

1. A canonical batch feed containing batch ID, trainer, course, customer, dates/times, timezone, status, and delivery mode.
2. Reliable upcoming schedule and leave/block information.
3. Requested delivery dates and constraints from Sales.
4. Stable course-to-topic/technology metadata.
5. Positive and detailed feedback records, not only negative totals.
6. Outcome labels: allocation accepted, trainer prepared, batch delivered, feedback result, cancellation/failure reason.
7. Manager action ownership, due dates, completion, and audit history.

Without outcome labels the system can rank heuristically, but it cannot honestly claim predictive learning.

## 10. Migration plan

### Phase 0: Safety and truth

- Rotate and remove credentials embedded in source.
- Fix hard-blocker/assignability contradictions.
- Create contract tests for every blocking rule.
- Define canonical IDs, timestamps, and unknown-state semantics.
- Freeze new page development.

Exit criteria: contradictory decisions are impossible and all security secrets are externalized.

### Phase 1: Live manager cockpit

- Build canonical trainer and batch records.
- Reconcile assignments, availability, schedules, and utilization.
- Replace the current Home and Team views with live status, batches, exceptions, and actions.
- Add freshness/conflict indicators.

Exit criteria: a manager can correctly answer what every reportee is doing now and next.

### Phase 2: Trainer 360 and feedback

- Consolidate trainer pages into one profile.
- Add batch timeline, skills provenance, feedback themes, risks, and actions.
- Move all remaining frontend classifications into backend services.

Exit criteria: every visible conclusion traces to evidence.

### Phase 3: Capability planning

- Introduce normalized skill/technology/course graph.
- Generate evidence-based upgrade pathways.
- Connect recommendations to demand and team coverage.
- Track manager-approved development plans.

Exit criteria: every upgrade recommendation has a target, gap, plan, value, and verification step.

### Phase 4: Sales request / TOC matching

- Add secure file ingestion and structured extraction.
- Implement candidate retrieval and decomposed ranking.
- Add date/constraint-aware shortlist comparison.
- Record allocation decisions and outcomes.

Exit criteria: the manager can upload a real TOC and receive an auditable primary, backup, mentor, and development candidate.

### Phase 5: Learning and optimization

- Use delivery outcomes to calibrate confidence and weights.
- Measure recommendation acceptance and success.
- Add controlled semantic retrieval and predictive models only where data supports them.

Exit criteria: model improvements are measurable against a versioned evaluation set.

## 11. Success measures

- Time to understand today's team status
- Percentage of reportees with trustworthy current engagement
- Time from Sales request to approved shortlist
- Shortlist acceptance rate
- Allocation success and post-delivery feedback
- Percentage of recommendations with complete evidence
- Backup coverage for high-demand courses
- Upgrade-plan completion and successful first delivery
- Data freshness and source failure rates
- Manager override rate and override reasons

## 12. First implementation slice

The first vertical slice is **Live Team Status and Current Batches**.

Deliverables:

1. canonical `trainer_current_state` and `batch_engagement` backend objects;
2. deterministic reconciliation rules across assignments, previous/upcoming assignments, trainer availability, RC schedule, free schedule, and utilization;
3. conflict, freshness, and confidence fields;
4. Command Center live-status module;
5. Team table with current batch, next batch, load, quality warning, and action;
6. fixture-based contract tests plus live-source verification;
7. no frontend status inference.

This slice establishes the truth required by every later capability and TOC-matching decision.
