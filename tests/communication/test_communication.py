"""Communication Intelligence — engine + API tests.

Mirrors the five canonical scenarios:
  1. My Message only (delivery update)
  2. User Message overrides My Message (firmness + Monday context)
  3. Team message (travel advisory)
  4. Opportunity response using verified SkillEdge context
  5. Missing context (no invented date)
Plus purpose classification, validation rules, history persistence and API auth.
"""

import os
import tempfile
import unittest

import backend
from repositories.communication_store import CommunicationStore
from repositories.opportunity_store import OpportunityStore
from services.communication import policy
from services.communication.service import CommunicationService

MANAGER = "comm@koenig-solutions.com"


class BaseCommunicationTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.prev_comm = backend._communication_service
        self.prev_opp = backend._opportunity_repository
        backend._communication_service = CommunicationService(
            CommunicationStore(os.path.join(self.temp.name, "comm.sqlite3")))
        backend._opportunity_repository = OpportunityStore(
            os.path.join(self.temp.name, "opp.sqlite3"))
        backend._sessions.clear()
        backend._sessions["comm-session"] = {"email": MANAGER, "role": "manager"}
        self.client = backend.app.test_client()
        self.headers = {"Authorization": "Bearer comm-session"}

    def tearDown(self):
        backend._communication_service = self.prev_comm
        backend._opportunity_repository = self.prev_opp
        backend._sessions.clear()
        self.temp.cleanup()

    def generate(self, body, verified=None):
        if verified is not None:
            return backend._communication_service.generate(MANAGER, body, verified_context=verified)
        return backend._communication_service.generate(MANAGER, body)


class CommunicationEngineTests(BaseCommunicationTest):
    def test_my_message_only_delivery_update(self):
        out = self.generate({
            "recipient": {"name": "Gaurav Joshi", "type": "MANAGER"},
            "purpose": "DELIVERY_UPDATE",
            "myMessage": (
                "Tell Gaurav the session is going fine. There was around one hour of "
                "power cut from client side but we still completed what was required "
                "and took the assessment."
            ),
        })
        text = out.text
        self.assertTrue(out.validation.passed)
        self.assertEqual([], out.validation.issues)
        self.assertLessEqual(len(text), 1000)
        # structure: greeting / body / closing
        self.assertTrue(text.startswith("Hello"))
        self.assertIn("*Gaurav Joshi*", text)
        self.assertIn("*Thanks*", text)
        # positive, factual, mentions the issue without complaint
        self.assertIn("going well", text)
        self.assertIn("power interruption", text)
        self.assertIn("assessment", text)
        self.assertNotIn("complaint", text.lower())

    def test_user_message_overrides_firmness_and_monday(self):
        out = self.generate({
            "recipient": {"name": "", "type": "REPORTEE"},
            "userMessage": "Please make it firmer because the task has been pending since Monday.",
            "myMessage": "Can you please complete this when possible?",
        })
        text = out.text
        self.assertTrue(out.validation.passed)
        self.assertEqual("firm", out.tone)
        self.assertIn("Monday", text)
        self.assertIn("**", text)  # bold key action
        self.assertNotIn("when possible", text)  # soft phrasing replaced

    def test_team_message_travel_advisory(self):
        out = self.generate({
            "recipient": {"name": "", "type": "TEAM"},
            "purpose": "TRAVEL_COORDINATION",
            "myMessage": (
                "Tell the team that if they have FMAT or ILT travel coming up, they "
                "should coordinate with Travel Desk and Payroll in advance where cab "
                "arrangements outside India may be needed."
            ),
        })
        text = out.text
        self.assertTrue(out.validation.passed)
        self.assertTrue(text.startswith("Hello team,"))
        self.assertIn("Travel Desk", text)
        self.assertIn("Payroll", text)
        self.assertIn("cab", text.lower())
        self.assertNotIn("•", text)
        for line in text.splitlines():
            self.assertFalse(line.strip().startswith("- "))

    def test_opportunity_response_uses_verified_context_only(self):
        backend._opportunity_repository.create(MANAGER, {
            "id": "SE-TEST1",
            "course_code": "DP-700",
            "location": "Egypt",
            "course": "DP-700T00",
            "decision": "accept",
            "preparation_hours": "4-6 hours",
        })
        # goes through the API route so the route-side context resolution runs
        r = self.client.post("/api/v2/communication/generate", json={
            "manager": MANAGER,
            "recipient": {"name": "Gaurav Joshi", "type": "MANAGER"},
            "purpose": "OPPORTUNITY_RESPONSE",
            "relatedEntityId": "SE-TEST1",
            "myMessage": "say yes I can do it",
        }, headers=self.headers)
        self.assertEqual(200, r.status_code)
        body = r.get_json()
        text = body["message"]
        self.assertTrue(body["validation"]["passed"])
        self.assertIn("Yes, I can take this up", text)
        self.assertIn("DP-700", text)
        # only verified facts surfaced
        self.assertTrue(any(f.startswith("opportunity.") for f in body["facts_used"]))
        self.assertIn("*Gaurav Joshi*", text)
        self.assertNotIn("Egypt", text)  # location omitted unless verifiably relevant

    def test_opportunity_response_without_verified_record_stays_generic(self):
        out = self.generate({
            "recipient": {"name": "Gaurav Joshi", "type": "MANAGER"},
            "purpose": "OPPORTUNITY_RESPONSE",
            "relatedEntityId": "DOES-NOT-EXIST",
            "myMessage": "say yes I can do it",
        })
        self.assertEqual("OPPORTUNITY_RESPONSE", out.purpose)
        # never invents course/location that SkillEdge cannot verify
        self.assertNotIn("DP-700", out.text)

    def test_missing_context_preserves_friday_without_inventing_date(self):
        out = self.generate({
            "recipient": {"name": "Niharika", "type": "COLLEAGUE"},
            "myMessage": "Tell Niharika I will finish it by Friday.",
        })
        text = out.text
        self.assertIn("Friday", text)
        self.assertNotRegex(text, r"\d{1,2}[/-]\d{1,2}(?:[/-]\d{2,4})?")  # no invented calendar date

    def test_purpose_classification(self):
        self.assertEqual("DELIVERY_UPDATE",
                         self.generate({"purpose": "", "myMessage": "session going well"}).purpose)
        self.assertEqual("OPPORTUNITY_RESPONSE",
                         self.generate({"purpose": "", "myMessage": "can i do it yes"}).purpose)
        self.assertEqual("TRAVEL_COORDINATION",
                         self.generate({"purpose": "", "myMessage": "cab to airport please"}).purpose)


