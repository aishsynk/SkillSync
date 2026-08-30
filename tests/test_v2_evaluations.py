from unittest.mock import patch

from backend import app, _generate_manager_evaluation


def _no_feedback():
    return {
        "count": 0, "response_count": 0, "avg_rating": None, "rating_scale": 5,
        "recent_date": "", "positive_quotes": [], "constructive_quotes": [], "quotes": [],
    }


def _feedback(avg, positives=(), constructives=()):
    return {
        "count": len(positives) + len(constructives) or 1,
        "response_count": 5, "avg_rating": avg, "rating_scale": 5,
        "recent_date": "2026-08-20",
        "positive_quotes": [{"text": p, "date": "2026-08-20", "rating": 5, "kind": "positive"} for p in positives],
        "constructive_quotes": [{"text": c, "date": "2026-08-10", "rating": 2, "kind": "constructive"} for c in constructives],
        "quotes": [],
    }


# Boilerplate that used to be asserted on every trainer regardless of evidence.
_BANNED = [
    "articulation remains the primary growth area",
    "pacing was noticeably more controlled",
    "hesitation and slight panic",
    "Goal → Steps → Verify",
    "Composure: Improving",
    "unscripted Q&A",
]


def test_evaluation_is_evidence_only_no_boilerplate():
    with patch("backend._trainer_feedback_detail",
               return_value=_feedback(4.6, positives=["Explained cloud networking clearly with real examples."])):
        res = _generate_manager_evaluation(
            name="Abhinav Sharma", email="abhinav@koenig-solutions.com", month_label="August 2026",
            avg_qubits=84, top_courses=[{"course_name": "PL-300: Power BI Data Analyst"}],
            month_util=75.0, util_3m=72.0, batch_count=2,
            month_assignments=[{"course": "PL-300", "participants": 12}],
            neg_total=0, hr_pos=2, hr_neg=0,
            cert_intel={"held": ["PL-300"], "gap_count": 0, "gaps": []}, hr_score=92,
        )

    for key in ("strength", "area_of_improvement", "other_feedback", "trajectory",
                "sentiment", "mock_summary", "formatted_text", "learner_feedback"):
        assert key in res

    blob = " ".join([res["strength"], res["area_of_improvement"], res["other_feedback"], res["mock_summary"]])
    for phrase in _BANNED:
        assert phrase not in blob, f"boilerplate leaked: {phrase!r}"

    # Real signals surface
    assert "4.6/5" in res["strength"]
    assert "Explained cloud networking clearly" in res["strength"]
    assert "2 positive HR" in res["strength"]
    assert res["trajectory"] == "High Performer"
    assert res["mock_summary"].startswith("Learner rating 4.6/5")


def test_evaluation_with_no_evidence_says_so():
    with patch("backend._trainer_feedback_detail", return_value=_no_feedback()):
        res = _generate_manager_evaluation(
            name="Priya Nair", email="priya@koenig-solutions.com", month_label="August 2026",
            avg_qubits=70, top_courses=[], month_util=68.0, util_3m=66.0, batch_count=0,
            month_assignments=[], neg_total=0, hr_pos=0, hr_neg=0,
            cert_intel={"held": [], "gap_count": 0, "gaps": []}, hr_score=74,
        )
    assert "No improvement areas are flagged" in res["area_of_improvement"]
    assert res["mock_summary"] == "No learner feedback on record for this period."
    assert res["trajectory"] in ("No Activity", "Steady")


def test_evaluation_at_risk_profile_is_specific():
    with patch("backend._trainer_feedback_detail",
               return_value=_feedback(2.4, constructives=["Sessions moved too fast to follow the labs."])):
        res = _generate_manager_evaluation(
            name="Rohan Verma", email="rohan@koenig-solutions.com", month_label="August 2026",
            avg_qubits=62, top_courses=[{"course_name": "AZ-104: Azure Administrator"}],
            month_util=45.0, util_3m=40.0, batch_count=0, month_assignments=[],
            neg_total=1, hr_pos=0, hr_neg=1,
            cert_intel={"held": [], "gap_count": 1, "gaps": [{"because": "AZ-104"}]}, hr_score=50,
        )
    assert res["trajectory"] == "Needs Coaching"
    assert res["sentiment"] == "Urgent Attention"
    assert "certification gap" in res["area_of_improvement"]
    assert "negative feedback record" in res["area_of_improvement"]
    assert "Sessions moved too fast" in res["area_of_improvement"]
    assert "1-on-1" in res["other_feedback"]


def test_hr_monthly_report_v2_endpoint_returns_structured_feedback():
    with app.test_client() as client:
        res = client.get('/api/v2/hr/monthly-report?manager=aishwar_v@koenig-solutions.com')
        assert res.status_code in (200, 401)
        if res.status_code == 200:
            data = res.get_json()
            if data.get("loading"):
                return
            assert "reportees" in data
            for rep in data["reportees"]:
                sf = rep["structured_feedback"]
                assert {"strength", "area_of_improvement", "other_feedback", "trajectory"} <= set(sf)
