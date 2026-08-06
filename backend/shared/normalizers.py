"""Pure normalization helpers for SkillEdge backend data shaping."""

from datetime import datetime

from shared.safety import clean_name, safe_int, safe_num, safe_truthy


def norm_detail(d):
    return {
        "course_name": d.get("CourseName") or "",
        "vendor_name": d.get("VendorName") or "",
        "qubits_score": safe_num(d.get("QubitsScore")),
        "skill_level": d.get("SkillLevel") or "",
        "officially_approved": safe_truthy(d.get("OfficiallyApproved")),
        "is_future_skill": safe_truthy(d.get("Is Future Skill")),
        "future_skill_date": d.get("Future Skill Date") or "",
        "dm": d.get("DM") or "",
        "tech_call_rating": safe_num(d.get("techcallrating")),
        "course_assignment": safe_int(d.get("Course Assignment")),
    }


def norm_skill(s):
    return {
        "course_name": s.get("course_name") or "",
        "course_id": s.get("course_id") or "",
        "is_duplicate": safe_truthy(s.get("is_duplicate_course")),
        "is_discontinue": safe_truthy(s.get("is_discontinue_course")),
    }


def norm_hr(rows):
    r = rows[0] if rows else {}
    return {
        "positive": safe_int(r.get("Positive Count") or r.get("PositiveCount")),
        "negative": safe_int(r.get("Negative Count") or r.get("NegativeCount")),
    }


def norm_negfb(rows):
    return sum(safe_int(r.get("Total")) for r in rows) if rows else 0


def _rows(value):
    if isinstance(value, list):
        return [x for x in value if isinstance(x, dict)]
    if isinstance(value, dict):
        return [value]
    return []


def _safe_text(v):
    return "" if v is None else str(v).strip()


def _parse_dt(v):
    txt = _safe_text(v)
    for fmt in ("%Y-%m-%d", "%d-%m-%Y", "%m/%d/%Y", "%Y/%m/%d"):
        try:
            return datetime.strptime(txt[:10], fmt).date()
        except Exception:
            pass
    return None


def _rows_text(rows):
    return " ".join(_safe_text(x) for x in rows if x is not None)


def _health_dataset_for(api_name):
    a = (api_name or "").lower()
    if "availability engine" in a:
        return "trainer_availability_engine_df"
    if "intelligence engine" in a:
        return "trainer_operations_df, trainer_availability_engine_df"
    if "assignment" in a:
        return "course_allocation_df, trainer_timeline_df"
    if "feedback" in a or "hr" in a:
        return "trainer_operations_df, manager_action_df"
    return "trainer_operations_df"


def _health_page_for(api_name):
    a = (api_name or "").lower()
    if "availability engine" in a:
        return "Data Health, Risk-Taker Candidates, Trainer 360"
    if "intelligence engine" in a:
        return "Data Health, Dashboard, Trainer 360"
    if "assignment" in a:
        return "Allocation Desk, Trainer 360"
    return "Trainer 360, Dashboard"


def _health_impact_for(issue_type):
    i = (issue_type or "").lower()
    if "failed" in i:
        return "Signal unavailable for this trainer; dependent scores use fewer inputs and lower confidence."
    if "parse" in i or "mismatch" in i or "unexpected" in i:
        return "Response could not be parsed; the affected metric is treated as unknown."
    if "unavailable" in i or "missing" in i or i.startswith("no "):
        return "This signal is missing; affected readiness/availability is estimated with reduced confidence."
    return "Reduced data completeness for this trainer."


def _health_fix_for(issue_type):
    i = (issue_type or "").lower()
    if "missing email" in i:
        return "Ensure this reportee has an official email in RMS."
    if "employee id" in i:
        return "Ensure this reportee has an EmpId in RMS."
    if "parse" in i or "mismatch" in i:
        return "Check the source API response format and adjust the normalizer."
    return "Verify the source API is reachable and returning data, then re-run refresh."


