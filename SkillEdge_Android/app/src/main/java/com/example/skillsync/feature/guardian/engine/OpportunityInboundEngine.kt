package com.example.skillsync.feature.guardian.engine
import com.example.skillsync.feature.guardian.retry.OpportunityRetryWorker
import com.example.skillsync.core.notification.LocalNotificationService

import android.app.Notification
import android.content.Context
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.skillsync.core.data.ManagerRepository
import com.example.skillsync.core.storage.LocalCache
import com.example.skillsync.feature.opportunity.data.TrustedSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar
import androidx.compose.material3.Text

/**
 * The inbound half of Opportunity Guardian.
 *
 * [ViberNotificationListener] hands every posted notification here. This engine:
 *  1. Keeps only Viber notifications and offloads them from the main thread.
 *  2. Loads the manager's guardian config (network, cached) and aborts when the
 *     guardian is disabled.
 *  3. Matches trigger keywords to decide "is this an opportunity at all" and
 *     ranks it normal/high/critical, then honours quiet hours per rank.
 *  4. Dedupes by a message fingerprint so one posted notification becomes at
 *     most one opportunity.
 *  5. Persists the opportunity through [ManagerRepository.createOpportunity].
 *     Network failures are queued locally and retried on the next invocation.
 *
 * Purely additive: nothing here touches the outbound Viber automation path.
 */
object OpportunityInboundEngine {

    private const val TAG = "OpportunityInboundEngine"
    private const val VIBER_PACKAGE = "com.viber.voip"

    private const val PREF_NAME = "guardian_seen"
    private const val SEEN_KEY = "fingerprints"
    private const val SEEN_LIMIT = 300

    private val repository = ManagerRepository()

    /** Built-in default trigger language; the server config can extend this. */
    private val TRIGGER_KEYWORDS = listOf(
        "can anyone deliver", "can you deliver", "availability", "training requirement",
        "trainer needed", "travel opportunity", "international delivery", "rgn delivery",
        "abroad", "overseas", "toc attached", "toc is attached", "requirement", "urgent",
        "last minute", "new batch", "need a trainer", "looking for a trainer",
    )

    private val CRITICAL_HINTS = listOf(
        "urgent", "immediately", "asap", "today", "tomorrow", "critical", "last minute",
    )

    private val HIGH_HINTS = listOf(
        "deliver", "delivery", "course", "training", "batch", "toc", "certification",
    )

    suspend fun process(context: Context, sbn: StatusBarNotification): Boolean {
        if (sbn.packageName != VIBER_PACKAGE) return false
        val manager = com.example.skillsync.core.data.SessionManager.getEmail() ?: return false

        return withContext(Dispatchers.IO) {
            try {
                val config = repository.guardianConfig(manager, fresh = false).data ?: emptyMap()
                if (config["enabled"] != true) return@withContext false

                val message = parse(sbn)
                if (message.isBlank()) return@withContext false

                val sources = matchesTrustedSource(config, sbn, message)
                val hasTrigger = TRIGGER_KEYWORDS.any { message.contains(it, ignoreCase = true) } ||
                    (config["trigger_keywords"] as? List<*>)?.any {
                        message.contains(it.toString(), ignoreCase = true)
                    } == true
                if (!hasTrigger) return@withContext false

                val fingerprint = fingerprint(message, sources)
                if (fingerprint in seenSet(context)) return@withContext false

                val rank = rank(message)
                if (inQuietHours(config, rank)) return@withContext false

                flushPending(context, manager)

                val body = buildOpportunity(manager, sbn, message, sources, rank)
                val created = try {
                    repository.createOpportunity(body)
                } catch (e: Exception) {
                    Log.w(TAG, "createOpportunity failed; queueing pending", e)
                    enqueuePending(context, manager, body)
                    null
                }

                if (created != null) {
                    rememberSeen(context, fingerprint)
                    LocalNotificationService.createNotificationChannel(context)
                    if (rank == "critical") {
                        LocalNotificationService.showEscalation(
                            context,
                            "CRITICAL Opportunity Detected",
                            created["title"]?.toString()
                                ?: "Critical opportunity detected from ${body["source_group"]}",
                        )
                    } else {
                        LocalNotificationService.showNotification(
                            context,
                            rankTitle(rank),
                            created["title"]?.toString()
                                ?: "New ${rank.lowercase()} opportunity detected from ${body["source_group"]}",
                        )
                    }
                    true
                } else {
                    false
                }
            } catch (e: Exception) {
                Log.w(TAG, "inbound opportunity processing failed", e)
                false
            }
        }
    }

