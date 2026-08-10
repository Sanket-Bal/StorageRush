package com.storagerush.app.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.storagerush.app.data.gamification.Achievement
import com.storagerush.app.data.gamification.AchievementDefinitions
import com.storagerush.app.data.repository.AppStats
import com.storagerush.app.data.repository.PlayerRepository
import com.storagerush.app.data.repository.PlayerState
import com.storagerush.app.data.repository.StatsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Combined state for the 3-tab Stats screen (Progress / Achievements /
 * Leaderboard). `achievements` is the static definition list; unlock
 * status for each is computed on read via Achievement.isUnlocked(), not
 * stored here, so it's always consistent with the latest appStats/playerState.
 */
data class StatsUiState(
    val appStats: AppStats = AppStats(),
    val playerState: PlayerState = PlayerState(),
    val achievements: List<Achievement> = AchievementDefinitions.ALL,
    val isLoading: Boolean = true
) {
    val unlockedAchievements: List<Achievement>
        get() = achievements.filter { it.isUnlocked(playerState, appStats) }

    val unlockedCount: Int
        get() = unlockedAchievements.size
}

class StatsViewModel(context: Context) : ViewModel() {

    private val statsRepository = StatsRepository(context)
    private val playerRepository = PlayerRepository(context)

    val uiState: StateFlow<StatsUiState> = combine(
        statsRepository.getStats(),
        playerRepository.getPlayerState()
    ) { appStats, playerState ->
        StatsUiState(
            appStats = appStats,
            playerState = playerState,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = StatsUiState()
    )

    /**
     * Backward-compatible with the original StatsViewModel's public API
     * (StateFlow<AppStats?>, null while loading), in case any other screen
     * reads this directly rather than through uiState. Prefer uiState for
     * new code — this is a thin derived view, not a second source of truth.
     */
    val stats: StateFlow<AppStats?> = uiState
        .map { if (it.isLoading) null else it.appStats }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    /**
     * Backward-compatible synchronous accessor, matching the original
     * StatsViewModel.getCurrentStats().
     */
    fun getCurrentStats(): AppStats = uiState.value.appStats

    /**
     * Resets lifetime stats. Deliberately does NOT reset player level/XP/
     * streak — those live in PlayerRepository and represent progression
     * the user earned, which "Reset All Stats" (a storage-stats concept)
     * shouldn't silently wipe. If you want a combined reset later, add an
     * explicit second button/confirmation for that rather than folding it
     * into this one.
     */
    fun clearAllStats() {
        viewModelScope.launch {
            statsRepository.clearAllStats()
        }
    }
}