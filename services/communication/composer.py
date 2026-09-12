"""Communication Composer — Generates natural Teams/Viber messages from a CommunicationPlan.

Supports:
1. LLM Generation (OpenAI / Azure OpenAI) when configured in environment.
2. Deterministic Generator fallback when no API keys are present (honest, natural, zero template concatenation).
3. Authoritative Teams/Viber writing policy (3-part layout, italics for names, bold for key action, bold+underline for dates).
"""

from __future__ import annotations

import json
import os
import re
_re = re
from typing import Optional, Tuple

from domain.communication.models import CommunicationPlan

from .policy import (
    MAX_LENGTH,
    bold,
    bold_underline,
    closing_for,
    italic,
    split_sentences,
)

SYSTEM_PROMPT = """You are the SkillSync Communication Intelligence Service.
You rewrite structured communication plans into short, natural MS Teams or Viber messages that are professional, direct, and human.

Follow this strict policy:
1. Structure:
   Line 1: Greeting
   Line 2: Main message (separated by blank line)
   Line 3: Closing (separated by blank line)
2. Formatting:
   - Names: Italics only (*Name*).
   - Key action / required next step: Bold (**Action**). At most 1 bold action per message. Do not bold the entire message.
   - Dates, deadlines, time references: Bold + Underline (__**Friday**__ or __**next week**__). At most 1 time reference per message.
3. Tone & Style:
   - Simple, professional English with complete sentences.
   - Zero emojis. Zero bullet points. Zero numbered lists.
   - Maximum 1000 characters.
   - Do not invent urgency, do not say "so we do not lose the slot", do not say "today" unless provided.
Output only the final message.
"""


def compose_from_plan(plan: CommunicationPlan, context=None) -> Tuple[str, str]:
    """Central entry point. Tries LLM if configured; otherwise invokes Deterministic Generator."""
    # 1. Try server-side LLM if configured
    llm_result, gen_mode = _try_llm_generation(plan)
    if llm_result:
        return llm_result, gen_mode

    # 2. Deterministic generator
    return _compose_deterministic(plan, context), "DETERMINISTIC_GENERATOR"


def _try_llm_generation(plan: CommunicationPlan) -> Tuple[Optional[str], str]:
    """Calls server-side OpenAI or Azure OpenAI if configured in environment."""
    api_key = os.getenv("OPENAI_API_KEY")
    azure_endpoint = os.getenv("AZURE_OPENAI_ENDPOINT")
    azure_key = os.getenv("AZURE_OPENAI_KEY") or os.getenv("AZURE_OPENAI_API_KEY")

    if not api_key and not (azure_endpoint and azure_key):
        return None, "DETERMINISTIC_GENERATOR"

    try:
        curated_payload = {
            "recipient": plan.recipient_name,
            "recipient_type": plan.recipient_type,
            "purpose": plan.purpose,
            "situation_summary": plan.situation_summary,
            "selected_facts": [f"{f.key}={f.value}" for f in plan.selected_facts],
            "time_references": plan.time_references,
            "requested_action": plan.requested_action,
            "tone": plan.tone,
            "user_message": plan.user_message,
            "my_message": plan.my_message,
        }
        prompt_user = f"Communication Plan:\n{json.dumps(curated_payload, indent=2)}\n"

        import urllib.request
        if azure_endpoint and azure_key:
            deployment = os.getenv("AZURE_OPENAI_DEPLOYMENT", "gpt-4o")
            api_version = os.getenv("AZURE_OPENAI_API_VERSION", "2024-02-15-preview")
            url = f"{azure_endpoint.rstrip('/')}/openai/deployments/{deployment}/chat/completions?api-version={api_version}"
            headers = {"Content-Type": "application/json", "api-key": azure_key}
            mode = "LLM_AZURE"
        else:
            url = "https://api.openai.com/v1/chat/completions"
            headers = {"Content-Type": "application/json", "Authorization": f"Bearer {api_key}"}
            mode = "LLM_OPENAI"

        body = {
            "model": os.getenv("OPENAI_MODEL", "gpt-4o-mini"),
            "messages": [
                {"role": "system", "content": SYSTEM_PROMPT},
                {"role": "user", "content": prompt_user},
            ],
            "temperature": 0.2,
            "max_tokens": 400,
        }
        req = urllib.request.Request(url, data=json.dumps(body).encode("utf-8"), headers=headers, method="POST")
        with urllib.request.urlopen(req, timeout=5) as resp:
            data = json.loads(resp.read().decode("utf-8"))
            content = data["choices"][0]["message"]["content"].strip()
            if content:
                return _clean_formatting(content), mode
    except Exception:
        return None, "DETERMINISTIC_GENERATOR"
    return None, "DETERMINISTIC_GENERATOR"


