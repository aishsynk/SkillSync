"""Weekly / monthly manager briefs — purpose policy, fact selection, validation.

Part of the one Communication Intelligence pipeline, not a second engine:

    verified facts (computed by the report builders, never here)
        -> select_brief_facts        (this module: choose, never calculate)
        -> CommunicationService._brief
        -> providers.generate        (Ollama -> Azure/OpenAI -> none)
        -> brief_issues              (this module: policy validation)
        -> one corrective retry
        -> deterministic fallback    (the caller's existing composed prose)

Four purposes, each a genuine intent rather than a template switch:

  WEEKLY_TEAM_BRIEF        what matters this week, what needs attention, one action
  WEEKLY_REPORTEE_BRIEF    this person's own week, their evidence, their next step
  MONTHLY_TEAM_REVIEW      what happened, what stands out, what needs attention next
  MONTHLY_REPORTEE_REVIEW  this person's month, evidence, development next step

and two real timeframes:

  current     status of the period in progress, actionable now
  period_end  retrospective for the closing period plus the handoff to the next

PERMANENT INVARIANT: low utilisation is not availability. Nothing here may
state or imply that anyone is free unless an authoritative availability fact
is present (verified leave/booking/schedule evidence), and the validator
rejects such a claim outright.
"""

from __future__ import annotations

import re
from dataclasses import dataclass
from typing import Any, Dict, List, Optional, Tuple

from . import providers
from .providers import DETERMINISTIC, Provenance

TEAM_WEEK = "WEEKLY_TEAM_BRIEF"
REPORTEE_WEEK = "WEEKLY_REPORTEE_BRIEF"
TEAM_MONTH = "MONTHLY_TEAM_REVIEW"
REPORTEE_MONTH = "MONTHLY_REPORTEE_REVIEW"

CURRENT = "current"
PERIOD_END = "period_end"


@dataclass(frozen=True)
class BriefPurpose:
    id: str
    scope: str        # "team" | "reportee"
    period: str       # "week" | "month"
    intent_current: str
    intent_period_end: str

    def intent(self, timeframe: str) -> str:
        return self.intent_period_end if timeframe == PERIOD_END else self.intent_current


BRIEF_PURPOSES: Dict[str, BriefPurpose] = {
    TEAM_WEEK: BriefPurpose(
        TEAM_WEEK, "team", "week",
        intent_current=("Tell the team what matters this week, what needs attention, and — only where the "
                        "facts justify it — one clear action. Forward-looking."),
        intent_period_end=("Close the week with the team: what actually happened, what stands out, and what "
                           "carries into next week. Retrospective, with a short handoff."),
    ),
    REPORTEE_WEEK: BriefPurpose(
        REPORTEE_WEEK, "reportee", "week",
        intent_current=("Write to this person about their own week: their delivery, their evidence, and one "
                        "clear next step for them. Never a team paragraph with a name inserted."),
        intent_period_end=("Close the week with this person: what they delivered, what the evidence shows, "
                           "and what they pick up next week."),
    ),
    TEAM_MONTH: BriefPurpose(
        TEAM_MONTH, "team", "month",
        intent_current=("Give the team the month so far: where delivery and utilisation stand, what needs "
                        "attention in the rest of the month, and appreciation only where facts support it."),
        intent_period_end=("Review the closing month with the team: what was delivered, what stands out, what "
                           "needs attention next month, and appreciation only where facts support it."),
    ),
    REPORTEE_MONTH: BriefPurpose(
        REPORTEE_MONTH, "reportee", "month",
        intent_current=("Write to this person about their month so far: delivery, utilisation, feedback and "
                        "certification evidence, and one development step that follows from it."),
        intent_period_end=("Review this person's closing month: what they delivered, what the evidence shows, "
                           "and the development step for next month."),
    ),
}

