package com.codeci.ide.ui.projects

/**
 * Phase 33.1 — the first-run starter tiles (Pydroid-easy). Three tiles only:
 * **C** (works offline), **Python**, and **HTML**. Each starter maps to the
 * same project type + entry file the wizard already scaffolds
 * ([ProjectScaffold]), so a tile tap is "create-or-open the starter project
 * and open its entry file" — RUN ▶ then behaves exactly as it does for any
 * project of that type (33.0: TCC no-dialog for C, the Phase 21 install gate
 * only if python is missing, web preview for HTML).
 *
 * Pure Kotlin, host-testable: the definitions and the idempotent lookup are
 * Android-free. [ensureProject] is the one Android-touching helper (it needs
 * a real [ProjectManager]).
 */
data class WelcomeStarter(
    val id: String,
    val title: String,
    val subtitle: String,
    val projectType: String,
    val projectName: String,
    val entryFile: String,
)

object WelcomeStarters {

    val starters: List<WelcomeStarter> = listOf(
        WelcomeStarter(
            id = "c",
            title = "C",
            subtitle = "Works offline — built-in TCC compiler, no setup",
            projectType = "c",
            projectName = "C Starter",
            entryFile = "main.c",
        ),
        WelcomeStarter(
            id = "python",
            title = "Python",
            subtitle = "Run scripts with python3 — installs once on first run",
            projectType = "python",
            projectName = "Python Starter",
            entryFile = "main.py",
        ),
        WelcomeStarter(
            id = "web",
            title = "HTML",
            subtitle = "A web page you edit and preview live",
            projectType = "web",
            projectName = "Web Starter",
            entryFile = "index.html",
        ),
    )

    fun byId(id: String): WelcomeStarter? = starters.firstOrNull { it.id == id }

    /**
     * Create-or-get the starter's project. Idempotent: a second tap reuses the
     * same project (and its files) instead of failing on "name already exists".
     * Returns null only when the filesystem refuses (the caller keeps the
     * welcome/empty state so the user can retry).
     */
    fun ensureProject(manager: ProjectManager, starter: WelcomeStarter): ProjectInfo? =
        manager.project(starter.projectName)
            ?: manager.createProject(starter.projectName, starter.projectType).getOrNull()
}
