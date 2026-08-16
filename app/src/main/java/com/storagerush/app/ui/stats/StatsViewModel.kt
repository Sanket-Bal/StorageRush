package com.storagerush.app.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.storagerush.app.data.gamification.Achievement
import com.storagerush.app.data.gamification.AchievementDefinitions
import com.storagerush.app.data.model.remote.FriendLeaderboardEntry
import com.storagerush.app.data.repository.AppStats
import com.storagerush.app.data.repository.CloudSyncRepository
import com.storagerush.app.data.repository.FriendsRepository
import com.storagerush.app.data.repository.PlayerRepository
import com.storagerush.app.data.repository.PlayerState
import com.storagerush.app.data.repository.StatsRepository
import com.storagerush.app.data.repository.UserPreferencesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Combined state for the 3-tab Stats screen (Profile / Achievements /
 * Leaderboard — the first tab was renamed from "Progress" to "Profile").
 * `achievements` is the static definition list; unlock status for each is
 * computed on read via Achievement.isUnlocked(), not stored here, so it's
 * always consistent with the latest appStats/playerState.
 */
data class StatsUiState(
    val appStats: AppStats = AppStats(),
    val playerState: PlayerState = PlayerState(),
    val achievements: List<Achievement> = AchievementDefinitions.ALL,
    val isLoading: Boolean = true
) {
    val unlockedAchievements: List<Achievement>
        get() = achievements.filter { it.isUnlocked(playerState, appStats) }

    val unlockedCount: Int
        get() = unlockedAchievements.size
}

/**
 * State for the Profile tab's inline nickname editor. Separate from the
 * reactive [StatsViewModel.nickname]/[StatsViewModel.hasCloudProfile]
 * flows below since this only covers the transient in-progress save, not
 * the nickname's persisted value itself.
 */
data class NicknameEditState(
    val isSaving: Boolean = false,
    val errorMessage: String? = null
)

/**
 * Phase B: state for the Leaderboard tab's "Friends" scope. Separate from
 * StatsUiState above since this is fetched on-demand from Supabase (not a
 * reactive local DataStore Flow) — only loaded when the user actually
 * switches to the Friends scope, not on every Stats screen open.
 */
data class FriendsUiState(
    val isLoading: Boolean = false,
    val myFriendCode: String? = null,
    val friends: List<FriendLeaderboardEntry> = emptyList(),
    val errorMessage: String? = null,
    val isRedeeming: Boolean = false,
    val redeemSuccessMessage: String? = null
)

/**
 * State for the Leaderboard tab's "Global" scope. Same on-demand-load
 * pattern as FriendsUiState above — only fetched when the user actually
 * switches to Global, since it's a network call. [myNickname] is the
 * locally cached nickname (no network needed, see
 * UserPreferencesRepository.getSavedNickname()) used purely to highlight
 * the current player's own row if they happen to be in the fetched top
 * [entries] list.
 */
data class GlobalUiState(
    val isLoading: Boolean = false,
    val entries: List<FriendLeaderboardEntry> = emptyList(),
    val myNickname: String? = null,
    val errorMessage: String? = null
)

/**
 * Maps errors from Friends/Global network calls to safe UI text. Known
 * connectivity failures (DNS resolution, connect refused, timeout) get a
 * clear "check your connection" message. Anything that looks like one of
 * Supabase's verbose RestException dumps (URL/Headers/HTTP method) falls
 * back to [fallback] instead of ever reaching the screen raw — same fix
 * applied to AccountLinkDialog's auth errors. Short, legitimate messages
 * from repository Result.failure() calls (e.g. "Invalid code") pass
 * through unchanged.
 */
private fun friendlyErrorMessage(e: Throwable, fallback: String): String {
    val raw = e.message ?: return fallback
    val isNetworkError = raw.contains("Unable to resolve host", ignoreCase = true) ||
        raw.contains("No address associated with hostname", ignoreCase = true) ||
        raw.contains("failed to connect", ignoreCase = true) ||
        raw.contains("timeout", ignoreCase = true) ||
        raw.contains("UnknownHostException", ignoreCase = true) ||
        raw.contains("ConnectException", ignoreCase = true) ||
        raw.contains("Network is unreachable", ignoreCase = true)
    if (isNetworkError) {
        return "No internet connection. Check your connection and try again."
    }
    val looksLikeRawDump = raw.contains("Headers=", ignoreCase = true) ||
        raw.contains("Http Method:", ignoreCase = true) ||
        raw.contains("HTTP request to", ignoreCase = true)
    return if (looksLikeRawDump) fallback else raw
}

class StatsViewModel(context: Context) : ViewModel() {

    private val statsRepository = StatsRepository(context)
    private val playerRepository = PlayerRepository(context)

    // Phase B: friends leaderboard
    private val cloudSyncRepository = CloudSyncRepository(context)
    private val friendsRepository = FriendsRepository(context)
    private val userPreferencesRepository = UserPreferencesRepository(context)

