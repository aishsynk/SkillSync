# AGENTS.md — AI Agent Guidelines & Operating Procedures

## Core Operating Principles

1. **Single Source of Truth**: Always start by reading `AI/PROGRESS.md` and treat it as the single source of truth for the project.
2. **Context & Decisions**:
   - Review `AI/CONTEXT.md` for durable, reusable project knowledge and architecture facts.
   - Review `AI/DECISIONS.md` for key architectural, design, business, or process decisions whenever additional context is required.
   - If any required `AI/*.md` file does not exist, create it based on the current project state and continue working.

## Session Startup Protocol

Before making any changes, provide a brief summary covering:
- Current project status
- What was completed previously
- What is currently in progress
- Recommended next actions

When reviewing `AI/PROGRESS.md`, identify and display:
- **Last model used**
- **Last tool/agent used** (Claude, GPT, Gemini, Copilot, Cursor, Windsurf, OpenCode, Antigravity, etc.)
- **Last update date/time**
- **Current project state**
- **Pending actions**

## Cloud & Azure Operations

- **Access Verification**: For Azure-related work, do not assume Azure access is unavailable. First verify access by running appropriate Azure authentication and account validation commands (e.g. `az account show`) and proceed based on the result. Follow Azure guidance documented in `AI/CONTEXT.md` as the authoritative reference.
- **Resource Stewardship**: Do not create unnecessary Azure resources, environments, databases, storage accounts, or services unless explicitly required.
- **Security**: Never store secrets, connection strings, passwords, API keys, or credentials in plaintext. Follow existing project security and deployment practices.

## Deployment Workflow

Follow the strict deployment pipeline:
`Local Development` → `Development Environment` → `Validation/Testing` → `Deploy the identical validated package to Production`

## Progress Tracking & Documentation Maintenance

After every significant task, update `AI/PROGRESS.md` with:
- Date and time
- Model used
- Tool/agent used
- Files modified
- Work completed
- Current status
- Known issues or blockers
- Next recommended actions

### Documentation Guidelines
- Store **only durable and reusable project knowledge** in `AI/CONTEXT.md`.
- Store **only important decisions and their rationale** in `AI/DECISIONS.md`.
- Keep all entries concise, structured, implementation-focused, AI-agnostic, and free from conversational history, debugging logs, or unnecessary discussion.

## Session Handover

Before ending your session, append a final handover entry to `AI/PROGRESS.md` so another model can immediately continue work without losing context.

## Future Session Standard Prompt

For future sessions, the standard prompt is:
> "Read and follow AGENTS.md. Review the latest AI/PROGRESS.md entry, provide a brief current-status summary, identify the last model and tool used, and continue with the next recommended actions."
