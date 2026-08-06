"""Canonical batch engagement and live trainer-state decisions.

Only dated assignment evidence can assert that a trainer is teaching now.
Undated assignment indexes and utilization are supporting context, never proof of
current engagement.
"""

from __future__ import annotations

from datetime import datetime, timezone, timedelta
from typing import Any, Dict, Iterable, List, Optional


def _text(value: Any) -> str:
    return str(value or "").strip()


def _email(value: Any) -> str:
    return _text(value).lower()


def _parse_datetime(date_value: Any, time_value: Any = None) -> Optional[datetime]:
    date_text = _text(date_value)
    time_text = _text(time_value)
    if not date_text:
        return None
    combined = f"{date_text} {time_text}".strip() if time_text else date_text
    candidates = [combined, date_text]
    formats = (
        "%Y-%m-%d %H:%M:%S", "%Y-%m-%d %H:%M", "%Y-%m-%d",
        "%d-%m-%Y %H:%M:%S", "%d-%m-%Y %H:%M", "%d-%m-%Y",
        "%d/%m/%Y %H:%M:%S", "%d/%m/%Y %H:%M", "%d/%m/%Y",
        "%m/%d/%Y %H:%M:%S", "%m/%d/%Y %H:%M", "%m/%d/%Y",
        "%d-%b-%Y %H:%M:%S", "%d-%b-%Y %H:%M", "%d-%b-%Y",
    )
    for candidate in candidates:
        normalized = candidate.replace("Z", "+00:00")
        try:
            parsed = datetime.fromisoformat(normalized)
            return parsed if parsed.tzinfo else parsed.replace(tzinfo=timezone.utc)
        except ValueError:
            pass
        for fmt in formats:
            try:
                return datetime.strptime(candidate, fmt).replace(tzinfo=timezone.utc)
            except ValueError:
                continue
    return None


def _iso(value: Optional[datetime]) -> Optional[str]:
    return value.astimezone(timezone.utc).isoformat() if value else None


def _assignment_status(row: Dict[str, Any]) -> str:
    return _text(row.get("Status") or row.get("AssignmentStatus") or row.get("CourseStatus")).lower()


def _is_cancelled(row: Dict[str, Any]) -> bool:
    status = _assignment_status(row)
    return any(word in status for word in ("cancel", "delete", "inactive", "rejected"))


def build_batch_engagement_rows(
    fetched_trainers: Iterable[Dict[str, Any]],
    *,
    now: Optional[datetime] = None,
) -> List[Dict[str, Any]]:
    now = now or datetime.now(timezone.utc)
    if not now.tzinfo:
        now = now.replace(tzinfo=timezone.utc)
    rows: List[Dict[str, Any]] = []
    seen = set()
    for fetched in fetched_trainers or []:
        trainer = fetched.get("trainer") or {}
        trainer_email = _email(fetched.get("email") or trainer.get("OffEmail"))
        trainer_key = trainer_email or _text(fetched.get("emp") or trainer.get("TrainerId") or trainer.get("TrainerName"))
        trainer_name = _text(trainer.get("TrainerName")) or "Unknown"
        primary_rows = ((fetched.get("prev_upcoming") or {}).get("rows") or [])
        upcoming_rows = ((fetched.get("upcoming_assignments") or {}).get("rows") or [])
        source_rows = [("Previous & Upcoming Assignments", row) for row in primary_rows]
        source_rows.extend(("Upcoming Assignments", row) for row in upcoming_rows)
        for source_index, source_item in enumerate(source_rows):
            source_api, source = source_item
            if not isinstance(source, dict) or _is_cancelled(source):
                continue
            start = _parse_datetime(source.get("StarDate") or source.get("StartDate"), source.get("StartTime"))
            end = _parse_datetime(source.get("EndDate"), source.get("EndTime"))
            if start and end and end < start and start.date() == end.date():
                end += timedelta(days=1)
            assignment_id = _text(source.get("AssignmentId") or source.get("AssignmentID"))
            course_name = _text(source.get("Course") or source.get("CourseName"))
            dedup_key = (trainer_key, assignment_id or course_name.lower(), _iso(start), _iso(end))
            if dedup_key in seen:
                continue
            seen.add(dedup_key)
            if start and end and end < start:
                state = "invalid"
            elif start and end and start <= now < end:
                state = "current"
            elif start and start > now:
                state = "upcoming"
            elif end and end <= now:
                state = "previous"
            else:
                state = "undated"
            evidence_quality = "high" if assignment_id and start and end else "medium" if start or end else "low"
            rows.append({
                "batch_key": assignment_id or f"{trainer_key}:{source_index}:{course_name}",
                "assignment_id": assignment_id or None,
                "trainer_key": trainer_key,
                "trainer_email": trainer_email or None,
                "trainer_name": trainer_name,
                "course_name": course_name or None,
                "vendor": _text(source.get("Vendor")) or None,
                "delivery_mode": _text(source.get("Mode")) or None,
                "participant_count": source.get("NoOfParticipants"),
                "location": _text(source.get("Location")) or None,
                "start_at": _iso(start),
                "end_at": _iso(end),
                "engagement_state": state,
                "source_api": source_api,
                "source_row_index": source_index,
                "evidence_quality": evidence_quality,
                "retrieved_at": _iso(now),
            })
    return rows


