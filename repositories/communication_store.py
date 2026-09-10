"""Communication history persistence.

Structured metadata only — for messages actually generated/saved through
SkillEdge. Status is DRAFT / COPIED / SENT / ARCHIVED; SENT is only ever set
when the app has real evidence the send action happened (the app never marks
a plain copy as sent).
"""

from __future__ import annotations

from contextlib import contextmanager
import os
import sqlite3
import threading


class CommunicationStore:
    def __init__(self, path: str):
        self.path = os.path.abspath(path)
        self._lock = threading.RLock()
        self.available = True
        try:
            os.makedirs(os.path.dirname(self.path), exist_ok=True)
            self._initialize()
        except (OSError, sqlite3.Error):
            self.available = False

    @contextmanager
    def _db(self):
        conn = sqlite3.connect(self.path, timeout=15)
        conn.row_factory = sqlite3.Row
        conn.execute("PRAGMA journal_mode=WAL")
        try:
            yield conn
            conn.commit()
        except Exception:
            conn.rollback()
            raise
        finally:
            conn.close()

    def _initialize(self):
        with self._lock, self._db() as db:
            db.executescript(
                """
                CREATE TABLE IF NOT EXISTS communication_messages (
                    id TEXT PRIMARY KEY,
                    manager_email TEXT NOT NULL,
                    recipient TEXT NOT NULL DEFAULT '',
                    recipient_type TEXT NOT NULL DEFAULT 'UNKNOWN',
                    channel TEXT NOT NULL DEFAULT 'MS_TEAMS_OR_VIBER',
                    purpose TEXT NOT NULL DEFAULT 'GENERAL_PROFESSIONAL',
                    related_entity_type TEXT NOT NULL DEFAULT '',
                    related_entity_id TEXT NOT NULL DEFAULT '',
                    message_text TEXT NOT NULL,
                    status TEXT NOT NULL DEFAULT 'DRAFT',
                    created_at TEXT NOT NULL
                );
                CREATE INDEX IF NOT EXISTS idx_comm_scope
                    ON communication_messages(manager_email, created_at);
                """
            )

    def create(self, entry: dict) -> dict:
        if not self.available:
            return dict(entry or {})
        e = dict(entry or {})
        try:
            with self._lock, self._db() as db:
                db.execute(
                    "INSERT OR REPLACE INTO communication_messages "
                    "(id,manager_email,recipient,recipient_type,channel,purpose,"
                    "related_entity_type,related_entity_id,message_text,status,created_at) "
                    "VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                    (
                        e.get("id", ""),
                        e.get("manager_email", ""),
                        e.get("recipient", ""),
                        e.get("recipient_type", "UNKNOWN"),
                        e.get("channel", "MS_TEAMS_OR_VIBER"),
                        e.get("purpose", "GENERAL_PROFESSIONAL"),
                        e.get("related_entity_type", ""),
                        e.get("related_entity_id", ""),
                        e.get("message_text", ""),
                        e.get("status", "DRAFT"),
                        e.get("created_at", ""),
                    ),
                )
        except sqlite3.Error:
            pass
        return e

    def list(self, manager_email: str, limit: int = 50) -> list:
        if not self.available:
            return []
        try:
            with self._lock, self._db() as db:
                rows = db.execute(
                    "SELECT * FROM communication_messages WHERE manager_email=? "
                    "ORDER BY created_at DESC LIMIT ?",
                    (str(manager_email or "").strip().lower(), int(limit) or 50),
                ).fetchall()
            return [self._row(r) for r in rows]
        except sqlite3.Error:
            return []

    @staticmethod
    def _row(r):
        return {
            "id": r["id"],
            "manager_email": r["manager_email"],
            "recipient": r["recipient"],
            "recipientType": r["recipient_type"],
            "channel": r["channel"],
            "purpose": r["purpose"],
            "relatedEntityType": r["related_entity_type"],
            "relatedEntityId": r["related_entity_id"],
            "message": r["message_text"],
            "status": r["status"],
            "createdAt": r["created_at"],
        }