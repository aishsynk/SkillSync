import unittest
import os
import tempfile

import backend
from action_store import SessionRevocationStore


class SessionBoundaryTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.previous_revocations = backend._session_revocations
        backend._session_revocations = SessionRevocationStore(
            os.path.join(self.temp.name, "sessions.sqlite3")
        )
        backend._sessions.clear()
        backend._sessions["valid-session"] = {
            "email": "manager@koenig-solutions.com",
            "role": "manager",
        }
        self.client = backend.app.test_client()

    def tearDown(self):
        backend._session_revocations = self.previous_revocations
        self.temp.cleanup()

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

    def test_logout_rejects_signed_token_after_memory_restart(self):
        token = backend._generate_session_token(
            "manager@koenig-solutions.com", "manager"
        )
        response = self.client.post(
            "/api/auth/logout",
            headers={"Authorization": f"Bearer {token}"},
        )
        backend._sessions.clear()
        validation = self.client.get(
            "/api/auth/session",
            headers={"Authorization": f"Bearer {token}"},
        )

        self.assertEqual(200, response.status_code)
        self.assertEqual(401, validation.status_code)

    def test_revocation_is_shared_by_a_new_store_instance(self):
        token = backend._generate_session_token(
            "manager@koenig-solutions.com", "manager"
        )
        self.client.post(
            "/api/auth/logout",
            headers={"Authorization": f"Bearer {token}"},
        )
        backend._sessions.clear()
        backend._session_revocations = SessionRevocationStore(
            os.path.join(self.temp.name, "sessions.sqlite3")
        )

        self.assertIsNone(backend._verify_session_token(token))


if __name__ == "__main__":
    unittest.main()
