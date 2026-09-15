"""Model providers for Communication Intelligence.

The one boundary between the communication pipeline and any language model.
Every CommunicationPurpose that uses a model asks this module for text; none
of them know or care which provider produced it.

Resolution order (first one that returns text wins):
  1. OLLAMA        — when OLLAMA_BASE_URL is configured and a model is installed
  2. AZURE_OPENAI  — when AZURE_OPENAI_ENDPOINT + key are configured
  3. OPENAI        — when OPENAI_API_KEY is configured
If none produce text the caller uses its deterministic generator.

Security: every endpoint comes from server environment variables only. No
function here accepts a URL or host from a request, so a client cannot turn
the provider into an SSRF proxy. The Ollama base URL is further restricted to
http(s) and must not carry credentials, a path, query or fragment.
"""

from __future__ import annotations

import json
import logging
import os
import time
import urllib.error
import urllib.parse
import urllib.request
from dataclasses import dataclass
from typing import Callable, List, Optional

log = logging.getLogger("skilledge.communication.providers")

OLLAMA = "OLLAMA"
AZURE_OPENAI = "AZURE_OPENAI"
OPENAI = "OPENAI"
DETERMINISTIC = "DETERMINISTIC"

_DEFAULT_OLLAMA_TIMEOUT = 20.0
_MAX_OLLAMA_TIMEOUT = 60.0
_TAGS_TTL_SECONDS = 60.0

# Families/names that are not chat models and must never be picked for text.
_NON_CHAT_MARKERS = ("embed", "bge", "nomic", "clip", "whisper", "rerank")


@dataclass
class ProviderResult:
    text: str
    provider: str
    model: str = ""


@dataclass
class Provenance:
    """Diagnostic record of how a message was produced. Not for manager UI."""
    provider: str = DETERMINISTIC
    model: str = ""
    fallback_used: bool = True
    attempts: int = 0

    def as_dict(self) -> dict:
        return {"provider": self.provider, "model": self.model,
                "fallback_used": self.fallback_used, "attempts": self.attempts}

    @property
    def generation_mode(self) -> str:
        return "DETERMINISTIC_GENERATOR" if self.provider == DETERMINISTIC else f"LLM_{self.provider}"


# ── HTTP seam (patched in tests) ────────────────────────────────────────────

def _http_json(url: str, body: Optional[dict], headers: dict, timeout: float) -> dict:
    data = json.dumps(body).encode("utf-8") if body is not None else None
    req = urllib.request.Request(url, data=data, headers=headers, method="POST" if body is not None else "GET")
    with urllib.request.urlopen(req, timeout=timeout) as resp:
        return json.loads(resp.read().decode("utf-8"))


# ── Ollama ──────────────────────────────────────────────────────────────────

def _ollama_base_url() -> Optional[str]:
    """Validated base URL from server config, or None when unset/unsafe."""
    raw = (os.getenv("OLLAMA_BASE_URL") or "").strip()
    if not raw:
        return None
    parsed = urllib.parse.urlparse(raw)
    if parsed.scheme not in ("http", "https") or not parsed.hostname:
        log.warning("OLLAMA_BASE_URL ignored: unsupported scheme or host")
        return None
    if parsed.username or parsed.password or parsed.query or parsed.fragment or parsed.path not in ("", "/"):
        log.warning("OLLAMA_BASE_URL ignored: must be scheme://host[:port] only")
        return None
    return f"{parsed.scheme}://{parsed.netloc}"


def _ollama_timeout() -> float:
    try:
        value = float(os.getenv("OLLAMA_TIMEOUT_SECONDS") or _DEFAULT_OLLAMA_TIMEOUT)
    except ValueError:
        value = _DEFAULT_OLLAMA_TIMEOUT
    return max(1.0, min(value, _MAX_OLLAMA_TIMEOUT))


_tags_cache: dict = {"base": None, "at": 0.0, "models": []}


def _installed_ollama_models(base: str, timeout: float) -> List[str]:
    now = time.monotonic()
    if _tags_cache["base"] == base and now - _tags_cache["at"] < _TAGS_TTL_SECONDS:
        return list(_tags_cache["models"])
    data = _http_json(f"{base}/api/tags", None, {"Accept": "application/json"}, min(timeout, 5.0))
    names = [str(m.get("name") or m.get("model") or "") for m in (data.get("models") or []) if isinstance(m, dict)]
    names = [n for n in names if n]
    _tags_cache.update(base=base, at=now, models=names)
    return names


