package com.storagerush.app.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.storagerush.app.ui.deck.DeckType
import com.storagerush.app.ui.deck.VideoFilterType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.userPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

/**
 * Storage-layer mirror of [DeckType] (Phase 4.4). DeckType itself lives in
 * the ui layer and isn't @Serializable, so this is the persisted shape —
 * one flat record covering every DeckType variant via a "kind" discriminator,
 * same idea as how TrashItem mirrors MediaItem for storage.
 */
@Serializable
data class LastDeckSelection(
    val kind: String, // "SCREENSHOTS" | "MONTHLY_PHOTOS" | "BUCKET" | "VIDEO_FILTER"
    val bucketId: Long? = null,
    val bucketName: String? = null,
    val videoFilterType: String? = null // matches VideoFilterType.name
)

class UserPreferencesRepository(private val context: Context) {

    private val dataStore = context.userPreferencesDataStore
    private val LAST_DECK_SELECTION_KEY = stringPreferencesKey("last_deck_selection_json")
    private val HAS_SEEN_TUTORIAL_KEY = booleanPreferencesKey("has_seen_tutorial")

    // Phase A: cloud sync / nickname setup tracking
    private val HAS_COMPLETED_CLOUD_SETUP_KEY = booleanPreferencesKey("has_completed_cloud_setup")
    private val SAVED_NICKNAME_KEY = stringPreferencesKey("saved_nickname")

    // Account linking (email OTP): separate from cloud setup above, since
    // this is optional and dismissible — a player can have completed
    // cloud setup (anonymous + nickname) without ever linking an account.
    private val HAS_LINKED_ACCOUNT_KEY = booleanPreferencesKey("has_linked_account")
    private val LINKED_EMAIL_KEY = stringPreferencesKey("linked_email")

    /**
     * Whether the user has completed the mandatory first-launch tutorial.
     * Used both to gate the onboarding overlay on Deck, and to decide
     * whether TutorialGuideScreen opens in mandatory or dismissible mode.
     */
    suspend fun hasSeenTutorial(): Boolean {
        val preferences = dataStore.data.first()
        return preferences[HAS_SEEN_TUTORIAL_KEY] ?: false
    }

    /**
     * Marks the tutorial as seen. Called when the user finishes the
     * mandatory first-launch flow — never needs to be un-set (no "reset
     * tutorial" flow requested), so no corresponding clear function.
     */
    suspend fun markTutorialSeen() {
        dataStore.edit { preferences ->
            preferences[HAS_SEEN_TUTORIAL_KEY] = true
        }
    }

    /**
     * Persist the user's most recently viewed deck section.
     * Called every time a deck loads (see DeckViewModel.loadDeck()), so any
     * future screen that triggers a deck load gets "remember last section"
     * automatically, without needing its own save call.
     */
    suspend fun saveLastDeckSelection(deckType: DeckType) {
        val selection = when (deckType) {
            is DeckType.Screenshots -> LastDeckSelection(kind = "SCREENSHOTS")
            is DeckType.MonthlyPhotos -> LastDeckSelection(kind = "MONTHLY_PHOTOS")
            is DeckType.Bucket -> LastDeckSelection(
                kind = "BUCKET",
                bucketId = deckType.bucketId,
                bucketName = deckType.bucketName
            )
            is DeckType.VideoFilter -> LastDeckSelection(
                kind = "VIDEO_FILTER",
                videoFilterType = deckType.filter.name
            )
        }
        dataStore.edit { preferences ->
            preferences[LAST_DECK_SELECTION_KEY] = Json.encodeToString(selection)
        }
    }

    /**
     * Reads the last saved deck selection as a [DeckType], or null if:
     * - nothing has been saved yet (first launch), or
     * - the saved JSON is corrupt/unreadable, or
     * - it was a Bucket/VideoFilter selection missing required fields.
     *
     * Note: this does NOT verify the saved bucket still exists on the device
     * (e.g. the folder could have been deleted since last launch) — that
     * validity check belongs to Phase 4.6's fallback wiring, which has
     * access to the live bucket list to check against.
     */
    suspend fun getLastDeckType(): DeckType? {
        val preferences = dataStore.data.first()
        val json = preferences[LAST_DECK_SELECTION_KEY] ?: return null

        val selection = try {
            Json.decodeFromString<LastDeckSelection>(json)
        } catch (e: Exception) {
            return null
        }

        return when (selection.kind) {
            "SCREENSHOTS" -> DeckType.Screenshots
            "MONTHLY_PHOTOS" -> DeckType.MonthlyPhotos
            "BUCKET" -> {
                val bucketId = selection.bucketId
                val bucketName = selection.bucketName
                if (bucketId != null && bucketName != null) {
                    DeckType.Bucket(bucketId = bucketId, bucketName = bucketName)
                } else {
                    null
                }
            }
            "VIDEO_FILTER" -> {
                selection.videoFilterType
                    ?.let { runCatching { VideoFilterType.valueOf(it) }.getOrNull() }
                    ?.let { DeckType.VideoFilter(it) }
            }
            else -> null
        }
    }

