"""API metadata and credentials for SkillEdge backend.

In development mode (SKILLEDGE_ENV != 'production'), hardcoded fallback
credentials are used when env vars are not set.  In production mode,
every credential env var must be populated or startup will fail.
"""

import os
import logging
import re
from pathlib import Path

log = logging.getLogger("skilledge.config")

_SKILLEDGE_ENV = os.getenv("SKILLEDGE_ENV", "development").strip().lower()
IS_PRODUCTION = _SKILLEDGE_ENV == "production"

# ── Credential env vars with dev-only fallbacks ─────────────────────────────
_DEV_FALLBACKS = {
    "SKILLEDGE_RMS_REPORTEES_USER": "AISHWAR_GetDirectIndire",
    "SKILLEDGE_RMS_REPORTEES_PASS": "3R$Nc7ThBX64",
    "SKILLEDGE_RMS_TRAINER_DETAILS_USER": "AISHWAR_GetTrainerDetai",
    "SKILLEDGE_RMS_TRAINER_DETAILS_PASS": "7zCheFM$Cc$t",
    "SKILLEDGE_RMS_TRAINER_SKILLS_USER": "AISHWAR_GetTrainerSkill",
    "SKILLEDGE_RMS_TRAINER_SKILLS_PASS": "dpcwt4L5$@7U",
    "SKILLEDGE_RMS_UTILIZATION_USER": "AISHWAR_GetUtilization",
    "SKILLEDGE_RMS_UTILIZATION_PASS": "j4CakF7gEg#f",
    "SKILLEDGE_RMS_VENDOR_CERTS_USER": "AISHWAR_GettrainerVende",
    "SKILLEDGE_RMS_VENDOR_CERTS_PASS": "!$R#gQuAs9Rw",
    "SKILLEDGE_RMS_RESUME_DETAILS_USER": "AISHWAR_TrainerResumeDe",
    "SKILLEDGE_RMS_RESUME_DETAILS_PASS": "nw@dL3xQD#BL",
    "SKILLEDGE_RMS_NEG_FEEDBACK_USER": "AISHWAR_GetNegativeFeed",
    "SKILLEDGE_RMS_NEG_FEEDBACK_PASS": "#9u7@@hAHWUg",
    "SKILLEDGE_RMS_HR_INCIDENTS_USER": "AISHWAR_GetHRIncidentPo",
    "SKILLEDGE_RMS_HR_INCIDENTS_PASS": "42nLmM!#weDk",
    "SKILLEDGE_RMS_TRAINER_AVAIL_USER": "AISHWAR_Traineravailabi",
    "SKILLEDGE_RMS_TRAINER_AVAIL_PASS": "c2yRDVdG#XCs",
    "SKILLEDGE_RMS_FREE_SCHEDULE_USER": "AISHWAR_GetTrainerFreeS",
    "SKILLEDGE_RMS_FREE_SCHEDULE_PASS": "J6FLKGx!exA7",
    "SKILLEDGE_RMS_PREV_UPCOMING_USER": "AISHWAR_PreviousUpcommi",
    "SKILLEDGE_RMS_PREV_UPCOMING_PASS": "J8LzP@HkW#Ve",
    "SKILLEDGE_RMS_TRAINER_FEEDBACK_USER": "AISHWAR_GetTrainerFeedb",
    "SKILLEDGE_RMS_TRAINER_FEEDBACK_PASS": "T9$jsBnSW7Rd",
    "SKILLEDGE_RMS_TRAINER_LAST3_USER": "AISHWAR_TrainerLast3Mon",
    "SKILLEDGE_RMS_TRAINER_LAST3_PASS": "TmSe!9A!@GfL",
    "SKILLEDGE_RMS_TR_RC_SCHEDULE_USER": "AISHWAR_TrainerRCSchedu",
    "SKILLEDGE_RMS_TR_RC_SCHEDULE_PASS": "jGErt8!Agr$a",
    "SKILLEDGE_RMS_COURSE_WITHOUT_EXAM_USER": "AISHWAR_CourseWhitoutEx",
    "SKILLEDGE_RMS_COURSE_WITHOUT_EXAM_PASS": "V9n82gfmC$$W",
    "SKILLEDGE_RMS_EXAM_COURSE_LINKED_USER": "AISHWAR_ExamCourseLinke",
    "SKILLEDGE_RMS_EXAM_COURSE_LINKED_PASS": "K7!k@n3dA$w2",
    "SKILLEDGE_RMS_UNIQUE_CERT_COUNT_USER": "AISHWAR_GetUniqueCertif",
    "SKILLEDGE_RMS_UNIQUE_CERT_COUNT_PASS": "G8!9P@$m3t25",
    "SKILLEDGE_RMS_COURSE_LIST_USER": "AISHWAR_CourseList",
    "SKILLEDGE_RMS_COURSE_LIST_PASS": "@56Crxj#Yc@5",
    "SKILLEDGE_RMS_ASSIGNMENTS_USER": "AISHWAR_AssignmentAPI",
    "SKILLEDGE_RMS_ASSIGNMENTS_PASS": "4PV6aCe6Sc8!",
}