def build_trainer_current_states(
    fetched_trainers: Iterable[Dict[str, Any]],
    batch_rows: Iterable[Dict[str, Any]],
    *,
    now: Optional[datetime] = None,
) -> List[Dict[str, Any]]:
    now = now or datetime.now(timezone.utc)
    if not now.tzinfo:
        now = now.replace(tzinfo=timezone.utc)
    batch_by_trainer: Dict[str, List[Dict[str, Any]]] = {}
    for row in batch_rows or []:
        batch_by_trainer.setdefault(_text(row.get("trainer_key")), []).append(row)

    results: List[Dict[str, Any]] = []
    for fetched in fetched_trainers or []:
        trainer = fetched.get("trainer") or {}
        email = _email(fetched.get("email") or trainer.get("OffEmail"))
        key = email or _text(fetched.get("emp") or trainer.get("TrainerId") or trainer.get("TrainerName"))
        rows = batch_by_trainer.get(key, [])
        current = sorted((r for r in rows if r.get("engagement_state") == "current"), key=lambda r: r.get("start_at") or "")
        upcoming = sorted((r for r in rows if r.get("engagement_state") == "upcoming"), key=lambda r: r.get("start_at") or "")
        invalid = [r for r in rows if r.get("engagement_state") == "invalid"]
        rc_rows = ((fetched.get("rc_schedule") or {}).get("rows") or [])
        has_rc_evidence = bool(rc_rows)
        if current:
            status = "teaching_now"
            confidence = 95 if current[0].get("evidence_quality") == "high" else 80
            reason = "A dated assignment overlaps the current time."
        elif upcoming and upcoming[0].get("start_at", "")[:10] == _iso(now)[:10]:
            status = "scheduled_today"
            confidence = 90
            reason = "A dated assignment starts later today."
        elif upcoming:
            status = "preparing"
            confidence = 80
            reason = "A future dated assignment is visible."
        elif has_rc_evidence:
            status = "unknown"
            confidence = 35
            reason = "RC schedule rows exist, but their date contract is not yet normalized; no busy or free claim is made."
        elif rows:
            status = "free"
            confidence = 70
            reason = "Dated assignment history is available and no current or future assignment is visible in the fetched window."
        else:
            status = "unknown"
            confidence = 25
            reason = "No trustworthy dated assignment evidence is available."
        conflicts = []
        if len(current) > 1:
            conflicts.append("Multiple current assignments overlap")
        if invalid:
            conflicts.append(f"{len(invalid)} assignment row(s) have invalid date ranges")
        if conflicts:
            confidence = max(20, confidence - 25)
        results.append({
            "trainer_key": key,
            "trainer_email": email or None,
            "trainer_name": _text(trainer.get("TrainerName")) or "Unknown",
            "current_status": status,
            "status_label": status.replace("_", " ").title(),
            "current_batch": current[0] if current else None,
            "next_batch": upcoming[0] if upcoming else None,
            "current_batch_count": len(current),
            "upcoming_batch_count": len(upcoming),
            "confidence": confidence,
            "reason": reason,
            "conflicts": conflicts,
            "missing_evidence": [] if rows else ["dated assignment schedule"],
            "source_datasets": ["batch_engagement_df"],
            "as_of": _iso(now),
        })
    return results
