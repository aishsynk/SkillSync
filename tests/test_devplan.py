import os
import tempfile
import unittest
from unittest.mock import patch

import backend
from dev_plan_store import DevPlanStore

MANAGER = "manager@koenig-solutions.com"
T1 = "t1@koenig-solutions.com"
T2 = "t2@koenig-solutions.com"
OUTSIDER = "nobody@koenig-solutions.com"


class DevPlanTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.previous = backend._devplan_repository
        backend._devplan_repository = DevPlanStore(
            os.path.join(self.temp.name, "devplans.sqlite3"))
        backend._sessions.clear()
        backend._sessions["mgr-session"] = {"email": MANAGER, "role": "manager"}
        self.client = backend.app.test_client()
        self.headers = {"Authorization": "Bearer mgr-session"}

    def tearDown(self):
        backend._devplan_repository = self.previous
        backend._sessions.clear()
        self.temp.cleanup()

    # One _rms side-effect serves the reportee lookup for every test.
    def _rms(self, api, body, *a, **k):
        if api == "reportees":
            return [
                {"OffEmail": T1, "TrainerName": "Trainer One"},
                {"OffEmail": T2, "TrainerName": "Trainer Two"},
            ]
        return []

    def _skills(self, email):
        if email == T1:
            return [
                {"course": "AZ-104: Microsoft Azure Administrator",
                 "course_name": "AZ-104: Microsoft Azure Administrator", "approved": False},
            ]
        return [
            {"course": "AZ-900", "course_name": "AZ-900", "approved": True},
            {"course": "SC-900", "course_name": "SC-900", "approved": True},
            {"course": "MS-900", "course_name": "MS-900", "approved": True},
        ]

    def _demand_rows(self):
        return [{"course_name": "AZ-104: Microsoft Azure Administrator"},
                {"course_name": "AZ-104: Microsoft Azure Administrator"}]

    def _feedback(self, email, *a, **k):
        return {"avg_rating": 3.2 if email == T1 else 4.6}

    def _get(self, trainer):
        with patch.object(backend, "_exam_policy", return_value={}), \
             patch.object(backend, "_rms", side_effect=self._rms), \
             patch.object(backend, "_skills", side_effect=self._skills), \
             patch.object(backend, "_demand_rows", side_effect=self._demand_rows), \
             patch.object(backend, "_trainer_feedback_detail", side_effect=self._feedback):
            return self.client.get(
                f"/api/v2/devplan?manager={MANAGER}&trainer={trainer}", headers=self.headers)

    def _post(self, payload):
        with patch.object(backend, "_rms", side_effect=self._rms):
            return self.client.post("/api/v2/devplan/item", json=payload, headers=self.headers)

    def _patch(self, payload):
        with patch.object(backend, "_rms", side_effect=self._rms):
            return self.client.patch("/api/v2/devplan/item", json=payload, headers=self.headers)

    def test_auth_gate(self):
        self.assertEqual(401, self.client.get(
            f"/api/v2/devplan?manager={MANAGER}&trainer={T1}").status_code)

    def test_scope_check_rejects_non_reportee(self):
        self.assertEqual(403, self._get(OUTSIDER).status_code)

    def test_create_round_trips_via_get(self):
        r = self._post({"manager": MANAGER, "trainer": T1,
                        "title": "Shadow an AZ-104 batch", "kind": "coaching"})
        self.assertEqual(201, r.status_code)
        item_id = r.get_json()["id"]

        body = self._get(T1).get_json()
        self.assertEqual([item_id], [i["id"] for i in body["items"]])
        self.assertEqual("open", body["items"][0]["status"])

    def test_bad_kind_is_invalid_input(self):
        r = self._post({"manager": MANAGER, "trainer": T1, "title": "x", "kind": "nonsense"})
        self.assertEqual(400, r.status_code)
        self.assertEqual("INVALID_INPUT", r.get_json()["code"])

    def test_patch_status_transition(self):
        item_id = self._post({"manager": MANAGER, "trainer": T1,
                              "title": "Book PL-300", "kind": "certification"}).get_json()["id"]
        r = self._patch({"manager": MANAGER, "id": item_id, "status": "in_progress"})
        self.assertEqual(200, r.status_code)
        self.assertEqual("in_progress", r.get_json()["status"])

    def test_patch_other_managers_id_is_not_found(self):
        item_id = self._post({"manager": MANAGER, "trainer": T1,
                              "title": "Book PL-300", "kind": "certification"}).get_json()["id"]
        backend._sessions["other-session"] = {
            "email": "other@koenig-solutions.com", "role": "manager"}
        with patch.object(backend, "_rms", side_effect=self._rms):
            r = self.client.patch(
                "/api/v2/devplan/item",
                json={"manager": "other@koenig-solutions.com", "id": item_id, "status": "done"},
                headers={"Authorization": "Bearer other-session"})
        self.assertEqual(404, r.status_code)
        self.assertEqual("NOT_FOUND", r.get_json()["code"])

    def test_suggested_items_for_low_rated_few_skills_trainer(self):
        body = self._get(T1).get_json()
        kinds = {s["kind"] for s in body["suggested"]}
        self.assertIn("certification", kinds)   # AZ-104 taught, unapproved, open demand
        self.assertIn("coaching", kinds)        # avg 3.2 < 4.0
        self.assertIn("portfolio", kinds)       # only 1 course

    def test_no_suggestions_for_healthy_trainer(self):
        body = self._get(T2).get_json()
        self.assertEqual([], body["suggested"])


if __name__ == "__main__":
    unittest.main()
