"""MessageComposer — deterministic draft of the final professional message.

Policy is the single source of formatting rules. Facts inserted here come only
from (a) the inputs and (b) verified SkillEdge context. Nothing is guessed.
"""

from __future__ import annotations

import re as _re

from .policy import bold, bold_underline, closing_for, greeting_for, italic, split_sentences


def _strip_names(text: str, name: str) -> str:
    """Italicise the recipient name without double-wrapping existing markup."""
    if name:
        text = _re.sub(
            rf"(?<!\*)\b{_re.escape(name)}\b(?!\*)",
            italic(name),
            text,
            flags=_re.I,
        )
    return text


def compose(intent, context=None) -> str:
    """context: CommunicationContext (domain models) or None."""
    recipient_type = (context.recipient.type if context else "") or (intent.recipient_class or "UNKNOWN")
    recipient_type = "TEAM" if recipient_type == "TEAM" else recipient_type
    tone = intent.tone or "professional"
    name = intent.recipient_name or (context.recipient.name if context else "")

    greeting = greeting_for(recipient_type, tone, name)

    if intent.purpose == "OPPORTUNITY_RESPONSE" and context and context.verified_context.get("opportunity"):
        body = _opportunity_response(intent, context.verified_context["opportunity"])
    else:
        body = _draft_body(intent, context)

    for line in intent.override_lines:
        if line and line.lower() not in body.lower():
            body = body.rstrip().rstrip(".") + ". " + line + "."

    lines = [greeting, body]
    if not intent.remove_closing:
        lines.append(closing_for(tone, getattr(context.recipient, "relationship", "") if context else ""))

    message = "\n".join(lines).strip()

    if intent.short:
        first_sentence = split_sentences(body)[0] if split_sentences(body) else body
        message = "\n".join([greeting, first_sentence]) if intent.remove_closing else "\n".join(
            [greeting, first_sentence, closing_for(tone)]
        )

    message = _strip_names(message, name)
    if intent.deadline_text:
        for marker in (" by ", " before ", " until ", " till ", " for "):
            if marker + intent.deadline_text in message:
                message = message.replace(
                    marker + intent.deadline_text, marker + bold_underline(intent.deadline_text)
                )
    for excl in intent.exclusions:
        message = _re.sub(_re.escape(excl), "", message, flags=_re.I)

    message = _re.sub(r"\s{2,}", " ", message)
    message = _re.sub(r"\s+([.,;:])", r"\1", message)
    return message.strip()


def _opportunity_response(intent, opp: dict) -> str:
    course = opp.get("course_code") or opp.get("course") or ""
    decision = str(opp.get("decision") or "").lower()
    if decision == "decline":
        return (
            "Thank you for considering me, but I will not be able to take this up at this time."
        )
    parts = ["Yes, I can take this up."]
    if course:
        parts.append(f"I have reviewed the requirement for {bold(course)} and it aligns with my capability, with brief preparation.")
    else:
        parts.append("I have reviewed the requirement and it aligns with my capability, with brief preparation.")
    if decision and "preparation" in decision:
        parts.append("A short preparation is needed and it is manageable.")
    parts.append("Please share the next steps whenever ready.")
    return " ".join(parts)


def _draft_body(intent, context) -> str:
    content = (intent.content or "").strip()
    purpose = intent.purpose

    if purpose == "DELIVERY_UPDATE":
        return _delivery_update_body(intent)
    if purpose == "TASK_FOLLOWUP":
        if intent.pending_since:
            task = bold(f"This task has been pending since {intent.pending_since}")
            return f"{task}. Please complete it as soon as possible."
        action = bold("Please complete this soon") if intent.urgency == "HIGH" else "Can you please take care of this when possible?"
        deadline = intent.deadline_text
        tail = f" It would help to have it {deadline}." if deadline else ""
        return "Please confirm the current status of this task." + tail + " " + action
    if purpose == "TASK_ASSIGNMENT":
        item = content or "this item"
        deadline = f" The deadline is {bold_underline(intent.deadline_text)}." if intent.deadline_text else ""
        return bold(f"Please take ownership of {item}") + deadline
    if purpose == "APPRECIATION":
        core = content or "Great work on this."
        return core.rstrip(".") + "; please keep it up."
    if purpose == "TRAVEL_COORDINATION":
        return (
            "A reminder for those with FMAT or ILT travel coming up: please coordinate "
            "with the Travel Desk and Payroll in advance whenever cab arrangements outside "
            "India may be needed."
        )
    if purpose == "STATUS_UPDATE":
        return (content or "Here is the current status.").rstrip(".") + "."
    if purpose == "APPROVAL_REQUEST":
        return bold("Requesting your approval") + " on this so the next steps can proceed."
    if purpose == "BLOCKER_ESCALATION":
        issue = _issue_phrase(intent.issues[0]) if intent.issues else "an operational issue"
        return bold(f"Escalating this: {issue}") + " is blocking progress and needs your attention."
    if purpose == "SCHEDULE_UPDATE":
        return "A schedule change is coming up." + (f" The update is {intent.deadline_text}." if intent.deadline_text else "")
    if content:
        content = _strip_salutation(content, intent.recipient_name)
        sentences = split_sentences(content)
        if not sentences:
            sentences = [content]
        points = list(sentences)
        if intent.completed:
            points.append("This has been completed as required.")
        if intent.issues:
            points.append("A brief operational issue came up, but it was managed.")
        return " ".join(points)
    return "I would like to follow up on this." if not intent.owner else f"Please can you handle {intent.owner}'s request."


def _strip_salutation(content: str, name: str) -> str:
    """Turn 'Tell <Name> <fact>' into '<fact>' when the recipient is <Name>."""
    before = content
    content = _re.sub(rf"^tell\s+(?:{_re.escape(name)}\s+)?(.+)$", r"\1", content.strip(), flags=_re.I)
    content = _re.sub(r"^please tell\s+(.+)$", r"\1", content, flags=_re.I)
    return content if content.strip() else before


def _issue_phrase(token: str) -> str:
    t = str(token or "").lower()
    if "power" in t:
        return "a power interruption on the client side"
    if "transport" in t:
        return "the daily transport coordination"
    if "hiccup" in t:
        return "a brief interruption"
    return "an operational issue"


def _delivery_update_body(intent) -> str:
    points = []
    if intent.positive_points:
        points.append("The session is going well.")
    if intent.issues:
        issue = _issue_phrase(intent.issues[0])
        points.append(f"There was {issue}, but it was managed and the session continued on schedule.")
    if intent.completed:
        done = " and ".join(sorted(set(intent.completed)))
        if "assessment" in done:
            points.append("The required delivery, including the assessment, was completed as planned.")
        else:
            points.append(f"The required work, including {done}, was completed as planned.")
    if not points:
        points.append("The delivery is proceeding as planned.")
    if "transport" in " ".join(intent.issues):
        points.append("I would appreciate help with the daily transport coordination.")
    return " ".join(points)