# Phrases the manager's voice never uses. Extends the greeting policy list with
# the brief-specific filler the operator called out.
BRIEF_BANNED_PHRASES = (
    "please act on your part today", "act on your part", "keep me posted", "keep pushing",
    "make it shine", "give 100%", "stay focused", "keep the momentum", "finish strong",
    "make today count", "have a productive day", "productive week", "crush your goals",
    "stay awesome", "onwards and upwards", "wishing everyone", "at the earliest",
    "needless to say", "as you are aware", "kindly do the needful",
)

# Claiming someone is free/available requires an authoritative availability fact.
_AVAILABILITY_CLAIM = re.compile(
    r"\b(\d+\s+(?:of\s+(?:us|you|the team)\s+)?(?:are|is)\s+(?:free|available|idle|unoccupied)"
    r"|(?:we|you|the team)\s+(?:have|has)\s+(?:free|spare|idle)\s+(?:capacity|bandwidth|time)"
    r"|(?:is|are|am)\s+(?:currently\s+)?(?:free|available)\s+(?:this|next|for)\b"
    r"|sitting\s+(?:idle|free)|nothing\s+booked|on\s+the\s+bench\s+and\s+free)\b",
    re.IGNORECASE,
)
_AUTHORITATIVE_AVAILABILITY_KEYS = (
    "verified_available_count", "verified_available_names", "authoritative_availability",
    "clear_days", "confirmed_days", "leave_days",
)

# Day-level urgency needs a day-level fact to stand on.
_DAY_URGENCY = re.compile(r"\b(today|by end of day|eod|right now|immediately|by tonight)\b", re.IGNORECASE)
_DAY_LEVEL_KEYS = ("earliest_uncovered_date", "critical_actions", "next_start_date", "deadline_date")

BRIEF_PROMPT = """You write one short internal message from a delivery manager to their own team or to one of their reportees, to be pasted into Microsoft Teams or Viber.

You are given the PURPOSE, the TIMEFRAME and the VERIFIED FACTS. Write only from those facts.

HARD RULES
- Never state a number, name, course, date or claim that is not in the verified facts.
- Never say or imply that anyone is free, available, idle or has spare capacity unless an availability fact explicitly says so. Low utilisation is NOT availability. Bench or low-utilisation counts are NOT "free people".
- Never invent urgency. Do not say "today", "immediately" or "end of day" unless a dated fact supports it.
- If a fact is absent, leave it out. Do not hedge, speculate or fill space.

VOICE
- The manager writing naturally: simple professional English, warm but direct, concise, human.
- Not HR language, not corporate filler, not motivational poster lines.
- Never use: "please act on your part today", "keep me posted", "keep pushing", "give 100%", "stay focused", "keep the momentum", "finish strong", "make today count", "have a productive day", "crush your goals", "make it shine", "kindly do the needful".
- A reportee message is about that person, never a team paragraph with a name dropped in.

SHAPE
- Greeting line, blank line, body, blank line, short closing line.
- Body: 2 to 4 sentences. Whole message under 700 characters.
- Vary openings and closings; do not reuse a recent message's opening.

FORMATTING (Viber/WhatsApp markers, used sparingly)
- *bold* for at most one key action. _italic_ for a name or a light closing. ~strike~ only to correct something.
- No markdown doubles (** or __), no emojis, no bullet points, no numbered lists, no headings, no labels.

OUTPUT
- Only the message. No code fences, no "Generated message:", no explanation."""


