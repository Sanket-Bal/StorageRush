package com.example.swipeclean.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.swipeclean.data.model.MediaItem
import com.example.swipeclean.data.repository.MediaStoreRepository
import com.example.swipeclean.data.repository.TrashBinRepository
import com.example.swipeclean.data.repository.UserPreferencesRepository
import com.example.swipeclean.ui.deck.DeckState
import com.example.swipeclean.ui.deck.DeckType
import com.example.swipeclean.ui.deck.VideoFilterType
import com.example.swipeclean.ui.deck.displayName
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DeckViewModel(context: Context) : ViewModel() {

    private val mediaRepository = MediaStoreRepository(context)
    private val trashRepository = TrashBinRepository(context)
    private val userPreferencesRepository = UserPreferencesRepository(context)

    private val _deckState = MutableStateFlow<DeckState>(DeckState())
    val deckState: StateFlow<DeckState> = _deckState.asStateFlow()

    private companion object {
        const val MAX_UNDO_DEPTH = 5
    }

    /**
     * Resolves which deck to show on app launch (Phase 4.6). Called once
     * from MainActivity after permission is granted.
     *
     * - Saved selection exists and, if it's a Bucket, still exists on the
     *   device -> use it as-is.
     * - Saved Bucket no longer exists (folder deleted/emptied since last
     *   launch) -> clear the stale preference, fall back to the resolved
     *   default bucket instead of silently reopening a dead folder.
     * - Nothing saved at all (genuine first launch) -> resolve the real
     *   default bucket: Camera folder -> any DCIM folder -> most-populated
     *   bucket -> Screenshots as a last resort if the device has no image
     *   buckets whatsoever, which DeckScreen's existing EmptyDeckState
     *   already handles gracefully rather than crashing or showing nothing.
     */
    suspend fun resolveInitialDeckType(): DeckType {
        val saved = userPreferencesRepository.getLastDeckType()

        if (saved is DeckType.Bucket) {
            val buckets = mediaRepository.getAllImageBuckets()
            val stillExists = buckets.any { it.bucketId == saved.bucketId }
            if (stillExists) return saved

            userPreferencesRepository.clearLastDeckSelection()
            return mediaRepository.resolveDefaultBucket(buckets)
                ?.let { DeckType.Bucket(bucketId = it.bucketId, bucketName = it.bucketName) }
                ?: DeckType.Screenshots
        }

        if (saved != null) return saved

        // Genuine first launch: nothing saved yet.
        val buckets = mediaRepository.getAllImageBuckets()
        return mediaRepository.resolveDefaultBucket(buckets)
            ?.let { DeckType.Bucket(bucketId = it.bucketId, bucketName = it.bucketName) }
            ?: DeckType.Screenshots
    }

    /**
     * Whether the mandatory first-launch tutorial has been completed.
     * Read once by MainActivity to decide whether to show the onboarding gate.
     */
    suspend fun hasSeenTutorial(): Boolean = userPreferencesRepository.hasSeenTutorial()

    /**
     * Marks the tutorial as seen. Called when the mandatory tutorial's
     * final "Got it, let's go!" button is tapped.
     */
    fun markTutorialSeen() {
        viewModelScope.launch {
            userPreferencesRepository.markTutorialSeen()
        }
    }

    /**
     * Loads a deck for the given [DeckType] (Phase 4.3).
     * Replaces the previous four separate load*Deck() functions — the
     * `when` below is exhaustive over the sealed class, so adding a new
     * DeckType variant later will fail to compile here until it's handled,
     * rather than silently doing nothing (as the old CUSTOM enum case did).
     */
    fun loadDeck(deckType: DeckType) {
        _deckState.value = DeckState(isLoading = true, deckType = deckType.displayName())

        // Persist as the last-viewed section (Phase 4.4). Fire-and-forget on
        // a separate launch from the data load below, so a slow/failed save
        // never blocks or breaks the actual deck loading.
        viewModelScope.launch {
            try {
                userPreferencesRepository.saveLastDeckSelection(deckType)
            } catch (e: Exception) {
                // Non-critical: worst case, next launch falls back to default.
            }
        }

        viewModelScope.launch {
            try {
                val allItems = mutableListOf<MediaItem>()
                val itemsFlow = when (deckType) {
                    is DeckType.Screenshots -> mediaRepository.getScreenshots()
                    is DeckType.MonthlyPhotos -> mediaRepository.getPhotosByMonth()
                    is DeckType.Bucket -> mediaRepository.getMediaByBucketId(deckType.bucketId)
                    is DeckType.VideoFilter -> when (deckType.filter) {
                        VideoFilterType.LARGE_VIDEOS -> mediaRepository.getLargeVideos()
                        VideoFilterType.SHORT_VIDEOS -> mediaRepository.getShortVideos()
                        VideoFilterType.ALL_VIDEOS -> mediaRepository.getAllVideosSorted()
                    }
                }
                itemsFlow.collect { batch ->
                    allItems.addAll(batch)
                    _deckState.value = _deckState.value.copy(
                        mediaItems = allItems.toList(),
                        isLoading = false,
                        errorMessage = null
                    )
                }
            } catch (e: Exception) {
                _deckState.value = _deckState.value.copy(
                    isLoading = false,
                    errorMessage = "Failed to load deck: ${e.message}"
                )
            }
        }
    }

    /**
     * User swiped/clicked right (Keep action)
     * Item stays on device
     */
    fun swipeRight() {
        val currentState = _deckState.value
        val currentCard = currentState.getCurrentCard() ?: return

        _deckState.value = currentState.copy(
            currentCardIndex = currentState.currentCardIndex + 1,
            lastSwipeWasRight = true,
            undoStack = buildUndoStack(currentState.undoStack + currentCard)
        )
    }

    /**
     * User swiped/clicked left (Trash action)
     * Item added to trash and advances to next card
     */
    fun swipeLeft() {
        val currentState = _deckState.value
        val currentCard = currentState.getCurrentCard() ?: return

        // Add to trash bin
        viewModelScope.launch {
            try {
                trashRepository.addToTrash(currentCard)
            } catch (e: Exception) {
                _deckState.value = _deckState.value.copy(
                    errorMessage = "Failed to add to trash: ${e.message}"
                )
            }
        }

        // Move to next card and add to undo stack
        _deckState.value = currentState.copy(
            currentCardIndex = currentState.currentCardIndex + 1,
            lastSwipeWasRight = false,
            undoStack = buildUndoStack(currentState.undoStack + currentCard)
        )
    }

    /**
     * User clicked trash button (same as swipe left)
     */
    fun clickTrash() {
        swipeLeft()
    }

    /**
     * User clicked keep button (same as swipe right)
     */
    fun clickKeep() {
        swipeRight()
    }

    /**
     * User clicked undo
     * Restores the last swiped card
     */
    fun undo() {
        val currentState = _deckState.value
        if (!currentState.canUndo()) return

        val restoredItem = currentState.undoStack.last()
        val newUndoStack = currentState.undoStack.dropLast(1)

        // If last action was trash (swipeLeft), restore from trash bin
        if (currentState.lastSwipeWasRight == false) {
            viewModelScope.launch {
                try {
                    trashRepository.removeFromTrash(restoredItem.id)
                } catch (e: Exception) {
                    _deckState.value = _deckState.value.copy(
                        errorMessage = "Failed to restore from trash: ${e.message}"
                    )
                }
            }
        }

        _deckState.value = currentState.copy(
            currentCardIndex = (currentState.currentCardIndex - 1).coerceAtLeast(0),
            undoStack = newUndoStack,
            lastSwipeWasRight = null
        )
    }

    /**
     * Resets the deck to the beginning
     */
    fun resetDeck() {
        _deckState.value = _deckState.value.copy(
            currentCardIndex = 0,
            undoStack = emptyList(),
            lastSwipeWasRight = null
        )
    }

    /**
     * Clears the entire deck
     */
    fun clearDeck() {
        _deckState.value = DeckState()
    }

    /**
     * Ensures undo stack never exceeds MAX_UNDO_DEPTH
     */
    private fun buildUndoStack(stack: List<MediaItem>): List<MediaItem> {
        return if (stack.size > MAX_UNDO_DEPTH) {
            stack.takeLast(MAX_UNDO_DEPTH)
        } else {
            stack
        }
    }
}