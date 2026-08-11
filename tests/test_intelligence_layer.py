"""
The intelligence layer's business rules.

These are not implementation tests. Each one pins a rule the operator stated
explicitly, so a later refactor cannot quietly reverse a business decision:

  - utilisation is not availability
  - DNC is absolute and non-overridable
  - unknown visa is surfaced, never silently excluded
  - tentative work is a soft conflict, not an absence
  - a miss on course resolution means "cannot verify", not "nobody available"
"""
import unittest
from datetime import date, timedelta

import backend


def days(start, n):
    return [start + timedelta(days=i) for i in range(n)]


def schedule(**kw):
    """An empty RC schedule, overridden per test."""
    s = {
        "leave_dates": set(), "confirmed_dates": set(), "tentative_dates": set(),
        "dnc_clients": set(), "specified_clients": set(), "modes": [], "rows": 0,
    }
    s.update(kw)
    return s


class AvailabilityIsNotUtilisation(unittest.TestCase):
    """The governing principle, made executable."""

    def setUp(self):
        self.start = date(2026, 9, 1)
        self.batch_days = days(self.start, 4)
        self.free = set(days(self.start, 30))

    def test_a_heavily_utilised_trainer_free_on_the_dates_is_available(self):
        v = backend.availability_verdict(self.free, schedule(), self.batch_days)
        self.assertEqual("available", v["status"])

    def test_a_lightly_utilised_trainer_on_leave_is_unavailable(self):
        s = schedule(leave_dates=set(self.batch_days))
        v = backend.availability_verdict(self.free, s, self.batch_days)
        self.assertEqual("unavailable", v["status"])
        self.assertIn("leave", v["reason"])

    def test_a_confirmed_booking_blocks(self):
        s = schedule(confirmed_dates=set(self.batch_days))
        v = backend.availability_verdict(self.free, s, self.batch_days)
        self.assertEqual("unavailable", v["status"])

    def test_tentative_work_is_a_soft_conflict_not_an_absence(self):
        # A provisional booking must not make a bench look emptier than it is.
        s = schedule(tentative_dates=set(self.batch_days))
        v = backend.availability_verdict(self.free, s, self.batch_days)
        self.assertEqual("available_with_conflicts", v["status"])
        self.assertEqual(4, len(v["soft_conflicts"]))

    def test_partial_overlap_names_the_blocked_days(self):
        s = schedule(leave_dates={self.start})
        v = backend.availability_verdict(self.free, s, self.batch_days)
        self.assertEqual("partially_available", v["status"])
        self.assertEqual([self.start.isoformat()], v["blocked_days"])

    def test_no_availability_record_is_unknown_not_unavailable(self):
        # None means RMS returned no row for this trainer.
        v = backend.availability_verdict(None, schedule(), self.batch_days)
        self.assertEqual("unknown", v["status"])

    def test_a_row_listing_no_free_days_is_unavailable_not_unknown(self):
        # An empty set is a definite answer, not a missing one. Collapsing the
        # two let a fully booked trainer pass the eligibility gate.
        v = backend.availability_verdict(set(), schedule(), self.batch_days)
        self.assertEqual("unavailable", v["status"])

    def test_unknown_batch_dates_are_unknown(self):
        self.assertEqual("unknown", backend.availability_verdict(self.free, schedule(), [])["status"])


