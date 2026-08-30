"""Transactional manager-action persistence with an append-only audit trail."""

from __future__ import annotations

from contextlib import contextmanager
from datetime import datetime, timezone
import json
import os
import sqlite3
import threading


def _now():
    return datetime.now(timezone.utc).isoformat()


class ActionStore:
    def __init__(self, path: str, legacy_json: str | None = None):
        self.path = os.path.abspath(path)
        self.legacy_json = legacy_json
        self._lock = threading.RLock()
        os.makedirs(os.path.dirname(self.path), exist_ok=True)
        self._initialize()
        self._migrate_legacy_once()

    @contextmanager
    def _db(self):
        connection = sqlite3.connect(self.path, timeout=15)
        connection.row_factory = sqlite3.Row
        connection.execute("PRAGMA foreign_keys=ON")
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
                CREATE TABLE IF NOT EXISTS action_records (
                    manager_email TEXT NOT NULL,
                    action_id TEXT NOT NULL,
                    source TEXT NOT NULL,
                    record_json TEXT NOT NULL,
                    created_at TEXT NOT NULL,
                    updated_at TEXT NOT NULL,
                    PRIMARY KEY (manager_email, action_id)
                );
                CREATE TABLE IF NOT EXISTS action_states (
                    manager_email TEXT NOT NULL,
                    action_id TEXT NOT NULL,
                    state TEXT NOT NULL DEFAULT 'open',
                    assignee TEXT NOT NULL DEFAULT '',
                    due_date TEXT NOT NULL DEFAULT '',
                    updated_at TEXT NOT NULL,
                    PRIMARY KEY (manager_email, action_id)
                );
                CREATE TABLE IF NOT EXISTS action_events (
                    event_id INTEGER PRIMARY KEY AUTOINCREMENT,
                    manager_email TEXT NOT NULL,
                    action_id TEXT NOT NULL,
                    event_type TEXT NOT NULL,
                    actor_email TEXT NOT NULL,
                    payload_json TEXT NOT NULL,
                    created_at TEXT NOT NULL
                );
                CREATE INDEX IF NOT EXISTS idx_action_events_scope
                    ON action_events(manager_email, action_id, event_id);
                CREATE TABLE IF NOT EXISTS action_metadata (
                    key TEXT PRIMARY KEY,
                    value TEXT NOT NULL
                );
            """)

    def _migrate_legacy_once(self):
        if not self.legacy_json or not os.path.exists(self.legacy_json):
            return
        with self._lock, self._db() as db:
            if db.execute("SELECT 1 FROM action_metadata WHERE key='legacy_json_migrated'").fetchone():
                return
            try:
                with open(self.legacy_json, encoding="utf-8") as handle:
                    legacy = json.load(handle)
            except (OSError, ValueError):
                legacy = {}
            for action_id, record in (legacy.get("raised") or {}).items():
                manager = str(record.get("manager_email", "") or "").strip().lower()
                if manager:
                    self._put_record(db, manager, action_id, record)
                    self._put_state(db, manager, action_id, record)
            # Old derived state had no manager key. Preserve it under a wildcard
            # for read compatibility; the next scoped mutation writes a proper row.
            for action_id, record in (legacy.get("states") or {}).items():
                self._put_state(db, "*", action_id, record)
            db.execute("INSERT OR REPLACE INTO action_metadata(key,value) VALUES('legacy_json_migrated',?)", (_now(),))

    def _put_record(self, db, manager, action_id, record):
        now = str(record.get("updated_at") or _now())
        created = str(record.get("created_at") or now)
        db.execute("""
            INSERT INTO action_records(manager_email,action_id,source,record_json,created_at,updated_at)
            VALUES(?,?,?,?,?,?)
            ON CONFLICT(manager_email,action_id) DO UPDATE SET
              record_json=excluded.record_json, updated_at=excluded.updated_at
        """, (manager, action_id, str(record.get("source") or "raised"), json.dumps(record), created, now))

    def _put_state(self, db, manager, action_id, record):
        state = str(record.get("lifecycle_state") or record.get("state") or "open")
        db.execute("""
            INSERT INTO action_states(manager_email,action_id,state,assignee,due_date,updated_at)
            VALUES(?,?,?,?,?,?)
            ON CONFLICT(manager_email,action_id) DO UPDATE SET
              state=excluded.state, assignee=excluded.assignee,
              due_date=excluded.due_date, updated_at=excluded.updated_at
        """, (manager, action_id, state, str(record.get("assignee") or ""),
              str(record.get("due_date") or ""), str(record.get("updated_at") or _now())))

    def _event(self, db, manager, action_id, kind, actor, payload, at=None):
        db.execute("""
            INSERT INTO action_events(manager_email,action_id,event_type,actor_email,payload_json,created_at)
            VALUES(?,?,?,?,?,?)
        """, (manager, action_id, kind, actor, json.dumps(payload), at or _now()))

    def list_raised(self, manager):
        with self._lock, self._db() as db:
            rows = db.execute("SELECT record_json FROM action_records WHERE manager_email=? ORDER BY created_at DESC", (manager,)).fetchall()
            return [json.loads(row["record_json"]) for row in rows]

    def overlay(self, manager, actions):
        if not actions:
            return actions
        with self._lock, self._db() as db:
            for action in actions:
                action_id = str(action.get("id") or "")
                state = db.execute("""
                    SELECT * FROM action_states WHERE action_id=? AND manager_email IN (?, '*')
                    ORDER BY CASE manager_email WHEN ? THEN 0 ELSE 1 END LIMIT 1
                """, (action_id, manager, manager)).fetchone()
                events = self._audit_rows(db, manager, action_id)
                if state:
                    action.update({"lifecycle_state": state["state"], "assignee": state["assignee"],
                                   "due_date": state["due_date"], "updated_at": state["updated_at"]})
                else:
                    action.setdefault("lifecycle_state", "open")
                action["notes"] = [e["payload"] for e in events if e["event_type"] == "note_added"]
                action["history"] = events
        return actions

    def raise_action(self, manager, record, actor):
        with self._lock, self._db() as db:
            self._put_record(db, manager, record["id"], record)
            self._put_state(db, manager, record["id"], record)
            self._event(db, manager, record["id"], "action_raised", actor, {"title": record.get("title"), "priority": record.get("priority")}, record.get("created_at"))
        return record

    def transition(self, manager, action_id, state, actor, assignee="", due_date="", note=""):
        now = _now()
        with self._lock, self._db() as db:
            current = db.execute("SELECT * FROM action_states WHERE manager_email=? AND action_id=?", (manager, action_id)).fetchone()
            record = {"state": state, "assignee": assignee or (current["assignee"] if current else ""),
                      "due_date": due_date or (current["due_date"] if current else ""), "updated_at": now}
            self._put_state(db, manager, action_id, record)
            self._event(db, manager, action_id, "state_changed", actor, {"state": state, "assignee": record["assignee"], "due_date": record["due_date"], "note": note}, now)
            if note:
                self._event(db, manager, action_id, "note_added", actor, {"text": note, "by": actor, "at": now}, now)
            raised = db.execute("SELECT record_json FROM action_records WHERE manager_email=? AND action_id=?", (manager, action_id)).fetchone()
            if raised:
                obj = json.loads(raised["record_json"]); obj.update({"lifecycle_state": state, "assignee": record["assignee"], "due_date": record["due_date"], "updated_at": now})
                self._put_record(db, manager, action_id, obj)
        return {"id": action_id, **record, "history": self.audit(manager, action_id)}

    def add_note(self, manager, action_id, text, actor):
        now = _now(); payload = {"text": text, "by": actor, "at": now}
        with self._lock, self._db() as db:
            self._event(db, manager, action_id, "note_added", actor, payload, now)
        return payload

    def _audit_rows(self, db, manager, action_id):
        rows = db.execute("SELECT * FROM action_events WHERE manager_email=? AND action_id=? ORDER BY event_id", (manager, action_id)).fetchall()
        return [{"event_id": r["event_id"], "event_type": r["event_type"], "actor_email": r["actor_email"],
                 "payload": json.loads(r["payload_json"]), "created_at": r["created_at"]} for r in rows]

    def audit(self, manager, action_id):
        with self._lock, self._db() as db:
            return self._audit_rows(db, manager, action_id)

    def status(self):
        durable = os.getenv("SKILLEDGE_DURABLE_STATE", "").strip().lower() in {"1", "true", "yes"}
        return {"engine": "sqlite", "transactional": True, "audit_log": True,
                "durable_across_deploys": durable,
                "durability": "persistent_volume" if durable else "local_ephemeral"}


class SessionRevocationStore:
    """Process-safe denylist for signed sessions.

    Signed tokens can be reconstructed after an app worker restart, so removing
    one from an in-memory dictionary is not a logout. Store only a SHA-256
    digest of the bearer token and its natural expiry; no credential or raw
    session token is persisted.
    """

    def __init__(self, path: str):
        self.path = os.path.abspath(path)
        self._lock = threading.RLock()
        os.makedirs(os.path.dirname(self.path), exist_ok=True)
        with self._lock, self._db() as db:
            db.execute("""
                CREATE TABLE IF NOT EXISTS revoked_sessions (
                    token_hash TEXT PRIMARY KEY,
                    expires_at INTEGER NOT NULL,
                    revoked_at TEXT NOT NULL
                )
            """)

    @contextmanager
    def _db(self):
        connection = sqlite3.connect(self.path, timeout=15)
        try:
            yield connection
            connection.commit()
        except Exception:
            connection.rollback()
            raise
        finally:
            connection.close()

    @staticmethod
    def _digest(token: str) -> str:
        import hashlib
        return hashlib.sha256(token.encode("utf-8")).hexdigest()

    def revoke(self, token: str, expires_at: int):
        if not token:
            return
        with self._lock, self._db() as db:
            db.execute("DELETE FROM revoked_sessions WHERE expires_at < strftime('%s','now')")
            db.execute(
                "INSERT OR REPLACE INTO revoked_sessions(token_hash,expires_at,revoked_at) VALUES(?,?,?)",
                (self._digest(token), int(expires_at), _now()),
            )

    def is_revoked(self, token: str) -> bool:
        if not token:
            return False
        with self._lock, self._db() as db:
            row = db.execute(
                "SELECT 1 FROM revoked_sessions WHERE token_hash=? AND expires_at>=strftime('%s','now')",
                (self._digest(token),),
            ).fetchone()
            return row is not None
