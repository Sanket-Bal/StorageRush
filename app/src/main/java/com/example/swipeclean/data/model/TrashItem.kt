package com.example.swipeclean.data.model

import kotlinx.serialization.Serializable

@Serializable
data class TrashItem(
    val mediaId: Long,
    val displayName: String,
    val sizeBytes: Long,
    val mimeType: String,
    val dateAddedSeconds: Long,
    val uri: String,  // ✅ NEW: Store the actual MediaStore URI
    val createdAtMillis: Long = System.currentTimeMillis()
) {
    /**
     * Get human-readable file size
     */
    fun getReadableSize(): String {
        return when {
            sizeBytes < 1024 -> "$sizeBytes B"
            sizeBytes < 1024 * 1024 -> "${sizeBytes / 1024} KB"
            sizeBytes < 1024 * 1024 * 1024 -> String.format("%.1f MB", sizeBytes / (1024.0 * 1024.0))
            else -> String.format("%.1f GB", sizeBytes / (1024.0 * 1024.0 * 1024.0))
        }
    }

    /**
     * Check if item is older than given days (for auto-purge)
     */
    fun isOlderThan(days: Int): Boolean {
        val dayInMillis = days * 24 * 60 * 60 * 1000L
        val ageMillis = System.currentTimeMillis() - createdAtMillis
        return ageMillis > dayInMillis
    }

    /**
     * Get age in days
     */
    fun getAgeInDays(): Int {
        val ageMillis = System.currentTimeMillis() - createdAtMillis
        return (ageMillis / (24 * 60 * 60 * 1000L)).toInt()
    }
}