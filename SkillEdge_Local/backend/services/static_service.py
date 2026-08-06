"""Static file resolution helpers for SkillEdge.

Serving model after the production restructure:
  * /assets/*  -> served from the SeanTheme assets dir (frontend/assets)
  * everything else -> searched, in order, across the given page roots
    (frontend/pages, then frontend/deprecated, then frontend root for /js/*),
    so active pages, deprecated redirect pages, and shared js all keep working
    at their original URLs even though the files moved under /frontend.
"""

import mimetypes
import os
from pathlib import Path


def _try(file_path, allowed_root):
    try:
        resolved = file_path.resolve()
    except Exception:
        return None
    allowed_root = Path(allowed_root).resolve()
    # containment check (prevent path traversal outside the allowed root)
    if resolved != allowed_root and allowed_root not in resolved.parents:
        return None
    if not resolved.exists() or not resolved.is_file():
        return None
    mime, _ = mimetypes.guess_type(str(resolved))
    return {
        "path": resolved,
        "mime": mime or "application/octet-stream",
        "data": resolved.read_bytes(),
    }


def resolve_static_request(path, assets_dir, page_roots):
    """Resolve a GET path to a static file.

    Args:
        path:       request path (e.g. "/index.html", "/assets/css/app.min.css", "/js/api.js").
        assets_dir: directory that backs the /assets/ URL prefix.
        page_roots: ordered list of directories to search for non-asset requests.
    Returns a dict {path, mime, data} or None.
    """
    if path.startswith("/assets/"):
        rel = path[len("/assets/"):]
        return _try(Path(assets_dir) / rel.replace("/", os.sep), assets_dir)

    rel = (path.lstrip("/") or "login.html").replace("/", os.sep)
    for root in page_roots:
        hit = _try(Path(root) / rel, root)
        if hit:
            return hit
    return None
