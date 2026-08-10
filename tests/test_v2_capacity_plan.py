import unittest
from datetime import date

import backend


class CapacityPlanTests(unittest.TestCase):
    def test_weekly_pressure_never_treats_unknown_availability_as_free(self):
        payload = {"batches": [
            {
                "start_date": "2026-08-12", "relevance": 82, "is_priority": True,
                "candidates": [{"availability_verified": False, "availability_status": "available"}],
            },
            {
                "start_date": "2026-08-18", "relevance": 20,
                "candidates": [{"availability_verified": True, "availability_status": "available"}],
            },
        ]}
        plan = backend._capacity_plan_from_allocation(payload, today=date(2026, 8, 10), weeks=2)

        self.assertEqual(2, plan["summary"]["demand"])
        self.assertEqual("high", plan["weeks"][0]["pressure"])
        self.assertEqual(0, plan["weeks"][0]["verified_available_candidates"])
        self.assertEqual(1, plan["weeks"][0]["availability_unknown_candidates"])
        self.assertEqual(50, plan["confidence"]["availability_pct"])

    def test_empty_horizon_is_not_fake_zero_coverage(self):
        plan = backend._capacity_plan_from_allocation({"batches": []}, today=date(2026, 8, 10), weeks=2)
        self.assertIsNone(plan["summary"]["coverage_pct"])
        self.assertIsNone(plan["confidence"]["availability_pct"])
        self.assertEqual("none", plan["weeks"][0]["pressure"])


class CapacityPlanRouteTests(unittest.TestCase):
    def setUp(self):
        backend._sessions.clear()
        backend._allocation_payload_cache.clear()
        backend._sessions["session"] = {"email": "manager@koenig-solutions.com", "role": "manager"}
        self.client = backend.app.test_client()
        self.headers = {"Authorization": "Bearer session"}

    def test_requires_authenticated_manager_scope(self):
        missing = self.client.get("/api/v2/planning/capacity?manager=manager@koenig-solutions.com")
        cross = self.client.get("/api/v2/planning/capacity?manager=other@koenig-solutions.com", headers=self.headers)
        self.assertEqual(401, missing.status_code)
        self.assertEqual(403, cross.status_code)

    def test_returns_202_until_allocation_snapshot_exists(self):
        response = self.client.get("/api/v2/planning/capacity?manager=manager@koenig-solutions.com", headers=self.headers)
        self.assertEqual(202, response.status_code)
        self.assertFalse(response.get_json()["ready"])

    def test_returns_versioned_plan_from_manager_snapshot(self):
        backend._allocation_payload_cache["manager@koenig-solutions.com"] = {"batches": []}
        response = self.client.get("/api/v2/planning/capacity?manager=manager@koenig-solutions.com", headers=self.headers)
        self.assertEqual(200, response.status_code)
        self.assertTrue(response.get_json()["ready"])
        self.assertEqual("2.1", response.get_json()["schema_version"])


if __name__ == "__main__":
    unittest.main()
