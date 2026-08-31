"""GET /api/v2/digest — proactive morning brief and weekly summary."""
import unittest
from datetime import datetime, timedelta
from unittest.mock import patch

import backend

MANAGER = "manager@koenig-solutions.com"


def _iso(d):
    return d.strftime("%Y-%m-%d")


FAKE_PRIORITIES = {
    "manager": MANAGER, "generated_at": "x", "counts": {},
    "items": [
        {"id": "unstaffed_demand:D1", "kind": "unstaffed_demand", "title": "Unstaffed: AZ-104",
         "detail": "...", "severity": "high",
         "due": _iso(datetime.utcnow().date() + timedelta(days=2)),
         "target_type": "demand", "target_id": "D1"},
        {"id": "overload:t@x", "kind": "overload", "title": "Beta Two is overloaded",
         "detail": "Utilisation at 96%.", "severity": "high", "due": "",
         "target_type": "trainer", "target_id": "t@x"},
        {"id": "one_to_one:a@x", "kind": "one_to_one", "title": "1:1 with Alpha One",
         "detail": "...", "severity": "high", "due": "",
         "target_type": "trainer", "target_id": "a@x"},
    ],
    "loading": False,
}


class ManagerDigestTests(unittest.TestCase):
    def setUp(self):
        backend._sessions.clear()
        backend._sessions["mgr"] = {"email": MANAGER, "role": "manager"}
        backend._warm_payload_cache.clear()
        backend._warm_building.clear()
        self.client = backend.app.test_client()
        self.headers = {"Authorization": "Bearer mgr"}
        self._p = patch.object(backend, "_priorities_build", return_value=FAKE_PRIORITIES)
        self._p.start()

    def tearDown(self):
        self._p.stop()
        backend._sessions.clear()
        backend._warm_payload_cache.clear()
        backend._warm_building.clear()

    def _build(self, kind):
        r = self.client.get(
            f"/api/v2/digest?manager={MANAGER}&kind={kind}&_build=1", headers=self.headers)
        self.assertEqual(200, r.status_code)
        return r.get_json()

    def test_requires_session(self):
        r = self.client.get(f"/api/v2/digest?manager={MANAGER}&kind=morning")
        self.assertEqual(401, r.status_code)

    def test_invalid_kind_rejected(self):
        r = self.client.get(
            f"/api/v2/digest?manager={MANAGER}&kind=bogus&_build=1", headers=self.headers)
        self.assertEqual(400, r.status_code)

    def test_morning_shape(self):
        b = self._build("morning")
        self.assertEqual("morning", b["kind"])
        self.assertFalse(b["loading"])
        self.assertTrue(b["headline"])
        self.assertIn("generated_at", b)
        self.assertGreaterEqual(len(b["items"]), 1)
        self.assertLessEqual(len(b["items"]), 5)
        self.assertEqual(1, b["unstaffed_this_week"])
        self.assertEqual(["Beta Two is overloaded"], b["flagged_trainers"])

    def test_weekly_reads_warm_report(self):
        today = datetime.utcnow().date()
        monday = today - timedelta(days=today.weekday())
        key = f"weekly::{MANAGER}::{_iso(monday)}"
        backend._warm_payload_cache[key] = (backend.time.time(), {
            "week_label": "01 September to 07 September 2026",
            "team_digest_weekend": "Hello team, solid week.",
            "team_summary": {
                "headcount": 5, "delivering_count": 3, "bench_count": 1,
                "at_risk_count": 0, "total_batches": 4, "total_participants": 40,
                "unallocated_demand": 2, "total_cert_gaps": 1,
            },
        })
        b = self._build("weekly")
        self.assertEqual("weekly", b["kind"])
        self.assertFalse(b["loading"])
        self.assertEqual("Hello team, solid week.", b["message"])
        self.assertEqual(5, b["summary"]["headcount"])
        self.assertEqual(1, b["summary"]["on_bench"])
        self.assertEqual(2, b["summary"]["unallocated_demand"])


if __name__ == "__main__":
    unittest.main()
