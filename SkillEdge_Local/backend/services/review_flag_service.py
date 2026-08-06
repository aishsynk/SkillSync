"""Local persistence for review/compliance flag acknowledgement & resolution.

A "review flag" in SkillEdge is a trainer-level state (compliance flag,
delivery concern, or both — see `shared.classification`), identified by the
trainer's email. This service does not compute or alter the underlying
compliance/feedback signals that produce a flag; it stores only the manager's
acknowledge/resolve/escalate decision as a small portable JSON overlay, keyed
by trainer email, and merges that overlay onto each trainer's `classification`
block on every request (see `apply_overlay`). Original evidence is preserved.
"""

import json
import threading
import time
from pathlib import Path

_LOCK = threading.Lock()
_STORE_PATH = Path(__file__).resolve().parents[1] / "data" / "review_flag_state.json"

VALID_STATES = ("acknowledged", "resolved", "escalated")


def _now_iso():
    return time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime())


def _normalise_id(flag_id):
    return str(flag_id or "").strip().lower()


def _load():
    if not _STORE_PATH.exists():
        return {"flags": {}}
    try:
        data = json.loads(_STORE_PATH.read_text(encoding="utf-8"))
        if not isinstance(data, dict) or not isinstance(data.get("flags"), dict):
            return {"flags": {}}
        return data
    except Exception:
        return {"flags": {}}


def _save(data):
    _STORE_PATH.parent.mkdir(parents=True, exist_ok=True)
    tmp = _STORE_PATH.with_suffix(".tmp")
    tmp.write_text(json.dumps(data, indent=2, default=str), encoding="utf-8")
    tmp.replace(_STORE_PATH)


def set_flag_state(flag_id, state, note=None, manager_email=None):
    """Record an acknowledge/resolve/escalate decision for one trainer's flag."""
    flag_id = _normalise_id(flag_id)
    if not flag_id:
        raise ValueError("flag_id is required")
    if state not in VALID_STATES:
        raise ValueError(f"Unsupported flag state: {state}")
    with _LOCK:
        data = _load()
        record = data["flags"].get(flag_id) or {"history": []}
        record["state"] = state
        record["note"] = note
        record["updated_at"] = _now_iso()
        record["updated_by"] = manager_email
        record.setdefault("history", []).append({
            "action": state,
            "note": note,
            "updated_by": manager_email,
            "updated_at": record["updated_at"],
        })
        data["flags"][flag_id] = record
        _save(data)
        return dict(record)


def get_flag_state(flag_id):
    with _LOCK:
        return _load().get("flags", {}).get(_normalise_id(flag_id))


def get_all_states():
    with _LOCK:
        return dict(_load().get("flags") or {})


def apply_overlay(trainers):
    """Annotate each trainer's classification block with its stored flag state.

    Additive only. Adds to `classification`: `flag_state` (none/open/
    acknowledged/resolved/escalated), `flag_note`, `flag_updated_at`,
    `flag_history`, and `flag_is_open` (False once resolved — a resolved flag
    must not count as an open review flag, but the underlying
    has_compliance_flag / has_delivery_concern signals are left untouched).
    """
    if not isinstance(trainers, list):
        return trainers
    states = get_all_states()
    for t in trainers:
        if not isinstance(t, dict):
            continue
        classification = t.get("classification")
        if not isinstance(classification, dict):
            continue
        email = _normalise_id(t.get("official_email") or t.get("trainer_key"))
        record = states.get(email)
        has_flag = classification.get("review_flag_type") not in (None, "none")
        if record:
            classification["flag_state"] = record.get("state")
            classification["flag_note"] = record.get("note")
            classification["flag_updated_at"] = record.get("updated_at")
            classification["flag_history"] = record.get("history")
            classification["flag_is_open"] = has_flag and record.get("state") != "resolved"
        else:
            classification["flag_state"] = "open" if has_flag else "none"
            classification["flag_is_open"] = has_flag
    return trainers
