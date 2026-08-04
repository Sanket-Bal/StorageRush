package com.example.swipeclean.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * White circle outline with a white "?" centered inside — drawn directly
 * rather than using an emoji/font glyph, since those can render
 * inconsistently across OEM font stacks (same class of issue behind the
 * font-scaling bug fixed in Phase 3.7). A vector-drawn shape looks
 * identical on every device.
 *
 * Used in two places that must stay visually identical: the real button
 * in DeckScreen's top bar, and the duplicate tappable hotspot drawn on
 * top of the onboarding gate's scrim.
 */
@Composable
fun HelpButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    IconButton(
        onClick = onClick,
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .border(BorderStroke(1.5.dp, Color.White), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "?",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        }
    }
}