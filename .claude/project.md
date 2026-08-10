# SwipeClean — Project Context

## What is SwipeClean?
SwipeClean is an Android app that lets users quickly clean up their phone storage by swiping through media files (photos/videos) — swipe right to keep, swipe left to trash. Think Tinder, but for your gallery clutter.

## Tech Stack
- **Language**: Kotlin
- **UI**: Jetpack Compose + Material3
- **Architecture**: MVVM (ViewModel + StateFlow)
- **Data**: Android MediaStore API (no Room DB)
- **Persistence**: Jetpack DataStore Preferences (trash list + stats)
- **Image/Video loading**: Coil (`coil-compose:2.6.0`) + `VideoFrameDecoder` for video thumbnails
- **Serialization**: `kotlinx-serialization-json:1.6.0` (for trash persistence)
- **Coroutines**: Kotlin Flows for async media loading
- **Min SDK**: 24 | **Target SDK**: 34 | **Compile SDK**: 34
- **Java 8+ APIs**: enabled via `coreLibraryDesugaring:2.0.4`
- **Kotlin**: 2.2.10 | **AGP**: 9.3.1 | **Compose BOM**: 2026.02.01

## Package
`com.example.swipeclean`

---

## Architecture Overview

```
SwipeCleanApplication          ← Application class, registers Coil VideoFrameDecoder
└── MainActivity
    └── SwipeCleanTheme
        ├── PermissionDialog   ← shown if permissions not granted
        └── Navigation (manual mutableStateOf<AppScreen>)
            ├── DeckScreen     ← main swipe screen
            ├── TrashBinScreen
            └── StatsScreen
```

### Navigation
No Jetpack Navigation component. Navigation is a simple `mutableStateOf<AppScreen>` in `MainActivity`. Screens enum: `DECK`, `TRASH_BIN`, `STATS`.

### State Management
All UI state is held in ViewModels as `MutableStateFlow`, collected in Compose via `collectAsState()`. ViewModels are instantiated directly in `MainActivity` (no Hilt/DI).

---

## File Structure

