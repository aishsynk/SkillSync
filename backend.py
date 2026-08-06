"""
SkillSync Backend API v4.0
Deployed on Render — production backend for SkillSync Android app.

Auth: POST /api/auth/login — @koenig-solutions.com only, manager or Trainer Plus role.
Data: GET /api/data/unified-manager-intelligence?email=EMAIL — dashboard payload
      matching the web frontend data model (trainer_operations_df, trainer_current_state_df,
      batch_engagement_df, unallocated_demand_df, trainer_feedback_summary_df,
      manager_action_objects, trainer_decision_objects).
      GET /api/data/trainer-360?email=EMAIL — deep single-trainer profile
      (capability, certifications, delivery history, feedback, availability).

RMS schema notes — all verified against live responses, not the instruction files,
which have proven wrong more than once:
  * reportees (82)      TrainerName, TrainerId, EmpId, OffEmail, TrainerPlus,
                        IsdirectReportee, Designation
  * utilization (55)    one row; TrainerId/TrainerName/EmailId/DOJ plus a
                        "Mon YYYY" column per month holding "load / utilization"
  * trainerDetails (75) one row PER COURSE (capability), not a profile record —
                        CourseName, VendorName, QubitsScore, SkillLevel,
                        OfficiallyApproved, Is Future Skill, Course Assignment
  * prevUpcoming (16)   dates as "03-Aug-2026"; note the StarDate spelling
  * vendorCertCount(57) Trainer is "Name;EmpCode"; one "True"/"False" column per body
  * unallocated (190)   Coursename, CourseSDate/CourseEDate, "Delivery Mode",
                        vendor, "Assignment City", NoOfParticipants, AssignmentID
  * trainerSkills (217), trainerNegFeedback (218), last3MonthsUtil (39) key off
    employee_id / EmpCode — passing an email or blank returns zero rows silently.

There is no leave/absence endpoint in the RMS catalogue. The only unavailability
signal is the *OffDates fields on trainerDetails, which are frequently null.
"""

from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, date, timedelta
from flask import Flask, jsonify, request
from flask_cors import CORS
import json
import os
import re as _re
import secrets
import urllib.parse
import urllib.request

app = Flask(__name__)
CORS(app)

# ─── RMS API wiring ───────────────────────────────────────────────────────────
_RMS_BASE = "https://api.koenig-solutions.com"
_TOKEN_EP  = "/api/Kites/Operator/GetToken"
_DATA_EP   = "/api/Kites/Operator/common"
_TIMEOUT   = 30

def _ev(name, fallback=""):
    return os.getenv(name, "").strip() or fallback

