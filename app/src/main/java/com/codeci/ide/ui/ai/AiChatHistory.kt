package com.codeci.ide.ui.ai

/**
 * Phase 95 — **the history drawer**: more than one conversation per project,
 * while the app is open.
 *
 * The owner, with two ChatGPT screenshots beside his message: *"add history option
 * and add a welcome interface and an agreement …"*. ChatGPT's drawer shows
 * Pinned and Recents rows and a New chat pill; this is that shape, scoped by the
 * app's own laws:
 *
 *  - **D6 does not move.** Nothing here is written to a file, a store, a log or a
 *    backup. The history lives in [AiViewModel]'s memory map keyed by project and
 *    dies with the process, exactly like the transcripts it holds. (The owner's
 *    Phase 93 instruction was explicit: *"Session-only, don't amend D6"*. Making
 *    history survive a restart would be a D6 amendment, so it is not done here.)
 *  - **A switch is a save and a restore**, not a merge: [withCurrent] snapshots the
 *    conversation in front of you, [switchTo] replaces the whole value. One
 *    project can therefore never draw another project's chat, and a chat can never
 *    grow turns from a conversation it is not.
 *  - **Bounded**: [AiChatHistoryLimits.MAX_CHATS] entries, and eviction never
 *    touches a pinned chat or the one being viewed — the same "a control that
 *    silently eats your work is worse than a cap" rule the transcript follows.
 *
 * Pure Kotlin: no `android.*`, no `java.io`, no clock (ids are counted, not
 * timed), so `AiChatHistoryTest` runs on the host JVM.
 */
object AiChatHistoryLimits {
    /** Conversations kept per project, this session. */
    const val MAX_CHATS = 20

    /** A drawer row's title is one clipped line, like a real one. */
    const val MAX_TITLE_CHARS = 48
}

/** One conversation in the drawer. [pinned] survives eviction; [id] is stable. */
data class AiChatEntry(
    val id: Long,
    val session: AiChatSession,
    val taskCommitted: Boolean = false,
    val pinned: Boolean = false
) {
    fun title(): String = AiChatHistory.titleOf(session)
}

/** What the drawer draws: a row with no message text in it. */
data class AiChatSummary(val id: Long, val title: String, val pinned: Boolean, val current: Boolean)

/**
 * The ordered set of conversations for one project, plus which one is on screen.
 * Immutable: every change returns a new value, so a Compose read is a snapshot
 * like the rest of [AiUiState] and a switch can never half-apply.
 */
data class AiChatHistory(
    /** Newest first. Includes the current conversation, if it has any turns. */
    val entries: List<AiChatEntry> = emptyList(),
    val currentId: Long? = null,
    /** Ids are counted, never clocked: no timestamp is needed to order a list. */
    val nextId: Long = 1
) {

    fun current(): AiChatEntry? = currentId?.let { id -> entries.firstOrNull { it.id == id } }

    /** The conversation on screen, or an empty one before the first question. */
    fun session(): AiChatSession = current()?.session ?: AiChatSession.EMPTY

    fun taskCommitted(): Boolean = current()?.taskCommitted ?: false

    /**
     * Snapshot the conversation in front of you. Called before anything that
     * leaves it (a switch, New chat, a project change) and whenever a task
     * settles. An empty conversation is not archived: a project whose chat was
     * never used shows an empty drawer, not a row called "New chat".
     */
    fun withCurrent(session: AiChatSession, taskCommitted: Boolean): AiChatHistory {
        if (session.isEmpty()) {
            return if (currentId == null) this else copy(entries = entries.filterNot { it.id == currentId })
        }
        val existing = currentId?.let { id -> entries.firstOrNull { it.id == id } }
        if (existing != null) {
            val updated = existing.copy(session = session, taskCommitted = taskCommitted)
            return copy(entries = entries.map { if (it.id == updated.id) updated else it })
        }
        val entry = AiChatEntry(id = nextId, session = session, taskCommitted = taskCommitted)
        return bounded(copy(entries = listOf(entry) + entries, currentId = entry.id, nextId = nextId + 1))
    }

    /**
     * New chat: the conversation in front of you stays in the drawer and an empty
     * one takes its place. This is what the owner's *"New chat"* does now — it
     * used to delete the chat, which is exactly what a history must not do.
     */
    fun beginNew(): AiChatHistory = copy(currentId = null)

    /** Open a different conversation. Ids that are not in the drawer are ignored. */
    fun switchTo(id: Long): AiChatHistory =
        if (entries.any { it.id == id }) copy(currentId = id) else this

    fun togglePin(id: Long): AiChatHistory =
        copy(entries = entries.map { if (it.id == id) it.copy(pinned = !it.pinned) else it })

    /** The drawer's two sections, each newest first. */
    fun pinned(): List<AiChatEntry> = entries.filter { it.pinned }

    fun recents(): List<AiChatEntry> = entries.filterNot { it.pinned }

    /** Rows for the sheet: titles only — never message text (D4's spirit, D6's letter). */
    fun summaries(): List<AiChatSummary> =
        entries.map { AiChatSummary(it.id, it.title(), it.pinned, it.id == currentId) }

    /**
     * Eviction: over [AiChatHistoryLimits.MAX_CHATS], drop the oldest **unpinned**
     * entry that is not the one on screen. A pinned chat and the conversation you
     * are looking at are never removed by a cap.
     */
    private fun bounded(h: AiChatHistory): AiChatHistory {
        if (h.entries.size <= AiChatHistoryLimits.MAX_CHATS) return h
        val victim = h.entries.lastOrNull { !it.pinned && it.id != h.currentId } ?: return h
        return h.copy(entries = h.entries.filterNot { it.id == victim.id })
    }

    companion object {
        val EMPTY = AiChatHistory()

        /**
         * A drawer row's title: the first question, one line, clipped. Derived
         * when the drawer is drawn, never stored — a stored title would be a
         * second copy of chat text to keep in step with the transcript.
         *
         * Phase 96 — the whitespace pattern is a value, not a call-site literal:
         * [summaries] runs on every recomposition of the drawer (up to
         * [AiChatHistoryLimits.MAX_CHATS] rows each), and `Regex(...)` compiles.
         */
        private val WHITESPACE = Regex("\\s+")

        fun titleOf(session: AiChatSession): String {
            val line = session.turns.firstOrNull { it.role == AiChatRole.YOU }?.text
                ?: session.turns.firstOrNull()?.text
                ?: return "New chat"
            val flat = line.replace(WHITESPACE, " ").trim()
            if (flat.length <= AiChatHistoryLimits.MAX_TITLE_CHARS) return flat
            return flat.take(AiChatHistoryLimits.MAX_TITLE_CHARS - 1).trimEnd() + "…"
        }
    }
}
