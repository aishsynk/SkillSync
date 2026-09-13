"""Phase 1 capability-foundation tests.

Covers two independent surfaces:
  1. `CapabilityStore`/`CapabilityService` — the new domain/repository/service
     layer (Cid identity, dedup, alias resolution, relationship semantics,
     provenance, approval lifecycle, evidence-gating).
  2. `backend._capability_for()` — proving the 8 named-trainer fabrications
     and the generic AZ-104/MCT fallback are gone, and that "no RMS data"
     now produces an honest empty/unknown state instead.

No network calls: `backend._rms` is monkeypatched to return `[]` everywhere
capability-related tests need "RMS has nothing for this person."
"""

import os
import tempfile
import unittest

from domain.capability.models import MappingStatus, EvidenceType, RelationshipType
from repositories.capability_store import CapabilityStore
from services.capability.capability_service import CapabilityService


class CapabilityStoreTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.store = CapabilityStore(os.path.join(self.temp.name, "cap.sqlite3"))
        self.service = CapabilityService(self.store)

    def tearDown(self):
        self.temp.cleanup()

    # ── Cid identity ─────────────────────────────────────────────────────────

    def test_course_capability_keyed_on_cid_not_title_or_code(self):
        """Two different Cids with the SAME title must not collide — title is
        metadata, Cid is identity (per the Phase 0 finding of duplicate title
        rows, e.g. the real DP-700 course vs. its 1-day 'Exam Prep' variant)."""
        self.service.submit_course_capability_draft(
            rms_cid=18301, requirements=[{"capability_id": "microsoft_fabric", "mandatory": True}],
            course_title="DP-700T00: Microsoft Fabric Data Engineer", curator_email="carol",
        )
        self.service.submit_course_capability_draft(
            rms_cid=18768, requirements=[],  # the "DP-700 Exam Prep" variant — deliberately empty
            course_title="DP-700 Exam Prep", curator_email="carol",
        )
        p1 = self.store.latest_course_capability(18301)
        p2 = self.store.latest_course_capability(18768)
        self.assertEqual(len(p1["requirements"]), 1)
        self.assertEqual(len(p2["requirements"]), 0)
        self.assertNotEqual(p1["rms_cid"], p2["rms_cid"])

    # ── Duplicate prevention ─────────────────────────────────────────────────

    def test_duplicate_capability_and_alias_inserts_are_ignored(self):
        self.assertTrue(self.store.add_capability("apache_spark", "Apache Spark", "Data Engineering"))
        self.assertTrue(self.store.add_capability("apache_spark", "Apache Spark", "Data Engineering"))
        self.store.add_alias("apache_spark", "PySpark")
        self.store.add_alias("apache_spark", "PySpark")  # silently ignored, not an error
        self.assertEqual(self.store.resolve_alias("PySpark"), "apache_spark")

    # ── Alias normalization ──────────────────────────────────────────────────

    def test_alias_resolves_case_insensitively_exact_only(self):
        self.store.add_capability("apache_spark", "Apache Spark", "Data Engineering")
        self.store.add_alias("apache_spark", "PySpark")
        self.assertEqual(self.store.resolve_alias("pyspark"), "apache_spark")
        self.assertEqual(self.store.resolve_alias("PYSPARK"), "apache_spark")
        # No fuzzy/substring behaviour: a near-miss must not resolve.
        self.assertIsNone(self.store.resolve_alias("Py Sparks"))
        self.assertIsNone(self.store.resolve_alias("Spark-ish"))

    # ── Relationship semantics ───────────────────────────────────────────────

    def test_related_to_is_not_child_of_and_not_equivalence(self):
        self.store.add_capability("delta_lake", "Delta Lake", "Data Engineering")
        self.store.add_capability("apache_spark", "Apache Spark", "Data Engineering")
        self.store.add_relationship("delta_lake", "apache_spark", RelationshipType.RELATED_TO)
        rels = self.store.relationships_for("delta_lake")
        self.assertEqual(len(rels), 1)
        self.assertEqual(rels[0]["type"], RelationshipType.RELATED_TO)
        self.assertNotEqual(rels[0]["type"], RelationshipType.CHILD_OF)

    def test_parent_does_not_imply_every_child_is_satisfied(self):
        """A course requiring the parent capability is not satisfied merely
        because a relationship to a child exists — Phase 1 stores the graph;
        it does NOT expand requirements or grant satisfaction (that is the
        matching engine's job, explicitly out of scope here)."""
        self.store.add_capability("microsoft_fabric", "Microsoft Fabric", "Data Engineering")
        self.store.add_capability("onelake", "OneLake", "Data Engineering")
        self.store.add_relationship("onelake", "microsoft_fabric", RelationshipType.CHILD_OF)
        self.service.submit_course_capability_draft(
            rms_cid=1, requirements=[{"capability_id": "microsoft_fabric", "mandatory": True}],
            curator_email="carol",
        )
        profile = self.store.latest_course_capability(1)
        # Only the capability actually listed appears — no silent expansion.
        self.assertEqual([r["capability_id"] for r in profile["requirements"]], ["microsoft_fabric"])

    # ── Provenance ───────────────────────────────────────────────────────────

    def test_evidence_provenance_survives_round_trip(self):
        self.store.add_capability("microsoft_fabric", "Microsoft Fabric", "Data Engineering")
        raw = {"CourseName": "DP-700T00", "SkillLevel": "4", "OfficiallyApproved": "Yes"}
        self.store.add_trainer_capability_evidence(
            "trainer@koenig-solutions.com", "microsoft_fabric", EvidenceType.VERIFIED_SKILL,
            source_system="RMS.trainerDetails", source_record_id="18301", raw=raw,
        )
        caps = self.service.get_trainer_capabilities("trainer@koenig-solutions.com")
        self.assertEqual(len(caps), 1)
        ev = caps[0]["evidence"][0]
        self.assertEqual(ev["source_system"], "RMS.trainerDetails")
        self.assertEqual(ev["raw"], raw)  # original RMS row preserved exactly

    # ── Approval lifecycle ───────────────────────────────────────────────────

    def test_approval_lifecycle_and_authoritative_gating(self):
        self.service.submit_course_capability_draft(
            rms_cid=9055, requirements=[{"capability_id": "azure_compute", "mandatory": True}],
            curator_email="carol",
        )
        # DRAFT is invisible to an authoritative-only reader (the future matcher).
        self.assertIsNone(self.service.get_course_capability_profile(9055))
        self.assertIsNotNone(self.service.get_course_capability_profile(9055, authoritative_only=False))

        self.service.request_review(9055, updated_by="carol")
        self.assertEqual(
            self.service.get_course_capability_profile(9055, authoritative_only=False)["status"],
            MappingStatus.REVIEW_REQUIRED,
        )
        self.assertIsNone(self.service.get_course_capability_profile(9055))  # still not authoritative

        self.service.approve_course_capability(9055, approved_by="manager@koenig-solutions.com")
        approved = self.service.get_course_capability_profile(9055)
        self.assertIsNotNone(approved)
        self.assertEqual(approved["status"], MappingStatus.APPROVED)
        self.assertEqual(approved["updated_by"], "manager@koenig-solutions.com")

        self.service.deprecate_course_capability(9055, updated_by="manager@koenig-solutions.com")
        self.assertIsNone(self.service.get_course_capability_profile(9055))  # deprecated is not authoritative

    def test_approval_requires_a_named_approver(self):
        self.service.submit_course_capability_draft(rms_cid=1, requirements=[], curator_email="carol")
        self.assertFalse(self.service.approve_course_capability(1, approved_by=""))
        self.assertIsNone(self.service.get_course_capability_profile(1))

    # ── Empty capability state / fabrication-shaped invariant ───────────────

    def test_unverified_trainer_has_no_capabilities(self):
        """NO VERIFIED DATA -> NO CAPABILITY CLAIM."""
        self.assertEqual(self.service.get_trainer_capabilities("nobody@koenig-solutions.com"), [])

    def test_capability_without_evidence_cannot_be_created(self):
        """There is no service/store method that creates a TrainerCapability
        without evidence — attempting to record evidence with a blank type
        is refused outright, not silently defaulted."""
        ok = self.service.record_evidence(
            "trainer@koenig-solutions.com", "microsoft_fabric", evidence_type="",
        )
        self.assertFalse(ok)
        self.assertEqual(self.service.get_trainer_capabilities("trainer@koenig-solutions.com"), [])


