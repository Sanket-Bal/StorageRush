# SwipeClean — Claude Instructions

This is an Android app. Full project context is in `.claude/project.md`. Read it first before making any changes.

## Quick Reference

**Package**: `com.example.swipeclean`
**Language**: Kotlin + Jetpack Compose + Material3
**Architecture**: MVVM, no Hilt, no NavComponent, manual `mutableStateOf` navigation
**Persistence**: DataStore only (no Room)
**Media access**: Android MediaStore API

## Coding Rules

- Match existing code style: no DI framework, ViewModels take `Context` directly
- UI state = single immutable data class, updated via `.copy()`
- All async work uses Kotlin Coroutines / Flow, runs on `Dispatchers.IO`
- New screens go in `ui/<feature>/`, new ViewModels go in `viewmodel/` (except `StatsViewModel` which is in `ui/stats/` — don't move it)
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
- `data/model/MediaItem.kt` — core domain model
- `data/model/TrashItem.kt` — serializable trash entry
- `ui/deck/DeckState.kt` — deck UI state + `DeckType` enum
- `data/repository/MediaStoreRepository.kt` — all MediaStore queries
- `data/repository/TrashBinRepository.kt` — DataStore trash persistence
- `data/repository/StatsRepository.kt` — DataStore stats persistence
