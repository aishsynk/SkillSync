"""Backend custom course parsing and trainer matching.

This mirrors the current browser fallback, but produces deterministic,
explainable rows that can replace frontend heuristics as the backend matures.
"""

from __future__ import annotations

import re
from typing import Any, Dict, Iterable, List, Tuple

from services.decision_objects import make_decision_object, stable_id
from services.local_ml_service import score_trainers_for_toc


SYNONYMS = {
    "power bi": ["power bi", "powerquery", "power query", "dax", "data modeling", "dashboard", "deployment pipeline"],
    "alteryx": ["alteryx", "workflow automation", "join", "cleansing", "blending", "macro", "analytics app"],
    "python": ["python", "pandas", "numpy", "matplotlib", "scikit-learn", "machine learning", "nlp", "model deployment", "ai"],
    "azure ai": ["azure ai", "cloud", "prompt", "agents", "deployment", "generative ai"],
    "fabric": ["fabric", "lakehouse", "power bi", "data engineering", "analytics"],
    "sql": ["sql", "join", "database", "query", "data prep", "reporting"],
    "databricks": ["databricks", "spark", "lakehouse", "notebook", "data engineering"],
}

ADJACENT = {
    "power bi": ["sql", "dax", "power query", "data modeling", "dashboard"],
    "alteryx": ["sql", "data prep", "workflow automation", "joins", "blending"],
    "python": ["pandas", "numpy", "matplotlib", "scikit-learn", "ml", "ai"],
    "azure ai": ["cloud", "ai", "deployment", "prompt", "agents"],
    "fabric": ["power bi", "lakehouse", "data engineering", "analytics"],
    "sql": ["data prep", "reporting", "power bi", "dashboard"],
    "databricks": ["spark", "lakehouse", "data engineering", "notebooks"],
}

TOOLS = [
    "power bi", "alteryx", "python", "sql", "pandas", "numpy", "matplotlib",
    "scikit-learn", "dax", "power query", "azure ai", "fabric", "prompt",
    "agents", "excel", "databricks", "synapse", "lakehouse",
]

CERT_TERMS = [
    "cert", "certification", "exam", "assessment", "lab", "practical",
    "accreditation", "badge", "credential", "associate", "professional",
]


def _clean(value: Any, default: str = "") -> str:
    if value is None:
        return default
    text = str(value).strip()
    return re.sub(r"\s+", " ", text) if text else default


def _norm(value: Any) -> str:
    return _clean(value).lower()


def _num(value: Any, default: float = 0.0) -> float:
    try:
        if value is None or value == "":
            return default
        return float(value)
    except (TypeError, ValueError):
        return default


def _pct(value: Any) -> float:
    return max(0.0, min(100.0, _num(value)))


def _to_list(value: Any) -> List[str]:
    if not value:
        return []
    if isinstance(value, list):
        out: List[str] = []
        for item in value:
            out.extend(_to_list(item))
        return [x for x in out if x]
    if isinstance(value, dict):
        return [_clean(value.get("name") or value.get("title") or value.get("value"))]
    return [_clean(x) for x in re.split(r"[,;\n|/]+", str(value)) if _clean(x)]