    /**
     * Best-effort structured read of a Viber notification. Falls back to raw
     * title/text when the MessagingStyle payload is unavailable, which is the
     * norm for group-summary notifications on Android.
     */
    private fun parse(sbn: StatusBarNotification): String {
        val extras = sbn.notification.extras ?: return ""
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()

        var text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.let { big ->
            if (big.length > text.length) text = big.toString()
        }

        val messages = NotificationCompat.MessagingStyle
            .extractMessagingStyleFromNotification(sbn.notification)?.messages
        if (!messages.isNullOrEmpty()) {
            val parts = messages.mapNotNull { m ->
                val who = m.sender?.toString().orEmpty()
                val what = m.text?.toString().orEmpty()
                if (what.isEmpty()) null else "$who: $what"
            }
            if (parts.isNotEmpty()) text = parts.joinToString("\n")
        }

        val titleText = title
            .replace(Regex("\\s+"), " ")
            .replace(
                Regex("(sent you a message|sent an attachment|posted to the group|is typing...)$", RegexOption.IGNORE_CASE),
                "",
            )
            .trim()
        return buildString {
            if (titleText.isNotBlank() && titleText != "Viber") append("$titleText. ")
            append(text)
        }.trim()
    }

    /**
     * A configured trusted source wins when its group or sender name appears in
     * the notification. Blank group/sender means "trust anything from this app".
     */
    private fun matchesTrustedSource(
        config: Map<String, Any>,
        sbn: StatusBarNotification,
        message: String,
    ): List<TrustedSource> {
        val raw = arrayOf(
            sbn.notification.extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty(),
            sbn.notification.extras?.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString().orEmpty(),
            message,
        ).joinToString(" ")

        val configured = (config["trusted_sources"] as? List<*>)?.mapNotNull {
            (it as? Map<*, *>)?.let { m ->
                TrustedSource(
                    app = m["app"]?.toString().orEmpty(),
                    group = m["group"]?.toString().orEmpty(),
                    sender = m["sender"]?.toString().orEmpty(),
                    enabled = m["enabled"] as? Boolean ?: true,
                )
            }
        } ?: emptyList()

        return configured.filter { source ->
            source.enabled &&
                (source.group.isBlank() || raw.contains(source.group, ignoreCase = true)) &&
                (source.sender.isBlank() || raw.contains(source.sender, ignoreCase = true))
        }
    }

    private fun fingerprint(message: String, sources: List<TrustedSource>): String {
        val anchor = sources.firstOrNull()?.group ?: "viber"
        return anchor + "|" + message.trim().take(90).hashCode().toUInt().toString(16)
    }

    private fun rank(message: String): String {
        val lower = message.lowercase()
        return when {
            CRITICAL_HINTS.any { lower.contains(it) } -> "critical"
            HIGH_HINTS.any { lower.contains(it) } -> "high"
            else -> "normal"
        }
    }

    private fun rankTitle(rank: String) = when (rank) {
        "critical" -> "Critical Opportunity Detected"
        "high" -> "High-Value Opportunity Detected"
        else -> "Opportunity Detected"
    }

    /**
     * Local-clock quiet-hours gate for the rank. Normal messages and
     * high-value opportunities are silenced per configuration; critical
     * opportunities are NEVER silenced --- they always reach the manager with
     * alarm escalation (see `quiet_hours_critical_opportunities`, which toggles
     * escalation persistence, not delivery).
     */
    private fun inQuietHours(config: Map<String, Any>, rank: String): Boolean {
        if (rank == "critical") return false
        val suppress = when (rank) {
            "high" -> config["quiet_hours_high_opportunities"] as? Boolean ?: true
            else -> config["quiet_hours_normal_messages"] as? Boolean ?: true
        }
        if (!suppress) return false

        val start = config["quiet_hours_start"]?.toString() ?: "23:00"
        val end = config["quiet_hours_end"]?.toString() ?: "07:00"
        val now = Calendar.getInstance()
        val minute = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)

        val s = start.split(":")
        val e = end.split(":")
        val startMin = (s.getOrNull(0)?.toIntOrNull() ?: 23) * 60 + (s.getOrNull(1)?.toIntOrNull() ?: 0)
        val endMin = (e.getOrNull(0)?.toIntOrNull() ?: 7) * 60 + (e.getOrNull(1)?.toIntOrNull() ?: 0)

