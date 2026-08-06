# SkillEdge / Manager OS — Project Context

Stable, high-level knowledge about the project. Update only for durable facts.

## What it is

AI-assisted delivery-intelligence workspace for Koenig Solutions delivery managers.
A manager logs in with their official email; the backend fetches scoped reportees from
live RMS APIs, runs intelligence engines, and serves a unified payload that powers a
SeanTheme Color Admin dashboard frontend.

## Run

```bash
python server.py
# then open http://localhost:8765/ (redirects to login)
```

`server.py` is the single entry point; it puts `backend/` on the import path and starts
`backend/app.py`. Config via environment variables (see `.env.example`); dev defaults run
on port 8765. Production mode (`SKILLEDGE_ENV=production`) requires all RMS credentials.

## Key structure

- `server.py` — entry point
- `backend/app.py` — HTTP server: auth, RMS relay, unified endpoint, static serving
- `backend/intelligence.py` — unified intelligence build pipeline
- `backend/api/` — RMS client + credential config
- `backend/services/` — auth, cache, rms, static, scope, fetch, unified services
- `backend/shared/` — scoring, normalizers, explainability, intelligence modules
- `backend/intelligence_engines/` — engine modules
- `backend/knowledge/` — domain graphs
- `frontend/pages/` — active product pages (index, login, team, etc.)
- `frontend/deprecated/` — legacy pages that client-redirect to current ones
- `frontend/js/` — app.js, api.js (shared client)
- `frontend/assets/` — SeanTheme Color Admin assets (served at /assets/*)
- `docs/` — architecture, status, planning & review docs
- `tests/smoke_test.py` — end-to-end smoke test against a running server
- `runtime/cache/` — per-manager intelligence cache (regenerated)
- `runtime/logs/` — rotating server logs

## Serving model

- `/assets/*` served from `frontend/assets/` preserving SeanTheme layout so existing URLs work.
- Other page paths resolved in order: `frontend/pages/` → `frontend/deprecated/` → `frontend/`
  (covers shared `/js/*`). So `/team.html` serves the active page, `/trainers.html` serves the
  deprecated redirect, `/js/api.js` serves the shared client.

## Key endpoints

- `GET /healthz` — liveness + session stats
- `POST /auth/login` — session cookie for a manager email
- `GET /auth/session`, `GET /auth/logout`
- `GET /data/unified-manager-intelligence` — unified payload (session-scoped)
- `POST /rms/<api>` — server-side RMS relay (credentials never reach the browser)

## RMS integrations

RMS credentials are configured per-endpoint as user/pass env pairs
(`SKILLEDGE_RMS_<NAME>_USER` / `_PASS`). Endpoints cover assignments, course lists,
schedules, trainer details/skills/availability, feedback, incidents, utilization,
certification counts, vendor certs, and an extended catalogue (upcoming/unallocated
assignments, course technology/domain/names/availability/schedule/trainers).

## Tests

Start the server, then run `python tests/smoke_test.py`.

## Deployment

See `DEPLOYMENT.md` for deployment details; `docs/` holds architecture and status.

## Analysis notes (2026-08-03)

- **Request flow:** login builds+caches intelligence → `/data/unified-manager-intelligence` serves cache with freshness metadata (`cache_status`, `cache_age_minutes`, `last_refresh_*`) + per-request lifecycle overlays; `POST /rms/<api>` is a credentialed server-side relay.
- **Unified payload:** ~40 keys — 6 canonical datasets plus `vendor_strength_df`, `growth_intelligence_df`, `certification_*`, `delivery_*`, `allocation_*`, `organization_*`, `executive_*`, `feedback_coaching_df`, `future_skill/certification_roadmap_df`, `data_health_df`, and `trainer_decision_objects` / `allocation_decision_objects` / `manager_action_objects` (contract v3, `entity_type:sha1[:12]` ids).
- **Real logic homes:** `services/trainer_fetch_service.py`, `services/decision_objects.py`, `services/custom_course_match_service.py` (+ `local_ml_service.py` TF-IDF), `shared/*_intelligence.py`. `intelligence_engines/` and `knowledge/` are unused stubs.
- **Persistence:** manager actions → `backend/data/action_state.json`; review flags → `backend/data/review_flag_state.json` (atomic writes); refresh state → `runtime/refresh/`; KB JSONL → `runtime/knowledge_base/`; per-manager cache → `runtime/cache/` (TTL 4h).
- **Known defect:** `app.py::_read_kb_jsonl` mis-parses multi-line JSONL — knowledge search/lookup endpoints return empty for populated KB files.

## AI/ML reality (2026-08-03)

- **No agentic AI / LLM** in the codebase. The "copilot" (`frontend/js/manager-copilot.js`) is a deterministic 15-question keyword answer engine, mounted only on trainer-intelligence.html; the UI labels itself "Deterministic, not live AI".
- **Only ML:** hand-rolled TF-IDF + cosine (`backend/services/local_ml_service.py`), used solely for custom-course matching. No model persistence, versions, eval, or metrics.
- **No learning loop:** manager lifecycle actions (close/escalate/reassign, acknowledge/resolve review flags) are persisted (`backend/data/*.json`) but never feed back into scoring or recommendations.
- **Two divergent scoring models:** backend `shared/scoring.py` (qubit/assignments/certs/util/feedback/tech weights) vs client-side `_intelScore` in `frontend/js/app.js` (Qubit 30/Certs 20/Feedback 20/75·15/HR 15). Pages re-deriving buckets from raw strings (e.g. capability-builder.html) can disagree with backend `classification`.
- **Known misalignment patterns in the unified payload:** `upcoming_assignment_count`/`current_assignment` are derived from multiple sources and can differ across frames; availability engine can assert `Busy`/conf 100 where utilization says underutilized; SPOF/bench/executive risk formulas are not calibrated by `trainer_count` (a 2-trainer team shows 27 SPOF courses).

## Agentic layer & learning loop (2026-08-03)

- **Deterministic agent (no LLM required):** `backend/agentic/` — `tools.py` (12 retrieval tools over the unified payload), `agent.py` (`classify_intent`, `answer()`, `build_briefing()`), `context.py` (`build_agent_context` reads payload + `runtime/knowledge_base/*.jsonl`), `learning.py`. `agent.answer` is the single seam where an LLM provider could be plugged in later.
- **Endpoints (session-cookie auth, cache-first non-blocking):** `GET /api/agent/briefing`, `GET /api/agent/learning`, `POST /api/agent/ask`, `POST /api/agent/feedback`, `POST /api/agent/tune`.
- **Learning loop is closed:** every manager lifecycle decision (`POST /api/actions/<id>/<close|escalate|reassign>`, `POST /api/review-flags/<id>/<acknowledge|resolve|escalate>`) is auto-recorded as a labeled example; `tune_weights` applies gradient updates (clamped 0.03–0.60, renormalized) and bumps a versioned model. Persistence: `runtime/learning/feedback.jsonl` + `runtime/learning/model_weights.json`.
- **Frontend:** `frontend/js/agent-copilot.js` mounts a server-backed chat + daily briefing on the dashboard (`frontend/pages/index.html`). The older `frontend/js/manager-copilot.js` remains the per-trainer client-side rule engine on trainer-intelligence.html.
- **Auth is non-blocking:** login issues the session cookie immediately; stale cache is served with `refresh_pending: true` and a guarded background rebuild (`_schedule_background_refresh` in `app.py`).
- **Scoring calibration:** SPOF/bench/executive risk is calibrated by `trainer_count` (teams ≤2 show "Thin Bench"/Medium instead of 27 SPOF alarms); availability confidence is capped ≤60 and contradictions surfaced when calendar and capacity signals disagree.

## Architecture & data flow map (2026-08-06)

### 3-tier system architecture

```
Browser (frontend/)
  ↓ (HTTP, no secrets)
Local Server (backend/app.py, port 8765)
  ↓ (Credentials in env vars only)
External RMS APIs (https://api.koenig-solutions.com)
```

### Login → Intelligence → Dashboard (complete flow)

1. User enters email on `/login.html`
2. `POST /auth/login` → `auth_service.authenticate_manager(email)` → validates in RMS
3. Session cookie issued immediately (non-blocking; stale cache served if available)
4. Browser calls `GET /data/unified-manager-intelligence?email=...`
5. Backend checks cache (4-hour TTL at `runtime/cache/skilledge_{email}.json`)
6. **Cache hit:** return immediately + maybe schedule background refresh
7. **Cache miss/stale:** call `intelligence.build_unified(email)` — 10-step pipeline:
   - Step 1: Validate manager scope
   - Step 2: Fetch reportees (direct + indirect) via RMS
   - Step 3–9: Parallel fetch per trainer (Details, Skills, Utilization, Certs, HR, Feedback, Assignments)
   - Step 4: Global course list (once per manager)
   - Step 5–10: Normalize, score (7 engines), build graphs (4 graphs), assemble payload (15+ datasets), cache
8. Response includes freshness metadata: `generated_at`, `cache_status`, `cache_age_minutes`, `refresh_pending`, `last_refresh_error`
9. Frontend renders dashboard: KPIs, team table, charts, calendar, action queue, copilot

### 9 verified RMS APIs (active in intelligence pipeline)

| API | Key | Credentials | Purpose |
|-----|-----|-------------|---------|
| Reportees | 1 | AISHWAR_GetReportees / 7zCheFM$Cc$t | Direct/indirect reports |
| Trainer Details | 75 | AISHWAR_GetTrainerDetai / 7zCheFM$Cc$t | Skills, courses, QubitsScore, certs |
| Trainer Skills | 5 | AISHWAR_TrainerSkill / 7zCheFM$Cc$t | Certified courses by employee ID |
| Utilization | 12 | AISHWAR_Utilization / 7zCheFM$Cc$t | % workload utilization |
| Vendor Certs | 11 | AISHWAR_VendorCert / 7zCheFM$Cc$t | Count of vendor certifications |
| Negative Feedback | 10 | AISHWAR_NegativeFeed / 7zCheFM$Cc$t | Feedback complaint count |
| HR Incidents | 9 | AISHWAR_HRIncident / 7zCheFM$Cc$t | Positive & negative events |
| Course List | 2 | AISHWAR_CourseList / 7zCheFM$Cc$t | Master course catalog (once per manager) |
| Assignments | 15 | AISHWAR_AssignmentAPI / 4PV6aCe6Sc8! | Past and upcoming deliveries |

Each API requires: (1) POST to `/api/Kites/Operator/GetToken` with username/password/role → get accessToken + deviceToken; (2) POST to `/api/Kites/Operator/common?apikey={key}&accessToken={token}&deviceToken={device_token}` with request body.

### 14 active frontend pages (data sources from unified intelligence)

- `index.html` — Dashboard; uses trainer_operations_df, trainer_current_state_df, batch_engagement_df, manager_action_objects, unallocated_demand_df, data_health_df, future_skill_roadmap_df
- `login.html` — Email entry; calls API.login then getUnifiedManagerIntelligence
- `team.html` — Trainer roster 360 view; uses trainer_operations_df
- `trainer-intelligence.html` — Individual trainer deep-dive; targeted API calls
- `trainer-detail.html` — Alt trainer view (similar to trainer-intelligence)
- `actions.html` — Manager action queue; uses manager_action_objects (close/escalate/reassign)
- `allocation-desk.html` — Match trainers to unallocated demand; uses custom_course_match_service
- `custom-course-match.html` — Ranked trainer suggestions for a course
- `capability-builder.html` — Skills roadmap, training recommendations; uses future_skill_roadmap_df
- `certifications.html` — Certification landscape, gaps; uses certification_summary
- `risk-takers.html` — High-risk trainers; uses trainer_classification
- `data-health.html` — Data quality issues, API errors; uses data_health_df
- `settings.html` — Theme/preference settings (localStorage)
- `coming-soon.html` — Placeholder for future features

### 20+ backend HTTP routes

**Auth:** `POST /auth/login`, `GET /auth/session`, `GET /auth/logout`

**Data & Intelligence:** `GET /data/unified-manager-intelligence?email=...`, `POST /api/refresh/run`, `GET /api/refresh/status`

**RMS Relay:** `POST /rms/{api_name}` (all 9 APIs proxied here; credentials handled server-side)

**Action Lifecycle:** `POST /api/actions/{id}/close|escalate|reassign`, `GET /api/actions`

**Review Flags:** `POST /api/review-flags/{id}/acknowledge|resolve|escalate`, `GET /api/review-flags`

**Agentic:** `GET /api/agent/briefing`, `GET /api/agent/learning`, `POST /api/agent/ask`, `POST /api/agent/feedback`, `POST /api/agent/tune`

**Diagnostics:** `GET /healthz` (liveness + session stats)

**Static:** `/`, `/login.html`, `/js/api.js`, `/js/agent-copilot.js`, `/assets/*`

### 7 intelligence engines (explainable scoring)

1. **Availability Engine** — Status (teaching_now, preparing, scheduled_today, free, blocked, unknown) + confidence; looks at current/next batch, roaming/IL dates
2. **Readiness Engine** — Bucket (Ready ≥75%, Prep ≥55%, Blocked <55%); factors: skills, certs, feedback, utilization
3. **Capability Engine** — Fit score (0–100) for trainer vs specific course; factors: skill match, certs, delivery experience, availability
4. **Trust Engine** — Risk level (Low, Medium, High); factors: negative feedback, HR incidents, consistency trend
5. **Recommendation Engine** — Suggests: next actions, skill gaps, career paths; returns ranked with priority
6. **Action Engine** — Identifies manager decisions needed (blockers, feedback cases, skill gaps)
7. **Custom Course Matching** — Ranks all trainers by fit score for unallocated demand

### 4 knowledge graphs (built per refresh)

1. **Trainer Graph** — Trainer → Skills → Certs → Courses → Domains
2. **Certification Graph** — Cert → Vendor → Courses → Trainers
3. **Course Graph** — Course → Modules → Topics → Prerequisites
4. **Technology Graph** — Tech → Products → Domains → Skills

### 15+ output datasets (in unified intelligence JSON)

- `trainer_operations_df` — all trainers + stats
- `trainer_current_state_df` — current status, current/next batch, confidence
- `batch_engagement_df` — current & upcoming training batches
- `manager_action_objects` — decisions manager needs to make
- `trainer_feedback_summary_df` — feedback sentiment, positive/negative, risk
- `unallocated_demand_df` — sales requests waiting allocation
- `trainer_decision_objects` — trainer-specific decisions (gaps, risks, recommendations)
- `future_skill_roadmap_df` — recommended training paths
- `data_health_df` — data quality issues, missing fields, API errors
- `certification_summary` — certs by vendor, gaps, coverage
- `delivery_intelligence` — delivery schedule, pipeline, capacity
- `allocation_intelligence` — demand matching, fit scores, blockers
- `organization_intelligence` — team structure, span of control, capacity
- `growth_intelligence` — skill progression, career paths, upgrades
- `vendor_strength_df`, `executive_intelligence`, `feedback_coaching_df`, `future_certification_roadmap_df`, etc.

### Caching & freshness strategy

- **Per-manager cache:** `runtime/cache/skilledge_{email_normalized}.json` (4-hour TTL)
- **Freshness metadata attached to every response:** `generated_at` (Unix timestamp when built), `served_at` (now), `cache_status` (live | cached | stale | refresh_failed), `cache_age_minutes`, `refresh_pending` (bool), `last_refresh_error` (string)
- **Stale cache handling:** serves old cache immediately + schedules non-blocking background refresh via `_schedule_background_refresh` (daemon thread, per-email guard)
- **Build failure fallback:** 3 retry attempts (wait 1s between) → if all fail and old cache exists: serve stale + record error; if no cache: raise error to user

### Security model

- **Secrets server-only:** RMS credentials stored as environment variables (`SKILLEDGE_RMS_<API>_USER` / `_PASS`), never in code or browser
- **Frontend proxy pattern:** browser calls `POST /rms/{api_name}`, backend adds auth tokens and calls external API, unwraps response, returns clean data
- **Session in-memory:** cookie-based, expires on logout (no persistent tokens in browser)
- **Auth non-blocking:** login issues session immediately; old cache served while rebuild runs in background

### Error handling & recovery

- **Intelligence build fails (3 retries):** Attempt 1 fails → wait 1s → Attempt 2 → wait 1s → Attempt 3 → if all fail: fall back to old cache (if exists) with `last_refresh_error` set; if no cache: error to user
- **Session expiration:** HTTP 401/403 → redirect to /login.html?expired=1 → clear sessionStorage
- **API unreachable:** fallback to stale cache with honest freshness metadata; frontend can judge data age

### Known gaps (2026-08-03, candidate follow-ups)

1. **Live smoke test:** `tests/smoke_test.py` needs real RMS creds + running server to verify end-to-end journey
2. **Duplicated scoring:** client-side `_intelScore` (app.js) should be removed; scoring is canonical backend-side (shared/scoring.py)
3. **Session persistence:** sessions lost on restart; could use database backing
4. **Dead scaffolding:** `intelligence_engines/` and `knowledge/` stubs (7 + 4 files) have zero imports; wire or delete
5. **KB parser bug:** `app.py::_read_kb_jsonl` mis-parses multi-line JSONL; knowledge search endpoints return empty

### Additional APIs documented but not yet integrated (21 more)

`trainer_portal_api_details/` lists 30 APIs total; 9 active, 21 documented but not in intelligence pipeline:
- Course modules, syllabus, schedule, content URL
- Course without exam, exam-linked courses, latest version, availability in RMS
- SCID (Scheduling ID), recording details
- Active SC date, assignment PAX, course & domain
- Trainer free schedule, last 3-month utilization, resume details
- Trainer negative feedback (detailed), unique cert count
- Direct/indirect reportee (detailed), unallocated/upcoming assignments
- Add trainer skill (IDP), check course availability

These are verified endpoints with credentials + API keys; integration would expand intelligence pipeline coverage but is not blocking current functionality.