def reset_ollama_cache() -> None:
    _tags_cache.update(base=None, at=0.0, models=[])


def _pick_ollama_model(installed: List[str]) -> Optional[str]:
    """Configured model if installed; otherwise the first installed chat-capable model."""
    wanted = (os.getenv("OLLAMA_MODEL") or "").strip()
    if wanted:
        for name in installed:
            # "qwen2.5" matches "qwen2.5:latest"; a tagged name must match exactly.
            if name == wanted or (":" not in wanted and name.split(":")[0] == wanted):
                return name
        log.info("OLLAMA_MODEL %s not installed; discovering an installed model", wanted)
    usable = [n for n in installed if not any(mark in n.lower() for mark in _NON_CHAT_MARKERS)]
    return usable[0] if usable else None


def _ollama_generate(system: str, user: str, temperature: float, max_tokens: int) -> Optional[ProviderResult]:
    base = _ollama_base_url()
    if not base:
        return None
    timeout = _ollama_timeout()
    try:
        model = _pick_ollama_model(_installed_ollama_models(base, timeout))
        if not model:
            log.info("Ollama reachable but no usable model installed; skipping provider")
            return None
        data = _http_json(
            f"{base}/api/chat",
            {
                "model": model,
                "stream": False,
                "messages": [{"role": "system", "content": system}, {"role": "user", "content": user}],
                "options": {"temperature": temperature, "num_predict": max_tokens},
            },
            {"Content-Type": "application/json"},
            timeout,
        )
        text = str(((data.get("message") or {}).get("content")) or "").strip()
        if not text:
            log.info("Ollama model %s returned no content", model)
            return None
        log.info("communication provider=OLLAMA model=%s chars=%d", model, len(text))
        return ProviderResult(text, OLLAMA, model)
    except (urllib.error.URLError, TimeoutError, OSError, ValueError, AttributeError, TypeError) as exc:
        log.info("Ollama unavailable (%s); falling through", type(exc).__name__)
        return None


# ── OpenAI / Azure OpenAI (existing configuration, moved here) ──────────────

def _openai_generate(system: str, user: str, temperature: float, max_tokens: int) -> Optional[ProviderResult]:
    api_key = os.getenv("OPENAI_API_KEY")
    azure_endpoint = os.getenv("AZURE_OPENAI_ENDPOINT")
    azure_key = os.getenv("AZURE_OPENAI_KEY") or os.getenv("AZURE_OPENAI_API_KEY")
    if not api_key and not (azure_endpoint and azure_key):
        return None
    if azure_endpoint and azure_key:
        deployment = os.getenv("AZURE_OPENAI_DEPLOYMENT", "gpt-4o")
        api_version = os.getenv("AZURE_OPENAI_API_VERSION", "2024-02-15-preview")
        url = f"{azure_endpoint.rstrip('/')}/openai/deployments/{deployment}/chat/completions?api-version={api_version}"
        headers = {"Content-Type": "application/json", "api-key": azure_key}
        provider, model = AZURE_OPENAI, deployment
    else:
        url = "https://api.openai.com/v1/chat/completions"
        headers = {"Content-Type": "application/json", "Authorization": f"Bearer {api_key}"}
        provider, model = OPENAI, os.getenv("OPENAI_MODEL", "gpt-4o-mini")
    try:
        data = _http_json(url, {
            "model": model,
            "messages": [{"role": "system", "content": system}, {"role": "user", "content": user}],
            "temperature": temperature,
            "max_tokens": max_tokens,
        }, headers, 8.0)
        text = str(data["choices"][0]["message"]["content"] or "").strip()
        if not text:
            return None
        log.info("communication provider=%s model=%s chars=%d", provider, model, len(text))
        return ProviderResult(text, provider, model)
    except Exception as exc:  # network, auth, malformed body
        log.info("%s unavailable (%s); falling through", provider, type(exc).__name__)
        return None


_PROVIDERS: List[Callable[[str, str, float, int], Optional[ProviderResult]]] = [_ollama_generate, _openai_generate]


def generate_text(system: str, user: str, temperature: float = 0.2, max_tokens: int = 400) -> Optional[ProviderResult]:
    """First provider that returns text, or None when no model is available."""
    for provider in _PROVIDERS:
        result = provider(system, user, temperature, max_tokens)
        if result and result.text.strip():
            return result
    return None
