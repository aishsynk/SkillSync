"""Manager scope helpers for SkillEdge."""


def manager_email_from_query(value):
    return (value or "").strip()


def manager_scope_key(email):
    return (email or "default").strip().lower()

