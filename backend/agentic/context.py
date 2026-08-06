"""Assemble the agent's context from the unified payload + knowledge base."""

from __future__ import annotations

import json
from pathlib import Path
from typing import Any, Dict, Optional

from agentic import learning

_KB_DIR = Path(__file__).resolve().parents[2] / "runtime" / "knowledge_base"
_KB_DATASETS = (
    "entities",
    "trainer_profiles",
    "course_matching",
    "risk_signals",
    "action_recommendations",
    "manager_decisions",
    "refresh_snapshots",
)


def _read_jsonl(path: Path) -> list:
    if not path.exists():
        return []
    out = []
    for line in path.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line:
            continue
        try:
            out.append(json.loads(line))
        except json.JSONDecodeError:
            continue
    return out


def load_knowledge_base() -> Dict[str, list]:
    kb: Dict[str, list] = {}
    for name in _KB_DATASETS:
        rows = _read_jsonl(_KB_DIR / f"{name}.jsonl")
        if rows:
            kb[name] = rows
    return kb


def build_agent_context(payload: Dict[str, Any]) -> Dict[str, Any]:
    """Build the context object consumed by the agent.

    The payload itself is the primary source; the knowledge base and the
    learning registry are additive context. Freshness metadata passes through so
    the briefing can be honest about data age.
    """
    ctx = dict(payload or {})
    ctx["knowledge_base"] = load_knowledge_base()
    ctx["learning_status"] = learning.get_status()
    return ctx
