package com.storagerush.app.ui.components

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

/**
 * Full-screen photo viewer, opened from the Trash Bin's "View" button so
 * the user can see exactly what they're about to permanently delete
 * before confirming. Deliberately mirrors ui/deck/VideoOverlay.kt's
 * conventions (dark scrim, close button top-right in the same position/
 * style, BackHandler covering both the system edge-swipe-back gesture and
 * the hardware/software back button) so both overlays feel like the same
 * app-wide "preview" pattern rather than two different ones.
 *
 * Deliberately independent of VideoOverlay/MediaItem — this only ever
 * needs a URI, so it stays reusable from anywhere without requiring a
 * full MediaItem to be constructed first.
 */
@Composable
fun PhotoViewer(
    imageUri: Uri,
    contentDescription: String? = null,
    onDismiss: () -> Unit
) {
    // Same reasoning as VideoOverlay's BackHandler: composed only while
    // this overlay is visible, so it takes priority over whatever back
    // handling the screen underneath (e.g. TrashBinScreen) has — closing
    // just the photo viewer instead of navigating away entirely.
    BackHandler(enabled = true) {
        onDismiss()
    }

    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Close button, top-right — same placement/style as VideoOverlay
            Box(modifier = Modifier.fillMaxWidth()) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.CenterEnd)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close photo",
                        tint = Color.White
                    )
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    // Double-tap toggles between fit and a fixed zoomed-in
                    // level — the standard photo-viewer shortcut, in
                    // addition to continuous pinch-zoom below.
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = {
                                if (scale > 1f) {
                                    scale = 1f
                                    offset = Offset.Zero
                                } else {
                                    scale = 2.5f
                                }
                            }
                        )
                    }
                    // Pinch to zoom continuously, and pan while zoomed in.
                    // Panning is disabled at scale 1 (offset snaps back to
                    // zero) so the image can't be dragged off-frame while
                    // unzoomed.
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            val newScale = (scale * zoom).coerceIn(1f, 5f)
                            scale = newScale
                            offset = if (newScale <= 1f) Offset.Zero else offset + pan
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = imageUri,
                    contentDescription = contentDescription,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offset.x,
                            translationY = offset.y
                        )
                )
            }
        }
    }
}