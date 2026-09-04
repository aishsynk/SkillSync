"""_delivery_alerts_build — delivery-quality early-warning shape."""
import unittest
from datetime import datetime, timedelta
from unittest.mock import patch

import backend


def _iso(d):
    return d.strftime("%Y-%m-%d")


class DeliveryAlertsTests(unittest.TestCase):
    def test_recording_gap_and_roster_gap(self):
        today = datetime.utcnow().date()
        batches = [{
            "assignment_id": "A1", "engagement_state": "current", "course_name": "AZ-104",
            "trainer_name": "Alpha One", "participants": 10,
            "start_at": _iso(today - timedelta(days=1)),
        }]

        def fake_rms(api, body, *a, **k):
            if api == "recordingDetails":
                return []
            if api == "assignmentPax":
                return [{"StudentName": "x"}, {"StudentName": "y"}]
            return []

        with patch.object(backend, "_rms", side_effect=fake_rms):
            alerts = backend._delivery_alerts_build(batches, [], today)

        kinds = {a["kind"] for a in alerts}
        self.assertIn("recording_gap", kinds)
        self.assertIn("roster_gap", kinds)
        for a in alerts:
            self.assertEqual("A1", a["assignment_id"])
            self.assertEqual("AZ-104", a["course"])
            self.assertIn(a["severity"], ("high", "medium"))
            self.assertTrue(a["detail"])
        pax = next(a for a in alerts if a["kind"] == "roster_gap")
        self.assertEqual("high", pax["severity"])  # 8 of 10 places unfilled
        self.assertEqual(2, pax["enrolled_participants"])
        self.assertEqual(10, pax["expected_participants"])
        self.assertIn("8 places remain unfilled", pax["detail"])
        self.assertNotIn("dropped", pax["detail"].lower())

    def test_empty_roster_is_reported_when_expected_count_is_known(self):
        today = datetime.utcnow().date()
        batches = [{
            "assignment_id": "A0", "engagement_state": "upcoming", "course_name": "DP-600",
            "trainer_name": "Alpha One", "participants": 25, "start_at": _iso(today),
        }]
        with patch.object(backend, "_rms", return_value=[]):
            alerts = backend._delivery_alerts_build(batches, [], today)
        self.assertEqual(1, len(alerts))
        self.assertEqual("roster_gap", alerts[0]["kind"])
        self.assertEqual(0, alerts[0]["enrolled_participants"])
        self.assertIn("25 places remain unfilled", alerts[0]["detail"])

    def test_starts_soon_unstaffed(self):
        today = datetime.utcnow().date()
        demand = [{"demand_id": "D9", "course_name": "SC-200",
                   "start_date": _iso(today + timedelta(days=2))}]
        with patch.object(backend, "_rms", side_effect=lambda *a, **k: []):
            alerts = backend._delivery_alerts_build([], demand, today)
        self.assertEqual(1, len(alerts))
        self.assertEqual("starts_soon_unstaffed", alerts[0]["kind"])
        self.assertEqual("D9", alerts[0]["assignment_id"])
        self.assertEqual("high", alerts[0]["severity"])

    def test_no_alerts_when_compliant(self):
        today = datetime.utcnow().date()
        batches = [{
            "assignment_id": "A2", "engagement_state": "current", "course_name": "C",
            "trainer_name": "B", "participants": 5, "start_at": _iso(today),
        }]

        def fake_rms(api, body, *a, **k):
            if api == "recordingDetails":
                return [{"RecordingURL": "http://x"}]
            if api == "assignmentPax":
                return [{"StudentName": f"s{i}"} for i in range(6)]
            return []

        with patch.object(backend, "_rms", side_effect=fake_rms):
            alerts = backend._delivery_alerts_build(batches, [], today)
        self.assertEqual([], alerts)


if __name__ == "__main__":
    unittest.main()
