package com.storagerush.app.ui.trash

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.storagerush.app.data.model.MediaItem
import com.storagerush.app.data.model.MediaTypeCategory
import com.storagerush.app.data.model.TrashItem
import com.storagerush.app.ui.components.PhotoViewer
import com.storagerush.app.ui.deck.VideoOverlay
import com.storagerush.app.viewmodel.TrashBinViewModel
import androidx.activity.compose.BackHandler
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext

@Composable
fun TrashBinScreen(
    viewModel: TrashBinViewModel,
    onNavigateBack: () -> Unit = {},
    onDeleteTriggered: (List<android.net.Uri>) -> Unit = {}
) {
    val state = viewModel.trashBinState.collectAsState().value
    val context = LocalContext.current  // ✅ CAPTURE HERE AT COMPOSABLE LEVEL

    // Which item (if any) is currently being previewed full-screen before
    // the user decides whether to keep it trashed or restore it. Pure
    // UI-local state — viewing doesn't touch TrashBinViewModel at all.
    var previewItem by remember { mutableStateOf<TrashItem?>(null) }

    // Handle device back button
    BackHandler {
        onNavigateBack()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        if (state.isLoading) {
            LoadingState()
        } else if (state.trashItems.isEmpty()) {
            EmptyTrashState(onBack = onNavigateBack)
        } else {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Header with stats
                TrashBinHeader(
                    itemCount = state.totalCount,
                    totalSize = state.getTotalSizeReadable(),
                    onBack = onNavigateBack,
                    onSelectAll = { viewModel.selectAll() }
                )

                // Trash items list
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(16.dp)
                ) {
                    items(state.trashItems) { item ->
                        TrashItemRow(
                            item = item,
                            isSelected = item.mediaId in state.selectedForDeletion,
                            onToggleSelect = {
                                viewModel.toggleItemSelection(item.mediaId)
                            },
                            onRestore = {
                                viewModel.restoreItem(item)
                            },
                            onPreview = {
                                previewItem = item
                            }
                        )
                    }
                }

                // Bottom action bar (show when items selected)
                if (state.selectedForDeletion.isNotEmpty()) {
                    TrashBinActionBar(
                        selectedCount = state.getSelectedCount(),
                        selectedSize = state.getSelectedSizeReadable(),
                        onDelete = {
                            viewModel.showDeleteConfirmation()
                        },
                        onCancel = {
                            viewModel.deselectAll()
                        }
                    )
                }
            }

            // Delete confirmation dialog
            if (state.showDeleteConfirmation) {
                DeleteConfirmationDialog(
                    itemCount = state.getSelectedCount(),
                    sizeToFree = state.getSelectedSizeReadable(),
                   onConfirm = {
    val uris = viewModel.getAllUrisForDeletion()
    
    // Use captured context variable
    android.widget.Toast.makeText(
        context,
        "Deleting... URIs count: ${uris.size}",
        android.widget.Toast.LENGTH_SHORT
    ).show()
    
    if (uris.isNotEmpty()) {
        onDeleteTriggered(uris)
        viewModel.hideDeleteConfirmation()
    } else {
        android.widget.Toast.makeText(
            context,
            "ERROR: No URIs found!",
            android.widget.Toast.LENGTH_LONG
        ).show()
    }
},
                    onCancel = {
                        viewModel.hideDeleteConfirmation()
                    }
                )
            }

            // Error message
            if (state.errorMessage != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.errorContainer)
                        .padding(16.dp)
                        .navigationBarsPadding()
                ) {
                    Text(
                        text = state.errorMessage,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        // Full-screen preview overlay — drawn last so it's always on top
        // of the list, action bar, and delete dialog. VideoOverlay.kt
        // itself is completely untouched: this just builds the MediaItem
        // it expects from the TrashItem fields that already exist.
        previewItem?.let { item ->
            if (item.getMediaTypeCategory() == MediaTypeCategory.VIDEO) {
                VideoOverlay(
                    mediaItem = item.toPreviewMediaItem(),
                    onDismiss = { previewItem = null }
                )
            } else {
                PhotoViewer(
                    imageUri = Uri.parse(item.uri),
                    contentDescription = item.displayName,
                    onDismiss = { previewItem = null }
                )
            }
        }
    }
}

/**
 * Builds a MediaItem out of a TrashItem's existing fields so VideoOverlay
 * (built for the Deck, where MediaItem is the native model) can be reused
 * as-is here without modification. VideoOverlay only ever reads
 * mediaItem.id, .uri, and .duration — the fields below with placeholder
 * values (dateModifiedSeconds, relativePath, bucketDisplayName) aren't
 * tracked for trashed items and are never read by VideoOverlay, so they're
 * safe to leave blank. duration = 0 is likewise safe: VideoOverlay already
 * tolerates this for real videos too, before ExoPlayer reports the real
 * duration via its own polling loop (~300ms) — this isn't a new code path,
 * just relying on one that already exists.
 */
