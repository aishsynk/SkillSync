"""File-backed refresh and knowledge-base service for SkillEdge."""

from __future__ import annotations

import json
import threading
import time
from dataclasses import dataclass, asdict
from datetime import datetime, timedelta, timezone
from pathlib import Path
from typing import Any

from services.cache_service import cache_path, read_cache, write_cache

REPO_ROOT = Path(__file__).resolve().parents[2]
RUNTIME_DIR = REPO_ROOT / "runtime"
REFRESH_DIR = RUNTIME_DIR / "refresh"
KB_DIR = RUNTIME_DIR / "knowledge_base"
STATE_FILE = REFRESH_DIR / "refresh_state.json"
LOG_FILE = REFRESH_DIR / "refresh_logs.jsonl"
SNAPSHOT_DIR = KB_DIR / "snapshots"
INDEX_FILE = KB_DIR / "index.json"
LOCK = threading.Lock()
RUNNING = False

for _dir in (REFRESH_DIR, KB_DIR, SNAPSHOT_DIR):
    _dir.mkdir(parents=True, exist_ok=True)


def _now() -> datetime:
    return datetime.now(timezone.utc)


def _ts(dt: datetime | None = None) -> str:
    return (dt or _now()).isoformat()


def _safe_json_write(path: Path, obj: Any) -> None:
    if path.suffix == ".jsonl" and isinstance(obj, list):
        path.write_text("\n".join(json.dumps(item, default=str) for item in obj), encoding="utf-8")
    else:
        path.write_text(json.dumps(obj, indent=2, default=str), encoding="utf-8")


def _safe_json_read(path: Path, default: Any) -> Any:
    try:
        if path.suffix == ".jsonl":
            return [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines() if line.strip()]
        return json.loads(path.read_text(encoding="utf-8"))
    except Exception:
        return default


@dataclass
class RefreshRecord:
    status: str = "Idle"
    last_success_ts: str | None = None
    last_start_ts: str | None = None
    last_end_ts: str | None = None
    duration_seconds: float | None = None
    records_updated: int = 0
    records_skipped: int = 0
    errors: list[str] | None = None
    next_run_ts: str | None = None
    running: bool = False
    refresh_count: int = 0
    last_snapshot_path: str | None = None
    last_refresh_email: str | None = None


def load_state() -> dict[str, Any]:
    state = _safe_json_read(STATE_FILE, asdict(RefreshRecord()))
    state.setdefault("status", "Idle")
    state.setdefault("errors", [])
    state.setdefault("refresh_count", 0)
    state.setdefault("running", False)
    return state


def save_state(state: dict[str, Any]) -> dict[str, Any]:
    state = dict(state)
    state["next_run_ts"] = next_scheduled_run(state.get("last_success_ts"))
    _safe_json_write(STATE_FILE, state)
    return state


def next_scheduled_run(last_success_ts: str | None) -> str:
    try:
        dt = datetime.fromisoformat(last_success_ts) if last_success_ts else _now()
    except Exception:
        dt = _now()
    return _ts(dt + timedelta(hours=24))


def append_log(entry: dict[str, Any]) -> None:
    LOG_FILE.parent.mkdir(parents=True, exist_ok=True)
    with LOG_FILE.open("a", encoding="utf-8") as fh:
        fh.write(json.dumps(entry, default=str) + "\n")


def get_logs(limit: int = 50) -> list[dict[str, Any]]:
    if not LOG_FILE.exists():
        return []
    lines = LOG_FILE.read_text(encoding="utf-8").splitlines()
    out = []
    for line in lines[-limit:]:
        try:
            out.append(json.loads(line))
        except Exception:
            continue
    return out[::-1]


