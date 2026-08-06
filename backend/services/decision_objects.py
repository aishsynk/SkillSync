"""Shared decision object builders for manager intelligence payloads.

The dataframe keys remain the compatibility contract. These helpers add a
stable decision-object spine so manager pages can render consistent readiness,
risk, action, and evidence without duplicating scoring heuristics.
"""

from __future__ import annotations

import hashlib
import re
from typing import Any, Dict, Iterable, List


REQUIRED_DECISION_FIELDS = [
    "decision_contract_version",
    "id",
    "entity_type",
    "trainer_email",
    "trainer_name",
    "score",
    "confidence",
    "blockers",
    "evidence",
    "recommended_action",
    "source_datasets",
]

SOURCE_FIELD_HINTS = {
    "trainer_operations_df": ["trainer_name", "official_email", "readiness_bucket", "overall_readiness_score", "hr_risk", "negative_feedback_count"],
    "trainer_availability_engine_df": ["final_availability_status", "availability_confidence", "calendar_status", "capacity_status", "missing_signals"],
    "delivery_intelligence_df": ["delivery_readiness_score", "delivery_risk_level", "delivery_confidence"],
    "certification_intelligence_df": ["resume_certifications", "certification_gaps", "certification_recommendations", "vendor_certifications"],
    "allocation_decision_objects": ["course_name", "allocation_score", "course_readiness", "allocation_risk", "source_row_ids"],
    "allocation_ranked_trainer_df": ["course_name", "trainer_email", "allocation_score", "rank", "recommendation_role"],
    "allocation_intelligence_df": ["course_name", "trainer_email", "reason", "trade_offs", "recommended_manager_action"],
    "allocation_risk_df": ["course_name", "allocation_risk", "risk_reason", "recommended_manager_action"],
    "course_best_trainer_df": ["course_name", "best_trainer", "alternative_trainers", "allocation_score"],
    "course_allocation_df": ["course_name", "trainer_email", "overall_allocation_score", "allocation_status"],
    "manager_action_objects": ["priority", "category", "recommended_action", "reason", "source_row_ids"],
    "manager_action_df": ["trainer_name", "action_type", "priority", "reason", "recommended_action", "confidence"],
    "custom_course_match_objects": ["parsed_course_outline", "bucket", "score", "confidence", "evidence"],
    "feedback_coaching_df": ["trainer_email", "feedback_theme", "sentiment", "recommended_action"],
    "future_skill_roadmap_df": ["trainer_email", "related_future_skill", "future_oem", "reason"],
    "future_certification_roadmap_df": ["trainer_email", "vendor", "certification", "recommended_action"],
    "vendor_strength_df": ["trainer_email", "vendor", "strength_score", "course_count"],
    "trainer_backup_df": ["course_name", "primary_trainer_key", "backup_trainer_name", "backup_trainer_email"],
    "replacement_recommendation_df": ["course_name", "impacted_trainer_email", "replacement_trainer", "replacement_status"],
    "single_point_failure_df": ["course_name", "primary_trainer_email", "risk_level", "recommendation"],
}


def clean_text(value: Any, default: str = "") -> str:
    if value is None:
        return default
    text = re.sub(r"\s+", " ", str(value)).strip()
    return text if text else default


def clean_email(value: Any, default: str = "") -> str:
    email = clean_text(value, default).lower()
    return email if "@" in email or email == default else email


def number(value: Any, default: float = 0.0) -> float:
    try:
        if value is None or value == "":
            return default
        if isinstance(value, str):
            value = value.replace("%", "").replace(",", "").strip()
        return float(value)
    except (TypeError, ValueError):
        return default


def percent(value: Any, default: float = 0.0) -> float:
    return round(max(0.0, min(100.0, number(value, default))), 2)


