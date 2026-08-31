package com.codeci.ide.ui.projects

/** One choosable project template in the New Project dialog. */
data class ProjectTypeOption(
    val id: String,
    val label: String,
    val description: String
)

/**
 * Phase 14 — the project types the Files-tab wizard can create. [id] is the
 * ProjectConfig type; the server entries scaffold a background server and
 * RUN ▶ opens Web Preview on the detected loopback URL.
 */
object ProjectTypes {

    val options: List<ProjectTypeOption> = listOf(
        ProjectTypeOption("c", "C Program", "main.c — compiled with CodeC's embedded TCC"),
        ProjectTypeOption("python", "Python Script", "main.py — run with python3"),
        ProjectTypeOption("web", "Static Web", "index.html — open in the Web Preview"),
        ProjectTypeOption(
            "python-flask",
            "Flask Web Server",
            "app.py on http://127.0.0.1:5000 — RUN ▶ opens the Web Preview"
        ),
        ProjectTypeOption(
            "python-fastapi",
            "FastAPI Server",
            "main.py on http://127.0.0.1:8000 — RUN ▶ opens the Web Preview"
        ),
        ProjectTypeOption(
            "c-microservice",
            "C Microservice",
            "server.c on http://127.0.0.1:8080 — RUN ▶ opens the Web Preview"
        )
    )

    fun optionFor(id: String): ProjectTypeOption? = options.firstOrNull { it.id == id }
}
