"""Per-trainer development-plan persistence.

A lightweight sibling of ActionStore: one small SQLite file, one table, one
module-level instance guarded by a lock. The plan is manager-authored — prep
and coaching goals for a trainer — never generated prose and never an
allocation. A read-only filesystem must not break the request, exactly as
ActionStore tolerates.
"""

from __future__ import annotations

from contextlib import contextmanager
from datetime import datetime, timezone
import os
import sqlite3
import threading


def _now():
    return datetime.now(timezone.utc).isoformat()


VALID_KINDS = ("certification", "coaching", "portfolio", "other")
VALID_STATUSES = ("open", "in_progress", "done", "dropped")

_COLUMNS = ("id", "manager_email", "trainer_email", "title", "kind", "status",
           "target_date", "note", "created_at", "updated_at")


class DevPlanStore:
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
                CREATE TABLE IF NOT EXISTS dev_plan_items (
                    id TEXT PRIMARY KEY,
                    manager_email TEXT,
                    trainer_email TEXT,
                    title TEXT,
                    kind TEXT,
                    status TEXT,
                    target_date TEXT,
                    note TEXT,
                    created_at TEXT,
                    updated_at TEXT
                );
                CREATE INDEX IF NOT EXISTS idx_dev_plan_scope
                    ON dev_plan_items(manager_email, trainer_email);
            """)

    @staticmethod
    def _row(r):
        return {k: (r[k] if r[k] is not None else "") for k in _COLUMNS}

    def list_items(self, manager_email, trainer_email):
        if not self.available:
            return []
        m = str(manager_email or "").strip().lower()
        t = str(trainer_email or "").strip().lower()
        try:
            with self._lock, self._db() as db:
                rows = db.execute(
                    "SELECT * FROM dev_plan_items WHERE manager_email=? AND trainer_email=? "
                    "ORDER BY created_at",
                    (m, t),
                ).fetchall()
            return [self._row(r) for r in rows]
        except sqlite3.Error:
            return []

    def create(self, manager_email, trainer_email, title, kind,
               target_date="", note=""):
        now = _now()
        item = {
            "id": "dpi_" + os.urandom(8).hex(),
            "manager_email": str(manager_email or "").strip().lower(),
            "trainer_email": str(trainer_email or "").strip().lower(),
            "title": str(title or "").strip(),
            "kind": kind,
            "status": "open",
            "target_date": str(target_date or "").strip(),
            "note": str(note or "").strip(),
            "created_at": now,
            "updated_at": now,
        }
        if not self.available:
            return item
        with self._lock, self._db() as db:
            db.execute(
                "INSERT INTO dev_plan_items "
                "(id,manager_email,trainer_email,title,kind,status,target_date,note,created_at,updated_at) "
                "VALUES (?,?,?,?,?,?,?,?,?,?)",
                tuple(item[k] for k in _COLUMNS),
            )
        return item

    def get(self, manager_email, item_id):
        if not self.available:
            return None
        m = str(manager_email or "").strip().lower()
        try:
            with self._lock, self._db() as db:
                r = db.execute(
                    "SELECT * FROM dev_plan_items WHERE id=? AND manager_email=?",
                    (str(item_id or ""), m),
                ).fetchone()
            return self._row(r) if r else None
        except sqlite3.Error:
            return None

    def update(self, manager_email, item_id, status=None, note=None, target_date=None):
        m = str(manager_email or "").strip().lower()
        current = self.get(m, item_id)
        if current is None:
            return None
        fields = {}
        if status is not None:
            fields["status"] = status
        if note is not None:
            fields["note"] = str(note).strip()
        if target_date is not None:
            fields["target_date"] = str(target_date).strip()
        fields["updated_at"] = _now()
        with self._lock, self._db() as db:
            db.execute(
                "UPDATE dev_plan_items SET %s WHERE id=? AND manager_email=?"
                % ", ".join("%s=?" % k for k in fields),
                tuple(fields.values()) + (str(item_id), m),
            )
        return self.get(m, item_id)