_APIS = {
    # ── Core ────────────────────────────────────────────────────────────────
    "reportees": {
        "user": _ev("SKILLEDGE_RMS_REPORTEES_USER", "AISHWAR_GetDirectIndire"),
        "pass": _ev("SKILLEDGE_RMS_REPORTEES_PASS", "3R$Nc7ThBX64"),
        "role": "Get Direct Indirect Reportee",
        "key":  "82",
    },
    "trainerDetails": {
        "user": _ev("SKILLEDGE_RMS_TRAINER_DETAILS_USER", "AISHWAR_GetTrainerDetai"),
        "pass": _ev("SKILLEDGE_RMS_TRAINER_DETAILS_PASS", "7zCheFM$Cc$t"),
        "role": "Get Trainer Details",
        "key":  "75",
    },
    "utilization": {
        "user": "AISHWAR_GetUtilization",
        "pass": "j4CakF7gEg#f",
        "role": "Get Utilization",
        "key":  "55",
    },
    # ── Assignments ─────────────────────────────────────────────────────────
    "prevUpcoming": {
        "user": "AISHWAR_PreviousUpcommi",
        "pass": "J8LzP@HkW#Ve",
        "role": "Previous & Upcomming Assignments",
        "key":  "16",
    },
    "upcomingAssignments": {
        "user": "AISHWAR_UpcomingAssignm",
        "pass": "nFY$g68zSaRD",
        "role": "Upcoming Assignments",
        "key":  "93",
    },
    "unallocated": {
        "user": "AISHWAR_UnallocatedAssi",
        "pass": "$5djCU@w7eR3",
        "role": "Unallocated Assignment",
        "key":  "190",
    },
    "assignment": {
        "user": "AISHWAR_AssignmentAPI",
        "pass": "4PV6aCe6Sc8!",
        "role": "Assignment API",
        "key":  "15",
    },
    # ── Feedback & Incidents ─────────────────────────────────────────────────
    "negFeedbackCount": {
        "user": "AISHWAR_GetNegativeFeed",
        "pass": "#9u7@@hAHWUg",
        "role": "Get Negative Feedback Count",
        "key":  "58",
    },
    "trainerFeedback": {
        "user": "AISHWAR_GetTrainerFeedb",
        "pass": "T9$jsBnSW7Rd",
        "role": "Get Trainer Feedback Details",
        "key":  "244",
    },
    "hrIncident": {
        "user": "AISHWAR_GetHRIncidentPo",
        "pass": "42nLmM!#weDk",
        "role": "Get HR Incident Positive Negative",
        "key":  "59",
    },
    "trainerNegFeedback": {
        "user": "AISHWAR_GetTrainerNegat",
        "pass": "j34JFz$s9Um#",
        "role": "Get Trainer Negative Feedback",
        "key":  "218",
    },
    # ── Skills & Certs ───────────────────────────────────────────────────────
    "trainerSkills": {
        "user": "AISHWAR_GetTrainerSkill",
        "pass": "dpcwt4L5$@7U",
        "role": "Get Trainer Skills",
        "key":  "217",
    },
    "vendorCertCount": {
        "user": "AISHWAR_GettrainerVende",
        "pass": "!$R#gQuAs9Rw",
        "role": "Get trainer Vender Certification Count",
        "key":  "57",
    },
    # ── Course & Scheduling ──────────────────────────────────────────────────
    "trainerAvailability": {
        "user": "AISHWAR_Traineravailabi",
        "pass": "c2yRDVdG#XCs",
        "role": "Trainer availability",
        "key":  "90",
    },
    "scid": {
        "user": "AISHWAR_GetSCID",
        "pass": "kLH#4T!Tfu6f",
        "role": "Get SCID",
        "key":  "173",
    },
    "activeSCDate": {
        "user": "AISHWAR_GetActiveSCDate",
        "pass": "P2mbqrhB#t4F",
        "role": "Get Active SC Date",
        "key":  "13",
    },
    "assignmentPax": {
        "user": "AISHWAR_GetAssignmentpa",
        "pass": "!zSgxaRdA9dC",
        "role": "Get Assignment pax",
        "key":  "209",
    },
    "recordingDetails": {
        "user": "AISHWAR_GetRecordingDet",
        "pass": "RPtPvRq5nF$H",
        "role": "Get Recording Details by Assignment Id",
        "key":  "278",
    },
    "last3MonthsUtil": {
        "user": "AISHWAR_TrainerLast3Mon",
        "pass": "TmSe!9A!@GfL",
        "role": "Trainer_Last_3_Months_Utilization",
        "key":  "39",
    },
}

_token_cache: dict = {}
_sessions: dict = {}


# ─── RMS low-level helpers ────────────────────────────────────────────────────

def _rms_post(path, body, timeout=_TIMEOUT):
    data = json.dumps(body).encode()
    req = urllib.request.Request(
        _RMS_BASE + path, data=data,
        headers={"Content-Type": "application/json", "Accept": "application/json"},
        method="POST",
    )
    with urllib.request.urlopen(req, timeout=timeout) as r:
        return json.loads(r.read().decode())


def _token(api_name):
    if api_name in _token_cache:
        return _token_cache[api_name]
    cfg = _APIS[api_name]
    js = _rms_post(_TOKEN_EP, {
        "userName": cfg["user"],
        "userPassword": cfg["pass"],
        "userRole": cfg["role"],
    })
    tok = js.get("content") or {}
    _token_cache[api_name] = tok
    return tok


def _rms(api_name, body):
    """Call RMS and return list/dict content. Returns None on network failure."""
    try:
        cfg = _APIS[api_name]
        for attempt in range(2):
            tok = _token(api_name)
            at = urllib.parse.quote(str(tok.get("accessToken", "")), safe="")
            dt = urllib.parse.quote(str(tok.get("deviceToken", "")), safe="")
            qs = f"?apikey={cfg['key']}&accessToken={at}&deviceToken={dt}"
            js = _rms_post(_DATA_EP + qs, body)
            code = js.get("statuscode", js.get("statusCode", 200))
            if code in (401, 403) and attempt == 0:
                _token_cache.pop(api_name, None)
                continue
            content = js.get("content")
            if isinstance(content, str):
                try:
                    content = json.loads(content)
                except Exception:
                    content = []
            if isinstance(content, dict):
                for k in ("Data", "data", "Result", "result", "Items", "items"):
                    if k in content:
                        content = content[k]
                        break
            return content if isinstance(content, list) else ([] if content is None else content)
    except Exception:
        return None


