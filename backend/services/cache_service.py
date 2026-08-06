"""Cache helpers for unified intelligence payloads."""

import json
import re
import time
from pathlib import Path

# backend/services/cache_service.py -> repo root is two levels up.
REPO_ROOT = Path(__file__).resolve().parents[2]
CACHE_DIR = REPO_ROOT / "runtime" / "cache"
CACHE_TTL = 4 * 60 * 60
CACHE_SCHEMA_VERSION = 5
CACHE_DIR.mkdir(parents=True, exist_ok=True)


def cache_path(email):
    slug = re.sub(r"[^a-z0-9]+", "_", (email or "default").lower())
    return CACHE_DIR / f"intel_{slug}.json"


def cache_fresh(path):
    if not path.exists() or (time.time() - path.stat().st_mtime) >= CACHE_TTL:
        return False
    try:
        cached = read_cache(path)
        return cached.get("cache_schema_version") == CACHE_SCHEMA_VERSION
    except (OSError, ValueError, TypeError):
        return False


def read_cache(path):
    return json.loads(path.read_text(encoding="utf-8"))


def write_cache(path, data):
    data["cache_schema_version"] = CACHE_SCHEMA_VERSION
    path.write_text(json.dumps(data), encoding="utf-8")

