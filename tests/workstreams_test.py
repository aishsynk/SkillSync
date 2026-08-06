"""Regression tests for the three SkillEdge workstreams.

1. Data alignment + team-size calibration
   - build_unallocated_demand drops blank rows and keeps the customer field
   - _upgrade_availability_engine caps confidence on contradictions and
     downgrades "Busy but Strong Candidate" when the calendar is unknown
   - build_organization_intelligence labels small-team SPOF as "Thin Bench"
2. Non-blocking auth is exercised by smoke_test; here we verify the agent
   endpoints require a session (covered by agent e2e) and lifecycle actions
   record learning feedback.
3. Agentic layer: intent classification, tool dispatch, and the learning loop.

Run:  python tests/workstreams_test.py
"""

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
for path in (ROOT, ROOT / "backend"):
    if str(path) not in sys.path:
        sys.path.insert(0, str(path))

from services.reference_data_service import build_unallocated_demand
from intelligence import _upgrade_availability_engine
from shared.organization_intelligence import build_organization_intelligence
from agentic import agent, learning


def assert_true(condition, message):
    if not condition:
        raise AssertionError(message)


# ── Workstream 1 ──────────────────────────────────────────────────────────────
def test_demand_drops_blank_rows():
    raw = [
        {"Coursename": "Azure AI", "CourseSDate": "2026-09-01", "Customer": "Acme"},
        {"AssignmentID": "", "Coursename": "", "CourseSDate": None},  # all-blank → drop
        {"AssignmentID": "D-7", "CourseSDate": "2026-10-01"},          # only an id → keep
        {"CourseSDate": "2026-11-01", "Coursename": "Power BI"},       # no customer → keep
    ]
    out = build_unallocated_demand(raw)
    assert_true(len(out) == 3, f"blank demand row must be dropped, got {len(out)}")
    ids = [d.get("demand_id") for d in out]
    assert_true("unallocated:1" not in ids, "blank row must not be synthesised")
    assert_true(out[0]["customer"] == "Acme", "customer field must be carried through")
    assert_true(out[2]["customer"] is None, "customer may be absent but row stays")


def test_availability_contradiction_caps_confidence():
    rows = [
        {
            "trainer_name": "Rohan S",
            "calendar_status": "Busy",
            "capacity_status": "Underused",
            "availability_confidence": 100,
            "final_availability_status": "Busy but Strong Candidate",
            "upcoming_assignment_count": 1,
        },
        {
            "trainer_name": "Asha T",
            "calendar_status": "Unknown",
            "capacity_status": "Underused",
            "availability_confidence": 90,
            "final_availability_status": "Busy but Strong Candidate",
        },
    ]
    out = _upgrade_availability_engine(rows)
    by_name = {r["trainer_name"]: r for r in out}
    r1 = by_name["Rohan S"]
    assert_true(r1["availability_confidence"] == 60, f"conflict must cap at 60, got {r1['availability_confidence']}")
    assert_true(r1.get("contradictions"), "conflicting signals must surface contradictions")
    assert_true("conflicting signals" in r1["confidence_reason"], "confidence_reason must explain the cap")
    r2 = by_name["Asha T"]
    assert_true(r2["final_availability_status"] == "Available but Needs Prep",
                "Busy without calendar evidence must downgrade")
    assert_true(r2["availability_confidence"] <= 60, "unknown-calendar case must still be capped")


