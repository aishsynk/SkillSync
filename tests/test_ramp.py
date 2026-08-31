import unittest
from datetime import datetime, timedelta
from unittest.mock import patch

import backend


def _iso(d):
    return d.isoformat()


class RampTests(unittest.TestCase):
    def setUp(self):
        backend._warm_purge("ramp")
        backend._sessions.clear()
        backend._sessions["mgr-session"] = {
            "email": "manager@koenig-solutions.com", "role": "manager",
        }
        self.client = backend.app.test_client()
        self.headers = {"Authorization": "Bearer mgr-session"}

        self.today = datetime.utcnow().date()
        self.doj = {
            "t1@koenig-solutions.com": self.today - timedelta(days=60),    # fresh, 0 batches
            "t2@koenig-solutions.com": self.today - timedelta(days=210),   # stalled
            "t3@koenig-solutions.com": self.today - timedelta(days=120),   # first-deliveries
            "t5@koenig-solutions.com": self.today - timedelta(days=100),   # established
            "t4@koenig-solutions.com": self.today - timedelta(days=430),   # not new
        }
        self.empty = False

    def tearDown(self):
        backend._sessions.clear()
        backend._warm_purge("ramp")

    def _rms(self, api, body, *a, **k):
        if api == "reportees":
            if self.empty:
                return [{"OffEmail": "t4@koenig-solutions.com", "TrainerName": "T Four"}]
            return [
                {"OffEmail": "t1@koenig-solutions.com", "TrainerName": "T One"},
                {"OffEmail": "t2@koenig-solutions.com", "TrainerName": "T Two"},
                {"OffEmail": "t3@koenig-solutions.com", "TrainerName": "T Three"},
                {"OffEmail": "t5@koenig-solutions.com", "TrainerName": "T Five"},
                {"OffEmail": "t4@koenig-solutions.com", "TrainerName": "T Four"},
            ]
        if api == "prevUpcoming":
            email = str(body.get("Email", "")).lower()
            doj = self.doj.get(email)
            if email == "t3@koenig-solutions.com":
                return [
                    {"StarDate": _iso(doj + timedelta(days=30)),
                     "EndDate": _iso(doj + timedelta(days=32))},
                    {"StarDate": _iso(doj + timedelta(days=60)),
                     "EndDate": _iso(doj + timedelta(days=62))},
                ]
            if email == "t5@koenig-solutions.com":
                return [{"StarDate": _iso(doj + timedelta(days=10 + 7 * i)),
                         "EndDate": _iso(doj + timedelta(days=12 + 7 * i))} for i in range(5)]
            return []
        if api == "trainerFeedback":
            return []
        return []

    def _util_row(self, email):
        util = {
            "t1@koenig-solutions.com": "5.0/10.0",
            "t2@koenig-solutions.com": "5.0/15.0",
            "t3@koenig-solutions.com": "40.0/55.0",
            "t5@koenig-solutions.com": "60.0/70.0",
            "t4@koenig-solutions.com": "60.0/80.0",
        }
        d = self.doj[email]
        return {
            "TrainerName": email,
            "DOJ": _iso(d),
            d.strftime("%b %Y"): util[email],
        }

    def _skills(self, email):
        return [{"course_name": "AZ-104: Microsoft Azure Administrator"}]

    def _demand(self):
        w = self.today
        return [{
            "course_name": "AZ-104: Microsoft Azure Administrator",
            "start_date": _iso(w + timedelta(days=7)),
            "end_date": _iso(w + timedelta(days=9)),
            "days": 3, "participants": 8,
        }]

    def _build(self):
        with patch.object(backend, "_rms", side_effect=self._rms), \
             patch.object(backend, "_demand_rows", side_effect=self._demand), \
             patch.object(backend, "_skills", side_effect=self._skills), \
             patch.object(backend, "_util_row", side_effect=self._util_row):
            resp = self.client.get(
                "/api/v2/ramp?manager=manager@koenig-solutions.com&_build=1",
                headers=self.headers)
        self.assertEqual(200, resp.status_code)
        return resp.get_json()

    def _by_email(self, body):
        return {t["email"]: t for t in body["trainers"]}

    # ── tests ──────────────────────────────────────────────────────────────
    def test_requires_manager_scope(self):
        self.assertEqual(401, self.client.get(
            "/api/v2/ramp?manager=manager@koenig-solutions.com").status_code)
        self.assertEqual(403, self.client.get(
            "/api/v2/ramp?manager=other@koenig-solutions.com",
            headers=self.headers).status_code)

    def test_window_excludes_old_joiners(self):
        body = self._build()
        emails = self._by_email(body)
        self.assertNotIn("t4@koenig-solutions.com", emails)
        self.assertEqual(4, body["summary"]["new_count"])
        self.assertEqual(12, body["window_months"])

    def test_stage_classification(self):
        t = self._by_email(self._build())
        self.assertEqual("onboarding", t["t1@koenig-solutions.com"]["ramp_stage"])
        self.assertEqual("first-deliveries", t["t3@koenig-solutions.com"]["ramp_stage"])
        self.assertEqual("established", t["t5@koenig-solutions.com"]["ramp_stage"])

    def test_stalled_flag(self):
        t = self._by_email(self._build())
        self.assertTrue(t["t2@koenig-solutions.com"]["stalled"])
        self.assertFalse(t["t1@koenig-solutions.com"]["stalled"])   # tenure <= 3 months
        self.assertEqual(1, self._build()["summary"]["stalled_count"])

    def test_days_to_first_batch_math(self):
        t = self._by_email(self._build())
        self.assertEqual(2, t["t3@koenig-solutions.com"]["batches_delivered"])
        self.assertEqual(30, t["t3@koenig-solutions.com"]["days_to_first_batch"])
        self.assertEqual(10, t["t5@koenig-solutions.com"]["days_to_first_batch"])
        self.assertIsNone(t["t1@koenig-solutions.com"]["days_to_first_batch"])

    def test_summary_avg_days(self):
        # only t3 (30) and t5 (10) have a first batch -> mean 20
        self.assertEqual(20, self._build()["summary"]["avg_days_to_first_batch"])

    def test_empty_when_no_new_trainers(self):
        self.empty = True
        body = self._build()
        self.assertEqual([], body["trainers"])
        self.assertEqual(0, body["summary"]["new_count"])
        self.assertIn("note", body["summary"])


if __name__ == "__main__":
    unittest.main()
