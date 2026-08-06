# SkillEdge / SkillNex Manager — Complete Product Redesign (v3)

Grounded in (a) a live review of 24 pages on `seantheme.com/color-admin/admin/html/` performed before writing a word of this plan, and (b) first-hand knowledge of the current codebase — I built Phases 1–6 (`docs/MANAGER_REDESIGN_BLUEPRINT_2026.md`) and Phase A's diagnosis (`docs/MANAGER_REDESIGN_BLUEPRINT_V2_2026.md`) myself in this session, plus fresh reads of `backend/services/refresh_service.py` and `backend/knowledge/*.py` for this pass.

---

## 0. Live SeanTheme Audit — what I actually found

I fetched and inspected: `index.html`, `index_v2.html`, `ai_chat.html`, `ai_image_generator.html`, `email_inbox.html`, `email_detail.html`, `email_compose.html`, `widget.html`, `ui_tabs_accordions.html`, `ui_modal_notification.html`, `ui_widget_boxes.html`, `ui_offcanvas_toasts.html`, `ui_media_object.html`, `form_elements.html`, `form_plugins.html`, `form_wizards.html`, `form_dropzone.html`, `form_validation.html`, `table_manage_combine.html`, `chart-apex.html`, `extra_profile.html`, `extra_data_management.html`, `extra_timeline.html`, `extra_scrum_board.html`, `login_v3.html`, `page_with_search_sidebar.html`, `page_with_right_sidebar.html`, `page_full_height.html`, `calendar.html`.

**Two live pages named in the request don't exist standalone** — `extra_settings.html` and `extra_error.html` both **301-redirect to `index_v3.html`** on the current live site. So "use the `extra_settings.html` pattern" and "use `extra_error.html`'s error-page design" can't be literally sourced — I substitute the closest real pattern from what does exist (settings → the tabbed/sectioned form layout from `form_elements.html` + `ui_widget_boxes.html`'s panel styles; error state → the plain alert/empty-state pattern already used across our own pages, which is itself SeanTheme-consistent).

