"""ContextSelector — Evaluates situation and selects only facts relevant to intent and purpose.

Strictly adheres to:
1. Privacy Filter: screens sensitive facts (salary, margin, billing_rate, etc.).
2. Clean Separation of Concerns: outputs structured CommunicationPlan only (NO finished prose or markdown).
3. Semantic Priority: there is no external "[User Message]" input for manager
   communication. Aishwar (the manager) is always the sender. `my_message` is
   his own optional instruction/position — it may shape tone, emphasis, or
   which purpose to lead with, but it is never primary business context, and
   it can never override `verified_context` (Verified Context is the
   SUPPORTING EVIDENCE, i.e. authoritative facts). `user_message` remains
   accepted on the wire for API-shape compatibility (a future, separate
   "draft a reply to an inbound message" feature may reintroduce a real use
   for it under its own contract) but is no longer treated as primary intent
   here — no live caller in this repository populates it as of the Phase 2
   architecture restructuring (2026-09), confirmed by a repo-wide search.
4. Auto-generation evaluates the WHOLE operational situation, not simple first-match KPI branching.
5. Suppresses communication noise with NO_MEANINGFUL_MESSAGE when operations are steady or load is self-managing.
"""

from __future__ import annotations

import re
from typing import Any, Dict, List, Optional

from domain.communication.models import CommunicationPlan, FactItem

NO_MEANINGFUL_MESSAGE = "NO_MEANINGFUL_MESSAGE"

# Sensitive keys screened from any message context
SENSITIVE_KEYS = {
    "salary", "ctc", "compensation", "billing_rate", "client_rate", "margin", "cost",
    "profit", "billing_code", "client_billing_key", "retention_flag", "attrition_risk",
    "internal_notes", "performance_warning", "pip", "disciplinary", "confidential",
    "ssn", "passport", "bank_account", "pan", "aadhar", "national_id"
}


