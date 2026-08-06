# SkillEdge / Manager OS — Phased Rebuild Guide

Purpose of this document: a complete, module-by-module map of what this codebase is and how it was
built, ordered as a **dependency-correct build sequence** so another AI coding agent (e.g. OpenCode)
can recreate the same system in a new project from scratch — same architecture, same algorithms,
same endpoints, without needing to read the original source.

## What this product is

SkillEdge / Manager OS is a delivery-intelligence dashboard for Koenig Solutions delivery managers
(an instructor-led training company). A manager logs in with their email; the backend pulls their
scoped reportee trainers from ~19-27 RMS (Resource Management System) APIs, normalizes and scores
that data server-side, and serves **one unified JSON payload** that every frontend page renders from.
Nothing is invented — every score/flag must trace back to a real API field and a documented rule.
No database, no third-party LLM. Single Python process, vanilla-JS frontend on a Bootstrap admin theme.

**Non-goals confirmed by the project's own docs:** not an LMS/CRM/generic BI tool; not currently
using a real LLM (the "AI copilot" is a deterministic rule engine with one seam left open for a
future model); `backend/knowledge/*` and `backend/intelligence_engines/*` are dead scaffolding —
zero imports anywhere in the codebase. Do not treat their file names as required architecture.

---

## Build order at a glance

```
Phase 1  Foundation & config           (env, constants, safety helpers)
Phase 2  External API client           (RMS token auth, throttling, retry)
Phase 3  Data normalization            (raw API rows -> internal shape)
Phase 4  Core platform services        (auth/session, disk cache, static files)
Phase 5  Per-trainer data fan-out      (concurrent multi-API collection per trainer)
Phase 6  Scoring & classification      (the core readiness/risk formulas)
Phase 7  Domain intelligence modules   (growth, cert, delivery, allocation, org, exec)
Phase 8  Decision objects & business services (rankings, matching, manager actions)
Phase 9  Persisted manager-state       (action/review-flag lifecycle, JSON-backed)
Phase 10 Unified pipeline orchestrator (the one function that calls everything above)
Phase 11 HTTP server & routing         (the actual web server + endpoints)
Phase 12 Refresh scheduling            (background rebuild, staleness policy)
Phase 13 Agentic assistant layer       (rule-based Q&A + closed-loop learning)
Phase 14 Frontend foundation           (shared API client, layout/nav, login)
Phase 15 Frontend pages                (13 product pages)
Phase 16 Testing                       (smoke test contract)
Phase 17 Deployment                    (env modes, process, logging)
```

Each phase only depends on phases above it. Build strictly top-down; nothing in Phase N imports
from Phase N+1 in the original codebase, so this order is safe to follow literally.

---

## Phase 1 — Foundation & config

**Goal:** get environment-driven configuration and defensive parsing primitives in place before
anything else, since every later module leans on them.

- `shared/constants.py` — `API_BASE`, `TOKEN_ENDPOINT`, `DATA_ENDPOINT` (the external RMS base URL
  and its two sub-paths), `DEFAULT_TIMEOUT = 40s`, `CACHE_TTL_SECONDS = 4h`, and a `DATASET_NAMES`
  tuple naming the canonical pipeline output tables.
- `shared/safety.py` — `safe_num`/`safe_int` (coerce with default), `safe_truthy` (accepts
  `"1"/"true"/"yes"/"y"`), `clean_name` (whitespace collapse), `safe_error(exc)` — **redacts secrets**
  (`accessToken`, `deviceToken`, `userPassword`, `apikey`, `userName`, `userRole`, `Bearer`) from any
  exception text before it's logged or returned to a client, truncated to 200 chars. Every error path
  in the app should route through this.
- `api/config.py` — the credential registry. A `CONFIGS` dict keyed by short API name →
  `{"key", "user", "pass", "role"}`. `_env(name, fallback)` reads env vars; if unset **and**
  `SKILLEDGE_ENV=production`, raise `EnvironmentError` (fail fast); in dev, fall back to a
  `_DEV_FALLBACKS` dict of hardcoded dev credentials (only ever committed for a private/internal
  dev environment — do not do this if credentials are sensitive in your new project; prefer
  `.env`-only with no in-repo fallback). `is_configured(api_name)` returns whether user+pass are
  both non-empty, without leaking values. `validate_all_credentials()` is the startup fail-fast gate.
  List every external API name you'll need up front — this project enumerates 27 (see Phase 2).
- `.env.example` — document every env var: `SKILLEDGE_ENV`, `SKILLEDGE_PORT` (default 8765),
  `SKILLEDGE_LOG_LEVEL`, `SKILLEDGE_LOG_FORMAT`, plus one `_USER`/`_PASS` pair per external API.

**Design decision worth keeping:** two explicit modes, `development` (never blocks startup, uses
fallbacks) vs `production` (refuses to start if any credential is missing). This is the single
biggest thing that made local dev friction-free without risking a silent misconfiguration in prod.

---

## Phase 2 — External API client

**Goal:** one low-level, thread-safe client for calling the external system (RMS), with token
caching and throttling, so nothing above this layer has to think about auth or connection limits.

