"""MessageIntentAnalyzer — works out WHO / WHY / WHAT / WHEN / HOW FIRM.

The authoritative rule:
- If User Message exists: User Message is the PRIMARY source of intent (what the communication responds to).
- If User Message is empty: derive intent entirely from My Message.
- If both exist: understand them together; User Message defines what the communication is responding to,
  and My Message defines what I want to communicate/do about it.
"""

from __future__ import annotations

import re as _re
from typing import List

from . import policy

_DAY_NAMES = ["monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday"]

# Expanded Hinglish normalisation — understands common phrases and rough notes.
_HINGLISH = {
    "kal": "tomorrow",
    "parso": "day after tomorrow",
    "jaldi": "soon",
    "plz": "please",
    "pls": "please",
    "bhejo": "send",
    "thoda": "a little",
    "aaj": "today",
    "karo": "please do",
    "nahi": "not",
    "haan": "yes",
    "ha": "yes",
    "bol do": "tell them",
    "le lunga": "I will take it",
    "le lenge": "we will take it",
    "chahiye": "is needed",
    "ke liye": "for",
    "possible hai": "is possible",
    "ho jayega": "will be done",
    "kya": "what",
    "kab": "when",
    "bhej diya": "sent it",
    "de diya": "gave it",
}


def _normalise(text: str) -> str:
    out = str(text or "")
    for k, v in _HINGLISH.items():
        out = _re.sub(rf"\b{_re.escape(k)}\b", v, out, flags=_re.IGNORECASE)
    return out


class Intent:
    def __init__(self):
        self.tone = "professional"
        self.purpose = "GENERAL_PROFESSIONAL"
        self.urgency = "NORMAL"
        self.recipient_name = ""
        self.recipient_class = "UNKNOWN"
        self.user_message = ""
        self.my_message = ""
        self.inbound_context = ""
        self.response_position = ""  # ACCEPT | DECLINE | COMPLETED | DIRECTIVE | INQUIRY
        self.course = ""
        self.qualifiers: List[str] = []
        self.deadline_text = ""
        self.pending_since = ""
        self.time_refs: List[str] = []
        self.exclusions: List[str] = []
        self.positive_points: List[str] = []
        self.issues: List[str] = []
        self.completed: List[str] = []
        self.next_actions: List[str] = []
        self.content = ""
        self.short = False
        self.remove_closing = False
        self.firm = False
        self.soft = False
        self.override_lines: List[str] = []


def _strip_directives(text: str) -> str:
    """Remove directive phrases so they do not leak into the message body."""
    return _re.sub(
        r"\b(?:please |could you |kindly )?(?:make it |keep it )?(?:more |less )?"
        r"(?:firm|softer|soft|short|shorter|brief|to the point|polite|formal)"
        r"\b[^.\n]*",
        "",
        text,
        flags=_re.IGNORECASE,
    ).strip()


