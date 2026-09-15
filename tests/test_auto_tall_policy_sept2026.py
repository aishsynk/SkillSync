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


class AutoTallSept2026PolicyTests(unittest.TestCase):
    """New Auto Tall / HR allocation policy updates effective Aug-Sep 2026.
    Mirrors the style of test_auto_tall_policy.py: synthetic candidate flags,
    since the eligibility/preference framework in evaluate_candidate already
    takes those as plain dict fields regardless of which upstream call
    ultimately populates them."""

    def setUp(self):
        self.start = date(2026, 9, 1)
        self.ilt_batch = {
            "start_date": self.start,
            "end_date": self.start + timedelta(days=4),
            "country": "India",
            "international": False,
            "customer": "Microsoft Corp",
            "delivery_mode": "ILT",
            "course_name": "AZ-104T00",
        }
        self.base_cand = {
            "trainer_name": "Test Trainer",
            "skill_level": 8,
            "course_assignments": 5,
            "free_dates": set(days(self.start, 30)),
            "timezone": "India Standard Time",
        }

    # ── Omnissa officially-approved == Certified (07 Sep 2026) ─────────────

    def test_omnissa_officially_approved_satisfies_certification_only_for_omnissa(self):
        omnissa_batch = dict(self.ilt_batch, customer="Omnissa")
        cand_approved = dict(self.base_cand, approved=True)

        r = backend.evaluate_candidate(cand_approved, schedule(), omnissa_batch)

        self.assertTrue(r["eligible"])
        self.assertIn("Certification (Omnissa)", [f["name"] for f in r["factors"]])

    def test_approved_flag_does_not_grant_omnissa_credit_for_other_vendors(self):
        other_batch = dict(self.ilt_batch, customer="Microsoft Corp")
        cand_approved = dict(self.base_cand, approved=True)

        r = backend.evaluate_candidate(cand_approved, schedule(), other_batch)

        self.assertNotIn("Certification (Omnissa)", [f["name"] for f in r["factors"]])

    def test_omnissa_batch_without_approval_gets_no_credit(self):
        omnissa_batch = dict(self.ilt_batch, customer="Omnissa")
        cand_unapproved = dict(self.base_cand, approved=False)

        r = backend.evaluate_candidate(cand_unapproved, schedule(), omnissa_batch)

        self.assertNotIn("Certification (Omnissa)", [f["name"] for f in r["factors"]])

    # ── Multi-assignment trip-history preference (07 Sep 2026) ─────────────

    def test_trip_history_is_a_preference_not_a_requirement(self):
        cand_no_history = dict(self.base_cand)
        cand_history = dict(self.base_cand, multi_assignment_trip_history=True)

        r_no_history = backend.evaluate_candidate(cand_no_history, schedule(), self.ilt_batch)
        r_history = backend.evaluate_candidate(cand_history, schedule(), self.ilt_batch)

        self.assertTrue(r_no_history["eligible"], "Missing trip history must never block eligibility")
        self.assertTrue(r_history["eligible"])
        self.assertGreater(r_history["fit"], r_no_history["fit"])
        self.assertIn("Trip continuity", [f["name"] for f in r_history["factors"]])

    def test_trip_history_preference_only_applies_to_ilt_fmat(self):
        ilo_batch = dict(self.ilt_batch, delivery_mode="ILO")
        cand_history = dict(self.base_cand, multi_assignment_trip_history=True)

        r = backend.evaluate_candidate(cand_history, schedule(), ilo_batch)

        self.assertNotIn("Trip continuity", [f["name"] for f in r["factors"]])

    # ── International vaccination preference (24 Aug 2026) ─────────────────

    def test_vaccination_preference_only_when_international(self):
        intl_batch = dict(self.ilt_batch, international=True, country="Kenya")
        cand_vaccinated = dict(self.base_cand, vaccinations={"yellow_fever": True, "polio": True})

        r = backend.evaluate_candidate(cand_vaccinated, schedule(), intl_batch)

        self.assertTrue(r["eligible"])
        self.assertIn("International vaccination", [f["name"] for f in r["factors"]])

    def test_no_vaccination_information_does_not_block_eligibility(self):
        intl_batch = dict(self.ilt_batch, international=True, country="Kenya")
        cand_unknown = dict(self.base_cand)  # no "vaccinations" key at all

        r = backend.evaluate_candidate(cand_unknown, schedule(), intl_batch)

        self.assertTrue(r["eligible"], "Missing vaccination data must not make a trainer ineligible")
        self.assertNotIn("International vaccination", [f["name"] for f in r["factors"]])

    def test_vaccination_preference_does_not_apply_domestically(self):
        cand_vaccinated = dict(self.base_cand, vaccinations={"yellow_fever": True, "polio": True})

        r = backend.evaluate_candidate(cand_vaccinated, schedule(), self.ilt_batch)  # international=False

        self.assertNotIn("International vaccination", [f["name"] for f in r["factors"]])

    def test_partial_vaccination_coverage_earns_no_preference(self):
        intl_batch = dict(self.ilt_batch, international=True, country="Kenya")
        cand_partial = dict(self.base_cand, vaccinations={"yellow_fever": True, "polio": False})

        r = backend.evaluate_candidate(cand_partial, schedule(), intl_batch)

        self.assertNotIn("International vaccination", [f["name"] for f in r["factors"]])

    # ── 2-hour / alternate 4-hour batches participate in allocation (27 Aug 2026) ──

    def test_two_hour_batch_participates_in_allocation(self):
        short_batch = dict(self.ilt_batch, duration_hours=2)

        r = backend.evaluate_candidate(dict(self.base_cand), schedule(), short_batch)

        self.assertTrue(r["eligible"], "A 2-hour batch must not be silently excluded from allocation")

    def test_alternate_four_hour_batch_participates_in_allocation(self):
        alt_batch = dict(self.ilt_batch, duration_hours=4, schedule_pattern="alternate")

        r = backend.evaluate_candidate(dict(self.base_cand), schedule(), alt_batch)

        self.assertTrue(r["eligible"], "An alternate 4-hour batch must not be silently excluded from allocation")


if __name__ == "__main__":
    unittest.main()
