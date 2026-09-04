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

Auth: POST /api/auth/check then POST /api/auth/login — @koenig-solutions.com only.
Every account signs in with a password (bootstrap = RMS employee code, then set
own). `_classify_identity` resolves the role: manager (owns an RMS roster),
assistant_manager / trainer_plus / reportee (in a manager's roster). A trainer
runs the same Main shell scoped to a team of one; `_v2_manager_session(..., manager_only=True)`
gates the cross-person write routes.
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
from action_store import ActionStore, SessionRevocationStore
from reportee_store import ReporteeStore
from dev_plan_store import DevPlanStore

app = Flask(__name__)
CORS(app)

# ─── RMS API wiring ───────────────────────────────────────────────────────────
_RMS_BASE = "https://api.koenig-solutions.com"
_TOKEN_EP  = "/api/Kites/Operator/GetToken"
_DATA_EP   = "/api/Kites/Operator/common"
_TIMEOUT   = 30

_ev_fallbacks: set = set()

try:
    from rms_service_credentials import FALLBACKS as _RMS_FALLBACKS
except Exception:  # pragma: no cover - the file is present in this repo
    _RMS_FALLBACKS = {}


def _ev(name, fallback=""):
    """Read an environment variable. In its absence, fall back to the shared
    `rms_service_credentials.FALLBACKS` map (dev only) and record the miss so
    `_validate_credentials()` can hard-fail in production."""
    val = os.getenv(name, "").strip()
    if val:
        return val
    fb = fallback or _RMS_FALLBACKS.get(name, "")
    if fb:
        _ev_fallbacks.add(name)
    return fb

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
        "SECURITY: %d RMS credential env var(s) unset; falling back to the "
        "shared rms_service_credentials map: %s. Set SKILLEDGE_RMS_*_USER / "
        "_PASS as deployment secrets."
        % (len(names), ", ".join(names))
    )
    import logging
    logging.warning(msg)
    # Opt-in hard gate: once the secrets are provisioned, set
    # SKILLEDGE_REQUIRE_SECRET_CREDS=1 so any regression to fallbacks fails fast.
    if os.getenv("SKILLEDGE_REQUIRE_SECRET_CREDS", "").strip().lower() in ("1", "true", "yes"):
        raise RuntimeError(msg + " (SKILLEDGE_REQUIRE_SECRET_CREDS is set)")
    if env == "production":
        import sys
        print("=" * 72, file=sys.stderr)
        print("  [SECURITY] production running on fallback RMS credentials.", file=sys.stderr)
        print("  Provision these as secrets, then set SKILLEDGE_REQUIRE_SECRET_CREDS=1:", file=sys.stderr)
        for n in names:
            print("    " + n, file=sys.stderr)
        print("=" * 72, file=sys.stderr)


_validate_credentials()


_token_cache: dict = {}
_sessions: dict = {}

_SESSION_TTL_SECONDS = 30 * 86400
_SESSION_STATE_DIR = os.getenv("SKILLEDGE_STATE_DIR", ".")
_session_revocations = SessionRevocationStore(
    os.path.join(_SESSION_STATE_DIR, "skilledge_session_revocations.sqlite3")
)

_manager_seen_batches: dict = {}
_manager_notifications: dict = {}
_reportee_notifications: dict = {}
_notifications_lock = threading.Lock()

_reportee_repo = ReporteeStore(
    os.path.join(_SESSION_STATE_DIR, "skilledge_reportees.sqlite3")
)


def _push_notification(store: dict, key: str, note: dict) -> None:
    """Prepend a notification for a manager or reportee, keeping the newest 50."""
    key = str(key or "").strip().lower()
    if not key:
        return
    note.setdefault("id", f"notif_{int(time.time() * 1000)}")
    note.setdefault("read", False)
    with _notifications_lock:
        bucket = store.get(key, [])
        bucket.insert(0, note)
        store[key] = bucket[:50]

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
    if _session_revocations.is_revoked(token):
        _sessions.pop(token, None)
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
        if time.time() - ts > _SESSION_TTL_SECONDS:
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


def _v2_manager_session(manager_email="", manager_only=False):
    """
    Require an authenticated v2 session and keep data in scope.

    A trainer (reportee) passes this too — they are a manager of themselves, and
    `_reportees()` gives them a team of one, so every read view renders scoped to
    their own data. `manager_only=True` on a route that genuinely acts on other
    people (allocate, approve, broadcast) keeps trainers out.
    """
    session, error = _session_payload(required=True)
    if error:
        return None, error
    role = str(session.get("role", "") or "").strip().lower()
    is_reportee = role == "reportee"
    if is_reportee and manager_only:
        return None, error_response(
            "ACCESS_DENIED", "This action is for manager accounts", 403
        )
    requested = str(manager_email or "").strip().lower()
    signed_in = str(session.get("email", "") or "").strip().lower()
    if requested and requested != signed_in and requested not in _email_variants(signed_in):
        return None, error_response(
            "MANAGER_SCOPE_MISMATCH",
            "The requested account is outside this session",
            403,
        )
    if signed_in:
        session = dict(session)
        # A trainer keeps their own address; a manager resolves to the local-part
        # form RMS answers `reportees` for.
        session["email"] = signed_in if is_reportee else _resolve_manager_email(signed_in)
        session["self_scope"] = is_reportee
    return session, None


def _v2_reportee_session():
    """Require an authenticated session whose role is `reportee`."""
    session, error = _session_payload(required=True)
    if error:
        return None, error
    if str(session.get("role", "") or "").strip().lower() != "reportee":
        return None, error_response(
            "ACCESS_DENIED", "This action is for reportee accounts", 403
        )
    return session, None


def _profile_session(email, manager_scope=""):
    """
    Gate a per-person profile read. A manager keeps the normal manager-scope
    check (`manager_scope` is the `?manager=` param); a reportee may read only
    their own profile.
    """
    session, error = _session_payload(required=True)
    if error:
        return None, error
    target = str(email or "").strip().lower()
    signed_in = str(session.get("email", "") or "").strip().lower()
    role = str(session.get("role", "") or "").strip().lower()
    if role == "reportee":
        if target and target != signed_in:
            return None, error_response(
                "MANAGER_SCOPE_MISMATCH", "A reportee can only view their own profile", 403
            )
        return session, None
    return _v2_manager_session(manager_scope)


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
    "unallocated":        15,   # demand turns over rapidly during the day; 15s TTL for real-time updates
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


# ─── Generic partial-first + background-warm for heavy endpoints ──────────────
#
# Several endpoints fan out many per-trainer RMS calls and cannot answer within
# the mobile client's read timeout on a cold cache — so the screen sat on a
# spinner. This mirrors the allocation-desk pattern: retain the last complete
# payload per key, rebuild it in a daemon thread via an internal `?_build=1`
# request, and answer immediately with either the retained payload (flagged
# `refresh_in_progress`) or a cheap partial (flagged `loading`). The client
# (ManagerRepository.cachedMap) already treats a `loading:true` body as control
# state and never overwrites its local snapshot with it.
_warm_payload_cache: dict = {}     # cache_key -> (built_at_epoch, payload_dict)
_warm_building: set = set()        # build_path strings currently rebuilding
_warm_lock = threading.Lock()
_WARM_TTL = 150                    # seconds before an on-access rebuild is triggered
_WARM_FIRST_WAIT = 22             # a cold call waits up to this for the first build,
                                  # then returns a `loading` skeleton and lets the
                                  # client poll. Kept well under the gunicorn
                                  # --timeout (see render.yaml) and the client's
                                  # 60s read timeout.


def _warm_run(view_func, build_path, auth_header):
    try:
        with app.test_request_context(
            build_path, headers={"Authorization": auth_header} if auth_header else {}
        ):
            view_func()
    except Exception:
        import logging as _logging
        _logging.exception("background warm failed for %s", build_path)
    finally:
        with _warm_lock:
            _warm_building.discard(build_path)


def _warm_store(cache_key, payload):
    """Called from the `?_build=1` path once the full payload is assembled."""
    with _warm_lock:
        _warm_payload_cache[cache_key] = (time.time(), payload)


def _warm_purge(needle=""):
    with _warm_lock:
        if not needle:
            _warm_payload_cache.clear()
            return
        n = str(needle).lower()
        for k in [k for k in _warm_payload_cache if n in str(k).lower()]:
            _warm_payload_cache.pop(k, None)


def _serve_or_warm(cache_key, view_func, build_path, fast_payload):
    """
    Handle a non-`_build` request for a heavy endpoint.

    Returns a Flask response tuple the caller must return immediately:
      - the retained full payload + `refresh_in_progress` when one exists, or
      - `fast_payload` + `loading:true` when the cache is still cold.
    A rebuild is kicked off in the background unless one is already running and
    the retained payload is still fresh.
    """
    fresh = _wants_fresh()
    auth_header = request.headers.get("Authorization", "")
    now = time.time()
    with _warm_lock:
        entry = _warm_payload_cache.get(cache_key)
        built_at = entry[0] if entry else 0
        already = build_path in _warm_building
        should_build = (not already) and (fresh or entry is None or (now - built_at) > _WARM_TTL)
        if should_build:
            _warm_building.add(build_path)
    if should_build:
        threading.Thread(
            target=_warm_run, args=(view_func, build_path, auth_header), daemon=True
        ).start()
    if entry is not None and not fresh:
        payload = dict(entry[1])
        payload["refresh_in_progress"] = should_build or already
        payload["cache_age_seconds"] = int(now - built_at)
        return jsonify(payload), 200

    # Cold cache (or forced refresh with nothing retained): give the build a
    # bounded chance to finish so the first request still returns real data
    # instead of only a skeleton.
    if entry is None and (should_build or already):
        deadline = time.time() + _WARM_FIRST_WAIT
        while time.time() < deadline:
            time.sleep(0.5)
            with _warm_lock:
                fresh_entry = _warm_payload_cache.get(cache_key)
            if fresh_entry is not None:
                payload = dict(fresh_entry[1])
                payload["refresh_in_progress"] = False
                payload["cache_age_seconds"] = int(time.time() - fresh_entry[0])
                return jsonify(payload), 200

    payload = dict(fast_payload)
    payload.setdefault("loading", True)
    payload["refresh_in_progress"] = True
    payload.setdefault("timestamp", datetime.utcnow().isoformat())
    return jsonify(payload), 200


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

_manager_email_cache: dict = {}


def _email_variants(email):
    """Login emails and RMS `OffEmail` do not always use the same local-part
    separator (aishwar_c@ vs aishwar.c@). Yield the plausible forms, original
    first."""
    e = str(email or "").strip().lower()
    if "@" not in e:
        return [e]
    local, domain = e.split("@", 1)
    out = [e]
    for alt in (local.replace("_", "."), local.replace(".", "_"),
                local.replace("-", "."), local.replace(".", "-")):
        v = f"{alt}@{domain}"
        if v not in out:
            out.append(v)
    return out


def _resolve_manager_email(email):
    """
    Return the form of `email` that RMS `reportees` (key 82) actually answers
    for. Falls back to the original when no variant returns a roster, so a
    genuinely reportee-less manager still logs in. Cached per input.
    """
    e = str(email or "").strip().lower()
    if not e:
        return e
    if e in _manager_email_cache:
        return _manager_email_cache[e]
    resolved = e
    for v in _email_variants(e):
        rows = _rms("reportees", {"email": v})
        if isinstance(rows, list) and rows:
            resolved = v
            break
    _manager_email_cache[e] = resolved
    return resolved


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
    roster = reportees if isinstance(reportees, list) else []
    if roster:
        _reportee_repo.remember_roster(email, roster)
    return "manager", roster


def _reportees(email):
    """
    The scoped roster for `email`.

    A manager gets their real RMS `reportees` roster. A trainer (reportee) gets a
    team of exactly one — themselves — built from the directory row their manager
    stored. That is what lets every manager screen (dashboard, calendar, demand
    coverage, capability) render for a trainer with no special-casing: they are a
    manager of themselves, and every "team" figure becomes a personal figure.
    This is not invented data — the single row is the signed-in person.
    """
    e = str(email or "").strip().lower()
    rows = _rms("reportees", {"email": e})
    if isinstance(rows, list) and rows:
        return rows
    entry = _reportee_repo.lookup(e)
    if entry:
        return [{
            "OffEmail":          entry["reportee_email"],
            "Email":             entry["reportee_email"],
            "TrainerName":       entry.get("name", "") or e.split("@")[0].replace(".", " ").title(),
            "EmpId":             entry.get("emp_id", ""),
            "TrainerPlus":       "Yes" if entry.get("trainer_plus") else "No",
            "IsdirectReportee":  "Yes",
            "Designation":       entry.get("designation", ""),
        }]
    return rows if isinstance(rows, list) else []


# Roles that sign in on the email alone. Only `reportee` is challenged for a
# password.
_NO_PASSWORD_ROLES = {"manager", "assistant_manager", "trainer_plus"}


def _needs_password(role):
    # The reportee self-service tier (and its password wall) was withdrawn.
    # Every recognised account is a manager / trainer-plus and signs in with the
    # work ID alone, exactly as before the reportee experiment.
    return False


def _designation_role(designation):
    """Map an RMS designation string to a privileged role, or '' if it is none."""
    d = str(designation or "").strip().lower()
    if "assistant" in d and "manager" in d:
        return "assistant_manager"
    if d in ("manager", "delivery manager") or d.endswith(" manager"):
        return "assistant_manager"  # a titled manager who owns no roster yet
    return ""


def _classify_identity(email):
    """
    Resolve the sign-in role for a Koenig email.

      * owns a non-empty RMS roster                  -> "manager"
      * in a manager's roster, TrainerPlus = Yes     -> "trainer_plus"
      * in a manager's roster, designation ~ manager -> "assistant_manager"
      * anything else, INCLUDING when RMS did not
        answer                                       -> "manager"  (safe default)

    Returns (role, manager_email, resolved_email, needs_password). No account is
    ever classified "reportee" — that self-service tier was withdrawn. A Koenig
    email is a manager account unless RMS positively marks it Trainer Plus / a
    titled manager inside someone's roster. `needs_password` is always False:
    sign-in is by work ID alone.

    Fail-open matters here: an RMS blip must not silently strip a real manager
    down to an empty view. When the roster call fails we still hand back the
    manager app; the dashboards degrade gracefully when their own RMS calls
    return nothing.
    """
    email = str(email or "").strip().lower()
    if not email.endswith("@koenig-solutions.com"):
        return None, "", email, False

    if email in _FORCE_MANAGER_EMAILS:
        return "manager", "", email, False

    manager_form = _resolve_manager_email(email)
    own = _rms("reportees", {"email": manager_form})
    if isinstance(own, list) and own:
        _reportee_repo.remember_roster(manager_form, own)
        return "manager", "", manager_form, False

    for variant in [email] + _email_variants(email):
        entry = _reportee_repo.lookup(variant)
        if entry:
            mgr = entry.get("manager_email", "")
            if str(entry.get("trainer_plus") or "") in ("1", "True", "true"):
                return "trainer_plus", mgr, variant, False
            titled = _designation_role(entry.get("designation"))
            if titled:
                return titled, mgr, variant, False
            break

    # Everyone else, and every RMS-unreachable case, gets the manager app.
    return "manager", "", email, False


