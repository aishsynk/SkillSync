# SkillEdge Production Release v3.45.1 (Build 129)

- **Release Date**: 2026-08-30
- **Version Code**: `129`
- **Version Name**: `3.45.1`

## Why this release

Production verification of v3.45.0 found that logout removed a signed session
only from process memory. The token could immediately reconstruct itself from
its valid signature, so the server continued accepting it after logout.

## What changed

- Logout now writes a SHA-256 token digest to a process-safe SQLite denylist.
- Every authenticated request checks the denylist before accepting either an
  in-memory or reconstructed signed session.
- Revocations retain the token's natural 30-day expiry and expired entries are
  pruned automatically.
- Raw bearer tokens and credentials are never persisted.
- Android package and signing identity are unchanged; build 129 installs over
  build 128 while preserving user data.

## Validation

- Auth regression coverage includes logout, process-memory restart, and a new
  store instance representing another worker.
- Full backend and Android release gates must pass before publication.
