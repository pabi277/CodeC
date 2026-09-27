package com.codeci.ide.ui.projects

import java.io.File
import java.lang.ref.WeakReference

/** Main-thread callbacks. Weak registration covers editors parked on the back stack too. */
interface GitDiscardEditor {
    fun canDiscardFile(root: File, path: String): Boolean
    fun reloadDiscardedFile(root: File, path: String)
}

object GitDiscardEditors {
    private val editors = mutableListOf<WeakReference<GitDiscardEditor>>()
    private var active: Ticket? = null
    class Ticket internal constructor(
        val root: File, val path: String,
        internal val participants: List<GitDiscardEditor>,
    )

    @Synchronized fun register(editor: GitDiscardEditor) {
        editors.removeAll { it.get() == null }
        if (editors.none { it.get() === editor }) editors += WeakReference(editor)
    }

    @Synchronized fun unregister(editor: GitDiscardEditor) {
        editors.removeAll { it.get() == null || it.get() === editor }
    }

    @Synchronized fun begin(root: File, path: String): Ticket {
        check(active == null) { "A discard is already running." }
        require(GitDiscardPolicy.safePath(path) != null) { "Unsafe discard path." }
        val participants = editors.mapNotNull { it.get() }
        check(participants.all { it.canDiscardFile(root, path) }) {
            "Save the open file first, then review its diff before discarding. Unsaved editor changes were kept."
        }
        return Ticket(root.canonicalFile, path, participants).also { active = it }
    }

    @Synchronized fun blocks(root: File, path: String): Boolean =
        active?.let { it.root == root.canonicalFile && it.path == path } ?: false

    /** Always release, including a failed Git command or an editor reload failure. */
    @Synchronized fun finish(ticket: Ticket) {
        if (active !== ticket) return
        try {
            var failure: Exception? = null
            ticket.participants.forEach {
                try { it.reloadDiscardedFile(ticket.root, ticket.path) }
                catch (e: Exception) { failure = e }
            }
            failure?.let { throw it }
        } finally {
            active = null
        }
    }
}
