"""Retrieval tools for the SkillEdge agent.

Tools are pure functions over the unified payload `ctx` (a dict keyed by the
same names the unified endpoint serves) plus optional manager lifecycle state.
Each tool returns a compact, JSON-serializable result plus provenance so the
answer layer can cite its sources.
"""

from __future__ import annotations

from typing import Any, Dict, Iterable, List, Optional


def _text(value: Any) -> str:
    return str(value or "").strip()


def _lower(value: Any) -> str:
    return _text(value).lower()


def _rows(ctx: Dict[str, Any], key: str) -> List[Dict[str, Any]]:
    rows = ctx.get(key) or []
    return [r for r in rows if isinstance(r, dict)]


def _email(value: Any) -> str:
    return _lower(value)


def _by_email(ctx: Dict[str, Any], dataset: str) -> Dict[str, Dict[str, Any]]:
    return {
        _email(r.get("trainer_email") or r.get("official_email") or r.get("email")): r
        for r in _rows(ctx, dataset)
        if r.get("trainer_email") or r.get("official_email") or r.get("email")
    }


def _num(value: Any, default: Optional[float] = None) -> Optional[float]:
    try:
        return float(value)
    except (TypeError, ValueError):
        return default


# ── Trainer tools ───────────────────────────────────────────────────────────
def list_trainers(ctx: Dict[str, Any]) -> Dict[str, Any]:
    trainers = _rows(ctx, "trainer_operations_df")
    summary = {
        "count": len(trainers),
        "ready_now": sum(1 for t in trainers if t.get("readiness_bucket") == "Ready Now"),
        "can_deliver_with_prep": sum(1 for t in trainers if t.get("readiness_bucket") == "Can Deliver with Prep"),
        "teaching_now": sum(1 for t in trainers if (t.get("current_engagement") or {}).get("current_status") == "teaching_now"),
        "preparing": sum(1 for t in trainers if (t.get("current_engagement") or {}).get("current_status") == "preparing"),
        "unknown_status": sum(1 for t in trainers if (t.get("current_engagement") or {}).get("current_status") == "unknown"),
    }
    return {
        "tool": "list_trainers",
        "summary": summary,
        "trainers": [
            {
                "trainer_key": t.get("trainer_key"),
                "trainer_name": t.get("trainer_name"),
                "trainer_email": t.get("official_email"),
                "readiness_bucket": t.get("readiness_bucket"),
                "readiness_score": t.get("overall_readiness_score"),
                "utilization": t.get("current_utilization"),
                "current_status": (t.get("current_engagement") or {}).get("current_status"),
                "classification": (t.get("classification") or {}).get("badge_label"),
            }
            for t in trainers
        ],
    }


def get_trainer(ctx: Dict[str, Any], email: str = "", name: str = "") -> Dict[str, Any]:
    target = _email(email)
    name_target = _lower(name)
    trainer = None
    for t in _rows(ctx, "trainer_operations_df"):
        if target and _email(t.get("official_email") or t.get("trainer_email")) == target:
            trainer = t
            break
        if name_target and name_target in _lower(t.get("trainer_name") or ""):
            trainer = t
            break
    if not trainer:
        return {"tool": "get_trainer", "found": False, "error": "Trainer not found"}
    state = trainer.get("current_engagement") or {}
    decision = None
    for d in _rows(ctx, "trainer_decision_objects"):
        if _email(d.get("trainer_email")) in (_email(trainer.get("official_email")), target) or _lower(d.get("trainer_name")) == name_target:
            decision = d
            break
    return {
        "tool": "get_trainer",
        "found": True,
        "trainer": {
            "trainer_name": trainer.get("trainer_name"),
            "trainer_email": trainer.get("official_email"),
            "designation": trainer.get("designation"),
            "readiness_score": trainer.get("overall_readiness_score"),
            "readiness_bucket": trainer.get("readiness_bucket"),
            "growth_bucket": trainer.get("growth_bucket"),
            "utilization": trainer.get("current_utilization"),
            "availability_status": trainer.get("availability_status"),
            "current_status": state.get("current_status"),
            "next_batch": (state.get("next_batch") or {}).get("course_name"),
            "current_assignment": trainer.get("current_assignment"),
            "classification": trainer.get("classification"),
            "recommended_action": trainer.get("recommended_action"),
            "certification_count": trainer.get("certificate_count"),
            "negative_feedback_count": trainer.get("negative_feedback_count"),
        },
        "decision": decision,
    }


# ── Course / allocation tools ───────────────────────────────────────────────
def best_for_course(ctx: Dict[str, Any], course: str = "") -> Dict[str, Any]:
    rows = _rows(ctx, "course_best_trainer_df")
    target = _lower(course)
    match = None
    for row in rows:
        if target and target in _lower(row.get("course_name") or ""):
            match = row
            break
    if not match:
        # fall back to the top-ranked allocation row for the course
        for row in _rows(ctx, "allocation_ranked_trainer_df"):
            if target and target in _lower(row.get("course_name") or ""):
                match = {"course_name": row.get("course_name"), "best_trainer": row.get("trainer_name"),
                         "trainer_email": row.get("trainer_email"), "allocation_score": row.get("allocation_score")}
                break
    if not match:
        return {"tool": "best_for_course", "found": False, "error": "Course not found"}
    return {
        "tool": "best_for_course",
        "found": True,
        "course": match.get("course_name"),
        "best_trainer": match.get("best_trainer"),
        "trainer_email": match.get("trainer_email"),
        "allocation_score": match.get("allocation_score"),
        "alternatives": match.get("alternative_trainers") or [],
        "reason": match.get("reason"),
        "recommended_action": match.get("recommended_manager_action"),
    }


