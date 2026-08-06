"""Course graph scaffolding for SkillEdge."""


def normalize_course_name(value):
    return " ".join(str(value or "").strip().split())


def build_course_node(course):
    return {
        "course_name": normalize_course_name(course.get("course_name") or course.get("Course") or ""),
        "vendor": course.get("vendor") or course.get("vendor_name") or "",
        "topics": course.get("topics") or [],
        "technologies": course.get("technologies") or [],
        "domains": course.get("domains") or [],
        "difficulty": course.get("difficulty") or None,
        "certification": course.get("certification") or None,
    }


def get_course_topics(course):
    node = build_course_node(course)
    return node.get("topics", [])
