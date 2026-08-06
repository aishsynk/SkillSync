import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
for path in (ROOT, ROOT / "backend"):
    if str(path) not in sys.path:
        sys.path.insert(0, str(path))

from services.allocation_decision_service import build_allocation_decision_objects
from services.custom_course_match_service import build_custom_course_match_objects
from services.decision_objects import build_trainer_decision_objects
from services.manager_action_service import build_manager_action_objects
from services.current_state_service import build_batch_engagement_rows, build_trainer_current_states
from services.reference_data_service import build_course_master, build_unallocated_demand
from services.feedback_intelligence_service import build_feedback_facts, build_feedback_summaries
from services.local_ml_service import score_trainers_for_toc


REQUIRED = {
    "decision_contract_version",
    "id",
    "entity_type",
    "trainer_email",
    "trainer_name",
    "score",
    "confidence",
    "blockers",
    "evidence",
    "recommended_action",
    "source_datasets",
    "source_field_names",
    "source_labels",
}


def assert_true(condition, message):
    if not condition:
        raise AssertionError(message)


def assert_contract(row, label):
    missing = REQUIRED - set(row)
    assert_true(not missing, f"{label} missing fields: {sorted(missing)}")
    assert_true("bucket" in row or "status" in row, f"{label} missing bucket/status")
    assert_true(isinstance(row["blockers"], list), f"{label} blockers must be a list")
    assert_true(isinstance(row["evidence"], dict), f"{label} evidence must be an object")
    assert_true(isinstance(row["source_datasets"], list), f"{label} source_datasets must be a list")
    assert_true(isinstance(row["source_field_names"], dict), f"{label} source_field_names must be an object")
    assert_true(isinstance(row["source_labels"], dict), f"{label} source_labels must be an object")
    assert_true(row["decision_contract_version"], f"{label} decision_contract_version must be populated")
    assert_true(0 <= row["score"] <= 100, f"{label} score outside 0-100")
    assert_true(0 <= row["confidence"] <= 100, f"{label} confidence outside 0-100")


def sample_trainers():
    return [
        {
            "trainer_key": "t-1",
            "trainer_name": "Asha Trainer",
            "official_email": "ASHA.TRAINER@EXAMPLE.COM ",
            "resume_skills": "Power BI, DAX, Power Query, SQL, data modeling",
            "resume_certifications": ["Microsoft Power BI"],
            "qubits_score": 88,
            "readiness_bucket": "Ready",
            "negative_feedback_count": 0,
            "hr_negative_count": 0,
        },
        {
            "resume_skills": None,
            "readiness_status": "Hold",
            "negative_feedback_count": "4",
            "hr_negative_count": "1",
        },
    ]


def test_no_outline():
    rows = build_custom_course_match_objects(
        outline="",
        course_meta={},
        trainers=sample_trainers(),
        availability_rows=[],
        delivery_rows=[],
        allocation_rows=[],
        allocation_ranked_rows=[],
        feedback_rows=[],
        future_skill_rows=[],
        future_cert_rows=[],
        vendor_strength_rows=[],
        certification_rows=[],
        course_best_rows=[],
    )
    assert_true(rows == [], "no outline should return an empty custom_course_match_df")


def test_outline_provided_and_missing_trainer_fields():
    rows = build_custom_course_match_objects(
        outline="Advanced Power BI workshop covering DAX, Power Query, SQL models, dashboards, labs, and certification readiness.",
        course_meta={"title": "Power BI Custom Workshop"},
        trainers=sample_trainers(),
        availability_rows=[{"trainer_email": "asha.trainer@example.com", "final_availability_status": "Ready for Live Delivery", "availability_confidence": 90}],
        delivery_rows=[{"trainer_email": "asha.trainer@example.com", "delivery_readiness_score": 86, "delivery_risk_level": "Low"}],
        allocation_rows=[],
        allocation_ranked_rows=[{"trainer_email": "asha.trainer@example.com", "allocation_score": 82}],
        feedback_rows=[],
        future_skill_rows=[],
        future_cert_rows=[],
        vendor_strength_rows=[{"trainer_email": "asha.trainer@example.com", "vendor": "Microsoft", "strength_score": 84}],
        certification_rows=[{"trainer_email": "asha.trainer@example.com", "vendor_certifications": ["PL-300"]}],
        course_best_rows=[],
    )
    assert_true(rows, "outline should produce backend match rows")
    for idx, row in enumerate(rows):
        assert_contract(row, f"custom_course_match_objects[{idx}]")
    missing_field_row = next(row for row in rows if row["trainer_name"] == "Unknown")
    assert_true(missing_field_row["confidence"] <= 75, "missing trainer identity should lower confidence")
    assert_true(missing_field_row["blockers"], "risky trainer should include blockers")


