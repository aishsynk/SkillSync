# SkillEdge — API and Version Audit (2026-08-10)

Read-only audit of three surfaces:
1. The 37 RMS API documents in `trainer_portal_api_details/`.
2. The Flask backend in `backend.py` (and `action_store.py`).
3. The Android consumer in `SkillEdge_Android/app/src/main/java/com/example/skillsync/`.

Source-of-truth follow-ups (PROGRESS.md) were already updated through v3.1.1; this document is a separate, deeper review of the *contract* and *consistency* layer, not a release blocker. No code was changed.

---

## Part 1 — RMS API Catalogue Observations

### 1.1 Transport layer (shared by all 37 endpoints)

- **Base URL**: `https://api.koenig-solutions.com`.
- **Two-step auth** for every endpoint:
  1. `POST /api/Kites/Operator/GetToken` with `{userName, userPassword, userRole}` → returns `{accessToken, deviceToken}`.
  2. `POST /api/Kites/Operator/common?apikey={key}&accessToken=…&deviceToken=…` with the endpoint-specific JSON body.
- All 37 endpoints ride the same envelope: `{statuscode, message, content}` — `content` is either a list or a stringified JSON.
- Every endpoint has a **dedicated, hardcoded username/password/role triplet** used only for `GetToken`. These are plaintext credentials stored in `trainer_portal_api_details/*.txt`. The current backend caches them in `_APIS` in `backend.py`.

### 1.2 Cross-cutting API problems

| # | Issue | Severity |
|---|-------|----------|
| A | **Plaintext credentials in repo**: 37 username/password pairs live in the documentation directory. Anyone with repo access can mint a session for every API. | **Critical** |
| B | **Same `GetToken` per endpoint**: each API has its own credential triplet, so the backend must call `GetToken` 37 times in the worst case. There is no documented rate limit; the backend caps at `timeout=6, attempts=1` per call. | High |
| C | **All endpoints are `POST`**, including read-only catalogue and reporting calls. This burns an extra request/response hop for every read, and prevents caching at intermediaries. | High |
| D | **`content` is sometimes a string, sometimes a list**: 12 of 37 endpoints document `"content": "[...results...]"` — a literal string placeholder, not a parsed schema. The client cannot know the field shape without a live probe. | High |
| E | **Field-name typos in RMS contracts**: `TrainerEmailAddres` (Assignment_API, key 15), `StarDate` (Previous_&_Upcomming, key 16), `Languagee` (Trainer_availability, key 90), `InternationaRoamingOffDates` (Get_Trainer_Details, key 75), `Shedule` (Get_Trainer_Free_Shedule_and_Details, key 171), `Upcomming` (Previous_&_Upcomming, key 16), `Whitout` (Course_Whitout_Exam, key 213), `Vender` (Get_trainer_Vender_Certification_Count, key 57). Every client must either carry the typo or alias it. | High |
| F | **Field keys with embedded spaces**: `Positive Count`, `Negative Count`, `Future Skill Date`, `Exam Required or Not`, `Course Available in RMS`, `Course Status`, `Total Fee`, `Is Future Skill`. JSON parsers preserve them, but mobile keys and database columns cannot. | Medium |
| G | **Pagination is undocumented**: only Assignment_API (key 15) and Get_Active_SC_Date (key 13) accept `PageNumber`/`PageSize`. No file documents total count, max page size, or whether pages are zero- or one-indexed. | Medium |
| H | **No documented error codes**: no file describes what RMS returns for 4xx/5xx or for "no rows" vs "auth failed" vs "rate limited". Clients must probe. | Medium |
| I | **Empty body for catalogue endpoints**: Course_List (164), Course_Whitout_Exam (213), Course_&_Technology_List (114), Get_Course_Name (70), Unallocated_Assignment (190), Get_Course_Syllabus_TOC (248). This forces the server to return the entire catalogue on every call, with no filter — a 8,816-row payload for Course_Name. | High |
| J | **Duplicated functionality across keys**: there are *five* "get assignments" sources (15, 16, 93, 111, 190, 209) with overlapping but not identical schemas. The current backend picks one (Previous_&_Upcomming, key 16) and uses Assignment_API (key 15) as a fallback. The other three are dormant. | Medium |
| K | **One write endpoint**: Add_Trainer_Skill (255). HTTP verb is implied, the response is a status GUID rather than a structured object. The current backend's `mark_skill` only treats `verified==true` as success — which is the right behaviour, but the contract makes it hard to debug. | Medium |
| L | **Two unverified contracts are advertised**: Get_Latest_Version_Of_Courses (172) and Get_Course_Content_URL (156) have no documented schema. Per `AI/PROGRESS.md` (v1.55 entry), live probes returned zero rows. | Low |

