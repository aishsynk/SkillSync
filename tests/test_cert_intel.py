import unittest
from unittest.mock import patch

import backend


def _reset_taxonomy():
    backend._taxonomy_cache["data"] = None
    backend._taxonomy_cache["built_at"] = 0.0


class CourseTaxonomyTests(unittest.TestCase):
    def setUp(self):
        _reset_taxonomy()

    def tearDown(self):
        _reset_taxonomy()

    def _rms(self, api, body, *a, **k):
        if api == "courseTechnology":
            return [
                {"technology_name": "Azure", "course_name": "AZ-104: Microsoft Azure Administrator",
                 "course_id": "101", "technology_id": "1"},
                {"technology_name": "Azure", "course_name": "AI-102: Azure AI Solution",
                 "course_id": "102", "technology_id": "1"},
                {"technology_name": "Kubernetes", "course_name": "CKA: Certified Kubernetes Administrator",
                 "course_id": "201", "technology_id": "2"},
            ]
        if api == "courseDomain":
            return {
                "Azure": [{"CId": "101", "CName": "AZ-104", "DomainName": "Cloud"}],
                "Kubernetes": [{"CId": "201", "CName": "CKA", "DomainName": "DevOps"}],
            }[body["TechName"]]
        return []

    def test_taxonomy_builds_and_caches(self):
        with patch.object(backend, "_rms", side_effect=self._rms) as m:
            tax = backend._course_taxonomy()
            self.assertEqual({"technology": "Azure", "domain": "Cloud"},
                             tax[backend._norm_course("AZ-104: Microsoft Azure Administrator")])
            self.assertEqual("DevOps", tax["id:201"]["domain"])
            calls_after_first = m.call_count
            backend._course_taxonomy()          # served from module cache
            self.assertEqual(calls_after_first, m.call_count)

    def test_portfolio_groups_by_domain_and_technology(self):
        with patch.object(backend, "_rms", side_effect=self._rms):
            tax = backend._course_taxonomy()
        courses = [
            {"course": "AZ-104: Microsoft Azure Administrator", "vendor": "Microsoft",
             "coverage": "single", "exam_code": "AZ-104", "certified_count": 0},
            {"course": "AI-102: Azure AI Solution", "vendor": "Microsoft",
             "coverage": "shared", "exam_code": "AI-102", "certified_count": 2},
            {"course": "CKA: Certified Kubernetes Administrator", "vendor": "Linux Foundation",
             "coverage": "shared", "exam_code": "", "certified_count": 0},
        ]
        portfolio = backend._capability_portfolio([{"readiness_score": 80}], courses, tax)
        self.assertTrue(portfolio["confidence"]["domain_taxonomy_available"])
        cloud = next(r for r in portfolio["by_domain"] if r["domain"] == "Cloud")
        self.assertEqual(2, cloud["courses"])
        self.assertEqual(1, cloud["single_owner"])
        self.assertEqual(1, cloud["certification_exposed"])
        techs = {r["technology"]: r for r in portfolio["by_technology"]}
        self.assertEqual(2, techs["Azure"]["courses"])
        self.assertEqual("DevOps", techs["Kubernetes"]["domain"])

    def test_portfolio_without_taxonomy_stays_vendor_only(self):
        portfolio = backend._capability_portfolio([{"readiness_score": 80}], [
            {"course": "X", "vendor": "V", "coverage": "single", "exam_code": "", "certified_count": 0},
        ])
        self.assertFalse(portfolio["confidence"]["domain_taxonomy_available"])
        self.assertEqual([], portfolio["by_domain"])
        self.assertTrue(portfolio["vendor_coverage"])


class CertIntelRouteTests(unittest.TestCase):
    def setUp(self):
        _reset_taxonomy()
        backend._warm_purge("certintel")
        backend._sessions.clear()
        backend._sessions["mgr-session"] = {"email": "manager@koenig-solutions.com", "role": "manager"}
        self.client = backend.app.test_client()
        self.headers = {"Authorization": "Bearer mgr-session"}

    def tearDown(self):
        _reset_taxonomy()
        backend._sessions.clear()
        backend._warm_purge("certintel")

    def _rms(self, api, body, *a, **k):
        if api == "reportees":
            return [{"OffEmail": "t1@koenig-solutions.com", "TrainerName": "T One"},
                    {"OffEmail": "t2@koenig-solutions.com", "TrainerName": "T Two"}]
        if api == "unallocated":
            return [
                {"Coursename": "AZ-104: Microsoft Azure Administrator", "AssignmentID": "1", "CourseId": "101"},
                {"Coursename": "AZ-104: Microsoft Azure Administrator", "AssignmentID": "2", "CourseId": "101"},
                {"Coursename": "AI-102: Azure AI Solution", "AssignmentID": "3", "CourseId": "102"},
            ]
        if api == "courseWithoutExam":
            return []
        if api == "courseTechnology":
            return [{"technology_name": "Azure", "course_name": "AZ-104: Microsoft Azure Administrator",
                     "course_id": "101", "technology_id": "1"}]
        if api == "courseDomain":
            return [{"CId": "101", "CName": "AZ-104", "DomainName": "Cloud"}]
        if api == "vendorCertCount":
            return [{"Trainer": "T;E1", "Certificate Count": "1", "MCT": "True"}]
        return []

    def _capability_for(self, r, policy=None):
        email = r["OffEmail"]
        missing = [{"code": "AZ-104", "name": "Azure Administrator Associate", "because": "AZ-104"}]
        if email == "t2@koenig-solutions.com":
            missing.append({"code": "AI-102", "name": "Azure AI Engineer Associate", "because": "AI-102"})
        return {
            "trainer_name": r["TrainerName"], "trainer_email": email,
            "photo_url": "", "courses": [], "readiness_score": 70, "readiness_bucket": "Ready",
            "certification": {"missing": missing, "gap_count": len(missing), "held": [],
                              "coverage_pct": None, "taught_codes": [], "held_codes": []},
        }

    def test_requires_manager_scope(self):
        self.assertEqual(401, self.client.get("/api/v2/capability/cert-intel?email=manager@koenig-solutions.com").status_code)
        self.assertEqual(403, self.client.get(
            "/api/v2/capability/cert-intel?email=other@koenig-solutions.com", headers=self.headers).status_code)

    def test_demand_led_ranking_and_honest_empty_expiry(self):
        with patch.object(backend, "_rms", side_effect=self._rms), \
             patch.object(backend, "_capability_for", side_effect=self._capability_for):
            resp = self.client.get(
                "/api/v2/capability/cert-intel?email=manager@koenig-solutions.com&_build=1",
                headers=self.headers)
        self.assertEqual(200, resp.status_code)
        body = resp.get_json()

        # AZ-104 unlocks 2 batches, AI-102 one -> AZ-104 ranked first.
        self.assertEqual(["AZ-104", "AI-102"], [d["exam_code"] for d in body["demand_led"]])
        az = body["demand_led"][0]
        self.assertEqual(2, az["opens_batches"])
        self.assertEqual(2, az["trainers_missing"])       # both trainers lack AZ-104
        self.assertEqual("Cloud", az["domain"])
        self.assertEqual(1, body["demand_led"][1]["trainers_missing"])  # only T Two lacks AI-102

        # RMS exposes no expiry dates -> honest empty list + note.
        self.assertEqual([], body["expiring"])
        self.assertEqual("RMS does not expose certification expiry dates", body["note"])


if __name__ == "__main__":
    unittest.main()
