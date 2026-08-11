package com.example.skillsync.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The agent's reasoning, its refusals, and the learning loop.
 *
 * The refusal tests matter as much as the answer tests: an agent with no
 * language model must be provably unable to produce confident prose for a
 * question it did not understand.
 */
class AgentTest {

    private val flagged = TrainerFact(
        email = "divya@koenig-solutions.com", name = "Divya Nair",
        utilisation = 55, capacityBucket = "Balanced", feedbackRisk = "High",
        currentCourse = "AZ-305 Azure Infrastructure", readiness = 62,
    )
    private val stretched = TrainerFact(
        email = "abhinav@koenig-solutions.com", name = "Abhinav Samant",
        utilisation = 95, capacityBucket = "Stretched", readiness = 88,
        utilisationHistory = listOf(70, 80, 95), teachableCourses = listOf("PL-300 Power BI"),
    )
    private val benched = TrainerFact(
        email = "beena@koenig-solutions.com", name = "Beena Rao",
        utilisation = 30, capacityBucket = "On Bench", currentStatus = "free",
        candidateFor = listOf(DemandMatch("264455", "Cisco NSO", 92, "2026-08-24", "FMAT")),
        teachableCourses = listOf("Cisco NSO"),
    )
    private val gapped = TrainerFact(
        email = "chetan@koenig-solutions.com", name = "Chetan Iyer",
        utilisation = 61, capacityBucket = "Balanced",
        certGaps = listOf("Azure AI Engineer Associate"), coveragePct = 60,
        teachableCourses = listOf("AI-102 Azure AI"),
    )
    private val healthy = TrainerFact(
        email = "farhan@koenig-solutions.com", name = "Farhan Ali",
        utilisation = 76, capacityBucket = "Balanced", readiness = 80,
        certsHeld = listOf("PL-300"), teachableCourses = listOf("PL-300 Power BI"),
    )

    private val team = TeamFact(
        trainers = listOf(flagged, stretched, benched, gapped, healthy),
        avgUtilisation = 63,
        readinessScore = 74,
        utilisationHistory = listOf(58, 61, 63),
        unallocated = listOf(DemandMatch("264455", "Cisco NSO", 0, "2026-08-24", "FMAT")),
        activeDeliveries = 3,
        upcomingDeliveries = 4,
    )

    // ── Recommendations ─────────────────────────────────────────────────────

    @Test
    fun aFlaggedTrainerOutranksEveryOtherSignal() {
        val top = Recommender.forTeam(team).first()
        assertEquals(SuggestionKind.ADDRESS_FEEDBACK, top.kind)
        assertEquals("Divya Nair", top.subject)
    }

    @Test
    fun everySuggestionCarriesItsEvidence() {
        Recommender.forTeam(team).forEach {
            assertTrue("No evidence on ${it.kind}", it.evidence.isNotEmpty())
            assertTrue("No rationale on ${it.kind}", it.rationale.isNotBlank())
        }
    }

    @Test
    fun aBenchedTrainerWithAMatchIsProposedForThatBatch() {
        val s = Recommender.forTrainer(benched).first { it.kind == SuggestionKind.ALLOCATE_TO_DEMAND }
        assertTrue(s.headline.contains("Cisco NSO"))
        assertTrue("Fit score must be shown", s.evidence.any { it.contains("92") })
    }

    @Test
    fun aBenchedTrainerWithNoMatchGetsASkillTargetInstead() {
        val alone = benched.copy(candidateFor = emptyList())
        val kinds = Recommender.forTrainer(alone).map { it.kind }
        assertTrue(SuggestionKind.BUILD_BENCH_SKILL in kinds)
        assertFalse(SuggestionKind.ALLOCATE_TO_DEMAND in kinds)
    }

    /** The agent must be able to bring good news, not only problems. */
    @Test
    fun aHealthyTrainerProducesRecognitionRatherThanSilence() {
        val kinds = Recommender.forTrainer(healthy).map { it.kind }
        assertEquals(listOf(SuggestionKind.RECOGNISE), kinds)
    }

    @Test
    fun aSingleOwnerCourseIsFlaggedAsKeyPersonRisk() {
        val risks = Recommender.forTeam(team).filter { it.kind == SuggestionKind.COVER_KEY_PERSON_RISK }
        assertTrue("Cisco NSO has exactly one owner", risks.any { it.subject == "Cisco NSO" })
        // PL-300 has two owners and must not be flagged.
        assertFalse(risks.any { it.subject.contains("PL-300") })
    }

    // ── Question answering ──────────────────────────────────────────────────

