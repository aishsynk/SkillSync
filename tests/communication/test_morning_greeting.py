"""MORNING_TEAM_GREETING — server-side weekday greeting through CommunicationService."""

import os
import tempfile
import unittest
from unittest import mock

import backend
from repositories.communication_store import CommunicationStore
from services.communication import composer, providers
from services.communication.providers import ProviderOutcome, ProviderResult
from services.communication.service import CommunicationService

MANAGER = "comm@koenig-solutions.com"
WEEKDAYS = ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY"]
def ok(text, provider="OLLAMA", model="m", ms=40):
    return ProviderOutcome(result=ProviderResult(text, provider, model), elapsed_ms=ms, tried=["ollama"])


def core(provenance):
    """Provenance without latency, which varies run to run."""
    return {k: v for k, v in provenance.items() if k != "elapsed_ms"}


GOOD_WEDNESDAY = "Midweek already, everyone.\n\n*Halfway through* - share what works and help someone past a hurdle. _Have a good Wednesday._"


class MorningGreetingServiceTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.svc = CommunicationService(CommunicationStore(os.path.join(self.temp.name, "c.sqlite3")))
        self.env = mock.patch.dict(os.environ, {}, clear=False)
        self.env.start()
        for k in ("OLLAMA_BASE_URL", "OLLAMA_MODEL", "OPENAI_API_KEY", "AZURE_OPENAI_ENDPOINT", "AZURE_OPENAI_KEY", "AZURE_OPENAI_API_KEY", "COMMUNICATION_PROVIDER_ORDER"):
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
            self.assertEqual({"provider": "DETERMINISTIC", "model": "", "fallback_used": True, "attempts": 0, "attempted_providers": []}, core(r.provenance))

    def test_weekend_is_suppressed_and_never_calls_a_model(self):
        with mock.patch.object(providers, "generate") as gen:
            for day in ("SATURDAY", "SUNDAY", ""):
                r = self.gen(day)
                self.assertFalse(r.requires_communication)
                self.assertEqual("", r.text)
        gen.assert_not_called()

    def test_valid_model_greeting_is_accepted_sanitised_with_provenance(self):
        raw = "```\nGenerated message:\n" + GOOD_WEDNESDAY.replace("*Halfway through*", "**Halfway through**") + "\n```"
        with mock.patch.object(providers, "generate", return_value=ok(raw, model="qwen2.5:7b")) as gen:
            r = self.gen("WEDNESDAY")
        gen.assert_called_once()
        self.assertEqual(GOOD_WEDNESDAY, r.text)
        self.assertEqual("LLM_OLLAMA", r.generation_mode)
        self.assertEqual({"provider": "OLLAMA", "model": "qwen2.5:7b", "fallback_used": False, "attempts": 1, "attempted_providers": ["ollama"]}, core(r.provenance))
        self.assertEqual(40, r.provenance["elapsed_ms"])

    def test_invalid_greeting_is_retried_once_with_corrective_guidance(self):
        bad = "Good morning team! Stay focused and make today count."
        with mock.patch.object(providers, "generate", side_effect=[ok(bad), ok(GOOD_WEDNESDAY)]) as gen:
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
        with mock.patch.object(providers, "generate", return_value=ok(bad)) as gen:
            r = self.gen("WEDNESDAY")
        self.assertEqual(2, gen.call_count)  # exactly one retry, never more
        self.assertNotIn("crush", r.text.lower())
        self.assertEqual("DETERMINISTIC_GENERATOR", r.generation_mode)
        self.assertEqual({"provider": "DETERMINISTIC", "model": "", "fallback_used": True, "attempts": 2, "attempted_providers": ["ollama"]}, core(r.provenance))
        self.assertEqual(80, r.provenance["elapsed_ms"])

    def test_timeout_falls_back_immediately_without_a_corrective_retry(self):
        timed_out = ProviderOutcome(result=None, elapsed_ms=10_000, timeout_reason="OLLAMA timed out after 10000ms", tried=["ollama"])
        with mock.patch.object(providers, "generate", return_value=timed_out) as gen:
            r = self.gen("WEDNESDAY")
        self.assertEqual(1, gen.call_count)
        self.assertTrue(r.text)
        self.assertEqual({"provider": "DETERMINISTIC", "model": "", "fallback_used": True, "attempts": 1,
                          "attempted_providers": ["ollama"],
                          "timeout_reason": "OLLAMA timed out after 10000ms"}, core(r.provenance))
        self.assertEqual(10_000, r.provenance["elapsed_ms"])

    def test_invalid_text_after_a_timed_out_provider_is_not_retried(self):
        # Ollama timed out, a slower cloud provider answered badly: no second round.
        slow_bad = ProviderOutcome(result=ProviderResult("Good morning team! Stay focused.", "OPENAI", "gpt"),
                                   elapsed_ms=12_000, timeout_reason="OLLAMA timed out after 10000ms", tried=["ollama", "openai"])
        with mock.patch.object(providers, "generate", return_value=slow_bad) as gen:
            r = self.gen("WEDNESDAY")
        self.assertEqual(1, gen.call_count)
        self.assertEqual("DETERMINISTIC", r.provenance["provider"])
        self.assertEqual(1, r.provenance["attempts"])

    def test_stock_motivational_language_is_rejected_whatever_the_punctuation(self):
        for bad in ("Today's a good day to catch up and learn. Let's make it shine!",
                    "Nice work midweek, everyone - stay awesome, team, and share a win.",
                    "Halfway through the week, so keep pushing forward and help each other.",
                    "Share one thing you learned today, and let's CRUSH the rest of it.",
                    "A good midweek check-in, everyone. Keep it going strong!",
                    "Share what is working today. Onwards and upwards, everyone!"):
            issues = composer.morning_greeting_issues(bad, [], "WEDNESDAY")
            self.assertTrue(any(i.startswith("banned phrase") for i in issues), bad)
        self.assertEqual([], composer.morning_greeting_issues(GOOD_WEDNESDAY, [], "WEDNESDAY"))

    def test_mediocre_model_output_is_replaced_by_the_weekday_bank(self):
        shiny = "Today's a good day to catch up and learn from each other. Let's make it shine!"
        with mock.patch.object(providers, "generate", return_value=ok(shiny)):
            r = self.gen("WEDNESDAY")
        self.assertNotIn("make it shine", r.text.lower())
        self.assertEqual("DETERMINISTIC", r.provenance["provider"])
        self.assertEqual(["ollama"], r.provenance["attempted_providers"])

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
        issues = composer.morning_greeting_issues("*In the flow, team? Let's chat, learn, and help each other.*", [], "TUESDAY")
        self.assertIn("whole greeting wrapped in one formatting marker", issues)
        issues = composer.morning_greeting_issues("Good afternoon, team. How's the week going? Keep it going strong!", [], "WEDNESDAY")
        self.assertIn("not a morning greeting", issues)

    def test_whole_request_stays_inside_the_interactive_budget(self):
        calls = []

        def slow_invalid(system, user, temperature=0.2, max_tokens=400, budget_ms=None):
            calls.append(budget_ms)
            return ok("Good morning team! Stay focused.", ms=15_000)

        with mock.patch.object(providers, "generate", side_effect=slow_invalid):
            r = self.gen("WEDNESDAY")
        self.assertEqual([20_000, 5_000], calls)  # retry only gets what is left of 20s
        self.assertEqual("DETERMINISTIC", r.provenance["provider"])

        calls.clear()

        def very_slow_invalid(system, user, temperature=0.2, max_tokens=400, budget_ms=None):
            calls.append(budget_ms)
            return ok("Good morning team! Stay focused.", ms=19_000)

        with mock.patch.object(providers, "generate", side_effect=very_slow_invalid):
            self.gen("WEDNESDAY")
        self.assertEqual([20_000], calls)  # under 2s left: no retry
        issues = composer.morning_greeting_issues("Hi all, halfway there. *Share a quick win!* _Take care._",
                                                  ["Hi all, halfway there.\n\nOld note."], "WEDNESDAY")
        self.assertIn("repeats a recent opening", issues)
        issues = composer.morning_greeting_issues("Good morning team,\n\nHalfway through, share the small wins. _Take care._", [], "WEDNESDAY")
        self.assertIn("stock opening", issues)

    def test_recent_greetings_are_sent_to_the_model_and_near_duplicates_rejected(self):
        recent = ["Hi all, halfway there.\n\nGood day to learn one small thing from someone on the team. _Take care._"]
        with mock.patch.object(providers, "generate", return_value=ok(GOOD_WEDNESDAY)) as gen:
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
