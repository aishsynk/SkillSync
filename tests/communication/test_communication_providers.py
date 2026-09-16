"""Model provider boundary — configurable order, Ollama, Azure/OpenAI, deterministic.

All HTTP is mocked at providers._http_json; no real Ollama is required.
"""

import os
import tempfile
import unittest
import urllib.error
from unittest import mock

import backend
from repositories.communication_store import CommunicationStore
from services.communication import providers
from services.communication.providers import ProviderTimeout
from services.communication.service import CommunicationService

MANAGER = "comm@koenig-solutions.com"
BASE = "http://127.0.0.1:11434"
TAGS = {"models": [{"name": "nomic-embed-text:latest"}, {"name": "qwen2.5:7b-instruct"}, {"name": "llama3.2:3b"}]}
_ENV_KEYS = ("OLLAMA_BASE_URL", "OLLAMA_MODEL", "OLLAMA_TIMEOUT_SECONDS", "OLLAMA_KEEP_ALIVE", "OPENAI_API_KEY", "OPENAI_MODEL",
             "AZURE_OPENAI_ENDPOINT", "AZURE_OPENAI_KEY", "AZURE_OPENAI_API_KEY", "COMMUNICATION_PROVIDER_ORDER")


def names(*n):
    return {"models": [{"name": x} for x in n]}


class FakeHttp:
    """Answers Ollama /api/tags + /api/chat and Azure/OpenAI chat completions; records calls."""

    def __init__(self, tags=TAGS, chat=None, chat_error=None, tags_error=None):
        self.tags, self.chat, self.chat_error, self.tags_error = tags, chat, chat_error, tags_error
        self.calls = []

    def __call__(self, url, body, headers, timeout):
        self.calls.append((url, body, timeout))
        if url.endswith("/api/tags"):
            if self.tags_error:
                raise self.tags_error
            return self.tags
        if url.endswith("/api/chat"):
            if self.chat_error:
                raise self.chat_error
            return self.chat if self.chat is not None else {"message": {"role": "assistant", "content": "Hello team,\n\nFrom Ollama.\n\n*Thanks*"}}
        if "openai.azure" in url:
            return {"choices": [{"message": {"content": "Hello team,\n\nFrom Azure.\n\n*Thanks*"}}]}
        if "api.openai.com" in url:
            return {"choices": [{"message": {"content": "Hello team,\n\nFrom OpenAI.\n\n*Thanks*"}}]}
        raise AssertionError(f"unexpected URL {url}")

    def chat_bodies(self):
        return [b for (u, b, _) in self.calls if u.endswith("/api/chat")]

    def urls(self):
        return [u for (u, _, _) in self.calls]


class ProviderTestBase(unittest.TestCase):
    def setUp(self):
        self.env = mock.patch.dict(os.environ, {}, clear=False)
        self.env.start()
        for k in _ENV_KEYS:
            os.environ.pop(k, None)
        providers.reset_ollama_cache()

    def tearDown(self):
        self.env.stop()
        providers.reset_ollama_cache()

    def all_configured(self):
        os.environ.update(OLLAMA_BASE_URL=BASE, OPENAI_API_KEY="k",
                          AZURE_OPENAI_ENDPOINT="https://x.openai.azure.com", AZURE_OPENAI_KEY="az")


class ProviderOrderTest(ProviderTestBase):
    def test_default_order_is_ollama_azure_openai_deterministic(self):
        self.assertEqual(["ollama", "azure", "openai", "deterministic"], providers.provider_order())

    def test_order_parsing_normalises_dedupes_and_ignores_unknown(self):
        os.environ["COMMUNICATION_PROVIDER_ORDER"] = " Azure , openai,bogus,AZURE, deterministic "
        self.assertEqual(["azure", "openai", "deterministic"], providers.provider_order())
        os.environ["COMMUNICATION_PROVIDER_ORDER"] = "nonsense,,"
        self.assertEqual(list(providers.DEFAULT_PROVIDER_ORDER), providers.provider_order())

    def test_production_order_never_calls_ollama(self):
        self.all_configured()
        os.environ["COMMUNICATION_PROVIDER_ORDER"] = "azure,openai,deterministic"
        fake = FakeHttp()
        with mock.patch.object(providers, "_http_json", fake):
            out = providers.generate("s", "u")
        self.assertEqual("AZURE_OPENAI", out.result.provider)
        self.assertFalse(any("11434" in u for u in fake.urls()))

    def test_deterministic_only_makes_no_calls(self):
        self.all_configured()
        os.environ["COMMUNICATION_PROVIDER_ORDER"] = "deterministic"
        fake = FakeHttp()
        with mock.patch.object(providers, "_http_json", fake):
            out = providers.generate("s", "u")
        self.assertIsNone(out.result)
        self.assertEqual([], fake.calls)

    def test_deterministic_stops_the_chain_before_later_providers(self):
        self.all_configured()
        os.environ["COMMUNICATION_PROVIDER_ORDER"] = "deterministic,openai"
        fake = FakeHttp()
        with mock.patch.object(providers, "_http_json", fake):
            self.assertIsNone(providers.generate("s", "u").result)
        self.assertEqual([], fake.calls)

    def test_order_fallback_moves_past_an_unavailable_provider(self):
        self.all_configured()
        os.environ["COMMUNICATION_PROVIDER_ORDER"] = "ollama,openai,azure"
        fake = FakeHttp(tags_error=urllib.error.URLError("refused"))
        with mock.patch.object(providers, "_http_json", fake):
            out = providers.generate("s", "u")
        self.assertEqual("OPENAI", out.result.provider)
        self.assertEqual(["ollama", "openai"], out.tried)
        self.assertEqual("", out.timeout_reason)


