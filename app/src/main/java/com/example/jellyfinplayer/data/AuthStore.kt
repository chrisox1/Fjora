package com.example.jellyfinplayer.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

private val Context.dataStore by preferencesDataStore(name = "auth")

/**
 * Persists Jellyfin accounts. The store holds a list of accounts (each tied to
 * a server + user) plus an active-account pointer. Callers consume:
 *
 *   - `authFlow`     — observable view of the currently active account, in
 *                      the same shape the rest of the app expects. When
 *                      there's no active account this emits an empty Auth
 *                      with `isLoggedIn = false`, which the VM uses to
 *                      decide whether to show LoginScreen.
 *   - `accountsFlow` — full list of saved accounts for the Settings UI.
 *
 * Mutations: `addAccount`, `switchTo`, `remove`. The deviceId is stored
 * separately and is shared across all accounts on the install — Jellyfin
 * tracks one token per (deviceId, user) pair, so reusing the same deviceId
 * for multiple users on the same server is fine.
 */
class AuthStore(private val context: Context) {
    companion object {
        // Persistent installation identity. Survives sign-out and account
        // switches. Keep stable so the server's session bookkeeping doesn't
        // explode with a new device record on every login.
        val DEVICE_ID = stringPreferencesKey("device_id")

        // JSON-encoded list of AccountRecord.
        val ACCOUNTS_JSON = stringPreferencesKey("accounts_json")

        // ID of the currently-active account within the list.
        val ACTIVE_ID = stringPreferencesKey("active_id")

        // Legacy single-account keys — read on app startup ONCE so users
        // upgrading from a prior version aren't logged out.
        private val LEGACY_SERVER = stringPreferencesKey("server_url")
        private val LEGACY_TOKEN = stringPreferencesKey("access_token")
        private val LEGACY_USER_ID = stringPreferencesKey("user_id")
        private val LEGACY_USER_NAME = stringPreferencesKey("user_name")
    }

    @Serializable
    data class AccountRecord(
        /** Stable client-side id; we use this to switch / remove. */
        val id: String,
        val server: String,
        val token: String,
        val userId: String,
        val userName: String
    ) {
        /**
         * URL to the user's Jellyfin profile picture, or null if the account
         * is missing the data needed to build it. Will 404 cleanly if the
         * user hasn't set an avatar — Coil falls back to its error slot in
         * that case, which is what we want.
         */
        fun avatarUrl(): String? {
            if (server.isBlank() || userId.isBlank() || token.isBlank()) return null
            val base = server.trimEnd('/')
            return "$base/Users/$userId/Images/Primary?api_key=$token"
        }
    }

    /**
     * Snapshot of the active account, or empty if no accounts exist. Kept in
     * the same shape as the original single-record API so the rest of the
     * codebase didn't have to change.
     */
    data class Auth(
        val server: String,
        val token: String,
        val userId: String,
        val userName: String,
        val deviceId: String
    ) {
        val isLoggedIn get() = server.isNotBlank() && token.isNotBlank() && userId.isNotBlank()
    }

    private val json = Json { ignoreUnknownKeys = true }
    private val tokenCipher = TokenCipher()

    /** Active-account view used by the rest of the app. */
    val authFlow: Flow<Auth> = context.dataStore.data.map { prefs ->
        val deviceId = prefs[DEVICE_ID] ?: ""
        val accounts = readAccounts(prefs[ACCOUNTS_JSON], decryptTokens = true)
            // Migration path for installs that pre-date multi-account: if no
            // accounts list exists but legacy single keys do, synthesize an
            // account from them.
            .ifEmpty {
                val server = prefs[LEGACY_SERVER]
                val token = prefs[LEGACY_TOKEN]
                val userId = prefs[LEGACY_USER_ID]
                val userName = prefs[LEGACY_USER_NAME] ?: ""
                if (!server.isNullOrBlank() && !token.isNullOrBlank() && !userId.isNullOrBlank()) {
                    listOf(
                        AccountRecord(
                            id = UUID.randomUUID().toString(),
                            server = server,
                            token = tokenCipher.decrypt(token),
                            userId = userId,
                            userName = userName
                        )
                    )
                } else emptyList()
            }
        val activeId = prefs[ACTIVE_ID]
        val active = accounts.firstOrNull { it.id == activeId } ?: accounts.firstOrNull()
        if (active != null) {
            Auth(
                server = active.server,
                token = active.token,
                userId = active.userId,
                userName = active.userName,
                deviceId = deviceId
            )
        } else {
            Auth("", "", "", "", deviceId)
        }
    }

    /** Full list for the Settings UI. */
    val accountsFlow: Flow<List<AccountRecord>> = context.dataStore.data.map { prefs ->
        readAccounts(prefs[ACCOUNTS_JSON], decryptTokens = true)
    }

