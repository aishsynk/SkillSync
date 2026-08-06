"""Allocation decision objects for manager intelligence consumers."""

from __future__ import annotations

from typing import Any, Dict, Iterable, List, Tuple

from services.decision_objects import (
    as_list,
    clean_email,
    clean_text,
    make_decision_object,
    normalize_bucket,
    percent,
    stable_id,
)


SOURCE_DATASETS = [
    "allocation_ranked_trainer_df",
    "allocation_intelligence_df",
    "course_best_trainer_df",
    "allocation_risk_df",
    "course_allocation_df",
]


def _course_key(value: Any) -> str:
    return clean_text(value).lower()


def _email(row: Dict[str, Any]) -> str:
    return clean_email(row.get("trainer_email") or row.get("official_email") or row.get("email") or row.get("trainer_key"))


def _name(row: Dict[str, Any]) -> str:
    return clean_text(row.get("trainer_name") or row.get("trainer") or row.get("best_trainer"), "Unknown")


def _score(row: Dict[str, Any]) -> float:
    return percent(row.get("allocation_score") or row.get("overall_allocation_score") or row.get("score") or row.get("fit_score"))


def _row_id(row: Dict[str, Any], source: str, index: int) -> str:
    explicit = row.get("id") or row.get("row_id") or row.get("course_id") or row.get("trainer_key")
    return clean_text(explicit) or f"{source}:{index}"


def _index(rows: Iterable[Dict[str, Any]] | None, source: str) -> Dict[Tuple[str, str], Dict[str, Any]]:
    out: Dict[Tuple[str, str], Dict[str, Any]] = {}
    for idx, row in enumerate(rows or []):
        if not isinstance(row, dict):
            continue
        key = (_course_key(row.get("course_name") or row.get("course")), _email(row))
        if key[0] and key[1] and key not in out:
            enriched = dict(row)
            enriched.setdefault("__source", source)
            enriched.setdefault("__source_row_id", _row_id(row, source, idx))
            out[key] = enriched
    return out


def _risk_by_course(rows: Iterable[Dict[str, Any]] | None) -> Dict[str, Dict[str, Any]]:
    out: Dict[str, Dict[str, Any]] = {}
    for idx, row in enumerate(rows or []):
        if not isinstance(row, dict):
            continue
        key = _course_key(row.get("course_name") or row.get("course"))
        if key:
            enriched = dict(row)
            enriched.setdefault("__source", "allocation_risk_df")
            enriched.setdefault("__source_row_id", _row_id(row, "allocation_risk_df", idx))
            out[key] = enriched
    return out


def _best_by_course(rows: Iterable[Dict[str, Any]] | None) -> Dict[str, Dict[str, Any]]:
    out: Dict[str, Dict[str, Any]] = {}
    for idx, row in enumerate(rows or []):
        if not isinstance(row, dict):
            continue
        key = _course_key(row.get("course_name") or row.get("course"))
        if key:
            enriched = dict(row)
            enriched.setdefault("__source", "course_best_trainer_df")
            enriched.setdefault("__source_row_id", _row_id(row, "course_best_trainer_df", idx))
            out[key] = enriched
    return out


def _risk_flags(row: Dict[str, Any], risk_row: Dict[str, Any]) -> List[str]:
    flags = [clean_text(x) for x in as_list(row.get("risk_flags")) if clean_text(x)]
    for key in ("blocker", "blocker_reason", "delivery_risk", "trainer_status"):
        value = clean_text(row.get(key) or risk_row.get(key))
        if value and value.lower() not in {"low", "none", "not available", "unknown", "eligible"}:
            flags.append(value)
    for reason in as_list(row.get("eligibility_reasons")):
        if clean_text(reason):
            flags.append(clean_text(reason))
    return sorted(set(flags))


def _risk_tags(row: Dict[str, Any], risk_row: Dict[str, Any], blockers: List[str]) -> List[str]:
    tags = list(blockers)
    allocation_risk = clean_text(row.get("allocation_risk") or risk_row.get("allocation_risk"))
    if allocation_risk and allocation_risk.lower() not in {"low", "none", "not available", "unknown"}:
        tags.append(f"Allocation risk: {allocation_risk}")
    reason = clean_text(row.get("risk_reason") or risk_row.get("risk_reason"))
    if reason and allocation_risk.lower() in {"medium", "high"}:
        tags.append(reason)
    return sorted(set(tags))


def _readiness_bucket(row: Dict[str, Any], blockers: List[str]) -> str:
    if blockers:
        return "not_recommended"
    role = clean_text(row.get("recommendation_role")).lower()
    if "best" in role:
        return "best_now"
    if "strong" in role:
        return "strong_alternative"
    if "backup" in role:
        return "backup"
    if "development" in role or "prep" in role:
        return "prep_required"
    return normalize_bucket(row.get("bucket"), _score(row), blockers)


