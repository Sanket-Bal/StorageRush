package com.example.swipeclean.ui.permission

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.compose.foundation.shape.RoundedCornerShape

/**
 * Builds the correct permission list to request for the running OS version.
 * - API 34+ : IMAGES + VIDEO + VISUAL_USER_SELECTED (lets the user pick "Select photos")
 * - API 33  : IMAGES + VIDEO
 * - API 24-32 : READ_EXTERNAL_STORAGE
 * POST_NOTIFICATIONS is added on 33+ regardless, since it's an independent runtime permission there.
 */
private fun buildPermissionsToRequest(): List<String> {
    val permissions = mutableListOf<String>()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
        permissions.add(Manifest.permission.READ_MEDIA_VIDEO)
        if (Build.VERSION.SDK_INT >= 34) {
            permissions.add(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
        }
        permissions.add(Manifest.permission.POST_NOTIFICATIONS)
    } else {
        permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
    return permissions
}

/**
 * Whether the app currently has *usable* media access — full or partial —
 * checked fresh from the OS rather than trusting the raw request-result map,
 * since Android 14's partial access grants a permission that was never
 * explicitly requested.
 */
private fun hasUsableMediaAccess(context: Context): Boolean {
    fun granted(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    return when {
        Build.VERSION.SDK_INT >= 34 -> {
            (granted(Manifest.permission.READ_MEDIA_IMAGES) && granted(Manifest.permission.READ_MEDIA_VIDEO)) ||
                granted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
        }
        Build.VERSION.SDK_INT >= 33 -> {
            granted(Manifest.permission.READ_MEDIA_IMAGES) && granted(Manifest.permission.READ_MEDIA_VIDEO)
        }
        else -> granted(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
}

@Composable
fun PermissionDialog(
    onPermissionsGranted: () -> Unit
) {
    val scrollState = rememberScrollState()
    val context = LocalContext.current

    val multiplePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        // Re-check actual granted state rather than trusting the raw result map:
        // on Android 14+, choosing "Select photos" grants READ_MEDIA_VISUAL_USER_SELECTED
        // even though it wasn't the permission literally requested.
        if (hasUsableMediaAccess(context)) {
            onPermissionsGranted()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .background(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Text(
                text = "Welcome to SwipeClean",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            Text(
                text = "We need a few permissions to help you clean up your phone",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            // Scrollable permission items
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                PermissionItem(
                    emoji = "🖼️",
                    title = "Photos",
                    description = "Access your photos to help you review and clean up unwanted images"
                )

                PermissionItem(
                    emoji = "🎬",
                    title = "Videos",
                    description = "Access your videos to identify and manage large video files"
                )

                PermissionItem(
                    emoji = "🔔",
                    title = "Notifications",
                    description = "Send you updates when cleanup is complete or when storage is freed"
                )
            }

            // Allow All button
            Button(
                onClick = {
                    multiplePermissionLauncher.launch(buildPermissionsToRequest().toTypedArray())
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text(
                    text = "Allow All",
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.labelLarge
                )
            }

            // Skip option
            Button(
                onClick = {
                    onPermissionsGranted()
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) {
                Text(
                    text = "Later",
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@Composable
private fun PermissionItem(
    emoji: String,
    title: String,
    description: String,
    modifier: Modifier = Modifier
) {
    androidx.compose.foundation.layout.Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(12.dp)
            )
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = emoji,
            fontSize = 28.sp,
            modifier = Modifier.padding(top = 4.dp)
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}