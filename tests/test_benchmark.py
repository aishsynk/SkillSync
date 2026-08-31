import unittest
from unittest.mock import patch

import backend


class BenchmarkTests(unittest.TestCase):
    def setUp(self):
        backend._warm_purge("benchmark")
        backend._sessions.clear()
        backend._sessions["mgr-session"] = {
            "email": "manager@koenig-solutions.com", "role": "manager",
        }
        self.client = backend.app.test_client()
        self.headers = {"Authorization": "Bearer mgr-session"}

        self.demand = [
            {"course_name": "AZ-104: Microsoft Azure Administrator",
             "start_date": "2026-09-07", "end_date": "2026-09-09"},
            {"course_name": "CKA: Certified Kubernetes Administrator",
             "start_date": "2026-09-14", "end_date": "2026-09-16"},
        ]
        # key-244 dump: 3 roster trainers (all rated >= 4) + 2 outsiders with
        # low ratings. Company mean = 4.0, 3 incidents across 5 trainers -> 0.6.
        self.feedback = (
            [{"TrainerEmail": "t1@koenig-solutions.com", "MCQAnswer": v} for v in (4, 5)]
            + [{"TrainerEmail": "t2@koenig-solutions.com", "MCQAnswer": v} for v in (5, 4)]
            + [{"TrainerEmail": "t3@koenig-solutions.com", "MCQAnswer": v} for v in (4, 4)]
            + [{"TrainerEmail": "o1@koenig-solutions.com", "MCQAnswer": v} for v in (3, 3)]
            + [{"TrainerEmail": "o2@koenig-solutions.com", "MCQAnswer": v} for v in (3, 5)]
        )

    def tearDown(self):
        backend._sessions.clear()
        backend._warm_purge("benchmark")

    def _rms(self, api, body, *a, **k):
        if api == "reportees":
            return [
                {"OffEmail": "t1@koenig-solutions.com", "TrainerName": "T One"},
                {"OffEmail": "t2@koenig-solutions.com", "TrainerName": "T Two"},
                {"OffEmail": "t3@koenig-solutions.com", "TrainerName": "T Three"},
            ]
        if api == "trainerFeedback":
            return list(self.feedback)
        return []

    _UTIL = {
        "t1@koenig-solutions.com": "63.0/63.0",
        "t2@koenig-solutions.com": "61.0/61.0",
        "t3@koenig-solutions.com": "59.0/59.0",
    }
    _RATING = {
        "t1@koenig-solutions.com": 4.6,
        "t2@koenig-solutions.com": 4.5,
        "t3@koenig-solutions.com": 4.4,
    }

    def _util_row(self, email):
        return {"TrainerName": email, "Aug 2026": self._UTIL.get(email, "")}

    def _skills(self, email):
        return [{"course_name": "AZ-104: Microsoft Azure Administrator"}]

    def _fb_detail(self, email, *a, **k):
        return {"avg_rating": self._RATING.get(email)}

    def _build(self):
        with patch.object(backend, "_rms", side_effect=self._rms), \
             patch.object(backend, "_demand_rows", return_value=self.demand), \
             patch.object(backend, "_skills", side_effect=self._skills), \
             patch.object(backend, "_util_row", side_effect=self._util_row), \
             patch.object(backend, "_trainer_feedback_detail", side_effect=self._fb_detail):
            resp = self.client.get(
                "/api/v2/benchmark?manager=manager@koenig-solutions.com&_build=1",
                headers=self.headers)
        self.assertEqual(200, resp.status_code)
        return resp.get_json()

    def _metric(self, body, key):
        return next(m for m in body["metrics"] if m["key"] == key)

    # ── auth gate ───────────────────────────────────────────────────────────
    def test_requires_manager_scope(self):
        self.assertEqual(401, self.client.get(
            "/api/v2/benchmark?manager=manager@koenig-solutions.com").status_code)
        self.assertEqual(403, self.client.get(
            "/api/v2/benchmark?manager=other@koenig-solutions.com",
            headers=self.headers).status_code)

    # ── baseline_source is always present ───────────────────────────────────
    def test_baseline_source_always_present(self):
        body = self._build()
        self.assertTrue(body["baseline_source"])
        self.assertIn("no peer-manager average", body["baseline_source"])
        # also present on the cold fast payload
        with patch.object(backend, "_rms", side_effect=self._rms), \
             patch.object(backend, "_demand_rows", return_value=self.demand), \
             patch.object(backend, "_skills", side_effect=self._skills), \
             patch.object(backend, "_util_row", side_effect=self._util_row), \
             patch.object(backend, "_trainer_feedback_detail", side_effect=self._fb_detail):
            fast = self.client.get(
                "/api/v2/benchmark?manager=manager@koenig-solutions.com",
                headers=self.headers).get_json()
        self.assertTrue(fast["baseline_source"])

    # ── verdict thresholding: on_par / ahead / behind all exercised ─────────
    def test_verdicts(self):
        body = self._build()
        # utilisation mean = 61 vs 60 baseline -> within 5% -> on_par
        self.assertEqual("on_par", self._metric(body, "team_utilization")["verdict"])
        # company rating mean 4.0, team 4.5 -> ahead
        self.assertEqual("ahead", self._metric(body, "avg_learner_rating")["verdict"])
        self.assertEqual(4.0, self._metric(body, "avg_learner_rating")["baseline_value"])
        # only 1 of 2 open-demand courses covered -> 50% vs 100% -> behind
        cov = self._metric(body, "cert_coverage")
        self.assertEqual("behind", cov["verdict"])
        self.assertEqual(-50.0, cov["gap"])

    # ── direction handling for a lower_better metric ────────────────────────
    def test_lower_better_direction(self):
        body = self._build()
        bench = self._metric(body, "bench_rate")
        self.assertEqual("lower_better", bench["direction"])
        self.assertEqual(0.0, bench["team_value"])      # nobody under 40% util
        self.assertEqual("ahead", bench["verdict"])     # below baseline is good here

        inc = self._metric(body, "feedback_incident_rate")
        self.assertEqual("lower_better", inc["direction"])
        self.assertEqual(0.6, inc["baseline_value"])    # 3 incidents / 5 trainers
        self.assertEqual(0.0, inc["team_value"])
        self.assertEqual("ahead", inc["verdict"])

    # ── headline names the worst metric ────────────────────────────────────
    def test_headline_picks_worst_metric(self):
        body = self._build()
        self.assertEqual(3, body["summary"]["ahead_count"])
        self.assertEqual(1, body["summary"]["behind_count"])
        headline = body["summary"]["headline"]
        self.assertIn("Open-demand certification coverage", headline)
        self.assertIn("50.0%", headline)
        self.assertIn("below the line", headline)


if __name__ == "__main__":
    unittest.main()