def test_empty_datasets():
    assert_true(build_trainer_decision_objects([]) == [], "empty trainer rows should be safe")
    assert_true(build_allocation_decision_objects([]) == [], "empty allocation rows should be safe")
    assert_true(build_manager_action_objects([]) == [], "empty action rows should be safe")


def test_other_decision_contracts_and_deduplication():
    trainer_rows = build_trainer_decision_objects(sample_trainers())
    for idx, row in enumerate(trainer_rows):
        assert_contract(row, f"trainer_decision_objects[{idx}]")

    allocation_rows = build_allocation_decision_objects(
        [
            {
                "course_name": "Power BI Custom Workshop",
                "trainer_name": "Asha Trainer",
                "trainer_email": "asha.trainer@example.com",
                "allocation_score": 88,
                "rank": 1,
                "recommendation_role": "Best Trainer",
                "risk_flags": "",
            },
            {
                "course_name": "Power BI Custom Workshop",
                "trainer_name": "Backup Trainer",
                "trainer_email": "backup@example.com",
                "allocation_score": 72,
                "rank": 2,
                "recommendation_role": "Strong Alternative",
            },
        ],
        allocation_intelligence_rows=[
            {
                "course_name": "Power BI Custom Workshop",
                "trainer_email": "asha.trainer@example.com",
                "reason": "Strong readiness and allocation evidence.",
                "trade_offs": ["Availability confirmed"],
            }
        ],
        course_best_rows=[
            {
                "course_name": "Power BI Custom Workshop",
                "best_trainer": "Asha Trainer",
                "trainer_email": "asha.trainer@example.com",
                "alternative_trainers": [{"trainer_name": "Backup Trainer", "trainer_email": "backup@example.com", "allocation_score": 72}],
            }
        ],
        allocation_risk_rows=[
            {
                "course_name": "Power BI Custom Workshop",
                "allocation_risk": "Low",
                "risk_reason": "Multiple alternatives are visible.",
            }
        ],
        course_allocation_rows=[
            {
                "course_name": "Power BI Custom Workshop",
                "trainer_email": "asha.trainer@example.com",
                "overall_allocation_score": 84,
            }
        ],
    )
    assert_contract(allocation_rows[0], "allocation_decision_objects[0]")
    assert_true(allocation_rows[0]["bucket"] == "best_now", "best trainer should map to best_now bucket")
    assert_true(allocation_rows[0]["backup_trainer_options"], "allocation object should expose backup trainer options")
    assert_true(allocation_rows[0]["source_row_ids"], "allocation object should expose source_row_ids")

    action_rows = build_manager_action_objects([
        {
            "action_type": "certification_gap",
            "priority": "urgent",
            "trainer_name": "Asha Trainer",
            "trainer_email": "asha.trainer@example.com",
            "course_name": "Power BI Custom Workshop",
            "reason": "Close certification evidence before allocation",
        },
        {
            "action_type": "certification_gap",
            "priority": "High",
            "trainer_name": "Asha Trainer",
            "trainer_email": "asha.trainer@example.com",
            "course_name": "Power BI Custom Workshop",
            "reason": "Close certification evidence before allocation",
        },
    ])
    assert_true(len(action_rows) == 1, "manager action objects should dedupe duplicate actions")
    assert_contract(action_rows[0], "manager_action_objects[0]")


