import pytest
from backend import app, _generate_manager_evaluation


def test_generate_manager_evaluation_structure_and_tone():
    """Verify that _generate_manager_evaluation produces high-quality, structured feedback."""
    eval_res = _generate_manager_evaluation(
        name="Abhinav Sharma",
        email="abhinav@koenig-solutions.com",
        month_label="August 2026",
        avg_qubits=84,
        top_courses=[{"course_name": "PL-300: Power BI Data Analyst"}, {"course_name": "DP-300: Administering SQL"}],
        month_util=75.0,
        util_3m=72.0,
        batch_count=2,
        month_assignments=[{"course": "PL-300", "participants": 12}],
        neg_total=0,
        hr_pos=2,
        hr_neg=0,
        cert_intel={"held": ["PL-300"], "gap_count": 0, "gaps": []},
        hr_score=92,
    )

    assert "strength" in eval_res
    assert "area_of_improvement" in eval_res
    assert "other_feedback" in eval_res
    assert "trajectory" in eval_res
    assert "sentiment" in eval_res
    assert "mock_summary" in eval_res
    assert "formatted_text" in eval_res

    # Verify Strength content
    assert "Abhinav continues to show strong theoretical grounding" in eval_res["strength"]
    assert "Qubits mastery (84%)" in eval_res["strength"]
    assert "pacing was noticeably more controlled" in eval_res["strength"]
    assert "composure" in eval_res["strength"]
    assert "positive recognition" in eval_res["strength"]

    # Verify Area of Improvement content
    assert "articulation remains the primary growth area" in eval_res["area_of_improvement"]
    assert "Goal → Steps → Verify" in eval_res["area_of_improvement"]
    assert "unscripted Q&A" in eval_res["area_of_improvement"]
    assert "clarity window" in eval_res["area_of_improvement"]

    # Verify Other Feedback / Manager's Verdict
    assert eval_res["trajectory"] == "High Performer"
    assert eval_res["sentiment"] == "Positive"
    assert "operating with high delivery readiness" in eval_res["other_feedback"]

    # Verify formatted text contains all headers
    assert "Strength:\n" in eval_res["formatted_text"]
    assert "Area of Improvement:\n" in eval_res["formatted_text"]
    assert "Other Feedback:\n" in eval_res["formatted_text"]


def test_generate_manager_evaluation_at_risk_profile():
    """Verify evaluation for at-risk / transitioning profile with cert gaps and negative feedback."""
    eval_res = _generate_manager_evaluation(
        name="Rohan Verma",
        email="rohan@koenig-solutions.com",
        month_label="August 2026",
        avg_qubits=62,
        top_courses=[{"course_name": "AZ-104: Azure Administrator"}],
        month_util=45.0,
        util_3m=40.0,
        batch_count=0,
        month_assignments=[],
        neg_total=1,
        hr_pos=0,
        hr_neg=1,
        cert_intel={"held": [], "gap_count": 1, "gaps": [{"because": "AZ-104"}]},
        hr_score=50,
    )

    assert eval_res["trajectory"] == "Needs Coaching"
    assert eval_res["sentiment"] == "Urgent Attention"
    assert "Action Required: Complete and pass the official certification exams" in eval_res["area_of_improvement"]
    assert "1 noted feedback item" in eval_res["area_of_improvement"]
    assert "1-on-1 managerial coaching" in eval_res["other_feedback"]


def test_hr_monthly_report_v2_endpoint_returns_structured_feedback():
    """Verify that /api/v2/hr/monthly-report includes structured feedback and flattened metrics."""
    with app.test_client() as client:
        res = client.get('/api/v2/hr/monthly-report?manager=aishwar_v@koenig-solutions.com')
        # Route returns 200 (if session passes) or 401 unauthenticated
        assert res.status_code in (200, 401)
        if res.status_code == 200:
            data = res.get_json()
            assert "reportees" in data
            assert "team_summary" in data
            for rep in data["reportees"]:
                assert "structured_feedback" in rep
                assert "strength" in rep["structured_feedback"]
                assert "area_of_improvement" in rep["structured_feedback"]
                assert "other_feedback" in rep["structured_feedback"]
                assert "trajectory" in rep
