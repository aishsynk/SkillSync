"""MORNING_TEAM_GREETING — server-side weekday greeting through CommunicationService."""

import os
import tempfile
import unittest
from unittest import mock

import backend
from repositories.communication_store import CommunicationStore
from services.communication import composer, providers
from services.communication.providers import ProviderResult
from services.communication.service import CommunicationService

MANAGER = "comm@koenig-solutions.com"
WEEKDAYS = ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY"]
GOOD_WEDNESDAY = "Midweek already, everyone.\n\n*Halfway through* - share what works and help someone past a hurdle. _Have a good Wednesday._"


class MorningGreetingServiceTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.svc = CommunicationService(CommunicationStore(os.path.join(self.temp.name, "c.sqlite3")))
        self.env = mock.patch.dict(os.environ, {}, clear=False)
        self.env.start()
        for k in ("OLLAMA_BASE_URL", "OLLAMA_MODEL", "OPENAI_API_KEY", "AZURE_OPENAI_ENDPOINT", "AZURE_OPENAI_KEY", "AZURE_OPENAI_API_KEY"):
            os.environ.pop(k, None)

    def tearDown(self):
        self.env.stop()
        self.temp.cleanup()

    def gen(self, day, recent=None, variation=0):
        return self.svc.generate(MANAGER, {
            "purpose": "MORNING_TEAM_GREETING", "localWeekday": day,
            "recentGreetings": recent or [], "variation": variation,
        })

    def test_prompt_carries_every_weekday_personality_and_the_style_rules(self):
        p = composer.MORNING_GREETING_PROMPT
        for marker in ("MONDAY - FRESH START", "TUESDAY - IN THE FLOW", "WEDNESDAY - MIDWEEK RESET",
                       "THURSDAY - TAKING SHAPE", "FRIDAY - FUN + WEEKEND", "ANTI-REPEAT", "No code fences",
                       "75-150 characters", "*bold*, _italic_, ~strike~"):
            self.assertIn(marker, p)
        for phrase in composer.MORNING_BANNED_PHRASES:
            self.assertIn(phrase, p)

    def test_every_weekday_produces_a_valid_greeting_without_any_model(self):
        for day in WEEKDAYS:
            r = self.gen(day)
            self.assertTrue(r.requires_communication)
            self.assertTrue(r.text)
            self.assertTrue(r.validation.passed, r.validation.issues)
            self.assertEqual({"provider": "DETERMINISTIC", "model": "", "fallback_used": True, "attempts": 0}, r.provenance)

    def test_weekend_is_suppressed_and_never_calls_a_model(self):
        with mock.patch.object(providers, "generate_text") as gen:
            for day in ("SATURDAY", "SUNDAY", ""):
                r = self.gen(day)
                self.assertFalse(r.requires_communication)
                self.assertEqual("", r.text)
        gen.assert_not_called()

    def test_valid_model_greeting_is_accepted_sanitised_with_provenance(self):
        raw = "```\nGenerated message:\n" + GOOD_WEDNESDAY.replace("*Halfway through*", "**Halfway through**") + "\n```"
        with mock.patch.object(providers, "generate_text", return_value=ProviderResult(raw, "OLLAMA", "qwen2.5:7b")) as gen:
            r = self.gen("WEDNESDAY")
        gen.assert_called_once()
        self.assertEqual(GOOD_WEDNESDAY, r.text)
        self.assertEqual("LLM_OLLAMA", r.generation_mode)
        self.assertEqual({"provider": "OLLAMA", "model": "qwen2.5:7b", "fallback_used": False, "attempts": 1}, r.provenance)

    def test_invalid_greeting_is_retried_once_with_corrective_guidance(self):
        bad = "Good morning team! Stay focused and make today count."
        with mock.patch.object(providers, "generate_text", side_effect=[
            ProviderResult(bad, "OLLAMA", "m"), ProviderResult(GOOD_WEDNESDAY, "OLLAMA", "m"),
        ]) as gen:
            r = self.gen("WEDNESDAY")
        self.assertEqual(2, gen.call_count)
        retry_prompt = gen.call_args_list[1].args[1]
        self.assertIn("previous attempt was rejected", retry_prompt)
        self.assertIn("banned phrase: stay focused", retry_prompt)
        self.assertEqual(GOOD_WEDNESDAY, r.text)
        self.assertEqual(2, r.provenance["attempts"])
        self.assertFalse(r.provenance["fallback_used"])

    def test_second_invalid_output_falls_back_to_the_deterministic_bank(self):
        bad = "Good morning team! Crush your goals this Tuesday."
        with mock.patch.object(providers, "generate_text", return_value=ProviderResult(bad, "OLLAMA", "m")) as gen:
            r = self.gen("WEDNESDAY")
        self.assertEqual(2, gen.call_count)  # exactly one retry, never more
        self.assertNotIn("crush", r.text.lower())
        self.assertEqual("DETERMINISTIC_GENERATOR", r.generation_mode)
        self.assertEqual({"provider": "DETERMINISTIC", "model": "", "fallback_used": True, "attempts": 2}, r.provenance)

    def test_wrong_weekday_personality_is_rejected(self):
        issues = composer.morning_greeting_issues("Happy Tuesday, all. _Enjoy the weekend soon._ Keep sharing ideas.", [], "WEDNESDAY")
        self.assertTrue(any("wrong day" in i for i in issues))
        self.assertTrue(any("weekend" in i for i in issues))
        self.assertEqual([], composer.morning_greeting_issues(GOOD_WEDNESDAY, [], "WEDNESDAY"))

    def test_real_model_quirks_are_cleaned_or_rejected(self):
        self.assertEqual("Midweek check-in? Share one thing you learned. _Take care._",
                         composer.sanitize_morning_greeting('"Midweek check-in? Share one thing you learned. _Take care._"'))
        issues = composer.morning_greeting_issues("Halfway through, share the small wins today. ~Let's chat!~", [], "WEDNESDAY")
        self.assertIn("strikethrough used as a closing", issues)
        issues = composer.morning_greeting_issues("Good morning, team! Share something useful today. _Enjoy._", [], "WEDNESDAY")
        self.assertIn("stock opening", issues)
        issues = composer.morning_greeting_issues("Hi all, halfway there. *Share a quick win!* _Take care._",
                                                  ["Hi all, halfway there.\n\nOld note."], "WEDNESDAY")
        self.assertIn("repeats a recent opening", issues)
        issues = composer.morning_greeting_issues("Good morning team,\n\nHalfway through, share the small wins. _Take care._", [], "WEDNESDAY")
        self.assertIn("stock opening", issues)

    def test_recent_greetings_are_sent_to_the_model_and_near_duplicates_rejected(self):
        recent = ["Hi all, halfway there.\n\nGood day to learn one small thing from someone on the team. _Take care._"]
        with mock.patch.object(providers, "generate_text", return_value=ProviderResult(GOOD_WEDNESDAY, "OLLAMA", "m")) as gen:
            self.gen("WEDNESDAY", recent=recent)
        self.assertIn(recent[0].split("\n")[0], gen.call_args.args[1])
        issues = composer.morning_greeting_issues(recent[0].replace("Take care", "Enjoy it"), recent, "WEDNESDAY")
        self.assertTrue(any("recent" in i for i in issues))

    def test_api_route_returns_the_greeting_and_diagnostic_provenance(self):
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
        self.assertEqual("DETERMINISTIC", body["provenance"]["provider"])


if __name__ == "__main__":
    unittest.main()