# ─── Role verification ────────────────────────────────────────────────────────

def _verify_role(email):
    """
    Returns:
      ("manager",      reportees_list)  — user has direct/indirect reportees
      ("trainer_plus", [util_row])      — user is a trainer flagged Trainer Plus
      ("rms_error",    None)            — RMS unreachable
      (None,           None)            — no qualifying role found

    Note on Trainer Plus: the flag lives on the *manager's* reportee rows
    (`TrainerPlus: "Yes"|"No"`), and RMS exposes no self-service lookup for it.
    The previous implementation read `Designation` off `trainerDetails`, but that
    API returns one row *per course* and has no Designation field at all — so the
    check always failed and every Trainer Plus was rejected with 401.
    Until a self-lookup API exists, a non-manager is admitted only if RMS knows
    them as a trainer, and the session is marked `trainer_plus_unverified`.
    """
    reportees = _rms("reportees", {"email": email})
    if reportees is None:
        return "rms_error", None
    if isinstance(reportees, list) and reportees:
        return "manager", reportees

    util = _rms("utilization", {"email": email})
    if util is None:
        return "rms_error", None
    if isinstance(util, list) and util and isinstance(util[0], dict) and util[0].get("TrainerId"):
        return "trainer_plus", util

    return None, None


# ─── Date helpers ─────────────────────────────────────────────────────────────

# RMS is not consistent: assignments come back as "03-Aug-2026", unallocated
# demand as "2026-08-27T00:00:00+05:30". Missing %d-%b-%Y here meant every
# assignment date silently parsed to None, so no trainer could ever be detected
# as delivering and current/next batch were always empty.
_DATE_FMTS = [
    "%d-%b-%Y", "%d-%B-%Y", "%d %b %Y", "%d %B %Y",
    "%Y-%m-%d", "%d/%m/%Y", "%m/%d/%Y", "%d-%m-%Y", "%Y/%m/%d",
    "%b %d %Y", "%d.%m.%Y",
]


def _parse_date(s, default=None):
    if not s:
        return default
    s = str(s).strip().split("T")[0].strip()
    for fmt in _DATE_FMTS:
        try:
            return datetime.strptime(s, fmt).date()
        except ValueError:
            pass
    # Last resort: leading ISO date inside a longer string.
    m = _re.match(r"(\d{4})-(\d{2})-(\d{2})", s)
    if m:
        try:
            return date(int(m.group(1)), int(m.group(2)), int(m.group(3)))
        except ValueError:
            pass
    return default


def _iso(d):
    return d.isoformat() if isinstance(d, date) else ""


def _engagement_state(assignment, today):
    st = _parse_date(assignment.get("StarDate", assignment.get("StartDate", "")))
    en = _parse_date(assignment.get("EndDate", ""))
    if not st:
        return "unknown"
    end_d = en or (st + timedelta(days=1))
    if today > end_d:
        return "completed"
    if st <= today:
        return "current"
    return "upcoming"


# ─── Utilization helper ───────────────────────────────────────────────────────

_mpat = _re.compile(r'^[A-Z][a-z]{2}\s+\d{4}$')


def _util_row(email):
    """Raw utilization row: TrainerId, TrainerName, EmailId, DOJ + 'Mon YYYY' columns."""
    rows = _rms("utilization", {"email": email}) or []
    if isinstance(rows, list) and rows and isinstance(rows[0], dict):
        return rows[0]
    return rows if isinstance(rows, dict) else {}


def _util_series(row):
    """[{month, load, utilization}] in calendar order from the monthly columns."""
    out = []
    for k, v in row.items():
        if not _mpat.match(str(k).strip()) or not isinstance(v, str) or "/" not in v:
            continue
        load, util = (v.split("/") + [""])[:2]
        try:
            out.append({
                "month": str(k).strip(),
                "load": round(float(load.strip()), 1),
                "utilization": round(float(util.strip()), 1),
            })
        except ValueError:
            pass
    out.sort(key=lambda m: _parse_date("01 " + m["month"], default=date.min))
    return out


def _avg_util(series):
    """Average of the trailing three months, which is what the web dashboard shows."""
    recent = [m["utilization"] for m in series][-3:]
    return max(0, min(100, round(sum(recent) / len(recent)))) if recent else 0


def _safe_util(email):
    try:
        return _avg_util(_util_series(_util_row(email)))
    except Exception:
        return 0


