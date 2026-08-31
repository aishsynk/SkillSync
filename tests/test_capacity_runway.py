import unittest
from datetime import datetime, timedelta
from unittest.mock import patch

import backend


def _iso(d):
    return d.isoformat()


class CapacityRunwayTests(unittest.TestCase):
    def setUp(self):
        backend._warm_purge("runway")
        backend._sessions.clear()
        backend._sessions["mgr-session"] = {
            "email": "manager@koenig-solutions.com", "role": "manager",
        }
        self.client = backend.app.test_client()
        self.headers = {"Authorization": "Bearer mgr-session"}

        today = datetime.utcnow().date()
        self.week0 = today - timedelta(days=today.weekday())

        w = self.week0
        self.demand = [
            self._batch("AZ-104: Microsoft Azure Administrator", w + timedelta(days=7), 8),
            self._batch("AZ-104: Microsoft Azure Administrator", w + timedelta(days=8), 6),
            self._batch("CKA: Certified Kubernetes Administrator", w + timedelta(days=14), 5),
            self._batch("CKA: Certified Kubernetes Administrator", w + timedelta(days=21), 4),
            self._batch("AI-102: Azure AI Engineer", w + timedelta(days=15), 7),
            self._batch("AZ-104: Microsoft Azure Administrator", w + timedelta(days=21), 9),
        ]

    def tearDown(self):
        backend._sessions.clear()
        backend._warm_purge("runway")

    @staticmethod
    def _batch(name, start, pax):
        return {
            "course_name": name,
            "start_date": _iso(start),
            "end_date": _iso(start + timedelta(days=2)),
            "days": 3,
            "participants": pax,
        }

    def _rms(self, api, body, *a, **k):
        if api == "reportees":
            return [
                {"OffEmail": "t1@koenig-solutions.com", "TrainerName": "T One"},
                {"OffEmail": "t2@koenig-solutions.com", "TrainerName": "T Two"},
                {"OffEmail": "t3@koenig-solutions.com", "TrainerName": "T Three"},
            ]
        if api == "prevUpcoming":
            email = str(body.get("Email", "")).lower()
            if email == "t2@koenig-solutions.com":       # booked in week 1
                return [{"StarDate": _iso(self.week0 + timedelta(days=7)),
                         "EndDate": _iso(self.week0 + timedelta(days=9))}]
            if email == "t1@koenig-solutions.com":       # booked in week 3
                return [{"StarDate": _iso(self.week0 + timedelta(days=21)),
                         "EndDate": _iso(self.week0 + timedelta(days=23))}]
            return []
        return []

    def _skills(self, email):
        if email == "t2@koenig-solutions.com":
            return [{"course_name": "Azure AI Engineer Associate"}]
        # t1 and t3 both teach AZ-104
        return [{"course_name": "AZ-104: Microsoft Azure Administrator"}]

    def _util_row(self, email):
        if email == "t3@koenig-solutions.com":
            return {"TrainerName": "T Three", "Aug 2026": "5.0/90.0"}
        return {}

    def _build(self):
        with patch.object(backend, "_rms", side_effect=self._rms), \
             patch.object(backend, "_demand_rows", return_value=self.demand), \
             patch.object(backend, "_skills", side_effect=self._skills), \
             patch.object(backend, "_util_row", side_effect=self._util_row):
            resp = self.client.get(
                "/api/v2/planning/runway?manager=manager@koenig-solutions.com&_build=1",
                headers=self.headers)
        self.assertEqual(200, resp.status_code)
        return resp.get_json()

    def test_requires_manager_scope(self):
        self.assertEqual(401, self.client.get(
            "/api/v2/planning/runway?manager=manager@koenig-solutions.com").status_code)
        self.assertEqual(403, self.client.get(
            "/api/v2/planning/runway?manager=other@koenig-solutions.com",
            headers=self.headers).status_code)

    def test_week_buckets_and_gap_math(self):
        body = self._build()
        weeks = body["weeks"]
        self.assertEqual(8, len(weeks))
        self.assertEqual(8, body["horizon_weeks"])

        # week 0 — nothing incoming, t3 excluded from availability by 90% util
        self.assertEqual(0, weeks[0]["demand_batches"])
        self.assertEqual(2, weeks[0]["team_available"])

        # week 1 — two AZ-104 batches, only t1 free (t2 booked, t3 overloaded),
        # t1 teaches AZ-104 so both are coverable
        self.assertEqual(2, weeks[1]["demand_batches"])
        self.assertEqual(14, weeks[1]["demand_participants"])
        self.assertEqual(1, weeks[1]["team_available"])
        self.assertEqual(2, weeks[1]["coverable"])
        self.assertEqual(0, weeks[1]["gap"])

        # week 2 — CKA + AI-102, nobody free can teach either
        self.assertEqual(2, weeks[2]["demand_batches"])
        self.assertEqual(2, weeks[2]["team_available"])
        self.assertEqual(0, weeks[2]["coverable"])
        self.assertEqual(2, weeks[2]["gap"])

        # week 3 — CKA + AZ-104, only t2 free (t1 booked); t3 could teach AZ-104
        # but is over the utilisation line, so still uncovered
        self.assertEqual(2, weeks[3]["demand_batches"])
        self.assertEqual(1, weeks[3]["team_available"])
        self.assertEqual(0, weeks[3]["coverable"])
        self.assertEqual(2, weeks[3]["gap"])

    def test_worst_week_is_the_biggest_gap(self):
        body = self._build()
        self.assertEqual(_iso(self.week0 + timedelta(days=14)),
                         body["summary"]["worst_week"])

    def test_summary_totals(self):
        s = self._build()["summary"]
        self.assertEqual(6, s["total_demand"])
        self.assertEqual(2, s["total_coverable"])
        self.assertEqual(18, s["trainer_days_demanded"])          # 6 batches * 3 days
        # team_available per week: 2,1,2,1,2,2,2,2 = 14 -> * 5 working days
        self.assertEqual(70, s["trainer_days_available"])

    def test_upskilling_ranking_and_reasons(self):
        up = self._build()["upskilling"]
        self.assertEqual(
            ["CKA: Certified Kubernetes Administrator", "AI-102: Azure AI Engineer"],
            [u["course"] for u in up],
        )
        self.assertEqual(2, up[0]["opens_batches"])
        self.assertEqual("", up[0]["nearest_trainer"])
        self.assertEqual("2 open batches, no one on the team teaches this", up[0]["why"])

        self.assertEqual("t2@koenig-solutions.com", up[1]["nearest_trainer"])
        self.assertIn("one skill level short", up[1]["why"])


if __name__ == "__main__":
    unittest.main()
