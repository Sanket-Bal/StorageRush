package com.storagerush.app.ui.deck

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.storagerush.app.data.model.MediaItem
import kotlinx.coroutines.delay

/**
 * Full-screen video overlay (Phase B). Opened by tapping a video card in
 * the deck. Shows the video at its native aspect ratio (letterboxed, never
 * cropped or rotated), with a dark scrim behind it and custom play/pause +
 * seek controls below.
 *
 * ExoPlayer lifecycle: the player is created in [remember] and released in
 * [DisposableEffect]'s onDispose, which fires when this composable leaves
 * composition — i.e. exactly when the caller stops rendering the overlay
 * (X tapped, back pressed, etc). This is the officially recommended
 * Compose+Media3 pattern: the player's lifetime is tied directly to the
 * overlay's own lifetime, so it can never outlive the UI that owns it or
 * leak the Context it was built with.
 */
@Composable
fun VideoOverlay(
    mediaItem: MediaItem,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    val exoPlayer = remember(mediaItem.id) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(ExoMediaItem.fromUri(mediaItem.uri))
            prepare()
            playWhenReady = true
        }
    }

    // Back gesture/button support — closes just the overlay instead of
    // falling through to DeckScreen's own BackHandler (which would navigate
    // out of the deck entirely). Compose gives priority to the most
    // recently composed BackHandler, and since VideoOverlay is only
    // composed while it's showing, this one intercepts first — covering
    // both the system edge-swipe gesture and the hardware/software back
    // button, since both go through the same OnBackPressedDispatcher.
    BackHandler(enabled = true) {
        onDismiss()
    }

    DisposableEffect(exoPlayer) {
        onDispose {
            exoPlayer.release()
        }
    }

    var isPlaying by remember { mutableStateOf(true) }
    var currentPositionMs by remember { mutableFloatStateOf(0f) }
    var durationMs by remember { mutableFloatStateOf(mediaItem.duration.toFloat()) }
    var isUserSeeking by remember { mutableStateOf(false) }

    // Poll position while playing — ExoPlayer doesn't push continuous
    // position updates on its own, only discrete state-change events.
    LaunchedEffect(exoPlayer) {
        while (true) {
            if (!isUserSeeking) {
                currentPositionMs = exoPlayer.currentPosition.toFloat().coerceAtLeast(0f)
                if (exoPlayer.duration > 0) {
                    durationMs = exoPlayer.duration.toFloat()
                }
                isPlaying = exoPlayer.isPlaying
            }
            delay(300)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f))
            .pointerInput(Unit) {
                detectTapGestures { /* consume taps on the scrim itself, don't pass through to Deck */ }
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Close button, top-right
            Box(modifier = Modifier.fillMaxWidth()) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.CenterEnd)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close video",
                        tint = Color.White
                    )
                }
            }

            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center
            ) {
                // Give PlayerView the full available space and let its own
                // native resizeMode (RESIZE_MODE_FIT, the default) do the
                // letterboxing. This uses the real, decoder-reported video
                // dimensions — which correctly account for rotation —
                // instead of pre-shaping this container from MediaStore's
                // width/height metadata, which doesn't always match. One
                // source of truth for fitting, not two fighting each other.
                // Landscape videos get bars above/below, portrait videos
                // get bars left/right and use the full available height —
                // never cropped, never rotated.
                AndroidView(
                    factory = {
                        PlayerView(context).apply {
                            player = exoPlayer
                            useController = false // custom PlayerControls below instead
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            PlayerControls(
                isPlaying = isPlaying,
                currentPositionMs = currentPositionMs,
                durationMs = durationMs.coerceAtLeast(1f),
                onPlayPauseClick = {
                    if (exoPlayer.isPlaying) {
                        exoPlayer.pause()
                    } else {
                        exoPlayer.play()
                    }
                    isPlaying = exoPlayer.isPlaying
                },
                onSeekChange = { newPositionMs ->
                    isUserSeeking = true
                    currentPositionMs = newPositionMs
                },
                onSeekFinished = {
                    exoPlayer.seekTo(currentPositionMs.toLong())
                    isUserSeeking = false
                }
            )
        }
    }
}

@Composable
private fun PlayerControls(
    isPlaying: Boolean,
    currentPositionMs: Float,
    durationMs: Float,
    onPlayPauseClick: () -> Unit,
    onSeekChange: (Float) -> Unit,
    onSeekFinished: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp)
    ) {
        Slider(
            value = currentPositionMs.coerceIn(0f, durationMs),
            valueRange = 0f..durationMs,
            onValueChange = onSeekChange,
            onValueChangeFinished = onSeekFinished,
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = Color.White.copy(alpha = 0.3f)
            )
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatDurationMs(currentPositionMs.toLong()),
                color = Color.White,
                style = MaterialTheme.typography.labelSmall
            )
            Text(
                text = formatDurationMs(durationMs.toLong()),
                color = Color.White,
                style = MaterialTheme.typography.labelSmall
            )
        }

        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            IconButton(
                onClick = onPlayPauseClick,
                modifier = Modifier.size(56.dp)
            ) {
                if (isPlaying) {
                    // Hand-drawn — Icons.Default.Pause isn't confirmed to be
                    // in this project's core icon set (same risk that broke
                    // the build with PhotoLibrary earlier), so avoid it.
                    PauseGlyph(modifier = Modifier.size(40.dp), color = Color.White)
                } else {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play",
                        tint = Color.White,
                        modifier = Modifier.size(40.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun PauseGlyph(
    modifier: Modifier = Modifier,
    color: Color = Color.White
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .weight(1f)
                .background(color)
        )
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .weight(1f)
                .background(color)
        )
    }
}

private fun formatDurationMs(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}