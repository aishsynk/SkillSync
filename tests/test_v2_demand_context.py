import unittest
from unittest.mock import patch

import backend


class V2DemandContextTests(unittest.TestCase):
    def setUp(self):
        backend._sessions.clear()
        backend._sessions["v2-session"] = {
            "email": "manager@koenig-solutions.com",
            "role": "manager",
        }
        self.client = backend.app.test_client()
        self.headers = {"Authorization": "Bearer v2-session"}

    def test_requires_session(self):
        response = self.client.get(
            "/api/v2/operations/demand-context?manager=manager@koenig-solutions.com&demandId=265295&courseName=DP-700"
        )
        self.assertEqual(401, response.status_code)

    def test_rejects_cross_manager_access(self):
        response = self.client.get(
            "/api/v2/operations/demand-context?manager=other@koenig-solutions.com&demandId=265295&courseName=DP-700",
            headers=self.headers,
        )
        self.assertEqual(403, response.status_code)
        self.assertEqual("MANAGER_SCOPE_MISMATCH", response.get_json()["code"])

    @patch("backend._rms")
    def test_returns_typed_verified_context(self, rms):
        def answer(name, _body):
            if name == "courseAvailability":
                return [{
                    "Course Available in RMS": "Yes", "Course Status": "Active",
                    "Is Duplicate": "No", "Is Discontinued": "No",
                }]
            return [{"SCIDs": "SC-20, SC-10;SC-20"}]
        rms.side_effect = answer

        response = self.client.get(
            "/api/v2/operations/demand-context?manager=manager@koenig-solutions.com&demandId=265295&courseName=DP-700",
            headers=self.headers,
        )
        body = response.get_json()
        self.assertEqual(200, response.status_code)
        self.assertEqual("2.1", body["schema_version"])
        self.assertEqual("verified", body["confidence"])
        self.assertEqual(["SC-10", "SC-20"], body["sales_confirmations"]["ids"])

    @patch("backend._rms", return_value=[])
    def test_zero_rows_are_reported_as_unverified(self, _rms):
        response = self.client.get(
            "/api/v2/operations/demand-context?manager=manager@koenig-solutions.com&demandId=265295&courseName=DP-700",
            headers=self.headers,
        )
        body = response.get_json()
        self.assertEqual("partial", body["confidence"])
        self.assertFalse(body["course"]["verified"])
        self.assertFalse(body["sales_confirmations"]["verified"])


if __name__ == "__main__":
    unittest.main()
