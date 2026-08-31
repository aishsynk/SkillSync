import unittest
from datetime import datetime, timedelta
from unittest.mock import patch

import backend


def _iso(d):
    return d.isoformat()


class AccountsTests(unittest.TestCase):
    def setUp(self):
        backend._warm_purge("accounts")
        backend._sessions.clear()
        backend._sessions["mgr-session"] = {
            "email": "manager@koenig-solutions.com", "role": "manager",
        }
        self.client = backend.app.test_client()
        self.headers = {"Authorization": "Bearer mgr-session"}

        self.today = datetime.utcnow().date()
        t = self.today
        self.demand = [
            self._d("Globex", "CKA: Certified Kubernetes Administrator", t + timedelta(days=20), 5),
            self._d("Globex", "AZ-104: Microsoft Azure Administrator", t + timedelta(days=25), 6),
            self._d("Acme Corp", "AZ-104: Microsoft Azure Administrator", t + timedelta(days=15), 8),
            self._d("", "PL-300: Power BI Data Analyst", t + timedelta(days=5), 4),
            self._d("Initech", "AI-900: Azure AI Fundamentals", t + timedelta(days=200), 9),
        ]

    def tearDown(self):
        backend._sessions.clear()
        backend._warm_purge("accounts")

    @staticmethod
    def _d(customer, course, start, pax):
        return {
            "customer": customer,
            "course_name": course,
            "start_date": _iso(start),
            "end_date": _iso(start + timedelta(days=2)),
            "participants": pax,
        }

    def _asg(self, vendor, course, start, end, pax, aid):
        return {
            "Vendor": vendor, "Course": course,
            "StarDate": _iso(start), "EndDate": _iso(end),
            "NoOfParticipants": pax, "AssignmentId": aid,
        }

    def _rms(self, api, body, *a, **k):
        t = self.today
        if api == "reportees":
            return [
                {"OffEmail": "t1@koenig-solutions.com", "TrainerName": "T One"},
                {"OffEmail": "t2@koenig-solutions.com", "TrainerName": "T Two"},
            ]
        if api == "prevUpcoming":
            email = str(body.get("Email", "")).lower()
            if email == "t1@koenig-solutions.com":
                return [
                    self._asg("Acme Corp", "AZ-104: Microsoft Azure Administrator",
                              t - timedelta(days=20), t - timedelta(days=18), 8, "A1"),
                    self._asg("acme corp", "AZ-305: Designing Azure Infrastructure",
                              t - timedelta(days=12), t - timedelta(days=10), 5, "A2"),
                    self._asg("Acme Corp", "AZ-104: Microsoft Azure Administrator",
                              t + timedelta(days=10), t + timedelta(days=12), 7, "A5"),
                ]
            if email == "t2@koenig-solutions.com":
                return [
                    self._asg("Globex", "CKA: Certified Kubernetes Administrator",
                              t - timedelta(days=32), t - timedelta(days=30), 4, "A3"),
                    self._asg("", "AI-102: Azure AI Engineer",
                              t - timedelta(days=7), t - timedelta(days=5), 3, "A4"),
                ]
            return []
        if api == "trainerFeedback":
            return [
                {"TrainerEmail": "t1@koenig-solutions.com", "AssignmentId": "A1", "MCQAnswer": "4"},
                {"TrainerEmail": "t1@koenig-solutions.com", "AssignmentId": "A2", "MCQAnswer": "5"},
                {"TrainerEmail": "outsider@x.com", "AssignmentId": "A3", "MCQAnswer": "1"},
            ]
        return []

    def _build(self):
        with patch.object(backend, "_rms", side_effect=self._rms), \
             patch.object(backend, "_demand_rows", return_value=self.demand):
            resp = self.client.get(
                "/api/v2/accounts?manager=manager@koenig-solutions.com&_build=1",
                headers=self.headers)
        self.assertEqual(200, resp.status_code)
        return resp.get_json()

    def _accounts(self, body):
        return {a["name"]: a for a in body["accounts"]}

    def test_requires_manager_scope(self):
        self.assertEqual(401, self.client.get(
            "/api/v2/accounts?manager=manager@koenig-solutions.com").status_code)
        self.assertEqual(403, self.client.get(
            "/api/v2/accounts?manager=other@koenig-solutions.com",
            headers=self.headers).status_code)

    def test_grouping_and_casing_merge(self):
        body = self._build()
        acc = self._accounts(body)
        # "Acme Corp" and "acme corp" collapse to one account
        self.assertIn("Acme Corp", acc)
        self.assertNotIn("acme corp", acc)
        self.assertEqual(2, acc["Acme Corp"]["batches_delivered"])
        self.assertEqual(13, acc["Acme Corp"]["participants_delivered"])
        self.assertEqual(1, acc["Acme Corp"]["batches_upcoming"])
        self.assertEqual(["T One"], acc["Acme Corp"]["trainers"])
        self.assertEqual(
            ["AZ-104: Microsoft Azure Administrator", "AZ-305: Designing Azure Infrastructure"],
            acc["Acme Corp"]["courses"])

    def test_open_demand_vs_delivered_split(self):
        acc = self._accounts(self._build())
        # Globex: one delivered batch, two open demand batches, no upcoming assigned
        self.assertEqual(1, acc["Globex"]["batches_delivered"])
        self.assertEqual(2, acc["Globex"]["open_demand_batches"])
        self.assertEqual(0, acc["Globex"]["batches_upcoming"])
        # Acme: one open demand batch
        self.assertEqual(1, acc["Acme Corp"]["open_demand_batches"])
        # demand beyond the forward window (Initech, +200d) is excluded
        self.assertNotIn("Initech", acc)

    def test_unspecified_bucket(self):
        acc = self._accounts(self._build())
        self.assertIn("Unspecified", acc)
        self.assertEqual(1, acc["Unspecified"]["batches_delivered"])
        self.assertEqual(1, acc["Unspecified"]["open_demand_batches"])
        self.assertEqual(2, self._build()["summary"]["unspecified_batches"])

    def test_concentration_maths(self):
        body = self._build()
        # delivered batches: Acme 2, Globex 1, Unspecified 1 -> total 4, Acme 50%
        self.assertEqual("Acme Corp", body["concentration"]["account"])
        self.assertEqual(4, body["concentration"]["team_batches_delivered"])
        self.assertEqual(50.0, body["concentration"]["share_pct"])
        self.assertEqual("Acme Corp", body["summary"]["top_account"])
        self.assertEqual(50.0, body["summary"]["top_account_share"])

    def test_sort_order(self):
        body = self._build()
        # (open_demand desc, batches_delivered desc): Globex(2,1), Acme(1,2), Unspecified(1,1)
        self.assertEqual(["Globex", "Acme Corp", "Unspecified"],
                         [a["name"] for a in body["accounts"]])

    def test_avg_learner_rating_join(self):
        acc = self._accounts(self._build())
        self.assertEqual(4.5, acc["Acme Corp"]["avg_learner_rating"])
        # no team feedback rows for Globex / Unspecified -> key omitted
        self.assertNotIn("avg_learner_rating", acc["Globex"])
        self.assertNotIn("avg_learner_rating", acc["Unspecified"])

    def test_summary_and_window(self):
        body = self._build()
        self.assertEqual(3, body["summary"]["account_count"])
        self.assertEqual({"past_days": 90, "forward_days": 60}, body["window"])
        self.assertFalse(body["loading"])


if __name__ == "__main__":
    unittest.main()
