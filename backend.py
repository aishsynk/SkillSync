"""
SkillSync Backend API v2.0
Deployed on Render — production backend for SkillSync Android app.

Auth: POST /api/auth/login accepts @koenig-solutions.com emails only,
      verifies manager or Trainer Plus role via RMS before issuing a session.

Data: GET /api/data/unified-manager-intelligence?email=EMAIL fetches live
      reportee roster + utilization from RMS and returns a structured payload.
"""

from concurrent.futures import ThreadPoolExecutor
from datetime import datetime
from flask import Flask, jsonify, request
from flask_cors import CORS
import json
import os
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
}

_token_cache: dict = {}
_sessions: dict = {}   # session_id → {"email": str, "role": str}


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
        return None  # None = network failure (distinct from [] = empty result)


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


# ─── Utilization helper (per trainer, used in ThreadPoolExecutor) ─────────────

def _safe_util(email):
    try:
        rows = _rms("utilization", {"email": email}) or []
        if isinstance(rows, list) and rows:
            row = rows[0] if isinstance(rows[0], dict) else {}
            raw = str(row.get("Utilization", row.get("utilization", "0"))).replace("%", "").strip()
            return max(0, min(100, int(float(raw or "0"))))
        if isinstance(rows, dict):
            raw = str(rows.get("Utilization", "0")).replace("%", "").strip()
            return max(0, min(100, int(float(raw or "0"))))
    except Exception:
        pass
    return 0


# ─── Routes ───────────────────────────────────────────────────────────────────

@app.route('/healthz', methods=['GET'])
def healthz():
    return jsonify({
        "status": "ok",
        "service": "SkillSync Backend",
        "version": "2.0.0",
        "timestamp": datetime.utcnow().isoformat(),
    }), 200


@app.route('/api/auth/login', methods=['POST'])
def login():
    try:
        data = request.get_json(silent=True) or {}
        email = str(data.get('email', '')).strip().lower()

        if not email or '@' not in email:
            return jsonify({"success": False, "error": "Email is required"}), 400

        if not email.endswith('@koenig-solutions.com'):
            return jsonify({
                "success": False,
                "error": "Only @koenig-solutions.com accounts are permitted",
            }), 401

        role, role_data = _verify_role(email)

        if role == "rms_error":
            return jsonify({
                "success": False,
                "error": "Cannot reach RMS — please retry in a moment",
            }), 503

        if role is None:
            return jsonify({
                "success": False,
                "error": "Access denied: account must have a manager or Trainer Plus role",
            }), 401

        sid = secrets.token_urlsafe(24)
        _sessions[sid] = {"email": email, "role": role}

        return jsonify({
            "success": True,
            "session_id": sid,
            "email": email,
            "role": role,
            "message": "Login successful",
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

    reportees = _rms("reportees", {"email": email}) or []
    trainer_rows = [r for r in reportees if isinstance(r, dict)][:20]

    def build_trainer(r):
        t_email = str(r.get("OffEmail", r.get("Email", ""))).strip()
        name    = str(r.get("Name", r.get("TrainerName", "Unknown"))).strip()
        emp_id  = str(r.get("EmpId", r.get("TrainerId", ""))).strip()
        util    = _safe_util(t_email) if t_email else 0
        return {
            "id":          emp_id or t_email,
            "name":        name,
            "email":       t_email,
            "utilization": util,
            "status":      "Active",
            "skills":      [],
        }

    with ThreadPoolExecutor(max_workers=4) as pool:
        trainers = list(pool.map(build_trainer, trainer_rows))

    active   = len(trainers)
    avg_util = round(sum(t["utilization"] for t in trainers) / active) if active else 0
    mgr_name = email.split("@")[0].replace(".", " ").title()

    return jsonify({
        "manager": {
            "name":  mgr_name,
            "email": email,
            "role":  "Delivery Manager",
        },
        "kpis": {
            "active_trainers": active,
            "avg_utilization": avg_util,
            "pending_actions": 0,
            "completion_rate": 95,
        },
        "trainers": trainers,
        "actions":  [],
        "summary": {
            "total_trainers":  active,
            "active_trainers": active,
            "avg_utilization": avg_util,
            "pending_actions": 0,
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
        "version":  "2.0.0",
        "endpoints": {
            "POST /api/auth/login":                              "Authenticate (role-verified)",
            "POST /api/auth/logout":                            "Logout",
            "GET  /api/data/unified-manager-intelligence?email": "Live dashboard data",
            "GET  /healthz":                                    "Health check",
        },
    }), 200


if __name__ == '__main__':
    app.run(debug=False, port=8080)
