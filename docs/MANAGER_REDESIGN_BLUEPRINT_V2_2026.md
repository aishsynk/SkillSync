# SkillEdge / SkillNex Manager — Next Upgrade Plan (v2)

Builds on `docs/MANAGER_REDESIGN_BLUEPRINT_2026.md` (Phases 1–6, complete and QA-verified). This plan covers the next round: fixing the two open limitations, an AI-accuracy layer, a menu/IA restructure, and a page-by-page visual upgrade.

**Grounding note before anything else:** I checked `frontend/assets/` directly. The literal SeanTheme demo files named in the request (`ai_chat.html`, `email_inbox.html`, `index_v3.html`, `extra_profile.html`, `login_v3.html`, `form_wizards.html`, etc.) **are not present in this repo** — only the compiled `css/`, `js/`, `img/`, and `plugins/` asset bundle is. So "use `ai_chat.html`'s pattern" means *match that theme's known layout convention using the plugins we actually have*, not "open and copy this file." I confirmed which plugins genuinely exist locally (so nothing below requires a new download):

```
datatables.net(-bs5) + buttons/colreorder/fixedcolumns/fixedheader/keytable/responsive/rowreorder/scroller/select
select2, sweetalert, dropzone, intro.js, @fullcalendar, apexcharts, bootstrap-daterangepicker
```
All currently unused by any of the 14 live pages (confirmed by grep) — this is the same finding the original Critical Review made, still true today.

---

## 1. Fix Current Limitations First

### 1.1 RMS Token Endpoint Outage — root cause, not a guess

I re-tested this live, directly against the vendor, independent of our code:

```
POST https://api.koenig-solutions.com/api/Kites/Operator/GetToken   → 404 (IIS default "File or directory not found" HTML page)
POST https://api.koenig-solutions.com/api/Kites/Operator/common     → 404 (same IIS default page)
GET  https://api.koenig-solutions.com/                              → 200 (domain, DNS, TLS all fine)
```
The response is a **generic Microsoft-IIS/10.0 static 404 page** (`Content-Type: text/html`, `X-Powered-By: ASP.NET`), not a JSON application error. That specific signature means IIS itself cannot find the route at the web-server level — this is not a bad credential, not a bad body, not a timeout. **Both `GetToken` and `common` are gone/moved on the vendor's server.** This has been reproducible continuously for the last ~2 hours of testing (confirmed via `runtime/logs/skilledge.log`, `1783358343` / 2026-07-06 18:19 being the latest fallback event) and is outside anything fixable in this codebase. The correct action is to escalate to whoever owns `api.koenig-solutions.com` with this exact evidence (path, method, IIS signature, timestamp) — not to "fix" it in Python.

**A real bug I found while diagnosing this (not the outage itself):** `build_or_load_intelligence()` in `backend/app.py` does this on the stale-cache-fallback path:
```python
if path.exists():
    cached = read_cache(path)
    cached["generated_at"] = int(time.time())   # <-- rewrites the timestamp to NOW
    write_cache(path, cached)
    return cached, True
```
This **overwrites `generated_at` with the current time even though the underlying data never actually refreshed** — so every fallback silently makes stale data look freshly built. Any freshness banner built on top of `generated_at` today would lie. This must be fixed as part of Phase A (see below) — it's the actual blocking defect, not the vendor outage itself.