class DncIsAbsolute(unittest.TestCase):
    """A client exclusion is a decision, not a weight."""

    def setUp(self):
        self.start = date(2026, 9, 1)
        self.batch = {
            "start_date": self.start, "end_date": self.start + timedelta(days=3),
            "customer": "Cisco", "delivery_mode": "ILO",
        }
        self.candidate = {
            "trainer_name": "A", "skill_level": 10, "course_assignments": 40,
            "free_dates": set(days(self.start, 30)), "visa": [], "timezone": "India Standard Time",
        }

    def test_a_dnc_trainer_is_ineligible_regardless_of_fit(self):
        s = schedule(dnc_clients={"cisco"})
        r = backend.evaluate_candidate(self.candidate, s, self.batch)
        self.assertFalse(r["eligible"])
        self.assertEqual(0, r["fit"])
        self.assertEqual(["dnc"], [b["gate"] for b in r["blockers"]])

    def test_a_perfect_dnc_trainer_never_outranks_a_clear_weaker_one(self):
        strong = backend.evaluate_candidate(self.candidate, schedule(dnc_clients={"cisco"}), self.batch)
        weak = backend.evaluate_candidate(
            dict(self.candidate, trainer_name="B", skill_level=4, course_assignments=1),
            schedule(), self.batch)
        self.assertFalse(strong["eligible"])
        self.assertTrue(weak["eligible"])
        self.assertGreater(weak["fit"], strong["fit"])

    def test_client_preference_boosts_but_is_not_a_gate(self):
        r = backend.evaluate_candidate(self.candidate, schedule(specified_clients={"cisco"}), self.batch)
        self.assertTrue(r["eligible"])
        self.assertIn("Client preference", [f["name"] for f in r["factors"]])


class UnknownVisaIsSurfacedNotExcluded(unittest.TestCase):
    """Roughly half of trainers carry no visa record; hiding them is wrong."""

    def setUp(self):
        self.start = date(2026, 9, 1)
        self.days = days(self.start, 4)
        self.batch = {
            "start_date": self.start, "end_date": self.start + timedelta(days=3),
            "country": "United Arab Emirates", "international": True, "customer": "Acme",
        }
        self.candidate = {
            "trainer_name": "A", "skill_level": 8, "course_assignments": 5,
            "free_dates": set(days(self.start, 30)), "visa": [],
            "timezone": "India Standard Time",
        }

    def test_no_visa_record_stays_eligible_and_is_flagged(self):
        r = backend.evaluate_candidate(self.candidate, schedule(), self.batch)
        self.assertTrue(r["eligible"], "an unrecorded visa must never exclude a trainer")
        self.assertEqual("unknown", r["international"]["visa"])
        self.assertTrue(r["requires_verification"])

    def test_a_visa_for_the_wrong_country_does_exclude(self):
        c = dict(self.candidate, visa=[{
            "country": "Australia", "expiry": date(2030, 1, 1),
            "stay_days": 90, "associates": [],
        }])
        r = backend.evaluate_candidate(c, schedule(), self.batch)
        self.assertFalse(r["eligible"])
        self.assertEqual(["visa"], [b["gate"] for b in r["blockers"]])

    def test_associate_countries_count_as_coverage(self):
        # Live data showed an Australia visa also covering Philippines and Egypt.
        c = dict(self.candidate, visa=[{
            "country": "Australia", "expiry": date(2030, 1, 1),
            "stay_days": 90, "associates": ["philippines", "egypt"],
        }])
        batch = dict(self.batch, country="Egypt")
        r = backend.evaluate_candidate(c, schedule(), batch)
        self.assertTrue(r["eligible"])
        self.assertEqual("available", r["international"]["visa"])
        self.assertIn("via Australia", r["international"]["visa_detail"])

    def test_an_expired_visa_excludes(self):
        c = dict(self.candidate, visa=[{
            "country": "United Arab Emirates", "expiry": date(2026, 1, 1),
            "stay_days": 90, "associates": [],
        }])
        r = backend.evaluate_candidate(c, schedule(), self.batch)
        self.assertFalse(r["eligible"])

    def test_a_stay_shorter_than_the_batch_excludes(self):
        batch = dict(self.batch, end_date=self.start + timedelta(days=20))
        c = dict(self.candidate,
                 free_dates=set(days(self.start, 40)),
                 visa=[{"country": "United Arab Emirates", "expiry": date(2030, 1, 1),
                        "stay_days": 7, "associates": []}])
        r = backend.evaluate_candidate(c, schedule(), batch)
        self.assertFalse(r["eligible"])
        self.assertIn("permitted stay", r["blockers"][0]["detail"])

    def test_visa_is_not_evaluated_for_domestic_batches(self):
        batch = dict(self.batch, international=False)
        r = backend.evaluate_candidate(self.candidate, schedule(), batch)
        self.assertTrue(r["eligible"])
        self.assertIsNone(r["international"])


