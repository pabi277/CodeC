package com.codeci.ide.ui.projects

import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException

/**
 * Phase 40.3 — the single HTTP call behind "Publish to GitHub".
 *
 * Deliberately dependency-free (`HttpURLConnection`, no OkHttp, no JSON
 * library, no coroutines), so this file stays on the host-JVM unit-test path
 * and the request/response *shapes* live in the pure [GitHubPublish].
 *
 * **Blocking by design:** callers run it on `Dispatchers.IO`. The token is
 * used for exactly one request header and never written to disk or logged;
 * every response body is passed through [GitRedactor] before it can reach a
 * `String` the UI shows.
 */
object GitHubPublishApi {

    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 30_000

    /**
     * `POST https://api.github.com/user/repos`.
     *
     * @param token the stored GitHub PAT (fine-grained tokens need
     *   *Administration: write* on all repositories for this endpoint).
     * @param name  already GitHub-safe ([GitHubPublish.nameFor]).
     * @param isPrivate true for a private repository — GitHub's own default is public.
     */
    fun createRepo(
        token: String,
        name: String,
        description: String? = null,
        isPrivate: Boolean = true
    ): PublishResult {
        val secret = token.trim()
        if (secret.isEmpty()) {
            return PublishResult.ApiError(
                PublishErrorKind.TOKEN_MISSING,
                "No GitHub token is connected. Add one in Settings → GitHub Account, " +
                    "then publish again.",
                helpUrl = GitErrors.TOKEN_HELP_URL
            )
        }
        val redactor = GitRedactor(secret)
        val payload = GitHubPublish.body(GitHubPublish.nameFor(name), description, isPrivate)

        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(GitHubPublish.CREATE_URL).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                doOutput = true
                setRequestProperty("Authorization", "Bearer $secret")
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
                setRequestProperty("User-Agent", "CodeC-IDE")
            }

            connection.outputStream.use { out ->
                out.write(payload.toByteArray(Charsets.UTF_8))
                out.flush()
            }

            val code = connection.responseCode
            val accepted = connection.getHeaderField("X-Accepted-GitHub-Permissions")
            val raw = readBody(connection, code)
            GitHubPublish.parseCreateResponse(
                code = code,
                body = redactor.redact(raw),
                acceptedPermissions = accepted
            )
        } catch (e: UnknownHostException) {
            offline()
        } catch (e: SocketTimeoutException) {
            offline()
        } catch (e: Exception) {
            // A dropped connection, TLS problem, or malformed URL: never let the
            // raw message (which may embed the URL) reach the UI unredacted.
            PublishResult.ApiError(
                PublishErrorKind.OFFLINE,
                "Could not reach GitHub: " + redactor.redact(e.message ?: e.javaClass.simpleName),
                helpUrl = null
            )
        } finally {
            runCatching { connection?.disconnect() }
        }
    }

    /** GitHub sends the error body on `errorStream` for every 4xx/5xx. */
    private fun readBody(connection: HttpURLConnection, code: Int): String {
        val stream = if (code in 200..299) {
            runCatching { connection.inputStream }.getOrNull()
        } else {
            runCatching { connection.errorStream }.getOrNull()
                ?: runCatching { connection.inputStream }.getOrNull()
        }
        if (stream == null) return ""
        return runCatching {
            BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() }
        }.getOrDefault("")
    }

    private fun offline() = PublishResult.ApiError(
        PublishErrorKind.OFFLINE,
        "GitHub could not be reached. Check your connection, then publish again."
    )
}