```
app/src/main/java/com/example/swipeclean/
├── SwipeCleanApplication.kt     # Application class — registers Coil VideoFrameDecoder globally
├── MainActivity.kt              # Entry point, permission check, navigation
├── MainScreen.kt                # Legacy Phase 1 placeholder (not used in production)
├── data/
│   ├── model/
│   │   ├── MediaItem.kt         # Core domain model for a photo/video
│   │   ├── TrashItem.kt         # Serializable model for trashed items
│   │   └── AppSettings.kt       # User preferences model (not yet persisted to DataStore)
│   └── repository/
│       ├── MediaStoreRepository.kt   # Queries MediaStore, returns Flow<List<MediaItem>>
│       ├── TrashBinRepository.kt     # DataStore-backed trash list (JSON serialized)
│       └── StatsRepository.kt        # DataStore-backed cleanup stats
├── ui/
│   ├── deck/
│   │   ├── DeckScreen.kt        # Main swipe screen composable
│   │   ├── DeckState.kt         # UI state data class + DeckType enum
│   │   ├── MediaCard.kt         # Swipeable card (drag gesture + tap-to-preview for videos)
│   │   ├── MediaInfoPanel.kt    # File info panel below the card
│   │   ├── ActionButtons.kt     # Keep / Trash buttons row
│   │   ├── UndoButton.kt        # Floating FAB undo button (bottom-right)
│   │   └── EmptyDeckState.kt    # Empty/completed deck UI
│   ├── menu/
│   │   └── AppMenu.kt           # Hamburger dropdown menu (Images, Videos, Trash, Stats)
│   ├── permission/
│   │   ├── PermissionDialog.kt  # Multi-permission request dialog (Photos, Videos, Notifications)
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
│   └── PermissionHelper.kt      # Permission check helpers (full/partial access, missing list)
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
- Helpers: `getReadableSize()`, `getReadableDate()`, `getMonthYear()`, `isScreenshot()`, `isLargeVideo(thresholdBytes)`

### TrashItem (`@Serializable`)
Serializable snapshot of a trashed media item (stored in DataStore as JSON).
- `mediaId`, `displayName`, `sizeBytes`, `mimeType`, `dateAddedSeconds`, `createdAtMillis`
- Helpers: `isOlderThan(days)`, `getAgeInDays()`, `getReadableSize()`

### DeckState
Immutable UI state for the swipe deck.
- `mediaItems: List<MediaItem>`, `currentCardIndex: Int`
- `undoStack: List<MediaItem>` (max depth: 5)
- `isLoading`, `errorMessage`, `lastSwipeWasRight`, `deckType: String`
- Helpers: `getCurrentCard()`, `isEmpty()`, `isComplete()`, `getRemainingCount()`, `getProgressPercentage()`, `canUndo()`

### DeckType (enum)
`SCREENSHOTS`, `LARGE_VIDEOS`, `MONTHLY_PHOTOS`, `CUSTOM`

### TrashBinState
UI state for the trash screen (lives in `TrashBinViewModel`).
- `trashItems`, `totalCount`, `totalSizeBytes`, `isLoading`, `errorMessage`
- `selectedForDeletion: Set<Long>`, `showDeleteConfirmation`
- `lastDeletionFreedBytes`, `lastDeletionItemCount`

### AppStats
Stats model from `StatsRepository`.
- `totalStorageFreedBytes`, `totalMediaCleaned`, `lastSessionFreedBytes`, `lastSessionMediaCleaned`

---

## Key Flows

### Swipe Flow
1. User swipes left (trash) or right (keep) on `MediaCard`, or taps the Keep/Trash buttons
2. `DeckViewModel.swipeLeft()` / `swipeRight()` called
3. On swipe left: `TrashBinRepository.addToTrash(mediaItem)` is called
4. `currentCardIndex` increments, item pushed to `undoStack` (capped at 5)
5. Undo: pops `undoStack`, decrements index, calls `TrashBinRepository.removeFromTrash()` if last action was trash

### Deletion Flow
1. User opens TrashBinScreen, selects items via checkboxes
2. Taps Delete → confirmation dialog shown
3. On confirm: `TrashBinViewModel.deleteSelectedItems()` triggers `MediaStore.createDeleteRequest` (OS-level delete dialog on Android 11+)
4. On success: `onDeletionSuccess()` updates `StatsRepository`, removes items from `TrashBinRepository`
5. On return to DeckScreen: snackbar shows "✅ Freed X MB | Deleted N item(s)"

### Media Loading
`MediaStoreRepository` queries `MediaStore.Files.getContentUri("external")` and emits batches via `Flow` on `Dispatchers.IO`:
- `getScreenshots()` — filters by `RELATIVE_PATH LIKE '%Screenshots%'`, batch size 20
- `getLargeVideos(thresholdBytes)` — videos by size > threshold (default 50 MB), sorted by size DESC, batch size 15
- `getPhotosByMonth()` — all images excluding screenshots, sorted by date DESC, batch size 25
- `getMediaByBucket(bucketName)` — custom bucket filter, batch size 20

### Video Thumbnail Flow
`SwipeCleanApplication` registers Coil's `VideoFrameDecoder.Factory()` globally via `ImageLoaderFactory`. This means every `AsyncImage` given a video URI automatically extracts a preview frame — no per-call setup needed.

---

## Permissions

### Manifest
```
READ_MEDIA_IMAGES          (API 33+)
READ_MEDIA_VIDEO           (API 33+)
READ_MEDIA_VISUAL_USER_SELECTED  (API 34+ — partial photo access)
READ_EXTERNAL_STORAGE      (API ≤ 32, maxSdkVersion="32")
POST_NOTIFICATIONS
```

### Runtime Request Logic (`PermissionDialog.kt`)
- API 34+: requests `READ_MEDIA_IMAGES` + `READ_MEDIA_VIDEO` + `READ_MEDIA_VISUAL_USER_SELECTED` + `POST_NOTIFICATIONS`
- API 33: requests `READ_MEDIA_IMAGES` + `READ_MEDIA_VIDEO` + `POST_NOTIFICATIONS`
- API 24–32: requests `READ_EXTERNAL_STORAGE`

### Usable Access Check
`hasUsableMediaAccess()` re-checks actual OS grants after the request result (not the raw result map), because Android 14 can grant `READ_MEDIA_VISUAL_USER_SELECTED` even when it wasn't explicitly requested (partial "Select photos" flow).

### PermissionHelper (util)
Utility object with: `getRequiredPermissions()`, `hasAllPermissions()`, `hasImagePermission()`, `hasVideoPermission()`, `hasPartialAccess()`, `getMissingPermissions()`, `getPermissionStatusMessage()`

---

## Persistence (DataStore)
Two DataStore instances:
- `trash_bin` — stores trash list as JSON string (`List<TrashItem>` via `kotlinx-serialization`)
- `app_stats` — stores `Long` keys: `total_storage_freed_bytes`, `total_media_cleaned`, `last_session_freed_bytes`, `last_session_media_cleaned`

Trash auto-purges items older than 30 days on every `addToTrash()` call. Max 100 items in trash.

---

## UI Components Detail

### MediaCard
- Drag gesture via `detectDragGestures` — threshold 200px to trigger swipe action
- Subtle card tilt rotation (max ±15°) while dragging
- Tap gesture via `detectTapGestures` — only fires `onTapVideo()` for video items; photo taps are ignored
- Video items show a `VideoBadge` overlay (play icon + formatted duration) centered on the card
- Uses `AsyncImage` (Coil) with `ContentScale.Crop`

### AppMenu
Hamburger `☰` dropdown with 4 items:
1. 🖼️ Images → `onImagesClick()`
2. 🎬 Videos → `onVideosClick()`
3. 🗑️ Trash Bin → `onTrashBinClick()`
4. 📊 Cleanup Stats → `onStatsClick()`

Items 1 & 2 are wired up but navigation targets not yet implemented (Phase 4.5 placeholders).

### DeckScreen Layout
- Top 60%: `MediaCard` (swipeable)
- Bottom 40%: `MediaInfoPanel` (File, Size, Added, Folder) + `ActionButtons` (Trash | Keep)
- Floating `UndoButton` FAB at bottom-right (only visible when `canUndo()` is true)
- `SnackbarHost` at bottom-center for post-deletion stats feedback

---

## Known Patterns & Conventions
- ViewModels take `Context` directly in constructor (no Hilt)
- All repository methods are either `suspend fun` or return `Flow`
- UI state is always a single immutable data class updated via `.copy()`
- No Navigation component — screen switching is `mutableStateOf<AppScreen>` in `MainActivity`
- `StatsViewModel` lives in `ui/stats/` (not `viewmodel/`) — inconsistency to be aware of, don't move it
- `MainScreen.kt` is a leftover Phase 1 placeholder, not used in production flow
- `AppSettings.kt` model exists but is not yet wired to DataStore persistence
- `PermissionDialogState.kt` exists but is not actively used by `PermissionDialog.kt` (dialog manages its own state internally)
