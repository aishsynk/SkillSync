import unittest
from unittest.mock import patch

import backend


class SkillMarkingReliabilityTests(unittest.TestCase):
    def setUp(self):
        backend._sessions.clear()
        backend._sessions["test-session"] = {
            "email": "manager@koenig-solutions.com",
            "role": "manager",
        }
        self.client = backend.app.test_client()
        self.headers = {"Authorization": "Bearer test-session"}
        self.payload = {
            "course_id": "17997",
            "trainer_email": "aishwar.c@koenig-solutions.com",
            "skill_level": 8,
            "from_date": "2026-08-09",
            "officially_approved": "No",
        }

    @patch.object(backend, "_cache_purge")
    @patch.object(backend, "_emp_code", return_value="1001")
    @patch.object(backend, "_rms")
    def test_write_is_bounded_and_verified_without_pre_read(self, rms, _emp, _purge):
        rms.side_effect = [
            [{"JSON_RESULT": None}],
            [{"course_id": 17997, "course_name": "AI-102"}],
        ]

        response = self.client.post("/api/action/mark-skill", json=self.payload, headers=self.headers)

        self.assertEqual(200, response.status_code)
        self.assertTrue(response.get_json()["verified"])
        self.assertEqual(2, rms.call_count)
        for call in rms.call_args_list:
            self.assertEqual(6, call.kwargs["timeout"])
            self.assertEqual(1, call.kwargs["attempts"])

    @patch.object(backend, "_emp_code", return_value="1001")
    @patch.object(backend, "_rms", return_value=None)
    def test_rms_timeout_returns_structured_503_not_proxy_502(self, _rms, _emp):
        response = self.client.post("/api/action/mark-skill", json=self.payload, headers=self.headers)

        self.assertEqual(503, response.status_code)
        body = response.get_json()
        self.assertFalse(body["success"])
        self.assertFalse(body["verified"])
        self.assertIn("did not answer in time", body["error"])

    def test_mark_skill_requires_a_session(self):
        backend._sessions.clear()
        response = self.client.post("/api/action/mark-skill", json=self.payload)

        self.assertEqual(401, response.status_code)
        self.assertEqual("SESSION_REQUIRED", response.get_json()["code"])

    def test_mark_skill_rejects_unknown_session_token(self):
        response = self.client.post(
            "/api/action/mark-skill", json=self.payload,
            headers={"Authorization": "Bearer does-not-exist"},
        )

        self.assertEqual(401, response.status_code)
        self.assertEqual("SESSION_REQUIRED", response.get_json()["code"])

    @patch.object(backend, "_course_catalogue_index")
    def test_course_search_includes_unowned_rms_catalogue_courses(self, catalogue):
        catalogue.return_value = {
            "dp 700 microsoft fabric data engineer": {
                "course_id": 22001,
                "course_name": "DP-700 Microsoft Fabric Data Engineer",
                "syllabus_url": "https://example.test/dp700.pdf",
                "vendor": "Microsoft", "duration_days": 4,
            },
            "other course": {"course_id": 2, "course_name": "Other Course"},
        }
        response = self.client.get("/api/data/course-search?q=DP-700", headers=self.headers)
        self.assertEqual(200, response.status_code)
        body = response.get_json()
        self.assertEqual(1, body["count"])
        self.assertEqual("22001", body["courses"][0]["course_id"])
        self.assertEqual("Microsoft", body["courses"][0]["vendor"])

    @patch.object(backend, "_course_schedule")
    @patch.object(backend, "_course_catalogue_index")
    def test_course_intelligence_combines_metadata_and_verified_dates(self, catalogue, schedule):
        catalogue.return_value = {
            "ai 102": {"course_id": "9716", "course_name": "AI-102", "vendor": "Microsoft"}
        }
        schedule.return_value = {
            "course_id": "9716", "course_name": "AI-102T00: Develop AI Solutions in Azure",
            "schedule_dates": ["14-Aug-26 - 20-Aug-26"], "available": True,
        }
        response = self.client.get("/api/data/course-intelligence?courseName=AI-102", headers=self.headers)
        self.assertEqual(200, response.status_code)
        body = response.get_json()
        self.assertTrue(body["schedule_available"])
        self.assertEqual("Microsoft", body["vendor"])
        self.assertEqual(["14-Aug-26 - 20-Aug-26"], body["schedule_dates"])


if __name__ == "__main__":
    unittest.main()
