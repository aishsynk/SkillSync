"""Shared scoring helpers for SkillEdge."""

from shared.safety import safe_truthy


def score_trainer(t):
    d_rows = t["details"]
    q = t["avg_qubit"]
    skills_active = [s for s in t["skills"] if not s["is_discontinue"]]

    skills_score = min(100.0, len(skills_active) * 12.0)
    assign_score = min(100.0, len(t["assignments"]) * 10.0)
    cert_score   = min(100.0, t["cert_count"] * 20.0)
    util_score   = t["util_pct"] if t["util_pct"] is not None else None
    fb_score     = max(0.0, 100.0 - t["negfb_total"] * 20.0)
    details_score = min(100.0, (sum(r["tech_call_rating"] for r in d_rows) / len(d_rows) * 20.0)) if d_rows else None
    qubit_score  = q if q is not None else None

    parts = [
        (qubit_score if qubit_score is not None else skills_score, 0.25),
        (assign_score, 0.15),
        (cert_score,   0.15),
        (util_score,   0.10),
        (fb_score,     0.15),
        (details_score, 0.10),
    ]
    avail = [(v, w) for v, w in parts if v is not None]
    wsum  = sum(w for _, w in avail) or 1.0
    readiness = round(sum(v * w for v, w in avail) / wsum, 1)

    missing = [name for name, v in [("utilization", util_score), ("qubit", qubit_score),
                                     ("tech_rating", details_score)] if v is None]
    confidence = round(100.0 - len(missing) * 12.0, 0)

    bucket = ("Ready Now" if readiness >= 80 else
              "Can Deliver with Prep" if readiness >= 65 else
              "Needs Coaching" if readiness >= 45 else
              "Not Recommended")
    if q is None and t["util_pct"] is None:
        bucket = "Data Incomplete"

    diversity = len({s["course_name"] for s in skills_active})
    future    = any(r["is_future_skill"] for r in d_rows)
    risk_taker = round(min(100.0, diversity * 8 + (20 if future else 0) +
                           (15 if safe_truthy(t["trainer"].get("TrainerPlus")) else 0) +
                           (t["util_pct"] is not None and (100 - t["util_pct"]) * 0.15 or 0)) -
                       t["negfb_total"] * 5, 1)
    growth = ("Safe Expert" if readiness >= 80 and t["negfb_total"] == 0 else
              "Risk Taker" if risk_taker >= 60 and readiness >= 45 else
              "Growth Candidate" if readiness >= 55 else
              "Do Not Risk" if (t["negfb_total"] > 2) else
              "Data Incomplete" if bucket == "Data Incomplete" else "Growth Candidate")

    return {
        "readiness": readiness, "readiness_bucket": bucket, "confidence": confidence,
        "risk_taker_score": max(0.0, risk_taker), "growth_bucket": growth,
        "missing_signals": missing,
        "evidence": {
            "skills_active": len(skills_active), "assignments": len(t["assignments"]),
            "certs": t["cert_count"], "neg_feedback": t["negfb_total"],
            "hr_pos": t["hr"]["positive"], "hr_neg": t["hr"]["negative"],
            "avg_qubit": q, "utilization": t["util_pct"], "diversity": diversity,
        },
    }


def recommend(t, sc):
    r = sc["readiness"]
    util = t["util_pct"]
    if t["negfb_total"] > 2:
        return ("Hold Delivery", "Quality/compliance risk signals present", "High")
    if r >= 80:
        if util is not None and util >= 85:
            return ("Review Availability", "Ready but overloaded", "Medium")
        return ("Allocate", "Delivery-ready with capacity", "High")
    if 45 <= r < 80:
        if util is not None and util < 40:
            return ("Book Mock", "Capacity exists but not delivery-safe", "High")
        return ("Coach", "Adjacent skills; can be upgraded", "Medium")
    if sc["readiness_bucket"] == "Data Incomplete":
        return ("Review Data Gap", "Key signals missing", "Medium")
    return ("Upskill", "Low readiness", "Low")
