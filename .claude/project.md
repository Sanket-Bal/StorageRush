# SwipeClean — Project Context

## What is SwipeClean?
SwipeClean is an Android app that lets users quickly clean up their phone storage by swiping through media files (photos/videos) — swipe right to keep, swipe left to trash. Think Tinder, but for your gallery clutter.

## Tech Stack
- **Language**: Kotlin
- **UI**: Jetpack Compose + Material3
- **Architecture**: MVVM (ViewModel + StateFlow)
- **Data**: Android MediaStore API (no Room DB)
- **Persistence**: Jetpack DataStore Preferences (trash list + stats)
- **Image loading**: Coil (`coil-compose:2.6.0`)
- **Serialization**: `kotlinx-serialization-json` (for trash persistence)
- **Coroutines**: Kotlin Flows for async media loading
- **Min SDK**: 24 | **Target SDK**: 34
- **Java 8+ APIs**: enabled via `coreLibraryDesugaring`

## Package
`com.example.swipeclean`

---

## Architecture Overview

```
MainActivity
└── SwipeCleanTheme
    ├── PermissionDialog (if permissions not granted)
    └── Navigation (manual state-based, no NavComponent)
        ├── DeckScreen  ← main screen
        ├── TrashBinScreen
        └── StatsScreen
```

### Navigation
No Jetpack Navigation component. Navigation is a simple `mutableStateOf<AppScreen>` in `MainActivity`. Screens: `DECK`, `TRASH_BIN`, `STATS`.

### State Management
All UI state is held in ViewModels as `MutableStateFlow`, collected in Compose via `collectAsState()`. ViewModels are instantiated directly in `MainActivity` (no Hilt/DI).

---

## File Structure

```
app/src/main/java/com/example/swipeclean/
├── MainActivity.kt              # Entry point, permission check, navigation
├── MainScreen.kt                # Legacy placeholder screen (Phase 1 artifact)
├── data/
│   ├── model/
│   │   ├── MediaItem.kt         # Core domain model for a photo/video
│   │   ├── TrashItem.kt         # Serializable model for trashed items
│   │   └── AppSettings.kt       # User preferences model (not yet persisted)
│   └── repository/
│       ├── MediaStoreRepository.kt   # Queries MediaStore, returns Flow<List<MediaItem>>
│       ├── TrashBinRepository.kt     # DataStore-backed trash list (JSON serialized)
│       └── StatsRepository.kt        # DataStore-backed cleanup stats
├── ui/
│   ├── deck/
│   │   ├── DeckScreen.kt        # Main swipe screen composable
│   │   ├── DeckState.kt         # UI state data class + DeckType enum
│   │   ├── MediaCard.kt         # Swipeable card composable (drag gesture)
│   │   ├── MediaInfoPanel.kt    # File info below the card
│   │   ├── ActionButtons.kt     # Keep / Trash buttons
│   │   ├── UndoButton.kt        # Floating undo button (bottom-right)
│   │   └── EmptyDeckState.kt    # Empty/completed deck UI
│   ├── menu/
│   │   └── AppMenu.kt           # Hamburger/dropdown menu (Trash, Stats links)
│   ├── permission/
│   │   ├── PermissionDialog.kt  # READ_MEDIA_IMAGES + READ_MEDIA_VIDEO request
│   │   └── PermissionDialogState.kt
│   ├── stats/
│   │   ├── StatsScreen.kt       # Lifetime + last session stats display
│   │   └── StatsViewModel.kt    # Loads AppStats from StatsRepository
│   ├── trash/
│   │   └── TrashBinScreen.kt    # List of trashed items, bulk delete, restore
│   └── theme/
│       ├── Color.kt
│       ├── Theme.kt
│       └── Type.kt
├── util/
│   └── PermissionHelper.kt
└── viewmodel/
    ├── DeckViewModel.kt         # Deck loading, swipe logic, undo stack
    └── TrashBinViewModel.kt     # Trash state, selection, deletion, stats update
```

