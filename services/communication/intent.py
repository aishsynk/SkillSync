"""MessageIntentAnalyzer — works out WHO / WHY / WHAT / WHEN / HOW FIRM.

User Message is the primary source of intent when present (the user's explicit
instructionary text). My Message carries the raw communication content and is
treated as *semantic intent* (Hinglish, fragments, rough notes), never as
literal polished text. Inferred intent may steer tone and structure, but it
must never invent facts that are not in the inputs or in verified context.
"""

from __future__ import annotations

import re as _re

from . import policy

_DAY_NAMES = ["monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday"]

# Simple Hinglish normalisation — enough to read common rough notes.
_HINGLISH = {
    "kal": "tomorrow",
    "parso": "day after tomorrow",
    "jaldi": "soon",
    "plz": "please",
    "bhejo": "send",
    "thoda": "a little",
    "aaj": "today",
    "karo": "please do",
    "nahi": "not",
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
        self.owner = ""
        self.deadline_text = ""
        self.pending_since = ""
        self.exclusions = []
        self.positive_points = []
        self.issues = []
        self.completed = []
        self.next_actions = []
        self.content = ""
        self.short = False
        self.remove_closing = False
        self.firm = False
        self.soft = False
        self.override_lines = []  # "add this line …" directives


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
    um = _normalise(user_message or "").strip()
    mm = _normalise(my_message or "").strip()
    combined = " ".join([um, mm]).strip()
    intent = Intent()
    intent.recipient_name = (recipient_name or "").strip()
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
        intent.recipient_class = "TEAM" if _re.search(r"\b(team|everyone|all|colleagues)\b", combined, _re.I) else rt or "UNKNOWN"

    # Tone overrides from the user's explicit wording.
    if _re.search(r"\b(?:softer|kindly|kind|gentle|polite|nicer|less firm|soften)\b", combined, _re.I):
        intent.soft, intent.tone = True, "professional"
    if _re.search(r"\b(?:firm|firmer|strict|strictly|strongly worded|not be soft|be firm)\b", combined, _re.I):
        intent.firm, intent.tone = True, "firm"
    if _re.search(r"\b(?:urgent|asap|immediately|right away|do not delay|at the earliest)\b", combined, _re.I):
        intent.urgency = "HIGH"
    if _re.search(r"\b(?:short|shorter|brief|concise|one line|one sentence)\b", combined, _re.I):
        intent.short = True
    if _re.search(r"remove closing|no closing|without closing|leave off closing", combined, _re.I):
        intent.remove_closing = True

    # Exclusions: "do not mention X" / "without mentioning X".
    excl = _re.findall(
        r"\b(?:do not mention|dont mention|don'?t mention|without mentioning|leave out|skip)\s+([^.,;\n]{2,40})",
        combined,
        flags=_re.I,
    )
    intent.exclusions = [e.strip() for e in excl]

    # captured "add this line …" overrides
    addline = _re.findall(r"add this line\s*[:\-]?\s*(.+?)(?:\.|$)", combined, flags=_re.I)
    intent.override_lines = [a.strip() for a in addline if a.strip()]

    # urgency: pending since a day
    m = _re.search(r"\bpending\s+(?:since|from|for)\s+((?:an? )?(?:" + "|".join(_DAY_NAMES) + r"))", combined, _re.I)
    if m:
        intent.pending_since = m.group(1)
        if not intent.firm:
            intent.firm = True
            intent.tone = "firm"
    # deadline references ("by Friday", "tomorrow", "by 5 pm")
    m = _re.search(
        r"\b(?:by|before|until|till|for)\s+((?:today|tomorrow|next week|this week)?" +
        r"(?:\s*(?:an? )?(?:" + "|".join(_DAY_NAMES) + r"))?"
        r"(?:\s*(?:at\s*)?\d{1,2}(?:[:.]\d{2})?\s*(?:am|pm|IST|PST)?)?)\b",
        combined,
        flags=_re.I,
    )
    if m:
        intent.deadline_text = " ".join(m.group(1).split())

    # Purpose inference
    lower = combined.lower()
    if purpose_hint and str(purpose_hint).upper() in policy.PURPOSES:
        intent.purpose = str(purpose_hint).upper()
    elif _re.search(r"\b(can i|yes i can|take it up|i (?:can|could) do|available to|aligns with)\b", lower):
        intent.purpose = "OPPORTUNITY_RESPONSE"
    elif _re.search(r"\b(session|training|class|delivery|assessment|took the assessment|power cut|going (?:on|fine|good))\b", lower):
        intent.purpose = "DELIVERY_UPDATE"
    elif _re.search(r"\b(cab|travel desk|travel|hotel|flight|expense|outstation|ilove|fmat|ilt)\b", lower):
        intent.purpose = "TRAVEL_COORDINATION"
    elif _re.search(r"\b(appreciat|good job|great work|well done|thank(s| you))\b", lower) and not _re.search(r"\b(please|kindly|can you|need)\b", lower):
        intent.purpose = "APPRECIATION"
    elif _re.search(r"\b(approve|approval|signed off|sign-off|authorise|authorize)\b", lower):
        intent.purpose = "APPROVAL_REQUEST"
    elif _re.search(r"\b(blocked|blocker|cannot (?:proceed|start|complete)|stuck|escalat)\b", lower):
        intent.purpose = "BLOCKER_ESCALATION"
    elif _re.search(r"\b(pl?ea?se (?:complete|do|share|send|cross-check|fill|submit)|when possible|pending|overdue|outstanding|need to (?:complete|finish)|must (?:be|complete|share))\b", lower):
        intent.purpose = "TASK_FOLLOWUP"
    elif _re.search(r"\b(reschedul|postpon|move (?:to|the)|schedule|cancel|timing|date of)\b", lower):
        intent.purpose = "SCHEDULE_UPDATE"
    elif _re.search(r"\b(assign|task (?:is|to)|add this|please (?:handle|own|take care)|from monday|do this)\b", lower):
        intent.purpose = "TASK_ASSIGNMENT"
    elif _re.search(r"\b(status|update on|where is|how is)\b", lower):
        intent.purpose = "STATUS_UPDATE"
    else:
        intent.purpose = "GENERAL_PROFESSIONAL"
    if intent.purpose == "OPPORTUNITY_RESPONSE" and not intent.recipient_name:
        intent.recipient_name = "team"

    # Content facts — pull from My Message when supplied, otherwise User Message.
    source = (mm or um or "").strip()
    # Clean directive words out of the body.
    body = _strip_directives(source)
    intent.content = body
    for hit in _re.finditer(
        r"\b(session|training|class|delivery|assessment|power|transport|hiccup|good|fine|great|went well|completed|managed|collected|finished)\w*",
        body,
        flags=_re.I,
    ):
        word = hit.group(0).lower()
        if word in ("good", "fine", "great", "well"):
            intent.positive_points.append(word)
        elif word in ("power", "hiccup", "transport", "issue", "problem"):
            intent.issues.append(word)
        elif word in ("completed", "finished", "managed", "took", "collected"):
            intent.completed.append(word)
    if "assessment" in body.lower():
        intent.completed.append("assessment")
    return intent