### 1.3 Categorisation of the 37 endpoints

| Group | APIs | Notes |
|-------|------|-------|
| Trainer profile | Get_Trainer_Details (75), Get_Trainer_Skills (217), Trainer_Resume_Details (87), Get_Direct_Indirect_Reportee (82), Trainer_availability (90), Add_Trainer_Skill (255), Get_Trainer_Free_Shedule_and_Details (171) | One write (255), one with date filter (90), all the rest by email. |
| Course catalogue | Course_List (164), Get_Course_Name (70), Get_Course_and_Domain (205), Course_&_Technology_List (114), Course_Whitout_Exam (213), Exam_Course_Linked_API (215), Get_Course_Schedule (246), Get_Course_Module (206), Get_Course_Syllabus_TOC (248), Get_Course_Content_URL (156), Get_Latest_Version_Of_Courses (172), Check_Course_Availability_in_RMS (104), Get_Inhouse_and_FL_Trainers_Of_Courses (157) | 13 APIs, all but two return the entire catalogue. |
| Demand & schedule | Assignment_API (15), Upcoming_Assignments (93), Previous_&_Upcomming_Assignments (16), Trainer_RC_Schedule (111, from `Check Course Availability in RMS.txt`), Unallocated_Assignment (190), Get_Active_SC_Date (13), Get_SCID (173), Get_Assignment_pax (209), Get_Recording_Details_by_Assignment_Id (278) | Five overlapping "assignments" sources. |
| Feedback | Get_Trainer_Feedback_Details (244), Get_Negative_Feedback_Count (58), Get_Trainer_Negative_Feedback (218) | Three for one feature. |
| Certifications | Get_trainer_Vender_Certification_Count (57), Get_Unique_Certifications_Count_Value (72) | Key 72 is unverified (always returns zero rows). |
| HR / Incident | Get_HR_Incident_Positive_Negative (59) | Only one. |
| Manager hierarchy | Get_Direct_Indirect_Reportee (82) | Overlaps with Trainer profile. |
| Utilisation | Get_Utilization (55), Trainer_Last_3_Months_Utilization (39) | Two for one feature. |

### 1.4 Improvements for the API surface (upstream recommendations)

> These are *upstream* changes the SkillEdge team cannot make alone, but they should be on the table when engaging with the RMS team.

1. **Single sign-on / token exchange**: replace 37 username/password triplets with one OIDC or mTLS identity. Endpoints then need only a Bearer token.
2. **Move reads to `GET`** with query parameters, so HTTP semantics match and intermediaries can cache. Most read-only contracts (course catalogue, utilisation, trainer details, feedback) can be `GET` without body.
3. **Versioned, typed responses**: every endpoint declares a `schema_version` and a documented JSON schema (OpenAPI/JSON Schema). The 12 placeholder `"content": "[…]"` contracts become real.
4. **Pagination metadata**: `{rows, total, page, page_size}` envelope, with a documented max page size.
5. **Standardised error envelope**: `{code, message, retryable, trace_id}`. Map RMS-level failures to this once.
6. **Fix field-name typos** server-side; alias them client-side in the meantime.
7. **Deduplicate the assignments API** to one canonical "Assignments" with status filters (`previous | upcoming | unallocated`).

---

## Part 2 — Backend (`backend.py`) observations

### 2.1 The biggest problem: **unauthenticated PII and writes**

Every legacy route under `/api/data/...`, `/api/action/...`, and `/api/actions/...` is keyed only by `?email=…` and performs **no session check**. The V2 routes under `/api/v2/...` *do* require a Bearer session and enforce manager-scope.

The leaked surface, with severity:

