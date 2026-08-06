"""
Capability & Growth Intelligence — pure, API-derived logic.

100% live-signal. No API calls, no file reads, no Excel, no pandas, no notebook,
no snapshot. Operates only on data already fetched by the pipeline:
  trainerSkills, trainerDetails, vendorCerts, assignments, courseWithoutExam, courseList.

Exam pass/fail is NOT available via any working API, so it is never invented —
`exam_result_status` is reported as "unavailable_via_api".
"""

# Well-known OEM trainer-accreditation body -> vendor keyword. Static domain
# knowledge (not a data dependency); used to check accreditation against a vendor.
_ACCRED_VENDOR = {
    "mct": "microsoft",
    "microsoft certified trainer": "microsoft",
    "rhci": "red hat", "rhca": "red hat",
    "vci": "vmware",
    "ccsi": "cisco", "cci": "cisco",
    "aai": "aws",
    "aci (autodesk)": "autodesk", "aci": "autodesk",
    "jnci": "juniper",
    "fct": "fortinet",
    "oci": "oracle",
    "isaca accredited trainer": "isaca",
    "comptia certified corporate trainers": "comptia",
    "pecb certified trainer": "pecb",
    "(isc)2": "isc2",
}


def _num(v, default=None):
    try:
        return float(v)
    except (TypeError, ValueError):
        return default


def _accredited_for(vendor, accreditations):
    """True if the trainer holds an OEM accreditation matching this vendor."""
    v = (vendor or "").strip().lower()
    if not v:
        return False
    for body in (accreditations or []):
        b = (body or "").strip().lower()
        if not b:
            continue
        mapped = _ACCRED_VENDOR.get(b)
        if mapped and (mapped in v or v in mapped):
            return True
        # token-overlap fallback (honest, conservative)
        if v in b or b in v:
            return True
    return False


def _exam_required(course_name, cwe_by_name):
    row = (cwe_by_name or {}).get(str(course_name or "").strip().lower())
    if not row:
        return False
    return str(row.get("Exam Required or Not") or "").strip().lower().startswith("exam req")


def _strength_label(score):
    if score >= 75:
        return "Strong"
    if score >= 50:
        return "Moderate"
    if score >= 25:
        return "Emerging"
    return "Nascent"


def build_vendor_strengths(f, cwe_by_name):
    """One entry per vendor the trainer has delivered/mapped courses in.

    Vendor attribution comes from trainerDetails (per-course VendorName + Qubit +
    SkillLevel + future-skill). Delivery proof from assignments. Exam-required from
    courseWithoutExam. Accreditation from vendorCerts.
    """
    details = f.get("details") or []
    accreditations = f.get("cert_vendors") or []
    assignment_courses = {str(a.get("Course") or "").strip().lower()
                          for a in (f.get("assignments") or [])}

    groups = {}
    for d in details:
        vendor = (d.get("vendor_name") or "").strip()
        if not vendor:
            continue
        g = groups.setdefault(vendor, {
            "courses": [], "qubits": [], "future": 0, "exam_required": 0,
            "high_qubit": 0, "levels": [],
        })
        cname = d.get("course_name") or ""
        g["courses"].append(cname)
        q = _num(d.get("qubits_score"))
        if q is not None and q > 0:
            g["qubits"].append(q)
            if q >= 80:
                g["high_qubit"] += 1
        if d.get("is_future_skill"):
            g["future"] += 1
        if d.get("skill_level"):
            g["levels"].append(d.get("skill_level"))
        if _exam_required(cname, cwe_by_name):
            g["exam_required"] += 1

    out = []
    for vendor, g in groups.items():
        course_count = len(g["courses"])
        avg_q = round(sum(g["qubits"]) / len(g["qubits"]), 1) if g["qubits"] else None
        vendor_course_set = {c.strip().lower() for c in g["courses"]}
        delivery_proof = sum(1 for c in assignment_courses if c and c in vendor_course_set)
        accredited = _accredited_for(vendor, accreditations)

        base = (avg_q or 0) * 0.45
        breadth = min(30.0, course_count * 5.0)
        proof = min(15.0, delivery_proof * 3.0)
        accred_pts = 10.0 if accredited else 0.0
        future_pts = min(10.0, g["future"] * 3.0)
        score = round(min(100.0, base + breadth + proof + accred_pts + future_pts), 1)

        out.append({
            "vendor": vendor,
            "course_count": course_count,
            "avg_qubits": avg_q,
            "high_qubit_courses": g["high_qubit"],
            "future_skill_count": g["future"],
            "exam_required_course_count": g["exam_required"],
            "accredited_for_vendor": accredited,
            "delivery_proof_count": delivery_proof,
            "strength_score": score,
            "strength_label": _strength_label(score),
            "evidence": {
                "courses": course_count,
                "avg_qubit": avg_q,
                "high_qubit_courses": g["high_qubit"],
                "deliveries": delivery_proof,
                "future_skills": g["future"],
                "exam_required_courses": g["exam_required"],
                "accredited": accredited,
            },
        })
    out.sort(key=lambda v: v["strength_score"], reverse=True)
    return out


