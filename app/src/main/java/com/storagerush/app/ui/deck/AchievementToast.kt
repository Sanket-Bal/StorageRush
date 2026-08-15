package com.storagerush.app.ui.deck

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import com.storagerush.app.data.gamification.Achievement

// Same brand purple used elsewhere for gamification accents (ProgressCard,
// StatsScreen's ring, LevelUpCelebrationOverlay).
private val BrandPurple = Color(0xFF2E2480)

/**
 * Top-anchored toast for a single achievement unlock. DeckScreen owns the
 * queue/auto-dismiss timing and passes in whichever Achievement (if any)
 * should currently be visible — this composable is just the visual +
 * enter/exit animation.
 */
@Composable
fun AchievementToast(
    achievement: Achievement?,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = achievement != null,
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
        modifier = modifier
    ) {
        // achievement is non-null whenever this is actually composed
        // (AnimatedVisibility keeps rendering the last content briefly
        // during the exit animation, so this local val is captured once
        // rather than re-reading a value that may have already gone null).
        val current = achievement ?: return@AnimatedVisibility

        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(BrandPurple)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(text = current.emoji, style = MaterialTheme.typography.titleMedium)
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column {
                Text(
                    text = "Achievement unlocked",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.7f)
                )
                Text(
                    text = "${current.title} — ${current.description}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            }
        }
    }
}