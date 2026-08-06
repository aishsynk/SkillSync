# Trainer Command Center Architecture

## Purpose

The Trainer Command Center is the canonical trainer experience in SkillEdge.
It is not a page summary and it is not a stitched collection of page fragments.
It is the single trainer operating system.

The page must answer one question completely:

> Show me everything about this trainer.

The other trainer-related pages remain useful, but they are no longer the primary mental model.
They become alternate entry points into the same manager-wide data model.

## Architectural Rule

### Old model

- `team.html` owns roster
- `allocation-desk.html` owns allocation
- `capability-builder.html` owns growth
- `certifications.html` owns accreditation
- `trainer-intelligence.html` owns trainer detail

### New model

- `trainer-intelligence.html` owns the trainer operating system
- `team.html`, `allocation-desk.html`, `capability-builder.html`, and `certifications.html` are global aggregate views
- all trainer-specific detail lives in the Trainer Command Center

## Product Definition

The Trainer Command Center should behave like a manager opening one trainer profile and asking:

- Who is this trainer?
- Where do they sit on the team?
- What can they do?
- Are they certified?
- Can I allocate them now?
- What have they delivered?
- What blocks them?
- How do I improve them?
- What should I do next?
- What can the knowledgebase tell me?

## Functional Domains

The page should be organized by domains, not by source page ownership.

### Identity

Answers:

- who the trainer is
- manager name
- direct vs indirect
- team position
- readiness rank
- utilization rank

### Team

Answers:

- where the trainer sits in the team
- peer comparison
- succession impact
- backup coverage

### Capability

Answers:

- strongest vendors
- vendor ranking
- readiness percentile
- capability bucket
- growth path

### Certifications

Answers:

- held certifications
- missing certifications
- OEM accreditations
- certification ranking
- certification blockers

### Allocation

Answers:

- can I assign now
- best course fit
- allocation ranking
- allocation readiness
- allocation blocker
- confidence

### Delivery

Answers:

- delivered history
- delivery readiness
- batch/delivery signals
- utilization trend
- availability

### Risks

Answers:

- hardest blocker
- coaching risk
- allocation risk
- delivery risk
- data quality risk

### Growth

Answers:

- future skill roadmap
- next certification
- what to coach
- what to unlock next
- 30/60/90 day path

### Actions

Answers:

- what should the manager do
- grouped open actions
- priority
- source
- linked course/vendor/cert

### Knowledgebase

Answers:

- free-form manager questions
- deterministic retrieval from decision objects
- evidence-first answers
- source datasets
- decision version

## UI Rules

### The page must feel compact, not sparse

Compact does not mean fewer capabilities.
Compact means:

- short rows
- short cards
- no oversized KPI blocks
- no email overflow
- no horizontal tab strip
- no unnecessary page-level navigation between trainer-specific sections

### The page must keep trainer names visible

If a manager is focused on one trainer, the roster must still be visible on the left.
The trainer list is part of the working surface, not a hidden drawer only.

### The page must expose depth through drill-down

Do not hide detail by removing it.
Hide detail by moving it behind a better interaction.

Approved drill-down patterns:

- modal for dense read-only details
- right-side slide-over for fast context
- inline mini-cards for top signals
- “View all” opens the full evidence table or modal

### Use SeanTheme patterns, not custom widget inventions

Prefer SeanTheme component language such as:

- `widget` KPI cards
- `panel panel-inverse`
- `widget-list`
- `table table-panel`
- `ui_modal_notification`
- `email_inbox`
- `extra_order_details`
- `extra_timeline`
- right-sidebar and slide-over patterns when available

## Knowledgebase Architecture

The Trainer Knowledgebase Assistant must be deterministic.
It must not fake AI behavior.

It should behave like a retrieval layer:

1. Detect the question intent
2. Look up decision objects
3. Normalize evidence
4. Build a grounded response
5. Show source datasets and decision version

### Required source hierarchy

1. `trainer_decision_objects`
2. `allocation_decision_objects`
3. `manager_action_objects`
4. `custom_course_match_objects`
5. legacy fallback dataframes only when the decision objects are missing or incomplete

### Answer qualities

Each answer should include:

- concise manager answer
- supporting evidence
- confidence
- source datasets
- decision version

### Question coverage

The assistant should answer more than a fixed set of canned prompts.
It should support trainer-specific questions like:

- Can Niharika deliver AI-102 next month?
- Why is she not first choice?
- Compare her with Abhinav.
- Which certification blocks her from Microsoft AI?
- What if I move her to Azure?
- Which OEM should I invest in?

## Global Pages vs Trainer Command Center

### Global pages

These should answer manager-wide aggregate questions:

- `team.html`: show everybody
- `allocation-desk.html`: global allocation board
- `capability-builder.html`: global workforce planning
- `certifications.html`: global certification management

### Trainer Command Center

This should answer trainer-specific questions:

- everything about one trainer
- all source domains projected onto that trainer
- all relevant actions and evidence

## Drill-Down Model

The experience should feel modern and layered:

- overview cards show the top signals
- view-all actions open the full list
- detail modals show the complete data table
- charts are preserved in the modal or a focused panel
- nothing is deleted, only compressed or relocated

## Implementation Guidance

### Keep the left roster visible

The left rail should stay present so the manager can jump between trainers without leaving the command center.

### Keep the center focused on one trainer

The center area should merge the major trainer domains:

- identity
- team
- capability
- certifications
- allocation
- delivery
- risks
- growth

### Keep the right rail operational

The right rail should hold:

- knowledgebase
- actions
- blockers
- evidence
- source confidence