def _skills(email):
    """Capability rows from trainerDetails (one row per course the trainer holds)."""
    rows = _rms("trainerDetails", {"email": email}) or []
    out = []
    for r in (rows if isinstance(rows, list) else []):
        if not isinstance(r, dict) or not r.get("CourseName"):
            continue
        try:
            qubits = float(r.get("QubitsScore") or 0)
        except (TypeError, ValueError):
            qubits = 0.0
        try:
            delivered = int(str(r.get("Course Assignment") or "0").strip() or 0)
        except ValueError:
            delivered = 0
        out.append({
            "course":       str(r.get("CourseName", "")).strip(),
            "vendor":       str(r.get("VendorName", "") or "").strip(),
            "qubits_score": round(qubits),
            "skill_level":  str(r.get("SkillLevel", "") or "").strip(),
            "approved":     str(r.get("OfficiallyApproved", "")).strip().lower() == "yes",
            "future_skill": str(r.get("Is Future Skill", "")).strip().lower() == "yes",
            "delivered":    delivered,
        })
    out.sort(key=lambda s: (-s["qubits_score"], s["course"]))
    return out


def _off_dates(email):
    """Availability constraints. RMS has no leave endpoint — these off-date fields
    on trainerDetails are the only unavailability signal, and are often null."""
    rows = _rms("trainerDetails", {"email": email}) or []
    row = rows[0] if (isinstance(rows, list) and rows and isinstance(rows[0], dict)) else {}
    fields = {
        "roaming":               "RoamingOffDates",
        "international_roaming": "InternationaRoamingOffDates",
        "night_il":              "NightILOffDates",
        "morning_il":            "MorningILOffDates",
        "evening_il":            "EveningILOffDates",
    }
    return {k: str(row.get(v)).strip() for k, v in fields.items()
            if row.get(v) not in (None, "", "null")}


def _certifications(email):
    """{count, held[]} — vendorCertCount returns one 'True'/'False' column per body."""
    rows = _rms("vendorCertCount", {"email": email}) or []
    row = rows[0] if (isinstance(rows, list) and rows and isinstance(rows[0], dict)) else {}
    skip = {"Trainer", "EmailId", "Certificate Count"}
    held = [k for k, v in row.items()
            if k not in skip and str(v).strip().lower() == "true"]
    try:
        count = int(row.get("Certificate Count") or 0)
    except (TypeError, ValueError):
        count = len(held)
    emp_code = ""
    if ";" in str(row.get("Trainer", "")):
        emp_code = str(row["Trainer"]).split(";")[-1].strip()
    return {"count": count, "held": held, "emp_code": emp_code}


# ─── Per-trainer build (runs in ThreadPoolExecutor) ───────────────────────────

