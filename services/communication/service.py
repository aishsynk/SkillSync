"""CommunicationService — Central Intelligence Service for Communications.

Implements the authoritative processing pipeline:
  USER MESSAGE + MY MESSAGE + CURRENT SKILLSYNC CONTEXT
        ↓
  INTENT UNDERSTANDING
        ↓
  RECIPIENT / RELATIONSHIP UNDERSTANDING
        ↓
  SITUATION EVALUATION
        ↓
  IMPORTANT FACT SELECTION
        ↓
  IRRELEVANT FACT REJECTION
        ↓
  SENSITIVE FACT REJECTION
        ↓
  ACTION / OUTCOME DETERMINATION
        ↓
  MESSAGE PLANNING
        ↓
  NATURAL MESSAGE GENERATION (LLM or Intelligent Native Engine)
        ↓
  POLICY / FORMATTING VALIDATION
        ↓
  FINAL MESSAGE
"""

from __future__ import annotations

from datetime import datetime, timezone
import os
import threading
import uuid
from typing import Any, Dict, List, Optional

from domain.communication.models import (
    CommunicationContext,
    CommunicationRecipient,
    GeneratedMessage,
    ValidationResult,
)
from repositories.communication_store import CommunicationStore

from . import policy
from .briefs import BRIEF_PURPOSES, brief_issues, compose_brief
from .composer import MORNING_TEAM_GREETING, compose_from_plan_detailed, compose_morning_greeting, morning_greeting_issues
from .context_selector import ContextSelector, NO_MEANINGFUL_MESSAGE
from .intent import analyze
from .validator import truncate, validate


