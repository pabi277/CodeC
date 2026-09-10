package com.codeci.ide.ui.projects

import com.codeci.ide.ui.projects.GitRedactor.redactUrls
import com.codeci.ide.ui.projects.GitRedactor.redactAll
import java.util.Locale

/**
 * Phase 40.3 — pure, Android-free GitHub repo creation.
 *
 * All logic is testable on the host JVM. The actual HTTP transport lives in
 * [GitHubPublishApi], which uses the same shape as [ApkUpdateManager] so no
 * new dependency (OkHttp, coroutines-test) is needed.
 *
 * The token is read from [GitCredentialsStore], never written to disk, never
 * logged, and any error body passes through [GitRedactor] before it reaches
 * UI code.
 */
object GitHubPublish {

    /** GitHub-safe project name: alphanumerics, dot, hyphen, underscore.
     *  Max 100 characters, no leading or trailing dot.
     */
    @JvmField
    fun nameFor(projectName: String): String {
        val safe = projectName
            .replace(" ", "-")    // spaces -> hyphen
            .replace("\n", "-")   // newlines -> hyphen
            .replace("\r", "")    // strip carriage returns
        // Keep only [A-Za-z0-9._-], trim to 100 chars, no leading/trailing .
        val filtered = safe.filter { it.isLetterOrDigit() || it == '.' || it == '-' || it == '_' }
        val trimmed = safe.take(100)
        return trimmed.removePrefix { it == '.' }.removeSuffix { it == '.' }
    }

    /** Hand-rolled JSON body for `POST /user/repos`.
     *  No OkJson, no gson — just a string we can safely pass through GitRedactor.
     */
    @JvmField
    fun body(name: String, description: String?, private: Boolean): String {
        val desc = description
            ?.take(200)              // GitHub truncates at 1000 but we cap earlier
            ?.replace("\"", "\\\"") // escape double quotes
            ?.replace("\n", "\\n")  // escape newlines
            : ""

        val privateStr = if (private) "true" else "false"
        """{"name":"${name}","description":${if (desc.isNotEmpty()) "\"" + desc + "\"" else "null"}","homepage":null,"private":${privateStr}}"""
    }

    /** Parse the JSON response from GitHub's `CREATE` endpoint.
     *
     * On success returns [PublishResult] with html_url, ssh_url, default_branch.
     * On failure returns [ApiError] with a user‑friendly kind + message.
     *
     * The [json] parameter should already be [GitRedactor]-cleaned so no token
     * reaches the parser.
     */
    sealed class ApiError(
        val kind: ApiErrorKind,
        val message: String,
        val helpUrl: String? = null
    )

    /** Successful repo creation result. */
    data class PublishResult(
        val htmlUrl: String,
        val sshUrl: String?,
        val defaultBranch: String,
        val private: Boolean
    )

    /** ApiError subclasses. */
    object ApiError {
        /** Token is missing or invalid. */
        @JvmField
        object TokenMissing : ApiError(
            kind = ApiErrorKind.TOKEN_MISSING,
            message = "No GitHub token is connected.",
            helpUrl = TOKEN_HELP_URL
        )

        /** Permission missing — token doesn't have repo:write. */
        @JvmField
        data class PermissionMissing(
            val needed: String,
            @JvmField
            override val helpUrl: String? = TOKEN_HELP_URL
        ) : ApiError(
            kind = ApiErrorKind.PERMISSION_MISSING,
            message = "Your token may not allow repository creation. $needed",
            helpUrl = TOKEN_HELP_URL
        )

        /** Name already exists on this account. */
        @JvmField
        object NameTaken : ApiError(
            kind = ApiErrorKind.NAME_TAKEN,
            message = "A repository with that name already exists on this account.",
            helpUrl = null
        )

        /** Rate limited — GitHub returned 403 with X-RateLimit-Remaining: 0. */
        @JvmField
        object RateLimited : ApiError(
            kind = ApiErrorKind.RATE_LIMITED,
            message = "GitHub rate limit exceeded. Try again later.",
            helpUrl = null
        )

        /** Generic server error. */
        @JvmField
        object Server : ApiError(
            kind = ApiErrorKind.SERVER,
            message = "GitHub could not be reached. Check your network and retry.",
            helpUrl = null
        )
    }

    /** Enum class for API error kinds. */
    enum class ApiErrorKind {
        TOKEN_MISSING,
        PERMISSION_MISSING,
        NAME_TAKEN,
        RATE_LIMITED,
        SERVER,
        GENERIC
    }

    /** Successful repo creation result. */
    data class PublishResult(
        val htmlUrl: String,
        val sshUrl: String?,
        val defaultBranch: String,
        val private: Boolean
    )

    /** Parse the raw JSON body from GitHub.
     *
     * On success returns [PublishResult] with html_url, ssh_url, default_branch.
     * On failure returns [ApiError] with a user‑friendly kind + message.
     *
     * The [json] parameter should already be [GitRedactor]-cleaned so no token
     * reaches the parser.
     */
    @JvmField
    fun parseCreateResponse(json: String): Either<PublishResult, ApiError> {
        val lower = json.lowercase()

        // 401 → token missing/invalid
        if (lower.contains("credentials login failed") ||
            lower.contains("bad credentials")) {
            return Right(ApiError.TokenMissing())
        }

        // 403 with rate-limit info
        if (lower.contains("rate limit exceeded") ||
            lower.contains("too many requests")) {
            return Right(ApiError.RateLimited())
        }

        // 422 — validation errors (name taken, etc.)
        if (lower.contains("\"message\"") && lower.contains("\"status\": 422")) {
            // Check for name taken
            if (lower.contains("\"name\"") && lower.contains("\"already exists\""))
                return Right(ApiError.NameTaken())
            // Generic 422
            return Right(ApiError.NameTaken())
        }

        // 5xx or other server error
        if (lower.contains("\"message\"") && lower.contains("\"status\": 5")) {
            return Right(ApiError.Server())
        }

        // Try to extract the fields we need for success
        val htmlUrl = extractJsonField(json, "html_url")
        val sshUrl = extractJsonField(json, "ssh_url")
        val defaultBranch = extractJsonField(json, "default_branch")

        if (htmlUrl != null && defaultBranch != null) {
            return Left(PublishResult(
                htmlUrl = htmlUrl,
                sshUrl = sshUrl,
                defaultBranch = defaultBranch,
                private = false // caller decides
            ))
        }

        // Fallback: generic error
        return Right(ApiError.Server())
    }

    /** Very simple JSON field extractor: returns the value after "key":, or null.
     *  Handles simple quoted strings without nested quotes.
     */
    @JvmField
    private fun extractJsonField(json: String, key: String): String? {
        val keyLower = key.lowercase()
        val keySearch = """"${keyLower}":"""
        val idx = json.indexOf(keySearch)
        if (idx < 0) return null
        val start = idx + keySearch.length
        // Skip the opening quote
        var i = start + 1
        val sb = StringBuilder()
        while (i < json.length) {
            val ch = json[i]
            if (ch == '"' || ch == ',' || ch == '}') break
            sb.append(ch)
            i++
        }
        return if (sb.isNotEmpty()) sb.toString() else null
    }

    /** Turn an [ApiError] into a user‑friendly message + help link. */
    @JvmField
    fun apiErrorMessage(error: ApiError): String {
        val parts = StringBuilder()
        parts.append(error.message)
        if (error.helpUrl != null && !error.helpUrl.isBlank()) {
            parts.append('\n').append(error.helpUrl)
        }
        return parts.toString()
    }
}
