"""
Phase 1b and Phase 2: certification intelligence, operational-only SC data,
off-date gating, and the candidate route.

As with the availability tests, each case pins a stated business rule rather
than an implementation detail.
"""
import json
import unittest
from datetime import date, timedelta
from unittest import mock

import backend


POLICY = {
    "az 305t00 designing microsoft azure infrastructure solutions":
        {"required": True, "vendor": "Microsoft"},
    "introduction to project management": {"required": False, "vendor": "Koenig"},
}


class CertificationIntelligence(unittest.TestCase):

    def test_a_course_needing_no_exam_is_never_a_gap(self):
        # 9,561 of 11,007 catalogue courses require no exam. Reporting gaps on
        # those is the noise that teaches managers to ignore the signal.
        v = backend.certification_verdict("Introduction to Project Management", [], POLICY)
        self.assertFalse(v["exam_required"])
        self.assertFalse(v["gap"])

    def test_teaching_an_exam_course_without_the_certification_is_a_gap(self):
        v = backend.certification_verdict(
            "AZ-305T00: Designing Microsoft Azure Infrastructure Solutions", [], POLICY)
        self.assertTrue(v["exam_required"])
        self.assertTrue(v["gap"])

    def test_holding_the_certification_closes_the_gap(self):
        v = backend.certification_verdict(
            "AZ-305T00: Designing Microsoft Azure Infrastructure Solutions",
            ["AZ-305T00: Designing Microsoft Azure Infrastructure Solutions"], POLICY)
        self.assertFalse(v["gap"])

    def test_exam_identity_is_always_labelled_inferred(self):
        # Key 215 turned out to be a mutation, so the exam name can only be
        # inferred from delivery history and must never look authoritative.
        hints = backend._exam_hints([
            {"CourseName": "AZ-305T00: Designing Microsoft Azure Infrastructure Solutions",
             "Exam": "Microsoft Certified: Azure Solutions Architect Expert"},
        ])
        v = backend.certification_verdict(
            "AZ-305T00: Designing Microsoft Azure Infrastructure Solutions", [], POLICY, hints)
        self.assertEqual("Microsoft Certified: Azure Solutions Architect Expert", v["exam_name"])
        self.assertEqual("inferred_from_delivery_history", v["exam_source"])

    def test_a_course_absent_from_the_policy_is_unknown_not_no_gap(self):
        # The exam-policy catalogue (key 213) uses different course names from
        # the delivery catalogue, so unmatched courses are common. Returning
        # False here would silently under-report real certification risk.
        v = backend.certification_verdict("Some Course Not In The Policy", [], POLICY)
        self.assertIsNone(v["exam_required"])
        self.assertFalse(v["policy_known"])
        self.assertFalse(v["gap"], "an unknown requirement must not assert a gap")

    def test_a_known_requirement_still_asserts_a_gap(self):
        v = backend.certification_verdict(
            "AZ-305T00: Designing Microsoft Azure Infrastructure Solutions", [], POLICY)
        self.assertTrue(v["policy_known"])
        self.assertTrue(v["gap"])

    def test_an_unknown_exam_says_unknown_rather_than_guessing(self):
        v = backend.certification_verdict(
            "AZ-305T00: Designing Microsoft Azure Infrastructure Solutions", [], POLICY, {})
        self.assertEqual("", v["exam_name"])
        self.assertEqual("unknown", v["exam_source"])

    def test_priority_is_driven_by_blocked_demand_not_by_value(self):
        a = backend.certification_verdict("AZ-305T00: Designing Microsoft Azure Infrastructure Solutions", [], POLICY)
        b = dict(a, course="Other Exam Course", gap=True)
        ranked = backend.certification_priority(
            [a, b],
            demand_by_course={"az 305t00 designing microsoft azure infrastructure solutions": 4},
            blocked_counts={"az 305t00 designing microsoft azure infrastructure solutions": 2},
        )
        self.assertEqual("AZ-305T00: Designing Microsoft Azure Infrastructure Solutions",
                         ranked[0]["course"])
        self.assertEqual(14, ranked[0]["priority_score"])
        for r in ranked:
            self.assertNotIn("fee", json.dumps(r).lower())

    def test_only_real_gaps_are_ranked(self):
        clean = backend.certification_verdict("Introduction to Project Management", [], POLICY)
        self.assertEqual([], backend.certification_priority([clean]))