    val uiState: StateFlow<StatsUiState> = combine(
        statsRepository.getStats(),
        playerRepository.getPlayerState()
    ) { appStats, playerState ->
        StatsUiState(
            appStats = appStats,
            playerState = playerState,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = StatsUiState()
    )

    /**
     * Backward-compatible with the original StatsViewModel's public API
     * (StateFlow<AppStats?>, null while loading), in case any other screen
     * reads this directly rather than through uiState. Prefer uiState for
     * new code — this is a thin derived view, not a second source of truth.
     */
    val stats: StateFlow<AppStats?> = uiState
        .map { if (it.isLoading) null else it.appStats }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    /**
     * Backward-compatible synchronous accessor, matching the original
     * StatsViewModel.getCurrentStats().
     */
    fun getCurrentStats(): AppStats = uiState.value.appStats

    /**
     * Whether the player has a linked email account (Sign Up or Log In
     * completed), as opposed to just having a local nickname/anonymous
     * cloud profile via NicknameSetupDialog. Friends and Global
     * leaderboard tabs are gated on this specifically — see
     * loadFriendsTab()/loadGlobalTab() — since those are the "social /
     * cross-device" features, while Profile/Achievements/Local stay
     * available to every player regardless of linking status.
     */
    val isAccountLinked: StateFlow<Boolean> = userPreferencesRepository.isAccountLinkedFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    // ---------------------------------------------------------------
    // Profile tab: nickname display + inline edit
    // ---------------------------------------------------------------

    /** Reactive locally cached nickname, shown (and edited) on the Profile tab. */
    val nickname: StateFlow<String?> = userPreferencesRepository.savedNicknameFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    /**
     * Whether the player has a cloud profile at all (anonymous + nickname
     * via NicknameSetupDialog, or account-linked). Editing only makes
     * sense once a nickname/cloud record actually exists.
     */
    val hasCloudProfile: StateFlow<Boolean> = userPreferencesRepository.hasCompletedCloudSetupFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    private val _nicknameEditState = MutableStateFlow(NicknameEditState())
    val nicknameEditState: StateFlow<NicknameEditState> = _nicknameEditState.asStateFlow()

    /**
     * Renames the player's nickname: availability-checks it (same check
     * NicknameSetupDialog uses), updates the cloud players row, then the
     * local cache — matching the existing "cloud first, then local"
     * ordering used elsewhere in this file (see loadFriendsTab()).
     */
    fun updateNickname(newNickname: String) {
        val trimmed = newNickname.trim()
        if (trimmed.isBlank()) {
            _nicknameEditState.value = NicknameEditState(errorMessage = "Nickname can't be empty")
            return
        }
        if (trimmed == nickname.value) {
            // No actual change — nothing to save, just clear any stale error.
            _nicknameEditState.value = NicknameEditState()
            return
        }

        viewModelScope.launch {
            _nicknameEditState.value = NicknameEditState(isSaving = true)
            try {
                val available = cloudSyncRepository.isNicknameAvailable(trimmed)
                if (!available) {
                    _nicknameEditState.value = NicknameEditState(errorMessage = "Nickname already taken")
                    return@launch
                }

                val anonymousId = cloudSyncRepository.ensureSignedIn()
                val result = cloudSyncRepository.updateNickname(anonymousId, trimmed)
                result.fold(
                    onSuccess = {
                        userPreferencesRepository.updateSavedNickname(trimmed)
                        _nicknameEditState.value = NicknameEditState()
                    },
                    onFailure = { e ->
                        _nicknameEditState.value = NicknameEditState(
                            errorMessage = e.message ?: "Couldn't update nickname"
                        )
                    }
                )
            } catch (e: Exception) {
                _nicknameEditState.value = NicknameEditState(
                    errorMessage = e.message ?: "Couldn't update nickname"
                )
            }
        }
    }

    /** Clears a stale error, e.g. when the user reopens the editor after a previous failure. */
    fun clearNicknameEditError() {
        _nicknameEditState.value = _nicknameEditState.value.copy(errorMessage = null)
    }

    /**
     * Resets lifetime stats. Deliberately does NOT reset player level/XP/
     * streak — those live in PlayerRepository and represent progression
     * the user earned, which "Reset All Stats" (a storage-stats concept)
     * shouldn't silently wipe. If you want a combined reset later, add an
     * explicit second button/confirmation for that rather than folding it
     * into this one.
     */
    fun clearAllStats() {
        viewModelScope.launch {
            statsRepository.clearAllStats()
        }
    }

    // ---------------------------------------------------------------
    // Phase B: Friends leaderboard
    // ---------------------------------------------------------------

    private val _friendsUiState = MutableStateFlow(FriendsUiState())
    val friendsUiState: StateFlow<FriendsUiState> = _friendsUiState.asStateFlow()

    // Cached after first resolution — the players.id (Postgres UUID) is
    // different from the Supabase auth anonymous_id, and resolving it
    // costs a network round trip via getPlayerRecord(), so we don't want
    // to repeat that on every friends-tab interaction.
    private var cachedPlayerId: String? = null

    /**
     * Loads (or refreshes) this player's friend code and friends list.
     * Call when the Leaderboard tab's scope switches to Friends — not on
     * every Stats screen open, since this hits the network.
     */
    fun loadFriendsTab() {
        viewModelScope.launch {
            _friendsUiState.value = _friendsUiState.value.copy(isLoading = true, errorMessage = null)
            try {
                if (!userPreferencesRepository.isAccountLinkedFlow.first()) {
                    _friendsUiState.value = _friendsUiState.value.copy(
                        isLoading = false,
                        errorMessage = "Sign up or log in to use friends."
                    )
                    return@launch
                }

                val playerId = resolvePlayerId()
                if (playerId == null) {
                    _friendsUiState.value = _friendsUiState.value.copy(
                        isLoading = false,
                        errorMessage = "Couldn't find your player profile. Try again in a moment."
                    )
                    return@launch
                }

                val codeResult = friendsRepository.getOrCreateFriendCode(playerId)
                val friends = friendsRepository.getFriendsLeaderboard(playerId)

                _friendsUiState.value = _friendsUiState.value.copy(
                    isLoading = false,
                    myFriendCode = codeResult.getOrNull(),
                    friends = friends,
                    errorMessage = codeResult.exceptionOrNull()?.let {
                        friendlyErrorMessage(it, "Couldn't load your friend code. Please try again.")
                    }
                )
            } catch (e: Exception) {
                _friendsUiState.value = _friendsUiState.value.copy(
                    isLoading = false,
                    errorMessage = friendlyErrorMessage(e, "Failed to load friends. Please try again.")
                )
            }
        }
    }

    /** Redeems a friend's code, then refreshes the friends list on success. */
    fun redeemFriendCode(code: String) {
        if (code.isBlank()) return

        viewModelScope.launch {
            _friendsUiState.value = _friendsUiState.value.copy(
                isRedeeming = true,
                errorMessage = null,
                redeemSuccessMessage = null
            )
            try {
                val playerId = resolvePlayerId()
                if (playerId == null) {
                    _friendsUiState.value = _friendsUiState.value.copy(
                        isRedeeming = false,
                        errorMessage = "Couldn't find your player profile. Try again in a moment."
                    )
                    return@launch
                }

                val result = friendsRepository.redeemFriendCode(code.trim().uppercase())
                result.fold(
                    onSuccess = {
                        val friends = friendsRepository.getFriendsLeaderboard(playerId)
                        _friendsUiState.value = _friendsUiState.value.copy(
                            isRedeeming = false,
                            friends = friends,
                            redeemSuccessMessage = "Friend added!"
                        )
                    },
                    onFailure = { error ->
                        _friendsUiState.value = _friendsUiState.value.copy(
                            isRedeeming = false,
                            errorMessage = friendlyErrorMessage(error, "Couldn't redeem code. Please try again.")
                        )
                    }
                )
            } catch (e: Exception) {
                _friendsUiState.value = _friendsUiState.value.copy(
                    isRedeeming = false,
                    errorMessage = friendlyErrorMessage(e, "Couldn't redeem code. Please try again.")
                )
            }
        }
    }

    /** Clears error/success messages, e.g. after they've been shown once. */
    fun clearFriendsMessages() {
        _friendsUiState.value = _friendsUiState.value.copy(errorMessage = null, redeemSuccessMessage = null)
    }

    private suspend fun resolvePlayerId(): String? {
        cachedPlayerId?.let { return it }
        val anonymousId = cloudSyncRepository.ensureSignedIn()
        val record = cloudSyncRepository.getPlayerRecord(anonymousId) ?: return null
        cachedPlayerId = record.id
        return record.id
    }

    // ---------------------------------------------------------------
    // Global leaderboard
    // ---------------------------------------------------------------

    private val _globalUiState = MutableStateFlow(GlobalUiState())
    val globalUiState: StateFlow<GlobalUiState> = _globalUiState.asStateFlow()

    /**
     * Loads (or refreshes) the top-50 global leaderboard. Call when the
     * Leaderboard tab's scope switches to Global — not eagerly, since
     * this hits the network. Unlike Friends, no player id resolution or
     * anonymous sign-in is required to just *view* this list: it's a
     * public read, gated on having a linked account (same gate Friends
     * uses) — an anonymous player with just a local nickname doesn't get
     * the social/cross-device tabs, only Progress/Achievements/Local.
     */
    fun loadGlobalTab() {
        viewModelScope.launch {
            _globalUiState.value = _globalUiState.value.copy(isLoading = true, errorMessage = null)
            try {
                if (!userPreferencesRepository.isAccountLinkedFlow.first()) {
                    _globalUiState.value = _globalUiState.value.copy(
                        isLoading = false,
                        errorMessage = "Sign up or log in to view the global leaderboard."
                    )
                    return@launch
                }

                val myNickname = userPreferencesRepository.getSavedNickname()
                val entries = cloudSyncRepository.getGlobalLeaderboard(limit = 50)

                _globalUiState.value = _globalUiState.value.copy(
                    isLoading = false,
                    entries = entries,
                    myNickname = myNickname
                )
            } catch (e: Exception) {
                _globalUiState.value = _globalUiState.value.copy(
                    isLoading = false,
                    errorMessage = friendlyErrorMessage(e, "Failed to load global leaderboard. Please try again.")
                )
            }
        }
    }
}