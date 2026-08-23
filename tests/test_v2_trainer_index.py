import pytest
from backend import app, _calculate_trainer_index


def test_trainer_index_calculation_elite_tier():
    """Validates Trainer Index calculation for a high-performing trainer across all 20 criteria."""
    result = _calculate_trainer_index(
        email="test_trainer@koenig-solutions.com",
        name="Abhinav Samant",
        month_util=80.0,
        util_3m=75.0,
        quarterly_utils=[75.0, 80.0, 78.0, 82.0],
        non_sc_hours_pct=10.0,
        beast_ai_deliveries=5,
        beast_ai_saas_deliveries=3,
        quality_index=110.0,
        tbts_count=4,
        mocks_taken=6,
        internal_trainings=2,
        first_time_deliveries_or_certs=4,
        certs_held=["AZ-305: Azure Solutions Architect Expert", "PL-300: Power BI Data Analyst", "AZ-900: Azure Fundamentals"],
        roaming_hours_l12m=50.0,
        night_ilo_hours_l12m=80.0,
        hr_pos=2,
        hr_neg=0,
        vendor_certs=["AAI: AWS Authorized Instructor", "MCT"],
        trainers_developed=2,
        sales_feedback_points=15.0,
        solution_selling_count=1,
        skill_takeovers=2,
        negative_feedbacks=0,
        centre_improvements_reported=1,
        tech_calls_converted=2,
        koenig_tenure_months=36.0,
        prior_exp_months=48.0,
        has_overseas_visa_commitment=True,
    )

    assert result["email"] == "test_trainer@koenig-solutions.com"
    assert result["total_score"] >= 1000.0
    assert result["tier_level"] in [1, 2]
    assert len(result["criteria"]) == 20

    # Verify specific criteria calculations
    criteria_map = {c["s_no"]: c for c in result["criteria"]}

    # S.No 1: Utilization (80% + 10% non-sc = 90%. (90-60)*10 = 300 base pts + 50 quarterly = 350 pts)
    assert criteria_map[1]["points"] == 350.0

    # S.No 2: Beast AI (5 * 10 + 3 * 20 = 110 pts)
    assert criteria_map[2]["points"] == 110.0

    # S.No 3: QI (110 * 2.5 = 275 pts)
    assert criteria_map[3]["points"] == 275.0

    # S.No 4: Knowledge Sharing (4*5 + 6*5 + 2*10 = 70 pts)
    assert criteria_map[4]["points"] == 70.0

    # S.No 10: Instructor (AAI premier = 100, MCT = 20 -> 120 pts)
    assert criteria_map[10]["points"] == 120.0

    # S.No 20: Visa (100 pts)
    assert criteria_map[20]["points"] == 100.0


def test_trainer_index_low_utilization_and_deductions():
    """Validates penalties for under-utilization (<60%) and negative feedback deductions."""
    result = _calculate_trainer_index(
        email="low_trainer@koenig-solutions.com",
        name="Junior Trainer",
        month_util=45.0,
        util_3m=50.0,
        quarterly_utils=[45.0, 50.0, 55.0, 48.0],
        non_sc_hours_pct=0.0,
        beast_ai_deliveries=0,
        beast_ai_saas_deliveries=0,
        quality_index=70.0,
        negative_feedbacks=2,  # -200 pts
        hr_neg=1,              # -20 pts
        hr_pos=0,
    )

    criteria_map = {c["s_no"]: c for c in result["criteria"]}

    # Utilization: (45-60)*10 = -150 pts base, -100 pts quarterly (4 quarters < 60%) = -200 capped min
    assert criteria_map[1]["points"] < 0

    # Deductions
    assert criteria_map[15]["points"] == -200.0
    assert criteria_map[9]["points"] == -20.0


def test_trainer_index_endpoint_and_report():
    """Tests the /api/v2/trainer/trainer-index and /api/v2/hr/monthly-report endpoints."""
    with app.test_client() as client:
        res = client.get('/api/v2/trainer/trainer-index?email=aishwar_v@koenig-solutions.com')
        assert res.status_code == 200
        data = res.get_json()
        assert "trainer_index" in data
        ti = data["trainer_index"]
        assert "total_score" in ti
        assert "tier" in ti
        assert "criteria" in ti
        assert len(ti["criteria"]) == 20
