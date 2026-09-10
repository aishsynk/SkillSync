"""Opportunity Guardian persistence.

A lightweight sibling of DevPlanStore: one small SQLite file, two tables (one
for opportunities, one for the per-manager guardian config), all guarded by a
module lock. A read-only filesystem must not break the request, exactly as
ActionStore/DevPlanStore tolerate.

Opportunity rows keep the wide, list-valued opportunity record as a JSON blob
with `status` denormalized into a column so status filters stay cheap SQL.
"""

from __future__ import annotations

from contextlib import contextmanager
from datetime import datetime, timezone
import json
import os
import sqlite3
import threading


def _now():
    return datetime.now(timezone.utc).isoformat()


_OPPORTUNITY_COLUMNS = ("id", "manager_email", "status", "payload", "created_at")


class OpportunityStore:
    def __init__(self, path: str):
        self.path = os.path.abspath(path)
        self._lock = threading.RLock()
        self.available = True
        try:
            os.makedirs(os.path.dirname(self.path), exist_ok=True)
            self._initialize()
        except (OSError, sqlite3.Error):
            # A read-only filesystem must not break import or the request.
            self.available = False

    @contextmanager
    def _db(self):
        connection = sqlite3.connect(self.path, timeout=15)
        connection.row_factory = sqlite3.Row
        connection.execute("PRAGMA journal_mode=WAL")
        try:
            yield connection
            connection.commit()
        except Exception:
            connection.rollback()
            raise
        finally:
            connection.close()

    def _initialize(self):
        with self._lock, self._db() as db:
            db.executescript("""
                CREATE TABLE IF NOT EXISTS opportunities (
                    id TEXT PRIMARY KEY,
                    manager_email TEXT NOT NULL,
                    status TEXT NOT NULL DEFAULT 'detected',
                    payload TEXT NOT NULL,
                    created_at TEXT NOT NULL
                );
                CREATE INDEX IF NOT EXISTS idx_opp_scope
                    ON opportunities(manager_email, status, created_at);
                CREATE TABLE IF NOT EXISTS guardian_configs (
                    manager_email TEXT PRIMARY KEY,
                    config TEXT NOT NULL,
                    updated_at TEXT NOT NULL
                );
            """)

    # ── Opportunities ────────────────────────────────────────────────────────

    def list(self, manager_email, status=""):
        if not self.available:
            return []
        m = str(manager_email or "").strip().lower()
        try:
            with self._lock, self._db() as db:
                if status:
                    rows = db.execute(
                        "SELECT * FROM opportunities WHERE manager_email=? AND status=? "
                        "ORDER BY created_at DESC",
                        (m, str(status)),
                    ).fetchall()
                else:
                    rows = db.execute(
                        "SELECT * FROM opportunities WHERE manager_email=? ORDER BY created_at DESC",
                        (m,),
                    ).fetchall()
            return [self._row(r) for r in rows]
        except sqlite3.Error:
            return []

    def create(self, manager_email, payload):
        if not self.available:
            return dict(payload or {})
        m = str(manager_email or "").strip().lower()
        data = dict(payload or {})
        opp_id = str(data.get("id") or "").strip() or ("SE-" + os.urandom(6).hex())
        status = str(data.get("status") or "detected").strip() or "detected"
        data["id"] = opp_id
        data["status"] = status
        data["created_at"] = data.get("created_at") or _now()
        data["detected_at"] = data.get("detected_at") or data["created_at"]
        if "evidence" not in data:
            data["evidence"] = []
        if not isinstance(data.get("strong_areas"), list):
            data["strong_areas"] = []
        if not isinstance(data.get("weak_areas"), list):
            data["weak_areas"] = []
        try:
            with self._lock, self._db() as db:
                db.execute(
                    "INSERT OR REPLACE INTO opportunities "
                    "(id,manager_email,status,payload,created_at) VALUES (?,?,?,?,?)",
                    (opp_id, m, status, json.dumps(data, ensure_ascii=False), data["created_at"]),
                )
        except sqlite3.Error:
            pass
        return data

    def set_status(self, manager_email, opp_id, status):
        if not self.available:
            return False
        m = str(manager_email or "").strip().lower()
        try:
            with self._lock, self._db() as db:
                cur = db.execute(
                    "UPDATE opportunities SET status=? WHERE id=? AND manager_email=?",
                    (str(status), str(opp_id), m),
                )
            return cur.rowcount > 0
        except sqlite3.Error:
            return False

    def patch(self, manager_email, opp_id, updates):
        """Merge [updates] into one opportunity row; returns the updated row."""
        m = str(manager_email or "").strip().lower()
        item = next((r for r in self.list(m, "") if r["id"] == opp_id), None)
        if not item:
            return None
        item.update(updates or {})
        return self.create(m, item)

    @staticmethod
    def _row(r):
        payload = {}
        try:
            payload = json.loads(r["payload"] or "{}")
        except (ValueError, TypeError):
            payload = {}
        payload["id"] = r["id"]
        payload["status"] = r["status"]
        payload["created_at"] = r["created_at"]
        payload["detected_at"] = payload.get("detected_at") or r["created_at"]
        return payload

    # ── Guardian config ──────────────────────────────────────────────────────

    def get_config(self, manager_email):
        if not self.available:
            return None
        m = str(manager_email or "").strip().lower()
        try:
            with self._lock, self._db() as db:
                r = db.execute(
                    "SELECT config FROM guardian_configs WHERE manager_email=?", (m,),
                ).fetchone()
            if not r:
                return None
            return json.loads(r["config"] or "null")
        except (sqlite3.Error, ValueError):
            return None

    def save_config(self, manager_email, config):
        if not self.available:
            return
        m = str(manager_email or "").strip().lower()
        try:
            with self._lock, self._db() as db:
                db.execute(
                    "INSERT OR REPLACE INTO guardian_configs "
                    "(manager_email,config,updated_at) VALUES (?,?,?)",
                    (m, json.dumps(config, ensure_ascii=False), _now()),
                )
        except sqlite3.Error:
            pass