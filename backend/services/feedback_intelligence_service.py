"""Canonical feedback facts and conservative evidence-backed summaries."""

from __future__ import annotations

from typing import Any, Dict, Iterable, List


THEMES = {
    "technical_depth": ("technical", "knowledge", "expert", "concept", "depth", "accuracy"),
    "explanation": ("explain", "clarity", "understand", "clear"),
    "pace": ("pace", "fast", "slow", "speed"),
    "labs": ("lab", "exercise", "hands-on", "demo", "practical"),
    "engagement": ("engage", "interactive", "question", "participation"),
    "communication": ("communication", "language", "voice", "accent"),
    "setup": ("setup", "environment", "machine", "access"),
    "professionalism": ("professional", "punctual", "behaviour", "behavior"),
}


def _text(value: Any) -> str:
    return " ".join(str(value or "").strip().split())


def _email(value: Any) -> str:
    return _text(value).lower()


def _theme(question: str, answer: str) -> str:
    text = f"{question} {answer}".lower()
    for theme, terms in THEMES.items():
        if any(term in text for term in terms):
            return theme
    return "general"


def _sentiment(answer: str) -> str:
    text = answer.lower()
    negative = ("poor", "bad", "not clear", "too fast", "too slow", "issue", "problem", "difficult", "improve")
    positive = ("excellent", "good", "great", "clear", "helpful", "satisfied", "strong")
    if any(term in text for term in negative):
        return "negative"
    if any(term in text for term in positive):
        return "positive"
    return "unknown"


def build_feedback_facts(fetched_trainers: Iterable[Dict[str, Any]]) -> List[Dict[str, Any]]:
    facts = []
    seen = set()
    for fetched in fetched_trainers or []:
        trainer = fetched.get("trainer") or {}
        fallback_email = _email(fetched.get("email") or trainer.get("OffEmail"))
        for index, row in enumerate(fetched.get("feedback_details") or []):
            if not isinstance(row, dict):
                continue
            answer = _text(row.get("TextAnswer") or row.get("MCQAnswer"))
            question = _text(row.get("Question"))
            if not answer:
                continue
            assignment_id = _text(row.get("AssignmentId") or row.get("AssignmentID"))
            email = _email(row.get("TrainerEmail") or fallback_email)
            key = (email, assignment_id, _text(row.get("FeedBackDate")), question, answer)
            if key in seen:
                continue
            seen.add(key)
            facts.append({
                "feedback_id": f"{assignment_id or email}:{index}",
                "trainer_email": email or None,
                "trainer_name": _text(row.get("TrainerName") or trainer.get("TrainerName")) or "Unknown",
                "assignment_id": assignment_id or None,
                "scid": row.get("SCID"),
                "feedback_date": row.get("FeedBackDate"),
                "question": question or None,
                "answer": answer,
                "answer_type": "text" if _text(row.get("TextAnswer")) else "mcq",
                "theme": _theme(question, answer),
                "sentiment": _sentiment(answer),
                "source_api": "Get Trainer Feedback Details",
            })
    return facts


def build_feedback_summaries(facts: Iterable[Dict[str, Any]], trainer_rows: Iterable[Dict[str, Any]]) -> List[Dict[str, Any]]:
    by_email: Dict[str, List[Dict[str, Any]]] = {}
    for fact in facts or []:
        by_email.setdefault(_email(fact.get("trainer_email")), []).append(fact)
    summaries = []
    for trainer in trainer_rows or []:
        email = _email(trainer.get("official_email") or trainer.get("trainer_email"))
        rows = by_email.get(email, [])
        themes: Dict[str, Dict[str, int]] = {}
        for row in rows:
            bucket = themes.setdefault(row.get("theme") or "general", {"positive": 0, "negative": 0, "unknown": 0})
            bucket[row.get("sentiment") or "unknown"] += 1
        strengths = sorted((theme for theme, counts in themes.items() if counts["positive"] > counts["negative"]))
        improvements = sorted((theme for theme, counts in themes.items() if counts["negative"] > 0))
        summaries.append({
            "trainer_email": email or None,
            "trainer_name": trainer.get("trainer_name") or "Unknown",
            "feedback_record_count": len(rows),
            "positive_count": sum(1 for row in rows if row.get("sentiment") == "positive"),
            "negative_count": sum(1 for row in rows if row.get("sentiment") == "negative"),
            "unknown_count": sum(1 for row in rows if row.get("sentiment") == "unknown"),
            "strength_themes": strengths,
            "improvement_themes": improvements,
            "theme_counts": themes,
            "summary": "No detailed feedback evidence available." if not rows else f"{len(rows)} detailed feedback response(s); {len(strengths)} strength theme(s) and {len(improvements)} improvement theme(s).",
            "recommended_action": "Collect or repair detailed feedback evidence." if not rows else ("Review recurring improvement themes and record a coaching action." if improvements else "Continue current delivery approach and monitor future feedback."),
            "confidence": min(95, 35 + len(rows) * 8) if rows else 15,
            "source_datasets": ["trainer_feedback_fact_df"],
        })
    return summaries
