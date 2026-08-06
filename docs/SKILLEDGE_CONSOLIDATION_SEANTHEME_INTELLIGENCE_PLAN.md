# SKILLEDGE CONSOLIDATION, SEANTHEME & INTELLIGENCE PLAN

> Status note: Historical consolidation plan. The current runtime has authenticated `/rms/<api>`, no `/proxy`, server-side sessions, and compatibility redirects. See `MANAGER_OS_PRODUCTION_STATUS.md` and `DEPRECATED_PAGES.md`.

====================================================
SECTION 1 — CURRENT REALITY
====================================================

**What is actually built today:**
SkillEdge is currently operating as a lightweight Python HTTP server (`server.py`) serving HTML/JS/CSS assets while simultaneously proxying requests to the Koenig API (`/proxy/*`). It includes an intelligence backend (`intelligence.py`) serving a unified JSON dataset (`/data/unified-manager-intelligence`) calculated from API and notebook logic.

**Working Backend Datasets:**
- `trainer_operations_df`: Core trainer profile, capability, and scope.
- `trainer_availability_engine_df`: Composite availability and capacity scores.
- `trainer_timeline_df`: Historical assignment and lifecycle events.
- `course_allocation_df`: Standard course matching and trainer assignments.
- `manager_action_df`: Deterministic action generation.
- `data_health_df`: Trust, missing signals, and API health checks.

**Working Pages:**
- `index.html` (Dashboard)
- `team.html` (My Team)
- `trainer-detail.html` (Trainer 360)
- `allocation-desk.html` (Standard Course Allocation)
- `actions.html` (Action Center)
- `data-health.html` (Data Trust & Health)

**Broken / Partial Areas:**
- `custom-course-match.html` operates mostly on frontend JS heuristics. The backend dataset `custom_course_match_df` is not fully wired up.
- The Availability Engine has unstable dependencies due to 5-second timeouts on calendar APIs.
- Explanations for Readiness are sometimes opaque due to unhandled nulls.

**Duplicate Concepts & Overlapping Pages:**
- `capability-builder.html` overlaps heavily with `custom-course-match.html` and `trainer-detail.html`.
- `trainer-readiness.html` overlaps with `trainer-detail.html` and `index.html`.
- Multiple disjointed views currently attempt to answer "Who can teach X?" (Allocation Desk vs Capability Builder vs Custom Course Match).

**Direction:**
- Retain the 7 core workflows. 
- Merge and pause the remaining supplementary pages to reduce visual clutter and manager confusion.

====================================================
SECTION 2 — ORIGINAL PYTHON AGENDA MAPPING
====================================================

| Original Agenda Item | Built? | Where is it? | API / Dataset | Page | Action |
|---|---|---|---|---|---|
| **Certification mapping** | Yes | Backend | Vendor Cert API, Unique Cert API → `trainer_operations_df` | `trainer-detail.html`, `team.html` | Remain |
| **Future certification planning** | Partial | Backend/UI | Heuristic rule → `manager_action_df` | `actions.html`, `trainer-detail.html` | Merge into Actions |
| **Trainer skill gaps** | Yes | Backend | Trainer Skills API → `trainer_operations_df` | `trainer-detail.html` | Remain |
| **Missing course detection** | Yes | Backend | Course List API + Allocation → `course_allocation_df` | `allocation-desk.html` | Remain |
| **Course upgrade recommendation** | Partial | UI | Derived from capability | `trainer-detail.html` | Merge to Trainer 360 |
| **Readiness scoring** | Yes | Backend | Details API (Qubit) + History → `trainer_operations_df` | `index.html`, `trainer-detail.html` | Remain |
| **Qubit-based strength** | Yes | Backend | Details API → `trainer_operations_df` | `team.html`, `trainer-detail.html` | Remain |
| **Availability intelligence** | Yes (Rule) | Backend | Util API, Calendar APIs → `trainer_availability_engine_df` | `allocation-desk.html`, `team.html` | Remain |
| **Trainer feedback/HR risk** | Yes | Backend | Neg Feedback API, HR Incident API → `trainer_operations_df`, `manager_action_df` | `actions.html`, `trainer-detail.html` | Remain |
| **Custom course matching** | Partial | Frontend JS | Custom file drop + Skill matching | `custom-course-match.html` | Move to Backend |
| **Risk-taker discovery** | Yes (Rule) | Backend | Heuristic buckets → `trainer_operations_df` | `custom-course-match.html` | Merge into Match |
| **AI/ML trainer recommendation** | No | — | Rule-based currently | — | Future ML |
| **Manager action generation** | Yes | Backend | All signals → `manager_action_df` | `actions.html` | Remain |

