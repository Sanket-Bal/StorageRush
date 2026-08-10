package com.storagerush.app.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Helper for managing media permissions in a graceful, user-friendly way.
 * Handles both Android 13+ granular permissions and fallback for older devices.
 */
object PermissionHelper {

    /**
     * Media permissions required for Storage Rush.
     * Dynamically switches based on Android version.
     */
    fun getRequiredPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ (API 33): Granular media permissions
            arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
            )
        } else {
            // Pre-Android 13: Broader read permission
            arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
            )
        }
    }

    /**
     * Checks if all required media permissions are granted.
     */
    fun hasAllPermissions(context: Context): Boolean {
        return getRequiredPermissions().all { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Checks if the user has granted READ_MEDIA_IMAGES permission.
     */
    fun hasImagePermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_MEDIA_IMAGES
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Checks if the user has granted READ_MEDIA_VIDEO permission.
     */
    fun hasVideoPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_MEDIA_VIDEO
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Checks if partial media access was granted (e.g., images but not videos, or vice versa).
     * Only relevant for Android 13+.
     */
    fun hasPartialAccess(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return false
        }

        val hasImage = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_MEDIA_IMAGES
        ) == PackageManager.PERMISSION_GRANTED

        val hasVideo = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_MEDIA_VIDEO
        ) == PackageManager.PERMISSION_GRANTED

        // Partial access means one but not the other
        return (hasImage && !hasVideo) || (!hasImage && hasVideo)
    }

    /**
     * Determines which permission is missing for a more targeted user message.
     */
    fun getMissingPermissions(context: Context): List<String> {
        return getRequiredPermissions().filter { permission ->
            ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * User-friendly description of the permission state for UI feedback.
     */
    fun getPermissionStatusMessage(context: Context): String {
        return when {
            hasAllPermissions(context) -> "Full access granted"
            hasPartialAccess(context) -> "Partial access granted (missing some media types)"
            else -> "Media permissions required"
        }
    }

    /**
     * Returns a user-friendly message guiding them to grant full access in settings.
     */
    fun getPartialAccessGuidanceMessage(): String {
        return "To access all your photos and videos, please grant full media permissions in Settings."
    }
}