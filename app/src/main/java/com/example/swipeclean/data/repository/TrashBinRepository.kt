package com.example.swipeclean.data.repository

import android.content.Context
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.swipeclean.data.model.MediaItem
import com.example.swipeclean.data.model.TrashItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.trashDataStore: DataStore<Preferences> by preferencesDataStore(name = "trash_bin")

class TrashBinRepository(private val context: Context) {
    
    private val dataStore = context.trashDataStore
    private val TRASH_ITEMS_KEY = stringPreferencesKey("trash_items_json")
    
    /**
     * Add a media item to trash
     * ✅ NOW stores the actual MediaStore URI from MediaItem
     */
    suspend fun addToTrash(mediaItem: MediaItem) {
        val trashItem = TrashItem(
            mediaId = mediaItem.id,
            displayName = mediaItem.displayName,
            sizeBytes = mediaItem.sizeBytes,
            mimeType = mediaItem.mimeType,
            dateAddedSeconds = mediaItem.dateAddedSeconds,
            uri = mediaItem.uri.toString()  // ✅ Store the actual URI string
        )
        
        dataStore.edit { preferences ->
            val currentList = getTrashItemsSync(preferences).toMutableList()
            
            // Remove if already exists, then add (to avoid duplicates)
            currentList.removeAll { it.mediaId == trashItem.mediaId }
            currentList.add(trashItem)
            
            // Keep only last 100 items
            if (currentList.size > 100) {
                currentList.removeAt(0)
            }
            
            // Auto-purge items older than 30 days
            currentList.removeAll { it.isOlderThan(30) }
            
            preferences[TRASH_ITEMS_KEY] = Json.encodeToString(currentList)
        }
    }
    
    /**
     * Get all trash items as Flow
     */
    fun getAllTrashItems(): Flow<List<TrashItem>> {
        return dataStore.data.map { preferences ->
            getTrashItemsSync(preferences)
        }
    }
    
    /**
     * Get all trash items synchronously (for internal use)
     */
    private fun getTrashItemsSync(preferences: Preferences): List<TrashItem> {
        val json = preferences[TRASH_ITEMS_KEY] ?: return emptyList()
        return try {
            Json.decodeFromString(json)
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * Remove item from trash (restore)
     */
    suspend fun removeFromTrash(mediaId: Long) {
        dataStore.edit { preferences ->
            val currentList = getTrashItemsSync(preferences).toMutableList()
            currentList.removeAll { it.mediaId == mediaId }
            preferences[TRASH_ITEMS_KEY] = Json.encodeToString(currentList)
        }
    }
    
    /**
     * Clear all trash items
     */
    suspend fun clearAllTrash() {
        dataStore.edit { preferences ->
            preferences[TRASH_ITEMS_KEY] = Json.encodeToString(emptyList<TrashItem>())
        }
    }
    
    /**
     * Get total size of all trash items (bytes)
     */
    fun getTotalTrashSize(): Flow<Long> {
        return dataStore.data.map { preferences ->
            getTrashItemsSync(preferences).sumOf { it.sizeBytes }
        }
    }
    
    /**
     * Get count of trash items
     */
    fun getTrashCount(): Flow<Int> {
        return dataStore.data.map { preferences ->
            getTrashItemsSync(preferences).size
        }
    }

    /**
     * ✅ FIXED Phase 3.5: Get URIs for deletion
     * Now uses STORED URIs directly instead of reconstructing
     * This works across all media types and storage volumes
     */
    fun getUrisForDeletion(mediaIds: List<Long>, trashItems: List<TrashItem>): List<Uri> {
        val uris = mutableListOf<Uri>()
        
        mediaIds.forEach { mediaId ->
            // Find the trash item with matching ID
            val trashItem = trashItems.find { it.mediaId == mediaId }
            
            if (trashItem != null && trashItem.uri.isNotEmpty()) {
                // ✅ Use the stored URI directly - NO reconstruction needed!
                uris.add(Uri.parse(trashItem.uri))
            }
        }
        
        return uris
    }
    
    /**
     * Remove deleted items from trash after successful MediaStore deletion
     */
    suspend fun removeDeletedItems(mediaIds: List<Long>) {
        dataStore.edit { preferences ->
            val currentList = getTrashItemsSync(preferences).toMutableList()
            currentList.removeAll { it.mediaId in mediaIds }
            preferences[TRASH_ITEMS_KEY] = Json.encodeToString(currentList)
        }
    }
    
    /**
     * Auto-purge items older than specified days
     */
    suspend fun autoPurgeOldItems(olderThanDays: Int = 30) {
        dataStore.edit { preferences ->
            val currentList = getTrashItemsSync(preferences).toMutableList()
            currentList.removeAll { it.isOlderThan(olderThanDays) }
            preferences[TRASH_ITEMS_KEY] = Json.encodeToString(currentList)
        }
    }
}