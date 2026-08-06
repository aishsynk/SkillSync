"""Allocation Intelligence: additive course-to-trainer decision logic."""


def _num(value, default=None):
    try:
        return float(value)
    except (TypeError, ValueError):
        return default


def _key(value):
    return str(value or "").strip().lower()


def _risk(score, alternatives, blocker, quality_risk):
    if blocker or quality_risk or alternatives == 0:
        return "High"
    if score < 60 or alternatives < 2:
        return "Medium"
    return "Low"


def _status_from_signals(trainer, base, delivery):
    reasons = []
    neg_count = int(trainer.get("negative_feedback_count") or 0)
    if base.get("blocker"):
        reasons.append(str(base.get("blocker")))
    if str(delivery.get("delivery_risk_level") or "").lower() == "high":
        reasons.append("Delivery risk is high.")
    if neg_count >= 3 or trainer.get("feedback_risk") == "High" or base.get("feedback_risk") == "High":
        reasons.append("Serious negative feedback or quality risk is active.")
    if str(base.get("allocation_status") or "").lower() in ("blocked", "not recommended"):
        reasons.append(f"Current allocation status is {base.get('allocation_status')}.")
    if reasons:
        if base.get("blocker"):
            return "Blocked", reasons
        return "Compliance Restricted", reasons
    return "Eligible", []


def _plain_list(values):
    if not values:
        return []
    if isinstance(values, list):
        return values
    return [values]


def _role(rank, status, total):
    if status != "Eligible":
        return "Not Recommended / Blocked"
    if rank == 1:
        return "Best Trainer"
    if rank == 2:
        return "Strong Alternative"
    if rank == 3:
        return "Backup Option"
    if rank == 4:
        return "Development Candidate"
    return "Only Visible Trainer" if total == 1 else "Development Candidate"


def _capacity_score(delivery_row, availability_row):
    status = (delivery_row or {}).get("delivery_capacity_status") or (availability_row or {}).get("capacity_status")
    util = _num((delivery_row or {}).get("current_utilization"))
    if util is not None:
        if util >= 85:
            return 30.0, "High utilization lowers allocation safety."
        if util < 40:
            return 95.0, "Underutilized capacity improves allocation fit."
    if status in ("Underutilized", "Underused"):
        return 95.0, "Available capacity improves allocation fit."
    if status in ("Balanced", "Trend Down"):
        return 75.0, "Capacity appears manageable."
    if status in ("Overloaded", "Trend Up", "Heavy"):
        return 35.0, "Workload or utilization suggests overload risk."
    return 60.0, "Capacity data is incomplete; score is kept neutral."


def _cert_signal(cert_row, course_name, vendor):
    certs = cert_row or {}
    certifiable = certs.get("certifiable_courses") or []
    accreds = certs.get("oem_accreditations") or []
    names = " ".join(str(x) for x in certifiable + accreds).lower()
    course_hit = _key(course_name) and _key(course_name) in names
    vendor_hit = _key(vendor) and _key(vendor) in names
    if course_hit or vendor_hit or certs.get("certificate_count", 0):
        return 85.0, "Certification/accreditation evidence increases confidence."
    return 55.0, "Certification evidence is missing or indirect."


def _vendor_signal(vendor_rows, vendor):
    if not vendor:
        return 55.0, "Vendor signal unavailable."
    candidates = [r for r in vendor_rows if _key(r.get("vendor")) == _key(vendor)]
    if not candidates:
        return 55.0, "No matching vendor-strength row found."
    best = max(candidates, key=lambda r: _num(r.get("strength_score"), 0) or 0)
    score = _num(best.get("strength_score"), 0) or 0
    label = best.get("strength_label") or "Vendor evidence"
    return min(100.0, max(45.0, score)), f"{label} vendor-strength signal for {vendor}."


def _alternative(row):
    return {
        "trainer_key": row.get("trainer_key"),
        "trainer_name": row.get("trainer_name"),
        "trainer_email": row.get("trainer_email"),
        "allocation_score": row.get("allocation_score"),
        "allocation_risk": row.get("allocation_risk"),
        "confidence": row.get("confidence"),
        "reason": row.get("reason"),
    }