def test_small_team_spof_calibration():
    trainers = [
        {"trainer_name": "A One", "official_email": "a@x.com", "trainer_key": "a@x.com"},
        {"trainer_name": "B Two", "official_email": "b@x.com", "trainer_key": "b@x.com"},
    ]
    allocation = [
        {"course_id": "C1", "course_name": "Power BI", "trainer_name": "A One", "trainer_email": "a@x.com",
         "allocation_score": 85, "confidence": 90, "blocker": None, "allocation_risk": "Low"},
        {"course_id": "C1", "course_name": "Power BI", "trainer_name": "B Two", "trainer_email": "b@x.com",
         "allocation_score": 40, "confidence": 70, "blocker": None, "allocation_risk": "Low"},
    ]
    result = build_organization_intelligence(
        trainer_rows=trainers,
        course_allocation_rows=allocation,
        allocation_intelligence_rows=allocation,
        delivery_rows=[],
        vendor_strength_rows=[],
        certification_rows=[],
        oem_heatmap_rows=[],
        trainer_count=2,
    )
    spof = result.get("single_point_failure_df") or []
    assert_true(spof, "single-capable course should be reported")
    row = spof[0]
    assert_true(row["coverage_status"] == "Thin Bench", f"small team must read Thin Bench, got {row['coverage_status']}")
    assert_true(row["risk_level"] == "Medium", f"small-team single coverage must be Medium risk, got {row['risk_level']}")
    assert_true(row["single_point_failure"] is False, "small team must not be labelled a hard SPOF crisis")
    assert_true("small team" in row["recommendation"].get("reason", ""), "reason must explain the calibration")


def test_large_team_still_spof():
    trainers = [{"trainer_name": f"T{i}", "official_email": f"t{i}@x.com", "trainer_key": f"t{i}@x.com"} for i in range(6)]
    allocation = []
    for i in range(6):
        allocation.append({"course_id": "C1", "course_name": "Power BI", "trainer_name": f"T{i}",
                           "trainer_email": f"t{i}@x.com", "allocation_score": 80 if i == 0 else 30,
                           "confidence": 90, "blocker": None, "allocation_risk": "Low"})
    result = build_organization_intelligence(
        trainer_rows=trainers,
        course_allocation_rows=allocation,
        allocation_intelligence_rows=allocation,
        delivery_rows=[],
        vendor_strength_rows=[],
        certification_rows=[],
        oem_heatmap_rows=[],
        trainer_count=6,
    )
    row = (result.get("single_point_failure_df") or [])[0]
    assert_true(row["coverage_status"] == "Single Point of Failure",
                f"large team single coverage must stay SPOF, got {row['coverage_status']}")
    assert_true(row["risk_level"] == "High", "large-team single coverage must stay High risk")


# ── Workstream 3: agent intents ───────────────────────────────────────────────
def sample_ctx():
    return {
        "trainer_operations_df": [
            {"trainer_key": "a@x.com", "trainer_name": "Abhinav Samant", "trainer_email": "a@x.com",
             "official_email": "a@x.com", "designation": "Corporate Trainer", "overall_readiness_score": 67,
             "readiness_bucket": "Can Deliver with Prep", "current_utilization": 52,
             "availability_status": "Limited", "recommended_action": "Coach", "certificate_count": 1,
             "negative_feedback_count": 0, "classification": {"badge_label": "Ready"},
             "current_engagement": {"current_status": "preparing", "next_batch": {"course_name": "Power BI"}}},
            {"trainer_key": "b@x.com", "trainer_name": "Niharika Niharika", "trainer_email": "b@x.com",
             "official_email": "b@x.com", "designation": "Corporate Trainer", "overall_readiness_score": 66,
             "readiness_bucket": "Can Deliver with Prep", "current_utilization": 25,
             "availability_status": "Available", "recommended_action": "Book Mock", "certificate_count": 1,
             "negative_feedback_count": 0, "classification": {"badge_label": "Ready"},
             "current_engagement": {"current_status": "preparing", "next_batch": {"course_name": "Alteryx"}}},
        ],
        "trainer_decision_objects": [
            {"trainer_email": "a@x.com", "trainer_name": "Abhinav Samant", "status": "review",
             "assignment_status": "review", "primary_blocker": "High allocation risk"},
        ],
        "course_best_trainer_df": [
            {"course_name": "Power BI", "best_trainer": "Abhinav Samant", "trainer_email": "a@x.com",
             "allocation_score": 82, "reason": "readiness+evidence"},
        ],
        "trainer_backup_df": [
            {"course_name": "Power BI", "primary_trainer_name": "Abhinav Samant",
             "backup_trainer_name": "Niharika Niharika", "backup_score": 71, "backup_reason": "adjacent"},
        ],
        "certification_gap_df": [{"trainer_name": "Abhinav Samant", "course": "PL-300", "vendor": "Microsoft"}],
        "data_health_df": [{"api_name": "Trainer Skills", "issue_type": "API failed", "severity": "Medium"}],
        "unallocated_demand_df": [{"demand_id": "D1", "course_name": "Azure AI", "start_date": "2026-09-01"}],
        "oem_bench_risk_df": [{"vendor": "Microsoft", "bench_risk": "Medium", "trainer_count": 2}],
        "manager_action_objects": [
            {"id": "manager_action:1", "action_type": "Coach", "trainer_name": "Abhinav Samant",
             "priority": "Medium", "category": "Coaching", "lifecycle_state": "open"},
        ],
        "course_master_df": [],
        "knowledge_base": {},
        "learning_status": learning.get_status(),
    }


