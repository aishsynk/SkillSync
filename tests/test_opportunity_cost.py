"""
The dashboard 'opportunity cost' block: how much open demand the team could
cover but isn't. Pins the counting and the trainer-days-at-stake maths for
`backend._team_opportunity_cost` on mocked inputs (no RMS).
"""
import unittest

import backend


def trainer(courses, vendors=None, on_bench=False):
    return {"email": "t@x", "courses": courses,
            "vendors": vendors or [], "on_bench": on_bench}


def demand(course_name, start=None, end=None):
    d = {"course_name": course_name}
    if start:
        d["start_date"] = start
    if end:
        d["end_date"] = end
    return d


class OpportunityCostCounts(unittest.TestCase):

    def test_coverable_vs_total_and_days_at_stake(self):
        team = [trainer(["AZ-104: Azure Administrator", "Kubernetes Fundamentals"])]
        rows = [
            demand("AZ-104: Azure Administrator", "2026-09-01", "2026-09-04"),  # covered, 4d
            demand("Kubernetes Fundamentals"),                                   # covered, default 3d
            demand("SAP S/4HANA Finance"),                                       # not covered
        ]
        out = backend._team_opportunity_cost(team, rows)
        self.assertEqual(out["open_batches_total"], 3)
        self.assertEqual(out["open_batches_coverable"], 2)
        self.assertEqual(out["trainer_days_at_stake"], 7)
        self.assertEqual(len(out["top_courses"]), 2)

    def test_distinct_batches_are_deduped_by_course(self):
        team = [trainer(["Docker Deep Dive"])]
        rows = [demand("Docker Deep Dive"), demand("docker deep dive"),
                demand("Unrelated Course")]
        out = backend._team_opportunity_cost(team, rows)
        self.assertEqual(out["open_batches_total"], 2)
        self.assertEqual(out["open_batches_coverable"], 1)
        self.assertEqual(out["trainer_days_at_stake"], 3)

    def test_skill_gap_attribution_within_team_code_space(self):
        # Team owns an AZ-xxx course; an uncovered AZ-500 batch sits in that space.
        team = [trainer(["AZ-104: Azure Administrator"])]
        rows = [demand("AZ-500: Azure Security Engineer"),
                demand("COBOL for the Enterprise")]
        out = backend._team_opportunity_cost(team, rows)
        self.assertEqual(out["open_batches_coverable"], 0)
        self.assertEqual(out["by_cause"]["skill_gap"], 1)
        self.assertEqual(out["by_cause"]["availability"], 0)
        self.assertEqual(out["by_cause"]["certification"], 0)

    def test_empty_inputs(self):
        out = backend._team_opportunity_cost([], [])
        self.assertEqual(out["open_batches_total"], 0)
        self.assertEqual(out["open_batches_coverable"], 0)
        self.assertEqual(out["trainer_days_at_stake"], 0)


if __name__ == "__main__":
    unittest.main()
