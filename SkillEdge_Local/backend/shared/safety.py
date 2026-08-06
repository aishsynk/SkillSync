"""Safety helpers for redaction, sanitization, and stable parsing."""

def safe_num(v, default=0.0):
    try:
        return float(v)
    except (TypeError, ValueError):
        return default


def safe_int(v, default=0):
    try:
        return int(float(v))
    except (TypeError, ValueError):
        return default


def safe_truthy(v):
    return str(v).strip().lower() in ("1", "true", "yes", "y")


def clean_name(v):
    return " ".join(str(v or "").split())


def safe_error(exc):
    etype = type(exc).__name__
    msg = str(exc)
    for bad in ("accessToken", "deviceToken", "userPassword", "apikey", "userName", "userRole", "Bearer"):
        if bad in msg:
            msg = msg.split(bad)[0].strip() + " [redacted]"
    return etype, (msg[:200] or "unknown error")
