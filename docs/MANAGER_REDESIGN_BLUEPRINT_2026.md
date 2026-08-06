# SkillEdge / SkillNex Manager — Full Redesign Blueprint

Grounded in the actual current codebase (not the aspirational docs). Sources: `docs/CURRENT_PROJECT_REALITY_MAP.md`, `docs/SKILLEDGE_PRODUCT_CRITICAL_REVIEW_V2.md`, `docs/MANAGER_OS_ARCHITECTURE.md`, `docs/MANAGER_OS_PRODUCTION_STATUS.md`, `frontend/js/app.js`, `frontend/pages/*.html`, `backend/app.py`, `backend/services/*.py`, `backend/shared/*.py`.

This supersedes prior page-count assumptions: the app currently ships **14 pages under one shared nav** (`frontend/js/app.js:MENU_MODEL`), not the "7 core pages" the older Constitution doc proposed. `trainer-detail.html` is already a 24-line redirect shim into `trainer-intelligence.html` — that consolidation already happened. What has **not** happened is KPI discipline and HR-language cleanup, both of which are inconsistent across pages today.

---

## 0. Executive Summary

**What's actually good:** the unified endpoint (`GET /data/unified-manager-intelligence`), the manager-scoped session auth, the backend intelligence spine (`backend/shared/*_intelligence.py`), and the nav/layout injection pattern in `app.js` (one nav definition, not 14 copies). Keep all of this.

**What's actually broken:**
1. **Every page invents its own KPI array and its own client-side scoring/classification logic**, inline in a `<script>` tag inside the HTML file. `frontend/js/pages/` is empty — there is no separation between markup and page logic. Nine different pages independently decide what counts as "risk," "ready," or "blocked."
2. **The "Data Confidence" KPI tile and its entire render template are copy-pasted byte-for-byte across 8 files** (`index.html`, `team.html`, `allocation-desk.html`, `actions.html`, `capability-builder.html`, `certifications.html`, `risk-takers.html`, `custom-course-match.html`). Same for the "Dataset coverage (X/Y ready)" heading string.
3. **The HR-language cleanup is half-done.** `risk-takers.html` softened its page copy ("not only negative risk") but its own KPI card still says "High Risk Trainers" and its severity value is literally `'HR / Compliance'`. Backend field names (`hr_pos`, `hr_neg`, `risk_taker_score` in `backend/shared/scoring.py`) still carry the old framing all the way through the payload into the UI. `login.html`, `settings.html`, `custom-course-match.html`, `team.html`, and `index.html` all still render the literal string "HR" to a manager.
4. **`backend/intelligence_engines/*.py` are stub scaffolding**, not real logic — the real scoring lives scattered in `backend/shared/*_intelligence.py` and `backend/services/*.py`. This is fine architecturally but means "intelligence_engines" is a misleading directory name that should either become real or be removed from the mental model.
5. Every page KPI needs to pass this test: *does it change what a manager does next?* Several currently don't (see KPI Matrix, Section 5).

**The fix is not a rebuild.** It's: (a) centralize KPI + classification logic server-side so the 9 pages stop each computing their own "risk," (b) extract one shared `renderKpiStrip()` component instead of 8 copies, (c) finish the HR-terminology migration everywhere at once — UI strings, field names, and backend reason-strings together — not in patches, and (d) apply the per-page KPI relevance test below and cut what fails it.

---

## 1. Full Application Purpose

SkillEdge is a **manager decision cockpit** for Delivery/Resourcing Managers, not a reporting BI tool. Every screen must resolve into one of these manager actions: **assign**, **hold**, **coach**, **certify**, **escalate**, **verify**. If a KPI or panel doesn't lead toward one of those six verbs, it doesn't belong.

Composite roles the product plays:
- **Command center** — `index.html`: what needs my attention today.
- **Trainer cockpit** — `trainer-intelligence.html`: is this specific trainer safe to allocate, and why.
- **Allocation decision system** — `allocation-desk.html`: which trainer goes on which course, right now.
- **Capability planning system** — `capability-builder.html`: who should be grown into what.
- **Certification/readiness monitor** — `certifications.html`: what accreditation gaps block delivery or compliance.
- **Review/compliance flag tracker** — `risk-takers.html` (rename candidate, see Section 4): who needs manager review before being trusted with delivery, and why — never framed as an HR penalty score.
- **Action management system** — `actions.html`: the inbox of concrete next steps.
- **Course-match/backup engine** — `custom-course-match.html`: given a course outline, who can deliver, who's backup.
- **Data trust layer** — `data-health.html`: can I trust what the other 9 pages just told me.

