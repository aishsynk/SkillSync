import os
import tempfile
import unittest
from unittest.mock import patch

import backend
from reportee_store import ReporteeStore

MANAGER = "manager@koenig-solutions.com"
REPORTEE = "asha.k@koenig-solutions.com"
EMP_ID = "40771"

ROSTER = [
    {"OffEmail": REPORTEE, "TrainerName": "Asha K", "EmpId": EMP_ID, "TrainerPlus": "No"},
]


def _rms_stub(api, body, *a, **k):
    email = str((body or {}).get("email", "")).lower()
    if api == "reportees":
        return ROSTER if email == MANAGER else []
    if api == "unallocated":
        return [
            {"AssignmentID": "1", "CourseId": "11", "Coursename": "AZ-104: Microsoft Azure Administrator",
             "CourseSDate": "2026-10-01", "CourseEDate": "2026-10-05", "NoOfParticipants": 6},
            {"AssignmentID": "2", "CourseId": "22", "Coursename": "CKA: Certified Kubernetes Administrator",
             "CourseSDate": "2026-10-10", "CourseEDate": "2026-10-14", "NoOfParticipants": 4},
        ]
    if api == "trainerSkills":
        return [{"course_id": "11", "course_name": "AZ-104: Microsoft Azure Administrator"}]
    if api == "addTrainerSkill":
        return [{"JSON_F52E2B61": None}]
    return []


class ReporteeRoleTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.prev_repo = backend._reportee_repo
        backend._reportee_repo = ReporteeStore(os.path.join(self.temp.name, "reportees.sqlite3"))
        backend._sessions.clear()
        backend._manager_notifications.clear()
        backend._reportee_notifications.clear()
        backend._manager_email_cache.clear()
        self.client = backend.app.test_client()
        self.p_rms = patch.object(backend, "_rms", side_effect=_rms_stub)
        self.p_rms.start()
        self.p_emp = patch.object(backend, "_emp_code", return_value="9001")
        self.p_emp.start()

    def tearDown(self):
        self.p_rms.stop()
        self.p_emp.stop()
        backend._reportee_repo = self.prev_repo
        backend._sessions.clear()
        self.temp.cleanup()

    def _seed_directory(self):
        backend._reportee_repo.remember_roster(MANAGER, ROSTER)

    # ── identity ─────────────────────────────────────────────────────────────
    def test_classify_roster_owner_is_manager(self):
        role, mgr, _, needs_pw = backend._classify_identity(MANAGER)
        self.assertEqual(role, "manager")
        self.assertFalse(needs_pw)

    def test_classify_directory_member_is_reportee(self):
        self._seed_directory()
        role, mgr, resolved, needs_pw = backend._classify_identity(REPORTEE)
        self.assertEqual(role, "reportee")
        self.assertEqual(mgr, MANAGER)
        self.assertTrue(needs_pw)

    def test_classify_trainer_plus_needs_no_password(self):
        backend._reportee_repo.remember_roster(MANAGER, [
            {"OffEmail": "tp@koenig-solutions.com", "TrainerName": "TP", "EmpId": "1", "TrainerPlus": "Yes"},
        ])
        role, _, _, needs_pw = backend._classify_identity("tp@koenig-solutions.com")
        self.assertEqual(role, "trainer_plus")
        self.assertFalse(needs_pw)

    def test_classify_unknown_defaults_to_manager(self):
        role, _, _, needs_pw = backend._classify_identity("nobody.x@koenig-solutions.com")
        self.assertEqual(role, "manager")
        self.assertFalse(needs_pw)

    def test_auth_check_reports_role_without_session(self):
        self._seed_directory()
        r = self.client.post("/api/auth/check", json={"email": "asha.k"})
        body = r.get_json()
        self.assertTrue(body["ok"])
        self.assertEqual(body["role"], "reportee")
        self.assertTrue(body["needs_password"])
        self.assertTrue(body["first_login"])
        self.assertEqual(backend._sessions, {})  # no session minted

    def test_auth_check_manager_needs_no_password(self):
        r = self.client.post("/api/auth/check", json={"email": "manager"})
        self.assertFalse(r.get_json()["needs_password"])

    # ── login handshake ──────────────────────────────────────────────────────
    def test_reportee_login_requires_password_then_emp_code(self):
        self._seed_directory()
        r1 = self.client.post("/api/auth/login", json={"email": "asha.k"})
        self.assertEqual(r1.get_json()["code"], "PASSWORD_REQUIRED")

        r2 = self.client.post("/api/auth/login", json={"email": "asha.k", "password": "wrong"})
        self.assertEqual(r2.status_code, 401)

        r3 = self.client.post("/api/auth/login", json={"email": "asha.k", "password": EMP_ID})
        body = r3.get_json()
        self.assertTrue(body["success"])
        self.assertEqual(body["role"], "reportee")
        self.assertTrue(body["must_change"])

    def test_set_password_then_login_with_it(self):
        self._seed_directory()
        self.client.post("/api/auth/login", json={"email": "asha.k", "password": EMP_ID})
        sid = next(t for t, s in backend._sessions.items() if s["email"] == REPORTEE)
        rs = self.client.post("/api/auth/set-password", json={"new_password": "spring2026"},
                              headers={"Authorization": f"Bearer {sid}"})
        self.assertTrue(rs.get_json()["success"])

        r = self.client.post("/api/auth/login", json={"email": "asha.k", "password": "spring2026"})
        self.assertTrue(r.get_json()["success"])
        self.assertFalse(r.get_json()["must_change"])

    # ── scope ────────────────────────────────────────────────────────────────
    def _reportee_headers(self):
        backend._sessions["rp"] = {"email": REPORTEE, "role": "reportee"}
        return {"Authorization": "Bearer rp"}

    def test_reportee_cannot_call_manager_route(self):
        r = self.client.get("/api/v2/manager/skill-requests", headers=self._reportee_headers())
        self.assertEqual(r.status_code, 403)

    def test_reportee_demand_only_returns_skill_matches(self):
        r = self.client.get("/api/v2/reportee/demand", headers=self._reportee_headers())
        rows = r.get_json()["matched_demand"]
        names = {row["course_name"] for row in rows}
        self.assertIn("AZ-104: Microsoft Azure Administrator", names)
        self.assertNotIn("CKA: Certified Kubernetes Administrator", names)
        for row in rows:
            self.assertNotIn("Total Fee", row)
            self.assertNotIn("Currency", row)

    # ── skill ceiling ────────────────────────────────────────────────────────
    def test_reportee_mark_at_or_below_four_writes(self):
        with patch.object(backend, "_write_trainer_skill",
                          return_value=({"success": True, "verified": True}, 200)) as w:
            r = self.client.post("/api/action/mark-skill", headers=self._reportee_headers(),
                                 json={"course_id": "11", "skill_level": 3, "from_date": "2026-10-01"})
        self.assertTrue(r.get_json()["success"])
        w.assert_called_once()
        self.assertEqual(w.call_args[0][3], 3)

    def test_reportee_mark_above_four_creates_pending_request_no_write(self):
        self._seed_directory()
        with patch.object(backend, "_write_trainer_skill") as w:
            r = self.client.post("/api/action/mark-skill", headers=self._reportee_headers(),
                                 json={"course_id": "11", "skill_level": 7, "from_date": "2026-10-01"})
        body = r.get_json()
        self.assertTrue(body["pending"])
        w.assert_not_called()
        self.assertEqual(len(backend._reportee_repo.list_for_manager(MANAGER)), 1)
        self.assertTrue(backend._manager_notifications[MANAGER])
        self.assertTrue(backend._reportee_notifications[REPORTEE])

    def test_manager_approve_performs_write_and_resolves(self):
        self._seed_directory()
        self.client.post("/api/action/mark-skill", headers=self._reportee_headers(),
                         json={"course_id": "11", "skill_level": 7, "from_date": "2026-10-01"})
        req_id = backend._reportee_repo.list_for_manager(MANAGER)[0]["id"]
        backend._sessions["mgr"] = {"email": MANAGER, "role": "manager"}
        with patch.object(backend, "_write_trainer_skill",
                          return_value=({"success": True, "verified": True, "message": "ok"}, 200)) as w:
            r = self.client.post(f"/api/v2/manager/skill-requests/{req_id}",
                                 headers={"Authorization": "Bearer mgr"}, json={"decision": "approve"})
        self.assertTrue(r.get_json()["success"])
        self.assertEqual(w.call_args[0][3], 7)
        self.assertEqual(backend._reportee_repo.get_request(req_id)["status"], "approved")

    def test_manager_deny_resolves_without_write(self):
        self._seed_directory()
        self.client.post("/api/action/mark-skill", headers=self._reportee_headers(),
                         json={"course_id": "11", "skill_level": 8, "from_date": "2026-10-01"})
        req_id = backend._reportee_repo.list_for_manager(MANAGER)[0]["id"]
        backend._sessions["mgr"] = {"email": MANAGER, "role": "manager"}
        with patch.object(backend, "_write_trainer_skill") as w:
            r = self.client.post(f"/api/v2/manager/skill-requests/{req_id}",
                                 headers={"Authorization": "Bearer mgr"}, json={"decision": "deny"})
        self.assertTrue(r.get_json()["success"])
        w.assert_not_called()
        self.assertEqual(backend._reportee_repo.get_request(req_id)["status"], "denied")


if __name__ == "__main__":
    unittest.main()