class TimeZoneFit(unittest.TestCase):

    def test_offsets_are_classified(self):
        c = {"timezone": "India Standard Time", "visa": []}
        d = days(date(2026, 9, 1), 3)
        self.assertEqual("comfortable", backend.international_verdict(c, "UAE", d)["timezone_fit"])
        self.assertEqual("workable", backend.international_verdict(c, "UK", d)["timezone_fit"])
        self.assertEqual("unsocial", backend.international_verdict(c, "USA", d)["timezone_fit"])

    def test_an_unknown_zone_is_unknown_not_a_penalty(self):
        c = {"timezone": "", "visa": []}
        v = backend.international_verdict(c, "UAE", days(date(2026, 9, 1), 3))
        self.assertEqual("unknown", v["timezone_fit"])


class TransparentScoring(unittest.TestCase):
    """A score a manager cannot audit is a score they cannot overrule."""

    def setUp(self):
        self.start = date(2026, 9, 1)
        self.batch = {"start_date": self.start, "end_date": self.start + timedelta(days=3),
                      "customer": "Acme", "delivery_mode": "ILO"}
        self.candidate = {"trainer_name": "A", "skill_level": 9, "course_assignments": 12,
                          "free_dates": set(days(self.start, 30)), "visa": [],
                          "timezone": "India Standard Time"}

    def test_every_factor_states_its_contribution_and_evidence(self):
        r = backend.evaluate_candidate(self.candidate, schedule(modes=["ILO", "ILO"]), self.batch)
        self.assertTrue(r["factors"])
        for f in r["factors"]:
            self.assertTrue(f["name"])
            self.assertTrue(f["evidence"], f"{f['name']} has no evidence")
            self.assertIsInstance(f["contribution"], int)

    def test_course_experience_outweighs_raw_utilisation(self):
        experienced = backend.evaluate_candidate(
            dict(self.candidate, course_assignments=12, utilisation=88), schedule(), self.batch)
        idle_novice = backend.evaluate_candidate(
            dict(self.candidate, trainer_name="B", course_assignments=0, utilisation=20),
            schedule(), self.batch)
        self.assertGreater(experienced["fit"], idle_novice["fit"])

    def test_fit_is_bounded(self):
        r = backend.evaluate_candidate(
            dict(self.candidate, course_assignments=999, skill_level=10, utilisation=10),
            schedule(specified_clients={"acme"}, modes=["ILO"] * 20), self.batch)
        self.assertLessEqual(r["fit"], 100)
        self.assertGreaterEqual(r["fit"], 0)

    def test_a_skill_floor_is_a_gate(self):
        r = backend.evaluate_candidate(dict(self.candidate, skill_level=2), schedule(),
                                       self.batch, required_level=6)
        self.assertFalse(r["eligible"])
        self.assertEqual(["skill_level"], [b["gate"] for b in r["blockers"]])


