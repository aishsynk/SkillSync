"""Communication Intelligence domain models.

These are plain structured values the engine accepts and returns. Nothing here
reaches RMS or performs I/O — the service layer does that.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any, Dict, List, Optional


@dataclass
class CommunicationRecipient:
    """WHO receives the message."""

    name: str = ""
    type: str = "UNKNOWN"  # MANAGER | REPORTEE | COLLEAGUE | CLIENT | TEAM | INDIVIDUAL | OTHER
    relationship: str = ""  # free-text hint (manager, external, peer…)


@dataclass
class CommunicationContext:
    """Everything the composer may *verifiably* use.

    `verified_context` carries SkillEdge-confirmed facts only (a resolved
    opportunity, a delivery record, travel facts…). The composer never
    invents facts; anything absent is simply not mentioned.
    """

    recipient: CommunicationRecipient = field(default_factory=CommunicationRecipient)
    channel: str = "MS_TEAMS_OR_VIBER"
    purpose: str = "GENERAL_PROFESSIONAL"
    user_message: str = ""
    my_message: str = ""
    related_entity_type: str = ""  # OPPORTUNITY | DELIVERY | TASK | TRAVEL | ...
    related_entity_id: str = ""
    verified_context: Dict[str, Any] = field(default_factory=dict)
    user_overrides: Dict[str, Any] = field(default_factory=dict)


@dataclass
class ValidationResult:
    passed: bool = True
    issues: List[str] = field(default_factory=list)


@dataclass
class FactItem:
    key: str
    value: Any
    provenance: str = "VERIFIED_SKILLSYNC_CONTEXT"  # EXPLICIT_USER_INPUT | VERIFIED_SKILLSYNC_CONTEXT | INFERRED_INTENT


@dataclass
class CommunicationPlan:
    """Structured Communication Plan produced by ContextSelector.
    
    Contains structured meaning only - NO finished message sentences or markdown prose.
    """
    purpose: str
    recipient_name: str = ""
    recipient_type: str = "UNKNOWN"
    recipient_relationship: str = ""

    user_message: str = ""
    my_message: str = ""

    situation_summary: str = ""

    selected_facts: List[FactItem] = field(default_factory=list)
    rejected_facts: List[str] = field(default_factory=list)
    sensitive_facts_removed: List[str] = field(default_factory=list)

    expected_outcome: str = ""
    requested_action: str = ""  # Conceptual action descriptor (NO hardcoded prose!)

    urgency: str = "NORMAL"  # NORMAL | HIGH | LOW
    tone: str = "professional"  # professional | firm | appreciative | collaborative | corrective

    time_references: List[str] = field(default_factory=list)
    provenance: Dict[str, str] = field(default_factory=dict)

    requires_communication: bool = True
    no_message_reason: Optional[str] = None

    @property
    def intent(self) -> str:
        return self.my_message or self.user_message or self.purpose

    @property
    def action_required(self) -> str:
        return self.requested_action


# Backwards compatibility alias
ContextSelectionPlan = CommunicationPlan


@dataclass
class GeneratedMessage:
    text: str = ""
    validation: ValidationResult = field(default_factory=ValidationResult)
    facts_used: List[str] = field(default_factory=list)
    purpose: str = ""
    tone: str = ""
    selected_facts: List[str] = field(default_factory=list)
    rejected_facts: List[str] = field(default_factory=list)
    generation_mode: str = "DETERMINISTIC_GENERATOR"
    requires_communication: bool = True
    no_message_reason: Optional[str] = None
    sensitive_facts_removed: List[str] = field(default_factory=list)
    # Diagnostic only (provider/model/fallback_used/attempts) — never shown to managers.
    provenance: Dict[str, Any] = field(default_factory=dict)
