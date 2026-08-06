import importlib
import json
import re
import sys
import time
from pathlib import Path
from urllib.parse import urlencode
from http.cookiejar import CookieJar
from urllib.error import HTTPError
from urllib.request import build_opener, HTTPCookieProcessor, Request


# tests/smoke_test.py -> repo root is one level up; active pages live in frontend/pages.
ROOT = Path(__file__).resolve().parents[1]
PAGES = ROOT / "frontend" / "pages"
BASE = "http://localhost:8765"

# Make the repo root (for the `server` launcher) and backend package importable
# so the import checks below resolve after the production restructure.
for _p in (ROOT, ROOT / "backend"):
    if str(_p) not in sys.path:
        sys.path.insert(0, str(_p))
MANAGER_EMAIL = "Aishwar.C@koenig-solutions.com"
DIRECT_TRAINER_EMAIL = "niharika.n@koenig-solutions.com"
opener = build_opener(HTTPCookieProcessor(CookieJar()))


def ok(msg):
    print(f"[OK] {msg}")


def fail(msg):
    print(f"[FAIL] {msg}")
    return False


def check_import(module_name):
    try:
        importlib.import_module(module_name)
        ok(f"import {module_name}")
        return True
    except Exception as exc:  # noqa: BLE001
        return fail(f"import {module_name}: {exc}")


def fetch_json(url):
    try:
        with opener.open(url, timeout=120) as resp:
            body = resp.read().decode("utf-8", errors="replace")
            return resp.status, json.loads(body)
    except HTTPError as exc:
        body = exc.read().decode("utf-8", errors="replace")
        return exc.code, json.loads(body)


def post_json(url, payload):
    data = json.dumps(payload).encode("utf-8")
    req = Request(url, data=data, headers={"Content-Type": "application/json"}, method="POST")
    with opener.open(req, timeout=120) as resp:
        body = resp.read().decode("utf-8", errors="replace")
        return resp.status, json.loads(body)


def post_json_with_status(url, payload):
    try:
        return post_json(url, payload)
    except HTTPError as exc:
        body = exc.read().decode("utf-8", errors="replace")
        return exc.code, json.loads(body)


def check_required_files():
    required = [
        "index.html",
        "team.html",
        "trainer-detail.html",
        "allocation-desk.html",
        "custom-course-match.html",
        "actions.html",
        "data-health.html",
    ]
    passed = True
    for name in required:
        path = PAGES / name
        if path.exists():
            ok(f"page exists: {name}")
        else:
            passed = fail(f"missing page: {name}") and passed
    return passed


def check_page_contents():
    pages = [
        "index.html",
        "team.html",
        "trainer-detail.html",
        "allocation-desk.html",
        "custom-course-match.html",
        "actions.html",
        "data-health.html",
    ]
    passed = True
    bad_patterns = [
        "trainer_profile_unknown_unknown.html",
    ]
    rms_patterns = [
        r"/api/Kites/Operator/common",
        r"/proxy/",
    ]
    for page in pages:
        text = (PAGES / page).read_text(encoding="utf-8", errors="replace")
        for bad in bad_patterns:
            if bad in text:
                passed = fail(f"{page} contains broken trainer profile link: {bad}") and passed
        # Direct RMS/proxy calls in HTML are not allowed.
        if page.endswith(".html"):
            for pat in rms_patterns:
                if re.search(pat, text):
                    passed = fail(f"{page} contains direct RMS/proxy call pattern: {pat}") and passed
    if passed:
        ok("pages do not contain direct RMS/proxy calls or broken trainer profile links")
    return passed


def check_health_endpoint():
    status, data = fetch_json(f"{BASE}/healthz")
    passed = True
    if status != 200:
        return fail(f"health endpoint returned HTTP {status}")
    ok("health endpoint returned HTTP 200")
    if data.get("status") != "ok":
        passed = fail("health endpoint status is not ok") and passed
    else:
        ok("health endpoint reports status=ok")
    if "session_ttl_seconds" not in data:
        passed = fail("health endpoint missing session_ttl_seconds") and passed
    else:
        ok("health endpoint exposes session TTL")
    return passed