def build_knowledge_base_from_payload(email: str, payload: dict[str, Any]) -> dict[str, Any]:
    KB_DIR.mkdir(parents=True, exist_ok=True)
    SNAPSHOT_DIR.mkdir(parents=True, exist_ok=True)
    snapshot_name = f"snapshot_{int(time.time())}.json"
    snapshot_path = SNAPSHOT_DIR / snapshot_name
    _safe_json_write(snapshot_path, payload)

    rows = payload.get("trainer_operations_df") or []
    engine_rows = payload.get("trainer_availability_engine_df") or []
    actions = payload.get("manager_action_df") or []
    allocation = payload.get("allocation_decision_objects") or []
    certs = payload.get("certification_intelligence_df") or []
    g_rows = payload.get("growth_intelligence_df") or []
    snapshots = payload.get("summary") or {}

    entities = []
    trainer_profiles = []
    risk_signals = []
    recommendations = []
    course_matching = []
    manager_decisions = []
    refresh_snapshots = []

    for row in rows:
        email_addr = row.get("official_email") or row.get("trainer_email") or ""
        entity = {
            "entity_type": "trainer_profile",
            "source": "trainer_operations_df",
            "timestamp": _ts(),
            "confidence": row.get("confidence"),
            "page_module": "trainer-intelligence",
            "business_purpose": "trainer intelligence and matching",
            "id": email_addr or row.get("trainer_key"),
            "data": row,
        }
        entities.append(entity)
        trainer_profiles.append({
            **entity,
            "input": f"Trainer {row.get('trainer_name')} profile",
            "expected_output": row.get("recommended_action"),
            "reasoning_summary": row.get("action_reason"),
            "recommended_action": row.get("recommended_action"),
        })

    for row in engine_rows:
        risk_signals.append({
            "entity_type": "risk_signal",
            "source": "trainer_availability_engine_df",
            "timestamp": _ts(),
            "confidence": row.get("availability_confidence"),
            "page_module": "risk-takers",
            "business_purpose": "availability and risk detection",
            "id": row.get("email") or row.get("trainer_key"),
            "data": row,
        })

    for row in actions:
        recommendations.append({
            "entity_type": "action_recommendation",
            "source": "manager_action_df",
            "timestamp": _ts(),
            "confidence": row.get("confidence"),
            "page_module": "actions",
            "business_purpose": "manager action prioritization",
            "id": row.get("trainer_key") or row.get("trainer_name"),
            "input": row.get("action_reason") or row.get("reason"),
            "expected_output": row.get("recommended_action"),
            "reasoning_summary": row.get("reason"),
            "recommended_action": row.get("recommended_action"),
        })
        manager_decisions.append({
            "entity_type": "manager_decision",
            "source": "manager_action_df",
            "timestamp": _ts(),
            "confidence": row.get("confidence"),
            "page_module": "actions",
            "business_purpose": "decision history",
            "id": row.get("trainer_key") or row.get("trainer_name"),
            "data": row,
        })

    for row in allocation:
        course_matching.append({
            "entity_type": "course_matching",
            "source": "allocation_decision_objects",
            "timestamp": _ts(),
            "confidence": row.get("confidence"),
            "page_module": "allocation-desk",
            "business_purpose": "trainer to course mapping",
            "id": row.get("course_name") or row.get("course_id"),
            "data": row,
        })

    for row in certs:
        entities.append({
            "entity_type": "certification_profile",
            "source": "certification_intelligence_df",
            "timestamp": _ts(),
            "confidence": row.get("confidence"),
            "page_module": "certifications",
            "business_purpose": "certification coverage",
            "id": row.get("trainer_email") or row.get("trainer_key"),
            "data": row,
        })

    refresh_snapshots.append({
        "entity_type": "refresh_snapshot",
        "source": "summary",
        "timestamp": _ts(),
        "confidence": 100,
        "page_module": "dashboard",
        "business_purpose": "24-hour refresh auditing",
        "id": snapshot_name,
        "data": {
            "manager_email": email,
            "summary": snapshots,
        },
    })

    outputs = {
        "entities": entities,
        "trainer_profiles": trainer_profiles,
        "course_matching": course_matching,
        "risk_signals": risk_signals,
        "action_recommendations": recommendations,
        "manager_decisions": manager_decisions,
        "refresh_snapshots": refresh_snapshots,
    }
    for name, rows_out in outputs.items():
        _safe_json_write(KB_DIR / f"{name}.jsonl", rows_out)

    index = {
        "updated_at": _ts(),
        "snapshot_path": str(snapshot_path),
        "counts": {name: len(rows_out) for name, rows_out in outputs.items()},
        "source_email": email,
    }
    _safe_json_write(INDEX_FILE, index)
    return {"snapshot_path": str(snapshot_path), "counts": index["counts"]}


def refresh_once(email: str, build_fn, force: bool = True) -> dict[str, Any]:
    global RUNNING
    with LOCK:
        if RUNNING:
            return {"status": "Duplicate", "message": "Refresh already running"}
        RUNNING = True
    state = load_state()
    state.update({
        "status": "In Progress",
        "running": True,
        "last_start_ts": _ts(),
        "errors": [],
    })
    save_state(state)
    start = time.perf_counter()
    log_entry = {"event": "refresh_start", "email": email, "ts": _ts()}
    append_log(log_entry)
    try:
        payload = build_fn(email, force=force)
        kb = build_knowledge_base_from_payload(email, payload)
        duration = time.perf_counter() - start
        state.update({
            "status": "Success",
            "running": False,
            "last_success_ts": _ts(),
            "last_end_ts": _ts(),
            "duration_seconds": round(duration, 3),
            "records_updated": len(payload.get("trainer_operations_df") or []),
            "records_skipped": 0,
            "refresh_count": int(state.get("refresh_count", 0)) + 1,
            "last_snapshot_path": kb["snapshot_path"],
            "last_refresh_email": email,
        })
        append_log({"event": "refresh_success", "email": email, "ts": _ts(), "duration_seconds": state["duration_seconds"], "records_updated": state["records_updated"]})
        save_state(state)
        return {"status": "Success", "state": state, "knowledge": kb}
    except Exception as exc:  # noqa: BLE001
        duration = time.perf_counter() - start
        state.update({
            "status": "Failed",
            "running": False,
            "last_end_ts": _ts(),
            "duration_seconds": round(duration, 3),
            "errors": [str(exc)],
        })
        append_log({"event": "refresh_failed", "email": email, "ts": _ts(), "duration_seconds": state["duration_seconds"], "error": str(exc)})
        save_state(state)
        return {"status": "Failed", "state": state, "error": str(exc)}
    finally:
        with LOCK:
            RUNNING = False


def status_payload() -> dict[str, Any]:
    state = load_state()
    last_success = state.get("last_success_ts")
    return {
        **state,
        "last_success_ts": last_success,
        "next_run_ts": next_scheduled_run(last_success),
        "knowledge_base_path": str(KB_DIR),
        "knowledge_base_index": _safe_json_read(INDEX_FILE, {"counts": {}}),
    }


def poll_due(email: str, build_fn) -> None:
    state = load_state()
    if state.get("running"):
        return
    last_success = state.get("last_success_ts")
    if not last_success:
        refresh_once(email, build_fn, force=True)
        return
    try:
        due = datetime.fromisoformat(last_success) + timedelta(hours=24)
    except Exception:
        due = _now()
    if _now() >= due:
        refresh_once(email, build_fn, force=True)
