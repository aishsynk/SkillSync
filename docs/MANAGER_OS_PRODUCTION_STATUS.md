# Manager OS Production Status

This document records the current production surface after the Manager OS hardening passes.

## Current Runtime Model

- Runtime data is API-first through live RMS calls.
- The browser uses `/data/unified-manager-intelligence` for active intelligence pages.
- `/rms/<api>` remains as an authenticated compatibility relay for legacy client helpers.
- `/proxy` has been removed.
- No page should use Excel, CSV, notebook output, or manual imports at runtime.

## Active Pages

- `login.html`
- `index.html`
- `team.html`
- `trainer-detail.html`
- `allocation-desk.html`
- `custom-course-match.html`
- `actions.html`
- `data-health.html`
- `settings.html`
- `certifications.html`
- `risk-takers.html`
- `coming-soon.html`

## Security

- Login creates an HttpOnly `skilledge_session` cookie.
- Server derives manager scope from the session email.
- Unified endpoint rejects mismatched email queries.
- Protected routes:
  - `/data/unified-manager-intelligence`
  - `/rms/<api>`
- Sessions expire after `SKILLEDGE_SESSION_TTL_SECONDS`, defaulting to 8 hours.
- RMS credentials can be supplied through environment variables in `api/config.py`; source fallbacks remain for local compatibility.

## Health And Verification

- `/healthz` returns process health, active session count, and session TTL.
- `tests/smoke_test.py` verifies:
  - health endpoint
  - protected unauthenticated access
  - login
  - authenticated unified payload
  - manager email scoping
  - payload contract
  - cache behavior

## Intelligence Blocks Present

- Trainer operations
- Course allocation
- Trainer timeline
- Manager actions
- Availability engine
- Data health
- Capability signals
- Certification intelligence
- Growth intelligence
- Delivery intelligence
- Allocation intelligence
- Organization and succession intelligence
- Executive intelligence

## Remaining Risks

- Some RMS APIs remain unstable or partial; these are surfaced through `data_health_df`.
- Exam pass/fail status is not available through the current verified API surface.
- Production should set RMS credential environment variables and remove source fallback secrets in a later credential rotation pass.
- The AI copilot layer is not implemented as a runtime feature yet; it should only answer from the unified payload when added.
