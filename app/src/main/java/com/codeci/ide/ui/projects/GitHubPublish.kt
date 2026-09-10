package com.codeci.ide.ui.projects

/**
 * Phase 40.3 — pure, Android-free "Publish to GitHub" logic.
 *
 * Symptom (owner, 2026-09-10): *"Github integration update now Github is
 * working but it's not user friendly"* — the sharpest instance is a local
 * project with nowhere to push: the sheet offers COMMIT & PUSH, git answers
 * `fatal: 'origin' does not exist`, and the user is told nothing actionable.
 *
 * The actual HTTP call lives in [GitHubPublishApi]; everything that can be
 * tested on the host JVM lives here. The token is read by the caller from
 * [GitCredentialsStore], never written to disk, never logged, and every
 * response body passes through [GitRedactor] before it reaches this parser.
 */
sealed interface PublishResult {

    /** The repository exists on GitHub and is ready to be a remote. */
    data class Published(
        val htmlUrl: String,
        val sshUrl: String?,
        val remoteUrl: String,
        val defaultBranch: String,
        val isPrivate: Boolean
    ) : PublishResult

    /** GitHub refused the request; [message] is safe to show the user. */
    data class ApiError(
        val kind: PublishErrorKind,
        val message: String,
        /** GitHub's own `X-Accepted-GitHub-Permissions` value, when it sent one. */
        val needsPermission: String? = null,
        val helpUrl: String? = null
    ) : PublishResult
}

enum class PublishErrorKind {
    TOKEN_MISSING,
    PERMISSION_MISSING,
    NAME_TAKEN,
    RATE_LIMITED,
    OFFLINE,
    SERVER,
    BAD_RESPONSE
}

object GitHubPublish {

    /** `POST /user/repos` — creates a repository owned by the token's user. */
    const val CREATE_URL = "https://api.github.com/user/repos"

    /** The browser fallback when the token cannot create repositories. */
    const val NEW_REPO_URL = "https://github.com/new"

    /** GitHub's own limits: `[A-Za-z0-9._-]`, ≤ 100 chars, no leading/trailing dot. */
    fun nameFor(projectName: String): String {
        val mapped = projectName.trim().map { ch ->
            when {
                ch in 'a'..'z' || ch in 'A'..'Z' || ch in '0'..'9' -> ch
                ch == '.' || ch == '-' || ch == '_' -> ch
                else -> '-'
            }
        }.joinToString("")
        // Runs of mapped characters collapse ("café note" -> "caf-note", not
        // "caf--note"); GitHub names should read like the project they came from.
        var name = mapped.replace(Regex("-{2,}"), "-").trim('-', '_', '.')
        if (name.length > 100) name = name.take(100).trimEnd('-', '_', '.')
        // GitHub rejects an empty name; a project folder is often "My App".
        return name.ifBlank { "codec-project" }
    }

    /** `name-2`, `name-3`, … for the 422 "name already exists" conversation. */
    fun alternativeName(name: String, attempt: Int): String =
        "${name.take(96)}-${attempt.coerceAtLeast(2)}"

    /** The JSON body for `POST /user/repos`. Hand-rolled — no new dependency. */
    fun body(name: String, description: String?, isPrivate: Boolean): String = buildString {
        append("{\"name\":\"").append(jsonEscape(name)).append('"')
        append(",\"description\":")
        val desc = description?.trim()?.takeIf { it.isNotEmpty() }?.take(350)
        if (desc == null) append("null") else append('"').append(jsonEscape(desc)).append('"')
        // GitHub's own default is PUBLIC — `private` must be sent explicitly.
        append(",\"private\":").append(if (isPrivate) "true" else "false")
        append(",\"has_issues\":true,\"has_wiki\":false}")
    }

