"""Organization & Succession Intelligence: additive bench-risk logic."""


def _num(value, default=None):
    try:
        return float(value)
    except (TypeError, ValueError):
        return default


def _key(value):
    return str(value or "").strip().lower()


def _priority(risk):
    return "High" if risk == "High" else "Medium" if risk == "Medium" else "Low"


def _recommendation(risk_type, title, affected, impacted, replacement, reason, evidence, confidence, priority, action):
    return {
        "risk_type": risk_type,
        "title": title,
        "affected_course_or_vendor": affected,
        "impacted_trainer": impacted,
        "replacement_trainer": replacement,
        "reason": reason,
        "evidence": evidence,
        "confidence": confidence,
        "priority": priority,
        "manager_action": action,
    }


def build_organization_intelligence(
    trainer_rows,
    course_allocation_rows,
    allocation_intelligence_rows,
    delivery_rows,
    vendor_strength_rows,
    certification_rows,
    oem_heatmap_rows,
    trainer_count=None,
):
    trainers = [r for r in trainer_rows or [] if isinstance(r, dict)]
    # Team-size calibration: SPOF/bench formulas assume a bench of usable size.
    # A 1-2 person team legitimately has one trainer per course — flagging every
    # course as a "single point of failure" crisis is alarm noise, not insight.
    small_team = (trainer_count if trainer_count is not None else len(trainers)) <= 2
    course_allocation_rows = [r for r in course_allocation_rows or [] if isinstance(r, dict)]
    allocation_rows = [r for r in allocation_intelligence_rows or [] if isinstance(r, dict)]
    delivery_rows = [r for r in delivery_rows or [] if isinstance(r, dict)]
    vendor_strength_rows = [r for r in vendor_strength_rows or [] if isinstance(r, dict)]
    certification_rows = [r for r in certification_rows or [] if isinstance(r, dict)]
    oem_heatmap_rows = [r for r in oem_heatmap_rows or [] if isinstance(r, dict)]

    trainer_by_email = {_key(t.get("official_email") or t.get("trainer_email") or t.get("email")): t for t in trainers}
    delivery_by_email = {_key(t.get("trainer_email")): t for t in delivery_rows}
    cert_by_key = {_key(t.get("trainer_key")): t for t in certification_rows}
    vendor_by_trainer = {}
    for row in vendor_strength_rows:
        vendor_by_trainer.setdefault(_key(row.get("trainer_key")), []).append(row)

    by_course = {}
    for row in allocation_rows:
        course_key = _key(row.get("course_id") or row.get("course_name"))
        if course_key:
            by_course.setdefault(course_key, []).append(row)

    spof_rows = []
    replacement_rows = []
    trainer_impact = {}
    single_courses_by_vendor = {}
    all_courses_by_vendor = {}

    for _, rows in by_course.items():
        rows.sort(key=lambda r: (-(_num(r.get("allocation_score"), 0) or 0), -(_num(r.get("confidence"), 0) or 0)))
        primary = rows[0]
        vendor = None
        for raw in course_allocation_rows:
            if _key(raw.get("course_id") or raw.get("course_name")) == _key(primary.get("course_id") or primary.get("course_name")):
                vendor = raw.get("vendor")
                break
        capable = [r for r in rows if (_num(r.get("allocation_score"), 0) or 0) >= 55 and not r.get("blocker")]
        strong = [r for r in rows if (_num(r.get("allocation_score"), 0) or 0) >= 70 and not r.get("blocker")]
        replacements = [r for r in rows if r is not primary and (_num(r.get("allocation_score"), 0) or 0) >= 55 and r.get("allocation_risk") != "High" and not r.get("blocker")]
        if len(capable) <= 1:
            coverage = "Thin Bench" if small_team else "Single Point of Failure"
        elif len(capable) == 2:
            coverage = "Moderate Risk"
        else:
            coverage = "Covered"
        risk = "High" if coverage == "Single Point of Failure" else "Medium" if coverage in ("Moderate Risk", "Thin Bench") else "Low"
        replacement = replacements[0] if replacements else None
        evidence = {
            "capable_trainer_count": len(capable),
            "strong_trainer_count": len(strong),
            "replacement_count": len(replacements),
            "primary_score": primary.get("allocation_score"),
            "primary_confidence": primary.get("confidence"),
        }
        reason = (
            "Only one capable trainer is visible for this course."
            if risk == "High" else
            "This course is carried by a single trainer on a small team; treat it as thin bench, not a crisis."
            if coverage == "Thin Bench" else
            "Two capable trainers exist, but backup depth is still thin."
            if risk == "Medium" else
            "Three or more capable trainers provide usable coverage."
        )
        action = (
            "Develop a backup trainer before relying on this course at scale."
            if risk == "High" else
            "Strengthen one more backup through mentoring or certification."
            if risk == "Medium" else
            "Maintain coverage and monitor readiness."
        )
        rec = _recommendation(
            "Course Dependency",
            "Course has single-point dependency" if risk == "High" else "Course backup coverage review",
            primary.get("course_name"),
            primary.get("trainer_name"),
            replacement.get("trainer_name") if replacement else "Develop Backup",
            reason,
            evidence,
            90 if rows else 45,
            _priority(risk),
            action,
        )
        spof_rows.append({
            "course_id": primary.get("course_id"),
            "course_name": primary.get("course_name"),
            "vendor": vendor,
            "coverage_status": coverage,
            "single_point_failure": risk == "High",
            "capable_trainer_count": len(capable),
            "strong_trainer_count": len(strong),
            "primary_trainer": primary.get("trainer_name"),
            "primary_trainer_email": primary.get("trainer_email"),
            "replacement_trainer": replacement.get("trainer_name") if replacement else None,
            "replacement_trainer_email": replacement.get("trainer_email") if replacement else None,
            "risk_level": risk,
            "recommendation": rec,
        })
        all_courses_by_vendor.setdefault(_key(vendor) or "unknown", set()).add(primary.get("course_name"))
        if risk == "High":
            single_courses_by_vendor.setdefault(_key(vendor) or "unknown", set()).add(primary.get("course_name"))

        impact = trainer_impact.setdefault(_key(primary.get("trainer_email")), {
            "trainer_name": primary.get("trainer_name"),
            "trainer_email": primary.get("trainer_email"),
            "dependent_courses": [],
            "dependent_vendors": set(),
            "replacement_gaps": 0,
            "recommendations": [],
        })
        if risk in ("High", "Medium"):
            impact["dependent_courses"].append(primary.get("course_name"))
            if vendor:
                impact["dependent_vendors"].add(vendor)
            if not replacement:
                impact["replacement_gaps"] += 1
            impact["recommendations"].append(rec)

        replacement_rows.append({
            "course_id": primary.get("course_id"),
            "course_name": primary.get("course_name"),
            "vendor": vendor,
            "impacted_trainer": primary.get("trainer_name"),
            "impacted_trainer_email": primary.get("trainer_email"),
            "replacement_trainer": replacement.get("trainer_name") if replacement else "Develop Backup",
            "replacement_trainer_email": replacement.get("trainer_email") if replacement else None,
            "replacement_score": replacement.get("allocation_score") if replacement else None,
            "replacement_confidence": replacement.get("confidence") if replacement else None,
            "replacement_status": "Available Replacement" if replacement else "Develop Backup",
            "recommendation": rec,
        })

    organization_rows = []
    for email, impact in trainer_impact.items():
        trainer = trainer_by_email.get(email, {})
        delivery = delivery_by_email.get(email, {})
        dependent_count = len(impact["dependent_courses"])
        loss = min(100, dependent_count * 25 + impact["replacement_gaps"] * 20)
        risk = "High" if loss >= 60 else "Medium" if loss >= 30 else "Low"
        organization_rows.append({
            "trainer_name": impact["trainer_name"],
            "trainer_email": impact["trainer_email"],
            "dependent_course_count": dependent_count,
            "dependent_courses": impact["dependent_courses"],
            "dependent_vendors": sorted(impact["dependent_vendors"]),
            "replacement_gap_count": impact["replacement_gaps"],
            "estimated_capability_loss": loss,
            "succession_risk": risk,
            "delivery_risk_level": delivery.get("delivery_risk_level"),
            "avg_qubits_score": trainer.get("avg_qubits_score"),
            "recommendations": impact["recommendations"][:5],
        })

    succession_rows = []
    for row in organization_rows:
        reason = (
            "Trainer owns multiple course/vendor dependencies with limited replacement coverage."
            if row["succession_risk"] == "High" else
            "Trainer has some dependency concentration that needs backup planning."
            if row["succession_risk"] == "Medium" else
            "Dependency concentration is currently manageable."
        )
        succession_rows.append(_recommendation(
            "Trainer Succession",
            "Trainer unavailability would create delivery impact",
            ", ".join(row["dependent_courses"][:3]),
            row["trainer_name"],
            "Multiple backups needed" if row["replacement_gap_count"] else "Existing backup bench",
            reason,
            {
                "dependent_course_count": row["dependent_course_count"],
                "replacement_gap_count": row["replacement_gap_count"],
                "estimated_capability_loss": row["estimated_capability_loss"],
                "dependent_vendors": row["dependent_vendors"],
            },
            85,
            _priority(row["succession_risk"]),
            "Create backup plan, cross-skill another trainer, and prioritize certification where needed.",
        ))

    oem_rows = []
    for heat in oem_heatmap_rows:
        vendor = heat.get("vendor")
        vendor_key = _key(vendor) or "unknown"
        cert_count = 0
        for cert in certification_rows:
            for course in cert.get("certifiable_courses", []):
                if _key(course.get("vendor")) == vendor_key and course.get("accredited_for_vendor"):
                    cert_count += 1
                    break
        single_count = len(single_courses_by_vendor.get(vendor_key, set()))
        total_courses = len(all_courses_by_vendor.get(vendor_key, set()))
        trainer_count = int(heat.get("trainer_count") or 0)
        strong_count = int(heat.get("strong_trainer_count") or 0)
        future_count = int(heat.get("future_skill_trainer_count") or 0)
        if small_team:
            # A small team has at most one trainer per OEM by construction.
            # Cap the risk so a thin bench reads as "watch" rather than crisis,
            # unless the bench is also weak (no strong trainer for the vendor).
            if strong_count == 0:
                risk = "High"
            elif trainer_count <= 1 or single_count >= 3:
                risk = "Medium"
            else:
                risk = "Low"
        elif trainer_count <= 1 or strong_count == 0 or single_count >= 3:
            risk = "High"
        elif strong_count == 1 or single_count > 0 or cert_count == 0:
            risk = "Medium"
        else:
            risk = "Low"
        reason = (
            "OEM bench has single-trainer, weak-strength, or repeated course dependency risk."
            if risk == "High" else
            "OEM bench is thin (small team) or has some backup and accreditation gaps."
            if risk == "Medium" else
            "OEM bench has usable depth and coverage."
        )
        oem_rows.append({
            "vendor": vendor,
            "bench_risk": risk,
            "trainer_count": trainer_count,
            "strong_trainer_count": strong_count,
            "accredited_trainer_count": heat.get("accredited_trainer_count", cert_count),
            "future_skill_trainer_count": future_count,
            "single_point_course_count": single_count,
            "covered_course_count": max(0, total_courses - single_count),
            "reason": reason,
            "recommendation": _recommendation(
                "OEM Bench Risk",
                "OEM bench needs succession coverage" if risk != "Low" else "OEM bench coverage is healthy",
                vendor,
                None,
                "Develop Backup" if risk != "Low" else "Existing bench",
                reason,
                {
                    "trainer_count": trainer_count,
                    "strong_trainer_count": strong_count,
                    "accredited_trainer_count": heat.get("accredited_trainer_count", cert_count),
                    "future_skill_trainer_count": future_count,
                    "single_point_course_count": single_count,
                },
                85,
                _priority(risk),
                "Cross-skill, certify, or mentor another trainer for this OEM." if risk != "Low" else "Maintain bench health.",
            ),
        })

    organization_rows.sort(key=lambda r: ({"High": 0, "Medium": 1, "Low": 2}.get(r["succession_risk"], 3), -r["estimated_capability_loss"]))
    succession_rows.sort(key=lambda r: ({"High": 0, "Medium": 1, "Low": 2}.get(r["priority"], 3), r["impacted_trainer"] or ""))
    spof_rows.sort(key=lambda r: ({"High": 0, "Medium": 1, "Low": 2}.get(r["risk_level"], 3), _key(r.get("course_name"))))
    replacement_rows.sort(key=lambda r: (r["replacement_status"] != "Develop Backup", _key(r.get("course_name"))))
    oem_rows.sort(key=lambda r: ({"High": 0, "Medium": 1, "Low": 2}.get(r["bench_risk"], 3), _key(r.get("vendor"))))

    return {
        "organization_intelligence_df": organization_rows,
        "succession_risk_df": succession_rows,
        "single_point_failure_df": spof_rows,
        "replacement_recommendation_df": replacement_rows,
        "oem_bench_risk_df": oem_rows,
    }
