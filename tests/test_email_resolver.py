"""Login emails and RMS `OffEmail` do not always share the local-part separator
(aishwar_c@ vs aishwar.c@). `_resolve_manager_email` picks the form RMS answers."""
from unittest.mock import patch
import backend


def test_resolves_to_the_form_with_a_roster():
    backend._manager_email_cache.clear()

    def fake_rms(api, body):
        if api == "reportees":
            return [{"TrainerName": "X", "OffEmail": "x@koenig-solutions.com"}] \
                if body.get("email") == "aishwar.c@koenig-solutions.com" else []
        return []

    with patch.object(backend, "_rms", side_effect=fake_rms):
        assert backend._resolve_manager_email("aishwar_c@koenig-solutions.com") == "aishwar.c@koenig-solutions.com"


def test_falls_back_to_original_when_no_variant_has_a_roster():
    backend._manager_email_cache.clear()
    with patch.object(backend, "_rms", return_value=[]):
        assert backend._resolve_manager_email("nobody_x@koenig-solutions.com") == "nobody_x@koenig-solutions.com"


def test_v2_session_accepts_a_variant_of_the_signed_in_email():
    backend._sessions.clear()
    backend._sessions["s"] = {"email": "aishwar.c@koenig-solutions.com", "role": "manager"}
    with backend.app.test_request_context(headers={"Authorization": "Bearer s"}):
        sess, err = backend._v2_manager_session("aishwar_c@koenig-solutions.com")
    assert err is None and sess["email"] == "aishwar.c@koenig-solutions.com"
    backend._sessions.clear()
