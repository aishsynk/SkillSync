"""
SkillEdge Local Server
- Serves HTML files from this directory at /
- Serves theme assets from ./assets/ (local copy of Sean Theme Color Admin)
- Structured logging, startup validation, configurable PORT
"""
import http.server
import json
import logging
import logging.handlers
import os
import re
import sys
import time
import mimetypes
import threading
from datetime import datetime, timezone
from pathlib import Path
from urllib.parse import urlparse, parse_qs, unquote

import intelligence
from api.config import IS_PRODUCTION, validate_all_credentials
from services import auth_service, rms_service, static_service, refresh_service
from services import action_state_service, review_flag_service
from services.cache_service import cache_path, cache_fresh, read_cache, write_cache, CACHE_TTL
from services.custom_course_match_service import build_custom_course_match_objects
from agentic import agent as agent_service
from agentic import learning as learning_service
from agentic.context import build_agent_context

# Path-parameterized POST routes for Phase 4 action/review-flag persistence.
_ACTION_ROUTE_RE = re.compile(r"^/api/actions/([^/]+)/(close|escalate|reassign)$")
_REVIEW_FLAG_ROUTE_RE = re.compile(r"^/api/review-flags/([^/]+)/(acknowledge|resolve|escalate)$")
_ACTION_VERB_TO_STATE = {"close": "closed", "escalate": "escalated", "reassign": "reassigned"}
_FLAG_VERB_TO_STATE = {"acknowledge": "acknowledged", "resolve": "resolved", "escalate": "escalated"}

# ── Configuration from environment ──────────────────────────────────────────
PORT = int(os.getenv("PORT", os.getenv("SKILLEDGE_PORT", "8765")))
LOG_LEVEL = os.getenv("SKILLEDGE_LOG_LEVEL", "INFO").upper()
LOG_FORMAT = os.getenv("SKILLEDGE_LOG_FORMAT", "text").lower()  # "text" or "json"

# ── Project layout (production structure) ───────────────────────────────────
# backend/app.py -> repo root is one level up.
REPO_ROOT = Path(__file__).resolve().parents[1]
PROJECT_DIR = REPO_ROOT                         # alias kept for startup log messages
FRONTEND_DIR = REPO_ROOT / "frontend"
PAGES_DIR = FRONTEND_DIR / "pages"              # active product pages
DEPRECATED_DIR = FRONTEND_DIR / "deprecated"    # client-side redirect pages
ASSETS_DIR = FRONTEND_DIR / "assets"            # SeanTheme Color Admin assets (served at /assets/*)
# Search order for non-/assets GETs: active pages, then deprecated redirects, then frontend root (js/, etc.)
STATIC_ROOTS = [PAGES_DIR, DEPRECATED_DIR, FRONTEND_DIR]
RUNTIME_DIR = REPO_ROOT / "runtime"             # cache + logs live here

# ── Structured Logging ──────────────────────────────────────────────────────
log = logging.getLogger("skilledge")


class JsonFormatter(logging.Formatter):
    """Structured JSON log formatter for production environments."""
    def format(self, record):
        entry = {
            "ts": self.formatTime(record),
            "level": record.levelname,
            "logger": record.name,
            "msg": record.getMessage(),
        }
        if record.exc_info and record.exc_info[1]:
            entry["error"] = str(record.exc_info[1])
            entry["error_type"] = type(record.exc_info[1]).__name__
        return json.dumps(entry, default=str)


def _setup_logging():
    """Configure structured logging based on environment."""
    root = logging.getLogger()
    root.setLevel(getattr(logging, LOG_LEVEL, logging.INFO))
    # Clear any existing handlers
    root.handlers.clear()
    handler = logging.StreamHandler(sys.stdout)
    if LOG_FORMAT == "json":
        handler.setFormatter(JsonFormatter())
    else:
        handler.setFormatter(logging.Formatter(
            "%(asctime)s [%(levelname)s] %(name)s: %(message)s",
            datefmt="%Y-%m-%d %H:%M:%S",
        ))
    root.addHandler(handler)
    # File handler with rotation (10 MB max, 5 backups)
    log_dir = RUNTIME_DIR / "logs"
    log_dir.mkdir(parents=True, exist_ok=True)
    fh = logging.handlers.RotatingFileHandler(
        log_dir / "skilledge.log",
        maxBytes=10 * 1024 * 1024,
        backupCount=5,
        encoding="utf-8",
    )
    fh.setFormatter(logging.Formatter(
        "%(asctime)s [%(levelname)s] %(name)s: %(message)s",
        datefmt="%Y-%m-%d %H:%M:%S",
    ))
    root.addHandler(fh)


