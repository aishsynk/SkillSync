"""SkillEdge agentic layer.

A deterministic, evidence-backed agent that reads the unified intelligence
payload, the knowledge base, and manager lifecycle state, then answers manager
questions and produces a daily briefing. No LLM is required; the architecture is
tool-based so a model provider can be plugged into `agent.answer` later without
changing the retrieval tools.

Learning lives in `learning.py` (feedback registry + weight tuning + model
versions) and forms the closed loop: manager decisions feed labeled examples,
which tune scoring weights with versioned evaluation.
"""
