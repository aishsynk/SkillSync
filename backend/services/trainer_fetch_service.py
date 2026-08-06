"""Trainer fetch orchestration for SkillEdge unified intelligence."""

from datetime import date, timedelta
from concurrent.futures import ThreadPoolExecutor

from api.client import _call
from api.config import is_configured
from shared.normalizers import (
    norm_detail,
    norm_skill,
    norm_hr,
    norm_negfb,
    parse_utilization,
    parse_certs,
    norm_resume_details,
    norm_trainer_availability,
    norm_prev_upcoming,
    norm_feedback_details,
    norm_last3_util,
    norm_unique_cert_count,
    _rows,
)


def fetch_trainer(trainer):
    email = trainer.get("OffEmail") or ""
    emp = str(trainer.get("EmpId") or trainer.get("TrainerId") or "")
    health = []
    today = date.today()
    d1 = (today - timedelta(days=30)).isoformat()
    d2 = (today + timedelta(days=60)).isoformat()

    def safe(api_name, fn):
        try:
            return fn()
        except Exception as e:  # noqa: BLE001
            health.append({"api": api_name, "issue": "API failed", "detail": str(e)[:160]})
            return []

    # Independent RMS calls run concurrently. The previous sequential version
    # multiplied every upstream timeout and made a two-trainer refresh take
    # more than two minutes.
    # RMS becomes unstable under high fan-out; five workers per trainer keeps
    # latency bounded without sacrificing source completeness.
    with ThreadPoolExecutor(max_workers=5) as pool:
        futures = {}

        def submit(name, enabled, fn):
            futures[name] = pool.submit(safe, name, fn) if enabled else None

        submit("Trainer Details", bool(email), lambda: _call("trainerDetails", {"email": email}))
        submit("Trainer Skills", bool(emp), lambda: _call("trainerSkills", {"employee_id": emp}))
        submit("Utilization", bool(email), lambda: _call("utilization", {"email": email}))
        submit("Vendor Cert Count", bool(email), lambda: _call("vendorCerts", {"email": email}))
        submit("Trainer Resume Details", bool(email), lambda: _call("resumeDetails", {"email": email}, timeout=5))
        submit("Unique Certifications", bool(email), lambda: _call("uniqueCertCount", {"email": email}, timeout=5))
        submit("Previous & Upcoming Assignments", bool(email), lambda: _call("prevUpcoming", {"Startdate": d1, "Enddate": d2, "Email": email}, timeout=5))
        submit("Upcoming Assignments", bool(emp) and is_configured("upcomingAssignments"), lambda: _call("upcomingAssignments", {"fromDate": today.isoformat(), "todate": d2, "trainerID": str(trainer.get("TrainerId") or ""), "empCode": emp}, timeout=5))
        submit("Trainer RC Schedule", bool(email), lambda: _call("trainerRCSchedule", {"traineremail": email, "fromDate": d1, "toDate": d2}, timeout=5))
        submit("Trainer Last 3 Months", bool(emp), lambda: _call("trainerLast3", {"EmpCode": emp}, timeout=5))
        submit("Negative Feedback", bool(email), lambda: _call("negativeFeedback", {"email": email}, timeout=5))
        submit("Compliance Signal", bool(email), lambda: _call("hrIncidents", {"email": email}, timeout=5))
        submit("Assignment API", bool(email), lambda: _call("assignments", {"TrainerEmailAddres": email, "PageNumber": "1", "PageSize": "100"}, timeout=8))

        details = futures["Trainer Details"].result() if futures["Trainer Details"] else []

        skills = futures["Trainer Skills"].result() if futures["Trainer Skills"] else []
        util = futures["Utilization"].result() if futures["Utilization"] else []
        certs = futures["Vendor Cert Count"].result() if futures["Vendor Cert Count"] else []
        resume = futures["Trainer Resume Details"].result() if futures["Trainer Resume Details"] else []
        unique_c = futures["Unique Certifications"].result() if futures["Unique Certifications"] else []
        prev_up = futures["Previous & Upcoming Assignments"].result() if futures["Previous & Upcoming Assignments"] else []
        upcoming = futures["Upcoming Assignments"].result() if futures["Upcoming Assignments"] else []
        rc_sched = futures["Trainer RC Schedule"].result() if futures["Trainer RC Schedule"] else []
        last3 = futures["Trainer Last 3 Months"].result() if futures["Trainer Last 3 Months"] else []
        negfb = futures["Negative Feedback"].result() if futures["Negative Feedback"] else []
        hr = futures["Compliance Signal"].result() if futures["Compliance Signal"] else []
        assigns = futures["Assignment API"].result() if futures["Assignment API"] else []

        # Calendar evidence is operationally critical. RMS occasionally times
        # out this source during the initial fan-out, while succeeding moments
        # later after the other calls have drained. Retry once in the quieter
        # second pass instead of publishing an empty delivery calendar.
        if email and not prev_up:
            prev_up = safe("Previous & Upcoming Assignments", lambda: _call(
                "prevUpcoming", {"Startdate": d1, "Enddate": d2, "Email": email}, timeout=15
            ))

        # Retrieve feedback for the most recent distinct assignments rather
        # than silently treating the first assignment as the whole history.
        assignment_ids = []
        for assignment in assigns or []:
            if not isinstance(assignment, dict):
                continue
            assignment_id = str(assignment.get("AssignmentID") or assignment.get("AssignmentId") or "").strip()
            if assignment_id and assignment_id not in assignment_ids:
                assignment_ids.append(assignment_id)
        feedback_futures = [
            pool.submit(safe, "Trainer Feedback Details", lambda aid=aid: _call("trainerFeedback", {"TrainerEmail": email, "AssignmentId": aid, "SCID": ""}, timeout=5))
            for aid in assignment_ids[:5]
        ]
        fb_det = [row for future in feedback_futures for row in (future.result() or []) if isinstance(row, dict)]

    if not email:
        health.append({"api": "Reportee", "issue": "Missing email", "detail": "trainer has no OffEmail"})
    if not emp:
        health.append({"api": "Reportee", "issue": "Missing employee ID", "detail": "trainer has no EmpId"})

    d_rows = [norm_detail(x) for x in details if isinstance(x, dict)]
    s_rows = [norm_skill(x) for x in skills if isinstance(x, dict)]
    hr_v = norm_hr([x for x in hr if isinstance(x, dict)])
    negfb_v = norm_negfb([x for x in negfb if isinstance(x, dict)])
    util_obj = parse_utilization(util)
    last3_obj = norm_last3_util(last3)
    if util and util_obj is None:
        health.append({"api": "Utilization", "issue": "Unknown utilization parse", "detail": "no monthly columns found"})
    cert_obj = parse_certs(certs)
    resume_obj = norm_resume_details(resume)
    resume_certs = resume_obj["resume_certifications"]
    cert_source = "Both" if resume_certs and cert_obj["vendors"] else "Resume Details API" if resume_certs else "Vendor Certification Count API" if cert_obj["vendors"] else "Not Available"
    unique_cert_total = norm_unique_cert_count(unique_c)
    rc_obj = norm_trainer_availability(rc_sched)
    prev_obj = norm_prev_upcoming(prev_up)
    fb_rows = norm_feedback_details(fb_det)
    if resume and not resume_certs:
        health.append({"api": "Trainer Resume Details", "issue": "Missing resume certifications", "detail": "resume API returned no Certifications"})
    if not resume:
        health.append({"api": "Trainer Resume Details", "issue": "Resume API unavailable", "detail": "no response returned for resume details"})
    elif resume and not isinstance(resume, (list, dict)):
        health.append({"api": "Trainer Resume Details", "issue": "Resume API failed", "detail": "unexpected resume response shape"})
    if not rc_obj["rows"]:
        health.append({"api": "Trainer RC Schedule", "issue": "RC schedule unavailable", "detail": "no RC rows returned"})
    if not prev_obj["rows"]:
        health.append({"api": "Previous & Upcoming Assignments", "issue": "No assignment rows", "detail": "could not derive calendar load"})
    if not fb_rows and not negfb_v:
        health.append({"api": "Trainer Feedback Details", "issue": "Feedback details unavailable", "detail": "no feedback evidence returned"})

    q_scores = [r["qubits_score"] for r in d_rows if r["qubits_score"] > 0]
    return {
        "trainer": trainer,
        "email": email,
        "emp": emp,
        "details": d_rows,
        "skills": s_rows,
        "resume": resume_obj,
        "unique_cert_count": unique_cert_total["count"],
        # Course-specific availability must only be evaluated for a concrete
        # Sales demand (course + requested dates), never during generic 360 refresh.
        "availability": {"rows": [], "evaluation_scope": "demand_only"},
        "rc_schedule": rc_obj,
        "free_schedule": {"rows": [], "evaluation_scope": "demand_only"},
        "prev_upcoming": prev_obj,
        "upcoming_assignments": {"rows": _rows(upcoming)},
        "last3_util": last3_obj,
        "feedback_details": fb_rows,
        "assignments": [a for a in assigns if isinstance(a, dict)],
        "cert_count": cert_obj["count"],
        "cert_vendors": cert_obj["vendors"],
        "resume_certs": resume_certs,
        "cert_source": cert_source,
        "negfb_total": negfb_v,
        "hr": hr_v,
        "util": util_obj,
        "util_pct": (util_obj["current"] if util_obj else None),
        "util_trend": (util_obj["trend"] if util_obj else None),
        "util_avg3m": (util_obj["avg_3m"] if util_obj else None),
        "avg_qubit": round(sum(q_scores) / len(q_scores), 1) if q_scores else None,
        "readiness_bucket": None,
        "growth_bucket": None,
        "health": health,
    }


def safe_fetch(trainer, fetch_fn):
    """Isolate fetch+normalize failures so one bad trainer never crashes the whole map."""
    try:
        return fetch_fn(trainer)
    except Exception as exc:  # noqa: BLE001
        from shared.safety import safe_error as _safe_error

        return {
            "__error__": _safe_error(exc),
            "__stage__": "fetch/normalize",
            "trainer": trainer,
            "email": trainer.get("OffEmail") or "",
            "emp": str(trainer.get("EmpId") or trainer.get("TrainerId") or ""),
            "health": [],
        }