    val activeAccountIdFlow: Flow<String?> = context.dataStore.data.map { it[ACTIVE_ID] }

    private fun readAccounts(s: String?, decryptTokens: Boolean): List<AccountRecord> {
        if (s.isNullOrBlank()) return emptyList()
        return runCatching {
            json.decodeFromString<List<AccountRecord>>(s)
                .map { rec ->
                    if (decryptTokens) rec.copy(token = tokenCipher.decrypt(rec.token)) else rec
                }
        }.getOrDefault(emptyList())
    }

    private fun encryptedRecord(record: AccountRecord): AccountRecord {
        return record.copy(token = tokenCipher.encrypt(record.token))
    }

    suspend fun migrateTokensToEncryptedStorage() {
        context.dataStore.edit { prefs ->
            var changed = false
            val current = readAccounts(prefs[ACCOUNTS_JSON], decryptTokens = false)
            if (current.isNotEmpty()) {
                val migrated = current.map { rec ->
                    if (tokenCipher.isEncrypted(rec.token)) {
                        rec
                    } else {
                        changed = true
                        encryptedRecord(rec)
                    }
                }
                if (changed) prefs[ACCOUNTS_JSON] = json.encodeToString(migrated)
            }

            val legacyToken = prefs[LEGACY_TOKEN]
            if (!legacyToken.isNullOrBlank() && !tokenCipher.isEncrypted(legacyToken)) {
                prefs[LEGACY_TOKEN] = tokenCipher.encrypt(legacyToken)
            }
        }
    }

    suspend fun getOrCreateDeviceId(): String {
        val existing = context.dataStore.data.first()[DEVICE_ID].orEmpty()
        if (existing.isNotBlank()) return existing
        val fresh = UUID.randomUUID().toString()
        context.dataStore.edit { it[DEVICE_ID] = fresh }
        return fresh
    }

    /**
     * Add an account (or refresh an existing one identified by server + user).
     * Sets the new account as active. Returns its id.
     */
    suspend fun addAccount(
        server: String,
        token: String,
        userId: String,
        userName: String
    ): String {
        var resultId = ""
        context.dataStore.edit { prefs ->
            val current = readAccounts(prefs[ACCOUNTS_JSON], decryptTokens = true).toMutableList()
            // Same (server, userId) means re-login — replace the token but
            // keep the id stable so any external references survive.
            val existing = current.indexOfFirst {
                it.server.equals(server, ignoreCase = true) && it.userId == userId
            }
            val rec = if (existing >= 0) {
                val keep = current[existing].copy(
                    server = server,
                    token = token,
                    userName = userName
                )
                current[existing] = keep
                keep
            } else {
                val fresh = AccountRecord(
                    id = UUID.randomUUID().toString(),
                    server = server,
                    token = token,
                    userId = userId,
                    userName = userName
                )
                current += fresh
                fresh
            }
            prefs[ACCOUNTS_JSON] = json.encodeToString(current.map(::encryptedRecord))
            prefs[ACTIVE_ID] = rec.id
            resultId = rec.id
            // Wipe any legacy fields now that we've migrated.
            prefs.remove(LEGACY_SERVER)
            prefs.remove(LEGACY_TOKEN)
            prefs.remove(LEGACY_USER_ID)
            prefs.remove(LEGACY_USER_NAME)
        }
        return resultId
    }

    /** Switch the active account. No-op if `id` doesn't exist. */
    suspend fun switchTo(id: String) {
        context.dataStore.edit { prefs ->
            val exists = readAccounts(prefs[ACCOUNTS_JSON], decryptTokens = false).any { it.id == id }
            if (exists) prefs[ACTIVE_ID] = id
        }
    }

    /**
     * Remove an account by id. If it was the active one, falls back to the
     * first remaining account (if any).
     */
    suspend fun remove(id: String) {
        context.dataStore.edit { prefs ->
            val current = readAccounts(prefs[ACCOUNTS_JSON], decryptTokens = true).toMutableList()
            current.removeAll { it.id == id }
            prefs[ACCOUNTS_JSON] = json.encodeToString(current.map(::encryptedRecord))
            if (prefs[ACTIVE_ID] == id) {
                val fallback = current.firstOrNull()?.id
                if (fallback != null) prefs[ACTIVE_ID] = fallback
                else prefs.remove(ACTIVE_ID)
            }
        }
    }

    /** Sign out everywhere — wipes all accounts. */
    suspend fun clear() {
        context.dataStore.edit { prefs ->
            prefs.remove(ACCOUNTS_JSON)
            prefs.remove(ACTIVE_ID)
            prefs.remove(LEGACY_SERVER)
            prefs.remove(LEGACY_TOKEN)
            prefs.remove(LEGACY_USER_ID)
            prefs.remove(LEGACY_USER_NAME)
        }
    }
}
