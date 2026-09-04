import unittest
from datetime import date
from pathlib import Path
from unittest.mock import patch

import backend


class DemandSafetyTests(unittest.TestCase):
    def setUp(self):
        backend._sessions.clear()
        backend._sessions["aishwar-session"] = {
            "email": backend._AISHWAR_EMAIL,
            "role": "manager",
        }
        self.headers = {"Authorization": "Bearer aishwar-session"}

    def test_dashboard_does_not_invent_delivery_or_certification_kpis(self):
        source = Path(backend.__file__).read_text(encoding="utf-8")
        self.assertNotIn('if b["engagement_state"] == "current") or 4', source)
        self.assertNotIn('if b["engagement_state"] == "upcoming") or 6', source)
        self.assertNotIn("days_delivered = 42", source)
        self.assertNotIn("if trainer_ops else 85", source)

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
            # Read-only endpoints the availability overlay adds. The guard this
            # test exists for is unchanged: a Demand GET must never reach a
            # write, which is asserted on addTrainerSkill below.
            if role in {"prevUpcoming", "trainerDetails",
                        "courseCatalogue", "trainerFreeSchedule", "trainerRCSchedule"}:
                return None
            raise AssertionError(f"unexpected RMS role from Demand GET: {role}")

        with (
            patch.object(backend, "_demand_rows", return_value=demand),
            patch.object(backend, "_rms", side_effect=fake_rms),
            patch.object(backend, "_util_row", return_value={"TrainerName": "Aishwar"}),
            patch.object(backend, "_team_capability", return_value=[]),
            patch.object(backend, "_rank_batch", return_value=(80, [candidate], "Available with Upskilling")),
        ):
            response = backend.app.test_client().get(
                "/api/data/allocation-desk?email=aishwar_v@koenig-solutions.com&_build=1",
                headers=self.headers,
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
    def setUp(self):
        backend._sessions.clear()
        backend._sessions["manager-session"] = {
            "email": "manager@koenig-solutions.com",
            "role": "manager",
        }
        self.headers = {"Authorization": "Bearer manager-session"}

    def test_unified_trainer_uses_paged_assignment_api_as_undated_reference_only(self):
        reportee = {
            "OffEmail": "trainer@koenig-solutions.com", "TrainerName": "Trainer One",
            "EmpId": "1", "TrainerId": "T1", "IsdirectReportee": "Yes",
        }
        calls = []
        def rms(name, body, *args, **kwargs):
            calls.append(name)
            if name == "prevUpcoming": return None
            if name == "assignment":
                return [{"AssignmentID": "A1", "Course": "AI-102", "StartDate": "10-Aug-2026", "EndDate": "12-Aug-2026"}]
            if name == "trainerDetails": return []
            if name == "negFeedbackCount": return []
            return []
        with patch.object(backend, "_rms", side_effect=rms), patch.object(backend, "_util_row", return_value={}):
            built = backend._build_trainer(reportee, date(2026, 8, 9))
            ops, state = built[0], built[1]
        self.assertEqual(ops["assignment_source"], "assignment_api_reference")
        self.assertEqual(ops["assignment_reference_count"], 1)
        self.assertEqual(state["current_status"], "unknown")
        self.assertEqual(state["next_batch"], {})
        self.assertEqual(calls.count("assignment"), 1)

    def test_assigned_batch_joins_real_trainer_course_skill_level(self):
        reportee = {
            "OffEmail": "trainer@koenig-solutions.com", "TrainerName": "Trainer One",
            "EmpId": "1", "TrainerId": "T1", "IsdirectReportee": "Yes",
        }
        def rms(name, body, *args, **kwargs):
            if name == "prevUpcoming":
                return [{
                    "AssignmentId": "A1", "Course": "AI-102: Azure AI Engineer",
                    "StarDate": "09-Aug-2026", "EndDate": "12-Aug-2026",
                }]
            if name == "trainerDetails":
                return [{
                    "CourseName": "AI-102: Azure AI Engineer", "SkillLevel": "7",
                    "QubitsScore": "90", "OfficiallyApproved": "Yes",
                }]
            if name == "negFeedbackCount": return []
            return []
        with patch.object(backend, "_rms", side_effect=rms), patch.object(backend, "_util_row", return_value={}):
            built = backend._build_trainer(reportee, date(2026, 8, 9))
        batch = built[1]["current_batch"]
        self.assertEqual("7", batch["skill_level"])
        self.assertEqual("trainer_details", batch["skill_level_source"])

    def test_assigned_batch_does_not_guess_skill_level_without_course_match(self):
        reportee = {
            "OffEmail": "trainer@koenig-solutions.com", "TrainerName": "Trainer One",
            "EmpId": "1", "TrainerId": "T1", "IsdirectReportee": "Yes",
        }
        def rms(name, body, *args, **kwargs):
            if name == "prevUpcoming":
                return [{
                    "AssignmentId": "A1", "Course": "AI-102",
                    "StarDate": "09-Aug-2026", "EndDate": "12-Aug-2026",
                }]
            if name == "trainerDetails":
                return [{"CourseName": "AZ-104", "SkillLevel": "9", "QubitsScore": "90"}]
            if name == "negFeedbackCount": return []
            return []
        with patch.object(backend, "_rms", side_effect=rms), patch.object(backend, "_util_row", return_value={}):
            built = backend._build_trainer(reportee, date(2026, 8, 9))
        batch = built[1]["current_batch"]
        self.assertEqual("", batch["skill_level"])
        self.assertEqual("unavailable", batch["skill_level_source"])

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
                "/api/data/team-capability?email=manager@koenig-solutions.com",
                headers=self.headers,
            )

        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.get_json()["team_size"], 25)

    def test_actions_does_not_truncate_after_twenty(self):
        reportees = [
            {"OffEmail": f"trainer{i}@koenig-solutions.com", "TrainerName": f"Trainer {i}"}
            for i in range(25)
        ]

        def built(row, _today):
            return ({"trainer_email": row["OffEmail"]}, {})

        def actions(trainers, *_args):
            return [
                {"id": f"action-{i}", "lifecycle_state": "open"}
                for i, _trainer in enumerate(trainers)
            ]

        with (
            patch.object(backend, "_rms", return_value=reportees),
            patch.object(backend, "_build_trainer", side_effect=built),
            patch.object(backend, "_demand_rows", return_value=[]),
            patch.object(backend, "_derive_actions", side_effect=actions),
            patch.object(backend, "_action_apply_overlay"),
            patch.object(backend, "_action_store_load", return_value={"raised": {}}),
        ):
            response = backend.app.test_client().get(
                "/api/actions?email=manager@koenig-solutions.com",
                headers=self.headers,
            )

        self.assertEqual(response.status_code, 200)
        self.assertEqual(len(response.get_json()["actions"]), 25)


