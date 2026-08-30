package com.codeci.ide.ui.projects

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.codeci.ide.ui.theme.dataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Phase 13 — app-private storage for the GitHub HTTPS credentials and the
 * commit author identity, on the same DataStore the rest of the settings use
 * (`files/datastore/user.preferences_pb`, sandboxed to the app UID).
 *
 * The token never leaves this store except into the per-command environment
 * of a git child process ([GitManager]); it is never written into
 * `.git/config`, shell profiles, terminal sessions, or log lines
 * ([GitRedactor] scrubs every git output path).
 */
class GitCredentialsStore(private val context: Context) {

    companion object {
        val GIT_TOKEN = stringPreferencesKey("git_token")
        val GIT_USERNAME = stringPreferencesKey("git_username")
        val GIT_AUTHOR_NAME = stringPreferencesKey("git_author_name")
        val GIT_AUTHOR_EMAIL = stringPreferencesKey("git_author_email")
    }

    data class Stored(
        val token: String,
        val username: String,
        val authorName: String,
        val authorEmail: String
    ) {
        val hasToken: Boolean get() = token.isNotBlank()
        val credentials: GitCredentials?
            get() = token.takeIf { it.isNotBlank() }?.let { GitCredentials(it, username.ifBlank { null }) }
        val identity: GitIdentity
            get() = GitIdentity(authorName.ifBlank { GitIdentity.FALLBACK.name }, authorEmail.ifBlank { GitIdentity.FALLBACK.email })
    }

    val storedFlow: Flow<Stored> = context.dataStore.data.map { prefs ->
        Stored(
            token = prefs[GIT_TOKEN].orEmpty(),
            username = prefs[GIT_USERNAME].orEmpty(),
            authorName = prefs[GIT_AUTHOR_NAME].orEmpty(),
            authorEmail = prefs[GIT_AUTHOR_EMAIL].orEmpty()
        )
    }

    suspend fun stored(): Stored = storedFlow.first()

    suspend fun save(token: String, username: String, authorName: String, authorEmail: String) {
        context.dataStore.edit { prefs ->
            prefs[GIT_TOKEN] = token.trim()
            prefs[GIT_USERNAME] = username.trim()
            prefs[GIT_AUTHOR_NAME] = authorName.trim()
            prefs[GIT_AUTHOR_EMAIL] = authorEmail.trim()
        }
    }

    /** Disconnects the GitHub account (token + username) but keeps the author identity. */
    suspend fun clearCredentials() {
        context.dataStore.edit { prefs ->
            prefs[GIT_TOKEN] = ""
            prefs[GIT_USERNAME] = ""
        }
    }
}