| Route | Auth | What it leaks |
|-------|------|---------------|
| `GET /api/data/unified-manager-intelligence` | **none** | Full team KPIs, utilisation, feedback counts, demand list, action objects for any Koenig manager email. |
| `GET /api/data/manager-profile` | **none** | Photo URL, designation, tenure, languages, summary, certifications, clients count. |
| `GET /api/data/trainer-360` | **none** | Full trainer profile including `clients[:24]`, experience blob, summary, feedback responses, assignment history, participant rosters (`participants[]` from key 209). |
| `GET /api/data/allocation-desk` | **none** | Global unallocated demand list with rankings, candidate scoring, full candidate pool. |
| `GET /api/data/trainer-skills` | **none** | Skill register per email. |
| `GET /api/data/trainer-utilization-history` | **none** | 3-month utilisation per email. |
| `GET /api/data/course-syllabus` | **none** | Per-course syllabus URL. |
| `GET /api/data/course-search` | **none** | Per-query course catalogue (8,816 rows). |
| `GET /api/data/course-intelligence` | **none** | Per-course schedule dates. |
| `GET /api/data/alternative-trainers` | **none** | Per-course wider trainer network. |
| `GET /api/actions`, `POST /api/actions`, `POST /api/actions/<id>/state`, `POST /api/actions/<id>/note` | **none** | Manager inbox (read, raise, state change, notes). |
| **`POST /api/action/mark-skill`** | **none** | **Writes to production RMS** (`addTrainerSkill`, key 255). Only gate is `@koenig-solutions.com` on `trainer_email`. |

The same handlers are also bound under `/api/v2/...` paths and only the V2 paths enforce session + scope. That asymmetry is itself a bug: a client that calls `/api/data/allocation-desk?email=anyone@koenig-solutions.com` will succeed; the same client calling `/api/v2/operations/demand-context?manager=anyone@koenig-solutions.com` will get 401/403.

**This is a release blocker for a production rollout to anyone other than the current named-test manager.**

### 2.2 Version split is half-built

- `/api/v2/...` enforces Bearer session + manager-scope match.
- `/api/data/...`, `/api/action/...`, `/api/actions/...` accept email as a parameter with no session.
- `/api/auth/login` is unversioned (predates the split).
- The `unified-manager-intelligence` dashboard is V1 only — there is no V2 peer.
- `/api/v2/actions/<id>/audit` is V2-only — there is no V1 peer, which breaks the "v1 and v2 share a handler" pattern.
- Three handlers are bound to both `/api/data/...` and `/api/v2/...` with auth gated on `request.path.startswith("/api/v2/")` — fragile coupling.

### 2.3 Error-code inconsistency

- 401 is used for "no session", "wrong domain", and "role lookup failed". Mixing auth and authz.
- 403 is used *only* on V2 routes for `MANAGER_SCOPE_MISMATCH`. Never on V1 routes because V1 has no scope check.
- 404 is the global handler only; no route returns 404 explicitly (e.g. `course-syllabus` returns 200 with `found: False`).
- 409 is used *only* by `mark_skill` for "refused" / "absent after write".
- 422 is never used.
- 503 is used on most RMS-touching routes for "Cannot reach RMS"; but `unified-manager-intelligence` returns 200 with empty KPIs and `alternative-trainers` returns 200 with `available: false`. Inconsistent failure presentation.

### 2.4 Misleading / hardcoded / dead values

| Symptom | Where | Why it matters |
|---------|-------|----------------|
| `from_cache: False` always, even when served from `_CACHE_TTL`. | `unified_intelligence()` line 2541 | Client can't tell cached from live. |
| `cache: {age: 0, ttl: 3600, source: "rms_live"}` always literal. | line 2578 | Same problem, twice. |
| `future_skill_roadmap_df: []`, `data_health_df: []` empty placeholders. | lines 2539–2540 | Always-empty blocks surface in clients as "no data" without explanation. |
| `responses_raw_sample: fbdet_raw[:2]` exposed temporarily. | trainer-360 line 3119 | Documents that the trainerFeedback contract has not been live-verified for field names. |
| `recordingDetails (278)` registered in `_APIS`, no route uses it. | `_APIS` 220–225 | Dead config. |
| `uniqueCerts (72)` always returns zero rows; not exposed. | header comment | Correctly dropped, but a paper trail that this is genuinely unavailable. |
| `_AISHWAR_EMAIL = "aishwar_v@koenig-solutions.com"` literal. | line 1255 | Narrow business rule for one named account. Should be a configurable recommendation rule, not a hardcoded email. |
| `_SESSION_STORE` (`_sessions`) is in-process memory. | login, validate_session | Lost on every redeploy; not horizontally scalable. |
| `_TOKEN_CACHE` (per-API access tokens) is in-process. | `_rms` helper | Same — lost on restart, can clash across instances. |

