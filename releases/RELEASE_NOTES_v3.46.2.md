# SkillEdge Production Release v3.46.2 (Build 132)

- **Release Date**: 2026-08-30
- **Version Code**: `132`
- **Version Name**: `3.46.2`
- **Deployed commit**: (this commit)

## Why this release

v3.46.1 wrapped `/api/data/trainer-360` in the partial-first warm pattern, but
production still returned HTTP 502 at ~31s. Root cause: the Render start command
was `gunicorn backend:app` with **no `--timeout`**, so gunicorn's 30s default
killed the worker mid-request — shorter than both a cold `trainer-360` build
(~43s) and the `_serve_or_warm` first-build wait. The background warm thread was
killed along with the worker, so every retry hit the same wall.

## What changed

- **`render.yaml` / `Procfile`**: start command is now
  `gunicorn backend:app --workers 1 --threads 8 --worker-class gthread --timeout 120`.
  - `--timeout 120` lets a cold build finish instead of a 30s worker kill.
  - `--threads 8` gives real concurrency for this I/O-bound service and lets the
    `_serve_or_warm` background threads run alongside requests.
  - `--workers 1` keeps the in-process warm/response caches
    (`_warm_payload_cache`, `_allocation_payload_cache`) coherent — multiple
    workers would each warm independently.
- `_WARM_FIRST_WAIT` reduced 45s → 22s: a cold call returns a `loading` skeleton
  sooner and the client polls, rather than holding the request open.
- No app-code change; APK content is identical to build 131. Version is
  incremented so the release history records the production deployment.

## Compatibility

- Android package and signing identity unchanged; build 132 installs over 131/130.

## Validation

- Backend pytest suite green (169).
- After deploy: `/api/data/trainer-360` must return 200 (not 502) with real
  `learner_rating` / quotes; the other warm endpoints must stay green.

## Operator note

If Render ignores `render.yaml` (service created manually, not as a Blueprint),
set the Start Command in the Render dashboard to the gunicorn line above.