def parse_course_outline(outline: str, meta: Dict[str, Any] | None = None) -> Dict[str, Any]:
    meta = meta or {}
    raw = _clean(outline)
    if not raw:
        return {}
    lower = raw.lower()
    lines = [_clean(x) for x in re.split(r"\r?\n", raw) if _clean(x)]
    tokens = [x for x in re.split(r"[^a-z0-9+.#-]+", lower) if x]
    topics = sorted({k for values in SYNONYMS.values() for k in values if k in lower})
    tools = sorted({tool for tool in TOOLS if tool in lower})
    certs = sorted({term for term in CERT_TERMS if term in lower})
    prerequisites = sorted({term for term in ["sql", "python", "excel", "power bi", "data prep", "statistics", "cloud", "machine learning", "power query", "dax", "azure"] if term in lower})
    vendor = _clean(meta.get("vendor"))
    if not vendor:
        vendor = (
            "Microsoft" if re.search(r"microsoft|azure|power bi|fabric", raw, re.I)
            else "Alteryx" if re.search(r"alteryx", raw, re.I)
            else "Oracle" if re.search(r"oracle", raw, re.I)
            else "Databricks" if re.search(r"databricks", raw, re.I)
            else "OpenAI" if re.search(r"openai|gpt|prompt|agents", raw, re.I)
            else "AWS" if re.search(r"\baws\b|amazon web services", raw, re.I)
            else "Google" if re.search(r"\bgcp\b|google cloud", raw, re.I)
            else ""
        )
    domain = _clean(meta.get("domain"))
    if not domain:
        domain = (
            "Data / Analytics" if re.search(r"power bi|alteryx|sql|analytics|data", raw, re.I)
            else "AI / Data Science / Cloud" if re.search(r"python|ai|ml|machine learning|azure ai|fabric|databricks|agents", raw, re.I)
            else "Cloud / Infrastructure" if re.search(r"security|network|infra|cloud", raw, re.I)
            else "General"
        )
    level = _clean(meta.get("level")) or (
        "Expert" if re.search(r"expert", raw, re.I)
        else "Advanced" if re.search(r"advanced", raw, re.I)
        else "Intermediate" if re.search(r"intermediate", raw, re.I)
        else "Beginner"
    )
    delivery_type = _clean(meta.get("delivery_type") or meta.get("deliveryType")) or (
        "FMAT" if re.search(r"fmat", raw, re.I)
        else "ILT" if re.search(r"ilt", raw, re.I)
        else "Hybrid" if re.search(r"hybrid", raw, re.I)
        else "Custom / Workshop" if re.search(r"workshop|custom", raw, re.I)
        else "Not specified"
    )
    complexity = min(100, 25 + len(topics) * 7 + len(tools) * 5 + len(certs) * 10 + len(lines) * 2 + (8 if delivery_type != "Not specified" else 0) + (10 if level in ("Advanced", "Expert") else 0))
    confidence = max(20, min(95, 30 + len(topics) * 6 + len(tools) * 5 + len(certs) * 6 + min(20, len(tokens) / 4)))
    return {
        "title": _clean(meta.get("title")) or (lines[0] if lines else "Custom Course"),
        "vendor": vendor,
        "domain": domain,
        "technology": tools[0] if tools else (topics[0] if topics else "General"),
        "level": level,
        "deliveryType": delivery_type,
        "topics": topics,
        "tools": tools,
        "prerequisites": prerequisites,
        "certifications": certs,
        "complexity": round(complexity),
        "confidence": round(confidence),
        "rawText": raw,
    }


def _skill_tokens(trainer: Dict[str, Any]) -> List[str]:
    values: List[str] = []
    for key in ("resume_skills", "resume_summary", "resume_experience", "trainings_delivered_for", "current_courses", "resume_feedback", "mapped_courses", "current_assignment", "last_delivered_course", "course_name", "course_url"):
        values.extend(_to_list(trainer.get(key)))
    for cert in trainer.get("resume_certifications") or []:
        values.extend(_to_list(cert))
    for cert in trainer.get("certified_vendors") or []:
        values.extend(_to_list(cert))
    return sorted({_norm(x) for x in values if _norm(x)})


def _overlap(tokens: Iterable[str], haystack: Iterable[str]) -> int:
    token_list = [_norm(t) for t in tokens if _norm(t)]
    hay = [_norm(h) for h in haystack if _norm(h)]
    if not token_list:
        return 0
    hits = sum(1 for token in token_list if any(token in item or item in token for item in hay))
    return round((hits / len(token_list)) * 100)


def _availability_score(status: str, delivery_status: str) -> int:
    if status == "Ready for Live Delivery":
        return 100
    if status == "Available but Needs Prep":
        return 82
    if status == "Busy but Strong Candidate":
        return 68
    if status == "Data Incomplete":
        return 48
    if status == "Hold / Do Not Allocate":
        return 15
    if delivery_status == "Underutilized":
        return 88
    if delivery_status == "Overloaded":
        return 35
    return 55


def _bucket(score: int, readiness: float, availability_score: int, hard_gate: bool, future_skill_fit: int) -> Tuple[str, str]:
    if hard_gate:
        return "not_recommended", "Not Recommended / Blocked"
    if score >= 82 and readiness >= 75 and availability_score >= 70:
        return "best_now", "Best Trainer Now"
    if score >= 72 and availability_score >= 60:
        return "strong_alternative", "Strong Alternative"
    if score >= 62:
        return "backup", "Backup Trainer"
    if score >= 50 and future_skill_fit >= 55:
        return "prep_required", "Can Deliver With Prep"
    return "future_candidate", "Future Development Candidate"