### 2.5 Dead / unreachable code

- `login()` returns 503 only when `_verify_role` returns `"rms_error"`; `_verify_role` never returns that — it returns `("manager", list)` or `(None, None)`. The 503 branch is unreachable.
- `_verify_role` is documented as "Manager or Trainer Plus role" but actually grants `("manager", …)` to *every* Koenig email. The docstring lies.
- `Trainer_RC_Schedule` (key 111) is documented in `Check Course Availability in RMS.txt` despite the filename; never wired into any route.

### 2.6 Backend improvements (concrete, ranked)

1. **Close the auth gap**: move every `/api/data/...` and `/api/action/...` route behind the V2 session+scope helper. Either (a) deprecate V1, or (b) make V1 require session too — but pick one and document it. The current state is "PII by email" and is the highest-risk item.
2. **Rotate RMS credentials**: move all 37 username/password pairs out of `_APIS` into environment variables / a secret store. Audit which are still live.
3. **Standardise error envelope**: a single `error_response(code, message, http_status)` helper. Codes: `EMAIL_REQUIRED`, `INVALID_EMAIL`, `INVALID_DEMAND_ID`, `INVALID_COURSE_NAME`, `SESSION_REQUIRED`, `MANAGER_SCOPE_MISMATCH`, `RMS_UNREACHABLE`, `NOT_FOUND`, `CONFLICT`.
4. **Remove the dead 503 branch** in `login()`; align `_verify_role` with its docstring or rewrite the docstring.
5. **Fix misleading cache metadata**: `from_cache` should reflect `_cache`; `cache.age` and `cache.source` should reflect reality. A cached response should advertise it.
6. **Drop placeholder keys**: `recordingDetails (278)`, `responses_raw_sample`. Either wire them up or remove the scaffolding.
7. **Decouple `_AISHWAR_EMAIL`** from a literal — move the rule to a config flag with an explicit opt-in.
8. **Make V1/V2 a real contract split**: either a versioning decorator (`@v2_required`) or a separate `v2_routes.py`. The current "same handler, different auth" coupling is hard to reason about.
9. **Add `/healthz` schema with `dependencies`**: list which RMS contracts are reachable now, and per-cache TTL status. A single boolean hides too much.
10. **Replace the `/` hand-written endpoint list** with auto-generation from the routes — the current list omits `/api/v2/...`, `/api/auth/logout`, etc.

---

## Part 3 — Android observations

### 3.1 Live-vs-cache races

- `MainScreenViewModel.adoptBackgroundSync` (lines 153–167) replaces `_uiState`, `_profile`, `_capability`, `_teamActions` from disk **without consulting the in-memory age**. A background WorkManager run can overwrite a fresh foreground fetch if the disk file changed during a network flap.
- `AllocationViewModel.adoptBackgroundSync` (lines 163–173) replaces `_state.value` and recomputes `SeenBatches`. A background sync can flip a batch from "seen" to "unseen" mid-tab.
- `AllocationViewModel.fetch` polls every 3s while `loading == true` and overwrites `_state.value = AllocationState.Success(data)` on the first non-loading response, **without a version comparison** against the previous snapshot.
- Three independent ViewModels (`MainScreenViewModel`, `ActionsViewModel`, `Trainer360ViewModel`) all read `actions_<email>` and parse `body["actions"]` independently with different filters. No shared `ActionRow` type.
- The "offline" signal is computed two different ways: `RetrofitClient.isNetworkAvailable(context)` (validated internet) in `fetchDashboard`, and `SyncScheduler.online` (capability callback) in the banner. They can disagree during a flap.

### 3.2 State machines are inconsistent

