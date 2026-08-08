import unittest
from unittest.mock import patch

import backend


class SkillMarkingReliabilityTests(unittest.TestCase):
    def setUp(self):
        self.client = backend.app.test_client()
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

        response = self.client.post("/api/action/mark-skill", json=self.payload)

        self.assertEqual(200, response.status_code)
        self.assertTrue(response.get_json()["verified"])
        self.assertEqual(2, rms.call_count)
        for call in rms.call_args_list:
            self.assertEqual(6, call.kwargs["timeout"])
            self.assertEqual(1, call.kwargs["attempts"])

    @patch.object(backend, "_emp_code", return_value="1001")
    @patch.object(backend, "_rms", return_value=None)
    def test_rms_timeout_returns_structured_503_not_proxy_502(self, _rms, _emp):
        response = self.client.post("/api/action/mark-skill", json=self.payload)

        self.assertEqual(503, response.status_code)
        body = response.get_json()
        self.assertFalse(body["success"])
        self.assertFalse(body["verified"])
        self.assertIn("did not answer in time", body["error"])


if __name__ == "__main__":
    unittest.main()
