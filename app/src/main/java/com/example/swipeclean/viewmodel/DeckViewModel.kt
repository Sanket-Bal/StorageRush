package com.example.swipeclean.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.swipeclean.data.model.MediaItem
import com.example.swipeclean.data.repository.MediaStoreRepository
import com.example.swipeclean.data.repository.TrashBinRepository
import com.example.swipeclean.ui.deck.DeckState
import com.example.swipeclean.ui.deck.DeckType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DeckViewModel(context: Context) : ViewModel() {

    private val mediaRepository = MediaStoreRepository(context)
    private val trashRepository = TrashBinRepository(context)

    private val _deckState = MutableStateFlow<DeckState>(DeckState())
    val deckState: StateFlow<DeckState> = _deckState.asStateFlow()

    private companion object {
        const val MAX_UNDO_DEPTH = 5
    }

    fun loadScreenshotsDeck() {
        _deckState.value = DeckState(isLoading = true, deckType = "Screenshots")
        viewModelScope.launch {
            try {
                val allScreenshots = mutableListOf<MediaItem>()
                mediaRepository.getScreenshots().collect { batch ->
                    allScreenshots.addAll(batch)
                    _deckState.value = _deckState.value.copy(
                        mediaItems = allScreenshots.toList(),
                        isLoading = false,
                        errorMessage = null
                    )
                }
            } catch (e: Exception) {
                _deckState.value = _deckState.value.copy(
                    isLoading = false,
                    errorMessage = "Failed to load screenshots: ${e.message}"
                )
            }
        }
    }

    fun loadLargeVideosDeck(thresholdMB: Int = 50) {
        _deckState.value = DeckState(isLoading = true, deckType = "Large Videos")
        viewModelScope.launch {
            try {
                val allVideos = mutableListOf<MediaItem>()
                val thresholdBytes = thresholdMB.toLong() * 1024 * 1024
                mediaRepository.getLargeVideos(thresholdBytes).collect { batch ->
                    allVideos.addAll(batch)
                    _deckState.value = _deckState.value.copy(
                        mediaItems = allVideos.toList(),
                        isLoading = false,
                        errorMessage = null
                    )
                }
            } catch (e: Exception) {
                _deckState.value = _deckState.value.copy(
                    isLoading = false,
                    errorMessage = "Failed to load large videos: ${e.message}"
                )
            }
        }
    }

    fun loadMonthlyPhotosDeck() {
        _deckState.value = DeckState(isLoading = true, deckType = "Monthly Photos")
        viewModelScope.launch {
            try {
                val allPhotos = mutableListOf<MediaItem>()
                mediaRepository.getPhotosByMonth().collect { batch ->
                    allPhotos.addAll(batch)
                    _deckState.value = _deckState.value.copy(
                        mediaItems = allPhotos.toList(),
                        isLoading = false,
                        errorMessage = null
                    )
                }
            } catch (e: Exception) {
                _deckState.value = _deckState.value.copy(
                    isLoading = false,
                    errorMessage = "Failed to load monthly photos: ${e.message}"
                )
            }
        }
    }

    fun loadCustomDeck(bucketName: String) {
        _deckState.value = DeckState(isLoading = true, deckType = bucketName)
        viewModelScope.launch {
            try {
                val allMedia = mutableListOf<MediaItem>()
                mediaRepository.getMediaByBucket(bucketName).collect { batch ->
                    allMedia.addAll(batch)
                    _deckState.value = _deckState.value.copy(
                        mediaItems = allMedia.toList(),
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