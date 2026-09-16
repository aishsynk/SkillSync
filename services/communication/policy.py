"""Communication Intelligence — the authoritative SkillEdge message policy.

Central, maintainable home for the professional MS Teams / Viber communication
rules. Composer and validator both read from here; no Compose screen or route
should hard-code its own copy of these rules.

Policy summary
    greeting   → own line
    main       → own line, after greeting
    closing    → own line, light emphasis, chosen by relationship + tone
    names      → italics (*Name*)
    key action → bold (**...**)
    time/date  → bold + underline (__**...**__)
    no emojis, bullets, numbered lists, decorative symbols
    <= MAX_LENGTH characters, no invented facts
"""

from __future__ import annotations

import re as _re

MAX_LENGTH = 1000

# ── Tone category ──────────────────────────────────────────────────────────

TONES = (
    "professional",
    "firm",
    "corrective",
    "advisory",
    "appreciative",
    "collaborative",
    "urgent",
    "informational",
)


class Tone:
    PROFESSIONAL = "professional"
    FIRM = "firm"
    CORRECTIVE = "corrective"
    ADVISORY = "advisory"
    APPRECIATIVE = "appreciative"
    COLLABORATIVE = "collaborative"
    URGENT = "urgent"
    INFORMATIONAL = "informational"


# ── Recipient types ────────────────────────────────────────────────────────

RECIPIENT_TYPES = (
    "INDIVIDUAL",
    "MANAGER",
    "REPORTEE",
    "COLLEAGUE",
    "CLIENT",
    "TEAM",
    "OTHER",
    "UNKNOWN",
)


# ── Purpose categories ─────────────────────────────────────────────────────

PURPOSES = (
    "DELIVERY_UPDATE",
    "TASK_ASSIGNMENT",
    "TASK_FOLLOWUP",
    "STATUS_UPDATE",
    "APPROVAL_REQUEST",
    "BLOCKER_ESCALATION",
    "OPPORTUNITY_RESPONSE",
    "TRAVEL_COORDINATION",
    "SCHEDULE_UPDATE",
    "APPRECIATION",
    "CORRECTIVE_MESSAGE",
    "GENERAL_PROFESSIONAL",
    # Weekday morning greeting and the weekly/monthly manager briefs.
    "MORNING_TEAM_GREETING",
    "WEEKLY_TEAM_BRIEF",
    "WEEKLY_REPORTEE_BRIEF",
    "MONTHLY_TEAM_REVIEW",
    "MONTHLY_REPORTEE_REVIEW",
)


# ── Greetings by recipient kind + firmness──────────────────────────────────

GREETINGS = {
    "team_professional": "Hello team,",
    "team_collaborative": "Hi everyone,",
    "team_urgent": "Team,",
    "team_informational": "Hello team,",
    "individual_professional": "Hello {name},",
    "individual_collaborative": "Hi {name},",
    "individual_urgent": "{name},",
    "individual_informational": "Hello {name},",
    "client_professional": "Hello {name},",
    "client_collaborative": "Dear {name},",
    "client_urgent": "{name},",
    "client_informational": "Hello {name},",
}


def greeting_for(recipient_type: str, tone: str, name: str = "") -> str:
    """Pick the greeting; falls back to a safe professional default."""
    rt = str(recipient_type or "OTHER").upper()
    tone_key = _nearest_tone(tone)
    if rt == "TEAM":
        return GREETINGS.get("team_" + tone_key) or GREETINGS["team_professional"]
    kind = "client" if rt == "CLIENT" else "individual"
    bare = GREETINGS.get(f"{kind}_{tone_key}")
    if not bare:
        bare = GREETINGS[kind + "_professional"]
    display = str(name or "").strip()
    if not display:
        if rt == "CLIENT":
            return bare.format(name=italic("team")) if "{name}" in bare else bare
        prefix = bare.split("{name}")[0].rstrip(", ")
        return prefix + "," if prefix.strip() else "Hello,"
    return bare.format(name=italic(display))


def _nearest_tone(tone):
    t = str(tone or "").lower()
    for candidate in (Tone.FIRM, Tone.URGENT, Tone.CORRECTIVE):
        if candidate in t:
            return "urgent" if candidate != Tone.CORRECTIVE else "professional"
    if "appreciat" in t:
        return "collaborative"
    if "inform" in t:
        return "informational"
    return "professional"


# ── Closings ───────────────────────────────────────────────────────────────

CLOSINGS = (
    "*Thanks*",
    "*Thank you*",
    "*Regards*",
    "*Kind regards*",
    "*Much appreciated*",
    "*Thanks for your support*",
    "*Looking forward*",
)

_TRUST_CLOSING = {"*Thanks*"}
_AUTHORITY_CLOSING = "*Regards*"
_URGENT_CLOSING = ("*Thanks*", "*Thank you*")


def closing_for(tone: str, relationship: str = "") -> str:
    """Choose a closing by tone/relationship; not always the same one."""
    t = str(tone or "").lower()
    if "urgent" in t or "firm" in t or "correct" in t:
        return _URGENT_CLOSING[0]
    if "appreciat" in t:
        return "*Much appreciated*"
    mapping = {}
    # deterministic pick: relationship + tone steer the closings pool
    pool = ["*Thanks*", "*Regards*"]
    return pool[int(len(t) + len(str(relationship))) % len(pool)]


# ── Formatting helpers (Teams + Viber compatible) ──────────────────────────

def italic(text: str) -> str:
    """*text* — italics render reliably in both Teams and Viber text."""
    t = str(text or "").strip()
    if not t:
        return t
    if t.startswith("*") and t.endswith("*"):
        return t
    return "*" + t + "*"


def bold(text: str) -> str:
    t = str(text or "").strip()
    if not t:
        return t
    if t.startswith("**") and t.endswith("**"):
        return t
    return "**" + t + "**"


def bold_underline(text: str) -> str:
    """__**text**__ — bold+underline is the policy's time/date treatment."""
    t = str(text or "").strip()
    if not t:
        return t
    return "__**" + t.strip("*") + "**__"


# ── Forbidden content ──────────────────────────────────────────────────────

_EMOJI = re_emoji = _re.compile(
    "[\U0001F300-\U0001FAFF\U00002600-\U000027BF\U0001F1E6-\U0001F1FF\U00002B00-\U00002BFF\U0000FE0F\u2705\u2611\u2728\u2714\u2716]"
)


def has_emoji(text) -> bool:
    return bool(_EMOJI.search(str(text or "")))


def has_bullet_list(text) -> bool:
    return any(
        line.strip().startswith(("- ", "* ", "• ", "·")) for line in str(text or "").splitlines()
    )


def has_numbered_list(text) -> bool:
    return bool(_re.search(r"(?m)^\s*\d{1,2}[.)]\s+\S", str(text or "")))


def split_sentences(text):
    """Cheap sentence splitter; keeps the terminal punctuation on each part."""
    return [p.strip() for p in _re.split(r"(?<=[.!?])\s+", str(text or "").strip()) if p.strip()]