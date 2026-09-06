# StorageRush — Claude Instructions

This is an Android app. Full project context is in `.claude/project.md`. Read it first before making any changes.

## Quick Reference

**Package**: `com.storagerush.app`
**App Name**: Storage Rush
**Language**: Kotlin + Jetpack Compose + Material3
**Architecture**: MVVM, no Hilt, no NavComponent, manual `mutableStateOf` navigation
**Persistence**: DataStore only (no Room)
**Media access**: Android MediaStore API
**Video thumbnails**: Coil `VideoFrameDecoder` registered in `StorageRushApplication`
**Cloud backend**: Supabase (anonymous auth + Postgrest) via `supabase-kt BOM 3.5.0`
**Min SDK**: 24 | **Target/Compile SDK**: 36 | **Version**: 1.1 (code 2) | **Kotlin**: 2.2.10 | **AGP**: 9.3.1

## Coding Rules

- Match existing code style: no DI framework, ViewModels take `Context` directly
- UI state = single immutable data class, updated via `.copy()`
- All async work uses Kotlin Coroutines / Flow, runs on `Dispatchers.IO`
- New screens go in `ui/<feature>/`, new ViewModels go in `viewmodel/`
- Exception: `StatsViewModel` lives in `ui/stats/` (file path) but declares `package com.storagerush.app.viewmodel` — do NOT move it
- Navigation: add new screens to the `AppScreen` enum in `MainActivity.kt` and handle in the `when` block
- Do NOT add Hilt, Room, or Jetpack Navigation unless explicitly asked
- Do NOT remove existing code unless asked

## Current Screens
| Screen | File | ViewModel |
|--------|------|-----------| 
| Deck (swipe) | `ui/deck/DeckScreen.kt` | `viewmodel/DeckViewModel.kt` |
| Trash Bin | `ui/trash/TrashBinScreen.kt` | `viewmodel/TrashBinViewModel.kt` |
| Stats (3-tab) | `ui/stats/StatsScreen.kt` | `ui/stats/StatsViewModel.kt` (package: `viewmodel`) |
| Image Sections | `ui/sections/ImageSectionsScreen.kt` | *(no ViewModel — direct repo call)* |
| Video Sections | `ui/sections/VideoSectionsScreen.kt` | *(no ViewModel — static list)* |
| Tutorial | `ui/tutorial/TutorialGuideScreen.kt` | *(no ViewModel)* |

## Leaderboard Scopes (StatsScreen → Leaderboard tab)
| Scope | Source | State class |
|-------|--------|-------------|
| Local | Local DataStore | `StatsUiState` / `PlayerState` |
| Friends | Supabase `friends` + `players` join | `FriendsUiState` in `StatsViewModel` |
| Global | Supabase `players` top-50 | `GlobalUiState` in `StatsViewModel` |

