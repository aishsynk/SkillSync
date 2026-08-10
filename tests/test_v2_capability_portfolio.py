import unittest
from unittest.mock import patch

import backend


class CapabilityPortfolioTests(unittest.TestCase):
    def test_rollup_exposes_succession_and_certification_risk(self):
        team = [{"readiness_bucket": "Ready", "readiness_score": 84}]
        courses = [
            {"course": "AI-102", "vendor": "Microsoft", "coverage": "single",
             "owner_count": 1, "approved_count": 1, "exam_code": "AI-102", "certified_count": 0},
            {"course": "AZ-104", "vendor": "Microsoft", "coverage": "shared",
             "owner_count": 2, "approved_count": 2, "exam_code": "AZ-104", "certified_count": 1,
             "future_skill": True},
        ]
        result = backend._capability_portfolio(team, courses)

        self.assertEqual("high_risk", result["summary"]["portfolio_health"])
        self.assertEqual(1, result["summary"]["ready_trainers"])
        self.assertEqual(50, result["vendor_coverage"][0]["coverage_pct"])
        self.assertEqual(50, result["vendor_coverage"][0]["certification_coverage_pct"])
        self.assertFalse(result["confidence"]["domain_taxonomy_available"])

    def test_empty_evidence_is_unknown_not_healthy(self):
        result = backend._capability_portfolio([], [])
        self.assertEqual("unknown", result["summary"]["portfolio_health"])
        self.assertEqual("partial", result["confidence"]["status"])


class CapabilityPortfolioRouteTests(unittest.TestCase):
    def setUp(self):
        backend._sessions.clear()
        backend._sessions["manager-session"] = {
            "email": "manager@koenig-solutions.com", "role": "manager"
        }
        self.client = backend.app.test_client()
        self.headers = {"Authorization": "Bearer manager-session"}

    def tearDown(self):
        backend._sessions.clear()

    def test_v2_route_requires_manager_scope(self):
        missing = self.client.get("/api/v2/capability/portfolio?email=manager@koenig-solutions.com")
        cross = self.client.get("/api/v2/capability/portfolio?email=other@koenig-solutions.com",
                                headers=self.headers)
        self.assertEqual(401, missing.status_code)
        self.assertEqual(403, cross.status_code)

    @patch.object(backend, "_exam_policy", return_value={})
    @patch.object(backend, "_capability_for")
    @patch.object(backend, "_rms")
    def test_v2_route_returns_portfolio(self, rms, capability_for, _policy):
        rms.return_value = [{"OffEmail": "trainer@koenig-solutions.com"}]
        capability_for.return_value = {
            "trainer_name": "Trainer", "trainer_email": "trainer@koenig-solutions.com",
            "photo_url": "", "courses": [], "readiness_score": None,
            "readiness_bucket": "Unknown", "certification": {
                "gap_count": 0, "held": [], "coverage_pct": None,
                "taught_codes": [], "held_codes": [],
            },
        }
        response = self.client.get(
            "/api/v2/capability/portfolio?email=manager@koenig-solutions.com",
            headers=self.headers,
        )
        self.assertEqual(200, response.status_code)
        self.assertEqual("unknown", response.get_json()["portfolio"]["summary"]["portfolio_health"])


if __name__ == "__main__":
    unittest.main()