class CommunicationValidatorTests(BaseCommunicationTest):
    def test_validation_blocks_emoji_bullets_and_oversize(self):
        self.assertTrue(policy.has_emoji("hello \U0001f642"))
        self.assertFalse(policy.has_emoji("plain text"))
        self.assertTrue(policy.has_bullet_list("a\n- item\nb"))
        self.assertTrue(policy.has_numbered_list("1. first\n2. second"))
        self.assertGreater(policy.MAX_LENGTH, 900)
        self.assertEqual(policy.MAX_LENGTH, 1000)

    def test_history_save_and_list_via_api(self):
        self.assertEqual(401, self.client.get(
            f"/api/v2/communication/history?manager={MANAGER}").status_code)
        r = self.client.post("/api/v2/communication/save", json={
            "manager": MANAGER,
            "recipient": {"name": "Niharika", "type": "COLLEAGUE"},
            "message": "Hello *Niharika*,\nAll set for Friday.\n*Thanks*",
            "purpose": "STATUS_UPDATE",
            "relatedEntityId": "SE-TEST1",
            "status": "COPIED",
        }, headers=self.headers)
        self.assertEqual(200, r.status_code)
        msg_id = r.get_json()["id"]
        self.assertTrue(msg_id)

        items = self.client.get(
            f"/api/v2/communication/history?manager={MANAGER}", headers=self.headers).get_json()
        self.assertEqual(1, items["count"])
        self.assertEqual("Niharika", items["items"][0]["recipient"])
        self.assertEqual("COPIED", items["items"][0]["status"])
        self.assertEqual("SE-TEST1", items["items"][0]["relatedEntityId"])

    def test_generate_api_requires_auth(self):
        self.assertEqual(401, self.client.post(
            "/api/v2/communication/generate", json={
                "manager": MANAGER, "myMessage": "test",
            }).status_code)

    def test_generate_api_end_to_end(self):
        r = self.client.post("/api/v2/communication/generate", json={
            "manager": MANAGER,
            "recipient": {"name": "Gaurav Joshi", "type": "MANAGER"},
            "purpose": "DELIVERY_UPDATE",
            "myMessage": "session went well, assessment completed",
        }, headers=self.headers)
        self.assertEqual(200, r.status_code)
        body = r.get_json()
        self.assertTrue(body["message"].startswith("Hello"))
        self.assertTrue(body["validation"]["passed"])


if __name__ == "__main__":
    unittest.main()