def as_list(value: Any) -> List[Any]:
    if value is None or value == "":
        return []
    if isinstance(value, list):
        out: List[Any] = []
        for item in value:
            out.extend(as_list(item))
        return [item for item in out if item not in (None, "")]
    if isinstance(value, tuple) or isinstance(value, set):
        return as_list(list(value))
    if isinstance(value, dict):
        return [value]
    return [clean_text(x) for x in re.split(r"[,;\n|]+", str(value)) if clean_text(x)]


def source_list(value: Any) -> List[str]:
    return sorted({clean_text(x) for x in as_list(value) if clean_text(x)})


def normalize_priority(value: Any) -> str:
    text = clean_text(value, "Medium").lower()
    if text in {"critical", "urgent", "p0", "p1", "high"}:
        return "High"
    if text in {"low", "p3", "p4", "nice to have"}:
        return "Low"
    return "Medium"


def normalize_status(value: Any, score: Any = None, blockers: Iterable[Any] | None = None) -> str:
    text = clean_text(value).lower()
    if blockers and list(blockers):
        return "blocked"
    if any(token in text for token in ("block", "hold", "risk", "not recommended")):
        return "blocked"
    if any(token in text for token in ("ready", "best", "available")):
        return "ready"
    if any(token in text for token in ("prep", "partial", "review")):
        return "prep_required"
    score_value = percent(score)
    if score_value >= 80:
        return "ready"
    if score_value >= 60:
        return "review"
    if score_value > 0:
        return "weak"
    return "unknown"


def normalize_bucket(value: Any, score: Any = None, blockers: Iterable[Any] | None = None) -> str:
    text = clean_text(value).lower().replace(" ", "_").replace("-", "_")
    text = re.sub(r"_+", "_", text).strip("_")
    allowed = {
        "best_now",
        "strong_alternative",
        "backup",
        "prep_required",
        "future_candidate",
        "not_recommended",
    }
    if blockers and list(blockers):
        return "not_recommended"
    if text in allowed:
        return text
    score_value = percent(score)
    if score_value >= 82:
        return "best_now"
    if score_value >= 72:
        return "strong_alternative"
    if score_value >= 62:
        return "backup"
    if score_value >= 50:
        return "prep_required"
    if score_value > 0:
        return "future_candidate"
    return "not_recommended"


def stable_id(entity_type: str, *parts: Any) -> str:
    raw = "|".join(clean_text(part) for part in parts if clean_text(part)) or entity_type
    digest = hashlib.sha1(raw.lower().encode("utf-8")).hexdigest()[:12]
    return f"{entity_type}:{digest}"


def evidence_object(
    evidence: Any,
    *,
    source_datasets: Iterable[str] | None = None,
    trace: Dict[str, Any] | None = None,
) -> Dict[str, Any]:
    if isinstance(evidence, dict):
        out = dict(evidence)
    elif evidence:
        out = {"summary": evidence}
    else:
        out = {}
    if source_datasets:
        sources = source_list(source_datasets)
        out.setdefault("source_datasets", sources)
        out.setdefault("source_field_names", {source: SOURCE_FIELD_HINTS.get(source, []) for source in sources})
        out.setdefault("source_labels", {source: source.replace("_df", "").replace("_", " ").title() for source in sources})
    if trace:
        out.setdefault("trace", trace)
    return out


