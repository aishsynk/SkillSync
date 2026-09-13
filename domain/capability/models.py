"""Capability Intelligence domain models.

Plain structured values only — no I/O, no RMS calls, no scoring. The
repository (`repositories/capability_store.py`) persists these; the service
(`services/capability/capability_service.py`) applies the rules (approval
gating, honest-empty states). Nothing here computes a match score or a
percentage — that is explicitly out of scope until the matching-engine phase.

Every capability claim about a course or a trainer must trace back to a
`CapabilityEvidence` record. A capability with no evidence is not a capability
claim — see `CapabilityEvidence.NO_EVIDENCE` and the store's refusal to persist
a `TrainerCapability` without at least one evidence row.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from typing import List, Optional


# ── Relationship semantics (instruction's five explicit meanings) ───────────

class RelationshipType:
    IS_A = "IS_A"
    PARENT_OF = "PARENT_OF"
    CHILD_OF = "CHILD_OF"
    RELATED_TO = "RELATED_TO"          # never counted as direct evidence
    REQUIRES = "REQUIRES"
    SPECIALIZATION_OF = "SPECIALIZATION_OF"

    ALL = (IS_A, PARENT_OF, CHILD_OF, RELATED_TO, REQUIRES, SPECIALIZATION_OF)


# ── Evidence hierarchy ───────────────────────────────────────────────────────

class EvidenceType:
    VERIFIED_SKILL = "VERIFIED_SKILL"
    CERTIFICATION = "CERTIFICATION"
    ASSESSMENT = "ASSESSMENT"
    DELIVERY_HISTORY = "DELIVERY_HISTORY"
    TRAINING_COMPLETION = "TRAINING_COMPLETION"
    MANAGER_APPROVAL = "MANAGER_APPROVAL"

    ALL = (VERIFIED_SKILL, CERTIFICATION, ASSESSMENT, DELIVERY_HISTORY,
           TRAINING_COMPLETION, MANAGER_APPROVAL)


# ── Curation lifecycle ───────────────────────────────────────────────────────

class MappingStatus:
    DRAFT = "DRAFT"
    REVIEW_REQUIRED = "REVIEW_REQUIRED"
    APPROVED = "APPROVED"
    DEPRECATED = "DEPRECATED"

    ALL = (DRAFT, REVIEW_REQUIRED, APPROVED, DEPRECATED)
    # Only these feed a future matching engine — a DRAFT/REVIEW_REQUIRED
    # mapping is visible to curators but must never influence allocation.
    AUTHORITATIVE = (APPROVED,)


@dataclass
class Capability:
    """A canonical, named unit of knowledge. Never a course title or code."""

    id: str                      # stable slug, e.g. "microsoft_fabric"
    name: str                    # display name, e.g. "Microsoft Fabric"
    family: str = ""             # grouping, e.g. "Data Engineering"
    description: str = ""


@dataclass
class CapabilityAlias:
    capability_id: str
    alias: str                   # e.g. "Fabric", "MS Fabric"


@dataclass
class CapabilityRelationship:
    from_id: str
    to_id: str
    type: str                    # one of RelationshipType.ALL


@dataclass
class CourseCapabilityRequirement:
    capability_id: str
    mandatory: bool = True       # False = preferred, not required
    minimum_level: Optional[int] = None   # optional, scale left to curator


@dataclass
class CourseCapabilityProfile:
    """WHAT a course requires, keyed on the stable RMS numeric Cid — never on
    course title or course_code (both are unreliable, per the 2026-09-13
    Phase 0 live-verification findings: blank/inconsistent-case codes,
    duplicate title rows)."""

    rms_cid: int
    course_code: str = ""        # metadata only, never the join key
    course_title: str = ""       # metadata only, never the join key
    vendor: str = ""             # metadata only
    requirements: List[CourseCapabilityRequirement] = field(default_factory=list)
    required_certifications: List[str] = field(default_factory=list)
    status: str = MappingStatus.DRAFT
    source: str = ""             # e.g. "human_curated", "imported"
    updated_by: str = ""
    updated_at: str = ""

    @property
    def is_authoritative(self) -> bool:
        return self.status in MappingStatus.AUTHORITATIVE


@dataclass
class CapabilityEvidence:
    """One provenance-carrying fact backing a trainer's claim to a capability.

    `raw` retains the original source record (e.g. the RMS trainerDetails
    row) so the interpretation can be audited or corrected without losing the
    underlying fact — RMS truth is never overwritten, only interpreted.
    """

    type: str                    # one of EvidenceType.ALL
    source_system: str = ""      # e.g. "RMS.trainerDetails", "manual"
    source_record_id: str = ""
    captured_at: str = ""
    raw: dict = field(default_factory=dict)


@dataclass
class TrainerCapability:
    """A trainer's claim to one capability — always evidence-backed.

    There is no `proficiency` float here without at least one
    `CapabilityEvidence` entry; the store enforces this (see
    `CapabilityStore.upsert_trainer_capability`). This is the concrete
    expression of the instruction's invariant: NO VERIFIED DATA → NO
    CAPABILITY CLAIM.
    """

    trainer_email: str
    capability_id: str
    evidence: List[CapabilityEvidence] = field(default_factory=list)
    proficiency: Optional[str] = None   # free text/level, curator-set only
    verified: bool = False              # True only once evidence exists
    last_verified_at: str = ""
