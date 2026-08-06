"""
Certification Intelligence — pure, API-derived logic.

100% live-signal. No API calls, no file reads, no Excel, no pandas, no notebook,
no snapshot. Operates only on data already fetched by the pipeline:
  vendorCerts, trainerSkills, trainerDetails, courseWithoutExam, courseList, resumeDetails.

IMPORTANT HONESTY RULES
  - `vendorCerts` is an OEM trainer-ACCREDITATION signal (MCT, RHCI, VCI…), NOT exam
    pass/fail. It is never treated as a graded exam result.
  - `resumeDetails.Certifications` is resume-DECLARED evidence only, never verified.
  - Exam Pass/Pending/Cleared has no working API, so `exam_result_status` is always
    "unavailable_via_api". Nothing is invented.
"""

# Known OEM trainer-accreditation body -> vendor keyword (static domain knowledge,
# not a data dependency). Mirrors the map used by growth_intelligence.
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
        if v in b or b in v:
            return True
    return False


def _exam_required_row(course_name, course_id, cwe_by_name, cwe_by_id):
    row = None
    if course_id is not None:
        row = (cwe_by_id or {}).get(str(course_id))
    if row is None:
        row = (cwe_by_name or {}).get(str(course_name or "").strip().lower())
    return row


def _exam_required(row):
    if not row:
        return False
    return str(row.get("Exam Required or Not") or "").strip().lower().startswith("exam req")


def build_certification_block(f, course_without_exam):
    """Per-trainer certification profile, certifiable courses, and gaps — all live."""
    cwe_by_name = (course_without_exam or {}).get("by_name", {})
    cwe_by_id = (course_without_exam or {}).get("by_id", {})

    accreditations = f.get("cert_vendors") or []
    details = f.get("details") or []
    skills = [s for s in (f.get("skills") or []) if not s.get("is_discontinue")]

    # vendor + qubit + skill-level per course from trainerDetails
    detail_by_course = {}
    for d in details:
        cn = (d.get("course_name") or "").strip().lower()
        if cn:
            detail_by_course[cn] = d

    # Union of delivery-capable courses: trainerSkills (id+name) and trainerDetails (name+vendor)
    seen = set()
    catalog = []
    for s in skills:
        cn = (s.get("course_name") or "").strip()
        if not cn:
            continue
        key = cn.lower()
        if key in seen:
            continue
        seen.add(key)
        catalog.append({"course_name": cn, "course_id": s.get("course_id")})
    for d in details:
        cn = (d.get("course_name") or "").strip()
        if not cn:
            continue
        key = cn.lower()
        if key in seen:
            continue
        seen.add(key)
        catalog.append({"course_name": cn, "course_id": None})

    certifiable, gaps = [], []
    for c in catalog:
        row = _exam_required_row(c["course_name"], c["course_id"], cwe_by_name, cwe_by_id)
        if not _exam_required(row):
            continue  # only courses that actually require a certification/exam
        vendor = (row.get("Vendor") or "").strip()
        d = detail_by_course.get(c["course_name"].strip().lower(), {})
        if not vendor:
            vendor = (d.get("vendor_name") or "").strip()
        accredited = _accredited_for(vendor, accreditations)
        entry = {
            "course": c["course_name"],
            "vendor": vendor or "Unknown",
            "exam_required": True,
            "qubit": _num(d.get("qubits_score")),
            "skill_level": d.get("skill_level"),
            "accredited_for_vendor": accredited,
        }
        certifiable.append(entry)
        # gap = capable of delivering (has the skill) + exam required + no OEM accreditation
        if not accredited:
            gaps.append({
                "course": c["course_name"],
                "vendor": vendor or "Unknown",
                "qubit": entry["qubit"],
                "reason": "Exam-required course the trainer can deliver, but no matching OEM accreditation.",
            })

    return {
        "oem_accreditations": accreditations,
        "certificate_count": f.get("cert_count", 0),
        "resume_certifications": f.get("resume_certs", []),  # resume-declared only, unverified
        "certifiable_courses": certifiable,
        "certification_gap_count": len(gaps),
        "certification_gaps": gaps,
        "exam_result_status": "unavailable_via_api",
    }


