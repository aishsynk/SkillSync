import unittest
from datetime import date
from unittest.mock import patch

import backend


class DemandSafetyTests(unittest.TestCase):
    def test_aishwar_recommendation_is_pure_and_non_mutating(self):
        batch = {
            "delivery_mode_kind": "FMAT",
            "is_international": True,
        }
        candidates = [{
            "trainer_name": "Aishwar (You)",
            "trainer_email": backend._AISHWAR_EMAIL,
            "match": 82,
        }]

        with patch.object(backend, "_rms", side_effect=AssertionError("recommendation must not call RMS")):
            result = backend._aishwar_recommendation(batch, candidates)

        self.assertTrue(result["recommended"])
        self.assertEqual(result["suggested_skill_level"], 8)
        self.assertEqual(result["skill_match"], 82)
        self.assertFalse(result["availability_verified"])

    def test_demand_get_never_calls_skill_write(self):
        demand = [{
            "demand_id": "D-1",
            "course_id": "123",
            "course_name": "AZ-104",
            "customer": "Microsoft",
            "delivery_mode": "FMAT",
            "location": "London, United Kingdom",
            "participants": 4,
        }]
        candidate = {
            "trainer_name": "Aishwar (You)",
            "trainer_email": backend._AISHWAR_EMAIL,
            "match": 80,
        }
        rms_roles = []

        def fake_rms(role, _params):
            rms_roles.append(role)
            if role == "reportees":
                return []
            raise AssertionError(f"unexpected RMS role from Demand GET: {role}")

        with (
            patch.object(backend, "_demand_rows", return_value=demand),
            patch.object(backend, "_rms", side_effect=fake_rms),
            patch.object(backend, "_util_row", return_value={"TrainerName": "Aishwar"}),
            patch.object(backend, "_team_capability", return_value=[]),
            patch.object(backend, "_rank_batch", return_value=(80, [candidate], "Available with Upskilling")),
        ):
            response = backend.app.test_client().get(
                "/api/data/allocation-desk?email=aishwar_v@koenig-solutions.com"
            )

        self.assertEqual(response.status_code, 200)
        self.assertNotIn("addTrainerSkill", rms_roles)
        payload = response.get_json()
        recommendation = payload["batches"][0]["manager_recommendation"]
        self.assertEqual(recommendation["suggested_skill_level"], 8)
        self.assertFalse(recommendation["availability_verified"])

    def test_next_weekend_is_recommendation_metadata(self):
        self.assertEqual(backend._next_weekend(date(2026, 8, 9)).isoformat(), "2026-08-15")


class CompleteTeamTests(unittest.TestCase):
    def test_team_capability_does_not_truncate_after_twenty(self):
        reportees = [
            {"OffEmail": f"trainer{i}@koenig-solutions.com", "TrainerName": f"Trainer {i}"}
            for i in range(25)
        ]

        def capability(row, _policy):
            return {
                "trainer_name": row["TrainerName"],
                "trainer_email": row["OffEmail"],
                "courses": [],
                "readiness_score": 70,
                "readiness_bucket": "Ready",
                "certification": {
                    "gap_count": 0,
                    "held": [],
                    "taught_codes": [],
                    "held_codes": [],
                    "coverage_pct": None,
                },
            }

        with (
            patch.object(backend, "_rms", return_value=reportees),
            patch.object(backend, "_exam_policy", return_value={}),
            patch.object(backend, "_capability_for", side_effect=capability),
        ):
            response = backend.app.test_client().get(
                "/api/data/team-capability?email=manager@koenig-solutions.com"
            )

        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.get_json()["team_size"], 25)


if __name__ == "__main__":
    unittest.main()
