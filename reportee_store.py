"""Reportee self-service persistence.

Three concerns, one sqlite file (mirrors action_store.py conventions):

  * directory     — who is a reportee and which manager owns them. Populated as
                    a side effect of a manager loading their roster; it is the
                    only place the app learns "email X is a reportee of Y",
                    because RMS `reportees` (key 82) is keyed by manager only.
  * credentials   — a salted PBKDF2 hash of the reportee's chosen password and a
                    must_change flag. First login is verified against the RMS
                    employee code (never stored here); no plaintext is persisted.
  * skill_requests — a reportee's request to be marked at a skill level above the
                    self-service ceiling (4). Nothing is written to RMS until a
                    manager approves; this table is that queue.
"""

from __future__ import annotations

from contextlib import contextmanager
from datetime import datetime, timezone
import hashlib
import hmac
import os
import secrets
import sqlite3
import threading


def _now() -> str:
    return datetime.now(timezone.utc).isoformat()


_PBKDF2_ROUNDS = 200_000


def _hash_password(password: str, salt: str) -> str:
    dk = hashlib.pbkdf2_hmac(
        "sha256", password.encode("utf-8"), salt.encode("utf-8"), _PBKDF2_ROUNDS
    )
    return dk.hex()


class ReporteeStore:
    def __init__(self, path: str):
        self.path = os.path.abspath(path)
        self._lock = threading.RLock()
        os.makedirs(os.path.dirname(self.path) or ".", exist_ok=True)
        self._initialize()

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
            db.executescript(
                """
                CREATE TABLE IF NOT EXISTS directory (
                    reportee_email TEXT PRIMARY KEY,
                    manager_email  TEXT NOT NULL,
                    name           TEXT NOT NULL DEFAULT '',
                    emp_id         TEXT NOT NULL DEFAULT '',
                    trainer_plus   INTEGER NOT NULL DEFAULT 0,
                    designation    TEXT NOT NULL DEFAULT '',
                    is_direct      INTEGER NOT NULL DEFAULT 0,
                    updated_at     TEXT NOT NULL
                );
                CREATE TABLE IF NOT EXISTS credentials (
                    reportee_email TEXT PRIMARY KEY,
                    salt           TEXT NOT NULL,
                    pass_hash      TEXT NOT NULL,
                    must_change    INTEGER NOT NULL DEFAULT 0,
                    updated_at     TEXT NOT NULL
                );
                CREATE TABLE IF NOT EXISTS skill_requests (
                    id              TEXT PRIMARY KEY,
                    reportee_email  TEXT NOT NULL,
                    manager_email   TEXT NOT NULL,
                    course_id       TEXT NOT NULL,
                    course_name     TEXT NOT NULL DEFAULT '',
                    requested_level INTEGER NOT NULL,
                    from_date       TEXT NOT NULL DEFAULT '',
                    status          TEXT NOT NULL DEFAULT 'pending',
                    rms_message     TEXT NOT NULL DEFAULT '',
                    created_at      TEXT NOT NULL,
                    resolved_at     TEXT NOT NULL DEFAULT ''
                );
                CREATE INDEX IF NOT EXISTS idx_skill_requests_manager
                    ON skill_requests(manager_email, status, created_at);
                CREATE INDEX IF NOT EXISTS idx_skill_requests_reportee
                    ON skill_requests(reportee_email, created_at);
                """
            )
            # Migrate pre-existing directory tables that predate these columns.
            cols = {r[1] for r in db.execute("PRAGMA table_info(directory)").fetchall()}
            if "designation" not in cols:
                db.execute("ALTER TABLE directory ADD COLUMN designation TEXT NOT NULL DEFAULT ''")
            if "is_direct" not in cols:
                db.execute("ALTER TABLE directory ADD COLUMN is_direct INTEGER NOT NULL DEFAULT 0")

    # ── directory ────────────────────────────────────────────────────────────
    def remember_roster(self, manager_email: str, rows: list) -> None:
        """Upsert one directory row per reportee in a manager's RMS roster."""
        manager_email = str(manager_email or "").strip().lower()
        if not manager_email or not isinstance(rows, list):
            return
        now = _now()
        clean = []
        for r in rows:
            if not isinstance(r, dict):
                continue
            email = str(r.get("OffEmail", "") or "").strip().lower()
            if not email or "@" not in email:
                continue
            clean.append(
                (
                    email,
                    manager_email,
                    " ".join(str(r.get("TrainerName", "") or "").split()),
                    str(r.get("EmpId", "") or "").strip(),
                    1 if str(r.get("TrainerPlus", "") or "").strip().lower() == "yes" else 0,
                    " ".join(str(r.get("Designation", "") or "").split()),
                    1 if str(r.get("IsdirectReportee", "") or "").strip().lower() == "yes" else 0,
                    now,
                )
            )
        if not clean:
            return
        with self._lock, self._db() as db:
            db.executemany(
                """
                INSERT INTO directory(reportee_email,manager_email,name,emp_id,trainer_plus,designation,is_direct,updated_at)
                VALUES(?,?,?,?,?,?,?,?)
                ON CONFLICT(reportee_email) DO UPDATE SET
                  manager_email=excluded.manager_email, name=excluded.name,
                  emp_id=CASE WHEN excluded.emp_id != '' THEN excluded.emp_id ELSE directory.emp_id END,
                  trainer_plus=excluded.trainer_plus, designation=excluded.designation,
                  is_direct=excluded.is_direct, updated_at=excluded.updated_at
                """,
                clean,
            )

    def lookup(self, email: str) -> dict | None:
        email = str(email or "").strip().lower()
        if not email:
            return None
        with self._lock, self._db() as db:
            row = db.execute(
                "SELECT * FROM directory WHERE reportee_email=?", (email,)
            ).fetchone()
            return dict(row) if row else None

    # ── credentials ──────────────────────────────────────────────────────────
    def credential(self, email: str) -> dict | None:
        email = str(email or "").strip().lower()
        with self._lock, self._db() as db:
            row = db.execute(
                "SELECT * FROM credentials WHERE reportee_email=?", (email,)
            ).fetchone()
            return dict(row) if row else None

    def set_password(self, email: str, password: str, must_change: bool = False) -> None:
        email = str(email or "").strip().lower()
        salt = secrets.token_hex(16)
        with self._lock, self._db() as db:
            db.execute(
                """
                INSERT INTO credentials(reportee_email,salt,pass_hash,must_change,updated_at)
                VALUES(?,?,?,?,?)
                ON CONFLICT(reportee_email) DO UPDATE SET
                  salt=excluded.salt, pass_hash=excluded.pass_hash,
                  must_change=excluded.must_change, updated_at=excluded.updated_at
                """,
                (email, salt, _hash_password(password, salt), 1 if must_change else 0, _now()),
            )

    def verify_password(self, email: str, password: str) -> bool:
        cred = self.credential(email)
        if not cred:
            return False
        candidate = _hash_password(password, cred["salt"])
        return hmac.compare_digest(candidate, cred["pass_hash"])

    # ── skill requests ───────────────────────────────────────────────────────
    def add_request(
        self,
        reportee_email: str,
        manager_email: str,
        course_id: str,
        course_name: str,
        requested_level: int,
        from_date: str,
    ) -> dict:
        rid = "skreq_" + secrets.token_hex(8)
        row = (
            rid,
            str(reportee_email or "").strip().lower(),
            str(manager_email or "").strip().lower(),
            str(course_id or "").strip(),
            str(course_name or "").strip(),
            int(requested_level),
            str(from_date or "").strip(),
            "pending",
            "",
            _now(),
            "",
        )
        with self._lock, self._db() as db:
            db.execute(
                """
                INSERT INTO skill_requests(id,reportee_email,manager_email,course_id,course_name,
                    requested_level,from_date,status,rms_message,created_at,resolved_at)
                VALUES(?,?,?,?,?,?,?,?,?,?,?)
                """,
                row,
            )
        return self.get_request(rid)

    def get_request(self, request_id: str) -> dict | None:
        with self._lock, self._db() as db:
            r = db.execute(
                "SELECT * FROM skill_requests WHERE id=?", (str(request_id or ""),)
            ).fetchone()
            return dict(r) if r else None

    def list_for_manager(self, manager_email: str, status: str = "pending") -> list:
        manager_email = str(manager_email or "").strip().lower()
        with self._lock, self._db() as db:
            if status:
                rows = db.execute(
                    "SELECT * FROM skill_requests WHERE manager_email=? AND status=? ORDER BY created_at DESC",
                    (manager_email, status),
                ).fetchall()
            else:
                rows = db.execute(
                    "SELECT * FROM skill_requests WHERE manager_email=? ORDER BY created_at DESC",
                    (manager_email,),
                ).fetchall()
            return [dict(r) for r in rows]

    def list_for_reportee(self, reportee_email: str) -> list:
        reportee_email = str(reportee_email or "").strip().lower()
        with self._lock, self._db() as db:
            rows = db.execute(
                "SELECT * FROM skill_requests WHERE reportee_email=? ORDER BY created_at DESC",
                (reportee_email,),
            ).fetchall()
            return [dict(r) for r in rows]

    def pending_count(self, manager_email: str) -> int:
        return len(self.list_for_manager(manager_email, "pending"))

    def resolve(self, request_id: str, status: str, rms_message: str = "") -> dict | None:
        with self._lock, self._db() as db:
            db.execute(
                "UPDATE skill_requests SET status=?, rms_message=?, resolved_at=? WHERE id=?",
                (status, str(rms_message or ""), _now(), str(request_id or "")),
            )
        return self.get_request(request_id)
