import os
import tempfile
import unittest
from unittest.mock import patch

import backend
from repositories.opportunity_store import OpportunityStore

MANAGER = "manager@koenig-solutions.com"


class OpportunityGuardianTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.previous = backend._opportunity_repository
        backend._opportunity_repository = OpportunityStore(
            os.path.join(self.temp.name, "opportunities.sqlite3"))
        backend._sessions.clear()
        backend._sessions["mgr-session"] = {"email": MANAGER, "role": "manager"}
        self.client = backend.app.test_client()
        self.headers = {"Authorization": "Bearer mgr-session"}

    def tearDown(self):
        backend._opportunity_repository = self.previous
        backend._sessions.clear()
        self.temp.cleanup()

    def _rms(self, api, body, *a, **k):
        return []

    def _create(self, **overrides):
        payload = {
            "manager": MANAGER,
            "source": "viber",
            "source_app": "Viber",
            "source_group": "Trailblazers",
            "sender": "Gaurav Joshi",
            "title": "Can anyone deliver AZ-104 next week?",
            "course": "AZ-104",
            "is_critical": True,
        }
        payload.update(overrides)
        with patch.object(backend, "_rms", side_effect=self._rms):
            return self.client.post("/api/v2/opportunities", json=payload, headers=self.headers)

    def test_auth_gate(self):
        self.assertEqual(401, self.client.get(
            f"/api/v2/opportunities?manager={MANAGER}").status_code)

    def test_create_then_list_persists(self):
        r = self._create()
        self.assertEqual(201, r.status_code)
        opp_id = r.get_json()["id"]
        self.assertTrue(opp_id.startswith("SE-"))

        with patch.object(backend, "_rms", side_effect=self._rms):
            body = self.client.get(
                f"/api/v2/opportunities?manager={MANAGER}&_build=1", headers=self.headers).get_json()
        self.assertEqual([opp_id], [i["id"] for i in body["items"]])
        self.assertEqual("detected", body["items"][0]["status"])
        self.assertEqual("Trailblazers", body["items"][0]["source_group"])

    def test_accepted_survives_a_reload(self):
        opp_id = self._create().get_json()["id"]
        with patch.object(backend, "_rms", side_effect=self._rms):
            self.client.post(
                f"/api/v2/opportunities/{opp_id}/accept", headers=self.headers)

            # A brand-new store instance over the same file proves persistence.
            backend._opportunity_repository = OpportunityStore(
                os.path.join(self.temp.name, "opportunities.sqlite3"))
            body = self.client.get(
                f"/api/v2/opportunities?manager={MANAGER}&_build=1", headers=self.headers).get_json()
        self.assertEqual("accepted", body["items"][0]["status"])

    def test_status_filter(self):
        self._create()
        opp_id = self._create(title="Second requirement").get_json()["id"]
        with patch.object(backend, "_rms", side_effect=self._rms):
            self.client.post(
                f"/api/v2/opportunities/{opp_id}/decline", headers=self.headers)
            body = self.client.get(
                f"/api/v2/opportunities?manager={MANAGER}&status=declined&_build=1",
                headers=self.headers).get_json()
        self.assertEqual([opp_id], [i["id"] for i in body["items"]])

    def test_guardian_config_persists(self):
        with patch.object(backend, "_rms", side_effect=self._rms):
            self.client.post(
                "/api/v2/opportunity/guardian-config?manager=%s" % MANAGER,
                json={"enabled": False, "quiet_hours_start": "22:00"}, headers=self.headers)
            backend._opportunity_repository = OpportunityStore(
                os.path.join(self.temp.name, "opportunities.sqlite3"))
            r = self.client.get(
                "/api/v2/opportunity/guardian-config?manager=%s&_build=1" % MANAGER,
                headers=self.headers)
        self.assertEqual(200, r.status_code)
        self.assertFalse(r.get_json()["enabled"])
        self.assertEqual("22:00", r.get_json()["quiet_hours_start"])

    def test_extract_structured_requirements(self):
        def no_rms(api, body, *a, **k):
            return []
        with patch.object(backend, "_rms", side_effect=no_rms):
            r = self.client.post(
                "/api/v2/opportunity/extract",
                json={"manager": MANAGER,
                      "text": "Can anyone deliver DP-600 in Dubai from 22 Sep 2026 to 26 Sep 2026? TOC attached."},
                headers=self.headers)
        self.assertEqual(200, r.status_code)
        body = r.get_json()
        self.assertEqual("DP-600", body["course_code"])
        self.assertEqual("2026-09-22", body["dates_start"])
        self.assertEqual("2026-09-26", body["dates_end"])
        self.assertEqual("Dubai", body["location"])
        self.assertEqual("UAE", body["country"])
        self.assertTrue(body["documentation_mentioned"])
        self.assertEqual("training requirement", body["action"])

    def test_document_status_transition(self):
        opp_id = self._create().get_json()["id"]
        with patch.object(backend, "_rms", side_effect=self._rms):
            r = self.client.post(
                f"/api/v2/opportunities/{opp_id}/document",
                json={"status": "shared"}, headers=self.headers)
            self.assertEqual(200, r.status_code)
            body = self.client.get(
                f"/api/v2/opportunities?manager={MANAGER}&_build=1", headers=self.headers).get_json()
        self.assertEqual("shared", body["items"][0]["document_status"])

    def test_document_status_invalid(self):
        opp_id = self._create().get_json()["id"]
        r = self.client.post(
            f"/api/v2/opportunities/{opp_id}/document",
            json={"status": "bogus"}, headers=self.headers)
        self.assertEqual(400, r.status_code)


if __name__ == "__main__":
    unittest.main()