        return if (endMin < startMin) minute >= startMin || minute < endMin
        else minute in startMin until endMin || (startMin == endMin && minute == startMin)
    }

    private fun buildOpportunity(
        manager: String,
        sbn: StatusBarNotification,
        message: String,
        sources: List<TrustedSource>,
        rank: String,
    ): Map<String, Any> {
        val group = sources.firstOrNull()?.group ?: "Viber"
        val sender = sources.firstOrNull()?.sender
            ?: sbn.notification.extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()

        val req = extractRequirements(message)
        val isInternational = message.contains("international", ignoreCase = true) ||
            message.contains("abroad", ignoreCase = true) ||
            message.contains("overseas", ignoreCase = true)

        return LinkedHashMap<String, Any>().apply {
            put("manager", manager)
            put("source", VIBER_PACKAGE)
            put("source_app", "Viber")
            put("source_group", group)
            put("sender", sender)
            put("sender_phone", "")
            put("title", message.trim().lines().first().take(80))
            put("course", req["course"] ?: "")
            put("course_code", req["course_code"] ?: "")
            put("location", req["location"] ?: "")
            put("country", req["country"] ?: "")
            put("dates_start", req["dates_start"] ?: "")
            put("dates_end", req["dates_end"] ?: "")
            put("skill_match_score", 0)
            put("verdict", when (rank) {
                "critical" -> "DETECTED"
                "high" -> "DETECTED"
                else -> "REVIEW"
            })
            put("decision", "pending")
            put("confidence", rank)
            put("preparation_hours", "")
            put("major_gap", "")
            put("strong_areas", emptyList<String>())
            put("weak_areas", emptyList<String>())
            put("evidence", listOf(
                mapOf(
                    "topic" to "message",
                    "evidence" to message.trim().take(220),
                    "source" to "Viber notification",
                    "status" to "SIGHTED",
                    "strength" to 0.0,
                ),
            ))
            put("requirements", req)
            put("raw_text", message)
            put("document_status", if (req["documentation_mentioned"].toString().isNotBlank()) "mentioned" else "none")
            put("is_high_opportunity", rank == "high")
            put("is_critical", rank == "critical")
            put("is_international", isInternational)
        }
    }

    /**
     * Lightweight structured requirement extraction. Mirrors the backend's
     * parser (`_extract_structured_requirements`) so detection works fully
     * offline; every field is read from the text or left empty --- nothing is
     * guessed.
     */
    internal fun extractRequirements(message: String): Map<String, Any> {
        val examCode = Regex("""\b([A-Z]{2})[-\s]?(\d{3})(?!\d)""", RegexOption.IGNORE_CASE)
            .find(message)
        val code = examCode?.let { "${it.groupValues[1].uppercase()}-${it.groupValues[2]}" } ?: ""
        val lower = message.lowercase()

        var start = ""
        var end = ""
        val toRange = Regex(
            """from\s+(\d{1,2})\w*\s+([a-z]{3,9})\.?\s*(?:[ ,]*(20\d{2}))?\s+(?:to|until|up to)\s+(\d{1,2})\w*\s+([a-z]{3,9})\.?\s*(?:[ ,]*(20\d{2}))?""",
            RegexOption.IGNORE_CASE,
        ).find(lower)
        if (toRange != null) {
            val y1 = toRange.groupValues[3].ifBlank { java.time.LocalDate.now().year.toString() }
            val y2 = toRange.groupValues[6].ifBlank { y1 }
            val s = iso(y1, monthNum(toRange.groupValues[2]), toRange.groupValues[1].toInt())
            val e = iso(y2, monthNum(toRange.groupValues[5]), toRange.groupValues[4].toInt())
            if (s.isNotBlank() && e.isNotBlank() && s <= e) { start = s; end = e }
        }
        if (start.isBlank()) {
            val range = Regex(
                """(\d{1,2})\w*\s*(?:-|to)\s*(\d{1,2})\w*\s+([a-z]{3,9}).?(?:\b[ ,]+(20\d{2}))?""",
                RegexOption.IGNORE_CASE,
            ).find(lower)
            if (range != null) {
                val y = range.groupValues[4].ifBlank { java.time.LocalDate.now().year.toString() }
                val m = monthNum(range.groupValues[3])
                val s = iso(y, m, range.groupValues[1].toInt())
                val e = iso(y, m, range.groupValues[2].toInt())
                if (s.isNotBlank() && e.isNotBlank() && s <= e) { start = s; end = e }
            }
        }
        if (start.isBlank()) {
            val single = Regex("""(\d{1,2})\w*\s+([a-z]{3,9}).?\b[ ,]+(20\d{2})""", RegexOption.IGNORE_CASE).find(lower)
            if (single != null) {
                val m = monthNum(single.groupValues[2])
                val s = iso(single.groupValues[3], m, single.groupValues[1].toInt())
                if (s.isNotBlank()) { start = s; end = s }
            }
        }

        val cities = mapOf(
            "dubai" to "UAE", "abu dhabi" to "UAE", "singapore" to "Singapore",
            "delhi" to "India", "bangalore" to "India", "bengaluru" to "India",
            "mumbai" to "India", "hyderabad" to "India", "chennai" to "India",
            "pune" to "India", "gurgaon" to "India", "london" to "UK",
            "berlin" to "Germany", "frankfurt" to "Germany", "munich" to "Germany",
            "zurich" to "Switzerland", "paris" to "France", "toronto" to "Canada",
            "new york" to "USA", "dallas" to "USA", "chicago" to "USA",
            "sydney" to "Australia", "melbourne" to "Australia", "doha" to "Qatar",
            "manama" to "Bahrain", "riyadh" to "Saudi Arabia", "lagos" to "Nigeria",
            "nairobi" to "Kenya", "cairo" to "Egypt", "amsterdam" to "Netherlands",
            "warsaw" to "Poland", "brussels" to "Belgium", "vancouver" to "Canada",
        )
        var location = ""
        var country = ""
        for ((city, cty) in cities) {
            if (Regex("""\b${Regex.escape(city)}\b""", RegexOption.IGNORE_CASE).containsMatchIn(lower)) {
                location = city.split(" ").joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
                country = cty
                break
            }
        }

        val mode = when {
            Regex("""\b(vilt|virtual|online|remote)\b""", RegexOption.IGNORE_CASE).containsMatchIn(lower) -> "virtual"
            Regex("""\b(onsite|in[- ]person|classroom|ilt|dedicated)\b""", RegexOption.IGNORE_CASE).containsMatchIn(lower) -> "onsite"
            else -> ""
        }

        val pax = Regex("""(\d{1,3})\s*(?:participants?|pax|heads|delegates?|learners?|people)""", RegexOption.IGNORE_CASE)
            .find(lower)?.groupValues?.get(1) ?: ""

        val docMarkers = listOf("toc", "syllabus", "agenda", "brochure", "pdf", "deck", "course material", "attachment")
        val docs = docMarkers.filter { lower.contains(it) }.map { it.replaceFirstChar { c -> c.uppercase() } }

        val action = when {
            Regex("""\b(availability|who is free|who can take|free this)\b""", RegexOption.IGNORE_CASE).containsMatchIn(lower) -> "availability check"
            Regex("""\b(can anyone deliver|can you deliver|who can deliver|need a trainer|trainer needed|looking for a trainer)\b""", RegexOption.IGNORE_CASE).containsMatchIn(lower) -> "training requirement"
            Regex("""\b(international|travel|overseas|abroad)\b""", RegexOption.IGNORE_CASE).containsMatchIn(lower) -> "travel opportunity"
            else -> "information"
        }

        return mapOf(
            "course_code" to code,
            "course" to code,
            "dates_start" to start,
            "dates_end" to end,
            "location" to location,
            "country" to country,
            "mode" to mode,
            "participants" to pax,
            "documentation_mentioned" to docs,
            "action" to action,
        )
    }

    private fun monthNum(token: String): Int {
        val months = listOf(
            "january", "february", "march", "april", "may", "june",
            "july", "august", "september", "october", "november", "december",
        )
        val t = token.lowercase().trimEnd('.')
        months.forEachIndexed { i, name ->
            if (name == t || name.startsWith(t)) return i + 1
        }
        return 0
    }

    private fun iso(year: String, month: Int, day: Int): String =
        try {
            java.time.LocalDate.of(year.toInt(), month, day).toString()
        } catch (_: Exception) {
            ""
        }

    // --- Dedupe set ---

    private fun seenSet(context: Context): Set<String> {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getStringSet(SEEN_KEY, emptySet()) ?: emptySet()
    }

    private fun rememberSeen(context: Context, fingerprint: String) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val updated = (seenSet(context) + fingerprint).toList().takeLast(SEEN_LIMIT).toSet()
        prefs.edit().putStringSet(SEEN_KEY, updated).apply()
    }

    // --- Offline-first retry queue ---

    private fun enqueuePending(context: Context, manager: String, body: Map<String, Any>) {
        val key = pendingKey(manager)
        val existing = (LocalCache.loadMap(key)?.get("items") as? List<*>)?.toMutableList() ?: mutableListOf()
        existing.add(body)
        LocalCache.saveMap(key, mapOf("items" to existing))
        OpportunityRetryWorker.enqueue(context, manager)
    }

    /** Retries previously-queued creates so a brief network outage self-heals. */
    private suspend fun flushPending(context: Context, manager: String) {
        val key = pendingKey(manager)
        val items = (LocalCache.loadMap(key)?.get("items") as? List<*>) ?: return
        if (items.isEmpty()) return
        val remaining = mutableListOf<Any>()
        for (it in items) {
            val body = it as? Map<*, *> ?: continue
            val asSheet = body.entries.associate { e -> e.key.toString() to (e.value ?: "") }
            try {
                repository.createOpportunity(asSheet)
            } catch (_: Exception) {
                remaining.add(body)
            }
        }
        LocalCache.saveMap(key, mapOf("items" to remaining))
    }

    internal fun pendingKey(manager: String) = "guardian_pending_${manager.trim().lowercase()}"
}