def build_capability_profile(f, vendor_strengths):
    details = f.get("details") or []
    skills = [s for s in (f.get("skills") or []) if not s.get("is_discontinue")]
    qubits = [_num(d.get("qubits_score")) for d in details]
    qubits = [q for q in qubits if q is not None and q > 0]
    levels = [d.get("skill_level") for d in details if d.get("skill_level")]
    top_level = max(set(levels), key=levels.count) if levels else None
    return {
        "total_courses": len(skills),
        "vendor_courses": len(details),
        "distinct_vendors": len(vendor_strengths),
        "avg_qubit": round(sum(qubits) / len(qubits), 1) if qubits else None,
        "high_qubit_courses": sum(1 for q in qubits if q >= 80),
        "future_skill_courses": sum(1 for d in details if d.get("is_future_skill")),
        "exam_required_courses": sum(v["exam_required_course_count"] for v in vendor_strengths),
        "oem_accreditations": f.get("cert_vendors") or [],
        "top_skill_level": top_level,
    }


def build_growth_recommendations(f, t_row, vendor_strengths):
    util = t_row.get("current_utilization")
    avg_q = None
    if vendor_strengths:
        qs = [v["avg_qubits"] for v in vendor_strengths if v["avg_qubits"] is not None]
        avg_q = round(sum(qs) / len(qs), 1) if qs else None
    recs = []

    for v in vendor_strengths:
        strong = v["strength_label"] in ("Strong", "Moderate")

        # 1 & 5 — strong vendor + exam-required courses but NOT accredited -> certify
        if strong and not v["accredited_for_vendor"] and v["exam_required_course_count"] > 0:
            recs.append({
                "recommendation_type": "Certification",
                "title": f"Push {v['vendor']} accreditation",
                "vendor": v["vendor"],
                "reason": f"{v['strength_label']} in {v['vendor']} ({v['course_count']} courses, "
                          f"avg Qubit {v['avg_qubits']}) but no OEM accreditation, and "
                          f"{v['exam_required_course_count']} delivered course(s) require certification.",
                "evidence": v["evidence"],
                "confidence": 85 if v["avg_qubits"] is not None else 60,
                "priority": "High",
                "manager_action": f"Nominate for {v['vendor']} trainer certification",
            })

        # 2 — strong vendor + future skills -> expand future-skill delivery
        if strong and v["future_skill_count"] > 0:
            recs.append({
                "recommendation_type": "Future Skill",
                "title": f"Expand future-skill delivery in {v['vendor']}",
                "vendor": v["vendor"],
                "reason": f"{v['future_skill_count']} future-skill course(s) flagged in {v['vendor']} "
                          f"where the trainer is already {v['strength_label'].lower()}.",
                "evidence": v["evidence"],
                "confidence": 80,
                "priority": "Medium",
                "manager_action": f"Assign upcoming {v['vendor']} future-skill batches",
            })

        # 4 — emerging vendor exposure -> upskill
        if v["strength_label"] == "Emerging" and (v["future_skill_count"] > 0 or v["course_count"] >= 2):
            recs.append({
                "recommendation_type": "Upskill",
                "title": f"Grow {v['vendor']} depth",
                "vendor": v["vendor"],
                "reason": f"Emerging exposure in {v['vendor']} ({v['course_count']} course(s)); "
                          f"room to deepen into a delivery-ready OEM.",
                "evidence": v["evidence"],
                "confidence": 65,
                "priority": "Low",
                "manager_action": f"Add {v['vendor']} upskilling to the development plan",
            })

    # 3 — high Qubit + low utilization -> deploy more delivery
    if avg_q is not None and avg_q >= 80 and util is not None and util < 40:
        primary = vendor_strengths[0]["vendor"] if vendor_strengths else "their strong OEM"
        recs.append({
            "recommendation_type": "Deployment",
            "title": "Deploy more delivery",
            "vendor": primary,
            "reason": f"High capability (avg Qubit {avg_q}) with low utilization ({round(util)}%); "
                      f"capacity to take more {primary} delivery.",
            "evidence": {"avg_qubit": avg_q, "utilization": util},
            "confidence": 80,
            "priority": "High",
            "manager_action": f"Allocate additional {primary} batches",
        })

    # 6 — strong cluster -> nominate for advanced OEM growth
    strong_vendors = [v for v in vendor_strengths if v["strength_label"] == "Strong"]
    if strong_vendors:
        v = strong_vendors[0]
        recs.append({
            "recommendation_type": "Advanced Growth",
            "title": f"Advance in {v['vendor']}",
            "vendor": v["vendor"],
            "reason": f"Strong {v['vendor']} cluster (score {v['strength_score']}); ready for "
                      f"advanced / next-tier {v['vendor']} courses.",
            "evidence": v["evidence"],
            "confidence": 75,
            "priority": "Medium",
            "manager_action": f"Nominate for advanced {v['vendor']} enablement",
        })

    order = {"High": 0, "Medium": 1, "Low": 2}
    recs.sort(key=lambda r: order.get(r["priority"], 3))
    return recs[:6]


