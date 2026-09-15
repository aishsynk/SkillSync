import unittest
import backend


class MatchScoreTaxonomyFallbackTests(unittest.TestCase):
    """_match_score's text/code matching is unchanged by default (taxonomy
    omitted); the taxonomy fallback only ever rescues a genuine 0, never
    overrides a real textual match. Regression coverage for the
    "PL-300 must map to its Power BI capability, not the literal course
    title" requirement, using the real _course_taxonomy() shape
    ({key: {"technology": ..., "domain": ...}}, keyed by "id:<course_id>"
    or normalised course name) without fabricating RMS wiring."""

    def setUp(self):
        self.taxonomy = {
            "id:9100": {"technology": "Power BI", "domain": "Data Analytics"},
            "id:9200": {"technology": "Azure Administration", "domain": "Cloud"},
            # Keyed by backend._norm_course() of the capability row's course
            # title -- the only lookup available for trainer capability rows,
            # which never carry a course_id (confirmed: RMS trainerDetails
            # only returns CourseName).
            "vizify enterprise dashboards": {"technology": "Power BI", "domain": "Data Analytics"},
            "cloudops resource governance": {"technology": "Azure Administration", "domain": "Cloud"},
        }

    def test_exact_title_match_wins_without_needing_taxonomy(self):
        score = backend._match_score("PL-300T00", "Microsoft", "PL-300T00", "Microsoft",
                                     taxonomy=self.taxonomy, batch_course_id="9100")
        self.assertEqual(100, score)

    def test_same_technology_different_title_scores_via_taxonomy_fallback(self):
        # "PL-300" (by id) and a completely differently-named internal course
        # ("Vizify Enterprise Dashboards" -- zero shared tokens with "PL-300")
        # that nonetheless resolves to the same RMS technology, Power BI.
        score = backend._match_score(
            "PL-300", "Microsoft", "Vizify Enterprise Dashboards", "Contoso",
            taxonomy=self.taxonomy, batch_course_id="9100",
        )
        self.assertEqual(60, score, "same-technology courses with zero title overlap must not score 0")

    def test_different_technology_still_scores_zero(self):
        score = backend._match_score(
            "PL-300", "Microsoft", "CloudOps Resource Governance", "Contoso",
            taxonomy=self.taxonomy, batch_course_id="9100",
        )
        self.assertEqual(0, score)

    def test_taxonomy_fallback_never_overrides_a_real_token_match(self):
        with_taxonomy = backend._match_score(
            "Introduction to Power BI", "Microsoft", "Power BI Introduction Workshop", "Microsoft",
            taxonomy=self.taxonomy, batch_course_id="9100",
        )
        without_taxonomy = backend._match_score(
            "Introduction to Power BI", "Microsoft", "Power BI Introduction Workshop", "Microsoft",
        )
        self.assertEqual(with_taxonomy, without_taxonomy, "a real token match must not be altered by taxonomy")
        self.assertGreater(with_taxonomy, 0)

    def test_omitting_taxonomy_preserves_exact_prior_text_only_behaviour(self):
        score = backend._match_score(
            "PL-300", "Microsoft", "Power BI Data Analyst Fundamentals Workshop", "Microsoft",
        )  # no taxonomy passed at all
        self.assertEqual(0, score, "without a taxonomy, unrelated titles must still score 0 (old behaviour)")

    def test_missing_taxonomy_entry_for_either_course_scores_zero_not_a_guess(self):
        score = backend._match_score(
            "Quantum Widgetry Basics", "Vendor X", "Legacy Sprocket Engineering", "Vendor Y",
            taxonomy=self.taxonomy, batch_course_id="9999",
        )
        self.assertEqual(0, score)


class RankBatchTaxonomyIntegrationTests(unittest.TestCase):
    """Confirms _rank_batch actually threads its optional taxonomy param and
    the batch's course_id through to _match_score, end-to-end through the
    real ranking function (not just the helper)."""

    def test_rank_batch_surfaces_a_same_technology_candidate_via_taxonomy(self):
        taxonomy = {
            "id:9100": {"technology": "Power BI", "domain": "Data Analytics"},
            "vizify enterprise dashboards": {"technology": "Power BI", "domain": "Data Analytics"},
        }
        batch = {
            "course_name": "PL-300",
            "course_id": "9100",
            "customer": "Acme Corp",
            "start_date": "2026-09-01",
            "end_date": "2026-09-05",
        }
        team = [(
            "Test Trainer", "trainer@koenig-solutions.com",
            [{"course": "Vizify Enterprise Dashboards", "vendor": "Contoso",
              "qubits_score": 80, "skill_level": "8"}],
            {"blocked": False, "blocked_until": None, "recent_negative_6mo": False},
            False,
        )]

        relevance, candidates, coverage = backend._rank_batch(batch, team, taxonomy=taxonomy)

        self.assertEqual(60, relevance)
        self.assertEqual(1, len(candidates))
        self.assertNotEqual("No Coverage", coverage)

    def test_rank_batch_without_taxonomy_finds_no_coverage_for_the_same_pair(self):
        batch = {
            "course_name": "PL-300",
            "course_id": "9100",
            "customer": "Acme Corp",
            "start_date": "2026-09-01",
            "end_date": "2026-09-05",
        }
        team = [(
            "Test Trainer", "trainer@koenig-solutions.com",
            [{"course": "Vizify Enterprise Dashboards", "vendor": "Contoso",
              "qubits_score": 80, "skill_level": "8"}],
            {"blocked": False, "blocked_until": None, "recent_negative_6mo": False},
            False,
        )]

        relevance, candidates, coverage = backend._rank_batch(batch, team)  # taxonomy omitted

        self.assertEqual(0, relevance)
        self.assertEqual([], candidates)
        self.assertEqual("No Coverage", coverage)


if __name__ == "__main__":
    unittest.main()