def test_agent_intents():
    ctx = sample_ctx()
    cases = [
        ("how is my team doing", "team", "list_trainers"),
        ("who is free right now", "free", "list_trainers"),
        ("who can teach Power BI", "best", "best_for_course"),
        ("backup for Power BI", "backup", "backups_for_course"),
        ("what blockers exist", "blockers", "list_blockers"),
        ("certification gaps", "certification", "certification_gaps"),
        ("any data issues", "health", "data_health"),
        ("what should I do today", "actions", "list_manager_actions"),
        ("any unallocated demand", "demand", "unallocated_demand"),
        ("how is our OEM bench", "oem", "oem_bench"),
        ("tell me about Abhinav", "team", "get_trainer"),
        ("how is the learning model", "learning", "learning"),
    ]
    for q, intent, tool in cases:
        r = agent.answer(q, ctx)
        assert_true(r["intent"] == intent, f"'{q}' expected intent {intent}, got {r['intent']}")
        assert_true(r["tool_used"] == tool, f"'{q}' expected tool {tool}, got {r['tool_used']}")
        assert_true(r["answer"] and r["confidence"], f"'{q}' must return an answer with confidence")
    print("[OK] agent intents dispatch correctly")


def test_agent_trainer_lookup_by_first_name():
    ctx = sample_ctx()
    r = agent.answer("tell me about abhinav", ctx)
    assert_true("Abhinav Samant" in r["answer"], "first-name token must resolve the trainer")
    assert_true("readiness" in r["answer"], "trainer answer must include readiness")


def test_briefing_shape():
    ctx = sample_ctx()
    b = agent.build_briefing(ctx)
    assert_true({"team", "issues", "opportunities", "next_actions", "freshness"} <= set(b),
                "briefing must include team/issues/opportunities/next_actions/freshness")
    assert_true(b["team"]["count"] == 2, "briefing team count must reflect the payload")
    assert_true(any(i["priority"] == "Medium" for i in b["issues"]), "certification gap should surface as an issue")
    assert_true(b["opportunities"], "underutilised demand should surface as an opportunity")


def test_learning_loop():
    before = learning.get_status()
    ex = learning.record_feedback(
        manager_email="manager@x.com",
        entity_type="manager_action",
        entity_id="manager_action:1",
        decision="state:closed",
        outcome="positive",
        note="resolved well",
        features={"qubit": 0.9, "feedback": 0.5, "certs": 0.3},
    )
    assert_true(ex["id"].startswith("fb-"), "feedback example must be persisted with an id")
    after = learning.tune_weights(force=True)
    assert_true(after["version"] >= before["version"] + 1, "tuning must bump the model version")
    status = learning.get_status()
    assert_true(status["sample_count"] >= 1, "status must reflect recorded examples")
    assert_true(abs(sum(status["weights"].values()) - 1.0) < 0.01, "tuned weights must sum to 1")
    assert_true(all(0.03 <= w <= 0.60 for w in status["weights"].values()), "weights must stay in the clamp band")


def main():
    tests = [
        test_demand_drops_blank_rows,
        test_availability_contradiction_caps_confidence,
        test_small_team_spof_calibration,
        test_large_team_still_spof,
        test_agent_intents,
        test_agent_trainer_lookup_by_first_name,
        test_briefing_shape,
        test_learning_loop,
    ]
    for test in tests:
        test()
        print(f"[OK] {test.__name__}")
    print("[OK] workstreams tests passed")
    return 0


if __name__ == "__main__":
    sys.exit(main())