`api/client.py`:
- Module-level state: `_token_cache` (dict, api_name → token payload, cached **forever**, no TTL —
  evicted only on 401/403), `_token_locks` (per-api-name `threading.Lock`, created under a guard
  lock — double-checked locking pattern), and `_request_slots = threading.BoundedSemaphore(4)` that
  throttles **all** outbound HTTP (token fetch + data calls) process-wide to 4 concurrent requests,
  regardless of how many threads are fanning out above it. This exists because the external system
  degrades sharply under nested concurrent fan-out — keep this pattern if your new project's upstream
  API has similar characteristics.
- `_post(path, body, timeout)` — builds and sends one JSON POST via `urllib.request` (or your
  language's stdlib HTTP client — no external HTTP library dependency was used here), acquiring a
  semaphore slot first.
- `_get_token(api_name, timeout)` — fast-path cache hit; on miss, take the per-api lock, re-check
  cache (another thread may have just filled it), then POST `{"userName","userPassword","userRole"}`
  to `TOKEN_ENDPOINT`, cache the returned token indefinitely.
- `_call(api_name, body, timeout)` — the only public entry point. Fetch token → build
  `DATA_ENDPOINT?apikey=<key>&accessToken=<url-encoded>&deviceToken=<url-encoded>` → POST body.
  On 401/403 (from response body or `HTTPError`) or an exception containing `"Forbidden"`, evict the
  cached token and retry **once**. Any other exception propagates immediately — no exponential
  backoff, no more than 2 total attempts. Response unwrapping: if `content` is a string, `json.loads`
  it; if a dict, unwrap the first present of `Data/data/Result/result/Items/items`; always return a
  list (empty list on `None`).
- `clear_token_cache()` — manual reset, exposed to an admin/settings surface.

**RMS APIs this project integrates with (27 total, for reference):** `reportees, trainerDetails,
trainerSkills, utilization, vendorCerts, resumeDetails, negativeFeedback, hrIncidents, trainerAvail,
freeSchedule, prevUpcoming, trainerFeedback, trainerLast3, trainerRCSchedule, courseWithoutExam,
examCourseLinked, uniqueCertCount, courseList, assignments, upcomingAssignments,
unallocatedAssignments, courseTechnology, courseDomain, courseNames, courseAvailability,
courseSchedule, courseTrainers, addTrainerSkill`. In your new project, replace this list with
whatever your actual upstream integration exposes — the pattern (one config entry per API name,
one client function) is what to keep, not these specific names.

---

## Phase 3 — Data normalization

**Goal:** convert whatever shape the external API returns into one stable internal shape, so every
downstream module can assume clean data and never touch a raw API field name again.

- `shared/normalizers.py` — one `norm_*`/`parse_*` function per raw dataset (e.g. `norm_detail`,
  `norm_skill`, `norm_hr`, `norm_negfb`, `parse_utilization` — parses a `"Jun 2026": "load%/util%"`
  style monthly string column into a trend array, `parse_certs`, `norm_resume_details`,
  `norm_trainer_availability`, `norm_prev_upcoming`, `norm_last3_util`, `norm_unique_cert_count`,
  `normalize_health` — builds the "data health" dataset with severity/impact/fix metadata, deduped).
  This file is where every literal upstream field name lives — isolate it here so a field rename
  upstream only touches one file.
- `shared/explainability.py` — small lookup helpers (`health_dataset_for`, `health_page_for`,
  `health_impact_for`, `health_fix_for`) that map an API name / issue type to a human-readable
  "which dataset does this affect and how do I fix it" explanation. This is what powers the
  Data Health page (Phase 15) — build it now while the concept is fresh.

**Principle to carry forward:** normalization is pure and side-effect-free — no I/O, no calls to
other services. Every function takes raw dict/list in, returns clean dict/list out.

---

## Phase 4 — Core platform services

**Goal:** the platform primitives every route handler needs: session auth, an on-disk cache, and
static file resolution. None of these depend on business logic yet.

- `services/auth_service.py` — in-memory session store (`_SESSIONS` dict, process-local — lost on
  restart, single-instance only; note this constraint explicitly if you need multi-instance auth).
  `create_session(email)` → `secrets.token_urlsafe(32)` opaque token, stored with `{email,
  created_at}`. `session_cookie(token)` builds `Set-Cookie: <name>=<token>; HttpOnly; SameSite=Lax;
  Max-Age=<TTL>` (TTL from `SKILLEDGE_SESSION_TTL_SECONDS`, default 8h). `session_email_from_cookie`
  validates age and evicts if expired. This is intentionally a simple opaque-token session, not JWT.
- `services/cache_service.py` — disk-backed JSON cache, one file per user
  (`runtime/cache/intel_<slug(email)>.json`). `CACHE_TTL = 4h`. Critically: a
  `CACHE_SCHEMA_VERSION` constant is stamped into every cache write and checked on read
  (`cache_fresh`) — **bump this any time the payload shape changes**, so stale-shaped cache never
  gets served to a frontend expecting a new shape. This is the single most valuable pattern in the
  whole caching design; keep it in any new project with a similarly expensive-to-rebuild payload.
- `services/static_service.py` — `resolve_static_request(path, assets_dir, page_roots)`: `/assets/*`
  maps straight to an assets dir; every other path is searched across an **ordered list** of page
  roots (active pages → deprecated redirects → shared js root), so restructuring the frontend never
  breaks an old bookmarked URL. Enforces path-containment (resolve + verify inside allowed root) to
  block path traversal.
- `services/manager_scope_service.py` — trivial: `manager_email_from_query` (trim), `manager_scope_key`
  (lowercase, default "default") — the scoping key used everywhere a per-manager cache/session lookup
  happens.

---

## Phase 5 — Per-trainer data fan-out

**Goal:** given one manager's list of reportee trainers, concurrently pull every relevant dataset
for each trainer from the Phase 2 client, normalized via Phase 3, with per-trainer failure isolation.

`services/trainer_fetch_service.py`:
- `fetch_trainer(trainer)` — fires ~13 independent API calls per trainer concurrently
  (`ThreadPoolExecutor(max_workers=5)`): details, skills, utilization, vendor certs, resume details,
  unique certs, prev/upcoming assignments, upcoming assignments, RC schedule, last-3-months,
  negative feedback, HR incidents, assignments. Each call is wrapped in a `safe()` helper so **one
  API failure only appends a `health[]` entry**, it never aborts the batch. One flaky-endpoint
  special case worth keeping: if the calendar call comes back empty, retry once with a longer
  timeout — some upstream APIs are slow-but-eventually-consistent under load rather than truly empty.
  After the main fan-out, extract distinct assignment IDs (capped, e.g. top 5) and fire a second
  round of parallel per-assignment feedback-detail lookups.
- `safe_fetch(trainer, fetch_fn)` — the outer isolation wrapper used by the batch orchestrator
  (Phase 10): catches any exception and returns an `__error__` sentinel row instead of raising, so
  one bad trainer never crashes the whole manager's payload build.
- `services/reference_data_service.py` — builds shared reference tables once per manager-build
  (not per trainer): `build_course_master` merges course/technology/domain/exam-policy datasets
  keyed by course id (falls back to `name:<lower>` when no id), tracking provenance
  (`source_datasets`) per merged field. `build_unallocated_demand` normalizes an "unallocated work"
  feed into demand rows, synthesizing a stable id when the upstream doesn't provide one.

**Concurrency note worth keeping verbatim:** this project does **sequential token pre-warm** for the
first call per trainer, then switches to concurrent fetch — because in the real upstream API, token
generation invalidates a prior device token, so racing the very first fetch per trainer causes
spurious 403s. If your new project's upstream auth has similar single-active-token semantics, budget
for this exact pattern (pre-warm sequentially, then fan out).

---

## Phase 6 — Scoring & classification (the core algorithm)

**Goal:** the single per-trainer readiness/risk scorer everything else in Phase 7-8 builds on top of.

`shared/scoring.py` — `score_trainer(t)`: a weighted composite over whatever signals are present
(weights renormalize if some inputs are missing):
- `qubit_score|skills_score` weight 0.25, `assign_score` 0.15, `cert_score` 0.15, `util_score` 0.10,
  `fb_score` 0.15, `details_score` 0.10.
- Sub-scores: `skills_score = min(100, skill_count*12)`, `assign_score = min(100, assignments*10)`,
  `cert_score = min(100, certs*20)`, `fb_score = max(0, 100 - negative_feedback*20)`.
- `readiness_bucket`: **Ready Now** ≥80, **Can Deliver with Prep** ≥65, **Needs Coaching** ≥45,
  else **Not Recommended** — or **Data Incomplete** if the two most load-bearing signals
  (score + utilization) are both missing. Never guess past that point.
- `confidence = 100 - missing_signal_count*12`.
- `risk_taker_score = diversity*8 + (20 if future_skill) + (15 if advanced credential) +
  (100-util)*0.15 - negfb*5`; `growth_bucket`: **Safe Expert** (readiness≥80 & 0 negfb), **Risk
  Taker** (risk≥60 & readiness≥45), **Growth Candidate** (readiness≥55), **Do Not Risk** (negfb>2).
- `recommend(t, sc)` — maps readiness+utilization to one action string ("Hold Delivery" if negfb>2,
  "Allocate"/"Review Availability" if ready, "Book Mock"/"Coach" if mid-range, "Review Data Gap" if
  incomplete, else "Upskill").

`shared/classification.py` — `build_trainer_classification(t_row, decision_row=None)`: the
single source of truth for status badges (used to prevent duplicated logic between backend and
frontend). Derives boolean flags (`has_compliance_flag`, `has_delivery_concern`,
`has_certification_blocker`, `is_overloaded`, `is_underused`), maps a decision object's
`assignment_status` to an `assignability` enum (`assign_now/review_required/hold/upskill_first`) via
a fixed lookup table, and produces a `badge_label`/`badge_tone` plus a single `manager_next_action`
string. Falls back to the raw `readiness_bucket` if no decision object is available yet (decision
objects are built later, in Phase 8 — this module must tolerate being called before or after them).

**Principle:** every bucket boundary above is a literal magic number chosen by the product owner —
copy them into a constants section instead of scattering them, but don't try to make them "smarter"
without direction; they encode calibrated business judgment, not a formula waiting to be improved.

---

## Phase 7 — Domain intelligence modules

**Goal:** team/course/vendor-level rollups built purely from already-scored trainer rows — each
function here is pure (no I/O, no cross-imports between these files; they only receive pre-shaped
lists/dicts as arguments and are all called from Phase 10's orchestrator).

- `shared/growth_intelligence.py` — vendor/OEM capability. `build_vendor_strengths`:
  `strength_score = avg_qubit*0.45 + min(30, courses*5) + min(15, deliveries*3) +
  (10 if accredited) + min(10, future_skills*3)`, labeled Strong≥75/Moderate≥50/Emerging≥25/Nascent.
  `build_growth_recommendations` is rule-based (certify / expand / upskill / deploy / advance).
  `build_oem_heatmap` rolls this to team-level bench risk per vendor.
- `shared/certification_intelligence.py` — accreditation-vs-course-requirement gap analysis (not
  exam pass/fail — that data doesn't exist upstream in this project; if your new project *does*
  have real exam results, this is where you'd wire them in). Groups gaps by vendor with confidence
  85/70/55 tiers based on trainer quality signal.
- `shared/delivery_intelligence.py` — per-trainer/per-course delivery readiness:
  `score = readiness*0.35 + quality*0.25 + history_score*0.15 + capacity_score*0.15 +
  availability_confidence*0.2 - quality_penalty*0.35`; capacity from a 3-state utilization bucket
  (Overloaded ≥85 util, Underutilized <40, else Balanced). Also emits course-level backup-coverage
  tables.
- `shared/allocation_intelligence.py` — the most complex scoring module: per (course, trainer) pair,
  `allocation_score = qubit*0.18 + skill_match*0.24 + readiness*0.24 + availability*0.14 +
  capacity*0.10 + cert_signal*0.06 + vendor_signal*0.04 - quality_penalty`. **Hard gate**: if trainer
  status isn't "Eligible" (compliance/delivery-risk derived), score is capped at 40 and confidence
  at 45 regardless of the formula above — gates always win over the weighted score. Produces 4
  output tables: all-pairs, best-per-course, course-level risk, top-5-ranked-per-course.
- `shared/organization_intelligence.py` — bench-depth/succession risk built on top of allocation
  output. **Team-size calibration matters here**: don't apply absolute thresholds to small teams —
  this project explicitly caps risk severity for teams ≤2 people ("Thin Bench"/Medium instead of a
  high-alarm label) after discovering the naive formula produced absurd output ("27 single-points-
  of-failure" for a 2-person team). Carry this calibration principle into any new project with
  variable team sizes.
- `shared/executive_intelligence.py` — top-of-house rollup consuming nearly every other dataset;
  computes an overall team-health label from a capped count of high-severity signals, and a ranked
  risk register (top 50) merging allocation/delivery/cert/succession/OEM risks.
- `shared/manager_recommendation_intelligence.py` — joins already-computed rows into
  manager-facing roadmap tables (coaching needs, future-skill roadmap, future-certification
  roadmap) — adds no new scoring math, just assembly + a simple `confidence = min(90, 45 +
  present_signal_count*12)`.

**Do not build (unless you have a real reason to):** this project also ships a
`backend/intelligence_engines/` package (capability/availability/readiness/recommendation/action/
trust/custom_course engines) — every function in it is an unimplemented stub returning `None`,
never imported anywhere. It's placeholder namespace for a future refactor that never happened.
Skip it in a rebuild; the real logic already lives in the modules listed above.

---

## Phase 8 — Decision objects & business services

**Goal:** convert the Phase 6-7 scored rows into the canonical "decision object" shape the frontend
actually consumes for actionable UI (rankings, blockers, recommended actions).

- `services/decision_objects.py` — the shared contract every decision-builder below depends on.
  Defines a `decision_contract_version` string (bump on any shape change — mirrors the cache-schema
  versioning principle from Phase 4), input sanitizers (`clean_text`, `clean_email`, `number`,
  `percent` clamped 0-100), `normalize_priority`/`normalize_status`/`normalize_bucket` (map
  free-text or a numeric score into a fixed enum), `stable_id(entity_type, *parts)` — a sha1 hash of
  joined parts truncated to 12 hex chars, so the **same real-world entity gets the same id across
  rebuilds** (critical for the frontend to track lifecycle state like "closed"/"escalated" across
  cache refreshes), `evidence_object` (wraps evidence + attaches which raw dataset/fields it came
  from, for the UI's "why" tooltip), and `make_decision_object(...)` — the single factory every
  builder below calls; it caps confidence if blockers exist or trainer identity is missing.
  Also contains `build_trainer_decision_objects` and a risk-register rule engine producing
  `hard_blockers` / `review_risks` / `weak_signals` / `missing_data`, each with title, severity,
  category, why, evidence, impact, mitigation.
- `services/allocation_decision_service.py` — ranks trainers per course into
  best_now/strong_alternative/backup/prep_required/not_recommended buckets, computes blockers/risk
  tags, a human-readable recommended action, and top-3 backup options per course.
- `services/manager_action_service.py` — normalizes raw "action" rows (things the manager should
  do) into decision objects: dedupe on `(action_type, trainer, course, reason-prefix)`, classify
  into a category via keyword search, score by a fixed priority map (High=90/Medium=60/Low=35).
- `services/custom_course_match_service.py` — the ad-hoc "match this new course outline to my
  trainers" feature. Two phases: (1) `parse_course_outline` — regex/keyword-parses free text into
  structured `{topics, tools, vendor, domain, level, deliveryType, complexity, confidence}`; (2)
  `build_custom_course_match_objects` — a weighted composite score combining semantic similarity
  (TF-IDF, see below), skill/tech/domain match, readiness, availability, utilization, cert signal,
  vendor strength, minus a penalty for negative feedback or hard gates. Buckets by score+readiness
  thresholds; backfills top-2 backup trainers per row.
- `services/local_ml_service.py` — a **dependency-free TF-IDF cosine-similarity engine** (no
  sklearn/numpy) purpose-built for the course-match feature: tokenizer with a couple of
  domain-specific multi-word merges (e.g. "power bi" → one token), per-document TF-IDF vectors,
  cosine similarity, returns a 0-100 similarity score plus the top matched terms for explainability.
  This is a good pattern to copy verbatim if your new project needs lightweight semantic matching
  without an ML dependency or external embedding API.
- `services/current_state_service.py` — infers "is this trainer teaching right now" purely from
  dated assignment evidence (13-format datetime fallback parser), explicitly refusing to guess
  busy/free from undated utilization indexes alone — a direct instance of "evidence over assertion."
  States: teaching_now > scheduled_today > preparing > unknown (dated evidence contradictory) >
  free > unknown (no evidence at all, low confidence).
- `services/feedback_intelligence_service.py` — extracts individual feedback Q&A rows, tags each
  with a theme (technical_depth/pace/labs/engagement/etc.) and sentiment via keyword lists, then
  aggregates per trainer into strength/improvement themes with a confidence score.
- `services/trainer_match_service.py` — currently a stub/placeholder; real matching logic
  actually lives in `custom_course_match_service.py`. Only build this if you intend to later split
  reusable trainer-fit scoring out of the course-match service.

---

## Phase 9 — Persisted manager-state (lifecycle overlays)

**Goal:** the only two places the app writes manager-input state to disk — everything else in
Phases 1-8 is recomputed fresh on every rebuild.

- `services/action_state_service.py` (`data/action_state.json`) and
  `services/review_flag_service.py` (`data/review_flag_state.json`) — near-identical pattern:
  keyed JSON store (action id / lowercased trainer email), `threading.Lock` + atomic write
  (write to `.tmp`, then rename over the target — never a partial write), an append-only `history`
  list per record, and an `apply_overlay(rows)` function that **additively annotates** already-built
  decision-object rows with `*_state`/`*_note`/`*_updated_at`/`*_history` fields **without mutating
  the underlying computed signal**. This separation (computed truth vs. manager-applied state) is
  the key design idea — copy it whenever a rebuild has "the system's opinion" and "the human's
  override" as distinct concepts that both need to survive a recompute.

---

## Phase 10 — Unified pipeline orchestrator

**Goal:** the single function that wires every phase above together into one payload per manager.
Everything before this phase is a pure building block; this is the only place they get assembled.

`backend/intelligence.py` — `build_unified(manager_email)`, the sole public entry point:
1. Call the "reportees" API for this manager; if empty, return an empty-shell payload with an
   explanatory error instead of a 500.
2. Concurrently fetch global/reference data (course list, technology, domain, unallocated
   assignments, etc.) via `ThreadPoolExecutor(max_workers=4)`, gated per-api by `is_configured()`;
   failures recorded into a `global_health` list, never raised.
3. Sequential per-trainer token pre-warm (see Phase 5's concurrency note), then concurrent
   `ThreadPoolExecutor(max_workers=6)` fan-out via `trainer_fetch_service.safe_fetch`.
4. Per-trainer loop: build operations row, actions row, timeline events, allocation rows,
   availability-engine row (with contradiction detection between calendar and capacity signals —
   see Phase 7's availability-confidence-capping note), growth intelligence, certification
   intelligence — **atomic commit per trainer**: only write results after every stage for that
   trainer succeeds; on any exception, emit a `_failed_trainer_health` row and continue to the next
   trainer. One bad trainer must never blank the whole manager's dashboard.
5. `normalize_health(health)` — dedupe, redact secrets (Phase 1's `safe_error`), standardize schema.
6. Chain ~15 engine-builder calls in the dependency order established in Phases 6-8: scoring →
   growth/cert intelligence → delivery intelligence → allocation intelligence → organization
   intelligence → executive intelligence → manager-recommendation roadmaps → feedback facts/summaries
   → availability-engine upgrade pass → decision objects (allocation, manager-action, trainer) →
   per-trainer classification (needs decision objects, so it's last).
7. Return one payload dict with ~35 named `*_df`/`*_objects` datasets, plus a few friendly aliases
   (`trainers`, `actions`, `timeline`, `data_health`) for simpler frontend consumption.

**This is the file to write last of the backend "business logic" tier and first of the "make it
run" tier** — it has no logic of its own beyond sequencing; get every phase above correct first.

---

## Phase 11 — HTTP server & routing

**Goal:** expose Phase 10's payload (and Phase 8/9's write paths) over HTTP, with auth, caching,
and static serving. No framework used here — raw `http.server`.

- `server.py` (repo root) — a thin launcher: put the backend package directory on the import path,
  import the app module, call its `main()`. Nothing else. This indirection lets backend modules use
  flat imports (`import shared.scoring`) instead of a nested package path.
- `backend/app.py` — everything else:
  - Structured logging: JSON or text formatter, stdout + rotating file handler.
  - `_validate_startup()` — checks assets dir exists, cache dir writable, credentials valid
    (Phase 1), required pages exist; **fatal** in production, warn-only in development.
  - Cache orchestration (`build_or_load_intelligence`): fresh cache → return immediately; stale
    cache → serve it **immediately** and kick a non-blocking background refresh thread (deduped via
    a lock + in-flight set so concurrent requests for the same manager don't double-build); no
    cache or forced refresh → build synchronously with up to 3 retries (1s backoff), falling back to
    stale cache if all retries fail, else propagate the error. **Never block the response on a
    rebuild if any cache — even stale — exists.**
  - Freshness metadata (`served_at`, `cache_age_minutes`, `cache_status`: live/cached/stale/
    refresh_failed) is attached without ever touching the underlying `generated_at` timestamp — so
    the frontend can always tell the user exactly how fresh their view is.
  - Routes (cookie-session auth on everything except health/login/logout):
    - `POST /auth/login` — creates session, sets cookie, returns **immediately** (does not build
      intelligence synchronously — see the "non-blocking login" decision below).
    - `GET /auth/session`, `GET /auth/logout`.
    - `GET /data/unified-manager-intelligence` — the main payload endpoint; enforces
      `requested_email == session_email` (403 on mismatch — a manager can never fetch another
      manager's data even by tampering with a query param); supports `refresh=1` and custom-course
      query params that get merged into the build.
    - `POST /rms/<api_name>` — generic relay: validates the api name is known, forwards body to
      the Phase 2 client, returns the result. **Credentials never reach the browser.**
    - `POST /api/actions/{id}/(close|escalate|reassign)`,
      `POST /api/review-flags/{id}/(acknowledge|resolve|escalate)` — persist via Phase 9 services,
      then feed the outcome into the Phase 13 learning loop (escalate = negative signal, resolve =
      positive signal).
    - `POST /api/agent/ask|feedback|tune`, `GET /api/agent/briefing|learning` — Phase 13 surface.
    - `GET /api/refresh/status|logs|last-success`, `POST /api/refresh/run` — Phase 12 surface.
    - `GET /api/knowledge/*` — reads JSONL files from the refresh service's knowledge-base output.
    - `GET /healthz` — no auth; liveness + active session count.
    - anything else GET → Phase 4's static resolver; 404 if nothing matches.
  - All JSON responses set `Access-Control-Allow-Origin: *` (adjust if you need real CORS
    restrictions) and `Cache-Control: no-cache`; `do_OPTIONS` handles preflight.
  - `main()` — logging setup, startup validation, register mimetypes, start the background-refresh
    thread (Phase 12), then run `ThreadingHTTPServer` on `("", PORT)` — default port **8765**.

**Decision worth keeping verbatim:** login used to synchronously build the whole intelligence
payload (multi-minute spinner, could 401 on a flaky upstream). It was changed to issue the session
cookie immediately and let the dashboard's first data fetch trigger the (now non-blocking,
stale-cache-tolerant) build. Never make an auth endpoint depend on an expensive downstream build.

---

## Phase 12 — Refresh scheduling

**Goal:** keep each manager's cache warm automatically instead of relying only on request-triggered
rebuilds, and build a lightweight knowledge base for the agentic layer (Phase 13) as a side effect.

`services/refresh_service.py`:
- File-backed state (`runtime/refresh/refresh_state.json`, `refresh_logs.jsonl`) and a
  knowledge-base output (`runtime/knowledge_base/` — snapshots + per-entity `.jsonl` files:
  entities, trainer_profiles, course_matching, risk_signals, action_recommendations,
  manager_decisions, refresh_snapshots).
- `refresh_once(email, build_fn, force=True)` — guarded by a module-level lock + running flag so
  only one refresh runs at a time; concurrent calls get a `"Duplicate"` status instead of queuing or
  racing. On run: mark in-progress → call the injected `build_fn` (Phase 10's `build_unified`) →
  write a timestamped snapshot → derive knowledge-base JSONL from the payload → mark
  success/failed → append a log line either way.
- `next_scheduled_run = last_success + 24h`; `poll_due(email, build_fn)` triggers a refresh if one
  has never run or 24h have elapsed — this is the cron-like mechanism; call it from a background
  thread loop started in `app.py`'s `main()` (this project sleeps 15 minutes between polls).

---

## Phase 13 — Agentic assistant layer

**Goal:** a chat-style "ask a question about my team" surface, built **without** an LLM — entirely
rule-based, evidence-backed, with one explicit seam left for a future model — plus a genuine
closed-loop learning mechanism that adjusts scoring weights from manager feedback.

- `agentic/tools.py` — pure retrieval functions over the unified payload (`ctx`): `list_trainers`,
  `get_trainer`, `best_for_course`, `backups_for_course`, `unallocated_demand`, `list_blockers`,
  `list_manager_actions`, `data_health`, `certification_gaps`, `oem_bench`, `future_roadmap`,
  `search_kb` (substring search over the Phase 12 knowledge base, capped rows). No I/O of its own.
- `agentic/agent.py` — **deterministic, not an LLM call.** `classify_intent(question)` does
  keyword/substring matching against a fixed set of intents (compare, backup, free, blockers,
  certification, health, demand, oem, actions, learning, best, team). `_extract_trainer`/
  `_extract_course` do regex/substring entity extraction against the payload. `answer(question,
  ctx)` dispatches intent+entity to the matching `tools.*` function and formats a canned response
  string, returning `{question, intent, answer, confidence, tool_used, sources, result}`. Document
  this honestly in your UI as "deterministic, not live AI" if you build it this way — this project
  does, and it's the right call when there's no LLM credential in the environment. `answer()` is
  explicitly the single seam where a real model provider would be plugged in later — keep that
  seam narrow and obvious if you build this pattern.
- `agentic/context.py` — merges the unified payload + knowledge base + learning status into the
  `ctx` dict `agent.py`/`tools.py` consume.
- `agentic/learning.py` — closed feedback loop with **no ML library dependency**: a hand-rolled
  linear weight-nudge over a small fixed set of signal dimensions (in this project: qubit,
  assignments, certs, utilization, feedback, tech), persisted to
  `runtime/learning/{feedback.jsonl, model_weights.json}` (atomic write, versioned). Every manager
  action (close/escalate/reassign an action; acknowledge/resolve/escalate a review flag) is recorded
  as a labeled example (`record_feedback`); `tune_weights()` nudges each present feature's weight
  by `direction * learning_rate * clamp(value, 0, 1)`, clamps every weight to a floor/ceiling, and
  renormalizes so weights sum to 1, bumping a version number only if there were new examples.
  This is a nice, cheap pattern for "the system gets slightly better calibrated over time" without
  standing up any ML infrastructure — copy it if you want a learning loop but don't want the
  complexity of a real model.

---

## Phase 14 — Frontend foundation

**Goal:** the shared client and shell every page loads before its own page-specific script.

- `frontend/js/api.js` — the only place that talks to the backend. One function per endpoint (see
  the endpoint table below), all through `fetch(..., {credentials:'same-origin'})` so the session
  cookie rides along. `isAuthError`/`handleAuthFailure` centralize 401/403 handling: clear local
  session state, show a "session expired" banner, redirect to login after ~1.8s. Client-side caching
  layer on top of `sessionStorage`: team data cached 4h per-manager-email key, course list cached
  indefinitely, in-flight de-duplication so concurrent calls to the same endpoint share one promise
  instead of firing twice. `warmWorkspace(email)` is called right after login to pre-fetch
  everything the dashboard will need, in parallel, before the user ever sees a loading spinner.

  | Frontend function | Endpoint |
  |---|---|
  | `login(email)` | `POST /auth/login` |
  | `session()` | `GET /auth/session` |
  | `logout()` | `GET /auth/logout` |
  | `call(apiName, body)` | `POST /rms/<apiName>` |
  | `getUnifiedManagerIntelligence(opts)` | `GET /data/unified-manager-intelligence` |
  | `updateActionState(id, verb, body)` | `POST /api/actions/{id}/{verb}` |
  | `updateReviewFlagState(id, verb, body)` | `POST /api/review-flags/{id}/{verb}` |
  | `agentAsk/agentBriefing/learningStatus/learningFeedback/learningTune` | `/api/agent/*` |
  | `runRefresh()/getRefreshStatus()` | `/api/refresh/*` |
  | `searchKnowledge(q)` | `GET /api/knowledge/search` |

- `frontend/js/app.js` — loaded on every page after `api.js`. An auth-guard IIFE redirects to
  login if no session; `injectLayout(activeNav)` builds the sidebar/header/nav from a single
  `MENU_MODEL` + `BUILT_PAGES` set (unbuilt nav links route to a generic "coming soon" page instead
  of 404ing); shared UI helpers (toast, loader, score-to-badge-color, progress bars, date
  formatting); a notification-bell populator that derives counts from the unified payload
  (critical/high actions, open review flags, blocked allocations, cert gaps, cache staleness).
- `frontend/pages/login.html` — standalone (loads only `api.js`, not the shared layout). Flow: on
  load, silently check for an existing valid session and redirect straight in if found; on submit,
  clear stale client caches, call login, then immediately call the unified-intelligence endpoint
  once to confirm the manager actually has reportees before redirecting to the dashboard (fail loud
  here rather than showing an empty dashboard).

**Stack note:** no framework, no build step, no `package.json`. Plain `<script>` tags, jQuery
(bundled with the admin theme), hand-written IIFE modules. This is a legitimate choice for an
internal tool at this scale — don't introduce a framework/bundler in the rebuild unless there's a
concrete reason to.

---

## Phase 15 — Frontend pages

Build these last, once Phase 14's client exists — each is one HTML file that mounts inside the
shared layout and renders from one call to `getUnifiedManagerIntelligence()`, plus page-specific
mutation calls where relevant.

| Page | Purpose | Key calls |
|---|---|---|
| `index.html` | Command center: attention queue, utilization/readiness/pipeline charts, delivery calendar, hosts the agent copilot chat | `getUnifiedManagerIntelligence` |
| `team.html` | Filterable/sortable trainer roster with KPI strip | `getUnifiedManagerIntelligence` |
| `trainer-intelligence.html` | Single-trainer cockpit; manager can act on decisions/flags for this trainer | `getUnifiedManagerIntelligence`, `updateActionState`, `updateReviewFlagState` |
| `trainer-detail.html` | Legacy-URL redirect shim → `trainer-intelligence.html?email=...` | — |
| `actions.html` | Decision inbox over `manager_action_objects` | `getUnifiedManagerIntelligence`, `updateActionState` |
| `allocation-desk.html` | Course→trainer allocation board | `getUnifiedManagerIntelligence` |
| `capability-builder.html` | Workforce growth/upskilling planning board | `getUnifiedManagerIntelligence` |
| `certifications.html` | OEM accreditation gap view | `getUnifiedManagerIntelligence` |
| `custom-course-match.html` | Submit a new course outline, get ranked trainer matches | `getUnifiedManagerIntelligence` with course params |
| `data-health.html` | Which datasets are healthy/partial/missing, cache freshness | `getUnifiedManagerIntelligence` |
| `risk-takers.html` | Open compliance/delivery review-flag queue | `getUnifiedManagerIntelligence`, `updateReviewFlagState` |
| `settings.html` | Force refresh, rewarm caches, clear token cache | various |
| `coming-soon.html` | Generic placeholder for unbuilt nav items (`?title=`) | none |

`frontend/deprecated/*` — legacy URLs kept alive as instant-redirect shims (both a meta-refresh
and an inline `location.replace()` for reliability) so old bookmarks/links never 404 after a page
gets renamed or merged.

---

## Phase 16 — Testing

`tests/smoke_test.py` — a single end-to-end script run against a **live** server (`python
server.py` first, then `python tests/smoke_test.py`). It checks, in order: backend modules import
cleanly; every active frontend page file exists and contains no direct-RMS-call anti-pattern;
`GET /healthz` returns 200; unauthenticated calls to the unified-intelligence and RMS-relay
endpoints are rejected; login succeeds and sets a session; an authenticated forced-refresh call
returns 200 with all required top-level dataset keys and required per-row fields present (including
a hardcoded specific-trainer contract check); the custom-course-match endpoint behaves correctly
both with and without an outline; cross-manager email queries are rejected; a non-refresh call
returns cached data. Write this test **after** Phase 11 is functional and use it as your own
definition of done for the whole backend before moving to frontend polish.

---

## Phase 17 — Deployment

- Single Python process (`python server.py`), no framework/container required — this project runs
  bare, fine for the target scale ("tens of reportees per manager, not hundreds of managers").
- Two `SKILLEDGE_ENV` modes: `development` (default, permissive) vs `production` (all ~19×2
  credential env vars required or the process refuses to start).
- No TLS built in — put nginx/Caddy in front for HTTPS and rate limiting; sessions are in-memory
  and single-instance, so there's no load-balancing story without moving sessions to shared storage
  first (a legitimate future improvement, not implemented here).
- Logging: stdout + rotating file (`runtime/logs/`, 10MB × 5 backups), text or JSON format.
  Backup targets before any deploy: `runtime/cache/` (avoids a 30-120s cold rebuild), `runtime/logs/`,
  and never commit `.env`.
- Rollback plan: stop, `git checkout` the previous commit, optionally clear `runtime/cache/*.json`,
  restart, then re-run the Phase 16 smoke test against the live server as your deploy verification.

---

## Explicit non-requirements for a rebuild

Things present in this repo that you should **not** treat as required architecture:

- `backend/knowledge/*` (certification_graph, course_graph, technology_graph, trainer_graph) —
  thin normalization helpers, zero cross-imports, not consumed anywhere observed in this codebase.
- `backend/intelligence_engines/*` — 7 files, each a single stub function returning `None`, never
  imported. Placeholder namespace for a refactor that never happened.
- A real LLM integration — there isn't one; the "AI copilot" is Phase 13's rule engine.
- Multi-instance/horizontal scaling, a database, or a message queue — none exist; the design is
  intentionally a single process with a filesystem cache, appropriate to the current scale.
