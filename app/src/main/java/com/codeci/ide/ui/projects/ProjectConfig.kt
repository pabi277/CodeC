package com.codeci.ide.ui.projects

import org.json.JSONObject

/** The versioned, user-editable run configuration for one project. */
data class ProjectConfig(
    val version: Int = CURRENT_VERSION,
    val name: String,
    val type: String = "c",
    val entry: String = "main.c",
    val build: String = "mkdir -p bin && cc main.c -o bin/app",
    val run: String = "./bin/app",
    val clean: String = "rm -rf bin/app"
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("version", version)
        put("name", name)
        put("type", type)
        put("entry", entry)
        put("build", build)
        put("run", run)
        put("clean", clean)
    }

    fun toJsonString(): String = toJson().toString()

    companion object {
        const val CURRENT_VERSION = 1

        fun defaultFor(name: String, type: String = "c"): ProjectConfig {
            val projectType = type.trim().ifEmpty { "c" }.lowercase()
            return when (projectType) {
                "web", "static-web" -> ProjectConfig(
                    name = name,
                    type = "web",
                    entry = "index.html",
                    build = "",
                    run = "",
                    clean = ""
                )
                "python" -> ProjectConfig(
                    name = name,
                    type = "python",
                    entry = "main.py",
                    build = "",
                    run = "python3 main.py",
                    clean = ""
                )
                else -> ProjectConfig(name = name)
            }
        }

        fun fromJson(text: String, fallbackName: String): ProjectConfig {
            val json = JSONObject(text)
            val name = json.optString("name", fallbackName)
                .takeIf { ProjectPathUtils.sanitizeProjectName(it) != null }
                ?: fallbackName
            return ProjectConfig(
                version = json.optInt("version", CURRENT_VERSION),
                name = name,
                type = json.optString("type", "c").ifBlank { "c" },
                entry = json.optString("entry", "main.c"),
                build = json.optString("build", "mkdir -p bin && cc main.c -o bin/app"),
                run = json.optString("run", "./bin/app"),
                clean = json.optString("clean", "rm -rf bin/app")
            )
        }
    }
}
