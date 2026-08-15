package com.storagerush.app.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.storagerush.app.data.gamification.XpCalculator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.ZoneId

private val Context.playerDataStore: DataStore<Preferences> by preferencesDataStore(name = "player_progress")

/**
 * Streak weeks are fixed to IST regardless of device timezone/locale,
 * since the weekly cadence itself (spec) was chosen for Indian usage
 * patterns — a traveling user shouldn't get a different streak boundary.
 */
private val STREAK_ZONE: ZoneId = ZoneId.of("Asia/Kolkata")

data class PlayerState(
    val level: Int = 1,
    val currentXp: Long = 0L,          // XP earned toward the CURRENT level
    val totalCareerXp: Long = 0L,      // Lifetime XP, never decreases
    val weeklyStreak: Int = 0,
    val bestStreak: Int = 0,
    val lastCleanupTimestamp: Long = 0L
) {
    val xpRequiredForNextLevel: Long
        get() = XpCalculator.xpRequiredForLevel(level)

    val xpProgressFraction: Float
        get() {
            val required = xpRequiredForNextLevel
            if (required <= 0L) return 0f
            return (currentXp.toFloat() / required.toFloat()).coerceIn(0f, 1f)
        }
}

/**
 * Result of recording a cleanup — how much XP was earned and how many
 * levels (if any) were gained. `levelsGained` can be >1 for a single huge
 * deletion. Reserved mainly for the level-up celebration UI (Phase 2), but
 * returned now so callers have it without a second read.
 */
data class CleanupRewardResult(
    val xpEarned: Long,
    val levelsGained: Int,
    val newState: PlayerState
)

class PlayerRepository(private val context: Context) {

    private val dataStore = context.playerDataStore

    private val LEVEL_KEY = intPreferencesKey("player_level")
    private val CURRENT_XP_KEY = longPreferencesKey("player_current_xp")
    private val TOTAL_CAREER_XP_KEY = longPreferencesKey("player_total_career_xp")
    private val WEEKLY_STREAK_KEY = intPreferencesKey("weekly_streak")
    private val BEST_STREAK_KEY = intPreferencesKey("best_streak")
    private val LAST_CLEANUP_TIMESTAMP_KEY = longPreferencesKey("last_cleanup_timestamp")

    fun getPlayerState(): Flow<PlayerState> {
        return dataStore.data.map { prefs ->
            PlayerState(
                level = prefs[LEVEL_KEY] ?: 1,
                currentXp = prefs[CURRENT_XP_KEY] ?: 0L,
                totalCareerXp = prefs[TOTAL_CAREER_XP_KEY] ?: 0L,
                weeklyStreak = prefs[WEEKLY_STREAK_KEY] ?: 0,
                bestStreak = prefs[BEST_STREAK_KEY] ?: 0,
                lastCleanupTimestamp = prefs[LAST_CLEANUP_TIMESTAMP_KEY] ?: 0L
            )
        }
    }

    /**
     * Records a completed deletion: updates the weekly streak, awards XP
     * (scaled by the resulting streak), and rolls over any level-ups.
     *
     * Call this from the SAME place StatsRepository.updateStatsAfterDeletion()
     * is already called — i.e. TrashBinViewModel.onDeletionSuccess() and
     * deleteSelectedItemsFallback() — so XP and the lifetime stats total
     * always move together.
     */
    suspend fun recordCleanup(freedBytes: Long): CleanupRewardResult {
        var xpEarned = 0L
        var levelsGained = 0
        var resultState = PlayerState()

        dataStore.edit { prefs ->
            val now = System.currentTimeMillis()
            val lastTimestamp = prefs[LAST_CLEANUP_TIMESTAMP_KEY] ?: 0L
            val previousStreak = prefs[WEEKLY_STREAK_KEY] ?: 0

            val updatedStreak = updateStreak(previousStreak, lastTimestamp, now)

            xpEarned = XpCalculator.calculateXp(freedBytes, updatedStreak)

            var level = prefs[LEVEL_KEY] ?: 1
            var currentXp = (prefs[CURRENT_XP_KEY] ?: 0L) + xpEarned
            val totalCareerXp = (prefs[TOTAL_CAREER_XP_KEY] ?: 0L) + xpEarned

            // Roll over level-ups — a single large deletion can jump
            // multiple levels at once, so this loops rather than checking once.
            var requiredForNext = XpCalculator.xpRequiredForLevel(level)
            while (requiredForNext > 0L && currentXp >= requiredForNext) {
                currentXp -= requiredForNext
                level += 1
                levelsGained += 1
                requiredForNext = XpCalculator.xpRequiredForLevel(level)
            }

            val newBestStreak = maxOf(prefs[BEST_STREAK_KEY] ?: 0, updatedStreak)

            prefs[LEVEL_KEY] = level
            prefs[CURRENT_XP_KEY] = currentXp
            prefs[TOTAL_CAREER_XP_KEY] = totalCareerXp
            prefs[WEEKLY_STREAK_KEY] = updatedStreak
            prefs[BEST_STREAK_KEY] = newBestStreak
            prefs[LAST_CLEANUP_TIMESTAMP_KEY] = now

            resultState = PlayerState(
                level = level,
                currentXp = currentXp,
                totalCareerXp = totalCareerXp,
                weeklyStreak = updatedStreak,
                bestStreak = newBestStreak,
                lastCleanupTimestamp = now
            )
        }

        return CleanupRewardResult(xpEarned, levelsGained, resultState)
    }

