# SkillEdge Repository Refactoring Blueprint

This document is a design-only blueprint for reorganizing the current SkillEdge repository into reusable capability modules without changing functionality.

## 1) Current repo map

### Current top-level reality

- `server.py` is the HTTP server and cache layer.
- `intelligence.py` is the full backend intelligence pipeline.
- `js/api.js` is the browser API client and data cache/warm-up layer.
- `js/app.js` is the shared layout, menu, notifications, and UI helper layer.
- HTML pages are currently self-contained application views consuming the unified payload.

### Current behavior shape

- The backend still returns one unified payload from `/data/unified-manager-intelligence`.
- The payload contains the canonical datasets:
  - `trainer_operations_df`
  - `course_allocation_df`
  - `trainer_timeline_df`
  - `manager_action_df`
  - `trainer_availability_engine_df`
  - `custom_course_match_df`
  - `data_health_df`
- The frontend pages mostly consume that one payload and render different slices of it.

## 2) Proposed repo map

This target layout preserves the current behavior but separates responsibilities.

```text
SkillEdge/
├── api/
│   ├── reportees.py
│   ├── trainer_details.py
│   ├── skills.py
│   ├── assignments.py
│   ├── utilization.py
│   ├── certification.py
│   ├── feedback.py
│   ├── resume.py
│   └── courses.py
├── knowledge/
│   ├── technology_graph.py
│   ├── course_graph.py
│   ├── trainer_graph.py
│   └── certification_graph.py
├── intelligence/
│   ├── capability_engine.py
│   ├── availability_engine.py
│   ├── readiness_engine.py
│   ├── recommendation_engine.py
│   ├── action_engine.py
│   ├── trust_engine.py
│   └── custom_course_engine.py
├── services/
│   ├── unified_intelligence_service.py
│   ├── cache_service.py
│   ├── auth_service.py
│   └── manager_scope_service.py
├── shared/
│   ├── normalizers.py
│   ├── scoring.py
│   ├── explainability.py
│   ├── constants.py
│   └── safety.py
├── pages/
│   ├── index.html
│   ├── team.html
│   ├── trainer-detail.html
│   ├── allocation-desk.html
│   ├── custom-course-match.html
│   ├── actions.html
│   └── data-health.html
├── server.py
├── intelligence.py
├── js/
└── assets/
```

## 3) Module responsibility table

| Target module | Responsibility | Current code it should absorb |
|---|---|---|
| `api/reportees.py` | Fetch and normalize manager scope | reportee token/call logic in `intelligence.py`, `js/api.js` |
| `api/trainer_details.py` | Trainer capability details | Trainer Details call and field normalization |
| `api/skills.py` | Trainer skills and course links | Trainer Skills call and normalization |
| `api/assignments.py` | Assignment retrieval and shaping | Assignment API call and normalization |
| `api/utilization.py` | Utilization fetch and parse | Utilization fetch/parse code in Python and JS |
| `api/certification.py` | Vendor cert, unique cert, exam-link, course-without-exam | cert and exam-related call paths |
| `api/feedback.py` | Negative feedback and detailed feedback | neg feedback and trainer feedback calls |
| `api/resume.py` | Resume details and certification text | Trainer Resume Details handling |
| `api/courses.py` | Course list and course master | Course List fetch and course lookups |
| `knowledge/technology_graph.py` | Technologies, domains, OEM relationships | blocked course-domain/technology mapping placeholder |
| `knowledge/course_graph.py` | Course-to-topic/technology/cert structure | course catalog enrichment and match support |
| `knowledge/trainer_graph.py` | Trainer-to-skill/course/history graph | trainer relationships currently flattened in `trainer_operations_df` |
| `knowledge/certification_graph.py` | Certification relationships and source blending | certification source logic and cert rollups |
| `intelligence/capability_engine.py` | Trainer capability scoring and capability rollups | `_score_trainer`, capability fields in `trainer_operations_df` |
| `intelligence/availability_engine.py` | Availability composite and delivery safety | availability rows and `trainer_availability_engine_df` |
| `intelligence/readiness_engine.py` | Readiness scoring and buckets | readiness logic from `_score_trainer` |
| `intelligence/recommendation_engine.py` | Allocate / coach / hold / risk-taker recommendations | `_recommend`, allocation recommendations |
| `intelligence/action_engine.py` | Manager action generation | `manager_action_df` construction |
| `intelligence/trust_engine.py` | Health, confidence, missing-data handling | `data_health_df` and health normalization |
| `intelligence/custom_course_engine.py` | Custom course outline matching | `custom_course_match_df` placeholder and page-side heuristic logic |
| `services/unified_intelligence_service.py` | Orchestrate the full build and return payload | `build_unified()` in `intelligence.py` plus `server.py` endpoint wiring |
| `services/cache_service.py` | Cache read/write/TTL | per-manager disk cache in `server.py` |
| `services/auth_service.py` | Token acquisition and secret-safe API call access | token handling in `intelligence.py` and browser client config in `js/api.js` |
| `services/manager_scope_service.py` | Determine scoped reportees and manager identity | reportee filtering and manager email handling |
| `shared/normalizers.py` | Reusable response normalizers | all `norm_*` functions in `intelligence.py` and the normalizers in `js/api.js` |
| `shared/scoring.py` | Shared score math and bucket thresholds | `_score_trainer`, `_intelScore`, allocation scoring |
| `shared/explainability.py` | Evidence and reason text builders | availability reason, action reason, health impact strings |
| `shared/constants.py` | Stable labels, thresholds, dataset names | status labels, API names, page names |
| `shared/safety.py` | Redaction, sanitization, safe defaults | `_safe_error`, health scrubbing, secret protection |
| `pages/*` | UI-only page consumers | current HTML pages |

