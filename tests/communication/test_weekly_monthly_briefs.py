"""Weekly / monthly manager briefs through Communication Intelligence.

Facts-first: the planner selects verified facts, the model only writes, and the
validator rejects availability claims, invented urgency and stock language.
No real model is required — the provider boundary is mocked.
"""

import os
import tempfile
import unittest
from unittest import mock

from repositories.communication_store import CommunicationStore
from services.communication import briefs, providers
from services.communication.briefs import (
    CURRENT, PERIOD_END, REPORTEE_MONTH, REPORTEE_WEEK, TEAM_MONTH, TEAM_WEEK,
)
from services.communication.providers import ProviderOutcome, ProviderResult
from services.communication.service import CommunicationService

MANAGER = "comm@koenig-solutions.com"

TEAM_FACTS = {
    "manager_first": "Aishwar", "headcount": 8, "delivering": 5, "total_batches": 6, "total_pax": 74,
    "open_demand": 3, "coverable_open": 2, "bench": 2, "total_gaps": 4, "at_risk": 1,
    "earliest_uncovered_date": "2026-09-22", "earliest_uncovered_course": "DP-700T00",
    "avg_rating": 4.6, "top_performers": ["Priya Sharma"], "period_ref": "this week",
}
REPORTEE_FACTS = {
    "name": "Priya Sharma", "current_course": "AI-102T00", "batches_delivered": 2, "total_pax": 24,
    "utilisation": 88, "avg_rating": 4.7, "rating_count": 12, "cert_gap_courses": ["DP-700T00"],
    "period_ref": "this week",
}
GOOD_TEAM = ("Hello team,\n\nThree batches are open this week and two of them match skills we already hold. "
             "*Please tell me if you can pick up DP-700T00 from 22 September.*\n\n_Thanks for the steady delivery._")
GOOD_REPORTEE = ("Hi Priya,\n\nTwo batches delivered this week and learner feedback is averaging 4.7 across 12 responses. "
                 "*Worth booking the DP-700T00 certification while the run is going well.*\n\n_Good week._")


def ok(text, ms=50):
    return ProviderOutcome(result=ProviderResult(text, "OLLAMA", "m"), elapsed_ms=ms, tried=["ollama"])


class BriefFactSelectionTest(unittest.TestCase):
    def test_bench_is_never_offered_as_availability(self):
        facts = dict(briefs.select_brief_facts(TEAM_FACTS, TEAM_WEEK, CURRENT))
        self.assertNotIn("bench", facts)
        self.assertEqual(2, facts["low_utilisation_band_count"])
        self.assertFalse(any(k in facts for k in ("verified_available_count", "clear_days")))

    def test_authoritative_availability_is_passed_through_when_supplied(self):
        facts = dict(briefs.select_brief_facts({**TEAM_FACTS, "verified_available_count": 2}, TEAM_WEEK, CURRENT))
        self.assertEqual(2, facts["verified_available_count"])

    def test_reportee_selection_is_person_scoped(self):
        facts = dict(briefs.select_brief_facts(REPORTEE_FACTS, REPORTEE_WEEK, CURRENT))
        self.assertEqual("Priya Sharma", facts["name"])
        self.assertEqual(88, facts["utilisation"])
        self.assertNotIn("headcount", facts)

    def test_empty_and_missing_facts_are_dropped(self):
        facts = dict(briefs.select_brief_facts({"headcount": 4, "avg_rating": None, "top_performers": []}, TEAM_WEEK, CURRENT))
        self.assertEqual(4, facts["headcount"])
        self.assertNotIn("avg_rating", facts)
        self.assertNotIn("top_performers", facts)


class BriefPurposeAndTimeframeTest(unittest.TestCase):
    def test_four_purposes_exist_with_distinct_scope_and_period(self):
        self.assertEqual({("team", "week"), ("reportee", "week"), ("team", "month"), ("reportee", "month")},
                         {(p.scope, p.period) for p in briefs.BRIEF_PURPOSES.values()})

    def test_this_period_and_period_end_are_different_intents_not_string_swaps(self):
        for purpose in (TEAM_WEEK, TEAM_MONTH, REPORTEE_WEEK, REPORTEE_MONTH):
            spec = briefs.BRIEF_PURPOSES[purpose]
            self.assertNotEqual(spec.intent(CURRENT), spec.intent(PERIOD_END))
            # period_end looks back and hands off; current looks at the period in progress.
            ending = spec.intent(PERIOD_END).lower()
            self.assertTrue(any(w in ending for w in ("close", "closing", "review", "retrospective")), ending)
        current = briefs.brief_prompt_user(TEAM_MONTH, CURRENT, briefs.select_brief_facts(TEAM_FACTS, TEAM_MONTH, CURRENT))
        ending = briefs.brief_prompt_user(TEAM_MONTH, PERIOD_END, briefs.select_brief_facts(TEAM_FACTS, TEAM_MONTH, PERIOD_END))
        self.assertIn("the period in progress", current)
        self.assertIn("retrospective", ending)
        self.assertNotEqual(current, ending)

    def test_prompt_carries_the_invariants(self):
        p = briefs.BRIEF_PROMPT
        for marker in ("Low utilisation is NOT availability", "Never invent urgency", "No code fences",
                       "*bold*", "reportee message is about that person"):
            self.assertIn(marker, p)


