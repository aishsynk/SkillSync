import os
import unittest
from unittest.mock import patch

import backend


class CredentialEnvTests(unittest.TestCase):
    """Verify _ev reads env vars and tracks fallback usage."""

    def setUp(self):
        backend._ev_fallbacks = set()

    def test_ev_returns_env_var_when_set(self):
        with patch.dict(os.environ, {"MY_TEST_VAR": "from-env"}):
            self.assertEqual("from-env", backend._ev("MY_TEST_VAR", "fallback"))

    def test_ev_returns_fallback_when_env_var_absent(self):
        backend._ev_fallbacks = set()
        result = backend._ev("MY_DEFINITELY_UNSET_VAR", "fallback-value")
        self.assertEqual("fallback-value", result)

    def test_ev_tracks_fallback_usage(self):
        backend._ev_fallbacks = set()
        backend._ev("MY_DEFINITELY_UNSET_VAR_2", "fallback-value")
        self.assertIn("MY_DEFINITELY_UNSET_VAR_2", backend._ev_fallbacks)

    def test_ev_does_not_track_when_env_var_present(self):
        backend._ev_fallbacks = set()
        with patch.dict(os.environ, {"MY_TEST_VAR_2": "from-env"}):
            backend._ev("MY_TEST_VAR_2", "fallback-value")
        self.assertNotIn("MY_TEST_VAR_2", backend._ev_fallbacks)

    def test_ev_no_fallback_no_track(self):
        backend._ev_fallbacks = set()
        result = backend._ev("MY_DEFINITELY_UNSET_VAR_3")
        self.assertEqual("", result)
        self.assertNotIn("MY_DEFINITELY_UNSET_VAR_3", backend._ev_fallbacks)


class ValidateCredentialsTests(unittest.TestCase):
    """Verify _validate_credentials behavior in dev vs production."""

    def setUp(self):
        backend._ev_fallbacks = set()

    @patch("logging.warning")
    @patch("builtins.print")
    @patch.dict(os.environ, {"SKILLEDGE_ENV": "production"})
    def test_production_warns_and_prints_banner_when_fallbacks_exist(self, mock_print, mock_warning):
        backend._ev_fallbacks = {"SKILLEDGE_RMS_REPORTS_PASS"}
        backend._validate_credentials()
        mock_warning.assert_called_once()
        self.assertGreater(mock_print.call_count, 0)

    @patch("logging.warning")
    @patch("builtins.print")
    @patch.dict(os.environ, {"SKILLEDGE_ENV": "production"})
    def test_production_no_warning_when_no_fallbacks(self, mock_print, mock_warning):
        backend._ev_fallbacks = set()
        backend._validate_credentials()
        mock_warning.assert_not_called()
        mock_print.assert_not_called()

    @patch("logging.warning")
    @patch("builtins.print")
    @patch.dict(os.environ, {"SKILLEDGE_ENV": "development"})
    def test_development_does_not_print_banner(self, mock_print, mock_warning):
        backend._ev_fallbacks = {"SKILLEDGE_RMS_REPORTEES_PASS"}
        backend._validate_credentials()
        mock_warning.assert_called_once()
        mock_print.assert_not_called()

    @patch("logging.warning")
    @patch("builtins.print")
    @patch.dict(os.environ, {}, clear=True)
    def test_default_env_is_development(self, mock_print, mock_warning):
        backend._ev_fallbacks = {"SKILLEDGE_RMS_REPORTEES_PASS"}
        backend._validate_credentials()
        mock_warning.assert_called_once()
        mock_print.assert_not_called()


class ApisStructureTests(unittest.TestCase):
    """Verify _APIS entries have the expected shape after env-var wiring."""

    def test_all_apis_have_user_pass_role_key(self):
        for api_name, cfg in backend._APIS.items():
            self.assertIn("user", cfg, f"{api_name} missing 'user'")
            self.assertIn("pass", cfg, f"{api_name} missing 'pass'")
            self.assertIn("role", cfg, f"{api_name} missing 'role'")
            self.assertIn("key", cfg, f"{api_name} missing 'key'")

    def test_active_api_env_vars_exist(self):
        active = [
            "reportees", "trainerDetails", "utilization", "prevUpcoming",
            "unallocated", "courseWithoutExam", "assignment", "negFeedbackCount",
            "hrIncident", "trainerNegFeedback", "trainerSkills", "vendorCertCount",
            "trainerResume", "addTrainerSkill",
        ]
        for api_name in active:
            self.assertIn(api_name, backend._APIS, f"active API {api_name} not in _APIS")


if __name__ == "__main__":
    unittest.main()