def _course_readiness(row: Dict[str, Any], blockers: List[str]) -> str:
    if blockers:
        return "blocked"
    score = _score(row)
    if score >= 70:
        return "ready_now"
    if score >= 45:
        return "near_ready"
    return "not_ready"


def _role(bucket: str, row: Dict[str, Any]) -> str:
    explicit = clean_text(row.get("recommendation_role"))
    if explicit:
        return explicit
    return {
        "best_now": "Best Trainer",
        "strong_alternative": "Strong Alternative",
        "backup": "Backup Option",
        "prep_required": "Development Candidate",
        "future_candidate": "Development Candidate",
        "not_recommended": "Not Recommended / Blocked",
    }.get(bucket, "Additional Candidate")


def _recommended_action(bucket: str, course_readiness: str, blockers: List[str], row: Dict[str, Any], risk_row: Dict[str, Any]) -> str:
    existing = clean_text(row.get("recommended_action") or row.get("recommended_manager_action") or row.get("manager_action"))
    if existing:
        return existing
    risk_action = clean_text(risk_row.get("recommended_manager_action"))
    if blockers:
        return "Do not use as primary until allocation blockers are resolved."
    if bucket == "best_now" and course_readiness == "ready_now":
        return "Confirm trainer as primary and keep backup coverage visible."
    if bucket in {"strong_alternative", "backup"}:
        return "Keep trainer warm as backup or alternative coverage."
    if bucket == "prep_required":
        return "Create prep plan, mock validation, and mentor review before allocation."
    return risk_action or "Review allocation evidence before assigning."


def _source_trace(row: Dict[str, Any], intel: Dict[str, Any], best: Dict[str, Any], risk: Dict[str, Any], raw: Dict[str, Any]) -> Dict[str, Any]:
    source_row_ids = {}
    for item in (row, intel, best, risk, raw):
        source = item.get("__source")
        row_id = item.get("__source_row_id")
        if source and row_id:
            source_row_ids[source] = row_id
    return {
        "source_row_ids": source_row_ids,
        "course_id": row.get("course_id") or intel.get("course_id") or raw.get("course_id") or best.get("course_id"),
        "trainer_key": row.get("trainer_key") or intel.get("trainer_key") or raw.get("trainer_key"),
        "rank": row.get("rank"),
    }


def _backup_options(course_rows: List[Dict[str, Any]], current_email: str) -> List[Dict[str, Any]]:
    options = []
    for row in course_rows:
        email = _email(row)
        if not email or email == current_email:
            continue
        blockers = _risk_flags(row, {})
        if blockers:
            continue
        score = _score(row)
        if score < 45:
            continue
        options.append({
            "trainer_email": email,
            "trainer_name": _name(row),
            "score": score,
            "role": clean_text(row.get("recommendation_role"), "Backup Option"),
        })
    return sorted(options, key=lambda item: item["score"], reverse=True)[:3]


def _normalise_row(row: Dict[str, Any], source: str, index: int) -> Dict[str, Any]:
    out = dict(row)
    out.setdefault("__source", source)
    out.setdefault("__source_row_id", _row_id(row, source, index))
    return out