class CapabilityForFabricationRemovedTests(unittest.TestCase):
    """Exercises the real `backend._capability_for` with RMS mocked to return
    nothing, proving the 8 hardcoded profiles and the generic fallback are
    gone — the critical regression test this phase exists to satisfy."""

    PREVIOUSLY_HARDCODED_EMAILS = [
        "subhashish.bhattacharjee@koenig-solutions.com",
        "sachin.khanna@koenig-solutions.com",
        "neha.sharma@koenig-solutions.com",
        "rohit.agarwal@koenig-solutions.com",
        "amit.kumar@koenig-solutions.com",
        "vikas.sharma@koenig-solutions.com",
        "priyanshu.sharma@koenig-solutions.com",
        "aishwar.singh@koenig-solutions.com",
    ]

    def setUp(self):
        import backend
        self.backend = backend
        self._orig_rms = backend._rms
        backend._rms = lambda *a, **k: []  # RMS has nothing for anyone in this test

    def tearDown(self):
        self.backend._rms = self._orig_rms

    def _row(self, email):
        return {"OffEmail": email, "TrainerName": "Some Trainer", "EmpId": "1", "Designation": "Trainer"}

    def test_previously_fabricated_emails_now_get_honest_empty_state(self):
        for email in self.PREVIOUSLY_HARDCODED_EMAILS:
            with self.subTest(email=email):
                result = self.backend._capability_for(self._row(email), policy={})
                self.assertIsNotNone(result)
                self.assertEqual(result["courses"], [], f"{email} still has invented courses")
                self.assertEqual(result["course_count"], 0)
                self.assertFalse(result["capability_data_available"])
                self.assertIsNone(result["avg_qubits"], "avg_qubits must be None, not a fabricated/zero score")
                self.assertIsNone(result["utilization"], "utilization must be None (unknown), not a guessed 82")
                held_names = {c.get("name") for c in result["certification"]["held"]}
                self.assertEqual(held_names, set(), f"{email} still has invented certifications")

    def test_unknown_trainer_receives_no_invented_skills_or_certs(self):
        """The generic AZ-104/MCT fallback must not fire for an arbitrary
        trainer either — this is the fallback that used to apply to
        literally anyone with empty RMS data, not just the 8 named ones."""
        result = self.backend._capability_for(self._row("totally.unknown@koenig-solutions.com"), policy={})
        self.assertEqual(result["courses"], [])
        self.assertEqual(result["certification"]["held"], [])
        self.assertIsNone(result["avg_qubits"])

    def test_no_named_email_fabrication_constant_remains_in_source(self):
        """Static check: the removed hardcoded dicts must not have crept back
        into the module as some other name."""
        import inspect
        source = inspect.getsource(self.backend._capability_for)
        for email in self.PREVIOUSLY_HARDCODED_EMAILS:
            self.assertNotIn(email, source, f"{email} still hardcoded in _capability_for")
        self.assertNotIn("known_courses", source)
        self.assertNotIn("known_certs", source)

    def test_endpoint_still_returns_200_with_empty_capability_trainers(self):
        """Endpoint compatibility: /api/v2/data/team-capability must not
        crash now that `_capability_for` can return None-valued fields it
        never returned before.

        Uses a manager email unique to this test — `team_capability()` warm-
        caches its response under `capability::<email>` (`_serve_or_warm`),
        so reusing another test's email (e.g. the widely-used
        "manager@koenig-solutions.com") would leak a cached empty payload
        into any test that later hits the same route for that email with
        different mocks, exactly the kind of cross-test pollution this suite
        must not introduce.
        """
        backend = self.backend
        email = "phase1.capability.endpoint.test@koenig-solutions.com"
        prev_sessions = dict(backend._sessions)
        backend._sessions.clear()
        backend._sessions["mgr-session"] = {"email": email, "role": "manager"}
        try:
            client = backend.app.test_client()
            resp = client.get(
                f"/api/v2/data/team-capability?email={email}",
                headers={"Authorization": "Bearer mgr-session"},
            )
            self.assertEqual(resp.status_code, 200)
            body = resp.get_json()
            self.assertIn("trainers", body)
            self.assertIn("kpis", body)
        finally:
            backend._sessions.clear()
            backend._sessions.update(prev_sessions)
            backend._cache_purge(email)


if __name__ == "__main__":
    unittest.main()
