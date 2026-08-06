"""Local persistence for manager action lifecycle (close / escalate / reassign).

Actions themselves are recomputed fresh on every intelligence build — they are
derived from live RMS signals via `manager_action_objects`. This service does
not touch that derivation. It stores only the manager's lifecycle decision
("I closed this", "I escalated this", "I reassigned this to X") as a small
portable JSON overlay, keyed by the action's own stable id, and merges that
overlay onto the served payload on every request (see `apply_overlay`). The
original action record is never mutated or deleted.
"""

import json
import threading
import time
from pathlib import Path

_LOCK = threading.Lock()
_STORE_PATH = Path(__file__).resolve().parents[1] / "data" / "action_state.json"

VALID_STATES = ("closed", "escalated", "reassigned")


def _now_iso():
    return time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime())


def _load():
    if not _STORE_PATH.exists():
        return {"actions": {}}
    try:
        data = json.loads(_STORE_PATH.read_text(encoding="utf-8"))
        if not isinstance(data, dict) or not isinstance(data.get("actions"), dict):
            return {"actions": {}}
        return data
    except Exception:
        return {"actions": {}}


def _save(data):
    _STORE_PATH.parent.mkdir(parents=True, exist_ok=True)
    tmp = _STORE_PATH.with_suffix(".tmp")
    tmp.write_text(json.dumps(data, indent=2, default=str), encoding="utf-8")
    tmp.replace(_STORE_PATH)


def set_action_state(action_id, state, note=None, assignee=None, reason=None, manager_email=None):
    """Record a close/escalate/reassign decision for one action id.

    Returns the stored record (state/note/assignee/reason/updated_at/history).
    """
    action_id = str(action_id or "").strip()
    if not action_id:
        raise ValueError("action_id is required")
    if state not in VALID_STATES:
        raise ValueError(f"Unsupported action state: {state}")
    with _LOCK:
        data = _load()
        record = data["actions"].get(action_id) or {"history": []}
        record["state"] = state
        record["note"] = note
        record["assignee"] = assignee
        record["reason"] = reason
        record["updated_at"] = _now_iso()
        record["updated_by"] = manager_email
        record.setdefault("history", []).append({
            "action": state,
            "note": note,
            "assignee": assignee,
            "reason": reason,
            "updated_by": manager_email,
            "updated_at": record["updated_at"],
        })
        data["actions"][action_id] = record
        _save(data)
        return dict(record)


def get_action_state(action_id):
    with _LOCK:
        return _load().get("actions", {}).get(str(action_id or "").strip())


def get_all_states():
    with _LOCK:
        return dict(_load().get("actions") or {})


def apply_overlay(manager_action_objects):
    """Annotate manager_action_objects rows with their stored lifecycle state.

    Additive only: every original field is preserved. Adds `lifecycle_state`
    (open/closed/escalated/reassigned), `lifecycle_note`, `lifecycle_assignee`,
    `lifecycle_updated_at`, and `lifecycle_history` to each row that has an
    `id`. Rows with no stored state get `lifecycle_state: "open"`.
    """
    if not isinstance(manager_action_objects, list):
        return manager_action_objects
    states = get_all_states()
    for row in manager_action_objects:
        if not isinstance(row, dict):
            continue
        action_id = str(row.get("id") or "")
        record = states.get(action_id)
        if record:
            row["lifecycle_state"] = record.get("state")
            row["lifecycle_note"] = record.get("note")
            row["lifecycle_assignee"] = record.get("assignee")
            row["lifecycle_reason"] = record.get("reason")
            row["lifecycle_updated_at"] = record.get("updated_at")
            row["lifecycle_history"] = record.get("history")
        else:
            row["lifecycle_state"] = "open"
    return manager_action_objects
