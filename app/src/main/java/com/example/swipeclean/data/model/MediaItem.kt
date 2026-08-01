package com.example.swipeclean.data.model

import android.net.Uri
import java.time.Instant

/**
 * Represents a single media item (photo/video) from MediaStore.
 * This is the core domain model for SwipeClean.
 */
data class MediaItem(
    val id: Long,                    // MediaStore ID (unique identifier)
    val uri: Uri,                    // ContentUri to access the media file
    val displayName: String,         // File name (e.g., "IMG_1234.jpg")
    val mimeType: String,            // MIME type (e.g., "image/jpeg", "video/mp4")
    val sizeBytes: Long,             // File size in bytes
    val dateAddedSeconds: Long,      // Date added to device (seconds since epoch)
    val dateModifiedSeconds: Long,   // Date last modified (seconds since epoch)
    val relativePath: String,        // Relative path (e.g., "DCIM/Screenshots")
    val bucketDisplayName: String,   // Bucket name (e.g., "Screenshots", "Camera")
    val isDraft: Boolean = false,    // For future use: unpublished media
    val width: Int = 0,              // Image/video width in pixels
    val height: Int = 0,             // Image/video height in pixels
    val duration: Long = 0,          // For videos: duration in milliseconds
) {
    /**
     * Returns a human-readable file size (e.g., "2.5 MB", "145 KB")
     */
    fun getReadableSize(): String {
        return when {
            sizeBytes < 1024 -> "$sizeBytes B"
            sizeBytes < 1024 * 1024 -> "${sizeBytes / 1024} KB"
            sizeBytes < 1024 * 1024 * 1024 -> String.format("%.1f MB", sizeBytes / (1024.0 * 1024.0))
            else -> String.format("%.1f GB", sizeBytes / (1024.0 * 1024.0 * 1024.0))
        }
    }

    /**
     * Returns the media type category for UI purposes
     */
    fun getMediaTypeCategory(): MediaTypeCategory {
        return when {
            mimeType.startsWith("image/") -> MediaTypeCategory.IMAGE
            mimeType.startsWith("video/") -> MediaTypeCategory.VIDEO
            else -> MediaTypeCategory.UNKNOWN
        }
    }

    /**
     * Returns a human-readable date string
     */
    fun getReadableDate(): String {
        val instant = Instant.ofEpochSecond(dateAddedSeconds)
        val dateTime = java.time.LocalDateTime.ofInstant(
            instant,
            java.time.ZoneId.systemDefault()
        )
        return dateTime.format(
            java.time.format.DateTimeFormatter.ofPattern("MMM dd, yyyy")
        )
    }

    /**
     * Returns a short date string for monthly grouping (e.g., "March 2024")
     */
    fun getMonthYear(): String {
        val instant = Instant.ofEpochSecond(dateAddedSeconds)
        val dateTime = java.time.LocalDateTime.ofInstant(
            instant,
            java.time.ZoneId.systemDefault()
        )
        return dateTime.format(
            java.time.format.DateTimeFormatter.ofPattern("MMMM yyyy")
        )
    }

    /**
     * Checks if this is likely a screenshot based on bucket/path
     */
    fun isScreenshot(): Boolean {
        return bucketDisplayName.contains("screenshot", ignoreCase = true) ||
                relativePath.contains("screenshot", ignoreCase = true)
    }

    /**
     * Checks if this is a large video (default threshold: 50 MB)
     */
    fun isLargeVideo(thresholdBytes: Long = 50 * 1024 * 1024): Boolean {
        return getMediaTypeCategory() == MediaTypeCategory.VIDEO && sizeBytes > thresholdBytes
    }
}

enum class MediaTypeCategory {
    IMAGE, VIDEO, UNKNOWN
}