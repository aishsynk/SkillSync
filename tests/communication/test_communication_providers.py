"""Model provider boundary — Ollama first, then Azure/OpenAI, then deterministic.

All HTTP is mocked at providers._http_json; no real Ollama is required.
"""

import os
import socket
import tempfile
import unittest
import urllib.error
from unittest import mock

import backend
from repositories.communication_store import CommunicationStore
from services.communication import providers
from services.communication.service import CommunicationService

MANAGER = "comm@koenig-solutions.com"
BASE = "http://127.0.0.1:11434"
TAGS = {"models": [{"name": "nomic-embed-text:latest"}, {"name": "qwen2.5:7b-instruct"}, {"name": "llama3.2:3b"}]}
_ENV_KEYS = ("OLLAMA_BASE_URL", "OLLAMA_MODEL", "OLLAMA_TIMEOUT_SECONDS", "OPENAI_API_KEY", "OPENAI_MODEL",
             "AZURE_OPENAI_ENDPOINT", "AZURE_OPENAI_KEY", "AZURE_OPENAI_API_KEY")


class FakeOllama:
    """Records calls and answers /api/tags and /api/chat."""

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
            return self.chat if self.chat is not None else {"message": {"role": "assistant", "content": "Hello team,\n\nAll good.\n\n*Thanks*"}}
        if "openai" in url:
            return {"choices": [{"message": {"content": "Hello team,\n\nFrom OpenAI.\n\n*Thanks*"}}]}
        raise AssertionError(f"unexpected URL {url}")

    def chat_bodies(self):
        return [b for (u, b, _) in self.calls if u.endswith("/api/chat")]


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


class OllamaProviderTest(ProviderTestBase):
    def test_configured_and_reachable_ollama_is_selected(self):
        os.environ["OLLAMA_BASE_URL"] = BASE
        fake = FakeOllama()
        with mock.patch.object(providers, "_http_json", fake):
            r = providers.generate_text("sys", "user", 0.3, 100)
        self.assertEqual("OLLAMA", r.provider)
        body = fake.chat_bodies()[0]
        self.assertEqual(False, body["stream"])
        self.assertEqual([{"role": "system", "content": "sys"}, {"role": "user", "content": "user"}], body["messages"])
        self.assertEqual({"temperature": 0.3, "num_predict": 100}, body["options"])

    def test_configured_model_is_used_when_installed(self):
        os.environ.update(OLLAMA_BASE_URL=BASE, OLLAMA_MODEL="llama3.2:3b")
        fake = FakeOllama()
        with mock.patch.object(providers, "_http_json", fake):
            r = providers.generate_text("s", "u")
        self.assertEqual("llama3.2:3b", r.model)
        self.assertEqual("llama3.2:3b", fake.chat_bodies()[0]["model"])

    def test_missing_configured_model_falls_back_to_an_installed_chat_model(self):
        os.environ.update(OLLAMA_BASE_URL=BASE, OLLAMA_MODEL="mistral:7b")
        fake = FakeOllama()
        with mock.patch.object(providers, "_http_json", fake):
            r = providers.generate_text("s", "u")
        # Embedding models are never chosen for chat.
        self.assertEqual("qwen2.5:7b-instruct", r.model)

    def test_no_installed_models_skips_the_provider(self):
        os.environ["OLLAMA_BASE_URL"] = BASE
        fake = FakeOllama(tags={"models": []})
        with mock.patch.object(providers, "_http_json", fake):
            self.assertIsNone(providers.generate_text("s", "u"))
        self.assertEqual([], fake.chat_bodies())

    def test_timeout_falls_through_to_the_next_provider(self):
        os.environ.update(OLLAMA_BASE_URL=BASE, OPENAI_API_KEY="k")
        fake = FakeOllama(chat_error=socket.timeout("timed out"))
        with mock.patch.object(providers, "_http_json", fake):
            r = providers.generate_text("s", "u")
        self.assertEqual("OPENAI", r.provider)

    def test_unreachable_ollama_with_nothing_else_returns_none(self):
        os.environ["OLLAMA_BASE_URL"] = BASE
        fake = FakeOllama(tags_error=urllib.error.URLError("refused"))
        with mock.patch.object(providers, "_http_json", fake):
            self.assertIsNone(providers.generate_text("s", "u"))

    def test_malformed_ollama_response_falls_through(self):
        os.environ["OLLAMA_BASE_URL"] = BASE
        for bad in ({"message": None}, {"unexpected": 1}, {"message": {"content": "   "}}):
            providers.reset_ollama_cache()
            with mock.patch.object(providers, "_http_json", FakeOllama(chat=bad)):
                self.assertIsNone(providers.generate_text("s", "u"), bad)

    def test_timeout_is_bounded(self):
        os.environ.update(OLLAMA_BASE_URL=BASE, OLLAMA_TIMEOUT_SECONDS="9999")
        fake = FakeOllama()
        with mock.patch.object(providers, "_http_json", fake):
            providers.generate_text("s", "u")
        self.assertTrue(all(t <= 60.0 for (_, _, t) in fake.calls))

    def test_unsafe_base_urls_are_rejected(self):
        for bad in ("file:///etc/passwd", "http://user:pw@host:11434", "http://host:11434/api?x=1", "gopher://host", "not a url"):
            os.environ["OLLAMA_BASE_URL"] = bad
            self.assertIsNone(providers._ollama_base_url(), bad)
        os.environ["OLLAMA_BASE_URL"] = "http://10.0.0.5:11434/"
        self.assertEqual("http://10.0.0.5:11434", providers._ollama_base_url())

    def test_unconfigured_ollama_makes_no_network_call(self):
        fake = FakeOllama()
        with mock.patch.object(providers, "_http_json", fake):
            self.assertIsNone(providers.generate_text("s", "u"))
        self.assertEqual([], fake.calls)


