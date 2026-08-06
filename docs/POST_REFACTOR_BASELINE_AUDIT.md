# Post-Refactor Baseline Audit

> Status note: Historical baseline from before the production folder restructure. The current runtime structure is `server.py` as the only executable entry, `backend/app.py` for runtime app logic, active pages in `frontend/pages/`, deprecated redirects in `frontend/deprecated/`, and cache/log output in `runtime/`.

This is a factual audit of the repository state after the refactor scaffolding phases. No additional code changes are proposed here.

## 1) Current folder / module structure

### Current top-level structure

- `server.py`
- `intelligence.py`
- `api/`
- `shared/`
- `knowledge/`
- `services/`
- `intelligence_engines/`
- `js/`
- Active HTML pages in `frontend/pages/`; deprecated compatibility pages in `frontend/deprecated/`

### Present modules

#### `api/`
- `__init__.py`
- `config.py`
- `client.py`

#### `shared/`
- `__init__.py`
- `constants.py`
- `safety.py`
- `normalizers.py`
- `explainability.py`
- `scoring.py`

#### `knowledge/`
- `__init__.py`
- `technology_graph.py`
- `course_graph.py`
- `trainer_graph.py`
- `certification_graph.py`

#### `services/`
- `__init__.py`
- `cache_service.py`
- `unified_intelligence_service.py`
- `manager_scope_service.py`

#### `intelligence_engines/`
- `__init__.py`
- `capability_engine.py`
- `availability_engine.py`
- `readiness_engine.py`
- `recommendation_engine.py`
- `action_engine.py`
- `trust_engine.py`
- `custom_course_engine.py`

## 2) What was extracted successfully

### Safe helper layer

- API constants and metadata
- low-level API client wrapper and token cache
- cache helpers
- shared text / number / safety helpers
- pure normalizers
- pure scoring and recommendation helpers
- explainability helpers

### Scaffolding layer

- knowledge graph module shells
- engine module shells

### Behavior preserved

- unified endpoint shape
- reportee-first flow
- existing pages and routes
- cache freshness and `from_cache` behavior
- smoke test pass state

## 3) What still remains in `intelligence.py`

`intelligence.py` is now smaller, but it still owns the core orchestration path.

### Still in `intelligence.py`

- `_fetch_trainer`
- `_failed_trainer_health`
- `_health_dataset_for`
- `_health_page_for`
- `_health_impact_for`
- `_health_fix_for`
- `_health_scrub`
- `normalize_health`
- `build_unified`

### What that means

- API calls are already delegated to `api.client`
- scoring is already delegated to `shared.scoring`
- explanation helpers are already delegated to `shared.explainability`
- normalizers are already delegated to `shared.normalizers`
- but the per-trainer orchestration and final dataset assembly still live in one file

## 4) Which parts are safe to extract next

These are the safest next candidates because they are already close to separable units and can be moved with low behavior risk if done carefully.

1. `normalize_health` and the health helper set into a dedicated trust module, if not already treated as fully shared.
2. `_fetch_trainer` into a service or trainer orchestration helper.
3. Small assembly helpers for `trainer_operations_df`, `course_allocation_df`, `trainer_timeline_df`, and `manager_action_df`, one at a time.
4. Cache upgrade / payload post-processing helpers that only normalize the availability rows.
5. Knowledge-graph scaffolds can later be wired into read-only recommendation helpers once the graph data is stable.

## 5) Which parts are risky to extract next

These parts are riskier because they are tightly coupled to multiple outputs or to the token / fetch flow.

1. `build_unified`
   - This is the highest-risk seam because it controls the response shape and dataset assembly.

2. The per-trainer fetch loop inside `build_unified`
   - It drives ordering, failure isolation, and the health side effects.

3. The route path in `server.py`
   - The route is stable and should stay stable from the browser’s perspective.

4. Any logic that changes how `trainer_availability_engine_df` is upgraded after cache load
   - This can alter the perceived labels even if the payload shape remains the same.

5. Any logic that changes the meaning of readiness, availability, or action labels
   - Even a harmless-looking relocation can create a hidden label regression if not tested carefully.

## 6) Whether `smoke_test.py` covers enough

### Current coverage

`smoke_test.py` currently verifies:

- `server.py` imports
- `intelligence.py` imports
- required page files exist
- no direct RMS/proxy API calls in pages
- no broken `trainer_profile_unknown_unknown.html` link
- unified endpoint cold-build with `refresh=1`
- required datasets are present
- cached second call is faster and reports `from_cache=true`

### What it covers well

- endpoint viability
- payload presence
- page existence
- cache behavior
- basic import health

### What it does not fully cover

- exact field-level parity of the payload after a refactor
- exact label parity for readiness, availability, and actions
- exact row-level equality for all datasets
- direct import-cycle detection beyond the basic import check

## 7) Missing regression checks

The current smoke test is good for baseline safety, but it is not yet a full contract test.

### Missing checks

1. Snapshot comparison of the unified payload structure and key fields against a known baseline.
2. Row-level parity checks for:
   - `trainer_operations_df`
   - `course_allocation_df`
   - `trainer_timeline_df`
   - `manager_action_df`
   - `trainer_availability_engine_df`
   - `data_health_df`
3. Explicit checks for label stability:
   - readiness labels
   - availability labels
   - recommendation labels
4. Explicit circular-import detection beyond a successful import.
5. Explicit verification that `build_unified` still returns the same top-level aliases in addition to the core datasets.
6. A check for the exact cache file path / TTL semantics beyond the observed cache behavior.

## 8) Recommended next implementation step

The safest next implementation step is:

**Extract the remaining orchestration seams in very small increments, starting with `normalize_health` or `_fetch_trainer`, and add a stronger contract test before touching `build_unified`.**

Why this next:

- It reduces the largest remaining concentration of logic without disturbing the route or payload shape.
- It keeps the refactor aligned with the current working baseline.
- It lowers the risk of accidental output drift before the final assembly split.

## 9) Audit checks

- smoke_test.py passes: yes
- unified endpoint shape unchanged: yes, based on smoke test and preserved route behavior
- pages still exist: yes
- cache still works: yes
- circular imports detected: none from the import check
- unused broken imports detected: none surfaced by the current import check
