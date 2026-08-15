# StorageRush — Project Context

## What is StorageRush?
StorageRush is an Android app that lets users quickly clean up their phone storage by swiping through media files (photos/videos) — swipe right to keep, swipe left to trash. Think Tinder, but for your gallery clutter. It includes a gamification layer (XP, levels, streaks, achievements) to reward consistent cleanup habits, and a cloud backend (Supabase) for leaderboards and cross-device progress restore.

## Tech Stack
- **Language**: Kotlin
- **UI**: Jetpack Compose + Material3
- **Architecture**: MVVM (ViewModel + StateFlow)
- **Data**: Android MediaStore API (no Room DB)
- **Persistence**: Jetpack DataStore Preferences (trash list, stats, player progress, user prefs)
- **Image/Video loading**: Coil (`coil-compose:2.6.0`) + `VideoFrameDecoder` for video thumbnails
- **Video playback**: Media3 ExoPlayer (`media3-exoplayer:1.3.1`, `media3-ui:1.3.1`) for full-screen overlay
- **Serialization**: `kotlinx-serialization-json:1.6.0` (for trash + user prefs persistence)
- **Coroutines**: Kotlin Flows for async media loading
- **Cloud backend**: Supabase (`supabase-kt BOM 3.5.0`) — anonymous auth, Postgrest, email OTP
- **HTTP engine**: Ktor Android (`ktor-client-android:3.1.1`) — required by supabase-kt
- **Min SDK**: 24 | **Target SDK**: 34 | **Compile SDK**: 36
- **Java 8+ APIs**: enabled via `coreLibraryDesugaring:2.0.4` (also required for supabase-kt on minSdk 24)
- **Kotlin**: 2.2.10 | **AGP**: 9.3.1 | **Compose BOM**: 2026.06.01

## Package
`com.storagerush.app`

---

## Architecture Overview

```
StorageRushApplication          ← Application class, registers Coil VideoFrameDecoder
└── MainActivity
    └── StorageRushTheme
        ├── PermissionDialog       ← shown if permissions not granted
        ├── OnboardingGateOverlay  ← mandatory first-launch gate (blocks until tutorial done)
        ├── TutorialGuideScreen    ← 4-card tutorial (mandatory or dismissible mode)
        ├── AccountLinkDialog      ← optional email OTP Sign Up / Log In (shown once after tutorial)
        ├── NicknameSetupDialog    ← mandatory nickname + cloud profile creation (shown after tutorial)
        └── Navigation (manual mutableStateOf<AppScreen>)
            ├── DeckScreen
            ├── ImageSectionsScreen
            ├── VideoSectionsScreen
            ├── TrashBinScreen
            ├── StatsScreen
            └── TutorialGuideScreen (voluntary reopen via "?" button)
```

### Navigation
No Jetpack Navigation component. Navigation is a simple `mutableStateOf<AppScreen>` in `MainActivity`. Screens enum: `DECK`, `TRASH_BIN`, `STATS`, `IMAGE_SECTIONS`, `VIDEO_SECTIONS`, `TUTORIAL`.

### State Management
All UI state is held in ViewModels as `MutableStateFlow`, collected in Compose via `collectAsState()`. ViewModels are instantiated directly in `MainActivity` (no Hilt/DI).

---

## File Structure