def _env(name, fallback):
    """Read env var. In production: fail if missing. In development: use fallback."""
    value = os.getenv(name, "").strip()
    if value:
        return value
    if IS_PRODUCTION:
        raise EnvironmentError(
            f"Missing required env var {name}. "
            f"Set SKILLEDGE_ENV=development to use fallback credentials."
        )
    return fallback


def validate_all_credentials():
    """Called at startup to fail-fast in production if any credential is missing."""
    missing = []
    for env_name in _DEV_FALLBACKS:
        val = os.getenv(env_name, "").strip()
        if not val:
            missing.append(env_name)
    if IS_PRODUCTION and missing:
        raise EnvironmentError(
            f"Production mode requires all RMS credentials. "
            f"Missing {len(missing)} env vars: {', '.join(missing[:5])}"
            f"{'...' if len(missing) > 5 else ''}"
        )
    if missing:
        log.info(
            "Development mode: %d credential env vars not set, using fallbacks.",
            len(missing),
        )
    else:
        log.info("All %d credential env vars are set from environment.", len(_DEV_FALLBACKS))
    return missing


CONFIGS = {
    "reportees":        {"key": "82",  "user": _env("SKILLEDGE_RMS_REPORTEES_USER", _DEV_FALLBACKS["SKILLEDGE_RMS_REPORTEES_USER"]), "pass": _env("SKILLEDGE_RMS_REPORTEES_PASS", _DEV_FALLBACKS["SKILLEDGE_RMS_REPORTEES_PASS"]),  "role": "Get Direct Indirect Reportee"},
    "trainerDetails":   {"key": "75",  "user": _env("SKILLEDGE_RMS_TRAINER_DETAILS_USER", _DEV_FALLBACKS["SKILLEDGE_RMS_TRAINER_DETAILS_USER"]), "pass": _env("SKILLEDGE_RMS_TRAINER_DETAILS_PASS", _DEV_FALLBACKS["SKILLEDGE_RMS_TRAINER_DETAILS_PASS"]),  "role": "Get Trainer Details"},
    "trainerSkills":    {"key": "217", "user": _env("SKILLEDGE_RMS_TRAINER_SKILLS_USER", _DEV_FALLBACKS["SKILLEDGE_RMS_TRAINER_SKILLS_USER"]), "pass": _env("SKILLEDGE_RMS_TRAINER_SKILLS_PASS", _DEV_FALLBACKS["SKILLEDGE_RMS_TRAINER_SKILLS_PASS"]),  "role": "Get Trainer Skills"},
    "utilization":      {"key": "55",  "user": _env("SKILLEDGE_RMS_UTILIZATION_USER", _DEV_FALLBACKS["SKILLEDGE_RMS_UTILIZATION_USER"]),  "pass": _env("SKILLEDGE_RMS_UTILIZATION_PASS", _DEV_FALLBACKS["SKILLEDGE_RMS_UTILIZATION_PASS"]),  "role": "Get Utilization"},
    "vendorCerts":      {"key": "57",  "user": _env("SKILLEDGE_RMS_VENDOR_CERTS_USER", _DEV_FALLBACKS["SKILLEDGE_RMS_VENDOR_CERTS_USER"]), "pass": _env("SKILLEDGE_RMS_VENDOR_CERTS_PASS", _DEV_FALLBACKS["SKILLEDGE_RMS_VENDOR_CERTS_PASS"]),  "role": "Get trainer Vender Certification Count"},
    "resumeDetails":    {"key": "87",  "user": _env("SKILLEDGE_RMS_RESUME_DETAILS_USER", _DEV_FALLBACKS["SKILLEDGE_RMS_RESUME_DETAILS_USER"]), "pass": _env("SKILLEDGE_RMS_RESUME_DETAILS_PASS", _DEV_FALLBACKS["SKILLEDGE_RMS_RESUME_DETAILS_PASS"]), "role": "Trainer Resume Details"},
    "negativeFeedback": {"key": "58",  "user": _env("SKILLEDGE_RMS_NEG_FEEDBACK_USER", _DEV_FALLBACKS["SKILLEDGE_RMS_NEG_FEEDBACK_USER"]), "pass": _env("SKILLEDGE_RMS_NEG_FEEDBACK_PASS", _DEV_FALLBACKS["SKILLEDGE_RMS_NEG_FEEDBACK_PASS"]),  "role": "Get Negative Feedback Count"},
    "hrIncidents":      {"key": "59",  "user": _env("SKILLEDGE_RMS_HR_INCIDENTS_USER", _DEV_FALLBACKS["SKILLEDGE_RMS_HR_INCIDENTS_USER"]), "pass": _env("SKILLEDGE_RMS_HR_INCIDENTS_PASS", _DEV_FALLBACKS["SKILLEDGE_RMS_HR_INCIDENTS_PASS"]),  "role": "Get HR Incident Positive Negative"},
    "trainerAvail":     {"key": "90",  "user": _env("SKILLEDGE_RMS_TRAINER_AVAIL_USER", _DEV_FALLBACKS["SKILLEDGE_RMS_TRAINER_AVAIL_USER"]), "pass": _env("SKILLEDGE_RMS_TRAINER_AVAIL_PASS", _DEV_FALLBACKS["SKILLEDGE_RMS_TRAINER_AVAIL_PASS"]), "role": "Trainer availability"},
    "freeSchedule":     {"key": "171", "user": _env("SKILLEDGE_RMS_FREE_SCHEDULE_USER", _DEV_FALLBACKS["SKILLEDGE_RMS_FREE_SCHEDULE_USER"]), "pass": _env("SKILLEDGE_RMS_FREE_SCHEDULE_PASS", _DEV_FALLBACKS["SKILLEDGE_RMS_FREE_SCHEDULE_PASS"]), "role": "Get Trainer Free Shedule and Details"},
    "prevUpcoming":     {"key": "16",  "user": _env("SKILLEDGE_RMS_PREV_UPCOMING_USER", _DEV_FALLBACKS["SKILLEDGE_RMS_PREV_UPCOMING_USER"]), "pass": _env("SKILLEDGE_RMS_PREV_UPCOMING_PASS", _DEV_FALLBACKS["SKILLEDGE_RMS_PREV_UPCOMING_PASS"]), "role": "Previous & Upcomming Assignments"},
    "trainerFeedback":  {"key": "244", "user": _env("SKILLEDGE_RMS_TRAINER_FEEDBACK_USER", _DEV_FALLBACKS["SKILLEDGE_RMS_TRAINER_FEEDBACK_USER"]), "pass": _env("SKILLEDGE_RMS_TRAINER_FEEDBACK_PASS", _DEV_FALLBACKS["SKILLEDGE_RMS_TRAINER_FEEDBACK_PASS"]), "role": "Get Trainer Feedback Details"},
    "trainerLast3":     {"key": "39",  "user": _env("SKILLEDGE_RMS_TRAINER_LAST3_USER", _DEV_FALLBACKS["SKILLEDGE_RMS_TRAINER_LAST3_USER"]), "pass": _env("SKILLEDGE_RMS_TRAINER_LAST3_PASS", _DEV_FALLBACKS["SKILLEDGE_RMS_TRAINER_LAST3_PASS"]), "role": "Trainer_Last_3_Months_Utilization"},
    "trainerRCSchedule":{"key": "111", "user": _env("SKILLEDGE_RMS_TR_RC_SCHEDULE_USER", _DEV_FALLBACKS["SKILLEDGE_RMS_TR_RC_SCHEDULE_USER"]), "pass": _env("SKILLEDGE_RMS_TR_RC_SCHEDULE_PASS", _DEV_FALLBACKS["SKILLEDGE_RMS_TR_RC_SCHEDULE_PASS"]), "role": "Trainer RC Schedule"},
    "courseWithoutExam":{"key": "213", "user": _env("SKILLEDGE_RMS_COURSE_WITHOUT_EXAM_USER", _DEV_FALLBACKS["SKILLEDGE_RMS_COURSE_WITHOUT_EXAM_USER"]), "pass": _env("SKILLEDGE_RMS_COURSE_WITHOUT_EXAM_PASS", _DEV_FALLBACKS["SKILLEDGE_RMS_COURSE_WITHOUT_EXAM_PASS"]), "role": "Course Whitout Exam"},
    "examCourseLinked": {"key": "215", "user": _env("SKILLEDGE_RMS_EXAM_COURSE_LINKED_USER", _DEV_FALLBACKS["SKILLEDGE_RMS_EXAM_COURSE_LINKED_USER"]), "pass": _env("SKILLEDGE_RMS_EXAM_COURSE_LINKED_PASS", _DEV_FALLBACKS["SKILLEDGE_RMS_EXAM_COURSE_LINKED_PASS"]), "role": "Exam Course Linked API"},
    "uniqueCertCount":  {"key": "72",  "user": _env("SKILLEDGE_RMS_UNIQUE_CERT_COUNT_USER", _DEV_FALLBACKS["SKILLEDGE_RMS_UNIQUE_CERT_COUNT_USER"]), "pass": _env("SKILLEDGE_RMS_UNIQUE_CERT_COUNT_PASS", _DEV_FALLBACKS["SKILLEDGE_RMS_UNIQUE_CERT_COUNT_PASS"]), "role": "Get Unique Certifications Count Value"},
    "courseList":       {"key": "164", "user": _env("SKILLEDGE_RMS_COURSE_LIST_USER", _DEV_FALLBACKS["SKILLEDGE_RMS_COURSE_LIST_USER"]),      "pass": _env("SKILLEDGE_RMS_COURSE_LIST_PASS", _DEV_FALLBACKS["SKILLEDGE_RMS_COURSE_LIST_PASS"]),  "role": "Course List"},
    "assignments":      {"key": "15",  "user": _env("SKILLEDGE_RMS_ASSIGNMENTS_USER", _DEV_FALLBACKS["SKILLEDGE_RMS_ASSIGNMENTS_USER"]),   "pass": _env("SKILLEDGE_RMS_ASSIGNMENTS_PASS", _DEV_FALLBACKS["SKILLEDGE_RMS_ASSIGNMENTS_PASS"]),  "role": "Assignment API"},
    # Extended RMS catalogue. These integrations intentionally have no source
    # credential fallback; configure the matching environment variables before
    # enabling their jobs. This keeps newly supplied credentials out of source.
    "upcomingAssignments": {"key": "93", "user": _env("SKILLEDGE_RMS_UPCOMING_ASSIGNMENTS_USER", ""), "pass": _env("SKILLEDGE_RMS_UPCOMING_ASSIGNMENTS_PASS", ""), "role": "Upcoming Assignments"},
    "unallocatedAssignments": {"key": "190", "user": _env("SKILLEDGE_RMS_UNALLOCATED_ASSIGNMENTS_USER", ""), "pass": _env("SKILLEDGE_RMS_UNALLOCATED_ASSIGNMENTS_PASS", ""), "role": "Unallocated Assignment"},
    "courseTechnology": {"key": "114", "user": _env("SKILLEDGE_RMS_COURSE_TECHNOLOGY_USER", ""), "pass": _env("SKILLEDGE_RMS_COURSE_TECHNOLOGY_PASS", ""), "role": "Course & Technology List"},
    "courseDomain": {"key": "205", "user": _env("SKILLEDGE_RMS_COURSE_DOMAIN_USER", ""), "pass": _env("SKILLEDGE_RMS_COURSE_DOMAIN_PASS", ""), "role": "Get Course and Domain"},
    "courseNames": {"key": "70", "user": _env("SKILLEDGE_RMS_COURSE_NAMES_USER", ""), "pass": _env("SKILLEDGE_RMS_COURSE_NAMES_PASS", ""), "role": "Get Course Name"},
    "courseAvailability": {"key": "104", "user": _env("SKILLEDGE_RMS_COURSE_AVAILABILITY_USER", ""), "pass": _env("SKILLEDGE_RMS_COURSE_AVAILABILITY_PASS", ""), "role": "Check Course Availability in RMS"},
    "courseSchedule": {"key": "246", "user": _env("SKILLEDGE_RMS_COURSE_SCHEDULE_USER", ""), "pass": _env("SKILLEDGE_RMS_COURSE_SCHEDULE_PASS", ""), "role": "Get Course Schedule"},
    "courseTrainers": {"key": "157", "user": _env("SKILLEDGE_RMS_COURSE_TRAINERS_USER", ""), "pass": _env("SKILLEDGE_RMS_COURSE_TRAINERS_PASS", ""), "role": "Get Inhouse and FL Trainers Of Courses"},
    "addTrainerSkill": {"key": "255", "user": _env("SKILLEDGE_RMS_ADD_TRAINER_SKILL_USER", ""), "pass": _env("SKILLEDGE_RMS_ADD_TRAINER_SKILL_PASS", ""), "role": "Add Trainer Skill (IDP)"},
}


