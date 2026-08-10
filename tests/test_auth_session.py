import unittest

import backend


class SessionBoundaryTests(unittest.TestCase):
    def setUp(self):
        backend._sessions.clear()
        backend._sessions["valid-session"] = {
            "email": "manager@koenig-solutions.com",
            "role": "manager",
        }
        self.client = backend.app.test_client()

    def test_session_endpoint_rejects_missing_token(self):
        response = self.client.get("/api/auth/session")

        self.assertEqual(401, response.status_code)
        self.assertEqual("SESSION_REQUIRED", response.get_json()["code"])

    def test_session_endpoint_returns_authenticated_identity(self):
        response = self.client.get(
            "/api/auth/session",
            headers={"Authorization": "Bearer valid-session"},
        )

        self.assertEqual(200, response.status_code)
        self.assertTrue(response.get_json()["authenticated"])
        self.assertEqual("manager@koenig-solutions.com", response.get_json()["email"])

    def test_logout_revokes_the_presented_session(self):
        response = self.client.post(
            "/api/auth/logout",
            headers={"Authorization": "Bearer valid-session"},
        )

        self.assertEqual(200, response.status_code)
        self.assertNotIn("valid-session", backend._sessions)


if __name__ == "__main__":
    unittest.main()