## Success Criteria

The page is successful when a manager can answer all of these without leaving the Trainer Command Center:

- who is this trainer
- where do they rank
- what can they do
- what are they missing
- can I assign them now
- what should I do next
- what is the evidence
- what is the data confidence

## Non-Goals

This page should not:

- behave like a generic dashboard
- re-own trainer data by page
- hide detail behind “summary only” views
- rely on page-centric language
- fake AI-generated answers

## Final Statement

The Trainer Command Center is the canonical trainer experience.
Every trainer-specific capability should land there first.
The other pages exist as aggregate entry points, not as separate owners of the trainer story.

---

# Trainer Command Center Architecture v2.0

## Vision

The Trainer Command Center is the canonical trainer operating system for SkillEdge.

It is not:

- a dashboard
- a report
- a collection of widgets
- a stitched combination of existing pages

It is:

- the complete operational workspace for managing one trainer
- the only page a manager needs to understand, evaluate, compare, develop, allocate, and act on a trainer

The page should answer:

> Everything the organization knows about this trainer, in one place.

## Product Philosophy

The application should be divided into two concepts.

### Aggregate Pages

Manager-wide views.

These answer:

> Show me everyone.

Examples:

- Team
- Allocation Desk
- Capability Builder
- Certifications
- Actions
- Data Health

These pages work across the whole organization.

### Trainer Command Center

Trainer-specific view.

Answers:

> Show me everything about THIS trainer.

No trainer-specific workflow should require leaving this page.

## Information Architecture

Instead of pages, organize by business domains.

### Domain 1 - Identity & Organization

Questions answered:

- Who is this trainer?
- Where do they belong?
- Who manages them?
- Direct or indirect?
- Practice?
- Region?
- Reporting chain?
- Current status?
- Team rank?
- Utilization percentile?
- Readiness percentile?

### Domain 2 - Team Intelligence

Questions answered:

- Compare with peers
- Succession impact
- Single-point-of-failure
- Backup coverage
- Strategic importance
- Team heatmap
- Vendor contribution
- Delivery contribution
- Manager dependency

### Domain 3 - Capability Intelligence

Questions answered:

- Strongest technologies
- Strongest OEM
- Vendor ranking
- Readiness percentile
- Capability bucket
- Capability score
- Qubit score
- Growth trajectory
- Skill strengths
- Skill gaps

### Domain 4 - Certification Intelligence

Questions answered:

- Certifications held
- Missing certifications
- Accreditation status
- OEM alignment
- Certification risk
- Renewal risk
- Recommended next certifications
- Certification roadmap
- Certification ranking

### Domain 5 - Allocation Intelligence

Questions answered:

- Can I assign now?
- Allocation readiness
- Best course fit
- Delivery confidence
- Backup trainers
- Why not primary?
- Allocation conflicts
- Utilization
- Allocation history
- Similar delivery proof

### Domain 6 - Delivery Intelligence

Questions answered:

- Courses delivered
- Upcoming deliveries
- Batch history
- Feedback quality
- Delivery quality
- Labs readiness
- Student satisfaction
- Delivery timeline

### Domain 7 - Risk Intelligence

Questions answered:

- Hard blockers
- Review risks
- Weak signals
- Missing data
- HR risk
- Feedback risk
- Delivery risk
- Compliance risk
- Allocation risk

### Domain 8 - Growth Intelligence

Questions answered:

- Growth recommendation
- OEM expansion
- Future readiness
- Skill roadmap
- Certification roadmap
- Investment priority
- 30/60/90 plan
- Bench opportunity

### Domain 9 - Manager Action Center

Questions answered:

- What do I do today?
- What's overdue?
- Highest priority?
- Which actions are blocked?
- Escalations
- Coaching tasks
- Certification actions
- Allocation actions

### Domain 10 - Knowledge Intelligence

This becomes the AI operating layer.

Not canned questions.

Not a chatbot.

A deterministic knowledge engine.

The user can ask:

- Why can't I assign Niharika?
- Compare Niharika with Abhinav.
- Which OEM should I invest in?
- What certification blocks AI delivery?
- Show weak signals.
- Explain readiness score.
- Explain allocation score.
- Why is confidence low?

The engine should:

Intent -> Decision Objects -> Evidence -> Response

Every response must show:

- confidence
- evidence
- source datasets
- decision version
- affected entities

## Interaction Philosophy

The main page should never be long.

Instead:

Summary -> Drill Down

Examples:

```text
Course Readiness

AI-102
DP-203
PL-300

[View All]
```

opens

```text
Course Readiness Drawer

Search

Filters

67 Courses

Export

Compare

Evidence
```

Every domain follows this.

## Navigation Philosophy

Trainer Command Center becomes the canonical destination.

All trainer links route here.

Aggregate pages remain:

- Team Overview
- Allocation Desk
- Capability Builder
- Certifications
- Actions

These pages answer:

> Show me everybody.

Trainer Command Center answers:

> Show me everything about one trainer.

## UI Philosophy

- Compact
- Dense
- Executive
- Minimal scrolling
- Progressive disclosure
- Charts only when they aid decisions
- Tables only when necessary
- Drill-down via drawers/offcanvas preferred and modals where appropriate
- No duplicated information
- No page ownership mentality

## Data Philosophy

Decision objects are the source of truth.

Legacy dataframes are fallback only.

Every widget must display:

- Decision source
- Confidence
- Missing data
- Evidence availability

## Practical Implementation Rule

Do not think in pages.

Think in domains, then in drills, then in evidence.

The target is a trainer operating system, not a merged dashboard.