def _health_scrub(msg, issue_type):
    txt = str(msg or "").strip()
    for bad in ("accessToken", "deviceToken", "userPassword", "apikey", "userName", "userRole", "Bearer"):
        if bad in txt:
            txt = txt.split(bad)[0].strip() + " [redacted]"
    txt = txt[:240]
    return txt or (issue_type or "Unknown issue")


def parse_utilization(content):
    """
    Utilization returns one row with monthly columns like "Jun 2026": "75.77 / 43.05"
    (load% / utilization%). We extract a real trend, not a single point.
    Returns dict: {current, avg_3m, trend:[{month,util,load}], months:int} or None.
    """
    import re as _re

    row = content[0] if isinstance(content, list) and content else (content if isinstance(content, dict) else {})
    if not isinstance(row, dict):
        return None
    monthly = []
    mpat = _re.compile(r"^[A-Za-z]{3}\s+\d{4}$")
    for k, v in row.items():
        if mpat.match(str(k).strip()) and isinstance(v, str) and "/" in v:
            parts = v.split("/")
            load = safe_num(parts[0].strip())
            util = safe_num(parts[1].strip())
            monthly.append({"month": k, "load": load, "util": util})
    if not monthly:
        return None
    recent = monthly[-3:]
    nonzero = [m for m in monthly if m["util"] > 0]
    current = nonzero[-1]["util"] if nonzero else monthly[-1]["util"]
    avg_3m = round(sum(m["util"] for m in recent) / len(recent), 1) if recent else 0.0
    return {"current": round(current, 1), "avg_3m": avg_3m, "trend": monthly, "months": len(monthly)}


def parse_certs(content):
    row = content[0] if isinstance(content, list) and content else {}
    if not isinstance(row, dict):
        return {"count": 0, "vendors": []}
    meta = {"trainer", "emailid", "email", "certificate count", "certificatecount"}
    vendors = [k for k, v in row.items() if k.strip().lower() not in meta and safe_truthy(v)]
    count = safe_int(row.get("Certificate Count") or row.get("CertificateCount")) or len(vendors)
    return {"count": count, "vendors": sorted(vendors)}


def parse_resume_certifications(value):
    if not value:
        return []
    if isinstance(value, list):
        raw = value
    else:
        raw = [x.strip() for x in str(value).split("#") if x.strip()]
    certs = []
    for item in raw:
        if isinstance(item, dict):
            name = item.get("name") or item.get("title") or item.get("certification") or ""
            image = item.get("image") or item.get("url") or item.get("logo") or ""
        else:
            text = str(item).strip()
            if not text:
                continue
            if ": " in text:
                name, image = text.rsplit(": ", 1)
            elif "https://" in text:
                name, image = text.split("https://", 1)
                image = "https://" + image
                name = name.rstrip(": ").strip()
            else:
                name, image = text, ""
        name = str(name).strip()
        image = str(image).strip()
        if name:
            certs.append({"name": name, "image": image})
    return certs


def norm_resume_details(rows):
    row = rows[0] if isinstance(rows, list) and rows else (rows if isinstance(rows, dict) else {})
    certs = parse_resume_certifications(row.get("Certifications"))
    skills = [x.strip() for x in str(row.get("Skill") or "").split("#") if x.strip()]
    return {
        "trainer_name": row.get("TrainerName") or "",
        "trainer_email": row.get("TrainerEmail") or "",
        "trainer_image": row.get("TrainerImage") or "",
        "resume_languages": [x.strip() for x in str(row.get("Languages") or "").split("#") if x.strip()],
        "resume_certifications": certs,
        "resume_summary": row.get("Summary") or "",
        "resume_experience": row.get("Experience") or "",
        "resume_skills": skills,
        "trainings_delivered_for": row.get("TrainingsDeliveredFor") or "",
        "resume_feedback": row.get("Feedback") or "",
        "trainer_interests": row.get("Interest") or "",
        "resume_raw": row,
    }


