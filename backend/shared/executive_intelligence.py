"""Executive Intelligence: leadership-level rollups from unified datasets."""


def _num(value, default=None):
    try:
        return float(value)
    except (TypeError, ValueError):
        return default


def _avg(values):
    vals = [v for v in (_num(v) for v in values) if v is not None]
    return round(sum(vals) / len(vals), 1) if vals else None


def _key(value):
    return str(value or "").strip().lower()


def _row(title, category, priority, reason, evidence, confidence, action, **extra):
    out = {
        "title": title,
        "category": category,
        "priority": priority,
        "reason": reason,
        "evidence": evidence,
        "confidence": confidence,
        "recommended_action": action,
    }
    out.update(extra)
    return out


def _priority_from_risk(risk):
    return "High" if risk == "High" else "Medium" if risk == "Medium" else "Low"


def build_executive_intelligence(datasets):
    trainers = [r for r in datasets.get("trainer_operations_df", []) if isinstance(r, dict)]
    vendor_strength = [r for r in datasets.get("vendor_strength_df", []) if isinstance(r, dict)]
    growth = [r for r in datasets.get("growth_intelligence_df", []) if isinstance(r, dict)]
    certification = [r for r in datasets.get("certification_intelligence_df", []) if isinstance(r, dict)]
    cert_gaps = [r for r in datasets.get("certification_gap_df", []) if isinstance(r, dict)]
    cert_summary = [r for r in datasets.get("certification_summary_df", []) if isinstance(r, dict)]
    delivery = [r for r in datasets.get("delivery_intelligence_df", []) if isinstance(r, dict)]
    allocation = [r for r in datasets.get("allocation_intelligence_df", []) if isinstance(r, dict)]
    course_best = [r for r in datasets.get("course_best_trainer_df", []) if isinstance(r, dict)]
    allocation_risk = [r for r in datasets.get("allocation_risk_df", []) if isinstance(r, dict)]
    organization = [r for r in datasets.get("organization_intelligence_df", []) if isinstance(r, dict)]
    succession = [r for r in datasets.get("succession_risk_df", []) if isinstance(r, dict)]
    spof = [r for r in datasets.get("single_point_failure_df", []) if isinstance(r, dict)]
    replacement = [r for r in datasets.get("replacement_recommendation_df", []) if isinstance(r, dict)]
    oem_bench = [r for r in datasets.get("oem_bench_risk_df", []) if isinstance(r, dict)]
    oem_heatmap = [r for r in datasets.get("oem_capability_heatmap_df", []) if isinstance(r, dict)]
    health = [r for r in datasets.get("data_health_df", []) if isinstance(r, dict)]

    strong_trainers = [t for t in trainers if (_num(t.get("avg_qubits_score"), 0) or 0) >= 80 or t.get("readiness_bucket") == "Ready Now"]
    accredited_trainers = [c for c in certification if c.get("oem_accreditations")]
    underutilized_strong = [
        d for d in delivery
        if d.get("delivery_capacity_status") in ("Underutilized", "Underused")
        and (_num(d.get("avg_qubits_score"), 0) or 0) >= 80
    ]
    high_alloc = [r for r in allocation_risk if r.get("allocation_risk") == "High"]
    single_courses = [r for r in spof if r.get("single_point_failure") or r.get("risk_level") == "High"]
    oem_at_risk = [r for r in oem_bench if r.get("bench_risk") in ("High", "Medium")]
    avg_delivery = _avg(d.get("delivery_readiness_score") for d in delivery)
    avg_vendor = _avg(v.get("strength_score") for v in vendor_strength)
    # Team-size calibration: a small team legitimately produces one high-risk
    # allocation per course and one SPOF per vendor. Cap those counts when
    # computing the health label so the summary reflects relative depth rather
    # than absolute volume — a 2-person team reads as "thin bench / watch", not
    # "27 crises". Raw counts stay in `summary_evidence` for honesty.
    small_team = len(trainers) <= 2
    high_alloc_score = min(len(high_alloc), 2) if small_team else len(high_alloc)
    single_score = min(len(single_courses), 2) if small_team else len(single_courses)
    oem_high_score = min(len([r for r in oem_bench if r.get("bench_risk") == "High"]), 2) if small_team else len([r for r in oem_bench if r.get("bench_risk") == "High"])
    high_signals = high_alloc_score + single_score + oem_high_score
    if high_signals >= 8 or len(health) > max(8, len(trainers) * 4):
        health_label = "Needs Attention"
        priority = "High"
    elif high_signals >= 3 or oem_at_risk:
        health_label = "Watch"
        priority = "Medium"
    else:
        health_label = "Healthy"
        priority = "Low"

    summary_evidence = {
        "total_trainers": len(trainers),
        "strong_trainers": len(strong_trainers),
        "accredited_trainers": len(accredited_trainers),
        "underutilized_strong_trainers": len(underutilized_strong),
        "high_risk_allocations": len(high_alloc),
        "single_point_courses": len(single_courses),
        "oems_at_risk": len(oem_at_risk),
        "certification_gaps": len(cert_gaps),
        "average_delivery_readiness": avg_delivery,
        "average_vendor_strength": avg_vendor,
        "data_health_issues": len(health),
        "overall_team_health_label": health_label,
    }
    executive_summary = [_row(
        "Manager/team executive health",
        "Executive Summary",
        priority,
        f"Overall team health is {health_label} based on bench depth, allocation risk, certification gaps, and data quality.",
        summary_evidence,
        90 if trainers else 45,
        "Prioritize high-risk dependencies and certification/bench gaps before expanding delivery load.",
        **summary_evidence,
    )]

    cert_summary_by_vendor = {_key(r.get("vendor")): r for r in cert_summary}
    bench_by_vendor = {_key(r.get("vendor")): r for r in oem_bench}
    heat_by_vendor = {_key(r.get("vendor")): r for r in oem_heatmap}
    spof_by_vendor = {}
    for row in spof:
        spof_by_vendor.setdefault(_key(row.get("vendor")) or "unknown", []).append(row)
    delivery_by_email = {_key(r.get("trainer_email")): r for r in delivery}
    vendor_trainers = {}
    for row in vendor_strength:
        vendor_trainers.setdefault(_key(row.get("vendor")) or "unknown", []).append(row)

    oem_rows = []
    for vendor_key, rows in vendor_trainers.items():
        vendor = rows[0].get("vendor") or vendor_key
        heat = heat_by_vendor.get(vendor_key, {})
        bench = bench_by_vendor.get(vendor_key, {})
        cert = cert_summary_by_vendor.get(vendor_key, {})
        trainer_emails = {_key(r.get("trainer_email")) for r in rows if r.get("trainer_email")}
        readiness = _avg(delivery_by_email.get(email, {}).get("delivery_readiness_score") for email in trainer_emails)
        single_count = len([r for r in spof_by_vendor.get(vendor_key, []) if r.get("risk_level") == "High"])
        risk = bench.get("bench_risk") or heat.get("capability_risk") or "Low"
        cert_gaps_count = int(cert.get("certification_gap_count") or 0)
        if single_count > 0 or cert_gaps_count > 0 or risk == "High":
            priority = "High" if risk == "High" or single_count > 2 else "Medium"
        else:
            priority = "Low"
        evidence = {
            "trainer_count": heat.get("trainer_count", len(trainer_emails)),
            "strong_trainer_count": heat.get("strong_trainer_count"),
            "accredited_trainer_count": heat.get("accredited_trainer_count", cert.get("accredited_trainer_count")),
            "future_skill_trainer_count": heat.get("future_skill_trainer_count"),
            "certification_gap_count": cert_gaps_count,
            "single_point_course_count": single_count,
            "avg_vendor_strength": _avg(r.get("strength_score") for r in rows),
            "avg_delivery_readiness": readiness,
            "bench_risk": risk,
            "best_trainer_course_rows": len(course_best),
        }
        reason = (
            f"{vendor} has bench or dependency risk that needs leadership attention."
            if priority != "Low" else
            f"{vendor} has usable bench coverage and capability signals."
        )
        action = (
            f"Invest in backup development and certification for {vendor}."
            if priority != "Low" else
            f"Maintain {vendor} bench health and use strong trainers strategically."
        )
        oem_rows.append(_row(
            f"{vendor} OEM health",
            "OEM Health",
            priority,
            reason,
            evidence,
            85,
            action,
            vendor=vendor,
            bench_risk=risk,
        ))

    risk_rows = []
    for row in allocation_risk:
        if row.get("allocation_risk") == "High":
            risk_rows.append(_row(
                f"High allocation risk: {row.get('course_name')}",
                "Allocation Risk",
                "High",
                row.get("risk_reason") or "Course allocation has high delivery risk.",
                row,
                85,
                row.get("recommended_manager_action") or "Review allocation and confirm backup before committing.",
                impact="Delivery disruption",
                likelihood="High",
            ))
    for row in delivery:
        if row.get("delivery_risk_level") == "High":
            risk_rows.append(_row(
                f"Trainer delivery risk: {row.get('trainer_name')}",
                "Delivery Risk",
                "High",
                "Trainer has high delivery risk signals.",
                row,
                row.get("delivery_confidence") or 75,
                "Reduce exposure, supervise delivery, or assign backup.",
                impact="Quality or delivery failure",
                likelihood="Medium",
            ))
    for row in cert_gaps:
        risk_rows.append(_row(
            f"Certification gap: {row.get('vendor') or 'Unknown vendor'}",
            "Certification Risk",
            "Medium",
            row.get("reason") or "Exam-required capability lacks matching accreditation.",
            row,
            75,
            "Push certification/accreditation for affected trainer or OEM.",
            impact="Accreditation or delivery eligibility gap",
            likelihood="Medium",
        ))
    for row in succession:
        if row.get("priority") in ("High", "Medium"):
            risk_rows.append(_row(
                row.get("title") or "Succession risk",
                "Succession Risk",
                row.get("priority"),
                row.get("reason"),
                row.get("evidence") or row,
                row.get("confidence") or 80,
                row.get("manager_action") or "Build backup coverage.",
                impact="Capability loss if trainer unavailable",
                likelihood="Medium",
            ))
    for row in oem_bench:
        if row.get("bench_risk") in ("High", "Medium"):
            rec = row.get("recommendation") or {}
            risk_rows.append(_row(
                f"OEM bench risk: {row.get('vendor')}",
                "OEM Bench Risk",
                _priority_from_risk(row.get("bench_risk")),
                row.get("reason"),
                row,
                rec.get("confidence", 85),
                rec.get("manager_action") or "Grow OEM bench depth.",
                impact="OEM delivery capacity constraint",
                likelihood="Medium",
            ))
    risk_rows.sort(key=lambda r: ({"High": 0, "Medium": 1, "Low": 2}.get(r["priority"], 3), r["category"], r["title"]))

    investment_rows = []
    if single_courses:
        investment_rows.append(_row(
            "Build backup trainers for single-point courses",
            "Investment",
            "High",
            "Single-point course dependencies create delivery continuity risk.",
            {"single_point_courses": len(single_courses), "examples": [r.get("course_name") for r in single_courses[:5]]},
            90,
            "Select backup candidates, mentor them, and validate readiness through mock or shadow delivery.",
            investment_type="Build Backup Trainer",
        ))
    if cert_gaps:
        investment_rows.append(_row(
            "Push certification for exam-required capability",
            "Investment",
            "High" if len(cert_gaps) >= 3 else "Medium",
            "Certification gaps may limit accredited delivery coverage.",
            {"certification_gap_count": len(cert_gaps), "vendors": sorted({_key(r.get("vendor")) for r in cert_gaps if r.get("vendor")})},
            85,
            "Prioritize OEM accreditation for high-Qubit trainers with delivery demand.",
            investment_type="Certification Push",
        ))
    for row in oem_bench:
        if row.get("bench_risk") in ("High", "Medium"):
            investment_rows.append(_row(
                f"Grow {row.get('vendor')} OEM bench",
                "Investment",
                _priority_from_risk(row.get("bench_risk")),
                row.get("reason"),
                row,
                85,
                "Cross-skill, certify, or mentor another trainer for this OEM.",
                investment_type="Grow OEM Bench",
            ))
    if underutilized_strong:
        investment_rows.append(_row(
            "Deploy underutilized strong trainers",
            "Investment",
            "Medium",
            "Strong trainers with spare capacity can reduce pressure on risky allocations.",
            {"trainer_count": len(underutilized_strong), "trainers": [r.get("trainer_name") for r in underutilized_strong[:5]]},
            85,
            "Use these trainers for appropriate allocations or as backup builders.",
            investment_type="Deploy Capacity",
        ))
    if health:
        investment_rows.append(_row(
            "Improve data quality for leadership decisions",
            "Investment",
            "Medium" if len(health) >= 5 else "Low",
            "Data health gaps reduce confidence in delivery, allocation, and succession decisions.",
            {"data_health_issue_count": len(health), "examples": health[:5]},
            75,
            "Resolve missing or unstable source signals before making high-stakes allocations.",
            investment_type="Data Quality",
        ))

    vendor_count_by_trainer = {}
    for row in vendor_strength:
        vendor_count_by_trainer.setdefault(_key(row.get("trainer_key")), []).append(row)
    delivery_by_name = {_key(r.get("trainer_name")): r for r in delivery}
    org_by_name = {_key(r.get("trainer_name")): r for r in organization}
    cert_by_name = {_key(r.get("trainer_name")): r for r in certification}
    growth_by_name = {_key(r.get("trainer_name")): r for r in growth}
    strategic_rows = []
    for trainer in trainers:
        name = trainer.get("trainer_name")
        tkey = _key(trainer.get("trainer_key"))
        drow = delivery_by_name.get(_key(name), {})
        orow = org_by_name.get(_key(name), {})
        crow = cert_by_name.get(_key(name), {})
        grow = growth_by_name.get(_key(name), {})
        vendors = vendor_count_by_trainer.get(tkey, [])
        strong_vendor_count = len([v for v in vendors if v.get("strength_label") == "Strong" or (_num(v.get("strength_score"), 0) or 0) >= 70])
        score = 0
        score += 25 if (_num(trainer.get("avg_qubits_score"), 0) or 0) >= 85 else 15 if (_num(trainer.get("avg_qubits_score"), 0) or 0) >= 75 else 0
        score += 20 if len(vendors) >= 3 else 10 if len(vendors) >= 2 else 0
        score += 15 if crow.get("oem_accreditations") else 0
        score += 20 if (_num(drow.get("delivery_readiness_score"), 0) or 0) >= 80 else 10 if (_num(drow.get("delivery_readiness_score"), 0) or 0) >= 65 else 0
        score += 15 if orow.get("succession_risk") in ("High", "Medium") else 0
        score += 10 if grow.get("growth_stage") in ("Growth Candidate", "Risk Taker") else 0
        if score < 40:
            continue
        priority = "High" if score >= 70 else "Medium"
        evidence = {
            "strategic_score": score,
            "avg_qubits_score": trainer.get("avg_qubits_score"),
            "vendor_count": len(vendors),
            "strong_vendor_count": strong_vendor_count,
            "accreditation_count": len(crow.get("oem_accreditations") or []),
            "delivery_readiness_score": drow.get("delivery_readiness_score"),
            "succession_risk": orow.get("succession_risk"),
            "growth_stage": grow.get("growth_stage"),
        }
        strategic_rows.append(_row(
            f"Strategic trainer: {name}",
            "Strategic Trainer",
            priority,
            "Trainer combines capability, readiness, accreditation, or backup-critical value.",
            evidence,
            85,
            "Protect capacity, use for strategic delivery, and develop as mentor/backup owner.",
            trainer_name=name,
            trainer_email=trainer.get("official_email"),
            strategic_score=score,
        ))
    strategic_rows.sort(key=lambda r: (-r["strategic_score"], r["trainer_name"] or ""))

    return {
        "executive_summary_df": executive_summary,
        "executive_oem_health_df": oem_rows,
        "executive_risk_register_df": risk_rows[:50],
        "executive_investment_recommendation_df": investment_rows,
        "strategic_trainer_df": strategic_rows,
    }