def select_brief_facts(raw: Dict[str, Any], purpose: str, timeframe: str) -> List[Tuple[str, Any]]:
    """Choose the facts this purpose may cite. Selection only — never calculation.

    Availability is passed through ONLY when the caller supplied an
    authoritative availability fact; bench/utilisation counts are relabelled so
    a model cannot read them as "people who are free".
    """
    raw = raw or {}
    spec = BRIEF_PURPOSES.get(purpose)
    if spec is None:
        return []

    def pick(*keys: str) -> List[Tuple[str, Any]]:
        out: List[Tuple[str, Any]] = []
        for k in keys:
            v = raw.get(k)
            if v is None or v == "" or v == [] or v == {}:
                continue
            out.append((k, v))
        return out

    facts: List[Tuple[str, Any]] = [("purpose", purpose), ("timeframe", timeframe)]
    if spec.scope == "team":
        facts += pick("headcount", "delivering", "total_batches", "total_pax",
                      "open_demand", "coverable_open", "earliest_uncovered_date", "earliest_uncovered_course",
                      "total_gaps", "at_risk", "avg_rating", "avg_utilisation", "top_performers",
                      "critical_actions", "month_label", "period_ref")
        # Bench is a workload band, not availability.
        if raw.get("bench") is not None:
            facts.append(("low_utilisation_band_count", raw.get("bench")))
    else:
        facts += pick("name", "first_name", "current_course", "upcoming_course", "batches_delivered",
                      "total_pax", "utilisation", "avg_rating", "rating_count", "positive_quote",
                      "constructive_quote", "cert_gap_courses", "cert_held_count", "qubits",
                      "opp_courses", "ti_score", "ti_tier", "month_label", "period_ref",
                      "development_step", "mock_summary")
    # Authoritative availability only.
    for key in _AUTHORITATIVE_AVAILABILITY_KEYS:
        if raw.get(key) is not None:
            facts.append((key, raw[key]))
    return facts


def _facts_text(facts: List[Tuple[str, Any]]) -> str:
    return "\n".join(f"- {k}: {v}" for k, v in facts)


def brief_prompt_user(purpose: str, timeframe: str, facts: List[Tuple[str, Any]],
                      recipient_name: str = "", recent: Optional[List[str]] = None,
                      manager_instruction: str = "", corrections: Optional[List[str]] = None) -> str:
    spec = BRIEF_PURPOSES[purpose]
    parts = [
        f"PURPOSE: {purpose}",
        f"TIMEFRAME: {timeframe} ({'retrospective for the closing period plus handoff' if timeframe == PERIOD_END else 'the period in progress, actionable now'})",
        f"INTENT: {spec.intent(timeframe)}",
        f"AUDIENCE: {'the whole team' if spec.scope == 'team' else 'one reportee: ' + (recipient_name or 'this person')}",
        "VERIFIED FACTS:",
        _facts_text(facts) or "- none",
    ]
    if manager_instruction.strip():
        parts.append("MANAGER INSTRUCTION (tone or emphasis only; it can never override or add facts): "
                     + manager_instruction.strip())
    if recent:
        parts.append("RECENT MESSAGES TO AVOID REPEATING:\n" + "\n".join(f"- {r}" for r in recent[:5]))
    if corrections:
        parts.append("Your previous attempt was rejected for: " + "; ".join(corrections)
                     + ". Write a new message that fixes every point. Output only the message.")
    return "\n\n".join(parts)


def _normalise(text: str) -> str:
    flat = re.sub(r"['’]", "", str(text or "").lower())
    return re.sub(r"\s+", " ", re.sub(r"[^a-z0-9]+", " ", flat)).strip()


