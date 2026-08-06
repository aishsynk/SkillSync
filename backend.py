"""
SkillSync Backend API v3.0
Deployed on Render — production backend for SkillSync Android app.

Auth: POST /api/auth/login — @koenig-solutions.com only, manager or Trainer Plus role.
Data: GET /api/data/unified-manager-intelligence?email=EMAIL — full dashboard payload
      matching the web frontend data model (trainer_operations_df, trainer_current_state_df,
      batch_engagement_df, unallocated_demand_df, trainer_feedback_summary_df,
      manager_action_objects, trainer_decision_objects).
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
      ("trainer_plus", [details_row])   — user is a Trainer Plus designation
      ("rms_error",    None)            — RMS unreachable
      (None,           None)            — no qualifying role found
    """
    reportees = _rms("reportees", {"email": email})
    if reportees is None:
        return "rms_error", None
    if isinstance(reportees, list) and reportees:
        return "manager", reportees

    details = _rms("trainerDetails", {"email": email})
    if isinstance(details, list) and details:
        row = details[0] if isinstance(details[0], dict) else {}
    elif isinstance(details, dict):
        row = details
    else:
        row = {}
    desig = str(row.get("Designation", row.get("Role", row.get("designation", "")))).lower()
    if "plus" in desig or "senior" in desig:
        return "trainer_plus", [row]

    return None, None


# ─── Date helpers ─────────────────────────────────────────────────────────────

_DATE_FMTS = ["%Y-%m-%d", "%d/%m/%Y", "%m/%d/%Y", "%d-%m-%Y", "%Y/%m/%d", "%d %b %Y"]


def _parse_date(s, default=None):
    if not s:
        return default
    s = str(s).strip().split("T")[0].split(" ")[0]
    for fmt in _DATE_FMTS:
        try:
            return datetime.strptime(s, fmt).date()
        except ValueError:
            pass
    return default


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


def _safe_util(email):
    """Return avg util% for last 3 months from monthly-column format ('Jun 2026': '75.77 / 43.05')."""
    try:
        rows = _rms("utilization", {"email": email}) or []
        row = rows[0] if (isinstance(rows, list) and rows and isinstance(rows[0], dict)) \
              else (rows if isinstance(rows, dict) else {})
        monthly = []
        for k, v in row.items():
            if _mpat.match(str(k).strip()) and isinstance(v, str) and "/" in v:
                try:
                    monthly.append(float(v.split("/")[1].strip()))
                except (ValueError, IndexError):
                    pass
        if monthly:
            return max(0, min(100, round(sum(monthly[-3:]) / len(monthly[-3:]))))
    except Exception:
        pass
    return 0


# ─── Per-trainer build (runs in ThreadPoolExecutor) ───────────────────────────

def _build_trainer(r, today):
    """Fetch all per-trainer data and build ops/state/batches/feedback rows."""
    t_email = str(r.get("OffEmail", r.get("Email", ""))).strip().lower()
    t_name  = str(r.get("Name", r.get("TrainerName", "Unknown"))).strip()
    emp_id  = str(r.get("EmpId", r.get("TrainerId", ""))).strip()
    desig   = str(r.get("Designation", r.get("designation", ""))).strip()
    t_type  = str(r.get("Type", "direct")).strip().lower()

    # ── Parallel sub-fetches (sequential within this worker) ────────────
    util = _safe_util(t_email) if t_email else 0

    neg_count = 0
    try:
        neg_rows = _rms("negFeedbackCount", {"email": t_email}) or []
        if isinstance(neg_rows, list) and neg_rows and isinstance(neg_rows[0], dict):
            neg_count = int(neg_rows[0].get("Total", 0) or 0)
    except Exception:
        pass

    window_start = (datetime.utcnow() - timedelta(days=30)).strftime("%Y-%m-%d")
    window_end   = (datetime.utcnow() + timedelta(days=90)).strftime("%Y-%m-%d")
    assignments_raw = _rms("prevUpcoming", {
        "Startdate": window_start, "Enddate": window_end, "Email": t_email,
    }) or []
    assignments = [a for a in (assignments_raw if isinstance(assignments_raw, list) else []) if isinstance(a, dict)]

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

    if current_a:
        status = "teaching_now"
    elif upcoming_a:
        days_to = (_parse_date(upcoming_a.get("StarDate", ""), default=today) - today).days
        status = "scheduled_today" if days_to <= 3 else "preparing"
    else:
        status = "free" if util < 30 else "unknown"

    # ── Readiness score ──────────────────────────────────────────────────
    base_score = util
    if neg_count > 2:
        base_score -= 20
    elif neg_count > 0:
        base_score -= 8
    if status == "teaching_now":
        base_score += 5
    readiness_score = max(0, min(100, round(base_score)))

    readiness_bucket = (
        "Ready" if readiness_score >= 70 else
        "Needs Coaching" if readiness_score >= 45 else
        "At Risk"
    )

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

    confidence = 90 if current_a else (70 if upcoming_a else 40)

    cur_batch = {}
    nxt_batch = {}
    if current_a:
        cur_batch = {
            "course_name":    current_a.get("Course", ""),
            "delivery_mode":  current_a.get("Mode", ""),
            "location":       current_a.get("Location", ""),
            "assignment_id":  current_a.get("AssignmentId", ""),
            "participants":   current_a.get("NoOfParticipants", ""),
        }
    if upcoming_a:
        nxt_batch = {
            "course_name": upcoming_a.get("Course", ""),
            "start_at":    upcoming_a.get("StarDate", ""),
            "delivery_mode": upcoming_a.get("Mode", ""),
        }

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
        "designation":            desig,
        "direct_or_indirect":     t_type,
        "current_utilization":    util,
        "utilization_current":    util,
        "readiness_bucket":       readiness_bucket,
        "overall_readiness_score": readiness_score,
        "feedback_risk":          feedback_risk,
        "negative_count":         neg_count,
        "recommended_action":     recommended,
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
            "Currently on assignment" if current_a else
            (days_label if upcoming_a else "No scheduled assignment")
        ),
    }

    batch_rows = []
    for a in assignments:
        es = _engagement_state(a, today)
        batch_rows.append({
            "trainer_name":   t_name,
            "trainer_email":  t_email,
            "course_name":    a.get("Course", ""),
            "delivery_mode":  a.get("Mode", ""),
            "start_at":       a.get("StarDate", ""),
            "end_at":         a.get("EndDate", ""),
            "assignment_id":  a.get("AssignmentId", ""),
            "engagement_state": es,
        })

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
        "version":   "3.0.0",
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
        "version":  "3.0.0",
        "endpoints": {
            "POST /api/auth/login":                               "Authenticate (role-verified)",
            "POST /api/auth/logout":                              "Logout",
            "GET  /api/data/unified-manager-intelligence?email=": "Full dashboard payload",
            "GET  /healthz":                                      "Health check",
        },
    }), 200


if __name__ == '__main__':
    app.run(debug=False, port=8080)