**Confirmed real, reusable patterns (this drives Section 4 below):**
- **Dashboard:** 4-KPI-card row + tabbed content panels + right-rail widgets (todo/calendar/map) — `index.html`/`index_v2.html`.
- **AI Chat:** two-column layout — left conversation-history rail (New Chat button, grouped by recency) + right message thread (user/assistant bubbles, assistant avatar) + suggested-prompt chips above the input box.
- **Email Inbox/Detail/Compose:** folder+label left rail, message list (avatar/sender/subject/preview/timestamp), 3-button toolbar (Delete/Archive/Junk), detail view with reply/delete/archive + attachment chips, compose with To/Cc/Bcc + rich editor + Send/Attach/Save draft.
- **Widgets (11 distinct types on `widget.html`):** stat-card, chart-progress card, todolist, chat-preview, map, icon-card, promo-card, square-stat-grid, table-widget, list-with-icon, image-only card.
- **Tabs/Accordion:** underline tabs, pill tabs, 7-item collapsible accordion.
- **Modals/Notifications:** 4 modal variants (default/fade/full-width-white/alert), Gritter-style toasts, **SweetAlert with 5 tone variants (primary/info/success/warning/danger)** — this is the exact plugin already sitting unused in our `frontend/assets/plugins/sweetalert`.
- **Widget boxes (12 variants):** default/dropdown/radio-header/progress-header/badge-header/alert-body/hover-icons/scrollable/toolbar+footer/tabbed/6 color themes/full-color.
- **Offcanvas/Toast:** right (default) and bottom placements, flex-based toast header, stacking supported.
- **Media object:** avatar+heading+text, nested/list variant, large-image variant, action-button variant (Reject/Cancel/Confirm/Edit) — this is the correct pattern for the Review Flags evidence list and Decision Inbox rows.
- **Forms:** default/horizontal/inline layouts, floating labels, input groups, Select2/date-range/IonRange/masked-input/tag-it (all confirmed present in our plugin bundle), inline validation with "Looks good!"/error text.
- **Wizards:** 3 layout variants, explicitly UI-only ("no javascript or backend logic" per the theme's own docs) — confirms a wizard is a pure visual shell we drive ourselves, exactly as planned in v2.
- **DataTables combined example:** ColReorder + Buttons + KeyTable + Responsive + RowReorder + Select, all in our plugin bundle already, unused everywhere in our app.
- **ApexCharts:** 12 chart types available (line/column/area/bar/mixed/candlestick/bubble/scatter/heatmap/pie/radial/radar) — we currently only use donut and bar.
- **Profile page:** cover+avatar hero, 5 tabs (Posts/About/Photos/Videos/Friends) — the About-tab field-grid pattern is the right shape for a Trainer Cockpit summary block.
- **Data management page:** filter toolbar (date/status/location/priority + 2 custom filters) + Export/Print/Reload actions + status-badge table + footer totals row — this is *exactly* the Review Flags / Data Health pattern we should formalize.
- **Timeline:** vertical stack, date-grouped, avatar-anchored, dropdown menu per entry (Save/Edit/Archive) — matches trainer action history.
- **Scrum board:** 3-column kanban with counts, card metadata, subtask progress, drag handles — a strong candidate for a future "Allocation Pipeline" board view (not required now, noted as an option).
- **Login v3:** centered form, branding above fields, email+password+remember-me+sign-in — structurally identical to our current `login.html`, confirming Phase 1 already got this right.
- **Layout variants:** confirmed as CSS-class toggles on the `.app` container (`app-with-end-sidebar`, `app-content-full-height`, `data-sidebar-search="true"`) — not separate page templates. Useful to know these are one-line opt-ins, not rebuilds.

---

## 1. Executive Architecture Review

**Where the product actually stands today** (I know this first-hand, not from documentation): a real, working, explainable backend pipeline (`intelligence.py` → per-trainer scoring → 6 canonical datasets → decision objects → `classification` overlay → action/review-flag lifecycle overlays → custom-course-match scoring), served through one unified endpoint, consumed by 14 static pages that share a common nav/KPI-helper (`app.js`). Terminology is clean (Phase 1), KPI duplication is centralized (Phase 2), classification is server-resolved (Phase 3), manager actions/flags persist (Phase 4), course matching is backend-first (Phase 5), and it's all QA-verified (Phase 6). **The architecture is sound. What's missing is not more backend — it's: (a) the product not lying about data freshness, (b) an AI layer worth the name, (c) a menu that reads like an executive tool instead of a developer's file list, and (d) a visual language that uses the SeanTheme asset library it's already paying the bytes for but not using** (DataTables, Select2, SweetAlert, real chart variety, offcanvas, wizard shell).

**Production-readiness verdict:** backend 8/10 (deduct only for the `generated_at` freshness bug found in v2 and the vendor outage exposure), frontend 6/10 (functionally complete, visually flat — plain `<select>` and hand-rolled tables everywhere a real plugin sits unused), AI 3/10 (a genuinely good rule-based single-trainer Q&A engine exists in `trainer-intelligence.html` but it's undiscoverable, not schema'd, not evidence-badged, and not available anywhere else), knowledge layer 4/10 (real JSONL flattening + working search exists in `refresh_service.py`, but two separate, non-integrated adjacency dictionaries exist — one live in `backend/knowledge/technology_graph.py`, unused; one duplicated inline in `custom_course_match_service.py`, used — this is exactly the kind of duplicate-logic problem Phase 1–3 solved elsewhere in the app and missed here).

---

## 2. Menu Redesign (final)

Same 14 pages, same hrefs (no link breakage), executive-friendly labels, one rename executed properly this time (file + shim, not just page copy):

| Group | Item | Page | Notes |
|---|---|---|---|
| **Command Center** | Dashboard | `index.html` | |
| **Command Center** | Decision Inbox | `actions.html` | was "Action Center" |
| **Trainer Intelligence** | Team Overview | `team.html` | |
| **Trainer Intelligence** | Trainer Cockpit | `trainer-intelligence.html` | was "Trainer Command Center" |
| **Trainer Intelligence** | Review Flags | `review-flags.html` (renamed from `risk-takers.html`) | was "Growth & Risk" |
| **Delivery Planning** | Allocation Desk | `allocation-desk.html` | |
| **Delivery Planning** | Course Match | `custom-course-match.html` | was "Custom Course Match" |
| **Capability Growth** | Capability Builder | `capability-builder.html` | |
| **Capability Growth** | Certifications | `certifications.html` | |
| **AI & Knowledge** | Ask SkillNex AI | new panel, deep-linkable from Dashboard + Trainer Cockpit | no new standalone page yet |
| **AI & Knowledge** | Refresh & Trust | `data-health.html` | relabeled to reflect its expanded `platform_status` role |
| **System** | Settings | `settings.html` | |
| **System** | Logout | (existing) | |

No dead pages, no duplicate nav entries, no new page count beyond the file rename. "Backup Planning" and "Skill Gaps" remain **sections inside** Allocation Desk and Capability Builder respectively — confirmed correct in v2 and unchanged here; splitting them into pages would recreate the fragmentation the very first blueprint (Phase 1) diagnosed as the core problem.

---

## 3. AI Redesign — Manager Copilot

**Foundation (do not discard):** `trainer-intelligence.html`'s `answerTrainerQuestion()` / `renderKnowledgeAssistant()` already implements 15 real question types against real evidence (`decision.risk_register`, `classification`, `certification_gap_df`, etc.) with `{answer, evidence, source, confidence}`. This is a working rule-based copilot, just scoped to one trainer and undiscoverable. **Formalize and extend it — do not build a second engine.**

**Manager Copilot answer schema (extends the v2 draft with the explicit requirements from this request):**
```json
{
  "answer_type": "trainer_summary | allocation_recommendation | certification_blocker | review_flag | capability_gap | readiness | backup_ranking | data_health",
  "answer": "plain-English sentence",
  "why": "the specific rule/threshold that produced this answer",
  "evidence": [{"dataset": "trainer_operations_df", "object_id": "...", "field": "classification.primary_blocker", "value": "..."}],
  "decision_path": ["trainer_decision_objects.risk_register[0]", "classification.has_compliance_flag", "manager_action_objects[...]"],
  "datasets_used": ["trainer_decision_objects", "classification", "certification_gap_df"],
  "confidence": 0-100,
  "confidence_label": "High|Medium|Low",
  "serving_mode": "live|cached|demo",
  "freshness_note": "string or null",
  "hallucination_guard": "not_found | ok"
}
```
**Non-negotiable rules (code-enforced, not just documented — same discipline as Phase 3's classification rule):**
1. Every answer function must resolve its verdict from `classification`, `trainer_decision_objects`, `allocation_decision_objects`, `custom_course_match_objects`, or `data_health_df` — never re-derive from raw fields. This is Phase 3's rule extended to the AI layer specifically.
2. **`decision_path`** is new and directly answers "explain WHY / explain the decision path" — it's the literal chain of object fields the answer traversed, not a prose reconstruction after the fact.
3. If the referenced trainer/course/certification isn't in the current payload, return the fixed `"cannot verify — not present in current scope"` answer with `hallucination_guard: "not_found"` — this is the hard anti-hallucination gate.
4. If `serving_mode !== "live"`, every answer prepends `freshness_note` — inherited from v2, now enforced at the schema level, not just as a UI convention.
5. Six required answer types, matching this request exactly: **Trainer Summary, Allocation Recommendation, Certification Blocker, Review Flag, Capability Gap, Readiness** — plus **Backup Ranking** (explaining *why* trainer B is trainer A's suggested backup, using the real `backup_trainers` match-score list Phase 5 built) and **Data Health** (explaining *why* a signal is stale/missing).

**UI (SeanTheme `ai_chat.html` pattern, confirmed live):** left rail = suggested-question chips grouped by answer type (not full chat history — a manager doesn't need saved conversation threads the way a general chatbot does); right pane = message thread with **evidence cards** rendered under each assistant bubble (dataset name, field, value — using the media-object list pattern confirmed on `ui_media_object.html`) and a freshness/confidence badge row using the same badge language already established (Phase 1's `badge_tone` values). Ship as `SkillEdge.renderAssistantPanel()` in `app.js` (Phase 2 precedent: centralize once, mount on Dashboard *and* Trainer Cockpit — not two implementations).

---

## 4. Complete UX/UI Redesign — Page by Page

Format per the request's exact field list. KPI cards on every page must still pass the Phase 1 test (assign/hold/coach/certify/escalate/verify) — no page below gets a pass to reintroduce a vanity KPI.

### `index.html` — Dashboard (Command Center)
- **Purpose/User/Workflow/Business goal:** morning triage for a Delivery Manager — "what needs a decision today"; workflow: scan KPI row → open Decision Inbox item or Ask Copilot → act → return.
- **APIs/services/data:** `/data/unified-manager-intelligence` (+ new `platform_status` from v2 Phase A); `manager_action_objects`, `trainer_operations_df`/`classification`, `certification_gap_df`, `single_point_failure_df`.
- **KPIs:** Ready Now · Review Required · Open Actions · Certification Gaps · Single Point Risks · Overloaded (unchanged, Phase 1–3 verified).
- **Charts:** readiness donut, capacity scatter (existing) — **add** a radial-bar "team health" gauge (SeanTheme confirmed radial-bar support) replacing the plain percentage text.
- **Tables:** none primary (evidence tables stay collapsed, per the original Page Design Map rule).
- **Filters:** none at this level — filtering lives on Team/Actions.
- **Cards:** SeanTheme stat-card pattern (already matched).
- **AI panel:** new — Ask SkillNex AI, collapsed by default, one suggested question pre-filled ("What needs my attention today?").
- **Evidence panel:** Data Confidence modal (existing, keep).
- **Actions:** none inline — links out to Decision Inbox/Team/Review Flags.
- **Nav:** links to all 5 other groups.
- **Empty state:** "No trainers in scope" (existing).
- **Error state:** existing `loadError` alert block — **add** the v2 `platform_status` banner here specifically.
- **Loading state:** existing spinner — keep.
- **Toasts:** none today — **add** SweetAlert toast on manual refresh completion.
- **Modals:** Data Confidence (existing).
- **Drawers:** none.
- **Search:** existing global search box in header — currently decorative; wire it to jump to Team/Trainer Cockpit by name (small, real fix).
- **Drilldowns:** existing chart click-throughs (keep).
- **Remove:** nothing further — Phase 1–3 already cleaned this page.
- **Keep:** everything else.

### `team.html` — Team Overview
- **Purpose/User/Workflow/Goal:** roster reference and comparison, not a second dashboard.
- **APIs/data:** `trainer_operations_df`, `classification`, `trainer_availability_engine_df`.
- **KPIs:** Total Trainers · Direct Reportees · Ready Now · Available (unchanged, Phase 1 verified — do not re-add Risk/Cert/Overload tiles).
- **Charts:** readiness/risk/availability donuts (existing, keep).
- **Tables:** **replace hand-built `<table>` with DataTables** (`datatables.net-bs5` + responsive + fixedheader, confirmed present) — sort/export/pagination for free, matches `table_manage_combine.html`'s confirmed pattern.
- **Filters:** Select2 instead of plain `<select>` (confirmed plugin present, currently unused anywhere).
- **Cards:** roster KPI row only.
- **AI panel:** none needed at roster level — link to Trainer Cockpit's copilot instead.
- **Evidence panel:** existing collapsed accordion, keep.
- **Actions:** row click → Trainer Cockpit (existing).
- **Empty/Error/Loading:** existing, keep.
- **Toasts:** SweetAlert on refresh.
- **Modals:** Data Confidence (existing).
- **Drawers:** **new** — row-click profile-preview offcanvas (right, confirmed SeanTheme pattern) as a faster alternative to full navigation, without removing the existing full Trainer Cockpit link.
- **Search:** existing per-page search box, keep.
- **Remove:** hand-rolled table markup once DataTables lands (logic stays, render layer only changes).

### `trainer-intelligence.html` — Trainer Cockpit
- **Purpose/User/Workflow/Goal:** the single-trainer decision surface — "is this trainer safe to allocate, and why."
- **APIs/data:** full unified payload scoped to one trainer; `classification`; `trainer_decision_objects`; `custom_course_match_objects`; lifecycle overlays (Phase 4).
- **KPIs:** per-trainer decision cards (Readiness/Alloc Fit/Utilization/Cert Health/Delivery Qlty/Risk Level/Pending Actions/Data Conf.) — correct pattern, unchanged.
- **Charts:** existing mini-charts, keep.
- **Tables:** existing evidence tables — convert long ones (allocation history) to DataTables.
- **Filters:** trainer-switcher search (existing), keep.
- **Cards:** existing risk/blocker cards, use the confirmed **widget-box "alert-body" and "badge-header" variants** for compliance/delivery-concern cards instead of plain `<div class="alert">`.
- **AI panel:** the full Manager Copilot (Section 3), replacing today's narrower `renderKnowledgeAssistant` with the shared `SkillEdge.renderAssistantPanel()`.
- **Evidence panel:** existing evidence drawer/modal, keep, restyle with media-object pattern.
- **Actions:** Close/Escalate/Reassign (Phase 4), Acknowledge/Resolve/Escalate flag buttons (Phase 4) — **replace `window.prompt()` with SweetAlert input dialogs** (SweetAlert supports prompt-style inputs — this directly fixes the UX limitation Phase 4's own QA flagged).
- **Nav:** tabs (SeanTheme `ui_tabs_accordions.html` underline-tab pattern, confirmed) instead of today's long stacked panel sections — Summary / Evidence / Actions / Course Match / Certifications / Review Flags.
- **Empty/Error/Loading:** existing, keep.
- **Toasts:** SweetAlert success/error on every lifecycle action (replacing plain `showToast` — or layering SweetAlert's toast mode on top of the existing `showToast` helper so nothing else that calls `showToast` needs to change).
- **Modals:** existing detail modals, keep.
- **Drawers:** trainer-switcher becomes an offcanvas (right) instead of the current inline column — frees vertical space for the new tabs.
- **Search:** existing, keep.
- **Remove:** the old `window.prompt()`-based lifecycle flow (replaced, not deleted logic — same endpoints).

### `allocation-desk.html` — Allocation Desk
- **Purpose/Workflow/Goal:** course-to-trainer assignment decisions.
- **APIs/data:** `course_allocation_df`, `allocation_decision_objects`, `classification`.
- **KPIs:** unchanged 8-tile set (Phase 2, out of Phase 1 terminology scope but content-correct).
- **Charts:** existing.
- **Tables:** DataTables with **RowReorder** disabled (allocation isn't manually orderable) but **Select** enabled for bulk actions (confirmed plugin), **ColReorder** for manager-customizable column order.
- **Filters:** Select2.
- **Cards:** existing.
- **AI panel:** Ask Copilot deep-link pre-filled with "Explain this allocation recommendation" (Allocation Recommendation answer type).
- **Actions:** assign/reject/review — **SweetAlert confirmation** instead of native `confirm()`.
- **Empty/Error/Loading/Toasts/Modals:** existing, keep; add SweetAlert toasts.
- **Remove:** native `confirm()` calls.

### `actions.html` — Decision Inbox
- **Purpose/Workflow/Goal:** the actual action queue — close/escalate/reassign.
- **APIs/data:** `manager_action_objects` + lifecycle overlay (Phase 4).
- **KPIs:** unchanged (Open · Critical · High Priority · Blocked · Escalated · Data Quality).
- **Tables/list:** convert the current modal-based detail view into a **right-side offcanvas drawer** (confirmed SeanTheme pattern) — a real inbox feel without needing the literal `email_inbox.html`/`email_detail.html` files (not needed; the pattern is fully reproducible with Bootstrap offcanvas + the existing action-list markup).
- **Actions:** Close/Escalate/Reassign — **SweetAlert prompt-style dialogs** replacing `window.prompt()` (same fix as Trainer Cockpit, same endpoints, no backend change).
- **AI panel:** Ask Copilot pre-filled with "What's the highest-priority open action?"
- **Toasts:** SweetAlert on every lifecycle transition.
- **Remove:** `window.prompt()`.

### `capability-builder.html` — Capability Builder
- **KPI trim (finally executing what v2 flagged as deferred):** drop "Risk-Taker Candidates" (owned by Review Flags) and the page-level "Data Confidence" tile (owned by header badge); keep Ready to Upgrade / Needs Coaching First / OEM Bench Risks / Future Skill Items / Certification Attention.
- **Charts:** add radar chart (confirmed ApexCharts support) for skill-coverage-by-trainer — genuinely useful multi-axis view this page currently lacks.
- **Tables:** DataTables for the trainer-gap list.
- **AI panel:** Ask Copilot pre-filled "Explain this capability gap" (Capability Gap answer type).

### `certifications.html` — Certifications
- **Tables:** DataTables with **FixedHeader** (confirmed plugin, matches the page's own long-list nature) + Select2 filters.
- **AI panel:** Ask Copilot "Explain this certification blocker" (Certification Blocker answer type).
- **KPIs:** unchanged.

### `review-flags.html` (renamed from `risk-takers.html`) — Review Flags
- **Purpose:** governance — compliance flags, delivery concerns, lifecycle state.
- **Layout:** adopt the **data-management page pattern** confirmed live (filter toolbar + status-badge table + Export/Print actions + footer totals) — this page's current ad hoc filter row becomes a proper toolbar matching that pattern exactly.
- **KPIs:** unchanged (Open Review Flags · Hard Blockers · Review Risks · Coaching Needed · Single Point Risks · Bench/OEM Risk · Growth Candidates).
- **Actions:** Acknowledge/Resolve/Escalate (Phase 4) — SweetAlert dialogs.
- **AI panel:** Ask Copilot "Explain this review flag" (Review Flag answer type).
- **Terminology:** Compliance Flag / Delivery Concern / acknowledged / resolved / escalated — unchanged, still no punitive wording.
- **Migration:** file rename + `frontend/deprecated/risk-takers.html` redirect shim (same pattern as `trainer-detail.html`), `MENU_MODEL` href updated, grep-verified no dangling links before cutover.

### `custom-course-match.html` — Course Match
- **Layout:** convert to the **3-layout wizard shell confirmed live** (Layout 1's numbered-circle step indicator is the best fit) — Step 1 Paste Outline → Step 2 Extracted Skills → Step 3 Matched Trainers → Step 4 Confirm/Assign. Purely a visual/state-machine change; **Phase 5's backend scoring is untouched**.
- **Upload:** Dropzone (confirmed plugin) for the file-upload path — but only if a real parsing backend is built first; do not fake a working upload against no parser (same caution v2 already gave).
- **AI panel:** Ask Copilot "Explain this backup trainer ranking" (Backup Ranking answer type) using Phase 5's real `backup_trainers` scores.
- **Keep:** required_skills/compliance_flag/delivery_concern/certification_blockers/readiness_blocker/backup_trainers fields (Phase 5), all terminology (Phase 1).

### `data-health.html` — Refresh & Trust
- **New primary content:** the v2 `platform_status` panel (RMS reachability, failing endpoint, IIS/HTTP signature, last live success, next retry) — this page's role literally expands to match its new menu label.
- **Layout:** data-management pattern (confirmed live) for the issue list — filter by severity/dataset/page, Export for sharing with the RMS vendor team when escalating the outage.
- **AI panel:** Ask Copilot "Explain this data health issue" (Data Health answer type).
- **KPIs:** unchanged (system/data-quality only).

### `settings.html` — Settings
- Since `extra_settings.html` doesn't exist live, use the confirmed **form-elements + widget-box** patterns: horizontal-form sections (Account & Session / Cache & Refresh / API Connectivity / Display Preferences / Intelligence Score Weights — all already present) each inside a themed widget-box panel with a header toolbar, rather than today's flat stacked sections.
- **New:** Diagnostic Mode toggle (demo dataset, v2), refresh-cadence read-only display.
- **Keep:** Compliance Record terminology (Phase 1).

### `login.html` — Login
- Already matches the confirmed `login_v3.html` structural pattern (centered form, branding above fields, email+remember-me+sign-in). **No redesign needed** — Phase 1's terminology fix already made this page correct. Do not touch except to keep it in sync if the header/branding elsewhere changes.

---

## 5. Backend Redesign Recommendations

- **Consolidate the two adjacency dictionaries.** `backend/knowledge/technology_graph.py` (orphaned, never imported by anything) and the inline `SYNONYMS`/`ADJACENT` dicts in `backend/services/custom_course_match_service.py` (actually used) cover overlapping ground with different data. Merge into one authoritative `backend/knowledge/technology_graph.py`, have `custom_course_match_service.py` import it instead of hand-rolling its own copy. This is the same "one authoritative owner per signal" rule Phase 1–3 already applied everywhere else — this is the one place it was missed.
- **`backend/intelligence_engines/*.py` (6 files)** remain confirmed docstring-only scaffolding — either delete (the real logic already lives correctly in `backend/shared/*_intelligence.py`) or actually migrate logic there. Recommend: delete, and stop referencing this directory in any future doc, since it currently misleads readers about where scoring logic lives.
- **`platform_status`** (v2, Phase A) becomes the one new backend contract this round — everything else is additive fields on existing objects, not new datasets.
- **Manager Copilot answer functions** — start in `frontend/js/app.js` (mirroring where `answerTrainerQuestion` already lives) for the six required answer types; only promote to a `backend/shared/assistant_answers.py` if manager-wide aggregation (not just per-trainer) proves too heavy for the browser — do not build backend-side prematurely.

## 6. API Redesign

No new external RMS APIs are needed or obtainable right now (outage). Internal surface changes:
- **Extend, don't split, the unified payload:** add `platform_status` (v2) as the only new top-level key this round.
- **No new REST routes needed for the AI layer** — it's a payload-reading function, same as every other page, consistent with "do not split the unified read endpoint" from every prior phase.
- **Existing lifecycle endpoints (Phase 4) are final** — `/api/actions/{id}/close|escalate|reassign`, `/api/review-flags/{id}/acknowledge|resolve|escalate` — no changes.

## 7. Data Model Redesign

- `trainer_operations_df` rows keep their existing shape + `classification` (Phase 3) + `platform_status` reference (new, at payload root, not per-row).
- `manager_action_objects` / trainer `classification` keep their Phase 4 lifecycle overlay fields.
- `custom_course_match_df` keeps Phase 5's schema (`required_skills`, `compliance_flag`, `delivery_concern`, `certification_blockers`, `readiness_blocker`, `backup_trainers`).
- **No schema breaking changes anywhere in this round** — every addition is additive, matching the discipline every prior phase followed.

## 8. Knowledge Engine Redesign

`refresh_service.py`'s `build_knowledge_base_from_payload()` is real and working — it flattens the unified payload into 7 typed JSONL files (`entities`, `trainer_profiles`, `course_matching`, `risk_signals`, `action_recommendations`, `manager_decisions`, `refresh_snapshots`) with `entity_type`/`source`/`confidence`/`page_module`/`business_purpose` on every row, and `/api/knowledge/search` does a literal substring search over the JSON-dumped rows. **This is a legitimate v1 knowledge base — not a stub.** Its two real gaps:
1. Search is substring-only (no ranking, no field-weighting) — acceptable for now, but the Manager Copilot's evidence citations should reference these `entity_type`/`source` fields directly rather than re-deriving citation text, since the KB already carries exactly the "which dataset, which page, which confidence" metadata the Copilot needs.
2. The orphaned/duplicated technology-adjacency issue (Section 5) is technically a knowledge-engine problem, not a course-match problem — fixing it there also strengthens what "Knowledge Base" surfaces mean when a manager searches for a skill/technology.

No rebuild needed — wire the Copilot to read from this KB's metadata fields instead of only the unified payload, and fix the adjacency duplication.

## 9. RMS Resiliency Redesign

Already fully diagnosed in v2 Section 1.1 — restated as the authoritative version here: fix the `generated_at` overwrite bug, add `platform_status`, capture real HTTP diagnostics (status + truncated body + path) on every token/data call failure, surface a banner on Dashboard + Data Health, never let an AI answer claim freshness it doesn't have. The vendor-side IIS 404 on both `GetToken` and `common` is not fixable in this codebase — escalate with the exact evidence captured in v2.

---

## 10. Page-by-Page Implementation Plan (condensed from Section 4)

| Page | Primary change this round | Depends on |
|---|---|---|
| `index.html` | `platform_status` banner, radial-bar chart, Ask Copilot panel, wire header search | Phase A, Phase C |
| `team.html` | DataTables, Select2, profile-preview offcanvas | Phase D |
| `trainer-intelligence.html` | Tabs layout, shared Copilot panel, SweetAlert lifecycle dialogs, offcanvas trainer-switcher | Phase C, Phase D |
| `allocation-desk.html` | DataTables (Select+ColReorder), Select2, SweetAlert confirmations | Phase D |
| `actions.html` | Offcanvas detail drawer, SweetAlert lifecycle dialogs | Phase D |
| `capability-builder.html` | KPI trim, radar chart, DataTables | Phase D |
| `certifications.html` | DataTables+FixedHeader, Select2 | Phase D |
| `review-flags.html` (renamed) | Data-management layout pattern, SweetAlert dialogs | Phase B (rename), Phase D |
| `custom-course-match.html` | Wizard-step shell (visual only) | Phase D |
| `data-health.html` | `platform_status` panel (primary new content), data-management layout | Phase A, Phase D |
| `settings.html` | Widget-box sectioned form, Diagnostic Mode toggle | Phase A, Phase D |
| `login.html` | No change | — |

## 11. Phase-Wise Execution Roadmap

| Phase | Scope |
|---|---|
| **A — RMS/Freshness reliability** | Fix `generated_at` bug, `platform_status`, diagnostics, demo-data service, environment badge (from v2, unchanged, do first) |
| **B — Menu + rename** | `MENU_MODEL` relabel, `risk-takers.html` → `review-flags.html` + shim, link verification |
| **C — AI Copilot** | Formalize answer schema (6+2 types), `decision_path`, `hallucination_guard`, `SkillEdge.renderAssistantPanel()`, wire to Dashboard + Trainer Cockpit + KB metadata |
| **D — Visual/plugin adoption** | DataTables/Select2/SweetAlert/offcanvas/wizard-shell/radial-bar/radar per the page table above, in this order: `team.html` → `actions.html` → `trainer-intelligence.html` → `allocation-desk.html` → `certifications.html` → `capability-builder.html` → `review-flags.html` → `custom-course-match.html` → `data-health.html` → `settings.html` → `index.html` |
| **E — Backend hygiene** | Consolidate technology-adjacency duplication, delete/resolve `intelligence_engines` scaffolding |
| **F — QA + ZIP** | Full smoke test, 12-page browser pass, console check, new dated ZIP |

## 12. Exact Codex Implementation Order

1. **Phase A** (already scoped as a standalone first task at the end of v2 — execute that exact task first, unchanged).
2. **Phase B**: menu relabel + `risk-takers.html` rename/shim, in one pass, since Phase D's Review Flags work depends on the final filename.
3. **Phase C**: Copilot schema + shared panel — build against the *existing* payload fields first (classification, decision objects, custom_course_match) so it's usable even before Phase D's visual work lands; mount only on `index.html` and `trainer-intelligence.html` initially.
4. **Phase D**, one page at a time, in the dependency order given above — each page is an independent, revertible unit; do not batch multiple pages into one change.
5. **Phase E**, backend-only, no visible behavior change — safe to do anytime after Phase A, sequenced last only because it's lowest urgency.
6. **Phase F** last, always.

**First task to hand to Codex right now:** the Phase A task already defined at the end of `docs/MANAGER_REDESIGN_BLUEPRINT_V2_2026.md` — it has not been executed yet and everything above (the freshness banner, the environment badge, the Copilot's `serving_mode`/`freshness_note` fields) depends on it existing first.