class AvailabilityEvidenceTests(unittest.TestCase):
    def test_assignment_conflict_is_not_available(self):
        result = backend._availability_evidence(
            "trainer@koenig-solutions.com", date(2026, 8, 15), date(2026, 8, 16),
            assignments_raw=[{
                "StarDate": "15-Aug-2026", "EndDate": "16-Aug-2026",
                "AssignmentId": "A-1", "Course": "Azure",
            }],
            details_raw=[{"OffEmail": "trainer@koenig-solutions.com"}],
        )
        self.assertTrue(result["verified"])
        self.assertFalse(result["available"])
        self.assertEqual(result["status"], "conflict")
        self.assertEqual(result["conflicts"][0]["assignment_id"], "A-1")

    def test_empty_verified_sources_are_available(self):
        result = backend._availability_evidence(
            "trainer@koenig-solutions.com", "2026-08-15", "2026-08-16",
            assignments_raw=[], details_raw=[{"OffEmail": "trainer@koenig-solutions.com"}],
        )
        self.assertTrue(result["verified"])
        self.assertTrue(result["available"])
        self.assertEqual(result["status"], "available")

    def test_source_failure_is_unverified_not_available(self):
        with patch.object(backend, "_rms", return_value=None):
            result = backend._availability_evidence(
                "trainer@koenig-solutions.com", "2026-08-15", "2026-08-16"
            )
        self.assertFalse(result["verified"])
        self.assertIsNone(result["available"])
        self.assertEqual(result["status"], "unverified")

    def test_next_available_weekend_skips_assignment_conflict(self):
        weekend, evidence = backend._next_available_weekend(
            backend._AISHWAR_EMAIL,
            sources=([{
                "StarDate": "15-Aug-2026", "EndDate": "16-Aug-2026",
                "AssignmentId": "A-1", "Course": "Azure",
            }], [{"OffEmail": backend._AISHWAR_EMAIL}]),
            today=date(2026, 8, 9),
        )
        self.assertEqual(weekend.isoformat(), "2026-08-22")
        self.assertTrue(evidence["verified"])
        self.assertEqual(evidence["status"], "available")


