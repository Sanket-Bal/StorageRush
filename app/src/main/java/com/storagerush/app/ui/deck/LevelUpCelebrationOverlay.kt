package com.storagerush.app.ui.deck

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Same brand purple sampled from the logo, used elsewhere for the XP bar
// and Progress tab's ring (ProgressCard.kt, StatsScreen.kt).
private val BrandPurple = Color(0xFF2E2480)

private data class SparkleSpec(
    val xDp: Int,
    val yDp: Int,
    val sizeSp: Int,
    val periodMs: Int,
    val delayMs: Int,
    val symbol: String
)

// Fixed scattered positions — matches the mockup's layout. Each sparkle
// twinkles on its own period/delay so they don't all pulse in unison.
private val sparkles = listOf(
    SparkleSpec(xDp = 40, yDp = 60, sizeSp = 20, periodMs = 1400, delayMs = 0, symbol = "\u2728"),
    SparkleSpec(xDp = 300, yDp = 110, sizeSp = 16, periodMs = 1600, delayMs = 200, symbol = "\u2728"),
    SparkleSpec(xDp = 90, yDp = 90, sizeSp = 14, periodMs = 1200, delayMs = 400, symbol = "\u2B50"),
    SparkleSpec(xDp = 270, yDp = 180, sizeSp = 20, periodMs = 1800, delayMs = 100, symbol = "\u2B50"),
    SparkleSpec(xDp = 55, yDp = 260, sizeSp = 16, periodMs = 1500, delayMs = 300, symbol = "\u2728"),
    SparkleSpec(xDp = 310, yDp = 320, sizeSp = 18, periodMs = 1300, delayMs = 500, symbol = "\u2728")
)

/**
 * Full-screen level-up celebration. Purely driven by transient state passed
 * in from DeckScreen (sourced from TrashBinViewModel's in-memory
 * TrashBinState.leveledUp flag) — nothing here is persisted, so if the app
 * process dies the celebration simply doesn't exist to show again on
 * reopen. Only dismisses via the Continue button; there is no scrim-tap or
 * back-button dismissal by design.
 */
@Composable
fun LevelUpCelebrationOverlay(
    newLevel: Int,
    freedBytesReadable: String,
    onContinue: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BrandPurple.copy(alpha = 0.92f))
    ) {
        sparkles.forEach { spec ->
            TwinklingSparkle(spec)
        }

        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .background(Color.White.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "\u2B50", fontSize = 48.sp)
            }

            androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(top = 24.dp))

            Text(
                text = "LEVEL UP",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.7f),
                letterSpacing = 1.sp
            )

            Text(
                text = "Level $newLevel",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Medium,
                color = Color.White,
                modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
            )

            Text(
                text = "You freed $freedBytesReadable to get here",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.75f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 40.dp)
            )

            Button(
                onClick = onContinue,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = BrandPurple
                )
            ) {
                Text(
                    text = "Continue",
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun TwinklingSparkle(spec: SparkleSpec) {
    val infiniteTransition = rememberInfiniteTransition(label = "sparkle_${spec.xDp}_${spec.yDp}")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = spec.periodMs,
                delayMillis = spec.delayMs,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "sparkle_alpha"
    )

    Text(
        text = spec.symbol,
        fontSize = spec.sizeSp.sp,
        color = Color.White.copy(alpha = alpha),
        modifier = Modifier.offset(x = spec.xDp.dp, y = spec.yDp.dp)
    )
}