"""Manager message composer — weekly/monthly, reportee/team.

Turns analysed delivery data into a message a manager would actually send on
Teams/Viber: greeting line, prose body, closing line; no emojis/bullets/hyphens;
one bold action; one underlined time reference; <=1000 chars.
"""
import re
import unittest

import backend as b


def _facts_bench_with_opp():
    return b._reportee_message_facts(
        {
            "email": "abhinav@koenig-solutions.com", "name": "Abhinav Sharma",
            "current_utilization": 34, "avg_qubits": 72, "assignments": [],
            "capacity_bucket": "On Bench", "cert_gap_courses": ["AZ-104 Azure Administrator"],
            "negative_feedback_count": 0, "hr_negative_count": 0,
            "learner_feedback": {"avg_rating": None, "response_count": 0,
                                 "positive_quotes": [], "constructive_quotes": []},
        },
        "weekly",
        demand_rows=[{"course_name": "AZ-104T00: Microsoft Azure Administrator"}],
        skills_courses=["AZ-104: Azure Administrator"],
    )


class ComposerShape(unittest.TestCase):
    def _assert_house_style(self, msg):
        lines = msg.split("\n")
        self.assertGreaterEqual(len(lines), 3)
        self.assertTrue(lines[0].lower().startswith("hello"))
        self.assertEqual(lines[1], "")                       # blank after greeting
        self.assertLessEqual(len(msg), 1000)
        for banned in ("•", "—", "–"):
            self.assertNotIn(banned, msg)
        for ln in lines:
            self.assertFalse(re.match(r"\s*[-*]\s", ln), f"bullet line: {ln!r}")
        self.assertFalse(re.search(r"[\U0001F000-\U0001FAFF☀-➿]", msg), "no emoji")
        self.assertLessEqual(msg.count("**") // 2, 1, "at most one bold span")
        self.assertLessEqual(msg.count("__") // 2, 1, "at most one underline span")

    def test_reportee_weekly_bench_opportunity(self):
        msg = b._compose_manager_message("reportee", "weekly", _facts_bench_with_opp())
        self._assert_house_style(msg)
        self.assertIn("Abhinav", msg.split("\n")[0])
        self.assertIn("open", msg.lower())                    # opportunity surfaced
        self.assertIn("certification", msg.lower())           # cert gap surfaced
        self.assertIn("**", msg)                              # an action is bolded

    def test_reportee_weekly_strong_delivery(self):
        f = b._reportee_message_facts(
            {"email": "k@koenig-solutions.com", "name": "Krishna Dwivedi",
             "current_utilization": 78, "avg_qubits": 85,
             "assignments": [{"course": "DP-700: Fabric Data Engineer"}], "total_pax": 14,
             "capacity_bucket": "Delivering", "cert_gap_courses": [],
             "negative_feedback_count": 0, "hr_negative_count": 0,
             "learner_feedback": {"avg_rating": 4.7, "response_count": 31,
                                  "positive_quotes": [{"text": "Explained Fabric pipelines with clear real world examples"}],
                                  "constructive_quotes": []}},
            "weekly", demand_rows=[], skills_courses=["DP-700"])
        msg = b._compose_manager_message("reportee", "weekly", f)
        self._assert_house_style(msg)
        self.assertIn("4.7", msg)
        self.assertRegex(msg.split("\n")[-1].lower(), r"thank you|posted|consistency")

    def test_my_message_leads(self):
        msg = b._compose_manager_message(
            "reportee", "weekly", _facts_bench_with_opp(),
            my_message="pls upload recordings same day, client asked",
        )
        body = msg.split("\n\n")[1]
        self.assertTrue(body.lower().startswith("**") or "recordings" in body.lower().split(".")[0])
        self.assertIn("recordings", msg.lower())

    def test_team_weekly(self):
        f = {"manager_first": "Aishwar", "headcount": 8, "delivering": 5,
             "total_pax": 92, "total_batches": 6, "at_risk": 1, "risk_names": "Neha",
             "open_demand": 4, "coverable_open": 3, "bench": 2, "total_gaps": 2,
             "period_key": "2026-08-25"}
        msg = b._compose_manager_message("team", "weekly", f)
        self._assert_house_style(msg)
        self.assertTrue(msg.split("\n")[0].startswith("Hello team"))
        self.assertIn("open", msg.lower())

    def test_no_flags_is_short_and_appreciative(self):
        f = {"manager_first": "A", "headcount": 6, "delivering": 6, "total_pax": 70,
             "total_batches": 6, "at_risk": 0, "open_demand": 0, "coverable_open": 0,
             "bench": 0, "total_gaps": 0, "period_key": "x"}
        msg = b._compose_manager_message("team", "weekly", f)
        self._assert_house_style(msg)
        self.assertIn("thank you", msg.lower())
        self.assertNotIn("**", msg)   # nothing to action, so nothing bolded


class ComposeRoute(unittest.TestCase):
    def setUp(self):
        backend = b
        backend._sessions.clear()
        backend._sessions["mgr"] = {"email": "manager@koenig-solutions.com", "role": "manager"}
        backend._warm_payload_cache.clear()
        self.client = backend.app.test_client()
        self.h = {"Authorization": "Bearer mgr"}

    def tearDown(self):
        b._sessions.clear()
        b._warm_payload_cache.clear()

    def test_team_compose_uses_cached_digest(self):
        b._warm_payload_cache["weekly::manager@koenig-solutions.com::" +
            b._iso(__import__("datetime").datetime.utcnow().date() -
                   __import__("datetime").timedelta(days=__import__("datetime").datetime.utcnow().date().weekday()))] = (
            0, {"team_digest": "Hello team,\n\nDelivery is steady.\n\nThank you all.", "reportees": []})
        r = self.client.post("/api/v2/message/compose",
                             json={"manager": "manager@koenig-solutions.com", "cadence": "weekly"},
                             headers=self.h)
        self.assertEqual(200, r.status_code)
        self.assertIn("team", r.get_json()["scope"])


if __name__ == "__main__":
    unittest.main()
