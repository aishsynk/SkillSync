import os
import tempfile
import unittest
from unittest.mock import patch

import backend
from action_store import ActionStore
from dev_plan_store import DevPlanStore

MANAGER = "manager@koenig-solutions.com"
T1 = "t1@koenig-solutions.com"
T2 = "t2@koenig-solutions.com"
OUTSIDER = "outsider@koenig-solutions.com"


class StrategicCapabilitiesTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.prev_devplan = backend._devplan_repository
        self.prev_action = backend._action_repository
        backend._devplan_repository = DevPlanStore(os.path.join(self.temp.name, "devplans.sqlite3"))
        backend._action_repository = ActionStore(os.path.join(self.temp.name, "actions.sqlite3"))
        backend._sessions.clear()
        backend._sessions["mgr-session"] = {"email": MANAGER, "role": "manager"}
        self.client = backend.app.test_client()
        self.headers = {"Authorization": "Bearer mgr-session"}

    def tearDown(self):
        backend._devplan_repository = self.prev_devplan
        backend._action_repository = self.prev_action
        backend._sessions.clear()
        self.temp.cleanup()

    def test_pipeline_radar_strips_fee_and_calculates_lead_time(self):
        """Test Pre-Demand Pipeline Radar correctly strips fee/currency and groups SCs."""
        mock_sc_rows = [
            {
                "SCId": "SC-1001",
                "CourseName": "AZ-104: Microsoft Azure Administrator",
                "CSM": "Rahul Sharma",
                "AssignmentId": "",
                "SCCreatedDate": "2026-08-15",
                "Total Fee": "50000",
                "Currency": "INR",
            },
            {
                "SCId": "SC-1002",
                "CourseName": "AWS Solutions Architect Associate",
                "CSM": "Priya Verma",
                "AssignmentId": "99012",
                "SCCreatedDate": "2026-08-20",
                "Total Fee": "75000",
                "Currency": "USD",
            }
        ]

        def mock_rms(api, body, *a, **k):
            if api == "activeSCDate":
                return mock_sc_rows
            if api == "reportees":
                return [{"OffEmail": T1, "TrainerName": "Trainer One"}]
            return []

        def mock_skills(email):
            if email == T1:
                return [{"course_name": "AZ-104: Microsoft Azure Administrator", "skill_level": 8}]
            return []

        with patch.object(backend, "_rms", side_effect=mock_rms), \
             patch.object(backend, "_skills", side_effect=mock_skills):
            resp = self.client.get("/api/v2/planning/pipeline", headers=self.headers)
            self.assertEqual(resp.status_code, 200)
            data = resp.get_json()
            self.assertEqual(data["total_orders"], 2)
            self.assertEqual(data["covered_orders"], 1)
            self.assertEqual(data["uncovered_orders"], 1)
            
            # Verify Total Fee and Currency are strictly stripped
            for item in data["pipeline_items"]:
                self.assertNotIn("Total Fee", item)
                self.assertNotIn("Currency", item)
                self.assertIn("lead_time_days", item)
                self.assertIn("matching_trainers", item)

    def test_delivery_compliance_detects_missing_recording_and_composes_nudge(self):
        """Test Live Delivery Compliance Sentinel identifies missing recordings and prepares nudge."""
        mock_prev_upcoming = [
            {
                "assignment_id": "88001",
                "course_name": "AZ-305: Azure Solutions Architect",
                "Startdate": "2026-08-30",
                "Enddate": "2026-09-03",
            }
        ]

        def mock_rms(api, body, *a, **k):
            if api == "reportees":
                return [{"OffEmail": T1, "TrainerName": "Trainer One"}]
            if api == "prevUpcoming":
                return mock_prev_upcoming
            if api == "recordingDetails":
                # Day 3 of delivery, but 0 recordings uploaded
                return []
            return []

        with patch.object(backend, "_rms", side_effect=mock_rms):
            resp = self.client.get("/api/v2/delivery/compliance", headers=self.headers)
            self.assertEqual(resp.status_code, 200)
            data = resp.get_json()
            self.assertEqual(data["total_active"], 1)
            self.assertEqual(data["violations_count"], 1)
            delivery = data["active_deliveries"][0]
            self.assertEqual(delivery["compliance_status"], "RECORDING_MISSING_URGENT")
            self.assertIn("Hello Trainer", delivery["nudge_message"])
            self.assertIn("AZ-305", delivery["nudge_message"])

    def test_skill_endorsement_enforces_manager_scope_and_updates_devplan(self):
        """Test 1-Tap IDP Skill Endorsement enforces reportee scope and updates DevPlan."""
        # Create a pending dev plan item for T1
        item = backend._devplan_repository.create(
            MANAGER, T1, "Master AZ-305 exam", "certification", "2026-09-30"
        )
        self.assertEqual(item["status"], "open")

        def mock_rms(api, body, *a, **k):
            if api == "reportees":
                return [{"OffEmail": T1, "TrainerName": "Trainer One"}]
            if api == "addTrainerSkill":
                return [{"status": "Success", "message": "Skill added"}]
            return []

        with patch.object(backend, "_rms", side_effect=mock_rms):
            # Test outsider rejection
            bad_resp = self.client.post("/api/v2/skills/endorse", json={
                "manager_email": MANAGER,
                "trainer_email": OUTSIDER,
                "course_id": "101",
                "course_name": "AZ-305",
                "skill_level": 8,
            }, headers=self.headers)
            self.assertEqual(bad_resp.status_code, 403)

            # Test successful endorsement for T1
            ok_resp = self.client.post("/api/v2/skills/endorse", json={
                "manager_email": MANAGER,
                "trainer_email": T1,
                "course_id": "101",
                "course_name": "AZ-305",
                "skill_level": 8,
                "dev_plan_id": item["id"],
            }, headers=self.headers)
            self.assertEqual(ok_resp.status_code, 200)
            data = ok_resp.get_json()
            self.assertTrue(data["ok"])
            self.assertEqual(data["skill_level"], 8)

            # Verify DevPlan item updated to 'done'
            updated_items = backend._devplan_repository.list_items(MANAGER, T1)
            self.assertEqual(updated_items[0]["status"], "done")

    def test_sentiment_keyword_extraction_and_quote_categorization(self):
        """Test Learner Voice sentiment engine extracts keyword clouds and representative quotes."""
        mock_feedback = [
            {
                "TrainerEmail": T1,
                "TextAnswer": "The hands-on labs were fantastic and the instructor had deep knowledge of Azure.",
                "MCQAnswer": "5",
                "FeedBackDate": "2026-08-25",
            },
            {
                "TrainerEmail": T1,
                "TextAnswer": "Clear explanations and patient with doubts, but the pacing was a bit too fast on day 2.",
                "MCQAnswer": "4",
                "FeedBackDate": "2026-08-26",
            }
        ]

        def mock_rms(api, body, *a, **k):
            if api == "trainerFeedback":
                return mock_feedback
            return []

        with patch.object(backend, "_rms", side_effect=mock_rms):
            resp = self.client.get(f"/api/v2/trainer/sentiment?trainer_email={T1}", headers=self.headers)
            self.assertEqual(resp.status_code, 200)
            data = resp.get_json()
            self.assertEqual(data["trainer_email"], T1)
            self.assertGreaterEqual(data["positive_percent"], 90.0)
            
            praise_keys = [p["keyword"] for p in data["praise_keywords"]]
            self.assertIn("hands-on labs", praise_keys)
            self.assertIn("deep knowledge", praise_keys)
            
            growth_keys = [g["keyword"] for g in data["growth_keywords"]]
            self.assertIn("pacing & speed", growth_keys)
            
            self.assertTrue(len(data["representative_quotes"]["strengths"]) > 0)
            self.assertTrue(len(data["representative_quotes"]["growth"]) > 0)


if __name__ == "__main__":
    unittest.main()
