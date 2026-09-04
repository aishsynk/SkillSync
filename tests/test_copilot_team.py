import unittest
from unittest.mock import patch

import backend

REPORTEES = [
    {"TrainerName": "Ravi Kumar", "OffEmail": "ravi@koenig-solutions.com", "EmpId": "E1"},
    {"TrainerName": "Sara Lee", "OffEmail": "sara@koenig-solutions.com", "EmpId": "E2"},
    {"TrainerName": "Tom Ray", "OffEmail": "tom@koenig-solutions.com", "EmpId": "E3"},
]

DEMAND = [
    {"Coursename": "AZ-104: Microsoft Azure Administrator", "AssignmentID": "1",
     "CourseSDate": "2026-09-07", "CourseEDate": "2026-09-11", "NoOfParticipants": 8},
    {"Coursename": "AZ-104: Microsoft Azure Administrator", "AssignmentID": "2",
     "CourseSDate": "2026-09-14", "CourseEDate": "2026-09-18", "NoOfParticipants": 6},
    {"Coursename": "CKA: Certified Kubernetes Administrator", "AssignmentID": "3",
     "CourseSDate": "2026-09-09", "CourseEDate": "2026-09-11", "NoOfParticipants": 5},
    {"Coursename": "AI-102: Azure AI Engineer", "AssignmentID": "4",
     "CourseSDate": "2026-09-21", "CourseEDate": "2026-09-24", "NoOfParticipants": 7},
    {"Coursename": "AI-102: Azure AI Engineer", "AssignmentID": "5",
     "CourseSDate": "2026-09-28", "CourseEDate": "2026-10-01", "NoOfParticipants": 4},
    {"Coursename": "AI-102: Azure AI Engineer", "AssignmentID": "6",
     "CourseSDate": "2026-10-05", "CourseEDate": "2026-10-08", "NoOfParticipants": 9},
]

UTIL = {
    "ravi@koenig-solutions.com": {"TrainerName": "Ravi Kumar", "Aug 2026": "10/30"},
    "sara@koenig-solutions.com": {"TrainerName": "Sara Lee", "Aug 2026": "40/92"},
    "tom@koenig-solutions.com": {"TrainerName": "Tom Ray", "Aug 2026": "25/65"},
}

SKILLS = {
    "ravi@koenig-solutions.com": [{"CourseName": "AZ-104: Microsoft Azure Administrator"}],
    "sara@koenig-solutions.com": [{"CourseName": "AZ-104: Microsoft Azure Administrator"},
                                  {"CourseName": "CKA: Certified Kubernetes Administrator"}],
    "tom@koenig-solutions.com": [{"CourseName": "SC-300: Identity and Access Administrator"}],
}


def _fake_rms(api, body=None, *a, **k):
    body = body or {}
    if api == "reportees":
        return REPORTEES
    if api == "unallocated":
        return DEMAND
    if api == "courseWithoutExam":
        return []
    if api == "utilization":
        row = UTIL.get(str(body.get("email", "")).strip().lower())
        return [row] if row else []
    if api == "trainerDetails":
        return SKILLS.get(str(body.get("email", "")).strip().lower(), [])
    if api == "trainerNegFeedback":
        return [{"Question": "Pace", "Score": "2"}] if body.get("employee_id") == "E3" else []
    if api == "hrIncident":
        return []
    return []


