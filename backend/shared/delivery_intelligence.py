"""Delivery Intelligence: additive, API-derived delivery decision logic."""


def _num(value, default=None):
    try:
        return float(value)
    except (TypeError, ValueError):
        return default


def _label(score):
    if score >= 80:
        return "Ready"
    if score >= 65:
        return "Ready with Prep"
    if score >= 45:
        return "Needs Mentoring"
    return "Hold"


def _risk_label(score):
    if score >= 70:
        return "High"
    if score >= 35:
        return "Medium"
    return "Low"


def _capacity_status(utilization, availability_row):
    workload = (availability_row or {}).get("workload_status")
    capacity = (availability_row or {}).get("capacity_status")
    if utilization is not None:
        if utilization >= 85:
            return "Overloaded"
        if utilization < 40:
            return "Underutilized"
        return "Balanced"
    if workload in ("Heavy", "Moderate", "Light"):
        return "Overloaded" if workload == "Heavy" else "Balanced"
    return capacity or "Unknown"


def _recommendations(row):
    recs = []
    evidence = {
        "delivery_readiness_score": row["delivery_readiness_score"],
        "capacity_status": row["delivery_capacity_status"],
        "risk_level": row["delivery_risk_level"],
        "assignment_count": row["assignment_count"],
        "avg_qubits_score": row["avg_qubits_score"],
        "availability_confidence": row["availability_confidence"],
    }
    if row["delivery_capacity_status"] == "Overloaded":
        recs.append({
            "recommendation_type": "Capacity",
            "title": "Protect overloaded trainer",
            "reason": "Trainer is delivery-capable but current utilization or workload indicates limited capacity.",
            "evidence": evidence,
            "confidence": 80,
            "priority": "High",
            "manager_action": "Use a backup trainer or delay new allocation until capacity improves.",
        })
    if row["delivery_capacity_status"] == "Underutilized" and (row["avg_qubits_score"] or 0) >= 75:
        recs.append({
            "recommendation_type": "Opportunity",
            "title": "Use underutilized strong trainer",
            "reason": "Strong Qubit signal with available capacity suggests safe additional delivery opportunity.",
            "evidence": evidence,
            "confidence": 80,
            "priority": "Medium",
            "manager_action": "Prioritize this trainer for a suitable upcoming batch.",
        })
    if row["delivery_risk_level"] == "High":
        recs.append({
            "recommendation_type": "Risk",
            "title": "Hold or closely supervise delivery",
            "reason": "Quality or compliance risk signals are present and increase delivery risk.",
            "evidence": evidence,
            "confidence": 85,
            "priority": "High",
            "manager_action": "Require manager review, mock validation, or a safer backup before allocation.",
        })
    if row["skill_without_delivery_count"] > 0:
        recs.append({
            "recommendation_type": "Mentoring",
            "title": "Convert skill coverage into delivery proof",
            "reason": "Trainer has mapped skills that do not yet have matching delivery history.",
            "evidence": {**evidence, "skill_without_delivery_count": row["skill_without_delivery_count"]},
            "confidence": 70,
            "priority": "Medium",
            "manager_action": "Pair with a proven trainer or schedule a mock before first live delivery.",
        })
    if not recs:
        recs.append({
            "recommendation_type": "Delivery",
            "title": "Keep in delivery pool",
            "reason": "Readiness, capacity, and risk signals support continued allocation consideration.",
            "evidence": evidence,
            "confidence": row["delivery_confidence"],
            "priority": "Low",
            "manager_action": "Use for matching courses when capacity and schedule permit.",
        })
    return recs