def _load_local_instruction_credentials():
    """Load the user-supplied RMS catalogue into memory in development.

    The instruction files are treated as a local secret store: credentials are
    never copied into source, logs, cache payloads, or browser responses. In
    production this loader is disabled and environment-backed secrets remain
    mandatory.
    """
    if IS_PRODUCTION:
        return 0
    configured_dir = os.getenv("SKILLEDGE_API_INSTRUCTION_DIR", "").strip()
    catalogue = Path(configured_dir) if configured_dir else Path.home() / "Downloads" / "trainer_portal_api_details"
    if not catalogue.is_dir():
        log.info("Local RMS instruction catalogue was not found; environment credentials will be used.")
        return 0

    by_role = {str(cfg.get("role", "")).strip().casefold(): cfg for cfg in CONFIGS.values()}
    loaded = 0
    patterns = {
        "user": re.compile(r"^\s*username\s*:\s*(.+?)\s*$", re.I | re.M),
        "pass": re.compile(r"^\s*password\s*:\s*(.+?)\s*$", re.I | re.M),
        "role": re.compile(r"^\s*role\s*:\s*(.+?)\s*$", re.I | re.M),
        "key": re.compile(r'[\"\']api_key[\"\']\s*:\s*[\"\']?(\d+)', re.I),
    }
    for instruction in catalogue.glob("*.txt"):
        try:
            content = instruction.read_text(encoding="utf-8", errors="ignore")
            values = {name: (match.group(1).strip() if (match := pattern.search(content)) else "") for name, pattern in patterns.items()}
            cfg = by_role.get(values["role"].casefold())
            if not cfg or not values["user"] or not values["pass"]:
                continue
            cfg.update({"user": values["user"], "pass": values["pass"]})
            if values["key"]:
                cfg["key"] = values["key"]
            loaded += 1
        except OSError:
            continue
    log.info("Loaded %d RMS API credential sets from the local instruction catalogue.", loaded)
    return loaded


LOCAL_CATALOGUE_CREDENTIALS_LOADED = _load_local_instruction_credentials()


def is_configured(api_name):
    """Return whether an API has usable credentials without exposing them."""
    cfg = CONFIGS.get(api_name) or {}
    return bool(str(cfg.get("user") or "").strip() and str(cfg.get("pass") or "").strip())
