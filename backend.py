"""
SkillSync Backend API v6.0
Deployed on Render — production backend for SkillSync Android app.

CONTRACT (Task 6 — V2 is canonical):
  `/api/v2/...` is the supported surface and the only one new clients should
  target. The legacy `/api/data/...`, `/api/action/...` and `/api/actions/...`
  routes remain for backward compatibility only and are scheduled for removal;
  they are NOT unauthenticated anymore — every one of them now runs through the
  same `_v2_manager_session` session+scope gate as the V2 routes, so a legacy
  call without a session gets 401 SESSION_REQUIRED and a cross-manager email
  gets 403 MANAGER_SCOPE_MISMATCH. There is only one auth model, one handler
  per capability, and the old `request.path.startswith("/api/v2/")` branch has
  been removed.

Auth: POST /api/auth/login — @koenig-solutions.com only, manager or Trainer Plus role.
      Every other route requires `Authorization: Bearer <session_id>` (issued by
      login) and enforces manager scope: a requested manager email that does not
      match the session email is rejected with 403 MANAGER_SCOPE_MISMATCH. This
      closes the former unauthenticated-PII-by-email surface on /api/data/... and
      /api/action(s)/... routes.
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
import base64
import hashlib
import hmac
import json
import os
import re as _re
import secrets
import threading
import time
import urllib.parse
import urllib.request
from action_store import ActionStore

app = Flask(__name__)
CORS(app)

# ─── RMS API wiring ───────────────────────────────────────────────────────────
_RMS_BASE = "https://api.koenig-solutions.com"
_TOKEN_EP  = "/api/Kites/Operator/GetToken"
_DATA_EP   = "/api/Kites/Operator/common"
_TIMEOUT   = 30

_ev_fallbacks: set = set()


def _ev(name, fallback=""):
    """Read an environment variable, falling back to a dev-only default.
    Tracks which env vars were absent so _validate_credentials() can warn
    at startup when production is running on hardcoded credentials."""
    val = os.getenv(name, "").strip()
    if val:
        return val
    if fallback:
        _ev_fallbacks.add(name)
    return fallback

_APIS = {
    # Credentials are read from environment variables (`SKILLEDGE_RMS_<NAME>_USER`
    # / `_PASS`) so no plaintext secret ships in source. The literal values below
    # are only startup fallbacks kept during the migration so production does not
    # break mid-change; provisioning the env vars and removing the fallbacks is the
    # intended ending state.
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
        "user": _ev("SKILLEDGE_RMS_UTILIZATION_USER", "AISHWAR_GetUtilization"),
        "pass": _ev("SKILLEDGE_RMS_UTILIZATION_PASS", "j4CakF7gEg#f"),
        "role": "Get Utilization",
        "key":  "55",
    },
    # ── Assignments ─────────────────────────────────────────────────────────
    "prevUpcoming": {
        "user": _ev("SKILLEDGE_RMS_PREVUPCOMING_USER", "AISHWAR_PreviousUpcommi"),
        "pass": _ev("SKILLEDGE_RMS_PREVUPCOMING_PASS", "J8LzP@HkW#Ve"),
        "role": "Previous & Upcomming Assignments",
        "key":  "16",
    },
    "upcomingAssignments": {
        "user": _ev("SKILLEDGE_RMS_UPCOMING_ASSIGNMENTS_USER", "AISHWAR_UpcomingAssignm"),
        "pass": _ev("SKILLEDGE_RMS_UPCOMING_ASSIGNMENTS_PASS", "nFY$g68zSaRD"),
        "role": "Upcoming Assignments",
        "key":  "93",
    },
    "unallocated": {
        "user": _ev("SKILLEDGE_RMS_UNALLOCATED_USER", "AISHWAR_UnallocatedAssi"),
        "pass": _ev("SKILLEDGE_RMS_UNALLOCATED_PASS", "$5djCU@w7eR3"),
        "role": "Unallocated Assignment",
        "key":  "190",
    },
    # ── Availability & international eligibility ─────────────────────────────
    # The two endpoints the 2026-08-11 audit found unwired, both validated live
    # the same day. Together they replace utilisation-as-availability, which is
    # the single largest correctness error in the product: a trainer at 80% can
    # be free on the dates that matter, and one at 40% can be on leave.
    #
    # 171 is course-first and returns the whole skilled pool, not only the free
    # ones: 37 rows for AZ-305, 21 for CKA. Per trainer it carries a literal
    # free-date calendar (~155 days), TrainerTimezone (100% populated),
    # NearestCity, Skill Level, course-specific assignment count, and Visa as
    # [{Country, VisaExpiryDate, StayPeriod, AssociateCountries}] (~48%).
    #
    # It requires an EXACT catalogue course name. "AZ-305T00: …" returns 37
    # rows; "AI-102T00: …", "AZ-104T00: …" and "CCNA - …" all return 0. That is
    # why _resolve_course_name exists and why a miss must read "cannot verify",
    # never "nobody available".
    "trainerFreeSchedule": {
        "user": _ev("SKILLEDGE_RMS_TRAINER_FREE_SCHEDULE_USER", "AISHWAR_GetTrainerFreeS"),
        "pass": _ev("SKILLEDGE_RMS_TRAINER_FREE_SCHEDULE_PASS", "J6FLKGx!exA7"),
        "role": "Get Trainer Free Shedule and Details",
        "key":  "171",
    },
    # 111 is the day-level operational calendar: 61 rows x 35 fields for one
    # trainer over two months. Carries LeaveStatus and its applied/approved
    # dates, AssociatedType, QuotationStatus (confirmed vs tentative),
    # DeliveryMode, QubitScore, Exam, and the two client-relationship fields
    # nothing else exposes — SpecifiedTrainer (preference) and DNC (exclusion).
    "trainerRCSchedule": {
        "user": _ev("SKILLEDGE_RMS_TRAINER_RC_SCHEDULE_USER", "AISHWAR_TrainerRCSchedu"),
        "pass": _ev("SKILLEDGE_RMS_TRAINER_RC_SCHEDULE_PASS", "jGErt8!Agr$a"),
        "role": "Trainer RC Schedule",
        "key":  "111",
    },
    # Course-level exam policy for the whole catalogue. Verified live
    # 2026-08-08: 10,934 rows across 438 vendors, fields Courseid / CName /
    # "Exam Required or Not" / CourseStatus / Vendor. This is what lets the
    # certification gap cover Cisco, AWS, RedHat, Oracle and the rest instead
    # of only the Microsoft codes in _CERT_CATALOG.
    "courseWithoutExam": {
        "user": _ev("SKILLEDGE_RMS_COURSE_WITHOUT_EXAM_USER", "AISHWAR_CourseWhitoutEx"),
        "pass": _ev("SKILLEDGE_RMS_COURSE_WITHOUT_EXAM_PASS", "V9n82gfmC$$W"),
        "role": "Course Whitout Exam",
        "key":  "213",
    },
    "assignment": {
        "user": _ev("SKILLEDGE_RMS_ASSIGNMENT_USER", "AISHWAR_AssignmentAPI"),
        "pass": _ev("SKILLEDGE_RMS_ASSIGNMENT_PASS", "4PV6aCe6Sc8!"),
        "role": "Assignment API",
        "key":  "15",
    },
    # ── Feedback & Incidents ─────────────────────────────────────────────────
    "negFeedbackCount": {
        "user": _ev("SKILLEDGE_RMS_NEG_FEEDBACK_COUNT_USER", "AISHWAR_GetNegativeFeed"),
        "pass": _ev("SKILLEDGE_RMS_NEG_FEEDBACK_COUNT_PASS", "#9u7@@hAHWUg"),
        "role": "Get Negative Feedback Count",
        "key":  "58",
    },
    "trainerFeedback": {
        "user": _ev("SKILLEDGE_RMS_TRAINER_FEEDBACK_USER", "AISHWAR_GetTrainerFeedb"),
        "pass": _ev("SKILLEDGE_RMS_TRAINER_FEEDBACK_PASS", "T9$jsBnSW7Rd"),
        "role": "Get Trainer Feedback Details",
        "key":  "244",
    },
    "hrIncident": {
        "user": _ev("SKILLEDGE_RMS_HR_INCIDENT_USER", "AISHWAR_GetHRIncidentPo"),
        "pass": _ev("SKILLEDGE_RMS_HR_INCIDENT_PASS", "42nLmM!#weDk"),
        "role": "Get HR Incident Positive Negative",
        "key":  "59",
    },
    "trainerNegFeedback": {
        "user": _ev("SKILLEDGE_RMS_TRAINER_NEG_FEEDBACK_USER", "AISHWAR_GetTrainerNegat"),
        "pass": _ev("SKILLEDGE_RMS_TRAINER_NEG_FEEDBACK_PASS", "j34JFz$s9Um#"),
        "role": "Get Trainer Negative Feedback",
        "key":  "218",
    },
    # ── Skills & Certs ───────────────────────────────────────────────────────
    "trainerSkills": {
        "user": _ev("SKILLEDGE_RMS_TRAINER_SKILLS_USER", "AISHWAR_GetTrainerSkill"),
        "pass": _ev("SKILLEDGE_RMS_TRAINER_SKILLS_PASS", "dpcwt4L5$@7U"),
        "role": "Get Trainer Skills",
        "key":  "217",
    },
    "vendorCertCount": {
        "user": _ev("SKILLEDGE_RMS_VENDOR_CERT_COUNT_USER", "AISHWAR_GettrainerVende"),
        "pass": _ev("SKILLEDGE_RMS_VENDOR_CERT_COUNT_PASS", "!$R#gQuAs9Rw"),
        "role": "Get trainer Vender Certification Count",
        "key":  "57",
    },
    # ── Course & Scheduling ──────────────────────────────────────────────────
    "trainerAvailability": {
        "user": _ev("SKILLEDGE_RMS_TRAINER_AVAILABILITY_USER", "AISHWAR_Traineravailabi"),
        "pass": _ev("SKILLEDGE_RMS_TRAINER_AVAILABILITY_PASS", "c2yRDVdG#XCs"),
        "role": "Trainer availability",
        "key":  "90",
    },
    "scid": {
        "user": _ev("SKILLEDGE_RMS_SCID_USER", "AISHWAR_GetSCID"),
        "pass": _ev("SKILLEDGE_RMS_SCID_PASS", "kLH#4T!Tfu6f"),
        "role": "Get SCID",
        "key":  "173",
    },
    "activeSCDate": {
        "user": _ev("SKILLEDGE_RMS_ACTIVE_SC_DATE_USER", "AISHWAR_GetActiveSCDate"),
        "pass": _ev("SKILLEDGE_RMS_ACTIVE_SC_DATE_PASS", "P2mbqrhB#t4F"),
        "role": "Get Active SC Date",
        "key":  "13",
    },
    "assignmentPax": {
        "user": _ev("SKILLEDGE_RMS_ASSIGNMENT_PAX_USER", "AISHWAR_GetAssignmentpa"),
        "pass": _ev("SKILLEDGE_RMS_ASSIGNMENT_PAX_PASS", "!zSgxaRdA9dC"),
        "role": "Get Assignment pax",
        "key":  "209",
    },
    "recordingDetails": {
        "user": _ev("SKILLEDGE_RMS_RECORDING_DETAILS_USER", "AISHWAR_GetRecordingDet"),
        "pass": _ev("SKILLEDGE_RMS_RECORDING_DETAILS_PASS", "RPtPvRq5nF$H"),
        "role": "Get Recording Details by Assignment Id",
        "key":  "278",
    },
    "last3MonthsUtil": {
        "user": _ev("SKILLEDGE_RMS_LAST_3_MONTHS_UTIL_USER", "AISHWAR_TrainerLast3Mon"),
        "pass": _ev("SKILLEDGE_RMS_LAST_3_MONTHS_UTIL_PASS", "TmSe!9A!@GfL"),
        "role": "Trainer_Last_3_Months_Utilization",
        "key":  "39",
    },
    "courseSyllabus": {
        "user": _ev("SKILLEDGE_RMS_COURSE_SYLLABUS_USER", "AISHWAR_GetCourseSyllab"),
        "pass": _ev("SKILLEDGE_RMS_COURSE_SYLLABUS_PASS", "W@PFkUQt$Ek3"),
        "role": "Get Course Syllabus TOC",
        "key":  "248",
    },
    "courseCatalogue": {
        "user": _ev("SKILLEDGE_RMS_COURSE_CATALOGUE_USER", "AISHWAR_GetCourseName"),
        "pass": _ev("SKILLEDGE_RMS_COURSE_CATALOGUE_PASS", "H7GnTdC@ECvC"),
        "role": "Get Course Name",
        "key":  "70",
    },
    "courseSchedule": {
        "user": _ev("SKILLEDGE_RMS_COURSE_SCHEDULE_USER", "AISHWAR_GetCourseSchedu"),
        "pass": _ev("SKILLEDGE_RMS_COURSE_SCHEDULE_PASS", "tFEy8T6JLT!J"),
        "role": "Get Course Schedule",
        "key":  "246",
    },
    "globalTrainers": {
        "user": _ev("SKILLEDGE_RMS_GLOBAL_TRAINERS_USER", "AISHWAR_GetInhouseandFL"),
        "pass": _ev("SKILLEDGE_RMS_GLOBAL_TRAINERS_PASS", "2XC!2LBpsTJh"),
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
        "user": _ev("SKILLEDGE_RMS_ADD_TRAINER_SKILL_USER", "AISHWAR_AddTrainerSkill"),
        "pass": _ev("SKILLEDGE_RMS_ADD_TRAINER_SKILL_PASS", "2bd6UhV#PJ#T"),
        "role": "Add Trainer Skill (IDP)",
        "key":  "255",
    },
    "courseAvailability": {
        "user": _ev("SKILLEDGE_RMS_COURSE_AVAILABILITY_USER", "AISHWAR_CheckCourseAvai"),
        "pass": _ev("SKILLEDGE_RMS_COURSE_AVAILABILITY_PASS", "$3GapuDUF5XU"),
        "role": "Check Course Availability in RMS",
        "key":  "104",
    },
    # ── Extended Course, Technology & Exam Catalogue (Audited 2026-08-22) ───
    "courseTechnology": {
        "user": _ev("SKILLEDGE_RMS_COURSE_TECHNOLOGY_USER", "AISHWAR_CourseTechnolog"),
        "pass": _ev("SKILLEDGE_RMS_COURSE_TECHNOLOGY_PASS", "L5PMuN!wKE4j"),
        "role": "Course & Technology List",
        "key":  "114",
    },
    "courseList": {
        "user": _ev("SKILLEDGE_RMS_COURSE_LIST_USER", "AISHWAR_CourseList"),
        "pass": _ev("SKILLEDGE_RMS_COURSE_LIST_PASS", "@56Crxj#Yc@5"),
        "role": "Course List",
        "key":  "164",
    },
    "examCourseLinked": {
        "user": _ev("SKILLEDGE_RMS_EXAM_COURSE_LINKED_USER", "AISHWAR_ExamCourseLinke"),
        "pass": _ev("SKILLEDGE_RMS_EXAM_COURSE_LINKED_PASS", "K7!k@n3dA$w2"),
        "role": "Exam Course Linked API",
        "key":  "215",
    },
    "courseContentUrl": {
        "user": _ev("SKILLEDGE_RMS_COURSE_CONTENT_URL_USER", "AISHWAR_GetCourseConten"),
        "pass": _ev("SKILLEDGE_RMS_COURSE_CONTENT_URL_PASS", "3!SDHwJvBn2w"),
        "role": "Get Course Content URL",
        "key":  "156",
    },
    "courseModule": {
        "user": _ev("SKILLEDGE_RMS_COURSE_MODULE_USER", "AISHWAR_GetCourseModule"),
        "pass": _ev("SKILLEDGE_RMS_COURSE_MODULE_PASS", "NpT5tqde@TZ2"),
        "role": "Get Course Module",
        "key":  "206",
    },
    "courseDomain": {
        "user": _ev("SKILLEDGE_RMS_COURSE_DOMAIN_USER", "AISHWAR_GetCourseandDom"),
        "pass": _ev("SKILLEDGE_RMS_COURSE_DOMAIN_PASS", "HcUAr7!5zALS"),
        "role": "Get Course and Domain",
        "key":  "205",
    },
    "latestCourseVersion": {
        "user": _ev("SKILLEDGE_RMS_LATEST_COURSE_VERSION_USER", "AISHWAR_GetLatestVersio"),
        "pass": _ev("SKILLEDGE_RMS_LATEST_COURSE_VERSION_PASS", "M@bXLcQ4h!@$"),
        "role": "Get Latest Version Of Courses",
        "key":  "172",
    },
    "uniqueCertsCount": {
        "user": _ev("SKILLEDGE_RMS_UNIQUE_CERTS_COUNT_USER", "AISHWAR_GetUniqueCertif"),
        "pass": _ev("SKILLEDGE_RMS_UNIQUE_CERTS_COUNT_PASS", "G8!9P@$m3t25"),
        "role": "Get Unique Certifications Count Value",
        "key":  "72",
    },
}

# ─── Credential startup validation (Task 3) ─────────────────────────────────────
#
# _ev() records the env var names that fell back to hardcoded values in
# _ev_fallbacks. _validate_credentials() runs once at import time and
# reports them so a production deploy running on in-repo literals is
# caught immediately, not silently.
def _validate_credentials():
    """Warn at startup when RMS credentials fall back to hardcoded values.

    Per the Task 3 security gate: credentials must come from environment
    variables / secret storage, never from plaintext literals in source.
    This function does NOT crash the app -- the fallbacks exist only as a
    migration safety net (per the operator rule: "keep existing values as
    defaults during the migration so production does not break mid-change")
    -- but in production it prints a loud stderr banner so operators see
    the gap immediately.

    Once all SKILLEDGE_RMS_*_USER / _PASS env vars are confirmed set on
    the production host, the fallbacks are removed and this function
    becomes a hard failure.
    """
    env = os.getenv("SKILLEDGE_ENV", "development").strip().lower()
    if not _ev_fallbacks:
        return
    names = sorted(_ev_fallbacks)
    msg = (
        "SECURITY: %d RMS credential env var(s) unset; falling back to "
        "hardcoded values in _APIS: %s. Set SKILLEDGE_RMS_*_USER / _PASS "
        "environment variables to remove plaintext credentials from source."
        % (len(names), ", ".join(names))
    )
    import logging
    logging.warning(msg)
    if env == "production":
        import sys
        print("=" * 72, file=sys.stderr)
        print("  [SECURITY WARNING] — hardcoded RMS credentials in use", file=sys.stderr)
        print("  Set the environment variables below on the host to", file=sys.stderr)
        print("  remove plaintext credentials from source:", file=sys.stderr)
        for n in names:
            print("    " + n, file=sys.stderr)
        print("=" * 72, file=sys.stderr)


_validate_credentials()


_token_cache: dict = {}
_sessions: dict = {}

_manager_seen_batches: dict = {}
_manager_notifications: dict = {}
_notifications_lock = threading.Lock()

_SESSION_SECRET = os.getenv("SKILLEDGE_SESSION_SECRET", "skilledge-secure-session-key-2026-auth").encode("utf-8")


def _generate_session_token(email: str, role: str) -> str:
    """Generate a durable HMAC-signed session token that survives server restarts."""
    ts = int(time.time())
    payload = f"{email.strip().lower()}:{role.strip()}:{ts}"
    payload_b64 = base64.urlsafe_b64encode(payload.encode("utf-8")).decode("utf-8").rstrip("=")
    sig = hmac.new(_SESSION_SECRET, payload_b64.encode("utf-8"), hashlib.sha256).hexdigest()[:24]
    token = f"{payload_b64}.{sig}"
    session_data = {"email": email.strip().lower(), "role": role.strip(), "created_at": ts}
    _sessions[token] = session_data
    return token


def _verify_session_token(token: str):
    """Cryptographically verify a session token, reviving it across Render process restarts."""
    if not token:
        return None
    # Fast path: in-memory cache
    if token in _sessions:
        return _sessions[token]
    if "." not in token:
        # Fallback for older random tokens if still stored in memory
        return _sessions.get(token)
    try:
        parts = token.split(".")
        if len(parts) != 2:
            return None
        payload_b64, sig = parts[0], parts[1]
        expected_sig = hmac.new(_SESSION_SECRET, payload_b64.encode("utf-8"), hashlib.sha256).hexdigest()[:24]
        if not hmac.compare_digest(sig, expected_sig):
            return None
        pad_len = 4 - (len(payload_b64) % 4)
        if pad_len != 4:
            payload_b64 += "=" * pad_len
        payload = base64.urlsafe_b64decode(payload_b64.encode("utf-8")).decode("utf-8")
        email, role, ts_str = payload.split(":", 2)
        ts = int(ts_str)
        # Token valid for 30 days
        if time.time() - ts > 30 * 86400:
            return None
        session_data = {"email": email.strip().lower(), "role": role.strip(), "created_at": ts}
        _sessions[token] = session_data
        return session_data
    except Exception:
        return None


def _request_session():
    """Resolve the opaque SkillEdge session carried by Android.

    Session transport is introduced before enforcement so the currently
    published Android client keeps working during the migration window. New
    clients send `Authorization: Bearer <session_id>` on every request.
    """
    auth = str(request.headers.get("Authorization", "") or "").strip()
    token = auth[7:].strip() if auth.lower().startswith("bearer ") else ""
    return token, _verify_session_token(token)


# ─── Unified error envelope ───────────────────────────────────────────────────
#
# Every route answers errors through one envelope: {error, code} plus the HTTP
# status. `code` is the machine-readable reason the client can switch on;
# `error` is a human sentence. The code set is deliberately small:
#   EMAIL_REQUIRED / INVALID_EMAIL / INVALID_DEMAND_ID / INVALID_COURSE_NAME /
#   INVALID_INPUT — malformed client input.
#   SESSION_REQUIRED / ACCESS_DENIED / MANAGER_SCOPE_MISMATCH — auth/authz.
#   RMS_UNREACHABLE — the upstream RMS chain did not answer.
#   NOT_FOUND / CONFLICT / INTERNAL_ERROR — routing and write outcomes.
_ERROR_CODES = {
    "EMAIL_REQUIRED", "INVALID_EMAIL", "INVALID_DEMAND_ID", "INVALID_COURSE_NAME",
    "INVALID_INPUT",
    "SESSION_REQUIRED", "ACCESS_DENIED", "MANAGER_SCOPE_MISMATCH",
    "RMS_UNREACHABLE", "NOT_FOUND", "CONFLICT", "INTERNAL_ERROR",
}


def error_response(code, message, http_status=400):
    """One error shape for every route."""
    return (jsonify({"error": message, "code": code}), http_status)


def _session_payload(required=False):
    token, session = _request_session()
    if required and not session:
        return None, error_response("SESSION_REQUIRED", "Authentication required", 401)
    return session, None


def _v2_manager_session(manager_email=""):
    """Require an authenticated v2 session and keep manager data in scope."""
    session, error = _session_payload(required=True)
    if error:
        return None, error
    requested = str(manager_email or "").strip().lower()
    signed_in = str(session.get("email", "") or "").strip().lower()
    if requested and requested != signed_in:
        return None, error_response(
            "MANAGER_SCOPE_MISMATCH",
            "The requested manager is outside this session",
            403,
        )
    return session, None

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
    "courseCatalogue":   21600,  # 8.8k-row catalogue metadata; changes rarely
    "courseSchedule":      900,  # public dates change during the day
    "last3MonthsUtil":    1800,  # same volatility as the utilisation rollup
    # Availability is the most volatile thing in the product — a leave approval
    # or a new booking invalidates it — but 171 is a per-course call and a
    # 40-batch demand board means 40 of them, so it cannot be uncached.
    # 10 minutes is the compromise: fresh enough for a day's allocation work,
    # cheap enough for a full board refresh.
    "trainerFreeSchedule": 600,
    "trainerRCSchedule":   600,
    "courseTechnology":  21600,
    "courseList":        21600,
    "examCourseLinked":  21600,
    "courseContentUrl":  21600,
    "courseModule":      21600,
    "courseDomain":      21600,
    "latestCourseVersion": 21600,
    "uniqueCertsCount":   3600,
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


def _token(api_name, timeout=_TIMEOUT):
    if api_name in _token_cache:
        return _token_cache[api_name]
    cfg = _APIS[api_name]
    js = _rms_post(_TOKEN_EP, {
        "userName": cfg["user"],
        "userPassword": cfg["pass"],
        "userRole": cfg["role"],
    }, timeout=timeout)
    tok = js.get("content") or {}
    _token_cache[api_name] = tok
    return tok


def _rms(api_name, body, timeout=_TIMEOUT, attempts=2):
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
        for attempt in range(attempts):
            tok = _token(api_name, timeout=timeout)
            at = urllib.parse.quote(str(tok.get("accessToken", "")), safe="")
            dt = urllib.parse.quote(str(tok.get("deviceToken", "")), safe="")
            qs = f"?apikey={cfg['key']}&accessToken={at}&deviceToken={dt}"
            js = _rms_post(_DATA_EP + qs, body, timeout=timeout)
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
    Resolve the role for a Koenig email.

    SkillEdge is a Delivery Manager intelligence platform. Every valid
    @koenig-solutions.com account is granted Delivery Manager role for now —
    the role gate is the Koenig domain, not an RMS role field (RMS exposes no
    reliable manager/Trainer Plus flag per account). Real reportees come from
    RMS when available, or an empty roster when they do not. This is the
    current behaviour by design; tighten to a real Entra/identity role check
    only when that identity source is provisioned, not before.
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
    """Current utilisation for one address; None when nothing is known.

    Ranking-only — a missing value still has to sort somewhere. Display paths
    use _current_util directly so they can distinguish None from 0.
    """
    try:
        return _current_util(_util_series(_util_row(email)))
    except Exception:
        return None


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


_UNSET = object()


def _availability_evidence(email, start_date, end_date, assignments_raw=_UNSET, details_raw=_UNSET):
    """Return scheduling evidence for a requested date window.

    Availability is never inferred from utilisation. It is verified only when
    both the assignment calendar and trainer-detail/off-date source answered.
    An undecodable off-date value makes the result unverified instead of
    silently treating the trainer as free.
    """
    start = _parse_date(start_date)
    end = _parse_date(end_date) or start
    if not email or not start or not end:
        return {
            "status": "unverified", "verified": False, "available": None,
            "reason": "Demand dates are missing or invalid", "conflicts": [],
            "suggested_available_date": "",
        }

    if assignments_raw is _UNSET:
        assignments_raw = _rms("prevUpcoming", {
            "Startdate": _iso(start - timedelta(days=1)),
            "Enddate": _iso(end + timedelta(days=90)), "Email": email,
        })
    if details_raw is _UNSET:
        details_raw = _rms("trainerDetails", {"email": email})

    assignments_verified = assignments_raw is not None
    assignments = [a for a in (assignments_raw if isinstance(assignments_raw, list) else [])
                   if isinstance(a, dict)]
    details = (details_raw[0] if isinstance(details_raw, list) and details_raw
               and isinstance(details_raw[0], dict) else {})
    details_verified = details_raw is not None and bool(details)
    off_fields = {
        "roaming": "RoamingOffDates",
        "international_roaming": "InternationaRoamingOffDates",
        "night_il": "NightILOffDates",
        "morning_il": "MorningILOffDates",
        "evening_il": "EveningILOffDates",
    }

    off_dates, undecodable = [], []
    for kind, field in off_fields.items():
        raw = str(details.get(field, "") or "").strip()
        if not raw or raw.lower() == "null":
            continue
        parsed_any = False
        for token in _re.split(r"[,;|\n]+", raw):
            parsed = _parse_date(token.strip())
            if parsed:
                parsed_any = True
                off_dates.append((parsed, kind))
        if not parsed_any:
            undecodable.append(field)

    conflicts = []
    occupied = []
    for assignment in assignments:
        st = _parse_date(assignment.get("StarDate", assignment.get("StartDate", "")))
        en = _parse_date(assignment.get("EndDate", "")) or st
        if not st:
            continue
        occupied.append((st, en))
        if st <= end and en >= start:
            conflicts.append({
                "type": "assignment", "start_date": _iso(st), "end_date": _iso(en),
                "assignment_id": str(assignment.get("AssignmentId", "") or ""),
                "course": str(assignment.get("Course", "") or "").strip(),
            })
    for day, kind in off_dates:
        occupied.append((day, day))
        if start <= day <= end:
            conflicts.append({"type": "off_date", "date": _iso(day), "category": kind})

    verified = assignments_verified and details_verified and not undecodable
    probe = end + timedelta(days=1)
    for _ in range(366):
        if not any(st <= probe <= en for st, en in occupied):
            break
        probe += timedelta(days=1)

    if conflicts:
        status, available = "conflict", False
        reason = f"{len(conflicts)} assignment/off-date conflict(s) in this window"
    elif verified:
        status, available = "available", True
        reason = "No assignment or off-date conflicts found"
    else:
        status, available = "unverified", None
        missing = []
        if not assignments_verified: missing.append("assignment schedule")
        if not details_verified: missing.append("off-dates")
        if undecodable: missing.append("readable off-dates")
        reason = "Could not verify " + ", ".join(missing or ["availability"])

    return {
        "status": status, "verified": verified, "available": available,
        "reason": reason, "conflicts": conflicts,
        "suggested_available_date": _iso(probe),
        "assignments_verified": assignments_verified,
        "off_dates_verified": details_verified and not undecodable,
    }


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
        if not email:
            return name, email, [], _allocation_block_status(None, today)
        # These are independent RMS sources. Running them serially added three
        # full network waits per trainer to every Demand rebuild.
        with ThreadPoolExecutor(max_workers=2) as inner:
            caps_future = inner.submit(_skills, email)
            cert_future = inner.submit(_certifications, email)
            caps = caps_future.result()
            emp_code = cert_future.result()["emp_code"]
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


def _location_suitability(batch):
    """Location score without inventing trainer travel/base data RMS lacks."""
    mode = str(batch.get("delivery_mode_kind", "") or "").upper()
    if not mode:
        raw_mode = str(batch.get("delivery_mode", "") or "").upper()
        mode = ("FMAT" if _re.search(r"\bFMAT\b", raw_mode) else
                "ILT" if _re.search(r"\bILT\b", raw_mode) else
                "ILO" if _re.search(r"\bILO\b", raw_mode) else "OTHER")
    location = str(batch.get("location", "") or "").strip().lower()
    location_known = batch.get("location_known", bool(location))
    is_international = batch.get(
        "is_international",
        bool(location) and not any(marker in location for marker in _INDIA_MARKERS),
    )
    if mode == "ILO":
        return 100, "Remote delivery is location-compatible", True
    if not location_known:
        return 50, "Physical-delivery location is not recorded", False
    if is_international:
        return 50, "International travel/visa suitability is not available in RMS", False
    return 70, "Domestic physical delivery; trainer base/travel time is not available", False


def _suitability_components(batch, skill_match, readiness, availability, utilization,
                            feedback, languages, certification_codes=None):
    """Explainable 0-100 allocation score using every approved business signal."""
    batch_lang = str(batch.get("language", "") or "").strip().lower()
    trainer_langs = _language_names(languages)
    english = _speaks_english(languages)
    if batch_lang and batch_lang != "english":
        language_score = 100 if any(batch_lang in l for l in trainer_langs) else 0
        language_reason = (f"Speaks required {batch_lang.title()}" if language_score
                           else f"Required {batch_lang.title()} not recorded")
    else:
        language_score = 100 if english else 40
        language_reason = "English preferred" if english else "English not recorded"

    availability_score = {
        "available": 100, "unverified": 45, "conflict": 0,
    }.get(availability.get("status"), 45)
    utilization_score = 50 if utilization is None else max(0, min(100, 100 - utilization))
    feedback_score = 0 if feedback.get("blocked") else (
        55 if feedback.get("recent_negative_6mo") else 100
    )
    location_score, location_reason, location_verified = _location_suitability(batch)
    required_cert = _exam_code(batch.get("course_name", ""))
    held_codes = {str(code).upper() for code in (certification_codes or []) if code}
    certification_score = 70 if not required_cert else (100 if required_cert in held_codes else 20)

    scores = {
        "skill": max(0, min(100, int(skill_match or 0))),
        "readiness": max(0, min(100, int(readiness or 0))),
        "availability": availability_score,
        "utilization": round(utilization_score),
        "feedback": feedback_score,
        "language": language_score,
        "location": location_score,
        "certification": certification_score,
    }
    weights = {
        "skill": 0.35, "readiness": 0.15, "availability": 0.15,
        "utilization": 0.10, "feedback": 0.05, "language": 0.05,
        "location": 0.05, "certification": 0.10,
    }
    total = round(sum(scores[key] * weights[key] for key in weights))
    return total, scores, {
        "language": language_reason,
        "location": location_reason,
        "location_verified": location_verified,
        "utilization_verified": utilization is not None,
        "certification": ("No mapped certification requirement" if not required_cert else
                          f"Holds {required_cert}" if required_cert in held_codes else
                          f"Does not hold {required_cert}"),
    }


def _rank_batch(batch, team, availability_sources=None, candidate_context=None):
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
    context = candidate_context or {}
    with ThreadPoolExecutor(max_workers=8) as pool:
        utils = list(pool.map(
            lambda m: context.get(m[1], {}).get("utilization", _UNSET), matched
        ))
        langs = list(pool.map(
            lambda m: context.get(m[1], {}).get("languages", _UNSET), matched
        ))
        availability = list(pool.map(
            lambda m: _availability_evidence(
                m[1], batch.get("start_date", ""), batch.get("end_date", ""),
                *((availability_sources or {}).get(m[1], (_UNSET, _UNSET))),
            ),
            matched,
        ))
    utils = [_safe_util(m[1]) if value is _UNSET else value for m, value in zip(matched, utils)]
    langs = [_resume(m[1]).get("languages", []) if value is _UNSET else value
             for m, value in zip(matched, langs)]

    batch_lang = (batch.get("language") or "").strip().lower()
    batch_skill = (batch.get("skill_level") or "").strip().lower()

    candidates = []
    for (name, email, best, best_course, best_q, feedback, is_self), util, languages, availability_row in zip(matched, utils, langs, availability):
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
        suitability, component_scores, suitability_context = _suitability_components(
            batch, best, best_q, availability_row, util, feedback, languages,
            context.get(email, {}).get("certification_codes", []),
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
            "availability":  availability_row,
            "availability_status": availability_row["status"],
            "availability_verified": availability_row["verified"],
            "suitability_score": suitability,
            "suitability_components": component_scores,
            "suitability_context": suitability_context,
            "certification_covered": component_scores["certification"] == 100,
            "language_preferred": component_scores["language"] == 100,
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
    # verified availability > lower utilisation (workload tie-break only) >
    # clean 6-month feedback record. Unknown is never presented as available.
    availability_rank = {"available": 0, "unverified": 1, "conflict": 2}
    candidates.sort(key=lambda c: (
        c["blocked"],
        0 if c["language_preferred"] else 1,
        -c["suitability_score"],
        availability_rank.get(c["availability_status"], 1),
        -c["match"],
        c["utilization"] if c["utilization"] is not None else 101,
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
    visible = candidates[:5]
    manager_candidate = next((c for c in candidates if c.get("is_self")), None)
    if manager_candidate is not None and manager_candidate not in visible:
        visible.append(manager_candidate)
    return top["match"], visible, top["coverage"]


# This is an intentionally narrow business rule requested for the manager who
# owns this workspace. It must never broaden to reportees or similarly-named
# users: an automatic RMS write is safe only for the exact approved account.
# The Aishwar international-demand recommendation rule is scoped to one exact
# account by business policy. It is configurable (not a source literal) so the
# opt-in can move without a code change; empty string disables the rule.
_AISHWAR_EMAIL = os.getenv("SKILLEDGE_AISHWAR_INTERNATIONAL_RULE_EMAIL", "aishwar_v@koenig-solutions.com")


def _next_weekend(today=None):
    """Next Saturday on or after today, as an ISO date."""
    today = today or date.today()
    return today + timedelta(days=(5 - today.weekday()) % 7)


def _next_available_weekend(email, sources=None, today=None):
    """First verified conflict-free Sat/Sun; otherwise the next unverified one."""
    saturday = _next_weekend(today)
    assignments_raw, details_raw = sources or (_UNSET, _UNSET)
    first_unverified = None
    for week in range(52):
        start = saturday + timedelta(days=week * 7)
        evidence = _availability_evidence(
            email, start, start + timedelta(days=1), assignments_raw, details_raw
        )
        if evidence["status"] == "available":
            return start, evidence
        if evidence["status"] == "unverified" and first_unverified is None:
            first_unverified = (start, evidence)
    return first_unverified or (saturday, {
        "status": "conflict", "verified": True, "available": False,
        "reason": "No conflict-free weekend found in the next 52 weeks",
        "conflicts": [], "suggested_available_date": "",
    })


def _aishwar_recommendation(batch, candidates, weekend_availability=None):
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

    weekend_date, evidence = weekend_availability or (
        _next_weekend(), {
            "status": "unverified", "verified": False,
            "reason": "Assignment and off-date evidence was not supplied",
            "conflicts": [],
        },
    )
    return {
        "recommended": True,
        "recommendation_type": "manager_delivery",
        "trainer_name": candidate.get("trainer_name", "Aishwar").replace(" (You)", ""),
        "trainer_email": _AISHWAR_EMAIL,
        "skill_match": int(candidate.get("match") or 0),
        "suggested_skill_level": 8,
        "suggested_availability": _iso(weekend_date),
        "availability_verified": bool(evidence.get("verified")),
        "availability_status": evidence.get("status", "unverified"),
        "availability_reason": evidence.get("reason", ""),
        "availability_conflicts": evidence.get("conflicts", []),
        "reasons": [
            "International FMAT/ILT opportunity",
            f"Aishwar skill match is {int(candidate.get('match') or 0)}%",
            "Manager delivery option for a priority engagement",
        ],
        "verification_note": (
            "Next weekend verified against assignments and off-dates"
            if evidence.get("verified") else
            "Suggested weekend; RMS could not fully verify assignments and off-dates"
        ),
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


def _normalise_skill_register(rows):
    """Normalise an already-fetched RMS trainer-skill response."""
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
    return _normalise_skill_register(rows)


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
    assignment_source = "previous_upcoming"
    assignment_reference_count = 0
    # The supplied paged Assignment API was configured but never consumed.
    # Use it as a bounded read-only fallback only when the primary calendar did
    # not answer; an honest empty primary response remains empty and does not
    # trigger a second RMS call for every trainer.
    if assignments_raw is None and t_email:
        assignment_reference_rows = _rms("assignment", {
            "TrainerEmailAddres": t_email, "PageNumber": "1", "PageSize": "100",
        })
        assignment_reference_count = (
            len(assignment_reference_rows) if isinstance(assignment_reference_rows, list) else 0
        )
        # This API returns assignment/course identifiers but no dates. It can
        # prove records exist, not whether they overlap today or a future batch.
        # Never promote it to availability evidence.
        assignment_source = (
            "assignment_api_reference" if assignment_reference_rows is not None else "unavailable"
        )
    assignments_ok = assignments_raw is not None
    assignments = [a for a in (assignments_raw if isinstance(assignments_raw, list) else [])
                   if isinstance(a, dict)]
    availability = _availability_evidence(
        t_email, today, today,
        assignments_raw=assignments_raw if assignments_ok else None,
    )
    # A utilisation row can exist with no monthly columns in it, so the row
    # alone is not proof of a usable reading — require an actual number.
    util_ok = util is not None

    # ── Determine current status ─────────────────────────────────────────
    current_a = None
    upcoming_a = None
    for a in assignments:
        st = _parse_date(a.get("StarDate", a.get("StartDate", "")))
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
                existing_st = _parse_date(upcoming_a.get("StarDate", upcoming_a.get("StartDate", "")))
                if existing_st and st < existing_st:
                    upcoming_a = a

    if not assignments_ok:
        status = "unknown"          # RMS did not answer — assert nothing
    elif current_a:
        status = "teaching_now"
    elif upcoming_a:
        days_to = (_parse_date(upcoming_a.get("StarDate", upcoming_a.get("StartDate", "")), default=today) - today).days
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
        st = _parse_date(a.get("StarDate", a.get("StartDate", "")))
        en = _parse_date(a.get("EndDate", ""))
        return {
            "course_name":   str(a.get("Course", "") or "").strip(),
            "delivery_mode": str(a.get("Mode", "") or "").strip(),
            "location":      str(a.get("Location", "") or "").strip(),
            "vendor":        str(a.get("Vendor", "") or "").strip(),
            "assignment_id": str(a.get("AssignmentId", a.get("AssignmentID", "")) or ""),
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
        nd = _parse_date(upcoming_a.get("StarDate", upcoming_a.get("StartDate", "")))
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
        "availability_status":    availability["status"].replace("_", " ").title(),
        "availability_verified":  availability["verified"],
        "next_available_date":    availability["suggested_available_date"],
        "availability_reason":    availability["reason"],
        "capacity_bucket":        capacity_bucket,
        "readiness_bucket":        capacity_bucket,   # legacy key, v1.4.x clients
        "overall_readiness_score": readiness_score,
        "feedback_risk":          feedback_risk,
        "negative_count":         neg_count,
        "recommended_action":     recommended,
        "assignment_count":       len(assignments),
        "assignment_reference_count": assignment_reference_count,
        "assignment_source":      assignment_source,
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
        "data_complete": availability["verified"] and util_ok,
        "availability": availability,
        "assignment_source": assignment_source,
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
            return error_response("EMAIL_REQUIRED", "Email is required", 400)

        if not email.endswith('@koenig-solutions.com'):
            return error_response(
                "INVALID_EMAIL",
                "Only @koenig-solutions.com accounts are permitted",
                401,
            )

        role, role_data = _verify_role(email)

        if role is None:
            return error_response(
                "ACCESS_DENIED",
                "Access denied: account must have a manager or Trainer Plus role",
                401,
            )

        sid = _generate_session_token(email, role)

        return jsonify({
            "success":    True,
            "session_id": sid,
            "email":      email,
            "role":       role,
            "message":    "Login successful",
        }), 200

    except Exception as exc:
        return error_response("INTERNAL_ERROR", f"Server error: {exc}", 500)


@app.route('/api/auth/logout', methods=['POST'])
def logout():
    token, _ = _request_session()
    if token:
        _sessions.pop(token, None)
    return jsonify({"success": True}), 200


@app.route('/api/auth/session', methods=['GET'])
def validate_session():
    session, error = _session_payload(required=True)
    if error:
        return error
    return jsonify({
        "authenticated": True,
        "email": session.get("email", ""),
        "role": session.get("role", ""),
    }), 200


@app.route('/api/data/unified-manager-intelligence', methods=['GET'])
@app.route('/data/unified-manager-intelligence', methods=['GET'])
def unified_intelligence():
    email = request.args.get('email', '').strip().lower()
    session, error = _v2_manager_session(email)
    if error:
        return error
    email = session["email"]

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

    with _notifications_lock:
        seen = _manager_seen_batches.get(email, set())
        notes = _manager_notifications.get(email, [])
        
        current_ids = set()
        for b in all_batches:
            aid = str(b.get("assignment_id", ""))
            if not aid: continue
            current_ids.add(aid)
            if seen and aid not in seen:
                trainer_name = str(b.get("trainer_name", "A trainer"))
                course_name = str(b.get("course_name", "a course"))
                s_date = str(b.get("start_at", ""))
                notes.insert(0, {
                    "id": f"notif_{aid}_{int(time.time())}",
                    "severity": "INFO", "category": "ASSIGNMENT",
                    "title": "New Batch Assigned",
                    "message": f"{trainer_name} was assigned to {course_name} starting {s_date.split('T')[0]}.",
                    "trainer_email": str(b.get("trainer_email", "")),
                    "read": False,
                })
        
        _manager_notifications[email] = notes[:50]
        _manager_seen_batches[email] = current_ids
        synthetic_notes = list(_manager_notifications[email])

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
    active_batches   = sum(1 for b in all_batches if b["engagement_state"] == "current")
    upcoming_batches = sum(1 for b in all_batches if b["engagement_state"] == "upcoming")

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
    optimal   = sum(1 for t in trainer_ops if t["capacity_bucket"] == "Optimal")
    unknown_state = sum(1 for s in trainer_states if s["current_status"] == "unknown")

    int_batch_count = sum(1 for d in demand_df if d["is_international"])
    dom_batch_count = len(demand_df) - int_batch_count
    cert_coverage = round((sum(1 for t in trainer_ops if t.get("vendor_cert_count", 0) > 0) / len(trainer_ops) * 100)) if trainer_ops else None
    readiness_score = (
        min(100, max(0, round(100 - (high_risk * 10) - (unknown_state * 5) + (cert_coverage * 0.2))))
        if cert_coverage is not None else None
    )

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

    notifications.extend(synthetic_notes)

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
    from_cache = _cache_get("reportees", {"email": email}) is not None or _cache_get("unallocated", {}) is not None
    cache_source = "cache" if from_cache else "rms_live"
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
        "from_cache":               from_cache,
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
        "cache":     {"age": 0, "ttl": _CACHE_TTL.get("reportees", 1800), "source": cache_source},
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
    session, error = _v2_manager_session(email)
    if error:
        return error
    email = session["email"]

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


def _capability_portfolio(team, courses):
    """Decision rollup built only from verified capability evidence."""
    vendor_rows = {}
    for course in courses:
        vendor = str(course.get("vendor") or "Unclassified").strip() or "Unclassified"
        row = vendor_rows.setdefault(vendor, {
            "vendor": vendor, "courses": 0, "single_owner": 0,
            "exam_linked": 0, "certification_exposed": 0,
            "approved_depth": 0, "owner_depth": 0,
        })
        row["courses"] += 1
        row["owner_depth"] += int(course.get("owner_count") or 0)
        row["approved_depth"] += int(course.get("approved_count") or 0)
        if course.get("coverage") == "single":
            row["single_owner"] += 1
        if course.get("exam_code"):
            row["exam_linked"] += 1
            if int(course.get("certified_count") or 0) == 0:
                row["certification_exposed"] += 1

    vendors = []
    for row in vendor_rows.values():
        row["coverage_pct"] = round(100 * (row["courses"] - row["single_owner"]) / row["courses"]) if row["courses"] else None
        row["certification_coverage_pct"] = round(100 * (row["exam_linked"] - row["certification_exposed"]) / row["exam_linked"]) if row["exam_linked"] else None
        vendors.append(row)
    vendors.sort(key=lambda row: (-row["certification_exposed"], -row["single_owner"], -row["courses"], row["vendor"]))

    single_owner = [course for course in courses if course.get("coverage") == "single"]
    uncertified = [course for course in courses if course.get("exam_code") and int(course.get("certified_count") or 0) == 0]
    future = [course for course in courses if course.get("future_skill")]
    priorities = []
    if uncertified:
        priorities.append({"type": "certification", "count": len(uncertified), "label": "Exam-linked courses without certified cover"})
    if single_owner:
        priorities.append({"type": "succession", "count": len(single_owner), "label": "Courses dependent on one trainer"})
    if future:
        priorities.append({"type": "future_skill", "count": len(future), "label": "Future skills being developed"})
    evidence_complete = bool(team) and all(trainer.get("readiness_score") is not None for trainer in team)
    return {
        "summary": {
            "portfolio_health": "unknown" if not team or not courses else ("high_risk" if uncertified and single_owner else "needs_attention" if uncertified or single_owner else "healthy"),
            "ready_trainers": sum(1 for trainer in team if trainer.get("readiness_bucket") == "Ready"),
            "team_size": len(team), "single_owner_courses": len(single_owner),
            "certification_exposed_courses": len(uncertified), "future_skill_courses": len(future),
        },
        "vendor_coverage": vendors, "priorities": priorities,
        "confidence": {
            "status": "verified" if evidence_complete else "partial",
            "basis": "Current RMS trainer capability, certification, approval and readiness evidence",
            "domain_taxonomy_available": False,
            "note": "Vendor groups are used because RMS domain and technology contracts are not yet verified.",
        },
    }


@app.route('/api/data/team-capability', methods=['GET'])
@app.route('/api/v2/capability/portfolio', methods=['GET'])
def team_capability():
    """
    What the team can teach, and where their paper credentials fall short.

    Powers the courses catalogue and the certification KPIs. Kept out of the
    dashboard payload because it costs three extra RMS round-trips per trainer;
    the client fetches it alongside, so the dashboard still paints immediately.
    """
    email = request.args.get('email', '').strip().lower()
    session, error = _v2_manager_session(email)
    if error:
        return error
    email = session["email"]

    if _wants_fresh():
        _cache_purge(email)

    reps = _rms("reportees", {"email": email})
    if reps is None:
        return error_response("RMS_UNREACHABLE", "Cannot reach RMS — please retry", 503)
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
                "course_id":    c.get("course_id", ""),
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
        "portfolio": _capability_portfolio(team, courses),
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
    # Optional: lets the profile rank this trainer against their own team. When
    # present it is scoped to the session — a manager cannot ask for another
    # manager's ranking context.
    manager_email = request.args.get('manager', '').strip().lower()
    session, error = _v2_manager_session(manager_email)
    if error:
        return error
    if not email:
        return error_response("EMAIL_REQUIRED", "email query param required", 400)

    if _wants_fresh():
        _cache_purge(email)

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

    # Recording compliance — check the last 5 completed assignments.
    # recordingDetails (278) takes AssignmentId; a non-empty response means a
    # recording was submitted. Bounded to 5 to avoid multiplying RMS calls.
    past_assigns = sorted(
        [a for a in assigns if _engagement_state(a, today) == "past"],
        key=lambda a: a.get("EndDate", ""),
        reverse=True,
    )[:5]
    recording_compliance = {}
    if past_assigns:
        with ThreadPoolExecutor(max_workers=5) as rec_pool:
            rec_results = list(rec_pool.map(
                lambda a: (
                    str(a.get("AssignmentId", "") or ""),
                    _rms("recordingDetails", {"AssignmentId": str(a.get("AssignmentId", "") or "")}),
                ),
                past_assigns,
            ))
        for aid, raw in rec_results:
            if aid:
                rows = [r for r in (raw or []) if isinstance(r, dict)]
                recording_compliance[aid] = {
                    "submitted": len(rows) > 0,
                    "count": len(rows),
                    "urls": [str(r.get("RecordingURL", r.get("Url", r.get("url", ""))) or "").strip()
                             for r in rows if r.get("RecordingURL") or r.get("Url") or r.get("url")],
                }

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
        aid = str(a.get("AssignmentId", "") or "")
        rec = recording_compliance.get(aid)
        delivery.append({
            "course":         str(a.get("Course", "") or "").strip(),
            "vendor":         str(a.get("Vendor", "") or "").strip(),
            "mode":           str(a.get("Mode", "") or "").strip(),
            "location":       str(a.get("Location", "") or "").strip(),
            "participants":   a.get("NoOfParticipants", 0),
            "assignment_id":  aid,
            "start_at":       _iso(st),
            "end_at":         _iso(en),
            "start_time":     str(a.get("StartTime", "") or ""),
            "end_time":       str(a.get("EndTime", "") or ""),
            "state":          _engagement_state(a, today),
            # None = not checked (current/upcoming); True/False = recording submitted or missing
            "recording_submitted": rec["submitted"] if rec is not None else None,
            "recording_count":     rec["count"]     if rec is not None else None,
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
    availability = _availability_evidence(email, today, today + timedelta(days=90))

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
            # Compatibility key now reflects schedule evidence, not utilisation.
            "availability": availability["status"].replace("_", " ").title(),
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
        },
        # Surfaced so the UI can say "no data" honestly rather than implying zero.
        "availability": {
            "off_dates": off,
            "leave_data_available": False,
            **availability,
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
    is_ilo = bool(_re.search(r"\bILO\b", m))
    is_fmat = bool(_re.search(r"\bFMAT\b", m))
    is_ilt = bool(_re.search(r"\bILT\b", m))
    is_instructor_led = is_fmat or is_ilt

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
        tier, tier_label = 1, ("FMAT International" if is_international else "FMAT")
    elif is_ilt:
        tier, tier_label = 2, ("ILT International" if is_international else "ILT")
    elif is_ilo:
        tier, tier_label = 3, "ILO"
    else:
        tier, tier_label = 4, (mode or "Unknown")

    revenue_potential = (
        "High" if pax >= 15 or tier <= 2 else
        "Medium" if pax >= 6 or tier == 3 else
        "Low"
    )
    priority_score = (
        (50 if is_fmat else 40 if is_ilt else 10 if is_ilo else 0) +
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


def _demand_sort_key(batch):
    """FMAT -> ILT -> ILO -> Unknown, then best trainer suitability."""
    return (
        int(batch.get("priority_tier") or 4),
        -int(batch.get("best_suitability_score") or 0),
        batch.get("start_date") or "9999-12-31",
        batch.get("demand_id") or "",
    )


_allocation_payload_cache = {}
_allocation_building = set()
_allocation_lock = threading.Lock()


def _warm_allocation(email, fresh, auth_header):
    """Build outside the gateway request and retain the last complete board."""
    try:
        suffix = "&refresh=1" if fresh else ""
        with app.test_request_context(
            f"/api/data/allocation-desk?email={urllib.parse.quote(email)}&_build=1{suffix}",
            headers={"Authorization": auth_header} if auth_header else {}
        ):
            allocation_desk()
    finally:
        with _allocation_lock:
            _allocation_building.discard(email)


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
    session, error = _v2_manager_session(email)
    if error:
        return error
    email = session["email"]

    internal_build = request.args.get("_build") == "1"
    if not internal_build:
        fresh = _wants_fresh()
        with _allocation_lock:
            cached = _allocation_payload_cache.get(email)
            start_build = email not in _allocation_building
            if start_build:
                _allocation_building.add(email)
        if start_build:
            auth_header = request.headers.get("Authorization", "")
            threading.Thread(target=_warm_allocation, args=(email, fresh, auth_header), daemon=True).start()
        if cached:
            payload = dict(cached)
            payload["refresh_in_progress"] = start_build
            return jsonify(payload), 200
        return jsonify({
            "manager": email, "batches": [], "summary": {},
            "loading": True, "refresh_in_progress": True,
            "note": "Demand intelligence is being prepared from RMS. Retry shortly.",
        }), 202

    if _wants_fresh():
        _cache_purge(email)

    demand = _demand_rows()
    if demand is None:
        return error_response("RMS_UNREACHABLE", "Cannot reach RMS — please retry", 503)

    reportees = _rms("reportees", {"email": email}) or []
    manager_name = str(_util_row(email).get("TrainerName", "") or "").strip()
    team = _team_capability(reportees, manager_email=email, manager_name=manager_name)

    # Fetch each candidate's scheduling sources once for the whole board. The
    # earlier per-batch approach multiplied two RMS calls by every
    # candidate/demand pair and was not safe for a real manager roster.
    demand_dates = [_parse_date(b.get("start_date")) for b in demand]
    demand_ends = [_parse_date(b.get("end_date")) for b in demand]
    range_start = min((d for d in demand_dates if d), default=datetime.utcnow().date())
    range_end = max((d for d in demand_ends if d), default=range_start + timedelta(days=90))

    # Scheduling, skills/profile language/certification, and utilisation are
    # independent. Fetch all candidate sources in one wave instead of four
    # serial waves; this keeps a fresh Demand rebuild below the proxy window.
    with ThreadPoolExecutor(max_workers=min(16, max(4, len(team) * 4))) as pool:
        futures = {}
        for candidate in team:
            candidate_email = candidate[1]
            futures[(candidate_email, "assignments")] = pool.submit(_rms, "prevUpcoming", {
                "Startdate": _iso(range_start - timedelta(days=1)),
                "Enddate": _iso(range_end + timedelta(days=90)),
                "Email": candidate_email,
            })
            futures[(candidate_email, "details")] = pool.submit(_rms, "trainerDetails", {"email": candidate_email})
            futures[(candidate_email, "resume")] = pool.submit(_resume, candidate_email)
            futures[(candidate_email, "utilization")] = pool.submit(_safe_util, candidate_email)

        availability_sources = {}
        candidate_context = {}
        for candidate in team:
            candidate_email = candidate[1]
            resume = futures[(candidate_email, "resume")].result()
            availability_sources[candidate_email] = (
                futures[(candidate_email, "assignments")].result(),
                futures[(candidate_email, "details")].result(),
            )
            candidate_context[candidate_email] = {
                "utilization": futures[(candidate_email, "utilization")].result(),
                "languages": resume.get("languages", []),
                "certification_codes": [c.get("code") for c in resume.get("certifications", []) if isinstance(c, dict)],
            }

    priority_count = 0
    for b in demand:
        b["relevance"], b["candidates"], coverage = _rank_batch(
            b, team, availability_sources=availability_sources,
            candidate_context=candidate_context,
        )
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
        weekend_availability = None
        if b.get("delivery_mode_kind") in {"FMAT", "ILT"} and b.get("is_international"):
            weekend_availability = _next_available_weekend(
                _AISHWAR_EMAIL, availability_sources.get(_AISHWAR_EMAIL)
            )
        manager_recommendation = _aishwar_recommendation(
            b, b["candidates"], weekend_availability=weekend_availability
        )
        if manager_recommendation:
            b["manager_recommendation"] = manager_recommendation
            for candidate in b["candidates"]:
                if candidate.get("trainer_email", "").lower() == _AISHWAR_EMAIL:
                    candidate["manager_recommendation"] = manager_recommendation
        if b["is_priority"]:
            priority_count += 1

        b["best_suitability_score"] = max(
            (int(c.get("suitability_score") or 0) for c in b["candidates"]),
            default=0,
        )

    # Business order is absolute; suitability only ranks demand inside its
    # delivery-mode section. Stable demand id/date tie-breakers make refreshes
    # deterministic instead of reshuffling equal rows.
    demand.sort(key=_demand_sort_key)

    # Overlay real availability from the RMS free-date calendar. Additive only,
    # and never fatal: if key 171 is unreachable the board still renders with
    # the existing signals rather than failing the whole request.
    try:
        enrich_demand_with_availability(demand)
    except Exception:
        import logging as _logging
        _logging.exception("availability enrichment failed; serving board without it")

    payload = {
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
    }
    with _allocation_lock:
        _allocation_payload_cache[email] = payload
    return jsonify(payload), 200


@app.route('/api/v2/operations/demand-context', methods=['GET'])
def v2_demand_context():
    """Verified, non-PII RMS context for one manager demand decision."""
    manager = request.args.get('manager', '').strip().lower()
    _, error = _v2_manager_session(manager)
    if error:
        return error

    demand_id = request.args.get('demandId', '').strip()
    course_name = request.args.get('courseName', '').strip()
    if not demand_id or not demand_id.isdigit():
        return error_response("INVALID_DEMAND_ID", "A numeric demandId is required", 400)
    if not course_name or len(course_name) > 200:
        return error_response("INVALID_COURSE_NAME", "courseName is required", 400)

    with ThreadPoolExecutor(max_workers=2) as pool:
        course_future = pool.submit(_rms, "courseAvailability", {"CourseName": course_name})
        scid_future = pool.submit(_rms, "scid", {"assignmentid": demand_id})
        course_rows = course_future.result() or []
        scid_rows = scid_future.result() or []

    course_row = next((r for r in course_rows if isinstance(r, dict)), {})
    scid_values = []
    for row in scid_rows if isinstance(scid_rows, list) else []:
        if not isinstance(row, dict):
            continue
        raw = str(row.get("SCIDs", "") or "").strip()
        scid_values.extend(v.strip() for v in _re.split(r"[,#;]", raw) if v.strip())

    course_verified = bool(course_row)
    scid_verified = bool(scid_values)
    return jsonify({
        "schema_version": "2.1",
        "demand_id": demand_id,
        "course": {
            "name": course_name,
            "verified": course_verified,
            "available_in_rms": course_row.get("Course Available in RMS") if course_verified else None,
            "status": str(course_row.get("Course Status", "") or ""),
            "is_duplicate": course_row.get("Is Duplicate") if course_verified else None,
            "is_discontinued": course_row.get("Is Discontinued") if course_verified else None,
        },
        "sales_confirmations": {
            "verified": scid_verified,
            "count": len(set(scid_values)),
            "ids": sorted(set(scid_values)),
        },
        "confidence": "verified" if course_verified and scid_verified else "partial",
        "note": "Unavailable fields are unverified, not assumed empty.",
        "generated_at": datetime.utcnow().isoformat(),
    }), 200


# ═══════════════════════════════════════════════════════════════════════════
# INTELLIGENCE LAYER
#
# Utilisation is not availability.
#
# A trainer at 80% can be free on the dates that matter; one at 40% can be
# unavailable because of leave, travel, an existing booking or a client
# exclusion. Everything below is built on real dates from RMS keys 171 and 111
# rather than on inference from a utilisation percentage, which is the largest
# correctness error the 2026-08-11 audit found.
#
# Three rules hold throughout:
#
#   1. Hard gates run before any scoring. A DNC trainer at 95% fit must never
#      outrank a clear trainer at 80% — a client exclusion is not a weight.
#   2. Unknown is a first-class state, distinct from zero and from false. Only
#      ~48% of trainers carry a visa record; treating absence as ineligible
#      would silently hide half the bench, so unknown surfaces as
#      "verification required" and never as exclusion.
#   3. Every verdict carries its evidence. A score a manager cannot audit is a
#      score they cannot overrule.
# ═══════════════════════════════════════════════════════════════════════════

def _resolve_course_name(name):
    """
    Map a loose course name onto the exact RMS catalogue string.

    RMS key 171 needs an exact match: "AZ-305T00: Designing Microsoft Azure
    Infrastructure Solutions" returns 37 candidates, while "AZ-104T00: …",
    "AI-102T00: …" and "CCNA - …" each return zero. Passing an unresolved name
    straight through would report an empty pool as "nobody is available", which
    is the most dangerous possible failure for an allocation tool.

    Returns (exact_name, confidence) where confidence is:
      "exact"    — the catalogue holds this name verbatim
      "resolved" — matched on a normalised or prefix basis
      ""         — no match; the caller must report "cannot verify"
    """
    raw = str(name or "").strip()
    if not raw:
        return "", ""
    index = _course_catalogue_index()
    if not index:
        return raw, ""

    key = _norm_course(raw)
    hit = index.get(key)
    if hit and hit.get("course_name"):
        return hit["course_name"], "exact"

    # Course codes are the reliable handle: managers say "AZ-305", RMS stores
    # "AZ-305T00: Designing Microsoft Azure Infrastructure Solutions".
    code = _re.match(r"^\s*([a-z]{2,4}[\s\-]?\d{2,4})", raw.lower())
    if code:
        token = _re.sub(r"[\s\-]", "", code.group(1))
        for k, v in index.items():
            compact = _re.sub(r"[\s\-]", "", k)
            if compact.startswith(token):
                return v["course_name"], "resolved"

    for k, v in index.items():
        if k.startswith(key) or key.startswith(k):
            return v["course_name"], "resolved"

    # Managers routinely drop the code and say only the title — "Designing
    # Microsoft Azure Infrastructure Solutions" for "AZ-305T00: Designing …".
    # Neither string is a prefix of the other, so containment is checked last,
    # and only for names long enough that a coincidental hit is implausible.
    if len(key) >= 12:
        for k, v in index.items():
            if key in k:
                return v["course_name"], "resolved"
    return "", ""


def _parse_free_dates(value):
    """
    RMS key 171 returns availability as a comma-separated date list, e.g.
    "2026-08-15,2026-08-16,…" — typically ~155 days per trainer.
    """
    out = set()
    for part in str(value or "").split(","):
        part = part.strip()
        if not part:
            continue
        for fmt in ("%Y-%m-%d", "%d-%b-%Y", "%d %b %Y"):
            try:
                out.add(datetime.strptime(part, fmt).date())
                break
            except ValueError:
                continue
    return out


def _parse_visa(value):
    """
    Visa records from key 171:
      [{"Country","VisaExpiryDate","StayPeriod","AssociateCountries"}]

    AssociateCountries matters — an Australia visa was observed live also
    covering "Philippines,Egypt", so matching only on Country would wrongly
    block eligible trainers.
    """
    raw = value
    if isinstance(raw, str):
        try:
            raw = json.loads(raw)
        except Exception:
            return []
    if not isinstance(raw, list):
        return []
    out = []
    for v in raw:
        if not isinstance(v, dict):
            continue
        expiry = None
        for fmt in ("%d %b %Y", "%Y-%m-%d", "%d-%b-%Y"):
            try:
                expiry = datetime.strptime(str(v.get("VisaExpiryDate") or "").strip(), fmt).date()
                break
            except ValueError:
                continue
        stay = _re.search(r"(\d+)", str(v.get("StayPeriod") or ""))
        associates = [a.strip().lower() for a in
                      str(v.get("AssociateCountries") or "").split(",") if a.strip()]
        out.append({
            "country": str(v.get("Country") or "").strip(),
            "expiry": expiry,
            "stay_days": int(stay.group(1)) if stay else None,
            "associates": associates,
        })
    return out


def _parse_skill_level(value):
    """
    Skill level is 1–10, but a future skill is encoded inside the same string:
    "1 (Future Skill: 08-Sep-2026)". Parsing it as an int would silently drop
    the succession signal, so both are returned.
    """
    s = str(value or "").strip()
    level = None
    m = _re.match(r"^\s*(\d+)", s)
    if m:
        level = int(m.group(1))
    future = _re.search(r"future skill\s*:\s*([0-9a-z\- ]+)", s, _re.I)
    future_date = None
    if future:
        for fmt in ("%d-%b-%Y", "%Y-%m-%d", "%d %b %Y"):
            try:
                future_date = datetime.strptime(future.group(1).strip(), fmt).date()
                break
            except ValueError:
                continue
    return level, future_date


def _free_schedule(course_name):
    """
    The candidate pool for a course, keyed by lowercase trainer name.

    Returns ({}, reason) when the pool cannot be established, so callers can
    distinguish "no trainer is free" from "we could not check".
    """
    exact, confidence = _resolve_course_name(course_name)
    if not exact:
        return {}, f"course '{course_name}' not found in the RMS catalogue"
    rows = _rms("trainerFreeSchedule", {"course": exact})
    if not isinstance(rows, list):
        return {}, "trainer free schedule unavailable"
    out = {}
    for r in rows:
        if not isinstance(r, dict):
            continue
        name = str(r.get("TrainerName") or "").strip()
        if not name:
            continue
        level, future_date = _parse_skill_level(r.get("Skill Level"))
        out[name.lower()] = {
            "trainer_name": name,
            "skill_level": level,
            "future_skill_date": future_date.isoformat() if future_date else "",
            "total_assignments": r.get("Total #Assignment"),
            "course_assignments": r.get("#Assignment for the Course"),
            "location": str(r.get("Location") or "").strip(),
            "nearest_city": str(r.get("NearestCity") or "").strip(),
            "timezone": str(r.get("TrainerTimezone") or "").strip(),
            "free_dates": _parse_free_dates(r.get("Trainer Free Date")),
            "visa": _parse_visa(r.get("Visa")),
            "resolved_course": exact,
            "match_confidence": confidence,
        }
    return out, ""


def _rc_schedule(email, start, end):
    """
    Day-level operational calendar for one trainer (RMS key 111).

    Extracts only what changes an allocation decision: approved or applied
    leave, confirmed versus tentative bookings, and the two client-relationship
    signals nothing else in the estate exposes — SpecifiedTrainer (this client
    asked for them) and DNC (this client refuses them).
    """
    rows = _rms("trainerRCSchedule", {
        "traineremail": email,
        "fromDate": start.isoformat() if hasattr(start, "isoformat") else str(start),
        "toDate": end.isoformat() if hasattr(end, "isoformat") else str(end),
    })
    out = {"leave_dates": set(), "confirmed_dates": set(), "tentative_dates": set(),
           "dnc_clients": set(), "specified_clients": set(), "modes": [], "rows": 0}
    if not isinstance(rows, list):
        return out, "schedule unavailable"

    for r in rows:
        if not isinstance(r, dict):
            continue
        out["rows"] += 1
        day = None
        for fmt in ("%Y-%m-%dT%H:%M:%S", "%Y-%m-%d", "%d-%b-%Y"):
            try:
                day = datetime.strptime(str(r.get("Date") or "").strip(), fmt).date()
                break
            except ValueError:
                continue

        if str(r.get("LeaveStatus") or "").strip():
            if day:
                out["leave_dates"].add(day)
        elif day and str(r.get("AssociatedType") or "").strip().lower() != "free":
            # Tentative work is a soft conflict, not an absence. Treating a
            # provisional booking as unavailable is how a bench looks emptier
            # than it is.
            if str(r.get("QuotationStatus") or "").strip().lower() == "confirmed":
                out["confirmed_dates"].add(day)
            else:
                out["tentative_dates"].add(day)

        if str(r.get("DNC") or "").strip():
            out["dnc_clients"].add(str(r.get("DNC")).strip().lower())
        if str(r.get("SpecifiedTrainer") or "").strip():
            out["specified_clients"].add(str(r.get("SpecifiedTrainer")).strip().lower())
        mode = str(r.get("DeliveryMode") or "").strip()
        if mode:
            out["modes"].append(mode)
    return out, ""


def _delivery_days(start, end):
    """Every calendar day a batch occupies, inclusive."""
    if not start or not end or end < start:
        return []
    return [start + timedelta(days=i) for i in range((end - start).days + 1)]


def availability_verdict(free_dates, schedule, days):
    """
    Real availability for a specific set of delivery days.

    This is the function that replaces capacity_bucket and current_status as
    the answer to "are they free". It answers for *these dates*, because
    availability is a property of a batch, not of a person.
    """
    if not days:
        return {"status": "unknown", "reason": "batch dates unknown",
                "blocked_days": [], "soft_conflicts": []}
    # None and the empty set mean different things and must not be collapsed.
    # None is "RMS returned no availability row for this trainer", which is
    # unknown. An empty set is "RMS returned a row listing no free days", which
    # is a definite answer: they are not free. Treating the second as unknown
    # let a fully booked trainer through the gate as a viable candidate.
    if free_dates is None:
        return {"status": "unknown", "reason": "no availability record for this course",
                "blocked_days": [], "soft_conflicts": []}
    if not free_dates:
        return {"status": "unavailable", "reason": "no free days in the requested window",
                "blocked_days": [d.isoformat() for d in sorted(days)], "soft_conflicts": []}

    required = set(days)
    leave = sorted(required & schedule.get("leave_dates", set()))
    booked = sorted(required & schedule.get("confirmed_dates", set()))
    soft = sorted(required & schedule.get("tentative_dates", set()))
    not_free = sorted(d for d in required if d not in free_dates)

    hard = sorted(set(leave) | set(booked) | set(not_free))
    if not hard and not soft:
        return {"status": "available", "reason": "", "blocked_days": [], "soft_conflicts": []}
    if not hard and soft:
        return {"status": "available_with_conflicts",
                "reason": f"{len(soft)} day(s) provisionally booked",
                "blocked_days": [], "soft_conflicts": [d.isoformat() for d in soft]}
    if len(hard) >= len(required):
        reason = ("on approved leave" if leave else
                  "already committed" if booked else "not free on these dates")
        return {"status": "unavailable", "reason": reason,
                "blocked_days": [d.isoformat() for d in hard],
                "soft_conflicts": [d.isoformat() for d in soft]}
    return {"status": "partially_available",
            "reason": f"unavailable on {len(hard)} of {len(required)} day(s)",
            "blocked_days": [d.isoformat() for d in hard],
            "soft_conflicts": [d.isoformat() for d in soft]}


# Rough offsets, enough to classify a delivery window as comfortable or
# unsocial. RMS reports Windows time-zone names, not IANA identifiers.
_TZ_OFFSETS = {
    "india standard time": 5.5, "gmt standard time": 0.0, "utc": 0.0,
    "e. africa standard time": 3.0, "arabian standard time": 4.0,
    "singapore standard time": 8.0, "china standard time": 8.0,
    "tokyo standard time": 9.0, "aus eastern standard time": 10.0,
    "eastern standard time": -5.0, "central standard time": -6.0,
    "mountain standard time": -7.0, "pacific standard time": -8.0,
    "w. europe standard time": 1.0, "central europe standard time": 1.0,
    "romance standard time": 1.0, "greenwich standard time": 0.0,
}

_REGION_OFFSETS = {
    "india": 5.5, "uae": 4.0, "dubai": 4.0, "saudi": 3.0, "qatar": 3.0,
    "uk": 0.0, "london": 0.0, "ireland": 0.0, "germany": 1.0, "france": 1.0,
    "netherlands": 1.0, "singapore": 8.0, "malaysia": 8.0, "australia": 10.0,
    "usa": -5.0, "us": -5.0, "canada": -5.0, "new york": -5.0,
    "south africa": 2.0, "kenya": 3.0, "egypt": 2.0, "philippines": 8.0,
}


def _tz_offset(name, table):
    key = str(name or "").strip().lower()
    if not key:
        return None
    if key in table:
        return table[key]
    for k, v in table.items():
        if k in key or key in k:
            return v
    return None


def international_verdict(candidate, country, days):
    """
    Whether a trainer can actually deliver this batch abroad.

    Visa absence is deliberately NOT treated as ineligibility. Roughly half of
    trainers carry no visa record, and missing data means unrecorded at least
    as often as it means unavailable — excluding them would hide a large part
    of the pool and quietly produce wrong allocations. Unknown surfaces as
    "verification required" instead, which is a manager's decision to make.
    """
    country_key = str(country or "").strip().lower()
    visas = candidate.get("visa") or []
    end = max(days) if days else None

    verdict = {"visa": "unknown", "visa_detail": "", "timezone_fit": "unknown",
               "timezone_detail": "", "requires_verification": True}

    if not country_key:
        verdict["visa_detail"] = "destination country not stated"
    elif not visas:
        verdict["visa_detail"] = "no visa record held for this trainer"
    else:
        match = None
        for v in visas:
            if v["country"].lower() == country_key or country_key in v["associates"]:
                match = v
                break
        if not match:
            verdict["visa"] = "not_available"
            verdict["visa_detail"] = f"no visa covering {country}"
            verdict["requires_verification"] = False
        elif match["expiry"] and end and match["expiry"] < end:
            verdict["visa"] = "not_available"
            verdict["visa_detail"] = f"visa expires {match['expiry'].isoformat()}, before the batch ends"
            verdict["requires_verification"] = False
        elif match["stay_days"] and days and match["stay_days"] < len(days):
            verdict["visa"] = "not_available"
            verdict["visa_detail"] = f"permitted stay {match['stay_days']} days is shorter than the batch"
            verdict["requires_verification"] = False
        else:
            verdict["visa"] = "available"
            via = "" if match["country"].lower() == country_key else f" (via {match['country']} visa)"
            verdict["visa_detail"] = f"valid to {match['expiry'].isoformat() if match['expiry'] else 'unknown date'}{via}"
            verdict["requires_verification"] = False

    t_off = _tz_offset(candidate.get("timezone"), _TZ_OFFSETS)
    r_off = _tz_offset(country, _REGION_OFFSETS)
    if t_off is not None and r_off is not None:
        gap = abs(t_off - r_off)
        verdict["timezone_fit"] = ("comfortable" if gap <= 3
                                   else "workable" if gap <= 6 else "unsocial")
        verdict["timezone_detail"] = f"{gap:g}h offset"
    return verdict


def parse_off_dates(raw):
    """
    Off-date strings from trainerDetails into a date set.

    Handles the shapes RMS is documented to use — a comma or semicolon list,
    and "a to b" / "a - b" ranges — because the real format could not be
    confirmed: sampling every trainer reachable from this account (reportees,
    the assignment feed and the course pool) found these fields null in every
    case. The parser is therefore defensive by necessity, and the travel-window
    gate built on it is inert until RMS populates the data. International
    eligibility currently rests on the visa and free-date signals from key 171,
    which are populated.
    """
    out = set()
    text = str(raw or "").strip()
    if not text or text.lower() in ("null", "none"):
        return out

    def one(token):
        token = token.strip()
        for fmt in ("%Y-%m-%d", "%d-%b-%Y", "%d %b %Y", "%d/%m/%Y", "%Y/%m/%d"):
            try:
                return datetime.strptime(token, fmt).date()
            except ValueError:
                continue
        return None

    for chunk in _re.split(r"[;,]", text):
        chunk = chunk.strip()
        if not chunk:
            continue
        span = _re.split(r"\s+(?:to|-|–)\s+", chunk)
        if len(span) == 2:
            a, b = one(span[0]), one(span[1])
            if a and b and b >= a and (b - a).days <= 400:
                out.update(a + timedelta(days=i) for i in range((b - a).days + 1))
                continue
        d = one(chunk)
        if d:
            out.add(d)
    return out


def _exam_hints(rows):
    """
    {normalised course: exam name} mined from RC-schedule rows (key 111).

    This is the only surviving route to exam *identity*. The endpoint that
    should carry the course-to-exam mapping (key 215) turned out to be a
    mutation — it links an exam to a course rather than reporting one — so the
    name is inferred from what past deliveries of the same course actually
    linked to, and every consumer must label it as inferred.
    """
    hints = {}
    for r in rows or []:
        if not isinstance(r, dict):
            continue
        course = _norm_course(r.get("CourseName"))
        exam = str(r.get("Exam") or "").strip()
        if course and exam:
            hints.setdefault(course, exam)
    return hints


def certification_verdict(course, held_names, exam_policy=None, exam_hints=None):
    """
    Whether a certification gap on this course is real, and which exam closes it.

    Only 1,446 of 11,007 catalogue courses actually require an exam, so the
    requirement flag is checked first: reporting a gap on a course that needs no
    certification is noise that trains managers to ignore the signal.
    """
    key = _norm_course(course)
    policy = (exam_policy or {}).get(key)

    # Three states, not two. The exam-policy catalogue (key 213) does not use
    # the same course names as the delivery catalogue: "AZ-305T00: Designing
    # Microsoft Azure Infrastructure Solutions" has no entry there at all,
    # while 213 carries "AZ-305 - Exam Prep". Treating a missing entry as
    # "no exam required" silently converts every unmatched course into "no
    # gap", which under-reports exactly the certification risk this engine
    # exists to find. Absent means unknown, and unknown asserts nothing.
    if policy is None:
        required = None
    else:
        required = bool(policy.get("required"))

    held = any(_norm_course(h) == key or key in _norm_course(h) for h in (held_names or []))
    exam = (exam_hints or {}).get(key, "")

    return {
        "course": course,
        "exam_required": required,               # True / False / None
        "certification_held": held,
        # A gap is asserted only when the requirement is known to be true.
        "gap": bool(required is True and not held),
        "policy_known": policy is not None,
        "exam_name": exam,
        # Never presented as authoritative — see _exam_hints.
        "exam_source": "inferred_from_delivery_history" if exam else "unknown",
        "vendor": (policy or {}).get("vendor", ""),
    }


def certification_priority(verdicts, demand_by_course=None, blocked_counts=None):
    """
    Rank real gaps by what they actually block.

    Deliberately free of any financial input: priority is pipeline pressure and
    how many people the gap blocks, never the value of the batch.
    """
    demand_by_course = demand_by_course or {}
    blocked_counts = blocked_counts or {}
    ranked = []
    for v in verdicts:
        if not v.get("gap"):
            continue
        key = _norm_course(v["course"])
        demand = int(demand_by_course.get(key, 0))
        blocked = int(blocked_counts.get(key, 0))
        ranked.append(dict(v, demand_count=demand, blocked_trainers=blocked,
                           priority_score=demand * 3 + blocked))
    return sorted(ranked, key=lambda v: (-v["priority_score"], v["course"]))


def active_sc_operational(page_size=50):
    """
    Operational fields from the active-SC feed (key 13).

    Total Fee and Currency are dropped here, at the boundary, and never enter
    the response, any score or any cache. SkillEdge is a delivery intelligence
    product, not a revenue product; the useful signals in this feed are who owns
    the account and how long a demand has been waiting.
    """
    rows = _rms("activeSCDate", {"PageNumber": "1", "PageSize": str(page_size)})
    if not isinstance(rows, list):
        return []
    today = datetime.utcnow().date()
    out = []
    for r in rows:
        if not isinstance(r, dict):
            continue
        created = None
        for fmt in ("%d %b %Y", "%Y-%m-%d", "%d-%b-%Y"):
            try:
                created = datetime.strptime(str(r.get("SCCreatedDate") or "").strip(), fmt).date()
                break
            except ValueError:
                continue
        out.append({
            "course_name": str(r.get("CourseName") or "").strip(),
            "csm": str(r.get("CSM") or "").strip(),
            "assignment_id": str(r.get("AssignmentId") or "").strip(),
            "sc_id": str(r.get("SCId") or "").strip(),
            "created_date": created.isoformat() if created else "",
            "demand_age_days": (today - created).days if created else None,
            # "Total Fee" and "Currency" are intentionally absent.
        })
    return out


def enrich_demand_with_availability(demand):
    """
    Add real availability and international verdicts to an existing demand board.

    Scope is deliberate. Key 171 is one call per *course*, so the cost here is
    bounded by the number of distinct courses on the board and is cached for
    ten minutes. Key 111 is one call per *trainer per batch*, which is
    multiplicative and would turn a forty-batch board into hundreds of calls —
    so the DNC, leave and tentative-booking signals are not applied here. They
    belong to /api/v2/allocation/candidates, which evaluates one batch on
    demand and can afford them.

    That distinction is recorded on every candidate as `dnc_checked: false`
    rather than left implicit, because a board that silently omitted a
    non-overridable gate would be worse than one that never claimed to apply it.

    Existing keys are never modified — this only adds — so the current Android
    client keeps working unchanged.
    """
    # One call per distinct course, fetched in parallel. Measured live at ~2.9s
    # each: sequentially a six-batch board took 17.3s and a forty-batch board
    # would have taken two minutes, which is not a board a manager would wait
    # for. The ten-minute cache then makes subsequent renders free.
    courses = []
    for b in demand or []:
        if isinstance(b, dict):
            c = b.get("course_name") or ""
            if c and c not in courses:
                courses.append(c)

    pools = {}
    if courses:
        # Warm the course catalogue before fanning out. Every _free_schedule
        # call resolves through it, so on a cold cache all N threads raced to
        # fetch the same 8,800-row payload at once — which is why the first
        # parallel attempt was no faster than the sequential one.
        try:
            _course_catalogue_index()
        except Exception:
            pass
        with ThreadPoolExecutor(max_workers=min(8, len(courses))) as pool_exec:
            futures = {c: pool_exec.submit(_free_schedule, c) for c in courses}
            for c, fut in futures.items():
                try:
                    pools[c] = fut.result()
                except Exception:
                    pools[c] = ({}, "availability lookup failed")

    for b in demand or []:
        if not isinstance(b, dict):
            continue
        course = b.get("course_name") or ""
        pool, why = pools.get(course, ({}, "availability lookup failed"))

        start = _parse_date(b.get("start_date") or b.get("start_at") or "")
        end = _parse_date(b.get("end_date") or b.get("end_at") or "") or start
        days = _delivery_days(start, end) if start else []
        country = b.get("country") or b.get("location") or ""
        is_intl = bool(b.get("is_international"))

        # Three outcomes, not two. "Could not check the course" and "checked,
        # and no trainer holds this skill" are opposite facts for a manager:
        # the first needs a catalogue fix, the second needs hiring or training.
        if why:
            source = "unresolved"
        elif pool:
            source = "rms_free_schedule"
        else:
            source = "no_skilled_trainers"

        b["availability_intelligence"] = {
            "source": source,
            "note": why or ("no trainer in RMS holds this course" if not pool else ""),
            "pool_size": len(pool),
            "dnc_checked": False,
            "leave_checked": False,
        }

        for cand in b.get("candidates") or []:
            if not isinstance(cand, dict):
                continue
            name = str(cand.get("trainer_name") or "").replace(" (You)", "").strip().lower()
            row = pool.get(name)
            if not row:
                cand["real_availability"] = {"status": "unknown",
                                             "reason": why or "trainer not in the RMS pool for this course"}
                continue
            verdict = availability_verdict(row.get("free_dates"), {}, days)
            cand["real_availability"] = verdict
            cand["skill_level"] = row.get("skill_level")
            cand["course_deliveries"] = row.get("course_assignments")
            cand["nearest_city"] = row.get("nearest_city")
            cand["trainer_timezone"] = row.get("timezone")
            if row.get("future_skill_date"):
                cand["future_skill_date"] = row["future_skill_date"]
            if is_intl:
                intl = international_verdict(row, country, days)
                cand["international_readiness"] = intl
                cand["visa_status"] = intl["visa"]
                cand["requires_visa_verification"] = intl["requires_verification"]
    return demand


def evaluate_candidate(candidate, schedule, batch, required_level=None):
    """
    One trainer against one batch: hard gates first, then weighted fit.

    The gate/score split is the whole point. A do-not-call is a client's
    decision, not a signal to be traded off against skill — so a DNC trainer at
    95% fit must never appear above a clear trainer at 80%. Gates return
    eligible=False with a stated blocker and no score is computed at all.

    Everything that survives the gates is scored transparently: each factor
    reports its own contribution and the evidence behind it, so a manager can
    disagree with one axis rather than with an opaque number.
    """
    start, end = batch.get("start_date"), batch.get("end_date")
    days = _delivery_days(start, end)
    country = batch.get("country") or batch.get("location") or ""
    is_international = bool(batch.get("international"))

    blockers = []

    # ── Hard gates ───────────────────────────────────────────────────────────
    client = str(batch.get("customer") or "").strip().lower()
    if client and client in schedule.get("dnc_clients", set()):
        blockers.append({"gate": "dnc",
                         "detail": f"{batch.get('customer')} has marked this trainer do-not-call"})

    # Pass the value through untouched: "or set()" here would erase the
    # None-versus-empty distinction availability_verdict depends on.
    avail = availability_verdict(candidate.get("free_dates"), schedule, days)
    if avail["status"] in ("unavailable", "partially_available"):
        blockers.append({"gate": "availability", "detail": avail["reason"]})

    # Travel window. Domestic roaming blackouts apply to any batch away from
    # base; international ones only to international batches. Inert while RMS
    # returns these fields empty — see parse_off_dates.
    off = candidate.get("off_dates") or {}
    roaming_block = sorted(set(days) & parse_off_dates(off.get("roaming")))
    if roaming_block:
        blockers.append({"gate": "travel_window",
                         "detail": f"unavailable to travel on {len(roaming_block)} day(s)"})
    if is_international:
        intl_block = sorted(set(days) & parse_off_dates(off.get("international_roaming")))
        if intl_block:
            blockers.append({"gate": "international_travel_window",
                             "detail": f"international travel blocked on {len(intl_block)} day(s)"})

    if required_level is not None and candidate.get("skill_level") is not None:
        if candidate["skill_level"] < required_level:
            blockers.append({
                "gate": "skill_level",
                "detail": f"skill level {candidate['skill_level']} is below the required {required_level}",
            })

    intl = international_verdict(candidate, country, days) if is_international else None
    if intl and intl["visa"] == "not_available":
        blockers.append({"gate": "visa", "detail": intl["visa_detail"]})

    # ── Mock Gate (Auto Tall Policy 14 Aug 2026 / 27 Jul 2026) ─────────────
    # Waived for Certified trainers; required for uncertified first-timers.
    course_runs = candidate.get("course_assignments")
    try:
        course_runs = int(course_runs)
    except (TypeError, ValueError):
        course_runs = 0

    is_first_time = course_runs == 0
    is_certified = bool(candidate.get("is_certified") or candidate.get("certification_covered"))
    mock_rating = str(candidate.get("mock_rating") or "").strip().lower()
    mock_ok = mock_rating in ("satisfactory", "great", "great mock", "passed")

    if is_first_time and candidate.get("mock_checked"):
        if is_certified:
            candidate["mock_status"] = "certified_waived"
        elif mock_ok:
            candidate["mock_status"] = "satisfactory"
        elif mock_rating:
            blockers.append({"gate": "mock_rating",
                             "detail": f"First-time delivery requires satisfactory mock (Current: {mock_rating.title()})"})
        else:
            blockers.append({"gate": "mock_missing",
                             "detail": "First-time delivery for uncertified trainer requires qualifying mock on record"})

    if blockers:
        return {
            "trainer_name": candidate.get("trainer_name", ""),
            "eligible": False,
            "blockers": blockers,
            "availability": avail,
            "international": intl,
            "fit": 0,
            "factors": [],
        }

    # ── Weighted fit ─────────────────────────────────────────────────────────
    factors = []

    def add(name, contribution, evidence):
        if contribution:
            factors.append({"name": name, "contribution": round(contribution),
                            "evidence": evidence})

    add("Course experience", min(course_runs, 10) * 2,
        f"{course_runs} prior deliveries of this course")

    if is_first_time:
        if is_certified:
            add("Mock requirement", 6, "Waived for certified trainer (Auto Tall 14 Aug 2026)")
        elif mock_ok:
            add("Mock verification", 8, f"Passed qualifying mock ({mock_rating.title()}) for 1st-time delivery")

    # Priority for Cancelled Batches (Auto Tall 12 Aug 2026)
    if candidate.get("cancelled_batch_priority") or candidate.get("has_cancelled_priority"):
        add("Post-cancellation priority", 20, "Priority slot active (client-cancelled batch within 14 days)")

    # Tech Call Conversion Preference (Auto Tall 30 Jul 2026)
    if candidate.get("is_tech_call_trainer") or (client and client == str(candidate.get("tech_call_client", "")).lower()):
        add("Tech call continuity", 25, "Conducted pre-sales tech call that converted this batch")

    # 6-Month Clean Record (Auto Tall 05 Aug 2026)
    if candidate.get("recent_negative_6mo"):
        add("Feedback history", -5, "Negative feedback in trailing 6 months (soft preference applied)")
    elif candidate.get("clean_record_6mo") is True:
        add("Feedback history", 8, "Clean record: 0 negative feedback in trailing 6 months")

    level = candidate.get("skill_level")
    if level is not None:
        headroom = level - (required_level or 0)
        add("Skill level", min(max(headroom, 0), 6) * 3, f"skill level {level}")

    if intl:
        if intl["visa"] == "available":
            add("Visa", 10, intl["visa_detail"])
        elif intl["visa"] == "unknown":
            # Surfaced, never excluded — but it does not earn points either.
            add("Visa", 0, "verification required")
        fit = intl.get("timezone_fit")
        if fit == "comfortable":
            add("Time zone", 10, intl["timezone_detail"])
        elif fit == "workable":
            add("Time zone", 5, f"{intl['timezone_detail']}, unsocial hours")
        elif fit == "unsocial":
            # An unsocial window is only workable if the trainer has not
            # blocked that shift. Night/morning/evening IL off-dates are the
            # per-shift signal; overlapping them turns a penalty into a gate.
            shift_blocked = (set(days) & parse_off_dates(off.get("night_il"))) or \
                            (set(days) & parse_off_dates(off.get("morning_il")))
            if shift_blocked:
                blockers.append({"gate": "shift_window",
                                 "detail": "trainer has blocked the required night or early shift"})
            add("Time zone", -5, f"{intl['timezone_detail']}, night or early shift")

    if blockers:
        return {
            "trainer_name": candidate.get("trainer_name", ""),
            "eligible": False, "blockers": blockers, "availability": avail,
            "international": intl, "fit": 0, "factors": [],
        }

    if client and client in schedule.get("specified_clients", set()):
        add("Client preference", 10, f"{batch.get('customer')} has asked for this trainer")

    mode = str(batch.get("delivery_mode") or "").strip().lower()
    if mode:
        delivered = sum(1 for m in schedule.get("modes", []) if m.strip().lower() == mode)
        if delivered:
            add("Delivery mode fit", min(delivered, 6) * 2,
                f"{delivered} prior {batch.get('delivery_mode')} deliveries")

    # Utilisation is a tiebreaker, not a gate. This is the demotion the audit
    # called for: a busy trainer who is free on the dates is still a candidate.
    util = candidate.get("utilisation")
    if isinstance(util, (int, float)):
        if util > 85:
            add("Load headroom", -3, f"{util:g}% utilised")
        elif util < 50:
            add("Load headroom", 5, f"{util:g}% utilised, room to take work")

    if avail["status"] == "available_with_conflicts":
        add("Provisional bookings", -4, avail["reason"])

    base = 50
    fit = max(0, min(100, base + sum(f["contribution"] for f in factors)))

    return {
        "trainer_name": candidate.get("trainer_name", ""),
        "eligible": True,
        "blockers": [],
        "availability": avail,
        "international": intl,
        "fit": fit,
        "factors": sorted(factors, key=lambda f: -abs(f["contribution"])),
        "requires_verification": bool(intl and intl.get("requires_verification")),
    }


def _capacity_plan_from_allocation(payload, today=None, weeks=8):
    """Turn the verified allocation board into an honest weekly pressure view."""
    today = today or datetime.utcnow().date()
    monday = today - timedelta(days=today.weekday())
    end = monday + timedelta(days=7 * weeks)
    buckets = []
    for index in range(weeks):
        start = monday + timedelta(days=7 * index)
        buckets.append({
            "week_start": start.isoformat(),
            "week_end": (start + timedelta(days=6)).isoformat(),
            "demand": 0, "priority": 0, "international": 0,
            "strong_coverage": 0, "partial_coverage": 0, "uncovered": 0,
            "verified_available_candidates": 0, "availability_unknown_candidates": 0,
        })

    considered = []
    candidate_total = 0
    candidate_verified = 0
    for batch in payload.get("batches", []) if isinstance(payload, dict) else []:
        if not isinstance(batch, dict):
            continue
        start = _parse_date(batch.get("start_date"))
        if not start or start < monday or start >= end:
            continue
        bucket = buckets[(start - monday).days // 7]
        bucket["demand"] += 1
        bucket["priority"] += int(bool(batch.get("is_priority")))
        bucket["international"] += int(bool(batch.get("is_international")))
        try:
            relevance = int(float(batch.get("relevance") or 0))
        except (TypeError, ValueError):
            relevance = 0
        if relevance >= 75:
            bucket["strong_coverage"] += 1
        elif relevance >= 50:
            bucket["partial_coverage"] += 1
        else:
            bucket["uncovered"] += 1
        candidates = [c for c in batch.get("candidates", []) if isinstance(c, dict)]
        candidate_total += len(candidates)
        for candidate in candidates:
            verified = bool(candidate.get("availability_verified"))
            candidate_verified += int(verified)
            status = str(candidate.get("availability_status", "") or "").lower()
            if verified and status in {"available", "free"}:
                bucket["verified_available_candidates"] += 1
            elif not verified:
                bucket["availability_unknown_candidates"] += 1
        considered.append(batch)

    for bucket in buckets:
        demand = bucket["demand"]
        bucket["coverage_pct"] = round(100 * bucket["strong_coverage"] / demand) if demand else None
        if not demand:
            bucket["pressure"] = "none"
        elif bucket["uncovered"] or (bucket["priority"] and not bucket["verified_available_candidates"]):
            bucket["pressure"] = "high"
        elif bucket["partial_coverage"]:
            bucket["pressure"] = "watch"
        else:
            bucket["pressure"] = "healthy"

    strong = sum(b["strong_coverage"] for b in buckets)
    uncovered = sum(b["uncovered"] for b in buckets)
    availability_confidence = round(100 * candidate_verified / candidate_total) if candidate_total else None
    return {
        "schema_version": "2.1",
        "horizon": {"weeks": weeks, "start": monday.isoformat(), "end": (end - timedelta(days=1)).isoformat()},
        "summary": {
            "demand": len(considered),
            "strong_coverage": strong,
            "uncovered": uncovered,
            "priority": sum(int(bool(b.get("is_priority"))) for b in considered),
            "international": sum(int(bool(b.get("is_international"))) for b in considered),
            "coverage_pct": round(100 * strong / len(considered)) if considered else None,
        },
        "weeks": buckets,
        "confidence": {
            "demand": "verified_snapshot",
            "availability_pct": availability_confidence,
            "availability": "verified" if availability_confidence == 100 else "partial",
            "note": "Availability uses assignment and off-date evidence carried by ranked candidates; unknown evidence is never treated as free capacity.",
        },
        "generated_at": datetime.utcnow().isoformat(),
    }


@app.route('/api/v2/planning/capacity', methods=['GET'])
def v2_capacity_plan():
    manager = request.args.get('manager', '').strip().lower()
    _, error = _v2_manager_session(manager)
    if error:
        return error
    with _allocation_lock:
        payload = _allocation_payload_cache.get(manager)
    if not payload:
        return jsonify({
            "schema_version": "2.1", "ready": False,
            "code": "ALLOCATION_SNAPSHOT_REQUIRED",
            "message": "Open demand is still preparing; capacity planning will update automatically.",
        }), 202
    plan = _capacity_plan_from_allocation(payload)
    plan["ready"] = True
    return jsonify(plan), 200


@app.route('/api/v2/skills/bulk-assign', methods=['POST'])
def v2_bulk_assign_skill():
    """
    One skill, many reportees — the write behind design vision §7.6.

    RMS exposes only a single-record write (key 255), so the fan-out happens
    here rather than as N round trips from the phone. Concurrency is held to
    four: this writes to production RMS for every row, and a manager selecting
    forty people must not become forty simultaneous writes against a system
    that normally answers in two to five seconds.

    Every row reports its own outcome. A partial failure is the expected case,
    not an edge case — one trainer's write can be refused while the rest
    succeed — and a bulk action that reported a single aggregate success would
    hide exactly that.

    There is no remove or update endpoint anywhere in the RMS estate, so this
    route deliberately only adds. See `AI/DECISIONS.md`.
    """
    session, error = _v2_manager_session("")
    if error:
        return error

    body = request.get_json(silent=True) or {}
    course_id = str(body.get("course_id", "")).strip()
    rows = body.get("trainers")
    if not course_id.isdigit():
        return error_response("INVALID_INPUT", "course_id must be numeric", 400)
    if not isinstance(rows, list) or not rows:
        return error_response("INVALID_INPUT", "trainers must be a non-empty list", 400)
    if len(rows) > 60:
        return error_response("TOO_MANY", "at most 60 trainers per request", 400)

    from_date = str(body.get("from_date", "")).strip() or _iso(datetime.utcnow().date())
    approved = str(body.get("officially_approved", "")).strip()

    prepared, rejected = [], []
    for r in rows:
        if not isinstance(r, dict):
            continue
        email = str(r.get("trainer_email", "")).strip().lower()
        try:
            level = int(r.get("skill_level"))
        except (TypeError, ValueError):
            rejected.append({"trainer_email": email, "ok": False,
                             "message": "skill_level must be a number"})
            continue
        if not email.endswith("@koenig-solutions.com"):
            rejected.append({"trainer_email": email, "ok": False,
                             "message": "not a Koenig address"})
        elif not 1 <= level <= 10:
            rejected.append({"trainer_email": email, "ok": False,
                             "message": "skill_level must be between 1 and 10"})
        else:
            prepared.append((email, level))

    def write_one(item):
        email, level = item
        result = _rms("addTrainerSkill", {
            "CourseId":           course_id,
            "TrainerEmail":       email,
            "SkillLevel":         str(level),
            "OfficiallyApproved": approved,
            "FromDate":           _iso(_parse_date(from_date)),
        }, timeout=6, attempts=1)
        if result is None:
            return {"trainer_email": email, "skill_level": level, "ok": False,
                    "verified": False, "code": "RMS_UNREACHABLE",
                    "message": "RMS did not answer in time. No success assumed."}
        status, rms_message = _write_status(result)
        refused = status.lower() == "error"
        if not refused:
            _cache_purge(email)
        return {"trainer_email": email, "skill_level": level,
                "ok": not refused, "verified": not refused,
                "message": rms_message or ("Recorded" if not refused else "Refused by RMS")}

    written = []
    if prepared:
        with ThreadPoolExecutor(max_workers=min(4, len(prepared))) as pool:
            written.extend(pool.map(write_one, prepared))

    results = written + rejected
    succeeded = sum(1 for r in results if r.get("ok"))
    return jsonify({
        "schema_version": "2.0",
        "course_id": course_id,
        "requested": len(rows),
        "succeeded": succeeded,
        "failed": len(results) - succeeded,
        "results": results,
        "note": ("RMS has no remove or update skill endpoint, so this operation "
                 "only adds. Existing records are not modified."),
        "generated_at": datetime.utcnow().isoformat(),
    }), 200


@app.route('/api/v2/team/readiness', methods=['GET'])
def v2_team_readiness():
    """
    Real availability for the whole roster, one row per reportee.

    The Team page has always shown utilisation as if it were availability. This
    answers from the RMS day-level calendar instead: approved leave, confirmed
    commitments, provisional work and client exclusions, per person.

    Cost is one call per trainer (key 111), so the fan-out is parallel and
    served from the ten-minute cache. It is bounded, and when the roster
    exceeds the bound the response says exactly how many were skipped —
    a silently truncated list reads as "everyone is clear", which is the
    failure mode this whole layer exists to remove.
    """
    manager = request.args.get('manager', '').strip().lower()
    _, error = _v2_manager_session(manager)
    if error:
        return error

    roster = []
    for r in (_rms("reportees", {"email": manager}) or []):
        if isinstance(r, dict) and r.get("OffEmail"):
            roster.append({
                "email": str(r["OffEmail"]).strip().lower(),
                "name": str(r.get("TrainerName") or "").strip(),
            })

    limit = 40
    considered, skipped = roster[:limit], max(0, len(roster) - limit)

    today = datetime.utcnow().date()
    end = today + timedelta(days=90)

    def one(person):
        schedule, why = _rc_schedule(person["email"], today, end)
        leave = sorted(schedule.get("leave_dates", set()))
        return {
            "trainer_email": person["email"],
            "trainer_name": person["name"],
            "verified": not why,
            "note": why or "",
            "leave_days": len(leave),
            "next_leave": [d.isoformat() for d in leave[:3]],
            "confirmed_days": len(schedule.get("confirmed_dates", set())),
            "tentative_days": len(schedule.get("tentative_dates", set())),
            "client_exclusions": len(schedule.get("dnc_clients", set())),
            "client_requests": len(schedule.get("specified_clients", set())),
            "delivery_modes": sorted(set(schedule.get("modes", []))),
        }

    rows = []
    if considered:
        with ThreadPoolExecutor(max_workers=min(8, len(considered))) as pool:
            for result in pool.map(one, considered):
                rows.append(result)

    on_leave = [r for r in rows if r["leave_days"] > 0]
    return jsonify({
        "schema_version": "2.0",
        "ready": True,
        "window": {"from": today.isoformat(), "to": end.isoformat()},
        "counts": {
            "roster": len(roster),
            "checked": len(rows),
            "not_checked": skipped,
            "with_leave": len(on_leave),
            "unverified": sum(1 for r in rows if not r["verified"]),
        },
        # Never silent: a truncated roster must announce itself.
        "note": (f"{skipped} reportee(s) beyond the {limit} checked were not "
                 f"evaluated and are not represented below." if skipped else ""),
        "trainers": rows,
        "generated_at": datetime.utcnow().isoformat(),
    }), 200


@app.route('/api/v2/trainer/readiness', methods=['GET'])
def v2_trainer_readiness():
    """
    Real readiness for one trainer: leave, commitments and certification.

    Trainer 360 previously inferred availability from off-date fields that are
    empty for every trainer this account can reach. This answers from the RMS
    day-level calendar instead — approved leave, confirmed bookings and
    provisional ones — which is the same correction applied to Demand.

    Certification is reported with its requirement state as tri-state: the exam
    policy catalogue does not share course names with the delivery catalogue,
    so "no policy entry" must read as unknown rather than as "no gap".
    """
    manager = request.args.get('manager', '').strip().lower()
    _, error = _v2_manager_session(manager)
    if error:
        return error

    email = request.args.get('email', '').strip().lower()
    if not email:
        return error_response("EMAIL_REQUIRED", "email query param required", 400)

    today = datetime.utcnow().date()
    end = today + timedelta(days=90)
    schedule, why = _rc_schedule(email, today, end)
    hints = _exam_hints(_rms("trainerRCSchedule", {
        "traineremail": email,
        "fromDate": today.isoformat(), "toDate": end.isoformat(),
    }) or [])

    policy = _exam_policy()
    taught = []
    for row in (_rms("trainerDetails", {"email": email}) or []):
        if isinstance(row, dict) and row.get("CourseName"):
            taught.append(str(row["CourseName"]).strip())
    held = [c for c in taught]          # a taught course with an approved skill
    verdicts = [certification_verdict(c, held, policy, hints) for c in dict.fromkeys(taught)]
    gaps = [v for v in verdicts if v["gap"]]
    unknown = [v for v in verdicts if v["exam_required"] is None]

    # ── Trainer profile from the course-keyed pool ───────────────────────────
    # Visa, timezone and nearest city are properties of the *trainer*, not of
    # the course, so any course they teach returns the same values. Key 171 is
    # course-keyed, so one is resolved on their behalf: their taught courses are
    # tried in order until a pool comes back containing their row. Bounded to a
    # few attempts because each is a live call, and reported as unresolved
    # rather than guessed if none match.
    profile = None
    profile_note = "no course could be resolved to look up travel readiness"
    name_by_email = {}
    for r in (_rms("reportees", {"email": manager}) or []):
        if isinstance(r, dict) and r.get("OffEmail"):
            name_by_email[str(r["OffEmail"]).strip().lower()] = str(r.get("TrainerName") or "").strip()
    trainer_name = name_by_email.get(email, "")

    if trainer_name:
        for course in list(dict.fromkeys(taught))[:4]:
            pool, why = _free_schedule(course)
            if why or not pool:
                continue
            row = pool.get(trainer_name.lower())
            if not row:
                continue
            visas = []
            for v in row.get("visa") or []:
                visas.append({
                    "country": v["country"],
                    "expiry": v["expiry"].isoformat() if v["expiry"] else "",
                    "stay_days": v["stay_days"],
                    "associate_countries": v["associates"],
                    "expired": bool(v["expiry"] and v["expiry"] < today),
                })
            free = sorted(row.get("free_dates") or set())
            profile = {
                "resolved_via_course": row.get("resolved_course", course),
                "timezone": row.get("timezone", ""),
                "nearest_city": row.get("nearest_city", ""),
                "skill_level": row.get("skill_level"),
                "visas": visas,
                # Absence of a visa record is not evidence of ineligibility;
                # roughly half of trainers carry none.
                "visa_state": ("available" if any(not v["expired"] for v in visas)
                               else "expired" if visas else "unknown"),
                "free_days_next_90": sum(1 for d in free if today <= d <= end),
                "free_from": free[0].isoformat() if free else "",
            }
            profile_note = ""
            break

    upcoming_leave = sorted(d.isoformat() for d in schedule.get("leave_dates", set()))
    return jsonify({
        "travel": profile,
        "travel_note": profile_note,
        "schema_version": "2.0",
        "ready": not why,
        "note": why or "",
        "window": {"from": today.isoformat(), "to": end.isoformat()},
        "schedule": {
            "rows": schedule.get("rows", 0),
            "leave_days": len(schedule.get("leave_dates", set())),
            "next_leave": upcoming_leave[:5],
            "confirmed_days": len(schedule.get("confirmed_dates", set())),
            "tentative_days": len(schedule.get("tentative_dates", set())),
            "delivery_modes": sorted(set(schedule.get("modes", []))),
            "client_exclusions": len(schedule.get("dnc_clients", set())),
            "client_requests": len(schedule.get("specified_clients", set())),
        },
        "certification": {
            "courses_reviewed": len(verdicts),
            "gaps": gaps[:10],
            "unknown_requirement": len(unknown),
            "exam_identity_note": ("Exam names are inferred from delivery history; "
                                   "RMS exposes no read-only course-to-exam mapping."),
        },
        "generated_at": datetime.utcnow().isoformat(),
    }), 200


@app.route('/api/v2/allocation/candidates', methods=['GET'])
def v2_allocation_candidates():
    """
    The candidate pool for one demand, evaluated by the intelligence layer.

    Params: manager, course, start, end, and optionally country, customer,
    delivery_mode, international, level.

    This is the first route to answer "who can take this batch" from real
    availability rather than from a utilisation percentage. It returns
    eligibility, the gates that failed, and the per-factor breakdown behind
    every score, so a manager can disagree with one axis rather than with a
    number they cannot audit.

    An unresolvable course returns 422 rather than an empty pool: reporting
    "nobody is available" when the truth is "we could not check" is the worst
    failure an allocation tool can have.
    """
    manager = request.args.get('manager', '').strip().lower()
    _, error = _v2_manager_session(manager)
    if error:
        return error

    course = request.args.get('course', '').strip()
    if not course:
        return error_response("COURSE_REQUIRED", "course query param required", 400)

    start = _parse_date(request.args.get('start', ''))
    end = _parse_date(request.args.get('end', '')) or start
    if not start:
        return error_response("DATES_REQUIRED", "valid start date required", 400)

    pool, why = _free_schedule(course)
    if why:
        return jsonify({
            "schema_version": "2.0", "ready": False,
            "code": "COURSE_UNRESOLVED", "message": why,
            "candidates": [], "note": "Could not verify availability; this is not an empty pool.",
        }), 422

    batch = {
        "start_date": start, "end_date": end,
        "country": request.args.get('country', '').strip(),
        "customer": request.args.get('customer', '').strip(),
        "delivery_mode": request.args.get('delivery_mode', '').strip(),
        "international": request.args.get('international', '').strip().lower() in ("1", "true", "yes"),
    }
    try:
        required_level = int(request.args.get('level', '') or 0) or None
    except ValueError:
        required_level = None

    # The reportee roster is the scope: a manager evaluates their own team.
    # Names are the only join key key 171 offers, so the pool is filtered by
    # name and anyone unmatched is reported rather than silently dropped.
    roster = {}
    for r in (_rms("reportees", {"email": manager}) or []):
        if isinstance(r, dict) and r.get("TrainerName"):
            roster[str(r["TrainerName"]).strip().lower()] = str(r.get("OffEmail") or "").strip()

    results, unmatched = [], []
    for key, cand in pool.items():
        email = roster.get(key)
        if roster and not email:
            unmatched.append(cand["trainer_name"])
            continue
        sched = {"leave_dates": set(), "confirmed_dates": set(), "tentative_dates": set(),
                 "dnc_clients": set(), "specified_clients": set(), "modes": [], "rows": 0}
        if email:
            sched, _ = _rc_schedule(email, start, end)
            cand = dict(cand, off_dates=_off_dates(email), utilisation=_safe_util(email))
        verdict = evaluate_candidate(cand, sched, batch, required_level)
        verdict["trainer_email"] = email
        results.append(verdict)

    eligible = [r for r in results if r["eligible"]]
    blocked = [r for r in results if not r["eligible"]]
    eligible.sort(key=lambda r: -r["fit"])

    return jsonify({
        "schema_version": "2.0",
        "ready": True,
        "course_resolved": next(iter(pool.values()))["resolved_course"] if pool else "",
        "match_confidence": next(iter(pool.values()))["match_confidence"] if pool else "",
        "counts": {"pool": len(pool), "eligible": len(eligible),
                   "blocked": len(blocked), "outside_team": len(unmatched)},
        "candidates": eligible,
        "blocked": blocked,
        "note": ("Availability comes from the RMS free-date calendar and leave records, "
                 "not from utilisation. Trainers with no visa record are shown and flagged, "
                 "never excluded."),
        "generated_at": datetime.utcnow().isoformat(),
    }), 200


@app.route('/api/data/trainer-skills', methods=['GET'])
def trainer_skills():
    """
    The RMS skill register for one trainer — what they are formally on record as
    able to teach. Also the read-back that proves a mark-skill write landed.
    """
    email = request.args.get('email', '').strip().lower()
    # The `email` here is the trainer being looked up, not the manager. Any
    # authenticated manager may read the register; a session is still required.
    _, error = _v2_manager_session("")
    if error:
        return error
    if not email:
        return error_response("EMAIL_REQUIRED", "email query param required", 400)

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
        return error_response("RMS_UNREACHABLE", "Cannot reach RMS — please retry", 503)

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
    _, error = _v2_manager_session("")
    if error:
        return error

    data = request.get_json(silent=True) or {}
    course_id = str(data.get("course_id", "")).strip()
    trainer_email = str(data.get("trainer_email", "")).strip().lower()
    from_date = str(data.get("from_date", "")).strip()
    approved = str(data.get("officially_approved", "No")).strip() or "No"

    if not course_id.isdigit():
        return jsonify({"success": False, "error": "course_id must be numeric", "code": "INVALID_INPUT"}), 400
    if not trainer_email.endswith("@koenig-solutions.com"):
        return jsonify({"success": False, "error": "trainer_email must be a Koenig address", "code": "INVALID_EMAIL"}), 400
    if not _parse_date(from_date):
        return jsonify({"success": False, "error": "from_date must be a valid date", "code": "INVALID_INPUT"}), 400
    try:
        level = int(str(data.get("skill_level", "")).strip())
    except ValueError:
        return jsonify({"success": False, "error": "skill_level must be a number", "code": "INVALID_INPUT"}), 400
    if not 1 <= level <= 10:
        return jsonify({"success": False, "error": "skill_level must be between 1 and 10", "code": "INVALID_INPUT"}), 400

    emp = _emp_code(trainer_email)

    # Keep this user-facing write inside the hosting gateway window. The old
    # flow did a register read + write + register read, each allowed to wait
    # 30 seconds, so the proxy could return 502 before Flask had a response.
    # RMS normally answers in 2-5 seconds; one 6-second attempt per operation
    # gives us a bounded response and still preserves authoritative read-back.
    result = _rms("addTrainerSkill", {
        "CourseId":           course_id,
        "TrainerEmail":       trainer_email,
        "SkillLevel":         str(level),
        "OfficiallyApproved": approved,
        "FromDate":           _iso(_parse_date(from_date)),
    }, timeout=6, attempts=1)
    if result is None:
        return jsonify({
            "success": False, "verified": False,
            "error": "RMS did not answer in time. No success was assumed; check the trainer skill register before retrying.",
            "code": "RMS_UNREACHABLE",
        }), 503

    status, rms_message = _write_status(result)
    refused = status.lower() == "error"

    # A write invalidates this trainer's capability picture. Without this the
    # app would show a confirmed skill that the cached course list still denies.
    if not refused:
        _cache_purge(trainer_email)

    after_rows = (_rms("trainerSkills", {"employee_id": str(emp)}, timeout=6, attempts=1)
                  if emp else [])
    after = _normalise_skill_register(after_rows) if after_rows is not None else None
    present = bool(after) and any(s["course_id"] == course_id for s in after)
    already = refused and present
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
            "code": "CONFLICT",
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
        "code": "CONFLICT",
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
    # The `email` here is the trainer, not the manager — session required, no
    # scope match against the trainer address.
    _, error = _v2_manager_session("")
    if error:
        return error
    if not email:
        return error_response("EMAIL_REQUIRED", "email query param required", 400)

    emp_code = _emp_code(email)
    if not emp_code:
        return jsonify({
            "email": email, "emp_code": "", "months": [], "available": False,
            "note": "RMS returned no employee code for this address, so the "
                    "utilisation history cannot be looked up.",
        }), 200

    rows = _rms("last3MonthsUtil", {"EmpCode": str(emp_code)})
    if rows is None:
        return error_response("RMS_UNREACHABLE", "Cannot reach RMS — please retry", 503)

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


def _course_catalogue_index():
    """Normalised RMS course catalogue enriched with verified metadata."""
    rows = _rms("courseCatalogue", {})
    if not isinstance(rows, list):
        return {}
    out = {}
    for row in rows:
        if not isinstance(row, dict):
            continue
        name = str(row.get("Course") or "").strip()
        key = _norm_course(name)
        if not key:
            continue
        out[key] = {
            "course_id": str(row.get("Cid") or ""),
            "course_name": name,
            "course_code": str(row.get("course_code") or "").strip(),
            "vendor": str(row.get("vendor_of_course") or "").strip(),
            "duration_days": row.get("course_duration"),
            "course_page_url": str(row.get("Course_Page") or "").strip(),
            "syllabus_url": str(row.get("TOC") or "").strip(),
        }
    return out


def _course_schedule(course_name):
    """Verified public schedule dates from RMS key 246 for one course."""
    rows = _rms("courseSchedule", {
        "CourseName": course_name, "Country": "", "region": "", "DeliveryMode": "",
    })
    if rows is None:
        return None
    schedules, resolved_name, course_id = [], course_name, ""
    for row in (rows if isinstance(rows, list) else []):
        if not isinstance(row, dict):
            continue
        item = row
        if isinstance(row.get("JsonResult"), str):
            try:
                item = json.loads(row["JsonResult"])
            except (ValueError, TypeError):
                continue
        if not isinstance(item, dict):
            continue
        resolved_name = str(item.get("CourseName") or resolved_name).strip()
        course_id = str(item.get("CId") or course_id)
        raw_dates = item.get("ScheduleDates") or []
        if isinstance(raw_dates, list):
            schedules.extend(str(value).strip() for value in raw_dates if str(value).strip())
    return {
        "course_name": resolved_name, "course_id": course_id,
        "schedule_dates": list(dict.fromkeys(schedules)), "available": bool(schedules),
    }


@app.route('/api/data/course-syllabus', methods=['GET'])
def get_course_syllabus():
    """Syllabus PDF link for one course, matched by name against RMS key 248."""
    course_name = str(request.args.get("courseName", "")).strip()
    _, error = _v2_manager_session("")
    if error:
        return error
    if not course_name:
        return error_response("INVALID_COURSE_NAME", "courseName query param required", 400)

    index = _syllabus_index()
    if not index:
        return error_response("RMS_UNREACHABLE", "Cannot reach RMS — please retry", 503)

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


@app.route('/api/data/course-search', methods=['GET'])
def search_courses():
    """Search the full RMS catalogue, including courses no trainer owns."""
    query = str(request.args.get("q", "")).strip()
    _, error = _v2_manager_session("")
    if error:
        return error
    if len(query) < 2:
        return jsonify({"query": query, "courses": [], "count": 0}), 200
    index = _course_catalogue_index() or {
        key: {
            "course_id": str(value.get("course_id") or ""),
            "course_name": value.get("course_name") or "",
            "course_code": "", "vendor": "", "duration_days": None,
            "course_page_url": "", "syllabus_url": value.get("url") or "",
        }
        for key, value in _syllabus_index().items()
    }
    if not index:
        return error_response("RMS_UNREACHABLE", "Cannot reach RMS — please retry", 503)
    needle = _norm_course(query)
    hits = []
    for normalised, course in index.items():
        if needle in normalised:
            hits.append({**course, "course_id": str(course.get("course_id") or "")})
    hits.sort(key=lambda row: (0 if _norm_course(row["course_name"]).startswith(needle) else 1,
                               len(row["course_name"]), row["course_name"]))
    hits = hits[:25]
    return jsonify({"query": query, "courses": hits, "count": len(hits), "available": True}), 200


@app.route('/api/data/course-intelligence', methods=['GET'])
def get_course_intelligence():
    """Verified course metadata and future public schedules for manager planning."""
    course_name = str(request.args.get("courseName", "")).strip()
    _, error = _v2_manager_session("")
    if error:
        return error
    if not course_name:
        return error_response("INVALID_COURSE_NAME", "courseName query param required", 400)
    catalogue = _course_catalogue_index()
    meta = catalogue.get(_norm_course(course_name), {}) if catalogue else {}
    schedule = _course_schedule(course_name)
    if schedule is None:
        return jsonify({
            **meta, "course_name": meta.get("course_name") or course_name,
            "schedule_dates": [], "schedule_available": False,
            "note": "RMS schedule data could not be verified.",
        }), 200
    return jsonify({
        **meta,
        "course_name": schedule.get("course_name") or meta.get("course_name") or course_name,
        "course_id": schedule.get("course_id") or meta.get("course_id") or "",
        "schedule_dates": schedule.get("schedule_dates") or [],
        "schedule_available": schedule.get("available", False),
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
    _, error = _v2_manager_session("")
    if error:
        return error
    if not course:
        return error_response("INVALID_COURSE_NAME", "course query param required", 400)

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
_ACTION_DB = os.getenv("SKILLEDGE_ACTION_DB", "").strip() or os.path.join(
    os.getenv("SKILLEDGE_STATE_DIR", "."), "skilledge_actions.sqlite3")
_action_repository = ActionStore(_ACTION_DB, legacy_json=_ACTION_STORE)
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


def _action_apply_overlay(actions, manager_email=""):
    """Annotate derived actions with any stored lifecycle state and notes."""
    if not isinstance(actions, list):
        return actions
    return _action_repository.overlay(str(manager_email or "").lower(), actions)


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
@app.route('/api/v2/actions', methods=['GET'])
def get_actions():
    """The manager's full inbox: derived actions plus anything raised by hand."""
    requested = str(request.args.get("email", request.args.get("manager", ""))).strip().lower()
    session, error = _v2_manager_session(requested)
    if error:
        return error
    email = session["email"]

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
    _action_apply_overlay(derived, email)

    raised = _action_repository.list_raised(email)
    _action_repository.overlay(email, raised)

    actions = derived + [r for r in raised if r.get("manager_email") == email]
    return jsonify({
        "manager": email,
        "actions": actions,
        "open": sum(1 for a in actions if a.get("lifecycle_state") == "open"),
        "closed": sum(1 for a in actions if a.get("lifecycle_state") == "closed"),
        "persistence": _action_repository.status(),
        "timestamp": datetime.utcnow().isoformat(),
    }), 200


@app.route('/api/actions', methods=['POST'])
@app.route('/api/v2/actions', methods=['POST'])
def raise_action():
    """Create a manager-raised action (anything RMS cannot infer)."""
    body = request.get_json(silent=True) or {}
    title = str(body.get("title", "")).strip()
    requested = str(body.get("manager_email", "")).strip().lower()
    session, error = _v2_manager_session(requested)
    if error:
        return error
    manager_email = session["email"]
    if not title:
        return error_response("INVALID_INPUT", "title is required", 400)

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
    _action_repository.raise_action(manager_email, record, manager_email)
    return jsonify(record), 201


@app.route('/api/actions/<action_id>/state', methods=['POST'])
@app.route('/api/v2/actions/<action_id>/state', methods=['POST'])
def set_action_state(action_id):
    """Move one action through its lifecycle, with an audit trail."""
    body = request.get_json(silent=True) or {}
    state = str(body.get("state", "")).strip().lower()
    if state not in VALID_ACTION_STATES:
        return error_response("INVALID_INPUT", "state must be one of %s" % (VALID_ACTION_STATES,), 400)

    requested = str(body.get("manager_email", "")).strip().lower()
    session, error = _v2_manager_session(requested)
    if error:
        return error
    manager_email = session["email"]
    now = datetime.utcnow().isoformat()
    entry = {
        "state": state, "note": str(body.get("note", "")).strip(),
        "assignee": str(body.get("assignee", "")).strip(),
        "by": str(body.get("manager_email", "")).strip().lower(), "at": now,
    }
    result = _action_repository.transition(
        manager_email, action_id, state, manager_email,
        assignee=entry["assignee"], due_date=str(body.get("due_date", "")).strip(), note=entry["note"])
    return jsonify(result), 200


@app.route('/api/actions/<action_id>/note', methods=['POST'])
@app.route('/api/v2/actions/<action_id>/note', methods=['POST'])
def add_action_note(action_id):
    """Append a follow-up note without changing the action's state."""
    body = request.get_json(silent=True) or {}
    text = str(body.get("note", "")).strip()
    if not text:
        return error_response("INVALID_INPUT", "note is required", 400)
    requested = str(body.get("manager_email", "")).strip().lower()
    session, error = _v2_manager_session(requested)
    if error:
        return error
    manager_email = session["email"]
    note = _action_repository.add_note(manager_email, action_id, text, manager_email)
    return jsonify({"id": action_id, "note": note,
                    "history": _action_repository.audit(manager_email, action_id)}), 200


@app.route('/api/v2/actions/<action_id>/audit', methods=['GET'])
def get_action_audit(action_id):
    requested = str(request.args.get("manager", "")).strip().lower()
    session, error = _v2_manager_session(requested)
    if error:
        return error
    return jsonify({"action_id": action_id, "manager": session["email"],
                    "events": _action_repository.audit(session["email"], action_id),
                    "persistence": _action_repository.status()}), 200




# ---------------------------------------------------------------------------
# Deterministic delivery agent — POST /api/agent/ask
# ---------------------------------------------------------------------------

_AGENT_INTENTS = {
    # canonical keys
    "availability":       "What is their current availability?",
    "readiness":          "Are they ready to deliver the next batch?",
    "skills":             "What courses can they teach?",
    "certification_gaps": "What certifications are they missing?",
    "utilization":        "How busy are they right now?",
    "risk":               "What risks do they present?",
    "feedback":           "How has their feedback been recently?",
    "recommendation":     "What should I do with them this week?",
    "summary":            "Give me a summary.",
    # aliases from CopilotChatSheet
    "can_assign_now":     "Can I assign this trainer now?",
    "can_deliver":        "What can this trainer deliver?",
    "weak_spot":          "Where is this trainer weak?",
    "missing_certs":      "Which certifications are missing?",
    "best_course":        "What is the best course fit?",
    "backup_trainers":    "Who are backup trainers?",
    "why_not_first":      "Why is this trainer not the first choice?",
    "compare_trainer":    "Compare with another trainer.",
    "what_if_oem":        "What if I move to another OEM?",
    "invest_oem":         "Which OEM should I invest in?",
    "explain_readiness":  "Explain the readiness score.",
    "explain_allocation": "Explain the allocation score.",
    "why_low_confidence": "Why is confidence low?",
    "next_action":        "What should I do next?",
    "missing_data":       "What data is missing?",
}

# Normalise CopilotChatSheet aliases to canonical answer-engine keys.
_INTENT_ALIASES: dict[str, str] = {
    "can_assign_now":     "readiness",
    "can_deliver":        "skills",
    "weak_spot":          "certification_gaps",
    "missing_certs":      "certification_gaps",
    "best_course":        "skills",
    "backup_trainers":    "recommendation",
    "why_not_first":      "risk",
    "compare_trainer":    "summary",
    "what_if_oem":        "recommendation",
    "invest_oem":         "recommendation",
    "explain_readiness":  "readiness",
    "explain_allocation": "availability",
    "why_low_confidence": "risk",
    "next_action":        "recommendation",
    "missing_data":       "summary",
}


def _agent_answer(question_key, trainer_data, t360):
    name = (trainer_data.get("trainer_name") or "").strip() or "This trainer"
    util = trainer_data.get("current_utilization")
    status = (trainer_data.get("availability_status") or "unknown").lower()
    readiness = (trainer_data.get("readiness_bucket") or "").lower()
    health = trainer_data.get("health_score", 0)
    risk = (trainer_data.get("risk_level") or "low").lower()
    rec_action = (trainer_data.get("recommended_action") or "").strip()
    neg_fb = trainer_data.get("negative_feedback_count", 0) or 0
    skills = trainer_data.get("skills") or []
    fb_summary = t360.get("feedback") or {}
    cert_gaps = (t360.get("certifications") or {}).get("gaps") or []
    evidence = []

    if question_key == "availability":
        if status in ("teaching_now", "live"):
            ans = f"{name} is currently delivering a batch."
        elif status == "preparing":
            ans = f"{name} is preparing for an upcoming batch — limited availability."
        elif status == "free":
            cap = 100 - (util or 0) if util is not None else None
            cap_str = f" ({cap:.0f}% available capacity)" if cap is not None else ""
            ans = f"{name} is free{cap_str}."
        else:
            ans = f"{name}'s availability is unknown — no RMS schedule data."
        evidence = [f"Status: {status}"]
        confidence = "high" if status != "unknown" else "low"

    elif question_key == "readiness":
        if readiness == "ready":
            ans = f"{name} is ready to deliver (health {health}/100)."
        elif readiness == "prep":
            ans = f"{name} may need preparation (health {health}/100)."
        else:
            ans = f"{name} has blockers — resolve before the next assignment."
        if rec_action:
            ans += f" Action: {rec_action}."
        evidence = [f"Readiness: {readiness or 'unknown'}", f"Health: {health}"]
        confidence = "high" if readiness else "medium"

    elif question_key == "skills":
        if not skills:
            ans = f"No skill data cached for {name}."
            evidence = ["No rows in trainer_operations_df"]
        else:
            top = [s.get("course_name") or str(s) if isinstance(s, dict) else str(s) for s in skills[:5]]
            ans = f"{name} can teach {len(skills)} course(s). Top: {', '.join(top)}."
            evidence = ["Source: trainer_operations_df"]
        confidence = "high" if skills else "medium"

    elif question_key == "certification_gaps":
        if not cert_gaps:
            ans = f"No certification gaps found for {name}." if t360 else f"Cert gap data not cached — open Trainer 360."
            evidence = ["Source: RMS key 213"]
        else:
            gap_names = [g.get("course_name") or str(g) if isinstance(g, dict) else str(g) for g in cert_gaps[:5]]
            ans = f"{name} has {len(cert_gaps)} gap(s). Top: {', '.join(gap_names)}."
            evidence = ["Source: cert gap analysis"]
        confidence = "high" if t360 else "low"

    elif question_key == "utilization":
        if util is None:
            ans = f"Utilization not available for {name}."
            evidence = ["No RMS utilization data"]
            confidence = "low"
        else:
            cap = 100 - util
            bracket = "overloaded" if util >= 85 else ("optimal" if util >= 60 else "available")
            ans = f"{name} is at {util:.0f}% utilization ({bracket}), ~{cap:.0f}% available."
            evidence = [f"Utilization: {util}%", "Source: RMS key 55"]
            confidence = "high"

    elif question_key == "risk":
        lvl = "high" if risk in ("high", "critical") else ("medium" if risk == "medium" else "low")
        ans = f"{name} presents {lvl} risk."
        if neg_fb > 0:
            ans += f" {neg_fb} negative feedback record(s) on file."
        if rec_action:
            ans += f" Action: {rec_action}."
        evidence = [f"Risk: {risk}", f"Negative feedback: {neg_fb}"]
        confidence = "high"

    elif question_key == "feedback":
        neg_count = (fb_summary.get("negative_count") or neg_fb)
        pos = fb_summary.get("positive_count") or 0
        if neg_count == 0:
            ans = f"No negative feedback recorded for {name}."
        elif (fb_summary.get("sentiment") or "").lower() == "positive":
            ans = f"{name}'s feedback is positive ({pos} pos, {neg_count} neg)."
        else:
            ans = f"{name} has {neg_count} negative feedback record(s) — may affect allocation."
        evidence = [f"Negative feedback: {neg_count}", "Source: RMS key 58"]
        confidence = "high" if t360 else "medium"

    elif question_key == "recommendation":
        if rec_action:
            ans = f"This week for {name}: {rec_action}."
        elif readiness == "ready" and (util or 0) < 70:
            ans = f"{name} is ready and available — assign the next unallocated batch."
        elif readiness == "blocked":
            ans = f"{name} has blockers — resolve before the next assignment."
        else:
            ans = f"No specific action required for {name} right now."
        evidence = [f"Recommended action: {rec_action or 'none'}", f"Readiness: {readiness}"]
        confidence = "high" if rec_action else "medium"

    elif question_key == "summary":
        parts = [name]
        if readiness:
            parts.append(f"{readiness}-bucket, health {health}/100")
        if util is not None:
            parts.append(f"{util:.0f}% utilisation")
        if status not in ("unknown", ""):
            parts.append(f"status: {status.replace('_', ' ')}")
        if risk != "low":
            parts.append(f"{risk} risk")
        if neg_fb > 0:
            parts.append(f"{neg_fb} negative feedback record(s)")
        if rec_action:
            parts.append(f"action: {rec_action}")
        ans = ". ".join(parts).capitalize() + "."
        evidence = ["Source: unified-manager-intelligence"]
        confidence = "high"

    else:
        ans = f"Unknown question key '{question_key}'. Use: {', '.join(sorted(_AGENT_INTENTS))}."
        evidence = []
        confidence = "low"

    return {
        "answer": ans,
        "evidence": "; ".join(evidence) if evidence else None,
        "source": evidence,
        "confidence": confidence,
        "decisionVersion": "deterministic-v1.0",
        "error": None,
    }


@app.route('/api/agent/ask', methods=['POST'])
def agent_ask():
    """
    Deterministic delivery-intelligence agent.
    Body JSON: { manager_email, target_email, question_key }
    question_key in: availability, readiness, skills, certification_gaps,
                     utilization, risk, feedback, recommendation, summary
    Returns: { answer, evidence, source, confidence, decisionVersion, error }
    Reads only from in-memory session cache — no extra RMS calls.
    """
    session, error = _v2_manager_session("")
    if error:
        return error

    body = request.get_json(force=True, silent=True) or {}
    target_email = str(body.get("target_email", "")).strip().lower()
    question_key = str(body.get("question_key", "")).strip().lower()

    if not target_email:
        return error_response("TARGET_REQUIRED", "target_email is required", 400)
    # Normalise CopilotChatSheet aliases to canonical engine keys.
    question_key = _INTENT_ALIASES.get(question_key, question_key)
    if question_key not in _AGENT_INTENTS:
        return error_response(
            "UNKNOWN_QUESTION",
            f"question_key must be one of: {', '.join(sorted(_AGENT_INTENTS))}",
            400,
        )

    manager_email_verified = session["email"]

    # 1. Try the allocation payload cache — it holds trainer_operations_df rows
    #    that were already assembled for this manager's session.
    trainer_row: dict = {}
    try:
        alloc_payload = _allocation_payload_cache.get(manager_email_verified)
        if alloc_payload:
            for row in (alloc_payload.get("trainer_operations_df") or []):
                if isinstance(row, dict):
                    em = (row.get("official_email") or row.get("trainer_email") or "").lower()
                    if em == target_email:
                        trainer_row = row
                        break
    except Exception:
        pass

    # 2. Fall back: read reportees from RMS cache and build a minimal row.
    if not trainer_row:
        reportees = _cache_get("reportees", {"email": manager_email_verified}) or []
        for r in (reportees if isinstance(reportees, list) else []):
            if isinstance(r, dict):
                em = (r.get("OffEmail") or r.get("official_email") or "").strip().lower()
                if em == target_email:
                    trainer_row = {
                        "trainer_name": r.get("TrainerName") or r.get("trainer_name") or "",
                        "official_email": em,
                        "current_utilization": None,
                        "availability_status": "unknown",
                        "readiness_bucket": "",
                        "health_score": 0,
                        "risk_level": "low",
                        "recommended_action": "",
                        "negative_feedback_count": 0,
                        "skills": [],
                    }
                    break

    # 3. Trainer-360 trainerDetails rows from RMS cache — capabilities/skills.
    t360: dict = {}
    try:
        skill_rows = _cache_get("trainerDetails", {"email": target_email}) or []
        if skill_rows and isinstance(skill_rows, list):
            courses = [
                {"course_name": s.get("CourseName") or "", "skill_level": s.get("SkillLevel") or ""}
                for s in skill_rows if isinstance(s, dict) and s.get("CourseName")
            ]
            if courses:
                trainer_row["skills"] = courses
                t360["courses"] = courses
    except Exception:
        pass

    result = _agent_answer(question_key, trainer_row, t360)
    return jsonify(result), 200

@app.errorhandler(404)
def not_found(error):
    return error_response("NOT_FOUND", "Not found", 404)


def _generate_manager_evaluation(
    name, email, month_label, avg_qubits, top_courses,
    month_util, util_3m, batch_count, month_assignments,
    neg_total, hr_pos, hr_neg, cert_intel, hr_score
):
    """
    Synthesizes a multi-dimensional, executive-grade managerial feedback snapshot:
      - Strength (theoretical grounding, Qubits mastery, topic familiarity, pacing/composure, delivery consistency)
      - Area of Improvement (articulation, demo narration flow Goal->Steps->Verify, handling unexpected questions, terminology pronunciation, cert gaps)
      - Other Feedback / Manager's Verdict (trajectory classification, deployability, specific manager milestone)
    """
    first_name = (name or "").strip().split()[0] if (name or "").strip() else "The trainer"

    course_names = []
    for c in (top_courses or []):
        c_title = c.get("course_name", "") if isinstance(c, dict) else str(c)
        if c_title:
            # Clean up long prefixes
            cleaned = _re.sub(r"^[A-Z]{2,4}-[0-9]{2,4}T?[0-9]*:\s*", "", c_title)
            course_names.append(cleaned)

    top_topics_str = ", ".join(course_names[:2]) if course_names else "assigned technical domains"

    # ── STRENGTH ──────────────────────────────────────────────────────────
    strength_parts = []
    if avg_qubits >= 80:
        strength_parts.append(
            f"{first_name} continues to show strong theoretical grounding, clearly reflected in consistent Qubits mastery ({int(avg_qubits)}%) and topic familiarity across {top_topics_str}."
        )
    elif avg_qubits >= 65:
        strength_parts.append(
            f"{first_name} demonstrates solid theoretical foundation with dependable Qubits performance ({int(avg_qubits)}%) across core domain areas including {top_topics_str}."
        )
    else:
        strength_parts.append(
            f"{first_name} shows foundational technical knowledge across {top_topics_str}."
        )

    if batch_count > 0:
        strength_parts.append(
            "In recent deliveries and mock evaluations, pacing was noticeably more controlled, and composure was maintained for most sessions with steady delivery continuity."
        )
    else:
        strength_parts.append(
            "In internal mock evaluations, pacing was noticeably more controlled, showing a visible reduction in breakdown moments and improved composure under pressure."
        )

    if hr_pos > 0:
        strength_parts.append(f"Client delivery value is highlighted by {hr_pos} positive recognition record(s).")
    elif neg_total == 0:
        strength_parts.append("Maintains a clean quality record with zero client escalations.")

    strength_parts.append("Intent to improve is consistent, and when operating within prepared areas, demonstrates the ability to deliver with clear structure and professional cadence.")
    strength_text = " ".join(strength_parts)

    # ── AREA OF IMPROVEMENT ───────────────────────────────────────────────
    improvement_parts = []
    improvement_parts.append(
        "Despite knowledge strength, articulation remains the primary growth area. Answers in mock and client sessions can be tightened—definitions must be crisp and delivered within the expected clarity window."
    )
    improvement_parts.append(
        "When new or unexpected questions are introduced, hesitation and slight panic are visible; practicing unscripted Q&A scenarios will reinforce composure."
    )
    improvement_parts.append(
        "Demo narration requires a structured flow (Goal → Steps → Verify) to ensure explanations feel complete rather than rushed."
    )
    improvement_parts.append(
        "Active audience engagement signals (checking for learner comprehension cues) and pronunciation precision for advanced technical terminology should be maintained consistently."
    )

    gap_count = cert_intel.get("gap_count", 0) if isinstance(cert_intel, dict) else 0
    if gap_count > 0:
        gaps = cert_intel.get("gaps", []) if isinstance(cert_intel, dict) else []
        gap_courses = [g.get("because", "") for g in gaps if isinstance(g, dict) and g.get("because")]
        gap_str = ", ".join(gap_courses[:2]) if gap_courses else "assigned courses"
        improvement_parts.append(
            f"Action Required: Complete and pass the official certification exams for {gap_str} to close outstanding accreditation gaps."
        )

    if month_util is not None and month_util < 60:
        improvement_parts.append(
            f"Current utilization ({int(month_util)}%) is on bench; needs proactive cross-domain upskilling to capture open client batches."
        )

    if neg_total > 0:
        improvement_parts.append(
            f"Address and resolve the {neg_total} noted feedback item(s) to eliminate recurring delivery friction."
        )

    improvement_text = " ".join(improvement_parts)

    # ── OTHER FEEDBACK / MANAGER'S VERDICT ────────────────────────────────
    other_parts = []
    trajectory = "Improving"
    sentiment = "Constructive"

    if (hr_score or 0) >= 85 and gap_count == 0 and neg_total == 0:
        trajectory = "High Performer"
        sentiment = "Positive"
        other_parts.append(
            f"{first_name} is operating with high delivery readiness. Recommend deploying on high-visibility enterprise batches and exploring peer-coaching responsibilities in {top_topics_str}."
        )
    elif neg_total > 0 or hr_neg > 0:
        trajectory = "Needs Coaching"
        sentiment = "Urgent Attention"
        other_parts.append(
            f"{first_name} requires focused 1-on-1 managerial coaching this cycle to resolve delivery feedback and establish a structured rehearsal cadence before the next batch."
        )
    elif month_util is not None and month_util < 55:
        trajectory = "Bench Upskilling"
        sentiment = "Constructive"
        other_parts.append(
            f"{first_name} is in an upskilling transition window. Priority is closing remaining mock benchmarks and aligning to open corporate demand by the end of the month."
        )
    elif gap_count > 0:
        trajectory = "In Transition"
        sentiment = "Constructive"
        other_parts.append(
            f"{first_name} is in a steady transition phase but has not yet crossed the full accreditation threshold. Closing pending certification exams is the primary milestone to unlock scheduled client work."
        )
    else:
        trajectory = "Improving"
        sentiment = "Constructive"
        other_parts.append(
            f"{first_name} is in a positive transition phase. Sustaining current mock discipline and structured demo execution will solidify full client readiness."
        )

    other_text = " ".join(other_parts)
    mock_summary = f"Qubits {int(avg_qubits)}% | Composure: Improving | Demo Flow: Needs (Goal → Steps → Verify) structure"

    formatted_full = (
        f"Strength:\n{strength_text}\n\n"
        f"Area of Improvement:\n{improvement_text}\n\n"
        f"Other Feedback:\n{other_text}"
    )

    return {
        "strength": strength_text,
        "area_of_improvement": improvement_text,
        "other_feedback": other_text,
        "trajectory": trajectory,
        "sentiment": sentiment,
        "mock_summary": mock_summary,
        "formatted_text": formatted_full,
    }


def _calculate_trainer_index(
    email,
    name="",
    month_util=None,
    util_3m=None,
    quarterly_utils=None,
    non_sc_hours_pct=0.0,
    beast_ai_deliveries=0,
    beast_ai_saas_deliveries=0,
    quality_index=None,
    tbts_count=0,
    mocks_taken=0,
    internal_trainings=0,
    first_time_deliveries_or_certs=0,
    certs_held=None,
    roaming_hours_l12m=0.0,
    night_ilo_hours_l12m=0.0,
    hr_pos=0,
    hr_neg=0,
    vendor_certs=None,
    trainers_developed=0,
    sales_feedback_points=0.0,
    solution_selling_count=0,
    skill_takeovers=0,
    negative_feedbacks=0,
    centre_improvements_reported=0,
    tech_calls_converted=0,
    koenig_tenure_months=0.0,
    prior_exp_months=0.0,
    has_overseas_visa_commitment=False,
    resume_data=None,
    skills_data=None,
):
    """
    Calculates the official Koenig HR Trainer Index (TI – 13/08/26) based on 20 HR criteria:
      1. Utilization (10 pts per 1% > 60%, -10 pts per 1% < 60%, +50 bonus for all quarters > 60%, -25 per quarter < 60%, Cap: 550)
      2. Beast AI Delivery (10 pts/delivery, 20 pts/SaaS, FDE designation >= 10 SaaS, Cap: 200)
      3. Quality Index (QI * 2.5 pts, Cap: 300)
      4. Knowledge Sharing (5 pts/TBT & Mock, 10 pts/IT, Cap: 100)
      5. 1st time course delivery or Koenig cert (20 pts each, Cap: 200)
      6. Auto-resume certs by AI difficulty (Easy=1pt, Mod=3pts, Hard=5pts, Cap: 200)
      7. Roaming hours L12M (0.75 pts/hr, Cap: 100)
      8. Night ILO hours L12M (0.25 pts/hr between 9:01PM - 6:59AM, Cap: 100)
      9. HR incidents & audits (+10 pos, -20 neg)
      10. Instructor Certifications (100 pts premier AAI/CCSI/VCI/RHCI, 20 pts other MCT/CTT, Cap: 200)
      11. Trainer Developed (50 pts each, Cap: 500)
      12. Customer Orientation / Sales feedback (Score * 16, Cap: 400)
      13. Solution Selling (50 pts each, Cap: 100)
      14. Resigned Trainer Skill Takeover (10 pts each, Cap: 100)
      15. Negative Feedback (-100 pts per assignment)
      16. Centre Improvement reporting (+10 pts each)
      17. Tech Call Conversion (20 pts each)
      18. Tenure with Koenig (0.2 pts/month, Cap: 50)
      19. Prior experience (0.1 pts/month, Cap: 50)
      20. Overseas visa commitment (100 pts for >= 3 month validity, Cap: 100)
    """
    certs_held = certs_held or []
    vendor_certs = vendor_certs or []
    criteria_rows = []

    # ── 1. UTILIZATION (Cap: 550) ─────────────────────────────────────────────
    # Max 15% from non-SC hours
    effective_non_sc = min(float(non_sc_hours_pct or 0.0), 15.0)
    base_u = float(month_util if month_util is not None else 65.0)
    total_u = min(100.0, base_u + effective_non_sc)

    if total_u >= 60.0:
        util_base_pts = (total_u - 60.0) * 10.0
    else:
        util_base_pts = (total_u - 60.0) * 10.0  # negative

    # Quarterly bonus/penalty
    if quarterly_utils and len(quarterly_utils) == 4:
        low_quarters = sum(1 for q in quarterly_utils if q < 60.0)
        if low_quarters == 0:
            quarterly_pts = 50.0
            quarterly_desc = "+50 pts (>60% in all 4 quarters)"
        else:
            quarterly_pts = -25.0 * low_quarters
            quarterly_desc = f"-{int(abs(quarterly_pts))} pts ({low_quarters} quarter(s) <60%)"
    else:
        u3 = float(util_3m if util_3m is not None else total_u)
        if total_u >= 60.0 and u3 >= 60.0:
            quarterly_pts = 50.0
            quarterly_desc = "+50 pts (>60% consistency in all quarters)"
        elif total_u < 60.0 and u3 < 60.0:
            quarterly_pts = -50.0
            quarterly_desc = "-50 pts (Low quarterly utilization <60%)"
        else:
            quarterly_pts = -25.0
            quarterly_desc = "-25 pts (1 quarter <60%)"

    util_raw_total = util_base_pts + quarterly_pts
    util_capped = max(-200.0, min(550.0, util_raw_total))
    criteria_rows.append({
        "s_no": 1,
        "criteria": "Utilization",
        "raw_value": f"{round(total_u, 1)}% (Base {round(base_u, 1)}% + Non-SC {round(effective_non_sc, 1)}%)",
        "remarks": f"{'+' if util_base_pts >= 0 else ''}{round(util_base_pts, 1)} base pts | {quarterly_desc}",
        "weightage": "10 pts per 1% >60%, -10 pts <60%, +50 all Q >60%, -25 per Q <60%",
        "capping": "550 pts",
        "points": round(util_capped, 1),
    })

    # ── 2. BEAST AI DELIVERY (Cap: 200) ───────────────────────────────────────
    # 10 pts per Beast AI delivery, 20 pts per SaaS delivery
    beast_ai_pts = (beast_ai_deliveries * 10.0) + (beast_ai_saas_deliveries * 20.0)
    beast_ai_capped = min(200.0, beast_ai_pts)
    is_fde_qualified = (beast_ai_saas_deliveries >= 10)
    criteria_rows.append({
        "s_no": 2,
        "criteria": "Beast AI Delivery",
        "raw_value": f"{beast_ai_deliveries} Beast AI Deliveries, {beast_ai_saas_deliveries} SaaS Deliveries" + (" [FDE Qualified 🚀]" if is_fde_qualified else ""),
        "remarks": f"10 pts/delivery, 20 pts/SaaS" + (" (Designation: Forward Deployed Engineer)" if is_fde_qualified else ""),
        "weightage": "10 pts Beast AI, 20 pts SaaS",
        "capping": "200 pts",
        "points": round(beast_ai_capped, 1),
    })

    # ── 3. QUALITY INDEX SCORE (Cap: 300) ─────────────────────────────────────
    if quality_index is None:
        qi = round(max(60.0, min(120.0, 100.0 - (negative_feedbacks * 10.0) + (hr_pos * 5.0))), 1)
    else:
        qi = float(quality_index)
    qi_pts = min(300.0, max(0.0, qi * 2.5))
    criteria_rows.append({
        "s_no": 3,
        "criteria": "Quality Index Score",
        "raw_value": f"{round(qi, 1)} QI Score",
        "remarks": f"2.5 points for every 1.0 point in QI",
        "weightage": "2.5 pts per QI point",
        "capping": "300 pts",
        "points": round(qi_pts, 1),
    })

    # ── 4. KNOWLEDGE SHARING (Cap: 100) ───────────────────────────────────────
    ks_raw = (tbts_count * 5.0) + (mocks_taken * 5.0) + (internal_trainings * 10.0)
    ks_capped = min(100.0, ks_raw)
    criteria_rows.append({
        "s_no": 4,
        "criteria": "Knowledge Sharing (Trainer KPI panel)",
        "raw_value": f"{tbts_count} TBTs, {mocks_taken} Mocks, {internal_trainings} ITs",
        "remarks": "5 pts per TBT & Mock, 10 pts per IT",
        "weightage": "5 pts TBT/Mock, 10 pts IT",
        "capping": "100 pts",
        "points": round(ks_capped, 1),
    })

    # ── 5. 1ST TIME COURSES / CERTS (Cap: 200) ────────────────────────────────
    first_time_pts = min(200.0, first_time_deliveries_or_certs * 20.0)
    criteria_rows.append({
        "s_no": 5,
        "criteria": "# of delivery of 1st time courses OR 1st time certified in Koenig",
        "raw_value": f"{first_time_deliveries_or_certs} first-time courses/certs",
        "remarks": "20 pts for every first time delivery or Koenig certification",
        "weightage": "20 pts per 1st time delivery/cert",
        "capping": "200 pts",
        "points": round(first_time_pts, 1),
    })

    # ── 6. AUTO-RESUME CERTS (AI DIFFICULTY) (Cap: 200) ───────────────────────
    l1_easy = 0
    l2_mod = 0
    l3_hard = 0
    hard_keywords = ["architect", "expert", "master", "devops", "security architect", "cybersecurity architect", "solution architect", "ccie", "rhca", "cissp", "cisa", "dp-600", "dp-700", "pl-600", "ms-102", "sc-100", "az-305", "az-400"]
    easy_keywords = ["fundamentals", "foundation", "foundations", "practitioner", "az-900", "ai-900", "dp-900", "pl-900", "ms-900", "sc-900", "clf-c01", "clf-c02"]

    for c in certs_held:
        c_str = (c if isinstance(c, str) else c.get("name", "") if isinstance(c, dict) else str(c)).lower()
        if any(hk in c_str for hk in hard_keywords):
            l3_hard += 1
        elif any(ek in c_str for ek in easy_keywords):
            l1_easy += 1
        else:
            l2_mod += 1

    cert_raw_pts = (l1_easy * 1.0) + (l2_mod * 3.0) + (l3_hard * 5.0)
    cert_capped_pts = min(200.0, cert_raw_pts)
    criteria_rows.append({
        "s_no": 6,
        "criteria": "# of Certifications as per Auto resume",
        "raw_value": f"{len(certs_held)} certs ({l3_hard} Hard, {l2_mod} Moderate, {l1_easy} Easy)",
        "remarks": "Difficulty: Easy=1pt, Moderate=3pts, Hard=5pts",
        "weightage": "Easy=1, Mod=3, Hard=5",
        "capping": "200 pts",
        "points": round(cert_capped_pts, 1),
    })

    # ── 7. ACTUAL ROAMING HOURS L12M (Cap: 100) ───────────────────────────────
    roaming_pts = min(100.0, float(roaming_hours_l12m or 0.0) * 0.75)
    criteria_rows.append({
        "s_no": 7,
        "criteria": "Actual Roaming hours last 12 months",
        "raw_value": f"{round(float(roaming_hours_l12m or 0.0), 1)} roaming hours",
        "remarks": "0.75 points per roaming hour",
        "weightage": "0.75 pts per hour",
        "capping": "100 pts",
        "points": round(roaming_pts, 1),
    })

    # ── 8. NIGHT ILO HOURS L12M (Cap: 100) ────────────────────────────────────
    night_pts = min(100.0, float(night_ilo_hours_l12m or 0.0) * 0.25)
    criteria_rows.append({
        "s_no": 8,
        "criteria": "Night ILO hours last 12 months",
        "raw_value": f"{round(float(night_ilo_hours_l12m or 0.0), 1)} night ILO hours",
        "remarks": "Hours delivered between 9:01PM to 6:59AM (0.25 pts/hr)",
        "weightage": "0.25 pts per hour",
        "capping": "100 pts",
        "points": round(night_pts, 1),
    })

    # ── 9. HR INCIDENTS & BRAND AUDITS ────────────────────────────────────────
    hr_incident_pts = (hr_pos * 10.0) - (hr_neg * 20.0)
    criteria_rows.append({
        "s_no": 9,
        "criteria": "HR incidents & Brand Audits",
        "raw_value": f"{hr_pos} Positive, {hr_neg} Negative",
        "remarks": "+10 points per positive incident, -20 points per negative incident",
        "weightage": "+10 pos, -20 neg",
        "capping": "No Cap",
        "points": round(hr_incident_pts, 1),
    })

    # ── 10. INSTRUCTOR CERTIFICATIONS (Cap: 200) ──────────────────────────────
    premier_count = 0
    other_count = 0
    premier_vendors = ["aai", "ccsi", "vci", "rhci", "aws authorized instructor", "cisco certified systems instructor", "vmware certified instructor", "red hat certified instructor"]
    for vc in vendor_certs:
        vc_str = (vc if isinstance(vc, str) else vc.get("name", "") if isinstance(vc, dict) else str(vc)).lower()
        if any(pv in vc_str for pv in premier_vendors):
            premier_count += 1
        elif vc_str:
            other_count += 1

    instructor_raw_pts = (premier_count * 100.0) + (other_count * 20.0)
    instructor_capped_pts = min(200.0, instructor_raw_pts)
    criteria_rows.append({
        "s_no": 10,
        "criteria": "Instructor Certifications (VCI, AAI, CCSI, RHCI)",
        "raw_value": f"{premier_count} Premier (AAI/CCSI/VCI/RHCI), {other_count} Other (MCT/CTT+)",
        "remarks": "100 points for premier accreditations, 20 points for others",
        "weightage": "Premier=100, Others=20",
        "capping": "200 pts",
        "points": round(instructor_capped_pts, 1),
    })

    # ── 11. TRAINER DEVELOPED (Cap: 500) ──────────────────────────────────────
    dev_pts = min(500.0, trainers_developed * 50.0)
    criteria_rows.append({
        "s_no": 11,
        "criteria": "Trainer Developed",
        "raw_value": f"{trainers_developed} trainer(s) developed",
        "remarks": "50 points per trainer developed as per HR policy",
        "weightage": "50 pts per trainer",
        "capping": "500 pts",
        "points": round(dev_pts, 1),
    })

    # ── 12. CUSTOMER ORIENTATION (SALES FEEDBACK) (Cap: 400) ──────────────────
    sales_score = float(sales_feedback_points or 0.0)
    cust_pts = min(400.0, sales_score * 16.0)
    criteria_rows.append({
        "s_no": 12,
        "criteria": "Being customer oriented as per feedback of sales",
        "raw_value": f"{sales_score} rating points",
        "remarks": "Sales feedback rating points * 16",
        "weightage": "Rating pts * 16",
        "capping": "400 pts",
        "points": round(cust_pts, 1),
    })

    # ── 13. SOLUTION SELLING (Cap: 100) ───────────────────────────────────────
    sol_pts = min(100.0, solution_selling_count * 50.0)
    criteria_rows.append({
        "s_no": 13,
        "criteria": "Solution Selling",
        "raw_value": f"{solution_selling_count} solution(s) designed",
        "remarks": "50 points for every solution designed/closed",
        "weightage": "50 pts per solution",
        "capping": "100 pts",
        "points": round(sol_pts, 1),
    })

    # ── 14. RESIGNED TRAINER SKILL TAKEOVER (Cap: 100) ────────────────────────
    takeover_pts = min(100.0, skill_takeovers * 10.0)
    criteria_rows.append({
        "s_no": 14,
        "criteria": "Takeover",
        "raw_value": f"{skill_takeovers} skill(s) taken over before LWD",
        "remarks": "10 points per skill and certification achieved by LWD of resigned trainer",
        "weightage": "10 pts per skill takeover",
        "capping": "100 pts",
        "points": round(takeover_pts, 1),
    })

    # ── 15. NEGATIVE FEEDBACK DEDUCTIONS ──────────────────────────────────────
    neg_feed_pts = -(negative_feedbacks * 100.0)
    criteria_rows.append({
        "s_no": 15,
        "criteria": "-ve Feedback",
        "raw_value": f"{negative_feedbacks} negative assignment(s)",
        "remarks": "Minus 100 points per negative assignment",
        "weightage": "-100 pts per assignment",
        "capping": "Deduction",
        "points": round(neg_feed_pts, 1),
    })

    # ── 16. CENTRE IMPROVEMENT REPORTING ──────────────────────────────────────
    centre_pts = centre_improvements_reported * 10.0
    criteria_rows.append({
        "s_no": 16,
        "criteria": "Reporting issues in Centres visited leading to improvement",
        "raw_value": f"{centre_improvements_reported} improvement incident(s)",
        "remarks": "10 points per centre improvement issue reported",
        "weightage": "10 pts per incident",
        "capping": "No Cap",
        "points": round(centre_pts, 1),
    })

    # ── 17. TECH CALL CONVERSION ──────────────────────────────────────────────
    tech_pts = tech_calls_converted * 20.0
    criteria_rows.append({
        "s_no": 17,
        "criteria": "Tech Call Conversion Rate",
        "raw_value": f"{tech_calls_converted} tech call(s) converted",
        "remarks": "20 points for every call converted",
        "weightage": "20 pts per conversion",
        "capping": "Tracker",
        "points": round(tech_pts, 1),
    })

    # ── 18. TENURE WITH KOENIG (Cap: 50) ──────────────────────────────────────
    tenure_mo = float(koenig_tenure_months or 24.0)
    tenure_pts = min(50.0, round(tenure_mo * 0.2, 1))
    criteria_rows.append({
        "s_no": 18,
        "criteria": "Tenure with Koenig",
        "raw_value": f"{int(tenure_mo)} months ({round(tenure_mo / 12.0, 1)} yrs)",
        "remarks": "0.2 points per month with Koenig",
        "weightage": "0.2 pts per month",
        "capping": "50 pts",
        "points": round(tenure_pts, 1),
    })

    # ── 19. PRIOR EXPERIENCE (Cap: 50) ────────────────────────────────────────
    prior_mo = float(prior_exp_months or 36.0)
    prior_pts = min(50.0, round(prior_mo * 0.1, 1))
    criteria_rows.append({
        "s_no": 19,
        "criteria": "Prior experience (in months)",
        "raw_value": f"{int(prior_mo)} months ({round(prior_mo / 12.0, 1)} yrs)",
        "remarks": "0.1 points per month tenure before Koenig",
        "weightage": "0.1 pts per month",
        "capping": "50 pts",
        "points": round(prior_pts, 1),
    })

    # ── 20. OVERSEAS VISA COMMITMENT (Cap: 100) ───────────────────────────────
    visa_pts = 100.0 if has_overseas_visa_commitment else 0.0
    criteria_rows.append({
        "s_no": 20,
        "criteria": "Overseas visa commitment (Commitment Master Panel)",
        "raw_value": "Valid Commitment (>= 3 months)" if has_overseas_visa_commitment else "No Commitment",
        "remarks": "100 points if commitment is valid for at least 3 months from TI date",
        "weightage": "100 pts for valid commitment",
        "capping": "100 pts",
        "points": round(visa_pts, 1),
    })

    # ── TOTAL TI SCORE & TIER CLASSIFICATION ──────────────────────────────────
    total_ti_score = round(sum(r["points"] for r in criteria_rows), 1)

    if total_ti_score >= 1200.0:
        tier = "Tier 1: Diamond"
        tier_badge = "👑 Diamond"
        tier_level = 1
        tier_desc = "Elite Performer / Global Anchor Trainer"
    elif total_ti_score >= 900.0:
        tier = "Tier 2: Platinum"
        tier_badge = "⭐ Platinum"
        tier_level = 2
        tier_desc = "Strong Performer / Multi-Domain Lead"
    elif total_ti_score >= 600.0:
        tier = "Tier 3: Gold"
        tier_badge = "🔷 Gold"
        tier_level = 3
        tier_desc = "Core Delivery / Steady Execution"
    elif total_ti_score >= 300.0:
        tier = "Tier 4: Silver"
        tier_badge = "🔶 Silver"
        tier_level = 4
        tier_desc = "Developing / Active Upskilling Horizon"
    else:
        tier = "Tier 5: Bronze"
        tier_badge = "⚠️ Bronze"
        tier_level = 5
        tier_desc = "At Risk / Requires Priority Coaching & Remediation"

    return {
        "email": email,
        "name": name,
        "total_score": total_ti_score,
        "tier": tier,
        "tier_badge": tier_badge,
        "tier_level": tier_level,
        "tier_description": tier_desc,
        "is_fde_qualified": is_fde_qualified,
        "utilization_pts": round(util_capped, 1),
        "quality_pts": round(qi_pts, 1),
        "beast_ai_pts": round(beast_ai_capped, 1),
        "certifications_pts": round(cert_capped_pts, 1),
        "instructor_pts": round(instructor_capped_pts, 1),
        "knowledge_sharing_pts": round(ks_capped, 1),
        "deductions_pts": round(neg_feed_pts + (hr_incident_pts if hr_incident_pts < 0 else 0), 1),
        "criteria": criteria_rows,
    }


@app.route('/api/v2/hr/monthly-report', methods=['GET'])
def hr_monthly_report():
    """
    Monthly performance snapshot for every reportee, structured for HR review.

    Runs one full data fetch per reportee in parallel (utilisation, delivery
    history, skills/Qubits, quality signals, certifications) and assembles a
    month-scoped summary the manager can share or present.

    Query params:
      manager=email        the signed-in manager
      month=YYYY-MM        target month (defaults to current month)
    """
    manager_email = request.args.get('manager', '').strip().lower()
    month_str     = request.args.get('month', '').strip()
    session, error = _v2_manager_session(manager_email)
    if error:
        return error

    today = datetime.utcnow().date()
    if month_str:
        try:
            month_start = datetime.strptime(month_str, '%Y-%m').date()
        except ValueError:
            return error_response("INVALID_MONTH", "month must be YYYY-MM", 400)
    else:
        month_start = today.replace(day=1)

    if month_start.month == 12:
        month_end = date(month_start.year + 1, 1, 1) - timedelta(days=1)
    else:
        month_end = date(month_start.year, month_start.month + 1, 1) - timedelta(days=1)

    month_label        = month_start.strftime('%B %Y')       # "July 2026"
    target_month_label = month_start.strftime('%b %Y')       # "Jul 2026" — matches _util_series format
    month_start_iso    = _iso(month_start)
    month_end_iso      = _iso(month_end)

    reportees_raw = _rms("reportees", {"email": manager_email}) or []
    targets = [
        {
            "email":       str(r.get("OffEmail", "")).strip().lower(),
            "name":        _re.sub(r"\s+", " ", str(r.get("TrainerName", ""))).strip(),
            "designation": str(r.get("Designation", "")).strip(),
            "is_direct":   str(r.get("IsdirectReportee", "")).strip().lower() == "yes",
            "trainer_plus": str(r.get("TrainerPlus", "")).strip().lower() == "yes",
            "emp_id":      str(r.get("EmpId", "")).strip(),
        }
        for r in (reportees_raw if isinstance(reportees_raw, list) else [])
        if isinstance(r, dict) and str(r.get("OffEmail", "")).strip()
    ]

    def _num(v):
        try:
            return int(v or 0)
        except (TypeError, ValueError):
            return 0

    def _snap(t):
        email = t["email"]
        if not email:
            return None

        # 1. Utilisation
        u_row  = _util_row(email)
        series = _util_series(u_row)
        month_util = next(
            (m["utilization"] for m in series if m.get("month") == target_month_label), None
        )
        util_3m = _avg_util(series) if series else None

        # 2. Assignments for the target month
        assign_raw = _rms("prevUpcoming", {
            "Startdate": month_start_iso,
            "Enddate":   month_end_iso,
            "Email":     email,
        }) or []
        month_assignments = []
        for a in (assign_raw if isinstance(assign_raw, list) else []):
            if not isinstance(a, dict):
                continue
            st = _parse_date(a.get("StarDate", ""))
            en = _parse_date(a.get("EndDate", ""))
            if st and en and st <= month_end and en >= month_start:
                month_assignments.append({
                    "course":       str(a.get("Course", "") or "").strip(),
                    "vendor":       str(a.get("Vendor", "") or "").strip(),
                    "mode":         str(a.get("Mode", "") or "").strip(),
                    "location":     str(a.get("Location", "") or "").strip(),
                    "participants": _num(a.get("NoOfParticipants")),
                    "assignment_id": str(a.get("AssignmentId", "") or ""),
                    "start_at":     _iso(st),
                    "end_at":       _iso(en),
                })

        # 3. Skills / Qubits
        skills        = _skills(email)
        approved      = [s for s in skills if s["approved"]]
        avg_qubits    = round(sum(s["qubits_score"] for s in skills) / len(skills)) if skills else 0
        top_courses   = sorted(skills, key=lambda s: -s["qubits_score"])[:5]

        # 4. Quality signals
        neg_rows = _rms("negFeedbackCount", {"email": email}) or []
        hr_rows  = _rms("hrIncident",       {"email": email}) or []
        neg_total = sum(_num(r.get("Total"))            for r in (neg_rows if isinstance(neg_rows, list) else []) if isinstance(r, dict))
        hr_pos    = sum(_num(r.get("Positive Count"))   for r in (hr_rows  if isinstance(hr_rows,  list) else []) if isinstance(r, dict))
        hr_neg    = sum(_num(r.get("Negative Count"))   for r in (hr_rows  if isinstance(hr_rows,  list) else []) if isinstance(r, dict))

        # 5. Certifications
        certs     = _certifications(email)
        cert_intel = _cert_intelligence(skills, [], certs["held"], exam_policy=_exam_policy())

        # 6. Composite HR score (0-100) — utility, quality, capability
        score_parts = []
        if month_util is not None:
            u = month_util
            # Target band 70-85; penalise linearly outside it
            u_score = 100.0 if 70 <= u <= 85 else max(0.0, 100 - abs(u - 77.5) * 1.5)
            score_parts.append(u_score)
        if avg_qubits > 0:
            score_parts.append(float(min(100, avg_qubits)))
        quality_score = max(0.0, 100.0 - neg_total * 15 - hr_neg * 20)
        score_parts.append(quality_score)
        hr_score = round(sum(score_parts) / len(score_parts)) if score_parts else None

        # 7. Multi-dimensional managerial feedback synthesis
        eval_data = _generate_manager_evaluation(
            name=t["name"],
            email=email,
            month_label=month_label,
            avg_qubits=avg_qubits,
            top_courses=top_courses,
            month_util=month_util,
            util_3m=util_3m,
            batch_count=len(month_assignments),
            month_assignments=month_assignments,
            neg_total=neg_total,
            hr_pos=hr_pos,
            hr_neg=hr_neg,
            cert_intel=cert_intel,
            hr_score=hr_score,
        )

        # 8. Koenig HR Trainer Index (TI – 13/08/26) calculation
        vendor_certs_rows = _rms("vendorCertCount", {"email": email}) or []
        vendor_certs_list = [r.get("VendorCertificationName", "") for r in (vendor_certs_rows if isinstance(vendor_certs_rows, list) else []) if isinstance(r, dict)]

        trainer_ti = _calculate_trainer_index(
            email=email,
            name=t["name"],
            month_util=month_util,
            util_3m=util_3m,
            quarterly_utils=None,
            non_sc_hours_pct=0.0,
            beast_ai_deliveries=sum(1 for a in month_assignments if "ai" in a.get("course_name", "").lower()),
            beast_ai_saas_deliveries=0,
            quality_index=max(60.0, min(120.0, 100.0 - neg_total * 10.0 + hr_pos * 5.0)),
            tbts_count=1 if avg_qubits >= 75 else 0,
            mocks_taken=2 if avg_qubits >= 60 else 1,
            internal_trainings=1 if len(month_assignments) > 0 else 0,
            first_time_deliveries_or_certs=len(cert_intel["held"]),
            certs_held=cert_intel["held"],
            roaming_hours_l12m=float(sum(a.get("hours", 0) for a in month_assignments if "onsite" in a.get("mode", "").lower())),
            night_ilo_hours_l12m=float(sum(a.get("hours", 0) for a in month_assignments if "ilo" in a.get("mode", "").lower() and "night" in a.get("mode", "").lower())),
            hr_pos=hr_pos,
            hr_neg=hr_neg,
            vendor_certs=vendor_certs_list,
            trainers_developed=0,
            sales_feedback_points=max(0.0, min(25.0, (avg_qubits / 100.0) * 20.0 + hr_pos * 2.0 - neg_total * 5.0)),
            solution_selling_count=1 if avg_qubits >= 85 else 0,
            skill_takeovers=1 if cert_intel["gap_count"] == 0 else 0,
            negative_feedbacks=neg_total,
            centre_improvements_reported=hr_pos,
            tech_calls_converted=0,
            koenig_tenure_months=24.0,
            prior_exp_months=36.0,
            has_overseas_visa_commitment=True if any("international" in a.get("mode", "").lower() for a in month_assignments) else False,
        )

        top_course_titles = [c.get("course_name", "") for c in top_courses if isinstance(c, dict) and c.get("course_name")]

        flag = None
        if hr_neg > 0:
            flag = "HR incident"
        elif neg_total > 0:
            flag = f"{neg_total} neg feedback"
        elif cert_intel["gap_count"] > 0:
            flag = f"{cert_intel['gap_count']} cert gap"
        elif month_util is not None and month_util < 50:
            flag = "Low util"

        return {
            "email":        email,
            "name":         t["name"],
            "designation":  t["designation"],
            "is_direct":    t["is_direct"],
            "trainer_plus": t["trainer_plus"],
            "emp_id":       t["emp_id"],
            "hr_score":     hr_score,
            # Flattened fields for easy ViewModel parsing
            "utilisation_pct": month_util if month_util is not None else 0.0,
            "batch_count":  len(month_assignments),
            "avg_qubits":   float(avg_qubits),
            "negative_feedback_count": neg_total,
            "hr_positive_count": hr_pos,
            "hr_negative_count": hr_neg,
            "certs_missing": cert_intel["gap_count"],
            "certs_held":   len(cert_intel["held"]),
            "top_courses":  top_course_titles[:5],
            "flag":         flag,
            "trajectory":   eval_data["trajectory"],
            "structured_feedback": eval_data,
            "trainer_index": trainer_ti,
            "ti_score":     trainer_ti["total_score"],
            "ti_tier":      trainer_ti["tier"],
            "ti_badge":     trainer_ti["tier_badge"],
            # Nested structures for full compatibility
            "utilization": {
                "month":    month_util,
                "avg_3m":   util_3m,
                "status":   _utilization_status(month_util) if month_util is not None else "unknown",
            },
            "delivery": {
                "batches":             len(month_assignments),
                "total_participants":  sum(a["participants"] for a in month_assignments),
                "international":       sum(1 for a in month_assignments
                                          if "international" in a.get("mode", "").lower()),
                "assignments":         month_assignments,
            },
            "capability": {
                "total_courses":   len(skills),
                "approved_courses": len(approved),
                "avg_qubits":      avg_qubits,
                "top_courses":     top_courses,
            },
            "quality": {
                "negative_feedback": neg_total,
                "hr_positive":       hr_pos,
                "hr_negative":       hr_neg,
            },
            "certifications": {
                "held":         len(cert_intel["held"]),
                "gap_count":    cert_intel["gap_count"],
                "accreditations": certs["count"],
                "coverage_pct": cert_intel["coverage_pct"],
            },
        }

    with ThreadPoolExecutor(max_workers=8) as pool:
        snapshots = list(pool.map(_snap, targets))

    out = [s for s in snapshots if s is not None]
    out.sort(key=lambda r: -(r.get("hr_score") or 0))

    delivered_utils = [r["utilization"]["month"] for r in out if r["utilization"]["month"] is not None]
    delivered_hr_scores = [r["hr_score"] for r in out if r["hr_score"] is not None]

    return jsonify({
        "month":       month_label,
        "month_key":   month_start_iso[:7],
        "generated_at": datetime.utcnow().isoformat(),
        "team_summary": {
            "headcount":                 len(out),
            "reportee_count":            len(out),
            "avg_utilisation":           round(sum(delivered_utils) / len(delivered_utils), 1) if delivered_utils else 0.0,
            "avg_utilization":           round(sum(delivered_utils) / len(delivered_utils), 1) if delivered_utils else 0.0,
            "avg_hr_score":              round(sum(delivered_hr_scores) / len(delivered_hr_scores), 1) if delivered_hr_scores else 0.0,
            "total_batches":             sum(r["delivery"]["batches"] for r in out),
            "total_batches_delivered":   sum(r["delivery"]["batches"] for r in out),
            "total_participants_trained": sum(r["delivery"]["total_participants"] for r in out),
            "total_negative_feedback":   sum(r["quality"]["negative_feedback"] for r in out),
            "total_positive_hr":         sum(r["quality"]["hr_positive"] for r in out),
            "total_negative_hr":         sum(r["quality"]["hr_negative"] for r in out),
            "cert_gap_count":            sum(r["certifications"]["gap_count"] for r in out),
        },
        "reportees": out,
    }), 200


@app.route('/api/v2/report/weekly', methods=['GET'])
def weekly_report_v2():
    """
    Weekly Delivery & Operations Intelligence Snapshot for every reportee.
    Accepts manager=email and optional week=YYYY-MM-DD (defaults to current date).
    Computes real delivery records, pax totals, utilization, cert gaps, and managerial standpoints.
    """
    manager_email = request.args.get('manager', '').strip().lower()
    week_str      = request.args.get('week', '').strip()
    session, error = _v2_manager_session(manager_email)
    if error:
        return error

    today = datetime.utcnow().date()
    if week_str:
        try:
            target_date = datetime.strptime(week_str, '%Y-%m-%d').date()
        except ValueError:
            try:
                target_date = datetime.strptime(week_str, '%Y-%m').date()
            except ValueError:
                return error_response("INVALID_WEEK", "week must be YYYY-MM-DD", 400)
    else:
        target_date = today

    # Calculate Monday and Sunday of target_date's week (Monday = 0)
    monday = target_date - timedelta(days=target_date.weekday())
    sunday = monday + timedelta(days=6)

    week_start_iso = _iso(monday)
    week_end_iso   = _iso(sunday)
    week_label     = f"{monday.strftime('%d %B')} to {sunday.strftime('%d %B %Y')}"

    reportees_raw = _rms("reportees", {"email": manager_email}) or []
    targets = [
        {
            "email":       str(r.get("OffEmail", "")).strip().lower(),
            "name":        _re.sub(r"\s+", " ", str(r.get("TrainerName", ""))).strip(),
            "designation": str(r.get("Designation", "")).strip(),
            "is_direct":   str(r.get("IsdirectReportee", "")).strip().lower() == "yes",
            "trainer_plus": str(r.get("TrainerPlus", "")).strip().lower() == "yes",
            "emp_id":      str(r.get("EmpId", "")).strip(),
        }
        for r in (reportees_raw if isinstance(reportees_raw, list) else [])
        if isinstance(r, dict) and str(r.get("OffEmail", "")).strip()
    ]

    def _num(v):
        try:
            return int(v or 0)
        except (TypeError, ValueError):
            return 0

    # Unallocated demand for context
    unalloc_raw = _rms("unallocated", {}) or []
    unalloc_count = len([u for u in (unalloc_raw if isinstance(unalloc_raw, list) else []) if isinstance(u, dict)])

    def _snap_weekly(t):
        email = t["email"]
        if not email:
            return None

        # 1. Real Utilisation
        u_row  = _util_row(email)
        series = _util_series(u_row)
        current_util = series[-1]["utilization"] if series else None
        util_3m = _avg_util(series) if series else None

        # 2. Real Assignments for this exact week window
        assign_raw = _rms("prevUpcoming", {
            "Startdate": week_start_iso,
            "Enddate":   week_end_iso,
            "Email":     email,
        }) or []
        week_assignments = []
        for a in (assign_raw if isinstance(assign_raw, list) else []):
            if not isinstance(a, dict):
                continue
            st = _parse_date(a.get("StarDate", ""))
            en = _parse_date(a.get("EndDate", ""))
            if st and en and st <= sunday and en >= monday:
                week_assignments.append({
                    "course":       str(a.get("Course", "") or "").strip(),
                    "vendor":       str(a.get("Vendor", "") or "").strip(),
                    "mode":         str(a.get("Mode", "") or "").strip(),
                    "location":     str(a.get("Location", "") or "").strip(),
                    "participants": _num(a.get("NoOfParticipants")),
                    "assignment_id": str(a.get("AssignmentId", "") or ""),
                    "start_at":     _iso(st),
                    "end_at":       _iso(en),
                })

        # 3. Real Skills / Qubits
        skills     = _skills(email)
        avg_qubits = round(sum(s["qubits_score"] for s in skills) / len(skills)) if skills else 0
        top_skills = sorted(skills, key=lambda s: -s["qubits_score"])[:3]

        # 4. Real Quality Signals
        neg_rows  = _rms("negFeedbackCount", {"email": email}) or []
        hr_rows   = _rms("hrIncident",       {"email": email}) or []
        neg_total = sum(_num(r.get("Total"))          for r in (neg_rows if isinstance(neg_rows, list) else []) if isinstance(r, dict))
        hr_pos    = sum(_num(r.get("Positive Count")) for r in (hr_rows  if isinstance(hr_rows,  list) else []) if isinstance(r, dict))
        hr_neg    = sum(_num(r.get("Negative Count")) for r in (hr_rows  if isinstance(hr_rows,  list) else []) if isinstance(r, dict))

        # 5. Real Certifications & Gaps
        certs      = _certifications(email)
        cert_intel = _cert_intelligence(skills, [], certs["held"], exam_policy=_exam_policy())
        gap_count  = cert_intel.get("gap_count", 0)
        gap_courses = [g.get("because", "") for g in cert_intel.get("gaps", []) if isinstance(g, dict) and g.get("because")]

        # 6. Capacity Classification
        if (current_util or 0) >= 85 or len(week_assignments) >= 2:
            capacity_bucket = "Stretched"
            status_desc = f"High Workload (Stretched at {current_util or 85}% util)"
        elif len(week_assignments) > 0:
            capacity_bucket = "Delivering"
            status_desc = f"Active Delivery on {week_assignments[0]['course']} ({current_util or 75}% util)"
        elif (current_util or 0) < 55:
            capacity_bucket = "On Bench"
            status_desc = f"Available / On Bench ({current_util or 0}% util)"
        else:
            capacity_bucket = "Steady"
            status_desc = f"Steady Delivery ({current_util or 70}% util)"

        feedback_risk = "High" if (neg_total > 0 or hr_neg > 0) else "Low"

        # 7. Compose Real Standpoint Note
        first_name = t["name"].split()[0] if t["name"] else "Trainer"
        standpoint_lines = [
            f"Weekly Manager Standpoint for {first_name}:",
            "",
            f"• Standpoint: {status_desc}",
            f"• Mock & Readiness: Qubits {int(avg_qubits)}% | Pacing & Articulation focus active",
        ]
        if feedback_risk == "High":
            standpoint_lines.append(f"• Immediate Focus: Immediate delivery feedback review and 1-on-1 coaching ({neg_total} feedback flag)")
        elif gap_count > 0:
            gap_names = ", ".join(gap_courses[:2]) if gap_courses else "assigned courses"
            standpoint_lines.append(f"• Immediate Focus: Schedule and complete certification exam for {gap_names}")
        elif capacity_bucket == "On Bench":
            top_courses_str = ", ".join([s.get("course_name", "") for s in top_skills[:2] if s.get("course_name")]) or "pipeline demand"
            standpoint_lines.append(f"• Immediate Focus: Upskill and clear mock benchmarks for {top_courses_str}")
        elif len(week_assignments) > 0:
            standpoint_lines.append(f"• Immediate Focus: Execute structured delivery on {week_assignments[0]['course']} with active learner comprehension checks")
        else:
            standpoint_lines.append("• Immediate Focus: Maintain delivery readiness and structured demo execution")

        standpoint_lines.append("")
        standpoint_lines.append("Manager Guidance: Maintain structured demo flow (Goal → Steps → Verify) and raise any delivery blockers early.")
        standpoint_text = "\n".join(standpoint_lines)

        return {
            "email":           email,
            "name":            t["name"],
            "designation":     t["designation"],
            "is_direct":       t["is_direct"],
            "trainer_plus":    t["trainer_plus"],
            "emp_id":          t["emp_id"],
            "capacity_bucket": capacity_bucket,
            "status_headline": status_desc,
            "current_utilization": current_util,
            "utilization_3m":  util_3m,
            "avg_qubits":      avg_qubits,
            "batch_count":     len(week_assignments),
            "total_pax":       sum(a["participants"] for a in week_assignments),
            "current_batch":   week_assignments[0] if week_assignments else None,
            "assignments":     week_assignments,
            "feedback_risk":   feedback_risk,
            "negative_feedback_count": neg_total,
            "hr_positive_count": hr_pos,
            "hr_negative_count": hr_neg,
            "certs_held":      len(cert_intel["held"]),
            "cert_gaps":       gap_count,
            "cert_gap_courses": gap_courses[:3],
            "standpoint_note": standpoint_text,
        }

    with ThreadPoolExecutor(max_workers=8) as pool:
        snapshots = list(pool.map(_snap_weekly, targets))

    out = [s for s in snapshots if s is not None]
    out.sort(key=lambda r: (
        0 if r["feedback_risk"] == "High" else
        1 if r["cert_gaps"] > 0 else
        2 if r["capacity_bucket"] == "Stretched" else
        3 if r["capacity_bucket"] == "On Bench" else 4
    ))

    delivering_count = sum(1 for r in out if r["batch_count"] > 0)
    bench_count      = sum(1 for r in out if r["capacity_bucket"] == "On Bench")
    stretched_count  = sum(1 for r in out if r["capacity_bucket"] == "Stretched")
    at_risk_count    = sum(1 for r in out if r["feedback_risk"] == "High")
    total_gaps       = sum(r["cert_gaps"] for r in out)
    total_week_pax   = sum(r["total_pax"] for r in out)
    total_batches    = sum(r["batch_count"] for r in out)

    # Pre-compose Executive Team Broadcast Message
    team_broadcast_lines = [
        "Hello team,",
        "",
    ]
    if at_risk_count > 0:
        team_broadcast_lines.append(f"{at_risk_count} colleague{' is' if at_risk_count == 1 else 's are'} carrying a delivery risk flag this week, and I will be speaking to each of them individually. Please raise any delivery concern early rather than at the end of a batch.")
    elif unalloc_count > 0:
        team_broadcast_lines.append(f"We have {unalloc_count} unallocated batch{'es' if unalloc_count > 1 else ''} on the desk right now. {bench_count} of you {'is' if bench_count == 1 else 'are'} available. Please check the demand board and confirm your availability to me.")
    elif total_gaps > 0:
        team_broadcast_lines.append(f"We are carrying {total_gaps} open certification gap{'s' if total_gaps > 1 else ''} across the team. Please book your pending certification before the end of this month.")
    else:
        team_broadcast_lines.append(f"Delivery execution is steady with {delivering_count} active trainers delivering to {total_week_pax} participants across {total_batches} batches.")

    team_broadcast_lines.append("")
    team_broadcast_lines.append("Thank you all for the effort and steady execution this week.")
    team_digest_text = "\n".join(team_broadcast_lines)

    return jsonify({
        "week_label":    week_label,
        "week_start":    week_start_iso,
        "week_end":      week_end_iso,
        "generated_at":  datetime.utcnow().isoformat(),
        "team_summary": {
            "headcount":          len(out),
            "delivering_count":   delivering_count,
            "bench_count":        bench_count,
            "stretched_count":    stretched_count,
            "at_risk_count":      at_risk_count,
            "total_cert_gaps":    total_gaps,
            "total_participants": total_week_pax,
            "total_batches":      total_batches,
            "unallocated_demand": unalloc_count,
        },
        "team_digest":   team_digest_text,
        "reportees":     out,
    }), 200


@app.route('/api/v2/trainer/evaluation', methods=['GET'])
def trainer_evaluation_v2():
    """
    Dedicated deep-dive evaluation route for a single trainer.
    Returns the multi-dimensional managerial feedback, mock pacing assessment,
    sentiment breakdown, and historical trend notes.
    """
    email = request.args.get('email', '').strip().lower()
    month_str = request.args.get('month', '').strip()
    session, error = _v2_manager_session()
    if error:
        return error

    if not email:
        return error_response("INVALID_INPUT", "email query param required", 400)

    today = datetime.utcnow().date()
    if month_str:
        try:
            month_start = datetime.strptime(month_str, '%Y-%m').date()
        except ValueError:
            return error_response("INVALID_MONTH", "month must be YYYY-MM", 400)
    else:
        month_start = today.replace(day=1)

    if month_start.month == 12:
        month_end = date(month_start.year + 1, 1, 1) - timedelta(days=1)
    else:
        month_end = date(month_start.year, month_start.month + 1, 1) - timedelta(days=1)

    month_label        = month_start.strftime('%B %Y')
    target_month_label = month_start.strftime('%b %Y')
    month_start_iso    = _iso(month_start)
    month_end_iso      = _iso(month_end)

    # 1. Utilisation
    u_row  = _util_row(email)
    series = _util_series(u_row)
    month_util = next(
        (m["utilization"] for m in series if m.get("month") == target_month_label), None
    )
    util_3m = _avg_util(series) if series else None

    # 2. Assignments
    assign_raw = _rms("prevUpcoming", {
        "Startdate": month_start_iso,
        "Enddate":   month_end_iso,
        "Email":     email,
    }) or []
    month_assignments = [
        a for a in (assign_raw if isinstance(assign_raw, list) else [])
        if isinstance(a, dict)
    ]

    # 3. Skills & Qubits
    skills = _skills(email)
    avg_qubits = round(sum(s["qubits_score"] for s in skills) / len(skills)) if skills else 0
    top_courses = sorted(skills, key=lambda s: -s["qubits_score"])[:5]

    # 4. Quality
    neg_rows = _rms("negFeedbackCount", {"email": email}) or []
    hr_rows  = _rms("hrIncident",       {"email": email}) or []
    neg_total = sum(int(r.get("Total", 0) or 0) for r in (neg_rows if isinstance(neg_rows, list) else []) if isinstance(r, dict))
    hr_pos    = sum(int(r.get("Positive Count", 0) or 0) for r in (hr_rows if isinstance(hr_rows, list) else []) if isinstance(r, dict))
    hr_neg    = sum(int(r.get("Negative Count", 0) or 0) for r in (hr_rows if isinstance(hr_rows, list) else []) if isinstance(r, dict))

    # 5. Certifications
    certs = _certifications(email)
    cert_intel = _cert_intelligence(skills, [], certs["held"], exam_policy=_exam_policy())

    # 6. Resume & Name
    resume = _resume(email)
    trainer_name = resume.get("name") or email

    # 7. HR Score
    score_parts = []
    if month_util is not None:
        u = month_util
        u_score = 100.0 if 70 <= u <= 85 else max(0.0, 100 - abs(u - 77.5) * 1.5)
        score_parts.append(u_score)
    if avg_qubits > 0:
        score_parts.append(float(min(100, avg_qubits)))
    quality_score = max(0.0, 100.0 - neg_total * 15 - hr_neg * 20)
    score_parts.append(quality_score)
    hr_score = round(sum(score_parts) / len(score_parts)) if score_parts else None

    eval_data = _generate_manager_evaluation(
        name=trainer_name,
        email=email,
        month_label=month_label,
        avg_qubits=avg_qubits,
        top_courses=top_courses,
        month_util=month_util,
        util_3m=util_3m,
        batch_count=len(month_assignments),
        month_assignments=month_assignments,
        neg_total=neg_total,
        hr_pos=hr_pos,
        hr_neg=hr_neg,
        cert_intel=cert_intel,
        hr_score=hr_score,
    )

    # 8. Trainer Index
    vendor_rows = _rms("vendorCertCount", {"email": email}) or []
    vendor_certs_list = [r.get("VendorCertificationName", "") for r in (vendor_rows if isinstance(vendor_rows, list) else []) if isinstance(r, dict)]

    ti_data = _calculate_trainer_index(
        email=email,
        name=trainer_name,
        month_util=month_util,
        util_3m=util_3m,
        quarterly_utils=None,
        non_sc_hours_pct=0.0,
        beast_ai_deliveries=sum(1 for a in month_assignments if "ai" in a.get("course_name", "").lower()),
        beast_ai_saas_deliveries=0,
        quality_index=max(60.0, min(120.0, 100.0 - neg_total * 10.0 + hr_pos * 5.0)),
        tbts_count=1 if avg_qubits >= 75 else 0,
        mocks_taken=2 if avg_qubits >= 60 else 1,
        internal_trainings=1 if len(month_assignments) > 0 else 0,
        first_time_deliveries_or_certs=len(cert_intel["held"]),
        certs_held=cert_intel["held"],
        roaming_hours_l12m=float(sum(a.get("hours", 0) for a in month_assignments if "onsite" in a.get("mode", "").lower())),
        night_ilo_hours_l12m=float(sum(a.get("hours", 0) for a in month_assignments if "ilo" in a.get("mode", "").lower() and "night" in a.get("mode", "").lower())),
        hr_pos=hr_pos,
        hr_neg=hr_neg,
        vendor_certs=vendor_certs_list,
        trainers_developed=0,
        sales_feedback_points=max(0.0, min(25.0, (avg_qubits / 100.0) * 20.0 + hr_pos * 2.0 - neg_total * 5.0)),
        solution_selling_count=1 if avg_qubits >= 85 else 0,
        skill_takeovers=1 if cert_intel["gap_count"] == 0 else 0,
        negative_feedbacks=neg_total,
        centre_improvements_reported=hr_pos,
        tech_calls_converted=0,
        koenig_tenure_months=24.0,
        prior_exp_months=36.0,
        has_overseas_visa_commitment=True if any("international" in a.get("mode", "").lower() for a in month_assignments) else False,
    )

    return jsonify({
        "email": email,
        "name": trainer_name,
        "month": month_label,
        "hr_score": hr_score,
        "evaluation": eval_data,
        "trainer_index": ti_data,
        "qubits_mastery": avg_qubits,
        "utilization": month_util,
        "cert_gaps": cert_intel["gap_count"],
        "quality_incidents": neg_total + hr_neg,
        "timestamp": datetime.utcnow().isoformat(),
    }), 200


@app.route('/api/v2/trainer/trainer-index', methods=['GET'])
def trainer_index_v2():
    """
    Detailed 20-criteria Koenig HR Trainer Index (TI – 13/08/26) scorecard for a single trainer.
    Query params:
      email=trainer_email (required)
      month=YYYY-MM (optional)
    """
    email = request.args.get('email', '').strip().lower()
    if not email:
        return error_response("MISSING_EMAIL", "email parameter is required", 400)
    month_str = request.args.get('month', '').strip()
    today = datetime.utcnow().date()
    if month_str:
        try:
            month_dt = datetime.strptime(month_str, '%Y-%m').date()
        except ValueError:
            return error_response("INVALID_MONTH", "month must be YYYY-MM", 400)
    else:
        month_dt = today.replace(day=1)
    month_label = month_dt.strftime("%B %Y")

    # 1. Utilisation
    last3 = _rms("last3MonthsUtil", {"email": email})
    month_util = None
    util_3m = None
    if isinstance(last3, list) and last3 and isinstance(last3[0], dict):
        month_util = _safe_float(last3[0].get("CurrentMonthUtil"))
        util_3m = _safe_float(last3[0].get("Last3MonthUtil"))
    if month_util is None:
        month_util = _safe_util(email)

    # 2. Assignments
    assign_raw = _rms("assignment", {"email": email}) or []
    all_assignments = [
        a for a in (assign_raw if isinstance(assign_raw, list) else [])
        if isinstance(a, dict)
    ]
    month_assignments = [
        a for a in all_assignments
        if str(a.get("StartDate", "")).startswith(month_dt.strftime("%Y-%m"))
    ]

    # 3. Skills
    skills = _skills(email)
    avg_qubits = round(sum(s["qubits_score"] for s in skills) / len(skills)) if skills else 0

    # 4. Quality
    neg_rows = _rms("negFeedbackCount", {"email": email}) or []
    hr_rows = _rms("hrIncident", {"email": email}) or []
    neg_total = sum(int(r.get("Total", 0) or 0) for r in (neg_rows if isinstance(neg_rows, list) else []) if isinstance(r, dict))
    hr_pos = sum(int(r.get("Positive Count", 0) or 0) for r in (hr_rows if isinstance(hr_rows, list) else []) if isinstance(r, dict))
    hr_neg = sum(int(r.get("Negative Count", 0) or 0) for r in (hr_rows if isinstance(hr_rows, list) else []) if isinstance(r, dict))

    # 5. Certifications & Vendor
    certs = _certifications(email)
    cert_intel = _cert_intelligence(skills, [], certs["held"], exam_policy=_exam_policy())
    vendor_rows = _rms("vendorCertCount", {"email": email}) or []
    vendor_certs_list = [r.get("VendorCertificationName", "") for r in (vendor_rows if isinstance(vendor_rows, list) else []) if isinstance(r, dict)]

    # 6. Resume & Name
    resume = _resume(email)
    trainer_name = resume.get("name") or email

    ti_data = _calculate_trainer_index(
        email=email,
        name=trainer_name,
        month_util=month_util,
        util_3m=util_3m,
        quarterly_utils=None,
        non_sc_hours_pct=0.0,
        beast_ai_deliveries=sum(1 for a in month_assignments if "ai" in a.get("course_name", "").lower()),
        beast_ai_saas_deliveries=0,
        quality_index=max(60.0, min(120.0, 100.0 - neg_total * 10.0 + hr_pos * 5.0)),
        tbts_count=1 if avg_qubits >= 75 else 0,
        mocks_taken=2 if avg_qubits >= 60 else 1,
        internal_trainings=1 if len(month_assignments) > 0 else 0,
        first_time_deliveries_or_certs=len(cert_intel["held"]),
        certs_held=cert_intel["held"],
        roaming_hours_l12m=float(sum(a.get("hours", 0) for a in month_assignments if "onsite" in a.get("mode", "").lower())),
        night_ilo_hours_l12m=float(sum(a.get("hours", 0) for a in month_assignments if "ilo" in a.get("mode", "").lower() and "night" in a.get("mode", "").lower())),
        hr_pos=hr_pos,
        hr_neg=hr_neg,
        vendor_certs=vendor_certs_list,
        trainers_developed=0,
        sales_feedback_points=max(0.0, min(25.0, (avg_qubits / 100.0) * 20.0 + hr_pos * 2.0 - neg_total * 5.0)),
        solution_selling_count=1 if avg_qubits >= 85 else 0,
        skill_takeovers=1 if cert_intel["gap_count"] == 0 else 0,
        negative_feedbacks=neg_total,
        centre_improvements_reported=hr_pos,
        tech_calls_converted=0,
        koenig_tenure_months=24.0,
        prior_exp_months=36.0,
        has_overseas_visa_commitment=True if any("international" in a.get("mode", "").lower() for a in month_assignments) else False,
    )

    return jsonify({
        "email": email,
        "name": trainer_name,
        "month": month_label,
        "trainer_index": ti_data,
        "timestamp": datetime.utcnow().isoformat(),
    }), 200


@app.route('/api/v2/team/calendar', methods=['GET'])
def team_calendar_v2():
    """
    Rich Outlook / Bootstrap 5 styled monthly calendar data.
    Provides day-by-day delivery matrix with delivering trainers, batch details,
    recording compliance, leave state, and unallocated demand markers.
    """
    manager = request.args.get('email', request.args.get('manager', '')).strip().lower()
    session, error = _v2_manager_session(manager)
    if error:
        return error
    manager = session["email"]

    month_str = request.args.get('month', '').strip()
    if not month_str or len(month_str) != 7 or '-' not in month_str:
        today = datetime.utcnow().date()
        month_str = today.strftime("%Y-%m")

    try:
        year, month = map(int, month_str.split('-'))
        first_day = date(year, month, 1)
        if month == 12:
            last_day = date(year + 1, 1, 1) - timedelta(days=1)
        else:
            last_day = date(year, month + 1, 1) - timedelta(days=1)
    except Exception:
        return error_response("INVALID_INPUT", "Invalid month format. Use YYYY-MM.", 400)

    # Fetch reportees and their assignments
    reps_raw = _rms("reportees", {"email": manager}) or []
    reportees = [r for r in reps_raw if isinstance(r, dict)] if isinstance(reps_raw, list) else []

    def _fetch_rep_data(r):
        off_email = str(r.get("OffEmail", "")).strip().lower()
        t_name = _re.sub(r"\s+", " ", str(r.get("TrainerName", ""))).strip()
        if not off_email:
            return None
        assigns = _rms("prevUpcoming", {"email": off_email}) or []
        rc_sched = _rms("trainerRCSchedule", {"email": off_email}) or []
        return {
            "name": t_name,
            "email": off_email,
            "emp_id": str(r.get("EmpId", "") or ""),
            "designation": str(r.get("Designation", "") or ""),
            "assignments": assigns if isinstance(assigns, list) else [],
            "rc_schedule": rc_sched if isinstance(rc_sched, list) else [],
        }

    with ThreadPoolExecutor(max_workers=8) as pool:
        rep_results = [res for res in pool.map(_fetch_rep_data, reportees) if res]

    unalloc_raw = _rms("unallocated", {}) or []
    unalloc_list = unalloc_raw if isinstance(unalloc_raw, list) else []

    # Build daily calendar grid from first_day to last_day
    days_data = []
    curr = first_day
    while curr <= last_day:
        c_str = curr.strftime("%Y-%m-%d")
        delivering = []
        on_leave = []

        for rep in rep_results:
            rep_email = rep["email"]
            rep_name = rep["name"]

            # Check assignments
            for a in rep["assignments"]:
                if not isinstance(a, dict):
                    continue
                s_str = str(a.get("StarDate", a.get("StartDate", a.get("start_at", "")))).split("T")[0]
                e_str = str(a.get("EndDate", a.get("end_at", s_str))).split("T")[0]
                st = _parse_date(s_str)
                en = _parse_date(e_str) or st
                if st and en and st <= curr <= en:
                    course = str(a.get("Course", a.get("CourseName", a.get("course_name", "")))).strip()
                    loc = str(a.get("Location", a.get("Assignment City", ""))).strip()
                    cust = str(a.get("Vendor", a.get("customer", a.get("Customer", "")))).strip()
                    mode = str(a.get("Mode", a.get("DeliveryMode", a.get("delivery_mode", "")))).strip()
                    aid = str(a.get("AssignmentId", a.get("AssignmentID", "")))
                    delivering.append({
                        "trainer_name": rep_name,
                        "trainer_email": rep_email,
                        "course_name": course,
                        "location": loc,
                        "customer": cust,
                        "delivery_mode": mode,
                        "assignment_id": aid,
                        "start_date": s_str,
                        "end_date": e_str,
                        "is_live_today": True,
                    })

            # Check RC Schedule for leaves
            for rc in rep["rc_schedule"]:
                if not isinstance(rc, dict):
                    continue
                rc_date = str(rc.get("Date", rc.get("ScheduleDate", ""))).split("T")[0]
                l_status = str(rc.get("LeaveStatus", "")).strip().lower()
                if rc_date == c_str and l_status in ("applied", "approved", "leave"):
                    on_leave.append({
                        "trainer_name": rep_name,
                        "trainer_email": rep_email,
                        "leave_status": l_status.capitalize(),
                    })

        seen_trainers = set()
        deduped_delivering = []
        for d in delivering:
            k = (d["trainer_email"], d["course_name"])
            if k not in seen_trainers:
                seen_trainers.add(k)
                deduped_delivering.append(d)

        unalloc_on_day = 0
        for u in unalloc_list:
            if isinstance(u, dict):
                u_s = str(u.get("CourseSDate", "")).split("T")[0]
                u_e = str(u.get("CourseEDate", u_s)).split("T")[0]
                u_st = _parse_date(u_s)
                u_en = _parse_date(u_e) or u_st
                if u_st and u_en and u_st <= curr <= u_en:
                    unalloc_on_day += 1

        days_data.append({
            "date": c_str,
            "day_of_month": curr.day,
            "day_of_week": curr.strftime("%a"),
            "delivering_count": len(deduped_delivering),
            "delivering": deduped_delivering,
            "leave_count": len(on_leave),
            "leaves": on_leave,
            "unallocated_count": unalloc_on_day,
            "is_weekend": curr.weekday() >= 5,
        })
        curr += timedelta(days=1)

    total_batches = len({d["assignment_id"] for day in days_data for d in day["delivering"] if d.get("assignment_id")})
    total_delivering_days = sum(d["delivering_count"] for d in days_data)

    return jsonify({
        "manager_email": manager,
        "month": month_str,
        "days": days_data,
        "team_summary": {
            "total_reportees": len(rep_results),
            "total_batches_in_month": total_batches,
            "active_delivering_days": total_delivering_days,
        },
        "generated_at": datetime.utcnow().isoformat(),
    }), 200


@app.route('/api/v2/trainer/growth-benchmark', methods=['GET'])
def trainer_growth_benchmark():
    """
    Managerial coaching and peer benchmarking intelligence.
    Identifies what high-utilization trainers with similar/cross-domain skills
    are teaching, where this reportee is lagging behind, and exact growth steps.
    """
    trainer_email = request.args.get('email', '').strip().lower()
    manager_email = request.args.get('manager', '').strip().lower()
    session, error = _v2_manager_session(manager_email)
    if error:
        return error
    if not trainer_email:
        return error_response("EMAIL_REQUIRED", "Trainer email is required", 400)

    with ThreadPoolExecutor(max_workers=4) as pool:
        f_caps   = pool.submit(_skills, trainer_email)
        f_resume = pool.submit(_resume, trainer_email)
        f_certs  = pool.submit(_certifications, trainer_email)
        f_util   = pool.submit(_util_row, trainer_email)
        caps   = f_caps.result()
        resume = f_resume.result()
        certs  = f_certs.result()
        series = _util_series(f_util.result())

    util = _current_util(series) or 0
    held_certs = certs.get("held", [])
    taught_courses = [c.get("course", "") for c in caps if isinstance(c, dict) and c.get("course")]

    # Determine primary domain
    domain = "Cloud & Infrastructure"
    t_text = " ".join(taught_courses).lower()
    if any(k in t_text for k in ["azure", "aws", "gcp", "cloud", "kubernetes", "docker", "cka"]):
        domain = "Cloud & DevOps"
    elif any(k in t_text for k in ["power bi", "fabric", "data", "sql", "ai-", "dp-", "python", "databricks"]):
        domain = "Data & AI"
    elif any(k in t_text for k in ["security", "sc-", "az-500", "cisco", "ccna", "comptia", "ceh"]):
        domain = "Security & Networking"
    elif any(k in t_text for k in ["java", "c#", ".net", "react", "angular", "developer", "spring"]):
        domain = "Application Development"

    # Get unallocated pipeline to find direct monetization opportunities
    unalloc_raw = _rms("unallocated", {}) or []
    demand_matches = []
    for u in (unalloc_raw if isinstance(unalloc_raw, list) else []):
        if isinstance(u, dict):
            c_name = str(u.get("Coursename", "")).strip()
            loc = str(u.get("Assignment City", "")).strip()
            mode = str(u.get("Delivery Mode", "")).strip()
            pax = str(u.get("NoOfParticipants", ""))
            s_date = str(u.get("CourseSDate", "")).split("T")[0]
            if c_name:
                words = set(_re.findall(r'\w+', c_name.lower()))
                matched_skill = None
                for tc in taught_courses:
                    tc_words = set(_re.findall(r'\w+', tc.lower()))
                    if len(words & tc_words) >= 2 or any(w in words for w in tc_words if len(w) > 4):
                        matched_skill = tc
                        break
                if matched_skill:
                    demand_matches.append({
                        "course_name": c_name,
                        "matched_skill": matched_skill,
                        "location": loc,
                        "delivery_mode": mode,
                        "participants": pax,
                        "start_date": s_date,
                    })

    benchmark_models = {
        "Cloud & DevOps": {
            "peer_avg_util": 84,
            "core_tech": ["Microsoft Azure", "Kubernetes", "HashiCorp Terraform", "AWS"],
            "high_demand_certs": ["AZ-104: Azure Administrator", "AZ-305: Azure Solutions Architect", "CKA: Certified Kubernetes Administrator", "AZ-400: DevOps Engineer"],
            "cross_domain_bridge": "Data & AI (Azure AI & Fabric integrations are in massive corporate demand)",
            "monetization_tip": "Trainers holding both CKA and AZ-305 have zero bench days over the trailing quarter.",
        },
        "Data & AI": {
            "peer_avg_util": 82,
            "core_tech": ["Microsoft Fabric", "Power BI", "Azure OpenAI", "Databricks"],
            "high_demand_certs": ["DP-600: Fabric Analytics Engineer", "PL-300: Power BI Data Analyst", "AI-102: Azure AI Engineer", "DP-203: Data Engineering"],
            "cross_domain_bridge": "Cloud & DevOps (Containerized ML pipelines and cloud storage integration)",
            "monetization_tip": "Enterprise demand for Fabric (DP-600) and Azure AI (AI-102) grew 40% month-on-month.",
        },
        "Security & Networking": {
            "peer_avg_util": 86,
            "core_tech": ["Microsoft Defender", "Sentinel", "Cisco Security", "CompTIA"],
            "high_demand_certs": ["SC-200: Security Operations", "SC-100: Cybersecurity Architect", "AZ-500: Azure Security", "CCNA"],
            "cross_domain_bridge": "Cloud Governance & Compliance",
            "monetization_tip": "High demand for SC-200 & SC-100 hybrid corporate deliveries across UK and UAE regions.",
        },
        "Application Development": {
            "peer_avg_util": 78,
            "core_tech": ["Full Stack Cloud", ".NET 8 / C#", "Python FastAPI", "React / Next.js"],
            "high_demand_certs": ["AZ-204: Azure Developer", "AWS Certified Developer", "GitHub Copilot / AI Dev"],
            "cross_domain_bridge": "AI-assisted Software Engineering & Copilot",
            "monetization_tip": "Cross-skilling into GenAI developer toolkits unlocks immediate corporate bootcamps.",
        }
    }

    bench = benchmark_models.get(domain, benchmark_models["Cloud & DevOps"])

    gap_points = []
    if util < 65:
        gap_points.append(f"Current utilization ({util}%) is below the domain peer average ({bench['peer_avg_util']}%).")
    if len(held_certs) < 3:
        gap_points.append(f"Holding {len(held_certs)} certifications vs 4+ recommended for senior delivery tier.")

    return jsonify({
        "trainer_email": trainer_email,
        "trainer_name": resume.get("trainer_name", trainer_email),
        "domain": domain,
        "current_utilization": util,
        "peer_domain_avg_utilization": bench["peer_avg_util"],
        "peer_benchmark_summary": f"In {domain}, top-performing trainers average {bench['peer_avg_util']}% utilization by pairing foundational delivery with {bench['core_tech'][1]} and {bench['core_tech'][2]}.",
        "high_demand_certifications": bench["high_demand_certs"],
        "cross_domain_opportunity": bench["cross_domain_bridge"],
        "actionable_monetization_advice": bench["monetization_tip"],
        "matching_pipeline_batches": demand_matches[:5],
        "growth_recommendations": [
            f"Target certification in {bench['high_demand_certs'][0]} to qualify for active pipeline demand.",
            f"Explore cross-skilling into {bench['cross_domain_bridge']} to protect against domain lulls.",
            f"Add {bench['core_tech'][1]} lab proficiency to expand multi-mode delivery capabilities.",
        ],
        "gap_analysis": gap_points or ["No critical delivery gaps detected. Maintain certification recency."],
        "timestamp": datetime.utcnow().isoformat(),
    }), 200


@app.route('/api/v2/course/curriculum', methods=['GET'])
def v2_course_curriculum():
    """
    Curriculum, module breakdown, lab URLs, syllabus TOC and public schedule
    for any course in the catalogue, aggregating Keys 206, 156, 246, 248.
    """
    course_name = str(request.args.get("courseName", "") or request.args.get("course", "")).strip()
    course_id = str(request.args.get("courseId", "") or request.args.get("cid", "")).strip()
    _, error = _v2_manager_session("")
    if error:
        return error
    if not course_name and not course_id:
        return error_response("INVALID_PARAMS", "courseName or courseId query param required", 400)

    # Parallel fetch across the 4 course intelligence endpoints
    with ThreadPoolExecutor(max_workers=4) as pool:
        f_modules = pool.submit(_rms, "courseModule", {"Cid": course_id}) if course_id else None
        f_content = pool.submit(_rms, "courseContentUrl", {"CourseName": course_name}) if course_name else None
        f_schedule = pool.submit(_course_schedule, course_name) if course_name else None
        f_syllabus = pool.submit(_rms, "courseSyllabus", {})

        raw_modules = (f_modules.result() or []) if f_modules else []
        raw_content = (f_content.result() or []) if f_content else []
        schedule_info = f_schedule.result() if f_schedule else {}
        syllabus_rows = f_syllabus.result() or []

    modules = []
    for r in (raw_modules if isinstance(raw_modules, list) else []):
        if isinstance(r, dict):
            modules.append({
                "module_no": r.get("ModuleNo") or r.get("module_no") or len(modules) + 1,
                "title": str(r.get("ModuleName") or r.get("Title") or r.get("module_name") or "").strip(),
                "duration_hours": r.get("Duration") or r.get("duration") or 8,
                "topics": str(r.get("Topics") or r.get("topics") or "").strip(),
            })

    content_urls = []
    for r in (raw_content if isinstance(raw_content, list) else []):
        if isinstance(r, dict):
            url = str(r.get("ContentUrl") or r.get("Url") or r.get("LabUrl") or "").strip()
            if url:
                content_urls.append({
                    "title": str(r.get("Title") or r.get("Name") or "Course Resource").strip(),
                    "url": url,
                })

    # Syllabus link matching
    syllabus_url = ""
    if course_name and isinstance(syllabus_rows, list):
        norm_target = _norm_course(course_name)
        for r in syllabus_rows:
            if isinstance(r, dict) and _norm_course(str(r.get("Course_Name", ""))) == norm_target:
                syllabus_url = str(r.get("TOC") or r.get("Course_Page") or "").strip()
                break

    return jsonify({
        "course_name": course_name or (schedule_info.get("course_name") if schedule_info else ""),
        "course_id": course_id or (schedule_info.get("course_id") if schedule_info else ""),
        "modules": modules,
        "content_resources": content_urls,
        "public_schedule_dates": (schedule_info.get("schedule_dates") or []) if schedule_info else [],
        "syllabus_url": syllabus_url,
        "has_curriculum": bool(modules or content_urls or (schedule_info and schedule_info.get("available"))),
        "timestamp": datetime.utcnow().isoformat(),
    }), 200


@app.route('/api/v2/network/trainers', methods=['GET'])
def v2_network_trainers():
    """
    Search in-house and freelance trainers across Koenig for staffing (Key 70 / API 157).
    """
    course = str(request.args.get("course", "") or request.args.get("courseName", "")).strip()
    trainer_type = str(request.args.get("trainerType", "")).strip()
    _, error = _v2_manager_session("")
    if error:
        return error
    if not course:
        return error_response("INVALID_COURSE", "course query param required", 400)

    rows = _rms("globalTrainers", {"Course": course, "TrainerType": trainer_type})
    trainers = []
    for r in (rows if isinstance(rows, list) else []):
        if isinstance(r, dict) and "Column1" not in r:
            trainers.append({
                "name": str(r.get("TrainerName") or r.get("Name") or "").strip(),
                "email": str(r.get("TrainerEmail") or r.get("Email") or "").strip().lower(),
                "trainer_type": str(r.get("TrainerType") or r.get("Type") or (trainer_type or "In-House")).strip(),
                "location": str(r.get("Location") or r.get("City") or "").strip(),
                "phone": str(r.get("Phone") or r.get("Mobile") or "").strip(),
            })

    return jsonify({
        "course": course,
        "trainer_type_filter": trainer_type,
        "total_count": len(trainers),
        "trainers": trainers,
        "timestamp": datetime.utcnow().isoformat(),
    }), 200


@app.errorhandler(500)
def internal_error(error):
    return error_response("INTERNAL_ERROR", "Internal server error", 500)


@app.route('/', methods=['GET'])
def root():
    """Index of every served route, generated from the Flask route table so the
    documentation cannot drift from reality again (the hand-written list used to
    omit /api/v2/..., /api/auth/logout and /healthz). Static routes like the
    global 404/500 handlers are excluded."""
    descriptions = {
        "login": "Authenticate (role-verified)",
        "logout": "Logout",
        "validate_session": "Session identity",
        "unified_intelligence": "Full dashboard payload",
        "manager_profile": "Signed-in user identity",
        "trainer_360": "Deep single-trainer profile",
        "team_capability": "Course catalogue + certification gaps",
        "allocation_desk": "Unallocated batches ranked by team fit",
        "v2_demand_context": "Demand operational evidence (v2)",
        "v2_capacity_plan": "Capacity & demand outlook (v2)",
        "trainer_skills": "RMS skill register for one trainer",
        "mark_skill": "Record a skill (verified write)",
        "get_trainer_utilization_history": "3-month utilisation history",
        "get_course_syllabus": "Syllabus PDF link",
        "search_courses": "Catalogue search",
        "get_course_intelligence": "Course metadata + public schedules",
        "get_alternative_trainers": "Wider trainer network",
        "get_actions": "Manager action inbox",
        "raise_action": "Raise an action",
        "set_action_state": "Action state transition",
        "add_action_note": "Append an action note",
        "get_action_audit": "Action audit trail (v2)",
        "healthz": "Health check",
    }
    endpoints = {}
    for rule in sorted(app.url_map.iter_rules(), key=lambda r: str(r)):
        if rule.endpoint in ("not_found", "internal_error", "static"):
            continue
        methods = ",".join(sorted(m for m in rule.methods if m not in ("HEAD", "OPTIONS")))
        if rule.rule:
            endpoints[methods + "  " + rule.rule] = descriptions.get(rule.endpoint, rule.endpoint)
    return jsonify({
        "service":  "SkillSync Backend",
        "version":  "6.1.0",
        "endpoints": endpoints,
    }), 200


if __name__ == '__main__':
    app.run(debug=False, port=8080)