# ── Startup Validation ──────────────────────────────────────────────────────
def _validate_startup():
    """Run startup checks. Raises on fatal issues in production."""
    issues = []
    # 1. Check assets directory
    if not ASSETS_DIR.exists():
        issues.append(f"Assets directory not found: {ASSETS_DIR}")
    # 2. Check cache directory writable
    cache_dir = RUNTIME_DIR / "cache"
    cache_dir.mkdir(parents=True, exist_ok=True)
    try:
        test_file = cache_dir / ".write_test"
        test_file.write_text("ok")
        test_file.unlink()
    except Exception as e:
        issues.append(f"Cache directory not writable: {e}")
    # 3. Validate credentials
    missing_creds = validate_all_credentials()
    if IS_PRODUCTION and missing_creds:
        issues.append(f"{len(missing_creds)} credential env vars missing in production mode")
    # 4. Check required HTML pages
    for page in ["index.html", "login.html"]:
        if not (PAGES_DIR / page).exists():
            issues.append(f"Required page missing: {page}")

    if issues:
        for issue in issues:
            log.error("Startup check FAILED: %s", issue)
        if IS_PRODUCTION:
            raise RuntimeError(
                f"Startup validation failed with {len(issues)} issue(s). "
                "Fix issues or set SKILLEDGE_ENV=development to bypass."
            )
        else:
            log.warning("Startup validation found %d issue(s) — continuing in development mode.", len(issues))
    else:
        log.info("Startup validation passed.")


# ── Intelligence Cache Layer ────────────────────────────────────────────────
def _now_iso():
    return datetime.now(timezone.utc).isoformat()


_BG_REFRESH_LOCK = threading.Lock()
_BG_REFRESH_INFLIGHT = set()


def _schedule_background_refresh(email):
    """Kick off a non-blocking background intelligence rebuild for a manager.

    Stale caches are served immediately (see build_or_load_intelligence); this
    refreshes them in a daemon thread so login/dashboard never block on RMS.
    A per-email in-flight guard prevents duplicate rebuilds from concurrent
    requests. The refresh_service already serializes with its own RUNNING flag.
    """
    with _BG_REFRESH_LOCK:
        if email in _BG_REFRESH_INFLIGHT:
            return
        _BG_REFRESH_INFLIGHT.add(email)

    def _run():
        try:
            refresh_service.refresh_once(email, lambda em, force=True: build_or_load_intelligence(em, force=True)[0], force=True)
        except Exception:  # noqa: BLE001
            log.exception("Background refresh failed for %s", email)
        finally:
            with _BG_REFRESH_LOCK:
                _BG_REFRESH_INFLIGHT.discard(email)

    threading.Thread(target=_run, name=f"bg-refresh-{email[:16]}", daemon=True).start()


def _with_freshness_metadata(payload, from_cache, refresh_failed_now):
    """Attach honest, always-recomputed freshness metadata to a payload.

    `generated_at` is the only field that reflects when the underlying data was
    actually built — everything here is *derived* from it plus the current
    request time, never used to rewrite it. A stale-cache fallback must never
    make old data look freshly built (that was the Phase 0 bug: the fallback
    path used to overwrite `generated_at` to "now").
    """
    payload = dict(payload)
    now = time.time()
    generated_at = payload.get("generated_at")
    served_at = int(now)
    cache_age_minutes = None
    if isinstance(generated_at, (int, float)) and generated_at:
        cache_age_minutes = round(max(0.0, now - generated_at) / 60.0, 1)

    if not from_cache:
        cache_status = "live"
    elif refresh_failed_now:
        cache_status = "refresh_failed"
    elif cache_age_minutes is not None and cache_age_minutes > (CACHE_TTL / 60.0):
        cache_status = "stale"
    else:
        cache_status = "cached"

    payload["served_at"] = served_at
    payload["from_cache"] = from_cache
    payload["cache_age_minutes"] = cache_age_minutes
    payload["cache_status"] = cache_status
    payload.setdefault("last_refresh_success_at", None)
    payload.setdefault("last_refresh_failed_at", None)
    payload.setdefault("last_refresh_error", None)
    return payload