    /**
     * LOGIN RESTORE: overwrites local progression with values pulled from
     * the cloud player record, for when a returning user logs back in on
     * a fresh install (local DataStore is empty/default at that point,
     * nothing to lose). Unlike recordCleanup(), this is a direct
     * overwrite — no XP math, no streak calculation, no level-up rollover,
     * since these values already represent the correct final state as of
     * the last successful cloud sync.
     */
    suspend fun restoreFromCloud(
        level: Int,
        currentXp: Long,
        totalCareerXp: Long,
        weeklyStreak: Int,
        bestStreak: Int,
        lastCleanupTimestamp: Long
    ) {
        dataStore.edit { prefs ->
            prefs[LEVEL_KEY] = level
            prefs[CURRENT_XP_KEY] = currentXp
            prefs[TOTAL_CAREER_XP_KEY] = totalCareerXp
            prefs[WEEKLY_STREAK_KEY] = weeklyStreak
            prefs[BEST_STREAK_KEY] = bestStreak
            prefs[LAST_CLEANUP_TIMESTAMP_KEY] = lastCleanupTimestamp
        }
    }

    /**
     * LOGOUT: overwrites local progression back to fresh new-player
     * defaults. Called when a user logs out of a linked account — the
     * level/XP/streak they'd built up belonged to that cloud identity,
     * so once unlinked, local state should read like a brand new
     * install rather than continuing to show someone else's numbers.
     * Same direct-overwrite shape as restoreFromCloud(), just with
     * PlayerState()'s defaults instead of cloud values.
     */
    suspend fun resetToNewPlayer() {
        val defaults = PlayerState()
        dataStore.edit { prefs ->
            prefs[LEVEL_KEY] = defaults.level
            prefs[CURRENT_XP_KEY] = defaults.currentXp
            prefs[TOTAL_CAREER_XP_KEY] = defaults.totalCareerXp
            prefs[WEEKLY_STREAK_KEY] = defaults.weeklyStreak
            prefs[BEST_STREAK_KEY] = defaults.bestStreak
            prefs[LAST_CLEANUP_TIMESTAMP_KEY] = defaults.lastCleanupTimestamp
        }
    }

    /**
     * Weekly streak logic, compared by IST week number:
     * - No prior cleanup ever -> streak starts at 1
     * - Same week as last cleanup -> streak unchanged (already counted)
     * - Exactly the next week -> streak increments by 1
     * - A gap of 2+ weeks -> streak restarts at 1 (missed a week = broken)
     */
    private fun updateStreak(previousStreak: Int, lastTimestamp: Long, now: Long): Int {
        if (lastTimestamp == 0L) return 1

        val lastWeek = weekIdentifier(lastTimestamp)
        val currentWeek = weekIdentifier(now)

        return when (currentWeek - lastWeek) {
            0L -> if (previousStreak == 0) 1 else previousStreak
            1L -> previousStreak + 1
            else -> 1
        }
    }

    /**
     * A monotonically increasing "week number" (epoch weeks, IST-adjusted)
     * so subtracting two timestamps' identifiers gives exactly how many
     * full weeks apart they are — correct across month/year boundaries,
     * unlike comparing calendar week-of-year fields directly.
     */
    private fun weekIdentifier(timestampMillis: Long): Long {
        val date = Instant.ofEpochMilli(timestampMillis).atZone(STREAK_ZONE).toLocalDate()
        return Math.floorDiv(date.toEpochDay(), 7L)
    }
}