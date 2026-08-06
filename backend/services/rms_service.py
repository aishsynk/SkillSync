"""Server-side RMS relay helpers."""

from api.client import _call as _rms_call
from api.config import CONFIGS


def is_known_api(api_name):
    return api_name in CONFIGS


def call_api(api_name, body):
    try:
        content = _rms_call(api_name, body)
        return {"content": content}
    except Exception as exc:  # noqa: BLE001
        return {"content": [], "error": str(exc)[:180]}
