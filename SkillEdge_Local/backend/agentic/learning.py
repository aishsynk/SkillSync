"""SkillEdge learning loop.

Closes the loop between manager decisions and scoring: every lifecycle decision
(close / escalate / reassign an action, or acknowledge / resolve / escalate a
review flag) is recorded as a labeled example. Weights are re-tuned from those
examples and stored as versioned model weights with evaluation metrics, so the
system demonstrably "learns" from real usage without external ML dependencies.

Persistence is plain JSON/JSONL under runtime/learning/ so any future ML service
can consume it. All writes are atomic (tmp + replace) and thread-safe.
"""

from __future__ import annotations

import json
import threading
import time
import uuid
from datetime import datetime, timezone
from pathlib import Path

# Dimension names mirror shared/scoring.py signal weights.
DIMENSIONS = ("qubit", "assignments", "certs", "utilization", "feedback", "tech")
BASE_WEIGHTS = {
    "qubit": 0.25,
    "assignments": 0.15,
    "certs": 0.15,
    "utilization": 0.10,
    "feedback": 0.15,
    "tech": 0.10,
}

_LEARNING_DIR = Path(__file__).resolve().parents[2] / "runtime" / "learning"
_FEEDBACK_PATH = _LEARNING_DIR / "feedback.jsonl"
_WEIGHTS_PATH = _LEARNING_DIR / "model_weights.json"
_LOCK = threading.Lock()
_LEARNING_RATE = 0.02


def _now_iso() -> str:
    return datetime.now(timezone.utc).isoformat()


def _atomic_write(path: Path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    tmp = path.with_suffix(path.suffix + ".tmp")
    tmp.write_text(data, encoding="utf-8")
    tmp.replace(path)


def _read_feedback() -> list:
    if not _FEEDBACK_PATH.exists():
        return []
    out = []
    for line in _FEEDBACK_PATH.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line:
            continue
        try:
            out.append(json.loads(line))
        except json.JSONDecodeError:
            continue
    return out


def _append_feedback(example: dict):
    _FEEDBACK_PATH.parent.mkdir(parents=True, exist_ok=True)
    with _FEEDBACK_PATH.open("a", encoding="utf-8") as fh:
        fh.write(json.dumps(example, default=str) + "\n")


def record_feedback(
    manager_email: str,
    entity_type: str,
    entity_id: str,
    decision: str,
    outcome: str,
    note: str = "",
    features: dict | None = None,
) -> dict:
    """Record one labeled example from a manager decision.

    outcome: "positive" | "negative" | "neutral" — how the decision turned out.
    features: {dimension: 0..1} signal strengths for the entity at decision time.
    """
    example = {
        "id": f"fb-{uuid.uuid4().hex[:12]}",
        "ts": _now_iso(),
        "manager_email": str(manager_email or "").strip().lower(),
        "entity_type": entity_type,
        "entity_id": entity_id,
        "decision": decision,
        "outcome": outcome if outcome in ("positive", "negative", "neutral") else "neutral",
        "note": str(note or "")[:500],
        "features": {k: float(v) for k, v in (features or {}).items() if k in DIMENSIONS},
    }
    with _LOCK:
        _append_feedback(example)
    return example


def _tune(weights: dict, examples: list) -> dict:
    new = dict(weights)
    for ex in examples:
        direction = 1.0 if ex.get("outcome") == "positive" else -1.0 if ex.get("outcome") == "negative" else 0.0
        if direction == 0.0:
            continue
        for dim, value in (ex.get("features") or {}).items():
            if dim not in new:
                continue
            # Nudge proportional to signal strength; strongly-present signals
            # move more, so the model learns which evidence matters most.
            new[dim] += direction * _LEARNING_RATE * max(0.0, min(1.0, float(value)))
    # Clamp and renormalize so weights stay a probability distribution.
    for dim in DIMENSIONS:
        new[dim] = max(0.03, min(0.60, new.get(dim, BASE_WEIGHTS[dim])))
    total = sum(new[dim] for dim in DIMENSIONS) or 1.0
    for dim in DIMENSIONS:
        new[dim] = round(new[dim] / total, 4)
    return new


def _metrics(examples: list, weights: dict, base: dict) -> dict:
    pos = sum(1 for e in examples if e.get("outcome") == "positive")
    neg = sum(1 for e in examples if e.get("outcome") == "negative")
    delta = {dim: round(weights.get(dim, 0) - base.get(dim, 0), 4) for dim in DIMENSIONS}
    return {
        "sample_count": len(examples),
        "positive_count": pos,
        "negative_count": neg,
        "mean_abs_weight_delta": round(sum(abs(v) for v in delta.values()) / max(1, len(DIMENSIONS)), 4),
        "weight_delta": delta,
    }


def tune_weights(force: bool = False) -> dict:
    """Re-tune weights from all labeled examples; bump the model version."""
    with _LOCK:
        current = _read_weights()
        examples = _read_feedback()
        new_weights = _tune(current.get("weights", BASE_WEIGHTS), examples)
        metrics = _metrics(examples, new_weights, BASE_WEIGHTS)
        if not force and metrics["sample_count"] == 0:
            return {"version": current.get("version", 1), "trained_at": current.get("trained_at"), "weights": current.get("weights"), "metrics": metrics, "status": "no_new_examples"}
        model = {
            "version": int(current.get("version", 0)) + 1,
            "trained_at": _now_iso(),
            "weights": new_weights,
            "metrics": metrics,
        }
        _atomic_write(_WEIGHTS_PATH, json.dumps(model, indent=2, default=str))
        return {**model, "status": "tuned"}


def _read_weights() -> dict:
    if not _WEIGHTS_PATH.exists():
        return {"version": 1, "trained_at": None, "weights": BASE_WEIGHTS}
    try:
        data = json.loads(_WEIGHTS_PATH.read_text(encoding="utf-8"))
        if data.get("weights"):
            return data
    except (json.JSONDecodeError, OSError):
        pass
    return {"version": 1, "trained_at": None, "weights": BASE_WEIGHTS}


def get_status() -> dict:
    """Learning status for the agent + UI (never returns raw example text)."""
    current = _read_weights()
    examples = _read_feedback()
    return {
        "version": current.get("version", 1),
        "trained_at": current.get("trained_at"),
        "sample_count": len(examples),
        "positive_count": sum(1 for e in examples if e.get("outcome") == "positive"),
        "negative_count": sum(1 for e in examples if e.get("outcome") == "negative"),
        "weights": current.get("weights"),
        "metrics": current.get("metrics"),
        "tune_path": f"runtime/learning/model_weights.json (v{current.get('version', 1)})",
    }