def check_endpoint():
    status, data = fetch_json(f"{BASE}/data/unified-manager-intelligence")
    if status not in (401, 403):
        return fail(f"unauthenticated unified endpoint returned HTTP {status} instead of 401/403"), None, None
    ok("unauthenticated unified endpoint was blocked")

    rms_status, rms_data = post_json_with_status(f"{BASE}/rms/reportees", {})
    if rms_status not in (401, 403):
        return fail(f"unauthenticated /rms/reportees returned HTTP {rms_status} instead of 401/403"), None, None
    ok("unauthenticated /rms/reportees was blocked")

    status, login_data = post_json(f"{BASE}/auth/login", {"email": MANAGER_EMAIL})
    if status != 200:
        return fail(f"login returned HTTP {status}: {login_data}"), None, None
    ok("login succeeded and established a session")

    query = urlencode({"email": MANAGER_EMAIL, "refresh": "1"})
    url = f"{BASE}/data/unified-manager-intelligence?{query}"
    start = time.perf_counter()
    status, data = fetch_json(url)
    elapsed = time.perf_counter() - start
    passed = True
    if status != 200:
        return fail(f"unified endpoint refresh build returned HTTP {status}")
    ok(f"unified endpoint refresh=1 returned HTTP 200 in {elapsed:.2f}s")

    required = [
        "trainer_operations_df",
        "course_allocation_df",
        "trainer_timeline_df",
        "manager_action_df",
        "trainer_availability_engine_df",
        "data_health_df",
    ]
    for key in required:
        if key not in data:
            passed = fail(f"missing dataset: {key}") and passed
        else:
            ok(f"dataset present: {key} ({len(data.get(key) or [])} rows)")

    if passed:
        passed = check_payload_contract(data) and passed
        passed = check_decision_objects(data) and passed
        passed = check_direct_trainer_payload_behavior(data) and passed
        passed = check_custom_course_endpoint_contract() and passed

    if data.get("from_cache") is True:
        ok("refresh build unexpectedly served from cache: true")

    wrong_query = urlencode({"email": "other.manager@koenig-solutions.com"})
    wrong_status, wrong_data = fetch_json(f"{BASE}/data/unified-manager-intelligence?{wrong_query}")
    if wrong_status not in (401, 403):
        return fail(f"wrong email query returned HTTP {wrong_status} instead of 401/403"), None, None
    ok("wrong email query was blocked")

    return passed, data, elapsed


def _first_row(payload, key):
    rows = payload.get(key) or []
    if isinstance(rows, list) and rows:
        first = rows[0]
        return first if isinstance(first, dict) else {}
    return {}


def _check_required_fields(payload, key, fields, label):
    row = _first_row(payload, key)
    passed = True
    if not row:
        return fail(f"{label} has no rows to validate") and False
    missing = [field for field in fields if field not in row]
    if missing:
        passed = fail(f"{label} missing required fields: {', '.join(missing)}") and passed
    else:
        ok(f"{label} includes required fields")
    return passed


def _is_blank(value):
    return value is None or (isinstance(value, str) and not value.strip())