**Ideal login-to-decision journey:**
Login → Dashboard shows 3–6 decisions that need action today → manager clicks a flagged trainer → lands on Trainer Intelligence cockpit with full evidence → manager either allocates (via Allocation Desk), logs an action (Action Center), or files a review note → manager returns to Dashboard, which reflects the new state. Total clicks from login to a logged decision: 3.

---

## 2. New Menu and Navigation Structure

The current nav (`frontend/js/app.js`, `MENU_MODEL` ~line 111) is close to right already. Recommended refinement — same six-item flat structure, renamed groups, no page moves (avoids breaking the already-working `BUILT_PAGES` gating):

| Group | Item | Page | Purpose | Verdict |
|---|---|---|---|---|
| Command | Dashboard | `index.html` | Today's decisions, attention triage | **Stay** — rebuild content per Section 3 |
| Command | *(new)* Today's Actions | fold into `actions.html`, add as Dashboard deep-link | Fast path to the inbox | **Add as dashboard section, not a new page** |
| Trainers | Trainer Intelligence | `trainer-intelligence.html` | Deep single-trainer cockpit | **Stay** |
| Trainers | Team Overview | `team.html` | Roster + filters | **Stay, de-duplicate KPI logic** |
| Trainers | *(retired)* Trainer Detail | `trainer-detail.html` | — | **Keep as redirect shim only** — do not re-link it; it already forwards to Trainer Intelligence |
| Delivery Planning | Allocation Desk | `allocation-desk.html` | Course-to-trainer assignment | **Stay** |
| Delivery Planning | Custom Course Match | `custom-course-match.html` | Outline-to-trainer + backup matching | **Stay**, move parsing server-side (see Section 6) |
| Capability | Capability Builder | `capability-builder.html` | Upskill pipeline | **Stay** |
| Capability | Certifications | `certifications.html` | Accreditation/cert readiness | **Stay** |
| Governance | Review Flags | `risk-takers.html` → rename to `review-flags.html` | Review/compliance/delivery-concern tracker | **Rename + reframe**, see Section 4 |
| Governance | Data Health | `data-health.html` | System trust | **Stay** |
| Governance | Settings | `settings.html` | Admin config | **Stay** |
| — | Login | `login.html` | Auth | **Stay, fix copy** (still says "HR flags") |

No "Skill Gaps" or "Backup Trainer Planning" as separate pages — those are sections inside Capability Builder and Allocation Desk/Custom Course Match respectively. Adding them as standalone pages would recreate exactly the fragmentation the Critical Review already flagged (10 architecture docs, dual pipelines) — don't repeat that mistake with pages.

---

## 3. Dashboard Redesign (`index.html`)

Per the Critical Review, `index.html` is already "close to enterprise-ready" — a Copilot-style insight strip, action inbox, readiness triage (Ready/Prep/Not Ready), 5 ApexCharts, risk-taker panel, timeline, data-health summary. The current KPI row (`renderKpis`, line ~585) is: Total Trainers, Ready Now, High Risk, Open Actions, Certification Gaps, Single Point Risks, Overloaded, Future Readiness, Data Confidence.

**Verdict per KPI:**

| KPI | Keep? | Reason |
|---|---|---|
| Total Trainers | Cut from KPI row | Context, not a decision. Move into the team-roster header instead. |
| Ready Now | Keep | Triggers "assign" workflow. |
| High Risk | Keep, relabel to "Review Required" | Business risk trigger — but rename to strip HR-penalty framing (see Section 4). |
| Open Actions | Keep | Direct link to Action Center. |
| Certification Gaps | Keep | Triggers "certify" workflow. |
| Single Point Risks | Keep | Real business/delivery risk (bench depth), not a vanity metric. |
| Overloaded | Keep | Operational health / reallocation trigger. |
| Future Readiness | Cut or merge into Capability panel | Doesn't map to a today-action; better as a Capability Builder teaser widget, not a dashboard KPI. |
| Data Confidence | Keep, but as one badge in the header, not a KPI tile | It's meta-information about trust, not a business metric — visually separate it from decision KPIs so managers don't average it in with real numbers. Also known bug: `exec.confidence` previously fell back to nonexistent keys (`overall_team_health_score`) and silently rendered 0% — verify this is fixed before shipping. |

**Final dashboard KPI row (6, not 9):** Ready Now · Review Required · Open Actions · Certification Gaps · Single Point Risks · Overloaded. Data Confidence moves to a persistent header badge (shown on every page, not just dashboard).