```
app/src/main/java/com/storagerush/app/
├── StorageRushApplication.kt    # Application class — registers Coil VideoFrameDecoder globally
├── MainActivity.kt              # Entry point, permission check, navigation, onboarding + cloud setup gate
├── MainScreen.kt                # Legacy Phase 1 placeholder (not used in production)
├── data/
│   ├── model/
│   │   ├── remote/
│   │   │   ├── PlayerRecord.kt      # Mirrors public.players Supabase table (@Serializable)
│   │   │   ├── FriendRecord.kt      # Mirrors public.friends table + FriendLeaderboardEntry joined shape
│   │   │   └── FriendCodeRecord.kt  # Mirrors public.friend_codes table
│   │   ├── MediaItem.kt         # Core domain model for a photo/video
│   │   ├── TrashItem.kt         # Serializable model for trashed items (includes uri: String)
│   │   ├── BucketInfo.kt        # Represents a discovered image folder (bucketId, name, count, thumbnail)
│   │   └── AppSettings.kt       # User preferences model (not yet persisted to DataStore)
│   ├── remote/
│   │   └── SupabaseClientProvider.kt  # Singleton Supabase client (Auth + Postgrest installed)
│   └── repository/
│       ├── CloudSyncRepository.kt    # Anonymous auth, player record CRUD, OTP sign-up/login, progress sync
│       ├── FriendsRepository.kt      # Friend code generate/redeem, friends leaderboard query
│       ├── MediaStoreRepository.kt   # Queries MediaStore, returns Flow<List<MediaItem>>
│       ├── TrashBinRepository.kt     # DataStore-backed trash list (JSON serialized)
│       ├── StatsRepository.kt        # DataStore-backed cleanup stats + restoreFromCloud()
│       ├── PlayerRepository.kt       # DataStore-backed player progression + restoreFromCloud()
│       └── UserPreferencesRepository.kt  # DataStore user prefs (last deck, tutorial seen, cloud setup, account linking)
├── ui/
│   ├── components/
│   │   └── HelpButton.kt        # Reusable "?" circle button (Deck top bar + onboarding gate)
│   ├── deck/
│   │   ├── DeckScreen.kt
│   │   ├── DeckState.kt         # UI state data class + DeckType sealed class + VideoFilterType enum
│   │   ├── MediaCard.kt         # Swipeable card (drag gesture + tap-to-preview for videos)
│   │   ├── MediaInfoPanel.kt
│   │   ├── ActionButtons.kt
│   │   ├── UndoButton.kt
│   │   ├── EmptyDeckState.kt
│   │   ├── ProgressCard.kt      # Compact Level/XP/streak bar
│   │   ├── AchievementToast.kt  # Top-anchored animated toast for achievement unlocks
│   │   ├── LevelUpCelebrationOverlay.kt
│   │   └── VideoOverlay.kt      # Full-screen ExoPlayer video overlay
│   ├── menu/
│   │   └── AppMenu.kt
│   ├── onboarding/
│   │   ├── AccountLinkDialog.kt     # Optional email OTP Sign Up / Log In (shown once after tutorial, dismissible)
│   │   └── NicknameSetupDialog.kt   # Mandatory nickname + Supabase player record creation
│   ├── permission/
│   │   ├── PermissionDialog.kt
│   │   └── PermissionDialogState.kt  # (exists but not actively used by PermissionDialog)
│   ├── sections/
│   │   ├── ImageSectionsScreen.kt
│   │   └── VideoSectionsScreen.kt
│   ├── stats/
│   │   └── StatsScreen.kt       # 3-tab stats screen (Progress / Achievements / Leaderboard)
├── viewmodel/
│   │   └── StatsViewModel.kt    # Moved here from ui/stats/; holds StatsUiState + FriendsUiState + GlobalUiState
│   ├── theme/
│   │   ├── Color.kt
│   │   ├── Theme.kt
│   │   └── Type.kt
│   ├── trash/
│   │   └── TrashBinScreen.kt
│   └── tutorial/
│       ├── OnboardingGateOverlay.kt
│       └── TutorialGuideScreen.kt
├── util/
│   └── PermissionHelper.kt
└── viewmodel/
    ├── DeckViewModel.kt
    ├── PlayerViewModel.kt
    └── TrashBinViewModel.kt     # Now includes syncProgressToCloud() after every deletion
```

---

## Core Models

### MediaItem
Represents a single photo or video from MediaStore.
- `id: Long`, `uri: Uri`, `displayName`, `mimeType`, `sizeBytes`, `dateAddedSeconds`, `dateModifiedSeconds`
- `relativePath`, `bucketDisplayName`, `width`, `height`, `duration` (ms, for videos), `isDraft: Boolean`
- Helpers: `getReadableSize()`, `getReadableDate()`, `getMonthYear()`, `isScreenshot()`, `isLargeVideo()`, `getMediaTypeCategory()`