    @Test
    fun itAnswersWhoNeedsAttention() {
        val a = Agent.ask("Who needs my attention?", team)
        assertEquals(Intent.WHO_NEEDS_ATTENTION, a.intent)
        assertTrue(a.suggestions.isNotEmpty())
        assertTrue(a.detail.contains("Divya"))
    }

    @Test
    fun itAnswersWhoIsAvailable() {
        val a = Agent.ask("who is available right now", team)
        assertEquals(Intent.WHO_IS_AVAILABLE, a.intent)
        assertTrue(a.headline.contains("Beena Rao"))
    }

    @Test
    fun itResolvesATrainerByFirstOrFullName() {
        assertEquals(Intent.TRAINER_STATUS, Agent.ask("How is Divya doing?", team).intent)
        assertEquals(Intent.TRAINER_STATUS, Agent.ask("tell me about Abhinav Samant", team).intent)
        assertTrue(Agent.ask("How is Divya doing?", team).headline.contains("Divya Nair"))
    }

    @Test
    fun askingWhatToDoAboutSomeoneGivesTheNextStepNotAStatus() {
        val a = Agent.ask("what should I do about Chetan?", team)
        assertEquals(Intent.TRAINER_NEXT_STEP, a.intent)
        assertTrue(a.headline.contains("Azure AI Engineer Associate"))
    }

    @Test
    fun itAnswersWhoCanTeachACourse() {
        val a = Agent.ask("who can teach PL-300 Power BI?", team)
        assertEquals(Intent.WHO_CAN_TEACH, a.intent)
        assertTrue(a.headline.contains("2"))
    }

    @Test
    fun itReportsTeamHealthAgainstTheTargetBand() {
        val a = Agent.ask("how is the team doing?", team)
        assertEquals(Intent.TEAM_HEALTH, a.intent)
        assertTrue(a.headline.contains("below the target band"))
    }

    @Test
    fun itReportsDemandCoverage() {
        val a = Agent.ask("can we cover the open demand?", team)
        assertEquals(Intent.DEMAND_COVERAGE, a.intent)
        assertTrue(a.headline.contains("international"))
    }

    // ── Refusals ────────────────────────────────────────────────────────────

    @Test
    fun itRefusesAQuestionItHasNoToolFor() {
        val a = Agent.ask("what is the weather in Delhi tomorrow", team)
        assertEquals(Intent.UNKNOWN, a.intent)
        assertEquals(Confidence.LOW, a.confidence)
        assertNotNull("A refusal must explain itself", a.unmet)
    }

    @Test
    fun itRefusesAnUnknownPerson() {
        val a = Agent.ask("how is Nobody McGhost doing", team)
        assertEquals(Confidence.LOW, a.confidence)
    }

    @Test
    fun itDropsConfidenceWhenTheUnderlyingDataIsMissing() {
        val thin = TeamFact(trainers = listOf(flagged.copy(utilisation = null, readiness = null)))
        val a = Agent.ask("how is Divya doing", thin)
        assertEquals(Confidence.MEDIUM, a.confidence)
        assertTrue(a.detail.contains("not been measured"))
    }

    @Test
    fun itSaysWhatIsMissingWhenTheAllocationDeskHasNotLoaded() {
        val noDesk = team.copy(trainers = team.trainers.map { it.copy(candidateFor = emptyList()) })
        val a = Agent.ask("can we cover demand", noDesk)
        assertNotNull(a.unmet)
        assertEquals(Confidence.MEDIUM, a.confidence)
    }

    @Test
    fun starterQuestionsAllResolveToARealIntent() {
        Agent.starters(team).forEach { q ->
            assertFalse("Starter question was not understood: $q", Agent.ask(q, team).intent == Intent.UNKNOWN)
        }
    }

    // ── Learning loop ───────────────────────────────────────────────────────

    @Test
    fun acceptingASuggestionRaisesItsWeightAndDismissingLowersIt() {
        val base = Weights()
        val up = base.record(SuggestionKind.CLOSE_CERT_GAP, accepted = true)
        assertTrue(up.weightFor(SuggestionKind.CLOSE_CERT_GAP) > base.weightFor(SuggestionKind.CLOSE_CERT_GAP))

        val down = base.record(SuggestionKind.CLOSE_CERT_GAP, accepted = false)
        assertTrue(down.weightFor(SuggestionKind.CLOSE_CERT_GAP) < base.weightFor(SuggestionKind.CLOSE_CERT_GAP))
    }

