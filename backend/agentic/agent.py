"""SkillEdge agent: intent classification, tool dispatch, answering and briefing.

The agent is deterministic and evidence-backed: every answer cites the tool and
the payload rows it used. It never invents data. The `answer` function is the
single seam where a model provider (LLM) could be plugged in later — it would
receive the tool results below and still return the same response shape.
"""

from __future__ import annotations

import re
from typing import Any, Dict, List, Optional

from agentic import tools


def _text(value: Any) -> str:
    return str(value or "").strip()


def _lower(value: Any) -> str:
    return _text(value).lower()


def _num(value: Any, default: Optional[float] = None) -> Optional[float]:
    try:
        return float(value)
    except (TypeError, ValueError):
        return default


def _match(question: str, keywords) -> bool:
    q = _lower(question)
    for kw in keywords:
        if kw in q:
            return True
    return False


def _extract_trainer(question: str, ctx: Dict[str, Any]):
    q = _lower(question)
    q_words = {w for w in re.split(r"[^a-z]+", q) if len(w) > 3}
    for t in tools.list_trainers(ctx).get("trainers", []):
        name = _lower(t.get("trainer_name") or "")
        email = _lower(t.get("trainer_email") or "")
        name_tokens = {w for w in re.split(r"[^a-z]+", name) if len(w) > 3}
        if email and email in q:
            return t.get("trainer_email") or "", t.get("trainer_name") or ""
        if name and (name in q or q in name or (name_tokens and name_tokens & q_words)):
            return t.get("trainer_email") or "", t.get("trainer_name") or ""
    return "", ""


def _extract_course(question: str, ctx: Dict[str, Any]):
    q = _lower(question)
    for row in (ctx.get("course_best_trainer_df") or []):
        name = _lower(row.get("course_name") or "")
        if name and len(name) > 3 and name in q:
            return row.get("course_name")
    for row in (ctx.get("course_master_df") or [])[:2000]:
        name = _lower(row.get("course_name") or "")
        if name and len(name) > 3 and name in q:
            return row.get("course_name")
    return ""


def classify_intent(question: str) -> str:
    q = _lower(question)
    if _match(q, ["compare", " vs ", "versus", "who is better", "which trainer"]):
        return "compare"
    if _match(q, ["backup", "cover for", "replacement"]):
        return "backup"
    if _match(q, ["free", "available", "capacity", "underutilized", "not busy"]):
        return "free"
    if _match(q, ["blocker", "blocked", "hold", "can't assign", "why not", "restricted"]):
        return "blockers"
    if _match(q, ["cert", "accredit", "certification gap", "exam"]):
        return "certification"
    if _match(q, ["data health", "data quality", "data gap", "data issue", "data issues", "trust", "missing data", "source issue"]):
        return "health"
    if _match(q, ["demand", "unallocated", "sales request", "incoming", "lead"]):
        return "demand"
    if _match(q, ["oem", "bench", "vendor"]):
        return "oem"
    if _match(q, ["action", "should i do", "next step", "what to do", "attention", "priority"]):
        return "actions"
    if _match(q, ["learn", "model", "weights", "feedback", "improve", "ml"]):
        return "learning"
    if _match(q, ["best", "teach", "who can", "trainer for", "allocate", "deliver"]):
        return "best"
    if _match(q, ["team", "overview", "status", "how is", "summary", "working"]):
        return "team"
    return "team"


# ── Answer builders ─────────────────────────────────────────────────────────
def _fmt_pct(value):
    return f"{round(value)}%" if _num(value) is not None else "—"


def _team_answer(ctx, result) -> str:
    s = result.get("summary", {})
    parts = [
        f"Your team has **{s.get('count', 0)} trainers**: {s.get('ready_now', 0)} ready now, "
        f"{s.get('can_deliver_with_prep', 0)} can deliver with prep, {s.get('teaching_now', 0)} teaching now, "
        f"{s.get('preparing', 0)} preparing for upcoming batches, and {s.get('unknown_status', 0)} with an unknown live status."
    ]
    return " ".join(parts)


