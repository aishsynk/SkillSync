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
class GeneratedMessage:
    text: str = ""
    validation: ValidationResult = field(default_factory=ValidationResult)
    facts_used: List[str] = field(default_factory=list)
    purpose: str = ""
    tone: str = ""