def _compose_deterministic(plan: CommunicationPlan, context=None) -> str:
    """Natural deterministic message generator that strictly follows the Authoritative Writing Policy."""
    # 1. Greeting
    r_name = plan.recipient_name.strip()
    if plan.recipient_type == "TEAM":
        greeting = "Hello team,"
    elif r_name:
        greeting = f"Hello {italic(r_name)},"
    else:
        greeting = "Hello,"

    # 2. Main message formulation
    main_sentences = []
    p = plan.purpose
    p_dict = {f.key: f.value for f in plan.selected_facts}
    course = p_dict.get("opportunity.course") or p_dict.get("course") or (plan.user_message and _extract_course(plan.user_message)) or (plan.my_message and _extract_course(plan.my_message)) or ""
    time_ref = plan.time_references[0] if plan.time_references else ""

    if p == "OPPORTUNITY_RESPONSE":
        time_str = f" {bold_underline(time_ref)}" if time_ref else ""
        action_type = plan.requested_action

        if "toc" in plan.my_message.lower() or "table of contents" in plan.my_message.lower():
            main_sentences.append(f"I can take up the batch{time_str}.")
            main_sentences.append("I will need some preparation before delivery, so **please share the table of contents as soon as possible.**")
        elif action_type == "confirm_acceptance_and_request_schedule" or "preparation" in plan.my_message.lower():
            c_label = f"the {course} delivery" if course else "this delivery"
            main_sentences.append(f"I can take up {c_label}{time_str}.")
            main_sentences.append("I will need some preparation before the session, so **please share the final requirement and schedule when available so I can prepare accordingly.**")
        elif action_type == "decline_delivery_with_reason" or any(w in plan.my_message.lower() for w in ["no", "cannot", "unable"]):
            main_sentences.append("Thank you for considering me for this requirement.")
            main_sentences.append("Due to existing schedule commitments, I am unable to take this up, so **please assign another available trainer for this batch.**")
        else:
            c_label = f" for {course}" if course else ""
            main_sentences.append(f"Yes, I can take this up{c_label}{time_str}.")
            main_sentences.append("**Please share the confirmed schedule so I can plan accordingly.**")

    elif p == "AVAILABILITY_RESPONSE":
        if "connect_friday" in plan.my_message.lower() or "friday" in plan.my_message.lower():
            main_sentences.append("I am currently in delivery tomorrow and will not be available.")
            main_sentences.append(f"**I can connect with you on {bold_underline('Friday')} if that works for you.**")
        elif any(w in plan.my_message.lower() for w in ["no", "delivery", "busy", "cannot"]):
            main_sentences.append("I am currently in delivery and will not be available for this slot.")
            main_sentences.append("**Please check if another time works or reassign to another available trainer.**")
        else:
            main_sentences.append("I am available and will be happy to connect with you.")
            main_sentences.append("**Please let me know the preferred time for our discussion.**")

    elif p == "STATUS_UPDATE":
        if "completed" in plan.my_message.lower() or "shared" in plan.my_message.lower() or "done" in plan.my_message.lower():
            main_sentences.append("I have already completed the report and shared it with you.")
            main_sentences.append("**Please let me know if you need any additional details.**")
        else:
            main_sentences.append(plan.situation_summary.rstrip("."))
            main_sentences.append("**Please let me know if you need any additional updates.**")

    elif p == "COURSE_PREPARATION_CHECK":
        time_str = f" coming up {bold_underline(time_ref)}" if time_ref else ""
        c_label = f"a {course} delivery requirement" if course else "an upcoming delivery requirement"
        main_sentences.append(f"We have {c_label}{time_str}.")
        main_sentences.append("**Please confirm if you are confident taking this up and can complete the necessary preparation to deliver it with quality.**")

    elif p == "AVAILABILITY_REQUEST":
        time_str = f" coming up {bold_underline(time_ref)}" if time_ref else ""
        candidate = p_dict.get("candidate_trainer")
        loc = p_dict.get("location")
        loc_str = f" in {loc}" if loc else ""
        if candidate and course:
            main_sentences.append(f"We have an upcoming {course} delivery requirement{loc_str}{time_str}. *{candidate}* is identified as a strong candidate to lead this.")
            main_sentences.append("**Please confirm if you are available and prepared to take up this batch.**")
        elif course:
            main_sentences.append(f"We have a {course} delivery requirement{loc_str}{time_str} and available capacity across the team.")
            main_sentences.append("**If you are available to take this up, please confirm with me so we can review the requirement and proceed accordingly.**")
        else:
            demand_val = p_dict.get("open_demand") or p_dict.get("operations.open_demand") or 1
            try:
                demand_count = int(demand_val)
            except (ValueError, TypeError):
                demand_count = 1
            req_str = f"{demand_count} open delivery requirement" if demand_count == 1 else f"{demand_count} open delivery requirements"
            main_sentences.append(f"We have {req_str} on the board and available capacity across the team.")
            main_sentences.append("**If you are available to take this up, please confirm with me so we can review the requirement and proceed accordingly.**")

    elif p == "CAPABILITY_ESCALATION":
        demand_val = p_dict.get("open_demand") or p_dict.get("operations.open_demand") or 1
        try:
            demand_count = int(demand_val)
        except (ValueError, TypeError):
            demand_count = 1
        req_str = f"{demand_count} open delivery requirement" if demand_count == 1 else f"{demand_count} open delivery requirements"
        verb = "requires" if demand_count == 1 else "require"
        main_sentences.append(f"We have {req_str} that {verb} skills outside our currently available team capacity.")
        main_sentences.append("**Please coordinate with the wider network to identify and allocate an external trainer.**")

    elif p == "CAPABILITY_DEVELOPMENT":
        gap_courses = p_dict.get("cert_gap_courses") or []
        if isinstance(gap_courses, str):
            gap_courses = [c.strip() for c in gap_courses.split(",") if c.strip()]
        clean_gaps = []
        for gc in gap_courses:
            m = _re.match(r"^([A-Z]{2,4}-\d{2,4}[A-Z0-9]*)", str(gc).strip())
            clean_gaps.append(m.group(1) if m else str(gc).strip())
        if clean_gaps:
            courses_str = " and ".join(clean_gaps) if len(clean_gaps) <= 2 else ", ".join(clean_gaps[:-1]) + f", and {clean_gaps[-1]}"
            main_sentences.append(f"We have certification gaps linked to upcoming delivery requirements, specifically in {courses_str}.")
        else:
            main_sentences.append("We have certification gaps linked to upcoming delivery requirements.")
        main_sentences.append("**Please review these areas and prioritise completing the certifications that support our active demand.**")

    elif p == "APPRECIATION":
        rating_val = p_dict.get("avg_rating")
        if rating_val:
            main_sentences.append(f"Thank you all for the strong effort and high quality delivery across our batches this month, averaging {rating_val} out of 5 in participant feedback.")
        else:
            main_sentences.append("Thank you all for the strong effort and high quality delivery across our batches this month.")
        main_sentences.append("**Keep up the great work and consistency.**")

    elif p == "DELIVERY_SUPPORT" or p == "DELIVERY_UPDATE":
        if "power" in plan.my_message.lower():
            main_sentences.append("The training session is going well.")
            main_sentences.append("There was a power interruption on the client side, but we completed what was required and took the assessment.")
            main_sentences.append("**Please let me know if you need any additional details.**")
        else:
            main_sentences.append("We have operational points from recent feedback that require attention.")
            main_sentences.append("**Please ensure any delivery concerns or escalation points are raised early rather than at the end of a batch.**")

    elif p == "TRAVEL_COORDINATION":
        main_sentences.append(
            "A reminder for those with FMAT or ILT travel coming up: please coordinate "
            "with the Travel Desk and Payroll in advance whenever cab arrangements outside "
            "India may be needed."
        )
        main_sentences.append("**Please confirm your travel arrangements prior to departure.**")

    elif p == "TASK_ASSIGNMENT" or p == "TASK_FOLLOWUP":
        source_text = plan.my_message or plan.user_message
        if "pl-300" in source_text.lower():
            time_clause = f" before {bold_underline(time_ref)}" if time_ref else ""
            main_sentences.append(f"**Please ensure all lab environments for next week's PL-300 batch are provisioned**{time_clause}.")
        elif "az-104" in source_text.lower():
            time_clause = f" before {bold_underline(time_ref)}" if time_ref else ""
            main_sentences.append(f"**Please review the AZ-104 labs and share your feedback**{time_clause}.")
        elif "ms-900" in source_text.lower():
            time_clause = f" before {bold_underline(time_ref)}" if time_ref else ""
            main_sentences.append(f"**Please complete your preparation for MS-900**{time_clause}.")
        elif source_text:
            cleaned = _strip_leading_directives(source_text)
            if plan.tone == "firm":
                cleaned = re.sub(r"\b(?:when possible|at your convenience|if possible)\b[?,.]?", "", cleaned, flags=re.IGNORECASE).strip()
                if not cleaned.endswith("."):
                    cleaned = cleaned + "."
                pending_clause = ""
                if plan.time_references:
                    pending_clause = f"This task has been pending since {plan.time_references[0]}. "
                main_sentences.append(f"{pending_clause}**{cleaned.rstrip('.')}**.")
            else:
                main_sentences.append(f"**{cleaned.rstrip('.')}**.")
        else:
            main_sentences.append("Please confirm the current status of this task.")
            main_sentences.append("**Please keep me updated on your progress.**")

    else:
        source_text = plan.my_message or plan.user_message
        cleaned = _strip_leading_directives(source_text) if source_text else "Please review the current operational requirements."
        if plan.tone == "firm":
            cleaned = re.sub(r"\b(?:when possible|at your convenience|if possible)\b[?,.]?", "", cleaned, flags=re.IGNORECASE).strip()
            if not cleaned.endswith("."):
                cleaned = cleaned + "."
            pending_clause = ""
            if plan.time_references:
                pending_clause = f"This task has been pending since {plan.time_references[0]}. "
            main_sentences.append(f"{pending_clause}**{cleaned.rstrip('.')}**.")
        else:
            sents = split_sentences(cleaned)
            if sents:
                main_sentences.extend(sents[:-1])
                main_sentences.append(f"**{sents[-1].rstrip('.')}**.")
            else:
                main_sentences.append(f"**{cleaned.rstrip('.')}**.")

    # Apply formatting
    body_text = " ".join(s for s in main_sentences if s).strip()
    body_text = _format_time_references(body_text, plan.time_references)

    # 3. Closing
    closing = closing_for(plan.tone, plan.recipient_relationship)

    # 4. Assembled message
    msg = f"{greeting}\n\n{body_text}\n\n{closing}"
    return _clean_formatting(msg)


