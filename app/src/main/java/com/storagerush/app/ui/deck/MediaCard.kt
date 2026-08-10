package com.storagerush.app.ui.deck

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.storagerush.app.data.model.MediaItem
import com.storagerush.app.data.model.MediaTypeCategory
import kotlin.math.roundToInt

/**
 * Composable for a single swipeable media card (image or video).
 * Displays the media with drag gesture support and optional video badge overlay.
 * 
 * @param mediaItem The media item to display
 * @param modifier Modifier for layout
 * @param onDragLeft Callback when user drags significantly to the left (Trash)
 * @param onDragRight Callback when user drags significantly to the right (Keep)
 * @param onDragCancel Callback when user releases without crossing threshold
 * @param onTapVideo Callback when the card is tapped (not dragged) AND the
 *   media is a video — opens the video overlay (Phase B). Photo cards
 *   ignore taps; only swipe/button actions apply to them.
 */
@Composable
fun MediaCard(
    mediaItem: MediaItem,
    modifier: Modifier = Modifier,
    onDragLeft: () -> Unit = {},
    onDragRight: () -> Unit = {},
    onDragCancel: () -> Unit = {},
    onTapVideo: () -> Unit = {}
) {
    val offsetX = remember { mutableFloatStateOf(0f) }
    val rotation = remember { mutableFloatStateOf(0f) }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .pointerInput(mediaItem) {
                // Single sequential gesture state machine, built on the
                // same primitives detectDragGestures itself uses internally
                // (awaitTouchSlopOrCancellation + drag) — reads the touch
                // stream exactly once per gesture, instead of racing two
                // independent detectDragGestures/detectTapGestures coroutines
                // against the same stream.
                val dragThreshold = 200f // pixels to trigger keep/trash action

                awaitEachGesture {
                    // 1) Wait for the initial touch-down.
                    val down = awaitFirstDown(requireUnconsumed = false)

                    // 2) Wait for either touch-slop to be crossed (→ drag)
                    // or the pointer to lift first (→ tap). This is the
                    // exact primitive Compose's own drag detectors use.
                    var overSlop = Offset.Zero
                    val slopChange = awaitTouchSlopOrCancellation(down.id) { change, over ->
                        change.consume()
                        overSlop = over
                    }

                    if (slopChange == null) {
                        // Slop never crossed before lift-up/cancel → tap.
                        if (mediaItem.getMediaTypeCategory() == MediaTypeCategory.VIDEO) {
                            onTapVideo()
                        }
                    } else {
                        // 3) Slop crossed → this is a drag. Seed the offset
                        // with the overSlop already consumed above, then
                        // keep tracking with the same offset/rotation logic
                        // as before until the pointer lifts.
                        offsetX.floatValue += overSlop.x
                        rotation.floatValue = (offsetX.floatValue / 100).coerceIn(-15f, 15f)

                        val finishedNormally = drag(slopChange.id) { change ->
                            // Read the delta BEFORE consuming. positionChange()
                            // is consumption-aware — it returns Offset.Zero
                            // once a change is marked consumed, so
                            // consume()-then-read (the previous order) was
                            // silently zeroing out almost every frame's
                            // movement, which is exactly what the logcat
                            // capture showed: offsetX frozen for long runs,
                            // jumping only on the rare frame that happened to
                            // read correctly.
                            val deltaX = change.positionChange().x
                            change.consume()
                            offsetX.floatValue += deltaX
                            rotation.floatValue = (offsetX.floatValue / 100).coerceIn(-15f, 15f)
                        }

                        if (finishedNormally) {
                            when {
                                offsetX.floatValue > dragThreshold -> onDragRight()
                                offsetX.floatValue < -dragThreshold -> onDragLeft()
                                else -> onDragCancel()
                            }
                        } else {
                            onDragCancel()
                        }

                        // Reset for next card.
                        offsetX.floatValue = 0f
                        rotation.floatValue = 0f
                    }
                }
            }
            .offset {
                // Lambda form: reads offsetX at the LAYOUT/DRAW phase, not
                // composition, so dragging no longer forces a full
                // recomposition of MediaCard (AsyncImage, VideoBadge, etc.)
                // on every pixel of movement. That per-pixel recomposition
                // was competing for main-thread time with the gesture
                // coroutine resolving tap-vs-drag, which is what caused the
                // delayed/inconsistent triggers.
                //
                // offsetX is already in raw pixels (from positionChange()),
                // and IntOffset takes pixels directly — no dp conversion,
                // no arbitrary scaling. This is what gives true 1:1 tracking;
                // the old `(offsetX.floatValue / 16).dp` was shrinking real
                // finger movement by roughly 16x the density factor, which
                // is why a full-screen swipe only moved the card a few
                // visible pixels.
                IntOffset(offsetX.floatValue.roundToInt(), 0)
            }
            .graphicsLayer {
                // Same reasoning as offset{} above — graphicsLayer{} reads
                // rotation at draw time instead of composition time.
                rotationZ = rotation.floatValue
            }
    ) {
        // Load media thumbnail via Coil with downsampling
        AsyncImage(
            model = mediaItem.uri,
            contentDescription = mediaItem.displayName,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // Video badge overlay (for videos only)
        if (mediaItem.getMediaTypeCategory() == MediaTypeCategory.VIDEO) {
            VideoBadge(
                durationMillis = mediaItem.duration,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(16.dp)
            )
        }
    }
}

/**
 * Composable for video badge overlay (play icon + duration)
 */
@Composable
private fun VideoBadge(
    durationMillis: Long,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                shape = RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.PlayArrow,
            contentDescription = "Video",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(end = 8.dp)
        )

        Text(
            text = formatDuration(durationMillis),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/**
 * Formats milliseconds to readable duration string (e.g., "02:15")
 */
private fun formatDuration(millis: Long): String {
    val seconds = millis / 1000
    val minutes = seconds / 60
    val secs = seconds % 60
    return String.format("%02d:%02d", minutes, secs)
}