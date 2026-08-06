"""Technology graph scaffolding for SkillEdge."""

TECHNOLOGY_ADJACENCY = {
    "power bi": ["sql", "dax", "data modeling", "reporting"],
    "python": ["pandas", "numpy", "machine learning", "data science"],
    "alteryx": ["workflow automation", "data prep", "blending", "join"],
    "fabric": ["power bi", "lakehouse", "data engineering"],
}


def normalize_technology_name(value):
    return " ".join(str(value or "").strip().lower().split())


def get_adjacent_technologies(value):
    key = normalize_technology_name(value)
    return TECHNOLOGY_ADJACENCY.get(key, [])
