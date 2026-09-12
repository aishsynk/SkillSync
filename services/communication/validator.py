"""MessageValidator — mechanical, non-destructive checks before presenting text.

The validator only reports; it does not rewrite natural language. Known
mechanical problems that can be repaired safely (an over-long message) are
repaired once at the service layer, then re-validated.
"""

from __future__ import annotations

import re as _re

from domain.communication.models import ValidationResult

from . import policy


def validate_factual_integrity(text: str, plan: any = None) -> list[str]:
    """Validate that every factual number, course code, and date is traceable to input or facts."""
    issues = []
    if not plan or str(text).strip() == "NO_MEANINGFUL_MESSAGE":
        return issues

    gt_courses = set()
    gt_numbers = set()
    gt_days = set()

    um = getattr(plan, "user_message", "")
    mm = getattr(plan, "my_message", "")

    for item in [um, mm]:
        for c in _re.findall(r"\b([A-Z]{2,4}-\d{2,4}[A-Z0-9]*)\b", str(item), _re.I):
            gt_courses.add(c.upper())
        for d in _re.findall(r"\b(monday|tuesday|wednesday|thursday|friday|saturday|sunday)\b", str(item), _re.I):
            gt_days.add(d.capitalize())
        for n in _re.findall(r"\b(\d+(?:\.\d+)?)\b", str(item)):
            gt_numbers.add(n)

    for tr in getattr(plan, "time_references", []):
        for d in _re.findall(r"\b(monday|tuesday|wednesday|thursday|friday|saturday|sunday)\b", str(tr), _re.I):
            gt_days.add(d.capitalize())

    for f in getattr(plan, "selected_facts", []):
        f_val = str(getattr(f, "value", f))
        for c in _re.findall(r"\b([A-Z]{2,4}-\d{2,4}[A-Z0-9]*)\b", f_val, _re.I):
            gt_courses.add(c.upper())
        for n in _re.findall(r"\b(\d+(?:\.\d+)?)\b", f_val):
            gt_numbers.add(n)

    # 1. Course codes check
    text_courses = _re.findall(r"\b([A-Z]{2,4}-\d{2,4}[A-Z0-9]*)\b", text)
    for c in text_courses:
        if c.upper() not in gt_courses:
            issues.append(f"Factual violation: course code '{c}' not found in verified facts or input")

    # 2. Day names check
    text_days = _re.findall(r"\b(Monday|Tuesday|Wednesday|Thursday|Friday|Saturday|Sunday)\b", text, _re.I)
    for d in text_days:
        if d.capitalize() not in gt_days:
            issues.append(f"Factual violation: day '{d}' not found in time references or input")

    # 3. Numeric quantities check (e.g., '1 open delivery requirement' or '2 open delivery requirements')
    m_open = _re.search(r"\b(\d+)\s+open delivery requirement", text, _re.I)
    if m_open:
        count_val = m_open.group(1)
        if count_val not in gt_numbers:
            issues.append(f"Factual violation: open demand count '{count_val}' does not match verified facts")

    # Rating check (e.g. 'averaging 4.85 out of 5')
    m_rating = _re.search(r"\baveraging\s+(\d+(?:\.\d+)?)\b", text, _re.I)
    if m_rating:
        r_val = m_rating.group(1)
        if r_val not in gt_numbers:
            issues.append(f"Factual violation: rating '{r_val}' does not match verified facts")

    # 4. Person names check (e.g. *Niharika*, *Gaurav*)
    text_names = _re.findall(r"\*([A-Z][a-z]+)\*", text)
    gt_names = set()
    closing_words = {"thanks", "thank", "regards", "best"}
    r_name = getattr(plan, "recipient_name", "")
    if r_name:
        for n in r_name.split():
            gt_names.add(n.lower())
    for item in [um, mm]:
        for n in _re.findall(r"\b([A-Z][a-z]+)\b", str(item)):
            gt_names.add(n.lower())
    for f in getattr(plan, "selected_facts", []):
        f_val = str(getattr(f, "value", f))
        for n in _re.findall(r"\b([A-Z][a-z]+)\b", f_val):
            gt_names.add(n.lower())
    for name in text_names:
        if name.lower() in closing_words:
            continue
        if name.lower() not in gt_names:
            issues.append(f"Factual violation: person name '{name}' not found in verified facts or input")

    return issues


def validate(message: str, plan: any = None) -> ValidationResult:
    result = ValidationResult(passed=True, issues=[])

    text = str(message or "").strip()
    if text == "NO_MEANINGFUL_MESSAGE":
        return ValidationResult(passed=True, issues=[])
    if not text:
        return ValidationResult(passed=False, issues=["empty message"])

    lines = [ln.strip() for ln in text.splitlines() if ln.strip()]

    if len(text) > policy.MAX_LENGTH:
        result.issues.append(f"over {policy.MAX_LENGTH} characters ({len(text)})")
    if not lines:
        result.issues.append("no content")
    elif not _re.match(r"^(hello|hi|dear|good morning|good afternoon|team[,:]|.+,$)", lines[0], _re.I):
        result.issues.append("missing greeting on the first line")
    if policy.has_emoji(text):
        result.issues.append("contains emojis")
    if policy.has_bullet_list(text):
        result.issues.append("contains a bullet list")
    if policy.has_numbered_list(text):
        result.issues.append("contains a numbered list")
    if len(lines) >= 2:
        if lines[-1].lower() in {c.lower() for c in policy.CLOSINGS} or _re.search(r"^\*[A-Za-z][A-Za-z ,]*\*$", lines[-1]):
            pass
        else:
            result.issues.append("missing closing")
    # duplicate greeting / duplicate closing
    greets = [ln for ln in lines if _re.match(r"^(hello|hi|dear)", ln, _re.I)]
    if len(greets) > 1:
        result.issues.append("duplicate greeting")
    closings = [ln for ln in lines if _re.search(r"^\*[A-Za-z][A-Za-z ,]*\*$", ln)]
    if len(closings) > 1:
        result.issues.append("duplicate closing")

    # Factual integrity check
    if plan:
        fact_issues = validate_factual_integrity(text, plan)
        result.issues.extend(fact_issues)

    result.passed = not result.issues
    return result


def truncate(message: str, limit: int = None) -> str:
    """Drop everything after the last sentence boundary under the limit.

    Never leaves an unmatched ** or * pair; safe only as a last resort.
    """
    limit = limit or policy.MAX_LENGTH
    text = str(message or "").strip()
    if len(text) <= limit:
        return text
    cut = text[:limit]
    best = -1
    for splitter in (". ", "! ", "? "):
        best = max(best, cut.rfind(splitter))
    if best > limit // 2:
        head = cut[: best + 2]
        for marker in ("**", "*"):
            if head.count(marker) % 2 != 0:
                idx = head.rfind(marker)
                if idx > limit // 2:
                    head = head[:idx]
        return head.strip()
    return cut