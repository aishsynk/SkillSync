"""
SkillEdge Delivery Intelligence — server-side pipeline.

Reportee-first flow:
    Reportee API (scope)  ->  per-trainer child APIs (scoped only)  ->  global Course List once
    -> normalize -> build 6 datasets -> explainable scoring -> unified JSON

Built ONCE per manager and cached to disk by server.py, so page navigation never
re-hits the RMS APIs. Only the 9 verified APIs are used. Columns that require the
still-unverified new APIs are emitted as `None` and surfaced in data_health.

Secrets live here (server side) — never shipped to the browser.
"""
import json
import urllib.request
import urllib.error
import urllib.parse
import time
from datetime import date
from concurrent.futures import ThreadPoolExecutor
from shared.constants import API_BASE, TOKEN_ENDPOINT, DATA_ENDPOINT, DEFAULT_TIMEOUT, DATASET_NAMES
from shared.safety import clean_name as _clean_name, safe_error as _safe_error, safe_truthy as _truthy
from api.client import _call
from shared.normalizers import (
    norm_detail,
    norm_skill,
    norm_hr,
    norm_negfb,
    parse_utilization,
    parse_certs,
    parse_resume_certifications,
    norm_resume_details,
    norm_trainer_availability,
    norm_prev_upcoming,
    norm_feedback_details,
    norm_last3_util,
    norm_course_without_exam,
    norm_exam_course_linked,
    norm_unique_cert_count,
    normalize_health,
    _rows,
    _safe_text,
    _parse_dt,
    _rows_text,
)
from shared.explainability import (
    health_dataset_for as _health_dataset_for,
    health_page_for as _health_page_for,
    health_impact_for as _health_impact_for,
    health_fix_for as _health_fix_for,
    health_scrub as _health_scrub,
)
from shared.scoring import score_trainer as _score_trainer, recommend as _recommend
from shared.growth_intelligence import build_trainer_growth, build_oem_heatmap
from shared.certification_intelligence import (
    build_certification_block,
    build_certification_recommendations,
    build_certification_summary,
)
from shared.delivery_intelligence import build_delivery_intelligence
from shared.allocation_intelligence import build_allocation_intelligence
from shared.organization_intelligence import build_organization_intelligence
from shared.executive_intelligence import build_executive_intelligence
from shared.manager_recommendation_intelligence import (
    build_feedback_coaching,
    build_future_skill_roadmap,
    build_future_certification_roadmap,
)
from services.trainer_fetch_service import fetch_trainer as _fetch_trainer, safe_fetch as _safe_fetch
from services.decision_objects import build_trainer_decision_objects
from services.allocation_decision_service import build_allocation_decision_objects
from services.manager_action_service import build_manager_action_objects
from services.current_state_service import build_batch_engagement_rows, build_trainer_current_states
from services.reference_data_service import build_course_master, build_unallocated_demand
from services.feedback_intelligence_service import build_feedback_facts, build_feedback_summaries
from api.config import is_configured
from shared.classification import build_trainer_classification as _build_trainer_classification


def _clean_email_key(value):
    return str(value or "").strip().lower()


# ── Scoring (explainable) ───────────────────────────────────────────────────
# Scoring moved to shared.scoring


def _upgrade_availability_engine(engine_rows):
    """Post-process availability engine rows to fill derived fields.

    This logic was originally in server.py as _upgrade_payload.
    Moved here so all business intelligence lives in the pipeline.
    """
    upgraded = []
    for row in engine_rows:
        row = dict(row)
        row["trainer_name"] = _clean_name(row.get("trainer_name"))
        calendar = row.get("calendar_status") or "Unknown"
        capacity = row.get("capacity_status") or "Unknown"
        workload = row.get("workload_status")
        if not workload:
            upcoming = row.get("upcoming_assignment_count", 0)
            previous = row.get("previous_assignment_count", 0)
            if upcoming >= 8 or previous >= 12:
                workload = "Heavy"
            elif upcoming >= 3 or previous >= 5:
                workload = "Moderate"
            elif upcoming > 0 or previous > 0:
                workload = "Light"
            else:
                workload = "Unknown"
        # Don't assert "Busy" without real assignment evidence
        if calendar == "Busy" and not row.get("upcoming_assignment_count") and not row.get("previous_assignment_count"):
            calendar = "Unknown"
        row["calendar_status"] = calendar
        row["capacity_status"] = capacity
        row["workload_status"] = workload
        row["availability_reason"] = row.get("availability_reason") or (
            "Calendar APIs timed out or were unavailable, so busy is not asserted from capacity alone"
            if calendar == "Unknown" else
            "Real upcoming assignment evidence supports a busy working profile"
            if calendar == "Busy" else
            "Known schedule evidence suggests the trainer can be considered for delivery"
            if calendar == "Available" else
            "Insufficient schedule evidence"
        )
        row["availability_confidence"] = row.get("availability_confidence") or 50
        row["confidence_reason"] = row.get("confidence_reason") or (
            "Confidence derived from whatever availability evidence is present"
        )
        row["evidence_used"] = row.get("evidence_used") or []
        row["evidence_missing"] = row.get("evidence_missing") or []
        # Contradiction handling: never present a confident verdict when the
        # capacity signal disagrees with the calendar signal. Confidence is
        # capped and the conflict is surfaced so the manager sees uncertainty,
        # not a false "conf 100" claim.
        contradictions = []
        if calendar == "Busy" and capacity in ("Underused", "Trend Down"):
            contradictions.append("capacity signal suggests underuse while the calendar is busy")
        if calendar == "Available" and capacity in ("Overloaded", "Trend Up"):
            contradictions.append("capacity signal suggests load while the calendar is free")
        if calendar == "Unknown" and capacity in ("Underused", "Overloaded"):
            contradictions.append("capacity signal present but no calendar evidence")
        if contradictions:
            row["availability_confidence"] = min(int(row.get("availability_confidence") or 100), 60)
            row["confidence_reason"] = "Reduced because of conflicting signals: " + "; ".join(contradictions)
            row["contradictions"] = contradictions
            row["availability_reason"] = (row.get("availability_reason") or "Insufficient schedule evidence") + " Signals conflict; treat status as a low-confidence estimate."
        else:
            row["contradictions"] = []
        if calendar == "Unknown" and row.get("final_availability_status") == "Busy but Strong Candidate":
            row["final_availability_status"] = "Available but Needs Prep"
        upgraded.append(row)
    return upgraded