def make_decision_object(
    *,
    entity_type: str,
    trainer_email: Any = "",
    trainer_name: Any = "",
    score: Any = 0,
    confidence: Any = 0,
    bucket: Any = None,
    status: Any = None,
    blockers: Any = None,
    evidence: Any = None,
    recommended_action: Any = "",
    source_datasets: Any = None,
    object_id: Any = "",
    extra: Dict[str, Any] | None = None,
) -> Dict[str, Any]:
    blocker_list = [clean_text(x) for x in as_list(blockers) if clean_text(x)]
    sources = source_list(source_datasets)
    score_value = percent(score)
    confidence_value = percent(confidence)
    if blocker_list and confidence_value > 85:
        confidence_value = 85
    if not clean_text(trainer_email) or clean_text(trainer_name).lower() in {"", "unknown"}:
        confidence_value = min(confidence_value, 75)

    obj: Dict[str, Any] = {
        "decision_contract_version": "2026-07-03.v3",
        "id": clean_text(object_id) or stable_id(entity_type, trainer_email, trainer_name, score_value, recommended_action),
        "entity_type": clean_text(entity_type, "decision"),
        "trainer_email": clean_email(trainer_email),
        "trainer_name": clean_text(trainer_name, "Unknown"),
        "score": score_value,
        "confidence": confidence_value,
        "blockers": blocker_list,
        "evidence": evidence_object(evidence, source_datasets=sources),
        "recommended_action": clean_text(recommended_action, "Review before manager action"),
        "source_datasets": sources,
        "source_field_names": {source: SOURCE_FIELD_HINTS.get(source, []) for source in sources},
        "source_labels": {source: source.replace("_df", "").replace("_", " ").title() for source in sources},
    }
    if bucket is not None:
        obj["bucket"] = normalize_bucket(bucket, score_value, blocker_list)
    if status is not None or bucket is None:
        obj["status"] = normalize_status(status, score_value, blocker_list)
    if extra:
        obj.update(extra)
    return obj


def _row_email(row: Dict[str, Any]) -> str:
    return clean_email(row.get("official_email") or row.get("trainer_email") or row.get("email") or row.get("trainer_key"))


def _row_name(row: Dict[str, Any]) -> str:
    return clean_text(row.get("trainer_name") or row.get("trainer") or row.get("name"), "Unknown")


def _indexed(rows: Iterable[Dict[str, Any]] | None) -> Dict[str, Dict[str, Any]]:
    indexed: Dict[str, Dict[str, Any]] = {}
    for row in rows or []:
        if not isinstance(row, dict):
            continue
        email = _row_email(row)
        if email:
            indexed[email] = row
    return indexed


def _rows_for_email(rows: Iterable[Dict[str, Any]] | None, email: str) -> List[Dict[str, Any]]:
    key = clean_email(email)
    out: List[Dict[str, Any]] = []
    for row in rows or []:
        if not isinstance(row, dict):
            continue
        if _row_email(row) == key:
            out.append(row)
    return out


def _source_row_ids(*sources: tuple[str, Iterable[Dict[str, Any]] | Dict[str, Any] | None]) -> Dict[str, List[str]]:
    out: Dict[str, List[str]] = {}
    for source, rows in sources:
        if not rows:
            continue
        row_list = list(rows.values()) if isinstance(rows, dict) else list(rows or [])
        ids: List[str] = []
        for idx, row in enumerate(row_list):
            if not isinstance(row, dict):
                continue
            row_id = clean_text(row.get("id") or row.get("row_id") or row.get("trainer_key") or row.get("course_id") or row.get("course_name") or idx)
            if row_id:
                ids.append(row_id)
        if ids:
            out[source] = ids[:20]
    return out


def _risk_item(title: str, severity: str, category: str, why: str, evidence: str, impact: str, mitigation: str) -> Dict[str, Any]:
    return {
        "title": title,
        "severity": severity,
        "category": category,
        "why": why,
        "evidence": evidence,
        "impact": impact,
        "mitigation": mitigation,
    }