- `LoginState`, `DashboardState`, `AllocationState`, `Trainer360State` are four near-identical sealed classes. `AllocationState.Success` lacks `fromCache`/`cachedAt`, so the Demand tab cannot show "showing saved data from 3 hours ago".
- `ActionsViewModel` does not use a sealed class — it uses three separate `StateFlow`s (`initialLoading`, `actions`, `error`).
- `CopilotViewModel` uses a `List<ChatMessage>` with `Loading/Error` mixed in (chat idiom).
- `Error` is unreachable for many screens after the first successful load (`fetchDashboard` only sets Error if no cached Success exists). This is *good* behaviour for a manager, but inconsistent with how `DashErrorView` is rendered for first-load failures — no stale-error state.

### 3.3 Fake-payload hazard

`AllocationViewModel.loadCourseIntelligence` (lines 117–122) replaces a failed call with a hand-built `mapOf("course_name" to …, "schedule_dates" to emptyList(), "schedule_available" to false, "note" to "Course schedule could not be verified.")`. The downstream `CoursesTab` cannot distinguish "no schedule in RMS" from "request failed", and the typed `CapacityPlanResponse` is unused for this path even though it shares intent. **This is the single highest-severity client bug**: the screen will lie about whether the data is real.

### 3.4 Field-name typos carried silently

Verified by reading `SkillEdgeApi.kt`:
- `MarkSkillRequest` uses snake_case Kotlin field names (`course_id`, `trainer_email`, `skill_level`, `from_date`, `officially_approved`). Gson serialises verbatim — the wire keys *are* snake_case, so the Android inventory's "silent rejection" concern does **not** apply here.
- `LoginRequest` is `{email: String}` — one lowercase field, matches backend.
- `MarkSkillResponse` mixes bare Kotlin names (`success`, `verified`, `changed`, `trainer_email`, `course_name`, `message`) with `@SerializedName`-annotated fields (`already_held`, `rms_status`, `rms_message`). Bare names work because Gson uses the property name verbatim and the backend returns those literal keys.
- `DemandContextResponse`, `CapacityPlanResponse`, etc. use `@SerializedName` correctly.

**The inventory was wrong about MarkSkillRequest and LoginRequest silently failing** — Gson does the right thing for snake_case Kotlin names. The risk that *was* real was a category error in the audit, not in the code.

### 3.5 Dead endpoints declared in the Retrofit interface

Three endpoints have Kotlin declarations but no caller:
- `/api/auth/session` (line 24)
- `/api/data/trainer-skills` (line 89)
- `/api/v2/actions/{id}/audit` (line 154)

Either wire them up (the audit endpoint in particular would be useful in `ActionsInbox`) or remove them. Keeping them declared increases maintenance surface.

### 3.6 Error handling — no shared helper

Each ViewModel rolls its own:
- `LoginViewModel` maps 401/503/400/else to a human message.
- `MainScreenViewModel` passes `e.localizedMessage` through with one-line overrides.
- `AllocationViewModel` passes through, plus a special-case offline string for multi-trainer write.
- `ActionsViewModel` wraps every error in `"Could not <verb>: <message>"`.
- `CopilotViewModel` uses `e.message` bare.

A `Throwable.userMessage(context, verb)` helper would dedupe this. The `fetchProfile` and several secondary fetch paths swallow errors silently — that's appropriate for "secondary data" (util history, syllabus) but is wrong for identity, and `loadCourseIntelligence`'s synthesised payload is a bug.

### 3.7 Theme tokens mostly used; some ad-hoc literals

178 `MaterialTheme.skill.*` references, 44 hardcoded `Color(...)` literals.

- `AllocationDeskScreen` (lines 667, 677, 1128, 1139, 1145, 1151) — `Color.White` for badge text, hardcoded `Color(0xFF071523)` for the hero counter text.
- `CopilotChatSheet` (lines 179–181, 188–189) — raw `Color(0xFFE57373)`, `Color(0xFF81C784)`, `Color(0xFFFFB74D)` for confidence badges.
- `DashboardSections` line 507 — `Color(0xFFFF8A9B)` for crit critical; line 264–265 — `Color(0xFFF44336)` for the Logout row.

The shape tokens are inconsistent: `Radii` defines `hero/card/kpi/chip/icon`, but `RoundedCornerShape(14.dp)` and `RoundedCornerShape(8.dp)` literals appear inline. `Space` is defined but largely unused — most padding is hardcoded.