def analyze(
    user_message: str,
    my_message: str,
    recipient_name: str = "",
    recipient_type: str = "UNKNOWN",
    purpose_hint: str = "",
) -> Intent:
    um_raw = str(user_message or "").strip()
    mm_raw = str(my_message or "").strip()
    um = _normalise(um_raw)
    mm = _normalise(mm_raw)
    combined = " ".join([um, mm]).strip()
    lower_comb = combined.lower()

    intent = Intent()
    intent.user_message = um_raw
    intent.my_message = mm_raw
    intent.recipient_name = (recipient_name or "").strip()

    # Recipient name inference if missing
    if not intent.recipient_name:
        m = _re.search(
            r"\b(?:tell|ask|message|inform|text|ping|email|update)\s+"
            r"((?:[A-Z][A-Za-z]{1,20})(?:[ ][A-Z][A-Za-z]{1,20})?)",
            combined,
        )
        if m:
            intent.recipient_name = m.group(1).strip()

    # Recipient type
    rt = str(recipient_type or "").upper()
    if rt in ("TEAM", "MANAGER", "REPORTEE", "COLLEAGUE", "CLIENT"):
        intent.recipient_class = rt
    else:
        intent.recipient_class = "TEAM" if _re.search(r"\b(team|everyone|all|colleagues)\b", lower_comb) else rt or "UNKNOWN"

    # Tone overrides
    if _re.search(r"\b(?:softer|kindly|kind|gentle|polite|nicer|less firm|soften)\b", lower_comb):
        intent.soft, intent.tone = True, "professional"
    if _re.search(r"\b(?:firm|firmer|strict|strictly|strongly worded|not be soft|be firm)\b", lower_comb):
        intent.firm, intent.tone = True, "firm"
    if _re.search(r"\b(?:urgent|asap|immediately|right away|do not delay|at the earliest)\b", lower_comb):
        intent.urgency = "HIGH"
    if _re.search(r"\b(?:short|shorter|brief|concise|one line|one sentence)\b", lower_comb):
        intent.short = True
    if _re.search(r"remove closing|no closing|without closing|leave off closing", lower_comb):
        intent.remove_closing = True

    # Exclusions
    excl = _re.findall(
        r"\b(?:do not mention|dont mention|don'?t mention|without mentioning|leave out|skip)\s+([^.,;\n]{2,40})",
        combined,
        flags=_re.I,
    )
    intent.exclusions = [e.strip() for e in excl]

    # Time references
    for tr in ["next week", "this week", "today", "tomorrow", "friday", "monday", "tuesday", "wednesday", "thursday", "end of this month"]:
        if tr in lower_comb and tr not in intent.time_refs:
            intent.time_refs.append(tr.capitalize() if tr in _DAY_NAMES else tr)

    # Course extraction
    c_match = _re.search(r"\b([A-Z]{2,4}-[0-9]{2,4}[A-Z0-9]*)\b", f"{um_raw} {mm_raw}", _re.I)
    if c_match:
        intent.course = c_match.group(1).upper()

    # Pending since (only if explicit!)
    m = _re.search(r"\bpending\s+(?:since|from)\s+((?:an? )?(?:" + "|".join(_DAY_NAMES) + r"))", lower_comb)
    if m:
        intent.pending_since = m.group(1).capitalize()
        if not intent.firm:
            intent.firm = True
            intent.tone = "firm"

    # ── AUTHORITATIVE INTENT DETERMINATION ────────────────────────────────────
    if purpose_hint and str(purpose_hint).upper() in policy.PURPOSES:
        intent.purpose = str(purpose_hint).upper()

    elif um_raw:
        # ── CASE 1: USER MESSAGE IS PRESENT (PRIMARY CONVERSATIONAL CONTEXT) ──
        lower_um = um.lower()
        lower_mm = mm.lower()

        # Did user ask about delivering a batch / course?
        if _re.search(r"\b(can you take|take up|possible to take|could you deliver|are you available to (?:deliver|teach|take)|teach|take (?:the )?([A-Z]{2,4}-\d+|batch))\b", lower_um) or "possible hai" in um_raw.lower():
            intent.purpose = "OPPORTUNITY_RESPONSE"
            intent.inbound_context = f"Delivery request for {intent.course or 'the batch'}"
            # Check position in my_message
            if _re.search(r"\b(yes|can take|will take|take it|sure|available|haan|ha|le lunga)\b", lower_mm):
                intent.response_position = "ACCEPT"
                if _re.search(r"\b(prep|preparation|toc|table of contents|material|study)\b", lower_mm):
                    intent.qualifiers.append("preparation")
                    if "toc" in lower_mm:
                        intent.qualifiers.append("toc")
            elif _re.search(r"\b(no|cannot|can't|unable|not possible|another delivery|busy|nahi)\b", lower_mm):
                intent.response_position = "DECLINE"
            else:
                intent.response_position = "ACCEPT"

        # Did user ask about availability / free time?
        elif _re.search(r"\b(are you free|free tomorrow|free next week|available tomorrow|available next week|can we meet|can we connect)\b", lower_um):
            intent.purpose = "AVAILABILITY_RESPONSE"
            intent.inbound_context = "Inquiry regarding availability"
            if _re.search(r"\b(no|delivery|in delivery|cannot|busy|not free)\b", lower_mm):
                intent.response_position = "DECLINE"
                if "friday" in lower_mm:
                    intent.qualifiers.append("connect_friday")
            else:
                intent.response_position = "ACCEPT"

        # Did user ask to send / submit / share a report or task?
        elif _re.search(r"\b(please send|share (?:the )?(?:feedback|report)|send the report|submit|status of)\b", lower_um):
            if _re.search(r"\b(completed|shared|already sent|done|sent it|bhej diya)\b", lower_mm):
                intent.purpose = "STATUS_UPDATE"
                intent.response_position = "COMPLETED"
            else:
                intent.purpose = "TASK_FOLLOWUP"
                intent.response_position = "DIRECTIVE"

        # Default fallback when User Message is present
        else:
            if _re.search(r"\b(thank|appreciat)\b", lower_mm):
                intent.purpose = "APPRECIATION"
            else:
                intent.purpose = "GENERAL_PROFESSIONAL"

    elif mm_raw:
        # ── CASE 2: MY MESSAGE ONLY (MY MESSAGE IS ENTIRE INTENT) ─────────────
        lower_mm = mm.lower()
        if _re.search(r"\b(confident|preparation|prep|readiness|take\s+[A-Z]{2,4}-\d+.*deliver\s+it\s+with\s+quality)\b", lower_mm):
            intent.purpose = "COURSE_PREPARATION_CHECK"
        elif _re.search(r"\b(ask the team|who can take|check availability|open requirement|available to take)\b", lower_mm):
            intent.purpose = "AVAILABILITY_REQUEST"
        elif _re.search(r"\b(appreciat\w*|good job|great work|well done|thank\w*|kudos|congrat\w*|proud)\b", lower_mm):
            intent.purpose = "APPRECIATION"
            intent.tone = "appreciative"
        elif _re.search(r"\b(session|training|class|delivery|assessment|power cut)\b", lower_mm):
            intent.purpose = "DELIVERY_UPDATE"
        elif _re.search(r"\b(cab|travel desk|travel|flight)\b", lower_mm):
            intent.purpose = "TRAVEL_COORDINATION"
        elif _re.search(r"\b(cert|exam|book exam)\b", lower_mm):
            intent.purpose = "CAPABILITY_DEVELOPMENT"
        elif _re.search(r"\b(can i do it|say yes|take (?:it|this) up|accept (?:the )?(?:batch|delivery))\b", lower_mm):
            intent.purpose = "OPPORTUNITY_RESPONSE"
        elif _re.search(r"\b(please ensure|provision|complete|review)\b", lower_mm):
            intent.purpose = "TASK_ASSIGNMENT"
        else:
            intent.purpose = "GENERAL_PROFESSIONAL"

    else:
        # ── CASE 3: NEITHER PROVIDED (AUTO-GENERATION MODE) ───────────────────
        intent.purpose = "SITUATION_EVALUATION"

    # Content body
    source = (mm_raw or um_raw or "").strip()
    intent.content = _strip_directives(source)

    return intent
