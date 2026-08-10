# SwipeClean — Claude Instructions

This is an Android app. Full project context is in `.claude/project.md`. Read it first before making any changes.

## Quick Reference

**Package**: `com.example.swipeclean`
**Language**: Kotlin + Jetpack Compose + Material3
**Architecture**: MVVM, no Hilt, no NavComponent, manual `mutableStateOf` navigation
**Persistence**: DataStore only (no Room)
**Media access**: Android MediaStore API
**Video thumbnails**: Coil `VideoFrameDecoder` registered in `SwipeCleanApplication`
**Min SDK**: 24 | **Target/Compile SDK**: 34 | **Kotlin**: 2.2.10

## Coding Rules

- Match existing code style: no DI framework, ViewModels take `Context` directly
- UI state = single immutable data class, updated via `.copy()`
- All async work uses Kotlin Coroutines / Flow, runs on `Dispatchers.IO`
- New screens go in `ui/<feature>/`, new ViewModels go in `viewmodel/`
- Exception: `StatsViewModel` lives in `ui/stats/` — do NOT move it
- Navigation: add new screens to the `AppScreen` enum in `MainActivity.kt` and handle in the `when` block
- Do NOT add Hilt, Room, or Jetpack Navigation unless explicitly asked
- Do NOT remove existing code unless asked

## Current Screens
| Screen | File | ViewModel |
|--------|------|-----------|
| Deck (swipe) | `ui/deck/DeckScreen.kt` | `viewmodel/DeckViewModel.kt` |
| Trash Bin | `ui/trash/TrashBinScreen.kt` | `viewmodel/TrashBinViewModel.kt` |
| Stats | `ui/stats/StatsScreen.kt` | `ui/stats/StatsViewModel.kt` |

## Key Files to Know
| File | Purpose |
|------|---------|
| `SwipeCleanApplication.kt` | Registers Coil `VideoFrameDecoder` globally for video thumbnails |
| `MainActivity.kt` | Entry point, permission check, `AppScreen` navigation |
| `data/model/MediaItem.kt` | Core domain model (photo/video) |
| `data/model/TrashItem.kt` | Serializable trash entry |
| `ui/deck/DeckState.kt` | Deck UI state + `DeckType` enum |
| `ui/deck/MediaCard.kt` | Swipeable card with drag + tap-for-video gesture |
| `ui/menu/AppMenu.kt` | Hamburger menu (Images, Videos, Trash, Stats) |
| `ui/permission/PermissionDialog.kt` | Multi-permission request (handles API 24–34+) |
| `data/repository/MediaStoreRepository.kt` | All MediaStore queries (Flow-based, batched) |
| `data/repository/TrashBinRepository.kt` | DataStore trash persistence (JSON, max 100, auto-purge 30d) |
| `data/repository/StatsRepository.kt` | DataStore stats persistence |
| `util/PermissionHelper.kt` | Permission check utilities |

## Permissions (AndroidManifest)
- `READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO` (API 33+)
- `READ_MEDIA_VISUAL_USER_SELECTED` (API 34+ partial access)
- `READ_EXTERNAL_STORAGE` (API ≤ 32)
- `POST_NOTIFICATIONS`

## Known Quirks
- `AppSettings.kt` model exists but is NOT yet wired to DataStore
- `PermissionDialogState.kt` exists but is NOT used by `PermissionDialog.kt`
- `MainScreen.kt` is a Phase 1 leftover — not used anywhere
- `AppMenu` has `onImagesClick` and `onVideosClick` callbacks that are not yet navigating anywhere (Phase 4.5 placeholders)