### 3.8 Notification destinations are stringly-typed

`NotificationDestinationStore` uses `type` strings (`"demand"`, `"demand_list"`, `"trainer"`, `"actions"`). A typo silently falls into `else -> Main(email, HomeTab.DASHBOARD)`. Same for `HomeTab.DASHBOARD = "today"` constants — concentrated in one file, safe to refactor.

### 3.9 Android improvements (ranked)

1. **Fix `AllocationViewModel.loadCourseIntelligence`'s fake payload**. Either propagate an error state, or model the "unverified" state in a typed `CourseIntelligence.Unverified` variant.
2. **Version the cache swap in `adoptBackgroundSync`**. Add a `lastAdopted` timestamp; only adopt if the persisted revision is newer than what's in memory.
3. **One state-machine interface**. `sealed interface UiState<T> { Loading; Success(data, source: FromCache | FromNetwork, cachedAt); Error(throwable) }`. Adopt it everywhere; deprecate the four bespoke sealed classes.
4. **One shared `Throwable.userMessage(verb)` helper**. Replace 8 ad-hoc error mappers.
5. **Drop dead Retrofit declarations** (`/api/auth/session`, `/api/data/trainer-skills`, `/api/v2/actions/{id}/audit`) or wire them up.
6. **Move all hardcoded `Color(...)` and `RoundedCornerShape(N.dp)` literals** into `theme/Color.kt` and `theme/Surfaces.kt`. `Space` token should be the default.
7. **Adopt a typed `ActionRow`** in a shared file so `MainScreenViewModel`, `ActionsViewModel`, `Trainer360ViewModel` parse the same cache the same way.
8. **Promote `HomeTab` and `NotificationDestination.type` to sealed classes**. A typo today silently routes to the dashboard.
9. **Wire `/api/v2/actions/{id}/audit`** into `ActionsInbox` so the manager can see the audit trail — the backend exposes it, the client ignores it.
10. **Unify the offline signal**. Pick `RetrofitClient.isNetworkAvailable` *or* `SyncScheduler.online`, not both.

---

## Part 4 — Cross-surface improvements (architecture)

### 4.1 Treat the V1/V2 split as a contract, not a route

The current pattern "same handler, different auth, branch on path" is a contract leak. Either:
- (a) Make V1 session-required too (simplest; matches the security goal). Deprecate `?email=…` as the only identifier.
- (b) Make V1 explicitly deprecated, schedule a removal date, ship the V2 routes as the only path.

Either way, document the contract: `/api/v2/...` is the supported surface; V1 exists for backward compatibility only and will be removed.

### 4.2 A typed contract layer between backend and Android

Currently the Android side parses JSON by string keys (`ops.str("trainer_email")`). The backend has no OpenAPI / JSON Schema. Both sides drift independently.

Recommendation: a shared `contracts/` folder (single source) that both:
- The backend can validate responses against (e.g. `jsonschema`) before returning, and
- The Android Gradle build can use to generate Kotlin models from (`gradle.openapi-generator`, or a thin codegen).

Cheap version: a single `contracts/README.md` per endpoint that lists the snake_case keys, their types, and which are nullable. Then have a CI check that the backend response shape matches the contract for at least the V2 routes.

### 4.3 Audit trail as a first-class concept

The action store already has `action_events`. Extend the same pattern to:
- Skill writes (`add_trainer_skill` → audit row in `skill_events`).
- Login / logout (`auth_events`).
- Demand context loads (`demand_context_events`).

Then surface all of them in a manager audit view. This turns "we wrote to RMS" into "we have a record of who wrote what when".

### 4.4 Manager-scope enforcement at the row level

The V2 routes enforce manager-scope on the URL parameter (`?manager=…` must equal the session email). They do *not* enforce it on row-level data: a manager's team capability portfolio is filtered by team membership on the way in, but if any downstream API returns another manager's reportees, those would leak through.

Recommendation: every response row that has a `manager_email` field should be filtered against the session email before being returned, with a clear `removed_due_to_scope: <count>` field in the response so the client can audit.

### 4.5 Cache TTL as a contract