def build_certification_recommendations(t_row, cert_block):
    """Explainable, prioritized certification recommendations (live-derived)."""
    recs = []
    gaps = cert_block["certification_gaps"]
    accreditations = cert_block["oem_accreditations"]
    resume_certs = cert_block["resume_certifications"]

    # Group gaps by vendor to certify per OEM, not per course
    by_vendor = {}
    for g in gaps:
        by_vendor.setdefault(g["vendor"], []).append(g)

    for vendor, vgaps in by_vendor.items():
        qubits = [g["qubit"] for g in vgaps if g["qubit"] is not None]
        avg_q = round(sum(qubits) / len(qubits), 1) if qubits else None
        strong = avg_q is not None and avg_q >= 80
        courses = [g["course"] for g in vgaps][:5]
        recs.append({
            "recommendation_type": "Certify",
            "title": f"Certify for {vendor}",
            "vendor": vendor,
            "course": courses[0] if courses else None,
            "reason": f"{len(vgaps)} exam-required {vendor} course(s) the trainer can deliver "
                      f"(avg Qubit {avg_q}) but holds no {vendor} OEM accreditation.",
            "evidence": {
                "gap_courses": courses,
                "gap_count": len(vgaps),
                "avg_qubit": avg_q,
                "held_accreditations": accreditations,
            },
            "confidence": 85 if strong else 70 if avg_q is not None else 55,
            "priority": "High" if strong else "Medium",
            "manager_action": f"Nominate for {vendor} trainer accreditation",
        })

    # Resume-declared certs that are not reflected in OEM accreditations -> verify
    if resume_certs and not accreditations:
        names = [c.get("name") for c in resume_certs if isinstance(c, dict) and c.get("name")][:5]
        if names:
            recs.append({
                "recommendation_type": "Verify",
                "title": "Verify resume-declared certifications",
                "vendor": None,
                "course": None,
                "reason": f"Resume declares {len(resume_certs)} certification(s) but no OEM "
                          f"trainer-accreditation is recorded in RMS; verification needed.",
                "evidence": {"resume_certifications": names,
                             "note": "resume-declared only; not verified via API"},
                "confidence": 60,
                "priority": "Medium",
                "manager_action": "Confirm certification records and update RMS accreditation",
            })

    order = {"High": 0, "Medium": 1, "Low": 2}
    recs.sort(key=lambda r: order.get(r["priority"], 3))
    return recs[:6]


def build_certification_summary(cert_rows):
    """Team-level certification posture, per vendor, from per-trainer cert rows."""
    vendors = {}
    for row in cert_rows:
        accredited_vendors = set()
        for a in row.get("oem_accreditations", []):
            for body, vend in _ACCRED_VENDOR.items():
                if str(a).strip().lower() == body:
                    accredited_vendors.add(vend)
        # certifiable courses give the vendor universe this trainer touches
        touched = {}
        for c in row.get("certifiable_courses", []):
            v = c["vendor"]
            t = touched.setdefault(v, {"exam_required": 0, "accredited": c["accredited_for_vendor"]})
            t["exam_required"] += 1
            t["accredited"] = t["accredited"] or c["accredited_for_vendor"]
        for gap in row.get("certification_gaps", []):
            touched.setdefault(gap["vendor"], {"exam_required": 0, "accredited": False})
        for vendor, t in touched.items():
            agg = vendors.setdefault(vendor, {
                "trainer_count": 0, "accredited_trainer_count": 0,
                "exam_required_course_count": 0, "certification_gap_count": 0,
                "recommendation_count": 0,
            })
            agg["trainer_count"] += 1
            if t["accredited"]:
                agg["accredited_trainer_count"] += 1
            agg["exam_required_course_count"] += t["exam_required"]
        for gap in row.get("certification_gaps", []):
            vendors.setdefault(gap["vendor"], {
                "trainer_count": 0, "accredited_trainer_count": 0,
                "exam_required_course_count": 0, "certification_gap_count": 0,
                "recommendation_count": 0,
            })["certification_gap_count"] += 1
        for rec in row.get("certification_recommendations", []):
            v = rec.get("vendor")
            if v and v in vendors:
                vendors[v]["recommendation_count"] += 1

    out = []
    for vendor, a in vendors.items():
        accredited = a["accredited_trainer_count"]
        gaps = a["certification_gap_count"]
        if accredited == 0 and gaps > 0:
            risk = "High"
            action = f"No accredited {vendor} trainer despite exam-required delivery — certify now"
        elif gaps > accredited:
            risk = "Medium"
            action = f"Certification gaps exceed accredited {vendor} trainers — nominate more"
        else:
            risk = "Low"
            action = f"{vendor} certification coverage is adequate"
        out.append({
            "vendor": vendor,
            "trainer_count": a["trainer_count"],
            "accredited_trainer_count": accredited,
            "exam_required_course_count": a["exam_required_course_count"],
            "certification_gap_count": gaps,
            "recommendation_count": a["recommendation_count"],
            "risk_level": risk,
            "manager_action": action,
        })
    out.sort(key=lambda r: (-r["certification_gap_count"], -r["trainer_count"]))
    return out
