package com.storagerush.app.data.repository

import android.content.Context
import android.util.Log
import com.storagerush.app.data.model.remote.FriendLeaderboardEntry
import com.storagerush.app.data.model.remote.PlayerRecord
import com.storagerush.app.data.remote.SupabaseClientProvider
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.OTP
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Handles anonymous auth + player record sync with Supabase.
 * Mirrors the app's existing repository pattern: takes Context directly,
 * exposes suspend functions, does its IO work on Dispatchers.IO.
 *
 * Context is accepted for consistency with the rest of the repository
 * layer (e.g. future local caching of sync status), though this
 * implementation doesn't currently touch it directly.
 */
class CloudSyncRepository(private val context: Context) {

    private val client = SupabaseClientProvider.client

    /**
     * Signs in anonymously if there's no existing session.
     * Safe to call on every app launch — no-ops if already signed in.
     * Returns the Supabase auth user ID (maps to players.anonymous_id).
     */
    suspend fun ensureSignedIn(): String = withContext(Dispatchers.IO) {
        val existingUser = client.auth.currentUserOrNull()
        if (existingUser != null) {
            return@withContext existingUser.id
        }
        client.auth.signInAnonymously()
        client.auth.currentUserOrNull()?.id
            ?: throw IllegalStateException("Anonymous sign-in did not return a user")
    }

    /** Checks if a nickname is already taken (client-side pre-check before insert). */
    suspend fun isNicknameAvailable(nickname: String): Boolean = withContext(Dispatchers.IO) {
        val existing = client.from("players")
            .select {
                filter { eq("nickname", nickname) }
            }
            .decodeList<PlayerRecord>()
        existing.isEmpty()
    }

    /**
     * Creates the player record on first-ever sync (after nickname is chosen).
     * Call once, right after ensureSignedIn() + nickname dialog confirmation.
     */
    suspend fun createPlayerRecord(anonymousId: String, nickname: String): Result<PlayerRecord> =
        withContext(Dispatchers.IO) {
            try {
                val record = PlayerRecord(
                    anonymousId = anonymousId,
                    nickname = nickname
                )
                val inserted = client.from("players")
                    .insert(record) { select() }
                    .decodeSingle<PlayerRecord>()
                Result.success(inserted)
            } catch (e: RestException) {
                Log.e("CloudSyncRepository", "createPlayerRecord failed", e)
                Result.failure(e)
            }
        }

    /** Fetches the current player's cloud record, or null if not yet created. */
    suspend fun getPlayerRecord(anonymousId: String): PlayerRecord? = withContext(Dispatchers.IO) {
        client.from("players")
            .select { filter { eq("anonymous_id", anonymousId) } }
            .decodeSingleOrNull<PlayerRecord>()
    }

