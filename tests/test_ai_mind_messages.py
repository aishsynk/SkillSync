"""
Comprehensive tests for the AI Mind data-driven message generation system across all 4 waves.
Tests reportee growth briefs, team intelligence broadcasts, copilot upgrades, and allocation/ramp outreach.
"""
import re
import unittest
import backend as b


class TestReporteeAiMind(unittest.TestCase):
    def _assert_house_style(self, msg, max_chars=1000):
        lines = msg.split("\n")
        self.assertGreaterEqual(len(lines), 3)
        self.assertTrue(lines[0].lower().startswith("hello"))
        self.assertEqual(lines[1], "")  # blank line after greeting
        self.assertLessEqual(len(msg), max_chars)
        for banned in ("•", "—", "–"):
            self.assertNotIn(banned, msg)
        for ln in lines:
            self.assertFalse(re.match(r"\s*[-*]\s", ln), f"bullet line: {ln!r}")
        self.assertFalse(re.search(r"[\U0001F000-\U0001FAFF☀-➿]", msg), "no emoji")
        self.assertLessEqual(msg.count("**") // 2, 1, "at most one bold span")
        self.assertLessEqual(msg.count("__") // 2, 1, "at most one underline span")

    def test_cert_gap_with_open_demand_quantifies_opportunity_and_ti_points(self):
        """Cross-referencing: cert gap + matching open demand = quantified participant-days + TI points."""
        f = b._reportee_message_facts(
            {
                "email": "priyanshu@koenig-solutions.com",
                "name": "Priyanshu Sharma",
                "current_utilization": 72,
                "avg_qubits": 78,
                "assignments": [{"course": "AZ-305: Designing Microsoft Azure Infrastructure Solutions"}],
                "total_pax": 18,
                "capacity_bucket": "Delivering",
                "cert_gap_courses": ["AZ-305: Designing Microsoft Azure Infrastructure Solutions"],
                "negative_feedback_count": 0,
                "hr_negative_count": 0,
                "trainer_index_score": 847,
                "trainer_index_tier": "Gold",
                "learner_feedback": {
                    "avg_rating": 4.5,
                    "response_count": 12,
                    "themes": [
                        {"theme": "depth", "sentiment": "positive", "mentions": 5},
                        {"theme": "labs", "sentiment": "positive", "mentions": 4},
                    ],
                },
            },
            "weekly",
            demand_rows=[
                {
                    "course_name": "AZ-305T00: Designing Microsoft Azure Infrastructure Solutions",
                    "participants": 7,
                    "start_date": "2026-10-01",
                    "end_date": "2026-10-02",
                },
                {
                    "course_name": "AZ-305T00: Designing Microsoft Azure Infrastructure Solutions",
                    "participants": 7,
                    "start_date": "2026-10-10",
                    "end_date": "2026-10-11",
                },
            ],
            skills_courses=["AZ-305"],
        )

        msg = b._compose_manager_message("reportee", "weekly", f)
        self._assert_house_style(msg)
        self.assertIn("Priyanshu", msg)
        self.assertIn("AZ-305", msg)
        # Should cite feedback themes
        self.assertIn("depth of knowledge", msg)
        self.assertIn("practical hands on labs", msg)
        # Should cite open demand impact and TI points
        self.assertIn("open batches are waiting", msg.lower())
        self.assertIn("participant days", msg.lower())
        self.assertIn("trainer index", msg.lower())

    def test_feedback_constructive_theme_coaching(self):
        """Constructive feedback theme is surfaced for coaching."""
        f = b._reportee_message_facts(
            {
                "email": "trainer@koenig-solutions.com",
                "name": "Rohan Verma",
                "current_utilization": 65,
                "avg_qubits": 60,
                "assignments": [{"course": "PL-300: Power BI Data Analyst"}],
                "total_pax": 10,
                "capacity_bucket": "Delivering",
                "cert_gap_courses": [],
                "negative_feedback_count": 0,
                "hr_negative_count": 0,
                "learner_feedback": {
                    "avg_rating": 3.6,
                    "response_count": 9,
                    "themes": [
                        {"theme": "pace", "sentiment": "constructive", "mentions": 3},
                    ],
                },
            },
            "weekly",
            demand_rows=[],
            skills_courses=["PL-300"],
        )
        msg = b._compose_manager_message("reportee", "weekly", f)
        self._assert_house_style(msg)
        self.assertIn("Pacing", msg)

    def test_ramp_onboarding_and_stalled_status(self):
        """Ramp status (onboarding/stalled) generates appropriate guidance."""
        # Onboarding new joiner
        f_onboard = b._reportee_message_facts(
            {
                "email": "newbie@koenig-solutions.com",
                "name": "Ananya Roy",
                "current_utilization": 0,
                "avg_qubits": 50,
                "assignments": [],
                "capacity_bucket": "Steady",
                "ramp_stage": "onboarding",
                "stalled": False,
                "cert_gap_courses": [],
                "learner_feedback": {},
            },
            "weekly",
        )
        msg_onboard = b._compose_manager_message("reportee", "weekly", f_onboard)
        self._assert_house_style(msg_onboard)
        self.assertIn("onboarding", msg_onboard.lower())

        # Stalled trainer on bench
        f_stalled = b._reportee_message_facts(
            {
                "email": "stalled@koenig-solutions.com",
                "name": "Vikas Gupta",
                "current_utilization": 15,
                "avg_qubits": 45,
                "assignments": [],
                "capacity_bucket": "On Bench",
                "ramp_stage": "onboarding",
                "stalled": True,
                "cert_gap_courses": [],
                "learner_feedback": {},
            },
            "weekly",
        )
        msg_stalled = b._compose_manager_message("reportee", "weekly", f_stalled)
        self._assert_house_style(msg_stalled)
        self.assertIn("stalled", msg_stalled.lower())

    def test_leave_awareness_in_reportee_message(self):
        """High leaves are acknowledged in delivery messages."""
        f = b._reportee_message_facts(
            {
                "email": "t@koenig-solutions.com",
                "name": "Tanvi Joshi",
                "current_utilization": 60,
                "assignments": [{"course": "SC-900"}],
                "leave_days": 4,
                "cert_gap_courses": [],
                "learner_feedback": {},
            },
            "weekly",
        )
        msg = b._compose_manager_message("reportee", "weekly", f)
        self._assert_house_style(msg)
        self.assertIn("4 days of planned leave", msg)

    def test_manager_evaluation_overhaul(self):
        """_generate_manager_evaluation incorporates feedback themes, TI progression, and demand ROI."""
        eval_result = b._generate_manager_evaluation(
            name="Priyanshu Sharma",
            email="priyanshu@koenig-solutions.com",
            month_label="August 2026",
            avg_qubits=82,
            top_courses=["AZ-305: Azure Solutions Architect"],
            month_util=75,
            util_3m=70,
            batch_count=2,
            month_assignments=[{"course": "AZ-305"}],
            neg_total=0,
            hr_pos=1,
            hr_neg=0,
            cert_intel={
                "held": ["AZ-104"],
                "gap_count": 1,
                "gaps": [{"exam": "AZ-305", "because": "AZ-305"}],
            },
            hr_score=90,
            demand_rows=[
                {
                    "course_name": "AZ-305",
                    "participants": 8,
                    "start_date": "2026-10-01",
                    "end_date": "2026-10-03",
                }
            ],
            skills_courses=["AZ-305"],
        )
        self.assertIn("Strength:", eval_result["formatted_text"])
        self.assertIn("Area of Improvement:", eval_result["formatted_text"])
        self.assertIn("Manager's Verdict:", eval_result["formatted_text"])
        self.assertIn("AZ-305", eval_result["area_of_improvement"])
        self.assertIn("unlocks 1 open batch", eval_result["area_of_improvement"])
        self.assertTrue(len(eval_result["message"]) > 50)
        self.assertTrue(len(eval_result["message"]) <= 1000)


class TestTeamIntelligence(unittest.TestCase):
    def test_team_message_group_safety_and_enrichment(self):
        """Team messages include customer concentration and ramp alerts without negative personal naming."""
        f_weekend = {
            "headcount": 6,
            "delivering": 4,
            "total_pax": 55,
            "total_batches": 5,
            "at_risk": 2,
            "open_demand": 3,
            "coverable_open": 2,
            "bench": 2,
            "total_gaps": 2,
            "top_performers": ["Krishna Dwivedi", "Niharika"],
            "avg_rating": 4.6,
            "top_customer": "Contoso Ltd",
            "top_customer_share_pct": 45,
            "period_key": "wk-2026-35",
        }
        msg = b._compose_manager_message("team", "weekend", f_weekend)
        # Should recognize top performers
        self.assertIn("Krishna", msg)
        self.assertIn("Niharika", msg)
        # Should include customer concentration alert
        self.assertIn("Contoso", msg)
        # Should NEVER name the 2 at_risk or bench trainers negatively
        self.assertNotIn("bad", msg.lower())
        self.assertIn("2 feedback points are being worked through one to one", msg)

    def test_copilot_team_answers(self):
        """Copilot 7 intents return structured, data-driven answers."""
        team = [
            {
                "name": "Niharika",
                "email": "niharika@k.com",
                "util": 70,
                "skills": [{"course_name": "AZ-104"}],
                "neg": 0,
                "hr": 0,
            },
            {
                "name": "Priyanshu",
                "email": "priyanshu@k.com",
                "util": 20,
                "skills": [{"course_name": "AZ-104"}, {"course_name": "AZ-305"}],
                "neg": 0,
                "hr": 0,
            },
        ]
        demand = [
            {"course_name": "AZ-104", "participants": 10},
            {"course_name": "PL-300", "participants": 15},
        ]

        # 1. free_for_course
        ans_free = b._copilot_team_answer("free_for_course", "Who can teach AZ-104?", team, demand)
        self.assertIn("AZ-104", ans_free["answer"])
        self.assertEqual(len(ans_free["data"]), 2)

        # 2. bench
        ans_bench = b._copilot_team_answer("bench", "Who is on the bench?", team, demand)
        self.assertIn("Priyanshu", ans_bench["answer"])

        # 3. top_upskills
        ans_upskills = b._copilot_team_answer("top_upskills", "What should we upskill?", team, demand)
        self.assertIn("PL-300", ans_upskills["answer"])

        # 4. coverage_risk
        ans_risk = b._copilot_team_answer("coverage_risk", "What are our coverage risks?", team, demand)
        self.assertIn("PL-300", ans_risk["answer"])

        # 5. team_summary
        ans_summary = b._copilot_team_answer("team_summary", "Give me a team summary", team, demand)
        self.assertIn("2 reportee(s)", ans_summary["answer"])


if __name__ == "__main__":
    unittest.main()