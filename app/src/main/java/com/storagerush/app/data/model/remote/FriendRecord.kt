package com.storagerush.app.data.model.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Mirrors the `public.friends` table in Supabase.
 * One-way relationship: if A adds B, only A -> B is stored.
 */
@Serializable
data class FriendRecord(
    val id: String? = null,
    @SerialName("user_id") val userId: String,
    @SerialName("friend_id") val friendId: String,
    @SerialName("friend_code_used") val friendCodeUsed: String? = null
)

/**
 * Joined shape for displaying a friend's leaderboard row.
 * Populated via a Postgrest select with a foreign-table join against
 * `players`, not the raw `friends` table — see FriendsRepository
 * (Phase B, not yet created).
 */
@Serializable
data class FriendLeaderboardEntry(
    val nickname: String,
    val level: Int,
    @SerialName("total_career_xp") val totalCareerXp: Long,
    @SerialName("weekly_streak") val weeklyStreak: Int
)