def brief_issues(text: str, facts: List[Tuple[str, Any]], purpose: str,
                 recipient_name: str = "", recent: Optional[List[str]] = None) -> List[str]:
    """Policy validation for a brief from any source (model or deterministic)."""
    issues: List[str] = []
    body = str(text or "")
    if not body.strip():
        return ["empty message"]
    fact_keys = {k for k, _ in facts}
    flat = _normalise(body)

    if "```" in body:
        issues.append("contains a code fence")
    if re.search(r"^\s*(generated message|message|draft|brief)\s*:", body, re.I | re.M):
        issues.append("contains a label")
    if "**" in body or "__" in body:
        issues.append("uses markdown doubles instead of Viber markers")
    if re.search(r"[\U0001F300-\U0001FAFF☀-➿]", body):
        issues.append("contains emoji")
    if re.search(r"^\s*[-*•]\s+", body, re.M):
        issues.append("contains a bullet list")
    if len(body) > 700:
        issues.append(f"too long ({len(body)} characters)")
    if len(body) < 60:
        issues.append(f"too short ({len(body)} characters)")

    for phrase in BRIEF_BANNED_PHRASES:
        if _normalise(phrase) in flat:
            issues.append(f"banned phrase: {phrase}")

    match = _AVAILABILITY_CLAIM.search(body)
    if match and not any(k in fact_keys for k in _AUTHORITATIVE_AVAILABILITY_KEYS):
        issues.append(f"claims availability without an authoritative availability fact: '{match.group(0).strip()}'")

    urgency = _DAY_URGENCY.search(body)
    if urgency and not any(k in fact_keys for k in _DAY_LEVEL_KEYS):
        issues.append(f"day-level urgency without a dated fact: '{urgency.group(0).strip()}'")

    spec = BRIEF_PURPOSES.get(purpose)
    if spec and spec.scope == "reportee":
        first = (recipient_name or "").split()[0] if recipient_name else ""
        if first and first.lower() not in body.lower():
            issues.append("reportee message does not address the person")
        if re.search(r"\bhello team\b|\bhi team\b|\beveryone\b", body, re.I):
            issues.append("reportee message addresses the team")

    if recent:
        opening = _normalise(re.split(r"(?<=[.!?])\s|\n", body.strip(), maxsplit=1)[0])
        for r in recent:
            if opening and _normalise(re.split(r"(?<=[.!?])\s|\n", str(r).strip(), maxsplit=1)[0]) == opening:
                issues.append("repeats a recent opening")
                break
    return issues


def sanitize_brief(text: str) -> str:
    """Reduce model output to the message alone; keep Viber markers."""
    t = str(text or "").replace("\r\n", "\n").strip()
    t = re.sub(r"^```[a-zA-Z]*\s*|\s*```$", "", t).replace("```", "").strip()
    label = re.compile(r"^\s*(generated message|message|draft|brief|subject)\s*[:\-]?\s*$", re.I)
    lines = t.split("\n")
    while lines and label.match(lines[0]):
        lines.pop(0)
    t = "\n".join(lines)
    t = re.sub(r"^\s*(generated message|message|draft|brief)\s*:\s*", "", t, flags=re.I)
    t = re.sub(r"\*\*(.+?)\*\*", r"*\1*", t)
    t = re.sub(r"__(.+?)__", r"_\1_", t)
    t = t.strip()
    while len(t) > 1 and t[0] in "\"'“‘" and t[-1] in "\"'”’":
        t = t[1:-1].strip()
    return re.sub(r"\n{3,}", "\n\n", re.sub(r"[ \t]+", " ", t)).strip()


