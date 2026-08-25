package com.storagerush.app.viewmodel

import android.content.Context
import android.provider.MediaStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.storagerush.app.data.model.MediaItem
import com.storagerush.app.data.repository.MediaStoreRepository
import com.storagerush.app.data.repository.TrashBinRepository
import com.storagerush.app.data.repository.UserPreferencesRepository
import com.storagerush.app.ui.deck.DeckState
import com.storagerush.app.ui.deck.DeckType
import com.storagerush.app.ui.deck.UndoEntry
import com.storagerush.app.ui.deck.VideoFilterType
import com.storagerush.app.ui.deck.displayName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DeckViewModel(context: Context) : ViewModel() {

    // Stored so syncWithTrash() can query MediaStore directly to check
    // whether a "no longer in trash" item still exists on the device
    // (restored) or was permanently deleted (gone for good).
    private val appContext = context

    private val mediaRepository = MediaStoreRepository(context)
    private val trashRepository = TrashBinRepository(context)
    private val userPreferencesRepository = UserPreferencesRepository(context)

    private val _deckState = MutableStateFlow<DeckState>(DeckState())
    val deckState: StateFlow<DeckState> = _deckState.asStateFlow()

    /**
     * Tracks which DeckType the current _deckState was actually built for.
     * DeckScreen's LaunchedEffect(deckType) re-invokes loadDeck() every time
     * the screen re-enters composition (e.g. navigating back from Trash Bin),
     * even when it's the exact same deck as before. Without this guard,
     * every such return visit would fully reset DeckState and re-query
     * MediaStore from scratch.
     */
    private var loadedDeckType: DeckType? = null

    /**
     * The full, unfiltered result of the last MediaStore query for the
     * current deck, in stable natural order. DeckState.mediaItems is
     * always a filtered *view* of this list (trashed items excluded).
     */
    private var rawItems: List<MediaItem> = emptyList()

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

        val buckets = mediaRepository.getAllImageBuckets()
        return mediaRepository.resolveDefaultBucket(buckets)
            ?.let { DeckType.Bucket(bucketId = it.bucketId, bucketName = it.bucketName) }
            ?: DeckType.Screenshots
    }

    suspend fun hasSeenTutorial(): Boolean = userPreferencesRepository.hasSeenTutorial()

    fun markTutorialSeen() {
        viewModelScope.launch {
            userPreferencesRepository.markTutorialSeen()
        }
    }

    /**
     * Loads a deck for the given [DeckType] (Phase 4.3).
     *
     * If this is the same DeckType already loaded, this does NOT reset
     * progress — it only re-syncs against the trash bin. Only does the
     * full reset + fresh MediaStore query when actually switching to a
     * new DeckType.
     */
    fun loadDeck(deckType: DeckType) {
        if (loadedDeckType == deckType && _deckState.value.mediaItems.isNotEmpty()) {
            syncWithTrash()
            return
        }

        loadedDeckType = deckType
        rawItems = emptyList()
        _deckState.value = DeckState(isLoading = true, deckType = deckType.displayName())

        viewModelScope.launch {
            try {
                userPreferencesRepository.saveLastDeckSelection(deckType)
            } catch (e: Exception) {
                // Non-critical: worst case, next launch falls back to default.
            }
        }

        viewModelScope.launch {
            try {
                val trashedIds = try {
                    trashRepository.getAllTrashItems().first().map { it.mediaId }.toSet()
                } catch (e: Exception) {
                    emptySet()
                }

                val allRawItems = mutableListOf<MediaItem>()
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
                    allRawItems.addAll(batch)
                    rawItems = allRawItems.toList()
                    _deckState.value = _deckState.value.copy(
                        mediaItems = rawItems.filterNot { it.id in trashedIds },
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
     * Re-syncs the already-loaded deck against the current trash bin
     * contents, without resetting the undo stack. Handles two cases:
     *
     * 1. A visible item got trashed elsewhere -> hidden from the deck.
     * 2. A hidden (trashed) item is no longer in the trash list. This is
     *    ambiguous by itself — TrashBinRepository removes the id both on
     *    restore AND on permanent delete — so idsStillOnDevice() checks
     *    directly against MediaStore (via the SAME MediaStore.Files
     *    collection the rest of the app already queries through, matching
     *    MediaStoreRepository's approach) to confirm which happened. Only
     *    genuinely-restored items are reinserted, at their natural
     *    position from the original raw query order.
     */
    private fun syncWithTrash() {
        viewModelScope.launch {
            try {
                if (rawItems.isEmpty()) return@launch

                val trashedIds = trashRepository.getAllTrashItems().first().map { it.mediaId }.toSet()
                val current = _deckState.value
                val currentVisibleIds = current.mediaItems.map { it.id }.toSet()

                val newlyTrashedIds = currentVisibleIds.intersect(trashedIds)

                // Cards not yet reached in this pass — never touched by the
                // restore logic below, they'll be handled naturally once
                // the user actually swipes them.
                val pendingAheadIds = current.mediaItems
                    .drop(current.currentCardIndex)
                    .map { it.id }
                    .toSet()

                // Restore candidates come from TWO places:
                //  (a) ids missing from mediaItems entirely — items that
                //      were already in the trash bin before this deck was
                //      loaded, so loadDeck() filtered them out up front.
                //  (b) current.sessionTrashedIds — items THIS session's own
                //      swipeLeft() sent to trash. These were NEVER removed
                //      from mediaItems (swipeLeft only advances
                //      currentCardIndex past them), so (a)'s
                //      "!in currentVisibleIds" check can never see them —
                //      that gap was the actual bug: restoring a
                //      just-swiped card from the Trash Bin screen (as
                //      opposed to Deck's own inline Undo button) silently
                //      did nothing, because this function never considered
                //      it a candidate in the first place.
                // Either source only counts if the id is no longer
                // actually in the trash bin, and isn't already an
                // upcoming/pending card.
                val candidateRestoredIds = (
                    rawItems.map { it.id }.filterNot { it in currentVisibleIds } +
                        current.sessionTrashedIds
                    )
                    .filter { it !in trashedIds && it !in pendingAheadIds }
                    .toSet()

                val actuallyRestoredIds = if (candidateRestoredIds.isEmpty()) {
                    emptySet()
                } else {
                    withContext(Dispatchers.IO) { idsStillOnDevice(candidateRestoredIds) }
                }

                if (actuallyRestoredIds.isEmpty() && newlyTrashedIds.isEmpty()) return@launch

                val newVisibleItems = rawItems.filter { item ->
                    (item.id in currentVisibleIds || item.id in actuallyRestoredIds) &&
                        item.id !in newlyTrashedIds
                }

                val anchor = current.getCurrentCard()
                val newIndex = when {
                    // Restored items must take priority over anchor-preservation.
                    // A restored item's raw position is always <= the anchor's
                    // (you can only restore something you already swiped past),
                    // so re-finding the anchor's position after re-inserting
                    // restored cards would always land back AFTER them —
                    // silently re-skipping cards the user just restored. Jumping
                    // to the earliest restored card's position instead makes it
                    // (and any anchor after it) show up again as intended.
                    actuallyRestoredIds.isNotEmpty() -> {
                        val earliestRestored = rawItems.firstOrNull { it.id in actuallyRestoredIds }
                        earliestRestored
                            ?.let { restored -> newVisibleItems.indexOfFirst { it.id == restored.id } }
                            ?.takeIf { it >= 0 } ?: 0
                    }
                    anchor != null -> {
                        val idx = newVisibleItems.indexOfFirst { it.id == anchor.id }
                        if (idx >= 0) idx else current.currentCardIndex.coerceAtMost(newVisibleItems.size)
                    }
                    else -> current.currentCardIndex.coerceAtMost(newVisibleItems.size)
                }

                _deckState.value = current.copy(
                    mediaItems = newVisibleItems,
                    currentCardIndex = newIndex.coerceIn(0, newVisibleItems.size),
                    sessionTrashedIds = current.sessionTrashedIds - actuallyRestoredIds
                )
            } catch (e: Exception) {
                // Non-critical: worst case, the deck is briefly out of sync
                // with the trash bin until the next sync.
            }
        }
    }

    /**
     * Checks which of the given media IDs still actually exist on the
     * device. Queries MediaStore.Files (the SAME collection
     * MediaStoreRepository uses for every deck-loading query in this app)
     * rather than the separate Images.Media/Video.Media collections —
     * querying a different collection than the rest of the app was the
     * bug in the previous version of this check, which caused restored
     * items to never be detected as still existing.
     */
    private fun idsStillOnDevice(ids: Set<Long>): Set<Long> {
        if (ids.isEmpty()) return emptySet()
        val existing = mutableSetOf<Long>()
        val placeholders = ids.joinToString(",") { "?" }
        val selection = "${MediaStore.Files.FileColumns._ID} IN ($placeholders)"
        val args = ids.map { it.toString() }.toTypedArray()

        try {
            appContext.contentResolver.query(
                MediaStore.Files.getContentUri("external"),
                arrayOf(MediaStore.Files.FileColumns._ID),
                selection,
                args,
                null
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                while (cursor.moveToNext()) {
                    existing.add(cursor.getLong(idCol))
                }
            }
        } catch (e: Exception) {
            // Err on the side of NOT resurrecting an item we couldn't confirm still exists.
        }
        return existing
    }

    fun swipeRight() {
        val currentState = _deckState.value
        val currentCard = currentState.getCurrentCard() ?: return

        _deckState.value = currentState.copy(
            currentCardIndex = currentState.currentCardIndex + 1,
            lastSwipeWasRight = true,
            undoStack = currentState.undoStack + UndoEntry(item = currentCard, wasTrashed = false)
        )
    }

    fun swipeLeft() {
        val currentState = _deckState.value
        val currentCard = currentState.getCurrentCard() ?: return

        viewModelScope.launch {
            try {
                trashRepository.addToTrash(currentCard)
            } catch (e: Exception) {
                _deckState.value = _deckState.value.copy(
                    errorMessage = "Failed to add to trash: ${e.message}"
                )
            }
        }

        _deckState.value = currentState.copy(
            currentCardIndex = currentState.currentCardIndex + 1,
            lastSwipeWasRight = false,
            undoStack = currentState.undoStack + UndoEntry(item = currentCard, wasTrashed = true),
            sessionTrashedIds = currentState.sessionTrashedIds + currentCard.id
        )
    }

    fun clickTrash() {
        swipeLeft()
    }

    fun clickKeep() {
        swipeRight()
    }

    /**
     * User clicked undo. Restores the last swiped card.
     *
     * Reads wasTrashed from the popped UndoEntry itself — NOT from the
     * shared lastSwipeWasRight flag. The flag only ever reflects the most
     * recent swipe, so using it here broke every undo after the first one
     * in a row (a trashed item's restoreFromTrash() call would silently
     * get skipped once lastSwipeWasRight had already been reset to null
     * by the previous undo). Each entry now carries its own correct
     * action, so this works no matter how many undos happen consecutively.
     */
    fun undo() {
        val currentState = _deckState.value
        if (!currentState.canUndo()) return

        val entry = currentState.undoStack.last()
        val newUndoStack = currentState.undoStack.dropLast(1)

        if (entry.wasTrashed) {
            viewModelScope.launch {
                try {
                    trashRepository.removeFromTrash(entry.item.id)
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
            lastSwipeWasRight = null,
            sessionTrashedIds = if (entry.wasTrashed) {
                currentState.sessionTrashedIds - entry.item.id
            } else {
                currentState.sessionTrashedIds
            }
        )
    }

    /**
     * Clears the deck's own undo history without touching currentCardIndex
     * or mediaItems. Called by DeckScreen when it leaves composition, so
     * the undo button never persists once the user has moved on from this
     * particular Deck screen session.
     */
    fun clearUndoStack() {
        _deckState.value = _deckState.value.copy(
            undoStack = emptyList(),
            lastSwipeWasRight = null
        )
    }

    fun resetDeck() {
        _deckState.value = _deckState.value.copy(
            currentCardIndex = 0,
            undoStack = emptyList(),
            lastSwipeWasRight = null
        )
    }

    fun clearDeck() {
        loadedDeckType = null
        rawItems = emptyList()
        _deckState.value = DeckState()
    }
}