def test_trainer_decision_backend_extraction():
    allocation_rows = build_allocation_decision_objects(
        [
            {
                "course_name": "Secure Azure Architecture",
                "trainer_name": "Review Trainer",
                "trainer_email": "review@example.com",
                "allocation_score": 86,
                "rank": 1,
                "recommendation_role": "Best Trainer",
            },
            {
                "course_name": "Secure Azure Architecture",
                "trainer_name": "Blocked Trainer",
                "trainer_email": "blocked@example.com",
                "allocation_score": 91,
                "rank": 2,
                "recommendation_role": "Strong Alternative",
            },
        ],
        allocation_risk_rows=[
            {
                "course_name": "Secure Azure Architecture",
                "allocation_risk": "High",
                "risk_reason": "Delivery depends on a narrow trainer pool.",
                "recommended_manager_action": "Review backup coverage before confirming.",
            }
        ],
    )
    action_rows = build_manager_action_objects(
        [
            {
                "action_type": "Review backup coverage",
                "priority": "High",
                "trainer_name": "Review Trainer",
                "trainer_email": "review@example.com",
                "course_name": "Secure Azure Architecture",
                "reason": "Delivery depends on a narrow trainer pool.",
                "recommended_action": "Confirm backup trainer before allocation.",
            },
            {
                "action_type": "Review backup coverage",
                "priority": "urgent",
                "trainer_name": "Review Trainer",
                "trainer_email": "review@example.com",
                "course_name": "Secure Azure Architecture",
                "reason": "Delivery depends on a narrow trainer pool.",
                "recommended_action": "Confirm backup trainer before allocation.",
            },
        ]
    )
    assert_true(len(action_rows) == 1, "trainer action rollup source actions should dedupe")

    trainer_rows = build_trainer_decision_objects(
        [
            {
                "trainer_name": "Review Trainer",
                "official_email": "review@example.com",
                "overall_readiness_score": 84,
                "readiness_bucket": "Ready",
                "hr_risk": "Low",
                "hr_negative_count": 0,
                "negative_feedback_count": 0,
            },
            {
                "trainer_name": "Blocked Trainer",
                "official_email": "blocked@example.com",
                "overall_readiness_score": 90,
                "readiness_bucket": "Ready",
                "hr_risk": "High",
                "hr_negative_count": 1,
                "negative_feedback_count": 0,
            },
        ],
        allocation_rows=allocation_rows,
        manager_action_rows=action_rows,
    )
    for idx, row in enumerate(trainer_rows):
        assert_contract(row, f"slice4_trainer_decision_objects[{idx}]")
        for field in ("can_assign_now", "primary_blocker", "next_manager_action", "risk_register", "allocation_summary", "action_summary", "source_row_ids"):
            assert_true(field in row, f"trainer decision object should expose {field}")

    review = next(row for row in trainer_rows if row["trainer_email"] == "review@example.com")
    blocked = next(row for row in trainer_rows if row["trainer_email"] == "blocked@example.com")
    assert_true(review["status"] != "blocked", "high risk without hard blocker should be review/monitor, not blocked")
    assert_true(review["can_assign_now"] is True, "review trainer remains assignable with manager review")
    assert_true(review["action_summary"]["total_actions"] == 1, "trainer action rollup should dedupe overlapping actions")
    assert_true(review["risk_register"], "trainer-level risk register should be populated from allocation risk")
    assert_true(blocked["status"] == "blocked", "hard blocker should block assignment")
    assert_true(blocked["can_assign_now"] is False, "hard blocker should mark trainer not assignable")
    assert_true(blocked["primary_blocker"], "hard blocker should expose a primary blocker")


