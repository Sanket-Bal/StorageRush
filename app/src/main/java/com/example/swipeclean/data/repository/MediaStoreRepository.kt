package com.example.swipeclean.data.repository

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.example.swipeclean.data.model.BucketInfo
import com.example.swipeclean.data.model.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * Repository for querying media items from MediaStore.
 * Handles efficient, non-blocking media fetching using Kotlin Flows.
 */
class MediaStoreRepository(private val context: Context) {

    private val contentResolver: ContentResolver = context.contentResolver

    /**
     * Builds a type-specific MediaStore URI (images/video collection),
     * required for MediaStore.createDeleteRequest() to accept it.
     * A generic Files-collection URI is rejected with IllegalArgumentException.
     */
    private fun buildMediaUri(id: Long, mimeType: String?): Uri {
        val collection = if (mimeType?.startsWith("video/") == true) {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        return ContentUris.withAppendedId(collection, id)
    }

    /**
     * Fetches all screenshots from MediaStore using Flow.
     * Non-blocking, lazy-loaded in batches.
     */
    fun getScreenshots(): Flow<List<MediaItem>> = flow {
        val mediaItems = mutableListOf<MediaItem>()

        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.MIME_TYPE,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.DATE_ADDED,
            MediaStore.Files.FileColumns.DATE_MODIFIED,
            MediaStore.Files.FileColumns.RELATIVE_PATH,
            MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME,
            MediaStore.Files.FileColumns.WIDTH,
            MediaStore.Files.FileColumns.HEIGHT,
            MediaStore.Files.FileColumns.DURATION,
        )

        val selection = buildString {
            append("(")
            append("${MediaStore.Files.FileColumns.MIME_TYPE} LIKE 'image/%'")
            append(" OR ")
            append("${MediaStore.Files.FileColumns.MIME_TYPE} LIKE 'video/%'")
            append(")")
            append(" AND (")
            append("${MediaStore.Files.FileColumns.RELATIVE_PATH} LIKE '%Screenshots%'")
            append(" OR ")
            append("${MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME} LIKE '%Screenshots%'")
            append(")")
        }

        val sortOrder = "${MediaStore.Files.FileColumns.DATE_ADDED} DESC"

        val cursor = contentResolver.query(
            MediaStore.Files.getContentUri("external"),
            projection,
            selection,
            null,
            sortOrder
        )

        cursor?.use { c ->
            val idColumn = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
            val displayNameColumn = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
            val mimeTypeColumn = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)
            val sizeColumn = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
            val dateAddedColumn = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_ADDED)
            val dateModifiedColumn = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)
            val relativePathColumn = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.RELATIVE_PATH)
            val bucketColumn = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME)
            val widthColumn = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.WIDTH)
            val heightColumn = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.HEIGHT)
            val durationColumn = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DURATION)

            while (c.moveToNext()) {
                val id = c.getLong(idColumn)
                val mimeTypeValue = c.getString(mimeTypeColumn)
                val uri = buildMediaUri(id, mimeTypeValue)

                val mediaItem = MediaItem(
                    id = id,
                    uri = uri,
                    displayName = c.getString(displayNameColumn) ?: "Unknown",
                    mimeType = c.getString(mimeTypeColumn) ?: "application/octet-stream",
                    sizeBytes = c.getLong(sizeColumn),
                    dateAddedSeconds = c.getLong(dateAddedColumn),
                    dateModifiedSeconds = c.getLong(dateModifiedColumn),
                    relativePath = c.getString(relativePathColumn) ?: "",
                    bucketDisplayName = c.getString(bucketColumn) ?: "Unknown",
                    width = c.getInt(widthColumn),
                    height = c.getInt(heightColumn),
                    duration = c.getLong(durationColumn)
                )

                mediaItems.add(mediaItem)

                // Emit batches of items for lazy loading (batch size: 20)
                if (mediaItems.size % 20 == 0) {
                    emit(mediaItems.toList())
                    mediaItems.clear()
                }
            }
        }

        // Emit remaining items
        if (mediaItems.isNotEmpty()) {
            emit(mediaItems)
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Fetches large videos from MediaStore (default threshold: 50 MB).
     * Non-blocking, lazy-loaded in batches.
     */
    fun getLargeVideos(thresholdBytes: Long = 50 * 1024 * 1024): Flow<List<MediaItem>> = flow {
        val mediaItems = mutableListOf<MediaItem>()

        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.MIME_TYPE,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.DATE_ADDED,
            MediaStore.Files.FileColumns.DATE_MODIFIED,
            MediaStore.Files.FileColumns.RELATIVE_PATH,
            MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME,
            MediaStore.Files.FileColumns.WIDTH,
            MediaStore.Files.FileColumns.HEIGHT,
            MediaStore.Files.FileColumns.DURATION,
        )

        val selection = "${MediaStore.Files.FileColumns.MIME_TYPE} LIKE 'video/%' AND ${MediaStore.Files.FileColumns.SIZE} > ?"
        val selectionArgs = arrayOf(thresholdBytes.toString())
        val sortOrder = "${MediaStore.Files.FileColumns.SIZE} DESC"

        val cursor = contentResolver.query(
            MediaStore.Files.getContentUri("external"),
            projection,
            selection,
            selectionArgs,
            sortOrder
        )

        cursor?.use { c ->
            val idColumn = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
            val displayNameColumn = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
            val mimeTypeColumn = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)
            val sizeColumn = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
            val dateAddedColumn = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_ADDED)
            val dateModifiedColumn = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)
            val relativePathColumn = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.RELATIVE_PATH)
            val bucketColumn = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME)
            val widthColumn = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.WIDTH)
            val heightColumn = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.HEIGHT)
            val durationColumn = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DURATION)

            while (c.moveToNext()) {
                val id = c.getLong(idColumn)
                val mimeTypeValue = c.getString(mimeTypeColumn)
                val uri = buildMediaUri(id, mimeTypeValue)

                val mediaItem = MediaItem(
                    id = id,
                    uri = uri,
                    displayName = c.getString(displayNameColumn) ?: "Unknown",
                    mimeType = c.getString(mimeTypeColumn) ?: "application/octet-stream",
                    sizeBytes = c.getLong(sizeColumn),
                    dateAddedSeconds = c.getLong(dateAddedColumn),
                    dateModifiedSeconds = c.getLong(dateModifiedColumn),
                    relativePath = c.getString(relativePathColumn) ?: "",
                    bucketDisplayName = c.getString(bucketColumn) ?: "Unknown",
                    width = c.getInt(widthColumn),
                    height = c.getInt(heightColumn),
                    duration = c.getLong(durationColumn)
                )

                mediaItems.add(mediaItem)

                // Emit batches of items for lazy loading (batch size: 15)
                if (mediaItems.size % 15 == 0) {
                    emit(mediaItems.toList())
                    mediaItems.clear()
                }
            }
        }

        // Emit remaining items
        if (mediaItems.isNotEmpty()) {
            emit(mediaItems)
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Fetches "short clips" — videos shorter than the given duration threshold
     * (Phase 4.2, default: 30 seconds). Good candidates for accidental or
     * burst recordings that are easy to review and trash.
     * Excludes items with DURATION <= 0, since a missing/unreadable duration
     * (some OEMs/formats don't populate it) would otherwise incorrectly
     * qualify as "short."
     * Non-blocking, lazy-loaded in batches.
     */
    fun getShortVideos(thresholdMillis: Long = 30_000): Flow<List<MediaItem>> = flow {
        val mediaItems = mutableListOf<MediaItem>()

        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.MIME_TYPE,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.DATE_ADDED,
            MediaStore.Files.FileColumns.DATE_MODIFIED,
            MediaStore.Files.FileColumns.RELATIVE_PATH,
            MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME,
            MediaStore.Files.FileColumns.WIDTH,
            MediaStore.Files.FileColumns.HEIGHT,
            MediaStore.Files.FileColumns.DURATION,
        )

        val selection = "${MediaStore.Files.FileColumns.MIME_TYPE} LIKE 'video/%'" +
            " AND ${MediaStore.Files.FileColumns.DURATION} > 0" +
            " AND ${MediaStore.Files.FileColumns.DURATION} < ?"
        val selectionArgs = arrayOf(thresholdMillis.toString())
        val sortOrder = "${MediaStore.Files.FileColumns.DATE_ADDED} DESC"

        val cursor = contentResolver.query(
            MediaStore.Files.getContentUri("external"),
            projection,
            selection,
            selectionArgs,
            sortOrder
        )

        cursor?.use { c ->
            val idColumn = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
            val displayNameColumn = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
            val mimeTypeColumn = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)
            val sizeColumn = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
            val dateAddedColumn = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_ADDED)
            val dateModifiedColumn = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)
            val relativePathColumn = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.RELATIVE_PATH)
            val bucketColumn = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME)
            val widthColumn = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.WIDTH)
            val heightColumn = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.HEIGHT)
            val durationColumn = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DURATION)

            while (c.moveToNext()) {
                val id = c.getLong(idColumn)
                val mimeTypeValue = c.getString(mimeTypeColumn)
                val uri = buildMediaUri(id, mimeTypeValue)

                val mediaItem = MediaItem(
                    id = id,
                    uri = uri,
                    displayName = c.getString(displayNameColumn) ?: "Unknown",
                    mimeType = c.getString(mimeTypeColumn) ?: "application/octet-stream",
                    sizeBytes = c.getLong(sizeColumn),
                    dateAddedSeconds = c.getLong(dateAddedColumn),
                    dateModifiedSeconds = c.getLong(dateModifiedColumn),
                    relativePath = c.getString(relativePathColumn) ?: "",
                    bucketDisplayName = c.getString(bucketColumn) ?: "Unknown",
                    width = c.getInt(widthColumn),
                    height = c.getInt(heightColumn),
                    duration = c.getLong(durationColumn)
                )

                mediaItems.add(mediaItem)

                if (mediaItems.size % 15 == 0) {
                    emit(mediaItems.toList())
                    mediaItems.clear()
                }
            }
        }

        if (mediaItems.isNotEmpty()) {
            emit(mediaItems)
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Fetches all videos, newest first (Phase 4.2) — the general "All Videos" section.
     * Non-blocking, lazy-loaded in batches.
     */
    fun getAllVideosSorted(): Flow<List<MediaItem>> = flow {
        val mediaItems = mutableListOf<MediaItem>()

        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.MIME_TYPE,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.DATE_ADDED,
            MediaStore.Files.FileColumns.DATE_MODIFIED,
            MediaStore.Files.FileColumns.RELATIVE_PATH,
            MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME,
            MediaStore.Files.FileColumns.WIDTH,
            MediaStore.Files.FileColumns.HEIGHT,
            MediaStore.Files.FileColumns.DURATION,
        )

        val selection = "${MediaStore.Files.FileColumns.MIME_TYPE} LIKE 'video/%'"
        val sortOrder = "${MediaStore.Files.FileColumns.DATE_ADDED} DESC"

        val cursor = contentResolver.query(
            MediaStore.Files.getContentUri("external"),
            projection,
            selection,
            null,
            sortOrder
        )

        cursor?.use { c ->
            val idColumn = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
            val displayNameColumn = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
            val mimeTypeColumn = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)
            val sizeColumn = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
            val dateAddedColumn = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_ADDED)
            val dateModifiedColumn = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)
            val relativePathColumn = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.RELATIVE_PATH)
            val bucketColumn = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME)
            val widthColumn = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.WIDTH)
            val heightColumn = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.HEIGHT)
            val durationColumn = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DURATION)

            while (c.moveToNext()) {
                val id = c.getLong(idColumn)
                val mimeTypeValue = c.getString(mimeTypeColumn)
                val uri = buildMediaUri(id, mimeTypeValue)

                val mediaItem = MediaItem(
                    id = id,
                    uri = uri,
                    displayName = c.getString(displayNameColumn) ?: "Unknown",
                    mimeType = c.getString(mimeTypeColumn) ?: "application/octet-stream",
                    sizeBytes = c.getLong(sizeColumn),
                    dateAddedSeconds = c.getLong(dateAddedColumn),
                    dateModifiedSeconds = c.getLong(dateModifiedColumn),
                    relativePath = c.getString(relativePathColumn) ?: "",
                    bucketDisplayName = c.getString(bucketColumn) ?: "Unknown",
                    width = c.getInt(widthColumn),
                    height = c.getInt(heightColumn),
                    duration = c.getLong(durationColumn)
                )

                mediaItems.add(mediaItem)

                if (mediaItems.size % 15 == 0) {
                    emit(mediaItems.toList())
                    mediaItems.clear()
                }
            }
        }

        if (mediaItems.isNotEmpty()) {
            emit(mediaItems)
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Fetches all photos organized by month/year from MediaStore.
     * Filters out screenshots.
     */
    fun getPhotosByMonth(): Flow<List<MediaItem>> = flow {
        val mediaItems = mutableListOf<MediaItem>()

        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.MIME_TYPE,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.DATE_ADDED,
            MediaStore.Files.FileColumns.DATE_MODIFIED,
            MediaStore.Files.FileColumns.RELATIVE_PATH,
            MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME,
            MediaStore.Files.FileColumns.WIDTH,
            MediaStore.Files.FileColumns.HEIGHT,
        )

        val selection = buildString {
            append("${MediaStore.Files.FileColumns.MIME_TYPE} LIKE 'image/%'")
            append(" AND NOT (")
            append("${MediaStore.Files.FileColumns.RELATIVE_PATH} LIKE '%Screenshots%'")
            append(" OR ")
            append("${MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME} LIKE '%Screenshots%'")
            append(")")
        }

        val sortOrder = "${MediaStore.Files.FileColumns.DATE_ADDED} DESC"

        val cursor = contentResolver.query(
            MediaStore.Files.getContentUri("external"),
            projection,
            selection,
            null,
            sortOrder
        )

        cursor?.use { c ->
            val idColumn = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
            val displayNameColumn = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
            val mimeTypeColumn = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)
            val sizeColumn = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
            val dateAddedColumn = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_ADDED)
            val dateModifiedColumn = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)
            val relativePathColumn = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.RELATIVE_PATH)
            val bucketColumn = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME)
            val widthColumn = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.WIDTH)
            val heightColumn = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.HEIGHT)

            while (c.moveToNext()) {
                val id = c.getLong(idColumn)
                val mimeTypeValue = c.getString(mimeTypeColumn)
                val uri = buildMediaUri(id, mimeTypeValue)

                val mediaItem = MediaItem(
                    id = id,
                    uri = uri,
                    displayName = c.getString(displayNameColumn) ?: "Unknown",
                    mimeType = c.getString(mimeTypeColumn) ?: "application/octet-stream",
                    sizeBytes = c.getLong(sizeColumn),
                    dateAddedSeconds = c.getLong(dateAddedColumn),
                    dateModifiedSeconds = c.getLong(dateModifiedColumn),
                    relativePath = c.getString(relativePathColumn) ?: "",
                    bucketDisplayName = c.getString(bucketColumn) ?: "Unknown",
                    width = c.getInt(widthColumn),
                    height = c.getInt(heightColumn),
                )

                mediaItems.add(mediaItem)

                // Emit batches of items for lazy loading (batch size: 25)
                if (mediaItems.size % 25 == 0) {
                    emit(mediaItems.toList())
                    mediaItems.clear()
                }
            }
        }

        // Emit remaining items
        if (mediaItems.isNotEmpty()) {
            emit(mediaItems)
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Fetches media items by a specific bucket/folder name.
     * Useful for custom cleanup decks.
     */
    fun getMediaByBucket(bucketName: String): Flow<List<MediaItem>> = flow {
        val mediaItems = mutableListOf<MediaItem>()

        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.MIME_TYPE,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.DATE_ADDED,
            MediaStore.Files.FileColumns.DATE_MODIFIED,
            MediaStore.Files.FileColumns.RELATIVE_PATH,
            MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME,
            MediaStore.Files.FileColumns.WIDTH,
            MediaStore.Files.FileColumns.HEIGHT,
            MediaStore.Files.FileColumns.DURATION,
        )

        val selection = "${MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME} = ?"
        val selectionArgs = arrayOf(bucketName)
        val sortOrder = "${MediaStore.Files.FileColumns.DATE_ADDED} DESC"

        val cursor = contentResolver.query(
            MediaStore.Files.getContentUri("external"),
            projection,
            selection,
            selectionArgs,
            sortOrder
        )

        cursor?.use { c ->
            val idColumn = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
            val displayNameColumn = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
            val mimeTypeColumn = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)
            val sizeColumn = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
            val dateAddedColumn = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_ADDED)
            val dateModifiedColumn = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)
            val relativePathColumn = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.RELATIVE_PATH)
            val bucketColumn = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME)
            val widthColumn = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.WIDTH)
            val heightColumn = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.HEIGHT)
            val durationColumn = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DURATION)

            while (c.moveToNext()) {
                val id = c.getLong(idColumn)
                val mimeTypeValue = c.getString(mimeTypeColumn)
                val uri = buildMediaUri(id, mimeTypeValue)

                val mediaItem = MediaItem(
                    id = id,
                    uri = uri,
                    displayName = c.getString(displayNameColumn) ?: "Unknown",
                    mimeType = c.getString(mimeTypeColumn) ?: "application/octet-stream",
                    sizeBytes = c.getLong(sizeColumn),
                    dateAddedSeconds = c.getLong(dateAddedColumn),
                    dateModifiedSeconds = c.getLong(dateModifiedColumn),
                    relativePath = c.getString(relativePathColumn) ?: "",
                    bucketDisplayName = c.getString(bucketColumn) ?: "Unknown",
                    width = c.getInt(widthColumn),
                    height = c.getInt(heightColumn),
                    duration = c.getLong(durationColumn)
                )

                mediaItems.add(mediaItem)

                if (mediaItems.size % 20 == 0) {
                    emit(mediaItems.toList())
                    mediaItems.clear()
                }
            }
        }

        if (mediaItems.isNotEmpty()) {
            emit(mediaItems)
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Junk/system folder patterns to hide from the user-facing bucket list.
     * Some OEMs surface app-cache-style folders here even though a .nomedia
     * file is supposed to prevent that.
     */
    private val junkBucketPatterns = listOf(
        "thumbnails", ".trashed", ".temp", "cache"
    )

    private fun isJunkBucket(bucketName: String): Boolean {
        val lower = bucketName.lowercase()
        if (lower.startsWith(".")) return true
        return junkBucketPatterns.any { lower.contains(it) }
    }

    /**
     * Discovers every real, user-facing image folder on the device (Phase 4.1).
     * Groups by BUCKET_ID — not by display name — so two folders that happen to
     * share a name (e.g. "Camera" on internal storage vs an SD card) are kept
     * as separate entries rather than merged.
     *
     * One-shot suspend call rather than a Flow: the caller needs the complete,
     * sorted list to render a picker screen, not an incremental stream.
     */
    suspend fun getAllImageBuckets(): List<BucketInfo> = withContext(Dispatchers.IO) {
        data class Accumulator(
            val bucketName: String,
            var itemCount: Int = 0,
            var thumbnailUri: Uri? = null,
            var representativePath: String = ""
        )

        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.BUCKET_ID,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Images.Media.RELATIVE_PATH,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.MIME_TYPE,
        )

        // Newest first, so the first row seen per bucket is the best thumbnail candidate.
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        val buckets = LinkedHashMap<Long, Accumulator>()

        val cursor = contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            sortOrder
        )

        cursor?.use { c ->
            val idColumn = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val bucketIdColumn = c.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_ID)
            val bucketNameColumn = c.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
            val pathColumn = c.getColumnIndexOrThrow(MediaStore.Images.Media.RELATIVE_PATH)
            val mimeTypeColumn = c.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)

            while (c.moveToNext()) {
                val bucketName = c.getString(bucketNameColumn) ?: continue
                if (isJunkBucket(bucketName)) continue
                val bucketId = c.getLong(bucketIdColumn)

                val acc = buckets.getOrPut(bucketId) { Accumulator(bucketName = bucketName) }
                acc.itemCount += 1

                // First row per bucket (newest, due to sort order) becomes the thumbnail.
                if (acc.thumbnailUri == null) {
                    val id = c.getLong(idColumn)
                    val mimeTypeValue = c.getString(mimeTypeColumn)
                    acc.thumbnailUri = buildMediaUri(id, mimeTypeValue)
                    acc.representativePath = c.getString(pathColumn) ?: ""
                }
            }
        }

        // Disambiguate buckets that share the same display name but are
        // different folders (different bucketId) — e.g. two "Camera" folders
        // on internal vs external storage.
        val nameCounts = buckets.values.groupingBy { it.bucketName }.eachCount()

        buckets.map { (bucketId, acc) ->
            val displayName = if ((nameCounts[acc.bucketName] ?: 0) > 1) {
                val volumeHint = acc.representativePath.substringBefore("/").ifBlank { "Other" }
                "${acc.bucketName} ($volumeHint)"
            } else {
                acc.bucketName
            }
            BucketInfo(
                bucketId = bucketId,
                bucketName = displayName,
                itemCount = acc.itemCount,
                thumbnailUri = acc.thumbnailUri,
                representativePath = acc.representativePath
            )
        }.sortedByDescending { it.itemCount }
    }

    /**
     * Picks the best default bucket for first launch / no saved preference.
     * Fallback chain: real Camera folder -> any DCIM folder -> most populated
     * bucket -> none (caller shows the empty state).
     */
    fun resolveDefaultBucket(buckets: List<BucketInfo>): BucketInfo? {
        if (buckets.isEmpty()) return null

        buckets.firstOrNull {
            it.representativePath.contains("DCIM/Camera", ignoreCase = true)
        }?.let { return it }

        buckets.firstOrNull {
            it.representativePath.contains("DCIM/", ignoreCase = true)
        }?.let { return it }

        return buckets.maxByOrNull { it.itemCount }
    }

    /**
     * Fetches media items by bucket ID (Phase 4.1) — unlike [getMediaByBucket],
     * this is accurate even when two folders share the same display name,
     * since BUCKET_ID (not the name) is the real unique key in MediaStore.
     */
    fun getMediaByBucketId(bucketId: Long): Flow<List<MediaItem>> = flow {
        val mediaItems = mutableListOf<MediaItem>()

        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.MIME_TYPE,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.DATE_ADDED,
            MediaStore.Files.FileColumns.DATE_MODIFIED,
            MediaStore.Files.FileColumns.RELATIVE_PATH,
            MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME,
            MediaStore.Files.FileColumns.WIDTH,
            MediaStore.Files.FileColumns.HEIGHT,
            MediaStore.Files.FileColumns.DURATION,
        )

        val selection = "${MediaStore.Files.FileColumns.BUCKET_ID} = ?"
        val selectionArgs = arrayOf(bucketId.toString())
        val sortOrder = "${MediaStore.Files.FileColumns.DATE_ADDED} DESC"

        val cursor = contentResolver.query(
            MediaStore.Files.getContentUri("external"),
            projection,
            selection,
            selectionArgs,
            sortOrder
        )

        cursor?.use { c ->
            val idColumn = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
            val displayNameColumn = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
            val mimeTypeColumn = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)
            val sizeColumn = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
            val dateAddedColumn = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_ADDED)
            val dateModifiedColumn = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)
            val relativePathColumn = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.RELATIVE_PATH)
            val bucketColumn = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME)
            val widthColumn = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.WIDTH)
            val heightColumn = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.HEIGHT)
            val durationColumn = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DURATION)

            while (c.moveToNext()) {
                val id = c.getLong(idColumn)
                val mimeTypeValue = c.getString(mimeTypeColumn)
                val uri = buildMediaUri(id, mimeTypeValue)

                val mediaItem = MediaItem(
                    id = id,
                    uri = uri,
                    displayName = c.getString(displayNameColumn) ?: "Unknown",
                    mimeType = c.getString(mimeTypeColumn) ?: "application/octet-stream",
                    sizeBytes = c.getLong(sizeColumn),
                    dateAddedSeconds = c.getLong(dateAddedColumn),
                    dateModifiedSeconds = c.getLong(dateModifiedColumn),
                    relativePath = c.getString(relativePathColumn) ?: "",
                    bucketDisplayName = c.getString(bucketColumn) ?: "Unknown",
                    width = c.getInt(widthColumn),
                    height = c.getInt(heightColumn),
                    duration = c.getLong(durationColumn)
                )

                mediaItems.add(mediaItem)

                if (mediaItems.size % 20 == 0) {
                    emit(mediaItems.toList())
                    mediaItems.clear()
                }
            }
        }

        if (mediaItems.isNotEmpty()) {
            emit(mediaItems)
        }
    }.flowOn(Dispatchers.IO)
}