def build_custom_course_match_objects(
    *,
    outline: str | None,
    course_meta: Dict[str, Any] | None,
    trainers: List[Dict[str, Any]],
    availability_rows: List[Dict[str, Any]],
    delivery_rows: List[Dict[str, Any]],
    allocation_rows: List[Dict[str, Any]],
    allocation_ranked_rows: List[Dict[str, Any]],
    feedback_rows: List[Dict[str, Any]],
    future_skill_rows: List[Dict[str, Any]],
    future_cert_rows: List[Dict[str, Any]],
    vendor_strength_rows: List[Dict[str, Any]],
    certification_rows: List[Dict[str, Any]],
    course_best_rows: List[Dict[str, Any]],
) -> List[Dict[str, Any]]:
    course = parse_course_outline(outline or "", course_meta)
    if not course:
        return []

    def by_email(rows: Iterable[Dict[str, Any]]) -> Dict[str, Dict[str, Any]]:
        return {
            _clean(r.get("official_email") or r.get("trainer_email") or r.get("email") or r.get("trainer_key")).lower(): r
            for r in rows or []
            if isinstance(r, dict)
        }

    availability_by_email = by_email(availability_rows)
    delivery_by_email = by_email(delivery_rows)
    feedback_by_email = by_email(feedback_rows)
    future_skill_by_email = by_email(future_skill_rows)
    future_cert_by_email = by_email(future_cert_rows)
    cert_by_email = by_email(certification_rows)
    alloc_rank_by_email = by_email(allocation_ranked_rows)
    tokens = sorted({_norm(x) for x in [course["domain"], course["technology"], course["vendor"], course["deliveryType"], *course["topics"], *course["tools"], *course["prerequisites"], *course["certifications"]] if _norm(x)})
    # The narrower skill/tool vocabulary the course actually teaches — used as
    # the manager-facing "required skills" list (tokens above also include
    # domain/vendor/delivery-type noise used only for scoring).
    required_skills = sorted({_norm(x) for x in [*course["topics"], *course["tools"]] if _norm(x)})

    source_datasets = [
        "trainer_operations_df",
        "trainer_availability_engine_df",
        "delivery_intelligence_df",
        "allocation_intelligence_df",
        "allocation_ranked_trainer_df",
        "course_best_trainer_df",
        "feedback_coaching_df",
        "future_skill_roadmap_df",
        "future_certification_roadmap_df",
        "vendor_strength_df",
        "certification_intelligence_df",
    ]
    rows: List[Dict[str, Any]] = []
    ml_by_email = {
        _clean(row.get("trainer_email")).lower(): row
        for row in score_trainers_for_toc(course.get("rawText") or outline or "", trainers or [])
    }
    for trainer in trainers or []:
        if not isinstance(trainer, dict):
            continue
        email = _clean(trainer.get("official_email") or trainer.get("trainer_email") or trainer.get("email") or trainer.get("trainer_key")).lower()
        availability = availability_by_email.get(email, {})
        delivery = delivery_by_email.get(email, {})
        feedback = feedback_by_email.get(email, {})
        future_skill = future_skill_by_email.get(email, {})
        future_cert = future_cert_by_email.get(email, {})
        cert = cert_by_email.get(email, {})
        allocation_ref = alloc_rank_by_email.get(email) or next((r for r in allocation_rows or [] if _clean(r.get("trainer_email")).lower() == email), {})
        skills = _skill_tokens(trainer)
        matched = [token for token in tokens if any(token in skill or skill in token for skill in skills)]
        missing = [token for token in tokens if token not in matched]
        adjacent = sorted({adj for token in tokens for adj in ADJACENT.get(token, []) if any(adj in skill or skill in adj for skill in skills)})
        skill_score = _overlap(tokens, skills)
        tech_score = _overlap([*course["tools"], *course["topics"]], skills)
        domain_anchor = _norm(course["domain"]).split(" ")[0]
        domain_score = 80 if domain_anchor and any(domain_anchor in s for s in skills) else min(50, skill_score)
        adjacent_score = min(100, len(adjacent) * 10 + (6 if course["level"] in ("Advanced", "Expert") else 0))
        util = _pct(trainer.get("current_utilization") or delivery.get("current_utilization") or availability.get("current_utilization"))
        utilization_score = max(20, 100 - util) if util else 60
        readiness = _pct(delivery.get("delivery_readiness_score") or trainer.get("overall_readiness_score") or trainer.get("readiness_score"))
        availability_status = _clean(availability.get("final_availability_status") or delivery.get("delivery_capacity_status"), "Not available")
        avail_score = _availability_score(_clean(availability.get("final_availability_status")), _clean(delivery.get("delivery_capacity_status")))
        vendor_signal = next((v for v in vendor_strength_rows or [] if _clean(v.get("trainer_name")).lower() == _clean(trainer.get("trainer_name")).lower() or _clean(v.get("trainer_email")).lower() == email or _clean(v.get("vendor")).lower() == _clean(course["vendor"]).lower()), {})
        vendor_strength = _pct(vendor_signal.get("strength_score"))
        cert_signal = 85 if (cert.get("oem_accreditations") or cert.get("vendor_certifications")) else 55
        allocation_score = _pct(allocation_ref.get("allocation_score") or allocation_ref.get("overall_allocation_score"))
        ml_signal = ml_by_email.get(email, {})
        semantic_similarity = _pct(ml_signal.get("similarity"))
        future_skill_fit = min(100, 50 + len(adjacent) * 8 + (10 if future_skill.get("future_oem") and _norm(course["vendor"]) in _norm(future_skill.get("future_oem")) else 0)) if future_skill.get("related_future_skill") else 45
        future_cert_fit = 65 if future_cert.get("recommended_certification_area") else 45
        negative_feedback = _num(trainer.get("negative_feedback_count"))
        feedback_risk = _clean(feedback.get("priority") or trainer.get("feedback_risk"), "Low")
        delivery_risk = _clean(delivery.get("delivery_risk_level"), "Not available")

        # Prefer the backend-resolved classification (Phase 3) over re-deriving
        # compliance/delivery/certification signals from raw fields a second
        # time — it already carries the trainer's real gate + primary blocker.
        classification = trainer.get("classification") if isinstance(trainer.get("classification"), dict) else None
        cert_gaps = cert.get("certification_gaps") or trainer.get("certification_gaps") or []
        certification_blockers = sorted({
            _clean(g.get("vendor") if isinstance(g, dict) else g)
            for g in cert_gaps if _clean(g.get("vendor") if isinstance(g, dict) else g)
        })
        if classification:
            has_compliance_flag = bool(classification.get("has_compliance_flag"))
            has_delivery_concern = bool(classification.get("has_delivery_concern"))
            has_certification_blocker = bool(classification.get("has_certification_blocker")) or bool(certification_blockers)
            hard_gate = classification.get("assignability") == "hold"
            blocker_reasons = [classification.get("primary_blocker")] if (hard_gate and classification.get("primary_blocker")) else []
        else:
            has_compliance_flag = _norm(trainer.get("hr_risk")) == "high" or _num(trainer.get("hr_negative_count")) > 0
            has_delivery_concern = negative_feedback > 0 or feedback_risk.lower() == "high"
            has_certification_blocker = bool(certification_blockers)
            blocker_reasons = []
            if "block" in _norm(trainer.get("final_status") or trainer.get("allocation_status") or trainer.get("readiness_status")):
                blocker_reasons.append("Trainer status is blocked")
            if "hold" in _norm(trainer.get("readiness_status")):
                blocker_reasons.append("Readiness status is hold")
            if has_compliance_flag:
                blocker_reasons.append("Compliance flag is active")
            if negative_feedback >= 3:
                blocker_reasons.append("Negative feedback count is high")
            if feedback_risk.lower() == "high":
                blocker_reasons.append("Feedback risk is high")
            if delivery_risk.lower() == "high":
                blocker_reasons.append("Delivery risk is high")
            hard_gate = bool(blocker_reasons)
        readiness_blocker = blocker_reasons[0] if blocker_reasons else ""
        base = semantic_similarity * 0.24 + skill_score * 0.16 + tech_score * 0.08 + domain_score * 0.07 + adjacent_score * 0.07 + readiness * 0.13 + avail_score * 0.11 + utilization_score * 0.05 + cert_signal * 0.04 + vendor_strength * 0.02 + allocation_score * 0.02 + future_skill_fit * 0.01
        penalty = negative_feedback * 7 + (12 if hard_gate else 0) + (12 if delivery_risk.lower() == "high" else 0)
        match_score = round(max(0, min(100, base - penalty)))
        confidence = round(max(25, min(98, 35 + len(matched) * 5 + len(adjacent) * 3 + course["confidence"] * 0.25 + (8 if availability_status != "Not available" else 0) + (_pct(delivery.get("delivery_confidence")) * 0.15 if delivery.get("delivery_confidence") else 0) + (4 if future_skill.get("related_future_skill") else 0) + (3 if future_cert.get("recommended_certification_area") else 0) - (8 if availability_status == "Data Incomplete" else 0))))
        bucket, role = _bucket(match_score, readiness, avail_score, hard_gate, future_skill_fit)
        manager_action = (
            "Do not assign as primary. Resolve the compliance flag, delivery concern, or certification blocker first." if hard_gate
            else "Allocate now and keep the strongest backup visible." if bucket == "best_now"
            else "Keep in reserve and compare against the primary trainer before confirming." if bucket == "strong_alternative"
            else "Use as backup coverage and keep the course scope visible." if bucket == "backup"
            else "Assign only after a prep plan, mentor review, and a quick verification run." if bucket == "prep_required"
            else "Develop this trainer for the course and close the gap before live delivery."
        )
        reason = (
            f"Blocked because {blocker_reasons[0].lower()}." if blocker_reasons
            else f"{_clean(trainer.get('trainer_name'), 'Trainer')} matches {', '.join(matched[:6]) or 'limited course signals'} and has {availability_status} availability."
        )
        trade_offs = [
            f"Matched skills: {', '.join(matched[:6]) if matched else 'few'}",
            f"Missing skills: {', '.join(missing[:6]) if missing else 'none obvious'}",
            f"Delivery readiness: {readiness:g}",
            f"Availability: {availability_status}",
            f"Certification: {'Relevant evidence present' if cert_signal >= 85 else 'Needs review'}",
            f"Risk: {feedback_risk} / {delivery_risk}",
        ]
        trainer_name = _clean(trainer.get("trainer_name"), "Unknown")
        evidence = {
            "scoring_source": "backend_scored",
            "source_datasets": source_datasets,
            "matched_skills": matched,
            "missing_skills": missing,
            "adjacent_skills": adjacent,
                "score_components": {
                "semantic_tfidf_similarity": semantic_similarity,
                "skill_match": skill_score,
                "technology_match": tech_score,
                "domain_match": domain_score,
                "adjacent_match": adjacent_score,
                "readiness": readiness,
                "availability": avail_score,
                "utilization": utilization_score,
                "certification": cert_signal,
                "vendor_strength": vendor_strength,
                "allocation_reference": allocation_score,
            },
            "confidence_inputs": {
                "course_parse_confidence": course["confidence"],
                "availability_status_present": availability_status != "Not available",
                "roadmap_present": bool(future_skill.get("related_future_skill")),
                "certification_plan_present": bool(future_cert.get("recommended_certification_area")),
                "trainer_email_present": bool(email),
                "trainer_name_present": trainer_name != "Unknown",
            },
            "blocker_reasons": blocker_reasons,
            "course_reference": next((r for r in course_best_rows or [] if _norm(course["vendor"]) in _norm(r.get("course_name")) or _norm(course["technology"]) in _norm(r.get("course_name"))), None),
            "future_skill": future_skill,
            "future_certification": future_cert,
            "delivery": delivery,
            "availability": availability,
            "feedback": feedback,
            "cert": cert,
                "trace": {
                "course_title": course["title"],
                "course_vendor": course["vendor"],
                "trainer_key": trainer.get("trainer_key"),
                "allocation_reference_found": bool(allocation_ref),
                "availability_row_found": bool(availability),
                "delivery_row_found": bool(delivery),
                "feedback_row_found": bool(feedback),
                "future_skill_row_found": bool(future_skill),
                "future_cert_row_found": bool(future_cert),
                "certification_row_found": bool(cert),
                "local_ml_model": ml_signal.get("model"),
                "local_ml_matched_terms": ml_signal.get("matched_terms") or [],
            },
        }
        compatibility = {
            "source": "backend_scored",
            "course_id": course["title"],
            "course_name": course["title"],
            "parsed_course_outline": course,
            "status": role,
            "recommendation_role": role,
            "trainer_key": trainer.get("trainer_key") or email,
            "allocation_score": match_score,
            "match_score": match_score,
            "readiness_score": readiness,
            "utilization": util or "Not available",
            "qubits_score": _pct(trainer.get("avg_qubits_score") or trainer.get("qubit_score")),
            "vendor_strength": vendor_strength or "Not available",
            "certification_status": future_cert.get("recommended_certification_area") or ("Relevant accreditation appears available" if cert_signal >= 85 else "Certification gap not explicit"),
            "availability_status": availability_status,
            "availability_confidence": _pct(availability.get("availability_confidence") or delivery.get("availability_confidence")),
            "feedback_risk": feedback.get("feedback_signal") or feedback_risk,
            "compliance_flag": "High" if has_compliance_flag else "Low",
            "delivery_concern": "High" if has_delivery_concern else "Low",
            "required_skills": required_skills,
            "certification_blockers": certification_blockers,
            "readiness_blocker": readiness_blocker,
            "backup_trainers": [],  # filled in the post-scoring pass below, once every trainer's match_score is known
            "delivery_mode_fit": allocation_ref.get("delivery_mode_fit") or allocation_ref.get("ilt_fmat_fit") or "Not available",
            "custom_batch_experience": allocation_ref.get("custom_batch_experience") or trainer.get("custom_batch_count") or "Not available",
            "delivery_history": delivery.get("delivery_history") or delivery.get("delivery_history_count") or allocation_ref.get("delivery_history") or "Not available",
            "succession_risk": allocation_ref.get("succession_risk") or "Unknown",
            "confidence": confidence,
            "reason": reason,
            "why_selected": reason,
            "why_not_primary": "No material reason; this is the safest immediate option." if bucket == "best_now" else ("This trainer is not eligible for primary delivery until the risk is resolved." if hard_gate else "A stronger or safer primary option may exist."),
            "matched_skills": matched,
            "missing_skills": missing,
            "adjacent_skills": adjacent,
            "future_skill_fit": f"{future_skill.get('related_future_skill')} ({future_skill.get('future_oem') or 'Not available'})" if future_skill.get("related_future_skill") else "No explicit future skill path detected",
            "future_certification_fit": future_cert.get("recommended_certification_area") or ("Relevant accreditation appears available" if cert_signal >= 85 else "Certification gap not explicit"),
            "delivery_risk": delivery_risk,
            "hard_gate": hard_gate,
            "blocked_reason": "; ".join(blocker_reasons),
            "manager_action": manager_action,
            "trade_offs": trade_offs,
            "prep_horizon": "Not suitable until risk clears" if hard_gate else ("Immediate" if bucket == "best_now" else "Short term" if bucket in ("strong_alternative", "backup") else "Medium term" if bucket == "prep_required" else "Long term"),
        }
        rows.append(
            make_decision_object(
                entity_type="custom_course_match",
                trainer_email=email,
                trainer_name=trainer_name,
                score=match_score,
                confidence=confidence,
                bucket=bucket,
                status=role,
                blockers=blocker_reasons,
                evidence=evidence,
                recommended_action=manager_action,
                source_datasets=source_datasets,
                object_id=stable_id("custom_course_match", course["title"], email or trainer_name),
                extra=compatibility,
            )
        )
    rows.sort(key=lambda row: row["allocation_score"], reverse=True)

    # Backup trainer suggestions: for each trainer, the next-best other
    # trainers for this same course who are not hard-gated — genuinely
    # derived from the match scores just computed above, not invented.
    eligible = [r for r in rows if not r.get("hard_gate")]
    for row in rows:
        candidates = [r for r in eligible if r["trainer_email"] != row["trainer_email"]]
        row["backup_trainers"] = [
            {
                "trainer_name": c["trainer_name"],
                "trainer_email": c["trainer_email"],
                "match_score": c["allocation_score"],
            }
            for c in candidates[:2]
        ]

    return rows
