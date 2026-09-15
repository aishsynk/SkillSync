"""Model providers for Communication Intelligence.

The one boundary between the communication pipeline and any language model.
Every CommunicationPurpose that uses a model asks this module for text; none
of them know or care which provider produced it.

Order comes from COMMUNICATION_PROVIDER_ORDER (comma-separated):
  ollama | azure | openai | deterministic
Default (local/dev): ollama,azure,openai,deterministic
Production examples: azure,openai,deterministic  — or just: deterministic
Providers are tried in order; "deterministic" ends the model search and the
caller uses its deterministic generator. Unknown names are ignored.

Latency: these calls serve interactive screens (Today's Morning Note), so the
Ollama timeout defaults to 10s and is hard-capped at 20s, and Ollama is asked
to keep the model loaded (keep_alive, default 15m) so a cold load does not
recur. A timeout moves straight on to the next provider.

Security: every endpoint comes from server environment variables only. No
function here accepts a URL, host or model from a request, so a client cannot
turn a provider into an SSRF proxy. OLLAMA_BASE_URL is further restricted to
http(s)://host[:port] with no credentials, path, query or fragment.
"""

from __future__ import annotations

import json
import logging
import os
import contextvars
import socket
import time
import urllib.error
import urllib.parse
import urllib.request
from dataclasses import dataclass, field
from typing import Callable, Dict, List, Optional

log = logging.getLogger("skilledge.communication.providers")

OLLAMA = "OLLAMA"
AZURE_OPENAI = "AZURE_OPENAI"
OPENAI = "OPENAI"
DETERMINISTIC = "DETERMINISTIC"

DEFAULT_PROVIDER_ORDER = ("ollama", "azure", "openai", "deterministic")
_KNOWN_PROVIDERS = {"ollama", "azure", "openai", "deterministic"}

_DEFAULT_OLLAMA_TIMEOUT = 10.0
_MAX_OLLAMA_TIMEOUT = 20.0          # hard cap for interactive Communication Intelligence
_DEFAULT_KEEP_ALIVE = "15m"
_CLOUD_TIMEOUT = 8.0
_TAGS_TTL_SECONDS = 60.0
INTERACTIVE_BUDGET_MS = int(_MAX_OLLAMA_TIMEOUT * 1000)   # whole-request cap for UI calls

# Absolute monotonic deadline for the current generate() call, if any.
_deadline: contextvars.ContextVar = contextvars.ContextVar("provider_deadline", default=None)


def _within_budget(timeout: float) -> float:
    """Clamp a provider timeout to what is left of the request budget."""
    deadline = _deadline.get()
    if deadline is None:
        return timeout
    return max(0.05, min(timeout, deadline - time.monotonic()))

_EMBEDDING_MARKERS = ("embed", "bge", "nomic", "clip", "whisper", "rerank", "minilm")
_CODER_MARKERS = ("coder", "code", "starcoder", "codellama", "codegemma", "deepseek-coder", "sqlcoder")
_INSTRUCT_MARKERS = ("instruct", "chat", "-it", ":it", "it-")
_GENERAL_FAMILIES = ("llama", "gemma", "qwen", "mistral", "phi", "granite", "olmo", "smollm")


@dataclass
class ProviderResult:
    text: str
    provider: str
    model: str = ""


@dataclass
class ProviderOutcome:
    """What one generate() call produced, including why it produced nothing."""
    result: Optional[ProviderResult] = None
    elapsed_ms: int = 0
    timeout_reason: str = ""                      # e.g. "OLLAMA timed out after 10.0s"
    tried: List[str] = field(default_factory=list)

    @property
    def timed_out(self) -> bool:
        return bool(self.timeout_reason)


@dataclass
class Provenance:
    """Diagnostic record of how a message was produced. Not for manager UI."""
    provider: str = DETERMINISTIC
    model: str = ""
    fallback_used: bool = True
    attempts: int = 0
    elapsed_ms: int = 0
    timeout_reason: str = ""

    def as_dict(self) -> dict:
        d = {"provider": self.provider, "model": self.model, "fallback_used": self.fallback_used,
             "attempts": self.attempts, "elapsed_ms": self.elapsed_ms}
        if self.timeout_reason:
            d["timeout_reason"] = self.timeout_reason
        return d

    @property
    def generation_mode(self) -> str:
        return "DETERMINISTIC_GENERATOR" if self.provider == DETERMINISTIC else f"LLM_{self.provider}"


class ProviderTimeout(Exception):
    pass