====================================================
SECTION 3 — FINAL PAGE CONSOLIDATION
====================================================

**1. Dashboard**
- **File:** `index.html`
- **Purpose:** Morning cockpit. Shows what needs attention today.
- **SeanTheme patterns:** `index.html` dashboard structure, `widget.html` KPI cards, `email_inbox.html` action inbox, `chart-apex.html` analytics blocks.

**2. My Team**
- **File:** `team.html`
- **Purpose:** Operational team matrix. Shows all trainers and their state.
- **SeanTheme patterns:** `widget.html` stat cards, `ui_general.html` badges/callouts, `table_manage_combine.html` comparison matrix, `ui_modal_notification.html` quick trainer modal.

**3. Trainer 360**
- **File:** `trainer-detail.html`
- **Purpose:** One trainer deep dive (skill, cert, readiness, availability, timeline, risk, actions).
- **SeanTheme patterns:** `extra_order_details.html` detail layout, `extra_timeline.html` timeline, `widget.html` score cards, `chart-apex.html` gauges/charts.

**4. Allocation Desk**
- **File:** `allocation-desk.html`
- **Purpose:** Course-first matching. Shows course → best trainer / prep trainer / risk-taker.
- **SeanTheme patterns:** `pos_customer_order.html` selection layout, `extra_orders.html` list cards, `extra_order_details.html` best-match detail, `form_plugins.html` filters.

**5. Custom Course Match**
- **File:** `custom-course-match.html`
- **Purpose:** Paste/upload TOC and match trainers. (Future AI/ML home).
- **SeanTheme patterns:** `form_elements.html`/`form_plugins.html` upload input, `email_inbox.html` ranked result list, `widget.html` match score cards.

**6. Action Center**
- **File:** `actions.html`
- **Purpose:** All manager actions in one place (coach, verify, allocate, hold, monitor).
- **SeanTheme patterns:** `email_inbox.html` action inbox, `widget.html` action KPIs, `ui_buttons.html` action buttons, `extra_timeline.html` action journey.

**7. Data Health**
- **File:** `data-health.html`
- **Purpose:** Trust and missing-data page.
- **SeanTheme patterns:** `extra_data_management.html`, `table_manage_combine.html`, `ui_general.html` alerts, `chart-apex.html` issue distribution.

====================================================
SECTION 4 — PAGES TO MERGE OR PAUSE
====================================================

- **capability-builder.html:** Merge into Custom Course Match + Trainer 360.
- **risk-takers.html:** Merge into Custom Course Match + My Team.
- **quality-risk.html:** Merge into Trainer 360 + Action Center.
- **timeline.html:** Convert into Timeline tab/section inside Trainer 360.
- **certification-status.html:** Convert into sections inside Trainer 360, Allocation Desk, and Custom Course Match.
- **assignment-history.html:** Convert into sections inside Trainer 360 and Timeline section.
- **readiness.html:** Merge components into Dashboard, Team, Trainer 360, and Allocation Desk.
- **All other conceptual pages:** Pause and remove from menu.

====================================================
SECTION 5 — SEANTHEME PAGE PATTERN MAP
====================================================

| SkillEdge Page | Manager Question | Dataset Used | SeanTheme Reference Page | Components to Use |
|---|---|---|---|---|
| Dashboard | What needs my attention today? | All | `index.html`, `index_v2.html` | KPI cards, action inbox, analytics blocks (`chart-apex.html`) |
| My Team | Who is on my team and what is their state? | `trainer_operations_df` | `table_manage_combine.html` | DataTables, Badges (`ui_general.html`), Quick View Modal (`ui_modal_notification.html`) |
| Trainer 360 | What is the complete picture of this trainer? | `trainer_operations_df`, `trainer_timeline_df` | `extra_order_details.html` | Detail layouts, `extra_timeline.html`, `chart-apex.html`, Stat Cards (`widget.html`) |
| Allocation Desk | Who is the best fit for this standard course? | `course_allocation_df`, `trainer_availability_engine_df` | `pos_customer_order.html` | Selection/cart layout, `extra_orders.html` list cards, Form filters (`form_plugins.html`) |
| Custom Course Match | Who can teach this custom curriculum? | `custom_course_match_df` | `form_plugins.html`, `email_inbox.html` | Upload/Paste form inputs, Ranked result list, Match score cards |
| Action Center | What do I need to do right now? | `manager_action_df` | `email_inbox.html` | Inbox view, Action Buttons (`ui_buttons.html`), Action Modals |
| Data Health | Can I trust this data? | `data_health_df` | `extra_data_management.html` | Issue tables (`table_manage_combine.html`), Alerts (`ui_general.html`) |