---

## Core Models

### MediaItem
Represents a single photo or video from MediaStore.
- `id: Long` — MediaStore ID
- `uri: Uri` — content URI
- `displayName`, `mimeType`, `sizeBytes`, `dateAddedSeconds`, `dateModifiedSeconds`
- `relativePath`, `bucketDisplayName`
- `width`, `height`, `duration` (ms, for videos)
- Helper methods: `getReadableSize()`, `getReadableDate()`, `getMonthYear()`, `isScreenshot()`, `isLargeVideo(thresholdBytes)`

### TrashItem
Serializable snapshot of a trashed media item (stored in DataStore).
- `mediaId`, `displayName`, `sizeBytes`, `mimeType`, `dateAddedSeconds`, `createdAtMillis`
- Helper: `isOlderThan(days)`, `getAgeInDays()`

### DeckState
Immutable UI state for the swipe deck.
- `mediaItems: List<MediaItem>`, `currentCardIndex: Int`
- `undoStack: List<MediaItem>` (max depth: 5)
- `isLoading`, `errorMessage`, `lastSwipeWasRight`, `deckType`
- Helpers: `getCurrentCard()`, `isEmpty()`, `isComplete()`, `getRemainingCount()`, `getProgressPercentage()`, `canUndo()`

### DeckType (enum)
`SCREENSHOTS`, `LARGE_VIDEOS`, `MONTHLY_PHOTOS`, `CUSTOM`

---

## Key Flows

### Swipe Flow
1. User swipes left (trash) or right (keep) on `MediaCard`
2. `DeckViewModel.swipeLeft()` / `swipeRight()` called
3. On swipe left: `TrashBinRepository.addToTrash(mediaItem)` is called
4. `currentCardIndex` increments, item pushed to `undoStack`
5. Undo: pops `undoStack`, decrements index, calls `TrashBinRepository.removeFromTrash()` if last action was trash

### Deletion Flow
1. User opens TrashBinScreen, selects items via checkboxes
2. Taps Delete → confirmation dialog shown
3. On confirm: `TrashBinViewModel.deleteSelectedItems()` triggers `MediaStore.createDeleteRequest` (OS-level delete dialog on Android 11+)
4. On success: `onDeletionSuccess()` updates `StatsRepository`, removes items from `TrashBinRepository`
5. On return to DeckScreen: snackbar shows "✅ Freed X MB | Deleted N item(s)"

### Media Loading
`MediaStoreRepository` queries `MediaStore.Files.getContentUri("external")` and emits batches via `Flow`:
- `getScreenshots()` — filters by `RELATIVE_PATH LIKE '%Screenshots%'`
- `getLargeVideos(thresholdBytes)` — filters videos by size > threshold (default 50 MB)
- `getPhotosByMonth()` — all images excluding screenshots, sorted by date
- `getMediaByBucket(bucketName)` — custom bucket filter

---

## Permissions
Required: `READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO` (Android 13+)
Checked in `MainActivity.checkPermissions()`. If not granted, `PermissionDialog` is shown before any content loads.

---

## Persistence (DataStore)
Two DataStore instances:
- `trash_bin` — stores trash list as JSON string (`List<TrashItem>`)
- `app_stats` — stores `Long` keys: `total_storage_freed_bytes`, `total_media_cleaned`, `last_session_freed_bytes`, `last_session_media_cleaned`

Trash auto-purges items older than 30 days on every `addToTrash()` call. Max 100 items in trash.

---

## Known Patterns & Conventions
- ViewModels take `Context` directly in constructor (no Hilt)
- All repository methods are either `suspend fun` or return `Flow`
- UI state is always a single immutable data class updated via `.copy()`
- No Navigation component — screen switching is `mutableStateOf<AppScreen>`
- `StatsViewModel` lives in `ui/stats/` (not `viewmodel/`) — inconsistency to be aware of
- `MainScreen.kt` is a leftover Phase 1 placeholder, not used in production flow
