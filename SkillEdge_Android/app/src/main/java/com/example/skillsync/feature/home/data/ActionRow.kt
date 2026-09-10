package com.example.skillsync.data.models

/**
 * One row of the manager action inbox.
 *
 * Every consumer — [MainScreenViewModel][com.example.skillsync.ui.main.MainScreenViewModel],
 * [ActionsViewModel][com.example.skillsync.ui.main.ActionsViewModel] and
 * [Trainer360ViewModel][com.example.skillsync.ui.trainer.Trainer360ViewModel] —
 * parses the `actions_<email>` snapshot through [parseActions], so a field is
 * decoded in exactly one place instead of three private copies drifting apart.
 */
data class ActionRow(
    val id: String = "",
    val title: String = "",
    val detail: String = "",
    val category: String = "",
    val priority: String = "",
    val lifecycleState: String = "",
    val source: String = "",
    val trainerName: String = "",
    val trainerEmail: String = "",
    val dueDate: String = "",
    val notes: List<Note> = emptyList(),
    val history: List<History> = emptyList(),
) {
    data class Note(val text: String = "", val at: String = "")
    data class History(val at: String = "", val text: String = "")

    val open: Boolean get() = lifecycleState != "closed" && lifecycleState != "resolved"

    /**
     * Snake-case keyed map of the same fields the [ActionRow] data class exposes.
     * Lets existing call sites keep reading `row.str("lifecycle_state")` /
     * `row.list("notes")` against the typed model — UI migration happens
     * incrementally, the decode is already centralised.
     */
    fun asMap(): Map<String, Any> = mapOf(
        "id" to id,
        "title" to title,
        "detail" to detail,
        "category" to category,
        "priority" to priority,
        "lifecycle_state" to lifecycleState,
        "source" to source,
        "trainer_name" to trainerName,
        "trainer_email" to trainerEmail,
        "due_date" to dueDate,
        "notes" to notes.map { mapOf("text" to it.text, "at" to it.at) },
        "history" to history.map { mapOf("at" to it.at, "text" to it.text) },
    )

    companion object {
        fun from(row: Map<*, *>): ActionRow {
            val str = { key: String -> (row[key] as? String)?.trim().orEmpty() }
            val notes = rowRowList(row, "notes").map { n ->
                Note(text = (n["text"] as? String)?.trim().orEmpty(), at = (n["at"] as? String)?.trim().orEmpty())
            }
            val history = rowRowList(row, "history").map { h ->
                History(
                    at = (h["at"] as? String)?.trim().orEmpty(),
                    text = (h["text"] as? String)?.trim().orEmpty(),
                )
            }
            return ActionRow(
                id = str("id"),
                title = str("title"),
                detail = str("detail"),
                category = str("category"),
                priority = str("priority"),
                lifecycleState = str("lifecycle_state"),
                source = str("source"),
                trainerName = str("trainer_name"),
                trainerEmail = str("trainer_email"),
                dueDate = str("due_date"),
                notes = notes,
                history = history,
            )
        }

        private fun rowRowList(row: Map<*, *>, key: String): List<Map<*, *>> =
            (row[key] as? List<*>)?.filterIsInstance<Map<*, *>>() ?: emptyList()
    }
}

/** Decodes the `{ actions: [...] }` envelope the v2 actions API and the
 *  `actions_<email>` cache both use. */
fun parseActions(body: Map<String, Any>?): List<ActionRow> {
    if (body == null) return emptyList()
    @Suppress("UNCHECKED_CAST")
    val rows = (body["actions"] as? List<Map<String, Any>>) ?: emptyList()
    return rows.map { ActionRow.from(it) }
}