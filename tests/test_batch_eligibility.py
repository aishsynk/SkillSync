"""
GET /api/v2/eligibility/batch — per open batch, what blocks each of the
manager's trainers and which blocks the manager is allowed to fix.

Koenig's algorithm owns allocation; the manager's only lever is preparation.
Each case pins that contract: the ready/blocked split, and the gate ->
fixable_by mapping that decides which blockers the app offers an action for.
"""
import unittest
from datetime import date, timedelta
from unittest import mock

import backend


DEMAND = [{
    "demand_id": "266940", "course_name": "Course X",
    "start_date": "2026-09-01", "end_date": "2026-09-03",
    "location": "Dubai, UAE", "customer": "Acme", "delivery_mode": "FMAT",
}]


def _pool(start):
    return {
        "good trainer": {
            "trainer_name": "Good Trainer", "skill_level": 9, "course_assignments": 10,
            "free_dates": {start + timedelta(days=i) for i in range(20)},
            "visa": [{"country": "UAE", "expiry": date(2030, 1, 1),
                      "stay_days": 90, "associates": []}],
            "timezone": "India Standard Time",
            "resolved_course": "Course X", "match_confidence": "exact",
        },
        "busy trainer": {
            "trainer_name": "Busy Trainer", "skill_level": 2, "course_assignments": 0,
            "free_dates": set(), "visa": [], "timezone": "India Standard Time",
            "resolved_course": "Course X", "match_confidence": "exact",
        },
    }


ROSTER = [{"OffEmail": "good@koenig-solutions.com", "TrainerName": "Good Trainer"},
          {"OffEmail": "busy@koenig-solutions.com", "TrainerName": "Busy Trainer"}]

EMPTY_SCHED = {"leave_dates": set(), "confirmed_dates": set(), "tentative_dates": set(),
               "dnc_clients": set(), "specified_clients": set(), "modes": [], "rows": 0}


class BatchEligibilityRoute(unittest.TestCase):

    def setUp(self):
        backend.app.config["TESTING"] = True
        self.client = backend.app.test_client()

    def _get(self, pool=None, why="", demand=None):
        start = date(2026, 9, 1)
        pool = _pool(start) if pool is None else pool
        with mock.patch.object(backend, "_v2_manager_session", return_value=({}, None)), \
             mock.patch.object(backend, "_demand_rows", return_value=demand or DEMAND), \
             mock.patch.object(backend, "_free_schedule", return_value=(pool, why)), \
             mock.patch.object(backend, "_rc_schedule", return_value=(EMPTY_SCHED, "")), \
             mock.patch.object(backend, "_rms", return_value=ROSTER):
            return self.client.get(
                "/api/v2/eligibility/batch?manager=m@koenig-solutions.com"
                "&demand_id=266940&_build=1")

    def test_requires_a_session(self):
        r = self.client.get("/api/v2/eligibility/batch?manager=m@k.com&demand_id=1")
        self.assertEqual(401, r.status_code)

    def test_missing_demand_id_is_rejected(self):
        with mock.patch.object(backend, "_v2_manager_session", return_value=({}, None)):
            r = self.client.get("/api/v2/eligibility/batch?manager=m@koenig-solutions.com")
        self.assertEqual(400, r.status_code)

    def test_unknown_demand_id_is_404(self):
        with mock.patch.object(backend, "_v2_manager_session", return_value=({}, None)), \
             mock.patch.object(backend, "_demand_rows", return_value=DEMAND):
            r = self.client.get("/api/v2/eligibility/batch"
                                "?manager=m@koenig-solutions.com&demand_id=999&_build=1")
        self.assertEqual(404, r.status_code)

    def test_ready_and_blocked_are_split(self):
        r = self._get()
        self.assertEqual(200, r.status_code)
        body = r.get_json()
        self.assertFalse(body["loading"])
        self.assertEqual("Course X", body["course"])
        self.assertEqual("2026-09-01", body["start"])
        self.assertEqual(["Good Trainer"], [t["trainer_name"] for t in body["ready"]])
        self.assertEqual(["Busy Trainer"], [t["trainer_name"] for t in body["blocked"]])
        self.assertTrue(body["ready"][0]["note"])
        self.assertEqual("good@koenig-solutions.com", body["ready"][0]["trainer_email"])

    def test_availability_blocker_maps_to_confirm_availability(self):
        body = self._get().get_json()
        gates = {b["gate"]: b for b in body["blocked"][0]["blockers"]}
        self.assertIn("availability", gates)
        self.assertEqual("confirm_availability", gates["availability"]["fixable_by"])
        self.assertTrue(gates["availability"]["fix_hint"])

    def test_skill_floor_blocker_maps_to_mark_skill(self):
        start = date(2026, 9, 1)
        pool = {"low trainer": {
            "trainer_name": "Low Trainer", "skill_level": 3, "course_assignments": 5,
            "free_dates": {start + timedelta(days=i) for i in range(20)},
            "visa": [{"country": "UAE", "expiry": date(2030, 1, 1),
                      "stay_days": 90, "associates": []}],
            "timezone": "India Standard Time",
            "resolved_course": "Course X", "match_confidence": "exact",
        }}
        roster = [{"OffEmail": "low@koenig-solutions.com", "TrainerName": "Low Trainer"}]
        demand = [dict(DEMAND[0], location="", delivery_mode="ILO")]
        with mock.patch.object(backend, "_v2_manager_session", return_value=({}, None)), \
             mock.patch.object(backend, "_demand_rows", return_value=demand), \
             mock.patch.object(backend, "_free_schedule", return_value=(pool, "")), \
             mock.patch.object(backend, "_rc_schedule", return_value=(EMPTY_SCHED, "")), \
             mock.patch.object(backend, "_rms", return_value=roster), \
             mock.patch.object(backend, "evaluate_candidate", return_value={
                 "trainer_name": "Low Trainer", "eligible": False,
                 "blockers": [{"gate": "skill_level",
                               "detail": "skill level 3 is below the required 5"}],
                 "fit": 0, "factors": [],
             }):
            r = self.client.get("/api/v2/eligibility/batch"
                                "?manager=m@koenig-solutions.com&demand_id=266940&_build=1")
        blk = r.get_json()["blocked"][0]["blockers"][0]
        self.assertEqual("mark_skill", blk["fixable_by"])

    def test_gate_to_fixable_by_mapping(self):
        self.assertEqual("none", backend._eligibility_fix("dnc")[0])
        self.assertEqual("none", backend._eligibility_fix("visa")[0])
        self.assertEqual("book_exam", backend._eligibility_fix("mock_missing")[0])
        self.assertEqual("book_exam", backend._eligibility_fix("mock_rating")[0])
        self.assertEqual("mark_skill", backend._eligibility_fix("skill_level")[0])
        self.assertEqual("confirm_availability", backend._eligibility_fix("shift_window")[0])
        self.assertEqual("confirm_availability", backend._eligibility_fix("availability")[0])

    def test_unresolved_course_is_not_an_empty_team(self):
        body = self._get(pool={}, why="course 'Course X' not found").get_json()
        self.assertEqual([], body["ready"])
        self.assertEqual([], body["blocked"])
        self.assertIn("not an empty team", body["note"])


if __name__ == "__main__":
    unittest.main()