def norm_trainer_availability(rows):
    row = rows[0] if isinstance(rows, list) and rows else (rows if isinstance(rows, dict) else {})
    return {
        "trainer": row.get("Trainer") or "",
        "trainer_email": row.get("TrainerEmail") or "",
        "course_assignment": safe_int(row.get("CourseAssignment")),
        "total_assignment": safe_int(row.get("TotalAssignment")),
        "languagee": row.get("Languagee") or "",
        "skill": row.get("skill") or "",
        "mti_issue": row.get("MTI_Issue") or "",
        "rows": rows if isinstance(rows, list) else ([row] if row else []),
    }


def norm_prev_upcoming(rows):
    rows = _rows(rows)
    return {
        "rows": rows,
        "previous_count": len([r for r in rows if _safe_text(r.get("EndDate")) and _safe_text(r.get("EndDate")) < _safe_text(r.get("StartDate"))]),
        "upcoming_count": len(rows),
    }


def norm_feedback_details(rows):
    return _rows(rows)


def norm_last3_util(rows):
    rows = _rows(rows)
    month_utils = [r for r in rows if _safe_text(r.get("MonthName"))]
    return {
        "rows": rows,
        "trend": [{"month": r.get("MonthName"), "util": safe_num(r.get("Utilization"))} for r in month_utils],
        "current": safe_num(month_utils[-1].get("Utilization")) if month_utils else None,
        "avg_3m": round(sum(safe_num(r.get("Utilization")) for r in month_utils[-3:]) / len(month_utils[-3:]), 1) if month_utils[-3:] else None,
    }


def norm_course_without_exam(rows):
    rows = _rows(rows)
    mapping = {}
    name_map = {}
    for r in rows:
        cid = _safe_text(r.get("Courseid"))
        cname = _safe_text(r.get("CName"))
        if cid:
            mapping[cid] = r
        if cname:
            name_map[cname.lower()] = r
    return {"rows": rows, "by_id": mapping, "by_name": name_map}


def norm_exam_course_linked(rows):
    return {"rows": _rows(rows)}


def norm_unique_cert_count(rows):
    rows = _rows(rows)
    row = rows[0] if rows else {}
    count = safe_int(row.get("Total") or row.get("Count") or row.get("UniqueCount") or row.get("Certificate Count"))
    return {"rows": rows, "count": count}


def normalize_health(rows):
    ts = datetime.now().isoformat(timespec="seconds")
    out, seen = [], set()
    for r in rows:
        if not isinstance(r, dict):
            continue
        api_name = r.get("api_name") or r.get("api") or "Unknown source"
        issue_type = r.get("issue_type") or r.get("issue") or "Unknown issue"
        if str(issue_type).strip().lower() in ("ok", "resolved", "all signals resolved", "none", ""):
            continue
        issue_detail = _health_scrub(r.get("issue_detail") or r.get("detail") or issue_type, issue_type)
        email = r.get("email") or ""
        tkey = r.get("trainer_key") or email or ""
        if not email and "@" in str(tkey):
            email = tkey
        norm = {
            "trainer_key": tkey,
            "trainer_name": clean_name(r.get("trainer_name")),
            "email": email,
            "api_name": api_name,
            "issue_type": issue_type,
            "issue_detail": issue_detail,
            "severity": r.get("severity") or ("High" if "failed" in str(issue_type).lower() else "Medium"),
            "business_impact": r.get("business_impact") or _health_impact_for(issue_type),
            "recommended_fix": r.get("recommended_fix") or _health_fix_for(issue_type),
            "affected_dataset": r.get("affected_dataset") or _health_dataset_for(api_name),
            "affected_page": r.get("affected_page") or _health_page_for(api_name),
            "timestamp": r.get("timestamp") or ts,
        }
        dedup = (norm["trainer_key"], norm["api_name"], norm["issue_type"], norm["issue_detail"])
        if dedup in seen:
            continue
        seen.add(dedup)
        out.append(norm)
    return out
