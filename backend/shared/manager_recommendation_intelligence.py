"""Manager recommendation roadmaps derived only from unified live datasets."""


def _key(value):
    return str(value or "").strip().lower()


def _num(value, default=None):
    try:
        return float(value)
    except (TypeError, ValueError):
        return default


def _confidence(*signals):
    present = sum(1 for s in signals if s not in (None, "", [], {}))
    return min(90, 45 + present * 12)


def build_feedback_coaching(trainers, delivery_rows):
    delivery_by_email = {_key(r.get("trainer_email")): r for r in delivery_rows or [] if isinstance(r, dict)}
    rows = []
    for trainer in trainers or []:
        if not isinstance(trainer, dict):
            continue
        email = _key(trainer.get("official_email") or trainer.get("trainer_email"))
        delivery = delivery_by_email.get(email, {})
        neg_count = int(trainer.get("negative_feedback_count") or 0)
        feedback_risk = trainer.get("feedback_risk") or "Not available"
        delivery_risk = delivery.get("delivery_risk_level") or "Not available"
        low_conf = _num(trainer.get("confidence")) is not None and _num(trainer.get("confidence")) < 70
        needs_row = neg_count > 0 or feedback_risk not in ("Low", "Not available", None) or delivery_risk in ("High", "Medium") or low_conf
        if not needs_row:
            rows.append({
                "trainer_name": trainer.get("trainer_name"),
                "trainer_email": trainer.get("official_email") or trainer.get("trainer_email"),
                "feedback_signal": "No active negative feedback signal",
                "strength": "No coaching concern visible from current RMS feedback data.",
                "improvement_area": "Monitor quality after next delivery.",
                "evidence": {
                    "negative_feedback_count": neg_count,
                    "feedback_risk": feedback_risk,
                    "delivery_risk": delivery_risk,
                },
                "coaching_action": "No immediate coaching action; continue normal observation.",
                "priority": "Low",
                "confidence": _confidence(feedback_risk, delivery_risk),
            })
            continue
        priority = "High" if neg_count >= 3 or feedback_risk == "High" or delivery_risk == "High" else "Medium"
        issue = []
        if neg_count:
            issue.append(f"{neg_count} negative feedback item(s)")
        if feedback_risk not in ("Low", "Not available", None):
            issue.append(f"{feedback_risk} feedback risk")
        if delivery_risk in ("High", "Medium"):
            issue.append(f"{delivery_risk} delivery quality risk")
        if low_conf:
            issue.append("low confidence signal")
        rows.append({
            "trainer_name": trainer.get("trainer_name"),
            "trainer_email": trainer.get("official_email") or trainer.get("trainer_email"),
            "feedback_signal": "; ".join(issue) or "Quality signal needs review",
            "strength": "Trainer may still have useful capability, but quality risk must be managed before stretch delivery.",
            "improvement_area": "Feedback themes, delivery consistency, learner experience, and readiness confidence.",
            "evidence": {
                "negative_feedback_count": neg_count,
                "feedback_risk": feedback_risk,
                "delivery_risk": delivery_risk,
                "confidence": trainer.get("confidence"),
            },
            "coaching_action": "Review feedback, run a manager or mentor coaching session, then validate improvement with a mock or shadowed delivery.",
            "priority": priority,
            "confidence": _confidence(neg_count, feedback_risk, delivery_risk, trainer.get("confidence")),
        })
    order = {"High": 0, "Medium": 1, "Low": 2}
    rows.sort(key=lambda r: (order.get(r["priority"], 3), _key(r.get("trainer_name"))))
    return rows