def backups_for_course(ctx: Dict[str, Any], course: str = "") -> Dict[str, Any]:
    rows = _rows(ctx, "trainer_backup_df")
    target = _lower(course)
    matches = [r for r in rows if target in _lower(r.get("course_name") or "")] if target else rows[:5]
    if not matches:
        return {"tool": "backups_for_course", "found": False, "error": "No backup pairings for course"}
    return {
        "tool": "backups_for_course",
        "found": True,
        "count": len(matches),
        "backups": [
            {
                "course_name": r.get("course_name"),
                "primary": r.get("primary_trainer_name"),
                "backup": r.get("backup_trainer_name"),
                "backup_score": r.get("backup_score"),
                "reason": r.get("backup_reason"),
            }
            for r in matches[:5]
        ],
    }


def unallocated_demand(ctx: Dict[str, Any]) -> Dict[str, Any]:
    rows = _rows(ctx, "unallocated_demand_df")
    return {
        "tool": "unallocated_demand",
        "count": len(rows),
        "demand": [
            {
                "demand_id": r.get("demand_id"),
                "course_name": r.get("course_name"),
                "start_date": r.get("start_date"),
                "delivery_mode": r.get("delivery_mode"),
                "location": r.get("location"),
                "customer": r.get("customer"),
            }
            for r in rows[:10]
        ],
    }


# ── Risk / actions tools ────────────────────────────────────────────────────
def list_blockers(ctx: Dict[str, Any]) -> Dict[str, Any]:
    decisions = _rows(ctx, "trainer_decision_objects")
    blocked = [
        d for d in decisions
        if (d.get("status") in ("blocked", "review") or d.get("assignment_status") in ("blocked", "review"))
    ]
    return {
        "tool": "list_blockers",
        "count": len(blocked),
        "blockers": [
            {
                "trainer_name": d.get("trainer_name"),
                "trainer_email": d.get("trainer_email"),
                "blocker": d.get("primary_blocker") or (d.get("blockers") or [None])[0],
            }
            for d in blocked[:10]
        ],
    }


def list_manager_actions(ctx: Dict[str, Any]) -> Dict[str, Any]:
    actions = _rows(ctx, "manager_action_objects")
    open_actions = [a for a in actions if a.get("lifecycle_state", "open") != "closed"]
    return {
        "tool": "list_manager_actions",
        "count": len(open_actions),
        "actions": [
            {
                "id": a.get("id"),
                "action_type": a.get("action_type") or a.get("recommended_action"),
                "trainer_name": a.get("trainer_name"),
                "priority": a.get("priority"),
                "reason": a.get("reason"),
                "category": a.get("category"),
                "lifecycle_state": a.get("lifecycle_state", "open"),
            }
            for a in open_actions[:10]
        ],
    }


def data_health(ctx: Dict[str, Any]) -> Dict[str, Any]:
    rows = _rows(ctx, "data_health_df")
    by_api: Dict[str, int] = {}
    by_severity: Dict[str, int] = {}
    for r in rows:
        by_api[r.get("api_name") or "Unknown"] = by_api.get(r.get("api_name") or "Unknown", 0) + 1
        sev = r.get("severity") or "Medium"
        by_severity[sev] = by_severity.get(sev, 0) + 1
    return {
        "tool": "data_health",
        "count": len(rows),
        "by_api": by_api,
        "by_severity": by_severity,
        "examples": [
            {
                "trainer_name": r.get("trainer_name"),
                "api_name": r.get("api_name"),
                "issue_type": r.get("issue_type"),
                "detail": r.get("issue_detail"),
            }
            for r in rows[:5]
        ],
    }


def certification_gaps(ctx: Dict[str, Any]) -> Dict[str, Any]:
    rows = _rows(ctx, "certification_gap_df")
    return {
        "tool": "certification_gaps",
        "count": len(rows),
        "gaps": [
            {
                "trainer_name": r.get("trainer_name"),
                "course": r.get("course"),
                "vendor": r.get("vendor"),
                "reason": r.get("reason"),
            }
            for r in rows[:10]
        ],
    }


def oem_bench(ctx: Dict[str, Any]) -> Dict[str, Any]:
    rows = _rows(ctx, "oem_bench_risk_df")
    return {
        "tool": "oem_bench",
        "count": len(rows),
        "bench": [
            {
                "vendor": r.get("vendor"),
                "bench_risk": r.get("bench_risk"),
                "trainer_count": r.get("trainer_count"),
                "strong_trainer_count": r.get("strong_trainer_count"),
                "single_point_course_count": r.get("single_point_course_count"),
            }
            for r in rows[:10]
        ],
    }


def future_roadmap(ctx: Dict[str, Any]) -> Dict[str, Any]:
    rows = _rows(ctx, "future_skill_roadmap_df")
    return {
        "tool": "future_roadmap",
        "count": len(rows),
        "rows": [
            {
                "trainer_name": r.get("trainer_name"),
                "related_future_skill": r.get("related_future_skill"),
                "future_oem": r.get("future_oem"),
                "manager_action": r.get("manager_action"),
            }
            for r in rows[:10]
        ],
    }


def search_kb(ctx: Dict[str, Any], query: str = "") -> Dict[str, Any]:
    kb = ctx.get("knowledge_base") or {}
    query_l = _lower(query)
    hits = []
    for dataset, rows in kb.items():
        for row in (rows or [])[:200]:
            if query_l and query_l in _lower(str(row)):
                hits.append({"dataset": dataset, "row": row})
    return {"tool": "search_kb", "count": len(hits), "hits": hits[:10]}
