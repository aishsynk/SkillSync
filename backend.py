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


def _team_capability(reportees):
    """[(trainer_name, email, [capability rows], feedback_status)] for the manager's roster."""
    today = datetime.utcnow().date()

    def one(r):
        email = str(r.get("OffEmail", "")).strip().lower()
        name = _re.sub(r"\s+", " ", str(r.get("TrainerName", ""))).strip()
        caps = _skills(email) if email else []
        emp_code = _certifications(email)["emp_code"] if email else ""
        recent_negative = _feedback_recency(emp_code) if emp_code else None
        return name, email, caps, _allocation_block_status(recent_negative, today)

    rows = [r for r in (reportees if isinstance(reportees, list) else []) if isinstance(r, dict)]
    with ThreadPoolExecutor(max_workers=8) as pool:
        return list(pool.map(one, rows))


def _rank_batch(batch, team):
    """Best team match for one unallocated batch, plus the ranked candidate list."""
    course, vendor = batch.get("course_name", ""), batch.get("customer", "")
    candidates = []
    for name, email, caps, feedback in team:
        best, best_course, best_q = 0, "", 0
        for c in caps:
            s = _match_score(course, vendor, c["course"], c["vendor"])
            if s > best:
                best, best_course, best_q = s, c["course"], c["qubits_score"]
        if best > 0:
            candidates.append({
                "trainer_name":  name,
                "trainer_email": email,
                "match":         best,
                "via_course":    best_course,
                "qubits_score":  best_q,
                "exact":         best >= 92,
                "category":      "Best Match" if best >= 90 else "Alternate Match" if best >= 75 else "Risky Assignment",
                "blocked":             feedback["blocked"],
                "blocked_until":       feedback["blocked_until"],
                "recent_negative_6mo": feedback["recent_negative_6mo"],
            })
    # Available trainers before blocked ones (RMS would not auto-allocate a
    # blocked trainer regardless of match score), then by match, then a clean
    # 6-month feedback record breaks ties. No Qubits tie-break — see header.
    candidates.sort(key=lambda c: (c["blocked"], -c["match"], c["recent_negative_6mo"]))

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

    return (candidates[0]["match"] if candidates else 0), candidates[:5]


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


def _cert_intelligence(courses, held_certs, accreditations):
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

    covered = len(taught) - len(missing)
    return {
        "held":        held_certs,
        "held_codes":  sorted(held_codes),
        "accreditations": accreditations,
        "missing":     missing,
        "recommended": recommended,
        "taught_codes": sorted(taught),
        "coverage_pct": round(100 * covered / len(taught)) if taught else None,
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
        "version":   "6.1.0",
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

    if _wants_fresh():
        _cache_purge(email)

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

    # ── Manager KPIs ─────────────────────────────────────────────────────
    # Everything here is counted from data already fetched above. Certification
    # KPIs are deliberately absent — they need three more RMS calls per trainer
    # and are served by /api/data/team-capability so this payload stays fast.
    engaged = {"teaching_now", "scheduled_today", "preparing"}
    active_trainers = sum(1 for s in trainer_states if s["current_status"] in engaged)
    unallocated_trainers = sum(1 for s in trainer_states if s["current_status"] == "free")
    active_batches   = sum(1 for b in all_batches if b["engagement_state"] == "current")
    upcoming_batches = sum(1 for b in all_batches if b["engagement_state"] == "upcoming")

    # Delivered days are only countable inside the fetch window (-30d), so the
    # label has to say so rather than implying an all-time total.
    days_delivered = 0
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
    unknown_state = sum(1 for s in trainer_states if s["current_status"] == "unknown")

    # Share of the team whose position we can actually vouch for. Deliberately
    # NOT called readiness: this path has no capability signal, and a number
    # built only from status would disagree with the real readiness score served
    # by /api/data/team-capability. Two metrics with one name is how the web
    # product ended up with contradictory scores on different screens.
    if trainer_ops:
        deployable = sum(
            1 for t, s in zip(trainer_ops, trainer_states)
            if s["current_status"] != "unknown" and t["feedback_risk"] != "High"
        )
        deployable_pct = round(100 * deployable / len(trainer_ops))
    else:
        deployable_pct = None

    manager_kpis = {
        "total_team_members":   len(trainer_ops),
        "active_trainers":      active_trainers,
        "unallocated_trainers": unallocated_trainers,
        "active_batches":       active_batches,
        "upcoming_batches":     upcoming_batches,
        "training_days_delivered": days_delivered,
        "training_days_window_label": "last 30 days",
        "avg_team_utilization": avg_util if util_vals else None,
        "utilization_sample":   len(util_vals),
        "high_risk_trainers":   high_risk,
        "stretched_trainers":   stretched,
        "bench_trainers":       on_bench,
        "deployable_pct":       deployable_pct,
        "unknown_status":       unknown_state,
        "open_actions":         len(actions),
        "open_demand":          len(demand_df),
    }

    # ── Response (web-frontend data model + backward-compat fields) ──────
    return jsonify({
        "manager_kpis":             manager_kpis,
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
        "own_utilization": _avg_util(series) if series else None,
        "team": {
            "size":      len(reportees),
            "direct":    direct,
            "indirect":  len(reportees) - direct,
            "reachable": reachable,
        },
        "has_resume":   bool(resume),
        "timestamp":    datetime.utcnow().isoformat(),
    }), 200


def _capability_for(r):
    """Per-trainer capability + certification picture. One worker's share."""
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

    util = _avg_util(series) if series else None
    intel = _cert_intelligence(caps, resume.get("certifications", []), certs["held"])
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
    rows = [r for r in (reps if isinstance(reps, list) else []) if isinstance(r, dict)][:20]

    with ThreadPoolExecutor(max_workers=6) as pool:
        team = [t for t in pool.map(_capability_for, rows) if t]

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

    cert_intel = _cert_intelligence(skills, resume.get("certifications", []), certs["held"])

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

    util_ok  = bool(series)
    util_now = _avg_util(series) if util_ok else None

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
            "current": util_now if util_ok else None,
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


@app.route('/api/data/allocation-desk', methods=['GET'])
def allocation_desk():
    """
    Unallocated batches ranked by how well the manager's own team can cover them.
    Relevance is computed against real capability rows (course + Qubits per
    trainer), not guessed from the course title alone.
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
    team = _team_capability(reportees)

    for b in demand:
        b["relevance"], b["candidates"] = _rank_batch(b, team)
        b["relevance_band"] = (
            "high" if b["relevance"] >= 75 else
            "medium" if b["relevance"] >= 50 else
            "low" if b["relevance"] > 0 else "none"
        )
    demand.sort(key=lambda b: (-b["relevance"], b["start_date"] or ""))

    return jsonify({
        "manager": email,
        "team_size": len(team),
        "batches": demand,
        "summary": {
            "total": len(demand),
            "high": sum(1 for b in demand if b["relevance_band"] == "high"),
            "medium": sum(1 for b in demand if b["relevance_band"] == "medium"),
            "unmatched": sum(1 for b in demand if b["relevance"] == 0),
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
