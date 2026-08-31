"""GET /api/v2/manager/priorities — the ranked "Your Week" worklist."""
import os
import tempfile
import unittest
from datetime import datetime, timedelta
from unittest.mock import patch

from action_store import ActionStore

import backend

MANAGER = "manager@koenig-solutions.com"
T1 = "alpha@koenig-solutions.com"   # negative feedback
T2 = "beta@koenig-solutions.com"    # overloaded


def _d(days):
    return (datetime.utcnow().date() + timedelta(days=days)).strftime("%Y-%m-%d")


def _fake_rms(api_name, body, *a, **k):
    if api_name == "reportees":
        return [
            {"OffEmail": T1, "TrainerName": "Alpha One"},
            {"OffEmail": T2, "TrainerName": "Beta Two"},
        ]
    if api_name == "unallocated":
        return [
            {"AssignmentID": "D-NEAR", "Coursename": "AZ-104", "CourseSDate": _d(3), "CourseEDate": _d(7)},
            {"AssignmentID": "D-FAR", "Coursename": "SC-200", "CourseSDate": _d(15), "CourseEDate": _d(18)},
        ]
    if api_name == "negFeedbackCount":
        return [{"Total": 2}] if body.get("email") == T1 else []
    if api_name == "hrIncident":
        return []
    return []


def _fake_util_series(row):
    return row if isinstance(row, list) else []


class ManagerPrioritiesTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.previous_store = backend._action_repository
        backend._action_repository = ActionStore(os.path.join(self.temp.name, "actions.sqlite3"))
        backend._sessions.clear()
        backend._sessions["mgr"] = {"email": MANAGER, "role": "manager"}
        backend._warm_payload_cache.clear()
        backend._warm_building.clear()
        self.client = backend.app.test_client()
        self.headers = {"Authorization": "Bearer mgr"}
        self._patchers = [
            patch.object(backend, "_rms", side_effect=_fake_rms),
            patch.object(backend, "_exam_policy", return_value={}),
            patch.object(backend, "_skills", return_value=[]),
            patch.object(backend, "_certifications", return_value={"held": []}),
            patch.object(backend, "_cert_intelligence",
                         return_value={"gaps": [{"because": "AZ-104"}], "gap_count": 1}),
            patch.object(backend, "_util_row",
                         side_effect=lambda e: [{"utilization": 96}] if e == T2 else []),
            patch.object(backend, "_util_series", side_effect=_fake_util_series),
        ]
        for p in self._patchers:
            p.start()

    def tearDown(self):
        for p in self._patchers:
            p.stop()
        backend._action_repository = self.previous_store
        backend._sessions.clear()
        backend._warm_payload_cache.clear()
        backend._warm_building.clear()
        self.temp.cleanup()

    def _build(self):
        r = self.client.get(
            "/api/v2/manager/priorities?manager=%s&_build=1" % MANAGER, headers=self.headers)
        self.assertEqual(200, r.status_code)
        return r.get_json()

    def test_requires_session(self):
        r = self.client.get("/api/v2/manager/priorities?manager=%s" % MANAGER)
        self.assertEqual(401, r.status_code)

    def test_items_ranked_descending_and_capped(self):
        body = self._build()
        self.assertFalse(body["loading"])
        scores = [it["rank_score"] for it in body["items"]]
        self.assertEqual(scores, sorted(scores, reverse=True))
        self.assertLessEqual(len(body["items"]), 40)
        self.assertEqual(sum(body["counts"].values()), len(body["items"]))

    def test_severities_by_kind(self):
        items = {it["id"]: it for it in self._build()["items"]}
        self.assertEqual("high", items["unstaffed_demand:D-NEAR"]["severity"])
        self.assertEqual("medium", items["unstaffed_demand:D-FAR"]["severity"])
        self.assertEqual("high", items["one_to_one:%s" % T1]["severity"])
        self.assertEqual("medium", items["overload:%s" % T2]["severity"])
        self.assertEqual(_d(3), items["unstaffed_demand:D-NEAR"]["due"])
        # A high-severity, imminent item outranks a far medium one.
        self.assertGreater(items["one_to_one:%s" % T1]["rank_score"],
                           items["unstaffed_demand:D-FAR"]["rank_score"])

    def test_action_overdue_severity_and_due(self):
        created = (datetime.utcnow() - timedelta(days=20)).isoformat()
        backend._action_repository.raise_action(MANAGER, {
            "id": "act_old", "source": "raised", "title": "Chase vendor contract",
            "detail": "still pending", "lifecycle_state": "open",
            "created_at": created, "updated_at": created,
        }, MANAGER)
        items = {it["id"]: it for it in self._build()["items"]}
        self.assertIn("action_overdue:act_old", items)
        it = items["action_overdue:act_old"]
        self.assertEqual("high", it["severity"])          # >= 14 days old
        self.assertEqual("action", it["target_type"])
        expected_due = (datetime.fromisoformat(created).date() + timedelta(days=7)).isoformat()
        self.assertEqual(expected_due, it["due"])


if __name__ == "__main__":
    unittest.main()
