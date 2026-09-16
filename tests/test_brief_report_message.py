"""Regression coverage for the release blocker: weekly/monthly report
messages (HR Monthly, Weekly Report) must be composed through the same
Communication Intelligence policy as the interactive path, never through
the unvalidated legacy `_compose_manager_message` template island.

Reuses the project's own banned-phrase list and validator (briefs.py)
rather than duplicating either.
"""
import pytest

from backend import _brief_report_message, _compose_manager_message
from services.communication.briefs import BRIEF_BANNED_PHRASES, brief_issues, select_brief_facts, TEAM_MONTH


def _contains_banned_phrase(text: str) -> str | None:
    lowered = text.lower()
    for phrase in BRIEF_BANNED_PHRASES:
        if phrase in lowered:
            return phrase
    return None


# The exact team facts shape that used to trigger the banned closing line in
# _compose_manager_message ("Please act on your part today and keep me
# posted."): open demand present, so the old "review" branch is False.
_TEAM_FACTS_WITH_OPEN_DEMAND = {
    "headcount": 5, "delivering": 3, "total_pax": 40, "total_batches": 6,
    "open_demand": 2, "coverable_open": 1, "total_gaps": 1, "at_risk": 0,
    "bench": 2,  # workload band, never rendered as "free"
    "avg_rating": 4.4, "top_performers": ["Niharika Rao"],
    "period_key": "2026-09", "month_label": "September 2026",
}

_REPORTEE_FACTS_WITH_GAP = {
    "first": "Priya", "email": "priya@koenig-solutions.com",
    "util": 68, "current_course": "AZ-104T00", "pax": 12,
    "rating": 4.1, "rating_count": 6,
    "cert_gap_courses": ["AZ-104"], "bench": False,
    "period_key": "2026-09", "month_label": "September 2026",
    "batches_done": 4,
}


@pytest.mark.parametrize("scope,cadence,facts,recipient", [
    ("team", "monthly", _TEAM_FACTS_WITH_OPEN_DEMAND, ""),
    ("team", "monthend", _TEAM_FACTS_WITH_OPEN_DEMAND, ""),
    ("team", "weekly", _TEAM_FACTS_WITH_OPEN_DEMAND, ""),
    ("team", "weekend", _TEAM_FACTS_WITH_OPEN_DEMAND, ""),
    ("reportee", "monthly", _REPORTEE_FACTS_WITH_GAP, "Priya Sharma"),
    ("reportee", "monthend", _REPORTEE_FACTS_WITH_GAP, "Priya Sharma"),
    ("reportee", "weekly", _REPORTEE_FACTS_WITH_GAP, "Priya Sharma"),
    ("reportee", "weekend", _REPORTEE_FACTS_WITH_GAP, "Priya Sharma"),
])
def test_brief_report_message_never_contains_a_banned_phrase(scope, cadence, facts, recipient):
    text = _brief_report_message(scope, cadence, facts, recipient_name=recipient)
    hit = _contains_banned_phrase(text)
    assert hit is None, f"{scope}/{cadence} message contains banned phrase: {hit!r}\n{text}"


def test_legacy_compose_manager_message_still_carries_the_banned_phrase():
    """Proves the regression this fix closes: the raw legacy composer, called
    directly and unvalidated (as every report route used to do), really did
    emit the banned closing line for this exact fact shape."""
    legacy_text = _compose_manager_message("team", "monthly", _TEAM_FACTS_WITH_OPEN_DEMAND)
    assert "keep me posted" in legacy_text.lower() or "act on your part" in legacy_text.lower()


def test_report_route_no_longer_calls_the_legacy_composer_directly():
    """Every weekly/monthly team/reportee call site must go through
    _brief_report_message. Only its own internal, policy-checked fallback
    may call the legacy composer."""
    import inspect
    import backend
    source = inspect.getsource(backend)
    # Every call to _compose_manager_message( outside its own definition and
    # the one guarded fallback inside _brief_report_message.
    calls = [i for i in range(len(source)) if source.startswith("_compose_manager_message(", i)]
    # def _compose_manager_message( is not a call; filter those out.
    call_sites = [i for i in calls if source[max(0, i - 4):i] != "def "]
    assert len(call_sites) == 1, (
        f"Expected exactly one _compose_manager_message( call site (the "
        f"validated fallback inside _brief_report_message), found {len(call_sites)}."
    )


def test_brief_report_message_passes_the_shared_validator():
    """The composed text must independently pass brief_issues — the same
    check the interactive model-generated path is held to."""
    text = _brief_report_message("team", "monthly", _TEAM_FACTS_WITH_OPEN_DEMAND)
    facts = select_brief_facts(
        {"headcount": 5, "delivering": 3, "total_batches": 6, "total_pax": 40,
         "open_demand": 2, "coverable_open": 1, "total_gaps": 1, "bench": 2,
         "avg_rating": 4.4, "top_performers": ["Niharika Rao"],
         "period_ref": "2026-09", "month_label": "September 2026"},
        TEAM_MONTH, "current",
    )
    assert not brief_issues(text, facts, TEAM_MONTH)


def test_brief_report_message_never_claims_availability_from_bench_count():
    """The permanent invariant: low utilisation / bench counts are workload
    bands, never an availability claim, for report messages too."""
    facts = dict(_TEAM_FACTS_WITH_OPEN_DEMAND)
    facts["bench"] = 3  # 3 people on a low-utilisation band, no authoritative
    # availability fact anywhere in these facts.
    for cadence in ("weekly", "monthly", "weekend", "monthend"):
        text = _brief_report_message("team", cadence, facts).lower()
        assert "are free" not in text and "is free" not in text and "available" not in text

    reportee_facts = dict(_REPORTEE_FACTS_WITH_GAP)
    reportee_facts["bench"] = True
    for cadence in ("weekly", "monthly", "weekend", "monthend"):
        text = _brief_report_message("reportee", cadence, reportee_facts, "Priya Sharma").lower()
        assert "you are free" not in text and "available" not in text
