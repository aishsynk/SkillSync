# SKILLEDGE PRODUCT CONSTITUTION

This document serves as the **Master Constitution** for the SkillEdge platform. Every future feature, page, dataset, engine, API integration, AI model, and UI decision must comply with this document to prevent feature drift, duplicate logic, unnecessary pages, and inconsistent implementations.

---

## PART 1: PRODUCT IDENTITY

**What SkillEdge is:**
SkillEdge is an AI-powered Delivery Intelligence Platform built for Delivery Managers, Practice Heads, Delivery SPOCs, Resource Managers, and Training Managers. It acts as an intelligence layer that converts fragmented RMS APIs into explainable, evidence-backed business intelligence that helps managers make better trainer allocation, growth, and risk management decisions.

**What SkillEdge is NOT:**
- NOT a dashboard.
- NOT a CRM (Customer Relationship Management) tool.
- NOT an LMS (Learning Management System).
- NOT a Trainer Portal (trainers are the subject, not the user).
- NOT merely a reporting portal or generic BI tool.

**Target Users:**
Delivery Managers, Practice Heads, Resource Managers, Training Managers, and Delivery SPOCs.

**Primary Business Problems Solved:**
- Delivery readiness and risk assessment.
- Allocation decision-making based on capability and availability.
- Detecting capability gaps and certification value.
- Trust in fragmented, multi-system RMS data.
- Stretch-risk decisions (identifying capable but uncertified trainers).

**Business Outcomes:**
- Reduced allocation friction and faster course-to-trainer mapping.
- Data-driven talent growth and upskilling.
- Reduced delivery failure risks through proactive intelligence.
- Unified managerial cockpit replacing manual spreadsheet analytics.

**Product Principles:**
1. **Intelligence over Data:** Do not just show metrics; show what they mean.
2. **Evidence-Backed:** Every score, recommendation, or bucket must be explainable.
3. **Manager-Centric:** Solve for the manager's decision, not just data visibility.
4. **Backend Truth:** All business logic belongs in the Python backend, never in the frontend.

**Non-Goals:**
- Content authoring or learner delivery.
- Scheduling execution (we provide intelligence, not the calendar mutation).
- Payroll or HR case management.
- Customer sales pipeline management.

**Success Criteria:**
- Managers use SkillEdge as their single source of truth for daily delivery operations.
- The platform reliably provides unified, non-crashing intelligence from underlying fragmented APIs.

---

## PART 2: THE FOUR INTELLIGENCE PILLARS

Every dataset, engine, and page maps to one of four business pillars:

### 1. Delivery Intelligence
**Question:** *Can this trainer successfully deliver?*
**Includes:** Readiness scoring, availability, utilization, delivery history, assignments, certifications, Qubit scores, approvals.
**Mapped Pages/Datasets:** Allocation Desk, Dashboard, `trainer_availability_engine_df`, `course_allocation_df`.

### 2. Capability Intelligence
**Question:** *What should this trainer become next?*
**Includes:** Current skills, resume analysis, certifications, adjacent technologies, upgrade paths, future certifications, market demand, missing skills.
**Mapped Pages/Datasets:** Trainer 360, `trainer_operations_df`.

### 3. Operational Intelligence
**Question:** *What should I do today?*
**Includes:** Manager actions, HR incidents, feedback, timelines, approvals, API trust, data health.
**Mapped Pages/Datasets:** Action Center, Data Health, `manager_action_df`, `data_health_df`, `trainer_timeline_df`.

### 4. AI Intelligence
**Question:** *What would the best manager do?*
**Includes:** Trainer similarity, course similarity, custom course matching, risk-taker prediction, growth prediction, recommendation engine, future AI copilots.
**Mapped Pages/Datasets:** Custom Course Match, `custom_course_match_df` (future backend implementation).

---

## PART 3: BUSINESS KNOWLEDGE GRAPH

The internal knowledge graph defines the core entities and their relationships. Pages only visualize this graph.

**Entities & Relationships:**
- **Manager** `owns` **Trainer** (Scope)
- **Trainer** `has` **Skill**, **Certification**, **Assignment**, **Feedback**, **HR Incident**, **Utilization**, **Availability**, **Resume**, **Qubit**
- **Course** `requires` **Skill**, `mapped_to` **Trainer**, `belongs_to` **OEM**
- **OEM** `issues` **Certification**, `owns` **Course**
- **Batch** `is_instance_of` **Course**, `delivered_by` **Trainer**
- **Manager** `takes` **Action** `on` **Trainer**
- **Data Health** `monitors` (All Entities)

---

## PART 4: TRAINER DNA MODEL

SkillEdge does not expose just metrics; it derives a "Trainer DNA" profile based on existing backend data (no new APIs).

- **Explorer:** High diversity of distinct courses delivered, actively adding new skills.
- **Specialist:** Deep history in a very narrow set of technologies; high Qubit in specific domains.
- **Generalist:** Broad delivery history across multiple OEMs, moderate Qubits across many skills.
- **Mentor:** High delivery volume, high Qubit, positive feedback, long tenure.
- **Delivery Expert:** Exceptionally high utilization, flawless feedback, strong HR safety.
- **Emerging AI Trainer:** Has recent future-skill flags, actively picking up Copilot/AI course delivery.
- **Fast Learner:** High learning velocity (rapidly gaining certifications), successfully delivering stretch assignments.
- **Risk Taker (Growth):** Low current utilization, high Qubit, willing to take on courses missing 20% exact skills.
- **Reliable Performer:** Consistent 80%+ utilization, stable feedback, core team backbone.
- **OEM Specialist:** 90%+ of delivery and certifications tied to a single vendor (e.g., Microsoft).
- **Capability Builder:** Focuses on acquiring exams and certs, often mapping unlinked courses.
- **High Risk:** High negative feedback count, HR incidents, or consistent utilization drops.

