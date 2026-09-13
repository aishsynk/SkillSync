"""Capability Intelligence persistence.

Six normalized tables, one small SQLite file, following the same conventions
as `OpportunityStore`/`DevPlanStore`/`ActionStore`: a module lock, WAL mode,
and `available=False` (never raise) on a read-only filesystem.

Course capability requirements are keyed on `rms_cid` — the RMS numeric
course ID — never on course title or `course_code`. Both were confirmed live
(2026-09-13 Phase 0 verification) to be unreliable: `course_code` is often
blank or inconsistently cased, and multiple catalogue rows can share a title.

No row in `trainer_capabilities` may exist without at least one linked
`capability_evidence` row — the store enforces this in
`upsert_trainer_capability`, not just by convention. This is the persistence
half of "NO VERIFIED DATA -> NO CAPABILITY CLAIM."
"""

from __future__ import annotations

from contextlib import contextmanager
from datetime import datetime, timezone
import json
import os
import sqlite3
import threading

from domain.capability.models import MappingStatus


def _now():
    return datetime.now(timezone.utc).isoformat()


class CapabilityStore:
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
        connection = sqlite3.connect(self.path, timeout=15)
        connection.row_factory = sqlite3.Row
        connection.execute("PRAGMA journal_mode=WAL")
        connection.execute("PRAGMA foreign_keys=ON")
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
                CREATE TABLE IF NOT EXISTS capabilities (
                    id TEXT PRIMARY KEY,
                    name TEXT NOT NULL,
                    family TEXT NOT NULL DEFAULT '',
                    description TEXT NOT NULL DEFAULT '',
                    created_at TEXT NOT NULL
                );

                CREATE TABLE IF NOT EXISTS capability_aliases (
                    capability_id TEXT NOT NULL REFERENCES capabilities(id),
                    alias TEXT NOT NULL,
                    PRIMARY KEY (capability_id, alias)
                );

                CREATE TABLE IF NOT EXISTS capability_relationships (
                    from_id TEXT NOT NULL REFERENCES capabilities(id),
                    to_id   TEXT NOT NULL REFERENCES capabilities(id),
                    type    TEXT NOT NULL,
                    PRIMARY KEY (from_id, to_id, type)
                );

                -- One row per (course, status-version). We keep history by
                -- inserting a new row on every status transition rather than
                -- overwriting, so "who approved this and when" is never lost.
                CREATE TABLE IF NOT EXISTS course_capabilities (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    rms_cid INTEGER NOT NULL,
                    course_code TEXT NOT NULL DEFAULT '',
                    course_title TEXT NOT NULL DEFAULT '',
                    vendor TEXT NOT NULL DEFAULT '',
                    requirements_json TEXT NOT NULL DEFAULT '[]',
                    required_certifications_json TEXT NOT NULL DEFAULT '[]',
                    status TEXT NOT NULL DEFAULT 'DRAFT',
                    source TEXT NOT NULL DEFAULT '',
                    updated_by TEXT NOT NULL DEFAULT '',
                    updated_at TEXT NOT NULL
                );
                CREATE INDEX IF NOT EXISTS idx_course_capabilities_cid
                    ON course_capabilities(rms_cid, updated_at DESC);

                CREATE TABLE IF NOT EXISTS trainer_capabilities (
                    trainer_email TEXT NOT NULL,
                    capability_id TEXT NOT NULL REFERENCES capabilities(id),
                    proficiency TEXT,
                    verified INTEGER NOT NULL DEFAULT 0,
                    last_verified_at TEXT NOT NULL DEFAULT '',
                    PRIMARY KEY (trainer_email, capability_id)
                );

                CREATE TABLE IF NOT EXISTS capability_evidence (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    trainer_email TEXT NOT NULL,
                    capability_id TEXT NOT NULL REFERENCES capabilities(id),
                    type TEXT NOT NULL,
                    source_system TEXT NOT NULL DEFAULT '',
                    source_record_id TEXT NOT NULL DEFAULT '',
                    captured_at TEXT NOT NULL,
                    raw_json TEXT NOT NULL DEFAULT '{}',
                    FOREIGN KEY (trainer_email, capability_id)
                        REFERENCES trainer_capabilities(trainer_email, capability_id)
                );
                CREATE INDEX IF NOT EXISTS idx_evidence_trainer_cap
                    ON capability_evidence(trainer_email, capability_id);
            """)

    # ── Capabilities / aliases / relationships ──────────────────────────────

    def add_capability(self, capability_id, name, family="", description=""):
        if not self.available:
            return False
        try:
            with self._lock, self._db() as db:
                db.execute(
                    "INSERT OR IGNORE INTO capabilities (id,name,family,description,created_at) "
                    "VALUES (?,?,?,?,?)",
                    (capability_id, name, family, description, _now()),
                )
            return True
        except sqlite3.Error:
            return False

    def add_alias(self, capability_id, alias):
        if not self.available:
            return False
        try:
            with self._lock, self._db() as db:
                db.execute(
                    "INSERT OR IGNORE INTO capability_aliases (capability_id,alias) VALUES (?,?)",
                    (capability_id, alias),
                )
            return True
        except sqlite3.Error:
            return False

    def add_relationship(self, from_id, to_id, rel_type):
        if not self.available:
            return False
        try:
            with self._lock, self._db() as db:
                db.execute(
                    "INSERT OR IGNORE INTO capability_relationships (from_id,to_id,type) VALUES (?,?,?)",
                    (from_id, to_id, rel_type),
                )
            return True
        except sqlite3.Error:
            return False

    def resolve_alias(self, text):
        """Exact alias/name lookup only — no fuzzy matching in the store."""
        if not self.available:
            return None
        t = str(text or "").strip()
        if not t:
            return None
        try:
            with self._lock, self._db() as db:
                row = db.execute(
                    "SELECT id FROM capabilities WHERE lower(name)=lower(?)", (t,),
                ).fetchone()
                if row:
                    return row["id"]
                row = db.execute(
                    "SELECT capability_id FROM capability_aliases WHERE lower(alias)=lower(?)", (t,),
                ).fetchone()
                return row["capability_id"] if row else None
        except sqlite3.Error:
            return None

    def relationships_for(self, capability_id):
        if not self.available:
            return []
        try:
            with self._lock, self._db() as db:
                rows = db.execute(
                    "SELECT from_id, to_id, type FROM capability_relationships "
                    "WHERE from_id=? OR to_id=?",
                    (capability_id, capability_id),
                ).fetchall()
            return [dict(r) for r in rows]
        except sqlite3.Error:
            return []

    # ── Course capability profile (versioned, status-gated) ─────────────────

    def upsert_course_capability(self, rms_cid, requirements, required_certifications=None,
                                  course_code="", course_title="", vendor="",
                                  status=MappingStatus.DRAFT, source="", updated_by=""):
        """Insert a new version row for this course. Never overwrites history —
        `latest_course_capability` always reads the most recent row per cid."""
        if not self.available:
            return False
        if status not in MappingStatus.ALL:
            raise ValueError(f"unknown status: {status}")
        try:
            with self._lock, self._db() as db:
                db.execute(
                    "INSERT INTO course_capabilities "
                    "(rms_cid, course_code, course_title, vendor, requirements_json, "
                    " required_certifications_json, status, source, updated_by, updated_at) "
                    "VALUES (?,?,?,?,?,?,?,?,?,?)",
                    (int(rms_cid), course_code, course_title, vendor,
                     json.dumps(requirements or [], ensure_ascii=False),
                     json.dumps(required_certifications or [], ensure_ascii=False),
                     status, source, updated_by, _now()),
                )
            return True
        except sqlite3.Error:
            return False

    def set_course_capability_status(self, rms_cid, status, updated_by=""):
        """Transition the latest version's status by inserting a new version
        row with the same content — preserves who approved/deprecated it and
        when, rather than mutating the prior row in place."""
        latest = self.latest_course_capability(rms_cid)
        if not latest:
            return False
        return self.upsert_course_capability(
            rms_cid=rms_cid,
            requirements=latest["requirements"],
            required_certifications=latest["required_certifications"],
            course_code=latest["course_code"],
            course_title=latest["course_title"],
            vendor=latest["vendor"],
            status=status,
            source=latest["source"],
            updated_by=updated_by or latest["updated_by"],
        )

    def latest_course_capability(self, rms_cid):
        if not self.available:
            return None
        try:
            with self._lock, self._db() as db:
                row = db.execute(
                    "SELECT * FROM course_capabilities WHERE rms_cid=? "
                    "ORDER BY updated_at DESC, id DESC LIMIT 1",
                    (int(rms_cid),),
                ).fetchone()
            return self._course_row(row) if row else None
        except sqlite3.Error:
            return None

    @staticmethod
    def _course_row(row):
        return {
            "rms_cid": row["rms_cid"],
            "course_code": row["course_code"],
            "course_title": row["course_title"],
            "vendor": row["vendor"],
            "requirements": json.loads(row["requirements_json"] or "[]"),
            "required_certifications": json.loads(row["required_certifications_json"] or "[]"),
            "status": row["status"],
            "source": row["source"],
            "updated_by": row["updated_by"],
            "updated_at": row["updated_at"],
        }

    # ── Trainer capability + evidence (evidence-gated) ──────────────────────

    def add_trainer_capability_evidence(self, trainer_email, capability_id, evidence_type,
                                         source_system="", source_record_id="", raw=None,
                                         proficiency=None):
        """The only way to create/extend a TrainerCapability. There is no path
        to set `verified=1` without inserting an evidence row in the same
        transaction — enforces NO VERIFIED DATA -> NO CAPABILITY CLAIM."""
        if not self.available:
            return False
        email = str(trainer_email or "").strip().lower()
        if not email or not capability_id or not evidence_type:
            return False
        try:
            with self._lock, self._db() as db:
                db.execute(
                    "INSERT OR IGNORE INTO trainer_capabilities "
                    "(trainer_email, capability_id, proficiency, verified, last_verified_at) "
                    "VALUES (?,?,?,0,'')",
                    (email, capability_id, proficiency),
                )
                db.execute(
                    "INSERT INTO capability_evidence "
                    "(trainer_email, capability_id, type, source_system, source_record_id, "
                    " captured_at, raw_json) VALUES (?,?,?,?,?,?,?)",
                    (email, capability_id, evidence_type, source_system, source_record_id,
                     _now(), json.dumps(raw or {}, ensure_ascii=False)),
                )
                now = _now()
                db.execute(
                    "UPDATE trainer_capabilities SET verified=1, last_verified_at=?, "
                    "proficiency=COALESCE(?, proficiency) "
                    "WHERE trainer_email=? AND capability_id=?",
                    (now, proficiency, email, capability_id),
                )
            return True
        except sqlite3.Error:
            return False

    def trainer_capabilities(self, trainer_email):
        """Only ever returns capabilities with at least one evidence row —
        the join with `capability_evidence` is what makes an orphaned
        `trainer_capabilities` row (which cannot exist via the write path
        above, but is defended here too) invisible to readers."""
        if not self.available:
            return []
        email = str(trainer_email or "").strip().lower()
        try:
            with self._lock, self._db() as db:
                rows = db.execute(
                    "SELECT tc.trainer_email, tc.capability_id, tc.proficiency, "
                    "       tc.verified, tc.last_verified_at "
                    "FROM trainer_capabilities tc "
                    "WHERE tc.trainer_email=? AND tc.verified=1 "
                    "AND EXISTS (SELECT 1 FROM capability_evidence ce "
                    "            WHERE ce.trainer_email=tc.trainer_email "
                    "            AND ce.capability_id=tc.capability_id)",
                    (email,),
                ).fetchall()
                out = []
                for r in rows:
                    ev_rows = db.execute(
                        "SELECT type, source_system, source_record_id, captured_at, raw_json "
                        "FROM capability_evidence WHERE trainer_email=? AND capability_id=?",
                        (email, r["capability_id"]),
                    ).fetchall()
                    out.append({
                        "trainer_email": r["trainer_email"],
                        "capability_id": r["capability_id"],
                        "proficiency": r["proficiency"],
                        "verified": bool(r["verified"]),
                        "last_verified_at": r["last_verified_at"],
                        "evidence": [
                            {
                                "type": e["type"], "source_system": e["source_system"],
                                "source_record_id": e["source_record_id"],
                                "captured_at": e["captured_at"],
                                "raw": json.loads(e["raw_json"] or "{}"),
                            } for e in ev_rows
                        ],
                    })
                return out
        except sqlite3.Error:
            return []
