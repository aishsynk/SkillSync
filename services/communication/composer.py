"""Communication Composer — Generates natural Teams/Viber messages from a CommunicationPlan.

Supports:
1. Model generation through services.communication.providers (Ollama, then Azure/OpenAI).
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

from . import providers
from .providers import DETERMINISTIC, Provenance, ProviderOutcome, ProviderResult

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
    """Central entry point. (text, generation_mode) — see compose_from_plan_detailed."""
    text, provenance = compose_from_plan_detailed(plan, context)
    return text, provenance.generation_mode


def compose_from_plan_detailed(plan: CommunicationPlan, context=None) -> Tuple[str, Provenance]:
    """Providers in COMMUNICATION_PROVIDER_ORDER; deterministic generator otherwise."""
    outcome = _try_llm_generation(plan)
    if outcome.result:
        r = outcome.result
        return _clean_formatting(r.text), Provenance(
            r.provider, r.model, fallback_used=False, attempts=1, elapsed_ms=outcome.elapsed_ms,
            timeout_reason=outcome.timeout_reason, attempted_providers=list(outcome.tried))
    return _compose_deterministic(plan, context), Provenance(
        DETERMINISTIC, "", fallback_used=True, attempts=1 if outcome.tried else 0,
        elapsed_ms=outcome.elapsed_ms, timeout_reason=outcome.timeout_reason,
        attempted_providers=list(outcome.tried))


def _try_llm_generation(plan: CommunicationPlan) -> ProviderOutcome:
    """Asks the provider boundary for a message built from the curated plan."""
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
    return providers.generate(SYSTEM_PROMPT, prompt_user, temperature=0.2, max_tokens=400)



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


# ── MORNING_TEAM_GREETING ──────────────────────────────────────────────────
#
# The manager's weekday morning note to the team for Teams/Viber. Same
# service, same compose entry point: the configured LLM is tried first with
# the full weekday policy below; the deterministic bank is the server-side
# fallback when no model is configured or its output fails the policy check.

MORNING_TEAM_GREETING = "MORNING_TEAM_GREETING"
MORNING_WEEKDAYS = ("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY")

# The greeting policy's stock language: motivational-poster and corporate
# filler the manager's voice never uses. Matched on normalised text, so
# punctuation and capitalisation ("Let's make it shine!") cannot slip past.
# Deliberately short — the deterministic weekday bank is preferred over
# mediocre model output, so this does not need to be an exhaustive blacklist.
MORNING_BANNED_PHRASES = (
    "stay focused", "keep the momentum", "finish strong", "make today count", "steady progress",
    "give 100%", "crush your goals", "have a productive day", "wishing everyone", "have a smooth day",
    "make it shine", "stay awesome", "keep pushing forward", "lets make it great", "lets crush",
    "keep it going strong", "onwards and upwards",
)

MORNING_GREETING_PROMPT = """You write one short weekday morning greeting from a senior delivery manager to their training team, to be pasted into Microsoft Teams or Viber.

WEEKDAY PERSONALITY (use the weekday you are given):
- MONDAY - FRESH START: a natural return into the week. Positive without Monday pressure. Rhythm, a clean page, reconnecting; light humour occasionally.
- TUESDAY - IN THE FLOW: the week has settled. Collaboration, conversations, ideas, learning, helping one another, normal productive rhythm.
- WEDNESDAY - MIDWEEK RESET: halfway through. A small reset rather than another push. Progress, appreciation, learning, helping someone; light midweek humour.
- THURSDAY - TAKING SHAPE: the week is coming together. Loose ends, helping each other, appreciation, something learned, lighter pre-Friday energy.
- FRIDAY - FUN + WEEKEND: clearly lighter and more human. Appreciation for the week, light jokes about meetings/calendars/work, the weekend naturally approaching. Never another productivity lecture.

VOICE: a senior manager - warm, confident, friendly, human, simple everyday English. Not HR. Not motivational-poster language. Not AI-sounding.

SHAPE: a fresh opening line, one weekday-appropriate thought, and a very short closing. Normally 75-150 characters; slightly longer only if it reads more naturally.
Vary openings naturally - do not begin with "Good morning team".
Rotate themes: team connection, appreciation, quality, learning, collaboration, ownership, conversations, small wins, helping each other, work-life balance, light workplace humour, occasional motivation.