# ── configuration ───────────────────────────────────────────────────────────

def provider_order() -> List[str]:
    """Parsed COMMUNICATION_PROVIDER_ORDER; default when unset or nothing valid."""
    raw = os.getenv("COMMUNICATION_PROVIDER_ORDER")
    if raw is None or not raw.strip():
        return list(DEFAULT_PROVIDER_ORDER)
    seen: List[str] = []
    for token in raw.split(","):
        name = token.strip().lower()
        if name in _KNOWN_PROVIDERS and name not in seen:
            seen.append(name)
        elif name:
            log.warning("COMMUNICATION_PROVIDER_ORDER: ignoring unknown provider %r", name)
    return seen or list(DEFAULT_PROVIDER_ORDER)


# ── HTTP seam (patched in tests) ────────────────────────────────────────────

def _http_json(url: str, body: Optional[dict], headers: dict, timeout: float) -> dict:
    data = json.dumps(body).encode("utf-8") if body is not None else None
    req = urllib.request.Request(url, data=data, headers=headers, method="POST" if body is not None else "GET")
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            return json.loads(resp.read().decode("utf-8"))
    except (socket.timeout, TimeoutError) as exc:
        raise ProviderTimeout(str(exc)) from exc
    except urllib.error.URLError as exc:
        if isinstance(exc.reason, (socket.timeout, TimeoutError)):
            raise ProviderTimeout(str(exc.reason)) from exc
        raise


# ── Ollama ──────────────────────────────────────────────────────────────────

def _ollama_base_url() -> Optional[str]:
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


def _ollama_keep_alive() -> str:
    value = (os.getenv("OLLAMA_KEEP_ALIVE") or _DEFAULT_KEEP_ALIVE).strip()
    return value if value.replace("m", "").replace("h", "").replace("s", "").lstrip("-").isdigit() else _DEFAULT_KEEP_ALIVE


_tags_cache: dict = {"base": None, "at": 0.0, "models": []}


def _installed_ollama_models(base: str, timeout: float) -> List[str]:
    now = time.monotonic()
    if _tags_cache["base"] == base and now - _tags_cache["at"] < _TAGS_TTL_SECONDS:
        return list(_tags_cache["models"])
    data = _http_json(f"{base}/api/tags", None, {"Accept": "application/json"}, min(timeout, 3.0))
    names = [str(m.get("name") or m.get("model") or "") for m in (data.get("models") or []) if isinstance(m, dict)]
    names = [n for n in names if n]
    _tags_cache.update(base=base, at=now, models=names)
    return names


def reset_ollama_cache() -> None:
    _tags_cache.update(base=None, at=0.0, models=[])


def model_rank(name: str) -> Optional[int]:
    """Lower is better for writing. None = never usable for text (embeddings)."""
    n = name.lower()
    if any(m in n for m in _EMBEDDING_MARKERS):
        return None
    if any(m in n for m in _CODER_MARKERS):
        return 3                                    # last-resort text candidate
    if any(m in n for m in _INSTRUCT_MARKERS):
        return 0
    if any(n.startswith(f) for f in _GENERAL_FAMILIES):
        return 1                                    # general chat families ship instruct-tuned by default
    return 2


def _pick_ollama_model(installed: List[str]) -> Optional[str]:
    """Explicit OLLAMA_MODEL if installed; otherwise best-ranked installed model."""
    wanted = (os.getenv("OLLAMA_MODEL") or "").strip()
    if wanted:
        for name in installed:
            if name == wanted or (":" not in wanted and name.split(":")[0] == wanted):
                return name
        log.info("OLLAMA_MODEL %s not installed; discovering an installed model", wanted)
    ranked = [(model_rank(n), i, n) for i, n in enumerate(installed)]
    usable = sorted((r, i, n) for r, i, n in ranked if r is not None)
    return usable[0][2] if usable else None


def _ollama(system: str, user: str, temperature: float, max_tokens: int) -> Optional[ProviderResult]:
    base = _ollama_base_url()
    if not base:
        return None
    timeout = _within_budget(_ollama_timeout())
    model = _pick_ollama_model(_installed_ollama_models(base, timeout))
    if not model:
        log.info("Ollama reachable but no usable model installed; skipping provider")
        return None
    data = _http_json(
        f"{base}/api/chat",
        {
            "model": model,
            "stream": False,
            "keep_alive": _ollama_keep_alive(),
            "messages": [{"role": "system", "content": system}, {"role": "user", "content": user}],
            "options": {"temperature": temperature, "num_predict": max_tokens},
        },
        {"Content-Type": "application/json"},
        timeout,
    )
    text = str(((data.get("message") or {}).get("content")) or "").strip()
    return ProviderResult(text, OLLAMA, model) if text else None


