package com.storagerush.app.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.storagerush.app.data.gamification.Achievement
import com.storagerush.app.data.gamification.AchievementDefinitions
import com.storagerush.app.data.model.TrashItem
import com.storagerush.app.data.repository.AppStats
import com.storagerush.app.data.repository.CloudSyncRepository
import com.storagerush.app.data.repository.PlayerRepository
import com.storagerush.app.data.repository.PlayerState
import com.storagerush.app.data.repository.StatsRepository
import com.storagerush.app.data.repository.TrashBinRepository
import com.storagerush.app.data.repository.UserPreferencesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class TrashBinState(
    val trashItems: List<TrashItem> = emptyList(),
    val totalCount: Int = 0,
    val totalSizeBytes: Long = 0L,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val selectedForDeletion: Set<Long> = emptySet(),
    val showDeleteConfirmation: Boolean = false,
    val lastDeletionFreedBytes: Long = 0L,
    val lastDeletionItemCount: Int = 0,
    // Gamification (Phase 1): XP earned by the most recent deletion, and
    // whether it leveled the player up. Reset alongside the existing
    // lastDeletion* fields via resetLastSessionStats() once shown.
    val lastXpEarned: Long = 0L,
    val leveledUp: Boolean = false,
    // Gamification (Phase 2): badges that crossed their unlock threshold
    // as a direct result of the most recent deletion. Computed by
    // diffing achievement-unlock status before vs. after the deletion —
    // achievements themselves are never separately persisted (see
    // Achievement.kt), so this snapshot is the only record of "just
    // unlocked" and only exists until the next reset.
    val newlyUnlockedAchievements: List<Achievement> = emptyList()
) {
    fun getTotalSizeReadable(): String {
        return when {
            totalSizeBytes < 1024 -> "$totalSizeBytes B"
            totalSizeBytes < 1024 * 1024 -> "${totalSizeBytes / 1024} KB"
            totalSizeBytes < 1024 * 1024 * 1024 -> String.format("%.1f MB", totalSizeBytes / (1024.0 * 1024.0))
            else -> String.format("%.1f GB", totalSizeBytes / (1024.0 * 1024.0 * 1024.0))
        }
    }

    fun getSelectedSizeReadable(): String {
        val selectedSize = trashItems
            .filter { it.mediaId in selectedForDeletion }
            .sumOf { it.sizeBytes }

        return when {
            selectedSize < 1024 -> "$selectedSize B"
            selectedSize < 1024 * 1024 -> "${selectedSize / 1024} KB"
            selectedSize < 1024 * 1024 * 1024 -> String.format("%.1f MB", selectedSize / (1024.0 * 1024.0))
            else -> String.format("%.1f GB", selectedSize / (1024.0 * 1024.0 * 1024.0))
        }
    }

    fun getSelectedCount(): Int {
        return selectedForDeletion.size
    }

    fun getLastDeletionReadable(): String {
        return when {
            lastDeletionFreedBytes < 1024 -> "$lastDeletionFreedBytes B"
            lastDeletionFreedBytes < 1024 * 1024 -> "${lastDeletionFreedBytes / 1024} KB"
            lastDeletionFreedBytes < 1024 * 1024 * 1024 -> String.format("%.1f MB", lastDeletionFreedBytes / (1024.0 * 1024.0))
            else -> String.format("%.1f GB", lastDeletionFreedBytes / (1024.0 * 1024.0 * 1024.0))
        }
    }
}

class TrashBinViewModel(context: Context) : ViewModel() {

    private val trashRepository = TrashBinRepository(context)
    private val statsRepository = StatsRepository(context)
    private val playerRepository = PlayerRepository(context)

    // Phase A: cloud sync
    private val cloudSyncRepository = CloudSyncRepository(context)
    private val userPreferencesRepository = UserPreferencesRepository(context)

    private val _trashBinState = MutableStateFlow<TrashBinState>(TrashBinState(isLoading = true))
    val trashBinState: StateFlow<TrashBinState> = _trashBinState.asStateFlow()

