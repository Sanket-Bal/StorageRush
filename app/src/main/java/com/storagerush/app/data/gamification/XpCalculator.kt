package com.storagerush.app.data.gamification

import kotlin.math.floor
import kotlin.math.pow

/**
 * Pure XP/level math for the gamification system. No Android dependencies
 * on purpose — this is the one part of the system worth unit testing in
 * isolation, since a formula bug here silently mis-rewards every deletion.
 */
object XpCalculator {

    // Tier brackets, by MB freed in a single deletion action.
    private const val TIER_1_MAX_MB = 50.0
    private const val TIER_2_MAX_MB = 200.0
    private const val TIER_3_MAX_MB = 500.0

    private const val TIER_1_MULTIPLIER = 1.0
    private const val TIER_2_MULTIPLIER = 1.2
    private const val TIER_3_MULTIPLIER = 1.5
    private const val TIER_4_MULTIPLIER = 2.0

    private const val MAX_STREAK_WEEKS_FOR_BONUS = 10
    private const val STREAK_BONUS_PER_WEEK = 0.05

    /**
     * Hybrid XP formula (per the approved spec): linear base + an
     * exponential bonus for large single deletions + a tiered multiplier
     * by file-size bracket, then scaled by the player's current weekly
     * streak.
     *
     * @param freedBytes bytes freed by this single deletion action
     * @param streakWeeks the player's CURRENT weekly streak (after this
     *   cleanup has already been factored into the streak, so the bonus
     *   reflects the streak that earned it)
     */
    fun calculateXp(freedBytes: Long, streakWeeks: Int): Long {
        if (freedBytes <= 0L) return 0L

        val freedMb = freedBytes / (1024.0 * 1024.0)

        val baseXp = freedMb * 1.0
        val exponentialBonus = maxOf(0.0, (freedMb - TIER_1_MAX_MB) * 0.5)
        val tierMultiplier = tierMultiplierFor(freedMb)
        val streakMultiplier = streakMultiplierFor(streakWeeks)

        val totalXp = (baseXp + exponentialBonus) * tierMultiplier * streakMultiplier

        // Any nonzero deletion earns at least 1 XP, so tiny cleanups still
        // register instead of silently rounding to zero.
        return totalXp.toLong().coerceAtLeast(1L)
    }

    private fun tierMultiplierFor(freedMb: Double): Double = when {
        freedMb <= TIER_1_MAX_MB -> TIER_1_MULTIPLIER
        freedMb <= TIER_2_MAX_MB -> TIER_2_MULTIPLIER
        freedMb <= TIER_3_MAX_MB -> TIER_3_MULTIPLIER
        else -> TIER_4_MULTIPLIER
    }

    private fun streakMultiplierFor(streakWeeks: Int): Double {
        val cappedWeeks = streakWeeks.coerceIn(0, MAX_STREAK_WEEKS_FOR_BONUS)
        return 1.0 + (cappedWeeks * STREAK_BONUS_PER_WEEK)
    }

    /**
     * Soft-curve XP requirement to advance FROM [level] to [level] + 1.
     * Grows gradually (linear term + a mild ^1.5 term) so early levels
     * come quickly and later levels take sustained effort without ever
     * spiking into "impossible grind" territory.
     *
     * Tuned so level 1 -> 2 costs ~50 XP (≈50 MB freed, since 1 MB ≈ 1 XP
     * below the tier-1 threshold) — the original curve started at 500 XP
     * (≈500 MB), which asked a brand-new user to clear out half a
     * gigabyte before their very first level-up. This curve keeps that
     * same "gets harder later, never punishing" shape, just rescaled so
     * the very first step is a quick, encouraging win instead of a wall.
     */
    fun xpRequiredForLevel(level: Int): Long {
        val n = (level - 1).coerceAtLeast(0)
        val required = 50.0 + (n * 40.0) + floor(n.toDouble().pow(1.5) * 15.0)
        return required.toLong()
    }
}