def test_current_state_requires_dated_evidence():
    fetched = [{
        "trainer": {"TrainerName": "Asha Trainer", "OffEmail": "asha@example.com"},
        "email": "asha@example.com",
        "emp": "E1",
        "prev_upcoming": {"rows": [{
            "AssignmentId": "A-1", "Course": "Power BI", "StarDate": "2026-07-15",
            "EndDate": "2026-07-15", "StartTime": "09:00", "EndTime": "17:00",
        }]},
        "rc_schedule": {"rows": []},
        "assignments": [{"AssignmentID": "UNDATED", "Course": "Old course"}],
    }]
    from datetime import datetime, timezone
    now = datetime(2026, 7, 15, 12, 0, tzinfo=timezone.utc)
    batches = build_batch_engagement_rows(fetched, now=now)
    states = build_trainer_current_states(fetched, batches, now=now)
    assert_true(states[0]["current_status"] == "teaching_now", "dated overlap should mean teaching now")
    assert_true(states[0]["current_batch"]["course_name"] == "Power BI", "current batch should come from dated evidence")

    fetched[0]["prev_upcoming"] = {"rows": [{
        "AssignmentId": "A-2", "Course": "Night delivery", "StarDate": "15-Jul-2026",
        "EndDate": "15-Jul-2026", "StartTime": "21:30", "EndTime": "05:30",
    }]}
    overnight_now = datetime(2026, 7, 15, 22, 0, tzinfo=timezone.utc)
    overnight = build_batch_engagement_rows(fetched, now=overnight_now)
    assert_true(overnight[0]["engagement_state"] == "current", "RMS DD-Mon-YYYY overnight batch should be current")
    assert_true(overnight[0]["end_at"].startswith("2026-07-16T05:30"), "overnight end must roll to the next day")

    fetched[0]["prev_upcoming"] = {"rows": []}
    batches = build_batch_engagement_rows(fetched, now=now)
    states = build_trainer_current_states(fetched, batches, now=now)
    assert_true(states[0]["current_status"] == "unknown", "undated assignment index must not imply a current batch")


def test_reference_and_demand_normalization():
    courses = build_course_master(
        [{"Courseid": 10, "Course": "Power BI", "vendor_name": "Microsoft"}],
        detail_rows=[{"Cid": 10, "Course": "Power BI", "course_code": "PL-300", "TOC": "DAX and Power Query"}],
        technology_rows=[{"course_id": 10, "course_name": "Power BI", "technology_name": "Analytics"}],
        exam_policy_rows=[{"Courseid": 10, "CName": "Power BI", "Exam Required or Not": "Yes"}],
    )
    assert_true(len(courses) == 1, "course sources should join by course id")
    assert_true(courses[0]["technologies"] == ["Analytics"], "technology edge should be retained")
    assert_true(courses[0]["exam_required"] is True, "exam policy should be normalized")
    demands = build_unallocated_demand([{"AssignmentID": "D1", "CourseID": 10, "Course": "Power BI", "StartDate": "2026-08-01"}])
    assert_true(demands[0]["demand_id"] == "D1", "unallocated demand should retain stable id")


def test_feedback_facts_and_summary():
    fetched = [{"email": "asha@example.com", "trainer": {"TrainerName": "Asha"}, "feedback_details": [
        {"AssignmentId": "A1", "FeedBackDate": "2026-07-01", "Question": "Was the explanation clear?", "TextAnswer": "Excellent and very clear"},
        {"AssignmentId": "A1", "FeedBackDate": "2026-07-01", "Question": "How was the pace?", "TextAnswer": "Too fast, please improve"},
    ]}]
    facts = build_feedback_facts(fetched)
    assert_true(len(facts) == 2, "detailed feedback should become question-level facts")
    summaries = build_feedback_summaries(facts, [{"trainer_name": "Asha", "official_email": "asha@example.com"}])
    assert_true(summaries[0]["feedback_record_count"] == 2, "summary should count detailed evidence")
    assert_true("pace" in summaries[0]["improvement_themes"], "negative pace feedback should become an improvement theme")


def test_local_ml_toc_similarity():
    trainers = sample_trainers()
    scores = score_trainers_for_toc("Power BI DAX Power Query SQL dashboard", trainers)
    assert_true(scores[0]["similarity"] > scores[1]["similarity"], "relevant trainer should rank higher by local TF-IDF")
    assert_true("power bi" in scores[0]["matched_terms"], "ML result should expose contributing terms")


def main():
    tests = [
        test_no_outline,
        test_outline_provided_and_missing_trainer_fields,
        test_empty_datasets,
        test_other_decision_contracts_and_deduplication,
        test_trainer_decision_backend_extraction,
        test_current_state_requires_dated_evidence,
        test_reference_and_demand_normalization,
        test_feedback_facts_and_summary,
        test_local_ml_toc_similarity,
    ]
    for test in tests:
        test()
        print(f"[OK] {test.__name__}")
    print("[OK] decision object contract tests passed")
    return 0


if __name__ == "__main__":
    sys.exit(main())
