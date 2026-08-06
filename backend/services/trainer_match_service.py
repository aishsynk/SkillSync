"""Trainer matching service entry points.

The first migration uses custom_course_match_service directly. This module is
kept as the future home for reusable trainer-fit calculations across pages.
"""

from __future__ import annotations

from typing import Any, Dict, List


def build_trainer_match_objects(rows: List[Dict[str, Any]] | None = None) -> List[Dict[str, Any]]:
    return [r for r in (rows or []) if isinstance(r, dict)]