## 4) Extraction plan from `intelligence.py`

`intelligence.py` is currently the largest concentration of responsibility. It should be split without changing the unified payload.

### Extract first

1. **API calls and token handling**
   - Move `CONFIGS`, `_get_token`, `_call`, and related helpers into `services/auth_service.py` and `api/*`.
   - Keep the call behavior identical.

2. **Response normalizers**
   - Move `norm_detail`, `norm_skill`, `norm_hr`, `parse_utilization`, `parse_certs`, `norm_resume_details`, `norm_trainer_availability`, `norm_prev_upcoming`, `norm_feedback_details`, `norm_last3_util`, `norm_course_without_exam`, `norm_exam_course_linked`, `norm_unique_cert_count`.
   - These should become reusable and unit-testable.

3. **Per-trainer fetch logic**
   - Move `_fetch_trainer` into a trainer orchestration/service layer.
   - Keep the per-trainer API call order and error isolation intact.

4. **Scoring**
   - Move `_score_trainer` and `_recommend` into `intelligence/readiness_engine.py` and `intelligence/recommendation_engine.py`.
   - Preserve weights, bucket thresholds, and fallback behavior.

5. **Health handling**
   - Move `_safe_error`, `_failed_trainer_health`, `_health_*`, and `normalize_health` into `intelligence/trust_engine.py` and `shared/safety.py`.
   - Preserve exact output schema for `data_health_df`.

6. **Dataset assembly**
   - Keep the assembly order and payload keys in a service wrapper.
   - This wrapper should still produce the same unified JSON shape.

### What must stay identical

- row counts
- field names in the unified payload
- page-facing aliases
- health row semantics
- cache TTL behavior
- manager scope filtering
- page routes and query parameter behavior

## 5) Migration order

This is the safest sequence if the refactor is ever executed.

1. **Create shared constants and safety helpers**
2. **Move normalizers into `shared/normalizers.py`**
3. **Extract auth and API call layer**
4. **Extract readiness and recommendation scoring**
5. **Extract trust and health logic**
6. **Extract availability engine**
7. **Extract course allocation and custom course logic**
8. **Wrap the unified payload assembly in a service layer**
9. **Split browser helpers into clearer API / layout files if needed**
10. **Only then consider deeper knowledge graph modules**

## 6) Risk areas

### Highest risk

- Changing payload shape by accident
- Changing label semantics for readiness or availability
- Changing cache behavior and triggering more RMS calls
- Breaking the manager scope guard
- Introducing duplicate normalization between backend and browser

### Medium risk

- Splitting `intelligence.py` too aggressively before tests exist
- Moving logic into modules but leaving circular imports
- Separating knowledge graph concepts before the underlying data is stable

### Lower risk

- Extracting helper functions with no behavior change
- Moving constants and explainability strings
- Isolating UI page helpers that only render data

## 7) Regression test checklist

After any future refactor, verify all of the following:

- `/data/unified-manager-intelligence?email=...` still returns the same top-level keys
- `trainer_operations_df` still contains the same expected fields
- `course_allocation_df` still renders on allocation pages
- `trainer_timeline_df` still renders on trainer detail and action pages
- `manager_action_df` still renders on actions
- `trainer_availability_engine_df` still renders on team, allocation, and data-health pages
- `data_health_df` still deduplicates and redacts properly
- the cache still honors the 4-hour TTL
- a page refresh does not re-hit RMS APIs when cache is valid
- manager scope is still restricted to the signed-in manager
- custom course match still loads from the same unified payload shape
- trainer detail URLs still resolve by email
- no new console errors appear from route or layout changes

## 8) Do not change contract

This contract must remain fixed during any refactor.

### Do not change

