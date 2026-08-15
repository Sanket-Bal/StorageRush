package com.storagerush.app.data.model.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Mirrors the `public.players` table in Supabase.
 * Field names use camelCase in Kotlin, mapped to snake_case columns
 * via @SerialName to match the Postgres schema exactly.
 */
@Serializable
data class PlayerRecord(
    val id: String? = null,               // UUID, server-generated — null when inserting
    @SerialName("anonymous_id") val anonymousId: String,
    val nickname: String,

    val level: Int = 1,
    @SerialName("current_xp") val currentXp: Long = 0L,
    @SerialName("total_career_xp") val totalCareerXp: Long = 0,
    @SerialName("weekly_streak") val weeklyStreak: Int = 0,
    @SerialName("best_streak") val bestStreak: Int = 0,

    @SerialName("storage_freed_bytes") val storageFreedBytes: Long = 0,
    @SerialName("total_media_cleaned") val totalMediaCleaned: Int = 0,
    @SerialName("largest_single_cleanup_bytes") val largestSingleCleanupBytes: Long = 0,

    @SerialName("last_cleanup_timestamp") val lastCleanupTimestamp: Long? = null
)