    /**
     * Updates the nickname on an existing player record. Used by the
     * Profile tab's nickname editor — distinct from createPlayerRecord(),
     * which is only for first-ever setup (insert, not update). Caller is
     * responsible for an availability pre-check (see isNicknameAvailable())
     * before calling this, same convention as createPlayerRecord's caller.
     */
    suspend fun updateNickname(anonymousId: String, nickname: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                client.from("players")
                    .update({ set("nickname", nickname) }) {
                        filter { eq("anonymous_id", anonymousId) }
                    }
                Result.success(Unit)
            } catch (e: RestException) {
                Log.e("CloudSyncRepository", "updateNickname failed", e)
                Result.failure(e)
            }
        }

    /**
     * Global leaderboard: the top [limit] players across the whole game,
     * ranked by lifetime XP descending — same ranking metric and same
     * joined shape (FriendLeaderboardEntry) as FriendsRepository's
     * friends-scoped query, just against the full "players" table with
     * no friends filter. Reuses FriendLeaderboardEntry rather than a new
     * model since the two queries return identical columns (nickname,
     * level, total_career_xp, weekly_streak).
     *
     * Explicitly selects only those four columns (not the default `*`) —
     * the players table has several more columns (storage stats, ids,
     * etc.) that FriendLeaderboardEntry doesn't declare, and this
     * project's JSON decoding is strict (no ignoreUnknownKeys), so a
     * plain select() here would throw at decode time.
     */
    suspend fun getGlobalLeaderboard(limit: Int = 50): List<FriendLeaderboardEntry> =
        withContext(Dispatchers.IO) {
            client.from("players")
                .select(columns = Columns.raw("nickname, level, total_career_xp, weekly_streak")) {
                    order("total_career_xp", Order.DESCENDING)
                    limit(limit.toLong())
                }
                .decodeList<FriendLeaderboardEntry>()
        }

    /**
     * Pushes local progression (level/XP/streak/storage stats) to Supabase.
     * Call this after every cleanup session, right after local DataStore is updated.
     */
    suspend fun syncPlayerProgress(
        anonymousId: String,
        level: Int,
        currentXp: Long,
        totalCareerXp: Long,
        weeklyStreak: Int,
        bestStreak: Int,
        storageFreedBytes: Long,
        totalMediaCleaned: Int,
        largestSingleCleanupBytes: Long,
        lastCleanupTimestamp: Long
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            client.from("players")
                .update(
                    {
                        set("level", level)
                        set("current_xp", currentXp)
                        set("total_career_xp", totalCareerXp)
                        set("weekly_streak", weeklyStreak)
                        set("best_streak", bestStreak)
                        set("storage_freed_bytes", storageFreedBytes)
                        set("total_media_cleaned", totalMediaCleaned)
                        set("largest_single_cleanup_bytes", largestSingleCleanupBytes)
                        set("last_cleanup_timestamp", lastCleanupTimestamp)
                    }
                ) {
                    filter { eq("anonymous_id", anonymousId) }
                }
            Result.success(Unit)
        } catch (e: RestException) {
            Log.e("CloudSyncRepository", "syncPlayerProgress failed", e)
            Result.failure(e)
        }
    }

    // ---------------------------------------------------------------
    // Account linking (Sign Up / Log In via email OTP)
    // ---------------------------------------------------------------

    /**
     * SIGN UP path: sends an email-change OTP to link [email] to the
     * CURRENT session (whichever anonymous identity is already active).
     * Requires the user to already be signed in — call ensureSignedIn()
     * first if unsure.
     *
     * NOTE: by default Supabase sends a clickable confirmation LINK, not
     * a typed code — getting an OTP code requires the "Change Email
     * Address" template in the Supabase dashboard to include {{ .Token }}.
     * See the accompanying setup note if verifySignUpOtp() below fails
     * with something like "invalid token" despite a correct-looking code.
     */
    suspend fun sendSignUpOtp(email: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // updateUser() needs a real session (one with a `sub` claim) to
            // attach as the Authorization bearer token. Without this call,
            // a fresh install / expired session has no such token and
            // supabase-kt falls back to the anon key, which Supabase then
            // rejects with "bad_jwt: missing sub claim".
            ensureSignedIn()
            client.auth.updateUser {
                this.email = email
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("CloudSyncRepository", "sendSignUpOtp failed", e)
            Result.failure(e)
        }
    }

    /**
     * Verifies the code sent by sendSignUpOtp(). On success, the CURRENT
     * session (previously anonymous) becomes a permanent account under
     * the same user ID — no data migration needed, the existing players
     * row is now reachable by logging in with this email in the future.
     */
    suspend fun verifySignUpOtp(email: String, code: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            client.auth.verifyEmailOtp(
                type = OtpType.Email.EMAIL_CHANGE,
                email = email,
                token = code
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("CloudSyncRepository", "verifySignUpOtp failed", e)
            Result.failure(e)
        }
    }

    /**
     * LOG IN path: sends a sign-in OTP to an email that should already
     * belong to an existing permanent account (createUser = false, so a
     * typo'd/unregistered email fails cleanly instead of silently
     * creating a new blank account).
     */
    suspend fun sendLoginOtp(email: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            client.auth.signInWith(OTP) {
                this.email = email
                createUser = false
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("CloudSyncRepository", "sendLoginOtp failed", e)
            Result.failure(e)
        }
    }

    /**
     * Verifies the code sent by sendLoginOtp(). On success, the CURRENT
     * session is REPLACED by the returning permanent account's session —
     * this is the moment the app "reconnects" to the user's original ID
     * from before their reinstall. Caller should follow this with
     * getPlayerRecord(anonymousId = <new current user id>) to fetch their
     * existing progress and restore it locally (see PlayerRepository/
     * StatsRepository.restoreFromCloud()).
     */
    suspend fun verifyLoginOtp(email: String, code: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            client.auth.verifyEmailOtp(
                type = OtpType.Email.EMAIL,
                email = email,
                token = code
            )
            val userId = client.auth.currentUserOrNull()?.id
                ?: return@withContext Result.failure(IllegalStateException("Login succeeded but no user id returned"))
            Result.success(userId)
        } catch (e: Exception) {
            Log.e("CloudSyncRepository", "verifyLoginOtp failed", e)
            Result.failure(e)
        }
    }

    /**
     * Signs out of the current Supabase session entirely (no session left
     * behind — NOT replaced by a fresh anonymous one; whatever calls this
     * next that needs auth, e.g. ensureSignedIn(), will establish a new
     * anonymous session on its own the next time it's needed).
     *
     * Used by the menu's "Log Out" action. Local DataStore (level, streak,
     * stats, nickname) is completely untouched by this — logging out only
     * severs the cloud link; it never deletes on-device progress. Any
     * cloud sync attempted after this point (e.g. TrashBinViewModel's
     * fire-and-forget syncProgressToCloud()) will end up targeting a
     * brand-new anonymous identity with no matching "players" row, so it
     * will silently affect zero rows rather than error or corrupt data —
     * this is already handled gracefully by the existing Result-based
     * error handling in syncPlayerProgress() above.
     */
    suspend fun signOut(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            client.auth.signOut()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("CloudSyncRepository", "signOut failed", e)
            Result.failure(e)
        }
    }
}