**What to build (Phase A):**
1. **Stop lying about freshness.** Add a distinct `last_live_build_at` field, set only inside the real success path of `build_or_load_intelligence` (never inside the fallback branch). Keep `generated_at` as "when this cache file was last written" (fine as-is) but never let the manager-facing freshness badge read it — it must read `last_live_build_at`.
2. **Capture real diagnostics on token/API failure.** In `backend/api/client.py`, `_post()`/`_get_token()` currently only propagate `urllib.error.HTTPError` with no response body captured. Add: on non-200, read and store (truncated, secret-redacted) response body + status + the exact path attempted, so `data_health_df` can show *"GetToken → 404 (IIS: file/directory not found) at 18:19:03, last live success 2026-07-05 09:12"* instead of a generic "API failed."
3. **Add a `platform_status` block to the unified payload:** `{"rms_reachable": bool, "last_live_build_at": iso, "last_attempt_at": iso, "failing_endpoint": str, "failure_signature": str, "next_retry_at": iso, "serving_mode": "live"|"cached"|"demo"}`. This is the single source every page's freshness banner reads from — no page recomputes this.
4. **Dashboard + Data Health banner:** "Using cached data since 2026-07-05 09:12 — GetToken endpoint returned 404 (Not Found) at the RMS server. Retrying automatically every 15 min (`_run_background_refresh` already exists — reuse it, don't add a second poller)." Banner must be visually distinct (SeanTheme `alert-warning`/`alert-danger` pattern already used elsewhere, e.g. data-health.html's existing alert blocks) — not a silent console-only warning.
5. **AI/dashboard answers must not claim freshness they don't have** — this is directly enforced by having every answer read `platform_status.serving_mode` and prepend a caveat when `serving_mode !== "live"` (see Section 2).

**Files to touch:** `backend/app.py` (`build_or_load_intelligence`), `backend/api/client.py` (`_post`, `_get_token`, `_call` — capture body on failure), `backend/intelligence.py` (attach `platform_status` to payload), `frontend/pages/index.html` + `frontend/pages/data-health.html` (render the banner). No changes needed to `runtime/refresh` — `refresh_service.py`'s poller already exists and is reused, not duplicated.

### 1.2 Two-Trainer Scope Limitation — demo mode, not fake production data

Add a clearly-labeled, clearly-isolated **Demo Mode**, never blended with production:
- New `backend/services/demo_data_service.py` producing a synthetic `trainer_operations_df`-shaped payload (8–12 trainers) covering every scenario the current 2-trainer scope can't: a certification-blocked trainer, an overloaded trainer, a compliance-flagged trainer, a delivery-concern trainer, a clean "ready now" trainer, a growth candidate — built from the *same* field shapes `intelligence.py` already emits (so `classification`, decision objects, etc. all still work unmodified against it).
- Gate it behind an explicit `?demo=1` flag or a `Settings → Diagnostic Mode` toggle, session-scoped, never persisted to `backend/data/*.json` (so demo actions/flags can never leak into real manager state).
- **Environment badge** (header, every page, next to the existing confidence badge): `Live` (green) / `Cached` (amber, driven by `platform_status.serving_mode`) / `Demo` (purple, unmistakably different color+label so nobody mistakes it for real data).
- This directly unblocks validating backup-trainer suggestions, certification blockers, and review flags with richer data — exactly what Phase 5/6 flagged as untestable with only 2 real trainers.

**Files to add:** `backend/services/demo_data_service.py`. **Files to touch:** `backend/app.py` (route demo flag through to `build_or_load_intelligence`), `frontend/js/app.js` (environment badge in the shared header).

---

## 2. AI Answer Accuracy Plan

**What exists today:** `trainer-intelligence.html` already has a deterministic, rule-based single-trainer Q&A (`answerTrainerQuestion()`, `renderKnowledgeAssistant()`) — 15 canned question types (`can_assign_now`, `biggest_risk`, `missing_certs`, etc.), each already returning `{answer, evidence, source, confidence, decisionVersion}`. **This is the right foundation — extend it, don't replace it with something new.** It already does most of what's being asked; it just isn't formalized as a schema, isn't available team-wide (only per-trainer), and doesn't carry a freshness/serving-mode flag.

**Answer schema (formalize what already exists + add freshness):**
```json
{
  "answer_type": "trainer_summary | allocation_recommendation | certification_blocker | review_flag | capability_gap | data_health",
  "answer": "string, plain-English",
  "evidence": [{"dataset": "trainer_operations_df", "field": "classification.primary_blocker", "value": "..."}],
  "source_datasets": ["trainer_decision_objects", "..."],
  "confidence": 0-100,
  "confidence_label": "High|Medium|Low",
  "serving_mode": "live|cached|demo",
  "freshness_note": "Cached since 2026-07-05 09:12 — RMS GetToken unavailable" ,
  "fallback": false
}
```
**Hard rules (enforced in code, not just documentation):**
- Every answer function must read `classification`, `trainer_decision_objects`, `custom_course_match_df`/`custom_course_match_objects`, or `data_health_df` — never re-derive a verdict from raw fields the way pre-Phase-3 code did. This is a direct continuation of the Phase 3 rule.
- If `platform_status.serving_mode !== "live"`, every answer prepends the `freshness_note` — no exceptions.
- If the referenced trainer/course/action isn't found in the current payload, return a fixed `"cannot verify — not present in current scope"` answer instead of guessing — never fabricate a name, score, or certification.
- Team-wide questions (today only trainer-scoped) need a manager-scope version: reuse the exact same `answerTrainerQuestion` evidence pattern but iterate `TRAINERS`/`manager_action_objects` and aggregate — do not build a second parallel answer engine.

**New UI:** a **Command Center chat panel** (Ask SkillNex AI) using the same evidence-card visual pattern already proven in `trainer-intelligence.html`'s assistant (bot bubble + suggested questions + evidence list) — extended to be reachable from the Dashboard, not just per-trainer. This is additive UI, not a rebuild: lift the existing `renderKnowledgeAssistant`/`answerTrainerQuestion` pattern into a shared `frontend/js/app.js` helper (`SkillEdge.renderAssistantPanel(...)`, matching the Phase 2 precedent of centralizing repeated UI into `app.js`) so Dashboard and Trainer Cockpit both call the same code instead of forking it.

**Files:** `backend/shared/` — no backend answer-generation exists yet (it's all frontend JS reading the payload); if true manager-wide aggregation logic grows complex, add `backend/shared/assistant_answers.py` mirroring `classification.py`'s pattern (pure functions, no new API calls). `frontend/js/app.js` (shared assistant renderer), `frontend/pages/trainer-intelligence.html` (migrate to shared renderer), `frontend/pages/index.html` (new panel).

---

## 3. Menu Restructure

Current `MENU_MODEL` in `frontend/js/app.js` (6 groups, confirmed via direct read) maps cleanly onto the requested structure — **rename groups/labels, do not move or duplicate pages**:

| New group | Item | Page | Change |
|---|---|---|---|
| Command Center | Dashboard | `index.html` | same |
| Command Center | Decision Inbox / Today's Actions | `actions.html` | **relabel only** — one page serves both listed menu rows; do not create a second "Today's Actions" page |
| Trainer Intelligence | Team Overview | `team.html` | same |
| Trainer Intelligence | Trainer Cockpit | `trainer-intelligence.html` | **relabel** (was "Trainer Command Center") |
| Trainer Intelligence | Review Flags | `risk-takers.html` | **relabel** (was "Growth & Risk") |
| Delivery Planning | Allocation Desk | `allocation-desk.html` | same |
| Delivery Planning | Course Match | `custom-course-match.html` | **relabel** (was "Custom Course Match") |
| Delivery Planning | Backup Planning | *section inside `allocation-desk.html`* | **no new page** — backups already render there; do not split out |
| Capability Growth | Capability Builder | `capability-builder.html` | same |
| Capability Growth | Certifications | `certifications.html` | same |
| Capability Growth | Skill Gaps | *section inside `capability-builder.html`* | **no new page** — already a panel there |
| AI & Knowledge | Ask SkillNex AI | *new panel on `index.html`, deep-linkable* | see Section 2 — no standalone page needed yet |
| AI & Knowledge | Knowledge Base | *existing `/api/knowledge/search` surfaced in Data Health or a new thin page only if the assistant panel proves insufficient* | build the panel first; add a page only if users actually need raw KB search |
| AI & Knowledge | Refresh Status | *section inside `data-health.html`* (already shows refresh info) | **no new page** |
| System | Data Health | `data-health.html` | same |
| System | Settings | `settings.html` | same |
| System | Logout | (existing) | same |

This keeps the page count at 14 (no new pages except the optional Knowledge Base page, deferred). Update `MENU_MODEL` labels/grouping in `frontend/js/app.js` only — `BUILT_PAGES` set and hrefs stay identical, so no link breaks.

---

## 4–5. Page-by-Page Redesign Spec

For every page: purpose, SeanTheme-pattern-to-emulate (using our actual plugins), KPI cards (**must still pass the assign/hold/coach/certify/escalate/verify test from Phase 1** — no new page gets to reintroduce vanity KPIs), what's new, what's removed/kept.

### `index.html` — Command Center
- **Emulate:** widget/KPI-row pattern (already used) + a new **AI insight strip** (evidence-carrying, per Section 2) + **freshness banner** (Section 1.1).
- **KPI cards (unchanged from Phase 1–3):** Ready Now · Review Required · Open Actions · Certification Gaps · Single Point Risks · Overloaded.
- **New:** environment badge (Live/Cached/Demo) in header; Ask SkillNex AI panel below the decision inbox.
- **Remove:** nothing new to remove — Phase 1–3 already cleaned this page.
- **API:** unified payload + new `platform_status`.

### `team.html` — Team Overview
- **Emulate:** DataTables (`datatables.net-bs5` + responsive + fixedheader, all present locally) instead of the current hand-built table — genuine upgrade (sort/filter/export/pagination free). Select2 for the filter dropdowns instead of plain `<select>`.
- **KPIs unchanged:** Total Trainers · Direct Reportees · Ready Now · Available.
- **New:** profile-preview slide-over on row click (reuse the profile-hero markup style already in `trainer-intelligence.html` rather than inventing a new layout).
- **Remove:** the current hand-rolled `<table>` markup once DataTables is wired (keep the data logic, replace only the render/interaction layer).

### `trainer-intelligence.html` — Trainer Cockpit
- **Emulate:** tabs/accordion pattern for the left/right column sections (currently long stacked panels) — use Bootstrap's native tab component already loaded by the theme, not a new library.
- **Keep:** per-trainer KPI cards, decision assistant (now formalized per Section 2), backup comparison modal, action lifecycle buttons (Phase 4).
- **New:** the assistant panel becomes `SkillEdge.renderAssistantPanel()` (shared with Dashboard).
- **Remove:** none — this page was already assessed as close to correct in every prior phase.

### `allocation-desk.html` — Allocation Desk
- **Emulate:** DataTables for the course/candidate list (same plugin already used elsewhere in this plan) instead of the current manual rows.
- **KPIs unchanged** (8 tiles from Phase 2, untouched — out of Phase 1 terminology scope, still valid here).
- **New:** SweetAlert (`plugins/sweetalert`, already present) for assign/reject confirmations instead of native `confirm()` — matches "stronger SeanTheme look."

### `actions.html` — Decision Inbox / Actions
- **Emulate:** inbox-list-plus-detail-drawer pattern (email-client-style), reusing the *existing* modal-based detail view — convert the modal into a right-side offcanvas drawer (Bootstrap offcanvas, already bundled) for a true inbox feel, since a literal `email_inbox.html`/`email_detail.html` file isn't available to copy.
- **Keep:** Close/Escalate/Reassign buttons (Phase 4), lifecycle badges.
- **KPIs unchanged:** Open · Critical · High Priority · Blocked · Escalated · Data Quality.
- **New:** SweetAlert confirmation before Close/Escalate/Reassign (replaces the current `window.prompt()`, which Phase 4's own QA flagged as unfriendly to automated testing and to real users alike — a genuine UX upgrade, not scope creep, since Phase 4 explicitly called this out as a known limitation).

### `capability-builder.html` — Capability Builder
- **Emulate:** existing ApexCharts panels, add DataTables for the trainer list.
- **KPIs:** per Phase 3 blueprint, this page's tile set was flagged as needing trimming (Risk-Taker Candidates / Data Confidence overlap with Review Flags / header badge) but explicitly **out of scope** for Phases 1–5 since it wasn't in any approved edit list. **This plan proposes finally applying that trim now**, consistent with the original Phase 1 KPI-relevance test: drop "Risk-Taker Candidates" (owned by Review Flags) and "Data Confidence" (owned by header badge), keep Ready to Upgrade / Needs Coaching First / OEM Bench Risks / Future Skill Items / Certification Attention.

### `certifications.html` — Certifications
- **Emulate:** DataTables with fixed header (`datatables.net-fixedheader`) for the long cert-gap list; Select2 for vendor/status filters.
- **KPIs unchanged.**

### `risk-takers.html` → **rename file to `review-flags.html`** (finally executing the Phase 1 blueprint's deferred rename — Phase 1 only relabeled the *page content*, not the *file/URL*, to avoid breaking links mid-migration; now that the menu is being restructured anyway is the right time)
- **Emulate:** existing `extra_data_management.html`-style governance layout (issue list + evidence + resolve workflow) — already close to this via Phase 4's acknowledge/resolve/escalate modal.
- **Keep:** Compliance Flag / Delivery Concern terminology, lifecycle buttons.
- **Migration:** add `frontend/deprecated/risk-takers.html` as a redirect shim (same pattern as `trainer-detail.html`), update `MENU_MODEL` href, grep-check no other page links to the old filename before cutting over.

### `custom-course-match.html` — Course Match
- **Emulate:** form-wizard step pattern (paste outline → extract → match → confirm) using Bootstrap's existing nav-pills/tab machinery for step state — no new wizard plugin needed since `form_wizards.html` itself isn't in our asset bundle; a native Bootstrap tab-based wizard is the honest equivalent.
- **Keep everything from Phase 5** (backend-first scoring, compliance_flag/delivery_concern/backup_trainers fields) — this page's backend is done; only the *visual step flow* changes.
- **New:** Dropzone (`plugins/dropzone`, present) for actual file upload — this finally closes the "Upload parser: Not implemented" honest-limitation banner from Phase 5, IF a real parsing backend is built (flagged as a distinct, larger future task — do not fake file parsing to look done).

### `data-health.html` — Data Health / Trust Center
- **New (Section 1.1):** `platform_status` panel — RMS reachability, failing endpoint, last live success, next retry. This is the page's new primary purpose addition.
- **KPIs unchanged** (system/data-quality only, already correct per Phase 1/2).

### `settings.html` — Settings
- **New:** Diagnostic Mode toggle (demo dataset), refresh cadence display (read-only, already fetched from `/api/refresh/status`).
- **Keep:** Compliance Record terminology (Phase 1), Intelligence Score Weights section.

### `login.html` — Login
- **Keep exactly as Phase 1 left it** ("review flags" copy) — no redesign needed, already simple and correct. Add environment-mode awareness only *after* login (per the original request: "cached/demo/live state after login only") — do not show any status before authentication.

---

## 6. Implementation Plan (phase order)

| Phase | Scope | Files | Depends on |
|---|---|---|---|
| **A — Freshness reliability** | Fix `generated_at` overwrite bug, add `platform_status`, capture real failure diagnostics, freshness banners on Dashboard + Data Health, demo-data service + environment badge | `backend/app.py`, `backend/api/client.py`, `backend/intelligence.py`, `backend/services/demo_data_service.py` (new), `frontend/js/app.js`, `frontend/pages/index.html`, `frontend/pages/data-health.html` | none — do this first, everything else depends on trustworthy freshness signals |
| **B — Menu + layout shell** | Relabel `MENU_MODEL`, rename `risk-takers.html`→`review-flags.html` with redirect shim, verify every link | `frontend/js/app.js`, `frontend/pages/review-flags.html` (renamed), `frontend/deprecated/risk-takers.html` (new shim) | A (badge needs to exist in the shared header first) |
| **C — AI accuracy layer** | Formalize answer schema, `SkillEdge.renderAssistantPanel()`, wire freshness/serving_mode into every answer, add Dashboard assistant panel | `frontend/js/app.js`, `frontend/pages/trainer-intelligence.html`, `frontend/pages/index.html`, optionally `backend/shared/assistant_answers.py` | A (needs `platform_status`) |
| **D — Page visual upgrade** | DataTables/Select2/SweetAlert/offcanvas adoption per page, capability-builder KPI trim | one page at a time, in this order: `team.html` → `actions.html` → `allocation-desk.html` → `certifications.html` → `capability-builder.html` → `custom-course-match.html` (wizard steps only) | B, C |
| **E — QA + ZIP** | Full smoke test, 12-page browser pass, console check, screenshot verification, new dated ZIP | none (validation only) | A–D |

---

## 7. Final Output Summary

- **RMS outage diagnosis:** confirmed IIS-level 404 on both `GetToken` and `common` endpoints, domain root healthy — vendor-side routing/deployment issue, needs escalation to Koenig's API team with the evidence above, not an app-side fix. The one real app-side bug found: `generated_at` gets overwritten on every fallback, hiding true staleness — fix in Phase A.
- **Two-trainer limitation fix:** isolated demo-data service + session-scoped toggle + Live/Cached/Demo header badge, never touching `backend/data/*.json`.
- **New menu:** table in Section 3 — 6 groups, same 14 pages (+1 possible future Knowledge Base page, deferred), only labels/grouping change.
- **Page-by-page spec:** Section 4–5, per page.
- **SeanTheme component map:** real plugins only (DataTables family, Select2, SweetAlert, Dropzone, Intro.js, offcanvas, tabs) — the specific demo HTML files named in the request don't exist in this repo and cannot be literally referenced.
- **AI accuracy plan:** formalize the existing `answerTrainerQuestion`/`renderKnowledgeAssistant` engine (don't rebuild), add the answer schema, freshness/serving-mode enforcement, and a shared Dashboard-reachable panel.
- **API/data gaps:** `platform_status` block (new), demo-data endpoint routing (new), no new external RMS APIs needed or possible right now given the outage.
- **Exact files to change:** listed per phase in Section 6.
- **Implementation order:** A → B → C → D → E, as tabled above.

**First task to hand to Codex:**
> "Implement Phase A only. In `backend/app.py`'s `build_or_load_intelligence`, stop overwriting `generated_at` on the stale-cache-fallback path; add a `last_live_build_at` field set only on genuine success. In `backend/api/client.py`, capture and log (secret-redacted, truncated to 300 chars) the response body and exact path on any non-200 `_post`/`_get_token` failure. Add a `platform_status` dict to the unified payload in `backend/intelligence.py` with fields `rms_reachable`, `last_live_build_at`, `last_attempt_at`, `failing_endpoint`, `failure_signature`, `serving_mode`. Render a freshness banner reading this block on `index.html` and `data-health.html`. Do not touch any other page, do not rename any file yet, do not add the demo-data service in this task — that's Phase A's second half, hand off separately once this lands and is smoke-tested."
