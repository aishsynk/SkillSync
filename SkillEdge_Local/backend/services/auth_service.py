"""Server-side auth/session helpers for SkillEdge."""

import os
import secrets
import time


SESSION_COOKIE = "skilledge_session"
SESSION_TTL_SECONDS = int(os.getenv("SKILLEDGE_SESSION_TTL_SECONDS", str(8 * 60 * 60)))
_SESSIONS = {}


def normalise_email(value):
    email = str(value or "").strip().lower()
    return email if email and "@" in email else ""


def create_session(email):
    token = secrets.token_urlsafe(32)
    _SESSIONS[token] = {"email": email, "created_at": int(time.time())}
    return token


def session_cookie(token):
    return f"{SESSION_COOKIE}={token}; Path=/; HttpOnly; SameSite=Lax; Max-Age={SESSION_TTL_SECONDS}"


def expired_session_cookie():
    return f"{SESSION_COOKIE}=; Path=/; HttpOnly; SameSite=Lax; Max-Age=0"


def session_email_from_cookie(cookie_header):
    token = session_token_from_cookie(cookie_header)
    if not token:
        return ""
    session = _SESSIONS.get(token)
    if not session:
        return ""
    if int(time.time()) - int(session.get("created_at", 0)) > SESSION_TTL_SECONDS:
        _SESSIONS.pop(token, None)
        return ""
    return session.get("email") or ""


def session_token_from_cookie(cookie_header):
    for part in str(cookie_header or "").split(";"):
        part = part.strip()
        if part.startswith(f"{SESSION_COOKIE}="):
            return part.split("=", 1)[1].strip()
    return ""


def delete_session_from_cookie(cookie_header):
    token = session_token_from_cookie(cookie_header)
    if token:
        _SESSIONS.pop(token, None)


def active_session_count():
    return len(_SESSIONS)
