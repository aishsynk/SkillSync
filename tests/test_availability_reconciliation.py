import unittest
from unittest import mock
import backend


def base_candidate(**overrides):
    c = {
        "trainer_name": "Test Trainer",
        "trainer_email": "trainer@koenig-solutions.com",
        "match": 90,
        "availability_status": "available",
        "suitability_score": 80,
        "suitability_components": {
            "skill": 90, "readiness": 70, "availability": 100,
            "utilization": 60, "feedback": 100, "language": 100,
            "location": 100, "certification": 100,
        },
    }
    c.update(overrides)
    return c


class AvailabilityScoreTableTests(unittest.TestCase):
    """The full status vocabulary this codebase produces (_availability_evidence's
    available/unverified/conflict and real_availability's richer
    available/available_with_conflicts/partially_available/unavailable/unknown)
    must all resolve through the same table, so no code path can invent its own
    score for a status the other path already defined."""

    def test_available_scores_100(self):
        self.assertEqual(100, backend._AVAILABILITY_SCORE["available"])

    def test_unavailable_and_conflict_score_zero(self):
        self.assertEqual(0, backend._AVAILABILITY_SCORE["unavailable"])
        self.assertEqual(0, backend._AVAILABILITY_SCORE["conflict"])

    def test_unknown_and_unverified_never_score_100(self):
        """Core invariant: insufficient data must never resolve to a perfect score."""
        self.assertNotEqual(100, backend._AVAILABILITY_SCORE["unknown"])
        self.assertNotEqual(100, backend._AVAILABILITY_SCORE["unverified"])
        self.assertEqual(
            backend._AVAILABILITY_SCORE["unknown"],
            backend._AVAILABILITY_SCORE["unverified"],
        )

    def test_partial_states_score_between_unavailable_and_available(self):
        self.assertTrue(
            0 < backend._AVAILABILITY_SCORE["partially_available"] < 100
        )
        self.assertTrue(
            0 < backend._AVAILABILITY_SCORE["available_with_conflicts"] < 100
        )


class ReconcileAvailabilityTests(unittest.TestCase):
    """Regression coverage for the exact production contradiction: a candidate
    whose early-pass evidence found "no conflict" (scored Avail 100) but whose
    later, authoritative real_availability verdict says the opposite must end
    up numerically consistent with what is shown on screen."""

    def test_regression_unknown_verdict_after_available_evidence_is_not_100(self):
        """This is the exact screenshot bug: 'Availability unknown' next to
        'Avail 100'. Before the fix, suitability_components["availability"]
        and suitability_status were frozen from the earlier _availability_evidence
        pass and never learned about the later, authoritative real_availability
        verdict of "unknown"."""
        cand = base_candidate()  # starts as if _availability_evidence said "available" -> 100
        verdict = {"status": "unknown", "reason": "no availability record for this course"}

        backend.reconcile_availability(cand, verdict)

        self.assertEqual("unknown", cand["availability_status"])
        self.assertNotEqual(100, cand["suitability_components"]["availability"])
        self.assertEqual(45, cand["suitability_components"]["availability"])

    def test_unavailable_verdict_zeroes_the_availability_component(self):
        cand = base_candidate()
        verdict = {"status": "unavailable", "reason": "not free on these dates"}

        backend.reconcile_availability(cand, verdict)

        self.assertEqual("unavailable", cand["availability_status"])
        self.assertEqual(0, cand["suitability_components"]["availability"])

    def test_suitability_score_moves_with_the_reconciled_component(self):
        cand = base_candidate()
        original_total = cand["suitability_score"]
        verdict = {"status": "unavailable", "reason": "on approved leave"}

        backend.reconcile_availability(cand, verdict)

        # availability weight is 0.15; component dropped 100 -> 0, so total
        # must drop by roughly 15 points, not stay frozen at the old number.
        self.assertLess(cand["suitability_score"], original_total)
        expected_delta = round((0 - 100) * backend._SUITABILITY_WEIGHTS["availability"])
        self.assertEqual(original_total + expected_delta, cand["suitability_score"])

    def test_matching_status_is_a_no_op(self):
        cand = base_candidate(availability_status="available")
        verdict = {"status": "available", "reason": ""}

        backend.reconcile_availability(cand, verdict)

        self.assertEqual(100, cand["suitability_components"]["availability"])
        self.assertEqual(80, cand["suitability_score"])

    def test_missing_suitability_components_does_not_raise(self):
        cand = {"trainer_name": "No Score Yet"}
        verdict = {"status": "unknown"}

        backend.reconcile_availability(cand, verdict)  # must not raise

        self.assertEqual("unknown", cand["availability_status"])

    def test_regression_availability_verified_flag_is_also_reconciled(self):
        """Second instance of the same bug class as the score contradiction:
        availability_verified is a real, consumed confidence flag
        (_match_trainers_for_demand and _capacity_plan_from_allocation both
        gate on it before calling a candidate AVAILABLE/COMMITTED rather than
        UNKNOWN). It must not stay stale at True from the earlier
        _availability_evidence pass once the authoritative real_availability
        verdict says "unknown" -- that would let an unresolved trainer be
        reported as a verified, countable AVAILABLE candidate in capacity
        planning."""
        cand = base_candidate(availability_status="available")
        cand["availability_verified"] = True  # stale from the earlier evidence pass
        verdict = {"status": "unknown", "reason": "no availability record for this course"}

        backend.reconcile_availability(cand, verdict)

        self.assertEqual("unknown", cand["availability_status"])
        self.assertFalse(cand["availability_verified"])

    def test_availability_verified_true_for_every_conclusive_status(self):
        for status in ("available", "available_with_conflicts", "partially_available",
                       "conflict", "unavailable"):
            cand = base_candidate()
            backend.reconcile_availability(cand, {"status": status})
            self.assertTrue(cand["availability_verified"], f"status={status!r} should be verified")

    def test_availability_verified_false_for_inconclusive_status(self):
        for status in ("unknown", "unverified"):
            cand = base_candidate()
            backend.reconcile_availability(cand, {"status": status})
            self.assertFalse(cand["availability_verified"], f"status={status!r} should not be verified")