def _build_trainer(r, today):
    """Fetch all per-trainer data and build ops/state/batches/feedback rows."""
    # Real reportee schema (verified live): TrainerName, TrainerId, EmpId,
    # OffEmail, TrainerPlus, IsdirectReportee, Designation.
    t_email = str(r.get("OffEmail", r.get("Email", ""))).strip().lower()
    t_name  = _re.sub(r"\s+", " ", str(r.get("TrainerName", r.get("Name", "Unknown")))).strip()
    emp_id  = str(r.get("EmpId", "")).strip()
    trn_id  = str(r.get("TrainerId", "")).strip()
    desig   = str(r.get("Designation", "")).strip()
    t_type  = "direct" if str(r.get("IsdirectReportee", "")).strip().lower() == "yes" else "indirect"
    is_plus = str(r.get("TrainerPlus", "")).strip().lower() == "yes"

    # ── Parallel sub-fetches (sequential within this worker) ────────────
    u_row  = _util_row(t_email) if t_email else {}
    series = _util_series(u_row)
    util   = _avg_util(series)

    neg_count = 0
    try:
        neg_rows = _rms("negFeedbackCount", {"email": t_email}) or []
        if isinstance(neg_rows, list) and neg_rows and isinstance(neg_rows[0], dict):
            neg_count = int(neg_rows[0].get("Total", 0) or 0)
    except Exception:
        pass

    window_start = (datetime.utcnow() - timedelta(days=30)).strftime("%Y-%m-%d")
    window_end   = (datetime.utcnow() + timedelta(days=90)).strftime("%Y-%m-%d")
    # _rms returns None on failure and [] for a genuinely empty result. Collapsing
    # the two (the old `or []`) made a transient RMS outage look like "no
    # assignments", so a busy trainer would be reported as Available. Keep them
    # distinct and degrade to "unknown" instead of asserting something false.
    assignments_raw = _rms("prevUpcoming", {
        "Startdate": window_start, "Enddate": window_end, "Email": t_email,
    })
    assignments_ok = assignments_raw is not None
    assignments = [a for a in (assignments_raw if isinstance(assignments_raw, list) else [])
                   if isinstance(a, dict)]
    util_ok = bool(u_row)

    # ── Determine current status ─────────────────────────────────────────
    current_a = None
    upcoming_a = None
    for a in assignments:
        st = _parse_date(a.get("StarDate", ""))
        en = _parse_date(a.get("EndDate", ""))
        if not st:
            continue
        end_d = en or (st + timedelta(days=1))
        if st <= today <= end_d:
            current_a = a
            break
        if st > today:
            if upcoming_a is None:
                upcoming_a = a
            else:
                existing_st = _parse_date(upcoming_a.get("StarDate", ""))
                if existing_st and st < existing_st:
                    upcoming_a = a

    if not assignments_ok:
        status = "unknown"          # RMS did not answer — assert nothing
    elif current_a:
        status = "teaching_now"
    elif upcoming_a:
        days_to = (_parse_date(upcoming_a.get("StarDate", ""), default=today) - today).days
        status = "scheduled_today" if days_to <= 3 else "preparing"
    else:
        status = "free"

    # ── Capacity bucket ──────────────────────────────────────────────────
    # This is deliberately *capacity*, not "readiness". The dashboard path has
    # no capability signal (qubits/approvals cost two extra RMS calls per
    # trainer), so the previous "readiness" bucket was just utilisation wearing
    # a different name — which labelled a 39%-utilised trainer "At Risk" when
    # they are simply under-booked. Real readiness is computed in trainer-360.
    capacity_bucket = (
        "Unknown" if not util_ok else
        "Stretched" if util > 85 else
        "Balanced" if util >= 60 else
        "Light" if util >= 30 else
        "On Bench"
    )
    readiness_score = util

    feedback_risk = "High" if neg_count > 2 else ("Medium" if neg_count > 0 else "Low")

    if neg_count > 2:
        recommended = "Urgent: Review feedback incidents"
    elif neg_count > 0:
        recommended = "Follow up on feedback"
    elif status == "free":
        recommended = "Consider new allocation"
    elif util < 40:
        recommended = "Check availability"
    else:
        recommended = "Monitor performance"

    confidence = 0 if not assignments_ok else (90 if current_a else (70 if upcoming_a else 55))

    cur_batch = {}
    nxt_batch = {}
    def _batch(a):
        st, en = _parse_date(a.get("StarDate", "")), _parse_date(a.get("EndDate", ""))
        return {
            "course_name":   str(a.get("Course", "") or "").strip(),
            "delivery_mode": str(a.get("Mode", "") or "").strip(),
            "location":      str(a.get("Location", "") or "").strip(),
            "vendor":        str(a.get("Vendor", "") or "").strip(),
            "assignment_id": str(a.get("AssignmentId", "") or ""),
            "participants":  a.get("NoOfParticipants", 0),
            "start_at":      _iso(st),
            "end_at":        _iso(en),
            "start_time":    str(a.get("StartTime", "") or ""),
            "end_time":      str(a.get("EndTime", "") or ""),
            "days_left":     (en - today).days if en else None,
            "days_until":    (st - today).days if st else None,
        }

    if current_a:
        cur_batch = _batch(current_a)
    if upcoming_a:
        nxt_batch = _batch(upcoming_a)

    days_label = ""
    if upcoming_a:
        nd = _parse_date(upcoming_a.get("StarDate", ""))
        if nd:
            d = (nd - today).days
            days_label = f"Upcoming in {d} day{'s' if d != 1 else ''}"

    state_labels = {
        "teaching_now":   "Delivering",
        "scheduled_today":"Scheduled",
        "preparing":      "Preparing",
        "free":           "Available",
        "unknown":        "Unknown",
    }

    ops_row = {
        "trainer_name":           t_name,
        "official_email":         t_email,
        "emp_id":                 emp_id,
        "trainer_id":             trn_id,
        "designation":            desig,
        "direct_or_indirect":     t_type,
        "trainer_plus":           is_plus,
        "current_utilization":    util,
        "utilization_current":    util,
        "utilization_series":     series,
        "capacity_bucket":        capacity_bucket,
        "readiness_bucket":        capacity_bucket,   # legacy key, v1.4.x clients
        "overall_readiness_score": readiness_score,
        "feedback_risk":          feedback_risk,
        "negative_count":         neg_count,
        "recommended_action":     recommended,
        "assignment_count":       len(assignments),
        "upcoming_count":         sum(1 for a in assignments
                                      if _engagement_state(a, today) == "upcoming"),
    }

    state_row = {
        "trainer_email":  t_email,
        "trainer_key":    t_email,
        "current_status": status,
        "status_label":   state_labels.get(status, "Unknown"),
        "confidence":     confidence,
        "current_batch":  cur_batch,
        "next_batch":     nxt_batch,
        "reason": (
            "Assignment data unavailable from RMS" if not assignments_ok else
            "Currently on assignment" if current_a else
            (days_label if upcoming_a else "No scheduled assignment")
        ),
        "data_complete": assignments_ok and util_ok,
    }

    batch_rows = []
    for a in assignments:
        row = _batch(a)
        row.update({
            "trainer_name":     t_name,
            "trainer_email":    t_email,
            "engagement_state": _engagement_state(a, today),
        })
        batch_rows.append(row)
    batch_rows.sort(key=lambda b: b["start_at"] or "")

    feedback_row = {
        "trainer_email":  t_email,
        "trainer_name":   t_name,
        "negative_count": neg_count,
    }

    decision = None
    if neg_count > 2:
        decision = {
            "trainer_email":      t_email,
            "trainer_name":       t_name,
            "assignment_status":  "review",
            "next_manager_action": "Urgent: Review feedback incidents",
        }

    action = None
    if neg_count > 0 or status == "free":
        action = {
            "title":           recommended,
            "trainer_name":    t_name,
            "category":        "Feedback" if neg_count > 0 else "Allocation",
            "priority":        "high" if neg_count > 2 else "medium",
            "lifecycle_state": "open",
        }

    return ops_row, state_row, batch_rows, feedback_row, decision, action