    init {
        loadTrashBin()
    }

    /**
     * Load all trash items from repository
     */
    fun loadTrashBin() {
        _trashBinState.value = _trashBinState.value.copy(isLoading = true)

        viewModelScope.launch {
            try {
                trashRepository.getAllTrashItems().collect { items ->
                    val totalSize = items.sumOf { it.sizeBytes }
                    _trashBinState.value = _trashBinState.value.copy(
                        trashItems = items,
                        totalCount = items.size,
                        totalSizeBytes = totalSize,
                        isLoading = false,
                        errorMessage = null
                    )
                }
            } catch (e: Exception) {
                _trashBinState.value = _trashBinState.value.copy(
                    isLoading = false,
                    errorMessage = "Failed to load trash: ${e.message}"
                )
            }
        }
    }

    /**
     * Restore a single item from trash
     */
    fun restoreItem(trashItem: TrashItem) {
        viewModelScope.launch {
            try {
                trashRepository.removeFromTrash(trashItem.mediaId)
                loadTrashBin()
            } catch (e: Exception) {
                _trashBinState.value = _trashBinState.value.copy(
                    errorMessage = "Failed to restore item: ${e.message}"
                )
            }
        }
    }

    /**
     * Toggle item selection for bulk delete
     */
    fun toggleItemSelection(mediaId: Long) {
        val currentSelected = _trashBinState.value.selectedForDeletion.toMutableSet()
        if (currentSelected.contains(mediaId)) {
            currentSelected.remove(mediaId)
        } else {
            currentSelected.add(mediaId)
        }
        _trashBinState.value = _trashBinState.value.copy(
            selectedForDeletion = currentSelected
        )
    }

    /**
     * Select all items
     */
    fun selectAll() {
        val allIds = _trashBinState.value.trashItems.map { it.mediaId }.toSet()
        _trashBinState.value = _trashBinState.value.copy(
            selectedForDeletion = allIds
        )
    }

    /**
     * Deselect all items
     */
    fun deselectAll() {
        _trashBinState.value = _trashBinState.value.copy(
            selectedForDeletion = emptySet()
        )
    }

    /**
     * Show delete confirmation dialog
     */
    fun showDeleteConfirmation() {
        _trashBinState.value = _trashBinState.value.copy(
            showDeleteConfirmation = true
        )
    }

    /**
     * Hide delete confirmation dialog
     */
    fun hideDeleteConfirmation() {
        _trashBinState.value = _trashBinState.value.copy(
            showDeleteConfirmation = false
        )
    }

    /**
 * Get URIs for deletion (returns single URI - deprecated, use getAllUrisForDeletion)
 */
fun getUrisForDeletion(): android.net.Uri? {
    val selectedIds = _trashBinState.value.selectedForDeletion.toList()
    if (selectedIds.isEmpty()) return null
    
    val uris = trashRepository.getUrisForDeletion(selectedIds, _trashBinState.value.trashItems)
    return if (uris.isNotEmpty()) uris[0] else null
}

/**
 * Get all URIs for deletion (MAIN METHOD - used by MainActivity)
 * Passes trashItems with mimeType so repository can create proper URIs
 */
fun getAllUrisForDeletion(): List<android.net.Uri> {
    val selectedIds = _trashBinState.value.selectedForDeletion.toList()
    return trashRepository.getUrisForDeletion(selectedIds, _trashBinState.value.trashItems)
}