### TrashItem (`@Serializable`)
- `mediaId`, `displayName`, `sizeBytes`, `mimeType`, `dateAddedSeconds`, `createdAtMillis`
- `uri: String` — actual MediaStore URI string (never reconstruct from IDs)
- Helpers: `isOlderThan(days)`, `getAgeInDays()`, `getReadableSize()`

### BucketInfo
- `bucketId: Long`, `bucketName: String`, `itemCount: Int`, `thumbnailUri: Uri?`, `representativePath: String`

### PlayerRecord (`@Serializable`, remote)
Mirrors `public.players` Supabase table.
- `id: String?`, `anonymousId: String`, `nickname: String`
- `level`, `currentXp`, `totalCareerXp`, `weeklyStreak`, `bestStreak`
- `storageFreedBytes`, `totalMediaCleaned`, `largestSingleCleanupBytes`, `lastCleanupTimestamp`

### FriendRecord / FriendLeaderboardEntry (`@Serializable`, remote)
- `FriendRecord`: mirrors `public.friends` (one-way: `userId → friendId`)
- `FriendLeaderboardEntry`: joined shape for leaderboard display (`nickname`, `level`, `totalCareerXp`, `weeklyStreak`)

### FriendCodeRecord (`@Serializable`, remote)
Mirrors `public.friend_codes` — `id`, `code`, `playerId`, `isUsed`, `usedById`.

### DeckState / DeckType / VideoFilterType
- `DeckType`: sealed class — `Screenshots`, `MonthlyPhotos`, `Bucket(bucketId, bucketName)`, `VideoFilter(filter)`
- `VideoFilterType`: `LARGE_VIDEOS`, `SHORT_VIDEOS`, `ALL_VIDEOS`

### TrashBinState
- `trashItems`, `totalCount`, `totalSizeBytes`, `isLoading`, `errorMessage`
- `selectedForDeletion: Set<Long>`, `showDeleteConfirmation`
- `lastDeletionFreedBytes`, `lastDeletionItemCount`, `lastXpEarned`, `leveledUp`, `newlyUnlockedAchievements`

### AppStats / PlayerState / StatsUiState
See `StatsRepository.kt`, `PlayerRepository.kt`, `StatsViewModel.kt` — unchanged from before.

---

## Key Flows

### Swipe Flow
1. User swipes left/right or taps Keep/Trash buttons
2. `DeckViewModel.swipeLeft()` / `swipeRight()` called
3. On swipe left: `TrashBinRepository.addToTrash(mediaItem)`
4. `currentCardIndex` increments, item pushed to `undoStack` (capped at 5)
5. Undo: pops `undoStack`, calls `TrashBinRepository.removeFromTrash()` if last action was trash

### Deletion Flow
1. User opens TrashBinScreen, selects items, taps Delete → confirmation dialog
2. `TrashBinViewModel.getAllUrisForDeletion()` → `MainActivity.launchFileDeletion()` → `MediaStore.createDeleteRequest` (API 30+) or fallback
3. On success: `TrashBinViewModel.onDeletionSuccess()` → updates stats + XP + streak locally, then calls `syncProgressToCloud()` fire-and-forget

### Cloud Sync Flow (Phase A)
1. After tutorial, `AccountLinkDialog` is shown (optional, dismissible via X)
   - Sign Up path: `sendSignUpOtp()` → `verifySignUpOtp()` → links email to current anonymous session → falls through to `NicknameSetupDialog`
   - Log In path: `sendLoginOtp()` → `verifyLoginOtp()` → fetches `PlayerRecord` → calls `PlayerRepository.restoreFromCloud()` + `StatsRepository.restoreFromCloud()` → marks setup complete, skips nickname dialog
   - Dismissed: falls through to `NicknameSetupDialog`
2. `NicknameSetupDialog` shown if `!hasCompletedCloudSetup`:
   - `CloudSyncRepository.isNicknameAvailable()` → `ensureSignedIn()` → `createPlayerRecord()` → `UserPreferencesRepository.markCloudSetupComplete()`
