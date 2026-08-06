"""Manager action decision object compatibility layer."""

from __future__ import annotations

from typing import Any, Dict, List, Set, Tuple

from services.decision_objects import (
    as_list,
    clean_email,
    clean_text,
    make_decision_object,
    normalize_priority,
    percent,
    stable_id,
)


PRIORITY_SCORE = {
    "High": 90,
    "Medium": 60,
    "Low": 35,
}


def _category(action_type: str, reason: str, recommended_action: str) -> str:
    hay = f"{action_type} {reason} {recommended_action}".lower()
    if any(token in hay for token in ("backup", "replacement", "succession")):
        return "Backup"
    if "cert" in hay:
        return "Certification"
    if any(token in hay for token in ("book", "coach", "feedback")):
        return "Coaching"
    if any(token in hay for token in ("assign", "alloc")):
        return "Allocation"
    if any(token in hay for token in ("risk", "block", "escalat")):
        return "Risk"
    return "General"


def _dedupe_key(action_type: str, trainer_email: str, trainer_name: str, course_ref: str, reason: str) -> Tuple[str, str, str, str]:
    trainer_key = trainer_email or trainer_name.lower()
    return (action_type.lower(), trainer_key, course_ref.lower(), reason.lower()[:120])


def _blockers(action: Dict[str, Any], priority: str) -> List[str]:
    blockers = [clean_text(x) for x in as_list(action.get("blockers")) if clean_text(x)]
    reason = clean_text(action.get("reason") or action.get("action_reason"))
    if priority == "High" and reason:
        blockers.append(reason)
    return sorted(set(blockers))


def build_manager_action_objects(actions: List[Dict[str, Any]] | None = None, source_page: str = "manager_os") -> List[Dict[str, Any]]:
    objects: List[Dict[str, Any]] = []
    seen: Set[Tuple[str, str, str, str]] = set()
    for action in actions or []:
        if not isinstance(action, dict):
            continue
        action_type = clean_text(action.get("action_type") or action.get("type"), "review")
        priority = normalize_priority(action.get("priority"))
        trainer_email = clean_email(action.get("trainer_email") or action.get("official_email") or action.get("email") or action.get("trainer_key"))
        trainer_name = clean_text(action.get("trainer_name") or action.get("trainer"), "Unknown")
        course_ref = clean_text(action.get("course_name") or action.get("course_id") or action.get("allocation_id"), "General")
        reason = clean_text(action.get("action_reason") or action.get("reason"), "Manager review required")
        dedupe_key = _dedupe_key(action_type, trainer_email, trainer_name, course_ref, reason)
        if dedupe_key in seen:
            continue
        seen.add(dedupe_key)

        blockers = _blockers(action, priority)
        score = percent(action.get("score"), PRIORITY_SCORE[priority])
        confidence = percent(action.get("confidence"), 70)
        if trainer_name == "Unknown" and not trainer_email:
            confidence = min(confidence, 65)
        sources = ["manager_action_df"]
        evidence = {
            "reason": reason,
            "priority": priority,
            "due_urgency": action.get("due_urgency") or action.get("urgency"),
            "raw_evidence": action.get("evidence") or {},
            "source_row_ids": {
                "manager_action_df": action.get("id") or action.get("row_id") or action.get("trainer_key") or trainer_email or trainer_name,
            },
            "trace": {
                "source_page": action.get("source_page") or source_page,
                "course_reference": course_ref,
                "action_type": action_type,
            },
        }
        recommended_action = clean_text(action.get("recommended_action") or action.get("manager_action"), reason)
        category = _category(action_type, reason, recommended_action)
        objects.append(
            make_decision_object(
                entity_type="manager_action",
                trainer_email=trainer_email,
                trainer_name=trainer_name,
                score=score,
                confidence=confidence,
                status=priority.lower(),
                blockers=blockers,
                evidence=evidence,
                recommended_action=recommended_action,
                source_datasets=sources,
                object_id=stable_id("manager_action", priority, action_type, trainer_email or trainer_name, course_ref, reason),
                extra={
                    "action_type": action_type,
                    "priority": priority,
                    "category": category,
                    "action_status": "recommended",
                    "status": priority.lower(),
                    "owner_manager": action.get("manager_email") or action.get("manager"),
                    "trainer": trainer_name,
                    "linked_course": course_ref,
                    "linked_allocation": action.get("allocation_id") or action.get("allocation_reference"),
                    "course_allocation_reference": course_ref,
                    "reason": reason,
                    "due_urgency": action.get("due_urgency") or action.get("urgency"),
                    "source_page": action.get("source_page") or source_page,
                    "source": "backend_decision_object",
                    "source_row_ids": evidence["source_row_ids"],
                },
            )
        )
    return sorted(objects, key=lambda row: (-row.get("score", 0), row.get("trainer_name") or ""))