class CopilotTeamTests(unittest.TestCase):
    def setUp(self):
        backend._sessions.clear()
        backend._manager_email_cache.clear()
        backend._cache.clear()
        backend._sessions["session"] = {"email": "manager@koenig-solutions.com", "role": "manager"}
        self.client = backend.app.test_client()
        self.headers = {"Authorization": "Bearer session"}

    def _ask(self, **body):
        body.setdefault("manager", "manager@koenig-solutions.com")
        with patch.object(backend, "_rms", side_effect=_fake_rms):
            return self.client.post("/api/v2/copilot/team", json=body, headers=self.headers)

    def test_requires_auth(self):
        r = self.client.post("/api/v2/copilot/team", json={"manager": "manager@koenig-solutions.com",
                                                           "question": "who is on the bench"})
        self.assertEqual(401, r.status_code)

    def test_free_for_course_routes_and_lists_ready_names(self):
        r = self._ask(question="who is free next week for AZ-104")
        self.assertEqual(200, r.status_code)
        j = r.get_json()
        self.assertEqual("free_for_course", j["question_key"])
        self.assertEqual("team-v1", j["decisionVersion"])
        names = [d["name"] for d in j["data"]]
        self.assertIn("Ravi Kumar", names)        # holds AZ-104, 30% utilised
        self.assertNotIn("Sara Lee", names)       # holds AZ-104 but 92% utilised
        self.assertTrue(all({"name", "email", "note"} <= set(d) for d in j["data"]))

    def test_free_for_course_does_not_call_unknown_capacity_not_overloaded(self):
        team = [{"name": "Ravi", "email": "r@k.com", "skills": [
            {"course_name": "AZ-104: Microsoft Azure Administrator"}
        ], "util": None}]
        result = backend._copilot_team_answer(
            "free_for_course", "who is free for AZ-104", team, []
        )
        self.assertIn("capacity is unknown", result["answer"])
        self.assertNotIn("not overloaded", result["answer"])
        self.assertEqual("utilisation unknown", result["data"][0]["note"])
        self.assertEqual("medium", result["confidence"])

    def test_free_for_course_does_not_recommend_upskilling_when_skill_exists(self):
        team = [{"name": "Sara", "email": "s@k.com", "skills": [
            {"course_name": "AZ-104: Microsoft Azure Administrator"}
        ], "util": 92}]
        result = backend._copilot_team_answer(
            "free_for_course", "who is free for AZ-104", team, []
        )
        self.assertIn("all have utilisation at or above 85%", result["answer"])
        self.assertIn("upskilling is not the issue", result["answer"])
        self.assertEqual("high", result["confidence"])

    def test_coverage_risk_routes_and_shapes_data(self):
        r = self._ask(question="what is our biggest coverage risk this month")
        j = r.get_json()
        self.assertEqual("coverage_risk", j["question_key"])
        self.assertTrue(all({"course", "count"} <= set(d) for d in j["data"]))
        # AI-102 has 3 open batches and nobody covers it -> named as the unlock
        self.assertIn("AI-102", j["answer"])

    def test_top_upskills_ranks_uncoverable_demand(self):
        r = self._ask(question="which upskills unlock the most demand")
        j = r.get_json()
        self.assertEqual("top_upskills", j["question_key"])
        self.assertEqual("AI-102: Azure AI Engineer", j["data"][0]["course"])
        self.assertEqual(3, j["data"][0]["count"])

    def test_bench_and_overloaded_split_by_utilisation(self):
        bench = self._ask(question="who is on the bench").get_json()
        self.assertEqual("bench", bench["question_key"])
        self.assertEqual(["Ravi Kumar"], [d["name"] for d in bench["data"]])

        over = self._ask(question="who is stretched right now").get_json()
        self.assertEqual("overloaded", over["question_key"])
        self.assertEqual(["Sara Lee"], [d["name"] for d in over["data"]])

    def test_feedback_watch_and_question_key_passthrough(self):
        fb = self._ask(question="who needs a 1:1").get_json()
        self.assertEqual("feedback_watch", fb["question_key"])
        self.assertEqual(["Tom Ray"], [d["name"] for d in fb["data"]])

        summary = self._ask(question_key="team_summary").get_json()
        self.assertEqual("team_summary", summary["question_key"])
        self.assertIn("3 reportee", summary["answer"])
        self.assertEqual(3, len(summary["data"]))


if __name__ == "__main__":
    unittest.main()