3. After every deletion: `TrashBinViewModel.syncProgressToCloud()` pushes latest `PlayerState` + `AppStats` to Supabase (only if `hasCompletedCloudSetup()` is true)

### Gamification Flow
- `PlayerRepository.recordCleanup(freedBytes)` called on every deletion
- Updates weekly streak (IST-based), awards XP via `XpCalculator`, rolls over level-ups
- Returns `CleanupRewardResult(xpEarned, levelsGained, newState)`
- `TrashBinViewModel` stores `lastXpEarned` / `leveledUp` / `newlyUnlockedAchievements` in state
- `DeckScreen` shows `LevelUpCelebrationOverlay` or snackbar on return from trash; enqueues achievement toasts

### Onboarding / Tutorial Flow
1. First launch: `OnboardingGateOverlay` blocks UI → user taps "?" → `TutorialGuideScreen(isMandatory=true)`
2. On complete: `markTutorialSeen()` → `AccountLinkDialog` shown (if `!hasCompletedCloudSetup`)
3. After account link (or dismiss): `NicknameSetupDialog` shown (if `!hasCompletedCloudSetup`)
4. Subsequent launches: gate never shows. "?" button reopens `TutorialGuideScreen(isMandatory=false)`

### Media Loading
`MediaStoreRepository` queries `MediaStore.Files.getContentUri("external")` and emits batches via `Flow` on `Dispatchers.IO`. Key methods: `getScreenshots()`, `getLargeVideos()`, `getShortVideos()`, `getAllVideosSorted()`, `getPhotosByMonth()`, `getMediaByBucket()`, `getMediaByBucketId()`, `getAllImageBuckets()`, `resolveDefaultBucket()`.

---

## Persistence (DataStore)
Four DataStore instances:
- `trash_bin` — trash list as JSON (`List<TrashItem>`)
- `app_stats` — `total_storage_freed_bytes`, `total_media_cleaned`, `last_session_*`, `largest_single_cleanup_bytes`
- `player_progress` — `player_level`, `player_current_xp`, `player_total_career_xp`, `weekly_streak`, `best_streak`, `last_cleanup_timestamp`
- `user_preferences` — `last_deck_selection_json`, `has_seen_tutorial`, `has_completed_cloud_setup`, `saved_nickname`, `has_linked_account`, `linked_email`

Trash auto-purges items older than 30 days on every `addToTrash()`. Max 100 items.

---

## Cloud Backend (Supabase)

### Tables
- `public.players` — one row per user; `anonymous_id` = Supabase auth user ID
- `public.friends` — one-way relationship rows (`user_id → friend_id`)
- `public.friend_codes` — 9-char codes (charset excludes 0/O/1/I); `is_used` flag

### Auth
Anonymous sign-in via `client.auth.signInAnonymously()`. Email linking upgrades the anonymous session to a permanent account without changing the user ID or requiring data migration.

### RLS
`friends` table RLS only allows a row where `user_id` = caller's own player ID. Reciprocal friend rows are inserted via a `SECURITY DEFINER` Postgres function `redeem_friend_code(input_code)` to bypass this for the code-owner's row.

### SupabaseClientProvider
Singleton `object` — matches the app's no-DI convention. Installs `Auth` and `Postgrest` plugins. Anon key is currently hardcoded — move to `BuildConfig` before release.

---

## Gamification System

### XpCalculator
Pure Kotlin object. `calculateXp(freedBytes, streakWeeks)` — hybrid formula with linear base + exponential bonus + tiered multiplier × streak multiplier (capped at 10 weeks). `xpRequiredForLevel(level)` — soft curve: `50 + (n*40) + floor(n^1.5 * 15)`.

### Achievements
12 badges across 3 categories (Storage, Level, Streak). Unlock status is derived, never stored. `newlyUnlockedAchievements` is diffed before/after each deletion.

### Streak Logic
IST (`Asia/Kolkata`) week boundaries. Same week → unchanged. Next week → +1. Gap ≥ 2 weeks → reset to 1. Uses `bestStreak` for achievement checks so earned streaks survive resets.

---

## UI Components Detail

