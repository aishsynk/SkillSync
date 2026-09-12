"""Comprehensive test suite for SkillSync Communication Intelligence Service.

Tests:
1. User Message Precedence (Tests 1, 2, 3, 4)
2. Hinglish Understanding
3. 6 Weekly Report Scenarios (holistic evaluation, single priority, rejection of KPI dump)
4. Monthly Report Scenarios (appreciation, capability, delivery support, steady)
5. Sensitive Information Filtering (privacy screen)
6. Generator Reality (DETERMINISTIC_GENERATOR when no LLM keys)
"""

import os
import tempfile
import unittest

import backend
from repositories.communication_store import CommunicationStore
from repositories.opportunity_store import OpportunityStore
from services.communication.service import CommunicationService
from services.communication.context_selector import NO_MEANINGFUL_MESSAGE

MANAGER = "manager@koenig-solutions.com"


class CommunicationEngineComprehensiveTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.prev_comm = backend._communication_service
        self.prev_opp = backend._opportunity_repository
        backend._communication_service = CommunicationService(
            CommunicationStore(os.path.join(self.temp.name, "comm.sqlite3"))
        )
        backend._opportunity_repository = OpportunityStore(
            os.path.join(self.temp.name, "opp.sqlite3")
        )
        backend._sessions.clear()
        backend._sessions["test-session"] = {"email": MANAGER, "role": "manager"}
        self.client = backend.app.test_client()
        self.headers = {"Authorization": "Bearer test-session"}

    def tearDown(self):
        backend._communication_service = self.prev_comm
        backend._opportunity_repository = self.prev_opp
        backend._sessions.clear()
        self.temp.cleanup()

    def generate(self, body, verified=None):
        return backend._communication_service.generate(MANAGER, body, verified_context=verified or {})

    # ── 1. USER MESSAGE PRECEDENCE TESTS (SECTION 24) ─────────────────────────

    def test_user_msg_priority_test_1_delivery_inquiry_with_preparation(self):
        """User Message: 'Can you take DP-700 next week?'
        My Message: 'yes, but I need preparation'
        Primary intent: Respond to request to take DP-700 next week.
        My position: Willing to take it, but preparation is required.
        Must NOT be reinterpreted as a generic COURSE_PREPARATION_REQUEST!
        """
        out = self.generate({
            "recipient": {"name": "Gaurav", "type": "MANAGER"},
            "userMessage": "Can you take DP-700 next week?",
            "myMessage": "yes, but I need preparation",
        })
        text = out.text
        self.assertTrue(out.validation.passed)
        self.assertTrue(out.requires_communication)
        self.assertEqual("OPPORTUNITY_RESPONSE", out.purpose)
        self.assertIn("*Gaurav*", text)
        self.assertIn("DP-700", text)
        self.assertTrue("__**next week**__" in text or "__next week__" in text)
        self.assertIn("preparation", text.lower())
        self.assertIn("schedule", text.lower())

    def test_user_msg_priority_test_2_task_inquiry_completed_response(self):
        """User Message: 'Please send the report by Friday.'
        My Message: 'tell him I have completed it and shared it'
        Primary intent: Respond to task/report request.
        Must not classify as TASK_FOLLOWUP based on arbitrary words in My Message!
        """
        out = self.generate({
            "recipient": {"name": "Gaurav", "type": "MANAGER"},
            "userMessage": "Please send the report by Friday.",
            "myMessage": "tell him I have completed it and shared it",
        })
        text = out.text
        self.assertTrue(out.validation.passed)
        self.assertTrue(out.requires_communication)
        self.assertEqual("STATUS_UPDATE", out.purpose)
        self.assertIn("*Gaurav*", text)
        self.assertIn("completed", text.lower())
        self.assertIn("shared", text.lower())

    def test_user_msg_priority_test_3_availability_inquiry_decline_and_propose(self):
        """User Message: 'Are you free tomorrow?'
        My Message: 'No, I am in delivery. I can connect Friday.'
        Must NOT turn this into an availability solicitation!
        """
        out = self.generate({
            "recipient": {"name": "Gaurav", "type": "COLLEAGUE"},
            "userMessage": "Are you free tomorrow?",
            "myMessage": "No, I am in delivery. I can connect Friday.",
        })
        text = out.text
        self.assertTrue(out.validation.passed)
        self.assertTrue(out.requires_communication)
        self.assertEqual("AVAILABILITY_RESPONSE", out.purpose)
        self.assertIn("*Gaurav*", text)
        self.assertIn("delivery", text.lower())
        self.assertTrue("Friday" in text)
        self.assertNotIn("open batch", text.lower())

    def test_user_msg_priority_test_4_my_message_only_team_availability(self):
        """User Message: EMPTY
        My Message: 'ask the team who can take the DP-700 batch next week'
        My Message becomes primary because User Message is empty.
        """
        out = self.generate({
            "recipient": {"type": "TEAM"},
            "userMessage": "",
            "myMessage": "ask the team who can take the DP-700 batch next week",
        })
        text = out.text
        self.assertTrue(out.validation.passed)
        self.assertTrue(out.requires_communication)
        self.assertEqual("AVAILABILITY_REQUEST", out.purpose)
        self.assertTrue(text.startswith("Hello team,"))
        self.assertIn("DP-700", text)
        self.assertTrue("__**next week**__" in text or "__next week__" in text)

    # ── 2. HINGLISH TEST (SECTION 25) ─────────────────────────────────────────

    def test_hinglish_delivery_request_and_response(self):
        """User Message: 'Sir ye batch next week possible hai?'
        My Message: 'haan bol do le lunga but prep ke liye toc jaldi chahiye'
        Result must be professional English while preserving meaning.
        """
        out = self.generate({
            "recipient": {"name": "Gaurav", "type": "MANAGER"},
            "userMessage": "Sir ye batch next week possible hai?",
            "myMessage": "haan bol do le lunga but prep ke liye toc jaldi chahiye",
        })
        text = out.text
        self.assertTrue(out.validation.passed)
        self.assertTrue(out.requires_communication)
        self.assertEqual("OPPORTUNITY_RESPONSE", out.purpose)
        self.assertIn("*Gaurav*", text)
        self.assertTrue("__**next week**__" in text or "__next week__" in text)
        self.assertIn("table of contents", text.lower())
        self.assertIn("preparation", text.lower())
        # Confirm no raw Hinglish leaked
        for h in ["haan", "bol do", "le lunga", "jaldi", "chahiye", "ke liye", "possible hai"]:
            self.assertNotIn(h, text.lower())

    # ── 3. 6 WEEKLY REPORT SCENARIOS (SECTION 22) ─────────────────────────────

    def test_weekly_scenario_1_open_demand_matching_availability(self):
        """Scenario 1: Open demand + matching availability.
        Selects 1-2 critical facts (demand + bench); rejects irrelevant metrics (pax, gaps, CSAT).
        No artificial 'today', no 'so we do not lose the slot'.
        """
        context = {
            "headcount": 8,
            "delivering": 5,
            "total_pax": 120,
            "total_batches": 6,
            "open_demand": 1,
            "bench": 1,
            "coverable_open": 1,
            "total_gaps": 13,
            "avg_rating": 4.8,
        }
        out = self.generate({"recipient": {"type": "TEAM"}}, verified=context)
        text = out.text
        self.assertTrue(out.validation.passed)
        self.assertTrue(out.requires_communication)
        self.assertEqual("AVAILABILITY_REQUEST", out.purpose)
        self.assertIn("open delivery requirement", text.lower())
        self.assertIn("available capacity", text.lower())
        self.assertNotIn("120", text)
        self.assertNotIn("13", text)
        self.assertNotIn("4.8", text)
        self.assertNotIn("so we do not lose the slot", text.lower())

    def test_weekly_scenario_2_open_demand_no_team_capability(self):
        """Scenario 2: Open demand but no relevant capability available in team.
        Triggers external/network escalation rather than asking unavailable team.
        """
        context = {
            "open_demand": 1,
            "bench": 0,
            "coverable_open": 0,
            "total_pax": 20,
        }
        out = self.generate({"recipient": {"type": "TEAM"}}, verified=context)
        self.assertTrue(out.requires_communication)
        self.assertEqual("CAPABILITY_ESCALATION", out.purpose)
        self.assertIn("external", out.text.lower())

    def test_weekly_scenario_3_capability_gap_threatening_upcoming_demand(self):
        """Scenario 3: No staffing issue, but capability gap threatens verified upcoming demand.
        Focuses on closing the gap linked to demand; does not invent artificial urgency.
        """
        context = {
            "open_demand": 0,
            "gap_demand_count": 2,
            "cert_gap_courses": ["AZ-104 Azure Administrator"],
            "total_pax": 30,
        }
        out = self.generate({"recipient": {"type": "TEAM"}}, verified=context)
        self.assertTrue(out.requires_communication)
        self.assertEqual("CAPABILITY_DEVELOPMENT", out.purpose)
        self.assertIn("certification", out.text.lower())

    def test_weekly_scenario_4_everything_healthy(self):
        """Scenario 4: Everything healthy.
        Emits NO_MEANINGFUL_MESSAGE with clear rationale; suppresses noise.
        """
        context = {
            "open_demand": 0,
            "bench": 0,
            "at_risk": 0,
            "total_gaps": 0,
            "total_pax": 50,
        }
        out = self.generate({"recipient": {"type": "TEAM"}}, verified=context)
        self.assertFalse(out.requires_communication)
        self.assertEqual(NO_MEANINGFUL_MESSAGE, out.text)
        self.assertIsNotNone(out.no_message_reason)

    def test_weekly_scenario_5_high_delivery_load_no_action_needed(self):
        """Scenario 5: High delivery load but no action required.
        Does NOT produce a useless statistics summary; suppresses noise.
        """
        context = {
            "open_demand": 0,
            "bench": 0,
            "at_risk": 0,
            "delivering": 8,
            "total_pax": 200,
            "total_batches": 10,
        }
        out = self.generate({"recipient": {"type": "TEAM"}}, verified=context)
        self.assertFalse(out.requires_communication)
        self.assertEqual(NO_MEANINGFUL_MESSAGE, out.text)
        self.assertIn("steady", out.no_message_reason.lower())

    def test_weekly_scenario_6_delivery_quality_risk(self):
        """Scenario 6: Delivery quality risk exists.
        Generates only the relevant quality communication; rejects unrelated metrics.
        """
        context = {
            "at_risk": 2,
            "open_demand": 0,
            "total_pax": 80,
            "total_batches": 4,
        }
        out = self.generate({"recipient": {"type": "TEAM"}}, verified=context)
        self.assertTrue(out.requires_communication)
        self.assertEqual("DELIVERY_SUPPORT", out.purpose)
        self.assertIn("feedback", out.text.lower())
        self.assertNotIn("80", out.text)

    # ── 4. MONTHLY REPORT SCENARIOS (SECTION 23) ──────────────────────────────

    def test_monthly_scenario_appreciation(self):
        """Monthly appreciation message recognizing team performance."""
        out = self.generate({
            "recipient": {"type": "TEAM"},
            "myMessage": "kudos to the team for strong delivery performance this month",
        }, verified={"total_pax": 150, "total_batches": 8})
        self.assertTrue(out.requires_communication)
        self.assertEqual("APPRECIATION", out.purpose)
        self.assertIn("thank you", out.text.lower())
        self.assertNotIn("150", out.text)

    # ── 5. PRIVACY FILTERING (SECTION 15) ─────────────────────────────────────

    def test_sensitive_information_filtering(self):
        """Screens salary, margin, billing_rate, client_billing_key into sensitive_facts_removed."""
        dirty_context = {
            "salary": 120000,
            "margin": 0.35,
            "billing_rate": 850,
            "client_billing_key": "SEC-9901-BILLING",
            "retention_flag": "HIGH_RISK",
            "open_demand": 1,
            "bench": 1,
        }
        plan = backend._communication_service.generate(
            MANAGER, {"recipient": {"type": "TEAM"}}, verified_context=dirty_context
        )
        self.assertNotIn("120000", plan.text)
        self.assertNotIn("0.35", plan.text)
        self.assertNotIn("SEC-9901", plan.text)
        self.assertNotIn("HIGH_RISK", plan.text)

    # ── 6. GENERATOR HONESTY (SECTION 13) ─────────────────────────────────────

    def test_generator_honesty_mode(self):
        """Gracefully operates with DETERMINISTIC_GENERATOR without LLM API keys."""
        old_key = os.environ.get("OPENAI_API_KEY")
        if "OPENAI_API_KEY" in os.environ:
            del os.environ["OPENAI_API_KEY"]
        try:
            out = self.generate({
                "recipient": {"name": "Deepak", "type": "REPORTEE"},
                "myMessage": "Please complete your preparation for MS-900 before next week.",
            })
            self.assertTrue(out.validation.passed)
            self.assertEqual("DETERMINISTIC_GENERATOR", out.generation_mode)
            self.assertIn("*Deepak*", out.text)
            self.assertIn("MS-900", out.text)
            self.assertTrue("__**next week**__" in out.text or "__next week__" in out.text)
        finally:
            if old_key:
                os.environ["OPENAI_API_KEY"] = old_key

    # ── 7. FACTUAL INTEGRITY VALIDATION & CORRUPTION REGRESSION ──────────────

    def test_open_demand_two_not_corrupted_to_one(self):
        """Regression test for Scenario 7 bug: open_demand = 2 must NEVER become 1."""
        context = {
            "open_demand": 2,
            "coverable_open": 0,
            "bench": 0,
        }
        out = self.generate({"recipient": {"type": "TEAM"}}, verified=context)
        self.assertTrue(out.requires_communication)
        self.assertEqual("CAPABILITY_ESCALATION", out.purpose)
        self.assertIn("2 open delivery requirements", out.text)
        self.assertNotIn("1 open delivery requirement", out.text)
        self.assertTrue(out.validation.passed)

    def test_factual_integrity_validator_detects_corrupted_demand_count(self):
        """Direct validator check: corrupted count must fail validation."""
        from services.communication.validator import validate
        from domain.communication.models import CommunicationPlan, FactItem

        plan = CommunicationPlan(
            purpose="CAPABILITY_ESCALATION",
            selected_facts=[FactItem("open_demand", 2, "VERIFIED_SKILLSYNC_CONTEXT")],
            time_references=[],
            requires_communication=True,
        )
        corrupted_text = "Hello team,\n\nWe have 1 open delivery requirement that requires skills outside our currently available team capacity.\n\nBest regards,"
        val = validate(corrupted_text, plan=plan)
        self.assertFalse(val.passed)
        self.assertTrue(any("open demand count '1' does not match verified facts" in issue for issue in val.issues))

    def test_factual_integrity_validator_detects_fabricated_course_code(self):
        """Direct validator check: fabricated course code must fail validation."""
        from services.communication.validator import validate
        from domain.communication.models import CommunicationPlan, FactItem

        plan = CommunicationPlan(
            purpose="CAPABILITY_DEVELOPMENT",
            selected_facts=[FactItem("cert_gap_courses", ["AZ-104"], "VERIFIED_SKILLSYNC_CONTEXT")],
            requires_communication=True,
        )
        fabricated_text = "Hello team,\n\nWe have certification gaps in MS-500.\n\nBest regards,"
        val = validate(fabricated_text, plan=plan)
        self.assertFalse(val.passed)
        self.assertTrue(any("course code 'MS-500' not found" in issue for issue in val.issues))

    def test_factual_integrity_validator_detects_fabricated_day(self):
        """Direct validator check: fabricated day name must fail validation."""
        from services.communication.validator import validate
        from domain.communication.models import CommunicationPlan

        plan = CommunicationPlan(
            purpose="AVAILABILITY_RESPONSE",
            user_message="Are you free tomorrow?",
            my_message="I can connect on Thursday.",
            time_references=["Thursday"],
            requires_communication=True,
        )
        fabricated_text = "Hello,\n\nI can connect with you on __**Monday**__.\n\nBest regards,"
        val = validate(fabricated_text, plan=plan)
        self.assertFalse(val.passed)
        self.assertTrue(any("day 'Monday' not found" in issue for issue in val.issues))

    def test_opportunity_response_preserves_material_time_context(self):
        """Scenario 5: User asked 'bhai AZ-104 possible hai kya next week?'
        My Message: 'haan le lunga, schedule bhej do'
        Must preserve 'next week' and request confirmed schedule.
        """
        out = self.generate({
            "recipient": {"name": "Gaurav", "type": "COLLEAGUE"},
            "userMessage": "bhai AZ-104 possible hai kya next week?",
            "myMessage": "haan le lunga, schedule bhej do",
        })
        self.assertTrue(out.requires_communication)
        self.assertTrue(out.validation.passed)
        self.assertEqual("OPPORTUNITY_RESPONSE", out.purpose)
        self.assertIn("*Gaurav*", out.text)
        self.assertIn("AZ-104", out.text)
        self.assertIn("__**next week**__", out.text)
        self.assertIn("schedule", out.text.lower())

    def test_capability_development_names_specific_verified_courses(self):
        """Scenario 8: When cert_gap_courses are verified, names them specifically."""
        context = {
            "open_demand": 0,
            "gap_demand_count": 2,
            "cert_gap_courses": ["AZ-104", "SC-300"],
        }
        out = self.generate({"recipient": {"type": "TEAM"}}, verified=context)
        self.assertTrue(out.requires_communication)
        self.assertTrue(out.validation.passed)
        self.assertEqual("CAPABILITY_DEVELOPMENT", out.purpose)
        self.assertIn("specifically in AZ-104 and SC-300", out.text)

    def test_monthly_appreciation_includes_qualitative_avg_rating(self):
        """Appreciation message includes qualitative average rating when >= 4.0."""
        context = {
            "avg_rating": 4.85,
            "total_pax": 120,
            "total_batches": 6,
        }
        out = self.generate({
            "recipient": {"type": "TEAM"},
            "myMessage": "appreciate the team's strong delivery this month",
        }, verified=context)
        self.assertTrue(out.requires_communication)
        self.assertTrue(out.validation.passed)
        self.assertEqual("APPRECIATION", out.purpose)
        self.assertIn("averaging 4.85 out of 5 in participant feedback", out.text)
        self.assertNotIn("120", out.text)
        self.assertNotIn("6", out.text)

    def test_availability_request_surfaces_entity_context(self):
        """Weekly report availability request surfaces course, location, and candidate trainer."""
        context = {
            "open_demand": 1,
            "bench": 1,
            "course": "AZ-104",
            "candidate_trainer": "Niharika",
            "location": "Mumbai",
        }
        out = self.generate({"recipient": {"type": "TEAM"}}, verified=context)
        self.assertTrue(out.requires_communication)
        self.assertTrue(out.validation.passed)
        self.assertEqual("AVAILABILITY_REQUEST", out.purpose)
        self.assertIn("AZ-104", out.text)
        self.assertIn("*Niharika*", out.text)
        self.assertIn("Mumbai", out.text)


if __name__ == "__main__":
    unittest.main()