def _build_trainer_risk_register(
    *,
    trainer: Dict[str, Any],
    readiness: float,
    readiness_bucket: str,
    allocation_rows: List[Dict[str, Any]],
    action_rows: List[Dict[str, Any]],
    certification_row: Dict[str, Any],
    feedback_rows: List[Dict[str, Any]],
    backup_rows: List[Dict[str, Any]],
    backup_for_rows: List[Dict[str, Any]],
    replacement_rows: List[Dict[str, Any]],
    spof_rows: List[Dict[str, Any]],
) -> Dict[str, Any]:
    hard_blockers: List[Dict[str, Any]] = []
    review_risks: List[Dict[str, Any]] = []
    weak_signals: List[Dict[str, Any]] = []
    missing_data: List[Dict[str, Any]] = []

    negative_feedback = number(trainer.get("negative_feedback_count"))
    hr_negative = number(trainer.get("hr_negative_count"))
    if hr_negative > 0:
        hard_blockers.append(_risk_item(
            "Compliance flag is active",
            "High",
            "hard_blocker",
            "A negative compliance signal requires clearance before allocation.",
            f"Negative compliance count {hr_negative:g}",
            "Live allocation is unsafe until an authorized reviewer clears the signal.",
            "Obtain authorized compliance clearance before assigning live delivery.",
        ))
    if "hold" in readiness_bucket.lower() or "block" in clean_text(trainer.get("final_status") or trainer.get("allocation_status")).lower():
        hard_blockers.append(_risk_item(
            "Trainer status blocker",
            "High",
            "hard_blocker",
            "Trainer readiness or allocation status indicates hold/block.",
            readiness_bucket or clean_text(trainer.get("final_status") or trainer.get("allocation_status")),
            "Allocation is unsafe until the status is cleared.",
            "Clear trainer status before assigning live delivery.",
        ))
    if negative_feedback >= 3:
        review_risks.append(_risk_item(
            "Feedback quality risk",
            "High",
            "review_risk",
            "Repeated negative feedback can compound into delivery risk.",
            f"Negative feedback count {negative_feedback:g}",
            "Manager review or coaching is needed before stretch allocation.",
            "Review feedback and confirm coaching plan before allocation.",
        ))
    if readiness < 45:
        review_risks.append(_risk_item(
            "Low readiness score",
            "High",
            "review_risk",
            "Readiness below 45 means the trainer is not yet safe as primary.",
            f"Readiness {readiness:g}; bucket {readiness_bucket or 'Not scored'}",
            "Delivery quality risk if assigned as primary.",
            "Prioritize skill-building and mock delivery before live assignment.",
        ))
    elif readiness < 70:
        weak_signals.append(_risk_item(
            "Near-ready trainer",
            "Medium",
            "weak_signal",
            "Readiness is below the safest immediate-allocation threshold.",
            f"Readiness {readiness:g}; bucket {readiness_bucket or 'Not scored'}",
            "Trainer may need prep or supervision before allocation.",
            "Use mock validation or mentor review before assignment.",
        ))

    allocation_blocked = [row for row in allocation_rows if row.get("blocker") or row.get("blockers")]
    if allocation_blocked:
        review_risks.append(_risk_item(
            "Course allocation blocker",
            "Medium",
            "review_risk",
            "One or more course matches are gated by allocation evidence.",
            "; ".join(clean_text(row.get("blocker") or row.get("blockers")) for row in allocation_blocked[:2]),
            "The affected course should not be assigned until resolved.",
            "Resolve the underlying allocation blocker before assigning the trainer.",
        ))
    high_allocation_risk = [
        row for row in allocation_rows
        if clean_text(row.get("allocation_risk")).lower() == "high" and not (row.get("blocker") or row.get("blockers"))
    ]
    if high_allocation_risk:
        review_risks.append(_risk_item(
            "High allocation risk",
            "High",
            "review_risk",
            "Allocation risk is high, but no hard blocker was detected.",
            f"{len(high_allocation_risk)} high-risk course match(es)",
            "Manager should monitor or build backup coverage rather than treating this as a hard block.",
            "Review backup coverage and delivery constraints before confirming.",
        ))
    if spof_rows:
        review_risks.append(_risk_item(
            "Single-point dependency",
            clean_text(spof_rows[0].get("risk_level"), "High"),
            "review_risk",
            "If this trainer is unavailable, one or more courses may lack capable backup coverage.",
            f"{len(spof_rows)} course dependency row(s)",
            "Delivery halt risk if trainer becomes unavailable.",
            spof_rows[0].get("recommendation", {}).get("manager_action") if isinstance(spof_rows[0].get("recommendation"), dict) else "Develop a backup trainer before scaling this course.",
        ))

    cert_gaps = as_list(certification_row.get("certification_gaps"))
    if cert_gaps:
        review_risks.append(_risk_item(
            f"Certification gap ({len(cert_gaps)})",
            "High" if len(cert_gaps) > 2 else "Medium",
            "review_risk",
            "Exam-required delivery without accreditation increases compliance and quality risk.",
            ", ".join(clean_text(g.get("vendor") if isinstance(g, dict) else g) for g in cert_gaps[:3]) or "Certification gaps present",
            "May block exam-required course delivery.",
            "Nominate for accreditation before the next assignment on the affected vendor.",
        ))
    for row in feedback_rows[:1]:
        priority = clean_text(row.get("priority"), "Low")
        if priority.lower() != "low":
            review_risks.append(_risk_item(
                "Feedback coaching signal",
                priority,
                "review_risk",
                "Unresolved feedback can compound into delivery or reputation risk.",
                clean_text(row.get("feedback_signal"), "Feedback signal present"),
                "May affect future allocation confidence.",
                clean_text(row.get("coaching_action"), "Review feedback and plan coaching."),
            ))

    if not backup_rows and not backup_for_rows and not replacement_rows:
        weak_signals.append(_risk_item(
            "No backup coverage identified",
            "Medium",
            "weak_signal",
            "No backup or replacement trainer is visible for this trainer's courses.",
            "trainer_backup_df / replacement_recommendation_df has no matching rows",
            "Higher bench risk if this trainer becomes unavailable.",
            "Identify and develop a backup trainer for at least one core course.",
        ))
    if not allocation_rows:
        missing_data.append(_risk_item(
            "Allocation data missing",
            "Low",
            "missing_data",
            "No trainer-scoped allocation decision rows are available.",
            "allocation_decision_objects/allocation_intelligence_df has no matching rows",
            "Assignment summary may be incomplete.",
            "Refresh allocation data or review course allocation rows.",
        ))

    ordered = hard_blockers + review_risks + weak_signals + missing_data
    return {
        "risk_register": ordered,
        "hard_blockers": hard_blockers,
        "review_risks": review_risks,
        "weak_signals": weak_signals,
        "missing_data": missing_data,
        "risk_flags": [row["title"] for row in ordered],
    }


