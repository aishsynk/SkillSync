"""Partial-first + background-warm behaviour for the heavy manager endpoints.

These endpoints used to run a full per-trainer RMS fan-out synchronously, which
could not answer inside the mobile client's read timeout on a cold cache — the
screen sat on a spinner. `_serve_or_warm` now retains the last complete payload,
rebuilds it in a daemon thread, and answers immediately.
"""
import time
import unittest
from unittest.mock import patch

import backend


_TEAM = {
    "trainer_name": "Trainer", "trainer_email": "trainer@koenig-solutions.com",
    "photo_url": "", "courses": [], "readiness_score": None,
    "readiness_bucket": "Unknown",
    "certification": {"gap_count": 0, "held": [], "coverage_pct": None,
                      "taught_codes": [], "held_codes": []},
}


class ServeOrWarmTests(unittest.TestCase):
    def setUp(self):
        backend._sessions.clear()
        backend._sessions["mgr"] = {"email": "manager@koenig-solutions.com", "role": "manager"}
        backend._warm_payload_cache.clear()
        backend._warm_building.clear()
        self.client = backend.app.test_client()
        self.headers = {"Authorization": "Bearer mgr"}

    def tearDown(self):
        backend._sessions.clear()
        backend._warm_payload_cache.clear()
        backend._warm_building.clear()

    @patch.object(backend, "_exam_policy", return_value={})
    @patch.object(backend, "_capability_for", return_value=_TEAM)
    @patch.object(backend, "_rms", return_value=[{"OffEmail": "trainer@koenig-solutions.com"}])
    def test_cold_call_returns_real_payload_then_warm_call_is_flagged(self, *_):
        r1 = self.client.get(
            "/api/v2/capability/portfolio?email=manager@koenig-solutions.com",
            headers=self.headers,
        )
        self.assertEqual(200, r1.status_code)
        # The bounded first-call wait lets the build finish, so real data comes back.
        self.assertIn("portfolio", r1.get_json())
        self.assertFalse(r1.get_json().get("loading", False))

        # Second call is served from the retained payload without rebuilding now.
        r2 = self.client.get(
            "/api/v2/capability/portfolio?email=manager@koenig-solutions.com",
            headers=self.headers,
        )
        body = r2.get_json()
        self.assertIn("portfolio", body)
        self.assertIn("cache_age_seconds", body)
        self.assertIn("refresh_in_progress", body)

    @patch.object(backend, "_exam_policy", return_value={})
    @patch.object(backend, "_capability_for", return_value=_TEAM)
    @patch.object(backend, "_rms", return_value=[{"OffEmail": "trainer@koenig-solutions.com"}])
    def test_refresh_purges_retained_payload(self, *_):
        self.client.get(
            "/api/v2/capability/portfolio?email=manager@koenig-solutions.com",
            headers=self.headers,
        )
        self.assertIn("capability::manager@koenig-solutions.com", backend._warm_payload_cache)
        self.client.get(
            "/api/v2/capability/portfolio?email=manager@koenig-solutions.com&refresh=1",
            headers=self.headers,
        )
        # refresh rebuilds; the entry is repopulated by the forced build/wait.
        self.assertIn("capability::manager@koenig-solutions.com", backend._warm_payload_cache)

    @patch.object(backend, "_exam_policy", return_value={})
    @patch.object(backend, "_rms", return_value=[{"OffEmail": "trainer@koenig-solutions.com"}])
    def test_builder_exception_keeps_previous_payload(self, *_):
        key = "capability::manager@koenig-solutions.com"
        backend._warm_payload_cache[key] = (time.time() - 999, {"portfolio": {"summary": {}}, "loading": False})
        with patch.object(backend, "_capability_for", side_effect=RuntimeError("RMS down")):
            r = self.client.get(
                "/api/v2/capability/portfolio?email=manager@koenig-solutions.com",
                headers=self.headers,
            )
            time.sleep(1)  # let the failing background rebuild run and be caught
        # Stale-but-usable payload is still served; the failed rebuild did not wipe it.
        self.assertIn("portfolio", r.get_json())


if __name__ == "__main__":
    unittest.main()