def build_trainer_growth(f, t_row, course_without_exam):
    cwe_by_name = (course_without_exam or {}).get("by_name", {})
    vendor_strengths = build_vendor_strengths(f, cwe_by_name)
    capability_profile = build_capability_profile(f, vendor_strengths)
    recs = build_growth_recommendations(f, t_row, vendor_strengths)

    primary = vendor_strengths[0]["vendor"] if vendor_strengths else None
    secondary = [v["vendor"] for v in vendor_strengths[1:4] if v["strength_score"] >= 25]

    top = recs[0] if recs else None
    stage_map = {
        "Certification": "Certify Now",
        "Deployment": "Deploy More",
        "Future Skill": "Expand OEM",
        "Advanced Growth": "Expand OEM",
        "Upskill": "Upskill",
    }
    growth_stage = stage_map.get(top["recommendation_type"], "Steady") if top else "Steady"
    growth_reason = top["reason"] if top else "No growth action needed right now; capability is steady."
    if capability_profile["avg_qubit"] is None:
        growth_confidence = 55
    elif vendor_strengths:
        growth_confidence = 85
    else:
        growth_confidence = 60

    return {
        "vendor_strengths": vendor_strengths,
        "primary_vendor": primary,
        "secondary_vendors": secondary,
        "capability_profile": capability_profile,
        "growth_recommendations": recs,
        "growth_stage": growth_stage,
        "growth_reason": growth_reason,
        "growth_confidence": growth_confidence,
        "exam_result_status": "unavailable_via_api",
    }


def build_oem_heatmap(trainer_growth_rows):
    """Aggregate per-team OEM capability from the per-trainer growth rows."""
    vendors = {}
    for row in trainer_growth_rows:
        for v in row.get("vendor_strengths", []):
            agg = vendors.setdefault(v["vendor"], {
                "trainer_count": 0, "strong_trainer_count": 0,
                "accredited_trainer_count": 0, "future_skill_trainer_count": 0,
                "scores": [],
            })
            agg["trainer_count"] += 1
            if v["strength_label"] == "Strong":
                agg["strong_trainer_count"] += 1
            if v["accredited_for_vendor"]:
                agg["accredited_trainer_count"] += 1
            if v["future_skill_count"] > 0:
                agg["future_skill_trainer_count"] += 1
            agg["scores"].append(v["strength_score"])

    out = []
    for vendor, a in vendors.items():
        avg_score = round(sum(a["scores"]) / len(a["scores"]), 1) if a["scores"] else 0.0
        if a["strong_trainer_count"] == 0:
            risk = "High"
            action = f"No strong {vendor} trainer — certify/develop capability now"
        elif a["trainer_count"] <= 1:
            risk = "High"
            action = f"Single {vendor} trainer — build a backup to remove single-point-of-failure"
        elif a["strong_trainer_count"] == 1:
            risk = "Medium"
            action = f"Develop a second strong {vendor} trainer"
        else:
            risk = "Low"
            action = f"Healthy {vendor} bench — consider advanced growth"
        out.append({
            "vendor": vendor,
            "trainer_count": a["trainer_count"],
            "strong_trainer_count": a["strong_trainer_count"],
            "accredited_trainer_count": a["accredited_trainer_count"],
            "future_skill_trainer_count": a["future_skill_trainer_count"],
            "avg_strength_score": avg_score,
            "capability_risk": risk,
            "recommended_manager_action": action,
        })
    out.sort(key=lambda r: (-r["trainer_count"], -r["avg_strength_score"]))
    return out
