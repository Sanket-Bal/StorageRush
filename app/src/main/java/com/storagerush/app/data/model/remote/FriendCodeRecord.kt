package com.storagerush.app.data.model.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Mirrors the `public.friend_codes` table in Supabase.
 */
@Serializable
data class FriendCodeRecord(
    val id: String? = null,
    val code: String,
    @SerialName("player_id") val playerId: String,
    @SerialName("is_used") val isUsed: Boolean = false,
    @SerialName("used_by_id") val usedById: String? = null
)