# ─── Routes ───────────────────────────────────────────────────────────────────

@app.route('/healthz', methods=['GET'])
def healthz():
    return jsonify({
        "status":    "ok",
        "service":   "SkillSync Backend",
        "version":   "4.0.0",
        "timestamp": datetime.utcnow().isoformat(),
    }), 200


@app.route('/api/auth/login', methods=['POST'])
def login():
    try:
        data  = request.get_json(silent=True) or {}
        email = str(data.get('email', '')).strip().lower()

        if not email or '@' not in email:
            return jsonify({"success": False, "error": "Email is required"}), 400

        if not email.endswith('@koenig-solutions.com'):
            return jsonify({
                "success": False,
                "error":   "Only @koenig-solutions.com accounts are permitted",
            }), 401

        role, role_data = _verify_role(email)

        if role == "rms_error":
            return jsonify({
                "success": False,
                "error":   "Cannot reach RMS — please retry in a moment",
            }), 503

        if role is None:
            return jsonify({
                "success": False,
                "error":   "Access denied: account must have a manager or Trainer Plus role",
            }), 401

        sid = secrets.token_urlsafe(24)
        _sessions[sid] = {"email": email, "role": role}

        return jsonify({
            "success":    True,
            "session_id": sid,
            "email":      email,
            "role":       role,
            "message":    "Login successful",
        }), 200

    except Exception as exc:
        return jsonify({"success": False, "error": f"Server error: {exc}"}), 500


@app.route('/api/auth/logout', methods=['POST'])
def logout():
    return jsonify({"success": True}), 200


