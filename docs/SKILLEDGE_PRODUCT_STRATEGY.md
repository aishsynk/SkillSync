# SkillEdge Product Strategy

This document defines what SkillEdge should become as a commercial enterprise product over the next three years.

It is not an implementation plan, not an architecture note, and not a feature backlog. It is the product strategy lens for deciding what belongs in SkillEdge and what does not.

## 1) Product Positioning

SkillEdge belongs in the category of **delivery intelligence software for enterprise training managers**.

It is closest, conceptually, to the problem spaces these products address:

- **Microsoft Viva**: helping managers understand people, work signals, and team readiness
- **Microsoft Copilot**: turning internal enterprise data into decisions and next actions
- **ServiceNow**: operationalizing work, routing decisions, and manager action flows
- **Salesforce**: centralizing decision signals around a business domain, but not as a CRM
- **Workday**: talent and workforce intelligence, but narrower and more delivery-focused
- **LinkedIn Learning**: skill growth and readiness, but internal and manager-scoped
- **Degreed**: capability mapping and skill progression
- **Cornerstone**: learning and talent capability planning
- **Docebo**: learning operations and content delivery

### What problem SkillEdge solves

SkillEdge solves a specific enterprise question:

**Who in my training organization can deliver what, who needs prep, who should not be risked, and what should I do next?**

That is not a generic LMS problem, not a CRM problem, and not a BI problem.

### What category SkillEdge belongs to

SkillEdge should be positioned as:

- a **trainer capability intelligence platform**
- a **delivery decision cockpit**
- a **manager-grade workforce readiness system for training organizations**

### What category SkillEdge should not belong to

SkillEdge should not become:

- a general LMS
- a CRM
- an HRIS
- a finance system
- a generic BI dashboard
- a content authoring tool
- a student/learner management system

### What makes SkillEdge unique

SkillEdge is unique because it is not about content. It is about **delivery readiness and delivery decisions**.

It combines:

- trainer identity and scope
- skills and mapped courses
- readiness and Qubit signals
- certifications and certification source logic
- utilization and availability
- assignment history
- quality and HR risk
- custom course matching
- manager actions

The product's real value is that it can tell a manager what to do with a trainer or course, not just show a score.

## 2) The One Sentence Test

### 10 words

SkillEdge helps managers decide which trainers can deliver what.

### 25 words

SkillEdge turns trainer, course, certification, readiness, and availability signals into manager decisions about allocation, coaching, risk, and future capability.

### 50 words

SkillEdge is a manager-facing delivery intelligence product that helps enterprise training teams decide who can deliver a course, who needs prep, who should be held back, and which capabilities should be grown next, using trainer skills, readiness, certifications, utilization, assignments, quality signals, and custom course matching.

### 100 words

SkillEdge is a delivery intelligence platform for enterprise training managers. It combines trainer scope, skills, Qubit strength, certifications, utilization, assignments, feedback, HR signals, and course matching into clear manager decisions. The product answers who can deliver now, who can deliver with prep, who is a stretch candidate, who should not be risked, and what action should be taken next. It is not a reporting portal or dashboard. It is a decision system for trainer capability, allocation, readiness, and future growth.

## 3) Product Differentiators

### Existing differentiators

1. Manager-scoped trainer intelligence instead of org-wide generic reporting.
2. Combined readiness, certification, utilization, assignment, feedback, and HR signals.
3. Explainable decision outputs instead of opaque scores.
4. Custom course matching for outline-to-trainer decisioning.
5. Delivery availability framing, not just calendar presence.
6. Manager actions generated from live signals.
7. Evidence and data-health surfacing instead of hiding missing data.
8. Trainer 360 view that connects capability and delivery signals.
9. Course allocation view that tries to answer who is best for a course.
10. Risk-taker and growth-candidate classification.

### Near-term differentiators

1. Server-side custom course matching that becomes a reliable product surface.
2. Stronger certification source logic and course-exam awareness.
3. Better availability confidence and safer allocation labels.
4. Clear distinction between ready now, needs prep, risky, and hold.
5. Better chronology of deliveries, feedback, and readiness changes.
6. More consistent action recommendations across the product.
7. Sharper trust and completeness scoring for data quality.
8. More explicit trainer upgrade and future capability planning.

### Long-term differentiators

1. Knowledge-graph style relationships between trainer, course, certification, and delivery history.
2. Similarity-based trainer-to-course recommendations.
3. Prediction of readiness and capacity trends.
4. Outcome-driven recommendation learning.
5. Copilot-style manager interaction over trainer data.
6. Market-aware skill planning from external demand signals.
7. Future certification planning tied to business need and opportunity.

## 4) What Should Never Be Built

These features would dilute the product and should stay out unless the business changes materially.

### Generic CRM features

