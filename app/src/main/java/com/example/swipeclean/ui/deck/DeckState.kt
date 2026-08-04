package com.example.swipeclean.ui.deck

import com.example.swipeclean.data.model.MediaItem

/**
 * Represents the UI state of a single swipe deck.
 * Immutable data class for reactive state management.
 */
data class DeckState(
    // Current media items in the deck (lazy-loaded, batched)
    val mediaItems: List<MediaItem> = emptyList(),
    
    // Index of the currently displayed card (0 = first card)
    val currentCardIndex: Int = 0,
    
    // Undo stack: last 5 swiped items (restored when user clicks Undo)
    val undoStack: List<MediaItem> = emptyList(),
    
    // Loading state
    val isLoading: Boolean = false,
    
    // Error message (null if no error)
    val errorMessage: String? = null,
    
    // User has swiped right (Keep)? Used for animation
    val lastSwipeWasRight: Boolean? = null,
    
    // Deck type (Screenshots, Large Videos, Monthly Photos)
    val deckType: String = "Unknown"
) {
    /**
     * Returns the currently displayed card, or null if no cards available
     */
    fun getCurrentCard(): MediaItem? {
        return if (currentCardIndex < mediaItems.size) {
            mediaItems[currentCardIndex]
        } else {
            null
        }
    }

    /**
     * Checks if the deck is empty (no media items)
     */
    fun isEmpty(): Boolean = mediaItems.isEmpty()

    /**
     * Checks if all cards have been swiped (reached the end)
     */
    fun isComplete(): Boolean = currentCardIndex >= mediaItems.size

    /**
     * Gets the number of cards remaining to review
     */
    fun getRemainingCount(): Int = (mediaItems.size - currentCardIndex).coerceAtLeast(0)

    /**
     * Gets the progress percentage (0-100)
     */
    fun getProgressPercentage(): Float {
        if (mediaItems.isEmpty()) return 0f
        return ((currentCardIndex.toFloat() / mediaItems.size) * 100).coerceIn(0f, 100f)
    }

    /**
     * Checks if undo is available (stack not empty)
     */
    fun canUndo(): Boolean = undoStack.isNotEmpty()
}

/**
 * Sealed class for different deck types (Phase 4.3).
 * Was a plain enum; converted to sealed class so dynamic variants
 * (a specific folder, a specific video filter) can carry their own data
 * instead of being forced into a single generic "CUSTOM" bucket.
 */
sealed class DeckType {
    object Screenshots : DeckType()
    object MonthlyPhotos : DeckType()

    /** A specific image folder, identified by its real MediaStore bucket ID (Phase 4.1). */
    data class Bucket(val bucketId: Long, val bucketName: String) : DeckType()

    /** A specific video filter section (Phase 4.2). */
    data class VideoFilter(val filter: VideoFilterType) : DeckType()
}

enum class VideoFilterType {
    LARGE_VIDEOS,
    SHORT_VIDEOS,
    ALL_VIDEOS
}

/**
 * Human-readable name for a deck type, used as DeckState.deckType / the top bar title.
 */
fun DeckType.displayName(): String = when (this) {
    is DeckType.Screenshots -> "Screenshots"
    is DeckType.MonthlyPhotos -> "Monthly Photos"
    is DeckType.Bucket -> bucketName
    is DeckType.VideoFilter -> when (filter) {
        VideoFilterType.LARGE_VIDEOS -> "Large Videos"
        VideoFilterType.SHORT_VIDEOS -> "Short Clips"
        VideoFilterType.ALL_VIDEOS -> "All Videos"
    }
}