### DeckScreen Layout
Top bar (☰ menu + deck name + ? button) → `ProgressCard` → `MediaCard` (top 60%) → `MediaInfoPanel` + `ActionButtons` (bottom 40%) → floating `UndoButton` FAB → `SnackbarHost` → `AchievementToast` (top-anchored, 2500 ms auto-dismiss) → `VideoOverlay` → `LevelUpCelebrationOverlay`

### AccountLinkDialog
Full-screen scrim overlay. Sign Up / Log In toggle. Email field → "Send code" → 6-digit OTP field → "Verify". Always dismissible via X. Three outcomes: `onDismissed`, `onNeedsNickname`, `onFullyRestored`. Accepts `confirmBeforeRestore: Boolean` (default false) — when true (menu-triggered path), a successful Log In pauses on an inline "replace your current progress?" confirmation before writing to local DataStore.

### AppMenu
Hamburger dropdown: Images, Videos, Trash Bin, Cleanup Stats, and a live account item that flips between "🔐 Sign Up / Log In" and "🔓 Log Out (email)" driven by `UserPreferencesRepository.isAccountLinkedFlow`.

### NicknameSetupDialog
Full-screen scrim overlay. Nickname field → "Check Availability" → "Create profile". Not dismissible — blocks until setup completes. Gated by `hasCompletedCloudSetup` so it never shows twice.

### StatsScreen (3 tabs)
- **Progress**: Level ring, weekly/best streak, lifetime stats
- **Achievements**: 2-column grid of 12 badges; locked = 🔒
- **Leaderboard**: 3 sub-scopes loaded on-demand:
  - **Local**: your stats card + personal best (streak, biggest cleanup, highest level)
  - **Friends**: your friend code (copy button) + redeem-code input + friends list sorted by lifetime XP. Loaded via `StatsViewModel.loadFriendsTab()`
  - **Global**: top-50 players worldwide ranked by lifetime XP; current player's row highlighted with "You" badge if in top 50. Loaded via `StatsViewModel.loadGlobalTab()`

### TutorialGuideScreen
4-card `HorizontalPager`. `isMandatory=true`: "Skip" + "Got it, let's go!" both call `onComplete`. `isMandatory=false`: X close + "Done" both call `onDismiss`.

---

## Permissions

### Manifest
`READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO` (API 33+), `READ_MEDIA_VISUAL_USER_SELECTED` (API 34+), `READ_EXTERNAL_STORAGE` (API ≤ 32, `maxSdkVersion="32"`), `POST_NOTIFICATIONS`

### MediaPermissionState (enum in `MainActivity.kt`)
`FULL`, `PARTIAL`, `DENIED`

---

## Known Patterns & Conventions
- ViewModels take `Context` directly (no Hilt)
- All repository methods are `suspend fun` or return `Flow`
- UI state = single immutable data class updated via `.copy()`
- No Navigation component — screen switching is `mutableStateOf<AppScreen>` in `MainActivity`
- `StatsViewModel` lives in `viewmodel/` — the old `ui/stats/StatsViewModel.kt` path is no longer used; do not recreate it there
- `MainScreen.kt` is a leftover Phase 1 placeholder, not used
- `AppSettings.kt` model exists but not yet wired to DataStore
- `PermissionDialogState.kt` exists but not actively used
- `TrashItem.uri` stores the actual MediaStore URI string — never reconstruct from IDs
- Streak timezone hardcoded to IST — intentional, do not change
- Cloud sync is fire-and-forget relative to local state — local DataStore is always source of truth
- `FriendsRepository` is fully implemented and the Friends leaderboard tab UI is built
- Global leaderboard tab is built (top-50 worldwide, "You" highlight)
- Logout flow: `cloudSyncRepository.signOut()` + `userPreferencesRepository.clearAccountLink()` + `playerRepository.resetToNewPlayer()` + `statsRepository.clearAllStats()` — all called together from MainActivity's logout confirmation dialog
- `AccountLinkDialog` is also reachable mid-session from the hamburger menu (`showMenuAccountLinkDialog`) with `confirmBeforeRestore = true`