Why not:
- SkillEdge is not a pipeline management tool.
- It is not meant to track leads, customers, stages, or deals.

### Student management

Why not:
- The product is for managers deciding on trainers, not for learner administration.

### HRIS functions

Why not:
- SkillEdge should consume HR-adjacent signals, not become an HR system of record.

### Finance modules

Why not:
- Budgeting, invoicing, payroll, and revenue accounting do not serve the core decision loop.

### LMS content delivery

Why not:
- SkillEdge should recommend delivery capability, not host or deliver the learning content itself.

### Reporting pages with no decisions

Why not:
- Any page that only repeats numbers without enabling a manager decision is noise.

### Duplicate trainer analytics

Why not:
- Repeating the same trainer score in multiple styles adds confusion, not value.

The product must stay opinionated around decisions.

## 5) The Product Flywheel

SkillEdge should get smarter through a closed loop:

Deliveries
→ Assignment outcomes
→ Feedback and HR signals
→ Readiness and availability updates
→ Better trainer recommendations
→ Better allocation decisions
→ Better training success
→ More trustworthy evidence
→ Better manager actions
→ More future-ready capability

The flywheel works only if the product captures outcomes after decisions are made.

Without outcomes, SkillEdge remains a smart snapshot.
With outcomes, it becomes a learning system.

## 6) The AI Roadmap

SkillEdge should not jump straight to LLMs. It should earn intelligence in layers.

### Phase 1: Rule-based intelligence

Prerequisites:
- stable unified backend
- scoped trainer data
- trusted normalization
- deterministic scoring rules

Expected value:
- explainable readiness, allocation, availability, risk, and action decisions

Risks:
- logic can become brittle if data quality is weak
- rules can overfit the current APIs

Measurable outcomes:
- better allocation confidence
- lower manual review time
- fewer ambiguous trainer decisions

### Phase 2: Knowledge Graph

Prerequisites:
- reliable entities and joins
- stable trainer, course, certification, assignment, and feedback relationships

Expected value:
- richer relationship reasoning
- better course and trainer adjacency logic
- better explainability for recommendations

Risks:
- complexity grows quickly
- graph quality depends on clean normalization

Measurable outcomes:
- better course matching
- better upgrade path suggestions
- stronger cross-signal reasoning

### Phase 3: Similarity Models

Prerequisites:
- enough historical data
- clean labels for recommendations and outcomes

Expected value:
- trainer similarity
- course similarity
- more nuanced matching than rules alone

Risks:
- can become hard to explain if not bounded
- may amplify noisy historical behavior

Measurable outcomes:
- improved match quality
- fewer false positives in risk-taker selection
- better prep recommendations

### Phase 4: Prediction Models

Prerequisites:
- outcome capture
- historical snapshots
- stable definitions of readiness and success

Expected value:
- readiness prediction
- utilization forecasting
- delivery risk prediction

Risks:
- prediction quality will suffer without clean outcome data
- model drift could mislead managers

Measurable outcomes:
- earlier warning on risk
- better staffing decisions
- improved success rates for new-course allocation

### Phase 5: Copilot

Prerequisites:
- strong core intelligence
- robust trust layer
- clear policy and guardrails
- stable retrieval over the right datasets

Expected value:
- natural-language decision support
- manager-specific guidance
- reduced navigation effort

Risks:
- bad answers become highly visible
- needs strong provenance and scope control

Measurable outcomes:
- fewer clicks to decisions
- faster manager response time
- higher decision confidence

## 7) 12-Month Product Roadmap

This is a product roadmap, not a build plan.

### Quarter 1

Focus:
- trust
- consistency
- decision clarity

Milestones:
- stable manager-scoped intelligence
- consistent readiness and availability language
- trustworthy data-health surface
- one clear decision vocabulary across pages

### Quarter 2

Focus:
- allocation quality
- certification intelligence
- custom course usefulness

Milestones:
- stronger course matching
- better certification source logic
- more useful trainer upgrade recommendations
- clearer action center behavior

### Quarter 3

Focus:
- learning loop
- chronology
- outcome capture

Milestones:
- richer timeline of trainer changes and decisions
- clearer relationship between actions and results
- better manager feedback loop

### Quarter 4

Focus:
- intelligence depth
- scalability
- automation readiness

Milestones:
- knowledge-graph foundations
- similarity-based matching readiness
- first prediction-layer opportunities
- copilot readiness assessment

## 8) Product Strategy Bottom Line

SkillEdge should become the system a training manager trusts when deciding:

- who can deliver now
- who needs prep
- who should be stretched
- who should not be risked
- what should happen next

If a feature does not improve one of those decisions, it should not be central to the product.

SkillEdge wins by being narrower, more opinionated, more explainable, and more manager-useful than generic enterprise tools.

