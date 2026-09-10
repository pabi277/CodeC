package com.codeci.ide.ui.projects

import java.net.HttpURLConnection
import java.net.URI

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
     * @return Either [GitHubPublish.PublishResult] or [GitHubPublish.ApiError]
     */
    @JvmField
    fun createRepo(
        token: String,
        name: String,
        private: Boolean = false
    ): Either<GitHubPublish.PublishResult, GitHubPublish.ApiError> {

        val actualToken = token.takeIf { it.isNotBlank() }
            ?: return Either.right(GitHubPublish.ApiError.TokenMissing())

        val url = "https://api.github.com/user/repos"
        val body = GitHubPublish.body(name, null, private)

        return try {
            val conn = java.net.HttpURLConnection(URI(url).toURL())
            conn.requestMethod = "POST"
            conn.setRequestProperty("Authorization", "Bearer $actualToken")
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setDoOutput(true)

            // Write body
            val os = conn.outputStream
            os.write(body.toByteArray(Charsets.UTF_8))
            os.flush()
            os.close()

            val responseCode = conn.responseCode
            val input = conn.inputStream
            val reader = java.io.BufferedReader(java.io.InputStreamReader(input, Charsets.UTF_8))
            val response = reader.use { it.readText() }
            input.close()

            // Parse the response
            val result = GitHubPublish.parseCreateResponse(response)

            // Enrich 403 with permission info from GitHub's header
            if (responseCode == 403) {
                val permHeader = conn.getHeaderField("X-Accepted-GitHub-Permissions")
                if (permHeader != null) {
                    // GitHub lists permitted scopes; determine if repo:write is present
                    val hasRepoWrite = permHeader.lowercase().contains("repo") &&
                        permHeader.lowercase().contains("write")

                    if (!hasRepoWrite) {
                        // Return a permission-missing error with helpful next step
                        return Either.right(GitHubPublish.ApiError.PermissionMissing(
                            needed = "repo (Contents → Read and write)",
                            helpUrl = GitHubPublish.Api.ApiError.TOKEN_HELP_URL
                        ))
                    }
                }
            }

            result
        } catch (e: Exception) {
            // Network error, IO failure, malformed JSON, etc.
            Either.right(GitHubPublish.ApiError.Server())
        }
    }
}
