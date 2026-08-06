# Architectural and Process Decisions

## Architecture
- **Middle-tier Backend**: Decision to use a local Python backend as a proxy and intelligence layer rather than having the frontend call external APIs directly. Rationale: Allows for secure credential storage, robust data processing (pandas/ML), background caching, and seamless integration of AI Agents (Agentic module).
- **Frontend Stack**: Decision to use Vanilla JS/HTML with a standard template (Sean Theme). Rationale: Keeps the frontend lightweight and easy to maintain without complex build steps for a locally served tool.

## Processes
- **AI Memory Pattern**: Establish `AI/PROGRESS.md` as the single source of truth for task tracking. Use `AI/CONTEXT.md` for durable knowledge and `AI/DECISIONS.md` for architectural choices. Rationale: Ensures seamless handover and context retention across sessions.
