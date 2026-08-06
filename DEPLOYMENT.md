# SkillEdge â€” Deployment Guide

## Quick Start (Local Development)

```bash
# No environment variables needed for development â€” fallback credentials are built in.
cd SkillEdge
python server.py
# Open http://localhost:8765
```

The server starts in **development mode** by default. All RMS credentials use hardcoded fallbacks.

---

## Environment Modes

| Variable | Value | Behavior |
|---|---|---|
| `SKILLEDGE_ENV` | `development` (default) | Uses fallback credentials. Startup validation logs warnings but does not block. |
| `SKILLEDGE_ENV` | `production` | Requires ALL credential env vars. Server refuses to start if any are missing. |

---

## Required Environment Variables

### Server Configuration

| Variable | Default | Description |
|---|---|---|
| `SKILLEDGE_ENV` | `development` | `development` or `production` |
| `SKILLEDGE_PORT` | `8765` | HTTP port for the server |
| `SKILLEDGE_LOG_LEVEL` | `INFO` | Python log level (DEBUG, INFO, WARNING, ERROR) |
| `SKILLEDGE_LOG_FORMAT` | `text` | `text` for human-readable, `json` for structured |
| `SKILLEDGE_SESSION_TTL_SECONDS` | `28800` | Session cookie lifetime (8 hours default) |
| `SKILLEDGE_CACHE_TTL_SECONDS` | `14400` | Intelligence cache lifetime (4 hours default) |

### RMS API Credentials (38 variables)

Each of the 19 RMS APIs requires a `_USER` and `_PASS` variable. See [.env.example](.env.example) for the complete list.

In **development mode**, these are optional â€” hardcoded fallbacks are used.  
In **production mode**, ALL must be set or the server will refuse to start.

---

## Production Deployment

### 1. Set Environment Variables

```bash
# Copy the example and fill in real values
cp .env.example .env
# Edit .env with production credentials
```

### 2. Set Production Mode

```bash
export SKILLEDGE_ENV=production
export SKILLEDGE_PORT=8765
export SKILLEDGE_LOG_LEVEL=INFO
export SKILLEDGE_LOG_FORMAT=json
```

### 3. Start the Server

```bash
python server.py
```

The server will:
1. Validate all credentials are present
2. Check assets directory exists
3. Check cache directory is writable
4. Check required HTML pages exist
5. Start listening on the configured port

### 4. Verify Deployment

```bash
# Health check
curl http://localhost:8765/healthz

# Expected response:
# {"status": "ok", "service": "skilledge", "version": "1.0.0", "environment": "production", ...}
```

---

## Logging

### Log Output

- **Console**: All log messages go to stdout
- **File**: `runtime/logs/skilledge.log` with 10 MB rotation, 5 backups

### Log Format

**Text mode** (default, development):
```
2026-07-02 01:30:00 [INFO] skilledge: Intelligence built for user@example.com in 12.34s (15 trainers)
```

**JSON mode** (production):
```json
{"ts": "2026-07-02 01:30:00", "level": "INFO", "logger": "skilledge", "msg": "Intelligence built for user@example.com in 12.34s (15 trainers)"}
```

### What Gets Logged

| Event | Level | Example |
|---|---|---|
| Server startup | INFO | Environment, port, directories |
| Login attempt | INFO | Email address |
| Intelligence build | INFO | Email, duration, trainer count |
| Cache hit | DEBUG | Email |
| Build failure | WARNING | Email, error message, attempt number |
| Stale cache fallback | WARNING | Email |
| Request error | ERROR | Email, full traceback |
| Email mismatch | WARNING | Session vs requested email |

---

## Smoke Test

Run after any deployment to verify the system is working:

```bash
# Server must be running on localhost:8765
python tests/smoke_test.py
```

The smoke test checks:
- All Python modules import correctly
- Required HTML pages exist
- No pages contain direct RMS/proxy calls
- Health endpoint returns 200
- Unauthenticated requests are blocked
- Login succeeds and establishes session
- Unified endpoint returns all 6 canonical datasets
- Payload contract (required fields, data health integrity)
- Wrong email queries are rejected
- Cached responses are served

---

## Backup and Rollback

### What to Back Up

1. **`runtime/cache/` directory** â€” Contains per-manager intelligence payloads. Not critical (rebuilt on next login), but preserving it avoids the 30-120 second first-build delay per manager.
2. **`runtime/logs/` directory** â€” Server log files with rotation.
3. **`.env`** â€” Production credentials (do NOT commit to version control).

### Rollback Steps

1. **Stop the server**: `Ctrl+C` or kill the process
2. **Restore previous code**: `git checkout HEAD~1` (or restore from backup)
3. **Clear caches if needed**: `rm -rf runtime/cache/*.json`
4. **Restart**: `python server.py`

### Emergency Cache Clear

If intelligence payloads are corrupted:
```bash
# Clear all cached payloads (they will rebuild on next login)
rm runtime/cache/intel_*.json
```

---

## Architecture Overview

```
server.py                    <- single executable launcher (`python server.py`)
backend/app.py               <- HTTP server, routing, session management
backend/intelligence.py      <- Intelligence pipeline (build_unified)
backend/api/                 <- RMS credentials and low-level API client
backend/services/            <- auth, cache, RMS relay, static serving helpers
backend/shared/              <- constants, normalizers, scoring, safety, explainability
frontend/pages/              <- active product pages
frontend/deprecated/         <- compatibility redirect pages
frontend/assets/             <- SeanTheme assets served at /assets/*
frontend/js/                 <- shared browser API/layout helpers served at /js/*
runtime/cache/               <- per-manager cached intelligence payloads
runtime/logs/                <- rotating server logs
```

---

## Security Notes

- **Credentials**: Never committed to version control. Use env vars in production.
- **Sessions**: Server-side tokens with HttpOnly cookies. No client-side token storage.
- **Path traversal**: `static_service.py` validates resolved paths against allowed roots.
- **Email scope**: The unified endpoint enforces that `?email=` matches the authenticated session.
- **Development fallbacks**: Only active when `SKILLEDGE_ENV != production`. In production, missing credentials are a fatal startup error.

---

## Remaining Deployment Risks

1. **Single-instance only**: No load balancing or multi-process support. Sessions are in-memory and lost on restart.
2. **No HTTPS**: The server runs plain HTTP. Use a reverse proxy (nginx, Caddy) for TLS in production.
3. **No rate limiting**: No request throttling. Could be added via reverse proxy.
4. **Cache invalidation**: 4-hour blunt TTL. No way to invalidate a specific manager's cache without deleting the file.
5. **RMS API dependency**: If Koenig RMS APIs are down, intelligence build fails. Stale cache is the fallback.

