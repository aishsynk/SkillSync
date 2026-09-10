"""Persistence layer for SkillEdge domains.

Stores are small SQLite-backed repositories shared by the backend routes.
They follow the same tolerances as ActionStore / DevPlanStore: a read-only
filesystem must never break a request.
"""