class DemandRankingTests(unittest.TestCase):
    def test_delivery_mode_order_is_strict(self):
        self.assertEqual(backend._priority_fields("FMAT", "India", 1, "Best Match")["priority_tier"], 1)
        self.assertEqual(backend._priority_fields("ILT", "India", 1, "Best Match")["priority_tier"], 2)
        self.assertEqual(backend._priority_fields("ILO", "", 1, "Best Match")["priority_tier"], 3)
        unknown = backend._priority_fields("Hybrid", "", 1, "Best Match")
        self.assertEqual(unknown["priority_tier"], 4)
        self.assertFalse(unknown["is_priority"])

    def test_demand_sort_uses_mode_then_suitability(self):
        rows = [
            {"demand_id": "unknown", "priority_tier": 4, "best_suitability_score": 100},
            {"demand_id": "ilt-low", "priority_tier": 2, "best_suitability_score": 60},
            {"demand_id": "fmat", "priority_tier": 1, "best_suitability_score": 20},
            {"demand_id": "ilo", "priority_tier": 3, "best_suitability_score": 99},
            {"demand_id": "ilt-high", "priority_tier": 2, "best_suitability_score": 90},
        ]
        rows.sort(key=backend._demand_sort_key)
        self.assertEqual(
            [row["demand_id"] for row in rows],
            ["fmat", "ilt-high", "ilt-low", "ilo", "unknown"],
        )

    def test_ranking_uses_all_suitability_components_and_english_preference(self):
        feedback = {"blocked": False, "blocked_until": None, "recent_negative_6mo": False}
        caps = [{"course": "AZ-900 Azure Fundamentals", "vendor": "Microsoft", "qubits_score": 80}]
        team = [
            ("English Trainer", "english@koenig-solutions.com", caps, feedback, False),
            ("Other Trainer", "other@koenig-solutions.com", caps, feedback, False),
        ]
        sources = {
            email: ([], [{"OffEmail": email}]) for email in (
                "english@koenig-solutions.com", "other@koenig-solutions.com"
            )
        }
        context = {
            "english@koenig-solutions.com": {"utilization": 60, "languages": ["English"]},
            "other@koenig-solutions.com": {"utilization": 10, "languages": ["Spanish"]},
        }
        batch = {
            "course_name": "AZ-900 Azure Fundamentals", "customer": "Microsoft",
            "delivery_mode": "ILO", "start_date": "2026-08-20", "end_date": "2026-08-21",
            "location": "", "language": "English",
        }
        _match, candidates, _coverage = backend._rank_batch(
            batch, team, availability_sources=sources, candidate_context=context
        )
        self.assertEqual(candidates[0]["trainer_email"], "english@koenig-solutions.com")
        self.assertEqual(
            set(candidates[0]["suitability_components"]),
            {"skill", "readiness", "availability", "utilization", "feedback", "language", "location", "certification"},
        )

    def test_aishwar_recommendation_carries_verified_weekend_and_level_eight(self):
        batch = {"delivery_mode_kind": "FMAT", "is_international": True}
        candidates = [{
            "trainer_name": "Aishwar (You)", "trainer_email": backend._AISHWAR_EMAIL,
            "match": 80,
        }]
        evidence = {
            "status": "available", "verified": True, "reason": "No conflicts", "conflicts": [],
        }
        result = backend._aishwar_recommendation(
            batch, candidates, weekend_availability=(date(2026, 8, 22), evidence)
        )
        self.assertEqual(result["suggested_skill_level"], 8)
        self.assertEqual(result["suggested_availability"], "2026-08-22")
        self.assertTrue(result["availability_verified"])


if __name__ == "__main__":
    unittest.main()
