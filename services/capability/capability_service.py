"""CapabilityService — the one place course/trainer capability rules live.

Phase 1 scope only: prove `Course -> Required Capabilities` and
`Trainer -> Verified Capabilities/Evidence` as reusable, evidence-backed
facts. This service deliberately does NOT compute a match score, a ranking,
or any percentage — that is the matching-engine phase's job, consuming this
service's output, not defining it.

Callers (eventually Plan, People, Trainer 360, Courses, Opportunities,
Upskilling) should go through this service, never the repository directly,
so the approval-gating rule below is enforced in exactly one place.
"""

from __future__ import annotations

from typing import Any, Dict, List, Optional

from domain.capability.models import MappingStatus
from repositories.capability_store import CapabilityStore


class CapabilityService:
    def __init__(self, store: CapabilityStore):
        self._store = store

    # ── Course side ──────────────────────────────────────────────────────────

    def get_course_capability_profile(self, rms_cid: int, *, authoritative_only: bool = True
                                       ) -> Optional[Dict[str, Any]]:
        """Returns the latest curated profile for a course, or None if none
        exists yet. When `authoritative_only` (the default — this is what a
        future matcher must always use), a DRAFT/REVIEW_REQUIRED mapping is
        withheld: an unapproved mapping must never look like ground truth to
        an allocation decision. Set `authoritative_only=False` only for a
        curation/admin view that needs to see pending work.
        """
        profile = self._store.latest_course_capability(rms_cid)
        if not profile:
            return None
        if authoritative_only and profile["status"] not in MappingStatus.AUTHORITATIVE:
            return None
        return profile

    def submit_course_capability_draft(self, rms_cid, requirements, required_certifications=None,
                                        course_code="", course_title="", vendor="",
                                        curator_email="") -> bool:
        """A human curator's first pass. Always lands as DRAFT — nothing
        submitted here is ever auto-approved."""
        return self._store.upsert_course_capability(
            rms_cid=rms_cid, requirements=requirements,
            required_certifications=required_certifications,
            course_code=course_code, course_title=course_title, vendor=vendor,
            status=MappingStatus.DRAFT, source="human_curated", updated_by=curator_email,
        )

    def request_review(self, rms_cid, updated_by="") -> bool:
        return self._store.set_course_capability_status(
            rms_cid, MappingStatus.REVIEW_REQUIRED, updated_by=updated_by)

    def approve_course_capability(self, rms_cid, approved_by) -> bool:
        """Only path to AUTHORITATIVE. Requires a named approver — an
        anonymous/blank approver is refused, since `updated_by` is the audit
        trail for who made a mapping trusted enough to drive allocation."""
        if not str(approved_by or "").strip():
            return False
        return self._store.set_course_capability_status(
            rms_cid, MappingStatus.APPROVED, updated_by=approved_by)

    def deprecate_course_capability(self, rms_cid, updated_by="") -> bool:
        return self._store.set_course_capability_status(
            rms_cid, MappingStatus.DEPRECATED, updated_by=updated_by)

    # ── Trainer side ─────────────────────────────────────────────────────────

    def get_trainer_capabilities(self, trainer_email: str) -> List[Dict[str, Any]]:
        """Always evidence-backed by construction (the repository refuses to
        surface a capability without linked evidence). An empty list is the
        honest answer for a trainer with no recorded capability evidence —
        never padded, never defaulted."""
        return self._store.trainer_capabilities(trainer_email)

    def record_evidence(self, trainer_email, capability_id, evidence_type,
                         source_system="", source_record_id="", raw=None,
                         proficiency=None) -> bool:
        return self._store.add_trainer_capability_evidence(
            trainer_email=trainer_email, capability_id=capability_id,
            evidence_type=evidence_type, source_system=source_system,
            source_record_id=source_record_id, raw=raw, proficiency=proficiency,
        )

    # ── Taxonomy ─────────────────────────────────────────────────────────────

    def resolve_capability(self, text: str) -> Optional[str]:
        """Exact canonical-name/alias lookup only. No fuzzy or semantic
        matching lives here — that belongs to the (not-yet-built) matching
        engine's own discovery layer, and even there must never be treated as
        proof of competency by itself."""
        return self._store.resolve_alias(text)
