"""Small, dependency-free, explainable ML primitives for SkillEdge.

This module intentionally performs no text generation. It uses TF-IDF cosine
similarity to compare a TOC with trainer evidence and returns an auditable
feature score plus the terms that created the match.
"""

from __future__ import annotations

import math
import re
from collections import Counter
from typing import Any, Dict, Iterable, List, Tuple


STOPWORDS = {
    "a", "an", "and", "are", "as", "at", "be", "by", "for", "from", "in",
    "is", "it", "of", "on", "or", "that", "the", "this", "to", "with",
    "course", "training", "trainer", "module", "learn", "learning",
}


def tokenize(value: Any) -> List[str]:
    text = str(value or "").lower().replace("power bi", "power_bi").replace("machine learning", "machine_learning")
    return [token for token in re.findall(r"[a-z0-9+#._-]{2,}", text) if token not in STOPWORDS]


def trainer_document(trainer: Dict[str, Any]) -> str:
    parts: List[str] = []
    for key in (
        "resume_skills", "resume_summary", "resume_experience", "current_courses",
        "trainings_delivered_for", "resume_certifications", "certified_vendors",
        "primary_vendor", "technology_strengths", "domain_strengths",
    ):
        value = trainer.get(key)
        if isinstance(value, list):
            for item in value:
                parts.append(str(item.get("name") if isinstance(item, dict) else item))
        elif value:
            parts.append(str(value))
    # Demonstrated/current courses are intentionally repeated so proven course
    # evidence carries more TF weight than a single resume mention.
    current = trainer.get("current_courses") or []
    parts.extend(str(item) for item in current)
    return " ".join(parts)


def tfidf_similarity(query: str, documents: Iterable[str]) -> List[Dict[str, Any]]:
    docs = [tokenize(document) for document in documents]
    query_tokens = tokenize(query)
    all_docs = [query_tokens, *docs]
    document_frequency = Counter()
    for tokens in all_docs:
        document_frequency.update(set(tokens))
    total = len(all_docs)

    def vector(tokens: List[str]) -> Dict[str, float]:
        counts = Counter(tokens)
        length = max(1, sum(counts.values()))
        return {
            term: (count / length) * (math.log((1 + total) / (1 + document_frequency[term])) + 1)
            for term, count in counts.items()
        }

    query_vector = vector(query_tokens)
    query_norm = math.sqrt(sum(value * value for value in query_vector.values()))
    results = []
    for tokens in docs:
        doc_vector = vector(tokens)
        doc_norm = math.sqrt(sum(value * value for value in doc_vector.values()))
        common = set(query_vector).intersection(doc_vector)
        dot = sum(query_vector[term] * doc_vector[term] for term in common)
        cosine = dot / (query_norm * doc_norm) if query_norm and doc_norm else 0.0
        contributions: List[Tuple[str, float]] = sorted(
            ((term.replace("_", " "), query_vector[term] * doc_vector[term]) for term in common),
            key=lambda item: item[1], reverse=True,
        )
        results.append({
            "similarity": round(cosine * 100, 1),
            "matched_terms": [term for term, _ in contributions[:12]],
            "query_term_count": len(set(query_tokens)),
            "trainer_term_count": len(set(tokens)),
            "model": "tfidf_cosine_v1",
        })
    return results


def score_trainers_for_toc(outline: str, trainers: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
    scores = tfidf_similarity(outline, [trainer_document(trainer) for trainer in trainers])
    return [
        {
            "trainer_key": trainer.get("trainer_key") or trainer.get("official_email"),
            "trainer_email": trainer.get("official_email"),
            "trainer_name": trainer.get("trainer_name"),
            **score,
        }
        for trainer, score in zip(trainers, scores)
    ]