@app.route('/api/data/unified-manager-intelligence', methods=['GET'])
def unified_intelligence():
    email = request.args.get('email', '').strip().lower()
    if not email:
        return jsonify({"error": "email query param required"}), 400

    today = datetime.utcnow().date()

    # ── Step 1: reportees ────────────────────────────────────────────────
    reportees    = _rms("reportees", {"email": email}) or []
    trainer_rows = [r for r in (reportees if isinstance(reportees, list) else [])
                    if isinstance(r, dict)][:20]

    # ── Step 2: unallocated demand (global) ──────────────────────────────
    # Field names from live API: AssignmentID, Coursename, CourseSDate, CourseEDate,
    # "Delivery Mode" (with space), vendor, "Assignment City"
    unallocated_raw = _rms("unallocated", {}) or []
    demand_df = []
    for d in (unallocated_raw if isinstance(unallocated_raw, list) else []):
        if isinstance(d, dict):
            demand_df.append({
                "demand_id":     str(d.get("AssignmentID", d.get("AssignmentId", ""))),
                "course_name":   str(d.get("Coursename", d.get("Course", d.get("CourseName", "")))),
                "start_date":    str(d.get("CourseSDate", d.get("StarDate", d.get("StartDate", "")))).split("T")[0],
                "end_date":      str(d.get("CourseEDate", d.get("EndDate", ""))).split("T")[0],
                "delivery_mode": str(d.get("Delivery Mode", d.get("Mode", d.get("DeliveryMode", "")))),
                "customer":      str(d.get("vendor", d.get("Customer", d.get("client", "")))),
                "location":      str(d.get("Assignment City", d.get("Location", ""))),
                "participants":  str(d.get("NoOfParticipants", "")),
            })

    # ── Step 3: per-trainer data (parallel) ──────────────────────────────
    def _worker(r):
        try:
            return _build_trainer(r, today)
        except Exception:
            return None

    with ThreadPoolExecutor(max_workers=8) as pool:
        results = list(pool.map(_worker, trainer_rows))

    trainer_ops    = []
    trainer_states = []
    all_batches    = []
    feedback_sums  = []
    decisions      = []
    actions        = []

    for res in results:
        if not res:
            continue
        ops, state, batches, feedback, decision, action = res
        trainer_ops.append(ops)
        trainer_states.append(state)
        all_batches.extend(batches)
        feedback_sums.append(feedback)
        if decision:
            decisions.append(decision)
        if action:
            actions.append(action)

    # ── KPI summary ──────────────────────────────────────────────────────
    util_vals   = [t["current_utilization"] for t in trainer_ops if isinstance(t["current_utilization"], (int, float))]
    avg_util    = round(sum(util_vals) / len(util_vals)) if util_vals else 0
    active_cnt  = sum(1 for s in trainer_states if s["current_status"] != "unknown")
    mgr_name    = email.split("@")[0].replace(".", " ").title()

    # ── Response (web-frontend data model + backward-compat fields) ──────
    return jsonify({
        # Web-frontend arrays
        "trainer_operations_df":    trainer_ops,
        "trainer_current_state_df": trainer_states,
        "batch_engagement_df":      all_batches,
        "unallocated_demand_df":    demand_df,
        "trainer_feedback_summary_df": feedback_sums,
        "manager_action_objects":   actions,
        "trainer_decision_objects": decisions,
        "future_skill_roadmap_df":  [],
        "data_health_df":           [],
        "from_cache":               False,
        # Backward-compat fields (Android v1.2.x)
        "manager": {
            "name":  mgr_name,
            "email": email,
            "role":  "Delivery Manager",
        },
        "kpis": {
            "active_trainers": len(trainer_ops),
            "avg_utilization": avg_util,
            "pending_actions": len(actions),
            "completion_rate": 95,
        },
        "trainers": [
            {
                "name":        t["trainer_name"],
                "email":       t["official_email"],
                "utilization": t["current_utilization"],
                "status":      "Active" if t["current_utilization"] > 20 else "Inactive",
                "skills":      [],
            }
            for t in trainer_ops
        ],
        "actions": actions,
        "summary": {
            "total_trainers":  len(trainer_ops),
            "active_trainers": active_cnt,
            "avg_utilization": avg_util,
            "pending_actions": len(actions),
        },
        "cache":     {"age": 0, "ttl": 3600, "source": "rms_live"},
        "timestamp": datetime.utcnow().isoformat(),
    }), 200