class OllamaProviderTest(ProviderTestBase):
    def test_configured_and_reachable_ollama_is_selected_with_keep_alive(self):
        os.environ["OLLAMA_BASE_URL"] = BASE
        fake = FakeHttp()
        with mock.patch.object(providers, "_http_json", fake):
            out = providers.generate("sys", "user", 0.3, 100)
        self.assertEqual("OLLAMA", out.result.provider)
        body = fake.chat_bodies()[0]
        self.assertEqual(False, body["stream"])
        self.assertEqual("15m", body["keep_alive"])
        self.assertEqual([{"role": "system", "content": "sys"}, {"role": "user", "content": "user"}], body["messages"])
        self.assertEqual({"temperature": 0.3, "num_predict": 100}, body["options"])

    def test_keep_alive_is_configurable_but_validated(self):
        os.environ.update(OLLAMA_BASE_URL=BASE, OLLAMA_KEEP_ALIVE="30m")
        fake = FakeHttp()
        with mock.patch.object(providers, "_http_json", fake):
            providers.generate("s", "u")
        self.assertEqual("30m", fake.chat_bodies()[0]["keep_alive"])
        os.environ["OLLAMA_KEEP_ALIVE"] = "forever; rm -rf"
        self.assertEqual("15m", providers._ollama_keep_alive())

    def test_explicit_ollama_model_overrides_ranking_even_for_a_coder_model(self):
        os.environ.update(OLLAMA_BASE_URL=BASE, OLLAMA_MODEL="qwen2.5-coder:7b")
        fake = FakeHttp(tags=names("llama3.2:3b", "qwen2.5-coder:7b"))
        with mock.patch.object(providers, "_http_json", fake):
            out = providers.generate("s", "u")
        self.assertEqual("qwen2.5-coder:7b", out.result.model)

    def test_missing_configured_model_falls_back_to_discovery(self):
        os.environ.update(OLLAMA_BASE_URL=BASE, OLLAMA_MODEL="mistral:7b")
        fake = FakeHttp()
        with mock.patch.object(providers, "_http_json", fake):
            out = providers.generate("s", "u")
        self.assertEqual("qwen2.5:7b-instruct", out.result.model)

    def test_discovery_prefers_instruct_then_general_then_generic_and_coder_last(self):
        pick = providers._pick_ollama_model
        self.assertEqual("llama3.2:3b", pick(["qwen-coder-fast:latest", "nomic-embed-text", "llama3.2:3b"]))
        self.assertEqual("mistral:7b-instruct", pick(["qwen2.5-coder:7b", "gemma3:4b", "mistral:7b-instruct"]))
        self.assertEqual("my-custom-model:latest", pick(["deepseek-coder:6.7b", "my-custom-model:latest"]))
        self.assertEqual("qwen-coder-fast:latest", pick(["nomic-embed-text:latest", "qwen-coder-fast:latest"]))
        self.assertIsNone(pick(["nomic-embed-text:latest", "mxbai-embed-large", "all-minilm"]))

    def test_no_installed_models_skips_the_provider(self):
        os.environ["OLLAMA_BASE_URL"] = BASE
        fake = FakeHttp(tags={"models": []})
        with mock.patch.object(providers, "_http_json", fake):
            self.assertIsNone(providers.generate("s", "u").result)
        self.assertEqual([], fake.chat_bodies())

    def test_timeout_is_recorded_and_continues_to_the_next_provider(self):
        os.environ.update(OLLAMA_BASE_URL=BASE, OPENAI_API_KEY="k")
        fake = FakeHttp(chat_error=ProviderTimeout("timed out"))
        with mock.patch.object(providers, "_http_json", fake):
            out = providers.generate("s", "u")
        self.assertEqual("OPENAI", out.result.provider)
        self.assertTrue(out.timed_out)
        self.assertTrue(out.timeout_reason.startswith("OLLAMA timed out after"))

    def test_timeout_default_is_10s_and_hard_capped_at_20s(self):
        os.environ["OLLAMA_BASE_URL"] = BASE
        self.assertEqual(10.0, providers._ollama_timeout())
        os.environ["OLLAMA_TIMEOUT_SECONDS"] = "9999"
        self.assertEqual(20.0, providers._ollama_timeout())
        fake = FakeHttp()
        with mock.patch.object(providers, "_http_json", fake):
            providers.generate("s", "u")
        self.assertTrue(all(t <= 20.0 for (_, _, t) in fake.calls))
        self.assertTrue(all(t <= 3.0 for (u, _, t) in fake.calls if u.endswith("/api/tags")))

    def test_budget_clamps_provider_timeouts_and_stops_when_exhausted(self):
        os.environ.update(OLLAMA_BASE_URL=BASE, OLLAMA_TIMEOUT_SECONDS="20")
        fake = FakeHttp()
        with mock.patch.object(providers, "_http_json", fake):
            providers.generate("s", "u", budget_ms=4000)
        chat_timeouts = [t for (u, _, t) in fake.calls if u.endswith("/api/chat")]
        self.assertTrue(chat_timeouts and all(t <= 4.0 for t in chat_timeouts), chat_timeouts)
        fake = FakeHttp()
        with mock.patch.object(providers, "_http_json", fake):
            out = providers.generate("s", "u", budget_ms=0)
        self.assertIsNone(out.result)
        self.assertEqual([], fake.calls)
        self.assertEqual("request budget exhausted", out.timeout_reason)

    def test_real_socket_timeout_is_translated_to_provider_timeout(self):
        import socket
        with mock.patch("urllib.request.urlopen", side_effect=socket.timeout("slow")):
            with self.assertRaises(ProviderTimeout):
                providers._http_json(f"{BASE}/api/chat", {}, {}, 1.0)
        with mock.patch("urllib.request.urlopen", side_effect=urllib.error.URLError(socket.timeout("slow"))):
            with self.assertRaises(ProviderTimeout):
                providers._http_json(f"{BASE}/api/chat", {}, {}, 1.0)

    def test_malformed_ollama_response_falls_through(self):
        os.environ["OLLAMA_BASE_URL"] = BASE
        for bad in ({"message": None}, {"unexpected": 1}, {"message": {"content": "   "}}, ["not", "a", "dict"]):
            providers.reset_ollama_cache()
            with mock.patch.object(providers, "_http_json", FakeHttp(chat=bad)):
                self.assertIsNone(providers.generate("s", "u").result, bad)

    def test_unsafe_base_urls_are_rejected(self):
        for bad in ("file:///etc/passwd", "http://user:pw@host:11434", "http://host:11434/api?x=1", "gopher://host", "not a url"):
            os.environ["OLLAMA_BASE_URL"] = bad
            self.assertIsNone(providers._ollama_base_url(), bad)
        os.environ["OLLAMA_BASE_URL"] = "http://10.0.0.5:11434/"
        self.assertEqual("http://10.0.0.5:11434", providers._ollama_base_url())

    def test_unconfigured_ollama_makes_no_network_call(self):
        fake = FakeHttp()
        with mock.patch.object(providers, "_http_json", fake):
            self.assertIsNone(providers.generate("s", "u").result)
        self.assertEqual([], fake.calls)