def build_allocation_decision_objects(
    allocation_rows: List[Dict[str, Any]] | None = None,
    *,
    allocation_intelligence_rows: List[Dict[str, Any]] | None = None,
    course_best_rows: List[Dict[str, Any]] | None = None,
    allocation_risk_rows: List[Dict[str, Any]] | None = None,
    course_allocation_rows: List[Dict[str, Any]] | None = None,
) -> List[Dict[str, Any]]:
    ranked = [_normalise_row(r, "allocation_ranked_trainer_df", i) for i, r in enumerate(allocation_rows or []) if isinstance(r, dict)]
    intel_by_key = _index(allocation_intelligence_rows, "allocation_intelligence_df")
    raw_by_key = _index(course_allocation_rows, "course_allocation_df")
    best_by_course = _best_by_course(course_best_rows)
    risk_by_course = _risk_by_course(allocation_risk_rows)

    if not ranked:
        ranked = [_normalise_row(r, "allocation_intelligence_df", i) for i, r in enumerate(allocation_intelligence_rows or []) if isinstance(r, dict)]
    if not ranked:
        ranked = [_normalise_row(r, "course_allocation_df", i) for i, r in enumerate(course_allocation_rows or []) if isinstance(r, dict)]

    by_course: Dict[str, List[Dict[str, Any]]] = {}
    for row in ranked:
        key = _course_key(row.get("course_name") or row.get("course"))
        if key:
            by_course.setdefault(key, []).append(row)

    objects: List[Dict[str, Any]] = []
    for course_key, course_rows in by_course.items():
        sorted_course_rows = sorted(course_rows, key=lambda r: (r.get("rank") or 99, -_score(r)))
        for idx, row in enumerate(sorted_course_rows, start=1):
            trainer_email = _email(row)
            trainer_name = _name(row)
            course = clean_text(row.get("course_name") or row.get("course"), "Unknown course")
            intel = intel_by_key.get((course_key, trainer_email), {})
            raw = raw_by_key.get((course_key, trainer_email), {})
            best = best_by_course.get(course_key, {})
            risk = risk_by_course.get(course_key, {})
            merged = {**raw, **intel, **row}
            score = _score(merged)
            readiness_score = percent(merged.get("readiness_score") or merged.get("delivery_readiness_score"))
            blockers = _risk_flags(merged, risk)
            risk_tags = _risk_tags(merged, risk, blockers)
            bucket = _readiness_bucket(merged, blockers)
            course_readiness = _course_readiness(merged, blockers)
            role = _role(bucket, merged)
            backup_options = _backup_options(sorted_course_rows, trainer_email)
            confidence = percent(merged.get("confidence"), 60)
            if not trainer_email:
                confidence = min(confidence, 70)
            if course == "Unknown course":
                confidence = min(confidence, 65)
            if blockers:
                confidence = min(confidence, 80)
            action = _recommended_action(bucket, course_readiness, blockers, merged, risk)
            trace = _source_trace(row, intel, best, risk, raw)
            evidence = {
                "reason": merged.get("why_selected") or merged.get("reason") or merged.get("why_this_trainer"),
                "why_not_primary": merged.get("why_not_primary"),
                "trade_offs": as_list(merged.get("trade_offs")),
                "score_components": merged.get("evidence") if isinstance(merged.get("evidence"), dict) else {},
                "allocation_risk": merged.get("allocation_risk") or risk.get("allocation_risk"),
                "risk_reason": risk.get("risk_reason"),
                "best_trainer": best.get("best_trainer"),
                "alternative_trainers": best.get("alternative_trainers") or backup_options,
                "source_row_ids": trace["source_row_ids"],
                "trace": trace,
            }
            objects.append(
                make_decision_object(
                    entity_type="allocation",
                    trainer_email=trainer_email,
                    trainer_name=trainer_name,
                    score=score,
                    confidence=confidence,
                    bucket=bucket,
                    status=course_readiness,
                    blockers=blockers,
                    evidence=evidence,
                    recommended_action=action,
                    source_datasets=SOURCE_DATASETS,
                    object_id=stable_id("allocation", course, trainer_email or trainer_name, idx),
                    extra={
                        "course": course,
                        "course_id": trace.get("course_id"),
                        "course_name": course,
                        "course_requirement": merged.get("course_requirement") or merged.get("requirement") or course,
                        "vendor": merged.get("vendor") or merged.get("oem"),
                        "technology": merged.get("technology"),
                        "domain": merged.get("domain"),
                        "trainer": trainer_name,
                        "rank": merged.get("rank") or idx,
                        "recommendation_role": role,
                        "allocation_score": score,
                        "overall_allocation_score": score,
                        "readiness_score": readiness_score,
                        "course_readiness": course_readiness,
                        "trainer_fit": role,
                        "trainer_status": clean_text(merged.get("trainer_status"), "Eligible" if not blockers else "Restricted"),
                        "allocation_status": (
                            "Blocked" if blockers
                            else "Ready Now" if course_readiness == "ready_now"
                            else "Needs Prep" if course_readiness == "near_ready"
                            else "Not Recommended"
                        ),
                        "allocation_risk": merged.get("allocation_risk") or risk.get("allocation_risk") or ("High" if blockers else "Low"),
                        "risk_flags": risk_tags,
                        "backup_trainer_options": backup_options,
                        "why_selected": merged.get("why_selected") or merged.get("reason") or merged.get("why_this_trainer"),
                        "why_this_trainer": merged.get("why_selected") or merged.get("reason") or merged.get("why_this_trainer"),
                        "why_not_primary": merged.get("why_not_primary"),
                        "reason": merged.get("reason") or merged.get("why_selected"),
                        "trade_offs": as_list(merged.get("trade_offs")),
                        "blocker": "; ".join(blockers),
                        "availability_score": merged.get("availability_score"),
                        "availability_status": merged.get("availability_status"),
                        "availability_confidence": merged.get("availability_confidence"),
                        "feedback_risk": merged.get("feedback_risk") or "Low",
                        "hr_risk": merged.get("hr_risk") or "Low",
                        "qubits_score": merged.get("qubits_score"),
                        "vendor_strength": merged.get("vendor_strength"),
                        "certification_status": merged.get("certification_status"),
                        "delivery_mode_fit": merged.get("delivery_mode_fit"),
                        "custom_batch_experience": merged.get("custom_batch_experience"),
                        "delivery_history": merged.get("delivery_history"),
                        "succession_risk": merged.get("succession_risk"),
                        "manager_action": action,
                        "recommended_manager_action": action,
                        "source_row_ids": trace["source_row_ids"],
                        "source": "backend_decision_object",
                    },
                )
            )
    return sorted(objects, key=lambda row: (clean_text(row.get("course_name")).lower(), row.get("rank") or 99, -row.get("score", 0)))