    @Test
    fun weightsStayInsideTheirBandUnderRepeatedFeedback() {
        var w = Weights()
        repeat(200) { w = w.record(SuggestionKind.RECOGNISE, accepted = true) }
        assertTrue(w.weightFor(SuggestionKind.RECOGNISE) <= Weights.MAX_WEIGHT)

        var d = Weights()
        repeat(200) { d = d.record(SuggestionKind.RECOGNISE, accepted = false) }
        assertTrue(d.weightFor(SuggestionKind.RECOGNISE) >= Weights.MIN_WEIGHT)
    }

    @Test
    fun theModelIsRenormalisedSoTheScaleDoesNotDrift() {
        var w = Weights()
        repeat(50) { w = w.record(SuggestionKind.ALLOCATE_TO_DEMAND, accepted = true) }
        val mean = w.values.values.average()
        assertEquals("Mean weight must stay at 1.0", 1.0, mean, 0.05)
    }

    @Test
    fun learningActuallyReordersTheQueue() {
        // Certification gaps start below a stretched trainer. Repeatedly telling
        // the agent that gaps matter must move them up.
        val neutral = Recommender.forTrainer(gapped.copy(capacityBucket = "Stretched", utilisation = 95))
        val gapFirstBefore = neutral.first().kind == SuggestionKind.CLOSE_CERT_GAP

        var w = Weights()
        repeat(12) { w = w.record(SuggestionKind.CLOSE_CERT_GAP, accepted = true) }
        repeat(12) { w = w.record(SuggestionKind.REBALANCE_LOAD, accepted = false) }
        val trained = Recommender.forTrainer(gapped.copy(capacityBucket = "Stretched", utilisation = 95), w)

        assertFalse("Precondition: gap should not already lead", gapFirstBefore)
        assertEquals(SuggestionKind.CLOSE_CERT_GAP, trained.first().kind)
    }

    @Test
    fun theModelReportsWhetherItHasEnoughEvidenceToBeTrusted() {
        var w = Weights()
        assertFalse(w.isTrained)
        repeat(Weights.MIN_EVENTS_TO_TRUST) { w = w.record(SuggestionKind.RECOGNISE, true) }
        assertTrue(w.isTrained)
        assertEquals(Weights.MIN_EVENTS_TO_TRUST, w.events)
    }

    @Test
    fun aStoredModelSurvivesANewSuggestionKindBeingAdded() {
        // Simulates upgrading the app: the file was written before a kind existed.
        val partial = LearningStore.Stored(
            values = mapOf(SuggestionKind.RECOGNISE.name to 1.7),
            version = 4, events = 9,
        )
        val restored = partial.toWeights()
        assertEquals(1.7, restored.weightFor(SuggestionKind.RECOGNISE), 0.001)
        // Every other kind is present and neutral rather than missing.
        assertEquals(SuggestionKind.entries.size, restored.values.size)
        assertEquals(1.0, restored.weightFor(SuggestionKind.ADDRESS_FEEDBACK), 0.001)
    }

    // ── Fact fusion ─────────────────────────────────────────────────────────

    @Test
    fun unmeasuredIsNotTheSameAsZero() {
        val dashboard = mapOf<String, Any>(
            "trainer_operations_df" to listOf(
                mapOf("official_email" to "a@x.com", "trainer_name" to "A", "capacity_bucket" to "Balanced"),
                mapOf("official_email" to "b@x.com", "trainer_name" to "B", "current_utilization" to 0.0),
            )
        )
        val facts = FactBuilder.build(dashboard)
        assertNull("Missing utilisation must stay null", facts.trainers[0].utilisation)
        assertEquals("A real zero must survive", 0, facts.trainers[1].utilisation)
    }

    @Test
    fun allocationCandidatesAreInvertedOntoTheTrainer() {
        val dashboard = mapOf<String, Any>(
            "trainer_operations_df" to listOf(
                mapOf("official_email" to "a@x.com", "trainer_name" to "A")
            )
        )
        val allocation = mapOf<String, Any>(
            "batches" to listOf(
                mapOf(
                    "demand_id" to "1", "course_name" to "Cisco NSO", "delivery_mode" to "FMAT",
                    "candidates" to listOf(mapOf("trainer_email" to "a@x.com", "relevance" to 88.0)),
                )
            )
        )
        val facts = FactBuilder.build(dashboard, allocation = allocation)
        val t = facts.trainers.first()
        assertEquals(1, t.candidateFor.size)
        assertEquals(88, t.candidateFor.first().relevance)
        assertTrue(t.candidateFor.first().isInternational)
    }
}