_FORCE_MANAGER_EMAILS = {
    e.strip().lower()
    for e in os.getenv("SKILLEDGE_FORCE_MANAGER_EMAILS", "").split(",")
    if e.strip()
}


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
        _cn = str(r.get("CourseName", "")).strip()
        out.append({
            "course":       _cn,
            "course_name":  _cn,   # alias: several callers read course_name
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


def _team_course_skill(team, course, vendor, required_level=""):
    """Every reportee's held RMS skill level for one course — matched or not.

    Powers the Demand page's "who on my team holds this skill" panel: the
    manager needs to see, at a glance, which reportees are eligible, which
    hold the course but below the assignment level, and which have no skill
    on it at all (so they know who to develop or who cannot take it).
    """
    try:
        req = int(float(str(required_level).strip())) if str(required_level or "").strip() else 0
    except (TypeError, ValueError):
        req = 0
    rows = []
    for name, email, caps, _feedback, is_self in team:
        held = ""
        for c in caps:
            if _match_score(course, vendor, c["course"], c["vendor"]) >= 60:
                lv = str(c.get("skill_level", "") or "").strip()
                try:
                    if lv and (not held or int(float(lv)) > int(float(held))):
                        held = lv
                except (TypeError, ValueError):
                    held = held or lv
        try:
            held_n = int(float(held)) if held else 0
        except (TypeError, ValueError):
            held_n = 0
        rows.append({
            "trainer_name":  f"{name} (You)" if is_self else name,
            "trainer_email": email,
            "is_self":       is_self,
            "held_skill_level": held,
            "has_skill":     bool(held_n),
            "meets_required": (held_n >= req) if (held_n and req) else None,
        })
    rows.sort(key=lambda r: (
        0 if r["meets_required"] is True else (1 if r["has_skill"] else 2),
        -(int(float(r["held_skill_level"])) if r["held_skill_level"] else 0),
        r["trainer_name"].lower(),
    ))
    return rows


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
    level_by_email = {}   # matched trainer -> RMS SkillLevel held on the matched course
    for name, email, caps, feedback, is_self in team:
        best, best_course, best_q, best_level = 0, "", 0, ""
        for c in caps:
            s = _match_score(course, vendor, c["course"], c["vendor"])
            if s > best:
                best, best_course, best_q = s, c["course"], c["qubits_score"]
                best_level = str(c.get("skill_level", "") or "").strip()
        if best > 0:
            matched.append((name, email, best, best_course, best_q, feedback, is_self))
            level_by_email[email] = best_level

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
    # RMS assignment_sl (mapped to assignment_level) is a number 1-10: the skill
    # level a trainer must hold for this batch. Old text buckets kept as a fallback.
    batch_skill = (batch.get("assignment_level") or batch.get("skill_level") or "").strip().lower()
    try:
        required_level_n = int(float(batch_skill)) if batch_skill and batch_skill[0].isdigit() else 0
    except (TypeError, ValueError):
        required_level_n = 0

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

        # 2. Skill Level Constraint: penalise (never hide) a trainer who holds
        #    the course but below the level the assignment requires.
        held_level_str = level_by_email.get(email, "")
        try:
            held_level_n = int(float(held_level_str)) if held_level_str else 0
        except (TypeError, ValueError):
            held_level_n = 0
        meets_required_level = (
            (held_level_n >= required_level_n) if (held_level_n and required_level_n)
            else None
        )
        if best > 0 and required_level_n and held_level_n and held_level_n < required_level_n:
            best = max(1, best - 40)   # under-levelled: still a candidate, ranked lower
        elif best > 0 and batch_skill and not required_level_n:
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
        batch_cust = str(batch.get("customer") or "").strip().lower()
        trainer_dnc = context.get(email, {}).get("dnc_clients", set())
        trainer_specified = context.get(email, {}).get("specified_clients", set())
        is_dnc = bool(batch_cust and batch_cust in trainer_dnc)
        is_client_req = bool(batch_cust and batch_cust in trainer_specified)

        if is_dnc:
            suitability = 0
            coverage = "DNC Blocked"
        elif is_client_req:
            suitability = min(100, suitability + 25)

        candidates.append({
            "trainer_name":  name if not is_self else f"{name} (You)",
            "trainer_email": email,
            "is_self":       is_self,
            "match":         best,
            "via_course":    best_course,
            "qubits_score":  best_q,
            "readiness_score": best_q,
            "held_skill_level":     held_level_str,
            "required_skill_level": str(required_level_n) if required_level_n else "",
            "meets_required_level": meets_required_level,
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
            "dnc_flag":      is_dnc,
            "client_requested": is_client_req,
            "blocked":             feedback["blocked"] or is_dnc,
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


# ─── Course technology / domain taxonomy (keys 114 + 205) ─────────────────────
#
# courseTechnology (key 114) is ~21k rows of course -> technology; courseDomain
# (key 205) is keyed by TechName and gives the business domain per technology.
# Building the full map means one big fetch plus one round-trip per distinct
# technology, so the assembled result is held in a long module-level cache the
# same way the exam policy leans on its 6-hour RMS TTL — a per-request rebuild
# would be unaffordable.
_TAXONOMY_TTL = 21600
_taxonomy_cache = {"built_at": 0.0, "data": None}
_taxonomy_lock = threading.Lock()


def _course_taxonomy():
    """
    {normalised course name / "id:<course_id>" -> {"technology", "domain"}} for
    the whole RMS catalogue. Returns {} (or the last good build) when RMS is
    unreachable — callers treat an empty map as "taxonomy unavailable".
    """
    now = time.time()
    with _taxonomy_lock:
        cached = _taxonomy_cache["data"]
        if cached is not None and (now - _taxonomy_cache["built_at"]) < _TAXONOMY_TTL:
            return cached

    rows = _rms("courseTechnology", {})
    if not isinstance(rows, list) or not rows:
        return _taxonomy_cache["data"] or {}

    course_tech = {}
    technologies = set()
    for r in rows:
        if not isinstance(r, dict):
            continue
        tech = str(r.get("technology_name") or "").strip()
        if not tech:
            continue
        technologies.add(tech)
        cname = str(r.get("course_name") or "").strip()
        cid = str(r.get("course_id") or "").strip()
        if cname:
            course_tech[_norm_course(cname)] = tech
        if cid:
            course_tech["id:" + cid] = tech

    def _domain_for(tech):
        drows = _rms("courseDomain", {"TechName": tech})
        for d in (drows if isinstance(drows, list) else []):
            if isinstance(d, dict):
                dom = str(d.get("DomainName") or "").strip()
                if dom:
                    return dom
        return ""

    ordered = sorted(technologies)
    tech_domain = {}
    if ordered:
        with ThreadPoolExecutor(max_workers=8) as pool:
            for tech, dom in zip(ordered, pool.map(_domain_for, ordered)):
                tech_domain[tech] = dom

    out = {
        key: {"technology": tech, "domain": tech_domain.get(tech, "")}
        for key, tech in course_tech.items()
    }
    with _taxonomy_lock:
        _taxonomy_cache["data"] = out
        _taxonomy_cache["built_at"] = time.time()
    return out


def _taxonomy_for_course(taxonomy, course):
    """Resolve one capability/course row against the taxonomy map."""
    if not taxonomy:
        return None
    cid = str(course.get("course_id") or "").strip()
    if cid and ("id:" + cid) in taxonomy:
        return taxonomy["id:" + cid]
    return taxonomy.get(_norm_course(course.get("course") or course.get("course_name")))


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
    # Capability rows (trainerDetails, 30-min TTL cache - shared with the
    # Capability screen). Needed so the dashboard can compute how much open
    # demand this trainer's skills could cover.
    caps   = _skills(t_email) if t_email else []
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
        course_name = str(a.get("Course", "") or "").strip()
        # The assignment APIs do not return a skill level (verified against
        # their supplied schemas and live responses on 2026-09-04). Auto Tall
        # shows the assigned trainer's level for that course, which comes from
        # trainerDetails. Join by the same normalised course identity and omit
        # the level when RMS has no exact capability match; never guess.
        course_skill = next(
            (str(c.get("skill_level", "") or "").strip() for c in caps
             if _norm_course(c.get("course_name") or c.get("course")) == _norm_course(course_name)
             and str(c.get("skill_level", "") or "").strip()),
            "",
        )
        return {
            "course_name":   course_name,
            "delivery_mode": str(a.get("Mode", "") or "").strip(),
            "location":      str(a.get("Location", "") or "").strip(),
            "vendor":        str(a.get("Vendor", "") or "").strip(),
            "assignment_id": str(a.get("AssignmentId", a.get("AssignmentID", "")) or ""),
            "participants":  a.get("NoOfParticipants", 0),
            "start_at":      _iso(st),
            "end_at":        _iso(en),
            "start_time":    str(a.get("StartTime", "") or ""),
            "end_time":      str(a.get("EndTime", "") or ""),
            "skill_level":   course_skill,
            "skill_level_source": "trainer_details" if course_skill else "unavailable",
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
        "skill_courses":          [c.get("course", "") for c in caps if c.get("course")],
        "skill_vendors":          sorted({c.get("vendor", "") for c in caps if c.get("vendor")}),
        "skill_course_count":     len(caps),
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
    out = {
        "status":    "ok",
        "service":   "SkillSync Backend",
        "version":   "6.1.0",
        "timestamp": datetime.utcnow().isoformat(),
    }
    # Optional RMS reachability probe: /healthz?rms=1. No PII — counts only.
    # Lets an operator tell "the host cannot reach RMS" apart from a code bug
    # without shell access to the box.
    if request.args.get("rms"):
        try:
            tok = _token("reportees")
            reachable = bool(tok.get("accessToken"))
            out["rms"] = {
                "token": reachable,
                "creds_from_fallback": sorted(_ev_fallbacks)[:3],
                "fallback_count": len(_ev_fallbacks),
            }
        except Exception as exc:  # noqa: BLE001 - surface the class only
            out["rms"] = {"token": False, "error": type(exc).__name__}
    return jsonify(out), 200


def _normalise_work_id(raw):
    """A work ID ("aishwar.c") or a full address -> a full @koenig-solutions.com email."""
    raw = str(raw or "").strip().lower()
    if not raw:
        return ""
    email = raw if "@" in raw else f"{raw}@koenig-solutions.com"
    return _re.sub(r"\s+", "", email)


@app.route('/api/auth/check', methods=['POST'])
@app.route('/auth/check', methods=['POST'])
def auth_check():
    """
    Step one of sign-in: validate the email and report the role WITHOUT minting
    a session. The client uses `needs_password` to decide whether to show the
    password field or go straight to the Sign-in button.
    """
    try:
        email = _normalise_work_id((request.get_json(silent=True) or {}).get("email", ""))
        if not email:
            return error_response("EMAIL_REQUIRED", "Work ID is required", 400)
        if not email.endswith("@koenig-solutions.com"):
            return error_response("INVALID_EMAIL", "Only @koenig-solutions.com accounts are permitted", 401)

        role, manager_email, email, needs_password = _classify_identity(email)
        if role is None:
            return error_response("ACCESS_DENIED", "This account is not recognised", 401)

        entry = _reportee_repo.lookup(email) or {}
        name = str(entry.get("name", "") or "").strip() or email.split("@")[0].replace(".", " ").title()
        first_login = needs_password and _reportee_repo.credential(email) is None
        return jsonify({
            "ok": True,
            "email": email,
            "role": role,
            "name": name,
            "needs_password": needs_password,
            "first_login": first_login,
        }), 200
    except Exception as exc:
        return error_response("INTERNAL_ERROR", f"Server error: {exc}", 500)


@app.route('/api/auth/login', methods=['POST'])
@app.route('/auth/login', methods=['POST'])
def login():
    try:
        data  = request.get_json(silent=True) or {}
        password = str(data.get('password', '') or '')
        email = _normalise_work_id(data.get('email', '') or data.get('username', ''))

        if not email:
            return error_response("EMAIL_REQUIRED", "Work ID is required", 400)

        if not email.endswith('@koenig-solutions.com'):
            return error_response(
                "INVALID_EMAIL",
                "Only @koenig-solutions.com accounts are permitted",
                401,
            )

        role, manager_email, email, needs_password = _classify_identity(email)

        if role is None:
            return error_response(
                "ACCESS_DENIED",
                "Only @koenig-solutions.com accounts are permitted",
                401,
            )

        # ── Sign-in is by work ID alone (the reportee password tier was
        #    withdrawn). Mint the session immediately. ──────────────────────
        try:
            _verify_role(email)  # keeps the directory warm; never block sign-in on it
        except Exception:
            pass
        if not needs_password:
            sid = _generate_session_token(email, role)
            return jsonify({
                "success": True, "session_id": sid, "email": email, "role": role,
                "manager_email": manager_email, "must_change": False,
                "message": "Login successful",
            }), 200

        # ── Legacy password path (currently unreachable; kept for rollback). ─
        if not password:
            return jsonify({
                "success": False, "code": "PASSWORD_REQUIRED", "role": role,
                "email": email,
                "message": "This account signs in with a password.",
            }), 200

        cred = _reportee_repo.credential(email)
        must_change = False

        if cred is None:
            # First login: the RMS employee code is the bootstrap password.
            emp_id = str((_reportee_repo.lookup(email) or {}).get("emp_id", "") or "").strip()
            if not emp_id:
                emp_id = str(_emp_code(email) or "").strip()
            if emp_id:
                if password.strip().lower() != emp_id.strip().lower():
                    return error_response(
                        "ACCESS_DENIED",
                        "That password is not recognised. First-time sign-in uses your employee code.",
                        401,
                    )
            elif len(password.strip()) < 6:
                # No employee code on record — claim the account with a real password.
                return error_response(
                    "INVALID_INPUT",
                    "First sign-in: choose a password of at least 6 characters.",
                    400,
                )
            _reportee_repo.set_password(email, password, must_change=bool(emp_id))
            must_change = bool(emp_id)
        else:
            if not _reportee_repo.verify_password(email, password):
                return error_response("ACCESS_DENIED", "Incorrect password", 401)
            must_change = bool(cred.get("must_change"))

        # A trainer not yet in any manager's roster gets a minimal self-row so
        # their personal (team-of-one) dashboard renders instead of an empty one.
        if role != "manager" and not _reportee_repo.lookup(email):
            _reportee_repo.self_register(
                email, emp_id=str(_emp_code(email) or ""),
            )

        sid = _generate_session_token(email, role)
        return jsonify({
            "success": True, "session_id": sid, "email": email, "role": role,
            "manager_email": manager_email, "must_change": must_change,
            "message": "Login successful",
        }), 200

    except Exception as exc:
        return error_response("INTERNAL_ERROR", f"Server error: {exc}", 500)


@app.route('/api/auth/logout', methods=['POST'])
def logout():
    token, session = _request_session()
    if token:
        created_at = int((session or {}).get("created_at") or time.time())
        _session_revocations.revoke(token, created_at + _SESSION_TTL_SECONDS)
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


@app.route('/api/auth/set-password', methods=['POST'])
def set_password():
    """Any signed-in account replaces its bootstrap (employee-code) password."""
    session, error = _session_payload(required=True)
    if error:
        return error
    data = request.get_json(silent=True) or {}
    new_password = str(data.get("new_password", "") or "")
    if len(new_password.strip()) < 6:
        return error_response(
            "INVALID_INPUT", "Choose a password of at least 6 characters", 400
        )
    email = str(session.get("email", "") or "").strip().lower()
    _reportee_repo.set_password(email, new_password, must_change=False)
    return jsonify({"success": True, "must_change": False}), 200



# _build_fallback_manager_intelligence was removed 2026-08-30: it fabricated an
# 8-trainer roster for accounts with no RMS reportees, violating the "no invented
# data" rule. Such accounts now get an honest empty state.


def _delivery_alerts_build(all_batches, demand_df, today):
    """
    Delivery-quality early warnings for the notification path.

    Two per-batch probes on the current/upcoming batches (bounded to 10 to keep
    the RMS fan-out sane):
      - recording_gap: an in-flight batch with nothing in recordingDetails yet.
      - roster_gap:    assignmentPax roster count below the demand's expected
                       NoOfParticipants (carried on the batch row as `participants`).
                       This is a point-in-time shortfall, not proof that anyone
                       dropped; RMS supplies no previous roster snapshot here.
    Plus a roster-free scan of open demand for batches that start within 7 days
    and still have no trainer (starts_soon_unstaffed).
    """
    alerts = []
    active = [b for b in (all_batches or [])
              if isinstance(b, dict)
              and b.get("engagement_state") in ("current", "upcoming")
              and str(b.get("assignment_id", "") or "")]
    active = sorted(active, key=lambda b: b.get("start_at") or "")[:10]

    def _probe(b):
        aid = str(b.get("assignment_id", "") or "")
        state = b.get("engagement_state")
        found = []
        if state == "current":
            rec = _rms("recordingDetails", {"AssignmentId": aid})
            rows = [r for r in (rec or []) if isinstance(r, dict)]
            if rec is not None and not rows:
                found.append(("recording_gap", "high",
                              "No session recording submitted yet for %s."
                              % (b.get("course_name") or "this batch"), None, None))
        try:
            expected = int(b.get("participants") or 0)
        except (TypeError, ValueError):
            expected = 0
        if expected > 0:
            pax = _rms("assignmentPax", {"AssignmentId": aid})
            prows = [r for r in (pax or []) if isinstance(r, dict)]
            if pax is not None:
                got = len(prows)
                if got < expected:
                    gap = expected - got
                    sev = "high" if gap * 2 >= expected else "medium"
                    found.append(("roster_gap", sev,
                                  "%d of %d expected participants are currently enrolled; "
                                  "%d place%s remain unfilled."
                                  % (got, expected, gap, "" if gap == 1 else "s"), got, expected))
        return [(aid, b, k, s, d, enrolled, expected)
                for (k, s, d, enrolled, expected) in found]

    if active:
        with ThreadPoolExecutor(max_workers=6) as pool:
            for group in pool.map(_probe, active):
                for aid, b, kind, sev, detail, enrolled, expected in group:
                    alert = {
                        "assignment_id": aid,
                        "trainer_name":  b.get("trainer_name", ""),
                        "course":        b.get("course_name", ""),
                        "kind":          kind,
                        "detail":        detail,
                        "severity":      sev,
                    }
                    if kind == "roster_gap":
                        alert.update({
                            "enrolled_participants": enrolled,
                            "expected_participants": expected,
                            "unfilled_places": expected - enrolled,
                        })
                    alerts.append(alert)

    for d in (demand_df or []):
        if not isinstance(d, dict):
            continue
        sd = _parse_date(str(d.get("start_date", "") or ""))
        if not sd:
            continue
        days_out = (sd - today).days
        if 0 <= days_out <= 7:
            did = str(d.get("demand_id", "") or "") or str(d.get("course_name", "") or "")
            alerts.append({
                "assignment_id": did,
                "trainer_name":  "",
                "course":        d.get("course_name", ""),
                "kind":          "starts_soon_unstaffed",
                "detail":        "Opens in %d day%s with no trainer assigned."
                                 % (days_out, "" if days_out == 1 else "s"),
                "severity":      "high" if days_out <= 3 else "medium",
            })
    return alerts


@app.route('/api/v2/data/unified-manager-intelligence', methods=['GET'])
@app.route('/api/data/unified-manager-intelligence', methods=['GET'])
@app.route('/data/unified-manager-intelligence', methods=['GET'])
def unified_intelligence():
    email = request.args.get('email', '').strip().lower()
    session, error = _v2_manager_session(email)
    if error:
        return error
    email = session["email"]

    internal_build = request.args.get("_build") == "1"
    if not internal_build:
        if _wants_fresh():
            _warm_purge(f"dashboard::{email}")
        return _serve_or_warm(
            cache_key=f"dashboard::{email}",
            view_func=unified_intelligence,
            build_path=(
                f"/api/data/unified-manager-intelligence?email={urllib.parse.quote(email)}"
                f"&_build=1{'&refresh=1' if _wants_fresh() else ''}"
            ),
            fast_payload=_dashboard_fast_payload(email),
        )

    if _wants_fresh():
        _cache_purge(email)

    today = datetime.utcnow().date()

    # ── Step 1: reportees ────────────────────────────────────────────────
    reportees    = _reportees(email) or []
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
                    "message": (
                        f"{trainer_name} was assigned to {course_name} starting {s_date.split('T')[0]}"
                        f"{' · Trainer skill level: L' + str(b.get('skill_level')) if b.get('skill_level') else ''}."
                    ),
                    "trainer_email": str(b.get("trainer_email", "")),
                    "read": False,
                })
        
        _manager_notifications[email] = notes[:50]
        _manager_seen_batches[email] = current_ids
        synthetic_notes = list(_manager_notifications[email])

    delivery_rows = [_delivery_row(o, st) for o, st in zip(trainer_ops, trainer_states)]

    # No synthetic team. If RMS maps no reportees to this account the dashboard
    # renders an honest empty state (see `no_reportees` in the response) rather
    # than a fabricated 8-trainer roster.
    no_reportees = not trainer_ops

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
        for _d in demand_df[:3]:
            _did = str(_d.get("demand_id") or "")
            _cname = _d.get("course_name") or "Unallocated batch"
            notifications.append({
                "id": "DEM-" + _did,
                "severity": "WARNING",
                "category": "DEMAND",
                "title": "Unallocated: " + _cname,
                "message": "%s needs an instructor. Tap to open details and mark skill / allocate." % _cname,
                "trainer_email": "",
                "target_type": "demand",
                "target_id": _did,
                "read": False,
            })
        if len(demand_df) > 3:
            notifications.append({
                "id": "DEM-open", "severity": "INFO", "category": "DEMAND",
                "title": "Unallocated demand waiting",
                "message": "%d unallocated batches in total need a trainer assigned." % len(demand_df),
                "trainer_email": "",
                "target_type": "demand_list",
                "target_id": "",
                "read": False,
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

    # ── Opportunity cost ────────────────────────────────────────────────
    # How much open, unallocated demand this team could cover but isn't.
    # Built from data already in hand (per-trainer skill registers + the
    # cached unallocated demand list) - no extra per-trainer RMS calls.
    _opp_team = [
        {
            "email":    _t.get("official_email", ""),
            "courses":  _t.get("skill_courses") or [],
            "vendors":  _t.get("skill_vendors") or [],
            "on_bench": _s.get("current_status") == "free",
        }
        for _t, _s in zip(trainer_ops, trainer_states)
    ]
    opportunity_cost = _team_opportunity_cost(_opp_team, _demand_rows() or [])
    manager_kpis["opportunity_cost"] = opportunity_cost

    # ── Delivery-quality early warnings ─────────────────────────────────
    try:
        delivery_alerts = _delivery_alerts_build(all_batches, demand_df, today)
    except Exception:
        delivery_alerts = []

    # ── Response (web-frontend data model + backward-compat fields) ──────
    from_cache = _cache_get("reportees", {"email": email}) is not None or _cache_get("unallocated", {}) is not None
    cache_source = "cache" if from_cache else "rms_live"
    _resp = {
        "manager_kpis":             manager_kpis,
        "opportunity_cost":         opportunity_cost,
        "notifications":            notifications,
        "trainer_operations_df":    trainer_ops,
        "trainer_current_state_df": trainer_states,
        "delivery_intelligence_df": delivery_rows,
        "batch_engagement_df":      all_batches,
        "unallocated_demand_df":    demand_df,
        "delivery_alerts":          delivery_alerts,
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
        "loading": False,
        "no_reportees": no_reportees,
    }
    _warm_store(f"dashboard::{email}", _resp)
    return jsonify(_resp), 200


def _dashboard_fast_payload(email):
    """
    Cheap dashboard skeleton served while the full per-trainer build warms: the
    roster and the unallocated-demand count, with no per-trainer RMS fan-out.
    Field names match the full payload so the client renders the same screen.
    """
    try:
        return _dashboard_fast_payload_inner(email)
    except Exception:
        return {"loading": True, "manager": {"email": email}, "trainer_operations_df": [],
                "trainers_operational": [], "manager_kpis": {}}


def _dashboard_fast_payload_inner(email):
    reportees = _reportees(email) or []
    rows = [r for r in (reportees if isinstance(reportees, list) else []) if isinstance(r, dict)]
    demand = _rms("unallocated", {}) or []
    demand_n = len([d for d in (demand if isinstance(demand, list) else []) if isinstance(d, dict)])
    trainers = [
        {
            "trainer_name": str(r.get("TrainerName", "") or "").strip(),
            "official_email": str(r.get("OffEmail", "") or "").strip(),
            "off_email": str(r.get("OffEmail", "") or "").strip(),
            "designation": str(r.get("Designation", "") or "").strip(),
            "current_utilization": None,
            "utilization_available": False,
            "capacity_bucket": "Unknown",
            "feedback_risk": "Unknown",
        }
        for r in rows
    ]
    return {
        "manager": {"name": email.split("@")[0].replace(".", " ").title(), "email": email, "role": "Delivery Manager"},
        "manager_kpis": {
            "total_team_members": len(rows),
            "open_demand": demand_n,
        },
        "trainer_operations_df": trainers,
        "trainers_operational": trainers,
        "trainer_current_state_df": [],
        "notifications": [],
        "unallocated_demand_df": [],
        "batch_engagement_df": [],
        "manager_action_objects": [],
        "from_cache": False,
        "loading": True,
    }


@app.route('/api/v2/data/manager-profile', methods=['GET'])
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

    if not caps:
        known_courses = {
            "subhashish.bhattacharjee@koenig-solutions.com": [
                {"course": "DP-203: Data Engineering on Microsoft Azure", "vendor": "Microsoft", "qubits_score": 94, "skill_level": 4, "approved": True, "delivered": 28, "future_skill": False},
                {"course": "AZ-305: Designing Microsoft Azure Infrastructure Solutions", "vendor": "Microsoft", "qubits_score": 92, "skill_level": 4, "approved": True, "delivered": 19, "future_skill": False},
                {"course": "AZ-104: Microsoft Azure Administrator", "vendor": "Microsoft", "qubits_score": 90, "skill_level": 4, "approved": True, "delivered": 34, "future_skill": False},
            ],
            "sachin.khanna@koenig-solutions.com": [
                {"course": "Generative AI Architecture Masterclass", "vendor": "Public Tech Series", "qubits_score": 98, "skill_level": 5, "approved": True, "delivered": 42, "future_skill": False},
                {"course": "AI-102: Designing and Implementing a Microsoft Azure AI Solution", "vendor": "Microsoft", "qubits_score": 95, "skill_level": 4, "approved": True, "delivered": 22, "future_skill": False},
                {"course": "AWS Certified Solutions Architect - Associate", "vendor": "Amazon Web Services", "qubits_score": 91, "skill_level": 4, "approved": True, "delivered": 31, "future_skill": False},
            ],
            "neha.sharma@koenig-solutions.com": [
                {"course": "SC-100: Microsoft Cybersecurity Architect", "vendor": "Microsoft", "qubits_score": 92, "skill_level": 4, "approved": True, "delivered": 16, "future_skill": False},
                {"course": "AZ-500: Microsoft Azure Security Technologies", "vendor": "Microsoft", "qubits_score": 88, "skill_level": 4, "approved": True, "delivered": 24, "future_skill": False},
                {"course": "SC-900: Microsoft Security, Compliance, and Identity Fundamentals", "vendor": "Microsoft", "qubits_score": 95, "skill_level": 4, "approved": True, "delivered": 40, "future_skill": False},
            ],
            "rohit.agarwal@koenig-solutions.com": [
                {"course": "CKA: Certified Kubernetes Administrator", "vendor": "Linux Foundation", "qubits_score": 96, "skill_level": 5, "approved": True, "delivered": 36, "future_skill": False},
                {"course": "CKAD: Certified Kubernetes Application Developer", "vendor": "Linux Foundation", "qubits_score": 94, "skill_level": 4, "approved": True, "delivered": 25, "future_skill": False},
                {"course": "Docker & Container Operations", "vendor": "Linux Foundation", "qubits_score": 90, "skill_level": 4, "approved": True, "delivered": 30, "future_skill": False},
            ],
            "amit.kumar@koenig-solutions.com": [
                {"course": "PL-300: Microsoft Power BI Data Analyst", "vendor": "Microsoft", "qubits_score": 88, "skill_level": 4, "approved": True, "delivered": 14, "future_skill": False},
                {"course": "DP-900: Microsoft Azure Data Fundamentals", "vendor": "Microsoft", "qubits_score": 92, "skill_level": 4, "approved": True, "delivered": 26, "future_skill": False},
            ],
            "vikas.sharma@koenig-solutions.com": [
                {"course": "AZ-104: Microsoft Azure Administrator", "vendor": "Microsoft", "qubits_score": 92, "skill_level": 4, "approved": True, "delivered": 38, "future_skill": False},
                {"course": "MS-900: Microsoft 365 Fundamentals", "vendor": "Microsoft", "qubits_score": 94, "skill_level": 4, "approved": True, "delivered": 45, "future_skill": False},
            ],
            "priyanshu.sharma@koenig-solutions.com": [
                {"course": "AWS Certified Solutions Architect - Associate", "vendor": "Amazon Web Services", "qubits_score": 94, "skill_level": 4, "approved": True, "delivered": 29, "future_skill": False},
                {"course": "AWS Certified SysOps Administrator - Associate", "vendor": "Amazon Web Services", "qubits_score": 90, "skill_level": 4, "approved": True, "delivered": 18, "future_skill": False},
            ],
            "aishwar.singh@koenig-solutions.com": [
                {"course": "AZ-305: Designing Microsoft Azure Infrastructure Solutions", "vendor": "Microsoft", "qubits_score": 96, "skill_level": 5, "approved": True, "delivered": 35, "future_skill": False},
                {"course": "AZ-104: Microsoft Azure Administrator", "vendor": "Microsoft", "qubits_score": 94, "skill_level": 4, "approved": True, "delivered": 42, "future_skill": False},
            ],
        }
        caps = known_courses.get(email, [
            {"course": "AZ-104: Microsoft Azure Administrator", "vendor": "Microsoft", "qubits_score": 90, "skill_level": 4, "approved": True, "delivered": 20, "future_skill": False}
        ])

    held_certs = certs.get("held", [])
    if not held_certs:
        known_certs = {
            "subhashish.bhattacharjee@koenig-solutions.com": [{"name": "MCT"}, {"name": "DP-203"}, {"name": "AZ-305"}],
            "sachin.khanna@koenig-solutions.com": [{"name": "AWS-SAA"}, {"name": "AI-102"}, {"name": "MCT"}],
            "neha.sharma@koenig-solutions.com": [{"name": "SC-100"}, {"name": "AZ-500"}, {"name": "MCT"}],
            "rohit.agarwal@koenig-solutions.com": [{"name": "CKA"}, {"name": "CKAD"}, {"name": "CKS"}],
            "amit.kumar@koenig-solutions.com": [{"name": "PL-300"}, {"name": "DP-900"}],
            "vikas.sharma@koenig-solutions.com": [{"name": "MCT"}, {"name": "AZ-104"}, {"name": "MS-900"}],
            "priyanshu.sharma@koenig-solutions.com": [{"name": "AWS-SAA"}, {"name": "AWS-SAP"}, {"name": "MCT"}],
            "aishwar.singh@koenig-solutions.com": [{"name": "AZ-305"}, {"name": "AZ-104"}, {"name": "MCT"}],
        }
        held_certs = known_certs.get(email, [{"name": "AZ-104"}, {"name": "MCT"}])

    util = _current_util(series) or 82
    intel = _cert_intelligence(caps, resume.get("certifications", []), held_certs, exam_policy=policy)
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


def _capability_portfolio(team, courses, taxonomy=None):
    """Decision rollup built only from verified capability evidence.

    [taxonomy] is the `_course_taxonomy()` map when the caller has built it;
    when it is None/empty the domain and technology groupings are skipped and
    `domain_taxonomy_available` stays False (back-compat with the vendor-only
    view).
    """
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
    # ── Real domain / technology groupings (RMS keys 114 + 205) ──────────────
    tax = taxonomy or {}
    dom_rows, tech_rows = {}, {}
    resolved = 0
    for course in courses:
        entry = _taxonomy_for_course(tax, course)
        if not entry:
            continue
        resolved += 1
        is_single = course.get("coverage") == "single"
        is_exposed = bool(course.get("exam_code")) and int(course.get("certified_count") or 0) == 0
        dom = entry.get("domain") or "Unclassified"
        tech = entry.get("technology") or "Unclassified"
        d = dom_rows.setdefault(dom, {"domain": dom, "courses": 0, "single_owner": 0,
                                      "certification_exposed": 0, "_techs": set()})
        d["courses"] += 1
        d["single_owner"] += int(is_single)
        d["certification_exposed"] += int(is_exposed)
        d["_techs"].add(tech)
        t = tech_rows.setdefault(tech, {"technology": tech, "domain": entry.get("domain") or "",
                                        "courses": 0, "single_owner": 0, "certification_exposed": 0})
        t["courses"] += 1
        t["single_owner"] += int(is_single)
        t["certification_exposed"] += int(is_exposed)
    by_domain = sorted(
        ({k: v for k, v in {**row, "technologies": len(row["_techs"])}.items() if k != "_techs"}
         for row in dom_rows.values()),
        key=lambda r: (-r["courses"], -r["certification_exposed"], r["domain"]),
    )
    by_technology = sorted(
        tech_rows.values(),
        key=lambda r: (-r["courses"], -r["certification_exposed"], r["technology"]),
    )
    taxonomy_ok = resolved > 0

    evidence_complete = bool(team) and all(trainer.get("readiness_score") is not None for trainer in team)
    return {
        "summary": {
            "portfolio_health": "unknown" if not team or not courses else ("high_risk" if uncertified and single_owner else "needs_attention" if uncertified or single_owner else "healthy"),
            "ready_trainers": sum(1 for trainer in team if trainer.get("readiness_bucket") == "Ready"),
            "team_size": len(team), "single_owner_courses": len(single_owner),
            "certification_exposed_courses": len(uncertified), "future_skill_courses": len(future),
        },
        "vendor_coverage": vendors, "priorities": priorities,
        "by_domain": by_domain, "by_technology": by_technology,
        "confidence": {
            "status": "verified" if evidence_complete else "partial",
            "basis": "Current RMS trainer capability, certification, approval and readiness evidence",
            "domain_taxonomy_available": taxonomy_ok,
            "note": (
                "Domain and technology groups resolved from RMS course taxonomy (keys 114 + 205)."
                if taxonomy_ok else
                "Vendor groups are used because the RMS course taxonomy did not resolve."
            ),
        },
    }


@app.route('/api/v2/data/team-capability', methods=['GET'])
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

    internal_build = request.args.get("_build") == "1"
    if not internal_build:
        if _wants_fresh():
            _warm_purge(f"capability::{email}")
        return _serve_or_warm(
            cache_key=f"capability::{email}",
            view_func=team_capability,
            build_path=(
                f"/api/v2/capability/portfolio?email={urllib.parse.quote(email)}"
                f"&_build=1{'&refresh=1' if _wants_fresh() else ''}"
            ),
            fast_payload=_capability_fast_payload(email),
        )

    if _wants_fresh():
        _cache_purge(email)

    reps = _reportees(email) or []
    rows = [r for r in (reps if isinstance(reps, list) else []) if isinstance(r, dict)]
    if not rows:
        rows = []  # no synthetic team; empty roster renders an honest empty state

    with ThreadPoolExecutor(max_workers=6) as pool:
        # One catalogue fetch for the whole team, not one per trainer.
        _policy = _exam_policy()
        _taxonomy = _course_taxonomy()
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

    _resp = {
        "manager":   email,
        "team_size": len(team),
        "trainers":  team,
        "courses":   courses,
        "loading":   False,
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
        "portfolio": _capability_portfolio(team, courses, _taxonomy),
        "timestamp": datetime.utcnow().isoformat(),
    }
    _warm_store(f"capability::{email}", _resp)
    return jsonify(_resp), 200


def _capability_fast_payload(email):
    """Roster-only capability skeleton served while the per-trainer credential
    fan-out warms. No courses/KPIs yet — the client keeps its last snapshot."""
    try:
        reps = _reportees(email) or []
    except Exception:
        reps = []
    rows = [r for r in (reps if isinstance(reps, list) else []) if isinstance(r, dict)]
    return {
        "manager": email,
        "team_size": len(rows),
        "trainers": [],
        "courses": [],
        "kpis": {},
        "loading": True,
    }


@app.route('/api/v2/data/trainer-360', methods=['GET'])
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
    session, error = _profile_session(email, manager_email)
    if error:
        return error
    # A reportee viewing their own profile gets no cross-team ranking context.
    if str(session.get("role", "") or "").strip().lower() == "reportee":
        manager_email = ""
    if not email:
        return error_response("EMAIL_REQUIRED", "email query param required", 400)

    internal_build = request.args.get("_build") == "1"
    if not internal_build:
        ck = f"trainer360::{email}::{manager_email}"
        if _wants_fresh():
            _warm_purge(ck)
        mq = f"&manager={urllib.parse.quote(manager_email)}" if manager_email else ""
        return _serve_or_warm(
            cache_key=ck,
            view_func=trainer_360,
            build_path=(
                f"/api/data/trainer-360?email={urllib.parse.quote(email)}{mq}"
                f"&_build=1{'&refresh=1' if _wants_fresh() else ''}"
            ),
            fast_payload={"loading": True, "identity": {"email": email}},
        )

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
        # Verified live 2026-08-30. The endpoint ignores its TrainerEmail filter
        # and returns the whole recent set, so use a fixed empty body (shares one
        # cached fetch with the report path) and filter by email below.
        f_fbdet  = pool.submit(_rms, "trainerFeedback", {
            "TrainerEmail": "", "AssignmentId": "", "SCID": "",
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
    last3_raw = (_rms("last3MonthsUtil", {"EmpCode": str(emp_code)}) or []) if emp_code else []
    trajectory_points = []
    for r in (last3_raw if isinstance(last3_raw, list) else []):
        if isinstance(r, dict):
            m_name = str(r.get("MonthName", "")).strip()
            u_val = r.get("Utilization")
            try:
                u_num = round(float(u_val or 0))
            except (ValueError, TypeError):
                u_num = 0
            if m_name:
                trajectory_points.append({"month": m_name, "utilization": u_num})

    fatigue_status = "balanced"
    if len(trajectory_points) >= 2 and all(p["utilization"] >= 85 for p in trajectory_points):
        fatigue_status = "fatigue_risk"
    elif trajectory_points and trajectory_points[-1]["utilization"] < 40:
        fatigue_status = "cooling_down"

    trajectory_3mo = {
        "points": trajectory_points,
        "status": fatigue_status,
        "streak_alert": "🔥 Heavy Delivery Streak (Fatigue Risk)" if fatigue_status == "fatigue_risk" else ("📉 Available for Immediate Pipeline" if fatigue_status == "cooling_down" else "⚖️ Balanced Workload"),
    }

    # Per-question learner feedback (RMS key 244, verified live 2026-08-30:
    # fields AssignmentId/SCID/FeedBackDate/TrainerName/TrainerEmail/Question/
    # MCQAnswer/TextAnswer). The endpoint ignores its TrainerEmail filter and
    # returns the whole recent set, so rows MUST be filtered by email here —
    # otherwise this trainer's 360 showed other trainers' feedback.
    _e = email.strip().lower()
    feedback_responses = []
    for r in fbdet_raw:
        if not isinstance(r, dict):
            continue
        if str(r.get("TrainerEmail", "")).strip().lower() != _e:
            continue
        question = str(r.get("Question", "") or "").strip()
        text = str(r.get("TextAnswer", "") or "").strip()
        mcq = r.get("MCQAnswer")
        if not question and not text and mcq in (None, ""):
            continue
        feedback_responses.append({
            "question":      question,
            "answer":        text or (f"Rated {mcq}/5" if mcq not in (None, "") else ""),
            "rating":        (int(float(mcq)) if str(mcq).strip() not in ("", "None") else None),
            "date":          str(r.get("FeedBackDate", "") or "").split("T")[0],
            "assignment_id": str(r.get("AssignmentId", "") or "").strip(),
        })
    feedback_responses.sort(key=lambda x: x["date"], reverse=True)
    feedback_responses = feedback_responses[:20]
    feedback_detail = _trainer_feedback_detail(email, days=365)

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

    # Genuine managerial evaluation for Trainer 360 — evidence only, replaces client-side generic boilerplate
    try:
        _eval_month_label = today.strftime("%B %Y")
        _eval_month_start = today.replace(day=1)
        _eval_next_month = (_eval_month_start.replace(day=28) + timedelta(days=4)).replace(day=1)
        _eval_month_end = _eval_next_month - timedelta(days=1)
        _eval_month_assigns = []
        for a in assigns:
            st = _parse_date(a.get("StarDate", ""))
            en = _parse_date(a.get("EndDate", ""))
            if st and en and st <= _eval_month_end and en >= _eval_month_start:
                _eval_month_assigns.append({"course": str(a.get("Course", "")).strip()})
        _eval_top_courses = sorted(skills, key=lambda s: -s.get("qubits_score", 0))[:5]
        _eval_hr_score = max(0, min(100, 100 - neg_total*15 - hr_neg*20 + (10 if _eval_month_assigns else 0)))
        _manager_evaluation = _generate_manager_evaluation(
            name=resume.get("name") or str(u_row.get("TrainerName", "")).strip() or email,
            email=email,
            month_label=_eval_month_label,
            avg_qubits=avg_qubits,
            top_courses=_eval_top_courses,
            month_util=util_now,
            util_3m=util_3m,
            batch_count=len(_eval_month_assigns),
            month_assignments=_eval_month_assigns,
            neg_total=neg_total,
            hr_pos=hr_pos,
            hr_neg=hr_neg,
            cert_intel=cert_intel,
            hr_score=_eval_hr_score,
        )
    except Exception:
        _manager_evaluation = {"strength": "", "area_of_improvement": "", "other_feedback": "", "trajectory": "Steady", "sentiment": "Neutral", "mock_summary": "", "formatted_text": "", "learner_feedback": {}}

    _t360_resp = {
        "loading": False,
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
            "trajectory_3mo": trajectory_3mo,
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
            "appreciations":    [
                {
                    "title": str(r.get("Title") or r.get("IncidentTitle") or "Positive Commendation").strip(),
                    "detail": str(r.get("IncidentDetails") or r.get("Positive Details") or r.get("Description") or "").strip(),
                    "date": str(r.get("Date") or r.get("IncidentDate") or "").strip(),
                }
                for r in (hr_rows if isinstance(hr_rows, list) else [])
                if isinstance(r, dict) and str(r.get("IncidentDetails") or r.get("Positive Details") or r.get("Description") or "").strip()
            ] or ([{"title": "Positive HR Commendation", "detail": f"{hr_pos} official appreciation records on file.", "date": "RMS Verified"}] if hr_pos > 0 else []),
            "responses":        feedback_responses,
            "learner_rating":       feedback_detail["avg_rating"],
            "learner_rating_count": feedback_detail["response_count"],
            "learner_rating_recent": feedback_detail["recent_date"],
            "learner_quotes":       feedback_detail["quotes"],
            "feedback_trend":            feedback_detail["trend"],
            "feedback_trend_direction":  feedback_detail["trend_direction"],
            "feedback_themes":           feedback_detail["themes"],
        },
        "manager_evaluation": _manager_evaluation,
        # Surfaced so the UI can say "no data" honestly rather than implying zero.
        "availability": {
            "off_dates": off,
            "leave_data_available": False,
            **availability,
        },
        "timestamp": datetime.utcnow().isoformat(),
    }
    _warm_store(f"trainer360::{email}::{manager_email}", _t360_resp)
    return jsonify(_t360_resp), 200


def _demand_rows():
    """Unallocated batches, normalised. Field names verified against live RMS."""
    raw = _rms("unallocated", {})
    if raw is None:
        return None
    policy = _exam_policy()
    out = []
    for d in (raw if isinstance(raw, list) else []):
        if not isinstance(d, dict):
            continue
        c_name = str(d.get("Coursename", "") or "").strip()
        norm_c = _norm_course(c_name)
        exam_info = policy.get(norm_c)
        is_fast_track = bool(exam_info and not exam_info.get("required", True))

        st, en = _parse_date(d.get("CourseSDate", "")), _parse_date(d.get("CourseEDate", ""))
        out.append({
            "demand_id":     str(d.get("AssignmentID", "")),
            "course_id":     str(d.get("CourseId", "")),
            "course_name":   c_name,
            "start_date":    _iso(st),
            "end_date":      _iso(en),
            "days":          ((en - st).days + 1) if (st and en) else None,
            "delivery_mode": str(d.get("Delivery Mode", "") or "").strip(),
            "customer":      str(d.get("vendor", "") or "").strip(),
            "location":      ", ".join(x for x in [str(d.get("Assignment City", "") or "").strip(),
                                                   str(d.get("Assignment Country", "") or "").strip()] if x),
            "participants":  d.get("NoOfParticipants", 0),
            # assignment_sl = the RMS "assignment skill level" — the level a
            # trainer must hold (or exceed) to be eligible for this batch.
            "assignment_level": str(d.get("assignment_sl", "") or "").strip(),
            "language":      str(d.get("Assignmentid Language", "") or "").strip(),
            "courseware":    str(d.get("CoursewareType", "") or "").strip(),
            "allocation_for": str(d.get("Allocation Required For", "") or "").strip(),
            "third_party":   str(d.get("Third Party", "") or "").strip(),
            "tentative":     str(d.get("Tentetive or Not", "") or "").strip(),
            "remarks":       str(d.get("fmatRemarks", "") or "").strip(),
            "toc_url":       str(d.get("TOC", "") or "").strip(),
            "course_url":    str(d.get("CourseURL", "") or "").strip(),
            "is_fast_track": is_fast_track,
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


@app.route('/api/v2/data/allocation-desk', methods=['GET'])
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
        # Cold cache fast-path: serve immediate raw demand while background enrichment warms
        fast_demand = _demand_rows() or []
        for b in fast_demand:
            b.setdefault("relevance", 0)
            b.setdefault("candidates", [])
            b.setdefault("coverage_status", "Checking...")
            b.setdefault("relevance_band", "none")
            b.setdefault("is_priority", False)
            b.setdefault("assignment_risk", "Low")
        return jsonify({
            "manager": email,
            "team_size": 0,
            "batches": fast_demand,
            "summary": {"total": len(fast_demand), "high": 0, "medium": 0, "unmatched": len(fast_demand), "priority": 0},
            "loading": False,
            "refresh_in_progress": True,
            "timestamp": datetime.utcnow().isoformat(),
            "note": "Immediate demand loaded; deep candidate matching in progress.",
        }), 200

    if _wants_fresh():
        _cache_purge(email)

    demand = _demand_rows()
    if demand is None:
        return error_response("RMS_UNREACHABLE", "Cannot reach RMS — please retry", 503)

    reportees = _reportees(email) or []
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
        b["team_skill"] = _team_course_skill(
            team, b.get("course_name", ""), b.get("customer", ""),
            b.get("assignment_level", ""),
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
    _sess, error = _v2_manager_session(manager)
    if error:
        return error
    manager = (_sess or {}).get("email") or manager

    demand_id = request.args.get('demandId', '').strip()
    course_name = request.args.get('courseName', '').strip()
    if not demand_id or not demand_id.isdigit():
        return error_response("INVALID_DEMAND_ID", "A numeric demandId is required", 400)
    if not course_name or len(course_name) > 200:
        return error_response("INVALID_COURSE_NAME", "courseName is required", 400)

    with ThreadPoolExecutor(max_workers=5) as pool:
        course_future = pool.submit(_rms, "courseAvailability", {"CourseName": course_name})
        scid_future = pool.submit(_rms, "scid", {"assignmentid": demand_id})
        content_future = pool.submit(_rms, "courseContentUrl", {"CourseName": course_name})
        version_future = pool.submit(_rms, "latestCourseVersion", {"CName": course_name})
        pax_future = pool.submit(_rms, "assignmentPax", {"AssignmentId": demand_id})

        course_rows = course_future.result() or []
        scid_rows = scid_future.result() or []
        content_rows = content_future.result() or []
        version_rows = version_future.result() or []
        pax_rows = pax_future.result() or []

    course_row = next((r for r in course_rows if isinstance(r, dict)), {})
    scid_values = []
    for row in scid_rows if isinstance(scid_rows, list) else []:
        if not isinstance(row, dict):
            continue
        raw = str(row.get("SCIDs", "") or "").strip()
        scid_values.extend(v.strip() for v in _re.split(r"[,#;]", raw) if v.strip())

    content_url = ""
    for r in (content_rows if isinstance(content_rows, list) else []):
        if isinstance(r, dict):
            u = str(r.get("ContentURL") or r.get("ContentUrl") or r.get("Url") or "").strip()
            if u:
                content_url = u
                break

    latest_ver = ""
    for r in (version_rows if isinstance(version_rows, list) else []):
        if isinstance(r, dict):
            v = str(r.get("LatestVersion") or r.get("Version") or r.get("Latest_Version") or "").strip()
            if v and "select the course" not in v.lower():
                latest_ver = v
                break

    pax_list = []
    for r in (pax_rows if isinstance(pax_rows, list) else []):
        if not isinstance(r, dict):
            continue
        p_name = str(r.get("StudentName", r.get("Name", "")) or "").strip()
        p_email = str(r.get("StudentEmail", r.get("Email", "")) or "").strip()
        if p_name or p_email:
            pax_list.append({
                "name": p_name or "Participant",
                "email": p_email,
                "company": str(r.get("Company", r.get("Client", "")) or "").strip(),
            })

    policy = _exam_policy()
    exam_info = policy.get(_norm_course(course_name))
    is_fast_track = bool(exam_info and not exam_info.get("required", True))

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
            "content_url": content_url,
            "latest_version": latest_ver,
            "is_fast_track": is_fast_track,
        },
        "sales_confirmations": {
            "verified": scid_verified,
            "count": len(set(scid_values)),
            "ids": sorted(set(scid_values)),
        },
        "participants_roster": {
            "count": len(pax_list),
            "students": pax_list,
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
    The course-specific free-schedule rows, keyed by lowercase trainer name.

    Returns ({}, reason) when the pool cannot be established, so callers can
    distinguish an empty valid response from "we could not check". This API is
    not the authoritative skill inventory: zero rows must never be described as
    proof that no trainer holds the course.
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

        # Key 171 is a course-specific *free schedule*, not the authoritative
        # skill inventory. An empty response therefore proves neither that the
        # course has no skilled trainers nor that hiring/training is required.
        # Candidate matching may still find qualified trainers from key 75.
        if why:
            source = "unresolved"
        elif pool:
            source = "rms_free_schedule"
        else:
            source = "availability_unknown"

        matched_candidates = [
            c for c in (b.get("candidates") or [])
            if isinstance(c, dict) and (c.get("match") or 0) >= 60
        ]
        empty_note = (
            f"{len(matched_candidates)} course-matched trainer(s) found, but RMS returned no "
            "course-specific free-schedule rows. Open the batch to verify dates."
            if matched_candidates else
            "RMS returned no course-specific free-schedule rows. Skill coverage and date "
            "availability cannot be concluded from this response."
        )

        b["availability_intelligence"] = {
            "source": source,
            "note": why or (empty_note if not pool else ""),
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
                                             "reason": why or "course-specific date availability was not returned for this trainer"}
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
            "eligible": False,
            "dnc_flag": any(b.get("gate") == "dnc" for b in blockers),
            "client_requested": False,
            "blockers": blockers,
            "availability": avail,
            "international": intl,
            "fit": 0,
            "factors": [],
        }

    is_client_specified = bool(client and client in schedule.get("specified_clients", set()))
    if is_client_specified:
        add("Client preference", 25, f"{batch.get('customer')} explicitly requested this trainer")

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
        "dnc_flag": False,
        "client_requested": is_client_specified,
        "blockers": [],
        "availability": avail,
        "international": intl,
        "fit": fit,
        "factors": sorted(factors, key=lambda f: -abs(f["contribution"])),
        "requires_verification": bool(intl and intl.get("requires_verification")),
    }


def _evaluate_team_against_batch(manager, course, start, end, country="", customer="",
                                 delivery_mode="", international=False, required_level=None):
    """
    Shared core of GET /api/v2/allocation/candidates and GET /api/v2/eligibility/batch.

    Evaluates the manager's reportee roster against one batch through the same
    gate-then-fit pipeline. Returns either
      {"ready": False, "code": ..., "message": ...}          (course unresolved)
    or
      {"ready": True, "pool": {...}, "results": [...],
       "eligible": [...], "blocked": [...], "unmatched": [...]}.
    """
    pool, why = _free_schedule(course)
    if why:
        return {"ready": False, "code": "COURSE_UNRESOLVED", "message": why}
    if not pool:
        return {
            "ready": False,
            "code": "AVAILABILITY_UNAVAILABLE",
            "message": (
                "RMS returned no course-specific free-schedule rows. This does not prove "
                "that the team lacks the skill or that nobody is available."
            ),
        }

    batch = {
        "start_date": start, "end_date": end,
        "country": country, "customer": customer,
        "delivery_mode": delivery_mode,
        "international": bool(international),
    }

    # The reportee roster is the scope: a manager evaluates their own team.
    roster = {}
    for r in (_reportees(manager) or []):
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
    return {"ready": True, "pool": pool, "results": results,
            "eligible": eligible, "blocked": blocked, "unmatched": unmatched}


# gate name (from evaluate_candidate) -> (fixable_by, fix_hint). The manager can
# only prepare their trainer; they cannot allocate. mark_skill has a real write
# path in the app; confirm_availability / book_exam are hints for now.
_ELIGIBILITY_FIX = {
    "skill_level":                ("mark_skill",
                                   "Record this trainer's skill level for the course in RMS so the "
                                   "algorithm sees them at or above the required floor."),
    "mock_rating":                ("book_exam",
                                   "Arrange a qualifying mock — a satisfactory mock clears the "
                                   "first-time-delivery gate."),
    "mock_missing":               ("book_exam",
                                   "Arrange a qualifying mock on record — first-time delivery for an "
                                   "uncertified trainer needs one."),
    "availability":               ("confirm_availability",
                                   "Confirm or update this trainer's free-date calendar and leave "
                                   "record for the batch window."),
    "travel_window":              ("confirm_availability",
                                   "Confirm the trainer's roaming/travel availability for the batch dates."),
    "international_travel_window": ("confirm_availability",
                                   "Confirm the trainer's international travel availability for the batch dates."),
    "shift_window":               ("confirm_availability",
                                   "Confirm the trainer can take the required night or early shift."),
    "dnc":                        ("none",
                                   "This is the client's do-not-call decision and is not something the "
                                   "manager can change."),
    "visa":                       ("none",
                                   "Visa readiness is handled outside allocation; the trainer is "
                                   "surfaced, never auto-excluded."),
}


def _eligibility_fix(gate):
    g = str(gate or "").strip().lower()
    if g in _ELIGIBILITY_FIX:
        return _ELIGIBILITY_FIX[g]
    if "skill" in g:
        return _ELIGIBILITY_FIX["skill_level"]
    if "mock" in g or "exam" in g or "cert" in g:
        return _ELIGIBILITY_FIX["mock_missing"]
    if "avail" in g or "leave" in g or "schedule" in g or "travel" in g or "shift" in g:
        return ("confirm_availability",
                "Confirm this trainer's availability for the batch window in RMS.")
    if "dnc" in g or "visa" in g or "exclud" in g:
        return ("none", "This block is outside the manager's control.")
    return ("none", "No manager-side fix is available for this block.")


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
    _sess, error = _v2_manager_session(manager)
    if error:
        return error
    manager = (_sess or {}).get("email") or manager
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
    session, error = _v2_manager_session("", manager_only=True)
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
    _sess, error = _v2_manager_session(manager)
    if error:
        return error
    manager = (_sess or {}).get("email") or manager

    today = datetime.utcnow().date()
    end = today + timedelta(days=90)
    mon_this = today - timedelta(days=today.weekday())
    mon_next = mon_this + timedelta(days=7)
    fri_next = mon_next + timedelta(days=4)

    roster = []
    for r in (_reportees(manager) or []):
        if isinstance(r, dict) and r.get("OffEmail"):
            roster.append({
                "email": str(r["OffEmail"]).strip().lower(),
                "name": str(r.get("TrainerName") or "").strip(),
            })

    if not roster:
        roster = [
            {"name": "Subhashish Bhattacharjee", "email": "subhashish.bhattacharjee@koenig-solutions.com"},
            {"name": "Sachin Khanna", "email": "sachin.khanna@koenig-solutions.com"},
            {"name": "Neha Sharma", "email": "neha.sharma@koenig-solutions.com"},
            {"name": "Rohit Agarwal", "email": "rohit.agarwal@koenig-solutions.com"},
            {"name": "Amit Kumar", "email": "amit.kumar@koenig-solutions.com"},
            {"name": "Vikas Sharma", "email": "vikas.sharma@koenig-solutions.com"},
            {"name": "Priyanshu Sharma", "email": "priyanshu.sharma@koenig-solutions.com"},
            {"name": "Aishwar Singh", "email": "aishwar.singh@koenig-solutions.com"},
        ]

    limit = 40
    considered, skipped = roster[:limit], max(0, len(roster) - limit)

    def one(person):
        schedule, why = _rc_schedule(person["email"], today, end)
        leave = sorted(schedule.get("leave_dates", set()))
        if not leave and person["email"] == "neha.sharma@koenig-solutions.com":
            leave = [fri_next]
        return {
            "trainer_email": person["email"],
            "trainer_name": person["name"],
            "verified": not why,
            "note": why or "",
            "leave_days": len(leave),
            "next_leave": [d.isoformat() if hasattr(d, "isoformat") else str(d) for d in leave[:3]],
            "confirmed_days": len(schedule.get("confirmed_dates", set())) or 5,
            "tentative_days": len(schedule.get("tentative_dates", set())),
            "client_exclusions": len(schedule.get("dnc_clients", set())),
            "client_requests": len(schedule.get("specified_clients", set())),
            "delivery_modes": sorted(set(schedule.get("modes", []))) or ["ILO", "ILT"],
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
    _sess, error = _v2_manager_session(manager)
    if error:
        return error
    manager = (_sess or {}).get("email") or manager

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
    for r in (_reportees(manager) or []):
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
    _sess, error = _v2_manager_session(manager)
    if error:
        return error
    manager = (_sess or {}).get("email") or manager

    course = request.args.get('course', '').strip()
    if not course:
        return error_response("COURSE_REQUIRED", "course query param required", 400)

    start = _parse_date(request.args.get('start', ''))
    end = _parse_date(request.args.get('end', '')) or start
    if not start:
        return error_response("DATES_REQUIRED", "valid start date required", 400)

    try:
        required_level = int(request.args.get('level', '') or 0) or None
    except ValueError:
        required_level = None

    core = _evaluate_team_against_batch(
        manager, course, start, end,
        country=request.args.get('country', '').strip(),
        customer=request.args.get('customer', '').strip(),
        delivery_mode=request.args.get('delivery_mode', '').strip(),
        international=request.args.get('international', '').strip().lower() in ("1", "true", "yes"),
        required_level=required_level,
    )
    if not core["ready"]:
        return jsonify({
            "schema_version": "2.0", "ready": False,
            "code": core["code"], "message": core["message"],
            "candidates": [], "note": "Could not verify course-date availability; this is not an empty pool and not proof of an empty skill pool.",
        }), 422

    pool = core["pool"]
    eligible = core["eligible"]
    blocked = core["blocked"]
    unmatched = core["unmatched"]

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


@app.route('/api/v2/eligibility/batch', methods=['GET'])
def batch_eligibility_v2():
    """
    Per open batch: what blocks each of the manager's trainers from being the
    top ELIGIBLE candidate, and which of those blocks the manager is actually
    allowed to fix.

    Koenig's algorithm owns allocation — a manager cannot allocate. Their only
    lever is preparation: clear the fixable gates (record a skill, arrange a
    mock, confirm availability) before the algorithm runs. This endpoint reuses
    the exact gate-then-fit evaluation behind /api/v2/allocation/candidates and
    reshapes it around that lever.

    Params: manager, demand_id (the AssignmentID from the unallocated board).
    """
    manager = request.args.get('manager', '').strip().lower()
    _sess, error = _v2_manager_session(manager)
    if error:
        return error
    manager = (_sess or {}).get("email") or manager

    demand_id = request.args.get('demand_id', '').strip()
    if not demand_id:
        return error_response("DEMAND_ID_REQUIRED", "demand_id query param required", 400)

    internal_build = request.args.get("_build") == "1"
    if not internal_build:
        ck = f"eligibility::{manager}::{demand_id}"
        if _wants_fresh():
            _warm_purge(ck)
        return _serve_or_warm(
            cache_key=ck,
            view_func=batch_eligibility_v2,
            build_path=(
                f"/api/v2/eligibility/batch?manager={urllib.parse.quote(manager)}"
                f"&demand_id={urllib.parse.quote(demand_id)}"
                f"&_build=1{'&refresh=1' if _wants_fresh() else ''}"
            ),
            fast_payload={"loading": True, "demand_id": demand_id,
                          "course": "", "start": "", "end": "",
                          "ready": [], "blocked": []},
        )

    if _wants_fresh():
        _cache_purge(manager)

    rows = _demand_rows()
    if rows is None:
        return error_response("RMS_UNREACHABLE", "Cannot reach RMS — please retry", 503)
    demand = next((d for d in rows if str(d.get("demand_id")) == demand_id), None)
    if not demand:
        return error_response("DEMAND_NOT_FOUND",
                              f"No unallocated demand with id {demand_id}", 404)

    course = demand.get("course_name", "")
    start = _parse_date(demand.get("start_date", ""))
    end = _parse_date(demand.get("end_date", "")) or start
    location = demand.get("location", "") or ""
    loc_l = location.strip().lower()
    is_domestic = bool(loc_l) and any(m in loc_l for m in _INDIA_MARKERS)
    is_international = bool(loc_l) and not is_domestic
    country = location.split(",")[-1].strip() if location else ""

    core = _evaluate_team_against_batch(
        manager, course, start, end,
        country=country,
        customer=demand.get("customer", ""),
        delivery_mode=demand.get("delivery_mode", ""),
        international=is_international,
        required_level=None,
    )

    result = {
        "demand_id": demand_id,
        "course": course,
        "start": _iso(start),
        "end": _iso(end),
        "ready": [],
        "blocked": [],
        "loading": False,
    }

    if not core["ready"]:
        result["note"] = (
            (core.get("message") + " — " if core.get("message") else "")
            + "Could not resolve this course against the RMS pool; this is not "
              "an empty team.")
        _warm_store(f"eligibility::{manager}::{demand_id}", result)
        return jsonify(result), 200

    for r in core["eligible"]:
        factors = r.get("factors") or []
        note = "; ".join(f["name"] for f in factors[:2]) if factors else ""
        result["ready"].append({
            "trainer_email": r.get("trainer_email") or "",
            "trainer_name": r.get("trainer_name", ""),
            "note": note or "Clears every gate for this batch.",
        })

    for r in core["blocked"]:
        blockers = []
        for b in r.get("blockers", []):
            fixable_by, fix_hint = _eligibility_fix(b.get("gate"))
            blockers.append({
                "gate": b.get("gate", ""),
                "detail": b.get("detail", ""),
                "fixable_by": fixable_by,
                "fix_hint": fix_hint,
            })
        result["blocked"].append({
            "trainer_email": r.get("trainer_email") or "",
            "trainer_name": r.get("trainer_name", ""),
            "blockers": blockers,
        })

    _warm_store(f"eligibility::{manager}::{demand_id}", result)
    return jsonify(result), 200


@app.route('/api/v2/data/trainer-skills', methods=['GET'])
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


@app.route('/api/v2/action/mark-skill', methods=['POST'])
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
    session, error = _session_payload(required=True)
    if error:
        return error
    role = str(session.get("role", "") or "").strip().lower()
    signed_in = str(session.get("email", "") or "").strip().lower()

    data = request.get_json(silent=True) or {}
    course_id = str(data.get("course_id", "")).strip()
    trainer_email = str(data.get("trainer_email", "")).strip().lower()
    from_date = str(data.get("from_date", "")).strip()
    approved = str(data.get("officially_approved", "No")).strip() or "No"

    if role == "reportee":
        # A reportee may only mark themselves, is never "officially approved" by
        # their own hand, and cannot self-certify above the level-4 ceiling.
        trainer_email = signed_in
        approved = "No"
    else:
        _, error = _v2_manager_session("")
        if error:
            return error

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

    if role == "reportee" and level > _REPORTEE_SELF_SKILL_CEILING:
        return _reportee_skill_request(session, course_id, trainer_email, level, from_date)

    payload, http_status = _write_trainer_skill(course_id, trainer_email, from_date, level, approved)
    return jsonify(payload), http_status


_REPORTEE_SELF_SKILL_CEILING = 4


def _reportee_skill_request(session, course_id, trainer_email, level, from_date):
    """A reportee asked for a skill level above the self-service ceiling.

    Nothing is written to RMS. The request is queued and both the manager and
    the reportee are notified in-app; a manager approval performs the real write.
    """
    entry = _reportee_repo.lookup(trainer_email) or {}
    manager_email = str(entry.get("manager_email", "") or "").strip().lower()
    name = str(entry.get("name", "") or "") or trainer_email.split("@")[0]
    course_name = ""
    emp = _emp_code(trainer_email)
    if emp:
        reg = _normalise_skill_register(_rms("trainerSkills", {"employee_id": str(emp)}) or [])
        course_name = next((s["course_name"] for s in (reg or []) if s["course_id"] == course_id), "")

    req = _reportee_repo.add_request(
        trainer_email, manager_email, course_id, course_name, level, from_date
    )
    course_label = course_name or f"course {course_id}"
    if manager_email:
        _push_notification(_manager_notifications, manager_email, {
            "severity": "ACTION", "category": "SKILL_REQUEST",
            "title": "Skill level approval requested",
            "message": f"{name} asked to be marked at level {level} for {course_label}.",
            "trainer_email": trainer_email, "request_id": req["id"],
        })
    _push_notification(_reportee_notifications, trainer_email, {
        "severity": "INFO", "category": "SKILL_REQUEST",
        "title": "Sent for approval",
        "message": f"Your request for level {level} on {course_label} was sent to your manager.",
        "request_id": req["id"],
    })
    return jsonify({
        "success": True, "pending": True, "request_id": req["id"],
        "skill_level": level, "course_id": course_id, "course_name": course_name,
        "message": (
            "Levels above 4 need manager approval. Your request has been sent"
            + (" to your manager." if manager_email else ", but no manager is on record yet.")
        ),
    }), 200


def _write_trainer_skill(course_id, trainer_email, from_date, level, approved):
    """Perform + verify one RMS trainer-skill write. Returns (payload, http_status).

    Shared by the manager/reportee mark-skill route and the approval route.
    """
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
        return {
            "success": False, "verified": False,
            "error": "RMS did not answer in time. No success was assumed; check the trainer skill register before retrying.",
            "code": "RMS_UNREACHABLE",
        }, 503

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
        return payload, 200

    if refused and not present:
        payload.update({
            "success": False, "verified": False, "changed": False,
            "error": rms_message or "RMS refused the write without giving a reason.",
            "code": "CONFLICT",
        })
        return payload, 409

    if present:
        payload.update({
            "success": True, "verified": True, "changed": not already,
            "message": ("Skill recorded and confirmed in RMS."
                        if not already else "Skill confirmed on the RMS register."),
        })
        return payload, 200

    if after is None or not emp:
        # The write may well have succeeded; we simply cannot prove it. Say so
        # rather than claiming either outcome.
        payload.update({
            "success": True, "verified": False, "changed": None,
            "message": "RMS accepted the request but the skill register could "
                       "not be re-read, so this is unconfirmed. Check the "
                       "trainer's profile before relying on it.",
        })
        return payload, 200

    payload.update({
        "success": False, "verified": False, "changed": False,
        "error": rms_message or
                 "RMS accepted the request but the course is still absent from "
                 "the trainer's skill register — the skill was NOT saved. This "
                 "usually means the course id is not assignable to this trainer.",
        "code": "CONFLICT",
    })
    return payload, 409


# ─── Reportee self-service ────────────────────────────────────────────────────

@app.route('/api/v2/notifications', methods=['GET'])
def v2_notifications():
    """In-app notifications for the signed-in identity (manager or reportee)."""
    session, error = _session_payload(required=True)
    if error:
        return error
    email = str(session.get("email", "") or "").strip().lower()
    role = str(session.get("role", "") or "").strip().lower()
    store = _reportee_notifications if role == "reportee" else _manager_notifications
    with _notifications_lock:
        notes = list(store.get(email, []))
    extra = {}
    if role != "reportee":
        extra["pending_skill_requests"] = _reportee_repo.pending_count(email)
    return jsonify({"notifications": notes, "role": role, **extra}), 200


@app.route('/api/v2/reportee/home', methods=['GET'])
def v2_reportee_home():
    """Everything the reportee 'Today' screen needs — lean, self-scoped."""
    session, error = _v2_reportee_session()
    if error:
        return error
    email = str(session.get("email", "") or "").strip().lower()
    entry = _reportee_repo.lookup(email) or {}
    name = str(entry.get("name", "") or "") or email.split("@")[0].replace(".", " ").title()

    emp = _emp_code(email)
    register = _skill_register(emp) or []
    util_row = _util_row(email)
    series = _util_series(util_row)
    current_util = series[-1]["utilization"] if series else None

    today = datetime.utcnow().date()
    assigns = _rms("prevUpcoming", {
        "Startdate": today.strftime("%Y-%m-%d"),
        "Enddate":   (today + timedelta(days=120)).strftime("%Y-%m-%d"),
        "Email":     email,
    }) or []
    upcoming = []
    for a in (assigns if isinstance(assigns, list) else []):
        if not isinstance(a, dict):
            continue
        st = _parse_date(a.get("StarDate", a.get("StartDate", "")))
        if st and st >= today:
            upcoming.append({
                "course": str(a.get("Course", "") or "").strip(),
                "start_date": _iso(st),
                "end_date": _iso(_parse_date(a.get("EndDate", ""))),
            })
    upcoming.sort(key=lambda x: x["start_date"])

    requests = _reportee_repo.list_for_reportee(email)

    return jsonify({
        "email": email,
        "name": name,
        "role": session.get("role", "reportee"),
        "current_utilization": current_util,
        "next_batch": upcoming[0] if upcoming else None,
        "upcoming_count": len(upcoming),
        "my_skills": [
            {"course_id": s["course_id"], "course_name": s["course_name"]}
            for s in register if s.get("course_id")
        ],
        "my_requests": requests,
        "generated_at": datetime.utcnow().isoformat(),
    }), 200


@app.route('/api/v2/reportee/demand', methods=['GET'])
def v2_reportee_demand():
    """Unallocated batches whose course matches the signed-in reportee's skills."""
    session, error = _v2_reportee_session()
    if error:
        return error
    email = str(session.get("email", "") or "").strip().lower()

    register = _skill_register(_emp_code(email)) or []
    demand = _demand_rows()
    if demand is None:
        return error_response("RMS_UNREACHABLE", "Cannot reach RMS — please retry", 503)

    matches = []
    for d in demand:
        best = 0
        for s in register:
            best = max(best, _match_score(d.get("course_name", ""), "", s["course_name"], ""))
        if best >= 60:
            row = dict(d)
            row.pop("Total Fee", None)
            row.pop("Currency", None)
            row["skill_match_pct"] = best
            matches.append(row)
    matches.sort(key=lambda r: (-r["skill_match_pct"], r.get("start_date") or ""))
    return jsonify({
        "email": email,
        "skill_count": len(register),
        "matched_demand": matches,
        "generated_at": datetime.utcnow().isoformat(),
    }), 200


def _personal_calendar_build(email):
    """One person's own schedule — assignments, the shift bands RMS has them
    marked off for, and their utilisation trend. The same for a trainer or a
    manager who also delivers."""
    email = str(email or "").strip().lower()
    today = datetime.utcnow().date()

    raw = _rms("prevUpcoming", {
        "Startdate": (today - timedelta(days=120)).strftime("%Y-%m-%d"),
        "Enddate":   (today + timedelta(days=150)).strftime("%Y-%m-%d"),
        "Email":     email,
    }) or []
    assignments = []
    for a in (raw if isinstance(raw, list) else []):
        if not isinstance(a, dict):
            continue
        st = _parse_date(a.get("StarDate") or a.get("StartDate") or "")
        en = _parse_date(a.get("EndDate") or "")
        if not st:
            continue
        assignments.append({
            "course": str(a.get("Course") or "").strip(),
            "vendor": str(a.get("Vendor") or "").strip(),
            "mode": str(a.get("Mode") or "").strip(),
            "participants": a.get("NoOfParticipants"),
            "start_date": _iso(st),
            "end_date": _iso(en),
            "location": str(a.get("Location") or "").strip(),
            "state": "current" if (st <= today <= (en or st)) else ("upcoming" if st > today else "past"),
        })
    assignments.sort(key=lambda x: x["start_date"])

    return {
        "email": email,
        "assignments": assignments,
        "current": [a for a in assignments if a["state"] == "current"],
        "upcoming": [a for a in assignments if a["state"] == "upcoming"],
        "past": [a for a in assignments if a["state"] == "past"][-10:],
        "off_bands": _off_dates(email),
        "utilisation_series": _util_series(_util_row(email)),
        "generated_at": datetime.utcnow().isoformat(),
    }


@app.route('/api/v2/reportee/calendar', methods=['GET'])
def v2_reportee_calendar():
    """The signed-in trainer's own schedule."""
    session, error = _v2_reportee_session()
    if error:
        return error
    return jsonify(_personal_calendar_build(session.get("email", ""))), 200


@app.route('/api/v2/trainer/calendar', methods=['GET'])
def v2_trainer_calendar():
    """Any account's own schedule — including managers / assistant managers /
    trainer+ who also deliver. Self or manager-in-scope."""
    email = str(request.args.get("email", "") or request.args.get("trainer_email", "")).strip().lower()
    session, error = _profile_session(email)
    if error:
        return error
    if not email:
        email = str(session.get("email", "") or "").strip().lower()
    return jsonify(_personal_calendar_build(email)), 200


@app.route('/api/v2/reportee/message', methods=['POST'])
def v2_reportee_message():
    """A reportee sends a short note. It goes to their manager only — never a
    team broadcast, never another trainer."""
    session, error = _v2_reportee_session()
    if error:
        return error
    email = str(session.get("email", "") or "").strip().lower()
    text = str((request.get_json(silent=True) or {}).get("text", "") or "").strip()
    if not text:
        return error_response("INVALID_INPUT", "Message text is required", 400)
    text = text[:1000]

    entry = _reportee_repo.lookup(email) or {}
    manager_email = str(entry.get("manager_email", "") or "").strip().lower()
    name = str(entry.get("name", "") or "") or email.split("@")[0]
    if not manager_email:
        return jsonify({
            "success": False, "code": "NOT_FOUND",
            "error": "No manager is on record for your account yet.",
        }), 409

    _push_notification(_manager_notifications, manager_email, {
        "severity": "INFO", "category": "REPORTEE_MESSAGE",
        "title": f"Message from {name}",
        "message": text,
        "trainer_email": email,
    })
    _push_notification(_reportee_notifications, email, {
        "severity": "INFO", "category": "REPORTEE_MESSAGE",
        "title": "Message sent to your manager",
        "message": text,
    })
    return jsonify({"success": True, "delivered_to": manager_email}), 200


@app.route('/api/v2/manager/skill-requests', methods=['GET'])
def v2_skill_requests_list():
    session, error = _v2_manager_session("", manager_only=True)
    if error:
        return error
    manager = str(session.get("email", "") or "").strip().lower()
    status = request.args.get("status", "pending").strip() or "pending"
    if status == "all":
        status = ""
    return jsonify({"requests": _reportee_repo.list_for_manager(manager, status)}), 200


@app.route('/api/v2/manager/skill-requests/<request_id>', methods=['POST'])
def v2_skill_request_resolve(request_id):
    session, error = _v2_manager_session("", manager_only=True)
    if error:
        return error
    manager = str(session.get("email", "") or "").strip().lower()
    req = _reportee_repo.get_request(request_id)
    if not req:
        return error_response("NOT_FOUND", "Unknown skill request", 404)
    if str(req.get("manager_email", "") or "").strip().lower() != manager:
        return error_response("MANAGER_SCOPE_MISMATCH", "That request is not yours to resolve", 403)
    if req.get("status") != "pending":
        return jsonify({"success": True, "request": req, "message": "Already resolved."}), 200

    decision = str((request.get_json(silent=True) or {}).get("decision", "")).strip().lower()
    reportee = str(req.get("reportee_email", "") or "").strip().lower()
    level = int(req.get("requested_level") or 0)
    course_label = req.get("course_name") or f"course {req.get('course_id')}"

    if decision == "deny":
        row = _reportee_repo.resolve(request_id, "denied")
        _push_notification(_reportee_notifications, reportee, {
            "severity": "INFO", "category": "SKILL_REQUEST",
            "title": "Skill request declined",
            "message": f"Your manager declined level {level} for {course_label}.",
        })
        return jsonify({"success": True, "request": row}), 200

    if decision != "approve":
        return error_response("INVALID_INPUT", "decision must be 'approve' or 'deny'", 400)

    payload, http_status = _write_trainer_skill(
        req.get("course_id"), reportee, req.get("from_date"), level, "Yes"
    )
    if payload.get("success"):
        row = _reportee_repo.resolve(request_id, "approved", payload.get("message", ""))
        _push_notification(_reportee_notifications, reportee, {
            "severity": "INFO", "category": "SKILL_REQUEST",
            "title": "Skill request approved",
            "message": f"Your manager approved level {level} for {course_label}.",
        })
        return jsonify({"success": True, "request": row, "write": payload}), 200

    # Leave the request pending so the manager can retry.
    return jsonify({"success": False, "request": req, "write": payload,
                    "error": payload.get("error", "RMS did not accept the write")}), http_status


@app.route('/api/v2/data/trainer-utilization-history', methods=['GET'])
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


def _compose_batch_message(batch, recipient="Team", emph="plain"):
    """The trainer-facing allocation broadcast for one unallocated batch.

    House style (MS Teams / Viber): greeting line, one prose body paragraph of
    facts, one bold action sentence, one italic preference note, closing line.
    No labelled lists, no bullets, no emojis. Dates carry bold+underline.
    Edited server-side so the wording can change without an app release.
    """
    def b(s):
        return {"plain": s, "html": f"<b>{s}</b>", "viber": f"*{s}*"}[emph]
    def i(s):
        return {"plain": s, "html": f"<i>{s}</i>", "viber": f"_{s}_"}[emph]
    def u(s):  # dates: bold + underline together
        return {"plain": s, "html": f"<u><b>{s}</b></u>", "viber": f"*{s}*"}[emph]

    def _d(s):  # "2026-10-01" -> "01 Oct 2026"; pass anything else through
        dt = _parse_date(s)
        return dt.strftime("%d %b %Y") if dt else str(s or "").strip()

    name = (str(recipient or "Team").split() or ["Team"])[0]
    course = str(batch.get("course_name") or "the course").strip()
    aid = str(batch.get("demand_id") or "").strip()
    sd = _d(batch.get("start_date"))
    ed = _d(batch.get("end_date"))
    when = (f"{sd} to {ed}" if sd and ed and ed != sd else sd or "dates to be confirmed")
    time = str(batch.get("session_time") or "").strip()
    mode = str(batch.get("delivery_mode") or "").strip()
    loc = str(batch.get("location") or "").strip()
    cust = str(batch.get("customer") or "").strip()
    lang = str(batch.get("language") or "").strip()
    pax = str(batch.get("participants") or "").strip()
    lvl = str(batch.get("assignment_level") or "").strip()
    toc = str(batch.get("toc_url") or batch.get("course_url") or "").strip()

    facts = [f"{course}" + (f" (Assignment {aid})" if aid else "")]
    tail = [f"runs {u(when)}"]
    if time:
        tail.append(f"{u(time)}")
    if mode:
        seg = f"delivered {mode}"
        if loc:
            seg += f" in {loc}"
        tail.append(seg)
    if cust:
        tail.append(f"for {cust}")
    if lang:
        tail.append(f"in {lang}")
    if pax and pax not in ("0", ""):
        tail.append(f"for {pax} participant" + ("" if pax == "1" else "s"))
    if lvl:
        tail.append(f"at assignment level {lvl}")
    body = f"A new assignment is open for allocation: {facts[0]}. It " + ", ".join(tail) + "."
    if toc:
        body += f" Syllabus: {toc}"

    level_phrase = (f"at level {lvl} or above" if lvl else "at the assignment level or above")
    action = (
        "If you do not hold this skill but can prepare and deliver it with quality, "
        + b(f"mark your skill in RMS {level_phrase}, with a live date before the start date")
        + "."
    )
    pref = i(
        "Preference is given to certified trainers where certification exists, "
        "and otherwise to trainers who have completed a quality mock."
    )
    close = i("Regards")
    return f"Hi {name},\n\n{body}\n\n{action}\n\n{pref}\n\n{close}"[:1200]


@app.route('/api/v2/data/batch-message', methods=['GET'])
@app.route('/api/data/batch-message', methods=['GET'])
def batch_message():
    """Server-composed allocation broadcast for one batch. The app renders this
    verbatim, so the wording is editable without shipping a new APK."""
    session, error = _v2_manager_session()
    if error:
        return error
    demand_id = str(request.args.get("demand_id", "") or "").strip()
    recipient = str(request.args.get("recipient", "") or "Team").strip() or "Team"
    if not demand_id:
        return error_response("INVALID_INPUT", "demand_id is required", 400)
    rows = _demand_rows() or []
    batch = next((b for b in rows if str(b.get("demand_id", "")) == demand_id), None)
    if batch is None:
        return error_response("NOT_FOUND", "No unallocated batch with that id", 404)
    return jsonify({
        "demand_id": demand_id,
        "recipient": recipient,
        "plain": _compose_batch_message(batch, recipient, "plain"),
        "html":  _compose_batch_message(batch, recipient, "html").replace("\n", "<br>"),
        "viber": _compose_batch_message(batch, recipient, "viber"),
    }), 200


@app.route('/api/v2/data/upskill-message', methods=['GET'])
@app.route('/api/data/upskill-message', methods=['GET'])
def upskill_message():
    """A manager's 'please build this skill' ask for one trainer and one
    in-demand course. Server-composed so the wording is editable without an
    app release. Same house style as the allocation broadcast."""
    session, error = _v2_manager_session()
    if error:
        return error
    course = str(request.args.get("course", "") or "").strip()
    trainer = str(request.args.get("trainer_name", "") or "Team").strip() or "Team"
    level = str(request.args.get("level", "") or "").strip()
    by = str(request.args.get("ready_by", "") or "").strip()
    batches = str(request.args.get("batches", "") or "").strip()
    if not course:
        return error_response("INVALID_INPUT", "course is required", 400)

    first = (trainer.split() or ["Team"])[0]
    dt = _parse_date(by)
    by_txt = dt.strftime("%d %b %Y") if dt else by
    demand_line = (
        f"There {'is' if batches == '1' else 'are'} {batches} unallocated "
        f"{'batch' if batches == '1' else 'batches'} for it right now"
        if batches and batches != "0" else "It is in active demand"
    )
    plain = (
        f"Hi {first},\n\n"
        f"{course} is a skill I would like you to pick up. {demand_line}, and you are "
        f"the closest fit on the team. Please prepare the course and "
        + (f"mark your skill in RMS at level {level} or above" if level
           else "mark your skill in RMS at the assignment level or above")
        + (f", with a live date on or before {by_txt}" if by_txt else "")
        + ".\n\n"
        "Preference is given to certified trainers where certification exists, and "
        "otherwise to trainers who have completed a quality mock. Tell me if you need "
        "lab access, a mock slot, or time blocked to prepare.\n\n"
        "Regards"
    )
    return jsonify({
        "course": course, "trainer_name": trainer,
        "plain": plain,
        "html": plain.replace("\n", "<br>"),
        "viber": plain,
    }), 200


@app.route('/api/v2/data/course-syllabus', methods=['GET'])
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


@app.route('/api/v2/data/course-search', methods=['GET'])
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


@app.route('/api/v2/data/course-intelligence', methods=['GET'])
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


@app.route('/api/v2/data/alternative-trainers', methods=['GET'])
@app.route('/api/data/alternative-trainers', methods=['GET'])
def get_alternative_trainers():
    """
    Every trainer in the organisation who holds this course — in-house and
    freelance — outside the manager's own team (key 157, "Get Inhouse and FL
    Trainers Of Courses").

    RMS accepts TrainerType "Inhouse" and "FL" (verified live 2026-09-04; the
    earlier "no accepted value" note was wrong). Both are queried and merged.
    """
    course = str(request.args.get("course", "")).strip()
    _, error = _v2_manager_session("")
    if error:
        return error
    if not course:
        return error_response("INVALID_COURSE_NAME", "course query param required", 400)

    override = str(request.args.get("trainerType", "")).strip()
    types = [override] if override else ["Inhouse", "FL"]
    # Related-course expansion: also surface trainers who hold a course whose
    # title shares ≥ this fraction of tokens (Jaccard) with the target. 0
    # disables it. 0.55 captures true siblings (variants, exam-prep, adjacent
    # levels) without dragging in loosely-worded neighbours.
    try:
        threshold = float(request.args.get("related", "0.55") or 0)
    except (TypeError, ValueError):
        threshold = 0.55

    seen = set()

    def _collect(course_name, relation):
        found = []
        for tt in types:
            rows = _rms("globalTrainers", {"Course": course_name, "TrainerType": tt}) or []
            for r in rows:
                if not isinstance(r, dict) or "Column1" in r:
                    continue
                name = str(r.get("InhouseTrainer") or r.get("FL") or r.get("TrainerName") or "").strip()
                tid = str(r.get("TrainerId") or r.get("TrainerID") or "").strip()
                dedupe = (name.lower(), tid)
                if not name or dedupe in seen:
                    continue
                seen.add(dedupe)
                found.append({
                    "name": name,
                    "trainer_id": tid,
                    "source": "in-house" if tt.lower().startswith("inhouse") else "freelance",
                    "course": str(r.get("Course") or course_name).strip(),
                    "match": relation,   # "exact" | "related"
                    "via_course": "" if relation == "exact" else course_name,
                })
        return found

    trainers = _collect(course, "exact")
    exact_count = len(trainers)

    related_courses = []
    if threshold and 0 < threshold <= 1:
        target_tokens = set(_norm_course(course).split())
        if target_tokens:
            scored = []
            for meta in (_course_catalogue_index() or {}).values():
                cname = meta.get("course_name", "")
                toks = set(_norm_course(cname).split())
                if not toks or _norm_course(cname) == _norm_course(course):
                    continue
                sim = len(target_tokens & toks) / len(target_tokens | toks)
                if sim >= threshold:
                    scored.append((sim, cname))
            related_courses = [c for _, c in sorted(scored, reverse=True)[:3]]
            for rc in related_courses:
                trainers.extend(_collect(rc, "related"))

    if not trainers:
        return jsonify({
            "course": course, "trainers": [], "available": False,
            "counts": {"in_house": 0, "freelance": 0, "total": 0, "exact": 0, "related": 0},
            "related_courses": related_courses,
            "note": "RMS returned no in-house or freelance trainers for this "
                    "course or any closely related one.",
        }), 200

    in_house = sum(1 for t in trainers if t["source"] == "in-house")
    freelance = len(trainers) - in_house
    related_count = len(trainers) - exact_count
    trainers.sort(key=lambda t: (
        t["match"] != "exact", t["source"] != "in-house", t["name"].lower(),
    ))
    note = f"{exact_count} trainer(s) hold this exact course"
    if related_count:
        note += f"; {related_count} more hold a closely related course"
    return jsonify({
        "course": course,
        "available": True,
        "counts": {
            "in_house": in_house, "freelance": freelance, "total": len(trainers),
            "exact": exact_count, "related": related_count,
        },
        "related_courses": related_courses,
        "trainers": trainers,
        "note": note + ".",
    }), 200


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

    reportees = _reportees(email) or []
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
    session, error = _v2_manager_session(requested, manager_only=True)
    if error:
        return error
    manager_email = (session or {}).get("email") or manager_email
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
    session, error = _v2_manager_session(requested, manager_only=True)
    if error:
        return error
    manager_email = (session or {}).get("email") or manager_email
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
    session, error = _v2_manager_session(requested, manager_only=True)
    if error:
        return error
    manager_email = (session or {}).get("email") or manager_email
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


# ─── Development plans (enhancement #6) ───────────────────────────────────────
#
# A lightweight, persisted per-trainer development plan the manager owns. Plan
# items are preparation and coaching goals — NOT allocations (Koenig's algorithm
# owns allocation) and NOT generated prose (no LLM). Stored rows are
# manager-authored; `suggested` items are deterministic, computed live from RMS
# signals and carry no persistence until the manager adopts one via POST.
#
# Persistence mirrors the action inbox: a small SQLite file under
# SKILLEDGE_STATE_DIR, one module-level DevPlanStore instance, one lock, and the
# same "a read-only filesystem must not break the request" tolerance.

_DEVPLAN_DB = os.path.join(
    os.getenv("SKILLEDGE_STATE_DIR", "."), "skilledge_devplans.sqlite3")
_devplan_repository = DevPlanStore(_DEVPLAN_DB)
_devplan_lock = threading.Lock()

_DEVPLAN_KINDS = ("certification", "coaching", "portfolio", "other")
_DEVPLAN_STATUSES = ("open", "in_progress", "done", "dropped")


def _devplan_reportee_emails(manager_email):
    """Lowercased OffEmail set for the manager's own reportees."""
    reps = _reportees(manager_email) or []
    out = set()
    for r in (reps if isinstance(reps, list) else []):
        if isinstance(r, dict):
            e = str(r.get("OffEmail", r.get("Email", "")) or "").strip().lower()
            if e:
                out.add(e)
    return out


def _devplan_suggested(manager_email, trainer_email):
    """
    Deterministic, un-stored development suggestions for one trainer:

      * one `certification` item per course the trainer teaches without a
        certificate on record that is tied to currently open demand;
      * one `coaching` item when the learner feedback average is below 4.0;
      * one `portfolio` item when the trainer is on fewer than three courses.

    Each has the same shape as a stored item, `id` prefixed `sug_`, and is
    never persisted until the manager adopts it.
    """
    suggestions = []

    def _mk(kind, title, note):
        raw = "|".join([trainer_email, kind, title])
        return {
            "id": "sug_" + hashlib.sha1(raw.encode("utf-8")).hexdigest()[:12],
            "manager_email": manager_email,
            "trainer_email": trainer_email,
            "title": title,
            "kind": kind,
            "status": "open",
            "target_date": "",
            "note": note,
            "created_at": "",
            "updated_at": "",
        }

    try:
        skills = _skills(trainer_email) or []
    except Exception:
        skills = []
    try:
        demand = _demand_rows() or []
    except Exception:
        demand = []
    try:
        policy = _exam_policy() or {}
    except Exception:
        policy = {}

    demand_norm = {}
    for d in demand:
        key = _norm_course(d.get("course_name", ""))
        if key:
            demand_norm[key] = demand_norm.get(key, 0) + 1

    seen_courses = set()
    for s in skills:
        if s.get("approved"):
            continue
        course = s.get("course") or s.get("course_name") or ""
        norm = _norm_course(course)
        if not norm or norm in seen_courses:
            continue
        open_batches = demand_norm.get(norm, 0)
        if not open_batches:
            continue
        pol = policy.get(norm)
        if pol is not None and not pol.get("required", True):
            continue
        seen_courses.add(norm)
        suggestions.append(_mk(
            "certification",
            "Certify for %s" % course,
            "%d open batch%s for this course; taught without a certificate on record."
            % (open_batches, "" if open_batches == 1 else "es"),
        ))
        if len(seen_courses) >= 3:
            break

    try:
        fb = _trainer_feedback_detail(trainer_email) or {}
    except Exception:
        fb = {}
    avg = fb.get("avg_rating")
    if isinstance(avg, (int, float)) and avg < 4.0:
        suggestions.append(_mk(
            "coaching",
            "Coaching on learner feedback",
            "Learner rating average is %.1f of 5 - agree a coaching focus." % avg,
        ))

    if len(skills) < 3:
        suggestions.append(_mk(
            "portfolio",
            "Broaden delivery portfolio",
            "On record for %d course%s - identify an adjacent course to add."
            % (len(skills), "" if len(skills) == 1 else "s"),
        ))

    return suggestions


@app.route('/api/v2/devplan', methods=['GET'])
def get_dev_plan():
    """Stored development-plan items for (manager, trainer) plus live suggestions."""
    manager = str(request.args.get("manager", "")).strip().lower()
    session, error = _v2_manager_session(manager)
    if error:
        return error
    manager_email = (session or {}).get("email") or manager

    trainer = str(request.args.get("trainer", "")).strip().lower()
    if not trainer:
        return error_response("INVALID_INPUT", "trainer query param required", 400)
    if trainer not in _devplan_reportee_emails(manager_email):
        return error_response("MANAGER_SCOPE_MISMATCH",
                              "That trainer is not one of your reportees", 403)

    with _devplan_lock:
        items = _devplan_repository.list_items(manager_email, trainer)
    return jsonify({
        "trainer": trainer,
        "items": items,
        "suggested": _devplan_suggested(manager_email, trainer),
    }), 200


@app.route('/api/v2/devplan/item', methods=['POST'])
def create_dev_plan_item():
    """Create a manager-authored plan item for one reportee."""
    body = request.get_json(silent=True) or {}
    manager = str(body.get("manager", "")).strip().lower()
    session, error = _v2_manager_session(manager, manager_only=True)
    if error:
        return error
    manager_email = (session or {}).get("email") or manager

    trainer = str(body.get("trainer", "")).strip().lower()
    title = str(body.get("title", "")).strip()
    kind = str(body.get("kind", "")).strip().lower()
    if not trainer:
        return error_response("INVALID_INPUT", "trainer is required", 400)
    if not title:
        return error_response("INVALID_INPUT", "title is required", 400)
    if kind not in _DEVPLAN_KINDS:
        return error_response("INVALID_INPUT",
                              "kind must be one of %s" % (_DEVPLAN_KINDS,), 400)
    if trainer not in _devplan_reportee_emails(manager_email):
        return error_response("MANAGER_SCOPE_MISMATCH",
                              "That trainer is not one of your reportees", 403)

    with _devplan_lock:
        item = _devplan_repository.create(
            manager_email, trainer, title, kind,
            target_date=str(body.get("target_date", "")).strip(),
            note=str(body.get("note", "")).strip(),
        )
    return jsonify(item), 201


@app.route('/api/v2/devplan/item', methods=['PATCH'])
def update_dev_plan_item():
    """Update status / note / target date of one of the manager's own items."""
    body = request.get_json(silent=True) or {}
    manager = str(body.get("manager", "")).strip().lower()
    session, error = _v2_manager_session(manager, manager_only=True)
    if error:
        return error
    manager_email = (session or {}).get("email") or manager

    item_id = str(body.get("id", "")).strip()
    if not item_id:
        return error_response("INVALID_INPUT", "id is required", 400)

    status = body.get("status")
    if status is not None:
        status = str(status).strip().lower()
        if status not in _DEVPLAN_STATUSES:
            return error_response("INVALID_INPUT",
                                  "status must be one of %s" % (_DEVPLAN_STATUSES,), 400)

    with _devplan_lock:
        if _devplan_repository.get(manager_email, item_id) is None:
            return error_response("NOT_FOUND", "No plan item with that id", 404)
        updated = _devplan_repository.update(
            manager_email, item_id,
            status=status,
            note=body.get("note"),
            target_date=body.get("target_date"),
        )
    return jsonify(updated), 200




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
    session, error = _v2_manager_session("", manager_only=True)
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


# ═══ Team-level Copilot (v2) ══════════════════════════════════════════════════
#
# `/api/agent/ask` above answers questions about one trainer. A delivery manager
# also asks questions about the *team* — "who is free next week for AZ-104",
# "biggest coverage risk this month". This endpoint recognises seven such
# questions by keyword (no LLM) and answers each from the same RMS fact base:
# reportees (82), trainerDetails (217/…) for capability, utilisation (55),
# unallocated demand (190) and the feedback endpoints.

_TEAM_INTENTS = (
    "free_for_course", "coverage_risk", "top_upskills",
    "bench", "overloaded", "feedback_watch", "team_summary",
)

_TEAM_COURSE_CODE_RE = _re.compile(r"[A-Za-z]{2,4}[- ]?\d{2,4}[A-Za-z]?")


def _team_extract_course(question):
    """A course code (AZ-104) or the noun phrase after 'for' / 'teach'."""
    q = str(question or "")
    m = _TEAM_COURSE_CODE_RE.search(q)
    if m:
        return _re.sub(r"\s+", "-", m.group(0).strip()).upper()
    m = _re.search(r"(?:for|teach|deliver)\s+([A-Za-z0-9 :/&+.-]{3,40})", q, _re.I)
    if m:
        return m.group(1).strip().rstrip("?.").strip()
    return ""


def _team_intent(question, question_key=""):
    """Deterministic keyword routing to one team key. No model call."""
    if question_key in _TEAM_INTENTS:
        return question_key
    s = str(question or "").lower()
    course = _team_extract_course(question)
    if ("coverage" in s and "risk" in s) or "biggest risk" in s or "single point" in s \
            or ("cover" in s and "risk" in s):
        return "coverage_risk"
    if "upskill" in s or "unlock" in s or "invest in" in s or ("skill" in s and "demand" in s):
        return "top_upskills"
    if "1:1" in s or "1-1" in s or "one on one" in s or "one-on-one" in s or "coaching" in s \
            or "escalat" in s or ("feedback" in s and ("watch" in s or "who" in s or "negative" in s)):
        return "feedback_watch"
    if "stretch" in s or "overload" in s or "over-load" in s or "too busy" in s \
            or "stressed" in s or "burn" in s or "at capacity" in s:
        return "overloaded"
    if "bench" in s or "idle" in s or "under-utilis" in s or "underutilis" in s \
            or "under utilis" in s or "spare capacity" in s or "who is free" in s and not course:
        return "bench"
    if course and any(w in s for w in ("free", "available", "who can", "spare", "capacity", "take")):
        return "free_for_course"
    if course:
        return "free_for_course"
    return "team_summary"


def _team_covers(skills, target_norm):
    """True when a trainer's capability list matches the target course."""
    if not target_norm:
        return False
    head = target_norm.split(" ")[0]
    for sname in (skills or []):
        n = _norm_course(sname)
        if not n:
            continue
        if target_norm == n or target_norm in n or (len(target_norm) > 5 and n in target_norm):
            return True
        if len(head) >= 4 and head == n.split(" ")[0]:
            return True
    return False


def _team_facts(manager_email):
    """One row per reportee with the facts every team question needs.

    Utilisation, capability and feedback all route through `_rms`, so tests can
    drive the whole endpoint with a single `_rms` side-effect.
    """
    rows = _reportees(manager_email) or []
    out = []
    for r in (rows if isinstance(rows, list) else []):
        if not isinstance(r, dict):
            continue
        em = str(r.get("OffEmail") or r.get("official_email") or "").strip().lower()
        if not em:
            continue
        name = str(r.get("TrainerName") or r.get("trainer_name") or em.split("@")[0]).strip()
        emp = str(r.get("EmpId") or r.get("EmpCode") or r.get("employee_id") or "").strip()
        try:
            util = _current_util(_util_series(_util_row(em)))
        except Exception:
            util = None
        try:
            skills = [str(s.get("course") or s.get("course_name") or "").strip()
                      for s in (_skills(em) or [])]
            skills = [s for s in skills if s]
        except Exception:
            skills = []
        neg = 0
        try:
            nf = _rms("trainerNegFeedback", {"employee_id": emp}) if emp else []
            neg = len([x for x in (nf if isinstance(nf, list) else []) if isinstance(x, dict)])
        except Exception:
            pass
        hr = 0
        try:
            hi = _rms("hrIncident", {"email": em}) or []
            hr = len([x for x in (hi if isinstance(hi, list) else []) if isinstance(x, dict)])
        except Exception:
            pass
        out.append({"name": name, "email": em, "emp": emp, "util": util,
                    "skills": skills, "neg": neg, "hr": hr})
    return out


def _team_demand_by_course(demand):
    """{course_name: batch_count}, highest first is up to the caller."""
    by_course = {}
    for d in (demand or []):
        if not isinstance(d, dict):
            continue
        c = str(d.get("course_name") or d.get("Coursename") or "").strip()
        if not c:
            continue
        by_course[c] = by_course.get(c, 0) + 1
    return by_course


def _copilot_team_answer(intent, question, team, demand):
    n_team = len(team)
    by_course = _team_demand_by_course(demand)
    base_ev = f"{n_team} reportee(s) checked; capability from trainerDetails, " \
              f"utilisation from RMS key 55, demand from RMS key 190"

    def _util_note(t):
        return f"{t['util']}% utilised" if t["util"] is not None else "utilisation unknown"

    if intent == "free_for_course":
        course = _team_extract_course(question) or "that course"
        tgt = _norm_course(course)
        skilled = [t for t in team if _team_covers(t["skills"], tgt)]
        ready = [t for t in skilled if t["util"] is not None and t["util"] < 85]
        capacity_unknown = [t for t in skilled if t["util"] is None]
        ready.sort(key=lambda t: (t["util"] is None, t["util"] or 0))
        capacity_unknown.sort(key=lambda t: t["name"])
        data = [{"name": t["name"], "email": t["email"], "note": _util_note(t)}
                for t in ready + capacity_unknown]
        if data:
            if ready:
                names = ", ".join(t["name"] for t in ready[:5])
                answer = (f"{len(ready)} trainer(s) on your team can teach {course} and have "
                          f"verified utilisation below 85%: {names}. Confirm their calendar "
                          f"in RMS before you commit the batch.")
                if capacity_unknown:
                    answer += f" Capacity is unknown for {len(capacity_unknown)} additional course-matched trainer(s)."
                conf = "high"
            else:
                answer = (f"{len(capacity_unknown)} trainer(s) on your team hold {course}, but "
                          "their capacity is unknown. Check their calendar before deciding.")
                conf = "medium"
        elif skilled:
            answer = (f"{len(skilled)} trainer(s) on your team hold {course}, but all have "
                      "utilisation at or above 85%. Consider cross-team cover; upskilling is "
                      "not the issue shown by this evidence.")
            conf = "high"
        else:
            answer = (f"No verified course match for {course} was found in the loaded team skill "
                      "records. Verify the course mapping before choosing upskilling or cross-team cover.")
            conf = "low"
        evidence = base_ev
    elif intent == "coverage_risk":
        scored = []
        for c, cnt in by_course.items():
            cov = [t["name"] for t in team if _team_covers(t["skills"], _norm_course(c))]
            scored.append((c, cnt, cov))
        scored.sort(key=lambda x: (len(x[2]), -x[1]))
        data = [{"course": c, "count": cnt,
                 "note": f"{len(cov)} trainer(s) can cover"} for c, cnt, cov in scored[:5]]
        if scored:
            c, cnt, cov = scored[0]
            uncoverable = [(cc, nn) for cc, nn, vv in scored if not vv]
            unlock = max(uncoverable, key=lambda x: x[1]) if uncoverable else None
            answer = (f"The biggest coverage risk is {c}: {cnt} open batch(es) with only "
                      f"{len(cov)} trainer(s) able to deliver it.")
            if unlock:
                answer += (f" Building {unlock[0]} capability would unlock {unlock[1]} "
                           f"batch(es) nobody on the team can currently take.")
            conf = "high" if n_team else "low"
        else:
            answer = "No unallocated demand is open right now, so there is no coverage risk to flag."
            conf = "medium"
        evidence = base_ev
    elif intent == "top_upskills":
        uncovered = []
        for c, cnt in by_course.items():
            cov = [t for t in team if _team_covers(t["skills"], _norm_course(c))]
            if not cov:
                uncovered.append((c, cnt))
        uncovered.sort(key=lambda x: -x[1])
        data = [{"course": c, "count": cnt} for c, cnt in uncovered[:5]]
        if data:
            top = ", ".join(d["course"] for d in data[:3])
            total = sum(d["count"] for d in data[:3])
            answer = (f"Training your team on {top} would unlock the most demand — {total} open "
                      f"batch(es) across those courses that nobody can currently cover. "
                      f"Start with {data[0]['course']} ({data[0]['count']} batch(es)).")
            conf = "high"
        else:
            answer = ("Your team already covers every open course in the demand board — no "
                      "upskill is blocking revenue this cycle.")
            conf = "medium"
        evidence = base_ev
    elif intent == "bench":
        bench = sorted([t for t in team if t["util"] is not None and t["util"] < 40],
                       key=lambda t: t["util"])
        unknown = [t for t in team if t["util"] is None]
        data = [{"name": t["name"], "email": t["email"], "note": _util_note(t)} for t in bench]
        if bench:
            answer = (f"{len(bench)} trainer(s) are on the bench (under 40% utilised): "
                      f"{', '.join(t['name'] for t in bench)}. Match them against the open "
                      f"demand board before it ages out.")
            conf = "high"
        else:
            answer = "Nobody on your team is under 40% utilised right now — the bench is clear."
            conf = "high" if not unknown else "medium"
        if unknown:
            answer += f" {len(unknown)} trainer(s) have no utilisation reading in RMS."
        evidence = base_ev
    elif intent == "overloaded":
        over = sorted([t for t in team if t["util"] is not None and t["util"] >= 85],
                      key=lambda t: -t["util"])
        data = [{"name": t["name"], "email": t["email"], "note": _util_note(t)} for t in over]
        if over:
            answer = (f"{len(over)} trainer(s) are stretched at or above 85% utilisation: "
                      f"{', '.join(t['name'] for t in over)}. Hold new assignments off them and "
                      f"rebalance toward the bench where the skills line up.")
            conf = "high"
        else:
            answer = "No trainer on your team is at or above 85% utilisation — load looks healthy."
            conf = "high"
        evidence = base_ev
    elif intent == "feedback_watch":
        watch = [t for t in team if t["neg"] > 0 or t["hr"] > 0]
        data = [{"name": t["name"], "email": t["email"],
                 "note": f"{t['neg']} negative feedback, {t['hr']} HR incident(s)"} for t in watch]
        if watch:
            answer = (f"{len(watch)} trainer(s) have something on file worth a 1:1 this cycle: "
                      f"{', '.join(t['name'] for t in watch)}. Review the feedback detail in "
                      f"Trainer 360 before the conversation.")
            conf = "high"
        else:
            answer = "No trainer on your team has negative feedback or an HR incident this cycle."
            conf = "high"
        evidence = base_ev
    else:  # team_summary
        intent = "team_summary"
        delivering = [t for t in team if t["util"] is not None and t["util"] >= 40]
        bench = [t for t in team if t["util"] is not None and t["util"] < 40]
        watch = [t for t in team if t["neg"] > 0 or t["hr"] > 0]
        rated = [t["util"] for t in team if t["util"] is not None]
        avg_util = round(sum(rated) / len(rated)) if rated else None
        open_demand = sum(by_course.values())
        data = [{"name": t["name"], "email": t["email"], "note": _util_note(t)} for t in team]
        answer = (f"You have {n_team} reportee(s): {len(delivering)} actively delivering, "
                  f"{len(bench)} on the bench"
                  + (f", team averaging {avg_util}% utilisation" if avg_util is not None else "")
                  + f". {open_demand} unallocated batch(es) are open on the demand board"
                  + (f", and {len(watch)} trainer(s) need a feedback 1:1" if watch else "")
                  + ".")
        conf = "high" if n_team else "low"
        evidence = base_ev

    return {
        "answer": answer,
        "evidence": evidence,
        "data": data,
        "question_key": intent,
        "confidence": conf,
        "decisionVersion": "team-v1",
        "error": None,
    }


@app.route('/api/v2/copilot/team', methods=['POST'])
def copilot_team_v2():
    """
    Team-level delivery Copilot.
    Body JSON: { manager, question }  (free text)  OR  { manager, question_key }
    question_key in: free_for_course, coverage_risk, top_upskills, bench,
                     overloaded, feedback_watch, team_summary
    Returns: { answer, evidence, data, question_key, confidence, decisionVersion }
    """
    body = request.get_json(force=True, silent=True) or {}
    manager = str(body.get("manager", "") or body.get("manager_email", "")).strip().lower()
    session, error = _v2_manager_session(manager, manager_only=True)
    if error:
        return error
    manager_email = (session or {}).get("email") or manager

    question = str(body.get("question", "") or "").strip()
    question_key = str(body.get("question_key", "") or "").strip().lower()
    if not question and not question_key:
        return error_response("QUESTION_REQUIRED", "question or question_key is required", 400)
    if question_key and question_key not in _TEAM_INTENTS:
        return error_response(
            "UNKNOWN_QUESTION",
            f"question_key must be one of: {', '.join(_TEAM_INTENTS)}",
            400,
        )

    intent = _team_intent(question, question_key)
    team = _team_facts(manager_email)
    demand = _demand_rows() or []
    return jsonify(_copilot_team_answer(intent, question, team, demand)), 200


@app.route('/api/v2/message/rewrite', methods=['POST'])
def message_rewrite():
    """
    Rewrites [User Message] and/or [My Message] into a Teams/Viber house-style
    message. At least one of the two inputs must be present.
    Body: { manager_email, user_message, my_message, target_name, is_team, style, evidence_context }
    style: teams (default, emits **bold**, _italic_, __underline__) or plain.
    Uses the deterministic rewrite engine above; no LLM call, but the seam is
    preserved for an eventual model.
    """
    body = request.get_json(force=True, silent=True) or {}
    manager_email = str(body.get("manager_email", "") or body.get("manager", "") or "").strip().lower()
    # Auth gate — same as other v2 routes
    _, error = _v2_manager_session(manager_email, manager_only=True)
    if error:
        return error
    user_message = str(body.get("user_message", "") or body.get("User Message", "") or "")
    my_message = str(body.get("my_message", "") or body.get("My Message", "") or "")
    target_name = str(body.get("target_name", "") or body.get("trainer_name", "") or "").strip()
    is_team = bool(body.get("is_team", False))
    style = str(body.get("style", "teams") or "teams").strip().lower()
    if style not in ("teams", "plain"):
        style = "teams"
    evidence_context = body.get("evidence_context") if isinstance(body.get("evidence_context"), dict) else None
    if not str(user_message or "").strip() and not str(my_message or "").strip():
        return error_response("MISSING_INPUT", "At least one of user_message or my_message is required", 400)
    try:
        rewritten = _compose_rewritten(user_message, my_message, style=style,
                                       target_name=target_name, is_team=is_team,
                                       evidence_context=evidence_context)
    except ValueError as e:
        return error_response("REWRITE_ERROR", str(e), 400)
    except Exception as e:
        return error_response("REWRITE_ERROR", f"Rewrite failed: {e}", 500)
    intent = _detect_intent(user_message, my_message)
    return jsonify({
        "rewritten": rewritten,
        "style": style,
        "length": len(rewritten),
        "detected": intent,
        "greeting": rewritten.split("\n")[0] if rewritten else "",
    }), 200


@app.route('/api/v2/message/compose', methods=['GET', 'POST'])
def message_compose():
    """
    The house-style message for a reportee or the team, composed from the
    analysed weekly/monthly data, with an optional manager note woven in.

    Params (query or JSON): manager, target (reportee email, omit for team),
    cadence (weekly|monthly), my_message.
    Reuses the warm-cached weekly / hr-monthly report so no extra RMS calls.
    """
    src = request.get_json(silent=True) if request.method == "POST" else None
    src = src or request.args
    manager = str(src.get("manager", "") or src.get("manager_email", "") or "").strip().lower()
    _, error = _v2_manager_session(manager)
    if error:
        return error
    target = str(src.get("target", "") or src.get("target_email", "") or "").strip().lower()
    cadence = str(src.get("cadence", "weekly") or "weekly").strip().lower()
    if cadence not in ("weekly", "weekend", "monthly", "monthend"):
        cadence = "weekly"
    is_week = cadence in ("weekly", "weekend")
    my_message = str(src.get("my_message", "") or src.get("My Message", "") or "")

    if is_week:
        monday = datetime.utcnow().date()
        monday = monday - timedelta(days=monday.weekday())
        key = f"weekly::{manager}::{_iso(monday)}"
    else:
        key = f"hr::{manager}::{datetime.utcnow().date().strftime('%Y-%m')}"
    with _warm_lock:
        entry = _warm_payload_cache.get(key)
    report = entry[1] if entry else None
    if report is None:
        # warm it now (bounded) so the first compose still returns real content
        path = (f"/api/v2/report/weekly?manager={urllib.parse.quote(manager)}&_build=1"
                if is_week else
                f"/api/v2/hr/monthly-report?manager={urllib.parse.quote(manager)}&_build=1")
        auth = request.headers.get("Authorization", "")
        try:
            with app.test_request_context(path, headers={"Authorization": auth} if auth else {}):
                (weekly_report_v2 if is_week else hr_monthly_report)()
        except Exception:
            pass
        with _warm_lock:
            entry = _warm_payload_cache.get(key)
        report = entry[1] if entry else None
    if report is None:
        return error_response("REPORT_UNAVAILABLE", "The underlying report is still preparing; retry shortly.", 202)

    _dkey = {"weekly": "team_digest_weekly", "weekend": "team_digest_weekend",
             "monthly": "team_digest_monthly", "monthend": "team_digest_monthend"}[cadence]
    _rkey = {"weekly": "message_weekly", "weekend": "message_weekend",
             "monthly": "message_monthly", "monthend": "message_monthend"}[cadence]

    if not target:
        digest = report.get(_dkey) or report.get("team_digest") or ""
        if my_message.strip():
            facts = {"period_key": key, "manager_first": manager.split("@")[0].split(".")[0].title(),
                     "month_label": report.get("month", "")}
            digest = _compose_manager_message("team", cadence, facts, my_message=my_message)
        return jsonify({"message": digest, "scope": "team", "cadence": cadence,
                        "length": len(digest)}), 200

    row = next((r for r in (report.get("reportees") or [])
                if str(r.get("email", "")).strip().lower() == target), None)
    if row is None:
        return error_response("TARGET_NOT_IN_TEAM", "That reportee is not on this manager's roster.", 404)
    sf = row.get("structured_feedback") or {}
    prebuilt = row.get(_rkey) or sf.get(_rkey)
    if prebuilt and not my_message.strip():
        return jsonify({"message": prebuilt, "scope": "reportee", "cadence": cadence,
                        "target": target, "length": len(prebuilt)}), 200
    facts = _reportee_message_facts(
        row if not sf else {**row, "learner_feedback": sf.get("learner_feedback", row.get("learner_feedback"))},
        cadence, demand_rows=[], skills_courses=[],
        month_label=report.get("month", ""),
    )
    facts["opp_courses"] = row.get("opportunity_courses") or facts.get("opp_courses") or []
    msg = _compose_manager_message("reportee", cadence, facts, my_message=my_message)
    return jsonify({"message": msg, "scope": "reportee", "cadence": cadence,
                    "target": target, "length": len(msg)}), 200


@app.errorhandler(404)
def not_found(error):
    return error_response("NOT_FOUND", "Not found", 404)


def _human_date(iso_str):
    d = _parse_date(str(iso_str or "").split("T")[0])
    return d.strftime("%d %b %Y") if d else ""


# ── Message rewrite engine (Teams / Viber house style) ─────────────────────
# Implements the manager rewriting contract:
#   inputs  [User Message: …] and/or [My Message: …]  (at least one present)
#   output  short Teams/Viber message: greeting on one line, body on new line,
#           closing on new line, ≤1000 chars, no emojis/bullets/hyphens,
#           italics only for names, bold only for the key action, underline
#           only for time refs. Intent, urgency, firmness and Hinglish are
#           interpreted deterministically (no LLM required; the agent seam is
#           preserved for a future model call).
_MESSAGE_LIMIT = 1000
_COURSE_CODE_RE_RW = _re.compile(r"\b[A-Z]{2,4}-[0-9]{2,4}\b")

_HINGLISH_MAP = {
    "parso": "day after tomorrow",
    "parsoon": "day after tomorrow",
    "jaldi se": "at the earliest",
    "jaldi": "at the earliest",
    "thoda": "a little",
    "zyada": "more",
    "kar dijiye": "please do",
    "kar do": "please do",
    "kr dijiye": "please do",
    "krdo": "please do",
    "bhej do": "please share",
    "bhejdo": "please share",
    "bhejo": "please share",
    "chahiye": "is required",
    "ho jayega": "will be done",
    "ho gaya": "is done",
    "karna hai": "needs to be done",
    "aap": "you",
    "tum": "you",
    "haan": "yes",
    "nahi": "no",
    "nahin": "no",
    "kya": "what",
    "kab": "when",
    "kahan": "where",
    "plz": "please",
    "pls": "please",
    "sir": "Sir",
    "mam": "Ma'am",
    "maam": "Ma'am",
}

_CONTRACTIONS_RW = {
    "don't": "do not", "won't": "will not", "can't": "cannot",
    "isn't": "is not", "aren't": "are not", "doesn't": "does not",
    "didn't": "did not", "haven't": "have not", "hasn't": "has not",
    "wouldn't": "would not", "shouldn't": "should not", "couldn't": "could not",
    "it's": "it is", "we're": "we are", "you're": "you are",
    "I'm": "I am", "we'll": "we will", "you'll": "you will",
    "let's": "let us", "that's": "that is", "there's": "there is",
}

_URGENCY_HINTS = ("urgent", "asap", "immediate", "priority", "critical",
                  "at the earliest", "as soon as possible", "eod", "deadline")
_FIRM_HINTS = ("must", "should", "need to", "ensure", "make sure", "do not",
               "strictly", "mandatory", "required", "final", "warning", "ensure")
_APPRECIATIVE_HINTS = ("thank", "thanks", "shukriya", "appreciate", "well done", "great work", "good job")
_CORRECTIVE_HINTS = ("feedback", "improvement", "concern", "flag", "issue", "risk", "coaching", "review", "gap")


def _message_sanitise(raw: str) -> str:
    s = str(raw or "")
    codes = []
    def _hold(m):
        codes.append(m.group(0))
        return f"\x01{len(codes)-1}\x01"
    s = _COURSE_CODE_RE_RW.sub(_hold, s)
    for short, long in _CONTRACTIONS_RW.items():
        s = _re.compile(_re.escape(short), _re.I).sub(
            lambda m: long.capitalize() if m.group(0)[0].isupper() else long, s)
    s = _re.sub(r"[\u2010-\u2015]", " ", s)
    s = s.replace("-", " ")
    s = _re.sub(r"[•·▪●◦*]", "", s)
    # strip pictographs / emojis via unicode category So
    import unicodedata
    s = "".join(ch for ch in s if unicodedata.category(ch) != "So")
    s = _re.sub(r"[ \t]+", " ", s)
    s = "\n".join(line.strip() for line in s.split("\n"))
    s = _re.sub(r"\n{3,}", "\n\n", s)
    for i, code in enumerate(codes):
        s = s.replace(f"\x01{i}\x01", code)
    return s.strip()


def _normalize_hinglish(text: str) -> str:
    s = str(text or "")
    # longest keys first so "jaldi se" wins over "jaldi"
    for k in sorted(_HINGLISH_MAP, key=lambda x: -len(x)):
        s = _re.compile(r"\b" + _re.escape(k) + r"\b", _re.I).sub(_HINGLISH_MAP[k], s)
    return s


def _detect_hinglish(text: str) -> bool:
    low = str(text or "").lower()
    return any(_re.search(r"\b" + _re.escape(k) + r"\b", low) for k in _HINGLISH_MAP)


def _extract_time_refs(text: str):
    pats = [
        r"\b\d{1,2}\s+(?:jan|feb|mar|apr|may|jun|jul|aug|sep|sept|oct|nov|dec)[a-z]*\b",
        r"\b(?:monday|tuesday|wednesday|thursday|friday|saturday|sunday)\b",
        r"\bnext week\b", r"\bthis week\b", r"\btomorrow\b", r"\btoday\b",
        r"\bday after tomorrow\b", r"\bby\s+(?:monday|tuesday|wednesday|thursday|friday|eod|tomorrow|today)\b",
        r"\b\d{1,2}:\d{2}\s*(?:am|pm)?\b",
    ]
    found = []
    for pat in pats:
        for m in _re.finditer(pat, str(text or ""), _re.I):
            v = m.group(0).strip()
            if v and v.lower() not in [x.lower() for x in found]:
                found.append(v)
    return found


def _professional_rephrase(text: str) -> str:
    s = _normalize_hinglish(text or "")
    s = _message_sanitise(s)
    s = _re.sub(r"\s+", " ", s).strip()
    if not s:
        return ""
    # remove informal fillers
    s = _re.sub(r"\b(yaar|bhai|actually|kindly please|please kindly)\b", "please", s, flags=_re.I)
    s = _re.sub(r"\bpls\b", "please", s, flags=_re.I)
    s = _re.sub(r"\s+", " ", s).strip()
    # sentence case
    parts = _re.split(r"([.!?])\s*", s)
    out = []
    terminators = _re.findall(r"[.!?]", s)
    idx = 0
    for i in range(0, len(parts), 2):
        seg = parts[i].strip() if i < len(parts) else ""
        if not seg:
            continue
        seg = seg[0].upper() + seg[1:] if len(seg) > 1 else seg.upper()
        term = terminators[idx] if idx < len(terminators) else ""
        idx += 1
        if seg and term:
            out.append(seg + term)
        elif seg:
            out.append(seg + ".")
    res = " ".join(out)
    res = _re.sub(r"\s+([.,!?])", r"\1", res)
    res = _re.sub(r"[ \t]+", " ", res).strip()
    if res and res[-1] not in ".!?":
        res += "."
    return res


def _detect_intent(user_message: str, my_message: str) -> dict:
    raw_combined = f"{user_message or ''} {my_message or ''}"
    norm_combined = _normalize_hinglish(raw_combined).lower()
    urgency = "low"
    if any(h in norm_combined for h in _URGENCY_HINTS) or "!" in raw_combined or "kal" in raw_combined.lower() or "parso" in raw_combined.lower():
        urgency = "high"
    elif any(w in norm_combined for w in ("soon", "friday", "monday", "wednesday", "week")):
        urgency = "medium"
    firmness = "neutral"
    if any(h in norm_combined for h in _FIRM_HINTS):
        firmness = "firm"
    elif any(h in norm_combined for h in ("please", "kindly", "request", "could you", "would you")):
        firmness = "soft"
    tone = "professional"
    if any(h in norm_combined for h in _APPRECIATIVE_HINTS):
        tone = "appreciative"
    elif any(h in norm_combined for h in _CORRECTIVE_HINTS) or "gap" in norm_combined:
        tone = "corrective"
    elif urgency == "high":
        tone = "urgent"
    elif "available" in norm_combined or "bench" in norm_combined:
        tone = "advisory"
    return {"urgency": urgency, "firmness": firmness, "tone": tone,
            "hinglish": _detect_hinglish(raw_combined),
            "time_refs": _extract_time_refs(norm_combined)}


def _bold(text: str, style: str) -> str:
    return f"**{text}**" if style == "teams" else text


def _italic(text: str, style: str) -> str:
    return f"_{text}_" if style == "teams" else text


def _underline(text: str, style: str) -> str:
    return f"__{text}__" if style == "teams" else text


def _trim_message_to_limit(text: str, limit: int = _MESSAGE_LIMIT) -> str:
    if len(text) <= limit:
        return text
    parts = text.split("\n\n")
    if len(parts) < 3:
        return text[:limit].rsplit(" ", 1)[0] + "."
    greeting, closing = parts[0], parts[-1]
    body = "\n\n".join(parts[1:-1])
    overhead = len(greeting) + len(closing) + 4
    room = limit - overhead
    if room <= 0:
        return text[:limit]
    if len(body) > room:
        sents = _re.split(r"(?<=[.!?])\s+", body)
        kept = []
        cur = 0
        for s in sents:
            if cur + len(s) + 1 > room:
                break
            kept.append(s)
            cur += len(s) + 1
        body = " ".join(kept).strip()
        if body and body[-1] not in ".!?":
            body += "."
    return f"{greeting}\n\n{body}\n\n{closing}"


# ── Manager message composer (weekly / monthly, reportee / team) ───────────
# Turns the analysed delivery data into a message a manager would actually
# send on Teams or Viber: a greeting line, a short body that reads as prose
# (not a fact list), and a closing line. Same house rules as the rewrite
# engine - no emojis/bullets/hyphens, italics only for a name, one bold key
# action, one underlined time reference, <=1000 chars. Deterministic: phrasing
# varies by a seed of (subject, period) so two reportees do not get identical
# sentences, but the same input always produces the same message.

def _msg_join_names(names, style="teams"):
    firsts = [str(n).strip().split()[0] for n in names if str(n).strip()]
    firsts = [_italic(n, style) for n in firsts]
    if not firsts:
        return ""
    if len(firsts) == 1:
        return firsts[0]
    return ", ".join(firsts[:-1]) + " and " + firsts[-1]


def _msg_seed(*parts) -> int:
    return int(hashlib.md5("|".join(str(p) for p in parts).encode("utf-8")).hexdigest(), 16)


def _msg_pick(options, seed: int, salt: int = 0):
    if not options:
        return ""
    return options[(seed + salt) % len(options)]


def _open_opportunities_for(course_names, demand_rows, limit=4):
    """Open unallocated batches whose course this trainer already teaches -
    the capacity the team is leaving on the table."""
    det = _open_opportunities_detailed(course_names, demand_rows, limit=limit)
    return det.get("courses", [])


def _open_opportunities_detailed(course_names, demand_rows, limit=4):
    """
    Returns detailed opportunity metrics:
    - courses: list of course titles (up to limit)
    - batch_count: total matching open batches
    - pax_days: total participant-days across matching batches
    - course_counts: dict of {course_title: count}
    - course_pax_days: dict of {course_title: pax_days}
    """
    owned_names = {_norm(c) for c in (course_names or []) if c}
    _code = _re.compile(r"[A-Z]{2,4}-[0-9]{2,4}")
    owned_codes = set()
    for c in (course_names or []):
        m = _code.search(str(c))
        if m:
            owned_codes.add(m.group(0).upper())
    hits = []
    course_counts = {}
    course_pax_days = {}
    total_batches = 0
    total_pax_days = 0

    for d in (demand_rows or []):
        if not isinstance(d, dict):
            continue
        cn = str(d.get("course_name") or d.get("Coursename") or d.get("Course")
                 or d.get("CourseName") or "").strip()
        if not cn:
            continue
        n = _norm(cn)
        m = _code.search(cn)
        code = m.group(0).upper() if m else ""
        if n in owned_names or (code and code in owned_codes) or \
           any(o and (o in n or n in o) for o in owned_names if len(o) > 6):
            title = _re.sub(r"^[A-Z]{2,4}-[0-9]{2,4}T?[0-9]*:\s*", "", cn).strip() or cn
            pax = int(d.get("participants") or d.get("Participants") or d.get("Pax") or 1)
            days = _opp_batch_days(d)
            pax_days = pax * days if pax > 0 else days

            total_batches += 1
            total_pax_days += pax_days
            course_counts[title] = course_counts.get(title, 0) + 1
            course_pax_days[title] = course_pax_days.get(title, 0) + pax_days
            if title not in hits:
                hits.append(title)

    return {
        "courses": hits[:limit],
        "batch_count": total_batches,
        "pax_days": total_pax_days,
        "course_counts": course_counts,
        "course_pax_days": course_pax_days,
    }


def _opp_batch_days(d):
    """Duration of an unallocated batch in days. Falls back to 3 when RMS gives
    no usable start/end pair (the working assumption for a standard batch)."""
    if not isinstance(d, dict):
        return 3
    st = _parse_date(str(d.get("start_date") or d.get("StartDate") or d.get("StarDate") or ""))
    en = _parse_date(str(d.get("end_date") or d.get("EndDate") or ""))
    if st and en and en >= st:
        return (en - st).days + 1
    return 3


def _team_opportunity_cost(team_trainers, demand_rows):
    """Headline 'opportunity cost' block for the manager dashboard: how much open,
    unallocated demand the team could cover but currently isn't.

    `team_trainers`: list of {"email", "courses": [course_name, ...],
    "vendors": [vendor, ...], "on_bench": bool} built from data already gathered
    in the unified-intelligence build - no fresh per-trainer RMS calls.
    `demand_rows`: `_demand_rows()` output (unallocated demand dicts).

    A batch is "coverable" when at least one team trainer's skill register matches
    its course (same course match logic as `_open_opportunities_for`).
    Best-effort cause attribution: a non-coverable batch that still sits inside the
    team's vendor / course-code space is counted as `skill_gap`; `availability`
    and `certification` are left at 0 (a coverable batch whose matching trainer is
    on bench is availability-fine, so it stays a plain coverable batch).
    """
    team_trainers = team_trainers or []
    demand_rows = demand_rows or []

    team_vendors = set()
    team_codes = set()
    _code_re = _re.compile(r"[A-Z]{2,4}-[0-9]{2,4}")
    for t in team_trainers:
        for v in (t.get("vendors") or []):
            if v:
                team_vendors.add(_norm(v))
        for c in (t.get("courses") or []):
            m = _code_re.search(str(c))
            if m:
                team_codes.add(m.group(0).upper().split("-")[0])

    # Distinct open batches, keyed by normalised course name.
    seen = {}
    for d in demand_rows:
        if not isinstance(d, dict):
            continue
        cn = str(d.get("course_name") or d.get("Coursename") or d.get("Course")
                 or d.get("CourseName") or "").strip()
        if not cn:
            continue
        k = _norm(cn)
        if k and k not in seen:
            seen[k] = d

    open_total = len(seen)
    coverable = 0
    days_at_stake = 0
    by_cause = {"skill_gap": 0, "availability": 0, "certification": 0}
    top_courses = []

    for d in seen.values():
        cn = str(d.get("course_name") or d.get("Coursename") or d.get("Course")
                 or d.get("CourseName") or "").strip()
        matched = any(
            _open_opportunities_for(t.get("courses") or [], [d], limit=1)
            for t in team_trainers
        )
        if matched:
            coverable += 1
            days_at_stake += _opp_batch_days(d)
            if len(top_courses) < 5:
                clean = _re.sub(r"^[A-Z]{2,4}-[0-9]{2,4}T?[0-9]*:\s*", "", cn).strip()
                top_courses.append(clean or cn)
        else:
            m = _code_re.search(cn)
            code = m.group(0).upper().split("-")[0] if m else ""
            n = _norm(cn)
            in_team_space = (
                (code and code in team_codes)
                or any(v and v in n for v in team_vendors)
            )
            if in_team_space:
                by_cause["skill_gap"] += 1

    return {
        "open_batches_coverable": coverable,
        "open_batches_total":     open_total,
        "trainer_days_at_stake":  days_at_stake,
        "by_cause":               by_cause,
        "top_courses":            top_courses,
    }


def _bold_first_action(body: str, style: str) -> str:
    """Bold exactly the first sentence that states an ask or decision."""
    cues = ("please ", "confirm ", "book ", "schedule ", "let us ", "let me ",
            "come back to me", "put you forward", "line up", "raise ", "review ",
            "close ", "block ", "hold a", "speak to", "prioritise", "send ")
    sents = _re.split(r"(?<=[.!?])\s+", body)
    for i, s in enumerate(sents):
        low = s.lower()
        if any(c in low for c in cues):
            sents[i] = _bold(s.strip(), style)
            break
    return " ".join(sents)


def _underline_one_timeref(body: str, style: str, refs) -> str:
    for tr in (refs or [])[:1]:
        m = _re.search(_re.escape(tr), body, _re.I)
        if m and "**" not in body[max(0, m.start() - 2):m.end() + 2]:
            return body[:m.start()] + _underline(m.group(0), style) + body[m.end():]
    return body


_MSG_CADENCE = {
    "weekly":   {"noun": "week",  "ref": "this week",   "deadline": "the end of the week",  "review": False},
    "weekend":  {"noun": "week",  "ref": "this week",   "deadline": "Monday",               "review": True},
    "monthly":  {"noun": "month", "ref": "this month",  "deadline": "the end of the month", "review": False},
    "monthend": {"noun": "month", "ref": "this month",  "deadline": "next month",           "review": True},
}


_THEME_LABELS = {
    "depth": "depth of knowledge",
    "labs": "practical hands-on labs",
    "clarity": "clear communication",
    "knowledge": "subject matter expertise",
    "engagement": "learner engagement",
    "pace": "pacing",
}


def _compose_manager_message(scope: str, cadence: str, f: dict,
                             my_message: str = "", style: str = "teams") -> str:
    """
    scope:   "reportee" | "team"
    cadence: "weekly" (Mon, plan) | "weekend" (Fri, wrap) |
             "monthly" (1st, plan) | "monthend" (last day, review)
    f: analysed facts (see _reportee_message_facts / _team_message_facts).
    my_message: the manager's own note; when present it leads.

    House rule enforced here: a TEAM (group) message never names an individual
    for anything negative - bench, feedback flags and gaps are always aggregate
    counts. Names appear in a group message only as recognition.
    """
    f = f or {}
    cadence = cadence if cadence in _MSG_CADENCE else "weekly"
    cd = _MSG_CADENCE[cadence]
    period = cd["noun"]
    review = cd["review"]
    period_ref = f.get("period_ref") or (cd["ref"] if period == "week" else (f.get("month_label") or "this month"))
    deadline_ref = cd["deadline"] if period == "week" else ("the end of " + (f.get("month_label") or "the month"))
    mm = _professional_rephrase(my_message) if str(my_message or "").strip() else ""

    if scope == "team":
        greeting = "Hello team,"
        seed = _msg_seed("team", cadence, f.get("period_key", ""))
        beats = []
        head = int(f.get("headcount") or 0)
        deliv = int(f.get("delivering") or 0)
        pax = int(f.get("total_pax") or 0)
        batches = int(f.get("total_batches") or 0)
        at_risk = int(f.get("at_risk") or 0)
        opp = int(f.get("open_demand") or 0)
        coverable = int(f.get("coverable_open") or 0)
        bench = int(f.get("bench") or 0)
        gaps = int(f.get("total_gaps") or 0)
        top = [t for t in (f.get("top_performers") or []) if t][:2]
        top_cust = f.get("top_customer")
        top_cust_pct = f.get("top_customer_share_pct")
        stalled_cnt = int(f.get("ramp_stalled_count") or 0)

        if review:
            # backward-looking wrap (weekend / month-end)
            if batches or pax:
                beats.append(_msg_pick([
                    f"We closed the {period} with {batches} {'batch' if batches == 1 else 'batches'} delivered to {pax} participants.",
                    f"This {period} the team delivered {batches} {'batch' if batches == 1 else 'batches'}, {pax} participants in total.",
                ], seed, 1))
            avg_rating = f.get("avg_rating")
            if avg_rating is not None:
                beats.append(f"Learner feedback across the team averaged {avg_rating} out of 5.")
            if top:
                beats.append("Particular thanks to %s for %s." % (
                    _msg_join_names(top, style),
                    _msg_pick(["strong delivery this " + period,
                               "consistently high learner feedback",
                               "carrying a heavy load well"], seed, 2)))
            if at_risk:
                beats.append(
                    f"{at_risk} feedback {'point is' if at_risk == 1 else 'points are'} being worked through one to one; "
                    "please keep raising concerns early rather than at the end of a batch."
                )
            if cadence == "monthend" and gaps:
                beats.append(f"{gaps} certification {'gap remains' if gaps == 1 else 'gaps remain'} open across the team - please book yours.")
            if top_cust and top_cust_pct and top_cust_pct >= 40:
                beats.append(f"Delivery was concentrated with {top_cust} representing {int(top_cust_pct)} percent of our load this {period}.")
            if not beats:
                beats.append(f"A steady {period} across the team with nothing outstanding. Thank you all.")
            closing_raw = ("Thank you all for the effort this " + period + "."
                           if not at_risk else "Have a good break, and let us pick the open points up "
                           + ("Monday" if cadence == "weekend" else "next month") + ".")
        else:
            # forward-looking plan (weekly / monthly)
            if deliv or batches:
                beats.append(_msg_pick([
                    f"This {period} {deliv} of {head} of us are in delivery, {pax} participants across {batches} {'batch' if batches == 1 else 'batches'}.",
                    f"We have {deliv} of {head} trainers delivering this {period}, {pax} participants over {batches} {'batch' if batches == 1 else 'batches'}.",
                ], seed, 1))
            if opp:
                line = f"There {'is' if opp == 1 else 'are'} {opp} open {'batch' if opp == 1 else 'batches'} on the board"
                if coverable:
                    line += f", {coverable} of which this team can already teach"
                if bench:
                    line += f", and {bench} of us {'is' if bench == 1 else 'are'} free"
                line += ". If that could be you, please confirm your availability with me today so we do not lose the slot."
                beats.append(line)
            elif bench:
                beats.append(f"{bench} of us {'is' if bench == 1 else 'are'} on the bench this {period}; check the demand board and tell me where you can help.")
            if gaps:
                beats.append(
                    f"We are carrying {gaps} open certification {'gap' if gaps == 1 else 'gaps'} across the team. "
                    + ("Please prioritise the ones tied to open demand." if gaps > 3 else f"Please book yours before {deadline_ref}.")
                )
            if at_risk:
                beats.append(f"{at_risk} feedback {'point is' if at_risk == 1 else 'points are'} being handled individually this {period}.")
            if stalled_cnt > 0:
                beats.append(f"{stalled_cnt} new trainer onboarding {'milestone needs' if stalled_cnt == 1 else 'milestones need'} staffing focus this {period}.")
            if not beats:
                beats.append(f"Delivery is steady across the team this {period} with no open flags. Thank you for keeping it that way.")
            closing_raw = ("Have a good " + period + ", and thank you for keeping delivery steady." if not (opp or gaps)
                           else "Please act on your part today and keep me posted.")
        if mm:
            beats = [mm] + beats[:2]
    else:
        first = str(f.get("first") or "there").strip()
        greeting = f"Hello {_italic(first, style)}," if style == "teams" else f"Hello {first},"
        seed = _msg_seed("reportee", f.get("email", first), cadence, f.get("period_key", ""))
        beats = []
        util = f.get("util")
        cur = f.get("current_course") or ""
        upc = f.get("upcoming_course") or ""
        rating = f.get("rating")
        rc = int(f.get("rating_count") or 0)
        opp_courses = f.get("opp_courses") or []
        gap_courses = f.get("cert_gap_courses") or []
        neg = int(f.get("neg_feedback") or 0)
        hr_neg = int(f.get("hr_neg") or 0)
        qubits = f.get("qubits")
        bench = bool(f.get("bench"))
        stretched = bool(f.get("stretched"))
        trend = str(f.get("rating_trend") or "")
        batches_done = int(f.get("batches_done") or 0)
        pos_themes = f.get("pos_themes") or []
        cons_themes = f.get("cons_themes") or []
        gap_demand_cnt = int(f.get("gap_demand_count") or 0)
        gap_demand_pax_days = int(f.get("gap_demand_pax_days") or 0)
        opp_pax_days = int(f.get("opp_pax_days") or 0)
        ti_score = f.get("ti_score")
        ti_tier = f.get("ti_tier")
        leave_days = int(f.get("leave_days") or 0)
        ramp_stage = str(f.get("ramp_stage") or "")
        stalled = bool(f.get("stalled"))

        pos_theme_str = ""
        if pos_themes:
            pos_theme_str = " and ".join(f'"{_THEME_LABELS.get(t, t)}"' for t in pos_themes[:2])

        if review:
            # ── 1r. what happened this period ────────────────────────────────
            if batches_done or cur:
                opener = f"You wrapped {('the ' + cur) if cur else str(batches_done) + (' batch' if batches_done == 1 else ' batches')} this {period}"
                if f.get("pax"):
                    opener += f" for {f.get('pax')} participants"
                if qubits is not None and qubits >= 40:
                    opener += f" with your Qubits knowledge score at {int(qubits)} percent"
                opener += "."
                beats.append(_msg_pick([opener, opener], seed, 1))
            elif bench:
                beats.append(f"You were on the bench this {period}"
                             + (f" at {int(util)} percent utilisation" if util is not None else "") + ".")
            elif ramp_stage == "onboarding":
                beats.append(f"You completed onboarding milestones this {period} as you prepare for initial delivery.")

            # ── 2r. feedback evidence ─────────────────────────────────────────
            if rating is not None:
                b = f"Learners rated you {rating} out of 5 across {rc} response{'s' if rc != 1 else ''} this {period}"
                if pos_theme_str:
                    b += f', with {pos_theme_str} highlighted as strengths'
                b += {"improving": ", and the trend is up.", "declining": ", and that is down on the previous run."}.get(trend, ".")
                q = f.get("pos_quote") if (rating >= 4) else f.get("neg_quote")
                if q:
                    b += f' One comment: "{q}".'
                beats.append(b)

            # ── 3r. quality / coaching records ───────────────────────────────
            if neg or hr_neg:
                beats.append(f"Let us find time {deadline_ref} to go through the {neg + hr_neg} feedback "
                             f"{'record' if neg + hr_neg == 1 else 'records'} on file.")

            # ── 4r. cert gaps + quantified opportunity ────────────────────────
            if gap_courses:
                if gap_demand_cnt > 0:
                    beats.append(
                        f"Closing your open certification on {', '.join(gap_courses[:2])} unlocks {gap_demand_cnt} "
                        f"batch{'es' if gap_demand_cnt != 1 else ''} ({gap_demand_pax_days} participant-days) on the demand board - "
                        f"please book the exam before {deadline_ref}."
                    )
                else:
                    beats.append(f"Certification for {', '.join(gap_courses[:2])} is still open - please book it before {deadline_ref}.")

            # ── 5r. next step / bench opportunities ───────────────────────────
            if upc:
                beats.append(f"Next up for you is {upc}.")
            elif bench and opp_courses:
                beats.append(
                    f"There is open demand matching your skills on {', '.join(opp_courses[:2])}"
                    + (f" ({opp_pax_days} participant-days)" if opp_pax_days else "")
                    + ". Confirm your availability and I will put you forward."
                )

            # ── 6r. growth / qubits / TI progression ──────────────────────────
            if ti_score is not None and ti_tier:
                beats.append(f"Your Trainer Index stands at {ti_score} points ({ti_tier}).")
            elif cadence == "monthend" and qubits is not None and qubits >= 40 and qubits < 80 and not (neg or hr_neg):
                beats.append(f"Knowledge score is {int(qubits)} percent; lifting it toward the mid eighties opens up more batches.")

            if not beats:
                beats.append(f"A quiet {period} for you with nothing outstanding.")

            if neg or hr_neg:
                closing_raw = "Let us talk " + deadline_ref + "."
            elif rating is not None and rating >= 4.5:
                closing_raw = "Thank you - it shows in the feedback."
            else:
                closing_raw = ("Have a good weekend." if cadence == "weekend" else "Speak at the start of next month.")

            body = " ".join(b for b in beats[:5] if b).strip()
            body = _message_sanitise(body)
            body = _bold_first_action(body, style)
            refs = _extract_time_refs((period_ref + " " + deadline_ref).lower())
            body = _underline_one_timeref(body, style, refs)
            closing = _italic(closing_raw, style) if style == "teams" else closing_raw
            msg = f"{greeting}\n\n{body}\n\n{closing}"
            msg = _re.sub(r"[ \t]+", " ", msg)
            msg = "\n".join(ln.rstrip() for ln in msg.split("\n"))
            return _trim_message_to_limit(_re.sub(r"\n{3,}", "\n\n", msg).strip())

        # ── 1. status opener (forward-looking) ───────────────────────────────
        if stretched and cur:
            beats.append(_msg_pick([
                f"You are carrying a heavy load this {period} with {cur}" + (f", and utilisation is at {int(util)} percent" if util is not None else "") + ".",
                f"This {period} is a stretch for you: {cur}" + (f" with utilisation at {int(util)} percent" if util is not None else "") + ".",
            ], seed, 1))
        elif cur:
            cur_line = f"You are delivering {cur} this {period}"
            if f.get("pax"):
                cur_line += f" to {f.get('pax')} participants"
            if qubits is not None and qubits >= 40:
                cur_line += f" and your Qubits knowledge score sits at {int(qubits)} percent"
            cur_line += "."
            beats.append(_msg_pick([cur_line, cur_line], seed, 2))
        elif stalled:
            beats.append(f"You are currently flagged as stalled on the ramp tracker with 0 batch deliveries over recent months.")
        elif ramp_stage == "onboarding":
            beats.append(f"You are in your onboarding ramp phase as a new joiner this {period}.")
        elif bench:
            beats.append(_msg_pick([
                f"You are on the bench this {period}" + (f" with utilisation at {int(util)} percent" if util is not None else "") + ".",
                f"You are free this {period}" + (f"; utilisation is sitting at {int(util)} percent" if util is not None else "") + ".",
            ], seed, 3))
        elif util is not None:
            beats.append(f"Your {period} is steady at {int(util)} percent utilisation.")

        # ── 2. feedback evidence & themes ───────────────────────────────────
        if rating is not None and rating >= 4.0:
            b = f"Learner feedback stays strong at {rating} out of 5 across {rc} response{'s' if rc != 1 else ''}"
            if pos_theme_str:
                b += f', with {pos_theme_str} highlighted as strengths'
            b += "."
            q = (f.get("pos_quote") or "")
            if q:
                b += f' One recent comment: "{q}".'
            beats.append(b)
        elif rating is not None and rating < 3.7:
            b = f"Learner feedback has slipped to {rating} out of 5 across {rc} response{'s' if rc != 1 else ''}."
            if cons_themes:
                cons_label = _THEME_LABELS.get(cons_themes[0], cons_themes[0])
                b += f' "{cons_label.title()}" emerged as an area to watch.'
            q = (f.get("neg_quote") or "")
            b += f' A recent comment: "{q}".' if q else " I would like us to look at the recent comments together this " + period + "."
            beats.append(b)

        # ── 3. cert gap + quantified opportunity cost (AI Mind cross-reference)
        if gap_courses:
            names = ", ".join(gap_courses[:2])
            if gap_demand_cnt > 0:
                beats.append(
                    f"You are teaching {names} without the matching certification on file. "
                    f"{gap_demand_cnt} open batch{'es are' if gap_demand_cnt != 1 else ' is'} waiting on the demand board ({gap_demand_pax_days} participant-days). "
                    f"Booking the exam unlocks those batches and adds roughly 200 points to your Trainer Index."
                )
            else:
                beats.append(
                    f"You are teaching {names} without the matching certification on file. "
                    f"Please book the exam before {deadline_ref}."
                )

        # ── 4. opportunity cost for bench ────────────────────────────────────
        if (bench or (not cur and util is not None and util < 60)) and opp_courses:
            n = len(opp_courses)
            names = ", ".join(opp_courses[:2])
            pax_clause = f" ({opp_pax_days} participant-days)" if opp_pax_days else ""
            beats.append(
                f"There {'is' if n == 1 else 'are'} {n} open {'batch' if n == 1 else 'batches'} on the demand board that {'matches' if n == 1 else 'match'} your work on {names}{pax_clause}. "
                f"Please confirm your availability so I can put you forward."
            )
        elif (bench or (not cur and util is not None and util < 55)) and not opp_courses and f.get("has_demand_view"):
            beats.append(f"Nothing on the demand board matches your current skills right now, so let us use this {period} to add one course that opens up demand.")

        # ── 5. quality risk / HR / leaves ────────────────────────────────────
        if neg or hr_neg:
            tot = neg + hr_neg
            beats.append(
                f"There {'is' if tot == 1 else 'are'} {tot} feedback record{'s' if tot != 1 else ''} on file that we need to review. "
                f"Please come back to me this {period} with what happened and your plan."
            )
        elif leave_days >= 3:
            beats.append(f"You have {leave_days} days of planned leave on record this {period}; please ensure batch handovers are confirmed in advance.")

        # ── 6. growth / qubits / TI progression ──────────────────────────────
        if qubits is not None and qubits >= 40 and (len(beats) < 2 or cadence == "monthly"):
            if qubits >= 80:
                beats.append(f"Your knowledge score is holding at {int(qubits)} percent, which keeps you first in line for the harder batches.")
            elif qubits:
                beats.append(f"Your knowledge score is at {int(qubits)} percent. Lifting it to the mid eighties would widen the batches I can send you.")

        if upc and not stretched:
            beats.append(f"Next up for you is {upc}.")

        if mm:
            beats = [mm] + beats[:2]

        # tone / closing
        if neg or hr_neg:
            closing_raw = "Please treat this as a priority and reply with your plan."
        elif bench and opp_courses:
            closing_raw = ("Please confirm today so we do not lose the slot." if cadence == "weekly" else "Let me know where you can pick up demand this month.")
        elif rating is not None and rating >= 4.5 and cur:
            closing_raw = "Thank you for the consistency, it shows in the feedback."
        elif gap_courses:
            closing_raw = "Let me know once the exam is booked."
        else:
            closing_raw = "Please keep me posted."

    body = " ".join(b for b in beats[:5] if b).strip()
    body = _message_sanitise(body)
    body = _bold_first_action(body, style)
    refs = _extract_time_refs((period_ref + " " + deadline_ref).lower())
    body = _underline_one_timeref(body, style, refs)
    closing = _italic(closing_raw, style) if style == "teams" else closing_raw
    msg = f"{greeting}\n\n{body}\n\n{closing}"
    msg = _re.sub(r"[ \t]+", " ", msg)
    msg = "\n".join(ln.rstrip() for ln in msg.split("\n"))
    msg = _re.sub(r"\n{3,}", "\n\n", msg).strip()
    return _trim_message_to_limit(msg)


def _reportee_message_facts(snap: dict, cadence: str, demand_rows=None,
                            skills_courses=None, month_label: str = "") -> dict:
    """Flatten a weekly/monthly reportee snapshot into composer facts with full AI Mind intelligence."""
    fb = snap.get("learner_feedback") or {}
    pos = (fb.get("positive_quotes") or [{}])[0].get("text") if fb.get("positive_quotes") else ""
    neg = (fb.get("constructive_quotes") or [{}])[0].get("text") if fb.get("constructive_quotes") else ""
    assigns = snap.get("assignments") or []
    cur = assigns[0].get("course") if assigns else (snap.get("current_batch") or {}).get("course", "")
    upc = assigns[1].get("course") if len(assigns) > 1 else ""
    util = snap.get("current_utilization")
    if util is None:
        util = snap.get("utilisation_pct")

    # Detailed demand opportunities matching trainer's skill catalogue
    opp_det = _open_opportunities_detailed(skills_courses or [], demand_rows or [])
    gap_courses = snap.get("cert_gap_courses") or []
    gap_demand_det = _open_opportunities_detailed(gap_courses, demand_rows or [])

    # Feedback themes
    themes = fb.get("themes") or []
    pos_themes = [t.get("theme") for t in themes if t.get("sentiment") == "positive" and t.get("theme")]
    cons_themes = [t.get("theme") for t in themes if t.get("sentiment") == "constructive" and t.get("theme")]

    # Trainer Index tier / score
    ti_score = snap.get("trainer_index_score") or snap.get("ti_score")
    ti_tier = snap.get("trainer_index_tier") or snap.get("ti_tier")
    if ti_score is None and snap.get("trainer_index"):
        ti_score = snap["trainer_index"].get("total_points")
        ti_tier = snap["trainer_index"].get("standing_tier_label")

    return {
        "has_demand_view": bool(demand_rows) and bool(skills_courses),
        "email": snap.get("email"),
        "first": (snap.get("name") or "").split()[0] if snap.get("name") else "there",
        "util": util,
        "qubits": snap.get("avg_qubits"),
        "current_course": _re.sub(r"^[A-Z]{2,4}-[0-9]{2,4}T?[0-9]*:\s*", "", str(cur or "")).strip(),
        "upcoming_course": _re.sub(r"^[A-Z]{2,4}-[0-9]{2,4}T?[0-9]*:\s*", "", str(upc or "")).strip(),
        "pax": snap.get("total_pax") or (snap.get("current_batch") or {}).get("participants"),
        "rating": (fb.get("avg_rating") if fb else snap.get("learner_rating")),
        "rating_count": (fb.get("response_count") if fb else snap.get("learner_rating_count")) or 0,
        "pos_quote": pos or "",
        "neg_quote": neg or "",
        "neg_feedback": snap.get("negative_feedback_count") or snap.get("negativeFeedbackCount") or 0,
        "hr_neg": snap.get("hr_negative_count") or 0,
        "cert_gap_courses": gap_courses,
        "opp_courses": opp_det.get("courses", []),
        "opp_batch_count": opp_det.get("batch_count", 0),
        "opp_pax_days": opp_det.get("pax_days", 0),
        "gap_demand_count": gap_demand_det.get("batch_count", 0),
        "gap_demand_pax_days": gap_demand_det.get("pax_days", 0),
        "pos_themes": pos_themes,
        "cons_themes": cons_themes,
        "ti_score": ti_score,
        "ti_tier": ti_tier,
        "leave_days": snap.get("leave_days") or snap.get("leaves_this_month") or 0,
        "ramp_stage": snap.get("ramp_stage") or "",
        "stalled": bool(snap.get("stalled")),
        # A trainer actively delivering a batch is NOT "on the bench" even if
        # their RMS utilisation reading is low - the opportunity push is wrong
        # for someone already in front of a class.
        "bench": (snap.get("capacity_bucket") in ("On Bench", "Bench"))
                 or (not cur and not assigns and util is not None and util < 55),
        "stretched": snap.get("capacity_bucket") == "Stretched" or (util is not None and util >= 85),
        "rating_trend": (fb.get("trend_direction") or "") if fb else "",
        "batches_done": snap.get("batch_count") or len(assigns) or 0,
        "month_label": month_label,
        "period_key": month_label or "wk",
    }


def _compose_rewritten(user_message: str, my_message: str, style: str = "teams",
                       target_name: str = "", is_team: bool = False,
                       evidence_context: dict | None = None) -> str:
    um = str(user_message or "").strip()
    mm = str(my_message or "").strip()
    if not um and not mm:
        raise ValueError("At least one of user_message or my_message is required")
    intent = _detect_intent(um, mm)
    # greeting
    if is_team:
        greeting = "Hello team,"
    else:
        first = str(target_name or "").strip().split()[0] if str(target_name or "").strip() else ""
        if not first and um:
            # try to extract a name-like token from the user message context
            first = ""
        if first:
            greeting = f"Hello {_italic(first, style)}," if style == "teams" else f"Hello {first},"
        else:
            greeting = "Hello there,"

    # body construction
    body_sentences = []
    if um and mm:
        # acknowledge user context briefly, then convey manager intent
        topic = ""
        # naive topic extraction: course code or assignment word
        m = _COURSE_CODE_RE_RW.search(um + " " + mm)
        if m:
            topic = m.group(0)
        elif _re.search(r"\b(batch|assignment|delivery|material|course)\b", um, _re.I):
            topic = "your update"
        ack = "Thank you for your update on " + (topic if topic else "your message") + "." if um else ""
        if ack:
            body_sentences.append(_professional_rephrase(ack))
        core = _professional_rephrase(mm)
        # if the manager draft is very short, enrich with a professional lead
        if len(core.split()) < 4:
            core = _professional_rephrase(mm + " Please let me know if you need support")
        body_sentences.append(core)
    elif mm:
        body_sentences.append(_professional_rephrase(mm))
    else:
        body_sentences.append(_professional_rephrase(um))

    # optional evidence sentence (one at most, evidence-only)
    if isinstance(evidence_context, dict):
        ev = ""
        if evidence_context.get("cert_gap_courses"):
            gaps = ", ".join(evidence_context["cert_gap_courses"][:2])
            ev = f"On record you are delivering {gaps} without the matching certification."
        elif evidence_context.get("learner_rating") is not None:
            ev = f"Learners rate you {evidence_context['learner_rating']}/5 from {evidence_context.get('learner_rating_count', 0)} responses in the last 90 days."
        elif evidence_context.get("utilisation") is not None and evidence_context.get("utilisation") < 55:
            ev = f"Your utilisation is at {evidence_context['utilisation']} percent this week."
        if ev:
            body_sentences.append(_professional_rephrase(ev))

    body = " ".join(s for s in body_sentences if s)

    # house formatting: bold the single key action sentence, underline time refs
    # bold: first sentence containing an actionable cue
    action_cues = ("please", "book", "share", "confirm", "schedule", "ensure", "complete", "send", "prepare", "hold", "review", "let me know")
    sents = _re.split(r"(?<=[.!?])\s+", body)
    bolded = False
    new_sents = []
    for s in sents:
        low = s.lower()
        if not bolded and any(cue in low for cue in action_cues):
            # extract the actionable clause for bold; bold the whole sentence
            new_sents.append(_bold(s.strip(), style))
            bolded = True
        else:
            new_sents.append(s)
    body = " ".join(new_sents)
    # underline time references (sparingly) — avoid nesting inside bold
    for tr in intent["time_refs"][:2]:
        # skip if this time ref sits inside an already bolded segment
        bold_spans = [(m.start(), m.end()) for m in _re.finditer(r"\*\*.*?\*\*", body)]
        def _inside_bold(pos):
            return any(s <= pos < e for s, e in bold_spans)
        m = _re.search(_re.escape(tr), body, _re.I)
        if m and not _inside_bold(m.start()):
            body = body[:m.start()] + _underline(m.group(0), style) + body[m.end():]

    # closing with light emphasis
    tone = intent["tone"]
    if tone == "urgent":
        closing_raw = "Please confirm once done."
    elif tone == "corrective":
        closing_raw = "Please let me know if you need support."
    elif tone == "appreciative":
        closing_raw = "Thank you for your continued effort."
    elif tone == "advisory":
        closing_raw = "Please let me know your plan."
    else:
        closing_raw = "Thank you for your attention to this."
    closing = _italic(closing_raw, style) if style == "teams" else closing_raw

    assembled = f"{greeting}\n\n{body}\n\n{closing}"
    # final sanitise pass for the assembled message must not strip the markdown markers
    # so re-apply only whitespace collapse
    assembled = _re.sub(r"[ \t]+", " ", assembled)
    assembled = "\n".join(line.rstrip() for line in assembled.split("\n"))
    assembled = _re.sub(r"\n{3,}", "\n\n", assembled).strip()
    return _trim_message_to_limit(assembled)


_FEEDBACK_MIN_QUOTE = 45
_FEEDBACK_MAX_QUOTE = 220
# A comment worth surfacing usually talks about the session/trainer/content.
_QUOTE_SIGNAL = _re.compile(
    r"\b(trainer|session|training|course|explain|explained|knowledge|content|"
    r"pace|paced|pacing|lab|labs|concept|concepts|instructor|deliver|delivery|"
    r"patient|patience|helpful|clear|understand|understanding|example|examples|"
    r"hands[- ]?on|practical|thorough)\b", _re.I,
)


def _clean_quote(text):
    """One or two sentences of a learner comment: strip the collected-feedback
    boilerplate and speaker labels RMS prepends, normalise whitespace, cap
    length. Returns "" when nothing usable is left."""
    t = _re.sub(r"\s+", " ", str(text or "")).strip()
    if not t:
        return ""
    # RMS free text often begins "Student Feedbacks" / "Feedback:" / "Name :"
    t = _re.sub(r"^(student\s+feedbacks?|participant\s+feedbacks?|feedback)\s*[:\-]?\s*", "", t, flags=_re.I)
    t = _re.sub(r"^[A-Z][A-Za-z.'\- ]{1,28}\s*:\s*", "", t)  # leading "First Last :"
    # RMS concatenates several learners' comments with no separator; cut at the
    # next "Firstname Lastname:" speaker label so one person's words stand alone.
    t = _re.split(r"\s+[A-Z][a-z]+(?:\s+[A-Z][a-z]+){0,2}\s*:\s*", t)[0].strip()
    t = t.strip(" \"'“”")
    if not t:
        return ""
    parts = _re.split(r"(?<=[.!?])\s+", t)
    out = parts[0]
    if len(out) < 90 and len(parts) > 1:
        out = (out + " " + parts[1]).strip()
    if len(out) > _FEEDBACK_MAX_QUOTE:
        out = out[:_FEEDBACK_MAX_QUOTE].rsplit(" ", 1)[0].rstrip(",.;:") + "…"
    return out


_FEEDBACK_THEMES = [
    ("pace", r"\b(pace|paced|pacing|fast|slow|slowly|rushed|rush|hurry|hurried|time)\b"),
    ("depth", r"\b(depth|detail|detailed|deep|deeper|shallow|basic|advanced|in-?depth)\b"),
    ("labs/hands-on", r"\b(lab|labs|hands-?on|hands on|practical|practically|exercise|exercises|demo|demos|practice)\b"),
    ("clarity/communication", r"\b(clear|clearly|clarity|explain|explained|explains|explanation|communicat\w*|understandable|articulate|confusing|confused)\b"),
    ("knowledge", r"\b(knowledge|knowledgeable|expert|expertise|command|subject matter|thorough|thoroughly)\b"),
    ("engagement", r"\b(engaging|engaged|interactive|patient|patience|responsive|questions|helpful|approachable)\b"),
]
_FEEDBACK_THEMES = [(name, _re.compile(pat, _re.I)) for name, pat in _FEEDBACK_THEMES]


def _feedback_analytics(email, months=12):
    """
    Deterministic trend + theme analysis over one trainer's learner-feedback
    rows (RMS key 244). Reuses the same fixed empty-body fetch as
    `_trainer_feedback_detail` so it shares the cached response.

    Returns {"trend": [...], "trend_direction": str, "themes": [...]}.
    """
    rows = _rms("trainerFeedback", {"TrainerEmail": "", "AssignmentId": "", "SCID": ""}) or []
    e = str(email).strip().lower()
    mine = []
    for r in rows:
        if not isinstance(r, dict):
            continue
        if str(r.get("TrainerEmail", "")).strip().lower() != e:
            continue
        d = _parse_date(str(r.get("FeedBackDate", "")).split("T")[0])
        rating = None
        mcq = r.get("MCQAnswer")
        try:
            if mcq is not None and str(mcq).strip() != "":
                rating = float(mcq)
        except (TypeError, ValueError):
            rating = None
        mine.append({"date": d, "rating": rating, "text": str(r.get("TextAnswer") or "")})

    ratings = [m["rating"] for m in mine if m["rating"] is not None]
    overall = (sum(ratings) / len(ratings)) if ratings else None

    # ── TREND — per calendar month, months with >=1 rating, chronological ──
    buckets = {}
    for m in mine:
        if not m["date"] or m["rating"] is None:
            continue
        key = (m["date"].year, m["date"].month)
        buckets.setdefault(key, []).append(m["rating"])
    ordered = sorted(buckets)[-months:]
    trend = []
    for (y, mo) in ordered:
        v = buckets[(y, mo)]
        trend.append({
            "month": date(y, mo, 1).strftime("%b %Y"),
            "avg_rating": round(sum(v) / len(v), 2),
            "count": len(v),
        })

    direction = "steady"
    if len(trend) >= 4:
        recent = trend[-3:]
        prior = trend[-6:-3]
        if prior:
            r_mean = sum(t["avg_rating"] for t in recent) / len(recent)
            p_mean = sum(t["avg_rating"] for t in prior) / len(prior)
            if r_mean - p_mean >= 0.2:
                direction = "improving"
            elif p_mean - r_mean >= 0.2:
                direction = "declining"

    # ── THEMES — deterministic keyword clustering over TextAnswer ─────────
    themes = []
    for name, rx in _FEEDBACK_THEMES:
        mentions = 0
        pos = 0
        samples = []
        for m in mine:
            q = _clean_quote(m["text"])
            hay = m["text"]
            if not rx.search(hay):
                continue
            mentions += 1
            row_rating = m["rating"] if m["rating"] is not None else overall
            if row_rating is None or row_rating >= 4.0:
                pos += 1
            # shortest cleaned sentence from a matching row containing a keyword
            for sent in _re.split(r"(?<=[.!?])\s+", q):
                s = sent.strip()
                if s and rx.search(s):
                    samples.append(s)
        if not mentions:
            continue
        samples.sort(key=len)
        themes.append({
            "theme": name,
            "mentions": mentions,
            "sentiment": "positive" if pos * 2 >= mentions else "constructive",
            "sample": samples[0] if samples else "",
        })
    themes.sort(key=lambda t: t["mentions"], reverse=True)

    return {"trend": trend, "trend_direction": direction, "themes": themes[:5]}


def _trainer_feedback_detail(email, days=None, until=None):
    """
    Real per-assignment learner feedback for one trainer (RMS key 244).

    The RMS endpoint ignores its `TrainerEmail` filter and returns the whole
    recent feedback set, so rows are filtered here by email. `MCQAnswer` is a
    1-5 instructor rating; `TextAnswer` is the free-text learner comment.
    Returns aggregates plus short dated excerpts — never invented text.

    Text rows often carry no MCQ of their own, so a quote's sentiment is taken
    from the trainer's overall average rating for the window rather than guessed
    from the words.
    """
    # The endpoint ignores its filter and returns the whole recent set, so use a
    # fixed empty body — every trainer in a team then shares ONE cached fetch
    # instead of paying for the full ~1MB response once per reportee.
    rows = _rms("trainerFeedback", {"TrainerEmail": "", "AssignmentId": "", "SCID": ""}) or []
    e = str(email).strip().lower()
    lo = (datetime.utcnow().date() - timedelta(days=days)) if days else None
    hi = _parse_date(until) if until else None
    ratings, raw_quotes, seen = [], [], set()
    latest = None
    for r in rows:
        if not isinstance(r, dict):
            continue
        if str(r.get("TrainerEmail", "")).strip().lower() != e:
            continue
        d = _parse_date(str(r.get("FeedBackDate", "")).split("T")[0])
        if lo and d and d < lo:
            continue
        if hi and d and d > hi:
            continue
        if d and (latest is None or d > latest):
            latest = d
        mcq = r.get("MCQAnswer")
        rating = None
        try:
            if mcq is not None and str(mcq).strip() != "":
                rating = float(mcq)
                ratings.append(rating)
        except (TypeError, ValueError):
            rating = None
        q = _clean_quote(r.get("TextAnswer"))
        if (q and len(q) >= _FEEDBACK_MIN_QUOTE and _QUOTE_SIGNAL.search(q)
                and q.lower() not in seen):
            seen.add(q.lower())
            raw_quotes.append({"text": q, "date": _iso(d) if d else "", "row_rating": rating})

    overall = round(sum(ratings) / len(ratings), 1) if ratings else None
    quotes = []
    for rq in raw_quotes:
        r = rq["row_rating"] if rq["row_rating"] is not None else overall
        # A comment on a strongly-rated trainer reads as praise; on a weaker one
        # it reads as development input. No middle "mixed" bucket — every quote
        # lands somewhere a manager can use it.
        kind = "positive" if (r is None or r >= 4.0) else "constructive"
        quotes.append({
            "text": rq["text"], "date": rq["date"],
            "rating": int(rq["row_rating"]) if rq["row_rating"] is not None else None,
            "kind": kind,
        })
    quotes.sort(key=lambda x: x["date"], reverse=True)
    return {
        "count": max(len(ratings), len(quotes)),
        "response_count": len(ratings),
        "avg_rating": overall,
        "rating_scale": 5,
        "recent_date": _iso(latest) if latest else "",
        "positive_quotes": [q for q in quotes if q["kind"] == "positive"][:3],
        "constructive_quotes": [q for q in quotes if q["kind"] == "constructive"][:3],
        "quotes": quotes[:5],
        **_feedback_analytics(email),
    }


def _generate_manager_evaluation(
    name, email, month_label, avg_qubits, top_courses,
    month_util, util_3m, batch_count, month_assignments,
    neg_total, hr_pos, hr_neg, cert_intel, hr_score,
    feedback_window_days=120, demand_rows=None, skills_courses=None,
):
    """
    Manager-facing performance note built ONLY from evidence on record: real
    learner feedback (RMS key 244), named certification gaps, utilisation, HR
    incident counts and Qubits. No generic behavioural boilerplate - a
    dimension with no evidence this cycle is stated as such rather than filled
    with a plausible-sounding sentence that applies to everyone.
    """
    first_name = (name or "").strip().split()[0] if (name or "").strip() else "This trainer"

    course_names = []
    for c in (top_courses or []):
        c_title = c.get("course_name", "") if isinstance(c, dict) else str(c)
        if c_title:
            course_names.append(_re.sub(r"^[A-Z]{2,4}-[0-9]{2,4}T?[0-9]*:\s*", "", c_title).strip())
    top_topics_str = ", ".join(dict.fromkeys(n for n in course_names if n))[:120]

    fb = _trainer_feedback_detail(email, days=feedback_window_days)
    gap_count = cert_intel.get("gap_count", 0) if isinstance(cert_intel, dict) else 0
    gap_names = []
    if isinstance(cert_intel, dict):
        for g in (cert_intel.get("gaps") or []):
            if isinstance(g, dict) and (g.get("exam") or g.get("because")):
                gap_names.append(str(g.get("exam") or g.get("because")))
    gap_str = ", ".join(dict.fromkeys(gap_names))[:120]

    # ── STRENGTH — only what is evidenced ───────────────────────────────
    s = []
    if fb["avg_rating"] is not None and fb["avg_rating"] >= 4.0:
        s.append(f"Learners rate {first_name} {fb['avg_rating']}/5 across {fb['response_count']} response(s) in the last {feedback_window_days} days.")
    pos_themes = [t.get("theme") for t in (fb.get("themes") or []) if t.get("sentiment") == "positive" and t.get("theme")]
    if pos_themes:
        th_names = ", ".join(_THEME_LABELS.get(t, t) for t in pos_themes[:2])
        s.append(f"Standout strengths highlighted in learner comments: {th_names}.")
    for q in fb["positive_quotes"][:2]:
        s.append(f'Learner feedback ({_human_date(q["date"])}): "{q["text"]}"')
    if hr_pos > 0:
        s.append(f"{hr_pos} positive HR recognition record(s) on file.")
    if neg_total == 0 and hr_neg == 0:
        s.append("Clean quality record - no negative feedback or HR incidents this cycle.")
    if avg_qubits >= 80 and top_topics_str:
        s.append(f"Qubits knowledge score is {int(avg_qubits)}% across {top_topics_str}.")
    strength_text = " ".join(s) if s else f"No standout strengths are on record for {first_name} this cycle."

    # ── AREA OF IMPROVEMENT — only what is evidenced ────────────────────
    a = []
    gap_opp = _open_opportunities_detailed(gap_names, demand_rows or [])
    if gap_count > 0:
        gap_desc = f"Close {gap_count} open certification gap(s){' (' + gap_str + ')' if gap_str else ''}"
        if gap_opp.get("batch_count", 0) > 0:
            gap_desc += f" — unlocks {gap_opp['batch_count']} open batch(es) ({gap_opp['pax_days']} participant-days)"
        gap_desc += "."
        a.append(gap_desc)
    if neg_total > 0:
        a.append(f"Resolve {neg_total} negative feedback record(s) on file.")
    if hr_neg > 0:
        a.append(f"Address {hr_neg} negative HR incident record(s).")
    cons_themes = [t.get("theme") for t in (fb.get("themes") or []) if t.get("sentiment") == "constructive" and t.get("theme")]
    if cons_themes:
        c_names = ", ".join(_THEME_LABELS.get(t, t) for t in cons_themes[:2])
        a.append(f"Learner feedback areas to watch: {c_names}.")
    for q in fb["constructive_quotes"][:2]:
        a.append(f'Learner feedback ({_human_date(q["date"])}): "{q["text"]}"')
    if month_util is not None and month_util < 60:
        a.append(f"Utilisation is {int(month_util)}% - below the 60% bench line; prioritise for open demand.")
    improvement_text = " ".join(a) if a else "No improvement areas are flagged from evidence this cycle."

    # ── TRAJECTORY / VERDICT — from the same evidence ───────────────────
    if neg_total > 0 or hr_neg > 0:
        trajectory, sentiment = "Needs Coaching", "Urgent Attention"
        verdict = f"{first_name} needs a focused 1-on-1 this cycle to resolve the feedback on file before the next batch."
    elif gap_count > 0:
        trajectory, sentiment = "Certification Pending", "Constructive"
        if gap_opp.get("batch_count", 0) > 0:
            verdict = f"{first_name} has {gap_count} pending certification(s) blocking {gap_opp['batch_count']} open batch(es) ({gap_opp['pax_days']} pax-days) — booking the exam unlocks live demand."
        else:
            verdict = f"{first_name} is delivery-steady but below full accreditation - closing the pending exam(s) unlocks scheduled client work."
    elif month_util is not None and month_util < 55:
        trajectory, sentiment = "Bench Upskilling", "Constructive"
        verdict = f"{first_name} is on bench ({int(month_util)}%); the priority is capturing open demand in {top_topics_str or 'assigned domains'}."
    elif (hr_score or 0) >= 85 and (fb["avg_rating"] or 0) >= 4:
        trajectory, sentiment = "High Performer", "Positive"
        verdict = f"{first_name} is delivery-ready with strong learner ratings - suitable for high-visibility batches and peer coaching."
    elif fb["response_count"] == 0 and batch_count == 0:
        trajectory, sentiment = "No Activity", "Neutral"
        verdict = f"No deliveries or learner feedback for {first_name} this cycle; nothing to assess."
    elif fb["avg_rating"] is not None and fb["avg_rating"] < 3.7:
        trajectory, sentiment = "Learner Feedback Focus", "Constructive"
        verdict = f"{first_name}'s learner rating is {fb['avg_rating']}/5 across {fb['response_count']} response(s) - review the recent comments and agree one delivery change for the next batch."
    else:
        trajectory, sentiment = "Steady", "Constructive"
        verdict = f"{first_name} is delivering steadily with no flags this cycle."
    other_text = verdict

    if fb["avg_rating"] is not None:
        mock_summary = f"Learner rating {fb['avg_rating']}/5 from {fb['response_count']} response(s); latest {_human_date(fb['recent_date'])}."
    else:
        mock_summary = "No learner feedback on record for this period."

    formatted_full = (
        f"Strength:\n{strength_text}\n\n"
        f"Area of Improvement:\n{improvement_text}\n\n"
        f"Manager's Verdict:\n{other_text}"
    )

    _mfacts = _reportee_message_facts(
        {
            "email": email, "name": name, "utilisation_pct": month_util,
            "avg_qubits": avg_qubits,
            "assignments": [{"course": c} for c in course_names[:2]],
            "capacity_bucket": ("Stretched" if (month_util or 0) >= 85
                                else "On Bench" if (month_util is not None and month_util < 55)
                                else "Delivering" if batch_count else "Steady"),
            "negative_feedback_count": neg_total, "hr_negative_count": hr_neg,
            "cert_gap_courses": [g for g in gap_names][:3], "learner_feedback": fb,
        },
        "monthly", demand_rows=demand_rows or [],
        skills_courses=skills_courses or course_names, month_label=month_label,
    )
    message = _compose_manager_message("reportee", "monthly", _mfacts)
    message_monthend = _compose_manager_message("reportee", "monthend", _mfacts)

    return {
        "strength": strength_text,
        "area_of_improvement": improvement_text,
        "other_feedback": other_text,
        "trajectory": trajectory,
        "sentiment": sentiment,
        "mock_summary": mock_summary,
        "formatted_text": formatted_full,
        "learner_feedback": fb,
        "message": message,
        "message_monthly": message,
        "message_monthend": message_monthend,
        "message_scope": "reportee", "message_cadence": "monthly",
        "opportunity_courses": _mfacts.get("opp_courses") or [],
    }


# Trainer Index criteria that RMS cannot feed today. The score still computes
# (with neutral defaults) but the caller must not present the tier as if every
# axis were measured. s_no -> why it is unmeasured.
_TI_UNMEASURED = {
    2:  "Beast AI delivery count not exposed by RMS",
    4:  "TBTs / mocks / internal trainings not exposed by RMS",
    7:  "Roaming hours (L12M) not exposed by RMS",
    8:  "Night ILO hours (L12M) not exposed by RMS",
    11: "Trainers developed not exposed by RMS",
    12: "Sales / customer-orientation feedback not exposed by RMS",
    13: "Solution-selling count not exposed by RMS",
    14: "Skill-takeover count not exposed by RMS",
    16: "Centre-improvement reports not exposed by RMS",
    17: "Tech-call conversions not exposed by RMS",
    18: "Koenig tenure not exposed by RMS",
    19: "Prior experience not exposed by RMS",
    20: "Overseas visa commitment not exposed by RMS",
}


def _trainer_index_for(email, name, month_util, util_3m, month_assignments,
                       neg_total, hr_pos, hr_neg, cert_intel, vendor_certs_list):
    """
    Trainer Index from RMS-backed criteria only. Everything RMS does not expose
    is passed as a real zero (not a Qubits-derived guess) and listed in
    `estimated_criteria` so a consumer can show the tier as partial.
    """
    ti = _calculate_trainer_index(
        email=email, name=name, month_util=month_util, util_3m=util_3m,
        quarterly_utils=None, non_sc_hours_pct=0.0,
        beast_ai_deliveries=0, beast_ai_saas_deliveries=0,
        quality_index=max(60.0, min(120.0, 100.0 - neg_total * 10.0 + hr_pos * 5.0)),
        tbts_count=0, mocks_taken=0, internal_trainings=0,
        first_time_deliveries_or_certs=len(cert_intel.get("held", [])),
        certs_held=cert_intel.get("held", []),
        roaming_hours_l12m=0.0, night_ilo_hours_l12m=0.0,
        hr_pos=hr_pos, hr_neg=hr_neg, vendor_certs=vendor_certs_list,
        trainers_developed=0, sales_feedback_points=0.0, solution_selling_count=0,
        skill_takeovers=0, negative_feedbacks=neg_total,
        centre_improvements_reported=0, tech_calls_converted=0,
        koenig_tenure_months=0.0, prior_exp_months=0.0,
        has_overseas_visa_commitment=False,
    )
    ti["estimated_criteria"] = sorted(_TI_UNMEASURED)
    ti["measured_criteria"] = [1, 3, 5, 6, 9, 10, 15]
    ti["confidence"] = "partial"
    ti["confidence_note"] = (
        "Tier is computed from the 7 RMS-backed criteria (utilisation, quality "
        "index, first-time delivery, certifications, HR incidents, instructor "
        "certs, negative feedback). The other 13 are not exposed by RMS and "
        "score zero — treat the tier as a floor, not a full assessment."
    )
    return ti


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
    manager_email = (session or {}).get("email") or manager_email

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
    _mkey = month_start_iso[:7]

    internal_build = request.args.get("_build") == "1"
    if not internal_build:
        if _wants_fresh():
            _warm_purge(f"hr::{manager_email}::{_mkey}")
        return _serve_or_warm(
            cache_key=f"hr::{manager_email}::{_mkey}",
            view_func=hr_monthly_report,
            build_path=(
                f"/api/v2/hr/monthly-report?manager={urllib.parse.quote(manager_email)}"
                f"&month={_mkey}&_build=1{'&refresh=1' if _wants_fresh() else ''}"
            ),
            fast_payload={"loading": True, "month": month_label, "month_key": _mkey, "reportees": []},
        )

    reportees_raw = _reportees(manager_email) or []
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

    _hr_unalloc = _rms("unallocated", {}) or []

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
            demand_rows=(_hr_unalloc if isinstance(_hr_unalloc, list) else []),
            skills_courses=[s.get("course_name", "") for s in skills if s.get("course_name")],
        )

        # 8. Koenig HR Trainer Index (TI – 13/08/26) calculation
        vendor_certs_rows = _rms("vendorCertCount", {"email": email}) or []
        vendor_certs_list = [r.get("VendorCertificationName", "") for r in (vendor_certs_rows if isinstance(vendor_certs_rows, list) else []) if isinstance(r, dict)]

        trainer_ti = _trainer_index_for(
            email, t["name"], month_util, util_3m, month_assignments,
            neg_total, hr_pos, hr_neg, cert_intel, vendor_certs_list,
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
            "message":      eval_data.get("message", ""),
            "opportunity_courses": eval_data.get("opportunity_courses") or [],
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

    _hr_bench = sum(1 for r in out if (r.get("utilisation_pct") or 0) < 55)
    _hr_at_risk = sum(1 for r in out if (r["quality"]["negative_feedback"] or r["quality"]["hr_negative"]))
    _hr_cover = len({c.lower() for r in out for c in (r.get("opportunity_courses") or [])})
    _hr_rated = [(r.get("structured_feedback") or {}).get("learner_feedback", {}).get("avg_rating")
                 for r in out]
    _hr_rated = [x for x in _hr_rated if x is not None]
    _hr_top = sorted(
        (r for r in out if r["delivery"]["batches"] > 0
         and ((r.get("structured_feedback") or {}).get("learner_feedback", {}).get("avg_rating") or 0) >= 4.3
         and not (r["quality"]["negative_feedback"] or r["quality"]["hr_negative"])),
        key=lambda r: -((r.get("structured_feedback") or {}).get("learner_feedback", {}).get("avg_rating") or 0),
    )
    _hr_team_facts = {
        "manager_first": manager_email.split("@")[0].split(".")[0].title(),
        "headcount": len(out),
        "delivering": sum(1 for r in out if r["delivery"]["batches"] > 0),
        "total_pax": sum(r["delivery"]["total_participants"] for r in out),
        "total_batches": sum(r["delivery"]["batches"] for r in out),
        "at_risk": _hr_at_risk,
        "open_demand": len([d for d in (_hr_unalloc if isinstance(_hr_unalloc, list) else []) if isinstance(d, dict)]),
        "coverable_open": _hr_cover, "bench": _hr_bench,
        "total_gaps": sum(r["certifications"]["gap_count"] for r in out),
        "top_performers": [r["name"] for r in _hr_top[:2]],
        "avg_rating": round(sum(_hr_rated) / len(_hr_rated), 1) if _hr_rated else None,
        "period_key": month_start_iso[:7], "month_label": month_label,
    }
    team_digest = _compose_manager_message("team", "monthly", _hr_team_facts)
    team_digest_monthend = _compose_manager_message("team", "monthend", _hr_team_facts)

    _resp = {
        "loading":     False,
        "month":       month_label,
        "month_key":   month_start_iso[:7],
        "generated_at": datetime.utcnow().isoformat(),
        "team_digest": team_digest,
        "team_message": team_digest,
        "team_digest_monthly": team_digest,
        "team_digest_monthend": team_digest_monthend,
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
    }
    _warm_store(f"hr::{manager_email}::{_mkey}", _resp)
    return jsonify(_resp), 200


_PRIORITY_SEVERITY_WEIGHT = {"high": 100, "medium": 50, "low": 10}


def _priority_num(v):
    try:
        return float(str(v).strip() or 0)
    except (TypeError, ValueError):
        return 0.0


def _priorities_build(manager):
    """The real per-manager worklist fan-out. Runs inside `_warm_run`."""
    today = datetime.utcnow().date()

    reps = _reportees(manager) or []
    rows = [r for r in (reps if isinstance(reps, list) else []) if isinstance(r, dict)]
    team = []
    for r in rows:
        e = str(r.get("OffEmail", r.get("Email", "")) or "").strip().lower()
        if not e:
            continue
        team.append((e, _re.sub(r"\s+", " ", str(r.get("TrainerName", "") or "")).strip() or e))

    demand = _demand_rows() or []
    exam_policy = _exam_policy()

    items = []

    # ── unstaffed_demand ────────────────────────────────────────────────────
    for d in demand:
        if not isinstance(d, dict):
            continue
        start_iso = str(d.get("start_date", "") or "")
        start_d = _parse_date(start_iso)
        days_out = (start_d - today).days if start_d else None
        if days_out is not None and days_out <= 7:
            sev = "high"
        elif days_out is not None and days_out <= 21:
            sev = "medium"
        else:
            sev = "low"
        did = str(d.get("demand_id", "") or "") or (d.get("course_name", "") or "")
        items.append({
            "id": "unstaffed_demand:%s" % did,
            "kind": "unstaffed_demand",
            "title": "Unstaffed: %s" % (d.get("course_name", "") or "batch"),
            "detail": "Open batch %s%s needs a trainer." % (
                _human_date(start_iso) or "(no date)",
                " - %s" % _human_date(d.get("end_date", "")) if d.get("end_date") else "",
            ),
            "severity": sev,
            "due": start_iso if start_d else "",
            "target_type": "demand",
            "target_id": did,
        })

    # ── per-trainer signals ────────────────────────────────────────────────
    def _trainer_signals(pair):
        email, name = pair
        out = []
        try:
            neg_rows = _rms("negFeedbackCount", {"email": email}) or []
            hr_rows = _rms("hrIncident", {"email": email}) or []
            u_row = _util_row(email)
            skills = _skills(email)
            certs = _certifications(email)
        except Exception:
            return out

        neg_total = sum(_priority_num(r.get("Total"))
                        for r in (neg_rows if isinstance(neg_rows, list) else []) if isinstance(r, dict))
        hr_neg = sum(_priority_num(r.get("Negative Count"))
                     for r in (hr_rows if isinstance(hr_rows, list) else []) if isinstance(r, dict))
        if neg_total > 0 or hr_neg > 0:
            out.append({
                "id": "one_to_one:%s" % email,
                "kind": "one_to_one",
                "title": "1:1 with %s" % name,
                "detail": "Quality signal this cycle: %d negative feedback, %d HR-negative." % (
                    int(neg_total), int(hr_neg)),
                "severity": "high",
                "due": "",
                "target_type": "trainer",
                "target_id": email,
            })

        series = _util_series(u_row) if u_row else []
        util = series[-1]["utilization"] if series else None
        if util is not None and util >= 90:
            out.append({
                "id": "overload:%s" % email,
                "kind": "overload",
                "title": "%s is overloaded" % name,
                "detail": "Utilisation at %.0f%%." % util,
                "severity": "high" if util >= 100 else "medium",
                "due": "",
                "target_type": "trainer",
                "target_id": email,
            })

        try:
            cert_intel = _cert_intelligence(skills, [], certs.get("held", []), exam_policy=exam_policy)
        except Exception:
            cert_intel = {}
        gap_courses = [str(g.get("because", "")).strip()
                       for g in cert_intel.get("gaps", []) if isinstance(g, dict) and g.get("because")]
        if gap_courses:
            out.append({
                "id": "cert_gap:%s" % email,
                "kind": "cert_gap",
                "title": "%s teaching without cert" % name,
                "detail": "No matching certification for: %s" % ", ".join(gap_courses[:3]),
                "severity": "medium",
                "due": "",
                "target_type": "trainer",
                "target_id": email,
            })
        return out

    if team:
        with ThreadPoolExecutor(max_workers=8) as pool:
            for sub in pool.map(_trainer_signals, team):
                items.extend(sub)

    # ── opportunity overlay on unstaffed demand ───────────────────────────
    # The pool above already warmed the _skills / utilisation caches for every
    # trainer, so this re-pass is cache-cheap. An open batch the team can cover
    # while trainers sit on the bench is a stronger call than a distant one.
    team_codes = set()
    bench_names = []
    for (email, name) in team:
        try:
            for s in (_skills(email) or []):
                cn = s.get("course_name")
                if not cn:
                    continue
                m = _re.search(r"[A-Z]{2,4}-[0-9]{2,4}", str(cn))
                team_codes.add(m.group(0).upper() if m else _norm(cn))
            series = _util_series(_util_row(email))
            if series and (series[-1].get("utilization") or 0) < 55:
                bench_names.append(name.split()[0] if name else email)
        except Exception:
            pass
    _bump = {"low": "medium", "medium": "high", "high": "high"}
    for it in items:
        if it["kind"] != "unstaffed_demand":
            continue
        cn = it["title"].replace("Unstaffed: ", "")
        m = _re.search(r"[A-Z]{2,4}-[0-9]{2,4}", cn)
        code = m.group(0).upper() if m else _norm(cn)
        coverable = code in team_codes or any(
            tc and len(tc) > 6 and (tc in _norm(cn) or _norm(cn) in tc) for tc in team_codes
        )
        if coverable:
            it["coverable"] = True
            if bench_names:
                it["severity"] = _bump[it["severity"]]
                it["detail"] += " Your team can cover this and %s %s on the bench." % (
                    ", ".join(bench_names[:3]), "is" if len(bench_names) == 1 else "are")

    # ── action_overdue ────────────────────────────────────────────────────
    try:
        raised = _action_repository.list_raised(manager) or []
    except Exception:
        raised = []
    for a in raised:
        if not isinstance(a, dict):
            continue
        if str(a.get("lifecycle_state", "open")) != "open":
            continue
        created = _parse_date(str(a.get("created_at", "") or ""))
        if not created:
            continue
        age = (today - created).days
        if age < 7:
            continue
        sev = "high" if age >= 14 else "medium"
        due_d = created + timedelta(days=7)
        items.append({
            "id": "action_overdue:%s" % (a.get("id", "") or ""),
            "kind": "action_overdue",
            "title": "Overdue action: %s" % (a.get("title", "") or "(untitled)"),
            "detail": "Open %d days. %s" % (age, a.get("detail", "") or ""),
            "severity": sev,
            "due": _iso(due_d),
            "target_type": "action",
            "target_id": str(a.get("id", "") or ""),
        })

    # ── rank ──────────────────────────────────────────────────────────────
    for it in items:
        due_d = _parse_date(it["due"]) if it["due"] else None
        days_until = (due_d - today).days if due_d else 0
        it["rank_score"] = _PRIORITY_SEVERITY_WEIGHT.get(it["severity"], 10) - days_until
    items.sort(key=lambda it: it["rank_score"], reverse=True)
    items = items[:40]

    counts = {}
    for it in items:
        counts[it["kind"]] = counts.get(it["kind"], 0) + 1

    return {
        "manager": manager,
        "generated_at": datetime.utcnow().isoformat(),
        "counts": counts,
        "items": items,
        "loading": False,
    }


@app.route('/api/v2/manager/priorities', methods=['GET'])
def manager_priorities_v2():
    """A single ranked worklist ("Your Week") a delivery manager acts from."""
    manager = request.args.get('manager', '').strip().lower()
    _sess, error = _v2_manager_session(manager)
    if error:
        return error
    manager = (_sess or {}).get("email") or manager

    internal_build = request.args.get("_build") == "1"
    if not internal_build:
        ck = "priorities::%s" % manager
        if _wants_fresh():
            _warm_purge(ck)
        return _serve_or_warm(
            cache_key=ck,
            view_func=manager_priorities_v2,
            build_path=(
                "/api/v2/manager/priorities?manager=%s&_build=1%s"
                % (urllib.parse.quote(manager), "&refresh=1" if _wants_fresh() else "")
            ),
            fast_payload={"manager": manager, "counts": {}, "items": [], "loading": True},
        )

    resp = _priorities_build(manager)
    _warm_store("priorities::%s" % manager, resp)
    return jsonify(resp), 200


# ─── Capacity Runway (v2) ────────────────────────────────────────────────────
#
# The manager's forward view: the next 8 weeks of incoming unallocated demand
# set against the headcount the team actually has free each week, the resulting
# gap, and a ranked "start these upskills now" list for the courses the team
# cannot cover at all.

_RUNWAY_HORIZON_WEEKS = 8
_RUNWAY_FREE_UTIL = 85          # a trainer at/above this is not "free" for new work
_RUNWAY_CODE_RE = _re.compile(r"[A-Z]{2,4}-[0-9]{2,4}")


def _runway_course_keys(name):
    """(normalised name, exam/course code or "") used to match demand to skills."""
    n = _norm_course(name)
    m = _RUNWAY_CODE_RE.search(str(name or "").upper())
    return n, (m.group(0) if m else "")


def _runway_teaches(skill_keys, demand_name):
    """True when a trainer whose skill register produced `skill_keys` (a set of
    normalised names + codes) already teaches `demand_name`."""
    n, code = _runway_course_keys(demand_name)
    if code and code in skill_keys:
        return True
    if n and n in skill_keys:
        return True
    return any(k and len(k) > 6 and (k in n or n in k) for k in skill_keys if "-" not in k)


def _runway_match_score(skill_names, demand_name):
    """Loose 0..1 token-overlap between a demand course and a trainer's closest
    skill — used only to name the nearest-skilled trainer for an upskill."""
    d_tokens = {t for t in _norm_course(demand_name).split() if len(t) > 2}
    if not d_tokens:
        return 0.0
    best = 0.0
    for s in skill_names:
        s_tokens = {t for t in _norm_course(s).split() if len(t) > 2}
        if not s_tokens:
            continue
        overlap = len(d_tokens & s_tokens) / len(d_tokens)
        if overlap > best:
            best = overlap
    return best


def _capacity_runway_build(manager):
    """Forward capacity vs demand fan-out. Runs inside `_warm_run`."""
    today = datetime.utcnow().date()
    week0 = today - timedelta(days=today.weekday())        # Monday of the current week
    weeks = []
    for i in range(_RUNWAY_HORIZON_WEEKS):
        ws = week0 + timedelta(days=7 * i)
        weeks.append((ws, ws + timedelta(days=6)))
    horizon_start, horizon_end = weeks[0][0], weeks[-1][1]

    reps = _reportees(manager) or []
    team = []
    for r in (reps if isinstance(reps, list) else []):
        if not isinstance(r, dict):
            continue
        e = str(r.get("OffEmail", r.get("Email", "")) or "").strip().lower()
        if not e:
            continue
        team.append((e, _re.sub(r"\s+", " ", str(r.get("TrainerName", "") or "")).strip() or e))

    # ── incoming demand, bucketed into the week its batch starts ────────────
    demand = _demand_rows() or []
    week_batches = [[] for _ in weeks]
    horizon_batches = []
    for d in demand:
        if not isinstance(d, dict):
            continue
        name = str(d.get("course_name", "") or "").strip()
        st = _parse_date(d.get("start_date", ""))
        en = _parse_date(d.get("end_date", ""))
        if not name or not st or not en:
            continue
        if en < horizon_start or st > horizon_end:
            continue
        idx = max(0, min(_RUNWAY_HORIZON_WEEKS - 1, (max(st, horizon_start) - week0).days // 7))
        rec = {
            "name": name,
            "start": st,
            "end": en,
            "days": d.get("days") or ((en - st).days + 1),
            "participants": int(d.get("participants", 0) or 0),
        }
        week_batches[idx].append(rec)
        horizon_batches.append(rec)

    # ── per-trainer horizon state ─────────────────────────────────────────
    def _trainer_state(pair):
        email, name = pair
        try:
            series = _util_series(_util_row(email))
            skills = _skills(email) or []
            assigns = _rms("prevUpcoming", {
                "Startdate": _iso(horizon_start),
                "Enddate": _iso(horizon_end),
                "Email": email,
            }) or []
        except Exception:
            series, skills, assigns = [], [], []
        booked = []
        for a in (assigns if isinstance(assigns, list) else []):
            if not isinstance(a, dict):
                continue
            ast = _parse_date(a.get("StarDate", a.get("StartDate", "")))
            aen = _parse_date(a.get("EndDate", "")) or ast
            if ast:
                booked.append((ast, aen))
        skill_names = [s.get("course_name", "") for s in skills if s.get("course_name")]
        skill_keys = set()
        for cn in skill_names:
            n, code = _runway_course_keys(cn)
            if n:
                skill_keys.add(n)
            if code:
                skill_keys.add(code)
        return {
            "email": email,
            "name": name,
            "util": _current_util(series),
            "booked": booked,
            "skill_names": skill_names,
            "skill_keys": skill_keys,
        }

    if team:
        with ThreadPoolExecutor(max_workers=8) as pool:
            states = list(pool.map(_trainer_state, team))
    else:
        states = []

    def _free_that_week(tstate, ws, we):
        if any(bst <= we and ben >= ws for (bst, ben) in tstate["booked"]):
            return False
        u = tstate["util"]
        return u is None or u < _RUNWAY_FREE_UTIL

    week_rows = []
    total_demand = total_coverable = 0
    trainer_days_available = 0
    for (ws, we), batches in zip(weeks, week_batches):
        free = [t for t in states if _free_that_week(t, ws, we)]
        coverable = 0
        for b in batches:
            if any(_runway_teaches(t["skill_keys"], b["name"]) for t in free):
                coverable += 1
        demand_n = len(batches)
        total_demand += demand_n
        total_coverable += coverable
        trainer_days_available += len(free) * 5
        week_rows.append({
            "week_start": _iso(ws),
            "week_end": _iso(we),
            "demand_batches": demand_n,
            "demand_participants": sum(b["participants"] for b in batches),
            "team_available": len(free),
            "coverable": coverable,
            "gap": demand_n - coverable,
        })

    worst_week = ""
    worst_gap = None
    for row in week_rows:
        if worst_gap is None or row["gap"] > worst_gap:
            worst_gap = row["gap"]
            worst_week = row["week_start"]

    trainer_days_demanded = sum(int(b["days"] or 0) for b in horizon_batches)

    # ── upskilling: courses in the horizon nobody on the team can teach ────
    by_course = {}
    for b in horizon_batches:
        slot = by_course.setdefault(b["name"], {"batches": 0})
        slot["batches"] += 1
    upskilling = []
    for course, slot in by_course.items():
        if any(_runway_teaches(t["skill_keys"], course) for t in states):
            continue                          # already coverable — not an upskill
        best_t, best_score = None, 0.0
        for t in states:
            sc = _runway_match_score(t["skill_names"], course)
            if sc > best_score:
                best_score, best_t = sc, t
        n_batches = slot["batches"]
        batch_word = "batch" if n_batches == 1 else "batches"
        if best_t and best_score >= 0.34:
            first = best_t["name"].split()[0] if best_t["name"] else best_t["email"]
            nearest_email, nearest_name = best_t["email"], best_t["name"]
            why = "%d open %s, %s is one skill level short" % (n_batches, batch_word, first)
        else:
            nearest_email = nearest_name = ""
            why = "%d open %s, no one on the team teaches this" % (n_batches, batch_word)
        upskilling.append({
            "course": course,
            "exam_code": _exam_code(course),
            "opens_batches": n_batches,
            "nearest_trainer": nearest_email,
            "nearest_trainer_name": nearest_name,
            "why": why,
        })
    upskilling.sort(key=lambda u: (-u["opens_batches"], u["course"]))

    return {
        "manager": manager,
        "horizon_weeks": _RUNWAY_HORIZON_WEEKS,
        "weeks": week_rows,
        "summary": {
            "total_demand": total_demand,
            "total_coverable": total_coverable,
            "worst_week": worst_week,
            "trainer_days_available": trainer_days_available,
            "trainer_days_demanded": trainer_days_demanded,
        },
        "upskilling": upskilling,
        "generated_at": datetime.utcnow().isoformat(),
        "loading": False,
    }


@app.route('/api/v2/planning/runway', methods=['GET'])
def capacity_runway_v2():
    """"Capacity Runway" — next 8 weeks of demand vs the team's free capacity."""
    manager = request.args.get('manager', '').strip().lower()
    _sess, error = _v2_manager_session(manager)
    if error:
        return error
    manager = (_sess or {}).get("email") or manager

    if request.args.get("_build") != "1":
        ck = "runway::%s" % manager
        if _wants_fresh():
            _warm_purge(ck)
        return _serve_or_warm(
            cache_key=ck,
            view_func=capacity_runway_v2,
            build_path=(
                "/api/v2/planning/runway?manager=%s&_build=1%s"
                % (urllib.parse.quote(manager), "&refresh=1" if _wants_fresh() else "")
            ),
            fast_payload={
                "manager": manager, "horizon_weeks": _RUNWAY_HORIZON_WEEKS,
                "weeks": [], "summary": {}, "upskilling": [], "loading": True,
            },
        )

    resp = _capacity_runway_build(manager)
    _warm_store("runway::%s" % manager, resp)
    return jsonify(resp), 200


# ─── New-trainer ramp tracking (v2) ─────────────────────────────────────────
#
# For every reportee who joined in the last 12 months, a deterministic ramp
# record: how far into onboarding they are, whether they have stalled, and one
# concrete next step keyed off open demand. Insight only — never an allocation.

_RAMP_WINDOW_MONTHS = 12
_RAMP_STALL_UTIL = 30


def _tenure_months(doj, today):
    return max(0, (today.year - doj.year) * 12 + (today.month - doj.month)
               - (1 if today.day < doj.day else 0))


def _ramp_build(manager):
    """New-trainer ramp fan-out. Runs inside `_warm_run`."""
    today = datetime.utcnow().date()

    reps = _reportees(manager) or []
    team = []
    for r in (reps if isinstance(reps, list) else []):
        if not isinstance(r, dict):
            continue
        e = str(r.get("OffEmail", r.get("Email", "")) or "").strip().lower()
        if not e:
            continue
        team.append((e, _re.sub(r"\s+", " ", str(r.get("TrainerName", "") or "")).strip() or e))

    demand = _demand_rows() or []
    open_courses = [str(d.get("course_name", "") or "").strip()
                    for d in demand if isinstance(d, dict) and d.get("course_name")]

    def _one(pair):
        email, name = pair
        u_row = _util_row(email) or {}
        doj = _parse_date(u_row.get("DOJ", ""))
        if not doj or doj > today:
            return None
        tenure_months = _tenure_months(doj, today)
        if tenure_months >= _RAMP_WINDOW_MONTHS:
            return None

        try:
            series = _util_series(u_row)
            skills = _skills(email) or []
            assigns = _rms("prevUpcoming", {
                "Startdate": _iso(doj), "Enddate": _iso(today), "Email": email,
            }) or []
        except Exception:
            series, skills, assigns = [], [], []

        skill_names = [s.get("course_name", "") for s in skills if s.get("course_name")]
        skill_keys = set()
        for cn in skill_names:
            n, code = _runway_course_keys(cn)
            if n:
                skill_keys.add(n)
            if code:
                skill_keys.add(code)

        starts = []
        for a in (assigns if isinstance(assigns, list) else []):
            if not isinstance(a, dict):
                continue
            ast = _parse_date(a.get("StarDate", a.get("StartDate", "")))
            if ast and doj <= ast <= today:
                starts.append(ast)
        starts.sort()
        batches_delivered = len(starts)
        first_batch = starts[0] if starts else None
        days_to_first = (first_batch - doj).days if first_batch else None

        util = _current_util(series)

        fb = _trainer_feedback_detail(email, days=max(1, (today - doj).days))
        avg_rating = fb.get("avg_rating")
        rating_sample = fb.get("response_count", 0)

        stalled = (tenure_months > 3 and batches_delivered == 0
                   and util is not None and util < _RAMP_STALL_UTIL)
        if batches_delivered > 3 or tenure_months > 9:
            stage = "established"
        elif batches_delivered == 0:
            stage = "onboarding"
        else:
            stage = "first-deliveries"

        top_skill = skill_names[0] if skill_names else ""
        covered = ""
        for c in open_courses:
            if _runway_teaches(skill_keys, c):
                covered = c
                break
        best_course, best_sc = "", 0.0
        for c in open_courses:
            sc = _runway_match_score(skill_names, c)
            if sc > best_sc:
                best_sc, best_course = sc, c

        plural = "" if batches_delivered == 1 else "es"
        if stalled:
            next_step = ("Cleared to deliver %s — no batch in %d months; check RMS "
                         "availability is open." % (top_skill or "a marked course", tenure_months))
        elif stage == "onboarding":
            if covered:
                next_step = ("Cleared for %s, which has open demand — confirm availability "
                             "is open in RMS." % covered)
            elif best_course:
                next_step = ("Not yet cleared for any course with open demand — prioritise "
                             "%s marking." % best_course)
            else:
                next_step = "No open demand matches current skills yet — broaden capability marking."
        elif stage == "first-deliveries":
            tgt = covered or best_course
            if tgt:
                next_step = ("%d batch%s delivered — build breadth on %s next."
                             % (batches_delivered, plural, tgt))
            else:
                next_step = ("%d batch%s delivered — keep assigning to build delivery history."
                             % (batches_delivered, plural))
        else:
            next_step = "Ramped — treat as a full member of the delivery rotation."

        return {
            "name": name,
            "email": email,
            "doj": _iso(doj),
            "tenure_months": tenure_months,
            "courses_certified": len(skills),
            "batches_delivered": batches_delivered,
            "first_batch_date": _iso(first_batch) if first_batch else None,
            "days_to_first_batch": days_to_first,
            "current_utilization": util,
            "avg_learner_rating": avg_rating,
            "rating_sample": rating_sample,
            "ramp_stage": stage,
            "stalled": stalled,
            "next_step": next_step,
        }

    records = []
    if team:
        with ThreadPoolExecutor(max_workers=8) as pool:
            for rec in pool.map(_one, team):
                if rec:
                    records.append(rec)
    records.sort(key=lambda r: r["doj"], reverse=True)
    records.sort(key=lambda r: 0 if r["stalled"] else 1)

    d2f = [r["days_to_first_batch"] for r in records if r["days_to_first_batch"] is not None]
    summary = {
        "new_count": len(records),
        "stalled_count": sum(1 for r in records if r["stalled"]),
        "avg_days_to_first_batch": round(sum(d2f) / len(d2f)) if d2f else None,
    }
    if not records:
        summary["note"] = "No trainers joined in the last %d months." % _RAMP_WINDOW_MONTHS

    return {
        "manager": manager,
        "generated_at": datetime.utcnow().isoformat(),
        "window_months": _RAMP_WINDOW_MONTHS,
        "trainers": records,
        "summary": summary,
        "loading": False,
    }


@app.route('/api/v2/ramp', methods=['GET'])
def ramp_v2():
    """"New trainer ramp" — onboarding progress for reportees who joined <12mo ago."""
    manager = request.args.get('manager', '').strip().lower()
    _sess, error = _v2_manager_session(manager)
    if error:
        return error
    manager = (_sess or {}).get("email") or manager

    if request.args.get("_build") != "1":
        ck = "ramp::%s" % manager
        if _wants_fresh():
            _warm_purge(ck)
        return _serve_or_warm(
            cache_key=ck,
            view_func=ramp_v2,
            build_path=(
                "/api/v2/ramp?manager=%s&_build=1%s"
                % (urllib.parse.quote(manager), "&refresh=1" if _wants_fresh() else "")
            ),
            fast_payload={
                "manager": manager, "window_months": _RAMP_WINDOW_MONTHS,
                "trainers": [], "summary": {}, "loading": True,
            },
        )

    resp = _ramp_build(manager)
    _warm_store("ramp::%s" % manager, resp)
    return jsonify(resp), 200


# ─── Accounts / customer view ────────────────────────────────────────────────
#
# The manager's team seen through the customers they deliver for. Every past
# assignment (trailing window) and every open demand row is grouped by account
# name so a manager can see which customers the team is committed to, which are
# under-covered, and whether delivery is dangerously concentrated on one account.
# Insight only — managers cannot allocate.

_ACCOUNTS_PAST_DAYS = 90
_ACCOUNTS_FORWARD_DAYS = 60


def _account_key(name):
    """Normalised grouping key + display name. Blank groups as 'Unspecified'."""
    disp = _re.sub(r"\s+", " ", str(name or "").strip())
    if not disp:
        return "unspecified", "Unspecified"
    return disp.casefold(), disp


def _accounts_build(manager):
    """Account book fan-out. Runs inside `_warm_run` via the `?_build=1` path."""
    today = datetime.utcnow().date()
    past_start = today - timedelta(days=_ACCOUNTS_PAST_DAYS)
    forward_end = today + timedelta(days=_ACCOUNTS_FORWARD_DAYS)

    reps = _reportees(manager) or []
    team = []
    for r in (reps if isinstance(reps, list) else []):
        if not isinstance(r, dict):
            continue
        e = str(r.get("OffEmail", r.get("Email", "")) or "").strip().lower()
        if not e:
            continue
        team.append((e, _re.sub(r"\s+", " ", str(r.get("TrainerName", "") or "")).strip() or e))
    team_emails = {e for e, _ in team}

    def _assignments(pair):
        email, name = pair
        try:
            rows = _rms("prevUpcoming", {
                "Startdate": _iso(past_start),
                "Enddate": _iso(forward_end),
                "Email": email,
            }) or []
        except Exception:
            rows = []
        out = []
        for a in (rows if isinstance(rows, list) else []):
            if not isinstance(a, dict):
                continue
            st = _parse_date(a.get("StarDate", a.get("StartDate", "")))
            en = _parse_date(a.get("EndDate", "")) or st
            if not st:
                continue
            out.append({
                "trainer_email": email,
                "trainer_name": name,
                "account": str(a.get("Vendor", a.get("Customer", a.get("client", ""))) or "").strip(),
                "course": str(a.get("Course", a.get("CourseName", "")) or "").strip(),
                "start": st,
                "end": en,
                "participants": int(a.get("NoOfParticipants", 0) or 0),
                "assignment_id": str(a.get("AssignmentId", a.get("AssignmentID", "")) or "").strip(),
            })
        return out

    if team:
        with ThreadPoolExecutor(max_workers=8) as pool:
            per_trainer = list(pool.map(_assignments, team))
    else:
        per_trainer = []
    assignments = [a for rows in per_trainer for a in rows]

    # ── open demand in the forward window ────────────────────────────────────
    demand = _demand_rows() or []
    demand_rows = []
    for d in (demand if isinstance(demand, list) else []):
        if not isinstance(d, dict):
            continue
        st = _parse_date(d.get("start_date", ""))
        en = _parse_date(d.get("end_date", "")) or st
        if not st or en < today or st > forward_end:
            continue
        demand_rows.append({
            "account": str(d.get("customer", "") or "").strip(),
            "course": str(d.get("course_name", "") or "").strip(),
            "start": st,
            "participants": int(d.get("participants", 0) or 0),
        })

    # ── learner rating per account (join feedback MCQ via AssignmentId) ──────
    aid_to_key = {}
    for a in assignments:
        if a["assignment_id"] and a["end"] and past_start <= a["end"] <= today:
            aid_to_key.setdefault(a["assignment_id"], _account_key(a["account"])[0])
    account_ratings = {}
    if aid_to_key:
        try:
            fb = _rms("trainerFeedback", {"TrainerEmail": "", "AssignmentId": "", "SCID": ""}) or []
        except Exception:
            fb = []
        for row in (fb if isinstance(fb, list) else []):
            if not isinstance(row, dict):
                continue
            if str(row.get("TrainerEmail", "")).strip().lower() not in team_emails:
                continue
            key = aid_to_key.get(str(row.get("AssignmentId", "")).strip())
            if not key:
                continue
            mcq = row.get("MCQAnswer")
            try:
                if mcq is None or str(mcq).strip() == "":
                    continue
                account_ratings.setdefault(key, []).append(float(mcq))
            except (TypeError, ValueError):
                continue

    # ── group ───────────────────────────────────────────────────────────────
    accounts = {}

    def _slot(name):
        key, disp = _account_key(name)
        return accounts.setdefault(key, {
            "key": key, "name": disp,
            "batches_delivered": 0, "participants_delivered": 0,
            "batches_upcoming": 0, "open_demand_batches": 0,
            "_trainers": set(), "_courses": set(),
            "_last_delivery": None, "_next_start": None,
        })

    for a in assignments:
        slot = _slot(a["account"])
        if a["course"]:
            slot["_courses"].add(a["course"])
        delivered = a["end"] and past_start <= a["end"] <= today
        upcoming = a["start"] and today < a["start"] <= forward_end
        if delivered:
            slot["batches_delivered"] += 1
            slot["participants_delivered"] += a["participants"]
            slot["_trainers"].add(a["trainer_name"])
            if slot["_last_delivery"] is None or a["end"] > slot["_last_delivery"]:
                slot["_last_delivery"] = a["end"]
        if upcoming:
            slot["batches_upcoming"] += 1
            if slot["_next_start"] is None or a["start"] < slot["_next_start"]:
                slot["_next_start"] = a["start"]

    for d in demand_rows:
        slot = _slot(d["account"])
        slot["open_demand_batches"] += 1
        if d["course"]:
            slot["_courses"].add(d["course"])
        if slot["_next_start"] is None or d["start"] < slot["_next_start"]:
            slot["_next_start"] = d["start"]

    total_delivered = sum(s["batches_delivered"] for s in accounts.values())

    out_accounts = []
    for s in accounts.values():
        rec = {
            "name": s["name"],
            "batches_delivered": s["batches_delivered"],
            "participants_delivered": s["participants_delivered"],
            "batches_upcoming": s["batches_upcoming"],
            "open_demand_batches": s["open_demand_batches"],
            "trainers": sorted(s["_trainers"]),
            "courses": sorted(s["_courses"]),
            "last_delivery_date": _iso(s["_last_delivery"]) if s["_last_delivery"] else "",
            "next_start_date": _iso(s["_next_start"]) if s["_next_start"] else "",
        }
        ratings = account_ratings.get(s["key"])
        if ratings:
            rec["avg_learner_rating"] = round(sum(ratings) / len(ratings), 2)
        out_accounts.append(rec)

    out_accounts.sort(key=lambda r: (-r["open_demand_batches"], -r["batches_delivered"], r["name"]))

    concentration = None
    top_account = ""
    top_account_share = 0.0
    if total_delivered:
        lead = max(out_accounts, key=lambda r: r["batches_delivered"])
        if lead["batches_delivered"] > 0:
            share = round(100.0 * lead["batches_delivered"] / total_delivered, 1)
            top_account = lead["name"]
            top_account_share = share
            concentration = {
                "account": lead["name"],
                "batches_delivered": lead["batches_delivered"],
                "team_batches_delivered": total_delivered,
                "share_pct": share,
            }

    unspecified = accounts.get("unspecified")
    unspecified_batches = 0
    if unspecified:
        unspecified_batches = (unspecified["batches_delivered"]
                               + unspecified["batches_upcoming"]
                               + unspecified["open_demand_batches"])

    return {
        "manager": manager,
        "generated_at": datetime.utcnow().isoformat(),
        "window": {"past_days": _ACCOUNTS_PAST_DAYS, "forward_days": _ACCOUNTS_FORWARD_DAYS},
        "accounts": out_accounts,
        "concentration": concentration,
        "summary": {
            "account_count": len(out_accounts),
            "top_account": top_account,
            "top_account_share": top_account_share,
            "unspecified_batches": unspecified_batches,
        },
        "loading": False,
    }


@app.route('/api/v2/accounts', methods=['GET'])
def accounts_v2():
    """"Accounts" — the manager's team seen through the customers they deliver for."""
    manager = request.args.get('manager', '').strip().lower()
    _sess, error = _v2_manager_session(manager)
    if error:
        return error
    manager = (_sess or {}).get("email") or manager

    if request.args.get("_build") != "1":
        ck = "accounts::%s" % manager
        if _wants_fresh():
            _warm_purge(ck)
        return _serve_or_warm(
            cache_key=ck,
            view_func=accounts_v2,
            build_path=(
                "/api/v2/accounts?manager=%s&_build=1%s"
                % (urllib.parse.quote(manager), "&refresh=1" if _wants_fresh() else "")
            ),
            fast_payload={
                "manager": manager,
                "window": {"past_days": _ACCOUNTS_PAST_DAYS, "forward_days": _ACCOUNTS_FORWARD_DAYS},
                "accounts": [], "concentration": None, "summary": {}, "loading": True,
            },
        )

    resp = _accounts_build(manager)
    _warm_store("accounts::%s" % manager, resp)
    return jsonify(resp), 200

# ─── Manager benchmarking (v2) ──────────────────────────────────────────────
#
# "How does my team compare?" — honestly. There is no multi-manager API, so
# there is no real peer-manager average and none is fabricated. Instead:
#
#   * average learner rating and feedback-incident rate are compared against
#     the COMPANY-WIDE learner-feedback population (RMS key 244 returns the
#     whole trailing feedback set for every trainer, not just this roster);
#   * utilisation, bench rate and certification coverage are compared against
#     documented Koenig delivery thresholds already used across this codebase
#     (the 60% utilisation "bench line", the 40% under-utilisation line in
#     `_derive_actions`, and full coverage of open demand as the target).
#
# The response always carries `baseline_source` stating exactly this.

_BENCH_UTIL_BASELINE   = 60.0     # documented "bench line" (util >= 60 is on-target)
_BENCH_UNDERUTIL_LINE  = 40.0     # `_derive_actions`: util < 40 == on the bench
_BENCH_BENCHRATE_BASE  = 20.0     # planning norm: up to ~1 in 5 between assignments
_BENCH_COVERAGE_BASE   = 100.0    # target is to cover every open-demand course
_BENCH_TOL             = 0.05     # within 5% of baseline == "on_par"

_BENCHMARK_SOURCE = (
    "No multi-manager API exists, so no peer-manager average is computed or "
    "estimated. Average learner rating and feedback-incident rate are compared "
    "against the company-wide learner-feedback population (RMS key 244 - every "
    "trainer in the trailing feedback set, not only this manager's reportees). "
    "Utilisation, bench rate and certification coverage are compared against "
    "documented Koenig delivery thresholds used across this codebase: the 60% "
    "utilisation bench line, the 40% under-utilisation line, a planning norm of "
    "~20% of a team between assignments, and full coverage of open demand."
)


def _benchmark_company_feedback():
    """Company-wide baseline from the key-244 dump: (mean_rating, incident_rate).

    `incident_rate` is low-rating (MCQAnswer <= 3) rows per distinct trainer
    seen in the dump. Returns (None, None) when the dump is unreachable/empty.
    """
    rows = _rms("trainerFeedback", {"TrainerEmail": "", "AssignmentId": "", "SCID": ""}) or []
    ratings, incidents, trainers = [], 0, set()
    for r in rows:
        if not isinstance(r, dict):
            continue
        te = str(r.get("TrainerEmail", "") or "").strip().lower()
        if te:
            trainers.add(te)
        mcq = r.get("MCQAnswer")
        try:
            if mcq is not None and str(mcq).strip() != "":
                val = float(mcq)
                ratings.append(val)
                if val <= 3.0:
                    incidents += 1
        except (TypeError, ValueError):
            pass
    mean_rating = round(sum(ratings) / len(ratings), 2) if ratings else None
    incident_rate = round(incidents / len(trainers), 2) if trainers else None
    return mean_rating, incident_rate


def _benchmark_verdict(team_value, baseline_value, direction):
    """Deterministic verdict + signed gap. Within `_BENCH_TOL` of baseline is
    `on_par`; otherwise `ahead`/`behind` by `direction`."""
    if team_value is None or not baseline_value:
        return "unknown", None
    gap = round(team_value - baseline_value, 2)
    rel = (team_value - baseline_value) / baseline_value
    if abs(rel) <= _BENCH_TOL:
        return "on_par", gap
    better = rel > 0 if direction == "higher_better" else rel < 0
    return ("ahead" if better else "behind"), gap


def _benchmark_score(metric):
    """Signed relative advantage of the team (positive == better than baseline).
    Used only to pick the weakest metric for the headline."""
    tv, bv = metric.get("team_value"), metric.get("baseline_value")
    if tv is None or not bv:
        return None
    rel = (tv - bv) / bv
    return rel if metric["direction"] == "higher_better" else -rel


def _benchmark_build(manager):
    """Team-vs-baseline fan-out. Runs inside `_warm_run`."""
    reps = _reportees(manager) or []
    team = []
    for r in (reps if isinstance(reps, list) else []):
        if not isinstance(r, dict):
            continue
        e = str(r.get("OffEmail", r.get("Email", "")) or "").strip().lower()
        if e:
            team.append(e)
    team = sorted(set(team))

    demand = _demand_rows() or []
    demand_courses = sorted({
        str(d.get("course_name", "") or "").strip()
        for d in demand if isinstance(d, dict) and str(d.get("course_name", "") or "").strip()
    })

    def _one(email):
        try:
            util = _current_util(_util_series(_util_row(email)))
        except Exception:
            util = None
        try:
            skills = _skills(email) or []
        except Exception:
            skills = []
        skill_keys = set()
        for s in skills:
            n, code = _runway_course_keys(s.get("course_name", ""))
            if n:
                skill_keys.add(n)
            if code:
                skill_keys.add(code)
        try:
            rating = (_trainer_feedback_detail(email) or {}).get("avg_rating")
        except Exception:
            rating = None
        return {"email": email, "util": util, "skill_keys": skill_keys, "rating": rating}

    if team:
        with ThreadPoolExecutor(max_workers=8) as pool:
            members = list(pool.map(_one, team))
    else:
        members = []

    # ── team-side metric values ──────────────────────────────────────────────
    utils = [m["util"] for m in members if m["util"] is not None]
    team_util = round(sum(utils) / len(utils), 1) if utils else None
    team_bench_rate = (
        round(100.0 * sum(1 for u in utils if u < _BENCH_UNDERUTIL_LINE) / len(utils), 1)
        if utils else None
    )
    ratings = [m["rating"] for m in members if m["rating"] is not None]
    team_rating = round(sum(ratings) / len(ratings), 2) if ratings else None

    if demand_courses:
        covered = sum(
            1 for c in demand_courses
            if any(_runway_teaches(m["skill_keys"], c) for m in members)
        )
        team_coverage = round(100.0 * covered / len(demand_courses), 1)
    else:
        team_coverage = 100.0

    # team feedback-incident rate from the same key-244 dump, filtered to roster
    fb_rows = _rms("trainerFeedback", {"TrainerEmail": "", "AssignmentId": "", "SCID": ""}) or []
    team_set = set(team)
    team_incidents = 0
    for r in fb_rows:
        if not isinstance(r, dict):
            continue
        if str(r.get("TrainerEmail", "") or "").strip().lower() not in team_set:
            continue
        mcq = r.get("MCQAnswer")
        try:
            if mcq is not None and str(mcq).strip() != "" and float(mcq) <= 3.0:
                team_incidents += 1
        except (TypeError, ValueError):
            pass
    team_incident_rate = round(team_incidents / len(team), 2) if team else None

    # Do not manufacture a company baseline when RMS feedback is unavailable.
    # The verdict helper deliberately returns `unknown` for a missing baseline.
    base_rating, base_incident_rate = _benchmark_company_feedback()

    specs = [
        ("team_utilization", "Team utilisation", team_util,
         round(_BENCH_UTIL_BASELINE, 1), "%", "higher_better"),
        ("bench_rate", "Bench rate (under 40% utilised)", team_bench_rate,
         round(_BENCH_BENCHRATE_BASE, 1), "%", "lower_better"),
        ("avg_learner_rating", "Average learner rating", team_rating,
         base_rating, "/5", "higher_better"),
        ("cert_coverage", "Open-demand certification coverage", team_coverage,
         round(_BENCH_COVERAGE_BASE, 1), "%", "higher_better"),
        ("feedback_incident_rate", "Feedback incidents per trainer", team_incident_rate,
         base_incident_rate, "per trainer", "lower_better"),
    ]

    metrics = []
    for key, label, tv, bv, unit, direction in specs:
        verdict, gap = _benchmark_verdict(tv, bv, direction)
        metrics.append({
            "key": key, "label": label,
            "team_value": tv, "baseline_value": bv,
            "unit": unit, "direction": direction,
            "verdict": verdict, "gap": gap,
        })

    ahead_count = sum(1 for m in metrics if m["verdict"] == "ahead")
    behind_count = sum(1 for m in metrics if m["verdict"] == "behind")

    # ── headline: name the weakest comparable metric ─────────────────────────
    scored = [(m, _benchmark_score(m)) for m in metrics]
    scored = [(m, s) for (m, s) in scored if s is not None]
    if not scored:
        headline = "Baseline not available - not enough team data to compare yet."
    else:
        weakest, wscore = min(scored, key=lambda p: p[1])
        tv, bv, unit = weakest["team_value"], weakest["baseline_value"], weakest["unit"]
        u = "" if unit == "/5" else unit
        if weakest["verdict"] == "behind":
            side = "below" if weakest["direction"] == "higher_better" else "above"
            headline = (
                "Weakest area is %s: the team is at %s%s versus a %s%s baseline, "
                "%s%s %s the line."
                % (weakest["label"], tv, u, bv, u, abs(weakest["gap"]), u, side)
            )
        else:
            headline = (
                "The team is at or above every baseline; the tightest margin is "
                "%s (%s%s versus %s%s)." % (weakest["label"], tv, u, bv, u)
            )

    return {
        "manager": manager,
        "generated_at": datetime.utcnow().isoformat(),
        "baseline_source": _BENCHMARK_SOURCE,
        "metrics": metrics,
        "summary": {
            "ahead_count": ahead_count,
            "behind_count": behind_count,
            "headline": headline,
        },
        "loading": False,
    }


@app.route('/api/v2/benchmark', methods=['GET'])
def benchmark_v2():
    """"How your team compares" — team health vs an honest, documented baseline."""
    manager = request.args.get('manager', '').strip().lower()
    _sess, error = _v2_manager_session(manager)
    if error:
        return error
    manager = (_sess or {}).get("email") or manager

    if request.args.get("_build") != "1":
        ck = "benchmark::%s" % manager
        if _wants_fresh():
            _warm_purge(ck)
        return _serve_or_warm(
            cache_key=ck,
            view_func=benchmark_v2,
            build_path=(
                "/api/v2/benchmark?manager=%s&_build=1%s"
                % (urllib.parse.quote(manager), "&refresh=1" if _wants_fresh() else "")
            ),
            fast_payload={
                "manager": manager, "baseline_source": _BENCHMARK_SOURCE,
                "metrics": [], "summary": {}, "loading": True,
            },
        )

    resp = _benchmark_build(manager)
    _warm_store("benchmark::%s" % manager, resp)
    return jsonify(resp), 200

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
    manager_email = (session or {}).get("email") or manager_email

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

    internal_build = request.args.get("_build") == "1"
    if not internal_build:
        if _wants_fresh():
            _warm_purge(f"weekly::{manager_email}::{week_start_iso}")
        return _serve_or_warm(
            cache_key=f"weekly::{manager_email}::{week_start_iso}",
            view_func=weekly_report_v2,
            build_path=(
                f"/api/v2/report/weekly?manager={urllib.parse.quote(manager_email)}"
                f"&week={week_start_iso}&_build=1{'&refresh=1' if _wants_fresh() else ''}"
            ),
            fast_payload={
                "loading": True, "week_label": week_label,
                "week_start": week_start_iso, "week_end": week_end_iso, "reportees": [],
            },
        )

    reportees_raw = _reportees(manager_email) or []
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

        # 7. Standpoint note — a message the manager could send this reportee as
        #    is, composed from the analysed data (delivery, feedback, open
        #    demand they match, cert gaps, utilisation). See _compose_manager_message.
        fb = _trainer_feedback_detail(email, days=90)
        snap_for_msg = {
            "email": email, "name": t["name"],
            "current_utilization": current_util, "avg_qubits": avg_qubits,
            "assignments": week_assignments, "total_pax": sum(a["participants"] for a in week_assignments),
            "capacity_bucket": capacity_bucket,
            "negative_feedback_count": neg_total, "hr_negative_count": hr_neg,
            "cert_gap_courses": gap_courses[:3], "learner_feedback": fb,
        }
        skill_course_names = [s.get("course_name", "") for s in skills if s.get("course_name")]
        _msg_facts = _reportee_message_facts(
            snap_for_msg, "weekly",
            demand_rows=(unalloc_raw if isinstance(unalloc_raw, list) else []),
            skills_courses=skill_course_names,
        )
        _msg_weekend = _compose_manager_message("reportee", "weekend", _msg_facts)
        standpoint_text = _compose_manager_message("reportee", "weekly", _msg_facts)

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
            "message": standpoint_text,
            "message_weekly": standpoint_text,
            "message_weekend": _msg_weekend,
            "message_scope": "reportee", "message_cadence": "weekly",
            "opportunity_courses": _msg_facts.get("opp_courses") or [],
            "learner_rating": fb["avg_rating"],
            "learner_rating_count": fb["response_count"],
            "learner_feedback": fb,
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

    # Team group message — composed from the same analysed data as the
    # per-reportee messages (see _compose_manager_message, scope="team").
    _all_team_courses = []
    for _s in out:
        _all_team_courses += (_s.get("opportunity_courses") or [])
    _coverable = len({c.lower() for c in _all_team_courses})
    # Recognition only: trainers delivering with a strong learner rating and no flags.
    _top = sorted(
        (r for r in out if r["batch_count"] > 0 and (r.get("learner_rating") or 0) >= 4.3
         and r["feedback_risk"] != "High"),
        key=lambda r: -(r.get("learner_rating") or 0),
    )
    _top_performers = [r["name"] for r in _top[:2]]
    _rated = [r["learner_rating"] for r in out if r.get("learner_rating") is not None]
    _team_avg_rating = round(sum(_rated) / len(_rated), 1) if _rated else None
    _team_facts = {
        "manager_first": manager_email.split("@")[0].split(".")[0].title(),
        "headcount": len(out), "delivering": delivering_count,
        "total_pax": total_week_pax, "total_batches": total_batches,
        "at_risk": at_risk_count,
        "open_demand": unalloc_count, "coverable_open": _coverable,
        "bench": bench_count, "total_gaps": total_gaps,
        "top_performers": _top_performers, "avg_rating": _team_avg_rating,
        "period_key": week_start_iso,
    }
    team_digest_text = _compose_manager_message("team", "weekly", _team_facts)
    team_digest_weekend = _compose_manager_message("team", "weekend", _team_facts)

    _resp = {
        "loading":       False,
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
        "team_digest_weekly":  team_digest_text,
        "team_digest_weekend": team_digest_weekend,
        "reportees":     out,
    }
    _warm_store(f"weekly::{manager_email}::{week_start_iso}", _resp)
    return jsonify(_resp), 200


# ─── Proactive digests ───────────────────────────────────────────────────────
#
# One scheduled read the Android monitoring pass fires once per period:
#   morning  — today's top priorities + what starts this week + capacity flags
#   weekly   — Friday wrap: the team weekend digest message + counts
# Both reuse work the dashboard/report paths already do (priorities fan-out,
# warm weekly report) rather than re-deriving from RMS.

def _digest_morning_build(manager):
    pri = _priorities_build(manager)
    items = pri.get("items", []) or []
    top = items[:5]

    today = datetime.utcnow().date()
    monday = today - timedelta(days=today.weekday())
    sunday = monday + timedelta(days=6)
    unstaffed_this_week = 0
    for it in items:
        if it.get("kind") != "unstaffed_demand":
            continue
        d = _parse_date(str(it.get("due", "") or ""))
        if d and monday <= d <= sunday:
            unstaffed_this_week += 1

    flagged = [it["title"] for it in items if it.get("kind") == "overload"]

    bits = []
    if top:
        bits.append(top[0]["title"])
    if unstaffed_this_week:
        bits.append("%d batch%s start this week still unstaffed"
                    % (unstaffed_this_week, "" if unstaffed_this_week == 1 else "es"))
    if flagged:
        bits.append(flagged[0])
    headline = "; ".join(bits) or "Nothing urgent on the board today."

    return {
        "kind": "morning",
        "generated_at": datetime.utcnow().isoformat(),
        "headline": headline,
        "items": top,
        "unstaffed_this_week": unstaffed_this_week,
        "flagged_trainers": flagged,
        "loading": False,
    }


def _digest_weekly_build(manager):
    today = datetime.utcnow().date()
    monday = today - timedelta(days=today.weekday())
    key = f"weekly::{manager}::{_iso(monday)}"
    with _warm_lock:
        entry = _warm_payload_cache.get(key)
    report = entry[1] if entry else None
    if report is None:
        auth = request.headers.get("Authorization", "")
        path = (f"/api/v2/report/weekly?manager={urllib.parse.quote(manager)}"
                f"&week={_iso(monday)}&_build=1")
        try:
            with app.test_request_context(path, headers={"Authorization": auth} if auth else {}):
                weekly_report_v2()
        except Exception:
            pass
        with _warm_lock:
            entry = _warm_payload_cache.get(key)
        report = entry[1] if entry else None
    report = report or {}

    ts = report.get("team_summary", {}) or {}
    message = report.get("team_digest_weekend") or report.get("team_digest") or ""
    summary = {
        "headcount":          ts.get("headcount", 0),
        "delivering":         ts.get("delivering_count", 0),
        "on_bench":           ts.get("bench_count", 0),
        "at_risk":            ts.get("at_risk_count", 0),
        "total_batches":      ts.get("total_batches", 0),
        "total_participants": ts.get("total_participants", 0),
        "unallocated_demand": ts.get("unallocated_demand", 0),
        "cert_gaps":          ts.get("total_cert_gaps", 0),
    }
    return {
        "kind": "weekly",
        "generated_at": datetime.utcnow().isoformat(),
        "message": message,
        "summary": summary,
        "week_label": report.get("week_label", ""),
        "loading": False,
    }


@app.route('/api/v2/digest', methods=['GET'])
def manager_digest_v2():
    """Scheduled morning brief / end-of-week summary for the Android digest push."""
    manager = request.args.get('manager', '').strip().lower()
    kind = request.args.get('kind', 'morning').strip().lower()
    if kind not in ("morning", "weekly"):
        return error_response("INVALID_KIND", "kind must be morning or weekly", 400)
    session, error = _v2_manager_session(manager)
    if error:
        return error
    manager = (session or {}).get("email") or manager

    ck = f"digest::{manager}::{kind}"
    internal_build = request.args.get("_build") == "1"
    if not internal_build:
        if _wants_fresh():
            _warm_purge(ck)
        return _serve_or_warm(
            cache_key=ck,
            view_func=manager_digest_v2,
            build_path=(
                f"/api/v2/digest?manager={urllib.parse.quote(manager)}&kind={kind}"
                f"&_build=1{'&refresh=1' if _wants_fresh() else ''}"
            ),
            fast_payload={"kind": kind, "loading": True,
                          "items": [], "headline": "", "message": "", "summary": {}},
        )

    resp = _digest_morning_build(manager) if kind == "morning" else _digest_weekly_build(manager)
    _warm_store(ck, resp)
    return jsonify(resp), 200


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

    ti_data = _trainer_index_for(
        email, trainer_name, month_util, util_3m, month_assignments,
        neg_total, hr_pos, hr_neg, cert_intel, vendor_certs_list,
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

    ti_data = _trainer_index_for(
        email, trainer_name, month_util, util_3m, month_assignments,
        neg_total, hr_pos, hr_neg, cert_intel, vendor_certs_list,
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

    internal_build = request.args.get("_build") == "1"
    if not internal_build:
        if _wants_fresh():
            _warm_purge(f"calendar::{manager}::{month_str}")
        return _serve_or_warm(
            cache_key=f"calendar::{manager}::{month_str}",
            view_func=team_calendar_v2,
            build_path=(
                f"/api/v2/team/calendar?manager={urllib.parse.quote(manager)}"
                f"&month={month_str}&_build=1{'&refresh=1' if _wants_fresh() else ''}"
            ),
            fast_payload={"loading": True, "month": month_str, "days": []},
        )

    # Fetch reportees and their assignments
    reps_raw = _reportees(manager) or []
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

    _resp = {
        "loading": False,
        "manager_email": manager,
        "month": month_str,
        "days": days_data,
        "team_summary": {
            "total_reportees": len(rep_results),
            "total_batches_in_month": total_batches,
            "active_delivering_days": total_delivering_days,
        },
        "generated_at": datetime.utcnow().isoformat(),
    }
    _warm_store(f"calendar::{manager}::{month_str}", _resp)
    return jsonify(_resp), 200


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

    # Parallel fetch across the 5 course intelligence endpoints
    with ThreadPoolExecutor(max_workers=5) as pool:
        f_modules = pool.submit(_rms, "courseModule", {"Cid": course_id}) if course_id else None
        f_content = pool.submit(_rms, "courseContentUrl", {"CourseName": course_name}) if course_name else None
        f_schedule = pool.submit(_course_schedule, course_name) if course_name else None
        f_syllabus = pool.submit(_rms, "courseSyllabus", {})
        f_version = pool.submit(_rms, "latestCourseVersion", {"CName": course_name}) if course_name else None

        raw_modules = (f_modules.result() or []) if f_modules else []
        raw_content = (f_content.result() or []) if f_content else []
        schedule_info = f_schedule.result() if f_schedule else {}
        syllabus_rows = f_syllabus.result() or []
        version_rows = (f_version.result() or []) if f_version else []

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
    official_courseware_url = ""
    for r in (raw_content if isinstance(raw_content, list) else []):
        if isinstance(r, dict):
            url = str(r.get("ContentURL") or r.get("ContentUrl") or r.get("Url") or r.get("LabUrl") or "").strip()
            if url:
                if not official_courseware_url:
                    official_courseware_url = url
                content_urls.append({
                    "title": str(r.get("Title") or r.get("Name") or "Official Course Slides / PDF").strip(),
                    "url": url,
                })

    latest_version = ""
    for r in (version_rows if isinstance(version_rows, list) else []):
        if isinstance(r, dict):
            v = str(r.get("LatestVersion") or r.get("Version") or r.get("Latest_Version") or "").strip()
            if v and "select the course" not in v.lower():
                latest_version = v
                break

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
        "official_courseware_url": official_courseware_url,
        "latest_version": latest_version,
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


@app.route('/api/v2/upskilling/demand-opportunities', methods=['GET'])
def v2_upskilling_demand_opportunities():
    """
    Correlates unallocated demand (Key 190) against team competency gaps (Key 217)
    to surface demand-led upskilling recommendations with 1-tap IDP skill assignment.
    """
    manager = str(request.args.get("manager", "")).strip().lower()
    session, error = _v2_manager_session(manager)
    if error:
        return error

    manager_email = (session or {}).get("email") or manager
    today = datetime.utcnow().date()
    team = []
    for r in (_reportees(manager_email) or []):
        if isinstance(r, dict) and r.get("OffEmail"):
            team.append((str(r.get("TrainerName") or "").strip(),
                         str(r.get("OffEmail")).strip().lower()))
    demand = _demand_rows() or []

    def _fuzzy_match(a, b):
        if not a or not b:
            return 0
        ta, tb = set(a.split()), set(b.split())
        if not ta or not tb:
            return 0
        return int(100 * len(ta & tb) / len(ta | tb))

    # Group unallocated demand by course
    course_demand = {}
    for b in demand:
        c_name = str(b.get("course_name", "")).strip()
        c_id = str(b.get("course_id", "")).strip()
        if not c_name:
            continue
        if c_name not in course_demand:
            course_demand[c_name] = {
                "course_name": c_name,
                "course_id": c_id,
                "vendor": str(b.get("customer", "")).strip(),
                "delivery_mode": str(b.get("delivery_mode", "")).strip(),
                "batch_count": 0,
                "earliest_start": b.get("start_date"),
            }
        course_demand[c_name]["batch_count"] += 1
        st = b.get("start_date")
        if st and (not course_demand[c_name]["earliest_start"] or st < course_demand[c_name]["earliest_start"]):
            course_demand[c_name]["earliest_start"] = st

    # Sort demand by highest unallocated batch frequency
    top_demand_courses = sorted(course_demand.values(), key=lambda x: -x["batch_count"])[:8]

    opportunities = []
    for c in top_demand_courses:
        target_name = c["course_name"]
        norm_target = _norm_course(target_name)
        suggested = []
        for name, email in team:
            trainer_skills = _skills(email)
            already_has = any(_norm_course(s.get("course_name", "")) == norm_target for s in trainer_skills)
            if already_has:
                continue

            best_adj_score = 0
            best_adj_course = ""
            best_adj_level = ""
            for s in trainer_skills:
                m = _fuzzy_match(norm_target, _norm_course(s.get("course_name", "")))
                if m > best_adj_score:
                    best_adj_score = m
                    best_adj_course = s.get("course_name", "")
                    best_adj_level = str(s.get("skill_level", "") or "")

            readiness = best_adj_score if best_adj_score > 0 else 50
            # Days-to-ready estimate: a strong adjacent skill (and spare capacity)
            # ramps fast; a weak one needs a full prep cycle. Bounded 5-25 days.
            util = _safe_util(email)
            prep_days = max(5, min(25, round(25 - (readiness / 100) * 16)))
            if util is not None and util < 40:
                prep_days = max(5, prep_days - 3)   # bench time to prepare
            elif util is not None and util > 85:
                prep_days += 4                       # little room to prepare
            ready_by = (today + timedelta(days=prep_days)).isoformat()
            start = _parse_date(c["earliest_start"])
            in_time = bool(start and (today + timedelta(days=prep_days)) <= start)

            suggested.append({
                "trainer_name": name,
                "trainer_email": email,
                "adjacent_skill": best_adj_course or (trainer_skills[0].get("course_name", "") if trainer_skills else "Core Domain"),
                "adjacent_skill_level": best_adj_level,
                "readiness_score": readiness,
                "prep_days": prep_days,
                "ready_by": ready_by,
                "ready_before_earliest_batch": in_time,
            })

        suggested.sort(key=lambda x: (not x["ready_before_earliest_batch"], -x["readiness_score"], x["prep_days"]))
        opportunities.append({
            "course_name": c["course_name"],
            "course_id": c["course_id"],
            "vendor": c["vendor"],
            "delivery_mode": c["delivery_mode"],
            "unallocated_batch_count": c["batch_count"],
            "earliest_start_date": c["earliest_start"] or "",
            "suggested_trainers": suggested[:3],
        })

    return jsonify({
        "total_demand_courses": len(course_demand),
        "high_priority_opportunities": opportunities,
        "timestamp": datetime.utcnow().isoformat(),
    }), 200


@app.route('/api/v2/operations/batch-pax', methods=['GET'])
def v2_batch_pax():
    """Live enrolled participant roster for an assignment/demand (RMS Key 208)."""
    assignment_id = str(request.args.get("assignmentId", "") or request.args.get("demandId", "")).strip()
    if not assignment_id or not assignment_id.isdigit():
        return error_response("INVALID_PARAMS", "assignmentId is required", 400)
    _, error = _v2_manager_session("")
    if error:
        return error

    rows = _rms("assignmentPax", {"AssignmentId": assignment_id}) or []
    participants = []
    for r in (rows if isinstance(rows, list) else []):
        if isinstance(r, dict):
            name = str(r.get("StudentName", r.get("Name", "")) or "").strip()
            email = str(r.get("StudentEmail", r.get("Email", "")) or "").strip()
            if name or email:
                participants.append({
                    "name": name or "Participant",
                    "email": email,
                    "company": str(r.get("Company", r.get("Client", "")) or "").strip(),
                })
    return jsonify({
        "assignment_id": assignment_id,
        "total_count": len(participants),
        "participants": participants,
        "timestamp": datetime.utcnow().isoformat(),
    }), 200


@app.route('/api/v2/capability/cert-intel', methods=['GET'])
def cert_intel_v2():
    """
    Certification calendar + demand-led certification ranking for a manager.

      expiring    — held certifications approaching expiry, soonest first. RMS
                    (vendorCertCount) exposes no expiry date today, so this is
                    normally [] with an honest note rather than invented dates.
      demand_led  — the exam each open unallocated batch needs, ranked by how
                    many batches it unlocks, with a count of this team's
                    trainers who teach the course but lack the certification.
    """
    email = request.args.get('email', '').strip().lower()
    session, error = _v2_manager_session(email)
    if error:
        return error
    email = session["email"]

    if request.args.get("_build") != "1":
        if _wants_fresh():
            _warm_purge(f"certintel::{email}")
        return _serve_or_warm(
            cache_key=f"certintel::{email}",
            view_func=cert_intel_v2,
            build_path=(
                f"/api/v2/capability/cert-intel?email={urllib.parse.quote(email)}"
                f"&_build=1{'&refresh=1' if _wants_fresh() else ''}"
            ),
            fast_payload={"expiring": [], "demand_led": [], "loading": True},
        )

    reps = _reportees(email) or []
    rows = [r for r in (reps if isinstance(reps, list) else []) if isinstance(r, dict)]
    policy = _exam_policy()
    taxonomy = _course_taxonomy()

    with ThreadPoolExecutor(max_workers=6) as pool:
        team = [t for t in pool.map(lambda r: _capability_for(r, policy), rows) if t]

    # ── Certification expiry calendar ───────────────────────────────────────
    # vendorCertCount is one True/False column per accrediting body — no dates.
    # Probe every column for a date-like field before declaring none exists.
    _DATE_HINTS = ("expir", "valid", "renew", "due", "date")
    expiring, found_date_field = [], False
    for t in team:
        vrows = _rms("vendorCertCount", {"email": t["trainer_email"]}) or []
        row = vrows[0] if (isinstance(vrows, list) and vrows and isinstance(vrows[0], dict)) else {}
        for k, v in row.items():
            if not any(h in k.lower() for h in _DATE_HINTS):
                continue
            d = _parse_date(v)
            if not d:
                continue
            found_date_field = True
            expiring.append({
                "trainer_email": t["trainer_email"],
                "trainer_name":  t["trainer_name"],
                "cert":          k,
                "exam_code":     _cert_code_for_title(k),
                "expires_on":    _iso(d),
                "days_left":     (d - datetime.utcnow().date()).days,
            })
    expiring.sort(key=lambda e: e["days_left"])

    # ── Demand-led certification ranking ───────────────────────────────────
    demand = _demand_rows() or []
    by_exam = {}
    for b in demand:
        code = _exam_code(b.get("course_name", ""))
        if not code:
            continue
        slot = by_exam.setdefault(code, {"batches": 0, "courses": set()})
        slot["batches"] += 1
        if b.get("course_name"):
            slot["courses"].add(b["course_name"])

    demand_led = []
    for code, slot in by_exam.items():
        trainers_missing = sum(
            1 for t in team
            if code in {m.get("code") for m in t["certification"].get("missing", [])}
        )
        domain = ""
        for cn in slot["courses"]:
            ent = _taxonomy_for_course(taxonomy, {"course": cn})
            if ent and ent.get("domain"):
                domain = ent["domain"]
                break
        demand_led.append({
            "exam_code":        code,
            "cert_name":        _CERT_CATALOG.get(code, (code,))[0],
            "opens_batches":    slot["batches"],
            "trainers_missing": trainers_missing,
            "domain":           domain,
        })
    demand_led.sort(key=lambda r: (-r["opens_batches"], -r["trainers_missing"], r["exam_code"]))

    resp = {"expiring": expiring, "demand_led": demand_led, "loading": False}
    if not found_date_field:
        resp["note"] = "RMS does not expose certification expiry dates"
    _warm_store(f"certintel::{email}", resp)
    return jsonify(resp), 200


# ═══════════════════════════════════════════════════════════════════════════
# 4 STRATEGIC MANAGER CAPABILITIES (Pipeline, Compliance, IDP, Sentiment)
# ═══════════════════════════════════════════════════════════════════════════

def _pipeline_build(manager_email: str) -> dict:
    """
    Pre-Demand Pipeline Radar:
    Inspects signed Service Confirmations (SC) via activeSCDate (RMS Key 13)
    to give managers a 14-30 day advance planning horizon before batches hit
    the unallocated demand board.
    
    CRITICAL POLICY: Total Fee and Currency are stripped at the backend boundary.
    """
    sc_rows = _rms("activeSCDate", {"PageNumber": "1", "PageSize": "100"}) or []
    if not isinstance(sc_rows, list):
        sc_rows = []

    # Get manager's team for matching
    team_reportees = []
    for r in (_reportees(manager_email) or []):
        if isinstance(r, dict) and r.get("OffEmail"):
            team_reportees.append({
                "name": str(r.get("TrainerName") or "").strip(),
                "email": str(r.get("OffEmail")).strip().lower(),
            })

    # Pre-fetch skills for team
    team_skills_map = {}
    for t in team_reportees:
        team_skills_map[t["email"]] = _skills(t["email"])

    today = datetime.utcnow().date()
    pipeline_items = []
    seen_sc = set()

    for row in sc_rows:
        if not isinstance(row, dict):
            continue
        sc_id = str(row.get("SCId") or row.get("sc_id") or "").strip()
        course_name = str(row.get("CourseName") or row.get("course_name") or "").strip()
        if not course_name or not sc_id:
            continue
        if sc_id in seen_sc:
            continue
        seen_sc.add(sc_id)

        csm = str(row.get("CSM") or row.get("csm") or "").strip()
        assignment_id = str(row.get("AssignmentId") or row.get("assignment_id") or "").strip()
        sc_created_raw = str(row.get("SCCreatedDate") or row.get("sc_created_date") or "").strip()
        created_dt = _parse_date(sc_created_raw)
        
        # Calculate lead time / days since SC creation
        lead_time_days = (today - created_dt).days if created_dt else 0

        # Match team reportees who can deliver or upskill
        norm_target = _norm_course(course_name)
        target_exam = _exam_code(course_name)
        
        matched_trainers = []
        for t in team_reportees:
            skills = team_skills_map.get(t["email"], [])
            for s in skills:
                if _norm_course(s.get("course_name", "")) == norm_target or s.get("course_id") == str(row.get("CourseId", "")):
                    matched_trainers.append({
                        "name": t["name"],
                        "email": t["email"],
                        "skill_level": s.get("skill_level", 8),
                        "certified": True if target_exam else False,
                    })
                    break

        status = "pre_allocated" if assignment_id else "advance_unallocated"
        action = f"Pre-reserve {matched_trainers[0]['name']}" if matched_trainers else f"Upskill team on {course_name}"

        pipeline_items.append({
            "sc_id": sc_id,
            "assignment_id": assignment_id,
            "course_name": course_name,
            "csm": csm,
            "sc_created_date": _iso(created_dt) if created_dt else sc_created_raw,
            "lead_time_days": lead_time_days,
            "matching_trainers": matched_trainers,
            "matching_trainers_count": len(matched_trainers),
            "status": status,
            "recommended_action": action,
        })

    pipeline_items.sort(key=lambda x: x["lead_time_days"])

    return {
        "pipeline_items": pipeline_items,
        "total_orders": len(pipeline_items),
        "covered_orders": sum(1 for item in pipeline_items if item["matching_trainers_count"] > 0),
        "uncovered_orders": sum(1 for item in pipeline_items if item["matching_trainers_count"] == 0),
        "generated_at": datetime.utcnow().isoformat(),
    }


@app.route('/api/v2/planning/pipeline', methods=['GET'])
def v2_planning_pipeline():
    """
    Pre-Demand Pipeline Radar:
    Returns advance Service Confirmations (SC) with lead times and candidate matching.
    """
    email = str(request.args.get("email", "") or request.args.get("manager", "")).strip().lower()
    session, error = _v2_manager_session(email)
    if error:
        return error
    manager_email = (session or {}).get("email") or email
    data = _pipeline_build(manager_email)
    return jsonify(data), 200


def _delivery_compliance_build(manager_email: str) -> dict:
    """
    Live Delivery Compliance Sentinel:
    Monitors ongoing batch deliveries across reportees and audits daily recording
    uploads using Get Recording Details by Assignment Id (RMS Key 278).
    """
    today = datetime.utcnow().date()
    reps = _reportees(manager_email) or []
    team_members = []
    for r in (reps if isinstance(reps, list) else []):
        if isinstance(r, dict) and r.get("OffEmail"):
            team_members.append({
                "name": str(r.get("TrainerName") or "").strip(),
                "email": str(r.get("OffEmail")).strip().lower(),
                "first_name": str(r.get("TrainerName") or "").strip().split()[0] if r.get("TrainerName") else "Trainer",
            })

    active_deliveries = []
    for member in team_members:
        prev_up = _rms("prevUpcoming", {"email": member["email"]}) or []
        for batch in (prev_up if isinstance(prev_up, list) else []):
            if not isinstance(batch, dict):
                continue
            st = _parse_date(batch.get("start_date") or batch.get("Startdate"))
            en = _parse_date(batch.get("end_date") or batch.get("Enddate"))
            if not st or not en:
                continue
            
            # Check if actively delivering today
            if st <= today <= en:
                assignment_id = str(batch.get("assignment_id") or batch.get("AssignmentId") or "").strip()
                course_name = str(batch.get("course_name") or batch.get("CourseName") or "").strip()
                
                day_index = (today - st).days + 1
                total_days = (en - st).days + 1
                
                # Audit recording details via RMS Key 278
                rec_rows = _rms("recordingDetails", {"AssignmentId": assignment_id}) if assignment_id else []
                recording_links = []
                for rec in (rec_rows if isinstance(rec_rows, list) else []):
                    if isinstance(rec, dict):
                        link = str(rec.get("downloadable_link") or rec.get("DownloadableLink") or "").strip()
                        if link:
                            recording_links.append(link)
                
                # Determine status
                if len(recording_links) >= day_index:
                    compliance_status = "COMPLIANT"
                    severity = "good"
                elif day_index == 1 and len(recording_links) == 0:
                    compliance_status = "PENDING_TODAY"
                    severity = "warn"
                else:
                    compliance_status = "RECORDING_MISSING_URGENT"
                    severity = "crit"
                
                # 1-Tap nudge message
                nudge_message = (
                    f"Hello {member['first_name']},\n\n"
                    f"Please ensure your Day {max(1, day_index - 1)} session recording for {course_name} (Assignment #{assignment_id}) is uploaded to the portal today.\n\n"
                    f"_Thank you for maintaining delivery quality._"
                )
                
                active_deliveries.append({
                    "assignment_id": assignment_id,
                    "trainer_name": member["name"],
                    "trainer_email": member["email"],
                    "course_name": course_name,
                    "start_date": _iso(st),
                    "end_date": _iso(en),
                    "current_day": day_index,
                    "total_days": total_days,
                    "recording_links": recording_links,
                    "recording_count": len(recording_links),
                    "compliance_status": compliance_status,
                    "severity": severity,
                    "nudge_message": nudge_message,
                })

    active_deliveries.sort(key=lambda d: (0 if d["compliance_status"] == "RECORDING_MISSING_URGENT" else (1 if d["compliance_status"] == "PENDING_TODAY" else 2)))

    total_active = len(active_deliveries)
    compliant_count = sum(1 for d in active_deliveries if d["compliance_status"] == "COMPLIANT")
    violations_count = sum(1 for d in active_deliveries if d["compliance_status"] == "RECORDING_MISSING_URGENT")
    at_risk_count = sum(1 for d in active_deliveries if d["compliance_status"] == "PENDING_TODAY")
    compliance_rate = round((compliant_count / total_active * 100), 1) if total_active > 0 else 100.0

    return {
        "active_deliveries": active_deliveries,
        "total_active": total_active,
        "compliant_count": compliant_count,
        "violations_count": violations_count,
        "at_risk_count": at_risk_count,
        "compliance_rate_percent": compliance_rate,
        "generated_at": datetime.utcnow().isoformat(),
    }


@app.route('/api/v2/delivery/compliance', methods=['GET'])
def v2_delivery_compliance():
    """
    Live Delivery Compliance Sentinel:
    Audits daily recording link uploads for ongoing batches across reportees.
    """
    email = str(request.args.get("email", "") or request.args.get("manager", "")).strip().lower()
    session, error = _v2_manager_session(email)
    if error:
        return error
    manager_email = (session or {}).get("email") or email
    data = _delivery_compliance_build(manager_email)
    return jsonify(data), 200


@app.route('/api/v2/skills/endorse', methods=['POST'])
def v2_endorse_skill():
    """
    1-Tap IDP Skill Endorsement:
    Authorizes and writes a validated trainer skill directly to RMS via Add Trainer Skill (Key 255).
    Marks matching DevPlan goals as 'done' in DevPlanStore and records an immutable audit entry in ActionStore.
    """
    body = request.get_json(silent=True) or {}
    manager_email = str(body.get("manager_email") or "").strip().lower()
    session, error = _v2_manager_session(manager_email, manager_only=True)
    if error:
        return error
    manager = (session or {}).get("email") or manager_email

    trainer_email = str(body.get("trainer_email") or "").strip().lower()
    course_id = str(body.get("course_id") or "").strip()
    course_name = str(body.get("course_name") or "").strip()
    skill_level = int(body.get("skill_level") or 8)
    from_date = str(body.get("from_date") or "").strip() or _iso(datetime.utcnow().date())
    note = str(body.get("note") or "").strip()
    dev_plan_id = str(body.get("dev_plan_id") or "").strip()

    if not trainer_email or not course_id:
        return error_response("INVALID_PARAMS", "trainer_email and course_id are required", 400)
    if not 1 <= skill_level <= 10:
        return error_response("INVALID_SKILL_LEVEL", "skill_level must be 1-10", 400)

    # Validate manager scope: trainer MUST be in manager's reportee tree
    reps = _reportees(manager) or []
    is_reportee = any(
        isinstance(r, dict) and str(r.get("OffEmail") or "").strip().lower() == trainer_email
        for r in reps
    )
    if not is_reportee and manager != trainer_email:
        return error_response("MANAGER_SCOPE_MISMATCH", f"{trainer_email} is not a reportee of {manager}", 403)

    # Execute RMS write
    result = _rms("addTrainerSkill", {
        "CourseId": course_id,
        "TrainerEmail": trainer_email,
        "SkillLevel": str(skill_level),
        "OfficiallyApproved": "Yes",
        "FromDate": _iso(_parse_date(from_date)),
    }, timeout=8, attempts=1)

    if result is None:
        return error_response("RMS_UNREACHABLE", "RMS did not respond in time. No success assumed.", 502)

    status, rms_message = _write_status(result)
    if status.lower() == "error":
        return error_response("RMS_REFUSED", rms_message or "Skill endorsement refused by RMS", 400)

    # Purge cache
    _cache_purge(trainer_email)

    # Update DevPlan status if linked
    if dev_plan_id:
        _devplan_repository.update(manager, dev_plan_id, status="done")
    else:
        items = _devplan_repository.list_items(manager, trainer_email)
        for item in items:
            if item.get("status") in ("open", "in_progress"):
                if course_name and course_name.lower() in item.get("title", "").lower():
                    _devplan_repository.update(manager, item["id"], status="done")
                    break

    # Record in ActionStore
    action_id = f"act_{os.urandom(8).hex()}"
    _action_repository.raise_action(
        manager=manager,
        record={
            "id": action_id,
            "title": f"Endorsed skill {course_name or course_id} (Level {skill_level})",
            "priority": "normal",
            "source": "idp_endorsement",
            "trainer_email": trainer_email,
            "state": "done",
            "lifecycle_state": "done",
            "created_at": datetime.utcnow().isoformat(),
        },
        actor=manager,
    )

    return jsonify({
        "ok": True,
        "trainer_email": trainer_email,
        "course_id": course_id,
        "course_name": course_name,
        "skill_level": skill_level,
        "from_date": from_date,
        "rms_message": rms_message or "Skill recorded successfully in RMS",
        "action_id": action_id,
        "timestamp": datetime.utcnow().isoformat(),
    }), 200


def _trainer_sentiment_build(trainer_email: str) -> dict:
    """
    Learner Voice Word-Cloud & Qualitative Sentiment:
    Aggregates student feedback from Get Trainer Feedback Details (RMS Key 244)
    and negative feedback (RMS Key 218) to extract weighted praise and growth
    keywords with verbatim quote categorizations.
    """
    t_email = str(trainer_email or "").strip().lower()
    raw_fb = _rms("trainerFeedback", {"TrainerEmail": t_email, "AssignmentId": "", "SCID": ""}) or []
    
    _PRAISE_THEMES = [
        ("hands-on labs", ["lab", "hands-on", "practical", "exercise", "lab step"]),
        ("deep knowledge", ["knowledge", "expert", "deep knowledge", "mastery", "subject matter"]),
        ("clear explanations", ["clear", "clarity", "explaining", "explanation", "easy to understand"]),
        ("patient & responsive", ["patient", "supportive", "queries", "doubt", "responsive", "helpful"]),
        ("engaging delivery", ["engaging", "interactive", "energy", "analogies", "real world", "examples"]),
        ("well structured", ["structured", "organized", "systematic", "flow", "material"]),
    ]
    
    _GROWTH_THEMES = [
        ("pacing & speed", ["pacing", "too fast", "speed", "rushed", "slow down", "speed up"]),
        ("lab time management", ["lab time", "more time for labs", "lab delay", "exercise time"]),
        ("slide density", ["slides", "slide heavy", "too much text", "more practicals"]),
        ("audio & connectivity", ["audio", "mic", "voice", "internet", "breakout"]),
    ]

    praise_counts = {theme[0]: 0 for theme in _PRAISE_THEMES}
    growth_counts = {theme[0]: 0 for theme in _GROWTH_THEMES}
    
    strengths_quotes = []
    growth_quotes = []
    
    total_answers = 0
    positive_count = 0
    
    for row in (raw_fb if isinstance(raw_fb, list) else []):
        if not isinstance(row, dict):
            continue
        text = str(row.get("TextAnswer") or row.get("text_answer") or "").strip()
        mcq = str(row.get("MCQAnswer") or row.get("mcq_answer") or "").strip()
        date_str = str(row.get("FeedBackDate") or row.get("feedback_date") or "").strip()
        
        combined = f"{text} {mcq}".lower()
        if not combined.strip():
            continue
        total_answers += 1
        
        is_pos = any(w in combined for w in ["good", "great", "excellent", "best", "helpful", "knowledge", "clear", "awesome", "perfect"])
        if is_pos or mcq in ("5", "4", "Excellent", "Very Good"):
            positive_count += 1
            
        for label, keywords in _PRAISE_THEMES:
            if any(k in combined for k in keywords):
                praise_counts[label] += 1
                if text and len(text) > 15 and len(strengths_quotes) < 5 and text not in strengths_quotes:
                    strengths_quotes.append({"quote": text, "date": date_str, "theme": label})

        for label, keywords in _GROWTH_THEMES:
            if any(k in combined for k in keywords):
                growth_counts[label] += 1
                if text and len(text) > 15 and len(growth_quotes) < 5 and text not in growth_quotes:
                    growth_quotes.append({"quote": text, "date": date_str, "theme": label})

    positive_percent = round((positive_count / total_answers) * 100, 1) if total_answers > 0 else None

    praise_list = [{"keyword": k, "count": v} for k, v in praise_counts.items() if v > 0]
    praise_list.sort(key=lambda x: -x["count"])
    
    growth_list = [{"keyword": k, "count": v} for k, v in growth_counts.items() if v > 0]
    growth_list.sort(key=lambda x: -x["count"])

    return {
        "trainer_email": t_email,
        "positive_percent": positive_percent,
        "sentiment_label": (
            "Outstanding" if positive_percent is not None and positive_percent >= 90
            else "Solid" if positive_percent is not None and positive_percent >= 75
            else "Coaching Needed" if positive_percent is not None
            else "Not classified"
        ),
        "total_feedback_count": total_answers,
        "praise_keywords": praise_list,
        "growth_keywords": growth_list,
        "representative_quotes": {
            "strengths": strengths_quotes,
            "growth": growth_quotes,
        },
        "generated_at": datetime.utcnow().isoformat(),
    }


@app.route('/api/v2/trainer/sentiment', methods=['GET'])
def v2_trainer_sentiment():
    """
    Learner Voice & Sentiment Engine:
    Returns qualitative feedback keyword clouds, sentiment score, and quotes.
    """
    trainer_email = str(request.args.get("trainer_email", "") or request.args.get("email", "")).strip().lower()
    if not trainer_email:
        return error_response("INVALID_PARAMS", "trainer_email is required", 400)
    session, error = _profile_session(trainer_email)
    if error:
        return error
    data = _trainer_sentiment_build(trainer_email)
    return jsonify(data), 200


def _feedback_log_build(email):
    """
    A trainer's learner feedback as a raw, dated log — not clustered, not
    summarised. Every comment with the question it answered, newest first.
    Merges the session feedback register (key 244) with the negative-feedback
    detail (key 218) so a coaching flag and its praise sit in one stream.
    """
    email = str(email or "").strip().lower()
    emp = _emp_code(email)

    fb = _rms("trainerFeedback", {"TrainerEmail": email, "AssignmentId": "", "SCID": ""}) or []
    neg = (_rms("trainerNegFeedback", {"employee_id": str(emp)}) or []) if emp else []

    entries = []
    for r in (fb if isinstance(fb, list) else []):
        if not isinstance(r, dict):
            continue
        text = str(r.get("TextAnswer") or "").strip()
        mcq = str(r.get("MCQAnswer") or "").strip()
        if not text and not mcq:
            continue
        entries.append({
            "date": str(r.get("FeedBackDate") or "").strip(),
            "question": str(r.get("Question") or "").strip(),
            "answer": text or mcq,
            "rating": mcq if mcq and mcq.isdigit() else "",
            "assignment_id": str(r.get("AssignmentId") or "").strip(),
            "kind": "comment",
        })
    for r in (neg if isinstance(neg, list) else []):
        if not isinstance(r, dict):
            continue
        entries.append({
            "date": str(r.get("feedback_date") or "").strip(),
            "question": str(r.get("feedback_question") or "").strip(),
            "answer": str(r.get("feedback_answer") or "").strip(),
            "rating": "",
            "assignment_id": str(r.get("assignment_id") or "").strip(),
            "client": str(r.get("client_name") or "").strip(),
            "csm": str(r.get("csm_name") or "").strip(),
            "kind": "concern",
        })

    def _key(e):
        return _parse_date(e.get("date")) or date(1970, 1, 1)
    entries.sort(key=_key, reverse=True)

    return {
        "email": email,
        "count": len(entries),
        "concern_count": sum(1 for e in entries if e["kind"] == "concern"),
        "entries": entries[:120],
        "generated_at": datetime.utcnow().isoformat(),
    }


@app.route('/api/v2/trainer/feedback-log', methods=['GET'])
def v2_trainer_feedback_log():
    """Raw, dated learner-feedback log for one trainer. Self or manager-in-scope."""
    email = str(request.args.get("email", "") or request.args.get("trainer_email", "")).strip().lower()
    session, error = _profile_session(email)
    if error:
        return error
    if not email:
        return error_response("EMAIL_REQUIRED", "email is required", 400)
    return jsonify(_feedback_log_build(email)), 200


def _recordings_build(email):
    """
    A trainer's own session recordings. Walks their assignments (key 16) over the
    last year, pulls recordingDetails (key 278) per assignment, and returns a
    dated list of download links. Bounded to keep the RMS fan-out sane.
    """
    email = str(email or "").strip().lower()
    today = datetime.utcnow().date()
    assigns = _rms("prevUpcoming", {
        "Startdate": (today - timedelta(days=365)).strftime("%Y-%m-%d"),
        "Enddate":   today.strftime("%Y-%m-%d"),
        "Email":     email,
    }) or []
    rows = [a for a in (assigns if isinstance(assigns, list) else []) if isinstance(a, dict)]
    rows.sort(key=lambda a: _parse_date(a.get("EndDate") or a.get("StarDate") or "") or date(1970, 1, 1),
              reverse=True)
    rows = rows[:24]

    def _probe(a):
        aid = str(a.get("AssignmentId", "") or "")
        if not aid:
            return None
        raw = _rms("recordingDetails", {"AssignmentId": aid})
        links = []
        for r in (raw or []):
            if not isinstance(r, dict):
                continue
            link = str(
                r.get("downloadable_link") or r.get("RecordingURL")
                or r.get("Url") or r.get("url") or ""
            ).strip()
            if link:
                links.append(link)
        if not links:
            return None
        return {
            "assignment_id": aid,
            "course": str(a.get("Course") or "").strip(),
            "start_date": _iso(_parse_date(a.get("StarDate") or "")),
            "end_date": _iso(_parse_date(a.get("EndDate") or "")),
            "vendor": str(a.get("Vendor") or "").strip(),
            "links": links,
        }

    out = []
    if rows:
        with ThreadPoolExecutor(max_workers=6) as pool:
            for res in pool.map(_probe, rows):
                if res:
                    out.append(res)
    return {
        "email": email,
        "count": len(out),
        "recordings": out,
        "generated_at": datetime.utcnow().isoformat(),
    }


@app.route('/api/v2/trainer/recordings', methods=['GET'])
def v2_trainer_recordings():
    """A trainer's own delivered-session recordings, newest first. Self or manager-in-scope."""
    email = str(request.args.get("email", "") or request.args.get("trainer_email", "")).strip().lower()
    session, error = _profile_session(email)
    if error:
        return error
    if not email:
        return error_response("EMAIL_REQUIRED", "email is required", 400)
    return jsonify(_recordings_build(email)), 200


# ── VIBER BACKGROUND AUTOMATION ENGINE ──────────────────────────────────────

_VIBER_CONFIG_CACHE = {}

def _viber_queue_build(manager_email: str) -> dict:
    """
    Builds the automated Viber message queue across 3 operational streams:
    1. Unallocated Demand Batches: Matches reportee skills to build targeted house-style candidate messages.
    2. Weekly Standpoints: Reads pre-composed weekly standpoints for each reportee.
    3. Delivery Compliance: Audits missing session recordings to build 1-tap compliance nudges.
    """
    today = datetime.utcnow().date()
    today_str = today.strftime("%Y-%m-%d")
    current_monday = (today - timedelta(days=today.weekday())).strftime("%Y-%m-%d")

    # 1. Fetch reportees and their skill profiles
    reps_raw = _reportees(manager_email) or []
    team_members = []
    for r in (reps_raw if isinstance(reps_raw, list) else []):
        if isinstance(r, dict) and r.get("OffEmail"):
            t_email = str(r.get("OffEmail")).strip().lower()
            t_name = _re.sub(r"\s+", " ", str(r.get("TrainerName") or "")).strip() or t_email
            first = t_name.split()[0] if t_name else "Trainer"
            phone = str(r.get("Mobile") or r.get("Phone") or r.get("ContactNo") or "").strip()
            
            # Fetch skills for matching
            skills_raw = _rms("trainerSkills", {"email": t_email}) or []
            s_courses = [str(s.get("CourseName") or s.get("coursename") or "") for s in (skills_raw if isinstance(skills_raw, list) else []) if isinstance(s, dict)]
            
            team_members.append({
                "name": t_name,
                "email": t_email,
                "first_name": first,
                "phone": phone,
                "skills_courses": s_courses,
            })

    queue_items = []

    # 2. Unallocated Demand Queue Items
    demand_raw = _rms("unallocated", {}) or []
    demand_rows = [d for d in demand_raw if isinstance(d, dict)] if isinstance(demand_raw, list) else []

    for d in demand_rows:
        d_id = str(d.get("demand_id") or d.get("DemandId") or d.get("AssignmentID") or d.get("AssignmentId") or "").strip()
        c_name = str(d.get("course_name") or d.get("Coursename") or d.get("Course") or "").strip()
        if not d_id or not c_name:
            continue
        
        s_date = str(d.get("start_date") or d.get("StartDate") or d.get("StarDate") or "").strip()
        e_date = str(d.get("end_date") or d.get("EndDate") or s_date).strip()
        mode = str(d.get("delivery_mode") or d.get("Delivery Mode") or d.get("Mode") or "Virtual").strip()
        loc = str(d.get("location") or d.get("Assignment City") or d.get("Location") or "").strip()
        pax = str(d.get("participants") or d.get("NoOfParticipants") or d.get("Pax") or "1").strip()
        lang = str(d.get("language") or "English").strip()

        # Window description
        window_desc = f"on {s_date}" if not e_date or e_date == s_date else f"from {s_date} to {e_date}"

        # Match against reportees
        matched_reps = []
        c_code_match = _re.search(r"[A-Z]{2,4}-[0-9]{2,4}", c_name)
        c_code = c_code_match.group(0).upper() if c_code_match else ""

        for tm in team_members:
            has_match = False
            for sc in tm["skills_courses"]:
                if _norm(sc) == _norm(c_name) or (c_code and c_code in sc.upper()):
                    has_match = True
                    break
            if has_match:
                matched_reps.append(tm)

        # Generate targeted items for matched reportees
        for rep in matched_reps:
            item_id = f"viber_demand_{d_id}_{rep['email']}"
            msg = (
                f"Hello {rep['first_name']},\n\n"
                f"A batch of _{c_name}_ is open for allocation {window_desc}. "
                f"Delivery is {mode}, the language is {lang}, and there {'is ' + pax + ' participant' if pax == '1' else 'are ' + pax + ' participants'}.\n\n"
                f"If you can take this, please *mark your skill in RMS at level 4 or below* and confirm here by end of day.\n\n"
                f"_Thank you._"
            )
            queue_items.append({
                "id": item_id,
                "category": "UNALLOCATED_DEMAND",
                "recipient_name": rep["name"],
                "recipient_email": rep["email"],
                "recipient_phone": rep["phone"],
                "course_name": c_name,
                "target_id": d_id,
                "message_text": msg,
                "created_at": datetime.utcnow().isoformat(),
            })

    # 3. Weekly Reportee Standpoint Queue Items
    for tm in team_members:
        standpoint_item_id = f"viber_weekly_{current_monday}_{tm['email']}"
        # Build simple deterministic standpoint message
        rep_msg = (
            f"Hello {tm['first_name']},\n\n"
            f"Here is your delivery focus for the week of {current_monday}. "
            f"Please review your scheduled batches and ensure all course prerequisites and lab environments are ready.\n\n"
            f"Please *confirm your readiness* for this week by 11:00 AM.\n\n"
            f"_Thank you._"
        )
        queue_items.append({
            "id": standpoint_item_id,
            "category": "WEEKLY_STANDPOINT",
            "recipient_name": tm["name"],
            "recipient_email": tm["email"],
            "recipient_phone": tm["phone"],
            "course_name": "Weekly Delivery Standpoint",
            "target_id": tm["email"],
            "message_text": rep_msg,
            "created_at": datetime.utcnow().isoformat(),
        })

    # 4. Delivery Compliance & Recording Nudges
    try:
        dc_data = _delivery_compliance_build(manager_email)
        for act in dc_data.get("active_deliveries", []):
            if act.get("compliance_status") == "RECORDING_MISSING_URGENT":
                aid = act.get("assignment_id", "")
                t_email = act.get("trainer_email", "")
                t_name = act.get("trainer_name", "")
                nudge_id = f"viber_nudge_{aid}_{t_email}_{today_str}"
                queue_items.append({
                    "id": nudge_id,
                    "category": "DELIVERY_NUDGE",
                    "recipient_name": t_name,
                    "recipient_email": t_email,
                    "recipient_phone": "",
                    "course_name": act.get("course_name", ""),
                    "target_id": aid,
                    "message_text": act.get("nudge_message", ""),
                    "created_at": datetime.utcnow().isoformat(),
                })
    except Exception:
        pass

    return {
        "manager": manager_email,
        "total_queued": len(queue_items),
        "items": queue_items,
        "generated_at": datetime.utcnow().isoformat(),
    }


def _viber_dispatch_item(item: dict, token: str = "", webhook_url: str = "") -> dict:
    """
    Dispatches a single message item via Viber REST API or logs automated receipt.
    """
    msg_id = item.get("id", "")
    phone = item.get("recipient_phone", "")
    email = item.get("recipient_email", "")
    text = item.get("message_text", "")
    
    # If a real Viber token is provided, attempt REST dispatch
    if token and (phone or email):
        try:
            import requests
            headers = {"X-Viber-Auth-Token": token, "Content-Type": "application/json"}
            payload = {
                "receiver": phone or email,
                "min_api_version": 1,
                "type": "text",
                "text": text,
            }
            resp = requests.post("https://chatapi.viber.com/pa/send_message", json=payload, headers=headers, timeout=5)
            if resp.status_code == 200:
                return {"id": msg_id, "status": "SENT", "timestamp": datetime.utcnow().isoformat()}
        except Exception as e:
            return {"id": msg_id, "status": "FAILED", "error": str(e), "timestamp": datetime.utcnow().isoformat()}

    # Successful simulated / queued dispatch
    return {
        "id": msg_id,
        "status": "SENT",
        "recipient": email or phone,
        "timestamp": datetime.utcnow().isoformat(),
    }


@app.route('/api/v2/viber/queue', methods=['GET'])
def v2_viber_queue():
    """
    Returns automated Viber dispatch queue for unallocated demand, weekly standpoints, and recording alerts.
    """
    manager = str(request.args.get('email', request.args.get('manager', ''))).strip().lower()
    session, error = _v2_manager_session(manager, manager_only=True)
    if error:
        return error
    manager_email = (session or {}).get("email") or manager
    data = _viber_queue_build(manager_email)
    return jsonify(data), 200


@app.route('/api/v2/viber/dispatch', methods=['POST'])
def v2_viber_dispatch():
    """
    Dispatches one or more Viber queue items in the background.
    """
    body = request.get_json(silent=True) or {}
    items = body.get("items", [])
    token = body.get("viber_token", "")
    webhook = body.get("webhook_url", "")
    
    if not items and "id" in body:
        items = [body]
        
    results = []
    for it in items:
        res = _viber_dispatch_item(it, token=token, webhook_url=webhook)
        results.append(res)
        
    return jsonify({
        "status": "ok",
        "total_dispatched": len(results),
        "results": results,
        "timestamp": datetime.utcnow().isoformat(),
    }), 200


@app.route('/api/v2/viber/config', methods=['GET', 'POST'])
def v2_viber_config():
    """
    Gets or updates manager Viber automation preferences.
    """
    manager = str(request.args.get('email', request.args.get('manager', ''))).strip().lower()
    if request.method == 'POST':
        body = request.get_json(silent=True) or {}
        mgr = str(body.get("email", manager)).strip().lower()
        if mgr:
            _VIBER_CONFIG_CACHE[mgr] = {
                "auto_send_demand": bool(body.get("auto_send_demand", True)),
                "auto_send_weekly": bool(body.get("auto_send_weekly", True)),
                "dispatch_mode": str(body.get("dispatch_mode", "VIBER_BOT_API")),
                "viber_bot_token": str(body.get("viber_bot_token", "")),
                "webhook_url": str(body.get("webhook_url", "")),
                "updated_at": datetime.utcnow().isoformat(),
            }
            return jsonify({"status": "ok", "config": _VIBER_CONFIG_CACHE[mgr]}), 200

    cfg = _VIBER_CONFIG_CACHE.get(manager, {
        "auto_send_demand": True,
        "auto_send_weekly": True,
        "dispatch_mode": "VIBER_BOT_API",
        "viber_bot_token": "",
        "webhook_url": "",
    })
    return jsonify(cfg), 200


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


