"""
SkillSync Backend API v6.0
Deployed on Render — production backend for SkillSync Android app.

Auth: POST /api/auth/login — @koenig-solutions.com only, manager or Trainer Plus role.
Data: GET /api/data/unified-manager-intelligence?email=EMAIL — dashboard payload
      matching the web frontend data model (trainer_operations_df, trainer_current_state_df,
      batch_engagement_df, unallocated_demand_df, trainer_feedback_summary_df,
      manager_action_objects, trainer_decision_objects) plus `manager_kpis`.
      GET /api/data/manager-profile?email=EMAIL — the signed-in user's own identity
      (name, photo, designation, experience) so the dashboard can be personalised.
      GET /api/data/trainer-360?email=EMAIL — deep single-trainer profile
      (profile, capability, certifications + gap analysis, delivery, feedback).
      GET /api/data/allocation-desk?email=EMAIL — unallocated batches ranked by
      how well the manager's own team can cover them (real capability matching).
      GET /api/data/team-courses?email=EMAIL — course catalogue the team can teach,
      with ownership, certification mapping and coverage depth.
      GET /api/data/trainer-skills?email=EMAIL — the RMS skill register for one
      trainer; also the read-back used to verify a skill write actually landed.
      POST /api/action/mark-skill — records a trainer skill. WRITES to production
      RMS (Add Trainer Skill / IDP, key 255); inputs are validated server-side and
      the write is verified by re-reading the register before success is reported.

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
  * vendorCertCount(57) Trainer is "Name;EmpCode"; one "True"/"False" column per
                        ACCREDITING BODY (MCT, CCSI, VCI…) — this is the trainer's
                        right to teach, NOT the exams they have passed.
  * trainerResume (87)  the only source of a real profile: TrainerName,
                        TrainerImage (literal string "None" when unset — not null),
                        Certifications, Languages, Summary, Experience, Skill,
                        TrainingsDeliveredFor, Feedback. Certifications/Languages/
                        Skill are "#"-delimited; each certification entry is
                        "<title>: <logo url>", and the title itself contains
                        colons ("Microsoft Certified: Azure AI Engineer
                        Associate"), so split on ": http", never on the last ":".
  * trainerSkills (217) employee_name/employee_code/course_id/course_name. Types
                        are inconsistent between trainers — ids come back as int
                        for one and str for another, so coerce both ends.
  * unallocated (190)   Coursename, CourseSDate/CourseEDate, "Delivery Mode",
                        vendor, "Assignment City", NoOfParticipants, AssignmentID
  * trainerSkills (217), trainerNegFeedback (218), last3MonthsUtil (39) key off
    employee_id / EmpCode — passing an email or blank returns zero rows silently.
  * unallocated (190) also carries CourseId, a direct TOC pdf URL, CourseURL,
    and HTML blobs in SCID / TOTRecords that must be stripped before display.
  * uniqueCerts (72) returns zero rows for every body shape tried
    (employee_id / email / EmpCode / empty) — treat as unavailable, not empty.
  * trainerFeedback (244) and assignmentPax (209), added 2026-08-07: NOT YET
    verified against a live response. Field names below are transcribed from
    the instruction file only, which has been wrong before (see
    trainer_portal_api_details/ "Check Course Availability in RMS.txt" —
    mislabeled, actually the Trainer RC Schedule API). Parsed defensively with
    multiple key fallbacks; trainer-360's feedback.responses_raw_sample field
    is temporary scaffolding to confirm the real shape from a live call, then
    should be deleted.
    - trainerFeedback expected: Question, TextAnswer/MCQAnswer, FeedBackDate,
      AssignmentId, SCID, TrainerEmail, TrainerName.
    - assignmentPax expected: StudentName, StudentEmail.

There is no leave/absence endpoint in the RMS catalogue. The only unavailability
signal is the *OffDates fields on trainerDetails, which are frequently null.
There is also no API that reports the signed-in user's own job title; the closest
real signal is the first role heading in their resume Experience blob.
"""

from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, date, timedelta
from flask import Flask, jsonify, request
from flask_cors import CORS
import hashlib
import json
import os
import re as _re
import secrets
import threading
import time
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
    # Course-level exam policy for the whole catalogue. Verified live
    # 2026-08-08: 10,934 rows across 438 vendors, fields Courseid / CName /
    # "Exam Required or Not" / CourseStatus / Vendor. This is what lets the
    # certification gap cover Cisco, AWS, RedHat, Oracle and the rest instead
    # of only the Microsoft codes in _CERT_CATALOG.
    "courseWithoutExam": {
        "user": "AISHWAR_CourseWhitoutEx",
        "pass": "V9n82gfmC$$W",
        "role": "Course Whitout Exam",
        "key":  "213",
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
    "courseSyllabus": {
        "user": "AISHWAR_GetCourseSyllab",
        "pass": "W@PFkUQt$Ek3",
        "role": "Get Course Syllabus TOC",
        "key":  "248",
    },
    "globalTrainers": {
        "user": "AISHWAR_GetInhouseandFL",
        "pass": "2XC!2LBpsTJh",
        "role": "Get Inhouse and FL Trainers Of Courses",
        "key":  "157",
    },
    # ── Profile ──────────────────────────────────────────────────────────────
    # The only endpoint that returns a person rather than a list of their
    # courses: photo, exam certifications, languages, experience, clients.
    "trainerResume": {
        "user": _ev("SKILLEDGE_RMS_RESUME_USER", "AISHWAR_TrainerResumeDe"),
        "pass": _ev("SKILLEDGE_RMS_RESUME_PASS", "nw@dL3xQD#BL"),
        "role": "Trainer Resume Details",
        "key":  "87",
    },
    # ── Write endpoint — mutates production RMS ──────────────────────────────
    "addTrainerSkill": {
        "user": "AISHWAR_AddTrainerSkill",
        "pass": "2bd6UhV#PJ#T",
        "role": "Add Trainer Skill (IDP)",
        "key":  "255",
    },
    "courseAvailability": {
        "user": "AISHWAR_CheckCourseAvai",
        "pass": "$3GapuDUF5XU",
        "role": "Check Course Availability in RMS",
        "key":  "104",
    },
}

_token_cache: dict = {}
_sessions: dict = {}

# ─── Response cache ───────────────────────────────────────────────────────────
#
# Measured from Render (2026-08-07): a single RMS round-trip costs 2-5s, and the
# screens overlap heavily — reportees is fetched by four different endpoints,
# trainerDetails by three. Uncached, opening Demand cost ~6s and Team Capability
# ~8s *every time*, re-fetching data that had just been read.
#
# TTLs are set by how fast each dataset actually moves, not by a single global
# number: utilisation is a monthly rollup, unallocated demand turns over during
# the day. The skill register is deliberately absent — it is the read-back that
# proves a write landed, and a cached copy would defeat the entire check.
_CACHE_TTL = {
    "reportees":        1800,   # org structure; changes on transfer, not hourly
    "trainerDetails":   1800,   # course capability
    "trainerResume":    3600,   # photo, certifications, experience
    "utilization":      1800,   # monthly rollup
    "vendorCertCount":  3600,   # accreditation bodies
    "negFeedbackCount":  900,
    "hrIncident":        900,
    "trainerNegFeedback": 900,
    "trainerFeedback":   900,   # per-question detail, same volatility as the count endpoints
    "assignmentPax":     600,   # roster can still change until the batch starts
    "prevUpcoming":      600,   # assignment calendar
    "unallocated":       180,   # demand turns over during the day
    "courseWithoutExam": 21600,  # catalogue-wide exam policy; changes rarely
    "courseSyllabus":    21600,  # 12k-row syllabus index; static, fetch once
    "last3MonthsUtil":    1800,  # same volatility as the utilisation rollup
    # "trainerSkills" intentionally omitted — see above.
    # "addTrainerSkill" is a write and must never be served from cache.
}

_cache: dict = {}
_cache_lock = threading.Lock()


def _cache_key(api_name, body):
    return api_name, json.dumps(body or {}, sort_keys=True, default=str)


def _cache_get(api_name, body):
    ttl = _CACHE_TTL.get(api_name)
    if not ttl:
        return None
    with _cache_lock:
        hit = _cache.get(_cache_key(api_name, body))
    if not hit:
        return None
    expires, value = hit
    return value if time.time() < expires else None


def _cache_put(api_name, body, value):
    ttl = _CACHE_TTL.get(api_name)
    # Never cache a failure: `None` means RMS did not answer, and freezing that
    # for 30 minutes would turn a blip into a half-hour outage.
    if not ttl or value is None:
        return
    with _cache_lock:
        _cache[_cache_key(api_name, body)] = (time.time() + ttl, value)


def _cache_purge(needle=""):
    """
    Drop cached entries. With [needle] (an email), drops only entries whose
    request body mentions it, so one manager's refresh does not evict another's.
    """
    with _cache_lock:
        if not needle:
            _cache.clear()
            return
        n = str(needle).lower()
        # "{}" is the unallocated-demand query: global data belonging to nobody,
        # so an email needle would never match it and pull-to-refresh on the
        # Demand tab would keep serving the stale list.
        for key in [k for k in _cache if n in k[1].lower() or k[1] == "{}"]:
            _cache.pop(key, None)


def _wants_fresh():
    """`?refresh=1` — pull-to-refresh must actually re-read RMS."""
    return str(request.args.get("refresh", "")).strip() in ("1", "true", "yes")


# ─── RMS low-level helpers ────────────────────────────────────────────────────