def build_allocation_intelligence(
    trainer_rows,
    course_allocation_rows,
    availability_rows,
    delivery_rows,
    certification_rows,
    vendor_strength_rows,
):
    trainers = [r for r in trainer_rows or [] if isinstance(r, dict)]
    allocation_rows = [r for r in course_allocation_rows or [] if isinstance(r, dict)]
    availability_rows = [r for r in availability_rows or [] if isinstance(r, dict)]
    delivery_rows = [r for r in delivery_rows or [] if isinstance(r, dict)]
    certification_rows = [r for r in certification_rows or [] if isinstance(r, dict)]
    vendor_strength_rows = [r for r in vendor_strength_rows or [] if isinstance(r, dict)]

    trainer_by_email = {_key(r.get("official_email") or r.get("trainer_email") or r.get("email")): r for r in trainers}
    availability_by_email = {_key(r.get("email") or r.get("trainer_email")): r for r in availability_rows}
    delivery_by_email = {_key(r.get("trainer_email")): r for r in delivery_rows}
    cert_by_key = {_key(r.get("trainer_key")): r for r in certification_rows}
    cert_by_name = {_key(r.get("trainer_name")): r for r in certification_rows}

    enriched = []
    by_course = {}
    for base in allocation_rows:
        email = _key(base.get("trainer_email"))
        trainer = trainer_by_email.get(email, {})
        delivery = delivery_by_email.get(email, {})
        availability = availability_by_email.get(email, {})
        cert = cert_by_key.get(_key(base.get("trainer_key"))) or cert_by_name.get(_key(base.get("trainer_name"))) or {}

        q = _num(trainer.get("avg_qubits_score"), 0.0) or 0.0
        skill = _num(base.get("overall_allocation_score"), 0.0) or 0.0
        readiness = _num(delivery.get("delivery_readiness_score"), _num(base.get("readiness_score"), 0.0)) or 0.0
        availability_score = _num(base.get("availability_score"))
        availability_confidence = _num(availability.get("availability_confidence"), _num(delivery.get("availability_confidence"), 50.0)) or 50.0
        capacity, capacity_reason = _capacity_score(delivery, availability)
        cert_score, cert_reason = _cert_signal(cert, base.get("course_name"), base.get("vendor"))
        vendor_score, vendor_reason = _vendor_signal(
            [r for r in vendor_strength_rows if _key(r.get("trainer_key")) == _key(base.get("trainer_key"))],
            base.get("vendor"),
        )
        quality_penalty = (int(trainer.get("negative_feedback_count") or 0) * 8.0)
        availability_component = availability_score if availability_score is not None else 65.0
        score = round(max(0.0, min(100.0, (
            q * 0.18
            + skill * 0.24
            + readiness * 0.24
            + availability_component * 0.14
            + capacity * 0.10
            + cert_score * 0.06
            + vendor_score * 0.04
            - quality_penalty
        ))), 1)
        missing_confidence = 0 if availability_score is not None else 12
        confidence = round(max(35.0, min(95.0, (
            (_num(base.get("confidence"), 60.0) or 60.0) * 0.35
            + availability_confidence * 0.25
            + cert_score * 0.20
            + (_num(delivery.get("delivery_confidence"), 60.0) or 60.0) * 0.20
            - missing_confidence
        ))), 0)
        trainer_status, status_reasons = _status_from_signals(trainer, base, delivery)
        hard_gate = trainer_status != "Eligible"
        quality_risk = int(trainer.get("negative_feedback_count") or 0) > 2
        if hard_gate:
            score = min(score, 40.0)
            confidence = min(confidence, 45.0)

        evidence = {
            "qubit_score": q,
            "skill_allocation_score": skill,
            "delivery_readiness_score": readiness,
            "availability_score": availability_score,
            "availability_confidence": availability_confidence,
            "capacity_score": capacity,
            "certification_signal": cert_score,
            "vendor_strength_signal": vendor_score,
            "negative_feedback_count": trainer.get("negative_feedback_count"),
            "hr_negative_count": trainer.get("hr_negative_count"),
            "trainer_status": trainer_status,
            "status_reasons": status_reasons,
        }
        trade_offs = [
            f"Readiness: {readiness}",
            f"Skill match: {skill}",
            f"Availability: {'missing confidence-only signal' if availability_score is None else availability_score}",
            capacity_reason,
            cert_reason,
            vendor_reason,
        ]
        if quality_penalty:
            trade_offs.append("Quality/compliance risk lowers allocation score.")
        if base.get("blocker"):
            trade_offs.append(f"Blocker present: {base.get('blocker')}")
        if status_reasons:
            trade_offs.extend(status_reasons)

        reason = (
            f"{base.get('trainer_name')} is a safe primary option because capability, readiness, capacity, "
            f"quality, certification, and vendor signals are acceptable for this course."
        )
        if hard_gate:
            reason = (
                f"{base.get('trainer_name')} should not be the primary trainer right now because "
                f"{status_reasons[0] if status_reasons else 'an eligibility restriction is active'} "
                "High Qubits or skill match do not override compliance, delivery, or serious feedback risk."
            )
        elif score < 65:
            reason = (
                f"{base.get('trainer_name')} can be considered only with preparation because the allocation "
                "signals show trade-offs across readiness, availability, certification, or delivery history."
            )

        row = {
            "course_id": base.get("course_id"),
            "course_name": base.get("course_name"),
            "trainer_key": base.get("trainer_key"),
            "trainer_name": base.get("trainer_name"),
            "trainer_email": base.get("trainer_email"),
            "allocation_score": score,
            "base_allocation_score": base.get("overall_allocation_score"),
            "allocation_risk": "Medium",
            "confidence": confidence,
            "reason": reason,
            "evidence": evidence,
            "trade_offs": trade_offs,
            "recommended_manager_action": "Allocate with normal monitoring.",
            "blocker": base.get("blocker"),
            "trainer_status": trainer_status,
            "eligibility_reasons": status_reasons,
            "readiness_score": readiness,
            "utilization": trainer.get("current_utilization"),
            "qubits_score": q,
            "vendor_strength": vendor_score,
            "certification_status": cert_reason,
            "availability_status": availability.get("final_availability_status") or availability.get("capacity_status") or base.get("availability_status"),
            "availability_confidence": availability_confidence,
            "feedback_risk": trainer.get("feedback_risk") or base.get("feedback_risk"),
            "hr_risk": trainer.get("hr_risk") or base.get("hr_risk"),
            "delivery_mode_fit": base.get("delivery_mode_fit") or base.get("ilt_fmat_fit") or "Not available",
            "custom_batch_experience": base.get("custom_batch_count") or delivery.get("custom_batch_count") or "Not available",
            "delivery_history": delivery.get("delivery_history_count") or delivery.get("similar_delivery_count") or base.get("delivery_history_count") or "Not available",
            "succession_risk": "Unknown",
        }
        enriched.append(row)
        by_course.setdefault(_key(base.get("course_id") or base.get("course_name")), []).append(row)

    best_rows = []
    risk_rows = []
    final_rows = []
    ranked_rows = []
    for _, rows in by_course.items():
        rows.sort(key=lambda r: (r.get("trainer_status") != "Eligible", -r["allocation_score"], -r["confidence"]))
        strong_alternatives = [r for r in rows[1:] if r["allocation_score"] >= 65 and not r.get("blocker")]
        best = rows[0]
        eligible_rows = [r for r in rows if r.get("trainer_status") == "Eligible"]
        for row in rows:
            alternatives = len([r for r in rows if r is not row and r["allocation_score"] >= 55 and r.get("trainer_status") == "Eligible"])
            quality_risk = (row["evidence"].get("negative_feedback_count") or 0) > 2 or (row["evidence"].get("hr_negative_count") or 0) > 0
            row["allocation_risk"] = _risk(row["allocation_score"], alternatives, row.get("trainer_status") != "Eligible", quality_risk)
            if row["allocation_risk"] == "High":
                row["recommended_manager_action"] = "Do not use as primary until risk is resolved; choose a safer trainer or build backup coverage."
            elif row["allocation_risk"] == "Medium":
                row["recommended_manager_action"] = "Allocate only after confirming availability and backup coverage."
            final_rows.append(row)
        for idx, row in enumerate(rows[:5], start=1):
            role = _role(idx, row.get("trainer_status"), len(rows))
            not_primary = ""
            if row.get("trainer_status") != "Eligible":
                not_primary = "Eligibility restriction: " + "; ".join(row.get("eligibility_reasons") or ["blocked or restricted"])
            elif idx > 1:
                not_primary = "A stronger or safer trainer is ranked ahead for this course."
            elif len(eligible_rows) == 1:
                not_primary = ""
                row["succession_risk"] = "Single visible capable trainer"

            if len(eligible_rows) == 1:
                row["succession_risk"] = "Single visible capable trainer"
            elif len(eligible_rows) == 2:
                row["succession_risk"] = "Thin backup bench"
            else:
                row["succession_risk"] = "Covered"

            why_selected = row["reason"]
            if role == "Best Trainer" and len(eligible_rows) == 1:
                why_selected += " Only one capable trainer is visible from RMS data, so backup development is required before scaling."

            manager_action = row["recommended_manager_action"]
            if len(eligible_rows) == 1 and row.get("trainer_status") == "Eligible":
                manager_action = "Use with backup awareness; develop backup trainer before scaling this course."

            ranked_rows.append({
                "course_id": row.get("course_id"),
                "course_name": row.get("course_name"),
                "rank": idx,
                "trainer_name": row.get("trainer_name"),
                "trainer_email": row.get("trainer_email"),
                "trainer_status": row.get("trainer_status"),
                "recommendation_role": role,
                "allocation_score": row.get("allocation_score"),
                "readiness_score": row.get("readiness_score"),
                "utilization": row.get("utilization"),
                "qubits_score": row.get("qubits_score"),
                "vendor_strength": row.get("vendor_strength"),
                "certification_status": row.get("certification_status"),
                "availability_status": row.get("availability_status") or "Not available",
                "availability_confidence": row.get("availability_confidence"),
                "feedback_risk": row.get("feedback_risk") or "Not available",
                "hr_risk": row.get("hr_risk") or "Not available",
                "delivery_mode_fit": row.get("delivery_mode_fit") or "Not available",
                "custom_batch_experience": row.get("custom_batch_experience") or "Not available",
                "delivery_history": row.get("delivery_history") or "Not available",
                "succession_risk": row.get("succession_risk"),
                "confidence": row.get("confidence"),
                "reason": row.get("reason"),
                "why_selected": why_selected,
                "why_not_primary": not_primary,
                "trade_offs": _plain_list(row.get("trade_offs")),
                "evidence": row.get("evidence"),
                "manager_action": manager_action,
            })

        best_rows.append({
            "course_id": best.get("course_id"),
            "course_name": best.get("course_name"),
            "best_trainer": best.get("trainer_name"),
            "trainer_email": best.get("trainer_email"),
            "alternative_trainers": [_alternative(r) for r in strong_alternatives[:3]],
            "allocation_score": best.get("allocation_score"),
            "allocation_risk": best.get("allocation_risk"),
            "confidence": best.get("confidence"),
            "reason": best.get("reason"),
            "evidence": best.get("evidence"),
            "trade_offs": best.get("trade_offs"),
            "recommended_manager_action": best.get("recommended_manager_action"),
        })
        risk = "High" if len(eligible_rows) == 1 or best.get("allocation_risk") == "High" else "Medium" if len(strong_alternatives) < 2 else "Low"
        risk_rows.append({
            "course_id": best.get("course_id"),
            "course_name": best.get("course_name"),
            "allocation_risk": risk,
            "primary_trainer": best.get("trainer_name"),
            "primary_score": best.get("allocation_score"),
            "capable_trainer_count": len([r for r in rows if r["allocation_score"] >= 55 and r.get("trainer_status") == "Eligible"]),
            "strong_alternative_count": len(strong_alternatives),
            "risk_reason": "Only one capable trainer visible from RMS data." if len(eligible_rows) == 1 else "Backup bench is thin." if len(strong_alternatives) < 2 else "Multiple strong alternatives reduce allocation risk.",
            "recommended_manager_action": "Build backup coverage before committing." if risk != "Low" else "Proceed with normal backup awareness.",
        })

    final_rows.sort(key=lambda r: (_key(r.get("course_name")), r.get("blocker") is not None, -r["allocation_score"]))
    ranked_rows.sort(key=lambda r: (_key(r.get("course_name")), r.get("rank") or 99))
    best_rows.sort(key=lambda r: _key(r.get("course_name")))
    risk_rows.sort(key=lambda r: ({"High": 0, "Medium": 1, "Low": 2}.get(r["allocation_risk"], 3), _key(r.get("course_name"))))
    return {
        "allocation_intelligence_df": final_rows,
        "course_best_trainer_df": best_rows,
        "allocation_risk_df": risk_rows,
        "allocation_ranked_trainer_df": ranked_rows,
    }