class CourseResolution(unittest.TestCase):
    """
    API 171 returns nothing for an inexact course name, so a resolution miss
    must be reported as "cannot verify" and never as "nobody is available".
    """

    CATALOGUE = {
        "az 305t00 designing microsoft azure infrastructure solutions": {
            "course_name": "AZ-305T00: Designing Microsoft Azure Infrastructure Solutions"},
        "certified kubernetes administrator cka": {
            "course_name": "Certified Kubernetes Administrator (CKA)"},
    }

    def setUp(self):
        self._real = backend._course_catalogue_index
        backend._course_catalogue_index = lambda: self.CATALOGUE

    def tearDown(self):
        backend._course_catalogue_index = self._real

    def test_an_exact_name_resolves_exactly(self):
        name, conf = backend._resolve_course_name("Certified Kubernetes Administrator (CKA)")
        self.assertEqual("exact", conf)

    def test_a_bare_course_code_resolves(self):
        name, conf = backend._resolve_course_name("AZ-305")
        self.assertEqual("resolved", conf)
        self.assertTrue(name.startswith("AZ-305T00"))

    def test_a_title_without_its_code_resolves(self):
        # Managers say "Designing Microsoft Azure Infrastructure Solutions";
        # RMS stores "AZ-305T00: Designing …". Neither is a prefix of the other.
        name, conf = backend._resolve_course_name("Designing Microsoft Azure Infrastructure Solutions")
        self.assertEqual("resolved", conf)
        self.assertTrue(name.startswith("AZ-305T00"))

    def test_an_unknown_course_returns_no_match(self):
        name, conf = backend._resolve_course_name("Total Nonsense Course")
        self.assertEqual("", conf)
        self.assertEqual("", name)

    def test_a_short_fragment_does_not_match_by_containment(self):
        # "azure" must not silently resolve to one arbitrary Azure course.
        _, conf = backend._resolve_course_name("azure")
        self.assertEqual("", conf)

    def test_an_unresolved_course_reports_cannot_verify_not_empty_pool(self):
        pool, err = backend._free_schedule("Total Nonsense Course")
        self.assertEqual({}, pool)
        self.assertIn("not found", err)


class Parsing(unittest.TestCase):

    def test_free_dates_parse_from_the_comma_separated_list(self):
        got = backend._parse_free_dates("2026-08-15,2026-08-16, 2026-08-17 ")
        self.assertEqual({date(2026, 8, 15), date(2026, 8, 16), date(2026, 8, 17)}, got)

    def test_junk_dates_are_dropped_not_fatal(self):
        self.assertEqual({date(2026, 8, 15)}, backend._parse_free_dates("2026-08-15,,nonsense"))

    def test_future_skill_is_recovered_from_the_skill_level_string(self):
        # Live shape: "1 (Future Skill: 08-Sep-2026)" — parsing it as an int
        # alone would silently discard the succession signal.
        level, future = backend._parse_skill_level("1 (Future Skill: 08-Sep-2026)")
        self.assertEqual(1, level)
        self.assertEqual(date(2026, 9, 8), future)

    def test_a_plain_skill_level_has_no_future_date(self):
        level, future = backend._parse_skill_level("9")
        self.assertEqual(9, level)
        self.assertIsNone(future)

    def test_visa_parses_from_a_json_string(self):
        raw = ('[{"Country":"Australia","VisaExpiryDate":"12 Mar 2030",'
               '"StayPeriod":"90 Days","AssociateCountries":"Philippines,Egypt"}]')
        got = backend._parse_visa(raw)
        self.assertEqual(1, len(got))
        self.assertEqual("Australia", got[0]["country"])
        self.assertEqual(date(2030, 3, 12), got[0]["expiry"])
        self.assertEqual(90, got[0]["stay_days"])
        self.assertEqual(["philippines", "egypt"], got[0]["associates"])

    def test_malformed_visa_is_empty_not_an_exception(self):
        self.assertEqual([], backend._parse_visa("not json"))
        self.assertEqual([], backend._parse_visa(None))

    def test_delivery_days_are_inclusive(self):
        d = backend._delivery_days(date(2026, 9, 1), date(2026, 9, 3))
        self.assertEqual(3, len(d))

    def test_reversed_dates_yield_nothing(self):
        self.assertEqual([], backend._delivery_days(date(2026, 9, 3), date(2026, 9, 1)))


if __name__ == "__main__":
    unittest.main()
