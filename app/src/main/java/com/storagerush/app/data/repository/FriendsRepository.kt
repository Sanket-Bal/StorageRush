package com.storagerush.app.data.repository

import android.content.Context
import android.util.Log
import com.storagerush.app.data.model.remote.FriendCodeRecord
import com.storagerush.app.data.model.remote.FriendLeaderboardEntry
import com.storagerush.app.data.model.remote.FriendRecord
import com.storagerush.app.data.remote.SupabaseClientProvider
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Wraps a `friends` row joined against the friend's `players` row.
 * Relies on Postgres's default foreign-key constraint naming
 * (`friends_friend_id_fkey`), auto-generated since the schema's
 * `friend_id` column REFERENCES players(id) without an explicit
 * constraint name. If Postgrest embedding fails to resolve at runtime,
 * this is the first thing to check against the actual constraint name
 * in the Supabase dashboard (Database -> Tables -> friends -> foreign keys).
 */
@Serializable
private data class FriendWithPlayerInfo(
    @SerialName("friend_id") val friendId: String,
    val players: FriendLeaderboardEntry
)

/**
 * Handles Phase B: friend codes (generate/redeem) and the friends-scoped
 * leaderboard query. Mirrors CloudSyncRepository's shape — Context-based
 * constructor, suspend functions, Dispatchers.IO, Result-wrapped writes.
 */
class FriendsRepository(private val context: Context) {

    private val client = SupabaseClientProvider.client

    // Excludes visually ambiguous characters (0/O, 1/I) so codes are easy
    // to read aloud or retype by hand.
    private val CODE_CHARSET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    private val CODE_LENGTH = 9 // matches the friend_codes.code CHECK constraint

    /**
     * Returns the player's existing unused, unexpired friend code if one
     * exists, otherwise generates and stores a new one. Call this whenever
     * the "invite a friend" screen opens — avoids piling up unused codes
     * from repeated visits.
     */
    suspend fun getOrCreateFriendCode(playerId: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val existing = client.from("friend_codes")
                .select {
                    filter {
                        eq("player_id", playerId)
                        eq("is_used", false)
                    }
                }
                .decodeList<FriendCodeRecord>()
                .firstOrNull()

            if (existing != null) {
                return@withContext Result.success(existing.code)
            }

            val newCode = generateRandomCode()
            val record = FriendCodeRecord(
                code = newCode,
                playerId = playerId
            )
            client.from("friend_codes").insert(record)
            Result.success(newCode)
        } catch (e: Exception) {
            Log.e("FriendsRepository", "getOrCreateFriendCode failed", e)
            Result.failure(e)
        }
    }

    /**
     * Redeems a friend code via the `redeem_friend_code` Postgres function
     * (SECURITY DEFINER — see the SQL migration for its definition).
     *
     * This can't be done as two plain client-side inserts: the `friends`
     * table's RLS policy only allows a row where `user_id` equals the
     * CALLER's own player id, so the reciprocal row (the code owner
     * being added as the redeemer's friend) always gets rejected — the
     * caller isn't the owner, so RLS correctly refuses to let them write
     * a row on the owner's behalf. Doing both inserts inside a
     * SECURITY DEFINER function, which resolves the caller from
     * auth.uid() server-side rather than trusting a client-supplied id,
     * is the safe way to grant that one exception.
     */
    suspend fun redeemFriendCode(code: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                client.postgrest.rpc(
                    function = "redeem_friend_code",
                    parameters = buildJsonObject {
                        put("input_code", code)
                    }
                )
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e("FriendsRepository", "redeemFriendCode failed", e)
                Result.failure(e)
            }
        }

    /**
     * Friends-scoped leaderboard: this player's added friends, sorted by
     * lifetime XP descending. Does NOT include the current player's own
     * row — StatsScreen's Friends tab should add that separately if it
     * wants to show "your rank among friends".
     */
    suspend fun getFriendsLeaderboard(playerId: String): List<FriendLeaderboardEntry> =
        withContext(Dispatchers.IO) {
            val rows = client.from("friends")
                .select(columns = Columns.raw(
                    "friend_id, players!friends_friend_id_fkey(nickname, level, total_career_xp, weekly_streak)"
                )) {
                    filter { eq("user_id", playerId) }
                }
                .decodeList<FriendWithPlayerInfo>()

            rows.map { it.players }.sortedByDescending { it.totalCareerXp }
        }

    private fun generateRandomCode(): String {
        return (1..CODE_LENGTH)
            .map { CODE_CHARSET.random() }
            .joinToString("")
    }
}