def build_or_load_intelligence(email, force=False):
    """Return (payload, from_cache). Builds via the live API pipeline, caches per manager.

    The returned payload always carries honest freshness metadata (see
    `_with_freshness_metadata`) — `generated_at` is preserved as-is on every
    fallback path, never rewritten to "now".
    """
    path = cache_path(email)
    if not force and path.exists():
        cached = read_cache(path)
        if cache_fresh(path):
            log.debug("Cache hit for %s", email)
            return _with_freshness_metadata(cached, from_cache=True, refresh_failed_now=False), True
        # Stale cache: serve it immediately and rebuild in the background. The
        # request never blocks on RMS; the UI shows honest freshness metadata
        # plus a "refresh_pending" flag so the manager knows a rebuild is running.
        log.info("Serving stale cache for %s; scheduling background refresh", email)
        _schedule_background_refresh(email)
        cached["refresh_pending"] = True
        return _with_freshness_metadata(cached, from_cache=True, refresh_failed_now=False), True
    last_err = None
    for attempt in range(3):
        try:
            log.info("Building intelligence for %s (attempt %d)", email, attempt + 1)
            start = time.perf_counter()
            data = intelligence.build_unified(email)
            elapsed = time.perf_counter() - start
            log.info("Intelligence built for %s in %.2fs (%d trainers)", email, elapsed, data.get("trainer_count", 0))
            break
        except Exception as e:  # noqa: BLE001
            last_err = e
            log.warning("Intelligence build attempt %d failed for %s: %s", attempt + 1, email, str(e)[:200])
            if attempt < 2:
                time.sleep(1.0)
                continue
            if path.exists():
                log.warning("Falling back to stale cache for %s", email)
                cached = read_cache(path)
                # `generated_at` is intentionally left untouched here — this is
                # the exact Phase 0 bug fix. Only the refresh-attempt metadata
                # is updated, so the UI can honestly say "we tried and failed"
                # without pretending the underlying data is new.
                cached["last_refresh_failed_at"] = _now_iso()
                cached["last_refresh_error"] = str(last_err)[:300]
                write_cache(path, cached)
                return _with_freshness_metadata(cached, from_cache=True, refresh_failed_now=True), True
            raise
    else:
        raise last_err
    data["generated_at"] = int(time.time())
    data["last_refresh_success_at"] = _now_iso()
    data["last_refresh_failed_at"] = None
    data["last_refresh_error"] = None
    write_cache(path, data)
    return _with_freshness_metadata(data, from_cache=False, refresh_failed_now=False), False


def _run_background_refresh():
    """Best-effort 24-hour refresher that never blocks startup."""
    while True:
        try:
            session_email = os.getenv("SKILLEDGE_REFRESH_EMAIL") or os.getenv("SKILLEDGE_DEFAULT_EMAIL") or ""
            if session_email:
                refresh_service.poll_due(session_email, lambda email, force=True: build_or_load_intelligence(email, force=True)[0])
        except Exception:
            log.exception("Background refresh loop failed")
        time.sleep(60 * 15)


