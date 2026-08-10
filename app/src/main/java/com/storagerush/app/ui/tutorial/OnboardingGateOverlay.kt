package com.storagerush.app.ui.tutorial

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.storagerush.app.ui.components.HelpButton
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Forced first-launch gate: dims/blocks the whole screen and points at the
 * help button until the user taps it. Nothing underneath is reachable.
 *
 * Note on blur: true background blur (Modifier.blur()) only has an effect
 * on API 31+ (Android 12+), and would require applying it to the actual
 * Deck content itself rather than this overlay — coupling this gate to
 * DeckScreen's internals for a purely cosmetic gain. A strong dark scrim
 * instead gives the same "this is blocked" signal uniformly across the
 * full API 24+ range this app supports, with no such coupling.
 *
 * Note on positioning: the duplicate "?" hotspot below is placed with a
 * padding value tuned to approximate the real button's position in
 * DeckScreen's top bar. Since the real button's generous IconButton touch
 * target makes small offsets forgivable, exact pixel alignment isn't
 * required for it to function — but if it looks visually off after
 * on-device testing, the top padding here is the value to adjust (same
 * tuning approach used for the undo button's position).
 */
@Composable
fun OnboardingGateOverlay(
    onHelpTapped: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // Scrim — intercepts and swallows every touch. Deliberately NOT
        // given statusBarsPadding: the dim should visually cover the full
        // screen, including the area behind the now-transparent status
        // bar, not leave a gap there.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.75f))
                .pointerInput(Unit) {
                    detectTapGestures { /* consume touch, do nothing */ }
                }
        )

        // Interactive/visual content gets its own statusBarsPadding so its
        // alignment anchors (TopEnd) line up with DeckScreen's real TopBar
        // and HelpButton, which now sit below the status bar too — without
        // this, the duplicate "?" hotspot below would drift out of sync
        // with the real button's on-screen position.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // Arrow + hint text, positioned bottom-left of the "?" button,
            // pointing diagonally up-right toward it.
            // NOTE: top/end padding values here are tuned approximations (same
            // approach used for the undo button and gate hotspot positioning
            // elsewhere) — nudge them if the on-device result looks off.
            Column(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 56.dp, end = 60.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
            // Arrow only — hand-drawn (not a text glyph like "↗"), so its
            // size is exact and it renders identically on every device,
            // same reasoning as the "?" button being a drawn shape rather
            // than an emoji/font character. Sized 64dp — at least 2x the
            // button's 30dp circle, as requested.
            val infiniteTransition = rememberInfiniteTransition(label = "arrow_pulse")
            val bounceOffset by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = -10f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 600, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "arrow_offset"
            )

            Canvas(
                modifier = Modifier
                    .size(64.dp)
                    .offset(y = bounceOffset.dp)
            ) {
                val strokeWidthPx = 6.dp.toPx()
                val start = Offset(x = size.width * 0.15f, y = size.height * 0.85f)
                val end = Offset(x = size.width * 0.85f, y = size.height * 0.15f)

                drawLine(
                    color = Color.White,
                    start = start,
                    end = end,
                    strokeWidth = strokeWidthPx,
                    cap = StrokeCap.Round
                )

                // Arrowhead at the pointing end, angled back from the line's direction.
                val angle = atan2((end.y - start.y).toDouble(), (end.x - start.x).toDouble())
                val headLength = size.minDimension * 0.22f
                val headSpread = Math.toRadians(28.0)

                val headPoint1 = Offset(
                    x = (end.x - headLength * cos(angle - headSpread)).toFloat(),
                    y = (end.y - headLength * sin(angle - headSpread)).toFloat()
                )
                val headPoint2 = Offset(
                    x = (end.x - headLength * cos(angle + headSpread)).toFloat(),
                    y = (end.y - headLength * sin(angle + headSpread)).toFloat()
                )

                val arrowheadPath = Path().apply {
                    moveTo(end.x, end.y)
                    lineTo(headPoint1.x, headPoint1.y)
                    moveTo(end.x, end.y)
                    lineTo(headPoint2.x, headPoint2.y)
                }

                drawPath(
                    path = arrowheadPath,
                    color = Color.White,
                    style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Static text, below the arrow — does not participate in the
            // bounce animation above.
            Text(
                text = "Tap here\nget started!",
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
        }

        // Duplicate "?" hotspot — the only tappable element while the gate
        // is active. Drawn last so it's on top of the scrim.
        HelpButton(
            onClick = onHelpTapped,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 16.dp, end = 16.dp)
        )
    } // inner statusBarsPadding'd content layer
    } // outer Box
}