class CommunicationServiceProviderTest(ProviderTestBase):
    def setUp(self):
        super().setUp()
        self.temp = tempfile.TemporaryDirectory()
        self.svc = CommunicationService(CommunicationStore(os.path.join(self.temp.name, "c.sqlite3")))

    def tearDown(self):
        self.temp.cleanup()
        super().tearDown()

    def test_general_purposes_use_ollama_through_the_same_pipeline(self):
        os.environ["OLLAMA_BASE_URL"] = BASE
        fake = FakeOllama()
        with mock.patch.object(providers, "_http_json", fake):
            r = self.svc.generate(MANAGER, {"recipient": {"type": "TEAM"}, "myMessage": "please confirm availability for next week", "channel": "MS_TEAMS_OR_VIBER"})
        self.assertEqual("OLLAMA", r.provenance["provider"])
        self.assertFalse(r.provenance["fallback_used"])
        self.assertEqual("LLM_OLLAMA", r.generation_mode)

    def test_no_provider_uses_the_deterministic_generator_with_honest_provenance(self):
        r = self.svc.generate(MANAGER, {"recipient": {"type": "TEAM"}, "myMessage": "please confirm availability for next week"})
        self.assertEqual({"provider": "DETERMINISTIC", "model": "", "fallback_used": True, "attempts": 0}, r.provenance)

    def test_morning_greeting_uses_ollama_and_sends_recent_greetings_in_the_prompt(self):
        os.environ["OLLAMA_BASE_URL"] = BASE
        good = "Midweek already, everyone.\n\n*Halfway through* - a good day to help someone past a hurdle. _Have a good Wednesday._"
        fake = FakeOllama(chat={"message": {"content": good}})
        with mock.patch.object(providers, "_http_json", fake):
            r = self.svc.generate(MANAGER, {"purpose": "MORNING_TEAM_GREETING", "localWeekday": "WEDNESDAY",
                                            "recentGreetings": ["Hi all, halfway there.\n\nOld note."]})
        self.assertEqual(good, r.text)
        self.assertEqual("OLLAMA", r.provenance["provider"])
        user_prompt = fake.chat_bodies()[0]["messages"][1]["content"]
        self.assertIn("Hi all, halfway there.", user_prompt)
        self.assertIn("WEDNESDAY", user_prompt)
        self.assertIn("WEDNESDAY - MIDWEEK RESET", fake.chat_bodies()[0]["messages"][0]["content"])

    def test_client_cannot_supply_an_ollama_url(self):
        os.environ["OLLAMA_BASE_URL"] = BASE
        fake = FakeOllama()
        backend._sessions["comm-session"] = {"email": MANAGER, "role": "manager"}
        prev = backend._communication_service
        backend._communication_service = self.svc
        try:
            with mock.patch.object(providers, "_http_json", fake):
                res = backend.app.test_client().post(
                    "/api/v2/communication/generate?ollama_base_url=http://evil.example",
                    json={"manager": MANAGER, "myMessage": "status please", "recipient": {"type": "TEAM"},
                          "ollama_base_url": "http://169.254.169.254", "OLLAMA_BASE_URL": "http://evil.example",
                          "baseUrl": "http://evil.example", "model": "attacker"},
                    headers={"Authorization": "Bearer comm-session"},
                )
        finally:
            backend._communication_service = prev
            backend._sessions.pop("comm-session", None)
        self.assertEqual(200, res.status_code)
        urls = [u for (u, _, _) in fake.calls]
        self.assertTrue(urls)
        self.assertTrue(all(u.startswith(BASE) for u in urls), urls)
        self.assertTrue(all(b is None or b.get("model") != "attacker" for (_, b, _) in fake.calls))


if __name__ == "__main__":
    unittest.main()