# ── Azure OpenAI / OpenAI ───────────────────────────────────────────────────

def _chat_completions(url: str, headers: dict, provider: str, model: str,
                      system: str, user: str, temperature: float, max_tokens: int) -> Optional[ProviderResult]:
    data = _http_json(url, {
        "model": model,
        "messages": [{"role": "system", "content": system}, {"role": "user", "content": user}],
        "temperature": temperature,
        "max_tokens": max_tokens,
    }, headers, _within_budget(_CLOUD_TIMEOUT))
    text = str(data["choices"][0]["message"]["content"] or "").strip()
    return ProviderResult(text, provider, model) if text else None


def _azure(system: str, user: str, temperature: float, max_tokens: int) -> Optional[ProviderResult]:
    endpoint = os.getenv("AZURE_OPENAI_ENDPOINT")
    key = os.getenv("AZURE_OPENAI_KEY") or os.getenv("AZURE_OPENAI_API_KEY")
    if not (endpoint and key):
        return None
    deployment = os.getenv("AZURE_OPENAI_DEPLOYMENT", "gpt-4o")
    api_version = os.getenv("AZURE_OPENAI_API_VERSION", "2024-02-15-preview")
    url = f"{endpoint.rstrip('/')}/openai/deployments/{deployment}/chat/completions?api-version={api_version}"
    return _chat_completions(url, {"Content-Type": "application/json", "api-key": key},
                             AZURE_OPENAI, deployment, system, user, temperature, max_tokens)


def _openai(system: str, user: str, temperature: float, max_tokens: int) -> Optional[ProviderResult]:
    key = os.getenv("OPENAI_API_KEY")
    if not key:
        return None
    return _chat_completions("https://api.openai.com/v1/chat/completions",
                             {"Content-Type": "application/json", "Authorization": f"Bearer {key}"},
                             OPENAI, os.getenv("OPENAI_MODEL", "gpt-4o-mini"), system, user, temperature, max_tokens)


_PROVIDERS: Dict[str, Callable[[str, str, float, int], Optional[ProviderResult]]] = {
    "ollama": _ollama, "azure": _azure, "openai": _openai,
}


def generate(system: str, user: str, temperature: float = 0.2, max_tokens: int = 400,
             budget_ms: Optional[int] = None) -> ProviderOutcome:
    """Try providers in configured order; stop at the first text or at 'deterministic'.

    budget_ms caps the whole call across providers (every provider timeout is
    clamped to what remains); when it runs out, the chain stops as a timeout.
    """
    started = time.monotonic()
    outcome = ProviderOutcome()
    token = _deadline.set(started + budget_ms / 1000.0) if budget_ms is not None else None
    try:
        _run_chain(system, user, temperature, max_tokens, outcome)
    finally:
        if token is not None:
            _deadline.reset(token)
    outcome.elapsed_ms = int((time.monotonic() - started) * 1000)
    return outcome


def _run_chain(system: str, user: str, temperature: float, max_tokens: int, outcome: ProviderOutcome) -> None:
    for name in provider_order():
        if name == "deterministic":
            break
        deadline = _deadline.get()
        if deadline is not None and deadline - time.monotonic() <= 0.05:
            outcome.timeout_reason = outcome.timeout_reason or "request budget exhausted"
            break
        outcome.tried.append(name)
        t0 = time.monotonic()
        try:
            result = _PROVIDERS[name](system, user, temperature, max_tokens)
        except ProviderTimeout:
            reason = f"{name.upper()} timed out after {int((time.monotonic() - t0) * 1000)}ms"
            log.info("communication provider %s", reason)
            outcome.timeout_reason = reason
            continue
        except Exception as exc:  # unreachable, auth, malformed body
            log.info("communication provider %s unavailable (%s); falling through", name, type(exc).__name__)
            continue
        if result and result.text.strip():
            outcome.result = result
            log.info("communication provider=%s model=%s elapsed_ms=%d chars=%d",
                     result.provider, result.model, int((time.monotonic() - t0) * 1000), len(result.text))
            break


def generate_text(system: str, user: str, temperature: float = 0.2, max_tokens: int = 400) -> Optional[ProviderResult]:
    """Convenience wrapper: just the result, or None."""
    return generate(system, user, temperature, max_tokens).result
