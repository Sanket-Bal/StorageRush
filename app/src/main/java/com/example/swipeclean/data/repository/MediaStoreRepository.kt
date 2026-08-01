package com.example.swipeclean.data.repository

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.example.swipeclean.data.model.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlin.math.roundToInt

/**
 * Repository for querying media items from MediaStore.
 * Handles efficient, non-blocking media fetching using Kotlin Flows.
 */
class MediaStoreRepository(private val context: Context) {

    private val contentResolver: ContentResolver = context.contentResolver

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
                val uri = Uri.withAppendedPath(
                    MediaStore.Files.getContentUri("external"),
                    id.toString()
                )

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
                val uri = Uri.withAppendedPath(
                    MediaStore.Files.getContentUri("external"),
                    id.toString()
                )

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
                val uri = Uri.withAppendedPath(
                    MediaStore.Files.getContentUri("external"),
                    id.toString()
                )

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
                val uri = Uri.withAppendedPath(
                    MediaStore.Files.getContentUri("external"),
                    id.toString()
                )

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