# SkillEdge / Manager OS

AI-assisted delivery-intelligence workspace for Koenig delivery managers. A manager
logs in with their official email; the backend fetches their scoped reportees from the
live RMS APIs, runs the intelligence engines, and serves a unified payload that powers
a SeanTheme Color Admin dashboard and supporting pages.

## Run

```bash
python server.py
```

Then open <http://localhost:8765/> (redirects to the login page).

`server.py` is the single executable entry point. It puts `backend/` on the import
path and starts `backend/app.py`. Configuration is via environment variables (see
`.env.example`); defaults run on port **8765** in development mode.

## Project structure

```
SkillEdge/
  server.py                # single entry point — python server.py
  README.md
  DEPLOYMENT.md
  .env.example
  .gitignore

  backend/
    app.py                 # HTTP server: auth, RMS relay, unified endpoint, static serving
    intelligence.py        # unified intelligence build pipeline
    api/                   # RMS client + credential config
    services/              # auth, cache, rms, static, scope, fetch, unified services
    shared/                # scoring, normalizers, explainability, intelligence modules
    intelligence_engines/  # engine modules
    knowledge/             # domain graphs

  frontend/
    pages/                 # active product pages (index, login, team, etc.)
    deprecated/            # legacy pages that client-redirect to current ones
    js/                    # app.js, api.js (shared client)
    assets/                # SeanTheme Color Admin css/js/img/plugins (served at /assets/*)

  docs/                    # architecture, status, planning & review docs
  tests/
    smoke_test.py          # end-to-end smoke test against a running server
  runtime/
    cache/                 # per-manager intelligence cache (regenerated)
    logs/                  # rotating server logs
```

## How serving works

- `GET /assets/*` is served from `frontend/assets/` (SeanTheme's own `css/js/img/plugins`
  layout is preserved so every existing `/assets/...` URL keeps working).
- Any other page path is resolved in order across `frontend/pages/` → `frontend/deprecated/`
  → `frontend/` (the last covers shared `/js/*`). So `/team.html` serves the active page,
  `/trainers.html` serves the deprecated redirect page (which forwards to `/team.html`), and
  `/js/api.js` serves the shared client — all at their original URLs.

## Tests

Start the server, then:

```bash
python tests/smoke_test.py
```

## Key endpoints

- `GET  /healthz` — liveness + session stats
- `POST /auth/login` — establishes a session cookie for a manager email
- `GET  /auth/session`, `GET /auth/logout`
- `GET  /data/unified-manager-intelligence` — unified payload (session-scoped)
- `POST /rms/<api>` — server-side RMS relay (credentials never reach the browser)

See `DEPLOYMENT.md` for deployment details and `docs/` for architecture and status.