def build_future_skill_roadmap(trainers, growth_rows, vendor_strength_rows, executive_investments):
    growth_by_name = {_key(r.get("trainer_name")): r for r in growth_rows or [] if isinstance(r, dict)}
    vendor_by_name = {}
    for row in vendor_strength_rows or []:
        if isinstance(row, dict):
            vendor_by_name.setdefault(_key(row.get("trainer_name")), []).append(row)
    investments = [r for r in executive_investments or [] if isinstance(r, dict)]
    rows = []
    for trainer in trainers or []:
        if not isinstance(trainer, dict):
            continue
        name_key = _key(trainer.get("trainer_name"))
        growth = growth_by_name.get(name_key, {})
        strengths = growth.get("vendor_strengths") or vendor_by_name.get(name_key) or []
        recs = growth.get("growth_recommendations") or []
        future_recs = [r for r in recs if r.get("recommendation_type") in ("Future Skill", "Advanced Growth", "Upskill", "Deployment")]
        if not future_recs and strengths:
            future_recs = [{
                "vendor": strengths[0].get("vendor"),
                "title": f"Extend capability in {strengths[0].get('vendor')}",
                "reason": growth.get("growth_reason") or "Trainer has vendor capability that can be extended when demand appears.",
                "manager_action": growth.get("manager_action") or "Use adjacent-course mentoring before live delivery.",
            }]
        for rec in future_recs[:3]:
            vendor = rec.get("vendor") or growth.get("primary_vendor")
            related = [v for v in strengths if not vendor or _key(v.get("vendor")) == _key(vendor)]
            strength = related[0] if related else (strengths[0] if strengths else {})
            courses_unlocked = strength.get("future_skill_count") or 0
            rows.append({
                "trainer_name": trainer.get("trainer_name"),
                "trainer_email": trainer.get("official_email") or trainer.get("trainer_email"),
                "current_strength": strength.get("strength_label") or growth.get("growth_stage") or "Not available",
                "related_future_skill": rec.get("title") or "Not available",
                "future_oem": vendor or "Not available",
                "future_certification_opportunity": "Not available" if not vendor else f"Review {vendor} accreditation need against certifiable courses.",
                "courses_unlocked": courses_unlocked,
                "reason": rec.get("reason") or "Future skill recommendation derived from growth and vendor-strength signals.",
                "evidence": rec.get("evidence") or strength.get("evidence") or {"investment_context": [i.get("title") for i in investments[:3]]},
                "confidence": rec.get("confidence") or _confidence(strength, rec.get("reason")),
                "manager_action": rec.get("manager_action") or "Assign outline, mentor review, and mock before live delivery.",
            })
    rows.sort(key=lambda r: (-(_num(r.get("confidence"), 0) or 0), _key(r.get("trainer_name"))))
    return rows


def build_future_certification_roadmap(certification_rows, certification_summary):
    summary_by_vendor = {_key(r.get("vendor")): r for r in certification_summary or [] if isinstance(r, dict)}
    rows = []
    for cert in certification_rows or []:
        if not isinstance(cert, dict):
            continue
        gaps = cert.get("certification_gaps") or []
        recs = cert.get("certification_recommendations") or []
        by_vendor = {}
        for gap in gaps:
            by_vendor.setdefault(gap.get("vendor") or "Unknown", []).append(gap)
        for rec in recs:
            vendor = rec.get("vendor") or "Unknown"
            by_vendor.setdefault(vendor, [])
        for vendor, vendor_gaps in by_vendor.items():
            summary = summary_by_vendor.get(_key(vendor), {})
            courses = [g.get("course") for g in vendor_gaps if g.get("course")][:5]
            rec = next((r for r in recs if _key(r.get("vendor")) == _key(vendor)), {})
            rows.append({
                "trainer_name": cert.get("trainer_name"),
                "trainer_email": cert.get("trainer_email") or cert.get("official_email"),
                "current_accreditation": cert.get("oem_accreditations") or [],
                "recommended_certification_area": rec.get("title") or f"{vendor} trainer accreditation",
                "vendor": vendor,
                "certifiable_courses": courses,
                "certification_gap": len(vendor_gaps),
                "business_benefit": f"Improves {vendor} delivery continuity and reduces certification coverage risk.",
                "courses_unlocked": courses,
                "reason": rec.get("reason") or "Exam-required or accreditation-sensitive delivery exists without enough matching accreditation evidence.",
                "confidence": rec.get("confidence") or _confidence(vendor_gaps, summary),
                "manager_action": rec.get("manager_action") or f"Nominate for {vendor} trainer accreditation or verify RMS records.",
                "exam_result_status": "unavailable_via_api",
            })
    rows.sort(key=lambda r: (-(r.get("certification_gap") or 0), _key(r.get("trainer_name"))))
    return rows