# ── HTTP Handler ────────────────────────────────────────────────────────────
class Handler(http.server.BaseHTTPRequestHandler):

    def log_message(self, fmt, *args):
        log.debug("%s %s", self.address_string(), fmt % args)

    # ------------------------------------------------------------------ POST
    def do_POST(self):
        path = urlparse(self.path).path
        action_match = _ACTION_ROUTE_RE.match(path)
        flag_match = _REVIEW_FLAG_ROUTE_RE.match(path)
        if path == "/auth/login":
            self._handle_login()
        elif path == "/api/agent/ask":
            if not self._require_session():
                return
            self._handle_agent_ask()
        elif path == "/api/agent/feedback":
            if not self._require_session():
                return
            self._handle_agent_feedback()
        elif path == "/api/agent/tune":
            if not self._require_session():
                return
            self._json(200, learning_service.tune_weights(force=True))
        elif path == "/api/refresh/run":
            if not self._require_session():
                return
            email = self._session_email()
            self._json(200, refresh_service.refresh_once(email, lambda em, force=True: build_or_load_intelligence(em, force=True)[0], force=True))
        elif action_match:
            if not self._require_session():
                return
            self._handle_action_lifecycle(unquote(action_match.group(1)), action_match.group(2))
        elif flag_match:
            if not self._require_session():
                return
            self._handle_review_flag_lifecycle(unquote(flag_match.group(1)), flag_match.group(2))
        elif path.startswith("/rms/"):
            if not self._require_session():
                return
            self._serve_rms(path[len("/rms/"):])
        else:
            self.send_error(405)

    def _read_json_body(self):
        length = int(self.headers.get("Content-Length", 0))
        raw = self.rfile.read(length) if length else b""
        try:
            body = json.loads(raw.decode() or "{}")
        except Exception:
            body = {}
        return body if isinstance(body, dict) else {}

    def _handle_action_lifecycle(self, action_id, verb):
        body = self._read_json_body()
        state = _ACTION_VERB_TO_STATE[verb]
        try:
            record = action_state_service.set_action_state(
                action_id=action_id,
                state=state,
                note=body.get("note"),
                assignee=body.get("assignee"),
                reason=body.get("reason"),
                manager_email=self._session_email(),
            )
        except ValueError as e:
            self._json(400, {"error": str(e)})
            return
        # Learning loop: every lifecycle decision becomes a labeled example.
        try:
            learning_service.record_feedback(
                manager_email=self._session_email(),
                entity_type="manager_action",
                entity_id=action_id,
                decision=f"state:{state}",
                outcome="negative" if state == "escalated" else "neutral",
                note=body.get("note") or body.get("reason") or "",
            )
        except Exception:  # noqa: BLE001
            log.debug("Could not record learning feedback for action %s", action_id)
        self._json(200, {
            "ok": True,
            "action_id": action_id,
            "state": record.get("state"),
            "updated_at": record.get("updated_at"),
            "note": record.get("note"),
        })

    def _handle_review_flag_lifecycle(self, flag_id, verb):
        body = self._read_json_body()
        state = _FLAG_VERB_TO_STATE[verb]
        try:
            record = review_flag_service.set_flag_state(
                flag_id=flag_id,
                state=state,
                note=body.get("note"),
                manager_email=self._session_email(),
            )
        except ValueError as e:
            self._json(400, {"error": str(e)})
            return
        # Learning loop: resolved flags are positive evidence; escalations negative.
        try:
            learning_service.record_feedback(
                manager_email=self._session_email(),
                entity_type="review_flag",
                entity_id=flag_id,
                decision=f"state:{state}",
                outcome="positive" if state == "resolved" else "negative" if state == "escalated" else "neutral",
                note=body.get("note") or "",
            )
        except Exception:  # noqa: BLE001
            log.debug("Could not record learning feedback for flag %s", flag_id)
        self._json(200, {
            "ok": True,
            "flag_id": flag_id,
            "state": record.get("state"),
            "updated_at": record.get("updated_at"),
            "note": record.get("note"),
        })

    def _handle_login(self):
        length = int(self.headers.get("Content-Length", 0))
        raw = self.rfile.read(length) if length else b""
        try:
            body = json.loads(raw.decode() or "{}")
        except Exception:
            body = {}
        email = auth_service.normalise_email((body or {}).get("email"))
        if not email:
            self._json(400, {"error": "email is required"})
            return
        # Non-blocking login: establish the session immediately instead of
        # running the full RMS intelligence build inside the request (that used
        # to leave a spinner hanging for minutes on a cold or stale cache). The
        # unified endpoint builds/serves on demand afterwards.
        token = auth_service.create_session(email)
        log.info("Login succeeded for %s (no blocking build)", email)
        login_payload = {"email": email}
        try:
            path = cache_path(email)
            if path.exists():
                cached = read_cache(path)
                login_payload["trainer_count"] = cached.get("trainer_count")
        except Exception:  # noqa: BLE001
            pass
        body_bytes = json.dumps(login_payload).encode()
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Set-Cookie", auth_service.session_cookie(token))
        self.send_header("Content-Length", str(len(body_bytes)))
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Cache-Control", "no-cache")
        self.end_headers()
        self.wfile.write(body_bytes)

    def _session_email(self):
        return auth_service.session_email_from_cookie(self.headers.get("Cookie", ""))

    def _require_session(self):
        if self._session_email():
            return True
        self._json(401, {"error": "Authentication required"})
        return False

    def _serve_rms(self, api_name):
        """Server-side RMS relay: injects credentials, calls RMS, returns content.
        Only whitelisted, configured API names are accepted."""
        if not rms_service.is_known_api(api_name):
            self._json(404, {"error": f"Unknown API: {api_name}"})
            return
        length = int(self.headers.get("Content-Length", 0))
        raw = self.rfile.read(length) if length else b""
        try:
            body = json.loads(raw.decode() or "{}")
        except Exception:
            body = {}
        if not isinstance(body, dict):
            body = {}
        self._json(200, rms_service.call_api(api_name, body))

    # ------------------------------------------------------------------ GET
    def do_GET(self):
        parsed = urlparse(self.path)
        path = parsed.path

        if path == "/data/unified-manager-intelligence":
            if not self._require_session():
                return
            qs = parse_qs(parsed.query)
            session_email = self._session_email()
            requested = auth_service.normalise_email((qs.get("email") or [""])[0])
            email = session_email
            if requested and requested != session_email:
                log.warning("Email mismatch: session=%s requested=%s", session_email, requested)
                self._json(403, {"error": "Requested email does not match authenticated session"})
                return
            force = (qs.get("refresh") or ["0"])[0] in ("1", "true", "yes")
            course_input = {
                "outline": (qs.get("course_outline") or qs.get("outline") or [""])[0],
                "meta": {
                    "title": (qs.get("course_title") or qs.get("title") or [""])[0],
                    "vendor": (qs.get("course_vendor") or qs.get("vendor") or [""])[0],
                    "domain": (qs.get("course_domain") or qs.get("domain") or [""])[0],
                    "level": (qs.get("course_level") or qs.get("level") or [""])[0],
                    "delivery_type": (qs.get("delivery_type") or qs.get("deliveryType") or [""])[0],
                },
            }
            self._serve_unified_manager_intelligence(email, force, course_input)
            return
        if path == "/healthz":
            self._json(200, {
                "status": "ok",
                "service": "skilledge",
                "version": "1.0.0",
                "environment": "production" if IS_PRODUCTION else "development",
                "active_sessions": auth_service.active_session_count(),
                "session_ttl_seconds": auth_service.SESSION_TTL_SECONDS,
            })
            return
        if path == "/api/agent/briefing":
            if not self._require_session():
                return
            self._handle_agent_briefing()
            return
        if path == "/api/agent/learning":
            if not self._require_session():
                return
            self._json(200, learning_service.get_status())
            return
        if path == "/auth/session":
            email = self._session_email()
            if not email:
                self._json(401, {"error": "Authentication required"})
                return
            self._json(200, {"email": email})
            return
        if path == "/auth/logout":
            self._handle_logout()
            return
        if path == "/api/refresh/status":
            self._json(200, refresh_service.status_payload())
            return
        if path == "/api/refresh/logs":
            qs = parse_qs(parsed.query)
            limit = int((qs.get("limit") or ["50"])[0])
            self._json(200, {"logs": refresh_service.get_logs(limit=limit)})
            return
        if path == "/api/refresh/last-success":
            state = refresh_service.status_payload()
            self._json(200, {"last_success_ts": state.get("last_success_ts"), "next_run_ts": state.get("next_run_ts")})
            return
        if path == "/api/knowledge/search":
            self._json(200, self._search_knowledge(parse_qs(parsed.query).get("q", [""])[0]))
            return
        if path.startswith("/api/knowledge/"):
            self._json(200, self._knowledge_lookup(path))
            return

        static_file = static_service.resolve_static_request(path, ASSETS_DIR, STATIC_ROOTS)
        if not static_file:
            self.send_error(404, f"Not found: {path}")
            return

        self.send_response(200)
        self.send_header("Content-Type", static_file["mime"])
        self.send_header("Content-Length", str(len(static_file["data"])))
        self.send_header("Cache-Control", "no-cache")
        self.end_headers()
        self.wfile.write(static_file["data"])

    def _serve_unified_manager_intelligence(self, email, force, course_input=None):
        if not email:
            self._json(401, {"error": "Authentication required"})
            return
        try:
            start = time.perf_counter()
            payload, from_cache = build_or_load_intelligence(email, force=force)
            payload = self._with_custom_course_match(payload, course_input or {})
            # Apply manager lifecycle overlays (Phase 4) on every request — cheap,
            # local, and independent of the (possibly cached/stale-fallback) RMS
            # build above, so a close/escalate/resolve is reflected on the very
            # next reload even if the intelligence payload itself is served from
            # cache. Never mutates the original action/trainer records.
            action_state_service.apply_overlay(payload.get("manager_action_objects"))
            review_flag_service.apply_overlay(payload.get("trainer_operations_df"))
            elapsed = time.perf_counter() - start
            payload["from_cache"] = from_cache
            log.info("Served intelligence for %s (cache=%s, %.2fs)", email, from_cache, elapsed)
            self._json(200, payload)
        except Exception as e:  # noqa: BLE001
            log.error("Intelligence request failed for %s", email, exc_info=True)
            self._json(500, {"error": str(e)})

    def _agent_payload(self):
        """Load the manager's payload (cache-first, non-blocking) for the agent."""
        email = self._session_email()
        payload, _ = build_or_load_intelligence(email, force=False)
        return payload

    def _handle_agent_ask(self):
        body = self._read_json_body()
        question = str(body.get("question") or "").strip()
        if not question:
            self._json(400, {"error": "question is required"})
            return
        try:
            payload = self._agent_payload()
            ctx = build_agent_context(payload)
            response = agent_service.answer(question, ctx)
            self._json(200, response)
        except Exception as e:  # noqa: BLE001
            log.error("Agent ask failed", exc_info=True)
            self._json(500, {"error": str(e)})

    def _handle_agent_briefing(self):
        try:
            payload = self._agent_payload()
            ctx = build_agent_context(payload)
            self._json(200, agent_service.build_briefing(ctx))
        except Exception as e:  # noqa: BLE001
            log.error("Agent briefing failed", exc_info=True)
            self._json(500, {"error": str(e)})

    def _handle_agent_feedback(self):
        body = self._read_json_body()
        try:
            example = learning_service.record_feedback(
                manager_email=self._session_email(),
                entity_type=str(body.get("entity_type") or "unknown"),
                entity_id=str(body.get("entity_id") or ""),
                decision=str(body.get("decision") or ""),
                outcome=str(body.get("outcome") or "neutral"),
                note=str(body.get("note") or ""),
                features=body.get("features") or {},
            )
            self._json(200, {"ok": True, "id": example["id"], "ts": example["ts"]})
        except Exception as e:  # noqa: BLE001
            log.error("Agent feedback failed", exc_info=True)
            self._json(500, {"error": str(e)})

    def _with_custom_course_match(self, payload, course_input):
        outline = (course_input or {}).get("outline") or ""
        if not outline.strip():
            payload["custom_course_match_df"] = []
            payload["custom_course_match_objects"] = []
            return payload
        rows = build_custom_course_match_objects(
            outline=outline,
            course_meta=(course_input or {}).get("meta") or {},
            trainers=payload.get("trainer_operations_df") or [],
            availability_rows=payload.get("trainer_availability_engine_df") or [],
            delivery_rows=payload.get("delivery_intelligence_df") or [],
            allocation_rows=payload.get("allocation_intelligence_df") or [],
            allocation_ranked_rows=payload.get("allocation_ranked_trainer_df") or [],
            feedback_rows=payload.get("feedback_coaching_df") or [],
            future_skill_rows=payload.get("future_skill_roadmap_df") or [],
            future_cert_rows=payload.get("future_certification_roadmap_df") or [],
            vendor_strength_rows=payload.get("vendor_strength_df") or [],
            certification_rows=payload.get("certification_intelligence_df") or [],
            course_best_rows=payload.get("course_best_trainer_df") or [],
        )
        payload["custom_course_match_df"] = rows
        payload["custom_course_match_objects"] = rows
        return payload

    def _json(self, code, obj):
        body = json.dumps(obj, default=str).encode()
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Cache-Control", "no-cache")
        self.end_headers()
        self.wfile.write(body)

    def do_OPTIONS(self):
        self.send_response(200)
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        self.send_header("Access-Control-Allow-Headers", "Content-Type")
        self.end_headers()

    def _handle_logout(self):
        auth_service.delete_session_from_cookie(self.headers.get("Cookie", ""))
        log.info("Session logged out")
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Set-Cookie", auth_service.expired_session_cookie())
        self.send_header("Content-Length", "2")
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Cache-Control", "no-cache")
        self.end_headers()
        self.wfile.write(b"{}")

    def _knowledge_lookup(self, path):
        if path.endswith("/risk-signals"):
            return {"items": self._read_kb_jsonl("risk_signals")}
        if path.endswith("/recommendations"):
            return {"items": self._read_kb_jsonl("action_recommendations")}
        if "/trainer/" in path:
            email = path.rsplit("/", 1)[-1]
            rows = [r for r in self._read_kb_jsonl("trainer_profiles") if email.lower() in str(r).lower()]
            return {"items": rows}
        if "/course/" in path:
            cid = path.rsplit("/", 1)[-1]
            rows = [r for r in self._read_kb_jsonl("course_matching") if cid.lower() in str(r).lower()]
            return {"items": rows}
        return {"items": self._read_kb_jsonl("entities")}

    def _read_kb_jsonl(self, name):
        path = refresh_service.KB_DIR / f"{name}.jsonl"
        if not path.exists():
            return []
        out = []
        try:
            text = path.read_text(encoding="utf-8")
            for line in text.splitlines():
                line = line.strip()
                if not line:
                    continue
                try:
                    out.append(json.loads(line))
                except json.JSONDecodeError:
                    continue
            if not out:
                try:
                    data = json.loads(text)
                    if isinstance(data, list):
                        out = data
                    elif isinstance(data, dict):
                        out = [data]
                except Exception:
                    pass
            return out
        except Exception:
            return []

    def _search_knowledge(self, q):
        q = (q or "").lower().strip()
        if not q:
            return {"items": []}
        hits = []
        for name in ("entities", "trainer_profiles", "course_matching", "risk_signals", "action_recommendations", "manager_decisions", "refresh_snapshots"):
            for row in self._read_kb_jsonl(name):
                if q in json.dumps(row, default=str).lower():
                    hits.append({"dataset": name, "row": row})
        return {"items": hits[:100]}


def main():
    """Configure logging, validate startup, and run the HTTP server (blocking)."""
    _setup_logging()
    log.info("=" * 60)
    log.info("SkillEdge Server Starting")
    log.info("  Environment : %s", "production" if IS_PRODUCTION else "development")
    log.info("  Port        : %d", PORT)
    log.info("  Log level   : %s", LOG_LEVEL)
    log.info("  Repo root   : %s", REPO_ROOT)
    log.info("  Frontend    : %s", FRONTEND_DIR)
    log.info("  Assets dir  : %s", ASSETS_DIR)
    log.info("=" * 60)

    _validate_startup()

    mimetypes.add_type("text/javascript", ".js")
    mimetypes.add_type("text/css", ".css")
    log.info("SkillEdge running at http://localhost:%d", PORT)
    threading.Thread(target=_run_background_refresh, daemon=True).start()
    with http.server.ThreadingHTTPServer(("", PORT), Handler) as srv:
        try:
            srv.serve_forever()
        except KeyboardInterrupt:
            log.info("Server shutting down.")
            srv.shutdown()


if __name__ == "__main__":
    main()