def _action_category(action: Dict[str, Any]) -> str:
    text = " ".join([
        clean_text(action.get("action_type")),
        clean_text(action.get("recommended_action")),
        clean_text(action.get("reason") or action.get("action_reason")),
        clean_text(action.get("source_page")),
    ]).lower()
    if "backup" in text or "replacement" in text or "succession" in text:
        return "Backup"
    if "cert" in text:
        return "Certification"
    if "book" in text or "coach" in text or "feedback" in text:
        return "Coaching"
    if "assign" in text or "alloc" in text:
        return "Allocation"
    if "risk" in text or "block" in text or "escalat" in text:
        return "Risk"
    return "General"


def _action_summary(action_rows: List[Dict[str, Any]]) -> Dict[str, Any]:
    by_priority = {"High": 0, "Medium": 0, "Low": 0}
    by_category: Dict[str, int] = {}
    top_action = None
    for action in action_rows:
        priority = normalize_priority(action.get("priority") or action.get("status"))
        by_priority[priority] = by_priority.get(priority, 0) + 1
        category = clean_text(action.get("category")) or _action_category(action)
        by_category[category] = by_category.get(category, 0) + 1
        if top_action is None or percent(action.get("score"), {"High": 90, "Medium": 60, "Low": 35}.get(priority, 35)) > percent(top_action.get("score"), 0):
            top_action = action
    return {
        "total": len(action_rows),
        "total_actions": len(action_rows),
        "by_priority": by_priority,
        "by_category": by_category,
        "top_action": top_action,
    }