class BriefValidationTest(unittest.TestCase):
    def facts(self, extra=None):
        return briefs.select_brief_facts({**TEAM_FACTS, **(extra or {})}, TEAM_WEEK, CURRENT)

    def test_good_messages_pass(self):
        self.assertEqual([], briefs.brief_issues(GOOD_TEAM, self.facts(), TEAM_WEEK))
        self.assertEqual([], briefs.brief_issues(GOOD_REPORTEE, briefs.select_brief_facts(REPORTEE_FACTS, REPORTEE_WEEK, CURRENT),
                                                 REPORTEE_WEEK, "Priya Sharma"))

    def test_availability_claims_are_rejected_without_an_authoritative_fact(self):
        for claim in ("Hello team,\n\nTwo of us are free this week, so please pick up the open batch.\n\n_Thanks_",
                      "Hello team,\n\nWe have spare capacity this week and three open batches to cover.\n\n_Thanks_",
                      "Hello team,\n\nTwo trainers are sitting idle while DP-700T00 is unstaffed.\n\n_Thanks_"):
            issues = briefs.brief_issues(claim, self.facts(), TEAM_WEEK)
            self.assertTrue(any("availability" in i for i in issues), claim)

    def test_availability_claim_is_allowed_with_authoritative_evidence(self):
        allowed = "Hello team,\n\nTwo of us are free this week from the confirmed schedule, so DP-700T00 is coverable.\n\n_Thanks_"
        self.assertEqual([], briefs.brief_issues(allowed, self.facts({"verified_available_count": 2}), TEAM_WEEK))

    def test_invented_day_urgency_is_rejected_without_a_dated_fact(self):
        text = "Hello team,\n\nPlease confirm your delivery plan today so we can close the open batches.\n\n_Thanks_"
        no_dates = briefs.select_brief_facts({"headcount": 8, "open_demand": 3}, TEAM_WEEK, CURRENT)
        self.assertTrue(any("urgency" in i for i in briefs.brief_issues(text, no_dates, TEAM_WEEK)))
        self.assertEqual([], briefs.brief_issues(text, self.facts(), TEAM_WEEK))  # earliest_uncovered_date present

    def test_stock_language_and_markup_are_rejected(self):
        cases = {
            "Hello team,\n\nPlease act on your part today and keep me posted on the open batches here.\n\n_Thanks_": "banned phrase",
            "Hello team,\n\nThree batches are open and **two are coverable** by the team this week.\n\n_Thanks_": "markdown doubles",
            "Hello team,\n\n- three batches open\n- two coverable\n\n_Thanks_": "bullet",
            "```\nHello team,\n\nThree batches are open this week for the team to cover.\n\n_Thanks_\n```": "code fence",
        }
        for text, expected in cases.items():
            self.assertTrue(any(expected in i for i in briefs.brief_issues(text, self.facts(), TEAM_WEEK)), text)

    def test_a_reportee_message_must_be_about_that_person(self):
        team_paragraph = "Hello team,\n\nTwo batches were delivered this week across the team with good feedback.\n\n_Thanks_"
        issues = briefs.brief_issues(team_paragraph, briefs.select_brief_facts(REPORTEE_FACTS, REPORTEE_WEEK, CURRENT),
                                     REPORTEE_WEEK, "Priya Sharma")
        self.assertIn("reportee message does not address the person", issues)
        self.assertIn("reportee message addresses the team", issues)

    def test_repeated_openings_are_rejected(self):
        issues = briefs.brief_issues(GOOD_TEAM, self.facts(), TEAM_WEEK, recent=[GOOD_TEAM])
        self.assertIn("repeats a recent opening", issues)


class BriefServiceTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.svc = CommunicationService(CommunicationStore(os.path.join(self.temp.name, "c.sqlite3")))
        self.env = mock.patch.dict(os.environ, {}, clear=False)
        self.env.start()
        for k in ("OLLAMA_BASE_URL", "OPENAI_API_KEY", "AZURE_OPENAI_ENDPOINT", "AZURE_OPENAI_KEY", "COMMUNICATION_PROVIDER_ORDER"):
            os.environ.pop(k, None)

    def tearDown(self):
        self.env.stop()
        self.temp.cleanup()

    def gen(self, purpose, facts, timeframe=CURRENT, name="", fallback="Hello team,\n\nDeterministic prose for this period covering the open batches.\n\n_Thanks_", instruction=""):
        return self.svc.generate(MANAGER, {
            "purpose": purpose, "timeframe": timeframe, "verifiedFacts": facts,
            "recipient": {"name": name, "type": "TEAM" if not name else "REPORTEE"},
            "fallbackText": fallback, "myMessage": instruction,
        })

    def test_no_provider_returns_the_deterministic_prose_with_honest_provenance(self):
        r = self.gen(TEAM_WEEK, TEAM_FACTS)
        self.assertIn("Deterministic prose", r.text)
        self.assertEqual("DETERMINISTIC_GENERATOR", r.generation_mode)
        self.assertTrue(r.provenance["fallback_used"])
        self.assertEqual([], r.provenance["attempted_providers"])
        self.assertIn("low_utilisation_band_count=2", r.facts_used)
        self.assertNotIn("bench=2", r.facts_used)

    def test_valid_model_brief_is_used_with_facts_and_provenance(self):
        with mock.patch.object(providers, "generate", return_value=ok(GOOD_TEAM)) as gen:
            r = self.gen(TEAM_WEEK, TEAM_FACTS)
        gen.assert_called_once()
        self.assertEqual(GOOD_TEAM, r.text)
        self.assertEqual("LLM_OLLAMA", r.generation_mode)
        self.assertFalse(r.provenance["fallback_used"])
        self.assertTrue(r.validation.passed, r.validation.issues)
        prompt = gen.call_args.args[1]
        self.assertIn("WEEKLY_TEAM_BRIEF", prompt)
        self.assertIn("earliest_uncovered_course: DP-700T00", prompt)

    def test_an_availability_claiming_model_reply_is_retried_then_falls_back(self):
        bad = "Hello team,\n\nTwo of us are free this week so please take the open batch when you can.\n\n_Thanks_"
        with mock.patch.object(providers, "generate", side_effect=[ok(bad), ok(GOOD_TEAM)]) as gen:
            r = self.gen(TEAM_WEEK, TEAM_FACTS)
        self.assertEqual(2, gen.call_count)
        self.assertIn("availability", gen.call_args_list[1].args[1])
        self.assertEqual(GOOD_TEAM, r.text)

        with mock.patch.object(providers, "generate", return_value=ok(bad)) as gen:
            r = self.gen(TEAM_WEEK, TEAM_FACTS)
        self.assertEqual(2, gen.call_count)          # exactly one retry
        self.assertIn("Deterministic prose", r.text)
        self.assertTrue(r.provenance["fallback_used"])

    def test_timeout_is_not_retried_and_returns_the_fallback(self):
        timed_out = ProviderOutcome(result=None, elapsed_ms=10_000, timeout_reason="OLLAMA timed out after 10000ms", tried=["ollama"])
        with mock.patch.object(providers, "generate", return_value=timed_out) as gen:
            r = self.gen(TEAM_MONTH, TEAM_FACTS, timeframe=PERIOD_END)
        self.assertEqual(1, gen.call_count)
        self.assertIn("Deterministic prose", r.text)
        self.assertIn("timeout_reason", r.provenance)

    def test_reportee_brief_addresses_the_person(self):
        with mock.patch.object(providers, "generate", return_value=ok(GOOD_REPORTEE)) as gen:
            r = self.gen(REPORTEE_MONTH, REPORTEE_FACTS, name="Priya Sharma", fallback="Hi Priya,\n\nDeterministic monthly prose about your delivery and feedback.\n\n_Thanks_")
        self.assertEqual(GOOD_REPORTEE, r.text)
        self.assertIn("one reportee: Priya Sharma", gen.call_args.args[1])
        self.assertTrue(r.validation.passed, r.validation.issues)

    def test_manager_instruction_is_tone_only_and_cannot_add_facts(self):
        with mock.patch.object(providers, "generate", return_value=ok(GOOD_TEAM)) as gen:
            self.gen(TEAM_WEEK, TEAM_FACTS, instruction="be firmer about the open batch")
        prompt = gen.call_args.args[1]
        self.assertIn("MANAGER INSTRUCTION", prompt)
        self.assertIn("can never override or add facts", prompt)


if __name__ == "__main__":
    unittest.main()
