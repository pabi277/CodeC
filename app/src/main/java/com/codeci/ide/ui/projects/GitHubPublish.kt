package com.codeci.ide.ui.projects

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
    fun nameFor(projectName: String): String {
        val safe = projectName
            .replace(" ", "-")    // spaces -> hyphen
            .replace("\n", "-")   // newlines -> hyphen
            .replace("\r", "")    // strip carriage returns
        // Keep only [A-Za-z0-9._-], trim to 100 chars, no leading/trailing .
        val filtered = safe.filter { it.isLetterOrDigit() || it == '.' || it == '-' || it == '_' }
        val trimmed = safe.take(100)
        var result = trimmed
        if (result.startsWith(".")) result = result.substring(1)
        if (result.endsWith(".")) result = result.substring(0, result.length - 1)
        return result
    }

    /** Hand-rolled JSON body for `POST /user/repos`.
     *  No OkJson, no gson — just a string we can safely pass through GitRedactor.
     */
    fun body(name: String, description: String?, private: Boolean): String {
        val desc: String? = description?.take(200)
        val descEscaped: String? = desc?.let { d ->
            d.replace("\"", "\\\"").replace("\n", "\\n")
        }
        val privateStr = if (private) "true" else "false"
        val descJson = if (descEscaped?.isNotEmpty() == true) "\"" + descEscaped + "\"" else "null"
        return """{"name":"${name}","description":${descJson},"homepage":null,"private":${privateStr}}"""
    }

    /** Parse the raw JSON body from GitHub.
     *
     * On success returns a simple data class with html_url, ssh_url, default_branch.
     * On failure returns a sealed class with error kind and message.
     *
     * The [json] parameter should already be [GitRedactor]-cleaned so no token
     * reaches the parser.
     */
    sealed class ApiError(
        val kind: Int,
        val message: String
    )

    object ApiError {
        const val TOKEN_MISSING_KIND = 1
        const val PERMISSION_MISSING_KIND = 2
        const val NAME_TAKEN_KIND = 3
        const val RATE_LIMITED_KIND = 4
        const val SERVER_KIND = 5
    }

    /** Result of parsing the GitHub create-repo response. */
    data class PublishResult(
        val htmlUrl: String,
        val sshUrl: String?,
        val defaultBranch: String
    )

    /** Parse the raw JSON body from GitHub.
     *
     * On success returns [PublishResult] with html_url, ssh_url, default_branch.
     * On failure returns [ApiError] with a kind code and message.
     *
     * The [json] parameter should already be [GitRedactor]-cleaned so no token
     * reaches the parser.
     */
    fun parseCreateResponse(json: String): Result<PublishResult, ApiError> {
        val lower = json.lowercase()

        // 401 → token missing/invalid
        if (lower.contains("credentials login failed") ||
            lower.contains("bad credentials")) {
            return Result.failure(ApiError(ApiError.TOKEN_MISSING_KIND, "No GitHub token is connected."))
        }

        // 403 with rate-limit info
        if (lower.contains("rate limit exceeded") ||
            lower.contains("too many requests")) {
            return Result.failure(ApiError(ApiError.RATE_LIMITED_KIND, "GitHub rate limit exceeded. Try again later."))
        }

        // 422 — validation errors (name taken, etc.)
        if (lower.contains("\"message\"") && lower.contains("\"status\": 422")) {
            // Check for name taken
            if (lower.contains("\"name\"") && lower.contains("\"already exists\""))
                return Result.failure(ApiError(ApiError.NAME_TAKEN_KIND, "A repository with that name already exists on this account."))
            // Generic 422
            return Result.failure(ApiError(ApiError.NAME_TAKEN_KIND, "A repository with that name already exists on this account."))
        }

        // 5xx or other server error
        if (lower.contains("\"message\"") && lower.contains("\"status\": 5")) {
            return Result.failure(ApiError(ApiError.SERVER_KIND, "GitHub could not be reached. Check your network and retry."))
        }

        // Try to extract the fields we need for success
        val htmlUrl = extractJsonField(json, "html_url")
        val sshUrl = extractJsonField(json, "ssh_url")
        val defaultBranch = extractJsonField(json, "default_branch")

        if (htmlUrl != null && defaultBranch != null) {
            return Result.success(PublishResult(
                htmlUrl = htmlUrl,
                sshUrl = sshUrl,
                defaultBranch = defaultBranch
            ))
        }

        // Fallback: generic error
        return Result.failure(ApiError(ApiError.SERVER_KIND, "GitHub could not be reached. Check your network and retry."))
    }

    /** Very simple JSON field extractor: returns the value after "key":, or null.
     *  Handles simple quoted strings without nested quotes.
     */
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

    /** When token cannot create repos, show this message + browser fallback. */
    fun tokenCanCreateReposMessage(): String {
        return "No GitHub token is connected, so repository creation is not possible. " +
            "Add a token in Settings → GitHub Account (a fine-grained token with " +
            "Contents → Read and write), then retry.\n" +
            "Alternatively, open https://github.com/settings/personal-access-tokens/new " +
            "to create a token, then try again."
    }
}

/** A simple result type (since we can't use Either without adding a dependency). */
sealed class Result {
    data class Success<T>(val value: T) : Result()
    data class Failure(val message: String) : Result()

    @JvmStatic
    fun success<T>(value: T): Result = Result.Success(value)

    @JvmStatic
    fun failure(message: String): Result = Result.Failure(message)
}