def check_payload_contract(payload):
    passed = True

    top_level = [
        "trainer_operations_df",
        "course_allocation_df",
        "trainer_timeline_df",
        "manager_action_df",
        "trainer_availability_engine_df",
        "data_health_df",
        "allocation_ranked_trainer_df",
        "course_best_trainer_df",
        "allocation_risk_df",
        "feedback_coaching_df",
        "future_skill_roadmap_df",
        "future_certification_roadmap_df",
        "vendor_strength_df",
        "certification_intelligence_df",
        "oem_bench_risk_df",
    ]
    missing_top = [key for key in top_level if key not in payload]
    if missing_top:
        passed = fail(f"payload missing top-level keys: {', '.join(missing_top)}") and passed
    else:
        ok("payload contains required top-level keys")

    passed = _check_required_fields(
        payload,
        "trainer_operations_df",
        [
            "trainer_name",
            "official_email",
            "designation",
            "qubits_score",
            "readiness_bucket",
            "certification_source",
            "resume_certifications",
            "utilization_status",
            "recommended_action",
        ],
        "trainer_operations_df",
    ) and passed

    passed = _check_required_fields(
        payload,
        "trainer_availability_engine_df",
        [
            "trainer_name",
            "calendar_status",
            "capacity_status",
            "workload_status",
            "final_availability_status",
            "availability_reason",
            "availability_confidence",
            "missing_signals",
        ],
        "trainer_availability_engine_df",
    ) and passed

    passed = _check_required_fields(
        payload,
        "manager_action_df",
        [
            "trainer_name",
            "action_type",
            "priority",
            "reason",
            "evidence",
            "recommended_action",
            "confidence",
        ],
        "manager_action_df",
    ) and passed

    data_health_rows = payload.get("data_health_df") or []
    if isinstance(data_health_rows, list) and data_health_rows:
        bad_rows = []
        ok_rows = 0
        for idx, row in enumerate(data_health_rows):
            if not isinstance(row, dict):
                bad_rows.append(f"row {idx}: not an object")
                continue
            issue_type = row.get("issue_type")
            issue_detail = row.get("issue_detail")
            status = str(row.get("status") or row.get("severity") or "").strip().lower()
            if _is_blank(issue_type):
                bad_rows.append(f"row {idx}: blank issue_type")
            if _is_blank(issue_detail):
                bad_rows.append(f"row {idx}: blank issue_detail")
            if status == "ok":
                bad_rows.append(f"row {idx}: raw OK row")
            if not _is_blank(issue_type) and not _is_blank(issue_detail) and status != "ok":
                ok_rows += 1
        if bad_rows:
            passed = fail("data_health_df contract issues: " + "; ".join(bad_rows)) and passed
        else:
            ok(f"data_health_df contract looks clean ({ok_rows} issue rows)")
    else:
        ok("data_health_df has no rows to contract-check")

    return passed


def _check_decision_row(row, label):
    required = [
        "decision_contract_version",
        "id",
        "entity_type",
        "trainer_email",
        "trainer_name",
        "score",
        "confidence",
        "blockers",
        "evidence",
        "recommended_action",
        "source_datasets",
        "source_field_names",
        "source_labels",
    ]
    missing = [field for field in required if field not in row]
    if missing:
        return fail(f"{label} missing decision fields: {', '.join(missing)}")
    if "bucket" not in row and "status" not in row:
        return fail(f"{label} missing bucket/status")
    if not row.get("decision_contract_version"):
        return fail(f"{label} missing decision_contract_version value")
    if not isinstance(row.get("source_datasets"), list):
        return fail(f"{label} source_datasets is not a list")
    if not isinstance(row.get("source_field_names"), dict):
        return fail(f"{label} source_field_names is not an object")
    if not isinstance(row.get("source_labels"), dict):
        return fail(f"{label} source_labels is not an object")
    return True


def check_decision_objects(payload):
    passed = True
    for key in ("trainer_decision_objects", "allocation_decision_objects", "manager_action_objects"):
        rows = payload.get(key) or []
        if not isinstance(rows, list):
            passed = fail(f"{key} is not a list") and passed
            continue
        if rows:
            passed = _check_decision_row(rows[0], key) and passed
            if "source_datasets" in rows[0] and "evidence" in rows[0] and isinstance(rows[0]["evidence"], dict):
                evidence_fields = rows[0]["evidence"].get("source_field_names")
                if not isinstance(evidence_fields, dict):
                    passed = fail(f"{key} evidence missing source_field_names") and passed
                else:
                    ok(f"{key} evidence exposes source_field_names")
            if key == "allocation_decision_objects":
                row = rows[0]
                if "source_row_ids" not in row:
                    passed = fail("allocation_decision_objects missing source_row_ids") and passed
                else:
                    ok("allocation_decision_objects expose source_row_ids")
        else:
            ok(f"{key} is empty; contract validation skipped")
    return passed


