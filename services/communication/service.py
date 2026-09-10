"""CommunicationService — the single seam the routes call.

Pipeline:  request → context → intent → composer → validator → GeneratedMessage.
Context gathering (route-side, using verified SkillEdge data) is deliberately
kept outside this service: this service only writes.
"""

from __future__ import annotations

from datetime import datetime, timezone
import os
import threading
import uuid

from domain.communication.models import (
    CommunicationContext,
    CommunicationRecipient,
    GeneratedMessage,
    ValidationResult,
)
from repositories.communication_store import CommunicationStore

from . import policy
from .composer import compose
from .intent import analyze
from .validator import truncate, validate


class CommunicationService:
    def __init__(self, store: CommunicationStore = None):
        self._store = store or CommunicationStore(
            os.path.join(os.getenv("SKILLEDGE_STATE_DIR", "."), "skilledge_communication.sqlite3")
        )
        self._lock = threading.Lock()

    # ── generate ────────────────────────────────────────────────────────────

    def generate(self, manager_email: str, request: dict, verified_context: dict = None) -> GeneratedMessage:
        """Build and return ONE final message. `verified_context` must contain
        only SkillEdge-confirmed facts (e.g. {"opportunity": {...}}); nothing
        else may be asserted."""
        req = request or {}
        recipient = CommunicationRecipient(
            name=str((req.get("recipient") or {}).get("name", "")),
            type=str((req.get("recipient") or {}).get("type", "")),
            relationship=str((req.get("recipient") or {}).get("relationship", "")),
        )
        context = CommunicationContext(
            recipient=recipient,
            channel=str(req.get("channel", "MS_TEAMS_OR_VIBER")),
            purpose=str(req.get("purpose", "")),
            user_message=str(req.get("userMessage", "")),
            my_message=str(req.get("myMessage", "")),
            related_entity_type=str(req.get("relatedEntityType", "")),
            related_entity_id=str(req.get("relatedEntityId", "")),
            verified_context=dict(verified_context or {}),
            user_overrides=dict(req.get("overrides") or {}),
        )
        intent = analyze(
            context.user_message,
            context.my_message,
            recipient_name=recipient.name,
            recipient_type=recipient.type,
            purpose_hint=context.purpose or context.user_overrides.get("purpose", ""),
        )
        text = compose(intent, context)
        validation = validate(text)
        if not validation.passed and len(text) > policy.MAX_LENGTH:
            text = truncate(text)
            validation = validate(text)
        facts = []
        if context.verified_context.get("opportunity"):
            opp = context.verified_context["opportunity"]
            for key in ("course_code", "course", "location", "country", "decision"):
                if opp.get(key):
                    facts.append(f"opportunity.{key}={opp[key]}")
        if intent.deadline_text:
            facts.append(f"deadline={intent.deadline_text}")
        return GeneratedMessage(
            text=text,
            validation=validation,
            facts_used=facts,
            purpose=intent.purpose,
            tone=intent.tone,
        )

    # ── history ─────────────────────────────────────────────────────────────

    def save(self, manager_email: str, record: dict) -> dict:
        with self._lock:
            entry = {
                "id": str(uuid.uuid4().hex[:12]),
                "manager_email": str(manager_email or "").strip().lower(),
                "recipient": str((record.get("recipient") or {}).get("name", "")),
                "recipient_type": str((record.get("recipient") or {}).get("type", "UNKNOWN")),
                "channel": str(record.get("channel", "MS_TEAMS_OR_VIBER")),
                "purpose": str(record.get("purpose", "GENERAL_PROFESSIONAL")),
                "related_entity_type": str(record.get("relatedEntityType", "")),
                "related_entity_id": str(record.get("relatedEntityId", "")),
                "message_text": str(record.get("message", "")),
                "status": str(record.get("status", "DRAFT")),
                "created_at": datetime.now(timezone.utc).isoformat(),
            }
            self._store.create(entry)
            return {"id": entry["id"], "status": entry["status"], "created_at": entry["created_at"]}

    def history(self, manager_email: str, limit: int = 50) -> list:
        return self._store.list(str(manager_email or "").strip().lower(), limit=int(limit) or 50)