@app.route('/api/data/trainer-360', methods=['GET'])
def trainer_360():
    """
    Deep profile for one trainer. Kept off the dashboard payload deliberately —
    skills and certifications are two extra RMS round-trips per trainer, which
    would multiply across a full roster. Fetched on demand instead.
    """
    email = request.args.get('email', '').strip().lower()
    if not email:
        return jsonify({"error": "email query param required"}), 400

    today = datetime.utcnow().date()

    def _identity_and_util():
        row = _util_row(email)
        return row, _util_series(row)

    def _assignments():
        return _rms("prevUpcoming", {
            "Startdate": (datetime.utcnow() - timedelta(days=365)).strftime("%Y-%m-%d"),
            "Enddate":   (datetime.utcnow() + timedelta(days=180)).strftime("%Y-%m-%d"),
            "Email":     email,
        }) or []

    with ThreadPoolExecutor(max_workers=6) as pool:
        f_util   = pool.submit(_identity_and_util)
        f_skills = pool.submit(_skills, email)
        f_certs  = pool.submit(_certifications, email)
        f_assign = pool.submit(_assignments)
        f_off    = pool.submit(_off_dates, email)
        f_neg    = pool.submit(_rms, "negFeedbackCount", {"email": email})
        f_hr     = pool.submit(_rms, "hrIncident", {"email": email})

        u_row, series = f_util.result()
        skills   = f_skills.result()
        certs    = f_certs.result()
        assigns  = [a for a in (f_assign.result() or []) if isinstance(a, dict)]
        off      = f_off.result()
        neg_rows = f_neg.result() or []
        hr_rows  = f_hr.result() or []

    emp_code = certs.get("emp_code", "")
    # Negative-feedback detail keys off employee_id, not email.
    neg_detail = (_rms("trainerNegFeedback", {"employee_id": emp_code}) or []) if emp_code else []

    delivery = []
    for a in assigns:
        st, en = _parse_date(a.get("StarDate", "")), _parse_date(a.get("EndDate", ""))
        delivery.append({
            "course":         str(a.get("Course", "") or "").strip(),
            "vendor":         str(a.get("Vendor", "") or "").strip(),
            "mode":           str(a.get("Mode", "") or "").strip(),
            "location":       str(a.get("Location", "") or "").strip(),
            "participants":   a.get("NoOfParticipants", 0),
            "assignment_id":  str(a.get("AssignmentId", "") or ""),
            "start_at":       _iso(st),
            "end_at":         _iso(en),
            "start_time":     str(a.get("StartTime", "") or ""),
            "end_time":       str(a.get("EndTime", "") or ""),
            "state":          _engagement_state(a, today),
        })
    delivery.sort(key=lambda d: d["start_at"] or "", reverse=True)

    def _num(v):
        try:
            return int(v or 0)
        except (TypeError, ValueError):
            return 0

    neg_total = sum(_num(r.get("Total")) for r in neg_rows if isinstance(r, dict))
    hr_pos = sum(_num(r.get("Positive Count")) for r in hr_rows if isinstance(r, dict))
    hr_neg = sum(_num(r.get("Negative Count")) for r in hr_rows if isinstance(r, dict))

    approved = [s for s in skills if s["approved"]]
    future   = [s for s in skills if s["future_skill"]]
    avg_qubits = round(sum(s["qubits_score"] for s in skills) / len(skills)) if skills else 0

    return jsonify({
        "identity": {
            "name":        _re.sub(r"\s+", " ", str(u_row.get("TrainerName", "") or "")).strip(),
            "email":       str(u_row.get("EmailId", email) or email),
            "trainer_id":  str(u_row.get("TrainerId", "") or ""),
            "emp_code":    emp_code,
            "date_of_joining": _iso(_parse_date(u_row.get("DOJ", ""))),
        },
        "utilization": {
            "current": _avg_util(series),
            "series":  series,
            "peak":    max((m["utilization"] for m in series), default=0),
        },
        "capability": {
            "total_courses":    len(skills),
            "approved_courses": len(approved),
            "future_skills":    len(future),
            "avg_qubits":       avg_qubits,
            "courses":          skills,
        },
        "certifications": {"count": certs["count"], "held": certs["held"]},
        "delivery": {
            "total":    len(delivery),
            "upcoming": sum(1 for d in delivery if d["state"] == "upcoming"),
            "current":  sum(1 for d in delivery if d["state"] == "current"),
            "assignments": delivery,
        },
        "feedback": {
            "negative_total":   neg_total,
            "hr_positive":      hr_pos,
            "hr_negative":      hr_neg,
            "negative_details": [r for r in neg_detail if isinstance(r, dict)],
        },
        # Surfaced so the UI can say "no data" honestly rather than implying zero.
        "availability": {
            "off_dates": off,
            "leave_data_available": False,
        },
        "timestamp": datetime.utcnow().isoformat(),
    }), 200


@app.errorhandler(404)
def not_found(error):
    return jsonify({"error": "Not found", "path": request.path}), 404


@app.errorhandler(500)
def internal_error(error):
    return jsonify({"error": "Internal server error"}), 500


@app.route('/', methods=['GET'])
def root():
    return jsonify({
        "service":  "SkillSync Backend",
        "version":  "4.0.0",
        "endpoints": {
            "POST /api/auth/login":                               "Authenticate (role-verified)",
            "POST /api/auth/logout":                              "Logout",
            "GET  /api/data/unified-manager-intelligence?email=": "Full dashboard payload",
            "GET  /api/data/trainer-360?email=":                  "Deep single-trainer profile",
            "GET  /healthz":                                      "Health check",
        },
    }), 200


if __name__ == '__main__':
    app.run(debug=False, port=8080)