class CommunicationServiceProviderTest(ProviderTestBase):
    def setUp(self):
        super().setUp()
        self.temp = tempfile.TemporaryDirectory()
        self.svc = CommunicationService(CommunicationStore(os.path.join(self.temp.name, "c.sqlite3")))

    def tearDown(self):
        self.temp.cleanup()
        super().tearDown()

    def test_general_purposes_use_ollama_with_latency_provenance(self):
        os.environ["OLLAMA_BASE_URL"] = BASE
        with mock.patch.object(providers, "_http_json", FakeHttp()):
            r = self.svc.generate(MANAGER, {"recipient": {"type": "TEAM"}, "myMessage": "please confirm availability for next week"})
        self.assertEqual("OLLAMA", r.provenance["provider"])
        self.assertEqual("qwen2.5:7b-instruct", r.provenance["model"])
        self.assertFalse(r.provenance["fallback_used"])
        self.assertIsInstance(r.provenance["elapsed_ms"], int)
        self.assertNotIn("timeout_reason", r.provenance)
        self.assertEqual("LLM_OLLAMA", r.generation_mode)

    def test_unconfigured_providers_are_not_reported_as_attempted(self):
        r = self.svc.generate(MANAGER, {"recipient": {"type": "TEAM"}, "myMessage": "status please"})
        self.assertEqual([], r.provenance["attempted_providers"])
        self.assertEqual(0, r.provenance["attempts"])

    def test_attempted_providers_records_the_real_chain(self):
        os.environ.update(OLLAMA_BASE_URL=BASE, OPENAI_API_KEY="k")
        fake = FakeHttp(chat_error=ProviderTimeout("slow"))
        with mock.patch.object(providers, "_http_json", fake):
            r = self.svc.generate(MANAGER, {"recipient": {"type": "TEAM"}, "myMessage": "status please"})
        self.assertEqual("OPENAI", r.provenance["provider"])
        self.assertEqual(["ollama", "openai"], r.provenance["attempted_providers"])
        self.assertTrue(r.provenance["timeout_reason"].startswith("OLLAMA timed out"))

    def test_timeout_provenance_on_deterministic_fallback(self):
        os.environ["OLLAMA_BASE_URL"] = BASE
        with mock.patch.object(providers, "_http_json", FakeHttp(chat_error=ProviderTimeout("slow"))):
            r = self.svc.generate(MANAGER, {"recipient": {"type": "TEAM"}, "myMessage": "please confirm availability for next week"})
        self.assertEqual("DETERMINISTIC", r.provenance["provider"])
        self.assertTrue(r.provenance["fallback_used"])
        self.assertEqual(["ollama"], r.provenance["attempted_providers"])
        self.assertEqual(1, r.provenance["attempts"])
        self.assertTrue(r.provenance["timeout_reason"].startswith("OLLAMA timed out"))

    def test_morning_greeting_timeout_is_not_retried(self):
        os.environ["OLLAMA_BASE_URL"] = BASE
        fake = FakeHttp(chat_error=ProviderTimeout("slow"))
        with mock.patch.object(providers, "_http_json", fake):
            r = self.svc.generate(MANAGER, {"purpose": "MORNING_TEAM_GREETING", "localWeekday": "WEDNESDAY"})
        self.assertEqual(1, len(fake.chat_bodies()))
        self.assertTrue(r.text)
        self.assertEqual("DETERMINISTIC", r.provenance["provider"])
        self.assertIn("timeout_reason", r.provenance)

    def test_morning_greeting_uses_ollama_and_sends_recent_greetings_in_the_prompt(self):
        os.environ["OLLAMA_BASE_URL"] = BASE
        good = "Midweek already, everyone.\n\n*Halfway through* - a good day to help someone past a hurdle. _Have a good Wednesday._"
        fake = FakeHttp(chat={"message": {"content": good}})
        with mock.patch.object(providers, "_http_json", fake):
            r = self.svc.generate(MANAGER, {"purpose": "MORNING_TEAM_GREETING", "localWeekday": "WEDNESDAY",
                                            "recentGreetings": ["Hi all, halfway there.\n\nOld note."]})
        self.assertEqual(good, r.text)
        self.assertEqual("OLLAMA", r.provenance["provider"])
        body = fake.chat_bodies()[0]
        self.assertIn("Hi all, halfway there.", body["messages"][1]["content"])
        self.assertIn("WEDNESDAY - MIDWEEK RESET", body["messages"][0]["content"])
        self.assertEqual("15m", body["keep_alive"])

    def test_client_cannot_supply_an_ollama_url_or_model(self):
        os.environ["OLLAMA_BASE_URL"] = BASE
        fake = FakeHttp()
        backend._sessions["comm-session"] = {"email": MANAGER, "role": "manager"}
        prev = backend._communication_service
        backend._communication_service = self.svc
        try:
            with mock.patch.object(providers, "_http_json", fake):
                res = backend.app.test_client().post(
                    "/api/v2/communication/generate?ollama_base_url=http://evil.example",
                    json={"manager": MANAGER, "myMessage": "status please", "recipient": {"type": "TEAM"},
                          "ollama_base_url": "http://169.254.169.254", "OLLAMA_BASE_URL": "http://evil.example",
                          "COMMUNICATION_PROVIDER_ORDER": "openai", "baseUrl": "http://evil.example", "model": "attacker"},
                    headers={"Authorization": "Bearer comm-session"},
                )
        finally:
            backend._communication_service = prev
            backend._sessions.pop("comm-session", None)
        self.assertEqual(200, res.status_code)
        self.assertTrue(fake.urls())
        self.assertTrue(all(u.startswith(BASE) for u in fake.urls()), fake.urls())
        self.assertTrue(all(b is None or b.get("model") != "attacker" for (_, b, _) in fake.calls))


if __name__ == "__main__":
    unittest.main()