def _failed_trainer_health(trainer, email, stage, etype, emsg):
    """Standard data_health row for a trainer that could not be fully processed."""
    return {
        "trainer_key": email or trainer.get("EmpId") or trainer.get("TrainerName"),
        "trainer_name": _clean_name(trainer.get("TrainerName")),
        "email": email,
        "api_name": "Delivery Intelligence Engine",
        "issue_type": "Trainer processing failed",
        "issue_detail": "{}: {}".format(etype, emsg),
        "failed_stage": stage,
        "error_type": etype,
        "business_impact": "This trainer is excluded from readiness, allocation, and availability results for this refresh; other trainers are unaffected.",
        "recommended_fix": "Re-run refresh; if the error persists, inspect this trainer's API data shape for the failed stage.",
        "severity": "High",
    }


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


def normalize_health(rows):
    """Coerce every data_health row into ONE standard schema. No null issue_type,
    no blank issue_detail, no repeated/OK rows, secrets redacted."""
    from datetime import datetime
    ts = datetime.now().isoformat(timespec="seconds")
    out, seen = [], set()
    for r in rows:
        if not isinstance(r, dict):
            continue
        api_name = r.get("api_name") or r.get("api") or "Unknown source"
        issue_type = r.get("issue_type") or r.get("issue") or "Unknown issue"
        if str(issue_type).strip().lower() in ("ok", "resolved", "all signals resolved", "none", ""):
            continue  # never emit OK rows
        issue_detail = _health_scrub(r.get("issue_detail") or r.get("detail") or issue_type, issue_type)
        email = r.get("email") or ""
        tkey = r.get("trainer_key") or email or ""
        if not email and "@" in str(tkey):
            email = tkey
        norm = {
            "trainer_key": tkey,
            "trainer_name": _clean_name(r.get("trainer_name")),
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
            continue  # no repeated rows
        seen.add(dedup)
        out.append(norm)
    return out


# ── Dataset assembly ────────────────────────────────────────────────────────
def build_unified(manager_email):
    reportees = _call("reportees", {"email": manager_email})
    if not isinstance(reportees, list) or not reportees:
        return {"manager_email": manager_email, "trainer_count": 0, "trainers": [],
                "actions": [], "timeline": [], "data_health": [], "courses": [], "summary": {},
                "error": "No reportees found — email is not a manager or account lacks hierarchy access."}

    global_health = []

    def global_call(api_name, body=None, timeout=12):
        if not is_configured(api_name):
            return []
        try:
            return _call(api_name, body or {}, timeout=timeout)
        except Exception as exc:  # noqa: BLE001
            global_health.append({"api": api_name, "issue": "API failed", "detail": str(exc)[:160]})
            return []

    # Reference and demand sources are independent and are bounded by the
    # process-wide RMS request limit in api.client.
    with ThreadPoolExecutor(max_workers=4) as global_pool:
        global_futures = {
            "courses": global_pool.submit(global_call, "courseList"),
            "details": global_pool.submit(global_call, "courseNames"),
            "technology": global_pool.submit(global_call, "courseTechnology"),
            "domain": global_pool.submit(global_call, "courseDomain", {"TechName": ""}),
            "demand": global_pool.submit(global_call, "unallocatedAssignments"),
            "exam_policy": global_pool.submit(global_call, "courseWithoutExam"),
            "exam_links": global_pool.submit(global_call, "examCourseLinked", {"courseid": "", "examid": "", "iswithoutexam": ""}),
        }
        courses = global_futures["courses"].result()
        course_detail_rows = global_futures["details"].result()
        course_technology_rows = global_futures["technology"].result()
        course_domain_rows = global_futures["domain"].result()
        unallocated_rows = global_futures["demand"].result()
        course_without_exam = norm_course_without_exam(global_futures["exam_policy"].result())
        exam_course_linked = norm_exam_course_linked(global_futures["exam_links"].result())

    # Pre-warm every token sequentially. Generating a fresh token invalidates the
    # account's previous device token, so concurrent first-time fetches race and
    # cause "Forbidden: Permission denied". Warm once up front, then fan out.
    with ThreadPoolExecutor(max_workers=6) as ex:
        fetched = list(ex.map(lambda tr: _safe_fetch(tr, _fetch_trainer), reportees))

    batch_engagement_rows = build_batch_engagement_rows(fetched)
    trainer_current_states = build_trainer_current_states(fetched, batch_engagement_rows)
    current_state_by_key = {
        _clean_email_key(row.get("trainer_key") or row.get("trainer_email")): row
        for row in trainer_current_states
        if row.get("trainer_key") or row.get("trainer_email")
    }

    trainers, actions, timeline, health = [], [], [], list(global_health)
    course_allocation = []
    engine_rows = []
    vendor_strength_rows = []
    growth_rows = []
    certification_rows = []
    certification_gap_rows = []
    course_url = {str(c.get("Courseid")): c.get("course_url") for c in courses if isinstance(c, dict)}
    course_vendor = {str(c.get("Courseid")): c.get("vendor_name") for c in courses if isinstance(c, dict)}
    course_without_exam_by_name = course_without_exam.get("by_name", {})
    course_master_rows = build_course_master(
        courses,
        detail_rows=course_detail_rows,
        technology_rows=course_technology_rows,
        domain_rows=course_domain_rows,
        exam_policy_rows=course_without_exam.get("rows", []),
    )
    unallocated_demand_rows = build_unallocated_demand(unallocated_rows)

    for f in fetched:
        tr = f.get("trainer") or {}
        email = f.get("email") or ""
        key = _clean_email_key(email) or f.get("emp") or tr.get("TrainerName")
        cs_row = current_state_by_key.get(key) or {}

        # Surface per-API failures captured during fetch (even if this trainer later fails).
        for h in f.get("health", []):
            health.append({"trainer_key": key, "trainer_name": _clean_name(tr.get("TrainerName")),
                           "email": email, "api_name": h["api"], "issue_type": h["issue"],
                           "issue_detail": h["detail"], "severity": "Medium"})

        # Fetch/normalize already failed for this trainer -> record and skip cleanly.
        if f.get("__error__"):
            _et, _em = f["__error__"]
            health.append(_failed_trainer_health(tr, email, f.get("__stage__", "fetch/normalize"), _et, _em))
            continue

        try:
            sc = _score_trainer(f)
            action, reason, prio = _recommend(f, sc)

            t_row = {
                "trainer_key": key,
                "trainer_name": _clean_name(tr.get("TrainerName")),
                "trainer_id": tr.get("TrainerId"),
                "employee_id": f["emp"],
                "official_email": f["email"],
                "designation": tr.get("Designation") or "",
                "direct_or_indirect": "Direct" if _truthy(tr.get("IsdirectReportee")) else "Indirect",
                "trainer_plus_status": tr.get("TrainerPlus"),
                "resume_summary": f["resume"]["resume_summary"],
                "resume_experience": f["resume"]["resume_experience"],
                "resume_languages": f["resume"]["resume_languages"],
                "resume_certifications": f["resume"]["resume_certifications"],
                "resume_certification_count": len(f["resume"]["resume_certifications"]),
                "resume_skills": f["resume"]["resume_skills"],
                "trainings_delivered_for": f["resume"]["trainings_delivered_for"],
                "resume_feedback": f["resume"]["resume_feedback"],
                "trainer_interests": f["resume"]["trainer_interests"],
                "trainer_image": f["resume"]["trainer_image"] if str(f["resume"]["trainer_image"]).startswith("http") else None,
                # capability
                "skills_count": len([s for s in f["skills"] if not s["is_discontinue"]]),
                "mapped_courses_count": len(f["details"]),
                "duplicate_course_count": len([s for s in f["skills"] if s["is_duplicate"]]),
                "discontinued_course_count": len([s for s in f["skills"] if s["is_discontinue"]]),
                "current_courses": sorted({r["course_name"] for r in f["details"] if r["course_name"]}),
                "domain_strengths": None,        # BLOCKED: Course & Domain API
                "technology_strengths": None,    # BLOCKED: Course & Technology API
                # certification
                "vendor_certification_count": f["cert_count"],
                "unique_certification_count": len(f["cert_vendors"]),
                "certified_vendors": f["cert_vendors"],
                "certification_detail_available_flag": bool(f["cert_vendors"]),
                "certification_source": f["cert_source"],
                # delivery
                "assignment_count": len(f["assignments"]),
                "current_assignment": (cs_row.get("current_batch") or {}).get("course_name"),
                "last_delivered_course": (f["assignments"][-1]["Course"] if f["assignments"] else None),
                "delivery_vendor_mix": sorted({r["vendor_name"] for r in f["details"] if r["vendor_name"]}),
                "upcoming_assignment_count": int(cs_row.get("upcoming_batch_count") or 0),
                "current_engagement": cs_row or None,
                # capacity
                "current_utilization": f["util_pct"],
                "utilization_status": (None if f["util_pct"] is None else
                                       "Overloaded" if f["util_pct"] >= 85 else
                                       "Healthy" if f["util_pct"] >= 40 else "Underutilized"),
                "last_3_month_utilization": f["util_avg3m"],
                "utilization_trend": f["util_trend"],
                "availability_status": (None if f["util_pct"] is None else
                                        "Available" if f["util_pct"] < 40 else
                                        "Limited" if f["util_pct"] < 85 else "Booked"),
                # quality
                "negative_feedback_count": f["negfb_total"],
                "feedback_detail_count": len(f.get("feedback_details") or []),
                "hr_positive_count": f["hr"]["positive"],
                "hr_negative_count": f["hr"]["negative"],
                "hr_risk": "High" if f["hr"]["negative"] > 0 else "Low",
                "feedback_risk": "High" if f["negfb_total"] > 2 else "Medium" if f["negfb_total"] > 0 else "Low",
                # readiness + action
                "avg_qubits_score": f["avg_qubit"],
                "qubits_score": f["avg_qubit"],
                "overall_readiness_score": sc["readiness"],
                "readiness_bucket": sc["readiness_bucket"],
                "confidence": sc["confidence"],
                "risk_taker_score": sc["risk_taker_score"],
                "growth_bucket": sc["growth_bucket"],
                "missing_signals": sc["missing_signals"],
                "evidence": sc["evidence"],
                "recommended_action": action,
                "action_reason": reason,
                "action_priority": prio,
            }

            a_row = {
                "trainer_key": key, "trainer_name": tr.get("TrainerName") or "",
                "action_type": action, "action_reason": reason, "reason": reason,
                "priority": prio,
                "confidence": sc["confidence"], "readiness": sc["readiness"],
                "recommended_action": action,
                "evidence": sc.get("evidence") or {
                    "action_reason": reason,
                    "confidence": sc["confidence"],
                    "trainer_status": sc["readiness_bucket"],
                },
            }

            t_events = []
            for a in f["assignments"]:
                t_events.append({"trainer_key": key, "trainer_name": tr.get("TrainerName"),
                                 "event_type": "Delivered / Assigned", "event_title": a.get("Course"),
                                 "related_course": a.get("Course"), "risk_level": "Info"})
            for r in f["details"]:
                if r["is_future_skill"]:
                    t_events.append({"trainer_key": key, "trainer_name": tr.get("TrainerName"),
                                     "event_type": "Future skill identified", "event_title": r["course_name"],
                                     "event_date": r["future_skill_date"], "related_course": r["course_name"],
                                     "risk_level": "Info"})
            if f["negfb_total"] > 0:
                t_events.append({"trainer_key": key, "trainer_name": tr.get("TrainerName"),
                                 "event_type": "Negative feedback", "event_title": f"{f['negfb_total']} flagged",
                                 "risk_level": "High"})
            if f["hr"]["negative"] > 0:
                t_events.append({"trainer_key": key, "trainer_name": tr.get("TrainerName"),
                                 "event_type": "HR negative signal", "event_title": f"{f['hr']['negative']} incident(s)",
                                 "risk_level": "High"})
            if f["resume"]["resume_certifications"]:
                t_events.append({"trainer_key": key, "trainer_name": tr.get("TrainerName"),
                                 "event_type": "Resume certifications loaded", "event_title": f"{len(f['resume']['resume_certifications'])} cert(s)",
                                 "risk_level": "Info"})

            t_alloc = []
            for s in f["skills"]:
                if s["is_discontinue"]:
                    continue
                avail = 100 - (t_row["current_utilization"] or 0) if t_row["current_utilization"] is not None else None
                comps = [(t_row["overall_readiness_score"], 0.5), (min(100, len(f["assignments"]) * 10), 0.2)]
                if avail is not None:
                    comps.append((avail, 0.3))
                wsum = sum(w for _, w in comps)
                alloc_score = round(sum(v * w for v, w in comps) / wsum, 1)
                blocker = ("Feedback risk" if t_row["negative_feedback_count"] > 2
                           else "Low readiness" if t_row["overall_readiness_score"] < 45 else None)
                t_alloc.append({
                    "course_id": s["course_id"], "course_name": s["course_name"],
                    "vendor": course_vendor.get(str(s["course_id"])),
                    "course_url": course_url.get(str(s["course_id"])),
                    "domain": None, "technology": None,
                    "trainer_key": t_row["trainer_key"], "trainer_name": t_row["trainer_name"],
                    "trainer_email": t_row["official_email"],
                    "readiness_score": t_row["overall_readiness_score"],
                    "availability_score": avail,
                    "assignment_experience": len(f["assignments"]),
                    "feedback_risk": t_row["feedback_risk"], "hr_risk": t_row["hr_risk"],
                    "overall_allocation_score": alloc_score,
                    "allocation_status": ("Ready Now" if alloc_score >= 75 and not blocker
                                          else "Needs Prep" if alloc_score >= 50 and not blocker
                                          else "Blocked" if blocker else "Not Recommended"),
                    "why_this_trainer": f"Skilled in {s['course_name']}; readiness {t_row['overall_readiness_score']}",
                    "blocker": blocker, "confidence": t_row["confidence"],
                })

            readiness = sc["readiness"]
            qbit = f["avg_qubit"]
            util_now = f["util_pct"]
            util_avg3 = f["util_avg3m"]
            trend = f["util_trend"] or []
            trend_dir = "Unknown"
            if util_now is not None and util_avg3 is not None:
                trend_dir = "Trend Up" if util_now > util_avg3 else "Trend Down" if util_now < util_avg3 else "Stable"
            elif len(trend) >= 2:
                first, last = trend[0].get("util"), trend[-1].get("util")
                if first is not None and last is not None:
                    trend_dir = "Trend Up" if last > first else "Trend Down" if last < first else "Stable"

            prev_rows = f["prev_upcoming"]["rows"]
            # Strict dated evidence only — an undated row is never "upcoming".
            # The canonical upcoming count comes from the current-state service
            # (which also consumes the Upcoming Assignments API), so every frame
            # agrees on the same number instead of deriving its own.
            upcoming_count = int(cs_row.get("upcoming_batch_count") or 0)
            previous_rows = [r for r in prev_rows if (_parse_dt(r.get("EndDate"))) is not None and _parse_dt(r.get("EndDate")).date() < date.today()]
            delivered_for_text = _rows_text([f["resume"]["resume_raw"].get("TrainingsDeliveredFor")])
            similar_delivery = sum(1 for a in f["assignments"] if _safe_text(a.get("Course")).lower() in delivered_for_text.lower() or _safe_text(a.get("Vendor")).lower() in delivered_for_text.lower())
            custom_batch_experience = "Yes" if any(k in delivered_for_text.lower() for k in ("custom", "client", "corporate")) else "No evidence"
            avail_rows = f["availability"]["rows"]
            rc_rows = f["rc_schedule"]["rows"]
            free_rows = f["free_schedule"]["rows"]
            has_calendar_evidence = bool(avail_rows or rc_rows or free_rows)
            upcoming_signal = upcoming_count > 0
            blocked_flag = any(_safe_text(r.get("MTI_Issue")) for r in avail_rows) or any(_safe_text(r.get("MTI_Issue")) for r in rc_rows)
            calendar_status = (
                "Blocked" if blocked_flag else
                "Busy" if upcoming_signal and has_calendar_evidence else
                "Available" if has_calendar_evidence and not upcoming_signal else
                "Unknown"
            )
            if util_now is None:
                capacity_status = "Unknown"
            elif util_now < 35:
                capacity_status = "Underused"
            elif util_now < 70:
                capacity_status = "Balanced"
            else:
                capacity_status = "Overloaded"
            if trend_dir in ("Trend Up", "Trend Down"):
                capacity_status = trend_dir
            workload_status = (
                "Heavy" if upcoming_count >= 8 or len(f["assignments"]) >= 12 else
                "Moderate" if upcoming_count >= 3 or len(f["assignments"]) >= 5 else
                "Light" if len(f["assignments"]) > 0 else
                "Unknown"
            )
            delivery_exp = "No evidence"
            if delivered_for_text:
                delivery_exp = "Delivered for similar client" if any(k in delivered_for_text.lower() for k in ("client", "corporate")) else "Has delivered similar course"
            elif similar_delivery > 0:
                delivery_exp = "Has delivered similar course"
            feedback_status = "Strong feedback" if f["negfb_total"] == 0 and f["hr"]["negative"] == 0 else "Risky" if f["negfb_total"] > 2 or f["hr"]["negative"] > 0 else "Review needed"
            if qbit is None and readiness < 50:
                readiness_status = "Hold"
            elif readiness >= 80:
                readiness_status = "Ready Now"
            elif readiness >= 65:
                readiness_status = "Can Deliver with Prep"
            elif readiness >= 45:
                readiness_status = "Needs Mock"
            else:
                readiness_status = "Hold"
            if f["resume_certs"] and f["cert_vendors"]:
                certification_status = "Both"
            elif f["resume_certs"]:
                certification_status = "Certification visible in resume"
            elif f["cert_vendors"]:
                certification_status = "Count only"
            elif any(course_without_exam_by_name.get(c.lower()) for c in t_row["current_courses"]):
                certification_status = "Exam not required"
            else:
                certification_status = "Certification unknown"
            batch_fit = {
                "ILT": "Yes" if readiness_status in ("Ready Now", "Can Deliver with Prep") else "Maybe" if readiness_status == "Needs Mock" else "No",
                "FMAT": "Yes" if readiness_status == "Ready Now" and feedback_status == "Strong feedback" else "Maybe" if readiness_status == "Can Deliver with Prep" else "No",
                "Corporate batch": "Yes" if calendar_status == "Available" and feedback_status != "Risky" else "Maybe" if calendar_status == "Busy" else "No",
                "Custom batch": "Yes" if custom_batch_experience == "Yes" or f["resume_certs"] else "Maybe" if readiness_status != "Hold" else "No",
                "First-time course": "Yes" if readiness_status == "Ready Now" and (qbit or 0) >= 80 else "Maybe" if readiness_status == "Can Deliver with Prep" else "No",
                "Qubit-style advanced delivery": "Yes" if (qbit or 0) >= 85 and readiness_status != "Hold" else "Maybe" if (qbit or 0) >= 70 else "No",
                "Stretch/risk-taker delivery": "Yes" if sc["growth_bucket"] in ("Risk Taker", "Growth Candidate") else "No",
            }
            final_status = (
                "Ready for Live Delivery" if calendar_status == "Available" and readiness_status == "Ready Now" and feedback_status == "Strong feedback" else
                "Available but Needs Prep" if calendar_status in ("Available", "Unknown") and readiness_status in ("Can Deliver with Prep", "Needs Mock") and feedback_status != "Risky" else
                "Available but Risky" if calendar_status in ("Available", "Unknown") and feedback_status in ("Review needed", "Risky") else
                "Busy but Strong Candidate" if calendar_status == "Busy" and readiness_status in ("Ready Now", "Can Deliver with Prep") else
                "Overloaded" if capacity_status == "Overloaded" or workload_status == "Heavy" else
                "Hold / Do Not Allocate" if readiness_status == "Hold" or feedback_status == "Risky" else
                "Data Incomplete" if "Unknown" in [calendar_status, capacity_status, readiness_status, feedback_status, certification_status] else
                "Available but Needs Prep"
            )
            missing = []
            for label, val in [("calendar", calendar_status), ("capacity", capacity_status), ("workload", workload_status), ("delivery", delivery_exp), ("feedback", feedback_status), ("certification", certification_status)]:
                if val in ("Unknown", "No evidence") or "Unknown" in str(val):
                    missing.append(label)
            evidence_used = []
            if avail_rows:
                evidence_used.append("availability")
            if rc_rows:
                evidence_used.append("rc_schedule")
            if free_rows:
                evidence_used.append("free_schedule")
            if upcoming_count or f["assignments"]:
                evidence_used.append("assignments")
            if f["resume_certs"] or f["cert_vendors"]:
                evidence_used.append("certifications")
            if f["negfb_total"] or f["hr"]["negative"] or f["feedback_details"]:
                evidence_used.append("quality")
            evidence_missing = missing[:]
            confidence_reason = (
                "High because schedule, workload, readiness, and quality evidence are present" if not evidence_missing else
                "Reduced because " + ", ".join(evidence_missing) + " signals are incomplete"
            )
            availability_confidence = max(35, 100 - len(evidence_missing) * 15)
            availability_reason = (
                "Real upcoming assignment evidence and readiness signal support a busy working profile" if calendar_status == "Busy" else
                "Schedule APIs timed out or were unavailable, so busy is not asserted from capacity alone" if calendar_status == "Unknown" else
                "Known schedule evidence suggests the trainer can be considered for delivery" if calendar_status == "Available" else
                "Calendar signal is blocked by schedule evidence" if calendar_status == "Blocked" else
                "Insufficient schedule evidence"
            )
            evidence = {
                "availability_rows": len(f["availability"]["rows"]),
                "rc_schedule_rows": len(f["rc_schedule"]["rows"]),
                "free_schedule_rows": len(f["free_schedule"]["rows"]),
                "upcoming_assignment_rows": upcoming_count,
                "previous_assignment_rows": len(previous_rows),
                "similar_delivery_count": similar_delivery,
                "delivery_experience": delivery_exp,
                "resume_certifications": len(f["resume_certs"]),
                "vendor_certifications": len(f["cert_vendors"]),
                "feedback_detail_rows": len(f["feedback_details"]),
            }
            e_row = {
                "trainer_key": key,
                "trainer_name": _clean_name(tr.get("TrainerName")),
                "email": f["email"],
                "calendar_status": calendar_status,
                "capacity_status": capacity_status,
                "workload_status": workload_status,
                "utilization_current": util_now,
                "utilization_3_month_avg": util_avg3,
                "utilization_trend": trend_dir,
                "upcoming_assignment_count": upcoming_count,
                "previous_assignment_count": len(previous_rows) or len(f["assignments"]),
                "similar_delivery_count": similar_delivery,
                "custom_batch_experience": custom_batch_experience,
                "ilt_fmat_suitability": batch_fit,
                "qubit_score": qbit,
                "readiness_status": readiness_status,
                "feedback_status": feedback_status,
                "hr_risk_status": "High" if f["hr"]["negative"] > 0 else "Low",
                "growth_bucket": f.get("growth_bucket"),
                "certification_status": certification_status,
                "batch_type_fit": batch_fit,
                "delivery_confidence": sc["confidence"],
                "availability_confidence": availability_confidence,
                "confidence_reason": confidence_reason,
                "evidence_used": evidence_used,
                "evidence_missing": evidence_missing,
                "availability_reason": availability_reason,
                "final_availability_status": final_status,
                "recommended_use": "Custom batch" if batch_fit["Custom batch"] == "Yes" else "Corporate batch" if batch_fit["Corporate batch"] == "Yes" else "ILT" if batch_fit["ILT"] == "Yes" else "Stretch/risk-taker delivery",
                "recommended_action": "Allocate immediately" if final_status == "Ready for Live Delivery" else "Book mock and assign mentor" if readiness_status in ("Can Deliver with Prep", "Needs Mock") else "Limit to controlled delivery / pilot" if final_status == "Busy but Strong Candidate" else "Hold until evidence improves" if final_status in ("Available but Risky", "Hold / Do Not Allocate") else "Resolve data gaps first",
                "evidence": evidence,
                "missing_signals": missing,
            }
            e_health = []
            if not has_calendar_evidence:
                e_health.append({"api": "Delivery Availability Engine", "issue": "Missing calendar availability", "detail": "no availability, free schedule, or RC schedule rows"})
            if util_now is None and util_avg3 is None:
                e_health.append({"api": "Delivery Availability Engine", "issue": "Missing capacity signal", "detail": "no utilization current or trend"})
            if not f["resume_certs"] and not f["cert_vendors"]:
                e_health.append({"api": "Delivery Availability Engine", "issue": "Missing certification signal", "detail": "no resume or vendor certifications"})

            # ── Capability & Growth Intelligence (additive; live-derived only) ──
            g = build_trainer_growth(f, t_row, course_without_exam)
            t_row["vendor_strengths"] = g["vendor_strengths"]
            t_row["primary_vendor"] = g["primary_vendor"]
            t_row["secondary_vendors"] = g["secondary_vendors"]
            t_row["capability_profile"] = g["capability_profile"]
            t_row["growth_recommendations"] = g["growth_recommendations"]
            t_row["growth_stage"] = g["growth_stage"]           # NOTE: distinct key — does NOT
            t_row["growth_reason"] = g["growth_reason"]         # overwrite existing growth_bucket
            t_row["growth_confidence"] = g["growth_confidence"]
            g_row = {
                "trainer_key": key,
                "trainer_name": t_row["trainer_name"],
                "primary_vendor": g["primary_vendor"],
                "secondary_vendors": g["secondary_vendors"],
                "capability_profile": g["capability_profile"],
                "vendor_strengths": g["vendor_strengths"],
                "growth_recommendations": g["growth_recommendations"],
                "growth_bucket": g["growth_stage"],   # growth-intel stage (no collision in this dataset)
                "growth_stage": g["growth_stage"],
                "growth_reason": g["growth_reason"],
                "growth_confidence": g["growth_confidence"],
                "exam_result_status": g["exam_result_status"],
            }
            vs_rows = [dict(v, trainer_key=key, trainer_name=t_row["trainer_name"])
                       for v in g["vendor_strengths"]]

            # ── Certification Intelligence (additive; live-derived only) ──
            cert_block = build_certification_block(f, course_without_exam)
            cert_recs = build_certification_recommendations(t_row, cert_block)
            t_row["oem_accreditations"] = cert_block["oem_accreditations"]
            t_row["certificate_count"] = cert_block["certificate_count"]
            t_row["resume_certifications"] = cert_block["resume_certifications"]
            t_row["certifiable_courses"] = cert_block["certifiable_courses"]
            t_row["certification_gap_count"] = cert_block["certification_gap_count"]
            t_row["certification_gaps"] = cert_block["certification_gaps"]
            t_row["certification_recommendations"] = cert_recs
            t_row["exam_result_status"] = cert_block["exam_result_status"]
            cert_row = {
                "trainer_key": key,
                "trainer_name": t_row["trainer_name"],
                "trainer_email": t_row.get("official_email"),
                "oem_accreditations": cert_block["oem_accreditations"],
                "certificate_count": cert_block["certificate_count"],
                "resume_certifications": cert_block["resume_certifications"],
                "certifiable_courses": cert_block["certifiable_courses"],
                "certification_gap_count": cert_block["certification_gap_count"],
                "certification_gaps": cert_block["certification_gaps"],
                "certification_recommendations": cert_recs,
                "exam_result_status": cert_block["exam_result_status"],
            }
            cg_rows = [dict(gap, trainer_key=key, trainer_name=t_row["trainer_name"])
                       for gap in cert_block["certification_gaps"]]

            # Atomic commit — only after every stage for this trainer succeeded.
            trainers.append(t_row)
            actions.append(a_row)
            timeline.extend(t_events)
            course_allocation.extend(t_alloc)
            engine_rows.append(e_row)
            health.extend(e_health)
            growth_rows.append(g_row)
            vendor_strength_rows.extend(vs_rows)
            certification_rows.append(cert_row)
            certification_gap_rows.extend(cg_rows)
        except Exception as _e:
            _et, _em = _safe_error(_e)
            health.append(_failed_trainer_health(tr, email, "operations/engine assembly", _et, _em))
            continue

    # Normalize every health row into one standard schema (no null issue_type,
    # no blank detail, no OK/duplicate rows, secrets redacted).
    health = normalize_health(health)

    # KPIs
    ready = [t for t in trainers if t["readiness_bucket"] == "Ready Now"]
    q_all = [t["avg_qubits_score"] for t in trainers if t["avg_qubits_score"] is not None]
    summary = {
        "trainer_count": len(trainers),
        "direct": len([t for t in trainers if t["direct_or_indirect"] == "Direct"]),
        "ready_now": len(ready),
        "needs_coaching": len([t for t in trainers if t["readiness_bucket"] == "Needs Coaching"]),
        "at_risk": len([t for t in trainers if t["hr_negative_count"] > 0 or t["negative_feedback_count"] > 2]),
        "avg_qubit": round(sum(q_all) / len(q_all), 1) if q_all else None,
        "total_certs": sum(t["vendor_certification_count"] for t in trainers),
        "resume_certs": sum(len(t.get("resume_certifications") or []) for t in trainers),
        "total_negfb": sum(t["negative_feedback_count"] for t in trainers),
        "risk_takers": len([t for t in trainers if t["growth_bucket"] == "Risk Taker"]),
        "data_gaps": len(health),
    }

    actions_sorted = sorted(actions, key=lambda a: {"High": 0, "Medium": 1, "Low": 2}.get(a["priority"], 3))
    delivery = build_delivery_intelligence(trainers, course_allocation, engine_rows)
    oem_heatmap = build_oem_heatmap(growth_rows)
    allocation = build_allocation_intelligence(
        trainers,
        course_allocation,
        engine_rows,
        delivery["delivery_intelligence_df"],
        certification_rows,
        vendor_strength_rows,
    )
    feedback_coaching = build_feedback_coaching(trainers, delivery["delivery_intelligence_df"])
    organization = build_organization_intelligence(
        trainers,
        course_allocation,
        allocation["allocation_intelligence_df"],
        delivery["delivery_intelligence_df"],
        vendor_strength_rows,
        certification_rows,
        oem_heatmap,
        trainer_count=len(trainers),
    )
    certification_summary = build_certification_summary(certification_rows)
    executive = build_executive_intelligence({
        "trainer_operations_df": trainers,
        "vendor_strength_df": vendor_strength_rows,
        "growth_intelligence_df": growth_rows,
        "certification_intelligence_df": certification_rows,
        "certification_gap_df": certification_gap_rows,
        "certification_summary_df": certification_summary,
        "delivery_intelligence_df": delivery["delivery_intelligence_df"],
        "allocation_intelligence_df": allocation["allocation_intelligence_df"],
        "course_best_trainer_df": allocation["course_best_trainer_df"],
        "allocation_risk_df": allocation["allocation_risk_df"],
        "organization_intelligence_df": organization["organization_intelligence_df"],
        "succession_risk_df": organization["succession_risk_df"],
        "single_point_failure_df": organization["single_point_failure_df"],
        "replacement_recommendation_df": organization["replacement_recommendation_df"],
        "oem_bench_risk_df": organization["oem_bench_risk_df"],
        "oem_capability_heatmap_df": oem_heatmap,
        "data_health_df": health,
    })
    future_skill_roadmap = build_future_skill_roadmap(
        trainers,
        growth_rows,
        vendor_strength_rows,
        executive["executive_investment_recommendation_df"],
    )
    future_certification_roadmap = build_future_certification_roadmap(
        certification_rows,
        certification_summary,
    )
    trainer_feedback_facts = build_feedback_facts(fetched)
    trainer_feedback_summaries = build_feedback_summaries(trainer_feedback_facts, trainers)

    # ── Upgrade availability engine rows (business logic formerly in server.py) ──
    engine_rows = _upgrade_availability_engine(engine_rows)

    allocation_decision_objects = build_allocation_decision_objects(
        allocation["allocation_ranked_trainer_df"],
        allocation_intelligence_rows=allocation["allocation_intelligence_df"],
        course_best_rows=allocation["course_best_trainer_df"],
        allocation_risk_rows=allocation["allocation_risk_df"],
        course_allocation_rows=course_allocation,
    )
    manager_action_objects = build_manager_action_objects(actions_sorted)
    trainer_decision_objects = build_trainer_decision_objects(
        trainers,
        engine_rows,
        delivery["delivery_intelligence_df"],
        certification_rows,
        allocation_rows=allocation_decision_objects,
        manager_action_rows=manager_action_objects,
        feedback_rows=feedback_coaching,
        backup_rows=delivery["trainer_backup_df"],
        replacement_rows=organization["replacement_recommendation_df"],
        spof_rows=organization["single_point_failure_df"],
    )

    # ── Trainer classification (additive; one resolved verdict per trainer) ──
    # Reuses the decision object already computed above instead of re-deriving
    # readiness/blocker logic a second time. Every page should read
    # trainer.classification instead of recomputing its own ready/blocked/
    # review-required rules from raw fields.
    decision_by_email = {
        _clean_email_key(d.get("trainer_email")): d
        for d in trainer_decision_objects
        if isinstance(d, dict) and d.get("trainer_email")
    }
    for t_row in trainers:
        email_key = _clean_email_key(t_row.get("official_email"))
        t_row["classification"] = _build_trainer_classification(t_row, decision_by_email.get(email_key))

    payload = {
        "manager_email": manager_email,
        "trainer_count": len(trainers),
        "summary": summary,
        "courses": [{"course": c.get("Course"), "course_id": c.get("Courseid"),
                     "vendor": c.get("vendor_name"), "url": c.get("course_url")}
                    for c in courses if isinstance(c, dict)],
        # ── The 6 canonical datasets ──
        "trainer_operations_df": trainers,
        "course_allocation_df":  course_allocation,
        "trainer_timeline_df":   timeline,
        "manager_action_df":     actions_sorted,
        "trainer_availability_engine_df": engine_rows,
        "batch_engagement_df": batch_engagement_rows,
        "trainer_current_state_df": trainer_current_states,
        "course_master_df": course_master_rows,
        "unallocated_demand_df": unallocated_demand_rows,
        "trainer_feedback_fact_df": trainer_feedback_facts,
        "trainer_feedback_summary_df": trainer_feedback_summaries,
        "custom_course_match_df": [],   # backend structure ready; populated on course upload (Phase 4)
        "data_health_df":        health,
        "trainer_decision_objects": trainer_decision_objects,
        "allocation_decision_objects": allocation_decision_objects,
        "custom_course_match_objects": [],
        "manager_action_objects": manager_action_objects,
        # ── Phase A: Capability & Growth Intelligence (additive, live-derived) ──
        "vendor_strength_df":        vendor_strength_rows,
        "growth_intelligence_df":    growth_rows,
        "oem_capability_heatmap_df": oem_heatmap,
        # ── Phase B: Certification Intelligence (additive, live-derived) ──
        "certification_intelligence_df": certification_rows,
        "certification_gap_df":          certification_gap_rows,
        "certification_summary_df":      certification_summary,
        "delivery_intelligence_df":      delivery["delivery_intelligence_df"],
        "course_delivery_risk_df":       delivery["course_delivery_risk_df"],
        "trainer_backup_df":             delivery["trainer_backup_df"],
        "allocation_intelligence_df":    allocation["allocation_intelligence_df"],
        "course_best_trainer_df":        allocation["course_best_trainer_df"],
        "allocation_risk_df":            allocation["allocation_risk_df"],
        "allocation_ranked_trainer_df":  allocation["allocation_ranked_trainer_df"],
        "feedback_coaching_df":          feedback_coaching,
        "future_skill_roadmap_df":       future_skill_roadmap,
        "future_certification_roadmap_df": future_certification_roadmap,
        "organization_intelligence_df":  organization["organization_intelligence_df"],
        "succession_risk_df":            organization["succession_risk_df"],
        "single_point_failure_df":       organization["single_point_failure_df"],
        "replacement_recommendation_df": organization["replacement_recommendation_df"],
        "oem_bench_risk_df":             organization["oem_bench_risk_df"],
        "executive_summary_df":          executive["executive_summary_df"],
        "executive_oem_health_df":       executive["executive_oem_health_df"],
        "executive_risk_register_df":    executive["executive_risk_register_df"],
        "executive_investment_recommendation_df": executive["executive_investment_recommendation_df"],
        "strategic_trainer_df":          executive["strategic_trainer_df"],
        # ── Friendly aliases (front-end convenience) ──
        "trainers": trainers,
        "actions":  actions_sorted,
        "timeline": timeline,
        "trainer_availability_engine": engine_rows,
        "data_health": health,
    }
    return payload

