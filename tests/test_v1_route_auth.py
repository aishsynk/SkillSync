import unittest
from unittest.mock import patch

import backend


class V1RouteAuthGateTests(unittest.TestCase):
    """Every legacy /api/data/... and /api/actions V1 route now requires the
    Version 2 session and rejects cross-manager email requests (Task 1)."""

    MANAGER = "manager@koenig-solutions.com"
    OTHER = "other@koenig-solutions.com"

    def setUp(self):
        backend._sessions.clear()
        backend._allocation_payload_cache.clear()
        backend._sessions["session"] = {"email": self.MANAGER, "role": "manager"}
        self.client = backend.app.test_client()
        self.headers = {"Authorization": "Bearer session"}

    def tearDown(self):
        backend._sessions.clear()
        backend._allocation_payload_cache.clear()

    # ── Every V1 route rejects a missing session with 401 ──────────────────
    def test_v1_routes_require_a_session(self):
        unauthenticated = [
            ("/api/data/unified-manager-intelligence?email=%s" % self.MANAGER, "GET"),
            ("/api/data/manager-profile?email=%s" % self.MANAGER, "GET"),
            ("/api/data/trainer-360?email=trainer@koenig-solutions.com", "GET"),
            ("/api/data/team-capability?email=%s" % self.MANAGER, "GET"),
            ("/api/data/allocation-desk?email=%s" % self.MANAGER, "GET"),
            ("/api/data/trainer-skills?email=trainer@koenig-solutions.com", "GET"),
            ("/api/data/trainer-utilization-history?email=trainer@koenig-solutions.com", "GET"),
            ("/api/data/course-syllabus?courseName=DP-700", "GET"),
            ("/api/data/course-search?q=DP", "GET"),
            ("/api/data/course-intelligence?courseName=DP-700", "GET"),
            ("/api/data/alternative-trainers?course=DP-700", "GET"),
            ("/api/actions?email=%s" % self.MANAGER, "GET"),
        ]
        for url, _method in unauthenticated:
            with self.subTest(url=url):
                response = self.client.get(url)
                self.assertEqual(401, response.status_code, url)
                self.assertEqual("SESSION_REQUIRED", response.get_json()["code"])

    # ── Manager-keyed routes reject a cross-manager email with 403 ─────────
    def test_v1_routes_reject_cross_manager_email(self):
        cross_manager = [
            "/api/data/unified-manager-intelligence?email=%s" % self.OTHER,
            "/api/data/manager-profile?email=%s" % self.OTHER,
            "/api/data/team-capability?email=%s" % self.OTHER,
            "/api/data/allocation-desk?email=%s" % self.OTHER,
            "/api/actions?email=%s" % self.OTHER,
        ]
        for url in cross_manager:
            with self.subTest(url=url):
                response = self.client.get(url, headers=self.headers)
                self.assertEqual(403, response.status_code, url)
                self.assertEqual("MANAGER_SCOPE_MISMATCH", response.get_json()["code"])

    def test_trainer_360_rejects_cross_manager_ranking_context(self):
        response = self.client.get(
            "/api/data/trainer-360?email=trainer@koenig-solutions.com&manager=%s" % self.OTHER,
            headers=self.headers,
        )
        self.assertEqual(403, response.status_code)
        self.assertEqual("MANAGER_SCOPE_MISMATCH", response.get_json()["code"])

    def test_trainer_360_with_matching_manager_passes_scope(self):
        with (
            patch.object(backend, "_emp_code", return_value="1001"),
            patch.object(backend, "_util_row", return_value={}),
            patch.object(backend, "_skills", return_value=[]),
            patch.object(backend, "_certifications", return_value={"emp_code": "", "count": 0, "held": []}),
            patch.object(backend, "_off_dates", return_value=[]),
            patch.object(backend, "_resume", return_value={}),
            patch.object(backend, "_cert_intelligence", return_value={
                "held": [], "accreditations": [], "missing": [], "recommended": [],
                "coverage_pct": None, "gap_count": 0, "taught_codes": [],
            }),
            patch.object(backend, "_exam_policy", return_value={}),
            patch.object(backend, "_availability_evidence", return_value={
                "status": "unknown", "verified": False, "available": None,
            }),
            patch.object(backend, "_rms", return_value=[]),
        ):
            response = self.client.get(
                "/api/data/trainer-360?email=trainer@koenig-solutions.com&manager=%s" % self.MANAGER,
                headers=self.headers,
            )
        self.assertEqual(200, response.status_code)

    # ── With a valid session the V1 routes still work ──────────────────────
    def test_allocation_desk_serves_cached_payload_to_authenticated_manager(self):
        backend._allocation_payload_cache[self.MANAGER] = {
            "manager": self.MANAGER, "batches": [], "summary": {}, "refresh_in_progress": False,
        }
        response = self.client.get(
            "/api/data/allocation-desk?email=%s" % self.MANAGER,
            headers=self.headers,
        )
        self.assertEqual(200, response.status_code)
        self.assertEqual([], response.get_json()["batches"])

    def test_trainer_skills_serves_unverified_register_to_authenticated_manager(self):
        with patch.object(backend, "_emp_code", return_value=""):
            response = self.client.get(
                "/api/data/trainer-skills?email=trainer@koenig-solutions.com",
                headers=self.headers,
            )
        self.assertEqual(200, response.status_code)
        self.assertFalse(response.get_json()["available"])

    def test_course_search_serves_empty_result_to_authenticated_manager(self):
        with patch.object(backend, "_course_catalogue_index", return_value={
            "unrelated course": {"course_id": "1", "course_name": "Unrelated Course"},
        }):
            response = self.client.get("/api/data/course-search?q=DP-700", headers=self.headers)
        self.assertEqual(200, response.status_code)
        self.assertEqual(0, response.get_json()["count"])


if __name__ == "__main__":
    unittest.main()