class CommunicationService:
    def __init__(self, store: CommunicationStore = None):
        self._store = store or CommunicationStore(
            os.path.join(os.getenv("SKILLEDGE_STATE_DIR", "."), "skilledge_communication.sqlite3")
        )
        self._lock = threading.Lock()

    def generate(
        self,
        manager_email: str,
        request: dict,
        verified_context: dict = None,
    ) -> GeneratedMessage:
        """Central communication intelligence entry point."""
        req = request or {}
        purpose_id = str(req.get("purpose", "")).upper()
        if purpose_id == MORNING_TEAM_GREETING:
            return self._morning_greeting(req)
        if purpose_id in BRIEF_PURPOSES:
            return self._brief(purpose_id, req)
        recipient_map = req.get("recipient") or {}
        recipient = CommunicationRecipient(
            name=str(recipient_map.get("name") or "").strip(),
            type=str(recipient_map.get("type") or "").strip(),
            relationship=str(recipient_map.get("relationship") or "").strip(),
        )
        context = CommunicationContext(
            recipient=recipient,
            channel=str(req.get("channel", "MS_TEAMS_OR_VIBER")),
            purpose=str(req.get("purpose", "")),
            user_message=str(req.get("userMessage") or req.get("user_message") or ""),
            my_message=str(req.get("myMessage") or req.get("my_message") or ""),
            related_entity_type=str(req.get("relatedEntityType") or req.get("related_entity_type") or ""),
            related_entity_id=str(req.get("relatedEntityId") or req.get("related_entity_id") or ""),
            verified_context=dict(verified_context or {}),
            user_overrides=dict(req.get("overrides") or {}),
        )

        # Step 1: Intent Analysis
        intent = analyze(
            context.user_message,
            context.my_message,
            recipient_name=recipient.name,
            recipient_type=recipient.type,
            purpose_hint=context.purpose or context.user_overrides.get("purpose", ""),
        )

        # Step 2 & 3: Context Selection & Situation Evaluation
        plan = ContextSelector.evaluate_and_select(
            user_message=context.user_message,
            my_message=context.my_message,
            recipient_name=recipient.name,
            recipient_type=recipient.type,
            recipient_relationship=recipient.relationship,
            purpose_hint=context.purpose,
            verified_context=context.verified_context,
            inferred_intent=intent,
        )

        # Step 4: Check for NO_MEANINGFUL_MESSAGE
        if not plan.requires_communication:
            return GeneratedMessage(
                text=NO_MEANINGFUL_MESSAGE,
                validation=ValidationResult(passed=True, issues=[]),
                facts_used=[],
                purpose=plan.purpose,
                tone=plan.tone,
                selected_facts=[],
                rejected_facts=plan.rejected_facts,
                generation_mode="DETERMINISTIC_GENERATOR",
                requires_communication=False,
                no_message_reason=plan.no_message_reason,
                sensitive_facts_removed=plan.sensitive_facts_removed,
            )

        # Step 5: Natural Message Generation
        text, provenance = compose_from_plan_detailed(plan, context)
        gen_mode = provenance.generation_mode

        # Step 6: Validation
        validation = validate(text, plan=plan)
        if not validation.passed and len(text) > policy.MAX_LENGTH:
            text = truncate(text)
            validation = validate(text, plan=plan)

        # Selected facts description
        facts_used = [f"{f.key}={f.value}" for f in plan.selected_facts]

        return GeneratedMessage(
            text=text,
            validation=validation,
            facts_used=facts_used,
            purpose=plan.purpose,
            tone=plan.tone,
            selected_facts=facts_used,
            rejected_facts=plan.rejected_facts,
            generation_mode=gen_mode,
            requires_communication=True,
            no_message_reason=None,
            sensitive_facts_removed=plan.sensitive_facts_removed,
            provenance=provenance.as_dict(),
        )

    def _morning_greeting(self, req: dict) -> GeneratedMessage:
        """Weekday team greeting. The weekday is the manager's local one, sent by
        the client; Saturday and Sunday produce no message at all."""
        weekday = str(req.get("localWeekday") or req.get("local_weekday") or "").upper()
        recent = req.get("recentGreetings") or req.get("recent_greetings") or []
        if not isinstance(recent, list):
            recent = []
        try:
            variation = int(req.get("variation") or 0)
        except (TypeError, ValueError):
            variation = 0
        text, provenance = compose_morning_greeting(weekday, recent, variation)
        mode = provenance.generation_mode
        if not text:
            return GeneratedMessage(
                text="", validation=ValidationResult(passed=True, issues=[]), facts_used=[],
                purpose=MORNING_TEAM_GREETING, tone="warm", selected_facts=[], rejected_facts=[],
                generation_mode="SUPPRESSED_WEEKEND", requires_communication=False,
                no_message_reason="No morning greeting at the weekend.", sensitive_facts_removed=[],
                provenance=provenance.as_dict(),
            )
        # The deterministic bank may legitimately reuse an opening once every
        # option is exhausted, so the recent-repeat rule is not re-applied to it.
        check_recent = [] if provenance.fallback_used else [str(r) for r in recent][:10]
        issues = morning_greeting_issues(text, check_recent, weekday)
        facts = [f"local_weekday={weekday}"]
        return GeneratedMessage(
            text=text, validation=ValidationResult(passed=not issues, issues=issues), facts_used=facts,
            purpose=MORNING_TEAM_GREETING, tone="warm", selected_facts=facts, rejected_facts=[],
            generation_mode=mode, requires_communication=True, no_message_reason=None,
            sensitive_facts_removed=[], provenance=provenance.as_dict(),
        )

    def _brief(self, purpose: str, req: dict) -> GeneratedMessage:
        """Weekly/monthly manager brief. Facts come from the caller (report
        builders); this only selects, writes and validates. The caller's
        deterministic prose is the fallback, so a message is always returned."""
        raw_facts = req.get("verifiedFacts") or req.get("verified_facts") or {}
        if not isinstance(raw_facts, dict):
            raw_facts = {}
        timeframe = str(req.get("timeframe") or "current").lower()
        recipient = req.get("recipient") or {}
        recipient_name = str(recipient.get("name") or "")
        fallback = str(req.get("fallbackText") or req.get("fallback_text") or "")
        recent = req.get("recentMessages") or req.get("recent_messages") or []
        if not isinstance(recent, list):
            recent = []
        text, provenance, facts = compose_brief(
            purpose, raw_facts, timeframe=timeframe, recipient_name=recipient_name,
            manager_instruction=str(req.get("myMessage") or req.get("my_message") or ""),
            recent=[str(r) for r in recent], deterministic_fallback=fallback,
        )
        fact_labels = [f"{k}={v}" for k, v in facts]
        # Validated whatever wrote it — model or deterministic composer.
        issues = brief_issues(text, facts, purpose, recipient_name)
        return GeneratedMessage(
            text=text,
            validation=ValidationResult(passed=not issues, issues=issues),
            facts_used=fact_labels,
            purpose=purpose,
            tone="warm",
            selected_facts=fact_labels,
            rejected_facts=[],
            generation_mode=provenance.generation_mode,
            requires_communication=bool(text.strip()),
            no_message_reason=None if text.strip() else "No verified facts to report for this period.",
            sensitive_facts_removed=[],
            provenance=provenance.as_dict(),
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
                "related_entity_type": str(record.get("relatedEntityType") or record.get("related_entity_type") or ""),
                "related_entity_id": str(record.get("relatedEntityId") or record.get("related_entity_id") or ""),
                "message_text": str(record.get("message", "")),
                "status": str(record.get("status", "DRAFT")),
                "created_at": datetime.now(timezone.utc).isoformat(),
            }
            self._store.create(entry)
            return {"id": entry["id"], "status": entry["status"], "created_at": entry["created_at"]}

    def history(self, manager_email: str, limit: int = 50) -> list:
        return self._store.list(str(manager_email or "").strip().lower(), limit=int(limit) or 50)