def compose_brief(
    purpose: str,
    raw_facts: Dict[str, Any],
    timeframe: str = CURRENT,
    recipient_name: str = "",
    manager_instruction: str = "",
    recent: Optional[List[str]] = None,
    deterministic_fallback: str = "",
) -> Tuple[str, Provenance, List[Tuple[str, Any]]]:
    """(message, provenance, facts_used). Model first, validated; one corrective
    retry for a fast-but-invalid reply; policy-clean deterministic prose last."""
    if purpose not in BRIEF_PURPOSES:
        return deterministic_fallback, Provenance(DETERMINISTIC, "", fallback_used=True, attempts=0), []
    timeframe = PERIOD_END if timeframe == PERIOD_END else CURRENT
    facts = select_brief_facts(raw_facts, purpose, timeframe)
    recent = [str(r) for r in (recent or []) if str(r).strip()][:5]

    corrections: Optional[List[str]] = None
    attempts = 0
    elapsed_ms = 0
    timeout_reason = ""
    tried: List[str] = []
    for _ in range(2):
        remaining = providers.INTERACTIVE_BUDGET_MS - elapsed_ms
        if remaining < 2000:
            break
        outcome = providers.generate(
            BRIEF_PROMPT,
            brief_prompt_user(purpose, timeframe, facts, recipient_name, recent, manager_instruction, corrections),
            temperature=0.6, max_tokens=320, budget_ms=remaining,
        )
        elapsed_ms += outcome.elapsed_ms
        timeout_reason = timeout_reason or outcome.timeout_reason
        tried += [p for p in outcome.tried if p not in tried]
        if outcome.tried:
            attempts += 1
        if not outcome.result:
            break
        clean = sanitize_brief(outcome.result.text)
        corrections = brief_issues(clean, facts, purpose, recipient_name, recent)
        if not corrections:
            return clean, Provenance(outcome.result.provider, outcome.result.model, fallback_used=False,
                                     attempts=attempts, elapsed_ms=elapsed_ms, timeout_reason=timeout_reason,
                                     attempted_providers=list(tried)), facts
        if outcome.timed_out:
            break
    # The deterministic composer is the fallback; a caller-supplied string is
    # only used when it passes the same policy check the model output must pass.
    fallback = compose_brief_deterministic(purpose, raw_facts, timeframe, recipient_name)
    if deterministic_fallback.strip() and not brief_issues(deterministic_fallback, facts, purpose, recipient_name):
        fallback = deterministic_fallback
    return fallback, Provenance(DETERMINISTIC, "", fallback_used=True, attempts=attempts,
                                elapsed_ms=elapsed_ms, timeout_reason=timeout_reason,
                                attempted_providers=list(tried)), facts


# ── deterministic brief composer ────────────────────────────────────────────
#
# Replaces the legacy `_compose_manager_message` template island for these four
# purposes. It selects from the same verified facts and writes short prose that
# obeys the invariants by construction: no availability claim without an
# authoritative fact, no day-level urgency without a dated fact, no stock
# filler, no markdown doubles, and a reportee message that names the person.
# Wording varies with a fact-derived seed so repeated sends are not identical.

def _italic(name: str) -> str:
    n = str(name or "").strip()
    return f"_{n}_" if n else ""


def _seed(facts: Dict[str, Any], purpose: str, timeframe: str) -> int:
    basis = f"{purpose}|{timeframe}|{facts.get('period_ref', '')}|{facts.get('month_label', '')}|{facts.get('name', '')}"
    return sum(ord(c) for c in basis)


def _pick(options: List[str], seed: int, salt: int = 0) -> str:
    return options[(seed + salt) % len(options)]