    /**
     * Interprets GitHub's answer. The kinds are the ones that actually happen,
     * and each carries a next step rather than an HTTP code.
     */
    fun parseCreateResponse(
        code: Int,
        body: String?,
        acceptedPermissions: String? = null
    ): PublishResult {
        val json = body.orEmpty()
        val gitHubMessage = jsonString(json, "message")?.takeIf { it.isNotBlank() }

        if (code == 200 || code == 201) {
            val htmlUrl = jsonString(json, "html_url")
            if (htmlUrl.isNullOrBlank()) {
                return PublishResult.ApiError(
                    PublishErrorKind.BAD_RESPONSE,
                    "GitHub created the repository but the response did not include its URL."
                )
            }
            return PublishResult.Published(
                htmlUrl = htmlUrl,
                sshUrl = jsonString(json, "ssh_url"),
                remoteUrl = remoteUrlFor(htmlUrl),
                defaultBranch = jsonString(json, "default_branch") ?: "main",
                isPrivate = json.contains("\"private\":true") || json.contains("\"private\": true")
            )
        }

        return when (code) {
            401 -> PublishResult.ApiError(
                PublishErrorKind.TOKEN_MISSING,
                "GitHub did not accept the stored token. Save a new one in " +
                    "Settings → GitHub Account, then publish again.",
                helpUrl = GitErrors.TOKEN_HELP_URL
            )
            403 -> if (json.contains("rate limit", ignoreCase = true)) {
                PublishResult.ApiError(
                    PublishErrorKind.RATE_LIMITED,
                    "GitHub's rate limit is reached for this token. Wait a few minutes, " +
                        "then publish again."
                )
            } else {
                PublishResult.ApiError(
                    PublishErrorKind.PERMISSION_MISSING,
                    "Your token can't create repositories" +
                        (gitHubMessage?.let { ": $it" } ?: ".") +
                        " Create it in the browser, then paste its URL.",
                    needsPermission = acceptedPermissions,
                    helpUrl = NEW_REPO_URL
                )
            }
            422 -> if (json.contains("already exists", ignoreCase = true)) {
                PublishResult.ApiError(
                    PublishErrorKind.NAME_TAKEN,
                    gitHubMessage?.let { "GitHub says: $it" }
                        ?: "A repository with that name already exists on this account."
                )
            } else {
                PublishResult.ApiError(
                    PublishErrorKind.BAD_RESPONSE,
                    gitHubMessage?.let { "GitHub rejected the request: $it" }
                        ?: "GitHub rejected the request (422)."
                )
            }
            in 500..599 -> PublishResult.ApiError(
                PublishErrorKind.SERVER,
                "GitHub could not be reached right now (HTTP $code). Try again in a moment."
            )
            else -> PublishResult.ApiError(
                PublishErrorKind.BAD_RESPONSE,
                gitHubMessage?.let { "GitHub answered: $it (HTTP $code)" }
                    ?: "GitHub answered HTTP $code."
            )
        }
    }

    /** Always the HTTPS clone URL git can use with the stored token. */
    fun remoteUrlFor(htmlUrl: String): String {
        val base = htmlUrl.trim().trimEnd('/')
        if (base.isEmpty()) return base
        return if (base.endsWith(".git")) base else "$base.git"
    }

    // ---- tiny JSON helpers (flat objects, like CodecJsonParser) ------------

    /** `{"key":"value"}` → `value`, or null when the key is absent/not a string. */
    fun jsonString(json: String, key: String): String? {
        val marker = "\"$key\""
        var from = 0
        while (true) {
            val idx = json.indexOf(marker, from)
            if (idx < 0) return null
            var i = idx + marker.length
            while (i < json.length && json[i].isWhitespace()) i++
            if (i >= json.length || json[i] != ':') {
                from = idx + marker.length
                continue
            }
            i++
            while (i < json.length && json[i].isWhitespace()) i++
            if (i >= json.length || json[i] != '"') return null
            i++
            val value = StringBuilder()
            while (i < json.length) {
                val ch = json[i]
                when {
                    ch == '\\' && i + 1 < json.length -> {
                        when (val next = json[i + 1]) {
                            'n' -> value.append('\n')
                            't' -> value.append('\t')
                            'r' -> value.append('\r')
                            else -> value.append(next)
                        }
                        i += 2
                    }
                    ch == '"' -> return value.toString()
                    else -> {
                        value.append(ch)
                        i++
                    }
                }
            }
            return null
        }
    }

    private fun jsonEscape(value: String): String = buildString(value.length + 8) {
        for (ch in value) {
            when {
                ch == '\\' -> append("\\\\")
                ch == '"' -> append("\\\"")
                ch == '\n' -> append("\\n")
                ch == '\r' -> append("\\r")
                ch == '\t' -> append("\\t")
                ch < ' ' -> append("\\u").append(ch.code.toString(16).padStart(4, '0'))
                else -> append(ch)
            }
        }
    }
}
