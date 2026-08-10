package com.storagerush.app.ui.deck

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.storagerush.app.data.repository.PlayerState

/**
 * Compact progress card shown at the top of the Deck screen, just below
 * the TopBar: Level + weekly streak (when active) + XP progress toward
 * the next level, all in one tappable row. Tapping navigates to the full
 * Stats screen — mirrors how the top-right Level badge behaved in the
 * earlier mockup iteration, just expanded into a full-width card.
 */
@Composable
fun ProgressCard(
    playerState: PlayerState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Sampled directly from the app logo's card (the deep indigo-purple in
    // its bottom bar) so the XP fill visually ties back to the brand,
    // rather than using the theme's lighter onPrimaryContainer tone.
    val brandDarkPurple = Color(0xFF2E2480)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "⭐",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(end = 12.dp)
        )

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Level ${playerState.level}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )

                // Streak only shows once one exists — a "0 week streak"
                // label right next to a brand-new Level 1 badge would read
                // as a mild scolding, not a hook.
                if (playerState.weeklyStreak > 0) {
                    val weekWord = if (playerState.weeklyStreak == 1) "week" else "weeks"
                    Text(
                        text = "   \uD83D\uDD25 ${playerState.weeklyStreak} $weekWord streak",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Text(
                text = "${playerState.currentXp} / ${playerState.xpRequiredForNextLevel} XP",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(top = 2.dp)
            )
        }

        // XP progress bar
        Box(
            modifier = Modifier
                .width(90.dp)
                .height(5.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.3f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(playerState.xpProgressFraction)
                    .height(5.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(brandDarkPurple)
            )
        }
    }
}