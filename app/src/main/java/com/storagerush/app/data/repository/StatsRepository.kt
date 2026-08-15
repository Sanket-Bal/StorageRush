package com.storagerush.app.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.statsDataStore: DataStore<Preferences> by preferencesDataStore(name = "app_stats")

data class AppStats(
    val totalStorageFreedBytes: Long = 0L,
    val totalMediaCleaned: Int = 0,
    val lastSessionFreedBytes: Long = 0L,
    val lastSessionMediaCleaned: Int = 0,
    // Gamification (Phase 2): biggest single deletion ever, for the
    // Leaderboard tab's "Personal best" section.
    val largestSingleCleanupBytes: Long = 0L
) {
    fun getTotalStorageFreedReadable(): String {
        return when {
            totalStorageFreedBytes < 1024 -> "$totalStorageFreedBytes B"
            totalStorageFreedBytes < 1024 * 1024 -> "${totalStorageFreedBytes / 1024} KB"
            totalStorageFreedBytes < 1024 * 1024 * 1024 -> String.format("%.1f MB", totalStorageFreedBytes / (1024.0 * 1024.0))
            else -> String.format("%.1f GB", totalStorageFreedBytes / (1024.0 * 1024.0 * 1024.0))
        }
    }

    fun getLastSessionFreedReadable(): String {
        return when {
            lastSessionFreedBytes < 1024 -> "$lastSessionFreedBytes B"
            lastSessionFreedBytes < 1024 * 1024 -> "${lastSessionFreedBytes / 1024} KB"
            lastSessionFreedBytes < 1024 * 1024 * 1024 -> String.format("%.1f MB", lastSessionFreedBytes / (1024.0 * 1024.0))
            else -> String.format("%.1f GB", lastSessionFreedBytes / (1024.0 * 1024.0 * 1024.0))
        }
    }

    fun getLargestSingleCleanupReadable(): String {
        return when {
            largestSingleCleanupBytes < 1024 -> "$largestSingleCleanupBytes B"
            largestSingleCleanupBytes < 1024 * 1024 -> "${largestSingleCleanupBytes / 1024} KB"
            largestSingleCleanupBytes < 1024 * 1024 * 1024 -> String.format("%.1f MB", largestSingleCleanupBytes / (1024.0 * 1024.0))
            else -> String.format("%.1f GB", largestSingleCleanupBytes / (1024.0 * 1024.0 * 1024.0))
        }
    }
}

class StatsRepository(private val context: Context) {

    private val dataStore = context.statsDataStore

    private val TOTAL_FREED_KEY = longPreferencesKey("total_storage_freed_bytes")
    private val TOTAL_CLEANED_KEY = longPreferencesKey("total_media_cleaned")
    private val LAST_SESSION_FREED_KEY = longPreferencesKey("last_session_freed_bytes")
    private val LAST_SESSION_CLEANED_KEY = longPreferencesKey("last_session_media_cleaned")
    private val LARGEST_SINGLE_CLEANUP_KEY = longPreferencesKey("largest_single_cleanup_bytes")

    /**
     * Get all stats as Flow
     */
    fun getStats(): Flow<AppStats> {
        return dataStore.data.map { preferences ->
            AppStats(
                totalStorageFreedBytes = preferences[TOTAL_FREED_KEY] ?: 0L,
                totalMediaCleaned = (preferences[TOTAL_CLEANED_KEY] ?: 0L).toInt(),
                lastSessionFreedBytes = preferences[LAST_SESSION_FREED_KEY] ?: 0L,
                lastSessionMediaCleaned = (preferences[LAST_SESSION_CLEANED_KEY] ?: 0L).toInt(),
                largestSingleCleanupBytes = preferences[LARGEST_SINGLE_CLEANUP_KEY] ?: 0L
            )
        }
    }

    /**
     * Update stats after successful deletion
     */
    suspend fun updateStatsAfterDeletion(freedBytes: Long, itemsDeleted: Int) {
        dataStore.edit { preferences ->
            val currentTotal = preferences[TOTAL_FREED_KEY] ?: 0L
            val currentCleaned = preferences[TOTAL_CLEANED_KEY] ?: 0L
            val currentLargest = preferences[LARGEST_SINGLE_CLEANUP_KEY] ?: 0L

            preferences[TOTAL_FREED_KEY] = currentTotal + freedBytes
            preferences[TOTAL_CLEANED_KEY] = currentCleaned + itemsDeleted
            preferences[LAST_SESSION_FREED_KEY] = freedBytes
            preferences[LAST_SESSION_CLEANED_KEY] = itemsDeleted.toLong()
            preferences[LARGEST_SINGLE_CLEANUP_KEY] = maxOf(currentLargest, freedBytes)
        }
    }

    /**
     * Reset last session stats (after showing notification)
     */
    suspend fun resetLastSessionStats() {
        dataStore.edit { preferences ->
            preferences[LAST_SESSION_FREED_KEY] = 0L
            preferences[LAST_SESSION_CLEANED_KEY] = 0L
        }
    }

    /**
     * LOGIN RESTORE: overwrites local lifetime stats with values pulled
     * from the cloud player record, for a returning user logging back in
     * on a fresh install. lastSession* fields are deliberately left at 0 —
     * there's no "last session" yet on this fresh install, and restoring
     * a stale one from the old device would trigger a misleading
     * "you just freed X" notification that didn't actually just happen.
     */
    suspend fun restoreFromCloud(
        totalStorageFreedBytes: Long,
        totalMediaCleaned: Int,
        largestSingleCleanupBytes: Long
    ) {
        dataStore.edit { preferences ->
            preferences[TOTAL_FREED_KEY] = totalStorageFreedBytes
            preferences[TOTAL_CLEANED_KEY] = totalMediaCleaned.toLong()
            preferences[LARGEST_SINGLE_CLEANUP_KEY] = largestSingleCleanupBytes
        }
    }

    /**
     * Clear all stats (for testing or manual reset)
     */
    suspend fun clearAllStats() {
        dataStore.edit { preferences ->
            preferences[TOTAL_FREED_KEY] = 0L
            preferences[TOTAL_CLEANED_KEY] = 0L
            preferences[LAST_SESSION_FREED_KEY] = 0L
            preferences[LAST_SESSION_CLEANED_KEY] = 0L
            preferences[LARGEST_SINGLE_CLEANUP_KEY] = 0L
        }
    }
}