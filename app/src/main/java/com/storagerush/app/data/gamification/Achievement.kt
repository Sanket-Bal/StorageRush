package com.storagerush.app.data.gamification

import com.storagerush.app.data.repository.AppStats
import com.storagerush.app.data.repository.PlayerState

enum class AchievementCategory {
    STORAGE, LEVEL, STREAK
}

/**
 * A single achievement badge. Unlock status is DERIVED, not stored — every
 * badge here is a threshold against a cumulative value (total storage
 * freed, level, best streak) that only ever goes up, so "unlocked" is
 * always just "is the current value >= the threshold". This avoids a
 * whole separate DataStore layer just to track which badges have been
 * seen; it also means an unlock is correct immediately, with no chance of
 * drifting out of sync with the underlying stats.
 */
data class Achievement(
    val id: String,
    val emoji: String,
    val title: String,
    val description: String,
    val category: AchievementCategory,
    val isUnlocked: (player: PlayerState, stats: AppStats) -> Boolean
)

object AchievementDefinitions {

    private const val MB = 1024L * 1024L
    private const val GB = 1024L * MB

    /**
     * Exactly the 12 badges from the approved gamification spec — 5
     * storage milestones, 4 level milestones, 3 streak milestones.
     */
    val ALL: List<Achievement> = listOf(
        // Storage-based
        Achievement(
            id = "storage_100mb",
            emoji = "\uD83C\uDF31", // 🌱
            title = "Starter",
            description = "Free 100 MB",
            category = AchievementCategory.STORAGE
        ) { _, stats -> stats.totalStorageFreedBytes >= 100 * MB },

        Achievement(
            id = "storage_1gb",
            emoji = "\uD83D\uDCE6", // 📦
            title = "Hoarder",
            description = "Free 1 GB",
            category = AchievementCategory.STORAGE
        ) { _, stats -> stats.totalStorageFreedBytes >= 1 * GB },

        Achievement(
            id = "storage_5gb",
            emoji = "\uD83D\uDE80", // 🚀
            title = "Speedrunner",
            description = "Free 5 GB",
            category = AchievementCategory.STORAGE
        ) { _, stats -> stats.totalStorageFreedBytes >= 5 * GB },

        Achievement(
            id = "storage_50gb",
            emoji = "\uD83C\uDF0D", // 🌍
            title = "Planet Saver",
            description = "Free 50 GB",
            category = AchievementCategory.STORAGE
        ) { _, stats -> stats.totalStorageFreedBytes >= 50 * GB },

        Achievement(
            id = "storage_500gb",
            emoji = "\uD83C\uDFC6", // 🏆
            title = "Legend",
            description = "Free 500 GB",
            category = AchievementCategory.STORAGE
        ) { _, stats -> stats.totalStorageFreedBytes >= 500 * GB },

        // Level-based
        Achievement(
            id = "level_5",
            emoji = "\uD83C\uDFAF", // 🎯
            title = "Novice",
            description = "Reach Level 5",
            category = AchievementCategory.LEVEL
        ) { player, _ -> player.level >= 5 },

        Achievement(
            id = "level_15",
            emoji = "\u26A1", // ⚡
            title = "Warrior",
            description = "Reach Level 15",
            category = AchievementCategory.LEVEL
        ) { player, _ -> player.level >= 15 },

        Achievement(
            id = "level_30",
            emoji = "\uD83D\uDC51", // 👑
            title = "Master",
            description = "Reach Level 30",
            category = AchievementCategory.LEVEL
        ) { player, _ -> player.level >= 30 },

        Achievement(
            id = "level_50",
            emoji = "\uD83C\uDF1F", // 🌟
            title = "Legendary",
            description = "Reach Level 50",
            category = AchievementCategory.LEVEL
        ) { player, _ -> player.level >= 50 },

        // Streak-based (uses bestStreak, so an achievement earned during a
        // long streak stays earned even after that streak later resets)
        Achievement(
            id = "streak_4",
            emoji = "\uD83D\uDD25", // 🔥
            title = "Consistent",
            description = "4-week streak",
            category = AchievementCategory.STREAK
        ) { player, _ -> player.bestStreak >= 4 },

        Achievement(
            id = "streak_10",
            emoji = "\uD83D\uDD25\uD83D\uDD25", // 🔥🔥
            title = "On Fire",
            description = "10-week streak",
            category = AchievementCategory.STREAK
        ) { player, _ -> player.bestStreak >= 10 },

        Achievement(
            id = "streak_26",
            emoji = "\uD83D\uDD25\uD83D\uDD25\uD83D\uDD25", // 🔥🔥🔥
            title = "Unstoppable",
            description = "26-week streak",
            category = AchievementCategory.STREAK
        ) { player, _ -> player.bestStreak >= 26 }
    )
}