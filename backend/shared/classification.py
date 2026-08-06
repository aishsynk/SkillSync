"""
Server-side trainer classification.

One place decides what "ready", "blocked", "review-required", "compliance-flagged",
"delivery-concerned", "overloaded", and "certification-blocked" mean for a trainer,
instead of every frontend page recomputing its own version of the same judgment
(team.html's riskLabel/isRisk, risk-takers.html's buildRiskRows, allocation-desk.html's
blocker checks, etc. all duplicated slightly different rules for the same questions).

This module invents no new signals — it only reads fields already computed earlier
in the pipeline: the trainer_operations_df row (readiness/utilization/feedback/HR
fields from shared.scoring) and, where available, the matching
trainer_decision_objects entry (assignment_status/readiness_status/primary_blocker
from services.decision_objects, which already runs the real risk-register logic).
"""

_ASSIGNABILITY_BY_STATUS = {
    "ready_now": "assign_now",
    "review_required": "review_required",
    "blocked": "hold",
    "prep_required": "upskill_first",
}

_READINESS_STATE_BY_STATUS = {
    "blocked": "blocked",
    "ready": "ready_now",
    "near_ready": "prep_required",
    "not_ready": "not_ready",
}

_BADGE_BY_ASSIGNABILITY = {
    "assign_now": ("Ready Now", "success"),
    "review_required": ("Review Required", "warning"),
    "hold": ("Hold — Blocked", "danger"),
    "upskill_first": ("Needs Prep", "info"),
}


def _num(v, default=0.0):
    try:
        if v is None:
            return default
        return float(v)
    except (TypeError, ValueError):
        return default


def build_trainer_classification(t_row, decision_row=None):
    """Build one normalized classification object for a trainer_operations_df row.

    t_row: the trainer's own trainer_operations_df entry (already scored/enriched).
    decision_row: the matching entry from trainer_decision_objects, if present.
    """
    t_row = t_row or {}
    decision_row = decision_row or {}

    hr_negative = _num(t_row.get("hr_negative_count"))
    negative_feedback = _num(t_row.get("negative_feedback_count"))
    cert_gap_count = _num(t_row.get("certification_gap_count"))
    utilization = t_row.get("current_utilization")
    utilization_status = str(t_row.get("utilization_status") or "").lower()

    has_compliance_flag = hr_negative > 0
    has_delivery_concern = negative_feedback > 0
    has_certification_blocker = cert_gap_count > 0
    is_overloaded = utilization_status == "overloaded" or (utilization is not None and _num(utilization) >= 85)
    is_underused = (not is_overloaded) and utilization is not None and _num(utilization) < 40
    has_capacity_blocker = is_overloaded

    if has_compliance_flag and has_delivery_concern:
        review_flag_type = "both"
    elif has_compliance_flag:
        review_flag_type = "compliance_flag"
    elif has_delivery_concern:
        review_flag_type = "delivery_concern"
    else:
        review_flag_type = "none"

    assignment_status = str(decision_row.get("assignment_status") or "").lower()
    readiness_status = str(decision_row.get("readiness_status") or "").lower()
    primary_blocker = decision_row.get("primary_blocker") or ""

    assignability = _ASSIGNABILITY_BY_STATUS.get(assignment_status)
    if not assignability:
        # No decision object yet for this trainer — fall back to the trainer's
        # own readiness bucket so classification is never left unset.
        bucket = str(t_row.get("readiness_bucket") or "").lower()
        if has_compliance_flag or "hold" in bucket:
            assignability = "hold"
        elif bucket == "ready now":
            assignability = "assign_now"
        elif has_delivery_concern or has_certification_blocker:
            assignability = "review_required"
        else:
            assignability = "upskill_first"

    readiness_state = _READINESS_STATE_BY_STATUS.get(readiness_status)
    if not readiness_state:
        bucket = str(t_row.get("readiness_bucket") or "").lower()
        if "ready now" in bucket:
            readiness_state = "ready_now"
        elif "hold" in bucket:
            readiness_state = "blocked"
        elif bucket:
            readiness_state = "prep_required"
        else:
            readiness_state = "not_ready"

    if not primary_blocker:
        if has_compliance_flag:
            primary_blocker = "Compliance Flag"
        elif has_certification_blocker:
            primary_blocker = "Certification Blocker"
        elif has_delivery_concern:
            primary_blocker = "Delivery Concern"
        elif is_overloaded:
            primary_blocker = "Capacity Blocker"
        else:
            primary_blocker = ""

    if assignability == "hold":
        manager_next_action = "Hold"
    elif has_certification_blocker:
        manager_next_action = "Certify"
    elif has_compliance_flag:
        manager_next_action = "Review"
    elif has_delivery_concern:
        manager_next_action = "Coach"
    elif assignability == "upskill_first":
        manager_next_action = "Coach"
    elif assignability == "review_required":
        manager_next_action = "Review"
    elif assignability == "assign_now":
        manager_next_action = "Assign"
    else:
        manager_next_action = "Verify"

    badge_label, badge_tone = _BADGE_BY_ASSIGNABILITY.get(assignability, ("Review Required", "warning"))
    if assignability == "review_required":
        if review_flag_type == "compliance_flag":
            badge_label, badge_tone = "Compliance Flag", "warning"
        elif review_flag_type == "delivery_concern":
            badge_label, badge_tone = "Delivery Concern", "warning"
        elif has_certification_blocker:
            badge_label, badge_tone = "Certification Blocker", "warning"

    return {
        "readiness_state": readiness_state,
        "assignability": assignability,
        "review_flag_type": review_flag_type,
        "has_compliance_flag": has_compliance_flag,
        "has_delivery_concern": has_delivery_concern,
        "has_certification_blocker": has_certification_blocker,
        "has_capacity_blocker": has_capacity_blocker,
        "is_overloaded": is_overloaded,
        "is_underused": is_underused,
        "primary_blocker": primary_blocker,
        "manager_next_action": manager_next_action,
        "badge_label": badge_label,
        "badge_tone": badge_tone,
    }
