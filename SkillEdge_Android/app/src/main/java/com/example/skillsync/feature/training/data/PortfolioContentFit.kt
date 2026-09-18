package com.example.skillsync.feature.training.data

/**
 * Deterministic content overlap between one demand course and a trainer's real
 * skill portfolio (held course titles + certification names from the manager's
 * `api/v2/capability/portfolio` payload).
 *
 * WHY THIS EXISTS, AND WHAT IT IS NOT
 * -----------------------------------
 * RMS matches trainers to a demand by exact course name, so a team that cleared
 * `PL-300 Power BI Data Analyst` can return zero results for a *custom* Power BI
 * course even though it holds exactly the content. This object answers the
 * manager's question — "who in my team has the content?" — by comparing the
 * demand's vocabulary against the team's total held skills, so the Power BI
 * trainers still surface.
 *
 * The verdict is derived from titles only and is presented as overlap, never as
 * eligibility. The gated allocation (`AllocationCandidatesResponse`) stays
 * authoritative: a high content overlap never claims the trainer can deliver
 * this course, holds this exact skill, or is certified for it.
 *
 * Matching is deterministic and cheap (token set intersection). A match is
 * "strong" when the demand vocabulary is substantially covered, two or more
 * tokens overlap, or a course code (e.g. `PL-300` → `pl300`) lines up exactly —
 * codes are never wildcarded, so a mistyped demand identifier cannot pretend to
 * match a similar-looking skill.
 */
object PortfolioContentFit {

    /** Structural/generic words that say nothing about which technology a course covers. */
    private val STOPWORDS = setOf(
        "course", "courses", "training", "trainings", "class", "classes",
        "learn", "learning", "teach", "teaching", "deliver", "delivery",
        "implement", "implementing", "implementation", "design", "designing",
        "develop", "developing", "development",
        "introduction", "intro", "fundamentals", "foundation", "foundations",
        "basics", "basic", "essential", "essentials", "concepts", "core",
        "advanced", "level", "levels", "using", "with", "through", "based",
        "microsoft", "official",
        "certified", "certification", "associate", "expert", "professional",
        "specialist", "specialty", "administering", "administration",
        "the", "and", "for", "with", "from", "of", "to", "in", "on", "at",
        "an", "a", "is", "it", "its", "this", "that", "these", "those",
        "your", "our", "by", "as", "or", "be", "are", "was", "were",
    )

    private const val MIN_TOKEN_LEN = 2
    private const val COVERAGE_THRESHOLD = 0.5

    data class ContentFit(
        /** Demand vocabulary present in the trainer's held skills, e.g. ["power", "bi"]. */
        val matchedTokens: List<String>,
        /** The held course/cert titles the matched tokens came from, up to three. */
        val heldTitles: List<String>,
        /** Fraction of the demand's strong vocabulary the trainer's portfolio covers (0.0..1.0). */
        val coverage: Double,
        /** True only when overlap is substantial enough to mention. */
        val strong: Boolean,
    ) {
        companion object {
            val NONE = ContentFit(emptyList(), emptyList(), 0.0, false)
        }
    }

    /**
     * @param demandCourseName real course title from the batch record
     * @param demandCourseId   real course id/code from the batch record
     * @param heldTitles       the trainer's portfolio scope — held course titles,
     *                         certification names and certification codes. Empty
     *                         held portfolio means no overlap, never "unsure".
     */
    fun fit(
        demandCourseName: String,
        demandCourseId: String,
        heldTitles: List<String>,
    ): ContentFit {
        val demandTokens = strongTokens("$demandCourseName $demandCourseId")
        if (demandTokens.isEmpty() || heldTitles.none { it.isNotBlank() }) return ContentFit.NONE

        val heldSources = heldTitles.filter { it.isNotBlank() }.distinct()
        val heldTokens = strongTokens(heldSources.joinToString(" "))
        if (heldTokens.isEmpty()) return ContentFit.NONE

        val matched = demandTokens.intersect(heldTokens).sorted().toList()
        if (matched.isEmpty()) return ContentFit.NONE

        val sourceTitles = heldSources.filter { title ->
            strongTokens(title).any { matched.contains(it) }
        }.take(3)

        val coverage = matched.size.toDouble() / demandTokens.size
        // The demand's own course code (joined form, e.g. `PL-300` → `pl300`)
        // appearing in the trainer's portfolio is the single strongest signal.
        val codeMatch = strongTokens(demandCourseId).any { matched.contains(it) }

        val strong = codeMatch || matched.size >= 2 || coverage >= COVERAGE_THRESHOLD

        return ContentFit(matched, sourceTitles, coverage, strong)
    }

    /**
     * Lowercased word tokens plus the joined form of hyphenated codes
     * (`PL-300` → `pl300`). Bare numbers are dropped unless they are part of a
     * joined code, so a "300-level" demand cannot match a "DP-300" skill by
     * their shared digit alone.
     */
    internal fun strongTokens(text: String): Set<String> {
        val raw = text.lowercase()
        val words = raw.split(Regex("[^0-9a-z]+")).filter { it.isNotBlank() }
        val cleaned = words.filter { token -> token.length >= MIN_TOKEN_LEN && token.any { it.isLetter() } }
        val meaningful = cleaned.filter { it !in STOPWORDS }

        val codes = raw.split(Regex("[^0-9a-z]+")).let { parts ->
            parts.indices.mapNotNull { i ->
                val a = parts.getOrNull(i)
                val b = parts.getOrNull(i + 1)
                val c = parts.getOrNull(i + 2)
                val code = when {
                    a != null && b != null && a.any { it.isLetter() } && b.any { it.isDigit() } -> a + b
                    a != null && b != null && a.any { it.isDigit() } && b.any { it.isLetter() } -> a + b
                    a != null && b != null && c != null &&
                        a.any { it.isLetter() } && b.any { it.isDigit() } && c.any { it.isLetter() } -> a + b + c
                    else -> null
                }
                code?.takeIf { it.length >= MIN_TOKEN_LEN }
            }
        }.toSet()

        return (meaningful + codes).toSet()
    }
}