---

## PART 5: TECHNOLOGY KNOWLEDGE GRAPH

A conceptual graph enabling capability mapping and Custom Course Matching.

**Example Graph Structure:**
- **Power BI** `connects_to` SQL, Fabric, Excel, Azure SQL, Power Query, Power Platform.
- **Python** `connects_to` Machine Learning, AI, Data Science, FastAPI, Pandas, NumPy, TensorFlow, PyTorch, Azure AI.
- **Azure AI** `connects_to` OpenAI, Agents, Prompt Engineering, RAG, Vector Search, Azure AI Foundry.

*This graph bridges the gap between explicit trainer capabilities and implicit required skills.*

---

## PART 6: CUSTOM COURSE MATCH ENGINE

The flagship AI intelligence engine (backend-driven).

**Inputs:**
- PDF, DOCX, PPTX, Markdown, Copied syllabus, Course outline, RFP, Microsoft Learn module, Certification blueprint, GitHub README.

**Expected Outputs:**
- Topic extraction, Technology extraction, Skill extraction, Difficulty estimation.
- Technology adjacency, Certification mapping.
- Trainer similarity, Capability overlap, Gap analysis, Stretch capability.
- Risk assessment, Preparation estimate.
- Best trainer, Backup trainer, Mentor recommendation.
- Business confidence score, Expected delivery success.

**Evolution:**
- *Current:* Rule-based frontend keyword matching.
- *Next:* Python backend heuristic extraction.
- *Future:* LLM-assisted capability extraction, vector embeddings, and cosine similarity for trainer-course matching.

---

## PART 7: SEANTHEME COLOR ADMIN DESIGN SYSTEM

Custom UI is strictly forbidden where SeanTheme provides an enterprise pattern.

**Page to Pattern Mapping:**
- **Dashboard:** `index_v3.html` (layout) + `widget.html` (KPI cards) + `email_inbox.html` (actions) + `chart-apex.html` (analytics).
- **Trainer 360:** `extra_order_details.html` (layout) + `widget.html` (stat cards) + `extra_timeline.html` + `chart-apex.html`.
- **Allocation Desk:** `pos_customer_order.html` (selection layout) + `extra_orders.html` (list cards) + `form_plugins.html` (filters).
- **Custom Course Match:** `form_plugins.html` (upload/paste) + `email_inbox.html` (ranked results) + `widget.html` (match score) + `chart-apex.html`.
- **My Team:** `table_manage_combine.html` (data matrix) + `ui_general.html` (badges) + `ui_modal_notification.html` (quick view).
- **Action Center:** `email_inbox.html` (inbox view) + `ui_buttons.html` (actions) + `ui_modal_notification.html`.
- **Data Health:** `extra_data_management.html` + `table_manage_combine.html` + `ui_general.html` (alerts).

---

## PART 8: PAGE CONSOLIDATION

SkillEdge relies on exactly **seven core pages** reflecting business workflows, not datasets.

**Final Navigation Structure:**
1. **Dashboard (`index.html`)** - Morning cockpit.
2. **My Team (`team.html`)** - Operational team matrix.
3. **Trainer 360 (`trainer-detail.html`)** - Deep dive into a single trainer.
4. **Allocation Desk (`allocation-desk.html`)** - Standard course-first matching.
5. **Custom Course Match (`custom-course-match.html`)** - AI-assisted custom syllabus matching.
6. **Action Center (`actions.html`)** - Manager task inbox.
7. **Data Health (`data-health.html`)** - Trust and API monitoring.

*(All other pages like capability-builder, risk-takers, timeline, readiness must be merged into the above 7 views.)*

---

## PART 9: RULE-BASED TO AI ROADMAP

**Current Rule-Based Implementation:**
- Readiness scoring, Availability labeling, Data health, Manager actions, Heuristic keyword matching.
- *Required APIs:* All current 9 core APIs (Reportee, Details, Skills, Utilization, Feedback, HR, etc.).

**Phase 2: ML Models:**
- Trainer-course success prediction, Skill/Course similarity (vector models), Learning velocity tracking, Certification ROI.
- *Training Data:* Historical assignment success, feedback sentiment, utilization trends.
- *Confidence Model:* Statistical probability based on sample size of past deliveries.

**Phase 3: LLM Enhancement:**
- Parsing uploaded outlines, natural language missing-skill explanations, generative learning paths, Manager Copilot chat.
- *Explainability Model:* LLM-generated transparent reasoning ("Trainer X is a 90% match because they possess Python and Pandas, overcoming the gap in FastAPI via adjacent knowledge").

---

## PART 10: IMPLEMENTATION CONSTITUTION

These permanent development rules govern all future work:

1. **Business logic belongs only in Python.** The frontend is strictly a visualization layer.
2. **HTML/JS never calls RMS APIs directly.** All data flows through `/data/unified-manager-intelligence` or equivalent backend proxy endpoints.
3. **Every recommendation must be explainable.** Provide the "why" alongside the score.
4. **Every confidence score must show evidence.** Disclose missing signals.
5. **Every page must answer one manager decision.** Do not build pages just to display a dataset.
6. **Every new feature must map to one intelligence pillar.**
7. **Every page must reuse SeanTheme components.** Do not invent custom UI.
8. **Every API must have one authoritative owner.**
9. **No duplicate datasets.**
10. **No duplicate scoring.**
11. **No duplicate recommendations.**
12. **No hidden business logic in JavaScript.**
