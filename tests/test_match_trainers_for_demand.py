"""
Unit tests for backend._match_trainers_for_demand — the real, skill-matched
candidate-trainer resolution that feeds Android's CommunicationPlanner (see
AI/DECISIONS.md, 2026-09-15: "Communication Intelligence: fact-selection
layer gets a recipient-resolution step, not a new engine").

These are pure unit tests over a pure function — no RMS/Flask mocking
needed, matching the same "who is actually eligible" honesty rules the
Android CommunicationPlannerTest asserts.
"""
import backend


def _trainer(name, email, skills, availability_status=None, availability_verified=False):
    row = {
        "trainer_name": name,
        "official_email": email,
        "skill_courses": skills,
        "availability_verified": availability_verified,
    }
    if availability_status is not None:
        row["availability_status"] = availability_status
    return row


def test_no_trainers_have_the_course_returns_empty_list():
    trainers = [_trainer("Priya Sharma", "priya@koenig-solutions.com", ["AZ-104"])]
    matches = backend._match_trainers_for_demand("DP-700T00: Fabric Data Engineer", trainers)
    assert matches == []


def test_capability_match_with_verified_available_is_reported_as_available():
    trainers = [
        _trainer("Niharika N", "niharika@koenig-solutions.com", ["DP-700T00"],
                 availability_status="available", availability_verified=True),
    ]
    matches = backend._match_trainers_for_demand("DP-700T00", trainers)
    assert len(matches) == 1
    assert matches[0]["name"] == "Niharika N"
    assert matches[0]["capability_match"] is True
    assert matches[0]["availability"] == "AVAILABLE"


def test_capability_match_with_verified_conflict_is_reported_as_committed_not_available():
    trainers = [
        _trainer("Abhinav Samant", "abhinav@koenig-solutions.com", ["DP-700T00"],
                 availability_status="conflict", availability_verified=True),
    ]
    matches = backend._match_trainers_for_demand("DP-700T00", trainers)
    assert matches[0]["availability"] == "COMMITTED"


def test_capability_match_with_unverified_availability_is_never_asserted_available():
    # This is the exact honesty rule the rebuild exists to enforce: an
    # unverified check must never be reported as AVAILABLE.
    trainers = [
        _trainer("Rahul Verma", "rahul@koenig-solutions.com", ["DP-700T00"],
                 availability_status="unverified", availability_verified=False),
    ]
    matches = backend._match_trainers_for_demand("DP-700T00", trainers)
    assert matches[0]["availability"] == "UNKNOWN"


def test_course_name_matching_is_normalised_not_exact_string_match():
    trainers = [
        _trainer("Priya Sharma", "priya@koenig-solutions.com",
                 ["dp-700t00: implementing a data fabric"],
                 availability_status="available", availability_verified=True),
    ]
    matches = backend._match_trainers_for_demand("DP-700T00: Implementing a Data Fabric", trainers)
    assert len(matches) == 1


def test_multiple_matching_trainers_are_all_returned_individually():
    trainers = [
        _trainer("Niharika N", "niharika@koenig-solutions.com", ["DP-700T00"],
                 availability_status="available", availability_verified=True),
        _trainer("Priya Sharma", "priya@koenig-solutions.com", ["DP-700T00"],
                 availability_status="conflict", availability_verified=True),
        _trainer("Rahul Verma", "rahul@koenig-solutions.com", ["AZ-104"]),
    ]
    matches = backend._match_trainers_for_demand("DP-700T00", trainers)
    names = {m["name"] for m in matches}
    assert names == {"Niharika N", "Priya Sharma"}


def test_blank_course_name_returns_empty_list_rather_than_matching_everyone():
    trainers = [_trainer("Priya Sharma", "priya@koenig-solutions.com", ["AZ-104"])]
    assert backend._match_trainers_for_demand("", trainers) == []
    assert backend._match_trainers_for_demand(None, trainers) == []