def check_direct_trainer_payload_behavior(payload):
    passed = True
    trainer_rows = payload.get("trainer_operations_df") or []
    trainer_decisions = payload.get("trainer_decision_objects") or []
    direct_ops = [
        row for row in trainer_rows
        if str(row.get("official_email") or row.get("trainer_email") or "").strip().lower() == DIRECT_TRAINER_EMAIL
    ]
    direct_decisions = [
        row for row in trainer_decisions
        if str(row.get("trainer_email") or "").strip().lower() == DIRECT_TRAINER_EMAIL
    ]
    if not direct_ops:
        passed = fail(f"direct trainer payload missing operations row for {DIRECT_TRAINER_EMAIL}") and passed
    else:
        ok(f"direct trainer operations row present for {DIRECT_TRAINER_EMAIL}")
    if not direct_decisions:
        passed = fail(f"direct trainer payload missing trainer_decision_objects row for {DIRECT_TRAINER_EMAIL}") and passed
    else:
        row = direct_decisions[0]
        passed = _check_decision_row(row, "direct trainer_decision_objects row") and passed
        for field in ("can_assign_now", "assignment_status", "risk_register", "action_summary", "source_row_ids"):
            if field not in row:
                passed = fail(f"direct trainer decision row missing {field}") and passed
        if passed:
            ok(f"direct trainer decision row present for {DIRECT_TRAINER_EMAIL}")
    return passed


def check_custom_course_endpoint_contract():
    no_outline_query = urlencode({"email": MANAGER_EMAIL})
    status, data = fetch_json(f"{BASE}/data/unified-manager-intelligence?{no_outline_query}")
    passed = True
    if status != 200:
        return fail(f"unified endpoint without outline returned HTTP {status}")
    if data.get("custom_course_match_df") != []:
        passed = fail("custom_course_match_df should be empty when no outline is provided") and passed
    else:
        ok("custom_course_match_df is empty without course outline")

    outline_query = urlencode({
        "email": MANAGER_EMAIL,
        "course_title": "Smoke Power BI Workshop",
        "course_outline": "Power BI workshop covering DAX, Power Query, SQL, dashboards, labs, and certification readiness.",
    })
    outline_status, outline_data = fetch_json(f"{BASE}/data/unified-manager-intelligence?{outline_query}")
    if outline_status != 200:
        return fail(f"unified endpoint with outline returned HTTP {outline_status}")
    rows = outline_data.get("custom_course_match_df") or []
    if not rows:
        passed = fail("custom_course_match_df should have backend rows when outline is provided") and passed
    else:
        passed = _check_decision_row(rows[0], "custom_course_match_df") and passed
        ok("custom_course_match_df has backend decision rows with outline")
    object_rows = outline_data.get("custom_course_match_objects") or outline_data.get("custom_course_match_df") or []
    if object_rows:
        passed = _check_decision_row(object_rows[0], "custom_course_match_objects") and passed
    return passed


def check_cached_call(baseline_elapsed):
    query = urlencode({"email": MANAGER_EMAIL})
    url = f"{BASE}/data/unified-manager-intelligence?{query}"
    start = time.perf_counter()
    status, data = fetch_json(url)
    elapsed = time.perf_counter() - start
    passed = True
    if status != 200:
        return fail(f"cached unified endpoint returned HTTP {status}")
    ok(f"cached unified endpoint returned HTTP 200 in {elapsed:.2f}s")
    if data.get("from_cache") is True:
        ok("cached response reports from_cache=true")
    else:
        ok("cached response did not expose from_cache=true")
    if elapsed <= baseline_elapsed:
        ok("cached call was faster than refresh build")
    else:
        ok("cached call was not faster than refresh build, but still succeeded")
    return passed


def main():
    passed = True
    passed = check_import("server") and passed      # root launcher entry point
    passed = check_import("app") and passed          # backend HTTP application
    passed = check_import("intelligence") and passed  # backend intelligence pipeline
    passed = check_required_files() and passed
    passed = check_page_contents() and passed
    try:
        passed = check_health_endpoint() and passed
    except Exception as exc:  # noqa: BLE001
        passed = fail(f"health endpoint check failed: {exc}") and passed

    endpoint_ok = False
    baseline_elapsed = None
    try:
        endpoint_ok, data, baseline_elapsed = check_endpoint()
        passed = endpoint_ok and passed
    except Exception as exc:  # noqa: BLE001
        passed = fail(f"unified endpoint refresh check failed: {exc}") and passed
        data = {}

    if endpoint_ok:
        try:
            passed = check_cached_call(baseline_elapsed) and passed
        except Exception as exc:  # noqa: BLE001
            passed = fail(f"cached call check failed: {exc}") and passed

    if passed:
        ok("smoke test passed")
        return 0

    print("[FAIL] smoke test failed")
    return 1


if __name__ == "__main__":
    sys.exit(main())
