package com.storagerush.app.data.model

/**
 * Data class representing user preferences and app stats.
 * Will be persisted to DataStore.
 */
data class AppSettings(
    val largeVideoThresholdMB: Int = 50,              // Default 50 MB threshold for large videos
    val totalStorageFreedBytes: Long = 0,             // Cumulative storage freed by user
    val totalMediaCleaned: Int = 0,                   // Total count of items cleaned
    val autoDeleteEnabled: Boolean = false,           // Future: auto-delete after X days
    val autoDeleteDays: Int = 30,                     // Default: 30 days before auto-delete from trash
    val darkModeEnabled: Boolean = true,              // Default: follow system theme
    val enableNotifications: Boolean = true,          // Show notifications on delete
    val lastCleanupDate: Long = 0,                    // Timestamp of last cleanup
) {
    /**
     * Returns human-readable format of total storage freed (e.g., "2.4 GB")
     */
    fun getTotalStorageFreedReadable(): String {
        return when {
            totalStorageFreedBytes < 1024 -> "$totalStorageFreedBytes B"
            totalStorageFreedBytes < 1024 * 1024 -> "${totalStorageFreedBytes / 1024} KB"
            totalStorageFreedBytes < 1024 * 1024 * 1024 -> String.format("%.1f MB", totalStorageFreedBytes / (1024.0 * 1024.0))
            else -> String.format("%.1f GB", totalStorageFreedBytes / (1024.0 * 1024.0 * 1024.0))
        }
    }

    /**
     * Returns the large video threshold in bytes
     */
    fun getLargeVideoThresholdBytes(): Long {
        return largeVideoThresholdMB.toLong() * 1024 * 1024
    }
}