    /**
     * Clears the saved selection. Phase 4.6 will call this if the saved
     * bucket is found to no longer exist, so the app doesn't keep trying
     * to reopen a deleted folder on every launch.
     */
    suspend fun clearLastDeckSelection() {
        dataStore.edit { preferences ->
            preferences.remove(LAST_DECK_SELECTION_KEY)
        }
    }

    // ---------------------------------------------------------------
    // Phase A: Cloud sync / nickname setup
    // ---------------------------------------------------------------

    /**
     * Whether the user has completed the one-time nickname dialog and
     * cloud player record creation. Used to gate showing the nickname
     * dialog again on subsequent launches, and to gate whether
     * TrashBinViewModel attempts to push progress to Supabase at all —
     * so nothing syncs to the cloud until the user has explicitly set
     * up their leaderboard identity.
     */
    suspend fun hasCompletedCloudSetup(): Boolean {
        val preferences = dataStore.data.first()
        return preferences[HAS_COMPLETED_CLOUD_SETUP_KEY] ?: false
    }

    /**
     * Marks cloud setup as complete and caches the chosen nickname
     * locally (so it can be displayed instantly in UI without a network
     * round-trip). Call this right after CloudSyncRepository successfully
     * creates the player record.
     */
    suspend fun markCloudSetupComplete(nickname: String) {
        dataStore.edit { preferences ->
            preferences[HAS_COMPLETED_CLOUD_SETUP_KEY] = true
            preferences[SAVED_NICKNAME_KEY] = nickname
        }
    }

    /** Returns the locally cached nickname, or null if cloud setup hasn't happened yet. */
    suspend fun getSavedNickname(): String? {
        val preferences = dataStore.data.first()
        return preferences[SAVED_NICKNAME_KEY]
    }

    /** Reactive locally cached nickname — used by the Profile tab to display and edit it live. */
    val savedNicknameFlow: Flow<String?> = dataStore.data.map { preferences ->
        preferences[SAVED_NICKNAME_KEY]
    }

    /** Reactive cloud-setup status — used by the Profile tab to know whether nickname editing applies. */
    val hasCompletedCloudSetupFlow: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[HAS_COMPLETED_CLOUD_SETUP_KEY] ?: false
    }

    /**
     * Updates just the cached nickname. Distinct from markCloudSetupComplete()
     * above — this is for editing an *existing* profile's nickname (Profile
     * tab), so it deliberately leaves has_completed_cloud_setup untouched
     * rather than re-marking first-time setup.
     */
    suspend fun updateSavedNickname(nickname: String) {
        dataStore.edit { preferences ->
            preferences[SAVED_NICKNAME_KEY] = nickname
        }
    }

    // ---------------------------------------------------------------
    // Account linking (email OTP Sign Up / Log In)
    // ---------------------------------------------------------------

    /**
     * Whether this player has linked a real email (via Sign Up) or logged
     * into an existing linked account (via Log In). Gates whether the
     * Friends/Global leaderboard tabs prompt for account linking before
     * granting access — local-only stats and the Progress/Achievements
     * tabs never require this.
     */
    suspend fun hasLinkedAccount(): Boolean {
        val preferences = dataStore.data.first()
        return preferences[HAS_LINKED_ACCOUNT_KEY] ?: false
    }

    /** Marks the account as linked and caches the email for display purposes. */
    suspend fun markAccountLinked(email: String) {
        dataStore.edit { preferences ->
            preferences[HAS_LINKED_ACCOUNT_KEY] = true
            preferences[LINKED_EMAIL_KEY] = email
        }
    }

    /** Returns the linked email, or null if no account has been linked. */
    suspend fun getLinkedEmail(): String? {
        val preferences = dataStore.data.first()
        return preferences[LINKED_EMAIL_KEY]
    }

    /**
     * Reactive "is linked" state, so the hamburger menu can flip live
     * between "Sign Up / Log In" and "Log Out" without needing a manual
     * re-read after every account-link/logout action.
     */
    val isAccountLinkedFlow: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[HAS_LINKED_ACCOUNT_KEY] ?: false
    }

    /** Reactive linked email, for showing "Logged in as x@y.com" in the menu. */
    val linkedEmailFlow: Flow<String?> = dataStore.data.map { preferences ->
        preferences[LINKED_EMAIL_KEY]
    }

    /**
     * Reverses markAccountLinked() on logout. Also resets
     * has_completed_cloud_setup — that flag is what gates
     * syncProgressToCloud(), so after logout syncing correctly no-ops
     * instead of failing against a dead session — and saved_nickname,
     * since that nickname belonged to the account being unlinked. Local
     * progress numbers (level/XP/streak/stats) are reset separately by
     * PlayerRepository.resetToNewPlayer() / StatsRepository.clearAllStats(),
     * called alongside this from the same logout flow.
     */
    suspend fun clearAccountLink() {
        dataStore.edit { preferences ->
            preferences[HAS_LINKED_ACCOUNT_KEY] = false
            preferences.remove(LINKED_EMAIL_KEY)
            preferences[HAS_COMPLETED_CLOUD_SETUP_KEY] = false
            preferences.remove(SAVED_NICKNAME_KEY)
        }
    }
}