def compose_brief_deterministic(purpose: str, raw_facts: Dict[str, Any], timeframe: str = CURRENT,
                                recipient_name: str = "") -> str:
    """Policy-clean prose from verified facts, for when no model is available."""
    spec = BRIEF_PURPOSES.get(purpose)
    if spec is None:
        return ""
    f = dict(raw_facts or {})
    seed = _seed(f, purpose, timeframe)
    ending = timeframe == PERIOD_END
    period = "week" if spec.period == "week" else "month"
    body: List[str] = []

    if spec.scope == "team":
        greeting = _pick(["Hello team,", "Hi all,", "Hi team,"], seed)
        batches, pax = f.get("total_batches"), f.get("total_pax")
        rating, gaps = f.get("avg_rating"), f.get("total_gaps")
        open_demand, coverable = f.get("open_demand"), f.get("coverable_open")
        top = [t for t in (f.get("top_performers") or []) if t]
        if ending:
            if batches:
                body.append(_pick([
                    f"We closed the {period} with {batches} {'batch' if batches == 1 else 'batches'} delivered"
                    + (f" to {pax} participants." if pax else "."),
                    f"That is {batches} {'batch' if batches == 1 else 'batches'} delivered this {period}"
                    + (f", {pax} participants in total." if pax else "."),
                ], seed, 1))
            if rating:
                body.append(f"Learner feedback averaged {rating} out of 5.")
            if top:
                body.append(_pick([
                    f"Thanks to {_italic(top[0])} for carrying a heavy load well.",
                    f"Credit to {_italic(top[0])} for the delivery this {period}.",
                ], seed, 2))
            if open_demand:
                body.append(f"{open_demand} {'batch' if open_demand == 1 else 'batches'} carry into next {period}"
                            + (f", {coverable} of which we can already teach." if coverable else "."))
            if gaps:
                body.append(f"{gaps} certification {'gap' if gaps == 1 else 'gaps'} are still open across the team.")
        else:
            course = str(f.get("earliest_uncovered_course") or "").strip()
            when = f.get("earliest_uncovered_date")
            # A month in progress leads with what has landed so far; a week in
            # progress leads with what still needs staffing.
            if period == "month" and batches:
                body.append(f"{batches} {'batch' if batches == 1 else 'batches'} delivered so far this month"
                            + (f" to {pax} participants." if pax else "."))
            if open_demand:
                line = (f"{open_demand} {'batch' if open_demand == 1 else 'batches'} on the board "
                        f"{'is' if open_demand == 1 else 'are'} still unstaffed")
                if coverable:
                    line += f", {coverable} of which this team can already teach"
                body.append(line + ".")
                if course and when:
                    body.append(f"The first is {course} starting {when}.")
                    body.append(f"*If you can take {course}, tell me and I will set it up.*")
                else:
                    body.append("*If you can pick one of these up, tell me and I will set it up.*")
            elif batches:
                body.append(f"{batches} {'batch' if batches == 1 else 'batches'} are in delivery this {period} "
                            "and nothing is unstaffed.")
            if gaps:
                body.append(f"{gaps} certification {'gap' if gaps == 1 else 'gaps'} are open; "
                            f"booking one this {period} helps our coverage.")
            if rating and not open_demand:
                body.append(f"Learner feedback is averaging {rating} out of 5.")
        closing = _pick(
            [f"_Thanks for the work this {period}._", "_Thanks, all._", f"_Good {period} so far._", "_Appreciated._"]
            if not ending else
            [f"_Thanks for a solid {period}._", f"_Good {period}, everyone._", "_Appreciated, all._"], seed, 3)
    else:
        first = (recipient_name or f.get("first_name") or str(f.get("name") or "").split(" ")[0] or "").strip()
        first = first.split(" ")[0]
        greeting = f"Hi {first}," if first else "Hi,"
        delivered, pax = f.get("batches_delivered"), f.get("total_pax")
        util, rating, count = f.get("utilisation"), f.get("avg_rating"), f.get("rating_count")
        gap_courses = [str(c).strip() for c in (f.get("cert_gap_courses") or []) if c]
        current_course = str(f.get("current_course") or "").strip()
        next_course = str(f.get("upcoming_course") or "").strip()
        if delivered:
            body.append(f"{delivered} {'batch' if delivered == 1 else 'batches'} delivered this {period}"
                        + (f" to {pax} participants." if pax else "."))
        elif current_course:
            body.append(f"You are on {current_course} this {period}.")
        if rating:
            body.append(f"Learner feedback is averaging {rating} out of 5"
                        + (f" across {count} responses." if count else "."))
        if util is not None:
            body.append(f"Your measured utilisation is {util} percent.")
        if gap_courses:
            body.append(_pick([
                f"*{gap_courses[0]} is still without the certification on file, so booking that exam is the next step.*",
                f"*Worth booking the {gap_courses[0]} certification next; it is the one gap on your record.*",
            ], seed, 1))
        elif next_course:
            body.append(f"*{next_course} is next for you, so keep the preparation moving.*")
        if not body:
            body.append(f"Nothing is flagged on your record this {period}.")
        closing = _pick(["_Thanks._", f"_Good {period}._", "_Appreciated._", "_Well done._"], seed, 2)

    text = f"{greeting}\n\n{' '.join(b for b in body if b).strip()}\n\n{closing}"
    return re.sub(r"\n{3,}", "\n\n", re.sub(r"[ \t]+", " ", text)).strip()
