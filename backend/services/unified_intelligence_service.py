"""Unified intelligence orchestration helpers."""

from services.cache_service import cache_path, cache_fresh, read_cache, write_cache


def load_cached_payload(email):
    path = cache_path(email)
    if cache_fresh(path):
        return read_cache(path), True
    return None, False


def persist_payload(email, data):
    path = cache_path(email)
    write_cache(path, data)
    return path