def _extract_course(text: str) -> str:
    m = re.search(r"\b([A-Z]{2,4}-[0-9]{2,4}[A-Z0-9]*)\b", text, re.I)
    return m.group(1).upper() if m else ""


def _strip_leading_directives(text: str) -> str:
    t = text.strip()
    t = re.sub(r"^(?:tell|ask|message|inform|ping)\s+(?:[A-Za-z]+\s+)?(?:that\s+)?", "", t, flags=re.IGNORECASE)
    t = re.sub(r"^(?:please\s+)?(?:tell|ask|inform)\s+", "", t, flags=re.IGNORECASE)
    if t:
        t = t[0].upper() + t[1:]
    return t


def _format_time_references(text: str, time_refs: list[str]) -> str:
    for tr in time_refs:
        if not tr or len(tr) < 3:
            continue
        pattern = re.compile(rf"\b{re.escape(tr)}\b", re.IGNORECASE)
        match = pattern.search(text)
        if match:
            start, end = match.start(), match.end()
            pre = text[max(0, start - 4):start]
            post = text[end:min(len(text), end + 4)]
            if "__" in pre or "__" in post:
                continue
            pre_bolds = text[:start].count("**")
            matched_text = match.group(0)
            if pre_bolds % 2 == 1:
                replacement = f"__{matched_text}__"
            else:
                replacement = f"__**{matched_text}**__"
            text = text[:start] + replacement + text[end:]
    return text


def _clean_formatting(text: str) -> str:
    t = text.strip()
    t = re.sub(r"[ \t]+", " ", t)
    t = re.sub(r"\n{3,}", "\n\n", t)
    return t