class AvailabilityVerdictVocabularyTests(unittest.TestCase):
    """availability_verdict's full output vocabulary must be represented in
    _AVAILABILITY_SCORE -- an unmapped status would silently fall through to
    the default 45, hiding a real conflict or a real "available" as a
    lukewarm mid-score instead."""

    def test_every_availability_verdict_status_is_mapped(self):
        import datetime
        day = datetime.date(2026, 9, 1)
        days = [day]

        cases = [
            (None, {}, "unknown"),                                    # no row
            (set(), {}, "unavailable"),                                # row, no free days
            ({day}, {}, "available"),                                  # free
            ({day}, {"tentative_dates": {day}}, "available_with_conflicts"),
            ({day}, {"leave_dates": {day}}, "unavailable"),
            (set(), {}, "unavailable"),
        ]
        for free_dates, schedule, expected_status in cases:
            verdict = backend.availability_verdict(free_dates, schedule, days)
            self.assertEqual(expected_status, verdict["status"])
            self.assertIn(
                verdict["status"], backend._AVAILABILITY_SCORE,
                f"status {verdict['status']!r} from availability_verdict has no score mapping",
            )


class EndToEndEnrichDemandTests(unittest.TestCase):
    """Exercises the real production entry point,
    enrich_demand_with_availability, not just the reconcile_availability
    helper in isolation -- proving the fix holds through the actual code
    path the demand board (and therefore Android's Recommended Trainers
    screen) consumes, per the instruction not to stop at a unit test on the
    helper alone."""

    def test_candidate_with_no_free_schedule_row_is_never_available_100(self):
        """Reproduces the exact screenshot scenario end-to-end: a candidate
        whose early-pass evidence found "available" (scored 100, as
        _availability_evidence's "no recorded conflict" branch would) but
        for whom RMS key 171 (course-specific free schedule) returns no row
        at all -- the "Availability unknown" case."""
        demand = [{
            "course_name": "AZ-104T00",
            "start_date": "2026-09-01",
            "end_date": "2026-09-05",
            "candidates": [{
                "trainer_name": "Bharatkumar Ramesh Bhojwani",
                "availability_status": "available",
                "availability_verified": True,
                "suitability_score": 80,
                "suitability_components": {
                    "skill": 90, "readiness": 70, "availability": 100,
                    "utilization": 60, "feedback": 100, "language": 100,
                    "location": 100, "certification": 100,
                },
            }],
        }]

        with mock.patch.object(backend, "_free_schedule", return_value=({}, "")), \
             mock.patch.object(backend, "_course_catalogue_index", return_value={}):
            backend.enrich_demand_with_availability(demand)

        cand = demand[0]["candidates"][0]
        self.assertEqual("unknown", cand["real_availability"]["status"])
        # The two fields Android actually renders must agree: the status the
        # chip reads, and the score the "Avail N" line reads.
        self.assertEqual("unknown", cand["availability_status"])
        self.assertNotEqual(100, cand["suitability_components"]["availability"])
        self.assertFalse(cand["availability_verified"])

    def test_candidate_with_a_free_schedule_row_reconciles_to_available(self):
        demand = [{
            "course_name": "AZ-104T00",
            "start_date": "2026-09-01",
            "end_date": "2026-09-05",
            "candidates": [{
                "trainer_name": "Available Trainer",
                "availability_status": "unverified",
                "suitability_score": 60,
                "suitability_components": {
                    "skill": 90, "readiness": 70, "availability": 45,
                    "utilization": 60, "feedback": 100, "language": 100,
                    "location": 100, "certification": 100,
                },
            }],
        }]
        pool = {"available trainer": {"free_dates": {backend._parse_date("2026-09-01"),
                                                       backend._parse_date("2026-09-02"),
                                                       backend._parse_date("2026-09-03"),
                                                       backend._parse_date("2026-09-04"),
                                                       backend._parse_date("2026-09-05")}}}

        with mock.patch.object(backend, "_free_schedule", return_value=(pool, "")), \
             mock.patch.object(backend, "_course_catalogue_index", return_value={}):
            backend.enrich_demand_with_availability(demand)

        cand = demand[0]["candidates"][0]
        self.assertEqual("available", cand["real_availability"]["status"])
        self.assertEqual("available", cand["availability_status"])
        self.assertEqual(100, cand["suitability_components"]["availability"])
        self.assertTrue(cand["availability_verified"])


if __name__ == "__main__":
    unittest.main()