The backend has `_CACHE_TTL` constants scattered around (1800, 3600, 21600 seconds). Each constant should have a documented rationale (why 6h for course catalogue but 30m for trainer details?). A single `CACHE_TTLS.md` table would make it auditable.

### 4.6 Make the offline mode a first-class state

Today, offline is detected two ways and presented differently in different screens. Promote it to a single `ConnectivityState` (Validated / Captive / Offline / Unknown) and have every screen react to it through the same component (banner, dot, offline-mode card).

### 4.7 Typed errors with retry semantics

The backend returns `503` for "RMS unreachable" and 200-with-`available: false` for the same condition on other routes. Introduce a single `rms_status: "reachable" | "rate_limited" | "timeout" | "auth_failed" | "unknown"` field on every RMS-backed response, and a typed `Retry-After` header where applicable. Then the Android client can implement one retry policy.

---

## Part 5 — What to ship first (ranked by ROI)

| Rank | Item | Impact | Cost | Source |
|------|------|--------|------|--------|
| 1 | Move every V1 read route behind session auth. | Closes the PII leak. | Small — add the V2 helper to V1 routes. | §2.1 |
| 2 | Move `mark_skill` behind session auth. | Closes the RMS-write leak. | Small. | §2.1 |
| 3 | Rotate RMS credentials out of `_APIS` into env vars / secrets. | Removes plaintext credentials from the repo. | Small — read once at startup, fail loud if absent. | §2.6 |
| 4 | Fix `AllocationViewModel.loadCourseIntelligence` synthesised payload. | Removes a screen that lies about its data. | Trivial. | §3.3 |
| 5 | Standardise error envelope and codes in the backend. | Makes every screen's error path uniform. | Medium — touches every route. | §2.3 |
| 6 | Unify the V1/V2 split into a clear contract. | Removes "same handler, different auth" coupling. | Medium. | §2.2 / §4.1 |
| 7 | Replace hand-written `/` endpoint list with auto-generated one. | Removes the drift between docs and reality. | Trivial. | §2.6 |
| 8 | Remove dead 503 branch and align `_verify_role` docstring. | Removes a lie. | Trivial. | §2.5 |
| 9 | Drop dead Retrofit declarations or wire them up. | Reduces maintenance surface. | Trivial. | §3.5 |
| 10 | Add typed `ActionRow`, `CourseIntelligence`, `CapacityPlan` models. | Removes stringly-typed access in clients. | Medium. | §3.1 / §3.8 |
| 11 | Adopt a `UiState<T>` interface across ViewModels. | Removes four bespoke sealed classes. | Medium. | §3.2 |
| 12 | Move hardcoded `Color(...)` and `RoundedCornerShape(N.dp)` into theme tokens. | Visual consistency. | Small. | §3.7 |
| 13 | Version the cache swap in `adoptBackgroundSync`. | Removes mid-tab data churn. | Small. | §3.1 |
| 14 | Wire `/api/v2/actions/{id}/audit` into `ActionsInbox`. | Gives the manager a visible audit trail. | Small. | §3.5 |
| 15 | Introduce shared `Throwable.userMessage(verb)` helper. | Dedupe of 8 error mappers. | Small. | §3.6 |
| 16 | Negotiate upstream changes with the RMS team (single sign-on, GET reads, versioned responses, deduplicated assignments). | Improves every consumer. | High — depends on RMS team. | §1.4 |

---

## Verification status

- ✅ Catalogue inventory: read every file in `trainer_portal_api_details/`.
- ✅ Backend inventory: read `backend.py` (4390 lines) end-to-end and `action_store.py`.
- ✅ Android inventory: read all Kotlin files under `SkillEdge_Android/app/src/main/java/com/example/skillsync/`.
- ✅ Cross-checked: `MarkSkillRequest` field-name concern from the Android inventory — *the inventory was wrong*; Gson serialises snake_case Kotlin names verbatim, so the wire keys are correct.
- ✅ Cross-checked: dead 503 branch in `login()` — confirmed; `_verify_role` never returns `"rms_error"`.
- ❌ Live RMS probe: not performed in this audit (no RMS credentials available without repo secret access).
- ❌ Render production probe: not performed; current v3.1.1 release is already production-validated per `AI/PROGRESS.md`.
- ❌ ADB device: not available — cannot physically validate the install-over upgrade or capture screenshots.
