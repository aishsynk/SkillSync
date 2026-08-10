import os
import tempfile
import unittest

import backend
from action_store import ActionStore


class ActionStoreTests(unittest.TestCase):
    def test_state_and_audit_survive_store_restart_and_are_manager_scoped(self):
        with tempfile.TemporaryDirectory() as directory:
            path = os.path.join(directory, "actions.sqlite3")
            store = ActionStore(path)
            record = {"id": "act_m_1", "source": "raised", "title": "Review readiness",
                      "lifecycle_state": "open", "created_at": "2026-08-10T10:00:00Z",
                      "updated_at": "2026-08-10T10:00:00Z"}
            store.raise_action("one@koenig-solutions.com", record, "one@koenig-solutions.com")
            store.transition("one@koenig-solutions.com", "act_m_1", "in_progress",
                             "one@koenig-solutions.com", note="Owner accepted")

            restarted = ActionStore(path)
            actions = restarted.list_raised("one@koenig-solutions.com")
            restarted.overlay("one@koenig-solutions.com", actions)
            self.assertEqual("in_progress", actions[0]["lifecycle_state"])
            self.assertEqual(3, len(restarted.audit("one@koenig-solutions.com", "act_m_1")))
            self.assertEqual([], restarted.list_raised("two@koenig-solutions.com"))
            self.assertEqual([], restarted.audit("two@koenig-solutions.com", "act_m_1"))


class V2ActionRouteTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.previous_store = backend._action_repository
        backend._action_repository = ActionStore(os.path.join(self.temp.name, "actions.sqlite3"))
        backend._sessions.clear()
        backend._sessions["manager-session"] = {
            "email": "manager@koenig-solutions.com", "role": "manager"
        }
        self.client = backend.app.test_client()
        self.headers = {"Authorization": "Bearer manager-session"}

    def tearDown(self):
        backend._action_repository = self.previous_store
        backend._sessions.clear()
        self.temp.cleanup()

    def test_v2_mutations_require_session_and_reject_body_identity_spoofing(self):
        missing = self.client.post("/api/v2/actions", json={"title": "Review"})
        cross = self.client.post("/api/v2/actions", headers=self.headers,
                                 json={"title": "Review", "manager_email": "other@koenig-solutions.com"})
        self.assertEqual(401, missing.status_code)
        self.assertEqual(403, cross.status_code)

    def test_v2_action_lifecycle_has_append_only_audit(self):
        created = self.client.post("/api/v2/actions", headers=self.headers,
                                   json={"title": "Review delivery", "priority": "high"})
        self.assertEqual(201, created.status_code)
        action_id = created.get_json()["id"]
        moved = self.client.post(f"/api/v2/actions/{action_id}/state", headers=self.headers,
                                 json={"state": "in_progress", "note": "Assigned today"})
        self.assertEqual(200, moved.status_code)
        audit = self.client.get(f"/api/v2/actions/{action_id}/audit?manager=manager@koenig-solutions.com",
                                headers=self.headers)
        events = audit.get_json()["events"]
        self.assertEqual(["action_raised", "state_changed", "note_added"],
                         [event["event_type"] for event in events])
        self.assertEqual("manager@koenig-solutions.com", events[-1]["actor_email"])


if __name__ == "__main__":
    unittest.main()