private fun TrashItem.toPreviewMediaItem(): MediaItem {
    return MediaItem(
        id = mediaId,
        uri = Uri.parse(uri),
        displayName = displayName,
        mimeType = mimeType,
        sizeBytes = sizeBytes,
        dateAddedSeconds = dateAddedSeconds,
        dateModifiedSeconds = dateAddedSeconds,
        relativePath = "",
        bucketDisplayName = "",
        duration = 0L
    )
}

@Composable
private fun TrashBinHeader(
    itemCount: Int,
    totalSize: String,
    onBack: () -> Unit,
    onSelectAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Back button
        Text(
            text = "← Back",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clickable { onBack() }
        )

        // Title centered
        Text(
            text = "Trash Bin",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        // Stats
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "$itemCount item(s) in trash",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Total: $totalSize",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        // Select All Button
        Button(
            onClick = onSelectAll,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors()
        ) {
            Text("Select All")
        }
    }
}

// Shared compact padding for the row's two action buttons (View, Restore)
// so both stay small and consistent, leaving more horizontal room for the
// thumbnail and filename — this is what keeps the taller, thumbnail-
// carrying row from feeling cramped despite fitting more into it.
private val TrashRowActionButtonPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)

@Composable
private fun TrashItemRow(
    item: TrashItem,
    isSelected: Boolean,
    onToggleSelect: () -> Unit,
    onRestore: () -> Unit,
    onPreview: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(12.dp)
            )
            .padding(12.dp),
        // Top alignment is the row-level default so the checkbox naturally
        // lands top-left against the now-taller (thumbnail-carrying) row,
        // per the requested layout. Every other child below explicitly
        // overrides this back to CenterVertically so nothing else looks
        // top-pinned — only the checkbox does.
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Checkbox — top-left, using the Row's default Top alignment
        Checkbox(
            checked = isSelected,
            onCheckedChange = { onToggleSelect() }
        )

        // Thumbnail — tapping it opens the same full-screen preview as the
        // View button. Sized generously enough to actually recognize the
        // photo/video content, not just a tiny icon.
        Box(
            modifier = Modifier
                .align(Alignment.CenterVertically)
                .size(72.dp)
                .clip(RoundedCornerShape(10.dp))
                .clickable { onPreview() }
        ) {
            AsyncImage(
                model = Uri.parse(item.uri),
                contentDescription = item.displayName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // Small play-badge for videos, mirroring MediaCard's existing
            // video-indicator language elsewhere in the app.
            if (item.getMediaTypeCategory() == MediaTypeCategory.VIDEO) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .background(Color.Black.copy(alpha = 0.45f), shape = CircleShape)
                        .padding(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Video",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // Item details
        Column(
            modifier = Modifier
                .weight(1f)
                .align(Alignment.CenterVertically),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = item.displayName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
            Text(
                text = "${item.getReadableSize()} • ${item.getAgeInDays()} day(s) old",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }

        // Action buttons — View, then Restore to its right, as requested.
        Row(
            modifier = Modifier.align(Alignment.CenterVertically),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // View button — opens the full-screen photo/video preview.
            Button(
                onClick = onPreview,
                contentPadding = TrashRowActionButtonPadding,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary
                )
            ) {
                Text("🔍", style = MaterialTheme.typography.labelLarge)
            }

            // Restore button — unchanged behavior, just recompacted to
            // match the View button's sizing.
            Button(
                onClick = onRestore,
                contentPadding = TrashRowActionButtonPadding,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text("↶", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun TrashBinActionBar(
    selectedCount: Int,
    selectedSize: String,
    onDelete: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Delete $selectedCount item(s) to free $selectedSize?",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = onCancel,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors()
            ) {
                Text("Cancel")
            }

            Button(
                onClick = onDelete,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary
                )
            ) {
                Text("Delete")
            }
        }
    }
}

@Composable
private fun DeleteConfirmationDialog(
    itemCount: Int,
    sizeToFree: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f))
            .clickable(enabled = false) {},
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .background(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Permanently Delete?",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Delete $itemCount item(s)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Free $sizeToFree",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Text(
                text = "This action cannot be undone.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors()
                ) {
                    Text("Cancel")
                }

                Button(
                    onClick = onConfirm,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text("Delete")
                }
            }
        }
    }
}

@Composable
private fun LoadingState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            Text(
                text = "Loading trash...",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
    }
}

@Composable
private fun EmptyTrashState(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Back button at top
        Text(
            text = "← Back",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .clickable { onBack() }
                .padding(16.dp)
        )

        // Centered empty state content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "🗑️",
                style = MaterialTheme.typography.displayLarge
            )

            Text(
                text = "Trash is Empty",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 16.dp)
            )

            Text(
                text = "Items you swipe left will appear here",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}