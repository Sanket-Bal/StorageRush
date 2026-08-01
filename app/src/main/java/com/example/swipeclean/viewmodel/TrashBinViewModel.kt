package com.example.swipeclean.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.swipeclean.data.model.TrashItem
import com.example.swipeclean.data.repository.StatsRepository
import com.example.swipeclean.data.repository.TrashBinRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    val lastDeletionItemCount: Int = 0
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

                // Update stats
                statsRepository.updateStatsAfterDeletion(freedBytes, itemCount)

                // Remove from trash
                trashRepository.removeDeletedItems(selectedIds)

                // Update UI state with last deletion stats
                _trashBinState.value = _trashBinState.value.copy(
                    selectedForDeletion = emptySet(),
                    showDeleteConfirmation = false,
                    lastDeletionFreedBytes = freedBytes,
                    lastDeletionItemCount = itemCount
                )

                loadTrashBin()
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

                // Update stats
                statsRepository.updateStatsAfterDeletion(freedBytes, itemCount)

                // Remove from trash
                trashRepository.removeDeletedItems(selectedIds)

                _trashBinState.value = _trashBinState.value.copy(
                    selectedForDeletion = emptySet(),
                    showDeleteConfirmation = false,
                    lastDeletionFreedBytes = freedBytes,
                    lastDeletionItemCount = itemCount
                )

                loadTrashBin()
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
     * Reset last session stats after showing notification
     */
    fun resetLastSessionStats() {
        viewModelScope.launch {
            try {
                statsRepository.resetLastSessionStats()
                _trashBinState.value = _trashBinState.value.copy(
                    lastDeletionFreedBytes = 0L,
                    lastDeletionItemCount = 0
                )
            } catch (e: Exception) {
                // Silent fail - not critical
            }
        }
    }
}