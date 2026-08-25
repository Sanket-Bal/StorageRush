package com.storagerush.app.ui.deck

import com.storagerush.app.data.model.MediaItem

/**
 * One entry in the undo stack: a swiped item, paired with whether THAT
 * specific swipe was a trash action. Previously the undo stack was a plain
 * List<MediaItem> and a single shared `lastSwipeWasRight` flag on DeckState
 * was used to decide whether to restore-from-trash on undo. That flag only
 * ever reflects the MOST RECENT swipe, so it broke on the second and any
 * later undo in a row — e.g. trash 3 items, undo 3 times in a row: only the
 * first undo correctly restored from the trash bin, the rest silently
 * skipped it (item reappeared in the deck but was never actually removed
 * from the trash bin). Tracking wasTrashed per-entry fixes this: each undo
 * always knows exactly what its own item's action was, regardless of order
 * or how many undos happen consecutively.
 */
data class UndoEntry(
    val item: MediaItem,
    val wasTrashed: Boolean
)

/**
 * Represents the UI state of a single swipe deck.
 * Immutable data class for reactive state management.
 */
data class DeckState(
    // Current media items in the deck (lazy-loaded, batched)
    val mediaItems: List<MediaItem> = emptyList(),
    
    // Index of the currently displayed card (0 = first card)
    val currentCardIndex: Int = 0,
    
    // Undo stack: swiped items this session, each paired with whether it
    // was a trash action, restored in reverse order when Undo is tapped.
    val undoStack: List<UndoEntry> = emptyList(),
    
    // Loading state
    val isLoading: Boolean = false,
    
    // Error message (null if no error)
    val errorMessage: String? = null,
    
    // Most recent swipe direction, kept for potential animation use.
    // NOT used to decide undo's trash-restore behavior anymore — see
    // UndoEntry.wasTrashed above for why a single shared flag was wrong.
    val lastSwipeWasRight: Boolean? = null,

    // Ids of items THIS session's swipeLeft() has sent to the trash bin.
    // Swiping left never removes the card from mediaItems — it only
    // advances currentCardIndex past it (see DeckViewModel.swipeLeft()) —
    // so a card sitting behind the pointer with a "still trashed?" status
    // is otherwise indistinguishable from one the user simply kept
    // (swiped right). DeckViewModel.syncWithTrash() reads this set to know
    // which behind-the-pointer cards are even eligible to reappear if
    // they're later restored via the Trash Bin screen (as opposed to
    // Deck's own inline Undo button, which restores immediately and
    // removes the id from this set — see DeckViewModel.undo()).
    val sessionTrashedIds: Set<Long> = emptySet(),

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