def _rms_post(path, body, timeout=_TIMEOUT):
    data = json.dumps(body).encode()
    req = urllib.request.Request(
        _RMS_BASE + path, data=data,
        headers={
            "Content-Type": "application/json",
            "Accept": "application/json",
            "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        },
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
    """
    Call RMS and return list/dict content. Returns None on network failure.

    Served from `_cache` when the endpoint has a TTL and a fresh entry exists;
    see `_CACHE_TTL` for why each TTL is what it is.
    """
    cached = _cache_get(api_name, body)
    if cached is not None:
        return cached
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
            out = content if isinstance(content, list) else ([] if content is None else content)
            _cache_put(api_name, body, out)
            return out
    except Exception:
        return None


# ─── Role verification ────────────────────────────────────────────────────────

def _verify_role(email):
    """
    SkillEdge is a Delivery Manager intelligence platform.
    All @koenig-solutions.com accounts logging in are granted full Delivery Manager role,
    with real reportees from RMS if available, or fallback team intelligence if
    reportees list is empty or RMS is unreachable.
    """
    if not email or not email.endswith("@koenig-solutions.com"):
        return None, None

    reportees = _rms("reportees", {"email": email})
    return "manager", reportees if isinstance(reportees, list) else []


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
    """Average of the trailing three months. A trend read, not a current one."""
    recent = [m["utilization"] for m in series][-3:]
    return max(0, min(100, round(sum(recent) / len(recent)))) if recent else 0


def _current_util(series):
    """
    Utilisation *right now*: the most recent month that actually carried load.

    Deliberately not the three-month average. RMS reports a rolling window, so
    the trailing months of someone who has just come off bench are zeros —
    averaging those in reports a trainer who is busy today as under-utilised,
    and the reverse for someone who just finished a heavy run. The last
    non-zero month is what a manager means by "how busy are they".

    Returns None when RMS carried no usable reading, which is a different
    fact from a measured 0% and must not be collapsed into one.
    """
    if not series:
        return None
    nonzero = [m["utilization"] for m in series if m["utilization"] > 0]
    value = nonzero[-1] if nonzero else series[-1]["utilization"]
    return max(0, min(100, round(value)))


def _utilization_status(util):
    """Offline parity: how loaded this trainer is, in words."""
    if util is None:
        return ""
    return "Overloaded" if util >= 85 else ("Healthy" if util >= 40 else "Underutilized")


def _availability_status(util):
    """Offline parity: how much room there is to give them more work."""
    if util is None:
        return ""
    return "Available" if util < 40 else ("Limited" if util < 85 else "Booked")


# ─── Delivery intelligence ────────────────────────────────────────────────────
#
# Thresholds mirror the offline project's shared/delivery_intelligence.py so a
# trainer reads the same on both products. The Android UI has always branched
# on these three fields (TrainerCard reads delivery_readiness_label,
# delivery_capacity_status and delivery_risk_level) but the backend never
# emitted them, so every one of those branches was dead and the card silently
# fell through to its capacity-bucket fallback.

def _delivery_label(score):
    if score >= 80:
        return "Ready"
    if score >= 65:
        return "Ready with Prep"
    if score >= 45:
        return "Needs Mentoring"
    return "Hold"


def _delivery_risk_label(score):
    return "High" if score >= 70 else ("Medium" if score >= 35 else "Low")


def _delivery_capacity_status(util):
    if util is None:
        return "Unknown"
    if util >= 85:
        return "Overloaded"
    if util < 40:
        return "Underutilized"
    return "Balanced"


def _delivery_row(ops_row, state_row):
    """One delivery-readiness verdict per trainer, from data already fetched."""
    util = ops_row.get("current_utilization")
    neg = ops_row.get("negative_count") or 0
    risk_pts = min(60, neg * 20)
    if util is not None and util > 95:
        risk_pts += 15
    if state_row.get("current_status") == "unknown":
        risk_pts += 20          # cannot vouch for someone RMS will not describe

    # Readiness here is capacity-and-conduct, not capability: the dashboard
    # path deliberately avoids the two extra RMS calls per trainer that real
    # capability scoring needs (see _readiness_score, used in trainer-360).
    score = 100
    score -= min(50, neg * 25)
    if util is None:
        score -= 20
    elif util >= 85:
        score -= 15
    if state_row.get("current_status") == "unknown":
        score -= 20
    score = max(0, min(100, score))

    return {
        "trainer_email":            ops_row.get("official_email", ""),
        "trainer_name":             ops_row.get("trainer_name", ""),
        "delivery_readiness_score": score,
        "delivery_readiness_label": _delivery_label(score),
        "delivery_capacity_status": _delivery_capacity_status(util),
        "delivery_risk_level":      _delivery_risk_label(min(100, risk_pts)),
    }


def _safe_util(email):
    """Current utilisation for one address; 0 when nothing is known.

    Ranking-only — a missing value still has to sort somewhere. Display paths
    use _current_util directly so they can distinguish None from 0.
    """
    try:
        return _current_util(_util_series(_util_row(email))) or 0
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


# ─── Course matching (allocation relevance) ───────────────────────────────────

_STOP = {"the", "and", "for", "with", "using", "to", "in", "of", "on", "a", "an",
         "introduction", "fundamentals", "course", "training", "certification"}
# Vendor course codes are the strongest identity signal. Two shapes occur:
# letter-led ("PL-300T00", "AI-102T00", "DP-900T00-A") and Microsoft's numeric
# MOC codes ("55071-A"). Matching only the first shape missed the latter entirely.
_CODE_ALPHA = _re.compile(r'\b([a-z]{2,6}[-\s]?\d{2,5}[a-z]?(?:[-\s]?\d{2,3})?[a-z]?)\b')
_CODE_NUM = _re.compile(r'\b(\d{4,6}[-\s]?[a-z]?)\b')


def _norm(s):
    return _re.sub(r'[^a-z0-9\s-]', ' ', str(s or "").lower()).strip()


def _course_code(name):
    n = _norm(name)
    m = _CODE_ALPHA.search(n) or _CODE_NUM.search(n)
    return _re.sub(r'[\s-]', '', m.group(1)) if m else ""


def _tokens(name):
    return {t for t in _norm(name).split() if len(t) > 2 and t not in _STOP}


def _match_score(batch_course, batch_vendor, cap_course, cap_vendor):
    """0-100 for how well one capability row covers a demanded course."""
    a, b = _norm(batch_course), _norm(cap_course)
    if not a or not b:
        return 0
    if a == b:
        return 100

    ca, cb = _course_code(batch_course), _course_code(cap_course)
    if ca and ca == cb:
        return 92                       # same vendor course code, different title text

    ta, tb = _tokens(batch_course), _tokens(cap_course)
    if not ta or not tb:
        return 0
    jaccard = len(ta & tb) / len(ta | tb)
    score = jaccard * 78
    if batch_vendor and cap_vendor and _norm(batch_vendor) == _norm(cap_vendor):
        score += 10                     # same vendor family is a real, weaker signal
    return int(min(100, round(score)))


# ─── AutoTall parity: negative-feedback allocation block + clean-record tie-break
#
# Mirrors RMS's own "Auto Tall" trainer-allocation engine (HR changelog rules,
# current as of 05 Aug 2026) so this app's suggested candidates match what RMS
# will actually let a manager auto-allocate — a "top match" the real system
# would refuse to auto-assign is worse than no suggestion at all.
#
#   - Negative-feedback block: starts 3 days after the feedback is marked,
#     lasts until 14 days after the mark date (effective 16 Jul 2026,
#     detailed 20 Jul 2026). A trainer inside that window is flagged and
#     sorted below every available candidate — not removed, since RMS's own
#     rule says the block only affects auto-selection; a manager can still
#     specify them manually.
#   - 6-month clean-record preference (effective 05 Aug 2026, the current
#     rule): among candidates tied on match score, one with no negative
#     feedback in the trailing 6 months is preferred over one who has any.
#   - Qubits score / QI category are deliberately NOT used as tie-breakers
#     below — RMS removed both on 27 Jul 2026 (they were briefly introduced
#     20-22 Jul 2026, then reversed). qubits_score is still returned for
#     display only.
#
# Rules referenced in the same HR changelog but NOT implemented here, because
# no RMS API in this app's integration (see AI/CONTEXT.md's 36-file audit)
# carries the underlying data: tech-call-trainer attribution (no pre-sales
# call endpoint), mock-delivery ratings (no mock/rehearsal endpoint), and the
# Main/Additional-Trainer role distinction (unallocated demand rows don't
# distinguish role types the way RMS's internal engine does).

def _feedback_recency(emp_code):
    """
    Most recent negative-feedback date for one trainer, or None.

    Field name is unverified against a live response — trainerNegFeedback's
    documented shape (feedback_date) comes from the same instruction-file set
    that has already proven wrong more than once this project, so every
    plausible key is checked rather than trusting one.
    """
    if not emp_code:
        return None
    rows = _rms("trainerNegFeedback", {"employee_id": emp_code}) or []
    dates = []
    for r in rows:
        if not isinstance(r, dict):
            continue
        raw = r.get("feedback_date") or r.get("FeedBackDate") or r.get("dates") or r.get("Date")
        d = _parse_date(str(raw or ""))
        if d:
            dates.append(d)
    return max(dates) if dates else None


def _allocation_block_status(most_recent_negative, today):
    """RMS AutoTall's block window: not auto-allocated from day 3 to day 14
    after the feedback is marked. Outside that window — including before day
    3, the verification grace period — the trainer is unaffected."""
    if most_recent_negative is None:
        return {"blocked": False, "blocked_until": None, "recent_negative_6mo": False}
    blocked_from = most_recent_negative + timedelta(days=3)
    blocked_until = most_recent_negative + timedelta(days=14)
    is_blocked = blocked_from <= today <= blocked_until
    return {
        "blocked": is_blocked,
        "blocked_until": _iso(blocked_until) if is_blocked else None,
        "recent_negative_6mo": (today - most_recent_negative).days <= 182,
    }


def _team_capability(reportees, manager_email=None, manager_name=""):
    """
    [(trainer_name, email, [capability rows], feedback_status, is_self)] for the
    manager's roster.

    The signed-in manager is appended as a candidate too (unless already a
    reportee of themselves, which cannot happen, or already present in the
    list for some other reason). Managers routinely deliver strategic,
    premium or escalated batches themselves — a matching engine that only
    ever suggests reportees misses that entirely.
    """
    today = datetime.utcnow().date()

    def one(email, name):
        caps = _skills(email) if email else []
        emp_code = _certifications(email)["emp_code"] if email else ""
        recent_negative = _feedback_recency(emp_code) if emp_code else None
        return name, email, caps, _allocation_block_status(recent_negative, today)

    rows = [r for r in (reportees if isinstance(reportees, list) else []) if isinstance(r, dict)]
    targets = [
        (str(r.get("OffEmail", "")).strip().lower(),
         _re.sub(r"\s+", " ", str(r.get("TrainerName", ""))).strip())
        for r in rows
    ]
    reportee_emails = {e for e, _ in targets if e}
    if manager_email and manager_email not in reportee_emails:
        targets.append((manager_email, manager_name or "You"))

    with ThreadPoolExecutor(max_workers=8) as pool:
        results = list(pool.map(lambda t: one(*t), targets))

    return [
        (name, email, caps, feedback, email == manager_email)
        for name, email, caps, feedback in results
    ]


# English is the default working language across the trainer pool, so it is
# the one language a course can always be assumed deliverable in unless a
# trainer's own profile says otherwise. Absence of a recorded language on
# the resume is treated as English (most resumes never list it explicitly
# precisely because it is the default), not as "unknown" — an unknown that
# silently demoted every trainer with an incomplete profile below trainers
# who happened to fill in "English: Fluent" would be a data-completeness
# artifact wearing a language-mismatch costume.
def _language_names(languages):
    """Language names as lowercase strings, whatever shape RMS returned.

    _resume() parses them into [{"language": ..., "level": ...}], but the
    field has arrived as bare strings before. Accepting both keeps a
    normaliser change from taking down allocation ranking again.
    """
    out = []
    for l in (languages or []):
        name = l.get("language", "") if isinstance(l, dict) else l
        name = str(name or "").strip().lower()
        if name:
            out.append(name)
    return out


def _speaks_english(languages):
    if not languages:
        return True
    return any("english" in n for n in _language_names(languages))


def _rank_batch(batch, team):
    """
    Best team match for one unallocated batch, plus the ranked candidate list.

    Ranking order, in priority: (1) skill alignment via _match_score, (2)
    trainer readiness — the Qubits score of the matched course, a real
    per-trainer-per-course signal rather than a generic profile number, (3)
    current utilisation/availability — a less-utilised trainer has more room
    to take this on, (4) English-speaking trainers are preferred as a class;
    non-English speakers are only surfaced ahead of them when no English
    speaker matches the course at all.
    """
    course, vendor = batch.get("course_name", ""), batch.get("customer", "")
    matched = []
    for name, email, caps, feedback, is_self in team:
        best, best_course, best_q = 0, "", 0
        for c in caps:
            s = _match_score(course, vendor, c["course"], c["vendor"])
            if s > best:
                best, best_course, best_q = s, c["course"], c["qubits_score"]
        if best > 0:
            matched.append((name, email, best, best_course, best_q, feedback, is_self))

    if not matched:
        return 0, [], "No Coverage"

    # Utilisation and language are per-course-RMS-call signals, so they are
    # only fetched for trainers who already matched the course — not the
    # whole team — to keep this proportional to real candidates.
    with ThreadPoolExecutor(max_workers=8) as pool:
        utils = list(pool.map(lambda m: _safe_util(m[1]), matched))
        langs = list(pool.map(lambda m: _resume(m[1]).get("languages", []), matched))

    batch_lang = (batch.get("language") or "").strip().lower()
    batch_skill = (batch.get("skill_level") or "").strip().lower()

    candidates = []
    for (name, email, best, best_course, best_q, feedback, is_self), util, languages in zip(matched, utils, langs):
        speaks_english = _speaks_english(languages)
        # _resume() returns languages as [{"language": ..., "level": ...}],
        # not as plain strings. Treating them as strings raised
        # AttributeError on every request and took the whole Demand endpoint
        # to a 500. Handle both shapes so a future normaliser change cannot
        # break it the same way again.
        trainer_langs = _language_names(languages)

        # 1. Language Constraint: Drop to 0 if the trainer does not speak the requested language.
        if batch_lang and batch_lang != "english":
            if not any(batch_lang in l for l in trainer_langs):
                best = 0
                best_q = 0

        # 2. Skill Level Constraint: Penalise heavily if the trainer is not qualified enough
        if best > 0 and batch_skill:
            if "expert" in batch_skill and best_q < 75:
                best = max(0, best - 50)
            elif "advanced" in batch_skill and best_q < 50:
                best = max(0, best - 50)
            elif "intermediate" in batch_skill and best_q < 25:
                best = max(0, best - 50)

        coverage = (
            "Best Match" if best >= 90 else
            "Available with Upskilling" if best >= 50 else
            "No Coverage"
        )
        candidates.append({
            "trainer_name":  name if not is_self else f"{name} (You)",
            "trainer_email": email,
            "is_self":       is_self,
            "match":         best,
            "via_course":    best_course,
            "qubits_score":  best_q,
            "readiness_score": best_q,
            "utilization":   util,
            "speaks_english": speaks_english,
            "exact":         best >= 92,
            "category":      coverage,
            "coverage":      coverage,
            "blocked":             feedback["blocked"],
            "blocked_until":       feedback["blocked_until"],
            "recent_negative_6mo": feedback["recent_negative_6mo"],
        })

    # Sort key, in priority order: available before blocked (RMS would not
    # auto-allocate a blocked trainer regardless of anything else) > skill
    # match > readiness (Qubits on the matched course) > English speaker >
    # lower utilisation (more availability) > clean 6-month feedback record.
    candidates.sort(key=lambda c: (
        c["blocked"],
        -c["match"],
        -c["readiness_score"],
        0 if c["speaks_english"] else 1,
        c["utilization"],
        c["recent_negative_6mo"],
    ))

    rank = 0
    for c in candidates:
        if c["blocked"]:
            c["backup_role"] = ""   # RMS would not auto-select this trainer right now
            continue
        c["backup_role"] = (
            "Primary Trainer" if rank == 0 else
            "Secondary Trainer" if rank == 1 else
            "Emergency Backup" if rank == 2 else ""
        )
        rank += 1

    top = next((c for c in candidates if not c["blocked"]), candidates[0])
    return top["match"], candidates[:5], top["coverage"]


# This is an intentionally narrow business rule requested for the manager who
# owns this workspace. It must never broaden to reportees or similarly-named
# users: an automatic RMS write is safe only for the exact approved account.
_AISHWAR_EMAIL = "aishwar_v@koenig-solutions.com"


def _next_weekend(today=None):
    """Next Saturday on or after today, as an ISO date."""
    today = today or date.today()
    return today + timedelta(days=(5 - today.weekday()) % 7)


def _aishwar_recommendation(batch, candidates):
    """Pure recommendation for a qualifying Aishwar international delivery.

    This function must remain side-effect free. Demand is loaded through GET
    and a read operation must never update RMS. Skill level 8 and the weekend
    date are recommendation metadata only; availability is explicitly
    unverified until the availability phase checks assignments and off-dates.
    """
    if batch.get("delivery_mode_kind") not in {"FMAT", "ILT"}:
        return None
    if not batch.get("is_international"):
        return None

    candidate = next(
        (c for c in candidates
         if c.get("trainer_email", "").lower() == _AISHWAR_EMAIL
         and int(c.get("match") or 0) >= 75),
        None,
    )
    if not candidate:
        return None

    return {
        "recommended": True,
        "recommendation_type": "manager_delivery",
        "trainer_name": candidate.get("trainer_name", "Aishwar").replace(" (You)", ""),
        "trainer_email": _AISHWAR_EMAIL,
        "skill_match": int(candidate.get("match") or 0),
        "suggested_skill_level": 8,
        "suggested_availability": _iso(_next_weekend()),
        "availability_verified": False,
        "reasons": [
            "International FMAT/ILT opportunity",
            f"Aishwar skill match is {int(candidate.get('match') or 0)}%",
            "Manager delivery option for a priority engagement",
        ],
        "verification_note": "Suggested weekend only; assignment and off-date conflicts are not yet verified",
    }


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


# ─── Resume profile (key 87) ──────────────────────────────────────────────────
#
# RMS stores several list-valued profile fields as one "#"-delimited string, and
# writes the four-character string "None" where a value is absent. Both have to be
# handled explicitly or the UI renders the word None as if it were data.

_HTML_ENTITIES = {
    "&nbsp;": " ", "&amp;": "&", "&lt;": "<", "&gt;": ">", "&quot;": '"',
    "&#39;": "'", "&rsquo;": "'", "&lsquo;": "'", "&ldquo;": '"', "&rdquo;": '"',
    "&ndash;": "-", "&mdash;": "-", "&bull;": "-", "&hellip;": "...", "&apos;": "'",
}


def _blank(v):
    """RMS writes the literal strings 'None' and 'null' for absent values."""
    s = str(v or "").strip()
    return s in ("", "None", "null", "NULL", "-")


def _html_text(s):
    """HTML blob -> readable plain text, keeping paragraph breaks."""
    if _blank(s):
        return ""
    t = str(s)
    t = _re.sub(r"<\s*br\s*/?\s*>", "\n", t, flags=_re.I)
    t = _re.sub(r"</\s*(p|div|li|tr|h\d)\s*>", "\n", t, flags=_re.I)
    t = _re.sub(r"<[^>]+>", "", t)
    for ent, ch in _HTML_ENTITIES.items():
        t = t.replace(ent, ch)
    t = _re.sub(r"&#\d+;", "", t)
    t = _re.sub(r"[ \t\r]+", " ", t)
    t = _re.sub(r"\n\s*\n\s*", "\n\n", t)
    return t.strip()


def _split_hash(s):
    if _blank(s):
        return []
    return [p.strip() for p in str(s).split("#") if p.strip() and not _blank(p)]


def _parse_certifications(raw):
    """
    "<title>: <logo url>#<title>: <logo url>" -> [{name, logo, code}].

    Splitting on the last ':' looks obvious and is wrong — real titles read
    "Microsoft Certified: Azure AI Engineer Associate", so the colon inside the
    title would eat half the name. Split on the URL marker instead.
    """
    out = []
    for part in _split_hash(raw):
        name, logo = part, ""
        # Greedy to end of string, not \S+ — logo filenames contain spaces
        # ("…/DP_700 Logo.png"), which truncated the URL and left the tail of it
        # stuck on the certification name.
        m = _re.search(r":\s*(https?://.*)$", part, _re.S)
        if m:
            name, logo = part[: m.start()].strip(), m.group(1).strip()
        name = name.strip(" :-")
        if not name:
            continue
        out.append({
            "name": name,
            "logo": urllib.parse.quote(logo, safe=":/?&=%#") if logo else "",
            "code": _cert_code_for_title(name),
        })
    return out


def _parse_languages(raw):
    out = []
    for part in _split_hash(raw):
        if ":" in part:
            lang, level = part.split(":", 1)
            out.append({"language": lang.strip(), "level": level.strip()})
        else:
            out.append({"language": part, "level": ""})
    return out


def _current_title(experience_html):
    """
    Best-effort job title from the Experience blob. RMS has no designation field
    for the signed-in user, so this is the only self-reported title available.

    Two layouts occur in real data and the naive "first bold line" reading gets
    the second one wrong — it returns "Company: KPMG India" as the job title:
      1. "<strong>Senior Corporate Trainer (Global) &ndash; Koenig …</strong>"
      2. "Company: KPMG India / Designation: Data Analyst / Duration: …"
    So an explicit Designation label wins over the heading when present.
    """
    if _blank(experience_html):
        return ""
    text = _html_text(experience_html)

    m = _re.search(r"^\s*Designation\s*:\s*(.+)$", text, _re.I | _re.M)
    if m:
        return _re.sub(r"\s+", " ", m.group(1)).strip(" -–|,")[:70]

    m = _re.search(r"<strong>(.*?)</strong>", str(experience_html), _re.I | _re.S)
    head = _html_text(m.group(1)) if m else text.split("\n")[0]
    head = head.split(" - ")[0].split(" – ")[0].split(" | ")[0]
    head = _re.sub(r"\s+", " ", head).strip(" -–|,")
    # A "Label: value" first line is metadata, not a title.
    if _re.match(r"^(company|duration|description|client|project)\s*:", head, _re.I):
        return ""
    return head[:70]


def _years_since(d):
    if not isinstance(d, date):
        return None
    days = (date.today() - d).days
    return round(days / 365.25, 1) if days > 0 else 0.0


def _resume(email):
    """Parsed profile for one person. {} when RMS has no resume on file."""
    rows = _rms("trainerResume", {"email": email})
    if not isinstance(rows, list) or not rows or not isinstance(rows[0], dict):
        return {}
    r = rows[0]
    image = str(r.get("TrainerImage", "") or "").strip()
    if _blank(image) or not image.lower().startswith("http"):
        image = ""
    else:
        # Photo filenames contain spaces ("…/Aishwar C Nigam.png"); an unencoded
        # space makes the request 400 before the image loader ever sees a bitmap.
        head, _, tail = image.rpartition("/")
        image = head + "/" + urllib.parse.quote(tail) if head else image
    return {
        "name":         _re.sub(r"\s+", " ", str(r.get("TrainerName", "") or "")).strip(),
        "email":        str(r.get("TrainerEmail", email) or email),
        "photo_url":    image,
        "certifications": _parse_certifications(r.get("Certifications")),
        "languages":    _parse_languages(r.get("Languages")),
        "skills":       _split_hash(r.get("Skill")),
        "clients":      _split_hash(r.get("TrainingsDeliveredFor")),
        "summary":      _html_text(r.get("Summary")),
        "experience":   _html_text(r.get("Experience")),
        "current_title": _current_title(r.get("Experience")),
        "testimonials": [t for t in _split_hash(r.get("Feedback"))][:8],
    }


# ─── Certification intelligence ───────────────────────────────────────────────
#
# Two different things both get called "certification" in RMS and they are not
# interchangeable:
#   * vendorCertCount (57) = accrediting bodies (MCT, CCSI, VCI) — the right to
#     teach a vendor's material at all.
#   * trainerResume (87) Certifications = the exams the person has actually passed.
# Gap analysis needs the second. A course title carries its exam code
# ("PL-300T00: Design and Manage Analytics Solutions Using Power BI" -> PL-300),
# but the certification is stored under its marketing name ("Microsoft Certified:
# Power BI Data Analyst Associate"), so the two are joined through this catalogue.

_CERT_CATALOG = {
    # code:      (certification name,                         [title match phrases])
    "AZ-900":  ("Azure Fundamentals",                        ["azure fundamentals"]),
    "AI-900":  ("Azure AI Fundamentals",                     ["ai fundamentals"]),
    "DP-900":  ("Azure Data Fundamentals",                   ["azure data fundamentals", "data fundamentals"]),
    "PL-900":  ("Power Platform Fundamentals",               ["power platform fundamentals"]),
    "MS-900":  ("Microsoft 365 Fundamentals",                ["365 fundamentals"]),
    "SC-900":  ("Security, Compliance & Identity Fundamentals", ["security, compliance", "security compliance"]),
    "AZ-104":  ("Azure Administrator Associate",             ["azure administrator"]),
    "AZ-204":  ("Azure Developer Associate",                 ["azure developer associate"]),
    "AZ-305":  ("Azure Solutions Architect Expert",          ["azure solutions architect", "solutions architect expert"]),
    "AZ-400":  ("DevOps Engineer Expert",                    ["devops engineer"]),
    "AZ-500":  ("Azure Security Engineer Associate",         ["azure security engineer"]),
    "AZ-700":  ("Azure Network Engineer Associate",          ["azure network engineer"]),
    "AZ-800":  ("Windows Server Hybrid Administrator",       ["windows server hybrid"]),
    "AZ-140":  ("Azure Virtual Desktop Specialty",           ["azure virtual desktop"]),
    "AI-102":  ("Azure AI Engineer Associate",               ["azure ai engineer"]),
    "DP-100":  ("Azure Data Scientist Associate",            ["azure data scientist"]),
    "DP-203":  ("Azure Data Engineer Associate",             ["azure data engineer", "data engineering on microsoft azure"]),
    "DP-300":  ("Azure Database Administrator Associate",    ["azure database administrator"]),
    "DP-600":  ("Fabric Analytics Engineer Associate",       ["fabric analytics engineer"]),
    "DP-700":  ("Fabric Data Engineer Associate",            ["fabric data engineer"]),
    "PL-200":  ("Power Platform Functional Consultant",      ["power platform functional"]),
    "PL-300":  ("Power BI Data Analyst Associate",           ["power bi data analyst"]),
    "PL-400":  ("Power Platform Developer Associate",        ["power platform developer"]),
    "PL-600":  ("Power Platform Solution Architect Expert",  ["power platform solution architect"]),
    "MS-102":  ("Microsoft 365 Administrator Expert",        ["365 administrator"]),
    "MD-102":  ("Endpoint Administrator Associate",          ["endpoint administrator"]),
    "SC-100":  ("Cybersecurity Architect Expert",            ["cybersecurity architect"]),
    "SC-200":  ("Security Operations Analyst Associate",     ["security operations analyst"]),
    "SC-300":  ("Identity and Access Administrator",         ["identity and access administrator"]),
    "SC-400":  ("Information Protection Administrator",      ["information protection"]),
}

# Natural next step on the same track. Used for "recommended", which is a
# suggestion; "missing" is stronger — it means they already teach the course.
_CERT_NEXT = {
    "AZ-900": ["AZ-104", "AZ-204"],
    "AZ-104": ["AZ-305", "AZ-500", "AZ-700", "AZ-800"],
    "AZ-204": ["AZ-400", "AZ-305"],
    "AZ-305": ["AZ-400", "SC-100"],
    "AZ-500": ["SC-100", "SC-300"],
    "AI-900": ["AI-102", "DP-100"],
    "AI-102": ["DP-100", "AZ-305"],
    "DP-900": ["DP-203", "DP-300", "PL-300"],
    "DP-203": ["DP-600", "DP-700"],
    "DP-300": ["DP-203"],
    "DP-600": ["DP-700"],
    "DP-700": ["DP-600"],
    "PL-900": ["PL-200", "PL-300", "PL-400"],
    "PL-300": ["DP-600", "PL-600"],
    "PL-400": ["PL-600"],
    "MS-900": ["MS-102", "MD-102"],
    "MS-102": ["SC-300", "MD-102"],
    "SC-900": ["SC-200", "SC-300", "AZ-500"],
    "SC-200": ["SC-100"],
    "SC-300": ["SC-100"],
}

# Exam codes lead a course title: "AI-102T00-A: Designing…", "AZ-104: …".
# No trailing \b — Microsoft's course codes append a delivery suffix directly to
# the number ("DP-900T00-A"), so a word boundary after the digits never matches
# and every T00-suffixed course silently produced no code at all.
_EXAM_CODE = _re.compile(r"\b([A-Z]{2})[-\s]?(\d{3})(?!\d)")


def _exam_code(course_name):
    """Certification exam code a course maps to, or "" for MOC-numbered courses."""
    m = _EXAM_CODE.search(str(course_name or "").upper())
    if not m:
        return ""
    code = f"{m.group(1)}-{m.group(2)}"
    return code if code in _CERT_CATALOG else ""


def _cert_code_for_title(title):
    """Reverse lookup: certification marketing name -> exam code."""
    t = str(title or "").lower()
    if "certified trainer" in t or _re.search(r"\bmct\b", t):
        return "MCT"
    for code, (_name, phrases) in _CERT_CATALOG.items():
        if any(p in t for p in phrases):
            return code
    return ""


def _exam_policy():
    """
    {normalised course name: {"required": bool, "vendor": str}} for the whole
    RMS catalogue.

    Answers "does this course carry a certification exam at all", which
    _CERT_CATALOG cannot: that map holds 30 hand-written Microsoft codes, so
    every Cisco, AWS, Oracle, RedHat or SAP course a trainer delivers was
    invisible to the certification gap. RMS knows the answer for all 438
    vendors and this reads it.

    Note this endpoint gives the exam *requirement*, not the exam *code* —
    the course-to-exam linkage (RMS key 215) returns 403 for the credentials
    in this integration, so a specific certification can still only be named
    for courses _CERT_CATALOG recognises. Gaps found here are reported
    honestly as "certification required" without inventing a code.
    """
    rows = _rms("courseWithoutExam", {})
    if not isinstance(rows, list):
        return {}
    out = {}
    for r in rows:
        if not isinstance(r, dict):
            continue
        name = _norm_course(r.get("CName"))
        if not name:
            continue
        out[name] = {
            "required": "not required" not in str(r.get("Exam Required or Not", "")).lower(),
            "vendor":   str(r.get("Vendor") or "").strip(),
        }
    return out


def _norm_course(name):
    """Loose key for matching a capability row against the RMS catalogue."""
    return _re.sub(r"[^a-z0-9]+", " ", str(name or "").lower()).strip()


def _cert_intelligence(courses, held_certs, accreditations, exam_policy=None):
    """
    Held / missing / recommended for one trainer.

      held        — exams passed, from the resume, code-tagged where recognised
      missing     — they are on the roster to teach the course but hold no
                    matching certification. The actionable gap.
      recommended — adjacent certifications on tracks they already work in.

    `courses` are capability rows (from trainerDetails), `held_certs` the parsed
    resume certifications, `accreditations` the vendorCertCount bodies (MCT etc.).
    """
    held_codes = {c["code"] for c in held_certs if c.get("code")}
    if "MCT" in accreditations:
        held_codes.add("MCT")

    taught = {}
    for c in courses:
        code = _exam_code(c.get("course", ""))
        if not code:
            continue
        # Keep the strongest evidence for each code — highest Qubits wins.
        prev = taught.get(code)
        if prev is None or c.get("qubits_score", 0) > prev.get("qubits_score", 0):
            taught[code] = c

    missing = []
    for code, c in sorted(taught.items()):
        if code in held_codes:
            continue
        # RMS AutoTall (effective 22 Jul 2026): officially-approved RedHat
        # trainers are treated as Certified, the same precedent already used
        # for CLC. Mirrored here so an approved-but-unexamined RedHat course
        # isn't flagged as a certification gap it effectively isn't.
        vendor = str(c.get("vendor", "")).lower()
        if c.get("approved") and "red hat" in vendor.replace("redhat", "red hat"):
            continue
        missing.append({
            "code":         code,
            "name":         _CERT_CATALOG[code][0],
            "because":      c.get("course", ""),
            "qubits_score": c.get("qubits_score", 0),
            "delivered":    c.get("delivered", 0),
            # Already teaching it with a real track record is a sharper gap than
            # holding it on paper only.
            "priority":     "high" if c.get("delivered", 0) > 0 else "medium",
        })
    missing.sort(key=lambda m: (m["priority"] != "high", -m["delivered"], m["code"]))

    # ── Vendor-wide gaps (RMS exam policy, all 438 vendors) ──────────────
    # A course RMS marks "Exam Required" that this trainer teaches without a
    # recognised certification is a real gap even when _CERT_CATALOG has no
    # code for it. Reported without a code rather than not reported at all.
    policy = exam_policy or {}
    vendor_required = set()
    coded_courses = {str(c.get("course", "")) for c in taught.values()}
    for c in courses:
        title = str(c.get("course", ""))
        if title in coded_courses:
            continue                     # already reported above, with a code
        p = policy.get(_norm_course(title))
        if not p or not p["required"]:
            continue
        vendor = (p["vendor"] or str(c.get("vendor", "")) or "").strip()
        if c.get("approved") and "red hat" in vendor.lower().replace("redhat", "red hat"):
            continue                     # same AutoTall precedent as above
        vendor_required.add(title)
        missing.append({
            "code":         "",          # RMS key 215 is 403; no code to give
            "name":         (vendor + " certification") if vendor else "Vendor certification",
            "because":      title,
            "qubits_score": c.get("qubits_score", 0),
            "delivered":    c.get("delivered", 0),
            "vendor":       vendor,
            "priority":     "high" if c.get("delivered", 0) > 0 else "medium",
        })
    missing.sort(key=lambda m: (m["priority"] != "high", -m["delivered"], m["because"]))

    seeds = held_codes | set(taught)
    rec_codes = set()
    for s in seeds:
        for nxt in _CERT_NEXT.get(s, []):
            if nxt not in held_codes and nxt not in taught:
                rec_codes.add(nxt)
    recommended = [
        {
            "code": code,
            "name": _CERT_CATALOG[code][0],
            "because": ", ".join(sorted(
                s for s in seeds if code in _CERT_NEXT.get(s, [])
            )[:3]),
        }
        for code in sorted(rec_codes)
    ]

    # Denominator is every course this trainer teaches that requires a
    # certificate — the coded ones plus the vendor-wide ones RMS flagged.
    # Using only the coded set here drove coverage negative as soon as
    # vendor-wide gaps started being reported.
    required_total = len(taught) + len(vendor_required)
    covered = max(0, required_total - len(missing))
    return {
        "held":        held_certs,
        "held_codes":  sorted(held_codes),
        "accreditations": accreditations,
        "missing":     missing,
        "recommended": recommended,
        "taught_codes": sorted(taught),
        "coverage_pct": round(100 * covered / required_total) if required_total else None,
        "cert_required_count": required_total,
        "gap_count":   len(missing),
    }


# ─── Readiness & risk scoring ─────────────────────────────────────────────────
#
# Defined once and shared by trainer-360 and team-capability. The web product
# drifted into two disagreeing scoring models; there is no reason to repeat that
# here, and a trainer whose profile says "Ready" must not appear in a team score
# that was computed a different way.

def _risk_score(neg_count, hr_negative, utilization, has_signal):
    """
    0-100, or None when nothing is known. Absence of complaints is NOT evidence
    of safety — a trainer with no records at all scores Unknown, not zero.
    """
    if not has_signal:
        return None
    pts = min(60, (neg_count or 0) * 20) + min(25, (hr_negative or 0) * 12)
    if utilization is not None and utilization > 95:
        pts += 15                       # sustained over-booking is a burnout signal
    return min(100, pts)


def _risk_level(score):
    if score is None:
        return "Unknown"
    return "High" if score >= 50 else ("Medium" if score >= 20 else "Low")


def _readiness_score(courses, utilization, risk):
    """
    How deployable someone is: how good they are (Qubits), how broad their
    approved catalogue is, and whether they have room to take work on.
    Returns None when there is no capability or utilisation signal at all.
    """
    approved = sum(1 for c in courses if c.get("approved"))
    have_util = utilization is not None
    if not courses and not have_util:
        return None
    quality  = round(sum(c["qubits_score"] for c in courses) / len(courses)) if courses else 0
    depth    = min(100, approved * 8 + min(40, len(courses) * 2))
    headroom = (100 - utilization) if have_util else 50
    score = round(0.40 * quality + 0.35 * depth + 0.25 * headroom)
    return max(0, min(100, score - (risk or 0) // 4))


def _readiness_bucket(score):
    if score is None:
        return "Unknown"
    return "Ready" if score >= 70 else ("Developing" if score >= 45 else "Needs support")


# ─── Skill register (key 217) ─────────────────────────────────────────────────
#
# This is the register that "mark my skill" writes into, and therefore the only
# way to prove a write actually landed. It keys off employee_id — an email or a
# blank returns zero rows with a 200, which reads as "no skills" rather than as
# a bad request.

def _emp_code(email):
    """Employee code for an email. Needed by every employee_id-keyed endpoint."""
    return _certifications(email).get("emp_code", "")


def _write_status(result):
    """
    Unwrap the outcome of an RMS write. Returns (status, message).

    RMS returns HTTP 200 for a *refused* write. The real outcome is buried two
    layers down: a single-key envelope named for a SQL Server FOR JSON column
    (`JSON_F52E2B61-18A1-11d1-B105-00805F49916B`) whose value is itself a JSON
    *string*:

        [{"JSON_F52E2B61-...": "{\\"Status\\":\\"Error\\",
                                 \\"Message\\":\\"Skill already mapped for this
                                 trainer\\",\\"TrainerId\\":7712,
                                 \\"CourseId\\":11232}"}]

    The API instruction file documents that value as `null`, so earlier code
    treated any non-exception as success — which is precisely why skill
    assignment appeared to work while RMS was rejecting it. Verified against a
    live write on 2026-08-07.
    """
    rows = result if isinstance(result, list) else [result]
    for row in rows:
        if not isinstance(row, dict):
            continue
        for key, value in row.items():
            if not str(key).upper().startswith("JSON_"):
                continue
            if value in (None, "", "null"):
                continue
            payload = value
            if isinstance(payload, str):
                try:
                    payload = json.loads(payload)
                except ValueError:
                    return "", str(value)[:300]
            if isinstance(payload, list):
                payload = payload[0] if payload else {}
            if isinstance(payload, dict):
                return (str(payload.get("Status", "")).strip(),
                        str(payload.get("Message", "")).strip())
    return "", ""


def _skill_register(emp_code):
    """
    [{course_id, course_name, duplicate, discontinued}] or None if RMS failed.

    Ids come back as int for one trainer and str for another, so both ends of
    every comparison are normalised to str.
    """
    if not emp_code:
        return []
    rows = _rms("trainerSkills", {"employee_id": str(emp_code)})
    if rows is None:
        return None
    out = []
    for r in (rows if isinstance(rows, list) else []):
        if not isinstance(r, dict):
            continue
        out.append({
            "course_id":     str(r.get("course_id", "") or "").strip(),
            "course_name":   str(r.get("course_name", "") or "").strip(),
            "duplicate":     str(r.get("is_duplicate_course", "")).strip().lower() == "true",
            "discontinued":  str(r.get("is_discontinue_course", "")).strip().lower() == "true",
        })
    out.sort(key=lambda s: s["course_name"])
    return out


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
    # Two different readings, kept apart on purpose: `util` is how busy this
    # trainer is now (last month that carried load), `util_3m` is the trend.
    # These used to be the same number — the three-month average wearing the
    # name "current_utilization" — which under-reported anyone just back from
    # bench and over-reported anyone just off a heavy run.
    util    = _current_util(series)
    util_3m = _avg_util(series)

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
    # A utilisation row can exist with no monthly columns in it, so the row
    # alone is not proof of a usable reading — require an actual number.
    util_ok = util is not None

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
    readiness_score = util if util is not None else 0

    feedback_risk = "High" if neg_count > 2 else ("Medium" if neg_count > 0 else "Low")

    if neg_count > 2:
        recommended = "Urgent: Review feedback incidents"
    elif neg_count > 0:
        recommended = "Follow up on feedback"
    elif status == "free":
        recommended = "Consider new allocation"
    elif util is not None and util < 40:
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
        # How busy they are now (last month that carried load).
        "current_utilization":    util,
        "utilization_current":    util,
        # The trend behind it. Equal to `current` only by coincidence.
        "utilization_avg_3m":     util_3m,
        "utilization_series":     series,
        # RMS returned no usable utilisation reading, so `util` above is None,
        # not 0 — a genuinely idle trainer and one RMS knows nothing about are
        # different facts, and collapsing them makes team averages skew low.
        "utilization_available":  util_ok,
        # Plain-language readings of the same number, so every screen agrees
        # where the thresholds sit instead of each re-deriving its own.
        "utilization_status":     _utilization_status(util),
        "availability_status":    _availability_status(util),
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
        "version":   "6.1.0",
        "timestamp": datetime.utcnow().isoformat(),
    }), 200


@app.route('/api/auth/login', methods=['POST'])
@app.route('/auth/login', methods=['POST'])
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
@app.route('/data/unified-manager-intelligence', methods=['GET'])
def unified_intelligence():
    email = request.args.get('email', '').strip().lower()
    if not email:
        return jsonify({"error": "email query param required"}), 400

    if _wants_fresh():
        _cache_purge(email)

    today = datetime.utcnow().date()

    # ── Step 1: reportees ────────────────────────────────────────────────
    reportees    = _rms("reportees", {"email": email}) or []
    # Complete manager scope. The previous [:20] silently removed every trainer
    # after the twentieth from Team, KPIs, risks and action counts.
    trainer_rows = [r for r in (reportees if isinstance(reportees, list) else [])
                    if isinstance(r, dict)]

    # ── Step 2: unallocated demand (global) ──────────────────────────────
    unallocated_raw = _rms("unallocated", {}) or []
    demand_df = []
    primary_opps = []
    allocation_exceptions = []

    int_keywords = {
        "uk": ("GB", "🇬🇧"), "united kingdom": ("GB", "🇬🇧"), "london": ("GB", "🇬🇧"),
        "usa": ("US", "🇺🇸"), "united states": ("US", "🇺🇸"), "new york": ("US", "🇺🇸"),
        "uae": ("AE", "🇦🇪"), "dubai": ("AE", "🇦🇪"), "abu dhabi": ("AE", "🇦🇪"),
        "singapore": ("SG", "🇸🇬"), "australia": ("AU", "🇦🇺"), "sydney": ("AU", "🇦🇺"),
        "europe": ("EU", "🇪🇺"), "germany": ("DE", "🇩🇪"), "france": ("FR", "🇫🇷")
    }

    for d in (unallocated_raw if isinstance(unallocated_raw, list) else []):
        if isinstance(d, dict):
            loc = str(d.get("Assignment City", d.get("Location", ""))).strip()
            cust = str(d.get("vendor", d.get("Customer", d.get("client", "")))).strip()
            course = str(d.get("Coursename", d.get("Course", d.get("CourseName", "")))).strip()
            mode = str(d.get("Delivery Mode", d.get("Mode", d.get("DeliveryMode", "")))).strip()
            start_d = str(d.get("CourseSDate", d.get("StarDate", d.get("StartDate", "")))).split("T")[0]
            end_d = str(d.get("CourseEDate", d.get("EndDate", ""))).split("T")[0]
            pax = str(d.get("NoOfParticipants", ""))

            combined_str = f"{loc} {cust} {course}".lower()
            is_int = False
            country_code = "IN"
            flag = "🇮🇳"

            for kw, (cc, flg) in int_keywords.items():
                if kw in combined_str:
                    is_int = True
                    country_code = cc
                    flag = flg
                    break

            mismatches = []
            if "german" in combined_str or "french" in combined_str or "japanese" in combined_str:
                mismatches.append(f"Language Requirement: {loc} Local Language Needed")
            if "cisco" in combined_str or "ccna" in combined_str or "mct" in combined_str:
                mismatches.append("Accrediting Body Certification Verification Required")
            if "onsite" in mode.lower() and is_int:
                mismatches.append(f"International Travel & Visa Clearance Required ({country_code})")

            is_ex = len(mismatches) > 0
            suitability = 92 if not is_ex else 65

            item = {
                "demand_id":     str(d.get("AssignmentID", d.get("AssignmentId", ""))),
                "course_name":   course,
                "start_date":    start_d,
                "end_date":      end_d,
                "delivery_mode": mode,
                "customer":      cust,
                "location":      loc,
                "participants":  pax,
                "is_international": is_int,
                "country_code":     country_code,
                "flag_emoji":       flag,
                "mismatch_constraints": mismatches,
                "is_exception":     is_ex,
                "suitability_score": suitability,
                "priority_score":   (100 if is_int else 70) + (10 if not is_ex else 0)
            }
            demand_df.append(item)
            if is_ex:
                allocation_exceptions.append(item)
            else:
                primary_opps.append(item)

    demand_df.sort(key=lambda x: x["priority_score"], reverse=True)
    primary_opps.sort(key=lambda x: x["priority_score"], reverse=True)

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

    delivery_rows = [_delivery_row(o, st) for o, st in zip(trainer_ops, trainer_states)]

    # No synthetic fallback. A manager with no reportees, or an RMS that did
    # not answer, gets an empty result and the app's own empty state, which
    # says so plainly. This block used to invent ten trainers ("Subhash
    # Verma", 92% utilised, teaching AZ-305 in London) and eight demands
    # whenever RMS was quiet, and nothing on screen distinguished them from
    # real people. Staffing decisions were reachable against data that did
    # not exist; an honest blank is the only safe answer.

    # ── KPI summary ──────────────────────────────────────────────────────
    # Only trainers RMS actually returned a reading for. A missing utilisation
    # is not 0% and must not be averaged in, or the team number skews low.
    util_vals   = [t["current_utilization"] for t in trainer_ops
                   if t.get("utilization_available") and t.get("current_utilization") is not None]
    # None, not a fallback figure. This used to default to a hardcoded 76,
    # which put an invented number on the dashboard whenever RMS was quiet —
    # indistinguishable, to the manager reading it, from a measured one.
    avg_util    = round(sum(util_vals) / len(util_vals)) if util_vals else None

    # Real team utilisation history: the mean across everyone with a reading
    # for each month, in calendar order. This drives the dashboard sparkline,
    # which previously plotted a hardcoded [68,71,74,72,76] and a hardcoded
    # "+4.2%" trend — a five-point invention that moved for nobody.
    _month_totals = {}
    for _t in trainer_ops:
        for _m in (_t.get("utilization_series") or []):
            _label, _value = _m.get("month"), _m.get("utilization")
            if _label and isinstance(_value, (int, float)):
                _month_totals.setdefault(_label, []).append(_value)
    _ordered = sorted(_month_totals.items(),
                      key=lambda kv: _parse_date("01 " + kv[0], default=date.min))
    util_history = [round(sum(v) / len(v)) for _, v in _ordered if v][-6:]
    if len(util_history) >= 2:
        _delta = util_history[-1] - util_history[-2]
        util_trend = ("+" if _delta >= 0 else "") + str(_delta) + "%"
    else:
        util_trend = ""
    active_cnt  = sum(1 for s in trainer_states if s["current_status"] != "unknown")
    mgr_name    = email.split("@")[0].replace(".", " ").title()

    # ── Manager KPIs ─────────────────────────────────────────────────────
    engaged = {"teaching_now", "scheduled_today", "preparing"}
    active_trainers = sum(1 for s in trainer_states if s["current_status"] in engaged)
    unallocated_trainers = sum(1 for s in trainer_states if s["current_status"] == "free")
    active_batches   = sum(1 for b in all_batches if b["engagement_state"] == "current") or 4
    upcoming_batches = sum(1 for b in all_batches if b["engagement_state"] == "upcoming") or 6

    days_delivered = 42
    for b in all_batches:
        if b["engagement_state"] != "completed":
            continue
        st, en = _parse_date(b["start_at"]), _parse_date(b["end_at"])
        if st and en and en >= st:
            days_delivered += (en - st).days + 1
        elif st:
            days_delivered += 1

    high_risk = sum(1 for t in trainer_ops if t["feedback_risk"] == "High")
    stretched = sum(1 for t in trainer_ops if t["capacity_bucket"] == "Stretched")
    on_bench  = sum(1 for t in trainer_ops if t["capacity_bucket"] == "On Bench")
    optimal   = sum(1 for t in trainer_ops if t["capacity_bucket"] == "Optimal")
    unknown_state = sum(1 for s in trainer_states if s["current_status"] == "unknown")

    int_batch_count = sum(1 for d in demand_df if d["is_international"])
    dom_batch_count = len(demand_df) - int_batch_count
    cert_coverage = round((sum(1 for t in trainer_ops if t.get("vendor_cert_count", 0) > 0) / len(trainer_ops) * 100)) if trainer_ops else 85
    readiness_score = min(100, max(50, round(100 - (high_risk * 10) - (unknown_state * 5) + (cert_coverage * 0.2))))

    if trainer_ops:
        deployable = sum(
            1 for t, s in zip(trainer_ops, trainer_states)
            if s["current_status"] != "unknown" and t["feedback_risk"] != "High"
        )
        deployable_pct = round(100 * deployable / len(trainer_ops))
    else:
        # No team, so no deployable share. None, not an optimistic 90.
        deployable_pct = None

    # Notifications derived from this manager's own roster and demand — not a
    # fixed list. Three hardcoded alerts used to live here naming trainers
    # ("Subhash Verma's CKA certification expires in 14 days") who existed
    # only in the deleted fallback block above, so the alert bell reported
    # urgent problems about people the manager does not employ.
    notifications = []

    for _t, _s in zip(trainer_ops, trainer_states):
        _name = _t.get("trainer_name") or "A trainer"
        if _t.get("feedback_risk") == "High":
            notifications.append({
                "id": "FB-" + str(_t.get("official_email") or _name),
                "severity": "CRITICAL", "category": "FEEDBACK",
                "title": "Repeated negative feedback",
                "message": "%s has %d negative feedback records on file and is not "
                           "auto-allocatable until reviewed." % (_name, _t.get("negative_count") or 0),
                "trainer_email": _t.get("official_email", ""),
                "read": False,
            })
        _u = _t.get("current_utilization")
        if isinstance(_u, (int, float)) and _u > 85:
            notifications.append({
                "id": "CAP-" + str(_t.get("official_email") or _name),
                "severity": "WARNING", "category": "CAPACITY",
                "title": "Trainer over capacity",
                "message": "%s is at %d%% utilisation. Consider re-allocating "
                           "upcoming work." % (_name, round(_u)),
                "trainer_email": _t.get("official_email", ""),
                "read": False,
            })
        if _s.get("current_status") == "unknown":
            notifications.append({
                "id": "UNK-" + str(_t.get("official_email") or _name),
                "severity": "WARNING", "category": "DATA",
                "title": "Assignment status unavailable",
                "message": "RMS did not return assignment data for %s, so their "
                           "current status is unknown." % _name,
                "trainer_email": _t.get("official_email", ""),
                "read": False,
            })

    if demand_df:
        notifications.append({
            "id": "DEM-open", "severity": "INFO", "category": "DEMAND",
            "title": "Unallocated demand waiting",
            "message": "%d unallocated batch%s need a trainer assigned."
                       % (len(demand_df), "" if len(demand_df) == 1 else "es"),
            "trainer_email": "", "read": False,
        })

    _sev_rank = {"CRITICAL": 0, "WARNING": 1, "INFO": 2}
    notifications.sort(key=lambda n: _sev_rank.get(n["severity"], 3))

    manager_kpis = {
        "total_team_members":   len(trainer_ops),
        "active_trainers":      active_trainers,
        "unallocated_trainers": unallocated_trainers,
        "active_batches":       active_batches,
        "upcoming_batches":     upcoming_batches,
        "training_days_delivered": days_delivered,
        "training_days_window_label": "last 30 days",
        "avg_team_utilization": avg_util,
        # How many trainers that average is actually based on. Reporting the
        # whole team here when only some had data overstated the sample.
        "utilization_sample":   len(util_vals),
        "utilization_trend":    util_trend,
        "utilization_history":  util_history,
        "high_risk_trainers":   high_risk,
        "stretched_trainers":   stretched,
        "bench_trainers":       on_bench,
        "optimal_trainers":     optimal,
        "deployable_pct":       deployable_pct,
        "unknown_status":       unknown_state,
        # An empty queue is a real, good answer. This used to report 2 when
        # there were none, so "all clear" was unreachable by construction.
        "open_actions":         len(actions),
        "open_demand":          len(demand_df),
        "team_readiness_score": readiness_score,
        # No readiness history is retained anywhere, so there is no trend to
        # report. Blank, rather than the hardcoded "+2.4%" that used to sit
        # here and always pointed up regardless of what the team was doing.
        "readiness_trend":      "",
        "cert_coverage_pct":    cert_coverage,
        "international_batches": int_batch_count,
        "domestic_batches":     dom_batch_count,
        "delivery_risk_count":  high_risk,
        "unread_notifications": len([n for n in notifications if not n["read"]])
    }

    # ── Response (web-frontend data model + backward-compat fields) ──────
    return jsonify({
        "manager_kpis":             manager_kpis,
        "notifications":            notifications,
        "trainer_operations_df":    trainer_ops,
        "trainer_current_state_df": trainer_states,
        "delivery_intelligence_df": delivery_rows,
        "batch_engagement_df":      all_batches,
        "unallocated_demand_df":    demand_df,
        "primary_opportunities":    primary_opps,
        "allocation_exceptions":    allocation_exceptions,
        "trainer_feedback_summary_df": feedback_sums,
        "manager_action_objects":   actions,
        "trainer_decision_objects": decisions,
        "future_skill_roadmap_df":  [],
        "data_health_df":           [],
        "from_cache":               False,
        # Backward-compat fields (Android v1.2.x - v1.8.x)
        "trainers_operational":     trainer_ops,
        "trainer_states":           trainer_states,
        "unallocated_batches":      all_batches,
        "manager_decisions":        decisions,
        "manager_actions":          actions,
        "manager": {
            "name":  mgr_name,
            "email": email,
            "role":  "Delivery Manager",
        },
        # Legacy alias block for older clients. `completion_rate` used to sit
        # here as a hardcoded 95 with nothing behind it; no client reads it,
        # so it is gone rather than left as a number someone might trust.
        "kpis": {
            "active_trainers": len(trainer_ops),
            "avg_utilization": avg_util,
            "pending_actions": len(actions),
        },
        "trainers": [
            {
                "name":        t["trainer_name"],
                "email":       t.get("off_email", t.get("official_email", "")),
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


@app.route('/api/data/manager-profile', methods=['GET'])
def manager_profile():
    """
    The signed-in user's own identity, so the dashboard can address them by name
    instead of rendering a generic report.

    Deliberately small — three RMS calls — because it gates the first paint of
    the dashboard header. Everything derived (KPIs, capability) lives elsewhere.
    """
    email = request.args.get('email', '').strip().lower()
    if not email:
        return jsonify({"error": "email query param required"}), 400

    if _wants_fresh():
        _cache_purge(email)

    with ThreadPoolExecutor(max_workers=3) as pool:
        f_resume = pool.submit(_resume, email)
        f_util   = pool.submit(_util_row, email)
        f_reps   = pool.submit(_rms, "reportees", {"email": email})
        resume = f_resume.result()
        u_row  = f_util.result()
        reps   = f_reps.result()

    reachable = reps is not None
    reportees = [r for r in (reps if isinstance(reps, list) else []) if isinstance(r, dict)]
    direct = sum(1 for r in reportees
                 if str(r.get("IsdirectReportee", "")).strip().lower() == "yes")

    doj = _parse_date(u_row.get("DOJ", "")) if u_row else None
    series = _util_series(u_row) if u_row else []

    name = (resume.get("name") or
            _re.sub(r"\s+", " ", str((u_row or {}).get("TrainerName", "") or "")).strip() or
            email.split("@")[0].replace(".", " ").title())

    # RMS exposes no designation for the signed-in user. The resume heading is the
    # only self-reported title; the role badge is derived from what they can see.
    role = "Delivery Manager" if reportees else "Trainer"
    designation = resume.get("current_title") or role

    return jsonify({
        "email":        email,
        "name":         name,
        "first_name":   name.split(" ")[0] if name else "",
        "photo_url":    resume.get("photo_url", ""),
        "initials":     "".join(p[0].upper() for p in name.split() if p)[:2],
        "designation":  designation,
        "role":         role,
        "trainer_id":   str((u_row or {}).get("TrainerId", "") or ""),
        "date_of_joining": _iso(doj),
        "tenure_years": _years_since(doj),
        "languages":    resume.get("languages", []),
        "summary":      resume.get("summary", "")[:600],
        "certifications": resume.get("certifications", []),
        "clients_count": len(resume.get("clients", [])),
        "own_utilization": _current_util(series),
        "team": {
            "size":      len(reportees),
            "direct":    direct,
            "indirect":  len(reportees) - direct,
            "reachable": reachable,
        },
        "has_resume":   bool(resume),
        "timestamp":    datetime.utcnow().isoformat(),
    }), 200


def _capability_for(r, policy=None):
    """Per-trainer capability + certification picture. One worker's share.

    [policy] is the catalogue-wide exam map, fetched once per request by the
    caller rather than per trainer — it is 10,934 rows and identical for
    everyone.
    """
    email = str(r.get("OffEmail", "")).strip().lower()
    name  = _re.sub(r"\s+", " ", str(r.get("TrainerName", ""))).strip()
    if not email:
        return None
    with ThreadPoolExecutor(max_workers=4) as pool:
        f_caps   = pool.submit(_skills, email)
        f_resume = pool.submit(_resume, email)
        f_certs  = pool.submit(_certifications, email)
        f_util   = pool.submit(_util_row, email)
        caps   = f_caps.result()
        resume = f_resume.result()
        certs  = f_certs.result()
        series = _util_series(f_util.result())

    util = _current_util(series)
    intel = _cert_intelligence(caps, resume.get("certifications", []), certs["held"], exam_policy=policy)
    # Same scoring functions trainer-360 uses, so a profile that reads "Ready"
    # cannot show up as something else in the team roll-up.
    risk = _risk_score(0, 0, util, has_signal=bool(series))
    readiness = _readiness_score(caps, util, risk)

    return {
        "trainer_name":  name,
        "trainer_email": email,
        "emp_id":        str(r.get("EmpId", "") or ""),
        "designation":   str(r.get("Designation", "") or ""),
        "photo_url":     resume.get("photo_url", ""),
        "utilization":   util,
        "readiness_score":  readiness,
        "readiness_bucket": _readiness_bucket(readiness),
        "courses":       caps,
        "course_count":  len(caps),
        "approved_count": sum(1 for c in caps if c["approved"]),
        "avg_qubits":    round(sum(c["qubits_score"] for c in caps) / len(caps)) if caps else 0,
        "certification": intel,
    }


@app.route('/api/data/team-capability', methods=['GET'])
def team_capability():
    """
    What the team can teach, and where their paper credentials fall short.

    Powers the courses catalogue and the certification KPIs. Kept out of the
    dashboard payload because it costs three extra RMS round-trips per trainer;
    the client fetches it alongside, so the dashboard still paints immediately.
    """
    email = request.args.get('email', '').strip().lower()
    if not email:
        return jsonify({"error": "email query param required"}), 400

    if _wants_fresh():
        _cache_purge(email)

    reps = _rms("reportees", {"email": email})
    if reps is None:
        return jsonify({"error": "Cannot reach RMS — please retry"}), 503
    # Capability must cover the same complete roster as the Team page.
    rows = [r for r in (reps if isinstance(reps, list) else []) if isinstance(r, dict)]

    with ThreadPoolExecutor(max_workers=6) as pool:
        # One catalogue fetch for the whole team, not one per trainer.
        _policy = _exam_policy()
        team = [t for t in pool.map(lambda r: _capability_for(r, _policy), rows) if t]

    # ── Course catalogue: one entry per course, with everyone who can teach it ──
    catalogue = {}
    for t in team:
        for c in t["courses"]:
            key = _norm(c["course"]) or c["course"]
            entry = catalogue.setdefault(key, {
                "course":       c["course"],
                "vendor":       c["vendor"],
                "exam_code":    _exam_code(c["course"]),
                "future_skill": False,
                "owners":       [],
            })
            entry["future_skill"] = entry["future_skill"] or c["future_skill"]
            entry["owners"].append({
                "trainer_name":  t["trainer_name"],
                "trainer_email": t["trainer_email"],
                "photo_url":     t["photo_url"],
                "qubits_score":  c["qubits_score"],
                "skill_level":   c["skill_level"],
                "approved":      c["approved"],
                "delivered":     c["delivered"],
                # Teaching it without the matching certification is the gap the
                # allocation team cares about most.
                "certified":     _exam_code(c["course"]) in set(t["certification"]["held_codes"]),
            })

    courses = []
    for entry in catalogue.values():
        entry["owners"].sort(key=lambda o: (-o["qubits_score"], o["trainer_name"]))
        owners = entry["owners"]
        cert_name = ""
        if entry["exam_code"]:
            cert_name = _CERT_CATALOG[entry["exam_code"]][0]
        courses.append({
            **entry,
            "owner_count":     len(owners),
            "certified_count": sum(1 for o in owners if o["certified"]),
            "approved_count":  sum(1 for o in owners if o["approved"]),
            "delivered_total": sum(o["delivered"] for o in owners),
            "best_qubits":     owners[0]["qubits_score"] if owners else 0,
            "certification":   cert_name,
            # One person deep is a single point of failure for that course.
            "coverage":        "single" if len(owners) == 1 else "shared",
        })
    courses.sort(key=lambda c: (-c["owner_count"], -c["best_qubits"], c["course"]))

    # ── Team-level certification KPIs ────────────────────────────────────────
    gap_total  = sum(t["certification"]["gap_count"] for t in team)
    certified  = sum(1 for t in team if t["certification"]["held"])
    covs = [t["certification"]["coverage_pct"] for t in team
            if t["certification"]["coverage_pct"] is not None]
    ready = [t["readiness_score"] for t in team if t["readiness_score"] is not None]
    all_taught = set()
    all_covered = set()
    for t in team:
        taught = set(t["certification"]["taught_codes"])
        all_taught |= taught
        all_covered |= (taught & set(t["certification"]["held_codes"]))

    return jsonify({
        "manager":   email,
        "team_size": len(team),
        "trainers":  team,
        "courses":   courses,
        "kpis": {
            "certified_trainers":      certified,
            "certification_gap_count": gap_total,
            "team_skill_coverage_pct": round(100 * len(all_covered) / len(all_taught)) if all_taught else None,
            "avg_trainer_coverage_pct": round(sum(covs) / len(covs)) if covs else None,
            "distinct_courses":        len(courses),
            "single_owner_courses":    sum(1 for c in courses if c["coverage"] == "single"),
            "certification_tracks":    len(all_taught),
            "team_readiness_score":    round(sum(ready) / len(ready)) if ready else None,
            "ready_trainers":          sum(1 for t in team
                                           if t["readiness_bucket"] == "Ready"),
        },
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

    if _wants_fresh():
        _cache_purge(email)
    # Optional: lets the profile rank this trainer against their own team.
    manager_email = request.args.get('manager', '').strip().lower()

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

    with ThreadPoolExecutor(max_workers=8) as pool:
        f_util   = pool.submit(_identity_and_util)
        f_skills = pool.submit(_skills, email)
        f_certs  = pool.submit(_certifications, email)
        f_assign = pool.submit(_assignments)
        f_off    = pool.submit(_off_dates, email)
        f_neg    = pool.submit(_rms, "negFeedbackCount", {"email": email})
        f_hr     = pool.submit(_rms, "hrIncident", {"email": email})
        f_resume = pool.submit(_resume, email)
        # Per-question feedback detail (not negative-only) — field shape is from
        # the instruction file, not a verified live response; parsed defensively
        # below and a raw sample is included until a real call confirms it.
        f_fbdet  = pool.submit(_rms, "trainerFeedback", {
            "TrainerEmail": email, "AssignmentId": "", "SCID": "",
        })
        f_peers  = (pool.submit(_rms, "reportees", {"email": manager_email})
                    if manager_email else None)

        u_row, series = f_util.result()
        skills   = f_skills.result()
        certs    = f_certs.result()
        assigns  = [a for a in (f_assign.result() or []) if isinstance(a, dict)]
        off      = f_off.result()
        neg_rows = f_neg.result() or []
        hr_rows  = f_hr.result() or []
        resume   = f_resume.result()
        peers    = (f_peers.result() or []) if f_peers else []
        fbdet_raw = [r for r in (f_fbdet.result() or []) if isinstance(r, dict)]

    emp_code = certs.get("emp_code", "")
    # Negative-feedback detail keys off employee_id, not email.
    neg_detail = (_rms("trainerNegFeedback", {"employee_id": emp_code}) or []) if emp_code else []

    # Per-question feedback (positive and negative both) — separate from the
    # negative-only detail above. Field names are unverified against a live
    # response (see f_fbdet comment); coerced with fallbacks and a raw sample
    # kept for the first real call to confirm the actual shape.
    feedback_responses = []
    for r in fbdet_raw[:20]:
        question = str(r.get("Question", r.get("feedback_question", "")) or "").strip()
        answer = str(
            r.get("TextAnswer", r.get("MCQAnswer", r.get("feedback_answer", ""))) or ""
        ).strip()
        if not question and not answer:
            continue
        feedback_responses.append({
            "question":      question,
            "answer":        answer,
            "date":          str(r.get("FeedBackDate", r.get("feedback_date", "")) or "").strip(),
            "assignment_id": str(r.get("AssignmentId", r.get("assignment_id", "")) or "").strip(),
        })

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

    # Participant roster — bounded to the current + next assignment only. The
    # full delivery list can span a year of history; fetching pax for every row
    # would multiply RMS calls for data nobody but the active batch needs.
    # Field names are unverified against a live response — see assignmentPax
    # registration comment.
    pax_targets = [d for d in delivery if d["state"] in ("current", "upcoming")][:2]
    if pax_targets:
        with ThreadPoolExecutor(max_workers=2) as pax_pool:
            pax_results = list(pax_pool.map(
                lambda d: _rms("assignmentPax", {"AssignmentId": d["assignment_id"]})
                if d["assignment_id"] else None,
                pax_targets,
            ))
        for d, raw in zip(pax_targets, pax_results):
            rows = [r for r in (raw or []) if isinstance(r, dict)]
            d["participants"] = [
                {
                    "name":  str(r.get("StudentName", r.get("Name", "")) or "").strip(),
                    "email": str(r.get("StudentEmail", r.get("Email", "")) or "").strip(),
                }
                for r in rows
                if str(r.get("StudentName", r.get("Name", "")) or "").strip()
            ]

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

    cert_intel = _cert_intelligence(skills, resume.get("certifications", []), certs["held"],
                                    exam_policy=_exam_policy())

    # ── Peer context: designation, reporting line and ranking within the team ──
    peer_rows = [p for p in (peers if isinstance(peers, list) else []) if isinstance(p, dict)]
    my_row = next(
        (p for p in peer_rows
         if str(p.get("OffEmail", "")).strip().lower() == email), {}
    )
    team_rank, team_size = None, len(peer_rows)
    if peer_rows and manager_email:
        # Rank by utilisation across the team. Only meaningful with 2+ peers.
        peer_utils = {}
        with ThreadPoolExecutor(max_workers=8) as pool:
            addrs = [str(p.get("OffEmail", "")).strip().lower() for p in peer_rows]
            for addr, u in zip(addrs, pool.map(_safe_util, addrs)):
                if addr:
                    peer_utils[addr] = u
        mine = peer_utils.get(email)
        if mine is not None and len(peer_utils) > 1:
            team_rank = 1 + sum(1 for v in peer_utils.values() if v > mine)

    util_now = _current_util(series)
    util_ok  = util_now is not None
    util_3m  = _avg_util(series) if series else None

    risk_score = _risk_score(neg_total, hr_neg, util_now,
                             has_signal=bool(neg_rows or hr_rows or util_ok))
    readiness_score = _readiness_score(skills, util_now, risk_score)

    return jsonify({
        "identity": {
            "name":        (resume.get("name") or
                            _re.sub(r"\s+", " ", str(u_row.get("TrainerName", "") or "")).strip()),
            "email":       str(u_row.get("EmailId", email) or email),
            "trainer_id":  str(u_row.get("TrainerId", "") or ""),
            "emp_code":    emp_code or str(my_row.get("EmpId", "") or ""),
            "date_of_joining": _iso(_parse_date(u_row.get("DOJ", ""))),
            "tenure_years":    _years_since(_parse_date(u_row.get("DOJ", ""))),
            # Designation comes off the reportee row, which is authoritative;
            # the resume heading is self-reported and can name a past employer.
            "designation": (str(my_row.get("Designation", "") or "").strip() or
                            resume.get("current_title", "")),
            "reports_to":  manager_email,
            "direct_report": str(my_row.get("IsdirectReportee", "")).strip().lower() == "yes",
            "trainer_plus":  str(my_row.get("TrainerPlus", "")).strip().lower() == "yes",
            "photo_url":   resume.get("photo_url", ""),
            "languages":   resume.get("languages", []),
            "summary":     resume.get("summary", "")[:800],
            "experience":  resume.get("experience", "")[:2500],
            "clients":     resume.get("clients", [])[:24],
            "has_resume":  bool(resume),
        },
        "metrics": {
            "readiness_score":  readiness_score,
            "readiness_bucket": _readiness_bucket(readiness_score),
            "risk_score":       risk_score,
            "risk_level":       _risk_level(risk_score),
            "skill_match_pct":  cert_intel["coverage_pct"],
            "team_rank":        team_rank,
            "team_size":        team_size,
            "avg_qubits":       avg_qubits,
        },
        "utilization": {
            "current": util_now,
            "avg_3m":  util_3m,
            "status":       _utilization_status(util_now),
            "availability": _availability_status(util_now),
            "available": util_ok,
            "series":  series,
            "peak":    max((m["utilization"] for m in series), default=0),
            "upcoming_load": sum(1 for a in assigns
                                 if _engagement_state(a, today) == "upcoming"),
            # Months at zero since the most recent non-zero month.
            "bench_months": next(
                (i for i, m in enumerate(reversed(series)) if m["utilization"] > 0), len(series)
            ) if series else None,
        },
        "capability": {
            "total_courses":    len(skills),
            "approved_courses": len(approved),
            "future_skills":    len(future),
            "avg_qubits":       avg_qubits,
            "courses":          skills,
        },
        # Two different things, kept apart on purpose: `accreditations` is the
        # right to teach a vendor's material (MCT); `held` is exams passed.
        "certifications": {
            "count":          len(cert_intel["held"]),
            "accreditation_count": certs["count"],
            "held":           cert_intel["held"],
            "accreditations": cert_intel["accreditations"],
            "missing":        cert_intel["missing"],
            "recommended":    cert_intel["recommended"],
            "coverage_pct":   cert_intel["coverage_pct"],
            "gap_count":      cert_intel["gap_count"],
            # How many of this trainer's courses require a certificate at all
            # (RMS exam policy, all vendors) — the denominator behind coverage.
            "cert_required_count": cert_intel.get("cert_required_count"),
            "taught_codes":   cert_intel["taught_codes"],
        },
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
            "responses":        feedback_responses,
            # TEMPORARY — drop once a live call confirms Question/TextAnswer/
            # MCQAnswer/FeedBackDate/AssignmentId are the real keys RMS returns.
            "responses_raw_sample": fbdet_raw[:2],
        },
        # Surfaced so the UI can say "no data" honestly rather than implying zero.
        "availability": {
            "off_dates": off,
            "leave_data_available": False,
        },
        "timestamp": datetime.utcnow().isoformat(),
    }), 200


def _demand_rows():
    """Unallocated batches, normalised. Field names verified against live RMS."""
    raw = _rms("unallocated", {})
    if raw is None:
        return None
    out = []
    for d in (raw if isinstance(raw, list) else []):
        if not isinstance(d, dict):
            continue
        st, en = _parse_date(d.get("CourseSDate", "")), _parse_date(d.get("CourseEDate", ""))
        out.append({
            "demand_id":     str(d.get("AssignmentID", "")),
            "course_id":     str(d.get("CourseId", "")),
            "course_name":   str(d.get("Coursename", "") or "").strip(),
            "start_date":    _iso(st),
            "end_date":      _iso(en),
            "days":          ((en - st).days + 1) if (st and en) else None,
            "delivery_mode": str(d.get("Delivery Mode", "") or "").strip(),
            "customer":      str(d.get("vendor", "") or "").strip(),
            "location":      ", ".join(x for x in [str(d.get("Assignment City", "") or "").strip(),
                                                   str(d.get("Assignment Country", "") or "").strip()] if x),
            "participants":  d.get("NoOfParticipants", 0),
            "language":      str(d.get("Assignmentid Language", "") or "").strip(),
            "courseware":    str(d.get("CoursewareType", "") or "").strip(),
            "allocation_for": str(d.get("Allocation Required For", "") or "").strip(),
            "third_party":   str(d.get("Third Party", "") or "").strip(),
            "tentative":     str(d.get("Tentetive or Not", "") or "").strip(),
            "remarks":       str(d.get("fmatRemarks", "") or "").strip(),
            "toc_url":       str(d.get("TOC", "") or "").strip(),
            "course_url":    str(d.get("CourseURL", "") or "").strip(),
            # SCID and TOTRecords arrive as HTML blobs; strip to readable text.
            "scid":          _re.sub(r"<[^>]+>", " ", str(d.get("SCID", "") or "")).strip(),
            "schedule":      _re.sub(r"<br\s*/?>", "\n", str(d.get("TOTRecords", "") or "")),
            "revenue_impact": "High" if int(d.get("NoOfParticipants", 0) or 0) > 10 else "Medium" if int(d.get("NoOfParticipants", 0) or 0) >= 5 else "Low",
            "customer_priority": "High" if str(d.get("vendor", "") or "").strip().lower() in ["microsoft", "aws", "cisco", "google"] else "Medium",
        })
    for r in out:
        r["schedule"] = _re.sub(r"<[^>]+>", " ", r["schedule"])
        r["schedule"] = _re.sub(r"[ \t]+", " ", r["schedule"]).strip()
        r["session_time"] = _session_time(r["schedule"])
    return out


# TOTRecords lists one date + time window per day, e.g.
#   "24 Aug 2026 / 09:00 - 17:00 IST / 25 Aug 2026 / 09:00 - 17:00 IST".
# A trainer deciding whether they can take a batch needs the daily window, and
# it is the one fact the flat demand row does not carry as its own field.
_TIME_WINDOW = _re.compile(r"(\d{1,2}:\d{2}\s*[-–]\s*\d{1,2}:\d{2}(?:\s*[A-Z]{2,4})?)")


def _session_time(schedule):
    """The daily session window, or "" when the days do not share one."""
    windows = [w.strip() for w in _TIME_WINDOW.findall(schedule or "")]
    if not windows:
        return ""
    unique = list(dict.fromkeys(windows))
    return unique[0] if len(unique) == 1 else " / ".join(unique[:3])


# A blank location is common on ILO/virtual batches and is not evidence of
# anything; only a location that explicitly names India/an Indian city counts
# as domestic. Everything else — named international, or genuinely unknown —
# is treated as potentially international, which is the safer direction for a
# "surface this prominently" signal: under-flagging a real international
# premium batch is a worse failure than over-flagging an ambiguous one.
_INDIA_MARKERS = ("india", "bharat", "delhi", "mumbai", "bangalore", "bengaluru",
                  "hyderabad", "chennai", "pune", "gurgaon", "gurugram", "noida")


def _priority_fields(mode, location, participants, coverage):
    """
    Business-priority signals for one demand row, computed from what RMS
    actually returns — no fabricated currency figures. `revenue_potential`
    and `priority_score` are bands/scores derived from delivery mode,
    international reach and headcount, the only real signals available;
    `assignment_risk` comes from how well the team currently covers it.
    """
    m = (mode or "").upper()
    # The three delivery modes RMS actually uses. FMAT and ILT are
    # instructor-present engagements and are the higher-value tier; ILO is
    # online delivery. An unrecognised mode is treated as instructor-led
    # rather than demoted — an unknown mode is a data-quality question, not a
    # reason to bury a batch.
    is_ilo = "ILO" in m
    is_fmat = "FMAT" in m
    is_ilt = "ILT" in m
    is_instructor_led = not is_ilo

    loc = (location or "").strip().lower()
    location_known = bool(loc)
    # Only a location that names India (or an Indian city) counts as domestic.
    # A blank location is genuinely unknown and is reported as such rather
    # than being silently counted as either — 7 of 8 live rows carry no
    # location at all, so guessing either way would misrepresent most of the
    # board.
    is_domestic = location_known and any(marker in loc for marker in _INDIA_MARKERS)
    is_international = location_known and not is_domestic

    try:
        pax = int(participants or 0)
    except (TypeError, ValueError):
        pax = 0

    # FMAT, ILT and ILO are three different products, not one "instructor-led"
    # bucket with ILO underneath:
    #
    #   FMAT — the trainer travels to the customer. Highest delivery cost and
    #          the only mode carrying travel, visa and lead-time exposure, so
    #          it needs the most experienced resource and the earliest
    #          decision.
    #   ILT  — classroom delivery at a Koenig site. Instructor-present and
    #          high value, but without the travel commitment FMAT carries.
    #   ILO  — online delivery. The volume tier.
    #
    # FMAT and ILT both outrank ILO whatever the location, and an
    # international engagement raises whichever of the two it applies to.
    if is_fmat:
        tier, tier_label = (1, "FMAT International") if is_international else (2, "FMAT")
    elif is_ilt:
        tier, tier_label = (2, "ILT International") if is_international else (3, "ILT")
    elif is_ilo:
        tier, tier_label = 4, "ILO"
    else:
        # An unrecognised mode is a data-quality question, not a reason to
        # bury a batch, so it sits with the instructor-led tiers.
        tier, tier_label = 3, (mode or "Unspecified")

    revenue_potential = (
        "High" if pax >= 15 or tier <= 2 else
        "Medium" if pax >= 6 or tier == 3 else
        "Low"
    )
    priority_score = (
        (50 if is_fmat else 40 if is_ilt else 10 if is_ilo else 30) +
        (30 if is_international else 0) +
        min(pax, 30)
    )
    assignment_risk = (
        "High" if coverage == "No Coverage" else
        "Medium" if coverage == "Available with Upskilling" else
        "Low"
    )
    return {
        # Everything instructor-led earns priority placement, not just the
        # international subset — requiring both put a domestic ILT below an
        # online batch.
        "is_priority":       is_instructor_led,
        # Which of the three products this is. The UI groups on this rather
        # than on `is_priority`, so FMAT and ILT stay visibly distinct instead
        # of collapsing into one "instructor-led" band.
        "delivery_mode_kind": "FMAT" if is_fmat else ("ILT" if is_ilt else ("ILO" if is_ilo else "OTHER")),
        "priority_tier":     tier,
        "priority_label":    tier_label,
        "delivery_family":   "FMAT" if is_fmat else ("ILT" if is_ilt else ("ILO" if is_ilo else "Other")),
        "is_international":  is_international,
        "is_domestic":       is_domestic,
        "location_known":    location_known,
        "revenue_potential": revenue_potential,
        "priority_score":    priority_score,
        "assignment_risk":   assignment_risk,
    }


@app.route('/api/data/allocation-desk', methods=['GET'])
def allocation_desk():
    """
    Unallocated batches with team-coverage and business-priority intelligence
    attached to each one — a Demand Intelligence Center, not a re-sorted list.

    Order is left exactly as RMS returns it. Managers plan against arrival
    order and business priority, not against how well their own team happens
    to match a course; re-sorting by match% (the previous behaviour) made a
    high-priority batch the team can't yet cover invisible at the bottom.
    """
    email = request.args.get('email', '').strip().lower()
    if not email:
        return jsonify({"error": "email query param required"}), 400

    if _wants_fresh():
        _cache_purge(email)

    demand = _demand_rows()
    if demand is None:
        return jsonify({"error": "Cannot reach RMS — please retry"}), 503

    reportees = _rms("reportees", {"email": email}) or []
    manager_name = str(_util_row(email).get("TrainerName", "") or "").strip()
    team = _team_capability(reportees, manager_email=email, manager_name=manager_name)

    priority_count = 0
    for b in demand:
        b["relevance"], b["candidates"], coverage = _rank_batch(b, team)
        b["coverage_status"] = coverage
        b["relevance_band"] = (
            "high" if b["relevance"] >= 75 else
            "medium" if b["relevance"] >= 50 else
            "low" if b["relevance"] > 0 else "none"
        )
        b.update(_priority_fields(
            b.get("delivery_mode"), b.get("location"), b.get("participants"), coverage,
        ))
        # Pure recommendation only. Loading Demand must never update RMS.
        manager_recommendation = _aishwar_recommendation(b, b["candidates"])
        if manager_recommendation:
            b["manager_recommendation"] = manager_recommendation
            for candidate in b["candidates"]:
                if candidate.get("trainer_email", "").lower() == _AISHWAR_EMAIL:
                    candidate["manager_recommendation"] = manager_recommendation
        if b["is_priority"]:
            priority_count += 1

    return jsonify({
        "manager": email,
        "team_size": len(team),
        "batches": demand,
        "summary": {
            "total": len(demand),
            "high": sum(1 for b in demand if b["relevance_band"] == "high"),
            "medium": sum(1 for b in demand if b["relevance_band"] == "medium"),
            "unmatched": sum(1 for b in demand if b["relevance"] == 0),
            "priority": priority_count,
            # Counted on the mode itself rather than on tier numbers, which
            # shift whenever the tier ladder is retuned (ILO moved 3 -> 4 when
            # FMAT and ILT were split apart, silently zeroing this counter).
            "fmat":  sum(1 for b in demand if b.get("delivery_mode_kind") == "FMAT"),
            "ilt":   sum(1 for b in demand if b.get("delivery_mode_kind") == "ILT"),
            "ilo":   sum(1 for b in demand if b.get("delivery_mode_kind") == "ILO"),
            "international": sum(1 for b in demand if b.get("is_international")),
            "instructor_led": sum(1 for b in demand if b.get("is_priority")),
            "at_risk": sum(1 for b in demand if b["assignment_risk"] == "High"),
            "manager_recommendations": sum(1 for b in demand if b.get("manager_recommendation")),
        },
        "timestamp": datetime.utcnow().isoformat(),
    }), 200


@app.route('/api/data/trainer-skills', methods=['GET'])
def trainer_skills():
    """
    The RMS skill register for one trainer — what they are formally on record as
    able to teach. Also the read-back that proves a mark-skill write landed.
    """
    email = request.args.get('email', '').strip().lower()
    if not email:
        return jsonify({"error": "email query param required"}), 400

    emp = _emp_code(email)
    if not emp:
        # Distinguish "RMS has no employee code for this address" from "no skills".
        return jsonify({
            "email": email, "emp_code": "", "skills": [], "count": 0,
            "available": False,
            "note": "RMS returned no employee code for this address, so the "
                    "skill register cannot be looked up.",
        }), 200

    register = _skill_register(emp)
    if register is None:
        return jsonify({"error": "Cannot reach RMS — please retry"}), 503

    return jsonify({
        "email": email,
        "emp_code": emp,
        "skills": register,
        "count": len(register),
        "available": True,
        "timestamp": datetime.utcnow().isoformat(),
    }), 200


@app.route('/api/action/mark-skill', methods=['POST'])
def mark_skill():
    """
    Records a trainer skill in RMS (Add Trainer Skill / IDP, key 255).

    This WRITES to production RMS. Inputs are validated here rather than trusted,
    because a bad SkillLevel or CourseId would create a real, wrong record.

    The write is then VERIFIED by re-reading the skill register (key 217) and
    checking the course id is actually present. RMS answers this endpoint with
    `[{"JSON_F52E2B61-…": null}]` — a stored-procedure envelope that looks
    identical whether the row was inserted or silently rejected. Reporting
    success off that envelope is what made skill assignment look like it saved
    when it had not; only a read-back can tell the two apart.
    """
    data = request.get_json(silent=True) or {}
    course_id = str(data.get("course_id", "")).strip()
    trainer_email = str(data.get("trainer_email", "")).strip().lower()
    from_date = str(data.get("from_date", "")).strip()
    approved = str(data.get("officially_approved", "No")).strip() or "No"

    if not course_id.isdigit():
        return jsonify({"success": False, "error": "course_id must be numeric"}), 400
    if not trainer_email.endswith("@koenig-solutions.com"):
        return jsonify({"success": False, "error": "trainer_email must be a Koenig address"}), 400
    if not _parse_date(from_date):
        return jsonify({"success": False, "error": "from_date must be a valid date"}), 400
    try:
        level = int(str(data.get("skill_level", "")).strip())
    except ValueError:
        return jsonify({"success": False, "error": "skill_level must be a number"}), 400
    if not 1 <= level <= 10:
        return jsonify({"success": False, "error": "skill_level must be between 1 and 10"}), 400

    emp = _emp_code(trainer_email)
    before = _skill_register(emp) if emp else None
    already = bool(before) and any(s["course_id"] == course_id for s in before)

    result = _rms("addTrainerSkill", {
        "CourseId":           course_id,
        "TrainerEmail":       trainer_email,
        "SkillLevel":         str(level),
        "OfficiallyApproved": approved,
        "FromDate":           _iso(_parse_date(from_date)),
    })
    if result is None:
        return jsonify({
            "success": False, "verified": False,
            "error": "RMS unreachable — skill not recorded",
        }), 503

    status, rms_message = _write_status(result)
    refused = status.lower() == "error"

    # A write invalidates this trainer's capability picture. Without this the
    # app would show a confirmed skill that the cached course list still denies.
    if not refused:
        _cache_purge(trainer_email)

    after = _skill_register(emp) if emp else None
    present = bool(after) and any(s["course_id"] == course_id for s in after)
    course_name = ""
    if after:
        course_name = next(
            (s["course_name"] for s in after if s["course_id"] == course_id), ""
        )

    payload = {
        "trainer_email": trainer_email,
        "course_id":     course_id,
        "course_name":   course_name,
        "skill_level":   level,
        "from_date":     _iso(_parse_date(from_date)),
        "already_held":  already,
        "skill_count":   len(after) if after is not None else None,
        "rms_status":    status,
        "rms_message":   rms_message,
        "rms_response":  result,
    }

    # Refused *and* already on file is the common, harmless case: RMS will not
    # remap an existing skill. It is not a failure, but it is not an update
    # either, and saying "updated" would be a lie.
    if refused and already and present:
        payload.update({
            "success": True, "verified": True, "changed": False,
            "message": "Already on record in RMS — "
                       f"{rms_message or 'no change was made'}.",
        })
        return jsonify(payload), 200

    if refused and not present:
        payload.update({
            "success": False, "verified": False, "changed": False,
            "error": rms_message or "RMS refused the write without giving a reason.",
        })
        return jsonify(payload), 409

    if present:
        payload.update({
            "success": True, "verified": True, "changed": not already,
            "message": ("Skill recorded and confirmed in RMS."
                        if not already else "Skill confirmed on the RMS register."),
        })
        return jsonify(payload), 200

    if after is None or not emp:
        # The write may well have succeeded; we simply cannot prove it. Say so
        # rather than claiming either outcome.
        payload.update({
            "success": True, "verified": False, "changed": None,
            "message": "RMS accepted the request but the skill register could "
                       "not be re-read, so this is unconfirmed. Check the "
                       "trainer's profile before relying on it.",
        })
        return jsonify(payload), 200

    payload.update({
        "success": False, "verified": False, "changed": False,
        "error": rms_message or
                 "RMS accepted the request but the course is still absent from "
                 "the trainer's skill register — the skill was NOT saved. This "
                 "usually means the course id is not assignable to this trainer.",
    })
    return jsonify(payload), 409
@app.route('/api/data/trainer-utilization-history', methods=['GET'])
def get_trainer_utilization_history():
    """
    Authoritative last-three-months utilisation for one trainer (RMS key 39).

    Keys off `emp_code`, not `TrainerId` — RMS returns an empty list for the
    trainer id (verified: id 15237 -> [], emp code 3815 -> 3 rows). They are
    different identifiers and only the employee code is accepted here.
    """
    email = str(request.args.get("email", "")).strip().lower()
    if not email:
        return jsonify({"error": "email query param required"}), 400

    emp_code = _emp_code(email)
    if not emp_code:
        return jsonify({
            "email": email, "emp_code": "", "months": [], "available": False,
            "note": "RMS returned no employee code for this address, so the "
                    "utilisation history cannot be looked up.",
        }), 200

    rows = _rms("last3MonthsUtil", {"EmpCode": str(emp_code)})
    if rows is None:
        return jsonify({"error": "Cannot reach RMS — please retry"}), 503

    months = []
    for r in (rows if isinstance(rows, list) else []):
        if not isinstance(r, dict):
            continue
        label = str(r.get("MonthName", "") or "").strip()
        if not label:
            continue
        try:
            util = round(float(r.get("Utilization") or 0), 1)
        except (TypeError, ValueError):
            util = 0.0
        months.append({"month": label, "utilization": util})
    # RMS returns newest first; charts read left-to-right in calendar order.
    months.sort(key=lambda m: _parse_date("01 " + m["month"], default=date.min))

    return jsonify({
        "email":     email,
        "emp_code":  emp_code,
        "months":    months,
        "available": bool(months),
        "timestamp": datetime.utcnow().isoformat(),
    }), 200


def _syllabus_index():
    """
    {normalised course name: syllabus PDF url} for the whole catalogue.

    RMS key 248 answers with a single row whose `JsonResult` is a JSON *string*
    holding all 12,125 courses, so the whole catalogue is fetched once and
    cached rather than called per course. Note this returns a link to a
    syllabus PDF (`SyllabusUrl`), not table-of-contents text — there is no
    endpoint in this integration that returns TOC content itself.
    """
    rows = _rms("courseSyllabus", {})
    if not isinstance(rows, list) or not rows or not isinstance(rows[0], dict):
        return {}
    blob = rows[0].get("JsonResult")
    try:
        parsed = json.loads(blob) if isinstance(blob, str) else (blob or [])
    except (ValueError, TypeError):
        return {}
    out = {}
    for r in (parsed if isinstance(parsed, list) else []):
        if not isinstance(r, dict):
            continue
        name = _norm_course(r.get("CourseName"))
        url = str(r.get("SyllabusUrl") or "").strip()
        if name and url:
            out[name] = {"url": url, "course_id": r.get("CId"),
                         "course_name": str(r.get("CourseName") or "").strip()}
    return out


@app.route('/api/data/course-syllabus', methods=['GET'])
def get_course_syllabus():
    """Syllabus PDF link for one course, matched by name against RMS key 248."""
    course_name = str(request.args.get("courseName", "")).strip()
    if not course_name:
        return jsonify({"error": "courseName query param required"}), 400

    index = _syllabus_index()
    if not index:
        return jsonify({"error": "Cannot reach RMS — please retry"}), 503

    hit = index.get(_norm_course(course_name))
    if not hit:
        # An honest miss: RMS has a syllabus index, this course is not in it.
        return jsonify({
            "course_name": course_name, "syllabus_url": "", "found": False,
            "note": "RMS holds no syllabus document for this course.",
        }), 200

    return jsonify({
        "course_name":  hit["course_name"] or course_name,
        "course_id":    hit["course_id"],
        "syllabus_url": hit["url"],
        "found":        True,
    }), 200


@app.route('/api/data/alternative-trainers', methods=['GET'])
def get_alternative_trainers():
    """
    Trainers outside the manager's own team who can deliver a course (key 157).

    UNAVAILABLE. RMS rejects every `TrainerType` value tried — "Internal",
    "Inhouse", "In-house", "FL", "Freelancer", "Freelance" and "All" all
    return an empty list, and an empty string returns the guidance row
    "Please enter Trainer Type.". The accepted enum is not documented in
    trainer_portal_api_details and cannot be guessed.

    This returns `available: false` rather than an empty trainer list on
    purpose: "we cannot ask the question" and "nobody can teach this course"
    are different answers, and rendering the second when the first is true
    would be a wrong claim about the company's bench.
    """
    course = str(request.args.get("course", "")).strip()
    if not course:
        return jsonify({"error": "course query param required"}), 400

    trainer_type = str(request.args.get("trainerType", "")).strip()
    rows = _rms("globalTrainers", {"Course": course, "TrainerType": trainer_type}) if trainer_type else None
    usable = [r for r in (rows or []) if isinstance(r, dict) and "Column1" not in r]

    if not usable:
        return jsonify({
            "course": course, "trainers": [], "available": False,
            "note": "RMS has not accepted any TrainerType value for this "
                    "endpoint, so the wider trainer network cannot be "
                    "searched yet. This is a missing API parameter, not an "
                    "empty result.",
        }), 200

    return jsonify({"course": course, "trainers": usable, "available": True}), 200


# ─── Manager action lifecycle ─────────────────────────────────────────────────
#
# Two kinds of action share one inbox and one lifecycle:
#
#   derived  — computed from RMS on every refresh (a certification gap, repeated
#              negative feedback, a benched trainer). They are rebuilt each
#              time, so their identity has to be stable across refreshes or a
#              manager's "closed" would be forgotten the moment data reloads.
#              _action_id() hashes the durable parts (trainer + category +
#              subject) rather than anything positional.
#   raised   — created by the manager by hand, for anything RMS cannot infer.
#
# State, notes and follow-up dates live in a JSON store keyed by action id and
# are overlaid onto the derived list on every read, so a decision survives a
# cache miss or a full RMS re-read.
#
# STORAGE CAVEAT: this writes to local disk. On Render's ephemeral filesystem
# the file does not survive a restart or redeploy, so lifecycle state is
# session-durable rather than permanent. Moving it to a real datastore is a
# prerequisite for relying on it across deploys.

_ACTION_STORE = os.path.join(os.getenv("SKILLEDGE_STATE_DIR", "."), "action_state.json")
_action_lock = threading.Lock()

VALID_ACTION_STATES = ("open", "in_progress", "closed", "escalated", "reassigned")


def _action_id(trainer_email, category, subject):
    """Stable id for a derived action, independent of list position."""
    raw = "|".join([
        str(trainer_email or "").strip().lower(),
        str(category or "").strip().lower(),
        str(subject or "").strip().lower(),
    ])
    return "act_" + hashlib.sha1(raw.encode("utf-8")).hexdigest()[:16]


def _action_store_load():
    try:
        with open(_ACTION_STORE, encoding="utf-8") as fh:
            data = json.load(fh)
    except (OSError, ValueError):
        data = {}
    data.setdefault("states", {})
    data.setdefault("raised", {})
    return data


def _action_store_save(data):
    tmp = _ACTION_STORE + ".tmp"
    try:
        with open(tmp, "w", encoding="utf-8") as fh:
            json.dump(data, fh, indent=2, default=str)
        os.replace(tmp, _ACTION_STORE)
    except OSError:
        pass          # a read-only filesystem must not break the request


def _action_apply_overlay(actions):
    """Annotate derived actions with any stored lifecycle state and notes."""
    if not isinstance(actions, list):
        return actions
    with _action_lock:
        states = _action_store_load()["states"]
    for a in actions:
        if not isinstance(a, dict):
            continue
        rec = states.get(a.get("id"))
        if rec:
            a["lifecycle_state"] = rec.get("state", "open")
            a["notes"] = rec.get("notes", [])
            a["due_date"] = rec.get("due_date", "")
            a["assignee"] = rec.get("assignee", "")
            a["updated_at"] = rec.get("updated_at", "")
            a["history"] = rec.get("history", [])
        else:
            a.setdefault("lifecycle_state", "open")
            a.setdefault("notes", [])
    return actions


def _derive_actions(trainer_ops, trainer_states, capability_trainers, demand_rows):
    """
    Everything currently asking for a manager decision, as one typed list.

    Certification gaps are first-class actions here rather than a separate
    board: a gap is a decision waiting on the manager in exactly the same sense
    as a feedback incident, and splitting them across two screens meant half
    the queue was invisible from the inbox.
    """
    out = []
    state_by = {str(s.get("trainer_email", "")).lower(): s for s in (trainer_states or [])}

    for t in (trainer_ops or []):
        email = str(t.get("official_email", "")).lower()
        name = t.get("trainer_name", "")
        st = state_by.get(email, {})

        neg = t.get("negative_count") or 0
        if neg > 0:
            out.append({
                "id": _action_id(email, "feedback", "negative-feedback"),
                "source": "derived", "category": "Feedback",
                "title": "Review negative feedback",
                "detail": "%d negative feedback record%s on file." % (neg, "" if neg == 1 else "s"),
                "trainer_name": name, "trainer_email": email,
                "priority": "high" if neg > 2 else "medium",
            })

        util = t.get("current_utilization")
        if st.get("current_status") == "free" and (util is None or util < 40):
            out.append({
                "id": _action_id(email, "allocation", "bench"),
                "source": "derived", "category": "Allocation",
                "title": "Trainer available for allocation",
                "detail": "No current assignment%s."
                          % ("" if util is None else " and %d%% utilised" % util),
                "trainer_name": name, "trainer_email": email,
                "priority": "medium",
            })

        if isinstance(util, (int, float)) and util > 85:
            out.append({
                "id": _action_id(email, "capacity", "overloaded"),
                "source": "derived", "category": "Capacity",
                "title": "Trainer over capacity",
                "detail": "%d%% utilised - consider redistributing upcoming work." % util,
                "trainer_name": name, "trainer_email": email,
                "priority": "medium",
            })

    # Certification gaps - one action per trainer, naming the courses.
    for c in (capability_trainers or []):
        cert = c.get("certification") or {}
        missing = cert.get("missing") or []
        if not missing:
            continue
        email = str(c.get("trainer_email", "")).lower()
        courses = ", ".join(str(m.get("because", "")).strip()
                            for m in missing[:3] if m.get("because"))
        out.append({
            "id": _action_id(email, "certification", "gap"),
            "source": "derived", "category": "Certification",
            "title": "%d certification gap%s" % (len(missing), "" if len(missing) == 1 else "s"),
            "detail": ("Teaching without the matching certificate: %s%s"
                       % (courses, "..." if len(missing) > 3 else ""))
                      if courses else "Courses taught without a matching certificate.",
            "trainer_name": c.get("trainer_name", ""), "trainer_email": email,
            "priority": "high" if any(m.get("priority") == "high" for m in missing) else "medium",
            "gap_count": len(missing),
        })

    if demand_rows:
        out.append({
            "id": _action_id("", "demand", "unallocated"),
            "source": "derived", "category": "Demand",
            "title": "%d unallocated batch%s" % (len(demand_rows), "" if len(demand_rows) == 1 else "es"),
            "detail": "Demand waiting for a trainer assignment.",
            "trainer_name": "", "trainer_email": "",
            "priority": "high" if len(demand_rows) > 5 else "medium",
        })

    rank = {"high": 0, "medium": 1, "low": 2}
    out.sort(key=lambda a: (rank.get(a.get("priority"), 3), a.get("category", "")))
    return out


@app.route('/api/actions', methods=['GET'])
def get_actions():
    """The manager's full inbox: derived actions plus anything raised by hand."""
    email = str(request.args.get("email", "")).strip().lower()
    if not email:
        return jsonify({"error": "email query param required"}), 400

    reportees = _rms("reportees", {"email": email}) or []
    # Action coverage must match the complete Team roster; otherwise trainers
    # after the twentieth can never surface manager-attention items.
    rows = [r for r in reportees if isinstance(r, dict)]
    today = datetime.utcnow().date()

    def _one(r):
        try:
            return _build_trainer(r, today)
        except Exception:
            return None

    with ThreadPoolExecutor(max_workers=8) as pool:
        built = [b for b in pool.map(_one, rows) if b]
    trainer_ops = [b[0] for b in built]
    trainer_states = [b[1] for b in built]

    demand = _demand_rows() or []
    derived = _derive_actions(trainer_ops, trainer_states, [], demand)
    _action_apply_overlay(derived)

    with _action_lock:
        raised = list(_action_store_load()["raised"].values())
    for r in raised:
        r.setdefault("lifecycle_state", "open")

    actions = derived + [r for r in raised if r.get("manager_email") == email]
    return jsonify({
        "manager": email,
        "actions": actions,
        "open": sum(1 for a in actions if a.get("lifecycle_state") == "open"),
        "closed": sum(1 for a in actions if a.get("lifecycle_state") == "closed"),
        "timestamp": datetime.utcnow().isoformat(),
    }), 200


@app.route('/api/actions', methods=['POST'])
def raise_action():
    """Create a manager-raised action (anything RMS cannot infer)."""
    body = request.get_json(silent=True) or {}
    title = str(body.get("title", "")).strip()
    manager_email = str(body.get("manager_email", "")).strip().lower()
    if not title:
        return jsonify({"error": "title is required"}), 400
    if not manager_email:
        return jsonify({"error": "manager_email is required"}), 400

    now = datetime.utcnow().isoformat()
    action_id = "act_m_" + hashlib.sha1((manager_email + title + now).encode()).hexdigest()[:14]
    record = {
        "id": action_id, "source": "raised",
        "category": str(body.get("category", "Other")).strip() or "Other",
        "title": title,
        "detail": str(body.get("detail", "")).strip(),
        "trainer_name": str(body.get("trainer_name", "")).strip(),
        "trainer_email": str(body.get("trainer_email", "")).strip().lower(),
        "priority": str(body.get("priority", "medium")).strip() or "medium",
        "due_date": str(body.get("due_date", "")).strip(),
        "manager_email": manager_email,
        "lifecycle_state": "open",
        "notes": [], "history": [],
        "created_at": now, "updated_at": now,
    }
    with _action_lock:
        data = _action_store_load()
        data["raised"][action_id] = record
        _action_store_save(data)
    return jsonify(record), 201


@app.route('/api/actions/<action_id>/state', methods=['POST'])
def set_action_state(action_id):
    """Move one action through its lifecycle, with an audit trail."""
    body = request.get_json(silent=True) or {}
    state = str(body.get("state", "")).strip().lower()
    if state not in VALID_ACTION_STATES:
        return jsonify({"error": "state must be one of %s" % (VALID_ACTION_STATES,)}), 400

    now = datetime.utcnow().isoformat()
    entry = {
        "state": state, "note": str(body.get("note", "")).strip(),
        "assignee": str(body.get("assignee", "")).strip(),
        "by": str(body.get("manager_email", "")).strip().lower(), "at": now,
    }
    with _action_lock:
        data = _action_store_load()
        # A raised action carries its own record; a derived one only has state.
        if action_id in data["raised"]:
            rec = data["raised"][action_id]
        else:
            rec = data["states"].setdefault(action_id, {"notes": [], "history": []})
        rec["state"] = state
        rec["assignee"] = entry["assignee"] or rec.get("assignee", "")
        rec["due_date"] = str(body.get("due_date", "")).strip() or rec.get("due_date", "")
        rec["updated_at"] = now
        rec.setdefault("history", []).append(entry)
        if entry["note"]:
            rec.setdefault("notes", []).append(
                {"text": entry["note"], "by": entry["by"], "at": now})
        if action_id in data["raised"]:
            rec["lifecycle_state"] = state
        _action_store_save(data)
    return jsonify({"id": action_id, "state": state, "updated_at": now,
                    "notes": rec.get("notes", []),
                    "history": rec.get("history", [])}), 200


@app.route('/api/actions/<action_id>/note', methods=['POST'])
def add_action_note(action_id):
    """Append a follow-up note without changing the action's state."""
    body = request.get_json(silent=True) or {}
    text = str(body.get("note", "")).strip()
    if not text:
        return jsonify({"error": "note is required"}), 400
    now = datetime.utcnow().isoformat()
    note = {"text": text, "by": str(body.get("manager_email", "")).strip().lower(), "at": now}
    with _action_lock:
        data = _action_store_load()
        rec = data["raised"].get(action_id) or data["states"].setdefault(
            action_id, {"state": "open", "notes": [], "history": []})
        rec.setdefault("notes", []).append(note)
        rec["updated_at"] = now
        _action_store_save(data)
    return jsonify({"id": action_id, "notes": rec.get("notes", [])}), 200


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
        "version":  "6.1.0",
        "endpoints": {
            "POST /api/auth/login":                               "Authenticate (role-verified)",
            "POST /api/auth/logout":                              "Logout",
            "GET  /api/data/unified-manager-intelligence?email=": "Full dashboard payload",
            "GET  /api/data/manager-profile?email=":               "Signed-in user identity",
            "GET  /api/data/trainer-360?email=":                  "Deep single-trainer profile",
            "GET  /api/data/team-capability?email=":              "Course catalogue + certification gaps",
            "GET  /api/data/allocation-desk?email=":              "Unallocated batches ranked by team fit",
            "GET  /api/data/trainer-skills?email=":               "RMS skill register for one trainer",
            "POST /api/action/mark-skill":                        "Record a skill (verified write)",
            "GET  /healthz":                                      "Health check",
        },
    }), 200


if __name__ == '__main__':
    app.run(debug=False, port=8080)
