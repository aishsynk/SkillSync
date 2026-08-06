"""Low-level Koenig API client helpers for SkillEdge."""

import json
import urllib.error
import urllib.parse
import urllib.request
import threading

from shared.constants import API_BASE, TOKEN_ENDPOINT, DATA_ENDPOINT, DEFAULT_TIMEOUT
from api.config import CONFIGS

_token_cache = {}
_token_locks = {}
_token_locks_guard = threading.Lock()
# RMS degrades sharply under nested fan-out. This is a process-wide ceiling,
# covering both token and data calls, irrespective of trainer worker count.
_request_slots = threading.BoundedSemaphore(4)


def _post(path, body, timeout=DEFAULT_TIMEOUT):
    req = urllib.request.Request(
        API_BASE + path,
        data=json.dumps(body).encode(),
        headers={"Content-Type": "application/json", "Accept": "application/json"},
        method="POST",
    )
    with _request_slots:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            return json.loads(resp.read().decode())


def _get_token(api_name, timeout=DEFAULT_TIMEOUT):
    if api_name in _token_cache:
        return _token_cache[api_name]
    with _token_locks_guard:
        lock = _token_locks.setdefault(api_name, threading.Lock())
    with lock:
        if api_name in _token_cache:
            return _token_cache[api_name]
        cfg = CONFIGS[api_name]
        js = _post(TOKEN_ENDPOINT, {"userName": cfg["user"], "userPassword": cfg["pass"], "userRole": cfg["role"]}, timeout=timeout)
        tokens = js.get("content") or {}
        _token_cache[api_name] = tokens
        return tokens


def _call(api_name, body, timeout=DEFAULT_TIMEOUT):
    cfg = CONFIGS[api_name]
    last_err = None
    for attempt in range(2):
        try:
            tok = _get_token(api_name, timeout=timeout)
            qs = f"?apikey={cfg['key']}&accessToken={_enc(tok.get('accessToken',''))}&deviceToken={_enc(tok.get('deviceToken',''))}"
            js = _post(DATA_ENDPOINT + qs, body, timeout=timeout)
            code = js.get("statuscode", js.get("statusCode", 200))
            if code != 200:
                msg = js.get("message") or f"status {code}"
                if code in (401, 403) and attempt == 0:
                    _token_cache.pop(api_name, None)
                    continue
                raise RuntimeError(msg)
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
        except urllib.error.HTTPError as e:
            if e.code in (401, 403) and attempt < 1:
                _token_cache.pop(api_name, None)
                last_err = e
                continue
            raise
        except Exception as e:  # noqa: BLE001
            last_err = e
            if "Forbidden" in str(e) and attempt < 1:
                _token_cache.pop(api_name, None)
                continue
            raise
    if last_err:
        raise last_err
    return []


def _enc(s):
    return urllib.parse.quote(str(s), safe="")


def clear_token_cache():
    _token_cache.clear()