- `/data/unified-manager-intelligence` response structure
- current HTML routes
- manager-scoped behavior
- cache TTL and cache file naming behavior
- readiness score semantics
- availability label semantics
- recommendation labels
- health row deduplication and redaction behavior
- front-end field names already consumed by pages

### Do not change unless explicitly approved later

- page URLs
- public login flow
- browser-facing query parameters
- the current definition of what counts as missing or unknown

## 9) Current file-to-target mapping

### `server.py`

- **Current ownership:** HTTP server, file serving, unified endpoint routing, cache file loading, cache TTL, refresh behavior.
- **Target move:** `services/cache_service.py`, `services/unified_intelligence_service.py`.
- **Extract:** cache path logic, cache read/write, payload upgrade pass, unified route handler.
- **Must not change:** endpoint path, cache semantics, response format, file serving.
- **Risk:** high if cache or route behavior changes.
- **Test:** refresh endpoint, cached endpoint, non-cached endpoint, HTML route serving.

### `intelligence.py`

- **Current ownership:** API configs, API calls, normalization, scoring, recommendation, health, dataset assembly.
- **Target move:** `api/*`, `shared/*`, `intelligence/*`, `services/unified_intelligence_service.py`.
- **Extract:** API access, normalizers, `_fetch_trainer`, scoring, recommendation, health, dataset assembly.
- **Must not change:** unified payload output and per-trainer API order.
- **Risk:** very high.
- **Test:** response parity for a known manager, field-level snapshot comparison, health row equality.

### `js/api.js`

- **Current ownership:** client-side API access, session cache, warm-up, unified endpoint fetch.
- **Target move:** `services/auth_service.py` conceptually maps to backend; browser code stays browser code.
- **Extract:** duplicate normalizer logic if kept in browser; unified fetch helper.
- **Must not change:** frontend cache and page behavior.
- **Risk:** medium.
- **Test:** login warm-up, page reads from unified endpoint, no direct RMS calls from HTML.

### `js/app.js`

- **Current ownership:** shared layout, menu model, notifications, page shells, auth guard.
- **Target move:** stays frontend shared layer, but could be split into layout/navigation helpers later.
- **Extract:** menu model constants, notification render helpers, page shell helpers.
- **Must not change:** route targets, active menu behavior, auth guard.
- **Risk:** medium.
- **Test:** active menu state, route rendering, notifications, logout.

### `index.html`

- **Current ownership:** cockpit dashboard rendering.
- **Target move:** stays in `pages/`.
- **Extract:** none for behavior; only shared render logic could move later.
- **Must not change:** summary cards, charts, unified payload consumption.
- **Risk:** low to medium.
- **Test:** page load, KPI rendering, chart rendering.

### `trainer-detail.html`

- **Current ownership:** Trainer 360 view.
- **Target move:** stays in `pages/`.
- **Extract:** none for behavior; reusable card widgets can be shared later.
- **Must not change:** trainer lookup by email, 360 sections, evidence blocks.
- **Risk:** low to medium.
- **Test:** direct trainer URL, trainer picker, evidence sections.

### `allocation-desk.html`

- **Current ownership:** allocation ranking and filtering.
- **Target move:** stays in `pages/`.
- **Extract:** shared allocation scoring helpers later.
- **Must not change:** ranking, filters, best match panel, evidence.
- **Risk:** medium.
- **Test:** filter behavior, best-match selection, card rendering.

### `custom-course-match.html`

- **Current ownership:** browser-side custom outline parsing and match preview.
- **Target move:** stays in `pages/`, but should eventually consume `intelligence/custom_course_engine.py`.
- **Extract:** parsing, matching, risk appetite logic, match grouping.
- **Must not change:** page route, current preview behavior until backend replacement exists.
- **Risk:** high because current logic is largely local.
- **Test:** paste mode, upload placeholder, result groups, browser render.

### `actions.html`

- **Current ownership:** manager action inbox and detail modal.
- **Target move:** stays in `pages/`.
- **Extract:** action grouping and explainability helpers later.
- **Must not change:** action data source, modal behavior, trainer links.
- **Risk:** medium.
- **Test:** action filters, modal drill-in, link routing.

### `data-health.html`

- **Current ownership:** trust and completeness view.
- **Target move:** stays in `pages/`.
- **Extract:** health grouping and severity helpers later.
- **Must not change:** no fake OK rows, health categories, collapsed evidence.
- **Risk:** medium.
- **Test:** health counts, issue grouping, evidence accordion.

## 10) Practical refactor principle

The refactor should be organized around **capabilities**, not file type.

That means:

- API fetching
- knowledge structures
- scoring and recommendation
- trust and safety
- orchestration services
- shared constants and normalizers
- page rendering

should become separate layers.

The actual user-facing behavior should remain unchanged until each extracted module can be tested against the current unified payload.