def build_trainer_decision_objects(
    trainers: Iterable[Dict[str, Any]],
    availability_rows: Iterable[Dict[str, Any]] | None = None,
    delivery_rows: Iterable[Dict[str, Any]] | None = None,
    certification_rows: Iterable[Dict[str, Any]] | None = None,
    allocation_rows: Iterable[Dict[str, Any]] | None = None,
    manager_action_rows: Iterable[Dict[str, Any]] | None = None,
    feedback_rows: Iterable[Dict[str, Any]] | None = None,
    backup_rows: Iterable[Dict[str, Any]] | None = None,
    replacement_rows: Iterable[Dict[str, Any]] | None = None,
    spof_rows: Iterable[Dict[str, Any]] | None = None,
) -> List[Dict[str, Any]]:
    availability_by_email = _indexed(availability_rows)
    delivery_by_email = _indexed(delivery_rows)
    cert_by_email = _indexed(certification_rows)

    objects: List[Dict[str, Any]] = []
    for trainer in trainers or []:
        if not isinstance(trainer, dict):
            continue
        email = _row_email(trainer)
        name = _row_name(trainer)
        availability = availability_by_email.get(email, {})
        delivery = delivery_by_email.get(email, {})
        cert = cert_by_email.get(email, {})
        trainer_allocations = _rows_for_email(allocation_rows, email)
        trainer_actions = _rows_for_email(manager_action_rows, email)
        trainer_feedback = _rows_for_email(feedback_rows, email)
        trainer_key = clean_text(trainer.get("trainer_key"))
        trainer_backups = [
            row for row in (backup_rows or [])
            if isinstance(row, dict) and clean_text(row.get("primary_trainer_key")).lower() == trainer_key.lower()
        ]
        trainer_backup_for = [
            row for row in (backup_rows or [])
            if isinstance(row, dict) and clean_email(row.get("backup_trainer_email")) == email
        ]
        trainer_replacements = [
            row for row in (replacement_rows or [])
            if isinstance(row, dict)
            and clean_email(row.get("impacted_trainer_email") or row.get("trainer_email") or row.get("official_email") or row.get("email")) == email
        ]
        trainer_spof = [
            row for row in (spof_rows or [])
            if isinstance(row, dict)
            and clean_email(row.get("primary_trainer_email") or row.get("trainer_email") or row.get("official_email") or row.get("email")) == email
        ]
        negative_feedback = number(trainer.get("negative_feedback_count"))
        hr_negative = number(trainer.get("hr_negative_count"))
        readiness = percent(
            delivery.get("delivery_readiness_score")
            or trainer.get("overall_readiness_score")
            or trainer.get("readiness_score")
            or trainer.get("qubits_score")
            or trainer.get("avg_qubits_score")
        )
        availability_status = clean_text(
            availability.get("final_availability_status")
            or availability.get("capacity_status")
            or delivery.get("delivery_capacity_status"),
            "Unknown",
        )
        readiness_bucket = clean_text(trainer.get("readiness_bucket") or trainer.get("readiness_status"), "Unknown")
        blocker_reasons: List[str] = []
        if hr_negative > 0:
            blocker_reasons.append("Compliance flag is active")
        if negative_feedback >= 3:
            blocker_reasons.append("Negative feedback count is high")
        if "hold" in readiness_bucket.lower():
            blocker_reasons.append("Readiness status is hold")
        if "block" in clean_text(trainer.get("final_status") or trainer.get("allocation_status")).lower():
            blocker_reasons.append("Trainer status is blocked")

        risk_result = _build_trainer_risk_register(
            trainer=trainer,
            readiness=readiness,
            readiness_bucket=readiness_bucket,
            allocation_rows=trainer_allocations,
            action_rows=trainer_actions,
            certification_row=cert,
            feedback_rows=trainer_feedback,
            backup_rows=trainer_backups,
            backup_for_rows=trainer_backup_for,
            replacement_rows=trainer_replacements,
            spof_rows=trainer_spof,
        )
        hard_blockers = risk_result["hard_blockers"]
        review_risks = risk_result["review_risks"]
        weak_signals = risk_result["weak_signals"]
        missing_data = risk_result["missing_data"]
        risk_register = risk_result["risk_register"]
        primary_blocker = clean_text(hard_blockers[0]["title"] if hard_blockers else review_risks[0]["title"] if review_risks else weak_signals[0]["title"] if weak_signals else "")
        ready_allocations = [
            row for row in trainer_allocations
            if clean_text(row.get("course_readiness")).lower() == "ready_now"
            or (not row.get("blocker") and percent(row.get("allocation_score") or row.get("overall_allocation_score")) >= 70)
        ]
        best_course = None
        if ready_allocations:
            best_course = sorted(ready_allocations, key=lambda row: percent(row.get("allocation_score") or row.get("overall_allocation_score")), reverse=True)[0]
        elif trainer_allocations:
            best_course = sorted(trainer_allocations, key=lambda row: percent(row.get("allocation_score") or row.get("overall_allocation_score")), reverse=True)[0]
        can_assign_now = bool(best_course and not hard_blockers and (clean_text(best_course.get("course_readiness")).lower() == "ready_now" or percent(best_course.get("allocation_score") or best_course.get("overall_allocation_score")) >= 70))
        assignment_status = (
            "blocked" if hard_blockers
            else "review_required" if review_risks
            else "ready_now" if can_assign_now
            else "prep_required" if weak_signals
            else "unknown"
        )
        readiness_status = (
            "blocked" if hard_blockers
            else "ready" if readiness >= 80
            else "near_ready" if readiness >= 50
            else "not_ready" if readiness > 0
            else "unknown"
        )
        action_summary = _action_summary(trainer_actions)
        top_action = action_summary.get("top_action") or {}
        next_manager_action = (
            clean_text(top_action.get("recommended_action") or top_action.get("action_type"))
            or ("Resolve blocker before allocation" if hard_blockers else "")
            or (best_course.get("recommended_manager_action") if isinstance(best_course, dict) else "")
            or (risk_register[0]["mitigation"] if risk_register else "")
            or "Continue normal observation"
        )

        confidence = 55
        if email:
            confidence += 10
        if name != "Unknown":
            confidence += 10
        if readiness:
            confidence += 10
        if availability_status != "Unknown":
            confidence += 8
        if cert or trainer.get("resume_certifications"):
            confidence += 5
        if blocker_reasons:
            confidence -= 8

        status = normalize_status(readiness_bucket or availability_status, readiness, blocker_reasons)
        action = next_manager_action
        sources = [
            "trainer_operations_df",
            "trainer_availability_engine_df",
            "delivery_intelligence_df",
            "certification_intelligence_df",
            "allocation_decision_objects",
            "manager_action_objects",
            "feedback_coaching_df",
            "trainer_backup_df",
            "replacement_recommendation_df",
            "single_point_failure_df",
        ]
        source_row_ids = _source_row_ids(
            ("trainer_operations_df", [trainer]),
            ("trainer_availability_engine_df", [availability] if availability else []),
            ("delivery_intelligence_df", [delivery] if delivery else []),
            ("certification_intelligence_df", [cert] if cert else []),
            ("allocation_decision_objects", trainer_allocations),
            ("manager_action_objects", trainer_actions),
            ("feedback_coaching_df", trainer_feedback),
            ("trainer_backup_df", trainer_backups + trainer_backup_for),
            ("replacement_recommendation_df", trainer_replacements),
            ("single_point_failure_df", trainer_spof),
        )
        allocation_summary = {
            "can_assign_now": can_assign_now,
            "assignment_status": assignment_status,
            "ready_count": len(ready_allocations),
            "total_matches": len(trainer_allocations),
            "best_course_match": {
                "course_name": best_course.get("course_name") if isinstance(best_course, dict) else None,
                "course_id": best_course.get("course_id") if isinstance(best_course, dict) else None,
                "allocation_score": (best_course.get("allocation_score") or best_course.get("overall_allocation_score")) if isinstance(best_course, dict) else None,
                "course_readiness": best_course.get("course_readiness") if isinstance(best_course, dict) else None,
                "allocation_risk": best_course.get("allocation_risk") if isinstance(best_course, dict) else None,
                "recommended_action": (best_course.get("recommended_manager_action") or best_course.get("recommended_action")) if isinstance(best_course, dict) else None,
            } if best_course else None,
            "backup_count": len(trainer_backups) + len(trainer_replacements),
        }
        nested = {
            "trainer_key": trainer.get("trainer_key") or email or name,
            "identity": {
                "designation": trainer.get("designation"),
                "direct_or_indirect": trainer.get("direct_or_indirect"),
            },
            "skills": {
                "resume_skills": trainer.get("resume_skills") or [],
                "current_courses": trainer.get("current_courses") or [],
                "mapped_courses": trainer.get("mapped_courses") or [],
            },
            "vendor_strength": trainer.get("vendor_strength") or trainer.get("vendor_strength_score"),
            "availability": {
                "status": availability_status,
                "confidence": percent(availability.get("availability_confidence") or delivery.get("availability_confidence")),
            },
            "delivery_readiness": {
                "score": readiness,
                "bucket": readiness_bucket,
            },
            "certification_status": {
                "certificate_count": number(cert.get("certificate_count") or trainer.get("certificate_count")),
                "resume_certifications": cert.get("resume_certifications") or trainer.get("resume_certifications") or [],
                "certification_gaps": cert.get("certification_gaps") or trainer.get("certification_gaps") or [],
            },
            "feedback_risk": {
                "negative_feedback_count": negative_feedback,
                "hr_negative_count": hr_negative,
            },
            "blocker_status": {
                "blocked": bool(hard_blockers or blocker_reasons),
                "blocker_reasons": blocker_reasons + [row["title"] for row in hard_blockers],
            },
            "can_assign_now": can_assign_now,
            "assignment_status": assignment_status,
            "readiness_status": readiness_status,
            "primary_blocker": primary_blocker,
            "best_course_match": allocation_summary["best_course_match"],
            "allocation_summary": allocation_summary,
            "risk_register": risk_register,
            "risk_flags": risk_result["risk_flags"],
            "hard_blockers": hard_blockers,
            "review_risks": review_risks,
            "weak_signals": weak_signals,
            "missing_data": missing_data,
            "next_manager_action": next_manager_action,
            "action_summary": action_summary,
            "source_row_ids": source_row_ids,
        }
        evidence = {
            "readiness_score": readiness,
            "availability_status": availability_status,
            "readiness_bucket": readiness_bucket,
            "negative_feedback_count": negative_feedback,
            "hr_negative_count": hr_negative,
            "trace": {
                "trainer_key": trainer.get("trainer_key"),
                "availability_row_found": bool(availability),
                "delivery_row_found": bool(delivery),
                "certification_row_found": bool(cert),
                "allocation_rows_found": len(trainer_allocations),
                "manager_action_rows_found": len(trainer_actions),
                "source_row_ids": source_row_ids,
            },
            "risk_register": risk_register,
            "allocation_summary": allocation_summary,
            "action_summary": action_summary,
        }
        objects.append(
            make_decision_object(
                entity_type="trainer",
                trainer_email=email,
                trainer_name=name,
                score=readiness,
                confidence=confidence,
                status=assignment_status,
                blockers=[row["title"] for row in hard_blockers] or blocker_reasons,
                evidence=evidence,
                recommended_action=action,
                source_datasets=sources,
                object_id=stable_id("trainer", email or name or trainer.get("trainer_key")),
                extra=nested,
            )
        )
    return objects


# Backward-compatible private aliases used by older services.
_clean = clean_text
_num = number