class OperationalDataOnly(unittest.TestCase):
    """Revenue is out of scope; the boundary is enforced, not merely intended."""

    ROWS = [{
        "CourseName": "Azure Fundamentals", "CSM": "Cherry",
        "AssignmentId": "266940", "SCId": "395503",
        "SCCreatedDate": "11 Aug 2026", "Total Fee": 84370.0, "Currency": "INR",
    }]

    def test_fee_and_currency_never_leave_the_backend(self):
        with mock.patch.object(backend, "_rms", return_value=self.ROWS):
            out = backend.active_sc_operational()
        blob = json.dumps(out).lower()
        self.assertNotIn("fee", blob)
        self.assertNotIn("currency", blob)
        self.assertNotIn("84370", blob)

    def test_operational_fields_survive(self):
        with mock.patch.object(backend, "_rms", return_value=self.ROWS):
            out = backend.active_sc_operational()
        self.assertEqual("Cherry", out[0]["csm"])
        self.assertEqual("395503", out[0]["sc_id"])
        self.assertIsNotNone(out[0]["demand_age_days"])

    def test_an_undecodable_date_yields_no_age_rather_than_zero(self):
        rows = [dict(self.ROWS[0], SCCreatedDate="not a date")]
        with mock.patch.object(backend, "_rms", return_value=rows):
            out = backend.active_sc_operational()
        self.assertIsNone(out[0]["demand_age_days"])


class OffDateGating(unittest.TestCase):
    """
    Live sampling found these fields empty for every reachable trainer, so the
    gate is inert today. It is still tested, because the day RMS populates it
    the behaviour must already be correct.
    """

    def setUp(self):
        self.start = date(2026, 9, 1)
        self.batch = {"start_date": self.start, "end_date": self.start + timedelta(days=3),
                      "country": "UAE", "international": True, "customer": "Acme"}
        self.cand = {"trainer_name": "A", "skill_level": 8, "course_assignments": 5,
                     "free_dates": {self.start + timedelta(days=i) for i in range(30)},
                     "visa": [{"country": "UAE", "expiry": date(2030, 1, 1),
                               "stay_days": 90, "associates": []}],
                     "timezone": "India Standard Time"}
        self.sched = {"leave_dates": set(), "confirmed_dates": set(), "tentative_dates": set(),
                      "dnc_clients": set(), "specified_clients": set(), "modes": [], "rows": 0}

    def test_no_off_dates_means_no_travel_blocker(self):
        r = backend.evaluate_candidate(self.cand, self.sched, self.batch)
        self.assertTrue(r["eligible"])

    def test_an_international_travel_blackout_blocks(self):
        c = dict(self.cand, off_dates={"international_roaming": "2026-09-02"})
        r = backend.evaluate_candidate(c, self.sched, self.batch)
        self.assertFalse(r["eligible"])
        self.assertIn("international_travel_window", [b["gate"] for b in r["blockers"]])

    def test_an_international_blackout_does_not_block_a_domestic_batch(self):
        c = dict(self.cand, off_dates={"international_roaming": "2026-09-02"})
        r = backend.evaluate_candidate(c, self.sched, dict(self.batch, international=False))
        self.assertTrue(r["eligible"])

    def test_a_domestic_roaming_blackout_blocks_either_way(self):
        c = dict(self.cand, off_dates={"roaming": "2026-09-03"})
        r = backend.evaluate_candidate(c, self.sched, dict(self.batch, international=False))
        self.assertFalse(r["eligible"])
        self.assertIn("travel_window", [b["gate"] for b in r["blockers"]])

    def test_ranges_are_expanded(self):
        got = backend.parse_off_dates("2026-09-01 to 2026-09-03")
        self.assertEqual({date(2026, 9, 1), date(2026, 9, 2), date(2026, 9, 3)}, got)

    def test_lists_and_junk_are_handled(self):
        got = backend.parse_off_dates("2026-09-01, nonsense; 2026-09-05")
        self.assertEqual({date(2026, 9, 1), date(2026, 9, 5)}, got)
        self.assertEqual(set(), backend.parse_off_dates(None))
        self.assertEqual(set(), backend.parse_off_dates("null"))

    def test_an_absurd_range_is_not_expanded(self):
        # A decade-long "off date" is bad data, not a decade of unavailability.
        self.assertEqual(set(), backend.parse_off_dates("2020-01-01 to 2030-01-01"))