====================================================
SECTION 6 — FINAL INTELLIGENCE ENGINE CONSOLIDATION
====================================================

**Centralize all logic to Backend. No more frontend JS processing of business rules.**

1. **Identity Engine:** (Reportee API) → Owns scoped team list.
2. **Capability Engine:** (Trainer skills, Resume, Course Tech) → Owns skill/course mapping.
3. **Certification Engine:** (Vendor Certs, Resume Certs, Unique Certs) → Owns cert mapping and cert gaps.
4. **Availability Engine:** (Utilization, Assignments, Calendar) → Owns availability labels and confidence scores.
5. **Delivery Experience Engine:** (Assignments, Past Delivery) → Owns past delivery evidence and volume.
6. **Risk Engine:** (Feedback, HR Incidents) → Owns risk labels and feedback severity.
7. **Recommendation Engine:** (All above) → Owns trainer-course matching, next actions, and rank confidence.
8. **Custom Course Match Engine:** (TOC + Capability Engine) → Owns match scores, missing skills, and risk-taker identification.
9. **Action Engine:** (Recommendations) → Converts intelligence into discrete manager tasks.
10. **Trust Engine:** (API states, Nulls) → Owns `data_health_df`, missing signals tracking, and confidence reductions.

====================================================
SECTION 7 — AI/ML MODEL PLAN
====================================================

**Rule-based now (Immediate):**
- Readiness score and buckets.
- Availability labeling.
- Certification source matching.
- Data health and API trust logic.
- Manager action generation.
- Basic custom course keyword matching.
- Risk-taker heuristics (e.g. low utilization + high qubit).

**ML later (Phase 2):**
- Trainer-course success prediction.
- Skill and Course similarity models.
- Adjacent technology vector models.
- Learning velocity tracking.
- Certification ROI and market trend prediction.

**LLM later (Phase 3):**
- Parsing uploaded PDF/DOCX/PPT TOCs.
- Extracting true skills from course outlines.
- Natural language explanation of missing skills.
- Generative learning path creation.
- Manager chat copilot interface.

====================================================
SECTION 8 — CUSTOM COURSE MATCH REALITY
====================================================

**Current State:** Works primarily as a frontend JavaScript heuristic matching user-pasted text against the JSON payload.
**Acceptable for Demo:** Local JS paste-matching is acceptable for initial UI demonstrations.
**Backend Migration:** All heuristic logic must be ported to Python into the `Custom Course Match Engine` yielding a `custom_course_match_df`.
**Future Vision:** 
- Pasted text -> Python NLP Keyword extraction.
- Uploaded Files -> LLM extraction.
- Scoring -> Cosine similarity between extracted course skills and trainer capability embeddings.
- Risk-Taker Flag -> Trainers with high learning velocity but missing 20% of exact skills should be highlighted as "Growth / Risk-Takers" with explanations.

====================================================
SECTION 9 — RELIABILITY PLAN
====================================================

**Practical Fixes to Implement:**
1. Normalize `data_health_df` row shapes and remove blank/null issue rows causing UI breaks.
2. Unify safe text helpers and formatting pipelines across the UI.
3. Remove duplicate page logic (e.g., redundant data fetching in UI).
4. Standardize all icons to Bootstrap Icons (drop FontAwesome dependencies to prevent visual bugs).
5. Enforce that *every* page uses the single `/data/unified-manager-intelligence` endpoint only. No direct RMS API calls from JS.
6. Add unified JSON contract tests to prevent backend changes breaking the UI.
7. Implement per-trainer error isolation so one bad API response doesn't crash the manager's whole dashboard.

====================================================
SECTION 10 — FINAL IMPLEMENTATION ORDER
====================================================

1. **Fix Schema Consistency:** Normalize `data_health_df` and unified JSON shapes.
2. **Standardize UI Assets:** Standardize shared helpers and icons across the project.
3. **Menu Consolidation:** Consolidate the frontend menu to the final 7 pages.
4. **Backend Migration:** Move custom course matching logic strictly to the backend.
5. **Centralize Intelligence:** Consolidate scoring and recommendation logic into the 10 defined Python engines.
6. **Improve Explainability:** Ensure every score outputs an explanation/confidence array.
7. **SeanTheme Polish:** Polish the 7 retained pages using their strictly mapped SeanTheme patterns.
8. **Reassess Excluded Pages:** Only consider adding risk-takers or quality-risk as separate pages if absolutely proven necessary post-consolidation.