def build_delivery_intelligence(trainers, allocation_rows, availability_rows):
    trainers = [t for t in trainers or [] if isinstance(t, dict)]
    allocation_rows = [r for r in allocation_rows or [] if isinstance(r, dict)]
    availability_rows = [r for r in availability_rows or [] if isinstance(r, dict)]

    by_email = {str(t.get("official_email") or "").lower(): t for t in trainers}
    av_by_email = {str(r.get("email") or r.get("trainer_key") or "").lower(): r for r in availability_rows}
    alloc_by_email = {}
    alloc_by_course = {}
    for row in allocation_rows:
        email = str(row.get("trainer_email") or "").lower()
        course = str(row.get("course_name") or "").strip()
        if email:
            alloc_by_email.setdefault(email, []).append(row)
        if course:
            alloc_by_course.setdefault(course.lower(), []).append(row)

    delivery_rows = []
    for email, trainer in by_email.items():
        rows = alloc_by_email.get(email, [])
        av = av_by_email.get(email, {})
        q = _num(trainer.get("avg_qubits_score"))
        readiness = _num(trainer.get("overall_readiness_score"), 0.0) or 0.0
        assignment_count = int(trainer.get("assignment_count") or 0)
        history_score = min(100.0, assignment_count * 12.0)
        quality_penalty = (int(trainer.get("negative_feedback_count") or 0) * 12.0)
        util = _num(trainer.get("current_utilization"))
        capacity = _capacity_status(util, av)
        capacity_score = 100.0 if capacity == "Underutilized" else 75.0 if capacity == "Balanced" else 35.0 if capacity == "Overloaded" else 55.0
        availability_confidence = _num(av.get("availability_confidence"), 50.0) or 50.0
        availability_factor = availability_confidence * 0.2
        score = round(max(0.0, min(100.0, readiness * 0.35 + (q or readiness) * 0.25 + history_score * 0.15 + capacity_score * 0.15 + availability_factor - quality_penalty * 0.35)), 1)
        risk_score = min(100.0, quality_penalty + (30.0 if capacity == "Overloaded" else 0.0) + (20.0 if availability_confidence < 50 else 0.0))
        delivered_courses = {str(c or "").strip().lower() for c in (trainer.get("current_courses") or [])}
        allocated_courses = {str(r.get("course_name") or "").strip().lower() for r in rows}
        skill_without_delivery = len([c for c in allocated_courses if c and c not in delivered_courses and assignment_count == 0])

        strengths = []
        constraints = []
        if q is not None and q >= 80:
            strengths.append("High Qubit score")
        if assignment_count > 0:
            strengths.append("Delivery history")
        if capacity == "Underutilized":
            strengths.append("Available capacity")
        if int(trainer.get("negative_feedback_count") or 0) > 0:
            constraints.append("Negative feedback risk")
        if capacity == "Overloaded":
            constraints.append("Overloaded")
        if availability_confidence < 60:
            constraints.append("Low availability confidence")
        if skill_without_delivery > 0:
            constraints.append("Skills need delivery proof")

        backup = None
        for row in sorted(rows, key=lambda r: _num(r.get("overall_allocation_score"), 0) or 0, reverse=True):
            course = str(row.get("course_name") or "").strip().lower()
            candidates = [
                r for r in alloc_by_course.get(course, [])
                if str(r.get("trainer_email") or "").lower() != email and (_num(r.get("overall_allocation_score"), 0) or 0) >= 50
                and not r.get("blocker")
            ]
            if candidates:
                best = sorted(candidates, key=lambda r: _num(r.get("overall_allocation_score"), 0) or 0, reverse=True)[0]
                backup = {
                    "trainer_key": best.get("trainer_key"),
                    "trainer_name": best.get("trainer_name"),
                    "trainer_email": best.get("trainer_email"),
                    "course_name": best.get("course_name"),
                    "allocation_score": best.get("overall_allocation_score"),
                    "reason": "Alternative trainer has mapped skill and allocation evidence for the same course.",
                }
                break

        row = {
            "trainer_key": trainer.get("trainer_key"),
            "trainer_name": trainer.get("trainer_name"),
            "trainer_email": trainer.get("official_email"),
            "delivery_readiness_score": score,
            "delivery_readiness_label": _label(score),
            "delivery_capacity_status": capacity,
            "delivery_risk_level": _risk_label(risk_score),
            "delivery_strengths": strengths,
            "delivery_constraints": constraints,
            "backup_trainer_candidate": backup,
            "delivery_recommendations": [],
            "assignment_count": assignment_count,
            "avg_qubits_score": q,
            "current_utilization": util,
            "availability_confidence": availability_confidence,
            "skill_without_delivery_count": skill_without_delivery,
            "delivery_confidence": round(max(35.0, min(95.0, availability_confidence - len(constraints) * 5 + len(strengths) * 3)), 0),
        }
        row["delivery_recommendations"] = _recommendations(row)
        delivery_rows.append(row)

    course_rows = []
    backup_rows = []
    for course_key, rows in alloc_by_course.items():
        capable = [r for r in rows if (_num(r.get("overall_allocation_score"), 0) or 0) >= 45]
        strong = [r for r in rows if (_num(r.get("overall_allocation_score"), 0) or 0) >= 75 and not r.get("blocker")]
        sorted_rows = sorted(rows, key=lambda r: _num(r.get("overall_allocation_score"), 0) or 0, reverse=True)
        primary = sorted_rows[0] if sorted_rows else {}
        backups = [
            r for r in sorted_rows[1:]
            if (_num(r.get("overall_allocation_score"), 0) or 0) >= 50 and not r.get("blocker")
        ]
        risk = "High" if len(capable) <= 1 or not backups else "Medium" if len(strong) <= 1 else "Low"
        reason = (
            "Only one capable trainer found; single-point-of-failure risk."
            if len(capable) <= 1 else
            "Primary trainer exists but backup bench is thin."
            if not backups or len(strong) <= 1 else
            "Multiple capable trainers provide backup coverage."
        )
        course_rows.append({
            "course_name": primary.get("course_name") or course_key,
            "course_id": primary.get("course_id"),
            "vendor": primary.get("vendor"),
            "capable_trainer_count": len(capable),
            "strong_trainer_count": len(strong),
            "backup_trainer_count": len(backups),
            "delivery_risk_level": risk,
            "risk_reason": reason,
            "primary_trainer": {
                "trainer_key": primary.get("trainer_key"),
                "trainer_name": primary.get("trainer_name"),
                "trainer_email": primary.get("trainer_email"),
                "allocation_score": primary.get("overall_allocation_score"),
            },
            "safe_alternatives": [
                {
                    "trainer_key": r.get("trainer_key"),
                    "trainer_name": r.get("trainer_name"),
                    "trainer_email": r.get("trainer_email"),
                    "allocation_score": r.get("overall_allocation_score"),
                    "confidence": r.get("confidence"),
                }
                for r in backups[:3]
            ],
            "recommendation": {
                "recommendation_type": "Backup Coverage",
                "title": "Build backup coverage" if risk != "Low" else "Maintain backup coverage",
                "reason": reason,
                "evidence": {"capable": len(capable), "strong": len(strong), "backups": len(backups)},
                "confidence": 85 if rows else 40,
                "priority": "High" if risk == "High" else "Medium" if risk == "Medium" else "Low",
                "manager_action": "Identify and mentor a backup trainer." if risk != "Low" else "Keep alternatives warm for continuity.",
            },
        })
        for backup in backups[:3]:
            backup_rows.append({
                "course_name": primary.get("course_name") or backup.get("course_name"),
                "course_id": primary.get("course_id") or backup.get("course_id"),
                "primary_trainer_key": primary.get("trainer_key"),
                "primary_trainer_name": primary.get("trainer_name"),
                "backup_trainer_key": backup.get("trainer_key"),
                "backup_trainer_name": backup.get("trainer_name"),
                "backup_trainer_email": backup.get("trainer_email"),
                "backup_score": backup.get("overall_allocation_score"),
                "backup_reason": "Mapped course skill with acceptable allocation score and no blocking signal.",
            })

    delivery_rows.sort(key=lambda r: (r["delivery_risk_level"] != "High", -r["delivery_readiness_score"]))
    course_rows.sort(key=lambda r: ({"High": 0, "Medium": 1, "Low": 2}.get(r["delivery_risk_level"], 3), r["course_name"]))
    return {
        "delivery_intelligence_df": delivery_rows,
        "course_delivery_risk_df": course_rows,
        "trainer_backup_df": backup_rows,
    }