class CandidateRoute(unittest.TestCase):

    def setUp(self):
        backend.app.config["TESTING"] = True
        self.client = backend.app.test_client()

    def test_an_unresolved_course_returns_422_not_an_empty_pool(self):
        with mock.patch.object(backend, "_v2_manager_session", return_value=({}, None)), \
             mock.patch.object(backend, "_free_schedule", return_value=({}, "course 'X' not found")):
            r = self.client.get("/api/v2/allocation/candidates"
                                "?manager=m@koenig-solutions.com&course=X&start=2026-09-01")
        self.assertEqual(422, r.status_code)
        body = r.get_json()
        self.assertEqual("COURSE_UNRESOLVED", body["code"])
        self.assertEqual([], body["candidates"])
        self.assertIn("not an empty pool", body["note"])

    def test_missing_course_is_rejected(self):
        with mock.patch.object(backend, "_v2_manager_session", return_value=({}, None)):
            r = self.client.get("/api/v2/allocation/candidates?manager=m@koenig-solutions.com")
        self.assertEqual(400, r.status_code)

    def test_eligible_candidates_are_ranked_and_blocked_ones_are_kept(self):
        start = date(2026, 9, 1)
        pool = {
            "good trainer": {"trainer_name": "Good Trainer", "skill_level": 9,
                             "course_assignments": 10,
                             "free_dates": {start + timedelta(days=i) for i in range(20)},
                             "visa": [], "timezone": "India Standard Time",
                             "resolved_course": "Course X", "match_confidence": "exact"},
            "busy trainer": {"trainer_name": "Busy Trainer", "skill_level": 9,
                             "course_assignments": 10, "free_dates": set(),
                             "visa": [], "timezone": "India Standard Time",
                             "resolved_course": "Course X", "match_confidence": "exact"},
        }
        with mock.patch.object(backend, "_v2_manager_session", return_value=({}, None)), \
             mock.patch.object(backend, "_free_schedule", return_value=(pool, "")), \
             mock.patch.object(backend, "_rms", return_value=[]):
            r = self.client.get("/api/v2/allocation/candidates"
                                "?manager=m@koenig-solutions.com&course=X"
                                "&start=2026-09-01&end=2026-09-03")
        self.assertEqual(200, r.status_code)
        body = r.get_json()
        self.assertEqual(2, body["counts"]["pool"])
        self.assertEqual(1, body["counts"]["eligible"])
        self.assertEqual("Good Trainer", body["candidates"][0]["trainer_name"])
        self.assertTrue(body["candidates"][0]["factors"])
        self.assertEqual("Busy Trainer", body["blocked"][0]["trainer_name"])

    def test_the_route_requires_a_session(self):
        r = self.client.get("/api/v2/allocation/candidates?manager=m@koenig-solutions.com&course=X")
        self.assertEqual(401, r.status_code)


if __name__ == "__main__":
    unittest.main()