## Key Files to Know
| File | Purpose |
|------|---------|
| `StorageRushApplication.kt` | Registers Coil `VideoFrameDecoder` globally for video thumbnails |
| `MainActivity.kt` | Entry point, permission check, `AppScreen` navigation, onboarding gate + cloud setup logic |
| `data/model/MediaItem.kt` | Core domain model (photo/video) |
| `data/model/TrashItem.kt` | Serializable trash entry (includes `uri: String`) |
| `data/model/BucketInfo.kt` | Represents a discovered image folder (bucketId, name, count, thumbnail) |
| `data/model/AppSettings.kt` | User preferences model (not yet wired to DataStore) |
| `data/model/remote/PlayerRecord.kt` | Mirrors `public.players` Supabase table |
| `data/model/remote/FriendRecord.kt` | Mirrors `public.friends` table + `FriendLeaderboardEntry` joined shape |
| `data/model/remote/FriendCodeRecord.kt` | Mirrors `public.friend_codes` table |
| `data/remote/SupabaseClientProvider.kt` | Singleton Supabase client (Auth + Postgrest); keys read from `BuildConfig` (sourced from `local.properties`) |
| `data/repository/CloudSyncRepository.kt` | Anonymous auth, player record CRUD, nickname update, global leaderboard, OTP sign-up/login, progress sync |
| `data/repository/FriendsRepository.kt` | Friend code generate/redeem, friends leaderboard query (fully implemented) |
| `ui/stats/StatsViewModel.kt` | Lives at `ui/stats/StatsViewModel.kt` but in package `com.storagerush.app.viewmodel`; holds `StatsUiState` + `FriendsUiState` + `GlobalUiState` + nickname edit; exposes `nickname`, `hasCloudProfile`, `isAccountLinked` flows and `updateNickname()` |
| `ui/deck/DeckState.kt` | Deck UI state + `UndoEntry` data class + `DeckType` sealed class + `VideoFilterType` enum |
| `ui/deck/MediaCard.kt` | Swipeable card with drag + tap-for-video gesture |
| `ui/deck/ProgressCard.kt` | Compact Level/XP/streak bar shown at top of Deck |
| `ui/deck/AchievementToast.kt` | Top-anchored animated toast for achievement unlocks |
| `ui/deck/LevelUpCelebrationOverlay.kt` | Full-screen level-up celebration |
| `ui/deck/VideoOverlay.kt` | Full-screen ExoPlayer video overlay |
| `ui/menu/AppMenu.kt` | Hamburger menu (Images, Videos, Trash Bin, Cleanup Stats) |
| `ui/components/HelpButton.kt` | Reusable "?" circle button |
| `ui/onboarding/AccountLinkDialog.kt` | Optional email OTP Sign Up / Log In dialog (shown once after tutorial) |
| `ui/onboarding/NicknameSetupDialog.kt` | Mandatory nickname + cloud profile creation dialog |
| `ui/permission/PermissionDialog.kt` | Multi-permission request (handles API 24–34+) |
| `ui/tutorial/OnboardingGateOverlay.kt` | Mandatory first-launch gate — blocks UI until user taps "?" |
| `ui/tutorial/TutorialGuideScreen.kt` | 4-card swipeable tutorial (mandatory + dismissible modes) |
| `data/repository/MediaStoreRepository.kt` | All MediaStore queries (Flow-based, batched) |
| `data/repository/TrashBinRepository.kt` | DataStore trash persistence (JSON, max 100, auto-purge 30d) |
| `data/repository/StatsRepository.kt` | DataStore stats persistence + `restoreFromCloud()` |
| `data/repository/PlayerRepository.kt` | DataStore player progression (XP, level, streak) + `restoreFromCloud()` |
| `data/repository/UserPreferencesRepository.kt` | DataStore user prefs (last deck, tutorial seen, cloud setup, account linking); contains `LastDeckSelection` serializable class |
| `data/gamification/XpCalculator.kt` | Pure XP/level math (no Android deps) |
| `data/gamification/Achievement.kt` | 12 achievement badge definitions (`AchievementDefinitions.ALL`) |
| `viewmodel/PlayerViewModel.kt` | Exposes `PlayerState` Flow for Deck's ProgressCard |
| `viewmodel/TrashBinViewModel.kt` | Includes `syncProgressToCloud()` after every deletion; `TrashBinState` defined here |
| `viewmodel/DeckViewModel.kt` | Manages deck loading, swipe/undo logic; exposes `clearUndoStack()`, `resetDeck()`, `clearDeck()` |

## Permissions (AndroidManifest)
- `READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO` (API 33+)
- `READ_MEDIA_VISUAL_USER_SELECTED` (API 34+ partial access)
- `READ_EXTERNAL_STORAGE` (API ≤ 32)
- `POST_NOTIFICATIONS`

## Known Quirks
- `AppSettings.kt` model exists but is NOT yet wired to DataStore
- `PermissionDialogState.kt` exists but is NOT used by `PermissionDialog.kt`
- `MainScreen.kt` is a Phase 1 leftover — not used anywhere
- `StatsViewModel` is physically at `ui/stats/StatsViewModel.kt` but its package declaration is `com.storagerush.app.viewmodel` — imports use the `viewmodel` package path
- `MediaPermissionState` enum (FULL / PARTIAL / DENIED) lives in `MainActivity.kt`
- Streak weeks are fixed to IST (`Asia/Kolkata`) regardless of device timezone — intentional per spec
- Supabase URL and anon key are read from `local.properties` via `BuildConfig` fields (`SUPABASE_URL`, `SUPABASE_ANON_KEY`) declared in `app/build.gradle.kts` — `local.properties` is git-ignored; missing entries fail the build loudly
- `FriendsRepository` is fully wired and the Friends leaderboard tab UI is built (friend code display/copy, redeem input, friends list)
- Global leaderboard tab is built (top-50 worldwide, "You" highlight if in top 50)
- `AccountLinkDialog` supports `confirmBeforeRestore = true` for mid-session login (menu path) to prevent silently overwriting local progress
- `AppMenu` has live Sign Up/Log In ↔ Log Out toggle driven by `UserPreferencesRepository.isAccountLinkedFlow`
- Logout flow resets local DataStore (level/XP/streak/stats/nickname) in addition to signing out of Supabase
- `TrashBinState` data class is defined inside `TrashBinViewModel.kt` (not a separate file)
- `StatsUiState`, `NicknameEditState`, `FriendsUiState`, `GlobalUiState` are all defined inside `StatsViewModel.kt`
- `AchievementDefinitions` object (with `ALL` list) is defined in `Achievement.kt` alongside the `Achievement` data class
- `UndoEntry` data class (item + wasTrashed flag) is defined in `DeckState.kt` alongside `DeckState`
- `LastDeckSelection` serializable class (storage mirror of `DeckType`) is defined inside `UserPreferencesRepository.kt`
- `FriendLeaderboardEntry` is reused for both Friends and Global leaderboard queries (same 4-column shape)
- `getGlobalLeaderboard()` in `CloudSyncRepository` selects only 4 columns explicitly — adding `*` would break decoding since `FriendLeaderboardEntry` doesn't declare all `players` columns and JSON decoding is strict