class ContextSelector:
    """Evaluates context and selects only facts relevant to the purpose and intent."""

    @classmethod
    def evaluate_and_select(
        cls,
        user_message: str,
        my_message: str,
        recipient_name: str,
        recipient_type: str,
        recipient_relationship: str,
        purpose_hint: str,
        verified_context: Dict[str, Any],
        inferred_intent: Any = None,
    ) -> CommunicationPlan:
        um = str(user_message or "").strip()
        mm = str(my_message or "").strip()
        has_manual_input = bool(um or mm)

        # 1. Privacy filter: screen sensitive keys first
        clean_context: Dict[str, Any] = {}
        sensitive_removed: List[str] = []
        for k, v in (verified_context or {}).items():
            if k.lower() in SENSITIVE_KEYS:
                sensitive_removed.append(k)
            elif isinstance(v, dict):
                sub_clean = {}
                for sk, sv in v.items():
                    if sk.lower() in SENSITIVE_KEYS:
                        sensitive_removed.append(f"{k}.{sk}")
                    else:
                        sub_clean[sk] = sv
                clean_context[k] = sub_clean
            else:
                clean_context[k] = v

        # Resolve recipient
        r_type = (recipient_type or getattr(inferred_intent, "recipient_class", "UNKNOWN") or "UNKNOWN").upper()
        if r_type not in ("TEAM", "INDIVIDUAL", "MANAGER", "REPORTEE", "COLLEAGUE", "CLIENT"):
            r_type = "TEAM" if "team" in f"{um} {mm}".lower() or "everyone" in f"{um} {mm}".lower() else "INDIVIDUAL"
        r_name = recipient_name or getattr(inferred_intent, "recipient_name", "")

        # Extract time references
        time_refs = list(getattr(inferred_intent, "time_refs", []))
        for tr in ["next week", "this week", "today", "tomorrow", "friday", "monday"]:
            if tr in f"{um} {mm}".lower() and tr.capitalize() not in time_refs and tr not in time_refs:
                time_refs.append(tr)

        selected_facts: List[FactItem] = []
        rejected_facts: List[str] = []
        tone = getattr(inferred_intent, "tone", "professional")
        urgency = getattr(inferred_intent, "urgency", "NORMAL")
        purpose = purpose_hint or getattr(inferred_intent, "purpose", "GENERAL_PROFESSIONAL")
        situation_summary = ""
        expected_outcome = ""
        requested_action = ""
        requires_comm = True
        no_msg_reason = None

        # ── FLOW A: MANAGER-INSTRUCTION-LED (my_message supplied) ──────────────
        # `um` is intentionally not branched on for intent/purpose here — see
        # the module docstring. It still contributes to recipient-type/time-
        # reference extraction above (benign parsing, not intent).
        if has_manual_input:
            opp = clean_context.get("opportunity") if isinstance(clean_context.get("opportunity"), dict) else {}
            course = getattr(inferred_intent, "course", "") or clean_context.get("course_code") or clean_context.get("course") or opp.get("course_code") or opp.get("course") or ""
            if opp.get("course_code") or opp.get("course"):
                c_val = opp.get("course_code") or opp.get("course")
                selected_facts.append(FactItem("opportunity.course", c_val, "VERIFIED_SKILLSYNC_CONTEXT"))
            elif course:
                selected_facts.append(FactItem("course", course, "EXPLICIT_USER_INPUT" if course in f"{um} {mm}" else "VERIFIED_SKILLSYNC_CONTEXT"))

            if purpose == "COURSE_PREPARATION_CHECK":
                situation_summary = f"Inquiring about trainer readiness and confidence for {course or 'upcoming curriculum'}."
                requested_action = "check_readiness_and_preparation"
                expected_outcome = "Trainer confirms confidence and readiness timeline."
                # If recipient has capability context, select it
                if "fabric" in mm.lower() or "fabric" in clean_context:
                    selected_facts.append(FactItem("capability_background", "Fabric", "VERIFIED_SKILLSYNC_CONTEXT"))

            elif purpose == "AVAILABILITY_REQUEST":
                situation_summary = f"Checking availability across team for {course or 'open delivery requirement'}."
                requested_action = "request_availability_confirmation"
                expected_outcome = "Available trainer confirms willingness to take up delivery."
                if "open_demand" in clean_context:
                    selected_facts.append(FactItem("open_demand", clean_context["open_demand"], "VERIFIED_SKILLSYNC_CONTEXT"))
                if "bench" in clean_context:
                    selected_facts.append(FactItem("bench", clean_context["bench"], "VERIFIED_SKILLSYNC_CONTEXT"))

            elif purpose == "TASK_ASSIGNMENT":
                situation_summary = "Manager directing task provisioning or completion."
                requested_action = "ensure_task_provisioning"
                expected_outcome = "Recipient ensures required environments and tasks are completed."

            elif purpose == "APPRECIATION":
                situation_summary = "Manager expressing recognition for team delivery performance."
                requested_action = "share_appreciation"
                expected_outcome = "Team feels recognized and motivated."
                avg_r = clean_context.get("avg_rating") or clean_context.get("average_rating")
                if avg_r is not None:
                    try:
                        r_float = float(avg_r)
                        if r_float >= 4.0:
                            selected_facts.append(FactItem("avg_rating", r_float, "VERIFIED_SKILLSYNC_CONTEXT"))
                    except (ValueError, TypeError):
                        pass

            elif purpose == "DELIVERY_UPDATE":
                situation_summary = "Providing update on active training session and status."
                requested_action = "share_delivery_update"
                expected_outcome = "Recipient is briefed on training progress."

            elif purpose == "TRAVEL_COORDINATION":
                situation_summary = "Coordinating travel logistics and desk arrangements."
                requested_action = "coordinate_travel_arrangements"
                expected_outcome = "Travelers confirm arrangements in advance."

            else:
                situation_summary = f"Manager communication: {mm}"
                requested_action = "communicate_intent"
                expected_outcome = "Recipient acts on communication."

            # Reject unneeded general metrics in manual input mode
            for k in ["total_pax", "total_participants", "total_batches", "avg_rating", "total_gaps", "delivering", "headcount"]:
                if k in clean_context and not any(f.key == k for f in selected_facts):
                    rejected_facts.append(k)

        # ── FLOW B: AUTO-GENERATION MODE (SITUATION EVALUATION ACROSS ALL DATA) ─
        else:
            open_demand = int(clean_context.get("open_demand") or clean_context.get("open_batches_total") or 0)
            coverable = int(clean_context.get("coverable_open") or clean_context.get("open_batches_coverable") or 0)
            bench = int(clean_context.get("bench") or clean_context.get("bench_count") or 0)
            at_risk = int(clean_context.get("at_risk") or clean_context.get("negative_feedback_count") or 0)
            gap_demand_count = int(clean_context.get("gap_demand_count") or 0)
            deliv = int(clean_context.get("delivering") or 0)

            # Scenario 1: Open demand + matching capacity
            if open_demand > 0 and (bench > 0 or coverable > 0):
                purpose = "AVAILABILITY_REQUEST"
                course = clean_context.get("course") or clean_context.get("open_course") or clean_context.get("opportunity_course")
                candidate = clean_context.get("candidate_trainer") or clean_context.get("candidate")
                loc = clean_context.get("location") or clean_context.get("batch_location")

                situation_summary = f"There {'is' if open_demand == 1 else 'are'} {open_demand} open delivery requirement{'s' if open_demand != 1 else ''} and available trainer capacity."
                requested_action = "request_availability_confirmation"
                expected_outcome = "Available trainer confirms willingness to take open delivery."
                selected_facts.append(FactItem("open_demand", open_demand, "VERIFIED_SKILLSYNC_CONTEXT"))
                if bench > 0:
                    selected_facts.append(FactItem("bench", bench, "VERIFIED_SKILLSYNC_CONTEXT"))
                if coverable > 0:
                    selected_facts.append(FactItem("coverable_open", coverable, "VERIFIED_SKILLSYNC_CONTEXT"))
                if course:
                    selected_facts.append(FactItem("course", course, "VERIFIED_SKILLSYNC_CONTEXT"))
                if candidate:
                    selected_facts.append(FactItem("candidate_trainer", candidate, "VERIFIED_SKILLSYNC_CONTEXT"))
                if loc:
                    selected_facts.append(FactItem("location", loc, "VERIFIED_SKILLSYNC_CONTEXT"))
                for k in ["total_pax", "total_participants", "total_batches", "total_gaps", "avg_rating", "headcount"]:
                    if k in clean_context and not any(f.key == k for f in selected_facts):
                        rejected_facts.append(k)

            # Scenario 2: Open demand but no relevant capability in team
            elif open_demand > 0 and coverable == 0 and bench == 0:
                purpose = "CAPABILITY_ESCALATION"
                situation_summary = f"There {'is' if open_demand == 1 else 'are'} {open_demand} open requirement{'s' if open_demand != 1 else ''} requiring skills not currently verified in available team capacity."
                requested_action = "escalate_external_coverage"
                expected_outcome = "External staffing or cross-team support initiated."
                selected_facts.append(FactItem("open_demand", open_demand, "VERIFIED_SKILLSYNC_CONTEXT"))
                selected_facts.append(FactItem("coverable_open", 0, "VERIFIED_SKILLSYNC_CONTEXT"))
                for k in ["total_pax", "total_batches", "avg_rating"]:
                    if k in clean_context:
                        rejected_facts.append(k)

            # Scenario 3: Delivery quality risk
            elif at_risk > 0:
                purpose = "DELIVERY_SUPPORT"
                situation_summary = "Delivery feedback indicators show operational points requiring attention."
                requested_action = "review_delivery_feedback"
                expected_outcome = "Trainers raise delivery risks early for prompt resolution."
                selected_facts.append(FactItem("at_risk", at_risk, "VERIFIED_SKILLSYNC_CONTEXT"))
                for k in ["total_pax", "open_demand", "total_gaps", "bench", "total_batches"]:
                    if k in clean_context:
                        rejected_facts.append(k)

            # Scenario 4: Capability gap threatening verified upcoming demand
            elif gap_demand_count > 0:
                purpose = "CAPABILITY_DEVELOPMENT"
                situation_summary = f"{gap_demand_count} open demand requirement{'s' if gap_demand_count != 1 else ''} require certification coverage."
                requested_action = "schedule_priority_certifications"
                expected_outcome = "Trainers prioritize exams tied to verified upcoming demand."
                selected_facts.append(FactItem("gap_demand_count", gap_demand_count, "VERIFIED_SKILLSYNC_CONTEXT"))
                if "cert_gap_courses" in clean_context:
                    selected_facts.append(FactItem("cert_gap_courses", clean_context["cert_gap_courses"], "VERIFIED_SKILLSYNC_CONTEXT"))
                for k in ["total_pax", "open_demand", "bench", "avg_rating"]:
                    if k in clean_context:
                        rejected_facts.append(k)

            # Scenario 5: High delivery load with steady execution (no intervention required)
            elif deliv > 3 and open_demand == 0 and at_risk == 0:
                requires_comm = False
                no_msg_reason = "Delivery load is high and operations are steady; no operational intervention or communication required."
                situation_summary = "High steady delivery; suppressing redundant broadcast."
                expected_outcome = "No message broadcast."

            # Scenario 6: Healthy baseline operations
            else:
                requires_comm = False
                no_msg_reason = "All operations and deliveries are steady with no unstaffed batches, critical blockers, or immediate actions required."
                situation_summary = "Operational baseline is steady; suppressing unnecessary broadcast noise."
                expected_outcome = "No message broadcast."

        return CommunicationPlan(
            purpose=purpose,
            recipient_name=r_name,
            recipient_type=r_type,
            recipient_relationship=recipient_relationship,
            user_message=um,
            my_message=mm,
            situation_summary=situation_summary,
            selected_facts=selected_facts,
            rejected_facts=rejected_facts,
            sensitive_facts_removed=sensitive_removed,
            expected_outcome=expected_outcome,
            requested_action=requested_action,
            urgency=urgency,
            tone=tone,
            time_references=time_refs,
            provenance={f.key: f.provenance for f in selected_facts},
            requires_communication=requires_comm,
            no_message_reason=no_msg_reason,
        )