def _free_answer(ctx, result) -> str:
    free = [
        t for t in result.get("trainers", [])
        if (t.get("utilization") is not None and _num(t.get("utilization")) < 40)
        or t.get("current_status") in ("free", "unknown")
    ]
    if not free:
        return "No trainer currently reads as clearly underutilized or free."
    names = ", ".join(f"{t.get('trainer_name')} ({_fmt_pct(t.get('utilization'))})" for t in free[:5])
    return f"Trainers with spare capacity right now: {names}."


def _best_answer(ctx, result, course) -> str:
    if not result.get("found"):
        return f"I couldn't find course data matching '{course or 'your request'}'. Try a course name from your catalogue."
    alt = result.get("alternatives") or []
    alt_text = "; ".join(f"{a.get('trainer_name')} ({_fmt_pct(a.get('allocation_score'))})" for a in alt[:3]) if alt else "no strong alternatives listed"
    return (
        f"For **{result.get('course')}**, the best fit is **{result.get('best_trainer')}** "
        f"with an allocation score of {_fmt_pct(result.get('allocation_score'))}. "
        f"Alternatives: {alt_text}. Reason: {result.get('reason') or 'based on readiness, availability and evidence.'}"
    )


def _backup_answer(ctx, result) -> str:
    if not result.get("found"):
        return "No backup pairings are available for that course yet (trainer_backup_df is empty)."
    lines = [f"{b.get('course_name')}: primary **{b.get('primary')}**, backup **{b.get('backup')}** ({_fmt_pct(b.get('backup_score'))})" for b in result.get("backups", [])]
    return "Backup coverage: " + " | ".join(lines)


def _blockers_answer(ctx, result) -> str:
    if not result.get("count"):
        return "No trainers are currently hard-blocked from assignment."
    lines = [f"**{b.get('trainer_name')}** — {b.get('blocker') or 'hard blocker'}" for b in result.get("blockers", [])[:6]]
    return "Blocked / needs review: " + "; ".join(lines)


def _cert_answer(ctx, result) -> str:
    if not result.get("count"):
        return "No certification gaps are currently flagged."
    lines = [f"**{g.get('trainer_name')}** — {g.get('course')} ({g.get('vendor')})" for g in result.get("gaps", [])[:6]]
    return f"{result.get('count')} certification gap(s): " + "; ".join(lines)


def _health_answer(ctx, result) -> str:
    if not result.get("count"):
        return "No outstanding data-health issues."
    top_api = ", ".join(f"{k} ({v})" for k, v in list(result.get("by_api", {}).items())[:4])
    return f"{result.get('count')} source issue(s) — most affected: {top_api}. This lowers confidence on affected trainer verdicts."


def _actions_answer(ctx, result) -> str:
    if not result.get("count"):
        return "No open manager actions right now."
    lines = [f"{a.get('priority', 'Review').upper()} · {a.get('action_type')} · {a.get('trainer_name')}" for a in result.get("actions", [])[:6]]
    return f"{result.get('count')} open action(s): " + " | ".join(lines)


def _demand_answer(ctx, result) -> str:
    if not result.get("count"):
        return "No unallocated sales demand right now."
    lines = [f"**{d.get('course_name') or d.get('demand_id')}** ({d.get('start_date') or 'no date'})" for d in result.get("demand", [])[:6]]
    return f"{result.get('count')} unallocated demand item(s): " + "; ".join(lines)


def _oem_answer(ctx, result) -> str:
    if not result.get("count"):
        return "No OEM bench data."
    lines = [f"{b.get('vendor')}: {b.get('bench_risk')} ({b.get('trainer_count')} trainer(s))" for b in result.get("bench", [])[:6]]
    return "OEM bench risk: " + "; ".join(lines)


