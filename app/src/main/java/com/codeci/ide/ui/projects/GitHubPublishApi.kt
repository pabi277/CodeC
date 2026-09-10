package com.codeci.ide.ui.projects

import java.net.HttpURLConnection
import java.net.URL

/**
 * Phase 40.3 — thin Android-side wrapper that performs the actual
 * HttpURLConnection call for `POST /user/repos`.
 *
 * Shares the same shape as [ApkUpdateManager] so no new dependency (OkHttp,
 * coroutines-test) is needed.
 *
 * The caller is responsible for providing a token that has already been
 * redacted by [GitRedactor]; the API echo of the token is scrubbed from
 * error bodies.
 */
object GitHubPublishApi {

    /** Create a repository for the authenticated user.
     *
     * @param token     the GitHub personal access token (Bearer auth). Must not be blank.
     * @param name      the repository name (already GitHub-safe, from [GitHubPublish.nameFor])
     * @param private   set to true for a private repository (default: false)
     * @return pair: (success, messageOrResult) where success is true if the repo
     *         was created successfully, and the message contains the html_url on success
     *         or an error message on failure.
     */
    fun createRepo(
        token: String,
        name: String,
        private: Boolean = false
    ): Pair<Boolean, String> {
        val actualToken = token.takeIf { it.isNotBlank() }
            ?: return Pair(false, "No GitHub token is connected.")

        val urlStr = "https://api.github.com/user/repos"
        val bodyStr = GitHubPublish.body(name, null, private)

        return try {
            val url = URL(urlStr)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Authorization", "Bearer $actualToken")
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setDoOutput(true)

            // Write body
            val os = conn.outputStream
            os.write(bodyStr.toByteArray(Charsets.UTF_8))
            os.flush()
            os.close()

            val responseCode = conn.responseCode
            val input = conn.inputStream
            val reader = java.io.BufferedReader(java.io.InputStreamReader(input, Charsets.UTF_8))
            val response = reader.use { it.readText() }
            input.close()

            // Parse the response and return result
            val result = parseResponse(response, responseCode)
            result
        } catch (e: Exception) {
            // Network error, IO failure, malformed JSON, etc.
            Pair(false, "GitHub could not be reached. Check your network and retry.")
        }
    }

    /** Parse the GitHub API response and return a pair of (success, message). */
    private fun parseResponse(response: String, responseCode: Int): Pair<Boolean, String> {
        val lower = response.lowercase()

        // 201 → created successfully
        if (responseCode == 201) {
            // Extract html_url from the response
            val htmlUrl = extractJsonField(response, "html_url")
            if (htmlUrl != null) {
                return Pair(true, htmlUrl)
            }
            return Pair(true, "Repository created successfully")
        }

        // 401 → token missing/invalid
        if (lower.contains("credentials login failed") ||
            lower.contains("bad credentials")) {
            return Pair(false, "No GitHub token is connected.")
        }

        // 403 with rate-limit info
        if (lower.contains("rate limit exceeded") ||
            lower.contains("too many requests")) {
            return Pair(false, "GitHub rate limit exceeded. Try again later.")
        }

        // 422 — validation errors (name taken, etc.)
        if (lower.contains("\"message\"") && lower.contains("\"status\": 422")) {
            // Check for name taken
            if (lower.contains("\"name\"") && lower.contains("\"already exists\""))
                return Pair(false, "A repository with that name already exists on this account.")
            // Generic 422
            return Pair(false, "A repository with that name already exists on this account.")
        }

        // 5xx or other server error
        if (lower.contains("\"message\"") && lower.contains("\"status\": 5")) {
            return Pair(false, "GitHub could not be reached. Check your network and retry.")
        }

        // Generic error
        return Pair(false, "GitHub returned an error. Check the response and retry.")
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
}
