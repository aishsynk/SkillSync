"""MORNING_TEAM_GREETING — server-side weekday greeting through CommunicationService."""

import os
import tempfile
import unittest
from unittest import mock

import backend
from repositories.communication_store import CommunicationStore
from services.communication import composer
from services.communication.service import CommunicationService

MANAGER = "comm@koenig-solutions.com"
WEEKDAYS = ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY"]


class MorningGreetingServiceTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.svc = CommunicationService(CommunicationStore(os.path.join(self.temp.name, "c.sqlite3")))

    def tearDown(self):
        self.temp.cleanup()

    def gen(self, day, recent=None, variation=0):
        return self.svc.generate(MANAGER, {
            "purpose": "MORNING_TEAM_GREETING", "localWeekday": day,
            "recentGreetings": recent or [], "variation": variation,
        })

    def test_prompt_carries_every_weekday_personality_and_the_style_rules(self):
        p = composer.MORNING_GREETING_PROMPT
        for marker in ("MONDAY - FRESH START", "TUESDAY - IN THE FLOW", "WEDNESDAY - MIDWEEK RESET",
                       "THURSDAY - TAKING SHAPE", "FRIDAY - FUN + WEEKEND", "ANTI-REPEAT", "No code fences"):
            self.assertIn(marker, p)
        for phrase in composer.MORNING_BANNED_PHRASES:
            self.assertIn(phrase, p)

    def test_every_weekday_produces_a_valid_greeting(self):
        for day in WEEKDAYS:
            r = self.gen(day)
            self.assertTrue(r.requires_communication)
            self.assertTrue(r.text)
            self.assertTrue(r.validation.passed, r.validation.issues)
            self.assertEqual("MORNING_TEAM_GREETING", r.purpose)

    def test_weekend_is_suppressed(self):
        for day in ("SATURDAY", "SUNDAY", ""):
            r = self.gen(day)
            self.assertFalse(r.requires_communication)
            self.assertEqual("", r.text)

    def test_configured_model_is_used_first_and_its_output_is_sanitised(self):
        raw = "```\nGenerated message:\nMidweek already, everyone.\n\n**Halfway through** - share what works. _Have a good Wednesday._\n```"
        with mock.patch.object(composer, "_try_llm_morning", return_value=(raw, "LLM_OPENAI")) as llm:
            r = self.gen("WEDNESDAY")
        llm.assert_called_once()
        self.assertEqual("LLM_OPENAI", r.generation_mode)
        self.assertNotIn("```", r.text)
        self.assertNotIn("Generated message", r.text)
        self.assertNotIn("**", r.text)
        self.assertTrue(r.text.startswith("Midweek already, everyone."))
        self.assertIn("*Halfway through*", r.text)

    def test_model_output_breaking_policy_falls_back_to_the_deterministic_generator(self):
        bad = "Good morning team! Stay focused and make today count."
        with mock.patch.object(composer, "_try_llm_morning", return_value=(bad, "LLM_OPENAI")):
            r = self.gen("TUESDAY")
        self.assertEqual("DETERMINISTIC_GENERATOR", r.generation_mode)
        self.assertNotIn("stay focused", r.text.lower())

    def test_recent_greetings_are_not_repeated(self):
        first = self.gen("THURSDAY").text
        second = self.gen("THURSDAY", recent=[first]).text
        self.assertNotEqual(first.split("\n")[0], second.split("\n")[0])

    def test_api_route_returns_the_greeting(self):
        prev = backend._communication_service
        backend._communication_service = self.svc
        backend._sessions["comm-session"] = {"email": MANAGER, "role": "manager"}
        try:
            res = backend.app.test_client().post(
                "/api/v2/communication/generate",
                json={"manager": MANAGER, "purpose": "MORNING_TEAM_GREETING", "localWeekday": "FRIDAY", "recentGreetings": []},
                headers={"Authorization": "Bearer comm-session"},
            )
        finally:
            backend._communication_service = prev
            backend._sessions.pop("comm-session", None)
        self.assertEqual(200, res.status_code)
        body = res.get_json()
        self.assertTrue(body["message"])
        self.assertEqual("MORNING_TEAM_GREETING", body["purpose"])
        self.assertTrue(body["requires_communication"])


if __name__ == "__main__":
    unittest.main()
