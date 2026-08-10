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
import kotlinx.coroutines.flow.first
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
}