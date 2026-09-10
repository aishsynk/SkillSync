"""MessageValidator — mechanical, non-destructive checks before presenting text.

The validator only reports; it does not rewrite natural language. Known
mechanical problems that can be repaired safely (an over-long message) are
repaired once at the service layer, then re-validated.
"""

from __future__ import annotations

import re as _re

from domain.communication.models import ValidationResult

from . import policy


def validate(message: str) -> ValidationResult:
    result = ValidationResult(passed=True, issues=[])

    text = str(message or "").strip()
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
    if _re.search(r"\*\*([^*]*)\*\*.*\*\*", text) or text.count("**") % 2 != 0:
        # unbalanced bold markers — count must be even per whole message
        pass
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