def _trainer_answer(ctx, result) -> str:
    if not result.get("found"):
        return "I couldn't find that trainer in your scope."
    t = result.get("trainer", {})
    d = result.get("decision") or {}
    return (
        f"**{t.get('trainer_name')}** ({t.get('designation') or 'Trainer'}) — readiness {_fmt_pct(t.get('readiness_score'))} "
        f"({t.get('readiness_bucket')}), utilization {_fmt_pct(t.get('utilization'))}, "
        f"status: {t.get('current_status') or 'unknown'}, next batch: {t.get('next_batch') or '—'}, "
        f"certifications {t.get('certification_count') or 0}. "
        f"Recommended action: {t.get('recommended_action') or 'review evidence'}. "
        f"Primary blocker: {d.get('primary_blocker') or 'none'}."
    )


def _compare_answer(ctx, result) -> str:
    trainers = result.get("trainers", [])
    if len(trainers) < 2:
        return "Provide two trainer names to compare (e.g. 'compare Abhinav and Niharika')."
    parts = []
    for t in trainers:
        parts.append(f"{t.get('trainer_name')} (readiness {_fmt_pct(t.get('readiness_score'))}, util {_fmt_pct(t.get('utilization'))}, {t.get('readiness_bucket')})")
    return " vs ".join(parts)


def _learning_answer(ctx, result) -> str:
    status = result.get("status") or {}
    return (
        f"Learning model v{status.get('version', '?')} · {status.get('sample_count', 0)} labeled example(s) "
        f"tuned at {status.get('trained_at') or 'never'}. "
        f"Feedback channels: close/escalate/reassign actions and review-flag decisions."
    )


# ── Public API ──────────────────────────────────────────────────────────────
def answer(question: str, ctx: Dict[str, Any]) -> Dict[str, Any]:
    """Answer a manager question against the unified payload.

    Returns a response dict with the same shape whether answered by the
    deterministic engine or (later) a plugged-in LLM:
      {question, intent, answer, confidence, tool_used, sources}
    """
    intent = classify_intent(question)
    trainer_email, trainer_name = _extract_trainer(question, ctx)
    course = _extract_course(question, ctx)
    result = None
    tool_used = intent
    sources: List[str] = []

    try:
        if trainer_email or (intent in ("compare",) and _match(question, ["compare"])):
            # Trainer-specific or comparison question dominates when a trainer
            # name is present in the question.
            if _match(question, ["compare", " vs ", "versus"]):
                names = [n for n in re.split(r"compare|vs|versus|and|,", question, flags=re.I) if n.strip()]
                results = []
                for n in names:
                    r = tools.get_trainer(ctx, name=n)
                    if r.get("found"):
                        results.append(r.get("trainer"))
                result = {"trainers": results}
                tool_used = "get_trainer"
                answer_text = _compare_answer(ctx, result)
            else:
                result = tools.get_trainer(ctx, email=trainer_email, name=trainer_name)
                tool_used = "get_trainer"
                answer_text = _trainer_answer(ctx, result)
                if result.get("found"):
                    sources.append(f"trainer_operations_df + trainer_decision_objects")
        elif intent == "free":
            result = tools.list_trainers(ctx)
            tool_used = "list_trainers"
            answer_text = _free_answer(ctx, result)
        elif intent == "backup":
            result = tools.backups_for_course(ctx, course=course)
            tool_used = "backups_for_course"
            answer_text = _backup_answer(ctx, result)
            sources.append("trainer_backup_df")
        elif intent == "best" or (course and not trainer_email):
            result = tools.best_for_course(ctx, course=course)
            tool_used = "best_for_course"
            answer_text = _best_answer(ctx, result, course)
            sources.append("course_best_trainer_df / allocation_ranked_trainer_df")
        elif intent == "blockers":
            result = tools.list_blockers(ctx)
            tool_used = "list_blockers"
            answer_text = _blockers_answer(ctx, result)
            sources.append("trainer_decision_objects")
        elif intent == "certification":
            result = tools.certification_gaps(ctx)
            tool_used = "certification_gaps"
            answer_text = _cert_answer(ctx, result)
            sources.append("certification_gap_df")
        elif intent == "health":
            result = tools.data_health(ctx)
            tool_used = "data_health"
            answer_text = _health_answer(ctx, result)
            sources.append("data_health_df")
        elif intent == "actions":
            result = tools.list_manager_actions(ctx)
            tool_used = "list_manager_actions"
            answer_text = _actions_answer(ctx, result)
            sources.append("manager_action_objects")
        elif intent == "demand":
            result = tools.unallocated_demand(ctx)
            tool_used = "unallocated_demand"
            answer_text = _demand_answer(ctx, result)
            sources.append("unallocated_demand_df")
        elif intent == "oem":
            result = tools.oem_bench(ctx)
            tool_used = "oem_bench"
            answer_text = _oem_answer(ctx, result)
            sources.append("oem_bench_risk_df")
        elif intent == "learning":
            result = {"status": ctx.get("learning_status")}
            tool_used = "learning"
            answer_text = _learning_answer(ctx, result)
        else:  # team
            result = tools.list_trainers(ctx)
            tool_used = "list_trainers"
            answer_text = _team_answer(ctx, result)
            sources.append("trainer_operations_df + trainer_current_state_df")
    except Exception:  # noqa: BLE001
        result = {}
        tool_used = "fallback"
        answer_text = "I hit an error while answering. Please try a different question."

    confidence = 90 if result else 50
    return {
        "question": question,
        "intent": intent,
        "answer": answer_text,
        "confidence": confidence,
        "tool_used": tool_used,
        "sources": sources,
        "result": result,
    }