    /**
     * Called AFTER successful MediaStore deletion
     * Updates stats and removes from trash
     */
    fun onDeletionSuccess() {
        val selectedIds = _trashBinState.value.selectedForDeletion.toList()
        val currentState = _trashBinState.value
        
        viewModelScope.launch {
            try {
                // Calculate freed space and items count
                val freedBytes = currentState.trashItems
                    .filter { it.mediaId in selectedIds }
                    .sumOf { it.sizeBytes }
                val itemCount = selectedIds.size

                // Gamification (Phase 2): snapshot achievement-unlock
                // status BEFORE this deletion's stats/XP are applied, so
                // we can diff against the after-state below and know
                // exactly which badges this specific deletion unlocked.
                val statsBefore = statsRepository.getStats().first()
                val playerBefore = playerRepository.getPlayerState().first()

                // Update stats
                statsRepository.updateStatsAfterDeletion(freedBytes, itemCount)

                // Gamification (Phase 1): award XP + roll streak for this
                // cleanup. Called right alongside the stats update above so
                // XP and lifetime storage-freed stats always move together.
                val reward = playerRepository.recordCleanup(freedBytes)

                val statsAfter = statsRepository.getStats().first()

                // Gamification (Phase 2): any badge that was locked before
                // this deletion and unlocked after it is "newly unlocked"
                // for celebration purposes — a badge unlocked in some
                // earlier session doesn't re-fire here.
                val newlyUnlocked = AchievementDefinitions.ALL.filter { achievement ->
                    !achievement.isUnlocked(playerBefore, statsBefore) &&
                        achievement.isUnlocked(reward.newState, statsAfter)
                }

                // Remove from trash
                trashRepository.removeDeletedItems(selectedIds)

                // Update UI state with last deletion stats
                _trashBinState.value = _trashBinState.value.copy(
                    selectedForDeletion = emptySet(),
                    showDeleteConfirmation = false,
                    lastDeletionFreedBytes = freedBytes,
                    lastDeletionItemCount = itemCount,
                    lastXpEarned = reward.xpEarned,
                    leveledUp = reward.levelsGained > 0,
                    newlyUnlockedAchievements = newlyUnlocked
                )

                loadTrashBin()

                // Phase A: push updated progress to Supabase. Fire-and-forget
                // relative to the local flow above — local state has already
                // been updated and the UI already reflects it by this point,
                // so a slow or failed cloud sync never blocks or breaks the
                // on-device experience.
                syncProgressToCloud(reward.newState, statsAfter)
            } catch (e: Exception) {
                _trashBinState.value = _trashBinState.value.copy(
                    errorMessage = "Failed to update after deletion: ${e.message}"
                )
            }
        }
    }

    /**
     * Fallback: Delete items without OS dialog (if user denies permission)
     * Still updates stats and removes from trash
     */
    fun deleteSelectedItemsFallback() {
        val selectedIds = _trashBinState.value.selectedForDeletion.toList()
        
        viewModelScope.launch {
            try {
                // Calculate freed space
                val freedBytes = _trashBinState.value.trashItems
                    .filter { it.mediaId in selectedIds }
                    .sumOf { it.sizeBytes }
                val itemCount = selectedIds.size

                // Gamification (Phase 2): same before/after achievement
                // snapshot as the primary deletion path above.
                val statsBefore = statsRepository.getStats().first()
                val playerBefore = playerRepository.getPlayerState().first()

                // Update stats
                statsRepository.updateStatsAfterDeletion(freedBytes, itemCount)

                // Gamification (Phase 1): same XP/streak award as the
                // primary deletion path above — this is still a real,
                // successful deletion, just without the OS confirmation
                // dialog, so it should reward the same way.
                val reward = playerRepository.recordCleanup(freedBytes)

                val statsAfter = statsRepository.getStats().first()

                val newlyUnlocked = AchievementDefinitions.ALL.filter { achievement ->
                    !achievement.isUnlocked(playerBefore, statsBefore) &&
                        achievement.isUnlocked(reward.newState, statsAfter)
                }

                // Remove from trash
                trashRepository.removeDeletedItems(selectedIds)

                _trashBinState.value = _trashBinState.value.copy(
                    selectedForDeletion = emptySet(),
                    showDeleteConfirmation = false,
                    lastDeletionFreedBytes = freedBytes,
                    lastDeletionItemCount = itemCount,
                    lastXpEarned = reward.xpEarned,
                    leveledUp = reward.levelsGained > 0,
                    newlyUnlockedAchievements = newlyUnlocked
                )

                loadTrashBin()

                // Phase A: same cloud sync as the primary deletion path above.
                syncProgressToCloud(reward.newState, statsAfter)
            } catch (e: Exception) {
                _trashBinState.value = _trashBinState.value.copy(
                    errorMessage = "Failed to delete items: ${e.message}"
                )
            }
        }
    }

