package com.example.skillsync.ai

import com.example.skillsync.data.cache.LocalCache

/**
 * The learning loop.
 *
 * Every time a manager accepts or dismisses a suggestion, that decision is a
 * label: it says this kind of recommendation was, or was not, worth their time
 * in this org. Those labels move the weight for that [SuggestionKind], and the
 * weights re rank everything the agent proposes next.
 *
 * Manager decisions are the highest quality labels available here and they cost
 * nothing to collect, which is why the loop is built on them rather than on a
 * separate rating exercise nobody would complete.
 *
 * Two deliberate limits, both worth stating plainly:
 *
 *  - The model is per device. Weights live in the local cache, so a manager who
 *    reinstalls starts from neutral. Sharing a model across a manager's devices,
 *    or pooling across managers, needs a backend table and is not built.
 *  - It learns ranking, not language. It cannot discover a new kind of
 *    suggestion, only reorder the ones it has. A genuinely new recommendation
 *    still has to be written into [Recommender].
 */
object LearningStore {

    private const val KEY = "ai_weights_v1"

    @Volatile
    private var cached: Weights? = null

    fun load(): Weights {
        cached?.let { return it }
        val stored = runCatching { LocalCache.loadObject(KEY, Stored::class.java) }.getOrNull()
        val weights = stored?.toWeights() ?: Weights()
        cached = weights
        return weights
    }

    /** Records one manager decision and returns the updated model. */
    fun record(kind: SuggestionKind, accepted: Boolean): Weights {
        val updated = load().record(kind, accepted)
        cached = updated
        runCatching { LocalCache.saveObject(KEY, Stored.from(updated)) }
        return updated
    }

    /** Wipes the learned model back to neutral. */
    fun reset(): Weights {
        val fresh = Weights()
        cached = fresh
        runCatching { LocalCache.saveObject(KEY, Stored.from(fresh)) }
        return fresh
    }

    /**
     * Gson needs a concrete class with stable field names; [Weights] is a
     * domain type and should stay free to change shape without silently
     * failing to deserialise an older file.
     */
    internal data class Stored(
        val values: Map<String, Double> = emptyMap(),
        val version: Int = 0,
        val events: Int = 0,
    ) {
        fun toWeights(): Weights {
            // Drop any key that no longer maps to a known kind, and fill in any
            // kind added since the file was written. Without this, adding a
            // suggestion type would leave it permanently unweighted.
            val known = SuggestionKind.entries.associate { kind ->
                kind.name to (values[kind.name] ?: 1.0)
                    .coerceIn(Weights.MIN_WEIGHT, Weights.MAX_WEIGHT)
            }
            return Weights(known, version, events)
        }

        companion object {
            fun from(w: Weights) = Stored(w.values, w.version, w.events)
        }
    }
}