# ── Briefing ────────────────────────────────────────────────────────────────
def build_briefing(ctx: Dict[str, Any]) -> Dict[str, Any]:
    """Produce a manager-facing daily briefing from the unified payload."""
    team = tools.list_trainers(ctx)
    ts = team.get("summary", {})
    actions = tools.list_manager_actions(ctx)
    blockers = tools.list_blockers(ctx)
    health = tools.data_health(ctx)
    demand = tools.unallocated_demand(ctx)
    oem = tools.oem_bench(ctx)
    certs = tools.certification_gaps(ctx)

    issues = []
    if blockers.get("count"):
        issues.append({"priority": "High", "text": f"{blockers['count']} trainer(s) blocked from assignment"})
    for a in (actions.get("actions") or [])[:2]:
        issues.append({"priority": a.get("priority"), "text": f"{a.get('action_type')} for {a.get('trainer_name')}"})
    if health.get("count"):
        issues.append({"priority": "Medium", "text": f"{health['count']} data-health issue(s) lower signal confidence"})
    if certs.get("count"):
        issues.append({"priority": "Medium", "text": f"{certs['count']} certification gap(s) to plan"})

    opportunities = []
    if demand.get("count"):
        opportunities.append({"text": f"{demand['count']} unallocated demand item(s) ready for matching"})
    for o in (oem.get("bench") or []):
        if o.get("bench_risk") in ("High", "Medium"):
            opportunities.append({"text": f"Grow {o.get('vendor')} OEM bench ({o.get('trainer_count')} trainer(s))"})
        if len(opportunities) >= 3:
            break
    underused = [t for t in team.get("trainers", []) if _num(t.get("utilization")) is not None and _num(t.get("utilization")) < 40]
    if underused:
        opportunities.append({"text": f"Deploy underutilized trainers: {', '.join(t.get('trainer_name') for t in underused[:3])}"})

    return {
        "generated_at": ctx.get("generated_at"),
        "freshness": {
            "from_cache": ctx.get("from_cache"),
            "cache_status": ctx.get("cache_status"),
            "cache_age_minutes": ctx.get("cache_age_minutes"),
            "refresh_pending": ctx.get("refresh_pending"),
            "last_refresh_success_at": ctx.get("last_refresh_success_at"),
        },
        "team": ts,
        "issues": issues[:5],
        "opportunities": opportunities[:5],
        "next_actions": (actions.get("actions") or [])[:3],
    }
