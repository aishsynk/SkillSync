import unittest
from datetime import date, timedelta
import backend


def schedule(**kwargs):
    s = {"leave_dates": set(), "confirmed_dates": set(), "tentative_dates": set(),
         "dnc_clients": set(), "specified_clients": set(), "modes": [], "rows": 0}
    s.update(kwargs)
    return s


def days(start, n):
    return [start + timedelta(days=i) for i in range(n)]


class AutoTallPolicyTests(unittest.TestCase):

    def setUp(self):
        self.start = date(2026, 9, 1)
        self.batch = {
            "start_date": self.start,
            "end_date": self.start + timedelta(days=4),
            "country": "India",
            "international": False,
            "customer": "Microsoft Corp",
            "delivery_mode": "ILO",
            "course_name": "AZ-104T00",
        }
        self.base_cand = {
            "trainer_name": "Test Trainer",
            "skill_level": 8,
            "course_assignments": 0,  # First-time delivery
            "free_dates": set(days(self.start, 30)),
            "timezone": "India Standard Time",
        }

    def test_certified_trainer_waives_mock_for_first_time_delivery(self):
        """Rule 1 & 8: Certified trainers are never filtered out for missing mock on 1st-time delivery."""
        # 1. Certified trainer without mock
        cand_cert = dict(self.base_cand, is_certified=True, mock_checked=True)
        r_cert = backend.evaluate_candidate(cand_cert, schedule(), self.batch)
        self.assertTrue(r_cert["eligible"], "Certified trainer must be eligible for first-time delivery without mock")
        self.assertIn("Mock requirement", [f["name"] for f in r_cert["factors"]])

        # 2. Uncertified trainer without mock (blocked)
        cand_uncert_no_mock = dict(self.base_cand, is_certified=False, mock_checked=True, mock_rating="")
        r_uncert_no = backend.evaluate_candidate(cand_uncert_no_mock, schedule(), self.batch)
        self.assertFalse(r_uncert_no["eligible"])
        self.assertEqual(["mock_missing"], [b["gate"] for b in r_uncert_no["blockers"]])

        # 3. Uncertified trainer with below expectation mock (blocked)
        cand_uncert_bad_mock = dict(self.base_cand, is_certified=False, mock_checked=True, mock_rating="Below Expectation")
        r_uncert_bad = backend.evaluate_candidate(cand_uncert_bad_mock, schedule(), self.batch)
        self.assertFalse(r_uncert_bad["eligible"])
        self.assertEqual(["mock_rating"], [b["gate"] for b in r_uncert_bad["blockers"]])

        # 4. Uncertified trainer with satisfactory mock (eligible)
        cand_uncert_good_mock = dict(self.base_cand, is_certified=False, mock_checked=True, mock_rating="Satisfactory")
        r_uncert_good = backend.evaluate_candidate(cand_uncert_good_mock, schedule(), self.batch)
        self.assertTrue(r_uncert_good["eligible"])
        self.assertIn("Mock verification", [f["name"] for f in r_uncert_good["factors"]])

    def test_priority_for_cancelled_batches(self):
        """Rule 2: Trainer with client-cancelled batch gets priority slot for next matching assignment."""
        cand_normal = dict(self.base_cand, course_assignments=5)
        cand_priority = dict(self.base_cand, course_assignments=5, cancelled_batch_priority=True)

        r_norm = backend.evaluate_candidate(cand_normal, schedule(), self.batch)
        r_prio = backend.evaluate_candidate(cand_priority, schedule(), self.batch)

        self.assertTrue(r_prio["eligible"])
        self.assertGreater(r_prio["fit"], r_norm["fit"])
        self.assertIn("Post-cancellation priority", [f["name"] for f in r_prio["factors"]])

    def test_6_month_clean_record_preference(self):
        """Rule 3: Clean record in trailing 6 months preferred over past negative feedback."""
        cand_clean = dict(self.base_cand, course_assignments=5, clean_record_6mo=True)
        cand_neg = dict(self.base_cand, course_assignments=5, recent_negative_6mo=True)

        r_clean = backend.evaluate_candidate(cand_clean, schedule(), self.batch)
        r_neg = backend.evaluate_candidate(cand_neg, schedule(), self.batch)

        self.assertTrue(r_clean["eligible"])
        self.assertTrue(r_neg["eligible"], "Soft preference should still allow eligibility")
        self.assertGreater(r_clean["fit"], r_neg["fit"])

    def test_tech_call_continuity_preference(self):
        """Rule 5: Pre-sales tech call trainer receives allocation preference for client continuity."""
        cand_normal = dict(self.base_cand, course_assignments=5)
        cand_tech = dict(self.base_cand, course_assignments=5, is_tech_call_trainer=True)

        r_norm = backend.evaluate_candidate(cand_normal, schedule(), self.batch)
        r_tech = backend.evaluate_candidate(cand_tech, schedule(), self.batch)

        self.assertTrue(r_tech["eligible"])
        self.assertGreater(r_tech["fit"], r_norm["fit"])
        self.assertIn("Tech call continuity", [f["name"] for f in r_tech["factors"]])