**Sections below the KPI row (already exist, keep, reorder):**
1. Decision Inbox (today's top 3–6 actions, pulled from `manager_action_df`, highest priority first)
2. Trainers Needing Attention (readiness triage: Ready / Prep / Not Ready columns — already built)
3. Allocation Pressure (courses with no ready trainer, open bench gaps)
4. Certification Blockers (from `certification_intelligence`)
5. Review Flags summary (count + link to Governance page — do not duplicate the detail table here)
6. Data Health footer strip (freshness timestamp + link to Data Health page)

Remove: the risk-taker panel duplicated wholesale on the dashboard — link to Review Flags/Capability instead of re-rendering the same cards.

---

## 4. Page-by-Page Redesign

### `team.html`
- **Current problem:** KPI row (Total Trainers, Direct Reportees, Ready Now, High Risk, Certification Gaps, Overloaded, Available, Data Confidence) duplicates half the dashboard's KPIs instead of being a roster tool. `riskLabel()` (line ~293) computes its own risk classification client-side and still literally emits `'HR flag'` as a filter option label.
- **New purpose:** roster + filter + compare, not a second dashboard.
- **Correct KPIs:** Total Trainers, Direct Reportees, Ready Now, Available — roster composition only. Drop High Risk / Certification Gaps / Overloaded from the KPI row (they belong on Dashboard/Governance/Certifications respectively) — keep them as **filter chips** on the roster table instead of top-line numbers.
- **Table:** trainer roster with columns — readiness state, certification status, current allocation, review-flag indicator (badge, not score), open action count. One-click drilldown to Trainer Intelligence.
- **Remove:** the `'HR flag'` filter label → rename to `'Review flag'`.

### `trainer-intelligence.html`
- Already the correct deep cockpit (confirmed: roster panel, KPI row, mini charts, left column, knowledge assistant, right column "Today's Actions," backup-trainer comparison modal). Keep this structure.
- **KPI row today:** Readiness, Alloc Fit, Utilization, Cert Health, Delivery Qlty, Risk Level, Pending Actions, Data Conf. These are all individual-trainer decision cards — correct pattern (global KPIs are wrong here; per-trainer cards are right).
- **Fix:** "Risk Level" and risk-card titles currently mix "HR / feedback risk" and "HR, compliance, or delivery-risk signal" in the same sentence (line ~742-749) — pick one vocabulary: **Delivery Concern** for quality/feedback issues, **Compliance Flag** for HR-sourced flags. Never blend them in one label.
- **Keep:** decision assistant, backup trainer options modal, action history.

### `trainer-detail.html`
- **Decision: keep as redirect shim, do not restore as a page.** It already forwards to `trainer-intelligence.html?email=...` for old bookmarks. This is the correct outcome — no work needed here beyond leaving it alone.

### `allocation-desk.html`
- **Current KPIs** (Courses Needing Allocation, Ready Now Matches, Backup Missing, Blocked Allocations, High Risk (Reviewable), Overloaded Trainers, Underused Capacity, Average Confidence) are almost entirely allocation-relevant already — this is one of the better-scoped pages.
- **Fix:** rename "High Risk (Reviewable)" → "Review Required" for terminology consistency. Cut "Underused Capacity" from the KPI row unless it directly feeds a reassignment action — otherwise fold into a capacity panel.
- **Keep:** open allocation needs, best-match panel, backup suggestions, blockers, manual assign/reject/review actions.

### `actions.html`
- **Current KPIs** (Total Actions, Open, Critical, High Priority, Blocked, Escalated, Data Quality, Data Confidence) are workload-only — correct pattern for an inbox page.
- **Fix:** "Total Actions" is close to noise (includes closed/historical) — replace with "Open" as the lead KPI, keep Critical/High Priority/Blocked/Escalated as secondary counts.
- **Keep:** inbox list, owner, due date, priority, linked trainer/course, close/reassign/escalate actions.

### `capability-builder.html`
- **Current KPIs** (Total Trainers Reviewed, Ready to Upgrade, Needs Coaching First, Risk-Taker Candidates, Certification Attention, Do Not Upgrade Yet, OEM Bench Risks, Future Skill Items, Data Confidence) — 9 cards is too many; several overlap with Certifications and Review Flags pages.
- **Fix:** keep Ready to Upgrade, Needs Coaching First, OEM Bench Risks, Future Skill Items. Cut "Risk-Taker Candidates" and "Certification Attention" from this page's KPI row — those belong to Review Flags and Certifications respectively; link to them instead of recomputing. Rename "Do Not Upgrade Yet" → neutral operational language, not a verdict-sounding label.
- **Keep:** skills-in-demand, trainer gaps, suggested upskilling, course coverage, upgrade timeline.

### `certifications.html`
- **Current KPIs** (Certified Trainers, Certification Gaps, Missing Critical Certs, Vendors Affected, Accreditation Coverage %, Roadmap Items, At-Risk/Expired, Data Confidence) are cert-focused and mostly correct.
- **Fix:** "Roadmap Items" only belongs here if it's cert-specific (not general capability roadmap items pulled in from Capability Builder — check for overlap). Keep the rest as-is.

### `risk-takers.html` → rename `review-flags.html`
- **This is the highest-priority terminology fix in the whole app.** The subagent audit found: page copy already says "Global Risk & Growth Watchlist... not only negative risk," but the KPI card is still hard-labeled "High Risk Trainers" and the severity value is literally the string `'HR / Compliance'`. This is exactly the "looks fixed, isn't fixed" problem the user flagged.
- **New purpose:** review/compliance flag tracker + growth/stretch-candidate discovery — these are two different concerns bolted into one page today (risk-taker/growth classification and HR/compliance flags share a page). Consider splitting into two clearly-labeled sections on one page rather than blending them into one KPI row: **Review Flags** (compliance/delivery-concern side) and **Growth Candidates** (stretch/upside side) — do not let one "risk score" represent both.
- **KPI fix:** "High Risk Trainers" → "Open Review Flags." "HR / Compliance" severity value → split into **Compliance Flag** (HR-sourced) and **Delivery Concern** (feedback/quality-sourced) as distinct badge types, never combined.
- **Keep:** growth candidates, single point risks, bench/OEM risk as separate, honestly-labeled KPIs — these are legitimate business-risk signals, just mislabeled today.
- **Remove:** any language implying a punitive HR score. Replace with: "This trainer has an open compliance flag requiring manager review before allocation" — actionable, neutral, evidence-linked.

### `custom-course-match.html`
- **Current KPIs** (Trainers Analysed, Best Now, Strong Alternatives, Backups, Prep Required, Blocked/Not Rec., Avg Match Confidence, Data Confidence) are appropriately matching-focused.
- **Fix:** same HR-language issue — variables `hr`, `hr_risk` and displayed string `"HR ${r.hrRisk}"`, blocked reason `"HR flag is active"` (lines ~339-349, 569) need renaming to `complianceFlag` / `"Compliance flag is active — manager review required"`.
- **Architectural fix (not KPI, but material):** the match/hard-gate logic (`hr>0||neg>=3` client-side) is computed in the browser. Per the Critical Review, `custom_course_match_df` is a backend placeholder returning `[]` — this page runs a shadow scoring engine in JS instead of consuming a backend-scored field. Move the match/gate scoring server-side (`custom_course_match_service.py` already exists — extend it) so the "why blocked" reason is backend-authoritative and consistent with Allocation Desk's blocker logic.

### `data-health.html`
- **Current KPIs** (Total Issues, Critical, Warnings, Missing, Timeouts, Affected) are correctly system/data-quality only — this page needs no KPI changes.
- **Keep as-is.** This is the one page where "system health" KPIs are exactly right because the whole page's job is trust, not business decisions.

### `settings.html`
- No KPI cards today (confirmed) — correct, settings pages shouldn't have KPIs.
- **Fix:** UI label "HR Record" and RMS-call display name `'Get HR Flags'` (key `hrIncidents`) should be renamed to "Compliance Record" / "Get Compliance Flags" for consistency, without touching the underlying RMS API key name (`hrIncidents` can stay as the technical key; only the manager-facing label changes).
- **Add:** refresh schedule control, knowledge-base index status, scoring-weight display (read-only unless there's a real admin use case — don't add editable scoring weights unless a workflow needs it).

### `login.html`
- **Fix:** copy at line ~51 says "...feedback, HR flags, course catalog..." — change to "...feedback, review flags, course catalog..." Otherwise keep simple, branded, unchanged.

---

## 5. KPI Rules Per Page (Matrix)

| Page | Allowed KPI type | Remove KPI type | Reason |
|---|---|---|---|
| Dashboard | Cross-team operational triage (ready/review/actions/certs/overload/bench-risk) | Roster counts (Total Trainers), redundant risk-panel duplication | Dashboard is triage, not a roster |
| Team | Roster composition (total/direct/ready/available) | Risk, cert-gap, overload counts as KPI tiles | Those belong to their owning pages; use as filters here, not KPIs |
| Trainer Intelligence | Per-trainer decision cards (readiness, fit, utilization, cert health, risk level, pending actions) | Any team-wide KPI | Single-trainer page; global numbers are meaningless here |
| Allocation Desk | Allocation-pressure and match-status counts | Underused-capacity unless tied to a reassignment action | Only allocation-actionable numbers belong |
| Custom Course Match | Match/backup/prep counts for the submitted course | — | Already correctly scoped |
| Actions | Workload counts (open/critical/high/blocked/escalated) | "Total Actions" as lead KPI | Total includes closed items — not actionable |
| Capability Builder | Upgrade-pipeline counts (ready-to-upgrade, coaching-first, bench-risk, future-skill items) | Risk-taker count, cert-attention count | Owned by Review Flags / Certifications — don't recompute |
| Certifications | Cert-readiness counts only | Generic "roadmap items" if not cert-specific | Avoid capability-roadmap bleed-through |
| Review Flags (was Risk-Takers) | Open compliance flags, delivery concerns, bench risk, growth candidates — each labeled by type | Blended "HR / Compliance" severity value; "High Risk Trainers" framing | Never merge compliance and delivery-quality into one score |
| Data Health | System/data-quality counts only | Any business KPI | This page's only job is trust |
| Settings | None | Any KPI | Configuration surface, not a decision surface |

---

## 6. API Planning

**Currently registered routes** (`backend/app.py`, confirmed by direct read):

| Method | Path | Backing |
|---|---|---|
| POST | `/auth/login` | `auth_service.create_session` + `build_or_load_intelligence` |
| GET | `/auth/session` | session lookup |
| GET | `/auth/logout` | `auth_service.delete_session_from_cookie` |
| GET | `/data/unified-manager-intelligence` | `build_or_load_intelligence` + `custom_course_match_service.build_custom_course_match_objects` |
| POST | `/rms/{api_name}` | `rms_service.call_api` |
| GET | `/healthz` | `auth_service.active_session_count` |
| POST | `/api/refresh/run` | `refresh_service.refresh_once(force=True)` |
| GET | `/api/refresh/status` | `refresh_service.status_payload` |
| GET | `/api/refresh/logs` | `refresh_service.get_logs` |
| GET | `/api/refresh/last-success` | derived from status_payload |
| GET | `/api/knowledge/search` | `_search_knowledge` |
| GET | `/api/knowledge/*` | `_knowledge_lookup` |
| GET | (all else) | `static_service.resolve_static_request` |

**Gap analysis against the user's proposed API groups:**

- **Dashboard APIs** (`/api/dashboard/summary`, `/decision-inbox`, `/attention-trainers`) — **do not exist as separate routes today.** All dashboard data currently comes from one call to `/data/unified-manager-intelligence` and is sliced client-side per page. **Recommendation: do not split these out.** The unified-endpoint pattern is a deliberate, already-validated architectural choice (single source of truth, one cache, one auth check). Splitting into per-widget endpoints would recreate the dual-pipeline problem the Critical Review already flagged as the #1 architectural risk. Instead, keep one endpoint, and **add typed sub-objects inside the existing unified payload** (e.g. `decision_inbox: [...]`, `attention_trainers: [...]`) that the frontend renders directly instead of computing client-side.
- **Trainer APIs** (`/api/trainers/{email}/intelligence`, `/actions`, `/allocations`, `/certifications`) — **not needed as separate REST routes**; same reasoning. The unified payload already carries per-trainer rows; what's missing is that the *classification* (readiness bucket, risk label, blocker reason) should be computed once server-side and shipped as a field, not recomputed in each page's JS.
- **Allocation APIs** (`/allocations/open`, `/match`, `/simulate`, `/backup-options`) — partially exists conceptually via `allocation_decision_service.py` and `allocation_intelligence.py`, but not as standalone endpoints — again, folded into the unified payload today. Keep it that way; add a `backup_options` array per trainer/course pair inside the payload if not already present (custom-course-match.html currently computes backups client-side).
- **Actions APIs** (`create`/`update`/`close`/`escalate`) — **this is a real, genuine gap.** `manager_action_df` is read-only today; there is no persistence for a manager closing/reassigning/escalating an action. This needs real endpoints: `POST /api/actions/{id}/close`, `POST /api/actions/{id}/escalate`, `POST /api/actions/{id}/reassign`, backed by a new lightweight action-state store (see Section 7).
- **Review Flag APIs** (`/review-flags/resolve`) — same genuine gap as Actions: there's no way today for a manager to acknowledge/resolve a compliance flag. Needs a real endpoint + persistence.
- **Certification APIs** — already served via unified payload's certification intelligence block; no separate endpoints needed.
- **Data Health APIs** — already exist and are correctly separated (`/api/refresh/*`, `/healthz`) — good pattern, don't change.
- **Knowledge APIs** — already exist (`/api/knowledge/search`, `/api/knowledge/*`) — keep.

**Bottom line on API planning:** the biggest real gap is not missing read endpoints — it's **missing write/mutation endpoints for actions and review-flag resolution.** Everything else should stay inside the unified payload, not be split into 20 new REST routes.

---

## 7. Backend Service Mapping

| File | Status | Action |
|---|---|---|
| `backend/shared/scoring.py` | Contains `risk_taker_score`, `hr_pos`, `hr_neg` field names | **Rename fields** to `growth_score` (or keep `risk_taker_score` as an internal name but never surface it raw — always ship a resolved label) and `compliance_pos`/`compliance_neg`. Do this as one atomic pass across scoring.py → payload → every consuming page, not piecemeal. |
| `backend/shared/allocation_intelligence.py` | Has both clean (`"Compliance Restricted"`) and old (`"Quality/HR risk"`) strings in the same file | **Refactor to one vocabulary** — finish what's already half-started here. |
| `backend/shared/delivery_intelligence.py` | Delivery scoring, no major issue found | Keep as-is |
| `backend/services/custom_course_match_service.py` | Exists but `custom_course_match_df` still returns placeholder per Critical Review | **Complete it** — move the client-side match/gate logic from `custom-course-match.html` here |
| `backend/services/decision_objects.py` | Shared decision-object builders, mixes "HR negative incident"/"compliance"/"quality risk" language | **Standardize terminology pass** |
| `backend/intelligence_engines/*.py` (6 files) | **All are stub scaffolding/docstring placeholders** — not the real logic path | Either (a) delete this directory and stop referencing it in docs since it's misleading, or (b) actually migrate the real logic out of `backend/shared/*_intelligence.py` into these files if the separation is wanted long-term. Don't leave it as a decoy. |
| `backend/services/trainer_fetch_service.py` | Uses `hr = safe("HR Incident"...)`, key `"hr"` | Rename internal key to `compliance_signal` when the terminology pass happens |
| `backend/api/config.py` | `hrIncidents` role documented as "Get HR Incident Positive Negative" | Keep the RMS API key name (it's the actual upstream API name) but change the **role/description string** shown anywhere in UI or logs to "Compliance/Review Incident Signal" |

**New services actually needed** (only two, not eight — most of the user's proposed 8 services duplicate what the unified payload already does):
- `action_state_service.py` — persistence for close/escalate/reassign on `manager_action_df` items (the one genuine gap in Section 6).
- `review_flag_service.py` — persistence for acknowledging/resolving a compliance/delivery-concern flag, plus the terminology-clean classification (Compliance Flag vs Delivery Concern) that today lives ad hoc in `risk-takers.html` and `trainer_fetch_service.py`.

Do **not** build separate `dashboard_service.py`, `trainer_service.py`, `certification_service.py`, `capability_service.py`, `refresh_service.py` (already exists), `knowledge_base_service.py` (already exists) — these would fragment the single unified-payload architecture that the Critical Review already identified as the project's biggest strength.

---

## 8. UI Design System

**Terminology — use everywhere, no exceptions:**
- Review Flag / Compliance Flag (HR-sourced signal requiring manager review)
- Delivery Concern (feedback/quality-sourced signal)
- Readiness Blocker
- Certification Blocker
- Manager Action
- Assignability
- Course Match Confidence

**Terminology — remove everywhere, including internal variable names where feasible without breaking API contracts:**
- "HR Risk" / "HR risk" / "HR Hold" / "HR flag" as a *manager-facing* label (the upstream RMS API name `hrIncidents` can stay as a technical key)
- Bare "risk score" language not qualified as delivery/compliance/business risk
- Duplicate dashboard cards (the 8-file "Data Confidence" copy-paste)
- Any KPI that doesn't map to assign/hold/coach/certify/escalate/verify

**Component discipline (already mostly followed per SeanTheme mapping in `PAGE_DESIGN_MAP.md` — keep it, just enforce it):**
- KPI cards → one shared `renderKpiStrip()` helper in `app.js`, not 8 copy-pasted implementations
- Inbox/action lists → `email_inbox.html` pattern
- Evidence tables → collapsed by default, `table_manage_combine.html`
- Charts → ApexCharts only, verified data only
- Modals → detail inspection only, never primary navigation
- Badges → one shared badge-language module (Compliance Flag = amber, Delivery Concern = orange, Blocker = red, Ready = green) applied identically across all 14 pages
- Data Confidence → persistent header badge, not a per-page KPI tile

---

## 9. Workflow Design

**Workflow 1 — Manager opens dashboard:** Dashboard → sees Decision Inbox → clicks flagged trainer → Trainer Intelligence → reviews blocker/evidence → logs action (needs `POST /api/actions` — see gap in Section 6) → returns to Dashboard, inbox count decrements.

**Workflow 2 — Allocation needed:** Allocation Desk → selects course → sees best matches + backups (already built) → checks blockers (rename to Compliance/Delivery Concern language) → assigns or requests upskilling (link to Capability Builder).

**Workflow 3 — Trainer needs improvement:** Trainer Intelligence → sees gap → creates action → link to Capability Builder upgrade pipeline for that trainer.

**Workflow 4 — Certification blocker:** Certifications → sees missing cert → assigns action with due date → action appears in Action Center.

**Workflow 5 — Review flag:** Review Flags (renamed) → checks evidence (feedback detail, compliance record) → manager logs a note and resolves or escalates (needs `review_flag_service.py` — genuine backend gap).

Workflows 1, 4 partially blocked today by the missing action/flag-persistence endpoints (Section 6) — this is the actual highest-value backend work, above any new page.

---

## 10. Current Issues Found (file-level)

- **`frontend/js/pages/` is empty.** All page logic is inline `<script>` in each HTML file (600–1500+ lines each). No separation of markup and logic anywhere.
- **Duplicated KPI-strip markup**, identical template literal, in: `index.html`, `team.html`, `allocation-desk.html`, `actions.html`, `capability-builder.html`, `certifications.html`, `risk-takers.html`, `custom-course-match.html`.
- **Duplicated "Dataset coverage (X/Y ready)" heading string** verbatim across the same 8 files.
- **Wrong/mixed terminology:**
  - `frontend/pages/team.html` — `riskLabel()` and filter option literally `'HR flag'`
  - `frontend/pages/settings.html` — "HR Record" label, `'Get HR Flags'` RMS display name
  - `frontend/pages/custom-course-match.html` — `hr`/`hr_risk` vars, `"HR ${r.hrRisk}"`, `"HR flag is active"`
  - `frontend/pages/login.html` — "...feedback, HR flags, course catalog..."
  - `frontend/pages/index.html` — bucket literal `'Feedback/HR'`
  - `frontend/pages/actions.html`, `risk-takers.html` — string-matching on `'hr'` token for tile classification
  - `backend/services/trainer_fetch_service.py`, `backend/api/config.py`, `backend/shared/scoring.py`, `backend/shared/allocation_intelligence.py`, `backend/shared/delivery_intelligence.py`, `backend/shared/explainability.py`, `backend/shared/normalizers.py` — all still carry `hr`/`HR` field names or reason strings
  - `frontend/pages/risk-takers.html` — copy softened but KPI ("High Risk Trainers") and severity value (`'HR / Compliance'`) not updated — the half-done migration the user flagged
  - `frontend/pages/trainer-intelligence.html` — mixes "HR / feedback risk" and "HR, compliance, or delivery-risk signal" in the same card
- **Client-side scoring duplication:** every page independently computes bucket/risk/severity classification in JS instead of consuming a backend-classified field — confirmed on `team.html`, `custom-course-match.html`, `risk-takers.html`, `capability-builder.html`, `certifications.html`, `allocation-desk.html`, `actions.html`, `index.html`, `trainer-intelligence.html`.
- **`custom_course_match_df` is still a backend placeholder** returning `[]` — the flagship "custom course match" feature runs entirely on a client-side heuristic (per Critical Review, confirmed still true).
- **`backend/intelligence_engines/*.py`** — all six files are stub/docstring scaffolding, not real logic. Misleading directory name relative to actual logic location (`backend/shared/*_intelligence.py`).
- **No empty/loading/error state audit was in scope for this pass** — flag for a follow-up UI QA pass before shipping the terminology + KPI changes.
- **`trainer-detail.html`** — confirmed already correctly reduced to a 24-line redirect shim; no action needed, contrary to any assumption it needs merging.

---

## 11. Redesign Implementation Plan

### Phase 1 — Terminology + KPI Cleanup (do this first, it's the actual ask)
- Files: `team.html`, `settings.html`, `custom-course-match.html`, `login.html`, `index.html`, `actions.html`, `risk-takers.html` (→ rename `review-flags.html`), `trainer-intelligence.html`
- Change: every manager-facing "HR" string → "Compliance Flag" or "Delivery Concern" per the vocabulary in Section 8. Update KPI labels per Section 5's matrix (cut Total Trainers from Dashboard KPI row, "High Risk Trainers" → "Open Review Flags," etc.)
- Validation: grep for `/HR\b/i` across `frontend/` after the pass — zero manager-facing hits should remain (RMS technical key `hrIncidents` is the only allowed exception, and it must never render to a manager).
- Expected result: consistent vocabulary across all 14 pages; no page shows a "risk score" without qualifying whether it's compliance, delivery, or bench risk.

### Phase 2 — Shared KPI Component
- Files: `frontend/js/app.js` (add `renderKpiStrip(kpis)` helper + shared "Dataset coverage" component), then update all 8 duplicated pages to call it instead of inlining the template.
- Validation: visual diff on all 8 pages — identical rendering, single source for the markup.

### Phase 3 — Server-Side Classification
- Files: `backend/shared/scoring.py`, `backend/shared/allocation_intelligence.py`, relevant page `<script>` blocks
- Change: move bucket/risk/severity classification into the backend payload as a resolved field (`review_flag_type: "compliance"|"delivery_concern"|null`); pages read the field instead of recomputing.
- Validation: `tests/smoke_test.py` payload-contract check + manual diff of classifications before/after on a known trainer.

### Phase 4 — Action + Review-Flag Persistence (the real backend gap)
- New: `backend/services/action_state_service.py`, `backend/services/review_flag_service.py`
- New routes: `POST /api/actions/{id}/close`, `/escalate`, `/reassign`; `POST /api/review-flags/{id}/resolve`
- Files touched: `backend/app.py` (route registration), `actions.html`, `review-flags.html` (wire buttons to real endpoints instead of no-ops)
- Validation: end-to-end — close an action, confirm dashboard count decrements on reload.

### Phase 5 — Custom Course Match Backend Completion
- Files: `backend/services/custom_course_match_service.py`, `custom-course-match.html`
- Change: move the client-side match/gate scoring server-side; populate `custom_course_match_df` for real.
- Validation: compare old client-side ranking vs new server-side ranking on 3 sample courses — should match or improve, not regress.

### Phase 6 — QA
- Smoke tests (`tests/smoke_test.py`), browser verification of all 14 pages, console error check, empty/loading/error state check (not previously audited), nav active-state check across renamed `review-flags.html`.

---

## 12. Priority Order and First Task

1. **Phase 1 (terminology + KPI cleanup)** — lowest risk, highest visible impact, directly answers the user's stated problem, no backend changes required.
2. **Phase 2 (shared KPI component)** — mechanical, safe, removes 8x duplication.
3. **Phase 4 (action/review-flag persistence)** — the one genuine backend gap that blocks real workflows.
4. **Phase 3 (server-side classification)** — larger, touches scoring; do after terminology is stable so you're not renaming twice.
5. **Phase 5 (custom course match backend)** — highest effort, lowest urgency relative to the KPI-cleanup ask.

**Best first task to hand to Codex/Claude:**
> "In `frontend/pages/risk-takers.html`, rename the page concept from risk-score to review-flag: (1) change the KPI label 'High Risk Trainers' to 'Open Review Flags', (2) split the severity value `'HR / Compliance'` into two distinct badge types `'Compliance Flag'` (HR-sourced) and `'Delivery Concern'` (feedback-sourced) — never blended into one value, (3) update the impact copy to remove any HR-penalty framing per the constitution in `docs/MANAGER_REDESIGN_BLUEPRINT_2026.md` Section 4/8. Do the same terminology fix in `team.html` (`riskLabel()` and the `'HR flag'` filter option), `custom-course-match.html` (`hr`/`hr_risk` vars and the `'HR flag is active'` blocked-reason string), `settings.html` ('HR Record' label), `login.html` (footer copy), and `index.html` (the `'Feedback/HR'` bucket literal). Do not touch backend RMS API key names (`hrIncidents` stays as the technical key) — only manager-facing labels and internal classification values change. Verify with a project-wide grep for `HR` in `frontend/` afterward — zero manager-visible hits should remain."

This is scoped, file-specific, and directly executable without further clarification — the correct starting point.