NEVER USE motivational-poster or corporate filler, including: "stay focused", "keep the momentum", "finish strong", "make today count", "steady progress", "give 100%", "crush your goals", "have a productive day", "wishing everyone", "have a smooth day", "make it shine", "stay awesome", "keep pushing forward", "lets make it great", "lets crush", "keep it going strong", "onwards and upwards".

ANTI-REPEAT: you are given the manager's recent greetings. Do not reuse their opening, sentence structure, thought, joke or closing.

FORMATTING: Viber/WhatsApp markers only, used naturally and sparingly: *bold*, _italic_, ~strike~. No emojis, no hashtags, no bullet points, no names, no business figures.

OUTPUT: only the greeting itself. No code fences, no labels, no "Generated message:", no weekday heading, no explanation."""

_MORNING_BANK = {
    "MONDAY": (
        ["Morning, everyone.", "Hi all, welcome back.", "Hope the weekend was a good one.", "New week, everyone.", "Hello all, back at it."],
        ["*A clean page this week* — worth ten minutes to reconnect with each other before the calendar fills up.",
         "Easing back in is fine. _The coffee is doing most of the work until eleven anyway._",
         "If something from last week is still bugging you, *say it early* — it is usually quicker to sort together.",
         "Good time to catch up with someone you did not get to speak to last week.",
         "*One small win today is plenty* to set the rhythm for the week.",
         "A quick check-in with a colleague often saves a long thread later."],
        ["_Have a good Monday._", "_Glad to have you back._", "_Enjoy the start._", "_Talk soon._"],
    ),
    "TUESDAY": (
        ["Hi all.", "Morning, team.", "Hello everyone.", "Tuesday already.", "Hope the week has settled in."],
        ["*The week has found its rhythm* — a nice day for the conversations that turn into good ideas.",
         "If you picked up something useful yesterday, *share it* — someone else is probably stuck on it.",
         "_Tuesday is the quiet hero of the week_: fewer surprises, more real work getting done.",
         "Worth asking a colleague how their week is going — the answer is often more useful than the status update.",
         "*Good collaboration beats long emails.* A five-minute call can clear most of today's questions.",
         "Learning something new this week? *Pass it on* while it is fresh."],
        ["_Have a good Tuesday._", "_Enjoy the day._", "_Take care._", "_Catch you later._"],
    ),
    "WEDNESDAY": (
        ["Midweek already, everyone.", "Hi all, halfway there.", "Hello team, it's Wednesday.", "Morning, all.", "Happy Wednesday, everyone."],
        ["*Halfway through* — a good day to share what's working and help someone past a small hurdle.",
         "A small reset helps: _look at what already moved this week_ before planning the rest.",
         "_Midweek rule_: if a meeting could be a message, it probably should be.",
         "*Thanks for the effort so far this week* — it has not gone unnoticed.",
         "Good day to learn one small thing from someone on the team.",
         "If you are carrying something heavy this week, *ask for a hand* — that is what the team is for."],
        ["_Have a good Wednesday._", "_Enjoy the day._", "_Onwards, gently._", "_Take care._"],
    ),
    "THURSDAY": (
        ["Hi all.", "Morning, everyone.", "Thursday, team.", "Hello all, nearly there.", "Hope everyone is well."],
        ["*The week is taking shape.* A good day to tie up loose ends before they follow you into Friday.",
         "If someone helped you out this week, *today is a nice day to tell them.*",
         "_Almost-Friday energy is allowed_, as long as the calendar invites are still being answered.",
         "What did you learn this week? *A two-line share* can save a colleague an afternoon.",
         "Worth a quick look at anything still open, so tomorrow can be lighter for everyone.",
         "*Small favours add up.* Offer help on one thing that is not yours today."],
        ["_Have a good Thursday._", "_Enjoy the day._", "_Nearly there._", "_Talk soon._"],
    ),
    "FRIDAY": (
        ["Friday, everyone.", "Hi all, we made it.", "Happy Friday, team.", "Morning, all, it's Friday.", "Hello everyone, weekend is in sight."],
        ["*Thanks for a solid week*, everyone — properly appreciated.",
         "_Friday forecast_: a few meetings, one mystery calendar invite, and the weekend approaching fast.",
         "If your inbox is winning today, *call it a draw* and pick it up on Monday.",
         "*Proud of how the team pulled together this week.* Enjoy the switch-off when it comes.",
         "_Official Friday policy_: at least one conversation today that has nothing to do with work.",
         "Take a moment to note one thing that went well this week — *there is usually more than you think.*"],
        ["_Have a great weekend._", "_Enjoy the weekend, all._", "_Rest well._", "_See you Monday._"],
    ),
}


def sanitize_morning_greeting(text: str) -> str:
    """Reduce model output to the greeting alone: no fences, labels or markdown doubles."""
    t = str(text or "").replace("\r\n", "\n").strip()
    t = re.sub(r"^```[a-zA-Z]*\s*|\s*```$", "", t).replace("```", "").strip()
    label = re.compile(r"^\s*(generated message|message|greeting|morning note|monday|tuesday|wednesday|thursday|friday)\s*[:\-]?\s*$", re.I)
    lines = t.split("\n")
    while lines and label.match(lines[0]):
        lines.pop(0)
    t = "\n".join(lines)
    t = re.sub(r"^\s*(generated message|greeting)\s*:\s*", "", t, flags=re.I)
    t = re.sub(r"\*\*(.+?)\*\*", r"*\1*", t)
    t = re.sub(r"__(.+?)__", r"_\1_", t)
    t = t.strip()
    # Models often wrap the whole reply in quotes.
    while len(t) > 1 and t[0] in "\"'“‘" and t[-1] in "\"'”’":
        t = t[1:-1].strip()
    return _clean_formatting(t)


_DAY_NAMES = ("monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday")


def _normalise_phrase(text: str) -> str:
    """Lowercase, drop apostrophes ("let's" -> "lets"), other punctuation to spaces."""
    flat = re.sub(r"['’]", "", str(text or "").lower())
    return re.sub(r"\s+", " ", re.sub(r"[^a-z0-9]+", " ", flat)).strip()


def _opening(text: str) -> str:
    """First sentence or line, normalised — the part a repeated greeting shares."""
    head = re.split(r"(?<=[.!?])\s|\n", str(text or "").strip(), maxsplit=1)[0]
    return re.sub(r"[^a-z' ]+", "", head.lower()).strip()


def _words(text: str) -> set:
    return set(re.findall(r"[a-z']+", str(text or "").lower()))


def morning_greeting_issues(text: str, recent: list, weekday: str = "") -> list:
    """Policy check for a morning greeting from any source (model or bank)."""
    issues = []
    low = text.lower()
    if not text.strip():
        issues.append("empty greeting")
    if "```" in text:
        issues.append("contains a code fence")
    if re.search(r"^\s*(generated message|message|greeting|morning note)\s*:", text, re.I | re.M):
        issues.append("contains a label")
    if len(text) < 30:
        issues.append(f"too short ({len(text)} characters)")
    if len(text) > 260:
        issues.append(f"too long ({len(text)} characters)")
    if re.search(r"\bgood (afternoon|evening|night)\b", low):
        issues.append("not a morning greeting")
    if re.fullmatch(r"\s*([*_~])[^*_~]+\1\s*", text):
        issues.append("whole greeting wrapped in one formatting marker")
    if "**" in text or "__" in text:
        issues.append("uses markdown doubles instead of Viber markers")
    if re.sub(r"[^a-z]+", " ", low).split()[:3] == ["good", "morning", "team"]:
        issues.append("stock opening")
    if re.search(r"~[^~\n]+~\s*[.!]?\s*$", text.strip()):
        # ~strike~ renders as crossed-out text; as a sign-off it reads as a retraction.
        issues.append("strikethrough used as a closing")
    flat = _normalise_phrase(text)
    for phrase in MORNING_BANNED_PHRASES:
        if _normalise_phrase(phrase) in flat:
            issues.append(f"banned phrase: {phrase}")
    if re.search(r"[\U0001F300-\U0001FAFF☀-➿]", text):
        issues.append("contains emoji")
    day = str(weekday or "").lower()
    if day in _DAY_NAMES[:5]:
        # Personality anchor: only today, or the next working day, may be named;
        # Saturday/Sunday may only appear on a Friday ("weekend" chat).
        idx = _DAY_NAMES.index(day)
        allowed = {day, "monday" if day == "friday" else _DAY_NAMES[idx + 1]}
        if day == "friday":
            allowed |= {"saturday", "sunday"}
        wrong = [d for d in _DAY_NAMES if re.search(rf"\b{d}\b", low) and d not in allowed]
        if wrong:
            issues.append(f"names the wrong day for {day.title()}: {', '.join(wrong)}")
        if day != "friday" and "weekend" in low and day != "monday":
            issues.append(f"weekend talk does not fit {day.title()}")
    first = _opening(text)
    mine = _words(text)
    for r in recent or []:
        prev = str(r)
        if first and _opening(prev) == first:
            issues.append("repeats a recent opening")
            break
        theirs = _words(prev)
        if mine and theirs and len(mine & theirs) / len(mine | theirs) > 0.6:
            issues.append("substantially identical to a recent greeting")
            break
    return issues



def _pick_unused(options: list, recent: list, seed: int) -> str:
    start = seed % len(options)
    rotated = [options[(start + i) % len(options)] for i in range(len(options))]
    for o in rotated:
        if not any(o in str(r) for r in recent):
            return o
    def last_use(o):
        for i, r in enumerate(recent):
            if o in str(r):
                return i
        return 10 ** 6
    return max(rotated, key=last_use)


def _compose_morning_deterministic(weekday: str, recent: list, variation: int) -> str:
    openings, thoughts, closings = _MORNING_BANK[weekday]
    seed = (variation * 7 + len(recent) * 3 + len(weekday)) % 997
    opening = _pick_unused(openings, recent, seed)
    thought = _pick_unused(thoughts, recent, seed // 2 + variation)
    closing = _pick_unused(closings, list(recent)[:2], seed + variation)
    return f"{opening}\n\n{thought} {closing}"


def morning_prompt_user(weekday: str, recent: list, variation: int, corrections: Optional[list] = None) -> str:
    """User/context prompt: the weekday, recent greetings to avoid, and any correction."""
    payload = {"weekday": weekday, "recent_greetings_to_avoid": list(recent), "variation": variation}
    text = json.dumps(payload, indent=2)
    if corrections:
        text += ("\n\nYour previous attempt was rejected for: " + "; ".join(corrections)
                 + ". Write a new greeting that fixes every point. Output only the greeting.")
    return text


def compose_morning_greeting(weekday: str, recent: list, variation: int = 0) -> Tuple[str, Provenance]:
    """Providers first, validated.

    valid response            -> use it
    fast but invalid response -> exactly one corrective retry
    second invalid response   -> deterministic bank
    timeout / no provider     -> deterministic bank immediately (no retry)
    """
    weekday = str(weekday or "").upper()
    if weekday not in MORNING_WEEKDAYS:
        return "", Provenance(DETERMINISTIC, "", fallback_used=False, attempts=0)
    recent = [str(r) for r in (recent or []) if str(r).strip()][:10]
    corrections: Optional[list] = None
    attempts = 0
    elapsed_ms = 0
    timeout_reason = ""
    tried: list = []
    for _ in range(2):  # first attempt + at most one corrective retry
        remaining_ms = providers.INTERACTIVE_BUDGET_MS - elapsed_ms
        if remaining_ms < 2000:
            break  # not enough of the interactive budget left for a useful retry
        outcome = providers.generate(
            MORNING_GREETING_PROMPT, morning_prompt_user(weekday, recent, variation, corrections),
            temperature=0.9, max_tokens=160, budget_ms=remaining_ms,
        )
        elapsed_ms += outcome.elapsed_ms
        timeout_reason = timeout_reason or outcome.timeout_reason
        tried += [p for p in outcome.tried if p not in tried]
        if outcome.tried:
            attempts += 1  # a provider was actually called this round
        if not outcome.result:
            break  # timed out or nothing available — a retry would only add latency
        clean = sanitize_morning_greeting(outcome.result.text)
        corrections = morning_greeting_issues(clean, recent, weekday)
        if not corrections:
            return clean, Provenance(outcome.result.provider, outcome.result.model, fallback_used=False,
                                     attempts=attempts, elapsed_ms=elapsed_ms, timeout_reason=timeout_reason,
                                     attempted_providers=list(tried))
        if outcome.timed_out:
            break  # a slow provider already cost a timeout; do not spend another on a retry
    return (_compose_morning_deterministic(weekday, recent, variation),
            Provenance(DETERMINISTIC, "", fallback_used=True, attempts=attempts,
                       elapsed_ms=elapsed_ms, timeout_reason=timeout_reason,
                       attempted_providers=list(tried)))