    /**
     * Clear all trash
     */
    fun clearAllTrash() {
        viewModelScope.launch {
            try {
                trashRepository.clearAllTrash()
                _trashBinState.value = _trashBinState.value.copy(
                    selectedForDeletion = emptySet(),
                    showDeleteConfirmation = false
                )
                loadTrashBin()
            } catch (e: Exception) {
                _trashBinState.value = _trashBinState.value.copy(
                    errorMessage = "Failed to clear trash: ${e.message}"
                )
            }
        }
    }

    /**
     * Get last deletion stats for toast notification
     */
    fun getLastDeletionStats(): Pair<Long, Int> {
        return Pair(
            _trashBinState.value.lastDeletionFreedBytes,
            _trashBinState.value.lastDeletionItemCount
        )
    }

    /**
     * Whether the most recent deletion (whose stats are still sitting in
     * lastDeletionFreedBytes/lastDeletionItemCount, not yet reset) caused a
     * level-up. DeckScreen checks this to decide between showing the
     * regular deletion snackbar or the full-screen LevelUpCelebrationOverlay.
     */
    fun wasLastDeletionLevelUp(): Boolean {
        return _trashBinState.value.leveledUp
    }

    /**
     * Achievements newly unlocked by the most recent deletion, not yet
     * reset. DeckScreen reads this to enqueue toast banners — one call per
     * deletion event, queued and shown sequentially if more than one badge
     * unlocked at once.
     */
    fun getNewlyUnlockedAchievements(): List<Achievement> {
        return _trashBinState.value.newlyUnlockedAchievements
    }

    /**
     * Reset last session stats after showing notification
     */
    fun resetLastSessionStats() {
        viewModelScope.launch {
            try {
                statsRepository.resetLastSessionStats()
                _trashBinState.value = _trashBinState.value.copy(
                    lastDeletionFreedBytes = 0L,
                    lastDeletionItemCount = 0,
                    lastXpEarned = 0L,
                    leveledUp = false,
                    newlyUnlockedAchievements = emptyList()
                )
            } catch (e: Exception) {
                // Silent fail - not critical
            }
        }
    }

    /**
     * Phase A: pushes the latest player progress + stats to Supabase.
     *
     * Only runs if the user has completed the one-time nickname/cloud setup
     * flow (see UserPreferencesRepository.hasCompletedCloudSetup()) — until
     * then there's no player record on the server to update, and we don't
     * want to force cloud setup just to use the app locally.
     *
     * `player` is PlayerRepository's PlayerState, `stats` is
     * StatsRepository's AppStats — both passed straight through from the
     * values already computed above (reward.newState / statsAfter).
     */
    private fun syncProgressToCloud(
        player: PlayerState,
        stats: AppStats
    ) {
        viewModelScope.launch {
            try {
                if (!userPreferencesRepository.hasCompletedCloudSetup()) return@launch

                val anonymousId = cloudSyncRepository.ensureSignedIn()
                cloudSyncRepository.syncPlayerProgress(
                    anonymousId = anonymousId,
                    level = player.level,
                    currentXp = player.currentXp,
                    totalCareerXp = player.totalCareerXp,
                    weeklyStreak = player.weeklyStreak,
                    bestStreak = player.bestStreak,
                    storageFreedBytes = stats.totalStorageFreedBytes,
                    totalMediaCleaned = stats.totalMediaCleaned,
                    largestSingleCleanupBytes = stats.largestSingleCleanupBytes,
                    lastCleanupTimestamp = player.lastCleanupTimestamp
                )
            } catch (e: Exception) {
                // Cloud sync failure should never surface as a user-facing
                // error or break the local cleanup flow — local DataStore
                // is always the source of truth for on-device UI. Just log
                // it so it's visible during development.
                Log.e("TrashBinViewModel", "Cloud sync failed", e)
            }
        }
    }
}